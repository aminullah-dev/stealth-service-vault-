/**
 * How much a customer has to commit before a salon holds a chair for her.
 *
 * SafeBeauty has measured customer reliability since the two-way ratings went in
 * — noShowCount is incremented every time a salon reports that nobody arrived —
 * and has never once used it. A number that is collected and never consulted is
 * not a safeguard; it is a record of harm already done.
 *
 * What it should govern is the cash option. Paying at the salon is the pleasant
 * way to book and the one most customers here want, but it is also the one that
 * costs a salon a chair and an hour when the customer does not turn up: no money
 * has moved, so nothing was at stake. Prepaying online puts something at stake.
 *
 * So cash is treated as earned rather than given. Deliberately narrow:
 *
 *   · a customer who has not turned up before prepays until she does turn up
 *   · a party prepays regardless of record — see below
 *   · everybody else is unaffected
 *
 * A first booking is NOT restricted. Making a stranger prepay is the obvious next
 * rule and it is the wrong one: it taxes the customers the platform most needs
 * and who have done nothing, in a market where paying cash at the counter is
 * simply how things are done. That trade-off belongs to a person, not to this
 * file, and the thresholds are configurable so it can be revisited without a
 * deploy.
 *
 * Pure and dependency-free, because it decides whether a woman can book at all.
 */

/** Applied when platform_config has nothing to say. */
const DEFAULTS = {
  // One is enough. A no-show is not an accident the second time.
  cashBlockedAfterNoShows: 1,
  // A wedding party books out most of a small salon's day. A no-show there is
  // not a lost hour, it is a lost Saturday, and no rating history is worth that
  // risk to a business with four chairs.
  partyMustPrepay: true,
};

function settings(config) {
  const c = config && typeof config === "object" ? config : {};
  const n = Number(c.cashBlockedAfterNoShows);
  return {
    cashBlockedAfterNoShows: Number.isFinite(n) && n >= 0 ? n : DEFAULTS.cashBlockedAfterNoShows,
    partyMustPrepay: c.partyMustPrepay === false ? false : DEFAULTS.partyMustPrepay,
  };
}

/**
 * Whether this customer may pay at the salon for this booking.
 *
 * Returns a reason rather than a bare false, so the app can explain itself. A
 * payment option that vanishes without saying why reads as a bug, and a customer
 * who thinks the app is broken does not book at all.
 */
function cashAllowed({ noShowCount, isParty, config } = {}) {
  const s = settings(config);
  const misses = Math.max(0, Number(noShowCount) || 0);

  if (isParty && s.partyMustPrepay) {
    return { allowed: false, reason: "PARTY" };
  }
  // 0 means the rule is switched off entirely.
  if (s.cashBlockedAfterNoShows > 0 && misses >= s.cashBlockedAfterNoShows) {
    return { allowed: false, reason: "NO_SHOW_HISTORY", noShowCount: misses };
  }
  return { allowed: true, reason: null };
}

module.exports = { DEFAULTS, cashAllowed };
