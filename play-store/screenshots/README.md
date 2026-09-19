# Play Store phone screenshots

The four images in this directory are the ones live on the `fa-AF` listing
(the only locale the listing has). Captured 2026-09-10 from a Pixel 8
emulator, 1080×2400, running `assembleProdRelease` against production data
with the emulator locale set to `fa-AF`.

Replace them with `scripts/play.py` — see DEPLOY.md, "Replacing the store
screenshots". Keep this directory in sync when you do, so what is on the store
is reviewable in git rather than only inside Play Console.

| File | Screen | Why it is in the set |
|---|---|---|
| `1-salon-list.png` | Salon list | Filters, city/neighbourhood, the offers strip. The first thing a customer sees. |
| `2-salon-detail.png` | Salon detail | Verified badge, today's hours, portfolio, rating. |
| `3-pick-a-time.png` | Slot picker | Stylist + free times. This is the thing the app exists to do. |
| `4-sign-in.png` | Sign-in | Phone and password, no email. Worth showing in a market where an email address is not a given. |

## What the previous set was, and why it went

The three it replaced had been live since before 2026-09-04 and were wrong in
four separate ways, not merely old:

- **Broken text wrapping** — "SafeBea / uty", "درخوا / ست‌ها", "پروفای / ل من".
- **A positioning the product no longer has** — "خصوصی · مخفیانه" (covert),
  which was dropped when `FLAG_SECURE` was removed on 2026-09-04. See
  `app/src/main/java/com/safebeauty/app/ui/MainActivity.kt`.
- **Dead content** — salons "رینا ناخن" and "خانه زیبایی" that no longer
  exist, "3 ارائه‌دهنده" when production has 2, a "۱۹ ژوئن" announcement.
- **The wrong audience** — one of the three was the *provider* income
  dashboard, on a listing whose installs are customers.

## `2-salon-detail.png` is cropped on purpose

1080×1800, not 1080×2400. The full screen's "تیم ما" section renders a real
photograph of a salon employee's face, pulled from production. A Play listing
is public and worldwide; publishing an identifiable Afghan woman's face on one
is not a decision to make in passing, and the store page loses nothing without
it. The crop keeps everything above that section.

The stylist's first name ("Maryam") does remain, here and in
`3-pick-a-time.png` — the salon publishes it in-app as a bookable stylist, and
a first name is not a face. If the owner wants that gone too, recapture with a
seeded demo salon rather than cropping further.

## Capturing a new set

The emulator recipe, since it took a few tries:

    ADB=~/Library/Android/sdk/platform-tools/adb
    $ADB shell settings put system ...          # boot Pixel_8, 1080×2400
    $ADB shell setprop persist.sys.locale fa-AF # then restart the framework
    $ADB shell pm clear com.security.stealthapp # so it comes up in Dari, RTL
    $ADB exec-out screencap -p > shot.png

Two traps: `input tap` does nothing on this emulator — use
`input touchscreen tap` — and **pressing Back on the customer dashboard signs
the session out**, dropping you at the login screen. Capture everything you
need in one pass; dismiss sheets with their own close button, not Back.
