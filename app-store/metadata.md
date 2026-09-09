# SafeBeauty — App Store Connect listing texts
# اینجا همه متن‌های اپ‌استور آمده — کپی و پیست کنید

App Store Connect → your app → **App Information** / **Version Information**
(under the build). Content adapted from `play-store/store_listing.md` —
same product, same facts, App Store's own field shapes and limits.

App Store Connect app record already exists: Bundle ID `com.safebeauty.app`,
Team ID `27RXPRW77S`, first build (1.0.0 / build 1) uploaded 2026-09-08 and
confirmed working via TestFlight on a real device 2026-09-09.

---

## Quick fill — "iOS App Version 1.0 / Prepare for Submission" page

Field-by-field, in the order the page shows them, 2026-09-09:

- **Previews and Screenshots** — blank, cannot be filled from this repo. See
  SCREENSHOTS below.
- **Promotional Text** — the English block above (or fa/ps under the
  language switcher next to "English (U.S.)" at the top of that page).
- **Description** — the DESCRIPTION section below, not
  `play-store/store_listing.md` directly — that file's emoji headers are
  what triggered "This field contains one or more invalid characters." on
  2026-09-09. The block below is the same copy with plain headers instead.
- **Keywords** — the English block above.
- **Support URL** — `https://safebeauty.web.app/support`
- **Marketing URL** — leave blank, or `https://safebeauty.web.app/`
- **Version** — already `1.0`, matches `MARKETING_VERSION` in
  `ios/project.yml`. Leave it.
- **Copyright** — `2026 SafeBeauty`
- **Routing App Coverage File** — leave blank (only for apps with a
  universal-link-routed App Clip; not this app)
- **App Clip** / **iMessage App** sections — leave collapsed, not applicable
- **Build** — click **Add Build**, select build **1.0 (1)**, the one already
  confirmed working over TestFlight
- **In-App Purchases and Subscriptions** / **Game Center** — not applicable,
  skip
- **App Review Information**
  - Sign-In required: leave checked
  - User name / Password: the demo account's phone + password (see APP
    REVIEW INFORMATION below — needs a real account you fill in)
  - Contact Information: your own name/phone/email as the developer —
    `aminhashemi979@gmail.com` is the account on file; phone is yours to add
  - Notes: the paragraph under APP REVIEW INFORMATION below
- **App Store Version Release** — recommend **Manually release this
  version**, not Automatically: it lets you confirm Apple's approval before
  the app goes live, rather than it publishing itself the moment review
  passes. The date field under the third option is inert unless that radio
  is selected — ignore it.

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

Embedded here in full, rather than "copy from play-store/store_listing.md" —
that instruction is what caused a real submission error on 2026-09-09:
`play-store/store_listing.md`'s emoji section headers (🌸📅💳⭐💬🔔🌍) tripped
App Store Connect's Description field validator ("This field contains one or
more invalid characters."), pasted live, on the real Prepare-for-Submission
page. Not reproduced with certainty which exact character it was — App Store
Connect gives no character position — so rather than guess at ONE emoji to
drop and risk a second round-trip, every emoji header is replaced with a
plain capitalized line, which is unambiguously safe and is itself a normal
App Store description style. Text otherwise unchanged from
`play-store/store_listing.md`, plus the four-city opening sentence.

If this version ALSO gets rejected, the next suspects, in order, are the em
dashes (—, U+2014, three per language) and the Persian/Pashto script letters
appearing inline inside the English paragraph ("دری", "پښتو") — replace an
em dash with a plain hyphen, or spell the language names in Latin script
only ("Dari", "Pashto") if that happens.

### English

```
SafeBeauty is a private booking platform designed for women in Afghanistan —
Kabul, Herat, Mazar-e-Sharif and Jalalabad. It connects women with trusted,
female-only beauty salons — quickly, safely, and respectfully.

PRIVATE & SECURE
Sign in with your own password or fingerprint. Your account and activity are
protected, all data is encrypted in transit (TLS), and on-device data is
encrypted with SQLCipher (AES-256).

BOOK IN SECONDS
Browse salons across every neighborhood, filter by service (Hair, Makeup,
Nails, Skincare, Eyebrows), pick a specific stylist, and book a real-time
slot — no calls, no waiting.

EASY PAYMENT
Pay securely online with HesabPay, or choose cash at the salon.

LOYALTY & OFFERS
Earn loyalty points on every confirmed booking, and enjoy last-minute deals,
promo codes, and salon offers.

TRANSPARENT REVIEWS
Read real reviews from verified customers and see how salons respond before
you book.

SMART WAITLIST & NOTIFICATIONS
Fully booked? Join the waitlist and get notified the moment a slot opens.

THREE LANGUAGES + DARK MODE
Full support for English, Dari, and Pashto, including right-to-left layout,
plus a comfortable dark mode.

FOR SALON OWNERS
Manage bookings, availability and working hours, service prices, staff,
gallery photos, offers, and income analytics — all from a dedicated
dashboard, in the app or on the web.
```

