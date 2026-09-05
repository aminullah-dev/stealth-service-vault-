# SafeBeauty — Branding Reference

Everything below is pulled directly from the codebase (`app/`, `public/`,
`play-store/`), not from memory. File paths are given so any value can be
re-verified against source. Generated 2026-09-01.

---

## 1. Identity

| | |
|---|---|
| App name | **SafeBeauty** (`app/src/main/res/values/strings.xml`) |
| Tagline (EN) | *Private beauty salon booking for women — safe, simple & trusted.* |
| Tagline (FA) | رزرو خصوصی سالن زیبایی برای خانم‌ها — ایمن، ساده و قابل اعتماد. |
| Tagline (PS) | د ښځو لپاره شخصي د سالون بکینګ — خوندي، ساده او باوروړ. |
| One-line pitch | A private booking platform connecting women with trusted, female-only beauty salons. |
| Cities served | Kabul · Herat · Mazar-e-Sharif · Jalalabad |
| Languages | English, دری (Dari), پښتو (Pashto) — full RTL support |
| Version (current) | 2.0.1 · versionCode 16 |

> **Known stale copy:** the Play Store long description and the privacy policy
> both still say "designed for women in Kabul" (singular city). The privacy
> policy was corrected 2026-09-01 (see `public/privacy.html`); the store
> listing text in `play-store/store_listing.md` was not — update it before
> next copying that text into Play Console.

---

## 2. Logo & App Icon

The app icon is a **pure vector adaptive icon** (Android, minSdk 26) — a
layered rose bloom in deep-rose/rose-gold with thin warm-gold petal edges,
over a faint shield + calendar motif (privacy + booking), on a soft
ivory-to-blush radial background.

**Source of truth** (edit these, not the PNGs):
- `app/src/main/res/drawable/ic_launcher_foreground.xml`
- `app/src/main/res/drawable/ic_launcher_background.xml`
- `app/src/main/res/drawable/ic_launcher_monochrome.xml`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`

**Exported assets** (`play-store/logos/`), rendered from the vectors above —
regenerate with `play-store/generate_feature_graphic.py` if the vectors
change:

| File | What it is |
|---|---|
| `safebeauty_icon.svg` | Launcher icon, full vector (background + bloom + rounded-square mask) |
| `safebeauty_mark.svg` | The bloom alone, vector, transparent background |
| `safebeauty_icon_1024.png` / `_512.png` / `_192.png` | Launcher icon, masked, PNG |
| `safebeauty_mark_transparent_1024.png` | The bloom alone, PNG, transparent — for use on any color background |
| `play_store_icon_512.png` | Play Console hi-res icon (512×512, required exact size) |
| `play_feature_graphic_1024x500.png` | Play Console feature graphic (1024×500, required exact size) |

Feature graphic regenerates from `play-store/generate_feature_graphic.py`
(headless-Chromium + the app's own Vazirmatn + the launcher vector — no
external dependencies). Keeps the `CITIES` line in step with `Areas.kt`.

---

## 3. Colour — Primary Brand (Rose)

This is the **default** palette (`AppBrand.ROSE`) — what ships unless a user
switches themes. Source: `app/src/main/java/com/safebeauty/app/ui/theme/Color.kt`
(`RoseLightPalette` / `RoseDarkPalette`).

### Light mode

| Token | Hex | Use |
|---|---|---|
| `deepRose` | `#8B3A47` | Headline ink, primary text-on-light |
| `roseGold` | `#B76E79` | Primary accent, buttons, links |
| `deeperRose` | `#7A2F3D` | Gradient dark stop |
| `blushPink` | `#F9CBDA` | Chips (inactive), soft fills |
| `warmGold` | `#D4A853` | Gold accent (badges, ratings) |
| `elegantCream` | `#FFF7FB` | Page background |
| `dashboardSurface` | `#FDEFF6` | Card / dashboard surface |
| `petalPink` | `#FCE4EF` | Soft gradient stop |
| `lilacMist` | `#E9D5F0` | Soft gradient stop |
| `softLavender` | `#F3E6F7` | Soft gradient stop |
| `rosePetal` | `#EBA9C0` | Gradient light stop |
| `cardBorder` | `#F3D2E0` | Card borders |
| `availableGreen` | `#4CAF50` | "Available" / success state |
| `unavailableGrey` | `#9E9E9E` | "Unavailable" / disabled state |
| `dangerRed` | `#C0392B` | Errors, destructive actions |
| `warningOrange` | `#E67E22` | Warnings |
| `adminPurple` | `#7B6FA0` | Admin-role accent |
| `textStrong` | `#4A3E44` | Emphasised body copy |
| `textMuted` | `#8A7A81` | Secondary copy |
| `textFaint` | `#AA9AA1` | Hints / placeholders |

**Signature gradient** (`Gradients.BrandRose`): `#EBA9C0 → #B76E79 → #7A2F3D`
(120° linear, this exact order — used on primary buttons and the login screen)

### Dark mode
Same semantic slots, tuned for a dark background — deep warm plums
(`bg1 #17100f`-family) with lightened text-role colours (`roseGold` becomes
`#C56E7E` etc.) so contrast holds. Full values in `RoseDarkPalette` in the
same file.

### Web consoles (admin.linumic.com, salon.linumic.com)
Consoles are self-contained HTML (`public/admin/index.html`,
`public/provider/index.html`) with their own `:root` CSS variables — same
palette, hex values match the app:

