const { contextBridge, ipcRenderer } = require('electron');

// Exposes a tiny, safe Touch ID bridge to the (remote) salon console page.
// Only these methods cross the boundary — no Node access leaks to the page.
contextBridge.exposeInMainWorld('electronBiometric', {
  available: () => ipcRenderer.invoke('bio:available'),
  has:       () => ipcRenderer.invoke('bio:has'),
  save:      (phone, password) => ipcRenderer.invoke('bio:save', { phone, password }),
  get:       () => ipcRenderer.invoke('bio:get'),
  clear:     () => ipcRenderer.invoke('bio:clear'),
});
