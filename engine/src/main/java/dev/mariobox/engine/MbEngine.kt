package dev.mariobox.engine

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.File

/**
 * Where the runtime looks for loadable cores.
 *
 * AGP drops every shared object built by :engine's CMake into the APK's ABI lib
 * directory, and the host dlopens the core from an absolute path. That indirection
 * is the point: a second core becomes another `add_library` in CMake plus one more
 * entry in this list, never a link-time choice.
 */
object Cores {
    const val FCEUMM = "libmbcore_fceumm.so"

    fun dir(context: Context): File = File(context.applicationInfo.nativeLibraryDir)

    fun path(context: Context, name: String = FCEUMM): String = File(dir(context), name).absolutePath

    /** Cores actually present in the APK, as short names ("fceumm"). */
    fun available(context: Context): List<String> =
        dir(context).listFiles()?.asSequence()
            ?.map { it.name }
            ?.filter { it.startsWith("libmbcore_") && it.endsWith(".so") }
            ?.map { it.removePrefix("libmbcore_").removeSuffix(".so") }
            ?.sorted()
            ?.toList()
            ?: emptyList()
}

class EngineException(message: String, val status: Int = MbStatus.INVALID) : Exception(message)

/**
 * One running game session.
 *
 * The engine is deliberately dumb: it owns a handle and marshals calls, keeps no
 * copy of emulator state that the host can answer for itself, and never blocks the
 * caller longer than the host does. Every method that can fail returns a status or
 * throws [EngineException] with the host's own error message attached, because a
 * generic "load failed" on a phone with 400 ROMs is useless to a user.
 *
 * Threading: [load] and [release] are safe from a worker (and should be called
 * there); everything else is safe from the UI thread while the emulator runs --
 * the host serialises it and only ever blocks for the length of one frame.
 */
class MbEngine {
    /** Set when the ABI the Kotlin side expects is not the ABI the .so exports. */
    val abiVersion: Int
        get() = if (MbNative.available) MbNative.nativeAbiVersion() else -1

    var handle: Long = 0L
        private set

    private var rom: File? = null
    private var saveDir: File? = null

    val active: Boolean get() = handle != 0L
    val loadedRom: File? get() = rom

    /**
     * Creates the session and starts the emulation thread.
     *
     * [saveDir] holds `<rom>.srm`, the save-state slots and the rewind spool; the
     * caller picks it (the app uses `filesDir/saves/<rom basename>`), because the
     * engine must not decide where a user's progress lives.
     */
    fun load(
        context: Context,
        rom: File,
        saveDir: File,
        coreName: String = Cores.FCEUMM,
        systemDir: File? = null,
        options: EngineOptions = EngineOptions(),
        listener: EngineListener? = null,
    ): Result<Unit> {
        val corePath = Cores.path(context, coreName)
        if (active) release()
        if (!MbNative.available) {
            return Result.failure(
                EngineException(
                    "lib${MbNative.LIBRARY}.so is not loaded -- the APK was built without the native engine",
                    MbStatus.NO_CORE,
                )
            )
        }
        if (!rom.isFile) {
            return Result.failure(EngineException("not a readable file: ${rom.absolutePath}", MbStatus.NO_ROM))
        }
        if (!saveDir.isDirectory && !saveDir.mkdirs()) {
            return Result.failure(EngineException("cannot create ${saveDir.absolutePath}", MbStatus.IO))
        }
        val core = File(corePath)
        if (!core.isFile) {
            return Result.failure(EngineException("core missing from the APK: ${core.name}", MbStatus.NO_CORE))
        }

        val created = MbNative.nativeCreate(
            corePath = core.absolutePath,
            romPath = rom.absolutePath,
            saveDir = saveDir.absolutePath,
            systemDir = systemDir?.absolutePath,
            rewindFrames = options.rewindFrames(),
            rewindStride = options.rewindStride,
            rewindBudgetKb = options.rewindBudgetKb,
            runAhead = options.runAhead,
            audioEnable = options.audioEnabled,
            sampleRate = options.sampleRate,
            videoEnable = options.videoEnabled,
            sramEnable = options.sramEnabled,
            sramSaveOnState = options.sramSaveOnState,
            listener = listener,
        )
        if (created == 0L) {
            return Result.failure(
                EngineException("the engine refused to start on ${rom.name} (ABI $abiVersion)", MbStatus.NO_CORE)
            )
        }
        handle = created
        val started = MbNative.nativeStart(created)
        if (started != MbStatus.OK) {
            val msg = MbStatus.describe(started, MbNative.nativeLastError(created))
            MbNative.nativeDestroy(created)
            handle = 0L
            return Result.failure(EngineException(msg, started))
        }
        this.rom = rom
        this.saveDir = saveDir
        Log.i(TAG, "started ${rom.name} (state ${MbNative.nativeStatsLong(created).getOrElse(15) { 0 }} bytes)")
        return Result.success(Unit)
    }

