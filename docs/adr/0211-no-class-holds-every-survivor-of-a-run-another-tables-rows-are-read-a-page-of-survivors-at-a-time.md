# ADR-211 — No class holds every survivor of a run: another table's rows are read a page of survivors at a time, and no page of a walk sorts the walk

- **Date**: 2026-10-08
- **Status**: accepted
- **Amends**: [ADR-209](0209-the-ledger-is-four-records-behind-one-type-no-capability-module-names-spring-batch-and-a-tables-sql-is-its-owners.md) §2, the paragraph *"The sets that are still held, and what they depart from"*, and the Consequence *"Six places still hold every survivor of a run at once"*. Once the commit that builds `src/main` lands, none of the six holds a set of the survivors (§1). The item of ADR-209's *What this does not decide* that owed this record is answered here. The sentence of ADR-060's banner that ADR-209 wrote, *"Six callers still build a set of every survivor's id themselves"*, stops holding with it.
- **Amends**: [ADR-193](0193-a-statement-sqlite-counts-reports-how-far-it-has-gone-and-one-it-cannot-count-says-how-long-it-took.md) §1, by a third form beside counted and timed, *a read made a page of survivors at a time* (§9); §3's table, whose rows for stage 3's shingle rows, `ConfidenceDistribution`'s extraction metrics and 5b's corpus survivors' extraction metrics lose their r; §6, whose rows for `DocumentFrequency.drainSurvivors`, `ConfidenceDistribution.drainSurvivors`, 5b's drain of the survivors and 5f's drain are struck, whose row for 5c's and 5d's drain becomes a timed count, and whose row for stage 3's read of `shingle` becomes timed, that statement now sorting (§3); and §7's enums, which lose `ExtractionStatement.SURVIVORS`, `SimilarityStatement.FREQUENCY_SURVIVORS` and `EmbeddingStatement.CORPUS_SURVIVORS`, and gain `EmbeddingStatement.CORPUS_METRICS_AGAIN` (§4). [ADR-204](0204-every-line-of-adr-193s-part-b-is-written-out-and-its-table-is-read-again-against-the-code.md) §3 and §4, in the same lines and constants.
- **Amends**: [ADR-192](0192-every-loop-in-every-stage-reports-its-progress-and-no-counter-floods-the-log.md) §4 and §5 in three counters: stage 3's `frequency rows` is struck, there being no loop over the distinct hashes (§3); stage 3 gains a running counter of the shingled occurrences it checks (§3); and 5f's counter of the keys it reads (ADR-206's renaming of *files hashed*) is one for each partition, where there was one over every partition's members summed (§5).
- **Amends**: [ADR-200](0200-stage-1-reads-survivors-by-size-and-holds-one-size-at-a-time.md)'s Consequence *"Other stages that drain survivors into a `Set` are not touched. `SeedCorpusComparison` and `ConfidenceDistribution` do, each for a stated reason of its own."* None does after this record.
- **Amends**: [ADR-191](0191-stage-3-says-how-many-shingle-rows-it-is-about-to-read-and-how-long-measuring-them-took.md) in what its read is: no longer a statement that hands every shingle row to Java, but a grouping that SQLite makes of them (§3). Its two lines stand word for word (§9). The progress lines ADR-193 put between them go, the statement now sorting.
- **Keeps**: [ADR-060](0060-survivors-is-a-ledger-owned-item-reader-not-a-view.md)'s *"never a materialized `List` of what could be a million-plus ids"*, now of every caller and not only of what `ledger` hands out. ADR-209 §3: no statement names a table its module does not own. ADR-193 §2's rule that a counted statement runs on the one connection it is handed and is never handed back to a template inside its callback, which is why the merge below is made a page at a time (§2). [ADR-156](0156-a-runs-survivors-are-read-through-its-upstream-runs-and-a-verdict-under-any-other-run-stays-recorded-and-removes-nothing.md): which runs a run's survivors answer to. [ADR-045](0045-clustering-runs-within-each-seed-partition.md) and [ADR-085](0085-vectors-live-in-sqlite-and-the-pairwise-matrix-is-never-materialised.md): a partition is clustered whole. [ADR-086](0086-seed-corpus-mismatch-is-measured-before-the-model-gate-and-reported-never-enforced.md): exact medians and quartiles, which §4 keeps exact. [ADR-074](0074-stage-3-measures-shingle-document-frequency-a-boilerplate-floor-ships-unset.md)'s omission rule: a hash seen in fewer than two surviving occurrences earns no row.
- **Rests on**: [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) and [ADR-048](0048-walk-and-run-identity.md) (which run ids move, §11); the operator's framing of #456, that Vespera has to work on any archive, so the bound is ADR-060's whatever the size and no archive's size is asked for; the operator's answers of 2026-10-08 to the three questions an earlier draft of this record put, that stage 3's map of distinct hashes and the comparison's corpus rows are fixed here, decided by measurement, and that stage 2's resume set and any wider survey are [#458](https://github.com/algernon28/vespera/issues/458)'s; and throwaway probes over synthetic ledgers built from the shipped `schema.sql` (Measured). No archive and no working directory was opened for this record (ADR-196).
- **Settles** [#456](https://github.com/algernon28/vespera/issues/456).

## Context

ADR-209 §2 counted six classes that put the id of every survivor of a run into a set, and left them the size they were. Read at `4b99a03`, this is what each does with its set, and what else it holds that grows with the corpus:

| Class | Survivors of | What the set is for | Held beside it |
| --- | --- | --- | --- |
| `extraction.ConfidenceDistribution` | stage 2's run | every `extraction_metric` row of the run is read in one statement and kept only if its occurrence is in the set | a `List<Double>` of one entry for every row read, the ruled-out ones as `null` |
| `similarity.DocumentFrequency` | stage 2's run | every `shingle` row of the run is read in one statement and counted only if its occurrence is in the set | one map entry for each distinct hash, holding a set of the occurrences it was seen in; and, for each granularity, a set of every shingled survivor. The map grows with the distinct shingles of the corpus, far more than its survivors |
| `embedding.SeedCorpusComparison` | the measurement run | every `extraction_metric` row of stage 2's run, through `MeasuredForms.each`, kept only if its occurrence is in the set | a `MetricRow` for every corpus survivor with a row, kept for the four medians and quartiles |
| `pipeline.ClusteringTasklet` | the scoring run | each partition's members, read from `relevance_score`, are kept only if in the set | the members of every partition at once, which after the filter are the scoring run's survivors |
| `pipeline.EmbeddingScoringTasklet` | the measurement run | **nothing is tested against it**: the step goes through the set, survivor by survivor, and takes its size for the counter | — |
| `pipeline.RelevanceScoringTasklet` | the measurement run | **nothing is tested against it**, the same | — |

The ticket's premise holds for four of the six. Two only go through the survivors, and for them the question of which candidate replaces the set does not arise (§6). An earlier draft of this record settled the sets and left `DocumentFrequency`'s map and `SeedCorpusComparison`'s rows to the operator, as being larger than a set of survivors and not one; the operator put both in this ticket (Rests on).

**One more thing was found while measuring, and it costs more than any of the sets.** The page statement of `Verdicts.survivors`, and of `Occurrences.occurrencesOf`, is planned by SQLite as a search of `file_occurrence_by_walk_and_size` on `walk_id` followed by `USE TEMP B-TREE FOR ORDER BY`: every page goes through the whole walk, asks the anti-join of every occurrence in it, sorts what survives, and keeps 1,000. So a whole read of the survivors grows as the square of the walk (Measured). `SurvivorsBySizeTest` pins that the read by size sorts nothing; no test pinned it of the two reads in id order. Every candidate below reads the survivors, as the sets do today, so the cost of each is mostly this one until it is fixed (§8).

## Measured

Throwaway probes outside the repository, on 2026-10-08: Java 26 and the sqlite-jdbc the pom carries (3.53.2.1), on a solid-state disk with the file cache warm, in write-ahead log mode as the application opens the database. Each ledger was built from the shipped `schema.sql`: one walk; a stage-1 run and a stage-2 run upstream of it; every tenth occurrence `broken` under stage 1 and every tenth, five on, `degenerate-output` under stage 2, so 80% survive stage 2; an `extraction_metric` row under stage 2 for every occurrence stage 1 kept; and a fixed number of `shingle` rows for each, a tenth of them drawn from a small pool so that hashes recur across occurrences, the rest seen once.

### The sets

Three ways of restricting another table's rows to the survivors were timed over the same rows:

- **A**, today's: every survivor's id into a `HashSet`, then one statement over the table's rows of the run, each tested against the set;
- **B**, the ticket's first candidate: the table read by pages of 1,000 rows by rowid, the distinct occurrences of each page asked of the ledger in one statement;
- **C**, the ticket's second, made a page at a time: the survivors read a page of 1,000, and each page's rows of the table read in one statement keyed by the page's occurrences.

**With the page statement as shipped**, which sorts the walk on every page:

| Ledger | Read | A | B | C |
| --- | --- | ---: | ---: | ---: |
| 100,000 occurrences, 80,000 survivors, 4,500,000 shingle rows | metrics | 1.29–1.35 s | 0.48–0.57 s | 1.33–1.41 s |
| the same | shingles | 2.95–3.68 s | 16.6–17.6 s | 2.95–3.06 s |
| 300,000 occurrences, 240,000 survivors, 2,700,000 shingle rows | metrics | 12.98 s | 4.12 s | 13.37 s |
| the same | shingles | 13.98 s | 33.27 s | 14.17 s |

Tripling the walk multiplied today's read of the survivors by about ten. B's ask was planned as the same walk search, so it went through the walk once for each page it asked about.

**With the two statements planned by the integer primary key** (§8), the same ledgers:

| Ledger | Read | A | B | C | Statements, A / B / C | Most ids held, A / B / C |
| --- | --- | ---: | ---: | ---: | --- | --- |
| 100,000 | metrics | 0.08–0.09 s | 0.13–0.15 s | 0.09–0.13 s | 82 / 181 / 161 | 80,000 / 1,000 / 1,000 |
| 100,000 | shingles | 1.96–2.02 s | 2.43–2.75 s | 1.64–1.81 s | 82 / 9,001 / 161 | 80,000 / 20 / 1,000 |
| 300,000 | metrics | 0.24 s | 0.37 s | 0.31 s | 242 / 541 / 481 | 240,000 / 1,000 / 1,000 |
| 300,000 | shingles | 1.33 s | 1.60 s | 1.07 s | 242 / 5,401 / 481 | 240,000 / 100 / 1,000 |

- **The answers were the same** in every run, each grade's count and every hash's two counts.
- **The plans**, from `EXPLAIN QUERY PLAN`: C's lookup of metric rows searches `sqlite_autoindex_extraction_metric_1 (occurrence_id=? AND run_id=?)`. The ledger's ask, written as the page statement is, with `walk_id` compared as a value and not as an index column, searches `file_occurrence` by `INTEGER PRIMARY KEY (rowid=?)`.
- **The last page of a walk with a larger walk after it**: with 1,000,000 occurrences of a later walk recorded after the 300,000, the last page of the first walk's survivors, planned by the primary key, took 0.031 s, going on through the later walk's rows once to find it has no more. The first page took 0.001 s against 0.098 s as shipped.
- **Heap.** A `HashSet` of ids held 66.8 MB for 1,000,000 survivors, about 67 to 77 bytes an id at every size measured.

### Stage 3's distinct hashes

The same ledger of 100,000 occurrences, 50 shingle rows each, 4,500,000 rows of 3,602,000 distinct hashes, 2,000 of them seen in two or more survivors; and one of 20,000 occurrences, 500 rows each, 9,000,000 rows of 7,220,000 distinct hashes, 20,000 of them seen twice or more. Four ways of writing stage 3's two tables, each in one transaction as the step is, each checked equal to the others row for row:

- **M**: the survivors a page at a time (C), their rows in occurrence order, one map entry a distinct hash counted by the last occurrence seen, the rows of two or more written at the end. The best that holds the map.
- **T1**: a tally kept in SQLite, in `similarity`'s own `shingle_document_frequency` and `shingle_corpus_size`: for each page of survivors one `INSERT … SELECT … GROUP BY` of that page's rows, upserted onto the run's rows, and every hash seen once deleted at the end.
- **T2**: the same tally, each page's rows counted in Java and upserted in a batch.
- **T6**: one grouping by SQLite of every shingle row of the run, keeping the hashes two or more occurrences carry, and one of each granularity's shingled occurrences; then the run's shingled occurrences read a page of 1,000 at a time, each page asked of the ledger, and what those that do not survive contributed taken off (§3).

| Ledger | M | T1 | T2 | T6 |
| --- | ---: | ---: | ---: | ---: |
| 100,000 occurrences, 4.5M rows | 2.47–2.88 s, map 527 MB | 35.3–38.8 s, of which 20.2–23.4 s the delete | 79.8 s | 5.87 s: grouping 3.16 s, corpus size 1.13 s, take-off 1.58 s |
| 20,000 occurrences, 9M rows | 4.91–5.31 s, map 1,053 MB | 27.2–28.4 s | 181.9 s | 12.03 s: grouping 6.57 s, corpus size 2.57 s, take-off 2.87 s |
| write-ahead log at commit | 0 | 220 MB, 442 MB | 220 MB, 446 MB | 0–4 MB |
| file growth | 0 | 219 MB, 440 MB | (reused T1's freed pages) | 1–5 MB |

- **M holds about 147 bytes a distinct hash as read** (527 MB for 3.6M, 1,053 MB for 7.2M), and grows with the corpus's distinct shingles, whatever the survivors.
- **T1 and T2 write a row for every distinct hash and delete nearly all of them**: seven to fourteen times M's time, a write-ahead log the size of the corpus's distinct hashes in one transaction, and a file left larger by as much in free pages.
- **T6 takes two to two and a half times M's time and holds a page.** It writes only the rows that stay. Its first form read the shingled occurrences by a page statement that SQLite planned on `shingle_by_run_id` with `USE TEMP B-TREE FOR DISTINCT`, sorting all the run's rows for every page: 34 s and 15 s for the take-off. Read by `shingle_by_occurrence`, with `run_id` compared as a value, the take-off took 0.8 s and 1.4 s with an `UPDATE … FROM`, and 1.6 s and 2.9 s as the upsert of negative counts with a delete keyed by the page's hashes that §3 decides, the times in the table. The `UPDATE … FROM` is the quicker here, and is refused for its plan: SQLite searches the run's whole frequency table for every page, `SEARCH f USING INDEX sqlite_autoindex_shingle_document_frequency_1 (run_id=?)`, which these ledgers keep small (2,000 and 20,000 rows) and a corpus need not. The upsert and the delete are each a lookup by key for every hash of the page.
- **The plans of T6**: the grouping searches `shingle_by_run_id (run_id=?)`, `USE TEMP B-TREE FOR GROUP BY` and `FOR count(DISTINCT)`; a page of shingled occurrences, `SEARCH shingle USING COVERING INDEX shingle_by_occurrence (occurrence_id>?)`; the keyed delete, `sqlite_autoindex_shingle_document_frequency_1 (run_id=? AND shingle_parameter_identity=? AND shingle_hash=?)` over `shingle_by_occurrence (occurrence_id=? AND run_id=?)`. The grouping's sort goes to SQLite's temporary storage, outside the Java heap; how large that is was not measured.

### The comparison's quartiles

A ledger of metric rows alone: 80% of the walk surviving the measurement run; word counts spread over four orders of magnitude, one in fifty with none; a third with no page count; the two counts the ratios are taken from spread up to a fifth of the words. Two ways of finding the corpus side's four quartiles, checked equal to the last bit:

- **A**, today's: every value held, sorted, and `Quartiles.of` taken;
- **R**: exact selection over re-reads (§4).

| Survivors | A | R | Reads | Most held by R |
| --- | ---: | ---: | --- | --- |
| 2,400 | 0.01 s | 0.01 s | 2 | 262,144 counters, 4,000 values |
| 240,000 | 0.39–0.48 s, 870,268 values held | 0.89–0.93 s | 3, about 0.30 s each | 1,048,576 counters, 4,000 values |
| 800,000 | 1.31–1.57 s, 2,901,097 values held | 2.98–3.06 s | 3, about 1.0 s each | 1,245,184 counters, 4,000 values |

R's answers were equal to A's for every quartile of every signal at every size. Its counters are ints, at most about 5 MB, the same at every size.

**Not measured**: any of this on a spinning disk, on `H:`, or with the file cache emptied; any archive. The probes measure plans and the relative cost of the shapes, not what stage 3 or stage 5 will take on the operator's database.

## Decision

### 1. Per site

| Site | Decision | Why, from Measured |
| --- | --- | --- |
| `ConfidenceDistribution` | **C**: a page of stage 2's survivors at a time, and that page's `extraction_metric` rows by key (§2) | within a tenth of a second of A, and cheaper than B, at both sizes, holding a page |
| `DocumentFrequency` | counted in the database, by **one grouping of the run's rows, the ruled-out taken off by B**: the run's shingled occurrences a page at a time, asked of `Verdicts` (§3) | the only way measured that holds neither the map nor a tally of every hash: two to two and a half times M's time, a page held, no growth of log or file |
| `SeedCorpusComparison` | **C**, through a new `MeasuredForms` method that reads the rows of named occurrences (§2), and **exact quartiles over at most four reads** (§4) | twice A's time at 800,000 survivors, three reads of about a second each, a fixed number of counters |
| `ClusteringTasklet` | **B**: each partition's members, already in hand, asked of `Verdicts`; one partition at a time (§5) | C would read every survivor once for each partition; B asks only about members, a page of them at a time |
| `EmbeddingScoringTasklet` | **neither**: it goes through `Verdicts.survivors` itself, and counts first (§6) | nothing is tested against a set |
| `RelevanceScoringTasklet` | **neither**, the same | the same |

### 2. Candidate C, a page at a time

**The shape.** The caller goes through `Verdicts.survivors(run)` and gathers its ids into pages of at most 1,000, the ledger's own page size, in the order they come. For each page it reads that page's rows of its own table in one statement:

- `ConfidenceDistribution`: `SELECT occurrence_id, mean_score FROM extraction_metric WHERE run_id = ? AND occurrence_id IN (?, …)`, the run being stage 2's, and adds each score to its grade as it is read.
- `SeedCorpusComparison`: `MeasuredForms` gains `eachOf(RunId, Collection<OccurrenceId>, Row)`, which hands each row of the named occurrences under the run to the callback. `pipeline` passes `extraction.ExtractionMetrics.eachMeasuredFormOf(RunId, Collection<OccurrenceId>, MeasuredFormRow)`, new, whose statement is the seven columns `eachMeasuredForm` reads with `AND occurrence_id IN (?, …)`. `embedding` names no `extraction` table (ADR-209 §3.2's arrangement, one method more). `each` stays, for the seeds' rows under the measurement run, which are the seed set and are read as before.

No statement names more than 1,000 occurrences, so none comes near SQLite's limit on a statement's variables. A page with no survivor makes no statement.

**Why a page at a time, and not one statement beside a cursor.** The candidate as the ticket words it reads the other table in occurrence order alongside the survivors and merges the two. Done literally, that is two statements open at once: the read of the other table, and the ledger's page statements, which go through the template. ADR-193 §2 forbids exactly that inside a counted read: outside a transaction it borrows a second connection, and under the test profile's pool of one it waits on a connection its own caller holds. So the merge is made the other way round: the survivors' page is read first and let go of as a statement, then the other table's rows for that page. Each statement finishes before the next begins, and the merge is the same merge.

**Each module keeps its statement.** The lookups are written by the module that owns the table, as every statement over that table is (ADR-209 §3). The survivors are asked of `ledger`. Nothing passes a connection, a table's name or a temp table across a module.

### 3. Stage 3's document frequency is counted in the database

`DocumentFrequency.measure` holds no map, no set and no list that grows with the corpus. Its statements are `similarity`'s, over `similarity`'s three tables, and the survivors are asked of `ledger`:

1. **The grouping.** `INSERT INTO shingle_document_frequency (run_id, shingle_parameter_identity, shingle_hash, document_count, total_count) SELECT ?, shingle_parameter_identity, shingle_hash, COUNT(DISTINCT occurrence_id), COUNT(*) FROM shingle WHERE run_id = ? GROUP BY shingle_parameter_identity, shingle_hash HAVING COUNT(DISTINCT occurrence_id) >= 2`, under the stage-3 run and over stage 2's.
2. **The corpus size.** `INSERT INTO shingle_corpus_size (run_id, shingle_parameter_identity, shingled_document_count) SELECT ?, shingle_parameter_identity, COUNT(DISTINCT occurrence_id) FROM shingle WHERE run_id = ? GROUP BY shingle_parameter_identity`.
3. **The take-off**, a page of the run's shingled occurrences at a time: `SELECT DISTINCT occurrence_id FROM shingle WHERE +run_id = ? AND occurrence_id > ? ORDER BY occurrence_id LIMIT 1000`, the keyset after the last id of the page before, planned by `shingle_by_occurrence` and sorting nothing (the unary `+` keeps `shingle_by_run_id` from being chosen, as §8 does for the walk). For each page, `ledger.verdicts().survivingAmong(stage2Run, page)` (§5); for the occurrences of the page it does not answer, if any, three statements, each naming them in `occurrence_id IN (?, …)`:
   - `INSERT INTO shingle_document_frequency (run_id, shingle_parameter_identity, shingle_hash, document_count, total_count) SELECT ?, shingle_parameter_identity, shingle_hash, -COUNT(DISTINCT occurrence_id), -COUNT(*) FROM shingle WHERE run_id = ? AND occurrence_id IN (…) GROUP BY shingle_parameter_identity, shingle_hash ON CONFLICT (run_id, shingle_parameter_identity, shingle_hash) DO UPDATE SET document_count = document_count + excluded.document_count, total_count = total_count + excluded.total_count`;
   - `DELETE FROM shingle_document_frequency WHERE run_id = ? AND document_count < 2 AND (shingle_parameter_identity, shingle_hash) IN (SELECT shingle_parameter_identity, shingle_hash FROM shingle WHERE run_id = ? AND occurrence_id IN (…))`;
   - `UPDATE shingle_corpus_size AS s SET shingled_document_count = s.shingled_document_count - x.d FROM (SELECT shingle_parameter_identity AS p, COUNT(DISTINCT occurrence_id) AS d FROM shingle WHERE run_id = ? AND occurrence_id IN (…) GROUP BY 1) AS x WHERE s.run_id = ? AND s.shingle_parameter_identity = x.p`.

   After each page it tells its progress `shingledOccurrencesChecked(int)`, with the page's number of occurrences.

**Why the counts are the survivors'.** The grouping counts every shingled occurrence of the run; each occurrence is in exactly one page; what a ruled-out one contributed is taken off once. A hash the grouping left out was carried by fewer than two occurrences of any kind, so by fewer than two survivors. A hash brought below two by the take-off is deleted in the same page, and a hash the upsert met for the first time, with a negative count, is deleted by the same statement. The rows that stay are ADR-074's: a hash seen in two or more surviving occurrences, with both its counts.

**What it holds and writes.** A page of 1,000 ids and the ledger's answer about it. It writes only the rows that stay, and the take-off's few, so the write-ahead log and the file stay the size they were (Measured). The grouping's sort is SQLite's, in its temporary storage, not in the heap.

**No new table and no DDL.** `shingle_document_frequency` and `shingle_corpus_size` already have the primary keys the upsert names. No schema version moves, and an existing working directory opens (§11). `shingleRowsUpTo` stays: it is the total ADR-191's line states.

**What stage 3 no longer does.** It reads no survivor of stage 2's run as such: the survivors are asked about only as the run's shingled occurrences come, a page at a time. It brings no shingle row into Java.

### 4. The comparison's quartiles are exact over at most four reads

The corpus side is read through `MeasuredForms.eachOf`, a page of the measurement run's survivors at a time (§2), as many times as its quartiles need and at most four. The seeds' side is the seed set, and is held and sorted as today.

**The first read** gives, for each of the four signals (word count, page count, vowelless-word ratio, single-character-word ratio, each skipped where it is undefined, as `MetricRow` has it): how many values there are; the least and the greatest; the first 1,000 values; and, in 65,536 counters, how many values share each value of their key's leading 16 bits. It also counts the corpus documents and the language and provenance categories, which need nothing more. The key of a value is `Double.doubleToRawLongBits` of it: the four signals are never negative, so their keys order as they do.

**Then each signal is answered.**

- **With no value**, it has no quartiles, as today.
- **With 1,000 values or fewer**, from the values kept, by `Quartiles.of`.
- **With its least and greatest equal**, every quartile is that value.
- **Otherwise**, the ranks `Quartiles.of` takes are known from the count: the median's one or two, and the same of each half, six at most. Each rank is narrowed 16 bits a read: from the counters of the read just made it learns 16 more bits of its key and how many values lie below them. A rank whose candidates, the values sharing the bits found so far, are 1,000 or fewer has them gathered on the next read, sorted, and picked. A rank whose candidates all share one key is answered. A rank has its whole key after its fourth read, so there is never a fifth.

Ranks of all four signals are narrowed in the same reads. A later read holds 65,536 counters for each rank being narrowed and at most 1,000 values for each rank being gathered, so at most about 1.6 million counters and 24,000 values, whatever the survivors. The quartiles are `Quartiles.of`'s own, the mean of two middle values where it takes two, so the figures and the statements on the page are the same as today's (Measured).

**Each read is reported as a read of its own** (§9): the first as `CORPUS_METRICS`, every later one as `EmbeddingStatement.CORPUS_METRICS_AGAIN`, new, declared straight after it, each started with the span of stage 2's rows, told its rows after each page, and ended. The seeds' `SEED_METRICS` comes after the last of them, as now.

### 5. Candidate B at 5f, and one partition at a time

**`Verdicts.survivingAmong(RunId run, Collection<OccurrenceId> occurrences)`** answers a `Set<OccurrenceId>`: those of the given occurrences that carry no blocking verdict under `run` or any run upstream of it (ADR-156), and that belong to `run`'s walk. It asks in statements of at most 1,000 occurrences each, and asks nothing for an empty collection, not even the runs in scope. Its statement is the survivors' anti-join with `id IN (?, …)` in place of the keyset, planned by the integer primary key (§8). It is `ledger`'s, beside `survivors` and `survivorCount`. Stage 3 asks it too (§3).

**`ClusteringTasklet` holds one partition at a time.** For each partition, in the order `Clustering.partitions` gives, it reads the members (`Clustering.membersOf`, timed as now), keeps those `survivingAmong(scoring, members)` answers, in the order `membersOf` gave them, reads their keys and clusters them, and lets the partition go before it reads the next. It reads no survivor of the scoring run otherwise.

**So its counter of the keys it reads is one for each partition**, `Stage 5f (clustering, cache keys read, partition <P> of <M>)`, over that partition's surviving members, opened when the partition's members are known and before its first key is read. The counter over every partition's members summed needed every partition's members before the first was clustered, and those are the scoring run's survivors. It takes the shape 5f's counter of pairs of blocks already has. A partition left with no member opens no counter, as it opens none over blocks, and is counted done as now. **The members of a partition are now read just before that partition is clustered**, where they were all read first, so the timed lines `reading the members of partition <P> of <M>` fall each before its partition's work and its group sizes.

### 6. 5c and 5d go through the read

Each asks `Verdicts.survivorCount(measurementRun)` for its counter's total and its starting line, then goes through `Verdicts.survivors(measurementRun)` and works on each survivor as it comes. Neither writes a verdict, so nothing it writes changes a page still to come. **The order changes**, from a hash set's to ascending occurrence id; nothing reads either step's rows by the order they were written. The seed set each holds stays: it is the operator's seed folder, not the corpus, and 5d holds the seeds' vectors resident on purpose (ADR-085).

### 7. What else goes from the six

- **`ConfidenceDistribution`'s list of scores.** Each score is added to its grade as it is read; nothing is kept per row.
- **`DocumentFrequency`'s map of distinct hashes, its sets of occurrences per hash and its sets per granularity** (§3).
- **`SeedCorpusComparison`'s rows of the corpus side** (§4).
- **`ClusteringTasklet`'s members of every partition** (§5).

### 8. The two reads in id order sort nothing

**Each page of `Verdicts.survivors` and of `Occurrences.occurrencesOf` is planned as a search of `file_occurrence` by its integer primary key, `rowid>?`, and names no temp B-tree.** The rows and their order are unchanged. Written as today, SQLite prefers the index on `walk_id` and sorts the walk on every page; compared as a value, `+walk_id`, the walk no longer chooses the index, and the page goes forward from the last id read until it has 1,000. `survivingAmong` is written the same way, so each ask is a lookup by key.

**What it costs instead.** The last page of a walk with later walks recorded after it goes on through their rows once, testing the walk of each, to learn it has no more: 0.031 s for 1,000,000 later rows (Measured), once a read. `ledger` is in no stage's implementation version, so this moves no run id of its own.

### 9. What the operator reads

- **Three timed lines are struck**: stage 3's two, `is reading stage 2's survivors for the shingle frequencies` and `for the confidence distribution`, and 5b's `is reading the corpus survivors`, each with its line after. There is no statement left for them to time; the pages are read inside the loop that reports, which ADR-199 §3 already counts as that loop's.
- **Stage 3's shingle rows keep ADR-191's line before, word for word**, `<N>` being the span of the run's rowids as now, and its line after the measurement. **Nothing comes between them any more.** The grouping and the corpus size sort in temp B-trees, and ADR-193 §1 makes a statement that sorts a timed one: SQLite gives no count during its sort. So the progress lines `Stage 3 (content census, reading shingle rows): about <X>% of <N> rows` that ADR-193 wrote between the two go. `SHINGLE_ROWS` is started once before the grouping, with the span, and ended once after the corpus size, and declares no steps a row. Stage 3's counter `Stage 3 (content census, frequency rows)` goes with the loop it counted. A running counter, `Stage 3 (content census, shingled documents checked): <n> so far`, counts the take-off's occurrences, on ADR-192's running cadence, its first line at the thousandth.
- **The reads of the extraction metrics keep every line they had.** `Stage 3 (content census) is reading the extraction metrics, over up to <N> rows` and 5b's `… is reading the corpus survivors' extraction metrics, over up to <N> rows`, and their lines after, stand as ADR-204 §3 wrote them; between them the same progress line, `<label>: about <X>% of <N> rows`, on ADR-193 §5's cadence. `<N>` is the span of the run's rowids in the table, as now. **What changes is what `<X>` is taken from: the rows read, counted by the module as it reads them, and not SQLite's steps.** That is the third form ADR-193 §1 gains: a read made a page of survivors at a time is reported with the counted form's lines, its total the span, its progress the rows read so far. It goes through only the survivors' rows, so it can end below 100% where the run holds rows of ruled-out occurrences; the span was always a bound, and the estimate falls behind, never ahead (ADR-193 §3).
- **5b's later reads of the corpus side** write the same three kinds of line under their own words: `Stage 5b (seed/corpus comparison) is reading the corpus survivors' extraction metrics again, over up to <N> rows`, `… read the corpus survivors' extraction metrics again in <S> s`, and between them `Stage 5b (seed/corpus comparison, reading corpus metrics again): about <X>% of <N> rows`. A corpus side of 1,000 documents or fewer, or whose signals each take one value, is read once and writes no such line.
- **The callback.** `ExtractionStatementProgress` and `EmbeddingStatementProgress` each gain `default void rowsRead(<Module>Statement statement, long rows) {}`, called once after each page's rows have been read, with the rows read so far in that read, the earlier pages' included. `statementStarting` is called once before the first page's rows are read, with the span, and `statementEnded` once after the last, on every path but one that throws, as ADR-193 §7 has it. `stepsTaken` is not called for these reads. `EXTRACTION_METRICS`, `CORPUS_METRICS` and `CORPUS_METRICS_AGAIN` declare no steps a row.
- **When each read is started.** `EXTRACTION_METRICS` and `SHINGLE_ROWS` are started whether or not there is a survivor, as today, with an empty total where the run holds no row; with no survivor nothing comes between start and end. `CORPUS_METRICS` is started only once the first page of survivors has come back with one, as today it is not issued over an empty population.
- **5c and 5d** write `Stage 5c (embedding scoring) is counting the corpus survivors`, and `… counted the corpus survivors in <S> s`, where they wrote `is reading` and `read`; 5d the same under its own name. ADR-199 §2's wording for a timed survivor count. Their counters and starting lines are unchanged.
- **5f** writes no line about the corpus survivors, and its keys counter is one for each partition (§5).

### 10. What is still held, and why

The ticket's acceptance asks that any class still holding a collection as large as a run's survivors have a record saying why. After this record, in the six classes:

- **The seed set**, in `SeedCorpusComparison`, 5c and 5d. Not survivors: the operator's seed folder.
- **The largest partition, at 5f.** A partition is clustered whole: every pair of its members is compared, and `Clustering` addresses its blocks by the members' keys (ADR-045, ADR-085). So 5f holds one partition's members and their keys, and a partition can be as large as the scoring run's survivors where one seed wins nearly all. The bound is the largest partition, as ADR-200's is the largest set of one size; it is no longer every partition at once.

Nothing else in the six grows with the corpus. Outside them, stage 2's resume set is [#458](https://github.com/algernon28/vespera/issues/458)'s (What this does not decide).

### 11. Which run ids move

**Stages 2 to 6b, once**, on the first build that ships this. Widening the record to §3 and §4 touches modules this change already touched, so **nothing more moves than the narrower draft moved**. `StageModules` names the modules each stage's implementation version spans (ADR-058):

| Stage | Modules named | Touched here |
| --- | --- | --- |
| 1 | `corpus` | nothing |
| 2 | `extraction`, `similarity` | `ConfidenceDistribution`, `ExtractionMetrics`, `ExtractionStatement`, `ExtractionStatementProgress`; `DocumentFrequency`, `FrequencyProgress`, `SimilarityStatement` |
| 3, 4 | `similarity`, `extraction`, `pipeline` | the same, and `pipeline` |
| 5, both runs | `embedding`, `extraction`, `pipeline` | `SeedCorpusComparison`, `MeasuredForms`, `EmbeddingStatement`, `EmbeddingStatementProgress`, and the same |
| 6a, 6b | `synthesis`, `extraction`, `embedding`, `pipeline` | the same; nothing in `synthesis` |

Stage 2's code is not what changes, but its id moves all the same, because the stage-3 code it shares a module with does. So **stage 2 does its work again under its new id over an existing working directory**: it reads and hashes each survivor to look up its conversion, finds it in the extraction cache and converts nothing again, as after ADR-207, ADR-209 and ADR-210. `ledger` is in no stage's list, so §5's and §8's changes to it move nothing on their own. **Stage 1's id does not move.**

**What does not move**: no DDL, so no schema version, and an existing working directory opens; no cache key; no `run.stage` and no `finished_step.step` value. `StageModules` is not edited, so `RunIdentityGoldenTest` is not. The verdicts under the old ids stay recorded and remove nothing (ADR-156). An arrangement is minted again, and `arrangementApproved` must name the new one.

## Alternatives refused

- **Candidate B for the reads of metrics.** Measured: no cheaper, and over shingles ten times the statements and the slowest of the three.
- **One statement over the other table beside the ledger's cursor.** Two statements open at once on one connection, which ADR-193 §2 forbids inside a counted read and the pool of one turns into a wait on itself (§2).
- **The survivors written into a temporary table and joined in the owning module's SQL.** That module's statement would then name a table of `ledger`'s, and a temporary one belongs to one connection, so it would hold only inside a transaction.
- **Counting the paged reads by SQLite's steps across their pages.** Each page is its own statement with its own handler, and a page of extraction metrics takes about 12,000 steps, under one callback, so the read would never report. The module reads every row in Java and knows the count exactly.
- **5f's counter kept summed, by reading every partition's members twice.** Twice the statements and twice each partition's timed lines, to keep a total over a collection this record exists to stop holding.
- **Stage 3's map kept, counted by the last occurrence seen (M).** The fastest measured, and it holds about 147 bytes for every distinct hash of the corpus: 1,053 MB for 9,000,000 shingle rows. The operator's framing is a bound whatever the size, and this has none.
- **A tally of every hash kept in SQLite a page at a time (T1, T2).** Bounded in the heap, and measured at seven to fourteen times M and slower still, with a write-ahead log and a file growth the size of the corpus's distinct hashes, nearly all of them written to be deleted.
- **A table of `similarity`'s own for the tally.** Not needed: the two tables the counts end in have the keys the upsert names, and T6 writes only rows that stay. A new table would bring DDL, a schema version and an existing working directory's upgrade for nothing measured.
- **The shingled occurrences read with `run_id` as an index column.** Measured: every page sorted all the run's rows, 15 to 34 s for the take-off where §3's form took 1.6 to 2.9 s.
- **The take-off as an `UPDATE … FROM`.** Measured at half the upsert's time on these ledgers, and planned as a search of the run's whole frequency table for every page: a cost that grows with the hashes seen twice or more, once a page, which the upsert's keyed form does not have.
- **Estimated quartiles**, from a sketch or a histogram. Bounded and one read, and the figures on the page would change; the operator chose exact (Rests on).
- **The quartiles found by SQLite, by `ORDER BY … LIMIT 1 OFFSET`.** The population is the survivors, and `extraction`'s SQL cannot name them (ADR-209 §3).

## Consequences

- **None of the six holds a set of a run's survivors**, nor anything else that grows with the corpus, but the seed set and 5f's largest partition (§10).
- **Every read of the survivors and of a walk's occurrences stops going through the walk on every page** (§8): measured at a hundredth of the time at 100,000 occurrences and a fiftieth at 300,000, and the gap grows with the walk. Every stage that reads its survivors gains it, not only the six.
- **Stage 3's document frequency takes two to two and a half times what the map would, and holds a page** (Measured): 5.9 s against 2.5 to 2.9 s, and 12.0 s against 4.9 to 5.3 s, on a solid-state disk. How long it takes on `H:` is not known; today's read took half an hour there (ADR-191), and the grouping goes through the same rows and sorts them.
- **Stage 3 says nothing between ADR-191's two lines** (§9), its read now sorting, and counts the shingled documents it checks.
- **5b reads the corpus side up to four times**, three in every probe of more than 1,000 different values, each a read like the one it makes today.
- **5c and 5d go through their survivors in occurrence order.**
- **The operator sees three pairs of lines fewer, two pairs say *counting* where they said *reading*, 5b's later reads write lines of their own, stage 3's frequency-rows counter goes and a running counter comes, and 5f's keys counter is one for each partition** (§9).
- **The run ids of stages 2 to 6b move once**, with the caches warm (§11).

## Tests

**Where they are.** As ADR-210's: each test is in `src/test`, compiles against the tree at `4b99a03`, and fails there for the reason given, where the tables below give one; the edits marked *green* pass there and must still pass after. The exception is two tests parked under `docs/adr/0211/tests/`, followed by the path each takes in the repository, because each names a method this record adds and would stop the test tree compiling. `spec-implementer` moves them into `src/test` with the change. A test that implements a callback this record adds or removes, `rowsRead`, `MeasuredForms.eachOf`, `shingledOccurrencesChecked`, `toGoThrough` or `hashGoneThrough`, writes it without `@Override`, so it compiles before the change and after. A constant this record adds, `CORPUS_METRICS_AGAIN`, is named by its text.

**New, in `src/test`:**

| Test | What it pins | Red at `4b99a03` because |
| --- | --- | --- |
| `StatementLog` (fixture, not a test) | a template whose connections keep, in order, the text of every statement they prepare or run, and notes a test adds among them | — |
| `ledger.ReadsInIdOrderSortNothingTest` | each page of `survivors` and of `occurrencesOf`, over a walk of 2,500 with a later walk after it, captured as issued, is planned by `INTEGER PRIMARY KEY` and names no temp B-tree; and the rows are the survivors, in order | each page searches `file_occurrence_by_walk_and_size` and sorts in a temp B-tree |
| `extraction.ConfidenceDistributionReadsAPageOfSurvivorsAtATimeTest` | over 2,500 survivors and 50 ruled out: the reads of metric rows alternate with the pages of survivors, one after each, three of each; each names at most 1,000 occurrences; each grade counts the survivors and no ruled-out one | one read, after all three pages, naming no occurrence |
| `similarity.DocumentFrequencyIsCountedInTheDatabaseTest` | over 2,500 survivors and 50 ruled out, all shingled, the rows written interleaved and the ruled-out sharing the survivors' hashes, each twice: every frequency row's two counts and the corpus size are the survivors' alone; no statement selects shingle hashes into Java; no page of the survivors is read; one grouping over the run comes first, then three pages of shingled occurrences, each followed by one ask of the ledger; each page is planned by `shingle_by_occurrence` and sorts nothing | the survivors are read into a set and every shingle row selected into Java |
| `embedding.SeedCorpusComparisonReadsAPageOfSurvivorsAtATimeTest` | over 2,500 corpus survivors whose values are all one: the corpus side's rows are asked for through `MeasuredForms.eachOf`, once after each page of survivors, at most 1,000 at a time, in one read; `each` is asked for the measurement run alone; the corpus count is the 2,500 | `each` is asked for stage 2's run and `eachOf` never |
| `embedding.SeedCorpusComparisonQuartilesOverReReadsTest` | over 2,500 corpus survivors with spread values and 50 ruled out far above them: each corpus spread equals `Quartiles.of` over the survivors' values; the corpus side is asked for only through `eachOf`, at most 1,000 at a time, the survivors named a whole number of times between two and four; the first read is reported as `CORPUS_METRICS` and each later one as `CORPUS_METRICS_AGAIN`, started with the span, told 1,000, 2,000 and 2,500 rows, and ended; the seeds' read comes after | `each` is asked for stage 2's run, and there is no later read |

**Edited, in `src/test`:**

| Test | Edit | Red at `4b99a03` because |
| --- | --- | --- |
| `extraction.ConfidenceDistributionStatementProgressOrderTest` | no drain is reported; the read of the metrics is started with the span, given its rows after each page, and ended; over 2,500 survivors `rowsRead` reports 1,000, 2,000 and 2,500 and no steps; with no row and no survivor, start and end only; a read that throws, started and never ended | the drain is reported, and the read reports steps |
| `similarity.SimilarityStatementProgressOrderTest` | measuring reports the grouping, started with the span and ended with nothing between, and then one page of shingled occurrences checked; nothing before it | the drain is reported, the read reports steps, and the loop over hashes is announced |
| `similarity.FrequencyProgressTest` | the shingled occurrences checked are reported a page at a time, and no loop over hashes is announced; with no shingle row nothing is reported | the loop over hashes is announced |
| `embedding.EmbeddingStatementProgressOrderTest` | no drain of the corpus survivors; the corpus metrics given their rows, not steps; the seeds' reads unchanged; the two tests of a read that throws without the drain | the drain is reported, and the corpus read reports steps |
| `embedding.RecordedForms` | gains `eachOf`, picking the named occurrences from the run's rows as `extraction` reads them | *green*: a fixture, not a test; the comparison does not call `eachOf` yet |
| `pipeline.StatementStepsPerRowAreTheDeclaredOnesTest` | the reads made a page of survivors at a time, by name, declare no steps a row; `SHINGLE_ROWS` is timed; `EmbeddingStatement` has `CORPUS_METRICS_AGAIN` straight after `CORPUS_METRICS` | `CORPUS_METRICS_AGAIN` does not exist, and the reads still declare their r |
| `pipeline.StatementStepsPerRowTest` | stage 3's two reads are no longer measured, no code issuing them; 5b's seed read still is | *green* |
| `pipeline.StageFiveReportsItsProgressInvocationTest` | 5b says nothing about reading the corpus survivors; 5c and 5d count them; 5f says nothing about them and reads each partition's members before that partition's group sizes; the keys counter is one for each partition; over tens of thousands of rows of a folder nobody walked, stage 3's and 5b's reads of extraction metrics say nothing about how far they have gone, never going through those rows | the old lines are written, and both reads go through those rows |
| `pipeline.RedundancyResolutionReportsItsProgressInvocationTest` | stage 3's lines carry no read of stage 2's survivors, and stage 3 writes no frequency-rows counter | both timed reads and the counter are written |
| `pipeline.StatementProgressInvocationTest` | over 60,000 shingle rows, stage 3 still announces its read and its measurement, and writes no progress line between them | the progress lines are written |

**Parked under `docs/adr/0211/tests/`:**

- `src/test/java/io/algernon/vespera/ledger/SurvivingAmongTest.java`: `survivingAmong` answers the occurrences that survive under the run's scope, leaving out one blocked under the run, one blocked under its upstream run and one of another walk, and keeping one blocked only under a sibling run; over 2,500 occurrences no statement names more than 1,000; an empty collection makes no statement; each statement is planned by the integer primary key.
- `src/test/java/io/algernon/vespera/extraction/MeasuredFormsOfNamedOccurrencesTest.java`: `eachMeasuredFormOf` hands over the rows of the named occurrences under the run and of no other, a missing score and a missing page count told apart from zero; an empty collection makes no statement.

**Not pinned, and why.** That 5c and 5d hold no set is pinned only through their lines, the count in place of the read: seeing it directly takes more than 1,000 survivors through the whole job. 5f's filter is pinned by the existing tests of a floor that empties a partition. That no read of the corpus side takes a fifth read, and the counters a later read holds, are pinned only by the bound in the read count; the four-read limit is a property of 64-bit keys taken 16 bits at a time. What any of this costs on `H:`, and how much temporary storage stage 3's grouping uses.

## What the commit that builds `src/main` owes

- **`ledger.Verdicts`**: the page statement of `survivors` planned by the primary key (§8), with `walk_id` compared as a value; `survivingAmong(RunId, Collection<OccurrenceId>)` returning `Set<OccurrenceId>` (§5), its statements of at most 1,000 occurrences, none for an empty collection, issued through `JdbcTemplate.query(String, RowMapper, Object...)`. **`ledger.Occurrences`**: `occurrencesOf`'s page the same way. Each page still carries ` LIMIT ` as ADR-209 owes; `survivingAmong`'s statements carry none.
- **`extraction`**: `ConfidenceDistribution.measure` as §2 and §7, holding no set and no list; `ExtractionMetrics.eachMeasuredFormOf(RunId, Collection<OccurrenceId>, MeasuredFormRow)`, none for an empty collection; `ExtractionStatement.SURVIVORS` removed and `EXTRACTION_METRICS` declaring no steps a row; `ExtractionStatementProgress.rowsRead`, default empty.
- **`similarity`**: `DocumentFrequency.measure` as §3, its statements as written there, holding no map, set or list; `FrequencyProgress` losing `toGoThrough` and `hashGoneThrough` and gaining an abstract `shingledOccurrencesChecked(int occurrences)`, its `NONE` doing nothing; `SimilarityStatement.FREQUENCY_SURVIVORS` removed and `SHINGLE_ROWS` declaring no steps a row, started with `shingleRowsUpTo` before the grouping and ended after the corpus size.
- **`embedding`**: `SeedCorpusComparison.measure` holding no set of the corpus survivors and no row of the corpus side, the corpus side through `MeasuredForms.eachOf` a page at a time, its quartiles as §4, `CORPUS_METRICS` started only once a page has a survivor and each later read started as `CORPUS_METRICS_AGAIN`; `MeasuredForms.eachOf(RunId, Collection<OccurrenceId>, Row)`; `EmbeddingStatement.CORPUS_SURVIVORS` removed, `CORPUS_METRICS_AGAIN` added straight after `CORPUS_METRICS`, both declaring no steps a row; `EmbeddingStatementProgress.rowsRead`, default empty.
- **`pipeline`**: `StatementProgress` taking rows read as well as steps, with the same lines and cadence; `ContentCensusTasklet` writing ADR-191's line at `SHINGLE_ROWS`'s start and no progress for it, the running counter `Stage 3 (content census, shingled documents checked)` from `shingledOccurrencesChecked`, the extraction metrics' progress from `rowsRead`, and the struck drains' lines no more; `SeedCorpusComparisonTasklet` writing the corpus reads' progress from `rowsRead` and `CORPUS_METRICS_AGAIN`'s lines as §9 words them; the implementation of `MeasuredForms` handing `eachOf` to `extractionMetrics::eachMeasuredFormOf`; `EmbeddingScoringTasklet` and `RelevanceScoringTasklet` as §6, timing the count with `counting` and `counted`; `ClusteringTasklet` as §5.
- **Javadoc that says otherwise**: the comments in the six classes that cite ADR-209 §2's departure, `ContentCensusTasklet`'s comment calling the shingle read *the one statement* and its account of the progress between ADR-191's lines, and `FrequencyProgress`'s account of the loop over hashes.
- **Move the two parked tests into `src/test`.** Edit no other test.
- **Leave the Markdown to the analyst**, as ADR-210's owes did: once the code lands, the banners on ADR-060, ADR-191, ADR-192, ADR-193, ADR-200, ADR-204 and ADR-209 pointing here, and `AGENTS.md`'s sentences on the ledger's reads and on the defects known and open.

## What this does not decide

- **Stage 2's resume set, and any survey beyond the six classes**, are [#458](https://github.com/algernon28/vespera/issues/458)'s, by the operator's decision of 2026-10-08. Read at `4b99a03`, stage 2's resume reads every occurrence the stopped run measured into a set (`ExtractionMetrics.occurrencesForRun`, used by `UnrecordedOccurrences`), as large as the survivors measured before the stop; no survey of the other classes was made for this record.
- **What any of it costs on `H:`**, and how much temporary storage stage 3's grouping takes there. Both are measured only on a solid-state disk, over synthetic ledgers.
