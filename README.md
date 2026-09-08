# RGBTv — IPTV player (webOS · Android · Windows · Xbox)

One HTML5 codebase (`app/`) shipped as:

| Platform | Folder | Build |
|---|---|---|
| LG webOS TV (.ipk) | `app/` + `services/` | `ares-package app services/com.rgbtv.app.service -o dist` |
| **Android NATIVE (.apk)** ⭐ | `native/` | GitHub Actions → *Build Android APK (native)*, or `cd native && ./gradlew :app:assembleRelease` |
| Android WebView shell, legacy (.apk) | `android/` | `cd android && SDK=/path/to/sdk sh ./build.sh` |
| Windows (Electron) | `desktop/` | `cd desktop && npm i && npm run dist:win` |
| Xbox Series S/X (UWP + WebView2, .msix) | `xbox/` | GitHub Actions → *Build Xbox package*, or `xbox\build.cmd` |

Features: Xtream Codes, Stalker Portal, M3U · Live TV / Movies / Series · multiple profiles · EPG · catch-up · favorites ·
parental lock · themes & hub styles · Arabic RTL · weather · prayer times (visual) · QR "Add from phone" · Magic Remote.

`app/js` is ES5 (webOS 3+), CSS is legacy-safe. Design stage 1920×1080 scaled to any screen.
