"use strict";
/**
 * Make each salon's rating and sortRating agree with its reviews.
 *
 * Why this is needed: seeding wrote `rating` from the world file, and then the
 * deployed awardReviewPoints trigger (onDocumentCreated reviews/{id}) recomputed
 * `rating` from the reviews that were seeded a moment later — leaving the card's
 * displayed rating (salon.rating) and the list's sort key (salon.sortRating,
 * written by deriveSalonFields) disagreeing. Computing both from the reviews,
 * with the server's own averageRating, is the only version that stays true.
 */
const L = require("./lib.js");
const { averageRating } = require(`${L.REPO}/functions/lib/reviews`);

const val = (f) => f && (f.stringValue ?? f.doubleValue ?? (f.integerValue !== undefined ? Number(f.integerValue) : undefined) ?? f.booleanValue);

async function main() {
  const reviews = await L.listDocs("reviews", 300);
  const bySalon = new Map();
  for (const d of reviews) {
    const salonId = val(d.fields.salonId);
    const rating = Number(val(d.fields.rating));
    if (!bySalon.has(salonId)) bySalon.set(salonId, []);
    bySalon.get(salonId).push({ rating });
  }

  const salons = await L.listDocs("salons");
  for (const d of salons) {
    const id = d.name.split("/").pop();
    const list = bySalon.get(id) || [];
    const avg = averageRating(list);
    const before = { rating: Number(val(d.fields.rating)), sortRating: Number(val(d.fields.sortRating)) };

    // PATCH with an updateMask so nothing else on the document is touched.
    const url = `${L.BASE}/salons/${id}?updateMask.fieldPaths=rating&updateMask.fieldPaths=sortRating`;
    await L.api("PATCH", url, {
      fields: { rating: { doubleValue: avg }, sortRating: { doubleValue: avg } },
    });

    console.log(
      String(val(d.fields.salonName)).padEnd(16),
      `reviews=${list.length}`.padEnd(11),
      `was rating=${before.rating} sort=${before.sortRating}`.padEnd(42),
      `now both=${avg.toFixed(3)}`
    );
  }
}

main().catch((e) => { console.error(e); process.exit(1); });
