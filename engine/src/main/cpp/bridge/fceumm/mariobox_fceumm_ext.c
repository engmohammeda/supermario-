/* mariobox_fceumm_ext.c — MarioBox extension surface for the FCEUmm-next core.
 *
 * Lives OUTSIDE third_party/ on purpose: the vendored core stays byte-for-byte
 * upstream so it can be re-synced without conflict resolution. Everything we
 * need beyond the libretro API — raw bus peek/poke and the core's own Game
 * Genie/Par cheat engine (which, unlike a frontend implementation, can patch
 * mapper-mirrored PRG ROM) — is reached from this translation unit, which is
 * compiled *into* the core .so and links against the core's internals.
 *
 * The functions below are exported and resolved by the host with dlsym() after
 * loading the core. They are only ever called from the emulation thread, which
 * is the thread the core itself runs on, so no locking is needed here.
 */

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "fceu-types.h"
#include "fceu.h"
#include "driver.h"
#include "cart.h"
#include "cheat.h"

#define MBX_EXPORT __attribute__((visibility("default")))

MBX_EXPORT uint32_t mbx_fceumm_supported(void) { return 1u; }

/* Raw peek/poke deliberately is NOT implemented here.  MMapPtrs[] lives in the
 * core's cheat.c and is only filled by FCEU_CheatAddRAM(), i.e. cart WRAM and
 * (for some mappers) PRG pages — never the 2 KB of internal work RAM at
 * $0000-$07FF.  A page-table shortcut therefore looks plausible while reading
 * the wrong bytes for most addresses.  The host resolves addresses through the
 * core's published memory map and retro_get_memory_data(), which is documented,
 * core-independent, and correct for the windows a UI actually touches. */

/* ------------------------------------------------------------------------- */
/* Cheats: forward to the core.                                              */
/*                                                                           */
/* FCEUmm keeps its own cheat list and rebuilds a "sub-cheat" read/write     */
/* table whenever it changes, so codes take effect on the next frame without  */
/* the frontend having to know which page a mapper has currently banked in.   */
/* FCEUI_AddCheat() always appends and enables; toggling goes through        */
/* FCEUI_SetCheat(), which needs the entry's current fields back.             */
/*                                                                           */
/* Do NOT call FCEU_PowerCheats() here. It is the emulator's power-on hook:   */
/* it clears `numsubcheats` and then rebuilds, which throws away the bookkeeping*/
/* for every read handler a type-1 ("substitute") cheat installed — and those  */
/* handlers are only ever removed by RebuildSubCheats() walking that list. So a */
/* frontend that calls it leaves a disabled or deleted cheat patching the bus   */
/* forever. AddCheat/SetCheat/ResetCheats each rebuild on their own, which is  */
/* all a cheat list edit needs; FCEUI_DelCheat() is the one function that does  */
/* not, hence mbx_disable_before_delete() below. This cost a full day to find:  */
/* the symptoms were "cheat off, byte still wrong" with an empty core list.     */
/* ------------------------------------------------------------------------- */

static uint32_t g_cheat_count;

static int cheat_count_cb(char *name, uint32_t a, uint8_t v, int compare, int s, int type,
                          void *data) {
  (void)name;
  (void)a;
  (void)v;
  (void)compare;
  (void)s;
  (void)type;
  (void)data;
  g_cheat_count++;
  return 1; /* keep walking */
}

static uint32_t mbx_cheat_count(void) {
  g_cheat_count = 0;
  FCEUI_ListCheats(cheat_count_cb, NULL);
  return g_cheat_count;
}

MBX_EXPORT uint32_t mbx_fceumm_cheat_count(void) { return mbx_cheat_count(); }

MBX_EXPORT int32_t mbx_fceumm_cheat_add_gg(const char *name, const char *code) {
  char buf[16];
  size_t n;
  uint16_t a;
  uint8_t v;
  int c;
  char *p;

  if (!code)
    return -1;
  n = strlen(code);
  if ((n != 6 && n != 8) || n >= sizeof(buf))
    return -1;
  memcpy(buf, code, n + 1);
  for (p = buf; *p; p++) {
    if (*p >= 'a' && *p <= 'z')
      *p = (char)(*p - 'a' + 'A');
    switch (*p) {
    case 'A': case 'P': case 'Z': case 'L': case 'G': case 'I': case 'T': case 'Y':
    case 'E': case 'O': case 'X': case 'U': case 'K': case 'S': case 'V': case 'N':
      break;
    default:
      return -1; /* the real Game Genie alphabet, as FCEUmm's GGtobin accepts it */
    }
  }
  if (!FCEUI_DecodeGG(buf, &a, &v, &c))
    return -1;
  /* Matches the core's own retro_cheat_set(): GG codes are "substitute" cheats
   * (type 1) except in the zero page, which bypasses the read handlers. */
  if (!FCEUI_AddCheat(name && *name ? name : buf, a, v, c, a < 0x0100u ? 0 : 1))
    return -1;
  return (int32_t)mbx_cheat_count() - 1;
}

