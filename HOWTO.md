# GTV2STREAM — Full How-To

Everything needed to go from zero to a working build on your own Google TV,
plus the actual war story of how this app got built, debugged, and published.
Every command here was executed for real; the debugging section documents
the exact failures we hit and how each was fixed.

---

## 1. What this app is

GTV2STREAM is an Android TV accessibility service that watches the Google TV
launcher (`com.google.android.apps.tv.launcherx`). When you focus and click a
recommendation card, it:

1. Captures the title from the click event (or, failing that, from the
   detail-screen title row via `TYPE_WINDOW_STATE_CHANGED`).
2. Matches it against TMDB `/3/search/multi` (year-aware where possible).
3. Force-stops Nuvio and relaunches it fresh on the resolved deep link:
   - Movie: `nuvio://movie/<imdb-id>`
   - Series: `nuvio://detail/tv/<imdb-id>`

It hosts, streams, and provides nothing. It is a redirect button with taste.

## 2. Prerequisites

- JDK 17 (`/usr/lib/jvm/java-17-openjdk-amd64` on the build box)
- Android SDK: platform 34, build-tools 34.0.0
- Gradle 8.13 / AGP 8.13.2 (via the bundled wrapper, nothing to install)
- `adb` on PATH
- A Google TV on the same LAN with **Wireless debugging** available
  (Settings → Device Preferences → About → enable Developer options by
  clicking Build number 7 times, then Developer options → Wireless debugging)
- A TMDB v3 API key: free at <https://www.themoviedb.org/settings/api>

## 3. Build environment

```sh
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/path/to/android-sdk

./gradlew clean runHelperTests test lintDebug assembleDebug
```

`runHelperTests` exercises the dependency-free parser, URI builder,
title-selection, false-positive, and normalization checks with no device
attached and no network. Full build output lands at:

```
app/build/outputs/apk/debug/app-debug.apk
```

The build performs no TMDB lookup and needs no secrets. See section 8 for why
`local.properties` and any signing material must never leave the machine.

## 4. TV setup: wireless ADB

On the TV, Wireless debugging shows two things you need:

1. **Pairing** — select "Pair device with pairing code". It shows a 6-digit
   code and a one-time port. Then from the build box:

   ```sh
   adb pair 192.168.0.182:<pairing-port>
   # enter the 6-digit code when prompted (e.g. 056668)
   ```

   The pairing port is *not* the connection port and changes every time you
   open the pairing dialog. The code expires quickly; pair immediately.

2. **Connect** — the Wireless debugging screen also shows the persistent
   `ip:port` (port commonly 5555 on many TVs):

   ```sh
   adb connect 192.168.0.182:5555
   adb devices          # must list the TV as "device"
   ```

Install and launch:

```sh
adb -s 192.168.0.182:5555 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 192.168.0.182:5555 shell monkey -p com.gtv2stream 1
```

`install -r` updates in place and preserves the accessibility-service binding
and stored settings, so you almost never need to uninstall between builds.

## 5. In-app setup

1. Open GTV2STREAM from the launcher.
2. Paste your TMDB v3 key, tap **Save TMDB key**. The key lives only in the
   app's private preferences on the TV. It is never compiled in and never
   committed.
3. Tap **Open Accessibility Settings**, find **GTV2STREAM recommendation
   redirect**, enable it.
4. Back in the app, confirm the status line says the service is enabled.

**Android 13+ restricted settings:** sideloaded accessibility services may be
greyed out. Workaround: Settings → Apps → GTV2STREAM → (three-dot menu) →
*Allow restricted settings*, then return to Accessibility and enable.

**Nuvio side (important):** because every launch is a deliberately *fresh*
Nuvio task (see section 7), a genuinely fresh task shows the "Who's
watching?" profile picker if you have multiple profiles. Enable Nuvio's
**Remember last profile** option once and fresh launches go straight to the
detail screen.

## 6. Smoke tests

Direct URI test (no GTV2STREAM involved, proves the deep link + Nuvio side):

```sh
adb -s 192.168.0.182:5555 shell am start -a android.intent.action.VIEW \
  -d "nuvio://movie/tt0371746"
```

The in-app **Test Nuvio** button fires the same URI through the same
fresh-launch path.

Live service logs (the tag is `GTV2STREAM`):

