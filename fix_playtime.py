import re

with open("app/src/main/java/dev/mariobox/app/Prefs.kt", "r") as f:
    content = f.read()

target = """    /** Per-cartridge save directory: SRAM, slots and thumbnails all live here. */
    fun saveDirFor(rom: File): File = File(savesDir, rom.nameWithoutExtension).apply { mkdirs() }"""
replacement = """    /** Per-cartridge save directory: SRAM, slots and thumbnails all live here. */
    fun saveDirFor(rom: File): File = File(savesDir, rom.nameWithoutExtension).apply { mkdirs() }

    /** Add elapsed playtime in seconds for a specific ROM. */
    fun addPlaytime(romName: String, seconds: Long) {
        val current = sp.getLong("playtime_$romName", 0L)
        sp.edit().putLong("playtime_$romName", current + seconds).apply()
    }

    /** Get total playtime in seconds for a specific ROM. */
    fun getPlaytime(romName: String): Long {
        return sp.getLong("playtime_$romName", 0L)
    }"""
content = content.replace(target, replacement)
with open("app/src/main/java/dev/mariobox/app/Prefs.kt", "w") as f:
    f.write(content)
