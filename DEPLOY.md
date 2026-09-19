# Deploy cheat‑sheet

Quick reference for **what changed → which command**. All commands run from the
project root on your Mac (`cd ~/Safe\ beauty`), after
`git pull origin claude/stealth-android-vault-4zr1d3`.

| What you changed | Command | Then |
|---|---|---|
| Android app code (`app/**/*.kt`, layouts, `AndroidManifest.xml`) | `./gradlew assembleProdDebug` | Install the APK from `app/build/outputs/apk/prod/debug/` |
| Cloud Functions (`functions/index.js`, `functions/domains/**`, `functions/lib/**`) | `cd functions && npm test` then `firebase deploy --only functions` | — |
| Alerting (`scripts/setup-monitoring.sh`) | `./scripts/setup-monitoring.sh safebeauty <your-email>` | Re-run it for **every** project you alert on. Adding a kind to `ALERTS` creates nothing on its own — the metric and the policy exist only after the script runs, so a new `alertable()` label logs to nobody until then. |
| Firestore rules (`firestore.rules`) | `firebase deploy --only firestore:rules` | — |
| Firestore indexes (`firestore.indexes.json`) | `firebase deploy --only firestore:indexes` | Wait for the index to finish building before the query is used — until then it fails, it does not just run slowly |
| Storage rules (`storage.rules`) | `firebase deploy --only storage` | — |
| Web admin / salon console (`public/**`) | `firebase deploy --only hosting` | Reopen the desktop app / refresh the browser |
| One console only | `firebase deploy --only hosting:admin` (or `:salon`, or `:app`) | — |
| Desktop admin app (`desktop/main.js`, `desktop/preload.js`, `desktop/package.json`) | `cd desktop && npm run dist:mac` | Reinstall the `.dmg` from `desktop/dist/` |
| Desktop salon app (`desktop-provider/main.js`, `desktop-provider/preload.js`, `desktop-provider/package.json`) | `cd desktop-provider && npm run dist:mac` | Reinstall the `.dmg` from `desktop-provider/dist/` |

Deploy several at once: `firebase deploy --only functions,firestore:rules,storage,hosting`

## Hosting: three sites, one `public/` tree

| Target | Site | Serves | Public dir |
|---|---|---|---|
| `app` | `safebeauty` | `safebeauty.web.app` — the app-facing pages | `public` |
| `admin` | `safebeauty-admin` | `9sg9ceuj.linumic.com` | `public/admin` |
| `salon` | `safebeauty-salon` | `salon.linumic.com` | `public/provider` |

A Firebase custom domain attaches to a site's **root**, not to a path, which is
why the two consoles needed sites of their own rather than a domain pointed at
`safebeauty.web.app/admin`.

`app` is deliberately unchanged and must stay that way: `safebeauty.web.app/admin`
and `/provider` are hardcoded in `desktop/main.js` and `desktop-provider/main.js`,
and those apps are already installed on people's machines. `/get` is in every
invite ever sent. So the consoles are served from **two** places on purpose —
the old paths and the new subdomains — and neither can be retired without
shipping new desktop builds first.

`firebase deploy --only hosting` deploys all three. Targets live in `.firebaserc`;
if a clone ever loses them, restore with:

    firebase target:apply hosting app   safebeauty
    firebase target:apply hosting admin safebeauty-admin
    firebase target:apply hosting salon safebeauty-salon

## Common gotchas
- **App-only change?** No Firebase deploy needed — just rebuild the app.
- **A new custom domain says "Records not yet detected" even though `dig` finds
  it.** Firebase asked before the record existed and cached the "no such name"
  answer. `linumic.com`'s SOA minimum is 600 seconds, so wait ten minutes and
  press Verify again — it is not a misconfiguration and re-adding the record
  does not help. Check what the world sees with
  `dig +short 9sg9ceuj.linumic.com @8.8.8.8`.

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


## The admin console's hostname

