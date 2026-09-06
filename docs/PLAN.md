# 🕹️ MARIOBOX — خطة البناء التفصيلية (محاكي NES احترافي لأندرويد)

> **الحالة:** مرجع العمل الملزم. كل مرحلة تُنفَّذ ثم تُقاس بمعايير قبولها قبل الانتقال للتالية.
> **القاعدة الحاكمة:** لا شيء "يعمل نظريًا" — كل طبقة تُختبر بأداة حقيقية، وبوابة الـ CI هي الحكم النهائي.

---

## 0) الرؤية ونطاق المنتج

محاكي NES لأندرويد مُصاغ لمتجر الاستخدام الشخصي: لعب **سوبر ماريو** (1/2/3/Worlds + Lost Levels +
مشتقات Famicom) بأعلى دقة ممكنة، مع تجربة تحكم لمسية/يدوية استثنائية، وقاعدة ميزات احترافية:

| # | الميزة | معيار الكمال |
|---|--------|--------------|
| 1 | استيراد الرومات + مكتبة | سحب من التخزين/SAF، تصنيف تلقائي بالهيدر والهاش، صور مصغّرة، مفضلة، بحث، آخر لعب |
| 2 | نواة محاكاة دقيقة | cycle-accurate + مرشّح NTSC حقيقي + أصوات APU كاملة |
| 3 | أزرار وتحكم | overlay قابل للتحرير الكامل + دعم يد Bluetooth/USB + إعادة تعيين الأزرار + Turbo |
| 4 | نقاط حفظ (Save States) | شرائح مع صورة مصغّرة وتاريخ ومدة لعب، حفظ سريع/تحميل سريع، حفظ تلقائي عند الخروج |
| 5 | رجوع (Rewind) + "عودة" | حلقة زمنية قابلة للسحب (Scrub) بدقة الإطار، طول قابل للضبط |
| 6 | الغش (Cheats) | محرّر Game Genie/PAR + مكتبة غش جاهزة لكل لعبة + تفعيل/تعطيل لحظي |
| 7 | قفزات ماريو (Fast Travel) | خريطة World/Zone جاهزة للعبور الفوري داخل SMB1/SMB3 |
| 8 | البطاريات | حفظ/استعادة `.srm` تلقائيًا + نسخ احتياطي يدوي |
| 9 | الأداء | 60 إطار/ث ثابتة على متوسّط الأجهزة، تأخير إدخال < 2 إطار، توقيت صوتي دقيق |
| 10 | النشر | CI يبني Debug/Release/AAB، ترقيم semver، ملاحظات مصنّفة، تلجرام |

**خارج النطاق عمداً:** الشبكة/اللعب الجماعي، Ruffle/FDS/القرص، ترجمة ROM، أي محتوى مملوك (رومات).

---

## 1) حقائق البيئة (قِس لا تخمّن)

| قيد | القيمة المكتشفة | الأثر على الخطة |
|-----|-----------------|------------------|
| SDK/NDK/JDK في الصندوق | **غير متوفرة** | لا يمكن بناء APK محليًا → البناء يُتحقّق منه عبر GitHub Actions (PR = بوابة خضراء) |
| `dl.google.com`, `maven.google.com`, `repo1.maven.org` | **محجوبة** (SSL_ERROR_SYSCALL) | لا تنزيل Gradle/AGP/Compose محليًا. الاعتماد على CI |
| `github.com`, `api.github.com`, `codeload` | **تعمل** | استيراد النوى مفتوحة المصدر عبر git/codeload ممكن |
| `apt` (root) | **ممنوع** | لا تثبيت cmake/ninja محليًا → نبني باختبارات `gcc/make` بسيطة للنواة |
| الموارد | 2 نواة، 3.8GB ذاكرة | نفضّل C على C++ الثقيل لتقليل زمن الترجمة واستهلاك الذاكرة |
| صلاحيات gh | كتابة على `arena/01a0774f-supermario` + PRs | حلقة تغذية راجعة حقيقية: دفع ← فحص ← إصلاح |
| المستودع | عام، `main` فارغ عمليًا (README فقط) | حذف كل شيء من الفرع السابق (لا شيء يُفقد) والتوقيع GPL سليم |

