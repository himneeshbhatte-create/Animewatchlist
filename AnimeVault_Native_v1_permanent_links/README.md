# AnimeVault Native v1

A real native Android UI for AnimeVault (not a WebView/TWA).

Features included in v1:
- Dashboard
- Popular Anime via AniList
- Continue Watching section
- Search/Browse
- Schedule with previous/next 7-day window
- Watchlist / Completed / Favorites / Ongoing / Mature sections
- Episode picker
- Stream Allow toggle
- 8 configurable external provider slots; slot 8 is reserved for mature content
- Provider URLs persist in Android SharedPreferences across restarts
- Export local provider/settings JSON
- AniList GraphQL metadata and schedule requests

Provider URLs are user-configurable. The app does not ship with third-party unauthorized streaming URLs.

## Build without Android Studio

Push this project to GitHub and run `.github/workflows/build-apk.yml`. The workflow builds `app-debug.apk` using Gradle on GitHub-hosted runners.
