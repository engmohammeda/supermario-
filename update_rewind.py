import re

with open("app/src/main/java/dev/mariobox/app/EmulatorViewModel.kt", "r") as f:
    content = f.read()

target = """    fun overlayPress(action: PadAction, down: Boolean) {
        if (!engine.active) return
        val turbo = PadLayout.turboBitFor(action)
        if (turbo != 0) {
            turboMask = if (down) turboMask or turbo else turboMask and turbo.inv()
            pushInput()
            return
        }
        val bit = PadLayout.bitFor(action)
        if (bit == 0) return
        touchMask = Gamepad.applyKey(touchMask, bit, down)
        pushInput()
    }"""
replacement = """    private var rewindJob: kotlinx.coroutines.Job? = null

    fun overlayPress(action: PadAction, down: Boolean) {
        if (!engine.active) return
        if (action == PadAction.REWIND) {
            if (down) {
                if (rewindJob == null) {
                    rewindJob = androidx.lifecycle.viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        while (kotlinx.coroutines.isActive) {
                            if (engine.rewindAvailable() > 0) {
                                engine.rewindStep(2)
                            }
                            kotlinx.coroutines.delay(33)
                        }
                    }
                }
            } else {
                rewindJob?.cancel()
                rewindJob = null
            }
            return
        }
        val turbo = PadLayout.turboBitFor(action)
        if (turbo != 0) {
            turboMask = if (down) turboMask or turbo else turboMask and turbo.inv()
            pushInput()
            return
        }
        val bit = PadLayout.bitFor(action)
        if (bit == 0) return
        touchMask = Gamepad.applyKey(touchMask, bit, down)
        pushInput()
    }"""
content = content.replace(target, replacement)
with open("app/src/main/java/dev/mariobox/app/EmulatorViewModel.kt", "w") as f:
    f.write(content)