**النتيجة المنهجية:** كل ما يمكن إثباته محليًا (منطق النواة، تحليل iNES، تحويل الغش، تخطيط الخريطة)
يُختبر هنا بـ `gcc/g++` على رومات اختبار مفتوحة؛ وكل ما يخص البناء الأندرويدي يُثبت في CI.

---

## 2) اختيار النواة — نتيجة البحث المقارن

فحصت المستودعات (نجوم، رخصة، حجم، تاريخ آخر دفع، دعم NDK، تغطية المابرات، وجود تسلسل حالة):

| المرشح | اللغة | رخصة | ملاحظات | القرار |
|---|---|---|---|---|
| `0ldsk00l/nestopia` (926★) | C++ | GPL-2.0 | دقة عالية + NTSC + قاعدة بيانات ROMs، لكن 482 ملف C++ ثقيلة، لا target لأندرويد، بلا libretro مدمج | بديل ثانوي/اختياري |
| `negativeExponent/libretro-fceumm_next` | C | GPL-2.0 | **فيه `jni/Android.mk` جاهز للـ NDK**، `HAVE_NTSC=1` + `src/ntsc/nes_ntsc`، `src/cheat.c` فيه `FCEUI_DecodeGG/DecodePAR`، تغطية مابرات كاملة، `state.c` لتسلسل الحالة | ✅ **النواة الأساسية** |
| `anlohse/nescc_emu` (MIT) | C++17 | MIT | خفيف و7 مابرات فقط — غير كافٍ لعائلة ماريو (MMC3/VRC/BNROM) | مرجع تصميم |
| `YuriiZosia/YCsys-NES-Core` | C++ | بلا رخصة | بلا رخصة = لا يُستخدم قانونيًا | مرفوض |
| Mesen/MesenCE libretro | C++ | GPL | ثقيل للترجمة على 2 نواة، ولا أندرويد مباشر | مرفوض لهذا المشروع |
| Rust (protractor/nes) | Rust | متعدد | يتطلب cargo-ndk وإدارةFFI إضافية | مرفوض للبساطة |

### لماذا FCEUmm-next؟ (التعليل الهندسي)
1. **دقة مثبتة** لعائلة Nintendo الأولى: كل ما يلزم ماريو: NROM, UxROM, MMC1, MMC2, MMC3/TvROM/VRAM,
   MMC4, MMC5, CNROM, ANROM, BNROM, VRC2/4, FME7, TxC07 — وفروع SMB3 الخاصة (MMC3 IRAM/battery).
2. **حالة قابلة للتسلسل بالكامل** (`retro_serialize`) ← هي الأساس الذي تُبنى عليه **نقاط الحفظ + الرجوع** بدقة الإطار.
3. **غش مدمج**: فك ترميز Game Genie وPAR حرفيًا داخل النواة ← دقة أعلى منحاكي الترميز في الطبقات العليا.
4. **NTSC filter أصلي** (`nes_ntsc`) ← صورة "سينمائية" مطابقة للجهاز الأصلي، مع وضع pixels نظيف.
5. **C خالص + `Android.mk`** ← بناء NDK سريع وموثوق حتى على أجهزة ضعيفة، وسهل التدقيق الأمني.
6. `retro_get_memory_data(RETRO_MEMORY_SAVE_RAM)` ← SRAM/بطارية مباشرة.

### ثمن الاختيار (مُدار وليس متجاهَلًا)
- **GPL-2.0**: التطبيق كامله يُرخَّص GPL-3.0-or-later (متوافقة)، والمستودع **عام أصلًا** ← الالتزام محقَّق
  تلقائيًا. سيُوثَّق في `LICENSE`, `NOTICE`, و`docs/THIRD_PARTY.md` مع رابط المصدر وأوامر البناء.
- **الاعتماد على libretro ABI**: سنكتب **مضيف libretro خاص بنا** (C++) بدل الاعتماد على RetroArch،
  وهو ما يمنحنا تحكمًا كاملاً في التوقيت والصوت والرسم والـ overlay، ويجعل أي نواة libretro أخرى
  قابلة للإسقاط لاحقًا (خيار مستقبلي: Nestopia/CoolFCEUX/… أو تحميل نواة من المستخدم).

---

## 3) البنية المعمارية (نقاط الاتصال الواضحة)

