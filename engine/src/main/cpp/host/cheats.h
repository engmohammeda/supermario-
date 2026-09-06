/* cheats.h — cheat code decoding and the host-side cheat list.
 *
 * Two engines share one list:
 *
 *  * `FCEUmm` exposes its own cheat engine (Game Genie *and* Par decoding, with
 *    mapper-aware writes and ROM patching). When the loaded core has it, codes
 *    are forwarded verbatim because only the core knows which bank is
 *    currently in view — which is exactly the case a frontend gets wrong.
 *
 *  * For any other libretro core (and for the desktop tests) the host applies
 *    codes itself, once per frame, through the core's peek/poke surface.
 *
 * The decoders below are bit-for-bit compatible with FCEUmm's, so a code typed
 * into the UI means the same thing on both paths, and the unit tests can pin
 * that behaviour without a core loaded.
 */
#ifndef MARIOBOX_CHEATS_H
#define MARIOBOX_CHEATS_H

#include <cstdint>
#include <string>
#include <vector>

namespace mb {

enum CheatKind {
  kCheatAuto = 0,
  kCheatGameGenie = 1, /* 6 or 8 chars from the A–P / 0–9 alphabet */
  kCheatPar = 2,       /* 8 hex digits: PP AA_hi AA_lo VAL */
  kCheatAR = 3,        /* Action Replay NES — same wire format as Par */
  kCheatRaw = 4,       /* "$DDAA:VV", "DDAA=VV", "DDAA,VV"  (+ "?CC" compare) */
};

struct DecodedCheat {
  bool ok = false;
  int kind = kCheatAuto;
  uint32_t address = 0;
  uint32_t value = 0;
  int32_t compare = -1; /* -1 = no compare */
  int core_type = 0;    /* the core's own type field (0 = direct, 1 = mapper) */
  std::string error;
};

/* `hint` may be kCheatAuto, in which case the shape of `text` picks the kind. */
DecodedCheat cheat_decode(const std::string &text, int hint);

/* An entry as the UI sees it. `core_index` is the slot in the core's own list
 * when the native engine is in use, otherwise -1. */
struct CheatEntry {
  int32_t id = 0;
  std::string code;
  std::string desc;
  bool enabled = true;
  int kind = kCheatAuto;
  DecodedCheat decoded;
  int32_t core_index = -1;
};

class CheatList {
public:
  int32_t add(CheatEntry e); /* returns the assigned id, or -1 on bad code */
  bool remove(int32_t id);
  bool set_enabled(int32_t id, bool enabled);
  bool set_value(int32_t id, uint32_t value);
  void clear();
  size_t size() const { return items_.size(); }
  const std::vector<CheatEntry> &items() const { return items_; }
  /* Which slot the core's own cheat engine assigned to entry `index`. That is
   * bookkeeping the list owns; it is written here rather than by handing out a
   * mutable reference, which would let callers edit codes behind `dirty_`. */
  void set_core_index(size_t index, int32_t core_index) {
    if (index < items_.size())
      items_[index].core_index = core_index;
  }
  /* Anything changed since the last sync? The emu thread uses this to avoid
   * touching the core on frames where nothing was edited. */
  bool dirty() const { return dirty_; }
  void mark_clean() { dirty_ = false; }

private:
  std::vector<CheatEntry> items_;
  int32_t next_id_ = 1;
  bool dirty_ = false;
};

} /* namespace mb */
#endif /* MARIOBOX_CHEATS_H */
