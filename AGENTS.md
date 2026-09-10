# GTV2STREAM v1.2 Agent Runbook

This file is the authoritative handoff for coding agents working on GTV2STREAM v1.2.

If you are an agent and have been told to work on GTV2STREAM, **read this entire file before editing anything**. Do not infer a different workflow unless the repository owner explicitly tells you to.

---

## 0. Mission

GTV2STREAM is an Android TV / Google TV companion app that intercepts Google TV launcher recommendation selections and redirects recognised titles into a configured target app.

Current stable release: `v1.1.0`

Current development target: `v1.2`

v1.2 is specifically a:

- provider-routing reliability release;
- provider whitelist release;
- update-awareness release;
- TizenTube Cobalt integration release;
- regression-testing / CI hardening release.

Do **not** expand the release into Plex, Jellyfin, Fire TV, a broad UI redesign, analytics, accounts, cloud services, or unrelated feature work.

---

## 1. Non-negotiable project rules

These rules override convenience.

1. **Never commit feature work directly to `main`.**
2. **All v1.2 work targets `release/1.2`.**
3. Work only on your assigned branch/workstream.
4. Preserve fail-closed behaviour: missing an ambiguous title is preferable to redirecting launcher UI, settings, ads, sponsored cards, metadata, or prose.
5. Do not weaken ad or sponsored-content rejection.
6. Do not add telemetry, analytics, accounts, tracking, remote backends, or viewing-history collection.
7. Do not commit TMDB keys, API credentials, signing keys, tokens, user data, logs containing private data, or secrets.
8. Do not change Android permissions unless required by the assigned task and clearly justified in the PR.
9. Do not refactor unrelated code while fixing a bug or adding a bounded feature.
10. Every parser/routing bug fix must include regression coverage.
11. If real launcher/app behaviour is unknown, do not invent it. Use known payloads, authoritative app behaviour/source/docs, or clearly bounded patterns.
12. If you cannot complete a required validation step, state exactly what was not verified in the PR.
13. Do not auto-publish releases, auto-install APKs, or add signing secrets unless the owner explicitly requests that in a separate task.
14. Existing v1.1 users should retain current behaviour by default unless the assigned workstream explicitly changes it.

---

## 2. Repository map

Important files:

- `README.md` — user-facing behaviour and setup overview.
- `ROADMAP.md` — public project roadmap.
- `CHANGELOG.md` — release history.
- `CONTRIBUTING.md` — contributor build guidance.
- `app/build.gradle` — Android app config and helper-test tasks.
- `app/src/main/java/com/gtv2stream/RecommendationTitleParser.java` — Google TV recommendation payload/title parsing.
- `app/src/main/java/com/gtv2stream/TvRecommendationService.java` — accessibility-event handling and redirect pipeline.
- `app/src/main/java/com/gtv2stream/AppPrefs.java` — persisted app preferences.
- `app/src/main/java/com/gtv2stream/SettingsActivity.java` — settings UI.
- `app/src/main/java/com/gtv2stream/NuvioLauncher.java` — Nuvio launch path.
- `app/src/main/java/com/gtv2stream/StremioLauncher.java` — Stremio launch path.
- `app/src/main/java/com/gtv2stream/YouTubeLauncher.java` — current SmartTube launch path.
- `app/src/test/java/com/gtv2stream/DeepLinkHelperTest.java` — dependency-free JVM regression test harness.
- `.github/workflows/ci.yml` — v1.2 CI workflow.

Before editing parser/routing behaviour, read at minimum:

1. `README.md`
2. `RecommendationTitleParser.java`
3. `TvRecommendationService.java`
4. `DeepLinkHelperTest.java`

Before modifying Settings/preferences/targets, also read:

- `AppPrefs.java`
- `SettingsActivity.java`
- the relevant launcher class.

---

## 3. Start procedure for every worker

Run these steps before making changes:

```bash
git fetch origin
git checkout release/1.2
git pull --ff-only origin release/1.2
```

Then switch to the branch assigned to your workstream.

If the assigned branch already exists remotely:

```bash
git checkout <assigned-branch>
git pull --ff-only origin <assigned-branch>
git merge origin/release/1.2
```

If it does not exist:

