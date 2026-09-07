# Release notes — versionCode 19 (versionName 2.1.2)

Covers versionCode 16 through 19 — no "What's new" text was written for 16,
17 or 18, so this is what changed for anyone updating from v2.0 (15), the
last version with a published note.

Paste each block into Play Console → Release → "What's new", per locale.
Keep each under 500 characters. Do NOT put the version number in the body —
Play shows it separately, and hardcoding it is how older notes went stale.

---

## English (en-US)

```
• Salon profiles now show their own photos instead of a plain tile.
• Sending a message to a salon now actually reaches them — it never did before.
• Fixed a bug where changing your password could lock you out of your account.
• Dates now display correctly in Dari and Pashto.
• Registration is more reliable, especially on a weak connection.
• Neighbourhood and district lists are now accurate for Kabul, Herat, Mazar and Jalalabad.
• You can now screenshot your bookings to share with a friend.
```

## دری (fa-AF)

```
• حالا صفحهٔ سالن عکس واقعی سالن را نشان می‌دهد، نه یک کاشی رنگی.
• پیامی که به سالن می‌فرستید حالا واقعاً به دستشان می‌رسد.
• یک باگ رفع شد که با تغییر رمز عبور، امکان داشت از حساب‌تان بیرون بمانید.
• تاریخ‌ها حالا در دری و پشتو درست نمایش داده می‌شوند.
• ثبت‌نام حالا مطمئن‌تر است، به‌خصوص روی اینترنت ضعیف.
• فهرست ناحیه و محلهٔ کابل، هرات، مزار و جلال‌آباد درست شد.
• حالا می‌توانید از رزروهای خود عکس بگیرید و برای یک دوست بفرستید.
```

## پښتو (ps-AF)

```
• اوس د سالون پاڼه د سالون اصلي انځور ښیي، نه یوه رنګه چوکاټ.
• هغه پیغام چې سالون ته یې لیږئ، اوس واقعیاً هغوی ته رسیږي.
• یو بګ سم شو چې د پټنوم بدلولو پر مهال یې ستاسو حساب بندولی شو.
• نیټې اوس په دري او پښتو کې سمې ښکاري.
• نوم لیکنه اوس ډاډمنه ده، په ځانګړې توګه په کمزوري انټرنټ کې.
• د کابل، هرات، مزار او جلال‌آباد د سیمو او ولسوالیو لړلیک سم شو.
• اوس کولی شئ د خپلو بکینګونو انځور واخلئ او یو ملګري ته یې ولیږئ.
```

Not independently reviewed by a native Pashto speaker — the phrasing follows
present-tense, subject-agreeing constructions throughout to avoid the
split-ergative past-transitive pitfall, but a native pass before publishing
is still worth it.

---

## What actually changed, v16 → v19

The store text is the fraction of this that a customer notices. Most of the
range was reliability and security work with no UI at all.

| Area | Change |
|---|---|
| **Registration** (v17, "the release that makes registration work") | Moved to a server callable (`registerAccount`) so a failed write rolls back cleanly instead of leaving a half-created account; Android had been reporting success on a run that had actually failed. |
| **Security** | `firestore.rules`: the client-writable `users` create path was closed — it allowed a client-chosen document id, which was the primitive behind a permanent account lockout, referral-credit theft, and stored XSS in the admin console (4975d16). `FLAG_SECURE` was removed (749445e) — deliberately: it blocked every screenshot app-wide, including a customer sharing her own booking with a friend, which was this product's only referral mechanism. |
| **Messaging** | `chat_messages` had no trigger at all — a salon never received a notification for a message sent to it, on either platform, since chat shipped. |
| **Money** | A cash booking (still PENDING, salon has not accepted it) was reaching the same reminder sweep as a paid one and telling the customer "your payment is being refunded" for a payment that was never taken. The founding-salon commission-free offer (`sb-owner-founding.json`) had no code behind it — `isCommissionFree` now reads the salon's own `confirmedCount`. |
| **Phone validation** | A Dari or Pashto keyboard's native digits (۰۱۲۳۴۵۶۷۸۹) passed the app's Unicode-aware digit check but were stripped by the server's ASCII-only one — a number typed correctly on a Dari keyboard could register as just "+93". |
| **Password change** | `ChangePinViewModel` wrote the new Firebase Auth password before confirming the matching hash update; a failure between the two left both the new and the old password rejected. |
| **RTL rendering** | Dates rendered backwards under a right-to-left paragraph — "9 Sep, 5:00 AM" showed as "Sep, 5:00 AM 9" — confirmed with `java.text.Bidi` on the exact string rather than reasoned about. |
| **Salon list** | `coverImageUrl` existed on the model and rendered nowhere; every salon showed a coloured initial tile even when it had uploaded a real photo. |
| **Location data** | Herat, Jalalabad and Mazar districts/neighbourhoods were pulled from the municipalities' own lists rather than approximated; the provider and customer pickers were split into city → district → neighbourhood instead of one flat list of 121 areas. |
| **Motion** | Provider tabs and screen transitions are swipeable; `selectedTab` is now derived from the pager instead of tracked separately, which had let the tab underline and the visible screen disagree. |

## Backend / rules dependency

This range needs the rules and functions already deployed alongside it —
`firestore.rules` (closed `users` create), `registerAccount`,
`deriveSalonStats` if not already live from v2.0. Confirm before rollout:

```bash
firebase deploy --only firestore:rules,functions --project safebeauty --dry-run 2>&1 | tail -5
```

Nothing here should show as pending; if it does, the rules/functions deploy
is behind this APK and must go out first — an old rule rejecting the new
client shape fails as a silent permission error, not a crash.
