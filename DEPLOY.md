# Deploy cheat‑sheet

Quick reference for **what changed → which command**. All commands run from the
project root on your Mac (`cd ~/Desktop/stealth-service-vault-`), after
`git pull origin claude/stealth-android-vault-4zr1d3`.

| What you changed | Command | Then |
|---|---|---|
| Android app code (`app/**/*.kt`, layouts, `AndroidManifest.xml`) | `./gradlew assembleDebug` | Install the APK from `app/build/outputs/apk/debug/` |
| Cloud Functions (`functions/index.js`) | `cd functions && npm test` then `firebase deploy --only functions` | — |
| Firestore rules (`firestore.rules`) | `firebase deploy --only firestore:rules` | — |
| Storage rules (`storage.rules`) | `firebase deploy --only storage` | — |
| Web admin / salon console (`public/**`) | `firebase deploy --only hosting` | Reopen the desktop app / refresh the browser |
| Desktop admin app (`desktop/main.js`, `desktop/preload.js`, `desktop/package.json`) | `cd desktop && npm run dist:mac` | Reinstall the `.dmg` from `desktop/dist/` |
| Desktop salon app (`desktop-provider/main.js`, `desktop-provider/preload.js`, `desktop-provider/package.json`) | `cd desktop-provider && npm run dist:mac` | Reinstall the `.dmg` from `desktop-provider/dist/` |

Deploy several at once: `firebase deploy --only functions,firestore:rules,storage,hosting`

## Common gotchas
- **App-only change?** No Firebase deploy needed — just rebuild the app.
- **Rules changed but not deployed** → the app silently gets "permission denied"
  (errors are swallowed, so nothing shows). Always deploy rules after editing them.
- **Desktop apps** (`desktop/` admin, `desktop-provider/` salon): each loads its
  *hosted* console (`/admin`, `/provider`), so console/web changes need only
  `firebase deploy --only hosting` — rebuild a `.dmg` **only** when files under
  that app's folder change. Both consoles ship from the single `public/` deploy.
- **Unsigned Mac app** first launch: `xattr -cr "/Applications/SafeBeauty Admin.app"`
  (or `"/Applications/SafeBeauty for Salons.app"`) then open.
- **Firebase asks "delete these indexes?"** → answer `n`.
- **Firebase asks "delete function `authenticateWithPin`?"** → answer `y`. It was
  removed on purpose (a security fix — it was a pre-auth account-takeover oracle);
  confirming deletes it from the cloud. This prompt appears once, on the first
  `firebase deploy --only functions` after the removal.

## Release AAB for Google Play
1. Bump `versionCode` (and `versionName`) in `app/build.gradle.kts`.
2. `./gradlew bundleRelease` (signs with the keystore in `keystore.properties`).
3. Upload `app/build/outputs/bundle/release/app-release.aab` to the Play Console.

## Signing key (READ THIS before touching keystores)
Google Play only accepts uploads signed with the **official upload key**:

    SHA1: A0:04:BE:C3:6A:A0:D8:BF:A6:C8:8B:7F:DB:09:36:E5:1C:68:A6:F5
    alias: safebeauty   (store == key password)

- The build now **guards this automatically**: `bundleRelease`/`assembleRelease`
  depend on `verifyReleaseSigningKey`, which fails fast with a clear message if the
  keystore in `keystore.properties` doesn't match the SHA1 above. No more finding
  out at upload time.
- If you ever see `❌ Wrong signing key`, the keystore file at `storeFile` is the
  wrong one. Restore the real upload key (fingerprint above) and rebuild.
- Verify any keystore's fingerprint manually:
  `keytool -list -v -keystore <file.jks> -storepass <pass> | grep SHA1`
- Verify a built bundle: `keytool -printcert -jarfile app-release.aab | grep SHA1`
- **Back up the upload keystore + password** somewhere safe (password manager).
  It's the only key that can ever push updates to this app — if it's lost, it's lost.

## Play upload warnings you can ignore
- **"no longer supports N devices"** → a native lib bumped its CPU requirement; N is
  usually a handful of very old models. On a testing track, click **Proceed anyway**.
- **"native code … debug symbols not uploaded"** → cosmetic; only affects crash
  readability. Optional to fix later with a symbols upload.

## Build the desktop apps
Admin console (`desktop/`):
- Run without installing: `cd desktop && npm start`
- Mac installer: `npm run dist:mac` → `desktop/dist/*.dmg`
- Windows installer: `npm run dist:win` (must run on Windows)

Salon console (`desktop-provider/`) — same commands, own folder:
- Run without installing: `cd desktop-provider && npm start`
- Mac installer: `npm run dist:mac` → `desktop-provider/dist/*.dmg`
- Windows installer: `npm run dist:win` (must run on Windows)
- Loads `/provider`; only PROVIDER-role accounts can sign in.
