import re

with open("app/src/main/java/dev/mariobox/app/ui/SettingsScreen.kt", "r") as f:
    content = f.read()

content = content.replace("var scanlinesPercent by remember { mutableStateOf(scanlinesPercent) }", "var scanlinesPercent by remember { mutableStateOf(prefs.scanlinesPercent.toFloat()) }")
content = content.replace("var overscanCrop by remember { mutableStateOf(overscanCrop) }", "var overscanCrop by remember { mutableStateOf(prefs.overscanCrop.toFloat()) }")
content = content.replace("var audioEnabled by remember { mutableStateOf(audioEnabled) }", "var audioEnabled by remember { mutableStateOf(prefs.audioEnabled) }")
content = content.replace("var overlayVisible by remember { mutableStateOf(overlayVisible) }", "var overlayVisible by remember { mutableStateOf(prefs.overlayVisible) }")
content = content.replace("var turboHz by remember { mutableStateOf(turboHz) }", "var turboHz by remember { mutableStateOf(prefs.turboHz.toFloat()) }")
content = content.replace("var rewindSeconds by remember { mutableStateOf(rewindSeconds) }", "var rewindSeconds by remember { mutableStateOf(prefs.rewindSeconds.toFloat()) }")
content = content.replace("var runAhead by remember { mutableStateOf(runAhead) }", "var runAhead by remember { mutableStateOf(prefs.runAhead.toFloat()) }")
content = content.replace("var sramEnabled by remember { mutableStateOf(sramEnabled) }", "var sramEnabled by remember { mutableStateOf(prefs.sramEnabled) }")
content = content.replace("var autoSaveOnExit by remember { mutableStateOf(autoSaveOnExit) }", "var autoSaveOnExit by remember { mutableStateOf(prefs.autoSaveOnExit) }")

with open("app/src/main/java/dev/mariobox/app/ui/SettingsScreen.kt", "w") as f:
    f.write(content)
