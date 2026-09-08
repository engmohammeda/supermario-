package dev.mariobox.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mariobox.app.EmulatorViewModel
import dev.mariobox.app.R
import dev.mariobox.engine.Cheat

/**
 * Professional cheat screen: one-tap features, a searchable, categorised library
 * of curated SMB1 codes, and the list of cheats currently applied to the game.
 */
@Composable
fun CheatsScreen(vm: EmulatorViewModel) {
    var mode by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<CheatLibrary.Category?>(null) }
    var zoomLevel by remember { mutableStateOf(vm.prefs.zoom) }
    val cheats = vm.cheats

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
    ) {
        // Quick Actions
        item {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                CheatLibrary.quickActions.forEach { a ->
                    Surface(
                        onClick = {
                            a.zoom?.let { vm.setZoom(it) }
                            vm.applyCheatCodes(a.codes, a.title, a.raw)
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = MarioBoxColors.PrimaryRed.copy(alpha = 0.16f),
                        modifier = Modifier.border(1.dp, MarioBoxColors.PrimaryRedGlow, RoundedCornerShape(10.dp))
                    ) {
                        Text(
                            a.title,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // View Features
        item {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                listOf(
                    Triple(Icons.Filled.Remove, "إبعاد", -0.25f),
                    Triple(Icons.Filled.Add, "تقريب", 0.25f),
                    Triple(Icons.Filled.Restore, "إعادة 1:1", 0f),
                ).forEach { (icon, label, delta) ->
                    Surface(
                        onClick = {
                            val next = if (delta == 0f) 1f else (vm.prefs.zoom + delta)
                                .coerceIn(dev.mariobox.engine.RenderSettings.ZOOM_MIN, dev.mariobox.engine.RenderSettings.ZOOM_MAX)
                            vm.setZoom(next)
                            zoomLevel = next
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = MarioBoxColors.SecondaryCyan.copy(alpha = 0.14f),
                        modifier = Modifier.border(1.dp, MarioBoxColors.SecondaryCyan, RoundedCornerShape(10.dp))
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "$label · ${(zoomLevel * 100).toInt()}%",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Toggle
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ModePill(
                    label = "المكتبة (${CheatLibrary.cheats.size})",
                    selected = mode == 0,
                    onClick = { mode = 0 },
                    modifier = Modifier.weight(1f)
                )
                ModePill(
                    label = "المفعّلة (${cheats.size})",
                    selected = mode == 1,
                    onClick = { mode = 1 },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (mode == 0) {
            item {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it.uppercase() },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.cheats_search), fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MarioBoxColors.Surface,
                            unfocusedContainerColor = MarioBoxColors.Surface,
                            focusedBorderColor = MarioBoxColors.PrimaryRed,
                            unfocusedBorderColor = MarioBoxColors.SurfaceBorder
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    CategoryStrip(selected = category, onPick = { category = it })
                }
            }
            
            val itemsList = CheatLibrary.search(query).filter { category == null || it.category == category }
            if (itemsList.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.cheats_no_results), color = MarioBoxColors.TextTertiary, fontSize = 13.sp)
                    }
                }
            } else {
                items(itemsList, key = { it.id }) { p ->
                    LibraryRow(preset = p, onApply = { vm.applyCheatCodes(p.codes, p.title, p.raw) })
                }
            }
        } else {
            if (cheats.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(34.dp), tint = MarioBoxColors.TextSecondary)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.cheats_none),
                                color = MarioBoxColors.TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                items(cheats, key = { it.id }) { c ->
                    val index = cheats.indexOf(c)
                    AppliedCheatRow(c, index, vm)
                }
            }
        }
    }
}

