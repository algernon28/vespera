# ADR-243 — The reads that ask a page of occurrences by key take the ledger's page, and cut it once

- **Date**: 2026-10-12
- **Status**: accepted on 2026-10-12, on the operator's word (*"do A11 with its ADR-216 check"*). The operator answered the check's one question: no `vespera run` has been made with a build carrying [#503](https://github.com/algernon28/vespera/pull/503) (§1). The form of the change is the session's call.
- **Applies**: [ADR-216](0216-nothing-ships-that-no-decision-requires-and-nothing-calls-a-javadoc-states-its-own-contract-and-agents-md-carries-no-history.md) §1, the ride-along rule, to a copy in `extraction` and `embedding` (§1). Amends nothing.
- **Keeps**: [ADR-240](0240-the-tree-asks-what-it-lists-of-each-survivor-a-page-of-survivors-at-a-time.md) §2: what the tree lists of each survivor is asked a page of up to 1,000 occurrences at a time, one statement each, and `ScoringProgress` is told of each occurrence once its page's statement has finished. The statements, their pages and their order are unchanged.
- **Settles**: architect follow-up A11 on #503, which the architect left optional and [#504](https://github.com/algernon28/vespera/pull/504) left out for ADR-216 §1.
- **Rests on**: `TheReadsByKeyTakeTheLedgersPageTest`, which failed on `c244916`; the tests ADR-240 names, unchanged. No archive, working directory, database, log, report or deliverable of the operator's was opened for this record ([ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md)).

## Context

ADR-240 gave three reads a page of occurrences asked by key: `Occurrences.pathsOf`, which cuts its pages by `KeysetPages.ROWS_IN_A_PAGE`, and `ExtractionCacheKeys.requireForOccurrences` and `RelevanceScoring.scoresFor`, which each declared their own `OCCURRENCES_ASKED_AT_ONCE = 1_000`. `scoresFor` cut its occurrences into pages and handed each to `RelevanceScoreCache.forOccurrences`, which cut it again by the ledger's size, so a page was cut twice, by two numbers that happen to agree. The architect named both on #503 as A11. Nothing a run writes depends on them.

## Decision

### 1. The ADR-216 check

ADR-216 §1: *"Everything this record removes from `src/main` lands now only if the run ids it moves are already moving."* A copy goes with a change that re-mints its module for its own reasons, and is never the only reason for the re-mint.

- **What this change moves.** It touches `extraction` and `embedding`, so stages 2 to 6b are minted again (ADR-058, ADR-048).
- **What already moves them.** ADR-237 to ADR-242, on `main` since #503, touch `similarity`, `embedding`, `extraction` and `synthesis`, and move stages 2 to 6b. A working directory first sees those new ids at the next `vespera run` on a build that carries them.
- **The operator's answer.** No `vespera run` has been made with a build carrying #503, so the next run mints stages 2 to 6b once, for #503 and this change together. This change adds no pass, as ADR-216 §8 found for its own removals beside #456 and #461. **It holds only if this change is on `main` before that run**: a run made on `c244916` first and on this change after pays stages 2 to 6b twice.

### 2. One page size

`ExtractionCacheKeys` and `RelevanceScoring` declare no page size. `requireForOccurrences` cuts its pages by `KeysetPages.ROWS_IN_A_PAGE`, as `Occurrences.pathsOf` does, and `extraction` may name `ledger` ([ADR-040](0040-modules-are-capability-shaped-not-stage-shaped.md)).

### 3. The scores are cut into pages once

`RelevanceScoreCache.forOccurrences(Collection, RunId, BiConsumer<List<OccurrenceId>, Map<OccurrenceId, RelevanceScore>>)` cuts the occurrences into pages of the ledger's size and hands each page, with the scores of those of its occurrences that have one, once its statement has finished. `scoresFor` goes through what each page is handed and tells `ScoringProgress` of each occurrence as before. *Put and not taken*: `scoresFor` asking the cache for every score at once and telling the progress afterwards, which would tell it of no occurrence until the last page was read, against ADR-192 §5.

## Consequences

- The reads ADR-240 added ask a page of the one size `KeysetPages` holds.
- **Which run ids move**: stages 2 to 6b, which #503 already moves, on the terms of §1. Every listing is the same.
- The other page sizes of 1,000 in `src/main` stand: `AnomalyLog`'s, which waits for a change that re-mints `corpus` (review register R31), and the paged reads that are not asked by key, which this record did not read.

## Tests

- `TheReadsByKeyTakeTheLedgersPageTest`: none of `ExtractionCacheKeys`, `RelevanceScoring`, `RelevanceScoreCache` and `Occurrences` declares a page size of its own. It failed on `c244916`, naming the two `OCCURRENCES_ASKED_AT_ONCE`.
- `pipeline.TheTreeAsksAboutAPageOfSurvivorsAtATimeInvocationTest` and `embedding.ScoringProgressTest`, unchanged: the statements and the progress are as ADR-240 has them.

## What this does not decide

- A shared form for a read that asks a page of keys with `IN (…)`, which `ExtractionCacheKeys`, `RelevanceScoreCache`, `Occurrences` and `RedundancyResolution` each write.
