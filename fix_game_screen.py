import re

with open("app/src/main/java/dev/mariobox/app/ui/GameScreen.kt", "r") as f:
    content = f.read()

target = """    LaunchedEffect(Unit) {
        val a = context as? Activity
        a?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
    }"""
replacement = """    LaunchedEffect(Unit) {
        val a = context as? Activity
        a?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
    }

    LaunchedEffect(vm.paused, vm.cartridge) {
        if (!vm.paused && vm.cartridge != null) {
            val romName = vm.cartridge!!.nameWithoutExtension ?: vm.cartridge!!.name
            while(true) {
                kotlinx.coroutines.delay(1000)
                vm.prefs.addPlaytime(romName, 1L)
            }
        }
    }"""
content = content.replace(target, replacement)
with open("app/src/main/java/dev/mariobox/app/ui/GameScreen.kt", "w") as f:
    f.write(content)
