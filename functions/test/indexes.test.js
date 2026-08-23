/**
 * Every composite index names a collection the code actually queries.
 *
 * Three indexes were added for `gallery` and `offers`. The collections are
 * called `salon_gallery` and `salon_offers`. Firestore does not mind: an index
 * on a collection that does not exist is legal, deploys cleanly, and reports
 * READY. The three real queries then fail with FAILED_PRECONDITION, the app's
 * catch turns that into an empty list, and a salon's portfolio, its offers and
 * the whole deals strip come back empty forever.
 *
 * Nothing catches that. Not lint, not the emulator, not a deploy. It was even
 * verified by hand against production and passed — because the check used the
 * collection name from the index file rather than the name in the code that
 * issues the query, so both halves were consistently wrong.
 *
 * The rule this encodes: an index is a claim about a query someone writes. If no
 * caller names that collection, the claim is about nothing.
 */

const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");
const { execFileSync } = require("node:child_process");

const REPO = path.join(__dirname, "..", "..");

/** Every collection name any Kotlin, JS or HTML in the repo passes to collection(). */
function collectionsNamedInCode() {
  const out = new Set();
  const hits = execFileSync("git", ["grep", "-hoE", "collection\\(\\s*(db\\s*,\\s*)?[\"'][a-z_]+[\"']"], {
    cwd: REPO, encoding: "utf8",
  });
  for (const line of hits.split("\n")) {
    const m = line.match(/["']([a-z_]+)["']/);
    if (m) out.add(m[1]);
  }
  return out;
}

test("every composite index is on a collection some caller queries", () => {
  const defined = JSON.parse(
    fs.readFileSync(path.join(REPO, "firestore.indexes.json"), "utf8")
  ).indexes;
  assert.ok(defined.length > 10, "read no indexes — the check would pass vacuously");

  const named = collectionsNamedInCode();
  assert.ok(named.has("appointments"), "grep found no collections — the check is broken");

  const orphans = [...new Set(defined.map((i) => i.collectionGroup))]
    .filter((c) => !named.has(c))
    .map((c) => `firestore.indexes.json defines an index on "${c}", which no code queries`);

  assert.deepEqual(orphans, [], orphans.join("\n"));
});
