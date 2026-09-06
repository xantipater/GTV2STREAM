# Changelog

All notable changes to GTV2STREAM are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/); versions follow
[Semantic Versioning](https://semver.org/).

## [1.1.0] - 2026-09-06

### Added
- Selectable **TV & movies** targets (Nuvio or Stremio), plus SmartTube
  YouTube redirects and matching test buttons in Settings.
- YouTube recommendation cards are detected from launcher payloads ("Watch on
  YouTube"/"Stream on YouTube" markers, YouTube provider-first items) and open
  as a YouTube title search in SmartTube — no TMDB key needed.
- Stremio support via its documented IMDb deep links
  (`stremio:///detail/{movie|series}/{imdb}`), resolved strictly to Stremio
  packages (`com.stremio.one`, `io.stremio.app`) — Nuvio also registers
  `stremio://`, so generic handler fallback would misroute.
- Redirect confirmation badge: small app-logo overlay at the top right,
  shown ~300 ms after a successful launch so it only draws over the target
  app; optional "Display over other apps" permission with a Settings toggle.
- In-app **Setup help (ADB install)** screen rendering the ADB installation
  steps and commands on the TV.
- Auto-start block detection for vendor TVs (TCL): the status banner reports
  "Enabled, but the TV has not connected the service" with an
  **Open app info (allow auto-start)** fix button; the service publishes a
  15-second connection heartbeat so the status is always live.
- Parser hardening: provider name variants (Apple TV, HBO Max, Starz, AMC+,
  Discovery+, and more), action suffixes (`Stream on`, `Streaming on`,
  `New on`, `Included with`), dash/bullet/comma/period provider segments,
  trailing-punctuation artifacts, and an expanded Google TV UI vocabulary so
  launcher buttons (Display, Move, quick-settings rows, input labels, price
  actions) never misfire as titles.
- Click-payload extraction improvements: ancestor walk, bounded region scan
  of the active window, and non-launcher click diagnostics for TV builds
  whose launcher strips card payloads.
- Diagnostics: raw launcher payloads are logged (truncated, not stored) when
  a card cannot be read, with the clicked node's view id and class.
- Performance: TMDB keep-alive connection reuse, 5-second connect timeout,
  warm Nuvio/Stremio launches, and a 32-entry in-memory match cache
  (normalized titles, 24-hour TTL) so repeated selections skip the network.
- Release builds now use R8 code and resource shrinking (~29 KB APK).

### Verified on-device (2026-09-05, TCL Smart_TV_Pro)
- Nuvio movie/series redirects (Iron Man probe and real cards: Black Widow,
  Supergirl, FBI, Greyhound, Rocky Horror, Three-Body, Murder Trial).
- Stremio detail deep links open the correct title in `com.stremio.one`.
- SmartTube title searches open from Google TV YouTube recommendations.
- Missing-target toasts, target persistence, badge toggle, blocked-service
  detection with the auto-start fix, redirect badge toggle.
