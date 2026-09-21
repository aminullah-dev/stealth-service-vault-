# SafeBeauty — App Store Connect listing texts
# اینجا همه متن‌های اپ‌استور آمده — کپی و پیست کنید

App Store Connect → your app → **App Information** / **Version Information**
(under the build). Content adapted from `play-store/store_listing.md` —
same product, same facts, App Store's own field shapes and limits.

App Store Connect app record already exists: Bundle ID `com.safebeauty.app`,
Team ID `27RXPRW77S`, first build (1.0.0 / build 1) uploaded 2026-09-08 and
confirmed working via TestFlight on a real device 2026-09-09.

---

## Must change in 1.0.1 — found 2026-09-21, after 1.0 (6) was approved

1.0 was approved with three claims that are not true, and all three were
checked, not assumed. Promotional text is editable without review and was
corrected live the same day (read back from the API); the other two are locked
to the approved version and ship corrected in 1.0.1.

| Field | Claim on the approved listing | What is true | Evidence |
|---|---|---|---|
| Description (all three languages) | "Kabul, Herat, Mazar-e-Sharif and Jalalabad" | Two salons, both in Kabul | Production `salons`, 2026-09-18 |
| Description, PRIVATE & SECURE | "on-device data is encrypted with SQLCipher (AES-256)" | **False on iOS.** SQLCipher is the Android Room database (`app/build.gradle.kts`, `AppDatabase.kt`); `ios/` contains no SQLCipher and no SQLite at all — it persists to UserDefaults and the Keychain | `grep -ri sqlcipher ios/` → nothing |
| Screenshots | The live Play-era captures: two REAL salons by name with real prices and review counts, and a "TestFlight" back-label in the status bar | The marketing skill requires a salon owner's written agreement to name her salon | The uploaded images themselves |

Replacements that are true: say "Kabul" and nothing about other cities; say
"Sign in with your password, or Face ID / Touch ID once you turn it on;
everything between the app and our servers travels encrypted (TLS)" — and NOT
"your password never leaves your phone", which is also false: sign-in sends it
to `authenticateWithPassword` to be checked. Screenshots: capture the demo
world (`marketing/demo/`), where every salon is invented.

**Also for 1.0.1 — the icon.** `ios/SafeBeauty/Resources/Assets.xcassets/
AppIcon.appiconset/icon-1024.png` is a placeholder: white "SB" on the rose
gradient. Android's launcher, the Play listing and every marketing image use the
flower (`app/src/main/res/drawable/`, rendered by `bloom_svg()` in
marketing/generate.py). A customer who sees the flower in an Instagram post
looks for the flower on the App Store and finds two letters. The icon ships in
the binary, so it needs a new build, not a metadata edit.

1.0 went live on the Afghanistan storefront on 2026-09-21 — availability had
never been set at all (no appAvailabilities resource existed; the listing read
"Removed from App Store" right after Release), now AFG only by the owner's
choice. Public lookup `itunes.apple.com/lookup?id=6810050614&country=af`
returned 1 result the same afternoon.

"women-only" / "female-only" is kept out as well until someone confirms the
platform actually restricts salons to women; nothing in onboarding checks it.

## What is already filed — 2026-09-09

All of this was written to App Store Connect through the API
(`scripts/asc.py`), not by hand, and read back to confirm:

| Field | Value |
|---|---|
| Name | SafeBeauty |
| Subtitle | Private Salon Booking |
| Description | the English block below, 1360 chars |
| Keywords | the English block below, 70 chars |
| Promotional Text | the English block below, 133 chars |
| Support URL | https://safebeauty.web.app/support |
| Marketing URL | https://safebeauty.web.app/ |
| Privacy Policy URL | https://linumic.com/safebeauty-privacy-policy/ |
| Copyright | 2026 SafeBeauty |
| Release | **Manually release this version** |
| Screenshots | 3, APP_IPHONE_67 (1290×2796) |
| Build | 1.0.0 (4) |
| Age rating | filed — see AGE RATING below |
| App Review Information | contact + demo account + notes |
| Primary category | Lifestyle |
| Content rights | Does not use third-party content |
| Price | Free (base territory USA, all territories) |

Still open, and deliberately not filed from here:

