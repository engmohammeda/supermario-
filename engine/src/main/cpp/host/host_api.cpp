/* host_api.cpp — the C ABI declared in mb_abi.h.
 *
 * Thin by design: every function is a null-check plus a delegation to
 * EmuHost, so the surface the JNI layer (and the tests) bind to never drifts
 * from the C++ semantics. Errors are returned as codes and mirrored into a
 * per-handle message that Kotlin reads back for the user.
 */
#include "mb_abi.h"

#include "cheats.h"
#include "host.h"
#include "mb_common.h"

#include <cstdlib>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

struct mb_handle {
  mb::EmuHost host;
};

namespace {

std::mutex g_reg_mutex;
std::vector<mb_core_factory> g_registry;

#define MB_GUARD(handle)                                                                         \
  if (!handle)                                                                                   \
  return MB_ERR_INVALID;                                                                         \
  mb::EmuHost &_h = static_cast<mb_handle *>(handle)->host

} /* namespace */

extern "C" {

/* ------------------------------------------------------------ lifecycle */
mb_status mb_create(const mb_config *cfg, void **out_handle) {
  if (!cfg || !out_handle)
    return MB_ERR_INVALID;
  *out_handle = nullptr;
  mb_handle *h = new (std::nothrow) mb_handle();
  if (!h)
    return MB_ERR_NOMEM;
  mb_status s = h->host.configure(*cfg);
  if (s != MB_OK) {
    delete h;
    return s;
  }
  *out_handle = h;
  return MB_OK;
}

void mb_destroy(void *handle) {
  if (!handle)
    return;
  delete static_cast<mb_handle *>(handle);
}

mb_status mb_start(void *handle) {
  MB_GUARD(handle);
  return _h.start();
}
mb_status mb_stop(void *handle) {
  MB_GUARD(handle);
  return _h.stop();
}
int mb_is_running(const void *handle) {
  if (!handle)
    return 0;
  return const_cast<mb::EmuHost &>(static_cast<const mb_handle *>(handle)->host).running() ? 1 : 0;
}
void mb_set_paused(void *handle, int paused) {
  if (!handle)
    return;
  static_cast<mb_handle *>(handle)->host.set_paused(paused != 0);
}
int mb_is_paused(const void *handle) {
  if (!handle)
    return 1;
  return const_cast<mb::EmuHost &>(static_cast<const mb_handle *>(handle)->host).paused() ? 1 : 0;
}

/* -------------------------------------------------------------- surface */
mb_status mb_set_surface(void *handle, void *native_window) {
  MB_GUARD(handle);
  return _h.set_surface(native_window);
}
mb_status mb_set_surface_size(void *handle, int32_t w, int32_t h) {
  MB_GUARD(handle);
  return _h.set_surface_size(w, h);
}
void mb_set_video_enabled(void *handle, int enabled) {
  if (!handle)
    return;
  static_cast<mb_handle *>(handle)->host.set_video_enabled(enabled != 0);
}
void mb_set_render_config(void *handle, int scale_mode, int filter_mode, int scanlines,
                          int overscan, int rotation) {
  if (!handle)
    return;
  mb::RenderConfig cfg;
  cfg.scale_mode = scale_mode;
  cfg.filter_mode = filter_mode;
  cfg.scanlines_percent = scanlines;
  cfg.overscan_crop = overscan;
  cfg.rotation = rotation;
  static_cast<mb_handle *>(handle)->host.set_render_config(cfg);
}
mb_status mb_copy_frame(void *handle, uint8_t *out, size_t cap, uint32_t *out_w, uint32_t *out_h,
                        uint32_t *out_pitch) {
  if (!handle)
    return MB_ERR_INVALID;
  return static_cast<mb_handle *>(handle)->host.copy_frame(out, cap, out_w, out_h, out_pitch);
}

/* ---------------------------------------------------------------- input */
void mb_set_input(void *handle, uint32_t port, uint32_t bitmask) {
  if (!handle)
    return;
  static_cast<mb_handle *>(handle)->host.set_input(port, bitmask);
}
uint32_t mb_get_input(void *handle, uint32_t port) {
  if (!handle)
    return 0;
  return static_cast<mb_handle *>(handle)->host.input(port);
}
void mb_set_turbo(void *handle, uint32_t port, uint32_t mask, int hz) {
  if (!handle)
    return;
  static_cast<mb_handle *>(handle)->host.set_turbo(port, mask, hz);
}
void mb_set_fast_forward(void *handle, int enabled) {
  if (!handle)
    return;
  static_cast<mb_handle *>(handle)->host.set_fast_forward(enabled != 0);
}
void mb_set_slow_motion(void *handle, int enabled) {
  if (!handle)
    return;
  static_cast<mb_handle *>(handle)->host.set_slow_motion(enabled != 0);
}
mb_status mb_advance_frame(void *handle) {
  MB_GUARD(handle);
  return _h.advance_frame();
}

/* ----------------------------------------------------------- save states */
mb_status mb_save_state(void *handle, const char *path) {
  MB_GUARD(handle);
  if (!path)
    return MB_ERR_INVALID;
  return _h.save_state(path);
}
mb_status mb_load_state(void *handle, const char *path) {
  MB_GUARD(handle);
  if (!path)
    return MB_ERR_INVALID;
  return _h.load_state(path);
}
size_t mb_serialize_size(const void *handle) {
  if (!handle)
    return 0;
  return const_cast<mb::EmuHost &>(static_cast<const mb_handle *>(handle)->host).serialize_size();
}
mb_status mb_serialize(void *handle, uint8_t *out, size_t size) {
  MB_GUARD(handle);
  return _h.serialize_to(out, size);
}
mb_status mb_unserialize(void *handle, const uint8_t *in, size_t size) {
  MB_GUARD(handle);
  return _h.unserialize_from(in, size);
}

/* --------------------------------------------------------------- rewind */
mb_status mb_rewind_step(void *handle, uint32_t frames) {
  MB_GUARD(handle);
  return _h.rewind_step(frames);
}
mb_status mb_rewind_jump(void *handle, uint32_t frames_back) {
  MB_GUARD(handle);
  return _h.rewind_step(frames_back);
}
uint32_t mb_rewind_available(const void *handle) {
  if (!handle)
    return 0;
  return const_cast<mb::EmuHost &>(static_cast<const mb_handle *>(handle)->host)
      .rewind_available();
}
void mb_rewind_clear(void *handle) {
  if (!handle)
    return;
  static_cast<mb_handle *>(handle)->host.rewind_clear();
}
uint32_t mb_rewind_count(const void *handle) {
  if (!handle)
    return 0;
  mb_stats s;
  const_cast<mb::EmuHost &>(static_cast<const mb_handle *>(handle)->host).stats(&s);
  return s.rewind_capacity_frames ? uint32_t(s.rewind_bytes / 1024) : 0;
}

/* ----------------------------------------------------------------- sram */
mb_status mb_sram_flush(void *handle) {
  MB_GUARD(handle);
  return _h.sram_flush();
}
mb_status mb_sram_invalidate(void *handle) {
  MB_GUARD(handle);
  return _h.sram_invalidate();
}

/* --------------------------------------------------------------- cheats */
int32_t mb_cheat_add(void *handle, const mb_cheat *cheat) {
  if (!handle || !cheat)
    return -1;
  return static_cast<mb_handle *>(handle)->host.cheat_add(*cheat);
}
mb_status mb_cheat_remove(void *handle, int32_t id) {
  MB_GUARD(handle);
  return _h.cheat_remove(id);
}
mb_status mb_cheat_set_enabled(void *handle, int32_t id, int enabled) {
  MB_GUARD(handle);
  return _h.cheat_set_enabled(id, enabled != 0);
}
mb_status mb_cheat_set_value(void *handle, int32_t id, uint32_t value) {
  MB_GUARD(handle);
  return _h.cheat_set_value(id, value);
}
void mb_cheat_clear(void *handle) {
  if (!handle)
    return;
  static_cast<mb_handle *>(handle)->host.cheat_clear();
}
int32_t mb_cheat_count(const void *handle) {
  if (!handle)
    return 0;
  return static_cast<const mb_handle *>(handle)->host.cheat_count();
}
mb_status mb_cheat_get(const void *handle, int32_t index, mb_cheat *out) {
  if (!handle)
    return MB_ERR_INVALID;
  return static_cast<const mb_handle *>(handle)->host.cheat_get(index, out);
}
mb_status mb_cheat_get_core(void *handle, int32_t index, mb_cheat *out) {
  if (!handle)
    return MB_ERR_INVALID;
  return static_cast<mb_handle *>(handle)->host.cheat_core_get(index, out);
}

void mb_cheat_apply(void *handle) {
  if (!handle)
    return;
  /* Nothing to do: the emulation thread re-syncs a dirty list at the start of
   * the next frame. Kept for ABI symmetry with frontends that drive it. */
}
int mb_cheat_test(const void *handle, int32_t index) {
  if (!handle)
    return -1;
  return const_cast<mb::EmuHost &>(static_cast<const mb_handle *>(handle)->host)
      .cheat_peek_value(index);
}

mb_status mb_cheat_decode(const char *code, uint32_t *out_addr, uint32_t *out_val,
                          int32_t *out_compare, int *out_kind) {
  if (!code)
    return MB_ERR_INVALID;
  mb::DecodedCheat d = mb::cheat_decode(code, mb::kCheatAuto);
  if (!d.ok)
    return MB_ERR_INVALID;
  if (out_addr)
    *out_addr = d.address;
  if (out_val)
    *out_val = d.value;
  if (out_compare)
    *out_compare = d.compare;
  if (out_kind)
    *out_kind = d.kind;
  return MB_OK;
}

/* ---------------------------------------------------------------- memory */
uint32_t mb_mem_peek(void *handle, uint32_t address) {
  if (!handle)
    return 0;
  return static_cast<mb_handle *>(handle)->host.mem_peek(address);
}
mb_status mb_mem_poke(void *handle, uint32_t address, uint32_t value) {
  MB_GUARD(handle);
  return _h.mem_poke(address, value);
}
mb_status mb_mem_read(void *handle, uint32_t address, uint8_t *out, uint32_t len) {
  MB_GUARD(handle);
  return _h.mem_read(address, out, len);
}
mb_status mb_mem_write(void *handle, uint32_t address, const uint8_t *in, uint32_t len) {
  MB_GUARD(handle);
  return _h.mem_write(address, in, len);
}
mb_status mb_mem_set_peek_mode(void *handle, int mode) {
  (void)handle;
  (void)mode;
  return MB_OK; /* the core-facing path is always side-effect free */
}

/* ---------------------------------------------------------------- options */
mb_status mb_option_set(void *handle, const char *key, const char *value) {
  MB_GUARD(handle);
  if (!key)
    return MB_ERR_INVALID;
  return _h.option_set(key, value ? value : "");
}
mb_status mb_option_get(const void *handle, const char *key, char *out, size_t cap) {
  if (!handle || !key || !out)
    return MB_ERR_INVALID;
  return static_cast<const mb_handle *>(handle)->host.option_get(key, out, cap);
}
int32_t mb_option_count(const void *handle) {
  if (!handle)
    return 0;
  return static_cast<const mb_handle *>(handle)->host.option_count();
}
mb_status mb_option_info(const void *handle, int32_t index, mb_option *out) {
  if (!handle)
    return MB_ERR_INVALID;
  return static_cast<const mb_handle *>(handle)->host.option_info(index, out);
}

/* ------------------------------------------------------------------ misc */
mb_status mb_reset(void *handle) {
  MB_GUARD(handle);
  return _h.reset();
}
mb_status mb_power_cycle(void *handle) {
  MB_GUARD(handle);
  return _h.power_cycle();
}
mb_status mb_get_stats(const void *handle, mb_stats *out) {
  if (!handle)
    return MB_ERR_INVALID;
  return static_cast<const mb_handle *>(handle)->host.stats(out);
}
const char *mb_last_error(void *handle) {
  if (!handle)
    return "invalid handle";
  return static_cast<mb_handle *>(handle)->host.last_error().c_str();
}
const char *mb_status_string(mb_status s) {
  switch (s) {
  case MB_OK:
    return "ok";
  case MB_ERR_INVALID:
    return "invalid argument";
  case MB_ERR_NO_CORE:
    return "core could not be loaded";
  case MB_ERR_NO_ROM:
    return "rom could not be read";
  case MB_ERR_UNSUPPORTED:
    return "not supported by this core";
  case MB_ERR_IO:
    return "file error";
  case MB_ERR_STATE:
    return "save state rejected";
  case MB_ERR_BUSY:
    return "engine busy";
  case MB_ERR_NOMEM:
    return "out of memory";
  case MB_ERR_NO_GAME:
    return "no game loaded";
  }
  return "unknown error";
}

void mb_set_log_callback(void *handle, mb_log_fn fn, void *user) {
  (void)handle;
  mb::log_set_sink(fn, user);
}
void mb_set_event_callback(void *handle, mb_event_fn fn, void *user) {
  if (!handle)
    return;
  static_cast<mb_handle *>(handle)->host.set_event_callback(fn, user);
}

int mb_register_core(const mb_core_factory *factory) {
  if (!factory || !factory->name)
    return -1;
  std::lock_guard<std::mutex> lk(g_reg_mutex);
  for (size_t i = 0; i < g_registry.size(); i++) {
    if (g_registry[i].name && !strcmp(g_registry[i].name, factory->name))
      return int(i);
  }
  g_registry.push_back(*factory);
  return int(g_registry.size() - 1);
}

int mb_list_cores(char names[8][32]) {
  if (!names)
    return 0;
  std::lock_guard<std::mutex> lk(g_reg_mutex);
  int n = 0;
  for (size_t i = 0; i < g_registry.size() && n < 8; i++) {
    snprintf(names[n], 32, "%s", g_registry[i].name ? g_registry[i].name : "?");
    n++;
  }
  return n;
}

} /* extern "C" */
