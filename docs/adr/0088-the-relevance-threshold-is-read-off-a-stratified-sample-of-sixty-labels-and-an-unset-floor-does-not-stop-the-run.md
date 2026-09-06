# ADR-088 — The relevance threshold is read off a stratified sample of sixty labels, and an unset floor does not stop the run

- **Date**: 2026-09-06
- **Status**: accepted
- **Amends**: [ADR-028](0028-relevance-threshold-human-labelling-gated-by-score-distribution.md) — its "sampled human labelling **at the candidate cut**" becomes a stratified sample across the observed score range, and its go/no-go becomes a report rather than an engine refusal

## Context

**This is the only place in the pipeline that asks a person to read documents.** That is what makes the cost a decision rather than a detail, and it is why the sample is bounded here rather than left to whoever implements it.

ADR-028 is a reconstituted record: one sentence survives — *"Go/no-go on distribution shape, then calibrate via sampled human labelling at the candidate cut."* It settles the mechanism and rejects two alternatives (cluster-level adjudication, leave-one-out seed scoring). It does not say how much labelling, what the person reads, where their answers are kept, or what happens when the model changes underneath them.

Two things in that sentence do not survive contact with the rest of the record, and both are amended below.

**"At the candidate cut" is circular.** The cut is what is being calibrated. A sample concentrated at a guessed cut can answer only *"is this cut right?"* — and when the guess is wrong by a lot, the operator spends the whole sitting and learns nothing about where the right cut is.

**A mechanical go/no-go on distribution shape would be an unmeasured threshold.** A cosine-similarity distribution over one archive is very often unimodal and tight even when the seed set is sound, and any separation statistic needs a cutoff nobody has measured — the guess **observe before enforce** exists to refuse, and the same argument [ADR-086](0086-seed-corpus-mismatch-is-measured-before-the-model-gate-and-reported-never-enforced.md) made two tickets ago against gating on language mix.

## Decision

### The engine states the distribution's shape; the operator makes the go/no-go

Stage 5's **scoring run** ([ADR-086](0086-seed-corpus-mismatch-is-measured-before-the-model-gate-and-reported-never-enforced.md)'s second run) writes a score-distribution report — quantiles of the score, its spread, the size of each seed partition, and how much of the corpus falls in each band — and **never refuses to proceed on the strength of it**.

The go/no-go is a human reading of that report, and it is recorded where this repository already records human judgement: as the **provenance** of the threshold value (ADR-031). ADR-028's gate is discharged by the threshold shipping unset, not by an engine that declines.

**What "no" means is named explicitly.** A distribution with no usable separation is not a threshold problem; it is a *seed set* problem. `CONTEXT.md` makes the seed set "the sole carrier of domain knowledge in the system", so it is the only thing that can be changed in response — a different seed folder and a re-score, rather than a cut chosen anyway because one was expected.

### The sample is sixty documents, stratified across the observed range

| | |
|---|---|
| **Bands** | 5, equal width over the **observed** score range — the minimum to the maximum of the scores actually present, never 0 to 1 |
| **Per band** | 12 documents, sampled uniformly at random within the band |
| **Total** | 60 |
| **Randomness** | deterministic, seeded from the run id; no RNG left to chance |
| **A short band** | contributes every document it has; the shortfall is **recorded, never backfilled** from a neighbour |

**Sixty, and why that number.** At roughly two minutes a document — open the extracted text, decide relevant or not — sixty is about two hours, the outer edge of one sitting. The ceiling is the point: an open-ended labelling task gets abandoned halfway, and what it leaves behind is a threshold calibrated from a partial pass with nothing recording that it was partial — a number that looks exactly as authoritative as a complete one. Twelve per band gives a proportion whose *direction* is legible; the report states how thin that is rather than hiding it behind a percentage.

**Equal width over the observed range, not over 0 to 1**, so the bands land where the scores actually are instead of mostly in empty space. **Bands rather than a cluster at one cut**, so the operator can *read the cut off* the labels — relevance proportion against score — instead of confirming or rejecting a guess. It costs the same two hours.

