import re

with open("app/src/main/java/dev/mariobox/app/ui/GameScreen.kt", "r") as f:
    content = f.read()

imports = """import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
"""
content = content.replace("import androidx.compose.runtime.Composable\n", imports + "import androidx.compose.runtime.Composable\n")

# Modify QuickBarButton signature and body
content = content.replace(
    'fun QuickBarButton(\n    icon: String,',
    'fun QuickBarButton(\n    icon: ImageVector,'
)
content = content.replace(
    'Text(icon, fontSize = 13.sp)',
    'Icon(icon, contentDescription = null, tint = if (isHighlight) Color.White else MarioBoxColors.TextPrimary, modifier = Modifier.size(16.dp))'
)

# Replace the calls
content = content.replace('icon = if (vm.paused) "▶" else "⏸",', 'icon = if (vm.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,')
content = content.replace('icon = "💾",', 'icon = Icons.Filled.Save,')
content = content.replace('icon = "🔮",', 'icon = Icons.Filled.AutoFixHigh,')
content = content.replace('icon = "🎛",', 'icon = Icons.Filled.Gamepad,')
content = content.replace('icon = "⚙️",', 'icon = Icons.Filled.Settings,')
content = content.replace('icon = "🚪",', 'icon = Icons.Filled.ExitToApp,')

with open("app/src/main/java/dev/mariobox/app/ui/GameScreen.kt", "w") as f:
    f.write(content)
