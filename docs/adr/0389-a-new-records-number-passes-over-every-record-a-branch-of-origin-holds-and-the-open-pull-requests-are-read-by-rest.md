# ADR-389 — A new record's number passes over every record a branch of origin holds, and the open pull requests are read by REST

- **Date**: 2026-11-14
- **Status**: accepted on 2026-11-14.
- **Settles**: which numbers `node docs/next-adr-number.mjs` holds as taken besides those of `origin/main`, the branch and the open pull requests; what the claim "the numbers of the records this branch adds" reads; and how both read the open pull requests. Settles #766.
- **Amends**: [ADR-346](0346-agents-md-is-measured-as-git-stores-it-and-a-new-decision-record-takes-a-number-no-other-holds.md) §2, the fetch, the reading of the open pull requests, their list's limit and the script's exit; §3, what the second claim reads and the parts it names as not read; §4, the functions' arguments and what `unreadPullRequests` gives; and §6, what the claims workflow's token reads.
- **Keeps**: ADR-346 §1 and §5, and §2's rule that no number under `origin/main`'s highest is printed. [ADR-259](0259-no-document-keeps-a-count-of-the-decision-records-and-agents-md-says-how-a-command-is-written-under-the-guard.md) §1: the number a record takes is printed by the script and written nowhere. [ADR-350](0350-the-claims-job-reads-the-tracker-off-a-pull-requests-ci-and-a-new-records-slug-keeps-to-120-characters.md) whole. `.github/workflows/claims.yml` unchanged. No run id moves.

## Context

A number is taken from the moment its writer chooses it, and the script saw it only once a pull request adding it was open. A record on a branch pushed to origin with no pull request yet, and a record in a worktree not pushed, were invisible to it, so it printed numbers that were taken, and the claim of the numbers a branch adds passed a collision with them.

`gh pr list` is answered through GitHub's GraphQL API, which is refused inside Claude Code: there it answers HTTP 403, so a run of either script inside Claude Code read no open pull request at all, and `next-adr-number.mjs` exited 2 every time.

### Measured

On 2026-11-14, against `algernon28/vespera`, from a shallow clone inside Claude Code:

- **The branches of origin.** Nineteen: `main`, seven under `claude/`, and eleven under `javafx`, `javafx-control-panel-r3`, `prototype/`, `research/` and `wip/`. Three of those eleven hold records `origin/main` holds at no such path (`javafx-control-panel-r3` 0339 to 0341, `wip/381-shingle-index-draft` 0182, and another branch under `wip/`, of drafts, 0170 to 0173); four of the seven under `claude/` do.
- **Reading them.** After one fetch of every branch, `git ls-tree` of `docs/adr` on the eighteen branches but `main` took 70 ms in all. Nine of the eighteen have no merge base with `origin/main` in that shallow clone.
- **The REST API.** `gh pr list` answers HTTP 403, GraphQL being unavailable inside Claude Code; `gh api repos/algernon28/vespera/pulls?state=open` answers in 0.5 s and `gh api repos/algernon28/vespera/pulls/N/files` in 0.9 s for 77 files. The REST list of pull requests gives no count of a pull request's changed files; GitHub documents that `pulls/N/files` lists at most 3,000.

## Decision

### §1. Every branch of origin holds the numbers of its records

