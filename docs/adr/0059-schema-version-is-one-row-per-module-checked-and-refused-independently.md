# ADR-059 — `schema_version` is one row per module, checked and refused independently

- **Date**: 2026-08-29
- **Status**: accepted

## Context

ADR-049 defers Flyway/Liquibase and specifies `schema.sql` per module (`CREATE TABLE IF NOT EXISTS`) plus an explicit `schema_version` table checked at startup, refusing to open on mismatch. With nine capability modules each owning their own tables (ADR-041), and each forbidden from depending horizontally on any other capability module (ADR-040), the granularity of that one `schema_version` table was undecided, along with what "refuses to open" actually does and how a version bump is applied with no migration tool present.

## Decision

**One row per module: `schema_version(module TEXT PRIMARY KEY, version INTEGER NOT NULL)`.** A single global counter would couple every module's schema evolution — bumping `extraction`'s schema would force `embedding`'s already-computed vectors to be treated as stale too, coupling modules ADR-040/041 deliberately decoupled, and repeating the over-eager-invalidation cost ADR-058 just rejected for run ids, at larger scale. The check itself is a small shared utility living in `ledger` (every module can already depend on it), called by each module at startup with its own name and its own code-compiled expected version.

**A mismatch is a hard, loud failure — not graceful degradation.** The checker throws immediately, naming the module and both the stored and expected versions, before any read or write touches that module's tables. Nothing attempts to run "everything except the broken module": this is a two-command CLI running one cascade against one shared database, and a schema the code doesn't recognize means it has no guarantee it's interpreting existing rows correctly. A partial run that silently skipped the broken module would produce a result that looks complete while curating a fraction of the archive — the exact failure `CONTEXT.md` names for a partial walk, reproduced at the schema level instead.

**The manual upgrade path, for this slice, is delete-and-recreate.** ADR-049 already defers real migration tooling specifically until the database holds irreplaceable data — cached extraction, vectors, human labels. None of `ledger`, `corpus`, or `profile`'s tables hold anything like that yet: a walk is cheap to redo (resumable, ADR-055) and occurrences carry no independent judgement worth preserving. So a version mismatch in this slice's modules is resolved by dropping and recreating that module's tables and re-running census. Building a general manual-migration procedure now, ahead of any module that actually needs one, would be solving a problem this slice's data doesn't have.

## Consequences

**The harder case — a schema change to `extraction` or `embedding` after real data exists — is explicitly not solved here.** It is left to whichever future ADR introduces that module's first schema change, at which point ADR-049's own stated trigger for adopting Flyway/Liquibase fires. This decision does not pre-empt that; it only settles what happens while nothing expensive exists yet.

**Every module needs both a `schema.sql` (already required by ADR-049) and a compiled-in expected-version constant**, checked against its `schema_version` row via the shared `ledger` utility at startup. For the hand-off spec.

**A stale database is indistinguishable, from the operator's side, between "this is genuinely an older build" and "a migration is owed."** The error message names the module and both versions; resolving which of the two is true, and doing anything about it, is a human judgement call this decision does not automate.

## Amends

None. This supplies the granularity and the startup-check behaviour ADR-049 left open; it does not change ADR-049's substance.
