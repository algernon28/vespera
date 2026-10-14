# ADR-238 — A bucket row already in the component of the page's lesser members in that bucket is passed over with one question

- **Date**: 2026-10-11
- **Status**: accepted on 2026-10-11.
- **Amends**: [ADR-225](0225-stage-4b-reads-its-candidate-pairs-and-containment-candidates-a-thousand-at-a-time-and-shingle-by-hash-ends-in-the-occurrence.md) §1, in how a pair of one component is passed over: no longer one pair at a time where every lesser member of the page in a bucket is in the row's component (§2). What §1 decides stands, and ADR-225 carries a note at its head.
- **Rests on**: `similarity.RedundancyResolutionTest.aFamilyOfCopiesIsResolvedInTimeThatDoesNotGrowWithTheSquareOfItsSize`; a throwaway probe (Measured).

## Context

Stage 4b's phase 1 has three costs for a large family of near-copies. Read against ADR-225 §1:

- **A pair is offered once for each band it shares, and a pair below the cut that shares several bands is scored once for each.** ADR-225 §1 says both in so many words: *"Every pair sharing a bucket is found in the page of its lesser member, as before, once for each bucket it shares"* and *"A pair below the cut that shares several buckets is scored once for each; scoring reads two sets the cache holds."* Accepted; not changed here.
- **Each page reads its buckets whole again.** That is the price of ADR-225's bound, a page of 1,000 lesser members and 1,000 rows of one bucket held at a time, with no list that grows with the occurrences sharing a bucket (*What it holds*, and its Consequences). Holding a bucket's members to read it once would break that bound. Accepted; not changed here.
- **Not addressed by ADR-225**: the pair loop asked of every row, for each lesser member of the page in its bucket, whether the two were already one component, `UnionFind.joined`, one pair at a time. For a family of G near-copies sharing a bucket that is about G²/2 questions for each band they share, and once the first pairs have joined them every answer is yes. ADR-225 §1 decides that such a pair is not scored; it says nothing of what asking costs.

## Measured

**Method.** A throwaway test against the shipped schema on the test profile's SQLite: G documents with one and the same 100 shingles, so every signature is the same and every pair shares all sixteen bands; stage 3's frequency, every signature, and `shingle_by_hash` built for the run, then `resolve` timed; phase 1 timed alone by a print added for the probe and not kept. Linux container, Java 26.

| G | Phase 1 before | Phase 1 after | Removed |
| ---: | ---: | ---: | ---: |
| 1,000 | 1.97 s | 0.37 s | 999 |
| 2,000 | 6.49 s | 0.45 s | 1,999 |
| 3,000 (index built; the whole of `resolve`) | 11.89 s | 0.95 s | 2,999 |
| 4,000 | 11.03 s | 1.07 s | 3,999 |
| 8,000 | — | 2.91 s | 7,999 |

What is left after the change grows with the bucket reads ADR-225 accepts: G/1,000 pages, each reading the family's sixteen buckets whole. The test, 4,000 copies alone in its JVM: 17.3 s before, 1.6 s after.

## Decision

### 1. What stands

ADR-225 §1 as it is: the page's band rows that share a bucket, each bucket read a page of its rows at a time, a pair offered once for each band it shares, a pair below the cut scored once for each, and a pair of one component not scored.

### 2. A row is passed over with one question where it is in the one component of the page's lesser members in its bucket

For each bucket of a page, `RedundancyResolution.OneComponent` keeps the component every lesser member of the page in that bucket is in, where there is one, found again only after a union has joined two components (`UnionFind.unions()`). A row whose occurrence is held and in that component is passed over: every pair it would make with those members is a pair of one component, which the loop would pass over one at a time. Any other row is gone through pair by pair as before.

**The components, the verdicts and the scores are the same**: a row is passed over only where every pair of it would have been. The order of unions is unchanged, since a passed-over row would have made none. What it costs is one look through the bucket's lesser members after each union, and the unions of a pass are fewer than its signed occurrences.

*Put and not taken*: grouping the lesser members by component and passing over each group in one question. It saves more where a bucket's members are in several components, and asks the bookkeeping to be kept through every union; the measured case, near-copies that become one component, does not need it.

## Consequences

- A family of near-copies resolves in time that grows with the bucket reads ADR-225 accepts, and not with the square of the family on top of them.
- `UnionFind` answers whether it holds an occurrence without adding it, and how many unions it has made.
- **Which run ids move**: the change touches `similarity`, so stages 2 to 6b move (ADR-058). Every verdict is the same.

## Tests

- `similarity.RedundancyResolutionTest.aFamilyOfCopiesIsResolvedInTimeThatDoesNotGrowWithTheSquareOfItsSize`: 4,000 copies resolve to one, in less than 6 s. It took 17.3 s before this change and 1.6 s after, alone in its JVM; the bound is a wall clock, so a much slower machine than the one measured could fail it.
- Every other test of `RedundancyResolution`, unchanged, holds that what is removed and the scores written are the same.

## What this does not decide

- Reading a bucket once for the whole pass rather than once for each page, which needs a bound ADR-225 does not have.
- Whether a wall-clock bound is the right guard for a cost no statement shows.
