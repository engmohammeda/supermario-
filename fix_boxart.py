import re

with open("app/src/main/java/dev/mariobox/app/ui/LibraryScreen.kt", "r") as f:
    content = f.read()

target = """                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(200.dp)
                            .shadow(24.dp, RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(MarioBoxColors.PrimaryRed, MarioBoxColors.AccentAmber)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // Game Pattern overlay
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Gamepad, contentDescription = null, tint = Color.White, modifier = Modifier.size(80.dp))
                            Spacer(Modifier.height(16.dp))
                            Text(selectedItem.name.take(3).uppercase(), color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Black, fontSize = 24.sp, letterSpacing = 8.sp)
                        }
                    }"""
replacement = """                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val pt = dev.mariobox.app.Prefs(ctx).getPlaytime(selectedItem.name)
                    val saveDir = dev.mariobox.app.Prefs(ctx).saveDirFor(selectedItem.file)
                    val coverFile = java.io.File(saveDir, "slot0.png")
                    val hasCover = coverFile.exists()

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(200.dp)
                            .shadow(24.dp, RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(MarioBoxColors.PrimaryRed, MarioBoxColors.AccentAmber)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (hasCover) {
                            val bitmap = remember(selectedItem) { android.graphics.BitmapFactory.decodeFile(coverFile.absolutePath)?.asImageBitmap() }
                            if (bitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = bitmap,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                                // Dark gradient at the bottom for readability if needed
                                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))))
                            }
                        } else {
                            // Game Pattern overlay
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.Gamepad, contentDescription = null, tint = Color.White, modifier = Modifier.size(80.dp))
                                Spacer(Modifier.height(16.dp))
                                Text(selectedItem.name.take(3).uppercase(), color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Black, fontSize = 24.sp, letterSpacing = 8.sp)
                            }
                        }
                    }"""
content = content.replace(target, replacement)

target2 = """                        val pt = dev.mariobox.app.Prefs(androidx.compose.ui.platform.LocalContext.current).getPlaytime(selectedItem.name)
                        val mins = pt / 60"""
replacement2 = """                        val mins = pt / 60"""
content = content.replace(target2, replacement2)

target3 = """                            val isSelected = item == selectedItem
                            Box(
                                modifier = Modifier
                                    .width(120.dp)
                                    .height(80.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        2.dp,
                                        if (isSelected) MarioBoxColors.PrimaryRed else Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                MarioBoxColors.PrimaryRed.copy(alpha = if (isSelected) 0.5f else 0.2f),
                                                MarioBoxColors.Background
                                            )
                                        )
                                    )
                                    .clickable { onSelect(item) },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Filled.VideogameAsset, contentDescription = null, tint = if (isSelected) Color.White else MarioBoxColors.TextTertiary, modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        item.name, 
                                        color = if (isSelected) Color.White else MarioBoxColors.TextTertiary, 
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                }
                            }"""
replacement3 = """                            val isSelected = item == selectedItem
                            val saveDir = dev.mariobox.app.Prefs(ctx).saveDirFor(item.file)
                            val thumbFile = java.io.File(saveDir, "slot0.png")
                            
                            Box(
                                modifier = Modifier
                                    .width(120.dp)
                                    .height(80.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        2.dp,
                                        if (isSelected) MarioBoxColors.PrimaryRed else Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                MarioBoxColors.PrimaryRed.copy(alpha = if (isSelected) 0.5f else 0.2f),
                                                MarioBoxColors.Background
                                            )
                                        )
                                    )
                                    .clickable { onSelect(item) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (thumbFile.exists()) {
                                    val bitmap = remember(item) { android.graphics.BitmapFactory.decodeFile(thumbFile.absolutePath)?.asImageBitmap() }
                                    if (bitmap != null) {
                                        androidx.compose.foundation.Image(
                                            bitmap = bitmap,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                            alpha = if (isSelected) 1f else 0.5f
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (!thumbFile.exists()) {
                                        Icon(Icons.Filled.VideogameAsset, contentDescription = null, tint = if (isSelected) Color.White else MarioBoxColors.TextTertiary, modifier = Modifier.size(24.dp))
                                        Spacer(Modifier.height(4.dp))
                                    }
                                    Text(
                                        item.name, 
                                        color = if (isSelected) Color.White else MarioBoxColors.TextTertiary, 
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 8.dp).background(Color.Black.copy(alpha=0.5f), RoundedCornerShape(4.dp)).padding(horizontal=4.dp)
                                    )
                                }
                            }"""
content = content.replace(target3, replacement3)

with open("app/src/main/java/dev/mariobox/app/ui/LibraryScreen.kt", "w") as f:
    f.write(content)
