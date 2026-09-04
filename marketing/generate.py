#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Render a social post or story from a content spec, in every language it defines.

    python3 marketing/generate.py marketing/content/launch-v2.json
    python3 marketing/generate.py --all

One spec in, one image per language out. The colour variant is decided by the
language and never by the caller, because CLAUDE.md fixes it: Dari gets the
light (cream) treatment, Pashto gets the dark (deep-rose) one, and the same
message is never shipped in both colourways for the same language. English —
which the project rule does not cover because it did not exist as an audience
when the rule was written — follows Dari, since the English audience is
partners and investors reading on a bright screen.

Nothing is downloaded. The typeface comes out of app/src/main/res/font/ and the
bloom out of app/src/main/res/drawable/, the same way the Play feature graphic
does, so a post cannot drift from the app it advertises.

Sizes are the ones Instagram actually wants and are asserted after rendering,
because an off-size image is rejected at upload with a message that does not
say which dimension was wrong.
"""

import base64
import io
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
CONTENT = os.path.join(ROOT, "marketing", "content")
OUT = os.path.join(ROOT, "marketing", "out")

SIZES = {"post": (1080, 1350), "story": (1080, 1920)}

# Which colourway each language ships in. Not a caller decision — see the
# module docstring.
VARIANT = {"fa": "light", "ps": "dark", "en": "light"}
DIR = {"fa": "rtl", "ps": "rtl", "en": "ltr"}
LANG_NAME = {"fa": "Dari", "ps": "Pashto", "en": "English"}

# app/src/main/java/com/safebeauty/app/ui/theme/Color.kt — RoseLightPalette
DEEP_ROSE = "#8B3A47"
DEEPER_ROSE = "#7A2F3D"
ROSE_GOLD = "#B76E79"
ROSE_PETAL = "#EBA9C0"
GOLD = "#D4A853"
CREAM = "#FFF7FB"
BLUSH = "#F9CBDA"

A = "{http://schemas.android.com/apk/res/android}"
AAPT = "{http://schemas.android.com/aapt}"

CHROME_CANDIDATES = [
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "/Applications/Chromium.app/Contents/MacOS/Chromium",
    "/usr/bin/google-chrome",
    "/usr/bin/chromium",
]


def argb(colour):
    """Android writes #AARRGGBB; SVG reads eight hex digits as #RRGGBBAA.

    Left alone, the bloom's translucent cream shield renders cyan-green — the
    alpha byte gets taken for red.
    """
    if not colour or not colour.startswith("#"):
        return colour, None
    h = colour[1:]
    if len(h) == 8:
        return "#" + h[2:], round(int(h[:2], 16) / 255, 4)
    return colour, None


def bloom_svg():
    """The flower alone, lifted from the launcher vector."""
    root = ET.parse(os.path.join(RES, "drawable", "ic_launcher_foreground.xml")).getroot()
    body = []
    for el in root.iter():
        if el.tag.split("}")[-1] != "path":
            continue
        d = el.get(A + "pathData")
        if not d:
            continue
        attrs = []
        col, op = argb(el.get(A + "fillColor") or "none")
        attrs.append('fill="%s"' % col)
        if op is not None:
            attrs.append('fill-opacity="%s"' % op)
        stroke, sop = argb(el.get(A + "strokeColor"))
        if stroke:
            attrs.append('stroke="%s"' % stroke)
            if sop is not None:
                attrs.append('stroke-opacity="%s"' % sop)
        for vd, svg in (("fillAlpha", "fill-opacity"), ("strokeWidth", "stroke-width"),
                        ("strokeAlpha", "stroke-opacity")):
            v = el.get(A + vd)
            if v:
                attrs.append('%s="%s"' % (svg, v))
        body.append('<path d="%s" %s/>' % (d, " ".join(attrs)))
    # Cropped to the artwork, not the canvas: an adaptive icon keeps its
    # content inside the middle two thirds of 108x108, so the full viewBox
    # renders the bloom small and adrift inside its card.
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="16 16 76 76">'
            + "".join(body) + "</svg>")


def font_b64(weight):
    with open(os.path.join(RES, "font", "vazirmatn_%s.ttf" % weight), "rb") as fh:
        return base64.b64encode(fh.read()).decode()


def build_html(spec, lang, kind):
    """One language, one size, one colourway."""
    w, h = SIZES[kind]
    variant = VARIANT[lang]
    dark = variant == "dark"
    text = spec["text"][lang]

    ink = CREAM if dark else DEEP_ROSE
    sub_ink = "rgba(255,247,251,.82)" if dark else "rgba(139,58,71,.74)"
    # Built by concatenation, not %-formatting: a CSS gradient is mostly percent
    # signs and every one of them would have to be doubled.
    if dark:
        bg = ("radial-gradient(130% 120% at 22% 12%, " + DEEP_ROSE
              + " 0%, " + DEEPER_ROSE + " 58%, #5C1F2A 100%)")
    else:
        bg = "radial-gradient(130% 120% at 22% 12%, #FFFDFB 0%, #FDEAF3 52%, #F3E6F5 100%)"
    halo = "rgba(255,247,251,.05)" if dark else "rgba(183,110,121,.055)"
    card = "rgba(255,247,251,.10)" if dark else "#FFFFFF"
    card_shadow = ("0 24px 60px rgba(0,0,0,.28)" if dark
                   else "0 24px 60px rgba(139,58,71,.13)")

    # Story is taller: the same content wants more air, not bigger type.
    pad = 96 if kind == "post" else 110
    mark = 190 if kind == "post" else 210
    h1 = 74 if kind == "post" else 80
    body_size = 33 if kind == "post" else 36

    rtl = DIR[lang] == "rtl"
    align = "right" if rtl else "left"

    cta = text.get("cta", "")
    body = text.get("body", "")

    return """<!doctype html><meta charset="utf-8">