```
┌──────────────────────────── UI: Jetpack Compose (Material 3) ───────────────────────────┐
│ LibraryScreen   GameScreen(overlay)   SaveStatesScreen   CheatsScreen   SettingsScreens   │
│ MapJumpScreen(SMB)   DebugHud   Onboarding(إذن المجلد + إخلاء مسؤولية قانوني)           │
└───────────────┬─────────────────────────────────────────────────────────────────────────┘
                │ StateFlow<UiState> (تدفق أحادي الاتجاه) + Events
┌───────────────▼─────────────────────────────────────────────────────────────────────────┐
│ ViewModel / Controller (Kotlin): EmulatorViewModel, LibraryViewModel, CheatsViewModel     │
│  ─ Repository: LibraryRepository(Room), SettingsRepository(DataStore), SaveStateRepo      │
└───────────────┬─────────────────────────────────────────────────────────────────────────┘
                │ JNI (C ABI واحد، ثابت)
┌───────────────▼─────────────────────────────────────────────────────────────────────────┐
│ libmariobox_host.so (C++17)  — "المضيف"                                                    │
│  ├ EmuThread: حلقة emu_run بتوقيت صوتي (AAudio callback = النبض) + تخطي إطار عند التأخر    │
│  ├ VideoSink: ANativeWindow + مقياس صحيح (integer scale)، CRC للإطار، وضع 2x/4x           │
│  ├ AudioSink: AAudio (low latency) + إعادة عينات عند الحاجة + كتم عند الإيقاف             │
│  ├ InputMap: retro_pad(RETRO_DEVICE_ID_JOYPAD_*) + turbo + slow-motion + frame advance     │
│  ├ Rewind: ring buffer من حالات مضغوطة (lz4/zstd) لكل إطار، scrub للوراء/للأمام            │
│  ├ Cheats: GG/PAR عبر النواة + peek/poke لذاكرة النظام                                    │
│  └ Env:  save dir, variables (palette/ntsc/region), geometry, input poll, audio latency   │
└───────────────┬─────────────────────────────────────────────────────────────────────────┘
                │ libretro API (retro_run/retro_serialize/…)
┌───────────────▼─────────────────────────────────────────────────────────────────────────┐
│ libmariobox_core.so = FCEUmm-next (C) + libretro-common + drivers/libretro glue           │
└───────────────────────────────────────────────────────────────────────────────────────────┘
```

**مبدأ الفصل:** الطبقة العليا لا تعرف شيئًا عن NDK؛ والطبقة السفلى لا تعرف شيئًا عن Android.
`libmariobox_host.so` هو الموثَّق والمُختبَر محليًا، ويُبنى أيضًا كـ **أداة اختبار C++ مستقلة** (host)
لتشغيل رومة اختبارية لـ 600 إطار والتحقق من البكسلات والحفظ/التحميل والرجوع — وهذا يُثبت في الصندوق.

---

## 4) هيكل المستودع (النهائي)

```
supermario-/
├── app/                         # :app — الواجهة والـ activities
│   ├── src/main/java/.../ui/…   # screens + components (Compose)
│   ├── src/main/res/            # قيم، أيقونات، themes، RTL (ar)
│   └── build.gradle.kts
├── engine/                      # :engine — غلاف الكوتلن للنواة (JNI + نماذج)
│   └── src/main/{cpp,java}      # CMakeLists.txt + host + bindings
├── core/{model,designsystem,common}/   # نماذج نقية، نظام تصميم، أدوات
├── feature/{library,play,cheats,savestates,map}/
├── engine/src/main/cpp/
│   ├── third_party/fceumm-next/ # منصوص عليه مع LICENSE وأرشيف المصدر الأصلي
│   ├── third_party/libretro-common/
│   ├── host/                    # مضيف libretro (C++17)
│   └── CMakeLists.txt
├── tools/                       # سكربتات: fetch-upstream, regen-cheat-db, host tests
├── .github/workflows/android-build.yml   # CI + Release (مشتق من قالبك)
├── docs/{PLAN,ARCHITECTURE,CONTROLS,CHEATS,MARIO_MAP,THIRD_PARTY,BUILD,QA.md}
├── gradle/{libs.versions.toml, wrapper}
├── LICENSE (GPL-3.0), NOTICE, README.md, .gitignore
└── CHANGELOG.md
```