```bash
git checkout -b <assigned-branch> origin/release/1.2
```

Do not start from `main`.

Before editing:

```bash
git status
git branch --show-current
```

Confirm the branch is correct and the working tree is clean.

---

## 4. Orchestrator mode

When one agent is assigned to coordinate v1.2, it acts as the senior engineering orchestrator rather than as a fourth implementation worker.

Recommended model allocation when available:

- orchestrator: **GPT-5.6 Sol, High reasoning**;
- bounded implementation workers: **GPT-5.6 Luna, Light reasoning**;
- final independent reviewer: **GPT-6 Astra**.

Model availability may differ. The workflow and quality bar matter more than the exact model name.

### Orchestrator responsibilities

The orchestrator must:

- read this file completely before assigning work;
- inspect `release/1.2`, open PRs, current issues and work branches first;
- spawn one worker per independent workstream where parallel work is safe;
- never assign two workers to the same branch;
- keep workers inside their prescribed workstreams;
- relay discoveries that materially affect another worker;
- inspect worker diffs and test evidence rather than trusting a "done" summary;
- send weak work back for a focused correction;
- identify shared-file conflicts before integration;
- ensure branches update from the latest `release/1.2` before final validation;
- keep the owner interruption rate low and surface only genuine blockers or material behaviour decisions;
- never allow a worker to merge its own implementation PR;
- never merge `release/1.2` into `main` without explicit owner approval.

### Worker communication model

Workers do not need peer-to-peer discussion. The orchestrator is the communication hub.

Relay only relevant information, for example:

- a regression worker introduces a fixture API the parser worker should use;
- the parser worker identifies a payload shape the regression worker should preserve;
- a target worker changes preference structures another target worker touches;
- CI changes a validation requirement all workers need to know.

Do not flood workers with unrelated implementation detail or make them repeatedly restart coherent work.

### Orchestrator acceptance rule

A worker reporting that tests pass is **not sufficient**. Before accepting a PR as ready, the orchestrator must inspect:

- the actual diff;
- changed files;
- scope compliance;
- positive regression coverage;
- negative/boundary coverage where applicable;
- duplication of existing logic;
- compatibility/default behaviour;
- privacy/permission changes;
- validation evidence.

Use the `What good looks like` section below as the mandatory quality bar.

---

## 5. Workstream selection and priority

If the owner gives a worker a named workstream, follow that section only.

If the orchestrator is coordinating the whole release, inspect existing PRs first and launch independent work that is not already underway.

Current v1.2 implementation workstreams:

1. **A — Reliability / payload regression harness**
2. **B — Disney/provider parser fix**
3. **C — Provider whitelist**
4. **D — Update awareness**
5. **E — TizenTube Cobalt target**
6. **F — CI / release hardening**
7. **G — Independent final review**

Do not duplicate work already present in an open PR.

---

# WORKSTREAM A — Reliability / payload regression harness

## Branch

`chore/reliability-tests`

## Goal

Make real Google TV launcher payload bugs reproducible in automated tests so a physical TV is not required for every parser change.

## Scope

Primarily modify:

- `app/src/test/java/com/gtv2stream/DeepLinkHelperTest.java`
- new test fixture/helper files under `app/src/test/`
- minimal production-code seams only when absolutely required for deterministic testing.

Do not redesign the application architecture.

## Required cases

Cover representative examples of:

- normal movie titles;
- normal TV titles;
- provider-first payloads;
- provider-last payloads;
- YouTube payloads;
- advertisements;
- sponsored cards;
- Google TV navigation/settings chrome;
- malformed payloads;
- ambiguous text that should fail closed.

A fixture should make clear:

- raw payload/input;
- expected title, if any;
- expected YouTube/source classification where relevant;
- accepted/rejected result.

## Acceptance criteria

- Existing helper tests still pass.
- New fixture tests fail if corresponding behaviour regresses.
- Ads, sponsored content, UI chrome and ambiguous text remain rejected.
- Test data contains no private user information.

Suggested PR: `test: add Google TV payload regression fixtures`

Target: `release/1.2`

---

# WORKSTREAM B — Disney/provider parsing

## Branch

`fix/disney-provider-parsing`

## Related issue

Issue `#2` — Disney+ links not resolving properly.

