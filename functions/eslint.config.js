/**
 * Lint for the backend — correctness only, not style.
 *
 * This exists because of what the domain split actually broke. index.js went
 * from 5,747 lines to nine modules, and every bug that move introduced was of
 * one of three kinds:
 *
 *   · an identifier that no longer resolved  (`onSchedule is not defined`,
 *     a `HESAB_BASE_URL` left behind in the file it was used from)
 *   · a declaration that ended up in two files at once
 *   · a require binding kept after the code that used it had moved away
 *
 * Every one of those is `no-undef`, `no-redeclare` or `no-unused-vars`. They
 * were found by running the code — which for a Cloud Function means finding
 * them after deploy, on the first request that reaches the broken path. A
 * ReferenceError in a callable is a 500 to a customer trying to pay.
 *
 * So the rule set is deliberately narrow: things that are wrong, not things
 * that are untidy. No formatting rules, no opinions about quotes or semicolons
 * — 6,000 lines of working code should not have to be rewritten to turn this
 * on, and a lint job that fails on style is a lint job people learn to skip.
 */

const NODE_GLOBALS = {
  require: "readonly",
  module: "writable",
  exports: "writable",
  process: "readonly",
  console: "readonly",
  Buffer: "readonly",
  URL: "readonly",
  URLSearchParams: "readonly",
  TextEncoder: "readonly",
  TextDecoder: "readonly",
  fetch: "readonly",
  setTimeout: "readonly",
  clearTimeout: "readonly",
  setInterval: "readonly",
  clearInterval: "readonly",
  setImmediate: "readonly",
  structuredClone: "readonly",
  AbortController: "readonly",
  __dirname: "readonly",
  __filename: "readonly",
  globalThis: "readonly",
};

module.exports = [
  {
    files: ["**/*.js"],
    ignores: ["node_modules/**"],
    languageOptions: {
      ecmaVersion: 2024,
      sourceType: "commonjs",
      globals: NODE_GLOBALS,
    },
    linterOptions: {
      // An unused disable comment usually means the code it guarded has moved
      // or been fixed — and the comment now silently weakens the next edit.
      reportUnusedDisableDirectives: "error",
    },
    rules: {
      // The three that would have caught the split.
      "no-undef": "error",
      "no-redeclare": "error",
      "no-unused-vars": [
        "error",
        {
          args: "none",              // Firebase handlers take (request, context)
          varsIgnorePattern: "^_",
          caughtErrors: "none",      // `catch (e)` with an ignored e is idiomatic here
          // `const { pinHash, salt, ...safeUser } = user` is how this codebase
          // strips credentials before returning a user to an admin. The named
          // bindings exist precisely so they are NOT in the rest object; they
          // are unused on purpose, and that is the point of the line.
          ignoreRestSiblings: true,
        },
      ],

      // Things that are always a mistake in this codebase.
      "no-dupe-keys": "error",       // a duplicated Firestore field silently wins
      "no-dupe-args": "error",
      "no-dupe-else-if": "error",
      "no-unreachable": "error",
      "no-fallthrough": "error",
      "no-self-assign": "error",
      "no-self-compare": "error",
      "no-unsafe-negation": "error",
      "no-unsafe-optional-chaining": "error",
      "no-constant-condition": ["error", { checkLoops: false }],
      "no-cond-assign": "error",
      "no-sparse-arrays": "error",
      "no-func-assign": "error",
      "no-import-assign": "error",
      "no-obj-calls": "error",
      "use-isnan": "error",
      "valid-typeof": "error",
      "no-async-promise-executor": "error",

      // A promise nobody waits for is how a write silently does not happen.
      // Warn, not error: the codebase has deliberate fire-and-forget paths
      // (uid_map refresh, audit logging) that are commented as such.
      "require-atomic-updates": "warn",
    },
  },
];
