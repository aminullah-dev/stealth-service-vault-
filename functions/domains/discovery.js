// discovery — moved out of index.js, which had grown past 5,700 lines.
//
// Every export here is registered by index.js re-exporting this module,
// so the deployed function set is unchanged by the move.

const { normalizeDistrict, cityOf, AREAS } = require("../lib/areas");
// `normalize` is imported under a clearer local name — lib/categories has no
// export called categoryNormalize, and dropping the rename made it undefined.
const { categoriesFor, normalize: categoryNormalize } = require("../lib/categories");
const { assertAdmin, idPage, logAdminAction, pageCursor, pageEnd } = require("../shared");
const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const { onCall } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { db, logger } = require("../shared");

// ── measureSalonReliability ───────────────────────────────────────────────────
//
// How dependably a salon answers the bookings it receives, counted from what is
// already recorded.
//
// This is measurement, not ranking. It stores counts and a rate; it does not
// order anyone or decide who gets shown first. That distinction is the whole
// reason it can be built now while D-6 keeps ranking closed: counting facts a
// salon produced is defensible at any scale, whereas weighting those facts into
// a position — which allocates real income between real businesses — needs data
// this platform does not yet have.
//
// It is also the input P8 will need whenever its gate opens, and it needs weeks
// of history to mean anything, so starting the clock now is the point.
//
// Deliberately excludes the customer's own behaviour. A salon is not less
// reliable because a customer cancelled, and folding that in would let a run of
// unlucky customers damage a salon's standing.
const RELIABILITY_WINDOW_DAYS = 90;

exports.measureSalonReliability = onSchedule(
  { schedule: "every day 02:30", timeZone: "Asia/Kabul", region: "us-central1" },
  async () => {
    const since = Date.now() - RELIABILITY_WINDOW_DAYS * 24 * 60 * 60 * 1000;
    const salons = await db.collection("salons").limit(500).get();

    for (const salonDoc of salons.docs) {
      const appts = await db.collection("appointments")
        .where("salonId", "==", salonDoc.id)
        .where("createdAt", ">", since)
        .limit(1000)
        .get();

      // Which cancellations this salon actually made. The trail denormalizes
      // salonId, so this is one query rather than a read per booking.
      const declined = new Set();
      const events = await db.collection("appointment_events")
        .where("salonId", "==", salonDoc.id)
        .where("to", "==", "CANCELLED")
        .limit(1000)
        .get();
      events.docs.forEach((e) => {
        const ev = e.data() || {};
        if (ev.actorRole === "PROVIDER" && ev.appointmentId) declined.add(ev.appointmentId);
      });

      let answered = 0;      // the salon acted: confirmed, or declined
      let confirmed = 0;
      let unanswered = 0;    // the visit time passed with the salon silent

      appts.docs.forEach((d) => {
        const a = d.data() || {};
        const status = a.status || "";
        if (status === "CONFIRMED" || status === "COMPLETED") {
          answered += 1; confirmed += 1;
        } else if (status === "CANCELLED") {
          // A cancellation counts against the salon only when the salon made
          // it. A customer changing her mind, or a checkout expiring, says
          // nothing about how dependably this salon answers — and counting it
          // would let a run of ordinary customer cancellations damage a salon's
          // standing for something it did not do. Anything the trail cannot
          // attribute is left out of both halves of the ratio rather than
          // guessed at.
          if (declined.has(d.id)) answered += 1;
        } else if (status === "PENDING" && Number(a.appointmentDate || 0) < Date.now()) {
          unanswered += 1;
        }
      });

      const decided = answered + unanswered;
      const reliability = {
        window: RELIABILITY_WINDOW_DAYS,
        bookings: appts.size,
        confirmed,
        unanswered,
        // Null rather than a flattering 1.0 when there is nothing to judge. A
        // brand new salon has not earned a perfect record, and showing one would
        // be a claim the data does not support.
        confirmRate: decided > 0 ? Math.round((confirmed / decided) * 100) : null,
        measuredAt: Date.now(),
      };

      // Compare every value that is stored, not a subset of them. A guard that
      // checks three of four fields silently pins the fourth: when the counts
      // held steady but the rate changed, the new rate was computed and thrown
      // away, and the salon kept a stale figure with no sign anything was wrong.
      const prev = (salonDoc.data() || {}).reliability || {};
      const changed = ["bookings", "confirmed", "unanswered", "confirmRate", "window"]
        .some((k) => prev[k] !== reliability[k]);
      if (changed) {
        await salonDoc.ref.update({ reliability });
      }
    }

    logger.log(`measureSalonReliability: measured ${salons.size} salon(s)`);
  }
);

