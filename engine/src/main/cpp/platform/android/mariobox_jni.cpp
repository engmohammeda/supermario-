/* mariobox_jni.cpp — the whole Android/JNI surface of the engine.
 *
 * One Kotlin class (dev.mariobox.engine.MbNative) declares one external function
 * per mb_* entry point, and this file is the marshalling for it. Rules this file
 * follows on purpose:
 *
 *   • A handle is a jlong carrying a `void *`. 0 means "not created". Every entry
 *     point checks for null before dereferencing, because Kotlin may legitimately
 *     call into a handle it failed to create and a SIGSEGV in an emulator is not
 *     an acceptable failure mode.
 *   • No mb_* call is ever made with a JNI critical/locked array: scratch buffers
 *     are copied. The emulator thread can block in an audio write for milliseconds
 *     and GC must never be made to wait for us.
 *   • Strings returned to Kotlin are UTF-8 and newly allocated; strings coming in
 *     are released on every path via the scoped holders below.
 *   • Statistics come back as primitive arrays with a fixed index layout that
 *     MbNative.kt mirrors. Marshalling `mb_stats` field-by-field through JNI would
 *     be 30 FindFieldID lookups per HUD refresh for no benefit.
 *   • Core events (geometry change, toast, error) are pushed to a listener object;
 *     the emulation thread is attached to the JVM for the call and detached after,
 *     so the callback works whichever thread the host decided to fire it from.
 */
#include <jni.h>

#include <android/native_window_jni.h>

#include <cstring>
#include <mutex>
#include <string>
#include <utility>
#include <vector>

#include "mb_abi.h"

#include "mb_common.h"

#define MB_JNI(cls, name) Java_dev_mariobox_engine_##cls##_##name

static JavaVM *g_vm = nullptr;

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *) {
  g_vm = vm;
  return JNI_VERSION_1_6;
}

JNIEXPORT void JNI_OnUnload(JavaVM *, void *) { g_vm = nullptr; }

} /* extern "C" */

namespace {

/* ---- scoped helpers ---------------------------------------------------- */
struct JChars {
  JChars(JNIEnv *env, jstring s) : env_(env), s_(s) {
    if (env_ && s_)
      c_ = env_->GetStringUTFChars(s_, nullptr);
  }
  ~JChars() {
    if (env_ && s_ && c_)
      env_->ReleaseStringUTFChars(s_, c_);
  }
  const char *get() const { return c_ ? c_ : ""; }
  bool is_null() const { return s_ == nullptr; }