### مصفوفة الإصدارات (مثبَّتة منوثائق رسمية، سبتمبر 2026)
| مكوّن | الإصدار | المرجع |
|---|---|---|
| AGP | **9.3.0** (يأتي بـ Kotlin مضمّن، لا حاجة لـ `kotlin-android`) | مطوّر أندرويد — إصدارات يوليو 2026 |
| Gradle | **9.5.0** (الحد الأدنى لـ AGP 9.3) | نفس المرجع |
| Kotlin / Compose plugin | **2.3.21** | دليل تهيئة Compose compiler |
| Compose BOM | **2026.08.00** (يتطلب compileSdk 37 + AGP ≥ 9.1.1) | مدونة مطوّري أندرويد أغسطس 2026 |
| compileSdk / targetSdk | **37 / 37** | شرط Compose 1.12 |
| minSdk | **26** (AAudio متوفر من API 26) | موثّق في قرار معماري ADR-0002 |
| Build Tools | 37.0.0 مع تراجع إلى 36.0.0 | CI يختار المتاح |
| NDK | **28.2.13676358** (افتراضي AGP 9.x) | جدول توافق AGP |
| JDK | 17 (temurin) | AGP 9 |
| CMake | 3.31.x (المسار الافتراضي للـ NDK يُستخدم عند الحاجة) | NDK r28 |

---

## 5) مواصفات الميزات وقابلية الاختبار

### 5.1 استيراد وبناء المكتبة
- `ACTION_OPEN_DOCUMENT` (multiple, `*.nes`/`*.unf`/`*.nez`) + مجلد مراقَب اختياري (SAF persistable) + NFC؟ لا.
- تحليل الهيدر: iNES/iNES2.0 (المابّر، PRG/CHR، mirroring، battery، VRAM)، حساب `md5` (أو `crc32` 8 بايت FCEUmm)
  ومطابقته بقاعدة أسماء/صور مُضمَّنة (JSON) لعناوين ماريو المعروفة؛ وإلا الاسم من اسم الملف.
- تخزين: Room (`game`, `play_session`, `save_state_meta`, `cheat`, `cheat_profile`) + نسخ ROM إلى
  `filesDir/roms` (أداء + خصوصية) مع خيار "افتح من مصدره" للملفات الكبيرة.
- **اختبار:** وحدة كوتلن على تحليل هيدر (byte fixtures) — تُدرَج في بوابة CI.

### 5.2 العرض والتوقيت
- وضعان: **Pixels** (1:1 مع تكبير صحيح) و**NTSC** (مرشّح `nes_ntsc`: تشبع/حِدّة/كتم ألوان + خطوط مسح)؛
  اختياري 4:3/ملء الشاشة/قص الحواف، تدوير 90° للوضع الأفقي الطويل.
- توقيت مدفوع بالصوت (audio clock) مع `SET_GEOMETRY` لتغيير الأبعاد الديناميكي (FCEUmm NTSC يغيّر العرض).
- **اختبار:** host test — تشغيل 600 إطار ثم مقارنة بصمة الإطار (hash) بمرجع، والتحقق من ثبات العرض 256x240 (أو NTSC 519x480).

### 5.3 التحكم
- Overlay: D-pad + A/B/Start/Select + زر Turbo (A/B) + زر حفظ سريع + زر رجوع + Fast Travel؛ كل عنصر
  قابل للسحب/التغيير حجمه/شفافيته، 3 layouts جاهزة (يسار/يمين/كل اليمين) + تخطيط مخصص محفوظ لكل لعبة.
- يد: `KEYCODE_BUTTON_A/B/X/Y`, dpad, start/select, L1 = fast forward, R1 = rewind hold, axis→dpad مع منطقة ميتة.
- إعادة تعيين الأزرار (remap) + ملف تعريف لكل لعبة + اهتزاز اختياري عند حفظ/موت.
- **اختبار:** آلة حالة Kotlin لتحويل `KeyEvent`→bitmask، مع تغطيه unit tests (بما فيها deadzone).