// ── adminDemandReport ─────────────────────────────────────────────────────────
//
// Which district wants which service, and whether there is anyone there to
// serve it. This is the question the whole demand-capture exists to answer:
// with two live salons and five hundred as the target, the binding constraint
// is supply, and until now which salon to recruit next was a guess.
//
// Demand and supply are counted from different collections and joined here, so
// the console does not have to know how either is stored. Signals carry no
// identity, so everything below is genuinely aggregate — there is no per-person
// view to accidentally build on top of it.
exports.adminDemandReport = onCall({ region: "us-central1" }, async (request) => {
  await assertAdmin(request);
  const days = Math.min(90, Math.max(1, Number((request.data || {}).days || 30)));
  const since = Date.now() - days * 24 * 60 * 60 * 1000;

  const signals = await db.collection("demand_signals")
    .where("at", ">", since)
    .orderBy("at", "desc")
    .limit(2000)
    .get();

  // demand[districtKey][category] = { total, byKind }
  const demand = new Map();
  const bump = (district, category, kind) => {
    const dk = district || "(unspecified)";
    const ck = category || "(any)";
    if (!demand.has(dk)) demand.set(dk, new Map());
    const row = demand.get(dk);
    if (!row.has(ck)) row.set(ck, { total: 0, byKind: {} });
    const cell = row.get(ck);
    cell.total += 1;
    cell.byKind[kind] = (cell.byKind[kind] || 0) + 1;
  };

  signals.docs.forEach((d) => {
    const s = d.data() || {};
    bump(s.districtKey, s.category, s.kind || "UNKNOWN");
  });

  // Supply, from the derived fields the discovery queries already use, so the
  // two halves of this report agree with what a customer actually sees.
  const salons = await db.collection("salons").limit(500).get();
  const supply = new Map();   // districtKey -> { total, byCategory }
  salons.docs.forEach((d) => {
    const s = d.data() || {};
    if (s.isAvailable !== true) return;
    const dk = s.districtKey || "(unspecified)";
    if (!supply.has(dk)) supply.set(dk, { total: 0, byCategory: {} });
    const row = supply.get(dk);
    row.total += 1;
    (Array.isArray(s.categories) ? s.categories : []).forEach((c) => {
      row.byCategory[c] = (row.byCategory[c] || 0) + 1;
    });
  });

  const rows = [];
  for (const [districtKey, categories] of demand) {
    for (const [category, cell] of categories) {
      const sup = supply.get(districtKey);
      const salonsHere = category === "(any)"
        ? (sup ? sup.total : 0)
        : (sup ? (sup.byCategory[category] || 0) : 0);
      rows.push({
        districtKey,
        category,
        demand: cell.total,
        byKind: cell.byKind,
        salons: salonsHere,
        // The number that ranks recruitment: demand with nobody to serve it is
        // worth more attention than demand a salon is already meeting.
        unmet: salonsHere === 0 ? cell.total : 0,
      });
    }
  }

  rows.sort((a, b) => (b.unmet - a.unmet) || (b.demand - a.demand));

  return {
    ok: true,
    days,
    signalCount: signals.size,
    truncated: signals.size >= 2000,
    supplyByDistrict: [...supply].map(([districtKey, v]) => ({ districtKey, ...v })),
    rows,
  };
});

// A salon with no priced service still has to appear in a price-sorted list,
// and it belongs at the end rather than nowhere. Firestore EXCLUDES documents
// that lack the orderBy field entirely, so "no price" must be a number, not an
// absent field — otherwise sorting by price silently hides salons, which is the
// same invisible-empty failure this whole phase exists to fix.
const NO_PRICE = 9999999;

/** Lowest priced service, or NO_PRICE when the salon has priced nothing. */

function salonMinPrice(salon) {
  const prices = (salon && salon.pricePerService) || {};
  const values = Object.values(prices)
    .map((v) => Number(v))
    .filter((v) => Number.isFinite(v) && v > 0);
  return values.length ? Math.min(...values) : NO_PRICE;
}

/** What the derived fields should be for a salon, given what it stores. */

/** Area lookup by key, built once — deriveSalonDiscovery runs on every write. */
const AREA_BY_KEY = new Map(AREAS.map((a) => [a.key, a]));

