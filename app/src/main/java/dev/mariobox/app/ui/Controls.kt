package dev.mariobox.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Visual style of a touch control. */
private enum class PadVariant { DPAD_ARM, ACTION, TURBO, PILL, UTILITY }

private fun variantOf(action: PadAction): PadVariant = when (action) {
    PadAction.UP, PadAction.DOWN, PadAction.LEFT, PadAction.RIGHT -> PadVariant.DPAD_ARM
    PadAction.A, PadAction.B -> PadVariant.ACTION
    PadAction.TURBO_A, PadAction.TURBO_B -> PadVariant.TURBO
    PadAction.START, PadAction.SELECT -> PadVariant.PILL
    else -> PadVariant.UTILITY
}

/**
 * The touch overlay: a professional tactile controller that mirrors a real
 * gamepad — a plus-shaped D-pad on one side, round A/B buttons on the other,
 * START/SELECT pills in the middle.
 *
 * Placement geometry comes from [PadLayout]: `cx`/`cy` are fractions of the view
 * width/height, `w`/`h` fractions of the **short edge**, so every round button is
 * a perfect circle on any screen size or orientation.
 */
@Composable
fun ControlsLayer(
    placements: Map<PadAction, Placement>,
    labelFor: (PadAction) -> String,
    onPress: (PadAction) -> Unit,
    onRelease: (PadAction) -> Unit,
    onTap: (PadAction) -> Unit,
    opacity: Float = 1f,
    scale: Float = 1f,
    enabled: Boolean = true,
    /** Per-control opacity from the custom editor (null = use the global one). */
    opacityFor: ((PadAction) -> Float)? = null,
    /** Long-press on a momentary utility button (e.g. quick-save -> quick-load). */
    onLongTap: (PadAction) -> Unit = {},
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width: Dp = maxWidth
        val height: Dp = maxHeight
        // Short-edge units make button sizes orientation-independent and square.
        val shortEdge: Dp = minOf(width, height)
        val k = scale.coerceIn(0.6f, 1.5f)

        // The plus-shaped D-pad base, drawn once under its four transparent arms.
        DpadBase(placements, width, height, shortEdge * k)

        for ((action, p) in placements) {
            val isMomentary = PadLayout.bitFor(action) == 0 && PadLayout.turboBitFor(action) == 0
            val variant = variantOf(action)

            val wDp = shortEdge * p.w * k
            val hDp = shortEdge * p.h * k
            val mod = Modifier
                .offset(x = width * p.cx - wDp / 2f, y = height * p.cy - hDp / 2f)
                .size(width = wDp, height = hDp)
            // Editor per-button opacity applies on top of the global overlay one.
            val alpha = (opacityFor?.invoke(action) ?: 1f).coerceIn(0.2f, 1f) * opacity

            if (isMomentary) {
                TapPadButton(
                    label = labelFor(action),
                    variant = variant,
                    modifier = mod,
                    alpha = alpha,
                    enabled = enabled,
                    onTap = { onTap(action) },
                    onLongTap = { onLongTap(action) }
                )
            } else {
                HoldPadButton(
                    action = action,
                    label = labelFor(action),
                    variant = variant,
                    modifier = mod,
                    alpha = alpha,
                    enabled = enabled,
                    onPress = { onPress(action) },
                    onRelease = { onRelease(action) }
                )
            }
        }
    }
}

/**
 * Draws the solid plus sign the four D-pad arms sit on. Purely decorative: the
 * arms themselves own the touch handling, so this is just one Canvas sized to the
 * bounding box of the four arm placements (works for any preset, mirrored or not).
 */
