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
- `.github/workflows/ci.yml` — v1.2 CI workflow.

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

Also cross-check equivalent forms for at least:

- Netflix;
- Prime Video;
- ITVX.

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

Suggested title:

`fix: harden provider action parsing`

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

Example:

```text
Prime recommendation
+ Prime Video whitelisted
=> GTV2STREAM does not redirect
=> normal Google TV/provider behaviour continues
```

A non-whitelisted provider continues through the configured redirect target.

Example:

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

Suggested title:

`feat: add provider whitelist`

Reference issue `#1` in the PR body.

---

# WORKSTREAM D — CI / release engineering

## Branch

`chore/ci-release`

Create it from the latest `release/1.2` if it does not already exist.

## Goal

Make CI a useful release gate, not a complicated release system.

## Existing required checks

CI should run at least:

```bash
./gradlew :app:runHelperTests --stacktrace
./gradlew :app:lintDebug --stacktrace
./gradlew :app:assembleDebug --stacktrace
```

## Tasks

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

Suggested title:

`ci: harden Android build and test workflow`

---

# WORKSTREAM E — Independent reviewer

## Rule

The reviewer should not be the agent that authored the changes under review.

## Inputs

Review all open PRs targeting `release/1.2` plus the resulting combined release branch.

## Review priorities

Look specifically for:

- parser false positives;
- parser false negatives introduced by over-tightening;
- duplicated provider recognition logic;
- action-regex overreach;
- broken whitelist defaults/migration;
- title/provider confusion;
- accidental secret or credential leakage;
- telemetry/privacy regressions;
- new unnecessary Android permissions;
- race/lifecycle regressions in `TvRecommendationService`;
- dead code;
- tests that do not actually exercise the intended path;
- unrelated refactors/scope creep.

## Reviewer output

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

Use this exact format:

```text
Validation
- [x] :app:runHelperTests
- [x] :app:lintDebug
- [x] :app:assembleDebug
```

If something cannot run, use:

```text
- [ ] :app:lintDebug — NOT RUN: <specific reason>
```

Never mark an unrun test as passed.

---

## 6. Commit rules

Use small, descriptive commits.

Good examples:

```text
test: add provider payload fixtures
fix: recognise available-on provider actions
feat: persist provider whitelist
ci: upload debug APK artifact
```

Avoid vague messages such as:

```text
updates
fix stuff
changes
wip
```

Do not combine unrelated changes in one commit solely to reduce commit count.

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

Automated tests and Android emulators are useful but are **not authoritative** for the exact accessibility payloads produced by the Google TV `launcherx` package or vendor-specific TV behaviour.

Before v1.2 is merged to `main`, the release candidate must be tested on real Google TV hardware.

Minimum real-device matrix:

- Disney+ recommendation;
- Prime Video recommendation;
- Netflix recommendation;
- ITVX recommendation;
- YouTube recommendation -> SmartTube;
- Nuvio target;
- Stremio target;
- whitelisted provider;
- non-whitelisted provider;
- sponsored/advertisement card;
- normal launcher navigation;
- normal settings interactions;
- accessibility service reconnect/restart behaviour.

If a real-device case fails, capture the smallest useful payload/diagnostic information and convert it into a regression fixture when possible.

---

## 10. What good looks like

Passing tests is necessary, but it is not enough. A change is good only when the implementation is small, bounded, understandable, regression-protected, and does not quietly alter unrelated behaviour.

### Good implementation standard

For every implementation or bug fix, aim for all of the following:

- Solve the assigned problem, not a larger adjacent problem.
- Change the smallest reasonable amount of production code.
- Reuse existing helpers and concepts rather than creating duplicate provider/parser logic.
- Keep behaviour explicit and bounded. Avoid broad heuristics when a narrow rule will solve the observed case.
- Add a regression test that would fail on the previous behaviour and pass with the fix.
- Add negative/boundary coverage where the change could create false positives.
- Preserve all existing tests and established v1.1 behaviour unless the workstream explicitly changes it.
- Keep code readable enough that another maintainer can understand why the rule exists without reconstructing the entire bug report.
- Avoid speculative abstractions, premature frameworks, and unrelated cleanup.
- Make the PR explain exactly what changed, why it is safe, what could regress, and what was actually tested.

### Parser-specific quality bar

For parser/routing changes, "good" means more than recognising the new input. The change must also show that nearby unsafe inputs remain rejected.

A good parser fix normally includes:

- at least one positive case for the reported/target payload;
- equivalent positive cases for relevant existing providers where the rule is provider-agnostic;
- at least one negative or boundary case showing the new rule is not free-form;
- confirmation that sponsored/advertisement content remains rejected;
- confirmation that launcher UI/settings text remains rejected where relevant;
- confirmation that title punctuation/provider stripping still behaves correctly.

### Example: bad vs good

Bad:

```text
Problem: Disney+ "Available on" payload is missed.
Change: accept any string containing "Disney+" or "available".
Tests: one Disney+ happy-path test.
Result: test passes, but parser is now broadly more permissive.
```

