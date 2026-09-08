# بناء ملف RGBTv للـXbox بدون تثبيت أي برنامج (GitHub Actions — مجاني)

1. أنشئ حساباً مجانياً على https://github.com ثم اضغط **New repository** → الاسم `rgbtv` → **Private** → Create.
2. في صفحة المستودع الجديدة اضغط **uploading an existing file**.
3. فكّ ضغط `RGBTv-Xbox-github.zip` على حاسوبك، ثم اسحب **كل ما بداخله** (المجلدات `.github` و `app` و `xbox`)
   إلى صفحة الرفع → **Commit changes**.
   - إن لم يظهر مجلد `.github` (مخفي) فعّل "إظهار الملفات المخفية" في Windows، أو ارفعه بعد الباقي.
4. افتح تبويب **Actions** → إن طلب تفعيلها اضغط **I understand… enable them** → اختر
   **Build Xbox package** → **Run workflow**.
5. انتظر 8–15 دقيقة حتى تظهر ✅ ثم افتح التشغيل → في الأسفل **Artifacts** → حمّل **RGBTv-Xbox-msix**.
6. داخل الملف المضغوط: `RGBTv.Xbox_2.1.0.0_x64.msix` + مجلد `Dependencies\x64` (ثلاثة ملفات .appx).

## التثبيت على الـXbox (Dev Mode)
1. على الـXbox ثبّت **Dev Mode Activation** من المتجر وسجّل الجهاز بحساب Partner Center → يعيد التشغيل في وضع المطوّر.
2. Dev Home → **Remote Access Settings** → فعّل **Xbox Device Portal** وضع اسم مستخدم وكلمة مرور، ودوّن العنوان (مثل `https://192.168.1.50:11443`).
3. افتح العنوان من متصفح الحاسوب (وافق على تحذير الشهادة) → **Home ▸ My games & apps ▸ Add**.
4. اختر ملف `.msix` → Next → أضف ملفات `Dependencies\x64\*.appx` الثلاثة → **Start**.
5. RGBTv يظهر في Dev Home تحت *Games & apps* → شغّله واستمتع 🎮

للرجوع إلى الوضع العادي: Dev Home ▸ **Leave Dev Mode** (يُحذف التطبيق، ويعود عند تفعيل وضع المطوّر مجدداً).