### دری (Dari)

```
سیف‌بیوتی یک پلتفرم رزرو خصوصی است که برای خانم‌های افغانستان طراحی شده —
کابل، هرات، مزار شریف و جلال‌آباد. این اپ خانم‌ها را با سالن‌های زیبایی زنانه
و مورد اعتماد به‌سرعت، ایمن و محترمانه مرتبط می‌کند.

خصوصی و امن
با رمز یا اثر انگشت خود وارد شوید. حساب و فعالیت شما محافظت می‌شود، همه
داده‌ها هنگام انتقال (TLS) رمزگذاری می‌شوند و داده‌های روی دستگاه با
SQLCipher (AES-256) رمزگذاری شده‌اند.

رزرو در چند ثانیه
سالن‌ها را در همه محله‌ها جستجو کنید، بر اساس خدمت (مو، آرایش، ناخن، مراقبت
پوست، ابرو) فیلتر کنید، آرایشگر مشخص انتخاب کنید و وقت آنی بگیرید — بدون
تماس، بدون انتظار.

پرداخت آسان
به‌صورت آنلاین با HesabPay پرداخت کنید، یا نقدی در سالن.

وفاداری و آفرها
به ازای هر رزرو تایید شده امتیاز بگیرید و از تخفیف‌های لحظه‌آخری، کد تخفیف و
آفرهای سالن بهره‌مند شوید.

نظرات شفاف
نظرات واقعی مشتریان تایید‌شده را بخوانید و پاسخ سالن‌ها را ببینید.

لیست انتظار هوشمند و اعلان‌ها
وقت کامل است؟ در لیست انتظار ثبت‌نام کنید تا به‌محض خالی شدن وقت خبر شوید.

سه زبان و حالت تاریک
پشتیبانی کامل از English، دری و پښتو، شامل چیدمان راست‌به‌چپ و حالت تاریک.

برای صاحبان سالن
رزروها، ساعات کاری، قیمت خدمات، کارکنان، گالری عکس، آفرها و درآمد را از یک
داشبورد اختصاصی (در اپ یا وب) مدیریت کنید.
```

### پښتو (Pashto)

```
سیف‌بیوتي یو شخصي د بکینګ پلیټفارم دی چې د افغانستان ښځو لپاره جوړ شوی —
کابل، هرات، مزار شریف او جلال‌آباد. دا اپ ښځې د باوروړو، یوازې د ښځو
سالونونو سره په چټکه، خوندي او درناوي سره نښلوي.

شخصي او خوندي
د خپل پټ نوم یا ګوته‌نښې سره ننوځئ. ستاسو حساب او فعالیت خوندي دی، ټول ډیټا
د لیږد پرمهال (TLS) کوډ کیږي، او په وسیله کې ډیټا د SQLCipher (AES-256) سره
کوډ شوی دی.

په څو ثانیو کې بک کول
سالونونه په ټولو ګاونډونو کې ولټوئ، د خدمت له مخې فلټر کړئ (ویښتان، سینګار،
نوکان، پوستکي پاملرنه، وروځې)، ځانګړی سټایلست وټاکئ او سمدلاسه وخت واخلئ.

اسانه تادیه
په آنلاین ډول د HesabPay سره تادیه وکړئ، یا په سالون کې نغدي.

وفاداري او وړاندیزونه
د هر تایید شوي بک لپاره ټکي ترلاسه کړئ او د وروستۍ شیبې تخفیفونو، کوډونو او
د سالون وړاندیزونو څخه ګټه واخلئ.

شفاف نظرونه
د تایید شوو پیرودونکو ریښتیني نظرونه ولولئ او د سالونونو ځوابونه وګورئ.

هوښیار د انتظار لیست او خبرتیاوې
وخت نشته؟ د انتظار لیست ته ورننوځئ چې د وخت خالي کیدو سره سم خبر شئ.

درې ژبې او تیاره حالت
د English، دری او پښتو بشپړه ملاتړ، د ښي‌خوا اویچپه لوري جوړښت او تیاره حالت
سره.

د سالون خاوندانو لپاره
بکینګونه، د کار ساعتونه، د خدماتو بیې، کارمندان، ګالري، وړاندیزونه او عاید له
یوه ځانګړي ډشبورډ (په اپ یا وب کې) اداره کړئ.
```


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
https://safebeauty.web.app/support

Written and deployed 2026-09-09 specifically for this field — the Hosting
root (`/`) is NOT a support page, it 302-redirects to `/get`, the Android
download landing page ("Get it on Google Play" / "I have an iPhone"), which
would have confused an App Store reviewer looking for how a user gets help.
`/support` is real: in-app Support tab pointer, the support email, an FAQ
(password reset, account deletion, cancelling a booking, salon
registration), and links to `/privacy` and `/terms`. Trilingual, same design
as `/terms` and `/privacy`.

## MARKETING URL  (optional)
Leave blank, or https://safebeauty.web.app/ — the download landing page is
the right one for THIS field, unlike Support above.

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
