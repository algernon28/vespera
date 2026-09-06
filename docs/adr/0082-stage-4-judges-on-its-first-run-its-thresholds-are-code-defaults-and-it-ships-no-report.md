# ADR-082 — Stage 4 judges on its first run; its thresholds are code defaults, and it ships no report

- **Date**: 2026-09-06
- **Status**: accepted
- **Amends**: none (settles the posture ADR-081 left open once it had fixed the numbers)

## Context

Stage 4 writes a blocking verdict, and no similarity distribution over this corpus has ever been measured. That is the shape of the situation ADR-070's tier-2 confidence floor was in, and stage 3 answered it with **observe before enforce**: ship the threshold unset, measure it, report it, let an operator calibrate ([ADR-075](0075-stage-3-writes-a-confidence-distribution-report-that-calibrates-tier-2.md)).

Three things make stage 4 a different case.

- **Tier 2 was a second tier on a gate that still worked.** With `degenerateOutputConfidenceFloor` unset, stage 2 still wrote `degenerate-output` for zero-alphanumeric text. A stage 4 with unset thresholds writes nothing at all.
- **The numbers are already decided, and not from corpus statistics.** [ADR-081](0081-minhash-retrieves-shingle-sets-judge-128-permutations-in-16-bands.md) fixes 0.80 for near-duplication and 0.95 for containment. Both were chosen for what they mean — how different two documents can be and still be the same document, and how much of a document must appear in another before the reader loses nothing by its removal. Nothing about this corpus informed either, and nothing about another corpus would change them.
- **An operator is already required to act.** [ADR-080](0080-the-boilerplate-floor-is-a-gate-applied-before-signatures-are-computed.md) makes the boilerplate floor a gate: stage 4 does not run at all until a person has read stage 3's measurements and set one.

And the repo already sorts numbers into two bins. `ShingleParameters` is documented as "a mechanism parameter, the same kind of call ADR-071 made for the timeout budget and the streak counts — a code default, not a profile key"; `CONTEXT.md` defines the profile as "the per-corpus record of every judgement the engine **cannot** make for itself."

## Decision

### Stage 4 judges on its first run

**There is no measure-first pass, no dry-run mode, and no second invocation required before verdicts are written.** Once the boilerplate gate is satisfied, a stage-4 run signs the survivors, retrieves candidates, scores them exactly, and writes `redundant-with`.

**The ledger is already the look-before-you-cut mechanism**, and a dry-run would be a second one doing its job worse. Nothing is deleted from disk; a verdict is an appended row carrying its reason; "survivors" is a query over occurrences with no blocking verdict, never a place documents were moved to; and retuning is a `DELETE` of one stage's rows plus a re-run. A separate preview pass would need its own storage, its own artifact, and an answer to what happens when the preview and the real run disagree — three new things to buy what querying the verdict table already gives.

### The thresholds are code defaults, not profile keys

**0.80 and 0.95 live in code, recorded by ADR-081, and are changed by a new ADR.** They are mechanism parameters in exactly `ShingleParameters`' sense: they encode what the system means by "redundant", and an operator who wants different numbers is changing what the product does, which is a decision worth recording rather than a knob worth turning.

**Why not a profile key.** The profile holds what the engine cannot decide for itself — where the seed set lives, what confidence is too low *for this corpus*, which shingles are boilerplate *in this corpus*. A similarity cut is not of that kind: no measurement of a corpus tells you whether two documents at 0.78 are the same document. It would also be the first key to ship carrying a value, and "authored by a person, never guessed at" is the property that would break.

**No new identity column is needed for them.** A changed constant is a changed implementation, so a run's `implementationVersion` already covers it (ADR-048, ADR-058): re-running after a threshold change mints a fresh stage-4 run rather than silently mixing verdicts from two definitions under one run id. This is unlike the boilerplate floor, which sits in a *signature's* identity because signatures are stored derived values (ADR-080); a threshold is applied at scoring time and stores nothing of its own.

### Stage 4 ships no report

**No distribution artifact, in ADR-075's shape or any other.** ADR-075 declined to pre-compute summaries nothing consumes, and with the thresholds fixed in code there is no threshold an operator can move and therefore no reader for a distribution of pair similarities. A report whose only honest caption is "here is a curve; you may not act on it" is the thing that decision refused to build.

**Validating the cuts against a real corpus is a probe, not a shipped feature.** If the first real run suggests 0.80 or 0.95 is wrong, that is measured outside the repository and recorded as an amendment to ADR-081 — the same "measure rather than argue, and put the measurement in the record" this slice has already used twice.

### What replaces the report: the verdict says what it measured

**Every `redundant-with` row records the score that produced it and which relation applied**, and the score is a value in the `similarity`-owned pointer table ADR-079 named, not text inside `verdict.reason`. Structured data parsed back out of a free-text field is the shape ADR-041 rejected and ADR-069 avoided; `reason` stays a human-readable sentence.

This is what an operator auditing a removal actually needs — not a corpus-wide curve, but the answer to "why did *this* document go", available by joining one row. It costs a column.

### Candidate pairs are transient

**Only the pairs that produced a verdict are stored, through that pointer table.** The candidate list LSH generates is an intermediate of retrieval — most of it exists to be rejected by exact scoring — and storing it would be the pipeline's largest table by far, in service of no reader. This closes the stage-4 map's open fog on the question: census's "store the measurement, do not throw it away once read" posture is about measurements, and a candidate pair is not one. The measurement stage 4 makes is the score behind each verdict, and that is kept.

## Consequences

**Stage 4 is the first stage that both gates and judges.** A fresh corpus's first invocation stops at the boilerplate gate; the second, after a person has set a floor, removes documents. There is no third step, and no second reason for the run to stop.

**The profile still has three keys.** Nothing here adds one, and `Profile` gains no new `withXMeasurement` method — there is no artifact for a `Measurement` to point at.

**[#70](https://github.com/algernon28/vespera/issues/70) inherits a smaller surface than the map anticipated**: no report, no HTML file, no profile change, no `pipeline` composition for the reporting half that no longer exists. What remains for the spec is the signature and pointer tables, the run wiring, and the step.

**If the cuts turn out to be wrong, the record moves, not a setting.** That is the intended cost: a corpus that needs 0.75 gets a new ADR explaining why, rather than a value someone changed in a profile and nobody can later account for.
