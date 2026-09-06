# ADR-087 — Clusters are modularity communities over a k-nearest-neighbour graph, built in blocks

- **Date**: 2026-09-06
- **Status**: accepted
- **Amends**: none — settles what [ADR-045](0045-clustering-runs-within-each-seed-partition.md) scoped and [ADR-027](0027-clustering-moves-to-stage-5.md) placed, under the memory contract [ADR-085](0085-vectors-live-in-sqlite-and-the-pairwise-matrix-is-never-materialised.md) imposed

## Context

ADR-045 fixed the scope — clustering runs **within each seed partition, never corpus-wide** — and ADR-027 moved it to stage 5 because the embeddings are already paid for. Neither says which algorithm, how many clusters, or what a cluster is once computed.

**The ticket's own premise no longer holds.** [#84](https://github.com/algernon28/vespera/issues/84) was written expecting that "an exhaustive distance matrix is available, so methods needing one are affordable at partition scale". ADR-085 then measured it and forbade it: 4.7 GiB for one 50,000-document partition, 74 GiB at 200,000, with no relief from a smaller dimension. Agglomerative clustering over a condensed distance matrix — the textbook answer — is out, and so is anything else that needs all pairs *stored* rather than merely *computed*.

**Partitions are larger than the word suggests.** ADR-020 assigns every scored document a winning seed by maximum, so the partitions cover the whole corpus rather than a relevant slice of it, and ADR-045's own "seed owns 60%" alarm is a partition of 120,000 documents in a 200,000-document archive.

## Decision

### Clustering runs over a partition's survivors, inside the scoring run

