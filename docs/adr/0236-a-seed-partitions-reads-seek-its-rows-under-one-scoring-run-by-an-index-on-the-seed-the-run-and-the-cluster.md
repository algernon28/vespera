# ADR-236 — A seed partition's reads seek its rows under one scoring run, by an index on the seed, the run and the cluster

- **Date**: 2026-10-11
- **Status**: accepted on 2026-10-11.
- **Amends**: [ADR-218](0218-every-statement-whose-temporary-files-grow-with-the-corpus-is-an-exception-to-adr-060-with-its-size.md): §2's class of start-up index builds gains one, `document_cluster_by_winning_seed_run_and_cluster`, with the size measured below, so the class is thirty-two; §3's class loses row 12, the cluster sizes of one partition, which sorts nothing once the index is there. ADR-218 carries a note at its head saying so.
- **Keeps**: [ADR-173](0173-every-column-that-references-a-file-occurrence-a-walk-or-a-run-carries-an-index.md)'s index on each reference, `document_cluster_by_winning_seed_occurrence_id` and `document_cluster_by_run_id` among them; [ADR-223](0223-5fs-size-report-6a-and-6b-go-through-one-seed-partition-at-a-time-and-what-is-still-held-says-why.md) §3's reads of one partition, whose statements are unchanged.
- **Rests on**: `embedding.APartitionsReadsSeekOnlyItsRunsRowsTest`; a throwaway probe outside the repository (Measured).

## Context

`DocumentClusters.sizeOf`, `membersOf` and `sizesFor` each ask for one seed's rows under one scoring run, `WHERE run_id = ? AND winning_seed_occurrence_id = ?`. 5f, 6a and 6b each make one of them for every seed partition. The only index SQLite could plan them by was `document_cluster_by_winning_seed_occurrence_id`, on the seed alone, which ADR-173 put there for the foreign key: each read went through every row of the seed under every scoring run the working directory keeps, and nothing removes a scoring run's rows (ADR-229). `sizesFor` then grouped and sorted the rows it kept, ADR-218's row 12.

## Measured

**Method.** One throwaway probe outside the repository: Java 26 and the sqlite-jdbc the pom carries (3.53.2.1, SQLite 3.53.2), the shipped `schema.sql` without the new index, the shipped URL's parameters (`foreign_keys=on`, `journal_mode=WAL`, `synchronous=NORMAL`, `journal_size_limit=536870912`), and SQLite's directory for temporary files set once, on a connection to no database, before the database was opened (ADR-211 §12). `document_cluster` held 300,000 rows for each of 5 and of 10 scoring runs, run ids of 64 characters, 20 seeds and 50 cluster ordinals drawn at random. The new index was then built by the statement `schema.sql` ships, as start-up builds it on a database that lacks it. While it ran, the files open in the temporary directory were summed every 5 ms through `/proc/self/fd`, since SQLite unlinks each temporary file as it opens it on Linux, and the write-ahead log's size was read with them. Each read was timed as the least of five, one seed each, of the last run.

**Disk.** A Linux container's ext4 volume, not ADR-218's NTFS disk. The figure a row agrees with ADR-218's row 5 for the indexes on a run's id, 71 to 77.

| Rows kept | Build: temporary files | Bytes a row | Write-ahead log, its growth | Build time | `sizeOf`, before → after | `sizesFor`, before → after |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1,500,000 (5 runs) | 118,952,810 | 79.3 | 118,808,472 | 2.0 s | 0.058 s → 0.0008 s | 0.050 s → 0.0012 s |
| 3,000,000 (10 runs) | 236,913,845 | 79.0 | 237,600,432 | 4.1 s | 0.103 s → 0.0012 s | 0.108 s → 0.0013 s |

Before the index, each read grows with the rows of every scoring run kept; after it, with the one partition's rows of the run named.

## Decision

### 1. `document_cluster` gains an index on `(winning_seed_occurrence_id, run_id, cluster_ordinal)`

`CREATE INDEX IF NOT EXISTS document_cluster_by_winning_seed_run_and_cluster ON document_cluster (winning_seed_occurrence_id, run_id, cluster_ordinal)`, in `schema.sql`, beside the table. SQLite plans all three reads by it, `(winning_seed_occurrence_id=? AND run_id=?)`, and `sizesFor` counts each cluster's members in the index's order with no temp B-tree. No statement changes.

### 2. The calls

- **The cluster ordinal is the third column**, so that `sizesFor` sorts nothing and ADR-218's row 12 goes. It costs nothing in the build beyond the one column (Measured).
- **The seed comes first**, so the index answers the foreign key check ADR-173 put the seed's index there for, as well as the reads.
- **`document_cluster_by_winning_seed_occurrence_id` stays.** Taking it out would be a `DROP INDEX` in `schema.sql`, which runs on every start, for an index ADR-173 records; the new one serves its purpose too, so dropping it is a later change's to make, with its own record. *Put and not taken.*
- **Additive, so no module's schema version moves**, as `schema.sql` says of an index added before (ADR-059, ADR-197).

### 3. The build is one of ADR-218 §2's class

A database that lacks the index builds it once, at the first start after an upgrade, over every row `document_cluster` keeps, of every scoring run: **79.0 to 79.3 bytes of temporary files a row, and about as much again of write-ahead log while it runs**, which is ADR-218's row 5 for an index on a run's id. It is excepted with the class.

## Consequences

- 5f, 6a and 6b read one partition's memberships in time that grows with that partition, not with every scoring run the working directory has kept.
- The first start after the upgrade takes the build's time and room once: about 2 s and 240 MB, temporary files and log together, for 1,500,000 kept memberships, on the probe's disk.
- `EveryStatementThatSortsIsRecordedTest` holds no statement that sorts for `DocumentClusters`, and lists the new index.
- **Which run ids move**: none. `schema.sql` is in no module's path, and no class of `src/main` changes (ADR-058).

## Tests

- `embedding.APartitionsReadsSeekOnlyItsRunsRowsTest`: the three reads are planned on the seed and the run together, and `sizesFor` sorts nothing.
- `EveryStatementThatSortsIsRecordedTest`: `embedding.DocumentClusters` leaves both maps; `schema.sql`'s indexes are thirty-two.

## What this does not decide

- Taking out `document_cluster_by_winning_seed_occurrence_id` (§2).
- The same question of `relevance_score`'s reads by winning seed (ADR-218 rows 10 and 11), which this record did not measure.
