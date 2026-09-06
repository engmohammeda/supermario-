package dev.mariobox.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.activity.viewModels
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.mariobox.app.ui.GameScreen
import dev.mariobox.app.ui.LibraryScreen
import dev.mariobox.app.ui.MarioBoxTheme
import dev.mariobox.app.ui.SheetHost
import dev.mariobox.app.ui.Sheet
import dev.mariobox.engine.Cores

/**
 * The whole app is one Activity with two screens, and no navigation library.
 *
 * A launcher, a library list, a game surface and three modal sheets do not need a
 * navigation graph, and every navigation dependency in an APK that must survive an
 * emulator's surface lifecycle is another thing that can recreate the Activity
 * underneath a running core. `configChanges` in the manifest plus this structure
 * means the emulation session is created exactly once per cartridge and released
 * exactly once when the user leaves.
 */
class MainActivity : ComponentActivity() {
    private val vm: EmulatorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { MarioBoxApp(vm) }
        consumeViewIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeViewIntent(intent)
    }

    /** A `.nes` opened from a file manager: import it and go straight to it. */
    private fun consumeViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri: Uri = intent.data ?: return
        vm.import(uri) { cartridge -> vm.start(cartridge) }
        intent.data = null
    }

    /**
     * Hardware keyboards and Bluetooth gamepads both arrive here as KeyEvents. The
     * mapping is in :engine's `Gamepad` (a pure table with unit tests), and this
     * override only decides whether the event was ours.
     */
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean =
        vm.handleKey(keyCode, true) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean =
        vm.handleKey(keyCode, false) || super.onKeyUp(keyCode, event)

    /**
     * Losing focus is the moment an emulator must stop: AAudio will be taken from
     * us anyway, and a core that keeps running in the background burns battery and
     * desyncs its own audio clock. The host pauses on the next frame boundary.
     */
    override fun onPause() {
        super.onPause()
        if (vm.cartridge != null) vm.setPaused(true)
    }
}

@Composable
fun MarioBoxApp(vm: EmulatorViewModel) {
    var showGame by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }

    MarioBoxTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(if (showGame) Color.Black else MaterialTheme.colorScheme.background)
        ) {
            if (!vm.prefs.legalAcknowledged) {
                OnboardingDialog(onOk = { vm.prefs.legalAcknowledged = true })
            }

            if (showGame && vm.cartridge != null) {
                GameScreen(
                    vm = vm,
                    onBack = { showGame = false; vm.stopGame() },
                    onSheet = { sheet = it },
                )
            } else {
                LibraryScreen(
                    vm = vm,
                    onPlay = { c ->
                        showGame = true
                        vm.start(c)
                    },
                )
            }

            sheet?.let { s ->
                SheetHost(sheet = s, vm = vm, onDismiss = { sheet = null })
            }

            val context = androidx.compose.ui.platform.LocalContext.current
            if (Cores.available(context).none { it == "fceumm" } && !showGame) {
                MissingCoreBanner()
            }
        }
    }
}

@Composable
private fun OnboardingDialog(onOk: () -> Unit) {
    AlertDialog(
        onDismissRequest = { },
        confirmButton = { Button(onClick = onOk) { Text(stringResource(R.string.legal_ok)) } },
        title = { Text(stringResource(R.string.app_name)) },
        text = { Text(stringResource(R.string.legal_notice)) },
    )
}

@Composable
private fun MissingCoreBanner() {
    Box(Modifier.fillMaxSize().systemBarsPadding(), Alignment.BottomCenter) {
        Row(
            Modifier
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(12.dp)
        ) {
            Text(stringResource(R.string.core_missing), color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

/**
 * Immersive mode for as long as a game screen is on screen. Restoring on the way
 * out matters: a library list with hidden system bars is a list you cannot scroll
 * back from.
 */
@Composable
fun rememberImmersive(active: Boolean) {
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity ?: return
    DisposableEffect(active) {
        val controller = androidx.core.view.WindowCompat.getInsetsController(
            activity.window, activity.window.decorView
        )
        if (active) {
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }
}