### 5.4 نقاط الحفظ / الرجوع / "العودة"
- `retro_serialize_size/serialize_state` ← ملف `FSM1` خاص بنا: ترويسة (إصدار، عنوان، ROM hash، زمن،
  إطار، thumbnail PNG 160x140 مضغوط، مدة اللعب) + الحالة + إعدادات. 10 شرائح يدوية + Quick + Auto-on-exit.
- الرجوع: حلقة دائرية من الحالات (lz4) بحجم قابل للضبط 10s/20s/60s/2min (ضبط تلقائي حسب حجم الحالة)،
  زر رجوع خطوة-بخطة + **شريط سحب** يعيد التشغيل من نقطة محددة (scrub → load → play).
- Undo بعد "الموت" = زر الرجوع المؤتمت: عند رصد `frame_count` انتقل لحالة "الموت" يُلتقط checkpoint تلقائيًا.
- **اختبار:** host test — حفظ، تشغيل 300 إطار، تحميل، مقارنة البصمة: يجب أن تتطابق 100%.

### 5.5 الغش (Cheats)
- محرّر يدعم: **Game Genie (6/8 خانة)**، **Par (7/8 خانة)**، **Action Replay NES**، وpoke خام (عنوان/قيمة)،
  مع شروط مقارنة وفئات (حياة، حصانة، توقيت، مستويات، أسلحة…)، تفعيل/تعطيل لحظي دون إعادة تشغيل.
- مكتبة مُضمَّنة (JSON) لكل لعبة ماريو: `super-mario-bros`, `smb2j`, `smb3`, `smb-lost-levels`,
  `mario-is-losing`, `dr-mario` … مع أسماء عربية/إنجليزية وملاحظات.
- **ملاحظة دقة:** فك ترميز GG يتم داخل النواة (`FCEUI_DecodeGG`) لتفادي أخطاء تحويل العناوين فوق المابرات.
- **اختبار:** اختبار خوارزمية GG في C (host) على 20 كودًا معروفًا، واختبار JSON schema للمكتبة.

### 5.6 خريطة قفزات ماريو (Fast Travel) — ميزة مميِّزة
- `assets/mario/world-map.json`: لمبة لكل World-Zone (SMB1: 8-1..8-4 مع الأنابيب، SMB3: خريطة العالم +
  مستوى الدخول) مع وصف ونوع الوصول.
- **SMB1:** كتابة مباشرة لعناوين PRG-RAM (من مصدر موثوق/عكس ROM): `0x0747` CurrentWorld(0-based)،
  `0x074B` PlayerStatus/world-index bytes، `0x074C` … إلخ، ثم إعادة تحميل الشاشة. العناوين الدقيقة
  ستُثبَّت في `docs/MARIO_MAP.md` مع مرجع كل عنوان، وتُختبر بآلية: poke → read-back.
- **SMB3:** قفز عبر كتابة مواضع الخريطة (يتطلب MMC3 IRAM) — يُدرج كمرحلة لاحقة M5 إن سمحت الميزانية.
- **اختبار:** host test — poke العنوان ثم قراءة قيمة الـ state بعد انتقال الشاشة.

### 5.7 الصوت
- AAudio بوضع LOW_LATENCY، 44100Hz stereo s16، مع `retro_audio_callback` غير قافِل حيثما أمكن +
  fallback blocking، إعادة تكوين تلقائي عند تغيير device، كتم عند فقدان التركيز، خيار تأخير (0/1/2 إطار).
- **اختبار:** عدّاد underrun في HUD + host test يثبت عدد العينات لكل إطار (≈735 for NTSC @44.1k).

### 5.8 الإعدادات
فيديو (وضع/مقياس/قص/زوايا/فلترة)، صوت (تأخير/مستوى/إعادة عينات)، محاكاة (منطقة NTSC/PAL/Dendy،
core options: palette, ntsc preset, vsync, run-ahead)، تحكم، حفظ (مسار، تلقائي، عدد الشرائح)،
غش، أداء (run-ahead 1/2 لتقليل تأخير الإدخال، thread priority)، متقدم (وضع المطوّر، تصدير سجلات).
كل إعداد: قيمة افتراضية، تخزين DataStore، وتراجع لكل لعبة.

### 5.9 الإتاحة واللمس
- RTL كامل (ar) + LTR، خط قابل للتكبير حتى 2.0، هدف لمس ≥ 48dp، `contentDescription` لكل زر،
  دعم لوحة مفاتيح فيزيائية/شاشة، TalkBack على المكتبة والإعدادات (اللعبة نفسها: semantics مخصصة للأزرار).

