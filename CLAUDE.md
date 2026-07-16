# SafeBeauty — project guide for Claude Code

SafeBeauty is a beauty‑salon booking marketplace for Afghanistan (customer +
provider + platform admin), plus a web/desktop admin console. Firebase is the
backend for everything.

## Repository layout
- `app/` — Android app (Kotlin, Jetpack Compose, Hilt, Firebase). Package
  `com.safebeauty.app`; **applicationId `com.security.stealthapp`** (they differ
  on purpose — see gotchas). minSdk 26, targetSdk 35.
- `functions/` — Firebase Cloud Functions v2 (Node 22, region `us-central1`).
  Single file `index.js`. Project id `safebeauty`, Blaze plan.
- `firestore.rules` / `storage.rules` — security rules.
- `public/` — Firebase Hosting: `admin/index.html` (self‑contained admin
  console), `provider/index.html` (self‑contained salon‑owner console — same
  design system/auth as admin, but PROVIDER‑role only), `privacy.html`,
  `terms.html`, `payment/`. `cleanUrls` serves them at `/admin` and `/provider`.
- `desktop/` — Electron wrapper that opens the hosted admin console (`/admin`)
  as a native Mac/Windows app, with macOS Touch ID sign‑in.
- `desktop-provider/` — the same Electron wrapper for the salon console
  (`/provider`); separate appId + Touch ID keychain entry so both can coexist.
- `play-store/` — Play Store listing assets.

## How to run / deploy
See **DEPLOY.md** (what‑changed → which‑command). Key rule: an app‑only change
needs no Firebase deploy; a rules change that isn't deployed makes the app
silently hit "permission denied".

## Architecture patterns (important)
- **Identity bridge (`uid_map`)**: the app‑level user id is a
  `UUID.randomUUID()` used as the `users/{uid}` doc id — independent of the
  Firebase Auth uid. Rules resolve `me()` via
  `get(uid_map/{request.auth.uid}).appUid`; functions resolve the caller with
  `resolveAppUser(request)` by auth‑token email. `syncUidMap` populates the
  bridge at login.
- **Auth**: phone + password. `PinHasher` (PBKDF2‑SHA256, 65 536 iters, 256‑bit,
  base64) hashes the password; `deriveAuthPassword` = `hash("AUTH:"+pw, salt)`
  is the real Firebase Auth password. `authenticateWithPassword` looks the user
  up by phone server‑side. The admin web console re‑implements this derivation
  in JS.
- **Phone**: `PhoneUtils.normalizeForLogin` / `normalizeAfghan` canonicalize to
  `+93…`. Uniqueness is enforced **server‑side** via `lookupAccountByPhone`
  (the users collection is not client‑listable). Any phone lookup must normalize.
- **Payments**: HesabPay. `createPaymentSession` handles online (returns a
  checkout URL, webhook flips AWAITING_PAYMENT→PENDING) and cash (confirmed
  immediately, commission becomes a debt on `provider_balances`). Commission and
  every discount apply here — promo code, referral credit, salon offer,
  last‑minute deal and service‑package bundle (discount math is pure/tested in
  `functions/lib/money.js`). It also rejects a slot already taken on the same
  chair (`hasSlotConflict`, `functions/lib/slots.js`).
- **Server‑authoritative mutations**: appointment status changes, payouts,
  refunds, KYC review, reports, promo admin, etc. all go through callables — the
  rules leave clients no direct write path for these. Sensitive/reputation
  fields on user docs are frozen against client writes.

## Conventions
- **Strings**: every user‑facing string lives in `AppStrings.kt` and MUST be
  added in all three language blocks — English, Dari (fa), Pashto (ps). Declare
  the field in the interface once, then add a value in each block (so each key
  appears 4× total). Lambda strings like `{ n -> "…" }` for interpolation.
- **Firestore models** (`FirestoreModels.kt`): Boolean fields whose name starts
  with `is`, or that need a stable stored name, use
  `@get:PropertyName("x") @set:PropertyName("x") var x`.
- **Verify before commit**: `node --check functions/index.js` and `npm test`
  (in `functions/` — Node's built-in runner, no deps) for functions;
  brace/paren balance and 4× string counts for Kotlin/AppStrings. There's no
  Android SDK in the Claude environment, so the app can't be compiled here —
  check imports and balance carefully; the user builds on their Mac.

## Git
- Work on branch `claude/stealth-android-vault-4zr1d3`. Commit + push each
  finished change. Don't open PRs unless asked.

## Gotchas
- `context.packageName` returns the **applicationId** (`com.security.stealthapp`),
  not the code package. Reference classes directly (`MainActivity::class.java`),
  never build a class name from `packageName`.
- Firebase Analytics injects `com.google.android.gms.permission.AD_ID`; it's
  stripped via `tools:node="remove"` in the manifest to avoid the Play "Ad ID"
  declaration error.
- Keystore files (`*.jks`, `keystore.properties`) are gitignored — never commit
  them.