function deriveSalonDiscovery(salon) {
  const { categories, unmatched } = categoriesFor(salon && salon.services);
  const area = normalizeDistrict(salon && salon.district);
  const districtKey = area.key || "";

  // The finer گذر/محله, kept only when it is a real area that actually sits in
  // the district the salon claims. A salon could otherwise store a guzar from
  // another district — or another city — and be displayed at an address it is
  // not at. Where it does not check out the field is emptied rather than
  // corrected: an unverifiable address should show as absent, not as a guess.
  const claimed = String((salon && salon.areaKey) || "").trim();
  const finer = claimed && AREA_BY_KEY.get(claimed);
  const areaKey = (finer && finer.kind !== "DISTRICT" && finer.parent === districtKey)
    ? claimed
    : "";

  return {
    categories,
    districtKey,
    areaKey,
    // What the salon actually typed, carried so the review flag can tell "left
    // it blank" apart from "typed something that matched nothing". Not stored.
    districtRaw: String((salon && salon.district) || "").trim(),
    // Prefix-searchable form of the name. Firestore cannot match a substring,
    // but a range on a normalized name gives prefix search, which is what a
    // customer typing the start of a salon name actually needs.
    nameKey: categoryNormalize(salon && salon.salonName),
    minPrice: salonMinPrice(salon),
    // Always written, never inherited from the document: a salon that has never
    // been rated must still be orderable by rating.
    sortRating: Number((salon && salon.rating) || 0),
    // Not stored — returned so the caller can report what a human needs to look at.
    unmatchedServices: unmatched,
    districtCandidates: area.candidates || [],
  };
}

/** The subset of derived values that actually get stored on the document. */

function storedDiscoveryFields(derived) {
  const review = {
    unmatchedServices:  derived.unmatchedServices,
    districtCandidates: derived.districtCandidates,
  };
  return {
    categories:  derived.categories,
    districtKey: derived.districtKey,
    areaKey:     derived.areaKey,
    // Derived from the district key's prefix rather than stored separately by
    // the salon, so the two can never disagree about which city a salon is in.
    // Empty when the district is unresolved — and empty is the honest value:
    // orderBy would drop such a salon anyway, and a guessed city would put it
    // in a list of salons a customer could not actually reach.
    city:        cityOf(derived.districtKey),
    nameKey:     derived.nameKey,
    minPrice:    derived.minPrice,
    sortRating:  derived.sortRating,
    // Stored, not only logged. The derivation refuses to guess when it cannot
    // decide confidently, which is right — but it was reporting that refusal to
    // a log line, and a log line is not a queue. A salon whose only earning
    // service matches no category does not appear under any category chip, and
    // nobody was ever going to find that out.
    //
    // A boolean beside the detail because the nightly sweep queries it, and it
    // lives in another domain: a flag it can filter on is the whole reason this
    // is a field rather than a cross-domain import.
    // The third case, which fell through both of the others: a salon typed an
    // address that resolves to no key AND to no candidates. Nothing is
    // ambiguous, so districtCandidates is empty and the flag stayed false —
    // while districtKey "" derives city "", and an equality filter never
    // matches a document whose field is empty. Such a salon was invisible in
    // every filtered query, and absent from the queue meant to catch exactly
    // that. Blank is not flagged: a salon that has not filled the field in is
    // not an error to review, and flagging it fills the queue with rows nobody
    // can act on.
    needsDiscoveryReview: review.unmatchedServices.length > 0 ||
                          review.districtCandidates.length > 0 ||
                          (derived.districtRaw.length > 0 && derived.districtKey === ""),
    discoveryReview:      review,
  };
}

/** True when the stored derived fields already equal the freshly derived ones. */

function discoveryUpToDate(salon, derived) {
  const stored = Array.isArray(salon.categories) ? salon.categories : [];
  const sameList = (a, b) => {
    const x = Array.isArray(a) ? a : [];
    const y = Array.isArray(b) ? b : [];
    return x.length === y.length && x.every((v, i) => v === y[i]);
  };
  const review = salon.discoveryReview || {};
  return stored.length === derived.categories.length
      && stored.every((c, i) => c === derived.categories[i])
      && (salon.districtKey || "") === derived.districtKey
      // Compared even though it is derived from districtKey, which is compared
      // one line above. Today that makes it redundant; the moment the districts
      // are migrated it stops being, because city would then be the only field
      // that could be missing — and a field this function does not look at is a
      // field the backfill decides it does not need to write.
      && (salon.city || "") === derived.city
      && (salon.areaKey || "") === derived.areaKey
      && (salon.nameKey || "") === derived.nameKey
      && Number(salon.minPrice) === derived.minPrice
      && Number(salon.sortRating) === derived.sortRating
      // Also compared, or a salon whose services stop matching a category would
      // keep its old derived fields, look up to date, and never be written —
      // so the flag that says a person should look would never be raised.
      && sameList(review.unmatchedServices, derived.unmatchedServices)
      && sameList(review.districtCandidates, derived.districtCandidates);
}

