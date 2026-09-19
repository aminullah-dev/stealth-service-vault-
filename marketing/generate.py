#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Render a social post or story from a content spec, in every language it defines.

    python3 marketing/generate.py marketing/content/launch-v2.json
    python3 marketing/generate.py --all

A spec with a "slides" array is a carousel: each slide renders as its own
image, numbered in order, in every language the slide defines.

Each slide picks a composition with "layout". The default is "text", the
original one, so every spec written before layouts existed renders exactly as
it did — the whole point of adding the others was more variety, not a new house
style that silently restyles what already shipped.

    text      wordmark, headline, body, gold rule. The house default.
    phone     a drawn app screen inside a phone. Mocked on purpose: the
              marketing rules forbid publishing a screenshot of a real salon or
              a real booking, and there is no seeded demo to photograph.
    quote     an oversized quotation mark over a centred pull-quote.
    stat      one number or short phrase at poster size, with a label under it.
    steps     three or four numbered steps. The "how it works" slide.
    ornament  an 8-fold girih star field, constructed here rather than drawn by
              hand, with the message set over it.
    bloom     the app's own flower, scattered along garlands behind the message.

Which fields a layout reads is documented on each layout_* function below, and
that docstring is the contract a spec author writes against.

Every layout shares one HTML shell, one palette() and one metrics(): the
gradients, the halos, the wordmark and the footer are defined once. Five copies
of the same radial-gradient is exactly how two of these would drift apart over
a couple of months and nobody would notice until the two were side by side in
one carousel.

One spec in, one image per language out. The colour variant is decided by the
language and never by the caller, because CLAUDE.md fixes it: Dari gets the
light (cream) treatment, Pashto gets the dark (deep-rose) one, and the same
message is never shipped in both colourways for the same language. English —
which the project rule does not cover because it did not exist as an audience
when the rule was written — follows Dari, since the English audience is
partners and investors reading on a bright screen.

Nothing is downloaded. The typeface comes out of app/src/main/res/font/ and the
bloom out of app/src/main/res/drawable/, the same way the Play feature graphic
does, so a post cannot drift from the app it advertises. The ornament is
generated from its own geometry for the same reason: an imported pattern is one
more asset nobody can regenerate.

Sizes are the ones Instagram actually wants and are asserted after rendering,
because an off-size image is rejected at upload with a message that does not
say which dimension was wrong.

