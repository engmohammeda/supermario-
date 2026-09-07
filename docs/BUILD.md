# Building MarioBox

Two halves, two toolchains:

* the **native host + core** build and run on plain Linux with gcc/make — no Android
  SDK, no device, no emulator. That is where the emulator is actually tested.
* the **Android app** (`:app` + `:engine`) needs the Android SDK, the NDK and Gradle.

---

## 1. Host tests (no Android needed)

```sh
make -C engine/src/test/native -j"$(nproc)" run
```

That single command: generates a probe cartridge (`tools/gen_test_rom.py`, an
iNES NROM written by this repo, 6502 assembly included), builds the core as
`libmbcore_fceumm.so` from the vendored `Makefile.common`, builds the host, and runs
the conformance suite (86 checks: bring-up, frame counting against NMI, palette
reaching the screen, save states, rewind, SRAM, Game Genie/Par decoding against the
core's own decoder, core options, audio gain, presentation plan incl. view zoom,
clean shutdown).

Other targets in the same directory:

| Command | What it proves |
|---|---|
| `make -C engine/src/test/native sources` | the file list the core build uses |
| `make -C engine/src/test/native check-source-list` | `fceumm_build_spec.cmake` still matches `Makefile.common` (CI runs this) |
| `make -C engine/src/test/native V=1 …` | show the compiler command lines |

Sanitiser build (this is how the core-option use-after-free was found):

```sh
make -C engine/src/test/native clean
OPT="-O1 -g -fsanitize=address,undefined -fno-omit-frame-pointer" \
  LDFLAGS="-fsanitize=address,undefined -pthread -ldl" \
  make -C engine/src/test/native -j"$(nproc)" run
```

CMake, desktop flavour (the same sources the Android build compiles, useful for
reviewing the CMakeLists without an NDK):

```sh
cmake -S engine/src/main/cpp -B build/cmake-host -DCMAKE_BUILD_TYPE=Release
cmake --build build/cmake-host -j"$(nproc)"      # mariobox_host + mbcore_fceumm
```

## 2. Android app

Requirements (docs/PLAN.md §4 pins these; `gradle/libs.versions.toml` is the
machine-readable copy):

| Component | Version |
|---|---|
| Android Gradle Plugin | 9.3.0 |
| Gradle | 9.5.0 |
| Kotlin / Compose compiler plugin | 2.3.21 |
| Compose BOM | 2026.08.00 |
| compileSdk / targetSdk / minSdk | 37 / 37 / 26 |
| NDK | 28.2.13676358 |
| CMake | 3.31.6 |
| JDK | 17 |

### With Android Studio

Open the repository root. Studio will offer to install the missing SDK platform,
build-tools and NDK; accept, then `Run 'app'`. If it complains about the Gradle
wrapper, run `gradle wrapper --gradle-version 9.5.0` once in a terminal (the wrapper
*jar* is not committed here, only `gradle/wrapper/gradle-wrapper.properties`, which
is why `./gradlew` alone does not work out of the box).

### From the command line