## Goal

Harden provider-action parsing without broadly relaxing title detection.

`Disney+` and `Disney Plus` are already recognised provider names. Treat the likely failure as payload/action-shape handling unless evidence proves otherwise.

Current known action families include variants of:

- `Watch on ...`
- `Watch Now on ...`
- `Stream on ...`
- `Streaming on ...`
- `New on ...`
- `Included with ...`

At minimum test bounded forms equivalent to:

```text
Daredevil. Watch on Disney+
Daredevil. Available on Disney+
Disney+. Daredevil.
Daredevil — Disney+
Daredevil, Disney+
```

Cross-check equivalent forms for at least Netflix, Prime Video and ITVX.

## Implementation rules

- Prefer narrow action-pattern additions over free-form matching.
- Preserve provider stripping.
- Preserve title punctuation handling, including titles such as `Mr. Robot` and initialisms.
- Preserve hard rejection of sponsored/advertisement payloads.
- Do not alter launcher UI-word rejection unless a failing regression proves it is necessary.

## Acceptance criteria

- Issue #2's known/expected payload forms parse correctly.
- Equivalent existing provider cases continue to work.
- No new ad/sponsored/UI false positives.
- Required Gradle validation passes.

Suggested PR: `fix: harden provider action parsing`

Target: `release/1.2`

---

# WORKSTREAM C — Provider whitelist

## Branch

`feature/whitelist`

## Related issue

Issue `#1` — Add a whitelist function.

## Critical instruction

**Do not recreate the whitelist from scratch if the owner's local implementation has not yet been pushed.**

If the branch does not contain that implementation, report:

```text
BLOCKED: owner whitelist implementation is not present on the remote branch yet.
```

You may inspect code and prepare tests/review notes, but do not invent a second competing implementation.

## Required behaviour once implementation exists

```text
Prime recommendation
+ Prime Video whitelisted
=> GTV2STREAM does not redirect
=> normal Google TV/provider behaviour continues
```

```text
Netflix recommendation
+ Netflix not whitelisted
=> normal configured GTV2STREAM redirect
```

Existing users with no whitelist configured must retain v1.1 behaviour.

## Verify

- preferences persist across restart;
- empty/default whitelist changes nothing;
- provider matching is exact/bounded enough not to whitelist a title accidentally;
- YouTube behaviour remains correct;
- ads/sponsored items remain rejected;
- no cloud/account behaviour is introduced;
- Settings remains TV-remote usable;
- migration/default handling is safe.

Suggested PR: `feat: add provider whitelist`

Target: `release/1.2`

---

# WORKSTREAM D — Update awareness

## Branch

`feature/update-checker`

## Related issue

Issue `#11` — Add update awareness in v1.2.

## Goal

Make sideloaded installs aware of newer stable GTV2STREAM releases without silently downloading or installing anything.

## Required behaviour

- Compare the installed app version with the latest **stable** GitHub release tag.
- Ignore drafts/prereleases unless a future task explicitly changes that policy.
- If current, do not show an intrusive warning.
- If newer, show a clear TV-friendly notice in Settings including the available version.
- Provide a user action that opens the relevant GitHub release/download page externally.
- Fail quietly when offline, GitHub is unavailable, the response is malformed, or a request times out.
- Throttle/cache checks so repeatedly opening Settings does not hammer GitHub; roughly daily is appropriate.
- Keep network activity off the UI thread.
- Do not auto-download or auto-install an APK.
- Do not add package-install/update permissions.
- Do not add accounts, analytics, telemetry, tracking or a backend.

## Test requirements

Version comparison must be deterministic and regression-tested, including:

- `v1.2.0` newer than `v1.1.0`;
- equal versions;
- older remote version;
- malformed/unexpected tags failing safely.

Where practical, isolate response parsing/version comparison so tests do not require live GitHub access.

The settings/service must continue to behave normally when update checking fails.

## Acceptance criteria

- New stable release can be surfaced in Settings.
- Current version remains unobtrusive.
- Network failure is non-fatal/non-blocking.
- No automatic installation occurs.
- Required Gradle validation passes.

Suggested PR: `feat: add update awareness`

Target: `release/1.2`

---

# WORKSTREAM E — TizenTube Cobalt YouTube target

