package dev.mariobox.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The touch overlay: a transparent layer of press-and-hold controls. */
@Composable
fun ControlsLayer(
    placements: Map<PadAction, Placement>,
    labelFor: (PadAction) -> String,
    onPress: (PadAction) -> Unit,
    onRelease: (PadAction) -> Unit,
    onTap: (PadAction) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width: Dp = maxWidth
        val height: Dp = maxHeight
        for ((action, p) in placements) {
            val isMomentary = PadLayout.bitFor(action) == 0 && PadLayout.turboBitFor(action) == 0
            val mod = Modifier
                .offset(x = width * p.cx - (width * p.w) / 2f, y = height * p.cy - (height * p.h) / 2f)
                .size(width = width * p.w, height = height * p.h)
            if (isMomentary) {
                TapButton(label = labelFor(action), modifier = mod, onTap = { onTap(action) })
            } else {
                HoldButton(label = labelFor(action), modifier = mod, onPress = { onPress(action) }, onRelease = { onRelease(action) })
            }
        }
    }
}

/**
 * A press-and-hold control.
 *
 * `awaitEachGesture` rather than `clickable`, because a NES pad needs the press and
 * release edges separately -- a tap callback arrives only after the finger lifts,
 * which would make every jump a zero-frame input -- and because holding two buttons
 * at once (run + jump, the whole reason NES games are hard) must not let one
 * gesture consume the other.
 */
@Composable
fun HoldButton(label: String, modifier: Modifier, onPress: () -> Unit, onRelease: () -> Unit) {
    var pressed by remember(label) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .background(
                if (pressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                else Color.White.copy(alpha = 0.14f),
                if (label.length > 2) RoundedCornerShape(16.dp) else CircleShape,
            )
            .pointerInput(label) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    onPress()
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.none { it.pressed }) break
                    }
                    pressed = false
                    onRelease()
                }
            }
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = if (label.length > 2) 11.sp else 16.sp,
        )
    }
}

/** A control that fires once per touch: quick save, rewind step, turbo toggle. */
@Composable
fun TapButton(label: String, modifier: Modifier, onTap: () -> Unit) {
    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .pointerInput(label) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onTap()
                    while (true) {
                        if (awaitPointerEvent().changes.none { it.pressed }) break
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
