# ADR-245 — Stage 4b asks a page whether its documents are kept, a move is attempted again for a sharing violation only on Windows, and the pager writes its own bound

> **Partly amended — see [ADR-246](0246-a-move-on-a-drive-windows-serves-is-attempted-again-and-the-survivors-pages-are-the-ledgers.md).** Of §2: a `FileSystemException` of no subclass is attempted again away from Windows too where the page's place is on a drive Windows serves, under WSL or over a share, told by the type of its file store.

- **Date**: 2026-10-12
- **Status**: accepted on 2026-10-12, on the call of the session that worked the review of the changes on `main` from `c244916` to `85b2d38` (*"fix them, test first, then open a PR"*). No question was put to the operator. Each change below that changes what the code does had a test written first that failed on the code at `85b2d38`, and passes after it.
- **Amends**: [ADR-244](0244-the-floors-answers-are-those-under-the-runs-embedder-identity-a-move-refused-as-a-sharing-violation-is-attempted-again-and-a-document-kept-for-another-is-checked-for-no-container.md) §3, that a `FileSystemException` of no subclass is attempted again on every platform (§2 here); §4, that whether a document is kept is asked of each document by itself (§1); §6, *"The pager does not write the bound itself"* (§3); and §7's last sentence, *"The seeds' counts are asked as they were"* (§4). It carries a note at its head.
- **Applies, and does not amend**: [ADR-216](0216-nothing-ships-that-no-decision-requires-and-nothing-calls-a-javadoc-states-its-own-contract-and-agents-md-carries-no-history.md) §1, the ride-along rule, to the copies of §5 (§6 here); [ADR-243](0243-the-reads-that-ask-a-page-of-occurrences-by-key-take-the-ledgers-page-and-cut-it-once.md) §2, one page size, to two reads it did not name (§4).
- **Keeps**: [ADR-234](0234-no-redundancy-pointer-names-a-removed-document-a-cut-is-read-from-the-band-and-a-verdict-reason-is-written-in-one-locale.md) §1's rule, that no document a row names as kept is removed in its turn, and ADR-244 §4's order, that this is asked before a document's candidates are gone through; [ADR-242](0242-a-page-moved-over-its-place-is-moved-again-a-few-times-where-the-move-is-refused-as-access-denied.md) §2's attempts and waits, and the retry of `AccessDeniedException` on every platform.
- **Rests on**: the code at `85b2d38`; the tests below. Nothing was run on Windows. No archive, working directory, database, log, report or deliverable of the operator's was opened for this record ([ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md)).

## Context

