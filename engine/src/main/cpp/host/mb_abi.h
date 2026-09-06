/* mb_abi.h — MarioBox native engine ABI.
 *
 * Stable C surface shared by the Android JNI bridge and the desktop test host.
 * Nothing here knows about Android; nothing here knows about Compose. The host
 * library (`libmariobox_host.so`) owns one `mb_t` per running game session and
 * drives a libretro core loaded from a separate shared object, which keeps core
 * symbols isolated (two NES cores can coexist in one APK without clashing) and
 * makes user-supplied cores a drop-in.
 *
 * Concurrency rules
 * -----------------
 * All calls are safe from any single UI thread. Only one `mb_t` may be touched
 * by more than one thread at a time, and the emulation thread never calls back
 * into Kotlin. `mb_set_input`/`mb_stats`/peek-poke are lock-free reads of
 * atomics or take a short mutex, so they are safe while the emulator runs.
 *
 * Everything is versioned by `struct_size`-style checks: structs grow at the
 * end and callers pass their size so an older caller never reads uninitialised
 * tail fields.
 */
#ifndef MARIOBOX_ABI_H
#define MARIOBOX_ABI_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define MB_ABI_VERSION 1u
#define MB_MAX_PATH 1024
#define MB_MAX_OPTIONS 48

typedef enum mb_status {
  MB_OK = 0,
  MB_ERR_INVALID = 1,          /* bad argument */
  MB_ERR_NO_CORE = 2,          /* dlopen/link failure */
  MB_ERR_NO_ROM = 3,           /* missing or unreadable ROM */
  MB_ERR_UNSUPPORTED = 4,      /* feature not supported by this core */
  MB_ERR_IO = 5,               /* file system failure */
  MB_ERR_STATE = 6,            /* save-state rejected (wrong game / truncated) */
  MB_ERR_BUSY = 7,             /* operation refused while running */
  MB_ERR_NOMEM = 8,
  MB_ERR_NO_GAME = 9,          /* no cartridge loaded */
} mb_status;

/* Core capabilities, filled by mb_get_core_info(). */
typedef struct mb_core_info {
  uint32_t struct_size;
  char name[64];
  char version[32];
  char extensions[128];   /* lowercase, '|'-separated, as the core reports it */
  int need_fullpath;      /* core must read the file itself */
  int supports_sram;
  int supports_cheats;    /* core has a native cheat path we forward to */
  int performance_level;  /* 0..9, higher = slower core */
} mb_core_info;

/* Live counters for the HUD and for CI assertions. */
typedef struct mb_stats {
  uint32_t struct_size;
  uint64_t frames_emulated;
  uint64_t frames_presented;
  uint64_t frames_skipped;
  uint64_t input_frames;      /* frames where a state-changing input was latched */
  double present_fps;         /* rendered frames per wall second */
  double emu_speed;           /* 1.0 == realtime */
  uint32_t video_width;
  uint32_t video_height;
  uint32_t video_pitch;       /* bytes per row of the internal RGBA frame */
  uint32_t base_width;
  uint32_t base_height;
  double aspect_ratio;
  uint32_t sample_rate;
  uint64_t audio_samples_written;
  uint32_t audio_underruns;
  uint32_t rewind_frames_available;
  uint32_t rewind_capacity_frames;
  uint64_t rewind_bytes;
  uint32_t state_size;
  uint32_t sram_size;
  int paused;
  int running;
  int audio_enabled;
  int pal;                    /* region currently emulated */
  uint32_t ms_per_frame;      /* moving average of the emu loop */
  uint32_t core_cpu_percent;  /* emu thread busy ratio, 0..100 */
} mb_stats;

/* Creation parameters. Grow at the end; pass struct_size. */
typedef struct mb_config {
  uint32_t struct_size;
  const char *core_path;   /* absolute path of the core .so */
  const char *rom_path;    /* absolute path of the ROM */
  const char *save_dir;    /* dir for SRAM + states; created if missing */
  const char *system_dir;  /* BIOS/dat dir or NULL */
  uint32_t rewind_frames;  /* 0 disables the rewind ring */
  uint32_t rewind_stride;  /* snapshot every N frames (1 = every frame) */
  uint32_t rewind_budget_kb; /* hard cap on ring memory */
  int32_t run_ahead;       /* extra frames per presented frame, 0..3 */
  int audio_enable;
  int32_t sample_rate;     /* requested, core may override */
  int video_enable;        /* 0 = headless (tests, fast-forward with no surface) */
  int sram_enable;         /* persist battery RAM */
  int sram_save_on_state;  /* flush SRAM whenever a save state is written */
} mb_config;

/* One cheat entry. `code` is the textual code (Game Genie/Par/AR) or a raw
 * "ADDR=VAL" form; the host decides which decoder fits. */
