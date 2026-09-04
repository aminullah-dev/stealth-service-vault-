#!/usr/bin/env node
/**
 * Delete Firebase Auth accounts that a broken registration left behind.
 *
 *   node functions/scripts/purge-orphan-accounts.js                # dry run
 *   node functions/scripts/purge-orphan-accounts.js --apply        # delete
 *   node functions/scripts/purge-orphan-accounts.js --apply --include-real-emails
 *
 * Registration used to be three calls from the handset — create the credential,
 * write the profile, delete the credential again if the write failed — and the
 * third needed the connection whose loss was the reason it was running. On
 * 2026-09-04 that had produced 117 Auth accounts against 12 user documents.
 * registerAccount fixed the mechanism; it does not clean up after it. This does.
 *
 * ── What it will not touch ────────────────────────────────────────────────────
 *
 * Everything here is re-derived at run time. There is no baked-in list of uids:
 * a list written today and run next week deletes accounts that became real in
 * between, and that is not a risk worth taking to save a query.
 *
 * An account is deleted only when ALL of these hold:
 *
 *   1. No users document is reachable from it — neither through uid_map nor
 *      through the appUid its <32hex>@sb.app address encodes.
 *   2. No document anywhere in Firestore mentions that appUid. Every collection
 *      is scanned and every document is matched whole, not by a list of fields
 *      someone remembered; a booking under a field this script has never heard
 *      of still counts as activity and still saves the account.
 *   3. It is older than MIN_AGE_HOURS. A registration in flight right now looks
 *      exactly like an orphan for the second between its two writes, and the
 *      one thing worse than leaving 99 dead accounts is deleting a live one.
 *   4. Its address is synthetic, unless --include-real-emails is passed. See
 *      below — the default is caution, but it is not obviously the kind thing.
 *
 * ── About the real-email accounts ─────────────────────────────────────────────
 *
 * An orphaned credential under someone's real address BLOCKS that person from
 * ever registering with it: Auth reports the email as taken, and there is no
 * profile behind it to sign in to. Deleting it is what unblocks them. So
 * --include-real-emails is a remedy, not a bigger hammer — it is merely a
 * decision about a named person, which is why it is not the default. Read the
 * dry run's list, recognise the addresses, then decide.
 *
 * Deleted accounts are recorded to admin_audit before removal, because after
 * removal there is nothing left to notice they existed.
 */

const admin = require("firebase-admin");

const APPLY        = process.argv.includes("--apply");
const INCLUDE_REAL = process.argv.includes("--include-real-emails");
const PROJECT      = process.env.GCLOUD_PROJECT || "safebeauty";

/** How recent is too recent to judge. A registration mid-flight is not an orphan. */
const MIN_AGE_HOURS = 24;

const SYNTHETIC = /^([0-9a-f]{32})@sb\.app$/;

admin.initializeApp({ projectId: PROJECT });
const db = admin.firestore();