  JNIEnv *env_;
  jstring s_;
  const char *c_ = nullptr;
};

jstring jstr(JNIEnv *env, const char *s) {
  return env->NewStringUTF(s ? s : "");
}

jobjectArray str_array(JNIEnv *env, const std::vector<std::string> &items) {
  jclass sc = env->FindClass("java/lang/String");
  jobjectArray out = env->NewObjectArray(jsize(items.size()), sc, nullptr);
  if (!out)
    return nullptr;
  for (size_t i = 0; i < items.size(); i++) {
    jstring s = jstr(env, items[i].c_str());
    env->SetObjectArrayElement(out, jsize(i), s);
    env->DeleteLocalRef(s);
  }
  env->DeleteLocalRef(sc);
  return out;
}

void *handle(jlong h) { return reinterpret_cast<void *>(h); }

/* The join used by every status-returning entry point. */
jint status(JNIEnv *env, void *h, mb_status rc) {
  if (rc != MB_OK) {
    const char *err = h ? mb_last_error(h) : nullptr;
    if (err && *err)
      MB_LOGW("jni call failed (%s): %s", mb_status_string(rc), err);
  }
  (void)env;
  return jint(rc);
}

/* ---- event callback into Kotlin ---------------------------------------- */
struct Bridge {
  jobject listener = nullptr; /* global ref */
  jmethodID on_event = nullptr;
};

/* The Kotlin listener is a global reference, so it has to outlive nativeCreate and
 * die with nativeDestroy. mb_abi.h has no "user pointer" getter, so the bridge is
 * keyed by handle here rather than stored in the host. */
std::mutex g_bridge_mu;
std::vector<std::pair<void *, Bridge *>> g_bridges;

Bridge *bridge_take(void *h) {
  std::lock_guard<std::mutex> lk(g_bridge_mu);
  for (size_t i = 0; i < g_bridges.size(); i++) {
    if (g_bridges[i].first == h) {
      Bridge *b = g_bridges[i].second;
      g_bridges.erase(g_bridges.begin() + ptrdiff_t(i));
      return b;
    }
  }
  return nullptr;
}

void push_event(void *user, uint32_t event_id, const char *text);

void push_event(void *user, uint32_t event_id, const char *text) {
  Bridge *b = static_cast<Bridge *>(user);
  if (!g_vm || !b || !b->listener || !b->on_event)
    return;
  JNIEnv *env = nullptr;
  bool attached_here = false;
  jint rc = g_vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
  if (rc == JNI_EDETACHED) {
    /* Android's jni.h declares AttachCurrentThread(JNIEnv**, void*); a host-side
     * syntax check against OpenJDK's header sees (void**, void*) and complains here
     * and nowhere else -- that error is a false positive, do not "fix" the cast. */
    JavaVMAttachArgs args;
    args.version = JNI_VERSION_1_6;
    args.name = const_cast<char *>("mariobox-emu");
    args.group = nullptr;
    if (g_vm->AttachCurrentThread(&env, &args) != 0)
      return;
    attached_here = true;
  } else if (rc != JNI_OK || !env) {
    return;
  }
  jstring js = env->NewStringUTF(text ? text : "");
  env->CallVoidMethod(b->listener, b->on_event, jint(event_id), js);
  if (env->ExceptionCheck())
    env->ExceptionClear();
  env->DeleteLocalRef(js);
  if (attached_here)
    g_vm->DetachCurrentThread();
}

/* ---- fixed layouts shared with Kotlin ---------------------------------- */
enum LongStat {
  kStatFrames = 0,
  kStatPresented,
  kStatSkipped,
  kStatInputFrames,
  kStatVideoW,
  kStatVideoH,
  kStatPitch,
  kStatBaseW,
  kStatBaseH,
  kStatRate,
  kStatAudioWritten,
  kStatUnderruns,
  kStatRewindAvail,
  kStatRewindCap,
  kStatRewindBytes,
  kStatStateSize,
  kStatSramSize,
  kStatPaused,
  kStatRunning,
  kStatAudioOn,
  kStatPal,
  kStatMsPerFrame,
  kStatCpu,
  kStatCount
};
enum DoubleStat { kFps = 0, kSpeed, kAspect, kDoubleCount };
enum CheatField {
  kCheatCode = 0,
  kCheatDesc,
  kCheatEnabled,
  kCheatKind,
  kCheatAddr,
  kCheatValue,
  kCheatCompare,
  kCheatCount
};
enum OptionField {
  kOptKey = 0,
  kOptDesc,
  kOptCategory,
  kOptValues,   /* '|'-joined strings to send to mb_option_set */
  kOptLabels,   /* '|'-joined strings to show the user */
  kOptCurrent,  /* what the core reports right now */
  kOptDefault,  /* index into the two lists above */
  kOptCount
};

void fill_cheat(std::vector<std::string> *out, const mb_cheat &c) {
  char num[32];
  out->resize(kCheatCount);
  (*out)[kCheatCode] = c.code;
  (*out)[kCheatDesc] = c.desc;
  snprintf(num, sizeof(num), "%d", c.enabled);
  (*out)[kCheatEnabled] = num;
  snprintf(num, sizeof(num), "%d", c.kind);
  (*out)[kCheatKind] = num;
  snprintf(num, sizeof(num), "%u", c.address);
  (*out)[kCheatAddr] = num;
  snprintf(num, sizeof(num), "%u", c.value);
  (*out)[kCheatValue] = num;
  snprintf(num, sizeof(num), "%d", c.compare);
  (*out)[kCheatCompare] = num;
}

} /* namespace */

