# ADR-396 — Topics inferred from the seed set's passages replace seed partitions, and a survivor is placed apart from its score

> **Partly amended — see [ADR-406](0406-a-topic-of-300-survivors-or-more-is-divided-into-subtopics-its-own-communities-and-its-clusters-are-found-within-each.md).** Of §3: a topic of at least `vespera.clustering.subtopic-floor` survivors, 300 as shipped, whose survivors form two communities or more, has subtopics, the level between it and its clusters: they are the communities clustering finds over the whole topic, and its clusters are found within each subtopic, over that subtopic's survivors alone. Subtopics go by their survivors, most first. Of §10: the level's term is *subtopic*. The rest of §3, and of this record, stands.

> **Partly amended — see [ADR-399](0399-topics-are-modularity-communities-over-a-ten-neighbour-graph-of-the-seed-passages-keyed-by-their-first-passage.md).** Of §8's table: what the stable topic key is, is ADR-399's, the seed path and chunk index of a topic's first passage in §9's order; #832 keeps the table it is stored in and the run it is written under.

> **Partly amended — see [ADR-402](0402-a-topics-overview-cites-its-written-clusters-by-number-in-every-sentence-and-a-citation-fault-is-asked-once-more.md).** Of §8's table: the topic overview's row is settled there: what it reads, the numbers it cites and the check, its 150 words, the two questions, the placeholders and their Italian, and the shape of its rows, whose tables stay #832's.

> **Partly amended — see [ADR-407](0407-topics-are-named-at-the-head-of-the-arrangement-step-by-a-naming-model-of-their-own-from-their-strongest-passages.md).** Of §12: *"The walk, stages 1 to 4 and seed measurement do not move"* does not hold of every child. A stage's version is the last commit touching the modules its row of ADR-058's table names, so a child whose code touches `embedding` moves seed measurement, and one touching `extraction` or `similarity` moves stages 2 to 4, and with them every run downstream. Each child says which runs its own code moves; ADR-407's moves every run but byte-level reduction's.

