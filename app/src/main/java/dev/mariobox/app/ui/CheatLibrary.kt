package dev.mariobox.app.ui

/**
 * Curated cheat library for the Super Mario Bros. family.
 *
 * This is intentionally a *data* table (the PLAN §5.5 file it replaces), not UI
 * strings: titles and notes are content, so they live with the codes. The codes
 * themselves are classic, community-documented Game Genie codes for the USA/World
 * SMB1 cart (they only affect that cartridge -- the library says so in the UI) plus
 * a small set of RAW RAM writes implemented by the host's own poke engine for the
 * features the user asked for (small/big/fire and flying).
 *
 * RAW cheats target SMB1 CPU RAM, verified against the public SMB1 disassembly:
 *    $0756 PlayerStatus: 0 = small, 1 = big, 2 = fiery
 *    $075A NumberOfLives (active player)
 *    $075F WorldNumber (0-based)
 */
object CheatLibrary {

    enum class Category { LIVES, JUMP_FLY, POWER, INVINCIBILITY, WORLDS, LEVELS, SPEED_FUN, RAW }

    data class CheatPreset(
        val id: String,
        val category: Category,
        val title: String,
        val titleEn: String,
        val codes: List<String>,
        val note: String = "",
        val raw: Boolean = false,
    ) {
        /** Human text for one row in the library. */
        val codeText: String
            get() = if (codes.size == 1) codes[0] else codes.joinToString(" + ")
    }

    data class QuickAction(
        val id: String,
        val title: String,
        val codes: List<String>,
        val raw: Boolean = false,
        val note: String = "",
        /** Optional view zoom applied together with the codes (e.g. "نار + تقريب"). */
        val zoom: Float? = null,
    )

