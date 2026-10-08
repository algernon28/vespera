# ADR-211 — No class holds every survivor of a run: another table's rows are read a page of survivors at a time, and no page of a walk sorts the walk

- **Date**: 2026-10-08
- **Status**: accepted
- **Amends**: [ADR-209](0209-the-ledger-is-four-records-behind-one-type-no-capability-module-names-spring-batch-and-a-tables-sql-is-its-owners.md) §2, the paragraph *"The sets that are still held, and what they depart from"*, and the Consequence *"Six places still hold every survivor of a run at once"*. Once the commit that builds `src/main` lands, none of the six holds a set of the survivors (§1). The item of ADR-209's *What this does not decide* that owed this record is answered here, less the three questions this record leaves to the operator (What this does not decide). The sentence of ADR-060's banner that ADR-209 wrote, *"Six callers still build a set of every survivor's id themselves"*, stops holding with it.
- **Amends**: [ADR-193](0193-a-statement-sqlite-counts-reports-how-far-it-has-gone-and-one-it-cannot-count-says-how-long-it-took.md) §1, by a third form beside counted and timed, *a read made a page of survivors at a time* (§7); §3's table, whose rows for stage 3's shingle rows, `ConfidenceDistribution`'s extraction metrics and 5b's corpus survivors' extraction metrics lose their r; §6, whose rows for `DocumentFrequency.drainSurvivors`, `ConfidenceDistribution.drainSurvivors`, 5b's drain of the survivors and 5f's drain are struck, and whose row for 5c's and 5d's drain becomes a timed count; and §7's enums, which lose `ExtractionStatement.SURVIVORS`, `SimilarityStatement.FREQUENCY_SURVIVORS` and `EmbeddingStatement.CORPUS_SURVIVORS`. [ADR-204](0204-every-line-of-adr-193s-part-b-is-written-out-and-its-table-is-read-again-against-the-code.md) §3 and §4, in the same lines and constants.
- **Amends**: [ADR-192](0192-every-loop-in-every-stage-reports-its-progress-and-no-counter-floods-the-log.md) §4, the row for 5f's counter of the keys it reads (ADR-206's renaming of *files hashed*): one counter for each partition, where there was one over every partition's members summed (§3).
- **Amends**: [ADR-200](0200-stage-1-reads-survivors-by-size-and-holds-one-size-at-a-time.md)'s Consequence *"Other stages that drain survivors into a `Set` are not touched. `SeedCorpusComparison` and `ConfidenceDistribution` do, each for a stated reason of its own."* None does after this record.
- **Amends**: [ADR-191](0191-stage-3-says-how-many-shingle-rows-it-is-about-to-read-and-how-long-measuring-them-took.md) in one respect: the read it announces is no longer one statement but one statement for each page of stage 2's survivors (§2). Its two lines stand word for word (§7).
- **Keeps**: [ADR-060](0060-survivors-is-a-ledger-owned-item-reader-not-a-view.md)'s *"never a materialized `List` of what could be a million-plus ids"*, now of every caller and not only of what `ledger` hands out. ADR-209 §3: no statement names a table its module does not own. ADR-193 §2's rule that a counted statement runs on the one connection it is handed and is never handed back to a template inside its callback, which is why the merge below is made a page at a time (§2). [ADR-156](0156-a-runs-survivors-are-read-through-its-upstream-runs-and-a-verdict-under-any-other-run-stays-recorded-and-removes-nothing.md): which runs a run's survivors answer to. [ADR-045](0045-clustering-runs-within-each-seed-partition.md) and [ADR-085](0085-vectors-live-in-sqlite-and-the-pairwise-matrix-is-never-materialised.md): a partition is clustered whole. [ADR-086](0086-seed-corpus-mismatch-is-measured-before-the-model-gate-and-reported-never-enforced.md): exact medians and quartiles. [ADR-074](0074-stage-3-measures-shingle-document-frequency-a-boilerplate-floor-ships-unset.md)'s omission rule.
- **Rests on**: [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) and [ADR-048](0048-walk-and-run-identity.md) (which run ids move, §9); the operator's framing of #456, that Vespera has to work on any archive, so the bound is ADR-060's whatever the size and no archive's size is asked for; and a throwaway probe over synthetic ledgers built from the shipped `schema.sql` (Measured). No archive and no working directory was opened for this record (ADR-196).
- **Takes up** [#456](https://github.com/algernon28/vespera/issues/456): it settles the six sets the ticket names, and leaves three questions to the operator (What this does not decide).

## Context

ADR-209 §2 counted six classes that put the id of every survivor of a run into a set, and left them the size they were. Read at `4b99a03`, this is what each does with its set, and what else it holds that grows with the same survivors:

| Class | Survivors of | What the set is for | Held beside it, as large as the survivors |
| --- | --- | --- | --- |
| `extraction.ConfidenceDistribution` | stage 2's run | every `extraction_metric` row of the run is read in one statement and kept only if its occurrence is in the set | a `List<Double>` of one entry for every row read, the ruled-out ones as `null` |
| `similarity.DocumentFrequency` | stage 2's run | every `shingle` row of the run is read in one statement and counted only if its occurrence is in the set | for each distinct hash, a set of the occurrences it was seen in; for each granularity, a set of every shingled survivor; and one map entry for each distinct hash, which is not a set of survivors and is larger than all of them (§8) |
| `embedding.SeedCorpusComparison` | the measurement run | every `extraction_metric` row of stage 2's run, through `MeasuredForms.each`, kept only if its occurrence is in the set | a `MetricRow` for every corpus survivor with a row, kept for the four medians (§8) |
| `pipeline.ClusteringTasklet` | the scoring run | each partition's members, read from `relevance_score`, are kept only if in the set | the members of every partition at once, which after the filter are the scoring run's survivors |
| `pipeline.EmbeddingScoringTasklet` | the measurement run | **nothing is tested against it**: the step goes through the set, survivor by survivor, and takes its size for the counter | — |
| `pipeline.RelevanceScoringTasklet` | the measurement run | **nothing is tested against it**, the same | — |

The ticket's premise holds for four of the six. Two only go through the survivors, and for them the question of which candidate replaces the set does not arise (§4).

**One more thing was found while measuring, and it costs more than any of the sets.** The page statement of `Verdicts.survivors`, and of `Occurrences.occurrencesOf`, is planned by SQLite as a search of `file_occurrence_by_walk_and_size` on `walk_id` followed by `USE TEMP B-TREE FOR ORDER BY`: every page goes through the whole walk, asks the anti-join of every occurrence in it, sorts what survives, and keeps 1,000. So a whole read of the survivors grows as the square of the walk (Measured). `SurvivorsBySizeTest` pins that the read by size sorts nothing; no test pinned it of the two reads in id order. Every candidate below reads the survivors, as the sets do today, so the cost of each is mostly this one until it is fixed (§6).

## Measured

A throwaway probe outside the repository, on 2026-10-08: Java 26 and the sqlite-jdbc the pom carries (3.53.2.1), on a solid-state disk with the file cache warm, in write-ahead log mode as the application opens the database. Each ledger was built from the shipped `schema.sql`: one walk; a stage-1 run and a stage-2 run upstream of it; every tenth occurrence `broken` under stage 1 and every tenth, five on, `degenerate-output` under stage 2, so 80% survive stage 2; an `extraction_metric` row under stage 2 for every occurrence stage 1 kept, a third with no score; and a fixed number of `shingle` rows for each, a tenth of them drawn from 2,000 hashes so that hashes recur across occurrences. Three ways of restricting another table's rows to the survivors were timed over the same rows:

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

**With the two statements planned by the integer primary key** (§6), the same ledgers:

| Ledger | Read | A | B | C | Statements, A / B / C | Most ids held, A / B / C |
| --- | --- | ---: | ---: | ---: | --- | --- |
| 100,000 | metrics | 0.08–0.09 s | 0.13–0.15 s | 0.09–0.13 s | 82 / 181 / 161 | 80,000 / 1,000 / 1,000 |
| 100,000 | shingles | 1.96–2.02 s | 2.43–2.75 s | 1.64–1.81 s | 82 / 9,001 / 161 | 80,000 / 20 / 1,000 |
| 300,000 | metrics | 0.24 s | 0.37 s | 0.31 s | 242 / 541 / 481 | 240,000 / 1,000 / 1,000 |
| 300,000 | shingles | 1.33 s | 1.60 s | 1.07 s | 242 / 5,401 / 481 | 240,000 / 100 / 1,000 |

- **The answers were the same** in every run: each grade's count A = B = C; 2,000 frequency rows written by each; for every hash, C's count of occurrences and of times seen equal to A's; and as many shingled survivors.
- **C's rows came in occurrence order**, every one, across pages and within each. That is what lets the counts per hash be kept without a set (§5).
- **B costs ten times the statements over shingles**, because a page of 1,000 shingle rows covers 20 occurrences at 50 rows each and 100 at 10, and is the slowest over shingles in every run.
- **The plans**, from `EXPLAIN QUERY PLAN`: C's lookup of metric rows searches `sqlite_autoindex_extraction_metric_1 (occurrence_id=? AND run_id=?)`, and of shingle rows `shingle_by_occurrence (occurrence_id=? AND run_id=?)`, with `ORDER BY occurrence_id` and no temp B-tree. B's pages search `extraction_metric_by_run_id` and `shingle_by_run_id` by `(run_id=? AND rowid>?)`. The ledger's ask, written as the page statement is, with `walk_id` compared as a value and not as an index column, searches `file_occurrence` by `INTEGER PRIMARY KEY (rowid=?)`.
- **The last page of a walk with a larger walk after it**: with 1,000,000 occurrences of a later walk recorded after the 300,000, the last page of the first walk's survivors, planned by the primary key, took 0.031 s, going on through the later walk's rows once to find it has no more. The first page took 0.001 s against 0.098 s as shipped.
- **Heap.** A `HashSet` of ids held 66.8 MB for 1,000,000 survivors, about 67 to 77 bytes an id at every size measured, `Long` or `OccurrenceId` alike. `DocumentFrequency`'s map held 291 bytes for each distinct hash seen once, as it counts today, and 99 bytes for each with the occurrences counted by the last one seen (§5): 2,000,000 hashes, 581 MB against 197 MB.

**Not measured**: any of this on a spinning disk, on `H:`, or with the file cache emptied; any archive. The probe is a measurement of plans and of the relative cost of three shapes, not of what stage 3 will take on the operator's database.

## Decision

### 1. Per site

| Site | Decision | Why, from Measured |
| --- | --- | --- |
| `ConfidenceDistribution` | **C**: a page of stage 2's survivors at a time, and that page's `extraction_metric` rows by key | within a tenth of a second of A, and cheaper than B, at both sizes, holding a page |
| `DocumentFrequency` | **C**, its rows by key in occurrence order | the cheapest of the three; its order is what lets the sets per hash and per granularity go (§5); B issued ten times the statements |
| `SeedCorpusComparison` | **C**, through a new `MeasuredForms` method that reads the rows of named occurrences (§2) | the same read as `ConfidenceDistribution`'s, with seven columns |
| `ClusteringTasklet` | **B**: each partition's members, already in hand, asked of `Verdicts`; one partition at a time (§3) | C would read every survivor once for each partition; B asks only about members, a page of them at a time |
| `EmbeddingScoringTasklet` | **neither**: it goes through `Verdicts.survivors` itself, and counts first (§4) | nothing is tested against a set |
| `RelevanceScoringTasklet` | **neither**, the same | the same |

### 2. Candidate C, a page at a time

**The shape.** The caller goes through `Verdicts.survivors(run)` and gathers its ids into pages of at most 1,000, the ledger's own page size, in the order they come. For each page it reads that page's rows of its own table in one statement:

- `ConfidenceDistribution`: `SELECT occurrence_id, mean_score FROM extraction_metric WHERE run_id = ? AND occurrence_id IN (?, …)`, the run being stage 2's, and adds each score to its grade as it is read.
- `DocumentFrequency`: `SELECT occurrence_id, shingle_parameter_identity, shingle_hash FROM shingle WHERE run_id = ? AND occurrence_id IN (?, …) ORDER BY occurrence_id`, the run being stage 2's. The `ORDER BY` is required, not decorative: §5 counts on each occurrence's rows coming together.
- `SeedCorpusComparison`: `MeasuredForms` gains `eachOf(RunId, Collection<OccurrenceId>, Row)`, which hands each row of the named occurrences under the run to the callback. `pipeline` passes `extraction.ExtractionMetrics.eachMeasuredFormOf(RunId, Collection<OccurrenceId>, MeasuredFormRow)`, new, whose statement is the seven columns `eachMeasuredForm` reads with `AND occurrence_id IN (?, …)`. `embedding` names no `extraction` table (ADR-209 §3.2's arrangement, one method more). `each` stays, for the seeds' rows under the measurement run, which are the seed set and are read as before.

No statement names more than 1,000 occurrences, so none comes near SQLite's limit on a statement's variables. A page with no survivor makes no statement.

**Why a page at a time, and not one statement beside a cursor.** The candidate as the ticket words it reads the other table in occurrence order alongside the survivors and merges the two. Done literally, that is two statements open at once: the read of the other table, and the ledger's page statements, which go through the template. ADR-193 §2 forbids exactly that inside a counted read: outside a transaction it borrows a second connection, and under the test profile's pool of one it waits on a connection its own caller holds. So the merge is made the other way round: the survivors' page is read first and let go of as a statement, then the other table's rows for that page. Each statement finishes before the next begins, and the merge is the same merge.

**Each module keeps its statement.** The lookups are written by the module that owns the table, `extraction` and `similarity`, as every statement over those tables is (ADR-209 §3). The survivors are asked of `ledger`. Nothing passes a connection, a table's name or a temp table across a module.

### 3. Candidate B at 5f, and one partition at a time

**`Verdicts.survivingAmong(RunId run, Collection<OccurrenceId> occurrences)`** answers a `Set<OccurrenceId>`: those of the given occurrences that carry no blocking verdict under `run` or any run upstream of it (ADR-156), and that belong to `run`'s walk. It asks in statements of at most 1,000 occurrences each, and asks nothing for an empty collection. Its statement is the survivors' anti-join with `id IN (?, …)` in place of the keyset, planned by the integer primary key (§6). It is `ledger`'s, beside `survivors` and `survivorCount`.

**`ClusteringTasklet` holds one partition at a time.** For each partition, in the order `Clustering.partitions` gives, it reads the members (`Clustering.membersOf`, timed as now), keeps those `survivingAmong(scoring, members)` answers, in the order `membersOf` gave them, reads their keys and clusters them, and lets the partition go before it reads the next. It reads no survivor of the scoring run otherwise.

**So its counter of the keys it reads is one for each partition**, `Stage 5f (clustering, cache keys read, partition <P> of <M>)`, over that partition's surviving members, opened when the partition's members are known and before its first key is read. The counter over every partition's members summed, which ADR-192 §4 and §5 gave 5f, needed every partition's members before the first was clustered, and those are the scoring run's survivors. It takes the shape 5f's counter of pairs of blocks already has. A partition left with no member opens no counter, as it opens none over blocks, and is counted done as now. **The members of a partition are now read just before that partition is clustered**, where they were all read first, so the timed lines `reading the members of partition <P> of <M>` fall each before its partition's work and its group sizes, not all together.

### 4. 5c and 5d go through the read

Each asks `Verdicts.survivorCount(measurementRun)` for its counter's total and its starting line, then goes through `Verdicts.survivors(measurementRun)` and works on each survivor as it comes. Neither writes a verdict, so nothing it writes changes a page still to come. **The order changes**, from a hash set's to ascending occurrence id; nothing reads either step's rows by the order they were written. The seed set each holds stays: it is the operator's seed folder, not the corpus, and 5d holds the seeds' vectors resident on purpose (ADR-085).

### 5. What else goes from the six

- **`ConfidenceDistribution`'s list of scores.** Each score is added to its grade as it is read; nothing is kept per row.
- **`DocumentFrequency`'s sets of occurrences.** Because §2's rows come in ascending occurrence order and each occurrence's rows together, a hash's count of occurrences is kept as the last occurrence it was seen in and a count, raised when the occurrence differs from the last; and a granularity's count of shingled survivors the same way. The counts are the ones the sets gave (Measured). Per hash, that is 99 bytes where it was 291.
- **`ClusteringTasklet`'s members of every partition** (§3).

### 6. The two reads in id order sort nothing

**Each page of `Verdicts.survivors` and of `Occurrences.occurrencesOf` is planned as a search of `file_occurrence` by its integer primary key, `rowid>?`, and names no temp B-tree.** The rows and their order are unchanged. Written as today, SQLite prefers the index on `walk_id` and sorts the walk on every page; compared as a value, `+walk_id`, the walk no longer chooses the index, and the page goes forward from the last id read until it has 1,000. `survivingAmong` is written the same way, so each ask is a lookup by key.

**What it costs instead.** The last page of a walk with later walks recorded after it goes on through their rows once, testing the walk of each, to learn it has no more: 0.031 s for 1,000,000 later rows (Measured), once a read. The pages before it cost a page each. `ledger` is in no stage's implementation version, so this moves no run id of its own.

### 7. What the operator reads

- **Three timed lines are struck**: stage 3's two, `is reading stage 2's survivors for the shingle frequencies` and `for the confidence distribution`, and 5b's `is reading the corpus survivors`, each with its line after. There is no statement left for them to time; the pages are read inside the loop that reports, which ADR-199 §3 already counts as that loop's.
- **The three reads keep every line they had.** Stage 3's shingle rows keep ADR-191's line before, word for word, and its line after the measurement; `Stage 3 (content census) is reading the extraction metrics, over up to <N> rows` and 5b's `… is reading the corpus survivors' extraction metrics, over up to <N> rows`, and their lines after, stand as ADR-204 §3 wrote them; and between them the same progress line, `<label>: about <X>% of <N> rows`, on ADR-193 §5's cadence. `<N>` is the span of the run's rowids in the table, as now.
- **What changes is what `<X>` is taken from: the rows read, counted by the module as it reads them, and not SQLite's steps.** That is the third form §1 of ADR-193 gains: a read made a page of survivors at a time is reported with the counted form's lines, its total the span, its progress the rows read so far. It goes through only the survivors' rows, so it can end below 100% where the run holds rows of ruled-out occurrences; the span was always a bound (*"up to"*), and the estimate falls behind, never ahead (ADR-193 §3).
- **The callback.** `ExtractionStatementProgress`, `SimilarityStatementProgress` and `EmbeddingStatementProgress` each gain `default void rowsRead(<Module>Statement statement, long rows) {}`, called once after each page's rows have been read, with the rows read so far, the earlier pages' included. `statementStarting` is called once before the first page's rows are read, with the span, and `statementEnded` once after the last, on every path but one that throws, as ADR-193 §7 has it. `stepsTaken` is not called for these three. `SHINGLE_ROWS`, `EXTRACTION_METRICS` and `CORPUS_METRICS` stay in their enums and declare no steps a row.
- **When each read is started.** `EXTRACTION_METRICS` and `SHINGLE_ROWS` are started whether or not there is a survivor, as today, with an empty total where the run holds no row; with no survivor nothing comes between start and end. `CORPUS_METRICS` is started only once the first page of survivors has come back with one, as today it is not issued over an empty population.
- **5c and 5d** write `Stage 5c (embedding scoring) is counting the corpus survivors`, and `… counted the corpus survivors in <S> s`, where they wrote `is reading` and `read`; 5d the same under its own name. ADR-199 §2's wording for a timed survivor count. Their counters and starting lines are unchanged.
- **5f** writes no line about the corpus survivors, and its keys counter is one for each partition (§3).

### 8. What is still held, and why

The ticket's acceptance asks that any class still holding a collection as large as a run's survivors have a record saying why. After this record:

- **The seed set**, in `SeedCorpusComparison`, 5c and 5d. Not survivors: the operator's seed folder.
- **The largest partition, at 5f.** A partition is clustered whole: every pair of its members is compared, and `Clustering` addresses its blocks by the members' keys (ADR-045, ADR-085). So 5f holds one partition's members and their keys, and a partition can be as large as the scoring run's survivors where one seed wins nearly all. The bound is the largest partition, as ADR-200's is the largest set of one size; it is no longer every partition at once.
- **`DocumentFrequency`'s map of distinct hashes.** One entry for each distinct (granularity, hash) seen in a surviving occurrence: the frequencies being measured, before the omission rule writes those seen twice or more. It is not a collection of survivors. It grows with the distinct shingles of the corpus, which is far more than its survivors, and at 99 bytes a hash it is the largest thing stage 3 holds. **Not decided here** (What this does not decide).
- **`SeedCorpusComparison`'s rows of the corpus side.** One for each corpus survivor with a metric row, kept because ADR-086's medians and quartiles are exact, and an exact quartile needs every value or a pass over them for each. **Not decided here** either.

### 9. Which run ids move

**Stages 2 to 6b, once**, on the first build that ships this. `StageModules` names the modules each stage's implementation version spans (ADR-058):

| Stage | Modules named | Touched here |
| --- | --- | --- |
| 1 | `corpus` | nothing |
| 2 | `extraction`, `similarity` | `ConfidenceDistribution`, `ExtractionMetrics`, the statement interface and enum of each; `DocumentFrequency` |
| 3, 4 | `similarity`, `extraction`, `pipeline` | the same, and `pipeline` |
| 5, both runs | `embedding`, `extraction`, `pipeline` | `SeedCorpusComparison`, `MeasuredForms`, `EmbeddingStatement`, `EmbeddingStatementProgress`, and the same |
| 6a, 6b | `synthesis`, `extraction`, `embedding`, `pipeline` | the same; nothing in `synthesis` |

Stage 2's code is not what changes, but its id moves all the same, because the stage-3 code it shares a module with does. So **stage 2 does its work again under its new id over an existing working directory**: it reads and hashes each survivor to look up its conversion, finds it in the extraction cache and converts nothing again, as after ADR-207, ADR-209 and ADR-210. `ledger` is in no stage's list, so §3's and §6's changes to it move nothing on their own. **Stage 1's id does not move.**

**What does not move**: no DDL, so no schema version, and an existing working directory opens; no cache key; no `run.stage` and no `finished_step.step` value. `StageModules` is not edited, so `RunIdentityGoldenTest` is not. The verdicts under the old ids stay recorded and remove nothing (ADR-156). An arrangement is minted again, and `arrangementApproved` must name the new one.

## Alternatives refused

- **Candidate B for the three reads.** Measured: no cheaper over metrics, and over shingles ten times the statements and the slowest of the three. Its rows come in rowid order, so `DocumentFrequency`'s sets per hash would have to stay.
- **One statement over the other table beside the ledger's cursor.** Two statements open at once on one connection, which ADR-193 §2 forbids inside a counted read and the pool of one turns into a wait on itself (§2).
- **The survivors written into a temporary table and joined in the owning module's SQL.** That module's statement would then name a table of `ledger`'s, and a temporary one belongs to one connection, so it would hold only inside a transaction.
- **Counting the three reads by SQLite's steps across their pages.** Each page is its own statement with its own handler, and a page of extraction metrics takes about 12,000 steps, under one callback, so the read would never report. The module reads every row in Java and knows the count exactly.
- **5f's counter kept summed, by reading every partition's members twice**, once to count and once to cluster. Twice the statements and twice each partition's timed lines, to keep a total over a collection this record exists to stop holding.

## Consequences

- **None of the six holds a set of a run's survivors.** What each still holds that grows with them is in §8, with its reason, or is left to the operator.
- **Every read of the survivors and of a walk's occurrences stops going through the walk on every page** (§6): measured at a hundredth of the time at 100,000 occurrences and a fiftieth at 300,000, and the gap grows with the walk. Every stage that reads its survivors gains it, not only the six.
- **Stage 3 holds about a third of what it held for each distinct hash** (§5).
- **5c and 5d go through their survivors in occurrence order.**
- **The operator sees three pairs of lines fewer, two pairs say *counting* where they said *reading*, and 5f's keys counter is one for each partition** (§7).
- **The run ids of stages 2 to 6b move once**, with the caches warm (§9).

## Tests

**Where they are.** As ADR-210's: each test is in `src/test`, compiles against the tree at `4b99a03`, and fails there for the reason given, where the tables below give one; the two edits marked *green* pass there and must still pass after. The exception is two tests parked under `docs/adr/0211/tests/`, followed by the path each takes in the repository, because each names a method this record adds and would stop the test tree compiling. `spec-implementer` moves them into `src/test` with the change. A test that implements a callback this record adds, `rowsRead` or `MeasuredForms.eachOf`, writes it without `@Override`, so it compiles before the method exists and implements it after.

**New, in `src/test`:**

| Test | What it pins | Red at `4b99a03` because |
| --- | --- | --- |
| `StatementLog` (fixture, not a test) | a template whose connections keep, in order, the text of every statement they prepare or run, and notes a test adds among them | — |
| `ledger.ReadsInIdOrderSortNothingTest` | each page of `survivors` and of `occurrencesOf`, over a walk of 2,500 with a later walk after it, captured as issued, is planned by `INTEGER PRIMARY KEY` and names no temp B-tree; and the rows are the survivors, in order | each page searches `file_occurrence_by_walk_and_size` and sorts in a temp B-tree |
| `extraction.ConfidenceDistributionReadsAPageOfSurvivorsAtATimeTest` | over 2,500 survivors and 50 ruled out: the reads of metric rows alternate with the pages of survivors, one after each, three of each; each names at most 1,000 occurrences; each grade counts the survivors and no ruled-out one | one read, after all three pages, naming no occurrence |
| `similarity.DocumentFrequencyReadsAPageOfSurvivorsAtATimeTest` | over 2,500 survivors whose shingle rows were written interleaved, so no occurrence's rows lie together by rowid, a hash written twice in some: the reads of shingle rows alternate with the pages, each ordered by occurrence and naming at most 1,000; every frequency row's two counts, and the corpus size, are those of the survivors alone | one read, after all three pages |
| `embedding.SeedCorpusComparisonReadsAPageOfSurvivorsAtATimeTest` | over 2,500 corpus survivors: the corpus side's rows are asked for through `MeasuredForms.eachOf`, once after each page of survivors, at most 1,000 at a time; `each` is asked for the measurement run alone; the corpus count is the 2,500 | `each` is asked for stage 2's run and `eachOf` never |

**Edited, in `src/test`:**

| Test | Edit | Red at `4b99a03` because |
| --- | --- | --- |
| `extraction.ConfidenceDistributionStatementProgressOrderTest` | no drain is reported; the read of the metrics is started with the span, given its rows after each page, and ended; over 2,500 survivors `rowsRead` reports 1,000, 2,000 and 2,500 and no steps; with no row and no survivor, start and end only; a read that throws, started and never ended | the drain is reported, and the read reports steps |
| `similarity.SimilarityStatementProgressOrderTest` | measuring reports the read of the shingle rows and nothing before it, its rows after each page, then its loop | the drain is reported first |
| `embedding.EmbeddingStatementProgressOrderTest` | no drain of the corpus survivors; the corpus metrics given their rows, not steps; the seeds' reads unchanged; the two tests of a read that throws without the drain | the drain is reported, and the corpus read reports steps |
| `embedding.RecordedForms` | gains `eachOf`, picking the named occurrences from the run's rows as `extraction` reads them | *green*: a fixture, not a test; the comparison does not call `eachOf` yet |
| `pipeline.StatementStepsPerRowAreTheDeclaredOnesTest` | the three reads leave the measured map and are held to declaring no steps a row, as reads made a page of survivors at a time | each still declares its r |
| `pipeline.StatementStepsPerRowTest` | stage 3's two reads are no longer measured, no code issuing them; 5b's seed read still is | *green* |
| `pipeline.StageFiveReportsItsProgressInvocationTest` | 5b says nothing about reading the corpus survivors; 5c and 5d count them; 5f says nothing about them and reads each partition's members before that partition's group sizes; the keys counter is one for each partition; over tens of thousands of rows of a folder nobody walked, stage 3's and 5b's reads of extraction metrics say nothing about how far they have gone, never going through those rows | the old lines are written, and both reads go through those rows |
| `pipeline.RedundancyResolutionReportsItsProgressInvocationTest` | stage 3's lines carry no read of stage 2's survivors | both timed reads are written |

**Parked under `docs/adr/0211/tests/`:**

- `src/test/java/io/algernon/vespera/ledger/SurvivingAmongTest.java`: `survivingAmong` answers the occurrences that survive under the run's scope, leaving out one blocked under the run, one blocked under its upstream run and one of another walk, and keeping one blocked only under a sibling run; over 2,500 occurrences no statement names more than 1,000; an empty collection makes no statement; each statement is planned by the integer primary key.
- `src/test/java/io/algernon/vespera/extraction/MeasuredFormsOfNamedOccurrencesTest.java`: `eachMeasuredFormOf` hands over the rows of the named occurrences under the run and of no other, a missing score and a missing page count told apart from zero; an empty collection makes no statement.

**Not pinned, and why.** That 5c and 5d hold no set is pinned only through their lines, the count in place of the read: seeing it directly takes more than 1,000 survivors through the whole job. 5f's filter is pinned by the existing tests of a floor that empties a partition. What any of this costs on `H:`.

## What the commit that builds `src/main` owes

- **`ledger.Verdicts`**: the page statement of `survivors` planned by the primary key (§6), with `walk_id` compared as a value; `survivingAmong(RunId, Collection<OccurrenceId>)` returning `Set<OccurrenceId>` (§3), its statements of at most 1,000 occurrences, none for an empty collection, issued through `JdbcTemplate.query(String, RowMapper, Object...)`. **`ledger.Occurrences`**: `occurrencesOf`'s page the same way. Each page still carries ` LIMIT ` as ADR-209 owes; `survivingAmong`'s statements carry none.
- **`extraction`**: `ConfidenceDistribution.measure` as §2 and §5, holding no set and no list; `ExtractionMetrics.eachMeasuredFormOf(RunId, Collection<OccurrenceId>, MeasuredFormRow)`, none for an empty collection; `ExtractionStatement.SURVIVORS` removed and `EXTRACTION_METRICS` declaring no steps a row; `ExtractionStatementProgress.rowsRead`, default empty.
- **`similarity`**: `DocumentFrequency.measure` as §2 and §5, the counts per hash and per granularity by last occurrence; `SimilarityStatement.FREQUENCY_SURVIVORS` removed and `SHINGLE_ROWS` declaring no steps a row; `SimilarityStatementProgress.rowsRead`, default empty. `shingleRowsUpTo` stays, its total for the read.
- **`embedding`**: `SeedCorpusComparison.measure` holding no set of the corpus survivors, the corpus side through `MeasuredForms.eachOf` a page at a time, `CORPUS_METRICS` started only once a page has a survivor; `MeasuredForms.eachOf(RunId, Collection<OccurrenceId>, Row)`; `EmbeddingStatement.CORPUS_SURVIVORS` removed and `CORPUS_METRICS` declaring no steps a row; `EmbeddingStatementProgress.rowsRead`, default empty.
- **`pipeline`**: `StatementProgress` taking rows read as well as steps, with the same lines and cadence; the handlers of `ContentCensusTasklet` and `SeedCorpusComparisonTasklet` writing the three reads' progress from `rowsRead` and the struck drains' lines no more; the implementation of `MeasuredForms` handing `eachOf` to `extractionMetrics::eachMeasuredFormOf`; `EmbeddingScoringTasklet` and `RelevanceScoringTasklet` as §4, timing the count with `counting` and `counted`; `ClusteringTasklet` as §3.
- **Javadoc that says otherwise**: the comments in the six classes that cite ADR-209 §2's departure, and `ContentCensusTasklet`'s comment calling the shingle read *the one statement*.
- **Move the two parked tests into `src/test`.** Edit no other test.
- **Leave the Markdown to the analyst**, as ADR-210's owes did: once the code lands, the banners on ADR-060, ADR-191, ADR-192, ADR-193, ADR-200, ADR-204 and ADR-209 pointing here, and `AGENTS.md`'s sentence on the ledger's reads.

## What this does not decide

Three questions are the operator's. Each is put with a recommendation; none is settled by this record.

1. **`DocumentFrequency`'s map of distinct hashes** (§8). It is not a collection of survivors, so it is outside the six sets this ticket names, but the acceptance's words reach it, and it is the largest thing stage 3 holds. Keeping it bounded means counting in SQLite instead, a table of `similarity`'s own written a page at a time and the rows seen once deleted at the end, which costs a write for every distinct hash and a write-ahead log of that size. *Recommended*: a ticket of its own, measured as this one was, since its cost is a different kind from a set's.
2. **`SeedCorpusComparison`'s corpus rows** (§8). Exact quartiles in bounded memory are possible by reading the corpus side's values again a few times, narrowing each quartile's range on each pass; an estimate would change the figures the page shows. *Recommended*: the passes, which keep ADR-086's figures exact and cost a few more reads of rows this record makes cheap, in a ticket of its own.
3. **How far the acceptance reaches outside the six classes.** Taken literally, *no class holds a collection as large as a run's survivors* is a survey of every class. One more was found while reading for this: stage 2's resume, which reads every occurrence the stopped run measured into a set (`ExtractionMetrics.occurrencesForRun`, `UnrecordedOccurrences`), as large as the survivors measured before the stop. No survey was made. *Recommended*: #456 closes on the six; a survey, and stage 2's resume set, are a ticket of their own.
