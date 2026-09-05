# ADR-077 — A regenerated measurement is a fresh row set under its own run id, not an overwritten table

- **Date**: 2026-09-05
- **Status**: accepted
- **Amends**: ADR-075 (its "Regenerated every run" clause, as it applies to the table; the HTML file is unchanged)

## Context

ADR-075 settled that stage 3's confidence-distribution report produces two outputs — a queryable table and a self-contained HTML file — and said of both: *"Both outputs — the table row and the HTML file — are overwritten on every stage-3 run, not written once and left."* Issue #59's acceptance criterion 7 restated it: *"Running stage 3 a second time overwrites both the table's rows and the HTML file."*

The implementation does not overwrite the table. It writes a fresh row set keyed by stage 3's own `run_id` and leaves earlier runs' rows in place, and its test asserts exactly that — *"the first run's own table rows are untouched — historical data under its own run_id, never rewritten by a later run's rows under a different run_id."*

So the code and the record disagreed, and a comment in `schema.sql` repeated the record's version while sitting directly above a bare `INSERT`. That comment is the reason this needs deciding rather than quietly leaving: a false comment is what a future reader trusts instead of reading the code.

**The sibling table had already settled the question, unnoticed.** `shingle_document_frequency` (ADR-074, landed in #58 the same week) uses the identical shape — `PRIMARY KEY (run_id, …)`, one row set per stage-3 run, nothing deleted — and its own schema comment explains why that is safe: `RunId.of` already folds the upstream stage-2 run id into stage 3's identity (ADR-048), so two different stage-2 runs never collide under one stage-3 run id. ADR-075 was written before that table existed and reached for "overwritten" as the plain-language opposite of "written once and left stale". It did not mean to overrule a sibling it had not seen.

## Decision

**For a stage-3 measurement table, "regenerated every run" means a fresh row set under this run's own `run_id`, never a rewrite or deletion of an earlier run's rows.** ADR-075's "Regenerated every run" clause is amended to this reading for `confidence_distribution`.

What ADR-075 was actually protecting against is unchanged and still holds: no row is ever stale. A reader asking "what did the distribution look like for the run the profile points at" gets an answer keyed to that exact run, which is a stronger guarantee than overwriting gives, not a weaker one.

**The HTML file is not covered by this amendment and is still overwritten in place.** The two outputs differ in kind: the file is what `ProfileValue.measurement.source` points at by path, and `refreshedAt` claims it is current, so a stale file behind a fresh timestamp would misrepresent the pointer — ADR-075's original reasoning, which stands untouched. A table row carries its own run id and makes no such claim about being the latest.

**Why not the other way — a `DELETE` before the insert.** It would satisfy the old wording literally while making this table the only stage-3 table that discards its history, and it would buy nothing: because each invocation mints a new walk, a re-run gets a distinct run id, so a delete keyed by `run_id` would never match anything, and a delete of the whole table would throw away measurements the profile's own pointers may still refer to.

## Consequences

**Issue #59's criterion 7 is amended to match**, and the `schema.sql` comment above `confidence_distribution` is corrected to describe per-run keying and to cite this record. The code and its test are unchanged — they were right.

**One rule now covers both stage-3 measurement tables.** `confidence_distribution` and `shingle_document_frequency` are the same shape for the same stated reason, rather than two tables that happen to look alike. A third stage-3 measurement table has a precedent to follow instead of a contradiction to resolve.

**This is a reading, not a new capability.** Nothing about what stage 3 computes, writes or points at changes; what changes is which of two documents a reader should believe. If retention ever becomes a real cost — a table growing across hundreds of runs — that is a pruning decision on its own terms, and it would apply to both tables at once.
