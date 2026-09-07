package dev.mariobox.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The overlay's only promise is that a preset puts every control somewhere sane.
 * These are cheap to assert and cheap to break, since a preset is data and the data
 * is hand-tuned fractions.
 */
class PadLayoutTest {
    private val allActions = PadAction.entries.toList()

    /** Worst-case phone landscape: short edge is 56% of the long one. */
    private val shortOverLong = 0.5625f

    @Test
    fun `every preset places every control inside the view`() {
        for (preset in listOf(PadLayout.PRESET_CLASSIC, PadLayout.PRESET_MIRRORED, PadLayout.PRESET_ONE_HAND)) {
            val map = PadLayout.placements(preset)
            assertEquals("preset $preset must place all actions", allActions.size, map.size)
            for ((action, p) in map) {
                assertNotNull(p)
                assertTrue("$action cx out of range in $preset", p.cx in 0f..1f)
                assertTrue("$action cy out of range in $preset", p.cy in 0f..1f)
                assertTrue("$action width", p.w > 0f && p.w <= 1f)
                assertTrue("$action height", p.h > 0f && p.h <= 1f)
                // The centre plus half the extent must not leave the screen, where
                // sizes are short-edge fractions: w as a width fraction is
                // w * shortOverLong, h as a height fraction stays h.
                val halfW = p.w / 2f * shortOverLong
                val halfH = p.h / 2f
                assertTrue("$action spills right", p.cx + halfW <= 1.005f)
                assertTrue("$action spills left", p.cx - halfW >= -0.005f)
                assertTrue("$action spills bottom", p.cy + halfH <= 1.005f)
                assertTrue("$action spills top", p.cy - halfH >= -0.005f)
            }
        }
    }

    @Test
    fun `classic pad matches a real controller - dpad left buttons right start bottom`() {
        val map = PadLayout.placements(PadLayout.PRESET_CLASSIC)
        // D-pad in the left third.
        for (a in listOf(PadAction.UP, PadAction.DOWN, PadAction.LEFT, PadAction.RIGHT)) {
            assertTrue("$a must be on the left", map.getValue(a).cx < 0.30f)
        }
        // A/B on the right.
        for (a in listOf(PadAction.A, PadAction.B)) {
            assertTrue("$a must be on the right", map.getValue(a).cx > 0.70f)
        }
        // START/SELECT at the bottom, inside the central third.
        for (a in listOf(PadAction.START, PadAction.SELECT)) {
            val p = map.getValue(a)
            assertTrue("$a must be near the bottom", p.cy > 0.80f)
            assertTrue("$a must be centred", p.cx in 0.30f..0.70f)
        }
    }

    @Test
    fun `dpad arms overlap at the hub so diagonals have no dead zone`() {
        val map = PadLayout.placements(PadLayout.PRESET_CLASSIC)
        // Up and Left must each cover the hub point between them.
        val up = map.getValue(PadAction.UP)
        val left = map.getValue(PadAction.LEFT)
        // The point where the user presses up+left is between the two centres.
        val diagX = (up.cx + left.cx) / 2f
        val diagY = (up.cy + left.cy) / 2f
        // Up's box in fraction space (width/height axes):
        fun covers(p: Placement, x: Float, y: Float): Boolean {
            val halfW = p.w / 2f * shortOverLong
            val halfH = p.h / 2f
            return x >= p.cx - halfW && x <= p.cx + halfW &&
                y >= p.cy - halfH && y <= p.cy + halfH
        }
        assertTrue("up must cover the up-left diagonal", covers(up, diagX, diagY))
        assertTrue("left must cover the up-left diagonal", covers(left, diagX, diagY))
    }

    @Test
    fun `the mirrored preset is the exact mirror of the classic one`() {
        val base = PadLayout.placements(PadLayout.PRESET_CLASSIC)
        val mirrored = PadLayout.placements(PadLayout.PRESET_MIRRORED)
        for ((action, p) in base) {
            assertEquals("mirror of $action", (1f - p.cx).toDouble(), mirrored.getValue(action).cx.toDouble(), 0.0001)
        }
    }

    @Test
    fun `custom map round-trips through the codec`() {
        val custom = PadLayout.defaultCustom().mapValues { (_, p) ->
            p.copy(cx = 0.42f, cy = 0.61f, w = 0.17f, h = 0.13f, visible = false, opacity = 0.55f)
        }
        val decoded = PadLayout.decodeCustom(PadLayout.encodeCustom(custom))
        assertNotNull(decoded)
        val a = decoded!!.getValue(PadAction.A)
        assertEquals(0.42f, a.cx, 0.0001f)
        assertEquals(0.61f, a.cy, 0.0001f)
        assertEquals(0.17f, a.w, 0.0001f)
        assertEquals(0.13f, a.h, 0.0001f)
        assertEquals(false, a.visible)
        assertEquals(0.55f, a.opacity, 0.0001f)
        assertEquals(custom.size, decoded.size)
    }

    @Test
    fun `hidden custom controls are not rendered and missing ones fall back`() {
        val custom = PadLayout.defaultCustom().toMutableMap()
        custom[PadAction.UP] = custom.getValue(PadAction.UP).copy(visible = false)
        custom.remove(PadAction.REWIND)
        val map = PadLayout.placements(PadLayout.PRESET_CUSTOM, custom)
        assertTrue("hidden UP must not render", PadAction.UP !in map)
        assertTrue("REWIND falls back to its classic spot", PadAction.REWIND in map)
    }

    @Test
    fun `custom preset without saved data falls back to classic`() {
        val map = PadLayout.placements(PadLayout.PRESET_CUSTOM, null)
        assertEquals(allActions.size, map.size)
    }

    @Test
    fun `utility buttons stay clear of the hidden top chrome`() {
        val map = PadLayout.placements(PadLayout.PRESET_CLASSIC)
        // The quick-save button may sit near the top; rewind/FF live at the bottom
        // edges so a thumb never hunts for them.
        assertTrue(map.getValue(PadAction.REWIND).cy > 0.85f)
        assertTrue(map.getValue(PadAction.FAST_FWD).cy > 0.85f)
    }

    @Test
    fun `clamp keeps a dragged control fully on screen`() {
        // A huge button dragged into the corner must be pulled back inside.
        val p = ControlPlacement(cx = 0f, cy = 0f, w = 0.30f, h = 0.30f)
        val clamped = PadLayout.clamp(p, 0.5625f)
        val halfW = 0.30f / 2f * 0.5625f
        assertTrue(clamped.cx >= halfW - 1e-4f)
        assertTrue(clamped.cy >= 0.30f / 2f - 1e-4f)
    }

    @Test
    fun `pad actions map to pad bits and the rest do not`() {
        for (a in listOf(PadAction.UP, PadAction.DOWN, PadAction.LEFT, PadAction.RIGHT, PadAction.A, PadAction.B, PadAction.START, PadAction.SELECT)) {
            assertTrue("$a needs a bit", PadLayout.bitFor(a) != 0)
        }
        for (a in listOf(PadAction.REWIND, PadAction.QUICK, PadAction.FAST_FWD)) {
            assertEquals("$a is an action, not a pad bit", 0, PadLayout.bitFor(a))
        }
        assertEquals(dev.mariobox.engine.Gamepad.A, PadLayout.turboBitFor(PadAction.TURBO_A))
    }
}
