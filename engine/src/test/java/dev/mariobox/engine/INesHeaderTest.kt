package dev.mariobox.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Header parsing is the library screen's whole idea of a game, so the fixtures here
 * are byte-exact cases the field has: NROM, a battery-backed MMC3, an iNES 2.0
 * expansion, a 4-screen board, and the two ways a file fails to be a ROM at all.
 */
class INesHeaderTest {

    private fun header(
        prg: Int,
        chr: Int,
        flags6: Int,
        flags7: Int,
        flags8: Int = 0,
        flags9: Int = 0,
    ): ByteArray = byteArrayOf(
        0x4E, 0x45, 0x53, 0x1A, // "NES" + EOF
        prg.toByte(), chr.toByte(), flags6.toByte(), flags7.toByte(),
        flags8.toByte(), flags9.toByte(), 0, 0, 0, 0, 0, 0,
    )

    @Test
    fun `rejects anything that is not a nes header`() {
        assertFalse(INesHeader.parse(ByteArray(0)).valid)
        assertFalse(INesHeader.parse(ByteArray(16)).valid)
        assertFalse(INesHeader.parse(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)).valid)
    }

    @Test
    fun `reads a plain nrom cartridge`() {
        val i = INesHeader.parse(header(prg = 2, chr = 1, flags6 = 0x01, flags7 = 0x00))
        assertTrue(i.valid)
        assertEquals(2, i.prg16k)
        assertEquals(1, i.chr8k)
        assertEquals(0, i.mapper)
        assertEquals(INesHeader.Mirroring.VERTICAL, i.mirroring)
        assertFalse(i.battery)
        // 16 + 32K + 8K
        assertEquals(16L + 32768L + 8192L, i.impliedSize)
    }

    @Test
    fun `mapper number comes from both nibbles`() {
        // MMC3 (4): low nibble in byte 6, high nibble in byte 7.
        val mmc3 = INesHeader.parse(header(32, 2, 0x42, 0x00))
        assertEquals(4, mmc3.mapper)
        assertTrue(mmc3.battery)
        // Mapper 157: low nibble 0xD in byte 6's high half, high nibble 9 in byte 7's.
        val m157 = INesHeader.parse(header(16, 0, 0xD0, 0x90))
        assertEquals(157, m157.mapper)
    }

    @Test
    fun `ines two extends the mapper with byte eight`() {
        // iNES 2.0 marker is bits 2-3 of byte 7 == 0b10; mapper 52 = 4 | (3 << 4)?
        val i = INesHeader.parse(header(16, 4, 0x40, 0x08, flags8 = 0x03))
        assertTrue(i.ines2)
        assertEquals(4 or (3 shl 6), i.mapper)
    }

    @Test
    fun `four screen mirroring wins over the horizontal bit`() {
        val i = INesHeader.parse(header(16, 0, 0x09, 0x00))
        assertEquals(INesHeader.Mirroring.FOUR_SCREEN, i.mirroring)
        assertTrue(i.vram)
    }

    @Test
    fun `a trainer adds its 512 bytes to the implied size`() {
        val i = INesHeader.parse(header(2, 1, 0x06, 0x00))
        assertTrue(i.trainer)
        assertEquals(16L + 32768L + 8192L + 512L, i.impliedSize)
    }

    @Test
    fun `summary mentions the things a player asks about`() {
        val i = INesHeader.parse(header(32, 2, 0x42, 0x00))
        val text = i.summary
        assertTrue(text.contains("mapper 4"))
        assertTrue(text.contains("battery"))
        assertTrue(text.contains("horizontal"))
    }
}
