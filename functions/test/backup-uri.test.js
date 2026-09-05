const test = require("node:test");
const assert = require("node:assert");
const { parseGsUri } = require("../domains/maintenance");

/**
 * verifyFirestoreBackup now refuses to write DONE until it has counted the
 * objects the export actually wrote. This is the function that decides WHICH
 * objects get counted, so a parsing slip does not produce a wrong number — it
 * produces a wrong verdict about whether a backup exists.
 */

test("a normal export uri splits into bucket and a prefix that ends in a slash", () => {
  assert.deepStrictEqual(
    parseGsUri("gs://safebeauty-firestore-backups/2026-09-05"),
    { bucketName: "safebeauty-firestore-backups", prefix: "2026-09-05/" }
  );
});

test("the prefix must end in a slash, or one day counts another day's objects", () => {
  // Without the trailing slash, prefix "2026-09-0" matches 2026-09-01 through
  // 2026-09-09 alike: an empty backup would be reported healthy on the strength
  // of its neighbours' files. The slash is what makes the match exact.
  const { prefix } = parseGsUri("gs://b/2026-09-05");
  assert.ok(prefix.endsWith("/"), "prefix must be slash-terminated");
  assert.strictEqual(prefix, "2026-09-05/");
  assert.ok(!"2026-09-051/x".startsWith(prefix), "and must not match a longer sibling");
});

test("a trailing slash already present is not doubled", () => {
  // "2026-09-05//" matches nothing at all, so a good backup would read as empty.
  assert.strictEqual(parseGsUri("gs://b/2026-09-05/").prefix, "2026-09-05/");
  assert.strictEqual(parseGsUri("gs://b/2026-09-05///").prefix, "2026-09-05/");
});

test("nested prefixes survive intact", () => {
  assert.deepStrictEqual(parseGsUri("gs://b/exports/daily/2026-09-05"),
    { bucketName: "b", prefix: "exports/daily/2026-09-05/" });
});

test("anything that is not a gs:// uri with both halves is refused", () => {
  // Refused, not guessed. measureBackup reads null as zero objects and the
  // caller reads zero objects as a failed backup — the conservative direction:
  // "we could not confirm one exists" must never render as DONE.
  for (const bad of [
    "", null, undefined, "  ",
    "https://storage.googleapis.com/b/p",   // not the gs scheme
    "gs://",                                 // no bucket, no path
    "gs://bucket",                           // bucket but no prefix
    "gs://bucket/",                          // prefix is empty after trimming
    "gs:///prefix",                          // empty bucket name
    "/2026-09-05",
  ]) {
    assert.strictEqual(parseGsUri(bad), null, `must refuse ${JSON.stringify(bad)}`);
  }
});

test("surrounding whitespace does not defeat it", () => {
  assert.deepStrictEqual(parseGsUri("  gs://b/p  "), { bucketName: "b", prefix: "p/" });
});
