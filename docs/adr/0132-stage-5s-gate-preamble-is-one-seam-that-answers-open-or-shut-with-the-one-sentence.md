# ADR-132 — Stage 5's gate preamble is one seam, and it answers open or shut with the one sentence that explains it

- **Date**: 2026-09-19
- **Status**: accepted
- **Rests on**: [ADR-080](0080-the-boilerplate-floor-is-a-gate-applied-before-signatures-are-computed.md) (a gate is a value the pipeline requires and does not have), [ADR-064](0064-the-walk-instrument-generalizes-a-seed-folder-is-walked-too.md), [ADR-083](0083-the-seed-set-is-extracted-by-stage-5-and-an-unusable-seed-is-recorded-not-a-gate.md), [ADR-084](0084-the-embedding-model-is-a-profile-gate-and-a-vector-carries-its-whole-embedder-identity.md), [ADR-086](0086-seed-corpus-mismatch-is-measured-before-the-model-gate-and-reported-never-enforced.md), [ADR-092](0092-the-seed-side-of-the-mismatch-comparison-is-measured-by-seed-extraction-under-the-measurement-run.md) (the gates themselves, whose semantics this record does not touch).
- **Amends**: none. This is a shape decision over facts ADR-064, ADR-080, ADR-083, ADR-084, ADR-086 and ADR-092 already settled.

## Context

### The same preamble, written seven times

Stage 5's steps all open the same way. Before a tasklet does anything it asks whether an operator has named an embedding model (ADR-084), whether the seed folder was walked and finished and stage 4's own floor was answered (ADR-064, ADR-080, ADR-089), and whether any seed produced text (ADR-083). Seven tasklets ask it — `EmbeddingScoringTasklet`, `RelevanceScoringTasklet`, `RelevanceReportTasklet`, `RelevanceFloorTasklet`, `ClusteringTasklet`, `ArrangementTasklet` and `SeedCorpusComparisonTasklet` — and each one used to ask its three gates one call at a time and word the three shut reasons itself.

The reasons are the same three facts everywhere. The clause *"no seed folder is named, or stage 4's gate is shut, or the seed walk has not finished"* was written in seven compilation units, and *"no seed document produced any text"* in six. The differences between the seven lines were the step's own name and an optional sentence about what the step consequently did not do — not the reasons. Seven copies of a sentence is seven chances for one of them to say something the others do not, which is the failure mode `NextAction` and `RedundancyJobConfiguration.logGateClosed` each answer for their own one sentence.

### The gates behind them are shallow adapters, and that is the point

`EmbeddingModelGate`, `SeedGate` and `UsableSeedGate` are each a small adapter at a seam: load the profile, read one value, return `Optional` or `boolean`. `SeedGate` is the one with real logic — it reads stage 4's own gate first and resolves a finished walk of the named folder, swallowing a path that resolves to nothing (ADR-064) — and `UsableSeedGate` is the one with no stored source at all, carrying the answer in process because a run with no usable seed mints no rows to read it back from (ADR-083). Those adapters are not the duplication. The duplication is the preamble that composes them.

### What the shape decision has to leave alone

- **`SeedGate` chains `RedundancyGate`.** Stage 5's measurement run names stage 4's run upstream and `run_upstream.upstream_run_id` is a foreign key (ADR-089), so stage 4's floor being unset is not a fourth value to ask for; it is a condition of whether the seed walk can be read at all. `SeedGateTest` pins each condition directly.
- **`ArrangementGate` approves a named arrangement**, not a profile value. It refuses an approval that names two arrangements, which is a different kind of question and belongs to a different stage boundary (ADR-107).
- **`EmbeddingModelGateTest` and `SeedGateTest` cross the adapter seams directly.** The adapters keep their names, their signatures and their behaviour.

## Decision

### One module answers the preamble

A new `pipeline` class, `StageFiveGates`, is the seam every stage-5 tasklet crosses before it works. Its interface is three entry points and one value type:

