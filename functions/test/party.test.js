const test = require("node:test");
const assert = require("node:assert");
const { normalizeParty, partyServices, partySpan, MAX_GUESTS } = require("../lib/party");

const OFFERED = ["Makeup", "Hair", "Nails"];
const DURATIONS = { Makeup: 60, Hair: 90, Nails: 30 };

test("a party's guests keep their own service lists", () => {
  const p = normalizeParty(
    [{ name: "عروس", services: ["Makeup", "Hair"] }, { name: "مادر عروس", services: ["Hair"] }],
    OFFERED
  );
  assert.equal(p.length, 2);
  assert.deepEqual(p[0].services, ["Makeup", "Hair"]);
  assert.equal(p[0].name, "عروس", "the salon needs to know who is who on the day");
});

test("services the salon does not offer are dropped", () => {
  // Straight off a phone. A guest cannot conjure a service into existence, or
  // book time for one the salon has no price for.
  const p = normalizeParty([{ name: "A", services: ["Hair", "Helicopter"] }], OFFERED);
  assert.deepEqual(p[0].services, ["Hair"]);
});

test("a guest with nothing to do is not a guest", () => {
  const p = normalizeParty(
    [{ name: "A", services: ["Hair"] }, { name: "B", services: [] },
     { name: "C", services: ["Nope"] }],
    OFFERED
  );
  assert.equal(p.length, 1, "an empty guest would occupy the salon for nothing");
});

test("the guest list is capped", () => {
  const many = Array.from({ length: MAX_GUESTS + 25 }, () => ({ name: "x", services: ["Hair"] }));
  assert.equal(normalizeParty(many, OFFERED).length, MAX_GUESTS);
});

test("a name cannot be a paragraph", () => {
  const p = normalizeParty([{ name: "ن".repeat(500), services: ["Hair"] }], OFFERED);
  assert.ok(p[0].name.length <= 60);
});

test("junk in the guest list is discarded, not trusted", () => {
  for (const bad of [null, undefined, "bride", 7, [], {}]) {
    const p = normalizeParty([bad, { name: "ok", services: ["Hair"] }], OFFERED);
    assert.equal(p.length, 1, `${JSON.stringify(bad)} must not become a guest`);
  }
});

test("partyServices flattens in order, for pricing", () => {
  const p = [{ name: "A", services: ["Makeup", "Hair"] }, { name: "B", services: ["Nails"] }];
  assert.deepEqual(partyServices(p), ["Makeup", "Hair", "Nails"]);
});

test("more stylists means the same work finishes sooner", () => {
  // Three guests, 180 minutes of work, 60-minute slots.
  const p = [
    { name: "A", services: ["Makeup"] },
    { name: "B", services: ["Makeup"] },
    { name: "C", services: ["Makeup"] },
  ];
  assert.equal(partySpan(p, DURATIONS, 60, 1), 3, "one stylist works through it");
  assert.equal(partySpan(p, DURATIONS, 60, 3), 1, "three stylists do it at once");
  assert.equal(partySpan(p, DURATIONS, 60, 2), 2, "two take an hour and a half, rounded up");
});

test("the span rounds up, because a bride waiting is the failure", () => {
  // 90 minutes over two stylists is 45, which is not a whole 60-minute slot.
  const p = [{ name: "A", services: ["Hair"] }];
  assert.equal(partySpan(p, DURATIONS, 60, 2), 1);
  // 210 minutes over two is 105 — one slot would leave the party mid-appointment.
  const big = [{ name: "A", services: ["Hair", "Makeup"] }, { name: "B", services: ["Hair"] }];
  assert.equal(partySpan(big, DURATIONS, 60, 2), 2);
});

test("a service with no duration still costs a slot", () => {
  const p = [{ name: "A", services: ["Unpriced"] }];
  assert.equal(partySpan(p, {}, 60, 1), 1);
});

test("no guests, or no stylists on record, still yields a real booking", () => {
  assert.equal(partySpan([], DURATIONS, 60, 3), 1);
  assert.equal(partySpan([{ name: "A", services: ["Makeup"] }], DURATIONS, 60, 0), 1,
    "a solo salon is one stylist, not zero");
});
