# Roadmap

GTV2STREAM is a free, open-source Android TV companion for Google TV. It runs quietly in the background and listens to the launcher: when you click a recommendation card, it redirects that title into the app you have chosen.

- **Movie and TV recommendations** are matched against TMDB and opened at exactly that title in Nuvio, Stremio or WuPlay.
- **YouTube recommendation cards** open as a title search in SmartTube or TizenTube Cobalt.
- It does not host, stream, or provide media itself. No accounts, no servers, no analytics. Nothing about your viewing ever leaves your device.

This file tracks what is currently supported, what is being worked on, and what is planned. It exists so you can see the state of the project before suggesting a feature, no GitHub account needed. It is updated with each release.

## Currently supported (v1.2.0)

**TV & movies redirects** — pick the target in Settings:

- **Nuvio** — movies and series, launched fresh via `nuvio://movie/<imdb-id>` / `nuvio://detail/tv/<imdb-id>`.
- **Stremio** — movies and series via IMDb deep links (`stremio:///detail/{movie|series}/<imdb-id>`), resolved strictly to Stremio packages.
- **WuPlay** — movies and series via `wuplay://movie/<imdb-id>` / `wuplay://series/<imdb-id>`.

**YouTube redirects** — pick the target in Settings:

- **SmartTube** — YouTube cards open as a title search in SmartTube. No TMDB key is needed for this path.
- **TizenTube Cobalt** — YouTube cards open as a title search in Cobalt, launched through the media-search action Cobalt actually honours, with the query delivered to the live instance. If Cobalt is not installed, the redirect fails visibly instead of quietly opening SmartTube.

Both YouTube paths prefill a title search. They do not pull the exact video automatically the way the native YouTube app does. Selecting a YouTube card may show a brief flash of the stock YouTube app first: the launcher starts it with an intent that cannot be intercepted, and GTV2STREAM diverts from there.

**Provider whitelist** — 26 providers can be toggled to skip GTV2STREAM entirely and keep normal Google TV behaviour. Every provider defaults to off, so nothing changes unless you ask for it.

**Update awareness** — GTV2STREAM checks for a newer stable release once every 24 hours and, when you open the app, offers to download and install it in place. Drafts and prereleases are ignored. Nothing is ever downloaded or installed without you asking, and the system installer always confirms.

**Parsing reliability**

- Card titles are read from the card you actually focused, not only from ambient panel text, which is what fixed clicks that produced no title on rows like "Top picks for you".
- YouTube card titles up to 15 words are accepted; every other path stays bounded at 7 words so synopsis prose can never be mistaken for a title.
- Card payloads that name a provider or a watch action accept long titles, up to 15 words, so an eight-word film title such as *Shang-Chi and the Legend of the Ten Rings* redirects from every card shape. Payloads with no card evidence keep the strict 7-word bound, so launcher chrome and prose stay refused.
- Launcher chrome, position labels, metadata, advertisements and sponsored cards are rejected outright. Fail-closed wins over the rare title miss.

**Install and setup**

- Pure ADB install flow (see the [Windows ADB guide](INSTALL_ADB.md)), needed because many TVs restrict sideloaded accessibility apps.
- In-app **Setup help** screen that renders the ADB steps on the TV itself.
- Auto-start protection detection (TCL and similar builds) with a one-tap fix button when the TV refuses to connect the service.

**Performance and polish**

- Cold start speeds down by up to 91%.
- Faster warm redirects.
- TMDB match cache: 32 titles kept for 24 hours, memory-only.
- Optional redirect badge: a small app-logo watermark in the top right after a successful redirect (toggle in Settings; needs the "Display over other apps" permission, redirects work without it).
- Bring your own TMDB key. The key and all settings stay on your device.
- Tiny APK, R8-shrunk.

Full behavior details: [README](README.md). Version history: [CHANGELOG](CHANGELOG.md).

## In progress after v1.2

- **Fire TV / Firestick fork (FTV2STREAM)** — a fork is starting, with v1 focusing on stable Nuvio directs. Fire TV's launcher is a different package with different payloads, so this is a port rather than a toggle, and no ETA yet pending an assessment of how much of the Google TV work transfers.
- **Disney+ / Paramount+ parsing follow-up** — provider edge shapes for both are covered by regression fixtures, but a specific failing title from the original report has not been reproduced yet, so this stays open until a named case is pinned down and covered.
- **Reaching users who never open the app** — update checks currently only surface inside the app, and the app's only screen is Settings, so a user who never opens it never learns an update exists. No notification permission has been added for this; any change here has to earn its permission.
- **Provider regression coverage** — deterministic fixtures around real Google TV recommendation payloads so known failures cannot silently return, and so a physical TV is not required for every parser change.
- **CI and release hardening** — helper tests, Android lint, debug APK builds and regression checks act as release gates.

Real Google TV validation is still required before any release is merged to `main`, because emulator tests cannot fully reproduce proprietary launcher accessibility payloads or third-party target-app behaviour. This project has one TV and one maintainer, which is exactly why the fixture suite exists.

## Investigating (feasibility, no promises)

- **Jellyfin and Plex targets** — redirect to a title in your own library when it exists there, with fallback to Nuvio or Stremio when it does not. This would require server API credentials stored on-device only.

## Principles

These are not up for debate, and they bound what gets accepted:

- Completely free and MIT open source, forever.
- Local-first and bring-your-own-key: nothing about your viewing ever leaves your device. No accounts, no servers, no analytics, no telemetry.
- **If a feature requires your viewing data to leave your device, it will never be added.**
- No permission is added unless a feature genuinely needs it.

## Suggesting a feature

- Reply in the r/nuvioaddons thread (no GitHub account needed), or
- [Open an issue](https://github.com/xantipater/GTV2STREAM/issues) if you have a GitHub account.

The most useful suggestions say what you clicked, what you expected, and what actually happened. Check this file first: if it is already listed as in progress or investigating, you will know where it stands.
