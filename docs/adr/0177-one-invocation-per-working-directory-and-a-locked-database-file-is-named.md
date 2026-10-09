# ADR-177 — One invocation per working directory, and a locked database file is named

> **Partly amended — see [ADR-209](0209-the-ledger-is-four-records-behind-one-type-no-capability-module-names-spring-batch-and-a-tables-sql-is-its-owners.md).** `Ledger.survivors` and `Ledger.occurrencesOf`, now `Verdicts.survivors` and `Occurrences.occurrencesOf`, read a page at a time through the application's `JdbcTemplate`, no longer through `JdbcPagingItemReader`. So the two reads §2.1 leaves outside the translator, and the last Consequence lists among what is not translated, are translated: a lock either meets is named as §2.2 says. The two passages are marked below. Everything else in this record stands.

> **Extended — see [ADR-211](0211-no-class-holds-every-survivor-of-a-run-another-tables-rows-are-read-a-page-of-survivors-at-a-time.md).** The process does one thing more while its environment is prepared, before any connection pool exists. After the working directory is created, its lock taken as this record has it, and its profile's shape checked, a fourth listener that `VesperaApplication.main` registers, `TemporaryFilesInTheWorkingDirectory`, sets SQLite's directory for temporary files to the working directory, once, on a connection of its own to no database. It is registered last, so a start refused for a working directory in use never reaches it and changes nothing of SQLite's; and where SQLite refuses the directory, the start ends there. ADR-211 changes nothing this record decided.

