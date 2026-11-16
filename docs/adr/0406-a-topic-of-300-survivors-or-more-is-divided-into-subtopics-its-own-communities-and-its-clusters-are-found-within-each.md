# ADR-406 — A topic of 300 survivors or more is divided into subtopics, its own communities, and its clusters are found within each

- **Date**: 2026-11-16
- **Status**: accepted on 2026-11-16.
- **Decided**: by the operator, in map [#822](https://github.com/algernon28/vespera/issues/822), as [ADR-396](0396-topics-inferred-from-the-seed-sets-passages-replace-seed-partitions-and-a-survivor-is-placed-apart-from-its-score.md) §3 records it: that a crowded topic gets at most one level between it and its clusters, and that the tree is kept as shallow as the document count allows. By the operator, in [#831](https://github.com/algernon28/vespera/issues/831): that the local model names that level as it names a topic, and that the 6a gate shows it read-only (ADR-396 §3, §5; ADR-407 §4, §7, §8). Under the operator's delegation, in #831 and in ADR-396 §8 and §10: when a topic is crowded (§1), how the level forms (§2), its order (§4) and its term (§6); and, with them, where the step runs and which run its number enters (§5).
- **Amends**: ADR-396 §3, its *"Stage 5's clustering (ADR-087) runs over the survivors of one winning topic"*: in a topic that has subtopics it runs over the survivors of each subtopic, and once over the whole topic to find the subtopics (§2). It carries a note at its head.
- **Amends**: [ADR-330](0330-the-control-panel-edits-every-value-of-profile-yaml-showing-the-value-in-use-and-where-it-came-from-and-ten-technical-choices-ship-as-presets-in-application-yaml.md) §8 and §9, whose table of presets gains `vespera.clustering.subtopic-floor`, whose table of refusals gains its line, and whose table of the members a run's configuration gains gains `subtopicFloor` (§5). It carries a note at its head.
- **Keeps**: [ADR-087](0087-clusters-are-modularity-communities-over-a-k-nearest-neighbour-graph-built-in-blocks.md)'s graph and communities, `NearestNeighbourGraph` and `Communities` unchanged, at clustering's neighbours and resolution, over the survivors' mean vectors, in blocks, visited in the order the clustering step visits them, and a cluster of one a cluster (§2); [ADR-085](0085-vectors-live-in-sqlite-and-the-pairwise-matrix-is-never-materialised.md)'s memory contract (§5); [ADR-112](0112-the-arrangement-is-ordered-by-partition-size-and-cluster-mean-score-and-the-path-carries-the-order.md)'s order of the clusters, now within a subtopic where there is one (§4); ADR-396 §4's order of the topics, which §4 follows one level down; [ADR-105](0105-stage-6a-names-the-arrangement-stage-5-already-built-and-unattributed-is-struck.md)'s total arrangement, every survivor in one cluster (§2).
- **Rests on**: the measurements below, on public text and local models only ([ADR-264](0264-the-private-paths-guard-refuses-only-what-reads-or-writes-outside-the-allow-list-and-a-working-directory-is-open-as-the-place-it-lies-in.md)).
- **Settles**: [#831](https://github.com/algernon28/vespera/issues/831).

## Context

ADR-396 puts topics at the top of the arrangement and clusters within each topic, and gives a crowded topic at most one level more between the two, the tree as shallow as the document count allows. It leaves to #831 when a topic is crowded, how that level forms, its order and its word, "group" being refused because it is how a cluster renders for a reader outside the project (ADR-122). The operator has since ruled, in #831, that the level is named by the local model as a topic is, and shown at the 6a gate read-only; ADR-407 §4, §7 and §8 name its parts once this record says what they are.

The issue asks for the document count at which a topic gains the level, measured against how readable a topic page of N clusters is, and offers two ways to form it: a coarser modularity resolution over the topic's survivors, clusters then found within each part; or the levels Louvain passes through in one run.

### Measured

A probe outside the repository, on public text only, with no seed set and no archive read.

- **Text.** #826's and #827's pool of Reuters-21578 articles of 40 to 512 words carrying one category, one article a document, its vector its mean vector: 5,592 articles of 42 categories, `earn` and `acq` 1,652 each. A document's *subject* is its category. Articles average 133 words.
- **Embedders.** Qwen3-Embedding-0.6B (Q8_0) and nomic-embed-text-v1.5 (F16), through a local Ollama on CPU, the vectors ADR-399's and ADR-401's probes stored.
- **Topics.** A topic is the survivors placed under it, drawn from the pool: of one subject (`earn` or `acq`), of two to four subjects (one of `earn` and `acq`, and one to three of the categories of 30 articles or more), or of the whole pool, at 200 to 5,592 survivors, in a fixed order standing in for the occurrence ids the clustering step visits in. 121 topics under each embedder.
- **The method** is the shipped `NearestNeighbourGraph.build` and `Communities.of`, compiled from this tree, at clustering's 15 neighbours and resolution of 1.0; the Louvain levels, which `Communities` does not return, from #826's port, which gave the shipped classes' clusters on every topic it was checked on.

**What grows with a topic is its clusters, not their number.** Clustered over the whole topic, as ADR-396 §3 has it:

| Survivors | Clusters on the topic's page | Survivors in its median cluster, Qwen / nomic | In its largest | Share of its median cluster one synthesis call reads |
| --- | --- | --- | --- | --- |
| 200 | 3 to 7 | 27.5–54.5 / 32.5–71 | 48–78 | 0.37–0.95 |
| 300 | 4 to 7 | 36–80 / 40.5–73 | 60–121 | 0.33–0.72 |
| 500 | 4 to 9 | 45–115.5 / 62–128 | 92–169 | 0.20–0.58 |
| 1,000 | 6 to 12 | 71–144 / 83.5–170 | 167–304 | 0.15–0.37 |
| 2,000 | 8 to 13 | 112.5–192 | 274–611 | 0.14–0.23 |
| 5,592 | 12 to 14 | 262.5–334.5 | 1,400–1,546 | 0.08–0.10 |

A synthesis call reads what fits the shipped window of 8,192 tokens (ADR-108, `ClusterSynthesis.CONTEXT_WINDOW`): 3,456 words at its 2.0 tokens a word once the reply and the instruction are kept back, 15 to 39 of these articles, 26 at the median, over 2,000 fills in random order. No cluster of one survivor occurred, in any topic, under either embedder.

- **A topic's page of clusters stays short at every size measured**: 2 to 14 entries from 25 survivors to 5,592 under Qwen, and 3 to 14 from 200 under nomic. Larson and Czerwinski (CHI 1998) found that 512 items were found fastest at two levels of 16 and 32 entries, and more slowly at three of 8; no topic measured lists more than 16. A topic's page alone never calls for a level more.
- **A cluster grows with its topic**: its median from 27.5 to 71 survivors at 200 to 262.5 to 334.5 at 5,592. Every topic of 300 survivors or more, of every kind and under both embedders, had a median cluster of more than 32, beyond the 16 to 32 entries a level reads best at, and beyond the 26 articles a synthesis call reads; at 200, 5 of 12 topics did under Qwen and 12 of 12 under nomic.

**Four ways to form the level**, on the same topics, 3 draws of each kind at each size; ranges over the draws, Qwen / nomic:

| Way | Parts, at 1,000 | Parts holding one cluster, at 1,000 | Largest cluster, at 5,592 | Median cluster, at 5,592 | Agreement of the parts with the subjects (NMI), at 500 |
| --- | --- | --- | --- | --- | --- |
| **the topic's own communities at 1.0, clusters found within each on a graph of its own** | **5–12 / 7–9** | **0 of 40 / 0 of 39** | **241 / 283** | **44.5 / 59** | **0.618 / 0.603** |
| the same, the parts at a resolution of 0.75 | 4–7 / 5–6 | 0 of 29 / 0 of 29 | 304 / 316 | 52 / 63.5 | 0.576 / 0.585 |
| the same, the parts at 0.5 | 3–4 / 4 | 0 of 19 / 0 of 20 | 340 / 323 | 87 / 66 | 0.523 / 0.477 |
| Louvain's last level as the parts, the level before it as the clusters | 5–12 / 7–9 | 16 of 40 / 6 of 39 | 1,398 / 1,236 | 131 / 195 | 0.618 / 0.603 |
| Louvain's last level as the parts, its first as the clusters | 5–12 / 7–9 | 10 of 40 / 6 of 39 | 1,167 / 766 | 21 / 26 | 0.618 / 0.603 |

- **Louvain's levels divide nothing that needs dividing.** Over a ten-neighbour graph its last pass merges little: in the topics of 3,000 survivors and more the level before the last held 13 to 23 communities where the last held 10 to 14, so up to 84% of the parts held one cluster, and the largest cluster stayed as large as the flat one. Its first level left one community of 339 to 1,167 survivors in the topics of 3,000 and more.
- **A resolution under 1.0 makes fewer, larger parts** and larger clusters within them, and agrees less with the subjects at 300 to 1,000 survivors; at 5,592, 0.75 agreed a little better (0.703 / 0.666 against 0.652 / 0.650). It is a number of its own, measured on one truth.
- **The topic's own communities as the parts** keep the topic's page as it would be without the level, entry for entry, and bring the median cluster to 44.5 and 59 at 5,592, from 293.5 and 418.

With the topic's own communities as its subtopics, through the shipped classes, on the 121 topics:

| Survivors | Subtopics | Clusters in a subtopic | Subtopics of one cluster, Qwen / nomic | Survivors in a median cluster | Topics whose median cluster is 32 or fewer, Qwen / nomic | Share of the median cluster a synthesis call reads |
| --- | --- | --- | --- | --- | --- | --- |
| 200 | 3 to 7 | 1 to 5 | 6 of 65 / 2 of 55 | 11–17 | 12 of 12 / 12 of 12 | 1.00 |
| 300 | 4 to 7 | 1 to 6 | 3 of 69 / 2 of 64 | 11.5–21 | 12 / 12 | 1.00 |
| 500 | 4 to 9 | 1 to 7 | 5 of 83 / 2 of 74 | 15–26 | 12 / 12 | 1.00 |
| 1,000 | 6 to 12 | 1 to 8 | 2 of 108 / 1 of 92 | 20–38 | 12 / 11 | 0.68–1.00 |
| 1,500 | 6 to 14 | 1 to 8 | 2 of 124 / 1 of 105 | 21–42.5 | 11 / 7 | 0.61–1.00 |
| 2,000 | 8 to 13 | 1 to 10 | 0 of 83 / 2 of 83 | 27–45.5 | 4 of 8 / 2 of 8 | 0.57–0.96 |
| 5,592 | 12 to 14 | 3 to 13 | 0 of 12 / 0 of 14 | 44.5–51 | 0 of 1 / 0 of 1 | 0.51–0.58 |

- **A subtopic of one cluster** occurs in 1% to 9% of subtopics at 200 to 1,500 survivors, and in 2 of 166 at 2,000, its survivors forming one community when clustered alone.
- **The clusters within subtopics are finer than the topic's** and, measured against the subjects, agree with them a little less (mean NMI 0.37 to 0.62 against 0.38 to 0.69), as finer parts of a subject do.
- **Cost.** The passes within the subtopics took 21 and 16 seconds over the 121 topics, where the pass over each whole topic took 139 and 105, on one thread with the vectors resident: a seventh more. No embedding call and no model call is made.

**Not measured**: the shipped 8B embedder; survivors of many chunks, whose mean vectors may lie closer together than one article's; topics past 5,592 survivors; operator archives, which no agent reads; how a person reads a page, which the literature and a synthesis call's reach stand in for. The subjects are Reuters' categories, and the agreement figures are read against that truth.

## Decision

### 1. A topic of 300 survivors or more is crowded

**A topic has subtopics where at least `vespera.clustering.subtopic-floor` survivors are placed under it**, 300 as shipped, **and its survivors form two communities or more** at clustering's neighbours and resolution (§2). The survivors counted are those clustering reads for the topic: its placed survivors that stand under the run (ADR-396 §3). A topic under the floor, or whose survivors form one community, has no subtopics, and its clusters are found over it whole, as ADR-396 §3 has them.

300 is the smallest count measured at which every topic, of every kind and under both embedders, had a median cluster of more than 32 survivors when clustered whole: more than the 16 to 32 entries a level of links reads best at, and more than a synthesis call reads at the shipped window, 15 to 39 of the probe's articles. Under the floor a topic's clusters are mostly small enough to be read whole, and the level would add a page to every path for nothing; at the floor, its subtopics brought every topic measured back to a median cluster of 21 or fewer, and to 38 or fewer up to 1,000 survivors. The topic's page of clusters, which the issue asked to measure against, never needed it: it listed 2 to 14 at every size.

### 2. A crowded topic's subtopics are its communities, and its clusters are found within each

**A crowded topic's subtopics are the communities `Communities.of` finds over the k-nearest-neighbour graph of all its survivors' mean vectors**, at clustering's neighbours (`vespera.clustering.neighbours`, 15) and resolution (`clusterResolution`, else `vespera.clustering.resolution`, 1.0): the communities ADR-396 §3 would have made its clusters. **Its clusters are then found within each subtopic**, by the same method at the same two numbers, over a graph of that subtopic's survivors alone. No number is added to find either.

- **Every survivor lies in one subtopic and one cluster** of it (ADR-105). A subtopic whose survivors form one community is one cluster, and is still a subtopic: every cluster of a crowded topic lies one level down, so that the topic's page lists subtopics alone.
- **A subtopic's survivors are visited in the order the topic's are**, the clustering step's (ADR-087), and its clusters are numbered from 0 within it, in the order of first appearance, as `Communities` numbers them. A subtopic is numbered within its topic the same way.
- **The topic's page shows what it would show without the level**: the same communities, as subtopics. A topic crossing the floor keeps its entries and gains clusters beneath each.

### 3. What is refused

- **A coarser resolution for the subtopics.** At 0.5 and 0.75 it made fewer and larger subtopics, larger clusters (a median of 52 to 87 at 5,592, against 44.5 to 59), and agreed less with the subjects at 300 to 1,000 survivors; and it would be a fourth number of the clustering step, measured on one truth, which *observe before enforce* refuses where none is needed.
- **The Louvain levels of one pass.** The level before the last is nearly the last: up to 84% of the subtopics held one cluster, and the largest cluster stayed at 1,236 to 1,398 survivors at 5,592. The first level left one of 339 to 1,167 in topics of 3,000 and more. Either would also need `Communities` to return levels it does not, and its clusters would not be ADR-087's.

### 4. The order

**Subtopics go by the number of survivors in each, most first, two of as many by their number within the topic** (§2). It is the rule ADR-396 §4 gives the topics, and ADR-112 gave its top level, for ADR-112's reason: size says which part of the topic is substantial. ADR-112's rule for clusters, two tiers and the mean relevance score, is not taken: a subtopic's survivors were scored against different seeds, whose mean carries the bias of a seed's chunk count ADR-112 refused to compare across partitions (ADR-396, *What this does not decide*), and its tiers set apart clusters of one survivor, of which none occurred.

**Within a subtopic, the clusters go by ADR-112's rule**, as a topic's clusters do where it has no subtopics. Where a topic's clusters are taken in one sequence, as by their number among the topic's clusters ([ADR-402](0402-a-topics-overview-cites-its-written-clusters-by-number-in-every-sentence-and-a-citation-fault-is-asked-once-more.md)), they are taken subtopic by subtopic in this order, each subtopic's in ADR-112's.

### 5. Where it runs, and which run the floor names

**The subtopics are found by the clustering step**, stage 5f, under the embedding-scoring run, where the topic's clusters are found: once a topic's surviving members are read, they are divided by §1 and §2 before the next topic is read. No step is added. The topic's members are held as they are now (ADR-211 §5); each subtopic's graph is built over the members of that subtopic in ADR-087's blocks, one subtopic at a time, so nothing held grows beyond a number per survivor (ADR-085, ADR-220).

| Preset | Ships | Profile key |
| --- | --- | --- |
| `vespera.clustering.subtopic-floor`, the survivors a topic must hold to be divided into subtopics | `300` | none |

It is a preset under ADR-330 §8, beside clustering's two, with no profile key: how the arrangement divides is not the operator's to set (ADR-396 §1, §3), and a machine may name another value. A value no run can be made under stops the invocation in one line, as ADR-330 §9 refuses every preset's: `vespera.clustering.subtopic-floor is <value>: a whole number from 1 to 2147483647`. At 1, every topic of two communities or more has subtopics.

**The floor enters the embedding-scoring run's identity** as the member `subtopicFloor`, a whole number, appended to its configuration after the members before it, `@JsonInclude(NON_NULL)` and written only where the value resolved is not `300`, as ADR-330 §9 writes every preset's. At the shipped value no member is written, so its configuration's text stays what `RunIdentityGoldenTest` holds.

**What must be stored**, for each clustered survivor under the embedding-scoring run: its subtopic's number within its topic, where its topic has subtopics, beside its cluster's number, now within that subtopic. The table, its key and how a re-run discards it are [#832](https://github.com/algernon28/vespera/issues/832)'s.

### 6. The word is subtopic

**The level between a topic and its clusters is a *subtopic*.** It says what it is, a part of a topic, and reads as one in the arrangement's path, topic › subtopic › cluster. It is no word built on "group", which a reader outside the project reads as a cluster (ADR-122), and none `CONTEXT.md` already rejects for something else: *section* names no leg, *theme* is a cluster's refused word. Our names say it: `Subtopic`, `subtopicFloor`, `subtopic-floor`.

`CONTEXT.md` gains a **Subtopic** entry, its `_Avoid_` line opening with *sub-group*, the word map #822 called it by. **Topic**, **Cluster** and **Arrangement** say that a crowded topic's clusters lie within its subtopics.

Where a subtopic's name falls back to its place (ADR-407 §7), it is `Subtopic n` in English and `Sottoargomento n` in Italian, `n` being its place among its topic's subtopics in §4's order, counted from 1, as a topic's is `Topic n` and `Argomento n`. The vector ADR-407 §4 sends a subtopic's survivors' leading chunks in the order of is each survivor's mean vector, as §2 forms subtopics over.

### 7. What the implementation owes

- In `embedding`: `TopicDivision(List<Integer> subtopicOf, List<Integer> clusterOf)`, `subtopicOf` empty where the topic has no subtopics and `clusterOf` numbered within the subtopic where it has; its `hasSubtopics()`; its `subtopicsInOrder()`, §4's order of the subtopics' numbers, empty where there are none; and `TopicDivision.of(NearestNeighbourGraph.MeanVectors topic, int neighbours, double resolution, int subtopicFloor, int blockSize)`, building one graph over the topic and one over each subtopic, the subtopic's mean vectors read as a view of the topic's in its order. `Clustering.clusterAndRecord` divides each topic through it and records the subtopic where #832 says.
- In `pipeline`: `Presets.subtopicFloor()` and its refusal in `Presets.refusal`, after clustering's two; the member of the scoring run's configuration; `PresetsFixture` naming the shipped value; and the floor handed to the clustering step.
- `application.yaml` gains `vespera.clustering.subtopic-floor: 300` under `vespera.clustering`, with its comment; the control panel shows it read-only, as ADR-330 §12 shows a preset with no profile key.
- ADR-407's naming of subtopics (§4, §7, §8), when it is built: the third fallback's word, and the subtopics named in §4's order.

## Consequences

- **A topic of 300 survivors or more is three levels deep**, topic › subtopic › cluster, and its clusters are found within its subtopics. On the probe's topics that held their median cluster at 11.5 to 21 survivors at 300, and at 20 to 38 at 1,000, where clustering the whole topic gave 36 to 80 and 71 to 170.
- **A topic's page lists the same entries either side of the floor**: its communities, as clusters under it, as subtopics at it and above.
- **A crowded topic has more clusters, and so more synthesis calls**: four to seven times as many (at 1,000 survivors the probe's topics went from 5–12 clusters to 25–46), each reading what fits the window as before.
- **Clusters still grow with very large topics**: the median reached 44.5 to 51 at 5,592, as a tree held to three levels must. ADR-108's synthesis written from the highest-scoring of a cluster's documents, saying so, still answers it.
- **A subtopic of one cluster occurs**, in 1% to 9% of the subtopics measured below 2,000 survivors; its page lists that one cluster.
- **Clustering a crowded topic costs a seventh more**, and no model call.
- **ADR-330 and ADR-396 carry a note *"Partly amended — see ADR-406"*** at their heads.
- **Run ids**: the embedding-scoring stage's run id moves with the code that builds this record, as ADR-396 §12 says of its children, and the arrangement's and generation's with it. At the shipped value no member is written.

## Tests

| Class | What it holds |
| --- | --- |
| `embedding.TopicDivisionTest.aTopicUnderTheFloorIsClusteredWhole` | ten survivors under a floor of eleven have no subtopics, and their clusters are the whole topic's communities (§1) |
| `…aTopicAtTheFloorHasItsCommunitiesAsSubtopics` | at a floor of ten, the subtopics are those communities, numbered as `Communities` numbers them (§1, §2) |
| `…clustersAreFoundWithinEachSubtopic` | the second subtopic's six survivors, clustered alone, divide into two where the pass over the whole topic kept them one, numbered within it; the first's four stay one cluster and one subtopic (§2) |
| `…aTopicOfOneCommunityHasNoSubtopics` | five survivors of one community at a floor of one have no subtopics (§1) |
| `…subtopicsGoByTheirSurvivorsMostFirst` | subtopics of 1, 2, 3 and 2 survivors go 2, 1, 3, 0 (§4) |
| `…aTopicWithNoSubtopicsListsNone` | a topic under the floor has no order of subtopics (§4) |
| `pipeline.TheSubtopicFloorPresetTest.shipsTheMeasuredValue` | `application.yaml` names a floor of 300 (§5) |
| `pipeline.TheSubtopicFloorPresetTest.refusesValuesNoRunCanBeMadeUnder` | 0 and 2.5 are refused, each in its line, and 1 is not (§5) |
| `TheVocabularyNamesTheSubtopicTest.subtopicIsATerm` | `CONTEXT.md` has a Subtopic entry whose `_Avoid_` line opens with sub-group and names group (§6) |
| `TheVocabularyNamesTheSubtopicTest.noAvoidLineRejectsSubtopic` | no `_Avoid_` line rejects subtopic (§6) |

`RunIdentityGoldenTest` holds, unchanged, that at the shipped value no member is written. The member written where the floor is not 300, the step's division of each topic, and the fallback's word are held by the tests the implementation adds beside `TheClusteringPresetsInvocationTest`, `ClusteringInvocationTest`, and beside the test of ADR-407's fallback name.

## What this does not decide

- **Where a survivor's subtopic is stored**, the key of a subtopic and of a cluster within it, and how a re-run discards them: #832.
- **How the three renderings draw a subtopic**, its folder and page, and what `cluster-sizes.html` shows of it: [#833](https://github.com/algernon28/vespera/issues/833) and [#834](https://github.com/algernon28/vespera/issues/834).
- **Whether a subtopic gets an overview of its own.** ADR-396 §7 gives one to a topic; none is given here.
- **Whether the clusters within a topic read the survivors it is linked from** (ADR-400): they do not, as ADR-396 §3 has it, and the subtopics are found over the placed survivors alone.
- **The floor under the shipped 8B embedder, and for survivors of many chunks**, which nothing here measured.
