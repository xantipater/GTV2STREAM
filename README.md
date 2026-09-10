# GTV2STREAM

![GTV2STREAM](.github/assets/banner.svg)

GTV2STREAM is a small, independent Android TV companion that redirects a selected Google TV launcher recommendation to your chosen app. Film and series titles open in Nuvio, Stremio or WuPlay; YouTube cards open as a title search in SmartTube or TizenTube Cobalt. It does not host, stream, or provide media.

**Current release: v1.2.0** (previous release: v1.1.0). Version history: [CHANGELOG](CHANGELOG.md). What is supported, in progress, and planned: [ROADMAP](ROADMAP.md).

## Install

- **[Install via ADB on Windows](INSTALL_ADB.md)**
- **[Updating to a new version](UPDATE.md)**
- **[Roadmap: supported, in progress, planned](ROADMAP.md)**

ADB installation is required. It installs the APK and applies the accessibility
service setup needed on TVs that restrict sideloaded accessibility apps.

## Behavior

The accessibility service accepts only these events from the Google TV `launcherx` package:

- `TYPE_VIEW_CLICKED`: reads a credible title directly from the event text/content description.
- `TYPE_WINDOW_STATE_CHANGED`: when the entity activity appears, reads the stable `entity_details_title_row` view ID.

If the title row is not ready, the service retries against a fresh accessibility tree after 250 ms, with a final attempt at 600 ms. It fails closed for UI chrome, metadata-only cards, advertisements, and sponsored cards. Titles are deduplicated within a short event window; no accessibility node is retained across events. The launcher's full UI vocabulary is excluded, including navigation tabs, row labels, the quick-settings sheet, device-preferences tree, edit-mode verbs, toggle labels, input/port labels, and price actions, so a UI element whose label coincides with a real film title is never opened. Fail-closed wins over the rare title miss.

Provider payload handling is shape-based and provider-agnostic: action suffixes ("Watch on", "Watch Now on", "Stream on", "Streaming on", "New on", "Included with") are stripped, and a known provider name in a leading or trailing segment position is recognized across period, comma, bullet, and dash separators (e.g. "Title. Watch on Paramount+.", "Netflix. Title.", "Title, Disney+", "Title — Prime Video"). Provider names never leak into a returned title.

Titles are also read from the card the user actually focused, rather than only from the launcher's ambient panel text. Google TV exposes grid rows such as "Top picks for you" as position labels (`Column 3`) rather than titles, so a focused card is remembered when it is seen and reused when the click arrives; a card title carries more weight than ambient panel text, and a poll that returns no readable title can no longer discard a title that was just captured. Position labels and other launcher chrome are never treated as titles.

### Targets

**TV & movies target** (Nuvio by default, or Stremio or WuPlay): a matching title is looked up through TMDB `/3/search/multi`, with optional year-aware matching. The result's IMDb identifier is then sent to the selected target using exactly:

- Nuvio — Movie: `nuvio://movie/<imdb-id>` · Series: `nuvio://detail/tv/<imdb-id>`
- Stremio — Movie: `stremio:///detail/movie/<imdb-id>` · Series: `stremio:///detail/series/<imdb-id>`
- WuPlay — Movie: `wuplay://movie/<imdb-id>` · Series: `wuplay://series/<imdb-id>`

**YouTube target** (SmartTube, or TizenTube Cobalt): a launcher payload whose action suffix is "Watch on YouTube" (including "Stream on YouTube" and provider-first `YouTube` items) is classified as YouTube content. It skips the TMDB lookup entirely, so no key is needed.

- **SmartTube** opens `https://www.youtube.com/results?search_query=<cleaned title>`. The stable package is tried first, followed by the beta and legacy packages.
- **TizenTube Cobalt** is launched with `android.media.action.MEDIA_PLAY_FROM_SEARCH` addressed at the explicit Cobalt component, flagged so the query reaches the live instance (`NEW_TASK|MULTIPLE_TASK|EXCLUDE_FROM_RECENTS`). Cobalt has no reliable external `VIEW` contract, and tearing its task down loses the query on a warm instance, which is why the warm-start flags matter. If Cobalt is not installed, GTV2STREAM fails visibly with a status message instead of silently opening SmartTube, because your explicit target choice is honoured.

