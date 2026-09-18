# The marketing demo world

Everything here lives in **`safebeauty-staging`** only — the project the `demo`
flavour (`com.security.stealthapp.demo`) points at. Nothing in this folder ever
touches production (`safebeauty`), and nothing in the demo world corresponds to
a real salon, a real customer or a real phone number.

It exists because marketing may not publish real salons, real bookings or real
customers (`.claude/skills/safebeauty-marketing/SKILL.md`), and staging held
zero salons — so there was nothing to photograph.

## The demo customer

Throwaway staging credentials. Not anyone's real account; safe to put in a
runbook, not safe to reuse anywhere else.

| | |
|---|---|
| Phone (as typed on the login screen) | `0700000099` |
| Password | `Demo-mJvKF5m785` |
| Stored phone | `+93700000099` |
| Name in the app | مهمان دیمو |
| App uid (`users/{uid}`) | `52909aff-1165-4a95-bca7-280f0eb7a9fd` |
| Firebase Auth uid | `ymrSnh0W3sRNiLyfeLVnJP9HDFc2` |
| Firebase Auth email | `52909aff11654a95bca7280f0eb7a9fd@sb.app` |
| Referral code | `DEMO01` |

State chosen so no screen is an empty state: `status: APPROVED`,
`kycStatus: APPROVED` (so the Deals strip is unlocked — **no identity document
is stored**, `tazkiraPhotoPath` and `selfiePhotoPath` are deliberately empty),
`loyaltyPoints: 80` (the REGULAR tier, with a visible bar to the next one).

The account was created exactly the way `registerAccount`
(`functions/domains/identity.js`) creates one: a 16-byte base64 salt,
`pinHash = PBKDF2-SHA256(password, salt, 65536, 32 bytes)`, and the real
Firebase Auth password `PBKDF2-SHA256("AUTH:" + password, salt, …)` — so it
signs in through the deployed `authenticateWithPassword` callable like any
other account.

## The salons

Six invented salons in Kabul, all `isAvailable: true`, ordered here by the
rating the list sorts on:

| Salon | Owner (invented) | District | Categories | From |
|---|---|---|---|---|
| سالن یاسمین | لیلا صدیقی | ناحیه ۱۰ – وزیراکبرخان | Hair, Makeup, Skincare | 500 AFN |
| سالن گل سرخ | زرغونه احمدی | ناحیه ۲ – شهرنو | Hair, Makeup | 350 AFN |
| آرایشگاه نسترن | فرشته نوری | ناحیه ۳ – کارته چهار | Makeup, Nails | 400 AFN |
| سالن لاله | عادله کریمی | ناحیه ۴ – کوته سنگی | Hair, Nails, Skincare, Eyebrows | 150 AFN |
| سالن بنفشه | سمیرا رحیمی | ناحیه ۱۱ – خیرخانه | Skincare, Eyebrows | 200 AFN |
| سالن پروانه | نیلوفر امینی | ناحیه ۹ – مکروریان | Hair, Nails, Skincare, Eyebrows | 150 AFN |

Owner phone numbers are all in the `+9370000001x` block and their user
documents carry **no credential** (`pinHash` and `salt` are empty), so nobody
can sign in as a demo salon owner.

`سالن یاسمین` and `سالن گل سرخ` are open on Fridays (10:00–16:00); the other
four keep the default Afghan week from `functions/lib/hours.js`, Friday closed.
That is not decoration — with every salon closed on Friday there are no slots
to photograph on a Friday.

### Two things that have to stay true

- **`rating` and `sortRating` must equal the average of that salon's reviews.**
  The card shows `rating`; the list orders by `sortRating`. Seeding writes
  `rating`, and then the deployed `awardReviewPoints` trigger recomputes it from
  the reviews that land a moment later — so the two drift apart unless something
  puts them back. `reconcile.js` is that something; run it after any re-seed.
- **Every derived discovery field is written by the seed, not left to the
  trigger** (`categories`, `districtKey`, `areaKey`, `city`, `nameKey`,
  `minPrice`, `sortRating`). The customer's queries `orderBy` these, and
  Firestore returns *no* documents that lack the ordering field.

## Reproducing it

`seed/` holds the scripts, and they are idempotent — every document has a
deterministic id, so re-running updates in place rather than duplicating.

```bash
cd marketing/demo/seed
export GCLOUD_TOKEN=$(gcloud auth print-access-token)

node seed.js          # salons, owners, reviews, offers, stats, balances
sleep 25              # let awardReviewPoints fire on any NEW review documents
node reconcile.js     # put rating and sortRating back in agreement
node bookings.js      # the demo customer's bookings (needs account.json)
node support.js       # the support thread, its history and a ratable conversation
node cleanup.js       # drop the stub users awardReviewPoints makes for reviewers
```

`account.js` creates a **new** customer account; it is not idempotent and does
not need re-running — the one above already exists. If you do run it, write the
new phone/password into this file.

Every script routes through `seed/lib.js`, which pins the project to
`safebeauty-staging` and refuses any URL that does not address it.

### Why cleanup.js exists

`awardReviewPoints` does `set(users/{review.customerId}, {loyaltyPoints:
increment(…)}, {merge: true})`, and a merge-set on a missing document creates
it. The invented reviewers have no accounts, so seeding 21 reviews minted 21
nameless `users/` rows and 21 notifications addressed to nobody. Harmless — no
credential, so nothing can sign in as one — but it fills the demo user list
with blank rows. `cleanup.js` removes only rows whose entire content is what
the trigger writes.