    fun stop() {
        if (!active) return
        val rc = MbNative.nativeStop(handle)
        if (rc != MbStatus.OK) Log.w(TAG, "nativeStop -> ${MbNative.nativeStatusString(rc)}")
    }

    fun release() {
        if (!active) return
        MbNative.nativeDestroy(handle)
        handle = 0L
        rom = null
        saveDir = null
    }

    // ---------------------------------------------------------------- lifecycle
    val isPaused: Boolean get() = active && MbNative.nativeIsPaused(handle)
    val isRunning: Boolean get() = active && MbNative.nativeIsRunning(handle)

    fun setPaused(paused: Boolean) {
        if (active) MbNative.nativeSetPaused(handle, paused)
    }

    /** Returns the new pause state, or false when there is nothing to pause. */
    fun togglePause(): Boolean {
        if (!active) return false
        val next = !MbNative.nativeIsPaused(handle)
        MbNative.nativeSetPaused(handle, next)
        return next
    }

    fun reset(): Int = if (active) MbNative.nativeReset(handle) else MbStatus.INVALID
    fun powerCycle(): Int = if (active) MbNative.nativePowerCycle(handle) else MbStatus.INVALID
    fun lastError(): String = if (active) MbNative.nativeLastError(handle) else "engine not created"

    // ---------------------------------------------------------------- presentation
    /** [surface] is an `android.view.Surface`; null detaches and stops drawing. */
    fun setSurface(surface: Any?): Int =
        if (active) MbNative.nativeSetSurface(handle, surface) else MbStatus.INVALID

    fun setSurfaceSize(width: Int, height: Int): Int =
        if (active) MbNative.nativeSetSurfaceSize(handle, width, height) else MbStatus.INVALID

    fun setVideoEnabled(enabled: Boolean) {
        if (active) MbNative.nativeSetVideoEnabled(handle, enabled)
    }

    fun applyRender(r: RenderSettings) {
        if (!active) return
        MbNative.nativeSetRenderConfig(
            handle, r.scaleMode, r.filterMode, r.scanlinesPercent, r.overscanCrop, r.rotation,
        )
    }

    fun setVolume(volume: Float) {
        if (active) MbNative.nativeSetAudioVolume(handle, volume)
    }

    val volume: Float
        get() = if (active) MbNative.nativeGetAudioVolume(handle) else 1f

    // ---------------------------------------------------------------- input
    fun setButtons(mask: Int, port: Int = 0) {
        if (active) MbNative.nativeSetInput(handle, port, mask)
    }

    fun buttons(port: Int = 0): Int = if (active) MbNative.nativeGetInput(handle, port) else 0

    fun setTurbo(mask: Int, hz: Int = 30, port: Int = 0) {
        if (active) MbNative.nativeSetTurbo(handle, port, mask, hz)
    }

    fun setFastForward(on: Boolean) {
        if (active) MbNative.nativeSetFastForward(handle, on)
    }

    fun setSlowMotion(on: Boolean) {
        if (active) MbNative.nativeSetSlowMotion(handle, on)
    }

    fun advanceFrame(): Int = if (active) MbNative.nativeAdvanceFrame(handle) else MbStatus.INVALID