    /** The classic SMB1 "good stuff", most of it from the community code list. */
    val cheats: List<CheatPreset> = listOf(
        // ---- lives ----------------------------------------------------------
        preset("lives_start1", Category.LIVES, "البدء بحياة واحدة", "Start with 1 life", listOf("AATOZA")),
        preset("lives_start6", Category.LIVES, "البدء بست حيوات", "Start with 6 lives", listOf("IATOZA")),
        preset("lives_start9", Category.LIVES, "البدء بتسع حيوات", "Start with 9 lives", listOf("AATOZE")),
        preset("lives_start83", Category.LIVES, "8 حيوات للاعب 1 و3 للاعب 2", "8 lives P1 / 3 lives P2", listOf("VATOLE")),
        preset("lives_inf", Category.LIVES, "حياة لا نهائية", "Infinite lives", listOf("SXIOPO")),
        preset("lives_99ram", Category.LIVES, "99 حياة (RAW)", "99 lives (RAM)", listOf("\$075A:63"), raw = true,
            note = "يكتب 99 في عداد الحيوات كل إطار — يعمل فقط مع SMB1."),
        preset("lives_never_lose", Category.LIVES, "لا تفقد القوة عند الضربة", "Don't lose power-ups when hit", listOf("SXULKNSE")),
        preset("lives_koopa_lives", Category.LIVES, "قفز على الكوبا يعطيك حياة", "Koopa bounce gives extra lives", listOf("KAAEPO")),

        // ---- jump / fly -----------------------------------------------------
        preset("jump_super_stand", Category.JUMP_FLY, "قفزة خارقة (واقف)", "Super jump standing", listOf("APZLGK")),
        preset("jump_super_walk", Category.JUMP_FLY, "قفزة خارقة (ماشي)", "Super jump walking", listOf("TPZLTG")),
        preset("jump_super_turbo", Category.JUMP_FLY, "قفزة خارقة (سرعة قصوى)", "Super jump turbo", listOf("GPZUAG")),
        preset("jump_mega_stand", Category.JUMP_FLY, "قفزة عملاقة (واقف)", "Mega jump standing", listOf("APZLGG")),
        preset("jump_mega_walk", Category.JUMP_FLY, "قفزة عملاقة (ماشي)", "Mega jump walking", listOf("APZLTG")),
        preset("jump_mega_turbo", Category.JUMP_FLY, "قفزة عملاقة (سرعة قصوى)", "Mega jump turbo", listOf("GAZUAG")),
        preset("jump_midair", Category.JUMP_FLY, "قفزة ثانية في الهواء", "Mid-air jumping", listOf("AEPLLG")),
        preset("fly_swim", Category.JUMP_FLY, "الطيران — سباحة في الهواء", "Fly: swim in the air", listOf("PIGOAP"),
            note = "يستبدل القفز بالسباحة: حرّك الماريو في الجو كأنه تحت الماء."),
        preset("fly_moon_stand", Category.JUMP_FLY, "جاذبية القمر (واقف)", "Moon gravity standing", listOf("YAZULG")),
        preset("fly_moon_walk", Category.JUMP_FLY, "جاذبية القمر (ماشي)", "Moon gravity walking", listOf("YAZUIG")),
        preset("fly_moon_turbo", Category.JUMP_FLY, "جاذبية القمر (سرعة قصوى)", "Moon gravity turbo", listOf("YAZUYG")),

        // ---- power / size ---------------------------------------------------
        preset("power_always_big", Category.POWER, "تبقى كبيرًا دائمًا", "Always stay big", listOf("OZTLLX", "AATLGZ", "SZLIVO")),
        preset("power_keep_fire", Category.POWER, "تحتفظ بالنار بعد الضربة", "Keep fire after hit", listOf("SZLIVO")),
        preset("power_always_fire_look", Category.POWER, "شكل ماريو الناري دائمًا", "Always look fiery", listOf("IIAAZT")),
        preset("power_starman_perm", Category.POWER, "نجمة دائمة", "Permanent Starman", listOf("PKAOZP"),
            note = "قد تموت عند لمس راية نهاية المرحلة."),
        preset("power_mushroom_multi", Category.POWER, "كل الفطر يمنحك نجمة", "Every power-up acts as star", listOf("ALASKA")),
        preset("power_small_raw", Category.POWER, "أصغّر ماريو الآن (RAW)", "Make Mario small now", listOf("\$0756:0"), raw = true,
            note = "يكتب حالة \"صغير\" مباشرة في ذاكرة SMB1."),
        preset("power_big_raw", Category.POWER, "كبّر ماريو الآن (RAW)", "Make Mario big now", listOf("\$0756:1"), raw = true,
            note = "يكتب حالة \"كبير\" مباشرة في ذاكرة SMB1."),
        preset("power_fire_raw", Category.POWER, "ماريو الناري الآن (RAW)", "Make Mario fiery now", listOf("\$0756:2"), raw = true,
            note = "يكتب حالة \"ناري\" مباشرة في ذاكرة SMB1."),

        // ---- invincibility --------------------------------------------------
        preset("inv_all", Category.INVINCIBILITY, "حصانة من كل شيء حتى الحمم", "Invincible from everything", listOf("GOZSXX")),
        preset("inv_enemy_touch", Category.INVINCIBILITY, "الأعداء يموتون بلمستك", "Enemies die on touch", listOf("SSASSA")),
        preset("inv_powerup", Category.INVINCIBILITY, "الأعداء يتحولون لقوى", "Enemies become power-ups", listOf("SUEISA")),

        // ---- worlds ---------------------------------------------------------
        preset("world_2", Category.WORLDS, "ابدأ من العالم 2", "Start on World 2", listOf("YSAOPE", "YEAOZA", "PEAPYA")),
        preset("world_3", Category.WORLDS, "ابدأ من العالم 3", "Start on World 3", listOf("YSAOPE", "YEAOZA", "ZEAPYA")),
        preset("world_4", Category.WORLDS, "ابدأ من العالم 4", "Start on World 4", listOf("YSAOPE", "YEAOZA", "LEAPYA")),
        preset("world_5", Category.WORLDS, "ابدأ من العالم 5", "Start on World 5", listOf("YSAOPE", "YEAOZA", "GEAPYA")),
        preset("world_6", Category.WORLDS, "ابدأ من العالم 6", "Start on World 6", listOf("YSAOPE", "YEAOZA", "IEAPYA")),
        preset("world_7", Category.WORLDS, "ابدأ من العالم 7", "Start on World 7", listOf("YSAOPE", "YEAOZA", "TEAPYA")),
        preset("world_8", Category.WORLDS, "ابدأ من العالم 8", "Start on World 8", listOf("YSAOPE", "YEAOZA", "YEAPYA")),
        preset("world_minus", Category.WORLDS, "العالم السالب (−1)", "Minus World", listOf("YSAOPE", "YEAOZA", "LXAPYA"),
            note = "مرة واحدة أجمل تجربة غريبة في نيس!"),
        preset("world_x", Category.WORLDS, "العالم X", "World X", listOf("YSAOPE", "YEAOZA", "AXAPYA")),

        // ---- levels / enemies ----------------------------------------------
        preset("level_bowser_1_1", Category.LEVELS, "نيران باوزر في 1-1", "Bowser fireballs in 1-1", listOf("IPEPNY")),
        preset("level_cheeps_1_1", Category.LEVELS, "سمك القفز في 1-1", "Cheep Cheeps in 1-1", listOf("KPEPNY")),
        preset("level_bullets_1_1", Category.LEVELS, "رصاصات في 1-1", "Bullet Bills in 1-1", listOf("NPEPNY")),
        preset("level_lakitu_1_1", Category.LEVELS, "لاكيتو في 1-1", "Lakitu in 1-1", listOf("OPEPNY")),
        preset("level_goomba_hammers", Category.LEVELS, "الغومبا يرمي مطارق", "Goombas throw hammers", listOf("STAGEO")),
        preset("level_swim_castle", Category.LEVELS, "قلعة تحت الماء", "Underwater castle", listOf("SIPPNG")),
        preset("level_walk_enemies", Category.LEVELS, "امشِ عبر الأعداء", "Walk through enemies", listOf("GTTTTL")),
        preset("level_walk_pipes", Category.LEVELS, "امشِ عبر الأنابيب والجدران", "Walk through pipes/walls", listOf("GIIIVY")),

        // ---- speed / fun ----------------------------------------------------
        preset("fun_fast_run", Category.SPEED_FUN, "ماريو يجري أسرع", "Mario runs fast", listOf("KILLEN")),
        preset("fun_crazy_level", Category.SPEED_FUN, "مرحلة مجنونة وقفز عالٍ", "Crazy level + high jump", listOf("SIKAEP")),
        preset("fun_every_pause", Category.SPEED_FUN, "أي زر يوقف اللعبة", "Every button pauses", listOf("OKZAPO")),
        preset("fun_cool_colors", Category.SPEED_FUN, "ألوان رائعة", "Cool colors", listOf("PINAOO")),
        preset("fun_weird_colors", Category.SPEED_FUN, "ألوان غريبة", "Weird colors", listOf("GYAEVO")),
        preset("fun_circus_music", Category.SPEED_FUN, "موسيقى سيرك", "Circus music", listOf("TVVOAE")),
        preset("fun_space_jazz", Category.SPEED_FUN, "جاز فضائي", "Space jazz", listOf("STLNYL")),
        preset("fun_beepy", Category.SPEED_FUN, "أصوات بيبي", "Beepy music", listOf("ZZAYGN")),
        preset("fun_slower_run", Category.SPEED_FUN, "مشي أبطأ", "Slower running", listOf("AZSLPIAK")),
        preset("fun_faster_jump", Category.SPEED_FUN, "قفز أسرع", "Faster jumps", listOf("AUSUPLAZ")),
    )

