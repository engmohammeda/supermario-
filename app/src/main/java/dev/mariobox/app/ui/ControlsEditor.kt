package dev.mariobox.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mariobox.app.EmulatorViewModel
import dev.mariobox.app.R
import androidx.compose.ui.res.stringResource

/**
 * The custom control editor.
 *
 * Full-screen (it is a sheet of its own), it draws the real overlay at the saved
 * custom placements and lets the user drag each control, then tweak size, opacity
 * and visibility in the inspector below. Nothing is written to disk until
 * "حفظ وإغلاق" is pressed, so an accidental drag is never permanent.
 */
@Composable
fun ControlsEditor(vm: EmulatorViewModel, onDone: () -> Unit) {
    var custom by remember {
        mutableStateOf(vm.prefs.customControlMap() ?: PadLayout.defaultCustom())
    }
    var selected by remember { mutableStateOf<PadAction?>(PadAction.A) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Toolbar ---------------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🎛", fontSize = 18.sp)
                Text(
                    stringResource(R.string.controls_editor_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MarioBoxColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                OutlinedChip(
                    label = stringResource(R.string.controls_reset),
                    onClick = { custom = PadLayout.defaultCustom(); selected = PadAction.A }
                )
                Button(
                    onClick = {
                        vm.prefs.setCustomControlMap(custom)
                        vm.prefs.overlayLayout = PadLayout.PRESET_CUSTOM
                        onDone()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MarioBoxColors.PrimaryRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.controls_save), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            // Action picker ----------------------------------------------------
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(PadAction.entries) { action ->
                    val isSel = selected == action
                    Surface(
                        onClick = { selected = action },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSel) MarioBoxColors.SecondaryCyan else MarioBoxColors.Surface,
                        modifier = Modifier.border(
                            1.dp,
                            if (isSel) MarioBoxColors.SecondaryCyan else MarioBoxColors.SurfaceBorder,
                            RoundedCornerShape(10.dp)
                        )
                    ) {
                        Text(
                            action.editorLabel,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSel) Color.Black else MarioBoxColors.TextPrimary
                        )
                    }
                }
            }

            // Canvas -----------------------------------------------------------
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                val width: androidx.compose.ui.unit.Dp = maxWidth
                val height: androidx.compose.ui.unit.Dp = maxHeight
                val density = androidx.compose.ui.platform.LocalDensity.current
                val widthPx = with(density) { width.toPx() }
                val heightPx = with(density) { height.toPx() }

                // faint grid so the drag feels like a real editor
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MarioBoxColors.Background.copy(alpha = 0.35f))
                        .border(1.dp, MarioBoxColors.SurfaceBorder, RoundedCornerShape(14.dp))
                )

                for ((action, p) in custom) {
                    if (!p.visible) continue
                    val isSel = selected == action
                    val mod = Modifier
                        .offset(
                            x = width * p.cx - (width * p.w) / 2f,
                            y = height * p.cy - (height * p.h) / 2f
                        )
                        .size(width = width * p.w, height = height * p.h)
                        .pointerInput(action) {
                            detectTapGestures(onTap = { selected = action })
                        }
                        .pointerInput(action) {
                            detectDragGestures(
                                onDragStart = { selected = action },
                                onDrag = drag@ { change, amount ->
                                    change.consume()
                                    val old = custom[action] ?: return@drag
                                    val next = old.copy(
                                        cx = old.cx + amount.x / widthPx,
                                        cy = old.cy + amount.y / heightPx
                                    )
                                    custom = custom + (action to PadLayout.clamp(next))
                                }
                            )
                        }

                    EditorControl(
                        label = action.editorLabel,
                        isSelected = isSel,
                        isAction = action == PadAction.A || action == PadAction.B,
                        modifier = mod
                    )
                }

                if (selected != null) {
                    Text(
                        stringResource(R.string.controls_hint),
                        color = MarioBoxColors.TextTertiary,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                    )
                }
            }

            // Inspector ---------------------------------------------------------
            InspectorPanel(
                vm = vm,
                action = selected,
                value = selected?.let { custom[it] },
                onChanged = { action, p ->
                    custom = custom + (action to p)
                },
                onResetSpot = { action ->
                    val base = PadLayout.defaultCustom().getValue(action)
                    custom = custom + (action to base)
                }
            )
        }
    }
}

@Composable
private fun EditorControl(
    label: String,
    isSelected: Boolean,
    isAction: Boolean,
    modifier: Modifier
) {
    val shape = if (isAction) CircleShape else RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (isSelected) MarioBoxColors.SecondaryCyan.copy(alpha = 0.25f)
                else if (isAction) Color(0x33FF2A4B)
                else Color(0x2B1A243B)
            )
            .border(
                if (isSelected) 2.dp else 1.5.dp,
                if (isSelected) MarioBoxColors.SecondaryCyan
                else if (isAction) Color(0x66FF2A4B)
                else Color(0x443E517A),
                shape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (isSelected) Color.White else MarioBoxColors.TextSecondary,
            fontSize = if (isAction) 14.sp else 10.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

@Composable
private fun InspectorPanel(
    vm: EmulatorViewModel,
    action: PadAction?,
    value: ControlPlacement?,
    onChanged: (PadAction, ControlPlacement) -> Unit,
    onResetSpot: (PadAction) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MarioBoxColors.SurfaceElevated,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    ) {
        if (action == null || value == null) {
            Text(
                stringResource(R.string.controls_select_hint),
                color = MarioBoxColors.TextTertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(12.dp)
            )
            return@Surface
        }

        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.controls_selected, action.editorLabel),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MarioBoxColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = value.visible,
                    onCheckedChange = { on -> onChanged(action, value.copy(visible = on)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MarioBoxColors.PrimaryRed
                    )
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { onResetSpot(action) }) {
                    Text("↺", color = MarioBoxColors.SecondaryCyan)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                MiniSlider(
                    label = stringResource(R.string.controls_size_x),
                    value = value.w,
                    from = PadLayout.MIN_W,
                    to = PadLayout.MAX_W,
                    onValue = { v -> onChanged(action, value.copy(w = v)) },
                    modifier = Modifier.weight(1f)
                )
                MiniSlider(
                    label = stringResource(R.string.controls_size_y),
                    value = value.h,
                    from = PadLayout.MIN_H,
                    to = PadLayout.MAX_H,
                    onValue = { v -> onChanged(action, value.copy(h = v)) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(4.dp))
            MiniSlider(
                label = stringResource(R.string.controls_opacity),
                value = value.opacity,
                from = 0.2f,
                to = 1f,
                onValue = { v -> onChanged(action, value.copy(opacity = v)) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MiniSlider(
    label: String,
    value: Float,
    from: Float,
    to: Float,
    onValue: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(
            "$label — ${(value * 100).toInt()}%",
            color = MarioBoxColors.TextSecondary,
            fontSize = 10.sp
        )
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
private fun OutlinedChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MarioBoxColors.Surface,
        modifier = Modifier.border(1.dp, MarioBoxColors.SurfaceBorder, RoundedCornerShape(10.dp))
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            color = MarioBoxColors.TextSecondary,
            fontSize = 12.sp
        )
    }
}
