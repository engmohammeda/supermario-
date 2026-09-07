package dev.mariobox.engine

/**
 * The raw JNI surface. One external function per `mb_*` entry point in
 * `engine/src/main/cpp/host/mb_abi.h`, with the same argument order as
 * `platform/android/mariobox_jni.cpp` -- that file is the source of truth, and the
 * engine tests in CI fail at link time if the two drift, which is a much better
 * failure mode than a `UnsatisfiedLinkError` on a user's phone.
 *
 * Nothing here is safe to call with a zero handle by accident, so nothing outside
 * [MbEngine] should use it: [MbEngine] owns creation, the listener lifetime and the
 * "did the .so load at all" question.
 */
internal object MbNative {
    const val LIBRARY = "mariobox_host"

    /** Loaded once per process. A missing library means the ABI list and the
     *  CMake target disagreed, which is a packaging bug, not a runtime state. */
    @Volatile
    private var loaded = false

    val available: Boolean
        get() {
            if (!loaded) {
                loaded = try {
                    System.loadLibrary(LIBRARY)
                    true
                } catch (t: Throwable) {
                    android.util.Log.e("MarioBox", "lib$LIBRARY.so failed to load", t)
                    false
                }
            }
            return loaded
        }

    external fun nativeAbiVersion(): Int

    external fun nativeCreate(
        corePath: String,
        romPath: String,
        saveDir: String,
        systemDir: String?,
        rewindFrames: Int,
        rewindStride: Int,
        rewindBudgetKb: Int,
        runAhead: Int,
        audioEnable: Boolean,
        sampleRate: Int,
        videoEnable: Boolean,
        sramEnable: Boolean,
        sramSaveOnState: Boolean,
        listener: EngineListener?,
    ): Long

    external fun nativeDestroy(handle: Long)
    external fun nativeStart(handle: Long): Int
    external fun nativeStop(handle: Long): Int
    external fun nativeIsRunning(handle: Long): Boolean
    external fun nativeSetPaused(handle: Long, paused: Boolean)
    external fun nativeIsPaused(handle: Long): Boolean
    external fun nativeLastError(handle: Long): String
    external fun nativeStatusString(status: Int): String

    external fun nativeSetSurface(handle: Long, surface: Any?): Int
    external fun nativeSetSurfaceSize(handle: Long, width: Int, height: Int): Int
    external fun nativeSetVideoEnabled(handle: Long, enabled: Boolean)
    external fun nativeSetRenderConfig(
        handle: Long,
        scaleMode: Int,
        filterMode: Int,
        scanlinesPercent: Int,
        overscanCrop: Int,
        rotation: Int,
        zoom: Float,
    )

    /** [meta] receives {width, height, pitch}; the return is RGBA bytes. */
    external fun nativeCopyFrame(handle: Long, meta: IntArray): ByteArray?

    external fun nativeSetAudioVolume(handle: Long, volume: Float)
    external fun nativeGetAudioVolume(handle: Long): Float

    external fun nativeSetInput(handle: Long, port: Int, bitmask: Int)
    external fun nativeGetInput(handle: Long, port: Int): Int
    external fun nativeSetTurbo(handle: Long, port: Int, mask: Int, hz: Int)
    external fun nativeSetFastForward(handle: Long, enabled: Boolean)
    external fun nativeSetSlowMotion(handle: Long, enabled: Boolean)
    external fun nativeAdvanceFrame(handle: Long): Int

    external fun nativeSaveState(handle: Long, path: String): Int
    external fun nativeLoadState(handle: Long, path: String): Int
    external fun nativeRewindStep(handle: Long, frames: Int): Int
    external fun nativeRewindAvailable(handle: Long): Int
    external fun nativeRewindClear(handle: Long)
    external fun nativeSramFlush(handle: Long): Int
    external fun nativeSramInvalidate(handle: Long): Int

    external fun nativeCheatAdd(
        handle: Long,
        code: String,
        desc: String,
        enabled: Boolean,
        kind: Int,
        address: Int,
        value: Int,
        compare: Int,
    ): Int

    external fun nativeCheatRemove(handle: Long, id: Int): Int
    external fun nativeCheatSetEnabled(handle: Long, id: Int, enabled: Boolean): Int
    external fun nativeCheatSetValue(handle: Long, id: Int, value: Int): Int
    external fun nativeCheatClear(handle: Long)
    external fun nativeCheatCount(handle: Long): Int
    external fun nativeCheatGet(handle: Long, index: Int): Array<String>?
    external fun nativeCheatGetCore(handle: Long, index: Int): Array<String>?
    external fun nativeCheatDecode(code: String): LongArray?

    external fun nativeMemPeek(handle: Long, address: Int): Int
    external fun nativeMemPoke(handle: Long, address: Int, value: Int): Int
    external fun nativeMemRead(handle: Long, address: Int, len: Int): ByteArray?
    external fun nativeMemWrite(handle: Long, address: Int, bytes: ByteArray): Int
    external fun nativeMemSetPeekMode(handle: Long, mode: Int): Int

    external fun nativeOptionCount(handle: Long): Int
    external fun nativeOptionInfo(handle: Long, index: Int): Array<String>?
    external fun nativeOptionSet(handle: Long, key: String, value: String): Int
    external fun nativeOptionGet(handle: Long, key: String): String

    external fun nativeReset(handle: Long): Int
    external fun nativePowerCycle(handle: Long): Int
    external fun nativeStatsLong(handle: Long): LongArray
    external fun nativeStatsDouble(handle: Long): DoubleArray
}
