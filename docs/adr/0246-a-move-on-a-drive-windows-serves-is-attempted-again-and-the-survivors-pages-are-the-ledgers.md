# ADR-246 — A move on a drive Windows serves is attempted again, and the survivors' pages are the ledger's

> **Partly amended — see [ADR-247](0247-stage-4b-asks-whether-a-page-is-kept-of-its-own-index-and-a-drive-windows-serves-is-told-once.md).** Of §1: a drive Windows serves is one of `9p`, `drvfs`, `virtiofs`, `cifs`, `smb3` and `smbfs`, and no longer `ntfs`, `ntfs3` or `fuseblk`; the drive is asked once a move, and a lookup that fails is no such drive. Of §3: the run is compared as a value, `+run_id`, since the planner otherwise chose the run's index.

- **Date**: 2026-10-13
- **Status**: accepted on 2026-10-13, on the call of the session that worked the review of the change on `main` from `85b2d38` to `c60dc68` (*"fix them, test first, then open a PR"*). No question was put to the operator; whether the operator's working directory is on a drive Windows serves was asked and not answered, and §1 holds either way. Each change below that changes what the code does had a test written first that failed on the code at `c60dc68`, and passes after it.
- **Amends**: [ADR-245](0245-stage-4b-asks-a-page-whether-its-documents-are-kept-a-move-is-attempted-again-for-a-sharing-violation-only-on-windows-and-the-pager-writes-its-own-bound.md) §2, *"Elsewhere it is thrown at once"*: elsewhere it is thrown at once unless the page's place is on a drive Windows serves (§1 here). It carries a note at its head.
- **Applies, and does not amend**: [ADR-216](0216-nothing-ships-that-no-decision-requires-and-nothing-calls-a-javadoc-states-its-own-contract-and-agents-md-carries-no-history.md) §1, the ride-along rule (§5); [ADR-243](0243-the-reads-that-ask-a-page-of-occurrences-by-key-take-the-ledgers-page-and-cut-it-once.md) §2, one page size, to a read it did not name (§2); [ADR-192](0192-every-loop-in-every-stage-reports-its-progress-and-no-counter-floods-the-log.md) §4, a counter moves as its unit is done (§4).
- **Keeps**: [ADR-242](0242-a-page-moved-over-its-place-is-moved-again-a-few-times-where-the-move-is-refused-as-access-denied.md) §2's attempts and waits; ADR-245 §1, §3 and §5.
- **Rests on**: the code at `c60dc68`; the tests below. Nothing was run on Windows or under WSL. No archive, working directory, database, log, report or deliverable of the operator's was opened for this record ([ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md)).

## Context

