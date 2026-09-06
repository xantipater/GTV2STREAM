# Roadmap

This file tracks what GTV2STREAM currently supports, what is being worked on,
and what is planned. It exists so you can see the state of the project before
suggesting a feature — no GitHub account needed. It is updated with each
release.

## Currently supported (v1.1.0)

**TV & movies redirects** — pick the target in Settings:

- **Nuvio** — movies and series, launched fresh via
  `nuvio://movie/<imdb-id>` / `nuvio://detail/tv/<imdb-id>`.
- **Stremio** — movies and series via IMDb deep links
  (`stremio:///detail/{movie|series}/<imdb-id>`), resolved strictly to Stremio
  packages.

**YouTube redirects (beta)** — YouTube recommendation cards on the Google TV
launcher open as a title search in **SmartTube**. No TMDB key needed for this
path. Honest caveat: this prefills the search with the video title; it does not
pull the exact video automatically the way the native YouTube app does.

**Install and setup**

- Pure ADB install flow (see the [Windows ADB guide](INSTALL_ADB.md)) — needed
  because many TVs restrict sideloaded accessibility apps.
- In-app **Setup help** screen that renders the ADB steps on the TV itself.
- Auto-start protection detection (TCL and similar builds) with a one-tap fix
  button when the TV refuses to connect the service.

**Performance and polish**

- Cold start speeds down by up to 91%.
- Faster warm redirects.
- TMDB match cache: 32 titles kept for 24 hours, memory-only.
- Optional redirect badge: a small app-logo watermark in the top right of the
  screen after a successful redirect (toggle in Settings; needs the
  "Display over other apps" permission, redirects work fine without it).
- Bring your own TMDB key. The key and all settings stay on your device.
- Tiny APK (~29 KB), R8-shrunk.

Full behavior details: [README](README.md). Version history:
[CHANGELOG](CHANGELOG.md).

## In progress

- **TizenTube Cobalt as a second YouTube target** — currently in alpha. It
  works, but is not yet reliable on Android 14. It ships when that is fixed;
  until then SmartTube remains the YouTube target.

## Investigating (feasibility, no promises)

- **Fire TV / Firestick fork** — Fire TV's launcher is a different package with
  different payloads, so this is a port rather than a toggle. Investigating.
- **Jellyfin and Plex targets** — the idea: redirect to the title in your own
  library when it exists there, with a fallback to Nuvio or Stremio when it
  does not. This would require your server API credentials, which would be
  stored on the device only and never touch the cloud — the same bar as
  everything else in this app.

## Principles

These are not up for debate, and they bound what gets accepted:

- Completely free and MIT open source, forever.
- Local-first and bring-your-own-key: nothing about your viewing ever leaves
  your device. No accounts, no servers, no analytics, no telemetry.
- **If a feature requires your data to leave your device, it will never be
  added.**

## Suggesting a feature

- Reply in the r/nuvioaddons thread (no GitHub account needed), or
- [Open an issue](https://github.com/xantipater/GTV2STREAM/issues) if you have
  a GitHub account.

The most useful suggestions say what you clicked, what you expected, and what
actually happened. Check this file first — if it is already listed as in
progress or investigating, you will know where it stands.