    /** One-tap feature buttons shown at the top of the Cheats screen. */
    val quickActions: List<QuickAction> = listOf(
        QuickAction("q_small", "أصغر", listOf("\$0756:0"), raw = true, note = "SMB1 RAW"),
        QuickAction("q_big", "كبير", listOf("\$0756:1"), raw = true, note = "SMB1 RAW"),
        QuickAction("q_fire", "نار", listOf("\$0756:2"), raw = true, note = "SMB1 RAW"),
        QuickAction("q_fly", "طيران", listOf("PIGOAP"), note = "سباحة بالهواء"),
        QuickAction("q_mega", "قفزة عملاقة", listOf("APZLGG")),
        QuickAction("q_inv", "حصانة", listOf("GOZSXX")),
        QuickAction("q_lives", "حياة ∞", listOf("SXIOPO")),
        QuickAction("q_world8", "العالم 8", listOf("YSAOPE", "YEAOZA", "YEAPYA")),
        // "زوم مع النار": fire + zoomed view in one tap.
        QuickAction("q_fire_zoom", "نار + تقريب", listOf("\$0756:2"), raw = true,
            note = "نار + زوم, zoom = 1.5", zoom = 1.5f),
    )

    fun search(query: String): List<CheatPreset> {
        val q = query.trim()
        if (q.isBlank()) return cheats
        return cheats.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.titleEn.contains(q, ignoreCase = true) ||
                it.codes.any { c -> c.contains(q, ignoreCase = true) }
        }
    }

    private fun preset(
        id: String,
        category: Category,
        title: String,
        titleEn: String,
        codes: List<String>,
        note: String = "",
        raw: Boolean = false,
    ) = CheatPreset(id, category, title, titleEn, codes, note, raw)
}
