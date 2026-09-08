import re

with open("app/src/main/java/dev/mariobox/app/ui/SettingsScreen.kt", "r") as f:
    content = f.read()

state_declarations = """
    val prefs = vm.prefs
    var paletteKey by remember { mutableStateOf(prefs.palette) }
    var scaleMode by remember { mutableStateOf(prefs.scaleMode) }
    var filterMode by remember { mutableStateOf(prefs.filterMode) }
    var scanlinesPercent by remember { mutableStateOf(prefs.scanlinesPercent.toFloat()) }
    var overscanCrop by remember { mutableStateOf(prefs.overscanCrop.toFloat()) }
    var zoom by remember { mutableStateOf(prefs.zoom) }
    var rotation by remember { mutableStateOf(prefs.rotation) }
    var audioEnabled by remember { mutableStateOf(prefs.audioEnabled) }
    var volume by remember { mutableStateOf(prefs.volume) }
    var overlayLayout by remember { mutableStateOf(prefs.overlayLayout) }
    var overlayVisible by remember { mutableStateOf(prefs.overlayVisible) }
    var overlayOpacity by remember { mutableStateOf(prefs.overlayOpacity) }
    var overlayScale by remember { mutableStateOf(prefs.overlayScale) }
    var turboHz by remember { mutableStateOf(prefs.turboHz.toFloat()) }
    var rewindSeconds by remember { mutableStateOf(prefs.rewindSeconds.toFloat()) }
    var runAhead by remember { mutableStateOf(prefs.runAhead.toFloat()) }
    var sramEnabled by remember { mutableStateOf(prefs.sramEnabled) }
    var autoSaveOnExit by remember { mutableStateOf(prefs.autoSaveOnExit) }
    var fastForwardRatio by remember { mutableStateOf(prefs.fastForwardRatio) }
"""

content = content.replace("    val prefs = vm.prefs\n    var paletteKey by remember { mutableStateOf(prefs.palette) }", state_declarations)

# Now replace the usage of prefs.* with the state variables
replacements = [
    ("prefs.scaleMode,", "scaleMode,"),
    ("{ prefs.scaleMode = it\n                    vm.refreshPresentation()\n                }", "{ scaleMode = it; prefs.scaleMode = it\n                    vm.refreshPresentation()\n                }"),
    ("prefs.filterMode,", "filterMode,"),
    ("{ prefs.filterMode = it\n                    vm.refreshPresentation()\n                }", "{ filterMode = it; prefs.filterMode = it\n                    vm.refreshPresentation()\n                }"),
    ("prefs.scanlinesPercent.toFloat()", "scanlinesPercent"),
    ("{ prefs.scanlinesPercent = it.toInt()\n                    vm.refreshPresentation()\n                }", "{ scanlinesPercent = it; prefs.scanlinesPercent = it.toInt()\n                    vm.refreshPresentation()\n                }"),
    ("prefs.overscanCrop.toFloat()", "overscanCrop"),
    ("{ prefs.overscanCrop = it.toInt()\n                    vm.refreshPresentation()\n                }", "{ overscanCrop = it; prefs.overscanCrop = it.toInt()\n                    vm.refreshPresentation()\n                }"),
    ("prefs.zoom,", "zoom,"),
    ("{ prefs.zoom = it\n                    vm.refreshPresentation()\n                }", "{ zoom = it; prefs.zoom = it\n                    vm.refreshPresentation()\n                }"),
    ("when (prefs.rotation) { 90 -> 1; 180 -> 2; else -> 0 },", "when (rotation) { 90 -> 1; 180 -> 2; else -> 0 },"),
    ("{ prefs.setRotationDegrees(listOf(0, 90, 180)[it])\n                    vm.refreshPresentation()\n                }", "{ val d = listOf(0, 90, 180)[it]; rotation = d; prefs.setRotationDegrees(d)\n                    vm.refreshPresentation()\n                }"),
    ("prefs.audioEnabled", "audioEnabled"),
    ("{ prefs.audioEnabled = it }", "{ audioEnabled = it; prefs.audioEnabled = it }"),
    ("prefs.volume,", "volume,"),
    ("{ prefs.volume = it\n                    vm.refreshPresentation()\n                }", "{ volume = it; prefs.volume = it\n                    vm.refreshPresentation()\n                }"),
    ("prefs.overlayLayout,", "overlayLayout,"),
    ("{ prefs.overlayLayout = it }", "{ overlayLayout = it; prefs.overlayLayout = it }"),
    ("prefs.overlayVisible", "overlayVisible"),
    ("{ prefs.overlayVisible = it }", "{ overlayVisible = it; prefs.overlayVisible = it }"),
    ("prefs.overlayOpacity,", "overlayOpacity,"),
    ("{ prefs.overlayOpacity = it }", "{ overlayOpacity = it; prefs.overlayOpacity = it }"),
    ("prefs.overlayScale,", "overlayScale,"),
    ("{ prefs.overlayScale = it }", "{ overlayScale = it; prefs.overlayScale = it }"),
    ("prefs.turboHz.toFloat()", "turboHz"),
    ("{ prefs.turboHz = it.toInt().coerceIn(5, 60) }", "{ turboHz = it; prefs.turboHz = it.toInt().coerceIn(5, 60) }"),
    ("prefs.rewindSeconds.toFloat()", "rewindSeconds"),
    ("{ prefs.rewindSeconds = it.toInt() }", "{ rewindSeconds = it; prefs.rewindSeconds = it.toInt() }"),
    ("prefs.runAhead.toFloat()", "runAhead"),
    ("{ prefs.runAhead = it.toInt() }", "{ runAhead = it; prefs.runAhead = it.toInt() }"),
    ("prefs.sramEnabled", "sramEnabled"),
    ("{ prefs.sramEnabled = it }", "{ sramEnabled = it; prefs.sramEnabled = it }"),
    ("prefs.autoSaveOnExit", "autoSaveOnExit"),
    ("{ prefs.autoSaveOnExit = it }", "{ autoSaveOnExit = it; prefs.autoSaveOnExit = it }"),
    ("prefs.fastForwardRatio,", "fastForwardRatio,"),
    ("{ prefs.fastForwardRatio = it }", "{ fastForwardRatio = it; prefs.fastForwardRatio = it }"),
]

for old, new in replacements:
    content = content.replace(old, new)

with open("app/src/main/java/dev/mariobox/app/ui/SettingsScreen.kt", "w") as f:
    f.write(content)
