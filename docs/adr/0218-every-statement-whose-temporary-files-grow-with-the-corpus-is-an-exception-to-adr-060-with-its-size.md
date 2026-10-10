# ADR-218 — Every statement whose temporary files grow with the corpus is an exception to ADR-060, recorded with its size, and stage 4b's build needs 183 bytes free for each shingle row

> **Partly amended — see [ADR-219](0219-stage-3s-grouping-names-the-index-on-the-run-and-the-clause-ships-with-the-next-change-to-similarity.md).** Two things below no longer hold as written. The item "Whether stage 3's grouping should ever run with `shingle_by_hash` present" of *What this does not decide*, with its "no ticket holds it" and its "whether the 45 steps a row ADR-211 declares for that statement's progress hold under that plan was not looked at": #473 held it, and ADR-219 decides it. The grouping is to name its index, `INDEXED BY shingle_by_run_id`, in a clause that ships with the next change that moves `similarity`, and the 45 steps a row were measured to be above the most taken in both plans. And Measured's "That reading is of `StageModules` and ADR-182, and no invocation was run to see it": the invocations were run for ADR-219, and stage 3 does meet the index after a change to `pipeline` alone, and also after a stop of stage 3 over one corpus root while another reaches stage 4b. This record's "six to seven times as long" is of its own probe; ADR-219's runs took 4.8 to 6.2 times as long over 1,500,000 to 9,000,000 rows. Everything else in this record stands.

