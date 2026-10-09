# ADR-193 — A statement SQLite counts reports how far it has gone, and one it cannot count says how long it took

> **Partly amended — see [ADR-209](0209-the-ledger-is-four-records-behind-one-type-no-capability-module-names-spring-batch-and-a-tables-sql-is-its-owners.md).** In §7, "pinned in the module that owns the statement, beside its SQL" no longer holds for `EmbeddingStatement.CORPUS_METRICS` and `SEED_METRICS`, whose steps a row stay declared in `embedding` while their SQL is `extraction`'s, as the SQL of the timed `SimilarityStatement.NEAR_DUPLICATE_METRICS` is. Every constant, ratio, line and order stands.

> **Partly amended — see [ADR-200](0200-stage-1-reads-survivors-by-size-and-holds-one-size-at-a-time.md).** Stage 1 no longer drains its survivors, so these passages below are no longer the decision for stage 1, and that record's §7 says why nothing replaces them: §6's two rows for `SurvivorDrain.drain`, "the survivors to check" and "the survivors to size"; §7's `CorpusStatement` with `SURVIVORS_TO_CHECK` and `SURVIVORS_TO_SIZE`, `corpus` among the modules given an enum and an interface, and `corpus.HashingProgress` and `CheckingProgress` among the interfaces that extend one; §9's `corpus` among part (b)'s modules and its "stages 1 to 6b", which is now stages 2 to 6b; and, under Tests, the claim owed in `StageOneReportsItsLoopsInvocationTest` and the contract test owed in `corpus`. Everything else in this record stands.

> **Extended — see [ADR-199](0199-the-statements-adr-193-left-unnamed-take-its-rule.md) and [ADR-204](0204-every-line-of-adr-193s-part-b-is-written-out-and-its-table-is-read-again-against-the-code.md).** ADR-199 gives a form to the statements §6 closes on and left to #429, to a read beside them and to stage 1's second survivor count, and adds two constants to `ExtractionStatement`. ADR-204 writes out every line of part (b) with its `<stage>`, reads §6 again against the code, gives a form to one statement ADR-197 added to the relevance report, lists the four enums of §7 as they stand, says part (b) moves the run ids of stages 2 to 6b, and says where each test this record's Tests owed with part (b) went. Under Tests, the sentence that the drop's steps are the same at two sizes is now what `StatementStepsPerRowTest` asserts.

> **Partly amended — see [ADR-211](0211-no-class-holds-every-survivor-of-a-run-another-tables-rows-are-read-a-page-of-survivors-at-a-time.md).** §1 gains a third form beside counted and timed, a read made a page of survivors at a time: it writes the counted form's lines, with its progress taken from the rows the module says it has read and not from SQLite's steps. §1's rule that a statement whose plan sorts is timed has one exception: stage 3's grouping of the shingle rows sorts and is counted, by 45, a figure above the most steps a row it was measured to take and not a constant, its lines saying `at least` where a counted statement's say `about`, under `grouping shingle rows` where this record named them `reading shingle rows`. In §3's table the row for stage 3's shingle rows declares 45 where it declared 7, and the rows for `ConfidenceDistribution`'s extraction metrics and 5b's corpus survivors' extraction metrics lose their r. In §6 the rows for `DocumentFrequency.drainSurvivors`, `ConfidenceDistribution.drainSurvivors`, 5b's drain of the survivors and 5f's drain are struck, and the row for 5c's and 5d's drain becomes a timed count. §7's enums lose `ExtractionStatement.SURVIVORS`, `SimilarityStatement.FREQUENCY_SURVIVORS` and `EmbeddingStatement.CORPUS_SURVIVORS`, and gain `EmbeddingStatement.CORPUS_METRICS_AGAIN`. §2's mechanism and its rule that a counted statement runs on the one connection it is handed stand.

> **Partly amended — see [ADR-220](0220-no-class-holds-every-occurrence-of-a-run-stage-2s-resume-the-census-stage-4b-and-stage-5e-read-a-page-at-a-time-or-ask-by-key-and-what-is-still-held-says-why.md).** In §6, stage 4b's timed reads of `the signature bands` and `the shingle document frequencies` are struck, each now one statement a page of signed occurrences or one an occurrence inside a loop that reports; its read of the near-duplicates' extraction metrics stays timed, in statements of at most 1,000; and 5e's read of `the scores below the floor`, which sorted, becomes a timed count, the scores then written a page at a time. §7's `SimilarityStatement` loses `SIGNATURE_BANDS` and `DOCUMENT_FREQUENCY`. The read of a stopped run's faults is a fourth read of the paged form ADR-211 gave §1. And §6's timed read of `the occurrences it could not read`, for stage 2's review list, keeps its two lines, which now span the count of those occurrences and the writing of the page as well as the read in path order (ADR-220 §6).

