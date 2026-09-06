/* state_store.cpp */
#include "state_store.h"

#include "mb_common.h"

#include <cstdio>
#include <cstring>

namespace mb {
namespace {

void put_u32(std::vector<uint8_t> &v, uint32_t x) {
  v.push_back(uint8_t(x & 0xFF));
  v.push_back(uint8_t((x >> 8) & 0xFF));
  v.push_back(uint8_t((x >> 16) & 0xFF));
  v.push_back(uint8_t((x >> 24) & 0xFF));
}
void put_u64(std::vector<uint8_t> &v, uint64_t x) {
  put_u32(v, uint32_t(x & 0xFFFFFFFFu));
  put_u32(v, uint32_t((x >> 32) & 0xFFFFFFFFu));
}
void put_str(std::vector<uint8_t> &v, const std::string &s) {
  put_u32(v, uint32_t(s.size()));
  v.insert(v.end(), s.begin(), s.end());
}
void put_blob(std::vector<uint8_t> &v, const std::vector<uint8_t> &b) {
  put_u32(v, uint32_t(b.size()));
  v.insert(v.end(), b.begin(), b.end());
}

struct Reader {
  const uint8_t *p;
  size_t n;
  size_t at = 0;
  bool bad = false;

  uint32_t u32() {
    if (at + 4 > n) {
      bad = true;
      return 0;
    }
    uint32_t v = uint32_t(p[at]) | (uint32_t(p[at + 1]) << 8) | (uint32_t(p[at + 2]) << 16) |
                 (uint32_t(p[at + 3]) << 24);
    at += 4;
    return v;
  }
  uint64_t u64() {
    uint64_t lo = u32();
    uint64_t hi = u32();
    return lo | (hi << 32);
  }
  std::string str() {
    uint32_t len = u32();
    if (bad || at + len > n) {
      bad = true;
      return {};
    }
    std::string s(reinterpret_cast<const char *>(p + at), len);
    at += len;
    return s;
  }
  std::vector<uint8_t> blob() {
    uint32_t len = u32();
    if (bad || at + len > n) {
      bad = true;
      return {};
    }
    std::vector<uint8_t> b(p + at, p + at + len);
    at += len;
    return b;
  }
};

std::vector<uint8_t> deflate(const std::vector<uint8_t> &in) {
  std::vector<uint8_t> out;
  if (in.empty())
    return out;
  size_t bound = compress_bound(in.size()) + 16;
  out.resize(bound);
  size_t got = 0;
  if (compress_mem(in.data(), in.size(), 6, out.data(), out.size(), &got) < 0)
    return in; /* fall back to stored */
  out.resize(got);
  return out;
}

bool inflate(const std::vector<uint8_t> &in, size_t raw_len, std::vector<uint8_t> *out) {
  out->assign(raw_len, 0);
  if (!raw_len)
    return true;
  size_t got = 0;
  /* A stored (uncompressed) fallback is written with the same length, so try
   * zlib first and accept the blob verbatim when it was not deflated. */
  if (decompress_mem(in.data(), in.size(), out->data(), out->size(), &got) < 0) {
    if (in.size() != raw_len)
      return false;
    *out = in;
  }
  return true;
}

} /* namespace */

uint64_t rom_fingerprint(const std::vector<uint8_t> &rom) {
  uint32_t a = fnv1a_32(rom.data(), rom.size() < 65536 ? rom.size() : 65536, 2166136261u);
  uint32_t b = rom.size() > 65536
                   ? fnv1a_32(rom.data() + (rom.size() - 65536), 65536, 0x9e3779b9u)
                   : a * 2654435761u;
  return (uint64_t(a) << 32) | uint64_t(b ^ uint32_t(rom.size() * 2654435761u));
}

uint64_t rom_fingerprint_file(const char *path) {
  std::vector<uint8_t> rom;
  if (!read_whole_file(path, &rom))
    return 0;
  return rom_fingerprint(rom);
}

std::string hex64(uint64_t v) {
  char buf[24];
  snprintf(buf, sizeof(buf), "%016llx", (unsigned long long)v);
  return buf;
}

bool state_write(const std::string &path, const SaveStateHeader &h, std::string *error) {
  if (h.state.empty()) {
    if (error)
      *error = "empty state";
    return false;
  }
  std::vector<uint8_t> v;
  put_u32(v, kStateMagic);
  put_u32(v, kStateVersion);
  size_t hdr_at = v.size();
  put_u32(v, 0); /* header size, patched below */
  put_u64(v, h.rom_fingerprint);
  put_str(v, h.core_name);
  put_str(v, h.title);
  put_u64(v, h.created_ms);
  put_u64(v, h.frame);
  put_u64(v, h.play_ms);
  put_u32(v, h.flags);
  put_u32(v, h.thumb_w);
  put_u32(v, h.thumb_h);
  std::vector<uint8_t> thumb = deflate(h.thumb_rgba);
  put_blob(v, thumb);
  size_t header_end = v.size();
  /* Patch header_bytes so a reader can skip to the state without parsing. */
  uint32_t hs = uint32_t(header_end - (hdr_at + 4));
  v[hdr_at] = uint8_t(hs & 0xFF);
  v[hdr_at + 1] = uint8_t((hs >> 8) & 0xFF);
  v[hdr_at + 2] = uint8_t((hs >> 16) & 0xFF);
  v[hdr_at + 3] = uint8_t((hs >> 24) & 0xFF);
  std::vector<uint8_t> body = deflate(h.state);
  put_blob(v, body);

  if (!write_whole_file(path.c_str(), v.data(), v.size())) {
    if (error)
      *error = "cannot write " + path;
    return false;
  }
  return true;
}

namespace {
bool parse(const std::vector<uint8_t> &v, SaveStateHeader *out, bool want_state,
           bool want_thumb, std::string *error) {
  Reader r{v.data(), v.size(), 0, false};
  if (r.u32() != kStateMagic) {
    if (error)
      *error = "not a MarioBox save state";
    return false;
  }
  uint32_t version = r.u32();
  uint32_t header_bytes = r.u32();
  if (version != kStateVersion) {
    if (error)
      *error = "unsupported save state version";
    return false;
  }
  out->rom_fingerprint = r.u64();
  out->core_name = r.str();
  out->title = r.str();
  out->created_ms = r.u64();
  out->frame = r.u64();
  out->play_ms = r.u64();
  out->flags = r.u32();
  out->thumb_w = r.u32();
  out->thumb_h = r.u32();
  std::vector<uint8_t> thumb = r.blob();
  if (want_thumb && !thumb.empty()) {
    size_t need = size_t(out->thumb_w) * size_t(out->thumb_h) * 4u;
    if (!inflate(thumb, need, &out->thumb_rgba) || out->thumb_rgba.size() != need) {
      out->thumb_rgba.clear(); /* cosmetic — never fail the load over art */
    }
  }
  if (r.bad) {
    if (error)
      *error = "truncated header";
    return false;
  }
  if (!want_state)
    return true;
  std::vector<uint8_t> body = r.blob();
  if (r.bad) {
    if (error)
      *error = "truncated state";
    return false;
  }
  if (body.empty()) {
    if (error)
      *error = "no state payload";
    return false;
  }
  (void)header_bytes;
  /* The core tells us the exact size it accepts; the caller checks it. We only
   * need to inflate to a generous bound here. */
  size_t cap = body.size() * 16u + 65536u;
  std::vector<uint8_t> raw;
  raw.resize(cap);
  size_t got = 0;
  if (decompress_mem(body.data(), body.size(), raw.data(), raw.size(), &got) < 0) {
    if (body.size() > cap) {
      if (error)
        *error = "state too large";
      return false;
    }
    out->state = body;
    return true;
  }
  raw.resize(got);
  out->state.swap(raw);
  return true;
}
} /* namespace */

bool state_read(const std::string &path, SaveStateHeader *out, const std::string &expect_rom_hex,
                const std::string &expect_core, bool want_thumb, std::string *error) {
  std::vector<uint8_t> v;
  if (!read_whole_file(path.c_str(), &v)) {
    if (error)
      *error = "cannot read " + path;
    return false;
  }
  if (!parse(v, out, true, want_thumb, error))
    return false;
  if (!expect_rom_hex.empty()) {
    uint64_t want = strtoull(expect_rom_hex.c_str(), nullptr, 16);
    if (want && out->rom_fingerprint != want) {
      if (error)
        *error = "state belongs to a different ROM";
      return false;
    }
  }
  if (!expect_core.empty() && !out->core_name.empty() && out->core_name != expect_core) {
    if (error)
      *error = "state belongs to core '" + out->core_name + "', not '" + expect_core + "'";
    return false;
  }
  return true;
}

bool state_read_header(const std::string &path, SaveStateHeader *out, std::string *error) {
  std::vector<uint8_t> v;
  if (!read_whole_file(path.c_str(), &v)) {
    if (error)
      *error = "cannot read " + path;
    return false;
  }
  return parse(v, out, false, true, error);
}

std::string sram_path_for(const std::string &save_dir, const std::string &rom_path) {
  return path_join(save_dir, path_basename_noext(rom_path) + ".srm");
}

bool sram_save(const std::string &path, const void *data, size_t len) {
  return write_whole_file(path.c_str(), data, len);
}

bool sram_load(const std::string &path, std::vector<uint8_t> *out) {
  return read_whole_file(path.c_str(), out);
}

void thumb_downscale(const uint8_t *src, uint32_t w, uint32_t h, uint32_t pitch, uint32_t max_w,
                     uint32_t max_h, std::vector<uint8_t> *out, uint32_t *out_w,
                     uint32_t *out_h) {
  if (!src || !w || !h) {
    *out_w = *out_h = 0;
    return;
  }
  uint32_t sx = 1, sy = 1;
  while (w / sx > max_w)
    sx++;
  while (h / sy > max_h)
    sy++;
  uint32_t ow = w / sx, oh = h / sy;
  if (!ow)
    ow = 1;
  if (!oh)
    oh = 1;
  out->assign(size_t(ow) * oh * 4u, 0);
  for (uint32_t y = 0; y < oh; y++) {
    const uint8_t *row = src + size_t(y * sy) * pitch;
    uint8_t *dst = out->data() + size_t(y) * ow * 4u;
    for (uint32_t x = 0; x < ow; x++) {
      const uint8_t *px = row + size_t(x * sx) * 4u;
      /* 2x2 box average keeps edges from crawling without a float loop. */
      uint32_t acc[4] = {0, 0, 0, 0};
      uint32_t cnt = 0;
      for (uint32_t dy = 0; dy < sy && (y * sy + dy) < h; dy++) {
        const uint8_t *r2 = src + size_t(y * sy + dy) * pitch + size_t(x * sx) * 4u;
        for (uint32_t dx = 0; dx < sx && (x * sx + dx) < w; dx++) {
          for (int c = 0; c < 4; c++)
            acc[c] += r2[dx * 4u + c];
          cnt++;
        }
      }
      if (!cnt)
        cnt = 1;
      for (int c = 0; c < 4; c++)
        dst[c] = uint8_t(acc[c] / cnt);
      dst[3] = 0xFF;
      dst += 4;
    }
  }
  *out_w = ow;
  *out_h = oh;
}

} /* namespace mb */