// Keeps the derived fields correct as salons edit themselves, so the backfill is
// a one-time repair rather than a permanent chore.
//
// This writes back to the document it is triggered by, which re-triggers it once.
// The up-to-date check is what stops that being a loop: the second invocation
// finds nothing to change and returns.
exports.deriveSalonFields = onDocumentWritten(
  { document: "salons/{salonId}", region: "us-central1" },
  async (event) => {
    const after = event.data && event.data.after;
    if (!after || !after.exists) return;

    const salon = after.data() || {};
    const derived = deriveSalonDiscovery(salon);
    if (discoveryUpToDate(salon, derived)) return;

    await after.ref.update(storedDiscoveryFields(derived));

    if (derived.unmatchedServices.length || derived.districtCandidates.length) {
      logger.warn("deriveSalonFields: needs human review", {
        salonId: event.params.salonId,
        unmatchedServices: derived.unmatchedServices,
        districtCandidates: derived.districtCandidates,
      });
    }
  }
);

// Runs the same derivation on a schedule, so existing salons are repaired
// without anyone remembering to press a button.
//
// The customer's discovery queries order by sortRating or minPrice, and
// Firestore returns NO documents that lack the ordering field — not documents
// sorted last, none at all. That makes the backfill a hard dependency of the
// read path rather than a nice-to-have, and a hard dependency that waits on a
// human is the deploy step invariant I-12 exists to forbid.
//
// Idempotent, and cheap when there is nothing to do: it writes only where the
// derived values differ from what is stored.
exports.normalizeSalonsDaily = onSchedule(
  { schedule: "every day 01:00", timeZone: "Asia/Kabul", region: "us-central1" },
  async () => {
    const snap = await db.collection("salons").limit(500).get();
    let updated = 0;
    const review = [];

    for (const d of snap.docs) {
      const salon = d.data() || {};
      const derived = deriveSalonDiscovery(salon);
      if (!discoveryUpToDate(salon, derived)) {
        await d.ref.update(storedDiscoveryFields(derived));
        updated += 1;
      }
      if (derived.unmatchedServices.length || derived.districtCandidates.length) {
        review.push({
          salonId: d.id,
          unmatchedServices: derived.unmatchedServices,
          districtCandidates: derived.districtCandidates,
        });
      }
    }

    logger.log(`normalizeSalonsDaily: scanned ${snap.size}, updated ${updated}, ${review.length} need review`);
    if (review.length) logger.warn("normalizeSalonsDaily: needs human review", { review: review.slice(0, 20) });
  }
);

// ── adminNormalizeSalons ──────────────────────────────────────────────────────
//
// Backfills the derived fields for salons that predate them, and reports what it
// could not resolve.
//
// The report is the valuable half. A service it cannot categorise means the
// vocabulary needs a synonym; a district with two candidates means a human must
// choose. Neither is guessed: filing a salon under a category it does not serve,
// or in a neighbourhood it is not in, is worse than leaving it unfiltered,
// because the customer only finds out by turning up.
exports.adminNormalizeSalons = onCall({ region: "us-central1" }, async (request) => {
  const me = await assertAdmin(request);
  const limit = Math.min(500, Math.max(1, Number((request.data || {}).limit || 300)));
  const after = pageCursor(request.data);

  // Was orderBy("createdAt").limit(limit) with no cursor — the same first 300
  // salons on every press, and none of the salons registered before createdAt
  // existed. Those are the ones whose filter chips match nothing. See idPage.
  const snap = await idPage("salons", limit, after);

  let updated = 0;
  const needsReview = [];

  for (const d of snap.docs) {
    const salon = d.data() || {};
    const derived = deriveSalonDiscovery(salon);

    if (!discoveryUpToDate(salon, derived)) {
      await d.ref.update(storedDiscoveryFields(derived));
      updated += 1;
    }

    if (derived.unmatchedServices.length || derived.districtCandidates.length) {
      needsReview.push({
        salonId:   d.id,
        salonName: salon.salonName || "",
        district:  salon.district || "",
        unmatchedServices:  derived.unmatchedServices,
        districtCandidates: derived.districtCandidates,
      });
    }
  }

  await logAdminAction(me, "NORMALIZE_SALONS", {
    scanned: snap.size, updated, needsReview: needsReview.length,
  });

  return { ok: true, scanned: snap.size, updated, needsReview, ...pageEnd(snap, limit, after) };
});


// Exported for functions/test/discovery.test.js. These two are pure — the
// derivation a salon's queryable fields come from — and a test that cannot
// import what it tests reports itself as skipped, which reads like a pass.
exports.deriveSalonDiscovery = deriveSalonDiscovery;
exports.storedDiscoveryFields = storedDiscoveryFields;
