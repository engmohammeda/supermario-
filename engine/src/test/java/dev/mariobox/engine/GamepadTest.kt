package dev.mariobox.engine

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pad table is the cheapest place in the project to lose input: one wrong
 * constant and a whole control silently stops working on a device nobody is
 * debugging. These assertions are the device-free proof, and they are the reason
 * the mapping lives in :engine instead of inside a composable.
 */
class GamepadTest {
    @Test
    fun `bit order matches the retro joypad ids`() {
        // Bit i == RETRO_DEVICE_ID_JOYPAD_i, which mb_abi.h documents and the host's
        // input_state_cb indexes by. A reorder here would swap A and B on every pad.
        assertEquals(1 shl 0, Gamepad.B)
        assertEquals(1 shl 1, Gamepad.Y)
        assertEquals(1 shl 2, Gamepad.SELECT)
        assertEquals(1 shl 3, Gamepad.START)
        assertEquals(1 shl 4, Gamepad.UP)
        assertEquals(1 shl 5, Gamepad.DOWN)
        assertEquals(1 shl 6, Gamepad.LEFT)
        assertEquals(1 shl 7, Gamepad.RIGHT)
        assertEquals(1 shl 8, Gamepad.A)
        assertEquals(1 shl 9, Gamepad.X)
    }

    @Test
    fun `opposite directions on the same axis cancel`() {
        // A touch target that catches a sliding thumb twice must not read as
        // "holding left and right", which cores resolve by priority.
        val both = Gamepad.dpadMask(up = false, down = false, left = true, right = true)
        assertEquals(0, both)
        assertEquals(Gamepad.RIGHT, Gamepad.dpadMask(false, false, false, true))
        assertEquals(Gamepad.UP, Gamepad.dpadMask(true, true, false, false))
    }

    @Test
    fun `applyKey sets and clears one bit without disturbing the rest`() {
        val pressed = Gamepad.applyKey(0, Gamepad.A, true)
        assertEquals(Gamepad.A, pressed)
        assertEquals(0, Gamepad.applyKey(pressed, Gamepad.A, false))
        val two = Gamepad.applyKey(pressed, Gamepad.B, true)
        assertEquals(Gamepad.A or Gamepad.B, two)
        assertEquals(Gamepad.A, Gamepad.applyKey(two, Gamepad.B, false))
    }

    @Test
    fun `keyboard and gamepad codes map to pad bits`() {
        assertEquals(Gamepad.UP, Gamepad.keyToBit(KeyEvent.KEYCODE_DPAD_UP)!!)
        assertEquals(Gamepad.A, (Gamepad.keyToBit(KeyEvent.KEYCODE_BUTTON_A) ?: -1))
        assertEquals(Gamepad.B, (Gamepad.keyToBit(KeyEvent.KEYCODE_BUTTON_B) ?: -1))
        assertEquals(Gamepad.START, (Gamepad.keyToBit(KeyEvent.KEYCODE_ENTER) ?: -1))
        assertEquals(Gamepad.SELECT, (Gamepad.keyToBit(KeyEvent.KEYCODE_SHIFT_LEFT) ?: -1))
        assertEquals(Gamepad.LEFT, Gamepad.keyToBit(KeyEvent.KEYCODE_A))
        assertNull("unmapped keys must fall through to the system", Gamepad.keyToBit(KeyEvent.KEYCODE_VOLUME_UP))
    }

    @Test
    fun `axis deadzone is asymmetric so a drifting stick does not chatter`() {
        assertFalse(Gamepad.axisPressed(previous = false, value = 0.40f))
        assertTrue(Gamepad.axisPressed(previous = false, value = 0.70f))
        assertTrue(Gamepad.axisPressed(previous = true, value = 0.45f))
        assertFalse(Gamepad.axisPressed(previous = true, value = 0.20f))
        // A negative axis is the other direction, never "pressed" for this one.
        assertFalse(Gamepad.axisPressed(previous = true, value = -0.9f))
    }

    @Test
    fun `turbo alternates at about the requested rate`() {
        var on = 0
        for (f in 0 until 120) {
            val mask = Gamepad.turboFrame(held = 0, turbo = Gamepad.A, hz = 30, frame = f.toLong())
            if (mask and Gamepad.A != 0) on++
        }
        // 30 Hz of presses in 2 seconds of frames is "about 30", with the exact
        // split depending on the period rounding; what must never happen is 0 or 120.
        assertTrue("turbo produced $on presses", on in 20..60)
        assertEquals(Gamepad.A, Gamepad.turboFrame(Gamepad.A, 0, 30, 7))
    }
}