typedef struct mb_cheat {
  uint32_t struct_size;
  char code[32];
  char desc[96];
  int enabled;
  int kind;         /* 0 auto, 1 game genie, 2 par, 3 action replay, 4 raw poke */
  uint32_t address; /* raw form only */
  uint32_t value;   /* raw form only, -1 style compare in `compare` */
  int32_t compare;  /* -1 when unused */
} mb_cheat;

/* Core option descriptor for the settings UI.
 *
 * `values` holds the strings that must be handed back to mb_option_set(); the
 * core's own option table has a separate display label per value (for FCEUmm:
 * value "0" is labelled "Auto"), and a settings screen that showed or sent the
 * wrong half of that pair would silently do nothing. So both are here:
 * `values[i]` = what to set, `labels[i]` = what to show, and defaults[0] is the
 * option's *current* value (or its documented default when untouched).
 */
typedef struct mb_option {
  uint32_t struct_size;
  char key[64];
  char desc[96];
  char category[48];
  char values[16][48]; /* value texts, terminated by an empty slot */
  char defaults[16][48];
  int default_index;
  int count;
  char labels[16][48]; /* display text for values[i], same order, may equal it */
} mb_option;

/* ---- lifecycle -------------------------------------------------------- */
mb_status mb_create(const mb_config *cfg, void **out_handle);
void mb_destroy(void *handle);
mb_status mb_start(void *handle);   /* begins the emu thread */
mb_status mb_stop(void *handle);    /* joins the emu thread, flushes SRAM */
int mb_is_running(const void *handle);
void mb_set_paused(void *handle, int paused);
int mb_is_paused(const void *handle);

/* ---- surface / rendering ---------------------------------------------- */
/* `native_window` is an ANativeWindow* (Android) or NULL. The host owns the
 * EGL context and renders on the emulation thread, so frame presentation is
 * locked to emulation and there is no tearing or double-buffer mismatch. */
mb_status mb_set_surface(void *handle, void *native_window);
mb_status mb_set_surface_size(void *handle, int32_t width, int32_t height);
void mb_set_video_enabled(void *handle, int enabled);
/* Copies the most recent emulated frame into `out`, as R,G,B,A bytes with A=255,
 * one row per `pitch` bytes, top-left origin (the host swizzles the core's
 * little-endian 0x00RRGGBB words so this contract holds for every core).
 * Returns MB_ERR_INVALID if `cap` is too small. Used for save-state art. */
mb_status mb_copy_frame(void *handle, uint8_t *out, size_t cap, uint32_t *out_w,
                        uint32_t *out_h, uint32_t *out_pitch);
/* Presentation scaling mode used by the renderer. */
typedef enum mb_scale_mode {
  MB_SCALE_FIT = 0,        /* letterbox to the window, keep aspect */
  MB_SCALE_INTEGER = 1,    /* largest whole-number multiple that fits */
  MB_SCALE_STRETCH = 2,    /* fill window (aspect ignored) */
  MB_SCALE_FILL_CROP = 3,  /* cover, crop overflow */
} mb_scale_mode;
typedef enum mb_filter_mode {
  MB_FILTER_NEAREST = 0,
  MB_FILTER_LINEAR = 1,
  MB_FILTER_xBR_NONE_UNUSED = 2,
} mb_filter_mode;
void mb_set_render_config(void *handle, int scale_mode, int filter_mode,
                          int scanlines_percent, int overscan_crop, int rotation);

/* ---- audio ------------------------------------------------------------ */
/* Software gain, 0..1, applied by the platform backend. Readable so a settings
 * screen can show the truth instead of its own copy. */
void mb_set_audio_volume(void *handle, float volume);
float mb_get_audio_volume(const void *handle);

/* ---- input ------------------------------------------------------------ */
/* Bit order matches RETRO_DEVICE_ID_JOYPAD_*:
 * B=0 Y=1 SELECT=2 START=3 UP=4 DOWN=5 LEFT=6 RIGHT=7 A=8 X=9 L=10 R=11
 * L2=12 R2=13 L3=14 R3=15 */
void mb_set_input(void *handle, uint32_t port, uint32_t bitmask);
uint32_t mb_get_input(void *handle, uint32_t port);
/* Turbo: hold-to-repeat at `hz` for the given bits on `port`. */
void mb_set_turbo(void *handle, uint32_t port, uint32_t mask, int hz);
void mb_set_fast_forward(void *handle, int enabled);
void mb_set_slow_motion(void *handle, int enabled);
/* Single-step while paused; returns MB_ERR_INVALID when not paused. */
mb_status mb_advance_frame(void *handle);

/* ---- save states ------------------------------------------------------ */
mb_status mb_save_state(void *handle, const char *path);
mb_status mb_load_state(void *handle, const char *path);
/* Raw (uncompressed) state access, for the rewind ring and for tests. */
size_t mb_serialize_size(const void *handle);
mb_status mb_serialize(void *handle, uint8_t *out, size_t size);
mb_status mb_unserialize(void *handle, const uint8_t *in, size_t size);

