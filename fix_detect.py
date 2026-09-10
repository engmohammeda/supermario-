import re
with open("app/src/main/java/dev/mariobox/app/ui/GameScreen.kt", "r") as f:
    content = f.read()

target = "androidx.compose.foundation.gestures.detectTransformGestures { centroid, pan, zoom, rotation ->"
replacement = "detectTransformGestures { centroid, pan, zoom, rotation ->"
content = content.replace(target, replacement)
with open("app/src/main/java/dev/mariobox/app/ui/GameScreen.kt", "w") as f:
    f.write(content)