**Survivors** — occurrences carrying no blocking verdict — which is one rule covering both states of a system whose relevance threshold ships unset (ADR-028, [#83](https://github.com/algernon28/vespera/issues/83)): while the threshold is unset nothing is removed and every scored document is clustered; once it is set, a `below-threshold` document is never clustered and costs nothing.

It runs in stage 5's **scoring run** — the second of the two runs [ADR-086](0086-seed-corpus-mismatch-is-measured-before-the-model-gate-and-reported-never-enforced.md) established — because it needs the vectors, and those need the model.

### A k-nearest-neighbour graph, built in blocks

The memory contract permits computing every pair while storing only a bounded few, so that is the shape:

**One exhaustive pass over the partition retains, for each document, its k nearest neighbours by cosine similarity over the document mean vectors** (ADR-085). Time is the N²/2 ADR-085 already priced — roughly two minutes across 16 cores at 50,000 documents — and storage is O(N·k) rather than O(N²).

**Vectors stream in fixed-size blocks rather than all being held resident.** ADR-085 records that a partition's vectors alone are 314 MiB at 20,000 documents, which is 1.9 GiB at the 120,000-document alarm case — bounded, but bounded by something that grows. Holding one block resident while the rest streams past it makes the ceiling independent of partition size:

| Resident | At B = 4,096 and 4,096 dimensions |
|---|---|
| two blocks of vectors | 128 MiB |
| the top-k heaps, for all N | ~21 MiB at N = 120,000, k = 15 |

One code path at every size, rather than one that works until it does not — which is the failure mode the stage-4 retrospective recorded and this whole ticket exists to avoid. The cost is re-reading the partition's vectors ⌈N/B⌉ times from a local SQLite file, sequentially; that is the trade, and it is the right way round, because time is recoverable and an out-of-memory run is not.

### Modularity communities over that graph, in plain Java

**The cluster count is not chosen; it falls out.** Modularity optimisation over the neighbour graph finds its own number of communities, which is the only property that lets a partition of eleven documents and one of eleven thousand share a code path. Every alternative on the table — k-means, a fixed count, an operator parameter — asks somebody to answer "how many pages belong under this heading" before anybody has seen the documents.

**Written rather than imported.** This repository already implements `HybridChunker`, MinHash and `UnionFind` in plain Java, and ADR-046 says the pom carries what a recorded decision *requires*. A modularity optimiser over an in-memory neighbour graph is a small amount of code against a large transitive dependency tree for one function.

**Deterministic.** Modularity optimisation is order-dependent, so nodes are visited in a fixed order — by occurrence id — and no random number generator is involved. The reasoning is ADR-029's, applied one layer up: a calibrated threshold is meaningless against boundaries that move between runs, and a page tree that reshuffles when nothing changed is worse, because a person has reviewed it.

### The parameters are code defaults, not profile keys

Following [ADR-082](0082-stage-4-judges-on-its-first-run-its-thresholds-are-code-defaults-and-it-ships-no-report.md), which settled the same question for stage 4's thresholds.

| Parameter | Value | Why not a profile key |
|---|---|---|
| `k`, neighbours retained | 15, floored to N−1 | an operational parameter, not a corpus judgement |
| resolution | 1.0 | see below |
| edge similarity floor | **none** | an unmeasured threshold is the guess **observe before enforce** exists to refuse; k already bounds the edges |

**Resolution deserves its own sentence, because it is the one an operator would reach for.** A `Profile` key is for "a judgement the engine cannot make for itself" — a fact about the corpus that the operator knows and the engine does not. Cluster granularity is not that. It is a preference about page size, discoverable only by looking at output that does not exist yet, so shipping it as a key would ship an unset value that gates nothing and that nobody is able to answer. If the sizes prove wrong in practice it becomes a key then, with a measurement behind it — which is exactly the shape ADR-028 and #83 use for the relevance threshold.

### Every survivor lands in exactly one cluster, and a cluster of one is a cluster

Modularity assigns every node to a community, so membership is total and disjoint by construction. A document close to nothing forms its own community.

**No in-partition unattributed bucket.** ADR-022's `unattributed` sits at the *top* level of the page tree, for documents with no winning seed — a different condition with a different cause. Inventing a second one inside a partition would create a page that no document actually belongs to, which is the engine making a judgement about what a reader wants.

**A partition where nothing is close to anything else clusters into all singletons**, and that is a real outcome rather than an error: it says the seed collected documents that resemble it individually and not each other. It is reported, not repaired.

### A cluster is a stored measurement, and its sizes are reported

**No verdict.** Clustering removes nothing, and the verdict vocabulary is closed (ADR-042) with nothing in it for this.

**Stored as one row per survivor** — the run that computed it, the occurrence, its winning seed, and its cluster ordinal within that partition. A cluster's identity is therefore `(run_id, winning_seed_occurrence, ordinal)`: it exists as the set of rows carrying it, rather than as a row of its own that membership then has to be kept in step with. Per [ADR-077](0077-a-regenerated-measurement-is-a-fresh-row-set-under-its-own-run-id.md), a re-run writes a fresh row set under its own run id.

**The size distribution is reported** — how many clusters each partition produced and how their sizes are spread. A partition that is 40% one-document pages is a fact whoever builds the page tree needs before they build it, and a report is where that belongs rather than a threshold that quietly merges them. Merging small communities into a catch-all would be two unmeasured thresholds and a fabricated page.

## Consequences

**Stage 6a receives a page tree whose shape it did not choose and can measure.** ADR-022 puts clusters within seed-named headings, so "how many clusters" was always "how many pages under this heading" — and this decision answers it with modularity plus a size report rather than with a number somebody had to invent.

**The memory ceiling is now a constant rather than a function of the corpus.** ADR-085 stated the contract; blocking is what discharges it. Whatever a partition's size, stage 5's clustering holds two blocks of vectors and one neighbour list.

**Re-reading vectors ⌈N/B⌉ times is real I/O** — around 30 passes over 1.9 GiB at the 120,000-document alarm case. Sequential, local, and against a cost that would otherwise be an out-of-memory failure at exactly the moment a corpus got interesting.

**Clustering quality is untested against a real archive**, like every other number in this map. What can be checked before then is the shape: total and disjoint membership, determinism across runs, and a bounded ceiling. What cannot is whether a modularity community reads as a coherent page to a person — that needs a corpus, and it is what the size report exists to make visible early.

**`CONTEXT.md` gains nothing.** **Cluster** was added by ADR-085, which needed the word to say what clustering runs over; this decision fills that entry in rather than adding to the glossary. Its `_Avoid_` list already guards the boundary the ticket asked about, in both directions: a cluster is not a redundancy set, and a redundancy set is not a cluster (ADR-079).

**Nothing here fixes the tables or the wiring.** The row's columns and how the step composes alongside scoring are the hand-off spec's ([#85](https://github.com/algernon28/vespera/issues/85)), the same deferral every prior slice made.
