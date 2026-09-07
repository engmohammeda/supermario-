package dev.mariobox.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mariobox.app.ui.GameScreen
import dev.mariobox.app.ui.LibraryScreen
import dev.mariobox.app.ui.MarioBoxColors
import dev.mariobox.app.ui.MarioBoxTheme
import dev.mariobox.app.ui.Sheet
import dev.mariobox.app.ui.SheetHost
import dev.mariobox.engine.Cores

class MainActivity : ComponentActivity() {
    private val vm: EmulatorViewModel by viewModels()

    /** Hoisted so an ACTION_VIEW (file manager) can jump straight into the game. */
    private val showGame = androidx.compose.runtime.mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MarioBoxApp(vm, showGame = showGame.value, onShowGame = { showGame.value = it })
        }
        consumeViewIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeViewIntent(intent)
    }

    private fun consumeViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri: Uri = intent.data ?: return
        // A ROM opened from the file manager opens the game screen directly --
        // one screen, never the library sitting on top of a running emulator.
        vm.import(uri) { cartridge -> showGame.value = true }
        intent.data = null
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean =
        vm.handleKey(keyCode, true) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean =
        vm.handleKey(keyCode, false) || super.onKeyUp(keyCode, event)

    override fun onPause() {
        super.onPause()
        if (vm.cartridge != null) vm.setPaused(true)
    }
}

@Composable
fun MarioBoxApp(vm: EmulatorViewModel, showGame: Boolean, onShowGame: (Boolean) -> Unit) {
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var showLegalDialog by remember { mutableStateOf(!vm.prefs.legalAcknowledged) }
    val inGame = showGame && vm.cartridge != null

    MarioBoxTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(if (showGame) Color.Black else MarioBoxColors.Background)
        ) {
            if (inGame) {
                GameScreen(
                    vm = vm,
                    onBack = {
                        onShowGame(false)
                        vm.stopGame()
                    },
                    onSheet = { sheet = it },
                )
            } else {
                LibraryScreen(
                    vm = vm,
                    onPlay = { c ->
                        onShowGame(true)
                        vm.start(c)
                    },
                    onOpenSettings = { sheet = Sheet.Settings },
                )
            }

            sheet?.let { s ->
                SheetHost(
                    sheet = s,
                    vm = vm,
                    onDismiss = { sheet = null },
                    onSelect = { sheet = it },
                    inGame = inGame,
                )
            }

            val context = androidx.compose.ui.platform.LocalContext.current
            if (Cores.available(context).none { it == "fceumm" } && !showGame) {
                MissingCoreBanner()
            }

            if (showLegalDialog) {
                OnboardingDialog(
                    onOk = {
                        vm.prefs.legalAcknowledged = true
                        showLegalDialog = false
                    }
                )
            }
        }
    }
}

@Composable
private fun OnboardingDialog(onOk: () -> Unit) {
    AlertDialog(
        onDismissRequest = onOk,
        containerColor = MarioBoxColors.SurfaceElevated,
        shape = RoundedCornerShape(22.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎮", fontSize = 24.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MarioBoxColors.TextPrimary
                )
            }
        },
        text = {
            Text(
                text = stringResource(R.string.legal_notice),
                style = MaterialTheme.typography.bodyMedium,
                color = MarioBoxColors.TextSecondary,
                lineHeight = 22.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onOk,
                colors = ButtonDefaults.buttonColors(containerColor = MarioBoxColors.PrimaryRed),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.legal_ok),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

@Composable
private fun MissingCoreBanner() {
    Box(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp),
        Alignment.BottomCenter
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⚠️", fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.core_missing),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
