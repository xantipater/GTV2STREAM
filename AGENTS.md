# GTV2STREAM Agent Handoff

This file is the working handoff for multi-agent development of GTV2STREAM v1.2.

## Current release goal

v1.2 is a **provider-routing reliability + whitelist + regression-protection** release.

Do not expand scope into Plex/Jellyfin, Fire TV, major UI redesigns, or unrelated features during this release cycle.

## Project guardrails

- Work from `release/1.2`; never commit feature work directly to `main`.
- Open PRs against `release/1.2` unless explicitly told otherwise.
- Preserve the parser's **fail-closed** philosophy: a rare missed title is preferable to redirecting launcher chrome, settings, ads, sponsored content, or ambiguous text.
- Preserve the project's **local-first/privacy** rules. No telemetry, analytics, accounts, remote backend, or viewing-history collection.
- Never commit TMDB keys, credentials, signing material, user data, captured private data, or secrets.
- Keep changes narrowly scoped to the assigned branch/workstream.
- Add regression coverage for every parser/routing bug fixed.
- Do not weaken ad/sponsored-content rejection.
- Avoid broad regex/parser changes unless backed by fixtures/tests.

## Branch ownership / workstreams

### 1. Disney/provider parsing

Branch: `fix/disney-provider-parsing`

Goal: resolve issue #2 and harden provider-action wording without making title extraction dangerously permissive.

Focus:

- Inspect `RecommendationTitleParser` and related helpers.
- Keep `Disney+` / `Disney Plus` provider recognition.
- Add support for real provider-action wording seen in launcher payloads.
- Cover representative payload shapes such as:
  - `Daredevil. Watch on Disney+`
  - `Daredevil. Available on Disney+`
  - `Disney+. Daredevil.`
  - `Daredevil — Disney+`
  - `Daredevil, Disney+`
- Cross-check equivalent Netflix, Prime Video, ITVX, etc. cases.
- Sponsored/advertisement payloads must remain hard rejected.

PR target: `release/1.2`

Suggested PR title: `fix: harden provider action parsing`

### 2. Provider whitelist

Branch: `feature/whitelist`

Goal: integrate and validate the provider whitelist requested in issue #1.

Important: **do not recreate this feature from scratch until the owner's existing local implementation has been pushed.** Review/integrate that implementation instead.

Required behaviour:

- Whitelisted provider recommendation -> GTV2STREAM does nothing and allows normal Google TV behaviour.
- Non-whitelisted provider recommendation -> normal configured GTV2STREAM redirect.
- Existing users with no whitelist configured retain current behaviour.
- Preferences persist across restart.
- Provider matching must not accidentally whitelist unrelated titles or UI text.

PR target: `release/1.2`

Suggested PR title: `feat: add provider whitelist`

### 3. Reliability / payload regression harness

Branch: `chore/reliability-tests`

Goal: make real Google TV launcher payload bugs reproducible without needing the physical TV for every code change.

Prefer a simple fixture-based approach over a large emulator framework.

Fixtures/tests should cover:

- movie titles
- TV titles
- provider-first payloads
- provider-last payloads
- YouTube payloads
- ads
- sponsored cards
- settings/navigation chrome
- malformed or ambiguous payloads

Desired workflow:

`real TV bug -> capture payload once -> add fixture -> regression test forever`

PR target: `release/1.2`

Suggested PR title: `test: add Google TV payload regression fixtures`

### 4. CI / release engineering

Branch: `chore/ci-release` (create from `release/1.2` when starting)

Goal: make the existing CI useful as the release gate.

Review/improve CI so PRs into `release/1.2` reliably run:

- helper tests
- Android lint
- debug APK build

Also consider:

- Gradle cache correctness
- clear failure output
- uploading the debug APK as a workflow artifact
- preparing the later `release/1.2 -> main` release PR

Do not automate signing or public release publishing unless explicitly requested.

PR target: `release/1.2`

Suggested PR title: `ci: harden Android build and test workflow`

## Reviewer role

After implementation PRs are ready, use a separate reviewer agent that did not author the changes.

Review for:

- parser false positives
- regressions in existing provider/title parsing
- duplicated provider logic
- whitelist preference/migration problems
- secrets or credential leakage
- privacy/local-first violations
- unnecessary Android permissions
- dead/unreachable code
- tests that pass without exercising the intended behaviour
- scope creep

The reviewer should review and request targeted fixes, not redesign the project.

## Merge order

Preferred sequence:

1. reliability / fixture tests
2. Disney/provider parsing fix
3. whitelist integration
4. CI/release improvements
5. independent combined review
6. release candidate testing on real Google TV hardware
7. `release/1.2` PR to `main`

Update/rebase remaining branches after earlier merges where necessary.

## Required local checks

Before marking implementation work ready:

```bash
./gradlew :app:runHelperTests --stacktrace
./gradlew :app:lintDebug --stacktrace
./gradlew :app:assembleDebug --stacktrace
```

If a host cannot run an Android/Gradle step, state exactly what was and was not verified in the PR.

## Real-device release check

The emulator/test harness is not authoritative for Google TV `launcherx` accessibility payloads. Before v1.2 reaches `main`, validate the candidate APK on a real Google TV device against at least:

- Disney+
- Prime Video
- Netflix
- ITVX
- YouTube / SmartTube
- Nuvio target
- Stremio target
- whitelisted provider
- non-whitelisted provider
- sponsored card
- normal launcher navigation/settings interactions

Captured failures should become regression fixtures whenever possible.

## Current repository state

- Stable branch: `main` (v1.1.0 baseline)
- Integration branch: `release/1.2`
- Existing work branches:
  - `feature/whitelist`
  - `fix/disney-provider-parsing`
  - `chore/reliability-tests`
- CI workflow exists on `release/1.2`.
- Issue #1: provider whitelist (`enhancement`)
- Issue #2: Disney+ links not resolving properly (`bug`)

When in doubt: keep the change smaller, preserve fail-closed behaviour, add a regression test, and target `release/1.2`.