## Branch

`feature/tizentube-cobalt`

## Related issue

Issue `#12` — Add TizenTube Cobalt as a YouTube target in v1.2.

## Goal

Ship TizenTube Cobalt as a second selectable YouTube redirect target alongside SmartTube, and resolve/bound the known Android 14 reliability issue before release.

## Required behaviour

- Add TizenTube Cobalt as a selectable YouTube target.
- Keep SmartTube available.
- Preserve SmartTube as the safe existing/default behaviour for current users unless a backwards-compatible preference migration proves another default is required.
- Route YouTube recommendation payloads only through the configured YouTube target.
- Do not change movie/TV Nuvio/Stremio routing.
- Settings must remain usable with a TV remote/D-pad.
- If TizenTube Cobalt is absent or cannot accept the launch, fail safely without crashing or destabilising the accessibility service.
- The Android 14 reliability problem must be reproduced, fixed, or tightly bounded with evidence before this workstream is called complete.

## Evidence rule

Do **not** invent package names, intents, deep links, Cobalt endpoints or launch contracts.

Establish the launch mechanism from authoritative TizenTube/Cobalt source/docs, package metadata, existing known-good implementation, or real-device evidence. If the exact launch contract cannot be established safely, stop with a `BLOCKED` report rather than guessing.

## Test requirements

- Existing SmartTube behaviour remains covered.
- Target-selection logic is deterministic and tested where practical.
- TizenTube chosen => only TizenTube launch path is attempted.
- SmartTube chosen => existing SmartTube path remains unchanged.
- Missing/unavailable TizenTube behaviour is safe.
- Movie/TV routing remains unchanged.

## Acceptance criteria

- Target is selectable and persisted.
- SmartTube regression-free.
- TizenTube path works using an evidence-backed launch contract.
- Android 14 reliability concern is addressed with evidence.
- Required Gradle validation passes.
- Real-device validation is explicitly required before release.

Suggested PR: `feat: add TizenTube Cobalt target`

Target: `release/1.2`

---

# WORKSTREAM F — CI / release engineering

## Branch

`chore/ci-release`

Create it from the latest `release/1.2` if it does not already exist.

## Goal

Make CI a useful release gate, not a complicated release system.

CI should run at least:

```bash
./gradlew :app:runHelperTests --stacktrace
./gradlew :app:lintDebug --stacktrace
./gradlew :app:assembleDebug --stacktrace
```

Review/improve `.github/workflows/ci.yml` so that:

- PRs into `release/1.2` run CI;
- helper tests run;
- Android lint runs;
- a debug APK builds;
- Gradle dependency caching is sensible;
- failures are easy to diagnose;
- the debug APK is uploaded as a workflow artifact if straightforward.

Do not add signing secrets, automate production publishing, or automatically create public releases.

Suggested PR: `ci: harden Android build and test workflow`

Target: `release/1.2`

---

# WORKSTREAM G — Independent final reviewer

The final reviewer must not be the agent that authored the implementation under review. When available, use GPT-6 Astra for this stage.

Review the combined `release/1.2` candidate against `main`, this runbook and all relevant issues.

Look specifically for:

- parser false positives/negatives;
- regex overreach;
- duplicate provider recognition logic;
- broken whitelist defaults/migration;
- update-checker UI-thread/network/privacy problems;
- unsafe or incorrect release-version comparison;
- TizenTube launch assumptions, Android 14 gaps or broken SmartTube defaults;
- title/provider confusion;
- secrets or credential leakage;
- telemetry/privacy regressions;
- unnecessary Android permissions;
- lifecycle/race regressions in `TvRecommendationService`;
- dead code;
- tests that do not actually exercise the intended path;
- CI gaps;
- unrelated refactors/scope creep.

For each problem report:

1. severity: blocker / important / minor;
2. exact file and relevant code area;
3. concrete failure scenario;
4. smallest reasonable fix.

Do not redesign the app unless a blocker genuinely requires it.

---

## 6. Required validation before any implementation PR is ready

Run from repository root:

```bash
./gradlew :app:runHelperTests --stacktrace
./gradlew :app:lintDebug --stacktrace
./gradlew :app:assembleDebug --stacktrace
```

