/* state_store.h — MarioBox save-state container ("MBSV").
 *
 * A save state is the raw core blob; that is not enough for a real player, so
 * the container adds what the UI needs to show a slot: which game it belongs
 * to, the frame and wall-clock time when it was written, how long the session
 * had been running, an optional caption, and a small thumbnail. Everything is
 * zlib-deflated except the header, so a slot's preview can be read back
 * without touching the state itself — which matters when the state is 200 KB
 * and the UI is listing twelve of them.
 *
 * Guarding matters more than compressing: loading a state saved against a
 * different ROM (or a different core build) would corrupt a running emulator in
 * ways that look like random bugs. The header carries a fingerprint of both and
 * the loader refuses on mismatch, unless the caller asks for a "force" load.
 *
 * Layout (little-endian, no alignment padding):
 *   magic "MBSV", u32 version=1, u32 header_bytes
 *   u64 rom_fingerprint, u32 core_name_len + core_name, u32 title_len + title
 *   u64 created_ms, u64 frame, u64 play_ms, u32 flags
 *   u32 thumb_w, u32 thumb_h, u32 thumb_bytes (+deflated RGBA8888)
 *   u32 state_bytes (+deflated core state)
 */
#ifndef MARIOBOX_STATE_STORE_H
#define MARIOBOX_STATE_STORE_H

#include <cstdint>
#include <string>
#include <vector>

namespace mb {

constexpr uint32_t kStateMagic = 0x5653424Du; /* "MBSV" */
constexpr uint32_t kStateVersion = 1u;
constexpr uint32_t kStateFlagManual = 1u << 0;
constexpr uint32_t kStateFlagQuick = 1u << 1;
constexpr uint32_t kStateFlagAuto = 1u << 2;
constexpr uint32_t kStateFlagScreenshot = 1u << 3;

struct SaveStateHeader {
  uint64_t rom_fingerprint = 0;
  std::string core_name;
  std::string title;
  uint64_t created_ms = 0;
  uint64_t frame = 0;
  uint64_t play_ms = 0;
  uint32_t flags = 0;
  uint32_t thumb_w = 0;
  uint32_t thumb_h = 0;
  std::vector<uint8_t> thumb_rgba; /* decompressed, may be empty */
  std::vector<uint8_t> state;      /* raw core state */
};

/* Writes the container. `thumb_rgba` may be empty (w/h must then be 0). */
bool state_write(const std::string &path, const SaveStateHeader &hdr, std::string *error);
/* Reads and validates. `expect_rom` / `expect_core` are checked when non-empty.
 * `want_thumb=false` skips decoding the thumbnail (cheap listing). */
bool state_read(const std::string &path, SaveStateHeader *out, const std::string &expect_rom_hex,
                const std::string &expect_core, bool want_thumb, std::string *error);
/* Header-only read for list views (does not inflate the state). */
bool state_read_header(const std::string &path, SaveStateHeader *out, std::string *error);

/* FNV-1a over the ROM file plus its length, so two different games cannot
 * collide and a truncated copy is detected. */
uint64_t rom_fingerprint(const std::vector<uint8_t> &rom);
uint64_t rom_fingerprint_file(const char *path);
std::string hex64(uint64_t v);

/* SRAM (battery save) sidecar helpers. */
std::string sram_path_for(const std::string &save_dir, const std::string &rom_path);
bool sram_save(const std::string &path, const void *data, size_t len);
bool sram_load(const std::string &path, std::vector<uint8_t> *out);

/* Thumbnail: downscale an RGBA frame to at most 160x120 with a box filter.
 * Cheap and good enough for a grid of slots; done on the emulation thread's
 * copy, not on the hot path (only when a state is written). */
void thumb_downscale(const uint8_t *src, uint32_t w, uint32_t h, uint32_t pitch, uint32_t max_w,
                     uint32_t max_h, std::vector<uint8_t> *out, uint32_t *out_w,
                     uint32_t *out_h);

} /* namespace mb */
#endif /* MARIOBOX_STATE_STORE_H */
