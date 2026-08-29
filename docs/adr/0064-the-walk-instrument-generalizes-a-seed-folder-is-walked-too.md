# ADR-064 — The walk instrument generalizes; a seed folder is walked too

- **Date**: 2026-08-29
- **Status**: accepted

## Context

The standing open question from the lost `docs/frontier.md`, restated by `docs/architecture.md` §1.7: should the seed set be profiled with the same instrument used to measure the corpus, against the worry that a shape mismatch between seed documents and corpus documents (clean born-digital seeds against long scanned-OCR corpus documents, say) silently distorts ADR-020's relevance score by measuring format rather than topic.

The full mismatch-detection question belongs to stage 5 (relevance), which this map's destination (census: `ledger`, `corpus`, `profile`, a minimal `pipeline`/CLI) does not build. What *is* this slice's concern, and what the ticket itself named as the reason it belongs on this map: whether census's instrument must be reusable against a second, non-corpus input at all — a constraint on `corpus`'s design, not a relevance decision.

Two facts settle the reusability question outright:

- `CONTEXT.md`'s own definition of walk is already root-agnostic: "One observation of a filesystem, producing file occurrences. Carries the root it observed and whether it finished." Nothing in it says a walk is specifically of a corpus.
- The code, however, is named as if it were: `Walk.walk(Path corpusRoot, Observer)`, `WalkRecorder.walk(Path corpusRoot)`, and `schema.sql`'s `walk.corpus_root` column all name the parameter after the corpus specifically, though none of the walking logic depends on the root being the corpus.

The mismatch signals the ticket actually worries about — length distribution, chunk count, OCR-error indicators, language mix — are stage 2/3 (extraction, content census) outputs. Stage 0's walk produces only byte-level facts: path, size, mtime. Detecting a genuine format-vs-topic mismatch is not buildable with this slice's instrument alone, regardless of what this decision settles.

## Decision

**The walk instrument generalizes.** `Walk`'s and `WalkRecorder`'s `corpusRoot` parameters, and `schema.sql`'s `walk.corpus_root` column, are renamed to `root` — the vocabulary was already root-agnostic; only the naming pretended a walk is corpus-specific. ADR-055's resumability and ADR-056's reconciliation apply to any walk, including a seed walk, with no separate logic: both are keyed by root and by `WalkId`, not by what the root represents.

**The seed folder's path is a profile key, not a second CLI argument.** ADR-054 already fixed `vespera run <root>` to one positional argument; a seed folder is exactly the kind of operator-supplied value `CONTEXT.md`'s Seed set and ADR-043's profile already exist for. No CLI surface changes.

**Census walks the seed folder independently of the corpus walk, on any invocation where the profile's seed-path key is set and no finished walk exists yet for that root** — mint-or-resume, exactly ADR-055's rule, unmodified. The two walks do not block or gate each other: the corpus walk is the expensive one, the seed walk is not, and nothing links their progress.

**No "purpose" tag is added to a walk row.** A consumer of a `WalkId` — a stage-1 run chained to the corpus's walk, or a future stage-5 scorer looking for the seed's walk — already knows which root it's asking about, from the corpus root the CLI was given or from the profile's seed-path value. Identity-by-root (the pattern ADR-059 already uses for `schema_version`) is enough.

**The mismatch-detection question itself is explicitly deferred, not silently dropped.** What signal exposes a format-vs-topic mismatch, and what a mismatch does — gate, warning, or diagnostic — cannot be decided now: the signals in question (OCR-error rate, chunk count, language mix) don't exist until stage 2/3 are built. This is parked for whichever future ticket specifies stage 5, with the reason recorded here rather than left as a silent gap.

## Consequences

**Two renames are owed to the hand-off spec**: `Walk`/`WalkRecorder`'s `corpusRoot` parameter and `schema.sql`'s `corpus_root` column, both to `root`. Mechanical, no behavior change.

**A corpus's working directory (ADR-054) now holds walks for more than one root.** Nothing about ADR-054's layout changes — the database already lives in a working directory keyed to the corpus, and a seed walk is just another `WalkId` row inside it, distinguished by its own `root` value.

**`docs/architecture.md` §1.7's callout of this ticket as "the one standing design question" is now stale** and is updated as part of closing this ticket.

## Amends

None. This resolves the standing open question `docs/frontier.md` left behind, to the extent this slice's instrument can settle it; the relevance-scoring half of the original worry is untouched and awaits stage 5.
