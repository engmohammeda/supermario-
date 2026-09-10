package dev.mariobox.app.ui

import dev.mariobox.engine.Gamepad

/** Everything the overlay can draw: pad buttons plus the host actions beside them. */
enum class PadAction {
    UP, DOWN, LEFT, RIGHT, A, B, START, SELECT, TURBO_A, TURBO_B, REWIND, QUICK, FAST_FWD;

    /** Human-readable Arabic label used by the editor list. */
    val editorLabel: String
        get() = when (this) {
            UP -> "لأعلى"
            DOWN -> "لأسفل"
            LEFT -> "يسار"
            RIGHT -> "يمين"
            A -> "A"
            B -> "B"
            START -> "بدء"
            SELECT -> "اختيار"
            TURBO_A -> "تربو A"
            TURBO_B -> "تربو B"
            REWIND -> "رجوع"
            QUICK -> "حفظ سريع"
            FAST_FWD -> "تسريع"
        }
}

/**
 * A control placement in fractions of the view it is laid out in: `cx`/`cy` are the
 * centre of the target, `w`/`h` its size.
 *
 * Fractions rather than dp because the same layout has to survive a 360dp phone, an
 * 800dp tablet and a rotation; and because "preset -> map" is a pure function a unit
 * test can pin down, which a column of hardcoded modifiers is not.
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

    const val MIN_W = 0.04f
    const val MAX_W = 0.30f
    const val MIN_H = 0.04f
    const val MAX_H = 0.30f

    /** Classic pad-on-the-left layout. The other two are derived from it. */
    private val classic: Map<PadAction, Placement> = mapOf(
        PadAction.UP to Placement(0.15f, 0.63f, 0.08f, 0.12f),
        PadAction.DOWN to Placement(0.15f, 0.87f, 0.08f, 0.12f),
        PadAction.LEFT to Placement(0.08f, 0.75f, 0.06f, 0.15f),
        PadAction.RIGHT to Placement(0.22f, 0.75f, 0.06f, 0.15f),
        PadAction.B to Placement(0.77f, 0.85f, 0.14f, 0.14f),
        PadAction.A to Placement(0.91f, 0.65f, 0.14f, 0.14f),
        PadAction.START to Placement(0.57f, 0.85f, 0.12f, 0.08f),
        PadAction.SELECT to Placement(0.43f, 0.85f, 0.12f, 0.08f),
        PadAction.TURBO_A to Placement(0.91f, 0.45f, 0.09f, 0.07f),
        PadAction.TURBO_B to Placement(0.77f, 0.65f, 0.09f, 0.07f),
        PadAction.REWIND to Placement(0.65f, 0.14f, 0.10f, 0.08f),
        PadAction.QUICK to Placement(0.50f, 0.14f, 0.10f, 0.08f),
        PadAction.FAST_FWD to Placement(0.35f, 0.14f, 0.10f, 0.08f),
    )

    /** The editable base map, one {@link ControlPlacement} per action. */
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
        /* Left-handed play: the whole surface mirrors, so the pad ends up under the
         * right thumb and the face buttons under the left. */
        PRESET_MIRRORED -> classic.mapValues { (_, p) -> p.copy(cx = 1f - p.cx) }
        /* One-handed portrait play: pad and buttons both in the right half, stacked
         * so a thumb sweeps between them without leaving the screen edge. */
        PRESET_ONE_HAND -> classic.mapValues { (k, p) ->
            val x = when (k) {
                PadAction.UP -> 0.65f
                PadAction.DOWN -> 0.65f
                PadAction.LEFT -> 0.55f
                PadAction.RIGHT -> 0.75f
                PadAction.A -> 0.95f
                PadAction.B -> 0.85f
                else -> p.cx
            }
            val y = when (k) {
                PadAction.UP -> 0.60f
                PadAction.DOWN -> 0.84f
                PadAction.LEFT, PadAction.RIGHT -> 0.72f
                PadAction.A -> 0.68f
                PadAction.B -> 0.80f
                else -> p.cy
            }
            p.copy(cx = x, cy = y)
        }
        else -> classic
    }

    /* ------------------------------------------------------------ serialisation
     * Pipe-separated rows keep the codec pure Kotlin (no org.json on the JVM test
     * classpath) and human-diffable: `A|0.93|0.58|0.14|0.14|1|0.90`.
     */
    fun encodeCustom(map: Map<PadAction, ControlPlacement>): String =
        PadAction.entries.mapNotNull { action -> map[action]?.let { action to it } }
            .joinToString("\n") { (action, p) ->
                "${action.name}|${p.cx}|${p.cy}|${p.w}|${p.h}|${if (p.visible) 1 else 0}|${p.opacity}"
            }

    fun decodeCustom(raw: String?): Map<PadAction, ControlPlacement>? {
        if (raw.isNullOrBlank()) return null
        val out = mutableMapOf<PadAction, ControlPlacement>()
        for (line in raw.split('\n')) {
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

    /** Clamps a placement so the control never leaves the screen. */
    fun clamp(p: ControlPlacement): ControlPlacement = ControlPlacement(
        cx = p.cx.coerceIn(p.w / 2f, 1f - p.w / 2f),
        cy = p.cy.coerceIn(p.h / 2f, 1f - p.h / 2f),
        w = p.w.coerceIn(MIN_W, MAX_W),
        h = p.h.coerceIn(MIN_H, MAX_H),
        visible = p.visible,
        opacity = p.opacity.coerceIn(0.2f, 1f),
    )

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
