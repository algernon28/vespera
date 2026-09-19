# ADR-127 — A database lock is waited out by SQLite's busy timeout in the URL, not by Hikari's connection timeout

- **Date**: 2026-09-19
- **Status**: accepted
- **Amends**: nothing. Corrects a claim the shipped configuration made about itself, and completes a measurement [ADR-111](0111-a-cluster-fault-is-a-row-in-synthesis-and-a-re-run-under-the-same-id-repairs-rather-than-regenerates.md) recorded without changing the configuration it measured.

## Context

`src/main/resources/application.yaml` carried a Hikari block whose comment said a stage waiting on the database lock waits rather than failing, and named `connection-timeout: 300000` — five minutes — as what bought that wait. The intent was real: the pipeline runs unattended for days ([ADR-035](0035-pipeline-never-publishes-adapter-invoked-separately-never-unattended.md), as [ADR-101](0101-the-run-ends-at-the-generated-documents-there-is-no-publication-stage-and-no-publication-target.md) leaves it), and a run that gives up on a lock it could have waited out costs every day already spent on it.

The setting did not do what the comment said. `connection-timeout` is Hikari's wait for a connection *from the pool*; with `maximumPoolSize` left at its default of ten, a borrower is handed one in about a millisecond and the timeout is never reached. What governs waiting on SQLite's own file lock is SQLite's `busy_timeout`, which the driver defaults to 3,000 ms and which the shipped JDBC URL never set. [ADR-111](0111-a-cluster-fault-is-a-row-in-synthesis-and-a-re-run-under-the-same-id-repairs-rather-than-regenerates.md) recorded the same measurement — "three seconds, the driver's default, which nothing in this project sets" — while settling that a second transaction cannot be opened inside the generation step's transaction. That record left the configuration as it found it; this one changes it.

### Measured, against the shipped dependency set (sqlite-jdbc 3.53.2.1, HikariCP as Spring Boot 4.1.1 manages it)

| Probed | Result |
| --- | --- |
| `PRAGMA busy_timeout` on a plain connection | 3,000 ms |
| `PRAGMA busy_timeout` with `?busy_timeout=1500` in the URL | 1,500 ms |
| a contended second write at `busy_timeout=1500` | fails with `[SQLITE_BUSY] The database file is locked (database is locked)` after about 1,519 ms |
| Hikari, key for key as shipped, against a held write lock | `getConnection()` returned in about 1 ms; the write failed after about 3,035 ms |
| `maximumPoolSize` / `connectionTimeout` in force | 10 / 300,000 ms |
| `Connection.isValid(1)` on a local file | under 1 ms |

So a contended stage failed after three seconds where the configuration promised five minutes, and the knob the comment named was not the knob that governs it. The `journal_mode` in force is `delete`, as the probe also reported, so there is no writer concurrency to fall back on.

## Decision

### The five-minute intent stands, and `busy_timeout` is where it is set

`busy_timeout=300000` joins `foreign_keys=on` in the datasource URL. That parameter is SQLite's own busy handler and is what a contended write actually observes. Five minutes is kept as the value: a lock defended for longer than that is a fault worth failing on, and [ADR-035](0035-pipeline-never-publishes-adapter-invoked-separately-never-unattended.md)'s unattended run makes a wait cheaper than a failure.

### The comment names the knob, and the two waits are told apart

The Hikari comment now says the lock wait lives in the URL and that `connection-timeout` times the wait for a connection from the pool. `connection-timeout` stays 300,000: it is inert at a pool of ten with the pipeline's sequential stages, and were it ever reached, waiting rather than failing is the same intent [ADR-035](0035-pipeline-never-publishes-adapter-invoked-separately-never-unattended.md) gives the lock wait.

`validation-timeout` drops from 300,000 to 5,000. A health check on a local SQLite file answers in under a millisecond, so five minutes bounded nothing; the smaller value is Hikari's own default and is proportionate to a check that cannot hang.

### The behaviour is pinned by the shipped configuration and by the driver

`ShippedConfigurationTest` gains two claims: the shipped URL names `busy_timeout=300000` and keeps `foreign_keys=on`, and a connection built from that URL reports `PRAGMA busy_timeout = 300000`. The first fails on the old text. The second is the one that matters: it establishes that the URL parameter is the driver's own knob rather than a string only Hikari reads, and it is the claim that would catch a value quietly reverted while the text still named the parameter.

## Consequences

**A contended write now waits up to five minutes before failing**, where it failed after three seconds. The database file is locked for a write for exactly as long as a writer's transaction is open, and nothing in this project holds one across a network call, so a lock outliving five minutes means a fault rather than ordinary contention.

**ADR-111's clause is now historical.** "Three seconds, the driver's default, which nothing in this project sets" was true when measured; the shipped configuration now sets five minutes. That record is append-only, so it stands as written and this record is the amendment. The javadoc in `GenerationTasklet` that repeated the clause is source rather than record and is updated in place, because it describes what a reader of the current code would observe.

**The Hikari numbers are no longer claimed to buy the lock wait.** `connection-timeout` is documented as the pool wait it is, and `validation-timeout` is proportionate to a local check. Neither is a lock setting, and nothing else in the shipped configuration is.

**What this does not decide.** Whether five minutes is the right ceiling for every lock, as opposed to a defensible one for an unattended pipeline; whether `journal_mode` should move to write-ahead logging so readers and writers stop contending at all, which the same measurement would inform and which is a separate decision; and whether `connection-test-query: SELECT 1` is still wanted now that the driver supports JDBC 4's `isValid`.