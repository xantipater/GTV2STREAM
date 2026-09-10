# Roadmap

GTV2STREAM is a free, open-source Android TV companion for Google TV. It runs quietly in the background and listens to the launcher: when you click a recommendation card, it redirects that title into the app you have chosen.

- **Movie and TV recommendations** are matched against TMDB and opened at exactly that title in Nuvio or Stremio.
- **YouTube recommendation cards** open as a title search in SmartTube.
- It does not host, stream, or provide media itself. No accounts, no servers, no analytics — nothing about your viewing ever leaves your device.

This file tracks what is currently supported, what is being worked on, and what is planned. It exists so you can see the state of the project before suggesting a feature — no GitHub account needed. It is updated with each release.

## Currently supported (v1.1.0)

**TV & movies redirects** — pick the target in Settings:

- **Nuvio** — movies and series, launched fresh via `nuvio://movie/<imdb-id>` / `nuvio://detail/tv/<imdb-id>`.
- **Stremio** — movies and series via IMDb deep links (`stremio:///detail/{movie|series}/<imdb-id>`), resolved strictly to Stremio packages.

**YouTube redirects (beta)** — YouTube recommendation cards on the Google TV launcher open as a title search in **SmartTube**. No TMDB key is needed for this path. This prefills the search with the video title; it does not pull the exact video automatically the way the native YouTube app does.

**Install and setup**

- Pure ADB install flow (see the [Windows ADB guide](INSTALL_ADB.md)) — needed because many TVs restrict sideloaded accessibility apps.
- In-app **Setup help** screen that renders the ADB steps on the TV itself.
- Auto-start protection detection (TCL and similar builds) with a one-tap fix button when the TV refuses to connect the service.

**Performance and polish**

- Cold start speeds down by up to 91%.
- Faster warm redirects.
- TMDB match cache: 32 titles kept for 24 hours, memory-only.
- Optional redirect badge: a small app-logo watermark in the top right after a successful redirect (toggle in Settings; needs the "Display over other apps" permission, redirects work without it).
- Bring your own TMDB key. The key and all settings stay on your device.
- Tiny APK (~29 KB), R8-shrunk.

Full behavior details: [README](README.md). Version history: [CHANGELOG](CHANGELOG.md).

## In progress for v1.2

v1.2 is focused on making routing more reliable while adding a small number of tightly-scoped user features.

- **Provider regression coverage** — build deterministic fixtures around Google TV recommendation payloads so known failures remain fixed.
- **Disney+/provider parsing reliability** — issue #2; harden known provider/action payload handling without making the parser broadly permissive.
- **Provider whitelist** — issue #1; allow selected providers to bypass GTV2STREAM and continue through normal Google TV/provider behaviour.
- **Update awareness** — issue #11; automatically check for a newer stable GitHub release and show a TV-friendly notice in Settings. Updates remain manual: GTV2STREAM will not silently download or install APKs.
- **TizenTube Cobalt target** — issue #12; add TizenTube Cobalt as a second selectable YouTube target alongside SmartTube. The existing alpha path has an Android 14 reliability concern that must be resolved or tightly bounded before v1.2 ships.
- **CI/release hardening** — helper tests, Android lint, debug APK builds and regression checks act as release gates.

The v1.2 candidate will still require real Google TV validation before it is merged to `main` because emulator tests cannot fully reproduce proprietary launcher accessibility payloads or third-party target-app behaviour.

## Investigating (feasibility, no promises)

- **Fire TV / Firestick fork** — Fire TV's launcher is a different package with different payloads, so this is a port rather than a toggle. Investigating.
- **Jellyfin and Plex targets** — redirect to a title in your own library when it exists there, with fallback to Nuvio or Stremio when it does not. This would require server API credentials stored on-device only.

## Principles

These are not up for debate, and they bound what gets accepted:

- Completely free and MIT open source, forever.
- Local-first and bring-your-own-key: nothing about your viewing ever leaves your device. No accounts, no servers, no analytics, no telemetry.
- **If a feature requires your viewing data to leave your device, it will never be added.**

## Suggesting a feature

- Reply in the r/nuvioaddons thread (no GitHub account needed), or
- [Open an issue](https://github.com/xantipater/GTV2STREAM/issues) if you have a GitHub account.

The most useful suggestions say what you clicked, what you expected, and what actually happened. Check this file first — if it is already listed as in progress or investigating, you will know where it stands.