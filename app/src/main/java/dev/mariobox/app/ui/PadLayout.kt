package dev.mariobox.app.ui

import dev.mariobox.engine.Gamepad
import kotlin.math.min

/** Everything the overlay can draw: pad buttons plus the host actions beside them. */
enum class PadAction {
    UP, DOWN, LEFT, RIGHT, A, B, START, SELECT, TURBO_A, TURBO_B, REWIND, QUICK, FAST_FWD;

    /** Human-readable Arabic label used by the editor list. */
    val editorLabel: String
        get() = when (this) {
            UP -> "▲ لأعلى"
            DOWN -> "▼ لأسفل"
            LEFT -> "◀ يسار"
            RIGHT -> "▶ يمين"
            A -> "A"
            B -> "B"
            START -> "بدء (Start)"
            SELECT -> "اختيار (Select)"
            TURBO_A -> "A⚡ تربو"
            TURBO_B -> "B⚡ تربو"
            REWIND -> "⏪ رجوع بالزمن"
            QUICK -> "💾 حفظ سريع"
            FAST_FWD -> "⏩ تسريع"
        }
}

/**
 * A control placement in fractions of the view it is laid out in.
 *
 * `cx`/`cy` are the centre of the target as fractions of the view **width/height**.
 * `w`/`h` are the button size as fractions of the view's **short edge** (height in
 * landscape, width in portrait). Short-edge units are the whole point: a circular
 * button specified as `w == h` is then a perfect circle on every phone and tablet,
 * and the same data survives a rotation.
 */
data class Placement(val cx: Float, val cy: Float, val w: Float, val h: Float)

/**
 * One editable control from the custom-layout editor. This is what gets serialised:
 * [visible] hides a button without deleting it, [opacity] is 0.2..1.
 */
data class ControlPlacement(
    val cx: Float,
    val cy: Float,
    val w: Float,
    val h: Float,
    val visible: Boolean = true,
    val opacity: Float = 1f,
) {
    fun render(): Placement = Placement(cx, cy, w, h)
}

object PadLayout {
    const val PRESET_CLASSIC = 0
    const val PRESET_MIRRORED = 1
    const val PRESET_ONE_HAND = 2
    const val PRESET_CUSTOM = 3

    /* Sizes are short-edge fractions; 0.30 of the short edge is already a thumb-wide
     * target, and the clamps keep a dragged button pressable. */
    const val MIN_W = 0.05f
    const val MAX_W = 0.34f
    const val MIN_H = 0.05f
    const val MAX_H = 0.34f

    /**
     * Professional gamepad layout, landscape.
     *
     * Geometry (short-edge units), tuned so a thumb rests exactly where it would on
     * a real NES/SNES pad:
     *   • D-pad: a plus sign on the lower-left. The four arms overlap around the
     *     hub, so diagonals (up+left etc.) press two bits at once with no dead zone.
     *   • A / B: two round face buttons on the lower-right, A high-right of B
     *     (the classic diamond the thumb rocks across).
     *   • Turbo A/B: small pills just above the face buttons.
     *   • SELECT / START: two small pills side by side, bottom-centre.
     *   • Rewind / fast-forward / quick-save: small utility buttons tucked at the
     *     edges, clear of the gamepad itself.
     */
    /*
     * D-pad construction. The four arms are square touch targets of edge `a`
     * (fraction of the short edge), centred on a plus whose hub is (xc, yc). Arm
     * centres sit 0.42·a from the hub (not 1·a as in a naïve 3×3 grid): each arm
     * then reaches PAST the hub, so every diagonal is two overlapping buttons and
     * the pivot itself presses all four directions' neighbours — no dead zone.
     */
    private const val DPAD_ARM = 0.130f
    private const val HUB_OFFSET = 0.42f       // arm-centre offset, short-edge units
    private const val HUB_OFFSET_X = HUB_OFFSET * 0.5625f // cx fraction (SOL in landscape)