- **The branches read** are every branch of origin but `main`, whatever its name, not only those under `claude/`. A record pushed under `wip/` or `prototype/` holds its number as one under `claude/` does, three of the eleven branches outside `claude/` hold records, and reading all of them costs one `git ls-tree` each.
- **What a branch holds** is each record of its tree, `git ls-tree -r --name-only refs/remotes/origin/<name> -- docs/adr`, at a path `origin/main` does not hold. A record is one as ADR-346 §2 reads it. The tree is read, not what the branch adds over its merge base with `origin/main`, because the tree needs only the branch's tip, and a shallow clone has no merge base for half the branches. A branch that is merged, or holds only `origin/main`'s records, holds nothing.
- **The fetch** is one: `git fetch --no-tags --prune origin +refs/heads/*:refs/remotes/origin/*`, in place of ADR-346 §2's fetch of `main` alone. `--prune` drops the ref of each branch origin no longer has, so a deleted branch holds nothing.
- **`next-adr-number.mjs`** prints the lowest number above `origin/main`'s highest that no record of this branch's tree, no record an open pull request adds and no record a branch of origin holds takes. A number held on a branch with no pull request is taken as one an open pull request adds: it is never printed, and the script prints the lowest number past it. This branch's own ref on origin is read with the others, the numbers it holds being this branch's or ones it gave up. Where the fetch fails, the refs are read as they stand, and standard error names `origin/main`, the branches of origin and the fetch's error; where the refs cannot be listed or a branch's tree cannot be read, no branch is read and standard error names the branches of origin. Either exits 2, the number printed from what was read.
- **The claim "the numbers of the records this branch adds"** also fails where a record this branch adds takes a number that a branch of origin other than this branch holds under a different file name, naming the number, both files and `origin/<name>`. Its collisions are listed by number, then `origin/main`'s, then the open pull requests' by number, then the branches' by name. It reads the branches only under `--with-network`, and only once the fetch succeeded: a ref not fetched may be one origin dropped, or lack a record pushed since. Where they were not read, it is NOT CHECKED, naming the branches of origin; its PASS names how many branches other than this one it read. This branch is set aside by the name ADR-346 §3 reads.

`next-adr-number.mjs` and the claim read one set: each number the script passes over above `origin/main`'s highest is one the claim fails a record of this branch for taking under another name.

### §2. The open pull requests are read through REST

- **The list** is `gh api --paginate repos/algernon28/vespera/pulls?state=open&per_page=100`, each pull request's `number` and `head.ref`, in place of `gh pr list`. Every page is read, so the list has no limit: ADR-346 §2's `--limit 100`, and the list that fills it, go, and with them the claim's NOT CHECKED part for it.
- **A pull request's files** are `gh api --paginate repos/algernon28/vespera/pulls/N/files?per_page=100`, each file's `filename` its path and its `status` upper-cased its `changeType`, `removed` read as `DELETED`, as ADR-346 §2 reads a paged one. Every open pull request's files are read so, this branch's own included.
- **Read in full** is a list of fewer than 3,000 files, its count the pull request's count of changed files. A list of 3,000, a paging that fails, and an answer that cannot be read leave the pull request with no count, cut short as ADR-346 §2 says. The count is not asked of `pulls/N`: a call more for each pull request buys a count that matters only past 3,000 files.
- **Where `gh` is absent, signed out, or its list fails**, the open pull requests are not read, as ADR-346 §2 and §3 say: the script prints the number from the rest and exits 2, and the claim is NOT CHECKED, naming `gh`'s error.

### §3. What the functions take, and where the reading is

`docs/check-claims-lib.mjs` keeps reading no file and asking no network. A branch is `{ name, tree }`, its name on origin and the paths of its tree under `docs/adr`; `branches` is `null` where the branches were not read.

- `nextAdrNumber({ main, branchTree, prs, branches })` gives §1's number.
- `adrCollisions({ added, main, prs, branches, branchName })` and `adrCollisionClaim({ added, main, prs, branches, branchName })` give §1's collisions and claim; neither takes a limit.
- `unreadPullRequests({ prs, branchName })` gives `{ incomplete }`, the numbers, in order, of the pull requests other than this branch's that are cut short.
- `nextAdrNumberExit({ fetched, prs, branches, branchName })` gives 0 where `fetched` is true, neither `prs` nor `branches` is `null`, and no pull request other than this branch's is cut short, and 2 otherwise.
- `restPullRequest({ number, headRefName, files })` gives a pull request as §2 reads it from REST, `files` being the `{ filename, status }` GitHub gave or `null` where they were not read; `REST_FILES_CAP` is 3,000.

