# Release notes

## v2.0 — versionCode 15

The Play Console "What's new" text, in the three languages the listing carries.
Paste each into its own language slot: `en-US`, `fa`, `ps`. Play allows 500
characters per language; these are 354 / 365 / 356.

Chosen from 40 app commits since v1.9. What is deliberately absent is the
engineering: 65 listeners bounded, search moved server-side, the composite
indexes, the Analytics funnel, the signing-key guard. Real work, invisible to
the person reading a store listing.

Two features that shipped are also absent because they belong to the salon
owner, not the customer whose phone shows this page: processing time (selling
the hour a colour develops) and wedding-party booking.

### en-US

```
• Salons on a map — see who is near you
• Discover is a photo grid now, with 24-hour stories, likes and comments
• Swipe between tabs; swipe a booking to cancel it
• Notifications arrive in your own language, and swipe away
• Invite friends and you both earn credit
• Six colour themes to choose from
• Faster search, with filters by service and district
```

### fa (Dari)

```
• سالن‌ها روی نقشه — ببینید کدام‌یک نزدیک شماست
• کشف حالا گالری عکس است، با استوری ۲۴ ساعته، پسندها و نظرها
• با کشیدن انگشت به بخش دیگر بروید؛ رزرو را بکشید تا لغو شود
• اعلان‌ها به زبان خودتان می‌رسند و با کشیدن حذف می‌شوند
• دوستان را دعوت کنید — هر دوی شما اعتبار می‌گیرید
• شش رنگ برنامه — رنگ دلخواهتان را انتخاب کنید
• جستجوی سریع‌تر، با فیلتر خدمات و ناحیه
```

### ps (Pashto)

```
• سالونونه په نقشه کې — وګورئ چې کوم یو درته نږدې دی
• کشف اوس د عکسونو ګالري — ۲۴ ساعته استوري، خوښې او نظرونه
• د ګوتې په کاږلو د برخو ترمنځ واوړئ؛ بکینګ څنګ ته وکاږئ چې لغو شي
• خبرتیاوې ستاسو په ژبه راځي، او په کاږلو ړنګیږي
• ملګري بلنه ورکړئ — تاسو دواړه کریډیټ ترلاسه کوئ
• د اپ شپږ رنګونه — خپل خوښ رنګ وټاکئ
• چټک لټون — د خدمت او ناحیې له مخې فلټر
```

Two things the reviewers caught, both worth remembering:

- Both translations first rendered "see who is near you" with the *animate*
  interrogative — کی in Dari, څوک in Pashto. The referent is salons. In Pashto
  that read as "see which woman is near you", which is a bad sentence to put in
  front of this app's users. Now کدام‌یک and کوم یو.
- The Pashto said `ستاسو په خپله ژبه`. خپل is reflexive and binds to the clause
  subject, which is خبرتیاوې — so it promised notifications arriving in *their
  own* language. The feature is the opposite: a server-side fix so they arrive
  in the *recipient's*. Now ستاسو په ژبه.

Vocabulary follows AppStrings.kt rather than the dictionary, so a feature named
here is named the same way on the screen the user then goes looking for:
حذف not پاک کردن, ړنګیږي not پاکیږي, and the swipe-hint phrasing reused verbatim.