/* ------------------------------------------------------------------ lifecycle */
extern "C" JNIEXPORT jlong JNICALL MB_JNI(MbNative, nativeCreate)(
    JNIEnv *env, jclass, jstring core_path, jstring rom_path, jstring save_dir,
    jstring system_dir, jint rewind_frames, jint rewind_stride, jint rewind_budget_kb,
    jint run_ahead, jboolean audio_enable, jint sample_rate, jboolean video_enable,
    jboolean sram_enable, jboolean sram_save_on_state, jobject listener) {
  mb_config cfg{};
  cfg.struct_size = sizeof(cfg);
  JChars core(env, core_path), rom(env, rom_path), sdir(env, save_dir), sysdir(env, system_dir);
  cfg.core_path = core.get();
  cfg.rom_path = rom.is_null() ? nullptr : rom.get();
  cfg.save_dir = sdir.is_null() ? nullptr : sdir.get();
  cfg.system_dir = sysdir.is_null() ? nullptr : sysdir.get();
  cfg.rewind_frames = uint32_t(rewind_frames < 0 ? 0 : rewind_frames);
  cfg.rewind_stride = uint32_t(rewind_stride < 1 ? 1 : rewind_stride);
  cfg.rewind_budget_kb = uint32_t(rewind_budget_kb < 0 ? 0 : rewind_budget_kb);
  cfg.run_ahead = run_ahead;
  cfg.audio_enable = audio_enable ? 1 : 0;
  cfg.sample_rate = uint32_t(sample_rate > 0 ? sample_rate : 44100);
  cfg.video_enable = video_enable ? 1 : 0;
  cfg.sram_enable = sram_enable ? 1 : 0;
  cfg.sram_save_on_state = sram_save_on_state ? 1 : 0;

  void *h = nullptr;
  mb_status rc = mb_create(&cfg, &h);
  if (rc != MB_OK || !h) {
    if (h) {
      MB_LOGE("mb_create failed: %s", mb_last_error(h));
      mb_destroy(h);
    } else {
      MB_LOGE("mb_create failed with status %s", mb_status_string(rc));
    }
    return 0;
  }

  Bridge *b = new Bridge();
  if (listener) {
    b->listener = env->NewGlobalRef(listener);
    jclass cl = env->GetObjectClass(listener);
    b->on_event = env->GetMethodID(cl, "onEngineEvent", "(ILjava/lang/String;)V");
    if (env->ExceptionCheck()) {
      env->ExceptionClear();
      b->on_event = nullptr;
    }
    env->DeleteLocalRef(cl);
  }
  mb_set_event_callback(h, &push_event, b);
  /* The bridge is tied to the handle's lifetime: registered here, unregistered and
   * freed in nativeDestroy after the emulation thread has been joined, so no
   * callback can ever fire at a dangling GlobalRef. */
  {
    std::lock_guard<std::mutex> lk(g_bridge_mu);
    g_bridges.emplace_back(h, b);
  }
  return jlong(uintptr_t(h));
}

extern "C" JNIEXPORT void JNICALL MB_JNI(MbNative, nativeDestroy)(JNIEnv *env, jclass,
                                                                   jlong hp) {
  void *h = handle(hp);
  if (!h)
    return;
  mb_set_event_callback(h, nullptr, nullptr);
  Bridge *b = bridge_take(h);
  mb_destroy(h);
  if (b) {
    if (b->listener)
      env->DeleteGlobalRef(b->listener);
    delete b;
  }
}

