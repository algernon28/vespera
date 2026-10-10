# ADR-225 — Stage 4b reads its candidate pairs and its containment candidates a thousand at a time and sorts neither, `shingle_by_hash` ends in the occurrence, and every read of one occurrence's shingles names its index

- **Date**: 2026-10-10
- **Status**: accepted on 2026-10-10. **Built** by the change that carries this record: *What is to be built* is in `src/main` as that section has it, and the sixteen tests that failed before it pass (Tests).
- **Amends**: [ADR-221](0221-shingle-by-hash-is-an-index-on-the-rows-of-the-run-in-hand-and-an-earlier-runs-rows-stay.md) in the index's columns and in every figure that follows from them, and in nothing else. §1's statement, and its *"The columns are the granularity and the hash"* and *"The index does not hold `occurrence_id`, the column that read returns"*: the occurrence is the third column (§3 here). §2's *"`RedundancyResolution`'s statement is not changed"*: it is, and it keeps the run as a bound value, which is what §2 decides (§2 here). Its Steps and §5's *"12 steps for a row of the run"*, *"25% + 75% × R / T"*, *"8,334 rows"* and *"11,112"*: 13 steps, 3/13 + 10/13 × R / T, 7,693 rows and 10,000 (§3). §6's *"51 bytes for each shingle row of the run in hand"* and its worked table: 58 (§5). Its Measured's *"23.3 to 24.4 bytes of temporary files, 24.3 to 25.3 of write-ahead log, and 24.1 to 25.2 of index in the file"*, which are of the two-column index. Its Plans' fifth row, *"one occurrence's shingles, as stages 4a and 4b read them … through `shingle_by_occurrence`"*: left to choose, such a read is drawn to the index of this record, and it is no longer left to choose (§4). Its P1's text of the statement, and P2's 12. What ADR-221 decides of whose index it is, of how stage 4b tells, of the run id written into the statement, of the build's lines and of the rows of earlier runs stands.
- **Amends**: [ADR-220](0220-no-class-holds-every-occurrence-of-a-run-stage-2s-resume-the-census-stage-4b-and-stage-5e-read-a-page-at-a-time-or-ask-by-key-and-what-is-still-held-says-why.md) §4 in two of stage 4b's statements, the candidate pairs of a page and the containment candidates of an occurrence, and in its *"Each pair is scored as now"* (§1, §2); §9 in its two entries for stage 4b's candidate pairs of one page and containment candidates of one occurrence, which are no longer held; and §15 in (b), in the answers to its questions 7 and 8 and in what it owes under #476, which this record measures (Measured). Its entry (a), an occurrence's rarest shingles, stands and gains its measured size (§6).
- **Amends**: [ADR-218](0218-every-statement-whose-temporary-files-grow-with-the-corpus-is-an-exception-to-adr-060-with-its-size.md) in what it lists: row 6 of its table of statements whose temporary files grow with the corpus no longer exists. And [ADR-224](0224-the-invocation-accounts-counts-by-kind-and-the-two-reads-of-the-embedder-identities-sort-nothing-and-four-of-adr-218s-reads-stay-excepted.md) §4, *"Row 6 stays excepted here"*: the statement is gone, so three of its four exceptions are left, rows 7, 10 and 11.
- **Keeps**: [ADR-079](0079-redundant-with-covers-near-duplication-and-containment-the-fuller-rendering-survives.md), [ADR-081](0081-minhash-retrieves-shingle-sets-judge-128-permutations-in-16-bands-and-containment-gets-its-own-index.md) and [ADR-082](0082-stage-4-judges-on-its-first-run-its-thresholds-are-code-defaults-and-it-ships-no-report.md): what stage 4b removes, what it is redundant with and the score written are unchanged (§7). ADR-220 §9's components with their members' profiles and its shingle-set cache, which are not this record's. [ADR-192](0192-every-loop-in-every-stage-reports-its-progress-and-no-counter-floods-the-log.md) and ADR-220 §4's counters, each told as often as before (§7). [ADR-209](0209-the-ledger-is-four-records-behind-one-type-no-capability-module-names-spring-batch-and-a-tables-sql-is-its-owners.md) §3: every statement here names `similarity`'s own tables. [ADR-193](0193-a-statement-sqlite-counts-reports-how-far-it-has-gone-and-one-it-cannot-count-says-how-long-it-took.md) §2: each statement finishes before the next is issued.
- **Rests on**: the operator's word of 2026-10-10, relayed to the session that wrote this record by the coordinating session, in the words *"yes, decide #476 yourself"*, and by the session that keeps the coordination board, *"tell the agents to make all decisions regarding their tickets"*. The four choices under *Who decided* are that session's, made on the measurements of this record under rules the coordinating session set. They are not the operator's own answers. The operator's decision of the same day, relayed the same way, that #476 ships in one build with the changes of #458, #353, #472, #477, #468, #479 and #486 (§8). [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) and [ADR-048](0048-walk-and-run-identity.md) (§8). Throwaway probes over synthetic ledgers built from the shipped `schema.sql` (Measured). No archive and no working directory was opened for this record ([ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md)).
- **Decides** [#476](https://github.com/algernon28/vespera/issues/476).

**A *bucket* here is one band value**: the signed occurrences of a stage-4 run whose signatures hash alike in one band, the rows of `signature_band` sharing a `band_ordinal` and a `band_hash` (ADR-081). It is never a seed partition. An *index* is always a SQLite index on a table.

## Who decided

The session that wrote this record, on 2026-10-10, under the word quoted above. The coordinating session's rules for it: the verdicts are the same; no cap that could change a verdict; the form with no temporary file and a bounded heap is preferred; the third column is taken only if the form that needs it is measured clean and nothing on the two-column index is bounded without changing what a counter counts.

1. **The candidate pairs are read by bucket**, with no new index (§1). The plain form, in which a pair sharing several buckets is offered once for each, is taken over the one that offers each pair once, with a pair already in one component not scored again.
2. **The containment candidates are merged from ordered reads**, one for each rarest hash (§2).
3. **`shingle_by_hash` gains `occurrence_id` as its third column**, amending ADR-221 by name, at 3.0 to 3.7 bytes more a row of the run in the file and 7.4 more at a first build's peak (§3, §5). The form on the two-column index that sorts nothing changes what the containment counter counts, and the one that keeps the counter sorts (Measured).
4. **Nothing is excepted but what already was**: an occurrence's rarest shingles, bounded by one occurrence (§6).

## Context

ADR-220 put a read of one page of signed occurrences in the place of stage 4b's read of every band row of the run. Two things it left growing with the corpus, and the operator excepted both as they stood on 2026-10-09, unmeasured, until this ticket:

- **A page's candidate pairs.** One statement, `SELECT DISTINCT a.occurrence_id, b.occurrence_id FROM signature_band a CROSS JOIN signature_band b …`, for a page of 1,000 lesser members. The page bounds the lesser member and not the greater: at most 1,000 times the signed occurrences less one. SQLite sorts them for the `DISTINCT`, and `candidatePairsOf` answers them as a list.
- **An occurrence's containment candidates.** One statement, `SELECT occurrence_id FROM shingle WHERE … shingle_hash IN (…) GROUP BY occurrence_id HAVING COUNT(DISTINCT shingle_hash) >= ?`, over the occurrence's 32 rarest hashes, answered as a set, from which `resolveContainment` makes a list and two more sets. On disk it is ADR-218's row 6.

ADR-224 kept row 6 excepted and gave its bounded form, one ordered read for each hash merged in Java over an index that ends in the occurrence, to this ticket, after #468. ADR-221 then made `shingle_by_hash` an index on two columns over one run's rows.

## Measured

**Method.** One throwaway probe outside the repository, on 2026-10-10, made as ADR-218's and ADR-221's were: Java 26.0.2.1 and the sqlite-jdbc the pom carries (3.53.2.1, SQLite 3.53.2), the shipped `schema.sql` at `066040d`, the shipped URL's parameters, and SQLite's directory for temporary files set once, on a connection to no database, before the database was opened. While each statement ran, the sizes of the files in that directory were summed every 5 ms and the greatest sum kept; for a build, the write-ahead log and the database file too. A time is the best of three rounds unless it says otherwise. A statement is prepared anew for each call, as `JdbcTemplate` sends it. One plain connection; `foreign_keys` turned off after the schema was run, the probe writing no walk and no run. A heap is the used heap after four collections with the collection alive, less the same before it was built.

**Disk.** A solid-state NVMe disk, NTFS, Windows 11, the file cache warm. **Every figure in this record is from synthetic ledgers on that disk.** Sizes are in bytes.

**Calibration.** ADR-221's build of the two-column index over 4,500,000 rows of one run, on a ledger made as its own: 105,111,245 bytes of temporary files where ADR-221 records 105,111,259, and its 109,188,272 of write-ahead log, 108,544,000 of index and 217,732,272 at the peak to the byte.

### The candidate pairs of one page

G signed occurrences share one bucket, among 50,000 that share none; the first page of 1,000 signed occurrences is read. Each form was checked to hand on the same pairs.

| G, and the bands they share | Pairs of the page | As shipped: temporary files | A pair | Time | The list on the heap | A pair |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 5,000, one | 4,499,500 | 49,176,576 | 10.9 | 4.9 s | 171,062,312 | 38.0 |
| 10,000, one | 9,499,500 | 105,848,832 | 11.1 | 10.2 s | 364,889,568 | 38.4 |
| 5,000, all sixteen | 4,499,500 | 49,176,576 | 10.9 | 71.4 s | 170,116,064 | 37.8 |

| Form | Temporary files | Time, G of 5,000; 10,000 | Statements a page | Cost |
| --- | ---: | --- | ---: | --- |
| the statement without `DISTINCT`, its rows scored as they come | none | 4.3 s; 8.7 s. 64.7 s where sixteen bands are shared, 71,992,000 rows | 1 | the statement is open while each pair is scored, and scoring reads shingle sets on the same connection |
| the same, each pair kept only at the lowest band it shares | none | 5.6 s; 11.3 s | 1 | the same, and a correlated subquery for every row |
| **by bucket** (§1) | none | 0.097 s; 0.113 s | 7; 12 | a pair is offered once for each band it shares |
| an index on `signature_band` ending in the occurrence, each lesser member's buckets read in order and merged | none | 0.55 s; 1.01 s | 5,001; 10,001 | a thirty-second index in `schema.sql`, its start-up build 89.7 to 90.6 bytes a row of temporary files where the shipped index's is 86.3 to 87.1 |

- **The plans.** As shipped: `SEARCH a USING INDEX sqlite_autoindex_signature_band_1 (occurrence_id=? AND run_id=?)`, `SEARCH b USING INDEX signature_band_by_bucket (run_id=? AND band_ordinal=? AND band_hash=?)`, `USE TEMP B-TREE FOR DISTINCT`. By bucket, its first statement: the same search of `a`, then `SEARCH b EXISTS USING COVERING INDEX signature_band_by_bucket (run_id=? AND band_ordinal=? AND band_hash=?)`; its second: `SEARCH signature_band USING INDEX signature_band_by_bucket (run_id=? AND band_ordinal=? AND band_hash=? AND rowid>?)`. Neither has a temp B-tree.
- **The by-bucket form was not timed alone where sixteen bands are shared**: that run was stopped. The whole pass below holds it on 300 near-copies that share most of their bands.

### The containment candidates of one occurrence

M occurrences carry the same 40 hashes, ten more of their own each, among filler rows of five to an occurrence, all of one run; the candidates of the first are found. Index (a) is ADR-221's, `(shingle_parameter_identity, shingle_hash) WHERE run_id = '…'`; index (b) is §3's, with `occurrence_id` third. Every form answered the same candidates.

| M; rows in `shingle`; rows carrying the 32 rarest hashes | Form | Index | Temporary files | A row | Time a call |
| --- | --- | --- | ---: | ---: | ---: |
| 20,000; 1,000,000; 640,000 | as shipped, one grouping | (a) | 8,955,934 | 14.0 | 1,351 ms |
| | the same | (b) | 8,955,934 | 14.0 | 121 ms |
| | **form C**, 32 ordered reads, a page of 1,000 at a time, merged (§2) | (b) | none | | 100 ms |
| | form C's reads on the two-column index | (a) | none: the sort of 20,000 rows stayed in memory | | 28.0 s, one round |
| | form D's reads: the 9 rarest hashes, a page at a time by row number | (a) | none | | 364 ms |
| 100,000; 5,000,000; 3,200,000 | as shipped | (a) | 51,043,500 | 16.0 | 6,840 ms |
| | the same | (b) | 51,043,500 | 16.0 | 949 ms |
| | **form C** | (b) | none | | 727 ms |
| | form C's reads on the two-column index | (a) | 567,111, 5.7 for each row of one hash | | 728 s, one round |
| | form D's reads | (a) | none | | 2,254 ms |

- **On the heap, as shipped**: the set, the list of the others and the two sets of which are signed and which removed held 2,555,424 bytes for 20,000 candidates and 13,647,600 for 100,000, 128 to 137 bytes a candidate.
- **The plans.** As shipped on (a): `SEARCH shingle USING INDEX shingle_by_hash (shingle_parameter_identity=? AND shingle_hash=?)` and two temp B-trees, for the grouping and for the count. On (b): the same search `USING COVERING INDEX`, and the one for the grouping. Form C's read on (b): `SEARCH shingle USING COVERING INDEX shingle_by_hash (shingle_parameter_identity=? AND shingle_hash=? AND occurrence_id>?)`, and no temp B-tree. The same read on (a): the search of two columns and `USE TEMP B-TREE FOR DISTINCT`, every row of the hash read and sorted again for each page of 1,000. Form D's read on (a): `… AND shingle_hash=? AND rowid>?`, no temp B-tree.
- **Form D** takes no new column. Any occurrence carrying 24 of 32 hashes carries one of any 9 of them, so it reads the occurrences of the 9 rarest a page at a time and takes the count of hits from each one's shingle set once that is loaded. It cannot tell a candidate from an occurrence carrying one hash until then, so it goes through every one: 115,302 in the pass below where the other forms go through 5,504.

### The build of the index with its third column

| Rows of the run, alone in the table | Index | Temporary files | Write-ahead log | Index in the file | Peak, first build; built again into freed pages | Time |
| ---: | --- | ---: | ---: | ---: | --- | --- |
| 4,500,000 | (a) | 105,111,245, 23.4 a row | 109,188,272, 24.3 | 108,544,000, 24.1 | 217,732,272, 48.4; 212,375,477, 47.2 | 3.1 s; 2.9 s |
| 4,500,000 | (b) | 122,490,519, 27.2 | 125,828,952, 28.0 | 125,087,744, 27.8 | 250,916,696, 55.8; 245,995,791, 54.7 | 3.8 s; 3.4 s |
| 5,000,000 | (a) | 117,135,349, 23.4 | 121,292,832, 24.3 | 120,578,048, 24.1 | 241,870,880, 48.4 | 3.5 s |
| 5,000,000 | (b), built after (a) was dropped | 135,490,632, 27.1 | 139,779,272, 28.0 | 138,956,800, 27.8 | 272,999,784, 54.6 | 4.7 s |
| 1,000,000 | (a) | 24,015,141, 24.0 | 24,246,232, 24.2 | 24,096,768, 24.1 | 48,343,000, 48.3 | 0.58 s |
| 1,000,000 | (b), built after (a) was dropped | 27,008,751, 27.0 | 27,295,032, 27.3 | 27,127,808, 27.1 | 51,708,183, 51.7 | 0.64 s |

| 1,052,000, the ledger of the whole pass below, two runs of the probe | (a) | 25,159,136, 23.9 | 25,535,792, 24.3 | 25,378,816, 24.1 | 50,914,608, 48.4 | 0.69 to 0.72 s |
| 1,052,000, the same | (b), built after (a) was dropped | 28,289,786, 26.9 | 28,737,032, 27.3 | 28,561,408, 27.1 | 54,571,298, 51.9; 54,826,738, 52.1 in the second run | 0.71 to 0.72 s |

- **The third column costs 3.0 to 3.7 bytes a row of the run in the file**, 3.0 to 3.8 of temporary files and 3.0 to 3.7 of write-ahead log, each (b) less the (a) of the same ledger. **At the peak it costs 7.4 where both are first builds**, the pair at 4,500,000 rows, 55.8 against 48.4, and 7.5 where both are built again into freed pages, 54.7 against 47.2. The other three pairs do not compare like with like: their (b) was built into the pages the drop of (a) had freed, so the file grew by less than the index, and their peaks are 3.4 to 6.2 above (a)'s first build. The index in the file is counted from the pages in use before and after.
- **Steps**, counted one at a time: 13,346 for 1,000 rows of the run, 39,346 for 3,000, 19,346 for 1,000 among 2,000 of another run, 9,346 for 3,000 of another run alone. **13 for a row of the run, 3 for a row of any other, and 346 besides**, where ADR-221 measured 12, 3 and 346.
- **Not measured**: the build for a run among the rows of other runs. ADR-221 measured the two-column build's peak rising from 48.4 to 50.5 bytes a row for a run written after 9,000,000 rows of others.

### What the third column draws, and what stops it

With (b) built for the run and the run bound, each shipped read of `shingle` was planned.

| Read | Plan, left to choose | Plan, naming `shingle_by_occurrence` |
| --- | --- | --- |
| one occurrence's shingle set (4a, 4b) | `SEARCH shingle USING COVERING INDEX shingle_by_hash (shingle_parameter_identity=?)`: every row of the run | `SEARCH shingle USING INDEX shingle_by_occurrence (occurrence_id=? AND run_id=? AND shingle_parameter_identity=?)` |
| one occurrence's rarest shingles (4b) | `SEARCH s USING COVERING INDEX shingle_by_hash (shingle_parameter_identity=?)`, then the frequency row by its key, and the two temp B-trees | `SEARCH s USING INDEX shingle_by_occurrence (…)`, the rest as it was |
| one occurrence's count of rows (4b) | `SEARCH shingle USING COVERING INDEX shingle_by_occurrence (…)`: not drawn | the same |
| stage 3's three reads of a page of occurrences | through `shingle_by_occurrence (occurrence_id=? AND run_id=?)`: not drawn | |
| stage 3's grouping, with ADR-219's clause and without it | through `shingle_by_run_id`: not drawn | |

**The index holds every column a read of one occurrence's shingles asks for, and SQLite takes it.** That is #277's defect, which ADR-221 found only of a read asked for `DISTINCT`.

### The whole of stage 4b

5,000 occurrences of 200 shingles each, made as ADR-220's stage-4 ledger was, every fiftieth a near-duplicate of the one before it and every fiftieth, twenty-five on, wholly contained in the one before it; and 300 near-copies of one text, 198 of their 200 shingles shared. 1,052,000 shingle rows, 86,985 frequency rows, 80,000 band rows, signatures of 128 values in sixteen bands. Two runs of the probe; each time is one pass.

| Phase | Form | Index | Time | Statements | Pairs scored, or candidates gone through | Rows written |
| --- | --- | --- | ---: | ---: | ---: | --- |
| 1 | as shipped | | 0.56 to 0.58 s | 962 | 44,976 | 397 near-duplicates |
| 1 | **by bucket, a pair in one component not scored again** | | 0.39 s | 1,313 | 427 | the same 397 |
| 2 | as shipped | (a) | 6.7 to 6.8 s | 15,632 | 5,504, 300 the most for one | 100 contained |
| 2 | form D | (a) | 5.8 s | 147,202 | 115,302, 2,700 the most for one | the same 100 |
| 2 | form C, the reads of one occurrence left to choose | (b) | 369 s | 167,625 | 5,504 | the same 100 |
| 2 | as shipped, the same reads left to choose | (b) | 367 s | 15,632 | 5,504 | the same 100 |
| 2 | **form C, those reads naming `shingle_by_occurrence`** | (b) | 6.2 s | 167,625 | 5,504 | the same 100 |
| 2 | as shipped, those reads naming it | (b) | 4.7 s | 15,632 | 5,504 | the same 100 |

- **Every form wrote the same rows**: the occurrence, what it is redundant with, the relation and the score, compared row by row.
- **A pass at 20,000 occurrences was started with (b) and the reads left to choose, and was stopped after thirty minutes**, which is how the draw was found. It was not run again at that size.
- **The probe's stage 4b is its own code**, written to ADR-220 §4 and to §1 and §2 here, with a cache of every set and no budget. It measures the statements and not `RedundancyResolution`.

### An occurrence's rarest shingles

One occurrence, each of its shingle rows with a frequency row, read by `rarestHashes`' statement.

| Shingle rows of the occurrence | Temporary files | A row | Time |
| ---: | ---: | ---: | ---: |
| 150,000 | 4,136,019 to 4,192,422 | 27.6 to 27.9 | 0.53 to 0.55 s |
| 1,000,000 | 27,390,619 to 27,949,630 | 27.4 to 27.9 | 5.6 s |

It does write a temporary file, which ADR-220 §15 left open. The two figures of each row are with no `shingle_by_hash` and with ADR-182's, the index on main when they were taken; the plan is the same in both, the two temp B-trees ADR-220 records.

## Decision

### 1. A page's candidate pairs are found a bucket at a time

For each page of up to 1,000 signed occurrences, where ADR-220 §4 has one statement:

**The page's band rows that share their bucket**, one statement:

```sql
SELECT a.occurrence_id, a.band_ordinal, a.band_hash FROM signature_band a
 WHERE a.run_id = ? AND a.occurrence_id IN (?, …)
 AND EXISTS (SELECT 1 FROM signature_band b WHERE b.run_id = a.run_id AND b.band_ordinal = a.band_ordinal
 AND b.band_hash = a.band_hash AND b.rowid <> a.rowid)
```

At most 16,000 rows, sixteen for each occurrence of the page, held until the page is done, grouped by bucket.

**Then each of those buckets, a page of its rows at a time**:

```sql
SELECT rowid, occurrence_id FROM signature_band
 WHERE run_id = ? AND band_ordinal = ? AND band_hash = ? AND rowid > ? ORDER BY rowid LIMIT 1000
```

going on from the last row read until a statement answers fewer than 1,000. For each row read, and each occurrence of the page in that bucket whose id is less than the row's occurrence, the two are a candidate pair. Each statement has finished before a pair of its rows is scored.

**Every pair sharing a bucket is found in the page of its lesser member, as before, once for each bucket it shares.** The `DISTINCT` gave each once. So:

**A pair whose two members are already in one component is not scored.** `UnionFind` answers whether both are held and have one root, adding neither. The components are the same: a union of two members of one component changes nothing, whatever the pair scores. The score a verdict carries is not the pair's but the member's against its component's survivor, computed when the component is resolved (ADR-220 §4, ADR-082), so it is the same too. A pair below the cut that shares several buckets is scored once for each; scoring reads two sets the cache holds.

**What it holds**: the page, its band rows that share a bucket, and 1,000 rows of one bucket. No list of pairs.

**The order pairs are scored in changes**, as it did with ADR-220, and decides nothing.

### 2. An occurrence's containment candidates are merged from one ordered read for each of its rarest hashes

For an occurrence A not removed in phase 1, with its rarest hashes found as ADR-220 §4 has them:

**Where A has fewer rarest hashes than a candidate must carry, 24, no read is made**: none can be a candidate, and the grouping answered none.

**Otherwise one read for each hash**, a page of up to 1,000 occurrences at a time:

```sql
SELECT DISTINCT occurrence_id FROM shingle
 WHERE run_id = ? AND shingle_parameter_identity = ? AND shingle_hash = ?
 AND occurrence_id > ? ORDER BY occurrence_id LIMIT 1000
```

going on from the last occurrence read until a statement answers fewer than 1,000. The run is a bound value, as ADR-221 §2 decides. With the index of §3 built for that run the rows come from the index alone, in order, and nothing is sorted (Measured).

**The reads are merged**: the least occurrence at the head of any read is taken, with every read that has it at its head, and it is a candidate where at least 24 reads had it. That is the occurrences the grouping answered, `COUNT(DISTINCT shingle_hash) >= 24`, in ascending order.

**The candidates are gone through a thousand at a time.** For each thousand, which of them other than A are signed and which phase 1 removed is asked by key, as ADR-220 §4 asks it of them all; then each is gone through as now. The best container is the one of highest score, and of lowest id among equals, which does not depend on the order candidates come in.

**What it holds**: at most 1,000 occurrences for each of the 32 reads, and 1,000 candidates with the two answers about them. No set of every candidate.

### 3. `shingle_by_hash` ends in the occurrence

Stage 4b builds it with ADR-221 §1's statement and one column more:

```sql
CREATE INDEX shingle_by_hash ON shingle (shingle_parameter_identity, shingle_hash, occurrence_id) WHERE run_id = '<the stage-2 run id>'
```

One space between words and after each comma, as written here: `sqlite_master` keeps it as issued, and ADR-221 §3 compares it character for character. Everything else of ADR-221 §1, §3 and §4 stands.

**The build takes 13 steps for a row of the run and 3 for a row of any other** (Measured), so `SHINGLE_HASH_INDEX_BUILD` declares 13. ADR-221 §5's lines keep their words. Its share at the end becomes 3/13 + 10/13 × R / T, 100% for a run alone and 23% and upward otherwise; a callback is 7,693 rows at most; and the line that the rows are gone through is not written where the table keeps more than about 10,000 rows of other runs.

**A database whose index was built under ADR-221 needs no migration.** Its statement is not this record's for any run, so it is the fourth row of ADR-221 §3's table: dropped, and this run's built. In the upgrade stage 4b does not meet it at all: the build that carries this record moves `similarity`, so stage 2 has a new run and drops the index before it writes (§8). Both are read from ADR-221 §3 and `ShingleHashIndex.buildFor`, and neither was run.

### 4. Every read of one occurrence's shingles names the index on the occurrence

The third column makes `shingle_by_hash` hold every column such a read asks for, and SQLite then reads the whole run through it for one occurrence (Measured: 369 s where 6.2 s). So the three shipped reads say which index they are answered through, as ADR-219 has stage 3's grouping say it:

- `RedundancyResolution`'s read of a shingle set and `RedundancySignatures`' read of the same, one text in both: `SELECT shingle_hash FROM shingle INDEXED BY shingle_by_occurrence WHERE occurrence_id = ? AND run_id = ? AND shingle_parameter_identity = ?`;
- `rarestHashes`' statement, in its first table: `FROM shingle s INDEXED BY shingle_by_occurrence JOIN shingle_document_frequency f …`.

`shingle_by_occurrence` is `schema.sql`'s, built at every start that lacks it, so the clause never names an index that is absent. The count of an occurrence's rows is not drawn and is not changed. Stage 4a's read is among them because a second stage-4 run over the same stage-2 run, which a changed boilerplate floor mints, finds the index built for that run (ADR-221, *What is kept*).

**A read of one occurrence's shingles added later without the clause is drawn**, with or without `DISTINCT`. `RedundancyResolutionTest`'s guard for #277 fails on one that goes through `JdbcTemplate`'s two methods it watches, and `RedundancyResolutionBoundsItsCandidatesTest` on any that stage 4b issues.

### 5. What the working directory's drive needs free at stage 4b

**58 bytes for each shingle row of the run in hand**, where ADR-221 §6 has 51:

> free bytes ≥ 58 × R, where R is the rows of the stage-2 run stage 4b reads

The greatest peak measured for this index is 55.8 a row, for a run alone in its table. ADR-221's 51 is of a run written after 9,000,000 rows of other runs, 2.1 above the same build alone; that position was not measured for this index, and the 58 is 55.8 with that 2.1 added and rounded up. It carries no other margin. About 28 of the 58 stay in `vespera.db` as the index. The build is still an exception to ADR-060's bound, as ADR-221 §6 leaves it: 26.9 to 27.2 bytes of temporary files a row of the run.

Worked for the 42,833,917 rows on record, were every one of them one run's, which no agent knows: 2,484,367,186 bytes, 2.31 GiB.

### 6. What stays excepted

**An occurrence's rarest shingles**, ADR-220 §15's (a): bounded by one occurrence's shingle rows that have a frequency row, and needing no exception. Its size is now measured: 27.4 to 27.9 bytes of temporary files a row, 27,949,630 bytes for an occurrence of 1,000,000 shingles. It gains §4's clause and is otherwise unchanged.

Nothing else of stage 4b's sorts where stage 4b runs it. `EveryStatementThatSortsIsRecordedTest` counts two statements for `RedundancyResolution`: that one, and §2's read, which it plans with no index of the run's to answer it and which there has to sort (Tests).

### 7. What an operator reads, and what stage 4b writes

**The verdicts, the rows of `redundant_with` and every line are unchanged.** `Stage 4b (redundancy resolution, near-duplicate candidates)` counts signed occurrences as ADR-220 has it, a page's thousand told together once its pairs are scored. The containment counter is told once for each signed occurrence, and the counter of candidates gone through once for each candidate, the same candidates in ascending order.

### 8. Which run ids move, and when it ships

A stage's implementation version is the last commit touching `src/main/java/io/algernon/vespera/<module>` for a module `StageModules` names for it (ADR-058). **The change that carries this record carries its build, and touches one module, `similarity`.** As `StageModules` stands at `066040d`, since [ADR-226](0226-the-eight-rules-in-pipeline-live-in-extraction-embedding-synthesis-and-profile-and-no-stages-version-names-pipeline.md):

| Stage | Modules it names | Moves with this build |
| --- | --- | --- |
| byte-level reduction (1) | `corpus` | no |
| extraction (2) | `extraction`, `similarity` | yes, for `similarity` |
| content census (3), content redundancy (4) | `similarity`, `extraction` | yes, for `similarity`, and for the run upstream |
| seed measurement and embedding scoring (5) | `embedding`, `extraction` | yes, for the run upstream alone (ADR-048) |
| arrangement (6a) | `synthesis`, `extraction`, `embedding` | yes, the same way |
| generation (6b) | `synthesis`, `extraction`, `embedding`, `profile` | yes, the same way |

**So stages 2 to 6b move.** Stage 2 does its work again from the extraction cache and writes one more copy of the corpus's shingle rows (ADR-221 §8); the arrangement is a new one, and `arrangementApproved` must name it.

**It ships in one build with the changes of #458, #353, #472, #477, #468, #479 and #486**, by the operator's decision of 2026-10-10, so that those stages are minted once by that build as a whole and not once for each. All seven are on main as this change is gated, #486 since `35a5a6b`, and so is #481's (ADR-229): this is the last change of that build.

No DDL in `schema.sql`, no schema version, no cache key. The comment of `schema.sql` that names the index's columns is corrected.

## What is to be built

All of it in `similarity`, and one comment.

**B1, `ShingleHashIndex.buildFor`.** The statement of §3 in the place of ADR-221's: `(shingle_parameter_identity, shingle_hash, occurrence_id)`. Nothing else of the method changes. The class javadoc names the three columns and cites this record.

**B2, `SimilarityStatement`.** `SHINGLE_HASH_INDEX_BUILD(13)`, its javadoc saying 13.

**B3, `RedundancyResolution`, phase 1.** `candidatePairsOf` and the record `PairKey` go. For each page: §1's first statement, its rows grouped by bucket; for each bucket §1's second statement until it answers fewer than `OCCURRENCES_IN_A_PAGE` rows; each pair scored and unioned at or above the cut as now, unless `UnionFind` answers that the two are already joined. `candidatesScored` is told as now.

**B4, `UnionFind`.** One method, package-private, `boolean joined(long a, long b)`: true where both are held and have one root. It adds neither, so the structure still holds only the occurrences a union named.

**B5, `RedundancyResolution`, phase 2.** `containmentCandidates` goes. §2 in its place: no read where the rarest hashes are fewer than `rareShingleHitCount`; one read for each hash a page at a time; the merge; the candidates handed on in ascending order and gone through `OCCURRENCES_IN_A_PAGE` at a time, `signedAmong` and `removedAmong` asked of each batch's occurrences other than A. The best container is kept across batches. `containmentCandidateGoneThrough` is told once for each candidate, A included, on every path, as now.

**B6, the clause of §4**, in `RedundancyResolution.ShingleSetCache.load`, in `RedundancyResolution.rarestHashes`, and in `RedundancySignatures.distinctiveShingleSet`. The two reads of a shingle set are one text, to the character: `RedundancyResolutionTest` holds that stage 4 reads one occurrence's shingles in two shapes.

**B7, javadoc** (ADR-216): `RedundancyResolution`'s class javadoc, which says containment retrieval is a `GROUP BY ... HAVING`, names the index's two columns, and lists a page's pairs and an occurrence's candidates as held until #476; `rarestHashes`'; `RedundancySignatures.distinctiveShingleSet`'s, which says the planner chooses `shingle_by_occurrence` where the index is absent.

**B8, `src/main/resources/schema.sql`, comment only**: the columns of `shingle_by_hash` in the comment that begins *"NOT CREATED HERE, on purpose (ADR-182)"*.

**Edit no test.** One the build finds it has to edit is a finding for the analyst. **Verify with `./mvnw verify`.**

## Alternatives refused

- **A cap on the pairs of a page or on the candidates of an occurrence.** It would leave pairs unscored or containers unseen, which changes verdicts.
- **The pairs statement without `DISTINCT`, read as a stream.** No temporary file, and the statement open while every pair is scored: ADR-193 §2 has each statement finish first, and scoring reads shingle sets on the one connection the test profile has.
- **Each pair kept at its lowest shared band, in SQL.** One offer a pair, at 1.3 times the plain statement's time, a correlated subquery for every row, and the same open statement.
- **An index on `signature_band` ending in the occurrence, with a merge.** Five to nine times slower than reading by bucket at the sizes measured, a statement for every lesser member, and a new index in `schema.sql` that every existing working directory would build at its next start.
- **Form D**, on ADR-221's index unchanged. Nothing sorted and no heap, and it goes through every occurrence carrying one of nine hashes: twenty-one times as many in the pass measured, each counted by the candidates counter, whose item would no longer be a candidate.
- **Form C's reads on the two-column index.** They sort every row of a hash again for each page: 728 s for one occurrence where the index of §3 takes 0.7 s. One read for each hash without a page sorts the rows of one hash, 5.7 bytes a row, which grows with the corpus.
- **Keeping the grouping on the index of §3.** Seven times quicker than on ADR-221's, and it still sorts 14 to 16 bytes for each row carrying a rarest hash and answers every candidate at once.
- **Row 6 excepted with its size.** The size is measured, 51,043,500 bytes for one occurrence with 100,000 candidates, and a form with none was found.
- **The clause left off, and the guard relied on.** The draw is certain, not a risk, with the third column.

## Consequences

- **No statement of stage 4b sorts, and no class of it holds, a collection that grows with the signed occurrences sharing a bucket or with the occurrences carrying an occurrence's rarest hashes.** What stage 4b still holds is ADR-220 §9's: the components with their profiles, and the shingle-set cache within its budget.
- **`shingle_by_hash` is about 3.5 bytes a row of the run larger in the file, and its build needs 58 bytes a row free where it needed 51.**
- **Containment retrieval sends 32 statements or more for an occurrence where it sent one**: 167,625 where 15,632 in the pass measured, at 0.9 times the time.
- **A read of one occurrence's shingles written without §4's clause reads the whole run**, wherever the run's index is built. Nothing in `src/main` checks a plan at run time.
- **Containment retrieval without the run's index is slower than it was**: 32 reads of every row of the run for an occurrence where the grouping made one. ADR-221 §3 is what keeps stage 4b from that state; several tests resolve without building the index, over a few rows.
- **A pair of one component is not scored again**, so a bucket of near-copies costs its members' unions and not its pairs' scores: 427 scored where 44,976.
- **The run ids of stages 2 to 6b move**, once with the build this ships in (§8).

## Tests

| Class | What it holds |
| --- | --- |
| `similarity.RedundancyResolutionBoundsItsCandidatesTest`, new, two tests over one resolution of a ledger written by hand, signatures and band rows included | **The verdicts**: of 1,050 signed occurrences in one bucket, three form a chain and the two with less text are removed, the first with its score against the survivor, 8 of 12; a pair sharing three buckets is removed once; a pair sharing two and a third of its shingles is not; an occurrence of which 1,107 others hold every shingle or all but one, 1,108 candidates with itself, is contained in the lower of the two signed ones that hold all of it and tie, which stand in different thousands of its candidates, and not in the unsigned one of lowest id, the one removed as a near-duplicate, or those that hold all but one of its shingles; five rows and five verdicts and no other. **The statements**: the read naming a page's occurrences is planned as one read of rows and one check that a row exists, and no join; 6,650 candidates are gone through, each once, one of them an occurrence whose every shingle row is written twice; none but the rarest shingles' is planned through a temp B-tree; every read of `signature_band` names at most 1,000 occurrences or one bucket with `LIMIT 1000` from the last row read; every read by one hash goes on from the last occurrence with `LIMIT 1000` and is planned `COVERING INDEX shingle_by_hash`; none groups by occurrence; every read of one occurrence's shingles is planned through `shingle_by_occurrence`; none names more than 1,000 occurrences |
| `similarity.TheRunsHashIndex` | §3's statement, written there a second time on purpose, as ADR-221's was |
| `similarity.ShingleIndexesInTheSchemaTest`, three tests turned | that the index is on three columns; that §2's read is answered through the run's index from the index alone with nothing sorted, and not without it nor through another run's; that a read of one occurrence's shingles goes through `shingle_by_occurrence` because it names it, and through the run's index where it does not |
| `pipeline.ShingleHashIndexInvocationTest` | the index's three columns after whole invocations, and §2's read planned through the index only for the run it was built for |
| `pipeline.StatementStepsPerRowTest`, `pipeline.StatementStepsPerRowAreTheDeclaredOnesTest` | 13 steps a row of the run, measured and declared |
| `pipeline.StatementProgressInvocationTest` | the bound on the other runs' rows below which the line that the rows are gone through can be written, 10,000 |
| `similarity.RedundancyResolutionReadsAPageOfSignedOccurrencesAtATimeTest`, ADR-220's, one claim widened | a read of `signature_band` names at most 1,000 occurrences, or one bucket with `LIMIT 1000` |
| `EveryStatementThatSortsIsRecordedTest` | two statements for `RedundancyResolution` where it held three, in both its states |

**Run on 2026-10-10 against `src/main` before the build, under Java 26**: 1,668 tests, none skipped, 16 failing, and each on the claim this record changes. **Run again against the build**: 1,674 tests and the 23 of the ten integration classes, none skipped, none failing.

| Test | Fails on | Turned by |
| --- | --- | --- |
| `TheHashIndexIsBuiltForOneRunTest`, four of five | the statement the database keeps is ADR-221's and not §3's | B1 |
| `ShingleHashIndexInvocationTest`, six of eight | the same, and the index's two columns | B1 |
| `AnEarlierRunsRowsStayInvocationTest`, one of two | the same | B1 |
| `StatementStepsPerRowAreTheDeclaredOnesTest` | the build declares 12 steps a row | B2 |
| `EveryStatementThatSortsIsRecordedTest`, two of four | `RedundancyResolution` holds three statements that sort | B3 and B5 |
| `RedundancyResolutionBoundsItsCandidatesTest`, its second test | the pairs statement and the grouping are planned through a temp B-tree, and no others | B3, B5 and B6 |
| `RedundancyResolutionTest`, #277's guard | the read of a shingle set is planned through `shingle_by_hash` | B6 |

`RedundancyResolutionBoundsItsCandidatesTest`'s first test, the verdicts, passed against `src/main` as it stood and passes against the build: that is the acceptance's *"stage 4b's verdicts are the same"*. Until B6 was built the tests that build the index for their run and then resolve read the whole run for each occurrence, over a few thousand rows.

**What no test holds.**

- **Any size or time**: the probe's. A test of the suite cannot watch the process's temporary files (ADR-218, Tests).
- **A heap.** That stage 4b holds no list of pairs and no set of candidates is held through the statements it issues, as ADR-220's tests hold it.
- **That a pair of one component is not scored again.** Nothing a test can read differs: the verdicts are the same either way.
- **The plan of §2's read over rows.** It is read over a ledger of some tens of thousands of rows; the probe's over 5,000,000 was the same.
- **How many statements of `RedundancyResolution` sort where the run's own index is usable**, in `EveryStatementThatSortsIsRecordedTest`: ADR-221 says why. `RedundancyResolutionBoundsItsCandidatesTest` holds it for the statements one resolution issues.
- **The free-space figure**, and that a database built under ADR-221 is rebuilt once.

## What this record does not measure

- Anything on a spinning disk, on `H:`, on Linux, or with the file cache empty.
- The heap of the forms decided: 32 pages of 1,000 ids and a batch of 1,000 are stated from the code, not measured.
- The by-bucket form alone where a bucket's pairs share all sixteen bands, and a whole pass above 5,300 occurrences with the forms decided.
- The build of §3's index for a run among other runs' rows, or over more than 5,000,000 rows.
- `RedundancyResolution` itself, its cache's evictions, or the application's pool: the probe is its own code on one plain connection.
- Stage 4a's read with the clause, beyond its plan.
- An occurrence of 100,000 shingles or more as a candidate container, which ADR-220 left unmeasured too.

## What this does not decide

- **Stage 4b's components and their profiles, and the shingle-set cache**: held as ADR-220 §9 has them.
- **Whether an invocation should check the drive's free space before stage 4b builds**, which ADR-218 and ADR-221 left open.
- **Whether containment retrieval should refuse to run without its run's index** in the place of reading every row of the run 32 times.
- **Whether an earlier run's rows are ever removed**: [#481](https://github.com/algernon28/vespera/issues/481).
