package dev.mariobox.engine

/**
 * iNES / iNES 2.0 header reader.
 *
 * The library screen uses this for what it can only learn from the cartridge
 * itself: how big the program and character ROMs are, which mapper the board
 * needs, whether there is battery-backed RAM (so the save screen knows whether to
 * offer SRAM at all) and whether mirroring is horizontal or vertical (which is why
 * some SMB-level screenshots look wrong on a mis-detected board). It is pure
 * arithmetic on 16 bytes, so it is unit-tested here rather than on a device.
 *
 * Field layout is the standard one: `tools/` keeps the same reading for the probe
 * generator, and a header the core rejects is not our problem -- the core's own
 * parser is the authority for loading, this one is only for display and sorting.
 */
object INesHeader {
    const val MAGIC = 0x4E45531F // "NES" followed by 0x1A

    data class Info(
        val valid: Boolean,
        val prg16k: Int,
        val chr8k: Int,
        val mapper: Int,
        val mirroring: Mirroring,
        val battery: Boolean,
        val trainer: Boolean,
        val vram: Boolean,
        val ines2: Boolean,
        val consoleType: Int,
        val bytesReadable: Int,
    ) {
        /** ROM size implied by the header, in bytes. */
        val impliedSize: Long
            get() = 16L + 16384L * prg16k + 8192L * chr8k + (if (trainer) 512L else 0L)

        val summary: String
            get() = if (!valid) "iNES?" else buildString {
                append("mapper ").append(mapper)
                append(" · PRG ").append(prg16k * 16).append("K")
                if (chr8k > 0) append(" · CHR ").append(chr8k * 8).append("K") else append(" · CHR RAM")
                append(" · ").append(mirroring.label)
                if (battery) append(" · battery")
                if (trainer) append(" · trainer")
                if (ines2) append(" · iNES2")
            }
    }

    enum class Mirroring(val label: String) {
        HORIZONTAL("horizontal"),
        VERTICAL("vertical"),
        FOUR_SCREEN("4-screen"),
        UNKNOWN("unknown"),
    }

    fun parse(bytes: ByteArray): Info {
        if (bytes.size < 16) return invalid(bytes.size)
        val magic = (bytes[0].toInt() and 0xFF) or
            ((bytes[1].toInt() and 0xFF) shl 8) or
            ((bytes[2].toInt() and 0xFF) shl 16) or
            ((bytes[3].toInt() and 0xFF) shl 24)
        if (magic != MAGIC) return invalid(bytes.size)

        val prg = bytes[4].toInt() and 0xFF
        val chr = bytes[5].toInt() and 0xFF
        val flags6 = bytes[6].toInt() and 0xFF
        val flags7 = bytes[7].toInt() and 0xFF
        val flags8 = bytes[8].toInt() and 0xFF
        val flags9 = bytes[9].toInt() and 0xFF

        // Bits 0-1: lower nibble of the mapper (NES 2.0 adds the rest in byte 8).
        var mapper = (flags6 shr 4) or ((flags7 shr 4) shl 4)
        val fourScreen = (flags6 and 0x08) != 0
        val battery = (flags6 and 0x02) != 0
        val trainer = (flags6 and 0x04) != 0
        val mirroring = when {
            fourScreen -> Mirroring.FOUR_SCREEN
            (flags6 and 0x01) == 0 -> Mirroring.HORIZONTAL
            else -> Mirroring.VERTICAL
        }
        val ines2 = (flags7 and 0x0C) == 0x08

        // iNES 2.0 keeps the high nibble of the mapper in byte 8's low half and the
        // submapper in byte 9; classic iNES uses byte 8 for VS UniSystem/PlayChoice.
        if (ines2) {
            mapper = mapper or ((flags8 and 0x3F) shl 6)
        }
        val console = if (ines2) (flags9 shr 2) and 0x0F else 0

        // An empty CHR field with no CHR RAM bit set means "the header is lying";
        // report it as CHR RAM, because that is what cores assume and what a user
        // can act on (a 0x0000-byte CHR ROM file is a download problem, not a
        // detection problem).
        return Info(
            valid = true,
            prg16k = if (prg == 0) 2 else prg,
            chr8k = chr,
            mapper = mapper,
            mirroring = mirroring,
            battery = battery,
            trainer = trainer,
            vram = chr == 0,
            ines2 = ines2,
            consoleType = console,
            bytesReadable = bytes.size,
        )
    }

    /** Header plus the first byte of the real file, for "is this a 512-byte header-only file" checks. */
    private fun invalid(size: Int) = Info(
        valid = false,
        prg16k = 0,
        chr8k = 0,
        mapper = 0,
        mirroring = Mirroring.UNKNOWN,
        battery = false,
        trainer = false,
        vram = false,
        ines2 = false,
        consoleType = 0,
        bytesReadable = size,
    )
}
