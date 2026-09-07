package dev.mariobox.app.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.mariobox.app.EmulatorViewModel
import dev.mariobox.app.R
import kotlinx.coroutines.delay

@Composable
fun rememberImmersive(immersive: Boolean) {
    val context = LocalContext.current
    DisposableEffect(immersive) {
        val window = (context as? Activity)?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (immersive) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
fun GameScreen(vm: EmulatorViewModel, onBack: () -> Unit, onSheet: (Sheet) -> Unit) {
    val context = LocalContext.current
    rememberImmersive(true)

    DisposableEffect(Unit) {
        val activity = context as? Activity
        val previous = activity?.requestedOrientation
        // Lock to landscape for the whole play session: no mid-game rotation can
        // ever leave the picture half-drawn in a portrait window.
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            vm.detachSurface()
        }
    }

    var ff by remember { mutableStateOf(false) }
    // The top chrome auto-hides so the screen shows ONE thing: the game. Tapping
    // the small floating tab brings it back.
    var chromeVisible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            vm.pollStats()
            delay(250)
        }
    }
    LaunchedEffect(chromeVisible) {
        if (chromeVisible) {
            delay(3500)
            chromeVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Core SurfaceView -- the only game screen, full bleed. The gesture
        // detector lives on this wrapper (below the controls layer): the pad
        // buttons always get first refusal of a touch, so a double-tap/long-press
        // only reaches here when it lands on the open game picture.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { vm.quickSave() },
                        onDoubleTap = { chromeVisible = !chromeVisible },
                    )
                }
        ) {
            AndroidView(
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(h: SurfaceHolder) {
                                vm.attachSurface(h.surface)
                            }
                            override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {
                                vm.resizeSurface(w, hh)
                                vm.attachSurface(h.surface)
                            }
                            override fun surfaceDestroyed(h: SurfaceHolder) {
                                vm.detachSurface()
                            }
                        })
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Custom On-Screen Virtual Controller (the real gamepad).
        if (vm.prefs.overlayVisible) {
            val labels = OverlayLabels.map()
            val customMap = vm.prefs.customControlMap()
            ControlsLayer(
                placements = PadLayout.placements(vm.prefs.overlayLayout, customMap),
                labelFor = { action -> labels[action] ?: "?" },
                opacity = vm.prefs.overlayOpacity,
                scale = vm.prefs.overlayScale,
                opacityFor = if (customMap != null) { a -> customMap[a]?.opacity ?: 1f } else null,
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
                onLongTap = { action ->
                    when (action) {
                        // Long-press the floppy to restore the quick-save state.
                        PadAction.QUICK -> vm.quickLoad()
                        else -> Unit
                    }
                }
            )
        }

        // Floating tab that brings the chrome back when it has auto-hidden.
        AnimatedVisibility(
            visible = !chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                    .background(MarioBoxColors.SurfaceElevated.copy(alpha = 0.75f))
                    .clickable { chromeVisible = true }
                    .padding(horizontal = 22.dp, vertical = 4.dp)
            ) {
                Text("⋮", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        // One clean top bar: HUD left, actions right. Auto-hidden after a few sec.
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.28f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                GameHudOverlay(vm)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QuickBarButton(
                        icon = if (vm.paused) "▶" else "⏸",
                        isHighlight = vm.paused,
                        onClick = { vm.togglePause() }
                    )
                    QuickBarButton(icon = "💾", onClick = { onSheet(Sheet.States) })
                    QuickBarButton(icon = "🔮", onClick = { onSheet(Sheet.Cheats) })
                    QuickBarButton(icon = "🎛", onClick = { onSheet(Sheet.Controls) })
                    QuickBarButton(icon = "⚙️", onClick = { onSheet(Sheet.Settings) })
                    QuickBarButton(icon = "🚪", onClick = onBack)
                }
            }
        }

        // Busy / Loading Indicator
        if (vm.busy) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MarioBoxColors.PrimaryRed)
                    Spacer(Modifier.height(12.dp))
                    Text("جاري التحميل...", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Toast Messages
        vm.toast?.let { message ->
            LuxuryToastBar(
                message = message,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp)
            )
            LaunchedEffect(message) {
                delay(2600)
                vm.toast = null
            }
        }

        // Error Dialog
        vm.error?.let { message ->
            GameErrorDialog(message = message, onDismiss = { vm.error = null })
        }
    }
}

@Composable
private fun QuickBarButton(
    icon: String,
    isHighlight: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isHighlight) MarioBoxColors.PrimaryRed.copy(alpha = 0.92f)
        else MarioBoxColors.SurfaceElevated.copy(alpha = 0.88f),
        modifier = Modifier.border(
            1.dp,
            if (isHighlight) MarioBoxColors.PrimaryRed else MarioBoxColors.SurfaceBorder,
            RoundedCornerShape(10.dp)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 13.sp)
        }
    }
}

@Composable
private fun GameHudOverlay(vm: EmulatorViewModel) {
    val stats = vm.stats
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MarioBoxColors.Surface.copy(alpha = 0.75f))
            .border(1.dp, MarioBoxColors.SurfaceBorderGlow, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${stats.presentFps.toInt()} FPS",
                color = if (stats.presentFps >= 55f) MarioBoxColors.AccentGreen else MarioBoxColors.AccentAmber,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "·",
                color = MarioBoxColors.TextTertiary,
                fontSize = 12.sp
            )
            Text(
                text = "${(stats.emuSpeed * 100).toInt()}%",
                color = MarioBoxColors.SecondaryCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            if (stats.underruns > 0) {
                Text(
                    text = "⚠️ ${stats.underruns}",
                    color = MarioBoxColors.AccentAmber,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun LuxuryToastBar(message: String, modifier: Modifier) {
    Box(
        modifier = modifier
            .shadow(12.dp, RoundedCornerShape(16.dp), spotColor = MarioBoxColors.PrimaryRed)
            .clip(RoundedCornerShape(16.dp))
            .background(MarioBoxColors.SurfaceElevated)
            .border(1.dp, MarioBoxColors.PrimaryRedGlow, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            text = message,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun GameErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MarioBoxColors.SurfaceElevated,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚠️", fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
            }
        },
        text = {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MarioBoxColors.TextPrimary
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MarioBoxColors.PrimaryRed),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.close), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}

private object OverlayLabels {
    @Composable
    fun map(): Map<PadAction, String> = mapOf(
        PadAction.A to stringResource(R.string.btn_a),
        PadAction.B to stringResource(R.string.btn_b),
        PadAction.START to stringResource(R.string.btn_start),
        PadAction.SELECT to stringResource(R.string.btn_select),
        PadAction.TURBO_A to "A⚡",
        PadAction.TURBO_B to "B⚡",
        PadAction.UP to "▲",
        PadAction.DOWN to "▼",
        PadAction.LEFT to "◀",
        PadAction.RIGHT to "▶",
        PadAction.REWIND to "⏪",
        PadAction.QUICK to "💾",
        PadAction.FAST_FWD to "⏩",
    )
}
