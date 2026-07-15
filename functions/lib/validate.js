"use strict";

// Document ids that arrive from client input or a webhook payload get
// interpolated into Firestore doc paths (`collection/${id}`). A value with a
// slash produces an odd-segment path and throws (an unhandled 500), and any
// untrusted string flowing into a path is a smell worth closing. Every real id
// in this app — Firestore auto-ids, UUIDs, uppercased promo codes — fits this
// charset, so validating is safe and rejects only malformed/hostile input.
const DOC_ID_RE = /^[A-Za-z0-9_-]{1,128}$/;

/** True when [id] is a well-formed, path-safe Firestore document id. */
function isValidDocId(id) {
  return typeof id === "string" && DOC_ID_RE.test(id);
}

module.exports = { isValidDocId };
