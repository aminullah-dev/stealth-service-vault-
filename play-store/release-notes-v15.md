# Release notes — versionCode 15 (versionName 2.0)

Paste each block into Play Console → Release → "What's new", per locale.
Keep each under 500 characters. Do NOT put the version number in the body —
Play shows it separately, and hardcoding it is how older notes went stale.

---

## English (en-US)

```
• The salon list opens faster and uses far less mobile data.
• Search for a salon by name.
• Group and wedding bookings: name each guest and choose her services — the salon sees the whole list.
• A group booking is paid in advance, so the salon can set aside its whole team for you.
• For salons: your bookings, calendar and income open instantly, however many years of history you have.
```

## دری (fa-AF)

```
• فهرست سالن‌ها سریع‌تر باز می‌شود و اینترنت بسیار کمتری مصرف می‌کند.
• جست‌وجوی سالن با نام.
• رزرو گروهی و عروسی: نام هر مهمان و خدمات او را وارد کنید — سالن تمام فهرست را می‌بیند.
• رزرو گروهی از پیش پرداخت می‌شود تا سالن بتواند تمام تیمش را برای شما کنار بگذارد.
• برای سالن‌ها: رزروها، تقویم و درآمد شما فوری باز می‌شوند، هر قدر هم سابقه داشته باشید.
```

## پښتو (ps-AF)

```
• د سالونونو لړلیک ژر پرانیستل کیږي او خورا لږ انټرنټ مصرفوي.
• سالون په نوم ولټوئ.
• ډله‌ییز او د واده بکینګ: د هر میلمه نوم او خدمتونه یې وټاکئ — سالون ټول لړلیک ویني.
• ډله‌ییز بکینګ مخکې ورکړل کیږي، ترڅو سالون ستاسو لپاره ټوله ډله ځانګړې کړي.
• د سالونونو لپاره: ستاسو بکینګونه، کلنډر او عاید سمدلاسه پرانیستل کیږي، که هر څومره اوږده سابقه ولرئ.
```

---

## What actually changed in this build

The headline for this market is the first row. Everything else is a consequence of
it: the app stopped downloading collections that grow forever.

| Area | Change |
|---|---|
| **Mobile data** | 25 snapshot listeners had no bound — each was a standing promise to download a whole collection and re-receive it on every write. All 25 are now paginated, filtered on the server, or answered by a `count()` aggregation. On a metered Kabul connection this is the change users will feel. |
| Discovery | Salon browsing is server-side and paginated: district, category, favourites, search and the primary ordering are all decided by Firestore before anything reaches the phone. Salons are searchable by name (`nameKey`, derived server-side). |
| Group bookings | A wedding party is sent as a guest list — each guest's name and services — instead of one flattened service list with the names pasted into the notes. The salon sees who is having what, and the booking holds the whole salon for the time everyone working in parallel actually needs, rather than queueing five guests onto one stylist. |
| Payment | A group booking is prepaid. So is a booking by a customer who has previously not turned up — `noShowCount` has been counted since two-way ratings shipped and now governs something. A first booking is deliberately **not** restricted. |
| Provider — Income | Lifetime totals come from a server-maintained tally (`salon_stats`) instead of counting every appointment the salon has ever taken, on the phone. |
| Provider — Calendar | Loads one month at a time. Paging back through a busy salon's history costs one month, not all of it. |
| Provider — chair time | The app honours `serviceTiming`: a colour leaves the stylist free while it develops, and that gap can be sold. The salon sets it in the **web console** (`/provider`), not in the app. |
| Notifications, bookings, waitlist, refunds | Bounded and ordered by the server. A long-standing customer no longer re-downloads her entire history when one booking changes. |

## Backend dependency

This build needs backend changes that are already live in production as of
2026-08-23: `deriveSalonStats`, `adminRebuildSalonStats`, party fields on
`appointments`, `serviceTiming` on `salons`, the cash-commitment policy in
`createPaymentSession`, and the composite indexes for the paginated queries.

Verify before rolling out, not after:

```bash
gcloud firestore indexes composite list --project=safebeauty --format='value(state)' | sort | uniq -c
```

Every index must read `READY`. An index still `CREATING` makes the query that
needs it fail, and the app's catch turns that into an empty list rather than an
error — a salon with no photos, a deals strip with no deals, and nothing in the
logs.
