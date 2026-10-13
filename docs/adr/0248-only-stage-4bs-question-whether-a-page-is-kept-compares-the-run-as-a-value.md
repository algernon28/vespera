# ADR-248 — Only stage 4b's question whether a page is kept compares the run as a value

- **Date**: 2026-10-13
- **Status**: accepted on 2026-10-13, on the call of the session that worked the review of the change on `main` from `8815146` to `0930f64` (*"fix them, test first, then open a PR"*). No question was put to the operator. The change had a test written first that failed on the code at `0930f64`, and passes after it: a claim of `asksWhetherAPageOfDocumentsIsKeptAtOnce`. The Tests table was amended on 2026-10-13, after the reviews of `0930f64..decc5b6` and `decc5b6..3dd1e39`: the claim became a test of its own, asked of each table, and matches the plan by the columns it searches on rather than by the name SQLite gives the key's index; what it asks of the code is unchanged.
- **Amends**: [ADR-247](0247-stage-4b-asks-whether-a-page-is-kept-of-its-own-index-and-a-drive-windows-serves-is-told-once.md) §1, *"`signedAmong`, `removedAmong` and `keptAmong` compare the run as `+run_id = ?`"* and *"the other two by the table's primary key"*. It carries a note at its head.
- **Keeps**: ADR-247 §1 for `keptAmong`; ADR-247 §2 to §4 and §6.
- **Rests on**: the code at `0930f64`; the plans SQLite gives through `sqlite-jdbc` 3.53; the test below. No archive, working directory, database, log, report or deliverable of the operator's was opened for this record ([ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md)).

## Context

A review of `8815146..0930f64`, the change of [#509](https://github.com/algernon28/vespera/pull/509) (ADR-247), found that its `+run_id` cost the two reads it did not need it for. `minhash_signature` and `redundant_with` are keyed `(occurrence_id, run_id)`, so `signedAmong` and `removedAmong` were planned by the primary key on both columns, `(occurrence_id=? AND run_id=?)`. With the run compared as a value they are planned on `(occurrence_id=?)` alone and read the row of every stage-4 run that names the document, then pass over those of other runs. They are asked for every batch of containment candidates, the busiest reads of stage 4b. ADR-247 §1 said the primary key was used, which it was, by one column; no test held the plan.

The review's other findings are left: why is in §3.

## Decision

### 1. The run is compared as a value only where no index holds both columns

`signedAmong` and `removedAmong` compare the run as a column, `run_id = ?`, and are planned by their table's primary key on the occurrence and the run. `keptAmong` keeps `+run_id = ?`: no index of `redundant_with` holds `redundant_with_occurrence_id` and the run, and without it SQLite reads every row of the run from the index on the run for each page (ADR-247 §1). `among`'s javadoc says how the run is compared is the caller's, and when each form is the right one.

### 2. The ADR-216 check

The change touches `similarity` (`RedundancyResolution`). **Stages 2 to 6b move**, as ADR-237 to ADR-247 already move them; the next run mints them once **provided this change is on `main` before that run** (ADR-243 §1). No statement but the two reads changes, by one character each; no verdict, no DDL and no line of the log changes.

### 3. What is left as it is

- **`virtiofs`, `9p`, `cifs` and `smb3` also serve Linux containers and VMs on macOS, and shares no Windows serves**, where a failure no wait lifts is said 1.5 seconds late. It is the cost ADR-246 and ADR-247 accepted for a Windows drive read from Linux, which Linux cannot tell from those by the file store's type.
- **An index on `redundant_with (redundant_with_occurrence_id, run_id)`**, which would hold both columns of the kept question and need no value comparison. It is a new index, which needs an ADR-218 record with its measured build; ADR-247's value comparison reads the rows that name a page's documents, and the run's only through them.
- **A line in the log when the drive's type cannot be read**: ADR-247 §3 reads it as no drive Windows serves, and the move's own failure is what is said.
- **`ReplacingMove`'s three-state answer of whether the drive is served**, and **`among` reading its column by name on each row**: neither changes what is done, and the second costs a lookup of one column's name a row beside a read by key.

## Consequences

- Stage 4b's reads of which candidates are signed and which removed find each document's row of the run by the key, and read no other run's.

## Tests

| Class | What it holds |
| --- | --- |
| `similarity.RedundancyResolutionReadsAPageOfSignedOccurrencesAtATimeTest.asksWhichCandidatesAreSignedAndRemovedByTheirKey` | for each of `minhash_signature` and `redundant_with`, at least one read of which documents have a row is made, and each is planned by a `SEARCH` of the table on `(occurrence_id=? AND run_id=?)`, which only its primary key holds. Failed first, as a claim of `asksWhetherAPageOfDocumentsIsKeptAtOnce`: each was planned on `occurrence_id=?` alone; moved to a test of its own after the review of `0930f64..decc5b6`, with `+run_id` put back on `signedAmong` it fails naming `minhash_signature`, and on `removedAmong` naming `redundant_with`. Its class resolves the documents once for its three tests |
| `similarity.RedundancyResolutionReadsAPageOfSignedOccurrencesAtATimeTest.asksWhetherAPageOfDocumentsIsKeptAtOnce`, unchanged | the kept question is planned by `redundant_with_by_redundant_with_occurrence_id`, and not by the run's index: §1's `+run_id` on `keptAmong`. With it taken off, the test fails |
