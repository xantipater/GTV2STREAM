# Contributing to GTV2STREAM

Thanks for looking at the project! GTV2STREAM is a small Android TV app that
turns Google TV launcher recommendations into fresh Nuvio launches. Any help
is welcome, but read this first so your PR doesn't bounce.

## Ground rules

- **This is a clean-room project.** Nothing may be copied from any other
  recommendation-bridge app, its code, or its decompiled output. If you've
  been reading someone else's implementation, wait 48 hours and write from
  behavior notes only. Behavior is the spec, not code.
- **No secrets, ever.** The TMDB API key is entered by the user at runtime in
  the app's Settings screen. Never commit a key, token, or any credential to
  the repo, the wiki, or screenshots. PRs containing secrets get closed.
- **Target the debug flavor.** Development happens against the debug build
  (`com.nuviodebug.com` on-device is Nuvio's debug build). Don't submit
  release-signed builds or keystore files.

## Getting started

1. **Fork, then clone your fork.**
   ```bash
   git clone https://github.com/<you>/GTV2STREAM.git
   cd GTV2STREAM
   ```
2. **Open in Android Studio** (or build via Gradle: `./gradlew assembleDebug`).
   Requires JDK 17 and the Android SDK (platform 34).
3. **Create a branch** for your change: `git checkout -b my-fix`.
4. **Build first** before you edit, so you know your toolchain works:
   ```bash
   ./gradlew assembleDebug
   ```
5. **Make your change**, rebuild, and install on a device/emulator:
   ```bash
   ./gradlew installDebug
   ```
6. **Commit with a short imperative subject line**, e.g.
   `Fix title parser stripping trailing punctuation`.

## Pull requests

- Keep PRs small. One fix or one feature per PR.
- **Explain the behavior, not the code.** Describe what the app should do
  before/after your change. Screenshots or short screen recordings of the
  launcher → Nuvio flow are gold; a PR that says "trust me" isn't.
- Fill in the PR description: what, why, how tested.
- New files get the MIT header comment where one is already used (be
  consistent with the file you're editing).
- Do **not** bump versionCode/versionName; the maintainer handles releases.

## Code style

- Java 17, no new dependencies without asking first.
- Match the existing formatting of the file you touch.
- Unit-test pure logic where practical. The title parser and deep-link
  builder already have tests — extend them if you're touching that logic.

## Reporting bugs

Open a GitHub issue with:

- GTV2STREAM version (Settings screen shows it) **or** release tag (e. g. v1.0.0)
- Android/Google TV version (e.g. Android 14)
- What you clicked, what you expected, what happened
- `adb logcat` output filtered to the app if you can (attach as a file and
  redact your TMDB key):
  ```bash
  adb logcat GTV2STREAM:V ActivityTaskManager:I '*:S' > log.txt
  ```
