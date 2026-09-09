import re

with open("app/src/main/java/dev/mariobox/app/ui/ControlsEditor.kt", "r") as f:
    content = f.read()

target = """    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width: Dp = maxWidth
        val height: Dp = maxHeight
        val density = LocalDensity.current
        val widthPx = with(density) { width.toPx() }
        val heightPx = with(density) { height.toPx() }

        Column(Modifier.fillMaxSize()) {"""
replacement = """    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {"""

if target in content:
    content = content.replace(target, replacement)
else:
    print("Target 1 not found")

target2 = """            // Canvas -----------------------------------------------------------
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                // faint grid so the drag feels like a real editor
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MarioBoxColors.Background.copy(alpha = 0.35f))
                        .border(1.dp, MarioBoxColors.SurfaceBorder, RoundedCornerShape(14.dp))
                )

                for ((action, p) in custom) {"""
replacement2 = """            // Canvas -----------------------------------------------------------
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                val width: androidx.compose.ui.unit.Dp = maxWidth
                val height: androidx.compose.ui.unit.Dp = maxHeight
                val density = androidx.compose.ui.platform.LocalDensity.current
                val widthPx = with(density) { width.toPx() }
                val heightPx = with(density) { height.toPx() }

                // faint grid so the drag feels like a real editor
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MarioBoxColors.Background.copy(alpha = 0.35f))
                        .border(1.dp, MarioBoxColors.SurfaceBorder, RoundedCornerShape(14.dp))
                )

                for ((action, p) in custom) {"""

if target2 in content:
    content = content.replace(target2, replacement2)
else:
    print("Target 2 not found")

with open("app/src/main/java/dev/mariobox/app/ui/ControlsEditor.kt", "w") as f:
    f.write(content)
