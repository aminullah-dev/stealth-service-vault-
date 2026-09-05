#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Rebuild the Play Store feature graphic (1024x500).

    python3 play-store/generate_feature_graphic.py

Replaces the cairosvg + PIL path in generate_assets.py for this one asset.
That version needed two native dependencies that are not installed anywhere in
this project, and it drew Persian and Pashto with DejaVu — a font with no
Arabic-script shaping worth the name. This renders the real thing: the app's own
Vazirmatn, the app's own launcher vector, headless Chromium for layout.

Nothing is downloaded and nothing is hardcoded twice. The fonts come from
app/src/main/res/font/ and the flower from app/src/main/res/drawable/, so the
banner cannot drift from the app it advertises.

CITIES is the line this exists to keep honest. It said "kabul" alone for months
after the app opened in Herat, Mazar-e-Sharif and Jalalabad, so a woman in Herat
read the store page and concluded it was not for her. When a city is added to
Areas.kt, add it here.
"""

import base64
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "play-store")
RES = os.path.join(ROOT, "app", "src", "main", "res")

# ── The live city list. Keep in step with app/.../util/Areas.kt ──────────────
CITIES = "کابل · هرات · مزارشریف · جلال‌آباد"

# ── Brand palette (app/src/main/java/com/safebeauty/app/ui/theme/Color.kt) ───
DEEP_ROSE = "#8B3A47"
ROSE = "#B76E79"
GOLD = "#D4A853"
CREAM = "#FFF8F0"

CHROME_CANDIDATES = [
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "/Applications/Chromium.app/Contents/MacOS/Chromium",
    "/usr/bin/google-chrome",
    "/usr/bin/chromium",
    "/usr/bin/chromium-browser",
]

A = "{http://schemas.android.com/apk/res/android}"
AAPT = "{http://schemas.android.com/aapt}"


def argb(colour):
    """Android writes #AARRGGBB; SVG reads eight hex digits as #RRGGBBAA.

    Left alone, the icon's translucent cream shield renders cyan-green — the
    alpha byte is taken for red. Returns (colour, opacity or None).
    """
    if not colour or not colour.startswith("#"):
        return colour, None
    h = colour[1:]
    if len(h) == 8:
        return "#" + h[2:], round(int(h[:2], 16) / 255, 4)
    if len(h) == 4:                                    # #ARGB
        return "#" + "".join(c * 2 for c in h[1:]), round(int(h[0] * 2, 16) / 255, 4)
    return colour, None


def vector_to_svg_parts(path, gradient_id=0):
    """One Android VectorDrawable -> (defs, body, next gradient id)."""
    root = ET.parse(path).getroot()
    defs, body = [], []
    for el in root.iter():
        if el.tag.split("}")[-1] != "path":
            continue
        d = el.get(A + "pathData")
        if not d:
            continue
        attrs = []
        gradient = None
        for child in el:
            if child.tag == AAPT + "attr" and child.get("name") == "android:fillColor":
                gradient = child[0]
        if gradient is not None:
            gradient_id += 1
            name = "g%d" % gradient_id
            stops = [(0.0, gradient.get(A + "startColor")),
                     (0.5, gradient.get(A + "centerColor")),
                     (1.0, gradient.get(A + "endColor"))]
            rendered = []
            for offset, colour in stops:
                if not colour:
                    continue
                col, op = argb(colour)
                rendered.append(
                    '<stop offset="%s" stop-color="%s"%s/>'
                    % (offset, col, ' stop-opacity="%s"' % op if op is not None else "")
                )
            defs.append(
                '<radialGradient id="%s" gradientUnits="userSpaceOnUse" cx="%s" cy="%s" r="%s">%s</radialGradient>'
                % (name, gradient.get(A + "centerX"), gradient.get(A + "centerY"),
                   gradient.get(A + "gradientRadius"), "".join(rendered))
            )
            attrs.append('fill="url(#%s)"' % name)
        else:
            col, op = argb(el.get(A + "fillColor") or "none")
            attrs.append('fill="%s"' % col)
            if op is not None:
                attrs.append('fill-opacity="%s"' % op)
        stroke, stroke_op = argb(el.get(A + "strokeColor"))
        if stroke:
            attrs.append('stroke="%s"' % stroke)
            if stroke_op is not None:
                attrs.append('stroke-opacity="%s"' % stroke_op)
        for vd, svg in (("fillAlpha", "fill-opacity"), ("strokeWidth", "stroke-width"),
                        ("strokeAlpha", "stroke-opacity"), ("strokeLineCap", "stroke-linecap"),
                        ("strokeLineJoin", "stroke-linejoin")):
            value = el.get(A + vd)
            if value:
                attrs.append('%s="%s"' % (svg, value))
        body.append('<path d="%s" %s/>' % (d, " ".join(attrs)))
    return defs, body, gradient_id


def bloom_svg():
    """The flower alone, no launcher background — it sits on its own card here."""
    defs, body, _ = vector_to_svg_parts(os.path.join(RES, "drawable", "ic_launcher_foreground.xml"))
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">'
        "<defs>%s</defs>%s</svg>" % ("".join(defs), "".join(body))
    )


def b64(data):
    return base64.b64encode(data if isinstance(data, bytes) else data.encode("utf-8")).decode()


def font_b64(weight):
    with open(os.path.join(RES, "font", "vazirmatn_%s.ttf" % weight), "rb") as fh:
        return b64(fh.read())


def build_html():
    return """<!doctype html><meta charset="utf-8">
