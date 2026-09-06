package dev.mariobox.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Which full-screen sheet is open over the game. */
enum class Sheet { States, Cheats, Settings }

/**
 * A three-colour palette and nothing else.
 *
 * The NES itself is the design language here: the chrome stays out of the way of a
 * 256x240 picture, so there is no dynamic-colour theming and no light variant --
 * a light background around a letterboxed frame would only wash out the game.
 */
@Composable
fun MarioBoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFE23636),
            onPrimary = Color.White,
            background = Color(0xFF101018),
            onBackground = Color(0xFFE8E8F0),
            surface = Color(0xCC1B1B2F),
            onSurface = Color(0xFFE8E8F0),
            surfaceVariant = Color(0xFF24243C),
            onSurfaceVariant = Color(0xFFB8B8CC),
            error = Color(0xFFFF8A80),
        ),
        content = content,
    )
}
