import re

with open("engine/src/main/java/dev/mariobox/engine/MbEngine.kt", "r") as f:
    content = f.read()
target = """    private fun cheatFromFields(index: Int, f: Array<String>): Cheat = Cheat(
        id = index,
        code = f.getOrElse(0) { "" },
        description = f.getOrElse(1) { "" },
        enabled = f.getOrElse(2) { "0" } != "0",
        kind = f.getOrElse(3) { "0" }.toIntOrNull() ?: 0,
        address = f.getOrElse(4) { "0" }.toLongOrNull()?.toInt() ?: 0,
        value = f.getOrElse(5) { "0" }.toLongOrNull()?.toInt() ?: 0,
        compare = f.getOrElse(6) { "-1" }.toIntOrNull() ?: -1,
    )"""
replacement = """    private fun cheatFromFields(index: Int, f: Array<String>): Cheat = Cheat(
        id = f.getOrElse(7) { "$index" }.toIntOrNull() ?: index,
        code = f.getOrElse(0) { "" },
        description = f.getOrElse(1) { "" },
        enabled = f.getOrElse(2) { "0" } != "0",
        kind = f.getOrElse(3) { "0" }.toIntOrNull() ?: 0,
        address = f.getOrElse(4) { "0" }.toLongOrNull()?.toInt() ?: 0,
        value = f.getOrElse(5) { "0" }.toLongOrNull()?.toInt() ?: 0,
        compare = f.getOrElse(6) { "-1" }.toIntOrNull() ?: -1,
    )"""
content = content.replace(target, replacement)
with open("engine/src/main/java/dev/mariobox/engine/MbEngine.kt", "w") as f:
    f.write(content)
