# ADR-182 — Stage 2 writes shingles without the by-hash index, and stage 4b builds it before containment retrieval reads it

- **Date**: 2026-10-03
- **Status**: accepted
- **Amends**: [ADR-081](0081-minhash-retrieves-shingle-sets-judge-128-permutations-in-16-bands-and-containment-gets-its-own-index.md) — **when its containment index exists, and nothing else.** *"Containment gets its own index"* was built by `schema.sql` at every start and grown one row at a time by stage 2. From this record it does not exist while stage 2 writes, and stage 4b builds it, whole, before its first read. Its name, `shingle_by_hash`, and its columns, `(run_id, shingle_parameter_identity, shingle_hash)`, are unchanged. So are the 32 rarest shingles, the 24-of-32 rule, the exact scoring, both thresholds and the banding.
- **Rests on**: [ADR-181](0181-a-stopped-stage-2-resumes-from-what-its-committed-chunks-recorded-and-redoes-only-the-rest.md). Stage 2 removes the index at the point where that record decides whether the step has work to do and keeps or discards rows. A resumed stage 2 is read in §3 under that record's rules.
- **Rests on**: [ADR-173](0173-every-column-that-references-a-file-occurrence-a-walk-or-a-run-carries-an-index.md). Its rule needs an index led by `shingle.run_id`, and `shingle_by_hash` was the only one. §2.1 adds the one-column index the rule asks for. Its §3 is why no schema version moves.
- **Rests on**: [ADR-115](0115-a-re-walk-that-observed-nothing-new-is-discarded-and-a-run-is-continued-under-its-own-id.md) and [ADR-116](0116-a-runs-completion-is-recorded-per-step-because-several-steps-share-one-run.md) (a finished step does no work), and [ADR-127](0127-a-database-lock-is-waited-out-by-sqlites-busy-timeout-not-by-hikaris-connection-timeout.md) and [ADR-140](0140-stage-2-converts-eight-file-occurrences-at-a-time-and-consecutive-means-consecutive-on-the-drain.md) §3 (one writer, on the thread the step runs on).
- **Settles** [#381](https://github.com/algernon28/vespera/issues/381). It answers the question #378's record leaves open in its §6 (ADR-180, in [#380](https://github.com/algernon28/vespera/pull/380)). This decision does not depend on that record: it holds under the rollback journal and under the write-ahead log alike. Its measurements were taken with the datasource URL #380 ships, because that is what will run.

**Two words here are SQLite's.** A *WAL checkpoint* is SQLite copying its write-ahead log back into the database file. It is not a walk's **Checkpoint** (ADR-055). An *index* here is always a SQLite index on a table, named where it matters. It is never the ledger.

## Context

### What was measured

While #378 was being settled, its benchmark wrote one stage-2 chunk into a synthetic database: 16 occurrences of 2,500 shingle rows each, in one transaction, into a database of 10,000,000 shingle rows. With `shingle_by_hash` in place, the chunk cost 3,906 ms. Without it, the chunk cost 96 ms. Building the index once, afterwards, over all 10,000,000 rows took 13.7 s. Every new row lands at a random place in a by-hash index far larger than any page cache, so one chunk changes about 30,000 scattered index pages. Over the archive's 10,406 occurrences, about 650 chunks, that is roughly 40 minutes of stage 2, and it grows with the database.

### Who reads `shingle_by_hash`

Every statement in `src/main` that reads or writes `shingle`, at `bb62751`, with the plan SQLite 3.53.2 chooses for it on the shipped schema (`EXPLAIN QUERY PLAN`, on the 10,000,000-row database described under "Measurements"):

| Statement | Where | Stage | Plan as shipped |
|---|---|---|---|
| `INSERT INTO shingle …` | `Shingler.write` | 2 | maintains both indexes, row by row |
| `DELETE FROM shingle WHERE run_id = ?` | `Shingler.discardForRun`, called by `extractionReader` | 2, at its start | `SEARCH shingle USING COVERING INDEX shingle_by_hash (run_id=?)` |
| `SELECT occurrence_id, shingle_parameter_identity, shingle_hash FROM shingle WHERE run_id = ?` | `DocumentFrequency.measure` | 3 | `SEARCH shingle USING INDEX shingle_by_hash (run_id=?)` |
| `SELECT shingle_hash FROM shingle WHERE occurrence_id = ? AND run_id = ? AND shingle_parameter_identity = ?` | `RedundancySignatures.distinctiveShingleSet`, `ShingleSetCache.load` | 4a, 4b | `SEARCH shingle USING INDEX shingle_by_occurrence (…)` |
| `SELECT COUNT(*) FROM shingle WHERE occurrence_id = ? AND run_id = ? AND shingle_parameter_identity = ?` | `RedundancyResolution.rawShingleSetSize` | 4b | `SEARCH shingle USING COVERING INDEX shingle_by_occurrence (…)` |
| `SELECT occurrence_id FROM shingle WHERE run_id = ? AND shingle_parameter_identity = ? AND shingle_hash IN (…) GROUP BY occurrence_id HAVING COUNT(DISTINCT shingle_hash) >= ?` | `RedundancyResolution.containmentCandidates` | 4b | `SEARCH shingle USING INDEX shingle_by_hash (run_id=? AND shingle_parameter_identity=? AND shingle_hash=?)` |
| `SELECT 1 FROM shingle WHERE run_id = ?`, the lookup SQLite makes for a foreign key (ADR-173) | a delete of a `run` row; nothing deletes one today | — | `SEARCH shingle USING COVERING INDEX shingle_by_hash (run_id=?)` |

**One reader needs it: containment retrieval, in stage 4b.** With the index, that query took a median of 0.14 ms over 20 calls. Without it, SQLite reads every row of the run for each call: a median of 2,400 ms over 3 calls on the same database. Stage 4b makes one such call per signed occurrence whose rare shingles fill the sample, so on the archive's 10,406 occurrences that would be hours. The index has to exist before that reader runs.

**Three others use it only because it is the only index that `run_id` leads.**

- **Stage 3 is a reader of it, and is slowed by it.** `DocumentFrequency` reads all of a run's rows. Through `shingle_by_hash` it visits them in hash order, so each row is fetched from a different table page: 38.1 s for 10,000,000 rows. Through an index on `run_id` alone, the same read visits them in the order they were written: 5.4 s.
- **Stage 2's discard at its start** deletes by run through it. ADR-181 takes that call away: `Shingler.discardForRun` loses its only caller.
- **ADR-173's rule** counts it as the index `shingle.run_id` leads. Nothing deletes a `run` row today, so no lookup reads it yet, but the rule binds the schema.

Nothing reads `shingle_by_hash` during stage 2 once ADR-181 is built. Nothing needs it before stage 4b.

## Decision

### 1. The index is absent while stage 2 writes and present from stage 4b on

`shingle_by_hash` exists for one purpose: containment retrieval in stage 4b. It is not maintained while shingles are being written. It is built once, by the step that reads it, over every row the table then holds.

### 2. What changes, and where

**2.1 `schema.sql` stops creating `shingle_by_hash`, and creates `shingle_by_run_id`.** The new index is `CREATE INDEX IF NOT EXISTS shingle_by_run_id ON shingle (run_id);`, directly after `shingle_by_occurrence`, with the comment ADR-173 §2 gives a column that references `run`. It keeps ADR-173's rule true when the by-hash index is absent. It also gives stage 3's read the plan measured above. The comment above the old `CREATE INDEX` moves to wherever §2.3's statement is written, and says that `schema.sql` does not create the index, and why.

**2.2 Stage 2 drops it, once, before its first chunk, when it has work to do.** Where `extractionReader` has found that the `extraction` step is not finished under its run, beside ADR-181's discard and outside any chunk transaction, stage 2 runs `DROP INDEX IF EXISTS shingle_by_hash`. Not when the step is finished: it then writes nothing, and the index is left as it is. Not behind a failed health check: nothing in stage 2 runs then (#319). Not inside a chunk: a chunk that rolled back would restore it.

**2.3 Stage 4b builds it, once, before its first read.** When the `content-redundancy` step's gate is open and the step is not finished under its run, it runs

```sql
CREATE INDEX IF NOT EXISTS shingle_by_hash ON shingle (run_id, shingle_parameter_identity, shingle_hash);
```

**in a transaction of its own, committed before resolution's transaction opens.** That tasklet runs inside one transaction (`TaskletSteps.taskletStep`). A build inside it would be rolled back with any failure of the resolution and paid again by the next invocation. It would also hold the whole index in one transaction with all of the resolution's writes. And a second transaction cannot be opened inside a step's own, which ADR-111 found while settling generation. So the build runs before that transaction: in a `beforeStep` listener on the step, for example, behind the same gate and the same finished-step check the tasklet applies. Asking for stage 4's run there mints nothing the tasklet would not mint a moment later. `startRun` is mint-or-continue (ADR-115).

`IF NOT EXISTS` is the whole of the check. The step builds whenever the index is missing and builds nothing when it is present, whoever left it there.

**2.4 The build says that it is starting and when it has finished.** Two INFO lines, in the voice of the step's other lines:

```
Stage 4b (redundancy resolution) is building shingle_by_hash over up to <N> shingle rows before it reads it; on a large database this takes minutes
Stage 4b (redundancy resolution) built shingle_by_hash in <S> s
```

`<N>` is `SELECT MAX(rowid) FROM shingle`. That answer is free, where `COUNT(*)` would read a whole index of the largest table in the database before the build reads it again. It is exact unless rows have been deleted, so the line says "up to". `<S>` is the wall clock of the `CREATE INDEX` statement, in seconds with one decimal. Both lines are written only when the index was missing. Where it was present, nothing is built and nothing is said.

These two lines are the operator's evidence that the run has not hung. They sit where every stage's lines sit, between stage 4a's closing line and stage 4b's `starting under run` line.

**2.5 The drop and the build live in `similarity`.** `similarity` owns `shingle` and its indexes (ADR-041), so the statements that drop and build `shingle_by_hash` are written there, once, beside the table they belong to. `pipeline` calls them at the two points §2.2 and §2.3 name. The operator chose this on 2026-10-03, over keeping the code in `pipeline`, knowing what it moves:

- **A change under `similarity/` moves the implementation version of stage 2, and of stages 3 and 4** (ADR-058, `StageModules`). Every stage-2 run id changes once, and every later run id with it.
- **So the first invocation after the upgrade does stage 2 again, over cached conversions, and every stage after it.** No file goes back to Docling, because the extraction cache is keyed outside the run (ADR-070). That replay is a whole stage 2 under a new run id, not a resume: the run the previous build left, finished or not, is not continued, and its rows stay where they are (ADR-077). With this record in place the replay no longer maintains the by-hash index row by row, so it costs what writing the rows costs.
- **The operator accepted the same trade for ADR-181, and the two land together**, so the run ids move once for both, not twice.

**A whole-archive run in progress should finish stage 2 on the build it started with before this one is installed.** A stage 2 stopped partway under the old build is not resumed by the new one, because its run id is not the new build's. Finishing it first loses nothing either: the new build replays stage 2 once whatever state the old run is in, and a finished stage 2 has converted every file, so the replay is served entirely from the extraction cache.

### 3. What the next invocation finds, and what it does

The table describes invocations under one build. The first invocation after installing this one is the replay §2.5 describes: stage 2 under a new run id, which the sixth row covers.

The decisions are made from what `sqlite_master` holds at the moment, through `IF EXISTS` and `IF NOT EXISTS`, and never from a record of what an earlier invocation did. A `CREATE INDEX` or `DROP INDEX` is one transaction, like any other write. A process that dies inside one leaves the database as it was before it began. So does a power cut, under either journal mode.

| The invocation before stopped… | The next one finds | and does |
|---|---|---|
| **Inside stage 2, partway.** | Stage 2 not finished under its run. Its committed chunks' rows. No `shingle_by_hash`, because §2.2 dropped it before the first chunk. | Resumes stage 2 under ADR-181. `DROP INDEX IF EXISTS` finds nothing to drop. The rest of the chunks are written with `shingle_by_occurrence` and `shingle_by_run_id` alone. Stage 3 reads through `shingle_by_run_id`. Stage 4b, once its gate is open, builds the index over every row the stage recorded, across both invocations, then reads it. |
| **After stage 2 finished and before stage 4b ran.** The stage-4 gate was shut, or the invocation stopped in stage 3 or 4a. | Stage 2 finished. No `shingle_by_hash`. | Stage 2 does no work and drops nothing (ADR-115, ADR-116). Stage 4b, once its gate is open, builds the index and reads it. Nothing is converted again. |
| **Inside the build.** | Stage 4b not finished. No `shingle_by_hash`: the build's transaction was rolled back. | Stage 4b builds it again from the start, and says so again. |
| **Inside stage 4b's resolution, after the build had committed.** | Stage 4b not finished. `shingle_by_hash` present. | `IF NOT EXISTS` builds nothing. Stage 4b discards its own rows and resolves again (ADR-116). |
| **Nowhere: everything finished, and nothing has changed.** | Every step finished under the same run ids. `shingle_by_hash` as stage 4b left it. | Nothing is dropped, built or said. The index is left as it is. |
| **Nowhere, but a profile value or the corpus has changed, so stage 2 is under a new run id.** | A stage-2 run with nothing under it. `shingle_by_hash` built by an earlier run's stage 4b. | Drops it before the new run's first chunk, writes the new run without it, and stage 4b of the new stage-4 run builds it again, over every row in the table. The earlier run's rows stay where they are (ADR-077). |
| **And a value is then put back, arriving at the earlier run again (ADR-156).** | The earlier runs, finished as they were. No `shingle_by_hash`, which the later stage 2 dropped. | Stages 2 to 4 of the earlier runs do no work if they are finished. If stage 4b of the earlier run is not finished, it builds the index first. |

The last two rows are why the build belongs to the reader and not to the end of stage 2. Completion is recorded per run. The index is one per table, over every run's rows. A build at the end of stage 2 would be skipped with the finished step, while a later run's stage 2 had dropped the index under it. Stage 4b would need a build of its own anyway.

**The single writer stays single.** The drop and the build are statements on the thread the step runs on, through the same pool and the same `JdbcTemplate` as every other write of that step. They run between that step's transactions, never beside one. No worker thread of ADR-140's touches either. A second invocation over the same working directory ([#364](https://github.com/algernon28/vespera/issues/364)) still meets the one writer it always met. While the build holds the write lock, that second invocation waits out `busy_timeout`, five minutes (ADR-127), and fails if the build is longer, as it would behind any long transaction. #364 stays open. Under the write-ahead log a reader, README's read-only query among them, is not blocked by the build.

### 4. What the build costs, and how long it takes on `H:`

**Measured**, on the hardware described under "Measurements", with the datasource URL #380 ships:

| Rows in `shingle` | `CREATE INDEX shingle_by_hash` | WAL checkpoint after it | Write-ahead log before that WAL checkpoint |
|---:|---:|---:|---:|
| 10,000,000 | 26,199 ms | 453 ms | 916,502,272 B |
| 25,000,000 | 66,910 ms, and 63,323 ms on a second build | 1,342 ms, and 1,415 ms | 2,291,317,432 B |

`DROP INDEX shingle_by_hash` took 569 ms at 10,000,000 rows and 804 ms at 25,000,000.

**Estimated, not measured: two to five minutes on `H:` for the archive's database.** ADR-173 counted 24,910,924 rows in `shingle` on its copy of the archive's database. The archive's database is not reachable from where this was measured, and nothing here ran on a spinning disk. The estimate rests on three things:

- **The 25,000,000-row build above took 65 s** with the whole database in the file cache, so that time is sorting and writing, not seeking.
- **On `H:` the build moves about 12 GB.** It reads the table, roughly 3 GB at that size. It writes its sorted keys out to temporary files and reads them back, roughly 4.6 GB. It writes the index into the write-ahead log, 2.3 GB, and the WAL checkpoint copies it into the database file, 2.3 GB more.
- **A desktop spinning disk moves 100 to 150 MB/s sequentially.** At that rate the transfers alone take 80 to 120 s, on top of the sorting.

It would take longer if the table's pages lie far apart on the disk, since a database grown chunk by chunk interleaves them with index pages. SQLite's temporary sort files go to the system's temporary directory, which may not be `H:`. Both are reasons to treat the upper end as soft. That is what §2.4's two lines are for.

The time is said where every stage's lines are: the two INFO lines of §2.4, immediately before stage 4b's `starting under run` line.

## Measurements

**Hardware, stated as far as it is known.** A cloud virtual machine: 4 vCPUs of an Intel Xeon at 2.10 GHz, 15 GiB of memory, Linux 6.18, OpenJDK 21.0.11, sqlite-jdbc 3.53.2.1. The disk is a virtio block device whose medium the guest cannot see. The kernel reports it as rotational, which it does for every virtio disk. The 3 GB database fits in the guest's file cache, and so, mostly, does the 9 GB one. The cache could not be emptied between measurements, so **every figure here is a warm-cache figure.** None of it was measured on the archive's database, and none on `H:`.

**The databases.** A synthetic database built from the `schema.sql` at `bb62751` by a throwaway probe adapted from #380's `JournalModeBenchmark` (`e182e49`). It was kept outside the repository, as AGENTS.md asks of probes. It holds 4,000 file occurrences of 2,500 random-hash shingle rows each: 10,000,000 rows, 2,949,865,472 bytes. The rows went in with both of the shipped indexes in place, so the index pages are as full as stage 2 leaves them, as in #380's benchmark. A second database of 10,000 occurrences, 25,000,000 rows, was built with `shingle_by_occurrence` and `shingle_by_run_id` only, for the build timings, since a build's cost does not depend on how full an index it replaces was.

**One repeat is one stage-2 chunk.** A single transaction holding 16 occurrences (`CHUNK_SIZE`) of 2,500 shingle rows each, inserted in batches of 5,000 as `Shingler` sends them, under the URL `…?foreign_keys=on&busy_timeout=300000&journal_mode=WAL&synchronous=NORMAL&journal_size_limit=536870912`. Ten repeats per variant, each variant from a fresh copy of the 10,000,000-row database. *Per chunk* is the total over the ten, including a closing `PRAGMA wal_checkpoint(TRUNCATE)`, divided by ten.

| Variant (indexes on `shingle` while the chunk is written) | Median transaction | Median commit | Per chunk, WAL checkpoint included | Largest write-ahead log |
|---|---:|---:|---:|---:|
| **Before**: `shingle_by_occurrence`, `shingle_by_hash`, as shipped | 1,589.9 ms | 876.9 ms | **1,694.8 ms** | 230,040,232 B |
| **After**: `shingle_by_occurrence`, `shingle_by_run_id` (§2) | 239.7 ms | 103.0 ms | **243.1 ms** | 11,362,992 B |
| `shingle_by_occurrence` alone, which ADR-173 forbids | 164.2 ms | 62.5 ms | 170.4 ms | 7,881,592 B |
| As shipped, each chunk's rows sorted by hash before they are inserted | 1,717.5 ms | 1,121.2 ms | 1,845.0 ms | 229,265,672 B |
| `shingle_by_occurrence`, `shingle_by_run_id`, and an index on `shingle_hash` alone | 1,801.3 ms | 1,131.4 ms | 1,946.6 ms | 202,300,272 B |

**This machine is not #378's.** The same *before* variant cost 3,814 ms a chunk there, on a solid-state disk, against 1,695 ms here, where the whole database sits in memory. The ratio is 7 to 1 here, and was 25 to 1 there against the nearest variant measured, `DELETE`/`FULL` with an index on `run_id` alone, at 154 ms. A database too large for the file cache, as the archive's 11.4 GB one is, pays for the scattered pages in reads as well as writes. That is the case #378's figure is nearer to.

**One-off costs**, on the same machine and URL:

| Statement | 10,000,000 rows | 25,000,000 rows |
|---|---:|---:|
| `CREATE INDEX shingle_by_hash …` | 26,199 ms | 66,910 ms; 63,323 ms |
| `DROP INDEX shingle_by_hash` | 569 ms | 804 ms |
| `CREATE INDEX shingle_by_run_id ON shingle (run_id)` | 14,190 ms | 30,406 ms |
| Stage 3's read of all of a run's rows, through `shingle_by_hash` (as shipped) | 38,077 ms; 38,607 ms | — |
| The same read through `shingle_by_run_id` | 5,386 ms; 5,836 ms | — |
| Containment retrieval, one call, with `shingle_by_hash` (median of 20) | 0.14 ms | — |
| The same call without it (median of 3) | 2,400 ms | — |

With both `shingle_by_run_id` and `shingle_by_hash` present, SQLite still answers stage 3's read and the per-run delete through `shingle_by_run_id`, and containment retrieval through `shingle_by_hash`.

## Why this shape, and what the others cost

- **Drop at stage 2's start, build at its end.** The drop is §2.2. The build at the end is refused for the reason under §3's table: completion is per run and the index is per table, so a finished stage 2 can be skipped while the index is missing, and stage 4b would need its own build anyway. Two build sites would decide one thing twice. It would also pay the build in an invocation that then stops at stage 4's gate, and it would have to be ordered before `RunCompletion` writes `finished_step`, or a stop between the two would leave a finished stage 2 with no index.
- **Build lazily before its first reader.** Taken, as §2.3. It is the only build site that holds on every path in §3, because it is the reader's own precondition.
- **Reorder or narrow its columns.** No index that serves containment retrieval avoids the scattered writes, because its key has to contain the hash, and a random key lands at a random place. Measured: an index on `shingle_hash` alone costs 1,947 ms a chunk, no better than the shipped one. Narrowing to `run_id` alone costs 243 ms a chunk, and §2.1 adds exactly that index, but it cannot serve containment retrieval: 2,400 ms a call.
- **Keep it, and write shingles in hash order.** Measured at 1,845 ms a chunk, no better. One chunk's 40,000 sorted keys are still spread across the whole index, so they touch as many pages as unsorted ones. Sorting more than a chunk would mean holding rows across commits, which ADR-181's resume rests on never doing.
- **Keep `shingle_by_hash` in `schema.sql` and drop it only during stage 2.** Refused. `schema.sql` runs at every start (ADR-173 §3), so every start after a stopped stage 2 would build the whole index during start-up, before any line is written, and stage 2 would drop it again a moment later. ADR-173's rule would also be false while stage 2 ran.

## Consequences

- **A stage-2 chunk on a large database costs what writing its rows costs.** On the machine measured, 243 ms where it cost 1,695 ms. Over the archive's 650-odd chunks that is about 16 minutes saved on this machine. Scaled from #378's figures it would be about 40 minutes on that one. Either is an extrapolation from a synthetic database, not a measurement of the archive.
- **Stage 3 reads the run's shingles in the order they were written.** 5.4 s against 38.1 s at 10,000,000 rows, warm. On a spinning disk the gap is expected to be wider, because the shipped plan fetches each row from a different page. That is not measured.
- **Stage 4b pays the build once per stage-2 run that writes.** About a minute at 25,000,000 rows on the machine measured, and two to five minutes estimated on `H:` (§4). It is announced (§2.4). During the build the write-ahead log grows to the size of the index, 2.3 GB at 25,000,000 rows, because one transaction cannot be copied back by a WAL checkpoint until it commits. After that WAL checkpoint, `journal_size_limit` cuts the file back. The disk needs that much free space beside the database for the length of the build.
- **The database grows by `shingle_by_run_id`.** Each entry is a 64-character run id and a rowid. That came to about 0.64 GB per 10,000,000 rows on the measured database, so about 1.6 GB at the archive's size. That is the price of ADR-173's rule on this table, which until now `shingle_by_hash` paid as a side effect. Once stage 4b has built `shingle_by_hash`, both indexes exist.
- **The first start after this change builds `shingle_by_run_id` on an existing database, once, before any stage runs, and writes no line about it.** ADR-173 §3's mechanism does this: `CREATE INDEX IF NOT EXISTS` in `schema.sql` at start-up. It took 30.4 s at 25,000,000 rows on the machine measured. **Estimated, not measured: one to three minutes on `H:`.** The operator accepted this on 2026-10-03 as an upgrade cost, paid once per database, with no line added for it: ADR-173 accepted an unannounced build at start-up when it cost 0.6 s, and this one is told instead in the pull request that ships the change, which says that the first start after upgrading may sit silent for a few minutes on a large database while it builds a new index on the shingle table, once.
- **Dropping the index does not shrink the database file.** Its pages go onto SQLite's free list, and the next build reuses them. Nothing here runs `VACUUM`.
- **No schema version moves**, on ADR-173 §3's argument: an index alters no table, and `IF NOT EXISTS` applies to an old database all the same. A database built before this change keeps `shingle_by_hash` until its next stage 2 that writes drops it. Until then stage 3 reads through whichever index SQLite prefers, which with both present is `shingle_by_run_id`.
- **Text that states the old arrangement must change with the code**, so that no comment contradicts the code beneath it (ADR-077's lesson):
  - `schema.sql`'s comment above the by-hash index.
  - `RedundancyResolution`'s class javadoc, which says the index *"exists specifically so containment retrieval is a `GROUP BY … HAVING` over it"*. It still does, and it now exists only from stage 4b on.
  - The javadoc on `containmentCandidates`.
  - `SimilaritySchema`'s javadoc, which lists the by-hash index among version 3's contents.
  - The javadoc on `RedundancySignatures.distinctiveShingleSet` and the comment in `ShingleSetCache.load`. Both describe what SQLite does with `DISTINCT` *when the index exists*. That stays true in stage 4b and is no longer true in stage 4a, which runs before the build.
- **`CONTEXT.md` needs no new term.** Shingle, run, stage and step are used as defined there, and *index* here names a SQLite index, never the ledger.
- **#378's record measured the premise and left this open.** Its §6 is answered here.

## Tests

- **`ShingleIndexesInTheSchemaTest`** (`src/test/java/io/algernon/vespera/similarity/`) runs the shipped `schema.sql` into an SQLite database of its own, so no other test's state reaches it, and asserts:
  - no index on `shingle` has `shingle_hash` among its columns;
  - `shingle_by_run_id` indexes `shingle` on `run_id` alone;
  - stage 3's per-run read is answered through `shingle_by_run_id`, both without `shingle_by_hash` and with it built as §2.3 builds it;
  - containment retrieval's query is answered through `shingle_by_hash` once it is built, and without it is not answered through any index on the hash.

  The first three fail today: the shipped schema creates `shingle_by_hash` and no `shingle_by_run_id`, and stage 3's read goes through the by-hash index. The fourth passes today and has to go on passing.
- **`ShingleHashIndexInvocationTest`** (`src/test/java/io/algernon/vespera/pipeline/`) drives whole invocations over `ConverterStopsPartwayBeans`, ADR-181's converter that stops answering partway, and asserts §3's rows:
  1. A stage 2 stopped partway, over a database holding `shingle_by_hash`, leaves its committed chunks' shingles and no `shingle_by_hash`. The invocation that resumes it with stage 4's gate open finishes stage 2 under the same run and finishes stage 4b, and ends with `shingle_by_hash` on `(run_id, shingle_parameter_identity, shingle_hash)`, announced by §2.4's two lines in order, before stage 4b starts resolving. Containment retrieval's query is then answered through it.
  2. A stage 2 that finished behind a shut stage-4 gate leaves no `shingle_by_hash`. The invocation that opens the gate converts nothing and builds it.
  3. An invocation over a corpus whose every step is finished builds nothing, says nothing about building, and leaves `shingle_by_hash` in place.
  4. A stage 2 under a new run id drops the index the earlier run's stage 4b built, and leaves that run's shingles as they were.

  Tests 1, 2 and 4 fail today, because the shipped schema creates the index and nothing drops it. Test 3 passes today and has to go on passing: it is what stops an implementation from dropping the index on every invocation.

## What this does not decide

- **A partial index per run** (`CREATE INDEX … WHERE run_id = ?`), which would build over one run's rows instead of the whole table. It would make each index's name carry a run id, and nothing has measured that a database holding several stage-2 runs is common enough to pay for it.
