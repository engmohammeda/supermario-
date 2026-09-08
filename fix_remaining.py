import re

with open("app/src/main/java/dev/mariobox/app/ui/LibraryScreen.kt", "r") as f:
    content = f.read()

# Replace Text("▶", ...) with Icon(Icons.Filled.PlayArrow, ...)
if "import androidx.compose.material.icons.filled.PlayArrow" not in content:
    imports = """import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
"""
    content = content.replace("import androidx.compose.runtime.Composable\n", imports + "import androidx.compose.runtime.Composable\n")

if "import androidx.compose.foundation.layout.size" not in content:
    content = content.replace("import androidx.compose.foundation.layout.width\n", "import androidx.compose.foundation.layout.width\nimport androidx.compose.foundation.layout.size\n")

content = content.replace('Text("▶", color = Color.White, fontSize = 14.sp)', 'Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))')
content = content.replace('Text("▶", color = MarioBoxColors.PrimaryRed, fontSize = 14.sp)', 'Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MarioBoxColors.PrimaryRed, modifier = Modifier.size(16.dp))')

# Maybe there are other emojis in LibraryScreen? Let's check for some common ones.
content = content.replace('Text("🗑", color = MarioBoxColors.TextTertiary)', 'Icon(Icons.Filled.Delete, contentDescription = null, tint = MarioBoxColors.TextTertiary, modifier = Modifier.size(16.dp))')
if "import androidx.compose.material.icons.filled.Delete" not in content:
    content = content.replace("import androidx.compose.material.icons.filled.PlayArrow", "import androidx.compose.material.icons.filled.PlayArrow\nimport androidx.compose.material.icons.filled.Delete")

with open("app/src/main/java/dev/mariobox/app/ui/LibraryScreen.kt", "w") as f:
    f.write(content)


with open("app/src/main/java/dev/mariobox/app/ui/GameScreen.kt", "r") as f:
    content = f.read()

content = content.replace('PadAction.UP to "▲",', 'PadAction.UP to "UP",')
content = content.replace('PadAction.DOWN to "▼",', 'PadAction.DOWN to "DOWN",')
content = content.replace('PadAction.LEFT to "◀",', 'PadAction.LEFT to "LEFT",')
content = content.replace('PadAction.RIGHT to "▶",', 'PadAction.RIGHT to "RIGHT",')
content = content.replace('PadAction.TURBO_A to "A⚡",', 'PadAction.TURBO_A to "A*",')
content = content.replace('PadAction.TURBO_B to "B⚡",', 'PadAction.TURBO_B to "B*",')
content = content.replace('PadAction.REWIND to "⏪",', 'PadAction.REWIND to "<<",')
content = content.replace('PadAction.FAST_FWD to "⏩",', 'PadAction.FAST_FWD to ">>",')
content = content.replace('PadAction.QUICK to "💾"', 'PadAction.QUICK to "SAVE"')

with open("app/src/main/java/dev/mariobox/app/ui/GameScreen.kt", "w") as f:
    f.write(content)
