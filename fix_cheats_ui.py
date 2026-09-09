import re

with open("app/src/main/java/dev/mariobox/app/ui/CheatsScreen.kt", "r") as f:
    content = f.read()

# Replace the empty mode block and the items block for applied cheats
target = """            if (cheats.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(34.dp), tint = MarioBoxColors.TextSecondary)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.cheats_none),
                                color = MarioBoxColors.TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                items(cheats, key = { it.id }) { c ->
                    val index = cheats.indexOf(c)
                    AppliedCheatRow(c, index, vm)
                }
            }"""

replacement = """            if (cheats.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(34.dp), tint = MarioBoxColors.TextSecondary)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.cheats_none),
                                color = MarioBoxColors.TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { vm.powerCycle() },
                            colors = ButtonDefaults.buttonColors(containerColor = MarioBoxColors.SurfaceElevated),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("إعادة تشغيل", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { vm.removeAllCheats() },
                            colors = ButtonDefaults.buttonColors(containerColor = MarioBoxColors.PrimaryRed),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("إلغاء الكل", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                items(cheats, key = { it.id }) { c ->
                    val index = cheats.indexOf(c)
                    AppliedCheatRow(c, index, vm)
                }
            }"""

if target in content:
    content = content.replace(target, replacement)
else:
    print("Target 1 not found")

target2 = """            if (cheats.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.cheats_none),
                        color = MarioBoxColors.TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                items(cheats, key = { it.id }) { c ->
                    val index = cheats.indexOf(c)
                    AppliedCheatRow(c, index, vm)
                }
            }"""
replacement2 = """            if (cheats.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.cheats_none),
                        color = MarioBoxColors.TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { vm.powerCycle() },
                            colors = ButtonDefaults.buttonColors(containerColor = MarioBoxColors.SurfaceElevated),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("إعادة تشغيل", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { vm.removeAllCheats() },
                            colors = ButtonDefaults.buttonColors(containerColor = MarioBoxColors.PrimaryRed),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("إلغاء الكل", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                items(cheats, key = { it.id }) { c ->
                    val index = cheats.indexOf(c)
                    AppliedCheatRow(c, index, vm)
                }
            }"""

if target2 in content:
    content = content.replace(target2, replacement2)
else:
    print("Target 2 not found")

# Need to import Icons.Filled.Refresh
if "import androidx.compose.material.icons.filled.Refresh" not in content:
    content = content.replace(
        "import androidx.compose.material.icons.filled.Delete",
        "import androidx.compose.material.icons.filled.Delete\nimport androidx.compose.material.icons.filled.Refresh"
    )

with open("app/src/main/java/dev/mariobox/app/ui/CheatsScreen.kt", "w") as f:
    f.write(content)
