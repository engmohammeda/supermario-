# MARIOBOX — مفتِّح NES على أندرويد، مخصَّص لعائلة Super Mario Bros.

محرّك libretro حقيقي (FCEUmm Next) داخل تطبيق Kotlin/Compose: سطح EGL + GLES2،
صوت AAudio، حلقة إطار تتحكَّم في إيقاعها بنفسها، حالات حفظ مصغَّرة `MBSV` مع
مصغّرات، rewind، SRAM منظَّفة، غشّات Game Genie/Par تُفكَّك داخل النواة نفسها،
ولوحة تحكّم لمسية بقابلة لإعادة الترتيب.

> ⚠️ **لا روم هنا.** المشروع لا يوزّع أي لعبة أو BIOS أو أصل مملوك لنينتندو،
> ولا يقبل صيغة `.fds` (قرءوا [docs/PLAN.md](docs/PLAN.md) ADR-0008). أحضروا
> ROM legally، أو استخدموا الرُّوم مفتوحة المصدر المولَدة للاختبار
> (`tools/gen_test_rom.py`) لإثبات أن الطبقات تعمل.

## الحالة الآن

| الطبقة | الدليل | النتيجة |
|---|---|---|
| المضيف + النواة (C++) | مجموعة امتثال على سطح المكتب: 83 فحصًا على ROM مولَّد + ASan/UBSan | ✅ **PASSED 83/0** |
| CMake للنواة (649 ملفًا) | `check-source-list` + بناء فعلي 652 هدفًا | ✅ |
| `platform/android` (EGL/GLES2/JNI) | فحص صياغة مقابل رؤوس Khronos/JDK الحقيقية بـ `-Werror` | ✅ نظيف (بدون GPU) |
| AAudio | لا رؤوس أندرويد في بيئة التطوير | 🚧 يُثبَت في CI وعلى جهاز |
| Gradle + Kotlin/Compose | لا JDK/SDK في بيئة التطوير | 🚧 يُثبَت في CI |
| APK (debug/release) | `assembleDebug` / `assembleRelease` | 🚧 CI (`.ci/android-build.yml`) |

**ما لا يعمل بعد** (مذكور بصدق، لا واجهات وهمية): النواة الثانية (`nescc` غير
قابلة للبناء كما هو منشور — خيار CMake يفشل بصوت عالٍ)، دعم_fds_مقفول بقرار،
تصدير/استيراد الحالات عبر SAF (الواجهة جاهزة في `Prefs`/`Library`، الحركة لم تُكتب)،
وإعادة تعيين لوحة الأزرار بالسحب. القائمة الكاملة في [docs/QA.md](docs/QA.md).

## التشغيل السريع

```sh
# 1) الطبقة الحرجة: لا تحتاج أندرويد إطلاقًا
make -C engine/src/test/native -j"$(nproc)" run     # → PASSED 83 checks, 0 failures

# 2) التطبيق: أندرويد ستوديو (أو Gradle 9.5 + NDK 28 + SDK 37)
gradle :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

البناء الكامل، والأدوات المطلوبة، وجدول الاستكشاف الأخطاء:
[docs/BUILD.md](docs/BUILD.md).

## خريطة المستودع

```
engine/src/main/cpp/host/          المضيف المستقل عن المنصّة: حلقة الإطار، dlopen للنواة، MBSV، rewind، SRAM
engine/src/main/cpp/platform/android/  EGL/GLES2 presenter، AAudio، دوال المنصّة، JNI (17 ألف سطر من Kotlin تُختصر هاهنا)
engine/src/main/cpp/platform/common/   حسابات العرض البحتة: القياس/الاقتصاص/الدوران/خطوط المسح — مخبَرة
engine/src/main/cpp/bridge/fceumm/     ما أضفناه للنواة فقط (dlsym)، دون تعديل النواة
engine/src/main/cpp/third_party/fceumm_next/  النواة، منقولة دون تعديل (GPL-2.0)
engine/src/main/java/dev/mariobox/engine/     MbNative/MbEngine/Gamepad/INesHeader (آمنة للاختبار على JVM)
engine/src/test/native/               مجموعة الامتثال (83 فحصًا) + Makefile + build-san
app/src/main/java/dev/mariobox/app/    MainActivity، EmulatorViewModel، مكتبة، تفضيلات
app/src/main/java/dev/mariobox/app/ui/  المكتبة، شاشة اللعبة، الأزرار/PadLayout، اللوحات (حالات/غشّات/إعدادات)
docs/PLAN.md                          العقد الملزم: القرار، الأدوات المتاحة، المخاطر، ADRs
docs/{BUILD,QA,THIRD_PARTY}.md        البناء، الجودة، الملكية الفكرية
.ci/android-build.yml                 بوابة CI (انظر docs/BUILD.md §3 لتفعيلها)
tools/                                gen_test_rom.py، gen_cmake_core_list.py
```

## بنية التصميم (لماذا هو هكذا)

* **النواة `.so` تُحمَّل بـ `dlopen`** من `mb_create()`، ولا تُربط وقت البناء: نواة
  مع رمز غير محلول تسقط كرسالة واضحة بدل ما تُسقِط العملية كلها، وأي نواة ثالثة تصير
  هدف CMake إضافيًا لا إعادة تصميم.
* **الميزانية الزمنية للحلقة ملك المضيف** (`frame_budget`)، والـ pacing عبر
  `eglSwapBuffers` حين يكون السطح حاضرًا وإلا `clock_nanosleep` — لأن أندرويد لا
  يوفّر ما يضمن معدل الإطارات، و`setAudioCallback` وحده لا يسترجع تأخّر القفل.
* **الإطار يخرج من النواة `XRGB8888`** (little-endian ⇒ B,G,R,X) والمضيف يقلبه إلى
  **R,G,B,A** مرة واحدة: نفس البافر يغذّي sampler في GLES2 و`Bitmap.createBitmap`
  في المصغّرات، فلا مسارَي ألوان ولا انحراف أحمر/أزرق مخفي.
* **حسابات العرض دوال خالصة** في `platform/common` لأنها الجزء الوحيد الذي يمكن
  اختباره دون GPU، وهي 11 فحصًا من الـ 83.
* **الأزرار تُقرأ من `RetroInputMap`** بعتبة هستيريسس وإلغاء اتجاهين متعاكسين،
  وتُجمَّع من ثلاث مصادر (لمس/لوحة مفاتيح-يد/تربو) قبل الدفع مرّة واحدة للنواة.

## الترخيص والحقوق

* المشروع: **GPL-3.0-or-later** ([LICENSE](LICENSE)).
* النواة مدمجة **دون تعديل** ورخصتها **GPL-2.0-only** (متوافقة)، ونسبتها وملفاتها
  `Copying`/`Authors` باقية في مكانها — التفاصيل في [NOTICE](NOTICE)
  و[docs/THIRD_PARTY.md](docs/THIRD_PARTY.md).
* لا ROMs، لا أصول نينتندو، لا قاعدة غشّات مملوكة. الأسماء التجارية لأصحابها؛
  هذا التطبيق ليس تابعًا لنينتندو ولا مدعومًا منها.