    // ---------------------------------------------------------------- save states
    fun saveState(path: String): Int =
        if (active) MbNative.nativeSaveState(handle, path) else MbStatus.INVALID

    fun loadState(path: String): Int =
        if (active) MbNative.nativeLoadState(handle, path) else MbStatus.INVALID

    fun rewindStep(frames: Int): Int =
        if (active) MbNative.nativeRewindStep(handle, frames.coerceAtLeast(1)) else MbStatus.INVALID

    fun rewindAvailable(): Int = if (active) MbNative.nativeRewindAvailable(handle) else 0

    fun rewindClear() {
        if (active) MbNative.nativeRewindClear(handle)
    }

    fun flushBattery(): Int = if (active) MbNative.nativeSramFlush(handle) else MbStatus.INVALID

    fun reloadBattery(): Int = if (active) MbNative.nativeSramInvalidate(handle) else MbStatus.INVALID

    /** The directory `load()` used, for the save-state and SRAM screens. */
    fun saveDirectory(): File? = saveDir

    /**
     * The most recent frame as a bitmap, for save-state thumbnails and the
     * "what am I about to overwrite?" dialog. Null when no frame has been drawn.
     */
    fun frameBitmap(maxWidth: Int = 256): Bitmap? {
        if (!active) return null
        val meta = IntArray(3)
        val bytes = MbNative.nativeCopyFrame(handle, meta) ?: return null
        val w = meta[0]
        val h = meta[1]
        val pitch = meta[2]
        if (w <= 0 || h <= 0 || pitch <= 0) return null
        val scale = if (maxWidth in 1 until w) maxWidth.toFloat() / w else 1f
        val ow = (w * scale).toInt().coerceAtLeast(1)
        val oh = (h * scale).toInt().coerceAtLeast(1)
        val pixels = IntArray(ow * oh)
        for (y in 0 until oh) {
            val sy = (y / scale).toInt().coerceAtMost(h - 1)
            val row = sy * pitch
            for (x in 0 until ow) {
                val sx = (x / scale).toInt().coerceAtMost(w - 1)
                val i = row + sx * 4
                if (i + 3 < bytes.size) {
                    // mb_copy_frame hands back R,G,B,A bytes; a Bitmap wants the
                    // 0xAARRGGBB int, so shift them into place rather than trusting
                    // a ByteBuffer's byte order.
                    pixels[y * ow + x] = (0xFF shl 24) or
                        ((bytes[i].toInt() and 0xFF) shl 16) or
                        ((bytes[i + 1].toInt() and 0xFF) shl 8) or
                        (bytes[i + 2].toInt() and 0xFF)
                }
            }
        }
        // createBitmap(int[], ...) is a newer overload than this module's minSdk, and
        // setPixels on a plain allocate-and-fill bitmap is both API 1 and obvious.
        val out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, ow, 0, 0, ow, oh)
        return out
    }

    // ---------------------------------------------------------------- stats
    fun stats(): EngineStats {
        if (!active) return EngineStats.EMPTY
        return try {
            EngineStats.decode(MbNative.nativeStatsLong(handle), MbNative.nativeStatsDouble(handle))
        } catch (t: Throwable) {
            Log.w(TAG, "stats failed", t)
            EngineStats.EMPTY
        }
    }

    // ---------------------------------------------------------------- cheats
    fun cheats(): List<Cheat> {
        if (!active) return emptyList()
        val n = MbNative.nativeCheatCount(handle)
        val out = ArrayList<Cheat>(n.coerceAtLeast(0))
        for (i in 0 until n) {
            val f = MbNative.nativeCheatGet(handle, i) ?: continue
            out.add(cheatFromFields(i, f))
        }
        return out
    }

    /** The same entry as the core's own cheat engine sees it, for the "is it live?" view. */
    fun cheatFromCore(index: Int): Cheat? {
        if (!active) return null
        val f = MbNative.nativeCheatGetCore(handle, index) ?: return null
        return cheatFromFields(index, f)
    }

    private fun cheatFromFields(index: Int, f: Array<String>): Cheat = Cheat(
        id = index,
        code = f.getOrElse(0) { "" },
        description = f.getOrElse(1) { "" },
        enabled = f.getOrElse(2) { "0" } != "0",
        kind = f.getOrElse(3) { "0" }.toIntOrNull() ?: 0,
        address = f.getOrElse(4) { "0" }.toLongOrNull()?.toInt() ?: 0,
        value = f.getOrElse(5) { "0" }.toLongOrNull()?.toInt() ?: 0,
        compare = f.getOrElse(6) { "-1" }.toIntOrNull() ?: -1,
    )

    /**
     * Adds a cheat and returns its id.
     *
     * The id is the host's, and it is stable until the list is cleared -- which is
     * what lets the UI toggle an entry from a checkbox without re-reading the list.
     */
    fun addCheat(
        code: String,
        description: String = "",
        enabled: Boolean = true,
        kind: Int = Cheat.KIND_AUTO,
        address: Int = 0,
        value: Int = 0,
        compare: Int = -1,
    ): Int {
        if (!active) return -MbStatus.INVALID
        return MbNative.nativeCheatAdd(handle, code, description, enabled, kind, address, value, compare)
    }

    fun setCheatEnabled(id: Int, enabled: Boolean): Int =
        if (active) MbNative.nativeCheatSetEnabled(handle, id, enabled) else MbStatus.INVALID

    fun removeCheat(id: Int): Int = if (active) MbNative.nativeCheatRemove(handle, id) else MbStatus.INVALID

    fun clearCheats() {
        if (active) MbNative.nativeCheatClear(handle)
    }

    fun decodeCheat(code: String): CheatDecode = CheatDecode.decode(code)

    // ---------------------------------------------------------------- core options
    fun options(): List<CoreOption> {
        if (!active) return emptyList()
        val n = MbNative.nativeOptionCount(handle)
        val out = ArrayList<CoreOption>(n)
        for (i in 0 until n) {
            val f = MbNative.nativeOptionInfo(handle, i) ?: continue
            if (f.size < 7) continue
            out.add(
                CoreOption(
                    key = f[0],
                    description = f[1],
                    category = f[2],
                    values = f[3].split('|').filter { it.isNotEmpty() },
                    labels = f[4].split('|').filter { it.isNotEmpty() },
                    current = f[5],
                    defaultIndex = f[6].toIntOrNull() ?: 0,
                )
            )
        }
        return out
    }

    fun optionValue(key: String): String =
        if (active) MbNative.nativeOptionGet(handle, key) else ""

    /**
     * Applies a core option. Some options only take effect on the core's next
     * `retro_unload_game`/load cycle (region and the NTSC preset are the two that
     * matter for a NES core); the host reports success for both cases, so the UI
     * says "takes effect after a restart" for the ones that need it rather than
     * pretending.
     */
    fun setOption(key: String, value: String): Int =
        if (active) MbNative.nativeOptionSet(handle, key, value) else MbStatus.INVALID

    fun needsRestart(key: String): Boolean = key in RESTART_KEYS

    // ---------------------------------------------------------------- memory
    fun peek(address: Int): Int = if (active) MbNative.nativeMemPeek(handle, address) else 0

    fun poke(address: Int, value: Int): Int =
        if (active) MbNative.nativeMemPoke(handle, address, value) else MbStatus.INVALID

    fun readMemory(address: Int, length: Int): ByteArray? =
        if (active) MbNative.nativeMemRead(handle, address, length) else null

    fun writeMemory(address: Int, bytes: ByteArray): Int =
        if (active) MbNative.nativeMemWrite(handle, address, bytes) else MbStatus.INVALID

    companion object {
        private const val TAG = "MarioBox"

        /** Options FCEUmm only re-reads when the cartridge is re-inserted. */
        val RESTART_KEYS = setOf(
            "fceumm_next_region",
            "fceumm_region",
            "fceumm_next_ntsc_filter",
            "fceumm_ntsc_filter",
            "fceumm_next_overclock",
        )
    }
}
