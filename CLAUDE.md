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
  design system/auth as admin, but PROVIDER‑role only), `provider-app/index.html`
  (trilingual download page for the salon desktop installers), `reset/index.html`
  (trilingual password-reset page — the custom Firebase Auth action URL; it does
  the PBKDF2 derivation and syncs pinHash/salt via `updatePinHash`, see
  DEPLOY.md), `privacy.html`,
  `terms.html`, `payment/`. `cleanUrls` serves them at `/admin`, `/provider`,
  `/provider-app`, `/reset`. The salon installers are built + published to a public GitHub
  Release (tag `salon-desktop`) by `.github/workflows/salon-desktop.yml`; the
  download page links to those release assets by stable filename.
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
- **Admin control center**: admin‑only callables (all gated via
  `assertAdmin(request)` → role `ADMIN`, and audited to `admin_audit`) let the
  platform admin resolve any account issue — `grantAdmin`/`revokeAdmin`
  (multi‑admin; can't remove the last one), `adminResetPassword` (rewrites salt +
  pinHash + the derived Firebase Auth password), `adminUpdateUser` (name/phone,
  phone re‑normalized + uniqueness‑checked), `adminAdjustProviderBalance`,
  `adminGrantCredit`. The web admin console surfaces these in a per‑user **Manage**
  modal + an **Admins** tab. `support_templates` holds canned support replies
  (admin‑only) the Support tab can copy/insert; seeded with trilingual defaults.

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
- **Marketing/social images** (Instagram posts, stories, banners): deliver
  exactly ONE image per language — Dari gets the LIGHT (cream) variant, Pashto
  gets the DARK (deep-rose) variant. Don't produce both color variants per
  language. Build them as HTML (Vazirmatn font, brand rose/gold palette) and
  render with the pre-installed headless Chromium; post 1080×1350, story
  1080×1920.

## Agents (`.claude/agents/`)
- **`verify-live`** — proves a change is actually live and actually working,
  from production and staging rather than from the repository. Run it after any
  deploy, after any backfill, or when a number looks wrong. It exists because
  this codebase's recurring defect is not code that breaks but code that looks
  healthy and has never run: a backup that wrote no files, a sweep never
  invoked, a Health tab no browser could read, derived fields only new writes
  populate. Those are absences, not failures, and nothing else looks for them.
- **`review-safebeauty`** — reviews a diff against the invariants here that were
  learned by breaking them: bounded reads, `orderBy` dropping documents that
  lack the field, index direction, a trigger needing a backfill, server-written
  collections having no rules, the trilingual 4× rule, client and server slot
  maths agreeing. Run it before committing anything touching queries, rules,
  money, the slot maths, or strings.

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
