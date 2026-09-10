with open("app/src/main/java/dev/mariobox/app/EmulatorViewModel.kt", "r") as f:
    content = f.read()

content = content.replace("import androidx.lifecycle.viewModelScope\nimport kotlinx.coroutines.isActive", "import androidx.lifecycle.viewModelScope\nimport androidx.lifecycle.ViewModel\nimport kotlinx.coroutines.isActive")
# Ensure viewModelScope is properly imported if missing
if "import androidx.lifecycle.viewModelScope" not in content:
    content = content.replace("import androidx.lifecycle.ViewModel", "import androidx.lifecycle.ViewModel\nimport androidx.lifecycle.viewModelScope\nimport kotlinx.coroutines.isActive")

with open("app/src/main/java/dev/mariobox/app/EmulatorViewModel.kt", "w") as f:
    f.write(content)

with open("app/src/main/java/dev/mariobox/app/ui/GameScreen.kt", "r") as f:
    content = f.read()

if "import androidx.compose.foundation.gestures.detectTransformGestures" not in content:
    content = content.replace("import androidx.compose.ui.input.pointer.pointerInput", "import androidx.compose.ui.input.pointer.pointerInput\nimport androidx.compose.foundation.gestures.detectTransformGestures")

with open("app/src/main/java/dev/mariobox/app/ui/GameScreen.kt", "w") as f:
    f.write(content)

