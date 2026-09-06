package dev.mariobox.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.mariobox.app.EmulatorViewModel
import dev.mariobox.app.R
import dev.mariobox.engine.RenderSettings
import java.util.Date
import java.text.DateFormat

/**
 * The three sheets that sit over the game: save states, cheats, settings.
 *
 * They are dialogs rather than destinations on purpose. A state save has to be
 * reachable while the picture is still on screen -- the thumbnail *is* the
 * confirmation -- and a settings sheet that navigates away would tear down the
 * surface and force a reload of the cartridge just to change scanline opacity.
 */
@Composable
fun SheetHost(sheet: Sheet, vm: EmulatorViewModel, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxSize().padding(8.dp)) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (sheet) {
                            Sheet.States -> stringResource(R.string.states_title)
                            Sheet.Cheats -> stringResource(R.string.cheats_title)
                            Sheet.Settings -> stringResource(R.string.settings_title)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                }
                when (sheet) {
                    Sheet.States -> StatesSheet(vm)
                    Sheet.Cheats -> CheatsSheet(vm)
                    Sheet.Settings -> SettingsSheet(vm)
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.quickSave(); refresh++ }) { Text(stringResource(R.string.states_quick_save)) }
            OutlinedButton(onClick = { vm.quickLoad(); refresh++ }) { Text(stringResource(R.string.states_quick_load)) }
            OutlinedButton(onClick = { vm.flushBattery() }) { Text(stringResource(R.string.battery_flush)) }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxSize().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(slots, key = { it.index }) { slot ->
                SlotCell(
                    label = if (slot.index == 10) stringResource(R.string.states_quick_save)
                    else "${slot.index + 1}",
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
    Card {
        Column(Modifier.padding(6.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f)) {
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            Text(label, style = MaterialTheme.typography.titleSmall)
            if (present) {
                Text(
                    "${bytes / 1024} KB · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(modified))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.states_save)) }
                Button(onClick = onLoad, enabled = present, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.states_load)) }
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
            Text(
                stringResource(R.string.cheats_none),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(cheats, key = { it.id }) { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = c.enabled,
                            onCheckedChange = { on -> vm.setCheatEnabled(cheats.indexOf(c), on) },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(c.code, style = MaterialTheme.typography.titleSmall)
                            val live = vm.cheatFromCore(c.id)
                            Text(
                                c.description.ifBlank { c.kindName } +
                                    if (live != null) " · ${stringResource(R.string.cheats_live, live.code)}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { vm.removeCheat(cheats.indexOf(c)) }) { Text("✕") }
                    }
                }
            }
        }

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
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase() },
                label = { Text(stringResource(R.string.cheats_code)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = desc,
                onValueChange = { desc = it },
                label = { Text(stringResource(R.string.cheats_desc)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Button(
            onClick = {
                if (vm.addCheat(code, desc)) {
                    code = ""
                    desc = ""
                }
            },
            enabled = code.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text(stringResource(R.string.cheats_add)) }
    }
}

// ------------------------------------------------------------------ settings
@Composable
private fun SettingsSheet(vm: EmulatorViewModel) {
    val prefs = vm.prefs
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_audio), modifier = Modifier.weight(1f))
                    Switch(
                        checked = prefs.audioEnabled,
                        onCheckedChange = { prefs.audioEnabled = it },
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_layout), modifier = Modifier.weight(1f))
                    SegmentedRow(
                        "",
                        listOf(
                            stringResource(R.string.layout_classic),
                            stringResource(R.string.layout_mirrored),
                            stringResource(R.string.layout_one_hand),
                        ),
                        prefs.overlayLayout,
                    ) { prefs.overlayLayout = it }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_overlay), modifier = Modifier.weight(1f))
                    Switch(checked = prefs.overlayVisible, onCheckedChange = { prefs.overlayVisible = it })
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_autosave), modifier = Modifier.weight(1f))
                    Switch(checked = prefs.autoSaveOnExit, onCheckedChange = { prefs.autoSaveOnExit = it })
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_sram), modifier = Modifier.weight(1f))
                    Switch(checked = prefs.sramEnabled, onCheckedChange = { prefs.sramEnabled = it })
                }
            }

            item { SectionHeader(stringResource(R.string.settings_core_options)) }
            item {
                Text(
                    stringResource(R.string.settings_restarts),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val options = vm.options
            if (options.isEmpty()) {
                item {
                    Text(
                        "—",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                OutlinedButton(onClick = { vm.powerCycle() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_power_cycle))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
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
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEachIndexed { i, name ->
                if (i == selected) {
                    Button(onClick = { onPick(i) }) { Text(name, maxLines = 1) }
                } else {
                    OutlinedButton(onClick = { onPick(i) }) { Text(name, maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, from: Float, to: Float, onValue: (Float) -> Unit) {
    Column {
        Text("$label: ${"%.0f".format(if (to <= 1f) value * 100 else value)}${if (to <= 1f) "%" else ""}")
        Slider(
            value = value.coerceIn(from, to),
            onValueChange = onValue,
            valueRange = from..to,
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        // A wrapped list of small buttons rather than a dropdown: core option tables
        // are 40+ rows long and a spinner inside a scrolling sheet is a finger trap.
        labels.forEachIndexed { i, label ->
            if (i == currentIndex) {
                Button(onClick = { onPick(i) }, modifier = Modifier.padding(start = 2.dp)) {
                    Text(label, maxLines = 1, style = MaterialTheme.typography.labelSmall)
                }
            } else {
                TextButton(onClick = { onPick(i) }, modifier = Modifier.padding(start = 2.dp)) {
                    Text(label, maxLines = 1, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
