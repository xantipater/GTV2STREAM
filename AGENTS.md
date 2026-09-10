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
- regression-testing / CI hardening release.

Do **not** expand the release into Plex, Jellyfin, Fire TV, a UI redesign, analytics, accounts, cloud services, or unrelated feature work.

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
9. Do not refactor unrelated code while fixing a bug.
10. Every parser/routing bug fix must include regression coverage.
11. If real launcher behaviour is unknown, do not invent it. Add support only for known payloads or clearly bounded patterns.
12. If you cannot complete a required validation step, state exactly what was not verified in the PR.

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
- `app/src/main/java/com/gtv2stream/YouTubeLauncher.java` — SmartTube launch path.
- `app/src/test/java/com/gtv2stream/DeepLinkHelperTest.java` — current dependency-free JVM regression test harness.
- `.github/workflows/ci.yml` — v1.2 CI workflow on `release/1.2`.

Before editing parser/routing behaviour, read at minimum:

1. `README.md`
2. `RecommendationTitleParser.java`
3. `TvRecommendationService.java`
4. `DeepLinkHelperTest.java`

---

## 3. Start procedure for every agent

Run these steps before making changes.

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

Before editing, run:

```bash
git status
git branch --show-current
```

Confirm you are on the correct assigned branch and the working tree is clean.

---

## 4. Workstream selection

If the owner gives you a named workstream, follow that section only.

If the owner simply says "work on v1.2" and gives no specific assignment, do **not** choose randomly. Check open PRs/issues and branch activity first, then take the first incomplete workstream in this priority order:

1. Reliability / payload regression harness
2. Disney/provider parser fix
3. Whitelist integration
4. CI/release hardening
5. Independent review

Do not duplicate work already present in an open PR.

---

# WORKSTREAM A — Reliability / payload regression harness

## Branch

`chore/reliability-tests`

## Goal

Make real Google TV launcher payload bugs reproducible in automated tests so a physical TV is not required for every parser change.

## Scope

You may modify primarily:

- `app/src/test/java/com/gtv2stream/DeepLinkHelperTest.java`
- new test fixture/helper files under `app/src/test/`
- minimal production-code seams only if absolutely required to make deterministic testing possible

Do not redesign the application architecture.

## Required cases

Tests/fixtures must cover representative examples of:

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

Use a simple deterministic fixture model. A fixture should make clear:

- raw payload/input;
- expected title, if any;
- expected YouTube/source classification where relevant;
- whether it should be accepted or rejected.

Do not build a large emulator framework for this workstream.

## Acceptance criteria

- Existing helper tests still pass.
- New fixture tests fail if the corresponding parser behaviour regresses.
- Ads, sponsored content, UI chrome and ambiguous text remain rejected.
- Test data contains no private user information.

## PR

Target: `release/1.2`

Suggested title:

`test: add Google TV payload regression fixtures`

---

# WORKSTREAM B — Disney/provider parsing

## Branch

`fix/disney-provider-parsing`

## Related issue

Issue `#2` — Disney+ links not resolving properly.

## Goal

Harden provider-action parsing without broadly relaxing title detection.

## Important existing behaviour

`Disney+` and `Disney Plus` are already recognised provider names. Treat the likely failure as payload/action-shape handling unless evidence proves otherwise.

Current known action families include variants of:

- `Watch on ...`
- `Watch Now on ...`
- `Stream on ...`
- `Streaming on ...`
- `New on ...`
- `Included with ...`

## Required regression inputs

At minimum test bounded forms equivalent to:

```text
Daredevil. Watch on Disney+
Daredevil. Available on Disney+
Disney+. Daredevil.
Daredevil — Disney+
Daredevil, Disney+
```

Also cross-check equivalent forms for at least Netflix, Prime Video and ITVX.

Do not assume every English phrase containing a provider name is a valid action.

## Implementation rules

- Prefer narrow action-pattern additions over permissive free-form matching.
- Preserve provider stripping so provider names never leak into returned titles.
- Preserve title punctuation handling, including titles such as `Mr. Robot` and initialisms.
- Preserve hard rejection of sponsored/advertisement payloads.
- Do not alter launcher UI-word rejection unless a failing regression test proves it is necessary.

## Acceptance criteria

- Issue #2's known/expected payload forms parse correctly.
- Equivalent existing provider cases continue to work.
- No new ad/sponsored/UI false-positive test failures.
- `runHelperTests`, `lintDebug` and `assembleDebug` pass.

## PR

Target: `release/1.2`

Suggested title: `fix: harden provider action parsing`

Reference issue `#2` in the PR body.

---

# WORKSTREAM C — Provider whitelist

## Branch

`feature/whitelist`

## Related issue

Issue `#1` — Add a whitelist function.

## Critical instruction

**Do not recreate the whitelist from scratch if the owner's local implementation has not yet been pushed.**

If the assigned branch does not contain a whitelist implementation, stop implementation work and report:

`BLOCKED: owner whitelist implementation is not present on the remote branch yet.`

You may inspect surrounding code and prepare tests/review notes, but do not invent a second competing implementation.

## Required behaviour once implementation exists

A whitelisted provider must bypass GTV2STREAM so Google TV handles the recommendation normally.

```text
Prime recommendation
+ Prime Video whitelisted
=> GTV2STREAM does not redirect
=> normal Google TV/provider behaviour continues
```

A non-whitelisted provider continues through the configured redirect target.

```text
Netflix recommendation
+ Netflix not whitelisted
=> normal GTV2STREAM Nuvio/Stremio redirect
```

Existing users who have configured no whitelist must retain current v1.1 behaviour.

## Review checklist

Verify:

