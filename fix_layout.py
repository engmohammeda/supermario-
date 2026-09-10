with open("app/src/main/java/dev/mariobox/app/ui/PadLayout.kt", "r") as f:
    content = f.read()

content = content.replace("PadAction.A -> 0.95f\n                PadAction.B -> 0.85f", "PadAction.A -> 0.93f\n                PadAction.B -> 0.83f")

with open("app/src/main/java/dev/mariobox/app/ui/PadLayout.kt", "w") as f:
    f.write(content)