Both YouTube paths prefill a title search. They do not pull the exact video automatically the way the native YouTube app does.

Sponsored and advertisement payloads are still rejected outright. YouTube card titles are accepted up to 15 words, because video titles genuinely run long ("Gemini 3.8 Flash Is HERE – Testing Google's BEST Model Yet!") and the card is only ever routed to a YouTube search, never to TMDB. Any card payload that names a recognised provider or watch action ("Shang-Chi and the Legend of the Ten Rings. Watch on Disney+.") is also accepted up to 15 words, because real film titles run long and an eight-word title was previously being rejected outright on every card shape except one. Payloads that carry no card evidence at all, such as node text and ambient panel values, stay bounded at 7 words. In both cases the sentence-case, internal-period, advertisement, rating-metadata and UI-chrome guards do the actual prose rejection, and a bare long title with no provider attached is still refused.

**Brief flash when redirecting YouTube**: the launcher itself starts the stock YouTube app with an explicit, package-targeted intent when a YouTube card is clicked, and that launch cannot be intercepted. GTV2STREAM diverts it a fraction of a second later, so you may see a short flash of the stock YouTube app before your chosen YouTube target opens. This is inherent to how the launcher fires that click, not a bug.

### Fresh-task launches

Every resolved recommendation launches the explicit target component with `ACTION_VIEW`, `NEW_TASK`, and `CLEAR_TASK`. Nuvio, Stremio, WuPlay and SmartTube accept this reliably while their process remains warm, which avoids an unnecessary cold start. If a resolved component rejects the launch, GTV2STREAM retries the same URI scoped to the target package (fully generically for the Nuvio scheme, whose `nuvio://` URIs can only resolve Nuvio handlers).

To keep redirects snappy, recent TMDB matches are kept in a 32-entry in-memory cache keyed by normalized title. Re-selecting the same card within 24 hours launches from the cached match without any network call. The cache is memory-only: nothing is stored on disk, and it resets when the service restarts.

If the selected target app is not installed, GTV2STREAM shows a message instead of launching blindly.

### Provider whitelist

The launcher's recommendation cards come from many providers, and not everyone wants every card redirected. Settings carries a whitelist with 26 providers (Netflix, Prime Video, Disney+, ITVX, BBC iPlayer, Hulu, Max, HBO Max, Paramount+, Apple TV+, YouTube, Google TV, Peacock, Channel 4, My5, Starz, Showtime, AMC+, Discovery+, MGM+, Britbox, Shudder, Tubi, Pluto TV, Freevee, Crunchyroll).

- Every provider is **off** by default, so an existing install behaves exactly as it did before the whitelist existed.
- A whitelisted provider's cards **skip GTV2STREAM entirely** and keep their normal Google TV behaviour, as if the app were not installed.
- Provider identity comes from the card's provider edge or watch action, never from the title text, and provider aliases collapse to one identity ("disney plus" and "Disney+" are the same switch), so toggling a provider can't accidentally match a film whose name looks like a brand.
- The whitelist summary line in Settings always shows what is currently being skipped.

### In-app updates

GTV2STREAM checks the GitHub releases API for a newer stable release, at most once every 24 hours. Drafts and prereleases are ignored, as is any release that is not newer than the version you have installed. Nothing is downloaded or installed in the background, and no update check is made if you never open the app.

When a newer release exists and you open the app, you get a prompt: **Download & install**, **Open release page**, or **Later**. The prompt appears once per release, so it cannot become a nag; tapping Later leaves the amber notice and both buttons in Settings, and the prompt stays quiet for that version.

The download **and install** button is offered when the release has an APK attached. It downloads the APK, verifies its size and that it is a valid APK archive, and then hands it to the Android system installer, which always asks you to confirm the install. GTV2STREAM never installs anything silently. Progress and outcomes are reported in Settings ("Downloading update… 47%", "Update installed", "Installation cancelled", and so on), and a cancelled or failed install leaves the installed app untouched.

