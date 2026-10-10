# ADR-221 — `shingle_by_hash` is an index on the rows of the run in hand, stage 4b builds it again where it is not that run's, and an earlier run's rows stay

> **Partly amended — see [ADR-225](0225-stage-4b-reads-its-candidate-pairs-and-containment-candidates-a-thousand-at-a-time-and-shingle-by-hash-ends-in-the-occurrence.md).** As of ADR-225's build the index's columns below, and every figure that follows from them, no longer hold. The index gains the occurrence as its third column: `CREATE INDEX shingle_by_hash ON shingle (shingle_parameter_identity, shingle_hash, occurrence_id) WHERE run_id = '<the stage-2 run id>'`. So §1's statement and its *"The index does not hold `occurrence_id`"*, P1's text and `TheRunsHashIndex`'s are that one; the build takes 13 steps for a row of the run where Steps, §5 and P2 say 12, its share at the end is 3/13 + 10/13 × R / T, a callback is 7,693 rows and the bound of §5's last line 10,000; a row of the run costs about 27 bytes of temporary files, 28 of write-ahead log and 27 to 28 in the file, and §6's 51 bytes free a row is 58. §2's *"`RedundancyResolution`'s statement is not changed"* no longer holds: containment retrieval is one read for each hash, the run still a bound value. And the fifth row of Plans, that one occurrence's shingles are read through `shingle_by_occurrence`, holds only because each such read now names that index: left to choose, it is drawn to the index with its third column. What this record decides of whose index it is, how stage 4b tells, the run id in the statement, the build's lines and the rows of earlier runs stands.

