# ADR-085 — Vectors live in SQLite as a content-addressed cache, and the pairwise matrix is never materialised

- **Date**: 2026-09-06
- **Status**: accepted
- **Amends**: none — settles the sizing [ADR-039](0039-chroma-is-derived-sqlite-is-authoritative-for-vectors.md) left open and [ADR-045](0045-clustering-runs-within-each-seed-partition.md) claimed to resolve, and gives [ADR-027](0027-clustering-moves-to-stage-5.md)'s "nearly free" a number

## Context

The stage-4 retrospective ([#65](https://github.com/algernon28/vespera/issues/65)) recorded the reason this decision is taken in advance rather than reviewed afterwards: **fixture-scale tests cannot see a scale defect**. Stage 4's first implementation passed every test while holding the entire shingle corpus in a `HashMap`, and only reading the code caught it. Stage 5 embeds every survivor and computes N²/2 distances per partition, so the same mistake here is larger and less visible.

[ADR-084](0084-the-embedding-model-is-a-profile-gate-and-a-vector-carries-its-whole-embedder-identity.md) settled what a vector *is*. This settles where it lives and what is held while it is used.

### Measured, on a 16-core development machine

Single-threaded scalar Java 26, float32, cosine similarity over random vectors. A throwaway probe, run rather than estimated, because "nearly free" and "fits in memory" are claims with numbers behind them that nobody had written down.

| | dimension 4096 | dimension 1024 |
|---|---|---|
| similarities per second, one thread | **658,605** (5.4 GFLOP/s) | 2,742,860 |
| 20,000 vectors resident | **314 MiB** | 80 MiB |
| all pairs of a 50,000-item partition | 31.6 min one thread | 7.6 min |
| **full distance matrix, 50,000 items** | **4.7 GiB** | 4.7 GiB |
| full distance matrix, 200,000 items | 74 GiB | 74 GiB |

**The compute is affordable and the matrix is not.** Even a partition holding most of a large corpus is minutes of parallel work, while materialising its distances is gigabytes — and, being a count of pairs rather than of components, that cost does not fall when the dimension does. This inverts the question the ticket asked: the ceiling is not the vectors, it is the N²/2.

## Decision

### Vectors live in SQLite, in a content-addressed cache with no run id

One row per chunk, keyed by ADR-084's identity: `chunk_cache`'s own key (`content_hash`, `chunker_identity`, `tokenizer_identity`, `ordinal`) plus the composed `embedder_identity`.

**No `run_id`, deliberately**, and this is the one place stage 5 departs from the shape every other stage's output takes. A vector is a *derivation of content under an instrument*, exactly like `extraction_cache` and `chunk_cache` — not a judgement about an occurrence at a moment. Keying it by run would re-embed an unchanged corpus every time an unrelated implementation version moved, which is the cost ADR-032 called durable precisely to avoid. The verdicts stage 5 writes carry run ids; the vectors they were computed from do not.

**Stored as a float32 BLOB**, little-endian, not as text or JSON: 16 KiB per 4096-dimension vector against roughly three times that as decimal text, over every chunk in the corpus.

### Vectors are stored at full dimension

A Matryoshka truncation is derivable from the stored full vector; the full vector is not recoverable from a truncation. Storing the fuller thing preserves the option, which is [ADR-079](0079-redundant-with-covers-near-duplication-and-containment-the-fuller-rendering-survives.md)'s survivor rule wearing different clothes.

Truncating is therefore a decision that can be taken later against measured scores, and — because ADR-084 put dimension in the identity — taking it mints a new row set rather than reinterpreting the stored one. The cost of the choice is 16 KiB per chunk against 4 KiB: roughly 3.1 GiB of SQLite at 200,000 chunks, against 0.8 GiB.

### Chroma is retained on speculation, and stage 5 does not use it

**Stage 5 neither reads nor writes it.** [ADR-020](0020-relevance-scoring-function.md)'s scoring needs no vector database, ADR-045's clustering is an exhaustive comparison rather than an approximate-nearest-neighbour search, and Chroma appears in no `src/main` file today — only in `TestcontainersConfiguration`, `TestVesperaApplication` and one integration test asserting the application boots against it.

The measurement says why it earns nothing here. An exact brute-force scan over **every chunk vector in the corpus** costs, at the throughput above:

| corpus | exact scan, one thread |
|---|---|
| 200,000 chunks | **0.30 s** |
| 1,000,000 chunks | 1.5 s |
| 50,000,000 chunks | 76 s |

An ANN index exists to turn a slow exact search into a fast approximate one. At three tenths of a second for the exact answer, it trades correctness for nothing. It would begin to earn its place somewhere beyond ten million chunks — an archive one to two orders of magnitude larger than this pipeline is aimed at.

**It is nevertheless kept**, and the reason is recorded honestly: stage 6 is not designed, it is the next map's destination, and the cost of carrying the dependency and its sidecar is smaller than the cost of an argument about re-adding it. This is a decision taken on speculation. **The next map is therefore obliged to justify Chroma rather than to inherit it**, and if stage 6a and 6b turn out to iterate over clusters and partitions already computed — which is what ADR-022's page tree and `CONTEXT.md`'s synthesis doc describe — then it should go, along with the compose service, the Testcontainers image and the integration test's Chroma half.

What would genuinely bring it back: an interactive query surface over the finished archive, which no recorded decision asks for; or a corpus large enough that the scan above stops being instant.

### Scoring holds the seed side resident and streams the corpus

ADR-020's function is a maximum over seed documents, so every corpus chunk must meet every seed chunk. The small side stays; the large side streams.

**The rule, checkable against a corpus size**: resident bytes = *seed chunk count* × *dimension* × 4. At 4096 dimensions that is **16 KiB per seed chunk** — a seed folder of a few dozen documents is roughly 500 chunks and 8 MiB, and it takes 64,000 seed chunks to reach 1 GiB.

**A seed folder that is not "a few dozen" is not a failure and not a gate.** [ADR-083](0083-the-seed-set-is-extracted-by-stage-5-and-an-unusable-seed-is-recorded-not-a-gate.md) already settled that a mistyped seed path — pointing at a parent directory — produces hundreds of nominal seeds and proceeds. The consequence here is arithmetic rather than a refusal: the resident cost and the scoring cost both scale linearly in seed chunks, and both are reported. Scoring 200,000 corpus chunks against 500 seed chunks is 10⁸ similarities — 152 s on one thread, and it parallelises. Against 20,000 seed chunks it is 4×10⁹, roughly 100 minutes on one thread.

Where that becomes an operator's problem it is the **seed/corpus mismatch report**'s to say so ([#82](https://github.com/algernon28/vespera/issues/82)), which exists for exactly this: telling someone their seed set does not resemble what they are scoring against, before the archive is scored.

### Clustering runs over document mean vectors, computed and discarded

ADR-045 clusters *documents* within a seed partition, and ADR-084 stores vectors per chunk. A document therefore needs a representation, and it is **the mean of its chunk vectors, computed at clustering time and never stored** — derivable from rows that already exist, so storing it would mint a second identity to keep in step with the first.

Two alternatives were weighed against worked cases from an industrial archive, seeded on a safety inspection report.

**Against the top-3 chunks that produced the score.** A 200-page maintenance manual with three pages on inspection procedure clusters, under the mean, with the other equipment manuals — which is what it *is* — and under top-3 with the inspection procedures, which is why it *matched*. ADR-022's page tree is for reading, so what a document is wins. The deciding case is the other direction: a document whose three best chunks are a shared legal disclaimer also present in the seed would, under top-3, cluster with every other document carrying that disclaimer — **a cluster of boilerplate that looks like a cluster of topic**. Nothing strips boilerplate from embeddings; stage 3 and 4's floor operates on shingles, for redundancy. The mean's own failure — misfiling a long heterogeneous document — is documented and visible; top-3's is an artefact that reads as a finding.

**Against clustering chunks and assigning each document to the cluster holding most of its own.** It handles a stapled bundle of three unrelated documents better than either, and it costs a partition's item count becoming chunks rather than documents: a 20,000-document partition at ten chunks each is 200,000 items and 2×10¹⁰ pairs — **32 minutes across 16 cores against 2 minutes**. The multi-cluster membership it would enable then has to be discarded anyway, because a page tree gives a document one home.

### The pairwise matrix is never materialised, and that is the memory contract

**No implementation of stage 5 may hold the full N×N (or N²/2) distance structure**, whatever algorithm [#84](https://github.com/algernon28/vespera/issues/84) selects. The table above is the reason: 4.7 GiB at a 50,000-document partition, 74 GiB at 200,000, and no relief from a smaller dimension.

This rules out textbook agglomerative clustering over a precomputed condensed distance matrix, and it is stated here rather than in #84 because the number that justifies it was measured here. What #84 inherits is a constraint, not an algorithm.

Resident cost for clustering is then the partition's vectors alone: *document count* × *dimension* × 4 — **314 MiB at 20,000 documents**, measured rather than computed.

### One seed owning most of the corpus is reported, never refused

ADR-045 names the "seed owns 60%" alarm. Because the matrix is never materialised, there is nothing to refuse at: a dominant partition costs resident vectors that scale linearly and a pair count that parallelises across cores, so the outcome is minutes and a number in a report rather than a wall.

Refusing would make stage 5 halt over a *measurement*, which is the argument ADR-083 lost and then won, and it would suppress exactly the fact the operator needs: `CONTEXT.md` says a partition's size "is also a statement about the seed that owns it".

## Consequences

**Stage 5's storage is one row per chunk and roughly 3.1 GiB per 200,000 chunks**, in the same SQLite file as everything else (ADR-008, ADR-009). That is the largest thing this pipeline stores, and it is the price of ADR-032's durability — the alternative is re-embedding a corpus every time anything downstream is re-run.

**A vector survives a re-run of every stage.** Having no run id, it is invalidated only by a change to the content, the chunking instruments, or the embedder — which is the whole point of ADR-084's identity, and what makes a second bake-off candidate cost one embedding pass rather than a rebuild.

**"Nearly free" (ADR-027) now has a number, and it holds.** Clustering a partition is minutes of parallel arithmetic over vectors that were computed for scoring anyway. The claim was made against no measurement; it survives one.

**Chroma is carried without a caller, on the record.** The next map inherits an obligation rather than a component: justify it against what stage 6 actually does, or remove it and its sidecar. Recording it this way is the difference between a decision and a thing nobody remembers agreeing to.

**The probe is not in the repository.** Its numbers are, above. A throughput measurement from one machine is a fact about that machine — useful for choosing a design, misleading if committed as a test that would fail on different hardware.

**Nothing here picks a clustering algorithm, a cluster count, or what a cluster is stored as.** Those are #84's, now constrained by the memory contract above; how the steps are wired is the hand-off spec's ([#85](https://github.com/algernon28/vespera/issues/85)).
