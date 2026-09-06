/* mb_common.cpp — implementation of the platform-neutral primitives. */
#include "mb_common.h"

#include <algorithm>
#include <ctime>
#include <errno.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <thread>
#ifdef MB_HAVE_ZLIB
#include <zlib.h>
#endif

#if defined(__APPLE__)
#include <mach/mach_time.h>
#elif defined(_POSIX_MONOTONIC_CLOCK) || defined(__linux__) || defined(__ANDROID__)
#include <time.h>
#endif

namespace mb {

/* ------------------------------------------------------------------ logging */
static void (*g_log_sink)(void *, int, const char *) = nullptr;
static void *g_log_user = nullptr;
static std::mutex g_log_mutex;

void log_set_sink(void (*fn)(void *, int, const char *), void *user) {
  std::lock_guard<std::mutex> lk(g_log_mutex);
  g_log_sink = fn;
  g_log_user = user;
}

void log_write(int level, const char *fmt, ...) {
  char buf[1024];
  va_list ap;
  va_start(ap, fmt);
  vsnprintf(buf, sizeof(buf), fmt, ap);
  va_end(ap);

  decltype(g_log_sink) sink = nullptr;
  void *user = nullptr;
  {
    std::lock_guard<std::mutex> lk(g_log_mutex);
    sink = g_log_sink;
    user = g_log_user;
  }
  if (sink) {
    sink(user, level, buf);
    return;
  }
#if defined(__ANDROID__)
  static const int prio[] = {ANDROID_LOG_VERBOSE, ANDROID_LOG_INFO, ANDROID_LOG_WARN,
                             ANDROID_LOG_ERROR};
  __android_log_write(prio[level & 3], MB_ANDROID_TAG, buf);
#else
  static const char *tag[] = {"V", "I", "W", "E"};
  fprintf(stderr, "[MarioBox/%s] %s\n", tag[level & 3], buf);
#endif
}

/* ------------------------------------------------------------------ time */
double now_seconds() {
#if defined(CLOCK_MONOTONIC)
  struct timespec ts;
  clock_gettime(CLOCK_MONOTONIC, &ts);
  return double(ts.tv_sec) + double(ts.tv_nsec) * 1e-9;
#else
  return double(std::clock()) / CLOCKS_PER_SEC;
#endif
}

uint64_t now_ns() {
#if defined(CLOCK_MONOTONIC)
  struct timespec ts;
  clock_gettime(CLOCK_MONOTONIC, &ts);
  return uint64_t(ts.tv_sec) * 1000000000ull + uint64_t(ts.tv_nsec);
#else
  return uint64_t(std::clock()) * 1000ull;
#endif
}

void sleep_ns(uint64_t ns) {
  if (!ns)
    return;
#if defined(CLOCK_MONOTONIC)
  struct timespec req;
  req.tv_sec = time_t(ns / 1000000000ull);
  req.tv_nsec = long(ns % 1000000000ull);
  while (nanosleep(&req, &req) == -1 && errno == EINTR) {
  }
#else
  std::this_thread::sleep_for(std::chrono::nanoseconds(ns));
#endif
}

/* ------------------------------------------------------------------ files */
bool read_whole_file(const char *path, std::vector<uint8_t> *out) {
  FILE *f = fopen(path, "rb");
  if (!f)
    return false;
  if (fseek(f, 0, SEEK_END) != 0) {
    fclose(f);
    return false;
  }
  long len = ftell(f);
  if (len < 0) {
    fclose(f);
    return false;
  }
  rewind(f);
  out->resize(size_t(len));
  size_t got = len ? fread(out->data(), 1, size_t(len), f) : 0;
  fclose(f);
  if (got != size_t(len)) {
    out->clear();
    return false;
  }
  return true;
}

bool write_whole_file(const char *path, const void *data, size_t len) {
  FILE *f = fopen(path, "wb");
  if (!f)
    return false;
  size_t put = len ? fwrite(data, 1, len, f) : 0;
  if (fflush(f) != 0) {
    fclose(f);
    return false;
  }
  fclose(f);
  return put == len;
}

bool ensure_dir(const char *path) {
  if (!path || !*path)
    return false;
  struct stat st;
  if (stat(path, &st) == 0)
    return S_ISDIR(st.st_mode);
  /* mkdir -p */
  char tmp[MB_MAX_PATH];
  snprintf(tmp, sizeof(tmp), "%s", path);
  size_t n = strlen(tmp);
  if (n && tmp[n - 1] == '/')
    tmp[n - 1] = 0;
  for (char *p = tmp + 1; *p; p++) {
    if (*p == '/') {
      *p = 0;
      if (mkdir(tmp, 0775) != 0 && errno != EEXIST)
        return false;
      *p = '/';
    }
  }
  if (mkdir(tmp, 0775) != 0 && errno != EEXIST)
    return false;
  return true;
}

std::string path_join(const std::string &a, const std::string &b) {
  if (a.empty())
    return b;
  if (!b.empty() && b[0] == '/')
    return b;
  std::string out = a;
  if (out.back() != '/')
    out += '/';
  out += b;
  return out;
}

std::string path_basename_noext(const std::string &path) {
  size_t slash = path.find_last_of("/\\");
  std::string name = (slash == std::string::npos) ? path : path.substr(slash + 1);
  size_t dot = name.find_last_of('.');
  if (dot != std::string::npos && dot > 0)
    name = name.substr(0, dot);
  return name;
}

/* ------------------------------------------------------------------ zlib */
#ifdef MB_HAVE_ZLIB

size_t compress_bound(size_t in_len) { return size_t(compressBound(z_size_t(in_len))); }

int compress_mem(const void *in, size_t in_len, int level, void *out, size_t out_cap,
                 size_t *out_len) {
  uLongf dst = uLongf(out_cap);
  int rc = compress2(static_cast<Bytef *>(out), &dst, static_cast<const Bytef *>(in),
                     uLong(in_len), level);
  if (rc != Z_OK)
    return -1;
  if (out_len)
    *out_len = size_t(dst);
  return int(dst);
}

int decompress_mem(const void *in, size_t in_len, void *out, size_t out_cap,
                   size_t *out_len) {
  uLongf dst = uLongf(out_cap);
  int rc = uncompress(static_cast<Bytef *>(out), &dst, static_cast<const Bytef *>(in),
                      uLong(in_len));
  if (rc != Z_OK)
    return -1;
  if (out_len)
    *out_len = size_t(dst);
  return int(dst);
}

#else /* !MB_HAVE_ZLIB — verbatim storage; readers accept it (see mb_common.h) */

size_t compress_bound(size_t in_len) { return in_len; }

int compress_mem(const void *in, size_t in_len, int /*level*/, void *out, size_t out_cap,
                 size_t *out_len) {
  if (in_len > out_cap)
    return -1;
  if (in_len)
    memcpy(out, in, in_len);
  if (out_len)
    *out_len = in_len;
  return int(in_len);
}

int decompress_mem(const void *in, size_t in_len, void *out, size_t out_cap,
                   size_t *out_len) {
  if (in_len > out_cap)
    return -1;
  if (in_len)
    memcpy(out, in, in_len);
  if (out_len)
    *out_len = in_len;
  return int(in_len);
}

#endif /* MB_HAVE_ZLIB */

/* ------------------------------------------------------------------ hashing */
uint32_t fnv1a_32(const void *data, size_t len, uint32_t seed) {
  const uint8_t *p = static_cast<const uint8_t *>(data);
  uint32_t h = seed;
  for (size_t i = 0; i < len; i++) {
    h ^= p[i];
    h *= 16777619u;
  }
  return h;
}

uint32_t crc32_mem(const void *data, size_t len) {
#ifdef MB_HAVE_ZLIB
  return uint32_t(crc32(uLong(0), static_cast<const Bytef *>(data), uInt(len)));
#else
  /* Same polynomial and the same final inversion as the canonical CRC-32, so a
   * checksum written by either build means the same thing. */
  static uint32_t table[256];
  static bool ready = false;
  if (!ready) {
    for (uint32_t i = 0; i < 256; i++) {
      uint32_t c = i;
      for (int k = 0; k < 8; k++)
        c = (c & 1) ? (0xEDB88320u ^ (c >> 1)) : (c >> 1);
      table[i] = c;
    }
    ready = true;
  }
  const uint8_t *p = static_cast<const uint8_t *>(data);
  uint32_t c = 0xFFFFFFFFu;
  for (size_t i = 0; i < len; i++)
    c = table[(c ^ p[i]) & 0xFF] ^ (c >> 8);
  return c ^ 0xFFFFFFFFu;
#endif
}

/* ------------------------------------------------------------------ string */
std::string str_lower(std::string s) {
  std::transform(s.begin(), s.end(), s.begin(),
                 [](unsigned char c) { return char(std::tolower(c)); });
  return s;
}

bool str_ieq(const char *a, const char *b) {
  if (!a || !b)
    return false;
  while (*a && *b) {
    if (std::tolower((unsigned char)*a) != std::tolower((unsigned char)*b))
      return false;
    a++;
    b++;
  }
  return *a == 0 && *b == 0;
}

int str_split(char *src, char sep, char **out, int max) {
  int n = 0;
  char *p = src;
  if (!src)
    return 0;
  while (n < max) {
    out[n++] = p;
    char *at = static_cast<char *>(memchr(p, sep, strlen(p)));
    if (!at)
      break;
    *at = 0;
    p = at + 1;
  }
  return n;
}

/* -------------------------------------------------------------- BlobRing */
void BlobRing::configure(uint32_t capacity_entries, uint64_t budget_bytes) {
  cap_ = capacity_entries ? capacity_entries : 1;
  budget_ = budget_bytes;
  slots_.assign(cap_, Entry{});
  for (auto &s : slots_) {
    s.raw_len = s.stored_len = 0;
    s.frame = 0;
    s.data.clear();
  }
  head_ = 0;
  count_ = 0;
  bytes_ = 0;
  dropped_ = 0;
}

void BlobRing::clear() {
  for (auto &s : slots_) {
    s.raw_len = s.stored_len = 0;
    s.data.clear();
  }
  head_ = 0;
  count_ = 0;
  bytes_ = 0;
}

void BlobRing::push(uint64_t frame, const void *raw, uint32_t raw_len, int level) {
  if (!cap_ || !raw_len)
    return;
  size_t bound = compress_bound(raw_len) + 16;
  if (scratch_.size() < bound)
    scratch_.resize(bound);
  size_t stored = 0;
  int rc = compress_mem(raw, raw_len, level, scratch_.data(), scratch_.size(), &stored);
  if (rc < 0)
    stored = 0; /* store raw if the codec refuses */

  /* Reuse the slot we are about to overwrite, and retire its bytes. */
  Entry &dst = slots_[head_];
  if (count_ == cap_) {
    bytes_ -= dst.stored_len;
    dropped_++;
  } else {
    count_++;
  }
  if (dst.data.size() < stored)
    dst.data.resize(stored);
  if (stored)
    memcpy(dst.data.data(), scratch_.data(), stored);
  dst.stored_len = uint32_t(stored);
  dst.raw_len = raw_len;
  dst.frame = frame;
  bytes_ += dst.stored_len;

  head_ = (head_ + 1) % cap_;

  /* Byte budget wins over the frame count: on a low-RAM phone we rewind fewer
   * seconds rather than growing without bound. */
  while (count_ > 1 && budget_ && bytes_ > budget_) {
    uint32_t oldest = (head_ + cap_ - count_) % cap_;
    bytes_ -= slots_[oldest].stored_len;
    slots_[oldest].stored_len = 0;
    slots_[oldest].raw_len = 0;
    count_--;
    dropped_++;
  }
}

const BlobRing::Entry *BlobRing::at(uint32_t idx_back) const {
  if (idx_back >= count_)
    return nullptr;
  uint32_t slot = (head_ + cap_ - 1 - idx_back) % cap_;
  return &slots_[slot];
}

bool BlobRing::pop_to(uint32_t idx_back, std::vector<uint8_t> *out_raw) {
  const Entry *e = at(idx_back);
  if (!e || !e->raw_len)
    return false;
  out_raw->resize(e->raw_len);
  size_t got = 0;
  if (e->stored_len) {
    if (decompress_mem(e->data.data(), e->stored_len, out_raw->data(), out_raw->size(),
                       &got) < 0)
      return false;
  } else {
    got = e->raw_len;
    memcpy(out_raw->data(), e->data.data(), got);
  }
  if (got != e->raw_len)
    return false;
  /* Everything newer than the target is unusable after a rewind. */
  while (count_ > idx_back) {
    uint32_t newest = (head_ + cap_ - 1) % cap_;
    bytes_ -= slots_[newest].stored_len;
    slots_[newest].stored_len = 0;
    slots_[newest].raw_len = 0;
    count_--;
  }
  head_ = (head_ + cap_ - count_) % cap_;
  return true;
}

} /* namespace mb */