- **Date**: 2026-10-03
- **Status**: accepted
- **Settles**: [#364](https://github.com/algernon28/vespera/issues/364).
- **Extends**: [ADR-050](0050-the-pipeline-has-exclusive-access-to-the-corpus.md). ADR-050 gives the pipeline exclusive access to the corpus for the length of a run and leaves the mechanism to the operator. `SchemaVersionGuard` already leans on the same guarantee for the database file ("there is no second process to race"), and nothing gave it. This decision gives it for the working directory, and the engine enforces it: one invocation at a time holds the working directory, and a second one is refused before it opens the database file. Exclusive access to the corpus stays the operator's to arrange, as ADR-050 says.
- **Extends**: [ADR-054](0054-a-corpus-is-its-root-path-the-database-lives-in-a-configured-working-directory.md). The working directory gains one more file, `vespera.lock`. The lock follows `vespera.working-dir` however it was set, `--db-dir=<path>` included.
- **Rests on**: [ADR-127](0127-a-database-lock-is-waited-out-by-sqlites-busy-timeout-not-by-hikaris-connection-timeout.md) and [ADR-180](0180-the-database-file-uses-sqlites-write-ahead-log-synced-at-wal-checkpoints.md). It changes neither the busy timeout nor the journal mode. §3 says why neither covers a second invocation, measured under the URL ADR-180 ships. ADR-180 §5 deferred exactly this case to #364.
- **Keeps**: [ADR-141](0141-the-cli-exits-with-the-commands-exit-code-and-the-scheduler-no-feature-uses-is-removed-at-its-source.md). The refusal's exit code is delivered by `VesperaApplication.main`, as every other one is. It is 1, the code a refused profile already returns (#321).
- **Narrows**: the closing lines of stage 2 and of seed extraction (#311, #306). When the failure is a locked database file, they drop the advice about docling-serve and say to close what holds the file (§2.3). Stage 4a's closing line is unchanged.

## Context

### What happened

On 2026-09-28, two invocations ran against the same working directory, `H:\Working docs corpora\Vespera_working_dir`:

| Time | Invocation | What it did |
|---|---|---|
| 22:31:51 | `java -jar vespera-00287c4.jar run …` (PID 45916) | Walked, then entered stage 2 and began `Shingler.discardForRun` (about 3 minutes of deletes) |
| 22:38:44 | IntelliJ "Local SpringApp" (PID 14236) | Started, walked the same root, and began stage 0 on the same `vespera.db` |
| 22:39:36 | PID 45916 | Stage 2 failed, and the invocation ended |

The first invocation's closing line named an `INSERT INTO extraction_metric` statement and `[SQLITE_BUSY] The database file is locked (database is locked)`, then said "Fix what that names -- if docling-serve stopped answering, bring it back -- and run the same command again." Nothing in it said that a second Vespera invocation was the cause, nothing said which database file, and the advice pointed at the sidecar. Nothing stopped the second invocation from starting.

Earlier the same day, an IntelliJ run that had not loaded `.env` opened the old `<repo>/.vespera/vespera.db` instead of the archive's, and stopped with "module extraction expects schema version 5, but the database records version 4". The line named no file, so it read as a fault in the archive's database.

That run was under the rollback journal. ADR-180 has since moved the shipped datasource to SQLite's write-ahead log, which changes what a collision looks like but not whether it happens (§3).

### What was measured

All on 2026-10-03, on Windows 11 Pro 26200, JDK 26.0.2.1, an NTFS volume on the solid-state `C:`, with throwaway probes outside the repository. A holder process took a lock with `FileChannel.tryLock(position, 1, false)` on a file named `vespera.lock`, wrote one line, and slept. A second JVM probed it.

**The operating-system lock, between processes:**

| Probe | Lock on byte 0 | Lock on byte `Long.MAX_VALUE - 1`, past the end of the file |
|---|---|---|
| Another process, `tryLock` on the same region | returns `null` | returns `null` |
| The same file named another way: upper case, forward slashes | returns `null` | returns `null` |
| Another process, `Files.readString` | fails: "another process has locked a portion of the file" | reads the holder's line |
| `cmd /c type vespera.lock` | fails, the same message | prints the holder's line |
| Holder killed with `taskkill /F`, then `tryLock` again | acquired within 101 ms | acquired within 92 ms |

Within one JVM, a second channel's `tryLock` on a region the JVM already holds throws `OverlappingFileLockException` rather than returning `null`.

**What the lock does not stop:** with the holder alive, another process could delete `vespera.lock`. Java opens the file sharing deletion. A third process then created a new `vespera.lock` and locked it while the first holder was still running. The lock is on the file, and a deleted file is a different file from its replacement.

**SQLite under the shipped URL** (sqlite-jdbc 3.53.2.1, `journal_mode=WAL&synchronous=NORMAL`, two connections to one file):

| Case | Busy timeout | Result |
|---|---|---|
| A. A second writer while the first holds an open write transaction | 2,000 ms | `SQLITE_BUSY` after 2,014 ms: the busy timeout is waited out |
| E. The same, with the shipped 300,000 ms; the first commits after 3 s | 300,000 ms | the second writer succeeds after 3,052 ms |
| B. A transaction that has read, then writes after another connection committed | 2,000 ms | `SQLITE_BUSY_SNAPSHOT` (extended code 517) after 0 ms: the busy timeout is not consulted |
| C. A writer while another connection holds a read transaction open | 2,000 ms | succeeds in 3 ms |
| D. A commit while another connection holds a read transaction open | 2,000 ms | succeeds in 3 ms |

In cases A and B, `SQLException.getErrorCode()` is 5, `SQLITE_BUSY`, and the extended code is in `SQLiteException.getResultCode()`. The message reads `[SQLITE_BUSY_SNAPSHOT] Another database connection has already written to the database (database is locked)` in case B.

## Decision

### 1. An invocation holds the working directory, and a second one is refused before it opens the database file

**The lock.** Every invocation takes an operating-system lock on the file `vespera.lock` in the working directory, and holds it until its process ends. A new environment listener in `pipeline`, `WorkingDirectoryLock`, takes it. `VesperaApplication.main` registers it beside the two listeners already there, after `WorkingDirectoryPreparer`, because the directory must exist, and before `ProfileShapeCheck`. That order is what puts the lock before the datasource opens `vespera.db`. The listener reads the working directory from the property `WorkingDirectoryPreparer` reads, and does nothing when it is unset, as both of the others do.

- It opens `vespera.lock` for writing, creating it if needed, and calls `tryLock(Long.MAX_VALUE - 1, 1, false)`. The locked byte lies past the end of anything written, so the file's content stays readable while it is held: a lock on byte 0 made the file unreadable to every other process, `type` included (measured above).
- **Acquired:** it truncates the file and writes one line, `pid=<pid> started=<ISO-8601 date and time, with offset, to the second> command=<the arguments, joined by single spaces>`, from byte 0. The arguments are the event's own (`ApplicationEnvironmentPreparedEvent.getArgs()`). The channel is kept in a static field, so the lock lives as long as the JVM. Nothing releases it before then, and nothing deletes the file.
- **Refused:** `tryLock` returned `null`, or threw `OverlappingFileLockException` because this JVM already holds it. The listener reads the file's line, without the lock, and throws `WorkingDirectoryInUseException`. A line that cannot be read, or is blank, because the holder is between truncating and writing, is described as `its details could not be read`.

**What the operator sees.** Exactly one line on standard error, and exit code 1 (`CommandLine.ExitCode.SOFTWARE`):

```
another Vespera invocation is already using the working directory <dir> (<holder's line>); wait for it to finish, or stop it, then run the same command again. Two invocations on one working directory would write to the same database file at once.
```

It is printed by a `SpringBootExceptionReporter`, `WorkingDirectoryInUseRefusal`, named in `META-INF/spring.factories` beside `MisshapenProfileRefusal`, and `VesperaApplication.main` exits with its code, beside the branch that already exits for a refused profile. That is #321's shape, for the same reason: the failure happens while the environment is prepared, so no banner, no bean and no context refresh is there to report. The line carries no record id: the operator reading it has no copy of these records.

**Every invocation that starts the application takes it.** That is `vespera run` and `vespera label`, and also `--help` and `--version`. Each of them opens `vespera.db` and runs `schema.sql` against it at start-up (`spring.sql.init.mode: always`). An exemption for the last two would have the listener parse picocli's grammar a second time, ahead of picocli, which is the drift `WorkingDirectoryOption` already guards against for `--db-dir`. The cost is that `vespera --version` against a working directory a run is using is refused, with a line that names the run. That is accepted.

**A crashed holder never blocks.** The operating system releases the lock when the process ends, however it ends: a kill released it within about 100 ms (measured above). The file stays, holding the dead holder's line, and the next invocation takes the lock and overwrites the line. The file's existence means nothing. Only the lock does.

**One directory, named two ways, is still one lock.** The lock belongs to the file the operating system opened, not to the path that named it. A second spelling of the same directory was refused (measured above). The same holds for `--db-dir=<path>` and `vespera.working-dir` naming one directory differently.

**Who it covers.** Everything that starts through `VesperaApplication.main`: the packaged jar, and the IDE's run configuration, which was the second invocation on 2026-09-28. A test context built by Spring's test support does not go through `main` and takes no lock, so tests that share a working directory are unaffected.

### 2. A locked database file says which file, and that another process holds it

The lock in §1 removes the second Vespera invocation. Something else can still hold the database file: a database browser with a write or a transaction open, or a Vespera started with a `spring.datasource.url` that points into another working directory's database. When SQLite reports the file locked, the operator is told which file and why. Under an overridden URL, the file named is the working directory's, not the one the URL opened (Consequences).

**2.1 Translated where the queries are made.** Every `JdbcTemplate` statement in `src/main` goes through the one `JdbcTemplate` Spring Boot builds, and no code uses `JdbcClient`. Two reads do not: `Ledger.survivors` and `Ledger.occurrencesOf` read through Spring Batch's `JdbcPagingItemReader`, which builds a `JdbcTemplate` of its own over the datasource and never sees the application's translator. They stay outside it (Consequences). *(Amended by [ADR-209](0209-the-ledger-is-four-records-behind-one-type-no-capability-module-names-spring-batch-and-a-tables-sql-is-its-owners.md) §2: both reads are now pages of the ledger's own, each a statement on the application's `JdbcTemplate`, so no read of the ledger is left outside it, and a lock either read meets is translated.)* Spring Boot hands its template the application's `SQLExceptionTranslator` bean, if there is exactly one. A new bean in `ledger`, `LockedDatabaseFileTranslator`, is that translator:

- When the exception, or any cause beneath it, is an `org.sqlite.SQLiteException` whose result code's primary code (`getResultCode().code & 0xFF`) is 5 (`SQLITE_BUSY`) or 6 (`SQLITE_LOCKED`), it returns a `DatabaseFileLockedException`. That covers the extended codes too, among them `SQLITE_BUSY_SNAPSHOT` (517), which the shipped URL produces (case B).
- Anything else it translates exactly as the template would without it, through `SQLExceptionSubclassTranslator`. sqlite-jdbc's own exceptions carry no SQL state, so they arrive uncategorised either way. The delegation is what keeps every other exception typed as before: a connection the pool could not hand out in time (`SQLTransientConnectionException`) or a statement that timed out (`SQLTimeoutException`) still becomes the Spring exception it became without this bean.

**2.2 The exception names the file.** `DatabaseFileLockedException` extends Spring's `CannotAcquireLockException` and carries the database file, `<vespera.working-dir>/vespera.db` (ADR-054). Its message is:

```
the database file <path> is held by another process, which is writing to it or keeping a transaction open on it (<SQLite's message>)
```

`StepFailure.named` already returns the first failure beneath Spring Batch's own wrappers, so every closing line that names a failure now names this one without a change to it.

**2.3 The closing lines stop pointing at docling-serve.** Stage 2's closing line and seed extraction's both append "Fix what that names -- if docling-serve stopped answering, bring it back -- and run the same command again." For a locked database file that sends the operator to the sidecar. `StepFailure` gains `lockedDatabaseFile(StepExecution)`: whether the failure `named` reads has a `DatabaseFileLockedException` in its cause chain. When it is true, both lines end instead with:

```
Close whatever else has the database file open and run the same command again.
```

Every other failure keeps its sentence as it is. Stage 4a's closing line, "Fix what that names and run the same command again.", gives no misleading advice, and is not changed.

**2.4 The schema-version refusal names the database file.** `SchemaVersionMismatchException` names the file the guard's connection has open, as SQLite reports it in `PRAGMA database_list` (the full path, which `WriteAheadDatabaseTest` already relies on). Asking the connection, rather than rebuilding the path from configuration, names the file actually opened, even under an overridden `spring.datasource.url`, and `SchemaVersionGuard`'s constructor stays as it is. The message becomes:

```
module <m> expects schema version <e>, but the database file <path> records version <r>; delete and recreate <m>'s tables, then re-run census. If that is not the working directory you meant, name it with --db-dir=<path> or vespera.working-dir
```

An in-memory database reports no file, and is named `an in-memory database` in place of `the database file <path>`.

### 3. The busy timeout and the write-ahead log stay as ADR-127 and ADR-180 set them

Under the shipped URL, a second invocation on the same database file no longer fails the way it did on 2026-09-28. It does one of two things, depending on the moment:

- **It waits, then writes between the other's commits.** A writer that finds the write lock taken waits out the busy timeout (cases A and E). Each stage commits per chunk, so the five-minute wait almost always ends at the other invocation's next commit. Then both invocations write the same database file, interleaved, with no error at all. The ledger would hold two invocations' walks and runs at once, and `SchemaVersionGuard`'s read-then-insert would race. Nothing would say so.
- **It fails at once.** A transaction that has read, and then writes after the other invocation committed, gets `SQLITE_BUSY_SNAPSHOT` immediately (case B), and the busy timeout is never consulted. Stage 2's chunk reads the extraction cache before it writes, which is this shape.

Neither is cured by the busy timeout. A longer wait is still a wait that ends in interleaving, and a snapshot conflict cannot be waited out, because the snapshot is already stale. The cure is that there is no second writer, which is §1. **So `busy_timeout=300000` stays.** It still buys what ADR-127 bought it for: a write that meets a brief foreign lock, from a browser's write or README's read-only query, waits instead of failing (case E). A reader does not block a writer at all under the write-ahead log (cases C and D).

**`transaction_mode=IMMEDIATE` is the alternative not taken.** sqlite-jdbc can begin every transaction with `BEGIN IMMEDIATE`, which takes the write lock at the start, so a conflict becomes a wait rather than `SQLITE_BUSY_SNAPSHOT`. It would apply to every transaction in all fifteen steps, read-only ones included, and it was not measured against them. With §1 in place, the only writers left to conflict with are other programs the operator can close. A clear sentence naming the file (§2) serves that better than a different way of waiting.

## Consequences

- **A second invocation on one working directory is refused before it opens the database file**, with a line naming the directory and the holder's process id, start time and command. The first invocation carries on unaffected. A refused invocation never opens `vespera.db`, so it creates none in a fresh directory, and it writes nothing to any file in the working directory. Logging is configured before the lock is taken, so a refused invocation does open `vespera.log`, and creates it in a fresh directory, without writing to it.
- **`vespera.lock` stays in the working directory** after every invocation. It is harmless, and holds the last holder's line. README's "Where things live" lists it.
- **Deleting `vespera.lock` while an invocation runs defeats the lock** (measured above). README says not to. Opening the file without sharing deletion would need `com.sun.nio.file.ExtendedOpenOption.NOSHARE_DELETE` from `jdk.unsupported`, Windows-only, and was not taken.
- **The lock guards the working directory, not every URL.** An operator who points `spring.datasource.url` at another working directory's `vespera.db` bypasses it. §2's sentence then says the file is held by another process, but the file it names is `<vespera.working-dir>/vespera.db`, built from configuration, not the file the connection opened: the translator is handed an exception, and has no connection to ask, as `SchemaVersionGuard` asks in §2.4. Overriding the URL is outside the lock's guarantee already, and this is a known limit of the sentence there.
- **The working directory stays on a local disk** (ADR-180 §4). Byte-range locks on a network share were not measured, and nothing here relies on them.
- **`vespera --version` and `--help` are refused while another invocation holds the working directory** (§1).
- **A database file locked by another program fails the step with a sentence naming the file**, not an SQL statement, and stage 2's and seed extraction's closing lines no longer send the operator to docling-serve for it.
- **`sqlite-jdbc` moves from `runtime` to compile scope in `pom.xml`** (ADR-046). §2's translator reads `org.sqlite.SQLiteException`'s result code, which code cannot name against a runtime-only dependency. The jar carries the same driver as before.
- **A database-level failure outside Spring Boot's `JdbcTemplate` is not translated.** That is `schema.sql` run at start-up, a commit through the transaction manager, and `Ledger.survivors` and `Ledger.occurrencesOf`, whose `JdbcPagingItemReader` builds its own template (§2.1). Under the write-ahead log a commit never met a lock (case D), because a write transaction already holds the write lock, and under the write-ahead log a writer does not block a reader, so neither read meets the lock a second writer holds. That last is SQLite's documented behaviour ([sqlite.org/wal.html](https://sqlite.org/wal.html): "readers do not block writers and a writer does not block readers"), not something measured here: cases C and D measured the other direction, a write and a commit beside an open read. The start-up script can meet one only if another program holds a write for longer than the busy timeout. All are left as they are. *(Amended by [ADR-209](0209-the-ledger-is-four-records-behind-one-type-no-capability-module-names-spring-batch-and-a-tables-sql-is-its-owners.md) §2: the two reads are no longer outside the template, and a lock either meets is translated and names the database file. The start-up script and the commit remain outside it.)*

## Tests

- **`WorkingDirectoryLockTest`** (`pipeline`, plain JUnit, real files in a temporary directory): the listener writes the holder's line with this process's id and the arguments, and holds the lock. A second take in the same JVM is refused with `WorkingDirectoryInUseException`, naming the directory and this process's id. A child process, started from the test class path, that holds the working directory refuses the test JVM, naming the child's id, and leaves the file readable meanwhile. Once the child is killed, the next take succeeds and the line is rewritten. An unset working directory takes nothing. The exception's text is pinned exactly, including the case where the holder's line cannot be read.
- **`WorkingDirectoryInUseRefusalTest`** (`pipeline`): the reporter prints the message as one line on standard error and claims the failure, wrapped or not, and claims nothing else. `refuses` agrees, and the exit code is 1.
- **`WorkingDirectoryInUseIT`** (failsafe, no Docker): with the test JVM holding a temporary working directory, the packaged jar's `run` and `label` both exit 1, print exactly one line naming the directory and the test JVM's process id, and leave no `vespera.db` behind.
- **`LockedDatabaseFileTest`** (`ledger`, a profile-free `@JdbcTest` slice over the shipped URL in a temporary working directory, with the translator imported): a read-then-write transaction, after another connection has committed to the same file, fails through the shipped template with `DatabaseFileLockedException`, naming the database file and another process. A duplicate key fails exactly as a template with no translator reports it. `SQLITE_BUSY`, `SQLITE_BUSY_SNAPSHOT` and `SQLITE_LOCKED` are translated, and a constraint failure is not. A pool that timed out handing out a connection translates to the same exception `SQLExceptionSubclassTranslator` gives it, which a translator without the delegation would not.
- **`ClosingLineOverALockedDatabaseFileTest`** (`pipeline`): stage 2's and seed extraction's closing lines, over a locked database file, name the file and say to close what holds it, without mentioning docling-serve. Over any other failure, stage 2's keeps its docling-serve advice. `StepFailure.lockedDatabaseFile` sees the exception directly and beneath Spring Batch's wrapper.
- **`SchemaVersionGuardTest`** gains a test over a database file in a temporary directory: the mismatch names the file and says how to name the working directory.
- **The measurements in Context are not tests**, and no code for them is in the repository. They came from throwaway probes: a holder process and a prober, each a single-file Java program, and a two-connection SQLite program on the shipped URL. The method is recorded in Context.
