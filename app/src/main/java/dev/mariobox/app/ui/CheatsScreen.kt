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
    var mode by remember { mutableStateOf(0) } // 0 = library, 1 = applied
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<CheatLibrary.Category?>(null) }
    var zoomLevel by remember { mutableStateOf(vm.prefs.zoom) }
    val cheats = vm.cheats

    Column(Modifier.fillMaxSize()) {
        // One-tap features ---------------------------------------------------
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp)
        ) {
            items(CheatLibrary.quickActions, key = { it.id }) { a ->
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

        // View features (zoom) — instant, reversible, saved with the settings. --
        Spacer(Modifier.height(6.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp)
        ) {
            items(
                listOf(
                    Triple("zoom_out", "➖ إبعاد", -0.25f),
                    Triple("zoom_in", "➕ تقريب", 0.25f),
                    Triple("zoom_reset", "1:1 إعادة", 0f),
                ),
                key = { it.first }
            ) { (id, label, delta) ->
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
                    Text(
                        "$label · ${(zoomLevel * 100).toInt()}%",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Library / applied toggle --------------------------------------------
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

        Spacer(Modifier.height(8.dp))

        if (mode == 0) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.uppercase() },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.cheats_search), fontSize = 12.sp) },
                leadingIcon = { Text("🔍", fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MarioBoxColors.Surface,
                    unfocusedContainerColor = MarioBoxColors.Surface,
                    focusedBorderColor = MarioBoxColors.PrimaryRed,
                    unfocusedBorderColor = MarioBoxColors.SurfaceBorder
                )
            )
            Spacer(Modifier.height(6.dp))
            CategoryStrip(selected = category, onPick = { category = it })
            Spacer(Modifier.height(6.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LibraryList(
                    presets = CheatLibrary.search(query).filter {
                        category == null || it.category == category
                    },
                    onApply = { vm.applyCheatCodes(it.codes, it.title, it.raw) }
                )
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AppliedCheatList(vm, cheats)
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
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        item { CategoryChip(null, "الكل", selected == null) { onPick(null) } }
        items(CheatLibrary.Category.entries) { c ->
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
                Text("🔮", fontSize = 34.sp)
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
                    Text("🗑", color = MarioBoxColors.TextTertiary)
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