### 5.10 الأمان والاستقرار
- لا أذونات سوى ما يلزم (لا شبكة إطلاقًا في وضع الإنتاج)؛ قراءة SAF فقط؛ لا تخزين خارجي عشوائي.
- منع ANR: كل شيء ثقيل في coroutines/threads؛ إعادة تشغيل تلقائي بعد crash مع اقتراح تحميل Quick save؛
  معالجة `retro_serialize` فاشلة؛ watchdog للنواة (إعادة تحميل ROM بدل إعادة تشغيل العملية).
- عدم تسريب secrets: لا مفاتيح في المستودع؛ keystore من secrets فقط (كما في قالبك).

---

## 6) طبقات الاختبار (بوابة الجودة)

| الطبقة | الأداة | أين تُنفَّذ | المحتوى |
|---|---|---|---|
| N1 نواة/مضيف | `gcc/g++ + make` | **محليًا في الصندوق** | إقلاع رومة، 600 إطار، بصمات فيديو/صوت، save/load/rewind، GG decode |
| N2 منطق Kotlin نقي | JUnit5 في `:core:common` | CI | iNES parse، hash→metadata، remap، cheat model، world map JSON |
| N3 Compose UI tests | Robolectric/Paparazzi (اختياري) | CI | لقطات المكتبة والإعدادات (لا تُعطّل البناء إن غيّرتها) |
| N4 بناء APK/AAB | AGP + NDK | CI | assemble/bundle + lint |
| N5 سلامة المصدر | `git diff` مقابل upstream، فحص ترخيص | CI | لا تعديلات غير موثَّقة في `third_party/` |

**رومات الاختبار المفتوحة (بدون محتوى مملوك):** سنستخدم رومات نشرها أصحابها برخص مفتوحة/للاختبار
(مثل `pinobatch/little-things-nes` و`ClusterM/nes-input-test`) + رومة NROM مُولَّدة يدويًا للتحقق
الأساسي، وتُخزَّن في `tools/testdata` بحجم صغير. **لا رومات ماريو داخل المستودع إطلاقًا.**

---

## 7) خط النشر (CI) — مشتق من قالبك مع اختلاف الاسم

`name: MARIOBOX Android Build & Release` — نفس الفلسفة:
- **CI job** (أي PR/فرع): setup JDK 17 + `android-actions/setup-android` + sdkmanager (platform 37/36 +
  build-tools) + NDK + **host tests (N1)** + `testDebugUnitTest` + `assembleDebug` + رفع artifact.
- **Release job** (main/tags/dispatch): اختبارات → semver من الوسوم → ملاحظات مصنّفة (feat/fix/refactor/ci)
  → build Debug+Release+AAB → SHA256 → GitHub Release → تلجرام (إن وُجدت الأسرار) → Job Summary.
- **الفرق عن القالب:** إزالة `google-services.json` (لا Firebase)؛ إضافة خطوات NDK و host tests؛
  إعادة تسمية المخرجات إلى `MarioBox-vX.Y.Z-*.apk/aab`؛ keystore من secrets فقط مع `if: always()` للتخلص منه.

---

## 8) المراحل ومعايير الإنجاز

| المرحلة | المحتوى | معيار القبول (Done) |
|---|---|---|
| **M0** | تصفية الفرع، هيكل المستودع، gradle skeleton، LICENSE، استيراد النواة و`third_party` | `git log` نظيف؛ README يشرح البناء؛ `find` يعرض الهيكل المخطط |
| **M1** | مضيف C++ + libretro glue + CMake + host tests (N1) | اختبار محلي أخضر: إقلاع رومة + 600 إطار + save/load متطابق + GG decode |
| **M2** | :engine كوتلن (JNI, SurfaceView/AAudio bridge) + تشغيل حقيقي | APK يبني في CI؛ تدفّق الإطارات مُثبَّت بسجل + HUD |
| **M3** | المكتبة والاستيراد + Room + metadata + أيقونات | شاشة تعمل + وحدة N2 خضراء + استيراد فعلي من SAF |
| **M4** | Overlay + يد + remapping + HUD | تخطيط قابل للتحرير يُحفظ؛ دعم يد كامل؛ اختبارات N2 |
| **M5** | شرائح حفظ + رجوع/scrub + SRAM + غش + مكتبة الغش | rewind 20s بدون تأثر FPS؛ cheat يعمل لحظيًا؛ thumbnail يظهر |
| **M6** | خريطة قفزات ماريو + تحسينات الأداء (run-ahead, scaling shaders) | قفزة world تعمل وتُقاس بـ read-back |
| **M7** | تلميع: RTL، إتاحة، onboarding، وثائق، CI كامل + إصدار v1.0.0 | PR أخضر ← دمج ← Release + تلجرام |