A review of `85b2d38..c60dc68`, the change of [#507](https://github.com/algernon28/vespera/pull/507) (ADR-245), reported nine findings. Four are taken here:

- **A move on a Windows drive written from Linux was no longer attempted again.** ADR-245 §2 attempts again a `FileSystemException` of no subclass only where the program runs on Windows. A working directory on a Windows drive read from Linux, under WSL (`/mnt/c`, a 9P or drvfs file store) or over a share (CIFS, SMB), is held by the same Windows programs, a virus scanner or an indexer, and Java on Linux gives their refusal as that type: 6b rolled back every synthesis doc where, before ADR-245, it waited.
- **`ConfidenceDistribution` put the ledger's pages of survivors together again by hand**, with a page size of its own, as 5d and `SeedCorpusComparison` did until ADR-245 §5.
- **Stage 4b's `keptAmong` was a second copy of its `among`**, the read by key in pages of the same class, differing only in the column it names.
- **The counter `Stage 5d (relevance scoring, seed cache keys read)` stood at nought** while every seed's key and chunk count was read, and then counted every seed at once, since ADR-245 §5 read them all before counting any.

## Decision

### 1. A move refused on a drive Windows serves is attempted again

`ReplacingMove` attempts again a move refused with a `FileSystemException` of no subclass where the program runs on Windows, as ADR-245 has it, or where the file store of the page's place, asked when such a failure comes, is of a type Linux gives a drive Windows serves: `9p` and `drvfs` (WSL), `cifs`, `smb3` and `smbfs` (a share), and `ntfs`, `ntfs3` and `fuseblk` (an NTFS disk). Elsewhere, on a disk of Linux's or macOS's own, it is thrown at once, as ADR-245 has it. A type that cannot be read is no such drive. The file store is read from the place's nearest folder that exists. A constructor takes the platform and how a place's file store type is read, so that a test stands in for either and for any drive.

*Put and not taken*: telling a sharing violation from the other failures of that type by the reason Java gives, which is the machine's message in its language, as ADR-244 §3 found.

### 2. The confidence distribution goes through the ledger's own pages

`ConfidenceDistribution.measure` takes each page of survivors from `Verdicts.eachPageOfSurvivors` and declares no page size. The pages, their statements and the progress it gives are as before. `TheReadsByKeyTakeTheLedgersPageTest` names it.

### 3. Stage 4b asks which documents are kept by its one read by key

`RedundancyResolution.among` takes the column its list of occurrences is matched against, and `keptAmong` is `among` over `redundant_with_occurrence_id`. The statement binds the run first, as the class's other reads by key do; it is planned by the index on that column and sorts nothing.

### 4. The seeds' read counter moves a page at a time

5d reads the seeds' content keys and chunk counts a page of seeds at a time, as ADR-245 §5 has it, and counts the page's seeds once its two statements have finished. The statements are the same: `requireForOccurrences` and `chunkCounts` cut by the same page. For a seed set of no more than a page, which is most, nothing a person reads changes.

### 5. The ADR-216 check

The change touches `extraction` (`ConfidenceDistribution`), `similarity` (`RedundancyResolution`) and, in a javadoc, `synthesis` (`Deliverable`), and `ledger` and `pipeline`, which no stage names. **Stages 2 to 6b move**, as ADR-237 to ADR-245 already move them, and, as ADR-243 §1 records the operator's answer that no `vespera run` has been made with a build carrying #503, the next run mints them once **provided this change is on `main` before that run**. Byte-level reduction keeps its run id. No DDL, no schema version, no cache key, no recorded setting, no verdict kind and no line of the log changes.

### 6. What is left as it is

- **`KeysetPages` writes its bound after any statement it is given**, and refuses none. A statement given with a bound, a trailing `;` or a closing line comment would fail at its first page or lose its bound. Every statement that ships is a constant of its caller with none of those, and `KeysetPagesTest` reads one as the pager reads it; a check of the text would test a spelling, as ADR-244 §6's did.
- **`keptAmong`'s rows.** It hands back every row of the run that names a document of the page, where a read of one document stopped at the first. A removed document has one row of the run (`redundant_with`'s key is the occurrence and the run), so all the rows `keptAmong` hands back in a pass are at most the documents the pass removed; the rows of other runs on the same index entry are read and passed over as the read of one document read them.
- **5d counts chunks under `ChunkingRule.DEFAULT`** and reads vectors under the identities read from it and from `HybridChunker` at the start of the step. Both are the one rule and the one chunker; a second rule would be a change to both, recorded where it is made.
- **5c and 5f's clustering read a survivor's content key by itself.** In 5c each read is beside a call to Ollama for each of the survivor's chunks; neither is in the change reviewed.
- **The reads by key that cut a list into pages by hand**, `ChunkCache.counts` and `keptAmong` among them, as ADR-243 and ADR-245 left them (review register S11).

## Consequences

- **A page on a Windows drive written from Linux is put in its place once the Windows program holding it lets go**, as on Windows.
- `ConfidenceDistribution` keeps no page size of its own.
- The seeds' read counter moves as each page of seeds is read.

## Tests

| Class | What it holds |
| --- | --- |
| `ledger.AMoveRefusedForAMomentIsTriedAgainTest.awayFromWindowsAnUntypedFailureOnADriveWindowsServesIsTriedAgain`, new | away from Windows, a page on a WSL drive whose move is refused twice with a `FileSystemException` of no subclass is moved on the third attempt, the drive's type asked of the page's place. Failed first: it did not compile, the drive having no seam |
| `ledger.AMoveRefusedForAMomentIsTriedAgainTest.theDrivesWindowsServesAreToldByTheirType`, new | `9p`, `drvfs`, `cifs`, `smb3`, `smbfs`, `ntfs`, `ntfs3` and `fuseblk` are drives Windows serves; `ext4`, `xfs`, `btrfs`, `tmpfs`, `apfs`, `overlay` and an unread type are not |
| `ledger.AMoveRefusedForAMomentIsTriedAgainTest`, two changed | the move made on Windows, and the one away from it on a disk of its own, stand in for an `ext4` disk |
| `TheReadsByKeyTakeTheLedgersPageTest` | names `ConfidenceDistribution`. Failed first, naming `ConfidenceDistribution.PAGE` |
| `similarity.RedundancyResolutionReadsAPageOfSignedOccurrencesAtATimeTest`, `RedundancyResolutionTest`, unchanged | 4b asks whether a page's documents are kept in one statement a page, and removes what it removed |
| `pipeline.StageFiveReportsItsProgressInvocationTest`, `ScoringCountsAPageOfSurvivorsChunksTogetherInvocationTest`, unchanged | the seeds' counter and statements are as they were for a seed set of one page |

## What this does not decide

- Telling a sharing violation from the other errors Java gives as a `FileSystemException` of no subclass, on Windows or on a drive Windows serves, as ADR-244 left it.
- A shared form for a read that asks a list of keys with `IN (…)`, as ADR-243 and ADR-245 left it.
