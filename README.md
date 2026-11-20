# Algo Ali Walls

An Android live wallpaper for traders: moving candlestick charts, order book, heatmap, Fibonacci and quote styles, with your own name, company and logo.

Prices on the wallpaper are generated for design only. They are not live market data.

## Install

1. On your phone, open the **Releases** page of this repo and download `AlgoAliWalls.apk` from the latest release.
2. Open the file. If Android asks, allow installing apps from your browser or file manager.
3. Open Algo Ali Walls, pick a style, add your branding, and tap **Set as live wallpaper**.

## Build

Every push to `main` builds a signed APK with GitHub Actions and publishes it to the `latest` release.
Locally: `./gradlew assembleRelease` (needs the Android SDK, API 34).

## Fonts

Chakra Petch and IBM Plex Mono, both under the SIL Open Font License (see `app/src/main/assets/fonts`).
