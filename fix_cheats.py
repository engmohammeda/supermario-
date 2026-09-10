import re

with open("app/src/main/java/dev/mariobox/app/EmulatorViewModel.kt", "r") as f:
    content = f.read()

target1 = """    fun removeAllCheats() {
        if (!engine.active) return
        val current = engine.cheats()
        current.forEach { engine.removeCheat(it.id) }
        reloadCheatList()
        persistCheats()
    }"""
replacement1 = """    fun removeAllCheats() {
        if (!engine.active) return
        engine.clearCheats()
        reloadCheatList()
        persistCheats()
    }"""
if target1 in content:
    content = content.replace(target1, replacement1)

with open("app/src/main/java/dev/mariobox/app/EmulatorViewModel.kt", "w") as f:
    f.write(content)
