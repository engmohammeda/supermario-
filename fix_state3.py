import re

with open("app/src/main/java/dev/mariobox/app/ui/SettingsScreen.kt", "r") as f:
    content = f.read()

# scaleMode
content = re.sub(r'prefs\.scaleMode = it', r'scaleMode = it; prefs.scaleMode = it', content)
# filterMode
content = re.sub(r'prefs\.filterMode = it', r'filterMode = it; prefs.filterMode = it', content)
# scanlinesPercent
content = re.sub(r'prefs\.scanlinesPercent = it\.toInt\(\)', r'scanlinesPercent = it; prefs.scanlinesPercent = it.toInt()', content)
# overscanCrop
content = re.sub(r'prefs\.overscanCrop = it\.toInt\(\)', r'overscanCrop = it; prefs.overscanCrop = it.toInt()', content)
# zoom
content = re.sub(r'prefs\.zoom = it', r'zoom = it; prefs.zoom = it', content)
# rotation
content = re.sub(r'prefs\.setRotationDegrees\(listOf\(0, 90, 180\)\[it\]\)', r'rotation = listOf(0, 90, 180)[it]; prefs.setRotationDegrees(rotation)', content)
# audioEnabled
content = re.sub(r'prefs\.audioEnabled = it', r'audioEnabled = it; prefs.audioEnabled = it', content)
# volume
content = re.sub(r'prefs\.volume = it', r'volume = it; prefs.volume = it', content)
# overlayLayout
content = re.sub(r'prefs\.overlayLayout = it', r'overlayLayout = it; prefs.overlayLayout = it', content)
# overlayVisible
content = re.sub(r'prefs\.overlayVisible = it', r'overlayVisible = it; prefs.overlayVisible = it', content)
# overlayOpacity
content = re.sub(r'prefs\.overlayOpacity = it', r'overlayOpacity = it; prefs.overlayOpacity = it', content)
# overlayScale
content = re.sub(r'prefs\.overlayScale = it', r'overlayScale = it; prefs.overlayScale = it', content)
# turboHz
content = re.sub(r'prefs\.turboHz = it\.toInt\(\)\.coerceIn\(5, 60\)', r'turboHz = it; prefs.turboHz = it.toInt().coerceIn(5, 60)', content)
# rewindSeconds
content = re.sub(r'prefs\.rewindSeconds = it\.toInt\(\)', r'rewindSeconds = it; prefs.rewindSeconds = it.toInt()', content)
# runAhead
content = re.sub(r'prefs\.runAhead = it\.toInt\(\)', r'runAhead = it; prefs.runAhead = it.toInt()', content)
# sramEnabled
content = re.sub(r'prefs\.sramEnabled = it', r'sramEnabled = it; prefs.sramEnabled = it', content)
# autoSaveOnExit
content = re.sub(r'prefs\.autoSaveOnExit = it', r'autoSaveOnExit = it; prefs.autoSaveOnExit = it', content)
# fastForwardRatio
content = re.sub(r'prefs\.fastForwardRatio = it', r'fastForwardRatio = it; prefs.fastForwardRatio = it', content)

with open("app/src/main/java/dev/mariobox/app/ui/SettingsScreen.kt", "w") as f:
    f.write(content)
