# ADR-187 — A database statement that can take minutes says so before it starts and when it ends

- **Date**: 2026-10-04
- **Status**: accepted
- **Amends**: [ADR-182](0182-stage-2-writes-shingles-without-the-by-hash-index-and-stage-4b-builds-it-before-containment-retrieval-reads-it.md) — **three sentences, and nothing it decided about when the index exists.** §2.2 ends *"It comes after stage 2's `starting` line and before its first chunk, with no line of its own"*: from this record the drop has two lines of its own (§1). §4 says of the drop *"On `H:` it is unmeasured"*: it is now measured there, at 1 hour 58 minutes (Context). Its Consequences say that the first start after that change builds `shingle_by_run_id` *"and writes no line about it"*, estimated at one to three minutes on `H:`, and that *"the operator accepted this on 2026-10-03 as an upgrade cost, paid once per database, with no line added for it"*: a start-up on `H:` then took 12 minutes 24 seconds in `schema.sql` and was taken for a hang, and from this record such a build has two lines (§3). **That last amendment sets aside something the operator accepted the day before, on an estimate the measurement did not bear out. The operator confirmed setting it aside on 2026-10-04.** ADR-182's §1, §2.1, §2.3 to §2.5 and §3 stand as written: stage 2 still drops the index on the same test (§2 here), and stage 4b still builds it.
- **Amends**: [ADR-173](0173-every-column-that-references-a-file-occurrence-a-walk-or-a-run-carries-an-index.md) — **one sentence of its Consequences.** *"The build happens during schema initialisation, before any step runs, and has no output of its own."* It has output from this record on, where the table already holds rows (§3). Its rule, the shape of an index added under it, and §3's *"No schema version moves"* are unchanged.
- **Extends**: [ADR-093](0093-logging-is-explicit-and-process-scoped-console-plus-rolling-file-per-item-and-per-step-at-info.md). It has every step say when it starts and ends, every file occurrence too, and a stage report progress where a denominator exists. It did not foresee one statement, inside a step or before any step, that lasts longer than most steps. §1 adds that case.
- **Keeps**: [ADR-059](0059-schema-version-is-one-row-per-module-checked-and-refused-independently.md). No schema version moves: no table changes and no index is added, removed or renamed.
- **Rests on**: [ADR-177](0177-one-invocation-per-working-directory-and-a-locked-database-file-is-named.md). The working-directory lock is taken while the environment is prepared, before the datasource exists, so `schema.sql` already runs under it.
- **Settles** [#401](https://github.com/algernon28/vespera/issues/401), the two-hour drop, and [#402](https://github.com/algernon28/vespera/issues/402), the twelve minutes in `schema.sql`. [#400](https://github.com/algernon28/vespera/issues/400) is the same two findings filed at the same moment by another session. It was closed as a duplicate, and it holds the fuller measurements, which the Context below quotes.

**Two words here are SQLite's.** An *index* is always a SQLite index on a table, named where it matters. It is never the ledger. A *WAL checkpoint* is SQLite copying its write-ahead log back into the database file. It is not a walk's **Checkpoint** (ADR-055).

## Context

### What happened

Twice on 2026-10-04 an invocation over the whole-archive working directory on `H:` wrote nothing for minutes to hours while it was inside one SQLite statement. Both times the operator took it for stuck. `H:` is a local spinning disk (ADR-180).

**Stage 2 spent 1 hour 58 minutes dropping `shingle_by_hash`.** Build `11691d0`, database 16.0 GB. `Stage 2 (extraction) sidecar is healthy` was written at 13:55:18.746, and the next line was the first `[extraction] finished` at 15:53:37: 7,098 seconds. In every `jstack` taken in between, the `main` thread was in `NativeDB.step` under `ShingleHashIndex.drop`, called from `ExtractionJobConfiguration.extractionReader`. The process read steadily and wrote nothing until the statement ended: 0.46 GB read by 14:05, 0.73 by 14:15, 0.96 by 14:24, 1.49 by 14:45, 2.53 by 15:20, 3.44 by 15:50. `vespera.db-wal` did not change. Docling was idle, and no conversion thread existed, because the reader had not finished opening.

From 14:05 to 15:50 that is 2.98 GB in 105 minutes: 28 MB a minute, or about 115 pages of 4,096 bytes a second, 8 to 9 ms a page. **That is the rate of a disk that seeks once for every page it reads.** The same disk reads a file from end to end at 100 MB a second or more.

**Start-up spent 12 minutes 24 seconds in `schema.sql`.** Build `e82edeb`, database 13.4 GB before and 16.0 GB after. `HikariPool-1 - Start completed.` was written at 10:01:03.156 and `Job: [SimpleJob: [name=vespera]] launched` at 10:13:27.907. At 10:03 and 10:07 the `main` thread was in `NativeDB.step` under `ScriptUtils.executeSqlScript`, called by `DataSourceScriptDatabaseInitializer`. The process wrote 11.7 GB and the database grew by 2.6 GB. The afternoon's start-up on the same database took 5 seconds.

### What the two statements are

`DROP INDEX IF EXISTS shingle_by_hash` is ADR-182 §2.2: stage 2 removes the index before its first chunk whenever its step is not finished, and writes no line. ADR-182 measured it at 804 ms on 25,000,000 rows with the database in the file cache, and warned that on an index grown at random places *"it may take minutes rather than seconds"*.

`schema.sql` holds 32 `CREATE TABLE IF NOT EXISTS` statements and 28 `CREATE INDEX IF NOT EXISTS` statements, and nothing else. A `CREATE TABLE IF NOT EXISTS` writes one row of the schema. **So the only statement in that file that can write gigabytes is a `CREATE INDEX` over a table that already holds rows.** ADR-182 §2.1 added `shingle_by_run_id` on the largest table in the database, and its Consequences estimated that build at one to three minutes on `H:`. Which of the 28 it was that morning is not confirmed (Not known).

## Measurements

**Where and how.** A throwaway probe, kept outside the repository as AGENTS.md asks. Python 3.13.5, whose `sqlite3` module reports SQLite 3.49.1 (the application's `sqlite-jdbc` bundles 3.53), on Windows 11, on the solid-state `C:` (ADR-180). The connection settings are the shipped URL's: write-ahead log, `synchronous=NORMAL`, `journal_size_limit=536870912`, and SQLite's default 2 MB page cache. **Nothing here ran on `H:`, on a spinning disk, or on the archive's database**, which a whole-archive run was using.

**The database.** The `shingle` table as `schema.sql` declares it. One run is 1,000 file occurrences of 2,500 random-hash rows each, 2,500,000 rows, written in transactions of 16 occurrences (`CHUNK_SIZE`). The first run is written with `shingle_by_occurrence` and `shingle_by_hash` in place, as a build before ADR-182 wrote it, so that index is grown one row at a time. Then the probe does what an upgraded build does: `CREATE INDEX shingle_by_run_id`, `DROP INDEX shingle_by_hash`, a second run written without the by-hash index, and `CREATE INDEX shingle_by_hash` over every row. It repeats drop, run and build three more times.

**Two things are measured about each index.** The first is the order of its pages in the file. `DROP INDEX` visits the pages of the index's b-tree from the root, each page and then its children from left to right (`clearDatabasePage` in SQLite's `btree.c`). The probe walks the file in that order and counts the steps that do not land on the next page of the file. On a spinning disk each such step is a seek, unless it is a short hop forward. The second is the time of `DROP INDEX` with the file cache emptied first. Opening a file unbuffered and closing it makes Windows discard its cached pages; the first row of the table shows that it does.

| The index dropped | Pages | Size | Steps not to the next page | `DROP INDEX`, cache emptied | Read | Written |
|---|---:|---:|---:|---:|---:|---:|
| Grown row by row, one run's rows | 68,654 | 281 MB | 68,651 (100.0%) | 5,790 ms; 205 ms warm | 281.2 MB | 0.3 MB |
| Built by `CREATE INDEX` into new pages, two runs' rows | 122,026 | 500 MB | 8,784 (7.2%) | 468 ms | 499.8 MB | 0.5 MB |
| The same, three runs' rows | 183,039 | 750 MB | 13,177 (7.2%) | 669 ms | 749.7 MB | 0.7 MB |
| The same, four runs' rows, 1,098 freed pages reused | 244,089 | 1,000 MB | 17,686 (7.2%) | 873 ms | 999.8 MB | 1.0 MB |
| The same, five runs' rows, 61,769 pages freed by the previous built index reused | 305,138 | 1,250 MB | 22,468 (7.4%) | 1,114 ms | 1,249.8 MB | 1.2 MB |
| Grown row by row, two runs of 600 occurrences | 82,413 | 338 MB | 82,409 (100.0%) | 4,970 ms | 337.6 MB | 0.3 MB |
| Built by `CREATE INDEX` into pages that row-grown index had freed | 82,369 | 337 MB | 56,464 (68.6%) | 3,281 ms | 337.4 MB | 0.3 MB |

The last two rows are a second database. Its row-grown index was dropped, a run of only 150 occurrences was written, which left 55,475 of the freed pages unused, and the build then took those pages before it extended the file.

What the table shows:

- **`DROP INDEX` reads every page of the index and writes almost nothing.** The bytes read equal the index's size in every row. The bytes written are a thousandth of it: the free list. The cost is not the free list, not `secure_delete` (off in the SQLite the probe ran on, Python's; for sqlite-jdbc's the evidence is the run on `H:`, where the process wrote nothing until the statement ended) and not the write-ahead log, which #401 asked.
- **An index grown row by row has no two consecutive pages next to each other in the file.** Every step is a jump. The cold drop cost 20.6 ms per MB and 14.7 ms per MB.
- **An index built whole into new pages is one unbroken stretch of the file.** Its pages run from the first to the last with no gap: 122,026 pages between page 353,389 and page 475,414. The 7% of steps that are not to the next page are the b-tree's interior pages, each a short hop. The cold drop cost 0.87 to 0.94 ms per MB, about a twentieth of the row-grown index's on a disk with no seek time.
- **An index built into pages a row-grown index freed inherits most of its disorder**: 68.6% of steps jump, and the cold drop cost 9.7 ms per MB.
- **Reusing pages a built index freed does not disorder the next one.** The fifth row reused 61,769 of them and stayed at 7.4%.

**Other figures from the same probe.**

| Statement or step | Measured |
|---|---:|
| One stage-2 chunk into the row-grown database with `shingle_by_hash` kept (63 chunks) | 1,489 ms |
| One stage-2 chunk without it (four runs of 63 chunks) | 137, 143, 108 and 102 ms |
| `CREATE INDEX shingle_by_run_id` over 2,500,000 existing rows | 2,061 ms; wrote 734.8 MB for 186 MB of growth |
| The same statement at the next start, the index now there | 0 ms, nothing read or written |
| `CREATE INDEX shingle_by_hash` over 5,000,000 rows | 7,455 ms; wrote 2,445.7 MB for 500 MB of index |
| The same over 7,500,000, 10,000,000 and 12,500,000 rows | 22,386 ms, 27,708 ms and 37,854 ms |
| SQLite's progress callback, asked for every 1,000 virtual-machine steps, during `CREATE INDEX shingle_by_run_id` over 2,500,000 rows | 22,500 calls |
| The same callback during `CREATE INDEX shingle_by_hash` over 5,000,000 rows | 55,000 calls |
| The same callback during each of the seven `DROP INDEX` statements | 0 calls |

Four readings of it:

- **An index build writes four to five times the index's size**: 3.95 times for `shingle_by_run_id`, 4.9 times for `shingle_by_hash` at every size. ADR-182 §4 counted the same four transfers: the sort's temporary files written and read back, the write-ahead log, and the WAL checkpoint. The morning's start-up wrote 11.7 GB for 2.6 GB of growth, 4.5 times. That fits one or more index builds. It does not say which index.
- **SQLite reports progress during a build and none during a drop.** A build is a loop over rows. A drop is one step of SQLite's virtual machine.
- **One run's rows, with `shingle_by_occurrence` and `shingle_by_run_id`, took 742 MB here, and its share of a built by-hash index 250 MB.** So a stage 2 that writes one whole run uses up the pages freed by a by-hash index over up to about three runs before it extends the file. The fourth row is that case: the dropped index covered three runs, 750 MB, and the run written after it left 1,098 of its pages unused.
- **Writing a run through a kept by-hash index reads none of the earlier runs' part of it.** A separate pass wrote a whole run of 63 chunks into the row-grown database with the index kept and a page cache large enough to hold everything, so that each page was read at most once. It read 0.2 MB of the database. Every key of a new run begins with its run id, so its rows go into pages of their own. What a kept index costs is the scattered writes inside that run's own part as it grows, which is the 1,489 ms above.

## Decision

### 1. A statement whose cost grows with a table says that it is starting and when it has ended

**A single SQL statement that builds or removes an index over rows that already exist is announced by whoever issues it**: one INFO line before it and one after it. The line before says what is being done, to which index, and over how many rows at most. The line after says that it is done and how long the statement took, in seconds with one decimal. Both are written only when the statement has work to do. A statement that finds nothing to build or remove says nothing.

There are three such statements today. Stage 4b's build already has its two lines (ADR-182 §2.4), and they are unchanged. This record adds the other two: stage 2's drop, here, and start-up's builds, in §3.

**A change that adds another such statement adds its two lines in the same change.**

**Stage 2's drop.** When `extractionReader` has found that the step is not finished and `shingle_by_hash` is there, it writes, around the drop:

```
Stage 2 (extraction) is removing shingle_by_hash, over up to <N> shingle rows, before it writes any; SQLite reads the whole index to remove it, which took two hours on a 16 GB database for an index grown row by row or built into the pages one had freed, and stopping before it ends undoes it
Stage 2 (extraction) removed shingle_by_hash in <S> s
```

`<N>` is `SELECT MAX(rowid) FROM shingle`, as in ADR-182 §2.4 and for its reason: the answer is free, and it is exact unless rows have been deleted. `<S>` is the wall clock of the `DROP INDEX` statement. Where the index is not there, on every resume of a stopped stage 2 and on every new working directory, nothing is dropped and nothing is said. Where the step is finished, nothing is dropped and nothing is said, as before.

The lines sit where the drop is: after `Stage 2 (extraction) sidecar is healthy`, before the `resumes run` line and before the first file.

**The first line says the worst that has been measured and that a stop loses the work**, because those are the two things the operator needs at the moment of deciding whether to wait. A `DROP INDEX` is one transaction. A process stopped inside it leaves the index as it was, and the next invocation starts the drop again from the beginning.

**Nothing is written between the two lines.** SQLite gives nothing to report: its progress callback was not called once during any drop measured. A line written on a timer would report that the clock is running, which the operator's own clock already does, and it would go on being written if SQLite had hung.

**`pipeline` writes the lines, and `similarity` is not touched.** `ShingleHashIndex` already offers `exists()`, `shingleRowsUpTo()` and `drop()`, and it goes on saying nothing itself. A commit under `similarity/` or `extraction/` moves every stage-2 run id (ADR-058, ADR-182 §2.5), and on the archive that is a whole stage 2 under a new run, with a drop and a build, paid in order to announce a drop.

### 2. Stage 2 goes on dropping `shingle_by_hash`, on ADR-182 §2.2's test, unchanged

**The two hours were the price of removing an index that had been grown row by row, or one built into the pages such an index freed.** The disk read 115 pages a second, a seek for every page. In the probe an index grown row by row has every one of its pages out of place, and one built into its freed pages has two thirds of them out of place. One built into new pages lies in one stretch. Which of the first two the archive's index was that afternoon is not known (Not known).

**From ADR-182 on, nothing grows that index row by row.** `schema.sql` does not create it and stage 2 does not write through it, so every by-hash index a database holds after its first drop was built whole by stage 4b. Where that build went into new pages, the next drop reads one stretch of the file. For the 4.6 GB that ADR-182 §4 estimates for the archive's first build, that is 30 to 46 seconds at the 100 to 150 MB a second ADR-182 assumed for this disk. **That figure is an estimate. No drop of a built index has been measured on `H:`.**

**The arithmetic #400 asked for.** The archive's stage 2 is 10,406 file occurrences in chunks of 16: 651 chunks.

| Where the saving per chunk was measured | With the index | Without | Saved per chunk | Saved over 651 chunks |
|---|---:|---:|---:|---:|
| ADR-180, solid-state disk, 10,000,000 rows | 3,905.6 ms | 96.2 ms | 3,809 ms | 41 minutes |
| ADR-182, a virtual machine, warm, 10,000,000 rows | 1,694.8 ms | 243.1 ms | 1,452 ms | 16 minutes |
| This probe, solid-state disk, 2,500,000 rows | 1,489 ms | 137 ms | 1,352 ms | 15 minutes |

**Against those figures the drop of 2026-10-04 was not repaid: 118 minutes spent, 15 to 41 saved.** None of the three was measured on `H:`. The one observation of stage 2 writing through the index on `H:` is ADR-180's, on 2026-10-02, under the rollback journal, at 11.4 GB: 520 files took 2 to 5 minutes early in the stage and 10 to 12 minutes late in it, with the step thread inside SQLite in 14 of 15 samples. Over 10,406 files that is somewhere between 40 minutes, if every 520 took 2, and 4 hours, if every 520 took 12. How much of it was the index was never measured there. **So whether that drop was repaid on `H:` is not known. It could have been either.**

**It does not decide the question, because that drop is not the one a later invocation makes.** What stage 2 pays from now on is the drop of an index stage 4b built. The three routes the tickets name:

- **Keep the drop, and say so.** Taken. Dropping costs a read of one stretch of the file, estimated at under a minute on `H:`, and stage 4b's rebuild, which ADR-182 §4 measured at about a minute per 25,000,000 rows and estimated at four to ten minutes for the archive's first. Keeping the index costs a whole-archive stage 2 the 15 to 41 minutes above on the disks measured, and it is expected to cost more on `H:`, where ADR-180 watched stage 2 slow down as the stage went on. Where the index is one of the two disordered kinds, the drop is long, and §1's lines say so before it starts.
- **Skip the drop when nothing would be gained.** Refused. The one case that needs no threshold is a stage 2 whose step is not finished and which has nothing left to write. ADR-182 §2.2 considered it and gave it no rule: it is rare, and it costs one drop and one build. With a built index that is still minutes. Every other form of the rule is "skip when stage 2 will write few rows", and that needs a number: how few. There is such a number. A new run's rows go into a part of the index of their own (Measurements), so a small stage 2 writes through a kept index at little cost, while a drop and a rebuild cost the same minutes however little stage 2 writes. On the figures measured off `H:`, where a chunk saves 1.4 to 3.8 seconds and a rebuild over 25,000,000 rows takes about a minute, the two cross at a few tens of chunks. On `H:` neither side has been measured. A threshold enforced there before it was observed is what this project does not do, and it would not act on the archive, where a new run id replays every survivor: 651 chunks. The case it would serve, a small corpus in a working directory that holds a large one, is in "What this does not decide".
- **A cheaper mechanism.** None exists inside one database file. SQLite cannot free a b-tree without reading it: the bytes read equal the index's size in all seven drops measured. The same holds for a table, so building the index into a table of its own, which #401 suggests, would change nothing. An index kept in a second database file could be removed by deleting the file, with no read at all. ADR-009 has one database, and this record does not reopen it for a cost that a built index does not have.

### 3. An index `schema.sql` creates stays in `schema.sql`, and start-up says when it builds one over existing rows

**`schema.sql` goes on declaring every index that is not built by a stage, and start-up goes on building the ones an older database lacks** (ADR-173 §3). What changes is that the build is no longer silent. The class that announces it is in `pipeline`, and it issues `schema.sql`'s own `CREATE INDEX` statements and a `MAX(rowid)` against tables other modules own (ADR-041). It names no table itself: every name it uses is read out of `schema.sql`.

**Before `schema.sql` is applied, start-up finds every index it names that the database does not have, on a table that exists and holds at least one row.** For each, in the order `schema.sql` names them, it writes a line, runs that index's own `CREATE INDEX` statement, and writes a second line:

```
Start-up is building index <index> on <table>, over up to <N> rows, which this database does not have yet; on a large database this takes minutes, and stopping before it ends undoes it
Start-up built index <index> in <S> s
```

`<N>` is `SELECT MAX(rowid) FROM <table>`. `<S>` is the wall clock of that one statement. Every table in `schema.sql` has a rowid.

- **The statement is `schema.sql`'s own, read from that file.** There is no second list of indexes. An index added to `schema.sql` later is announced with no further change, which is how §1's last rule is kept for this file.
- **An index on a table with no rows gets no line.** It is built at once. A new working directory, where every table is empty, starts as quietly as it does today, and so does every test context.
- **An index that is already there gets no line.** Every start after the first is as it was: the afternoon's took 5 seconds.
- **One pair of lines for each index, each around its own statement**, so the time stated is that index's and the operator sees which one is slow. That is also how the question this record could not answer (which statement took the twelve minutes) answers itself the next time.

**Why the index stays where it is.** Three other places were considered.

- **Built by the stage that first needs it**, as stage 4b builds `shingle_by_hash`. Refused. `shingle_by_hash` is built by its reader because stage 2 must write without it, so it has to be absent for part of every run. An index under ADR-173's rule has no such part: it is wanted by the foreign-key check on any delete, from any stage, and by `ForeignKeysToFileOccurrenceAreIndexedTest`, which reads the schema. A stage that builds it would leave the rule false until that stage ran, and it would pay the same minutes in a different place.
- **Only for commands that read the table**, since `vespera label` never reads `shingle` (#402). Refused. The index is built once for each database by whichever command starts first. Leaving it out of `label`'s start moves the wait to the next `run` and adds a second way for a start to go.
- **In a file of its own, apart from the table statements**, so that adding an index is a step the operator is told about (#402). Refused. The line tells the operator, and a second file is a second place for an index to be forgotten.

**The silence was the defect, not the place.** The build costs what it costs wherever it runs, and a stop during it loses it wherever it runs. It runs under the working-directory lock (ADR-177), so a second invocation is refused with a line of its own and never waits on it.

## Why this shape, and what the others cost

- **A line on a timer while the statement runs.** Refused in §1: it reports the clock.
- **Real progress for the builds**, from SQLite's progress callback, which fired 22,500 times during a build over 2,500,000 rows. Not taken here. It would give stage 4b's build and start-up's a percentage, and it would give the drop nothing, since the callback is never called during one. It is in "What this does not decide".
- **One pair of lines around the whole of `schema.sql`, on every start.** Refused. It would be written at every start of every command for a script that takes milliseconds, and when one statement took twelve minutes it would not say which.
- **A warning in the pull request that ships an index**, which is what ADR-182 did. It did not reach the operator at the moment of the wait, and the estimate in it was one to three minutes.
- **Remove the row-grown index faster by reading the database file from end to end first**, so that the drop finds its pages in the file cache. Not taken. It is unmeasured, it depends on the machine having memory to cache the file, and the one database known to have had such an index has already paid for its drop.

## Consequences

- **A stage 2 that drops the index says so**, and says when it has. The two-hour wait of 2026-10-04 would have begun with a line that named it.
- **A start-up that builds an index over existing rows says so**, index by index. A start-up that builds nothing says nothing more than it does today.
- **A database made by a build before ADR-182 still pays one long drop**, at its first stage 2 that writes. Nothing here shortens it. The archive's database has paid it.
- **An index stage 4b builds into pages a row-grown index freed is still slow to drop**: 68.6% of its pages were out of place in the probe. That happens when the stage 2 between the drop and the build wrote less than the drop freed. On the probe's proportions one whole run uses the pages freed by an index over up to about three runs, and the build after it goes into new pages. Whether the archive's next drop is of a disordered index is not known. It is announced either way.
- **No stage-2 run id moves, and stages 3 to 6b run again once.** The change is in `pipeline` and in tests. Stage 2's implementation version spans `extraction` and `similarity` only, so stage 2 is not replayed and nothing is dropped on account of this change. Every stage from 3 on spans `pipeline`, so each runs again under a new run id at the first invocation after upgrading, as after any change to `pipeline` (ADR-058). Stage 4b then finds `shingle_by_hash` in place and builds nothing.
- **No schema version moves** (ADR-059, ADR-173 §3). No table changes and no index is added.
- **Text that states the old silence must change with the code**: the comment above the drop in `ExtractionJobConfiguration.extractionReader`, which says the drop *"says nothing"*. `ShingleHashIndex.drop`'s Javadoc says the same of the method, which stays true of the method, and that file is not touched (§1).
- **`AGENTS.md` says no defect is known and open against what ships.** #401 and #402 are open until the code this record asks for is merged. The pull request that ships it adds them to that paragraph as closed.
- **`CONTEXT.md` needs no new term.** Stage, run, invocation and shingle are used as defined there.

## Tests

Both classes are whole-job slice tests on `@CascadeSliceTest`, and both read what the invocation wrote through `OutputCaptureExtension`.

- **`ShingleHashIndexRemovalIsAnnouncedInvocationTest`** (`src/test/java/io/algernon/vespera/pipeline/`, #401):
  1. A stage 2 under a new run id, over a database holding `shingle_by_hash` and an earlier run's shingles, writes the removing line and then the removed line, once each, between `sidecar is healthy` and the first `[extraction] finished`, and the first states `MAX(rowid)` of `shingle` as it was before the invocation. **Fails today**: the index is dropped and nothing is said.
  2. A stage 2 that finds no index writes neither line. Passes today and has to go on passing.
  3. An invocation with every step finished leaves the index and writes neither line. Passes today and has to go on passing.
- **`StartUpSaysWhichIndexItBuildsTest`** (the same directory, #402) points the slice at a database file of its own, made before the context starts: the shipped schema, two verdict rows, three shingle rows, and `verdict_by_run_id`, `verdict_by_occurrence`, `shingle_by_run_id` and `walk_anomaly_by_walk_id` dropped.
  1. The start builds the three indexes on the two tables that hold rows, and writes for each a building line naming the index, its table and the row count of that table, 2 or 3, followed by its built line, in `schema.sql`'s order, once each. **Fails today**: the indexes are built and nothing is said. Three indexes on two tables are used so that a line written for `shingle_by_run_id` alone does not pass.
  2. The start builds the index on the empty table and writes nothing naming it. Passes today and has to go on passing.
  3. The start writes nothing about `shingle_by_occurrence`, which was there. Passes today and has to go on passing.

  **Whatever class makes the start-up announcement has to be added to `CascadeSliceTest`'s `@Import` list.** That slice is a `@JdbcTest` and scans no component, so a bean it does not name is not in the context and the first test stays red. The annotation's own Javadoc says a change to how the job is composed is a change there.
- **ADR-182's tests are unchanged and go on passing.** `ShingleHashIndexInvocationTest` pins that stage 2 drops the index whenever it has work to do, which §2 keeps.

## Not known

- **Which `schema.sql` statement took the twelve minutes.** `CREATE INDEX IF NOT EXISTS shingle_by_run_id` is the likely one: it was new in that build, it is on the largest table, and the 11.7 GB written for 2.6 GB of growth is the ratio an index build has. No statement was timed on that database, and nothing here opened it. §3 does not depend on which it was: it announces all 28.
- **How large `shingle_by_hash` was on that database**, and so whether the 3.44 GB read by 15:50 was all of it.
- **Whether the index dropped that afternoon was the row-grown one or one stage 4b had built since.** #400 does not say what the morning's invocation did after 10:13. If its stage 2 dropped the row-grown index and its stage 4b built a new one into the freed pages, the afternoon dropped that one. The read rate fits either and does not fit an index built into new pages.
- **How long the drop of a built index takes on `H:`.** 30 to 46 seconds for 4.6 GB is arithmetic from an assumed 100 to 150 MB a second, not a measurement.
- **Whether the two-hour drop was repaid by the stage 2 that followed it** (§2).
- **Whether the drop of a disordered index is faster from a warm file cache on `H:`.** It was 28 times faster on the solid-state disk measured, 205 ms against 5,790 ms.

## What this does not decide

- **Progress during a build.** SQLite's progress callback could give stage 4b's build and start-up's builds a percentage of `<N>`. It needs the callback registered on the connection that runs the statement, through `sqlite-jdbc`'s own API and not through `JdbcTemplate`, and a decision on cadence under ADR-093. A ticket of its own if the two lines prove too little on a build of ten minutes.
- **Reading the file first to warm the cache before a long drop** ("Why this shape").
- **A small stage 2 beside a large one.** A small corpus walked into a working directory that already holds a large one has stage 2 of a few files drop, and stage 4b rebuild, an index over every run's rows. Two remedies exist and neither is taken: skipping the drop below some number of file occurrences, which needs that number measured on the disk it will run on (§2), and a partial by-hash index per run, which ADR-182 left undecided.
- **Whether `shingle_by_hash` should live in a database file of its own** (§2). That would reopen ADR-009.
