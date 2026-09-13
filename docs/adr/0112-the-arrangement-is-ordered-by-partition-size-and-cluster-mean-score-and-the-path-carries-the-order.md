# ADR-112 — The arrangement is ordered by partition size and cluster mean score, and the path carries the order

- **Date**: 2026-09-13
- **Status**: accepted
- **Amends**: [ADR-104](0104-the-surviving-originals-stay-where-they-are-and-the-deliverable-references-them.md) — **its `documents.csv` column list only**, which gains `partition_order` and `cluster_order`. Everything that record decided — the originals stay where they are, nothing is copied, links are absolute `file:` targets and are not durable — is untouched.
- **Rests on**: [ADR-105](0105-stage-6a-names-the-arrangement-stage-5-already-built-and-unattributed-is-struck.md) (6a writes a name and an order), [ADR-110](0110-pipeline-hands-synthesis-its-inputs-so-the-module-rule-gains-no-second-exception.md) (`partition_order` and `cluster_order` as columns kept apart from the identity ordinal), [ADR-103](0103-the-deliverable-is-a-markdown-tree-in-the-working-directory-one-tree-per-run-id.md) (the tree and `index.md`), [ADR-106](0106-a-cluster-gets-a-derived-label-from-6a-and-a-generated-title-from-6b.md) (label against title), [ADR-107](0107-the-arrangement-gate-approves-a-named-6a-run-and-the-path-becomes-five-invocations.md) (the gate approves a named 6a run), [ADR-111](0111-a-cluster-fault-is-a-row-in-synthesis-and-a-re-run-under-the-same-id-repairs-rather-than-regenerates.md) (the repair pass), ADR-087 (clustering), ADR-020 (the score).

## Context

ADR-105 committed stage 6a to writing "a name and an order". ADR-106 settled the name. ADR-110 fixed the columns — `partition_order` and `cluster_order`, explicit integers kept distinct from the identity ordinal so a re-ordering never rewrites a primary key — and deferred the rule that fills them, because the candidates are a real trade-off rather than a detail.

**Nothing orders the arrangement today.** `DocumentClusters.winningSeeds` is `ORDER BY winning_seed_occurrence_id`, which is walk order, which is the filesystem's enumeration order — the one thing in this system that is explicitly not a judgement about anything.

### The two levels are not the same comparison

This is the fact the decision turns on, and it is visible in `RelevanceScorer`. ADR-020's score is the maximum, over seed documents, of the mean of the top three cosine similarities between the survivor's chunks and that seed's chunks.

- **Within a seed partition**, every score was taken against the *same* seed document. "Which cluster is most like the seed" is a well-defined question and the numbers answer it.
- **Across seed partitions**, the scores were taken against different seeds, and the function is a maximum over pairs. A seed document with more chunks offers more (survivor chunk, seed chunk) pairs, so its top three are drawn from a larger sample and come out systematically higher. Ordering partitions by any score would rank them partly by **how long the operator's seed documents are**, which is not a statement about the corpus.

So a single rule applied at both levels would be tidiness bought by ignoring that the two comparisons are not alike.

### Two of the candidates do not exist

- **"The seed set's own order, as the operator supplied it"** is not an input this system has. `Profile.seedFolder` is a *folder* (ADR-064); census walks it with `Files.walkFileTree` and sorts nothing. A folder has no order, and reading one out of the enumeration would be the filesystem's order wearing the operator's name.
- **Anything keyed on the occurrence id is not reproducible.** An occurrence id is per-walk by design, and every ordinary run mints a new walk — the reasoning `relevance_label` already carries in its own schema comment (ADR-097). ADR-103 promises that an unchanged re-run writes *the same path*; an order that moved inside that path would weaken the promise without anything reporting it.

## Decision

### Seed partitions sort by document count, descending

`CONTEXT.md` answered this before the question was asked. Its **seed partition** entry: *"All file occurrences sharing a winning seed. The unit within which grouping happens, and the top level of the arrangement — so its size is also a statement about the seed that owns it."* Size is the one candidate that is a judgement the system actually made about the corpus, and the one free of the chunk-count bias above.

**Ties break on the seed's path relative to the seed folder**, never on the occurrence id — the path survives a re-walk, and it is the one handle the operator has on this order at all: an operator who wants a particular seed first can rename its file.

**Alphabetical by seed was refused.** It is more findable, but `index.md` is a mechanical listing the reader scans anyway (ADR-103), and an order that front-loads nothing spends the one chance this stage has to say which part of the archive is substantial.

### Clusters sort by their members' mean relevance score, in two tiers

Within a partition the comparison is sound, so the order is the one a reader wants: most like the seed, first.

