# Review stabilisation — implementation and validation record

## Source and release boundaries

Snapshot checked on 18 September 2026:

- `main`: `c8cbdf74f82242c3a7a235dd77ce523d5f6b713b` (older application).
- Published `v1.2.0`, `release/1.2`: `36a7022d8ee6f8978677e3fc003f23cea72c30d3`.
- PR #19: `f9445fa1ddd69a5ec5eed59377f83706d782419d`, unmerged.
- `fix/review-stabilisation` starts from PR #19, retaining its changes unchanged
  where they already solve the review. The implementation PR targets `release/1.2`;
  its diff includes PR #19 until that prerequisite is merged separately.

No production version, release, signing credential or main/release branch was
changed. PR #13's main/release integration conflict still needs an authorised
maintainer decision. Do not describe these fixes as installed on users' TVs.

## Findings and engineering decisions

| Finding | Implementation / status |
|---|---|
| F1 control/app false redirects | PR #19 retained; sponsored rejection is terminal too. Runtime tests send focus, app/edit and repeated window events through the service. |
| F2 ranking | Exact normalised title and unique media-type/ID required. Popularity does not disambiguate. Partial/franchise and ambiguous matches fail closed with feedback. |
| F3 year and cache identity | Explicit `(year)` retained in typed source and query/cache identity. Titles such as `1917` do not supply year metadata. Provider enrichment preserves known years. |
| F4 whitelist reassertion | Current policy/generation/target checked at every launch; successful YouTube launches alone arm one reassert. Old card state is consumed. |
| F5 updater | Arbitrary 100-KiB minimum removed; bounded ZIP/CRC and Android package/version checks added. Android remains the signature/compatibility authority. Published 1.2 needs manual recovery for small updates. |
| F6 node ownership | Ancestor walker borrows initial node and releases only acquired parents; caller owns initial release. API 26 runtime coverage includes pooling; API 33+ recycle is a no-op. |
| F7 clipping | Consistent bounds plus traversal budget. Actual AccessibilityNodeInfo lengths 200/201/299/300/301 and hidden nodes tested. |
| F8 failures | PR #19 main launch preserved; launcher Toast helpers main-safe independently. Current user selections receive key, timeout, failed lookup and no-match outcomes; stale errors suppressed. |
| F9 privacy | Accurate TMDB/GitHub/target disclosure, release payload logs disabled and preferences excluded from backup. No evidence of historic backup execution or public credential leakage is claimed. |
| F10 setup | Published-version filenames aligned to 1.2.0; PC commands use `adb shell appops`. Vendor operation remains device-specific. |
| F11 validation/release | Actual-code helper and Android runtime tests, debug/release lint/build, real shrunk archive validation. Main/release reconciliation and signed physical-TV release gates remain manual. |
| F12 process killing | Calls and permission removed. Android 14 does not permit killing another app with this API; historical redirect successes did not prove the kill worked. |

Additional substantiated risks addressed: delayed A after new B (including a
whitelisted B), release logging of transport messages, weak cross-window capture,
provider inference from a title mentioning YouTube, and installer session outcome
loss when the Activity callback disappears. Update callbacks are associated with
the saved session; raw URLs and credential-bearing exception messages are not
logged by release builds.

## Deliberate limits, not new feature work

The matcher now rejects genuine ambiguous same-name productions instead of guessing
by popularity. Without explicit year data this can reduce redirects, which is
preferable to opening the wrong title. General parser language/title-case limits
remain. Full localisation, fuzzy matching, compact whitelist UI, new targets and
AGP/Gradle major upgrades are separate work.

The fallback's existing coordinate bands remain specific to observed layouts.
Capture is now restricted to the active launcher window, not arbitrary app
windows. A focused card with no subsequent usable click does not authorise a stock
YouTube divert. A launcher that supplies neither a suitable click nor an entity
window can miss a redirect. Verify that tradeoff on the actual supported TVs.

## What the automated checks do