Everything here is deterministic, including the scattered bloom field, which
seeds its generator from the slug and slide index rather than from the clock.
Re-rendering a spec has to produce byte-identical PNGs or "did this change?"
stops being answerable by hashing the output.
"""

import base64
import hashlib
import io
import json
import math
import os
import random
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

# Dari and Pashto both number with the Eastern Arabic-Indic digits, so a step
# badge that says "2" in a Dari carousel is a tell that the piece was laid out
# in English and translated afterwards.
EASTERN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"

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


def bloom_paths(petals_only=False):
    """The flower's <path> elements alone, lifted from the launcher vector.

    Split out from bloom_svg() because the `bloom` layout stamps the same
    artwork a few dozen times through <use>, and re-parsing (or re-emitting)
    the paths per copy would make the HTML several hundred kilobytes for no
    visible difference.

    `petals_only` drops the launcher's faint shield-and-calendar motif, which
    is written with an alpha byte in its fill. Inside the icon's white card
    that motif is invisible; stamped forty times across a page it turns every
    flower into a flower on a pale square, and the field reads as stickers.
    """
    root = ET.parse(os.path.join(RES, "drawable", "ic_launcher_foreground.xml")).getroot()
    body = []
    for el in root.iter():
        if el.tag.split("}")[-1] != "path":
            continue
        d = el.get(A + "pathData")
        if not d:
            continue
        attrs = []
        fill = el.get(A + "fillColor") or "none"
        if petals_only and len(fill) == 9:
            continue
        col, op = argb(fill)
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
    return "".join(body)


def bloom_svg():
    """The flower alone, lifted from the launcher vector."""
    # Cropped to the artwork, not the canvas: an adaptive icon keeps its
    # content inside the middle two thirds of 108x108, so the full viewBox
    # renders the bloom small and adrift inside its card.
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="16 16 76 76">'
            + bloom_paths() + "</svg>")


def font_b64(weight):
    with open(os.path.join(RES, "font", "vazirmatn_%s.ttf" % weight), "rb") as fh:
        return base64.b64encode(fh.read()).decode()


def palette(lang):
    """Every colour a layout is allowed to use, decided by language alone.

    One function so that a gradient exists once. The alternative — each layout
    carrying its own copy — is how the cream in a "stat" slide ends up half a
    step off the cream in the "text" slide beside it in the same carousel.
    """
    dark = VARIANT[lang] == "dark"
    # Built by concatenation, not %-formatting: a CSS gradient is mostly percent
    # signs and every one of them would have to be doubled.
    if dark:
        bg = ("radial-gradient(130% 120% at 22% 12%, " + DEEP_ROSE
              + " 0%, " + DEEPER_ROSE + " 58%, #5C1F2A 100%)")
    else:
        bg = "radial-gradient(130% 120% at 22% 12%, #FFFDFB 0%, #FDEAF3 52%, #F3E6F5 100%)"
    return {
        "dark": dark,
        "bg": bg,
        "ink": CREAM if dark else DEEP_ROSE,
        "sub": "rgba(255,247,251,.82)" if dark else "rgba(139,58,71,.74)",
        "halo": "rgba(255,247,251,.05)" if dark else "rgba(183,110,121,.055)",
        "card": "rgba(255,247,251,.10)" if dark else "#FFFFFF",
        "cardshadow": ("0 24px 60px rgba(0,0,0,.28)" if dark
                       else "0 24px 60px rgba(139,58,71,.13)"),
        # The mid-tone of the page gradient, as bare rgb components, for the
        # scrims that hold ornament back from the text. A scrim has to be
        # mixable with alpha, so it cannot be a hex constant.
        "scrim": "122,47,61" if dark else "253,234,243",
    }


# How large the wordmark block is, relative to the default, on each layout. On
# every layout but the default the wordmark is a signature rather than an
# opening line: the device, the pattern, the number or the quote is the subject
# and a 190px badge above it competes for the eye.
MARK_SCALE = {"text": 1.0, "phone": 0.62, "quote": 0.58, "stat": 0.55,
              "steps": 0.60, "ornament": 0.64, "bloom": 0.72}


def metrics(kind, layout):
    """The sizes the shared shell needs. Story is taller: the same content
    wants more air, not bigger type."""
    post = kind == "post"
    m = {
        "pad": 96 if post else 110,
        "mark": 190 if post else 210,
        "h1": 74 if post else 80,
        "bodysz": 33 if post else 36,
    }
    if layout == "phone":
        # The device is the subject on these; the words introduce it.
        m["h1"] = 48 if post else 54
        m["bodysz"] = 27 if post else 30
    elif layout == "steps":
        # The headline is a label over the list, not the message itself.
        m["h1"] = 50 if post else 56
        m["bodysz"] = 30 if post else 33
    elif layout in ("ornament", "bloom"):
        m["h1"] = 62 if post else 70
        m["bodysz"] = 31 if post else 34
    m["mark"] = int(m["mark"] * MARK_SCALE[layout])
    return m


def fit_font(s, line_px, lines, base, advance=0.47, floor=0.58):
    """Shrink a font size until `s` plausibly fits in `lines` lines of `line_px`.

    A rough advance-width estimate, not a text measurement — Chrome does the
    real layout and we never see it. It exists because the two layouts that set
    type at poster size, `quote` and `stat`, fail loudly when a Pashto line runs
    a third longer than its Dari counterpart: at 210px there is no room to
    absorb that. Shrinking a little is always better than clipping.
    """
    budget = line_px * lines
    need = len(s) * base * advance
    if need <= budget:
        return base
    return max(int(base * floor), int(base * budget / need))


def localised_number(n, lang):
    s = str(n)
    if DIR[lang] == "rtl":
        return "".join(EASTERN_DIGITS[int(c)] for c in s)
    return s


def need(ctx, field):
    """A layout's required field, missing, named in a way that says which
    slide and which language to go and fix."""
    value = ctx["text"].get(field)
    if not value:
        sys.exit('%s (%s, slide %d): layout "%s" needs text.%s.%s'
                 % (ctx["slug"], LANG_NAME[ctx["lang"]], ctx["index"],
                    ctx["layout"], ctx["lang"], field))
    return value


def step_block(ctx):
    """The "2/4" counter, shared by every layout so a mixed carousel numbers
    consistently."""
    step = ctx["source"].get("step")
    return ('<div class="step">%s</div>' % step) if step else ""


SHELL = """<!doctype html><meta charset="utf-8">
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
.step{{position:absolute;top:0;{stepside}:0;font-size:26px;font-weight:700;color:{sub};
  direction:ltr}}
.art{{position:absolute;inset:0;pointer-events:none;overflow:hidden}}
.art svg{{display:block;width:100%;height:100%}}
{layoutcss}
</style>
<div class="halo h1"></div><div class="halo h2"></div>{art}
<div class="top">
  <div class="mark"><img src="data:image/svg+xml;base64,{icon}"></div>
  <div>
    <div class="brand">SafeBeauty</div>
    <div class="brandsub">{sub_line}</div>
  </div>
</div>
<div class="mid">
{mid}
</div>
<div class="foot">
  {cta_block}
  <div class="cities">{cities}</div>
