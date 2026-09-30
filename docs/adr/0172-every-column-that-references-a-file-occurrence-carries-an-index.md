# ADR-172 — Every column that references a file occurrence carries an index

- **Date**: 2026-09-29
- **Status**: accepted

## Context

The ledger's `file_occurrence` table (ADR-048) is a central hub: every derived fact is keyed on one of its rows (the occurrence reference a verdict needs belongs to a content identity (ADR-067, ADR-069) above, or a relevance score (ADR-020) above, etc.).

When stage 0 runs a walk that produces many duplicate file occurrences, `discardWalk` (ADR-115) uses SQLite's foreign key mechanism to delete the whole archive under one invocation. It is slow (ADR-368).

The foreign keys in `schema.sql` were added because they were required by the decisions that created each table. None of those decisions carried the performance rule "all foreign keys to `file_occurrence (id)` get an index." ADR-008/009 (SQLite) and ADR-115 (the walk discard) lack that rule.

## Decision

Every column in the schema that has a `REFERENCES file_occurrence (id)` constraint and is not the first column of an existing index or primary key gets an index added to `schema.sql`.

The rule applies to all tables that reference `file_occurrence (id)` — and to any future table added after this change, so that they cannot forget it. It also applies to columns that `REFERENCES walk (id)` and `REFERENCES run (id)`, because `discardWalk` deletes from `walk` and `file_occurrence`, and the same performance rule applies there.

## Consequences

**The before and after of the 4½-minute `discardWalk`:** Stage 0 is fast, not 4½ minutes. 

The first start after this change builds the new indexes on the existing ledger. This is a one-time cost of seconds to a minute or two on the 2.4 GB archive ledger. Say so in the PR body.

**The rule itself:** Adds to ADR-008/009 (SQLite) and ADR-115 (the walk discard). No schema version bump (ADR-059). The schema carries what a recorded decision requires (ADR-046).

**No rewriting of history:** This is a performance rule, not a schema change. No existing rows are re-keyed. Future foreign key additions are blocked by the rule.

## Amends

Amends ADR-008/009 (SQLite) and ADR-115 (the walk discard).

The rule rests on them, but the implementation is in this ADR.