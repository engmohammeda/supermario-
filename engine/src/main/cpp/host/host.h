/* host.h — the MarioBox emulation host.
 *
 * One EmuHost per game session. It owns:
 *   • a dlopen'd libretro core and the ROM loaded into it,
 *   • the single thread that is allowed to touch the core,
 *   • the frame buffer that the platform renderer and the save-state thumbnails
 *     both read from,
 *   • the rewind ring, the cheat list, SRAM, and the core's option table.
 *
 * Threading contract
 * ------------------
 * `core_mu_` serialises every entry point into the core. The emulation thread
 * holds it for the duration of one `retro_run()` — which is what makes
 * `save_state()` safe to call from the UI thread mid-frame — and releases it
 * while pacing, so the UI is never blocked for longer than a frame. Callers on
 * other threads post a job and wait; the job then runs *between* frames, which
 * is the only place a save state is guaranteed to be self-consistent.
 *
 * No callback ever reaches up into Kotlin: video is copied into the host's own
 * buffer and drained by the platform layer on the same thread; audio is handed
 * to an AudioOut that owns its own ring.
 */
#ifndef MARIOBOX_HOST_H
#define MARIOBOX_HOST_H

#include <atomic>
#include <condition_variable>
#include <deque>
#include <functional>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include <libretro.h>

#include "cheats.h"
#include "libretro_loader.h"
#include "mb_abi.h"
#include "mb_common.h"

namespace mb {

class EmuHost {
public:
  EmuHost();
  ~EmuHost();

  mb_status configure(const mb_config &cfg);
  mb_status start();
  mb_status stop();

  /* ---- lifecycle ---- */
  void set_paused(bool paused);
  bool paused() const { return paused_.load(); }
  bool running() const { return running_.load(); }
  mb_status reset();
  mb_status power_cycle();

  /* ---- surface ---- */
  mb_status set_surface(void *native_window);
  mb_status set_surface_size(int32_t w, int32_t h);
  void set_video_enabled(bool on);
  void set_render_config(const RenderConfig &cfg);
  mb_status copy_frame(uint8_t *out, size_t cap, uint32_t *w, uint32_t *h, uint32_t *pitch);

  /* ---- input ---- */
  void set_input(uint32_t port, uint32_t mask);
  uint32_t input(uint32_t port) const;
  void set_turbo(uint32_t port, uint32_t mask, int hz);
  void set_fast_forward(bool on);
  void set_slow_motion(bool on);
  mb_status advance_frame();

  /* ---- save states ---- */
  mb_status save_state(const std::string &path);
  mb_status load_state(const std::string &path);
  size_t serialize_size();
  mb_status serialize_to(uint8_t *out, size_t size);
  mb_status unserialize_from(const uint8_t *in, size_t size);

  /* ---- rewind ---- */
  mb_status rewind_step(uint32_t frames);
  uint32_t rewind_available();
  void rewind_clear();

  /* ---- sram ---- */
  mb_status sram_flush();
  mb_status sram_invalidate();

  /* ---- cheats ---- */
  int32_t cheat_add(const mb_cheat &c);
  mb_status cheat_remove(int32_t id);
  mb_status cheat_set_enabled(int32_t id, bool on);
  mb_status cheat_set_value(int32_t id, uint32_t v);
  void cheat_clear();
  int32_t cheat_count() const;
  mb_status cheat_get(int32_t index, mb_cheat *out) const;
  int32_t cheat_peek_value(int32_t id);
  /* The same entry read back out of the core's own cheat engine, so the UI and
   * the test suite can verify host decoder == core decoder. */
  mb_status cheat_core_get(int32_t index, mb_cheat *out);

  /* ---- memory ---- */
  uint32_t mem_peek(uint32_t addr);
  mb_status mem_poke(uint32_t addr, uint32_t value);
  mb_status mem_read(uint32_t addr, uint8_t *out, uint32_t len);
  mb_status mem_write(uint32_t addr, const uint8_t *in, uint32_t len);

  /* ---- options ---- */
  mb_status option_set(const std::string &key, const std::string &value);
  mb_status option_get(const std::string &key, char *out, size_t cap) const;
  int32_t option_count() const;
  mb_status option_info(int32_t index, mb_option *out) const;

  /* ---- misc ---- */
  mb_status stats(mb_stats *out) const;
  const std::string &last_error() const { return error_; }
  void set_event_callback(mb_event_fn fn, void *user);
  const RetroCore &core() const { return core_; }
  std::string core_display_name() const { return core_name_; }
  uint64_t rom_fingerprint() const { return rom_fp_; }
  double frame_rate() const { return fps_; }

private:
  using Job = std::function<void()>;
  template <typename F> mb_status post(F fn);
  mb_status run_now(const std::function<mb_status()> &fn);

  /* libretro trampolines */
  static bool env_cb(unsigned cmd, void *data);
  static void video_cb(const void *data, unsigned width, unsigned height, size_t pitch);
  static size_t audio_cb_batch(const int16_t *data, size_t frames);
  static void audio_cb_single(int16_t left, int16_t right);
  static void input_poll_cb(void);
  static int16_t input_state_cb(unsigned port, unsigned device, unsigned index, unsigned id);