<style>
@font-face {{ font-family:V; src:url(data:font/ttf;base64,{reg}); font-weight:400 }}
@font-face {{ font-family:V; src:url(data:font/ttf;base64,{med}); font-weight:500 }}
@font-face {{ font-family:V; src:url(data:font/ttf;base64,{bold}); font-weight:700 }}
*{{margin:0;padding:0;box-sizing:border-box}}
html,body{{width:{w}px;height:{h}px;overflow:hidden}}
body{{
  font-family:V,system-ui,sans-serif; background:{bg}; color:{ink};
  direction:{dir}; padding:{pad}px; position:relative;
  display:flex; flex-direction:column;
}}
.halo{{position:absolute;border-radius:50%;background:{halo};pointer-events:none}}
.h1{{width:{hw}px;height:{hw}px;right:-18%;top:-12%}}
.h2{{width:{hw2}px;height:{hw2}px;left:-22%;bottom:-14%}}
.top{{display:flex;align-items:center;gap:26px;position:relative;flex:none}}
.mark{{width:{mark}px;height:{mark}px;flex:none;border-radius:{markr}px;
  background:{card};box-shadow:{cardshadow};display:flex;align-items:center;
  justify-content:center}}
.mark img{{width:{marki}px;height:{marki}px}}
.brand{{font-size:44px;font-weight:700;letter-spacing:-.5px;direction:ltr;
  text-align:{align}}}
.brandsub{{font-size:24px;font-weight:500;color:{sub};margin-top:6px}}
.mid{{position:relative;flex:1;display:flex;flex-direction:column;
  justify-content:center;padding-bottom:40px}}
h1{{font-size:{h1}px;font-weight:700;line-height:1.28;text-align:{align}}}
.body{{font-size:{bodysz}px;font-weight:400;line-height:1.62;color:{sub};
  margin-top:34px;text-align:{align};max-width:{maxw}px}}
.rule{{width:220px;height:5px;background:{gold};border-radius:3px;margin-top:38px;
  margin-{marginside}:0}}
.foot{{position:relative;text-align:{align};flex:none}}
.cta{{font-size:34px;font-weight:700;color:{ink}}}
.cities{{font-size:26px;font-weight:500;color:{sub};margin-top:16px;white-space:nowrap}}
</style>
<div class="halo h1"></div><div class="halo h2"></div>
<div class="top">
  <div class="mark"><img src="data:image/svg+xml;base64,{icon}"></div>
  <div>
    <div class="brand">SafeBeauty</div>
    <div class="brandsub">{sub_line}</div>
  </div>
