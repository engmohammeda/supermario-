import re

with open("app/src/main/java/dev/mariobox/app/ui/CheatsScreen.kt", "r") as f:
    content = f.read()

# Replace emojis with icons
imports = """import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
"""

content = content.replace("import androidx.compose.runtime.Composable\n", imports + "import androidx.compose.runtime.Composable\n")

# Triple("zoom_out", "➖ إبعاد", -0.25f), etc -> Triple("zoom_out", Icons.Filled.Remove, "إبعاد", -0.25f)
content = content.replace(
    'Triple("zoom_out", "➖ إبعاد", -0.25f)',
    'Triple(Icons.Filled.Remove, "إبعاد", -0.25f)'
)
content = content.replace(
    'Triple("zoom_in", "➕ تقريب", 0.25f)',
    'Triple(Icons.Filled.Add, "تقريب", 0.25f)'
)
content = content.replace(
    'Triple("zoom_reset", "1:1 إعادة", 0f)',
    'Triple(Icons.Filled.Restore, "إعادة 1:1", 0f)'
)

# Fix the iteration over the Triples
content = content.replace(
    ') { (id, label, delta) ->',
    ') { (icon, label, delta) ->'
)
content = content.replace(
    'key = { it.first }',
    'key = { it.second }'
)

# Text("$label · ${(zoomLevel * 100).toInt()}%", -> Row { Icon... Text... }
content = content.replace(
    'Text(\n                        "$label · ${(zoomLevel * 100).toInt()}%",\n                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),\n                        color = Color.White,\n                        fontSize = 12.sp,\n                        fontWeight = FontWeight.Bold\n                    )',
    '''Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "$label · ${(zoomLevel * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }'''
)

# Text("🔮", fontSize = 34.sp) -> Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(34.dp), tint = MarioBoxColors.TextSecondary)
content = content.replace(
    'Text("🔮", fontSize = 34.sp)',
    'Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(34.dp), tint = MarioBoxColors.TextSecondary)'
)

# Text("🗑", color = MarioBoxColors.TextTertiary) -> Icon(Icons.Filled.Delete, contentDescription = null, tint = MarioBoxColors.TextTertiary)
content = content.replace(
    'Text("🗑", color = MarioBoxColors.TextTertiary)',
    'Icon(Icons.Filled.Delete, contentDescription = null, tint = MarioBoxColors.TextTertiary, modifier = Modifier.size(18.dp))'
)

# Change LazyRow to FlowRow (except for CategoryStrip maybe, but let's change both to FlowRow for better wrapping)
content = content.replace(
    '''LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp)
        ) {
            items(CheatLibrary.quickActions, key = { it.id }) { a ->''',
    '''@OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 2.dp)
        ) {
            CheatLibrary.quickActions.forEach { a ->'''
)

content = content.replace(
    '''LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp)
        ) {
            items(
                listOf(
                    Triple(Icons.Filled.Remove, "إبعاد", -0.25f),
                    Triple(Icons.Filled.Add, "تقريب", 0.25f),
                    Triple(Icons.Filled.Restore, "إعادة 1:1", 0f),
                ),
                key = { it.second }
            ) { (icon, label, delta) ->''',
    '''@OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 2.dp)
        ) {
            listOf(
                Triple(Icons.Filled.Remove, "إبعاد", -0.25f),
                Triple(Icons.Filled.Add, "تقريب", 0.25f),
                Triple(Icons.Filled.Restore, "إعادة 1:1", 0f),
            ).forEach { (icon, label, delta) ->'''
)

# Change CategoryStrip to FlowRow
content = content.replace(
    '''LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        item { CategoryChip(null, "الكل", selected == null) { onPick(null) } }
        items(CheatLibrary.Category.entries) { c ->
            CategoryChip(c, categoryLabel(c), selected == c) { onPick(c) }
        }
    }''',
    '''@OptIn(ExperimentalLayoutApi::class)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CategoryChip(null, "الكل", selected == null) { onPick(null) }
        CheatLibrary.Category.entries.forEach { c ->
            CategoryChip(c, categoryLabel(c), selected == c) { onPick(c) }
        }
    }'''
)

with open("app/src/main/java/dev/mariobox/app/ui/CheatsScreen.kt", "w") as f:
    f.write(content)
