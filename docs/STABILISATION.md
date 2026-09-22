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

## Continuation: verified results on 18 September 2026

Implementation head: `fa5186635dacf80877e7c45704e27ab5b0ba8085`.
GitHub CI run: [35380165250](https://github.com/xantipater/GTV2STREAM/actions/runs/35380165250),
completed successfully. Its PR merge checkout was
`e054fa6e84bc967f358aab4fdcb99dcf8cc6ec68`, tree
`f1ef91cf59b1a95fa661fdebec362c0dbee90ab7`. All 70 tracked files in the downloaded
source snapshot were verified against their Git blob hashes and against the local
working source. This record is a documentation-only follow-up to that tested code.

| Check actually executed | Result |
|---|---|
| `:app:runHelperTests` | PASS, 511 assertions; includes 63 stabilisation checks. |
| `:app:lintDebug`, `:app:lintRelease` | PASS, no errors; seven warnings in each report. |
| `:app:assembleDebug`, `:app:assembleDebugAndroidTest` | PASS. |
| `:app:assembleRelease`, `:app:verifyReleaseArchive` | PASS, real R8-shrunk unsigned APK accepted by production archive validation. |
| `:app:connectedDebugAndroidTest`, API 26 | PASS, 27 tests, zero failures/errors/skips. |
| `:app:connectedDebugAndroidTest`, API 34 | PASS, 27 tests, zero failures/errors/skips. |
| Downloaded CI bytecode rerun locally | Helper suite PASS (511); standalone stabilisation checks PASS (63); real release-archive check PASS. |
| `git diff --check` | PASS for the continuation changes. |

The shrunk unsigned APK is **59,686 bytes**; SHA-256:
`af9a7658b9a059fe24543c78b2d6c41d7554e4226c0ed72673558ca1d211a78d`.
Its ZIP entries/CRCs were also checked locally. This is not a signed installable
release or evidence of signing-key continuity. The standalone 63-check run repeats
checks already included in the 511 total; it is not additional coverage.

The seven lint warnings concern the existing target SDK, package-visibility usage,
newer available build/test dependencies, and a redundant SDK guard. Scoped launcher
queries are present. No lint baseline, disabled rule, or unrelated dependency
migration was used to obtain the passing result.

### What was corrected after the first PR run

The earlier run `35370785031` was not successful: lint found a missing-braces /
suspicious-indentation error in `SettingsActivity.refreshInstallStatus`, and 16
of 25 runtime tests failed because their synthetic events were writable.
Android delivers sealed events to accessibility services; calling `getSource()`
on an unsealed test event throws before routing is exercised. The remaining tests
were not evidence that those failing paths worked.

The continuation:

- Adds the missing null-guard braces and restores the update button's label and
  action after a persisted installer failure, not just its enabled state.
- Delivers sealed, record-free framework Parcel copies of synthetic events. The
  test-only adapter checks the API 26/34 parcel layout and verifies payload
  preservation, readable source access and rejected mutation. It does not relax
  Android checks or change production routing to accommodate the fixtures.
- Adds an actual SettingsActivity test using an uncommitted PackageInstaller
  session: pending state disables the button, then a persisted cancellation
  restores its retry label. No APK is installed by this test.
- Retains the exact tracked source identity, actual compiled helper classes and
  diagnostics as CI artifacts, without copying credentials or local settings.

The runtime XML reports were inspected, not inferred from a green workflow badge.
All 27 test cases ran on both API levels. They include normal redirects alongside
app/edit rejection, sponsored rejection, whitelist/reassertion sequences, delayed
A→B cancellation, policy changes during lookup, explicit-year propagation,
main-thread failure feedback, node ownership, clipping, foreign-window rejection,
package validation and persisted installer status.

**Still unverified:** real Google TV binding/payloads, actual Nuvio/Stremio/WuPlay/
SmartTube/Cobalt handling, physical TV UI and OEM backup behaviour, and a same-key
signed N→N+1 self-update with process replacement. The cancellation/outcome tests
use synthetic statuses and a live uncommitted session, not a completed install.
An independent review by someone other than the implementation author is also
still a release gate. No main/release merge or publication was performed.

## Independent continuation — 19 September 2026

The live baseline was still `f01bb3112b0f651e858772ebac5411cfc8776414`.
PR #19 remained its ancestor; PR #20 remained open against `release/1.2`, with
no review comments. Existing fixes were inspected before these additive changes.
The coordinator reviewed the TMDB change; separate reviewers checked routing and
updater changes they did not author. This is scoped peer review, not a claim of
independent approval of one's own additions.

### Newly corrected gaps

- A delayed providerless detail callback could lose the selected provider after
  the two-second duplicate window, bypassing the whitelist. Provider evidence now
  belongs to the same selection generation, title, year and route. Its explicit
  year also reaches the actual lookup/cache after a title-only callback. A newer
  explicit remake cancels older work; a new click cannot inherit an old bypass.
- A failed YouTube launch left click authorization available for a later stock
  window to replay. A resolved attempt now consumes it; only a successful launch
  arms the existing single reassert. Genuine new selections still work.
- `TmdbClient` considered only the default first search page. It now examines a
  complete, consistent result set of at most five pages before matching. A later
  remake/series prevents an ambiguous redirect; a unique later-page title can
  succeed. Larger, incomplete, malformed or changing result sets fail closed.
  This can add search requests/latency or intentionally produce no match.
- Foreground replacement Settings now observes durable installer outcomes.
  Queued callbacks respect listener ownership, terminal state is cleared on retry,
  a metadata refresh preserves Cancel, and an older Activity cannot cancel a newer
  Activity's transfer. Paused Settings cannot consume an unseen terminal outcome.
- Setup/update documentation no longer treats uninstalling as an in-place signing
  recovery or promises unconditional accessibility reconnection. Vendor auto-start
  commands are explicitly conditional.

### Local validation and identity

Published code commit: `9354236844771f4cf778f077e00a1d2865c16302`.
Local tested counterpart: `e8df9b636dcbb6a310c6c56868b786822ded15a2`.
Both have the exact Git tree `924f05c2f640f3fa80325b3df73f85bb530cfaca`.
Local Git had no push credentials; the connected GitHub account created additive
commits and advanced PR #20 without force. Every remote tree hash was checked
against its local counterpart. This documentation follow-up changes no code.

The Windows 11 harness installed portable Temurin 17.0.20.1 and Android tooling in
its workspace, used the repository's Gradle 8.13 wrapper, and ran WHPX-accelerated
Google APIs x86_64 emulators. API 34 (`emulator-5580`) and API 26 (`emulator-5582`)
were individually selected with `ANDROID_SERIAL`. No existing ADB devices were
connected. No owner's TV was contacted, cleared or overwritten. Process-local
TEMP/TMP and IPv4 JVM settings resolved a Windows loopback startup failure; the
application/build configuration was not weakened to work around it.

The unchanged `f01bb311` baseline passed all helper/lint/build/archive commands
below and its original 27 API 34 runtime tests. Its local unsigned APK was 59,686
bytes, SHA-256 `b82ffdb3456825fc78334652b169580d5d20e4b5df5177bc5085056f7a60b77c`.
This is distinct from the older CI artifact documented above.

| Check actually executed locally | Result |
|---|---|
| `:app:runHelperTests` | PASS: 511 assertions, including 63 stabilisation checks. |
| `:app:lintDebug :app:lintRelease` | PASS: no errors, six warnings per report. |
| `:app:assembleDebug :app:assembleDebugAndroidTest` | PASS. |
| `:app:assembleRelease :app:verifyReleaseArchive` | PASS: actual shrunk unsigned APK, 60,830 bytes. |
| `:app:connectedDebugAndroidTest`, API 34 | PASS: 63 tests, zero failures/errors/skips. |
| `:app:connectedDebugAndroidTest`, API 26 | PASS: 63 tests, zero failures/errors/skips. |
| Final diff / release artifact inspection | `git diff --check` passes; compiled manifest, backup XML and DEX inspected. |

The full helper/lint/build/archive matrix was rerun after the final fixture change
at `e8df9b636dcbb6a310c6c56868b786822ded15a2`; the two final runtime runs use that
same code/test tree. Documentation edits do not affect the APK. The earlier combined
62-test run at `723638f1e2ccfc824bd7afe2cf480582276fc595` also passed, but it does
not substitute for these final 63-test results.

GitHub CI [35406111192](https://github.com/xantipater/GTV2STREAM/actions/runs/35406111192)
also passed all three jobs for published code `9354236` (PR merge checkout
`6e20c32cafd7899bacb86e4edfdd7a2cb682d49d`, same tree): helper/lint/build/release
archive checks and 63 tests on each API. The detailed local XML reports above and
CI job logs were inspected, not just the workflow badge.

Final local unsigned APK SHA-256:
`ec1231afbf355ed65dc3b0332b0a4d615dc3c46ef6365363e2695f7d8f1c0d9b`.
Compiled permissions are INTERNET, REQUEST_INSTALL_PACKAGES and
SYSTEM_ALERT_WINDOW; neither KILL_BACKGROUND_PROCESSES nor QUERY_ALL_PACKAGES is
present. The manifest disables backup and references compiled preference-exclusion
rules for cloud/transfer. DEX inspection found no debug/payload Log.d calls.
Remaining log calls cover fixed updater/service messages and badge window errors;
the badge receives no title or key. This does not establish OEM backup execution
or physical-TV Logcat behavior.

Lint warnings concern target/compile SDK currency, newer test dependencies,
package visibility (scoped queries are present) and the existing redundant SDK
check. No new baseline or suppression was added. Gradle 9 deprecation notices remain;
the separate major toolchain migration is out of scope.

### Regression evidence and boundaries

The isolated `60867d9b55bf353fe0d1c1cf0ce7c94807d61011` checkout kept baseline
production behavior (apart from an injectable TMDB transport) and ran the added
regressions: 62 tests, 24 failures. Failures directly reproduced delayed/replayed
routing and broken Settings controls. That first TMDB fixture was then found to
supply responses by position alone: an erroneous external-ID request could consume
page-two JSON and accidentally look like a correct refusal. Its first-page positive
also unnecessarily required an explicit page=1 parameter. These fixture defects
were corrected, not counted as product defects.

The corrected TMDB-only negative run at
`53b0ba7f165f8452b6c88cf27ebf986a1b470fff` ran 13 tests against old behavior:
10 failed, while first-page matching, encoding, and no-result/invalid-ID controls
passed. Request endpoint/page assertions now prove that a second search page is
read before external IDs are requested. All 13 pass with the corrected client.
The final suite has 36 service/runtime tests, 13 TMDB tests, 11 updater lifecycle
tests and 3 actual Cobalt-launch entry tests: 63 total. XML reports were inspected.

External TMDB responses and target launches are substituted; no live TMDB key is
used. Detail fallback regressions invoke the actual service dispatch method because
the unbound node fixture cannot deliver a live entity-title source tree. The sealed
event adapter remains test-only. Node pooling/clipping, Handler/Looper, preferences,
Activities, real uncommitted installer sessions, receivers and Intent construction
run on Android. Actual Cobalt launch wiring now has endpoint/flags/fallback tests;
this still does not prove a third-party app displays the requested item.

### Additional observed debug-signed upgrade (API 34)

A separate disposable-emulator probe used this code tree plus an uncommitted test
and Gradle init-script override (code 6 / 1.2.0 to code 7 / 1.2.1). Both APKs passed
`apksigner verify` with the same Android Debug signer certificate SHA-256:
`52eef5f0d542d86445f67ac8e84d4648491ac50a6de7637502888be72fca790b`.
The 107,291-byte N APK SHA-256 was
`8912ea93aa5e25d2a021ca1efc393cac5e8afc03fb27e22de93d8c5ae579bf0b`;
the 107,295-byte N+1 APK SHA-256 was
`0f1859a3e686277ead0b01bb30345ce18d1d98cc2a62401e85f6b50a603cd10d`.

The probe bypassed network transfer by pushing the real candidate APK. It then
executed production archive/package/newer-version validation and `commitInstall`.
Android displayed its actual update-confirmation screen; selecting Update replaced
the package. System logs show PID 7929 killed for `installPackageLI`, then PID 8054
started for `UpdateInstallReceiver`. Android reported code 7 / 1.2.1 and the
receiver persisted STATUS_SUCCESS. Synthetic key/whitelist/badge values survived,
and Android's accessibility service dump showed the service bound after replacement.
Instrumentation had temporarily disrupted its pre-install binding; this does not
establish normal physical-TV reconnection timing. Reopened Settings showed
Update installed and Enabled—Ready.
These observations were checked against saved artifact, preference and system-log
records. No signing key was copied into evidence or source.

The instrumentation runner reported `Process crashed` because self-replacement
killed its process: this is a successful observed upgrade, **not** a passing JUnit
test. It is additional evidence, separate from the 63-test suites. Unknown-source
permission was granted for the emulator; denial/grant UI, installer cancellation,
signature rejection, real download/retry, physical OEM behavior and compatibility
with the published production signer were not established by this probe. Production
version/signing configuration was unchanged. Both test emulators were shut down.

### Refreshed finding status and release gates

| Finding | Status after this continuation |
|---|---|
| F1 | Existing app/edit rejection verified; delayed selection/replay gaps newly fixed. Real launcher matrix remains manual. |
| F2 | Existing exact/unique matcher verified; first-page-only client gap newly fixed and regression-tested. |
| F3 | Existing typed year/cache keys verified; delayed yearless callback gap newly fixed. |
| F4 | Existing final launch/reassert checks verified; persistent selection provider closes delayed whitelist bypass. |
| F5 | Existing bounded APK/package validation verified; additional installer UI/callback gaps newly fixed. Production-signer upgrade gate remains. |
| F6 | Borrowed initial-node ownership verified on Android; synthetic nodes do not model a live parent connection. |
| F7 | Actual-node clipping/bounded-capture tests pass; OEM layout assumptions remain. |
| F8 | Existing main-thread feedback/stale-result suppression verified by runtime tests. |
| F9 | Existing disclosure/backup/diagnostic changes verified in source and shrunk APK; no historic leak claimed. OEM checks remain. |
| F10 | Existing filenames/shell context verified; signing recovery and conditional vendor guidance corrected. Physical setup unrun. |
| F11 | Local baseline, failing regressions, final Android suites and release-build checks now executed; fixture limits stated above. |
| F12 | No cross-app kill call/permission; actual Cobalt wiring tested. Third-party warm/cold handling remains physical-TV work. |

Additional earlier risks—late A after B (including whitelisted B), provider names
inside titles, foreign-window capture and lost installer listeners—remain covered.
No supported-layout fixture, production signer or authorised physical TV was
available. The smallest remaining owner actions are the specific physical-TV
matrix below and a compatible production-signed N→N+1 update/recovery check.

PR #13 still has only a `ROADMAP.md` content conflict in a read-only merge-tree
check of `main` and `release/1.2`. A maintainer must reconcile that roadmap on an
integration branch while preserving current release and outstanding-gate wording,
then review the release-to-main merge. Neither branch was merged or overwritten.

Recommendation: ready for code review and physical-device testing, not a release
approval. No release, production version bump or signing-credential change occurred.

## Review follow-up — 22 September 2026

The refreshed PR #20 baseline was `189c5333ab24e99b9bef3d65accbbd0860747be2`.
Three additional source-review findings were reproduced and addressed:

- A same-selection detail callback could discover a whitelisted provider while
  an older providerless lookup or queued launch remained authorised. New provider
  evidence now supersedes the captured request before bypass or deduplication.
- An explicit year arriving after a bare title was treated as a duplicate. Lookup
  identity now includes that year when deciding whether to supersede work. Old
  results, errors and misses are suppressed; explicit-year-first followed by a
  bare detail callback still retains the known year and provider.
- Incomplete, malformed or changing TMDB search responses returned the same value
  as a completed no-match, creating a one-hour cached miss. They now throw a
  sanitised retryable lookup failure. A new selection can fetch again immediately;
  genuine complete misses, ambiguous matches and the initial five-page limit
  retain the existing refusal policy.

The tests-only commit `5ca044a226e33b9814c44d68db909ac58a1dd8b9` has tree
`0cb3bd06e9993deb10847d34235d265861bf6a42` and unchanged production code.
[Negative-control CI 35704612315](https://github.com/xantipater/GTV2STREAM/actions/runs/35704612315)
ran 75 runtime tests on each of API 26 and 34. Both runs failed the same 14
behavioural checks: ten service regressions and four malformed/incomplete TMDB
response groups. Job logs show the expected stale launches, suppressed refined
lookups, stale feedback and cacheable-null results, rather than compilation or
fixture failures. The helper/lint/build job passed.

The fixed suite also adds an allowed-provider enrichment positive control, for
76 runtime tests in total. Its final-head CI results and source identity are
recorded on [PR #20](https://github.com/xantipater/GTV2STREAM/pull/20); the negative
run above is intentionally failing evidence, not a release validation pass.
An independent reviewer who did not author these fixes found no blocking or
important issue in their source/test diff. The local host did not run an Android
build or emulator; runtime validation uses the repository's existing CI matrix.

The new tests use the production service, real Android scheduling, and scripted
TMDB transport. The retry regression passes actual malformed pagination through
`TmdbClient`, verifies no miss is cached, then repeats the click and succeeds with
a complete response. Detail callbacks enter the actual dispatch method after
title/provider extraction because the unbound fixture cannot expose a live
Google TV entity tree. In-flight and already-posted main-thread launches, stale
errors/misses and valid recovery are covered; real launcher delivery is not.
No production dependency, permission, signing material or version was changed.
The physical-TV and production-signed upgrade gates below remain open.

## Second bug sweep — 22 September 2026

The next sweep used `e84c4bb` as its baseline. The follow-up addresses these
additional paths without changing versions, permissions, signing or dependencies:

- Preserve title-only focus evidence and invalidate incompatible cached cards.
  Ambient hero evidence must match the current credible focus title, including
  a quick click before the panel has updated. Contradictory direct event/node
  evidence stops dispatch rather than falling through to an old card.
- Read compatible provider/year evidence from clicked node text and descriptions.
  A Watch/Play action on the same authoritative detail title retains the selected
  provider and year across its new click generation. Ordinary new card clicks,
  different titles and explicit remakes do not inherit that selection's policy.
- Merge an explicit description year even when event text already supplies a
  provider. Conflicting explicit years/providers fail closed.
- Accept numeric movie titles only in recognized non-YouTube provider title slots
  or authoritative detail rows: one to four ASCII digits, no leading zero.
  Bare event numbers, YouTube counters, ratings, runtimes and ads stay rejected.
- Treat repeated TMDB identities as retryable incomplete results, including
  ignored person rows; matching waits for a complete distinct result set.
- Make cancellation and final installer ownership mutually exclusive. Once a
  worker owns the handoff, Settings shows system confirmation instead of claiming
  cancellation, and a second update cannot replace it. Abandoned unsealed sessions
  recovered without a live worker become retryable failures; sealed/live sessions
  remain pending. Terminal outcomes retire matching live ownership before notifying
  preference observers, so a reentrant Settings refresh cannot restore pending UI.
  Android still owns installation approval.
- Resolve current SmartTube handlers on each selection in the existing package
  order. Refresh visible Settings on heartbeat changes/expiry, and keep the release
  button's label consistent with its action after a permission failure.

New tests exercise real service event dispatch, compatible and contradictory node
evidence, detail-action/new-card isolation, mutable package-query results, actual
Settings lifecycle/preferences and real uncommitted installer sessions. Small
acquisition boundaries substitute source nodes, detail/window extraction, package
queries and final system commitment; proprietary launcher delivery and an actual
self-update remain outside this suite. The installer concurrency test synchronizes
at the production handoff boundary rather than relying on a timing race.

The regression-only CI commit is `994ed736aaa816a6ccecbc373b9a2c704e1fcfd1`
(tree `3f0c5c7bf09cc1292560221b114414dff19127dc`). Its production algorithms are
unchanged, with behavior-preserving test boundaries added. Results for this
negative control and the final fixed revision are recorded on
[PR #20](https://github.com/xantipater/GTV2STREAM/pull/20).

The initial fixed run `35707839477` passed build/lint/archive checks and 109 of
111 tests on each API, but two existing installer-recovery tests exposed the
synchronous terminal-notification ordering bug described above. Those assertions
remain unchanged; a dedicated preference-observer regression also covers the fix.
That initial run is not a final validation pass.

Local compilation uses the official API-34 Android jar and AndroidX/JUnit jars
with compile-only resource constants. The actual helper suite passes 606
assertions after the fixes. A standalone JVM run of the actual TMDB test methods
using JSON-Java has 15 passes/5 failures before the fix and 20 passes afterward.
Neither result substitutes for Android resource linking, lint, packaging or
instrumentation; those checks run in the existing API 26/34 CI matrix.

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
