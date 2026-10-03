# ADR-182 — Stage 2 writes shingles without the lookup by hash, and stage 4b builds it before reading it

- **Date**: 2026-10-03
- **Status**: accepted
- **Amends**: [ADR-081](0081-minhash-retrieves-shingle-sets-judge-128-permutations-in-16-bands.md), **when its containment index exists**. ADR-081 decided the rare-shingle lookup and what it retrieves; `schema.sql` has built it as `shingle_by_hash` at every start since, so stage 2 has kept it up to date row by row. Its thresholds, its 32 rare shingles, its 24-of-32 rule and the lookup's columns all stand.
- **Rests on**: [ADR-173](0173-every-column-that-references-a-file-occurrence-a-walk-or-a-run-carries-an-index.md), whose rule and index shape `shingle.run_id` now meets with an index of its own, and whose §3 says an index-only change moves no schema version; [ADR-115](0115-a-re-walk-that-observed-nothing-new-is-discarded-and-a-run-is-continued-under-its-own-id.md) and [ADR-116](0116-a-runs-completion-is-recorded-per-step-because-several-steps-share-one-run.md), whose finished-step skip decides when stage 2 and stage 4b have work to do; [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md), which decides which module the code may live in (§5); and [ADR-180](0180-the-database-file-uses-sqlites-write-ahead-log-synced-at-wal-checkpoints.md), whose §6 measured the cost and left this decision open.
- **Settles** [#381](https://github.com/algernon28/vespera/issues/381).

## Context

### What was measured

ADR-180 measured one stage-2 chunk commit, 16 file occurrences × 2,500 `shingle` rows in one transaction, on a synthetic database built from the shipped `schema.sql`: 10,000,000 shingle rows, 2.95 GB, on an SSD. With `shingle_by_hash` the chunk took about 3.9 s in either journal mode. Without it, it took 96 ms. With an index on `run_id` alone in its place it took 154 ms. Building `shingle_by_hash` once over the whole table took 13.7 s.

`shingle_by_hash` is on `shingle (run_id, shingle_parameter_identity, shingle_hash)`. A shingle hash is the first 8 bytes of a SHA-256, so each new row lands at a random place in an index far larger than any page cache, and each insert reaches a different leaf page. Over the whole-archive run's 10,406 survivors, about 650 chunks, that is roughly 40 minutes of stage 2 on an SSD, and more on the spinning disk that run's working directory is on.

This record measured the shape it decides, again on a synthetic database of 10,000,000 rows built from the shipped schema, with `shingle_by_hash` grown one insert at a time as stage 2 grows it. The tool is `ShingleLookupBenchmark`, a `main` in the test tree. Ten repeats, medians, SSD:

| | `WAL`/`NORMAL` (ADR-180) | `DELETE`/`FULL` |
|---|---:|---:|
| One stage-2 chunk, `shingle_by_hash` maintained (today) | 3,516.5 ms | 3,692.7 ms |
| One stage-2 chunk, `shingle_by_run_id` only (this record) | 211.7 ms | 138.4 ms |
| Stage 3's read of one run's 10,400,000 rows, through `shingle_by_hash` (today) | 69.1 s | 69.3 s |
| The same read, through `shingle_by_run_id` | 2.4 s | 1.9 s |
| One-off: dropping a `shingle_by_hash` grown row by row | 17.9 s | 18.8 s |
| One-off: building `shingle_by_run_id` over a table that already has its rows | 28.4 s | 20.4 s |
| One-off: building `shingle_by_hash` over 10,400,000 rows, as stage 4b would | 18.6 s | 12.9 s |

A chunk costs 17 to 27 times less. The synthetic database was deleted after the measurement. No real database was opened.

### Who reads `shingle_by_hash`, and when

Every statement under `src/main` that touches `shingle`, with the plan SQLite gives it. `EXPLAIN QUERY PLAN` was read on a scratch database holding the shipped schema. Nothing in Vespera runs `ANALYZE`, so the planner decides from the schema alone, and the plan is the same on an empty table as on a 25-million-row one.

| Statement | Where, and when it runs | Plan today | Plan under this record |
|---|---|---|---|
| `INSERT INTO shingle …` | `Shingler.write`, stage 2, every converted occurrence | maintains all three indexes | maintains `shingle_by_occurrence` and `shingle_by_run_id` |
| `SELECT occurrence_id, shingle_parameter_identity, shingle_hash FROM shingle WHERE run_id = ?` | `DocumentFrequency.measure`, stage 3, once per stage-3 run | `SEARCH … USING INDEX shingle_by_hash (run_id=?)` | `SEARCH … USING INDEX shingle_by_run_id (run_id=?)`, with or without `shingle_by_hash` present |
| `DELETE FROM shingle WHERE run_id = ?` | `Shingler.discardForRun`, stage 2's discard (ADR-115) | `SEARCH … USING INDEX shingle_by_hash (run_id=?)` | `SEARCH … USING INDEX shingle_by_run_id (run_id=?)` |
| The foreign-key lookup on `shingle.run_id` | any delete from `run` under `foreign_keys=on` (ADR-173) | `SEARCH … USING COVERING INDEX shingle_by_hash (run_id=?)` | `SEARCH … USING COVERING INDEX shingle_by_run_id (run_id=?)` |
| `SELECT shingle_hash FROM shingle WHERE occurrence_id = ? AND run_id = ? AND shingle_parameter_identity = ?` | `RedundancySignatures`, stage 4a, and `RedundancyResolution`'s shingle-set cache, stage 4b | `shingle_by_occurrence` (#277) | unchanged |
| `SELECT COUNT(*) FROM shingle WHERE occurrence_id = ? AND run_id = ? AND shingle_parameter_identity = ?` | `RedundancyResolution.rawShingleSetSize`, stage 4b | covering `shingle_by_occurrence` | unchanged |
| `SELECT occurrence_id FROM shingle WHERE run_id = ? AND shingle_parameter_identity = ? AND shingle_hash IN (…) GROUP BY occurrence_id HAVING COUNT(DISTINCT shingle_hash) >= ?` | `RedundancyResolution.containmentCandidates`, stage 4b, once per signed occurrence | `SEARCH … USING INDEX shingle_by_hash (run_id=? AND shingle_parameter_identity=? AND shingle_hash=?)` | the same, once stage 4b has built it; without it, `SEARCH … USING INDEX shingle_by_run_id (run_id=?)`, which reads the whole run for each occurrence |

**Only one statement needs `shingle_by_hash`: stage 4b's containment search.** It is the only reader ADR-081 built the lookup for. The others use it only because it was the one index whose first column is `run_id`. Each of them goes through `shingle_by_run_id` instead, and stage 3's read gets faster rather than slower: through `shingle_by_hash` it visits the run's rows in hash order, one table page lookup per row, and through `shingle_by_run_id` it visits them in the order they were written. Stage 2 never reads the lookup, so nothing needs it while stage 2 writes. Stages 3 and 4a never read it either.

## Decision

### 1. No start builds `shingle_by_hash`, and `shingle.run_id` gets an index of its own

`schema.sql` stops creating `shingle_by_hash`. Since every start runs `schema.sql`, keeping the statement there would rebuild the lookup over the whole table at the start of every invocation after stage 2 had dropped it, before stage 2 could drop it again.

In its place `schema.sql` creates the index ADR-173's rule asks for, in ADR-173 §2's form, directly after `shingle_by_occurrence`:

```sql
-- SQLite checks this foreign key by scanning without it; ADR-173 indexes every reference to file_occurrence, walk and run.
CREATE INDEX IF NOT EXISTS shingle_by_run_id ON shingle (run_id);
```

Until now `shingle_by_hash` met that rule for `shingle.run_id`, because `run_id` is its first column. With it gone for most of a database's life, the rule needs an index that always stands. A single-column index on `run_id` is also cheap to keep up to date: within one run every new row carries the same key, so each insert lands at the end of that run's range, on the same few leaf pages.

The comment where `shingle_by_hash` was created says it is absent on purpose, and points here.

### 2. Stage 2 drops `shingle_by_hash` when it has work to do

When the `extraction` step is not recorded as finished under its run, it drops `shingle_by_hash` if it exists. It does this where `ExtractionJobConfiguration.extractionReader` decides that, outside any chunk transaction, as **the first statement of the not-finished branch**, before anything there deletes rows. Deleting a run's shingles with the lookup still in place would itself pay the scattered-page cost this record removes.

A step that is already finished reads nothing and writes nothing, so it leaves the lookup as it finds it. A run nothing is recorded under yet, or one an earlier invocation stopped, both have work to do, and both drop it.

When it drops one, stage 2 says so before and after, since on a large database dropping it takes a while of its own:

```
Stage 2 (extraction) is dropping the shingle lookup by hash, so that writing shingles does not keep it up to date; stage 4b builds it again before it reads it
Stage 2 (extraction) dropped the shingle lookup by hash in 41.3 s
```

Where there is nothing to drop, it says nothing. The check is one read of `sqlite_master`.

### 3. Stage 4b builds `shingle_by_hash` before it reads it, once, and says so

When the `content-redundancy` step does its work, meaning its gate is open and its completion is not recorded under its run, the first thing it does is build the lookup if it does not exist:

```sql
CREATE INDEX IF NOT EXISTS shingle_by_hash ON shingle (run_id, shingle_parameter_identity, shingle_hash);
```

This is the same name and the same columns as before, so ADR-081's search and its plan are unchanged. The build is the first thing the step's work does, before it reads anything and before anything decides whether there is a signed occurrence to compare. That keeps the rule one sentence long: whenever stage 4b works, the lookup stands before anything in it reads. On a database with no signed occurrence the build is wasted, and it is as small as that database's shingle table.

When it builds one, stage 4b says so before and after. The row count is `SELECT COUNT(*) FROM shingle`, every row in the table, because the lookup covers every run's rows:

```
Stage 4b (redundancy resolution) is building the shingle lookup by hash over 24,910,924 shingle rows; this is done once, and on a large archive takes minutes
Stage 4b (redundancy resolution) built the shingle lookup by hash in 212.4 s
```

The count is grouped with commas (`%,d` under `Locale.ROOT`), and the seconds have one decimal. Where the lookup already stands, stage 4b says nothing about it and builds nothing.

**Nothing else builds it, and nothing but stage 2 drops it.** After stage 4b it stays until the next stage 2 that has work to do. Invocations in between, which find stage 2 and stage 4b already finished, leave it where it is.

### 4. The index can never be wrong, so a stop anywhere leaves something correct

An index is derived from its table. A query returns the same rows with it or without it, and only the time differs. SQLite's `CREATE INDEX` and `DROP INDEX` are transactional, under the rollback journal and under the write-ahead log alike (ADR-180): a statement interrupted by a crash or a kill is rolled back on the next open, so a half-built or half-dropped index cannot exist. Each statement here runs in its own transaction, outside any chunk transaction. So every place an invocation can stop leaves one of two states, and the next invocation handles both:

| Stopped | What the next invocation finds | What it does |
|---|---|---|
| in stage 2, before the drop committed | the lookup standing, stage 2 not finished | stage 2 drops it (§2), then writes |
| in stage 2, after the drop | no lookup, stage 2 not finished | stage 2 has nothing to drop, and writes |
| between stage 2's completion and stage 4b | no lookup, stage 2 finished | stage 2 reads nothing and drops nothing; stage 4b builds it (§3) |
| in stage 4b, during the build | no lookup, because the build was rolled back; stage 4b not finished | stage 4b builds it again, from the start |
| in stage 4b, after the build | the lookup standing, stage 4b not finished | stage 4b finds it and builds nothing |
| after stage 4b | the lookup standing, both finished | nothing, until a stage 2 with work drops it |

Stage 3 may run with or without the lookup standing. Its read goes through `shingle_by_run_id` either way (Context).

### 5. The code lives in `pipeline`, and nothing under `similarity` or `extraction` changes

`shingle` and its indexes are `similarity`'s. Even so, the two statements above go in `pipeline`, beside the calls that already decide when each step has work to do: `extractionReader` for the drop and `RedundancyResolutionTasklet` for the build. That is a deliberate exception to "a capability owns its tables", and ADR-058 is the reason for it.

A stage's implementation version is the last commit touching its modules' source paths. Stage 2's version spans `extraction` and `similarity` (`StageModules.EXTRACTION`). A change to any file under `similarity` therefore mints a new stage-2 run id, and every working directory's stage 2 runs again under it. On the whole-archive database that means converting every survivor again from the cache, while the old run's ~25 million shingle rows stay where they are (ADR-077, ADR-156) and the new run writes as many again. That is a doubled table and a full replay, for a change that alters no row. `pipeline` is part of the version of stages 3 to 6b only. Those stages move with any `pipeline` change, and ADR-181's own change to `extractionReader` moves them anyway.

`schema.sql` is a resource, not a module's source path, so changing it moves no run id.

**No schema version moves**, for ADR-173 §3's reason: an index alters no table, and `IF NOT EXISTS` applies to an existing database all the same. A bump would also delete `similarity`'s tables (ADR-059), which is the opposite of what is wanted. `SimilaritySchema`'s Javadoc, which lists the by-hash index among version 3's contents, stays true as history and is not edited, because editing it would move stage 2's run id too.

### 6. How this composes with ADR-181

ADR-181 (#379, not merged when this was written) changes what stage 2's not-finished branch does with an earlier invocation's rows: it keeps the rows that committed chunks wrote, and reads only the occurrences no chunk recorded. This record puts one statement at the head of that same branch. The two compose whichever merges first.

- **Under ADR-115's discard (today):** the not-finished branch drops the lookup, then discards the run's verdicts, metrics, shingles and faults. The shingle delete goes through `shingle_by_run_id` and maintains no lookup.
- **Under ADR-181:** the not-finished branch drops the lookup, then deletes the fault rows and their verdicts, and keeps everything else. The shingle rows an earlier invocation committed stay, written without the lookup or, on a database from before this record, with it. Either way the lookup stage 4b builds later is built from the table as it then stands, so it covers the kept rows and the new rows alike. A lookup cannot be stale, because nothing is ever added to one after it is built: stage 2 drops it before it writes.
- **ADR-181 §3, a different run id:** that run has work to do, so it drops the lookup like any other.
- **Whichever change lands second** keeps the drop as the first statement of the not-finished branch, ahead of whatever ADR-181 leaves there. That is the only point where the two touch the same code.
- **ADR-181's consequence that `Shingler.discardForRun` loses its only caller** is unaffected. If that change deletes the method, it edits `similarity` and moves stage 2's run id for its own reasons. That is ADR-181's trade to state, not this record's.

### 7. What the one-off build costs on the whole-archive database

Measured above on an SSD at 10.4 million rows: building `shingle_by_hash` took 12.9 to 18.6 s, dropping a row-by-row-grown one took 17.9 to 18.8 s, and building `shingle_by_run_id` over existing rows took 20.4 to 28.4 s. The whole-archive database holds about 25 million shingle rows on a spinning disk. That disk was not measured, because the live run is using it.

- **Scaled to 25 million rows on the SSD:** about 2.5 times as long, since a build is a sort, n log n, over a near-linear amount of I/O. That gives 30 to 50 s for the build, 45 to 50 s for the drop, and 50 to 70 s for `shingle_by_run_id`.
- **On the spinning disk:** a build reads the table once and writes the index once in sorted order, so it is mostly sequential I/O, which a spinning disk does well. The lookup is about 90 bytes per row, so about 2.3 GB at 25 million rows. Under the write-ahead log those pages are written twice, once to the write-ahead log and once at the WAL checkpoint. At 100 to 150 MB/s that is 30 to 50 s of writing, plus the read of a table whose pages are interleaved with its indexes. **Estimate: 2 to 5 minutes for stage 4b's build, once per stage-4 run.** Dropping a lookup grown row by row reads every one of its pages, scattered across the file, so **estimate 1 to 5 minutes, once.** Both are against roughly 40 minutes of row-by-row maintenance per stage-2 run on an SSD, and more on this disk.
- **`shingle_by_run_id` on an existing database:** built once, at the first start after this change, during schema initialisation, before the invocation prints anything. **Estimate 2 to 5 minutes of silence on the whole-archive database.** It happens once, and every later start finds the index there. ADR-173 accepted silence for a build that took 0.6 s. This one is longer, and nothing here makes it visible, because no line can be printed from inside Spring's schema initialisation without new machinery. The operator of a database that large should expect it. The pull request that ships this says so.

The two drops and builds stage 2 and stage 4b make are logged (§2, §3), so a silence of minutes inside either stage is preceded by a line that says what is happening.

## Consequences

- **A stage-2 chunk commit costs about 140 to 210 ms instead of about 3.5 to 3.7 s** on the synthetic database, and the gap grows with the table. Over the archive's ~650 chunks, roughly 40 minutes of stage 2 becomes about two.
- **Stage 3's read of one run's shingles is about 30 times faster** (69 s to 2 s at 10.4 million rows), because it reads rows in the order they were written.
- **Stage 4b costs one build more than before**, logged, once per stage-4 run, and again only if it is stopped during the build.
- **The database is smaller while the lookup is absent**, by the size of the lookup, about 2.3 GB at 25 million rows. SQLite keeps the freed pages in the file and reuses them for later rows. It does not shrink the file.
- **`shingle_by_run_id` adds about 75 bytes a row**, roughly 1.9 GB at 25 million rows, and it stands for the whole of a database's life.
- **`JournalModeBenchmark`** (ADR-180) built its base from `schema.sql` and relied on it to create `shingle_by_hash`. It now creates the lookup itself and drops it with `IF EXISTS`, so it still measures the database it measured.
- **ADR-081's "Stage 4's cost is now legible"** gains one item: building the lookup, logged, before the containment search. The note on ADR-081 says so.

## Tests

- **`ShingleIndexesTest`** (`similarity`, `@JdbcTest` over the test profile's schema):
  - a start creates `shingle_by_run_id`, on `run_id` alone, and `shingle_by_occurrence`, and does not create `shingle_by_hash`;
  - stage 3's read of one run goes through `shingle_by_run_id`, both without the lookup and with it;
  - once built with §3's statement, the containment search goes through `shingle_by_hash` by run, granularity and hash.
- **`ShingleLookupByHashInvocationTest`** (`pipeline`, whole invocations). `ShingleWritesProbe`, a delegating `@Primary` `Shingler`, records from inside each write whether the lookup stood at that moment, because the database at the end of the job cannot say. The converter is stopped part-way by `ConverterStopsAnsweringBeans`, after one whole chunk and two occurrences of the next.
  - A stage 2 stopped part-way wrote every occurrence's shingles with no lookup standing, and leaves no lookup behind.
  - Resumed after that stop, stage 2 writes without the lookup. The invocation ends with stage 2 and stage 4b finished and the lookup built on `(run_id, shingle_parameter_identity, shingle_hash)`. Stage 4b's "building … over N shingle rows" line, with N the table's row count, comes before its "built … in" line.
  - With stage 4's gate shut, a finished stage 2 leaves no lookup, and no line says one is being built.
  - A lookup left standing from before is dropped before stage 2 writes its first shingle, the two drop lines appear in order, and with the gate shut nothing builds it again.
- **`ForeignKeysToFileOccurrenceAreIndexedTest`** (ADR-173) keeps passing only because `shingle_by_run_id` exists. It is the test that stops someone deleting it as redundant.

## What this does not decide

**Whether the silent first-start build of `shingle_by_run_id` should print a line.** §7 estimates it at minutes on the one database that large, once. Making it visible means printing from before or inside Spring's schema initialisation. If the operator finds the silence unacceptable, that is its own ticket.

**Whether stage 4b should drop the lookup again when it finishes.** That would free about 2.3 GB between stage-4 runs, at the cost of a rebuild whenever stage 4b is stopped after its build and resumed. Nothing measured asks for it. Stage 2 dropping the lookup at its next run already keeps the lookup out of the one place it costs anything.

**Whether the shingle rows themselves could be written in hash order, or the lookup narrowed or reordered.** Both were considered and set aside without measurement. Sorting within one chunk still scatters 40,000 rows across a tree of tens of millions of entries, so nearly every row still reaches its own leaf page. Reordering or narrowing the columns changes which pages are hit, not how scattered they are. A partial index per run would leave other runs' lookups untouched by a new run's writes. But the planner uses a partial index only when the query's `WHERE` provably implies the index's, and with a bound `run_id = ?` it cannot prove that. Each run's lookup would also need a name minted from its run id. None of the three beats not having the lookup while writing.
