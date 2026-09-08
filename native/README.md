# RGBTv native — real Android app (Kotlin + ExoPlayer, no WebView)

تطبيق أندرويد **أصلي 100%**: واجهة RecyclerView + مشغّل Media3 ExoPlayer + تحليل تدفقي
(JsonReader / XmlPullParser / SQLite) — يعمل على الهاتف والتابلت و Android TV.

## البناء محلياً (Windows / Linux / macOS)

1. ثبّت **JDK 17** و **Android SDK** (platform 36 + build-tools 36).
2. عرّف `ANDROID_HOME` (أو ضع `sdk.dir=...` في `native/local.properties`).
3. من مجلد `native/`:
   - Linux/macOS: `./gradlew :app:assembleRelease`
   - Windows: `gradlew.bat :app:assembleRelease`
4. الناتج: `native/app/build/outputs/apk/release/app-release.apk`

## البناء السحابي (بدون أي تثبيت)

GitHub → تبويب **Actions** →‏ **Build Android APK (native)** →‏ **Run workflow**
→ حمّل الـ APK من **Artifacts** (أو من Release `native-v3.0.0`).

## توقيع نسخة المتجر (اختياري)

بدون إعدادات، الـ APK يُوقَّع بمفتاح debug (صالح للتثبيت المباشر).
لتوقيع release دائم، أضف Secrets للمستودع:

| Secret | القيمة |
|---|---|
| `RGBTV_KEYSTORE_BASE64` | ملف keystore مشفّر base64 |
| `RGBTV_KEYSTORE_PASS` | كلمة مرور المخزن |
| `RGBTV_KEY_ALIAS` | اسم المفتاح |
| `RGBTV_KEY_PASS` | كلمة مرور المفتاح |

توليد keystore جديد:

```sh
keytool -genkeypair -v -keystore rgbtv.keystore -alias rgbtv \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 rgbtv.keystore   # ← انسخ الناتج إلى RGBTV_KEYSTORE_BASE64
```

## البنية

```
app/src/main/java/com/rgbtv/app/
  App.kt                 نقطة الدخول (لغة/تخزين/صور)
  data/  Models · Store (prefs) · DiskCache · EpgDb (SQLite)
  net/   Net (OkHttp) · Provider (واجهة) · Xtream · Stalker · M3U · Xmltv
  repo/  Repository (الجلسة + بحث + EPG موحّد)
  img/   Images (ذاكرة + قرص + فك ترميز بخيوط)
  ui/    MainActivity · PlayerActivity · 10 fragments · adapters · PIN · Guard
```

- نفس `applicationId` للنسخة القديمة (`com.rgbtv.app`) — احذف القديمة قبل تثبيت الجديدة
  (التوقيع مختلف).
- `minSdk 24` (Android 7.0+) · `targetSdk 35` · `compileSdk 36` · Kotlin 2.2 · Media3 1.11 · AGP 8.12 · Gradle 8.13.
