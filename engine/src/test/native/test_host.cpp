/* test_host.cpp — MarioBox native engine conformance suite.
 *
 * Built and run on the host (Linux/macOS) with plain gcc/g++ — no Android
 * device, no emulator, no network. It loads the *real* core shared object and
 * the *real* host library through the same C ABI the JNI bridge uses, so
 * everything asserted here is the code that ships: frame timing, save-state
 * codec, rewind ring, cheat decoders, SRAM round-trip, core options.
 *
 * Usage: test_host --core <libmbcore_fceumm.so> --rom <mbprobe.nes> --work <dir>
 */
#include <algorithm>
#include <cmath>
#include <ctime>
#include <cstdio>
#include <cstring>
#include <numeric>
#include <string>
#include <vector>

#include "mb_abi.h"
#include "render_plan.h"
#include "state_store.h"

/* ------------------------------------------------------------ tiny harness */
static int g_checks = 0;
static int g_failed = 0;
static std::string g_section;

static void section(const char *name) {
  g_section = name;
  printf("\n── %s ─────────────────────────────────────────────\n", name);
}

static void check(bool ok, const char *what) {
  g_checks++;
  if (!ok) {
    g_failed++;
    printf("  FAIL  %s\n", what);
  } else {
    printf("  ok    %s\n", what);
  }
}

static void check_eq_u64(uint64_t got, uint64_t want, const char *what) {
  g_checks++;
  if (got != want) {
    g_failed++;
    printf("  FAIL  %s (got %llu, want %llu)\n", what, (unsigned long long)got,
           (unsigned long long)want);
  } else {
    printf("  ok    %s (%llu)\n", what, (unsigned long long)got);
  }
}

/* ------------------------------------------------------------------ helpers */
static uint64_t frames_of(void *h) {
  mb_stats s{};
  s.struct_size = sizeof(s);
  mb_get_stats(h, &s);
  return s.frames_emulated;
}

static uint32_t wait_frames(void *h, uint64_t target, int timeout_ms) {
  for (int i = 0; i < timeout_ms; i += 2) {
    if (frames_of(h) >= target)
      return 1;
    struct timespec ts { 0, 2 * 1000 * 1000 };
    nanosleep(&ts, nullptr);
  }
  return frames_of(h) >= target;
}

/* Runs exactly `n` frames, deterministically, while the loop is paused. */
static int step_frames(void *h, int n) {
  for (int i = 0; i < n; i++) {
    uint64_t target = frames_of(h) + 1;
    mb_status s = mb_advance_frame(h);
    if (s != MB_OK) {
      printf("      advance_frame → %s\n", mb_status_string(s));
      return 0;
    }
    if (!wait_frames(h, target, 5000)) {
      printf("      timed out waiting for frame %llu\n", (unsigned long long)target);
      return 0;
    }
  }
  return 1;
}

static uint32_t frame_hash(void *h) {
  static std::vector<uint8_t> buf;
  uint32_t w = 0, ht = 0, pitch = 0;
  mb_status s = mb_copy_frame(h, nullptr, 0, &w, &ht, &pitch);
  if (s != MB_ERR_INVALID || !w || !ht) {
    if (s != MB_OK)
      return 0;
  }
  buf.resize(size_t(pitch) * ht);
  if (mb_copy_frame(h, buf.data(), buf.size(), &w, &ht, &pitch) != MB_OK)
    return 0;
  /* FNV over a strided sample plus a per-row XOR: a 245 KB full hash every
   * call would dominate the test's runtime for no extra signal. */
  uint32_t hsh = 2166136261u;
  for (size_t i = 0; i < buf.size(); i += 4) {
    hsh = (hsh ^ buf[i]) * 16777619u;
    hsh = (hsh ^ buf[i + 1]) * 16777619u;
    hsh = (hsh ^ buf[i + 2]) * 16777619u;
  }
  uint32_t rows = 0;
  for (size_t y = 0; y < ht; y++) {
    uint32_t acc = 0;
    for (uint32_t x = 0; x < w; x++)
      acc ^= *reinterpret_cast<const uint32_t *>(&buf[y * pitch + size_t(x) * 4]);
    rows = rows * 33u + acc;
  }
  return hsh ^ rows;
}

