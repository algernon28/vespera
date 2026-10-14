# ADR-257 — One cut of a list of keys into pages, and the walk anomalies are paged like the other reads

- **Date**: 2026-10-14
- **Status**: accepted on 2026-10-14.
- **Settles**: the shared form for a read that asks a list of keys with `IN`, which [ADR-243](0243-the-reads-that-ask-a-page-of-occurrences-by-key-take-the-ledgers-page-and-cut-it-once.md), [ADR-245](0245-stage-4b-asks-a-page-whether-its-documents-are-kept-a-move-is-attempted-again-for-a-sharing-violation-only-on-windows-and-the-pager-writes-its-own-bound.md), [ADR-246](0246-a-move-on-a-drive-windows-serves-is-attempted-again-and-the-survivors-pages-are-the-ledgers.md) and [ADR-247](0247-stage-4b-asks-whether-a-page-is-kept-of-its-own-index-and-a-drive-windows-serves-is-told-once.md) listed among what they do not decide, and `AnomalyLog`'s page of its own, which [ADR-220](0220-no-class-holds-every-occurrence-of-a-run-stage-2s-resume-the-census-stage-4b-and-stage-5e-read-a-page-at-a-time-or-ask-by-key-and-what-is-still-held-says-why.md) §3 wrote by hand. Each of the four carries a note at its head.
- **Applies, and does not amend**: [ADR-216](0216-nothing-ships-that-no-decision-requires-and-nothing-calls-a-javadoc-states-its-own-contract-and-agents-md-carries-no-history.md) §1, [ADR-040](0040-modules-are-capability-shaped-not-stage-shaped.md) (a capability module may name `ledger`), and [ADR-209](0209-the-ledger-is-four-records-behind-one-type-no-capability-module-names-spring-batch-and-a-tables-sql-is-its-owners.md) (a table's SQL is its owner's).

## Context

Ten places cut a collection of keys into pages with the same loop, `for (int from = 0; from < all.size(); from += page) { batch = all.subList(from, Math.min(...)) }`, and hand each batch to a statement of their own that names it with `IN`: the content keys of `ExtractionCacheKeys`, the counts of pieces in `ChunkCache`, the measures of `ExtractionMetrics`, the scores of `RelevanceScoreCache`, the paths of `Occurrences`, the survivors among a page and the verdicts to discard in `Verdicts`, the kept and signed documents of `RedundancyResolution`, the places of clusters in `Clusters`, and the seeds' pages in `RelevanceScoringTasklet`. Each loop is the same four lines, and each had to be given the ledger's page size separately when ADR-243 to ADR-246 found them: the size is `KeysetPages.ROWS_IN_A_PAGE` in nine and 500 in the tenth, with nothing to keep the nine equal.

`AnomalyLog.sameAnomalies` was the one read by keyset that still held a page size of its own and a loop of its own, 1,000 beside `KeysetPages`' 1,000, where the other reads of the ledger's tables go through `KeysetPages` (ADR-209 §2).

## Decision

### 1. `ledger.ListedPages` cuts a list of keys

`ListedPages.each(keys, page)` hands `page` each page of at most `KeysetPages.ROWS_IN_A_PAGE` keys, in the order the keys iterate, each key once, and none for an empty collection; `each(keys, size, page)` does the same in pages of `size`, for the one statement whose page is fixed at another size (§4). The keys are copied before the first page, so a caller that changes its collection while it goes through the pages changes nothing it is handed. It holds no rule and no SQL.

### 2. What stays the caller's

The statement. Each read writes its own text, with `Placeholders.of` for the brackets of its `IN`, so that the statement is a string literal the plan tests (`EveryStatementThatSortsIsRecordedTest`, the plan claims of `RedundancyResolution` and `ReadsInIdOrderSortNothingTest`) read and plan; what it binds beside the keys; and what it does with the rows. A helper that wrote the statement would be a statement no test could plan.

### 3. Every read that cut a list by hand goes through it

`ExtractionCacheKeys.requireForOccurrences`, `ChunkCache.counts`, `ExtractionMetrics`' reads of a page of occurrences, `RelevanceScoreCache.forOccurrences`, `Occurrences.pathsOf`, `Verdicts.survivingAmong` and `discardVerdictsAgainst`, `RedundancyResolution`'s reads of which documents are signed, removed and kept, `Clusters.placesOf` and `RelevanceScoringTasklet`'s read of the seeds' keys. Their statements, the number of rows each asks, the order of the pages and the arguments are as they were.

### 4. The discard of verdicts keeps its page of 500

`discardVerdictsAgainst` binds the run and the kind beside the keys and deletes; its page was written as 500, apart from the ledger's, and the test that holds it names 500. It goes through `each(keys, size, page)` with the size it had. A discard that took the ledger's page is a decision about a delete's cost, which this record does not take.

### 5. The anomalies of two walks are paged by `KeysetPages`

`AnomalyLog.sameAnomalies` reads each walk's anomalies through a `KeysetPages` over the statement it had (`… FROM walk_anomaly WHERE walk_id = ? AND id > ? ORDER BY id`, which the pager ends with the bound `LIMIT 1000`), and compares them row by row, the two asked in turn, a page of each held, stopping at the first row that differs or at the first walk that has no row where the other has one. The statement, its plan, the pages and their order are as they were; `AnomalyLog` keeps no page size.

### 6. Run ids

The change touches `extraction`, `embedding`, `similarity`, `synthesis` and `corpus`, each of which a stage's version names, so every stage moves, as it does for any change under those modules (ADR-058). The classes of `ledger` and `pipeline` that change are named by no stage's version. No schema, cache key, verdict or line of any report changes.

### 7. What is left as it is

- **A read of the survivors by keyset** (`Verdicts.eachPageOfSurvivors`, `DocumentClusters.eachPage` and the others) is `KeysetPages`' and unchanged; this record cuts a list that is given, `KeysetPages` pages a table.
- **`InvocationAccount`'s `IN` over the runs of a stage's lineage, and `DocumentFrequency`'s `IN` over the survivors and the ruled-out of a page.** The first is a short list bounded by the stages; the second splits a page the ledger has already cut. Each is asked in one statement.
- **`Shingler`'s batches of the rows it inserts** are a write cut at its own size, not a read of a list of keys.

## Consequences

- A read of a list of keys is a call and a statement; the page size and the cut are one place's.
- The ledger's page size can change in one place, and `ReadsOfTheLedgerHoldOnePageTest` and the plan tests still read each statement.
- The comparison of two walks' anomalies is paged as every other read of the ledger's tables is.

## Tests

| Class | What it holds |
| --- | --- |
| `ledger.ListedPagesTest` | pages of the ledger's size in the order given, each key once and the last page short; the boundary at exactly one page; no page for an empty collection; a size of the caller's own, and a size below one refused; the keys as they stood when the cut began |
| `NoReadCutsAListIntoPagesItselfTest.noClassCutsAListByHand` | no class of `src/main` but `ListedPages` has a `for (int from = 0; from < …; from += …)` loop, `Shingler`'s batch of inserted rows excepted |
| `NoReadCutsAListIntoPagesItselfTest.theAnomalyLogKeepsNoPageSizeOfItsOwn` | `AnomalyLog` declares no number of its own for its page |
| every test of the reads above, unchanged | their statements, pages, plans and results are as they were |

## What this does not decide

- A different page size for the discard of verdicts (§4), or for any read.
- Whether the lists of `InvocationAccount` and `DocumentFrequency` (§7) grow into a case for the cut.