    private val classic: Map<PadAction, Placement> = mapOf(
        // --- D-pad (left), hub around (0.094, 0.724) -------------------------
        PadAction.UP to Placement(0.094f, 0.724f - DPAD_ARM * HUB_OFFSET, DPAD_ARM, DPAD_ARM),
        PadAction.DOWN to Placement(0.094f, 0.724f + DPAD_ARM * HUB_OFFSET, DPAD_ARM, DPAD_ARM),
        PadAction.LEFT to Placement(0.094f - DPAD_ARM * HUB_OFFSET_X, 0.724f, DPAD_ARM, DPAD_ARM),
        PadAction.RIGHT to Placement(0.094f + DPAD_ARM * HUB_OFFSET_X, 0.724f, DPAD_ARM, DPAD_ARM),

        // --- Face buttons (right), NES diamond: B low-left, A high-right ------
        PadAction.B to Placement(0.866f, 0.760f, 0.145f, 0.145f),
        PadAction.A to Placement(0.948f, 0.636f, 0.145f, 0.145f),

        // --- Turbo pills above the face buttons ------------------------------
        PadAction.TURBO_B to Placement(0.805f, 0.470f, 0.085f, 0.075f),
        PadAction.TURBO_A to Placement(0.915f, 0.470f, 0.085f, 0.075f),

        // --- SELECT / START, bottom-centre like a real pad -------------------
        PadAction.SELECT to Placement(0.430f, 0.900f, 0.115f, 0.060f),
        PadAction.START to Placement(0.585f, 0.900f, 0.115f, 0.060f),

        // --- Utility: rewind & fast-forward at the lower edges, quick-save top
        PadAction.REWIND to Placement(0.030f, 0.940f, 0.075f, 0.075f),
        PadAction.FAST_FWD to Placement(0.970f, 0.940f, 0.075f, 0.075f),
        PadAction.QUICK to Placement(0.500f, 0.135f, 0.080f, 0.075f),
    )

    /** The editable base map, one [ControlPlacement] per action. */
    fun defaultCustom(): Map<PadAction, ControlPlacement> = classic.mapValues { (_, p) ->
        ControlPlacement(p.cx, p.cy, p.w, p.h)
    }

    fun placements(preset: Int): Map<PadAction, Placement> = placements(preset, null)

    /** Resolves a preset (or the custom map) into renderable placements. */
    fun placements(preset: Int, custom: Map<PadAction, ControlPlacement>?): Map<PadAction, Placement> {
        val base = presetPlacements(preset)
        if (preset != PRESET_CUSTOM || custom == null) return base
        // A custom map may be missing an action that a future build adds: fall back
        // to the classic spot for it rather than silently dropping the control.
        return PadAction.entries.mapNotNull { action ->
            val p = custom[action] ?: return@mapNotNull classic[action]?.let { c ->
                action to c
            }
            if (!p.visible) null else action to p.render()
        }.toMap()
    }

    private fun presetPlacements(preset: Int): Map<PadAction, Placement> = when (preset) {
        /* Left-handed play: the whole surface mirrors horizontally, so the pad
         * ends up under the right thumb and the face buttons under the left. */
        PRESET_MIRRORED -> classic.mapValues { (_, p) -> p.copy(cx = 1f - p.cx) }

        /* One-handed (right thumb) portrait/landscape play: everything clusters in
         * the right half with the D-pad below the face buttons, so one thumb sweeps
         * the whole controller without leaving the screen edge. */
        PRESET_ONE_HAND -> {
            // Right-thumb-only cluster, landscape: face buttons high-right,
            // a compact plus-shaped D-pad below them, START/SELECT pushed toward
            // the centre where a thumb sweep still reaches them.
            val hubX = 0.820f
            val hubY = 0.780f
            val ox = DPAD_ARM * HUB_OFFSET_X
            val oy = DPAD_ARM * HUB_OFFSET
            classic.mapValues { (k, p) ->
                val x = when (k) {
                    PadAction.UP -> hubX
                    PadAction.DOWN -> hubX
                    PadAction.LEFT -> hubX - ox
                    PadAction.RIGHT -> hubX + ox
                    PadAction.B -> 0.885f
                    PadAction.A -> 0.952f
                    PadAction.TURBO_B -> 0.815f
                    PadAction.TURBO_A -> 0.915f
                    PadAction.SELECT -> 0.575f
                    PadAction.START -> 0.700f
                    PadAction.REWIND -> 0.500f
                    PadAction.FAST_FWD -> 0.970f
                    PadAction.QUICK -> 0.380f
                    else -> p.cx
                }
                val y = when (k) {
                    PadAction.UP -> hubY - oy
                    PadAction.LEFT, PadAction.RIGHT -> hubY
                    PadAction.DOWN -> hubY + oy
                    PadAction.B -> 0.520f
                    PadAction.A -> 0.390f
                    PadAction.TURBO_B, PadAction.TURBO_A -> 0.270f
                    else -> p.cy
                }
                p.copy(cx = x, cy = y)
            }
        }
        else -> classic
    }