`9sg9ceuj.linumic.com` is deliberately not `admin.` — that is the first name
anyone tries. Be clear about what it buys, which is less than it looks:

- It stops wordlist guessing. That is all it stops.
- It is **not** a secret. A publicly-trusted certificate is logged to the
  Certificate Transparency logs the moment it is issued, and those logs are
  public and searchable. Any hostname served over HTTPS by Firebase is
  discoverable within minutes.
- The repository is private, so the hostname is not readable there — but that is
  a second lock on a door the CT logs have already described.

What actually keeps people out of that console: phone + password verified
server-side, the `ADMIN` role check in `assertAdmin`, and the rate limit of ten
attempts per number per fifteen minutes. Treat the hostname as convenience, and
never as the thing standing between a stranger and the identity documents.

## The two flavours
The app builds in two environments, and they are different apps to Android:

| Flavour | applicationId | Firebase project | What it is for |
|---|---|---|---|
| `prod` | `com.security.stealthapp` | `safebeauty` | What ships to Play |
| `demo` | `com.security.stealthapp.demo` | `safebeauty-staging` | The public demo on linumic.com |

Because the applicationIds differ, both install at once and neither can read the
other's data — which is the point: production holds customers' identity photos,
and the demo link is public. Each flavour picks up its own
`google-services.json`; demo's is in `app/src/demo/`, prod falls through to
`app/google-services.json`.

**Adding a flavour dimension renamed the variant tasks.** `assembleDebug` and
`assembleRelease` survive as aggregates that build BOTH flavours;
`testDebugUnitTest` does not exist at all any more. Name the flavour.

## Release AAB for Google Play
1. Bump `versionCode` (and `versionName`) in `app/build.gradle.kts`.
2. `./gradlew bundleProdRelease` (signs with the keystore in `keystore.properties`).
   Watch for `✅ Release signing key verified` — the guardrail refuses a wrong key.
3. Upload. Either drag the AAB into Play Console, or use the API (below).

**Check the AAB's age before uploading one that is already on disk.** On
2026-09-09 the bundle sitting in `app/build/outputs` had been built three days
earlier and predated the fix for salons appearing twice; uploading it would
have shipped a bug that was already fixed in the tree. Compare its mtime
against `git log -1 --format=%ad -- app/src/main`.

### Uploading from the command line

Set up 2026-09-10. The service account key is at
`~/.config/safebeauty/play-publisher.json` (gitignored, outside the tree).

    androidpublisher.googleapis.com   enabled on project safebeauty
    service account                   play-publisher@safebeauty.iam.gserviceaccount.com

The part that is NOT gcloud and cannot be scripted: that service account has
to be invited inside **Play Console → Users and permissions**, with *Release
to production…* and *Release apps to testing tracks*. IAM roles do not grant
Play access — Play keeps its own permission list. Deliberately no financial
or user-data access.

**Those two permissions cover releases only.** Editing the store listing —
screenshots, description, graphics — needs *Manage store presence* as well,
under **App permissions → Store presence**, and it was not granted until
2026-09-10. Without it the API lets you open an edit, delete images and
upload replacements without complaint, and then fails the very last call:

    POST .../edits/{id}:commit
    403 PERMISSION_DENIED — The caller does not have permission

That failure mode is survivable rather than dangerous, because an uncommitted
edit changes nothing — the live listing still had its old screenshots
afterwards, verified by reading them back. But every byte is uploaded before
you find out, so grant the permission first.

The flow is: open an edit → POST the bundle to the `/upload/` host → PUT the
track → `:commit`. An edit changes nothing until committed, so opening one and
deleting it is a safe way to read state (tracks, uploaded versionCodes)
without touching anything.

**Stage the release as `draft`, not `completed`.** Uploading is a mechanical
step; deciding that every user in Afghanistan gets a new build today is not.
A draft appears in Play Console ready for a human to press *Start rollout*,
and a staged percentage rollout is the safer first move.

