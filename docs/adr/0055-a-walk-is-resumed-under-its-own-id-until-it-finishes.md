# ADR-055 — A walk is resumed under its own id until it finishes

- **Date**: 2026-08-29
- **Status**: accepted

## Context

ADR-048 gave walks a completion status but no rule for what happens next: is an unfinished walk over the same root resumed, or discarded and re-walked from scratch? `Walk.walk()` already implements a single-shot `Files.walkFileTree` call with no per-entry checkpoint, and `WalkRecorder.walk()` currently mints a fresh `WalkId` on every call — so the code, as it stands, already answers the question by default (always discard, always re-walk), without that ever having been decided.

The corpus in view is hundreds of gigabytes across an unknown number of files (`AGENTS.md`). Discarding an interrupted walk and re-walking from the beginning is only free if a walk is cheap — and a walk that must re-stat every entry under the root, even a root that never changed, is not obviously cheap at that scale.

Two facts settle whether resuming is even soundly possible:

- **ADR-016**: the corpus is treated as static. A re-walk happens because code changed, not because content changed — so the tree an interrupted walk left behind is the same tree its resume will see.
- **Measured on this machine** (Windows 11, NTFS, OpenJDK 26): `Files.walkFileTree` over an unchanged 1,055-entry tree (files interleaved with nested directories) produced byte-identical pre-order traversal order across three calls in one process and across two separate JVM processes. Zero divergence. Pre-order DFS traversal of an unchanged NTFS directory is deterministic and reproducible, not merely conventionally so.

Those two facts together mean a resumed walk can trust that "the same tree, walked the same way, visits things in the same order" — which is exactly what resuming needs and what an unordered filesystem traversal would otherwise rule out.

## Decision

**A walk is resumable, not atomic.** An unfinished walk over a root is continued under its own `WalkId` on the next invocation; it is never discarded and silently replaced.

**Minting rule**: `WalkRecorder` mints a new `WalkId` only when no unfinished walk exists for the root — because the previous walk over that root finished, or none exists yet. An unfinished walk is always resumed, never abandoned in favour of a fresh one. This also gives a deliberate re-walk after a code change (ADR-016) a new id automatically: by the time an operator re-invokes for that reason, the prior walk has already finished.

**Checkpointing is at directory granularity, not per-entry.** A per-entry checkpoint that only skips already-written database rows would still re-stat every file under the root on resume — the traversal cost, not the write cost, is what makes redoing a walk expensive at this scale. Because traversal order is proven stable, a resumed walk can instead `SKIP_SUBTREE` on any directory it fully completed last time, skipping the re-stat cost for everything beneath it. The exact checkpoint encoding (a path of per-level ordinal positions through the directory tree, matched against the same deterministic order on resume) is left to the hand-off spec ([#18](https://github.com/algernon28/vespera/issues/18)) — this decision fixes the mechanism's shape, not its data structure.

**A mismatch between the checkpoint and what resume actually finds fails loudly.** If the tree resume encounters does not match what the checkpoint expects, the walk raises rather than silently producing a result that looks complete but is not — the same convention `Walk` and `Ledger.startWalk` already follow.

**An unfinished walk is never eligible input for a run**, resumable or not. A run looks for a *finished* walk over the root; finding none behaves like any other missing gate (ADR-047) — it terminates rather than proceeding against a fraction of the archive.

## Consequences

**Code changing while a walk is interrupted is not guarded against.** Resuming assumes the walk's logic is the same as when the walk started. If code changes between an interruption and its resume, the two halves of one `WalkId` were produced by different logic, and nothing detects that. Documented assumption, not designed around — matching how ADR-016 already treats the corpus's staticness as an explicit assumption rather than a discovered property.

**The walk table needs a persisted completion flag and a checkpoint column**, neither of which exists yet — `schema.sql`'s `walk` table currently carries only `id` and `corpus_root`. Belongs to the hand-off spec, not this decision.

**`WalkRecorder.walk()`'s current always-mint behaviour is now known-incorrect**, not merely incomplete — it must query the ledger for an existing unfinished walk over the root before deciding whether to mint. Also for the hand-off spec.

## Amends

Narrows **ADR-048**, which established the two identities and said a continuation-vs-minting rule was defined but did not record what it was. Does not amend **ADR-016**: the staticness assumption is unchanged, only relied upon more heavily (an interrupted walk's resume, not only a deliberate re-walk, now depends on it).