```sh
export ANDROID_HOME=/path/to/android-sdk        # or write sdk.dir into local.properties
gradle :app:assembleDebug                        # or: ./gradlew if you generated the wrapper
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Debug builds compile for `arm64-v8a` only — the core is 649 C files and one ABI keeps
a laptop build under a minute. Release adds `armeabi-v7a` and `x86_64`
(`app`/`engine` build files, `ndk { abiFilters … }`).

Release signing: `app/build.gradle.kts` reads `MARIOBOX_KEYSTORE_PATH`,
`MARIOBOX_KEYSTORE_PASSWORD`, `MARIOBOX_KEY_ALIAS` and `MARIOBOX_KEY_PASSWORD` from
the environment. Local machines can export those, or leave unset to get a debug-key
signed APK for sideload testing; CI provides them from repository secrets
(`MARIOBOX_KEYSTORE_B64` is base64-decoded in the workflow). The release build is
R8-minified + resource-shrunk, with ProGuard keeps for the JNI surface in
`app/proguard-rules.pro`.

### What actually gets built

```
:engine  →  libmariobox_host.so   (host + EGL/GLES2 presenter + AAudio + JNI)
            libmbcore_fceumm.so    (vendored core + bridge, dlopen'd at runtime)
:app     →  MarioBox-debug.apk
```

The app hands the core's absolute path (`applicationInfo.nativeLibraryDir`) to
`mb_create()`, which `dlopen`s it. That indirection is why a second core is another
CMake target instead of a link-time decision, and why a core with an undefined
symbol fails as a readable "core missing" toast rather than a load-time crash of the
whole process.

## 3. CI & releases

`.ci/android-build.yml` drives everything. It lives outside
`.github/workflows/` because the credential that pushes this branch (a GitHub App)
is not permitted to create or update files under `workflow` paths. Activate it once
with a credential that is (repository owner, or an App with `workflows` write):

```sh
mkdir -p .github/workflows
git mv .ci/android-build.yml .github/workflows/android-build.yml
git push
```

The workflow itself:

1. **native** — host tests + `check-source-list` + a CMake configure (N1).
2. **android** — `testDebugUnitTest`, `assembleDebug`, R8-minified
   `assembleRelease` (optionally signed from secrets), informational `lintDebug`,
   then versioned artifact names (`MarioBox-vX.Y.Z-*.apk`) + `SHA256SUMS`.
3. **release** — on a `v*` tag (or `workflow_dispatch`): creates/updates the
   GitHub Release with both APKs and checksums, then sends the release APK to
   Telegram using the `BOT_TOKEN` / `CHAT_ID` secrets (CHAT_ID defaults to
   `5926222376`; without `BOT_TOKEN` the step skips cleanly).

Version: `versionName` comes from the tag (`v0.2.0` → `0.2.0`), `workflow_dispatch`
input, or `git describe`; `versionCode` is `git rev-list --count HEAD`. No AAB:
this is a sideloaded app, so a universal APK is the artifact (PLAN.md §7 override).

## 4. Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `UnsatisfiedLinkError: dlopen failed: couldn't find "libmariobox_host.so"` | The ABI you installed has no native libs: check `abiFilters` against the device, and `applicationInfo.nativeLibraryDir` |
| Toast says the core is missing | `libmbcore_fceumm.so` was not packaged: `:engine`'s CMake target must be built for that ABI (`--info=--debug` or inspect the APK's `lib/` entries) |
| Black screen, audio fine | Surface attach order. `EmulatorViewModel` replays the last known surface after `load()`; if you restructured that, the first session after a cold launch loses the surface |
| Colours look red/blue swapped | The host must swizzle libretro's little-endian `0x00RRGGBB` into R,G,B,A (see `EmuHost::fill_frame_rgba`); both the GL sampler and `Bitmap` consume R,G,B,A |
| `dlopen failed: cannot locate symbol "sincos"` | The core lost `-fno-builtin-sincos -fno-builtin-sincosf` (see docs/THIRD_PARTY.md) |
| `PATH_MAX` undeclared (Android only) | The core needs `-DPATH_MAX=1024` plus the force-included `compat/strl.h`; both come from `fceumm_build_spec.cmake` and the CMakeLists |
| Save states are large/uncompressed | `MB_HAVE_ZLIB` was off at build time (no zlib on the machine). The container is readable either way; install zlib and rebuild to get deflate |
| `SDK location not found` | Write `sdk.dir=/path/to/sdk` into `local.properties` (git-ignored), or export `ANDROID_HOME` |
| `make: check-source-list` fails | The vendored core changed: run `python3 tools/gen_cmake_core_list.py`, review the diff, commit both |
| Gradle configuration cache error | Already disabled in `gradle.properties`; if you re-enable it, the CMake task graph is what trips it |
