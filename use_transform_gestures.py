import re

with open("app/src/main/java/dev/mariobox/app/ui/GameScreen.kt", "r") as f:
    content = f.read()

target = """            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var initialY1 = 0f
                    var initialY2 = 0f
                    var swipeTriggered = false
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
                    } while (event.changes.any { it.pressed })
                }
            }"""

replacement = """            .pointerInput(Unit) {
                androidx.compose.foundation.gestures.detectTransformGestures { centroid, pan, zoom, rotation ->
                    if (pan.y < -30f) {
                        vm.quickSave()
                        vm.toast = "تم الحفظ السريع"
                    } else if (pan.y > 30f) {
                        vm.quickLoad()
                        vm.toast = "تم الاسترجاع السريع"
                    }
                }
            }"""
content = content.replace(target, replacement)

with open("app/src/main/java/dev/mariobox/app/ui/GameScreen.kt", "w") as f:
    f.write(content)
