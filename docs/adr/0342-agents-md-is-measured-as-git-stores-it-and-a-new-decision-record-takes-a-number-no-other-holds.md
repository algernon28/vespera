# ADR-342 — AGENTS.md is measured as git stores it, and a new decision record takes a number no other holds

- **Date**: 2026-11-04
- **Status**: accepted on 2026-11-04.
- **Settles**: what the claims job measures of AGENTS.md against its ceiling, how the number of a new decision record is chosen, and what the claims job holds of the records' numbers.
- **Keeps**: [ADR-259](0259-no-document-keeps-a-count-of-the-decision-records-and-agents-md-says-how-a-command-is-written-under-the-guard.md) §1: no document states how many records there are or the highest number. The number a new record takes is printed by a script and written nowhere.

## Context

The claims job holds AGENTS.md to 30,000 bytes, a ceiling paid for by every agent that reads the file before working. It measured the bytes of the file in the working copy. A checkout with `core.autocrlf` set writes each line ending as CRLF, so it measures one byte more per line than git stores: text that is 29,354 bytes in git measures 29,531 in such a checkout. Text git stores under the ceiling can measure over it there, and the claim goes red on a Windows checkout and green in CI for the same commit.

A record takes the next number by its writer's reading of `docs/adr`. Two changes written at once read the same directory and take the same number, and a change on a branch cut before another merged takes a number main already holds. No check went red for either: two files under `docs/adr` of one number pass every claim, and the links between records resolve by file name. The numbers 299, 300 and 311 are held by no record, so the lowest number free is below the highest.

## Decision

### 1. AGENTS.md is measured as git stores it

The claim "how much AGENTS.md carries" measures the file's UTF-8 bytes with each CRLF counted as one byte, the LF git stores under `core.autocrlf`. A CR standing alone is counted as it stands. The working copy is what is read, so an edit not yet staged or committed counts; only its line endings are read as git stores them. The ceiling stays 30,000 bytes.

### 2. `docs/next-adr-number.mjs` prints the number a new record takes

`node docs/next-adr-number.mjs` prints the lowest number above the highest record on `origin/main` that no record on this branch's tree holds and no open pull request of `algernon28/vespera` adds. A number under `origin/main`'s highest is never printed, though 299, 300 and 311 are free: the highest number is the last file, and a record placed in a gap would not be.

- **A record** is a file `NNNN-<title>.md` directly under `docs/adr`, its number the four digits. The index, `docs/adr/README.md`, and every other file are none.
- **What a pull request adds** is each file `gh pr list --json files` gives with a `changeType` of `ADDED` or `RENAMED`, or with none. A file it edits or deletes is not read: an edited record is one `origin/main` already holds, and a deleted one `origin/main` holds until it merges.
- **The open pull requests** are read by `gh pr list --repo algernon28/vespera --state open --json number,headRefName,files`.
- **`origin/main`** is fetched first; where the fetch cannot reach the remote, the ref is read as it stands and a line on standard error says so.

It exits 0 when the number is checked against `origin/main`, the branch and every open pull request. Where `gh` is not installed, not signed in, or cannot reach GitHub, it still prints the number from `origin/main` and the branch, writes on standard error that the open pull requests were not read and the number may be one of theirs, and exits 2. Where `origin/main` cannot be resolved it prints no number, says why on standard error, and exits 1.

### 3. The claims job holds the numbers, in two claims

- **"no two decision records share a number"** fails where two different files under `docs/adr` in the tree take one number, naming the number and both files. It reads no network and runs on every run.
- **"the numbers of the records this branch adds"** fails where a record this branch adds over `origin/main`, a file git lists as added between their merge base and `HEAD`, takes a number that a record of `origin/main`, or one an open pull request other than this branch's adds, holds under a different file name. It names the number, both files, and `origin/main` or the pull request's number. A file of the same name is the same record, and no collision. This branch is `git rev-parse --abbrev-ref HEAD`, or `GITHUB_HEAD_REF` where `HEAD` is detached; the open pull request whose head is this branch is not read against it, and with no branch known none is left out.

