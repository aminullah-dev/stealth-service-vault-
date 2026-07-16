"""
Generates Play Store assets for SafeBeauty:
  - icon_512.png        (512×512 – hi-res icon for Play Console store listing)
  - feature_graphic.png (1024×500 – feature graphic for Play Console)

Android vector colours are #AARRGGBB; SVG uses #RRGGBBAA or rgba().
All 8-digit Android colours below are converted manually.
"""

import io, math
import cairosvg
from PIL import Image, ImageDraw, ImageFilter, ImageFont
import os

OUT = os.path.dirname(os.path.abspath(__file__))

# ── Android ARGB → SVG rgba() helper ────────────────────────────────────────
def argb(h):
    """Convert Android '#AARRGGBB' string to CSS 'rgba(R,G,B,alpha)' string."""
    h = h.lstrip("#")
    a = int(h[0:2], 16) / 255
    r = int(h[2:4], 16)
    g = int(h[4:6], 16)
    b = int(h[6:8], 16)
    return f"rgba({r},{g},{b},{a:.3f})"

# Pre-computed colours (readable names)
SHIELD_FILL   = argb("22B76E79")   # rgba(183,110,121,0.133) – faint rose
SHIELD_STROKE = argb("33D4A853")   # rgba(212,168,83,0.200)  – faint gold
CAL_FILL_LO   = argb("1A8B3A47")   # rgba(139,58,71,0.102)
CAL_FILL_HI   = argb("338B3A47")   # rgba(139,58,71,0.200)

# ── SVG strings ──────────────────────────────────────────────────────────────

BACKGROUND_SVG = """\
<svg xmlns="http://www.w3.org/2000/svg"
     width="{size}" height="{size}" viewBox="0 0 108 108">
  <defs>
    <radialGradient id="bg" cx="50%" cy="44%" r="72%">
      <stop offset="0%"   stop-color="#FFFDF9"/>
      <stop offset="40%"  stop-color="#FBEFEA"/>
      <stop offset="100%" stop-color="#F3D8DE"/>
    </radialGradient>
  </defs>
  <rect width="108" height="108" fill="url(#bg)"/>
</svg>"""

FOREGROUND_SVG = f"""\
<svg xmlns="http://www.w3.org/2000/svg"
     width="{{size}}" height="{{size}}" viewBox="0 0 108 108">

  <!-- Faint protective shield (barely visible deep rose) -->
  <path fill="{SHIELD_FILL}" stroke="{SHIELD_STROKE}" stroke-width="0.8"
    d="M38,30 Q38,26 42,26 H66 Q70,26 70,30 V56 Q70,70 54,82 Q38,70 38,56 Z"/>

  <!-- Calendar hint -->
  <path fill="{CAL_FILL_LO}"
    d="M45,33 h18 a1.5,1.5 0 0 1 1.5,1.5 v10 a1.5,1.5 0 0 1 -1.5,1.5
       h-18 a1.5,1.5 0 0 1 -1.5,-1.5 v-10 a1.5,1.5 0 0 1 1.5,-1.5 z"/>
  <path fill="{CAL_FILL_HI}" d="M48,31 h2 v4 h-2 z"/>
  <path fill="{CAL_FILL_HI}" d="M58,31 h2 v4 h-2 z"/>

  <!-- Leaves -->
  <path fill="#C9818E" stroke="#D4A853" stroke-width="0.7"
    d="M51.00,78.00 Q44.94,68.34 34.09,71.84 Q40.15,81.50 51.00,78.00 Z"/>
  <path fill="#C9818E" stroke="#D4A853" stroke-width="0.7"
    d="M57.00,78.00 Q67.85,81.50 73.91,71.84 Q63.06,68.34 57.00,78.00 Z"/>
  <!-- Stem -->
  <path fill="#D4A853" d="M53,66 h2 v16 h-2 z"/>

  <!-- Outer petals (deep rose) -->
  <path fill="#B05968" stroke="#E7C77A" stroke-width="0.8"
    d="M70.00,50.00 C80.73,53.80 75.59,66.21 65.31,61.31
       C70.21,71.59 57.80,76.73 54.00,66.00
       C50.20,76.73 37.79,71.59 42.69,61.31
       C32.41,66.21 27.27,53.80 38.00,50.00
       C27.27,46.20 32.41,33.79 42.69,38.69
       C37.79,28.41 50.20,23.27 54.00,34.00
       C57.80,23.27 70.21,28.41 65.31,38.69
       C75.59,33.79 80.73,46.20 70.00,50.00 Z"/>

  <!-- Middle petals -->
  <path fill="#C2788A" stroke="#E7C77A" stroke-width="0.7"
    d="M63.97,54.65 C70.53,61.26 61.76,68.43 56.58,60.69
       C55.50,69.94 44.43,67.56 47.25,58.68
       C39.35,63.61 34.30,53.46 43.00,50.14
       C34.22,47.03 39.01,36.76 47.03,41.49
       C43.99,32.68 55.01,30.03 56.31,39.25
       C61.30,31.38 70.24,38.33 63.85,45.10
       C73.11,44.10 73.25,55.43 63.97,54.65 Z"/>

  <!-- Inner petals -->
  <path fill="#D69BA8" stroke="#E7C77A" stroke-width="0.6"
    d="M60.89,51.22 C67.09,54.97 61.17,62.03 56.39,56.58
       C56.24,63.82 47.17,62.22 49.50,55.36
       C43.15,58.85 40.00,50.20 47.11,48.78
       C40.91,45.03 46.83,37.97 51.61,43.42
       C51.76,36.18 60.83,37.78 58.50,44.64
       C64.85,41.15 68.00,49.80 60.89,51.22 Z"/>

  <!-- Core -->
  <path fill="#EAC4CC" stroke="#E7C77A" stroke-width="0.5"
    d="M57.50,50.00 C61.80,51.79 58.12,56.86 55.08,53.33
       C54.70,57.97 48.75,56.03 51.17,52.06
       C46.64,53.13 46.64,46.87 51.17,47.94
       C48.75,43.97 54.70,42.03 55.08,46.67
       C58.12,43.14 61.80,48.21 57.50,50.00 Z"/>

  <!-- Centre dot (warm gold) -->
  <circle fill="#D4A853" cx="54" cy="50" r="2"/>
</svg>"""


