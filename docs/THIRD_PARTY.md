# Third-party code, licences, and how to re-verify any of it

Every byte of code MarioBox did not write is listed here with its licence, where it
came from, and the command that proves the statement. The project is GPL-3.0
(`LICENSE`); the core is GPL-2.0, which is compatible, and the repository is public,
so the corresponding-source obligation is met by the repository itself.

## Vendored emulation cores

| Path | Upstream | Ref (verified) | Licence | Modified? |
|---|---|---|---|---|
| `engine/src/main/cpp/third_party/fceumm_next/` | `github.com/negativeExponent/libretro-fceumm_next` | `d94fa342d289` (2026-05-19) | GPL-2.0-only | **no** — byte-for-byte |
| `engine/src/main/cpp/third_party/nescc/` | `github.com/anlohse/nescc_emu` | reference only | MIT | **not built** (see below) |

`fceumm_next` is vendored unmodified on purpose: it is 649 C files that track a
live upstream, and the day someone wants to re-sync it, `git diff` against the
upstream tarball has to be empty except for the two files below. The core's own
`Copying`, `Authors` and `changelog.txt` are kept in place.

### Proving the tree is unmodified

```sh
# Compare every vendored file against upstream's current default branch.
tmp=$(mktemp -d)
curl -sL https://codeload.github.com/negativeExponent/libretro-fceumm_next/tar.gz/refs/heads/master |
  tar -xz -C "$tmp" --strip-components=1
diff -ru "$tmp" engine/src/main/cpp/third_party/fceumm_next
```

Expected output: only the build glue we added (below) and files upstream does not
ship. Spot-checked during the last sync: `src/cheat.c`, `src/x6502.c` and
`libretro/libretro.c` are `cmp`-identical to upstream.

## What MarioBox adds to the core (and why it is not a core patch)

| File | Role |
|---|---|
| `engine/src/main/cpp/bridge/fceumm/mariobox_fceumm_ext.c` | Compiled **into** `libmbcore_fceumm.so`. Exposes `mbx_*` symbols (`dlsym`'d by the host) for the cheat operations the libretro ABI does not carry: `FCEUI_SetCheat`, `FCEUI_DelCheat`, `FCEU_PowerCheats`, and the `mbx_peek/mbx_poke` memory view. |
| `engine/src/main/cpp/fceumm_build_spec.cmake` | The core's source list, generated from `Makefile.common` by `tools/gen_cmake_core_list.py`. Regenerate after any vendored change; `make -C engine/src/test/native check-source-list` fails the build when it goes stale. |
| Build switches (not edits) | `-D__LIBRETRO__ -DHAVE_NTSC_FILTER` (via `HAVE_NTSC=1`), `-include libretro-common/include/compat/strl.h`, `-fno-builtin-sincos -fno-builtin-sincosf`, `-DPATH_MAX=1024` on Android. |

Two of those switches are load-bearing and worth recording, because both were
discovered by a test rather than by reading:

* `HAVE_NTSC=1` is what pulls in `src/ntsc/nes_ntsc.c` **and**
  `-DHAVE_NTSC_FILTER`. Dropping it silently removes the composite filter (the
  core's option table loses `fceumm_next_ntsc_filter` — which is how this project
  noticed it had been lost: an inline `# comment` after `HAVE_NTSC := 1` became
  part of the variable's value, so make compared `"1          "` to `"1"`).
* `-fno-builtin-sincos*` exists because GCC rewrites a `sin()/cos()` pair in
  `src/nsf.c` into one `sincos()` call. glibc and bionic ≥ 28 export it, older
  bionic does not, and a core `.so` with an unresolved symbol fails at `dlopen` on
  a user's phone with no diagnostic. The flag is cheaper than a crash report.

## `nescc` — vendored, not shipped

`third_party/nescc/` is kept as a design reference only (docs/PLAN.md §2 records
why: seven mappers cannot cover the Mario family's MMC3/VRC/BNROM boards). Its
libretro glue does not build against the tree as published, so
`MB_CORE_NESCC` defaults to `OFF` and enabling it is a **loud CMake failure**
rather than a half-built core. Adding a second core for real is a new
`add_library(mbcore_x SHARED …)` in `engine/src/main/cpp/CMakeLists.txt` plus a
`tools/` fetch script — the host is core-agnostic by construction (it `dlopen`s by
path and reads the option table the core publishes rather than any hardcoded keys).

## Runtime libraries

| Library | Where it comes from | Licence |
|---|---|---|
| `zlib` (`libz.so`) | Android NDK / system | zlib licence; optional at build time — `MB_HAVE_ZLIB` gates save-state compression and both container variants are readable either way |
| `libaaudio`, `libEGL`, `libGLESv2`, `libandroid`, `liblog` | Android system (NDK stubs at link time) | Apache-2.0 (AOSP); the EGL/GLES2 headers are Khronos' |
| Kotlin stdlib | Gradle | Apache-2.0 |
| `androidx.core`, `androidx.activity`, `androidx.lifecycle`, Compose BOM (`ui`, `foundation`, `material3`) | Google Maven | Apache-2.0 |
| `kotlinx.coroutines` | Maven Central | Apache-2.0 |
| JUnit 4 | Maven Central | EPL-1.0 (test-only) |

No ROMs, no BIOS files, no Nintendo assets, and no cheat database containing
copyrighted material are distributed with this repository (docs/PLAN.md
ADR-0008). `tools/gen_test_rom.py` generates the test cartridge from scratch at
build time; the generated file lives under `build/` and is never committed.
