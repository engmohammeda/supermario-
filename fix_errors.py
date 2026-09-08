import re

# Fix SettingsScreen
with open("app/src/main/java/dev/mariobox/app/ui/SettingsScreen.kt", "r") as f:
    content = f.read()

content = re.sub(r'\s*var fastForwardRatio by remember \{ mutableStateOf\(prefs\.fastForwardRatio\) \}', '', content)

with open("app/src/main/java/dev/mariobox/app/ui/SettingsScreen.kt", "w") as f:
    f.write(content)

# Fix CheatsScreen imports
with open("app/src/main/java/dev/mariobox/app/ui/CheatsScreen.kt", "r") as f:
    content = f.read()

if "import androidx.compose.foundation.layout.size" not in content:
    content = content.replace("import androidx.compose.foundation.layout.width\n", "import androidx.compose.foundation.layout.width\nimport androidx.compose.foundation.layout.size\n")

with open("app/src/main/java/dev/mariobox/app/ui/CheatsScreen.kt", "w") as f:
    f.write(content)

# Fix GameScreen imports
with open("app/src/main/java/dev/mariobox/app/ui/GameScreen.kt", "r") as f:
    content = f.read()

if "import androidx.compose.foundation.layout.size" not in content:
    content = content.replace("import androidx.compose.foundation.layout.width\n", "import androidx.compose.foundation.layout.width\nimport androidx.compose.foundation.layout.size\n")

with open("app/src/main/java/dev/mariobox/app/ui/GameScreen.kt", "w") as f:
    f.write(content)

