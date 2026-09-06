/* mb_common.h — shared primitives for the MarioBox native host.
 *
 * Deliberately platform-free: the emulator core, the rewind ring and the cheat
 * engine must compile on Linux so they can be unit-tested without an Android
 * device. Anything that needs a real surface or a real audio device goes
 * behind the two small interfaces at the bottom of this file.
 */
#ifndef MARIOBOX_COMMON_H
#define MARIOBOX_COMMON_H

#include <atomic>
#include <cstdarg>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#include "mb_abi.h"

#if defined(__ANDROID__)
#include <android/log.h>
#define MB_ANDROID_TAG "MarioBox"
#endif

namespace mb {

/* ------------------------------------------------------------------ logging */
enum LogLevel { kVerbose = 0, kInfo = 1, kWarn = 2, kError = 3 };

void log_write(int level, const char *fmt, ...) __attribute__((format(printf, 2, 3)));
void log_set_sink(void (*fn)(void *, int, const char *), void *user);

#define MB_LOGV(...) ::mb::log_write(::mb::kVerbose, __VA_ARGS__)
#define MB_LOGI(...) ::mb::log_write(::mb::kInfo, __VA_ARGS__)
#define MB_LOGW(...) ::mb::log_write(::mb::kWarn, __VA_ARGS__)
#define MB_LOGE(...) ::mb::log_write(::mb::kError, __VA_ARGS__)

/* ------------------------------------------------------------------ monotonic */
double now_seconds();
uint64_t now_ns();
void sleep_ns(uint64_t ns);

/* ------------------------------------------------------------------ files */
bool read_whole_file(const char *path, std::vector<uint8_t> *out);
bool write_whole_file(const char *path, const void *data, size_t len);
bool ensure_dir(const char *path);
std::string path_join(const std::string &a, const std::string &b);
std::string path_basename_noext(const std::string &path);

/* ------------------------------------------------------------------ zlib */
/* Deflate is a build option, not an assumption: MB_HAVE_ZLIB is set when zlib
 * is on the link line (it always is for Android — the NDK ships libz — and for
 * CI, which is where the compressed path gets exercised). Without it these
 * functions store bytes verbatim, and the readers below already accept a
 * verbatim blob, because a state whose length equals its raw length is by
 * definition not deflated. Save files therefore stay readable either way, which
 * is the property you want from a format that outlives the build that wrote it.
 *
 * Both return the number of bytes written, or -1. When `out` is null the size
 * is only measured (used to size buffers). */
int compress_mem(const void *in, size_t in_len, int level, void *out, size_t out_cap,
                 size_t *out_len);
size_t compress_bound(size_t in_len);
int decompress_mem(const void *in, size_t in_len, void *out, size_t out_cap,
                   size_t *out_len);

/* ------------------------------------------------------------------ hashing */
uint32_t fnv1a_32(const void *data, size_t len, uint32_t seed = 2166136261u);
/* Lightweight CRC used by the test suite to compare framebuffer content. */
uint32_t crc32_mem(const void *data, size_t len);

/* ------------------------------------------------------------------ string */
std::string str_lower(std::string s);
bool str_ieq(const char *a, const char *b);
int str_split(char *src, char sep, char **out, int max);

/* ------------------------------------------------------- small fixed ring */
/* Stores compressed blobs indexed newest-first, with a byte budget. The
 * rewind feature and the frame-advance history both need exactly this, and
 * they need it without touching the allocator on the hot path: buffers are
 * recycled from a free list. */
class BlobRing {
public:
  struct Entry {
    uint32_t raw_len = 0;
    uint32_t stored_len = 0;
    uint64_t frame = 0;
    std::vector<uint8_t> data; /* compressed */
  };

  void configure(uint32_t capacity_entries, uint64_t budget_bytes);
  void clear();
  void push(uint64_t frame, const void *raw, uint32_t raw_len, int level);
  /* index 0 == newest. */
  const Entry *at(uint32_t idx_back) const;
  bool pop_to(uint32_t idx_back, std::vector<uint8_t> *out_raw);
  uint32_t count() const { return count_; }
  uint32_t capacity() const { return cap_; }
  uint64_t bytes() const { return bytes_; }
  uint64_t dropped() const { return dropped_; }

private:
  uint32_t cap_ = 0;
  uint64_t budget_ = 0;
  uint32_t head_ = 0; /* next write slot */
  uint32_t count_ = 0;
  uint64_t bytes_ = 0;
  uint64_t dropped_ = 0;
  std::vector<Entry> slots_;
  std::vector<uint8_t> scratch_;
};

/* ------------------------------------------------------- platform surface */
/* A frame handed to the platform renderer. `pixels` is only valid for the
 * duration of the call. */
struct FrameView {
  const uint8_t *pixels;
  uint32_t width;
  uint32_t height;
  uint32_t pitch; /* bytes per row */
  uint32_t base_width;
  uint32_t base_height;
  double aspect_ratio;
};

struct RenderConfig {
  int scale_mode = MB_SCALE_FIT;
  int filter_mode = MB_FILTER_NEAREST;
  int scanlines_percent = 0;
  int overscan_crop = 0;
  int rotation = 0; /* 0 or 90 clockwise */
};

/* Implemented per platform. A null pointer means "no rendering". */
class Surface {
public:
  virtual ~Surface() = default;
  /* Returns true when the surface is ready to draw (buffers allocated). */
  virtual bool attach(void *native_window, uint32_t w, uint32_t h) = 0;
  virtual void detach() = 0;
  virtual void resize(uint32_t w, uint32_t h) = 0;
  virtual void apply(const RenderConfig &cfg) = 0;
  virtual void present(const FrameView &frame) = 0;
  /* Geometry the app must know about, e.g. after a rotation. */
  virtual void geometry_out(uint32_t *w, uint32_t *h) {
    if (w)
      *w = 0;
    if (h)
      *h = 0;
  }
};

Surface *surface_create(); /* may return nullptr (headless) */
void surface_destroy(Surface *s);

/* ------------------------------------------------------- platform audio */
class AudioOut {
public:
  virtual ~AudioOut() = default;
  virtual bool open(uint32_t sample_rate, uint32_t channels, uint32_t latency_frames) = 0;
  virtual void close() = 0;
  /* Blocking write of interleaved s16. Returns frames actually consumed.
   * A full queue that never drains would hang the emulator, so an
   * implementation must drop after a bounded wait and count an underrun. */
  virtual size_t write(const int16_t *frames, size_t frame_count) = 0;
  virtual void start() = 0;
  virtual void stop() = 0;
  virtual void set_paused(bool paused) = 0;
  virtual void set_volume(float v) = 0;
  virtual void drain() = 0;
  virtual uint32_t underruns() const = 0;
  virtual const char *backend_name() const = 0;
  /* True for the headless backends used by desktop builds and tests: no real
   * device means nothing to keep time with, so the host must not sleep. */
  virtual bool is_null() const { return false; }
};

AudioOut *audio_create(); /* may return nullptr (muted) */
void audio_destroy(AudioOut *a);

} /* namespace mb */
#endif /* MARIOBOX_COMMON_H */
