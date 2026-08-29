# ADR-056 — "Excludes nothing" is checked by reconciliation at finish

- **Date**: 2026-08-29
- **Status**: accepted

## Context

`CONTEXT.md` defines entry as "the denominator a walk is accountable for: every entry is either recorded as a file occurrence, recorded as a walk anomaly, or — a readable directory only — descended into, which is what makes 'excludes nothing' a claim anyone can check rather than assert." Nothing yet makes it checkable. `Walk.Outcome` already carries the identity the claim rests on — `entriesSeen == occurrences + anomalies + directoriesEntered - 1` — but `WalkRecorder.walk()` discards the `Outcome` entirely and returns only the `WalkId`; none of these counts are persisted, and nothing compares them against what actually landed in the ledger.

ADR-055 (ticket #5) made a walk resumable across multiple invocations under one `WalkId`, each contributing a partial `Outcome`. Whatever accumulates the identity's terms has to span every session a walk took, not just its last one.

## Decision

**The mechanism is a reconciliation query, not a trusted counter.** `entries_seen` and `directories_entered` — the two terms with nowhere else to live, since directories are neither occurrences nor anomalies — are persisted as columns on the `walk` row, incremented cumulatively at the same cadence as the resume checkpoint (ADR-055), so they reflect the whole walk's history across however many sessions it took. At finish, rather than trusting those same in-process counters as "the" occurrence and anomaly counts, the check independently counts the actual rows: `SELECT COUNT(*) FROM file_occurrence WHERE walk_id = ?` and the equivalent over `walk_anomaly`, and verifies the identity holds against those counts. A miscount inside `Walk.Visitor` that both increments a counter and skips a write would pass a self-trusting check and fails this one, because the two sides come from independent sources — one from counting as the walk proceeds, one from counting what was actually persisted.

**The check runs automatically, inside `WalkRecorder`, the moment a walk reports `finished=true`.** Not a test, not a CLI diagnostic, not a startup assertion. "Excludes nothing" is a claim about one specific run over one specific archive, not a property of the code in the abstract — a test proves the mechanism works against fixtures, but only running the check against the real corpus, every time, proves it held for *that* archive. A resumed-but-still-unfinished walk is never checked; its counts are provisional by construction.

**A failed reconciliation is a hard failure**, not a recorded discrepancy. The invocation aborts rather than leaving a `finished` walk whose own accounting is known to be inconsistent. A `finished` walk with a silent accounting hole is strictly worse than an explicitly unfinished one: nothing downstream would know to distrust it, and a run would query "survivors" against it as though it had seen the whole archive.

## Consequences

**The `walk` table needs two more columns**, on top of the `finished` flag and checkpoint ADR-055 already requires: cumulative `entries_seen` and `directories_entered`. Belongs to the hand-off spec ([#18](https://github.com/algernon28/vespera/issues/18)).

**`WalkRecorder.walk()` must stop discarding `Outcome`.** It needs to fold each session's `Outcome` into the walk row's cumulative counts, and run the reconciliation query as its last step before returning, once `finished=true`. Also for the hand-off spec.

**A reconciliation failure has no recovery path defined here.** It signals a bug in `Walk` or `WalkRecorder` itself, not an operator-actionable condition — there is nothing for a human to fix in the corpus. Out of scope for this decision, which only fixes that the invocation must not proceed past it.

## Amends

None. This supplies the checking mechanism `CONTEXT.md`'s "entry" definition presupposes but never specified; no prior ADR claimed to have already decided it.
