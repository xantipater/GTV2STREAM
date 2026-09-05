# GTV2STREAM: Normal installation

This guide installs the released GTV2STREAM APK without ADB. It covers both
Downloader and USB installation. After the APK is installed, it explains the
TMDB key, accessibility service, Nuvio profile, and a complete test.

## Why some TVs need the ADB guide

Some Android TV builds restrict accessibility services for sideloaded apps.
Those builds may not let you enable the required accessibility restricted
setting through the normal installation path. If normal installation leaves
the service unavailable, use the standalone [Install via ADB (Windows)
guide](INSTALL_ADB.md). ADB is not required on every device, and it is not
guaranteed to work on every Android build.

## What you need

- A Google TV or Android TV with the Google TV home launcher.
- Nuvio installed on the TV.
- An internet connection on the TV.
- The released APK:
  [GTV2STREAM-v1.0.0.apk](https://github.com/xantipater/GTV2STREAM/releases/download/v1.0.0/GTV2STREAM-v1.0.0.apk)
- Your own free TMDB v3 API key from
  <https://www.themoviedb.org/settings/api>. GTV2STREAM does not provide a
  shared key.

Use Downloader if your TV has the Play Store and internet access. Use the USB
method if you can copy the APK to a USB stick and your TV supports USB storage.
Downloader needs no computer. The USB method needs a computer or another
device that can download and copy the APK.

## Option 1: Install with Downloader

Downloader is the simplest normal installation route. Menu names can vary a
little between Google TV and Android TV versions.

1. On the TV, open the Play Store.
2. Search for **Downloader by AFTVnews**, then install the official Downloader
   app.
3. Open Downloader.
4. Enter this complete APK URL in Downloader. Enter the full address, not a
   shortened code:

   ```text
   https://github.com/xantipater/GTV2STREAM/releases/download/v1.0.0/GTV2STREAM-v1.0.0.apk
   ```

5. Start the download and wait for the APK installer to appear.
6. If Android asks whether Downloader may install unknown apps, open the
   requested setting, allow Downloader, and return to the installer. This is
   the ordinary APK installation permission. It is separate from the
   accessibility restricted setting discussed above.
7. Select **Install** and wait for the installation to finish.
8. Select **Open** to launch GTV2STREAM. If you close the installer instead,
   open GTV2STREAM from the TV app list.

## Option 2: Install from a USB stick

Use this method only with a TV that supports USB storage and a file manager
that can open APK files.

1. On a computer or another device that can download files, open the
   [GTV2STREAM releases page](https://github.com/xantipater/GTV2STREAM/releases)
   or download the direct APK from:

   ```text
   https://github.com/xantipater/GTV2STREAM/releases/download/v1.0.0/GTV2STREAM-v1.0.0.apk
   ```

2. Download `GTV2STREAM-v1.0.0.apk`.
3. Copy the APK to a USB stick, then safely eject the stick from the computer
   or other device.
4. Connect the USB stick to a USB port on the TV. If the TV does not support
   USB storage, use Downloader or the [ADB guide](INSTALL_ADB.md) instead.
5. On the TV, install and open a file manager that can read USB storage, such
   as **X-plore**, if one is not already installed.
6. In the file manager, browse to the USB stick and select
   `GTV2STREAM-v1.0.0.apk`.
7. If Android asks whether the file manager may install unknown apps, open the
   requested setting, allow that file manager, and return to the installer.
   This permission is for installing the APK and is not the accessibility
   restricted setting.
8. Select **Install**, wait for the installation to finish, and select
   **Open**. You can remove the USB stick after the install is complete.

## Configure GTV2STREAM after installation

Complete these steps after either normal installation method.

1. Open **GTV2STREAM** from the TV app list.
2. Enter your own TMDB **v3 API key**, not a v4 read access token. Create or
   retrieve it at <https://www.themoviedb.org/settings/api>.
3. Select **Save TMDB key**. The key is stored privately in the app's local
   storage on this TV. It is not shared with this project or committed to
   source.
4. Select **Open Accessibility Settings**.
5. Select **GTV2STREAM recommendation redirect** and enable it.
6. Return to GTV2STREAM and confirm that the service status says it is enabled
   and ready.
7. If you use more than one Nuvio profile, open Nuvio and enable **Remember
   last profile**. GTV2STREAM deliberately starts Nuvio in a fresh task, so
   this setting prevents the profile picker from appearing on every launch.

If the service cannot be enabled, follow the complete [Install via ADB
(Windows) guide](INSTALL_ADB.md).

## Use and test GTV2STREAM

1. Return to the Google TV home screen.
2. Focus a movie recommendation card and select it. A matched movie should
   open in Nuvio.
3. Repeat with a series recommendation card. A matched series should open in
   Nuvio.
4. You can also use the test button in GTV2STREAM's settings. It tests a
   known movie and follows the same fresh Nuvio launch behavior.
5. The links GTV2STREAM sends to Nuvio have these forms:

   - Movie: `nuvio://movie/<imdb-id>`
   - Series: `nuvio://detail/tv/<imdb-id>`

   For example, a movie link is `nuvio://movie/tt0371746`, and a series link
   is `nuvio://detail/tv/tt0944947`.

GTV2STREAM ignores advertisements, sponsored cards, and YouTube items. Test
with a real movie or series card on the Google TV home screen rather than an
advertisement or a card inside YouTube.

## Troubleshooting

### Accessibility service is unavailable or cannot be enabled

Some TV builds restrict accessibility for sideloaded apps. Use the standalone
[Install via ADB (Windows) guide](INSTALL_ADB.md). ADB is a separate install
route and is not guaranteed on every Android build.

### TMDB key is rejected

Confirm that you entered your own TMDB **v3 API key**, which is the API key
value from the TMDB API settings page. Do not enter a TMDB v4 read access
token. Save the key again after copying it carefully.

### The service is off or nothing happens when a card is selected

Open GTV2STREAM and check that its status says the accessibility service is
enabled and ready. If it is off, return to Accessibility Settings and enable
**GTV2STREAM recommendation redirect** again. Then test a normal movie or
series card on the Google TV home screen.

### Nuvio opens a profile picker every time

Enable **Remember last profile** in Nuvio. This is expected when Nuvio has
multiple profiles and GTV2STREAM starts a fresh task.

### The wrong title opens, or no title opens

Make sure the card is a Google TV home recommendation, not an advertisement,
sponsored card, or YouTube item. Confirm that Nuvio is installed and that the
accessibility service status is enabled and ready.

### I lost the key or moved to another TV

The key is stored locally on each TV. Repeat the configuration steps on this
TV and save the key again. If you moved to a different TV, repeat one of the
installation methods first, then configure the app on the new TV.

### Downloader or the file manager cannot install the APK

When Android prompts for permission, allow the specific app that opened the
APK to install unknown apps, then return to the installer. If the prompt does
not appear, check the app's install-unknown-apps permission in Android
settings. This permission is separate from the accessibility restriction. If
the APK still cannot be installed, download it again from the official
[releases page](https://github.com/xantipater/GTV2STREAM/releases).

## Uninstall

On the TV, open **Settings > Apps > GTV2STREAM**, select **Uninstall**, and
confirm. If GTV2STREAM is still listed in Accessibility Settings, disable its
service after uninstalling.
