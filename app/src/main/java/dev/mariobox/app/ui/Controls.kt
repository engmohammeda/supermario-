package dev.mariobox.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The touch overlay: a modern tactile glassmorphic arcade controller */
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
            val isActionButton = action == PadAction.A || action == PadAction.B
            val isTurboButton = action == PadAction.TURBO_A || action == PadAction.TURBO_B
            val isDirection = action == PadAction.UP || action == PadAction.DOWN || action == PadAction.LEFT || action == PadAction.RIGHT

            val mod = Modifier
                .offset(x = width * p.cx - (width * p.w) / 2f, y = height * p.cy - (height * p.h) / 2f)
                .size(width = width * p.w, height = height * p.h)

            if (isMomentary) {
                TapButton(
                    label = labelFor(action),
                    modifier = mod,
                    onTap = { onTap(action) }
                )
            } else {
                HoldButton(
                    action = action,
                    label = labelFor(action),
                    isActionButton = isActionButton,
                    isTurboButton = isTurboButton,
                    isDirection = isDirection,
                    modifier = mod,
                    onPress = { onPress(action) },
                    onRelease = { onRelease(action) }
                )
            }
        }
    }
}

/**
 * A press-and-hold tactile arcade button with glow on press.
 */
@Composable
fun HoldButton(
    action: PadAction,
    label: String,
    isActionButton: Boolean,
    isTurboButton: Boolean,
    isDirection: Boolean,
    modifier: Modifier,
    onPress: () -> Unit,
    onRelease: () -> Unit
) {
    var pressed by remember(action) { mutableStateOf(false) }

    val shape = when {
        isActionButton -> CircleShape
        isDirection -> RoundedCornerShape(12.dp)
        isTurboButton -> RoundedCornerShape(14.dp)
        else -> RoundedCornerShape(12.dp)
    }

    val glowColor by animateColorAsState(
        targetValue = when {
            pressed && isActionButton -> MarioBoxColors.PrimaryRed
            pressed && isTurboButton -> MarioBoxColors.AccentAmber
            pressed && isDirection -> MarioBoxColors.SecondaryCyan
            pressed -> Color.White
            else -> Color.Transparent
        },
        label = "btn_glow"
    )

    val bgColor = when {
        pressed && isActionButton -> MarioBoxColors.PrimaryRed.copy(alpha = 0.75f)
        pressed && isTurboButton -> MarioBoxColors.AccentAmber.copy(alpha = 0.75f)
        pressed && isDirection -> MarioBoxColors.SecondaryCyan.copy(alpha = 0.6f)
        pressed -> Color.White.copy(alpha = 0.4f)
        isActionButton -> Color(0x33FF2A4B)
        isTurboButton -> Color(0x33FFB300)
        isDirection -> Color(0x2B1A243B)
        else -> Color(0x221B1F33)
    }

    val borderColor = when {
        isActionButton -> if (pressed) MarioBoxColors.PrimaryRed else Color(0x66FF2A4B)
        isTurboButton -> if (pressed) MarioBoxColors.AccentAmber else Color(0x66FFB300)
        isDirection -> if (pressed) MarioBoxColors.SecondaryCyan else Color(0x443E517A)
        else -> Color(0x44FFFFFF)
    }

    val textColor = when {
        pressed -> Color.White
        isActionButton -> Color(0xFFFF6680)
        isTurboButton -> Color(0xFFFFD54F)
        isDirection -> Color(0xFFC0D8FF)
        else -> Color(0xFFCCCCCC)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(bgColor)
            .border(1.5.dp, borderColor, shape)
            .pointerInput(action) {
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
            color = textColor,
            fontWeight = FontWeight.Black,
            fontSize = when {
                isActionButton -> 20.sp
                isDirection -> 18.sp
                isTurboButton -> 13.sp
                else -> 11.sp
            },
        )
    }
}

/** Modern glassmorphic tactile momentary button */
@Composable
fun TapButton(label: String, modifier: Modifier, onTap: () -> Unit) {
    var pressed by remember(label) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (pressed) Color(0x5500E5FF) else Color(0x1F1A253D))
            .border(1.dp, if (pressed) MarioBoxColors.SecondaryCyan else Color(0x333F537E), RoundedCornerShape(12.dp))
            .pointerInput(label) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    onTap()
                    while (true) {
                        if (awaitPointerEvent().changes.none { it.pressed }) break
                    }
                    pressed = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (pressed) Color.White else MarioBoxColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
