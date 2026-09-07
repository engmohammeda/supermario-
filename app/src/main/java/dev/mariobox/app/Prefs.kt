package dev.mariobox.app

import android.content.Context
import android.content.SharedPreferences
import dev.mariobox.app.ui.ControlPlacement
import dev.mariobox.app.ui.PadAction
import dev.mariobox.app.ui.PadLayout
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

    /** View zoom factor (0.25..4); 1 = native fit. Part of the cheat features. */
    var zoom: Float
        get() = sp.getFloat("zoom", 1f)
        set(v) = sp.edit().putFloat("zoom", v.coerceIn(RenderSettings.ZOOM_MIN, RenderSettings.ZOOM_MAX)).apply()

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

    /**
     * Which overlay preset to draw; the custom editor maps this to a saved map.
     * Default is the professional classic pad (D-pad left, A/B right, START/SELECT
     * bottom-centre). A one-time migration moves installs that were silently
     * shipped the experimental "both/one-hand" default onto CLASSIC, because that
     * preset scattered buttons across the middle of the screen.
     */
    var overlayLayout: Int
        get() {
            val raw = try {
                sp.getInt("overlay_layout", LAYOUT_LEFT)
            } catch (e: Exception) {
                LAYOUT_LEFT
            }
            val migrated = sp.getBoolean("overlay_layout_v2", false)
            if (!migrated) {
                sp.edit().putBoolean("overlay_layout_v2", true).apply()
                // The old default index (2, "both") was a one-handed cluster; keep
                // an explicitly chosen mirror/custom, repair the shipped default.
                return if (raw == LAYOUT_BOTH) LAYOUT_LEFT else raw
            }
            return raw
        }
        set(v) = sp.edit().putInt("overlay_layout", v.coerceIn(0, 3)).apply()

    /** Global touch-control opacity, 0.2..1. */
    var overlayOpacity: Float
        get() = sp.getFloat("overlay_opacity", 0.85f)
        set(v) = sp.edit().putFloat("overlay_opacity", v.coerceIn(0.2f, 1f)).apply()

    /** Global touch-control size multiplier, 0.6..1.4. */
    var overlayScale: Float
        get() = sp.getFloat("overlay_scale", 1f)
        set(v) = sp.edit().putFloat("overlay_scale", v.coerceIn(0.6f, 1.4f)).apply()

    /** Custom per-button placements, serialised by [PadLayout]. */
    var customControls: String?
        get() = sp.getString("custom_controls", null)
        set(v) = sp.edit().putString("custom_controls", v?.takeIf { it.isNotBlank() }).apply()

    fun customControlMap(): Map<PadAction, ControlPlacement>? =
        PadLayout.decodeCustom(customControls)

    fun setCustomControlMap(map: Map<PadAction, ControlPlacement>) {
        customControls = PadLayout.encodeCustom(map)
    }

    /** The core's colour palette key (fceumm_next_palette); default = original PPU. */
    var palette: String
        get() = sp.getString("palette", DEFAULT_PALETTE) ?: DEFAULT_PALETTE
        set(v) = sp.edit().putString("palette", v.ifBlank { DEFAULT_PALETTE }).apply()

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
        zoom = zoom,
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
        const val LAYOUT_CUSTOM = 3

        /** FCEUmm's "Nintendo RGB PPU": the authentic NES PPU colours. */
        const val DEFAULT_PALETTE = "rgb"
    }
}
