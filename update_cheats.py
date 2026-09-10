import re

with open("app/src/main/java/dev/mariobox/app/ui/CheatLibrary.kt", "r") as f:
    content = f.read()

new_cheats = """        // ---- speed / fun ----------------------------------------------------
        preset("fun_fast_run", Category.SPEED_FUN, "ماريو يجري أسرع", "Mario runs fast", listOf("KILLEN")),
        preset("fun_crazy_level", Category.SPEED_FUN, "مرحلة مجنونة وقفز عالٍ", "Crazy level + high jump", listOf("SIKAEP")),
        preset("fun_every_pause", Category.SPEED_FUN, "أي زر يوقف اللعبة", "Every button pauses", listOf("OKZAPO")),
        preset("fun_cool_colors", Category.SPEED_FUN, "ألوان رائعة", "Cool colors", listOf("PINAOO")),
        preset("fun_weird_colors", Category.SPEED_FUN, "ألوان غريبة", "Weird colors", listOf("GYAEVO")),
        preset("fun_circus_music", Category.SPEED_FUN, "موسيقى سيرك", "Circus music", listOf("TVVOAE")),
        preset("fun_slow_motion", Category.SPEED_FUN, "حركة بطيئة جداً", "Super slow motion", listOf("XVKOPXXA")),
        preset("fun_blindfold", Category.SPEED_FUN, "اللعب الأعمى (تحدي)", "Blindfold challenge", listOf("IGLZYZ"), note = "الخلفية والمراحل مختفية، سترى الأعداء وماريو فقط."),
        preset("fun_bouncy", Category.SPEED_FUN, "قفزات ارتدادية مجنونة", "Bouncy jumps", listOf("XVVKEE")),

        // ---- raw (experimental) ---------------------------------------------
        preset("raw_time", Category.RAW, "تجميد الوقت (RAW)", "Freeze time", listOf("$07F8:00", "$07F9:00", "$07FA:00"), raw = true),
        preset("raw_coins", Category.RAW, "99 قرشاً دائماً (RAW)", "99 Coins", listOf("$075E:99"), raw = true),
        preset("raw_score", Category.RAW, "تصفير النقاط (RAW)", "Reset score", listOf("$07D7:00", "$07D8:00", "$07D9:00", "$07DA:00", "$07DB:00", "$07DC:00"), raw = true),
"""

target = """        // ---- speed / fun ----------------------------------------------------
        preset("fun_fast_run", Category.SPEED_FUN, "ماريو يجري أسرع", "Mario runs fast", listOf("KILLEN")),
        preset("fun_crazy_level", Category.SPEED_FUN, "مرحلة مجنونة وقفز عالٍ", "Crazy level + high jump", listOf("SIKAEP")),
        preset("fun_every_pause", Category.SPEED_FUN, "أي زر يوقف اللعبة", "Every button pauses", listOf("OKZAPO")),
        preset("fun_cool_colors", Category.SPEED_FUN, "ألوان رائعة", "Cool colors", listOf("PINAOO")),
        preset("fun_weird_colors", Category.SPEED_FUN, "ألوان غريبة", "Weird colors", listOf("GYAEVO")),
        preset("fun_circus_music", Category.SPEED_FUN, "موسيقى سيرك", "Circus music", listOf("TVVOAE")),"""

content = content.replace(target, new_cheats)

with open("app/src/main/java/dev/mariobox/app/ui/CheatLibrary.kt", "w") as f:
    f.write(content)
