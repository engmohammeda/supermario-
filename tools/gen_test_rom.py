#!/usr/bin/env python3
"""Generate the MarioBox NES probe ROM (original image — CC0, nothing third party).

Why generate a ROM instead of downloading a test suite: every assertion in the
host test must hold *exactly*, and what we are pinning is the emulator's own
behaviour. This image drives three independent axes at once, so a regression
shows up somewhere specific rather than as "the screen looks off":

  CPU   $07F2 counts main-loop iterations   → instructions really execute
  PPU   each vblank changes a palette entry and the fine-x scroll
                                           → the framebuffer must change every
                                             frame and be bit-reproducible
  BUS   $07F0 counts NMIs and is mirrored into battery-backed ExRAM at $6000
                                           → save-state/rewind/SRAM round trips
                                             have a value to compare

Memory contract relied on by engine/src/test/native/test_host.cpp:
  $07F0  NMI (frame) counter
  $07F1  last value of $07F0 seen by the main loop
  $07F2  main-loop iteration counter
  $07F3  value found at $6000 when the machine booted — the only evidence that
          battery RAM was restored *before* the program ran, since the first
          vblank overwrites $6000
  $07FA  polled by the main loop each iteration and never written by the game:
          a cheat that writes here is visible to the game at $07FB, which is
          what makes the cheat test race-free
  $07FB  last value read from $07FA
  $6000  low byte of $07F0, in 8 KiB of battery-backed work RAM

The assembler is intentionally tiny — labels, .byte, and the addressing modes
this program uses — and resolves every branch and label on a second pass, so no
address in the image is hand-pinned and the program cannot drift out of sync
with itself.
"""
from __future__ import annotations

import argparse
import os
import re
import struct

IMPLIED = {
    "nop": 0xEA, "sei": 0x78, "cli": 0x58, "cld": 0xD8, "sed": 0xF8,
    "clc": 0x18, "sec": 0x38, "cli2": 0x58, "pha": 0x48, "pla": 0x68,
    "php": 0x08, "plp": 0x28, "tay": 0xA8, "tax": 0xAA, "tya": 0x98,
    "txa": 0x8A, "iny": 0xC8, "inx": 0xE8, "dey": 0x88, "dex": 0xCA,
    "txs": 0x9A, "tsx": 0xBA, "rti": 0x40, "rts": 0x60,
}

# mode → opcode. Absent modes are unsupported and raise.
OPS = {
    "lda": {"imm": 0xA9, "zp": 0xA5, "abs": 0xAD, "absx": 0xBD, "absy": 0xB9, "ind": 0xB1},
    "sta": {"zp": 0x85, "abs": 0x8D, "absx": 0x9D, "absy": 0x99, "ind": 0x81},
    "cmp": {"imm": 0xC9, "zp": 0xC5, "abs": 0xCD},
    "cpx": {"imm": 0xE0, "zp": 0xE4, "abs": 0xEC},
    "cpy": {"imm": 0xC0, "zp": 0xC4, "abs": 0xCC},
    "and": {"imm": 0x29, "zp": 0x25, "abs": 0x2D},
    "ora": {"imm": 0x09, "zp": 0x05, "abs": 0x0D},
    "eor": {"imm": 0x49, "zp": 0x45, "abs": 0x4D},
    "adc": {"imm": 0x69, "zp": 0x65, "abs": 0x6D},
    "sbc": {"imm": 0xE9, "zp": 0xE5, "abs": 0xED},
    "inc": {"zp": 0xE6, "abs": 0xEE},
    "dec": {"zp": 0xC6, "abs": 0xCE},
    "ldx": {"imm": 0xA2, "zp": 0xA6, "abs": 0xAE},
    "ldy": {"imm": 0xA0, "zp": 0xA4, "abs": 0xAC},
    "bit": {"zp": 0x24, "abs": 0x2C},
}
BRANCH = {"beq": 0xF0, "bne": 0xD0, "bcc": 0x90, "bcs": 0xB0, "bmi": 0x30,
          "bpl": 0x10, "bvc": 0x50, "bvs": 0x70}

