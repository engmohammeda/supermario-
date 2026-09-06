/* libretro_loader.h — resolves the libretro ABI of a core shared object.
 *
 * MarioBox loads a core with dlopen() rather than linking it, which buys three
 * things: two NES cores can ship in one APK without their `retro_*` symbols
 * colliding, a core can be reloaded (or swapped) without restarting the
 * process, and the user can point the app at their own core build.
 *
 * Only the subset of the ABI MarioBox actually drives is resolved; optional
 * entry points stay null and callers check for that.
 */
#ifndef MARIOBOX_LIBRETRO_LOADER_H
#define MARIOBOX_LIBRETRO_LOADER_H

#include <libretro.h>

#include <string>

namespace mb {

/* Extension entry points exported by our own core builds (see
 * bridge/fceumm). Null when a third-party core is loaded. */
struct CoreExt {
  uint32_t (*supported)(void) = nullptr;
  int32_t (*cheat_add_gg)(const char *, const char *) = nullptr;
  int32_t (*cheat_add_par)(const char *, const char *) = nullptr;
  int32_t (*cheat_add_raw)(const char *, uint32_t, uint32_t, int32_t, int32_t) = nullptr;
  int32_t (*cheat_set_enabled)(uint32_t, int32_t) = nullptr;
  int32_t (*cheat_set_value)(uint32_t, uint32_t) = nullptr;
  int32_t (*cheat_remove)(uint32_t) = nullptr;
  void (*cheat_clear)(void) = nullptr;
  uint32_t (*cheat_count)(void) = nullptr;
  int32_t (*cheat_get)(uint32_t, uint32_t *, uint32_t *, int32_t *, int32_t *, int32_t *, char *,
                       uint32_t) = nullptr;
  bool native_cheats() const { return cheat_add_raw != nullptr; }
};

struct RetroCore {
  void *dl = nullptr;
  std::string path;
  std::string error;
  bool ok = false;

  void (*set_environment)(retro_environment_t) = nullptr;
  void (*set_video_refresh)(retro_video_refresh_t) = nullptr;
  void (*set_audio_sample)(retro_audio_sample_t) = nullptr;
  void (*set_audio_sample_batch)(retro_audio_sample_batch_t) = nullptr;
  void (*set_input_poll)(retro_input_poll_t) = nullptr;
  void (*set_input_state)(retro_input_state_t) = nullptr;

  void (*init)(void) = nullptr;
  void (*deinit)(void) = nullptr;
  void (*reset)(void) = nullptr;
  void (*run)(void) = nullptr;

  void (*get_system_info)(struct retro_system_info *) = nullptr;
  void (*get_system_av_info)(struct retro_system_av_info *) = nullptr;
  void (*set_controller_port_device)(unsigned, unsigned) = nullptr;

  bool (*load_game)(const struct retro_game_info *) = nullptr;
  void (*unload_game)(void) = nullptr;
  bool (*load_game_special)(unsigned, const struct retro_game_info *, size_t) = nullptr;

  size_t (*serialize_size)(void) = nullptr;
  bool (*serialize)(void *, size_t) = nullptr;
  bool (*unserialize)(const void *, size_t) = nullptr;

  void *(*get_memory_data)(unsigned) = nullptr;
  size_t (*get_memory_size)(unsigned) = nullptr;
  unsigned (*get_region)(void) = nullptr;

  CoreExt ext;

  /* Every entry point a frame loop cannot live without. Deliberately does NOT
   * consult `ok`: that flag is the *result* of this check, and folding it in
   * makes valid() false while the loader is still deciding, which sends the
   * loader down its failure path for a core that is perfectly fine. */
  bool valid() const {
    return run && load_game && unload_game && set_environment && set_video_refresh &&
           set_input_poll && set_input_state && serialize && unserialize && serialize_size &&
           get_system_info && get_system_av_info;
  }
};

/* Loads `path` and resolves symbols. `out->error` is filled on failure. */
bool core_load(const char *path, RetroCore *out);
void core_unload(RetroCore *core);

} /* namespace mb */
#endif /* MARIOBOX_LIBRETRO_LOADER_H */
