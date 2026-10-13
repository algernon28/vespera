# ADR-247 — Stage 4b asks whether a page is kept of its own index, and a drive Windows serves is told once

- **Date**: 2026-10-13
- **Status**: accepted on 2026-10-13, on the call of the session that worked the review of the change on `main` from `c60dc68` to `8815146` (*"fix them, test first, then open a PR"*). No question was put to the operator. Each change below that changes what the code does had a test written first that failed on the code at `8815146`, and passes after it.
- **Amends**: [ADR-245](0245-stage-4b-asks-a-page-whether-its-documents-are-kept-a-move-is-attempted-again-for-a-sharing-violation-only-on-windows-and-the-pager-writes-its-own-bound.md) §1, *"by the index on that column, sorting nothing"*: the planner chose the run's index, and does not now (§1 here). [ADR-246](0246-a-move-on-a-drive-windows-serves-is-attempted-again-and-the-survivors-pages-are-the-ledgers.md) §1, the types of a drive Windows serves and when the drive is asked (§2, §3), and §3, *"planned by the index on that column"* (§1). Each carries a note at its head.
- **Applies, and does not amend**: [ADR-211](0211-no-class-holds-every-survivor-of-a-run-another-tables-rows-are-read-a-page-of-survivors-at-a-time.md) §8, the run or walk compared as a value so that the planner looks a document up by its key (§1); [ADR-216](0216-nothing-ships-that-no-decision-requires-and-nothing-calls-a-javadoc-states-its-own-contract-and-agents-md-carries-no-history.md) §1 (§5).
- **Rests on**: the code at `8815146`; the plans SQLite gives through `sqlite-jdbc` 3.53, the driver the application runs on; the tests below. Nothing was run on Windows or under WSL. No archive, working directory, database, log, report or deliverable of the operator's was opened for this record ([ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md)).

## Context