SOURCE = r"""
        sei
        cld
        ldx     #$ff
        txs
        lda     #$00
        sta     $2001           ; display off while the picture is built
        sta     $2000
        sta     $2005           ; x scroll
        sta     $2005           ; y scroll

; Name table: 1024 bytes of incrementing tile indices, so every tile in the
; 8 KiB pattern bank is fetched at least once by a real PPU read.
        lda     #$20
        sta     $2006           ; PPU address high = $2000
        lda     #$00
        sta     $2006
        ldx     #$00
        ldy     #$00
fill    tya
        sta     $2007
        iny
        bne     fill
        inx
        cpx     #$04
        beq     fill_done
        ldy     #$00
        jmp     fill
fill_done

; Palette: $3F00..$3F1F ← 0x00..0x1F. A ramp makes any bit drift visible in
; the framebuffer hash instead of hiding inside a plausible image.
        lda     #$3f
        sta     $2006
        lda     #$00
        sta     $2006
        ldx     #$00
pal     txa
        sta     $2007
        inx
        cpx     #$20
        bne     pal

; NMI on vblank, background and sprites on.
        lda     #$88
        sta     $2000
        lda     #$1e
        sta     $2001
; Battery RAM: whatever the previous session left at $6000 is copied out to
; $07F3 exactly once, before NMI is enabled — so the SRAM restore test can tell
; "the file was loaded into the cart" apart from "the game happened to write a
; similar number".
        lda     $6000
        sta     $07f3
        lda     #$00
        sta     $07f0
        sta     $07f1
        sta     $07f2
        sta     $07fa
        sta     $07fb

; Main loop: proves instructions execute, mirrors the frame counter through a
; normal load/store pair (which the peek/poke path must agree with), and polls
; $07FA — an address the game never writes, so a cheat applied there is
; observable at $07FB without racing the emulator's own frame timing.
main    inc     $07f2
        lda     $07f0
        sta     $07f1
        lda     $07fa
        sta     $07fb
        jmp     main

; ---------------------------------------------------------------------------
; NMI — one frame of animation, one byte of battery RAM, then acknowledge.
; ---------------------------------------------------------------------------
nmi     pha
        txa
        pha
        tya
        pha
        inc     $07f0
        lda     $07f0
        and     #$07
        tay                     ; palette slot 0..7
; Write the palette through the $2006/$2007 address/data ports, never through
; the $3F00 mirror. Real hardware gives $3F00-$3FFF to the palette, but the
; classic PPU in FCEUmm mirrors the $2000-$2007 registers across the whole
; $2000-$3FFF window, so `sta $3F00,Y` lands on $2000+Y: it rewrites $2000
; (clearing the tile-select and, once the value changes, leaving the PPU in a
; mode where rendering stops) and $2001, and vblank NMIs then never fire again.
; That looked exactly like a host-side frame-loop bug for a long time.
        lda     #$3f
        sta     $2006
        tya
        sta     $2006
        lda     $07f0
        clc
        adc     #$10
        and     #$3f            ; stay inside the NES colour index range
        sta     $2007           ; data port: the address auto-incremented to $3F00+Y
        lda     $07f0
        and     #$07
        sta     $2005           ; fine-x scroll: the picture must move
        lda     $07f0
        sta     $6000           ; battery RAM, read back by the SRAM test
; Acknowledge vblank: reading $2002 clears the flag, which is what the picture
; generator and any sprite-0-hit logic then expect. The two reads are the
; hardware-safe pattern (the flag is set a cycle or two after vblank begins, so
; a handler that reads immediately can see the stale value). An earlier version
; of this file claimed a single read would stop NMIs forever; measured against
; FCEUmm that is false -- the real cause was the $3F00 write above.
        lda     $2002
        lda     $2002
        pla
        tay
        pla
        tax
        pla
        rti

irq     rti
"""


def _num(tok: str, labels: dict[str, int] | None) -> int:
    tok = tok.strip().lstrip("#").strip()
    low = tok.lower()
    if labels and low in labels:
        return labels[low]
    if re.fullmatch(r"\$[0-9a-f]+", low):
        return int(low[1:], 16)
    if re.fullmatch(r"\d+", low):
        return int(low)
    raise ValueError(f"unresolved operand {tok!r}")


def _split(rest: str) -> tuple[str, str]:
    """(mode, operand-text) for the modes this program needs."""
    rest = rest.strip()
    if rest.startswith("#"):
        return "imm", rest[1:]
    m = re.fullmatch(r"([^,]+)\s*,\s*([xXyY])", rest)
    if m:
        return ("absx" if m.group(2).lower() == "x" else "absy"), m.group(1)
    m = re.fullmatch(r"\(([^)]+)\)\s*,\s*[yY]", rest)
    if m:
        return "ind", m.group(1)
    return "addr", rest


def _size(mnem: str, rest: str, labels: dict[str, int] | None = None) -> int:
    if mnem in IMPLIED:
        return 1
    if mnem == ".byte":
        return len(rest.split(","))
    if mnem in BRANCH or mnem in ("jmp", "jsr"):
        return 2 if mnem in BRANCH else 3
    mode, operand = _split(rest)
    table = OPS[mnem]
    if mode == "imm":
        return 2
    if mode == "addr":
        # Zero-page only if the operand is a literal that fits, because in
        # pass 1 an unresolved label cannot be sized any other way. All labels
        # in this program are branch targets, never data operands.
        if "zp" in table and re.fullmatch(r"\$[0-9a-f]+", operand.strip().lower()) \
                and _num(operand, None) <= 0xFF:
            return 2
        return 3
    if mode == "ind":
        return 2
    return 3


