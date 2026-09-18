"use strict";
/**
 * Remove the stub documents the awardReviewPoints trigger creates for reviewer
 * ids that have no account.
 *
 * The trigger does `set(users/{review.customerId}, {loyaltyPoints: increment},
 * {merge:true})`, and a merge-set on a missing document CREATES it — so each
 * seeded review minted a nameless users/ row plus a "thanks for your review"
 * notification addressed to nobody. Harmless (no credential, so nothing can
 * sign in as one) but it fills the demo user list with blank rows.
 *
 * Only touches ids starting with demo-reviewer-, and only rows whose whole
 * content is what the trigger writes.
 */
const L = require("./lib.js");

async function main() {
  const users = await L.listDocs("users", 300);
  let removedUsers = 0;
  for (const d of users) {
    const id = d.name.split("/").pop();
    if (!id.startsWith("demo-reviewer-")) continue;
    const keys = Object.keys(d.fields || {});
    if (keys.some((k) => !["loyaltyPoints", "nameKey", "phoneDigits"].includes(k))) {
      console.log("skipped (has real fields):", id, keys.join(","));
      continue;
    }
    await L.deleteDoc(`users/${id}`);
    removedUsers += 1;
  }

  const notes = await L.listDocs("notifications", 300);
  let removedNotes = 0;
  for (const d of notes) {
    const to = (d.fields.recipientId || {}).stringValue || "";
    if (!to.startsWith("demo-reviewer-")) continue;
    await L.deleteDoc(`notifications/${d.name.split("/").pop()}`);
    removedNotes += 1;
  }

  console.log(`removed ${removedUsers} stub users, ${removedNotes} orphan notifications`);
}

main().catch((e) => { console.error(e); process.exit(1); });
