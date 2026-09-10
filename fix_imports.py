with open("app/src/main/java/dev/mariobox/app/ui/GameScreen.kt", "r") as f:
    content = f.read()

content = content.replace("import androidx.compose.foundation.gestures.awaitFirstDown", "import androidx.compose.foundation.gestures.awaitFirstDown\nimport androidx.compose.ui.input.pointer.awaitPointerEvent")
with open("app/src/main/java/dev/mariobox/app/ui/GameScreen.kt", "w") as f:
    f.write(content)
