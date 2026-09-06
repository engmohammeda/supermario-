package dev.mariobox.app.ui

import dev.mariobox.engine.Gamepad

/** Everything the overlay can draw: pad buttons plus the host actions beside them. */
enum class PadAction {
    UP, DOWN, LEFT, RIGHT, A, B, START, SELECT, TURBO_A, TURBO_B, REWIND, QUICK, FAST_FWD
}

/**
 * A control placement in fractions of the view it is laid out in: `cx`/`cy` are the
 * centre of the target, `w`/`h` its size.
 *
 * Fractions rather than dp because the same layout has to survive a 360dp phone, an
 * 800dp tablet and a rotation; and because "preset -> map" is a pure function a unit
 * test can pin down, which a column of hardcoded modifiers is not. The full drag
 * editor (docs/PLAN.md §5.3) replaces the three presets, not this shape.
 */
data class Placement(val cx: Float, val cy: Float, val w: Float, val h: Float)

object PadLayout {
    const val PRESET_CLASSIC = 0
    const val PRESET_MIRRORED = 1
    const val PRESET_ONE_HAND = 2

    /** Classic pad-on-the-left layout. The other two are derived from it. */
    private val classic: Map<PadAction, Placement> = mapOf(
        PadAction.UP to Placement(0.14f, 0.52f, 0.14f, 0.12f),
        PadAction.DOWN to Placement(0.14f, 0.76f, 0.14f, 0.12f),
        PadAction.LEFT to Placement(0.07f, 0.64f, 0.14f, 0.12f),
        PadAction.RIGHT to Placement(0.23f, 0.64f, 0.14f, 0.12f),
        PadAction.B to Placement(0.83f, 0.74f, 0.14f, 0.14f),
        PadAction.A to Placement(0.93f, 0.58f, 0.14f, 0.14f),
        PadAction.START to Placement(0.62f, 0.06f, 0.12f, 0.08f),
        PadAction.SELECT to Placement(0.50f, 0.06f, 0.12f, 0.08f),
        PadAction.TURBO_A to Placement(0.78f, 0.42f, 0.10f, 0.08f),
        PadAction.TURBO_B to Placement(0.66f, 0.42f, 0.10f, 0.08f),
        PadAction.REWIND to Placement(0.38f, 0.20f, 0.10f, 0.08f),
        PadAction.QUICK to Placement(0.27f, 0.20f, 0.10f, 0.08f),
        PadAction.FAST_FWD to Placement(0.16f, 0.20f, 0.10f, 0.08f),
    )

    fun placements(preset: Int): Map<PadAction, Placement> = when (preset) {
        /* Left-handed play: the whole surface mirrors, so the pad ends up under the
         * right thumb and the face buttons under the left. */
        PRESET_MIRRORED -> classic.mapValues { (_, p) -> p.copy(cx = 1f - p.cx) }
        /* One-handed portrait play: pad and buttons both in the right half, stacked
         * so a thumb sweeps between them without leaving the screen edge. */
        PRESET_ONE_HAND -> classic.mapValues { (k, p) ->
            val x = when (k) {
                PadAction.UP -> 0.60f
                PadAction.DOWN -> 0.60f
                PadAction.LEFT -> 0.51f
                PadAction.RIGHT -> 0.69f
                PadAction.A -> 0.90f
                PadAction.B -> 0.78f
                else -> p.cx
            }
            val y = when (k) {
                PadAction.UP, PadAction.A -> 0.52f
                PadAction.DOWN, PadAction.B -> 0.72f
                PadAction.LEFT, PadAction.RIGHT -> 0.62f
                else -> p.cy
            }
            p.copy(cx = x, cy = y)
        }
        else -> classic
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

