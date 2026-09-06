package dev.mariobox.engine

import android.view.KeyEvent

/**
 * The NES pad, as the host and as the app.
 *
 * [bit] values are `RETRO_DEVICE_ID_JOYPAD_*` positions, which is also the mask
 * layout `mb_set_input` expects. Everything that translates a device event into
 * that mask lives here so the mapping is unit-testable (`InputMapTest`) instead of
 * being discovered on a phone: the difference between "Start did nothing" and a
 * correct emulator is a table, and tables are cheap to test.
 */
object Gamepad {
    const val B = 1 shl 0
    const val Y = 1 shl 1
    const val SELECT = 1 shl 2
    const val START = 1 shl 3
    const val UP = 1 shl 4
    const val DOWN = 1 shl 5
    const val LEFT = 1 shl 6
    const val RIGHT = 1 shl 7
    const val A = 1 shl 8
    const val X = 1 shl 9
    const val L = 1 shl 10
    const val R = 1 shl 11

    /** Order used by the overlay editor and the remap screen. */
    val buttons = listOf(
        "B" to B, "Y" to Y, "SELECT" to SELECT, "START" to START,
        "UP" to UP, "DOWN" to DOWN, "LEFT" to LEFT, "RIGHT" to RIGHT,
        "A" to A, "X" to X, "L" to L, "R" to R,
    )

    /** D-pad as the touch layer produces it: four independent directions. */
    fun dpadMask(up: Boolean, down: Boolean, left: Boolean, right: Boolean): Int {
        var m = 0
        if (up) m = m or UP
        // Opposite directions on the same axis cancel: a core that saw both would
        // resolve it by priority and a stuck touch target reads exactly like that.
        if (down && !up) m = m or DOWN
        if (left && !right) m = m or LEFT
        if (right && !left) m = m or RIGHT
        return m
    }

    /**
     * Hardware/gamepad key to a pad bit. Returns null for keys the NES has no
     * equivalent for, which the caller must treat as "not handled" so the IME and
     * the volume keys keep working.
     */
    fun keyToBit(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> UP
        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> DOWN
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> RIGHT
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_SPACE -> A
        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_X -> B
        KeyEvent.KEYCODE_BUTTON_Y -> Y
        KeyEvent.KEYCODE_BUTTON_X -> X
        KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_Q -> L
        KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_E -> R
        // ENTER is the conventional "start" on a keyboard; MENU is the TV remote key.
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_MENU,
        KeyEvent.KEYCODE_BUTTON_START -> START
        // SHIFT is the usual "select" on a keyboard; back is what a remote user hits.
        KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT, KeyEvent.KEYCODE_BUTTON_SELECT -> SELECT
        else -> null
    }

    /**
     * True when an axis value should count as a direction press.
     *
     * The deadzone is asymmetric on purpose: it takes 0.55 of full deflection to
     * press and 0.35 to release, which is what stops a slightly drift-prone
     * Bluetooth stick from chattering at 60 Hz.
     */
    fun axisPressed(previous: Boolean, value: Float, pressAt: Float = 0.55f, releaseAt: Float = 0.35f): Boolean {
        val v = if (value < 0) -value else value
        val positive = value > 0
        return if (previous) positive && v > releaseAt else positive && v > pressAt
    }

    /**
     * Sets or clears one bit of a held mask. Both the touch layer and the key
     * handler keep their own mask and the engine gets the union, so a physical
     * gamepad and an on-screen button can press the same bit without either one
     * forgetting it was down.
     */
    fun applyKey(mask: Int, bit: Int, down: Boolean): Int =
        if (down) mask or bit else mask and bit.inv()

    /** Turbo: how the held mask is chopped into frames at [hz] for a 60 fps core. */
    fun turboFrame(held: Int, turbo: Int, hz: Int, frame: Long): Int {
        if (turbo == 0) return held
        val rate = hz.coerceIn(1, 60)
        val period = (120 / rate).coerceAtLeast(2)
        val on = (frame % period) < (period / 2)
        return if (on) held or turbo else held and turbo.inv()
    }
}
