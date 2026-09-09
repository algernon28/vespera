# ADR-096 — k retains neighbours regardless of distance, so the retained-edge similarities are reported

- **Date**: 2026-09-09
- **Status**: accepted
- **Amends**: [ADR-087](0087-clusters-are-modularity-communities-over-a-k-nearest-neighbour-graph-built-in-blocks.md) — its "a partition where nothing is close to anything else clusters into all singletons" describes an outcome its own mechanism cannot reach above a partition of one; and it adds the retained-edge similarity spread to the size report ADR-087 established. It does not touch ADR-087's objective, its determinism, its parameters or its no-floor rule.

## Context

[ADR-087](0087-clusters-are-modularity-communities-over-a-k-nearest-neighbour-graph-built-in-blocks.md) settled clustering as modularity communities over a k-nearest-neighbour graph, and its paragraph forbidding a catch-all reads:

> **A partition where nothing is close to anything else clusters into all singletons**, and that is a real outcome rather than an error: it says the seed collected documents that resemble it individually and not each other. It is reported, not repaired.

Implementing it (#109) showed that the first clause is not something the mechanism can do. **k retains a document's fifteen nearest neighbours however far away they are**, and the same decision sets **no edge similarity floor** — deliberately, because an unmeasured threshold is what *observe before enforce* refuses. A document therefore always has neighbours whenever the partition holds more than one document, and modularity always finds communities in the graph those neighbours make. A partition of twenty mutually orthogonal documents comes back grouped, by which of them are least unalike.

All singletons is reachable in exactly one place: where the neighbour lists are empty, which is a partition of one. `CommunitiesTest` covers it at the layer where it exists; nothing at the composed layer can produce it.

**The load-bearing rule in that paragraph is not the count.** It is the sentence after it — *reported, not repaired* — and the consequence ADR-087 draws from it: "Merging small communities into a catch-all would be two unmeasured thresholds and a fabricated page." That rule the code honours exactly. What went wrong is that an illustration of an extreme was written as though it were a state the mechanism passes through.

**But correcting the sentence exposes a real gap.** A partition of forty documents that genuinely resemble each other and a partition of forty that share nothing produce the same shape of output: the same rows, the same ordinals, the same tidy table of sizes. Nothing a reader sees distinguishes *grouped because alike* from *grouped because k forced edges*. ADR-087 already admits that "whether a modularity community reads as a coherent page to a person ... needs a corpus", and points at the size report as what makes the shape visible early — but the size report cannot see this, because sizes are the one thing the two cases share.

## Decision

### The illustration is corrected, and the mechanism is not changed

ADR-087's "clusters into all singletons" is read as what it was written under: an extreme illustrating that nothing merges small communities. **Above a partition of one, a partition of mutually distant documents is grouped, and that is the mechanism working as decided rather than failing.** No edge similarity floor is added, no distance-aware k, no minimum retained similarity. Each of those is the unmeasured threshold ADR-087 refused, and adding one to satisfy a sentence would be repairing the record with code.

### The retained-edge similarities are reported beside the sizes

The size report gains, per partition, the **spread of the similarities on the edges the graph actually kept** — the lowest, the middle and the highest. That is what tells the two cases apart: a partition whose retained edges sit at 0.95 was grouped by resemblance, and one whose retained edges sit at 0.02 was grouped by k.

**An observation, not a threshold.** Nothing reads the number, nothing gates on it, and no verdict or profile key follows from it. It is the measurement that would have to exist before an edge floor could be anything but a guess — the same shape ADR-028 and [ADR-088](0088-the-relevance-threshold-is-read-off-a-stratified-sample-of-sixty-labels-and-an-unset-floor-does-not-stop-the-run.md) use for the relevance threshold, and [ADR-075](0075-stage-3-writes-a-confidence-distribution-report-that-calibrates-tier-2.md) for the confidence floor: report the distribution first, and let the number be read off it by a person who has seen a corpus.

**It costs one field.** The similarities are computed already — `NearestNeighbourGraph`'s top-k heaps hold them while choosing what to keep, and throw them away when handing back indices. Nothing is recomputed and no vector is re-read.

### The similarity reaches the page and not the grouping

`Communities` continues to weight **every retained edge at 1.0**, and this decision says so rather than leaving it to whoever next reads the code. Weighting modularity by similarity would move every cluster boundary in the system, which is a change to ADR-087's objective and not to its reporting. If the reported spreads turn out to argue for it, that is its own decision, taken afterwards, with the measurement in hand.

## Consequences

**#109's acceptance criterion is satisfied by the community pass, not by the composed step.** "A partition of mutually distant documents yields all singletons" is true where neighbour lists are empty and unreachable above that; the criterion it was standing in for — nothing is merged into a catch-all, and every survivor lands in exactly one cluster — is met and tested at both layers.

**Stage 6a gains the signal ADR-087's consequences implied it had.** "Receives a page tree whose shape it did not choose and can measure" meant, until now, that it could read the sizes. The similarity spread is the first number in the system that speaks to whether a cluster is a cluster or an artefact of k.

**An edge floor becomes answerable.** Not answered — this ADR adds no number and no key. But after one real archive, the spreads say whether retained edges cluster near the top of the range or trail all the way to zero, and that is the difference between a floor with a measurement behind it and the guess ADR-087 was right to refuse.

**A partition of one is still the only all-singletons partition.** Anyone reading ADR-087 alone will expect otherwise, which is why this record exists rather than a corrected sentence in place: the repository does not edit an accepted decision, it amends it (ADR-072's precedent, which mints a record for exactly this — wording that describes a mechanism the mechanism does not have).
