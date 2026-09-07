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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mariobox.app.EmulatorViewModel
import dev.mariobox.app.R
import dev.mariobox.engine.RenderSettings

/** Palette options exposed by FCEUmm; default = the authentic PPU colours. */
object MarioPalettes {
    val options = listOf(
        "rgb" to "ألوان PPU الأصلية",
        "nintendo-vc" to "Virtual Console",
        "sony-cxa2025as-us" to "CXA2025AS (US)",
        "nescap" to "NESCAP",
        "bmf-final2" to "BMF Final 2",
        "bmf-final3" to "BMF Final 3",
        "wavebeam" to "Wavebeam",
        "pal" to "PAL",
        "default" to "الافتراضية القديمة",
    )

    fun indexOf(key: String): Int = options.indexOfFirst { it.first == key }.coerceAtLeast(0)
}

/**
 * Professional, sectioned settings screen.
 *
 * Sections: العرض (with الألوان), الصوت, التحكم, المحاكاة, خيارات النواة, حول.
 * Every control writes straight to [dev.mariobox.app.Prefs] and refreshes the
 * renderer/live core where the setting needs it.
 */
@Composable
fun SettingsScreen(vm: EmulatorViewModel, onOpenControlsEditor: () -> Unit) {
    val prefs = vm.prefs
    var paletteKey by remember { mutableStateOf(prefs.palette) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ---------------------------------------------------------------- video
        item {
            SectionCard(
                icon = "🖥",
                title = stringResource(R.string.settings_video)
            ) {
                SegmentedRow(
                    stringResource(R.string.settings_scale),
                    RenderSettings.scaleNames,
                    prefs.scaleMode,
                ) {
                    prefs.scaleMode = it
                    vm.refreshPresentation()
                }
                SegmentedRow(
                    stringResource(R.string.settings_filter),
                    RenderSettings.filterNames,
                    prefs.filterMode,
                ) {
                    prefs.filterMode = it
                    vm.refreshPresentation()
                }
                SliderRow(
                    stringResource(R.string.settings_scanlines),
                    prefs.scanlinesPercent.toFloat(), 0f, 100f,
                ) {
                    prefs.scanlinesPercent = it.toInt()
                    vm.refreshPresentation()
                }
                SliderRow(
                    stringResource(R.string.settings_overscan),
                    prefs.overscanCrop.toFloat(), 0f, 25f,
                ) {
                    prefs.overscanCrop = it.toInt()
                    vm.refreshPresentation()
                }
                SliderRow(
                    stringResource(R.string.settings_zoom),
                    prefs.zoom,
                    RenderSettings.ZOOM_MIN,
                    RenderSettings.ZOOM_MAX,
                ) {
                    prefs.zoom = it
                    vm.refreshPresentation()
                }
                SegmentedRow(
                    stringResource(R.string.settings_rotation),
                    listOf("0°", "90°", "180°"),
                    when (prefs.rotation) { 90 -> 1; 180 -> 2; else -> 0 },
                ) {
                    prefs.setRotationDegrees(listOf(0, 90, 180)[it])
                    vm.refreshPresentation()
                }
            }
        }

        // ---------------------------------------------------------------- colors
        item {
            SectionCard(
                icon = "🎨",
                title = stringResource(R.string.settings_colors)
            ) {
                Text(
                    stringResource(R.string.settings_colors_hint),
                    color = MarioBoxColors.TextTertiary,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(8.dp))
                PaletteGrid(
                    selectedKey = paletteKey,
                    onPick = { key ->
                        paletteKey = key
                        vm.setPalette(key)
                    }
                )
            }
        }

        // ---------------------------------------------------------------- audio
        item {
            SectionCard(
                icon = "🔊",
                title = stringResource(R.string.settings_audio)
            ) {
                SwitchRow(stringResource(R.string.settings_audio_enabled), prefs.audioEnabled) {
                    prefs.audioEnabled = it
                }
                SliderRow(stringResource(R.string.settings_volume), prefs.volume, 0f, 1f) {
                    prefs.volume = it
                    vm.refreshPresentation()
                }
            }
        }

        // ---------------------------------------------------------------- controls
        item {
            SectionCard(
                icon = "🎮",
                title = stringResource(R.string.settings_controls_title)
            ) {
                SegmentedRow(
                    stringResource(R.string.settings_layout),
                    listOf(
                        stringResource(R.string.layout_classic),
                        stringResource(R.string.layout_mirrored),
                        stringResource(R.string.layout_one_hand),
                        stringResource(R.string.layout_custom),
                    ),
                    prefs.overlayLayout,
                ) { prefs.overlayLayout = it }

                SwitchRow(stringResource(R.string.settings_overlay), prefs.overlayVisible) {
                    prefs.overlayVisible = it
                }

                SliderRow(
                    stringResource(R.string.settings_overlay_opacity),
                    prefs.overlayOpacity, 0.2f, 1f,
                ) { prefs.overlayOpacity = it }

                SliderRow(
                    stringResource(R.string.settings_overlay_scale),
                    prefs.overlayScale, 0.6f, 1.4f,
                ) { prefs.overlayScale = it }

                SliderRow(
                    stringResource(R.string.settings_turbo),
                    prefs.turboHz.toFloat(), 5f, 60f,
                ) { prefs.turboHz = it.toInt().coerceIn(5, 60) }

                Button(
                    onClick = onOpenControlsEditor,
                    colors = ButtonDefaults.buttonColors(containerColor = MarioBoxColors.SecondaryCyan),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.settings_edit_controls),
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ---------------------------------------------------------------- emulation
        item {
            SectionCard(
                icon = "⚙️",
                title = stringResource(R.string.settings_emulation)
            ) {
                SliderRow(
                    stringResource(R.string.settings_rewind),
                    prefs.rewindSeconds.toFloat(), 0f, 60f,
                ) { prefs.rewindSeconds = it.toInt() }
                SliderRow(
                    stringResource(R.string.settings_runahead),
                    prefs.runAhead.toFloat(), 0f, 3f,
                ) { prefs.runAhead = it.toInt() }
                SwitchRow(stringResource(R.string.settings_sram), prefs.sramEnabled) {
                    prefs.sramEnabled = it
                }
                SwitchRow(stringResource(R.string.settings_autosave), prefs.autoSaveOnExit) {
                    prefs.autoSaveOnExit = it
                }
            }
        }

        // ---------------------------------------------------------------- core
        item {
            SectionCard(
                icon = "🧩",
                title = stringResource(R.string.settings_core_options)
            ) {
                val options = vm.options
                if (options.isEmpty()) {
                    Text("—", color = MarioBoxColors.TextTertiary)
                } else {
                    options.forEach { o ->
                        OptionRow(
                            title = o.description.ifBlank { o.key },
                            labels = o.labels,
                            currentIndex = o.currentIndex,
                            onPick = { i -> o.values.getOrNull(i)?.let { v -> vm.setOption(o.key, v) } },
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
                Button(
                    onClick = { vm.powerCycle() },
                    colors = ButtonDefaults.buttonColors(containerColor = MarioBoxColors.PrimaryRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_power_cycle), color = Color.White, fontWeight = FontWeight.Bold)
                }
                Text(
                    stringResource(R.string.settings_restarts),
                    color = MarioBoxColors.TextTertiary,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        // ---------------------------------------------------------------- about
        item {
            SectionCard(
                icon = "ℹ️",
                title = stringResource(R.string.settings_about)
            ) {
                AboutRow(
                    stringResource(R.string.settings_version),
                    "${dev.mariobox.app.BuildConfig.VERSION_NAME} (${dev.mariobox.app.BuildConfig.VERSION_CODE})"
                )
                AboutRow(
                    stringResource(R.string.settings_palette_note_title),
                    stringResource(R.string.settings_palette_note)
                )
                AboutRow(
                    stringResource(R.string.settings_license_title),
                    stringResource(R.string.settings_license)
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/* ---------------------------------------------------------- reusable pieces */

@Composable
fun SectionCard(icon: String, title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MarioBoxColors.CardGradient)
            .border(1.dp, MarioBoxColors.SurfaceBorder, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MarioBoxColors.PrimaryRed.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 15.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MarioBoxColors.TextPrimary
            )
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
fun SegmentedRow(
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
                        modifier = Modifier.padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            name,
                            maxLines = 1,
                            fontSize = 10.sp,
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
fun SliderRow(label: String, value: Float, from: Float, to: Float, onValue: (Float) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MarioBoxColors.Surface)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MarioBoxColors.TextPrimary)
            Text(
                "${\"%.0f\".format(if (to <= 1f) value * 100 else value)}${if (to <= 1f) "%" else ""}",
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
fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MarioBoxColors.Surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MarioBoxColors.TextPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = MarioBoxColors.PrimaryRed)
        )
    }
}

@Composable
private fun PaletteGrid(selectedKey: String, onPick: (String) -> Unit) {
    MarioPalettes.options.chunked(2).forEach { rowOptions ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            rowOptions.forEach { (key, label) ->
                val isSel = key == selectedKey
                Surface(
                    onClick = { onPick(key) },
                    shape = RoundedCornerShape(10.dp),
                    color = when {
                        isSel -> MarioBoxColors.AccentAmber
                        key == MarioPalettes.options[0].first -> MarioBoxColors.SecondaryCyan.copy(alpha = 0.12f)
                        else -> MarioBoxColors.Surface
                    },
                    modifier = Modifier
                        .weight(1f)
                        .border(
                            1.dp,
                            if (isSel) MarioBoxColors.AccentAmber else MarioBoxColors.SurfaceBorder,
                            RoundedCornerShape(10.dp)
                        )
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(vertical = 9.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1,
                        fontSize = 10.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSel) Color.Black else MarioBoxColors.TextPrimary
                    )
                }
            }
            if (rowOptions.size == 1) Spacer(Modifier.weight(1f))
        }
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

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            color = MarioBoxColors.TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.width(96.dp)
        )
        Text(
            value,
            color = MarioBoxColors.TextPrimary,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