- **Mean, not maximum.** A maximum lets one outlier document carry a vague cluster to the top; the mean asks whether the cluster is on-topic, which is what its synthesis doc will claim to be about.
- **Two tiers: every multi-document cluster first, then every singleton**, each tier in mean-score order. Under a flat rule the partition's single best document, alone in its own cluster, outranks every substantial cluster beneath it. `ClusterSizeReport` exists precisely because "a partition that is 40% one-document pages" is a measured outcome and not a hypothetical, and a hundred one-document clusters ahead of a substantial one is a deliverable nobody reads.
- **Ties break on `cluster_ordinal`**, which is deterministic: `Communities` has no RNG, visits in occurrence-id order and hands out ordinals in order of first appearance.

**A blended size-and-score key was refused.** It would be a third unmeasured threshold with no value anyone can defend before a corpus run, and it would make the order impossible to state in one sentence — which matters, because `index.md` has to be readable with no database beside it. The two-tier rule states in one: *substance first, then the singles.* It costs nothing where there are no singletons, and where a partition is all singletons the first tier is empty and the rule reduces to score order.

### The order is rendered, never re-derived

`arrangement.html` and `index.md` both show the stored `partition_order` and `cluster_order`. Neither re-sorts.

This is what makes the columns worth storing. ADR-107 has the operator approve a *specific* arrangement, named by the first twelve characters of its 6a run id and printed by `arrangement.html`. If the deliverable re-sorted, the arrangement the operator approved and the arrangement the reader receives would be two different arrangements sharing one id, and the gate would be approving something nobody ever sees.

### The path carries the order

ADR-103's tree is `<seed-partition>/<cluster>.md` and never said what those segments are called. Both levels take a **zero-padded ordinal prefix**, padded to the count at that level — the partition count for the top level, that partition's own cluster count beneath it:

```
deliverable/<run-id>/
  index.md
  documents.csv
  01-industrial-safety-standards/
    001-fire-suppression-retrofits.md
```

A file browser and every wiki importer sort alphabetically. Without the prefix the order would be stated in `index.md` and contradicted by the directory listing beside it — stating it twice and disagreeing.

**A partition directory is named after its seed document's filename stem, slugged.** A seed is a document the operator chose and put in a folder, so its filename is already their own naming act, where a Docling `title` is its author's. **This mints no new concept**: there is no "partition label" and this record does not add one — a seed partition is named after its seed, which is the only thing that defines it. Two seeds with the same stem in different subfolders cannot collide, because the ordinal prefix makes every segment unique by construction.

**A cluster file is named from its 6a label, never its 6b title** (ADR-106). A path that depended on generation succeeding would not exist for a cluster that faulted.

### A faulted cluster keeps its slot

The order is 6a's and a fault is 6b's: a cluster's members and their scores exist before generation is attempted, so a cluster sorts identically whether or not its synthesis doc is ever written. It renders in position, as the hole headed by its label that ADR-109 and ADR-111 describe.

Sinking faults to the bottom was refused because it would make ADR-111's repair pass *rename files*: a repaired cluster would change its own filename and shift every cluster beneath it, turning "skip what succeeded" into a rewrite of the whole partition.

### `documents.csv` gains `partition_order` and `cluster_order`

ADR-104's own justification applies unchanged — every column there is one the ledger already holds, so it costs a query. The manifest exists so the tree can be re-read and re-pointed mechanically when the archive moves; without these columns a consumer rebuilding anything from it would have to re-derive the order by parsing `index.md`, or silently fall back to alphabetical.

## Consequences

**The order is now a thing the gate approves.** ADR-107's approval was of a named 6a run; until now the only content behind that name was the set of clusters and their labels. The sequence the operator reads at the gate is the sequence the deliverable ships.

**A large, incoherent partition leads the deliverable.** Size ranks it first whether or not its clusters hold together. This is accepted rather than mitigated: `ClusterSizeReport` already shows how each partition broke up, it is read *before* the gate, and a rule that demoted a big partition for being loose would need a coherence threshold nobody can set yet.

**The path is not identity, and the slug is lossy.** A cluster is identified by its ordinal within its seed partition (ADR-106); the filename is a rendering, like the heading above it. Two clusters may slug to the same string and the prefix separates them.

**A filename is stable across a repair pass while the heading above it may change.** The file is named from the 6a label; a repaired cluster's heading changes from the label to its generated title (ADR-106) and its filename does not move. That is the point of naming it from the label.

**Re-ordering still never rewrites a key**, which is what ADR-110 bought the separate columns for. A later record that changes either rule rewrites `partition_order` and `cluster_order` under a fresh 6a run and touches no identity.

**Whether a generated overview sits above the partitions is still not decided here** — ADR-103 left it open and it stays open. Whatever answers it inherits this order rather than setting its own.
