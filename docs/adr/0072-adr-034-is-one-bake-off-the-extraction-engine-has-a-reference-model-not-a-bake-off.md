# ADR-072 — ADR-034 is one bake-off; the extraction engine has a reference model, not a bake-off

- **Date**: 2026-09-03
- **Status**: accepted
- **Amends**: `docs/architecture.md` §2's tech-stack table (the `Extraction engine (bake-off reference)` row, which cited ADR-034 for a mechanism ADR-034 does not describe). Clarifies ADR-034's scope and its "abort verdicts" wording. Does not amend ADR-034's decision itself, and does not disturb ADR-012 or ADR-013, which it defers to.

## Context

`docs/architecture.md` §2 cites ADR-034 on two rows:

| Layer | Choice | Decided by |
| --- | --- | --- |
| Extraction engine (bake-off reference) | One hosted model (OpenAI) | ADR-034 |
| Embedding model | Not yet chosen — candidates …; chosen by bake-off | ADR-033, ADR-034 |

ADR-034's own title is "Embedding model chosen by bake-off, not argument," and its reconstituted summary describes exactly one mechanism, the embedding one: same sample and seeds, ADR-028's gate run under each candidate, abort verdicts confirmed against a larger model, chunking re-done per candidate (ADR-044). Nothing in it mentions extraction.

Stage 2 needs to cite something for its engine selection, so the ambiguity had to be resolved before the stage-2 hand-off spec could be written (issue #41, on the stage-2 map, issue #38).

**What the repo actually contains**, checked before deciding:

- **ADR-013 already chose the extraction engine, and explicitly not by bake-off**: "Chosen on friction; changeable without a new ADR." ADR-012 makes the serving runtime configuration rather than code, requires the extraction cache key to carry full extractor identity, and adds that "calibration must not cross engines."
- **The `Extraction engine (bake-off reference)` row is the only assertion of an extraction-engine bake-off anywhere in the repository.** `docs/decision-ledger.md` — the provenance witness for every reconstituted ADR — has no such row; no ADR describes one; no other section of `architecture.md` mentions one.
- The nearest supporting fact is ADR-046's pom line, "additions (Ollama starter alongside OpenAI)," and `pom.xml`'s `spring-ai-starter-model-openai`. That starter serves both chat and embedding, so it does not disclose which role the hosted model was meant to play.
- **ADR-034's "abort verdicts" are not verdicts.** ADR-057 fixes the verdict vocabulary at eight values, a closed enum, and none of them is an abort. A reader of ADR-034 arriving at stage 2 and hunting for an abort verdict finds nothing.
- `CONTEXT.md` defines neither "bake-off" nor any term for the larger model something is confirmed against — so one word was doing two jobs, which is how the two rows came to share a citation.

## Decision

### ADR-034 covers one mechanism, and it is embedding-model selection

ADR-034 is about choosing the embedding model and nothing else. There is no second, undescribed bake-off hiding inside it, and stage 2 does not apply its mechanism to extraction-engine candidates. **Stage 2 must not cite ADR-034 for engine selection.**

### There is no extraction-engine bake-off, and inventing one would contradict two recorded decisions

The extraction engine is already settled: ADR-013 chose Ollama on friction and said it may change without a new ADR; ADR-012 made it configuration. A selection tournament for something deliberately chosen by non-tournament means, and deliberately left cheap to change, would contradict both. **Stage 2's engine selection cites ADR-012 and ADR-013.** No bake-off is specified, run, or built for extraction.

### What the table row actually named is a reference model

The row is not pure noise. ADR-034's surviving clause — "abort verdicts must be confirmed against a larger model" — describes a **reference model**: a deliberately larger, hosted model that an unfavourable measurement is re-checked against before it is believed. The extraction row names that model's extraction incarnation (the hosted OpenAI engine `docling-serve` can be pointed at, which is why the starter is in the pom), and mis-cited the ADR whose *selection* mechanism it was never part of.

This ADR generalizes the clause into a rule of its own, so it can be cited outside the embedding bake-off: **an unfavourable measurement is confirmed against a larger model before it is acted on.** In the embedding bake-off that means an ADR-028 no-go; in extraction it means a run whose extraction quality looks bad enough to doubt the engine rather than the corpus.

### The reference model is out-of-band: one engine per run, compared across runs by a person

Stage 2 runs **exactly one** extraction engine per run — whichever is configured. There is no in-pipeline fallback: a document that comes back `extraction-failed` or `degenerate-output` under the configured engine is **not** re-extracted against the hosted model before its verdict is written.

Confirmation is instead a second run with the hosted engine configured, compared by a person. This needs no new machinery: ADR-012 already keys the extraction cache on full extractor identity, so the second run yields a cleanly comparable second set that cannot be confused with the first, and ADR-047 already makes iteration-between-runs the shape work takes here.

A per-document hosted fallback was considered and rejected on three counts. It would mix extractor identities inside one corpus, which is exactly what ADR-012's "calibration must not cross engines" exists to forbid. It would put a hosted call on the failure path, so the cost of a bad corpus would scale with how bad it is. And it would make a blocking verdict depend on which of two engines happened to answer, invisibly — a verdict has to be attributable to one extractor identity to be re-analyzable at all.

### ADR-034's "abort verdicts" means ADR-028's go/no-go, not a ledger verdict

For the avoidance of the trap above: the thing ADR-034 requires confirming is ADR-028's go/no-go outcome on the score distribution — a run-level outcome — not a verdict from ADR-057's closed enum. This is also why confirmation is a run-level act: the thing being confirmed was never per-occurrence.

### `docs/architecture.md` §2's row is corrected, and `CONTEXT.md` gains both terms

The extraction row is retitled and re-cited to ADR-012, ADR-013 and this ADR; the embedding row's `ADR-033, ADR-034` is left alone, being correct. `CONTEXT.md` gains a **Bake-off** entry (the embedding-model selection mechanism specifically, not a general word for comparing two models) and a **Reference model** entry (a role, not a fallback, and never invoked by the pipeline — the same hosted model may separately be a bake-off candidate, as ADR-033's candidate list has it, but its reference role is the confirmation, not the competition).

## Consequences

**Stage 2's hand-off spec inherits a negative and one citation.** It says stage 2 runs one configured engine, names no reference model, and cites ADR-012/ADR-013. Nothing new is built for stage 2 by this decision, which is why the item leaves the stage-2 map's "Not yet specified" list without adding scope.

**No Profile key and no CLI affordance for "confirm against the reference."** The engine is already configuration, so "run it again against the other engine" needs no new input; adding a flag would grow the surface ADR-047 deliberately sized at two commands, and adding a Profile key would misuse it — `CONTEXT.md` scopes the Profile to corpus judgements, and which engine to run is not one.

**The word "bake-off" now names one mechanism only.** Any future comparison of two extraction engines is a reference-model confirmation, not a bake-off, and must be written that way. This is the guard against the same two-rows-one-citation collision recurring.

**Extraction quality across engines stays a manual comparison with no recorded procedure.** This ADR says where the comparison happens (between runs, by a person) but not what a person should measure when they do it — that needs a real extraction run over real corpus data, which the stage-2 map excludes for the same reason it excludes throughput tuning. If a procedure is ever wanted, it is a new decision, not a gap in this one.

**One reconstitution artifact is now corrected rather than propagated.** `docs/decision-ledger.md` is left untouched: it is the provenance witness for what was digested, and it never claimed the extraction bake-off — the claim was `architecture.md`'s alone, and that is where the correction belongs.
