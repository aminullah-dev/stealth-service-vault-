# Deploy cheat‑sheet

Quick reference for **what changed → which command**. All commands run from the
project root on your Mac (`cd ~/Desktop/stealth-service-vault-`), after
`git pull origin claude/stealth-android-vault-4zr1d3`.

| What you changed | Command | Then |
|---|---|---|
| Android app code (`app/**/*.kt`, layouts, `AndroidManifest.xml`) | `./gradlew assembleDebug` | Install the APK from `app/build/outputs/apk/debug/` |
| Cloud Functions (`functions/index.js`) | `firebase deploy --only functions` | — |
| Firestore rules (`firestore.rules`) | `firebase deploy --only firestore:rules` | — |
| Web admin console (`public/**`) | `firebase deploy --only hosting` | Reopen the desktop app / refresh the browser |
| Desktop admin app (`desktop/main.js`, `desktop/preload.js`, `desktop/package.json`) | `cd desktop && npm run dist:mac` | Reinstall the `.dmg` from `desktop/dist/` |

Deploy several at once: `firebase deploy --only functions,firestore:rules,hosting`

## Common gotchas
- **App-only change?** No Firebase deploy needed — just rebuild the app.
- **Rules changed but not deployed** → the app silently gets "permission denied"
  (errors are swallowed, so nothing shows). Always deploy rules after editing them.
- **Desktop app**: it loads the *hosted* console, so console/web changes need
  only `firebase deploy --only hosting` — rebuild the `.dmg` **only** when files
  under `desktop/` change.
- **Unsigned Mac app** first launch: `xattr -cr "/Applications/SafeBeauty Admin.app"` then open.
- **Firebase asks "delete these indexes?"** → answer `n`.

## Release AAB for Google Play
1. Bump `versionCode` (and `versionName`) in `app/build.gradle.kts`.
2. `./gradlew bundleRelease` (signs with the keystore in `keystore.properties`).
3. Upload `app/build/outputs/bundle/release/app-release.aab` to the Play Console.

## Build the admin app
- Run without installing: `cd desktop && npm start`
- Mac installer: `npm run dist:mac` → `desktop/dist/*.dmg`
- Windows installer: `npm run dist:win` (must run on Windows)
