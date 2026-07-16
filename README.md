# SafeBeauty

A private beauty‑salon booking marketplace for women in Afghanistan — connecting
customers with trusted, women‑only salons quickly, safely, and respectfully.
Fully trilingual (English · دری · پښتو) with right‑to‑left support and a dark mode.

## What's inside

| Part | Tech |
|------|------|
| **Android app** (`app/`) | Kotlin · Jetpack Compose · Hilt · Firebase. Customer, provider, and admin experiences in one app. |
| **Cloud Functions** (`functions/`) | Node 22, Firebase Functions v2. Payments, commission split, KYC review, moderation, and all server‑authoritative logic. Pure money/slot logic is unit‑tested. |
| **Security rules** | `firestore.rules` · `storage.rules` |
| **Web consoles** (`public/`) | Self‑contained Firebase SPAs: `/admin` (platform admin) and `/provider` (salon owner), both with light/dark themes. |
| **Desktop apps** (`desktop/`, `desktop-provider/`) | Electron wrappers that open the hosted consoles as native Mac/Windows apps, with macOS Touch ID. |

## Features
- **Customers** — browse salons by Kabul neighborhood and service, pick a stylist,
  book a real‑time slot, pay online (HesabPay) or cash, earn loyalty points, use
  promo/referral/offers, review salons, join waitlists, and chat with the salon.
- **Salon owners** — manage bookings, working hours, service prices, staff,
  gallery photos, offers, and income; accept/decline requests; from the app or the
  web console.
- **Platform admin** — approvals, KYC review, user/salon management, finance,
  payouts/refunds, promos, reports, multi‑admin, canned support replies, and an
  audit log.

## Security & privacy
- Server‑authoritative mutations (bookings, payments, payouts, KYC, roles) go
  through Cloud Functions; Firestore/Storage rules leave clients no direct write
  path for sensitive data.
- Phone + password auth (PBKDF2), on‑device data encrypted with SQLCipher, all
  network traffic over TLS. See `play-store/privacy-policy.html`.

## Building & deploying
- **Run/deploy:** see [`DEPLOY.md`](DEPLOY.md) (what‑changed → which‑command).
- **Contributor guide:** see [`CLAUDE.md`](CLAUDE.md) (architecture, conventions,
  gotchas).
- **Play Store:** listing texts, release notes, privacy policy, and the go‑live
  checklist live in [`play-store/`](play-store/).

Backend is Firebase (project `safebeauty`, Blaze plan). The Android app's
`applicationId` is `com.security.stealthapp`.
