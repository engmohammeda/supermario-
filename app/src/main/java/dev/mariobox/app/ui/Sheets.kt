package dev.mariobox.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.mariobox.app.EmulatorViewModel
import dev.mariobox.app.R
import dev.mariobox.engine.RenderSettings
import java.text.DateFormat
import java.util.Date

/**
 * Modern Cyber-Arcade Overlays: save states, cheats & settings sheets
 */
@Composable
fun SheetHost(sheet: Sheet, vm: EmulatorViewModel, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, MarioBoxColors.SurfaceBorderGlow, RoundedCornerShape(24.dp))
                    .shadow(16.dp, RoundedCornerShape(24.dp), spotColor = MarioBoxColors.PrimaryRed),
                color = MarioBoxColors.SurfaceElevated,
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Sheet Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when (sheet) {
                                    Sheet.States -> "💾"
                                    Sheet.Cheats -> "🔮"
                                    Sheet.Settings -> "⚙️"
                                },
                                fontSize = 20.sp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when (sheet) {
                                    Sheet.States -> stringResource(R.string.states_title)
                                    Sheet.Cheats -> stringResource(R.string.cheats_title)
                                    Sheet.Settings -> stringResource(R.string.settings_title)
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MarioBoxColors.TextPrimary
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MarioBoxColors.Surface)
                        ) {
                            Text("✕", color = MarioBoxColors.TextSecondary, fontSize = 14.sp)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        when (sheet) {
                            Sheet.States -> StatesSheet(vm)
                            Sheet.Cheats -> CheatsSheet(vm)
                            Sheet.Settings -> SettingsSheet(vm)
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ save states
@Composable
private fun StatesSheet(vm: EmulatorViewModel) {
    var refresh by remember { mutableStateOf(0) }
    val slots = remember(refresh) { vm.slots() }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { vm.quickSave(); refresh++ },
                colors = ButtonDefaults.buttonColors(containerColor = MarioBoxColors.PrimaryRed),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.states_quick_save), color = Color.White, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = { vm.quickLoad(); refresh++ },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.states_quick_load), color = MarioBoxColors.TextPrimary)
            }
            OutlinedButton(
                onClick = { vm.flushBattery() },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("🔋 " + stringResource(R.string.battery_flush), color = MarioBoxColors.AccentGreen)
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(slots, key = { it.index }) { slot ->
                SlotCell(
                    label = if (slot.index == 10) stringResource(R.string.states_quick_save)
                    else "خانة ${slot.index + 1}",
                    present = slot.present,
                    bytes = slot.bytes,
                    modified = slot.modified,
                    thumbnail = slot.thumbnail,
                    onSave = { vm.saveToSlot(slot.index); refresh++ },
                    onLoad = { if (slot.present) { vm.loadSlot(slot.index); refresh++ } },
                )
            }
        }
    }
}

@Composable
private fun SlotCell(
    label: String,
    present: Boolean,
    bytes: Int,
    modified: Long,
    thumbnail: android.graphics.Bitmap?,
    onSave: () -> Unit,
    onLoad: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MarioBoxColors.Surface)
            .border(
                1.dp,
                if (present) MarioBoxColors.SecondaryCyan.copy(alpha = 0.3f) else MarioBoxColors.SurfaceBorder,
                RoundedCornerShape(14.dp)
            )
            .padding(10.dp)
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Text(
                        if (present) label else stringResource(R.string.states_empty),
                        color = MarioBoxColors.TextTertiary,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MarioBoxColors.TextPrimary
            )

            if (present) {
                Text(
                    "${bytes / 1024} KB · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(modified))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MarioBoxColors.SecondaryCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    stringResource(R.string.states_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MarioBoxColors.TextTertiary
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onSave,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.states_save), fontSize = 11.sp, maxLines = 1, color = MarioBoxColors.TextPrimary)
                }
                Button(
                    onClick = onLoad,
                    enabled = present,
                    colors = ButtonDefaults.buttonColors(containerColor = MarioBoxColors.SecondaryCyan),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.states_load), fontSize = 11.sp, maxLines = 1, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ cheats