</div>"""


def build_html(spec, lang, kind, slide=None, index=0):
    """One language, one size, one colourway.

    `slide` is one entry of a carousel's "slides"; without it the spec itself
    is the single slide, which is the shape every spec had before carousels.
    `index` is the slide's position, used only to seed the generative layouts
    so two bloom slides in one carousel do not come out identical.
    """
    w, h = SIZES[kind]
    source = slide if slide is not None else spec
    text = source["text"][lang]
    layout = source.get("layout", "text")
    if layout not in LAYOUTS:
        sys.exit("%s: unknown layout %r (have: %s)"
                 % (spec.get("slug", "?"), layout, ", ".join(sorted(LAYOUTS))))

    pal = palette(lang)
    m = metrics(kind, layout)
    rtl = DIR[lang] == "rtl"
    ctx = dict(pal, **m)
    ctx.update({
        "spec": spec, "source": source, "text": text, "lang": lang, "kind": kind,
        "layout": layout, "index": index, "slug": spec.get("slug", "?"),
        "w": w, "h": h, "rtl": rtl, "align": "right" if rtl else "left",
    })

    layout_css, art, mid = LAYOUTS[layout](ctx)
    cta = text.get("cta", "")

    return SHELL.format(
        reg=font_b64("regular"), med=font_b64("medium"), bold=font_b64("bold"),
        icon=base64.b64encode(bloom_svg().encode()).decode(),
        w=w, h=h, bg=pal["bg"], ink=pal["ink"], sub=pal["sub"], halo=pal["halo"],
        card=pal["card"], cardshadow=pal["cardshadow"],
        pad=m["pad"], mark=m["mark"], marki=int(m["mark"] * 0.86),
        markr=int(m["mark"] * 0.24), h1=m["h1"], bodysz=m["bodysz"], gold=GOLD,
        dir=DIR[lang], align=ctx["align"], marginside=("right" if rtl else "left"),
        maxw=w - m["pad"] * 2, hw=int(w * 0.85), hw2=int(w * 0.7),
        stepside=("left" if rtl else "right"),
        layoutcss=layout_css, art=art, mid=mid,
        sub_line=text.get("sub", ""),
        cta_block=('<div class="cta">%s</div>' % cta) if cta else "",
        cities=text.get("cities", ""),
    )


# --- layouts ---------------------------------------------------------------
#
# Each returns (extra CSS, absolutely-positioned artwork, the contents of .mid).
# Returning "" for the artwork is how a layout says it has none, and produces
# exactly the markup the shell had before artwork existed.


def layout_text(ctx):
    """text (the default) and phone.

    Spec fields, all under text.<lang>:
        sub        small line under the wordmark          optional
        headline   the one thing the slide says           REQUIRED
        body       two sentences at most                  optional
        cta        what she does next                     optional
        cities     the footer line                        optional
    Slide fields: "step" ("2/4"), and for layout "phone", "screen".<lang> with
    title / search / clock / chips[] / rows[{initial,name,meta,price}] / action.
    """
    text = ctx["text"]
    phone = ctx["layout"] == "phone"
    body = text.get("body", "")
    return (
        phone_css(ctx["kind"], ctx["dark"], ctx["lang"]) if phone else "",
        "",
        '  %s\n  <h1>%s</h1>\n  %s\n  %s\n  <div class="rule"></div>' % (
            step_block(ctx),
            text["headline"],
            ('<div class="body">%s</div>' % body) if body else "",
            (phone_block(ctx["source"].get("screen", {}).get(ctx["lang"], {}),
                         ctx["lang"]) if phone else ""),
        ),
    )


def phone_block(screen, lang):
    """The phone, holding either a real capture or a drawn screen.

    A capture is worth more than a drawing and was not available until today:
    the marketing rules forbid publishing a real salon or a real booking, so
    until marketing/demo/ seeded a demo world in the staging project there was
    nothing real to photograph. A spec that names a `shot` gets the capture; one
    that describes rows still gets the drawing.
    """
    shot = screen.get("shot")
    if not shot:
        return phone_svg_screen(screen, lang)
    full = shot if os.path.isabs(shot) else os.path.join(ROOT, shot)
    if not os.path.exists(full):
        sys.exit("phone shot not found: %s" % full)
    with open(full, "rb") as fh:
        data = base64.b64encode(fh.read()).decode()
    # Filled by height and cropped horizontally: the capture is 1080x2400 and
    # the frame is not, and a letterboxed phone inside a phone reads as a
    # mistake rather than as a device.
    return ('<div class="phone"><div class="screen shotscreen">'
            '<img class="shot" src="data:image/png;base64,%s"></div></div>' % data)


def phone_svg_screen(screen, lang):
    """The app screen that sits inside the phone on a `phone` slide.

    Every name, price and time here comes from the spec and is invented. The
    marketing rules are explicit that a real salon may not be named and a real
    booking may not be shown, and staging holds no seeded demo to photograph,
    so this is a drawing of the product rather than a capture of it — built
    from the same palette and the same typeface as the app so it cannot look
    like a different product.
    """
    rows = ""
    for row in screen.get("rows", []):
        meta = row.get("meta", "")
        price = row.get("price", "")
        rows += (
            '<div class="card">'
            '<div class="avatar">%s</div>'
            '<div class="cardtext">'
            '<div class="cname">%s</div>'
            '<div class="cmeta">%s</div>'
            '</div>'
            '<div class="cprice">%s</div>'
            '</div>'
        ) % (row.get("initial", "?"), row.get("name", ""), meta, price)

    chips = "".join(
        '<div class="chip%s">%s</div>' % (" on" if i == 0 else "", c)
        for i, c in enumerate(screen.get("chips", []))
    )
    return """
<div class="phone">
  <div class="screen">
    <div class="statusbar"><span>%s</span><span>▮ ▮ ▮</span></div>
    <div class="apphead">%s</div>
    <div class="search">%s</div>
    <div class="chips">%s</div>
    %s
    <div class="cta-pill">%s</div>
  </div>
