# ADR-117 — The relevance floor joins the scoring run's identity, so a changed threshold is a different run

- **Date**: 2026-09-14
- **Status**: accepted
- **Amends**: none. It applies [ADR-048](0048-walk-and-run-identity.md) where the code does not apply it, and makes true a consequence [ADR-107](0107-the-arrangement-gate-approves-a-named-6a-run-and-the-path-becomes-five-invocations.md) already states as fact. [ADR-088](0088-the-relevance-threshold-is-read-off-a-stratified-sample-of-sixty-labels-and-an-unset-floor-does-not-stop-the-run.md) decided the threshold and deferred the wiring to a hand-off spec; this is the part of that wiring nobody wrote down.
- **Rests on**: [ADR-087](0087-clusters-are-modularity-communities-over-a-k-nearest-neighbour-graph-built-in-blocks.md) (a below-threshold document is never clustered, which is why a changed floor changes more than a verdict), [ADR-085](0085-vectors-live-in-sqlite-and-the-pairwise-matrix-is-never-materialised.md) and [ADR-070](0070-extraction-failed-splits-on-doclings-status-degenerate-output-is-a-two-tier-floor.md) (the caches that make a re-scored run cheap), [ADR-115](0115-a-re-walk-that-observed-nothing-new-is-discarded-and-a-run-is-continued-under-its-own-id.md) (which turns this from untrue into harmful).
- **This record is a defect that predates ADR-115**, and is filed as one. It has been in the tree since the relevance floor landed, it is reachable on `main` today, and #191 neither caused it nor needs it — but #191 is what makes it bite, so the two land together.

## Context

### Measured: one profile key is named by no run identity at all

The profile carries seven keys. Six of them are folded into the identity of the run whose steps read them, which is what [ADR-048](0048-walk-and-run-identity.md) requires of a content-derived id:

| Profile key | Named in |
|---|---|
| `seedFolder` | `SeedMeasurementRun.configConsumed` |
| `degenerateOutputConfidenceFloor` | `ExtractionRun.configConsumed` |
| `boilerplateDocumentFrequencyFloor` | `RedundancyRun.configConsumed` |
| `embeddingModel` | `ScoringRun.configConsumed` |
| `arrangementApproved` | `GenerationRun.configConsumed`, as the arrangement run id it names |
| `generationModel` | `GenerationRun.configConsumed` |
| **`relevanceScoreFloor`** | **nothing** |

`ScoringRun.configConsumed` is the corpus root, the embedding model and the upstream measurement run id. The step that reads the floor — `relevance-floor` — writes its `below-threshold` verdicts under that very run. So two invocations whose profiles differ in the threshold, and in nothing else, derive a byte-identical scoring run id.

`RedundancyRun` shows what the same situation looks like when it was noticed: its `configConsumed` carries the boilerplate floor, and its javadoc says why — *"a run under a different floor must be a different run, since the floor sits in every signature's identity and changes what every signature this run writes actually means."* Every word of that is true of the relevance floor, one stage along.

### The record already claims this, and the claim is false

ADR-107 prices the cost of its gate in exactly these terms:

> a new embedding model, **a changed relevance floor** or a re-walk all mint a new 6a run id. That is the intended cost.

`ArrangementRun.configConsumed` is the corpus root and the upstream scoring run id. The scoring run id does not move when the floor moves, so neither does the arrangement's. A record states a property the code does not have, which is the drift [ADR-077](0077-a-regenerated-measurement-is-a-fresh-row-set-under-its-own-run-id.md) exists to stop.

### Why it is harmless today and serious tomorrow

Today every invocation mints a new walk, ADR-115 measured it, and a run id hashes the walk it read. So a re-invocation under a new threshold gets a new scoring run id — **for a reason that has nothing to do with the threshold**, and it re-does every step from scratch, so the new number is applied.

ADR-115 stops the walk churn and skips a step whose work is recorded. From that point the sequence is:

1. The operator reads the labelling page and writes `relevanceScoreFloor: 0.62` into the profile.
2. They invoke again.
3. The scoring run id is identical to the one that ran with no threshold at all.
4. Every step under it is recorded as done, so `relevance-floor` does nothing.
5. The invocation reports success. Nothing is removed. Nothing says so.

