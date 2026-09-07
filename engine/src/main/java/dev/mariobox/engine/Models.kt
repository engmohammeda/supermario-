package dev.mariobox.engine

/** Status codes from `mb_status` in host/mb_abi.h. Keep in sync with that enum. */
object MbStatus {
    const val OK = 0
    const val INVALID = 1
    const val NO_CORE = 2
    const val NO_ROM = 3
    const val UNSUPPORTED = 4
    const val IO = 5
    const val STATE = 6
    const val BUSY = 7
    const val NOMEM = 8
    const val NO_GAME = 9

    /** A message the UI can show verbatim: the host keeps a richer one per handle. */
    fun describe(status: Int, lastError: String?): String {
        val base = when (status) {
            OK -> return lastError?.takeIf { it.isNotBlank() } ?: "OK"
            INVALID -> "argument rejected by the engine"
            NO_CORE -> "the emulation core could not be loaded"
            NO_ROM -> "the ROM could not be read"
            UNSUPPORTED -> "this core does not implement that feature"
            IO -> "a file operation failed"
            STATE -> "the save state was rejected (different game or truncated)"
            BUSY -> "the engine refused that operation while running"
            NOMEM -> "out of memory"
            NO_GAME -> "no cartridge is loaded"
            else -> "engine error $status"
        }
        return if (lastError.isNullOrBlank()) base else "$base -- $lastError"
    }
}

/** `MB_EVENT_*`: what the host pushes up from the emulation thread. */
object MbEvent {
    const val GEOMETRY = 1
    const val MESSAGE = 2
    const val GAME_INFO = 3
    const val ERROR = 4
}

/** Implemented by anything that wants the host's event stream (toasts, geometry). */
fun interface EngineListener {
    /** Called on the emulation thread. Implementations must not block. */
    fun onEngineEvent(id: Int, text: String)
}

/** A cheat as the host stores it. [kind] mirrors `mb_cheat.kind`. */
data class Cheat(
    val id: Int,
    val code: String,
    val description: String,
    val enabled: Boolean,
    val kind: Int,
    val address: Int,
    val value: Int,
    val compare: Int,
) {
    val kindName: String
        get() = when (kind) {
            KIND_AUTO -> "auto"
            KIND_GAME_GENIE -> "Game Genie"
            KIND_PAR -> "Par"
            KIND_ACTION_REPLAY -> "Action Replay"
            KIND_RAW_POKE -> "poke"
            else -> "unknown"
        }

    /** What the core's own cheat engine holds for this entry, if the UI asked. */
    var coreMirror: Cheat? = null

    companion object {
        const val KIND_AUTO = 0
        const val KIND_GAME_GENIE = 1
        const val KIND_PAR = 2
        const val KIND_ACTION_REPLAY = 3
        const val KIND_RAW_POKE = 4
    }
}

/** One entry of the core's option table, ready for a dropdown. */
data class CoreOption(
    val key: String,
    val description: String,
    val category: String,
    /** Strings to send back to the engine, one per choice. */
    val values: List<String>,
    /** Display text for the same choices (may be identical to [values]). */
    val labels: List<String>,
    val current: String,
    val defaultIndex: Int,
) {
    val currentIndex: Int
        get() = values.indexOf(current).let { if (it < 0) defaultIndex.coerceIn(0, values.size - 1) else it }
}

/** Presentation settings handed to the GL renderer. Values match mb_abi.h enums. */
data class RenderSettings(
    val scaleMode: Int = SCALE_FIT,
    val filterMode: Int = FILTER_NEAREST,
    val scanlinesPercent: Int = 0,
    /** Percent of each axis cropped away, applied evenly to both edges. */
    val overscanCrop: Int = 0,
    /** 0, 90 or 180 clockwise. */
    val rotation: Int = 0,
    /** View zoom: 1.0 = native fit; 0.25..4.0. >1 crops into the picture. */
    val zoom: Float = 1f,
) {
    companion object {
        const val SCALE_FIT = 0
        const val SCALE_INTEGER = 1
        const val SCALE_STRETCH = 2
        const val SCALE_FILL_CROP = 3
        const val FILTER_NEAREST = 0
        const val FILTER_LINEAR = 1
        const val ZOOM_MIN = 0.25f
        const val ZOOM_MAX = 4f

        val scaleNames = listOf(
            "Fit (letterbox)",
            "Integer scale",
            "Stretch to fill",
            "Fill and crop",
        )
        val filterNames = listOf("Nearest (pixels)", "Linear (smooth)")
    }
}