Record the result of each in the PR body:

```text
Validation
- [x] :app:runHelperTests
- [x] :app:lintDebug
- [x] :app:assembleDebug
```

If something cannot run:

```text
- [ ] :app:lintDebug — NOT RUN: <specific reason>
```

Never mark an unrun check as passed.

---

## 7. What good looks like

Passing tests is necessary but not enough. Good work is small, bounded, understandable, regression-protected and does not quietly alter unrelated behaviour.

### Good implementation standard

- Solve the assigned problem, not a larger adjacent problem.
- Change the smallest reasonable amount of production code.
- Reuse existing helpers/concepts rather than duplicating provider/parser/preference logic.
- Prefer explicit bounded behaviour to broad heuristics.
- Add a regression test that would fail before the fix/change.
- Add negative/boundary coverage where permissiveness or routing changes.
- Preserve existing tests and v1.1 defaults unless explicitly changed.
- Avoid speculative abstractions, frameworks and unrelated cleanup.
- Make the PR explain exactly what changed, why it is safe, what could regress and what was actually tested.

### Parser-specific example

Bad:

```text
Problem: Disney+ "Available on" payload is missed.
Change: accept any string containing "Disney+" or "available".
Tests: one Disney+ happy-path test.
```

Good:

```text
Observed/target payload:
Daredevil. Available on Disney+

Change:
Add one bounded "Available on <provider>" action family through the existing parsing path.

Regression coverage:
- Disney+ positive
- Netflix equivalent positive
- Prime Video equivalent positive
- ITVX equivalent positive
- sponsored equivalent rejected
- unrelated UI text containing "available" rejected
- punctuation/title cases preserved
```

### Tests must prove behaviour

Tests should assert externally meaningful behaviour: parsed title, source classification, accepted/rejected status, bypass/redirect decision, target selection, version comparison, or generated launch/deep-link output as appropriate.

Do not add tests that merely repeat constants and cannot fail when the real bug returns.

When practical, confirm a new regression test would fail against the pre-fix behaviour.

### Mandatory pre-PR self-review

Before marking a PR ready, answer:

1. Did I solve the assigned problem without expanding scope?
2. Is this the smallest safe implementation?
3. Is there a regression test proving the intended behaviour?
4. Where behaviour became more permissive, is there a negative/boundary test?
5. Do all existing tests pass?
6. Did I reuse existing logic rather than duplicate it?
7. Did I avoid unrelated refactors/cosmetic churn?
8. Could an existing v1.1 user see an unintended default behaviour change?
9. Can I explain why every production-code change is necessary?
10. Did I verify rather than assume build/test results?
11. Does the PR distinguish automated/emulator confidence from real-device validation?
12. Would another maintainer be able to merge this from the diff, tests and explanation without relying on "probably works"?

Do not call work complete when only the happy path is tested, tests were not run, a parser fix broadly weakens rejection, real-device behaviour is claimed without evidence, or documentation promises behaviour the code/tests do not demonstrate.

---

## 8. Commit and PR rules

Use small descriptive commits, for example:

```text
test: add provider payload fixtures
fix: recognise available-on provider actions
feat: persist provider whitelist
feat: add update awareness
feat: add TizenTube Cobalt target
ci: upload debug APK artifact
```

Avoid vague messages such as `updates`, `fix stuff`, `changes`, or `wip`.

Every implementation PR must target `release/1.2` and contain:

```markdown
## What changed
<short concrete summary>

## Why
<bug / issue / release reason>

## Behavioural risk
<what could regress and how the change limits that risk>

## Validation
- [ ] :app:runHelperTests
- [ ] :app:lintDebug
- [ ] :app:assembleDebug

## Real-device testing
Not required for this PR / Required before release / Completed: <details>
```

Link the relevant issue where applicable.

Do not merge your own implementation PR unless the owner explicitly asks.

---

## 9. Integration / merge order

Preferred dependency-aware order into `release/1.2`:

1. reliability / regression fixtures;
2. Disney/provider parser fix;
3. provider whitelist;
4. update awareness;
5. TizenTube Cobalt target;
6. CI/release hardening;
7. combined validation;
8. independent Astra review;
9. real Google TV release-candidate validation;
10. release documentation/version preparation;
11. final `release/1.2 -> main` PR.