/* How many entries the CORE's own cheat engine holds. There is no ABI for this
 * on purpose (the host list is the one the UI reads); the suite probes the
 * by-index accessor until it runs out, which is exactly how a stale core list
 * would show up in the decode cross-check below. */
static int core_cheat_count(void *h) {
  int n = 0;
  mb_cheat c{};
  while (n < 64 && mb_cheat_get_core(h, n, &c) == MB_OK)
    n++;
  return n;
}

static uint64_t nonblack_pixels(void *h) {
  uint32_t w = 0, ht = 0, pitch = 0;
  mb_copy_frame(h, nullptr, 0, &w, &ht, &pitch);
  if (!w || !ht)
    return 0;
  std::vector<uint8_t> buf(size_t(pitch) * ht);
  if (mb_copy_frame(h, buf.data(), buf.size(), &w, &ht, &pitch) != MB_OK)
    return 0;
  uint64_t distinct = 0, nonzero = 0;
  uint32_t seen[64] = {0};
  size_t seen_n = 0;
  for (size_t y = 0; y < ht; y += 3) {
    for (size_t x = 0; x < w; x += 3) {
      const uint8_t *p = &buf[y * pitch + x * 4];
      uint32_t px = (p[0] << 16) | (p[1] << 8) | p[2];
      if (px)
        nonzero++;
      bool found = false;
      for (size_t i = 0; i < seen_n; i++)
        if (seen[i] == px) { found = true; break; }
      if (!found && seen_n < 64)
        seen[seen_n++] = px;
    }
  }
  distinct = seen_n;
  return (nonzero << 8) | distinct;
}