**Deterministic**, for ADR-029's and [ADR-087](0087-clusters-are-modularity-communities-over-a-k-nearest-neighbour-graph-built-in-blocks.md)'s reason one layer up: a calibration is meaningless against a sample that moves between runs, and a re-generated report that shows a different sixty documents than the one a person is halfway through reading is worse than useless.

**Backfilling is refused** because it would misreport which band a proportion describes — a band of three documents reported as three is a thin measurement; the same band padded to twelve from its neighbour is a wrong one.

### A label is a fact about a document, and no run owns it

**One `relevance_label` row per labelled occurrence, keyed by the occurrence and the seed set — never by `run_id`.** A label answers "is this document relevant to this seed set", which is true or false regardless of which model scored it or when.

The run id, the score the labeller was shown, and the **`embedder_identity`** ([ADR-084](0084-the-embedding-model-is-a-profile-gate-and-a-vector-carries-its-whole-embedder-identity.md)) are recorded **beside** the label as the context that was on screen, so a later reader can tell what the judgement was made against. They are not part of its key.

**[ADR-077](0077-a-regenerated-measurement-is-a-fresh-row-set-under-its-own-run-id.md)'s fresh-row-set rule deliberately does not apply here.** That rule exists for regenerated *measurements*, where a second computation is a second observation. A second copy of a human's answer is not a second observation; it is a duplicate. This is the first table in the system whose rows a re-run never rewrites, and it is stated as such so nobody later "fixes" it into consistency with the rest.

### An unset floor does not stop the run

