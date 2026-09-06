#!/usr/bin/env python3
"""Generate the iOS palettes from Android's Color.kt.

Android shipped first and its six brands are what customers already know, so
it is the source of truth for brand colour. Transcribing 12 palettes x 32
values by hand would drift on the first change; this regenerates them, the way
functions/test/areas.test.js keeps lib/areas.js honest against Areas.kt.

    python3 scripts/gen-palettes.py
"""
import re, pathlib, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
KT = ROOT / "app/src/main/java/com/safebeauty/app/ui/theme/Color.kt"
OUT = ROOT / "ios/SafeBeautyCore/Sources/SafeBeautyCore/Theme/Palettes.swift"

src = KT.read_text()
pals = re.findall(r"val (\w+)Palette = Palette\((.*?)\n\)", src, re.S)
dc = re.search(r"data class Palette\((.*?)\n\)", src, re.S).group(1)
solid = [n for n in re.findall(r"^\s*val (\w+):\s*Color\s*(?:,|$)", dc, re.M)]
grads = [n for n in re.findall(r"^\s*val (\w+):\s*List<Color>", dc, re.M)]
if not pals:
    sys.exit("no palettes parsed from Color.kt — did its shape change?")

def col(h):
    return f"Color(hex: 0x{h.upper()})"

out = ['''import SwiftUI

// GENERATED from app/src/main/java/com/safebeauty/app/ui/theme/Color.kt by
// scripts/gen-palettes.py. Do not hand-edit — regenerate instead, so the two
// platforms cannot drift apart on brand colour.

public struct Palette: Equatable, Sendable {
    public let name: String
    public let isDark: Bool''']
out += [f"    public let {n}: Color" for n in solid]
out += [f"    public let {n}: [Color]" for n in grads]
out.append("}\n")

for pname, body in pals:
    sol = dict(re.findall(r"(\w+)\s*=\s*Color\(0x([0-9A-Fa-f]{8})\)", body))
    gr = dict(re.findall(r"(\w+)\s*=\s*listOf\(([^\n]*)\)", body))
    isdark = "true" if re.search(r"isDark\s*=\s*true", body) else "false"
    lines = [f"public let palette{pname} = Palette(", f'    name: "{pname}", isDark: {isdark},']
    for n in solid:
        h = sol.get(n)
        lines.append(f"    {n}: {col(h[2:]) if h else 'Color.clear'},")
    for i, n in enumerate(grads):
        hexes = re.findall(r"0x([0-9A-Fa-f]{8})", gr.get(n, ""))
        arr = ", ".join(col(h[2:]) for h in hexes) or "Color.clear"
        lines.append(f"    {n}: [{arr}]" + ("," if i < len(grads) - 1 else ""))
    lines.append(")\n")
    out.append("\n".join(lines))

names = ", ".join(f'"{p}": palette{p}' for p, _ in pals)
out.append(f"/// Every palette Android defines, keyed by its Kotlin name.\npublic let allPalettes: [String: Palette] = [{names}]\n")
OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text("\n".join(out))
print(f"wrote {OUT.relative_to(ROOT)}: {len(pals)} palettes, {len(solid)} colours + {len(grads)} gradients each")
