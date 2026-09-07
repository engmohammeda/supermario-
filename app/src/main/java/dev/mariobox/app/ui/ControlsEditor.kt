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
import androidx.compose.runtime.rememberUpdatedState
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
import kotlin.math.min

/**
 * The custom control editor.
 *
 * The canvas is its own measured [BoxWithConstraints]: every button inside is
 * positioned with the *same geometry the real overlay uses* — `cx`/`cy` as
 * fractions of the canvas width/height, `w`/`h` as fractions of its short edge.
 * That is what makes a drag land where the finger is (the old editor measured the
 * whole sheet and the canvas was a weighted slice of it, so placements drifted).
 * Nothing is written to disk until "حفظ وإغلاق" is pressed.
 */
@Composable
fun ControlsEditor(vm: EmulatorViewModel, onDone: () -> Unit) {
    var custom by remember {
        mutableStateOf(vm.prefs.customControlMap() ?: PadLayout.defaultCustom())
    }
    var selected by remember { mutableStateOf<PadAction?>(PadAction.A) }

    Column(Modifier.fillMaxSize()) {
        // Toolbar -------------------------------------------------------------
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

        // Action picker --------------------------------------------------------
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

        // Canvas ---------------------------------------------------------------
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xF0090A10))
                .border(1.dp, MarioBoxColors.SurfaceBorder, RoundedCornerShape(14.dp))
        ) {
            EditorCanvas(
                custom = custom,
                selected = selected,
                onSelect = { selected = it },
                onMove = { action, p ->
                    custom = custom + (action to p)
                }
            )

            if (selected != null) {
                Text(
                    stringResource(R.string.controls_hint),
                    color = MarioBoxColors.TextTertiary,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                )
            }
        }

        // Inspector ------------------------------------------------------------
        InspectorPanel(
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

/**
 * The drag surface. Self-measured: all geometry inside uses only the canvas's own
 * width/height, matching [ControlsLayer] 1:1.
 */
@Composable
private fun EditorCanvas(
    custom: Map<PadAction, ControlPlacement>,
    selected: PadAction?,
    onSelect: (PadAction) -> Unit,
    onMove: (PadAction, ControlPlacement) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width: Dp = maxWidth
        val height: Dp = maxHeight
        val density = LocalDensity.current
        val widthPx = with(density) { width.toPx() }
        val heightPx = with(density) { height.toPx() }
        val shortPx = min(widthPx, heightPx)
        val aspect = PadLayout.shortOverLong(widthPx, heightPx)
        // Keep the drag handler seeing the freshest map without restarting the
        // pointerInput capture mid-gesture.
        val currentCustom by rememberUpdatedState(custom)
        val shortDp = minOf(width, height)

        // Faint game-screen guide (a 4:3/16:9 safe zone) so buttons can be kept
        // clear of where the picture sits.
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxSize(0.72f)
                .border(
                    1.dp,
                    MarioBoxColors.SurfaceBorder.copy(alpha = 0.6f),
                    RoundedCornerShape(6.dp)
                )
        )

        // The same plus-shaped base the real overlay draws under the arms.
        DpadBase(
            placements = currentCustom
                .filterValues { it.visible }
                .mapValues { (_, p) -> Placement(p.cx, p.cy, p.w, p.h) },
            width = width,
            height = height,
            shortEdge = shortDp,
        )

        for ((action, p) in custom) {
            if (!p.visible) continue
            val isSel = selected == action
            val wDp = with(density) { (shortPx * p.w).toDp() }
            val hDp = with(density) { (shortPx * p.h).toDp() }
            val mod = Modifier
                .offset(
                    x = width * p.cx - wDp / 2f,
                    y = height * p.cy - hDp / 2f
                )
                .size(width = wDp, height = hDp)
                .pointerInput(action) {
                    detectTapGestures(onTap = { onSelect(action) })
                }
                .pointerInput(action, widthPx, heightPx) {
                    detectDragGestures(
                        onDragStart = { onSelect(action) },
                        onDrag = drag@ { change, amount ->
                            change.consume()
                            val old = currentCustom[action] ?: return@drag
                            val next = old.copy(
                                cx = old.cx + amount.x / widthPx,
                                cy = old.cy + amount.y / heightPx
                            )
                            onMove(action, PadLayout.clamp(next, aspect))
                        }
                    )
                }

            EditorControl(
                label = action.editorLabel,
                isSelected = isSel,
                isAction = action == PadAction.A || action == PadAction.B,
                isDpad = PadLayout.bitFor(action).let {
                    it == dev.mariobox.engine.Gamepad.UP || it == dev.mariobox.engine.Gamepad.DOWN ||
                        it == dev.mariobox.engine.Gamepad.LEFT || it == dev.mariobox.engine.Gamepad.RIGHT
                },
                modifier = mod
            )
        }
    }
}

@Composable
private fun EditorControl(
    label: String,
    isSelected: Boolean,
    isAction: Boolean,
    isDpad: Boolean,
    modifier: Modifier
) {
    val shape = when {
        isAction -> CircleShape
        else -> RoundedCornerShape(10.dp)
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                when {
                    isSelected -> MarioBoxColors.SecondaryCyan.copy(alpha = 0.30f)
                    isDpad -> Color(0x22B9C9E8)
                    isAction -> Color(0x40FF2A4B)
                    else -> Color(0x2B1A243B)
                }
            )
            .border(
                if (isSelected) 2.dp else 1.5.dp,
                when {
                    isSelected -> MarioBoxColors.SecondaryCyan
                    isAction -> Color(0x80FF5470)
                    isDpad -> Color(0x66B9C9E8)
                    else -> Color(0x553E517A)
                },
                shape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (isSelected) Color.White else MarioBoxColors.TextSecondary,
            fontSize = if (isAction) 13.sp else 9.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

@Composable
private fun InspectorPanel(
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

            // One square size knob: size is short-edge-based, so a single value
            // keeps round buttons round.
            MiniSlider(
                label = stringResource(R.string.controls_size),
                value = ((value.w + value.h) / 2f),
                from = PadLayout.MIN_W,
                to = PadLayout.MAX_W,
                onValue = { v -> onChanged(action, value.copy(w = v, h = v)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(2.dp))
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
