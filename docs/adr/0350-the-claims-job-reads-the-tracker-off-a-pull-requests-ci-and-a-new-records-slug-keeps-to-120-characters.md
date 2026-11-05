# ADR-350 — The claims job reads the tracker off a pull request's CI, and a new record's slug keeps to 120 characters

- **Date**: 2026-11-05
- **Status**: accepted on 2026-11-05.
- **Settles**: on which events the claim of the current wayfinder map reads the tracker, when the claims workflow runs, and how long the slug of a new decision record may be.
- **Keeps**: [ADR-346](0346-agents-md-is-measured-as-git-stores-it-and-a-new-decision-record-takes-a-number-no-other-holds.md) §1, AGENTS.md measured as git stores it, which already makes `node docs/check-claims.mjs` pass on a Windows checkout; and §3, the claim of the numbers a branch's records take, which still reads the open pull requests on every event under `--with-network`.

## Context

The claim "the current wayfinder map" reads the tracker under `--with-network`, and the claims workflow passed that flag on every pull request. A pull request's CI ran the claim against the branch's AGENTS.md and the tracker as it stood that minute. #692 went red because issue #677 gained `wayfinder:map` while the branch's AGENTS.md predated the change on main that named it. Nothing in the pull request caused it, and nothing in it could fix it but a merge of main.

A record's file is named `NNNN-<slug>.md`, the slug its title. The slug of ADR-334 is 201 characters and its path from the repository's root 218, and a clone in a temporary folder could not check it out on Windows until `core.longpaths` was set. Nothing held a slug's length.

## Decision

### 1. A pull request's CI does not read the tracker for the map

Under `--with-network`, the claim of the current wayfinder map reads the tracker unless `GITHUB_EVENT_NAME` is `pull_request` or `pull_request_target`. There it is NOT CHECKED, naming the map claimed or that none is claimed, and saying that the tracker is read only on main's push and the daily schedule. Without `--with-network` it is NOT CHECKED as before. A run outside CI, `GITHUB_EVENT_NAME` unset, reads the tracker under the flag.

`.github/workflows/claims.yml` runs on main's push, on a pull request, on a dispatch, and on a daily schedule, `23 5 * * *`, so that a map closed or labelled with no commit is caught within a day. Its concurrency group names the event as well as the ref, so that a scheduled run and a push to main cancel neither the other.

### 2. A record's slug keeps to 120 characters from ADR-350 on

The claim "the length of a decision record's slug" fails where a record under `docs/adr`, numbered 350 or above, has a slug, the file name between its number's dash and `.md`, of more than 120 characters. The line names each such record's number and its slug's length, and the limit of 120. The records before ADR-350 keep their names: none is renamed, since every link to a record is by its file name. It reads no network and runs on every run.

### 3. What reads no file is in `docs/check-claims-lib.mjs`

- `readsTrackerState({ withNetwork, eventName })` gives §1's answer: true under `--with-network` where `eventName` is neither `pull_request` nor `pull_request_target`.
- `SLUG_LIMIT` is 120 and `FIRST_CAPPED` is 350.
- `overlongSlugs(paths)` gives `{ number, path, length }` for each record of §2 past the limit, in order of number.
- `slugClaim(paths)` gives `{ state, detail }`, `FAIL` naming each such record, its length and the limit, or `PASS` naming the limit.

### 4. Run ids

No class under `src/main` changes, so no stage's version moves.

## Consequences

- **A pull request's CI never fails on a tracker label or a close it did not make.** The map claim goes red on main's push or the next day's schedule instead, where the fix is an edit to AGENTS.md on main.
- **A map that closes with no commit is caught within a day**, not at the next push to main.
- **A record named past 120 characters fails the claims job on its own pull request**, offline and in CI alike. A path from the repository's root is then at most 137 characters.

## Alternatives refused

- **Keep the map claim on pull requests and have its failure line say to merge main.** The pull request still goes red for a change it did not make, and a branch already level with main gets the same advice for a map closed that hour.
- **Cap a slug for every record.** Seventy-one records are past 120 characters, and renaming them breaks every link to them by file name.
- **Cap the whole path instead of the slug.** The path's prefix depends on where the clone is; the slug is what the writer chooses.

## Tests

- `src/test/docs/check-claims.test.mjs` holds §3's `readsTrackerState` on a push, a schedule, a dispatch, outside CI and on both pull request events, with and without `--with-network`; `slugClaim` failing on a slug of 121 characters from ADR-350 on, naming the record, its length and 120, and a slug of exactly 120 passing; a record before ADR-350 kept, however long; and `overlongSlugs` reading the slug between the number's dash and `.md`, a file under a folder of `docs/adr` being no record. It runs `docs/check-claims.mjs --with-network` with `GITHUB_EVENT_NAME` `pull_request` where `fetch()` rejects and holds the map claim NOT CHECKED, naming the pull request and main; and runs it without the flag and holds the slug claim `PASS` on this tree, naming 120.
- Not held by a test: §1's schedule and concurrency group in the workflow.

## What this does not decide

- Whether the records past 120 characters are ever renamed.
- What the claim of the numbers a branch's records take reads on a pull request: ADR-346 §3 stands.