`DeepLinkHelperTest` and `StabilisationTest` execute production parser, matching,
cache, dispatch and archive methods. The legacy helper suite's source-code-string
checks were removed; assertion totals before/after are not comparable coverage
metrics. ZIP fixtures are valid ZIP structures, **not** genuine Android APKs.
`verifyReleaseArchive` separately validates the real R8-built unsigned release APK.

`StabilisationRuntimeTest` runs in an installed Android test APK. It sends synthetic
AccessibilityEvents through the production service and uses genuine Handler,
Looper, AccessibilityNodeInfo, Intent, SharedPreferences and PackageManager APIs.
Only network lookup results and actual external-app launches are substituted at
small overridable boundaries. It tests cancellation, failure feedback, whitelist
and reassert sequences, node ownership, clipping, foreign active-window rejection,
actual intent construction and durable installer outcomes.

Those tests are not a full Google TV accessibility binding test. Persisted installer
outcome tests simulate lifecycle loss; they do not claim to complete a signed
self-update that kills/replaces the application process.

## Commands and evidence

```sh
./gradlew :app:runHelperTests
./gradlew :app:lintDebug :app:lintRelease
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:verifyReleaseArchive
./gradlew :app:connectedDebugAndroidTest
```

Local baseline: exact source archive verified against all 63 Git blob hashes.
Actual baseline classes compiled with the API-34 android.jar and compile-only
resource constants; the unchanged helper harness passed 532 assertions. Ten
additional probes against those actual classes reproduced the original ranking,
identity, provider classification, sponsored rejection and APK validation defects.
These probes were not reconstructed copies of the implementation.

The authoring container has JDK but no complete Android SDK/emulator, and direct
Git/Maven network access fails. Local compilation/helper results are therefore
separate from the GitHub build and Android runtime jobs. Read the implementation
PR and CI artifacts for final results and exact tested commits. No unrun command
should be marked passed here.

## Physical-TV and signed-release gates (not completed by this implementation)

1. On an Onn Android 14 device and the supported TCL, focus a YouTube card then
   open MiX Xplorer; rearrange by long press and direct control; exit via Done,
   Back and Home. No redirect during controls; normal cards recover afterwards.
2. Test real movie/series cards across Disney+, Prime, Netflix and ITVX, including
   long titles, repeated selections, remakes/year metadata, ads and whitelisted
   providers. Test rapid A→B and A→Home during a deliberately slow lookup.
3. Exercise SmartTube and Cobalt warm/cold and repeated selections, missing targets,
   manual YouTube launch, whitelist changes and one-reassert behaviour. Collect
   consented debug payloads only for failures; redact before sharing.
4. Build two increasing version codes with the maintainer's unchanged test/release
   signing identity on an authorised machine. Verify the exact candidate artifact
   with `apksigner verify --print-certs`; compare signer identity to the installed
   predecessor. Do not store keys or passwords in the repository or test logs.
5. Install N, then update N→N+1 via the real system installer: unknown-source denial,
   user confirmation, cancel, failed transfer, rejected signature, Activity close,
   process replacement and accessibility reconnect. Confirm settings/key survive.
   A synthetic ZIP and a debug build do not satisfy this gate.
6. Existing published v1.2.0 may reject the next small APK before Android sees it.
   Test its one-time `adb install -r` recovery with a compatible signed artifact;
   no padding workaround or silent installer is added.
7. Verify release Logcat does not contain title payloads/keys. Confirm backup rules
   on the actual OEM devices, and review UI labels and D-pad focus after failures.

## Platform references

- Android 14 background-process API restrictions:
  https://developer.android.com/about/versions/14/behavior-changes-all#kill-background-processes
- Accessibility node recycle/pooling API:
  https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo#recycle()
- Backup configuration and Android 12+ transfer rules:
  https://developer.android.com/identity/data/autobackup
- User-confirmed PackageInstaller sessions:
  https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams#setRequireUserAction(int)
