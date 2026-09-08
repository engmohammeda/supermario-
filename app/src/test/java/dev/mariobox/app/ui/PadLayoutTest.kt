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
                // The centre plus half the extent must not leave the screen: a control
                // clipped off an edge is a control the user cannot press.
                assertTrue("$action spills right", p.cx + p.w / 2f <= 1.0001f)
                assertTrue("$action spills left", p.cx - p.w / 2f >= -0.0001f)
                assertTrue("$action spills bottom", p.cy + p.h / 2f <= 1.0001f)
            }
        }
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
    fun `momentary actions stay below the chrome strip`() {
        val map = PadLayout.placements(PadLayout.PRESET_CLASSIC)
        for (a in listOf(PadAction.REWIND, PadAction.QUICK, PadAction.FAST_FWD)) {
            assertTrue("$a must not sit under the top bar", map.getValue(a).cy >= 0.12f)
        }
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