A review of `c60dc68..8815146`, the change of [#508](https://github.com/algernon28/vespera/pull/508) (ADR-246), reported nine findings. One is a cost brought in by [#507](https://github.com/algernon28/vespera/pull/507) (ADR-245) and kept by #508:

- **Stage 4b's question whether a page of documents is kept read every row of the run, for each page.** `SELECT redundant_with_occurrence_id FROM redundant_with WHERE run_id = ? AND redundant_with_occurrence_id IN (…)` is planned, for a list of more than one document, as `SEARCH redundant_with USING INDEX redundant_with_by_run_id (run_id=?)`: no index holds both columns, and SQLite takes the run's index over the list's. Each page of signed documents then reads every row phase 1 and phase 2 have written under the run, so the pass costs the pages times the run's removals: some 10⁸ rows read for a million documents of which a tenth are removed. The read of one document, before ADR-245, was planned by the index on `redundant_with_occurrence_id`. ADR-245 §1 and ADR-246 §3 said the column's index was used; no test held the plan.

The others are of ADR-246's drives and helpers:

- **The types of a drive Windows serves named disks Linux mounts itself**: `ntfs`, `ntfs3` and `fuseblk`, the last any disk FUSE mounts, exFAT among them. A failure no wait lifts on such a disk was said 1.5 seconds late.
- **`virtiofs`, which WSL 2 can mount a Windows drive by, was not named.**
- **A lookup of the drive that failed, or answered nothing, replaced the move's own failure** with its exception or a `NullPointerException`.
- **The drive was asked again for each refused attempt**, though it does not change between them.
- **`ConfidenceDistribution`'s javadoc named `Verdicts#survivors`**, which it no longer calls.
- **Stage 4b's `among` read the first column of whatever statement it was given**, and matched the list against the column it was told: a statement reading one column and asked by another answered wrong and said nothing.

## Decision

### 1. The run is compared as a value in stage 4b's reads by key

`signedAmong`, `removedAmong` and `keptAmong` compare the run as `+run_id = ?`, so that SQLite cannot use the index on the run and looks the page's documents up by the index that holds them: `keptAmong` by `redundant_with_by_redundant_with_occurrence_id`, the other two by the table's primary key, whose first column is the occurrence. It is the form ADR-211 §8 gives `+walk_id`. Each page then reads the rows that name its documents, and no others of the run.

### 2. A drive Windows serves is one of six types

`9p`, `drvfs` and `virtiofs` (WSL) and `cifs`, `smb3` and `smbfs` (a share). A disk Linux mounts itself, NTFS or exFAT among them, is not one: no Windows program holds its files while Linux runs, and a failure of no more specific type on it is thrown at once, as ADR-245 has it for every disk of Linux's own.

### 3. The drive is asked once a move, and a lookup that fails is no drive Windows serves

`ReplacingMove.intoPlace` asks the type of the page's place at the first refusal of no more specific type away from Windows, and keeps the answer for the move's later attempts. A lookup that throws a `RuntimeException`, or answers nothing, is read as an empty type, so the move's own failure is what is thrown.

### 4. Stage 4b's reads by key read the column they are asked by, by name

`among` reads each row's `column` by its name, so a statement that reads another column fails at its first row. `ConfidenceDistribution`'s javadoc names `Verdicts#eachPageOfSurvivors`.

### 5. The ADR-216 check

The change touches `similarity` (`RedundancyResolution`), `extraction` in a javadoc (`ConfidenceDistribution`), and `ledger`, which no stage names. **Stages 2 to 6b move**, as ADR-237 to ADR-246 already move them; ADR-243 §1 records the operator's answer that no `vespera run` has been made with a build carrying #503, so the next run mints them once **provided this change is on `main` before that run**. No DDL, no schema version, no cache key, no recorded setting, no verdict kind and no line of the log changes; every verdict is what it was.

### 6. What is left as it is

- **Retrying a failure of no more specific type on every platform**, which would need neither the platform nor the drive. It is ADR-244 §3's rule, which ADR-245 §2 narrowed after review because a disk that is read-only or full was said late; this record keeps the narrowing.
- **5d's seed pages and the helpers' own pages.** `requireForOccurrences` and `chunkCounts` cut by the same page as 5d's seeds, so each statement is one page whichever cuts it; a content hash two seed pages share is counted in each.

## Consequences

- **Stage 4b's phase 2 reads, for each page, the rows that name its documents**, where it read every row of the run.
- A permanent failure on a disk Linux mounts itself is said at once; a move on a WSL drive mounted by `virtiofs` is attempted again.

## Tests

| Class | What it holds |
| --- | --- |
| `similarity.RedundancyResolutionReadsAPageOfSignedOccurrencesAtATimeTest.asksWhetherAPageOfDocumentsIsKeptAtOnce`, one claim added | each statement asking whether a page is kept is planned by `redundant_with_by_redundant_with_occurrence_id` and not by `redundant_with_by_run_id`. Failed first: six statements planned by the run's index |
| `ledger.AMoveRefusedForAMomentIsTriedAgainTest.theDrivesWindowsServesAreToldByTheirType` | `virtiofs` is a drive Windows serves; `ntfs`, `ntfs3`, `fuseblk` and `exfat` are not. Failed first |
| `ledger.AMoveRefusedForAMomentIsTriedAgainTest.awayFromWindowsAnUntypedFailureOnADriveWindowsServesIsTriedAgain` | the drive is asked once for a move refused twice. Failed first: asked twice |
| `ledger.AMoveRefusedForAMomentIsTriedAgainTest.aDriveThatCannotBeToldLeavesTheMovesOwnFailure`, new | a lookup that throws, and one that answers nothing, leave the move's own failure thrown after one attempt. Failed first: the lookup's `IllegalStateException` was thrown |
| `EveryStatementThatSortsIsRecordedTest`, unchanged | stage 4b's reads by key sort nothing and stay plannable |

## What this does not decide

- An index on `redundant_with (redundant_with_occurrence_id, run_id)`, which would hold both columns; the value comparison needs no new index and no ADR-218 record.
- A shared form for a read that asks a list of keys with `IN (…)`, as ADR-243 to ADR-246 left it.