`docs/adr-numbers-read.mjs` makes the reads both scripts make: the fetch of §1, the branches of origin, the open pull requests of §2, and this branch's name as ADR-346 §3 reads it. `docs/check-claims.mjs` and `docs/next-adr-number.mjs` call it and pass what it read to the functions above.

### §4. The claims workflow is unchanged

Its checkout, with `fetch-depth: 0`, already brings every branch of origin, and its token, which may read pull requests, reads them through REST as through GraphQL.

### §5. Run ids

No class under `src/main` changes, so no stage's version moves.

## Consequences

- **A number held on a pushed branch with no pull request is never printed**, and a record that takes it anyway fails the claim on its pull request's CI, naming the branch.
- **A record in a worktree not pushed stays invisible**, to the script and the claim alike. Two writers who choose a number before either pushes still take one number; the claim finds it once both are pushed, or one is pushed and the other opens its pull request.
- **A branch no one works on holds its numbers until it is deleted.** One above `origin/main`'s highest is passed over, and once records past it merge it is a gap under the highest, as 299, 300 and 311 are. A record that collides with it fails the claim until it is renumbered or the branch is deleted.
- **A run inside Claude Code reads the open pull requests**, and exits 0 where everything was read.
- **Each run asks GitHub once for the list and once more for each open pull request**, about half a second each, where `gh pr list` asked once.
- **A pull request from a fork** is read through §2 alone, its branch being none of origin's.

## Alternatives refused

- **Claim a number by opening a draft pull request holding the empty record the moment it is taken.** It works only where every writer remembers to, and a writer that stops before opening one, out of credits or cut off, holds its number with no pull request, as the branch under `wip/` holding drafts 0170 to 0173 does.
- **A list of claimed numbers, kept by the project-manager agent and read by the script.** It is a file every new record edits, the conflict ADR-259 removed, or a list one agent keeps current by hand for every other.
- **Read only the branches under `claude/`.** Three branches outside it hold records today, and a writer's drafts are pushed under `wip/`; the eleven others are eleven of the `git ls-tree` calls that took 70 ms for all eighteen.
- **Read what a branch adds over its merge base with `origin/main`, as the pull requests' files are read.** A shallow clone has no merge base for nine of the eighteen branches, which would leave them unread there; the tree needs only the tip.
- **Keep `gh pr list`, and fall back to REST where it fails.** Two readers of one list, the one that runs inside Claude Code being the fallback; REST alone reads it there and in CI.
- **Read each pull request's count of changed files from `pulls/N`.** A call more for each pull request, for a count that tells more than the files' list only past 3,000 files.
- **Make a collision with a branch of origin a warning.** A branch holding the number is the same fact as an open pull request adding it, and the two differ only in whether a pull request was opened yet.

## What this does not decide

- How a record in a worktree not pushed is seen.
- Whether, when or by whom a branch no one works on is deleted.

## Tests

- `src/test/docs/check-claims.test.mjs` holds, with no network: §1's number passed over where a branch of origin with no pull request holds it, under `claude/` or not, and nothing taken by a branch holding only `origin/main`'s records; that every number `nextAdrNumber` passes over, held by an open pull request, a branch of origin or both, fails `adrCollisionClaim` for a record of this branch taking it, and the number it prints passes; a collision with a branch of origin, named `origin/<name>`; this branch's own ref and a record of the same name colliding with nothing; the order of the collisions, a branch's copy of `origin/main`'s record named once, on `origin/main`; §2's reading of a REST pull request, its statuses and `removed` as `DELETED`, read in full under 3,000 files and cut short at 3,000 or unread; `nextAdrNumberExit` giving 2 where the branches were not read; the claim's PASS naming the branches it read, its FAIL naming the branch, and NOT CHECKED naming the branches of origin where they were not read. It runs `docs/next-adr-number.mjs` in a clone of an origin on disk, to which a branch holding the next number is pushed after the clone, with `gh` signed out: the script prints the number after it, names the open pull requests as not read, and exits 2.
- Not held by a test: the reading of the open pull requests through REST where `gh` succeeds, and the `--prune` of a branch origin deleted.
