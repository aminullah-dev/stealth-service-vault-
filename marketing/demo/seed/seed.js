"use strict";
/**
 * Seed the demo world into safebeauty-staging.
 *
 * Idempotent: every document has a deterministic id, so re-running updates in
 * place rather than duplicating. Derived discovery fields are written here
 * rather than left to the deriveSalonFields trigger — verify.js then checks the
 * trigger agreed with them.
 */
const L = require("./lib.js");
const { SALONS, REVIEWS, OFFERS } = require("./world.js");

const NOW = Date.now();
const DAY = 24 * 60 * 60 * 1000;

async function main() {
  const counts = {};
  const bump = (k, n = 1) => { counts[k] = (counts[k] || 0) + n; };

  // ── platform_config ────────────────────────────────────────────────────────
  await L.setDoc("platform_config", "general", {
    commissionPercent: L.asDouble(10),
    discountCapPercent: 50,
  });
  bump("platform_config");

  for (const s of SALONS) {
    // ── the salon owner's user document ─────────────────────────────────────
    // A real account shape, but with no credential: pinHash/salt are empty, so
    // authenticateWithPassword returns INVALID for these. Nobody can sign in as
    // a demo salon owner; they exist only so the salon has an owner to point at.
    await L.setDoc("users", s.providerId, {
      uid: s.providerId,
      name: s.providerName,
      phone: s.providerPhone,
      email: "",
      role: "PROVIDER",
      pinHash: "",
      salt: "",
      firebaseEmail: "",
      status: "APPROVED",
      kycStatus: "APPROVED",
      createdAt: NOW - 200 * DAY,
      loyaltyPoints: 0,
      referralCode: "",
      referredBy: "",
      hesabAccountNumber: s.providerPhone,
      pendingSalonName: "",
      pendingSalonDistrict: "",
      pendingSalonServices: [],
    });
    bump("users(provider)");

    // ── the salon ───────────────────────────────────────────────────────────
    const base = {
      providerId: s.providerId,
      providerName: s.providerName,
      salonName: s.salonName,
      district: s.district,
      areaKey: s.areaKey,
      services: s.services,
      staff: s.staff,
      isAvailable: true,
      rating: L.asDouble(s.rating),
      workingHours: (() => {
        const week = L.defaultWorkingHours();
        if (!s.opensFriday) return week;
        // Friday, 10:00–16:00: a shorter day, which is how a salon that works
        // the wedding day actually runs it.
        return week.map((h) => h.dayOfWeek === 6
          ? { ...h, isOpen: true, openHour: 10, openMinute: 0, closeHour: 16, closeMinute: 0 }
          : h);
      })(),
      slotDurationMinutes: s.slotDurationMinutes,
      pricePerService: s.pricePerService,
      durationPerService: s.durationPerService,
      serviceTiming: s.serviceTiming,
      blockedDates: [],
      lastMinuteEnabled: s.lastMinuteEnabled,
      lastMinutePercent: s.lastMinutePercent,
      lastMinuteWindowHours: s.lastMinuteWindowHours,
      packages: s.packages,
      coverImageUrl: "",
      confirmedCount: s.confirmedCount,
      latitude: L.asDouble(s.latitude),
      longitude: L.asDouble(s.longitude),
      isVerified: s.isVerified,
      createdAt: NOW - 180 * DAY,
    };
    const derived = L.deriveSalonDiscovery({ ...s, rating: s.rating });
    await L.setDoc("salons", s.id, { ...base, ...L.storedDiscoveryFields(derived) });
    bump("salons");

    // ── provider_balances ───────────────────────────────────────────────────
    await L.setDoc("provider_balances", s.providerId, {
      providerId: s.providerId,
      owedAmount: Math.round(s.confirmedCount * 180),
      updatedAt: NOW - 2 * DAY,
    });
    bump("provider_balances");
  }

  // ── reviews ─────────────────────────────────────────────────────────────────
  // Customer ids are per-review pseudonymous demo ids; there is no user document
  // behind them, which is exactly what a review written by someone who has since
  // deleted their account looks like.
  let ri = 0;
  for (const r of REVIEWS) {
    ri += 1;
    const created = NOW - r.daysAgo * DAY;
    await L.setDoc("reviews", `demo-review-${String(ri).padStart(2, "0")}`, {
      salonId: r.salon,
      customerId: `demo-reviewer-${String(ri).padStart(2, "0")}`,
      customerName: r.customerName,
      rating: r.rating,
      comment: r.comment,
      createdAt: created,
      providerReply: r.reply,
      repliedAt: r.reply ? created + 6 * 60 * 60 * 1000 : 0,
      imageUrls: [],
    });
    bump("reviews");
  }

  // ── salon_offers ────────────────────────────────────────────────────────────
  for (const o of OFFERS) {
    const salon = SALONS.find((s) => s.id === o.salon);
    await L.setDoc("salon_offers", o.id, {
      salonId: salon.id,
      providerId: salon.providerId,
      salonName: salon.salonName,
      title: o.title,
      description: o.description,
      service: o.service,
      discountPercent: o.discountPercent,
      discountAmount: o.discountAmount,
      expiresAt: NOW + o.expiresInDays * DAY,
      active: true,
      createdAt: NOW - 2 * DAY,
    });
    bump("salon_offers");
  }

  // ── salon_stats ─────────────────────────────────────────────────────────────
  // Normally maintained by the deriveSalonStats trigger, which only counts
  // appointments it sees written. Seeded so the provider-facing totals are not
  // blank, and consistent with each salon's confirmedCount.
  for (const s of SALONS) {
    const confirmed = s.confirmedCount;
    const cancelled = Math.max(1, Math.round(confirmed * 0.08));
    const byService = {};
    const confirmedByService = {};
    s.services.forEach((name, i) => {
      const n = Math.max(1, Math.round(confirmed / (i + 2)));
      byService[name] = n;
      confirmedByService[name] = n;
    });
    await L.setDoc("salon_stats", s.id, {
      salonId: s.id,
      total: confirmed + cancelled,
      byStatus: { COMPLETED: confirmed, CANCELLED: cancelled },
      byService,
      confirmedByService,
      updatedAt: NOW - DAY,
    });
    bump("salon_stats");
  }

  console.log("seeded:", JSON.stringify(counts, null, 1));
}

main().catch((e) => { console.error(e); process.exit(1); });