</div>
<div class="mid">
  <h1>{headline}</h1>
  {body_block}
  <div class="rule"></div>
</div>
<div class="foot">
  {cta_block}
  <div class="cities">{cities}</div>
</div>""".format(
        reg=font_b64("regular"), med=font_b64("medium"), bold=font_b64("bold"),
        icon=base64.b64encode(bloom_svg().encode()).decode(),
        w=w, h=h, bg=bg, ink=ink, sub=sub_ink, halo=halo, card=card,
        cardshadow=card_shadow, pad=pad, mark=mark, marki=int(mark * 0.86),
        markr=int(mark * 0.24), h1=h1, bodysz=body_size, gold=GOLD,
        dir=DIR[lang], align=align, marginside=("right" if rtl else "left"),
        maxw=w - pad * 2, hw=int(w * 0.85), hw2=int(w * 0.7),
        sub_line=text.get("sub", ""),
        headline=text["headline"],
        body_block=('<div class="body">%s</div>' % body) if body else "",
        cta_block=('<div class="cta">%s</div>' % cta) if cta else "",
        cities=text.get("cities", ""),
    )


def find_chrome():
    for p in CHROME_CANDIDATES:
        if os.path.exists(p):
            return p
    sys.exit("No Chrome or Chromium found. Add its path to CHROME_CANDIDATES.")


def render(spec, path):
    slug = spec["slug"]
    kind = spec.get("type", "post")
    if kind not in SIZES:
        sys.exit("%s: type must be 'post' or 'story', not %r" % (path, kind))
    w, h = SIZES[kind]
    chrome = find_chrome()
    made = []

    for lang in spec["text"]:
        if lang not in VARIANT:
            sys.exit("%s: unknown language %r (expected fa, ps or en)" % (path, lang))
        html_path = os.path.join(OUT, ".%s.%s.tmp.html" % (slug, lang))
        png_path = os.path.join(OUT, "%s_%s_%s.png" % (slug, lang, kind))
        with io.open(html_path, "w", encoding="utf-8") as fh:
            fh.write(build_html(spec, lang, kind))
        try:
            if os.path.exists(png_path):
                os.remove(png_path)          # Chrome will not overwrite
            subprocess.run(
                [chrome, "--headless", "--disable-gpu", "--hide-scrollbars",
                 "--screenshot=" + png_path, "--window-size=%d,%d" % (w, h),
                 "file://" + html_path],
                check=True, capture_output=True,
            )
        finally:
            os.remove(html_path)
        if not os.path.exists(png_path):
            sys.exit("Chrome ran but wrote nothing for %s/%s" % (slug, lang))
        with open(png_path, "rb") as fh:
            head = fh.read(24)
        got = (int.from_bytes(head[16:20], "big"), int.from_bytes(head[20:24], "big"))
        if got != (w, h):
            sys.exit("%s/%s came out %dx%d, not %dx%d — Instagram rejects that."
                     % (slug, lang, got[0], got[1], w, h))
        made.append((lang, png_path, VARIANT[lang]))

    print("%s (%s, %dx%d)" % (slug, kind, w, h))
    for lang, p, variant in made:
        print("   %-8s %-6s %s" % (LANG_NAME[lang], variant, os.path.basename(p)))
    return made


def main():
    args = sys.argv[1:]
    if not args:
        sys.exit(__doc__.strip().split("\n\n")[1])
    if args[0] == "--all":
        # TEMPLATE.json is the shape to copy, not a piece to render. Left in,
        # --all would quietly produce a post whose headline reads "the one thing
        # this post says".
        specs = sorted(os.path.join(CONTENT, f) for f in os.listdir(CONTENT)
                       if f.endswith(".json") and f != "TEMPLATE.json")
        if not specs:
            sys.exit("No specs in marketing/content/")
    else:
        specs = args
    for path in specs:
        with io.open(path, encoding="utf-8") as fh:
            render(json.load(fh), path)


if __name__ == "__main__":
    main()
