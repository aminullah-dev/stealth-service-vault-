"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { RETENTION_MS, shouldPurge } = require("../lib/kycretention");

const NOW = 1_800_000_000_000;
const base = { kycStatus: "APPROVED", reviewedAt: NOW - RETENTION_MS - 1, now: NOW, hasImages: true };

test("a verification older than the window loses its photographs", () => {
  assert.deepEqual(shouldPurge(base), { purge: true, why: "approved-expired" });
});

test("a rejected account loses them too", () => {
  // She can submit again, which uploads again. Holding a rejected woman's
  // tazkira in the meantime protects nobody.
  assert.deepEqual(shouldPurge({ ...base, kycStatus: "REJECTED" }),
    { purge: true, why: "rejected-expired" });
});

test("an application still waiting on a human is never touched", () => {
  // Deleting these deletes her application: the admin would open the review
  // and find nothing to look at.
  assert.deepEqual(shouldPurge({ ...base, kycStatus: "PENDING" }),
    { purge: false, why: "pending" });
  // Even one submitted long ago. An old queue is a queue problem, not a
  // reason to throw away what somebody sent.
  assert.deepEqual(shouldPurge({ ...base, kycStatus: "PENDING", reviewedAt: 1 }),
    { purge: false, why: "pending" });
});

test("inside the window, nothing happens", () => {
  assert.deepEqual(shouldPurge({ ...base, reviewedAt: NOW - RETENTION_MS + 1 }),
    { purge: false, why: "within-window" });
  assert.deepEqual(shouldPurge({ ...base, reviewedAt: NOW }),
    { purge: false, why: "within-window" });
});

test("exactly at the boundary counts as expired", () => {
  assert.equal(shouldPurge({ ...base, reviewedAt: NOW - RETENTION_MS }).purge, true);
});

test("no timestamp means no deletion", () => {
  // The caller falls back to the storage object's creation time. If even that
  // is unavailable the account is left alone: a purge that cannot say how old
  // something is has no business deleting it.
  for (const bad of [undefined, null, 0, -1, NaN, "yesterday"]) {
    assert.deepEqual(shouldPurge({ ...base, reviewedAt: bad }),
      { purge: false, why: "no-timestamp" }, `reviewedAt=${String(bad)}`);
  }
});

test("an account with no images is not work to do", () => {
  assert.deepEqual(shouldPurge({ ...base, hasImages: false }),
    { purge: false, why: "no-images" });
});

test("an account that never submitted is left alone", () => {
  assert.deepEqual(shouldPurge({ ...base, kycStatus: "NONE" }),
    { purge: false, why: "not-reviewed" });
});
