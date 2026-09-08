const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('RGBTvDesktop', {
  exit: () => ipcRenderer.send('rgbtv:exit'),
  fullscreen: (on) => ipcRenderer.send('rgbtv:fullscreen', on),
  pair: (method, params) => ipcRenderer.invoke('rgbtv:pair', method, params || {})
});
