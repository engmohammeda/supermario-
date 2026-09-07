package dev.mariobox.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
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
fun LibraryScreen(
    vm: EmulatorViewModel,
    onPlay: (Cartridge) -> Unit,
    onOpenSettings: () -> Unit = {},
) {
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
    var query by remember { mutableStateOf("") }
    var selectedInfoCartridge by remember { mutableStateOf<Cartridge?>(null) }
    var deleteCandidate by remember { mutableStateOf<Cartridge?>(null) }
    var deleteWithSaves by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { items = vm.library.roms }
    LaunchedEffect(vm.busy) { if (!vm.busy) items = vm.library.roms }

    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MarioBoxColors.HeroGradient)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Bar Header with brand glow
            HeaderSection(
                totalCount = items.size,
                onImport = { importLauncher.launch(arrayOf("*/*")) },
                onSettings = onOpenSettings
            )

            // Search & Filter Box
            SearchSection(
                query = query,
                onQueryChange = { query = it },
                onClear = { query = "" }
            )

            val filtered = items.filter {
                query.isBlank() || it.name.contains(query, ignoreCase = true)
            }

            val lastPlayed = items.firstOrNull { it.path == vm.prefs.lastRomPath }

            if (items.isEmpty()) {
                EmptyStateView(onImport = { importLauncher.launch(arrayOf("*/*")) })
            } else if (filtered.isEmpty()) {
                NoSearchResultsView(query = query)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Quick Resume Hero banner if last played matches
                    if (query.isBlank() && lastPlayed != null) {
                        item(key = "hero_last_played") {
                            FeaturedCartridgeCard(
                                cartridge = lastPlayed,
                                onPlay = { onPlay(lastPlayed) },
                                onInfo = { selectedInfoCartridge = lastPlayed }
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.library_all_games),
                                style = MaterialTheme.typography.titleSmall,
                                color = MarioBoxColors.TextSecondary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                            )
                        }
                    }

                    items(filtered, key = { it.path }) { cartridge ->
                        val cover = remember(cartridge.path) { vm.library.loadThumbnail(cartridge) }
                        CartridgeCard(
                            cartridge = cartridge,
                            isLastPlayed = cartridge.path == vm.prefs.lastRomPath,
                            cover = cover,
                            onPlay = { onPlay(cartridge) },
                            onInfo = { selectedInfoCartridge = cartridge },
                            onDelete = {
                                deleteCandidate = cartridge
                                deleteWithSaves = false
                            }
                        )
                    }
                }
            }
        }

        // Info Dialog
        selectedInfoCartridge?.let { c ->
            CartridgeInfoDialog(
                cartridge = c,
                onDismiss = { selectedInfoCartridge = null },
                onPlay = {
                    selectedInfoCartridge = null
                    onPlay(c)
                }
            )
        }

        // Delete Confirmation Dialog
        deleteCandidate?.let { c ->
            DeleteConfirmDialog(
                cartridge = c,
                deleteWithSaves = deleteWithSaves,
                onToggleSaves = { deleteWithSaves = it },
                onConfirm = {
                    vm.library.remove(c, deleteSaves = deleteWithSaves)
                    items = vm.library.roms
                    deleteCandidate = null
                },
                onDismiss = { deleteCandidate = null }
            )
        }

        // Busy / Loading Overlay
        if (vm.busy) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MarioBoxColors.PrimaryRed)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "جاري استيراد اللعبة...",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }

        // Toast Messages
        vm.toast?.let { message ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .shadow(12.dp, RoundedCornerShape(16.dp), spotColor = MarioBoxColors.PrimaryRed)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MarioBoxColors.SurfaceElevated)
                    .border(1.dp, MarioBoxColors.PrimaryRedGlow, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            LaunchedEffect(message) {
                delay(2600)
                vm.toast = null
            }
        }

        // Error Dialog
        vm.error?.let { message ->
            AlertDialog(
                onDismissRequest = { vm.error = null },
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
                        onClick = { vm.error = null },
                        colors = ButtonDefaults.buttonColors(containerColor = MarioBoxColors.PrimaryRed),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(stringResource(R.string.close), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

@Composable
private fun HeaderSection(totalCount: Int, onImport: () -> Unit, onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MarioBoxColors.PrimaryRed)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MarioBoxColors.TextPrimary
                )
            }
            Text(
                text = stringResource(R.string.library_total_count, totalCount),
                style = MaterialTheme.typography.bodySmall,
                color = MarioBoxColors.SecondaryCyan,
                fontWeight = FontWeight.SemiBold
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Settings — display & control customization from the main screen.
            Surface(
                onClick = onSettings,
                shape = RoundedCornerShape(14.dp),
                color = MarioBoxColors.SurfaceElevated,
                modifier = Modifier
                    .size(40.dp)
                    .border(1.dp, MarioBoxColors.SurfaceBorderGlow, RoundedCornerShape(14.dp))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("⚙️", fontSize = 18.sp)
                }
            }

            // Action Button: Add ROM
            Surface(
                onClick = onImport,
                shape = RoundedCornerShape(14.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .shadow(8.dp, RoundedCornerShape(14.dp), spotColor = MarioBoxColors.PrimaryRed)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MarioBoxColors.PrimaryGradient)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "＋",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.library_import),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchSection(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        placeholder = {
            Text(
                stringResource(R.string.library_search),
                color = MarioBoxColors.TextTertiary,
                fontSize = 14.sp
            )
        },
        leadingIcon = {
            Text("🔍", fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Text("✕", color = MarioBoxColors.TextSecondary, fontSize = 14.sp)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MarioBoxColors.SurfaceElevated,
            unfocusedContainerColor = MarioBoxColors.Surface,
            focusedBorderColor = MarioBoxColors.SecondaryCyan,
            unfocusedBorderColor = MarioBoxColors.SurfaceBorder,
            focusedTextColor = MarioBoxColors.TextPrimary,
            unfocusedTextColor = MarioBoxColors.TextPrimary,
            cursorColor = MarioBoxColors.SecondaryCyan
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
    )
}

@Composable
private fun FeaturedCartridgeCard(
    cartridge: Cartridge,
    onPlay: () -> Unit,
    onInfo: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF331626), Color(0xFF14172B))
                )
            )
            .border(1.dp, MarioBoxColors.PrimaryRedGlow, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "⭐ " + stringResource(R.string.library_recent),
                        style = MaterialTheme.typography.labelMedium,
                        color = MarioBoxColors.AccentAmber,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    onClick = onInfo,
                    shape = RoundedCornerShape(8.dp),
                    color = MarioBoxColors.SurfaceElevated
                ) {
                    Text(
                        text = "ℹ",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        color = MarioBoxColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = cartridge.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MarioBoxColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = cartridge.info.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MarioBoxColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(14.dp))

            Surface(
                onClick = onPlay,
                shape = RoundedCornerShape(12.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MarioBoxColors.PrimaryGradient)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("▶", color = Color.White, fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.library_play_now),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CartridgeCard(
    cartridge: Cartridge,
    isLastPlayed: Boolean,
    cover: android.graphics.Bitmap?,
    onPlay: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isLastPlayed) MarioBoxColors.PrimaryRedGlow else MarioBoxColors.SurfaceBorder,
        label = "border_color"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MarioBoxColors.CardGradient)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onPlay() }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover art: the real game thumbnail captured from play, falling back
            // to a cartridge badge for games that have never run.
            Box(
                modifier = Modifier
                    .size(width = 58.dp, height = 46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black)
                    .border(
                        1.dp,
                        if (isLastPlayed) MarioBoxColors.PrimaryRed.copy(alpha = 0.6f)
                        else MarioBoxColors.SurfaceBorderGlow,
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (cover != null) {
                    Image(
                        bitmap = cover.asImageBitmap(),
                        contentDescription = cartridge.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(text = "🎮", fontSize = 20.sp)
                }
            }

            Spacer(Modifier.width(14.dp))

            // Game Name & Metadata Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = cartridge.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MarioBoxColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (cartridge.hasBattery) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MarioBoxColors.AccentGreen.copy(alpha = 0.15f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "🔋 SRAM",
                                fontSize = 9.sp,
                                color = MarioBoxColors.AccentGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "M${cartridge.info.mapper} · ${cartridge.info.prg16k * 16}K PRG · ${if (cartridge.info.chr8k > 0) "${cartridge.info.chr8k * 8}K CHR" else "CHR RAM"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MarioBoxColors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            // Action Buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Play Icon Button
                IconButton(
                    onClick = onPlay,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MarioBoxColors.PrimaryRed.copy(alpha = 0.15f))
                ) {
                    Text("▶", color = MarioBoxColors.PrimaryRed, fontSize = 14.sp)
                }

                Spacer(Modifier.width(4.dp))

                // Info Action
                IconButton(
                    onClick = onInfo,
                    modifier = Modifier.size(34.dp)
                ) {
                    Text("ℹ", color = MarioBoxColors.TextSecondary, fontSize = 14.sp)
                }

                // Delete Action
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(34.dp)
                ) {
                    Text("🗑", color = MarioBoxColors.TextTertiary, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun EmptyStateView(onImport: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(MarioBoxColors.SurfaceElevated)
                    .border(1.dp, MarioBoxColors.SurfaceBorderGlow, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("🕹️", fontSize = 42.sp)
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.library_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MarioBoxColors.TextPrimary
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.library_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MarioBoxColors.TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(24.dp))

            Surface(
                onClick = onImport,
                shape = RoundedCornerShape(14.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .shadow(12.dp, RoundedCornerShape(14.dp), spotColor = MarioBoxColors.PrimaryRed)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MarioBoxColors.PrimaryGradient)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("＋", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.library_import),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun NoSearchResultsView(query: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🔍", fontSize = 36.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "لا توجد نتائج لـ \"$query\"",
                style = MaterialTheme.typography.titleMedium,
                color = MarioBoxColors.TextSecondary
            )
        }
    }
}

@Composable
private fun CartridgeInfoDialog(
    cartridge: Cartridge,
    onDismiss: () -> Unit,
    onPlay: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MarioBoxColors.SurfaceElevated,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🕹️", fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = cartridge.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MarioBoxColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow("Mapper", "${cartridge.info.mapper}")
                InfoRow("PRG ROM", "${cartridge.info.prg16k * 16} KB")
                InfoRow("CHR", if (cartridge.info.chr8k > 0) "${cartridge.info.chr8k * 8} KB" else "RAM")
                InfoRow("Mirroring", cartridge.info.mirroring.label)
                InfoRow("Battery Save", if (cartridge.info.battery) "Yes (SRAM)" else "No")
                InfoRow("File Size", "${cartridge.sizeBytes / 1024} KB")
                InfoRow("Path", cartridge.file.name)
            }
        },
        confirmButton = {
            Button(
                onClick = onPlay,
                colors = ButtonDefaults.buttonColors(containerColor = MarioBoxColors.PrimaryRed),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.library_play), color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close), color = MarioBoxColors.TextSecondary)
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MarioBoxColors.Surface)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MarioBoxColors.TextSecondary)
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            color = MarioBoxColors.TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun DeleteConfirmDialog(
    cartridge: Cartridge,
    deleteWithSaves: Boolean,
    onToggleSaves: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MarioBoxColors.SurfaceElevated,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                stringResource(R.string.library_delete_confirm),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MarioBoxColors.TextPrimary
            )
        },
        text = {
            Column {
                Text(
                    text = cartridge.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MarioBoxColors.TextSecondary
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MarioBoxColors.Surface)
                        .clickable { onToggleSaves(!deleteWithSaves) }
                        .padding(8.dp)
                ) {
                    Checkbox(
                        checked = deleteWithSaves,
                        onCheckedChange = onToggleSaves,
                        colors = CheckboxDefaults.colors(
                            checkedColor = MarioBoxColors.PrimaryRed,
                            uncheckedColor = MarioBoxColors.TextSecondary
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.library_delete_with_saves),
                        style = MaterialTheme.typography.bodySmall,
                        color = MarioBoxColors.TextPrimary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.delete), color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = MarioBoxColors.TextSecondary)
            }
        }
    )
}
