"use strict";

// What a person can report, and what an admin can do about it.
//
// Apple's Guideline 1.2 asks three things of an app carrying other people's
// content: a way to report it, a way to block the person who posted it, and a
// commitment to act within 24 hours. This app carries salon photos, stories,
// customer comments and customer reviews, and had none of the three. The parts
// that are pure decisions rather than Firestore writes live here so they can be
// tested without Firebase.

// Every surface a customer can see somebody else's words or photograph on.
// Chat is deliberately absent: it is one-to-one and private, and a report there
// is a different flow (the salon and the customer already know each other).
const TARGETS = {
  POST:    { collection: "salon_posts",   authorField: "salonId",  authorKind: "SALON" },
  STORY:   { collection: "salon_stories", authorField: "salonId",  authorKind: "SALON" },
  COMMENT: { collection: "post_comments", authorField: "userId",   authorKind: "USER" },
  REVIEW:  { collection: "reviews",       authorField: "customerId", authorKind: "USER" },
};

const REASONS = ["SPAM", "HARASSMENT", "NUDITY", "HATE", "SCAM", "OTHER"];

const ACTIONS = ["DISMISS", "REMOVE", "REMOVE_AND_SUSPEND"];

/** The collection and author field for a target type, or null if unknown. */
function targetSpec(targetType) {
  return Object.prototype.hasOwnProperty.call(TARGETS, targetType)
    ? TARGETS[targetType]
    : null;
}

/**
 * One report per person per piece of content, enforced by the document id
 * rather than by a query — the same trick post_likes uses. A second tap writes
 * the same document instead of adding a row, so a queue cannot be flooded by
 * one angry person and the admin count means what it says.
 */
function reportId(targetType, targetId, reporterUid) {
  return `${targetType}_${targetId}_${reporterUid}`;
}

/** Blocks are symmetric-free and one-directional: id is blocker then blocked. */
function blockId(blockerUid, blockedUid) {
  return `${blockerUid}_${blockedUid}`;
}

function isReason(value) {
  return REASONS.includes(value);
}

function isAction(value) {
  return ACTIONS.includes(value);
}

/**
 * Whether a report may be filed at all.
 *
 * Reporting your own content is refused rather than silently accepted: it is
 * either a mistake or an attempt to make the queue noisy, and the person who
 * wants their own post gone can delete it.
 *
 * [ownerUid] is the PERSON, not the author field. For a comment or a review
 * those are the same string; for a salon's post or story the author field is a
 * salonId and the person is that salon's provider. Comparing the reporter to
 * the salonId let a provider report her own salon's photo, and — worse on the
 * other side — meant the admin had nobody to suspend for it.
 */
function canReport({ targetType, targetId, reason, reporterUid, ownerUid }) {
  if (!targetSpec(targetType)) return { ok: false, why: "unknown-target" };
  if (!targetId) return { ok: false, why: "missing-target" };
  if (!isReason(reason)) return { ok: false, why: "unknown-reason" };
  if (!reporterUid) return { ok: false, why: "no-reporter" };
  if (ownerUid && ownerUid === reporterUid) return { ok: false, why: "own-content" };
  return { ok: true };
}

/**
 * What resolving a report actually does.
 *
 * REMOVE deletes the content. It does not merely hide it: a hidden row that
 * every client has to remember to filter is one client away from being visible
 * again, and this content is small and replaceable. Suspension reuses the
 * shape resolveCustomerReport writes, so a suspension decided here is the same
 * suspension the Manage modal can lift.
 */
function planFor(action) {
  switch (action) {
    case "DISMISS":            return { deleteTarget: false, suspendAuthor: false };
    case "REMOVE":             return { deleteTarget: true,  suspendAuthor: false };
    case "REMOVE_AND_SUSPEND": return { deleteTarget: true,  suspendAuthor: true };
    default:                   return null;
  }
}

module.exports = {
  TARGETS, REASONS, ACTIONS,
  targetSpec, reportId, blockId, isReason, isAction, canReport, planFor,
};