</div>""" % (
        screen.get("clock", "9:41"),
        screen.get("title", ""),
        screen.get("search", ""),
        chips,
        rows,
        screen.get("action", ""),
    )


PHONE_CSS = """
.phone{{position:relative;margin:0 auto;width:{pw}px;height:{ph}px;border-radius:{pr}px;
  background:{frame};padding:{bez}px;box-shadow:0 30px 70px rgba(0,0,0,.30)}}
.screen{{width:100%;height:100%;border-radius:{sr}px;background:{screenbg};overflow:hidden;
  display:flex;flex-direction:column;padding:22px 20px;gap:14px;direction:{dir}}}
.shotscreen{{padding:0;display:block}}
.shot{{width:100%;height:100%;object-fit:cover;object-position:top center;display:block}}
.statusbar{{display:flex;justify-content:space-between;font-size:17px;font-weight:500;
  color:{muted};direction:ltr}}
.apphead{{font-size:34px;font-weight:700;color:{ink};text-align:{align}}}
.search{{font-size:21px;font-weight:400;color:{muted};background:{field};
  border-radius:16px;padding:14px 18px;text-align:{align}}}
.chips{{display:flex;gap:9px;flex-wrap:nowrap;overflow:hidden}}
.chip{{font-size:19px;font-weight:500;color:{ink};background:{field};
  border-radius:999px;padding:9px 16px;white-space:nowrap}}
