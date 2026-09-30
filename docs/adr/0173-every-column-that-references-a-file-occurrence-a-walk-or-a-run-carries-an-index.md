# ADR-173 — Every column that references a file occurrence, a walk or a run carries an index

- **Date**: 2026-09-30
- **Status**: accepted
- **Rests on**: [ADR-008](0008-sqlite-is-the-census-artifact-store.md) and [ADR-009](0009-one-storage-technology-single-database.md). The ledger is one SQLite file, and the datasource opens it with `foreign_keys=on`, so every delete of a parent row is checked against every table that references it.
- **Rests on**: [ADR-115](0115-a-re-walk-that-observed-nothing-new-is-discarded-and-a-run-is-continued-under-its-own-id.md), whose discard of a traversal that observed nothing new is the delete measured here. What it deletes, and when, is unchanged.
- **Keeps**: [ADR-059](0059-schema-version-is-one-row-per-module-checked-and-refused-independently.md). No module's schema version moves, because no table changes shape (§3).
- **Settles** [#368](https://github.com/algernon28/vespera/issues/368).

## Context

### What happened

Every invocation over the whole archive spent about four and a half minutes in stage 0 after the walk itself had finished: 22:31:56 to 22:36:30 and 22:39:49 to 22:44:15 on 2026-09-28, and 10:33:05 to 10:37:40 on 2026-09-29. `jstack` showed the `main` thread in `Ledger.discardWalk`, on `DELETE FROM file_occurrence WHERE walk_id = ?`. The walk had observed exactly what an earlier walk had recorded, so ADR-115 discarded it, which deleted its 52,938 duplicate occurrence rows.

### Why

To delete a parent row under `foreign_keys=on`, SQLite looks, in every table with a column that references the parent, for a row pointing at it. SQLite documents that lookup as the query `SELECT rowid FROM <child> WHERE <column> = ?`, run once for each parent row deleted. When no index has that column as its first column, the query reads the whole child table. The discard therefore read each of those tables once for each occurrence it deleted.

No decision ever required an index on a referencing column. The primary keys and `UNIQUE` constraints that each table's own decision needed happened to lead with some of them, and the rest were left unindexed.

### The measurement

Measured for this record on 2026-09-30, on a **copy** of the archive's ledger, `vespera.db`, 10,496,176,128 bytes. The copy was taken while no Java process was running, and only the copy was opened. The tool was Python's `sqlite3` module (SQLite 3.49.1; the application's `sqlite-jdbc` bundles 3.53) with `PRAGMA foreign_keys=ON`.

The copy held four walks. Two of them, walks 5 and 22, were observations of the whole archive with 43,101 occurrences each. Everything derived is recorded against those occurrences, so their own occurrences cannot be deleted without breaking a foreign key. The delete that ADR-115 actually makes is of a walk that nothing references yet. The measurement therefore recreated that walk inside a transaction: one new walk row, and a copy of walk 22's 43,101 occurrence rows under it. It then ran `discardWalk`'s three statements, timed each one, and rolled the transaction back.

| | `DELETE FROM file_occurrence WHERE walk_id = ?` (43,101 rows) | `DELETE FROM walk_anomaly …` | `DELETE FROM walk …` |
|---|---|---|---|
| Before | **434.8 s** | 0.000 s | 0.000 s |
| After the indexes below | **0.182 s** | 0.000 s | 0.000 s |

That is 7¼ minutes for 43,101 rows on a 10.5 GB ledger. The invocations above spent 4½ minutes on 52,938 rows when the ledger was smaller. The cost grows with the derived tables, not with the walk alone.

`EXPLAIN QUERY PLAN` of the documented lookup, `SELECT 1 FROM <child> WHERE <column> = ?`, reported `SCAN` for 24 referencing columns. Nine reference `file_occurrence`, and every delete of an occurrence paid for these:

- `superseded_by.representative_occurrence_id` (185,690 rows in the copy)
- `redundant_with.redundant_with_occurrence_id` (738)
- `relevance_score.winning_seed_occurrence_id` (4,344)
- `document_cluster.winning_seed_occurrence_id` (4,344)
- `cluster.winning_seed_occurrence_id` (112)
- `synthesis_doc.winning_seed_occurrence_id`
- `call_exemplar.occurrence_id` and `call_exemplar.winning_seed_occurrence_id`
- `cluster_fault.winning_seed_occurrence_id`

Two reference `walk`, and the delete of the walk row pays for these:

- `run.walk_id`
- `walk_anomaly.walk_id`, which `discardWalk` also deletes from directly, with a full read of the table

Thirteen reference `run`. Nothing deletes a run today, so these cost nothing yet:

- `run_upstream.upstream_run_id`
- `run_id` of `verdict`, `content_hash`, `superseded_by`, `detected_format`, `extraction_metric`, `extraction_fault`, `minhash_signature`, `redundant_with`, `unusable_seed`, `relevance_score`, `document_cluster` and `relevance_label`

The same list follows from the schema alone. Load `schema.sql` into an empty database and, for each table, compare `PRAGMA foreign_key_list` against the first column of every index in `PRAGMA index_list`. The two agree column for column.

Building all 24 indexes on the copy took **0.6 s** in total. The largest took 0.13 s.

## Decision

### 1. The rule

**Every column that references `file_occurrence`, `walk` or `run` is the first column of an index on its own table.** A primary key, or a `UNIQUE` constraint whose first column it is, counts, because SQLite builds an index for it. So does an existing index whose first column it is.

The rule covers any table added later. A new column that references one of the three tables gets its index in the same change that adds the column.

The rule covers `run` as well as the two tables that `discardWalk` deletes from. Deleting a run is only one decision away, and a rule that stops at the tables deleted from today would have to be found again, the way this one was.

### 2. The shape of an index added under it

Where the rule finds no index, `schema.sql` adds one, directly after that table's `CREATE TABLE`:

```sql
-- SQLite checks this foreign key by scanning without it; a walk discarded under foreign_keys=on paid 4½ minutes for its absence (ADR-173).
CREATE INDEX IF NOT EXISTS <table>_by_<column> ON <table> (<column>);
```

The name is the table and the **whole** column name, `_id` included. For example, `superseded_by_by_representative_occurrence_id`, not `superseded_by_by_representative_occurrence`. This keeps the name mechanical, so it never needs choosing.

Indexes that already satisfy the rule keep their names. That includes `verdict_by_occurrence` and `shingle_by_occurrence`, which predate this rule.

The index is on the one column. A composite index would satisfy the rule too, but each of these exists only for the foreign key lookup, and a second column would widen it to serve a query nobody makes.

### 3. No schema version moves

`spring.sql.init.mode: always` runs `schema.sql` at every start, and `CREATE INDEX IF NOT EXISTS` builds each missing index on an existing ledger at its next start. `SchemaVersionGuard` compares a version recorded per module with a version compiled into that module. By each module's own `*Schema` Javadoc, the version counts changes to its tables' shape, meaning a table added or re-keyed. `schema.sql`'s header says the version exists because `IF NOT EXISTS` never alters a table that already exists, so an old ledger would silently lack what the code expects. An index alters no table, and `IF NOT EXISTS` applies it to an old ledger all the same, so there is nothing for a version to catch.

There is no precedent either way. Each of the four indexes already in the schema arrived in a change that also added a table. `shingle_by_hash` arrived on an existing table, but in the change that added stage 4's tables and moved `SimilaritySchema` to version 3. This is the first change that adds only indexes, and for the reason above it bumps no guard.

## Consequences

- **The discard of a duplicate walk costs a fraction of a second**, where it cost 7¼ minutes on the ledger measured.
- **The first start after this change builds the indexes on the existing ledger, once.** It took 0.6 s on the 10.5 GB copy. The build happens during schema initialisation, before any step runs, and has no output of its own. Each later start finds the indexes already there.
- **Each write to a referencing table also maintains one or two more indexes.** The largest such table is `superseded_by`, at 185,690 rows, which gains two. The indexes on `run_id` add a small cost to every stage's writes and buy nothing until a run is deleted. That is accepted in exchange for a rule with no exceptions to remember.
- **The ledger grows a little.** Each index holds one integer or one run id per row of its table. The largest, on `superseded_by`, is small next to `shingle`'s 24,910,924 rows and its two indexes.
- **Nothing is re-keyed and no row changes.** Every primary key, constraint and table shape stays as it is.

## Tests

- **`ForeignKeysToFileOccurrenceAreIndexedTest`**: runs the schema into the test profile's in-memory SQLite, the same way a start does. For every foreign key in every table that references `file_occurrence`, `walk` or `run`, it asserts that some index in `PRAGMA index_list`, or the table's `INTEGER PRIMARY KEY`, has that column first in `PRAGMA index_info`. A failure names each table and column left without one. This is the test that makes a future table carry the rule.
- **`DiscardingAWalkSearchesEveryReferenceTest`**: runs `Ledger.discardWalk` with foreign keys on. The duplicate walk has 3,000 occurrences, and an earlier walk has 3,000 occurrences with 2,999 `superseded_by` rows pointing at one of them. The test asserts that the duplicate's rows are gone and the earlier walk's are untouched. `EXPLAIN QUERY PLAN` of a `DELETE` does not show the foreign key check, so for each column that references `file_occurrence` or `walk` it takes the plan of the documented lookup, `SELECT 1 FROM <child> WHERE <column> = ?`, and asserts `SEARCH`, not `SCAN`. It asserts the same for `discardWalk`'s own `DELETE FROM walk_anomaly WHERE walk_id = ?`.
