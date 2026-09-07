package dev.mariobox.app

import android.content.Context
import android.content.SharedPreferences
import dev.mariobox.engine.RenderSettings
import java.io.File

/**
 * User settings, on [SharedPreferences].
 *
 * docs/PLAN.md ADR-0005 names DataStore, and DataStore is the right long-term home
 * for a settings screen that grows per-game overrides. It is not here yet for one
 * reason: every preference in this build is a primitive read on the UI thread
 * before a game starts, which is exactly what SharedPreferences is fine for, and
 * adding a coroutine-backed store now would be a dependency the CI gate has to
 * agree with. The read/write surface is confined to this class, so the swap is
 * local.
 */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("mariobox", Context.MODE_PRIVATE)

    var romsDir: File = File(context.filesDir, "roms")
    var savesDir: File = File(context.filesDir, "saves")

    init {
        romsDir.mkdirs()
        savesDir.mkdirs()
    }

    /** Per-cartridge save directory: SRAM, slots and thumbnails all live here. */
    fun saveDirFor(rom: File): File = File(savesDir, rom.nameWithoutExtension).apply { mkdirs() }

    var scaleMode: Int
        get() = sp.getInt("scale", RenderSettings.SCALE_FIT)
        set(v) = sp.edit().putInt("scale", v).apply()

    var filterMode: Int
        get() = sp.getInt("filter", RenderSettings.FILTER_NEAREST)
        set(v) = sp.edit().putInt("filter", v).apply()

    var scanlinesPercent: Int
        get() = sp.getInt("scanlines", 0)
        set(v) = sp.edit().putInt("scanlines", v.coerceIn(0, 100)).apply()

    var overscanCrop: Int
        get() = sp.getInt("overscan", 0)
        set(v) = sp.edit().putInt("overscan", v.coerceIn(0, 25)).apply()

    var rotation: Int
        get() = sp.getInt("rotation", 0)
        set(v) = sp.edit().putInt("rotation", v).apply()

    var volume: Float
        get() = sp.getFloat("volume", 1f)
        set(v) = sp.edit().putFloat("volume", v.coerceIn(0f, 1f)).apply()

    var audioEnabled: Boolean
        get() = sp.getBoolean("audio", true)
        set(v) = sp.edit().putBoolean("audio", v).apply()

    /** 0 disables the rewind ring entirely, which is the memory-saving choice. */
    var rewindSeconds: Int
        get() = sp.getInt("rewind_seconds", 20)
        set(v) = sp.edit().putInt("rewind_seconds", v.coerceIn(0, 120)).apply()

    /** Extra frames computed per presented frame: the latency knob, 0..3. */
    var runAhead: Int
        get() = sp.getInt("run_ahead", 0)
        set(v) = sp.edit().putInt("run_ahead", v.coerceIn(0, 3)).apply()

    var sramEnabled: Boolean
        get() = sp.getBoolean("sram", true)
        set(v) = sp.edit().putBoolean("sram", v).apply()

    /** Which overlay preset to draw; the editor that moves individual buttons is M4. */
    var overlayLayout: Int
        get() = try {
            sp.getInt("overlay_layout", LAYOUT_BOTH)
        } catch (e: Exception) {
            LAYOUT_BOTH
        }
        set(v) = sp.edit().putInt("overlay_layout", v.coerceIn(0, 2)).apply()

    var turboHz: Int
        get() = sp.getInt("turbo_hz", 30)
        set(v) = sp.edit().putInt("turbo_hz", v.coerceIn(1, 60)).apply()

    var lastRomPath: String?
        get() = sp.getString("last_rom", null)
        set(v) = sp.edit().putString("last_rom", v ?: "").apply()

    /** Write the quick save when the game is left, so "continue" is always possible. */
    var autoSaveOnExit: Boolean
        get() = sp.getBoolean("autosave_exit", true)
        set(v) = sp.edit().putBoolean("autosave_exit", v).apply()

    /** Show the touch overlay; a gamepad user turns it off to reclaim the screen. */
    var overlayVisible: Boolean
        get() = try {
            sp.getBoolean("overlay_visible", true)
        } catch (e: Exception) {
            true
        }
        set(v) = sp.edit().putBoolean("overlay_visible", v).apply()

    var legalAcknowledged: Boolean
        get() = sp.getBoolean("legal_ok", false)
        set(v) = sp.edit().putBoolean("legal_ok", v).apply()

    /** Core option overrides, applied after every load, per option key. */
    var coreOptionOverrides: Map<String, String>
        get() = sp.getString("option_overrides", null)?.let { raw ->
            raw.split('\n').mapNotNull { line ->
                val i = line.indexOf('=')
                if (i <= 0) null else line.substring(0, i) to line.substring(i + 1)
            }.toMap()
        } ?: emptyMap()
        set(v) {
            val raw = v.entries.joinToString("\n") { "${it.key}=${it.value}" }
            sp.edit().putString("option_overrides", raw).apply()
        }

    fun renderSettings(): RenderSettings = RenderSettings(
        scaleMode = scaleMode,
        filterMode = filterMode,
        scanlinesPercent = scanlinesPercent,
        overscanCrop = overscanCrop,
        rotation = rotation,
    )

    /** Rotation is a *display* choice: a 90-degree turn is how you play a landscape
     *  game on a portrait phone without a stand. */
    fun setRotationDegrees(deg: Int) {
        rotation = when (deg) {
            90 -> 90
            180 -> 180
            else -> 0
        }
    }

    companion object {
        const val LAYOUT_LEFT = 0
        const val LAYOUT_RIGHT = 1
        const val LAYOUT_BOTH = 2
    }
}
