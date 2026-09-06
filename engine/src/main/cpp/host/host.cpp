/* host.cpp — see host.h for the contract this file implements. */
#include "host.h"

#include <algorithm>
#include <chrono>
#include <cstdarg>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <future>
#include <memory>

#include "state_store.h"

namespace mb {

thread_local EmuHost *EmuHost::active_ = nullptr;

static constexpr double kNtscFps = 60.09881130985343;
static constexpr double kPalFps = 50.00697700455593;

/* ---------------------------------------------------------------- ctor/dtor */
EmuHost::EmuHost() = default;

EmuHost::~EmuHost() {
  stop();
  if (surface_) {
    surface_->detach();
    surface_destroy(surface_);
    surface_ = nullptr;
  }
  if (audio_) {
    audio_->close();
    audio_destroy(audio_);
    audio_ = nullptr;
  }
  core_unload(&core_);
}

mb_status EmuHost::configure(const mb_config &cfg) {
  cfg_ = cfg;
  if (cfg_.core_path)
    core_path_ = cfg_.core_path;
  if (cfg_.rom_path)
    rom_path_ = cfg_.rom_path;
  if (cfg_.save_dir)
    save_dir_ = cfg_.save_dir;
  if (cfg_.system_dir)
    system_dir_ = cfg_.system_dir;
  if (save_dir_.empty())
    save_dir_ = ".";
  if (rom_path_.empty() || core_path_.empty()) {
    error_ = "core_path and rom_path are required";
    return MB_ERR_INVALID;
  }
  if (!ensure_dir(save_dir_.c_str())) {
    error_ = "cannot create save directory: " + save_dir_;
    return MB_ERR_IO;
  }
  if (!read_whole_file(rom_path_.c_str(), &rom_bytes_)) {
    error_ = "cannot read ROM: " + rom_path_;
    return MB_ERR_NO_ROM;
  }
  if (rom_bytes_.size() < 16 || memcmp(rom_bytes_.data(), "NES\x1A", 4) != 0) {
    /* UNIF/FDS do not start with that magic; the core decides, we only reject
     * obviously truncated files. */
    if (rom_bytes_.size() < 64) {
      error_ = "file is too small to be a NES image";
      rom_bytes_.clear();
      return MB_ERR_NO_ROM;
    }
  }
    rom_fp_ = mb::rom_fingerprint(rom_bytes_);


  if (!core_load(core_path_.c_str(), &core_)) {
    error_ = "core load failed: " + core_.error;
    return MB_ERR_NO_CORE;
  }

  struct retro_system_info si {};
  core_.get_system_info(&si);
  core_name_ = si.library_name ? si.library_name : "core";
  
  cheats_native_ = core_.ext.native_cheats();


  uint32_t want_frames = cfg_.rewind_frames;
  uint32_t stride = cfg_.rewind_stride ? cfg_.rewind_stride : 1;
  if (want_frames) {
    uint32_t cap = want_frames / stride + 2;
    uint64_t budget =
        uint64_t(cfg_.rewind_budget_kb ? cfg_.rewind_budget_kb : 32u * 1024u) * 1024ull;
    rewind_.configure(cap, budget);
  }
  MB_LOGI("configured core=%s rom=%s size=%zu rewind=%u stride=%u native_cheats=%d",
          core_name_.c_str(), rom_path_.c_str(), rom_bytes_.size(), want_frames, stride,
          int(cheats_native_));
  return MB_OK;
}

/* ---------------------------------------------------------------- lifecycle */
mb_status EmuHost::start() {
  if (running_.load())
    return MB_OK;
  quit_ = false;
  running_ = true;
  loaded_ = false;
  thread_ = std::thread([this] { thread_main(); });
  /* Block until the core has answered, so a bad core or ROM is reported here
   * instead of after the first black frame. 20 s covers a slow cold start. */
  for (int i = 0; i < 2000 && running_.load() && !loaded_.load() && !quit_.load(); i++)
    sleep_ns(10 * 1000 * 1000);
  if (!loaded_.load()) {
    stop();
    return MB_ERR_NO_GAME;
  }
  return MB_OK;
}

mb_status EmuHost::stop() {
  if (!thread_.joinable())
    return MB_OK;
  quit_ = true;
  paused_ = false;
  job_cv_.notify_all();
  thread_.join();
  running_ = false;
  loaded_ = false;
  return MB_OK;
}

void EmuHost::set_paused(bool p) {
  paused_ = p;
  if (audio_)
    audio_->set_paused(p);
  job_cv_.notify_all();
}

mb_status EmuHost::reset() {
  return post([this] {
    if (!core_.reset)
      return MB_ERR_UNSUPPORTED;
    core_.reset();
    rewind_clear();
    return MB_OK;
  });
}

mb_status EmuHost::power_cycle() {
  return post([this] {
    do_unload();
    return do_load();
  });
}

/* ------------------------------------------------------------------ surface */
mb_status EmuHost::set_surface(void *native_window) {
  if (!surface_) {
    surface_ = surface_create();
    if (!surface_) {
      error_ = "no rendering backend available on this platform";
      return MB_ERR_UNSUPPORTED;
    }
    surface_->apply(render_cfg_);
  }
  if (!native_window) {
    surface_->detach();
    return MB_OK;
  }
  uint32_t w = 0, h = 0;
  {
    std::lock_guard<std::mutex> lk(frame_mu_);
    w = frame_w_;
    h = frame_h_;
  }
  if (!surface_->attach(native_window, w ? w : 256, h ? h : 240)) {
    error_ = "surface attach failed";
    return MB_ERR_INVALID;
  }
  surface_->apply(render_cfg_);
  return MB_OK;
}

mb_status EmuHost::set_surface_size(int32_t w, int32_t h) {
  if (!surface_)
    return MB_ERR_INVALID;
  surface_->resize(w > 0 ? uint32_t(w) : 0, h > 0 ? uint32_t(h) : 0);
  return MB_OK;
}

void EmuHost::set_video_enabled(bool on) { video_enabled_ = on; }

void EmuHost::set_render_config(const RenderConfig &cfg) {
  render_cfg_ = cfg;
  if (surface_)
    surface_->apply(cfg);
}

mb_status EmuHost::copy_frame(uint8_t *out, size_t cap, uint32_t *w, uint32_t *h,
                              uint32_t *pitch) {
  std::lock_guard<std::mutex> lk(frame_mu_);
  if (w)
    *w = frame_w_;
  if (h)
    *h = frame_h_;
  if (pitch)
    *pitch = frame_pitch_;
  size_t need = size_t(frame_pitch_) * frame_h_;
  if (!need)
    return MB_ERR_NO_GAME;
  if (cap < need)
    return MB_ERR_INVALID;
  memcpy(out, frame_.data(), need);
  return MB_OK;
}

/* -------------------------------------------------------------------- input */
void EmuHost::set_input(uint32_t port, uint32_t mask) {
  if (port >= 5)
    return;
  uint32_t prev = input_[port].exchange(mask & 0xFFFFu);
  if (prev != (mask & 0xFFFFu))
    input_frames_.fetch_add(1);
}

uint32_t EmuHost::input(uint32_t port) const { return port < 5 ? input_[port].load() : 0; }

void EmuHost::set_turbo(uint32_t port, uint32_t mask, int hz) {
  if (port >= 5)
    return;
  Turbo &t = turbo_[port];
  t.mask = mask;
  t.hz = hz;
  t.on = mask != 0 && hz > 0;
}

void EmuHost::set_fast_forward(bool on) {
  ff_ = on;
  if (audio_)
    audio_->set_paused(on);
}

void EmuHost::set_slow_motion(bool on) {
  slowmo_ = on;
  if (audio_)
    audio_->set_paused(on);
}

mb_status EmuHost::advance_frame() {
  if (!running_.load())
    return MB_ERR_BUSY;
  if (!paused_.load())
    return MB_ERR_INVALID; /* stepping only makes sense while paused */
  step_once_ = true;
  job_cv_.notify_all();
  return MB_OK;
}

/* --------------------------------------------------------------- save states */
mb_status EmuHost::save_state(const std::string &path) {
  return post([this, path] {
    size_t sz = serialize_size();
    if (!sz)
      return MB_ERR_NO_GAME;
    state_buf_.resize(sz);
    if (!core_.serialize(state_buf_.data(), sz)) {
      error_ = "core refused to serialize";
      return MB_ERR_STATE;
    }
    SaveStateHeader h;
    h.rom_fingerprint = rom_fp_;
    h.core_name = core_name_;
    h.frame = frames_emulated_.load();
    h.created_ms = uint64_t(now_ns() / 1000000ull);
    h.play_ms = play_ms_ + (play_started_ms_ ? uint64_t(now_ns() / 1000000ull) - play_started_ms_ : 0);
    h.flags = kStateFlagManual;
    h.state.swap(state_buf_);
    state_buf_.resize(sz); /* keep the scratch buffer alive for the next frame */
    h.title = path_basename_noext(path);
    {
      std::lock_guard<std::mutex> lk(frame_mu_);
      thumb_downscale(frame_.data(), frame_w_, frame_h_, frame_pitch_, 160, 120, &h.thumb_rgba,
                      &h.thumb_w, &h.thumb_h);
      if (h.thumb_w && h.thumb_h)
        h.flags |= kStateFlagScreenshot;
    }
    std::string err;
    if (!state_write(path, h, &err)) {
      error_ = err.empty() ? "state write failed" : err;
      return MB_ERR_IO;
    }
    if (cfg_.sram_save_on_state)
      sram_flush_locked();
    MB_LOGI("state saved: %s (%zu bytes, frame %llu)", path.c_str(), sz,
            (unsigned long long)h.frame);
    return MB_OK;
  });
}

mb_status EmuHost::load_state(const std::string &path) {
  return post([this, path] {
    SaveStateHeader h;
    std::string err;
    /* Strict first (same ROM + same core), then permissive: a state the core
     * still accepts is better than a hard refusal, and the core itself is the
     * authority on whether a blob fits the machine it is running. */
    if (!state_read(path, &h, hex64(rom_fp_), core_name_, false, &err)) {
      SaveStateHeader loose;
      if (!state_read(path, &loose, "", "", false, &err)) {
        error_ = err;
        return MB_ERR_STATE;
      }
      h = loose;
    }
    if (h.state.empty()) {
      error_ = "state has no payload";
      return MB_ERR_STATE;
    }
    size_t want = serialize_size();
    if (want && h.state.size() != want) {
      char buf[224];
      snprintf(buf, sizeof(buf),
               "state size %zu != core's %zu — saved by a different core build?",
               h.state.size(), want);
      error_ = buf;
      return MB_ERR_STATE;
    }
    if (!core_.unserialize(h.state.data(), h.state.size())) {
      error_ = "core refused to unserialize";
      return MB_ERR_STATE;
    }
    cheats_.mark_clean(); /* the state may have carried the core's cheat list */
    if (cheats_native_)
      sync_cheats_locked();
    rewind_.clear();
    emit(MB_EVENT_MESSAGE, "state loaded");
    MB_LOGI("state loaded: %s (frame %llu)", path.c_str(), (unsigned long long)h.frame);
    return MB_OK;
  });
}

size_t EmuHost::serialize_size() {
  if (state_size_)
    return state_size_;
  if (core_.serialize_size)
    state_size_ = core_.serialize_size();
  return state_size_;
}

mb_status EmuHost::serialize_to(uint8_t *out, size_t size) {
  size_t want = serialize_size();
  if (!want)
    return MB_ERR_NO_GAME;
  if (size < want)
    return MB_ERR_INVALID;
  std::lock_guard<std::mutex> lk(core_mu_);
  if (!core_.serialize(out, want))
    return MB_ERR_STATE;
  return MB_OK;
}

mb_status EmuHost::unserialize_from(const uint8_t *in, size_t size) {
  size_t want = serialize_size();
  if (want && size != want)
    return MB_ERR_INVALID;
  std::lock_guard<std::mutex> lk(core_mu_);
  if (!core_.unserialize(in, size))
    return MB_ERR_STATE;
  rewind_.clear();
  return MB_OK;
}

/* -------------------------------------------------------------------- rewind */
void EmuHost::capture_rewind_locked() {
  if (rewind_.capacity() == 0)
    return;
  uint32_t stride = cfg_.rewind_stride ? cfg_.rewind_stride : 1;
  if ((frames_emulated_.load() % stride) != 0)
    return;
  size_t sz = serialize_size();
  if (!sz)
    return;
  if (state_buf_.size() < sz)
    state_buf_.resize(sz);
  if (!core_.serialize(state_buf_.data(), sz))
    return;
  rewind_.push(frames_emulated_.load(), state_buf_.data(), uint32_t(sz), 1);
}

mb_status EmuHost::rewind_step(uint32_t frames) {
  if (!frames)
    frames = 1;
  return post([this, frames] {
    std::vector<uint8_t> raw;
    if (!rewind_.pop_to(frames, &raw))
      return MB_ERR_STATE;
    if (!core_.unserialize(raw.data(), raw.size())) {
      error_ = "rewind state rejected by core";
      return MB_ERR_STATE;
    }
    if (cheats_native_)
      sync_cheats_locked();
    return MB_OK;
  });
}

uint32_t EmuHost::rewind_available() {
  uint32_t stride = cfg_.rewind_stride ? cfg_.rewind_stride : 1;
  uint32_t c = rewind_.count();
  return c > 1 ? (c - 1) * stride : 0;
}

void EmuHost::rewind_clear() { rewind_.clear(); }

/* ---------------------------------------------------------------------- sram */
void EmuHost::sram_flush_locked() {
  if (!cfg_.sram_enable || !sram_ptr_ || !sram_size_)
    return;
  std::string p = sram_path_for(save_dir_, rom_path_);
  if (sram_save(p, sram_ptr_, sram_size_)) {
    sram_dirty_ = false;
    sram_flushes_.fetch_add(1);
    if (sram_disk_.size() != sram_size_)
      sram_disk_.assign(sram_size_, 0);
    memcpy(sram_disk_.data(), sram_ptr_, sram_size_);
  } else {
    MB_LOGW("sram flush failed: %s", p.c_str());
  }
}

mb_status EmuHost::sram_flush() {
  return post([this] {
    sram_flush_locked();
    return MB_OK;
  });
}

mb_status EmuHost::sram_invalidate() {
  return post([this] {
    std::string p = sram_path_for(save_dir_, rom_path_);
    std::vector<uint8_t> disk;
    if (!sram_load(p, &disk)) {
      error_ = "no sram file to reload";
      return MB_ERR_IO;
    }
    if (!sram_ptr_ || disk.size() != sram_size_) {
      error_ = "sram size mismatch";
      return MB_ERR_IO;
    }
    memcpy(sram_ptr_, disk.data(), sram_size_);
    return MB_OK;
  });
}

/* -------------------------------------------------------------------- cheats */
int32_t EmuHost::cheat_add(const mb_cheat &c) {
  CheatEntry e;
  e.code = c.code;
  e.desc = c.desc;
  e.enabled = c.enabled != 0;
  e.kind = c.kind;
  DecodedCheat d = cheat_decode(c.code, c.kind);
  if (!d.ok) {
    error_ = d.error;
    return -1;
  }
  int32_t id = cheats_.add(std::move(e));
  if (id > 0 && running_.load()) {
    post([this] {
      sync_cheats_locked();
      return MB_OK;
    });
  }
  return id;
}

mb_status EmuHost::cheat_remove(int32_t id) {
  if (!cheats_.remove(id))
    return MB_ERR_INVALID;
  return post([this] {
    sync_cheats_locked();
    return MB_OK;
  });
}

mb_status EmuHost::cheat_set_enabled(int32_t id, bool on) {
  if (!cheats_.set_enabled(id, on))
    return MB_ERR_INVALID;
  return post([this] {
    sync_cheats_locked();
    return MB_OK;
  });
}

mb_status EmuHost::cheat_set_value(int32_t id, uint32_t v) {
  if (!cheats_.set_value(id, v))
    return MB_ERR_INVALID;
  return post([this] {
    sync_cheats_locked();
    return MB_OK;
  });
}

void EmuHost::cheat_clear() {
  cheats_.clear();
  post([this] {
    sync_cheats_locked();
    return MB_OK;
  });
}

int32_t EmuHost::cheat_count() const { return int32_t(cheats_.size()); }

mb_status EmuHost::cheat_get(int32_t index, mb_cheat *out) const {
  if (index < 0 || size_t(index) >= cheats_.size() || !out)
    return MB_ERR_INVALID;
  const CheatEntry &e = cheats_.items()[size_t(index)];
  memset(out, 0, sizeof(*out));
  out->struct_size = sizeof(mb_cheat);
  snprintf(out->code, sizeof(out->code), "%s", e.code.c_str());
  snprintf(out->desc, sizeof(out->desc), "%s", e.desc.c_str());
  out->enabled = e.enabled ? 1 : 0;
  out->kind = e.decoded.kind;
  out->address = e.decoded.address;
  out->value = e.decoded.value;
  out->compare = e.decoded.compare;
  return MB_OK;
}

int32_t EmuHost::cheat_peek_value(int32_t id) {
  for (const auto &e : cheats_.items()) {
    if (e.id == id)
      return int32_t(mem_peek(e.decoded.address));
  }
  return -1;
}

mb_status EmuHost::cheat_core_get(int32_t index, mb_cheat *out) {
  if (!out || index < 0 || size_t(index) >= cheats_.size())
    return MB_ERR_INVALID;
  const CheatEntry &e = cheats_.items()[size_t(index)];
  if (!core_.ext.cheat_get || e.core_index < 0)
    return MB_ERR_UNSUPPORTED;
  uint32_t addr = 0, val = 0;
  int32_t cmp = 0, status = 0, type = 0;
  char name[96] = {0};
  std::lock_guard<std::mutex> lk(core_mu_);
  if (core_.ext.cheat_get(uint32_t(e.core_index), &addr, &val, &cmp, &status, &type, name,
                         sizeof(name)) < 0)
    return MB_ERR_STATE;
  memset(out, 0, sizeof(*out));
  out->struct_size = sizeof(mb_cheat);
  out->address = addr;
  out->value = val;
  out->compare = cmp;
  out->enabled = status ? 1 : 0;
  out->kind = type;
  snprintf(out->code, sizeof(out->code), "%s", e.code.c_str());
  snprintf(out->desc, sizeof(out->desc), "%s", name);
  return MB_OK;
}

void EmuHost::sync_cheats_locked() {
  if (!cheats_native_) {
    apply_cheats_host_locked();
    cheats_.mark_clean();
    return;
  }
  auto &ext = core_.ext;
  if (ext.cheat_clear)
    ext.cheat_clear();
  for (size_t i = 0; i < cheats_.size(); i++) {
    const CheatEntry &e = cheats_.items()[i];
    int32_t idx = -1;
    if (!e.enabled) {
      cheats_.set_core_index(i, -1);
      continue;
    }
    char buf[32];
    snprintf(buf, sizeof(buf), "%s", e.code.c_str());
    switch (e.decoded.kind) {
    case kCheatGameGenie:
      if (ext.cheat_add_gg)
        idx = ext.cheat_add_gg(e.desc.c_str(), buf);
      break;
    case kCheatPar:
    case kCheatAR:
      if (ext.cheat_add_par)
        idx = ext.cheat_add_par(e.desc.c_str(), buf);
      break;
    default:
      break;
    }
    if (idx < 0 && ext.cheat_add_raw) {
      idx = ext.cheat_add_raw(e.desc.c_str(), e.decoded.address, e.decoded.value,
                              e.decoded.compare, e.decoded.core_type);
    }
    cheats_.set_core_index(i, idx);
  }
  cheats_.mark_clean();
}

/* Fallback for cores without a cheat engine: poke the value every frame. This is
 * FCEUmm's type-0 ("replace") behaviour; its type-1 ("substitute") cheats patch
 * the value a read returns instead, which is why the native path and this one can
 * disagree about a cheat that targets an address the emulated program writes.
 * The native path wins whenever the core offers one, so the difference is only
 * visible on cores with no cheat API at all. */
void EmuHost::apply_cheats_host_locked() {
  if (cheats_native_ || cheats_.items().empty() || cheats_.dirty())
    return; /* dirty means sync_cheats_locked() owns this frame */
  for (const auto &e : cheats_.items()) {
    if (!e.enabled)
      continue;
    if (e.decoded.compare >= 0) {
      uint32_t cur = mem_peek_locked(e.decoded.address);
      if ((cur & 0xFFu) != uint32_t(e.decoded.compare))
        continue;
    }
    mem_poke_locked(e.decoded.address, e.decoded.value);
  }
}

/* -------------------------------------------------------------------- memory
 * Peek/poke is a *memory view*, not a bus cycle: it reads the buffers the core
 * exposes (work RAM, battery RAM, and anything the core's own memory map
 * publishes) rather than ticking the 6502. That is the right trade for a cheat
 * editor, a RAM viewer and a save-state inspector — it is what RetroArch does
 * for the same three features — and it is why reads of I/O registers and of
 * bank-switched PRG report "no memory here" instead of inventing a value.
 */
uint8_t *EmuHost::mem_host_ptr(uint32_t addr) const {
  if (core_.get_memory_data) {
    if (addr < 0x2000u) {
      /* The 2 KB of internal work RAM mirrors four times below $2000. */
      uint8_t *ram = static_cast<uint8_t *>(core_.get_memory_data(RETRO_MEMORY_SYSTEM_RAM));
      size_t n = core_.get_memory_size ? core_.get_memory_size(RETRO_MEMORY_SYSTEM_RAM) : 0;
      if (ram && n)
        return ram + ((addr & 0x7FFu) % n);
    }
    if (addr >= 0x6000u && addr < 0x8000u) {
      uint8_t *sram = static_cast<uint8_t *>(core_.get_memory_data(RETRO_MEMORY_SAVE_RAM));
      size_t n = core_.get_memory_size ? core_.get_memory_size(RETRO_MEMORY_SAVE_RAM) : 0;
      if (sram && n)
        return sram + ((addr - 0x6000u) % n);
    }
  }
  for (const auto &d : mem_map_) {
    if (d.select)
      continue; /* banked view; only the core can resolve it */
    uint64_t a = uint64_t(addr) & ~uint64_t(d.disconnect);
    if (a >= uint64_t(d.start) && a < uint64_t(d.start) + uint64_t(d.len))
      return static_cast<uint8_t *>(d.ptr) + d.offset + (a - uint64_t(d.start));
  }
  return nullptr;
}

uint32_t EmuHost::mem_peek_locked(uint32_t addr) const {
  const uint8_t *p = mem_host_ptr(addr);
  return p ? uint32_t(*p) : 0u;
}

bool EmuHost::mem_poke_locked(uint32_t addr, uint32_t value) {
  uint8_t *p = mem_host_ptr(addr);
  if (!p)
    return false;
  *p = uint8_t(value & 0xFFu);
  return true;
}

uint32_t EmuHost::mem_peek(uint32_t addr) {
  std::lock_guard<std::mutex> lk(core_mu_);
  return mem_peek_locked(addr);
}

mb_status EmuHost::mem_poke(uint32_t addr, uint32_t value) {
  std::lock_guard<std::mutex> lk(core_mu_);
  return mem_poke_locked(addr, value) ? MB_OK : MB_ERR_INVALID;
}

mb_status EmuHost::mem_read(uint32_t addr, uint8_t *out, uint32_t len) {
  if (!out)
    return MB_ERR_INVALID;
  std::lock_guard<std::mutex> lk(core_mu_);
  for (uint32_t i = 0; i < len; i++) {
    const uint8_t *p = mem_host_ptr(addr + i);
    out[i] = p ? *p : 0;
  }
  return MB_OK;
}

mb_status EmuHost::mem_write(uint32_t addr, const uint8_t *in, uint32_t len) {
  if (!in)
    return MB_ERR_INVALID;
  std::lock_guard<std::mutex> lk(core_mu_);
  uint32_t done = 0;
  for (uint32_t i = 0; i < len; i++)
    if (mem_poke_locked(addr + i, in[i]))
      done++;
  /* A partially mapped range is a caller error worth surfacing: the cheat
   * editor uses bulk writes to seed a RAM patch and must not report success
   * when half of it landed nowhere. */
  return done == len ? MB_OK : MB_ERR_INVALID;
}

/* ------------------------------------------------------------------- options */
mb_status EmuHost::option_set(const std::string &key, const std::string &value) {
  for (auto &kv : options_) {
    if (kv.first == key) {
      if (kv.second == value)
        return MB_OK;
      kv.second = value;
      vars_dirty_ = true;
      job_cv_.notify_all();
      return MB_OK;
    }
  }
  options_.emplace_back(key, value);
  vars_dirty_ = true;
  return MB_OK;
}

mb_status EmuHost::option_get(const std::string &key, char *out, size_t cap) const {
  for (const auto &kv : options_) {
    if (kv.first == key) {
      snprintf(out, cap, "%s", kv.second.c_str());
      return MB_OK;
    }
  }
  out[0] = 0;
  return MB_ERR_INVALID;
}

int32_t EmuHost::option_count() const {
  if (!opt_defs_v1_)
    return 0;
  int32_t n = 0;
  while (opt_defs_v1_[n].key)
    n++;
  return n;
}

mb_status EmuHost::option_info(int32_t index, mb_option *out) const {
  if (!out || index < 0)
    return MB_ERR_INVALID;
  memset(out, 0, sizeof(*out));
  out->struct_size = sizeof(mb_option);
  if (!opt_defs_v1_) {
    if (size_t(index) >= options_.size())
      return MB_ERR_INVALID;
    snprintf(out->key, sizeof(out->key), "%s", options_[size_t(index)].first.c_str());
    snprintf(out->desc, sizeof(out->desc), "%s", options_[size_t(index)].first.c_str());
    snprintf(out->defaults[0], sizeof(out->defaults[0]), "%s",
             options_[size_t(index)].second.c_str());
    snprintf(out->values[0], sizeof(out->values[0]), "%s",
             options_[size_t(index)].second.c_str());
    out->count = 1;
    return MB_OK;
  }
  int32_t n = 0;
  while (opt_defs_v1_[n].key)
    n++;
  if (index >= n)
    return MB_ERR_INVALID;
  const struct retro_core_option_definition &d = opt_defs_v1_[index];
  snprintf(out->key, sizeof(out->key), "%s", d.key ? d.key : "");
  snprintf(out->desc, sizeof(out->desc), "%s", d.desc ? d.desc : (d.info ? d.info : ""));
  for (int i = 0; i < RETRO_NUM_CORE_OPTION_VALUES_MAX && i < 16 && d.values[i].value; i++) {
    snprintf(out->values[i], sizeof(out->values[i]), "%s",
             d.values[i].label ? d.values[i].label : d.values[i].value);
    if (d.default_value && !strcmp(d.values[i].value, d.default_value))
      out->default_index = i;
  }
  while (out->count < 16 && out->values[out->count][0])
    out->count++;
  char cur[64];
  if (option_get(out->key, cur, sizeof(cur)) == MB_OK && cur[0])
    snprintf(out->defaults[0], sizeof(out->defaults[0]), "%s", cur);
  else
    snprintf(out->defaults[0], sizeof(out->defaults[0]), "%s", d.default_value ? d.default_value : "");
  return MB_OK;
}

mb_status EmuHost::stats(mb_stats *out) const {
  if (!out)
    return MB_ERR_INVALID;
  memset(out, 0, sizeof(*out));
  out->struct_size = sizeof(mb_stats);
  out->frames_emulated = frames_emulated_.load();
  out->frames_presented = frames_presented_;
  out->frames_skipped = frames_skipped_;
  out->input_frames = input_frames_.load();
  out->present_fps = present_fps_;
  out->emu_speed = speed_;
  out->video_width = frame_w_;
  out->video_height = frame_h_;
  out->video_pitch = frame_pitch_;
  out->base_width = base_w_;
  out->base_height = base_h_;
  out->aspect_ratio = aspect_;
  out->sample_rate = sample_rate_;
  out->audio_samples_written = audio_written_.load();
  out->audio_underruns = audio_ ? audio_->underruns() : 0;
  out->rewind_frames_available = rewind_.count() > 1 ? (rewind_.count() - 1) : 0;
  out->rewind_capacity_frames = rewind_.capacity();
  out->rewind_bytes = rewind_.bytes();
  out->state_size = uint32_t(state_size_);
  out->sram_size = uint32_t(sram_size_);
  out->paused = paused_.load() ? 1 : 0;
  out->running = running_.load() ? 1 : 0;
  out->audio_enabled = audio_ ? 1 : 0;
  out->pal = pal_ ? 1 : 0;
  out->ms_per_frame = ms_per_frame_;
  out->core_cpu_percent = cpu_percent_;
  return MB_OK;
}

void EmuHost::set_event_callback(mb_event_fn fn, void *user) {
  event_cb_ = fn;
  event_user_ = user;
}

void EmuHost::emit(uint32_t event_id, const std::string &text) {
  if (event_cb_)
    event_cb_(event_user_, event_id, text.c_str());
}

/* ---------------------------------------------------------- libretro: input */
void EmuHost::input_poll_cb(void) {
  EmuHost *h = self();
  if (!h)
    return;
  /* One snapshot per frame: the core queries the same pad hundreds of times
   * and must never see it change mid-frame. */
  for (uint32_t p = 0; p < 5; p++) {
    uint32_t v = h->input_[p].load();
    const Turbo &t = h->turbo_[p];
    if (t.on) {
      /* Turbo alternates the held bits at hz; a 60 Hz base makes that a
       * simple frame parity test scaled by the requested rate. */
      uint32_t period = t.hz > 0 ? uint32_t(60.0 / double(t.hz) + 0.5) : 2;
      if (period < 2)
        period = 2;
      if ((h->frames_emulated_.load() % period) == 0)
        v |= t.mask;
      else
        v &= ~t.mask;
    }
    h->poll_cache_[p] = v & 0xFFFFu;
  }
  h->poll_valid_ = true;
}

int16_t EmuHost::input_state_cb(unsigned port, unsigned device, unsigned index, unsigned id) {
  EmuHost *h = self();
  if (!h || port >= 5)
    return 0;
  uint32_t v = h->poll_valid_ ? h->poll_cache_[port] : h->input_[port].load();
  if (device == RETRO_DEVICE_JOYPAD) {
    if (id == RETRO_DEVICE_ID_JOYPAD_MASK)
      return int16_t(v & 0xFFFFu);
    return (v & (1u << id)) ? 1 : 0;
  }
  (void)index;
  return 0;
}

/* ---------------------------------------------------------- libretro: video */
void EmuHost::video_cb(const void *data, unsigned width, unsigned height, size_t pitch) {
  EmuHost *h = self();
  if (!h || !data)
    return; /* NULL = frame dup: keep the previous image */
  h->fill_frame_rgba(data, width, height, pitch);
}

void EmuHost::fill_frame_rgba(const void *src, unsigned w, unsigned h, size_t pitch) {
  std::lock_guard<std::mutex> lk(frame_mu_);
  if (!w || !h || w > 1024 || h > 1024)
    return;
  uint32_t need_pitch = w * 4u;
  bool resized = false;
  if (frame_w_ != w || frame_h_ != h || frame_pitch_ != need_pitch) {
    frame_w_ = w;
    frame_h_ = h;
    frame_pitch_ = need_pitch;
    frame_.assign(size_t(need_pitch) * h, 0);
    resized = true;
  }
  const uint8_t *s = static_cast<const uint8_t *>(src);
  switch (pixel_format_) {
  case RETRO_PIXEL_FORMAT_XRGB8888: {
    size_t row = size_t(w) * 4u;
    if (pitch == row) {
      memcpy(frame_.data(), s, row * h);
    } else {
      for (unsigned y = 0; y < h; y++)
        memcpy(frame_.data() + size_t(y) * row, s + size_t(y) * pitch, row);
    }
    uint8_t *p = frame_.data();
    for (size_t i = 3; i < frame_.size(); i += 4)
      p[i] = 0xFF; /* XRGB carries no alpha; our sampler expects opaque */
    break;
  }
  case RETRO_PIXEL_FORMAT_RGB565: {
    for (unsigned y = 0; y < h; y++) {
      const uint16_t *row = reinterpret_cast<const uint16_t *>(s + size_t(y) * pitch);
      uint8_t *dst = frame_.data() + size_t(y) * frame_pitch_;
      for (unsigned x = 0; x < w; x++) {
        uint16_t c = row[x];
        dst[x * 4 + 0] = uint8_t(((c >> 11) & 31u) * 255u / 31u);
        dst[x * 4 + 1] = uint8_t(((c >> 5) & 63u) * 255u / 63u);
        dst[x * 4 + 2] = uint8_t((c & 31u) * 255u / 31u);
        dst[x * 4 + 3] = 0xFF;
      }
    }
    break;
  }
  default: { /* 0RGB1555, the ABI default */
    for (unsigned y = 0; y < h; y++) {
      const uint16_t *row = reinterpret_cast<const uint16_t *>(s + size_t(y) * pitch);
      uint8_t *dst = frame_.data() + size_t(y) * frame_pitch_;
      for (unsigned x = 0; x < w; x++) {
        uint16_t c = uint16_t(row[x] & 0x7FFFu);
        dst[x * 4 + 0] = uint8_t(((c >> 10) & 31u) * 255u / 31u);
        dst[x * 4 + 1] = uint8_t(((c >> 5) & 31u) * 255u / 31u);
        dst[x * 4 + 2] = uint8_t((c & 31u) * 255u / 31u);
        dst[x * 4 + 3] = 0xFF;
      }
    }
    break;
  }
  }
  frame_ready_ = true;
  if (resized)
    emit(MB_EVENT_GEOMETRY, std::to_string(w) + "x" + std::to_string(h));
}

/* ---------------------------------------------------------- libretro: audio */
void EmuHost::audio_cb_single(int16_t left, int16_t right) {
  EmuHost *h = self();
  if (!h)
    return;
  const int16_t pair[2] = { left, right };
  /* Fast forward and slow motion drop audio rather than time-stretching it:
   * stretching a NES waveform in software sounds worse than silence, and the
   * pacing loop would otherwise stall on an underrun it cannot fill. */
  if (!h->audio_ || h->ff_.load() || h->slowmo_.load()) {
    h->audio_written_.fetch_add(1);
    return;
  }
  h->audio_->write(pair, 1);
  h->audio_written_.fetch_add(1);
}

size_t EmuHost::audio_cb_batch(const int16_t *data, size_t frames) {
  EmuHost *h = self();
  if (!h || !frames)
    return frames;
  if (!h->audio_ || h->ff_.load() || h->slowmo_.load() || !data) {
    h ? h->audio_written_.fetch_add(frames) : 0;
    return frames;
  }
  size_t used = h->audio_->write(data, frames);
  h->audio_written_.fetch_add(used ? used : frames);
  return frames;
}

/* The core's log callback is variadic, and a variadic lambda cannot decay to a
 * function pointer (GCC: "sorry, unimplemented"), so this is a plain static
 * function with C varargs. */
static void core_log_printf(enum retro_log_level level, const char *fmt, ...) {
  char buf[1024];
  va_list ap;
  va_start(ap, fmt);
  vsnprintf(buf, sizeof(buf), fmt, ap);
  va_end(ap);
  int lvl = level == RETRO_LOG_ERROR    ? kError
            : level == RETRO_LOG_WARN   ? kWarn
            : level == RETRO_LOG_INFO   ? kInfo
                                        : kVerbose;
  log_write(lvl, "[core] %s", buf);
}

/* -------------------------------------------------------- libretro: environ */
bool EmuHost::env_cb(unsigned cmd, void *data) {
  EmuHost *h = self();
  return h ? h->env_dispatch(cmd, data) : false;
}

bool EmuHost::env_dispatch(unsigned cmd, void *data) {
  switch (cmd) {
  case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT:
    if (!data)
      return false;
    pixel_format_ = *static_cast<enum retro_pixel_format *>(data);
    /* There is no way to ask a core for a different format: the core decides
     * and tells us here, from inside retro_set_environment()/load_game(). A
     * core that saw FRONTEND_SUPPORTS_ARGB888 (every core we ship) picks
     * XRGB8888; if one ever insists on the deprecated 15-bit format we accept
     * it and convert in fill_frame_rgba() rather than lie about support. */
    MB_LOGI("pixel format = %u", pixel_format_);
    return true;

  case RETRO_ENVIRONMENT_GET_CAN_DUPE:
    if (data)
      *static_cast<bool *>(data) = true;
    return true;

  case RETRO_ENVIRONMENT_GET_VARIABLE: {
    if (!data)
      return false;
    auto *v = static_cast<struct retro_variable *>(data);
    for (auto &kv : options_) {
      if (v->key && kv.first == v->key) {
        v->value = kv.second.c_str();
        return true;
      }
    }
    v->value = nullptr;
    return true; /* "no value set" is a supported answer, not an error */
  }

  case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:
    if (data)
      *static_cast<bool *>(data) = vars_dirty_.exchange(false);
    return true;

  case RETRO_ENVIRONMENT_SET_VARIABLE: {
    if (!data)
      return false;
    auto *v = static_cast<const struct retro_variable *>(data);
    if (v->key)
      option_set(v->key, v->value ? v->value : "");
    return true;
  }

  case RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION:
    /* 1 = the flat retro_core_option_definition list we can render and feed
     * back through GET_VARIABLE. Refusing 2 makes the core downgrade. */
    if (data)
      *static_cast<unsigned *>(data) = 1;
    return true;

  case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_DISPLAY:
    /* The core hides its advanced options behind a master switch. Accepted and
     * otherwise ignored: the settings screen shows everything, because honouring
     * the hiding would leave an option unreachable with no way back. */
    return true;

  case RETRO_ENVIRONMENT_SET_CORE_OPTIONS: {
    /* Deep copy, strings included: the core frees this array the moment the call
     * returns. Seeding `options_` from the copy (not the core's memory) also gives
     * GET_VARIABLE the documented default for options nobody has touched, which is
     * what a settings screen needs to show before the first change. */
    auto *src = static_cast<const struct retro_core_option_definition *>(data);
    opt_defs_v1_ = nullptr;
    opt_defs_owned_.clear();
    opt_strs_.clear();
    if (src) {
      for (int i = 0; src[i].key; i++) {
        const struct retro_core_option_definition &s = src[i];
        struct retro_core_option_definition d {};
        auto dup = [this](const char *v) -> const char * {
          if (!v)
            return nullptr;
          opt_strs_.emplace_back(v);
          return opt_strs_.back().c_str();
        };
        d.key = dup(s.key);
        d.desc = dup(s.desc);
        d.info = dup(s.info);
        d.default_value = dup(s.default_value);
        for (int v = 0; v < RETRO_NUM_CORE_OPTION_VALUES_MAX && s.values[v].value; v++) {
          d.values[v].value = dup(s.values[v].value);
          d.values[v].label = dup(s.values[v].label);
        }
        opt_defs_owned_.push_back(d);
        if (d.default_value) {
          bool known = false;
          for (auto &kv : options_)
            if (kv.first == d.key)
              known = true;
          if (!known)
            options_.emplace_back(d.key, d.default_value);
        }
      }
      if (!opt_defs_owned_.empty()) {
        /* Terminated by an entry with a null key, the same convention the core's
         * own array uses, so option_count()/option_info() stay simple. */
        opt_defs_owned_.push_back(retro_core_option_definition {});
        opt_defs_v1_ = opt_defs_owned_.data();
      }
    }
    return true;
  }

  case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2:
  case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2_INTL:
  case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_INTL:
    return false;

  case RETRO_ENVIRONMENT_SET_GEOMETRY: {
    if (!data)
      return false;
    auto *g = static_cast<struct retro_game_geometry *>(data);
    if (g->base_width)
      base_w_ = g->base_width;
    if (g->base_height)
      base_h_ = g->base_height;
    if (g->max_width)
      max_w_ = g->max_width;
    if (g->max_height)
      max_h_ = g->max_height;
    if (g->aspect_ratio > 0)
      aspect_ = g->aspect_ratio;
    emit(MB_EVENT_GEOMETRY, std::to_string(base_w_) + "x" + std::to_string(base_h_));
    return true;
  }

  case RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO: {
    if (!data)
      return false;
    auto *av = static_cast<struct retro_system_av_info *>(data);
    base_w_ = av->geometry.base_width;
    base_h_ = av->geometry.base_height;
    max_w_ = av->geometry.max_width;
    max_h_ = av->geometry.max_height;
    aspect_ = av->geometry.aspect_ratio ? av->geometry.aspect_ratio
                                       : double(base_w_) / double(base_h_ ? base_h_ : 1);
    fps_ = av->timing.fps > 1.0 ? av->timing.fps : (pal_ ? kPalFps : kNtscFps);
    if (av->timing.sample_rate > 1000.0)
      sample_rate_ = uint32_t(av->timing.sample_rate);
    return true;
  }

  case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
    if (!data)
      return false;
    *static_cast<const char **>(data) =
        system_dir_.empty() ? save_dir_.c_str() : system_dir_.c_str();
    return true;

  case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
    if (!data)
      return false;
    *static_cast<const char **>(data) = save_dir_.c_str();
    return true;

  case RETRO_ENVIRONMENT_GET_LOG_INTERFACE: {
    if (!data)
      return false;
    static const struct retro_log_callback cb = { core_log_printf };
    *static_cast<struct retro_log_callback *>(data) = cb;
    return true;
  }

  case RETRO_ENVIRONMENT_GET_INPUT_BITMASKS:
    if (data)
      *static_cast<bool *>(data) = true;
    return true;

  case RETRO_ENVIRONMENT_SET_MEMORY_MAPS: {
    /* The core's descriptor array is a stack local inside its own function, so
     * copy it. Descriptors with a nonzero `select` are banked views: resolving
     * them needs the core's bank state, which we do not have, so peek/poke
     * skips those and the core keeps its own cheat engine for ROM patches. */
    auto *m = static_cast<struct retro_memory_map *>(data);
    mem_map_.clear();
    if (m && m->descriptors) {
      for (unsigned i = 0; i < m->num_descriptors; i++)
        if (m->descriptors[i].ptr && m->descriptors[i].len)
          mem_map_.push_back(m->descriptors[i]);
    }
    return true;
  }

  case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS:
    return true;

  case RETRO_ENVIRONMENT_SET_MESSAGE: {
    if (!data)
      return false;
    auto *m = static_cast<const struct retro_message *>(data);
    emit(MB_EVENT_MESSAGE, m->msg ? m->msg : "");
    return true;
  }

  case RETRO_ENVIRONMENT_GET_MESSAGE_INTERFACE_VERSION:
    /* Any value other than 0 selects the extended message API; 0 keeps the
     * core on SET_MESSAGE, which is the one we route to the UI. */
    if (data)
      *static_cast<unsigned *>(data) = 0;
    return true;

  case RETRO_ENVIRONMENT_GET_LANGUAGE:
    if (data)
      *static_cast<unsigned *>(data) = RETRO_LANGUAGE_ENGLISH;
    return true;

  case RETRO_ENVIRONMENT_SET_SUPPORT_ACHIEVEMENTS:
  case RETRO_ENVIRONMENT_GET_PERF_INTERFACE:
  case RETRO_ENVIRONMENT_GET_VFS_INTERFACE:
  case RETRO_ENVIRONMENT_GET_HW_RENDER_INTERFACE:
  case RETRO_ENVIRONMENT_GET_RUMBLE_INTERFACE:
  case RETRO_ENVIRONMENT_SET_DISK_CONTROL_INTERFACE:
    return false;

  default:
    return false;
  }
}

/* ------------------------------------------------------------ load / unload */
mb_status EmuHost::do_load() {
  /* Reset before installing the callbacks, not after: a core reports its pixel
   * format and its option table from inside retro_set_environment(), so
   * anything we wrote afterwards would clobber the core's answer. */
  pixel_format_ = RETRO_PIXEL_FORMAT_XRGB8888;
  opt_defs_v1_ = nullptr;
  opt_defs_owned_.clear();
  opt_strs_.clear();
  core_.set_environment(env_cb);
  core_.set_video_refresh(video_cb);
  if (core_.set_audio_sample_batch)
    core_.set_audio_sample_batch(audio_cb_batch);
  if (core_.set_audio_sample)
    core_.set_audio_sample(audio_cb_single);
  core_.set_input_poll(input_poll_cb);
  core_.set_input_state(input_state_cb);
  if (core_.init)
    core_.init();

  struct retro_game_info gi {};
  gi.path = rom_path_.c_str();
  gi.data = nullptr;
  gi.size = 0;
  gi.meta = nullptr;
  if (!core_.load_game(&gi)) {
    error_ = "core rejected the ROM (" + rom_path_ + ")";
    return MB_ERR_NO_ROM;
  }

  struct retro_system_av_info av {};
  core_.get_system_av_info(&av);
  base_w_ = av.geometry.base_width ? av.geometry.base_width : 256;
  base_h_ = av.geometry.base_height ? av.geometry.base_height : 240;
  max_w_ = av.geometry.max_width ? av.geometry.max_width : base_w_;
  max_h_ = av.geometry.max_height ? av.geometry.max_height : base_h_;
  aspect_ = av.geometry.aspect_ratio ? av.geometry.aspect_ratio
                                     : double(base_w_) / double(base_h_ ? base_h_ : 1);
  pal_ = core_.get_region ? core_.get_region() == RETRO_REGION_PAL : false;
  fps_ = av.timing.fps > 1.0 ? av.timing.fps : (pal_ ? kPalFps : kNtscFps);
  if (av.timing.sample_rate > 1000.0)
    sample_rate_ = uint32_t(av.timing.sample_rate);

  if (core_.get_memory_data && core_.get_memory_size) {
    sram_ptr_ = static_cast<uint8_t *>(core_.get_memory_data(RETRO_MEMORY_SAVE_RAM));
    sram_size_ = core_.get_memory_size(RETRO_MEMORY_SAVE_RAM);
    if (cfg_.sram_enable && sram_ptr_ && sram_size_) {
      std::string p = sram_path_for(save_dir_, rom_path_);
      std::vector<uint8_t> disk;
      if (sram_load(p, &disk)) {
        if (disk.size() == sram_size_) {
          memcpy(sram_ptr_, disk.data(), sram_size_);
          MB_LOGI("sram restored: %s (%zu bytes)", p.c_str(), sram_size_);
          /* Deliberately no reset here. FCEUmm's retro_reset only *queues* a
           * command (FCEU_QSimpleCommand) that runs at the next frame boundary, and
           * that reset re-initialises battery RAM — so resetting after the copy
           * would throw the save away. This matches how the core is driven from
           * RetroArch, which also fills RETRO_MEMORY_SAVE_RAM after
           * retro_load_game(): a title that wants its progress reads it while
           * playing, not from the 6502 reset code. */
        } else {
          MB_LOGW("sram size %zu != %zu — starting fresh", disk.size(), sram_size_);
        }
        sram_disk_ = disk;
      } else {
        sram_dirty_ = true; /* treat a missing file as "game may write to it" */
      }
    }
  }

  state_size_ = core_.serialize_size ? core_.serialize_size() : 0;
  play_ms_ = 0;
  play_started_ms_ = uint64_t(now_ns() / 1000000ull);

  if (cfg_.audio_enable) {
    audio_ = audio_create();
    if (audio_) {
      if (!audio_->open(sample_rate_, 2, uint32_t(double(sample_rate_) / 30.0))) {
        MB_LOGW("audio open failed — running muted");
        audio_destroy(audio_);
        audio_ = nullptr;
      } else {
        audio_->start();
        MB_LOGI("audio backend %s @ %u Hz", audio_->backend_name(), sample_rate_);
      }
    }
  } else {
    audio_ = nullptr;
  }

  if (surface_)
    surface_->apply(render_cfg_);
  if (cheats_.size() && cheats_native_)
    sync_cheats_locked();
  emit(MB_EVENT_GAME_INFO, core_name_ + " " + std::to_string(base_w_) + "x" +
                               std::to_string(base_h_) + " " + (pal_ ? "PAL" : "NTSC"));
  MB_LOGI("loaded: %s %ux%u→%ux%u fps=%.4f sr=%u state=%zu sram=%zu", core_name_.c_str(),
          base_w_, base_h_, max_w_, max_h_, fps_, sample_rate_, state_size_, sram_size_);
  return MB_OK;
}

void EmuHost::do_unload() {
  if (audio_) {
    audio_->drain();
    audio_->stop();
    audio_->close();
    audio_destroy(audio_);
    audio_ = nullptr;
  }
  if (sram_ptr_ && sram_size_ && cfg_.sram_enable && sram_dirty_)
    sram_flush_locked();
  if (core_.unload_game)
    core_.unload_game();
  state_size_ = 0;
  sram_ptr_ = nullptr;
  sram_size_ = 0;
  sram_dirty_ = false;
  opt_defs_v1_ = nullptr;
  mem_map_.clear();
  poll_valid_ = false;
}

/* ------------------------------------------------------------------- pacing */
double EmuHost::frame_interval() const {
  double base = 1.0 / (fps_ > 1.0 ? fps_ : kNtscFps);
  if (ff_)
    return base / 4.0;
  if (slowmo_)
    return base * 2.0;
  return base;
}

/* ----------------------------------------------------------------- main loop */
void EmuHost::thread_main() {
  emu_tid_ = std::this_thread::get_id();
  active_ = this;

  mb_status ls = do_load();
  if (ls != MB_OK) {
    loaded_ = false;
    running_ = false;
    emit(MB_EVENT_ERROR, error_.empty() ? "load failed" : error_);
    active_ = nullptr;
    return;
  }
  loaded_ = true;

  double next = now_seconds();
  double win_start = next;
  uint64_t win_frames = 0;
  double busy = 0;

  while (!quit_.load()) {
    for (;;) {
      Job j;
      {
        std::lock_guard<std::mutex> lk(job_mu_);
        if (jobs_.empty())
          break;
        j = std::move(jobs_.front());
        jobs_.pop_front();
      }
      j();
    }

    bool want_step = step_once_.exchange(false);
    if (paused_.load() && !want_step) {
      std::unique_lock<std::mutex> lk(job_mu_);
      job_cv_.wait_for(lk, std::chrono::milliseconds(120), [&] {
        return !jobs_.empty() || !paused_.load() || step_once_.load() || quit_.load();
      });
      continue;
    }

    double t0 = now_seconds();
    {
      std::lock_guard<std::mutex> lk(core_mu_);
      if (cheats_.dirty())
        sync_cheats_locked();
      else
        apply_cheats_host_locked();
      core_.run();
      frames_emulated_.fetch_add(1);
      capture_rewind_locked();
      if (((frames_emulated_.load() & 63) == 0) && cfg_.sram_enable && sram_ptr_ && sram_size_) {
        if (sram_disk_.size() != sram_size_)
          sram_disk_.assign(sram_size_, 0);
        if (memcmp(sram_disk_.data(), sram_ptr_, sram_size_) != 0) {
          sram_dirty_ = true;
          sram_flush_locked();
        }
      }
    }
    double t1 = now_seconds();
    busy += t1 - t0;
    ms_per_frame_ = uint32_t((t1 - t0) * 1000.0);

    bool present = video_enabled_.load() && surface_ && (!ff_.load() || (frames_emulated_.load() & 3u) == 0);
    if (present) {
      std::lock_guard<std::mutex> lk(frame_mu_);
      if (frame_ready_.load() && frame_h_) {
        FrameView fv{frame_.data(), frame_w_, frame_h_, frame_pitch_, base_w_, base_h_, aspect_};
        surface_->present(fv);
        frames_presented_++;
        win_frames++;
      }
    } else {
      frames_skipped_++;
    }

    /* Pacing. A real audio device already brakes the loop from inside
     * retro_run(); the sleep only fills whatever time is left, so a device
     * that drains faster than we produce can never turn this into a spin.
     * With no device and no window at all (desktop tests, headless import)
     * there is nothing to keep time with and sleeping would only waste power. */
    bool paced = (audio_ && !audio_->is_null()) || (video_enabled_.load() && surface_);
    double interval = frame_interval();
    next += interval;
    double now = now_seconds();
    if (paced) {
      if (next < now - interval * 2.0)
        next = now; /* resync after a stall (seek, GC, screen off) */
      else if (next - now > 0.5)
        next = now; /* returning from pause: do not burst to catch up */
      if (next > now)
        sleep_ns(uint64_t((next - now) * 1e9));
    } else {
      next = now;
    }

    if (now - win_start >= 1.0) {
      present_fps_ = double(win_frames) / (now - win_start);
      speed_ = present_fps_ > 0 ? present_fps_ / double(fps_) : 1.0;
      double window = now - win_start;
      cpu_percent_ = uint32_t(std::min(1.0, busy / window) * 100.0);
      win_start = now;
      win_frames = 0;
      busy = 0;
    }
    if (want_step)
      paused_ = true;
  }

  if (play_started_ms_)
    play_ms_ += uint64_t(now_ns() / 1000000ull) - play_started_ms_;
  play_started_ms_ = 0;
  do_unload();
  running_ = false;
  active_ = nullptr;
  MB_LOGI("emulation thread stopped after %llu frames",
          (unsigned long long)frames_emulated_.load());
}

/* --------------------------------------------------------------------- jobs */
template <typename F> mb_status EmuHost::post(F fn) {
  bool on_emu_thread = std::this_thread::get_id() == emu_tid_;
  if (on_emu_thread || !running_.load() || !loaded_.load()) {
    if (running_.load() && loaded_.load() && !on_emu_thread)
      return MB_ERR_BUSY;
    return run_now(fn);
  }
  auto pkg = std::make_shared<std::packaged_task<mb_status()>>(std::move(fn));
  std::future<mb_status> fut = pkg->get_future();
  {
    std::lock_guard<std::mutex> lk(job_mu_);
    jobs_.emplace_back([pkg] { (*pkg)(); });
  }
  job_cv_.notify_one();
  if (fut.wait_for(std::chrono::seconds(15)) == std::future_status::timeout) {
    error_ = "emulation thread did not answer within 15 s";
    return MB_ERR_BUSY;
  }
  return fut.get();
}

mb_status EmuHost::run_now(const std::function<mb_status()> &fn) {
  std::lock_guard<std::mutex> lk(core_mu_);
  EmuHost *prev = active_;
  active_ = this; /* callbacks fired by the core must find us */
  mb_status s = fn();
  active_ = prev;
  return s;
}


} /* namespace mb */