  bool env_dispatch(unsigned cmd, void *data);
  void fill_frame_rgba(const void *src, unsigned w, unsigned h, size_t pitch);
  uint32_t mem_peek_locked(uint32_t addr) const;
  bool mem_poke_locked(uint32_t addr, uint32_t value);
  /* Host pointer for a CPU address, or null when no core memory is
   * reachable there (I/O registers, unmapped pages, banked views the
   * core does not publish). */
  uint8_t *mem_host_ptr(uint32_t addr) const;

  void thread_main();
  mb_status do_load();
  void do_unload();
  void capture_rewind_locked();
  void sync_cheats_locked();
  void apply_cheats_host_locked();
  void sram_flush_locked();
  double frame_interval() const;
  void emit(uint32_t event_id, const std::string &text);

  RetroCore core_;
  std::string core_path_, rom_path_, save_dir_, system_dir_;
  std::string core_name_ = "core";
  std::string error_;

  mb_config cfg_{};
  std::vector<uint8_t> rom_bytes_;
  uint64_t rom_fp_ = 0;

  std::thread thread_;
  std::atomic<bool> quit_{false};
  std::atomic<bool> running_{false};
  std::atomic<bool> loaded_{false};
  std::atomic<bool> paused_{false};
  std::atomic<bool> ff_{false};
  std::atomic<bool> slowmo_{false};
  std::atomic<bool> step_once_{false};
  std::atomic<bool> video_enabled_{true};
  std::atomic<bool> vars_dirty_{false};

  mutable std::mutex core_mu_;
  std::mutex job_mu_;
  std::condition_variable job_cv_;
  std::deque<Job> jobs_;
  std::thread::id emu_tid_;

  std::atomic<uint32_t> input_[5]{};
  uint32_t poll_cache_[5]{};
  bool poll_valid_ = false;
  struct Turbo {
    uint32_t port = 0;
    uint32_t mask = 0;
    int hz = 0;
    bool on = false;
  } turbo_[5];

  /* frame buffer */
  std::vector<uint8_t> frame_;
  uint32_t frame_w_ = 0, frame_h_ = 0, frame_pitch_ = 0;
  uint32_t pixel_format_ = RETRO_PIXEL_FORMAT_0RGB1555;
  std::atomic<bool> frame_ready_{false};
  mutable std::mutex frame_mu_;

  /* geometry / timing */
  uint32_t base_w_ = 256, base_h_ = 240;
  uint32_t max_w_ = 512, max_h_ = 480;
  double aspect_ = 4.0 / 3.0;
  double fps_ = 60.09881130985343;
  bool pal_ = false;
  uint32_t sample_rate_ = 44100;

  BlobRing rewind_;
  std::vector<uint8_t> state_buf_;
  size_t state_size_ = 0;

  CheatList cheats_;
  bool cheats_native_ = false;

  uint8_t *sram_ptr_ = nullptr;
  size_t sram_size_ = 0;
  std::vector<uint8_t> sram_disk_;
  bool sram_dirty_ = false;

  std::vector<std::pair<std::string, std::string>> options_;
  /* opt_defs_v1_ never points at the core's memory: the core calloc's its V1
   * option array and frees it, together with the value strings, *before* the
   * SET_CORE_OPTIONS call returns (libretro_set_core_options() in its
   * libretro_core_options.h). Keeping the pointer is a use-after-free that reads
   * fine until it doesn't. opt_defs_owned_ holds our deep copy; opt_strs_ owns
   * the strings it points at and is a deque because deque references are stable. */
  const struct retro_core_option_definition *opt_defs_v1_ = nullptr;
  std::vector<struct retro_core_option_definition> opt_defs_owned_;
  std::deque<std::string> opt_strs_;
  /* Copied out of the core's stack on SET_MEMORY_MAPS: the array itself is
   * a local in the core, so caching the pointer would dangle immediately. */
  std::vector<struct retro_memory_descriptor> mem_map_;

  Surface *surface_ = nullptr;
  AudioOut *audio_ = nullptr;
  RenderConfig render_cfg_;

  /* stats */
  std::atomic<uint64_t> frames_emulated_{0};
  uint64_t frames_presented_ = 0, frames_skipped_ = 0;
  std::atomic<uint64_t> input_frames_{0};
  std::atomic<uint64_t> audio_written_{0};
  double present_fps_ = 0, speed_ = 1.0;
  uint32_t ms_per_frame_ = 0;
  uint32_t cpu_percent_ = 0;
  uint64_t play_ms_ = 0;
  uint64_t play_started_ms_ = 0;
  std::atomic<uint32_t> sram_flushes_{0};

  mb_event_fn event_cb_ = nullptr;
  void *event_user_ = nullptr;

  /* The core calls back into us from inside retro_run; this is how the static
   * trampolines find the owning host. Only one host may be "in" the core at a
   * time, which the core_mu_ contract guarantees. */
  static thread_local EmuHost *active_;
  static EmuHost *self() { return active_; }
};

} /* namespace mb */
#endif /* MARIOBOX_HOST_H */
