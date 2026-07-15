"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { averageRating } = require("../lib/reviews");

test("averageRating: averages valid ratings", () => {
  assert.equal(averageRating([{ rating: 5 }, { rating: 4 }, { rating: 3 }]), 4);
});

test("averageRating: ignores zero / negative / non-finite ratings", () => {
  assert.equal(averageRating([{ rating: 5 }, { rating: 0 }, { rating: -2 }, { rating: "x" }]), 5);
});

test("averageRating: empty / missing input is 0 (never NaN)", () => {
  assert.equal(averageRating([]), 0);
  assert.equal(averageRating(null), 0);
  assert.equal(averageRating([{}, { rating: null }]), 0);
});

test("averageRating: coerces numeric strings", () => {
  assert.equal(averageRating([{ rating: "4" }, { rating: "2" }]), 3);
});