@Composable
internal fun DpadBase(
    placements: Map<PadAction, Placement>,
    width: Dp,
    height: Dp,
    shortEdge: Dp,
) {
    val up = placements[PadAction.UP] ?: return
    val down = placements[PadAction.DOWN] ?: return
    val left = placements[PadAction.LEFT] ?: return
    val right = placements[PadAction.RIGHT] ?: return
    // The arm edge in dp: used both to size the cross bars and the bbox.
    val armDp = shortEdge * up.w

    // Bounding box of the four arms in dp.
    val minX = minOf(
        width * left.cx - shortEdge * left.w / 2f,
        width * up.cx - shortEdge * up.w / 2f,
        width * right.cx - shortEdge * right.w / 2f,
        width * down.cx - shortEdge * down.w / 2f,
    )
    val maxX = maxOf(
        width * left.cx + shortEdge * left.w / 2f,
        width * up.cx + shortEdge * up.w / 2f,
        width * right.cx + shortEdge * right.w / 2f,
        width * down.cx + shortEdge * down.w / 2f,
    )
    val minY = minOf(
        height * up.cy - shortEdge * up.h / 2f,
        height * left.cy - shortEdge * left.h / 2f,
        height * right.cy - shortEdge * right.h / 2f,
        height * down.cy - shortEdge * down.h / 2f,
    )
    val maxY = maxOf(
        height * up.cy + shortEdge * up.h / 2f,
        height * left.cy + shortEdge * left.h / 2f,
        height * right.cy + shortEdge * right.h / 2f,
        height * down.cy + shortEdge * down.h / 2f,
    )
    val pad = 3.dp
    val boxW = maxX - minX + pad * 2
    val boxH = maxY - minY + pad * 2

    Box(
        modifier = Modifier
            .offset(x = minX - pad, y = minY - pad)
            .size(width = boxW, height = boxH)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Bar thickness = the arm touch-target edge (they overlap the hub).
            val arm = armDp.toPx()
            val radius = arm * 0.22f
            val baseColor = Color(0xF2141828)
            val borderColor = Color(0x883E517A)
            val ax = (w - arm) / 2f
            val ay = (h - arm) / 2f

            // Vertical bar of the plus (spans the full bounding-box height).
            drawRoundRect(
                color = baseColor,
                topLeft = Offset(ax, 0f),
                size = androidx.compose.ui.geometry.Size(arm, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
            )
            // Horizontal bar of the plus.
            drawRoundRect(
                color = baseColor,
                topLeft = Offset(0f, ay),
                size = androidx.compose.ui.geometry.Size(w, arm),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
            )
            // Soft inner border to give the plastic a bevel.
            val inset = 1.5.dp.toPx()
            drawRoundRect(
                color = borderColor,
                topLeft = Offset(ax + inset, inset),
                size = androidx.compose.ui.geometry.Size(arm - inset * 2, h - inset * 2),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                style = Stroke(width = 1.5.dp.toPx()),
            )
            drawRoundRect(
                color = borderColor,
                topLeft = Offset(inset, ay + inset),
                size = androidx.compose.ui.geometry.Size(w - inset * 2, arm - inset * 2),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                style = Stroke(width = 1.5.dp.toPx()),
            )
            // Arrows sit inside each arm, pointing away from the hub.
            val arrow = arm * 0.18f
            val cx = w / 2f
            val cy = h / 2f
            val halfW = w / 2f - arm / 2f   // distance from box centre to each arm's end
            val halfH = h / 2f - arm / 2f
            val arrowColor = Color(0xFFB9C9E8)
            listOf(
                Triple(Offset(cx, cy - halfH * 0.62f), 0f, "up"),
                Triple(Offset(cx, cy + halfH * 0.62f), 180f, "down"),
                Triple(Offset(cx - halfW * 0.62f, cy), -90f, "left"),
                Triple(Offset(cx + halfW * 0.62f, cy), 90f, "right"),
            ).forEach { (center, deg, _) ->
                val triangle = Path().apply {
                    moveTo(0f, -arrow)
                    lineTo(arrow * 0.72f, arrow * 0.55f)
                    lineTo(-arrow * 0.72f, arrow * 0.55f)
                    close()
                }
                withTransform({
                    translate(left = center.x, top = center.y)
                    rotate(deg)
                }) {
                    drawPath(triangle, color = arrowColor)
                }
            }
        }
    }
}

/**
 * A press-and-hold tactile button with glow and a slight "pressed down" scale.
 */
