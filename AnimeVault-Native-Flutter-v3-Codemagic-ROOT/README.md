# AnimeVault Native

A native Flutter Android rebuild of the uploaded AnimeVault web app. It is **not a WebView** and does not ship the web interface.

## Feature parity implemented

- Home: Popular + Continue Watching
- Discover: AniList search, filters, sorting and pagination
- Schedule: 7-day windows with previous/next navigation in local time
- Watchlist, Completed, Favorites and Ongoing sections
- Anime detail: poster, fullscreen poster, banner, synopsis, genres, score, format, season/year, studio, airing status
- Episode tracker: mark one, mark-through, next episode, jump-to, progress
- Watch Episode flow with exact AniList streaming links when provided plus legal provider-search links
- Return-to-app prompt to mark the episode watched after launching a provider
- Dashboard: watchlist, completed, watched episode count, estimated watch time, interactive tap-to-open progress bars
- Data export: CSV, JSON backup/restore and PDF report
- Settings: mature-area toggle, external streaming toggle, 18+ confirmation/provider toggle, custom provider templates, API enrichment
- Separate Mature area: browse, schedule, watching and completed
- API enrichment adapters: AniList, Jikan, Kitsu, AnimeChan, Waifu.im, NekosBest, AnimeFacts and Trace.moe entry
- Local persistence with SharedPreferences
- Poster caching with cached_network_image
- Dark native UI designed to match the original AnimeVault visual language without desktop clutter

## Cloud build

This project is prepared for APK cloud builders. `apkit` can auto-detect Flutter projects from `pubspec.yaml` and runs `flutter build apk`; no Gradle wrapper is required for Flutter projects.