A review of `c244916..85b2d38`, the changes of [#506](https://github.com/algernon28/vespera/pull/506) (ADR-244) and [#505](https://github.com/algernon28/vespera/pull/505) (ADR-243), reported ten findings. Two are what ADR-244 brought in:

- **Stage 4b asked one statement for each document phase 2 goes through.** ADR-244 §4 moved the question whether a row names a document as kept to before its candidates, so it is asked of every signed document phase 1 left standing, where it was asked only of those a container was found for. On 2,550 signed documents, 50 of them removed in phase 1, that was 2,500 statements.
- **A move that failed for good was attempted five times.** ADR-244 §3 attempts again a `FileSystemException` of no subclass on every platform. Away from Windows no program holding a file stops its move, and Java gives a read-only or full disk, a folder in the page's place, a loop of links or a fault of the device as that type, none of which a wait lifts: each was said 1.5 seconds late.

Four are reads that do not take a page as the others do:

- **`ChunkCache.counts` refused more than a page of hashes** with `IllegalArgumentException`, where every other read that asks a list of documents by key cuts the list into pages itself.
- **`ExtractionMetrics` kept a page size of its own**, `OCCURRENCES_IN_A_STATEMENT = 1_000`, the copy ADR-243 took out of `ExtractionCacheKeys` and `RelevanceScoring`, which `TheReadsByKeyTakeTheLedgersPageTest` did not name.
- **Stage 5d read each seed's content key and chunk count by itself**, two statements a seed, beside the reads of a page ADR-244 §7 added for the survivors.
- **5d put the ledger's pages of survivors together again by hand**, from `Verdicts.survivors` read one survivor at a time, as `SeedCorpusComparison` does; and it read the chunker and rule identities again for each survivor, beside the ones the seeds' vectors were read under.

One is how `KeysetPages` holds its bound: ADR-244 §6 refuses a statement whose text does not end in `LIMIT 1000`, a test of the text by a pattern, which a bound written another way meets or misses by its spelling. One is three javadocs that still say a move is attempted again only where it is refused as access denied. One, that `StageRuns.embeddingScoringUnder` answers a run already minted without asking whether it was minted under the name given, is not changed (§7).

## Decision

### 1. Stage 4b asks a page of documents at once whether a row keeps them

Phase 2 asks, for each page of signed documents, which of them a row of the run already names as kept (`redundant_with_occurrence_id IN (…)`, a page to a statement, by the index on that column, sorting nothing), beside its question which of them phase 1 removed. Going through the page, it adds each container it writes to that set, so that a document of the page a row of the page names as its container is kept in its turn, as when the question was asked of the database for each document. Nothing else changes: the same documents are removed, and the same containers written.

### 2. A move refused by an error Java has no more specific type for is attempted again only on Windows

`ReplacingMove` attempts a move again where it is refused with `AccessDeniedException`, on every platform, as ADR-242 has it, and where it is refused with a `FileSystemException` whose class is `FileSystemException` itself only where the program runs on Windows, read from `os.name`. There, that type is how Java gives a sharing or lock violation, and a full disk is still said after the attempts are spent, as ADR-244 §3 has it. Elsewhere it is thrown at once. A second constructor takes the platform, so that a test stands in for either on the one it runs on.

### 3. The keyset pager writes its own bound

`KeysetPages` writes ` LIMIT 1000` after the statement it is given, which names no bound, and refuses nothing. The ten statements that ship drop the bound from their text, and each page's statement is the same text, byte for byte, as before: no plan changes, and `EveryStatementThatSortsIsRecordedTest` passes unedited. A statement given with a bound of its own fails at its first page, a statement with two.

### 4. The counts of chunks and the measures of stage 2 take the ledger's page

`ChunkCache.counts`, and with it `HybridChunker.chunkCounts`, cut the hashes they are given into pages of `KeysetPages.ROWS_IN_A_PAGE`, one statement each. `ExtractionMetrics` declares no page size and cuts by the ledger's. `HybridChunker.chunkCount` and `ChunkCache.count`, which asked one hash's chunks, are taken out: after §5 nothing in `src/main` calls them, and `HybridChunkerTest` counts through `chunkCounts`.

### 5. Stage 5d reads the seeds a page at a time, and the ledger's own pages of survivors

The seeds' content keys are asked by `ExtractionCacheKeys.requireForOccurrences` and their chunk counts by `chunkCounts`, a page of seeds to a statement, where each was asked of each seed. `Verdicts.eachPageOfSurvivors(RunId, Consumer)` hands a caller each page the survivors' read brings back, once its statement has finished; 5d scores and `SeedCorpusComparison` reads its rows by it, and neither puts a page together. The pages are the ones they put together: 1,000 survivors, then the rest. `scorePage` reads a survivor's vectors under the chunker and rule identities the seeds' were read under, handed to it, and not read again for each survivor.

### 6. The ADR-216 check

The change touches `similarity` (`RedundancyResolution`), `extraction` (`ChunkCache`, `HybridChunker`, `ExtractionMetrics`, `ExtractionFaults`) and `embedding` (`SeedCorpusComparison`, `RelevanceDistribution`, `DocumentClusters`, `RelevanceScoreCache`), `synthesis` in a javadoc (`Deliverable`), and `ledger` and `pipeline`, which no stage names. **Stages 2 to 6b move: extraction, content census, content redundancy, seed measurement, embedding scoring, arrangement and generation**, as ADR-237 to ADR-244 already move them. ADR-243 §1 records the operator's answer that no `vespera run` has been made with a build carrying [#503](https://github.com/algernon28/vespera/pull/503); so the next run mints stages 2 to 6b once, for this change and those before it, **provided this change is on `main` before that run**. The copies §4 and §5 take out ride that re-mint, as ADR-216 §1 lets them. Byte-level reduction keeps its run id. No DDL, no schema version, no cache key, no recorded setting, no verdict kind and no line of the log changes; every verdict is what it was.

### 7. What is left as it is

- **`StageRuns.embeddingScoringUnder` answers a run already minted under another name.** No shipped path meets it: the embedding step's gate reads the name through `StageRuns.embeddingModelNamed()`, which is the minted name once the run is minted, so the name it hands is the minted one. A check would guard a state nothing reaches, at the cost of a test that mints a run by hand.
- **`UnrecordedOccurrences` keeps its own page of 1,000** and puts it together from the `Iterable` it is given: it is itself an `Iterable` that stage 2's reader takes one occurrence at a time from, so a page handed to a callback does not fit it. It reads no table and is in `pipeline`.

## Consequences

- **Stage 4b asks one statement a page of signed documents** whether they are kept, where it asked one a document.
- **A move that fails for good away from Windows is said at once**, and a move refused by another program on Windows is attempted again as before.
- **A count of chunks of more than a page of documents is answered**, and not refused.
- **Stage 5d asks two statements a page of seeds** where it asked two a seed.
- A caller of `KeysetPages` writes a statement with no bound.

## Tests

| Class | What it holds |
| --- | --- |
| `similarity.RedundancyResolutionReadsAPageOfSignedOccurrencesAtATimeTest.asksWhetherAPageOfDocumentsIsKeptAtOnce`, new | of 2,550 signed documents, whether a row keeps a document is asked in at most 3 statements, one a page, each naming at most 1,000; the same 50 near-duplicates and 25 contained documents are removed. Failed first: 2,500 statements. Without the containers a page writes added to its set, `RedundancyResolutionTest.aChainOfContainmentsLeavesNoPointerAtARemovedDocument` fails |
| `ledger.AMoveRefusedForAMomentIsTriedAgainTest.awayFromWindowsAnUntypedFailureIsNotTriedAgain`, new | away from Windows, a move failing with a `FileSystemException` of no subclass is thrown at once, after one attempt and no wait. `aMoveRefusedTwiceAsASharingViolationIsMadeOnTheThirdAttempt` stands in for Windows. Failed first: it did not compile, the platform having no seam |
| `extraction.HybridChunkerTest.countsThePiecesOfMoreDocumentsThanAPage`, new | the chunks of 1,001 documents are counted. Failed first: `IllegalArgumentException` |
| `TheReadsByKeyTakeTheLedgersPageTest` | names `ExtractionMetrics` and `ChunkCache` beside ADR-243's four. Failed first, naming `ExtractionMetrics.OCCURRENCES_IN_A_STATEMENT` |
| `ledger.KeysetPagesTest.thePagerBoundsEachPageItself`, new | a statement given with no bound is read as two pages of 1,000 and a short third, each statement ending ` LIMIT 1000`. Failed first: refused. `aStatementBoundByAnotherNumberThanAPageIsRefused` is taken out with the refusal it held |
| `pipeline.ScoringCountsAPageOfSurvivorsChunksTogetherInvocationTest` | no document's chunks, the seed's included, are counted by themselves: one statement counts the seeds' and one the page of survivors'. Failed first: the seed's was counted by itself |
| `pipeline.TheTreeAsksAboutAPageOfSurvivorsAtATimeInvocationTest`, `embedding.ScoringProgressTest`, the `SeedCorpusComparison` tests, unchanged | 5d's and stage 5's pages, statements and progress are as they were |

## What this does not decide

- Telling a sharing violation on Windows from the other errors Java gives as a `FileSystemException` of no subclass there, as ADR-244 left it.
- A shared form for a read that asks a list of keys with `IN (…)`, as ADR-243 left it; the copies stand in `ExtractionCacheKeys`, `RelevanceScoreCache`, `Occurrences`, `Verdicts`, `Clusters`, `ChunkCache`, `ExtractionMetrics` and `RedundancyResolution` (review register S11).
