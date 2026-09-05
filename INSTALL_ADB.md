# GTV2STREAM: Install via ADB on Windows

This is a complete Windows guide for installing the released GTV2STREAM APK
with Android Debug Bridge (ADB) over your local network. Use it when a TV
build blocks the accessibility service after a normal APK installation. If
your TV lets you enable the service normally, use the [normal installation
guide](INSTALL.md) instead.

This guide does not use a USB cable. Your Windows PC and TV must be connected
to the same local network, and Nuvio must already be installed on the TV.

## What you need

- A Windows PC on the same local network as the Google TV or Android TV.
- A Google TV or Android TV with the Google TV home launcher.
- Nuvio installed on the TV.
- Internet access for downloading Platform-Tools and the APK.
- The released APK:
  [GTV2STREAM-v1.0.0.apk](https://github.com/xantipater/GTV2STREAM/releases/download/v1.0.0/GTV2STREAM-v1.0.0.apk)
- Your own free TMDB v3 API key from
  <https://www.themoviedb.org/settings/api>.

## 1. Download Platform-Tools and the APK

1. On the Windows PC, download the official [Android SDK Platform-Tools for
   Windows](https://dl.google.com/android/repository/platform-tools-latest-windows.zip)
   ZIP file.
2. Extract the ZIP file. This creates a `platform-tools` folder containing
   `adb.exe`.
3. Download `GTV2STREAM-v1.0.0.apk` from the
   [GTV2STREAM releases page](https://github.com/xantipater/GTV2STREAM/releases)
   or from this direct URL:

   ```text
   https://github.com/xantipater/GTV2STREAM/releases/download/v1.0.0/GTV2STREAM-v1.0.0.apk
   ```

4. Put `GTV2STREAM-v1.0.0.apk` beside `adb.exe` in the extracted
   `platform-tools` folder.

## 2. Prepare the TV for network ADB

1. On the TV, open **Settings > System > About**. Menu names vary by device.
2. Select **Android TV OS build** seven times. A message should confirm that
   Developer options are enabled.
3. Return to **System** and open **Developer options**.
4. Turn on **USB debugging** if it is available. Also turn on **Network
   debugging** or **Wireless debugging** if your TV provides one of those
   options. USB debugging alone does not guarantee that the TV accepts a
   network connection on port `5555`.
5. Open the connected network in **Settings > Network & Internet** and note
   the TV's IP address. In the examples below, replace `TV_IP` with that
   address.

## 3. Connect the Windows PC to the TV

Use one connection path. Start with the direct connection if the TV exposes
ADB on port `5555`. Use wireless debugging pairing if the TV requires a
pairing code or shows separate pairing and connection ports.

1. Open Windows Explorer and browse to the extracted `platform-tools` folder.
2. Click the folder address bar, type `cmd`, and press Enter. A Command Prompt
   opens in that folder. Keep this window open for either connection option.

### Option A: Direct network connection on port 5555

1. Connect to the TV. Replace `TV_IP` with the address you noted above:

   ```text
   adb connect TV_IP:5555
   ```

2. If the TV shows an ADB authorization prompt, accept it. If it offers to
   remember the computer, select that option if you want to use this PC again.
3. If the command reports that the connection was refused or cannot be made,
   use [Option B: Wireless debugging pairing](#option-b-wireless-debugging-pairing)
   only if the TV offers **Wireless debugging**. Otherwise, review [Direct
   connection on port 5555 fails](#direct-connection-on-port-5555-fails) for
   network, IP address, and debugging checks.

### Option B: Wireless debugging pairing

Use this option before installing when the TV requires a pairing code. The
pairing port and the later connection port are different. Do not reuse the
pairing port for the main connection unless the TV explicitly shows the same
value.

1. On the TV, open **Developer options > Wireless debugging**.
2. Choose **Pair device with pairing code**. Keep that screen visible so you
   can read the TV IP address, pairing port, and pairing code.
3. In the `platform-tools` Command Prompt, replace `TV_IP` and
   `PAIRING_PORT` with the values shown on the TV:

   ```text
   adb pair TV_IP:PAIRING_PORT
   ```

4. When prompted, enter the pairing code shown on the TV. Wait for the
   command to confirm pairing.
5. Return to the main **Wireless debugging** screen on the TV and note its
   separate connection address and port. The connection port is often not the
   pairing port.
6. Connect with the main wireless debugging port. Replace `TV_IP` and
   `CONNECTION_PORT` with the values shown by the TV:

   ```text
   adb connect TV_IP:CONNECTION_PORT
   ```

7. Accept any ADB authorization prompt on the TV.

## 4. Install the APK

Run this command in the same `platform-tools` Command Prompt after either
connection path has succeeded:

```text
adb install -r GTV2STREAM-v1.0.0.apk
```

Wait for the command to finish. `Success` confirms that the APK was installed.
If the command reports an error, see [Troubleshooting](#troubleshooting).

## 5. Configure GTV2STREAM

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

## 6. Use and test GTV2STREAM

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

## 7. Turn off debugging after setup

After you have confirmed that GTV2STREAM works, you can disable debugging:

1. On the TV, open **Settings > System > Developer options**.
2. Turn off **USB debugging** if you enabled it.
3. Turn off **Network debugging** if your TV provides that setting.
4. Turn off **Wireless debugging** if you enabled it for the pairing route.

The app does not need an ongoing PC or an active ADB connection for daily use.
Leave the TV on your normal network settings after disabling debugging.

## Troubleshooting

### `adb` is not recognized

Open Command Prompt from inside the extracted `platform-tools` folder as
described above. The folder must contain `adb.exe`. Do not run the command from
the ZIP file itself. If you extracted Platform-Tools elsewhere, browse to that
folder before opening `cmd`.

### Direct connection on port 5555 fails

Confirm that the PC and TV are on the same local network and that the TV IP
address has not changed. USB debugging alone does not guarantee a TCP ADB
server on port `5555`. If the TV has **Wireless debugging**, use the pairing
route and its separate connection port instead. Some TVs expose neither form
of network ADB, in which case this guide cannot connect to that TV.

### Pairing succeeds but `adb connect` fails

Pairing and connecting are separate steps. Return to the main **Wireless
debugging** screen and use its current connection port, not the pairing port.
Confirm the TV IP address and run `adb connect TV_IP:CONNECTION_PORT` again.

### The TV does not show an authorization prompt

Keep the TV awake and check **Developer options**. Disconnect and reconnect
with the current IP and port. If the TV has a list of authorized debugging
computers, revoke old authorizations and try again. Do not assume that every
Android TV build exposes the same debugging controls.

### `adb install` fails

Confirm that the APK is named `GTV2STREAM-v1.0.0.apk` and is beside `adb.exe`.
Download it again from the official
[releases page](https://github.com/xantipater/GTV2STREAM/releases) if the file
is incomplete. Keep the TV connected while the install runs. The `-r` option
updates an existing GTV2STREAM installation while preserving its local key
when Android permits it.

### The accessibility service is still unavailable

ADB can help with builds that block normal sideloaded accessibility setup, but
it is not guaranteed on every Android build. Confirm that you enabled
**GTV2STREAM recommendation redirect** in Accessibility Settings and that the
status in GTV2STREAM says enabled and ready. If the TV does not expose a
usable ADB path or still applies a platform restriction, consult the TV
manufacturer's documentation or use a device that supports the required
accessibility service.

### TMDB key is rejected

Confirm that you entered your own TMDB **v3 API key**, which is the API key
value from the TMDB API settings page. Do not enter a TMDB v4 read access
token. Save the key again after copying it carefully.

### Nuvio shows a profile picker every time

Enable **Remember last profile** in Nuvio. This is expected when Nuvio has
multiple profiles and GTV2STREAM starts a fresh task.

### Nothing happens when a card is selected

Open GTV2STREAM and check that its status says the accessibility service is
enabled and ready. Test a normal movie or series card on the Google TV home
screen. Ads, sponsored cards, and YouTube items are intentionally ignored.

## Uninstall

On the TV, open **Settings > Apps > GTV2STREAM**, select **Uninstall**, and
confirm. If GTV2STREAM is still listed in Accessibility Settings, disable its
service after uninstalling. You can also turn off any debugging option that
remains enabled in Developer options.
