import re

with open("app/src/main/java/dev/mariobox/app/ui/PadLayout.kt", "r") as f:
    content = f.read()

# Update placements in classic layout
replacements = {
    "PadAction.UP to Placement(0.14f, 0.52f, 0.14f, 0.12f)": "PadAction.UP to Placement(0.14f, 0.50f, 0.12f, 0.10f)",
    "PadAction.DOWN to Placement(0.14f, 0.76f, 0.14f, 0.12f)": "PadAction.DOWN to Placement(0.14f, 0.78f, 0.12f, 0.10f)",
    "PadAction.LEFT to Placement(0.07f, 0.64f, 0.14f, 0.12f)": "PadAction.LEFT to Placement(0.06f, 0.64f, 0.12f, 0.10f)",
    "PadAction.RIGHT to Placement(0.23f, 0.64f, 0.14f, 0.12f)": "PadAction.RIGHT to Placement(0.22f, 0.64f, 0.12f, 0.10f)",
    "PadAction.B to Placement(0.83f, 0.74f, 0.14f, 0.14f)": "PadAction.B to Placement(0.81f, 0.74f, 0.12f, 0.12f)",
    "PadAction.A to Placement(0.93f, 0.58f, 0.14f, 0.14f)": "PadAction.A to Placement(0.93f, 0.58f, 0.12f, 0.12f)",
    "PadAction.TURBO_A to Placement(0.78f, 0.42f, 0.10f, 0.08f)": "PadAction.TURBO_A to Placement(0.78f, 0.42f, 0.09f, 0.07f)",
    "PadAction.TURBO_B to Placement(0.66f, 0.42f, 0.10f, 0.08f)": "PadAction.TURBO_B to Placement(0.66f, 0.42f, 0.09f, 0.07f)",
}

for old, new in replacements.items():
    content = content.replace(old, new)

with open("app/src/main/java/dev/mariobox/app/ui/PadLayout.kt", "w") as f:
    f.write(content)
