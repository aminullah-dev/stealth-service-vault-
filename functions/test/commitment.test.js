const test = require("node:test");
const assert = require("node:assert");
const { cashAllowed, DEFAULTS } = require("../lib/commitment");

test("a customer with no history may still pay cash", () => {
  // Deliberate. Making a stranger prepay taxes the customers the platform most
  // needs and who have done nothing wrong, in a market where paying at the
  // counter is simply how things are done.
  const r = cashAllowed({ noShowCount: 0 });
  assert.equal(r.allowed, true);
  assert.equal(r.reason, null);
});

test("one no-show is enough to withdraw the cash option", () => {
  const r = cashAllowed({ noShowCount: 1 });
  assert.equal(r.allowed, false);
  assert.equal(r.reason, "NO_SHOW_HISTORY");
  assert.equal(r.noShowCount, 1, "the app explains itself with the actual number");
});

test("a party always prepays, however good the record", () => {
  const r = cashAllowed({ noShowCount: 0, isParty: true });
  assert.equal(r.allowed, false);
  assert.equal(r.reason, "PARTY");
});

test("a party outranks a clean record but reports itself as the reason", () => {
  // Both rules would fire on a bad record; the party is the one worth saying,
  // because it is not the customer's fault and the message should not imply it.
  const r = cashAllowed({ noShowCount: 5, isParty: true });
  assert.equal(r.reason, "PARTY");
});

test("the no-show threshold is configurable", () => {
  const cfg = { cashBlockedAfterNoShows: 3 };
  assert.equal(cashAllowed({ noShowCount: 2, config: cfg }).allowed, true);
  assert.equal(cashAllowed({ noShowCount: 3, config: cfg }).allowed, false);
});

test("a threshold of zero switches the rule off entirely", () => {
  // An escape hatch that does not need a deploy: if this turns out to cost more
  // bookings than it saves chairs, it can be turned off from the console.
  const r = cashAllowed({ noShowCount: 99, config: { cashBlockedAfterNoShows: 0 } });
  assert.equal(r.allowed, true);
});

test("parties can be exempted too", () => {
  const r = cashAllowed({ isParty: true, config: { partyMustPrepay: false } });
  assert.equal(r.allowed, true);
});

test("nonsense config falls back to the defaults rather than opening the door", () => {
  for (const bad of [null, undefined, "yes", { cashBlockedAfterNoShows: "many" },
                     { cashBlockedAfterNoShows: -4 }]) {
    assert.equal(cashAllowed({ noShowCount: 1, config: bad }).allowed, false,
      `config ${JSON.stringify(bad)} must not disable the rule by accident`);
  }
});

test("a missing or malformed no-show count is treated as none", () => {
  for (const n of [undefined, null, NaN, "", "three", -2]) {
    assert.equal(cashAllowed({ noShowCount: n }).allowed, true,
      "an unreadable record is not evidence against a customer");
  }
});

test("the defaults are the strict ones", () => {
  assert.equal(DEFAULTS.cashBlockedAfterNoShows, 1);
  assert.equal(DEFAULTS.partyMustPrepay, true);
});
