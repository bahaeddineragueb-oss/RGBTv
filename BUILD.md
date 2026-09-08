# RGBTv — building for every platform

The web app in `app/` is the single source of truth. Three thin shells wrap it:

| Target            | Folder     | Command                                              | Output                                 |
|-------------------|------------|------------------------------------------------------|----------------------------------------|
| LG webOS TV       | `app/` + `services/` | `ares-package app services/com.rgbtv.app.service -o dist` | `dist/com.rgbtv.app_<ver>_all.ipk` |
| Android phone/TV  | `android/` | `SDK=/path/to/android-sdk ./build.sh`  (JDK 11+, build-tools 34, platform 34; no Gradle) | `android/RGBTv.apk` |
| Windows / Linux / macOS | `desktop/` | `npm install && npm run dist:win` (needs wine on Linux) — or `node pack-win-zip.js` for a portable zip without wine | `desktop/release/...` |

## Android notes
- Debug-signed by default (`debug.pk8/.crt` auto-created). To ship on the Play Store: `KEYSTORE=release.keystore KEYSTORE_PASS=... ./build.sh`.
- `android:screenOrientation="sensorLandscape"` — the UI is designed for landscape; the stage widens to the phone's aspect ratio (18:9…21:9) with no black bars.
- Hardware Back, media keys, TV-remote colour keys are forwarded to the app's key engine (`Nav.press`).
- Also installs on Android TV boxes (leanback launcher entry + banner).

## Desktop notes
- Electron 28. CORS/Origin/Referer restrictions are removed at the session level so every IPTV server works like on TV.
- F11 full screen · Backspace/Esc = Back · media keys mapped.
