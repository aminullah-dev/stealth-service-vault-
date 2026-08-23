const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");

/**
 * The two web consoles are single self-contained HTML files with no build step
 * and no linter, so nothing but a browser has ever executed them. Both bugs
 * this file guards against shipped to production and were found by a human
 * clicking a tab.
 *
 * 1. `t` is the translation function. In renderSalons a local
 *    `const t = term.trim().toLowerCase()` shadowed it, so opening the Salons
 *    tab died with `t is not a function ('t' is "")` — every row's button label
 *    called t(). The rule is therefore absolute: inside these files the
 *    identifier `t` is the translation function and nothing else, ever.
 *
 * 2. A t('key') whose key is not in STR renders as the raw key. The console
 *    would look translated except for the one string nobody re-read.
 */
const ROOT = path.join(__dirname, "..", "..");
const CONSOLES = ["public/admin/index.html", "public/provider/index.html"];

/** Every way JS can bind the name `t` in these files. */
const BINDS_T = [
  /(?:const|let|var)\s+t\s*[=;,]/,
  /(?:^|[^\w.$])t\s*=>/,
  /function\s*\w*\s*\([^)]*\bt\b[^)]*\)/,
  /\(\s*t\s*[,)]/,
  /,\s*t\s*[,)]/,
];

for (const rel of CONSOLES) {
  const src = fs.readFileSync(path.join(ROOT, rel), "utf8");

  test(`${rel}: nothing shadows the translation function t()`, () => {
    const offenders = [];
    src.split("\n").forEach((line, i) => {
      if (/^\s*(\/\/|\*)/.test(line)) return;          // comments
      for (const re of BINDS_T) {
        if (re.test(line)) { offenders.push(`${i + 1}: ${line.trim()}`); break; }
      }
    });
    assert.deepStrictEqual(offenders, [],
      "these lines bind the name `t`, which shadows the translation function:\n" +
      offenders.join("\n"));
  });

  test(`${rel}: every t('key') and data-i18n key exists in STR`, () => {
    const start = src.indexOf("const STR = {");
    assert.ok(start > 0, "STR table not found");
    const block = src.slice(start, src.indexOf("\n};", start));
    const defined = new Set(
      [...block.matchAll(/^\s{2}([A-Za-z0-9_]+)\s*:\s*[{(]/gm)].map((m) => m[1]));

    const used = new Set();
    // t('literal') only — t('tab_' + id) and friends are resolved at runtime.
    for (const m of src.matchAll(/\bt\(\s*['"]([^'"]+)['"]\s*[),]/g)) used.add(m[1]);
    for (const m of src.matchAll(/data-i18n(?:-[a-z]+)?="([^"]+)"/g)) used.add(m[1]);

    const missing = [...used].filter((k) => !defined.has(k));
    assert.deepStrictEqual(missing, [], `keys used but not in STR: ${missing}`);
  });

  test(`${rel}: every STR entry has all three languages`, () => {
    const start = src.indexOf("const STR = {");
    const block = src.slice(start, src.indexOf("\n};", start));
    const incomplete = [];
    // Brace-count rather than [^}]* — a value can be (n)=>`… ${n} …`, and the
    // ${…} of a template literal is balanced, so counting still lands right.
    for (const m of block.matchAll(/^\s{2}([A-Za-z0-9_]+)\s*:\s*\{/gm)) {
      let depth = 0, i = m.index + m[0].length - 1;
      do { if (block[i] === "{") depth++; else if (block[i] === "}") depth--; i++; }
      while (depth > 0 && i < block.length);
      const body = block.slice(m.index + m[0].length, i - 1);
      if (!(/\ben\s*:/.test(body) && /\bfa\s*:/.test(body) && /\bps\s*:/.test(body))) {
        incomplete.push(m[1]);
      }
    }
    assert.deepStrictEqual(incomplete, [], `missing a language: ${incomplete}`);
  });
}
