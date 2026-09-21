#!/usr/bin/env python3
"""
Render the iOS app icon from the SAME vector source as the Android launcher.

    python3 scripts/ios-icon.py

Writes ios/SafeBeauty/Resources/Assets.xcassets/AppIcon.appiconset/icon-1024.png.

Why this exists. iOS 1.0 shipped with a placeholder — a white "SB" on the rose
gradient — while Android's launcher, the Play listing and every marketing image
use the flower. A customer who saw the flower in a post looked for it on the
App Store and found two letters.

The flower is defined once, as SVG, in play-store/generate_assets.py (the
Android vector translated by hand). That script needs cairosvg, which is not
installed on this Mac, so rather than add a dependency this reads the same SVG
strings out of it and renders them with the Chrome that marketing/generate.py
already uses.

Only the Android adaptive-icon SAFE ZONE is rendered — viewBox 18 18 72 72 of
the 108-unit canvas. Android launchers show exactly that centre two-thirds and
mask the rest; iOS shows the whole square. Rendering all 108 units made the
flower look shrunken on iOS next to the same icon on an Android home screen.

The output has no alpha channel (Chrome's screenshot is RGB), which the App
Store requires of the 1024 icon; the script asserts it rather than trusting it.
"""

import io
import os
import struct
import subprocess
import sys
import tempfile
import types

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE = os.path.join(ROOT, "play-store", "generate_assets.py")
OUT = os.path.join(ROOT, "ios", "SafeBeauty", "Resources", "Assets.xcassets",
                   "AppIcon.appiconset", "icon-1024.png")
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
SIZE = 1024


def svg_sources():
    """BACKGROUND_SVG and FOREGROUND_SVG, exactly as the Play icon uses them."""
    src = io.open(SOURCE, encoding="utf-8").read()
    head = src[:src.index("def render_svg")]
    # Only the string definitions are wanted; the rendering imports are stubbed
    # so this runs without cairosvg or Pillow.
    sys.modules.setdefault("cairosvg", types.ModuleType("cairosvg"))
    pil = types.ModuleType("PIL")
    for name in ("Image", "ImageDraw", "ImageFilter", "ImageFont"):
        setattr(pil, name, None)
    sys.modules.setdefault("PIL", pil)
    ns = {"__file__": SOURCE}
    exec(compile(head, SOURCE, "exec"), ns)
    return ns["BACKGROUND_SVG"], ns["FOREGROUND_SVG"]


def main():
    bg, fg = svg_sources()
    svg = (bg + fg).format(size=SIZE)
    n = svg.count('viewBox="0 0 108 108"')
    if n != 2:
        sys.exit("expected two 108-unit viewBoxes in the source, found %d" % n)
    svg = svg.replace('viewBox="0 0 108 108"', 'viewBox="18 18 72 72"')
    html = ('<!doctype html><meta charset="utf-8"><style>html,body{margin:0;'
            'width:%dpx;height:%dpx;overflow:hidden;background:#fff}'
            'svg{position:absolute;left:0;top:0}</style>%s' % (SIZE, SIZE, svg))

    with tempfile.TemporaryDirectory() as tmp:
        page = os.path.join(tmp, "icon.html")
        shot = os.path.join(tmp, "icon.png")
        io.open(page, "w", encoding="utf-8").write(html)
        subprocess.run([CHROME, "--headless", "--disable-gpu", "--hide-scrollbars",
                        "--screenshot=" + shot, "--window-size=%d,%d" % (SIZE, SIZE),
                        "file://" + page], check=True, capture_output=True)
        data = open(shot, "rb").read()

    w, h, _depth, colour_type = struct.unpack(">IIBB", data[16:26])
    if (w, h) != (SIZE, SIZE):
        sys.exit("rendered %dx%d, not %dx%d" % (w, h, SIZE, SIZE))
    if colour_type != 2:
        sys.exit("icon has an alpha channel (PNG colour type %d); the App Store "
                 "rejects that for the 1024 icon" % colour_type)
    with open(OUT, "wb") as fh:
        fh.write(data)
    print("wrote %s (%dx%d, RGB, no alpha)" % (os.path.relpath(OUT, ROOT), w, h))


if __name__ == "__main__":
    main()
