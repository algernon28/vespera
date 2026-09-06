# ADR-081 — MinHash retrieves, shingle sets judge; 128 permutations in 16 bands, and containment gets its own index

- **Date**: 2026-09-06
- **Status**: accepted
- **Amends**: none (states the parameters ADR-018 said were derivable and did not derive)

## Context

[ADR-018](0018-stage-4-uses-minhash-with-lsh-banding.md) fixes MinHash with LSH banding and claims the parameters are "analytically derivable". It derives none of them: no permutation count, no band/row split, no threshold. [ADR-079](0079-redundant-with-covers-near-duplication-and-containment-the-fuller-rendering-survives.md) then settled that `redundant-with` covers **both** near-duplication and containment, and [ADR-080](0080-the-boilerplate-floor-is-a-gate-applied-before-signatures-are-computed.md) that signatures are computed over boilerplate-stripped shingle sets.

**The containment half cannot be retrieved by banding at all, and the arithmetic says so.** For a document A contained in B at containment `c`, with size ratio `ρ = |A|/|B|`, the Jaccard similarity is `J ≥ cρ / (1 + ρ − cρ)`:

| ρ | c = 0.90 | c = 0.95 |
| --- | --- | --- |
| 0.50 | 0.4286 | 0.4634 |
| 0.10 | 0.0891 | 0.0945 |
| 0.03 | 0.0269 | 0.0285 |

The 12-page chapter inside the 400-page volume — ADR-079's own worked case — sits at J ≈ 0.03. A banding tuned to make that a candidate makes nearly every pair in the corpus a candidate. Containment therefore needs its own retrieval path, not a lower threshold on the same one.

Both thresholds below were put to the operator as questions about what should be deleted, not derived from the mathematics: **0.80 for near-duplication, 0.95 for containment.** The mathematics then says what machinery reaches them.

## Decision

### Signatures retrieve; shingle sets judge

**Every candidate pair is scored exactly, from the stored `shingle` rows, and no verdict rests on an estimate.** MinHash and LSH do retrieval — turning an N² comparison into a short candidate list — and the judgement that removes a document is then computed from the sets themselves.

This is what makes the rest of the parameter choice tractable: a false candidate costs only the compute to reject it, so the banding is tuned for **recall at and above the cut** rather than for precision. It also removes estimator variance from an irreversible decision — a document removed because a 128-sample estimate happened to land above 0.80 is a document nobody can find again.

### Near-duplication: 128 permutations, 16 bands of 8 rows

Candidate probability is `1 − (1 − s^r)^b`. Measured across the splits, against the 0.80 cut:

| perms | b × r | knee | P@0.50 | P@0.60 | P@0.70 | **P@0.80** | P@0.85 | P@0.90 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 128 | **16 × 8** | 0.707 | 0.061 | 0.237 | 0.613 | **0.947** | 0.994 | 1.000 |
| 160 | 16 × 10 | 0.758 | — | 0.092 | 0.368 | 0.838 | 0.970 | 0.999 |
| 192 | 16 × 12 | 0.794 | — | 0.034 | 0.200 | 0.680 | 0.914 | 0.995 |
| 256 | 32 × 8 | 0.648 | 0.118 | 0.418 | 0.850 | 0.997 | 1.000 | 1.000 |

**Note what the knee is not.** Putting the knee *at* 0.80 (192 permutations, 16 × 12) retrieves only 68% of pairs that are exactly at the cut, because the knee is where the curve passes ½. Since exact scoring rejects false candidates, the split whose knee sits *below* the cut is the better one: 16 × 8 retrieves 94.7% at 0.80 and effectively everything above 0.85, at the cost of offering 24% of 0.60-similar pairs as candidates that scoring then discards.

**256 permutations in 32 × 8 was weighed and rejected**: it buys 94.7% → 99.7% recall at exactly the cut for double the signature storage, double the hashing, and 1.8× the candidate volume. A pair missed here is a duplicate that stays published, not a document wrongly removed, and a duplicate set of three members gets three chances rather than one.

**512 bytes per document** — 128 minima as 32-bit values — so roughly half a gigabyte per million documents, which is the first storage cost in this pipeline worth stating.

### Containment: an inverted index over each document's rarest shared shingles

For a document A, take the **32 shingles with the lowest `document_count` among those with a row in `shingle_document_frequency`**, ties broken by hash so the selection is deterministic. That row set is exactly the right one for free: ADR-074 writes rows only for hashes appearing in two or more documents, so a hash with no row is unique to A, and a shingle no other document holds can never be evidence that another document contains A.

**B is a containment candidate for A when B holds at least 24 of A's 32 rare shingles and `|B| > |A|`.** If A really is 0.95-contained in B, each of those shingles is in B with probability ≥ 0.95, so the chance of missing the pair is a binomial tail: **1.9 × 10⁻⁵** at c = 0.95 (and 3.3 × 10⁻³ at 0.90, which is below the cut and may be missed). Because the selected shingles are the corpus's rarest, their posting lists are short, and an unrelated document holding 24 of them is not a case that arises.

Candidates are then scored exactly, like every other candidate: `|A ∩ B| / |A|` from the stored shingle sets.

**Only in one direction, and only where banding would not already have caught it.** Containment is evaluated smaller-into-larger. Where two documents are of similar size, 0.95 containment implies a Jaccard around 0.90, which the near-duplicate path retrieves already — so the containment path exists for the small-in-large case that path cannot see.

### Identity, and no new dependency

**A signature's identity is the shingle parameter identity, the permutation identity, and the boilerplate floor** — ADR-073's pattern (a value dependent on a parameter lives under that parameter's identity), with the floor included per ADR-080. The permutation identity is the permutation count and the seed they are derived from, recorded so a re-run reproduces the same permutations rather than merely equivalent ones.

**Nothing is added to the pom** (ADR-046). The `shingle` table already stores a 64-bit `shingle_hash`; 128 permutations of it are 128 multiply-shift-xor evaluations per shingle in plain Java, and the banding index is a hash map of band hashes. A MinHash library would be a dependency carrying more than this decision needs.

## Consequences

**The two thresholds are recorded here as values, not as profile keys.** Whether either is operator-overridable, and what a calibration report over them shows, is [#69](https://github.com/algernon28/vespera/issues/69)'s — and it now has two distributions to show rather than one, cutting on different measures. Per ADR-078, if a key is added it names the one measure it reads.

**Stage 4's cost is now legible.** One pass to sign every survivor (128 hashes per shingle), one pass to band and bucket, one rare-shingle index pass, then exact scoring of a candidate list. Nothing here is N², and the only large stored artifact is the signature table.

**Retuning either threshold does not re-sign the corpus, but retuning the boilerplate floor does** (ADR-080). The thresholds are applied at scoring time, over signatures and shingle sets that do not depend on them; the floor is applied before signing and sits in the signature's identity. Worth knowing which knob is expensive.

**A pair at exactly 0.80 has a 1-in-20 chance of never being looked at.** That is the accepted cost of 128 permutations, stated plainly rather than left for someone to discover: recall at the cut is 94.7%, not 100%, and the missed case is a duplicate that stays in the corpus.

**Nothing here fixes the signature table's columns, the banding index's in-memory shape, or how the candidate list is held.** Those are the hand-off spec's ([#70](https://github.com/algernon28/vespera/issues/70)), along with whether the candidate pairs are stored as a re-analyzable artifact — the map's open fog, which #69's report decision may settle first.
