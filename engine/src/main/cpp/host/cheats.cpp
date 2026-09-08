/* cheats.cpp */
#include "cheats.h"

#include <cctype>
#include <cstdio>
#include <cstdlib>
#include <cstring>

namespace mb {
namespace {

/* The Game Genie alphabet is not A..P in order: the physical device encoded
 * nibbles in the sequence A P Z L G I T Y E O X U K S V N, so 'P' is 1 and 'N'
 * is 15. This table is FCEUmm's own GGtobin() (src/cheat.c), transcribed rather
 * than reinvented — the native test asserts that the two decoders agree on
 * every code, and this ordering is the single place a hand-written frontend
 * decoder gets it wrong most often. */
int gg_value(char c) {
  static const char kLetters[16] = {'A', 'P', 'Z', 'L', 'G', 'I', 'T', 'Y',
                                    'E', 'O', 'X', 'U', 'K', 'S', 'V', 'N'};
  if (c >= 'a' && c <= 'z')
    c = char(c - 'a' + 'A');
  for (int i = 0; i < 16; i++)
    if (kLetters[i] == c)
      return i;
  /* FCEUmm maps any character outside the alphabet to 0 instead of failing; the
   * host rejects it, because a code typed with a digit in it is a typo and
   * silently decoding it as 'A' would add a cheat the user never asked for. */
  return -1;
}

int hex_value(char c) {
  if (c >= '0' && c <= '9')
    return c - '0';
  if (c >= 'a' && c <= 'f')
    return 10 + (c - 'a');
  if (c >= 'A' && c <= 'F')
    return 10 + (c - 'A');
  return -1;
}

std::string strip_code(std::string s) {
  std::string out;
  out.reserve(s.size());
  for (char c : s) {
    if (c == ' ' || c == '-' || c == '+' || c == '\t' || c == '\r' || c == '\n')
      continue;
    out.push_back(c);
  }
  return out;
}

/* Mirrors FCEUI_DecodeGG(). Kept as a literal port so that "same code, same
 * effect" holds between the native and host paths. */
bool decode_game_genie(const std::string &code, DecodedCheat *out) {
  size_t n = code.size();
  if (n != 6 && n != 8)
    return false;
  int t[8];
  for (size_t i = 0; i < n; i++) {
    t[i] = gg_value(code[i]);
    if (t[i] < 0)
      return false;
  }
  uint32_t A = 0x8000, V = 0, C = 0;

  V |= (uint32_t)(t[0] & 0x07);
  V |= (uint32_t)((t[0] & 0x08) << 4);

  V |= (uint32_t)(t[1] & 0x07) << 4;
  A |= (uint32_t)((t[1] & 0x08) << 4);

  A |= (uint32_t)(t[2] & 0x07) << 4;

  A |= (uint32_t)(t[3] & 0x07) << 12;
  A |= (uint32_t)(t[3] & 0x08);

  A |= (uint32_t)(t[4] & 0x07);
  A |= (uint32_t)((t[4] & 0x08) << 8);

  if (n == 6) {
    A |= (uint32_t)(t[5] & 0x07) << 8;
    V |= (uint32_t)(t[5] & 0x08);
    out->compare = -1;
  } else {
    A |= (uint32_t)(t[5] & 0x07) << 8;
    C |= (uint32_t)(t[5] & 0x08);

    C |= (uint32_t)(t[6] & 0x07);
    C |= (uint32_t)((t[6] & 0x08) << 4);

    C |= (uint32_t)(t[7] & 0x07) << 4;
    V |= (uint32_t)(t[7] & 0x08);
    out->compare = int32_t(C);
  }
  out->address = A & 0xFFFF;
  out->value = V & 0xFF;
  /* FCEUmm's own retro_cheat_set() adds Game Genie codes as type 1
   * ("substitute", which XORs the byte the bus would have returned) and falls
   * back to type 0 below $0100 because zero-page accesses skip the read
   * handlers. Using 0 here would silently change what a code means. */
  out->core_type = (out->address < 0x0100u) ? 0 : 1;
  return true;
}

/* Mirrors FCEUI_DecodePAR(): "PP AAhi AAlo VV". */
bool decode_par(const std::string &code, DecodedCheat *out) {
  if (code.size() != 8)
    return false;
  int b[4];
  for (size_t i = 0; i < 4; i++) {
    int hi = hex_value(code[i * 2]);
    int lo = hex_value(code[i * 2 + 1]);
    if (hi < 0 || lo < 0)
      return false;
    b[i] = (hi << 4) | lo;
  }
  out->compare = -1;
  out->value = uint32_t(b[3]);
  out->address = uint32_t(b[2] | (b[1] << 8));
  /* Zero-page addresses bypass the core's read/write handlers, so the direct
   * (RAM pointer) method is used for them. */
  out->core_type = (out->address < 0x0100) ? 0 : 1;
  return true;
}

/* "$DDAA:VV?CC" — raw write with an optional compare byte. */
bool decode_raw(const std::string &code, DecodedCheat *out) {
  std::string s = code;
  if (!s.empty() && (s[0] == '$' || (s[0] == '0' && (s.size() > 1 && (s[1] == 'x' || s[1] == 'X'))))) {
    if (s[0] == '$')
      s.erase(s.begin());
    else
      s.erase(0, 2);
  }
  size_t sep = s.find_first_of(":,= ");
  if (sep == std::string::npos)
    return false;
  std::string as = s.substr(0, sep);
  std::string rest = s.substr(sep + 1);
  if (as.size() > 4 || as.empty())
    return false;
  uint32_t addr = 0;
  for (char c : as) {
    int hv = hex_value(c);
    if (hv < 0)
      return false;
    addr = (addr << 4) | uint32_t(hv);
  }
  std::string vs = rest;
  int32_t cmpv = -1;
  size_t q = vs.find('?');
  if (q != std::string::npos) {
    std::string cs = vs.substr(q + 1);
    vs = vs.substr(0, q);
    if (cs.size() != 2)
      return false;
    int h1 = hex_value(cs[0]), l1 = hex_value(cs[1]);
    if (h1 < 0 || l1 < 0)
      return false;
    cmpv = (h1 << 4) | l1;
  }
  if (vs.size() > 2 || vs.empty())
    return false;
  uint32_t val = 0;
  for (char c : vs) {
    int hv = hex_value(c);
    if (hv < 0)
      return false;
    val = (val << 4) | uint32_t(hv);
  }
  out->address = addr;
  out->value = val;
  out->compare = cmpv;
  out->core_type = (addr < 0x0100) ? 0 : 1;
  return true;
}

} /* namespace */

DecodedCheat cheat_decode(const std::string &text, int hint) {
  DecodedCheat d;
  std::string s = strip_code(text);
  if (s.empty()) {
    d.error = "empty code";
    return d;
  }

  auto try_kind = [&](int k) -> bool {
    DecodedCheat t;
    bool ok = false;
    switch (k) {
    case kCheatGameGenie:
      ok = decode_game_genie(s, &t);
      break;
    case kCheatPar:
    case kCheatAR:
      ok = decode_par(s, &t);
      break;
    case kCheatRaw:
      ok = decode_raw(s, &t);
      break;
    default:
      ok = false;
      break;
    }
    if (ok) {
      t.ok = true;
      t.kind = k;
      d = t;
    }
    return ok;
  };

  if (hint != kCheatAuto) {
    if (try_kind(hint))
      return d;
    d.error = "code does not match the selected format";
    return d;
  }

  /* Auto-detect. Raw wins first because it is unambiguous (it carries a
   * separator); then the shape of a plain string decides GG vs Par. */
  if (s.find_first_of(":,=?") != std::string::npos) {
    if (try_kind(kCheatRaw))
      return d;
  }
  bool all_gg = true, all_hex = false;
  for (char c : s) {
    if (gg_value(c) < 0)
      all_gg = false;
    if (hex_value(c) >= 0)
      all_hex = true;
  }
  if ((s.size() == 6 || s.size() == 8) && all_gg && try_kind(kCheatGameGenie))
    return d;
  if (s.size() == 8 && all_hex && try_kind(kCheatPar))
    return d;
  if (try_kind(kCheatRaw))
    return d;

  d.error = "unrecognised cheat format";
  return d;
}

/* -------------------------------------------------------------- CheatList */
int32_t CheatList::add(CheatEntry e) {
  if (e.code.empty())
    return -1;
  e.decoded = cheat_decode(e.code, e.kind);
  if (!e.decoded.ok)
    return -1;
  e.kind = e.decoded.kind;
  e.id = next_id_++;
  items_.push_back(std::move(e));
  dirty_ = true;
  return items_.back().id;
}

bool CheatList::remove(int32_t id) {
  for (size_t i = 0; i < items_.size(); i++) {
    if (items_[i].id == id) {
      items_.erase(items_.begin() + long(i));
      dirty_ = true;
      return true;
    }
  }
  return false;
}

bool CheatList::set_enabled(int32_t id, bool enabled) {
  for (auto &e : items_) {
    if (e.id == id) {
      if (e.enabled == enabled)
        return true;
      e.enabled = enabled;
      dirty_ = true;
      return true;
    }
  }
  return false;
}

bool CheatList::set_value(int32_t id, uint32_t value) {
  for (auto &e : items_) {
    if (e.id == id) {
      e.decoded.value = value & 0xFF;
      dirty_ = true;
      return true;
    }
  }
  return false;
}

void CheatList::clear() {
  if (!items_.empty())
    dirty_ = true;
  items_.clear();
}

} /* namespace mb */