Release notes go in the same call — `releaseNotes: [{language, text}]` with
`en-US`, `fa-AF`, `ps-AF`, each under Play's 500 characters. They live in
`play-store/release-notes-vNN.md`.

### Replacing the store screenshots

`scripts/play.py` is the whole client: it signs the service-account JWT by
shelling out to `openssl` (this Mac has neither `google-auth` nor
`cryptography`) and exposes `call(method, path, ...)`.

    cd scripts && python3 -c "
    import play, os
    P = f'/androidpublisher/v3/applications/{play.PKG}/edits'
    eid = play.call('POST', P)['id']
    base = f'{P}/{eid}/listings/fa-AF/phoneScreenshots'
    play.call('DELETE', base)                       # clears the whole set
    for n in ['1-salon-list.png', '2-salon-detail.png']:
        blob = open('../play-store/screenshots/' + n, 'rb').read()
        play.call('POST', f'/upload{base}?uploadType=media',
                  raw=blob, content_type='image/png')
    play.call('POST', f'{P}/{eid}:commit')
    "

Note the `/upload` prefix on the image POST and the absence of one on
everything else. `fa-AF` is the only locale this listing has.

Then **read it back and compare hashes** rather than trusting the commit —
the API returns a `sha1` per image, so `hashlib.sha1(open(f,'rb').read())`
proves the bytes on the store are the bytes on disk. `play-store/screenshots/`
holds the live set and its README explains what each one is for.


### Resubmitting after a rejection

Attach the new build to the same appStoreVersion, then on the SAME review
submission (it sits in `UNRESOLVED_ISSUES`) mark its rejected item resolved
before submitting — otherwise the submit is refused with the misleading
`STATE_ERROR … Version is not ready to be submitted yet, please try again
later`, and retrying does not help (six tries over three minutes on
2026-09-17):

    PATCH /v1/reviewSubmissionItems/{itemId}   {"attributes": {"resolved": true}}
    PATCH /v1/reviewSubmissions/{id}           {"attributes": {"submitted": true}}

The item goes REJECTED → READY_FOR_REVIEW, the submission to
WAITING_FOR_REVIEW. This is what the "Resubmit to App Review" button does.
Explain the fix in App Review notes (`appStoreReviewDetails.notes`) — the
Resolution Center reply thread has no public API.

## TestFlight / App Store build for iOS

Nothing here existed until 2026-09-09, and rediscovering it cost most of a
day. The whole path, in one command:

1. Bump `CURRENT_PROJECT_VERSION` in `ios/project.yml` (build number — must be
   higher than anything already uploaded; `MARKETING_VERSION` only changes for
   a real release).
2. `cd ios && xcodegen generate --spec project.yml`
3. Archive, then export-and-upload in one step:

```bash
cd ios
xcodebuild -project SafeBeauty.xcodeproj -scheme SafeBeauty \
  -configuration Release -destination 'generic/platform=iOS' \
  -archivePath build/archive/SafeBeauty.xcarchive \
  -allowProvisioningUpdates archive

xcodebuild -exportArchive \
  -archivePath build/archive/SafeBeauty.xcarchive \
  -exportPath build/upload \
  -exportOptionsPlist build/exportOptionsUpload.plist \
  -authenticationKeyPath "$HOME/.appstoreconnect/private_keys/AuthKey_<KEYID>.p8" \
  -authenticationKeyID <KEYID> \
  -authenticationKeyIssuerID 0e948a64-b5af-4815-bc6c-f7943bb4f637 \
  -allowProvisioningUpdates
```

`build/exportOptionsUpload.plist` is `method: app-store-connect`,
`teamID: 27RXPRW77S`, `signingStyle: automatic`, `uploadSymbols: true`,
`destination: upload`. Drop the `destination` key to get an `.ipa` on disk
instead (for Transporter).