Good:

```text
Observed/target payload:
Daredevil. Available on Disney+

Before:
The action suffix is not recognised, so the payload is rejected or misparsed.

Change:
Add one bounded "Available on <provider>" action family using the existing provider/title parsing path.

Regression coverage:
- Disney+ positive case
- Netflix equivalent positive case
- Prime Video equivalent positive case
- ITVX equivalent positive case
- sponsored equivalent remains rejected
- unrelated UI text containing "available" remains rejected
- existing punctuation/title cases still pass

Result:
The known payload shape works without turning provider recognition into free-form text matching.
```

### Tests must prove behaviour, not decorate the PR

A test is useful only if it exercises the behaviour that could break.

Do not add assertions that merely repeat implementation constants or cannot fail when the real bug returns. For bug fixes, prefer a test input representing the actual failing shape and assert the externally meaningful result: parsed title, source classification, acceptance/rejection, bypass/redirect decision, or generated deep link as appropriate.

When practical, confirm the new regression test would fail against the pre-fix behaviour before relying on it as proof of the fix.

### Mandatory pre-PR self-review gate

Before opening or marking a PR ready, answer these questions yourself. If any answer is "no" or "I don't know", fix the problem or report the uncertainty before declaring the work complete.

1. Did I solve the assigned problem rather than expanding scope?
2. Is this the smallest safe implementation I can reasonably make?
3. Is there a regression test that proves the intended new/fixed behaviour?
4. Where permissiveness changed, is there a negative/boundary test proving it did not become too broad?
5. Do all existing tests still pass?
6. Did I reuse existing logic instead of duplicating provider/parser/state handling?
7. Did I avoid unrelated refactors and cosmetic churn?
8. Could an existing v1.1 user encounter an unintended behaviour change from this diff?
9. Can I explain why every production-code change in this PR is necessary for the assigned task?
10. Did I verify rather than assume the required build/test results?
11. Does the PR clearly distinguish emulator/unit-test confidence from real Google TV validation?
12. Would I be comfortable having another maintainer merge this based only on the diff, tests and PR explanation, without relying on "probably works"?

### Do not call work complete when

- only the happy path was tested;
- tests were not run but the PR implies they passed;
- the implementation solves the issue by broadly weakening rejection rules;
- a large refactor is mixed with a small bug fix without necessity;
- the new code duplicates existing provider/title logic;
- real-device behaviour is claimed without real-device evidence;
- the implementation depends on guessed launcher payloads that were not safely bounded;
- comments or PR text promise behaviour that the tests/code do not demonstrate.

---

## 11. Stop conditions

Stop and report rather than guessing if any of these occur:

- the required branch is missing and you cannot create it;
- the whitelist workstream has no owner's implementation to integrate;
- fixing a bug appears to require weakening sponsored/ad/UI rejection broadly;
- a requested change would add telemetry, a remote backend, accounts, or send viewing data off-device;
- signing credentials or secrets would be required;
- real Google TV payload data is required but unavailable and the behaviour cannot be bounded safely;
- the task would require unrelated architectural changes beyond v1.2 scope.

Use this format:

```text
BLOCKED
Reason: <specific reason>
What I verified: <facts>
What is needed: <smallest missing input/action>
```

---

## 12. Definition of done for v1.2

v1.2 is ready for a release PR only when all of the following are true:

- provider regression fixture coverage is in place;
- Disney/provider issue #2 is fixed or explicitly deferred with evidence;
- whitelist issue #1 is integrated and validated;
- helper tests pass;
- lint passes;
- debug build passes;
- CI passes on the combined release branch;
- independent review has no unresolved blockers;
- real Google TV validation matrix has been completed;
- `CHANGELOG.md`, `README.md`, and `ROADMAP.md` reflect actual shipped behaviour;
- version code/name are updated only when preparing the actual release candidate.

---

## 13. Current branch/state summary

Stable:

`main` — v1.1.0 baseline / public stable branch.

Integration:

`release/1.2` — all v1.2 work merges here first.

Existing work branches:

- `chore/reliability-tests`
- `fix/disney-provider-parsing`
- `feature/whitelist`

Planned CI branch:

- `chore/ci-release`

Known issues:

- `#1` provider whitelist — enhancement; owner has stated a local implementation exists and should be integrated rather than recreated.
- `#2` Disney+ links not resolving properly — bug; likely provider/action payload parsing.

CI workflow exists on `release/1.2`.

---

## 14. One-line instruction for an autonomous agent

If you were given no other instructions, follow this exactly:

> Checkout the latest `release/1.2`, read `AGENTS.md` completely, inspect existing open PRs so you do not duplicate work, take the highest-priority incomplete workstream you can safely execute, work only on its prescribed branch, meet the "What good looks like" quality bar, run all required validation, perform the mandatory self-review, and open a PR back to `release/1.2`; stop with a precise `BLOCKED` report rather than guessing outside this runbook.