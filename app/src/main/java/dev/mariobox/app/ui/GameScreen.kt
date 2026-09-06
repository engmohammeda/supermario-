package dev.mariobox.app.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.mariobox.app.EmulatorViewModel
import dev.mariobox.app.R
import dev.mariobox.app.rememberImmersive
import kotlinx.coroutines.delay

/**
 * The play screen: a SurfaceView the native renderer draws into, the touch overlay on
 * top of it, and a collapsible strip of chrome.
 *
 * Three decisions worth stating:
 *
 *   • The SurfaceView is *not* Compose-drawn. The emulator's own thread owns an EGL
 *     context and swaps buffers on it; a Compose `Canvas` or `AndroidImageView` would
 *     put the frame through the app's render thread, adding a copy and a frame of
 *     latency for no benefit.
 *   • The surface is handed to the engine in the holder callback and the last known
 *     surface is replayed after a successful load, because a game started from the
 *     library has usually created its SurfaceView before the core has even dlopen'd.
 *     Without that replay the first session would be a black screen — the single most
 *     annoying failure this file can have.
 *   • Chrome hides itself. On a 6-inch phone the difference between "buttons plus
 *     menu" and "just buttons" is a fifth of the picture.
 */
@Composable
fun GameScreen(vm: EmulatorViewModel, onBack: () -> Unit, onSheet: (Sheet) -> Unit) {
    val context = LocalContext.current
    rememberImmersive(true)

    DisposableEffect(Unit) {
        val activity = context as? Activity
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            vm.detachSurface()
        }
    }

    var chromeOpen by remember { mutableStateOf(false) }
    var ff by remember { mutableStateOf(false) }

    // A slow poll is enough for a HUD; the emulator thread is the one that must be fast.
    LaunchedEffect(Unit) {
        while (true) {
            vm.pollStats()
            delay(250)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(h: SurfaceHolder) = vm.attachSurface(h.surface)
                        override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {
                            vm.resizeSurface(w, hh)
                            vm.attachSurface(h.surface)
                        }

                        override fun surfaceDestroyed(h: SurfaceHolder) = vm.detachSurface()
                    })
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { chromeOpen = !chromeOpen } },
        )

        if (vm.prefs.overlayVisible) {
            val labels = OverlayLabels.map()
            ControlsLayer(
                placements = PadLayout.placements(vm.prefs.overlayLayout),
                labelFor = { action -> labels[action] ?: "?" },
                onPress = { vm.overlayPress(it, true) },
                onRelease = { vm.overlayPress(it, false) },
                onTap = { action ->
                    when (action) {
                        PadAction.REWIND -> vm.rewind(1)
                        PadAction.QUICK -> vm.quickSave()
                        PadAction.FAST_FWD -> {
                            ff = !ff
                            vm.fastForward(ff)
                        }
                        else -> Unit
                    }
                },
            )
        }

        // Left edge: the menu strip. It overlaps nothing the overlay needs.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top: 4.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            TextButton(
                onClick = { chromeOpen = !chromeOpen },
                modifier = Modifier.background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
            ) {
                Text(if (chromeOpen) "✕" else "☰", color = Color.White, fontSize = 18.sp)
            }
            if (chromeOpen) {
                ChromeButton(stringResource(R.string.back)) { onBack() }
                ChromeButton(
                    stringResource(
                        if (vm.paused) R.string.game_resumed else R.string.game_paused,
                    ),
                ) { vm.togglePause() }
                ChromeButton(stringResource(R.string.states_title)) { onSheet(Sheet.States) }
                ChromeButton(stringResource(R.string.cheats_title)) { onSheet(Sheet.Cheats) }
                ChromeButton(stringResource(R.string.settings_title)) { onSheet(Sheet.Settings) }
                ChromeButton(stringResource(R.string.battery_flush)) { vm.flushBattery() }
            }
        }

        Hud(vm, Modifier.align(Alignment.TopEnd).padding(8.dp))

        if (vm.busy) {
            Text(
                "…",
                color = Color.White,
                fontSize = 32.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        vm.toast?.let { message ->
            ToastBar(
                message,
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp),
            )
            LaunchedEffect(message) {
                delay(2600)
                vm.toast = null
            }
        }

        vm.error?.let { message ->
            LoadErrorDialog(message, onDismiss = { vm.error = null })
        }
    }
}

@Composable
private fun ChromeButton(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.padding(start = 4.dp).background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun Hud(vm: EmulatorViewModel, modifier: Modifier) {
    val s = vm.stats
    Column(
        modifier
            .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            stringResource(R.string.hud_fps, s.presentFps, s.emuSpeed * 100.0),
            color = Color.White,
            fontSize = 12.sp,
        )
        Text(
            "${s.videoWidth}x${s.videoHeight} · ${s.msPerFrame} ms · cpu ${s.cpuPercent}%",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 11.sp,
        )
        if (s.underruns > 0) {
            Text(
                stringResource(R.string.hud_underruns, s.underruns),
                color = Color(0xFFFFB74D),
                fontSize = 11.sp,
            )
        }
        if (s.rewindCapacity > 0) {
            Text(
                stringResource(R.string.hud_rewind, s.rewindAvailable),
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun ToastBar(message: String, modifier: Modifier) {
    Text(
        message,
        color = Color.White,
        fontSize = 13.sp,
        modifier = modifier
            .padding(horizontal = 16.dp)
            .background(Color(0xE6000000), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun LoadErrorDialog(message: String, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_name)) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

/**
 * Overlay labels. Compose needs these from resources (ADR-0006), and the NES button
 * names are the one place where an A/B translation would actively confuse a user, so
 * the letters stay letters and only the words are localised.
 */
private object OverlayLabels {
    /**
     * Resolved once per recomposition instead of inside the control layer: the
     * overlay is laid out by a plain (non-composable) function, and threading
     * `stringResource` through it would force every button to be a composable.
     */
    @Composable
    fun map(): Map<PadAction, String> = mapOf(
        // The letters stay letters: "A"/"B" are what the cartridge and every NES
        // cheat table mean, and localising them would break the mental map.
        PadAction.A to stringResource(R.string.btn_a),
        PadAction.B to stringResource(R.string.btn_b),
        PadAction.START to stringResource(R.string.btn_start),
        PadAction.SELECT to stringResource(R.string.btn_select),
        PadAction.TURBO_A to "A\u2022",
        PadAction.TURBO_B to "B\u2022",
        PadAction.UP to "\u2191",
        PadAction.DOWN to "\u2193",
        PadAction.LEFT to "\u2190",
        PadAction.RIGHT to "\u2192",
        PadAction.REWIND to "\u23EA",
        PadAction.QUICK to stringResource(R.string.states_quick_save),
        PadAction.FAST_FWD to "\u23E9",
    )
}