- `modelSeedWalkUsable(subject, model, seeds, usable)` — the scoring-half steps (embedding scoring, relevance scoring, relevance floor, clustering, arrangement).
- `modelAndSeedWalk(subject, model, seeds)` — the labelling report, which does not ask the usable-seed question because a run with no usable seed produced no scores and the report gates on that itself (ADR-088).
- `seedWalkAndUsable(subject, seeds, usable)` — the seed/corpus comparison, which runs before the model gate by design and never names a model (ADR-086, ADR-092).

Each returns a `Preamble`: open, carrying the model name and the finished seed walk the step asked for; or shut, carrying the one sentence that explains which gate stopped it. The three reasons live in one private vocabulary, and the gates are asked in the ledger's fixed order — model, then seed walk, then usable seed — so a caller's argument order cannot change which reason an operator reads.

The tasklet then does one thing with the answer:

```java
StageFiveGates.Preamble preamble = StageFiveGates.modelSeedWalkUsable(
        "stage 5's scoring step", embeddingModelGate, seedGate, usableSeedGate);
if (!preamble.isOpen()) {
    LOG.info(preamble.shutSentence().orElseThrow());
    return RepeatStatus.FINISHED;
}
String modelName = preamble.modelName().orElseThrow();
SeedGate.SeedWalk seedWalk = preamble.seedWalk().orElseThrow();
```

### Why a stateless module rather than a bean

The tasklets already hold the three gate adapters by constructor injection, and the seven step shapes differ only in which adapters they hold. `StageFiveGates` holds no state of its own, so it is a plain class whose three entry points are given the adapters at the call. This keeps the adapters' own seams — the ones the gate tests cross — as the only seams in play, and it keeps the module out of the Spring context, where a new bean would have to be named in the same `@Import` list every sliced invocation test already carries. **Depth is a property of the interface, not of who constructs the implementation**; three static entry points are as deep as three bean methods and cost no wiring.

### What the module owns, and what the step keeps

**The module owns the reason.** Which gate shut, in what order the gates are asked, and every word of the sentence that explains it. That sentence begins with the step's own subject — *"stage 5's relevance-report step"*, *"the arrangement step"* — which the caller supplies because it is the step's identity and not a gate's fact. The assembly is one grammar in one place.

**The step keeps only its own subject and its own consequence.** The consequence clauses were never duplicated and were step-specific — *"No scoring run was minted"*, *"Nothing was clustered"* — so the module does not invent them. Where a line previously said more than the reason, the module's sentence is what the step now logs; the operator is told which gate is shut, and `NextAction`'s closing line already names the value to set.

## Consequences

**Two reasons have exactly one source among the seven tasklets.** *"no seed folder is named, or stage 4's gate is shut, or the seed walk has not finished"* and *"no seed document produced any text"* are no longer written at any of those call sites; a change to either sentence is a change to one enum constant. The seed-walk clause is still worded once outside this module, in `SeedExtractionItemWriter`, which is stage 5's first step and not one of the seven tasklets this record covers; that writer's own warning about an unusable seed is a different sentence (`"No seed document produced any text"`, capitalised, at WARN) and is untouched. Folding the writer into this module would mean reopening ADR-083's ordering, and is out of scope here.

**No gate changes semantics.** `SeedGate` still chains `RedundancyGate`; `ArrangementGate` is untouched; the model gate, the seed walk and the usable-seed flag are read exactly as before, in the same order, and a shut gate still ends its step with `RepeatStatus.FINISHED` having minted nothing (ADR-080). The relevance-floor step's combined check now names the first shut gate rather than listing all three causes, and the step that previously said *"has nothing to apply a threshold to"* now says *"is gated"* like its siblings — a wording change with no behavioural one.

**Every existing test passes unmodified.** The pinned operator substrings — `relevance-report step is gated`, `seed-extraction step is gated`, `no seed folder is named`, and the presence of an `is gated:` line among an unconfigured run's output — are preserved because the shared reason clause is preserved and each step still says `is gated:`. No test lives under `src/test` for this record; the seven invocation tests are the regression net.

**No new dependency, and ADR-046 is not invoked.** The module is plain Java over classes already in `pipeline`.

**This is a shape decision, and the deletion test is what defends it.** Delete `StageFiveGates` and the gate order, the three reason clauses and the sentence assembly reappear across seven tasklets; nothing else in the tree does their job. It earns its keep by that test, not by a count of lines removed.