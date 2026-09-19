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

  test(`${rel}: no STR key is defined twice`, () => {
    // A duplicate key in an object literal is legal JavaScript: the later one
    // silently wins. Adding tab_content for a new tab therefore overwrote the
    // existing Content tab's translations and changed its Pashto from
    // منځپانګه to محتوا — a string nobody would re-read, in a language most
    // reviewers cannot check, with no error anywhere.
    const start = src.indexOf("const STR = {");
    const block = src.slice(start, src.indexOf("\n};", start));
    const seen = new Set(), duplicated = [];
    for (const m of block.matchAll(/^\s{2}([A-Za-z0-9_]+)\s*:/gm)) {
      if (seen.has(m[1])) duplicated.push(m[1]);
      seen.add(m[1]);
    }
    assert.deepStrictEqual(duplicated, [], `defined twice: ${duplicated}`);
  });
}

/**
 * Two tabs cannot share an id or an icon.
 *
 * A duplicate id highlighted both tabs at once and made the sidebar read as if
 * the console had two Content sections; a duplicate icon made the new one
 * indistinguishable from Admins. Found by the owner looking at his own
 * sidebar, which is the only thing that has ever tested this file.
 */
test("public/admin/index.html: every tab has its own id and icon", () => {
  const src = fs.readFileSync(path.join(ROOT, "public/admin/index.html"), "utf8");
  const start = src.indexOf("const TABS = [");
  assert.ok(start > 0, "could not find the TABS array");
  const block = src.slice(start, src.indexOf("\n];", start));

  const ids = [...block.matchAll(/id:\s*'([^']+)'/g)].map((m) => m[1]);
  const icons = [...block.matchAll(/icon:\s*'([^']+)'/g)].map((m) => m[1]);
  assert.ok(ids.length > 10, `read only ${ids.length} tabs — the check would pass vacuously`);

  const dupes = (xs) => xs.filter((x, i) => xs.indexOf(x) !== i);
  assert.deepStrictEqual(dupes(ids), [], `duplicate tab ids: ${dupes(ids)}`);
  assert.deepStrictEqual(dupes(icons), [], `duplicate tab icons: ${dupes(icons)}`);

  // And every tab's label must exist, or the sidebar renders the raw key.
  const defined = new Set();
  const strStart = src.indexOf("const STR = {");
  const strBlock = src.slice(strStart, src.indexOf("\n};", strStart));
  for (const m of strBlock.matchAll(/^\s{2}([A-Za-z0-9_]+)\s*:/gm)) defined.add(m[1]);
  const unlabelled = ids.filter((id) => !defined.has(`tab_${id}`));
  assert.deepStrictEqual(unlabelled, [], `tabs with no tab_<id> string: ${unlabelled}`);
});
