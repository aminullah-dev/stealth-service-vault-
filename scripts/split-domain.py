#!/usr/bin/env python3
"""
Move one domain's exports out of functions/index.js into functions/domains/<name>.js.

Splitting a 5,700-line file that settles payments is not a thing to do by hand:
a helper left behind, or one moved that something else still needed, fails at
runtime rather than at parse time. So the move is computed from the file's own
structure and checked, not typed.

The rules it follows:

  * an export takes with it every top-level helper only IT uses,
  * a helper used by anything still in index.js stays in index.js and is
    imported by the domain,
  * original order is preserved inside the domain file, because `const` is not
    hoisted and reordering two declarations can turn a working module into a
    ReferenceError that only fires on the one code path that touches it.

Usage:  python3 scripts/split-domain.py <domain> <exportA> <exportB> ...
"""

import re
import sys
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
INDEX = os.path.join(ROOT, "functions", "index.js")
DOMAINS = os.path.join(ROOT, "functions", "domains")


def top_level_blocks(lines):
    """Every top-level declaration, as name -> (kind, start, end)."""
    marks = []
    for i, l in enumerate(lines):
        for pat, kind in (
            (r"^(?:async )?function (\w+)", "helper"),
            (r"^const (\w+)\s*=", "helper"),
            (r"^let (\w+)\s*=", "helper"),
            (r"^exports\.(\w+)\s*=", "export"),
            # A multi-line `const { a, b } = require(...)` is a top-level
            # declaration too. Without it as a boundary, one sitting at the end
            # of the file falls inside the last export's block and travels into
            # the domain — which then declares it twice, once in its own header
            # and once in the carried body.
            (r"^const \{(\s*)$", "require-block"),
        ):
            m = re.match(pat, l)
            if m:
                name = m.group(1) if kind != "require-block" else f"__require_block_{i}"
                marks.append((i, name, kind))
                break

    # Carry the comment block immediately above each declaration with it — a
    # comment explaining why code is the way it is is worth as much as the code,
    # and orphaning it is how it stops being true.
    def with_comments(start):
        s = start
        while s > 0 and lines[s - 1].startswith("//"):
            s -= 1
        return s

    adjusted = [(with_comments(start), name, kind) for start, name, kind in marks]

    # Each block ends where the NEXT one begins, comments included. Using the
    # next declaration's raw line instead leaves its comment header inside this
    # block too, so two blocks overlap — and a move built on overlapping ranges
    # copies code without removing it, leaving the same function defined twice
    # with the later definition silently winning.
    def trim_tail(end):
        """
        Give back trailing blanks and comments to whatever follows.

        A section header or doc comment separated from its declaration by a
        blank line is not picked up as that declaration's header, so it falls
        into the PREVIOUS block's tail — and moving that block carries the
        comment away from the code it describes and parks it above unrelated
        code. A comment explaining one function sitting above another is worse
        than no comment: it is confidently wrong.
        """
        while end > 0 and (lines[end - 1].strip() == "" or lines[end - 1].startswith("//")):
            end -= 1
        return end

    blocks = {}
    for idx, (start, name, kind) in enumerate(adjusted):
        raw_end = adjusted[idx + 1][0] if idx + 1 < len(adjusted) else len(lines)
        blocks[name] = (kind, start, trim_tail(raw_end))
    return blocks


def strip_noise(src):
    """
    Remove comments and string literals before looking for identifiers.

    Without this, a name that is also an ordinary English word matches inside
    prose — "content-type" in a header, or the word "content" in a comment —
    and the domain gets an import for a module it never uses. Comments in this
    codebase are long and deliberate, which makes the false-match rate high.
    """
    src = re.sub(r"/\*.*?\*/", " ", src, flags=re.S)
    src = re.sub(r"//[^\n]*", " ", src)
    src = re.sub(r'"(?:[^"\\\n]|\\.)*"', '""', src)
    src = re.sub(r"'(?:[^'\\\n]|\\.)*'", "''", src)
    # Template literals keep their ${...} expressions and lose their prose.
    # `${HESAB_BASE_URL.value()}/payment` is executable code wearing a string's
    # clothes — stripping the whole literal made the tool stop seeing a real
    # dependency, and the constant stayed behind while the code using it moved.
    # Keeping the whole literal instead makes ordinary words in message text
    # look like identifiers. Keeping only the expressions is both.
    def keep_expressions(m):
        return " ".join(re.findall(r"\$\{([^{}]*)\}", m.group(0)))

    src = re.sub(r"`(?:[^`\\]|\\.)*`", keep_expressions, src, flags=re.S)
    return src


def references(body, names):
    code = strip_noise(body)
    return {n for n in names if re.search(r"\b%s\b" % re.escape(n), code)}


