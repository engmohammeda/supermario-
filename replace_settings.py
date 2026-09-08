import re

with open("app/src/main/java/dev/mariobox/app/ui/SettingsScreen.kt", "r") as f:
    content = f.read()

# Add imports
imports = """import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
"""
content = content.replace("import androidx.compose.runtime.Composable\n", imports + "import androidx.compose.runtime.Composable\n")

# Replace SectionCard definition
content = content.replace(
    "fun SectionCard(icon: String, title: String, content: @Composable () -> Unit) {",
    "fun SectionCard(icon: ImageVector, title: String, content: @Composable () -> Unit) {"
)
content = content.replace(
    "Text(icon, fontSize = 15.sp)",
    "Icon(icon, contentDescription = null, tint = MarioBoxColors.PrimaryRed, modifier = Modifier.size(16.dp))"
)

# Replace SectionCard usages
content = content.replace('icon = "🖥",', 'icon = Icons.Filled.Tv,')
content = content.replace('icon = "🎨",', 'icon = Icons.Filled.Palette,')
content = content.replace('icon = "🔊",', 'icon = Icons.Filled.VolumeUp,')
content = content.replace('icon = "🎮",', 'icon = Icons.Filled.Gamepad,')
content = content.replace('icon = "⚙️",', 'icon = Icons.Filled.Memory,')
content = content.replace('icon = "🧩",', 'icon = Icons.Filled.Extension,')
content = content.replace('icon = "ℹ️",', 'icon = Icons.Filled.Info,')

with open("app/src/main/java/dev/mariobox/app/ui/SettingsScreen.kt", "w") as f:
    f.write(content)
