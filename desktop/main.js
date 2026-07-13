const { app, BrowserWindow, shell, Menu } = require('electron');

// The desktop app is a thin native shell around the hosted admin console, so it
// always shows the latest deployed version and Firebase Auth works normally
// (the page loads from the authorized safebeauty.web.app origin, not file://).
const ADMIN_URL = 'https://safebeauty.web.app/admin';

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
      nodeIntegration: false
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
