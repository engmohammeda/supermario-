import re

with open("app/src/main/java/dev/mariobox/app/ui/LibraryScreen.kt", "r") as f:
    content = f.read()

imports = """import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.VideogameAsset
"""
content = content.replace("import androidx.compose.material.icons.filled.Delete\n", "import androidx.compose.material.icons.filled.Delete\n" + imports)

content = content.replace('Text("⚠️", fontSize = 20.sp)', 'Icon(Icons.Filled.Warning, contentDescription = null, tint = MarioBoxColors.AccentAmber, modifier = Modifier.size(20.dp))')
content = content.replace('Text("🔍", fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))', 'Icon(Icons.Filled.Search, contentDescription = null, tint = MarioBoxColors.TextSecondary, modifier = Modifier.padding(start = 4.dp).size(14.dp))')
content = content.replace('Text("✕", color = MarioBoxColors.TextSecondary, fontSize = 14.sp)', 'Icon(Icons.Filled.Close, contentDescription = null, tint = MarioBoxColors.TextSecondary, modifier = Modifier.size(14.dp))')
content = content.replace('Text("ℹ", color = MarioBoxColors.TextSecondary, fontSize = 14.sp)', 'Icon(Icons.Filled.Info, contentDescription = null, tint = MarioBoxColors.TextSecondary, modifier = Modifier.size(14.dp))')
content = content.replace('Text("🗑", color = MarioBoxColors.TextTertiary, fontSize = 14.sp)', 'Icon(Icons.Filled.Delete, contentDescription = null, tint = MarioBoxColors.TextTertiary, modifier = Modifier.size(14.dp))')
content = content.replace('Text("＋", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)', 'Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))')
content = content.replace('Text("🔍", fontSize = 36.sp)', 'Icon(Icons.Filled.Search, contentDescription = null, tint = MarioBoxColors.TextSecondary, modifier = Modifier.size(36.dp))')
content = content.replace('Text("🕹️", fontSize = 20.sp)', 'Icon(Icons.Filled.VideogameAsset, contentDescription = null, tint = MarioBoxColors.PrimaryRed, modifier = Modifier.size(20.dp))')

with open("app/src/main/java/dev/mariobox/app/ui/LibraryScreen.kt", "w") as f:
    f.write(content)
