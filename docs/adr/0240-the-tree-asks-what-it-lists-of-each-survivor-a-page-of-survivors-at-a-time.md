# ADR-240 — The tree asks what it lists of each survivor a page of survivors at a time

> **Partly amended — see [ADR-241](0241-the-manifest-asks-a-pages-places-together-a-clusters-synthesis-doc-is-read-once-and-a-survivors-pictures-are-looked-up-by-its-listed-key.md).** §1's cluster place asked by its key for each row of the manifest and its doc read twice no longer stand: the manifest asks a page's places together, and a cluster's doc is read once, for its file and its row of the index. The first item of *What this does not decide* is decided: a survivor's pictures are looked up by the content hash §2's read gives the page, with no lookup of their own. §2 and everything else stand.

- **Date**: 2026-10-11
- **Status**: accepted on 2026-10-11.
- **Amends**: [ADR-223](0223-5fs-size-report-6a-and-6b-go-through-one-seed-partition-at-a-time-and-what-is-still-held-says-why.md)'s Consequences, *"a lookup by key for each cluster and each survivor of the manifest"*, for the survivors: what the tree lists of each, its path, its content key and its score, is asked of a page of survivors in one statement each (§2). Its lookups by key for each cluster stand, and so does the rest of ADR-223. ADR-223 carries a note at its head.
- **Rests on**: `pipeline.TheTreeAsksAboutAPageOfSurvivorsAtATimeInvocationTest`; the same test's statements counted before and after (Measured).

## Context

The tree asked about sixteen statements of one row for each survivor while 6b wrote it. Read against ADR-223:

- **A cluster's two places are asked by its key for each row of the manifest.** ADR-223 §13, 1, records `Clusters.placeOf`, a lookup of one `cluster` row by its key, for the manifest. Accepted; not changed here.
- **A cluster's synthesis doc is read twice**, for its cluster file and for its row of the index. ADR-223 §13, 5, records it as a consequence: *"holding a partition's titles to save the second would be a collection for a lookup."* Accepted; not changed here.
- **An exemplar's score and content key are asked by key** as the generation walk sends it (ADR-223 §5). Accepted; not changed here.
- **What the tree lists of each survivor.** `GenerationTasklet.survivorsOf` built each `ListedSurvivor` from three lookups of one row: the survivor's facts, for its path; its content key under the extraction run; its score under the scoring run. The tree goes through every survivor three times, for the cluster files a partition at a time, for the furniture rule and for the manifest a page at a time (ADR-223 §6, §7), so each survivor cost nine statements. ADR-223's Consequences name *"a lookup by key for each cluster and each survivor of the manifest"* and say *"Their time was not measured."* The pages ADR-223 reads the survivors in already hold the occurrences each lookup asks about.

## Measured

**Method.** The test's invocation: twelve documents, every one a survivor, one tree written; every statement the pool's connections prepare kept, and those from the first page of the run's memberships on counted. Linux container, Java 26.

| Statements while the tree is written | Before | After |
| --- | ---: | ---: |
| One survivor's score, by its key | 36 | 0 |
| One occurrence's path, by its id | 38 | 2 |
| One survivor's content key, by its key | 60 | 24 |
| Every statement prepared | 207 | 108 |

Before, each survivor cost three of each, one a pass, and two content keys more for its pictures. What is left asked one at a time: an exemplar's path, by its id, kept for the pass that asks it, here the one exemplar's twice; and a survivor's content key where its pictures are read, twice, for the furniture rule and for its cluster's file (ADR-149 §9), which `Deliverable`'s pictures ask for on their own.

## Decision

### 1. What stands

ADR-223 as it is: the three passes, their order, a partition held at a time, the manifest and the pictures a page of 1,000 memberships at a time, a cluster's place and doc asked by its key, an exemplar's score and key asked as it is sent.

### 2. A page of survivors is asked about in one statement for each thing listed

`survivorsOf` is handed a partition's members, or a page of the run's members, and asks for all of them at once:

- `RelevanceScoring.scoresFor`, which already took a collection, asks `RelevanceScoreCache.forOccurrences` for up to 1,000 occurrences a statement, `WHERE run_id = ? AND occurrence_id IN (…)`, by `relevance_score`'s key. `ScoringProgress` is told of each occurrence once its page's statement has finished, so `survivors listed` and the score counter count as before.
- `Occurrences.pathsOf` asks `file_occurrence` for the paths of up to 1,000 ids a statement.
- `ExtractionCacheKeys.requireForOccurrences` asks `extraction_cache_key` for up to 1,000 occurrences a statement, and refuses, as `requireForOccurrence` does, an occurrence with no key under the run.

A partition larger than 1,000 members is asked about in pages of 1,000, so no statement carries more than 1,001 values, under SQLite's limit of 32,766. **What is listed is the same**: the same survivors, in the same order, with the same path, key, score, exemplar and places. *Put and not taken*: one statement joining the three tables, which would name `file_occurrence` and `extraction_cache_key` from `embedding`, or `relevance_score` from `ledger`, against [ADR-209](0209-the-ledger-is-four-records-behind-one-type-no-capability-module-names-spring-batch-and-a-tables-sql-is-its-owners.md); and a path held for every survivor of a pass, which is a collection that grows with the run.

## Consequences

- The tree asks about each survivor three statements a page of up to 1,000 in each of its passes, where it asked nine a survivor.
- `RelevanceScoreCache`, `Occurrences` and `ExtractionCacheKeys` each answer a collection of occurrences in one statement a page; the lookups of one stay for the callers that ask about one.
- **Which run ids move**: the change touches `embedding` and `extraction`, so stages 2 to 6b move (ADR-058). Every listing is the same.

## Tests

- `pipeline.TheTreeAsksAboutAPageOfSurvivorsAtATimeInvocationTest`: while the tree is written, no survivor's score is asked by itself, a path by itself fewer times than there are survivors, and a content key by itself no more than twice a survivor, for its pictures.
- Every test of the tree, the manifest and the cluster files, unchanged, holds that what is listed is the same.
- `EachTableIsNamedOnlyByItsOwnerTest` and `EveryStatementThatSortsIsRecordedTest`: the three new statements name only their owners' tables and sort nothing.

## What this does not decide

- A survivor's content key asked by itself where its pictures are read (ADR-149 §9), which the pictures' own reader asks.
- The time of the tree's statements on a large run, which this record counted and did not time.
