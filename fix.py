with open("app/src/main/java/dev/mariobox/app/EmulatorViewModel.kt", "r") as f:
    content = f.read()
if "import androidx.lifecycle.viewModelScope" not in content:
    content = content.replace("import androidx.lifecycle.ViewModel", "import androidx.lifecycle.ViewModel\nimport androidx.lifecycle.viewModelScope\nimport kotlinx.coroutines.isActive")
with open("app/src/main/java/dev/mariobox/app/EmulatorViewModel.kt", "w") as f:
    f.write(content)

with open("app/src/main/java/dev/mariobox/app/ui/GameScreen.kt", "r") as f:
    content = f.read()

# Fix pointerInput imports
if "import androidx.compose.ui.input.pointer.pointerInput" not in content:
    content = content.replace("import androidx.compose.ui.graphics.vector.ImageVector", "import androidx.compose.ui.graphics.vector.ImageVector\nimport androidx.compose.ui.input.pointer.pointerInput\nimport androidx.compose.foundation.gestures.awaitEachGesture\nimport androidx.compose.foundation.gestures.awaitFirstDown")

# Remove broken imports I added
content = content.replace("import androidx.compose.foundation.gestures.awaitFirstDown\nimport androidx.compose.ui.input.pointer.awaitPointerEvent", "")
content = content.replace("import androidx.compose.runtime.LaunchedEffect\nimport androidx.compose.ui.input.pointer.pointerInput\nimport androidx.compose.foundation.gestures.awaitEachGesture\nimport androidx.compose.foundation.gestures.awaitFirstDown", "import androidx.compose.runtime.LaunchedEffect")

# Fix pointer event iteration
target = """                    var swipeTriggered = false
                    do {
                        val event = awaitPointerEvent()
                        val activePointers = event.changes.filter { it.pressed }
                        if (activePointers.size == 2) {
                            if (initialY1 == 0f) {
                                initialY1 = activePointers[0].position.y
                                initialY2 = activePointers[1].position.y
                            } else {
                                val dy1 = activePointers[0].position.y - initialY1
                                val dy2 = activePointers[1].position.y - initialY2
                                if (dy1 < -100f && dy2 < -100f && !swipeTriggered) {
                                    swipeTriggered = true
                                    vm.quickSave()
                                    vm.toast = "تم الحفظ السريع"
                                } else if (dy1 > 100f && dy2 > 100f && !swipeTriggered) {
                                    swipeTriggered = true
                                    vm.quickLoad()
                                    vm.toast = "تم الاسترجاع السريع"
                                }
                            }
                        } else {
                            initialY1 = 0f
                        }
                    } while (event.changes.any { it.pressed })"""

replacement = """                    var swipeTriggered = false
                    do {
                        val event = awaitPointerEvent()
                        val activePointers = event.changes.filter { it.pressed }
                        if (activePointers.size == 2) {
                            if (initialY1 == 0f) {
                                initialY1 = activePointers[0].position.y
                                initialY2 = activePointers[1].position.y
                            } else {
                                val dy1 = activePointers[0].position.y - initialY1
                                val dy2 = activePointers[1].position.y - initialY2
                                if (dy1 < -100f && dy2 < -100f && !swipeTriggered) {
                                    swipeTriggered = true
                                    vm.quickSave()
                                    vm.toast = "تم الحفظ السريع"
                                } else if (dy1 > 100f && dy2 > 100f && !swipeTriggered) {
                                    swipeTriggered = true
                                    vm.quickLoad()
                                    vm.toast = "تم الاسترجاع السريع"
                                }
                            }
                        } else {
                            initialY1 = 0f
                        }
                    } while (event.changes.any { it.pressed })"""

content = content.replace(target, replacement)
with open("app/src/main/java/dev/mariobox/app/ui/GameScreen.kt", "w") as f:
    f.write(content)
