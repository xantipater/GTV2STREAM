# GTV2STREAM

GTV2STREAM is a small, independent Android TV companion that redirects a selected Google TV launcher recommendation to Nuvio. It does not host, stream, or provide media.

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

On Android 13 or newer, sideloaded accessibility services may require Android's restricted-settings workaround. Follow the Android TV/device instructions for allowing the service, then reopen Accessibility Settings and enable GTV2STREAM.

## Build and tests

Requirements: JDK 17, Android SDK platform 34, and build-tools 34.0.0. The included Gradle wrapper uses Gradle 8.13 and AGP 8.13.2.

```sh
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/home/server/android-sdk
./gradlew clean runHelperTests test lintDebug assembleDebug
```

`runHelperTests` exercises the dependency-free parser, URI, title-selection, false-positive, and normalization checks. The build performs no TMDB/network lookup. APK output is `app/build/outputs/apk/debug/app-debug.apk`.

## Test the Nuvio URI directly

The in-app test button uses the known movie identifier `tt0371746`. A direct URI smoke test, when run on an Android TV test device with Nuvio installed, is:

```sh
adb shell am start -a android.intent.action.VIEW -d "nuvio://movie/tt0371746"
```

If Nuvio is not installed or does not advertise the URI handler, GTV2STREAM shows a status Toast rather than assuming a package name.

## Clean-room note

GTV2STREAM is an independent clean-room implementation. It uses publicly observable Android interoperability behavior and behavioral inspiration from the [TvReccomendationBridge release page](https://github.com/Yushetf33/TvReccomendationBridge/releases/latest), but does not copy its source, assets, or line structure. GTV2STREAM is not affiliated with Google, Nuvio, or TvReccomendationBridge.

## License

MIT. See [LICENSE](LICENSE).