@Composable
private fun HoldPadButton(
    action: PadAction,
    label: String,
    variant: PadVariant,
    modifier: Modifier,
    alpha: Float = 1f,
    enabled: Boolean = true,
    onPress: () -> Unit,
    onRelease: () -> Unit
) {
    var pressed by remember(action) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    val shape = when (variant) {
        PadVariant.ACTION -> CircleShape
        PadVariant.UTILITY -> CircleShape
        PadVariant.PILL -> RoundedCornerShape(50)
        PadVariant.TURBO -> RoundedCornerShape(12.dp)
        PadVariant.DPAD_ARM -> RoundedCornerShape(10.dp)
    }

    val bgColor = when {
        variant == PadVariant.DPAD_ARM ->
            if (pressed) MarioBoxColors.SecondaryCyan.copy(alpha = 0.28f) else Color.Transparent
        variant == PadVariant.ACTION ->
            if (pressed) MarioBoxColors.PrimaryRed.copy(alpha = 0.85f) else Color(0x59FF2A4B)
        variant == PadVariant.TURBO ->
            if (pressed) MarioBoxColors.AccentAmber.copy(alpha = 0.80f) else Color(0x40FFB300)
        variant == PadVariant.PILL ->
            if (pressed) Color(0xDDFFFFFF) else Color(0x33F0F3FA)
        else ->
            if (pressed) MarioBoxColors.SecondaryCyan.copy(alpha = 0.55f) else Color(0x2B1A243B)
    }

    val borderColor = when {
        variant == PadVariant.DPAD_ARM -> Color.Transparent
        variant == PadVariant.ACTION ->
            if (pressed) MarioBoxColors.PrimaryRed else Color(0x99FF5470)
        variant == PadVariant.TURBO ->
            if (pressed) MarioBoxColors.AccentAmber else Color(0x80FFC947)
        variant == PadVariant.PILL ->
            if (pressed) Color.White else Color(0x66F0F3FA)
        else ->
            if (pressed) MarioBoxColors.SecondaryCyan else Color(0x553F537E)
    }

    val textColor = when {
        variant == PadVariant.DPAD_ARM -> Color.Transparent // arrows live on the plus base
        pressed && variant != PadVariant.PILL -> Color.White
        pressed && variant == PadVariant.PILL -> Color(0xFF10131F)
        variant == PadVariant.ACTION -> Color.White
        variant == PadVariant.TURBO -> Color(0xFFFFE9A8)
        variant == PadVariant.PILL -> Color(0xFFF0F3FA)
        else -> MarioBoxColors.TextSecondary
    }

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = tween(durationMillis = 60),
        label = "btn_scale"
    )

    Box(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha)
            .clip(shape)
            .background(bgColor)
            .then(
                if (borderColor.alpha > 0f) Modifier.border(1.5.dp, borderColor, shape)
                else Modifier
            )
            .pointerInput(action, enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    haptics.performHapticFeedback(
                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                    )
                    onPress()
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.none { it.pressed }) break
                    }
                    pressed = false
                    onRelease()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // D-pad arms are transparent touch targets: the arrows are painted on the
        // plus base, so they show no label of their own.
        if (variant != PadVariant.DPAD_ARM) {
            Text(
                text = label,
                color = textColor,
                fontWeight = FontWeight.Black,
                fontSize = when (variant) {
                    PadVariant.ACTION -> 18.sp
                    PadVariant.PILL -> 10.sp
                    PadVariant.TURBO -> 10.sp
                    PadVariant.UTILITY -> 13.sp
                    PadVariant.DPAD_ARM -> 1.sp
                },
                maxLines = 1,
            )
        }
    }
}

/** A modern glassmorphic momentary button (rewind, quick-save, fast-forward). */
@Composable
private fun TapPadButton(
    label: String,
    variant: PadVariant,
    modifier: Modifier,
    alpha: Float = 1f,
    enabled: Boolean = true,
    onTap: () -> Unit,
    onLongTap: () -> Unit = {},
) {
    var pressed by remember(label) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    val shape = if (variant == PadVariant.UTILITY) CircleShape else RoundedCornerShape(12.dp)

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = tween(durationMillis = 60),
        label = "tap_scale"
    )

    Box(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha)
            .clip(shape)
            .background(
                if (pressed) Color(0x6600E5FF) else Color(0x33141828)
            )
            .border(
                1.dp,
                if (pressed) MarioBoxColors.SecondaryCyan else Color(0x553F537E),
                shape
            )
            .pointerInput(label, enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        pressed = true
                        haptics.performHapticFeedback(
                            androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                        )
                        try {
                            awaitRelease()
                        } finally {
                            pressed = false
                        }
                    },
                    onTap = { onTap() },
                    onLongPress = {
                        onLongTap()
                    }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (pressed) Color.White else MarioBoxColors.TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
