# ADR-069 — A duplicate set resolves by earliest creation time, then path

- **Date**: 2026-09-02
- **Status**: accepted
- **Amends**: none (extends census's capture; no prior ADR fixed the column set `Walk.java`/`WalkRecorder` records)

## Context

Within a content-identity group (ADR-067: same size, same SHA-256), stage 1 must pick one occurrence as the **representative occurrence** (`CONTEXT.md`: "the single copy chosen for publication") and verdict every other member `superseded-by`.

The first candidate signal was `last_modified` — the only timestamp census currently captures (`Walk.java` reads `BasicFileAttributes.lastModifiedTime()`). It turns out to be a poor signal for "which copy is more original": mtime records last-*write* time, not when a copy came into existence, and for occurrences that are already byte-identical, a mtime difference mostly reflects which copy tool touched the file last and how — a naive copy stamps copy-time, a `-preserve-timestamps` copy carries the source's mtime forward, an archive extraction often sets mtime from the archive's stored value. None of that reliably tracks "original vs. duplicate."

NTFS separately tracks creation time, and Java's `BasicFileAttributes` already exposes it via `.creationTime()` on the same object `Walk.java` reads `lastModifiedTime()` from — the JDK read is already in hand, only the column and the plumbing to store it are missing. Creation time records when a given path first came to hold this content, which is a more direct answer to "which copy appeared first" than a write timestamp that can be reset by unrelated tooling.

## Decision

**The representative is the occurrence with the earliest `creation_time` in its content-identity group. Ties break on lexicographically-lowest `path`. `last_modified` plays no role in this rule.** Mtime was deliberately dropped from the tie-break chain rather than kept as a secondary signal — the same reliability problem that disqualifies it as the primary signal (it reflects tooling behavior, not content history) disqualifies it as a tie-break too; a signal not trustworthy enough to lead is not trustworthy enough to break ties either. Path is deterministic, already the human-facing identity (ADR-051), and needs no new capture.

**`file_occurrence` gains a `creation_time` column, captured alongside the existing `size`/`last_modified` read** — `Walk.java` already holds the `BasicFileAttributes` instance `lastModifiedTime()` is read from; `creationTime()` is the same call on the same object. `WalkRecorder`, `Ledger.fileOccurrence`, and `schema.sql` extend accordingly. This amends already-implemented census (stage 0) code, not just stage 1's own tables — acceptable scope for this map, the same way ADR-066 amended a census decision after the census map's destination had already been reached. No backfill concern exists yet: no real corpus has been walked, only test fixtures.

**One rule, not two.** There is no separate "compute the representative" step and "assign `superseded-by`" step that could disagree — the representative is defined as the one occurrence in a content-identity group that never receives `superseded-by`; every other member of the group gets `superseded-by` pointing at it, in the same pass.

**The `superseded-by` pointer is stored in a `corpus`-owned table**, not encoded into `verdict.reason`. `reason` is free text for a human-readable explanation (ADR-057), not a parseable reference — encoding a real foreign key there is the implicit-structure shape ADR-041 already rejected in favor of typed side tables. This follows the same shape as ADR-067's content-identity table: `corpus` ends up with a small, related pair of tables, both joined back to `verdict`/`file_occurrence` by `occurrence_id` and `run_id`.

## Consequences

**Census's capture grows by one column**, requiring a small, mechanical change to already-shipped code (`Walk.java`, `WalkRecorder`, `Ledger.fileOccurrence`, `schema.sql`) — the exact column type and plumbing are left to the hand-off spec ([issue #32](https://github.com/algernon28/vespera/issues/32)), consistent with how ADR-067 already deferred table-shape detail there.

**`last_modified` remains captured but now serves no role in stage 1's duplicate-resolution rule.** It stays in `file_occurrence` for whatever future stage or diagnostic use it may have; this ADR only removes it from this one decision.

**The `superseded_by` table's exact column layout is an implementation detail** for the hand-off spec, same deferral as ADR-067.
