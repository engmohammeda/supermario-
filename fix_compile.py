import re

with open("app/src/main/java/dev/mariobox/app/EmulatorViewModel.kt", "r") as f:
    content = f.read()

if "import androidx.lifecycle.viewModelScope" not in content:
    content = content.replace("import androidx.lifecycle.ViewModel", "import androidx.lifecycle.ViewModel\nimport androidx.lifecycle.viewModelScope\nimport kotlinx.coroutines.isActive")

content = content.replace("androidx.lifecycle.viewModelScope", "viewModelScope")
with open("app/src/main/java/dev/mariobox/app/EmulatorViewModel.kt", "w") as f:
    f.write(content)


with open("app/src/main/java/dev/mariobox/app/ui/GameScreen.kt", "r") as f:
    content = f.read()
if "import androidx.compose.ui.input.pointer.pointerInput" not in content:
    content = content.replace("import androidx.compose.runtime.LaunchedEffect", "import androidx.compose.runtime.LaunchedEffect\nimport androidx.compose.ui.input.pointer.pointerInput\nimport androidx.compose.foundation.gestures.awaitEachGesture\nimport androidx.compose.foundation.gestures.awaitFirstDown")

content = content.replace(".androidx.compose.ui.input.pointer.pointerInput", ".pointerInput")
content = content.replace("androidx.compose.foundation.gestures.awaitEachGesture", "awaitEachGesture")
content = content.replace("androidx.compose.foundation.gestures.awaitFirstDown", "awaitFirstDown")

with open("app/src/main/java/dev/mariobox/app/ui/GameScreen.kt", "w") as f:
    f.write(content)


with open("app/src/main/java/dev/mariobox/app/ui/LibraryScreen.kt", "r") as f:
    content = f.read()

content = content.replace("val saveDir = dev.mariobox.app.Prefs(ctx).saveDirFor(item.file)", "val saveDir = dev.mariobox.app.Prefs(androidx.compose.ui.platform.LocalContext.current).saveDirFor(item.file)")

with open("app/src/main/java/dev/mariobox/app/ui/LibraryScreen.kt", "w") as f:
    f.write(content)