```css
--rose:#B76E79;  --deep:#8B3A47;  --plum:#7A2F3D;  --blush:#F9CBDA;
--gold:#D4A853;  --green:#3fa14a; --red:#C0392B;
--bg1:#FFF7FB;   --bg2:#FDEAF3;   --bg3:#F3E6F5;
```

> Note: the web consoles render in system UI fonts
> (`-apple-system, "Segoe UI", Roboto, …`), **not** Vazirmatn — an
> intentional/incidental difference from the Android app's typography.
> Confirm before assuming visual parity between app and console screenshots.

---

## 4. Alternate Brand Palettes

The app supports six selectable colour themes (`enum AppBrand`), each with a
light and dark variant — same structure as Rose, different hues. Rose is
default; these exist for users who want a different feel.

| Brand | Light `roseGold` (accent) | Light `deepRose` (ink) | Character |
|---|---|---|---|
| **Rose** (default) | `#B76E79` | `#8B3A47` | Warm rose & gold |
| Lavender | `#8E6FB0` | `#56347A` | Soft violet |
| Sage | `#6E9080` | `#2F4F42` | Muted sage / deep pine |
| Ocean | `#5E86A8` | `#294863` | Blue-grey |
| Honey | `#B08A4A` | `#6B4E1E` | Warm amber |
| Maroon | `#8E3B44` | `#5C1F2A` | Deep wine |

Full definitions: `app/src/main/java/com/safebeauty/app/ui/theme/Color.kt`,
search `LightPalette = Palette(`.

---

## 5. Typography

**Vazirmatn** (SIL Open Font License) — a Persian/Dari/Pashto-first typeface
with full Latin support, chosen because the platform default renders
Arabic-script text with generic, cramped glyphs.

- Files: `app/src/main/res/font/vazirmatn_{regular,medium,semibold,bold}.ttf`
- Definition: `app/src/main/java/com/safebeauty/app/ui/theme/Type.kt`
- Weights map to `FontWeight.Normal/Medium/SemiBold/Bold` app-wide
- Display/headline styles carry explicit line-height (Arabic-script ascenders
  need more leading than 1.0×); **no letter-spacing** on any script-bearing
  style — tracking breaks connected Arabic letterforms

| Style | Size | Weight | Line height |
|---|---|---|---|
| displayLarge | 36sp | Bold | 44sp |
| displayMedium | 30sp | Bold | 38sp |
| headlineLarge | 26sp | Bold | 34sp |
| headlineMedium | 24sp | Bold | 32sp |
| headlineSmall | 20sp | SemiBold | 28sp |
| titleLarge | 22sp | Bold | 30sp |
| titleMedium | 16sp | SemiBold | 24sp |
| titleSmall | 14sp | SemiBold | 20sp |
| bodyLarge | 15sp | Normal | — |
| bodyMedium | 13sp | Normal | — |
| bodySmall | 12sp | Normal | — |

---

## 6. Product Family & Identifiers

| Product | Identifier | Platform |
|---|---|---|
| Customer/Provider app | `com.security.stealthapp` | Android (Play, prod) |
| — demo variant | `com.security.stealthapp.demo` | Android (public demo) |
| Admin desktop app | `com.safebeauty.admin` | macOS/Windows (Electron) |
| Salon desktop app | `com.safebeauty.salon` | macOS/Windows (Electron) |

> The Android `applicationId` (`com.security.stealthapp`) intentionally does
> not match the Kotlin package (`com.safebeauty.app`) or the product name —
> see `CLAUDE.md` gotchas. Never derive a class name from `packageName`.

---

## 7. Domains

| Address | Serves |
|---|---|
| `safebeauty.web.app` | Main app-facing site: download page, privacy/terms, account deletion, invite links (`/get`) |
| `9sg9ceuj.linumic.com` | Admin console (deliberately not `admin.` — see `DEPLOY.md`) |
| `salon.linumic.com` | Salon-owner console |
| `linumic.com` | Parent domain; also hosts `/safebeauty-privacy-policy/` (WordPress) |

Both consoles carry `noindex, nofollow, noarchive` (meta + header) — never
intended to be publicly discoverable or indexed.

---

## 8. Marketing Asset Style Guide

From `CLAUDE.md` (project conventions) — apply when producing any new
Instagram post, story, or banner:

- **One image per language.** Dari gets the **light** (cream) colour variant;
  Pashto gets the **dark** (deep-rose) variant. Never produce both colour
  variants for the same language.
- Build as HTML using Vazirmatn + the brand rose/gold palette above; render
  with headless Chromium (see `play-store/generate_feature_graphic.py` for
  the working pattern: embed fonts as base64, embed the icon as inline SVG,
  no external asset fetches).
- Sizes: feed post **1080×1350**, story **1080×1920**, Play feature graphic
  **1024×500** (exact — Play rejects anything else).

---

## 9. Where to Find Things

| Need | File |
|---|---|
| Change a colour | `app/src/main/java/com/safebeauty/app/ui/theme/Color.kt` |
| Change type scale | `app/src/main/java/com/safebeauty/app/ui/theme/Type.kt` |
| Change the launcher icon | `app/src/main/res/drawable/ic_launcher_*.xml` |
| Add/edit a user-facing string | `app/src/main/java/com/safebeauty/app/ui/theme/AppStrings.kt` (must be added in all 3 language blocks) |
| Regenerate the Play feature graphic | `python3 play-store/generate_feature_graphic.py` |
| Store listing copy | `play-store/store_listing.md` |
| Privacy policy (canonical) | `public/privacy.html` (mirrored to `play-store/privacy-policy.html`) |
