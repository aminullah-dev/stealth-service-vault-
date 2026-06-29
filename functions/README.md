# SafeBeauty Cloud Functions — HesabPay Payments

These functions hold the **HesabPay API key server-side** so it never ships in
the Android APK. Two functions:

| Function | Type | Purpose |
|---|---|---|
| `createPaymentSession` | Callable | Creates an `AWAITING_PAYMENT` appointment + a HesabPay checkout session, returns the checkout URL. Reads price & commission server-side so the client can't tamper. |
| `hesabPayWebhook` | HTTPS | Verifies the HesabPay signature, marks the payment `PAID`, splits the amount (platform commission vs. provider net), flips the appointment to `PENDING`, credits `provider_balances`, and notifies the provider. |

## Commission model

Fixed global percentage, read from `platform_config/general.commissionPercent`
(falls back to **10%** if unset). Only admins can change it (see
`firestore.rules`). The split is computed in `createPaymentSession`:

```
commissionAmount = round(amount * commissionPercent / 100)   // platform keeps
providerNet      = amount - commissionAmount                 // provider is owed
```

`provider_balances/{providerId}.owedAmount` accumulates `providerNet` on every
paid booking — this is the ledger of what you owe each salon (you pay them out
separately; HesabPay settles the full amount to your merchant account).

## One-time setup

```bash
cd functions
npm install

# Secrets (never committed):
firebase functions:secrets:set HESAB_API_KEY
firebase functions:secrets:set HESAB_WEBHOOK_SECRET

# Optional non-secret base URL:
cp .env.example .env   # edit if needed

# Deploy
firebase deploy --only functions

# After deploy, register the webhook URL in the HesabPay dashboard:
#   https://us-central1-<PROJECT_ID>.cloudfunctions.net/hesabPayWebhook
```

> Requires the **Blaze** plan (Cloud Functions need billing enabled).

## Notes / TODO

- The HesabPay request/response field names (`url`, `metadata`, signature
  header) are based on public docs; confirm exact field names against your
  HesabPay dashboard and adjust the `body.url || ...` fallbacks in `index.js`.
- Abandoned checkouts leave a stale `AWAITING_PAYMENT` appointment. A scheduled
  cleanup function could delete ones older than ~30 min.
