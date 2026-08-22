/**
 * The pieces every part of the backend needs, in one place.
 *
 * index.js had grown past 5,700 lines. Splitting it by domain only works if
 * there is somewhere for the things that are genuinely shared to live —
 * otherwise each domain re-initialises Firebase and ends up with its own
 * Firestore handle, which is not a smaller version of the same program.
 *
 * initializeApp() runs HERE, once, on first require. Node caches modules, so
 * every domain that requires this gets the same initialised app and the same
 * `db`. Doing it in each domain instead would throw on the second call.
 */

const { logger } = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();
const db = admin.firestore();

/**
 * Log something a human should be told about, with a stable machine label.
 *
 * Alert policies match on jsonPayload.alert rather than on message text, so
 * rewording a log line cannot silently disable the alert that depends on it.
 */
function alertable(kind, message, details) {
  logger.error(message, { alert: kind, ...(details || {}) });
}

module.exports = { admin, db, logger, alertable };
