# Changelog

All notable changes to GTV2STREAM are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/); versions follow
[Semantic Versioning](https://semver.org/).

## [1.2.0] - 2026-09-10

### Added
- **TizenTube Cobalt as a second selectable YouTube target** (issue #12).
  Cobalt is launched with `android.media.action.MEDIA_PLAY_FROM_SEARCH`
  addressed at its explicit component and flagged
  `NEW_TASK|MULTIPLE_TASK|EXCLUDE_FROM_RECENTS` so the search reaches the live
  instance; `NEW_TASK|CLEAR_TASK` was proven to lose the query on a warm
  Cobalt. Cobalt exposes no reliable external `VIEW` contract, so this action
  is used instead. Selecting Cobalt and having it missing fails closed with a
  visible status message rather than silently opening SmartTube.
- **WuPlay as a third TV & movies target**, using its verified deep links
  `wuplay://movie/<imdb-id>` and `wuplay://series/<imdb-id>` (package
  `app.wuplay.androidtv`, verified against WuPlay 0.9.0-beta). Selectable in
  Settings alongside Nuvio and Stremio.
- **Provider whitelist with 26 providers** (issue #1): Netflix, Prime Video,
  Disney+, ITVX, BBC iPlayer, Hulu, Max, HBO Max, Paramount+, Apple TV+,
  YouTube, Google TV, Peacock, Channel 4, My5, Starz, Showtime, AMC+,
  Discovery+, MGM+, Britbox, Shudder, Tubi, Pluto TV, Freevee, Crunchyroll.
  Whitelisted providers skip GTV2STREAM and keep normal Google TV behaviour.
  Every provider defaults to off, so an existing install behaves exactly as it
  did before, and provider aliases collapse to a single canonical identity so
  a toggle can never be matched against a title.
- **Update awareness** (issue #11): a 24-hour-throttled check of the GitHub
  releases API that ignores drafts and prereleases and only reports a release
  newer than the installed version. Settings shows an amber notice with an
  **Open release page** button and, when the release has an APK attached, a
  one-tap **Download & install** button. Downloads are size- and
  archive-verified before being handed to the Android system installer, which
  always asks for confirmation. Nothing is ever downloaded or installed
  silently, and no check happens unless the app is opened.
- **Update prompt on app open**, shown once per release: **Download &
  install**, **Open release page**, or **Later**. Later silences the prompt for
  that version while leaving the notice and buttons in Settings, so an
  available update is never nagged and never hidden.
- **Focus-captured card titles**: the card the user actually focused is
  remembered and reused when the click arrives, and outranks ambient panel
  text. A poll that returns no readable title can no longer discard a title
  that was just captured.

### Fixed
- **YouTube cards with titles longer than 7 words were not redirected.** The
  YouTube path now accepts up to 15 words, because video titles genuinely run
  long and a YouTube card is only ever routed to a search, never to TMDB.
  Every other payload path stays bounded at 7 words so synopsis and metadata
  prose cannot be mistaken for a title.
- **YouTube cards whose payload is `<title>, YouTube • <channel>` were
  rejected** as having no credible title, which is the common shape for
  launcher rows such as "Top picks for you".
- **Launcher grid position labels (`Column 3`) could be searched as a title**,
  producing a redirect to a search for launcher chrome. UI-value rejection was
  extended to cover position labels.
- **An empty panel poll could discard a freshly captured title**, causing
  clicks to miss with "no card title cached" on rows that expose only position
  labels.
- **Disney+ and Paramount+ cards with long titles failed to redirect** (issue #2,
  reported on-device with the Disney film *Shang-Chi*). A card payload that named
  a recognised provider edge or watch action was capped at 7 words, so the
  eight-word *Shang-Chi and the Legend of the Ten Rings* was rejected outright in
  every shape: `Title. Watch on Disney+.`, `Title, Disney+`, `Disney+. Title.`,
  `Title • Disney+`, `Title. Available on Disney+`. Only the comma-middle shape
  worked, because that path alone allowed 10 words, which is exactly why the
  earlier fixture passed while the card on the TV did not. Card payloads naming a
  provider or watch action now use the 15-word bound already used by the
  entity-detail row and the YouTube card. Payloads with no card evidence keep the
  strict 7-word bound, so a bare long title is still refused, and the
  sentence-case and advertisement guards are untouched.
- **Provider edge parsing widened** for provider-first and provider-trailing
  payload shapes, which is what makes `Disney+. <title>.` and `<title> • Disney+`
  resolve at all.

### Changed
- The version comparison used for update awareness parses the release tag and
  the installed version differently on purpose: a release tag must be a plain
  `X.Y.Z` so a suffixed tag like `v1.2.0-rc1` is never offered as a stable
  update, while the installed version is read through any build suffix so that
  a build such as `1.2.0-testbuild` cannot silently disable update checks.
- README and ROADMAP updated for the v1.2 feature set.

### Verified on-device (2026-09-10, TCL Smart_TV_Pro)
- TizenTube Cobalt divert fires on a real Google TV YouTube card and opens the
  correct search in `io.gh.reisxd.tizentube.cobalt`.
- WuPlay resolves both `wuplay://movie/tt0371746` and
  `wuplay://series/tt1234567` to `app.wuplay.androidtv/.MainActivity`.
- Provider whitelist toggles persist and the summary line matches the toggles.
- Helper regression suite: 423 assertions passing, including the new update,
  whitelist, provider-card and long-title coverage.
- The signed release artifact itself (v1.2.0, `versionCode` 6, R8-shrunk,
  signed with the release key) installs in place over the previous build and the
  accessibility service reconnects, so the shippable APK is verified and not just
  the debug build.

### Not yet verified on-device
- The update prompt and one-tap in-place install have not been exercised end to
  end, because no published release is newer than the installed build. The
  install path depends on the release APK and the installed build sharing a
  signing key, which they do.
- A specific failing Disney+ or Paramount+ title from issue #2 has now been
  reproduced: *Shang-Chi and the Legend of the Ten Rings* was rejected in every
  card shape but one, and all the real shapes are pinned by fixtures, along with
  Netflix and Prime controls and negatives for prose and for titles over 15
  words. The corrected redirect has not yet been watched on the TV for a real
  Shang-Chi card, which is the obvious first check.

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
