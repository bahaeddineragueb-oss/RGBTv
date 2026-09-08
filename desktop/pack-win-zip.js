// Build a portable Windows folder/zip WITHOUT wine (electron-builder needs wine only to edit the exe icon).
// Uses the Electron win32-x64 zip from the npm cache (~/.cache/electron) or downloads it.
const fs = require('fs'), path = require('path'), os = require('os'), cp = require('child_process');
const ver = require('electron/package.json').version, name = `electron-v${ver}-win32-x64.zip`;
const cache = path.join(os.homedir(), '.cache', 'electron'), zip = path.join(cache, name);
if (!fs.existsSync(zip)) { fs.mkdirSync(cache, { recursive: true }); cp.execSync(`curl -L -o "${zip}" https://github.com/electron/electron/releases/download/v${ver}/${name}`, { stdio: 'inherit' }); }
const out = path.join(__dirname, 'release', 'RGBTv-win-x64'); fs.rmSync(out, { recursive: true, force: true }); fs.mkdirSync(out, { recursive: true });
cp.execSync(`unzip -q "${zip}" -d "${out}"`);
fs.renameSync(path.join(out, 'electron.exe'), path.join(out, 'RGBTv.exe'));
fs.rmSync(path.join(out, 'resources', 'default_app.asar'), { force: true });
const appDir = path.join(out, 'resources', 'app'); fs.mkdirSync(appDir);
for (const f of ['main.js', 'preload.js', 'package.json', 'icon.png', 'icon.ico']) fs.copyFileSync(path.join(__dirname, f), path.join(appDir, f));
fs.cpSync(path.join(__dirname, 'www'), path.join(appDir, 'www'), { recursive: true });
fs.writeFileSync(path.join(out, 'README.txt'), 'RGBTv for Windows (portable)\r\n\r\nRun RGBTv.exe. No installation needed.\r\nKeys: arrows/Enter navigate, Backspace or Esc = Back, F11 = full screen.\r\n');
const zipOut = path.join(__dirname, 'release', 'RGBTv-win-x64-portable.zip'); fs.rmSync(zipOut, { force: true });
cp.execSync(`cd "${path.dirname(out)}" && zip -qr "${zipOut}" "${path.basename(out)}"`);
console.log('OK →', zipOut, (fs.statSync(zipOut).size / 1048576).toFixed(1) + ' MB');
