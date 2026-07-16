# SafeBeauty — Google Play Closed Testing guide

A step-by-step path to a **closed testing** release. Do the sections in order.
Anything marked 🔴 will block you if skipped.

> Two big surprises to know up front:
> 1. 🔴 **New personal developer accounts** must run closed testing with **at
>    least 12 testers opted-in for 14 continuous days** before Google will let
>    you apply for production. Start the tester count early.
> 2. 🔴 **HesabPay is real money.** In a closed test, an online booking charges
>    real funds. Tell testers to use **cash bookings**, or point the app at a
>    HesabPay test/sandbox key for the test track (see section 6).

---

## 1. Ship the backend first 🔴
The v1.6 app depends on new callables + rules. Deploy before testers install.

```bash
cd ~/StudioProjects/stealth-service-vault-
git pull origin claude/stealth-android-vault-4zr1d3
cd functions && npm test && cd ..            # expect 95/95
firebase deploy --only functions,firestore:rules,storage,hosting
```
- [ ] Deploy finished with **Deploy complete!**

## 2. Build the signed release AAB 🔴
No Java on your Mac CLI → build from Android Studio.
- Android Studio → **Build → Generate Signed Bundle / APK → Android App Bundle**
- Choose the **release** signing (it reads `keystore.properties`) → Finish
- [ ] Guardrail printed **`✅ Release signing key verified`**
- [ ] File exists: `app/build/outputs/bundle/release/app-release.aab`
- [ ] It's `versionCode 10` / `versionName 1.6`

## 3. Seed content so testers see something 🔴
An empty marketplace looks broken. Before inviting testers:
- [ ] At least one **ADMIN** exists (set `role: "ADMIN"` on your user in the
      Firestore console once; after that use the admin console → Admins tab)
- [ ] Create 2–3 **provider** accounts, approve them (admin → Approvals), and
      add salons with services, prices, hours, and a photo (provider console)
- [ ] Mark those salons **Available** so they show in the app

## 4. Play Console — one-time app setup 🔴
If the app already exists on Play, skip to section 5. For a new app:
- [ ] Create the app (name **SafeBeauty**, app not game, free)
- [ ] Package name must be **`com.security.stealthapp`** (can never change later)
- [ ] Fill **App content** (required before any release):
  - Privacy policy URL — host `play-store/privacy-policy.html` (e.g. at
    `https://safebeauty.web.app/privacy`) and paste the URL
  - **Data safety** — use the accurate table in `store_listing.md`
    (collects name/phone/location/photos/KYC; no advertising ID; TLS; deletion)
  - **Ads**: No ads → declare no advertising ID (AD_ID is stripped)
  - **Content rating** questionnaire (answers in `store_listing.md`)
  - **Target audience**: adults; not directed at children
  - **App access**: give Google a working **demo phone + password** (fill the
    Notes-to-Reviewer block in `store_listing.md`) so review can sign in
- [ ] Store listing: short/full description (en/fa/ps) from `store_listing.md`,
      app icon (`icon_512.png`), feature graphic (`feature_graphic.png`), and
      at least 2 phone screenshots

## 5. Create the Closed testing track
- [ ] **Testing → Closed testing → Create track** (or use the default
      "Alpha" track)
- [ ] **Create new release** → upload `app-release.aab`
- [ ] Paste **Release notes** per language from `release-notes-v10.md`
- [ ] Save → **Review release** → **Start rollout to Closed testing**

## 6. Testers
- [ ] Testers list: **Create an email list** and add your testers' Google
      account emails (or a Google Group). Aim for **≥ 12** if this is a new
      personal account (section 0 rule)
- [ ] Copy the **opt-in URL** (Play shows it on the track page) and send it to
      testers — they must tap it, accept, then install SafeBeauty from Play
- [ ] 🔴 **Payment safety for the test:** either
  - tell testers to book with **Cash** (no real charge), **or**
  - swap the HesabPay key/secret (function secrets) to a **sandbox/test**
    credential for the duration of the test, then restore for production
- [ ] Give testers a short "what to try" note: register → browse a salon →
      book (cash) → leave a review; and one provider login to accept/decline

## 7. What to watch during the test 🟠
- [ ] Install the **release** build yourself first and run section 2–3 of
      `PRE_LAUNCH_CHECKLIST.md` (the R8 / Firestore-deserialization trap)
- [ ] Crashlytics (release-only crashes surface here)
- [ ] `refund_requests` / `provider_balances` for stuck rows
- [ ] Admin **Support** tab for tester questions
- [ ] Collect feedback; ship fixes as a new versionCode (11, 12, …) to the same
      track

## 8. Promote to production (later)
- [ ] For a new personal account: keep ≥ 12 testers opted-in for **14 continuous
      days**, then use **"Apply for production access"**
- [ ] Bump versionCode, upload a fresh AAB to the Production track, and roll out
      (start with a staged % rollout)

---

### The 5-minute version
1. Deploy backend → 2. Build signed AAB → 3. Seed a few salons →
4. Fill App content + Data safety + demo login → 5. Upload AAB to Closed
testing → 6. Add ≥12 testers, send the opt-in link, tell them to pay **cash**.