extern "C" JNIEXPORT jint JNICALL MB_JNI(MbNative, nativeStart)(JNIEnv *env, jclass, jlong hp) {
  return status(env, handle(hp), mb_start(handle(hp)));
}
extern "C" JNIEXPORT jint JNICALL MB_JNI(MbNative, nativeStop)(JNIEnv *env, jclass, jlong hp) {
  return status(env, handle(hp), mb_stop(handle(hp)));
}
extern "C" JNIEXPORT jboolean JNICALL
MB_JNI(MbNative, nativeIsRunning)(JNIEnv *, jclass, jlong hp) {
  return handle(hp) && mb_is_running(handle(hp)) ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT void JNICALL
MB_JNI(MbNative, nativeSetPaused)(JNIEnv *, jclass, jlong hp, jboolean paused) {
  if (handle(hp))
    mb_set_paused(handle(hp), paused ? 1 : 0);
}
extern "C" JNIEXPORT jboolean JNICALL
MB_JNI(MbNative, nativeIsPaused)(JNIEnv *, jclass, jlong hp) {
  return handle(hp) && mb_is_paused(handle(hp)) ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT jstring JNICALL
MB_JNI(MbNative, nativeLastError)(JNIEnv *env, jclass, jlong hp) {
  return jstr(env, handle(hp) ? mb_last_error(handle(hp)) : "no engine");
}
extern "C" JNIEXPORT jstring JNICALL
MB_JNI(MbNative, nativeStatusString)(JNIEnv *env, jclass, jint rc) {
  return jstr(env, mb_status_string(mb_status(rc)));
}
extern "C" JNIEXPORT jint JNICALL MB_JNI(MbNative, nativeAbiVersion)(JNIEnv *, jclass) {
  return jint(MB_ABI_VERSION);
}

/* ------------------------------------------------------------------ surface */
extern "C" JNIEXPORT jint JNICALL
MB_JNI(MbNative, nativeSetSurface)(JNIEnv *env, jclass, jlong hp, jobject surface) {
  void *h = handle(hp);
  if (!h)
    return MB_ERR_INVALID;
  ANativeWindow *win = surface ? ANativeWindow_fromSurface(env, surface) : nullptr;
  return status(env, h, mb_set_surface(h, win));
}
extern "C" JNIEXPORT jint JNICALL
MB_JNI(MbNative, nativeSetSurfaceSize)(JNIEnv *env, jclass, jlong hp, jint w, jint hgt) {
  return status(env, handle(hp), mb_set_surface_size(handle(hp), w, hgt));
}
extern "C" JNIEXPORT void JNICALL
MB_JNI(MbNative, nativeSetVideoEnabled)(JNIEnv *, jclass, jlong hp, jboolean on) {
  if (handle(hp))
    mb_set_video_enabled(handle(hp), on ? 1 : 0);
}
extern "C" JNIEXPORT void JNICALL MB_JNI(MbNative, nativeSetRenderConfig)(
    JNIEnv *, jclass, jlong hp, jint scale, jint filter, jint scanlines, jint overscan,
    jint rotation, jfloat zoom) {
  if (handle(hp))
    mb_set_render_config(handle(hp), scale, filter, scanlines, overscan, rotation, zoom);
}

/* Fills `meta` with {width, height, pitch} and returns the RGBA bytes. */
extern "C" JNIEXPORT jbyteArray MB_JNI(MbNative, nativeCopyFrame)(JNIEnv *env, jclass, jlong hp,
                                                                   jintArray meta) {
  void *h = handle(hp);
  if (!h)
    return nullptr;
  /* There is no "measure then fetch" call in the ABI: copy_frame() rejects a short
   * buffer, so the geometry comes from stats and the copy is attempted once. */
  mb_stats st{};
  st.struct_size = sizeof(st);
  if (mb_get_stats(h, &st) != MB_OK)
    return nullptr;
  uint32_t w = st.video_width, hh = st.video_height, pitch = st.video_pitch;
  if (!w || !hh || !pitch)
    return nullptr;
  std::vector<uint8_t> buf(size_t(pitch) * hh);
  if (mb_copy_frame(h, buf.data(), buf.size(), &w, &hh, &pitch) != MB_OK)
    return nullptr;
  if (meta && env->GetArrayLength(meta) >= 3) {
    jint m[3] = {jint(w), jint(hh), jint(pitch)};
    env->SetIntArrayRegion(meta, 0, 3, m);
  }
  jbyteArray out = env->NewByteArray(jsize(buf.size()));
  if (out)
    env->SetByteArrayRegion(out, 0, jsize(buf.size()),
                            reinterpret_cast<const jbyte *>(buf.data()));
  return out;
}

/* ------------------------------------------------------------------ audio */
extern "C" JNIEXPORT void JNICALL
MB_JNI(MbNative, nativeSetAudioVolume)(JNIEnv *, jclass, jlong hp, jfloat volume) {
  if (handle(hp))
    mb_set_audio_volume(handle(hp), float(volume));
}
extern "C" JNIEXPORT jfloat JNICALL
MB_JNI(MbNative, nativeGetAudioVolume)(JNIEnv *, jclass, jlong hp) {
  return handle(hp) ? jfloat(mb_get_audio_volume(handle(hp))) : 1.f;
}

/* ------------------------------------------------------------------ input */
extern "C" JNIEXPORT void JNICALL
MB_JNI(MbNative, nativeSetInput)(JNIEnv *, jclass, jlong hp, jint port, jint mask) {
  if (handle(hp))
    mb_set_input(handle(hp), uint32_t(port), uint32_t(mask));
}
extern "C" JNIEXPORT jint JNICALL
MB_JNI(MbNative, nativeGetInput)(JNIEnv *, jclass, jlong hp, jint port) {
  return handle(hp) ? jint(mb_get_input(handle(hp), uint32_t(port))) : 0;
}
extern "C" JNIEXPORT void JNICALL MB_JNI(MbNative, nativeSetTurbo)(JNIEnv *, jclass, jlong hp,
                                                                   jint port, jint mask,
                                                                   jint hz) {
  if (handle(hp))
    mb_set_turbo(handle(hp), uint32_t(port), uint32_t(mask), int(hz));
}
extern "C" JNIEXPORT void JNICALL
MB_JNI(MbNative, nativeSetFastForward)(JNIEnv *, jclass, jlong hp, jboolean on) {
  if (handle(hp))
    mb_set_fast_forward(handle(hp), on ? 1 : 0);
}
extern "C" JNIEXPORT void JNICALL
MB_JNI(MbNative, nativeSetSlowMotion)(JNIEnv *, jclass, jlong hp, jboolean on) {
  if (handle(hp))
    mb_set_slow_motion(handle(hp), on ? 1 : 0);
}
extern "C" JNIEXPORT jint JNICALL
MB_JNI(MbNative, nativeAdvanceFrame)(JNIEnv *env, jclass, jlong hp) {
  return status(env, handle(hp), mb_advance_frame(handle(hp)));
}

/* ------------------------------------------------------------------ states */
extern "C" JNIEXPORT jint JNICALL
MB_JNI(MbNative, nativeSaveState)(JNIEnv *env, jclass, jlong hp, jstring path) {
  JChars p(env, path);
  return status(env, handle(hp), mb_save_state(handle(hp), p.get()));
}
extern "C" JNIEXPORT jint JNICALL
MB_JNI(MbNative, nativeLoadState)(JNIEnv *env, jclass, jlong hp, jstring path) {
  JChars p(env, path);
  return status(env, handle(hp), mb_load_state(handle(hp), p.get()));
}
extern "C" JNIEXPORT jint JNICALL
MB_JNI(MbNative, nativeRewindStep)(JNIEnv *env, jclass, jlong hp, jint frames) {
  return status(env, handle(hp), mb_rewind_step(handle(hp), uint32_t(frames)));
}
extern "C" JNIEXPORT jint JNICALL
MB_JNI(MbNative, nativeRewindAvailable)(JNIEnv *, jclass, jlong hp) {
  return handle(hp) ? jint(mb_rewind_available(handle(hp))) : 0;
}
extern "C" JNIEXPORT void JNICALL
MB_JNI(MbNative, nativeRewindClear)(JNIEnv *, jclass, jlong hp) {
  if (handle(hp))
    mb_rewind_clear(handle(hp));
}
extern "C" JNIEXPORT jint JNICALL
MB_JNI(MbNative, nativeSramFlush)(JNIEnv *env, jclass, jlong hp) {
  return status(env, handle(hp), mb_sram_flush(handle(hp)));
}
extern "C" JNIEXPORT jint JNICALL
MB_JNI(MbNative, nativeSramInvalidate)(JNIEnv *env, jclass, jlong hp) {
  return status(env, handle(hp), mb_sram_invalidate(handle(hp)));
}

/* ------------------------------------------------------------------ cheats */
extern "C" JNIEXPORT jint JNICALL MB_JNI(MbNative, nativeCheatAdd)(
    JNIEnv *env, jclass, jlong hp, jstring code, jstring desc, jboolean enabled, jint kind,
    jint address, jint value, jint compare) {
  void *h = handle(hp);
  if (!h)
    return -MB_ERR_INVALID;
  mb_cheat c{};
  c.struct_size = sizeof(c);
  JChars cc(env, code), cd(env, desc);
  snprintf(c.code, sizeof(c.code), "%s", cc.get());
  snprintf(c.desc, sizeof(c.desc), "%s", cd.get());
  c.enabled = enabled ? 1 : 0;
  c.kind = int(kind);
  c.address = uint32_t(address);
  c.value = uint32_t(value);
  c.compare = int32_t(compare);
  return jint(mb_cheat_add(h, &c));
}
extern "C" JNIEXPORT jint JNICALL
MB_JNI(MbNative, nativeCheatRemove)(JNIEnv *env, jclass, jlong hp, jint id) {
  return status(env, handle(hp), mb_cheat_remove(handle(hp), id));
}
extern "C" JNIEXPORT jint JNICALL
MB_JNI(MbNative, nativeCheatSetEnabled)(JNIEnv *env, jclass, jlong hp, jint id, jboolean on) {
  return status(env, handle(hp), mb_cheat_set_enabled(handle(hp), id, on ? 1 : 0));
}
extern "C" JNIEXPORT jint JNICALL
MB_JNI(MbNative, nativeCheatSetValue)(JNIEnv *env, jclass, jlong hp, jint id, jint value) {
  return status(env, handle(hp), mb_cheat_set_value(handle(hp), id, uint32_t(value)));
}
extern "C" JNIEXPORT void JNICALL
MB_JNI(MbNative, nativeCheatClear)(JNIEnv *, jclass, jlong hp) {
  if (handle(hp))
    mb_cheat_clear(handle(hp));
}
extern "C" JNIEXPORT jint JNICALL
MB_JNI(MbNative, nativeCheatCount)(JNIEnv *, jclass, jlong hp) {
  return handle(hp) ? jint(mb_cheat_count(handle(hp))) : 0;
}

static jobjectArray cheat_info(JNIEnv *env, void *h, jint index, bool from_core) {
  if (!h)
    return nullptr;
  mb_cheat c{};
  c.struct_size = sizeof(c);
  mb_status rc = from_core ? mb_cheat_get_core(h, index, &c) : mb_cheat_get(h, index, &c);
  if (rc != MB_OK)
    return nullptr;
  std::vector<std::string> fields;
  fill_cheat(&fields, c);
  return str_array(env, fields);
}

extern "C" JNIEXPORT jobjectArray JNICALL
MB_JNI(MbNative, nativeCheatGet)(JNIEnv *env, jclass, jlong hp, jint index) {
  return cheat_info(env, handle(hp), index, false);
}
extern "C" JNIEXPORT jobjectArray JNICALL
MB_JNI(MbNative, nativeCheatGetCore)(JNIEnv *env, jclass, jlong hp, jint index) {
  return cheat_info(env, handle(hp), index, true);
}

extern "C" JNIEXPORT jlongArray JNICALL
MB_JNI(MbNative, nativeCheatDecode)(JNIEnv *env, jclass, jstring code) {
  JChars cc(env, code);
  uint32_t addr = 0, val = 0;
  int32_t cmp = 0;
  int kind = 0;
  mb_status rc = mb_cheat_decode(cc.get(), &addr, &val, &cmp, &kind);
  jlong out[5] = {jint(rc), jlong(addr), jlong(val), jlong(cmp), jlong(kind)};
  jlongArray arr = env->NewLongArray(5);
  if (arr)
    env->SetLongArrayRegion(arr, 0, 5, out);
  return arr;
}

/* ------------------------------------------------------------------ memory */
extern "C" JNIEXPORT jint JNICALL
MB_JNI(MbNative, nativeMemPeek)(JNIEnv *, jclass, jlong hp, jint address) {
  return handle(hp) ? jint(mb_mem_peek(handle(hp), uint32_t(address))) : 0;
}
extern "C" JNIEXPORT jint JNICALL
MB_JNI(MbNative, nativeMemPoke)(JNIEnv *env, jclass, jlong hp, jint address, jint value) {
  return status(env, handle(hp), mb_mem_poke(handle(hp), uint32_t(address), uint32_t(value)));
}
extern "C" JNIEXPORT jbyteArray JNICALL
MB_JNI(MbNative, nativeMemRead)(JNIEnv *env, jclass, jlong hp, jint address, jint len) {
  void *h = handle(hp);
  if (!h || len <= 0 || len > (1 << 20))
    return nullptr;
  /* resize(), not `buf(size_t(len))`: the parenthesised form is a function
   * declaration to the parser (most vexing parse) and the braced form becomes a
   * one-element initializer list. Both are silent-ish disasters in a memory view. */
  std::vector<uint8_t> buf;
  buf.resize(size_t(len));
  if (mb_mem_read(h, uint32_t(address), buf.data(), uint32_t(len)) != MB_OK)
    return nullptr;
  jbyteArray out = env->NewByteArray(len);
  if (out)
    env->SetByteArrayRegion(out, 0, len, reinterpret_cast<const jbyte *>(buf.data()));
  return out;
}
extern "C" JNIEXPORT jint JNICALL
MB_JNI(MbNative, nativeMemWrite)(JNIEnv *env, jclass, jlong hp, jint address, jbyteArray bytes) {
  void *h = handle(hp);
  if (!h || !bytes)
    return MB_ERR_INVALID;
  jsize n = env->GetArrayLength(bytes);
  std::vector<uint8_t> buf;
  buf.resize(size_t(n));
  env->GetByteArrayRegion(bytes, 0, n, reinterpret_cast<jbyte *>(buf.data()));
  return status(env, h, mb_mem_write(h, uint32_t(address), buf.data(), uint32_t(n)));
}
extern "C" JNIEXPORT jint JNICALL
MB_JNI(MbNative, nativeMemSetPeekMode)(JNIEnv *env, jclass, jlong hp, jint mode) {
  return status(env, handle(hp), mb_mem_set_peek_mode(handle(hp), int(mode)));
}

/* ------------------------------------------------------------------ options */
extern "C" JNIEXPORT jint JNICALL
MB_JNI(MbNative, nativeOptionCount)(JNIEnv *, jclass, jlong hp) {
  return handle(hp) ? jint(mb_option_count(handle(hp))) : 0;
}

extern "C" JNIEXPORT jobjectArray JNICALL
MB_JNI(MbNative, nativeOptionInfo)(JNIEnv *env, jclass, jlong hp, jint index) {
  void *h = handle(hp);
  if (!h)
    return nullptr;
  mb_option o{};
  o.struct_size = sizeof(o);
  if (mb_option_info(h, index, &o) != MB_OK)
    return nullptr;
  std::string values, labels;
  for (int i = 0; i < o.count && i < 16; i++) {
    if (i) {
      values += '|';
      labels += '|';
    }
    values += o.values[i];
    labels += o.labels[i][0] ? o.labels[i] : o.values[i];
  }
  char num[24];
  snprintf(num, sizeof(num), "%d", o.default_index);
  std::vector<std::string> f(kOptCount);
  f[kOptKey] = o.key;
  f[kOptDesc] = o.desc;
  f[kOptCategory] = o.category;
  f[kOptValues] = values;
  f[kOptLabels] = labels;
  f[kOptCurrent] = o.defaults[0];
  f[kOptDefault] = num;
  return str_array(env, f);
}

extern "C" JNIEXPORT jint JNICALL
MB_JNI(MbNative, nativeOptionSet)(JNIEnv *env, jclass, jlong hp, jstring key, jstring value) {
  JChars k(env, key), v(env, value);
  return status(env, handle(hp), mb_option_set(handle(hp), k.get(), v.get()));
}
extern "C" JNIEXPORT jstring JNICALL
MB_JNI(MbNative, nativeOptionGet)(JNIEnv *env, jclass, jlong hp, jstring key) {
  void *h = handle(hp);
  if (!h)
    return jstr(env, "");
  JChars k(env, key);
  char buf[64] = {0};
  if (mb_option_get(h, k.get(), buf, sizeof(buf)) != MB_OK)
    return jstr(env, "");
  return jstr(env, buf);
}

/* ------------------------------------------------------------------ misc */
extern "C" JNIEXPORT jint JNICALL MB_JNI(MbNative, nativeReset)(JNIEnv *env, jclass, jlong hp) {
  return status(env, handle(hp), mb_reset(handle(hp)));
}
extern "C" JNIEXPORT jint JNICALL
MB_JNI(MbNative, nativePowerCycle)(JNIEnv *env, jclass, jlong hp) {
  return status(env, handle(hp), mb_power_cycle(handle(hp)));
}

extern "C" JNIEXPORT jlongArray JNICALL
MB_JNI(MbNative, nativeStatsLong)(JNIEnv *env, jclass, jlong hp) {
  void *h = handle(hp);
  jlong out[kStatCount] = {0};
  if (h) {
    mb_stats s{};
    s.struct_size = sizeof(s);
    if (mb_get_stats(h, &s) == MB_OK) {
      out[kStatFrames] = jlong(s.frames_emulated);
      out[kStatPresented] = jlong(s.frames_presented);
      out[kStatSkipped] = jlong(s.frames_skipped);
      out[kStatInputFrames] = jlong(s.input_frames);
      out[kStatVideoW] = jlong(s.video_width);
      out[kStatVideoH] = jlong(s.video_height);
      out[kStatPitch] = jlong(s.video_pitch);
      out[kStatBaseW] = jlong(s.base_width);
      out[kStatBaseH] = jlong(s.base_height);
      out[kStatRate] = jlong(s.sample_rate);
      out[kStatAudioWritten] = jlong(s.audio_samples_written);
      out[kStatUnderruns] = jlong(s.audio_underruns);
      out[kStatRewindAvail] = jlong(s.rewind_frames_available);
      out[kStatRewindCap] = jlong(s.rewind_capacity_frames);
      out[kStatRewindBytes] = jlong(s.rewind_bytes);
      out[kStatStateSize] = jlong(s.state_size);
      out[kStatSramSize] = jlong(s.sram_size);
      out[kStatPaused] = jlong(s.paused);
      out[kStatRunning] = jlong(s.running);
      out[kStatAudioOn] = jlong(s.audio_enabled);
      out[kStatPal] = jlong(s.pal);
      out[kStatMsPerFrame] = jlong(s.ms_per_frame);
      out[kStatCpu] = jlong(s.core_cpu_percent);
    }
  }
  jlongArray arr = env->NewLongArray(kStatCount);
  if (arr)
    env->SetLongArrayRegion(arr, 0, kStatCount, out);
  return arr;
}

extern "C" JNIEXPORT jdoubleArray JNICALL
MB_JNI(MbNative, nativeStatsDouble)(JNIEnv *env, jclass, jlong hp) {
  void *h = handle(hp);
  jdouble out[kDoubleCount] = {0.0, 0.0, 0.0};
  if (h) {
    mb_stats s{};
    s.struct_size = sizeof(s);
    if (mb_get_stats(h, &s) == MB_OK) {
      out[kFps] = s.present_fps;
      out[kSpeed] = s.emu_speed;
      out[kAspect] = s.aspect_ratio;
    }
  }
  jdoubleArray arr = env->NewDoubleArray(kDoubleCount);
  if (arr)
    env->SetDoubleArrayRegion(arr, 0, kDoubleCount, out);
  return arr;
}
