# Release notes — versionCode 22 (2.1.5)

Bug-fix release over v21 (2.1.4). One change: the in-app "Update" button did
nothing when tapped on a real phone (commit 30a3c2d) — it now tries the Play
Store app (`market://`), falls back to a browser, and shows a message instead
of failing silently; the forced-update dialog no longer crashes in the same
case. Each Play language field is capped at 500 characters.

## en-US

```
• Fixed the "Update" button that sometimes did nothing when tapped.
```

## fa-AF (Dari)

```
• رفع دکمهٔ «به‌روزرسانی» که گاهی هنگام زدن، کاری انجام نمی‌داد.
```

## ps-AF (Pashto)

```
• د «تازه کړئ» تڼۍ به کله ناکله کار نه کاوه؛ دا ستونزه اوس حل شوه.
```
