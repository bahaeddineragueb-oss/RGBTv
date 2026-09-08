/* RGBTv desktop shell (Electron). Loads the same HTML5 app as the TV build.
 * - CORS/headers: the session strips browser-side restrictions so any Xtream/Stalker/M3U server works like on TV.
 * - Keys: arrows/Enter/Backspace(=Back)/Esc work natively; F11 toggles full screen; media keys map to the player. */
const { app, BrowserWindow, session, ipcMain, globalShortcut, Menu } = require('electron');
const path = require('path');
const pair = require('./pair');

app.commandLine.appendSwitch('autoplay-policy', 'no-user-gesture-required');
app.commandLine.appendSwitch('ignore-certificate-errors');
app.commandLine.appendSwitch('disable-features', 'OutOfBlinkCors');

let win;
function createWindow() {
  win = new BrowserWindow({
    width: 1600, height: 900, minWidth: 960, minHeight: 540, backgroundColor: '#0b0f19', show: false,
    autoHideMenuBar: true, title: 'RGBTv', icon: path.join(__dirname, 'icon.png'),
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, nodeIntegration: false, webSecurity: false, allowRunningInsecureContent: true, backgroundThrottling: false }
  });
  Menu.setApplicationMenu(null);
  win.once('ready-to-show', () => win.show());
  win.loadFile(path.join(__dirname, 'www', 'index.html'));
  win.on('closed', () => { win = null; });

  // make the app look like a normal browser to IPTV servers and remove CORS/Origin blockers
  const ses = win.webContents.session;
  ses.webRequest.onBeforeSendHeaders((details, cb) => {
    const h = details.requestHeaders; delete h['Origin']; delete h['Referer'];
    h['User-Agent'] = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36 RGBTv/2.0';
    cb({ requestHeaders: h });
  });
  ses.webRequest.onHeadersReceived((details, cb) => {
    const h = Object.assign({}, details.responseHeaders);
    for (const k of Object.keys(h)) if (/^access-control-|^x-frame-options|^content-security-policy/i.test(k)) delete h[k];
    h['Access-Control-Allow-Origin'] = ['*']; h['Access-Control-Allow-Headers'] = ['*']; h['Access-Control-Allow-Methods'] = ['*'];
    cb({ responseHeaders: h });
  });

  // keyboard: F11 full screen, Esc leaves full screen (the app itself treats Esc/Backspace as BACK)
  win.webContents.on('before-input-event', (ev, input) => {
    if (input.type !== 'keyDown') return;
    if (input.key === 'F11') { win.setFullScreen(!win.isFullScreen()); ev.preventDefault(); }
  });
}
ipcMain.on('rgbtv:exit', () => app.quit());
// "Add from phone": same protocol as the webOS Luna service, served by the Electron main process
ipcMain.handle('rgbtv:pair', async (e, method, params) => {
  if (method === 'pairStart') return pair.start((params || {}).lang);
  if (method === 'pairPoll') return pair.poll();
  if (method === 'pairStop') return pair.stop();
  throw new Error('unknown pair method ' + method);
});
ipcMain.on('rgbtv:fullscreen', (e, on) => win && win.setFullScreen(!!on));

app.whenReady().then(() => {
  createWindow();
  // media keys → the app's TV key codes (same numbers as webOS)
  const map = { MediaPlayPause: 10252, MediaStop: 413, MediaNextTrack: 418, MediaPreviousTrack: 419 };
  for (const k of Object.keys(map)) globalShortcut.register(k, () => win && win.webContents.executeJavaScript('window.Nav&&Nav.press(' + map[k] + ')').catch(() => {}));
  app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
});
app.on('window-all-closed', () => app.quit());
app.on('will-quit', () => { globalShortcut.unregisterAll(); pair.stop(); });