That is ADR-048's promise inverted in the direction that cannot be caught by inspection: not *two runs that would produce identical verdicts have different ids*, which merely wastes work, but **different inputs under one id**, which is a wrong answer recorded as a right one. It is also the one key whose whole purpose is to remove documents from the archive.

## Decision

### `relevanceScoreFloor` joins `ScoringRun.configConsumed`

The scoring run's identity becomes the corpus root, the embedding model, the upstream measurement run id, **and the relevance floor**. A corpus scored under a different threshold is a different run, on the same terms stage 4 already applies to its own floor.

### It is folded in as the value the step will act on, never as the text the operator typed

**Parsed, not verbatim.** `0.30` and `0.3` are one threshold and would produce one set of verdicts, so they are one run. ADR-048's rule is about what a run would *produce*, and a run id that moved on whitespace would mint a second run producing rows identical to the first's.

**Unreadable is unset, because that is what the step does with it.** `RelevanceFloor` already treats a value it cannot parse as unset, and `NextAction` already tells the operator their value reads as nonsense. The identity has to agree with the step: two profiles that both mean "no threshold applied" are one run, however differently they are misspelt.

**Not the derived applicability.** A threshold is only applied when it was calibrated on this run's own scale, and `RelevanceFloor.CalibratedElsewhere` removes nothing. That state is derived from the embedder identity carried by vectors this run's own steps produce, so folding it into the run's identity would make the identity depend on the run's output. The floor goes in as the operator's number; whether it turns out to be applicable is a fact about the run, not an input to it.

## Consequences

**A changed threshold re-scores and re-clusters, and that is required rather than tolerated.** ADR-087 says a below-threshold document is never clustered, so the cluster membership written under the old floor is wrong under the new one. A new run id is the mechanism that gets it recomputed, and ADR-115 already priced the recomputation: the vectors are keyed by chunk and embedder identity (ADR-085) and the extracted text by content hash (ADR-070), both outside the run, so the cost is arithmetic over cached material and never a second call to Ollama.

**ADR-107's sentence becomes true.** A changed relevance floor now does mint a new 6a run id, through the upstream chain and by construction, so an arrangement approved under one threshold is not spent on the arrangement produced by another. The approval expires exactly when the thing it approved changed, which is what that gate is for.

**A threshold calibrated under another model mints a run that removes nothing.** Setting a floor read off another model's labels changes the run id, so stage 5 re-scores and re-clusters, and the floor step still refuses to apply the number — telling the operator so on the labelling page. Some recomputation for no change in verdicts, and the honest price of not putting a derived state in an identity.

**Setting a threshold after a scoring run is no longer free in the way an operator might expect, and is exactly as cheap as it should be.** The second invocation does stage 5's arithmetic again over cached vectors, rather than skipping to the removal. Nothing in stages 0 to 4 moves.

**The general rule, now stated where it can be cited:** every value the profile carries is named in the identity of the run whose steps read it. A key a run consumes without being named by is an input that can change while the output keeps its name, and there is no way to notice that from the ledger afterwards. A new key arrives with the question *which run's identity names this*, and an answer of "none" is the defect this record is about.

**Nothing is migrated.** An existing database holds scoring runs whose ids were derived without the floor. They stay exactly as they are and are never rewritten (ADR-077); the next invocation derives an id that names the floor and is therefore a different run, which is the correct reading — those rows really were produced under a configuration nobody recorded in full.

## What this does not decide

**Whether `ScoringRun` should name the whole embedder identity rather than the model's name.** [ADR-084](0084-the-embedding-model-is-a-profile-gate-and-a-vector-carries-its-whole-embedder-identity.md) says it already does — *"Stage 5's `configConsumed` names the embedder identity, so a corpus scored under a different model, quantization, dimension or instruction is a different run"* — and the code names the profile's model string instead, which cannot tell a moved registry tag from the weights it used to point at. That is the same class of defect as this one and is **not** fixed here: the embedder identity is composed from what the running engine reports about itself, so naming it in a run id means asking a sidecar during identity derivation, and whether a run id may depend on a live service is a decision with a cost nobody has measured. It wants its own record and its own ticket.

**Whether an operator should be told that a threshold change caused a re-score.** The log line the new run writes says which run it is running under, and that is what this project already gives. Inventing a message here would be inventing a requirement.
