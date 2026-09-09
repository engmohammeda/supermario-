import re

with open("app/src/main/java/dev/mariobox/app/ui/Sheets.kt", "r") as f:
    content = f.read()

target = """                SlotCell(
                    label = if (slot.index == 10) stringResource(R.string.states_quick_save)
                    else "خانة ${slot.index + 1}",
                    present = slot.present,"""
replacement = """                SlotCell(
                    label = when (slot.index) {
                        10 -> stringResource(R.string.states_quick_save)
                        11 -> "تلقائي (Auto)"
                        else -> "خانة ${slot.index + 1}"
                    },
                    present = slot.present,"""

if target in content:
    content = content.replace(target, replacement)
else:
    print("Target not found")

with open("app/src/main/java/dev/mariobox/app/ui/Sheets.kt", "w") as f:
    f.write(content)
