# Owner-approved overrides to AGENTS.md

## 2026-09-10 — REQUEST_INSTALL_PACKAGES for one-tap in-app update (branch: `feature/in-app-update`)

- **What was overridden:** AGENTS.md workstream D rule "Do not add package-install/update permissions."
  (Related: rule 8 — no permission changes without a required, justified task; rule 13 — no auto-installs.)
- **Why:** The owner explicitly requested one-tap in-app update on this branch. The implementation
  downloads the stable release APK and commits it to a `PackageInstaller` full-install session for
  our own package, which requires `android.permission.REQUEST_INSTALL_PACKAGES`. No other new
  permission was added.
- **Safeguards / justification:** No silent installs ever. The flow requires the user to tap
  "Download & install" in Settings and then confirm in the Android system installer UI; the result
  receiver only forwards the system session outcome (installed / cancelled / failed / blocked).
- **Check-only defaults otherwise preserved:** The existing throttled, off-UI-thread, fail-quiet
  stable-release check is unchanged; the "Open release page" action is kept; the one-tap button is
  shown only when a release APK URL resolves; nothing auto-downloads or auto-installs.

## 2026-09-10 — KILL_BACKGROUND_PROCESSES for the YouTube redirect (branch: `fix/tizentube-android14` work, tested in `GTV2STREAM-testbuild`)

- **What was overridden:** the AGENTS.md rule on new permissions, specifically adding
  `android.permission.KILL_BACKGROUND_PROCESSES`.
- **Why:** the launcher starts its own copy of stock YouTube for every YouTube recommendation card,
  and a redirect that leaves that copy resident can lose the foreground back to it, so the user sees
  official YouTube instead of the configured target. The owner explicitly requested this: "if you
  click on a YouTube link, kills any running YouTube instance on divert."
- **Scope:** `KILL_BACKGROUND_PROCESSES` is a normal permission and cannot touch a foreground app.
  It is called twice on the YouTube path only, once before the redirect (the launcher's copy is still
  backgrounded then, so its own launch has to cold-start) and once after, and never on the
  movie/series path. No force-stop, no `FORCE_STOP_PACKAGES`, no silent install, no other permission.
