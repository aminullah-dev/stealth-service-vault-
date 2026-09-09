# SafeBeauty — App Store Connect listing texts
# اینجا همه متن‌های اپ‌استور آمده — کپی و پیست کنید

App Store Connect → your app → **App Information** / **Version Information**
(under the build). Content adapted from `play-store/store_listing.md` —
same product, same facts, App Store's own field shapes and limits.

App Store Connect app record already exists: Bundle ID `com.safebeauty.app`,
Team ID `27RXPRW77S`, first build (1.0.0 / build 1) uploaded 2026-09-08 and
confirmed working via TestFlight on a real device 2026-09-09.

---

## APP NAME  (30 chars max)
SafeBeauty


---

## SUBTITLE  (30 chars max — shown under the name on the product page)

### English
Private Salon Booking

### دری (Dari)
رزرو خصوصی سالن زیبایی

### پښتو (Pashto)
د سالون شخصي بکینګ


---

## PROMOTIONAL TEXT  (170 chars max — the only field editable without a new build)

### English
Book trusted, women-only beauty salons across Kabul, Herat, Mazar-e-Sharif and Jalalabad — private, in seconds, with real-time slots.

### دری (Dari)
سالن‌های زیبایی زنانه و مورد اعتماد را در کابل، هرات، مزار شریف و جلال‌آباد رزرو کنید — خصوصی، در چند ثانیه، با وقت آنی.

### پښتو (Pashto)
د کابل، هرات، مزار شریف او جلال‌آباد باوروړي، یوازې د ښځو سالونونه بکه کړئ — شخصي، په څو ثانیو کې، له سمدستي وختونو سره.


---

## DESCRIPTION  (4000 chars max)

Same text as `play-store/store_listing.md`'s FULL DESCRIPTION, all three
languages — App Store's limit is the same 4000 characters and nothing about
the product differs by platform. Copy directly from there rather than
duplicating it here and letting the two drift apart. One line changes: that
file's opening sentence says "designed for women in Kabul" — this app now
also serves Herat, Mazar-e-Sharif and Jalalabad (confirmed live, see
`ios/SafeBeautyCore/Sources/SafeBeautyCore/Areas.swift`), so open with:

> SafeBeauty is a private booking platform designed for women in Afghanistan
> — Kabul, Herat, Mazar-e-Sharif and Jalalabad. It connects women with
> trusted, female-only beauty salons — quickly, safely, and respectfully.

in place of that file's first sentence, in all three languages, everywhere
it's pasted (App Store here, and Play Store the next time
`play-store/store_listing.md` is touched — not changed there now, surgically
out of scope for this file).


---

## KEYWORDS  (100 chars max, comma-separated, no spaces — used for search only, not shown to users)

### English
beauty,salon,booking,women,private,kabul,afghanistan,nails,hair,makeup

### دری (Dari)
زیبایی,سالن,رزرو,خانم,خصوصی,کابل,افغانستان,ناخن,مو,آرایش

### پښتو (Pashto)
ښکلا,سالون,بکینګ,ښځې,شخصي,کابل,افغانستان,نوکان,ویښتان,سینګار


---

## SUPPORT URL  (required)
https://safebeauty.web.app/

(Or a dedicated support page if one gets built — this is the app's own
Hosting root, which is live today and reachable.)

## MARKETING URL  (optional)
Leave blank, or https://safebeauty.web.app/ — same reasoning as Support URL.

## PRIVACY POLICY URL  (required)
https://linumic.com/safebeauty-privacy-policy/

Live, checked 2026-09-09 — English/Dari/Pashto, current data practices
(gift cards, wallet, waitlist, KYC for all users, 30-day KYC photo purge,
account-deletion anonymization). Same URL to use for Play Console's privacy
policy field, so both listings point at one document instead of two that can
drift apart.

## COPYRIGHT
2026 SafeBeauty


---

## APP PRIVACY  (the "App Privacy" questionnaire — App Store's shape, not Play's Data Safety table, same underlying facts)

For each data type, App Store Connect asks three questions: collected? →
linked to identity? → used for tracking? **Nothing here is used for
tracking** (no ad network, no cross-app/cross-site identifier use) — answer
"No" to the tracking question for every row below, and "No" to the top-level
"Do you or your third-party partners use data for tracking purposes?"
gate before the table even starts.