Independent branches may be developed in parallel. Do not force serialization where no dependency exists.

After earlier work merges, remaining branches must update from `release/1.2` before final validation.

---

## 10. Real-device release gate

Automated tests and Android emulators are useful but are **not authoritative** for exact Google TV `launcherx` accessibility payloads, vendor-specific behaviour, or third-party target-app handling.

Before v1.2 is merged to `main`, test the release candidate on real Google TV hardware.

Minimum matrix:

- Disney+ recommendation;
- Prime Video recommendation;
- Netflix recommendation;
- ITVX recommendation;
- YouTube recommendation -> SmartTube;
- YouTube recommendation -> TizenTube Cobalt;
- TizenTube on the relevant Android 14 environment/device when available;
- Nuvio target;
- Stremio target;
- whitelisted provider;
- non-whitelisted provider;
- sponsored/advertisement card;
- normal launcher navigation/settings;
- update notice current-version path;
- update notice newer-version path where safely testable;
- accessibility service reconnect/restart behaviour.

If a real-device case fails, capture the smallest useful payload/diagnostic and convert it into a regression fixture when possible.

---

## 11. Stop conditions

Stop and report rather than guessing if:

- the required branch is missing and cannot be created;
- the whitelist workstream has no owner's implementation to integrate;
- a parser fix appears to require broad weakening of sponsored/ad/UI rejection;
- TizenTube's real launch contract cannot be established from reliable evidence;
- a requested change would add telemetry, accounts, viewing-data collection or an unnecessary backend;
- signing credentials/secrets would be required;
- real Google TV data is required but unavailable and the behaviour cannot be bounded safely;
- the task requires unrelated architecture beyond v1.2 scope.

Use:

```text
BLOCKED
Reason: <specific reason>
What I verified: <facts>
What is needed: <smallest missing input/action>
```

---

## 12. Definition of done for v1.2

v1.2 is ready for a release PR only when:

- provider regression fixture coverage is in place;
- Disney/provider issue #2 is fixed or explicitly deferred with evidence;
- whitelist issue #1 is integrated and validated;
- update-awareness issue #11 is implemented and validated;
- TizenTube Cobalt issue #12 is implemented and its Android 14 reliability concern is addressed with evidence;
- SmartTube remains regression-free;
- helper tests pass;
- lint passes;
- debug build passes;
- CI passes on the combined release branch;
- independent review has no unresolved blocker/important findings;
- real Google TV validation matrix has been completed;
- `CHANGELOG.md`, `README.md`, and `ROADMAP.md` reflect actual shipped behaviour;
- version code/name are updated only when preparing the actual release candidate.

---

## 13. Current branch/state summary

Stable:

`main` — v1.1.0 baseline/public stable branch.

Integration:

`release/1.2` — all v1.2 work merges here first.

Work branches:

- `chore/reliability-tests`
- `fix/disney-provider-parsing`
- `feature/whitelist`
- `feature/update-checker`
- `feature/tizentube-cobalt`
- `chore/ci-release` (create from latest `release/1.2` if absent)

Known issues:

- `#1` provider whitelist — owner has stated a local implementation exists and should be integrated rather than recreated.
- `#2` Disney+ links not resolving properly.
- `#11` update awareness — required for v1.2.
- `#12` TizenTube Cobalt target — required for v1.2, with Android 14 reliability as a release concern.

CI workflow exists on `release/1.2`.

---

## 14. One-line autonomous instructions

### Worker

> Checkout the latest `release/1.2`, read `AGENTS.md` completely, inspect existing open PRs so you do not duplicate work, execute only your assigned workstream on its prescribed branch, meet the "What good looks like" quality bar, run all required validation, perform the mandatory self-review, and open a PR back to `release/1.2`; stop with a precise `BLOCKED` report rather than guessing outside this runbook.

### Orchestrator

> Read `AGENTS.md` completely, inspect current repository/issue/PR/branch state, coordinate independent v1.2 workers without branch overlap, inspect every worker's actual diff and validation evidence, relay only cross-workstream dependencies, integrate in a dependency-aware order, run combined validation, commission an independent final review, and stop before `main` until the owner explicitly approves the release.