**The API key must have the Admin role, not App Manager.** On this Mac that is
`AuthKey_489AT7M94B.p8` (verified 2026-09-17 by uploading build 5 with it).
`DL35J6V9B7` fails with `No Accounts with App Store Connect Access`, and
`4SKX647AH5` is the key `scripts/asc.py` uses for metadata, not signing. This is the whole
trap. An App Manager key authenticates fine and then fails at signing:

```
error: exportArchive Cloud signing permission error
error: exportArchive No signing certificate "iOS Distribution" found
```

because exporting needs a *distribution certificate*, Xcode's cloud signing
mints one on demand, and minting one is certificate management — which App
Manager does not have. Apple will not let you raise an existing key's access
("can't be modified to access more services once created"), so make a new one:
App Store Connect → Users and Access → Integrations → App Store Connect API →
**+** → Access **Admin** → download the `.p8` (once only) into
`~/.appstoreconnect/private_keys/`.

Do NOT rely on the Apple ID signed into Xcode instead. It works until it
doesn't: on 2026-09-09 the account silently emptied out of
`com.apple.dt.Xcode.plist` mid-afternoon, three uploads into the day, and
`xcodebuild` started answering `error: exportArchive No Accounts` while the
Xcode GUI still showed the account present with Admin role. Signing back in
did not restore it for the command line. The API key has no session to lose.

Also worth knowing: there is no distribution certificate in the login
keychain and there does not need to be — cloud signing fetches an ephemeral
one per export (`Cloud Managed Apple Distribution` in
`build/upload/DistributionSummary.plist`). An App Store provisioning profile
for `com.safebeauty.app` does sit in `~/Library/Developer/Xcode/UserData/
Provisioning Profiles/`; a profile alone cannot sign anything.

The five `Upload Symbols Failed ... dSYM for FirebaseFirestoreInternal /
absl / grpc / grpcpp / openssl_grpc` warnings are expected and harmless —
those are Firebase's own binaries, with no source to symbolicate. SafeBeauty's
own frames symbolicate normally.

Apple then takes 15–60 minutes to process the build before it appears in
TestFlight and in App Store Connect's build picker.

## iOS push notifications (APNs) — set up 2026-09-09, verified working

Without this the iOS app asks for notification permission and then never
delivers anything: no booking confirmation, no cancellation, no waitlist
opening. It was the last dead feature in the iOS build.

Three pieces, and only the middle one is obvious:

1. **Push capability on the App ID.** Already on — `xcodebuild ...
   -allowProvisioningUpdates` enabled it during the first archive, after
   failing with "Provisioning profile ... doesn't include the Push
   Notifications capability". Verify with:
   `GET /v1/bundleIds?filter[identifier]=com.safebeauty.app&include=bundleIdCapabilities`
   → must list `PUSH_NOTIFICATIONS`.

2. **An APNs auth key (.p8)** from developer.apple.com → Certificates,
   Identifiers & Profiles → **Keys** → + → tick *Apple Push Notifications
   service (APNs)*. There is no API for this; `/v1/apnsKeys`, `/v1/keys`,
   `/v1/pushKeys` and `/v1/authKeys` are all 404. It is a portal-only step.

   🔴 **The trap: the Environment dropdown defaults to `Sandbox`, and Apple
   says on that same screen that it "can't be changed once saved".** A
   sandbox-only key works for Xcode debug builds and silently delivers
   nothing to TestFlight or the App Store, which are the production
   environment — and there is no way back, only a new key. Choose
   **Sandbox & Production**. Key Restriction `Team Scoped (All Topics)` is
   correct and lets the same key serve the other apps on this team.

   The `.p8` downloads **once**. Ours lives beside the App Store Connect keys
   in `~/.appstoreconnect/private_keys/` (all `*.p8` are gitignored).

