/* libretro_loader.cpp */
#include "libretro_loader.h"

#include <dlfcn.h>

namespace mb {

namespace {

template <typename T>
T sym(void *dl, const char *name, bool required, bool &ok) {
  void *p = dlsym(dl, name);
  if (!p && required)
    ok = false;
  (void)p;
  return reinterpret_cast<T>(p);
}

} /* namespace */

bool core_load(const char *path, RetroCore *out) {
  out->ok = false;
  out->error.clear();
  out->dl = nullptr;

  if (!path || !*path) {
    out->error = "empty core path";
    return false;
  }
  /* RTLD_NOW: fail here rather than on the first frame. RTLD_LOCAL keeps one
   * core's globals away from another core's. */
  void *dl = dlopen(path, RTLD_NOW | RTLD_LOCAL);
  if (!dl) {
    const char *e = dlerror();
    out->error = e ? e : "dlopen failed";
    return false;
  }
  out->dl = dl;
  out->path = path;

  bool ok = true;
  auto &c = *out;
  c.set_environment = sym<decltype(c.set_environment)>(dl, "retro_set_environment", true, ok);
  c.set_video_refresh = sym<decltype(c.set_video_refresh)>(dl, "retro_set_video_refresh", true, ok);
  c.set_audio_sample = sym<decltype(c.set_audio_sample)>(dl, "retro_set_audio_sample", false, ok);
  c.set_audio_sample_batch =
      sym<decltype(c.set_audio_sample_batch)>(dl, "retro_set_audio_sample_batch", false, ok);
  c.set_input_poll = sym<decltype(c.set_input_poll)>(dl, "retro_set_input_poll", true, ok);
  c.set_input_state = sym<decltype(c.set_input_state)>(dl, "retro_set_input_state", true, ok);
  c.init = sym<decltype(c.init)>(dl, "retro_init", false, ok);
  c.deinit = sym<decltype(c.deinit)>(dl, "retro_deinit", false, ok);
  c.reset = sym<decltype(c.reset)>(dl, "retro_reset", true, ok);
  c.run = sym<decltype(c.run)>(dl, "retro_run", true, ok);
  c.get_system_info = sym<decltype(c.get_system_info)>(dl, "retro_get_system_info", true, ok);
  c.get_system_av_info = sym<decltype(c.get_system_av_info)>(dl, "retro_get_system_av_info", true, ok);
  c.set_controller_port_device =
      sym<decltype(c.set_controller_port_device)>(dl, "retro_set_controller_port_device", false, ok);
  c.load_game = sym<decltype(c.load_game)>(dl, "retro_load_game", true, ok);
  c.unload_game = sym<decltype(c.unload_game)>(dl, "retro_unload_game", true, ok);
  c.load_game_special = sym<decltype(c.load_game_special)>(dl, "retro_load_game_special", false, ok);
  c.serialize_size = sym<decltype(c.serialize_size)>(dl, "retro_serialize_size", true, ok);
  c.serialize = sym<decltype(c.serialize)>(dl, "retro_serialize", true, ok);
  c.unserialize = sym<decltype(c.unserialize)>(dl, "retro_unserialize", true, ok);
  c.get_memory_data = sym<decltype(c.get_memory_data)>(dl, "retro_get_memory_data", false, ok);
  c.get_memory_size = sym<decltype(c.get_memory_size)>(dl, "retro_get_memory_size", false, ok);
  c.get_region = sym<decltype(c.get_region)>(dl, "retro_get_region", false, ok);

  /* Optional MarioBox extensions compiled into our own core builds. */
  auto &e = c.ext;
  e.supported = sym<decltype(e.supported)>(dl, "mbx_fceumm_supported", false, ok);
  e.cheat_add_gg = sym<decltype(e.cheat_add_gg)>(dl, "mbx_fceumm_cheat_add_gg", false, ok);
  e.cheat_add_par = sym<decltype(e.cheat_add_par)>(dl, "mbx_fceumm_cheat_add_par", false, ok);
  e.cheat_add_raw = sym<decltype(e.cheat_add_raw)>(dl, "mbx_fceumm_cheat_add_raw", false, ok);
  e.cheat_set_enabled =
      sym<decltype(e.cheat_set_enabled)>(dl, "mbx_fceumm_cheat_set_enabled", false, ok);
  e.cheat_set_value = sym<decltype(e.cheat_set_value)>(dl, "mbx_fceumm_cheat_set_value", false, ok);
  e.cheat_remove = sym<decltype(e.cheat_remove)>(dl, "mbx_fceumm_cheat_remove", false, ok);
  e.cheat_clear = sym<decltype(e.cheat_clear)>(dl, "mbx_fceumm_cheat_clear", false, ok);
  e.cheat_count = sym<decltype(e.cheat_count)>(dl, "mbx_fceumm_cheat_count", false, ok);
  e.cheat_get = sym<decltype(e.cheat_get)>(dl, "mbx_fceumm_cheat_get", false, ok);

  if (!ok || !c.valid()) {
    out->error = "not a usable libretro core (missing retro_run/retro_serialize/…)";
    /* No dlclose: unloading a libretro core is not safe in general — the DSO may
     * already have run constructors, registered atexit handlers, or kept global
     * state — and on a path this cold (a bad or mismatched core file) leaking one
     * mapping is a far better trade than an unload crash at 2 a.m. */
    out->dl = nullptr;
    return false;
  }
  out->ok = true;
  return true;
}

void core_unload(RetroCore *core) {
  if (!core)
    return;
  if (core->unload_game)
    core->unload_game();
  if (core->deinit)
    core->deinit();
  /* The mapping is intentionally left resident. libretro cores are not written
   * to be unloaded and reloaded in the same process — RetroArch keeps them
   * mapped for the session too — and a second dlopen of the same path would
   * return the stale, already-destroyed instance. Swapping a core therefore
   * means swapping which symbols we call, not which .so is mapped. The cost is
   * one resident mapping per core we actually load, which for a phone app that
   * can pick between a couple of NES cores is not worth the crash it prevents. */
  core->dl = nullptr;
  *core = RetroCore{};
}

} /* namespace mb */
