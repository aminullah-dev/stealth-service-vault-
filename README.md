# Kabul Signal — Android App

A native Android news reader for **[kabulsignal.com](https://kabulsignal.com/)**, a
Dari/Persian-language WordPress news site. The app reads content directly from the
site's WordPress REST API (`/wp-json/wp/v2/`) — no plugin or server changes required.

## Features

- **Latest feed** with featured images, headline, excerpt, author and date
- **Category filtering** via chips (categories pulled live from the site)
- **Full-text search** across articles
- **Article reader** that renders the post's HTML (images, embeds, formatting) in a
  styled, RTL-first WebView with light/dark support
- **Infinite scroll** pagination and **pull-to-refresh**
- **Share** and **open in browser** actions
- Right-to-left layout throughout, matching the publication's language

## Tech stack

- Kotlin + Jetpack Compose (Material 3)
- MVVM with `ViewModel` + `StateFlow`
- Retrofit + OkHttp + Gson against the WordPress REST API
- Coil for image loading
- Navigation Compose

## Project layout

```
app/src/main/java/com/kabulsignal/news/
├── data/
│   ├── model/            Domain models (Article, Category)
│   ├── remote/           Retrofit API, DTOs, ServiceLocator
│   └── NewsRepository.kt Maps the WP REST API to domain models
├── ui/
│   ├── home/             Feed screen + ViewModel + article card
│   ├── article/          Reader screen + ViewModel
│   ├── navigation/       NavGraph
│   └── theme/            Compose theme
└── util/                 HTML decoding, date formatting, article template
```

## Building

1. Open the project in **Android Studio** (Koala / 2024.1+).
2. Let Gradle sync (downloads the AGP 8.5, Kotlin 2.0 and Compose dependencies).
3. Run the `app` configuration on a device/emulator (min SDK 26, Android 8.0+).

To point the app at a different WordPress site, change `WP_BASE_URL` / `SITE_URL`
in `app/build.gradle.kts`.

> The build requires network access to download Gradle dependencies the first time.