3. **Upload it to Firebase**: console → Project settings → **Cloud
   Messaging** → Apple app configuration → SafeBeauty (iOS) → APNs
   Authentication Key. Needs the file, the **Key ID** (the ten characters in
   the filename) and the **Team ID** `27RXPRW77S`.

   Second trap: Firebase shows two rows, *development* and *production*, and
   uploading once fills only **development**. Upload the same file again into
   the production row — one auth key is valid for both environments, unlike
   the old certificates, which is what those two rows are a holdover from. If
   only development is filled, a TestFlight build gets nothing.

**Prove it rather than assume it.** From `functions/` (so `firebase-admin`
resolves), read the token off the user's own document and send one real push:

```js
const snap = await db.collection("users").where("phone","==","+93XXXXXXXXX").limit(1).get();
await admin.messaging().send({ token: snap.docs[0].data().fcmToken,
  notification: { title: "SafeBeauty", body: "test" } });
```

A returned message id means Firebase accepted it, NOT that Apple delivered
it — the only proof is the banner appearing on the phone. Ours did, on
2026-09-09, on the TestFlight build.

If it does not arrive: the app may be in the foreground (iOS shows no banner
then), notification permission may be off in Settings, or the stored token
may belong to an older install — reopening the app rewrites it.

## Demo APK for the website
`./gradlew assembleDemoRelease` → `app/build/outputs/apk/demo/release/app-demo-release.apk`.
Signed with the same key, so it installs cleanly; points only at
`safebeauty-staging`. Verify before publishing it anywhere:

```bash
unzip -p app/build/outputs/apk/demo/release/app-demo-release.apk resources.arsc \
  | strings | grep -oE 'safebeauty-staging|238802374530' | sort -u
```

It must print `safebeauty-staging` and must NOT print the production project
number `238802374530`.

## Signing key (READ THIS before touching keystores)
Google Play only accepts uploads signed with the **official upload key**:

    SHA1: A0:04:BE:C3:6A:A0:D8:BF:A6:C8:8B:7F:DB:09:36:E5:1C:68:A6:F5
    alias: safebeauty   (store == key password)

- The build now **guards this automatically**: every `assemble…Release` and
  `bundle…Release` task, flavoured or not, depends on `verifyReleaseSigningKey`,
  which fails fast with a clear message if the
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
- Mac installer: `npm run dist:mac` → `desktop-provider/dist/*.dmg` (arm64 + Intel)
- Windows installer: `npm run dist:win` (must run on Windows)
- Loads `/provider`; only PROVIDER-role accounts can sign in.

## Distribute the salon desktop app (for salon owners)
Salon owners get the installers from a **public download page** — they never
build anything:
1. Build + publish the installers: GitHub → **Actions** → **Build salon desktop
   apps** → **Run workflow** (or push a tag `salon-desktop*`). It builds macOS
   (arm64 + Intel) + Windows on GitHub's runners and uploads them to a public
   Release tagged `salon-desktop` with stable filenames.
2. `firebase deploy --only hosting` (once) so the page is live.
3. Share the link: **https://safebeauty.web.app/provider-app** — it auto-detects
   the visitor's OS, offers the right installer, and shows the one-time
   unsigned-app "open anyway" steps in Dari/Pashto/English. The console login
   page (`/provider`) also links to it. Re-run the workflow to ship an update;
   the download URLs stay the same.

## Password-reset web page (`/reset`) — one-time Firebase Console step
The app's real Firebase Auth password is **derived** (`PBKDF2("AUTH:"+password,
salt)`), so Firebase's default hosted reset page would store the raw password
and permanently lock the account out of the app. `public/reset/index.html` is a
trilingual reset page that does the correct derivation and then syncs
`pinHash`/`salt` via the `updatePinHash` callable.

To activate it (once):
1. `firebase deploy --only hosting` (ships the page at
   **https://safebeauty.web.app/reset**).
2. Firebase Console → **Authentication → Templates → Password reset** → pencil
   icon → **Customize action URL** → set it to `https://safebeauty.web.app/reset`
   → Save.

After that, every reset email (from the app's Forgot-password flow) opens this
page on any device/browser, and the account keeps working in the app afterwards.
