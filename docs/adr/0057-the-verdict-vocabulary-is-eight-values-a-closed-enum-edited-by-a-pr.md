# ADR-057 — The verdict vocabulary is eight values, a closed enum edited by a PR

- **Date**: 2026-08-29
- **Status**: accepted

## Context

ADR-042 gives `ledger` a fixed, closed verdict vocabulary with blocking-ness per value, exposed through `survivors(runId)`, and rejects runtime or opaque verdict registries because their failure mode is asymmetric — drift toward over-publishing. The reconstituted ADR-017 names the values only as a byproduct of its stage table (`docs/architecture.md` §1.2): `broken`, `duplicate-of`, `superseded-by` (stage 1); `extraction-failed`, `degenerate-output` (stage 2); `redundant-with` (stage 4); `below-threshold` (stage 5). ADR-049 adds `passed`, written by every stage. None of this was ever assembled into one enumeration with blocking-ness and payload decided, and the ticket named a real tension: a closed vocabulary owned by `ledger` means adding a stage means editing `ledger`.

## Decision

**The vocabulary is these eight values, and nothing else, until a future ADR amends this one:**

| Value | Stage | Blocks | References |
| --- | --- | --- | --- |
| `broken` | 1 | yes | — |
| `duplicate-of` | 1 | yes | another occurrence (byte-identical, representative already chosen) |
| `superseded-by` | 1 | yes | another occurrence (byte-identical, newer by mtime, ahead of any representative-selection rule) |
| `extraction-failed` | 2 | yes | — |
| `degenerate-output` | 2 | yes | — |
| `redundant-with` | 4 | yes | another occurrence (lexical near-duplicate) |
| `below-threshold` | 5 | yes | a relevance score and a winning seed |
| `passed` | every stage | no | — |

**Blocking-ness follows mechanically from `CONTEXT.md`'s definition of survivor** ("a file occurrence carrying no blocking verdict"): every value blocks except `passed`, which exists purely so the resume predicate can distinguish "examined, fine" from "not yet examined" (ADR-049). No value blocks conditionally — a verdict that sometimes blocks and sometimes doesn't is two kinds sharing one name, which this vocabulary does not have.

**`ledger`'s verdict row stays generic regardless of kind**: `occurrence_id`, `run_id`, `kind`, a free-text `reason` — the same shape ADR-053 used for `walk_anomaly`'s `detail`, never parsed, there for an operator to read. The occurrence-reference (`duplicate-of`, `superseded-by`, `redundant-with`) and the score-plus-seed (`below-threshold`) are not columns on this row. Per ADR-041 (`ledger` owns identity and verdicts; capabilities own their own tables), those payloads belong to the capability that computed them — `similarity` for the two duplicate/redundancy references, `embedding` for the relevance score and winning seed — in tables of their own, joined back to the verdict row by occurrence and run. This keeps the verdict table's schema identical no matter which stage wrote the row, which is the actual point of "one fixed vocabulary": one schema, not one per kind.

**`duplicate-of` and `superseded-by` are distinct because they resolve at different points.** `duplicate-of` marks either byte-identical copy once a representative has been chosen — the general rule is a still-open decision (`docs/architecture.md`'s "Not yet specified," the stage-1 slice). `superseded-by` is stage 1's own cheap, mtime-based call: a byte-identical copy that is older than another, decidable before any representative-selection rule exists at all. Stage 1 needs something to write for exact duplicates without waiting on a decision this map has not yet reached.

**Adding a stage's verdicts means editing this enum, and that is the accepted cost, not a gap.** ADR-042 rejected a *runtime or opaque* registry — a mechanism letting a stage introduce a kind/blocking-ness pair without review. A human editing a closed, source-controlled enum in a PR is a different thing: it is reviewed, versioned, and closed at every commit, exactly the shape `WalkAnomalyKind` already takes (ADR-053). A seam that let a new stage register a verdict without touching `ledger`'s enum would reintroduce the opaque registry ADR-042 rejected. No seam is designed; none is needed.

## Consequences

**`ledger`'s verdict table can be built now, complete, even though this slice implements no stage past census.** The schema does not grow when stage 1 or stage 5 is eventually built — only rows of an already-known kind get written.

**A future stage that needs a verdict kind not in this list requires a new ADR amending this one**, not a code-level extension point. That ADR edits the enum directly; it does not add a registry.

**`similarity` and `embedding` gain schema obligations before either module has a line of production code** — a table for duplicate/redundancy references and a table for relevance scores plus winning seeds. Neither is built in this slice; both are for the hand-off spec's account of what exists versus what is merely decided.

## Amends

None. This assembles and completes what ADR-017, ADR-042, and ADR-049 left as separate, partial pieces; it does not change any of their substance.