---

## 9) المخاطر وتخفيفها (بصراحة)

| خطر | احتمال | تخفيف |
|---|---|---|
| فشل بناء AGP 9.3/Compose BOM الجديد في CI (لاSDK محلي) | متوسط | حلقة تصحيح عبر CI (3–5 دورات)، وقوائم إصدار مثبَّتة، ولا ميزات تجريبية |
| تغيّر API في AGP 9 (dsl block methods) | متوسط | DSL بسيط، بدون `CommonExtension<…>`، بدون build-logic معقد (قرأءة: AGP9 يبسّط) |
| ذاكرة CI/وقت الترجمة للنواة C (650 ملف) | منخفض-متوسط | ccache عبر `setup-gradle` + `-O2` + `abiFilters arm64-v8a` للـ debug |
| NTSC geometry يسبّب اهتزاز مقياس | متوسط | تثبيت عرض الإخراج داخليًا (خيار "NTSC 519x480 ثابت") |
| أخطاء في عناوين SMB map | متوسط | توثيق مرجع لكل عنوان + read-back test + مفتاح "تجريبي" في الإعدادات |
| رخصة GPL والمستودع عام | — | مقصود وموثَّق؛ لا خرق |
| مطالب "مثالية" لا تُقاس | — | كل ميزة أعلاه لها معيار قبول في §5 |

---

## 10) قرارات معمارية مُسجَّلة (ADRs ملخّصة)
- **ADR-0001** نواة FCEUmm-next عبر libretro ABI (الدقة + تسلسل حالة + غش مدمج + NDK جاهز).
- **ADR-0002** `minSdk = 26` لأجل AAudio الحقيقي بدل OpenSL ES؛ لا دعم لأجهزة أقدم.
- **ADR-0003** مضيف libretro خاص بنا بدل Embedding RetroArch (توقيع + صيانة + تحكم بالتوقيت).
- **ADR-0004** الرجوع = حالات مضغوطة lz4 بحلقة دائرية، لا rewind خفي داخل النواة (قابلية النقل).
- **ADR-0005** Room + DataStore (لا Hilt: طبقات بسيطة تكفي لمشروع بـ 3–4 modules، يقلّل مخاطر البناء).
- **ADR-0006** كل النصوص في `strings.xml` (ar الأساسية + en) لا سلاسل مضمّنة في الكود.
- **ADR-0007** ميزات "تجريبية" خلف مفتاح Dev في الإعدادات (fast travel, run-ahead, core switch).
- **ADR-0008** لا رومات مضمّنة؛ إرشاد المستخدم قانونيًا في Onboarding.

---

## 11) ما سأسلّمه فعليًا (Deliverables)
1. مستودع منظم، مبني على Git، برخصة GPL-3.0 + `NOTICE` + `docs/THIRD_PARTY.md` و`PROVENANCE` للـ vendored code.
2. نواة + مضيف C++ مبنيان باختبارات host خضراء **محليًا** (دليل قابل للتكرار: `make -C tools test`).
3. تطبيق أندرويد كامل الكوتلن/Compose بالمواصفات أعلاه، يمرّ عبر CI (Debug APK + Release + AAB).
4. `workflow` نشر مطابق لفكرتك (semver + ملاحظات مصنّفة + تلجرام + SHA256 + ملخص المهام) باسم التطبيق.
5. توثيق: README (مزايا/التقاط/بناء/إسهام)، `docs/{ARCHITECTURE,CONTROLS,CHEATS,MARIO_MAP,QA,BUILD}`.
6. إصدار v1.0.0 على GitHub Releases مع APK جاهز للتثبيت.
