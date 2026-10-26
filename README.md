# Cinema in Be'er Sheva — Android MVP (Kotlin + Compose)

A native Android app with a dark theme, filters by cinema/language/subtitles, and ticket purchase links. No AI, API keys, custom server, or HTML scraping.

## Data sources

- **Planet Cinema Be'er Sheva** — public JSON API from the Vista/Quickbook system (`planetcinema.co.il`, cinema ID `1074`): it first fetches dates, then events and films for each date. `filmId` links a showtime to a film.
- **Cinema City Be'er Sheva** — JSON API from the booking system (`tickets.cinema-city.co.il/api/presentations/`, location ID `1178`). A showtime opens the order page `/order/<id>`.

These endpoints are not officially published developer APIs and can change without notice. Language tags come only from fields/attributes we were able to identify. If the API does not explicitly state the original language or subtitle language, the app shows “Not specified” instead of guessing.

Check the language and time on the ticket page before buying.

## Building the APK

You need Android Studio, Android SDK Platform 35, JDK 17, and internet access to download dependencies.

1. Unzip the archive.
2. Open the `BeerShevaCinema` folder in Android Studio.
3. Wait for Gradle Sync and for any missing components to install.
4. Select **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
5. The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Limitations

- The list includes Planet Cinema and Cinema City in Be'er Sheva only.
- Language attributes vary between providers. Cinema City's API does return language fields, but the exact set can change; unknown values should not be treated as confirmed.