def _parse(src: str) -> list[tuple]:
    """One normalised pass over the source: (label, mnemonic, operand, addr).

    Labels may share a line with the instruction they sit on, which is how
    6502 source is normally written and the only way this program stays
    readable.
    """
    known = set(IMPLIED) | set(OPS) | set(BRANCH) | {"jmp", "jsr"}
    items: list[tuple] = []
    pc = 0xC000
    for raw in src.splitlines():
        line = re.sub(r";.*$", "", raw).strip()
        if not line:
            continue
        label = None
        if line.endswith(":"):
            label, line = line[:-1].lower(), ""
        else:
            toks = line.split(None, 1)
            if toks[0].lower() not in known:
                # bare label, or label sharing the line with an instruction
                label = toks[0].lower()
                line = toks[1] if len(toks) > 1 else ""
        if line:
            m = re.match(r"^(\.?\w+)\s*(.*)$", line)
            mnem, rest = m.group(1).lower(), m.group(2)
            if label is not None:
                items.append((label, None, None, pc))
            items.append((None, mnem, rest, pc))
            pc += _size(mnem, rest, None) if mnem not in (".byte",) else \
                len(rest.split(","))
            continue
        if label is not None:
            items.append((label, None, None, pc))
    return items


def assemble(src: str, origin: int = 0xC000, span: int = 0x4000) -> tuple[bytes, dict]:
    items = _parse(src)
    labels = {lb: addr for lb, _m, _o, addr in items if lb is not None}

    out = bytearray(span)
    for lb, mnem, rest, addr in items:
        if mnem is None:
            continue
        if mnem == ".byte":
            enc = [_num(tok, labels) & 0xFF for tok in rest.split(",")]
        else:
            enc = _encode(mnem, rest, addr, labels)
        want = _size(mnem, rest, labels) if mnem != ".byte" else len(enc)
        if len(enc) != want:
            raise AssertionError(f"{mnem} {rest} at ${addr:04X}: sized {want}, encoded {len(enc)}")
        for i, b in enumerate(enc):
            out[addr - origin + i] = b & 0xFF
    return bytes(out), labels


def _encode(mnem: str, rest: str, pc: int, labels: dict[str, int]) -> list[int]:
    if mnem in IMPLIED:
        return [IMPLIED[mnem]]
    if mnem in BRANCH:
        rel = _num(rest, labels) - (pc + 2)
        if not -128 <= rel <= 127:
            raise ValueError(f"branch out of range at ${pc:04X}")
        return [BRANCH[mnem], rel & 0xFF]
    if mnem == "jmp":
        return [0x4C, *struct.pack("<H", _num(rest, labels))]
    if mnem == "jsr":
        return [0x20, *struct.pack("<H", _num(rest, labels))]
    mode, operand = _split(rest)
    table = OPS.get(mnem)
    if table is None:
        raise ValueError("unknown mnemonic " + mnem)
    if mode == "imm":
        return [table["imm"], _num(operand, labels) & 0xFF]
    value = _num(operand, labels)
    if mode == "addr":
        if "zp" in table and value <= 0xFF:
            return [table["zp"], value & 0xFF]
        return [table["abs"], *struct.pack("<H", value)]
    if mode == "ind":
        return [table["ind"], value & 0xFF]
    return [table[mode], *struct.pack("<H", value)]


def build_rom() -> bytes:
    header = bytearray(16)
    header[0:4] = b"NES\x1a"
    header[4] = 1        # 1 x 16 KiB PRG
    header[5] = 1        # 1 x 8 KiB CHR
    header[6] = 0x02     # flags6: battery-backed WRAM, horizontal mirroring
    header[7] = 0x00     # iNES 1.0 (no NES 2.0 marker)
    header[8] = 1        # 1 x 8 KiB WRAM
    header[9] = 0        # no CHR RAM
    prg, labels = assemble(SOURCE)
    # Vectors: NMI, RESET, IRQ — placed in the last six bytes of the bank.
    prg = bytearray(prg)
    for name, addr in (("nmi", 0xFFFA), ("reset", 0xFFFC), ("irq", 0xFFFE)):
        target = labels[name] if name in labels else {"reset": 0xC000}[name]
        prg[addr - 0xC000 : addr - 0xC000 + 2] = struct.pack("<H", target)
    prg = bytes(prg)
    assert len(prg) == 16384
    # CHR: a deterministic gradient, so reading the wrong pattern table shows
    # up as a different hash rather than another plausible-looking picture.
    chr_ = bytes(((i * 37) + (i >> 3)) & 0xFF for i in range(8192))
    return bytes(header) + prg + chr_


def main() -> int:
    ap = argparse.ArgumentParser(description="build the MarioBox probe ROM")
    ap.add_argument("out", nargs="?", help="path of the .nes file to write")
    ap.add_argument("--map", action="store_true", help="print the label map")
    args = ap.parse_args()
    if args.map:
        _, labels = assemble(SOURCE)
        for k in sorted(labels, key=lambda x: labels[x]):
            print(f"${labels[k]:04X}  {k}")
    if not args.out:
        print(len(build_rom()))
        return 0
    parent = os.path.dirname(os.path.abspath(args.out))
    os.makedirs(parent, exist_ok=True)
    with open(args.out, "wb") as f:
        f.write(build_rom())
    print(f"wrote {args.out} ({os.path.getsize(args.out)} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
