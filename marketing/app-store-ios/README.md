# App Store screenshots — iOS

Five 6.9-inch captures (1320×2868, RGB) of the real iOS app, in Dari, showing
the invented demo world from `marketing/demo/` — six fictional Kabul salons,
never a real one. Taken 2026-09-21 from a Debug build of 1.0.1 (7) on the
iPhone 17 Pro Max simulator, iOS 26.5.

| File | Screen |
|---|---|
| `01-salon-list.png` | Salon list, browsing without an account (ورود in the toolbar) |
| `02-category-hair.png` | Category مو applied: 4 providers |
| `03-salon-page-group-booking.png` | Salon page: the sign-in-to-book banner, group/wedding booking with two guests' services |
| `04-filters-cheapest.png` | Filter & sort sheet, cheapest first |
| `05-map.png` | The six salons on the map |

## How it works

Launched with `-useFirestoreEmulator`, a DEBUG-only hook
(`ScreenshotBackend` in `ios/SafeBeauty/App/SafeBeautyApp.swift`) configures
Firebase for a made-up project, `demo-screens`, instead of the bundled plist,
and points Firestore at `127.0.0.1:8080`. Auth and Functions are pointed at
local ports where nothing listens, so the launch-time anonymous sign-in and any
callable fail on this Mac rather than reaching a real project. Release builds do
not contain the hook.

`seed_emulator.py` copies the demo world from `safebeauty-staging` (GET only)
into the emulator and sets the emulator's rules (reads open, writes closed).
Nothing is written to staging or production.

## Reproduce

1. Emulator, from a throwaway folder. Never from a folder whose `firebase.json`
   has a `rules` key: this repo's default project is production.

   ```sh
   mkdir -p /tmp/sb-screens && cd /tmp/sb-screens
   echo '{"emulators":{"firestore":{"host":"127.0.0.1","port":8080},"ui":{"enabled":false}}}' > firebase.json
   firebase emulators:start --only firestore --project demo-screens
   ```

2. Seed, from the repo root (needs `gcloud auth login` as the owner). Expect
   `salons 6, reviews 21, salon_offers 2, salon_gallery 0` and the write probe
   refused with 403.

   ```sh
   python3 marketing/app-store-ios/seed_emulator.py
   ```

3. Build Debug and start the simulator:

   ```sh
   cd ios && xcodegen generate --spec project.yml
   xcodebuild -project SafeBeauty.xcodeproj -scheme SafeBeauty -configuration Debug \
     -destination 'platform=iOS Simulator,name=iPhone 17 Pro Max' -derivedDataPath /tmp/sb-dd build
   UDID=$(xcrun simctl list devices available | awk -F'[()]' '/iPhone 17 Pro Max/{print $2; exit}')
   xcrun simctl boot $UDID; xcrun simctl ui $UDID appearance light
   xcrun simctl status_bar $UDID override --time 9:41 --batteryState charged \
     --batteryLevel 100 --cellularBars 4 --wifiBars 3 --dataNetwork wifi
   xcrun simctl install $UDID /tmp/sb-dd/Build/Products/Debug-iphonesimulator/SafeBeauty.app
   xcrun simctl launch $UDID com.safebeauty.app -useFirestoreEmulator
   ```

   On the first launch pick دری on the onboarding picker, then رد شدن. Do not
   pass `-safebeauty.language fa` instead: the launch argument outranks the
   picker, and switching language later leaves strings and layout disagreeing.

4. Each screen:
   - **01**: drag the list up about 70pt, slowly, so the large title collapses
     to the inline سالن‌ها.
   - **02**: tap مو with the list at rest.
   - **03**: open سالن یاسمین. Turn on رزرو گروهی / عروسی (in the simulator a
     tap does not flip it, so drag the knob left). Tap افزودن مهمان, then pick
     آرایش عروس for guest 1 and میکاپ مجلسی for guest 2.
   - **04**: back, همه, collapse the title as in 01, فیلترها, ارزان‌ترین.
   - **05**: close the sheet and tap the map icon. Drag the pins to the middle
     and pinch until all six fit.

   Capture each one about two seconds after the last touch, so the scroll
   indicator has faded:
   `xcrun simctl io $UDID screenshot --type=png raw/NN-name.png`

5. Finish: `python3 marketing/app-store-ios/finish.py raw/*.png --out marketing/app-store-ios`.
   It asserts the 6.9-inch size and drops the alpha channel. It also fills the
   one-point black square that the iOS 26.5 simulator draws at the top-left of
   every capture; a Settings screenshot has it too, so it is not the app.

6. Clean up: Ctrl-C the emulator, then
   `xcrun simctl status_bar $UDID clear` and `xcrun simctl shutdown $UDID`.

## What this cannot capture

- **Time slots.** `getBookedSlots` requires `request.auth`, and the simulator
  cannot hold a Firebase Auth session (securityd -34018, CLAUDE.md), so the
  picker shows بارگذاری ناموفق بود. That is why 03 is in group-booking mode: two
  guests push the time section below the fold. The ordinary salon page has the
  error on its first screen.
- **Reviews.** They sit directly under the time section. The page ends about
  380pt after the error, which is less than a screen, so no scroll position
  shows the reviews without it. Hanging the callable to show a spinner instead
  would be staging the picker's state, so it was not done.

A device or TestFlight build holds a session and can capture both.

## App issues seen while capturing, not fixed here

- At rest, the salon list's large title does not draw: 02 shows the blank band
  above the category chips. It appears once the list scrolls (01).
- No search field is visible on the salon list, at rest or scrolled.
- In Dari the day strip opens at the end of the week, with today off-screen.
- While browsing without an account, the salon page's Book bar sits under the
  language picker.
- The map opens on the fallback centre near the airport and does not re-centre
  once the salons load.
