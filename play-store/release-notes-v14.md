# Release notes — versionCode 14 (versionName 1.9)

Paste each block into Play Console → Release → "What's new", per locale.
Keep each under 500 characters. Do NOT put the version number in the body —
Play shows it separately, and hardcoding it is how older notes went stale.

---

## English (en-US)

```
• You can now delete your account and personal data from Support → Delete my account.
• Password recovery and sign-up messages now appear correctly in your language.
• Better support for screen readers, larger tap targets, and smoother back navigation.
• Notification icons and dark mode have been cleaned up.
• Updated for the latest Android version, with security and stability improvements.
```

## دری (fa-AF)

```
• اکنون می‌توانید حساب و اطلاعات شخصی خود را از بخش پشتیبانی ← حذف حساب من پاک کنید.
• پیام‌های بازیابی رمز عبور و ثبت‌نام اکنون به‌درستی به زبان شما نمایش داده می‌شوند.
• پشتیبانی بهتر از صفحه‌خوان‌ها، دکمه‌های بزرگ‌تر و حرکت روان‌تر به عقب.
• آیکون اعلان‌ها و حالت تاریک بهبود یافت.
• به‌روزرسانی برای جدیدترین نسخه اندروید، همراه با بهبود امنیت و پایداری.
```

## پښتو (ps-AF)

```
• اوس کولی شئ خپل حساب او شخصي معلومات د ملاتړ ← زما حساب ړنګ کړئ له لارې ړنګ کړئ.
• د پټنوم بیارغونې او راجستر پیغامونه اوس ستاسو په ژبه سم ښکاري.
• د سکرین لوستونکو ښه ملاتړ، لوی د لمس ځایونه، او روانه شاته تګ.
• د خبرتیا آیکونونه او تیاره حالت ښه شول.
• د اندرویډ وروستي نسخې لپاره تازه شوی، د امنیت او ثبات ښه والي سره.
```

---

## What actually changed in this build

| Area | Change |
|---|---|
| Play compliance | `targetSdk` 36 (Android 16); SQLCipher moved to the 16 KB-page-aligned `sqlcipher-android` artifact |
| Play policy | In-app account deletion (`Support → Delete my account`) + `requestAccountDeletion` callable + public `/delete-account` page |
| Privacy | The full trilingual privacy policy is now what's served at `/privacy`, and it discloses location, crash logs, year of birth, and KYC-for-all-users |
| Localization | Forgot-password, set-new-password and **registration** errors now resolve through `AppStrings` in all three languages |
| Accessibility | TalkBack labels on icon-only buttons, 48 dp touch targets, system reduced-motion honoured, predictive back enabled |
| Notifications | Real white-on-transparent status-bar icon + FCM default icon/channel meta-data (was a white square) |
| Privacy footprint | The customer "nearest" sort now asks for coarse location instead of precise |
| Dark mode | Fixed the disabled-button and announcement-popup colours that ignored the palette |
