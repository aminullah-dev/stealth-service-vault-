/**
 * Wedding parties, as something other than one very long booking.
 *
 * A bride books for herself and four or ten others. Until now the app flattened
 * that into a single appointment whose `services` was every guest's services
 * concatenated, with the guest list pasted into the notes as text. Two things
 * follow from that, and both are wrong.
 *
 * The salon receives "Cut, Cut, Colour, Makeup, Makeup" and a paragraph. It
 * cannot see who is having what, cannot mark one guest done, and cannot plan.
 *
 * And the whole party lands on ONE stylist, back to back, because an appointment
 * carries one staffId. Five guests at an hour each becomes a five-hour block for
 * one person, when the salon has three stylists who would have done it in two.
 * A wedding party is inherently parallel work; modelling it as a queue is what
 * makes the most valuable booking in this market also the most awkward one.
 *
 * So a party is modelled as what it actually is: a block of the salon's day.
 * Everyone works, the wall-clock is the total work divided by the number of
 * stylists, and the booking occupies the salon rather than a chair. That is
 * deliberately not a matching engine — nobody is assigned to anybody. The salon
 * decides who does what on the day, which is what it already does.
 *
 * Pure and dependency-free: it decides how much of a business's Saturday is
 * spoken for.
 */

/** A party larger than this is a venue booking, not a salon appointment. */
const MAX_GUESTS = 40;
/** Long enough for a name, short enough not to be a paragraph. */
const MAX_NAME = 60;
/** No one guest has more services than a salon offers. */
const MAX_SERVICES_PER_GUEST = 12;

/**
 * Clean a client-supplied guest list into something safe to store.
 *
 * Comes straight from a phone, so nothing here is trusted: names are trimmed and
 * capped, services are filtered against what the salon actually offers, and
 * guests left with nothing to do are dropped rather than silently occupying time.
 */
function normalizeParty(raw, offeredServices) {
  const offered = new Set(
    (Array.isArray(offeredServices) ? offeredServices : []).map((s) => String(s))
  );
  const list = Array.isArray(raw) ? raw.slice(0, MAX_GUESTS) : [];

  const out = [];
  for (const g of list) {
    if (!g || typeof g !== "object") continue;
    const services = (Array.isArray(g.services) ? g.services : [])
      .map((s) => String(s || ""))
      .filter((s) => offered.size === 0 || offered.has(s))
      .slice(0, MAX_SERVICES_PER_GUEST);
    if (services.length === 0) continue;      // a guest having nothing done is not a guest
    out.push({
      name: String(g.name || "").trim().slice(0, MAX_NAME),
      services,
    });
  }
  return out;
}

/** Every service the party needs, in order, for pricing and for the salon's list. */
function partyServices(guests) {
  return (Array.isArray(guests) ? guests : []).flatMap((g) =>
    Array.isArray(g.services) ? g.services : []
  );
}

/**
 * How long the party takes, in slots, with [staffCount] stylists working.
 *
 * The total work is fixed; the wall-clock shrinks with the number of people
 * doing it. Rounded UP at both steps: a party that overruns its window is a
 * bride waiting in a salon that has taken its next booking.
 */
function partySpan(guests, durationPerService, slotMinutes, staffCount) {
  const step = Math.max(1, Number(slotMinutes) || 30);
  const durs = durationPerService && typeof durationPerService === "object" ? durationPerService : {};
  const staff = Math.max(1, Number(staffCount) || 1);

  const names = partyServices(guests);
  if (names.length === 0) return 1;

  let totalMinutes = 0;
  for (const n of names) {
    const d = Number(durs[n]);
    totalMinutes += Number.isFinite(d) && d > 0 ? d : step;
  }
  const wallClock = Math.ceil(totalMinutes / staff);
  return Math.max(1, Math.ceil(wallClock / step));
}

module.exports = {
  MAX_GUESTS,
  MAX_NAME,
  MAX_SERVICES_PER_GUEST,
  normalizeParty,
  partyServices,
  partySpan,
};
