# AnimeVault Native v2 — Full Streaming-Style UI

This is a real native Android app (Jetpack Compose), not a WebView/TWA wrapper.

## Included
- Netflix/Crunchyroll-style dark home screen
- Hero banner + poster cards
- Popular Anime section
- Continue Watching with progress bars
- Trending section
- Live AniList search with poster images
- Anime detail page with banner/poster, metadata, genres and description
- Large episode log with 100-episode paging; marking episode N watched marks all episodes <= N through max-progress logic
- Accurate watch-time accumulation using anime episode duration
- Schedule with Prev 7 / Today / Next 7 windows
- Watchlist / Completed / Favorites / Ongoing
- Separate Mature area and mature schedule
- Stream Allow switch
- Original external streaming links returned by AniList when available
- Seven regular persistent provider slots + one 18+ provider slot
- Provider settings auto-save to Android SharedPreferences and survive app restarts
- Full poster dialog
- Dashboard with watch hours, episodes, counts, CSV/JSON export
- GitHub Actions workflow for APK build without Android Studio

## Build APK without Android Studio
1. Upload this project to a GitHub repository with the files at the repository root.
2. Ensure `.github/workflows/build-apk.yml` is present.
3. Open GitHub Actions → Build AnimeVault APK → Run workflow.
4. Download the `animevault-native-v2-apk` artifact.
5. Extract and install `app-debug.apk` on Android.

## Important
The app does not ship with unauthorized streaming URLs. External providers are user-configurable and saved locally on the device.