| Data type | Collected | Linked to user | Purpose |
|---|---|---|---|
| Name | Yes | Yes | App Functionality |
| Phone Number | Yes | Yes | App Functionality |
| Email Address | Yes (optional) | Yes | App Functionality (password recovery; shared with HesabPay at online checkout) |
| Physical Address | Yes (KYC: province/address) | Yes | App Functionality |
| Other User Content (booking notes, reviews) | Yes | Yes | App Functionality |
| Photos | Yes (profile, salon gallery, review, KYC tazkira + selfie) | Yes | App Functionality |
| Precise Location | Yes (salon owners only, pinning their own salon) | Yes | App Functionality |
| Coarse Location | Yes (customers sorting by distance) | Yes | App Functionality |
| Purchase History | Yes (bookings, gift cards, loyalty) | Yes | App Functionality |
| Other Financial Info | Yes (wallet balance, gift-card amounts — never card/bank numbers) | Yes | App Functionality |
| Crash Data | Yes (Firebase Crashlytics) | No | App Functionality |
| Performance Data | Yes (Firebase Crashlytics) | No | App Functionality |
| Identifiers (Device ID) | Yes (Firebase Installation ID) | No | App Functionality |

Everything else on Apple's list — Health, Fitness, Contacts, Browsing
History, Search History, Sensitive Info beyond the KYC document number
itself, Advertising Data — **not collected**. (The tazkira/national-ID number
is the one field that may need "Sensitive Info" ticked rather than sitting
only under Physical Address — check Apple's current category list at
submission time, since this table is a snapshot, not a live link into their
UI.)


---

## AGE RATING  (App Store's questionnaire — same underlying facts as Play's Content rating in `store_listing.md`)

- No objectionable content in any Apple category (violence, sexual content,
  profanity, horror, gambling, alcohol/tobacco/drugs, etc.) — answer "None"
  throughout.
- Unrestricted Web Access: No.
- Contains User Generated Content (reviews): Yes — this alone does not
  force a high rating, but declare it; Apple asks a follow-up about
  moderation — this app has one (`resolveContentReport` / `moderation_archive`
  in `functions/domains/content.js`), so answer that content is moderated.
- Expected result: **4+**, same practical floor as Play's rating, though the
  Age Rating screen also asks about account creation and in-app purchase —
  answer Yes to account creation, and declare HesabPay booking payments under
  the purchase question the same way `store_listing.md` frames them for Play.


---

## APP REVIEW INFORMATION  (private — shown only to Apple's reviewer, not customers)

Contact info: use the developer account's own name/email/phone (Team ID
`27RXPRW77S`).

**Sign-in required**: Yes.

**Demo account** — same shape as `play-store/store_listing.md`'s Notes to
Reviewer, filled in before submitting:

    Phone:    <FILL IN a real demo phone, e.g. +93700000000>
    Password: <FILL IN the demo password>

**Notes**: SafeBeauty is a beauty-salon booking marketplace for women in
Afghanistan. Customers book appointments with female-only salons; salon
owners manage their business from a separate provider mode; a platform
admin moderates the marketplace from a web console (not part of this app).
Payments are processed through HesabPay, a local Afghan payment provider —
reviewing an online payment will attempt a real charge; please book with
**Cash** instead to reach a confirmed booking without moving money.


---

## VERSION RELEASE NOTES  ("What's New in This Version")

First submission — 1.0.0 (build 1) — needs no "what's new" text (App Store
skips that field for a first version). From the second submission on, reuse
`play-store/release-notes-v19.md`'s English block directly; App Store has no
character ceiling as tight as Play's 500, and the same three-language text
already exists there. Don't maintain a second copy here.


---

## SCREENSHOTS  — not produced yet, and cannot be from the Simulator

App Store requires at minimum a 6.7" (iPhone 15/16 Pro Max class) screenshot
set; 6.5" and 5.5" sets are reused from the 6.7" set if not supplied
separately, but Apple's own guidance is to supply what you can natively.

Why not simulator-captured: the Simulator's iOS Simulator SDK signs every
build ad-hoc with empty entitlements regardless of team (see CLAUDE.md's
Gotchas, confirmed 2026-09-08) — sign-in fails there specifically, so no
signed-in screen (salon list, booking, provider dashboard) can be captured
on a Mac. The pre-login onboarding carousel CAN be captured on the
Simulator; everything a customer sees after signing in cannot.

What CAN produce them, in order of effort:
1. **The phone already running TestFlight** (screenshots already sent
   2026-09-09) — the fastest path is retaking a clean set on that same
   device at the right size class, with demo content rather than a real
   account's data.
2. Xcode's own screenshot automation (`xcodebuild test` with a UI test plan
   that navigates and calls `XCUIScreen.main.screenshot()`) — real
   signed-in screens, captured on a physical device attached to this Mac,
   which sidesteps the Simulator entitlement gap entirely. Not built yet;
   ask if wanted.
