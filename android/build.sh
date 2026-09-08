#!/bin/sh
# RGBTv Android build — no Gradle needed. Requires: JDK 11+, Android build-tools 34, platform android-34.
#   SDK=/path/to/sdk ./build.sh        (expects $SDK/build-tools/<ver>/ and $SDK/platforms/android-34/android.jar,
#                                       or the flat layout $SDK/build-tools + $SDK/android-34 used in CI)
set -e
cd "$(dirname "$0")"
SDK="${SDK:-$HOME/android-sdk}"
if [ -x "$SDK/build-tools/aapt2" ]; then BT="$SDK/build-tools"; else BT="$(ls -d "$SDK"/build-tools/*/ | sort | tail -1)"; fi
PLAT="$SDK/platforms/android-34/android.jar"; [ -f "$PLAT" ] || PLAT="$SDK/android-34/android.jar"
OUT=build; rm -rf "$OUT"; mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/assets"

# 1. web app → assets/www (same files as the webOS ipk)
mkdir -p "$OUT/assets/www"; cp -r ../app/. "$OUT/assets/www/"; rm -f "$OUT/assets/www/appinfo.json"

# 2. resources
"$BT/aapt2" compile --dir res -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$PLAT" --manifest AndroidManifest.xml -A "$OUT/assets" --java "$OUT/gen" "$OUT/res.zip" --auto-add-overlay

# 3. java → dex
find src "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -encoding UTF-8 -source 1.8 -target 1.8 -bootclasspath "$PLAT" -cp "$PLAT" -d "$OUT/classes" @"$OUT/sources.txt" -Xlint:-options
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
"$BT/d8" --release --min-api 21 --lib "$PLAT" --output "$OUT" @"$OUT/classes.txt"
(cd "$OUT" && zip -q base.apk classes.dex)

# 4. align + sign. Debug key auto-created with keytool (JDK) or openssl (fallback). For the Play Store use your own release key:
#    KEYSTORE=my.keystore KEYSTORE_PASS=secret ./build.sh
"$BT/zipalign" -f -p 4 "$OUT/base.apk" "$OUT/aligned.apk"
if [ -n "$KEYSTORE" ]; then
  "$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:"$KEYSTORE_PASS" --key-pass pass:"$KEYSTORE_PASS" --out RGBTv.apk "$OUT/aligned.apk"
else
  if [ ! -f debug.pk8 ]; then
    openssl req -x509 -newkey rsa:2048 -nodes -days 10000 -subj "/CN=RGBTv debug/O=RGBTv/C=DZ" -keyout debug.key -out debug.crt >/dev/null 2>&1
    openssl pkcs8 -topk8 -nocrypt -in debug.key -outform DER -out debug.pk8 && rm -f debug.key
  fi
  "$BT/apksigner" sign --key debug.pk8 --cert debug.crt --out RGBTv.apk "$OUT/aligned.apk"
fi
"$BT/apksigner" verify RGBTv.apk && echo "OK -> android/RGBTv.apk ($(du -h RGBTv.apk | cut -f1))"
