# ADR-243 — The reads that ask a page of occurrences by key take the ledger's page, and cut it once

- **Date**: 2026-10-12
- **Status**: accepted on 2026-10-12.
- **Applies**: [ADR-216](0216-nothing-ships-that-no-decision-requires-and-nothing-calls-a-javadoc-states-its-own-contract-and-agents-md-carries-no-history.md) §1, the ride-along rule, to a copy in `extraction` and `embedding` (§1). Amends nothing.
- **Keeps**: [ADR-240](0240-the-tree-asks-what-it-lists-of-each-survivor-a-page-of-survivors-at-a-time.md) §2: what the tree lists of each survivor is asked a page of up to 1,000 occurrences at a time, one statement each, and `ScoringProgress` is told of each occurrence once its page's statement has finished. The statements, their pages and their order are unchanged.

## Context

ADR-240 gave three reads a page of occurrences asked by key: `Occurrences.pathsOf`, which cuts its pages by `KeysetPages.ROWS_IN_A_PAGE`, and `ExtractionCacheKeys.requireForOccurrences` and `RelevanceScoring.scoresFor`, which each declared their own `OCCURRENCES_ASKED_AT_ONCE = 1_000`. `scoresFor` cut its occurrences into pages and handed each to `RelevanceScoreCache.forOccurrences`, which cut it again by the ledger's size, so a page was cut twice, by two numbers that happen to agree. Nothing a run writes depends on them.

## Decision

### 1. Which run ids move

ADR-216 §1: *"Everything this record removes from `src/main` lands now only if the run ids it moves are already moving."* A copy goes with a change that re-mints its module for its own reasons, and is never the only reason for the re-mint.

- **What this change moves.** It touches `extraction` and `embedding`, so stages 2 to 6b are minted again (ADR-058, ADR-048).
- **What moves them for reasons of their own.** ADR-237 to ADR-242 touch `similarity`, `embedding`, `extraction` and `synthesis`, and move stages 2 to 6b; ADR-244 moves them too.
- **How the rule applies.** This change moves no stage those changes do not, so its copy goes beside them and is not the only reason for any stage's re-mint.

### 2. One page size

`ExtractionCacheKeys` and `RelevanceScoring` declare no page size. `requireForOccurrences` cuts its pages by `KeysetPages.ROWS_IN_A_PAGE`, as `Occurrences.pathsOf` does, and `extraction` may name `ledger` ([ADR-040](0040-modules-are-capability-shaped-not-stage-shaped.md)).

### 3. The scores are cut into pages once

`RelevanceScoreCache.forOccurrences(Collection, RunId, BiConsumer<List<OccurrenceId>, Map<OccurrenceId, RelevanceScore>>)` cuts the occurrences into pages of the ledger's size and hands each page, with the scores of those of its occurrences that have one, once its statement has finished. `scoresFor` goes through what each page is handed and tells `ScoringProgress` of each occurrence as before. *Put and not taken*: `scoresFor` asking the cache for every score at once and telling the progress afterwards, which would tell it of no occurrence until the last page was read, against ADR-192 §5.

## Consequences

- The reads ADR-240 added ask a page of the one size `KeysetPages` holds.
- **Which run ids move**: stages 2 to 6b, which ADR-237 to ADR-242 and ADR-244 also move (§1). Every listing is the same.
- The other page sizes of 1,000 in `src/main` stand: `AnomalyLog`'s, which waits for a change that re-mints `corpus`, and the paged reads that are not asked by key, which this record did not read.

## Tests

- `TheReadsByKeyTakeTheLedgersPageTest`: none of `ExtractionCacheKeys`, `RelevanceScoring`, `RelevanceScoreCache` and `Occurrences` declares a page size of its own.
- `pipeline.TheTreeAsksAboutAPageOfSurvivorsAtATimeInvocationTest` and `embedding.ScoringProgressTest`, unchanged: the statements and the progress are as ADR-240 has them.

## What this does not decide

- A shared form for a read that asks a page of keys with `IN (…)`, which `ExtractionCacheKeys`, `RelevanceScoreCache`, `Occurrences` and `RedundancyResolution` each write.
