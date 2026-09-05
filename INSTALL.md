# GTV2STREAM — Install Guide

Turn a Google TV launcher recommendation into a Nuvio deep link. No computer
needed for daily use; you only touch a PC once, to install the APK.

## What you need

- A Google TV / Android TV device with Nuvio installed
- The GTV2STREAM APK (from the [Releases page](../../releases))
- A free TMDB API key: create one at <https://www.themoviedb.org/settings/api>
  (sign up, then Settings → API → Create → Developer)

## 1. Install the APK

Easiest options:

- **Downloader app** (by AFTVnews): install Downloader from the Play Store,
  enter the APK URL, install.
- **USB stick**: copy the APK to a stick, open it with a file manager
  (e.g. X-plore) and install. Allow "install unknown apps" for the file
  manager when prompted.

## 2. Add your TMDB key

1. Open **GTV2STREAM** from your app list.
2. Paste your TMDB v3 API key and select **Save TMDB key**.

The key is stored only on your device, inside the app's private storage.

## 3. Enable the accessibility service

1. In GTV2STREAM, select **Open Accessibility Settings**.
2. Find **GTV2STREAM recommendation redirect** and enable it.

If the toggle is greyed out (Android 13+): Settings → Apps → GTV2STREAM →
menu (⋮) → **Allow restricted settings**, then go back and enable.

## 4. Set Nuvio to remember your profile

If you have more than one Nuvio profile, turn on **Remember last profile**
in Nuvio's settings. GTV2STREAM intentionally launches Nuvio fresh every
time so it always opens the title you clicked; remembering the profile keeps
that fresh launch on your profile instead of the picker.

## 5. Use it

On the Google TV home screen, focus a recommendation card and click it.
Nuvio opens directly on that title.

- Movies open as `nuvio://movie/<imdb-id>`
- Shows open as `nuvio://detail/tv/<imdb-id>`

Ads, sponsored cards, and YouTube items are ignored on purpose.

## Troubleshooting

- **Toggle won't enable / toggle missing:** do the "Allow restricted
  settings" step in section 3, then re-open Accessibility.
- **Nothing happens on click:** reopen GTV2STREAM and confirm the status
  line shows the service enabled; then re-check that the accessibility
  service is still on (sometimes toggles off after app updates).
- **Opens the wrong title or an old one:** make sure the accessibility
  service is enabled, and that you're clicking cards on the Google TV home
  screen (not inside YouTube or another app).
- **Nuvio shows "Who's watching?" every time:** enable **Remember last
  profile** in Nuvio (section 4).
- **"TMDB key invalid":** you need the **v3 API key** (32-character hash),
  not a v4 read access token. Re-copy from your TMDB account's API page.
- **Wiped your key or moved TVs:** just repeat sections 2–4.

## Uninstall

Settings → Apps → GTV2STREAM → Uninstall. Then remove the accessibility
service from Accessibility settings if it's still listed.
