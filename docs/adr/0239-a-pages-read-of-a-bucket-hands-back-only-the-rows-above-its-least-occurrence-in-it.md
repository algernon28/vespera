# ADR-239 — A page's read of a bucket hands back only the rows above its least occurrence in it

- **Date**: 2026-10-11
- **Status**: accepted on 2026-10-11.
- **Amends**: [ADR-225](0225-stage-4b-reads-its-candidate-pairs-and-containment-candidates-a-thousand-at-a-time-and-shingle-by-hash-ends-in-the-occurrence.md) §1, in its second statement, which gains `occurrence_id > ?` (§1 here). ADR-225 carries a note at its head.
- **Keeps**: ADR-225 §1's first statement, its offer of a pair once for each bucket it shares, its scoring of a pair below the cut once for each, and its reading of each bucket again for each page that names it, with its bound of 1,000 rows of one bucket held at a time; [ADR-238](0238-a-bucket-row-already-in-the-component-of-the-pages-lesser-members-in-that-bucket-is-passed-over-with-one-question.md)'s pass over a row already in the component of the page's lesser members, and what it found ADR-225 accepts. [ADR-193](0193-a-statement-sqlite-counts-reports-how-far-it-has-gone-and-one-it-cannot-count-says-how-long-it-took.md) §2: each statement finishes before the next is issued.
- **Rests on**: `similarity.RedundancyResolutionReadsOnlyTheBandRowsThatCanPairTest`; a throwaway probe outside the repository (Measured).

A *bucket* is ADR-225's: one band value, the rows of `signature_band` sharing a `band_ordinal` and a `band_hash`.

## Context

A pair is found in the page of its lesser member (ADR-225 §1), so a row of a bucket whose occurrence is no greater than every occurrence of the page in it makes no pair there. Phase 1 handed every row of each bucket to the pair loop all the same, and a page whose only occurrence in a bucket was the greatest of it was handed the whole bucket, page by page, for nothing. `RedundancyResolutionReadsOnlyTheBandRowsThatCanPairTest` counted 6,300 rows handed back for a bucket that 2,100 signed occurrences share over three pages, of which 3,299 can pair, and 4 for a pair's bucket whose greater member is in the last page, of which 1 can.

ADR-238 kept the reading of each bucket again for each page as the price of ADR-225's bound: reading a bucket once would hold its members. That stands. What this record changes is which of a bucket's rows a page is handed, which holds nothing.

## Measured

**Method.** One throwaway probe on 2026-10-11: Java 26 and the sqlite-jdbc the pom carries (3.53.2.1, SQLite 3.53.2), `signature_band` with the shipped columns and indexes, `journal_mode=WAL` and `synchronous=NORMAL` on the URL; 16 band rows for each of 20,000 occurrences, band 0 shared by the first 10,000; a bucket read a page of 1,000 rows at a time, three rounds. A Linux container's disk, the file cache warm. The probe times the statements only, and not the pair loop over the rows they hand back.

| A page's read of the bucket of 10,000 | Rows handed back | Statements | Milliseconds, three rounds |
| --- | ---: | ---: | --- |
| every row: as shipped | 10,000 | 11 | 14.3, 10.6, 8.9 |
| above the 9,000th occurrence (§1) | 1,000 | 2 | 7.6, 6.4, 7.0 |
| above the greatest occurrence (§1) | 0 | 1 | 6.4, 5.8, 6.1 |

§1's read is planned `SEARCH signature_band USING INDEX signature_band_by_bucket (run_id=? AND band_ordinal=? AND band_hash=? AND rowid>?)`, with no temp B-tree. The occurrence is compared on the row, so SQLite still goes through every row of the bucket for each page, as ADR-225 and ADR-238 accept. What is spared is the rows handed back, the statements, and for each row spared the pair loop's work on it, ADR-238's question or a question for each of the page's occurrences in the bucket.

## Decision

### 1. A page reads of a bucket only the rows above its least occurrence in it

ADR-225 §1's second statement becomes:

```sql
SELECT rowid, occurrence_id FROM signature_band
 WHERE run_id = ? AND band_ordinal = ? AND band_hash = ? AND occurrence_id > ?
 AND rowid > ? ORDER BY rowid LIMIT 1000
```

`occurrence_id > ?` is bound to the least occurrence of the page in that bucket, and the read goes on from the last row it handed back until a statement answers fewer than 1,000. **The pairs offered are the ones ADR-225 §1 offers**, each once for each bucket it shares, in the page of its lesser member, and in the same order; only rows that offer none are not handed back. So the unions, the components, the verdicts and the scores are the same.

### 2. ADR-225 §1's first statement is unchanged

Its `EXISTS` is not narrowed to a greater occurrence, which would leave a bucket where the page's occurrence is the greatest not named at all. `signature_band_by_bucket` does not hold the occurrence, so that check would read, for each band row of a page, the rows of its bucket up to the first greater occurrence. Stage 4a signs the survivors in id order, so a bucket's rows stand mostly in occurrence order and that is up to the row's own place in the bucket: on the order of the square of the bucket's size over the whole bucket, where the check as shipped stops at the first other row. §1 spares the same rows for one statement a page.

### 3. A pair sharing several buckets is still scored once for each

A pair is not scored only at the lowest band where both signatures' band hashes agree. ADR-225 decided against it (item 1 of its *Who decided*; *Alternatives refused*, *"Each pair kept at its lowest shared band, in SQL"*) and ADR-238 kept that. Phase 1 holds the band rows of the page's own occurrences and not those of the greater member a bucket's row names, so telling where a pair was first offered needs a read of those, a statement for each page of a bucket's rows or a subquery for each row, spent to spare a merge of two sets the cache holds, for a pair below the cut. Not measured here; it stays.

### 4. Which run ids move

A stage's implementation version is the last commit touching a module `StageModules` names for it (ADR-058, as [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md) leaves it). The change touches `similarity` alone in `src/main`, so stages 2 to 6b move. No DDL, no schema version, no cache key, no verdict kind; the verdicts, the rows of `redundant_with` and every line are unchanged.

## Consequences

- **A page is handed of a bucket only the rows that can pair with it**: a page whose occurrence is the greatest of a bucket gets none of it, in one statement; over a bucket of G rows read by P pages, about half the rows a page was handed where the pages' occurrences stand in order.
- **SQLite still goes through a bucket's rows for each page that names it**, as ADR-225 and ADR-238 accept.
- **A pair below the cut that shares several buckets is still scored once for each** (§3).

## Tests

| Class | What it holds |
| --- | --- |
| `similarity.RedundancyResolutionReadsOnlyTheBandRowsThatCanPairTest`, new, two tests over one resolution of a ledger written by hand | the verdicts; 3,299 rows handed back of the bucket 2,100 signed occurrences share over three pages, where 6,300 before, and 1 of a pair's bucket whose greater member is in the last page, where 4, each read planned through `signature_band_by_bucket` on from the last row and sorting nothing |

`RedundancyResolutionBoundsItsCandidatesTest` and `RedundancyResolutionReadsAPageOfSignedOccurrencesAtATimeTest` hold, unchanged, that a read of a bucket names one bucket with `LIMIT 1000` on from the last row, and that what is removed is the same.

## What this does not decide

- Whether `signature_band_by_bucket` should end in the occurrence, which would let §1's comparison and ADR-225's `EXISTS` be read from the index alone. ADR-225 refused a new index on that table for the build every working directory would make at its next start.
- Reading a bucket once for the whole pass, which ADR-238 leaves to a bound ADR-225 does not have.
- Scoring a pair only at its lowest shared band (§3), were it measured to pay.