- preferences persist across app restart;
- empty/default whitelist changes nothing for existing users;
- provider matching is exact/bounded enough not to whitelist a title accidentally;
- YouTube behaviour remains correct;
- ads/sponsored items remain rejected;
- no new cloud storage/account behaviour is introduced;
- Settings UI remains usable with a TV remote;
- any preference migration/default handling is safe.

## Acceptance criteria

- Whitelisted providers bypass redirect.
- Non-whitelisted providers redirect normally.
- Existing-user default behaviour is preserved.
- Required Gradle checks pass.

## PR

Target: `release/1.2`

Suggested title: `feat: add provider whitelist`

Reference issue `#1` in the PR body.

---

# WORKSTREAM D — CI / release engineering

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
- failures remain easy to diagnose;
- the debug APK is uploaded as a workflow artifact if straightforward.

Do not add signing secrets.
Do not automate production publishing.
Do not create GitHub releases automatically in v1.2 unless the owner explicitly requests it.

## Acceptance criteria

- Workflow syntax is valid.
- A PR into `release/1.2` triggers expected checks.
- CI does not require repository secrets for normal PR validation.
- Build/test commands match the project's Java 17 / Android build configuration.

## PR

Target: `release/1.2`

Suggested title: `ci: harden Android build and test workflow`

---

# WORKSTREAM E — Independent reviewer

The reviewer should not be the agent that authored the changes under review.

Review all open PRs targeting `release/1.2` plus the resulting combined release branch.

Look specifically for parser false positives/negatives, duplicated provider logic, regex overreach, broken whitelist defaults, title/provider confusion, secret leakage, telemetry/privacy regressions, unnecessary Android permissions, lifecycle regressions, dead code, ineffective tests and scope creep.

For each problem provide:

1. severity: blocker / important / minor;
2. exact file and relevant code area;
3. concrete failure scenario;
4. smallest reasonable fix.

Do not redesign the app unless a blocker genuinely requires it.

---

## 5. Required validation before any PR is marked ready

Run from repository root:

```bash
./gradlew :app:runHelperTests --stacktrace
./gradlew :app:lintDebug --stacktrace
./gradlew :app:assembleDebug --stacktrace
```

Record the result of each in the PR body.

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

Never mark an unrun test as passed.

---

## 6. Commit rules

Use small, descriptive commits such as:

```text
test: add provider payload fixtures
fix: recognise available-on provider actions
feat: persist provider whitelist
ci: upload debug APK artifact
```

Avoid vague messages such as `updates`, `fix stuff`, `changes`, or `wip`.

---

## 7. PR rules

Every implementation PR must target `release/1.2`.

PR body must contain:

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

Do not merge your own implementation PR unless the owner explicitly asks you to.

---

## 8. Integration / merge order

Preferred order into `release/1.2`:

1. reliability / regression fixtures;
2. Disney/provider parsing fix;
3. whitelist integration;
4. CI/release hardening;
5. independent combined review;
6. real Google TV release-candidate validation;
7. final `release/1.2 -> main` PR.

After an earlier workstream merges, remaining agents should update from `release/1.2` before final validation.

---

## 9. Real-device release gate

Automated tests and Android emulators are useful but are **not authoritative** for exact accessibility payloads produced by the Google TV `launcherx` package or vendor-specific behaviour.

Before v1.2 is merged to `main`, test the release candidate on real Google TV hardware against at least:

- Disney+;
- Prime Video;
- Netflix;
- ITVX;
- YouTube -> SmartTube;
- Nuvio;
- Stremio;
- whitelisted provider;
- non-whitelisted provider;
- sponsored/advertisement card;
- normal launcher navigation/settings;
- accessibility service reconnect/restart behaviour.

Convert real-device failures into regression fixtures whenever possible.

---

## 10. Stop conditions

Stop and report rather than guessing if:

- the required branch is missing and you cannot create it;
- the whitelist implementation is not present remotely;
- a fix seems to require broadly weakening ad/sponsored/UI rejection;
- the change would add telemetry, a backend, accounts or off-device viewing data;
- signing credentials or secrets are required;
- real launcher payload data is required but unavailable and safe behaviour cannot be bounded;
- the task requires unrelated architectural work beyond v1.2.

Use:

```text
BLOCKED
Reason: <specific reason>
What I verified: <facts>
What is needed: <smallest missing input/action>
```

---

## 11. Definition of done for v1.2

v1.2 is ready for a release PR only when:

- provider regression fixture coverage exists;
- issue #2 is fixed or explicitly deferred with evidence;
- issue #1 is integrated and validated;
- helper tests pass;
- lint passes;
- debug build passes;
- CI passes on the combined release branch;
- independent review has no unresolved blockers;
- real Google TV validation is complete;
- `CHANGELOG.md`, `README.md` and `ROADMAP.md` match actual shipped behaviour;
- version code/name are updated only when preparing the release candidate.

---

## 12. Current state

- `main` — stable v1.1.0 branch.
- `release/1.2` — v1.2 integration branch and source of truth for current development.
- `chore/reliability-tests` — test-harness workstream.
- `fix/disney-provider-parsing` — issue #2 workstream.
- `feature/whitelist` — issue #1 workstream; do not recreate missing local work.
- `chore/ci-release` — CI workstream; create when needed.
- `.github/workflows/ci.yml` exists on `release/1.2`.

---

## 13. Exact autonomous-agent instruction

If you were given no other instructions, do this:

> Fetch the repo, checkout the latest `release/1.2`, read `AGENTS.md` completely, inspect open PRs/issues to avoid duplicate work, take the highest-priority incomplete workstream you can safely execute, use only its prescribed branch and scope, implement the smallest correct change, run every required validation command, open a PR to `release/1.2` using the required PR format, and stop with the documented `BLOCKED` report instead of guessing outside this runbook.
