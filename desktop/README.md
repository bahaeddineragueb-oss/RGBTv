# RGBTv Desktop (Windows / macOS / Linux)

Same app as the TV build, wrapped in Electron.

## Build
```
cd desktop
npm install
npm run dist:win      # → release/RGBTv Setup 2.0.0.exe  +  release/RGBTv-portable-2.0.0.exe
npm run dist:linux    # → release/RGBTv-2.0.0.AppImage
```
(`electron-builder` downloads the Electron binaries on first run — needs internet.)

## Run without building
```
npm install
npm start
```

## Keys
Arrows / Enter = navigate · Backspace or Esc = Back · F11 = full screen · media keys = play/pause/stop/next/prev