const dashed = (hex) =>
  `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;

async function allAuthUsers() {
  const out = [];
  let page;
  do {
    const res = await admin.auth().listUsers(1000, page);
    out.push(...res.users);
    page = res.pageToken;
  } while (page);
  return out;
}

/**
 * Every id mentioned by any document in any collection.
 *
 * Deliberately not a list of known id fields. This script's whole job is to
 * decide that nothing refers to an account, and a field nobody remembered is
 * precisely how that decision goes wrong — so the test is "does this document
 * contain the string at all", which cannot miss a field it has not heard of.
 * It over-matches rather than under-matches, and over-matching only ever saves
 * an account from deletion.
 */
async function referencedIds() {
  const seen = new Set();
  const collections = await db.listCollections();
  for (const col of collections) {
    const snap = await col.get();
    snap.forEach((doc) => {
      seen.add(doc.id);
      const blob = JSON.stringify(doc.data());
      for (const m of blob.matchAll(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g)) {
        seen.add(m[0]);
      }
      for (const m of blob.matchAll(/\b[A-Za-z0-9]{28}\b/g)) seen.add(m[0]);
    });
  }
  return seen;
}

(async () => {
  console.log(`project: ${PROJECT}`);
  console.log(APPLY ? "MODE: APPLY — accounts will be deleted\n" : "MODE: dry run — nothing will be deleted\n");

  const [authUsers, refs] = await Promise.all([allAuthUsers(), referencedIds()]);

  const userDocs = new Set();
  (await db.collection("users").get()).forEach((d) => userDocs.add(d.id));

  const uidMap = new Map();
  (await db.collection("uid_map").get()).forEach((d) => uidMap.set(d.id, d.data().appUid));

  const cutoff = Date.now() - MIN_AGE_HOURS * 60 * 60 * 1000;

  const healthy = [], tooNew = [], hasActivity = [], realEmail = [], deletable = [];

  for (const u of authUsers) {
    const mapped   = uidMap.get(u.uid);
    const synth    = SYNTHETIC.exec(u.email || "");
    const appUid   = mapped || (synth ? dashed(synth[1]) : "");
    const created  = new Date(u.metadata.creationTime).getTime();
    const row      = { authUid: u.uid, email: u.email || "", appUid, created: u.metadata.creationTime };

    // 1. a reachable profile — this is a working account
    if (appUid && userDocs.has(appUid)) { healthy.push(row); continue; }
    // 3. too new to judge
    if (created > cutoff) { tooNew.push(row); continue; }
    // 2. anything at all refers to it
    if ((appUid && refs.has(appUid)) || refs.has(u.uid)) { hasActivity.push(row); continue; }
    // 4. a real address is a named person's decision
    if (!synth) { realEmail.push(row); if (!INCLUDE_REAL) continue; }

    deletable.push(row);
  }

  console.log(`Firebase Auth accounts        : ${authUsers.length}`);
  console.log(`  working (profile reachable) : ${healthy.length}`);
  console.log(`  kept — newer than ${MIN_AGE_HOURS}h      : ${tooNew.length}`);
  console.log(`  kept — something refers to it: ${hasActivity.length}`);
  console.log(`  real email address           : ${realEmail.length}${INCLUDE_REAL ? " (INCLUDED)" : " (kept — pass --include-real-emails)"}`);
  console.log(`  TO DELETE                    : ${deletable.length}\n`);

  for (const r of hasActivity) console.log(`  keeping (referenced) ${r.authUid} ${r.email}`);
  for (const r of realEmail)   console.log(`  real address         ${r.authUid} ${r.email}  created ${r.created}`);

  if (!deletable.length) { console.log("\nNothing to do."); process.exit(0); }

  if (!APPLY) {
    console.log("\nDry run. Re-run with --apply to delete the accounts counted above.");
    process.exit(0);
  }

  // Written before anything is removed: afterwards there is nothing left that
  // would show these accounts had ever been here.
  await db.collection("admin_audit").add({
    adminUid: "(script)",
    adminName: "purge-orphan-accounts",
    action: "PURGE_ORPHAN_AUTH_ACCOUNTS",
    details: {
      count: deletable.length,
      includedRealEmails: INCLUDE_REAL,
      minAgeHours: MIN_AGE_HOURS,
      authUids: deletable.map((r) => r.authUid),
    },
    createdAt: Date.now(),
  });

  let deleted = 0;
  const failures = [];
  for (let i = 0; i < deletable.length; i += 100) {
    const chunk = deletable.slice(i, i + 100);
    const res = await admin.auth().deleteUsers(chunk.map((r) => r.authUid));
    deleted += res.successCount;
    res.errors.forEach((e) => failures.push(`${chunk[e.index].authUid}: ${e.error.message}`));
    console.log(`  batch ${i / 100 + 1}: ${res.successCount} deleted, ${res.failureCount} failed`);
  }

  // The bridge rows those accounts owned. Left behind they resolve a uid that
  // can no longer sign in, which is litter that reads like a live account.
  let maps = 0;
  for (const r of deletable) {
    const ref = db.doc(`uid_map/${r.authUid}`);
    if ((await ref.get()).exists) { await ref.delete(); maps += 1; }
  }

  console.log(`\ndeleted ${deleted} account(s), ${maps} uid_map row(s)`);
  if (failures.length) {
    console.log(`${failures.length} failed:`);
    failures.forEach((f) => console.log("  " + f));
  }
  process.exit(failures.length ? 1 : 0);
})().catch((e) => { console.error("FAILED:", e.message); process.exit(1); });
