# Roadmap

GTV2STREAM is a free, open-source Android TV companion for Google TV. It runs quietly in the background and listens to the launcher: when you click a recommendation card, it redirects that title into the app you have chosen.

- **Movie and TV recommendations** are matched against TMDB and opened at exactly that title in Nuvio, Stremio or WuPlay.
- **YouTube recommendation cards** open as a title search in SmartTube or TizenTube Cobalt.
- It does not host, stream, or provide media itself. No GTV2STREAM accounts, project-operated backend or analytics. Title queries and the supplied key go to TMDB; selected data also goes to the chosen target app. See README Privacy.

This file tracks what is currently supported, what is being worked on, and what is planned. It exists so you can see the state of the project before suggesting a feature, no GitHub account needed. It is updated with each release.

## Stabilisation candidate (unreleased)

The implementation branch preserves PR #19 and addresses matching identity,
interaction cancellation, whitelist reassertion, node ownership, update validation,
failure feedback and privacy/documentation accuracy. See [STABILISATION](docs/STABILISATION.md)
for evidence, behavioural tradeoffs and outstanding physical-TV/signed-update gates.
No optional target, UI feature or major dependency migration is included.

## Published feature set (v1.2.0)

**TV & movies redirects** — pick the target in Settings:

- **Nuvio** — movies and series, launched fresh via `nuvio://movie/<imdb-id>` / `nuvio://detail/tv/<imdb-id>`.
- **Stremio** — movies and series via IMDb deep links (`stremio:///detail/{movie|series}/<imdb-id>`), resolved strictly to Stremio packages.
- **WuPlay** — movies and series via `wuplay://movie/<imdb-id>` / `wuplay://series/<imdb-id>`.

**YouTube redirects** — pick the target in Settings:

- **SmartTube** — YouTube cards open as a title search in SmartTube. No TMDB key is needed for this path.
- **TizenTube Cobalt** — YouTube cards open as a title search in Cobalt, launched through the media-search action Cobalt actually honours, with the query delivered to the live instance. If Cobalt is not installed, the redirect fails visibly instead of quietly opening SmartTube.

Both YouTube paths prefill a title search. They do not pull the exact video automatically the way the native YouTube app does. Selecting a YouTube card may show a brief flash of the stock YouTube app first: the launcher starts it with an intent that cannot be intercepted, and GTV2STREAM diverts from there.

**Provider whitelist** — 26 providers can be toggled to skip GTV2STREAM entirely and keep normal Google TV behaviour. Every provider defaults to off, so nothing changes unless you ask for it.

**Update awareness** — the published updater has a known minimum-size defect; use
[the ADB recovery route](UPDATE.md) for small updates. GTV2STREAM checks for a newer stable release once every 24 hours and, when you open the app, offers to download and install it in place. Drafts and prereleases are ignored. Nothing is ever downloaded or installed without you asking, and the system installer always confirms.

**Parsing reliability**

- Card titles are read from the card you actually focused, not only from ambient panel text, which is what fixed clicks that produced no title on rows like "Top picks for you".
- Provider-bearing cards and YouTube titles permit longer bounded titles; general node text remains more restrictive. These guards reduce false positives but do not prove every payload is correctly classified.
- Card payloads that name a provider or a watch action accept long titles, up to 15 words, including tested forms of *Shang-Chi and the Legend of the Ten Rings*. Real device card shapes still need validation. Payloads with no card evidence keep the strict 7-word bound, so launcher chrome and prose stay refused.
- Launcher chrome, position labels, metadata, advertisements and sponsored cards are rejected outright. Fail-closed wins over the rare title miss.

**Install and setup**

- Pure ADB install flow (see the [Windows ADB guide](INSTALL_ADB.md)), needed because many TVs restrict sideloaded accessibility apps.
- In-app **Setup help** screen that renders the ADB steps on the TV itself.
- Auto-start protection detection (TCL and similar builds) with a one-tap fix button when the TV refuses to connect the service.

**Performance and polish**

- Warm-process launches and bounded memory caches reduce repeated work; no cross-device latency guarantee is made.
- TMDB match cache: 32 titles kept for 24 hours, memory-only.
- Optional redirect badge: a small app-logo watermark in the top right after a successful redirect (toggle in Settings; needs the "Display over other apps" permission, redirects work without it).
- Bring your own TMDB key. The key is stored locally and used in HTTPS requests to TMDB.
- Tiny APK, R8-shrunk.

Full behavior details: [README](README.md). Version history: [CHANGELOG](CHANGELOG.md).

## In progress after v1.2

- **Fire TV / Firestick fork (FTV2STREAM)** — a fork is starting, with v1 focusing on stable Nuvio directs. Fire TV's launcher is a different package with different payloads, so this is a port rather than a toggle, and no ETA yet pending an assessment of how much of the Google TV work transfers.
- **Disney+ / Paramount+ parsing follow-up** — provider edge shapes for both are covered by regression fixtures, and the named Shang-Chi case is represented in fixtures; a corrected real-card redirect still needs physical-TV verification.
- **Reaching users who never open the app** — update checks currently only surface inside the app, and the app's only screen is Settings, so a user who never opens it never learns an update exists. No notification permission has been added for this; any change here has to earn its permission.
- **Provider regression coverage** — deterministic fixtures around real Google TV recommendation payloads so known failures cannot silently return, and so a physical TV is not required for every parser change.
- **CI and release hardening** — executable helper tests, debug/release lint, debug/test/shrunk-release builds and Android emulator interaction tests. A signed physical-TV upgrade remains a separate manual gate.

Real Google TV validation is still required before any release is merged to `main`, because emulator tests cannot fully reproduce proprietary launcher accessibility payloads or third-party target-app behaviour. This project has one TV and one maintainer, which is exactly why the fixture suite exists.

## Investigating (feasibility, no promises)

- **Jellyfin and Plex targets** — redirect to a title in your own library when it exists there, with fallback to Nuvio or Stremio when it does not. This would require server API credentials stored on-device only.

## Principles

These are not up for debate, and they bound what gets accepted:

- Completely free and MIT open source, forever.
- Local-first and bring-your-own-key: no project-operated viewing-history backend, analytics or telemetry. Disclose title lookup, target routing and update network requests accurately.
- **No viewing-history collection or undisclosed network transmission.**
- No permission is added unless a feature genuinely needs it.

## Suggesting a feature

- Reply in the r/nuvioaddons thread (no GitHub account needed), or
- [Open an issue](https://github.com/xantipater/GTV2STREAM/issues) if you have a GitHub account.

The most useful suggestions say what you clicked, what you expected, and what actually happened. Check this file first: if it is already listed as in progress or investigating, you will know where it stands.