```sh
adb -s 192.168.0.182:5555 logcat -c
adb -s 192.168.0.182:5555 logcat -s GTV2STREAM
```

Now click two different non-YouTube recommendations on the launcher. You
should see focused-title events and a `START u0 ... dat=nuvio://...` line in
`ActivityTaskManager` for each click.

Deeper inspection tools that earned their keep:

```sh
# is the service actually bound?
adb shell dumpsys accessibility | grep -A5 GTV2STREAM

# what is on screen right now?
adb shell uiautomator dump /sdcard/u.xml && adb pull /sdcard/u.xml

# what is the top activity?
adb shell dumpsys activity activities | grep topResumedActivity
```

## 7. How the redirect works

The service accepts events **only** from `com.google.android.apps.tv.launcherx`
(enforced in `accessibility_service_config.xml` via `android:packageNames`)
and only for `TYPE_VIEW_CLICKED` and `TYPE_WINDOW_STATE_CHANGED`.

**Title capture, in priority order:**

- Click events: read the title directly from `event.getText()`. On real
  Google TV hardware the focused card's title arrives as the first text
  item — there is often no source node, no view ID, and no content
  description to be had. Trust the text.
- Window-state change to a Nuvio-handled entity screen: read the stable view
  ID `entity_details_title_row` from the fresh tree, retrying once after
  600 ms if the row is not laid out yet.

**Fail-closed rules** — the service ignores: UI chrome, metadata-only cards
(no credible title), advertisements, sponsored cards, YouTube actions.
Titles are deduplicated inside a short window so one click cannot fan out
into repeated launches. No accessibility node is retained across events.

**Fresh-task launch (the part everyone gets wrong):** Nuvio keeps its task
stack alive, so a plain `startActivity` drops you back into whatever was on
screen last time. GTV2STREAM therefore:

1. Resolves the actual handler activity for the URI (never assumes a package).
2. Requests a background-process stop for that Nuvio package
   (`KILL_BACKGROUND_PROCESSES` permission).
3. Waits ~200 ms off the service worker thread.
4. Launches the explicit component with `ACTION_VIEW` +
   `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`.
5. Falls back to a generic `ACTION_VIEW` with the same flags if the explicit
   component rejects the launch.

This is intentionally destructive to Nuvio's task state. That is a feature.

## 8. Debugging playbook (real failures, real fixes)

**Symptom: opening random titles not related to what's on screen.**
Cause: trusting stale/weak sources — cached focused titles from earlier
scrolls, content descriptions from ad rows, node text picked up at the wrong
moment. Fix: bind the title to the event itself (first `getText()` item on
click), add the `entity_details_title_row` window-state path, dedup within a
window, and hard-fail on anything that smells like chrome or an ad.

**Symptom: clicks that do nothing.**
Cause: title-row race (tree not laid out when the event fires) and events
arriving before the service re-enabled after reinstall. Fix: the 600 ms
retry against a fresh tree, and always verify the binding after
`install -r`:

```sh
adb shell dumpsys accessibility | grep -i gtv2stream
```

If the bound-service list is empty, toggle the service in Accessibility
settings — do not assume the reinstall re-bound it.

**Symptom: Nuvio opens to the last-watched screen instead of the clicked
title.**
Cause: Nuvio's task was warm, so the intent landed on the existing stack.
Fix: the fresh-task launch sequence in section 7. Nothing else reliably
wipes Nuvio's state, including `am force-stop` from the shell in every case
we tested.

**Symptom: "Who's watching?" appears on every launch.**
Not a bug: it is the honest result of a genuinely fresh task. Enable
*Remember last profile* in Nuvio.

**General rules that saved the night:**

- Watch `logcat -s GTV2STREAM` while clicking; never trust a build you have
  not seen fire on the real launcher.
- `uiautomator dump` is ground truth for what the launcher is showing.
- After every `install -r`, re-check the accessibility binding.
- `am start -W` surfaces launch failures that a fire-and-forget start hides.

## 9. The clean-room method

This implementation was written against **behavior**, not against anyone's
source:

- Observed the reference app (TvReccomendationBridge, via its public release
  page) end to end on real hardware to learn *what* it does.
- Inspected Google TV's own event payloads on-device to learn what the
  launcher actually emits (the surprising part: click events carry the title
  as plain text with no source node).
- Confirmed Nuvio's deep-link contract from Nuvio itself
  (`nuvio://movie/<imdb>` and `nuvio://detail/tv/<imdb>`), not from guesswork.