[#83](https://github.com/algernon28/vespera/issues/83) proposed this as a third gate. It is not one, and the difference is the whole shape of the stage.

[ADR-080](0080-the-boilerplate-floor-is-a-gate-applied-before-signatures-are-computed.md)'s gate stops stage 4 because a boilerplate floor is an **input** stage 4 cannot work without. The relevance threshold is the opposite: **the run is what produces the data the threshold is calibrated from.** Gating on it would mean never producing the report that lets anyone set it.

So:

1. **A new `Profile` key, `relevanceScoreFloor`**, following the two existing floors' naming, shipping unset, with a `withRelevanceScoreFloorMeasurement` pointer at the labelling report — ADR-075's shape, and the third key to use it.
2. **Unset means stage 5 scores, clusters and reports, and writes no `below-threshold` verdict.** Nothing is removed. ADR-087 already assumed exactly this when it said that while the threshold is unset "nothing is removed and every scored document is clustered".
3. **Set under a *different* `embedder_identity` is treated identically to unset.** A threshold is a number on a scale, and changing the model changes the scale; applying the old number would write blocking verdicts — deletions — against a distribution it was never calibrated on. One rule, no new state, and the report says why the value is being ignored rather than silently ignoring it.

### What the operator reads, and how their answers get back

The scoring run writes two files beside the database and `profile.yaml` (ADR-054's placement rule — never inside the corpus):

- **One self-contained HTML file**: the distribution with the five bands marked, then the sixty sampled documents in band order, each showing its path, its score, its winning seed, and the opening of its extracted text, with a `file://` link to the original. Hand-assembled without a templating library, as `ConfidenceDistributionReport` already is (ADR-046). ADR-051 already fixes a path relative to the corpus root, so the link costs nothing — and it is what makes two minutes a document realistic rather than optimistic.
- **A YAML label file**, one entry per sampled document with the answer left blank. The same author-a-file-and-re-invoke loop as the profile, on the same Jackson YAML machinery ([ADR-061](0061-the-profile-is-yaml-typed-java-records-one-object-per-value.md)). It names the run id and `embedder_identity` it was generated under, and a file offered against a different sample is **refused rather than partially matched**.

Ingesting a completed label file writes `relevance_label` rows and mints no run, because labels are not run-scoped.

**An interactive prompt was the alternative and is out.** ADR-047's "the pipeline never blocks" is precisely the property that makes this loop resumable across days, which is what a two-hour task actually needs; a terminal that walks sixty documents is a session that has to be finished or lost.

### The engine does the arithmetic; it never writes the number

Given sixty labels, the report states per band: documents labelled, documents labelled relevant, the proportion. Then, for each band boundary as a candidate cut, the consequence **in documents rather than in statistics vocabulary**:

> *Cut here and 41,200 documents survive; of the labelled ones above the cut, 9 in 12 were relevant. 158,800 are discarded; of the labelled ones below it, 1 in 48 was relevant.*

That is the trade the operator is actually making, and the one thing they cannot work out in their head.

**It never writes the value into the profile.** Doing so would break the two rules the profile rests on — `CONTEXT.md`'s "authored by a person, never guessed at" and [ADR-062](0062-census-merges-new-profile-keys-and-never-touches-an-existing-value.md)'s census that never touches an existing value — and any automatic rule would need a target proportion that is itself an unmeasured threshold.

**And nothing checks that a threshold was ever labelled.** `relevanceScoreFloor` is an ordinary profile value and its `provenance` is free text, so a floor set from a guess is possible. What stands between it and the archive is the operator's own record of how they arrived at it, which is what provenance has been for since ADR-031. Stating it here so that the absence reads as a decision rather than an oversight.

### Hard negatives fall out; they are not a second mechanism

`CONTEXT.md` defines a hard negative as "a document near the decision boundary — plausibly relevant, actually not… cannot be supplied in advance", and `docs/architecture.md` says they are mined from the corpus after scoring. **The labelling pass is that mining.**

A hard negative is a `relevance_label` row marked irrelevant carrying a high score — a query, not a thing. No new table, no new step, and no `CONTEXT.md` edit: the existing entry already describes this.

**Stated as a limit**: nothing in stage 5 consumes them. Relevance is one-class and exemplar-based ([ADR-004](0004-relevance-defined-by-an-exemplar-seed-set.md), [ADR-007](0007-no-supplied-negative-example-set.md)) — there is no classifier to feed. They are evidence for the person choosing the cut, and evidence for a later revision of the seed set. Saying so keeps them from being trusted as a feedback loop that does not exist.

## Consequences

**A re-score under a new model re-calibrates for free.** Because labels are keyed to documents rather than runs, the existing sixty are re-read against the new scores and the bands recomputed with no second sitting — the operator loses the threshold value, which was a number on the old scale, and keeps every hour of judgement that produced it. This is the strongest argument for keeping labels out of the run scope, and it is the reason to resist any later change that attaches a `run_id` to them.

**The default invocation now reaches the end of stage 5 and removes nothing.** Out of the box, with a model named and a boilerplate floor set, `vespera run <root>` walks, reduces, extracts, censuses, dedups, scores, clusters, and writes two reports — and every scored document survives. That is **observe before enforce** applied to the last threshold in the cascade, and it means the first complete run of an archive is a measurement of it.

**ADR-028's two amendments are recorded rather than absorbed.** The sample is stratified rather than sited at a candidate cut, and the go/no-go is a report rather than a refusal. Both are narrowings of a one-sentence reconstituted record; the original file stays byte-for-byte alone, per ADR-077's precedent.

**Three thresholds now ship unset, and only one of them gates.** `boilerplateDocumentFrequencyFloor` stops stage 4 (ADR-080) because it is an input; `degenerateOutputConfidenceFloor` and `relevanceScoreFloor` do not, because the runs that read them are the runs that measure them. The distinction is not "how important is the threshold" but "does the stage need it to do its work at all", and it is worth having stated once with all three in view.

**Two hours of a person's time is now a designed cost with a number on it.** Whether sixty is the right number is the one thing here that cannot be checked before a real archive — like every other number in this map. What can be checked is the shape: a deterministic sample, a refused mismatch between label file and run, a label that survives a re-run, and a floor that removes nothing while unset.

**`CONTEXT.md` gains Relevance label.** The first thing in the system that is a person's recorded answer about one document — explicitly not a verdict and explicitly not run-scoped — and the distinction is exactly what this decision turns on.

**Nothing here fixes the tables or the wiring.** `relevance_label`'s columns, the two files' literal layout, the label file's schema, and how ingestion composes with the scoring run are the hand-off spec's ([#85](https://github.com/algernon28/vespera/issues/85)) — the same deferral every prior slice made.
