import re

with open("app/src/main/java/dev/mariobox/app/EmulatorViewModel.kt", "r") as f:
    content = f.read()

target1 = """    private val slotCount = 10"""
replacement1 = """    private val slotCount = 11"""
if target1 in content:
    content = content.replace(target1, replacement1)

target2 = """    private fun slotName(index: Int) = if (index == QUICK) "quick.mbs" else "slot%02d.mbs".format(index)"""
replacement2 = """    private fun slotName(index: Int) = when (index) {
        QUICK -> "quick.mbs"
        AUTOSAVE -> "autosave.mbs"
        else -> "slot%02d.mbs".format(index)
    }"""
if target2 in content:
    content = content.replace(target2, replacement2)

target3 = """        const val QUICK = 10
    }"""
replacement3 = """        const val QUICK = 10
        const val AUTOSAVE = 11
    }"""
if target3 in content:
    content = content.replace(target3, replacement3)

target4 = """    init {
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                if (engine.active) {
                    runCatching { engine.flushBattery() }
                }
            }
        }
    }"""
replacement4 = """    init {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(1000)
                if (engine.active) {
                    runCatching { engine.flushBattery() }
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(120_000L) // 2 minutes auto-save
                if (engine.active && !_paused) {
                    val rc = engine.saveState(slotFile(AUTOSAVE).absolutePath)
                    if (rc == MbStatus.OK) {
                        val bmp = engine.frameBitmap(maxWidth = 160)
                        if (bmp != null) {
                            val thumb = File(engine.saveDirectory() ?: prefs.savesDir, slotName(AUTOSAVE).replace(".mbs", ".png"))
                            runCatching {
                                java.io.FileOutputStream(thumb).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, it) }
                            }
                        }
                    }
                }
            }
        }
    }"""
if target4 in content:
    content = content.replace(target4, replacement4)

with open("app/src/main/java/dev/mariobox/app/EmulatorViewModel.kt", "w") as f:
    f.write(content)
