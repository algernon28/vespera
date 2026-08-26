# ADR-050 — The pipeline has exclusive access to the corpus

- **Date**: 2026-08-26
- **Status**: accepted

## Context

ADR-016 treats the corpus as static, as an explicit assumption rather than a discovered property, and says re-walks happen because code changed rather than because content changed. It never says what makes it static.

Deciding how a file occurrence's path identifies it ([#4](https://github.com/algernon28/vespera/issues/4)) forced the question, because a pile of defensive machinery was waiting on the answer: retry on sharing violations, re-stat before use, tolerate a file that vanishes between being listed and being read. Each one costs complexity in the walk, the single most expensive traversal in the system, and each one is pure waste if nothing else is touching the corpus.

## Decision

For the duration of a run, the pipeline has exclusive access to the corpus. Nothing else reads or writes it.

How the operator arranges that is outside this decision. What the engine relies on is the guarantee, not the mechanism.

Consequently the walk carries no concurrency defenses: no sharing-violation retries, no re-stat before use, no handling for a file that changes size or disappears mid-traversal. A file vanishing mid-walk and a file locked by another process remain recordable walk anomaly kinds — they cost nothing and a surprise there is worth seeing — but they are near-impossible rather than expected, and they do not shape the design.

## Consequences

**This is the premise ADR-016 was missing.** "The corpus is static" is not a property of archives in general; it holds because nothing else has access.

**Violating it degrades quietly, not loudly.** If something does write to the corpus during a run, the walk does not fail — it records occurrences whose `size` or `mtime` no longer match the file, and every downstream verdict is derived from stale facts. There is no detection for this, by construction. The guarantee is the operator's to keep.

**"Excludes nothing" is a claim about what the pipeline was pointed at**, and nothing more. The walk is accountable for every entry beneath the corpus root it was given; it cannot know whether that tree is the whole archive.

## Amends

Extends ADR-016 by supplying its missing premise. It does not reopen it.
