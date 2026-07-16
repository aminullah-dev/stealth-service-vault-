# SafeBeauty for Salons — Desktop App

A native desktop wrapper (Electron) around the salon console at
`https://safebeauty.web.app/provider`. It opens the console in its own window so
it feels like a real Mac/Windows app, and always shows the latest deployed
version. This is the salon-owner counterpart to the Admin desktop app in
`../desktop`.

## Prerequisites
- Node.js (you already have it — it's what runs the Cloud Functions).

## First-time setup
```bash
cd desktop-provider
npm install
```

## Run it (without building an installer)
```bash
npm start
```

## Build a Mac installer (.dmg)  — run this ON a Mac
```bash
npm run dist:mac
```
The `.dmg` lands in `desktop-provider/dist/`. Double-click it, drag
**SafeBeauty for Salons** to Applications, done.

> The app is **not code-signed**, so the first time macOS will say
> "unidentified developer". Right-click the app → **Open** → **Open** to allow it
> (only needed once). Proper signing needs a paid Apple Developer account.

## Build a Windows installer (.exe) — run this ON Windows
```bash
npm run dist:win
```
The `.exe` (NSIS installer) lands in `desktop-provider/dist/`.

> Building a Windows `.exe` must be done on a Windows machine (or via CI). You
> can't reliably cross-build it from a Mac.

## Notes
- Login uses the same salon phone + password the provider uses in the Android
  app. Only accounts with the **PROVIDER** role can sign in here.
- Touch ID (macOS) unlock is stored under a provider-specific keychain entry, so
  this app and the Admin app can each remember their own credential independently.
- Since it loads the hosted console, you don't need to rebuild the desktop app
  when the console changes — just `firebase deploy --only hosting` and reopen.