def destructured_requires(src):
    """
    Map each name pulled out of a require() to the module it came from.

    A domain needs its own copy of these — `const { onSchedule } = require(...)`
    is not a top-level declaration the block scanner sees, so without this the
    moved code parses cleanly and then dies on first load with
    "onSchedule is not defined". Which is exactly what it did.
    """
    out = {}
    for m in re.finditer(r'^const \{([^}]+)\}\s*=\s*require\((".*?")\);', src, re.M):
        names, mod = m.group(1), m.group(2)
        for raw in names.split(","):
            name = raw.split(":")[-1].strip()
            if name:
                out[name] = mod
    for m in re.finditer(r'^const (\w+)\s*=\s*require\((".*?")\);', src, re.M):
        out[m.group(1)] = m.group(2)
    return out


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(2)

    domain, wanted = sys.argv[1], sys.argv[2:]
    src = open(INDEX, encoding="utf-8").read()
    lines = src.split("\n")
    blocks = top_level_blocks(lines)

    missing = [w for w in wanted if w not in blocks or blocks[w][0] != "export"]
    if missing:
        print(f"not exports in index.js: {missing}")
        sys.exit(1)

    # A line like `exports.foo = domain.foo;` is index.js re-exporting an
    # already-moved function, not a definition. Matching it as movable would
    # carry the wiring out of index.js and leave the function undeployed —
    # while the tool reported a clean move.
    reexport = re.compile(r"^exports\.\w+\s*=\s*\w+\.\w+;\s*$")
    already = [w for w in wanted if reexport.match(lines[blocks[w][1]].strip() or lines[blocks[w][1]])]
    if already:
        print(f"already split (index.js only re-exports these): {already}")
        sys.exit(1)

    # Anything bound by a require() is not a helper this domain could take with
    # it — it is either a library (crypto) or wiring for an already-split domain.
    # Both match `^const NAME =`, so without this they look movable, and the
    # orphan check then reports them as missing from shared.js.
    require_bindings = set(destructured_requires(src))
    helpers = [n for n, (k, _, _) in blocks.items()
               if k == "helper" and n not in require_bindings]
    all_exports = [n for n, (k, _, _) in blocks.items() if k == "export"]
    staying = [e for e in all_exports if e not in wanted]

    def body_of(n):
        _, s, e = blocks[n]
        return "\n".join(lines[s:e])

    # Grow the moving set until it is closed: a helper only this domain uses
    # moves with it, and so does anything only that helper uses.
    moving = set(wanted)
    while True:
        used = set()
        for n in moving:
            used |= references(body_of(n), helpers)
        exclusive = set()
        for h in used:
            if h in moving:
                continue
            others = [e for e in staying if h in references(body_of(e), [h])]
            others += [x for x in helpers if x not in moving and h in references(body_of(x), [h]) and x != h]
            if not others:
                exclusive.add(h)
        if not exclusive:
            break
        moving |= exclusive

    moving_helpers = sorted(moving - set(wanted), key=lambda n: blocks[n][1])
    imports = sorted({h for n in moving for h in references(body_of(n), helpers)} - moving)

    print(f"domain: {domain}")
    print(f"  exports moving   : {len(wanted)}")
    print(f"  helpers moving   : {len(moving_helpers)} {moving_helpers if moving_helpers else ''}")
    print(f"  imported from core: {len(imports)}")

    ordered = sorted(moving, key=lambda n: blocks[n][1])
    parts = [body_of(n).rstrip() for n in ordered]

    # Framework and library symbols this domain uses, grouped by their module.
    reqs = destructured_requires(src)
    domain_src = "\n\n".join(parts)
    domain_code = strip_noise(domain_src)
    needed = {}
    for name, mod in reqs.items():
        if name in moving:
            continue
        # A bare word match is not a use. `payments: rows(paySnap)` is an object
        # property key and `x.payments` is a member access; neither refers to a
        # module. Emitting a require for them adds a cross-domain dependency
        # that is not real — and a cycle waiting to happen.
        #
        # Applied only here, to the import list. The helper-dependency walk
        # above stays deliberately generous: a spurious import is untidy, a
        # missed one is a ReferenceError.
        uses = [
            m for m in re.finditer(r"\b%s\b" % re.escape(name), domain_code)
            if not (m.start() > 0 and domain_code[m.start() - 1] == ".")
            and not re.match(r"\s*:", domain_code[m.end():m.end() + 4])
        ]
        # A name the domain declares for itself — a local const, or a
        # destructuring target — shadows any import of it. Emitting the require
        # anyway adds a module dependency that nothing can even reach.
        shadowed = re.search(
            r"(?:const|let|var)\s+(?:\[|\{)?[^=;]*\b%s\b[^=;]*(?:\]|\})?\s*=" % re.escape(name),
            domain_code,
        )
        if uses and not shadowed:
            needed.setdefault(mod, []).append(name)

    # Anything from shared.js comes from there, not from a second require of
    # firebase-admin — two initialisations is two Firestore handles.
    SHARED = {"admin", "db", "logger", "alertable"}
    def rebase(mod):
        """
        A domain file sits one directory deeper than index.js, so every relative
        require has to gain a level. Copying the path verbatim produces
        MODULE_NOT_FOUND at load time — after the move looks successful.
        """
        return mod.replace('"./', '"../', 1) if mod.startswith('"./') else mod

    # `const crypto = require("crypto")` binds the whole module; emitting it as
    # `const { crypto } = require("crypto")` yields undefined.
    whole = set(re.findall(r'^const (\w+)\s*=\s*require\(', src, re.M)) - set(
        n for m2 in re.finditer(r'^const \{([^}]+)\}\s*=\s*require\(', src, re.M)
        for n in re.findall(r"\w+", m2.group(1)))

    req_lines = []
    for mod in sorted(needed):
        names = sorted(n for n in needed[mod] if n not in SHARED)
        for n in [x for x in names if x in whole]:
            req_lines.append(f"const {n} = require({rebase(mod)});")
        rest = [x for x in names if x not in whole]
        if rest:
            req_lines.append(f"const {{ {', '.join(rest)} }} = require({rebase(mod)});")

    # Everything the domain still needs from outside must actually be IN
    # shared.js. The first two domains only needed things that were, so this
    # went unchecked: a helper that stays in index.js would have been imported
    # from "../shared" anyway, and come back undefined at the first call — with
    # the move reported as clean.
    shared_src = open(os.path.join(ROOT, "functions", "shared.js"), encoding="utf-8").read()
    m = re.search(r"module\.exports\s*=\s*\{(.*?)\};", shared_src, re.S)
    shared_exports = set(re.findall(r"\b(\w+)\b", m.group(1))) if m else set()

    orphans = sorted(n for n in imports if n not in shared_exports)
    if orphans:
        print("  *** these stay in index.js but the domain needs them ***")
        print(f"      {orphans}")
        print("      Move them to shared.js first, or keep their consumer in index.js.")
        sys.exit(1)

    shared_names = sorted((set(imports) | (SHARED & set(reqs))) & set(re.findall(r"\b(\w+)\b", domain_code)))
    if shared_names:
        req_lines.append(f'const {{ {", ".join(shared_names)} }} = require("../shared");')

    header = (
        f"// {domain} — moved out of index.js, which had grown past 5,700 lines.\n"
        f"//\n"
        f"// Every export here is registered by index.js re-exporting this module,\n"
        f"// so the deployed function set is unchanged by the move.\n"
        f"\n"
        + "\n".join(req_lines) + "\n"
    )

    os.makedirs(DOMAINS, exist_ok=True)
    out = os.path.join(DOMAINS, f"{domain}.js")
    open(out, "w", encoding="utf-8").write(header + "\n" + "\n\n".join(parts) + "\n")

    keep = []
    drop_ranges = sorted((blocks[n][1], blocks[n][2]) for n in moving)
    di = 0
    i = 0
    while i < len(lines):
        if di < len(drop_ranges) and i == drop_ranges[di][0]:
            i = drop_ranges[di][1]
            di += 1
            continue
        keep.append(lines[i])
        i += 1

    proposed = "\n".join(keep)
    open(INDEX + ".proposed", "w", encoding="utf-8").write(proposed)

    # The check that matters: every declaration that moved must now exist in
    # exactly one of the two files. A count of lines removed looks reassuring
    # whether or not the move actually happened.
    moved_file = open(out, encoding="utf-8").read()
    bad = []
    for n in sorted(moving):
        pat = re.compile(r"^(?:exports\.|(?:async )?function |const |let )%s\b" % re.escape(n), re.M)
        here = len(pat.findall(moved_file))
        there = len(pat.findall(proposed))
        if here != 1 or there != 0:
            bad.append(f"{n}: domain={here} index={there}")
    if bad:
        print("  *** MOVE IS WRONG — declaration must be in exactly one file ***")
        for b in bad:
            print("      " + b)
        sys.exit(1)

    print(f"  wrote {out}")
    print(f"  wrote {INDEX}.proposed  ({len(lines)} -> {len(keep)} lines)")
    print(f"  verified: all {len(moving)} declarations appear exactly once, in the domain file")


if __name__ == "__main__":
    main()
