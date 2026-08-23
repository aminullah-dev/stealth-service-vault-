const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");

/**
 * Every notification the server sends can be translated, and every translation
 * exists in all three languages.
 *
 * The Notification Center inside the app shows the title and body stored on the
 * document, and those are now written by pushOnNotificationCreated after running
 * them through NOTIF_I18N. A msgKey with no catalogue entry therefore does not
 * fail — localizeNotification falls back to the English the caller wrote, and
 * the customer simply gets English with nothing anywhere saying so. Two
 * notification types shipped that way for weeks.
 *
 * Static, because loading the domain modules needs Firebase.
 */

const DOMAINS = path.join(__dirname, "..", "domains");

function catalogue() {
  const src = fs.readFileSync(path.join(DOMAINS, "notifications.js"), "utf8");
  const m = src.match(/const NOTIF_I18N = \{([\s\S]*?)\n\};/);
  assert.ok(m, "NOTIF_I18N not found — this test cannot see the catalogue");
  const body = m[1];
  const entries = {};
  // Top-level keys are indented exactly two spaces; the language rows are four.
  for (const km of body.matchAll(/^ {2}(\w+):\s*\{([\s\S]*?)^ {2}\},/gm)) {
    entries[km[1]] = new Set([...km[2].matchAll(/^ {4}(en|fa|ps):/gm)].map((x) => x[1]));
  }
  return entries;
}

/** Every msgKey the backend actually sends. */
function usedKeys() {
  const out = new Map();
  for (const f of fs.readdirSync(DOMAINS)) {
    if (!f.endsWith(".js")) continue;
    const src = fs.readFileSync(path.join(DOMAINS, f), "utf8");
    for (const m of src.matchAll(/msgKey:\s*"(\w+)"/g)) {
      if (!out.has(m[1])) out.set(m[1], f);
    }
  }
  return out;
}

test("every msgKey the server sends has a catalogue entry", () => {
  const cat = catalogue();
  const used = usedKeys();
  assert.ok(used.size > 10, `only found ${used.size} msgKeys — the scan is broken`);
  const orphans = [...used].filter(([k]) => !cat[k]).map(([k, f]) => `${k} (sent from ${f})`);
  assert.deepEqual(orphans, [], orphans.join("\n"));
});

test("every catalogue entry has all three languages", () => {
  const cat = catalogue();
  assert.ok(Object.keys(cat).length > 15, "the catalogue scan found almost nothing");
  const gaps = [];
  for (const [key, langs] of Object.entries(cat)) {
    for (const l of ["en", "fa", "ps"]) {
      if (!langs.has(l)) gaps.push(`${key} is missing ${l}`);
    }
  }
  assert.deepEqual(gaps, [], gaps.join("\n"));
});

test("no notification is written without a msgKey", () => {
  // A notification with no key is one the Notification Center will show in
  // English forever, whatever language the reader chose.
  const gaps = [];
  for (const f of fs.readdirSync(DOMAINS)) {
    if (!f.endsWith(".js")) continue;
    const src = fs.readFileSync(path.join(DOMAINS, f), "utf8");
    // Notification writes are recognised by recipientId, which nothing else has.
    for (const m of src.matchAll(/recipientId:[\s\S]{0,700}?\n(\s*)\}/g)) {
      const block = m[0];
      if (!/\btype:\s*"/.test(block)) continue;      // not a notification document
      if (/msgKey:/.test(block)) continue;
      const typeMatch = block.match(/type:\s*"(\w+)"/);
      const type = typeMatch ? typeMatch[1] : "?";
      gaps.push(`${f}: a ${type} notification is written with no msgKey`);
    }
  }
  assert.deepEqual(gaps, [], gaps.join("\n"));
});
