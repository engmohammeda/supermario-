package dev.mariobox.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Which full-screen panel is open over the game. */
enum class Sheet { States, Cheats, Settings, Controls }

/**
 * Premium Arcade & Cyber-Retro Palette:
 * Deep space obsidian background with arcade crimson, neon cyan, electric amber & emerald accents.
 */
object MarioBoxColors {
    val Background = Color(0xFF090A10)
    val Surface = Color(0xFF121420)
    val SurfaceElevated = Color(0xFF1A1D30)
    val SurfaceCard = Color(0xFF1E2238)
    val SurfaceBorder = Color(0xFF2E3354)
    val SurfaceBorderGlow = Color(0xFF3F4876)

    val PrimaryRed = Color(0xFFFF2A4B)
    val PrimaryRedGlow = Color(0x66FF2A4B)
    val SecondaryCyan = Color(0xFF00E5FF)
    val AccentAmber = Color(0xFFFFB300)
    val AccentGreen = Color(0xFF00E676)
    val AccentPurple = Color(0xFF9D4EDD)

    val TextPrimary = Color(0xFFF0F3FA)
    val TextSecondary = Color(0xFF9DA7C5)
    val TextTertiary = Color(0xFF636D8E)

    // Gradients
    val HeroGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF1A1429), Color(0xFF090A10))
    )
    val PrimaryGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFFFF2A4B), Color(0xFFFF5E36))
    )
    val CyanGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFF00E5FF), Color(0xFF00A3FF))
    )
    val CardGradient = Brush.linearGradient(
        colors = listOf(Color(0xFF1B1E33), Color(0xFF121422))
    )
    val ActiveCardGradient = Brush.linearGradient(
        colors = listOf(Color(0xFF291E36), Color(0xFF161726))
    )
    val GlassBrush = Brush.verticalGradient(
        colors = listOf(Color(0x33FFFFFF), Color(0x0AFFFFFF))
    )
}

val MarioBoxShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val MarioBoxTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Black,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
        color = MarioBoxColors.TextPrimary,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.2).sp,
        color = MarioBoxColors.TextPrimary,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        color = MarioBoxColors.TextPrimary,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = MarioBoxColors.TextPrimary,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = MarioBoxColors.TextPrimary,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = MarioBoxColors.TextPrimary,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = MarioBoxColors.TextSecondary,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = MarioBoxColors.TextSecondary,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        color = MarioBoxColors.TextTertiary,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.5.sp,
        color = MarioBoxColors.TextPrimary,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        color = MarioBoxColors.TextSecondary,
    ),
)

@Composable
fun MarioBoxTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary = MarioBoxColors.PrimaryRed,
        onPrimary = Color.White,
        primaryContainer = Color(0xFF4A101C),
        onPrimaryContainer = Color(0xFFFFD9DF),
        secondary = MarioBoxColors.SecondaryCyan,
        onSecondary = Color.Black,
        secondaryContainer = Color(0xFF00363F),
        onSecondaryContainer = Color(0xFFB8F5FF),
        tertiary = MarioBoxColors.AccentAmber,
        background = MarioBoxColors.Background,
        onBackground = MarioBoxColors.TextPrimary,
        surface = MarioBoxColors.Surface,
        onSurface = MarioBoxColors.TextPrimary,
        surfaceVariant = MarioBoxColors.SurfaceCard,
        onSurfaceVariant = MarioBoxColors.TextSecondary,
        outline = MarioBoxColors.SurfaceBorder,
        outlineVariant = MarioBoxColors.SurfaceBorderGlow,
        error = Color(0xFFFF5252),
        onError = Color.White,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = MarioBoxShapes,
        typography = MarioBoxTypography,
        content = content,
    )
}

/** Glassmorphic Card modifier helper */
fun Modifier.glassCard(
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    borderColor: Color = MarioBoxColors.SurfaceBorder,
    borderWidth: Dp = 1.dp,
    backgroundColor: Color = MarioBoxColors.SurfaceCard,
): Modifier = this
    .clip(shape)
    .background(backgroundColor)
    .border(borderWidth, borderColor, shape)
