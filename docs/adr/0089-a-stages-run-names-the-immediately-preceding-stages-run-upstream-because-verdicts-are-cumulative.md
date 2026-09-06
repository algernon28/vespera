# ADR-089 — A stage's run names the immediately preceding stage's run upstream, because verdicts are cumulative

- **Date**: 2026-09-06
- **Status**: accepted
- **Amends**: [ADR-086](0086-seed-corpus-mismatch-is-measured-before-the-model-gate-and-reported-never-enforced.md) — its measurement run's upstream is stage 4's run, not stage 2's

## Context

Found while writing the stage-5 hand-off spec ([#94](https://github.com/algernon28/vespera/issues/94) §13.1), which flagged it rather than assuming a fix, and raised as [#95](https://github.com/algernon28/vespera/issues/95). Nothing is broken today, because stage 5 has no code. This is a record defect being fixed before it becomes a data defect.

### What ADR-086 said, and why it does not hold

ADR-086 mints stage 5's measurement run "with stage 2's run upstream". The reasoning given is sound as far as it goes: every signal the seed/corpus comparison reads is an `extraction_metric` column, and those exist after stage 2.

But that decision also states — load-bearingly, and for a good reason — that **the corpus side of the comparison is survivors**, "because what matters is whether the seeds resemble what would actually be scored". And `Ledger.survivors(RunId)` uses the run **only to name the walk**. Its own javadoc states the rule:

> Verdicts are not filtered by run: a blocking verdict from any run removes an occurrence, which is what makes survival cumulative across the cascade rather than one stage's opinion.

So stage 4's `redundant-with` verdicts change what the mismatch report says, while appearing nowhere in the run's ancestry. **Two measurement runs carrying the same run id can describe different corpora** — one taken before stage 4 ran, one after. That is precisely what [ADR-048](0048-walk-and-run-identity.md) exists to prevent: a run id is derived from everything that determines what it would produce.

It propagates. The scoring run's upstream is the measurement run, so **stage 4's run id appears nowhere in stage 5's ancestry at all** — though scoring reads survivors, clustering runs over survivors ([ADR-087](0087-clusters-are-modularity-communities-over-a-k-nearest-neighbour-graph-built-in-blocks.md)), and `below-threshold` is written against the survivor set stage 4 produced.

### The existing practice was already right, and only the record broke it

Read off the code rather than assumed. Every run the pipeline mints names the **immediately preceding stage's** run:

| Stage | Upstream named | Writes verdicts |
|---|---|---|
| 1 — `byte-level-reduction` | none | yes |
| 2 — `extraction` | stage 1's run | yes |
| 3 — `content-census` | stage 2's run | **no** |
| 4 — `content-redundancy` | stage 3's run | yes |

Each therefore folds every earlier run inside it, because a run id already folds in the runs it was derived from (ADR-048). And the reads line up: `ConfidenceDistribution` and `DocumentFrequency` both call `ledger.survivors(stage2RunId)`, and stage 2 is the last verdict-writing run ahead of them. Stage 4 naming stage 3 still folds stage 2's identity in, even though stage 3 writes nothing.

**ADR-086 is the only place in the record that reaches back past a stage.** The defect is not that it named the wrong run; it is that it reasoned from *which tables the measurement reads* rather than from *what determines what it reads*.

## Decision

### The rule: a stage's run names the immediately preceding stage's run as its upstream

Stated as a rule about the cascade rather than as a fact about stage 5, because stage 6 will read survivors too and the reasoning should not have to be re-derived.

**The reason is verdict cumulativity, and it belongs in the rule rather than beside it.** Blocking verdicts are counted from every run, so *any* pass over survivors is determined by every verdict-writing run before it — whether or not it reads a single column that stage wrote. A measurement that touches only `extraction_metric` is still a measurement of whatever stage 4 left standing.

**Naming the immediate predecessor is sufficient and is what to write**, rather than enumerating every verdict-writing ancestor: ADR-048's folding makes the chain transitive, and a rule that says "name your predecessor" cannot be got wrong by someone counting which earlier stages happen to write verdicts. ADR-086 got it wrong by exactly that counting.

**A verdict-free stage stays in the chain.** Stage 3 writes nothing and is still stage 4's upstream, because its own outputs — `shingle_document_frequency`, `shingle_corpus_size` — determine what stage 4 produces. The rule is about position in the cascade, not about whether a stage judges.

### ADR-086's measurement run names stage 4's run

The scoring run continues to name the measurement run, so stage 4's identity now reaches both.

**Fixing the upstream rather than the read.** The alternative was to keep stage 2 upstream and measure over every extracted occurrence instead of survivors — defensible on its face, since the report is about *form* (language, length, born-digital against converted, OCR damage) and form is a property of the archive. It is rejected because deduplication is not form-neutral: the fuller rendering survives and the shorter copies go ([ADR-079](0079-redundant-with-covers-near-duplication-and-containment-the-fuller-rendering-survives.md)), so the pre-dedup archive has a different length and provenance profile from the set about to be scored. Measuring it would answer a question nobody is about to act on, and would require amending the one sentence of ADR-086 that its whole argument rests on.

### The onboarding cost is accepted and recorded

The mismatch report now sits behind stage 4's gate. An operator's path to a curated archive is five invocations:

1. run — walks, reduces, extracts, censuses, **stops at stage 4** ([ADR-080](0080-the-boilerplate-floor-is-a-gate-applied-before-signatures-are-computed.md))
2. set `boilerplateDocumentFrequencyFloor`, run — dedups, then measures the seed/corpus mismatch and **stops at the model gate** (ADR-086, [ADR-084](0084-the-embedding-model-is-a-profile-gate-and-a-vector-carries-its-whole-embedder-identity.md))
3. name a model, run — chunks, embeds, scores, clusters, writes the distribution and the labelling artifacts, **removes nothing** ([ADR-088](0088-the-relevance-threshold-is-read-off-a-stratified-sample-of-sixty-labels-and-an-unset-floor-does-not-stop-the-run.md))
4. label sixty documents, set `relevanceScoreFloor`, run — writes `below-threshold`
5. — and only now has a document been removed for irrelevance

**This adds no restriction that did not already exist.** Stage 5 is *already* unreachable while stage 4's gate is closed, because the invocation ends at stage 4's step; this decision makes an incidental fact structural. The expensive thing ADR-086 protects against is embedding, and that is still gated behind the model.

Carving the measurement run out so it could run ahead of stage 4 was considered and rejected: it reintroduces exactly this defect, since it would then be measuring over a survivor set stage 4 had not yet reduced.

## Consequences

**Stage 5's `configConsumed` names stage 4's run id**, and #94 §9 is implemented against this rather than against ADR-086's sentence. A corpus scored after a changed boilerplate floor is now correctly a different run, because that floor changes which documents survive to be scored.

**The rule is checkable, and should be checked.** "Every run row's upstream is the immediately preceding stage's run" is an assertion a test can make over the `run` and `run_upstream` tables after a full invocation, rather than a convention four stages happen to share. That is worth building when stage 5 lands, since it is the thing that would have caught this.

**Stage 6 inherits a rule rather than a precedent to interpret.** ADR-022's page tree consumes seed partitions and clusters, both of which are computed over survivors, so the same reasoning would have applied and the same mistake was available.

**ADR-086's timing argument survives intact.** Its point was that the mismatch report must arrive *before the model is named and the corpus is embedded*, so an operator does not pay to embed against seeds that were never comparable. It still does. What changed is that it also arrives after the boilerplate floor is set, which was already true of everything in stage 5.

**Five gated invocations is a real onboarding story that nothing has written down end-to-end.** Step 2 above is the first place it becomes visible, and this ADR is not the place to fix it. Worth its own ticket: whether the sequence should be documented, reported at each stop, or reduced.

**Nothing here changes any code that exists.** Stages 1 through 4 already satisfy the rule; ADR-086's run has never been minted. The amendment is to the record, ahead of the implementation that would otherwise have copied it.
