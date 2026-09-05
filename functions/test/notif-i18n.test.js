"use strict";

// Every notification the server sends must exist in all three languages.
//
// The catalogue and the senders live in different files, so a new notification
// is one edit away from shipping English-only to a Dari-first audience — which
// is exactly how the Notification Center stayed English for months while the
// push banner was translated. This reads both sides of that gap directly from
// the source, so adding a msgKey without translating it fails here rather than
// on a customer's phone.

const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");

const ROOT = path.join(__dirname, "..");
const SOURCES = [
  ...fs.readdirSync(path.join(ROOT, "domains")).filter((f) => f.endsWith(".js"))
    .map((f) => path.join("domains", f)),
  ...fs.readdirSync(ROOT).filter((f) => f.endsWith(".js")),
];

const read = (rel) => fs.readFileSync(path.join(ROOT, rel), "utf8");

/** Keys the catalogue defines, as written. */
function catalogueKeys() {
  const src = read("domains/notifications.js");
  const start = src.indexOf("const NOTIF_I18N");
  assert.ok(start > -1, "NOTIF_I18N has moved or been renamed");
  const body = src.slice(start, src.indexOf("\n};", start));
  return { body, keys: new Set([...body.matchAll(/^  ([A-Z][A-Z0-9_]+):/gm)].map((m) => m[1])) };
}

/** Keys any function actually sends. Handles `msgKey: cond ? "A" : "B"`. */
function sentKeys() {
  const found = new Set();
  for (const rel of SOURCES) {
    for (const m of read(rel).matchAll(/msgKey:\s*([^\n]+)/g)) {
      for (const k of m[1].matchAll(/"([A-Z][A-Z0-9_]+)"/g)) found.add(k[1]);
    }
  }
  return found;
}

test("every notification sent has a catalogue entry", () => {
  const { keys } = catalogueKeys();
  const missing = [...sentKeys()].filter((k) => !keys.has(k)).sort();
  assert.deepStrictEqual(missing, [],
    `these would reach a Dari or Pashto reader in English: ${missing.join(", ")}`);
});

test("every catalogue entry has all three languages, with a title and a body", () => {
  const { body, keys } = catalogueKeys();
  for (const key of keys) {
    const at = body.indexOf(`\n  ${key}: {`);
    const next = [...keys].map((k) => body.indexOf(`\n  ${k}: {`)).filter((i) => i > at);
    const entry = body.slice(at, next.length ? Math.min(...next) : body.length);
    for (const lang of ["en", "fa", "ps"]) {
      assert.match(entry, new RegExp(`\\b${lang}:\\s*\\{`), `${key} is missing ${lang}`);
    }
    // A title and a body per language — three of each, none of them empty.
    assert.strictEqual((entry.match(/\bt:\s*"/g) || []).length, 3, `${key}: expected 3 titles`);
    assert.strictEqual((entry.match(/\bb:\s*(\(|")/g) || []).length, 3, `${key}: expected 3 bodies`);
    assert.ok(!/\bt:\s*""/.test(entry), `${key} has an empty title`);
  }
});

test("no catalogue entry is dead weight", () => {
  const { keys } = catalogueKeys();
  const sent = sentKeys();
  const unused = [...keys].filter((k) => !sent.has(k)).sort();
  assert.deepStrictEqual(unused, [], `translated but never sent: ${unused.join(", ")}`);
});

test("the Dari and Pashto bodies interpolate the same parameters as English", () => {
  const { body, keys } = catalogueKeys();
  const params = (s) => new Set([...s.matchAll(/\$\{p\.([A-Za-z0-9_]+)\}/g)].map((m) => m[1]));
  for (const key of keys) {
    const at = body.indexOf(`\n  ${key}: {`);
    const next = [...keys].map((k) => body.indexOf(`\n  ${k}: {`)).filter((i) => i > at);
    const entry = body.slice(at, next.length ? Math.min(...next) : body.length);
    const lines = entry.split("\n");
    const per = {};
    let lang = null;
    for (const line of lines) {
      const m = line.match(/^\s*(en|fa|ps):\s*\{/);
      if (m) { lang = m[1]; per[lang] = new Set(); }
      if (lang) for (const q of params(line)) per[lang].add(q);
    }
    // A translation that drops ${p.amount} silently tells the customer nothing
    // about how much money is involved.
    assert.deepStrictEqual([...(per.fa || [])].sort(), [...(per.en || [])].sort(),
      `${key}: the Dari body uses different parameters from English`);
    assert.deepStrictEqual([...(per.ps || [])].sort(), [...(per.en || [])].sort(),
      `${key}: the Pashto body uses different parameters from English`);
  }
})
