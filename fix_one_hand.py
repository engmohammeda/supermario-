import re

with open("app/src/main/java/dev/mariobox/app/ui/PadLayout.kt", "r") as f:
    content = f.read()

target = """        /* One-handed portrait play: pad and buttons both in the right half, stacked
         * so a thumb sweeps between them without leaving the screen edge. */
        PRESET_ONE_HAND -> classic.mapValues { (k, p) ->
            val x = when (k) {
                PadAction.UP -> 0.60f
                PadAction.DOWN -> 0.60f
                PadAction.LEFT -> 0.51f
                PadAction.RIGHT -> 0.69f
                PadAction.A -> 0.90f
                PadAction.B -> 0.78f
                else -> p.cx
            }
            val y = when (k) {
                PadAction.UP, PadAction.A -> 0.52f
                PadAction.DOWN, PadAction.B -> 0.72f
                PadAction.LEFT, PadAction.RIGHT -> 0.62f
                else -> p.cy
            }
            p.copy(cx = x, cy = y)
        }"""
replacement = """        /* One-handed portrait play: pad and buttons both in the right half, stacked
         * so a thumb sweeps between them without leaving the screen edge. */
        PRESET_ONE_HAND -> classic.mapValues { (k, p) ->
            val x = when (k) {
                PadAction.UP -> 0.65f
                PadAction.DOWN -> 0.65f
                PadAction.LEFT -> 0.55f
                PadAction.RIGHT -> 0.75f
                PadAction.A -> 0.95f
                PadAction.B -> 0.85f
                else -> p.cx
            }
            val y = when (k) {
                PadAction.UP -> 0.60f
                PadAction.DOWN -> 0.84f
                PadAction.LEFT, PadAction.RIGHT -> 0.72f
                PadAction.A -> 0.68f
                PadAction.B -> 0.80f
                else -> p.cy
            }
            p.copy(cx = x, cy = y)
        }"""
content = content.replace(target, replacement)

with open("app/src/main/java/dev/mariobox/app/ui/PadLayout.kt", "w") as f:
    f.write(content)