- **Date**: 2026-10-05
- **Status**: accepted
- **Amends**: [ADR-187](0187-a-database-statement-that-can-take-minutes-says-so-before-it-starts-and-when-it-ends.md) §1, **one sentence and nothing else.** *"Nothing is written between the two lines"* now holds only for a statement SQLite gives no callback for, of which stage 2's drop is the one ADR-187 names. A build of an index writes the lines of §4 here between its two. ADR-187's refusal of a line on a timer stands: no line here is written on a clock (the operator's decision 2). Its "What this does not decide", *"Progress during a build"*, is settled here, and so is the bullet of its "Why this shape, and what the others cost" that reads *"Real progress for the builds, from SQLite's progress callback … Not taken here"*, which this record supersedes.
- **Amends**: [ADR-182](0182-stage-2-writes-shingles-without-the-by-hash-index-and-stage-4b-builds-it-before-containment-retrieval-reads-it.md) §2.4, **the wording of stage 4b's line before the build and nothing it decided** (§4.2).
- **Amends**: [ADR-191](0191-stage-3-says-how-many-shingle-rows-it-is-about-to-read-and-how-long-measuring-them-took.md) §1 and §3, **two sentences.** §1's *"Nothing is written between the two lines"* no longer holds: stage 3's read writes §4's progress lines between them. §3's *"`ContentCensusTasklet` asks for the bound, writes the first line where a value comes back"* now reads: `DocumentFrequency.measure` asks for the bound itself and hands it to `pipeline` through §7's callback, immediately before the read, and `pipeline` writes the first line then. The line's words, its bound and when it is not written are unchanged. Its "What this does not decide", *"Progress during the read"*, is settled here.
- **Extends**: [ADR-093](0093-logging-is-explicit-and-process-scoped-console-plus-rolling-file-per-item-and-per-step-at-info.md), as [ADR-192](0192-every-loop-in-every-stage-reports-its-progress-and-no-counter-floods-the-log.md) §8 capped it, to one SQL statement: a line `<label>: about X% of N rows`, at INFO, on §8's cadence.
- **Settles** what [ADR-192](0192-every-loop-in-every-stage-reports-its-progress-and-no-counter-floods-the-log.md) §7 left to #411: every statement, drain and row callback named there.
- **Rests on**: [ADR-040](0040-modules-are-capability-shaped-not-stage-shaped.md) and [ADR-041](0041-ledger-owns-identity-and-verdicts-capabilities-own-their-own-tables.md) (the module that owns a table issues its statements, and a capability module writes no line), [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) (which run ids move), [ADR-110](0110-pipeline-hands-synthesis-its-inputs-so-the-module-rule-gains-no-second-exception.md) (a capability module hands back through a callback it owns), [ADR-173](0173-every-column-that-references-a-file-occurrence-a-walk-or-a-run-carries-an-index.md) (the indexes on `run_id` that make a total cheap), [ADR-177](0177-one-invocation-per-working-directory-and-a-locked-database-file-is-named.md) §2.1 (SQLite driver plumbing every module shares lives in `ledger`, and every statement goes through the application's `JdbcTemplate`), ADR-187, ADR-191 and ADR-192.
- **Keeps**: [ADR-059](0059-schema-version-is-one-row-per-module-checked-and-refused-independently.md). No table, column or index changes.
- **Settles** [#411](https://github.com/algernon28/vespera/issues/411), under the operator's decisions recorded there on 2026-10-04 and 2026-10-05.

**Three words here are SQLite's.** A *step* is one instruction of SQLite's virtual machine, the unit its progress callback counts. A *plan* is what `EXPLAIN QUERY PLAN` answers for a statement. A *temp B-tree* is the structure a plan names when it sorts or removes repeats in a structure of its own, after it has gone through the rows. An *index* is always a SQLite index, never the ledger.

**Two words here are the record's own.** A *counted* statement writes progress lines between its two (§1). A *timed* statement writes its two lines and the time between them, and nothing else.

## Context

### What happened

On 2026-10-04, on the whole-archive working directory on `H:` (a partition of a USB portable spinning disk), stage 4b wrote at 19:03:48 that it was building `shingle_by_hash` over up to 42,833,917 shingle rows, *"on a large database this takes minutes"*, and at 19:42:48 that it had built it in 2,340.0 s. Thirty-nine minutes passed with nothing in the log. Two ten-second samples of the process, at 19:18 and 19:22, showed about 0.5 MB read and 0.5 MB written a second in 4 KB operations and about 1% of one core: it was waiting on the disk, and nothing in the log could tell that from a hang. The resolution after it was silent for a further 26 minutes 40 seconds in the same way, and stage 5b, which has no loop of its own (ADR-192 §7), took 1 minute 47 seconds with nothing saying where.

ADR-187 had left progress during a build undecided, and ADR-191 had left progress during stage 3's read undecided, each for a ticket of its own if two lines proved too little. They proved too little.

### What the operator decided

On 2026-10-04: every stage gets a tracker, whatever its duration, with no threshold; loops are #412's (ADR-192), and what is truly one statement is #411's; the scope then widened to stage 5b, the single-statement reads of 4b's resolution, and `scoredBelow`, `partitions`, `forRun` and `measure`.

On 2026-10-05, after a probe of SQLite's progress callback (below):

1. **Progress, not a heartbeat.** A statement with callbacks and a known row total writes `about X% of N rows`. X comes from the callback count, through the measured steps-per-row ratio, which a test pins against the bundled SQLite.
2. **No callback, no line.** A statement SQLite gives no callback for, such as `DROP INDEX`, keeps ADR-187's line before and line after, and nothing between. No timer line.
3. **Scope is every statement named on #411 and #412**: stage 4b's build, stage 3's read, 4b's single-statement reads, 5b's reads, `scoredBelow`, `partitions`, `forRun`, `measure`, and the start-up builds of ADR-187. A statement with no cheap row total gets ADR-187's two lines plus its duration.
4. The measurement on `H:` was made (below).
5. Shortening the build on a spinning disk is a ticket of its own.
6. The archive's next run waits for this record, so that run ids are minted once.
7. **The silent final phase of an index build is announced.** When the counter reaches its total, one line says the rows are gone through and that writing the index reports nothing until it ends. Nothing is written after that until the after-line.

## Measurements

### The operator's probe, 2026-10-05

Synthetic rows only, in a throwaway file deleted afterwards: 20,000,000 rows of fixed text and random numbers, about 2.2 GB, with a callback every 100,000 steps, through `org.sqlite.ProgressHandler` of sqlite-jdbc 3.53.2.1.

| Statement | Solid-state disk (`D:`) | USB spinning disk (`H:`) |
|---|---|---|
| `CREATE INDEX` on one `INTEGER` column | 10 to 11 s; 1,800 callbacks, exactly 90 per million rows at 5,000,000 and at 20,000,000 rows. Callbacks steady for the first 90% of the time, then none for 1.2 s | 15.3 s. Callbacks steady for the first 55%, then none for 6.6 s, 43% of the build |
| `SELECT` over every row | 1.8 s, 1,400 callbacks (70 per million rows), no gap | 1.8 s, no gap |
| `DROP INDEX` | 0 callbacks | 0 callbacks |

The callback runs on the statement's own thread and only while SQLite's virtual machine runs. A build is silent at its end, and the share of the silence grew on the slower disk. **The probe's file was new and written in order, which the archive's is not**: 42,833,917 rows took 39 minutes on 2026-10-04 against 15 s here. So this record quotes the shape and never a duration.

### This record's probe

A throwaway Java program outside the repository, on the bundled SQLite of sqlite-jdbc 3.53.2.1 and HikariCP 7.0.2, Java 26, Windows 11. `schema.sql` as shipped, in memory, with synthetic rows written to every table a statement here reads: an earlier run of half as many rows, then the run read, at 20,000 and at 60,000 rows a run. Steps were counted with a callback every step or every hundred, which the handler allows.

**Every figure was the same at both sizes, and every one is a whole number of steps a row**, to within a few steps over the whole statement: a b-tree a level deeper at one size costs a handful more in all, as in 18,003 steps for 2,000 more rows of `run` under `run_by_walk_id`, against 18,000 for 9 a row. The handful of steps a statement takes whatever its size (between 200 and 500 for a build, a few dozen for a read) is below one callback at the interval §2 fixes.

| Kind | Statement | Plan | Steps a row |
|---|---|---|---:|
| Build | `CREATE INDEX` over 1, 2, 3 and 4 columns, `INTEGER` or `TEXT` alike | — | 9, 10, 11, 12: **8 plus the columns** |
| Build | `shingle_by_hash`, `signature_band_by_bucket`, `verdict_by_occurrence`, `shingle_by_run_id`, `verdict_by_run_id`, `extraction_metric_by_run_id`, `relevance_score_by_run_id` | — | 11, 11, 10, 9, 9, 9, 9 |
| Removal | `DROP INDEX shingle_by_hash` | — | 300 steps in all, at either size |
| Read | stage 3's shingle rows | `SEARCH shingle USING INDEX shingle_by_run_id (run_id=?)` | 7 |
| Read | `ConfidenceDistribution`'s `extraction_metric` read | `SEARCH ... extraction_metric_by_run_id (run_id=?)` | 7 |
| Read | `loadSignedOccurrenceIds` | `SEARCH ... minhash_signature_by_run_id (run_id=?)`; the `DISTINCT` needs nothing, since the key is unique within a run | 5 |
| Read | 5b's two `extraction_metric` reads | `SEARCH ... extraction_metric_by_run_id (run_id=?)` | 12 |
| Read | 5b's `unusable_seed` read | `SEARCH ... unusable_seed_by_run_id (run_id=?)` | 5 |
| Read | the `signature_band` read | `SEARCH ... signature_band_by_bucket (run_id=?)` | 7 |
| Read | `loadDocumentFrequency` | `SEARCH ... sqlite_autoindex_shingle_document_frequency_1 (run_id=? AND shingle_parameter_identity=?)` | 6 |
| Read | `BoilerplateShingles`' frequency read | the same | 7 where every row passes `document_count >= ?`, 5 where none does |
| Read | `scoredBelow`; `RelevanceDistribution`'s `scoredUnder`; `DocumentClusters.forRun`; `Clusters.forRun`; `SynthesisDocs`' `synthesis_doc` read; `ClusterFaults.forRun`; `UnusableSeeds.forRun` | each through its run's index, then `USE TEMP B-TREE FOR ORDER BY` | 13, 15, 14, 20, 17, 17, 12 |
| Read | the winning seeds behind `partitions` | `USE TEMP B-TREE FOR DISTINCT` | 13 |
| Read | `Ledger.extractionFailures` | a join, then `USE TEMP B-TREE FOR ORDER BY` | — |

**What a cheap total costs.** ADR-191 §2 asks the span of a run's rowids as two statements. They cost 20 or 21 steps each, at either size, on `shingle`, `minhash_signature`, `extraction_metric` and `unusable_seed`, each of which has an index on `run_id` alone. On `signature_band`, `shingle_document_frequency` and `call_exemplar`, whose only index leading with `run_id` has more columns after it, each costs 5 steps a row of the run: SQLite goes through the run's whole part of the index, because there the rowids are not in order within one run.

**What the pool does to the handler.** `ProgressHandler.setHandler` refuses the connection HikariCP hands out, *"connection must be to an SQLite db"*, and `clearHandler` given it throws `ClassCastException`. Both accept the connection unwrapped to `org.sqlite.SQLiteConnection`. A handler set and not cleared stays on the pooled connection when it is closed: the next borrower's statement of a thousand rows called it 17,015 times. Cleared, none. Whether a handler is set can be read back, through `NativeDB.getProgressHandler()`, non-zero while one is set and zero once cleared, which a test can reach by reflection.

## Decision

### 1. Two forms, and which statement takes which

**A statement is counted where SQLite calls back during it and its total is cheap; every other statement in scope is timed.**

- **Its total is cheap** where SQLite answers it before the statement without going through the rows: the table's `MAX(rowid)` for a build of an index over the whole table (ADR-182 §2.4, ADR-187 §3), or the span of one run's rowids, asked as two statements (ADR-191 §2), for a read that goes through every row the run holds in one table, **through an index on `run_id` alone, with no temp B-tree in its plan**. The first condition is what makes the span two descents (Measurements); the second is what makes the steps a row the same for every row, so that a count of steps is a count of rows.
- **A statement SQLite never calls back during is not counted**, whatever its total. Stage 2's drop is the one in scope: ADR-187's two lines, and nothing between (the operator's decision 2).
- **Every other statement in scope is timed**: one line before, one after with its duration. That includes a read whose plan sorts in a temp B-tree, whose steps come in two loops with a sort between that gives no callback, and a read through an index with columns after `run_id`, whose span costs as much as the read. Neither has a cheap total by this rule.
- **A statement whose cost does not grow with a table is not in scope**: a lookup of one row by its key, the two statements of a bound, an `exists` check. ADR-187 §1's own test, *"a statement whose cost grows with a table"*.
- **A capability call that is one or more statements and nothing else** — no loop of its own, no file, no call to a model — is timed as one by `pipeline`, which makes the call, and gets no overload. `SynthesisDocs.forRun` (two reads), `RelevanceDistribution.spreadOf` (two reads and arithmetic) and `BoilerplateShingles.resolve` (a one-row lookup and the frequency read) are the three such calls of more than one statement.

**A change that adds a statement whose cost grows with a table puts it in one of the two forms in the same change**, by this rule, as ADR-187 §1 asked of its own.

### 2. The mechanism

**`org.sqlite.ProgressHandler`, from the sqlite-jdbc the pom already carries (3.53.2.1), set every 100,000 steps on the connection that runs the statement, and cleared after it.** No dependency is added.

- **Where it is set.** The class that issues the statement runs it inside `JdbcTemplate.execute(ConnectionCallback)`. Every capability module passes the one `JdbcTemplate` Spring Boot builds, so ADR-177 §2.1's translator still sees every failure. Start-up passes its own: `StartUpIndexAnnouncement` runs before that template can be asked for, and builds `new JdbcTemplate(dataSource)` as it does today. The statement callback gets the connection the template handed over, close suppression included, and runs the statement on it; the handler goes on that connection unwrapped to `SQLiteConnection`, which is the same physical connection. So the class sets the handler on the unwrapped connection, **runs the statement on the connection object it was handed**, and clears the handler, again on the unwrapped one, in a `finally`. Inside a tasklet's transaction the callback's connection is the transaction's, so the statement stays in the transaction it was in; outside one, as for the build, which commits on its own (ADR-182 §2.3), the callback borrows a connection and returns it. **The statement is never handed back to a `JdbcTemplate` method inside the callback**: outside a transaction that would borrow a second pooled connection, and the handler would count nothing. `StatementStepsTest` (Tests) runs on a pool of two, outside any transaction, and so pins the helper's own handling of the connection: the handler set on the connection it hands the statement, and cleared from it. It supplies its own statement, so a caller that hands its statement back to the template is beyond it, and the test profile's pool of one cannot see that mistake either. Each caller's own contract test, and review, are what stand against it.
- **One helper for it, in `ledger`**: `ledger.StatementSteps`, a final class of static members, with `STEPS_PER_CALLBACK = 100_000` and one method, `<T> T counted(JdbcTemplate, LongConsumer stepsTaken, ConnectionCallback<T> statement)`, whose consumer is told the steps taken so far at each callback, a whole number of 100,000. It holds no total, no ratio, no label and no line. It is plumbing of the SQLite driver that every module issuing a counted statement needs, of the kind ADR-177 put in `ledger` as `LockedDatabaseFileTranslator`. **It is not the shared progress type ADR-192 §5 refused**: that was the shape a capability module reports a loop's total and items through, and each module still owns its own (§7). `ledger` is in no stage's implementation version (`StageModules`), so this class moves no run id. Four copies, one in each module that issues a counted statement, were the other way; they would have to be kept alike by four tests.
- **`progress()` always returns 0, so the handler never ends a statement** (§8). An exception thrown by what it calls is caught inside it and dropped, so a line that cannot be written never stops a statement: a failure in the database is the step's to report, and a failure to log is not.
- **Nothing called from the handler touches the database.** SQLite forbids using a connection from inside its own progress callback; writing a log line does not.
- **Every 100,000 steps** is the interval the operator's probe measured, and a whole number of rows of no statement here: 9,090 rows of the 4b build at 11 steps a row. A statement of fewer steps gets no callback, and so its two lines and nothing between. Over 42,833,917 rows the build calls back about 4,700 times, each a call from SQLite into Java; §5 writes at most 100 lines of them.

### 3. Steps per row, and how a test pins them

**Each counted statement's rows are estimated as `⌊steps ÷ r⌋`, where r is its steps a row, pinned in the module that owns the statement, beside its SQL.**

| Counted statement | r | Where r is declared |
|---|---:|---|
| any `CREATE INDEX` over a whole table, of c columns | 8 + c | `pipeline.StartUpIndexAnnouncement` for start-up's, whose `INDEX_STATEMENT` pattern captures nothing after the table's name today and must also capture the parenthesised column list, as `StatementStepsPerRowTest.INDEX_STATEMENT` does, so that c is the count of its columns; `similarity.SimilarityStatement.SHINGLE_HASH_INDEX_BUILD` for 4b's, 11 |
| stage 3's shingle rows | 7 | `SimilarityStatement.SHINGLE_ROWS` |
| 4b's signed occurrences | 5 | `SimilarityStatement.SIGNED_OCCURRENCES` |
| `ConfidenceDistribution`'s extraction metrics | 7 | `extraction.ExtractionStatement.EXTRACTION_METRICS` |
| 5b's unusable seeds | 5 | `embedding.EmbeddingStatement.UNUSABLE_SEEDS` |
| 5b's corpus survivors' and seeds' extraction metrics | 12 | `EmbeddingStatement.CORPUS_METRICS`, `SEED_METRICS` |

**Per statement, not per kind**, except builds, for which the probe measured one rule over seven indexes and four widths. A read's r depends on how many columns it returns and where they come from, so two reads of the same plan can differ (5 and 12 above).

**Pinned against the bundled SQLite** by `StatementStepsPerRowTest` (Tests): it applies `schema.sql` to an empty database, writes synthetic rows, runs each statement at two sizes with a callback every step, and holds the difference between them to r a row, to within a hundredth of a step a row. It runs every `CREATE INDEX` in `schema.sql` and the build of `shingle_by_hash`, each counted read's SQL as the code issues it, and the drop. **It also pins the classification**: each counted read's plan names an index on `run_id` alone and no temp B-tree, each sorting read in §6 names a temp B-tree, and the bounds on the three tables of §1 grow with the run. A newer SQLite that changes a plan or a step count fails it, and that is when a statement changes form or an r changes.

**An r is pinned where every row the statement goes through is returned.** Where a condition returns fewer, the statement takes fewer steps a row, so the estimate falls behind the rows gone through and never runs ahead of them.

### 4. The lines

#### 4.1 A counted statement

```
<label>: about <X>% of <N> rows
```

- **`<label>`** names the stage as its own lines do and, after a comma, what the statement is doing, as ADR-192 §4's labels name the unit: `Stage 4b (redundancy resolution, building shingle_by_hash)`, `Stage 3 (content census, reading shingle rows)`. Start-up's is `Start-up (building index <index>)`.
- **`<N>`** is the cheap total of §1, grouped as ADR-192 §8's lines are. **`<X>`** is `⌊rows × 100 ÷ N⌋`, the rows being `min(N, ⌊steps ÷ r⌋)`.
- **The word "about" is the record's admission** that X is a count of SQLite's steps turned into rows, not a count of rows, and that N is a bound ("up to").
- **No line where N is zero**, and none for a statement SQLite did not call back during.

**The line before a counted read** is new for every counted read but stage 3's, and is written only where the run holds a row:

```
<stage> is reading <what>, over up to <N> rows
<stage> read <what> in <S> s
```

`<stage>` is the stage's own name, `Stage 4b (redundancy resolution)`; `<S>` is the statement's wall clock in seconds to one decimal, as ADR-187's. A counted read of a run with no row writes neither line: there is nothing to wait for (ADR-191 §1's rule, made general). **Stage 3's read keeps ADR-191's two lines word for word**, its progress lines falling between them.

#### 4.2 A build

**The before-line and the after-line of start-up's builds are ADR-187 §3's, unchanged.** Start-up's *"on a large database this takes minutes"* stays: the twelve minutes measured there are not known to be one index's (ADR-187's Not known).

**Stage 4b's line before its build states the worst measured and what a stop costs**, as ADR-187 §1's and ADR-191 §1's do. It now reads:

```
Stage 4b (redundancy resolution) is building shingle_by_hash over up to <N> shingle rows before it reads it; that took 39 minutes for 42833917 rows on a USB spinning disk, and stopping before it ends undoes it
```

*"On a large database this takes minutes"* understated the build of 2026-10-04 by a factor the operator could not have guessed, which is the treatment ADR-191 gave stage 3's line. `<N>` stays ungrouped, as in every line of ADR-187's family. The after-line, `... built shingle_by_hash in <S> s`, is unchanged.

**When a build's rows are all gone through, one line says so, and nothing more is written until the after-line** (the operator's decision 7):

```
<label>: all <N> rows gone through; writing the index says nothing more until it ends
```

- **When.** At the first callback where the rows reach `N − ⌈100,000 ÷ r⌉`: within the rows one callback covers. The steps after a build's last callback never make one more callback, so a count that waited for N exactly would almost never arrive (9,090 rows short of it at most, for 4b's build). The line takes the place of the progress line that callback would have written.
- **Once**, and after it no progress line. A build whose rows never come within one callback of N — a table rows were deleted from, so that `MAX(rowid)` overstates it — never writes it, and its after-line follows its last progress line.
- **Builds only.** A read has no silent end: its last callback is followed by its last rows and the after-line. The operator's decision was made for builds, and nothing here extends it.

#### 4.3 A timed statement

```
<stage> is <doing> <what>
<stage> <did> <what> in <S> s
```

`<doing>` and `<did>` are `reading` and `read` everywhere but `UnrecordedOccurrences.countOver`, which is `counting` and `counted`. No total and no progress line, and both lines whenever the statement is issued. A statement not issued, because its step is already recorded under its run or its gate is shut, writes nothing.

#### 4.4 The drop

**ADR-187 §1's two lines, and nothing between.** The handler is not set for it.

### 5. Cadence

**A counted statement's progress lines follow ADR-192 §8 over its N**: a line at the first callback at which the rows have gone at least `max(1, min(⌊5N/100⌋, max(1,000, ⌈N/100⌉)))` beyond the rows the last line stated, or beyond zero for the first. So no counted statement writes more than 100 progress lines, a build's line of §4.2 included. Rows advance by a callback's worth at a time, 9,090 or more, so over a small N every callback can write a line; over 42,833,917 rows a line falls every 428,340 rows or more, which is every 48th callback of the build.

### 6. The statements

Every statement ADR-192 §7 left to #411, and the start-up builds of ADR-187, read off the code at `7b25d04`. "Span" is ADR-191 §2's two statements on the table and run named.

| Stage | Statement, where it is issued | Form | Total, and its source | r | `<what>` |
|---|---|---|---|---:|---|
| start-up | each `CREATE INDEX` of `schema.sql` the database lacks, on a table that holds a row (`StartUpIndexAnnouncement`) | counted | `MAX(rowid)` of the table (ADR-187 §3) | 8 + c | ADR-187's lines; label `Start-up (building index <index>)` |
| 1 | `SurvivorDrain.drain` in `BrokenOrOutOfScope.verdictSurvivors` | timed | none: `Ledger.survivors` is a paged reader, many statements, and its anti-join is what counts | — | `the survivors to check` |
| 1 | `SurvivorDrain.drain` in `ContentIdentityResolution.resolve` | timed | the same | — | `the survivors to size` |
| 2 | `UnrecordedOccurrences.countOver`, in `ExtractionItemProcessor`'s constructor | timed | none: it is the count | — | `the survivors still to read` |
| 2 | `ShingleHashIndex.drop` | no callback | ADR-187 §1 | — | ADR-187's lines |
| 2 | `Ledger.extractionFailures`, in `ReviewListListener` | timed | none: a join | — | `the occurrences it could not read` |
| 3 | `DocumentFrequency.drainSurvivors` | timed | none | — | `stage 2's survivors for the shingle frequencies` |
| 3 | `DocumentFrequency.measure`'s read of `shingle` | counted | span on `shingle` (ADR-191) | 7 | ADR-191's lines; label `Stage 3 (content census, reading shingle rows)` |
| 3 | `ConfidenceDistribution.drainSurvivors` | timed | none | — | `stage 2's survivors for the confidence distribution` |
| 3 | `ConfidenceDistribution.measure`'s read of `extraction_metric` | counted | span on `extraction_metric` | 7 | `the extraction metrics`; label `Stage 3 (content census, reading extraction metrics)` |
| 4b | `ShingleHashIndex.build`, in `ShingleHashIndexBuild` | counted | `MAX(rowid)` of `shingle` (ADR-182 §2.4) | 11 | §4.2's lines; label `Stage 4b (redundancy resolution, building shingle_by_hash)` |
| 4 (4a or 4b) | `BoilerplateShingles.resolve`, a one-row lookup of `shingle_corpus_size` and the read of `shingle_document_frequency`, called once an invocation from `RedundancyBoilerplate`'s constructor | timed, one call, in that constructor | the span costs the read (Measurements) | — | `the boilerplate shingles`, under `<stage>` `Stage 4 (content redundancy)` |
| 4b | `RedundancyResolution.loadSignedOccurrenceIds` | counted | span on `minhash_signature` | 5 | `the signed occurrences`; label `Stage 4b (redundancy resolution, reading signed occurrences)` |
| 4b | the `signature_band` read in `nearDuplicateCandidates` | timed | the span costs the read | — | `the signature bands` |
| 4b | the `extraction_metric` read in `loadOccurrenceProfiles` | timed | none: an `IN` list | — | `the near-duplicates' extraction metrics` |
| 4b | `RedundancyResolution.loadDocumentFrequency` | timed | the span costs the read | — | `the shingle document frequencies` |
| 5b | `SeedCorpusComparison.measure`'s drain of `Ledger.survivors` | timed | none | — | `the corpus survivors` |
| 5b | its drain of `Ledger.occurrencesOf` the seed walk | timed | none | — | `the seed walk's occurrences` |
| 5b | its `unusable_seed` read | counted | span on `unusable_seed` | 5 | `the unusable seeds`; label `Stage 5b (seed/corpus comparison, reading unusable seeds)` |
| 5b | its `extraction_metric` read under stage 2's run | counted | span on `extraction_metric` | 12 | `the corpus survivors' extraction metrics`; label `Stage 5b (seed/corpus comparison, reading corpus metrics)` |
| 5b | its `extraction_metric` read under the measurement run | counted | span on `extraction_metric` | 12 | `the seeds' extraction metrics`; label `Stage 5b (seed/corpus comparison, reading seed metrics)` |
| 5c, 5d | `ItemStreamReaders.drain` of the survivors, and of the seed walk's occurrences | timed | none | — | `the corpus survivors`; `the seed walk's occurrences` |
| 5c, 5d | `UnusableSeeds.forRun` | timed | temp B-tree | — | `the unusable seeds` |
| 5e | `RelevanceDistribution.embedderIdentityFor` | timed | none: no run | — | `the embedder identities` |
| 5e | `RelevanceScoring.scoredBelow` | timed | temp B-tree | — | `the scores below the floor` |
| 5e | `RelevanceLabels.forSeedSet`, through `RelevanceFloor` | timed | none: no run | — | `the recorded answers` |
| 5f | `ItemStreamReaders.drain` of the survivors | timed | none | — | `the corpus survivors` |
| 5f | `Clustering.partitions` | timed | temp B-tree | — | `the seed partitions` |
| 5f | `Clustering.membersOf`, once a partition | timed | none: two conditions | — | `the members of partition <P> of <M>` |
| 5f | `DocumentClusters.sizesFor`, once a partition | timed | none: grouped | — | `the cluster sizes of partition <P> of <M>` |
| report | `RelevanceDistribution.measure` | timed | temp B-tree | — | `the scores` |
| report | `RelevanceDistribution.spreadOf` | timed, one call | temp B-tree | — | `the scores against the answers` |
| report | `RelevanceDistribution.anyEmbedderIdentity`, `embedderIdentityFor` | timed | none: no run | — | `the embedder identities` |
| report | `RelevanceLabels.forSeedSet` | timed | none: no run | — | `the recorded answers` |
| 6a | `DocumentClusters.forRun`; `Clusters.forRun`, on either branch | timed | temp B-tree | — | `the cluster membership`; `the recorded clusters` |
| 6b | `DocumentClusters.forRun`; `Clusters.forRun` | timed | temp B-tree | — | `the cluster membership`; `the recorded clusters` |
| 6b | `SynthesisDocs.forRun` in `ClusterGeneration.write` | timed, one call | temp B-tree and a span that costs the read | — | `the clusters already written` |
| 6b | `ClusterFaults.forRun` in `ClusterGeneration.write` | timed | temp B-tree | — | `the standing faults` |
| 6b | `SynthesisDocs.forRun` and `ClusterFaults.forRun` in `writeDeliverable` and `whyUnwritten` | timed | temp B-tree | — | `the clusters written`; `the faults recorded` |

- **The boilerplate read names stage 4 with no letter.** `RedundancyBoilerplate` is `@JobScope`, and its constructor calls `BoilerplateShingles.resolve`, so the read happens once an invocation, wherever the bean is first asked for its hashes. That is stage 4a's writer, `RedundancySignatureItemWriter.write`, at its first chunk, on an invocation where 4a has work; and 4b's tasklet, `RedundancyResolutionTasklet`, where 4a has none, being already recorded, and 4b has. On an invocation where neither has work nothing asks, and nothing is read or said. A letter would be false on one of the two paths, and naming whichever stage asked would make the line's words depend on a bean's order of creation. Both steps are stage 4's, under one run (`StageRuns.contentRedundancy`), and `StageModules` names that stage `content-redundancy`, so the line is `Stage 4 (content redundancy) is reading the boilerplate shingles` and `Stage 4 (content redundancy) read the boilerplate shingles in <S> s`. `pipeline` writes both lines around the call in the constructor, and `similarity` is not asked for a callback.
- **Stage 2's count of the survivors still to read is timed over the whole expression** at `ExtractionItemProcessor`'s constructor, `UnrecordedOccurrences.countOver(ledger, extractionRun, extractionMetrics.occurrencesForRun(extractionRun))`: the read of the occurrences already recorded and the drain or count after it, as one wait, since the operator sees one.
- **6a and 6b's `<stage>`** is new wording, `Stage 6a (arrangement)` and `Stage 6b (generation)`, as ADR-192 §4's labels are.
- **`<P> of <M>`** is ADR-192 §5's numbering of 5f's partitions.
- **Outside the job's steps nothing is announced**: `NextAction`'s two `forRun` calls, in the line an invocation ends on, and `LabelIngestion`'s statements, under `vespera label`, which is not a stage. As in ADR-192 §7.
- **A statement in no row here and in no list of ADR-192 is not covered by this record**, and the survey that made this table was of ADR-192 §7's list and #411's, not of every statement in `src/main`. `Ledger.survivorCount`, where it sizes a counter (`ByteLevelReductionTasklet`, `RedundancySignatureItemWriter`, and `UnrecordedOccurrences` when nothing is recorded yet, the last inside stage 2's timed count above), and `ExtractionFaults.occurrencesForRun`, in stage 2's reader before its first chunk (`ExtractionJobConfiguration`), are the ones found while making it. They are left to [#429](https://github.com/algernon28/vespera/issues/429) (What this does not decide).

### 7. Who writes the line, and how it gets there

**`pipeline` writes every line here; a capability module writes none** (ADR-188 to ADR-192). `pipeline.StatementProgress`, a new final class beside `StageProgress`, holds one counted statement's label, N, r and kind, made by `StatementProgress.ofBuild(String label, long rowsUpTo, int stepsPerRow)` or `ofRead(…)` with the same arguments, and is told the steps taken through `stepsTaken(long steps)`; it writes §4.1's and §4.2's lines on §5's cadence through its own logger, and nothing else. Every timed line, and every counted statement's before-line and after-line, is written where `pipeline` makes the call or hears the callback.

**A statement inside a capability method is announced through a callback that module owns**, since `pipeline` cannot see it: **one interface and one enum for each module that has such a statement**, `<Module>StatementProgress` and `<Module>Statement`, in `corpus`, `extraction`, `similarity`, `embedding` and `synthesis`.

- **The enum** names the module's statements of §6, and gives each counted one its r, `OptionalInt stepsPerRow()`, empty for a timed one. It is the one place each r is declared, beside the module's SQL. Its constants, in the order the statements are issued: `CorpusStatement` `SURVIVORS_TO_CHECK`, `SURVIVORS_TO_SIZE`; `ExtractionStatement` `SURVIVORS`, `EXTRACTION_METRICS` (7); `SimilarityStatement` `SHINGLE_HASH_INDEX_BUILD` (11), `FREQUENCY_SURVIVORS`, `SHINGLE_ROWS` (7), `SIGNED_OCCURRENCES` (5), `SIGNATURE_BANDS`, `NEAR_DUPLICATE_METRICS`, `DOCUMENT_FREQUENCY`; `EmbeddingStatement` `CORPUS_SURVIVORS`, `SEED_OCCURRENCES`, `UNUSABLE_SEEDS` (5), `CORPUS_METRICS` (12), `SEED_METRICS` (12); `SynthesisStatement` `WRITTEN`, `STANDING_FAULTS`. `StartUpIndexAnnouncement` declares the build's 8 as `INDEX_BUILD_STEPS_BEYOND_COLUMNS`.
- **The interface** has three methods, all with default bodies that do nothing: `statementStarting(<Module>Statement statement, OptionalLong rowsUpTo)`, called once before the statement, with its total for a counted one (empty where the run holds no row) and empty for a timed one; `stepsTaken(<Module>Statement statement, long steps)`, from each callback of a counted one, through `StatementSteps`; and `statementEnded(<Module>Statement statement)`, once after it, on every path but one that throws.
- **Where it reaches the method**: `corpus.HashingProgress` and `CheckingProgress`, `similarity.FrequencyProgress` and `ResolutionProgress`, and `synthesis.GenerationProgress` extend their module's interface, so no signature changes for those. `ShingleHashIndex.build`, `ConfidenceDistribution.measure` and `SeedCorpusComparison.measure` gain an overload taking it; `BoilerplateShingles.resolve` does not, being timed as one call by `pipeline` (§1, §6); the old signatures call the new with an implementation that does nothing, as ADR-192 §5's did.
- **One for each module, not one for each class**, which is not ADR-192 §5's rule for loops: a statement's callbacks have one shape whatever the statement, so a class's interface would gain three methods a statement, `RedundancyResolution`'s twelve. The enum says which statement, as a loop's own method does in ADR-192.
- **`DocumentFrequency.measure` asks `shingleRowsUpTo` itself** and hands it in `statementStarting(SHINGLE_ROWS, …)`, immediately before its read and after its drain, and `pipeline` writes ADR-191's reading line then (the amendment above). A run with no shingle row hands an empty total, and `pipeline` writes no reading line, as before.
- **Start-up's builds and the drains and counts `pipeline` issues itself** need no callback: `StartUpIndexAnnouncement` uses `StatementSteps` and `StatementProgress` directly, and `ItemStreamReaders.drain` and `UnrecordedOccurrences.countOver` are timed where `pipeline` calls them.

### 8. What a stop during a statement costs

**The handler never stops a statement.** `progress()` returns 0 on every call, so SQLite never ends one early on its account, and this record gives the operator no way to stop a statement cleanly; that was never asked.

A stop is what it was before this record: the process ends. **A build's transaction is undone**, the index is not there, and the next invocation builds it again from its first row, with every line again (ADR-187 §1). **A read loses only the time spent**: nothing was written before it ended (ADR-191 §1). The handler holds nothing that outlives the process, and the database holds nothing of it. A statement that fails clears the handler in the `finally` before the step fails as it did.

### 9. Two parts, each landed on its own

Each part compiles, passes and is gated alone, and leaves a green tree. Both land before the archive's next run (the operator's decision 6).

| Part | Statements | Modules | Run ids moved | Effort |
|---|---|---|---|---|
| (a) | the mechanism, `StatementProgress`, start-up's builds, 4b's build, stage 3's read, and stage 4's read of the boilerplate shingles | `ledger`, `similarity`, `pipeline` | stages 2 to 6b | medium: the mechanism and four statements, with the line formats as their spec |
| (b) | every other statement of §6 | `corpus`, `extraction`, `similarity`, `embedding`, `synthesis`, `pipeline` | stages 1 to 6b | large in count, small each: most are two timed lines where `pipeline` already makes the call |

**Part (b) owes one thing the table above does not show.** At `ExtractionItemProcessor.java` lines 157 and 158, the timed span for `the survivors still to read` covers the whole `UnrecordedOccurrences.countOver(…, extractionMetrics.occurrencesForRun(extractionRun))` expression, the read inside the argument included (§6). The boilerplate read, timed in `RedundancyBoilerplate`'s constructor under `Stage 4 (content redundancy)` with no change to `similarity`, landed with part (a) (§6).

**Which run ids move**, read off `StageModules`: `corpus` is stage 1's, and every later stage names its upstream run (ADR-048), so part (b) moves every run id from stage 1 on. `ledger` is in no stage's. **This adds no replay to the archive's next run**, which waits for this record and already re-mints stages 1 to 6b under ADR-192.

## Why this shape, and what the others cost

- **A heartbeat on a timer.** Refused by the operator (decision 2), and by ADR-187 §1 before: it reports the clock, and it would go on if SQLite hung.
- **The growth of the database file or its write-ahead log as the measure.** The ticket's third candidate. Not taken: the callback measures the statement itself, and file growth was never shown to track a build.
- **Counting rows in Java, in a read's row callback.** Exact for a read, and ADR-191 named it. Not taken: the operator decided that X comes from the callback count through a pinned ratio (decision 1), and one mechanism for builds and reads is one thing to test. It would give a build nothing, since a build returns no row.
- **The handler set by `pipeline` on the transaction's connection, around a capability call**, so that no capability module names SQLite. Not taken: it counts every statement of the call, not the one announced, and the build runs outside any transaction (ADR-182 §2.3), so it would need one opened for it. And it depends on the call's statements finding the transaction's connection, which the test profile's pool of one cannot tell from a second connection.
- **`StatementSteps` copied into each module.** Four copies of one finally block, each with a test, against one class in the module ADR-177 already gave the driver's plumbing to.
- **A cheap total from an index with columns after `run_id`.** It is not cheap (Measurements). An index on `run_id` alone on `signature_band`, `shingle_document_frequency` or `call_exemplar` would make it cheap; that is a schema change this record does not make (What this does not decide).
- **Counting sorted reads through both of their loops.** Their steps fall in two loops with SQLite's sort between, which gives no callback; the operator's decision 7 announces that kind of silence for a build, and extending it to reads would be this record's own decision. Each sorted read here goes through one run's rows of a table of one row an occurrence, a cluster or a seed, and takes fewer than 100,000 steps on the figures of 2026-10-04 (4,275 survivors at 15 steps a row is 64,125), so it would write no progress line on the archive in any case.
- **Aborting a statement from the handler on a stop.** Not asked, and it would leave a build to undo and a read to repeat, which a stop already does.

## Consequences

- **Production code changes in six modules** (§9); `profile` is not touched. **No schema version moves** (ADR-059).
- **Lines change by addition, with three exceptions**: stage 4b's line before its build is reworded (§4.2); stage 3's reading line is written from the callback, after the drain of stage 2's survivors rather than before it; and timed statements add two lines each where a step issues one, so 5f writes four more lines for each partition. Every counted statement writes at most 100 progress lines (§5).
- **A counted statement costs SQLite one call into Java every 100,000 steps**, about 4,700 for the build of 2026-10-04, and two bound statements of about 20 steps each before a counted read.
- **A counted read whose condition removes rows lags**: its X falls behind the rows gone through (§3). `loadSignedOccurrenceIds` and the 5b reads have no such condition; stage 3's survivor filter and `ConfidenceDistribution`'s are in Java, after the row is returned, so they do not lag.
- **The archive's next run waits for both parts** (the operator's decision 6).
- **`AGENTS.md`, `CONTEXT.md` and `README.md` need no new term or claim.** #411 is not a defect, so `AGENTS.md`'s paragraph of closed defects does not change.

## Tests

**Where the tests are, and why**, as in ADR-192: a test that names a type or method this record adds would stop the whole test tree compiling until its part lands, so it is kept as a complete file at `docs/adr/0193/tests/<part>/` followed by the path it takes in the repository (`src/test/java/…`), and `spec-implementer` moves it into `src/test` with its part. `docs/` is not compiled, and `docs/check-claims.mjs` reads only the top level of `docs/adr`. **Both parts have landed**, and every test that was parked is in `src/test`; nothing remains under `docs/adr/0193/tests/` (note corrected with [#411](https://github.com/algernon28/vespera/issues/411), when part (b) landed).

| Part | Test | Where | State on this branch | Pins |
|---|---|---|---|---|
| — | `pipeline.StatementStepsPerRowTest` | `src/test` | green | r for every `CREATE INDEX` in `schema.sql` and `shingle_by_hash` (8 + c); r for each counted read of §3; each counted read's plan, through an index on `run_id` alone and no temp B-tree; each sorting read of §6 through a temp B-tree; the bound costing the same at two sizes on the four tables §1 counts and growing with the run on the three it does not; the drop's steps the same at two sizes and fewer than one callback |
| — | `ledger.ProgressHandlerOnAPooledConnectionTest` | `src/test` | green | HikariCP's connection refused by `setHandler` and `clearHandler`, accepted unwrapped; a handler not cleared called for the next borrower; cleared, not called; whether one is set read back |
| (a) | `pipeline.StatementProgressInvocationTest` | `src/test` | green since part (a) | a whole job over a corpus of about 60,000 shingle rows: 4b's reworded line; progress lines over `MAX(rowid)`, rising, under 100% and at most 100; the line that the rows are gone through, once, after the last progress line and before the built line; stage 3's progress lines between ADR-191's two; the pool's connection carrying no handler after the invocation, though one was set; a stage 2 under a new run writing nothing between the drop's two lines |
| (a) | `pipeline.StatementProgressInvocationTest`, the same whole job with stage 4's gate open | `src/test` | green since part (a) | `Stage 4 (content redundancy) is reading the boilerplate shingles` once, then `Stage 4 (content redundancy) read the boilerplate shingles in <S> s` once after it, both before `Stage 4b (redundancy resolution) finished` |
| (a) | `pipeline.StartUpSaysWhichIndexItBuildsTest`, its new fourth test, over 50,000 shingle rows written before the start | `src/test` | green since part (a) | start-up's progress lines for `shingle_by_run_id`, labelled `Start-up (building index shingle_by_run_id)`, between ADR-187's two lines, rising, under 100% and at most 100; the line that the rows are gone through, once, after the last progress line and before the built line; no handler left on the connection. Its first three tests are unchanged and stay green over the larger table |
| (a) | `ledger.StatementStepsTest` | `src/test` | green since part (a) | the handler cleared after the statement and after one that throws, on a pool of two outside a transaction; steps reported in multiples of 100,000; the statement run on the connection the handler is on; a consumer that throws not stopping the statement |
| (a) | `pipeline.StatementProgressTest` | `src/test` | green since part (a) | the line `about X% of N rows`; the cadence over a small and a very large N, and 42,833,917 rows at 11 a row writing at most 100 lines; a build's gone-through line once, at `N − ⌈100,000 ÷ r⌉`, and nothing after it; a read capped at 100% and with no such line; nothing over N of zero |
| (b) | `pipeline.StatementStepsPerRowAreTheDeclaredOnesTest` | `src/test` | green since part (b) | each declared r equal to the one `StatementStepsPerRowTest` measures, and each timed statement declaring none |

**Owed with part (b), not written here, and written since** (note corrected with [#411](https://github.com/algernon28/vespera/issues/411); [ADR-204](0204-every-line-of-adr-193s-part-b-is-written-out-and-its-table-is-read-again-against-the-code.md)'s Tests say where each went, and ADR-200 withdrew stage 1's and `corpus`'s): a claim in each existing whole-job test of the stage concerned that its timed and counted statements write §4's two lines, once each, in the order the step issues them — `StageOneReportsItsLoopsInvocationTest`, `RedundancyResolutionReportsItsProgressInvocationTest`, `StageFiveReportsItsProgressInvocationTest`, `GenerationReportsItsProgressInvocationTest`, and the 5b and review-list tests — and a contract test in each of the five modules that its callbacks come in §7's order on every path. Those depend on the shape `spec-implementer` gives each method, and §6's table is their spec.

**Not pinned, and why:**

- **The silent tail of a build on a spinning disk.** No test runs on one. The line that announces it is pinned; how long the silence lasts is the archive's to show.
- **A progress line from a sorted or timed read**: there is none by design.
- **A build's progress lines on the archive**: the invocation test's N is about 60,000, so every callback writes a line; the cap over 42,833,917 rows is pinned in `StatementProgressTest`.

## Not known

- **How long the silent end of the archive's build will be.** The probe's file was new and in order; the archive's is neither. 43% of the build was the share on `H:` in the probe.
- **How the 26 minutes 40 seconds of 4b's resolution divide** between its statements, its loops and its writes. The next run's lines will say.
- **Whether the steps a row hold for a database much larger than the probe's**: the operator's probe found the same 90 a million at 5,000,000 and 20,000,000 rows for one build; nothing else was measured above 90,000 rows.
- **How long a callback costs on `H:`**: about 4,700 calls from SQLite into Java over the archive's build, each a few microseconds on the probe's machine; not measured there.

## What this does not decide

- **Shortening a build or a read on a spinning disk.** The operator split it into a ticket of its own (decision 5).
- **An index on `run_id` alone for `signature_band`, `shingle_document_frequency` and `call_exemplar`**, which would give their reads a cheap total and make them counted. A schema change, with its own build at start-up.
- **Statements no list named**: `Ledger.survivorCount` and `ExtractionFaults.occurrencesForRun`, left to [#429](https://github.com/algernon28/vespera/issues/429), and any other statement in `src/main` this survey did not reach (§6).
- **A clean stop during a statement** (§8).
