# ADR-083 — The seed set is extracted by stage 5, and an unusable seed is recorded rather than gating

- **Date**: 2026-09-06
- **Status**: accepted
- **Amends**: none (fills the gap ADR-064 left: it settled that a seed folder is *walked*, and nothing settled that it is extracted)

## Context

[ADR-020](0020-relevance-scoring-function.md) scores a corpus document as the maximum, over **seed documents**, of the mean of its top-3 chunk similarities against that seed. Half of that comparison does not exist: [ADR-064](0064-the-walk-instrument-generalizes-a-seed-folder-is-walked-too.md) settled that census walks the seed folder and records its occurrences, but `ExtractionRun` resolves its walk with `finishedWalkFor(canonicalRoot)` — the corpus root — so **no seed document has ever been extracted, chunked or embedded**. [ADR-073](0073-stage-2-writes-the-derived-metric-columns-tokenizer-and-shingle-dependent-values-live-under-their-own-identity.md) recorded the precondition this has to satisfy: the comparison needs the seed set extracted with the same instrument as the corpus.

**A seed is not a corpus document, and the difference is what this decision turns on.** Every verdict in the closed vocabulary ([ADR-042](0042-ledger-owns-the-verdict-vocabulary-not-the-cascade.md)) exists to remove a document from publication. A seed is never published — it is the operator's statement of what relevance *means*, the sole carrier of domain knowledge in the system (`CONTEXT.md`). Removing one would silently redefine relevance for the whole archive.

## Decision

### Stage 5 extracts the seed set, reusing the corpus's instruments

**A stage-5-owned pass extracts, chunks and embeds the seed walk's occurrences**, rather than stage 2's step being pointed at a second root. Stage 2's step is bound to the corpus walk and exists to write corpus verdicts; giving it a second job would mean teaching it which of its outputs apply to which root.

**The instruments are reused exactly, and that is free rather than clever.** `extraction_cache` is keyed by content hash plus extractor identity, and `chunk_cache` by content hash plus chunker identity plus tokenizer identity (ADR-010, ADR-029, ADR-073). Both are content-addressed, so:

- ADR-073's "same instrument" precondition holds **by construction** rather than by discipline — a seed and a corpus document of identical content produce identical rows, because the key says nothing about which walk found them.
- A document that is both a seed and a member of the corpus is extracted **once**, and the second reader gets a cache hit.

### No verdict is ever written against a seed occurrence

The verdict vocabulary is closed and every kind in it is a reason to remove a document from publication. A seed is not a candidate for publication, so no kind applies, and inventing one would put a stage-2-shaped judgement on the object that defines the judgement.

**An unusable seed is recorded as data instead**: which occurrence, and why it produced no text. A seed is unusable on exactly the ground stage 2's tier 1 already uses — extraction produced no alphanumeric content at all (ADR-070). No stricter bar is applied to seeds than to corpus documents: a stricter one would need a confidence threshold nobody has measured, which is the guess ADR-070's "observe before enforce" exists to refuse. That a seed set is scanned while the corpus is digital text is a *comparison*, and reporting it belongs to the mismatch measurement ([#82](https://github.com/algernon28/vespera/issues/82)), not to a quality bar here.

### Scoring proceeds with the seeds that are usable

**An unreadable seed does not stop the run.** It is recorded, reported, and scoring goes ahead against the seeds that survive extraction.

This was argued the other way first, and the argument was wrong. `CONTEXT.md` defines a gate as "a value the pipeline requires and does not have"; an unreadable seed is not a missing value — the seed folder is set, the walk finished, the occurrences exist. It is a measurement about the seed set, and measuring rather than refusing is this pipeline's posture everywhere else (ADR-006). Stopping here would make stage 5 the only stage that halts over the *quality* of content rather than the *absence* of an input.

Two things make proceeding safe, and both already exist:

- **It is not silent.** The unusable seeds are recorded with their reasons, and the mismatch report (#82) exists to tell an operator when their seed set does not resemble what they are scoring against.
- **Run identity prevents the real hazard.** A run id is derived from everything that determines what it would produce (ADR-048), so a corrected seed folder is a *different run*. Scores computed against a partial seed set can never be mistaken for scores computed against a complete one — the ledger distinguishes them by construction.

### The one gate: no usable seed at all

**When no seed document produces any text, stage 5 does not run**: the invocation ends there, the job succeeds, and no run is minted — the shape [ADR-080](0080-the-boilerplate-floor-is-a-gate-applied-before-signatures-are-computed.md) established.

This is a gate for the right reason and not a judgement about quality. ADR-020's function is a maximum over the seed set; over an empty set it is undefined. There is nothing to compute, which is precisely "a value the pipeline requires and does not have".

## Consequences

**Stage 5 owns a pass that is not about the corpus.** Its run reads the seed walk, and its `configConsumed` therefore names the seed folder as well as the corpus root — a corpus scored against a changed seed set is a different run, which is the property the whole decision above leans on.

**A junk file in a seed folder costs an extraction call and is recorded as an unusable seed.** `BrokenCheck` returns `ok()` for any extension it does not recognise, so `Thumbs.db` and `desktop.ini` reach Docling like anything else. That is worth noticing but is **not a seed problem**: the same junk in the corpus costs the same call at the same stage today, which sits badly with ADR-017's cheapest-filter-first ordering. Whether stage 1's `broken` check should gain a format floor is an ADR-068 question and belongs to that map, not to this one.

**An operator who mistypes the seed path gets scores rather than a refusal.** Pointing at a parent directory produces hundreds of "seeds", most unusable, the rest meaningless — and the answer is that the mismatch report (#82) is built to say so, the compute is recoverable, and the corrected run is a new run. Refusing would require the engine to judge whether a seed set is *sensible*, which is the one judgement the operator exists to make.

**Nothing here fixes the tables**: where an unusable seed is recorded, whether seed chunks live beside corpus chunks or apart, and what the pass's step looks like are the hand-off spec's ([#85](https://github.com/algernon28/vespera/issues/85)), the same deferral every prior slice made.
