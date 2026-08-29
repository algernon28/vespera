# ADR-060 — `survivors` is a `ledger`-owned item reader, not a view

- **Date**: 2026-08-29
- **Status**: accepted

## Context

`docs/architecture.md` §1.4 already names `ledger`'s ownership as including "the `survivors(runId)` query," and ADR-014 makes survivors a query rather than a place documents move to — but not its form. ADR-041 records a live enforcement gap: module boundaries (`ledger` owns identity and verdicts; every other capability owns its own tables keyed by `occurrence_id`) are checked by Spring Modulith's `ApplicationModules.verify()`, which inspects Java type references via ArchUnit on bytecode — a raw SQL string crossing a table-ownership boundary is invisible to it. A SQL view exposing survivors would be exactly that: efficient, joinable by any other capability module directly in its own SQL, and unenforceable by the one mechanism this design has for catching a boundary violation.

## Decision

**`ledger` is the deep module; `survivors(runId)` is its interface for this concern.** No SQL view exists for another capability module to `JOIN` against. The join logic, the blocking-verdict semantics (`CONTEXT.md`'s "no blocking verdict"), and the run-chain resolution (ADR-048) all sit behind this one Java call — real behaviour, small interface, verified by `ApplicationModules.verify()` because it's a Java method, not a database object another module's SQL can reach past.

**The consumer contract is a Spring Batch `ItemReader<OccurrenceId>`** (concretely, `JdbcCursorItemReader` or `JdbcPagingItemReader`, built inside `ledger` from the survivors SQL), never a materialized `List` of what could be a million-plus ids. `pipeline`'s job definition wires this reader into a step exactly as Spring Batch already does everywhere else (ADR-036) — no bespoke streaming or paging mechanism is introduced. A step reads a bounded chunk of occurrence ids from `ledger` and hands that chunk to its own capability module (e.g. `embedding.findByOccurrenceIds(chunk)`), which does one query scoped to those ids against its own tables. No stage needs a one-shot global join: every stage already processes chunk-by-chunk by construction (ADR-036's chunking, ADR-049's per-occurrence verdict rows), which is the same efficiency a view would have bought, achieved without a boundary-invisible seam.

## Consequences

**Narrows ADR-041's gap for this one query, does not close it in general.** The survivors query specifically is now fully covered by `ApplicationModules.verify()`. Any future capability module that hand-writes a cross-boundary SQL string elsewhere remains invisible to the boundary test — that residual gap is exactly what ADR-041 already recorded, and this decision does not claim to resolve it.

**`ledger` owns constructing the reader, not just answering a query once.** Its interface now includes a Spring Batch-shaped return type, so `ledger` takes on a light dependency on Spring Batch's reader interfaces — acceptable, since `pipeline`'s composition-root role already assumes every module speaks Spring Batch at the seam where jobs are wired together.

## Amends

None. This fixes the form of the query `docs/architecture.md` already named but didn't specify.