@Composable
private fun CheatsSheet(vm: EmulatorViewModel) {
    var code by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    val cheats = vm.cheats

    Column(Modifier.fillMaxSize()) {
        if (cheats.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.cheats_none),
                    color = MarioBoxColors.TextSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(cheats, key = { it.id }) { c ->
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
                            onCheckedChange = { on -> vm.setCheatEnabled(cheats.indexOf(c), on) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MarioBoxColors.PrimaryRed,
                                uncheckedColor = MarioBoxColors.TextSecondary
                            )
                        )
                        Column(Modifier.weight(1f)) {
                            Text(c.code, style = MaterialTheme.typography.titleSmall, color = MarioBoxColors.TextPrimary, fontWeight = FontWeight.Bold)
                            val live = vm.cheatFromCore(c.id)
                            Text(
                                c.description.ifBlank { c.kindName } +
                                    if (live != null) " · ${stringResource(R.string.cheats_live, live.code)}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MarioBoxColors.SecondaryCyan,
                            )
                        }
                        IconButton(onClick = { vm.removeCheat(cheats.indexOf(c)) }) {
                            Text("🗑", color = MarioBoxColors.TextTertiary)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        val preview = remember(code) { if (code.isBlank()) null else vm.decodeCheat(code) }
        Text(
            when {
                preview == null -> " "
                preview.ok -> stringResource(
                    R.string.cheats_decode_ok,
                    "0x%04X".format(preview.address),
                    preview.value,
                )
                else -> stringResource(R.string.cheats_decode_bad)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (preview != null && !preview.ok) MaterialTheme.colorScheme.error
            else MarioBoxColors.AccentGreen,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase() },
                label = { Text(stringResource(R.string.cheats_code), fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1.2f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MarioBoxColors.Surface,
                    unfocusedContainerColor = MarioBoxColors.Surface,
                    focusedBorderColor = MarioBoxColors.PrimaryRed,
                    unfocusedBorderColor = MarioBoxColors.SurfaceBorder
                )
            )
            OutlinedTextField(
                value = desc,
                onValueChange = { desc = it },
                label = { Text(stringResource(R.string.cheats_desc), fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MarioBoxColors.Surface,
                    unfocusedContainerColor = MarioBoxColors.Surface,
                    focusedBorderColor = MarioBoxColors.PrimaryRed,
                    unfocusedBorderColor = MarioBoxColors.SurfaceBorder
                )
            )
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                if (vm.addCheat(code, desc)) {
                    code = ""
                    desc = ""
                }
            },
            enabled = code.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = MarioBoxColors.PrimaryRed),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.cheats_add), color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

// ------------------------------------------------------------------ settings
@Composable
private fun SettingsSheet(vm: EmulatorViewModel) {
    val prefs = vm.prefs
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SectionHeader(stringResource(R.string.settings_video)) }
        item {
            SegmentedRow(
                stringResource(R.string.settings_scale),
                RenderSettings.scaleNames,
                prefs.scaleMode,
            ) {
                prefs.scaleMode = it
                vm.refreshPresentation()
            }
        }
        item {
            SegmentedRow(
                stringResource(R.string.settings_filter),
                RenderSettings.filterNames,
                prefs.filterMode,
            ) {
                prefs.filterMode = it
                vm.refreshPresentation()
            }
        }
        item {
            SliderRow(
                stringResource(R.string.settings_scanlines),
                prefs.scanlinesPercent.toFloat(), 0f, 100f,
            ) {
                prefs.scanlinesPercent = it.toInt()
                vm.refreshPresentation()
            }
        }
        item {
            SliderRow(
                stringResource(R.string.settings_overscan),
                prefs.overscanCrop.toFloat(), 0f, 25f,
            ) {
                prefs.overscanCrop = it.toInt()
                vm.refreshPresentation()
            }
        }
        item {
            SegmentedRow(
                stringResource(R.string.settings_rotation),
                listOf("0°", "90°", "180°"),
                when (prefs.rotation) { 90 -> 1; 180 -> 2; else -> 0 },
            ) {
                prefs.setRotationDegrees(listOf(0, 90, 180)[it])
                vm.refreshPresentation()
            }
        }

        item { SectionHeader(stringResource(R.string.settings_audio)) }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MarioBoxColors.Surface)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_audio), color = MarioBoxColors.TextPrimary, modifier = Modifier.weight(1f))
                Switch(
                    checked = prefs.audioEnabled,
                    onCheckedChange = { prefs.audioEnabled = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = MarioBoxColors.PrimaryRed)
                )
            }
        }
        item {
            SliderRow(stringResource(R.string.settings_volume), prefs.volume, 0f, 1f) {
                prefs.volume = it
                vm.refreshPresentation()
            }
        }

        item { SectionHeader(stringResource(R.string.settings_emulation)) }
        item {
            SliderRow(stringResource(R.string.settings_rewind), prefs.rewindSeconds.toFloat(), 0f, 60f) {
                prefs.rewindSeconds = it.toInt()
            }
        }
        item {
            SliderRow(stringResource(R.string.settings_runahead), prefs.runAhead.toFloat(), 0f, 3f) {
                prefs.runAhead = it.toInt()
            }
        }
        item {
            SegmentedRow(
                stringResource(R.string.settings_layout),
                listOf(
                    stringResource(R.string.layout_classic),
                    stringResource(R.string.layout_mirrored),
                    stringResource(R.string.layout_one_hand),
                ),
                prefs.overlayLayout,
            ) { prefs.overlayLayout = it }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MarioBoxColors.Surface)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_overlay), color = MarioBoxColors.TextPrimary, modifier = Modifier.weight(1f))
                Switch(
                    checked = prefs.overlayVisible,
                    onCheckedChange = { prefs.overlayVisible = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = MarioBoxColors.PrimaryRed)
                )
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MarioBoxColors.Surface)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_autosave), color = MarioBoxColors.TextPrimary, modifier = Modifier.weight(1f))
                Switch(
                    checked = prefs.autoSaveOnExit,
                    onCheckedChange = { prefs.autoSaveOnExit = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = MarioBoxColors.PrimaryRed)
                )
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MarioBoxColors.Surface)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_sram), color = MarioBoxColors.TextPrimary, modifier = Modifier.weight(1f))
                Switch(
                    checked = prefs.sramEnabled,
                    onCheckedChange = { prefs.sramEnabled = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = MarioBoxColors.PrimaryRed)
                )
            }
        }

        item { SectionHeader(stringResource(R.string.settings_core_options)) }
        val options = vm.options
        if (options.isEmpty()) {
            item {
                Text("—", color = MarioBoxColors.TextTertiary)
            }
        }
        items(options, key = { it.key }) { o ->
            OptionRow(
                title = o.description.ifBlank { o.key },
                labels = o.labels,
                currentIndex = o.currentIndex,
                onPick = { i -> o.values.getOrNull(i)?.let { v -> vm.setOption(o.key, v) } },
            )
        }
        item {
            Button(
                onClick = { vm.powerCycle() },
                colors = ButtonDefaults.buttonColors(containerColor = MarioBoxColors.PrimaryRed),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_power_cycle), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MarioBoxColors.SecondaryCyan,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun SegmentedRow(
    label: String,
    options: List<String>,
    selected: Int,
    onPick: (Int) -> Unit,
) {
    Column {
        if (label.isNotBlank()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MarioBoxColors.TextSecondary)
            Spacer(Modifier.height(4.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEachIndexed { i, name ->
                val isSelected = i == selected
                Surface(
                    onClick = { onPick(i) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) MarioBoxColors.PrimaryRed else MarioBoxColors.Surface,
                    modifier = Modifier
                        .weight(1f)
                        .border(
                            1.dp,
                            if (isSelected) MarioBoxColors.PrimaryRed else MarioBoxColors.SurfaceBorder,
                            RoundedCornerShape(10.dp)
                        )
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            name,
                            maxLines = 1,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else MarioBoxColors.TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, from: Float, to: Float, onValue: (Float) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MarioBoxColors.Surface)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MarioBoxColors.TextPrimary)
            Text(
                "${"%.0f".format(if (to <= 1f) value * 100 else value)}${if (to <= 1f) "%" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = MarioBoxColors.SecondaryCyan,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value.coerceIn(from, to),
            onValueChange = onValue,
            valueRange = from..to,
            colors = SliderDefaults.colors(
                thumbColor = MarioBoxColors.PrimaryRed,
                activeTrackColor = MarioBoxColors.PrimaryRed,
                inactiveTrackColor = MarioBoxColors.SurfaceBorder
            )
        )
    }
}

@Composable
private fun OptionRow(
    title: String,
    labels: List<String>,
    currentIndex: Int,
    onPick: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MarioBoxColors.Surface)
            .padding(10.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = MarioBoxColors.TextPrimary)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            labels.forEachIndexed { i, label ->
                val isSelected = i == currentIndex
                Surface(
                    onClick = { onPick(i) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) MarioBoxColors.SecondaryCyan else MarioBoxColors.SurfaceElevated,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            maxLines = 1,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.Black else MarioBoxColors.TextSecondary
                        )
                    }
                }
            }
        }
    }
}