- **Dari and Pashto listing localizations are impossible, not pending.**
  Apple does not offer them. Asked the API directly on 2026-09-09 by trying
  to create each one: `fa`, `ps` and `ur` all come back "The language
  specified is not listed for localization", while `ar-SA, en-GB, en-AU, tr,
  hi, fr-FR, de-DE, ru, id, ms, th, vi` are accepted. So the product page in
  Afghanistan shows the en-US listing, and nothing can change that.

  What was done instead: the fa and ps blocks below are NOT wasted — a short
  Dari and Pashto passage now sits at the end of the English description
  saying the app itself is in all three languages and where to switch. Apple
  puts no language restriction on the body of a description, so this is the
  only way an Afghan customer sees her own script on the product page.
  `ar-SA` was deliberately not used: Dari in an Arabic slot is the wrong
  language, not a translation.
- **Screenshots for the other size classes.** The 6.7" set covers the rest by
  scaling, which Apple permits.
- The two screenshots showing an open city/neighbourhood menu were left out
  on purpose: that menu still renders its items left-to-right in Dari and
  Pashto (the tick moved to the right side, the labels did not), and a store
  screenshot of a control we know is wrong is worse than one screenshot
  fewer. Replace them once that is fixed.

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
Book beauty salons in Kabul: see the times that are really free and reserve without a phone call. In Dari, Pashto and English.

### دری (Dari)
سالن‌های زیبایی کابل را رزرو کنید: وقت‌هایی را که واقعاً خالی‌اند ببینید و بدون تماس تلفنی نوبت بگیرید. به دری، پښتو و انگلیسی.

### پښتو (Pashto)
د کابل د ښکلا سالونونه بک کړئ: هغه وختونه وګورئ چې واقعاً خالي دي او پرته له ټلیفونه نوبت ونیسئ. په دري، پښتو او انګلیسي.


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

### English  (1.0.1 — every claim checked against the code, 2026-09-21)

```
SafeBeauty is a booking app for beauty salons in Kabul, made for women in Afghanistan. Browse salons, see the times that are really free, and book without a phone call.

BOOK WITHOUT A PHONE CALL
Browse without an account. Filter by neighbourhood and by service (Hair, Makeup, Nails, Skincare, Eyebrows), pick a stylist, and choose a free time. You sign in only when you book.

PRIVATE & SECURE
Sign in with your password, or with Face ID or Touch ID once you turn it on. Everything between the app and our servers travels encrypted (TLS). Your bookings are visible to you and your salon, not to other customers.

EASY PAYMENT
Pay online with HesabPay, or choose cash at the salon.

LOYALTY & OFFERS
Earn 10 loyalty points for every booking a salon confirms, and use last-minute deals, promo codes and salon offers where salons run them.

HONEST REVIEWS
Reviews come only from customers who had the appointment, and salons can reply.

WAITLIST & NOTIFICATIONS
Fully booked? Join the waitlist and get notified the moment a time opens.

SUPPORT INSIDE THE APP
Message our team from the app. Past conversations are kept, and you can rate each one when it closes.

THREE LANGUAGES + DARK MODE
English, Dari and Pashto, with full right-to-left layout, plus dark mode.

FOR SALON OWNERS
Manage bookings, working hours, service prices, staff, gallery photos, offers and income — in the app or on the web.

— — —

به زبان دری و پښتو نیز در دسترس است. زبان را از پایین صفحهٔ سالن‌ها یا از بخش «حساب من» انتخاب کنید. تمام برنامه، شامل چیدمان راست‌به‌چپ، به هر سه زبان کار می‌کند.

په دري او پښتو کې هم شتون لري. ژبه د سالونونو د پاڼې له لاندې برخې یا د «زما حساب» له برخې وټاکئ. ټول اپ، د ښي‌څخه‌کیڼ لوري جوړښت په ګډون، په دریو ژبو کار کوي.
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

## APP PRIVACY — the ONE thing that cannot be done from here

App Store Connect refuses submission until this is filled AND published, and
it is the only required field with no API behind it: `appDataUsages` is not a
relationship on the `apps` resource at all (checked 2026-09-09 — the app
exposes 40 relationships and not one of them is privacy-related). It is a
web-form-only questionnaire.

**App Store Connect → your app → App Privacy → Get Started**, then answer with
the table below and press **Publish**. Nothing else is outstanding; the moment
this is published the app can be submitted.

Answer "No" to the opening question, "Do you or your third-party partners use
data for tracking purposes?" — there is no ad network and no cross-app
identifier use anywhere in this app.

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

## AGE RATING  — filed 2026-09-09, these are the answers actually on record

Set through the API (`scripts/asc.py`, PATCH /v1/ageRatingDeclarations). Apple
names the valid values in its own validation errors: the frequency fields take
NONE / INFREQUENT_OR_MILD / FREQUENT_OR_INTENSE / INFREQUENT / FREQUENT, and
the rest are plain booleans.

| Declared | Value |
|---|---|
| violence (cartoon, realistic, prolonged/graphic) | NONE |
| profanity or crude humour | NONE |
| mature or suggestive themes | NONE |
| horror or fear themes | NONE |
| alcohol, tobacco, drug use or references | NONE |
| sexual content or nudity (both fields) | NONE |
| simulated gambling · contests | NONE |
| medical or treatment information | NONE |
| guns or other weapons | NONE |
| gambling · loot box · unrestricted web access | false |
| advertising | false — no ad network; Android strips AD_ID too |
| parental controls | false |
| **messaging and chat** | **true** — customer↔salon, and Support |
| **user-generated content** | **true** — reviews, feed posts, comments |
| **social media** | **true** — Discover carries posts, likes and comments |
| health or wellness topics | false — booking a salon is not health information |
| age assurance · social-media age restricted | false |
| kidsAgeBand | left null — not a Kids Category app |
| developerAgeRatingInfoUrl | left null — optional |

The three trues are declared rather than argued away. An app with UGC and chat
that says it has neither is the kind of thing that gets found in review, and
this one does moderate: `reportContent` / `resolveContentReport` /
`moderation_archive` in `functions/domains/content.js`, plus per-user blocking,
all shipped and verified in production.

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