- **Date**: 2026-10-10
- **Status**: accepted.
- **Built**: P1 to P6 of *What is to be built* were built on 2026-10-10 in the change that carries this record (#468), after the commit that wrote it and its tests; fifteen tests were red between the two, and P7 was made once the method existed.
- **Amends**: [ADR-182](0182-stage-2-writes-shingles-without-the-by-hash-index-and-stage-4b-builds-it-before-containment-retrieval-reads-it.md) in what `shingle_by_hash` is and when stage 4b builds it. Its header's *"Its name, `shingle_by_hash`, and its columns, `(run_id, shingle_parameter_identity, shingle_hash)`, are unchanged"*: the name is, the columns are not (§1). §1's *"over every row the table then holds"*. §2.3's statement, and its *"`IF NOT EXISTS` is the whole of the check. The step builds whenever the index is missing and builds nothing when it is present, whoever left it there"* (§3). §2.4's first line (§5). §3's *"through `IF EXISTS` and `IF NOT EXISTS`"*, the sixth row of its table in its *"over every row in the table"*, and *"The index is one per table, over every run's rows"* (§3). §4's *"How many rows the build covers"* and its estimates, which are of the build over every run's rows. Its Consequences' *"Stage 4b pays the build once per stage-2 run that writes, over every row in the table"*. And the item *"A partial index per run"* of its *What this does not decide*, which this record decides, with that item's *"which containment retrieval's bound `run_id = ?` does not show at prepare time, so the query would have to change to carry the run id as a literal"*, which is not so (§2). What ADR-182 says of stage 2's drop, of where the build is made, of its own transaction, and of `shingle_by_run_id`, stands.
- **Amends**: [ADR-218](0218-every-statement-whose-temporary-files-grow-with-the-corpus-is-an-exception-to-adr-060-with-its-size.md) in the size of its first exception and in nothing else. Row 2 of its table of statements and the sentence under it, *"Row 2's rows are the table's, not the run's"*; §1's *"91.6 bytes a row"*, *"The build stays one statement, as ADR-182 has it"* and *"The row count is the table's and not the run's"*; §5's *"183 bytes for each row `shingle` keeps"*, its formula and its worked table. Those are of the build over every run's rows, which this record replaces. Measured again for this record over three runs' rows, that build wrote 184.0 to 185.6 bytes a row at 13,500,000 rows, above the 183 ADR-218 records from 4,500,000 and 9,000,000 (Measured). The exception itself stands: the build's temporary files still grow with the corpus and nothing bounds them (§6). ADR-218's §2 and §3 are not touched.
- **Amends**: [ADR-193](0193-a-statement-sqlite-counts-reports-how-far-it-has-gone-and-one-it-cannot-count-says-how-long-it-took.md) in what it says of stage 4b's build. §4.2's line before the build (§5 here gives the new one). The 11 steps a row it records for `shingle_by_hash`, in its table of measured statements and in §6's row for `ShingleHashIndex.build`, with §2's *"9,090 rows of the 4b build at 11 steps a row"* and *"Over 42,833,917 rows the build calls back about 4,700 times"*, and §4's *"9,090 rows short of it at most, for 4b's build"*: the build declares 12, the most a row takes, and takes 3 for a row of another run, so one callback is 8,334 rows at most (§5). And §4's form for a build's progress line, `about X% of N rows`, which for this one build becomes `at least X% of N rows` (§5). Its forms for every other statement, its interval, its cadence and its line that the rows are gone through stand.
- **Amends**: [ADR-219](0219-stage-3s-grouping-names-the-index-on-the-run-and-the-clause-ships-with-the-next-change-to-similarity.md) in one sentence of its Sequences, *"The index is one for the table, not one for a run or a walk"*: there is one index of that name in the database, and it is over one run's rows. Its sequences stand as [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md) leaves them, which answers the first of them no since content census stopped naming `pipeline`: an index of that name is in the database wherever ADR-219 found one. Its clause has shipped (ADR-220) and is not touched.
- **Amends**: [ADR-211](0211-no-class-holds-every-survivor-of-a-run-another-tables-rows-are-read-a-page-of-survivors-at-a-time.md) §12 and its *What this does not decide*, in the figure *"about 3.6 GB"* for stage 4b's build over the 42,833,917 rows on record, which ADR-218 had already read as half of what that build needed: it is of the build over every run's rows. And the banner [ADR-060](0060-survivors-is-a-ledger-owned-item-reader-not-a-view.md) carries for ADR-218, in its *"91.6 bytes of temporary files for each row `shingle` keeps"*.
- **Amends**: [ADR-204](0204-every-line-of-adr-193s-part-b-is-written-out-and-its-table-is-read-again-against-the-code.md) §4, in one figure of its table of enums: *"`SHINGLE_HASH_INDEX_BUILD` (11)"* for `similarity.SimilarityStatement`. The constant declares 12 (§5, P2). Nothing else of ADR-204 names the build's steps or its lines.
- **Amends**: [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md) in one sentence of its Consequences, *"It still meets it when it was stopped over one corpus root while another reached stage 4b, which is the sequence ADR-219's clause, shipped with ADR-220's change, is now for"*. In that sequence stage 3 still finds an index of that name in the database, and it is the other root's run's: SQLite may not answer the grouping of another run through it, and the grouping is not drawn to an index of this form even where it is its own run's (Plans, the fourth row). So the clause changes no plan in that sequence either, and no sequence is left that it is for; it stays, and costs nothing (Consequences). What ADR-222 decides is not touched.
- **Amends**: [ADR-224](0224-the-invocation-accounts-counts-by-kind-and-the-two-reads-of-the-embedder-identities-sort-nothing-and-four-of-adr-218s-reads-stay-excepted.md) in what it says of `shingle_by_hash` and its build, which it wrote of ADR-182's index, this record not being on main then. Its Calibration's *"The build of `shingle_by_hash` over 4,500,000 rows wrote 411,327,644 bytes of temporary files"* is of the build over every run's rows, which this record measured again at 411,327,650 (Measured). Row 6's *"a search of that index on the run, the granularity and the hash"*: the index is on the granularity and the hash, over the rows of the run in hand alone, and the plan is otherwise the one ADR-224 gives (Plans, the first row). Row 6's *"As shipped"*, with its 15.0 bytes a row and its times, and the *"where the shipped build writes 91.4"* of form B's row and of its §4, were measured with ADR-182's index and of ADR-182's build; the build that ships with this record is §1's, at 23.3 to 24.4 bytes of temporary files a row of the run. The containment read's own figures were not measured again under §1's index. §4's *"form A's cost, and the build's, grow with the `shingle` rows of earlier stage-2 runs, and whether those rows are kept is #468's"*: the rows are kept (§7), the room the build needs no longer grows with them, and its time grows by the reading of them alone (Measured); form A's cost was not looked at here. §4's *"so ADR-218 §1's 91.6 and §5's 183 would move"* and its Keeps' *"ADR-218 §1, §2 and §5, its 91.6 and its 183 bytes a row"*: those two figures are of the build this record replaces, as the bullet on ADR-218 above says. ADR-224's four exceptions, its §1 and its sequencing of form C after #468 stand.
- **Keeps**: ADR-182's drop by stage 2, as it stands, with [ADR-187](0187-a-database-statement-that-can-take-minutes-says-so-before-it-starts-and-when-it-ends.md)'s two lines for it; [ADR-081](0081-minhash-retrieves-shingle-sets-judge-128-permutations-in-16-bands-and-containment-gets-its-own-index.md)'s containment retrieval, its statement unchanged to the character; [ADR-156](0156-a-runs-survivors-are-read-through-its-upstream-runs-and-a-verdict-under-any-other-run-stays-recorded-and-removes-nothing.md), under which a run an invocation arrives at again finds its work recorded; [ADR-219](0219-stage-3s-grouping-names-the-index-on-the-run-and-the-clause-ships-with-the-next-change-to-similarity.md)'s clause; [ADR-220](0220-no-class-holds-every-occurrence-of-a-run-stage-2s-resume-the-census-stage-4b-and-stage-5e-read-a-page-at-a-time-or-ask-by-key-and-what-is-still-held-says-why.md), read once for this record, no sentence of which this record makes untrue.
- **Rests on**: the coordinating session's choices of 2026-10-10. On that day the operator handed every decision on [#468](https://github.com/algernon28/vespera/issues/468) to the coordinating session, in the words *"you take all decisions on #468"*, and gave the number ADR-221. The six choices under *Who decided* are that session's, made on the measurements of this record. They are not the operator's own answers. [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) (which run ids a change moves, §8). SQLite's own documentation (§2). Throwaway probes over synthetic ledgers built from the shipped `schema.sql` (Measured). No archive and no working directory was opened for this record ([ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md)): how many runs' rows the operator's database holds is not known to any agent.
- **Decides** [#468](https://github.com/algernon28/vespera/issues/468). Leaves the removal of an earlier run's rows to [#481](https://github.com/algernon28/vespera/issues/481).

**An *index* here is always a SQLite index on a table.** It is never the ledger. *The run in hand* is the stage-2 run the invocation arrives at, whose shingle rows stage 4b reads.

## Who decided

The coordinating session, on 2026-10-10, under the operator's hand-over quoted above:

1. `shingle_by_hash` becomes an index on the rows of the run in hand, one index under the fixed name, built by stage 4b where it is absent or is not that run's.
2. Containment retrieval keeps the run as a bound value, and a test pins the plan.
3. Stage 2's drop stays as it is.
4. The rows of earlier runs stay, their cost is stated, and their removal is #481's.
5. The free-space figure is restated for the new build, and ADR-218's is amended by name.
6. `AnEarlierRunsRowsStayInvocationTest` lands with this record.

What this record's author settled within them is in §1, §3, §4 and §5: the index's columns, how the run id enters the statement and which ids are refused, how an index is told to be a run's, and what the build's lines say.

## Context

ADR-182 has stage 4b build `shingle_by_hash` on `(run_id, shingle_parameter_identity, shingle_hash)` with no condition, so over every row `shingle` holds. Nothing in `src/main` deletes a row of `shingle`: every stage-2 run a working directory has had leaves its rows there (Measured, *What is kept*). ADR-218 measured the build at 183 bytes a row of free space and said the row count is the table's. So the room a run needs at stage 4b, and the time the build takes, grow with the number of stage-2 runs kept as well as with the corpus. #468 asked what to do about that.

Containment retrieval is the index's one reader, and it reads one run's rows.

## Measured

**Method.** Two throwaway probes outside the repository, on 2026-10-10, made as ADR-218's were: Java 26.0.2.1 and the sqlite-jdbc the pom carries (3.53.2.1, SQLite 3.53.2), the shipped `schema.sql`, the shipped URL's parameters (`foreign_keys=on`, `busy_timeout=300000`, `journal_mode=WAL`, `synchronous=NORMAL`, `journal_size_limit=536870912`), and SQLite's directory for temporary files set once, on a connection to no database, before the database was opened. While each statement ran, the sizes of the files in that directory, of the write-ahead log and of the database file were read every 5 ms and the greatest kept. Steps were counted by SQLite's progress handler.

**Ledgers.** `shingle` rows alone, fifty to an occurrence, a tenth of them drawn from a pool of 100 hashes and the rest random, the granularity `word:5`, run ids of 64 characters. Each run's rows are written together, one run after another.

**Disk.** A solid-state NVMe disk, NTFS, Windows 11, the file cache warm. **Every figure in this record is from synthetic ledgers on that disk.** Sizes are in bytes.

### What is kept

`AnEarlierRunsRowsStayInvocationTest` drives whole invocations over 48 texts in one working directory and counts rows by run. Each of the first three stage-2 runs wrote 1,200 shingle rows; the fourth, over 49 texts, wrote 1,225.

| What happened between two invocations | Stage-2 runs over the root | Rows in `shingle` |
| --- | ---: | ---: |
| nothing yet: the first invocation | 1 | 1,200 |
| a build that moves `pipeline` alone | 1 | 1,200 |
| the boilerplate floor changed in the profile | 1 | 1,200 |
| a build that moves `similarity` | 2 | 2,400 |
| `extractionAttempt` raised in the profile | 3 | 3,600 |
| the value put back | 3 | 3,600 |
| the first build run again | 3 | 3,600 |
| one text added to the archive | 4 | 4,825 |

- **Every new stage-2 run writes a whole copy**, the last one included: one text added has the whole corpus's rows written again under a run over the new walk, 1,225 of them.
- **A build that moves `similarity` converted nothing again**: the conversions are cached outside the run, and the rows are written from them.
- **A build that moves `pipeline` alone added nothing**: no shingle row, and no run of stage 4, whose one run kept its 768 `signature_band` rows. Neither stage 2 nor stage 4 names `pipeline`, stage 4 since [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md) took it out of content census, content redundancy and arrangement. When this table was first run, before that record merged, such a build minted a second stage-4 run.
- **A changed boilerplate floor left `shingle` as it was and added a second stage-4 run's `signature_band` rows beside the first's.** The floor is the value stage 4's run records, and the stage-2 run is the same. It is what now mints a stage-4 run without a stage-2 run; no module does, `similarity` and `extraction` being named by both.
- **Putting a value back, and running the first build again, arrive at a run already finished**: no run is minted, nothing is converted and nothing is written. That is ADR-156's put-back, and it is why a run's rows cannot simply be removed when another run of its stage is minted (§7).
- **With a run's shingle rows deleted by the test**, a later run of stage 4 over that stage-2 run signs nothing, removes nothing, and ends with exit code 0. The second test of that class holds it: no `minhash_signature` row, no `REDUNDANT_WITH` verdict and no `redundant_with` row under that run.

Read in the code and not run: a build moving `extraction` or `corpus`, a changed `degenerateOutputConfidenceFloor` and a changed extractor identity each mint a stage-2 run the same way (`StageRuns.extraction`).

### The build over the rows of one run

The statement of §1, for a run of R rows in a table of T. Two rounds each, the index dropped between them, the table read once before each so that the file cache is warm. The peak is the temporary files, the write-ahead log and the growth of the database file at their greatest together.

| R, rows of the run | T, rows in the table | Temporary files | Write-ahead log | Peak, first round | Peak a row of the run | Time, two rounds |
| ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 1,500,000 | 1,500,000 | 35,015,159 | 36,400,232 | 72,580,200 | 48.4 | 0.98 s, 0.97 s |
| 4,500,000 | 4,500,000 | 105,111,259 | 109,188,272 | 217,732,272 | 48.4 | 2.95 s, 3.04 s |
| 9,000,000 | 9,000,000 | 209,842,812 | 219,291,152 | 437,296,656 | 48.6 | 6.06 s, 5.90 s |
| 1,500,000 | 15,000,000 | 35,015,159 | 36,400,232 | 72,580,200 | 48.4 | 1.69 s, 1.67 s |
| 4,500,000 | 15,000,000 | 105,144,154 | 109,188,272 | 212,297,146 | 47.2 | 3.41 s, 3.43 s |
| 9,000,000 | 15,000,000 | 215,875,699 | 225,112,712 | 438,846,011 | 48.8 | 6.30 s, 6.24 s |
| 4,500,000 | 13,500,000 | 109,644,148 | 113,876,832 | 227,082,080 | 50.5 | 3.53 s, 3.74 s, 3.70 s |

The table of 15,000,000 rows holds three runs, of 1,500,000, 4,500,000 and 9,000,000 rows, written in that order. The last row is of an earlier probe of the same day: the third of three runs of 4,500,000 rows each, three rounds.

- **A row of the run costs 23.3 to 24.4 bytes of temporary files, 24.3 to 25.3 of write-ahead log, and 24.1 to 25.2 of index in the file**. The first two ranges are over the seven rows of the table. The third is over five of them: the index's share is the growth of the file, and it is the whole index only where the build found no freed pages to write into, which is the first four rows, at 36,179,968, 108,544,000, 218,005,504 and 36,179,968 bytes, and the last, at 113,205,248. In the fifth and sixth rows the first round grew the file by 72,364,032 and 115,249,152 bytes, which is not the index's size. Read from the figures and not observed, the free list not being printed there: the rest went into pages the drop before had freed, and for the fifth row the two, 72,364,032 and the 36,179,968 that drop freed, sum to the second row's 108,544,000.
- **At the peak, 46.1 to 50.5 bytes a row of the run**: 47.2 to 50.5 over the first rounds shown, and down to 46.1 in a second round, which writes the index into the pages the drop freed and grows the file by nothing.
- **The size does not depend on the other runs' rows.** The run of 1,500,000 rows wrote the same temporary files and the same log, to the byte, alone and among 13,500,000 rows of other runs.
- **It rises a little with the row numbers of the run's rows.** The run of 9,000,000 rows wrote 23.3 bytes a row of temporary files where its rows are the table's first and 24.0 where they follow 6,000,000 others; the run measured after 9,000,000 others wrote 24.4.
- **The time is 0.65 to 0.68 s for each million rows of the run where it is alone, and the scan of the other runs' rows shows**: 0.04 to 0.06 s more for each million rows of other runs, the build reading every row of the table to find the run's. Reading the 15,000,000 rows and nothing else took 0.93 to 0.96 s.
- **The write-ahead log stays its size when the statement returns**, as ADR-218 found of the other build; none here passed the 536,870,912 bytes at which the next write cuts it.

### The build over every run's rows, measured again

ADR-182's statement, over one, two and three runs of 4,500,000 rows each.

| Rows in the table | Temporary files | Write-ahead log | Peak | Peak a row of the table | Time |
| ---: | ---: | ---: | ---: | ---: | --- |
| 4,500,000 | 411,327,650 | 412,440,872 | 822,470,952 | 182.8 | 6.55 s |
| 9,000,000 | 824,323,652 | 824,976,472 | 1,646,510,884 | 182.9 | 25.02 s, one run, the first statement after the file was copied |
| 13,500,000 | 1,240,184,171 | 1,246,782,072 | 2,486,293,112 | 184.2 | 19.08 s to 21.02 s over three warm rounds; the sizes shown are of another build of the same day, which took 31.06 s |
| 13,500,000, of which 4,500,000 written into pages a delete had freed | 1,244,717,066 | 1,256,171,552 | 2,505,017,376 | 185.6 | 31.40 s, one run |

- **ADR-218's 183 is per row the table keeps, confirmed at its two sizes and exceeded at the third**: 182.8 and 182.9, then 184.0 to 184.2 over five builds at 13,500,000 rows, and 185.6 where a run's rows are not contiguous.
- **For one run in hand among three of equal size the new build needs 227,082,080 bytes where the old needed 2,486,293,112**, and takes 3.5 to 3.7 s where the old took 19.1 to 21.0 s. The 2,486,293,112 and the times are not of one build: the peak was read in the first probe's build of 31.06 s (`probe468-shingle-4500000.log`, line 20), and the three times are of three other rounds over the same rows.
- A second build of the old form into the pages a drop had freed grew the file by nothing, which ADR-218 lists as not measured.

### Steps

Counted every step, over small tables, and every 100,000 over the ledgers above.

| Rows of the run | Rows of another run | Steps |
| ---: | ---: | ---: |
| 1,000 | 0 | 12,346 |
| 3,000 | 0 | 36,346 |
| 1,000 | 2,000 | 18,346 |
| 1,000 | 6,000 | 30,346 |
| 3,000 | 6,000 | 54,346 |
| 0 | 3,000 | 9,346 |
| 0 | 1,000 | 3,346 |

**The build takes 12 steps for a row of the run and 3 for a row of any other run**, and 346 besides, in each of the seven. Over the ledgers the handler was called 180, 540 and 1,080 times for the three runs alone, which is 12.00 a row, and 585, 855 and 1,260 times for the same runs among 15,000,000 rows, which is 12 for each row of the run and 3 for each other row exactly.

### Plans

Each read with its values bound before the plan was asked for, as `JdbcTemplate` sends it.

**Which probe printed them, and under which name.** The first five rows are the first probe's (`probe468-shingle-4500000.log`, lines 32 to 39). Its index had §1's columns and condition and was named `shingle_by_hash_of_run`, so that probe printed `USING INDEX shingle_by_hash_of_run`; the table writes the name the index ships under. The second probe printed no plan. The sixth row is in neither probe's log: it is the plan `ShingleIndexesInTheSchemaTest` reads against the bundled SQLite, over empty tables, where the index has the shipped name, and that class holds the first, second and fourth rows under that name too.

| Statement | Index in the database | Plan |
| --- | --- | --- |
| containment retrieval, `run_id = ?` bound to the run | the run's | `SEARCH shingle USING INDEX shingle_by_hash (shingle_parameter_identity=? AND shingle_hash=?)`, then the two temp B-trees it always had |
| the same | another run's | `SEARCH shingle USING INDEX shingle_by_run_id (run_id=?)`: every row of the run, for each call |
| the same, the run written into the text | the run's | as the first row |
| stage 3's grouping without ADR-219's clause | the run's | through `shingle_by_run_id`, as with no index: it is not drawn to this index, as it was to ADR-182's |
| one occurrence's shingles, as stages 4a and 4b read them | the run's | through `shingle_by_occurrence` |
| the same asked for `DISTINCT`, which nothing ships | the run's | `SEARCH shingle USING INDEX shingle_by_hash (shingle_parameter_identity=?)`: every row of the run, the cost #277 took out |

2,000 containment reads took 98.2 s through the run's index with the run bound, 99.6 s with the run written into the text, and 98.2 s through ADR-182's index over the same three runs. The probe's 32 hashes for each read were an occurrence's first 32 and not its rarest, about three of them from the pool of 100, so each read fetched thousands of rows: the three times compare the indexes and say nothing of what a read costs in a run.

### Two corpus roots taking turns, and the index of an earlier build

In the table of 15,000,000 rows, the index being one run's and the other run's stage 4b dropping it and building its own, four turns:

| Turn | Drop of the other run's index | Build of this run's |
| ---: | ---: | --- |
| 1 | 0.06 s | 6.21 s over 9,000,000 rows |
| 2 | 0.12 s | 3.42 s over 4,500,000 rows |
| 3 | 0.06 s | 6.28 s over 9,000,000 rows |
| 4 | 0.11 s | 3.47 s over 4,500,000 rows |

The database file stayed 4,355,403,776 bytes through the four: each build went into the pages the drop before it freed.

An index of ADR-182's form over the same 15,000,000 rows, freshly built, took 0.59 s to drop and freed 334,113 pages; the run's index built after it grew the file by nothing.

### What a kept run costs in the file

- **277.5 bytes for each shingle row of each run kept**, with the table's two shipped indexes: 1,248,620,544 bytes for 4,500,000 rows. Three runs of that size took 3,707,416,576 bytes, 274.6 a row.
- **278.8 bytes for each `signature_band` row of each stage-4 run kept**, with its primary key's index and `signature_band_by_bucket`: 3,569,238,016 bytes for two runs of 6,400,000 rows. That is sixteen rows for each signed occurrence.
- **Not measured**: a `minhash_signature` row, which carries a signature of 512 bytes; a `shingle_document_frequency` row, of which each stage-3 run writes one for each hash carried by two occurrences or more; and the other tables keyed by a run.

## Decision

### 1. `shingle_by_hash` is over the rows of one stage-2 run

Stage 4b builds it with one statement, for the stage-2 run whose rows it is about to read:

```sql
CREATE INDEX shingle_by_hash ON shingle (shingle_parameter_identity, shingle_hash) WHERE run_id = '<the stage-2 run id>'
```

**The name is unchanged, and there is one index of that name in a database.** It is not one for each run: ADR-182 refused that for the names it would need and for whatever would have to drop the indexes of runs nobody reads, and neither arises with one name.

**The columns are the granularity and the hash, and not the run.** Every row in the index is one run's, so the run in the key told nothing apart, and it was most of the key: 64 characters in each entry. Containment retrieval looks up the granularity and a hash, which the plan above shows it doing. The index does not hold `occurrence_id`, the column that read returns, and nor did ADR-182's: each hit is fetched from the table, as before, and what that costs is as it was.

**The statement carries no `IF NOT EXISTS`.** By the time it is issued the index is absent (§3), and SQLite then keeps the statement in `sqlite_master` exactly as issued, which is what §3 compares. With `IF NOT EXISTS` it keeps the statement without those words, and the two would differ. That second sentence is the second probe's and no test's (`probe468b.log`, lines 4 to 6: issued with the words, stored without them, `equal: false`); the first is held by `ShingleIndexesInTheSchemaTest`.

### 2. Containment retrieval keeps the run as a bound value

`RedundancyResolution`'s statement is not changed. It is sent with `run_id = ?`, and SQLite answers it through the index where the value bound is the run the index was built for (Plans).

**The basis, in SQLite's own documentation.**

- *Partial Indexes*, section 3, gives two rules for when a query may use one, and says of the first: *"The terms in W and X must match exactly."* It says nothing of bound values, and adds that *"future versions of SQLite might incorporate a better theorem prover"*.
- The release notes of SQLite 3.20.0 (2017-08-01), item 12.2: *"The query planner examines the values of bound parameters to help determine if a partial index is usable."* That is the behaviour this section rests on. The check-in that made it, of 2017-06-24, reads: *"Consider the values bound to SQL variables when determining whether or not a partial index may be used."*
- The documentation of `SQLITE_DBCONFIG_ENABLE_QPSG`: *"When the QPSG is active, a single SQL query statement will always use the same algorithm regardless of values of bound parameters. The QPSG disables some query optimizations that look at the values of bound parameters"*. And *The Next-Generation Query Planner*, section 2.2: *"The QPSG is disabled by default."*

**What the shipped connection sets.** The datasource URL of `application.yaml` sets `foreign_keys`, `busy_timeout`, `journal_mode`, `synchronous` and `journal_size_limit`, and nothing in `src/main` calls `sqlite3_db_config`. So the stability guarantee is off, as SQLite ships it, and the planner may look at the value bound. Whether the bundled library was compiled with `SQLITE_ENABLE_QPSG` was not read from its build; the tests of this record run against that library and would fail if it were.

**What the documentation does not say, and what the tests then rest on.** The partial-index page was not brought up to the release note, so the one sentence of 3.20.0 is the whole of the documented basis; no page says in which step the value is looked at. Measured, it is looked at when the statement is planned with its values bound: the plans above were read after binding. `ShingleIndexesInTheSchemaTest` holds the plan against the bundled SQLite with the run's index and with another run's, and `ShingleHashIndexInvocationTest` holds it over a database an invocation left. A newer SQLite that stopped doing this would fail both, and the remedy would then be the run written into the statement's text, which this record does not take: `RedundancyResolution`'s statement is #476's to change.

**Why 4b's check has to be exact.** With an index of that name built for another run, the same statement reads every row of its run for each call (Plans), which ADR-182 measured at 2.4 s a call on 10,000,000 rows where the lookup took 0.14 ms. Nothing fails; the stage is only slow by four orders. So an index is never taken to be the run's because one exists.

### 3. Stage 4b builds the index where it is absent or is not this run's

Behind the gate and the finished-step check it applies today, and in the transaction of its own ADR-182 §2.3 gives it, stage 4b asks one thing of `sqlite_master`: the statement it holds for an index named `shingle_by_hash` on `shingle`.

**The index is this run's where that statement equals, character for character, the statement of §1 written for this run's id.** Nothing else makes it so: not that an index of the name exists, not its columns, not a part of its text.

| What `sqlite_master` holds | What stage 4b does |
| --- | --- |
| no such index | builds this run's |
| the statement of §1 for this run | nothing, and says nothing |
| the statement of §1 for another run | drops the index, builds this run's |
| any other statement, ADR-182's over every run's rows among them | drops the index, builds this run's |

The drop and the build are two statements and two transactions. A process that dies between them leaves no index, which is the first row at the next invocation. A process that dies inside the build leaves none either, as ADR-182 §3 has it.

**A database last run under an earlier build needs no migration.** Its whole-table index is the fourth row. In the ordinary upgrade stage 4b does not even meet it: the build that carries this record moves `similarity`, so stage 2 has a new run and drops the index before it writes (§8).

**`IF NOT EXISTS` is no longer the whole of the check**, and ADR-182 §3's sentence that the decisions are made *"through `IF EXISTS` and `IF NOT EXISTS`"* holds of stage 2's drop alone. Its principle stands: the decision is made from what `sqlite_master` holds at the moment, never from a record of what an earlier invocation did.

### 4. The run id is written into the statement, and an id not in the minted form is refused

SQLite takes no bound value in an index's `WHERE` (*Partial Indexes*, section 2: the clause *"may not contain subqueries, references to other tables, non-deterministic functions, or bound parameters"*). So the id is part of the statement's text.

**The build refuses, with an `IllegalArgumentException` and before it issues any statement, a run id that is not exactly 64 characters each of which is a digit or a lowercase letter from a to f**: the regular expression `[0-9a-f]{64}` over the whole id. That is the form `RunId.of` mints, the SHA-256 of the run's material in lowercase hexadecimal, and the form of every id `RunMint` hands a stage. `RunId`'s own constructor refuses only a blank, which is why the build checks.

Nothing else is ever joined into the statement. An id of that form holds no quote, so it cannot end the literal it is written in.

### 5. What the build says

**The line before**, in place of ADR-193 §4.2's:

```
Stage 4b (redundancy resolution) is building shingle_by_hash over the rows of run <run> alone, reading up to <N> shingle rows to find them, and removing first any shingle_by_hash built for another; stopping before it ends undoes the build
```

`<run>` is the stage-2 run id. `<N>` is `SELECT MAX(rowid) FROM shingle`, as ADR-182 §2.4 has it: an upper bound on the rows the build reads, which are every run's. It is no longer the rows the index holds, and the line says so. The sentence about 39 minutes is gone from it: that was the build over 42,833,917 rows of every run on a USB spinning disk, another statement, and this one has not been run on such a disk.

**The line is written before the drop**, so that a drop of an earlier build's index, which reads every page of it, is not silent (ADR-187). The drop has no lines of its own: the drop of another run's index took 0.06 to 0.12 s over 4,500,000 and 9,000,000 rows, and the line before the build says it is made.

**The progress lines say `at least`**, where ADR-193 §4 has `about` for a build:

```
Stage 4b (redundancy resolution, building shingle_by_hash): at least X% of N rows
```

The build declares 12 steps a row, the most a row takes (Steps). A row of another run takes 3. So steps divided by 12 are the rows read where every row is the run's, and fewer than the rows read where the table keeps other runs: the share is never ahead of the work, and ends at

> 25% + 75% × R / T

of the rows, R being the run's rows and T the table's. That is 100% for a run alone, 70% for a run of 9,000,000 among 15,000,000, 47.5% for 4,500,000 and 32.5% for 1,500,000. This is ADR-211 §9's form for a count that is a lower estimate, taken here by a build.

**The line that the rows are gone through** is written as ADR-193 has it, at the callback where the rows counted reach N less the rows of one callback. Where the table keeps more than about 11,000 rows of other runs the count never reaches that (a callback is 8,334 rows, each row of another run is counted as a quarter of one, and 8,334 over the three quarters it falls short by is 11,112), and the line is not written: the last progress line is followed by the line after the build.

**The line after** is unchanged: `Stage 4b (redundancy resolution) built shingle_by_hash in <S> s`. `<S>` runs from before the drop to the end of the build. Where the index is this run's, none of these lines is written.

### 6. What the working directory's drive needs free at stage 4b

**51 bytes for each shingle row of the run in hand**, beyond the database as it stands before the build:

> free bytes ≥ 51 × R, where R is the rows of the stage-2 run stage 4b reads

The 51 is the greatest peak measured, 50.46 a row, rounded up to a whole byte, and carries no margin; the seven rows of Measured's table, over their rounds, run from 46.1 to 50.5. R is not the number in stage 4b's line, which is the table's. Stage 3's line before its grouping states an upper bound on it, the span of the run's rows (ADR-191).

Of the 51, about 25 stay in `vespera.db` as the index and the rest is given back: the temporary files when the statement ends, the write-ahead log at the next write or when the invocation ends.

**It does not grow with the runs kept.** ADR-218's figure did: 183 to 186 for each row of every run.

**Worked for the 42,833,917 rows on record**, were every one of them one run's, which no agent knows:

| | Bytes | GiB |
| --- | ---: | ---: |
| The new build at its peak, 51 × R | 2,184,529,767 | 2.03 |
| The build over every run's rows at its peak, 183 × R, as ADR-218 worked it | 7,838,606,811 | 7.30 |

Were those rows two runs' of equal size, the new build would need half the first figure and the old would have needed the second all the same.

**The build is still an exception to ADR-060's bound.** Its temporary files grow with the run, 23.3 to 24.4 bytes a row, and nothing bounds them. ADR-218 §1's exception stands with this size in place of its own.

### 7. The rows of an earlier run stay

Nothing removes a row of `shingle`, and this record adds nothing that does. The same holds of `minhash_signature`, `signature_band`, `shingle_document_frequency` and every other table keyed by a run: the only deletes are a step discarding its own unfinished work under its own run (ADR-116).

**What that costs is the database file's size, and no longer the room or the time of stage 4b's build**: 277.5 bytes for each shingle row of each stage-2 run kept, 278.8 for each `signature_band` row of each stage-4 run kept, and the rows not measured (Measured, *What a kept run costs in the file*). Stage 4b's build also reads the other runs' rows, at 0.04 to 0.06 s a million on the disk measured.

**Why they are not removed here.** A run an invocation arrives at again finds its work recorded and does none (ADR-156 §2, and the sixth and seventh rows of *What is kept*, the value put back and the first build run again). Were its shingle rows gone, stage 2 would still be finished under it, and a stage 4 over it would sign nothing and say nothing was wrong. Which runs can never be arrived at again, what removing their rows costs, and whether the file should be made smaller, are [#481](https://github.com/algernon28/vespera/issues/481)'s.

### 8. Which run ids move

A stage's implementation version is the last commit touching `src/main/java/io/algernon/vespera/<module>` for a module `StageModules` names for it (ADR-058).

**The commit that wrote this record moved nothing.** It changed nothing under `src/main`.

**The build touches two modules: `similarity` and `pipeline`.** `similarity` for `ShingleHashIndex`, `SimilarityStatement` and `RedundancyResolution`'s javadoc; `pipeline` for `RedundancyJobConfiguration` and `StatementProgress`. `schema.sql`'s comment is under `src/main/resources`, which no module's version reads.

As `StageModules` stands since [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md), which took `pipeline` out of content census, content redundancy and arrangement and left it in seed measurement, embedding scoring and generation:

| Stage | Modules it names | Moves with this build |
| --- | --- | --- |
| byte-level reduction (1) | `corpus` | no |
| extraction (2) | `extraction`, `similarity` | yes, for `similarity` |
| content census (3) | `similarity`, `extraction` | yes, for `similarity`, and for the stage-2 run upstream of it |
| content redundancy (4) | `similarity`, `extraction` | yes, the same way |
| seed measurement (5) | `embedding`, `extraction`, `pipeline` | yes, for `pipeline`, and for the run upstream of it |
| embedding scoring (5) | `embedding`, `extraction`, `pipeline` | yes, the same way |
| arrangement (6a) | `synthesis`, `extraction`, `embedding` | yes, for the run upstream of it alone: it names neither module touched |
| generation (6b) | `synthesis`, `extraction`, `embedding`, `pipeline` | yes, for `pipeline`, and for the run upstream of it |

So every stage from extraction on moves, seven of the eight rows, and byte-level reduction does not. The change to `similarity` alone would move the same seven, each stage after extraction naming the run upstream of it (ADR-048). The arrangement is a new one, so `arrangementApproved` must name it, and generation's calls are made again.

**Stage 2 does its work again, from the extraction cache, and writes one more copy of the corpus's shingle rows** beside those `shingle` keeps (§7), dropping the index it finds first. No file goes back to the converter: a build that moves `similarity` converted nothing in the test of *What is kept*. Stage 4b then builds the index over that new run's rows alone.

## What is to be built

Written before any of it was built; the tests of this record were written against it, and `spec-implementer` built P1 to P6 from it (Built, above).

**A constraint from [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md) §4**, which merged between this record's commit and its build: its guard `PipelineHoldsOnlyTheRulesOnRecordTest` fails on any new class in `pipeline`, and on `RedundancyJobConfiguration` naming a type of a capability module or of `profile` beyond the five it names today, which of `similarity` are `RedundancySignatures`, `ShingleHashIndex`, `SimilarityStatement` and `SimilarityStatementProgress`. So the check, the drop and the build are one call of `ShingleHashIndex`, and `pipeline` hands it a run id and decides nothing; no class is added to `pipeline`. The run id is a `ledger.RunId`, which that class imports today and the guard does not count.

**P1, `similarity/ShingleHashIndex`.**

- One new public method, and the two it replaces removed, `build()` and `build(SimilarityStatementProgress)`, with the constant `BUILD`:

  ```java
  public Optional<Duration> buildFor(RunId stage2RunId, SimilarityStatementProgress progress)
  ```

  In this order:
  1. Where `stage2RunId.value()` does not match `[0-9a-f]{64}` whole, throw `IllegalArgumentException`. Nothing is read or issued before this.
  2. Write the statement of §1 for the id: the text `CREATE INDEX shingle_by_hash ON shingle (shingle_parameter_identity, shingle_hash) WHERE run_id = '`, the id, and `'`. One space between words, none before the bracket's contents, as written here: `sqlite_master` keeps it as issued.
  3. Read `SELECT sql FROM sqlite_master WHERE type = 'index' AND tbl_name = 'shingle' AND name = ?` for the index's name. Where the one row equals the statement of step 2, return `Optional.empty()`, having called nothing on `progress`.
  4. Call `progress.statementStarting(SimilarityStatement.SHINGLE_HASH_INDEX_BUILD, OptionalLong.of(shingleRowsUpTo()))`, and start the clock.
  5. Issue `DROP INDEX IF EXISTS shingle_by_hash`, outside `StatementSteps.counted`, so that its steps are not the build's.
  6. Issue the statement of step 2 inside `StatementSteps.counted`, telling `progress.stepsTaken` at each callback, as `build` does today.
  7. Call `progress.statementEnded(SimilarityStatement.SHINGLE_HASH_INDEX_BUILD)` and return the time since step 4.
- The statement is to be one text in the class that opens with `CREATE INDEX`, the id joined in by concatenation or by `%s`: `EveryStatementThatSortsIsRecordedTest` finds the class's index build by that opening and puts an id where the join is.
- `drop()`, `exists()` and `shingleRowsUpTo()` stay as they are; stage 2 calls all three, for its drop and the two lines about it.
- The class's javadoc states this contract and cites this record (ADR-216).

**P2, `similarity/SimilarityStatement`.** `SHINGLE_HASH_INDEX_BUILD(12)` in place of `(11)`, its javadoc saying 12 is the most a row takes and 3 what a row of another run takes.

**P3, `similarity`, javadoc only.** `RedundancyResolution`'s class javadoc and the javadoc of `containmentCandidates` name the index's columns as `(run_id, shingle_parameter_identity, shingle_hash)`: corrected to §1's. No statement of that class changes.

**P4, `pipeline/RedundancyJobConfiguration`, in `ShingleHashIndexBuild.beforeStep`.** After the gate and the finished-step check, which stay: the test `if (shingleHashIndex.exists()) return;` goes, and the call becomes `shingleHashIndex.buildFor(stageRuns.upstream(StageModules.EXTRACTION), progress)`, the run `RedundancyResolutionTasklet` takes the same way. `statementStarting` writes §5's line before, with that run's id and the rows it is handed, and makes its progress with P5's factory. The line after is written where the call returns a time, and not where it returns nothing. The listener's javadoc is corrected to §3.

**P5, `pipeline/StatementProgress`.** One more factory, for a build whose steps a row are the most a row takes: `build` true and the estimate `at least`, so that its lines are §5's and the line that the rows are gone through is written as for any build. The class javadoc's count of forms goes from three to four. `ofBuild` stays for start-up's builds.

**P6, `src/main/resources/schema.sql`, comment only.** The comment that begins *"NOT CREATED HERE, on purpose (ADR-182)"* names the index's columns as `(run_id, shingle_parameter_identity, shingle_hash)` and says stage 4b builds it *"once, whole"*: corrected to §1 and §3. No statement of the file changes, so no start builds anything.

**P7, test side, once P1 is in.** The body of `TheRunsHashIndex.buildFor` is the call itself. Until the method existed it was made by name, through reflection, so that the tests compiled.

## Alternatives refused

- **Keep ADR-182's index and state the growth.** No code and no run id moves. The room stage 4b needs stays 183 to 186 bytes for each row of every run kept, and its time grows with them, for an index of which one run's part is read.
- **Remove the rows of an earlier run when another is minted.** It contradicts ADR-156 §2 for a run on the same walk, and fails without a word (§7). For the runs of a walk that is no longer its root's latest the reading of the code is that nothing arrives at them again; that, and the cost measured for a delete, are in #481.
- **One index for each run, its name carrying the run id.** ADR-182 refused it, and its reasons stand.
- **The run written into containment retrieval's statement.** It needs no reliance on the planner looking at a bound value. It changes `RedundancyResolution`'s statement, which #476 is to change, and the plans are the same (Plans).
- **Keeping `run_id` as the index's first column, under the same condition.** The statement of containment retrieval would then match the key as it does today. It would carry 64 characters in every entry for nothing: the old key cost 91 bytes a row in the file where this one costs 24 to 25.
- **Lines of its own for stage 4b's drop.** Another statement to name in `similarity`, for a drop measured in tenths of a second. The line before the build says it is made, and is written before it.
- **A progress count of the run's rows alone.** The steps the handler counts are of every row read, 3 for each row of another run, so a count over the run's rows would run ahead of the work and reach a hundred before the end.

## Consequences

- **Stage 4b's build needs 51 bytes free for each shingle row of the run in hand, and no longer grows with the stage-2 runs a working directory has had.** It took a fifth to a sixth of the time for one run among three.
- **`shingle` goes on growing by a copy of the corpus's rows for each stage-2 run**, 277.5 bytes a row, and the build of this record adds one (§8). Nothing gives that back until #481 is decided.
- **Two corpus roots in one working directory, invoked in turn, each rebuild the index when their stage 4b has work**, where one index served both before: a drop of tenths of a second and a build of 0.65 to 0.68 s a million rows of the run, on the disk measured. A stage 4b with nothing to do builds nothing, whoever's the index is. The same holds of a run arrived at again by a value put back, where its stage 4 has work.
- **Containment retrieval is slow, and not wrong, wherever it would meet another run's index**: §3 is what keeps it from meeting one. Nothing in `src/main` checks the plan at run time.
- **Stage 3 no longer meets an index that draws its grouping**, clause or no clause (Plans). ADR-219's clause stays; it costs nothing.
- **A read of one occurrence's shingles asked for `DISTINCT` is still drawn to the index**, so #277's guard is still needed, and now builds the index for its own run so that it can fail.
- **The line before the build no longer states a duration.** An operator reads the run, the rows read and the progress lines.
- **Stage 2's line before its drop still says what a drop took on a 16 GB database** (ADR-187). The index it now drops is a quarter the size a row and one run's. The line is not changed: decision 3.

## Tests

| Class | What it holds |
| --- | --- |
| `similarity.TheHashIndexIsBuiltForOneRunTest`, new, five tests | §3's four states, a test each, through `buildFor`: no index, this run's, another run's, and ADR-182's over every run's rows; that the caller hears of one statement started and ended, or of none; and §4, that eight ids outside the minted form, one of them the minted form with a line end after it, are each refused before anything is issued and the index found is left |
| `similarity.ShingleIndexesInTheSchemaTest`, eight tests where it had six | §1, that the index is on two columns over part of the table and its statement is kept as issued; §2, that containment retrieval with the run bound is answered through the run's index, and not through another run's nor with none; that the grouping without ADR-219's clause is drawn to ADR-182's form and not to §1's; and that one occurrence's shingles asked for `DISTINCT` are drawn to it |
| `pipeline.ShingleHashIndexInvocationTest`, eight tests where it had five | by whole invocations: the five of ADR-182 with the index's columns and statement §1's; that a second stage-4 run over the same stage-2 run builds nothing; that two corpus roots in one working directory each build their own in turn, the plan going through the index only for the run it is for; and that ADR-182's form, found by a stage 4b, is replaced |
| `pipeline.AnEarlierRunsRowsStayInvocationTest`, new, two tests | *What is kept*, row by row, with §5's line naming the run in hand and stating the table's rows; and what a stage 4 does over a stage-2 run whose shingle rows are gone: it signs nothing, writes no `REDUNDANT_WITH` verdict and no `redundant_with` row, and ends with exit code 0 |
| `pipeline.StatementProgressInvocationTest`, one test turned | §5: the line before the build, word for word after the stage's name, and the progress lines saying `at least` and never `about` |
| `pipeline.StatementStepsPerRowTest`, six tests where it had five | Steps: 12 a row of the run and 3 a row of another, in a test of its own; the build no longer among the builds of eight steps and one a column |
| `pipeline.StatementStepsPerRowAreTheDeclaredOnesTest` | that the build declares the 12 measured |
| `similarity.SimilarityStatementProgressOrderTest`, one test turned | that the build is started with the table's highest row number and ended, through `buildFor` |
| `similarity.RedundancyResolutionTest`, #277's guard | builds the index for its fixture's stage-2 run and reads its plans with that run bound |
| `similarity.RedundancyResolutionReadsAPageOfSignedOccurrencesAtATimeTest` | builds the index with §1's statement for its run; no claim changes |
| `EveryStatementThatSortsIsRecordedTest` | puts a run id where the build's text joins one in, and counts an index build without planning it; this record changes none of its counts, one build in `ShingleHashIndex` among them. Its second state is the database with an index built for a run no planned statement names, every statement being planned with whole numbers bound, so it is the first state again: the count of sorting statements in each class is not held with a run's own index usable (*What no test holds*) |

**Fifteen tests failed until this was built, and the table says what turned each.**

| Test | Turned by |
| --- | --- |
| `TheHashIndexIsBuiltForOneRunTest`, all five | P1 |
| `SimilarityStatementProgressOrderTest.theBuildIsStartedWithItsTotalAndEnded` | P1 |
| `StatementStepsPerRowAreTheDeclaredOnesTest.eachCountedStatementDeclaresItsMeasuredSteps` | P2 |
| `ShingleHashIndexInvocationTest`: the stopped stage 2 resumed, the finished stage 2 behind the shut gate, the failed resolution, the second stage-4 run, the two corpus roots, and the whole-table index: six | P1 and P4 |
| `StatementProgressInvocationTest.theBuildAndTheReadSayHowFarTheyHaveGone` | P1, P2, P4 and P5 |
| `AnEarlierRunsRowsStayInvocationTest.everyRunOfStageTwoLeavesItsShingleRowsAndStageFourBBuildsOverTheRunInHandAlone`, which was written as `...BuildsOverAllOfThem` and renamed for what it claims | P1 and P4 |

Five, one, one, six, one and one: fifteen. Every other test of the suite passed then, and all pass with the build. The last of the fifteen was also changed after ADR-222 merged: it had a build that moves `pipeline` alone mint a second stage-4 run, which no longer happens, and takes that run from a changed boilerplate floor instead (*What is kept*).

**What no test holds.**

- **Any size or time.** They are the probes'. A test of the suite cannot watch the process's temporary files (ADR-218, Tests), and the figures need millions of rows.
- **The plan over millions of rows.** The plan tests run over empty tables or a corpus of 48 texts; the probes' plans over 13,500,000 rows were the same.
- **That the bundled SQLite was compiled without the stability guarantee**, except through the plans it gives.
- **§5's share at the end**, 25% + 75% × R / T. `StatementProgressInvocationTest` holds the words and that the shares rise and stay below a hundred. Its claim that the line that the rows are gone through is written holds there because the other rows in its database are few. It claims first that they are fewer than the 11,112 of §5, which is a necessary bound and not a sufficient one: below it, whether the line is written turns on where the last callback falls.
- **How many statements of each class sort where the run's own index is usable.** `EveryStatementThatSortsIsRecordedTest` plans every statement with whole numbers bound and builds the index for a run id of 64 letters `a`, so no statement it plans names that run and SQLite may use the index for none of them: its second state has the plans of its first. With ADR-182's index the two states differed in what SQLite could choose. What the index does for its own run is held for three statements and no other, by `ShingleIndexesInTheSchemaTest`, `RedundancyResolutionTest` and `ShingleHashIndexInvocationTest`: containment retrieval, the grouping without the clause, and one occurrence's shingles asked for `DISTINCT`.
- **That `IF NOT EXISTS` would have `sqlite_master` keep another text** (§1): the second probe's.
- **That `buildFor`'s drop is outside the counted statement**, and that the line before is written before the drop.
- **The statement `sqlite_master` keeps under a later SQLite**: the comparison of §3 is with what 3.53.2 keeps.

## What this record does not measure

- Anything on a spinning disk, on `H:`, on Linux, or with the file cache empty.
- A run of more than 9,000,000 rows, or a table of more than 15,000,000; the table on record has 42,833,917.
- The build through the application's own pool and `StatementSteps`: the probes used one plain connection.
- A containment read as the code makes it, with an occurrence's rarest hashes. The 98 s for 2,000 reads compares two indexes over one sample.
- What stage 2's inserts cost with another run's index of §1's form in the database. Its rows do not meet the index's condition, so SQLite has nothing to write to it, and stage 2 drops it all the same.
- The drop of an index of ADR-182's form grown row by row or built into freed pages, which ADR-187 records at two hours on a 16 GB database. The one dropped here was freshly built.
- A `minhash_signature` row and a `shingle_document_frequency` row in the file.
- The suite's integration tests, which `./mvnw verify` runs.

## What this does not decide

- **Whether an earlier run's rows are ever removed, by what, and whether the file is made smaller**: [#481](https://github.com/algernon28/vespera/issues/481).
- **Whether stage 2 need drop an index that is another run's**, its own rows not being in it. Decision 3 keeps the drop.
- **What stage 2's line before its drop should say now.**
- **Whether an invocation should check the drive's free space before stage 4b builds**, which ADR-218 left open.
- **Containment retrieval's statement and what it holds on the heap**: [#476](https://github.com/algernon28/vespera/issues/476).