def render_svg(svg_str: str, size: int) -> Image.Image:
    png = cairosvg.svg2png(
        bytestring=svg_str.format(size=size).encode(),
        output_width=size, output_height=size
    )
    return Image.open(io.BytesIO(png)).convert("RGBA")


# ── 512×512 hi-res icon ──────────────────────────────────────────────────────

def make_icon(size: int = 512) -> Image.Image:
    bg = render_svg(BACKGROUND_SVG, size)
    fg = render_svg(FOREGROUND_SVG, size)
    return Image.alpha_composite(bg, fg)


icon = make_icon(512)
icon.save(os.path.join(OUT, "icon_512.png"), "PNG")
print("✅  icon_512.png  (512×512)")


# ── 1024×500 feature graphic ─────────────────────────────────────────────────

def hex3(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))

def make_feature(w=1024, h=500) -> Image.Image:
    # Gradient background: cream → blush
    img = Image.new("RGBA", (w, h))
    c1, c2 = hex3("#FFFDF9"), hex3("#F0CDD6")
    for y in range(h):
        t = y / (h - 1)
        row_c = tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))
        ImageDraw.Draw(img).line([(0, y), (w, y)], fill=(*row_c, 255))

    # Soft large circle behind the icon (left quadrant)
    for r, a in [(340, 15), (250, 22), (170, 30)]:
        ov = Image.new("RGBA", (w, h), (0,0,0,0))
        ImageDraw.Draw(ov).ellipse(
            [w//4 - r, h//2 - r, w//4 + r, h//2 + r],
            fill=(176, 89, 104, a)
        )
        img = Image.alpha_composite(img, ov)

    # Subtle vertical line divider
    div_x = w // 2 - 10
    for y in range(60, h - 60):
        alpha = int(60 * math.sin(math.pi * (y - 60) / (h - 120)))
        ov = Image.new("RGBA", (w, h), (0,0,0,0))
        ImageDraw.Draw(ov).line([(div_x, y), (div_x, y)], fill=(212, 168, 83, alpha))
        img = Image.alpha_composite(img, ov)
    # Draw the full line at once (faster)
    line_ov = Image.new("RGBA", (w, h), (0,0,0,0))
    ImageDraw.Draw(line_ov).line([(div_x, 60), (div_x, h-60)], fill=(212, 168, 83, 60), width=1)
    img = Image.alpha_composite(img, line_ov)

    # Icon centred in the left half
    icon_sz = 300
    small_icon = make_icon(icon_sz)
    ix = w // 4 - icon_sz // 2
    iy = (h - icon_sz) // 2

    # Drop shadow
    sh_ov = Image.new("RGBA", (w, h), (0,0,0,0))
    ImageDraw.Draw(sh_ov).ellipse(
        [ix + 20, iy + icon_sz - 16, ix + icon_sz - 20, iy + icon_sz + 16],
        fill=(139, 58, 71, 45)
    )
    img = Image.alpha_composite(img, sh_ov.filter(ImageFilter.GaussianBlur(14)))
    img.paste(small_icon, (ix, iy), small_icon)

    # ── Text (right half) ────────────────────────────────────────────────────
    draw = ImageDraw.Draw(img)
    tx = div_x + 48

    def font(size, bold=False):
        paths = [
            f"/usr/share/fonts/truetype/dejavu/DejaVuSans{'-Bold' if bold else ''}.ttf",
            f"/usr/share/fonts/truetype/liberation/LiberationSans{'-Bold' if bold else '-Regular'}.ttf",
        ]
        for p in paths:
            try: return ImageFont.truetype(p, size)
            except: pass
        return ImageFont.load_default()

    DR = hex3("#8B3A47")   # deep rose
    RG = hex3("#C2788A")   # rose-gold muted
    GO = (212, 168, 83)    # warm gold
    GR = (110, 75, 85)     # warm grey

    # App name
    draw.text((tx, 110), "SafeBeauty", fill=(*DR, 255), font=font(70, bold=True))

    # Gold rule
    draw.rectangle([tx, 202, tx + 340, 205], fill=(*GO, 200))

    # EN tagline
    draw.text((tx, 218), "Private  ·  Discreet  ·  Trusted", fill=(*GR, 220), font=font(26))

    # Dari
    draw.text((tx, 262), "خصوصی  ·  محرمانه  ·  قابل اعتماد", fill=(*GR, 190), font=font(26))

    # Pashto
    draw.text((tx, 306), "شخصي  ·  پټ  ·  د اعتماد وړ", fill=(*GR, 170), font=font(26))

    # Short gold rule
    draw.rectangle([tx, 356, tx + 220, 358], fill=(*GO, 130))

    # Category line
    draw.text((tx, 368), "Beauty booking · كابل · کابل", fill=(*RG, 180), font=font(21))

    return img

feat = make_feature()
feat.convert("RGB").save(os.path.join(OUT, "feature_graphic.png"), "PNG")
print("✅  feature_graphic.png  (1024×500)")
print("\nAll Play Store assets ready in: play-store/")
