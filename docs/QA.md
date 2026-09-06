# Quality status: what is proven, and by what

The rule this project runs on (docs/PLAN.md §0): nothing "works theoretically".
Every line below is either backed by an executed check or explicitly marked as
needing one. "Needs device" means what it says: an assertion nobody has run yet.

## Layer-by-layer

| Layer | Proven by | Where | Status |
|---|---|---|---|
| libretro loader, `retro_run` loop, frame timing | 83-check conformance suite on a generated NROM cartridge | `engine/src/test/native/test_host.cpp` | green locally (gcc, `-O1`) |
| Memory model (peek/poke through SYSTEM_RAM / SAVE_RAM / `SET_MEMORY_MAPS`) | same suite | idem | green |
| Save-state container (`MBSV`), reject-on-mismatch, replay determinism | same suite (pixel hash equality after save→run→load) | idem | green |
| Rewind ring, `run_ahead`, fast-forward, pause semantics | same suite | idem | green |
| Battery RAM (`.srm`) flush + restore with no reset afterwards | same suite (byte at `$6000`, and `$07F3` read once at boot) | idem | green |
| Game Genie / Par / raw-poke decoding, host list == core list | same suite, cross-checked against `FCEUI_DecodeGG` inside the core | idem | green |
| Core option table (labels vs values, deep copy of the core's array) | same suite + ASan/UBSan build | idem | green (ASan clean) |
| Audio gain, mute, clamping | same suite | idem | green |
| Presentation maths: scale modes, overscan crop, rotation, scanline period | `platform/common/render_plan` | 11 checks in the same suite (scale modes, crop, rotation, scanline period, clamps) | `engine/src/test/native/test_host.cpp` | green locally |
| Vendored core integrity | `check-source-list` + `cmp` against upstream | CI + docs/THIRD_PARTY.md | green |
| **CMake for Android** | configure + build of both targets | CI job `android` | **needs CI** |
| EGL/GLES2 presenter | `g++ -fsyntax-only` against real Khronos EGL/GLES2 headers, `-Werror` clean; no GPU run | local check | syntax only — **needs device** |
| AAudio backend | none (no AAudio headers in the sandbox) | — | **needs CI + device** |
| JNI bridge | `g++ -fsyntax-only` against real `jni.h` (caught a most-vexing-parse that would have broken the NDK build) | local check | needs CI to link |
| Kotlin/Compose app, Gradle scripts, version pins | nothing yet — no JDK/Android SDK in the sandbox | CI | **needs CI** |
| `Gamepad` mapping, `INesHeader` parser, `PadLayout` presets | JVM unit tests (`testDebugUnitTest`) | `engine/src/test/java`, `app/src/test/java` | **needs CI** |
| Release build with R8 enabled | — | CI `assembleRelease` | **needs CI** |

## First real device: do these in order

1. **Does it show a picture at all** — colour sanity first: Super Mario Bros. sky
   must be light blue and Mario's overalls blue-with-red-shirt (a red/blue swap means
   the `XRGB8888 → RGBA` swizzle in the host was undone).
2. **Is it 60 fps** — HUD (`☰` → nothing needed, it is always on top-right):
   `present fps` ≈ 60.1, speed ≈ 100 %, `ms/frame` < 16 on a mid-range phone,
   `cpu %` reported by the host, and underruns **0** after a minute of play.
   Non-zero underruns with cpu < 60 % means the audio buffer is too small for the
   device's burst — raise `sampleRate`/capacity rather than "fixing" the loop.
3. **Latency** — hold B, tap A: the jump must start within 2 frames of release. If it
   feels like 3+, enable `run_ahead = 1` in settings and re-measure.
4. **Rotation / resize** — rotate 90°/180°, split-screen, fold/unfold a foldable:
   the surface is rebuilt and drawing resumes without restarting the cartridge
   (`eglSwapBuffers` failure → rebuild path in `gl_surface.cpp`).
5. **Audio focus** — play a notification while the game runs; the stream must
   reconnect (the AAudio error-callback reopen path) and not stall the emulator.
6. **Battery save** — play SMB1 until a pipe gives a 1-up, leave the game
   (back → pause-on-focus-loss → `onCleared`), relaunch: still at the same life count.
   `$6000`-based, i.e. the `.srm` round trip, which is what `flushBattery` also does.
7. **Save states** — slot 1 with a thumbnail, run 300 frames, load slot 1: pixels and
   HUD frame counter must return exactly. Then quit the app and load it from the
   slot: same result (the MBSV header carries the ROM fingerprint, so a mismatch is a
   refusal, not a corruption).
8. **Rewind** — hold a jump, tap the rewind button 20×: each tap is one frame back and
   the picture moves; `rewind available` in the HUD tracks the ring.
9. **Cheats** — add a Game Genie infinite-lives code for SMB1 while playing: the row
   must show the *core's* mirror (`from core: …`), and dying must not decrement the
   lives counter. Disable it: the counter must return to the game's control.
10. **Core options** — set the palette to `grayscale` → the picture is grey; set
    `NTSC Filter` (present only because `HAVE_NTSC_FILTER` is compiled in) → the
    picture changes; restart the cartridge for region.
11. **iNES metadata** — the library row for an SMB1 dump must read
    `mapper 0 · PRG 32K · CHR 8K · horizontal`; a MMC3 board must read `mapper 4`
    and mention `battery` when the header sets it.
12. **RTL + a11y** — switch to Arabic: layout mirrors, all labels translate, and a
    TalkBack pass over the library and settings screens announces buttons (the game
    surface deliberately does not).

## Long-running checks worth automating next

* 60-minute soak with the HUD counting `frames skipped` (must stay ≈ 0 when the
  device is not thermally throttled).
* Save-state size regression: `state size` for FCEUmm is ~41 KB and a change in the
  core or in the container's compression should be visible as a diff, not a surprise.
* A golden-frame hash per open-source test cartridge (the suite already hashes
  frames; pinning a reference hash per core version turns "the core updated" into a
  measurable change instead of a rumour).
