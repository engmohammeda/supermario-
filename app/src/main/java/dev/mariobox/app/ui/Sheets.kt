package dev.mariobox.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import java.text.DateFormat
import java.util.Date

/**
 * One professional full-screen panel at a time.
 *
 * The game behind is paused while a panel is open and the panel is opaque, so the
 * user never sees "two screens" fighting: game surface + sheet. The tab row lets
 * the user switch between save states, cheats, settings and the control editor
 * without closing anything.
 */
@Composable
fun SheetHost(
    sheet: Sheet,
    vm: EmulatorViewModel,
    onDismiss: () -> Unit,
    onSelect: (Sheet) -> Unit,
) {
    // Pause while any panel is open; resume on dismiss. One screen at a time.
    DisposableEffect(Unit) {
        val wasPaused = vm.paused
        if (!wasPaused) vm.setPaused(true)
        onDispose {
            if (!wasPaused && vm.cartridge != null) vm.setPaused(false)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MarioBoxColors.HeroGradient)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.dp, MarioBoxColors.SurfaceBorderGlow, RoundedCornerShape(22.dp))
                    .shadow(16.dp, RoundedCornerShape(22.dp), spotColor = MarioBoxColors.PrimaryRed),
                color = MarioBoxColors.SurfaceElevated,
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                ) {
                    // Header -------------------------------------------------
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            stringResource(R.string.panel_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MarioBoxColors.TextPrimary
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MarioBoxColors.Surface)
                        ) {
                            Text("✕", color = MarioBoxColors.TextSecondary, fontSize = 13.sp)
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Tabs ---------------------------------------------------
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PanelTab(stringResource(R.string.states_title), "💾", sheet == Sheet.States) {
                            onSelect(Sheet.States)
                        }
                        PanelTab(stringResource(R.string.cheats_title), "🔮", sheet == Sheet.Cheats) {
                            onSelect(Sheet.Cheats)
                        }
                        PanelTab(stringResource(R.string.settings_title), "⚙️", sheet == Sheet.Settings) {
                            onSelect(Sheet.Settings)
                        }
                        PanelTab(stringResource(R.string.controls_title), "🎛", sheet == Sheet.Controls) {
                            onSelect(Sheet.Controls)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        when (sheet) {
                            Sheet.States -> StatesSheet(vm)
                            Sheet.Cheats -> CheatsScreen(vm)
                            Sheet.Settings -> SettingsScreen(
                                vm = vm,
                                onOpenControlsEditor = { onSelect(Sheet.Controls) }
                            )
                            Sheet.Controls -> ControlsEditor(vm, onDone = onDismiss)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.PanelTab(label: String, icon: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(11.dp),
        color = if (selected) MarioBoxColors.PrimaryRed else MarioBoxColors.Surface,
        modifier = Modifier
            .weight(1f)
            .border(
                1.dp,
                if (selected) MarioBoxColors.PrimaryRed else MarioBoxColors.SurfaceBorder,
                RoundedCornerShape(11.dp)
            )
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 12.sp)
            Spacer(Modifier.width(4.dp))
            Text(
                label,
                maxLines = 1,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else MarioBoxColors.TextPrimary
            )
        }
    }
}

/* ------------------------------------------------------------------ save states */
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
                Text("🔋 " + stringResource(R.string.battery_flush), color = MarioBoxColors.AccentGreen, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
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
