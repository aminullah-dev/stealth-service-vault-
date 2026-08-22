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
        ):
            m = re.match(pat, l)
            if m:
                marks.append((i, m.group(1), kind))
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
    blocks = {}
    for idx, (start, name, kind) in enumerate(adjusted):
        end = adjusted[idx + 1][0] if idx + 1 < len(adjusted) else len(lines)
        blocks[name] = (kind, start, end)
    return blocks


def references(body, names):
    return {n for n in names if re.search(r"\b%s\b" % re.escape(n), body)}


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

    helpers = [n for n, (k, _, _) in blocks.items() if k == "helper"]
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
    needed = {}
    for name, mod in reqs.items():
        if name in moving:
            continue
        if re.search(r"\b%s\b" % re.escape(name), domain_src):
            needed.setdefault(mod, []).append(name)

    # Anything from shared.js comes from there, not from a second require of
    # firebase-admin — two initialisations is two Firestore handles.
    SHARED = {"admin", "db", "logger", "alertable"}
    req_lines = []
    for mod in sorted(needed):
        names = sorted(n for n in needed[mod] if n not in SHARED)
        if names:
            req_lines.append(f"const {{ {', '.join(names)} }} = require({mod});")

    shared_names = sorted((set(imports) | (SHARED & set(reqs))) & set(re.findall(r"\b(\w+)\b", domain_src)))
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
