# ADR-160 — The relevance report asks both seed-usability questions before it reaches the scoring run

- **Date**: 2026-09-26
- **Status**: accepted
- **Amends**: [ADR-132](0132-stage-5s-gate-preamble-is-one-seam-that-answers-open-or-shut-with-the-one-sentence.md) — its second entry point, `modelAndSeedWalk`, for the labelling report, is withdrawn; and [ADR-155](0155-a-seed-file-that-will-not-open-is-recorded-under-a-reason-of-its-own-and-seed-extraction-records-no-completion-until-it-opens.md) §3, whose sentence *"The labelling page does not consult the usable-seed gate. With no scores under the run, it stays shut on its own reason"* stops being true, and was not true of what the step minted.
- **Keeps**: [ADR-080](0080-the-boilerplate-floor-is-a-gate-applied-before-signatures-are-computed.md) (a shut gate ends its step having minted nothing), [ADR-083](0083-the-seed-set-is-extracted-by-stage-5-and-an-unusable-seed-is-recorded-not-a-gate.md) (no usable seed, no run), [ADR-088](0088-the-relevance-threshold-is-read-off-a-stratified-sample-of-sixty-labels-and-an-unset-floor-does-not-stop-the-run.md) (the report's own gate on there being no scores to spread, which still applies once every preamble gate is open), and ADR-132's shared sentence vocabulary, which gains nothing.
- **Rests on**: two invocation tests run against `main` at `5cba9ef` before the change, and again after it. They ship with this record.
- **Settles** [#309](https://github.com/algernon28/vespera/issues/309).

## Context

ADR-132 gave stage 5's gate preamble three entry points. The labelling report took `modelAndSeedWalk`, which asks whether a model is named and whether the seed walk finished, and not whether any seed produced text. ADR-132 gave the reason: *"a run with no usable seed produced no scores and the report gates on that itself (ADR-088)."* ADR-155 §3 then left the report out of its new reason on the same ground.

The reasoning was right about what the step would *say* and wrong about what it would *do*. To learn that no survivor carried a score, `RelevanceReportTasklet` asked `RelevanceDistribution.measure` about the scoring run, so it first resolved `ScoringRun`. Resolving `ScoringRun` mints an `embedding-scoring` run, and its constructor resolves `SeedMeasurementRun`, which mints a `seed-measurement` run. Both are rows behind a gate every other stage 5 step reported shut.

Measured with the seed fixture the invocation tests share, a model named, stage 4's floor set, and a seed folder whose one seed converts with no text in it:

- The runs over the corpus walk were `byte-level-reduction, extraction, content-census, content-redundancy, seed-measurement, embedding-scoring`. The invocation exited 0.
- Seed extraction warned *"No seed document produced any text, so stage 5 minted no run"*. In the same invocation stage 5 minted two.
- The report then logged that it was gated because *"no survivor carries a relevance score under"* the scoring run it had just minted, naming a run the gate should have left unminted.

The same route opened at ADR-155's gate, which #309 suspected and did not measure. With a model named, one seed that opens and one whose file is moved away when seed extraction reads it, seed extraction rightly minted the measurement run and recorded no completion. Every scoring-half step shut on *"a seed file could not be opened"*. The report went on and minted an `embedding-scoring` run over a seed set with a hole in it, which is the state ADR-155 exists to keep out of the record.

**Why no test saw it.** `SeedExtractionInvocationTest.mintsNoRunWhenNoSeedIsUsable` pins "no run" with no model named, so the report shut on the model gate before it could reach the scoring run. `EmbeddingScoringInvocationTest` names a model for ADR-155's case, but it checks that embedding scoring is not recorded *finished*, not that no scoring run exists.

## Decision

**The relevance-report step consults `modelSeedWalkUsable`, like every other scoring-half step.** It asks, in ADR-132's fixed order, whether a model is named, whether the seed walk finished, whether any seed produced text, and whether a seed file could not be opened. Only when all four are answered open does it resolve the scoring run.

**`modelAndSeedWalk` stops being an entry point.** It stays as the private first half of `modelSeedWalkUsable`, so there are two call shapes where ADR-132 had three: the scoring half (the report among them) and the seed/corpus comparison.

**What the operator reads changes in exactly these two states.** With a model named and no seed producing text, the report logs *"stage 5's relevance-report step is gated: no seed document produced any text."* With a model named and a seed file that would not open, it logs *"stage 5's relevance-report step is gated: a seed file could not be opened."* Those are the sentences its sibling steps already log in the same invocation. The report's own *"no survivor carries a relevance score under …"* line is kept for the state it was written for: every preamble gate open, and still nothing scored.

**Why the gate and not the run.** The alternative is to make `SeedMeasurementRun` or `ScoringRun` refuse to be built while a seed-usability fact is shut. A bean that refuses throws, and a throw out of a tasklet fails the invocation. #141 is the record of that happening in this very step. It would turn a silent mint into a failed invocation in a state every other step treats as a gate and exits 0 on. The gate is where ADR-080's rule is kept today, one call site at a time, and this record closes the one call site that did not keep it. Whether the run holders should also check their inputs is a separate question about how runs are minted, not about which gates this step asks.

## Consequences

**With no usable seed, stage 5 mints no run, whether or not a model is named.** Seed extraction's warning is true again. With a seed file that would not open, stage 5 mints the measurement run seed extraction needs to record what it found, and nothing after it.

**No other stage 5 or stage 6 step has this shape.** Every other step that resolves a run does so after `modelSeedWalkUsable` (embedding scoring, relevance scoring, the relevance floor, clustering, the arrangement) or `seedWalkAndUsable` (the seed/corpus comparison). Generation never resolves the arrangement run: it asks only about the arrangement this invocation arrived at (ADR-154 §2). `ArrangementRun` resolves the scoring run, and is itself resolved only behind the arrangement step's own preamble.

**Every existing test passes unmodified.** The pinned substring `relevance-report step is gated` is still logged in every state that logged it before.

**What pins it**, in `RelevanceReportMintsNothingBehindTheUsableSeedGateTest`. Both tests name a model, so the model gate cannot be what shuts the step:

- A seed folder whose only seed produces no text. The invocation must succeed. The runs over the corpus walk must be exactly the four ahead of stage 5. No line may say the report was gated for want of scores under a scoring run, and the report must log the usable-seed sentence. Before this change the runs included `seed-measurement` and `embedding-scoring`.
- A seed folder with one seed that opens and one moved away when seed extraction reads it. The invocation must succeed. The runs over the corpus walk must be the four ahead of stage 5 and `seed-measurement`, and the report must log ADR-155's sentence. Before this change the runs included `embedding-scoring`.
