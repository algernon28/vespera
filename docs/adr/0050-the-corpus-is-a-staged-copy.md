# ADR-050 — The corpus is a staged copy, exclusively owned by the pipeline

- **Date**: 2026-08-26
- **Status**: accepted

## Context

The pipeline curates an unknown multi-format archive of hundreds of gigabytes. ADR-016 already treats the corpus as static, as an explicit assumption rather than a discovered property, and states that re-walks happen because code changed rather than because content changed.

What ADR-016 does not say is *why* the corpus can be assumed static, and that gap was answered while deciding how a file occurrence's path identifies it ([#4](https://github.com/algernon28/vespera/issues/4)). Several defensive mechanisms were on the table there — retrying on sharing violations, re-stat before use, tolerating a file that vanishes mid-walk, detecting hard links so one physical file is not counted twice — and every one of them costs complexity in the walk, which is the single most expensive traversal in the system.

The operator does not point the pipeline at a live archive. They **stage a copy** of it, and nothing else reads or writes that copy for the duration.

## Decision

The corpus is a staged copy, exclusively owned by this pipeline. From the pipeline's perspective it is the only corpus that exists, and no other process modifies it.

Consequently:

- **No concurrency defenses.** No sharing-violation retries, no re-stat before use, no handling for a file that changes or disappears between being listed and being read. A file vanishing mid-walk and a file locked by another process remain recordable walk anomaly kinds, but they are near-impossible rather than expected.
- **Hard links are treated as ordinary identical copies.** No attempt is made to detect them. They are undetectable at walk time on Windows in any case — `fileKey()` is `null` and the Win32 link count is not exposed by `java.nio` — and ordinary copy tools do not preserve them, so a staged corpus is unlikely to contain any. Two paths become two file occurrences, hash identically, and are related as one content identity by stage 1.
- **Soft links are not followed.** Symlinks, junctions and volume mount points are skipped and recorded as walk anomalies rather than traversed. A staged copy is unlikely to contain working ones.
- **Paths are stored relative to the corpus root**, so re-staging the copy to a different location does not invalidate the ledger.

## Consequences

**The walk measures the copy, not the archive.** This is the price of the assumption and it is not recoverable inside the pipeline. Copy tools silently skip what they cannot name or read: a filename containing an unpaired surrogate — valid on NTFS, demonstrated to exist, with no UTF-8 encoding — and any file pushed past a path-length ceiling because the tree sits one level deeper at the destination. If staging lost files, census reports success over a corpus that was already incomplete, and nothing downstream can tell.

Bounding that is cheap and belongs to the operator, not the engine: compare file count and total bytes between source and staged copy once, before trusting a run.

**Creation time carries no information.** `mtime` survives a copy at 100 ns precision; `creationTime` does not, so on a staged corpus it records the staging date. ADR-015's choice of `path/size/mtime` for occurrence identity is unaffected, having picked the one metadatum that survives staging.

**Re-staging mints a new walk**, not a continuation, because the corpus root differs and `mtime` values may have shifted. The rule for that lives in [#5](https://github.com/algernon28/vespera/issues/5).

## Amends

Extends ADR-016, which assumed a static corpus without saying what makes it static. It does not reopen it.
