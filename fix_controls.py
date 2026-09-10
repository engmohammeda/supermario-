import re
with open("app/src/main/java/dev/mariobox/app/ui/Controls.kt", "r") as f:
    content = f.read()

target = """            val isMomentary = PadLayout.bitFor(action) == 0 && PadLayout.turboBitFor(action) == 0"""
replacement = """            val isMomentary = PadLayout.bitFor(action) == 0 && PadLayout.turboBitFor(action) == 0 && action != PadAction.REWIND"""
content = content.replace(target, replacement)
with open("app/src/main/java/dev/mariobox/app/ui/Controls.kt", "w") as f:
    f.write(content)

with open("app/src/main/java/dev/mariobox/app/ui/GameScreen.kt", "r") as f:
    content = f.read()
target2 = """                        PadAction.REWIND -> vm.rewind(1)"""
replacement2 = """                        // REWIND is now handled via onPress/onRelease"""
content = content.replace(target2, replacement2)
with open("app/src/main/java/dev/mariobox/app/ui/GameScreen.kt", "w") as f:
    f.write(content)

