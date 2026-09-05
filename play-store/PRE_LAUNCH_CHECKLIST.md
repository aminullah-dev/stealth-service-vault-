# SafeBeauty — Pre-Launch Checklist (v2.0 / versionCode 15)

Work top-to-bottom. Anything in **🔴 blocker** must pass before you upload the
AAB. 🟠 = important, 🟡 = polish. Check the box when done.

---

## 0. Order of operations (do NOT skip)
The new app build depends on new backend (callables + rules). Deploy the
backend **before** you roll the app out to users, or approved users hit
"permission denied" / missing functions.

```bash
cd ~/StudioProjects/stealth-service-vault-
git pull origin claude/stealth-android-vault-4zr1d3
cd functions && npm run test:all && cd ..   # unit 193, rules 23, concurrency 17, settlement 8
firebase deploy --only functions,firestore:rules,storage,hosting
```

- [ ] 🔴 `npm run test:all` is green — **193** unit, **23** rules, **17** concurrency, **8** settlement
- [ ] 🔴 `npm run lint` reports **0 errors** (warnings are fine)
- [ ] 🔴 `firebase deploy` finished with **Deploy complete!** (functions + rules + storage + hosting all green — no `npm ci` lock error)
- [ ] 🔴 Firestore **rules deployed** (a rules change that isn't deployed = silent "permission denied" in the app)

---

## 0b. Indexes must be READY before the app reaches anyone 🔴

Every paginated query in this build depends on a composite index. Firestore does
not fail loudly when one is missing or still building: the query returns
FAILED_PRECONDITION, the app's `catch` turns that into an empty list, and the
customer sees a salon with no photos and a Deals strip with no deals. Nothing
appears in the logs.

```bash
gcloud firestore indexes composite list --project=safebeauty \
  --format='value(state)' | sort | uniq -c
```

- [ ] 🔴 Every index reads **READY** — none `CREATING`
- [ ] 🔴 The count matches `firestore.indexes.json` (`node -e "console.log(require('./firestore.indexes.json').indexes.length)"`)
- [ ] 🔴 `cd functions && npm test` passes the index test — it fails if any index names a collection no code queries. Three did: `gallery` and `offers` instead of `salon_gallery` and `salon_offers`.

---

## 1. Build & signing 🔴
- [ ] `versionCode = 15`, `versionName = "2.0"` in `app/build.gradle.kts` (must be **higher** than the last uploaded code — 14)
- [ ] Official upload keystore is in place; `keystore.properties` points at it
- [ ] Build the **release AAB** in Android Studio → **Build → Generate Signed Bundle / APK → Android App Bundle → release**
- [ ] The guardrail printed **`✅ Release signing key verified`** (if it threw "Wrong signing key", the keystore is wrong — fix before uploading)
- [ ] Output exists: `app/build/outputs/bundle/release/app-release.aab`

## 2. Test the RELEASE build on a real device 🔴
> This is the #1 pre-launch trap: R8/ProGuard can break Firestore deserialization
> even when debug works. Install the **release** AAB/APK (not debug) and verify:
- [ ] 🔴 App installs and opens to the sign-in screen
- [ ] 🔴 PIN / password sign-in and app-lock work
- [ ] 🔴 Register a new account → salons **load** (proves Firestore `toObject` still works under R8)
- [ ] 🔴 Sign in / sign out / fingerprint unlock
- [ ] 🔴 Customer: browse → open a salon → **book + pay with real HesabPay** → booking reaches PENDING
- [ ] 🔴 Provider: **Accept** and **Decline** a booking; decline triggers the refund record
- [ ] 🟠 KYC submit (tazkira + selfie + birth year/dates) → status PENDING
- [ ] 🟠 Neighborhood filter shows the full Kabul list; provider district dropdown saves
- [ ] 🟠 Push notification arrives (booking confirmed / announcement)
- [ ] 🟠 Dark mode + all three languages (English / Dari / Pashto) render correctly, incl. RTL

## 3. Payments (HesabPay) 🔴
- [ ] 🔴 One real **online** payment end-to-end: money leaves → webhook flips AWAITING_PAYMENT → PENDING
- [ ] 🔴 One **cash** booking: confirmed immediately, commission shows as provider debt
- [ ] 🟠 A cancelled/declined paid booking creates a **refund_request** the admin can see
- [ ] 🟠 A promo code + referral credit apply correctly at checkout (amount matches)

## 4. Admin & web consoles 🟠
- [ ] There is at least **one ADMIN** account (first admin is set manually in Firestore; after that use the Admins tab)
- [ ] `https://safebeauty.web.app/admin` and `/provider` load and sign in
- [ ] Admin: reset a password, grant credit, adjust balance, approve KYC → each appears in the **Audit** tab
- [ ] Support **message templates** load (press "Add default replies" once)
- [ ] Provider console: gallery upload, staff add, profile save all work

## 5. Play Console — release setup 🟠
- [ ] Upload `app-release.aab` to a new release (Production or a testing track first — **closed testing recommended for the first roll**)
- [ ] Paste **What's new** for all 3 locales from `release-notes-v10.md` (each < 500 chars ✓)
- [ ] App name / short / full description from `store_listing.md` (en / fa / ps)
- [ ] Screenshots (phone), feature graphic (`feature_graphic.png`), app icon (`icon_512.png`) uploaded
- [ ] `applicationId` stays **`com.security.stealthapp`** — an existing listing's package can never change

## 6. Play Console — declarations 🔴
- [ ] 🔴 **Privacy Policy URL** set (host `play-store/privacy-policy.html`, e.g. at `safebeauty.web.app/privacy`)
- [ ] 🔴 **Data safety** form filled — declare what's collected and that it's encrypted in transit:
  - Personal: name, phone number
  - Sensitive / identity: **tazkira number + tazkira & selfie photos, birth year** (KYC)
  - Location: approximate location (salon distance/directions)
  - Photos: profile / gallery / review images
  - Financial: payment is handled by HesabPay (declare accordingly)
  - State: data **is** encrypted in transit; users **can request deletion**
- [ ] 🔴 **Ads / Advertising ID**: the `AD_ID` permission is stripped in the manifest → declare **the app does NOT use an advertising ID**
- [ ] 🔴 **Content rating** questionnaire completed
- [ ] 🔴 **Target audience**: adults (women's beauty booking) — not directed at children
- [ ] 🟠 **App access** instructions for the reviewer: **give Google a demo phone + password** (see `store_listing.md` → Notes to Reviewer) so the reviewer can sign in and reach a seeded salon, or it may be rejected as "non-functional"
- [ ] 🟠 **Permissions**: justify camera/photos (KYC + gallery), location (salon distance), notifications, biometric

## 7. Config & safety sanity 🟠
- [ ] No secrets committed: `keystore.properties`, `*.jks`, `functions/.env.*` are gitignored ✓
- [ ] Crashlytics is on in release (disabled only in debug) — a test crash appears in the console
- [ ] `targetSdk = 35` (meets Play's current requirement)
- [ ] Firebase project is on the **Blaze** plan (Cloud Functions require it) ✓
- [ ] HesabPay keys/webhook secret set as function secrets, not in code

## 8. Post-launch watch 🟡
- [ ] Watch Crashlytics for the first 24–48h (release-only R8 crashes surface here)
- [ ] Watch `refund_requests` and `provider_balances` for stuck rows
- [ ] Watch the Support tab for onboarding questions
- [ ] Keep the salon-desktop / admin consoles in sync: any console change = `firebase deploy --only hosting` (no app rebuild)

---

### Fastest path to "shippable"
1. Deploy backend (section 0) ✅
2. Build signed release AAB (section 1) ✅
3. **Install the release build and run the 🔴 smoke tests** (sections 2–3) — this catches the R8/payment traps ✅
4. Fill Play declarations (section 6) + provide a reviewer demo login ✅
5. Roll to **closed testing** first, then promote to production.
