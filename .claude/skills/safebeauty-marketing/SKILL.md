---
name: safebeauty-marketing
description: Write and produce SafeBeauty marketing — social posts, WhatsApp channel messages, community and VIP-group copy, linumic.com pages, launch announcements. Use for any customer-facing, salon-facing or partner-facing promotion, in Dari, Pashto or English. It carries the safety rules that make this product's marketing different from ordinary app marketing, the brand constants, and the image pipeline that renders from the app's own assets.
---

# SafeBeauty marketing

## Read this part first

SafeBeauty sells discretion. The whole architecture says so: the Play
`applicationId` is `com.security.stealthapp` rather than anything resembling the
brand, `FLAG_SECURE` blocks screenshots of the running app, there is a hidden
vault, and the database holds photographs of Afghan women's identity documents.

So the ordinary app-marketing playbook is not merely off-brand here — parts of
it are dangerous. **Every rule below exists because breaking it could identify a
user.** They are not stylistic preferences and they are not negotiable without
the owner saying so explicitly, in the conversation, for that specific piece.

### Never

- **Never publish, repost or quote anything that identifies a customer.** No
  names, no faces, no handles, no partial phone numbers, no "our customer in
  Karte Se". A testimonial is only usable if it is unattributed and carries no
  detail that narrows down who wrote it.
- **Never run a mechanic that makes someone's use of the app visible.** No "tag
  a friend", no "share your booking", no comment-to-enter, no referral post the
  user is asked to publish. The in-app referral code is private and stays that
  way; it is never a public call to action.
- **Never post a screenshot containing real data.** Real bookings, real salon
  names with real times, real balances. (`FLAG_SECURE` means you cannot
  screenshot the running app anyway — if you find yourself with one, ask where
  it came from before using it.)
- **Never name a salon without its owner's written agreement**, and never in a
  way that implies which customers go there.
- **Never imply that a specific person uses the app**, including by replying to
  a public comment in a way that confirms it.

### Ask the owner first

- Any paid advertising with audience targeting or a tracking pixel. Interest
  targeting on a beauty app can itself be a disclosure.
- Any collaboration, giveaway or cross-post with an account you do not control.
- Anything that moves conversation from a channel (where followers are hidden)
  into a group (where they are not). See the channel table.

### The WhatsApp VIP group is the sharp edge

In a normal WhatsApp **group**, every member's phone number is visible to every
other member. A VIP group of SafeBeauty customers is therefore a list of women
who use a beauty app, held on the phone of everyone in it. In an Afghan context
that is a real exposure, not a theoretical one.

A WhatsApp **channel** is the opposite: followers are hidden from each other and
from the admin.

If a VIP group is wanted anyway, the safe shapes are, in order:
1. A **channel** with a second, more selective channel for the VIP tier.
2. A **Community announcement group** (members cannot message, numbers are not
   exposed the way an open group exposes them).
3. Salon owners only — they are businesses and are already public.

Raise this before producing VIP-group content. Do not quietly design around it.

---

## Channels

| Channel | Audience | Privacy | What belongs there |
|---|---|---|---|
| WhatsApp **channel** | Customers | Followers hidden from each other and from admin — safest | New salons, new cities, offers, feature news. One-way. |
| WhatsApp **VIP group** | Customers | **Members see each other's numbers** — see above | Nothing, until reshaped. |
| Community | Mixed | Depends on platform — check before posting | Announcements, salon spotlights (with permission). |
| Instagram / social | Public | Fully public | Brand, cities, features. Never anything user-specific. |
| `linumic.com` | Partners, press, investors | Public | Company narrative, English-first, the parent-brand story. |
| Play Store listing | Discovery | Public | `play-store/store_listing.md` — see "known stale copy" below. |

---

## Audiences and what actually persuades them

**Customers (women).** The product promise is *privacy, then convenience*. Lead
with what stays hidden and what she does not have to do: no phone call, no
waiting, no one told. Never lead with discounts alone — this audience is
choosing on trust. Dari first, Pashto equal, English not needed.

**Salon owners.** They want more bookings, fewer no-shows and no admin. Lead
with filled empty slots and the calendar that manages itself. This audience can
be marketed to openly — they are businesses. Dari and Pashto.

**Partners and investors (`linumic.com`).** Lead with the market and the
discipline: four cities, three languages, server-authoritative money, identity
verification, an audit log. English.

---

## Making the images

Do not hand-build images. The generator renders from the app's own font and its
own launcher vector, so a post cannot drift from the product:

```bash
python3 marketing/generate.py marketing/content/<slug>.json
python3 marketing/generate.py --all
```

Write a spec in `marketing/content/<slug>.json`:

```json
{
  "slug": "kebab-case-name",
  "type": "post",
  "audience": "customers | salons | partners",
  "note": "why this exists — future you will want it",
  "text": {
    "fa": { "sub": "...", "headline": "...", "body": "...", "cta": "...", "cities": "..." },
    "ps": { "sub": "...", "headline": "...", "body": "...", "cta": "...", "cities": "..." }
  }
}
```

Rules the generator enforces so you do not have to remember them:

- **One image per language.** Dari renders **light** (cream), Pashto renders
  **dark** (deep-rose), English follows Dari. The colourway is not a caller
  choice — never ship the same message in both colourways for one language.
- `post` is 1080×1350, `story` is 1080×1920, asserted after rendering. Play and
  Instagram reject off-size images with unhelpful errors.

Output lands in `marketing/out/`. **Look at every image before it goes out** —
the generator guarantees size and brand, not that a headline wrapped well or
that a long Pashto word did not overflow.

Keep the four cities current: `کابل · هرات · مزارشریف · جلال‌آباد`. When a city
is added to `app/.../util/Areas.kt`, it changes here, in
`play-store/generate_feature_graphic.py`, and in the store listing.

## Brand constants

Full reference: `BRANDING.md`. The short version:

- Ink `#8B3A47` · accent `#B76E79` · gold `#D4A853` · cream `#FFF7FB`
- Signature gradient `#EBA9C0 → #B76E79 → #7A2F3D`
- Typeface **Vazirmatn** (in `app/src/main/res/font/`) — never substitute; the
  platform default renders Arabic script badly and it shows immediately
- No letter-spacing on Dari or Pashto. Tracking breaks connected letterforms.

## Language

Every customer-facing piece ships in Dari **and** Pashto. Not one then the other
later — a Pashto speaker seeing a Dari-only channel concludes the product is not
for her, which is the same failure the store banner had when it named one city.

Pashto is not translated Dari. Past-tense transitive verbs agree with the
object, not the subject; get this wrong and the sentence reads as machine
output to a native speaker. If a Pashto line is doing real work, check it
against the `pashto-language` skill before it ships.

## Known stale copy

- `play-store/store_listing.md` still says "designed for women in Kabul"
  (singular). Fix before next pasting that text into Play Console.