MBX_EXPORT int32_t mbx_fceumm_cheat_add_par(const char *name, const char *code) {
  uint16_t a;
  uint8_t v;
  int c;
  int type;
  char buf[16];
  size_t n;
  char *p;

  if (!code)
    return -1;
  n = strlen(code);
  if (n != 8 || n >= sizeof(buf))
    return -1;
  memcpy(buf, code, n + 1);
  for (p = buf; *p; p++) {
    if (*p >= 'a' && *p <= 'f')
      *p = (char)(*p - 'a' + 'A');
    if (!((*p >= '0' && *p <= '9') || (*p >= 'A' && *p <= 'F')))
      return -1;
  }
  if (!FCEUI_DecodePAR(buf, &a, &v, &c, &type))
    return -1;
  if (!FCEUI_AddCheat(name && *name ? name : buf, a, v, c, type))
    return -1;
  return (int32_t)mbx_cheat_count() - 1;
}

/* Raw write: address is a CPU address, compare == -1 means "no compare".
 * `type` follows the core's own numbering (0 = write, others are the
 * "cheat code type" values used by the built-in cheat search). */
MBX_EXPORT int32_t mbx_fceumm_cheat_add_raw(const char *name, uint32_t addr, uint32_t value,
                                            int32_t compare, int32_t type) {
  if (!FCEUI_AddCheat(name && *name ? name : "poke", addr & 0xFFFFu, value & 0xFFu,
                      (int)compare, (int)type))
    return -1;
  return (int32_t)mbx_cheat_count() - 1;
}

/* FCEUI_SetCheat() takes the whole entry back, and a negative `a`, `v` or
 * `compare` means "leave that field alone" — so an edit that changes only the
 * status or only the value passes -1 for the rest.  The one field that is
 * assigned unconditionally is `type`, which is why each of these reads the
 * current entry first.
 *
 * Never pass FCEUI_GetCheat()'s `name` back in: SetCheat realloc()s that very
 * buffer and then strlcpy()s from the pointer it was given, which is the freed
 * old block whenever realloc moved it.  Passing NULL means "keep the name", and
 * is the only safe choice here. */
MBX_EXPORT int32_t mbx_fceumm_cheat_set_enabled(uint32_t index, int32_t enabled) {
  char *name = NULL;
  uint32_t a = 0;
  uint8_t v = 0;
  int compare = 0, status = 0, type = 0;

  if (!FCEUI_GetCheat(index, &name, &a, &v, &compare, &status, &type))
    return -1;
  if (status == (enabled ? 1 : 0))
    return 0;
  if (!FCEUI_SetCheat(index, NULL, -1, -1, -1, enabled ? 1 : 0, type))
    return -1;
  return 0;
}

MBX_EXPORT int32_t mbx_fceumm_cheat_set_value(uint32_t index, uint32_t value) {
  char *name = NULL;
  uint32_t a = 0;
  uint8_t v = 0;
  int compare = 0, status = 0, type = 0;

  if (!FCEUI_GetCheat(index, &name, &a, &v, &compare, &status, &type))
    return -1;
  if (!FCEUI_SetCheat(index, NULL, -1, (int32_t)(value & 0xFFu), -1, status, type))
    return -1;
  return 0;
}

/* Turning an entry off is part of deleting it, not a courtesy: a type-1
 * ("substitute") cheat installs a read handler on the emulated address, and only
 * RebuildSubCheats() takes it back off again.  FCEUI_SetCheat() and
 * FCEUI_ToggleCheat() call it; FCEUI_DelCheat() does not — so deleting an active
 * cheat would leave the byte patched forever. */
static int mbx_disable_before_delete(uint32_t index) {
  char *name = NULL;
  uint32_t a = 0, v = 0;
  int compare = 0, status = 0, type = 0;
  if (FCEUI_GetCheat(index, &name, &a, &v, &compare, &status, &type))
    return FCEUI_SetCheat(index, NULL, -1, -1, -1, 0, type) ? 1 : 0;
  return 0;
}

MBX_EXPORT int32_t mbx_fceumm_cheat_remove(uint32_t index) {
  mbx_disable_before_delete(index);
  if (!FCEUI_DelCheat(index))
    return -1;
  return 0;
}

MBX_EXPORT void mbx_fceumm_cheat_clear(void) {
  /* Deletion shifts the list down, so always drop index 0. The guard keeps a
   * broken core from turning this into an infinite loop. */
  int guard = 0;
  while (mbx_cheat_count() && guard++ < 8192) {
    mbx_disable_before_delete(0);
    FCEUI_DelCheat(0);
  }
}

/* Read back one entry as the CORE decoded it. The MarioBox cheat editor uses
 * this to prove its own decoders agree with FCEUmm's before a code is offered
 * to the user as valid — a frontend that re-implements Game Genie decoding
 * tends to drift exactly on the 8-character compare codes. */
MBX_EXPORT int32_t mbx_fceumm_cheat_get(uint32_t index, uint32_t *addr, uint32_t *val,
                                        int32_t *cmp, int32_t *status, int32_t *type,
                                        char *name, uint32_t name_len) {
  char *n = NULL;
  uint32_t a = 0;
  uint8_t v = 0;
  int c = 0, st = 0, t = 0;
  if (!FCEUI_GetCheat(index, &n, &a, &v, &c, &st, &t))
    return -1;
  if (addr)
    *addr = a;
  if (val)
    *val = v;
  if (cmp)
    *cmp = c;
  if (status)
    *status = st;
  if (type)
    *type = t;
  if (name && name_len)
    snprintf(name, name_len, "%s", n ? n : "");
  return 0;
}
