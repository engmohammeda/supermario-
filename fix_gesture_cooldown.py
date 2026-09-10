import re

with open("app/src/main/java/dev/mariobox/app/ui/GameScreen.kt", "r") as f:
    content = f.read()

content = content.replace("var ff by remember { mutableStateOf(false) }", "var ff by remember { mutableStateOf(false) }\n    var lastGestureTime by remember { mutableStateOf(0L) }")

target = """            .pointerInput(Unit) {
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

replacement = """            .pointerInput(Unit) {
                androidx.compose.foundation.gestures.detectTransformGestures { centroid, pan, zoom, rotation ->
                    if (System.currentTimeMillis() - lastGestureTime > 2000) {
                        if (pan.y < -30f) {
                            lastGestureTime = System.currentTimeMillis()
                            vm.quickSave()
                            vm.toast = "تم الحفظ السريع"
                        } else if (pan.y > 30f) {
                            lastGestureTime = System.currentTimeMillis()
                            vm.quickLoad()
                            vm.toast = "تم الاسترجاع السريع"
                        }
                    }
                }
            }"""

content = content.replace(target, replacement)
with open("app/src/main/java/dev/mariobox/app/ui/GameScreen.kt", "w") as f:
    f.write(content)
