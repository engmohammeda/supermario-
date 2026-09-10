import re

with open("app/src/main/java/dev/mariobox/app/ui/Controls.kt", "r") as f:
    content = f.read()

target1 = """import androidx.compose.ui.unit.sp

/** The touch overlay: a modern tactile glassmorphic arcade controller */"""
replacement1 = """import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants

/** The touch overlay: a modern tactile glassmorphic arcade controller */"""

target2 = """fun HoldButton(
    action: PadAction,
    label: String,
    isActionButton: Boolean,
    isTurboButton: Boolean,
    isDirection: Boolean,
    modifier: Modifier,
    alpha: Float = 1f,
    enabled: Boolean = true,
    onPress: () -> Unit,
    onRelease: () -> Unit,
) {
    var pressed by remember(action) { mutableStateOf(false) }"""
replacement2 = """fun HoldButton(
    action: PadAction,
    label: String,
    isActionButton: Boolean,
    isTurboButton: Boolean,
    isDirection: Boolean,
    modifier: Modifier,
    alpha: Float = 1f,
    enabled: Boolean = true,
    onPress: () -> Unit,
    onRelease: () -> Unit,
) {
    var pressed by remember(action) { mutableStateOf(false) }
    val view = LocalView.current"""

target3 = """                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    onPress()
                    while (true) {"""
replacement3 = """                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                    onPress()
                    while (true) {"""

target4 = """fun TapButton(
    label: String,
    modifier: Modifier,
    alpha: Float = 1f,
    enabled: Boolean = true,
    onTap: () -> Unit,
) {
    var pressed by remember(label) { mutableStateOf(false) }"""
replacement4 = """fun TapButton(
    label: String,
    modifier: Modifier,
    alpha: Float = 1f,
    enabled: Boolean = true,
    onTap: () -> Unit,
) {
    var pressed by remember(label) { mutableStateOf(false) }
    val view = LocalView.current"""

target5 = """            .pointerInput(label, enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    while (true) {"""
replacement5 = """            .pointerInput(label, enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                    while (true) {"""

if target1 in content:
    content = content.replace(target1, replacement1)
if target2 in content:
    content = content.replace(target2, replacement2)
if target3 in content:
    content = content.replace(target3, replacement3)
if target4 in content:
    content = content.replace(target4, replacement4)
if target5 in content:
    content = content.replace(target5, replacement5)

with open("app/src/main/java/dev/mariobox/app/ui/Controls.kt", "w") as f:
    f.write(content)
