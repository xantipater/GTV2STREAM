# GTV2STREAM: Updating to a new version

This guide covers how to update GTV2STREAM when a newer version is released. There
are two routes: the built-in update prompt in the app, or the ADB sideload you
already used to install it.

**Updating keeps your settings and your TMDB key.** Both routes install over the
existing app rather than replacing it, so you do not need to re-enter anything or
re-enable the accessibility service.

Current release: **v1.2.0**. Previous release: v1.1.0. Full version history:
[CHANGELOG](CHANGELOG.md).

## Route 1: update from inside the app (recommended)

1. Open **GTV2STREAM** on the TV.
2. If a newer version exists, a prompt appears: **Update available: GTV2STREAM
   1.2.0**, with three choices:

   - **Download & install 1.2.0** — downloads the release APK and hands it to the
     Android installer.
   - **Open release page** — opens the GitHub release page in the TV browser so
     you can download it yourself.
   - **Later** — dismisses the prompt. The amber notice and both buttons stay on
     the Settings screen, so you can update whenever you want.

3. If you chose **Download & install**, watch the progress line. It reads
   **Downloading update… 47%** and then **Download complete. Confirm installation
   in the system prompt.**
4. Confirm the install in the **Android system installer** prompt. GTV2STREAM
   never installs anything without that confirmation, and there is no way to skip
   it.
5. When it finishes, the install is in place. Reopen GTV2STREAM and check that the
   version at the bottom of the screen matches the new release, and that the
   service still says **Enabled — Ready**.

### If the app asks you to allow installs

Android refuses package installs from an app that is not allowed to install
unknown apps. If that permission is off, GTV2STREAM tells you before it downloads
anything, rather than after, and shows a button:

- Select **Allow installs from GTV2STREAM**, which opens the exact setting page
  for GTV2STREAM.
- Turn on **Allow from this source** (the wording varies by TV build).
- Press back, then select **Download & install** again.

Doing it by hand instead: **Settings > Apps > GTV2STREAM > Install unknown apps >
Allow from this source**.

### Things worth knowing about the in-app route

- **The prompt only appears when you open the app.** Nothing runs in the
  background and no notification is posted, so a version you never open is a
  version you will not be told about. If you want to check manually, open the app
  and look for the amber notice, or open the
  [releases page](https://github.com/xantipater/GTV2STREAM/releases).
- **The check runs at most once every 24 hours**, and only a release newer than
  the version you have is offered. Drafts and prereleases are ignored, so a
  `-beta` or `-rc` tag will never be offered as an update.
- **The prompt appears once per release.** Tapping **Later** silences it for that
  version instead of asking again every time you open the app.
- **The download is verified** before the installer sees it: the file size is
  checked against the release metadata, and the file has to be a valid APK
  archive. A file that fails either check is discarded, not installed.
- **A cancelled or failed install changes nothing.** GTV2STREAM reports
  **Installation cancelled** or **Installation failed** and leaves your installed
  version exactly as it was.

## Route 2: update over ADB

Use this if you prefer the PC route, if your TV blocks the in-app route, or if you
are updating a TV that has no easy way to reach Android settings.

1. On the Windows PC, open the `platform-tools` folder and open a Command Prompt
   in it, exactly as in the [install guide](INSTALL_ADB.md).
2. Connect to the TV. Replace `TV_IP` with your TV's address:

   ```text
   adb connect TV_IP:5555
   ```

   If your TV uses wireless debugging pairing instead, follow
   [Option B in the install guide](INSTALL_ADB.md#option-b-wireless-debugging-pairing)
   and use its separate connection port.

3. Download the new APK from the
   [releases page](https://github.com/xantipater/GTV2STREAM/releases) into the
   same folder as `adb.exe`.
4. Install it over the existing version with `-r`, which keeps your settings and
   your TMDB key:

   ```text
   adb install -r GTV2STREAM-v1.2.0.apk
   ```

   Wait for `Success`.

5. On TCL TVs, re-apply the vendor auto-start permission if the service does not
   reconnect on its own:

   ```text
   appops set com.gtv2stream AUTO_START allow
   ```

6. Open GTV2STREAM and confirm the service status says **Enabled — Ready**.

## After updating

1. Open GTV2STREAM and check the version shown at the bottom of the screen.
2. Confirm the accessibility service still reports **Enabled — Ready**. If it says
   the TV is blocking it, use the **Open app info (allow auto-start)** button.
3. Test both targets with the test buttons, then test one real
   recommendation card on the Google TV home screen.

Your targets, provider whitelist toggles, badge setting and TMDB key all carry
over. If you want to confirm the key survived, a movie card that redirects in
under a second is proof: the lookup needs the key.

## Troubleshooting

### No update prompt appears

The prompt only shows when a release exists that is newer than your installed
version, and only once per release. Check the
[releases page](https://github.com/xantipater/GTV2STREAM/releases) and compare the
version with the one at the bottom of GTV2STREAM's Settings screen. If a newer
release does exist and you have already dismissed the prompt once for it, the
**Download & install** button is still on the Settings screen.

### `INSTALL_FAILED_UPDATE_INCOMPATIBLE` or a signature error

The APK you are installing was not signed with the same key as the version on the
TV, so Android refuses to update it in place. This happens if you built the app
yourself instead of using a release APK. Uninstall GTV2STREAM and install the
release fresh, then re-enter your TMDB key and re-enable the accessibility
service.

### `INSTALL_FAILED_VERSION_DOWNGRADE`

You are installing an older APK than the version already on the TV. Download the
newest release instead. If you specifically need to go backwards, uninstall first.

### The download fails or times out

The messages are specific: **You appear to be offline** means the TV has no
network connection, **Download failed** means the connection dropped, **Download
timed out** means the transfer stalled, and **Download verification failed**
means the file arrived damaged or was not a valid APK. Nothing is installed in any
of those cases. Check the TV's connection and try again.

### The service is enabled but nothing redirects

Confirm the accessibility service still shows as enabled and ready, and check
whether the provider of that card is toggled on in the provider whitelist: a
whitelisted provider keeps normal Google TV behaviour and is deliberately left
alone. Ads, sponsored cards and launcher UI are ignored by design.

### I do not want update prompts at all

There is no setting to disable the check, because it makes no network calls until
you open the app and it never installs anything without your confirmation. If you
never want to see it, update when convenient and the prompt stays silent until the
next release.

## Related

- [Install via ADB on Windows](INSTALL_ADB.md)
- [README](README.md) — behaviour, targets, and setup overview
- [ROADMAP](ROADMAP.md) — what is supported, in progress, and planned
- [CHANGELOG](CHANGELOG.md) — version history
