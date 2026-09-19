# Release notes — versionCode 20 (versionName 2.1.3)

Everything since versionCode 19, which was prepared on 2026-09-06 and never
actually uploaded — so this supersedes it, and a user updating from 2.1.2
gets both sets of changes.

Paste each block into Play Console → Release → "What's new", per locale.
Keep each under 500 characters. Do NOT put the version number in the body —
Play shows it separately, and hardcoding it is how older notes went stale.

---

## English (en-US)

```
• Report anything that shouldn't be there — a review, a photo, a post — and block anyone you don't want to see.
• Say what really happened after a visit, even if the salon says otherwise.
• Salons no longer appear twice in the list.
• Blocking a salon now hides its offers too, not just its posts.
• The neighbourhood filter picks the right area in Herat and Mazar-e-Sharif.
• "Leave a review" no longer appears on bookings you have already reviewed.
```

## دری (fa-AF)

```
• هر چیزی که نباید باشد گزارش کنید — نظر، عکس، پست — و هر کسی را نمی‌خواهید ببینید مسدود کنید.
• بعد از مراجعه بگویید واقعاً چه گذشت، حتی اگر سالن چیز دیگری بگوید.
• سالن‌ها دیگر دو بار در فهرست نمی‌آیند.
• با مسدود کردن یک سالن، آفرهایش هم پنهان می‌شود، نه فقط پست‌هایش.
• فیلتر محله در هرات و مزار شریف ناحیهٔ درست را انتخاب می‌کند.
• «نظر بدهید» دیگر روی رزروهایی که نظر داده‌اید نشان داده نمی‌شود.
```

## پښتو (ps-AF)

```
• هر هغه څه چې باید نه وي راپور کړئ — نظر، انځور، پوسټ — او هر څوک چې نه غواړئ ویې ګورئ بند کړئ.
• له مراجعې وروسته ووایاست چې واقعاً څه وشول، که څه هم سالون بل څه وايي.
• سالونونه نور په لړلیک کې دوه ځله نه ښکاري.
• د یوه سالون بندولو سره یې وړاندیزونه هم پټیږي، نه یوازې پوسټونه.
• د ګاوندي فلټر په هرات او مزار شریف کې سمه ناحیه ټاکي.
• «نظر ورکړئ» نور هغو بکینګونو ته نه ښکاري چې نظر مو ورکړی وي.
```

---

## What actually changed, 19 → 20

versionCode 19 was built on 2026-09-06 and sat unused; the AAB on disk
predated commit 8a93b29 by three days, so uploading it would have shipped the
duplicate-salon bug that was already fixed. Rebuilt at 20 instead.

| Area | Change |
|---|---|
| **Moderation** | An app full of other people's words had no way to report any of it. `reportContent` + a moderation queue in the admin console, plus per-user blocking that hides the blocked account's posts, stories, reviews AND offers — the offer half was a follow-up fix after blocking a salon left its deal on screen. |
| **Visit disputes** | The salon could report a customer for not showing up; the customer had no way to say the visit never happened, or that she was turned away. `reportVisit` closes that asymmetry. |
| **Duplicate salons** | Every recommended salon also satisfied the main list and rendered twice. Two salons, both recommended, read as four. |
| **Areas** | The neighbourhood dropdown filtered by the wrong area in Herat and Mazar — labels came from one list, keys from another. |
| **Reviews** | "Leave a review" showed on bookings already reviewed, because the `reviewed` field Android wrote was never read back. |
| **CSV export** | A salon with a comma in its name broke the exported file. Now RFC 4180 quoted. |

## Before rolling out

Backend is already live — the moderation and visit-report callables, their
rules and their indexes were deployed on 2026-09-06. Confirm:

```bash
gcloud firestore indexes composite list --project=safebeauty \
  --format='value(state)' | sort | uniq -c
```

All 73 must read READY.
