# GTV2STREAM

![GTV2STREAM](.github/assets/banner.svg)

GTV2STREAM is a small, independent Android TV companion that redirects a selected Google TV launcher recommendation to your chosen app. Film and series titles open in Nuvio or Stremio; YouTube cards open as a title search in TizenTube Cobalt or SmartTube. It does not host, stream, or provide media.

## Install

- **[Install normally (Downloader or USB)](INSTALL.md)**
- **[Install via ADB (Windows)](INSTALL_ADB.md)**

Some Android/Google TV builds restrict accessibility services for sideloaded
apps, so the ADB route may be needed when the required accessibility restricted
setting cannot be enabled normally. If normal installation works, use the
Downloader or USB guide.

## Behavior

The accessibility service accepts only these events from the Google TV `launcherx` package:

- `TYPE_VIEW_CLICKED`: reads a credible title directly from the event text/content description.
- `TYPE_WINDOW_STATE_CHANGED`: when the entity activity appears, reads the stable `entity_details_title_row` view ID.

If the title row is not ready, the service retries against a fresh accessibility tree after 600 ms. It fails closed for UI chrome, metadata-only cards, advertisements, and sponsored cards. Titles are deduplicated within a short event window; no accessibility node is retained across events.

Provider payload handling is shape-based and provider-agnostic: action suffixes ("Watch on", "Watch Now on", "Stream on", "Streaming on", "New on", "Included with") are stripped, and a known provider name in a leading or trailing segment position is recognized across period, comma, bullet, and dash separators (e.g. "Title. Watch on Paramount+.", "Netflix. Title.", "Title, Disney+", "Title — Prime Video"). Provider names never leak into a returned title.

### Targets

**TV & movies target** (Nuvio by default, or Stremio): a matching title is looked up through TMDB `/3/search/multi`, with optional year-aware matching. The result's IMDb identifier is then sent to the selected target using exactly:

- Nuvio — Movie: `nuvio://movie/<imdb-id>` · Series: `nuvio://detail/tv/<imdb-id>`
- Stremio — Movie: `stremio:///detail/movie/<imdb-id>` · Series: `stremio:///detail/series/<imdb-id>`

**YouTube target** (TizenTube Cobalt by default, or SmartTube): a launcher payload whose action suffix is "Watch on YouTube" (including "Stream on YouTube" and provider-first `YouTube` items) is classified as YouTube content. It skips the TMDB lookup entirely — no key is needed — and opens `https://www.youtube.com/results?search_query=<cleaned title>` in the selected app. Cobalt is launched through its fixed `dev.cobalt.app.MainActivity` component; SmartTube is tried on its stable package and then its beta package. Sponsored and advertisement payloads are still rejected outright.

### Fresh-task launches

Every resolved recommendation uses a deliberately fresh task: GTV2STREAM resolves the actual handler activity, requests Android's background-process stop for that target package, waits about 200 ms off the service worker thread, and launches the explicit component with `ACTION_VIEW`, `NEW_TASK`, and `CLEAR_TASK`. If that resolved component rejects the launch, it retries the same URI scoped to the target package (fully generically for the Nuvio scheme, whose `nuvio://` URIs can only resolve Nuvio handlers). This is intentionally destructive to the target app's task state.

To keep redirects snappy, recent TMDB matches are kept in a small in-memory cache keyed by normalized title. Re-selecting the same card within five minutes launches from the cached match without any network call (useful when retrying a failed launch or returning after closing the target app). The cache is memory-only: nothing is stored on disk, and it resets when the service restarts.

For Cobalt this matters more than for scheme-owned URIs: `CLEAR_TASK` alone leaves a cached process alive, and Cobalt's warm-start deep-link delivery depends on YouTube's closed web app. Killing the process forces a true cold start, so the web app consumes the link through the designed-for-voice-search `h5vcc.runtime.getInitialDeepLink` contract — the same path certified YouTube TV voice search uses. Users with multiple Nuvio profiles may want Nuvio's **Remember last profile** option enabled, because a genuinely fresh task can show profile selection by design. The Settings test buttons follow the same fresh-launch behavior.

If the selected target app is not installed, GTV2STREAM shows a message instead of launching blindly.

### Redirect badge

After every successful redirect, a small GTV2STREAM logo badge appears briefly at the top right of the screen. It is an application overlay window, so it requires the **Display over other apps** permission (one tap from the Settings screen); without that permission redirects work normally and the badge simply does not appear. The badge is non-focusable and non-touchable and never steals input from the launched app.

## Setup on Google TV / Android TV

For the released APK, start with either the [normal installation guide](INSTALL.md)
or the [Windows ADB installation guide](INSTALL_ADB.md). The guides include the
APK installation, TMDB key, target selection, accessibility service, usage,
test, and troubleshooting steps.

The basic setup is:

1. Install the released APK using one of the two guides above. End users do
   not need to build from source.
2. Create your own TMDB v3 API key at
   <https://www.themoviedb.org/settings/api>.
3. Open GTV2STREAM, enter the key, and select **Save TMDB key**. The key is
   stored only in the app's private local preferences and is never committed
   to source.
4. Pick your targets with the two selector buttons: **TV & movies target**
   (Nuvio or Stremio) and **YouTube target** (TizenTube Cobalt or SmartTube).
5. Select **Open Accessibility Settings**, choose **GTV2STREAM recommendation
   redirect**, and enable it.
6. Return to GTV2STREAM and confirm that the visible service status says
   enabled and ready.
7. If you use multiple Nuvio profiles, enable **Remember last profile** in
   Nuvio.
8. Optional: select **Allow display over other apps (redirect badge)**.

Developers may build from source for development. See [CONTRIBUTING.md](CONTRIBUTING.md)
for the optional build workflow.

## Test the redirect targets directly

The in-app test buttons follow the same fresh-launch behavior as real
redirects. The movie probe uses the known identifier `tt0371746` (Nuvio or
Stremio, per the selected target); the YouTube probe searches for "Big Buck
Bunny" (Cobalt or SmartTube, per the selected target). Direct URI smoke
tests, when run on an Android TV test device with a target installed, are:

```sh
adb shell am start -a android.intent.action.VIEW -d "nuvio://movie/tt0371746"
adb shell am start -a android.intent.action.VIEW -d "stremio:///detail/movie/tt0371746"
adb shell am start -a android.intent.action.VIEW -d "https://www.youtube.com/results?search_query=Big%20Buck%20Bunny"
```

If a target app is not installed or does not advertise a handler, GTV2STREAM
shows a status message rather than assuming a package name.

## License

MIT. See [LICENSE](LICENSE).
