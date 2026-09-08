package dev.mariobox.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mariobox.app.Cartridge
import dev.mariobox.app.EmulatorViewModel
import dev.mariobox.app.R
import dev.mariobox.engine.INesHeader
import kotlinx.coroutines.delay

@Composable
fun LibraryScreen(vm: EmulatorViewModel, onPlay: (Cartridge) -> Unit, onSheet: (Sheet) -> Unit) {
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            vm.import(uri) { c ->
                onPlay(c)
            }
        }
    }

    var items by remember { mutableStateOf<List<Cartridge>>(emptyList()) }
    var selectedIndex by remember { mutableStateOf(0) }
    var deleteCandidate by remember { mutableStateOf<Cartridge?>(null) }
    var deleteWithSaves by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { items = vm.library.roms }
    LaunchedEffect(vm.busy) { if (!vm.busy) items = vm.library.roms }

    // Ensure selectedIndex is valid
    LaunchedEffect(items.size) {
        if (selectedIndex >= items.size && items.isNotEmpty()) {
            selectedIndex = items.size - 1
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MarioBoxColors.Background)
    ) {
        if (items.isEmpty()) {
            EmptyLibraryState(
                onImport = { importLauncher.launch(arrayOf("*/*")) }
            )
        } else {
            val selectedCartridge = items.getOrNull(selectedIndex)
            DashboardView(
                selectedItem = selectedCartridge,
                items = items,
                onSelect = { selectedIndex = items.indexOf(it) },
                onPlay = { if (selectedCartridge != null) onPlay(selectedCartridge) },
                onSettings = { onSheet(Sheet.Settings) },
                onCheats = { onSheet(Sheet.Cheats) },
                onControls = { onSheet(Sheet.Controls) },
                onImport = { importLauncher.launch(arrayOf("*/*")) },
                onDelete = { deleteCandidate = selectedCartridge }
            )
        }

        if (vm.busy) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MarioBoxColors.PrimaryRed)
            }
        }
    }

    deleteCandidate?.let { c ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            containerColor = MarioBoxColors.SurfaceElevated,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MarioBoxColors.AccentAmber, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.delete_title), color = MarioBoxColors.TextPrimary)
                }
            },
            text = {
                Column {
                    Text(
                        stringResource(R.string.delete_body, c.name),
                        color = MarioBoxColors.TextSecondary
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { deleteWithSaves = !deleteWithSaves }
                    ) {
                        Checkbox(
                            checked = deleteWithSaves,
                            onCheckedChange = { deleteWithSaves = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MarioBoxColors.PrimaryRed,
                                uncheckedColor = MarioBoxColors.TextSecondary
                            )
                        )
                        Text(stringResource(R.string.delete_saves_too), color = MarioBoxColors.TextPrimary, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.library.remove(c, deleteWithSaves)
                        items = vm.library.roms
                        deleteCandidate = null
                        deleteWithSaves = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MarioBoxColors.PrimaryRed)
                ) {
                    Text(stringResource(R.string.delete), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text(stringResource(R.string.cancel), color = MarioBoxColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun EmptyLibraryState(onImport: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(MarioBoxColors.HeroGradient),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.VideogameAsset, contentDescription = null, tint = MarioBoxColors.PrimaryRed, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.library_empty),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "استورد ألعاب بصيغة .nes أو .zip للبدء",
            style = MaterialTheme.typography.bodyMedium,
            color = MarioBoxColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 40.dp)
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onImport,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MarioBoxColors.PrimaryRed),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.library_import), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun DashboardView(
    selectedItem: Cartridge?,
    items: List<Cartridge>,
    onSelect: (Cartridge) -> Unit,
    onPlay: () -> Unit,
    onSettings: () -> Unit,
    onCheats: () -> Unit,
    onControls: () -> Unit,
    onImport: () -> Unit,
    onDelete: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        // Blurred background effect for hero
        Box(
            Modifier
                .fillMaxSize()
                .background(MarioBoxColors.HeroGradient)
        )

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                IconButton(
                    onClick = onImport,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Import", tint = Color.White)
                }
            }

            if (selectedItem != null) {
                // Hero Area
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(220.dp)
                            .shadow(24.dp, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(MarioBoxColors.SurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.VideogameAsset, contentDescription = null, tint = MarioBoxColors.PrimaryRed, modifier = Modifier.size(72.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("NES", color = MarioBoxColors.TextSecondary, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    Text(
                        selectedItem.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${selectedItem.sizeBytes / 1024} KB",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MarioBoxColors.TextSecondary
                    )

                    Spacer(Modifier.height(32.dp))

                    // Dashboard Action Buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Play Button
                        Button(
                            onClick = onPlay,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MarioBoxColors.PrimaryRed),
                            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.library_play), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        // Utility Buttons
                        UtilityButton(Icons.Filled.Settings, onSettings)
                        UtilityButton(Icons.Filled.AutoFixHigh, onCheats)
                        UtilityButton(Icons.Filled.Gamepad, onControls)
                        UtilityButton(Icons.Filled.Delete, onDelete, tint = MarioBoxColors.TextTertiary)
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            // Bottom Carousel
            if (items.size > 1) {
                Column(Modifier.padding(bottom = 24.dp)) {
                    Text(
                        "مكتبة الألعاب",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(items) { item ->
                            val isSelected = item == selectedItem
                            Box(
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(75.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        2.dp,
                                        if (isSelected) MarioBoxColors.PrimaryRed else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .background(MarioBoxColors.Surface)
                                    .clickable { onSelect(item) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(item.name.take(3).uppercase(), color = MarioBoxColors.TextTertiary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun UtilityButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, tint: Color = Color.White) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.1f),
        modifier = Modifier.size(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

