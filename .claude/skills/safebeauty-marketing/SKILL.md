---
name: safebeauty-marketing
description: Write and produce SafeBeauty marketing — social posts, WhatsApp channel messages, community and VIP-group copy, linumic.com pages, launch announcements. Use for any customer-facing, salon-facing or partner-facing promotion, in Dari, Pashto or English. It carries the safety rules that make this product's marketing different from ordinary app marketing, the brand constants, and the image pipeline that renders from the app's own assets.
---

# SafeBeauty marketing

## Read this part first

SafeBeauty holds photographs of Afghan women's identity documents, their phone
numbers, and a record of where and when each of them goes. Protecting that from
an attacker is a real job — it is just not a marketing job. It is done in
`firestore.rules`, `storage.rules` and the callables, and nothing you write
here touches it.

**The threat model these rules were built around is retired.** As of
2026-09-04 the owner has lifted both the phone-inspection concern and the
social-visibility one, and `FLAG_SECURE` came off with them. Two things that
used to be stated here were never true anyway: `android:label` has always been
"SafeBeauty", so the launcher always showed the real name and icon, and
`BiometricVault` is a Keystore-backed biometric unlock for the user's own PIN,
not a hiding place.

What that means concretely: **Instagram is a normal customer channel now**, a
woman can screenshot her booking and send it to a friend, and you may write to
her the way you would write to any customer anywhere.

Two rules survive, and neither is about fear:

- **Consent.** Do not publish a person's name, face, or salon without their
  agreement. That is not a threat model, it is what you owe someone who trusted
  you with a booking, and it does not expire when a threat does.
- **Do not hand out phone numbers.** See the WhatsApp note below. Adding someone
  to a group publishes her number to every other member, which invites spam and
  unwanted contact whoever is or is not watching.

### Never

- **Never publish a customer's identity without her agreement.** Name, face,
  handle, phone digits, "our customer in Karte Se". With her agreement it is
  fine — this is a consent rule now, not a safety rule, so the way to get a
  testimonial is to ask for one rather than to anonymise one.
- **Never post a screenshot containing real data.** Real bookings, real salon
  names with real times, real balances. `FLAG_SECURE` used to make this
  impossible by accident; it is gone, so the rule now needs someone to keep it
  on purpose. Screenshot a seeded demo account, never a real one.
- **Never name a salon without its owner's written agreement**, and never in a
  way that implies which customers go there.
- **Never add anyone to a group that exposes their phone number.** See the
  WhatsApp note below — this is the one rule here that never rested on the
  retired threat model.

Tagging, resharing, comment-to-enter and referral posts are all fine now. The
in-app referral code is still not a public call to action, for the ordinary
reason that it is tied to one account's credit.

### Ask the owner first

- Any collaboration, giveaway or cross-post with an account you do not control.
- Anything that publishes a named person or a named salon.
- Any spend. Paid advertising and audience targeting are open now — the
  disclosure argument against them is gone — but the budget is not yours.

### Instagram is a customer channel

It was ruled out on the grounds that a follower list is public, so following a
beauty app disclosed something about the follower. With the social-visibility
concern lifted that argument is gone, and Instagram is simply the largest place
Afghan women are reachable.

So it carries customer copy, salon recruitment, product and brand — the same
mix any consumer marketplace runs. Resharing, tagging and commenting are all
fine. The only thing still off the table is publishing a specific person's
identity without her agreement, which is a consent rule, not a channel rule.

### Supply is the bottleneck, not demand

As of September 2026 there are **two salons, both in Kabul**. Herat,
Mazar-e-Sharif and Jalalabad have none.

Marketing to customers before that changes means sending a woman to an empty
screen, and she does not come back — on a product whose only asset is trust.
So the weighting is salon-first until a city has several salons, the four-city
claim stays out of customer-facing copy, and **every number in a post is checked
against the database before it ships**.

### The WhatsApp VIP group: still a channel, for a different reason

This one did not rest on the threat model and does not lift with it.

In a normal WhatsApp **group**, every member's phone number is visible to every
other member. That is true in Kabul and it is true in Toronto: a VIP group of
customers hands each woman's number to every stranger in it, which is how people
get spam, sales calls and unwanted contact from someone who liked the look of a
name. Nobody has to be watching for that to go wrong.

The reason to keep using a **channel** is that it costs nothing to. Followers
are hidden from each other and from the admin, and a one-way broadcast is what a
VIP tier actually needs. If a genuine two-way space is wanted, a Community
**announcement group** gives it without exposing numbers.

If the owner wants an open group anyway, that is a decision he can make — but
make it deliberately, knowing it publishes every member's number, rather than
inheriting it from a rule that has now been relaxed elsewhere.

---

## Channels

| Channel | Audience | Privacy | What belongs there |
|---|---|---|---|
| Instagram / social | **Customers, salon owners, brand** | Public | Anything: customer copy, salon recruitment, product, brand. The main reach channel. |
| WhatsApp **channel** | Customers | Followers hidden from each other and from admin | New salons, new cities, offers, feature news. One-way. |
| WhatsApp **VIP group** | Customers | **Members see each other's numbers** | Use a channel or an announcement group instead — see above. |
| Community | Mixed | Depends on platform — check before posting | Announcements, salon spotlights (with permission). |
| In-salon | Customers | Private, face to face | Still the highest-converting path: a QR or link handed over by the salon owner. |
| `linumic.com` | Partners, press, investors | Public | Company narrative, English-first, the parent-brand story. |
| Play Store listing | Discovery | Public | `play-store/store_listing.md` — see "known stale copy" below. |

---

## Audiences and what actually persuades them

**Customers (women).** The product promise is *privacy, then convenience*. Lead
with what she does not have to do: no phone call, no waiting, no explaining
where she is going. Never lead with discounts alone — this audience is choosing
on trust. Dari first, Pashto equal, English not needed. Reached through
Instagram, the WhatsApp channel, and from inside the salon.

**Salon owners.** They want more bookings, fewer no-shows and no admin. Lead
with filled empty slots and the calendar that manages itself. This audience can
be marketed to openly — they are businesses. Dari and Pashto.

**Partners and investors (`linumic.com`).** Lead with the discipline, not the
footprint: three languages, server-authoritative money, identity verification, an
audit log. **Say "built for four cities, live in Kabul" — never "four cities".**
This is the audience that checks, and being caught rounding up costs more than
the small number would have. English.

---

## Making the images

Do not hand-build images. The generator renders from the app's own font and its
own launcher vector, so a post cannot drift from the product:

```bash
python3 marketing/generate.py marketing/content/<slug>.json
python3 marketing/generate.py --all
```

The canonical, standalone copy of this generator lives in `~/Documents/SafeBeauty
Marketing/` with its own `assets/`. Change one, change the other.

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

The four cities are `کابل · هرات · مزارشریف · جلال‌آباد` — but see the supply
note above before putting them in customer-facing copy.

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
