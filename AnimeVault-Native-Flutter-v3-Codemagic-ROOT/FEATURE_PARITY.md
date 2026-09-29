# AnimeVault Native Flutter — feature parity notes

This native app is based on the uploaded AnimeVault v20 web app.

## Persistent viewing links

The app keeps **8 provider URL slots** on the device using `SharedPreferences`.

On first launch, the normal provider set is seeded once from the web app's configured viewing flow:
1. Crunchyroll
2. YouTube
3. Muse India · YouTube
4. Ani-One · YouTube
5. Prime Video
6. Netflix
7. JustWatch India
8. Custom Provider (user-configured slot)

After the user saves a URL in Settings, it stays there across app restarts. The watch picker uses the saved URL templates and substitutes `{title}`, `{episode}` and `{slug}` where present.

The web app's AniList `streamingEpisodes` links are also checked first for exact episode links, up to the picker limit.

## Important source-data note

The uploaded web app contains seven hardcoded normal provider searches plus an eighth configurable provider slot. No separate literal URL for an eighth normal provider was present in the uploaded source, so the native project does not invent one.
