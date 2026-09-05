# GTV2STREAM — Install Guide

Turn a Google TV launcher recommendation into a Nuvio deep link. No computer
needed for daily use; you only touch a PC once, to install the APK.

## Why ADB may be needed

Some Android/Google TV builds block sideloaded apps from enabling accessibility
and have no usable restricted-settings toggle. On those builds, use ADB
installation instead of Downloader or a file manager.

## What you need

- A Google TV / Android TV device with Nuvio installed
- The [GTV2STREAM-v1.0.0.apk](https://github.com/xantipater/GTV2STREAM/releases/download/v1.0.0/GTV2STREAM-v1.0.0.apk)
- A free TMDB API key: create one at <https://www.themoviedb.org/settings/api>
  (sign up, then Settings → API → Create → Developer)

## 1. Install the APK

### Option A: Windows over-network ADB

Use this option when your Google TV blocks the accessibility service after
sideloading. Your Windows PC and Google TV must be on the same local network.
No USB cable is needed. Nuvio should already be installed on the TV.

1. On the Windows PC, download the official [Android SDK Platform-Tools for
   Windows](https://dl.google.com/android/repository/platform-tools-latest-windows.zip)
   ZIP and extract it.
2. Download the
   [GTV2STREAM-v1.0.0.apk](https://github.com/xantipater/GTV2STREAM/releases/download/v1.0.0/GTV2STREAM-v1.0.0.apk).
   Put the APK file beside `adb.exe` in the extracted `platform-tools` folder.
3. On the TV, open **Settings → System → About**. Select **Android TV OS
   build** seven times to unlock Developer options. Menu names vary across
   devices.
4. Go back to **System → Developer options**. Turn on **USB debugging** and
   **Network debugging** if your TV offers it. USB debugging alone does not
   necessarily expose ADB on network port `5555`.
5. Find the TV's IP address in **Settings → Network & Internet** by opening
   the connected network.
6. In Windows Explorer, open the `platform-tools` folder. Click the address
   bar, type `cmd`, and press Enter.
7. In the Command Prompt window, connect to the TV. Replace `TV_IP` with the
   TV's actual IP address:

   ```text
   adb connect TV_IP:5555
   ```

   Accept the debugging authorization prompt on the TV. If your TV does not
   offer a direct network connection on port `5555`, use the pairing steps in
   the first troubleshooting subsection below instead.
8. Install the APK:

   ```text
   adb install -r GTV2STREAM-v1.0.0.apk
   ```

   `Success` confirms the install.

After setup, you can turn off USB debugging and Network debugging. No PC is
needed for daily use.

### Option B: Downloader

On devices that allow the accessibility service normally, install Downloader
from the Play Store, enter the [GTV2STREAM-v1.0.0.apk URL](https://github.com/xantipater/GTV2STREAM/releases/download/v1.0.0/GTV2STREAM-v1.0.0.apk), and install the APK.

### Option C: USB stick

On devices that allow the accessibility service normally, copy the APK to a
USB stick, open it with a file manager such as X-plore, and install it. Allow
the file manager to install unknown apps when prompted.

## 2. Configure GTV2STREAM

1. Open **GTV2STREAM** from the app list.
2. Enter your own TMDB **v3 API key**, not a v4 read access token. Create or
   retrieve it at <https://www.themoviedb.org/settings/api>, then select
   **Save TMDB key**.

   The key is stored only on your device, inside the app's private local storage.

3. Select **Open Accessibility Settings**, choose **GTV2STREAM recommendation
   redirect**, and enable it.
4. Return to GTV2STREAM and check that the visible service status says it is
   enabled and ready.
5. If you use multiple Nuvio profiles, enable **Remember last profile** in
   Nuvio. GTV2STREAM intentionally launches Nuvio fresh each time, so this
   keeps the launch on your profile instead of showing the profile picker.

## 3. Use it

On the Google TV home screen, focus a recommendation card and click it.
Nuvio opens directly on that title.

- Movies open as `nuvio://movie/<imdb-id>`
- Shows open as `nuvio://detail/tv/<imdb-id>`

Ads, sponsored cards, and YouTube items are ignored on purpose.

## Troubleshooting

### Optional: Wireless-debugging pairing

Some TVs require a pairing code instead of the direct `:5555` connection. Use
this flow instead of step 7 above, before installing the APK:

1. On the TV, open **Wireless debugging → Pair device with pairing code**.
2. In the `platform-tools` Command Prompt, replace `TV_IP` and
   `PAIRING_PORT` with the values shown by the TV:

   ```text
   adb pair TV_IP:PAIRING_PORT
   ```

3. Enter the pairing code shown on the TV.
4. On the main **Wireless debugging** screen, find the separate connection
   port. Replace `TV_IP` and `CONNECTION_PORT` with those values:

   ```text
   adb connect TV_IP:CONNECTION_PORT
   ```

   The pairing port and connection port are different. Pairing authorizes the
   PC, while connecting attaches ADB to the TV. Then continue with the
   `adb install -r GTV2STREAM-v1.0.0.apk` command above.

### Other issues

- **Nothing happens on click:** reopen GTV2STREAM and confirm the status
  line shows the service enabled and ready; then re-check that the accessibility
  service is still on (sometimes toggles off after app updates).
- **Opens the wrong title or an old one:** make sure the accessibility
  service is enabled, and that you're clicking cards on the Google TV home
  screen (not inside YouTube or another app).
- **Nuvio shows "Who's watching?" every time:** enable **Remember last
  profile** in Nuvio (section 2).
- **"TMDB key invalid":** you need the **v3 API key** (32-character hash),
  not a v4 read access token. Re-copy from your TMDB account's API page.
- **Wiped your key or moved TVs:** just repeat section 2. If you moved to a
  different TV, repeat the install steps in section 1 first.

## Uninstall

Settings → Apps → GTV2STREAM → Uninstall. Then remove the accessibility
service from Accessibility settings if it's still listed.
