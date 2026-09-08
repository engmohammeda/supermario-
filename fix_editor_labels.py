import re

with open("app/src/main/java/dev/mariobox/app/ui/PadLayout.kt", "r") as f:
    content = f.read()

replacements = {
    'UP -> "▲ لأعلى"': 'UP -> "لأعلى"',
    'DOWN -> "▼ لأسفل"': 'DOWN -> "لأسفل"',
    'LEFT -> "◀ يسار"': 'LEFT -> "يسار"',
    'RIGHT -> "▶ يمين"': 'RIGHT -> "يمين"',
    'TURBO_A -> "A⚡ تربو"': 'TURBO_A -> "تربو A"',
    'TURBO_B -> "B⚡ تربو"': 'TURBO_B -> "تربو B"',
    'REWIND -> "⏪ رجوع"': 'REWIND -> "رجوع"',
    'QUICK -> "💾 حفظ سريع"': 'QUICK -> "حفظ سريع"',
    'FAST_FWD -> "⏩ تسريع"': 'FAST_FWD -> "تسريع"'
}

for old, new in replacements.items():
    content = content.replace(old, new)

with open("app/src/main/java/dev/mariobox/app/ui/PadLayout.kt", "w") as f:
    f.write(content)