Android needs **Install unknown apps** allowed for GTV2STREAM before it will accept the package. That permission is checked *before* the download starts, so you are not made to wait through a transfer only to be told afterwards. If it is off, the app offers a button that opens that exact setting page for GTV2STREAM.

Updating in place keeps your settings and your TMDB key, and requires the release APK to be signed with the same key as your installed build, which it is if you installed from the release page. If you built the APK yourself with your own signing key, Android will refuse an in-place update and you will need to uninstall and reinstall.

### Redirect badge

After every successful redirect, a small GTV2STREAM logo badge appears briefly at the top right of the screen. It is an application overlay window, so it requires the **Display over other apps** permission (one tap from the Settings screen); without that permission redirects work normally and the badge simply does not appear. The badge is non-focusable and non-touchable and never steals input from the launched app, and it is shown ~300 ms after the launch so it only ever draws over the freshly opened target app, never over the launcher. A Settings toggle (**Show redirect badge**) turns it off entirely.

### TV auto-start protection (TCL and similar)

TCL TV builds ship a vendor auto-start firewall that can refuse to connect the accessibility service even when it is enabled. The app's status detects this and reports "Blocked by the TV's auto-start protection" with a one-tap fix (allow auto-start in the TV's app info). On the ADB route the equivalent command is `appops set com.gtv2stream AUTO_START allow`.

## Setup on Google TV / Android TV

For the released APK, follow the [Windows ADB installation guide](INSTALL_ADB.md).
It includes APK installation, TMDB key setup, target selection, accessibility
service setup, usage, testing, and troubleshooting.

The basic setup is:

1. Install the released APK using the ADB guide above. End users do not need
   to build from source.
2. Create your own TMDB v3 API key at
   <https://www.themoviedb.org/settings/api>.
3. Open GTV2STREAM, enter the key, and select **Save TMDB key**. The key is
   stored only in the app's private local preferences and is never committed
   to source.
4. Pick your **TV & movies target** (Nuvio, Stremio or WuPlay), and your
   **YouTube target** (SmartTube or TizenTube Cobalt).
5. Select **Open Accessibility Settings**, choose **GTV2STREAM recommendation
   redirect**, and enable it.
6. Return to GTV2STREAM and confirm that the visible service status says
   enabled and ready.
7. Optional: toggle any providers you want left alone under the whitelist.
8. If you use multiple Nuvio profiles, enable **Remember last profile** in
   Nuvio.
9. Optional: select **Allow display over other apps (redirect badge)**.

Developers may build from source for development. See [CONTRIBUTING.md](CONTRIBUTING.md)
for the optional build workflow.

## Test the redirect targets directly

The in-app test buttons follow the same fresh-launch behavior as real
redirects, and they name the app they will use. The movie probe uses the known
identifier `tt0371746` (Nuvio, Stremio or WuPlay, per the selected target); the
YouTube probe searches your selected YouTube target for "Big Buck Bunny". Direct
URI smoke tests, when run on an Android TV test device with a target installed,
are:

```sh
adb shell am start -a android.intent.action.VIEW -d "nuvio://movie/tt0371746"
adb shell am start -a android.intent.action.VIEW -d "stremio:///detail/movie/tt0371746"
adb shell am start -a android.intent.action.VIEW -d "wuplay://movie/tt0371746"
adb shell am start -a android.intent.action.VIEW -d "https://www.youtube.com/results?search_query=Big%20Buck%20Bunny"
```

There is no equivalent external smoke test for TizenTube Cobalt: it exposes no
reliable `VIEW` contract, so use the in-app YouTube test button to check it.

If a target app is not installed or does not advertise a handler, GTV2STREAM
shows a status message rather than assuming a package name.

## Privacy

No accounts, no servers, no analytics, no telemetry. Your TMDB key and all
settings live in this app's private local preferences. The only network calls
made are the TMDB title lookup, a check of the public GitHub releases API for
update awareness, and the update download itself if you ask for it.

## License

MIT. See [LICENSE](LICENSE).