/* ---- rewind ----------------------------------------------------------- */
mb_status mb_rewind_step(void *handle, uint32_t frames);
mb_status mb_rewind_jump(void *handle, uint32_t frames_back); /* from newest */
uint32_t mb_rewind_available(const void *handle);
void mb_rewind_clear(void *handle);
/* Exposes the ring for scrubbing: index 0 = oldest. */
uint32_t mb_rewind_count(const void *handle);

/* ---- SRAM ------------------------------------------------------------- */
mb_status mb_sram_flush(void *handle);
mb_status mb_sram_invalidate(void *handle); /* reload from disk */

/* ---- cheats ----------------------------------------------------------- */
/* Returns a non-negative id on success, or -MB_ERR_* on failure. */
int32_t mb_cheat_add(void *handle, const mb_cheat *cheat);
mb_status mb_cheat_remove(void *handle, int32_t id);
mb_status mb_cheat_set_enabled(void *handle, int32_t id, int enabled);
mb_status mb_cheat_set_value(void *handle, int32_t id, uint32_t value);
void mb_cheat_clear(void *handle);
int32_t mb_cheat_count(const void *handle);
mb_status mb_cheat_get(const void *handle, int32_t index, mb_cheat *out);
/* Same entry, read back from the core's own engine (MB_ERR_UNSUPPORTED when the
 * core has none). Lets the UI prove that what it decoded is what was applied. */
mb_status mb_cheat_get_core(void *handle, int32_t index, mb_cheat *out);
/* Applies pending cheat changes immediately (called automatically each frame). */
void mb_cheat_apply(void *handle);
/* Reads the effective value of a cheat's address (for the UI's live view). */
int mb_cheat_test(const void *handle, int32_t index);

/* ---- raw memory ------------------------------------------------------- */
uint32_t mb_mem_peek(void *handle, uint32_t address);   /* CPU space, 0xffff mask */
mb_status mb_mem_poke(void *handle, uint32_t address, uint32_t value);
mb_status mb_mem_read(void *handle, uint32_t address, uint8_t *out, uint32_t len);
mb_status mb_mem_write(void *handle, uint32_t address, const uint8_t *in, uint32_t len);
/* 0 = CPU read (may have side effects), 1 = side-effect free view. */
mb_status mb_mem_set_peek_mode(void *handle, int mode);

/* ---- core options ----------------------------------------------------- */
mb_status mb_option_set(void *handle, const char *key, const char *value);
mb_status mb_option_get(const void *handle, const char *key, char *out, size_t cap);
int32_t mb_option_count(const void *handle);
mb_status mb_option_info(const void *handle, int32_t index, mb_option *out);

/* ---- misc ------------------------------------------------------------- */
mb_status mb_reset(void *handle);         /* soft reset (RESET button) */
mb_status mb_power_cycle(void *handle);   /* full reload of the ROM */
/* Named mb_get_stats rather than mb_stats: a struct tag and a function may
 * share a name in C, but not in C++, and every consumer of this header is C++
 * (the JNI bridge), where the typedef would collide. */
mb_status mb_get_stats(const void *handle, mb_stats *out);
const char *mb_last_error(void *handle);
const char *mb_status_string(mb_status s);
/* Decodes a textual cheat code without a running core (settings preview and
 * unit tests). `out_kind` receives 1/2/3/4 as in mb_cheat.kind. */
mb_status mb_cheat_decode(const char *code, uint32_t *out_addr, uint32_t *out_val,
                          int32_t *out_compare, int *out_kind);

/* Callbacks pushed to the UI. `user` is the handle's user pointer. */
typedef void (*mb_log_fn)(void *user, int level, const char *msg);
typedef void (*mb_event_fn)(void *user, uint32_t event_id, const char *text);
#define MB_EVENT_GEOMETRY 1u   /* video geometry changed; text = "WxH" */
#define MB_EVENT_MESSAGE 2u    /* core message/toast */
#define MB_EVENT_GAME_INFO 3u  /* title/region discovered */
#define MB_EVENT_ERROR 4u
void mb_set_log_callback(void *handle, mb_log_fn fn, void *user);
void mb_set_event_callback(void *handle, mb_event_fn fn, void *user);

/* ---- global (process) helpers ----------------------------------------- */
/* Static core registry: cores linked into the same process can be selected by
 * short name instead of by file path ("fceumm", "nescc"). */
typedef struct mb_core_factory {
  uint32_t struct_size;
  const char *name;
  const char *display_name;
  int (*load)(const char *path, void **out_sym_table, void **out_user);
} mb_core_factory;
int mb_register_core(const mb_core_factory *factory);
int mb_list_cores(char names[8][32]);

#ifdef __cplusplus
} /* extern "C" */
#endif
#endif /* MARIOBOX_ABI_H */