.chip.on{{background:{accent};color:#fff}}
.card{{display:flex;align-items:center;gap:14px;background:{cardbg};border-radius:20px;
  padding:16px 18px;box-shadow:0 6px 18px rgba(139,58,71,.08)}}
.avatar{{width:64px;height:64px;border-radius:18px;flex:none;display:flex;
  align-items:center;justify-content:center;font-size:28px;font-weight:700;color:#fff;
  background:linear-gradient(135deg,{petal},{deep})}}
.cardtext{{flex:1;min-width:0;text-align:{align}}}
.cname{{font-size:24px;font-weight:700;color:{ink}}}
.cmeta{{font-size:19px;font-weight:400;color:{muted};margin-top:4px}}
.cprice{{font-size:21px;font-weight:700;color:{accent};white-space:nowrap;direction:ltr}}
.cta-pill{{margin-top:auto;text-align:center;font-size:24px;font-weight:700;color:#fff;
  background:linear-gradient(135deg,{petal},{deep});border-radius:18px;padding:16px}}
"""


def phone_css(kind, dark, lang):
    w, h = SIZES[kind]
    ph = 1020 if kind == "post" else 1240
    pw = int(ph * 0.49)
    return PHONE_CSS.format(
        pw=pw, ph=ph, pr=int(pw * 0.11), sr=int(pw * 0.095), bez=14,
        frame="rgba(255,247,251,.22)" if dark else "#FFFFFF",
        screenbg="#2A1119" if dark else CREAM,
        cardbg="rgba(255,247,251,.10)" if dark else "#FFFFFF",
        field="rgba(255,247,251,.12)" if dark else "#FDEEF3",
        ink=CREAM if dark else DEEP_ROSE,
        muted="rgba(255,247,251,.66)" if dark else "rgba(139,58,71,.62)",
        accent=ROSE_PETAL if dark else ROSE_GOLD,
        petal=ROSE_PETAL, deep=DEEPER_ROSE,
        dir=DIR[lang], align=("right" if DIR[lang] == "rtl" else "left"),
    )


QUOTE_CSS = """
.qwrap{{display:flex;flex-direction:column;align-items:center}}
.qmark{{font-size:{qm}px;line-height:.66;font-weight:700;color:{gold};opacity:{qo};
  direction:ltr;align-self:flex-start;margin-bottom:{qmb}px}}
.quote{{font-size:{qs}px;font-weight:500;line-height:1.5;text-align:center;
  max-width:{qw}px}}
.qrule{{width:92px;height:4px;background:{gold};border-radius:2px;margin:44px 0 28px}}
.qattr{{font-size:{qa}px;font-weight:500;color:{sub};text-align:center;
  max-width:{qw}px}}
"""


def layout_quote(ctx):
    """quote — a centred pull-quote under an oversized opening mark.

    Spec fields, under text.<lang>:
        quote        the sentence being quoted             REQUIRED
        attribution  who said it, small, underneath        optional
        sub / cta / cities as for `text`.
    Slide fields: "step".

    Set in medium, not bold, with wide leading and a short measure. A quote in
    the headline's weight is just a headline in bigger type; the composition
    has to say "someone said this" before a single word is read, which is what
    the oversized mark and the air around the text are doing.
    """
    post = ctx["kind"] == "post"
    quote = need(ctx, "quote")
    attribution = ctx["text"].get("attribution", "")
    measure = ctx["w"] - ctx["pad"] * 2 - 84

    css = QUOTE_CSS.format(
        qm=196 if post else 224,
        # The mark is a piece of furniture, not punctuation to be read; at full
        # strength it out-shouts the sentence it introduces.
        qo=".42" if ctx["dark"] else ".38",
        qmb=2 if post else 6,
        qs=fit_font(quote, measure, 4, 56 if post else 62),
        qa=27 if post else 30,
        qw=measure, gold=GOLD, sub=ctx["sub"],
    )
    # direction:ltr on the mark, deliberately: U+00AB is bidi-mirrored, so in an
    # RTL run the browser would flip it to » and the quote would open with a
    # closing mark.
    mark = "«" if ctx["rtl"] else "“"
    mid = (
        '  %s\n'
        '  <div class="qwrap">\n'
        '    <div class="qmark">%s</div>\n'
        '    <div class="quote">%s</div>\n'
        '    <div class="qrule"></div>\n'
        '    %s\n'
        '  </div>'
    ) % (step_block(ctx), mark, quote,
         ('<div class="qattr">%s</div>' % attribution) if attribution else "")
    return css, "", mid


STAT_CSS = """
.swrap{{text-align:center}}
/* The padding is not spacing: background-clip:text clips the gradient to the
   element's box, so at line-height 1.05 any part of a glyph reaching past the
   line box — most Arabic-script descenders do — would be filled with nothing
   at all and simply vanish. */
.stat{{font-size:{ss}px;font-weight:700;line-height:1.05;padding:.1em 0;
  background:linear-gradient(135deg,{g1},{g2} 34%,{g3});
  -webkit-background-clip:text;background-clip:text;color:transparent}}
.srule{{width:96px;height:5px;background:{gold};border-radius:3px;margin:{srm}px auto 0}}
.slabel{{font-size:{sl}px;font-weight:500;color:{ink};margin-top:32px;line-height:1.35}}
.snote{{font-size:{bodysz}px;font-weight:400;color:{sub};line-height:1.6;
  margin:22px auto 0;max-width:{snw}px}}
"""


def layout_stat(ctx):
    """stat — one number or short phrase at poster size.

    Spec fields, under text.<lang>:
        stat    the number or short phrase, e.g. «۲ دقیقه»   REQUIRED
        label   the short line under it                      REQUIRED
        body    one supporting sentence                      optional
        sub / cta / cities as for `text`.
    Slide fields: "step".

    The whole layout is one typographic gesture, so it is built to be given
    something genuinely short. fit_font() will rescue a long line rather than
    let it clip, but a `stat` of a dozen words is the wrong layout, not a
    sizing problem.
    """
    post = ctx["kind"] == "post"
    stat = need(ctx, "stat")
    label = need(ctx, "label")
    note = ctx["text"].get("body", "")
    measure = ctx["w"] - ctx["pad"] * 2

    css = STAT_CSS.format(
        # Fitted to one line: at this size a second line is never a wrap, it is
        # an accident.
        ss=fit_font(stat, measure, 1, 208 if post else 238, advance=0.55),
        sl=fit_font(label, measure, 2, 42 if post else 46, advance=0.50),
        srm=44 if post else 52,
        snw=int(measure * 0.80), bodysz=ctx["bodysz"], gold=GOLD,
        ink=ctx["ink"], sub=ctx["sub"],
        # The signature gradient on the light side; on the dark side it runs
        # the other way, cream into gold, because deep rose on deep rose is a
        # number nobody can read.
        g1=CREAM if ctx["dark"] else ROSE_PETAL,
        g2=BLUSH if ctx["dark"] else ROSE_GOLD,
        g3=GOLD if ctx["dark"] else DEEPER_ROSE,
    )
    mid = (
        '  %s\n'
        '  <div class="swrap">\n'
        '    <div class="stat">%s</div>\n'
        '    <div class="srule"></div>\n'
        '    <div class="slabel">%s</div>\n'
        '    %s\n'
        '  </div>'
    ) % (step_block(ctx), stat, label,
         ('<div class="snote">%s</div>' % note) if note else "")
    return css, "", mid


STEPS_CSS = """
.steps{{display:flex;flex-direction:column;gap:{sg}px;margin-top:{smt}px}}
.srow{{display:flex;align-items:center;gap:26px}}
.sbadge{{position:relative;width:{sb}px;height:{sb}px;border-radius:50%;flex:none;
  display:flex;align-items:center;justify-content:center;font-size:{sbf}px;
  font-weight:700;color:#fff;background:linear-gradient(135deg,{petal},{deep});
  box-shadow:0 10px 26px rgba(139,58,71,.24)}}
.srow:not(:last-child) .sbadge::after{{content:"";position:absolute;top:100%;left:50%;
  width:3px;height:{sc}px;margin-left:-1.5px;opacity:.5;
  background:linear-gradient(180deg,{gold},transparent)}}
.stext{{flex:1;min-width:0;font-size:{st}px;font-weight:500;line-height:1.42;
  text-align:{align}}}
"""


def layout_steps(ctx):
    """steps — three or four numbered steps down the canvas.

    Spec fields, under text.<lang>:
        headline   a label over the list                  optional
        steps      an array of 2-5 short lines            REQUIRED
        cta / cities / sub as for `text`.
    Slide fields: "step".

    The badges are numbered here rather than in the spec, in the digits of the
    language being rendered. A Dari carousel whose steps count 1, 2, 3 is the
    tell that the piece was laid out in English first.
    """
    post = ctx["kind"] == "post"
    steps = ctx["text"].get("steps")
    if not steps or not isinstance(steps, list):
        sys.exit('%s (%s, slide %d): layout "steps" needs text.%s.steps as a list'
                 % (ctx["slug"], LANG_NAME[ctx["lang"]], ctx["index"], ctx["lang"]))
    headline = ctx["text"].get("headline", "")
    gap = (40 if post else 50) if len(steps) > 3 else (52 if post else 62)

    css = STEPS_CSS.format(
        # The connector runs a little past the gap and tucks under the next
        # badge, which paints over it: sized to the gap exactly it would fall
        # short as soon as one step's text wrapped to a second line.
        sg=gap, sc=gap + 34, smt=(44 if headline else 0),
        sb=88 if post else 98, sbf=40 if post else 44,
        st=34 if post else 38,
        petal=ROSE_PETAL, gold=GOLD, align=ctx["align"],
        # Deep rose is the dark page's own background, so a badge that fades
        # into it there needs to fade into rose-gold instead.
        deep=ROSE_GOLD if ctx["dark"] else DEEPER_ROSE,
    )
    rows = "".join(
        '    <div class="srow"><div class="sbadge">%s</div>'
        '<div class="stext">%s</div></div>\n'
        % (localised_number(i, ctx["lang"]), line)
        for i, line in enumerate(steps, start=1)
    )
    mid = (
        '  %s\n'
        '  %s\n'
        '  <div class="steps">\n%s  </div>'
    ) % (step_block(ctx),
         ('<h1>%s</h1>' % headline) if headline else "",
         rows)
    return css, "", mid


# --- Islamic geometric ornament --------------------------------------------
#
# The `ornament` layout constructs a real 8-fold girih field rather than
# faking a texture, because a texture is the one thing here that could not be
# regenerated or corrected later — it would just be a picture somebody once
# made.
#
# The star is the outline of the star polygon {8/3} — the khatam, the eight
# point star with 45 degree points that is on half the woodwork in Kabul. It is
# a sixteen-sided figure alternating a point at radius r with an inner vertex at
# r*cos(3pi/8)/cos(pi/4), which is 0.541r. The shallower {8/2} (two overlapping
# squares) is also an eight-fold star and was tried first: its notches are only
# a quarter of the radius deep, so at any band width that is visible from a
# phone the star fills in and the field reads as a maze.
#
# Tiled on a square lattice of pitch 2r, each star's four axis tips land exactly
# on its neighbours' axis tips, and the space left between four stars is the
# classic cross.
#
# What makes it read as strapwork rather than as outlines is that the bands
# weave. At every tip two bands cross: cut the star outline at its four axis
# tips and each of the four arcs ends at one tip and starts at the next, so
# "an arc passes over at the tip it ends on and under at the tip it starts on"
# alternates correctly along every band, and — the part that matters — is the
# same rule in every star. One star is therefore woven once and stamped with
# <use>, which keeps a full-bleed field to a couple of hundred elements.


def girih_star(r):
    """One 8-fold star as four strapwork arcs, each running from one axis tip
    to the next."""
    inner = r * math.cos(math.radians(67.5)) / math.cos(math.radians(45.0))
    pts = []
    for k in range(16):
        a = math.radians(k * 22.5)
        rad = r if k % 2 == 0 else inner
        pts.append((rad * math.cos(a), rad * math.sin(a)))
    return [[pts[(q * 4 + i) % 16] for i in range(5)] for q in range(4)]


def _trim(pts, d):
    """Pull the first point `d` along its edge: how an arc gets out of the way
    of the band crossing over it at the tip where it starts."""
    (x1, y1), (x2, y2) = pts[0], pts[1]
    length = math.hypot(x2 - x1, y2 - y1)
    f = min(d / length, 0.85) if length else 0.0
    return [(x1 + (x2 - x1) * f, y1 + (y2 - y1) * f)] + list(pts[1:])


def girih_svg(w, h, pitch, band_col, core_col):
    """A star-and-cross field covering w x h and bleeding off every edge."""
    band = pitch * 0.050
    # The two bands meet at a tip at 45 degrees, so clearing the one on top
    # costs half a band width over sin 45; a round cap and a gap you can
    # actually see at a phone's size want about one more.
    arcs = [_trim(q, band * 1.75) for q in girih_star(pitch / 2.0)]
    d = " ".join("M" + "L".join("%.2f,%.2f" % p for p in q) for q in arcs)
    star = ('<g id="gs" fill="none" stroke-linecap="round" stroke-linejoin="round">'
            '<path d="%s" stroke="%s" stroke-width="%.2f"/>'
            '<path d="%s" stroke="%s" stroke-width="%.2f"/></g>'
            % (d, band_col, band, d, core_col, band * 0.30))

    nx = int(math.ceil(w / float(pitch))) + 2
    ny = int(math.ceil(h / float(pitch))) + 2
    ox = w / 2.0 - (nx - 1) * pitch / 2.0
    oy = h / 2.0 - (ny - 1) * pitch / 2.0
    uses = "".join(
        '<use href="#gs" x="%.1f" y="%.1f"/>' % (ox + i * pitch, oy + j * pitch)
        for j in range(ny) for i in range(nx))
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d">'
            '<defs>%s</defs>%s</svg>' % (w, h, star, uses))


ORNAMENT_CSS = """
.art{{opacity:{oo}}}
.scrim{{position:absolute;inset:0;pointer-events:none;background:linear-gradient(
  180deg, rgba({scrim},.34) 0%, rgba({scrim},.56) 15%, rgba({scrim},.93) 31%,
  rgba({scrim},.93) 69%, rgba({scrim},.56) 86%, rgba({scrim},.34) 100%)}}
.ornline{{height:2px;background:{gold};opacity:.5;flex:none}}
.ornline.a{{margin-bottom:38px}}
.ornline.b{{margin-top:40px}}
"""


def layout_ornament(ctx):
    """ornament — the message framed inside a generated girih field.

    Spec fields, under text.<lang>: exactly `text`'s — sub, headline (REQUIRED),
    body, cta, cities.
    Slide fields: "step", and optionally "tiles" (how many stars across the
    canvas, default 5; smaller numbers give a bolder, more architectural
    pattern and larger ones a finer one).

    The pattern runs the full bleed and a vertical scrim holds it back across
    the middle, so the band of ornament reads at the top and bottom and the
    message sits in clear air between two gold hairlines. That scrim is the
    whole trick: at a single opacity the pattern is either invisible or it is
    competing with the headline, and on the dark colourway it is competing at a
    lower number than on the light one.
    """
    tiles = ctx["source"].get("tiles", 5)
    pitch = ctx["w"] / float(tiles)
    band = ROSE_PETAL if ctx["dark"] else ROSE_GOLD
    svg = girih_svg(ctx["w"], ctx["h"], pitch, band, GOLD)
    css = ORNAMENT_CSS.format(
        oo=".40" if ctx["dark"] else ".54",
        scrim=ctx["scrim"], gold=GOLD,
    )
    body = ctx["text"].get("body", "")
    mid = (
        '  %s\n'
        '  <div class="ornline a"></div>\n'
        '  <h1>%s</h1>\n'
        '  %s\n'
        '  <div class="ornline b"></div>'
    ) % (step_block(ctx), ctx["text"]["headline"],
         ('<div class="body">%s</div>' % body) if body else "")
    return css, '<div class="art">%s</div><div class="scrim"></div>' % svg, mid


def bloom_field_svg(w, h, seed):
    """The launcher's flower, stamped along two garlands.

    Placed on arcs rather than scattered at random: the same petals dropped
    uniformly read as confetti, where an arc reads as a border somebody
    composed. The randomness is only jitter — size, angle, opacity and a small
    nudge off the curve — and it is seeded from the slug so the same spec
    renders the same field every time.
    """
    rng = random.Random(int(hashlib.sha256(seed.encode()).hexdigest()[:12], 16))
    uses = []

    def stamp(x, y, size, rot, opacity):
        # The artwork's own centre in the 108-unit viewport is about (54, 52),
        # and it is roughly 56 units across, so scale = size / 56.
        uses.append(
            '<use href="#bl" opacity="%.3f" transform="translate(%.1f,%.1f) '
            'rotate(%.1f) scale(%.4f) translate(-54,-52)"/>'
            % (opacity, x, y, rot, size / 56.0))

    # cx, cy, rx, ry, from deg, to deg, count, size, alpha low, alpha high
    garlands = [
        (w * .50, h * .92, w * .68, h * .25, 180, 360, 13, w * .175, .34, .64),
        (w * .50, -h * .07, w * .72, h * .17, 0, 180, 9, w * .100, .18, .34),
    ]
    for cx, cy, rx, ry, a0, a1, n, size, lo, hi in garlands:
        for k in range(n):
            f = k / float(n - 1)
            ang = math.radians(a0 + (a1 - a0) * f)
            x = cx + rx * math.cos(ang) + rng.uniform(-.035, .035) * rx
            y = cy + ry * math.sin(ang) + rng.uniform(-.06, .06) * ry
            # Heaviest at the middle of the sweep so the garland has a centre
            # of gravity instead of being an even stripe of flowers.
            taper = .45 + .55 * math.sin(math.pi * f)
            stamp(x, y, size * taper * rng.uniform(.82, 1.18),
                  math.degrees(ang) + 90 + rng.uniform(-28, 28),
                  rng.uniform(lo, hi))

    # A few small ones off the arcs, faint enough to sit under a headline; they
    # stop the two garlands from looking stamped rather than grown.
    for _ in range(7):
        stamp(rng.uniform(.04, .96) * w, rng.uniform(.08, .92) * h,
              rng.uniform(w * .045, w * .088), rng.uniform(0, 360),
              rng.uniform(.12, .22))

    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d">'
            '<defs><g id="bl">%s</g></defs>%s</svg>'
            % (w, h, bloom_paths(petals_only=True), "".join(uses)))


BLOOM_CSS = """
.art{{filter:{bf}}}
.scrim{{position:absolute;inset:0;pointer-events:none;background:radial-gradient(
  64% 40% at 50% 46%, rgba({scrim},.92) 0%, rgba({scrim},.66) 56%,
  rgba({scrim},0) 100%)}}
"""


def layout_bloom(ctx):
    """bloom — the message over a generated field of the app's own flower.

    Spec fields, under text.<lang>: exactly `text`'s — sub, headline (REQUIRED),
    body, cta, cities.
    Slide fields: "step", and optionally "seed" (any string) to re-roll the
    scatter without touching the copy. Without one the field is seeded from the
    slug and the slide's position, so it is stable across renders and different
    between slides.
    """
    seed = "%s|%d|%s" % (ctx["slug"], ctx["index"], ctx["source"].get("seed", ""))
    svg = bloom_field_svg(ctx["w"], ctx["h"], seed)
    css = BLOOM_CSS.format(
        # The flower's own rose tones sit a shade below the cream page and
        # almost exactly on top of the deep-rose one, so on dark the field
        # disappears entirely unless it is lifted. Per-petal opacity carries
        # the rest; this is only the tone correction.
        bf="brightness(1.55) saturate(.8)" if ctx["dark"] else "saturate(1.1)",
        scrim=ctx["scrim"],
    )
    body = ctx["text"].get("body", "")
    mid = (
        '  %s\n  <h1>%s</h1>\n  %s\n  <div class="rule"></div>'
    ) % (step_block(ctx), ctx["text"]["headline"],
         ('<div class="body">%s</div>' % body) if body else "")
    return css, '<div class="art">%s</div><div class="scrim"></div>' % svg, mid


LAYOUTS = {
    "text": layout_text,
    "phone": layout_text,
    "quote": layout_quote,
    "stat": layout_stat,
    "steps": layout_steps,
    "ornament": layout_ornament,
    "bloom": layout_bloom,
}


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


def render_carousel(spec, path):
    """A carousel: one image per slide per language, numbered in order.

    Numbered with a zero-padded index so an upload dialog listing the folder
    alphabetically offers them in the order they are meant to be swiped —
    slide 10 sorting between 1 and 2 is how a carousel ships scrambled.
    """
    slug = spec["slug"]
    kind = spec.get("type", "post")
    if kind not in SIZES:
        sys.exit("%s: type must be 'post' or 'story', not %r" % (path, kind))
    w, h = SIZES[kind]
    chrome = find_chrome()
    slides = spec["slides"]
    made = []

    for i, slide in enumerate(slides, start=1):
        for lang in slide["text"]:
            if lang not in VARIANT:
                sys.exit("%s: unknown language %r" % (path, lang))
            html_path = os.path.join(OUT, ".%s.%d.%s.tmp.html" % (slug, i, lang))
            png_path = os.path.join(OUT, "%s_%s_%02d_%s.png" % (slug, lang, i, kind))
            with io.open(html_path, "w", encoding="utf-8") as fh:
                fh.write(build_html(spec, lang, kind, slide, i))
            try:
                if os.path.exists(png_path):
                    os.remove(png_path)
                subprocess.run(
                    [chrome, "--headless", "--disable-gpu", "--hide-scrollbars",
                     "--screenshot=" + png_path, "--window-size=%d,%d" % (w, h),
                     "file://" + html_path],
                    check=True, capture_output=True,
                )
            finally:
                os.remove(html_path)
            if not os.path.exists(png_path):
                sys.exit("Chrome wrote nothing for %s slide %d/%s" % (slug, i, lang))
            with open(png_path, "rb") as fh:
                head = fh.read(24)
            got = (int.from_bytes(head[16:20], "big"), int.from_bytes(head[20:24], "big"))
            if got != (w, h):
                sys.exit("%s slide %d/%s came out %dx%d, not %dx%d."
                         % (slug, i, lang, got[0], got[1], w, h))
            made.append((lang, png_path))

    print("%s (carousel, %d slides, %s, %dx%d)" % (slug, len(slides), kind, w, h))
    for lang, p in made:
        print("   %-8s %s" % (LANG_NAME[lang], os.path.basename(p)))
    return made


def main():
    args = sys.argv[1:]
    if not args:
        sys.exit(__doc__.strip().split("\n\n")[1])
    if args[0] == "--all":
        # TEMPLATE.json is the shape to copy, not a piece to render. Left in,
        # --all would quietly produce a post whose headline reads "the one thing
        # this post says". A leading underscore means the same thing for
        # anything else that is not real content — a layout probe, a sketch, a
        # piece someone is still drafting — and is the way to keep a scratch
        # spec in the folder without it turning up in a real run. Named
        # explicitly, such a spec still renders.
        specs = sorted(os.path.join(CONTENT, f) for f in os.listdir(CONTENT)
                       if f.endswith(".json") and f != "TEMPLATE.json"
                       and not f.startswith("_"))
        if not specs:
            sys.exit("No specs in marketing/content/")
    else:
        specs = args
    for path in specs:
        with io.open(path, encoding="utf-8") as fh:
            spec = json.load(fh)
        if spec.get("slides"):
            render_carousel(spec, path)
        else:
            render(spec, path)


if __name__ == "__main__":
    main()