- **Date**: 2026-11-15
- **Status**: accepted on 2026-11-15.
- **Decided**: by the operator, in map [#822](https://github.com/algernon28/vespera/issues/822): that the top level of the arrangement is a set of topics found across the seed set's passages, so that one seed may feed several and several seeds one, how many being inferred from the content under a measured floor on a topic's breadth that ships as a preset, and a seed feeding no topic being reported (§1); that every survivor is placed, under one topic, copied once, every other topic it fits linking to it, and that a topic no survivor is placed under has no folder and is reported (§2); that clustering runs within a topic as it ran within a seed partition, a crowded topic getting at most one level more, the tree kept as shallow as the document count allows (§3); that the operator neither approves nor edits the topics, which the 6a gate shows read-only, generated before it (§5); that the local model names a topic in the deliverable language, the Docling title of its strongest contributing seed standing in where the model fails (§6); that each topic has a generated overview at 6b, and still no corpus-wide one (§7); that the same seed set, models and configuration give the same topics every run (§9); and that "topic" leaves the `_Avoid_` lists and "seed partition" retires (§10). By the operator, in [#823](https://github.com/algernon28/vespera/issues/823): option C, the seeds scoring and the topics placing (§2). By the operator, in [#830](https://github.com/algernon28/vespera/issues/830): that the overview cites the topic's clusters by ordinal, checked as ADR-109 checks a cluster's citations, and that an overview failing its check is asked for once more, then replaced by a placeholder, the fault recorded (§7). By the operator, in [#831](https://github.com/algernon28/vespera/issues/831): that the local model names the level between a topic and its clusters as it names a topic, and that the 6a gate shows that level read-only, as it shows the topics (§3, §5). By the operator, in #822 and #830: that a topic's overview is read off its clusters' synthesis docs and cites the clusters, synthesis over synthesis being accepted for it (§7). Under the operator's delegation, in #822: the order of the top level (§4), and that pages already published follow ADR-328 (§11). Under the operator's delegation, in [#825](https://github.com/algernon28/vespera/issues/825): how far each record below is amended, and the wording of the vocabulary (§10).
- **Amends**: [ADR-020](0020-relevance-scoring-function.md), its *"winning seed stored"*: the winning seed is the score's provenance and no longer places a survivor (§2). It carries a note at its head.
- **Amends**: [ADR-022](0022-stage-6-splits-into-arrangement-6a-then-generation-6b.md), its *"seed-named top level"*: the top level is the topics (§1). The 6a/6b split and the gate between them stand. It carries a note at its head.
- **Amends**: [ADR-027](0027-clustering-moves-to-stage-5.md) and [ADR-045](0045-clustering-runs-within-each-seed-partition.md): clustering runs within each topic, and never corpus-wide (§3). Each carries a note at its head.
- **Amends**: [ADR-106](0106-a-cluster-gets-a-derived-label-from-6a-and-a-generated-title-from-6b.md), its Consequences' *"No model runs before the human gate"*: the local model names the topics before it (§5). It carries a note at its head.
- **Amends**: [ADR-112](0112-the-arrangement-is-ordered-by-partition-size-and-cluster-mean-score-and-the-path-carries-the-order.md), its *"Seed partitions sort by document count, descending"* and its *"The path carries the order"* for the top level: topics sort by document count, ties broken on a stable topic key, and a top-level folder is no longer named after a seed (§4). It carries a note at its head.
- **Partly reopens**: [ADR-113](0113-no-corpus-wide-synthesis-doc-sits-above-the-seed-partitions.md), for topics only: each topic has a generated overview (§7). No corpus-wide synthesis doc sits above the topics. It carries a note at its head.
- **Keeps**: [ADR-105](0105-stage-6a-names-the-arrangement-stage-5-already-built-and-unattributed-is-struck.md): the arrangement is total, every survivor placed, 6a naming and ordering what stage 5 built and writing no verdict, and nothing is unattributed (§2).
- **Keeps**: [ADR-280](0280-a-clusters-folder-holds-its-page-and-a-copy-of-each-of-its-survivors-under-the-originals-own-name-suffixed-only-on-a-collision.md): a survivor is copied once, into the folder of its one cluster (§2).
- **Keeps**: ADR-020's score, the maximum over seeds of the mean of the top chunk similarities, and the relevance floor over it; [ADR-097](0097-a-relevance-label-is-keyed-by-the-documents-path-and-the-seed-set-not-by-the-occurrence.md)'s `relevance-labels.yaml` and its key, the path and the seed set; [ADR-197](0197-a-local-model-answers-the-relevance-questions-and-the-floor-is-a-rule-over-the-labels-so-no-document-reaches-a-hosted-model.md)'s `closestSeed`; and [ADR-086](0086-seed-corpus-mismatch-is-measured-before-the-model-gate-and-reported-never-enforced.md)'s seed-corpus comparison (§2).
- **Keeps**: [ADR-087](0087-clusters-are-modularity-communities-over-a-k-nearest-neighbour-graph-built-in-blocks.md): a cluster is a modularity community over a k-nearest-neighbour graph, now of one topic's survivors (§3).
- **Keeps**: [ADR-328](0328-the-confluence-rendering-is-one-tree-updated-in-place-by-each-publish-its-pages-keyed-and-titled-by-their-place-with-no-run-id-and-a-page-it-no-longer-has-moved-to-the-trash.md): a publish updates the one tree in place, and a page it no longer has goes to the trash (§11).
- **Keeps**: [ADR-264](0264-the-private-paths-guard-refuses-only-what-reads-or-writes-outside-the-allow-list-and-a-working-directory-is-open-as-the-place-it-lies-in.md): every measurement a later child makes is on synthetic or public text, and no agent reads a seed document to judge a topic.
- **Rests on**: the comparison of [#823](https://github.com/algernon28/vespera/issues/823), measured on public text, below.
- **Settles**: [#825](https://github.com/algernon28/vespera/issues/825).

## Context

ADR-020 scores a survivor's relevance as the maximum over seed documents and stores the seed that won. ADR-045 runs clustering within each seed partition, the survivors sharing a winning seed, and ADR-022 and ADR-112 make those partitions the top level of the arrangement, one for each seed that won a survivor, ordered by size and named after the seed's file. The top level is therefore the operator's seed files, one for one.

A seed set is not written as a list of subjects. Two seed files may cover one subject, and the survivors that subject wins are split between two partitions by whichever seed scored them a little higher; one seed file may mix three subjects, and the three share a partition and are clustered as one. The deliverable's top level then reads as the seed folder's listing, not as what the archive holds. Map #822 asks for a top level inferred from what the seeds say: topics, found across the seeds' passages.

That raises a question of its own, settled in #823: whether relevance is still scored against the seeds, or against the topics.

### Measured

A probe outside the repository, on public text only; no seed set and no archive was read (ADR-264). The text is Reuters-21578: synthetic seed files built from training-split articles, one article a passage, and a corpus of the test split's 574 articles on agricultural commodities and energy and 1,200 others, 1,774 documents, "relevant" meaning tagged with one of those categories. Set 1 is 7 seeds and 73 passages, set 2 16 seeds and 54 passages. The embedder is Qwen3-Embedding-0.6B (Q8_0) through a local Ollama on CPU. Topics are found as stage 5 finds clusters (ADR-087): a k-nearest-neighbour graph weighted by cosine, k = 15, Louvain at resolution 1.0 with no random seed, and a floor dropping any topic under 5% of the seed passages; each set gives 3 topics. A is ADR-020 as shipped; B1 the same function taken per topic, maximum over topics; B2 the cosine to each topic's centroid; C scores by A and places by topic. The operator's floor is stood in for by A's best-F1 cut-off.

| | AUC, set 1 / set 2 | AP, set 1 / set 2 |
| --- | --- | --- |
| A, seeds (ADR-020) | 0.939 / 0.943 | 0.910 / 0.896 |
| B1, topics, top 3 | 0.945 / 0.946 | 0.921 / 0.904 |
| B2, topic centroids | 0.951 / 0.947 | 0.929 / 0.916 |

Paired bootstrap intervals (95%, 1,000 resamples) put the gain of B over A at 0.3 to 1.2 points of AUC and 0.8 to 2.0 of AP, and not every interval excludes zero.

| What changes | Relevance verdicts that move, of 1,774 |
| --- | --- |
| B1 at A's floor | 49 on set 1, 218 on set 2 (precision 0.829 → 0.705) |
| B2 at A's floor | another scale: 913 and 1,338 pass, at precision 0.60 and 0.42 |
| resolution 0.5, 1.5 or 2.0; k = 10 or 5 | 0 under A and C; up to 79 under B1 and 98 under B2 |
| one seed removed | A 1 to 17; B1 6 to 28 |
| the passages visited in reversed or shuffled order | 0 under A; 2 to 19 under B1, and set 2's 3 topics become 4 |

A subject with one seed passage and 33 corpus documents is recalled at 0.42 and 0.88 under A, 0.27 and 0.79 under B1, 0.52 and 0.58 under B2: under A a one-article seed keeps a maximum of its own.

| Placement of the relevant documents | NMI, set 1 / set 2 | homes, set 1 / set 2 |
| --- | --- | --- |
| winning seed (ADR-020) | 0.466 / 0.390 | 7 / 15 |
| winning topic, top 3 | 0.474 / 0.403 | 3 / 3 |
| winning topic, centroid | 0.543 / 0.476 | 3 / 3 |

Under the winning seed, the grain articles of set 1 spread over 6 seeds; under a winning topic, 102 of 105 lie in one. No option adds an embedding call or a model call: the topics are formed from the seed chunks stage 5 already embeds, and placement by the top 3 is read in the scoring pass.

**Not measured**: the shipped 8B embedder; long documents of many sections; more than two seed sets or more than three topics; operator labels and the operator's own floor, which the Reuters categories and a best-F1 cut-off stand in for. The ranking differences are within what another embedder or corpus could reverse. The stability differences are structural.

## Decision

### 1. The top level is the topic

The top level of the arrangement is a set of **topics**, found across the passages of every seed rather than taken one per seed file: one seed may feed several topics, and several seeds one. How many there are is inferred from the passages, never set by the operator.

A topic's **breadth** is its share of all seed passages, and a topic under a measured floor on it is not a topic. A seed all of whose passages fall under the floor feeds no topic, and is reported. The floor ships as a preset (ADR-330), changeable in the control panel's Command options, and is no profile key.

### 2. Seeds score, topics place

**Relevance stays ADR-020's.** A survivor's score is the maximum over seeds, its winning seed stored as the score's provenance; the relevance floor, `relevance-labels.yaml` and its key (ADR-097), `closestSeed` (ADR-197) and the seed-corpus comparison (ADR-086) are unchanged, so the floor the operator chose and the labels given keep their meaning.

**Placement is a step of its own, after the score.** It gives each survivor its **winning topic**, the one topic it is arranged, clustered and copied under. The winning seed places nothing.

- **Every survivor is placed** (ADR-105): one no topic fits well goes to its nearest topic.
- **One home**: a survivor lies under its winning topic alone and is copied once (ADR-280). Every other topic it fits carries a "see also" link to it.
- **A topic no survivor is placed under** has no folder, and is listed, with the seeds that feed it, in a report.

### 3. Clustering runs within a topic

Stage 5's clustering (ADR-087) runs over the survivors of one winning topic, as it ran over one seed partition, and never over the corpus whole (ADR-045). A crowded topic gets at most one level more, between the topic and its clusters, so that the tree is at most three levels deep: topic, that level, cluster. The tree is kept as shallow as the document count allows. The local model names each part of that level from its content, in the deliverable language, under the rules that name a topic (§6, §9), and before the 6a gate, which shows it read-only (§5).

### 4. The order of the top level

**Topics sort by the number of survivors placed under them, descending, ties broken on a stable topic key.** The key survives a re-walk, so it is never an occurrence id (ADR-112's reason), and it is never the topic's generated name, which a change of naming model may change while the topics stand. A top-level folder keeps ADR-112's order prefix and its padding, padded to the topic count. It is no longer named after a seed's filename stem.

### 5. The 6a gate

**A model runs before the gate**: the local model names the topics, and the parts of the level between a topic and its clusters, before it (§3, §6). The gate stays, and what the operator approves there is the arrangement of the clusters, shown as topic › that level › clusters. The topics, that level and their names are shown at the gate read-only; the operator neither approves, edits nor renames them, as ADR-106 already keeps a cluster from being renamed there. A cluster's label is still derived by 6a, and no model runs before the gate for a cluster.

### 6. Names

The local model names each topic, in the deliverable language (`deliverableLanguage`, ADR-321). Where the model fails, the topic takes the Docling title of its strongest contributing seed. A name is stored under its run and never generated again for that run, and it is a rendering, never the topic's identity.

### 7. The topic overview

**Each topic has a short generated overview, written at 6b, summarising its clusters' synthesis docs.** In the HTML tree it is the topic's page; in the Confluence rendering it sits on the topic's page, above the table of its clusters.

This reopens ADR-113 for topics only, and no corpus-wide synthesis doc sits above the topics. ADR-113 refused a generated document above the seed partitions for three reasons. Its third, that the top level is the operator's own structure told back to them, does not hold of a topic, which the operator does not name. Its first, that a call sending no document has no exemplar a citation can name (ADR-109), is answered by what an overview cites:

- **An overview cites the topic's clusters, by ordinal**, `[n]` with `1 ≤ n ≤` the topic's cluster count, checked in range as ADR-109 checks a cluster's citations, each citation linking to that cluster's page. A survivor stays listed and linked on its cluster's page, one link further on, so every sentence of the overview traces to a cluster whose page traces to its documents.
- **An overview that fails the check**, a sentence citing nothing or a citation out of range, **is asked for once more.** The second call differs from the first, as by naming what failed, since at temperature 0 and a fixed seed the same request returns the same answer; it is as deterministic as the first, and both enter the run id.
- **Where the second fails too**, the topic's page carries a placeholder overview in the deliverable language, its table of clusters drawn as usual, and the failure is recorded as a fault (ADR-111), keyed by the topic.

Its second, that an overview read off synthesis docs is synthesis over synthesis, holds of a topic's overview, and is accepted for it: the overview reads its clusters' synthesis docs, and cites the clusters, whose pages carry the documents. The refusal stands for a corpus-wide document, for which ADR-113's first reason stands too.

### 8. What later children decide

Each value below is left to the child of #822 named beside it, and none is chosen here.

| Value | Child |
| --- | --- |
| how topics are found over the seed passages: the inference method and its parameters, in §9's order | [#826](https://github.com/algernon28/vespera/issues/826) |
| the breadth floor's value, measured on synthetic or public text, and its preset | [#827](https://github.com/algernon28/vespera/issues/827) |
| the placement function that gives the winning topic, the topic centroid that #823 measured placing best being the candidate and not a decision, and the threshold over which a survivor fits another topic, its "see also" | [#828](https://github.com/algernon28/vespera/issues/828) |
| the topic names: the call, the prompt, what the strongest contributing seed is, and where a name is stored | [#829](https://github.com/algernon28/vespera/issues/829) |
| the topic overview: what it reads, and the range a citation is checked against, where some of its topic's clusters have no synthesis doc; its word budget, the wording of the second call and of the placeholder, their Italian rendering (ADR-321), and the fault's table (§7) | [#830](https://github.com/algernon28/vespera/issues/830) |
| the level between a topic and its clusters: when a topic is crowded, how the level forms, its order, and its term | [#831](https://github.com/algernon28/vespera/issues/831) |
| the ledger's tables for topics, placement and names, the stable topic key, and which run each is written under | [#832](https://github.com/algernon28/vespera/issues/832) |
| the reports: seeds feeding no topic, topics with no survivor, and cluster sizes per topic | [#834](https://github.com/algernon28/vespera/issues/834) |
| the three renderings at three levels: the HTML tree, the Confluence rendering and the website rendering, a topic's folder and what it is named from | [#833](https://github.com/algernon28/vespera/issues/833) |

### 9. The same seeds give the same topics

The same seed set, models and configuration give the same topics, names and placement every run.

- **Topics are found in the order of each seed's path relative to the seed folder, then of each chunk's index.** Louvain's result depends on the order it visits the passages (Measured), and an occurrence id is per walk, so no other order survives a re-walk.
- **A seed renamed or moved may re-form the topics.** It is a change of input, as a seed added or removed is.
- **The naming model is sent temperature 0 and a seed, and both enter the run id** of the run the names are written under.
- **A change of embedder or of naming model may re-form or rename the topics.**

### 10. The vocabulary

`CONTEXT.md` gains a **Topic** entry and a **Winning topic** entry. "Topic" leaves the `_Avoid_` lists. **Seed partition** and **Seed partition's page** are retired, each saying what took its place. **Seed set** no longer names the top level of the arrangement: its passages are what the topics are inferred from. **Cluster** and **Arrangement** are scoped to a topic, and with them **Cluster folder** and **Confluence rendering**, which named a seed partition; **Cluster label** identifies a cluster by its ordinal and no longer says within what, the key being #832's. **Seed/corpus mismatch** says *subject* where it said *topic* in the everyday sense, so that the word names the term alone.

The level between a topic and its clusters is given no term here. "Group" is a cluster's rendering for a reader outside this project (ADR-122), so a word built on it would read as a cluster there; #831 names it.

### 11. Publishing

A publish follows ADR-328 unchanged: the Confluence tree is updated in place, each page keyed and titled by its place, and a page the rendering no longer has goes to the space's trash. The first publish after the top level becomes topics trashes each seed partition's page and adds each topic's.

### 12. Run ids

This record changes no code, so no run id moves with it. The children that build it move the ids of the embedding-scoring stage, under whose run the topics and the placement are written, of the arrangement and of generation. The walk, stages 1 to 4 and seed measurement do not move. Under its new id, the embedding-scoring stage writes the scores and the relevance verdicts it wrote before, ADR-020 being unchanged.

## Alternatives refused

- **Scoring relevance against the topics** (B1 or B2 of #823). It buys 0.3 to 1.2 points of AUC and 0.8 to 2.0 of AP, not every interval excluding zero. It costs a floor chosen again from another distribution, up to 98 of 1,774 relevance verdicts moved by any change of the topic inference or of the order the passages are visited in, and the relevance of a thin subject, or of a passage under the breadth floor, lost outright.
- **The operator approving or editing the topics.** The topics are inferred so that the top level reads as what the seeds say; topics the operator edited would be the operator's own structure again, held in a derived row set that a re-run either overwrites or refuses to (ADR-106).
- **A corpus-wide overview above the topics.** ADR-113's three reasons all hold of it, and it stays refused.

## Consequences

- **The deliverable's top level reads as the seeds' subjects**, not as the seed folder's listing: two seed files on one subject feed one topic, and one seed on three subjects may feed three.
- **A survivor's winning seed and its winning topic are two facts.** The first explains its score, the second its place; a seed's influence over the corpus is still a question anyone can ask.
- **The deliverable gains prose that cites clusters and not documents**: a topic's overview. ADR-113's invariant that every prose sentence traces to a document holds of it one link further on, through the cited cluster's page, and a topic whose overview fails twice shows a placeholder and no generated prose.
- **The 6a gate is no longer model-free.** An arrangement cannot be shown at the gate without the local model having named its topics and the level between, answering, where before it needed none (ADR-106's Consequences); a failed name falls back to a seed's Docling title, so the gate is never without a name.
- **A seed renamed or moved may change the top level**, which under ADR-112 it changed only by its folder's name and its place in the order.
- **ADR-020, ADR-022, ADR-027, ADR-045, ADR-106 and ADR-112 carry a note *"Partly amended — see ADR-396"*** at their heads, and ADR-113 a note *"Partly reopened — see ADR-396"*. The reconstituted records keep their summaries verbatim.
- **What describes the seed partition today describes it until its child is built**: the code, its names, from `partition_order` on, `documents.csv`'s `seed_partition` and `partition_order` columns, which ADR-112 and ADR-122 fix verbatim, `AGENTS.md`, `README.md` and `docs/architecture.md`. Each is brought to this record by the child that rebuilds it, the documents by [#835](https://github.com/algernon28/vespera/issues/835).

## Tests

| Class | What it holds |
| --- | --- |
| `TheVocabularyNamesTopicsAndRetiresTheSeedPartitionTest.topicIsATermAndNoAvoidLineRejectsIt` | `CONTEXT.md` has a Topic entry and a Winning topic entry, and no `_Avoid_` line lists "topic" as a rejected word (§10) |
| `TheVocabularyNamesTopicsAndRetiresTheSeedPartitionTest.noDefinitionButTheRetiredOnesNamesASeedPartition` | no entry's definition in `CONTEXT.md` names a seed partition but the two retired entries', and each of those opens by saying this record retired it (§10) |

Nothing else this record decides is built yet, and each child's record names the tests that hold its part.

## What this does not decide

- **Every value §8 lists**, each its child's.
- **The order of the clusters within a topic.** ADR-112's rule stands, two tiers and each by its members' mean score, but its premise does not: within a seed partition every score was taken against one seed, and within a topic the members' winning seeds differ, which ADR-112 refused to compare across partitions for the bias a seed's chunk count gives. No child of #822 is charted for it.
- **The key of 6a's cluster row**, which ADR-105 gives as the run, the winning seed and the ordinal. A cluster is now within a topic; the key is #832's, and with it whether ADR-105 is amended.
- **Whether the deliverable language enters the identity of a run before generation.** ADR-321 has it join the generation run's identity, and §6 has the names written in it before the gate. #829 and #832 settle it.
- **How ADR-045's alarm on a seed partition holding 60% of the corpus reads for a topic**: [#834](https://github.com/algernon28/vespera/issues/834).