> **Partly amended — see [ADR-220](0220-no-class-holds-every-occurrence-of-a-run-stage-2s-resume-the-census-stage-4b-and-stage-5e-read-a-page-at-a-time-or-ask-by-key-and-what-is-still-held-says-why.md).** What this record lists is no longer all of it; none of its decisions is changed. Rows 8 and 9 of *The statements whose temporary files grow with the corpus* no longer exist: ADR-220 put a read a page at a time, which sorts nothing, in the place of each, so §3's class is rows 6, 7, 10 to 13 and 15 to 20, and the test holds two statements for `RelevanceDistribution` and two for `RelevanceScoreCache`, and three for `RedundancyResolution`. Two statements of stage 4b's that sort are in no table below and are recorded in ADR-220's §15: its read of an occurrence's rarest shingles, bounded by one occurrence's `shingle` rows, which joins *What sorts and is bounded by something other than the corpus*; and its statement for a page's candidate pairs, whose rows grow with the signed occurrences that share a bucket, which the operator excepted as it stands on 2026-10-09, as §3's reads are (ADR-220 §15, question 7), with no measured size, its size and its bounded form being [#476](https://github.com/algernon28/vespera/issues/476)'s. Of *What was planned and keeps nothing in temporary storage*, `RedundancyResolution.java:243` and `:453` are gone, and the line numbers given below for the classes ADR-220 edits no longer hold. ADR-220's §15 also says what it answers of the items below that name #458, and the one on `rarestHashes`, and what it leaves open: whether the reads of §3 that are left stay as they are or are bounded is [#472](https://github.com/algernon28/vespera/issues/472)'s for rows 12, 13 and 16 to 18 and [#477](https://github.com/algernon28/vespera/issues/477)'s for rows 6, 7, 10, 11, 15, 19 and 20. Everything else in this record stands.

> **Partly amended — see [ADR-221](0221-shingle-by-hash-is-an-index-on-the-rows-of-the-run-in-hand-and-an-earlier-runs-rows-stay.md).** The size of this record's first exception is of a statement that no longer ships once ADR-221 is built. Stage 4b builds `shingle_by_hash` over the rows of the one stage-2 run it reads, and no longer over every row `shingle` keeps. So these do not hold as written: this record's title in its "183 bytes free for each shingle row"; row 2 of *The statements whose temporary files grow with the corpus* and the sentence under the table, "Row 2's rows are the table's, not the run's"; §1's "91.6 bytes a row", "The build stays one statement, as ADR-182 has it" and "The row count is the table's and not the run's"; and §5's "183 bytes for each row `shingle` keeps", its formula and its worked table. ADR-221 measured the new build at 23.3 to 24.4 bytes of temporary files for each row of the run and 46.1 to 50.5 at the peak, and gives 51 bytes free for each shingle row of the run in hand. It also measured the build of this record again over three runs' rows: 182.8 and 182.9 bytes a row at 4,500,000 and 9,000,000 rows, as recorded here, and 184.0 to 185.6 at 13,500,000, above the 183. The exception of §1 stands, with ADR-221's size: the build's temporary files still grow with the corpus and nothing bounds them. The item of *What this does not decide* that gave the rows of earlier runs to #468 is answered: they stay, and their removal is [#481](https://github.com/algernon28/vespera/issues/481)'s. §2 and §3 are not touched. The Tests note on `EveryStatementThatSortsIsRecordedTest` is corrected in place for #468.

> **Partly amended — see [ADR-224](0224-the-invocation-accounts-counts-by-kind-and-the-two-reads-of-the-embedder-identities-sort-nothing-and-four-of-adr-218s-reads-stay-excepted.md).** Five things below no longer hold as written, as of that record's build. §3's class, which the note above leaves as rows 6, 7, 10 to 13 and 15 to 20, loses rows 15, 19 and 20: the two reads of the embedder identities and the invocation account's three counts by kind or category sort nothing, so the class is rows 6, 7, 10 to 13 and 16 to 18, and "Three of the reads, row 18's and row 20's two, were not measured" holds of row 18 alone. Of the class, rows 6, 7, 10 and 11 stay excepted by ADR-224, each with its reason and a measured size; rows 12, 13 and 16 to 18 are #472's, and ADR-224 decides nothing of them. In Measured's table, row 20's "not measured: in memory at 20,000 and 10,020 rows; row 19's plan" is 14.6 bytes a row for the extraction faults and 24.0 for the cluster faults, at 250,000 and 500,000 rows; row 6's 15.0 is the figure for hashes of 64 bits, and 7.9 to 8.0 where the hashes are the numbers 1 to 100; and row 10's "4.0 to 5.0" is 6.0 to 7.2 with larger seed ids. "Bounding each read of §3 … Not measured" of *Alternatives refused*: ADR-224 records the bounded forms measured for rows 6, 7, 10, 11, 15, 19 and 20. "Rows 18 and 20 at a size where their sort leaves memory" and "The cost of any bounded form" of *What this record does not measure*: row 20 is measured, and so are the bounded forms of those seven rows. And the Tests row's "rows 1, 2, 6 to 13 and 15 to 20": less rows 15, 19 and 20, the test holding no entry for `RelevanceDistribution` or `InvocationAccount`. Everything else in this record stands as the notes above leave it.

> **Partly amended — see [ADR-223](0223-5fs-size-report-6a-and-6b-go-through-one-seed-partition-at-a-time-and-what-is-still-held-says-why.md).** None of this record's decisions is changed; what it lists is fewer. Rows 13, 16, 17 and 18 of *The statements whose temporary files grow with the corpus* no longer exist: 6a and 6b read a partition at a time or by key, in statements that sort nothing. §3's class is rows 6, 7, 10, 11, 12, 15, 19 and 20, and the test holds one statement for `DocumentClusters` and none for `Clusters`, `SynthesisDocs` and `ClusterFaults`.

- **Date**: 2026-10-09
- **Status**: accepted
- **Makes exceptions to**: [ADR-060](0060-survivors-is-a-ledger-owned-item-reader-not-a-view.md)'s bound, in the reading [ADR-211](0211-no-class-holds-every-survivor-of-a-run-another-tables-rows-are-read-a-page-of-survivors-at-a-time.md) records the operator gave it: nothing on the heap or in a statement's temporary storage grows with the archive without a bound. ADR-211 excepted one statement and settled no other. This record excepts the rest that grow, by the operator's choice, each with its measured size, or, for three reads and ten index builds whose rows were too few, or none, to leave memory in the probe, the size of a measured statement of the same plan: stage 4b's build of `shingle_by_hash` (§1), the builds start-up makes of an index a database lacks, as a class (§2), and the reads that sort a row for each survivor, verdict, vector or cluster, as a class (§3).
- **Amends**: ADR-211 in three places. Its sentence *"The temporary files of stage 3's grouping are outside it, by the operator's choice, and nothing else is"* no longer holds: §1, §2 and §3 here are outside it too. The item *"Whether any other statement's temporary storage meets ADR-060's bound"* of its *What this does not decide* is answered by this record. And §12's *"about 3.6 GB"* for stage 4b's build over the 42,833,917 rows on record is the temporary files alone: the write-ahead log grows by as much again while the same statement runs, so the room the build needs is about twice that (§1, §5). ADR-211 carries a banner at its head saying so, and is otherwise not edited.
- **Keeps**: ADR-211's exception for stage 3's grouping, as it stands; [ADR-193](0193-a-statement-sqlite-counts-reports-how-far-it-has-gone-and-one-it-cannot-count-says-how-long-it-took.md)'s forms, which this record does not touch; [ADR-182](0182-stage-2-writes-shingles-without-the-by-hash-index-and-stage-4b-builds-it-before-containment-retrieval-reads-it.md), under which stage 2 drops `shingle_by_hash` and stage 4b builds it whole; [ADR-180](0180-the-database-file-uses-sqlites-write-ahead-log-synced-at-wal-checkpoints.md)'s settings of the write-ahead log.
- **Rests on**: the operator's five answers of 2026-10-09, given through the coordinating session to the session that wrote this record: that stage 4b's build is an exception, recorded with 91.6 bytes a row of temporary files and 183 a row at the peak, the write-ahead log's share stated beside the temporary files'; that the rows of earlier runs are a ticket of their own, [#468](https://github.com/algernon28/vespera/issues/468); that the reads of §3 are one exception as a class, to be looked at again after [#458](https://github.com/algernon28/vespera/issues/458); that start-up's builds are one exception as a class; and that this record is ADR-218. [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) (which run ids a change moves, §6). Throwaway probes over synthetic ledgers built from the shipped `schema.sql` (Measured). No archive and no working directory was opened for this record ([ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md)).
- **Settles** [#466](https://github.com/algernon28/vespera/issues/466).

## Context

ADR-211 moved every temporary file SQLite writes into the working directory, by a setting of the whole process, and made one exception to ADR-060's bound: the temporary files of stage 3's grouping of the shingle rows. It said of every other statement that sorts that whether it meets the bound *"remains to be surveyed"*, and assigned the survey nowhere. #466 is that survey.

Three things were not known when it was opened:

- **Which statements sort.** ADR-193 §6 lists the statements it had in scope and says of some that their plan goes through a temp B-tree. It was not a survey of every statement in `src/main`, and says so.
- **What each writes.** Only two had been measured: stage 3's grouping, and stage 4b's build of `shingle_by_hash`, the second for where its files land and not for whether it meets the bound.
- **What a run needs on the working directory's drive.** ADR-211 §12 gives 3.6 GB for the build over the rows on record. That is its temporary files and nothing else.

## Measured

**Method.** Two throwaway probes outside the repository, on 2026-10-09, in the way ADR-211's were made: Java 26.0.2.1 and the sqlite-jdbc the pom carries (3.53.2.1), the shipped `schema.sql`, the shipped URL's parameters (`foreign_keys=on`, `journal_mode=WAL`, `synchronous=NORMAL`, `journal_size_limit=536870912`), and SQLite's directory for temporary files set once, on a connection to no database, before the database was opened (ADR-211 §12). While each statement ran, the sizes of the files in that directory were summed every 5 ms and the greatest sum kept. Each statement's plan is `EXPLAIN QUERY PLAN`'s, on the same database. `src/test` holds no harness that watches temporary files, and cannot (Tests).

**Ledgers.** One walk of 1,000,000 occurrences and one of 2,000,000, and a smoke run of 20,000. Of all the occurrences, one in ten is `broken` under stage 1, and under stage 2 one in ten is `degenerate-output` and one in twenty `extraction-failed`, so three in four survive: 750,000 and 1,500,000. Five `shingle` rows for each occurrence stage 1 kept, so 4,500,000 and 9,000,000 rows, a tenth of them drawn from a pool of 100 hashes; a signature and sixteen bands for 200,000 and 400,000 of the survivors; a score and a cluster membership for every survivor, 350,000 and 700,000 of them in one partition; a cluster for about every fifteen survivors, nine in ten with a synthesis doc of 2,016 characters; run ids of 64 characters and the granularity `word:5`. A second probe held `shingle` rows alone, at three sizes, 100,000, 4,500,000 and 9,000,000, and watched the write-ahead log and the database file beside the temporary files.

**Disk.** A solid-state NVMe disk, NTFS, the file cache warm. **Every figure in this record is from synthetic ledgers on that disk.**

**Units.** Sizes are in bytes. Where a GiB is given it is 2³⁰ bytes, the unit ADR-211 writes as GB for temporary files.

**Calibration against ADR-211.** The build of `shingle_by_hash` over 4,500,000 rows wrote 411,327,654 bytes of temporary files, which is ADR-211's 392.3 MB to the tenth. Stage 3's grouping over the same rows wrote 104,996,123 bytes against ADR-211's 103,663,916, on a ledger whose hashes fall differently.

### The statements whose temporary files grow with the corpus

Each row is a statement, or a group of statements of one plan. The two sizes are the temporary files at their largest over the smaller and the larger ledger.

| # | Stage | Class and line | What sorts | Its rows grow with | Bytes a row | Measured |
| ---: | --- | --- | --- | --- | --- | --- |
| 1 | 3 | `similarity/DocumentFrequency.java:81` | `GROUP BY` and `count(DISTINCT)` | the `shingle` rows of the stage-2 run | 23.2 to 23.3 | 104,996,123; 209,116,199 |
| 2 | 4b | `similarity/ShingleHashIndex.java:38` | `CREATE INDEX shingle_by_hash` | every row `shingle` keeps, of every stage-2 run | 91.4 to 91.6 | 411,327,654; 824,323,588 |
| 3 | start-up | `schema.sql:373`, `:378` | `CREATE INDEX shingle_by_occurrence`, `shingle_by_run_id` | every row `shingle` keeps | 86.4 to 86.6; 75.0 to 75.1 | 388,679,728; 779,175,805 and 337,279,671; 676,227,643 |
| 4 | start-up | `schema.sql:464` | `CREATE INDEX signature_band_by_bucket` | every row `signature_band` keeps, sixteen a signed occurrence | 86.4 to 86.5 | 276,431,487; 553,919,885 |
| 5 | start-up | every other `CREATE INDEX` of `schema.sql` | the build | the rows its table keeps: one an occurrence, a verdict, a member or a cluster | 71 to 77 where the index is on `run_id`; 2.7 to 24 otherwise | below |
| 6 | 4b | `similarity/RedundancyResolution.java:416` | `GROUP BY` and `count(DISTINCT)` | the `shingle` rows of the run that carry any of one document's rarest hashes | 15.0 | 2,152,424 for 143,810 rows; 4,311,449 for 287,744 |
| 7 | 2, as it ends | `ledger/Verdicts.java:136` | `ORDER BY o.path` | the run's `extraction-failed` verdicts | about 100, with paths of 43 characters and reasons of 49 or 50: the path, the reason and about 7 | 4,992,807 for 50,000; 10,042,816 for 100,000 |
| 8 | 5e, the report | `embedding/RelevanceDistribution.java:181` | `ORDER BY occurrence_id` | the scoring run's scores | 17.0 to 18.3 | 12,725,365; 27,523,396 |
| 9 | 5e | `embedding/RelevanceScoreCache.java:81` | `ORDER BY occurrence_id` | the same | 6.0 to 7.2 | 4,475,356; 10,807,415 |
| 10 | 5f | `embedding/RelevanceScoreCache.java:50` | `DISTINCT`, which sorts every row and not only the distinct seeds | the same | 4.0 to 5.0 | 3,000,027; 7,562,742 |
| 11 | 5f | `embedding/RelevanceScoreCache.java:65` | `ORDER BY occurrence_id` | the members of one partition | 6.0 | 2,088,502 for 350,000; 4,188,514 for 700,000 |
| 12 | 5f | `embedding/DocumentClusters.java:70` | `GROUP BY cluster_ordinal` | the members of one partition | 4.9 to 5.0 | 1,731,815; 3,481,827 |
| 13 | 6a, 6b | `embedding/DocumentClusters.java:55` | `ORDER BY occurrence_id` | the scoring run's cluster memberships | 10.9 to 12.3 | 8,186,365; 18,484,391 |
| 14 | — | struck | — | — | — | ADR-216 removed the statement the probe measured as `embedding/DocumentClusters.java:80`, a `DISTINCT` over the scoring run's cluster memberships at 4.0 to 5.0 bytes a row; the number is kept so that the rows after it keep theirs |
| 15 | 5e, the report | `embedding/RelevanceDistribution.java:172`, `:211` | `DISTINCT embedder_identity` | every row `vector` keeps: every chunk under every embedder, outside any run | 18.0; 9.0, with identities of 15 characters | 3,600,012; 7,200,024 and 1,800,006; 3,600,012, for 200,000 and 400,000 rows |
| 16 | 6a, 6b | `synthesis/Clusters.java:68` | `ORDER BY partition_order, cluster_order` | the arrangement's clusters | 56.4 to 56.6, with labels of about 40 characters | 2,818,744 for 50,020; 5,663,750 for 100,020 |
| 17 | 6b | `synthesis/SynthesisDocs.java:91` | `ORDER BY rowid`, the title and the prose carried through the sort | the run's synthesis docs, each as long as its prose | 2,128, with prose of 2,016 characters | 95,759,630 for 45,000; 191,574,401 for 90,000 |
| 18 | 6b | `synthesis/ClusterFaults.java:92` | `ORDER BY rowid`, the detail carried through | the run's cluster faults | not measured: the sort stayed in memory at 10,020 rows; row 17's plan | — |
| 19 | the invocation account | `pipeline/InvocationAccount.java:208` | `GROUP BY kind` | the verdicts of the invocation's runs | 15.6 | 3,900,015 for 250,000; 7,800,027 for 500,000 |
| 20 | the invocation account | `pipeline/InvocationAccount.java:210`, `:212` | `GROUP BY` | the extraction faults; the cluster faults | not measured: in memory at 20,000 and 10,020 rows; row 19's plan | — |

**Row 2's rows are the table's, not the run's.** The statement has no `WHERE`, and `shingle` keeps the rows of every stage-2 run a working directory has had: ADR-211 §11 notes a copy more each time stage 2's id moves. The 42,833,917 on record is what stage 4b's line stated, `MAX(rowid)` of the table.

**Rows 1 and 6 are planned differently in the two states a working directory is in**: without `shingle_by_hash`, from stage 2's first chunk until stage 4b, and with it, from stage 4b until stage 2 next runs (ADR-182). The bundled SQLite, 3.53.2, was asked for both plans.

- **Row 1 is measured without the index**, which is how stage 3 finds the database whenever stage 2 has run before it under a new id: `SEARCH shingle USING INDEX shingle_by_run_id (run_id=?)`, `USE TEMP B-TREE FOR GROUP BY`, `USE TEMP B-TREE FOR count(DISTINCT)`. With the index built it is `SEARCH shingle USING INDEX shingle_by_hash (run_id=?)` and `USE TEMP B-TREE FOR count(DISTINCT)` alone: the rows come in the grouping's order, and the one temp B-tree left holds the occurrences of one hash at a time. Run so by the second probe, **it wrote no temporary file at 4,500,000 or at 9,000,000 rows, and took 20.6 s and 47.2 s where it took 3.3 s and 6.7 s without the index** over as many rows of the first probe's ledgers. Stage 3 can find the index there: a change to `pipeline` alone moves stage 3's run id and not stage 2's (`StageModules`), so stage 2 does no work and does not drop it. That reading is of `StageModules` and ADR-182, and no invocation was run to see it.
- **Row 6 is measured with the index**, the only state stage 4b reads in, since it builds the index first: `SEARCH shingle USING INDEX shingle_by_hash (run_id=? AND shingle_parameter_identity=? AND shingle_hash=?)`, `USE TEMP B-TREE FOR GROUP BY`, `USE TEMP B-TREE FOR count(DISTINCT)`. Without the index the bundled SQLite still plans it through temporary storage; it is not run in that state and was not measured in it.
- **No class's count of statements that sort differs between the two states**: the test plans every shipped statement in both and holds the same classes and counts for each (Tests). Row 1 is counted in both, its plan keeping one temp B-tree with the index built, though that one wrote no file.

**Row 5, index by index**, the larger ledger's figure, rows and bytes a row: `detected_format_by_run_id` 2,000,000 at 75.1; `extraction_metric_by_run_id` and `extraction_cache_key_by_run_id` 1,800,000 at 74.8; `relevance_score_by_run_id` and `document_cluster_by_run_id` 1,500,000 at 75.2; `verdict_by_run_id` 500,000 at 76.0; `content_hash_by_run_id` and `minhash_signature_by_run_id` 400,000 at 77.0; `superseded_by_by_run_id` 99,999 at 71.7; `verdict_by_occurrence` 500,000 at 23.5; `file_occurrence_by_walk_and_size` 2,000,000 at 12.8; the two indexes by winning seed on `relevance_score` and `document_cluster` 1,500,000 at 9.3; the two on `call_exemplar` 450,000 at 7.9; `synthesis_doc_by_winning_seed_occurrence_id` 90,000 at 7.2; `superseded_by_by_representative_occurrence_id` 99,999 at 6.3; `cluster_by_winning_seed_occurrence_id` 100,020 at 2.7. **Not measured**, their tables holding too few rows or none in these ledgers: `run_by_walk_id`, `run_upstream_by_upstream_run_id`, `walk_anomaly_by_walk_id`, `extraction_fault_by_run_id`, `cluster_fault_by_winning_seed_occurrence_id`, both indexes on `redundant_with`, `unusable_seed_by_run_id`, `relevance_label_by_run_id` and `relevance_label_provenance_by_run_id`.

### Stage 4b's build, with the write-ahead log and the file

| `shingle` rows | Temporary files at their largest | Write-ahead log at its largest | Growth of the database file | The three together at their largest |
| ---: | ---: | ---: | ---: | ---: |
| 100,000 | 8,767,123 | 9,220,592 | 9,146,368 | 18,366,960 |
| 4,500,000 | 411,327,650 | 412,477,952 | 410,054,656 | 822,532,608 |
| 9,000,000 | 824,323,660 | 825,013,552 | 820,187,136 | 1,645,785,772 |

- **At the two larger sizes a row costs 91.4 to 91.6 bytes of temporary files, 91.7 of write-ahead log, and 91.1 of index in the file**, and 182.8 to 182.9 at the peak: two of the three at once, first the temporary files with the log, then the log with the file's growth. A second run of the build over the larger ledger, made for the measurement of stage 3 with the index present, peaked at 1,646,931,132 bytes, 183.0 a row, so the range over the runs made is 182.8 to 183.0.
- **At 100,000 rows the figures are 87.7, 92.2 and 91.5, and 183.7 at the peak**, above §5's 183: what a sort keeps in memory before it writes, and the fixed parts of the log and of the file, show at that size and not at the larger two. §5's figure is the larger sizes'.
- **The log stays its full size when the statement returns.** It was cut to 536,870,912 bytes, the shipped `journal_size_limit`, by the next write on that connection at the larger size, and stood at its 412,477,952 after the next write at the smaller, which is under the limit. It was empty once the connection closed.
- **Stage 3's grouping wrote 37,112 bytes of log** at all three sizes, and grew the file by nothing: its temporary files are all it needs.
- The `shingle` table with its two shipped indexes took 277.5 to 278.0 bytes a row of database file.

### What sorts and is bounded by something other than the corpus

| Stage | Class and line | Bounded by | Measured |
| --- | --- | --- | --- |
| 3 | `similarity/DocumentFrequency.java:139`, `:154`, `:169` | one page: the `shingle` rows of at most 1,000 occurrences (ADR-211 §3) | a temp B-tree in the first two plans and a list for `IN (SELECT …)` in the third; no file at 5,000 rows |
| 5c, 5d | `embedding/UnusableSeeds.java:54` | the seed set | a temp B-tree in the plan; not run over rows |

### What was planned and keeps nothing in temporary storage

**Carrying `ORDER BY` or `DISTINCT`, planned by the probe**: `ledger/Verdicts.java:26` and `:52` and `ledger/Occurrences.java:105`; `RedundancyResolution.java:138`; `SynthesisDocs.java:118`; `ChunkCache.java:31`, `LeadingChunks.java:46`, `DocumentTitles.java:32`, `VectorCache.java:77`; `RelevanceLabels.java:137` and `:160`; `Runs.java:143`.

**Carrying neither, planned by the probe because their rows grow with the corpus**: `ledger/Verdicts.java:164`, the count of a run's survivors; `RedundancyResolution.java:243`, the signature bands, and `:453`, the document frequencies; `BoilerplateShingles.java:54`.

**Carrying `ORDER BY`, not planned by the probe and planned by the test** (Tests), which finds no temporary storage in them: the three reads of `ledger/Walks.java` that end `ORDER BY id DESC LIMIT 1`, and `ledger/Verdicts.java:289`. Their rows are the walks of one root and a run's upstream runs.

No statement of `src/main` is a `UNION`, a window function or a common table expression.

### Large statements that do not sort

No temporary file was seen during the drop of `shingle_by_hash` (`ShingleHashIndex.java:35`), nor during three of the deletes a step makes of its own unfinished work: 6,400,000 rows of `signature_band`, 1,800,000 of `extraction_metric`, 300,000 of `verdict`.

## Decision

### 1. Stage 4b's build of `shingle_by_hash` is an exception to ADR-060

**Its temporary files grow with the rows `shingle` keeps, 91.6 bytes a row, and nothing bounds them.** That is the operator's choice, as stage 3's grouping was. The build stays one statement, as ADR-182 has it and ADR-193 reports it.

**The row count is the table's and not the run's** (Measured, row 2). Whether the build should cover only the run stage 4b reads, or the rows of earlier runs should go, is [#468](https://github.com/algernon28/vespera/issues/468)'s and is not decided here.

**The write-ahead log's share is stated beside the temporary files'**: 91.7 bytes a row more, in the same directory, while the same statement runs, and it outlasts the statement (Measured). ADR-211 §12's 3.6 GB is the temporary files alone. It is right as far as it goes, and it is half of what the drive needs.

### 2. A build start-up makes of an index a database lacks is an exception, as a class

`schema.sql` runs at every start, and each `CREATE INDEX IF NOT EXISTS` in it builds its index whole over the rows already there when the database lacks it: after an upgrade that adds an index, and on no other start (ADR-173, ADR-187). Each such build sorts every row of its table. **All thirty-one are excepted together**, with the sizes of Measured, rows 3 to 5: at most 86.6 bytes a row of `shingle` and 86.5 a row of `signature_band`, and 77 or less a row elsewhere. **Twenty-one of them were measured. The other ten were not**, their tables holding too few rows or none in the probe's ledgers, and each is recorded at the size of a measured build of the same shape: 77 or less a row for the six on a run's id, 24 or less for the four on an integer column. An index added to `schema.sql` later joins the class when a record lists it, and the test that holds the list fails until one does (Tests).

### 3. A read that sorts a row for each survivor, verdict, vector or cluster is an exception, as a class

**Rows 6 to 13 and 15 to 20 of Measured are excepted together**, row 14's statement being gone. Each grows with the corpus and none is bounded. Three of the reads, row 18's and row 20's two, were not measured, their sorts staying in memory at the probe's sizes: each is recorded at the size of the measured statement of its plan, row 17 and row 19. They are small beside the build: 18.3 bytes a scored survivor at most, about 100 for each file stage 2 could not read, and for row 17 the length of the run's prose.

**The class is to be looked at again after #458.** Several of these reads hand every row to Java, and whether such a read stays at all is that ticket's question about the heap. Bounding a statement's sort before that is settled could be work on a statement that then goes.

### 4. What needs no exception

The statements of *What sorts and is bounded by something other than the corpus* meet ADR-060 as they stand: a page of 1,000 occurrences, and the operator's seed folder. Those of *What was planned and keeps nothing in temporary storage* write no temporary file.

### 5. What the working directory's drive needs free

**183 bytes for each row `shingle` keeps**, beyond the database as it stands before stage 4b builds its index:

> free bytes ≥ 183 × S, where S is the number stage 4b's line states, `MAX(rowid)` of `shingle`

The 183 is the greatest peak measured at the two larger sizes, 182.99 a row, rounded to a whole byte, and carries no margin; the run at 100,000 rows peaked above it, at 183.7 (Measured).

Of the 183, 91.1 stay in `vespera.db`, as the index; the rest is given back. When stage 2 next drops the index those bytes stay in the file as free pages, nothing in `src/main` setting `auto_vacuum`, and whether a later build takes them up again, so that the file does not grow a second time, was not measured. The write-ahead log gives its share back at the next write, down to 512 MiB, or when the invocation ends.

The statements of an invocation run one at a time, so the need is the largest statement's and not their sum, and no other statement comes near the build's. Stage 3's grouping needs 23.3 × the run's rows. A start-up build of `shingle_by_occurrence` would need as much as stage 4b's, by its temporary files; its log was not measured.

**Worked for the 42,833,917 rows on record:**

| | Bytes | GiB |
| --- | ---: | ---: |
| At the peak, 183 × S | 7,838,606,811 | 7.30 |
| Of which temporary files, 91.6 × S | 3,923,586,797 | 3.65 |
| The index that stays, 91.1 × S | 3,902,169,839 | 3.63 |
| Stage 3's grouping, were every row one run's, 23.3 × S | 998,030,266 | 0.93 |

### 6. Which run ids move

**None.** A stage's implementation version is the last commit touching `src/main/java/io/algernon/vespera/<module>` for a module `StageModules` names for it (ADR-058). This record changes nothing under `src/main`: it adds this file, one test class, a constant in `Adr.java`, the index's row, a banner on ADR-060, a banner on ADR-211 and lines of `AGENTS.md`. `StageModules` was read for this and is not edited. No DDL, no schema version, no cache key.

## Alternatives refused

- **Bounding stage 4b's build.** One `CREATE INDEX` cannot be sliced. Bounded, it would be several partial indexes over ranges of the hash, each build reading every row of the table, which ADR-211 measured at about 0.55 s for 4.5 million rows a slice on a solid-state disk, and the containment read would have to name a range in its text for SQLite to use one. That changes `similarity`, which moves the run ids of stages 2 to 6b. No bounded form was built or timed for this record.
- **Bounding each read of §3**, by an index that already gives the order or by taking the `ORDER BY` out. Not measured, and set aside until #458 says which of the reads stay.
- **Temporary storage kept in memory.** ADR-211 refused it: the size of the build's sort, outside the Java heap.

## Consequences

- **A run over an archive the size of the one on record needs about 7.8 billion bytes free on the working directory's drive when stage 4b starts**, where ADR-211 said 3.6 GB. Nothing in the code checks for it or says so to the operator; a line that did would be a change to `pipeline` or `similarity` and is not made here.
- **ADR-060's bound now has three exceptions on record and one before them**, all on disk and none on the heap: ADR-211's grouping, and §1, §2 and §3 here. ADR-060's banner says so.
- **A class of `src/main` that gains a statement the test reads as sorting, and an index added to `schema.sql`, fail a test until this record or a later one has them.** What the test does not read is listed under Tests, and a sorting statement of one of those kinds passes it.
- **Nothing an operator sees changes.**

## Tests

| Class | What it holds |
| --- | --- |
| `EveryStatementThatSortsIsRecordedTest` | that the shipped statements SQLite plans through a temp B-tree, a materialised subquery or a list for `IN (SELECT …)`, and the one that builds an index, are in the classes this record names, as many in each as it lists: rows 1, 2, 6 to 13 and 15 to 20, and the four that are bounded; that this holds in both states of a working directory, as `schema.sql` leaves the database and with `shingle_by_hash` built by the statement `ShingleHashIndex` ships, which since ADR-221 is built for one run, the test putting a run id of the minted form where the statement joins one in, so that the second state is a database holding another run's index, and an index build is counted without being planned (corrected for [#468](https://github.com/algernon28/vespera/issues/468)); that the only texts opening as a statement does that it cannot plan are six read by hand, five reading the least or greatest rowid of a table named at run time and one the message of an exception, none of them a statement that sorts; and that `schema.sql`'s indexes are the thirty-one of rows 3 to 5. A text is read as a statement where it opens, in either case and after any white space, with `SELECT`, `INSERT`, `DELETE`, `UPDATE`, `REPLACE`, `WITH`, `CREATE INDEX`, `CREATE UNIQUE INDEX`, `DROP INDEX` or `CREATE TABLE`, the last with `TEMP` or `TEMPORARY` too |

**What no test holds.**

- **Any size.** A test of the suite cannot watch the process's temporary files: the directory is one setting of the whole JVM, which the contexts cached across test classes share (ADR-211 §12), and the figures need millions of rows.
- **That a recorded statement still sorts as measured over rows.** The test plans against empty tables and runs no statement. A working directory has no statistics table either, nothing in `src/main` running `ANALYZE`, but its tables hold rows, and the test's database is like one only in its schema and, for the second planning, in the index.
- **Which statement sorts.** The test holds how many statements of a class sort, so one sorting statement put in the place of another in the same class passes.
- **A statement completed at run time whose first constant is a statement on its own.** Text appended to it by a builder or by a second literal is not in that constant, so it is planned without its tail, and an `ORDER BY` added there is not seen.
- **A statement that opens with none of the keywords the test reads, and one read from a file.**
- **A list of more than two.** A list of placeholders joined in at run time is planned as two values.
- **The line numbers in this record's tables.** The test holds classes and counts.
- **The write-ahead log's share**, and the free-space figure.

## What this record does not measure

- Anything on a spinning disk, on `H:`, on Linux, or with the file cache empty.
- A temporary file that lived under 5 ms.
- The write-ahead log's growth for any statement but stage 4b's build and stage 3's grouping. For a start-up build it is taken to be as the build's.
- The build through the application's own pool and `StatementSteps`: the probe used one plain connection.
- Rows 18 and 20 at a size where their sort leaves memory, and the ten indexes row 5 names as not measured.
- What caps how many rows carry a document's rarest hashes in row 6. The probe's 32 hashes were its own choice and not the configured sample; `rarestHashes` was not read.
- The size of row 6 without `shingle_by_hash`, a state it is not run in, and the time of row 1 with the index on any other disk.
- Whether the database file grows again when the index is built a second time, after stage 2 has dropped it.
- The ledger's page statements with the real list of blocking kinds: the probe named three.
- Vectors and signatures at their real size: the probe's were 16 bytes, where a vector is up to 16 KiB and a signature 512 bytes. Neither is in a sorted key.
- The cost of any bounded form.

## What this does not decide

- **Whether `shingle` should keep the rows of earlier stage-2 runs, and whether the build should cover them**: [#468](https://github.com/algernon28/vespera/issues/468).
- **Whether any read of §3 stays as it is**: after [#458](https://github.com/algernon28/vespera/issues/458), which is about what those reads put on the heap.
- **Whether an invocation should check the drive's free space, or say what it needs, before stage 4b builds.**
- **Whether stage 3's grouping should ever run with `shingle_by_hash` present.** In the probe it then wrote no temporary file and took six to seven times as long (Measured). This record measures that and decides nothing about it, no ticket holds it, and whether the 45 steps a row ADR-211 declares for that statement's progress hold under that plan was not looked at.
- **Whether the progress callbacks keep coming on a slow disk**, which ADR-211 left open and this record does not take up.