/* -------------------------------------------------------------------- main */
int main(int argc, char **argv) {
  std::string core, rom, work = "/tmp/mariobox-test";
  for (int i = 1; i < argc; i++) {
    if (!strcmp(argv[i], "--core") && i + 1 < argc)
      core = argv[++i];
    else if (!strcmp(argv[i], "--rom") && i + 1 < argc)
      rom = argv[++i];
    else if (!strcmp(argv[i], "--work") && i + 1 < argc)
      work = argv[++i];
  }
  if (core.empty() || rom.empty()) {
    fprintf(stderr, "usage: %s --core <core.so> --rom <probe.nes> [--work dir]\n", argv[0]);
    return 2;
  }
  /* Unbuffered, so the last line printed before a crash identifies the failing
   * check instead of vanishing with a half-flushed stdout buffer. */
  setvbuf(stdout, nullptr, _IONBF, 0);
  printf("core=%s\nrom =%s\nwork=%s\n", core.c_str(), rom.c_str(), work.c_str());

  mb_config cfg{};
  cfg.struct_size = sizeof(cfg);
  cfg.core_path = core.c_str();
  cfg.rom_path = rom.c_str();
  cfg.save_dir = work.c_str();
  cfg.system_dir = work.c_str();
  cfg.rewind_frames = 120;
  cfg.rewind_stride = 1;
  cfg.rewind_budget_kb = 64 * 1024;
  cfg.run_ahead = 0;
  cfg.audio_enable = 1; /* the stub device proves the callback path stays live */
  cfg.sample_rate = 44100;
  cfg.video_enable = 0; /* headless: frame buffer is still filled, nothing shown */
  cfg.sram_enable = 1;
  cfg.sram_save_on_state = 0;

  void *h = nullptr;
  mb_status s = mb_create(&cfg, &h);
  section("bring-up");
  check(s == MB_OK && h, "mb_create accepts the probe ROM and the core");
  if (!h)
    return 1;
  check(mb_start(h) == MB_OK, "mb_start loads the game on the emulation thread");
  mb_set_paused(h, 1);
  check(mb_is_paused(h), "pause is honoured before any frame is stepped");

  /* The probe enables vblank during reset, so its counter starts one frame *after*
   * the machine does. Priming first keeps "one NMI per frame" a statement about
   * steady state rather than about boot ordering. */
  check(step_frames(h, 3), "three frames to settle after reset");
  uint64_t f0 = frames_of(h);
  uint32_t c0 = mb_mem_peek(h, 0x07F0);

  section("CPU and PPU run to a deterministic beat");
  check(step_frames(h, 40), "40 stepped frames complete");
  uint64_t f40 = frames_of(h);
  uint32_t c40 = mb_mem_peek(h, 0x07F0);
  check_eq_u64(c40 - c0, f40 - f0, "NMI counter advanced exactly one per frame");
  check(mb_mem_peek(h, 0x07F2) != 0, "main loop executed (CPU is not wedged)");
  check(mb_mem_peek(h, 0x07F1) != 0 || c40 == 0, "main loop mirrored the frame counter");
  check(frame_hash(h) != 0, "framebuffer is filled with a non-trivial image");
  uint64_t pix = nonblack_pixels(h);
  check((pix >> 8) > 100, "the probe picture has real content");
  check((pix & 0xFF) >= 2, "more than one colour is on screen (palette writes landed)");

  section("PPU output is reproducible frame to frame");
  uint32_t ha = frame_hash(h);
  check(step_frames(h, 1), "one more frame");
  uint32_t hb = frame_hash(h);
  check(ha != hb, "the animation changes the visible frame");
  /* The probe's picture only returns to a previous look when its counter wraps,
   * so "reproducible" cannot mean "periodic". It means: the same machine state
   * always renders the same pixels. Snapshot the state, walk six frames, restore,
   * walk six again, and the two pictures must agree byte for byte. */
  std::vector<uint8_t> snap(mb_serialize_size(h));
  check(!snap.empty() && mb_serialize(h, snap.data(), snap.size()) == MB_OK,
        "the core serialized a state buffer");
  std::vector<uint32_t> forward, replay;
  for (int i = 0; i < 6; i++) {
    step_frames(h, 1);
    forward.push_back(frame_hash(h));
  }
  check(mb_unserialize(h, snap.data(), snap.size()) == MB_OK, "the state was restored");
  for (int i = 0; i < 6; i++) {
    step_frames(h, 1);
    replay.push_back(frame_hash(h));
  }
  check(forward == replay && forward.size() == 6,
        "replaying from a save state renders identical pixels");

  section("presentation plan");
  {
    /* Scaling, overscan, rotation and scanline period are pure maths in
     * platform/common/render_plan.cpp -- the same code the EGL renderer calls, and
     * the reason the renderer itself is allowed to be 200 lines of nothing. */
    using namespace mb;
    RenderConfig fit{};
    fit.scale_mode = MB_SCALE_FIT;
    PresentPlan p = make_plan(1080, 2160, 256, 240, 4.0 / 3.0, fit);
    check(p.valid && p.viewport.width == 1080 && p.viewport.height == 810 && p.viewport.y == 675,
          "fit letterboxes a 4:3 frame inside a portrait window");

    RenderConfig integer{};
    integer.scale_mode = MB_SCALE_INTEGER;
    PresentPlan pi = make_plan(1080, 2160, 256, 240, 4.0 / 3.0, integer);
    check(pi.viewport.height % 240 == 0 && pi.scale_is_integer && pi.viewport.height < p.viewport.height,
          "integer scale never exceeds the fit scale and keeps whole source rows");

    RenderConfig stretch{};
    stretch.scale_mode = MB_SCALE_STRETCH;
    PresentPlan ps = make_plan(1080, 2160, 256, 240, 4.0 / 3.0, stretch);
    check(ps.viewport.width == 1080 && ps.viewport.height == 2160 && ps.u0 == 0.f && ps.u1 == 1.f,
          "stretch fills the window and samples the whole frame");

    RenderConfig fill{};
    fill.scale_mode = MB_SCALE_FILL_CROP;
    PresentPlan pf = make_plan(1080, 2160, 256, 240, 4.0 / 3.0, fill);
    check(pf.viewport.width == 1080 && pf.viewport.height == 2160 && pf.u1 - pf.u0 < 1.f &&
              pf.crop_x > 0,
          "fill-and-crop covers the window by trimming the sampled rect, not by squashing");

    RenderConfig over{};
    over.scale_mode = MB_SCALE_FIT;
    over.overscan_crop = 8;
    PresentPlan po = make_plan(1080, 2160, 256, 240, 4.0 / 3.0, over);
    check(std::fabs(po.u0 - 10.0 / 256.0) < 1e-4 && std::fabs(po.v0 - 9.0 / 240.0) < 1e-4 &&
              po.visible_rows == 222.f && po.visible_cols == 236.f,
          "overscan crop is symmetric and measured in source pixels");
    check(po.u1 - po.u0 < 1.f && po.v1 - po.v0 < 1.f, "a cropped frame samples less than all of it");

    RenderConfig zin{};
    zin.scale_mode = MB_SCALE_FIT;
    zin.zoom = 1.5f;
    PresentPlan pz = make_plan(1080, 2160, 256, 240, 4.0 / 3.0, zin);
    check(pz.valid && pz.u1 - pz.u0 < 1.f && pz.viewport.width == 1080 && pz.crop_x > 0 &&
              pz.viewport.height > p.viewport.height,
          "zoom in magnifies by cropping the sampled rect, never by squashing");

    RenderConfig zout{};
    zout.scale_mode = MB_SCALE_FIT;
    zout.zoom = 0.5f;
    PresentPlan pzo = make_plan(1080, 2160, 256, 240, 4.0 / 3.0, zout);
    check(pzo.valid && pzo.viewport.width == 540 && std::fabs(pzo.viewport.height - 405.f) < 1.f &&
              pzo.u0 == 0.f && pzo.u1 == 1.f,
          "zoom out shrinks the picture into a centred letterbox");

    RenderConfig zclamp{};
    zclamp.scale_mode = MB_SCALE_FIT;
    zclamp.zoom = 100.f;
    PresentPlan pzc = make_plan(1080, 2160, 256, 240, 4.0 / 3.0, zclamp);
    check(pzc.valid && pzc.u1 > pzc.u0 && pzc.viewport.width > 0,
          "an out-of-range zoom is clamped to a sane multiplier, not a divide-by-zero");

    RenderConfig rot{};
    rot.scale_mode = MB_SCALE_FIT;
    rot.rotation = 90;
    PresentPlan pr = make_plan(2160, 1080, 256, 240, 4.0 / 3.0, rot);
    check(pr.rotation_quarters == 1 && pr.viewport.width == 810 && pr.viewport.height == 1080 &&
              pr.viewport.x == 675,
          "a 90 degree rotation swaps the axes and stays centred in the real window");

    RenderConfig scan{};
    scan.scale_mode = MB_SCALE_INTEGER;
    scan.scanlines_percent = 35;
    PresentPlan psl = make_plan(1024, 768, 256, 240, 4.0 / 3.0, scan);
    check(std::fabs(psl.scanline_amount - 0.35f) < 1e-4 && std::fabs(psl.row_height_px - 3.f) < 1e-3,
          "one scanline period is exactly one source row of the integer scale");

    PresentPlan pn = make_plan(0, 0, 256, 240, 4.0 / 3.0, fit);
    check(!pn.valid, "a zero-sized window produces no plan instead of a divide by zero");

    /* No reported aspect means square pixels: 256x240 in a 512x384 window should be
     * limited by height, giving 384 * 256/240 wide. */
    PresentPlan psql = make_plan(512, 384, 256, 240, 0.0, fit);
    check(psql.viewport.height == 384 && std::abs(psql.viewport.width - 410) <= 1,
          "aspect_ratio 0 falls back to square pixels rather than 4:3");

    /* crop_pixels owns the clamp: out-of-range values are pulled back to 25% of each
     * axis, so a stale or hand-edited preference cannot blank the picture. */
    RenderConfig insane{};
    insane.scale_mode = MB_SCALE_FIT;
    insane.overscan_crop = 200;
    PresentPlan pin = make_plan(1080, 2160, 256, 240, 4.0 / 3.0, insane);
    int32_t cx = 0, cy = 0;
    crop_pixels(256, 240, 200, &cx, &cy);
    check(cx == 32 && cy == 30, "overscan is clamped to 25% of each axis");
    check(pin.visible_cols == 192 && pin.visible_rows == 180 && pin.u1 > pin.u0 && pin.v1 > pin.v0,
          "the plan stays a non-degenerate sampled rectangle at the clamp");
  }

  section("audio volume");
  {
    /* Gain is stored on the host, not the device, so a routing change that reopens
     * the stream must not silently reset the user's volume. */
    mb_set_audio_volume(h, 0.5f);
    float got = mb_get_audio_volume(h);
    check(got > 0.499f && got < 0.501f, "audio gain round-trips through the host");
    mb_set_audio_volume(h, 4.f);
    check(mb_get_audio_volume(h) <= 1.0f, "gain is clamped to unity");
    mb_set_audio_volume(h, -2.f);
    check(mb_get_audio_volume(h) >= 0.f, "negative gain clamps to silence");
    mb_set_audio_volume(h, 1.f);
  }

  section("core options");
  int nopt = int(mb_option_count(h));
  check(nopt > 0, "the core published its option table");
  char val[64] = {0};
  /* Read back a key the core actually published. Hardcoding one (for example
   * "fceumm_ntsc_filter", which only exists in builds with HAVE_NTSC, not our
   * HAVE_NTSC_FILTER blitter build) tests the toolchain configuration rather
   * than the host. */
  {
    mb_option o0{};
    o0.struct_size = sizeof(o0);
    bool first = nopt > 0 && mb_option_info(h, 0, &o0) == MB_OK && o0.key[0];
    check(first, "option 0 has a key and a description");
    if (first) {
      mb_status gs = mb_option_get(h, o0.key, val, sizeof(val));
      check(gs == MB_OK && val[0],
            "an untouched option still reads back the core's documented default");
      printf("      %s = '%s'\n", o0.key, val);
      /* The settings screen shows labels[] and sends values[]. If those two were
       * ever conflated -- which is what the single-array ABI invited -- picking an
       * entry in the UI would look applied and do nothing, so the round trip has
       * to be asserted with the string the UI would actually send. */
      if (o0.count > 1 && o0.values[0][0] && strcmp(val, o0.values[1]) != 0) {
        check(mb_option_set(h, o0.key, o0.values[1]) == MB_OK,
              "the label/value split leaves a settable value string");
        char back[64] = {0};
        check(mb_option_get(h, o0.key, back, sizeof(back)) == MB_OK && !strcmp(back, o0.values[1]),
              "a value taken from option_info().values round-trips through set");
        printf("      %s: values[1]='%s' labels[1]='%s'\n", o0.key, o0.values[1], o0.labels[1]);
        mb_option_set(h, o0.key, val); /* restore */
      } else {
        check(false, "option 0 exposes more than one value for the round-trip test");
      }
    }
  }
  check(mb_option_set(h, "fceumm_palette", "grayscale") == MB_OK, "an option can be set");
  check(mb_option_get(h, "fceumm_palette", val, sizeof(val)) == MB_OK &&
            !strcmp(val, "grayscale"),
        "the option reads back what was set (GET_VARIABLE path)");

  section("save states round-trip bit-exactly");
  std::string st = work + "/slot01.mbs";
  uint32_t c_saved = mb_mem_peek(h, 0x07F0);
  uint32_t hash_saved = frame_hash(h);
  check(mb_save_state(h, st.c_str()) == MB_OK, "mb_save_state writes a container");
  check(step_frames(h, 25), "25 frames of drift");
  check(mb_mem_peek(h, 0x07F0) != c_saved, "the counter really moved away from the save point");
  check(mb_load_state(h, st.c_str()) == MB_OK, "mb_load_state restores it");
  check_eq_u64(mb_mem_peek(h, 0x07F0), c_saved, "RAM is back exactly at the saved value");
  step_frames(h, 1);
  uint32_t hash_replay = frame_hash(h);
  check(mb_load_state(h, st.c_str()) == MB_OK, "second load of the same state");
  step_frames(h, 1);
  check_eq_u64(frame_hash(h), hash_replay, "same state + same input → same frame (bit-exact)");
  (void)hash_saved;

  section("the MBSV container carries what the library UI needs");
  {
    mb::SaveStateHeader hdr;
    std::string err;
    bool ok = mb::state_read_header(st, &hdr, &err);
    check(ok, "header parses without inflating the state");
    check(hdr.frame > 0, "frame number recorded");
    check(!hdr.core_name.empty(), "core name recorded");
    check(hdr.rom_fingerprint != 0, "ROM fingerprint recorded (guards cross-game loads)");
    check(hdr.thumb_w && hdr.thumb_h && hdr.thumb_rgba.size() == size_t(hdr.thumb_w) * hdr.thumb_h * 4,
          "thumbnail is present and the right size");
    mb_cheat probe{};
    snprintf(probe.code, sizeof(probe.code), "07f0=00");
    int32_t id = mb_cheat_add(h, &probe);
    mb_cheat back{};
    check(mb_cheat_get(h, 0, &back) == MB_OK && back.address == 0x07F0 && back.value == 0,
          "cheat list round-trips through the ABI");
    mb_cheat_remove(h, id);
  }

  section("rewind walks back frame by frame");
  {
    std::vector<uint32_t> counters;
    for (int i = 0; i < 20; i++) {
      step_frames(h, 1);
      counters.push_back(mb_mem_peek(h, 0x07F0));
    }
    check(mb_rewind_available(h) >= 20, "the rewind ring holds at least 20 frames");
    check(mb_rewind_step(h, 5) == MB_OK, "rewind 5 frames");
    check_eq_u64(mb_mem_peek(h, 0x07F0), counters[counters.size() - 6],
                 "counter matches the state from 5 frames back");
    check(step_frames(h, 5), "replaying forward after a rewind works");
    mb_rewind_clear(h);
    check_eq_u64(mb_rewind_available(h), 0, "rewind ring clears on demand");
  }

  section("cheats: host decoder agrees with the core decoder");
  {
    /* The core's own list is only rebuilt on the emulation thread when the list
     * is dirty, so each code is added, given one frame to land, and then read
     * back from both sides. Anything else compares the host against a stale
     * core and reports agreement that has not happened yet. */
    /* Every character has to be a real Game Genie letter: the 24-letter NES
     * alphabet is A,P,Z,L,G,I,T,Y,E,O,X,U,K,S,V,N plus 0-9, and this is exactly
     * the list a host decoder gets wrong if it assumes A-P. */
    const char *gg[] = {"AAKPPG", "KKKPPK", "AXKPAO", "PEOAGA", "SUOZUA", "AAXIPO", "OGLZPO",
                        "AAOAAX"};
    int matched = 0, tested = 0;
    for (const char *code : gg) {
      mb_cheat c{};
      snprintf(c.code, sizeof(c.code), "%s", code);
      snprintf(c.desc, sizeof(c.desc), "probe %s", code);
      c.kind = 1; /* Game Genie */
      c.enabled = 1;
      int32_t id = mb_cheat_add(h, &c);
      tested++;
      if (id < 0) {
        printf("      %s: the host refused to decode it (%s)\n", code, mb_last_error(h));
        continue;
      }
      step_frames(h, 1);
      mb_cheat host_side{}, core_side{};
      int got_host = mb_cheat_get(h, 0, &host_side) == MB_OK;
      int got_core = mb_cheat_get_core(h, 0, &core_side) == MB_OK;
      if (!got_host || !got_core) {
        printf("      %s: host=%d core=%d (one side had no entry to read)\n", code, got_host,
               got_core);
        mb_cheat_remove(h, id);
        continue;
      }
      if (host_side.address == core_side.address && host_side.value == core_side.value &&
          host_side.compare == core_side.compare)
        matched++;
      else
        printf("      %s: host addr=%04X val=%02X cmp=%d | core addr=%04X val=%02X cmp=%d\n", code,
               host_side.address, host_side.value, host_side.compare, core_side.address,
               core_side.value, core_side.compare);
      mb_cheat_remove(h, id);
      step_frames(h, 1);
    }
    check_eq_u64(uint64_t(matched), uint64_t(tested),
                 "every Game Genie code decodes identically in host and core");

    /* An 8-character code is ambiguous by design: "074700FF" is a legal Par
     * code *and* a legal 8-char Game Genie code, and no decoder can tell them
     * apart from the text alone. So the kind is carried by the entry, and the
     * Par form is checked through the add path rather than the bare decoder. */
    mb_cheat par{};
    snprintf(par.code, sizeof(par.code), "074700FF");
    par.kind = 2; /* Par: PP AAhi AAlo VV */
    par.enabled = 0;
    int32_t par_id = mb_cheat_add(h, &par);
    mb_cheat par_back{};
    check(par_id > 0 && mb_cheat_get(h, 0, &par_back) == MB_OK && par_back.address == 0x4700 &&
              par_back.value == 0xFF,
          "Par code splits into the documented address/value through the add path");
    mb_cheat_remove(h, par_id);
    uint32_t addr = 0, val = 0;
    int32_t cmp = 0, kind = 0;
    mb_status ds = mb_cheat_decode("$07F0=12", &addr, &val, &cmp, &kind);
    check(ds == MB_OK && addr == 0x07F0 && val == 0x12 && cmp == -1,
          "raw poke form parses with no compare");
    /* 'Z' is a legitimate Game Genie letter (it decodes to 2), so it proves
     * nothing here; 'B' is not in the alphabet at all. */
    ds = mb_cheat_decode("BBBBBB", &addr, &val, &cmp, &kind);
    check(ds != MB_OK, "nonsense is rejected rather than guessed at");
    ds = mb_cheat_decode("AZLPTIYEX", &addr, &val, &cmp, &kind);
    check(ds != MB_OK, "a nine-character code is rejected on length");
  }

  section("cheats: a live write reaches the running game");
  {
    /* $07FA is polled by the probe into $07FB and never written by the game, so
     * "the core applied the cheat" is observable without racing the frame
     * boundary — the failure mode that makes freezing a counter a flaky test
     * (the game writes the counter after the cheat does, every single frame). */
    step_frames(h, 2);
    mb_mem_poke(h, 0x07FA, 0x11);
    step_frames(h, 1);
    check_eq_u64(mb_mem_peek(h, 0x07FB), 0x11, "the game is polling the byte we poked");

    mb_cheat c{};
    snprintf(c.code, sizeof(c.code), "$07FA=5A");
    snprintf(c.desc, sizeof(c.desc), "force polled byte");
    c.kind = 4;
    c.enabled = 1;
    int32_t id = mb_cheat_add(h, &c);
    check(id > 0, "raw poke cheat accepted");
    step_frames(h, 3);
    check_eq_u64(mb_mem_peek(h, 0x07FB), 0x5A, "the core's cheat engine drives the polled byte");
    /* The disable has to reach the core *before* the poke: the host only
     * rebuilds the core's list at the top of a frame, and while the cheat is
     * still live in the core it rewrites $07FA on that frame, so the game would
     * copy the cheat's value and the byte we planted would be lost. */
    mb_cheat_set_enabled(h, id, 0);
    step_frames(h, 1);
    mb_mem_poke(h, 0x07FA, 0x22);
    step_frames(h, 2);
    if (mb_mem_peek(h, 0x07FB) != 0x22)
      printf("      after disable: target=%02X polled=%02X core_cheats=%d host_cheats=%d "
             "frames=%llu main=%u nmi=%u\n",
             mb_mem_peek(h, 0x07FA), mb_mem_peek(h, 0x07FB), core_cheat_count(h), mb_cheat_count(h),
             (unsigned long long)frames_of(h), unsigned(mb_mem_peek(h, 0x07F2)),
             unsigned(mb_mem_peek(h, 0x07F0)));
      /* A type-1 cheat patches what the CPU *reads*, not the byte in RAM, so
       * "target" is expected to stay at whatever the game or the test left
       * there; the observable is the polled copy. */
    check_eq_u64(mb_mem_peek(h, 0x07FB), 0x22, "disabling the cheat hands the byte back to the game");
    mb_cheat_remove(h, id);
    step_frames(h, 2);
    check_eq_u64(mb_cheat_count(h), 0, "the cheat list is empty again");
    check(mb_mem_peek(h, 0x07F0) > 1, "the game is still running with no cheats");
  }

  section("battery RAM survives a power cycle");
  {
    step_frames(h, 3);
    uint32_t want = mb_mem_peek(h, 0x07F0);
    check(mb_sram_flush(h) == MB_OK, "SRAM flush reports success");
    std::vector<uint8_t> disk;
    bool read_ok = mb::sram_load(mb::sram_path_for(work, rom), &disk);
    check(read_ok && disk.size() == 8192, "an 8 KiB .srm was written beside the save dir");
    check(read_ok && !disk.empty() && disk[0] == want,
          "the mirrored frame counter is the byte the game wrote");
    check(mb_power_cycle(h) == MB_OK, "power cycle reloads the ROM");
    check_eq_u64(mb_mem_peek(h, 0x6000), want,
                 "the restored file is in the core's battery RAM before the first frame");
    check(step_frames(h, 4), "four frames after the power cycle");
    check_eq_u64(mb_mem_peek(h, 0x07F3), want,
                 "the program's own reset code read the restored save out of the cart");
    check(mb_mem_peek(h, 0x07F0) <= 2, "the machine restarted its frame counter");
    check_eq_u64(mb_mem_peek(h, 0x6000), mb_mem_peek(h, 0x07F0),
                 "the game's battery-RAM mirror is live after the reload");
  }

  section("audio and stats plumbing stay honest");
  {
    mb_stats st2{};
    st2.struct_size = sizeof(st2);
    check(mb_get_stats(h, &st2) == MB_OK, "stats are readable");
    printf("      frames=%llu state=%uB sram=%uB underruns=%u speed=%.2f\n",
           (unsigned long long)st2.frames_emulated, st2.state_size, st2.sram_size,
           st2.audio_underruns, st2.emu_speed);
    check(st2.state_size > 0, "the core reported a save-state size (rewind depends on it)");
    check(st2.audio_enabled == 1, "the audio backend is attached");
    check(st2.video_width >= 256 && st2.video_height >= 224,
          "geometry is a real NES frame (FCEUmm's 224-line visible base)");
  }

  section("fast forward runs without stalling the queue");
  {
    mb_set_paused(h, 0);
    mb_set_fast_forward(h, 1);
    uint64_t before = frames_of(h);
    int ok = wait_frames(h, before + 200, 8000);
    mb_set_fast_forward(h, 0);
    mb_set_paused(h, 1);
    check(ok, "200 frames ran under fast forward while live");
  }

  section("teardown");
  check(mb_save_state(h, (work + "/final.mbs").c_str()) == MB_OK, "a state can be written at exit");
  mb_stop(h);
  check(!mb_is_running(h), "the emulation thread exits cleanly (SRAM flushed on the way out)");
  mb_destroy(h);

  printf("\n%s  %d checks, %d failures\n", g_failed ? "FAILED" : "PASSED", g_checks, g_failed);
  return g_failed ? 1 : 0;
}