@Composable
private fun ModePill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MarioBoxColors.SecondaryCyan else MarioBoxColors.Surface,
        modifier = modifier.border(
            1.dp,
            if (selected) MarioBoxColors.SecondaryCyan else MarioBoxColors.SurfaceBorder,
            RoundedCornerShape(10.dp)
        )
    ) {
        Text(
            label,
            modifier = Modifier.padding(vertical = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = if (selected) Color.Black else MarioBoxColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CategoryStrip(
    selected: CheatLibrary.Category?,
    onPick: (CheatLibrary.Category?) -> Unit,
) {
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CategoryChip(null, "الكل", selected == null) { onPick(null) }
        CheatLibrary.Category.entries.forEach { c ->
            CategoryChip(c, categoryLabel(c), selected == c) { onPick(c) }
        }
    }
}

@Composable
private fun CategoryChip(
    category: CheatLibrary.Category?,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MarioBoxColors.AccentAmber else MarioBoxColors.SurfaceElevated,
        modifier = Modifier.border(
            1.dp,
            if (selected) MarioBoxColors.AccentAmber else MarioBoxColors.SurfaceBorder,
            RoundedCornerShape(8.dp)
        )
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            color = if (selected) Color.Black else MarioBoxColors.TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun LibraryList(
    presets: List<CheatLibrary.CheatPreset>,
    onApply: (CheatLibrary.CheatPreset) -> Unit,
) {
    if (presets.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.cheats_no_results), color = MarioBoxColors.TextTertiary, fontSize = 13.sp)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(presets, key = { it.id }) { p ->
            LibraryRow(preset = p, onApply = { onApply(p) })
        }
    }
}

@Composable
private fun LibraryRow(preset: CheatLibrary.CheatPreset, onApply: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MarioBoxColors.Surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MarioBoxColors.SurfaceBorder, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(34.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (preset.raw) MarioBoxColors.AccentPurple.copy(alpha = 0.2f)
                        else MarioBoxColors.SecondaryCyan.copy(alpha = 0.14f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (preset.raw) "RAW" else "GG",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = if (preset.raw) MarioBoxColors.AccentPurple else MarioBoxColors.SecondaryCyan
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    preset.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MarioBoxColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    preset.codeText,
                    color = MarioBoxColors.SecondaryCyan,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (preset.note.isNotBlank()) {
                    Text(
                        preset.note,
                        color = MarioBoxColors.TextTertiary,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Button(
                onClick = onApply,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MarioBoxColors.PrimaryRed),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("+", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun AppliedCheatList(vm: EmulatorViewModel, cheats: List<Cheat>) {
    if (cheats.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(34.dp), tint = MarioBoxColors.TextSecondary)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.cheats_none),
                    color = MarioBoxColors.TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(cheats, key = { it.id }) { c ->
            val index = cheats.indexOf(c)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MarioBoxColors.Surface)
                    .border(1.dp, MarioBoxColors.SurfaceBorder, RoundedCornerShape(12.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = c.enabled,
                    onCheckedChange = { on -> vm.setCheatEnabled(index, on) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MarioBoxColors.PrimaryRed,
                        uncheckedColor = MarioBoxColors.TextSecondary
                    )
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        c.code,
                        style = MaterialTheme.typography.titleSmall,
                        color = MarioBoxColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    val live = vm.cheatFromCore(c.id)
                    Text(
                        c.description.ifBlank { c.kindName } +
                            if (live != null) " · ${stringResource(R.string.cheats_live, live.code)}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MarioBoxColors.SecondaryCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { vm.removeCheat(index) }) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MarioBoxColors.TextTertiary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun categoryLabel(c: CheatLibrary.Category): String = when (c) {
    CheatLibrary.Category.LIVES -> "حياة"
    CheatLibrary.Category.JUMP_FLY -> "قفز وطيران"
    CheatLibrary.Category.POWER -> "الحجم والقوة"
    CheatLibrary.Category.INVINCIBILITY -> "حصانة"
    CheatLibrary.Category.WORLDS -> "العوالم"
    CheatLibrary.Category.LEVELS -> "أعداء ومراحل"
    CheatLibrary.Category.SPEED_FUN -> "سرعة ومتعة"
    CheatLibrary.Category.RAW -> "خام (تجريبي)"
}

@Composable
private fun AppliedCheatRow(c: Cheat, index: Int, vm: EmulatorViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MarioBoxColors.Surface)
            .border(1.dp, MarioBoxColors.SurfaceBorder, RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = c.enabled,
            onCheckedChange = { on -> vm.setCheatEnabled(index, on) },
            colors = CheckboxDefaults.colors(
                checkedColor = MarioBoxColors.PrimaryRed,
                uncheckedColor = MarioBoxColors.TextSecondary
            )
        )
        Column(Modifier.weight(1f)) {
            Text(
                c.code,
                style = MaterialTheme.typography.titleSmall,
                color = MarioBoxColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
            val live = vm.cheatFromCore(c.id)
            Text(
                c.description.ifBlank { c.kindName } +
                    if (live != null) " · " + stringResource(R.string.cheats_live, live.code) else "",
                style = MaterialTheme.typography.bodySmall,
                color = MarioBoxColors.SecondaryCyan,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { vm.removeCheat(index) }) {
            Icon(Icons.Filled.Delete, contentDescription = null, tint = MarioBoxColors.TextTertiary, modifier = Modifier.size(18.dp))
        }
    }
}