    /* ------------------------------------------------------------ serialisation
     * Pipe-separated rows keep the codec pure Kotlin (no org.json on the JVM test
     * classpath) and human-diffable: `A|0.93|0.58|0.14|0.14|1|0.90`.
     */
    fun encodeCustom(map: Map<PadAction, ControlPlacement>): String =
        CUSTOM_V2 + "\n" + PadAction.entries.mapNotNull { action -> map[action]?.let { action to it } }
            .joinToString("\n") { (action, p) ->
                "${action.name}|${p.cx}|${p.cy}|${p.w}|${p.h}|${if (p.visible) 1 else 0}|${p.opacity}"
            }

    /** Version marker for the serialised map; v2 switched sizes to short-edge units. */
    private const val CUSTOM_V2 = "v2"

    fun decodeCustom(raw: String?): Map<PadAction, ControlPlacement>? {
        if (raw.isNullOrBlank()) return null
        // Maps written by older builds used width/height-fraction sizes, which
        // draw as wrong-sized buttons under the new short-edge geometry; start
        // those users from the professional default instead of a mis-rendered map.
        if (!raw.trimStart().startsWith(CUSTOM_V2)) return null
        val out = mutableMapOf<PadAction, ControlPlacement>()
        for (line in raw.split('\n')) {
            if (line.startsWith(CUSTOM_V2)) continue
            val parts = line.split('|')
            if (parts.size < 7) continue
            val action = runCatching { PadAction.valueOf(parts[0]) }.getOrNull() ?: continue
            val cx = parts[1].toFloatOrNull() ?: continue
            val cy = parts[2].toFloatOrNull() ?: continue
            val w = (parts[3].toFloatOrNull() ?: continue).coerceIn(MIN_W, MAX_W)
            val h = (parts[4].toFloatOrNull() ?: continue).coerceIn(MIN_H, MAX_H)
            val visible = parts[5] == "1"
            val opacity = (parts[6].toFloatOrNull() ?: 1f).coerceIn(0.2f, 1f)
            out[action] = ControlPlacement(
                cx.coerceIn(0f, 1f), cy.coerceIn(0f, 1f), w, h, visible, opacity
            )
        }
        return out.ifEmpty { null }
    }

    /**
     * Clamps a placement so the control never leaves the screen.
     *
     * `cx` is a width fraction but the button width is a short-edge fraction, so the
     * centre must stay inside `w/2 * (short/long)` of the edge; same for `cy`.
     */
    fun clamp(p: ControlPlacement, aspect: Float = 1f): ControlPlacement {
        // aspect = short/long of the view; <= 1.
        val a = aspect.coerceIn(0.4f, 1f)
        val halfW = (p.w / 2f) * a       // button half-width as a width fraction
        val halfH = (p.h / 2f)           // button half-height as a height fraction
        return ControlPlacement(
            cx = p.cx.coerceIn(halfW, 1f - halfW),
            cy = p.cy.coerceIn(halfH, 1f - halfH),
            w = p.w.coerceIn(MIN_W, MAX_W),
            h = p.h.coerceIn(MIN_H, MAX_H),
            visible = p.visible,
            opacity = p.opacity.coerceIn(0.2f, 1f),
        )
    }

    /** short/long ratio of a view given in px/dp; used by [clamp] and the editor. */
    fun shortOverLong(viewW: Float, viewH: Float): Float {
        if (viewW <= 0f || viewH <= 0f) return 1f
        return min(viewW, viewH) / maxOf(viewW, viewH)
    }

    /** The pad bit this action drives; 0 for actions that are not pad bits. */
    fun bitFor(action: PadAction): Int = when (action) {
        PadAction.UP -> Gamepad.UP
        PadAction.DOWN -> Gamepad.DOWN
        PadAction.LEFT -> Gamepad.LEFT
        PadAction.RIGHT -> Gamepad.RIGHT
        PadAction.A -> Gamepad.A
        PadAction.B -> Gamepad.B
        PadAction.START -> Gamepad.START
        PadAction.SELECT -> Gamepad.SELECT
        else -> 0
    }

    /** Turbo actions hold the underlying bit while repeating it at the user's Hz. */
    fun turboBitFor(action: PadAction): Int = when (action) {
        PadAction.TURBO_A -> Gamepad.A
        PadAction.TURBO_B -> Gamepad.B
        else -> 0
    }
}
