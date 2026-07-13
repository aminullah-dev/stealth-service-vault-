const { app, BrowserWindow, shell, Menu, ipcMain, safeStorage, systemPreferences } = require('electron');
const fs = require('fs');
const path = require('path');

// The desktop app is a thin native shell around the hosted admin console, so it
// always shows the latest deployed version and Firebase Auth works normally
// (the page loads from the authorized safebeauty.web.app origin, not file://).
const ADMIN_URL = 'https://safebeauty.web.app/admin';

// ── Touch ID sign-in ─────────────────────────────────────────────────────────
// The admin's phone+password is encrypted with the OS keychain (safeStorage)
// and only handed back after a successful Touch ID prompt. The server still
// verifies the credential, so this is a convenience unlock, not a bypass.
const credPath = () => path.join(app.getPath('userData'), 'admin-cred.bin');

ipcMain.handle('bio:available', () => {
  try { return process.platform === 'darwin' && systemPreferences.canPromptTouchID(); }
  catch { return false; }
});
ipcMain.handle('bio:has', () => { try { return fs.existsSync(credPath()); } catch { return false; } });
ipcMain.handle('bio:save', (_e, cred) => {
  try {
    if (!safeStorage.isEncryptionAvailable()) return false;
    fs.writeFileSync(credPath(), safeStorage.encryptString(JSON.stringify(cred || {})));
    return true;
  } catch { return false; }
});
ipcMain.handle('bio:get', async () => {
  try {
    if (!fs.existsSync(credPath())) return null;
    await systemPreferences.promptTouchID('sign in to SafeBeauty Admin');
    return JSON.parse(safeStorage.decryptString(fs.readFileSync(credPath())));
  } catch { return null; }   // cancelled, failed, or unreadable
});
ipcMain.handle('bio:clear', () => {
  try { if (fs.existsSync(credPath())) fs.unlinkSync(credPath()); return true; } catch { return false; }
});

function createWindow() {
  const win = new BrowserWindow({
    width: 1200,
    height: 820,
    minWidth: 900,
    minHeight: 600,
    title: 'SafeBeauty Admin',
    backgroundColor: '#FFF7FB',
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      preload: path.join(__dirname, 'preload.js')
    }
  });

  win.loadURL(ADMIN_URL);

  // Any window.open / target=_blank goes to the user's real browser.
  win.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: 'deny' };
  });

  // If the connection fails (offline), show a simple message with a retry.
  win.webContents.on('did-fail-load', (_e, code, desc) => {
    if (code === -3) return; // aborted (e.g. redirect) — ignore
    win.loadURL(
      'data:text/html;charset=utf-8,' +
      encodeURIComponent(
        `<body style="font-family:-apple-system,sans-serif;background:#FFF7FB;color:#8B3A47;
         display:flex;flex-direction:column;align-items:center;justify-content:center;height:100vh;margin:0">
         <h2>Couldn't reach the console</h2>
         <p style="color:#9a8b90">${desc}</p>
         <button onclick="location.href='${ADMIN_URL}'"
           style="padding:10px 20px;border:none;border-radius:10px;color:#fff;font-size:15px;cursor:pointer;
           background:linear-gradient(90deg,#EBA9C0,#B76E79,#7A2F3D)">Retry</button></body>`
      )
    );
  });
}

app.whenReady().then(() => {
  // Minimal, standard menu (keeps Cmd+Q, copy/paste, reload, devtools).
  Menu.setApplicationMenu(Menu.buildFromTemplate([
    ...(process.platform === 'darwin' ? [{ role: 'appMenu' }] : []),
    { role: 'editMenu' },
    { role: 'viewMenu' },
    { role: 'windowMenu' }
  ]));

  createWindow();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