<style>
@font-face {{ font-family:V; src:url(data:font/ttf;base64,{regular}); font-weight:400 }}
@font-face {{ font-family:V; src:url(data:font/ttf;base64,{medium});  font-weight:500 }}
@font-face {{ font-family:V; src:url(data:font/ttf;base64,{bold});    font-weight:700 }}
* {{ margin:0; padding:0; box-sizing:border-box }}
html,body {{ width:1024px; height:500px; overflow:hidden }}
body {{
  font-family:V, system-ui, sans-serif;
  background:radial-gradient(120% 150% at 18% 40%, #FDF1F2 0%, #FBEAEC 45%, #F6DDE2 100%);
  display:flex; align-items:center; position:relative;
}}
.halo {{ position:absolute; border-radius:50%; background:rgba(183,110,121,.055) }}
.h1 {{ width:640px; height:640px; left:-120px; top:-70px }}
.h2 {{ width:430px; height:430px; left:-15px; top:35px; background:rgba(183,110,121,.05) }}
.mark {{
  position:relative; margin-left:96px; width:300px; height:300px; flex:none;
  background:{cream}; border-radius:26px;
  box-shadow:0 18px 44px rgba(139,58,71,.13), 0 2px 6px rgba(139,58,71,.06);
  display:flex; align-items:center; justify-content:center;
}}
.mark img {{ width:270px; height:270px }}
.rule {{ width:1px; height:330px; margin-left:70px; background:linear-gradient(180deg,
        rgba(212,168,83,0) 0%, rgba(212,168,83,.5) 18%, rgba(212,168,83,.5) 82%, rgba(212,168,83,0) 100%) }}
.copy {{ margin-left:52px; direction:rtl; text-align:right; flex:1; padding-right:34px }}
h1 {{ font-size:82px; font-weight:700; color:{deep}; letter-spacing:-1px;
     direction:ltr; text-align:right; line-height:1 }}
.gold {{ width:262px; height:4px; background:{gold}; border-radius:2px; margin:20px 0 0 auto }}
.line {{ font-size:29px; font-weight:500; margin-top:19px; white-space:nowrap; color:{deep} }}
.en {{ direction:ltr; text-align:right; opacity:.86 }}
.fa {{ opacity:.80 }}
.ps {{ opacity:.72 }}
.cities {{ margin-top:30px; font-size:23px; font-weight:500; color:{rose}; white-space:nowrap }}
</style>
<div class="halo h1"></div><div class="halo h2"></div>
<div class="mark"><img src="data:image/svg+xml;base64,{icon}"></div>
<div class="rule"></div>
<div class="copy">
  <h1>SafeBeauty</h1>
  <div class="gold"></div>
  <div class="line en">Private &nbsp;·&nbsp; Discreet &nbsp;·&nbsp; Trusted</div>
  <div class="line fa">خصوصی &nbsp;·&nbsp; محرمانه &nbsp;·&nbsp; قابل اعتماد</div>
  <div class="line ps">شخصي &nbsp;·&nbsp; پټ &nbsp;·&nbsp; د اعتماد وړ</div>
  <div class="cities">{cities}</div>
</div>""".format(
        regular=font_b64("regular"), medium=font_b64("medium"), bold=font_b64("bold"),
        icon=b64(bloom_svg()), cities=CITIES,
        cream=CREAM, deep=DEEP_ROSE, gold=GOLD, rose=ROSE,
    )


def find_chrome():
    for path in CHROME_CANDIDATES:
        if os.path.exists(path):
            return path
    sys.exit("No Chrome or Chromium found. Install one, or add its path to CHROME_CANDIDATES.")


def main():
    html_path = os.path.join(OUT, ".feature_graphic.tmp.html")
    png_path = os.path.join(OUT, "feature_graphic.png")
    with open(html_path, "w", encoding="utf-8") as fh:
        fh.write(build_html())
    try:
        if os.path.exists(png_path):
            os.remove(png_path)                       # Chrome will not overwrite
        subprocess.run(
            [find_chrome(), "--headless", "--disable-gpu", "--hide-scrollbars",
             "--screenshot=" + png_path, "--window-size=1024,500",
             "file://" + html_path],
            check=True, capture_output=True,
        )
    finally:
        os.remove(html_path)
    if not os.path.exists(png_path):
        sys.exit("Chrome ran but wrote no file.")
    with open(png_path, "rb") as fh:
        head = fh.read(24)
    width = int.from_bytes(head[16:20], "big")
    height = int.from_bytes(head[20:24], "big")
    if (width, height) != (1024, 500):
        sys.exit("Wrong size: %dx%d. Play requires exactly 1024x500." % (width, height))
    print("feature_graphic.png  %dx%d  ·  %s" % (width, height, CITIES))


if __name__ == "__main__":
    main()
