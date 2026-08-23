/**
 * Every destructured require in the backend resolves to something real.
 *
 * This exists because of a bug lint cannot see. When index.js was split into
 * domains, one import lost its rename:
 *
 *     const { categoriesFor, normalize: categoryNormalize } = require(...)
 *     const { categoriesFor, categoryNormalize }            = require(...)
 *
 * lib/categories exports `normalize`, not `categoryNormalize`. The second form
 * is perfectly valid JavaScript: the binding exists and its value is undefined.
 * no-undef has nothing to complain about, node --check is happy, and the module
 * loads. It fails at the moment the function is called — which for
 * deriveSalonFields is the moment a salon edits its profile, and for
 * normalizeSalonsDaily was 20:30 every night for as long as nobody looked.
 *
 * A missing export is a typo the compiler would have caught in almost any other
 * language. Here it is a runtime error that waits for the right user action, so
 * it gets checked statically instead.
 */

const test = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");

const ROOT = path.join(__dirname, "..");

/** The names a module's `module.exports = { … }` actually provides. */
function exportedNames(file) {
  const src = fs.readFileSync(file, "utf8");
  const m = src.match(/module\.exports\s*=\s*\{([\s\S]*?)\n?\};/);
  if (!m) return null;
  return new Set(
    m[1]
      .split(",")
      .map((n) => n.split(":")[0].trim())
      .filter(Boolean)
  );
}

function localModules() {
  const out = {};
  for (const f of fs.readdirSync(path.join(ROOT, "lib"))) {
    if (!f.endsWith(".js")) continue;
    const names = exportedNames(path.join(ROOT, "lib", f));
    if (names) out[`../lib/${f.slice(0, -3)}`] = names;
  }
  const shared = exportedNames(path.join(ROOT, "shared.js"));
  if (shared) out["../shared"] = shared;
  return out;
}

test("every destructured import in a domain names a real export", () => {
  const modules = localModules();
  assert.ok(Object.keys(modules).length > 5, "found no local modules to check");

  const problems = [];
  const dir = path.join(ROOT, "domains");
  for (const file of fs.readdirSync(dir)) {
    if (!file.endsWith(".js")) continue;
    const src = fs.readFileSync(path.join(dir, file), "utf8");
    for (const m of src.matchAll(/const\s*\{([^}]*)\}\s*=\s*require\("([^"]+)"\)/g)) {
      const names = modules[m[2]];
      if (!names) continue;                       // an npm package, not ours
      for (const raw of m[1].split(",")) {
        const wanted = raw.split(":")[0].trim();  // the name in the module
        if (!wanted) continue;
        if (!names.has(wanted)) {
          problems.push(`domains/${file} imports "${wanted}" from ${m[2]}, which does not export it`);
        }
      }
    }
  }
  assert.deepEqual(problems, [], problems.join("\n"));
});

test("the checker can actually see an export list", () => {
  // Guard against the regexes quietly matching nothing and the test above
  // passing because it examined zero imports.
  const modules = localModules();
  assert.ok(modules["../lib/categories"], "lib/categories was not read");
  assert.ok(modules["../lib/categories"].has("normalize"));
  assert.equal(modules["../lib/categories"].has("categoryNormalize"), false,
    "if this ever becomes true the original bug is no longer detectable here");
});
