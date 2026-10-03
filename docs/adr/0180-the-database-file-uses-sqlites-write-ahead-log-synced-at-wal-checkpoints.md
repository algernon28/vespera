# ADR-180 — The database file uses SQLite's write-ahead log, synced at WAL checkpoints

- **Date**: 2026-10-03
- **Status**: accepted
- **Amends**: [ADR-127](0127-a-database-lock-is-waited-out-by-sqlites-busy-timeout-not-by-hikaris-connection-timeout.md), which left open in "What this does not decide" whether `journal_mode` should move to write-ahead logging. It now does. ADR-127's reason for putting a connection-level SQLite setting in the datasource URL stands: every connection the pool opens reads it, and `ShippedConfigurationTest` can see it. This decision adds three such settings beside `foreign_keys` and `busy_timeout`. It changes neither of those two, and it adds no writer concurrency, so ADR-127's lock wait stays exactly as it is. ADR-127's sentence that the journal mode in force is `delete`, and the matching sentence in [ADR-111](0111-a-cluster-fault-is-a-row-in-synthesis-and-a-re-run-under-the-same-id-repairs-rather-than-regenerates.md), were true when measured and are now historical. Both conclude that there is no writer concurrency to fall back on, and that is still true (§5).
- **Extends**: [ADR-054](0054-a-corpus-is-its-root-path-the-database-lives-in-a-configured-working-directory.md). The database lives in a working directory the operator configures. This decision adds one requirement: that directory must be on a disk attached to the machine that runs Vespera.
- **Rests on**: [ADR-008](0008-sqlite-is-the-census-artifact-store.md) and [ADR-009](0009-one-storage-technology-single-database.md). There is still one SQLite file. While it is open, two files sit beside it.
- **Keeps**: [ADR-115](0115-a-re-walk-that-observed-nothing-new-is-discarded-and-a-run-is-continued-under-its-own-id.md) and [ADR-116](0116-a-runs-completion-is-recorded-per-step-because-several-steps-share-one-run.md). How an interrupted stage resumes does not change. §2 shows that a power cut under this setting leaves the database in a state those two already handle.
- **Does not settle** [#364](https://github.com/algernon28/vespera/issues/364). Two invocations writing one database file still collide (§5).
- **Answers** [#378](https://github.com/algernon28/vespera/issues/378), and measures its premise false (§1). What does make stage 2 slow is left as an open decision (§6), carried by [#381](https://github.com/algernon28/vespera/issues/381).

**Two words here are SQLite's, not the project's.** A *WAL checkpoint* is SQLite copying its write-ahead log back into the database file. It is not a walk's **Checkpoint** (ADR-055). The *write-ahead log* is SQLite's file `vespera.db-wal`. It is not a **Log**, the document kind ADR-171 puts out of scope. This record writes both in those qualified forms, and never bare.

## Context

### What was observed

On 2026-10-02, a whole-archive run was replaying stage 2 from cached conversions. Docling was idle. Vespera's step thread was inside SQLite in 14 of 15 one-second `jstack` samples, in `NativeDB.step`, under the chunk's transaction. The database file, `vespera.db`, was 11.4 GB, and a 117 MB `vespera.db-journal` sat beside it. The datasource URL names no `journal_mode` and no `synchronous`, so SQLite used its defaults, `DELETE` and `FULL`. Throughput fell over the stage, from 520 files every 2–5 minutes to 520 every 10–12 minutes.

#378 read this as the cost of the rollback journal. Under that journal, every commit copies each page it is about to change into `-journal`, syncs that file, writes the database file and syncs it again. The ticket asked for write-ahead logging (`WAL`) with `synchronous=NORMAL`: a commit appends the changed pages to the write-ahead log, `vespera.db-wal`, and nothing is synced until a WAL checkpoint copies the write-ahead log back into the database file.

### Where the database file lives

The working directory in the ticket is `H:\Working docs corpora\Vespera_working_dir`. **`H:` is a local disk.** `fsutil fsinfo drivetype H:` reports a fixed drive, *Unità fissa*. The volume is NTFS, labelled "WD - Home Storage", 932 GB. `net use` lists no mapped connections, and `subst` lists none. `fsutil fsinfo sectorinfo H:` reports normal seeks and no TRIM, so the disk is a spinning one. That matters later: a sync and a scattered write both cost more on it than on the solid-state disk the measurements below ran on.

### What was measured

None of this was measured on the archive's database. A run was in stage 2 against it at the time (`vespera.log` shows stage 2 starting at 09:13:11 on 2026-10-03), and copying an 11 GB file that is being written gives a torn copy. A throwaway probe built a synthetic database instead. The probe is not in the repository; its source is in PR #380's history, at commit e182e49 (`src/test/java/io/algernon/vespera/JournalModeBenchmark.java`; see Tests). It runs the shipped `schema.sql`, then inserts 4,000 file occurrences of 2,500 shingle rows each, 10,000,000 rows, 2.95 GB. The rows go in with both of `shingle`'s indexes in place, so the index pages are as full as an index grown one insert at a time leaves them.

A first build that created the indexes after loading packed every leaf page full. Then almost every measured insert split a page. That is not how a database grown by stage 2 looks, so that build was discarded. The second build's chunk wrote a 121 MB rollback journal per commit, against the 117 MB observed on the archive, so it changes about as many pages as a real chunk does.

One repeat is one stage-2 chunk. It is a single transaction with 16 documents (`ExtractionJobConfiguration.CHUNK_SIZE`) of 2,500 random-hash shingle rows each, inserted in batches of 5,000 as `Shingler` sends them. There are 10 repeats per setting, each setting starting from a fresh copy of the same base. The disk is the solid-state `C:` (sqlite-jdbc 3.53.2.1, JDK 26). The *per chunk* figure is the total over all ten, including a closing `PRAGMA wal_checkpoint(TRUNCATE)`, divided by ten. Under WAL, a commit that skipped its WAL checkpoint has only put the write off, and a median of commits alone would hide the commit that pays for it.

| Setting | Median transaction | Median commit | Per chunk, WAL checkpoint included | Largest `-wal` |
|---|---:|---:|---:|---:|
| `DELETE`/`FULL`, as shipped | 3,623.6 ms | 911.1 ms | 3,905.6 ms | — |
| `WAL`/`NORMAL`, automatic WAL checkpoint at 1,000 pages | 3,532.7 ms | 1,471.4 ms | 3,814.2 ms | 230,040,232 B |
| `WAL`/`NORMAL`, WAL checkpoint at 250,000 pages | 3,084.4 ms | 179.0 ms | 3,393.4 ms | 1,144,886,232 B |
| `DELETE`/`FULL`, 256 MiB page cache | 3,273.0 ms | 1,173.3 ms | 3,453.5 ms | — |
| `WAL`/`NORMAL`, 256 MiB cache, WAL checkpoint at 250,000 pages | 2,694.7 ms | 328.1 ms | 3,113.6 ms | 1,144,886,232 B |
| `DELETE`/`FULL`, without `shingle_by_hash` | 92.9 ms | 40.3 ms | 96.2 ms | — |
| `WAL`/`NORMAL`, without `shingle_by_hash` | 105.2 ms | 53.7 ms | 108.7 ms | 7,881,592 B |
| `DELETE`/`FULL`, `shingle_by_hash` replaced by an index on `run_id` alone | 145.7 ms | 56.3 ms | 154.1 ms | — |

Building `shingle_by_hash` once, over all 10,000,000 rows, after the ten chunks took 13,678.8 ms.

A stage-2 chunk is a large transaction. The other stages commit small ones: verdict rows a chunk at a time, tens of rows each. The same harness was run with 4 rows a document, 64 shingle rows a transaction, and 200 repeats:

| Setting | Median transaction | Median commit | Per transaction, WAL checkpoint included |
|---|---:|---:|---:|
| `DELETE`/`FULL` | 14.8 ms | 5.3 ms | 23.9 ms |
| `WAL`/`NORMAL`, automatic WAL checkpoint | 8.7 ms | 0.5 ms | 11.8 ms |

Finally, a probe opened a file database with the URL this decision ships, on the same driver:

- A database created under `DELETE` reports `wal` once a connection opens it with `journal_mode=WAL`.
- Every connection reports `synchronous` 1, `busy_timeout` 300,000 and `foreign_keys` 1, and `journal_size_limit` reports the value in the URL.
- After a 400,000-row transaction the write-ahead log was 26,747,072 bytes. With `journal_size_limit=1048576` it was back to 1,048,576 bytes after the next commit. Without a limit it stayed at 26,747,072.
- When the last connection closed, both `vespera.db-wal` and `vespera.db-shm` were deleted. That holds unless another connection, in any process, still has the database open (measured during review, below).
- A database file reopened with a URL that names no journal mode still reports `wal`.
- README's read-only query was run through Python's `sqlite3`, reporting SQLite 3.49.1 (`sqlite3.connect('file:…/vespera.db?mode=ro', uri=True)`). It returned the committed count both while a writer held the database open and after the writer closed.

During review on 2026-10-03, on Linux, with sqlite-jdbc 3.53.2.1 (reporting SQLite 3.53.2) and HikariCP 7.0.2 as Spring Boot 4.1.1 manages them, and Python's `sqlite3` reporting SQLite 3.45.1:

- A pool with the shipped Hikari settings, left idle, retired its one connection at each `max-lifetime` expiry and closed it before opening the replacement. Each time, `vespera.db-wal` was copied back and deleted, and the replacement created a new, empty one: sampled every millisecond, it was absent for about 2–5 ms at 28.4 s and at 59.6 s, and in a second run it changed inode at 26.4 s and 53.8 s. All 50 rows written survived. An earlier 75 s probe sampled only once a second and missed this. Closing the pool deleted both `vespera.db-wal` and `vespera.db-shm`.
- With a read-only connection still open when the last writer closed, both files stayed. The write-ahead log still held the writer's commits. A copy of `vespera.db` taken alone did not hold them: the table the writer had created was not in it. Closing the read-only connection afterwards left both files as they were, because a read-only connection cannot copy the write-ahead log back.
- SQLite's source (3.53.2, `sqlite3WalClose`) runs the closing WAL checkpoint and deletes the write-ahead log only when the closing connection can take an exclusive lock on the database file, that is, when no other connection in any process has it open.

## Decision

### 1. The shipped datasource opens the database in write-ahead-log mode, synced at WAL checkpoints

The URL in `application.yaml` becomes:

```
jdbc:sqlite:${vespera.working-dir}/vespera.db?foreign_keys=on&busy_timeout=300000&journal_mode=WAL&synchronous=NORMAL&journal_size_limit=536870912
```

**This is not the fix for stage 2's throughput, and the table above says so.** On a stage-2 chunk, `WAL`/`NORMAL` with SQLite's own WAL checkpoint costs 3,814 ms where the rollback journal costs 3,906 ms, which is no real difference. The chunk changes about 30,000 scattered index pages, and every journal mode has to write all of them back into the database file. The rollback journal first copies their old contents. The write-ahead log writes their new contents twice, once into itself and once at the WAL checkpoint. The time goes on reaching and changing those pages, in the transaction (3.6 s), and not on the commit (0.9 s). What touches them is `shingle_by_hash` (§6).

**It is shipped for the commits it does make cheaper.** Every stage except stage 2 commits small transactions, and there `WAL`/`NORMAL` halves the cost: 11.8 ms a transaction against 23.9 ms, and 0.5 ms a commit against 5.3 ms. A commit stops waiting on a sync. Each sync it skips costs more on `H:`'s spinning disk than on the disk measured. A reader no longer blocks the writer either. Under the rollback journal, README's read-only query, or a database browser left open, holds a shared lock that a committing writer has to wait out.

**In the URL, not in a `SQLiteConfig`.** sqlite-jdbc applies the URL's parameters to every connection it opens. `synchronous` is a per-connection setting, so every connection must carry it, and the URL is the one place that reaches each one. ADR-127 put `busy_timeout` there for the same reason. A `SQLiteConfig` would need a `DataSource` bean in code, a second place the datasource is defined beside `application.yaml`, and `ShippedConfigurationTest`, which reads the file Spring reads, would no longer see it. An operator overriding `spring.datasource.url` sees the whole setting in one string. `journal_mode=WAL` is recorded in the database file itself, so after the first connection it is a no-op on every later one.

**The first start converts an existing database by itself.** The first connection sets the mode, and the file stays in it. The switch rewrites no pages: it changes how the next commit is written, not what the file already holds. The operator does nothing by hand.

### 2. A power cut may lose the last commits, and nothing in the ledger depends on them

Under `WAL` with `synchronous=NORMAL`, a commit is on disk once a WAL checkpoint has synced the write-ahead log. A WAL checkpoint also copies back and syncs the write-ahead log when the last connection closes, which at the end of an invocation is the pool closing, unless another connection, in any process, still has the database open (measured above).

- **A process that dies loses nothing.** That covers a crash, a kill, or Ctrl-C. A committed page is already in the operating system's file cache, and the next connection recovers it from the write-ahead log.
- **A power cut or an operating-system crash can lose the most recent commits.** These are the commits made since the last WAL checkpoint, and nothing else. Each lost transaction goes whole, and the database file is never left half-written. SQLite promises that for `NORMAL` in `WAL` mode, and it is the property the database needs.

That is compatible with how Vespera resumes (ADR-115, ADR-116):

- A lost chunk is a chunk the step never committed. The step is not recorded as complete, so the next invocation discards its unfinished rows under the same run id and redoes them, as it already does after any interruption.
- A lost completion record is a step that is not recorded as finished, so the next invocation runs it again.
- The files written beside the database can say more than it after a power cut: the reports, `relevance-labels.yaml` and the deliverable may have been written from commits the cut then lost. None of them is the record of what was done. Each is written again from the database by the pass that redoes the lost work.
- The one thing an operator could have to repeat is a `vespera label` whose answers were lost in a power cut. The window is while `label` runs, or later if another connection still held the database open when `label` exited, because then no closing WAL checkpoint synced its answers. A lost answer is still in `relevance-labels.yaml` only until the next `vespera run` rewrites that file from the database (ADR-169), which shows it blank, and a blank entry records nothing. The operator's recovery is to run `vespera label` again on the unchanged file before the next `vespera run`. This is a residual risk, recorded and accepted. The alternative not taken is a PASSIVE WAL checkpoint run by `label` after it records its answers.

### 3. WAL checkpoints are SQLite's own, and the emptied write-ahead log is cut back to 512 MiB

**No stage calls a WAL checkpoint.** SQLite's automatic WAL checkpoint runs at the commit that leaves the write-ahead log at 1,000 pages or more. It copies the write-ahead log back into the database file, and the next write starts the write-ahead log from its beginning. Two things grow the write-ahead log during a stage, and an explicit `PRAGMA wal_checkpoint(TRUNCATE)` at a stage's end prevents neither:

- One transaction's own size: no WAL checkpoint can run inside a transaction.
- A reader that never lets go: a WAL checkpoint cannot pass a page a reader still needs. Stage 2 reads through a paging reader, which holds no transaction between pages, so its WAL checkpoints complete.

A WAL checkpoint at a stage's end would only shrink the file after the stage. It would take a hook in each of the fifteen steps. And its `TRUNCATE` waits on any reader, README's query included. When the invocation ends, the pool closing its last connection does the same thing for free, so a working directory left by a clean exit holds `vespera.db` alone, unless another connection, in any process, still has the database open. Hikari's `max-lifetime` also retires an idle pool's only connection during the invocation, closing it before its replacement opens (measured above). So the closing WAL checkpoint, and the deletion of the write-ahead log, can also happen mid-invocation while no step holds a connection. That only syncs sooner, and nothing depends on it.

**`journal_size_limit=536870912` bounds what is left.** After a WAL checkpoint, SQLite reuses the write-ahead log file at its largest size rather than shrinking it. One very large transaction, such as discarding an unfinished run's shingle rows, would otherwise leave a write-ahead log of gigabytes on disk for the rest of the invocation. The limit is 512 MiB because one stage-2 chunk leaves about 230 MB of write-ahead log (measured above). A lower limit would cut the file back and grow it again every chunk. 512 MiB is still small beside an 11 GB database.

**The WAL checkpoint interval stays at SQLite's 1,000 pages.** A 250,000-page interval made a stage-2 chunk 11% cheaper, 3,393 ms against 3,814 ms. It did so by letting the write-ahead log grow past a gigabyte, so that pages touched by several chunks are written back once. That is a fraction of what §6 measures, and it is not taken.

### 4. The working directory stays on a local disk; nothing detects a share

Write-ahead logging keeps an index into the write-ahead log in shared memory, backed by `vespera.db-shm`. Every process that opens the database has to share that memory, so they all have to run on one machine. SQLite documents that WAL does not work on a network filesystem.

**Nothing detects a share, and nothing falls back.** Java cannot reliably tell a share from a local disk on Windows. A mapped drive reports its remote filesystem's type, `NTFS`, through `FileStore.type()`, and a check that cannot tell is worse than none. Neither sentence was measured for this record. A share was never safe for this database anyway. SQLite's locks under the rollback journal are only as good as the network filesystem's, so two machines opening one database file on a share could already corrupt it. Whether one Vespera process on one machine, with every connection in that process, works with the database on a share was not measured either, and SQLite does not support it. The rule is stated where the operator reads about the working directory: keep it on a disk attached to the machine that runs Vespera. The archive's working directory is on one.

### 5. What this does not do

- **Two writers still collide.** WAL lets readers and one writer proceed together. It does not let two writers. A second invocation on the same working directory (#364) still waits out `busy_timeout`, or fails at once when it tries to upgrade a read into a write after the other has committed. WAL reports that case as `SQLITE_BUSY_SNAPSHOT` where the rollback journal reported `SQLITE_BUSY`. #364 stays open, and its working-directory lock is still what closes it.
- **The test profile is unchanged.** Its datasource is `jdbc:sqlite::memory:`, whose journal mode answers `memory` whatever the URL asks for. That is why the test pinning this decision activates no profile.
- **An older build keeps the mode.** A database file switched by this build stays in WAL when an earlier jar opens it, with that jar's `FULL` sync. Going back to the rollback journal takes `PRAGMA journal_mode=DELETE` run by hand while nothing else has the file open.

### 6. Open: what stage 2's chunk actually waits on

`shingle_by_hash`, on `shingle (run_id, shingle_parameter_identity, shingle_hash)`, takes each new row at a random place in an index far larger than any page cache. Each insert reaches a different leaf page. Without that index, a stage-2 chunk costs 96 ms where it costs 3,906 ms. With an index on `run_id` alone in its place, which still serves ADR-173's per-run discard, it costs 154 ms. Building `shingle_by_hash` once afterwards over 10,000,000 rows took 13.7 s.

Over the archive's 10,406 occurrences, about 650 chunks, the gap is roughly 40 minutes of index maintenance in stage 2. Against that, a one-time build would cost well under a minute even at the archive's 25 million rows, extrapolated from 13.7 s for 10,000,000 rows on the solid-state `C:`; it was not measured on `H:`. ADR-081 requires the containment index, which `schema.sql` creates as `shingle_by_hash`. No record decides when it is built. ADR-081 counts "one rare-shingle index pass" in stage 4's cost without naming `shingle_by_hash`, and ADR-173 records only that the index arrived in the change that added stage 4's tables. That decision is not made here. It is the decision the slowdown in #378 is actually waiting on, and [#381](https://github.com/algernon28/vespera/issues/381) carries it.

`README.md`'s "Where things live" says what the operator needs from this decision: the two files beside `vespera.db` while a command runs, how to copy the working directory, and that it belongs on a local disk.

## Consequences

- **Two files sit beside `vespera.db` while an invocation is running**: `vespera.db-wal`, the write-ahead log, and `vespera.db-shm`, its index. A clean exit folds the write-ahead log back and deletes both, unless another connection, in any process, still has the database open, a database browser for one. A crash leaves them, and the next start recovers from them.
- **A copy taken while `vespera.db-wal` exists must take all three files.** Otherwise it misses every commit since the last WAL checkpoint. The simplest safe copy is taken after the invocation has ended, once `vespera.db` stands alone. If `-wal` is still there, because the last invocation crashed or something else had the database open when it ended, start and stop one invocation with nothing else open on the database, or copy all three files together.
- **README's read-only query keeps working**, while a run is going and after it. Opening the database read-only may leave a `vespera.db-wal` and a `vespera.db-shm` behind, because a read-only reader cannot delete them. Left by a one-off query after a clean exit, the write-ahead log is empty. Left by a reader that was still open when an invocation ended, it holds that invocation's last commits (measured above). Either way the next writer takes them over.
- **The write-ahead log is bounded only while WAL checkpoints complete.** Between WAL checkpoints it can reach 512 MiB on disk. Inside one very large transaction, such as a discard of a whole run's shingle rows, it grows to that transaction's size, gigabytes for a large run. While a reader holds a snapshot open, it grows without bound, because no WAL checkpoint can pass a page that reader still needs (§3). A database browser holding a read transaction open on `vespera.db` during a run is such a reader. Closing it lets the next WAL checkpoint complete, and the file is cut back to 512 MiB. A browser left open and idle does not grow the write-ahead log, but any open connection keeps the write-ahead log from being copied back and deleted at exit.
- **Stage 2 is not faster on a large database because of this.** The other stages' commits are, and the measurement says by how much.

## Tests

- **`WriteAheadDatabaseTest`** (`src/test/java/io/algernon/vespera/`) is a profile-free `@JdbcTest` slice. It builds the datasource `application.yaml` ships, moves only `vespera.working-dir` into a temporary directory, and asserts four things:
  - Each of two connections held at once from the shipped pool reports `journal_mode` `wal`, `synchronous` 1, `wal_autocheckpoint` 1,000, `journal_size_limit` 536,870,912, `busy_timeout` 300,000 and `foreign_keys` 1, over the file `vespera.db` in the working directory.
  - A database written under the rollback journal reports `wal` when opened with the shipped URL, and keeps its row.
  - With the shipped URL, a commit leaves `vespera.db-wal` and `vespera.db-shm` beside the database file. Once the last connection closes, both are gone, the database file alone holds the commit, and a plain connection still finds it in `wal`.
  - A second pool with every setting of the shipped one, written through and then closed as the context closes it at the end of a command, leaves no `vespera.db-wal` and no `vespera.db-shm`, and the database file alone holds the commit. This is the claim README makes about a command that has ended, and it rests on the pool closing, not on one connection.

  The first three failed before §1's URL shipped, each on `delete` where `wal` is expected, and pass with it. The fourth needs `journal_mode=WAL` too, since without it no write-ahead log is written. Removing one of the three new URL parameters at a time, supplied as a `spring.datasource.url` override, turns tests red: without `journal_mode=WAL` all four fail, without `synchronous=NORMAL` the first fails, and without `journal_size_limit` the first fails (run at the gate, on Java 21).
- **The measurement in Context is not a test, and no code for it is in the repository.** The figures came from a throwaway probe. Its source is in PR #380's history, at commit e182e49 (`src/test/java/io/algernon/vespera/JournalModeBenchmark.java`). Its method: a synthetic 2.95 GB database of 10,000,000 shingle rows, built from the shipped `schema.sql` with both of `shingle`'s indexes in place, on a solid-state disk; one stage-2 chunk of 16 documents × 2,500 shingle rows per repeat, 10 repeats per setting, each from a fresh copy of the base, reported as medians; and a small transaction of 64 shingle rows, with 200 repeats.