No source, assets, or line structure was copied from the reference app.
When you extend GTV2STREAM, keep it that way: capture behavior as a spec,
write your own code.

## 10. Publishing to GitHub (exactly how this repo went up)

The build box here is headless, so everything is CLI. Install `gh` (apt's
copy is ancient; grab the current binary into `~/.local/bin`), then:

```sh
# device-flow login — no browser on the box needed
gh auth login --hostname github.com --git-protocol https --web
#   -> prints a one-time code (e.g. 98DA-DB5B) and https://github.com/login/device
#   -> open the URL anywhere, enter the code, authorize
```

Then wire git up to gh's credential helper and set a commit identity:

```sh
gh auth setup-git
git config --global user.name  "Antipater"
git config --global user.email "xantipater@users.noreply.github.com"
```

Use the `users.noreply.github.com` address; it keeps your real email out of
the public commit log.

**Account-rename gotcha:** gh caches your username at login. If you later
rename the GitHub account, `gh auth status` may keep printing the old name
and `gh auth switch` will refuse. The token still works — it resolves to the
current name server-side. `gh auth setup-git` is what actually matters for
pushes; the stale label is cosmetic.

**Secret sweep before the first push (do not skip):**

```sh
grep -rn --include='*.java' --include='*.xml' --include='*.gradle' \
  --include='*.properties' -i 'api_key\|apikey\|token\|secret\|password' . \
  | grep -v build/
git check-ignore local.properties build .gradle   # each must echo back = ignored
```

`.gitignore` already excludes `local.properties` (SDK path + anything local),
`**/build/`, keystores, and signing configs. If a key was ever baked into an
earlier build, rebuild from the current source before publishing.

**Create and push in one shot:**

```sh
git add -A
git commit -m "Initial release: ..."
gh repo create GTV2STREAM --public --source=. --remote=origin --push
```

**Verify it actually landed (trust, then publish):**

```sh
gh repo view --json url,visibility,defaultBranchRef
gh api repos/<owner>/<repo>/commits/main --jq '.sha[0:7],.commit.message'
curl -sL https://raw.githubusercontent.com/<owner>/<repo>/main/README.md | head
```

## 11. Release checklist

- [ ] `./gradlew clean runHelperTests test lintDebug assembleDebug` green
- [ ] No secrets in tree: sweep per section 10
- [ ] Fresh install on the TV (`install -r`), accessibility binding verified
- [ ] Two different non-YouTube recommendations clicked, both open the right
      title fresh in Nuvio (watch `logcat -s GTV2STREAM`)
- [ ] Wrong-title regression check: click an ad/sponsored card, confirm
      nothing launches
- [ ] Stale-state check: open Nuvio to some detail screen, back out to the
      launcher, click a different recommendation, confirm the clicked title
      opens (not the previous one)
- [ ] Release APK built, sha256 recorded
- [ ] Pushed, then verified on the remote, not just locally

## 12. How this actually went (compressed)

For context, the real sequence that produced this repo — rough
timing, both nights combined:

1. **Zip arrives broken.** A previous build (different tool, different
   model) opened stale Nuvio state on every click. Named GTV2STREAM,
   told to make Nuvio work, period.
2. **First pass failed on real hardware.** Wrong titles, missed clicks,
   stale state. adb wireless pairing (pair code + connect) went fine; the
   app logic did not.
3. **Behavioral spec, not line-copy.** Cloned and decompiled the reference
   app and Nuvio itself to learn the deep-link contract and event shapes;
   wrote GTV2STREAM's logic from that spec.
4. **Root causes found:** stale cached titles (fixed by event-text-first
   capture + fail-closed rules), tree races (fixed by the 600 ms retry),
   warm Nuvio task (fixed by kill + `CLEAR_TASK` fresh launch).
5. **Live verification:** two manual clicks under `logcat`, both opened the
   correct title fresh. The "working pretty nicely??" moment.
6. **Secrets stripped, tree swept,** device-flow GitHub auth from a headless
   box (the account had been renamed, which confused nothing but gh's cached
   label), `gh repo create --push`, remote verified with `gh api` and a raw
   fetch of the README.

Total: one evening of failure, one night of clean-room rebuild, working
build, public repo. The debugging playbook in section 8 is the part worth
stealing.
