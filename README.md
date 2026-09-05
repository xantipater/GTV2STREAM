# GTV2STREAM

GTV2STREAM is a small, independent Android TV companion that redirects a selected Google TV launcher recommendation to Nuvio. It does not host, stream, or provide media.

## Why install with ADB?

Some Android/Google TV builds block accessibility services for sideloaded apps. Some also do not provide a usable restricted-settings toggle. On those affected builds, install the APK with [Windows over-network ADB](INSTALL.md#option-a-windows-over-network-adb) instead. ADB is not required on every device. If your device lets you enable the service normally, use Downloader or a USB stick as described in [INSTALL.md](INSTALL.md).

## Behavior

The accessibility service accepts only these events from the Google TV `launcherx` package:

- `TYPE_VIEW_CLICKED`: reads a credible title directly from the event text/content description.
- `TYPE_WINDOW_STATE_CHANGED`: when the entity activity appears, reads the stable `entity_details_title_row` view ID.

If the title row is not ready, the service retries against a fresh accessibility tree after 600 ms. It fails closed for UI chrome, metadata-only cards, advertisements, sponsored cards, and YouTube actions. Titles are deduplicated within a short event window; no accessibility node is retained across events.

A matching title is looked up through TMDB `/3/search/multi`, with optional year-aware matching. The result's IMDb identifier is then sent to Nuvio using exactly:

- Movie: `nuvio://movie/<imdb-id>`
- Series: `nuvio://detail/tv/<imdb-id>`

Every resolved recommendation uses a deliberately fresh Nuvio task: GTV2STREAM resolves the actual handler activity, requests Android's background-process stop for that Nuvio package, waits about 200 ms off the service worker thread, and launches the explicit component with `ACTION_VIEW`, `NEW_TASK`, and `CLEAR_TASK`. If that resolved component rejects the launch, it retries the same URI with a generic `ACTION_VIEW` carrying the same fresh-task flags. This is intentionally destructive to the Nuvio task state. Users with multiple Nuvio profiles may want Nuvio's **Remember last profile** option enabled, because a genuinely fresh task can show profile selection by design. The Settings test button follows the same fresh-launch behavior.

## Setup on Google TV / Android TV

1. Create your own TMDB v3 API key at <https://www.themoviedb.org/settings/api>.
2. Build and sideload the debug APK; this project makes no Play Store support claim.
3. Open GTV2STREAM, paste the key, and tap **Save TMDB key**. The key is stored only in the app's private local preferences and is never committed to source.
4. Tap **Open Accessibility Settings**, select **GTV2STREAM recommendation redirect**, and enable it.
5. Return to GTV2STREAM and confirm the visible service status says enabled and ready.

On Android 13 or newer, some builds block accessibility services for sideloaded apps and do not expose a usable restricted-settings toggle. On those devices, use the [Windows over-network ADB installation route](INSTALL.md#option-a-windows-over-network-adb), then reopen Accessibility Settings and enable GTV2STREAM.

## Install (users)

See [INSTALL.md](INSTALL.md) for a step-by-step install guide: sideload the APK, add your TMDB key, enable the accessibility service, and enable Nuvio's **Remember last profile**.

## Test the Nuvio URI directly

The in-app test button uses the known movie identifier `tt0371746`. A direct URI smoke test, when run on an Android TV test device with Nuvio installed, is:

```sh
adb shell am start -a android.intent.action.VIEW -d "nuvio://movie/tt0371746"
```

If Nuvio is not installed or does not advertise the URI handler, GTV2STREAM shows a status Toast rather than assuming a package name.

## License

MIT. See [LICENSE](LICENSE).
