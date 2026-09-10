import re

with open("app/src/main/java/dev/mariobox/app/ui/LibraryScreen.kt", "r") as f:
    content = f.read()

target = """                    Box(
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
                    }"""
replacement = """                    Box(
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
content = content.replace(target, replacement)

target2 = """                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${selectedItem.sizeBytes / 1024} KB",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MarioBoxColors.TextSecondary
                    )"""
replacement2 = """                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val pt = dev.mariobox.app.Prefs(androidx.compose.ui.platform.LocalContext.current).getPlaytime(selectedItem.name)
                        val mins = pt / 60
                        val hrs = mins / 60
                        val ptStr = if (hrs > 0) "${hrs}h ${mins % 60}m" else "${mins}m ${pt % 60}s"
                        
                        Text(
                            "وقت اللعب: $ptStr",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MarioBoxColors.AccentAmber,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "•",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MarioBoxColors.TextSecondary
                        )
                        Text(
                            "${selectedItem.sizeBytes / 1024} KB",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MarioBoxColors.TextSecondary
                        )
                    }"""
content = content.replace(target2, replacement2)

target3 = """                            val isSelected = item == selectedItem
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
                            }"""
replacement3 = """                            val isSelected = item == selectedItem
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
content = content.replace(target3, replacement3)

with open("app/src/main/java/dev/mariobox/app/ui/LibraryScreen.kt", "w") as f:
    f.write(content)