The second claim reads the open pull requests only under `--with-network`, as the wayfinder map claim reads the tracker: a run without it asks no network, which the script's header promises. It is reported NOT CHECKED, with its reason, without `--with-network`, where `gh` is absent or answers with an error, and where `origin/main` or its merge base with `HEAD` cannot be resolved, as in a shallow checkout. It never passes where it did not read.

### 4. What reads no file and asks no network is one module

`docs/check-claims-lib.mjs` exports what both scripts compute:

- `agentsMdSize(text)` gives `{ size, within }`: §1's size of the text and whether it is at most 30,000.
- `adrNumber(path)` gives a record's number from its path from the repository's root, or `null` for a path that is no record.
- `sharedNumbers(paths)` gives `{ number, files }` for each number two different paths take, the files sorted, in order of number.
- `nextAdrNumber({ main, branch, prs })` gives §2's number from the paths of `origin/main`'s tree, the paths of this branch's tree, and the open pull requests as `gh` gives them.
- `adrCollisions({ added, main, prs, branch })` gives `{ number, added, other, where }` for each collision of §3's second claim, `where` being `origin/main` or `#` and the pull request's number, in order of number, then `origin/main` before the pull requests, then the pull requests by number.

`docs/check-claims.mjs` and `docs/next-adr-number.mjs` import it, and call git and `gh` themselves.

### 5. The writer is told where the number comes from

The line of `docs/adr/README.md` that says how the records are numbered names `node docs/next-adr-number.mjs`. AGENTS.md is not lengthened.

### 6. The claims workflow runs the tests and reads `origin/main`

`.github/workflows/claims.yml` runs `node --test src/test/docs/check-claims.test.mjs` beside the workflow's other test of a script. Its checkout fetches `origin/main` with enough history for a merge base with the pull request's head, and its token may read pull requests, so that the second claim of §3 is checked in CI and not reported NOT CHECKED there.

### 7. Run ids

No class under `src/main` changes, so no stage's version moves.

## Consequences

- **A Windows checkout reads AGENTS.md's size as CI does.** The ratchet still binds: a paragraph that takes the stored text over 30,000 bytes fails on both.
- **Two records of one number in the tree fail the claims job on every run**, CI's included, with no network.
- **A collision with an open pull request is found before either merges**, wherever the job runs with `--with-network`, `gh` and `origin/main`.
- **Two writers who run `next-adr-number.mjs` before either opens a pull request still take one number.** The second claim finds it once the first pull request is open.

## Alternatives refused

- **Measure the index's or `HEAD`'s blob of AGENTS.md, `git cat-file -s`.** It does not see an edit not yet staged, which is when a writer runs the check.
- **Print the lowest number free anywhere.** That is 299, and a record there sorts before forty older ones.
- **Read the open pull requests on every run, without `--with-network`.** A run without the flag asks no network, and a check that waits on GitHub offline would make every local run slower or red for no fault of the tree.
- **Keep the next number in a file, or claim it on the tracker.** A file every new record edits is the conflict ADR-259 removed, and a claim on the tracker is one more thing to keep current by hand.

## Tests

- `src/test/docs/check-claims.test.mjs` holds §1's measure, §2's choice of number, §3's reading of a record's number, of a number two files share, and of a collision, and §4's shapes, with no network. It also runs `docs/check-claims.mjs` without `--with-network` and holds that it reports AGENTS.md's size as git's index blob of it, that the tree's records share no number, and that the second claim of §3 is reported NOT CHECKED.
- Not held by a test: the scripts' calls to git and `gh`, `next-adr-number.mjs`'s exit statuses and lines on standard error, and §6's workflow.

## What this does not decide

- The ceiling's value.
- Whether 299, 300 and 311 are ever taken: no record is renumbered.
