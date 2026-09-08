# كيف ترفع RGBTv إلى GitHub (خطوة بخطوة، بدون أي أوامر)

الملف الجاهز للرفع: **RGBTv-github-full.zip** (يحوي كل المنصات: webOS + Android + Windows + Xbox، بدون ملفات البناء الثقيلة).

## 1) إنشاء الحساب والمستودع
1. ادخل https://github.com → **Sign up** (بريد إلكتروني + كلمة مرور) → أكّد البريد.
2. اضغط زر **+** أعلى اليمين → **New repository**.
3. Repository name: `rgbtv` — اختر **Private** (خاص، لا أحد يراه غيرك) — **لا** تضع علامة على "Add a README" — اضغط **Create repository**.

## 2) رفع الملفات
1. فكّ ضغط `RGBTv-github-full.zip` في مجلد على حاسوبك.
2. في صفحة المستودع الجديدة (الفارغة) اضغط الرابط **uploading an existing file**.
3. افتح المجلد الذي فككته، اضغط `Ctrl+A` لتحديد **كل** ما بداخله (المجلدات `.github`, `app`, `android`, `desktop`, `services`, `xbox`, `tools` والملفات) واسحبها إلى صفحة المتصفح.
   - مجلد `.github` مخفي في Windows: في مستكشف الملفات → **View ▸ Show ▸ Hidden items**. بدونه لن يعمل البناء التلقائي للـXbox.
   - إن كان السحب بطيئاً (٢٠٠ ملف) اسحب مجلداً مجلداً، كل مرة ثم **Commit changes**.
4. في الأسفل اكتب رسالة مثل `RGBTv v2.1` ثم **Commit changes**. انتظر حتى يكتمل الرفع.

## 3) التحقق
افتح صفحة المستودع: يجب أن ترى `app/`, `xbox/`, `.github/`, `README.md`… وأسفلها README بالإنجليزية يعرض جدول المنصات.

## 4) بناء ملف Xbox تلقائياً (مجاني)
1. تبويب **Actions** → إن ظهر زر أخضر **I understand my workflows, go ahead and enable them** اضغطه.
2. من القائمة اليسرى **Build Xbox package** → **Run workflow** → **Run workflow**.
3. بعد ٨–١٥ دقيقة تظهر ✅ → افتح التشغيل → قسم **Artifacts** → حمّل **RGBTv-Xbox-msix**.
4. تابع التثبيت على الـXbox من `xbox/GITHUB-BUILD-AR.md`.

## 5) تحديثات لاحقة
عندما أرسل لك نسخة جديدة: افتح المستودع → **Add file ▸ Upload files** → اسحب الملفات المتغيّرة (أو كلها) → Commit. GitHub يستبدل القديم ويحتفظ بالتاريخ. كل Commit على `main` يبني Xbox تلقائياً.

## نصائح
- **Private** يعني أن الكود لك وحدك؛ بناء Actions مجاني للحسابات الشخصية (2000 دقيقة/شهر، بناء Xbox يستهلك ~30 دقيقة لأن Windows يُحسب ×2).
- لا ترفع ملفات `.apk`/`.ipk`/`.exe` الجاهزة إلى الكود؛ ضعها في **Releases** (تبويب Releases ▸ Draft a new release ▸ اسحب الملفات) لتوزيعها على أجهزتك.
- إن أردت لاحقاً بناء APK وIPK تلقائياً أيضاً على GitHub أخبرني وأضيف Workflows لها.
