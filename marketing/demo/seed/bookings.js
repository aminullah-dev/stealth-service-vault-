"use strict";
/**
 * The demo customer's booking history, so "my bookings" is not an empty state.
 *
 * Written straight to Firestore rather than through createPaymentSession,
 * because that callable wants a real HesabPay session for an online payment and
 * a provider on the other end to confirm a cash one. What it produces is this
 * document shape; these are the same fields with plausible values.
 *
 * Times are snapped to :00 / :30 inside the salon's 09:00–18:00 Kabul week, so
 * they sit on the same grid computeSlots offers and nothing looks off-grid.
 */
const L = require("./lib.js");
const { SALONS } = require("./world.js");

const CUSTOMER = require("./account.json");   // written by account.js
const DAY = 24 * 60 * 60 * 1000;

/** A Kabul-local time on the day `daysFromNow`, snapped to the slot grid. */
function kabulSlot(daysFromNow, hour, minute) {
  const d = new Date(Date.now() + daysFromNow * DAY);
  // Kabul is UTC+4:30 and does not observe DST.
  const utcMs = Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate(), hour, minute) - (4 * 60 + 30) * 60 * 1000;
  return utcMs;
}

/** Friday (Kabul) is the salons' day off — nudge off it. */
function avoidFriday(ms) {
  const wd = new Intl.DateTimeFormat("en-US", { timeZone: "Asia/Kabul", weekday: "short" }).format(new Date(ms));
  return wd === "Fri" ? ms + DAY : ms;
}

const salon = (id) => SALONS.find((s) => s.id === id);

function booking({ id, salonId, services, daysFromNow, hour, minute, status, staffId, staffName, paymentMethod, code, reviewed }) {
  const s = salon(salonId);
  const at = avoidFriday(kabulSlot(daysFromNow, hour, minute));
  const total = services.reduce((sum, n) => sum + (s.pricePerService[n] || 0), 0);
  return {
    id,
    doc: {
      bookingCode: code,
      customerId: CUSTOMER.appUid,
      customerName: CUSTOMER.name,
      customerPhone: CUSTOMER.phoneStored,
      salonId: s.id,
      salonName: s.salonName,
      serviceName: services.join("، "),
      staffId: staffId || "",
      staffName: staffName || "",
      appointmentDate: at,
      status,
      createdAt: at - 3 * DAY,
      reviewed: !!reviewed,
      visitReported: false,
      notes: "",
      paymentMethod,
      customerRatingSum: 0,
      customerRatingCount: 0,
      noShowCount: 0,
      customerReported: false,
      totalAmount: total,
    },
  };
}

const BOOKINGS = [
  booking({ id: "demo-appt-upcoming-yasamin", salonId: "demo-salon-yasamin",
    services: ["میکاپ مجلسی"], daysFromNow: 2, hour: 15, minute: 0,
    status: "CONFIRMED", staffId: "st-ys-1", staffName: "یاسمین",
    paymentMethod: "ONLINE", code: "SB-7K2QME" }),

  booking({ id: "demo-appt-pending-golsorkh", salonId: "demo-salon-gol-e-sorkh",
    services: ["کوتاهی مو"], daysFromNow: 5, hour: 11, minute: 30,
    status: "PENDING", staffId: "st-gs-1", staffName: "زرغونه",
    paymentMethod: "CASH", code: "SB-3D9XHT" }),

  booking({ id: "demo-appt-past-nastaran", salonId: "demo-salon-nastaran",
    services: ["مانیکور", "پدیکور"], daysFromNow: -9, hour: 10, minute: 0,
    status: "COMPLETED", staffId: "st-ns-1", staffName: "نسترن",
    paymentMethod: "ONLINE", code: "SB-5W8PLC", reviewed: false }),

  booking({ id: "demo-appt-past-banafsha", salonId: "demo-salon-banafsha",
    services: ["بند انداختن ابرو"], daysFromNow: -26, hour: 16, minute: 30,
    status: "COMPLETED", paymentMethod: "CASH", code: "SB-2H6NRB", reviewed: true }),
];

async function main() {
  const fmt = (ms) => new Intl.DateTimeFormat("en-GB", {
    timeZone: "Asia/Kabul", weekday: "short", day: "2-digit", month: "short",
    hour: "2-digit", minute: "2-digit", hour12: false,
  }).format(new Date(ms));

  for (const b of BOOKINGS) {
    await L.setDoc("appointments", b.id, b.doc);
    console.log(b.id.padEnd(30), b.doc.status.padEnd(10), fmt(b.doc.appointmentDate),
      "|", b.doc.salonName, "|", b.doc.serviceName, "|", b.doc.totalAmount, "AFN");
  }
  console.log(`appointments written: ${BOOKINGS.length} for ${CUSTOMER.appUid}`);
}

main().catch((e) => { console.error(e); process.exit(1); });