/** Everything that has to be decided before the core is even dlopen'd. */
data class EngineOptions(
    val rewindSeconds: Int = 20,
    val rewindStride: Int = 1,
    val rewindBudgetKb: Int = 24 * 1024,
    val runAhead: Int = 0,
    val audioEnabled: Boolean = true,
    val sampleRate: Int = 44100,
    val videoEnabled: Boolean = true,
    val sramEnabled: Boolean = true,
    val sramSaveOnState: Boolean = true,
) {
    /** Frames of history at the emulator's nominal rate; the ring clamps to budget. */
    internal fun rewindFrames(fps: Double = 60.0988) = (rewindSeconds * fps).toInt().coerceAtLeast(1)
}

/** Live counters, decoded from the two primitive arrays the JNI bridge returns. */
data class EngineStats(
    val framesEmulated: Long,
    val framesPresented: Long,
    val framesSkipped: Long,
    val inputFrames: Long,
    val videoWidth: Int,
    val videoHeight: Int,
    val pitch: Int,
    val baseWidth: Int,
    val baseHeight: Int,
    val sampleRate: Int,
    val audioSamplesWritten: Long,
    val underruns: Int,
    val rewindAvailable: Int,
    val rewindCapacity: Int,
    val rewindBytes: Long,
    val stateSize: Int,
    val sramSize: Int,
    val paused: Boolean,
    val running: Boolean,
    val audioEnabled: Boolean,
    val pal: Boolean,
    val msPerFrame: Int,
    val cpuPercent: Int,
    val presentFps: Double,
    val emuSpeed: Double,
    val aspectRatio: Double,
) {
    companion object {
        val EMPTY = EngineStats(
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            false, false, false, false, 0, 0, 0.0, 0.0, 0.0,
        )

        /** Mirrors LongStat/DoubleStat in mariobox_jni.cpp. */
        fun decode(l: LongArray, d: DoubleArray): EngineStats = EngineStats(
            framesEmulated = l.getOrElse(0) { 0 },
            framesPresented = l.getOrElse(1) { 0 },
            framesSkipped = l.getOrElse(2) { 0 },
            inputFrames = l.getOrElse(3) { 0 },
            videoWidth = l.getOrElse(4) { 0 }.toInt(),
            videoHeight = l.getOrElse(5) { 0 }.toInt(),
            pitch = l.getOrElse(6) { 0 }.toInt(),
            baseWidth = l.getOrElse(7) { 0 }.toInt(),
            baseHeight = l.getOrElse(8) { 0 }.toInt(),
            sampleRate = l.getOrElse(9) { 0 }.toInt(),
            audioSamplesWritten = l.getOrElse(10) { 0 },
            underruns = l.getOrElse(11) { 0 }.toInt(),
            rewindAvailable = l.getOrElse(12) { 0 }.toInt(),
            rewindCapacity = l.getOrElse(13) { 0 }.toInt(),
            rewindBytes = l.getOrElse(14) { 0 },
            stateSize = l.getOrElse(15) { 0 }.toInt(),
            sramSize = l.getOrElse(16) { 0 }.toInt(),
            paused = l.getOrElse(17) { 0 } != 0L,
            running = l.getOrElse(18) { 0 } != 0L,
            audioEnabled = l.getOrElse(19) { 0 } != 0L,
            pal = l.getOrElse(20) { 0 } != 0L,
            msPerFrame = l.getOrElse(21) { 0 }.toInt(),
            cpuPercent = l.getOrElse(22) { 0 }.toInt(),
            presentFps = d.getOrElse(0) { 0.0 },
            emuSpeed = d.getOrElse(1) { 0.0 },
            aspectRatio = d.getOrElse(2) { 0.0 },
        )
    }
}

/** Cheat-code decoding without a running core, for live validation in the editor. */
data class CheatDecode(
    val ok: Boolean,
    val address: Int,
    val value: Int,
    val compare: Int,
    val kind: Int,
) {
    companion object {
        fun decode(code: String): CheatDecode {
            val r = try {
                MbNative.nativeCheatDecode(code)
            } catch (t: Throwable) {
                null
            } ?: return CheatDecode(false, 0, 0, 0, 0)
            return CheatDecode(
                ok = r.getOrElse(0) { 1L } == 0L,
                address = r.getOrElse(1) { 0L }.toInt(),
                value = r.getOrElse(2) { 0L }.toInt(),
                compare = r.getOrElse(3) { 0L }.toInt(),
                kind = r.getOrElse(4) { 0L }.toInt(),
            )
        }
    }
}
