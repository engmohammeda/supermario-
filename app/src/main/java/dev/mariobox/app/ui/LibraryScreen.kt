package dev.mariobox.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.mariobox.app.Cartridge
import dev.mariobox.app.EmulatorViewModel
import dev.mariobox.app.R
import dev.mariobox.engine.INesHeader

/**
 * The ROM list: everything the user imported, plus what its own header says.
 *
 * The header line is not decoration. A mapper number and "battery" answer the two
 * questions a NES player asks before launching -- "will this save?" and "is this
 * really the board the dump claims?" -- and both are readable from 16 bytes without
 * running anything, which is why the parser lives in :engine with unit tests.
 */
@Composable
fun LibraryScreen(vm: EmulatorViewModel, onPlay: (Cartridge) -> Unit) {
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri -> vm.import(uri) { c -> vm.start(c) } }
    }

    /* Listing a directory is a stat per file, so it runs on entry, after an import
     * and after a removal -- not on every recomposition. */
    var items by remember { mutableStateOf<List<Cartridge>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var showHeader by remember { mutableStateOf<INesHeader.Info?>(null) }

    LaunchedEffect(Unit) { items = vm.library.roms }
    LaunchedEffect(vm.busy) { if (!vm.busy) items = vm.library.roms }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.library_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                Text(stringResource(R.string.library_import))
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            label = { Text(stringResource(R.string.library_search)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )

        val filtered = items.filter {
            query.isBlank() || it.name.contains(query, ignoreCase = true)
        }
        if (filtered.isEmpty()) {
            Text(
                stringResource(R.string.library_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(filtered, key = { it.path }) { c ->
                CartridgeRow(
                    cartridge = c,
                    isLast = c.path == vm.prefs.lastRomPath,
                    onPlay = { onPlay(c) },
                    onInfo = { showHeader = c.info },
                    onRemove = {
                        vm.library.remove(c)
                        items = vm.library.roms
                    },
                )
            }
        }
    }

    showHeader?.let { info ->
        AlertDialog(
            onDismissRequest = { showHeader = null },
            confirmButton = {
                TextButton(onClick = { showHeader = null }) { Text(stringResource(R.string.close)) }
            },
            text = { Text(info.summary) },
        )
    }
}

@Composable
private fun CartridgeRow(
    cartridge: Cartridge,
    isLast: Boolean,
    onPlay: () -> Unit,
    onInfo: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        color = if (isLast) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(cartridge.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    cartridge.info.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${cartridge.sizeBytes / 1024} KB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = onPlay) { Text(stringResource(R.string.library_play)) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = onInfo) { Text("iNES") }
                    OutlinedButton(onClick = onRemove) { Text(stringResource(R.string.library_remove)) }
                }
            }
        }
    }
}
