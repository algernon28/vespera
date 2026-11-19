# ADR-399 — Topics are modularity communities over a ten-neighbour graph of the seed passages, keyed by their first passage

> **Partly amended — see [ADR-428](0428-the-presets-with-no-profile-key-are-shown-read-only-in-command-options-a-list-for-each-run-they-enter.md).** Of §7, as ADR-411 left it: the control panel shows `vespera.topics.neighbours` and `vespera.topics.resolution` read-only in Command options, so *"the control panel shows them read-only, as ADR-330 §12 shows a preset with no profile key"* holds again. The rest of this record stands.

> **Partly amended — see [ADR-411](0411-adr-399-to-401-move-seed-measurement-and-adr-406s-floor-is-not-shown-in-the-control-panel.md).** Of §7: the control panel does not show `vespera.topics.neighbours` or `vespera.topics.resolution`, and *"the control panel shows them read-only, as ADR-330 §12 shows a preset with no profile key"* is not part of what lands; that showing, which ADR-330 §12 owes for every preset with no profile key, is [#855](https://github.com/algernon28/vespera/issues/855)'s. Of Consequences, its *Run ids*: the code that builds this record touches `embedding`, so it moves seed measurement as well as embedding scoring, the arrangement and generation (ADR-058, `StageModules`); *"The walk, stages 1 to 4 and seed measurement do not move"* reads *"The walk and stages 1 to 4 do not move"*. Of its first Amends line: *"table of the members a run's configuration gains gains"* reads *"table of the members of a run's configuration gains"*. The rest of this record stands.

- **Date**: 2026-11-15
- **Status**: accepted on 2026-11-15.
- **Decided**: by the operator, in map [#822](https://github.com/algernon28/vespera/issues/822), as [ADR-396](0396-topics-inferred-from-the-seed-sets-passages-replace-seed-partitions-and-a-survivor-is-placed-apart-from-its-score.md) records it: that topics are found across the seeds' passages, how many inferred, the same seeds giving the same topics, in the order of each seed's path then each chunk's index (ADR-396 §9), a seed renamed or moved being a change of input (§2, §4). By the operator, in [#826](https://github.com/algernon28/vespera/issues/826): that the topics' two numbers ship as presets (§3); that the topic key is decided here (§5); and that a seed of the same content as another feeds the topics once, relevance unchanged (§1). Under the operator's delegation, in #826: what a passage is, what the same content is and which of two seeds of it feeds (§1), the method (§2), the two numbers' values (§3), how §9's order compares two paths (§4), what the key is (§5), and where the step runs and which run its numbers enter (§6).
- **Amends**: [ADR-330](0330-the-control-panel-edits-every-value-of-profile-yaml-showing-the-value-in-use-and-where-it-came-from-and-ten-technical-choices-ship-as-presets-in-application-yaml.md) §8 and §9, whose tables of presets gain two rows, `vespera.topics.neighbours` and `vespera.topics.resolution`, §9's sixteen keys becoming eighteen, and whose table of the members a run's configuration gains gains two, `topicResolution` and `topicNeighbours` (§3, §6). It carries a note at its head.
- **Amends**: ADR-396 §8, whose table gives the stable topic key to [#832](https://github.com/algernon28/vespera/issues/832): what the key is, is this record's (§5), and #832 keeps the table it is stored in and the run it is written under. It carries a note at its head.
- **Keeps**: [ADR-087](0087-clusters-are-modularity-communities-over-a-k-nearest-neighbour-graph-built-in-blocks.md)'s graph and communities, `NearestNeighbourGraph` and `Communities` as they are, so that no cluster moves (§2); [ADR-085](0085-vectors-live-in-sqlite-and-the-pairwise-matrix-is-never-materialised.md)'s memory contract, the seed side resident and no pairwise matrix stored (§2, §6); [ADR-029](0029-chunking-structure-first-with-a-measured-llm-fallback.md)'s chunks, `ChunkingRule.DEFAULT` (§1); [ADR-051](0051-a-file-occurrence-is-identified-by-its-path-relative-to-the-corpus-root.md)'s spelling of a path (§4); ADR-020's score, which no topic enters (§6); ADR-396 §1, §4 and §9, whose order this record compares paths in and does not change.
- **Rests on**: the measurements below, on public text and local models only ([ADR-264](0264-the-private-paths-guard-refuses-only-what-reads-or-writes-outside-the-allow-list-and-a-working-directory-is-open-as-the-place-it-lies-in.md)).
- **Settles**: [#826](https://github.com/algernon28/vespera/issues/826).

## Context

ADR-396 makes the top level of the arrangement a set of topics found across the seed set's passages, how many inferred and never set by the operator, the same seeds giving the same topics, and decides the order the passages are visited in, each seed's path relative to the seed folder and then each chunk's index (§9). It leaves to #826 the inference method and its parameters, in that order, and to #832 the stable topic key. The comparison behind ADR-396 found topics with ADR-087's method, a k-nearest-neighbour graph and Louvain's modularity communities with no random number generator, at ADR-087's numbers for clusters, k = 15 and a resolution of 1.0, on two seed sets of 54 and 73 passages; it measured that the order the passages are visited in changes the topics, and nothing else of the method.

A seed set is not a corpus. It may be five files or two hundred, one file on fifteen subjects or sixty files on one, tens of passages or thousands, and a seed of one short paragraph beside seeds of fifty chunks. A number chosen for the clusters of one seed partition, which ADR-087 sized at up to 120,000 documents, is not known to suit it.

### Measured

A probe outside the repository, on public text only, with no seed set and no archive read.

- **Text.** Reuters-21578: 2,792 articles of 40 to 512 words carrying exactly one Reuters category, of the 42 categories holding at least ten such articles, `earn` and `acq` sampled to 250 each; the seed sets draw on the 34 holding at least 14. One article is one passage, as a heading starts a chunk and 512 words is `ChunkingRule.DEFAULT`'s budget. A passage's *subject* is its category, the only truth there is to measure against; categories that overlap, as `crude`, `gas` and `nat-gas` do, cost every method alike. Beside them, 41 *short passages*: the first 25 words of an article no seed set otherwise draws.
- **Embedders.** Qwen3-Embedding-0.6B (Q8_0) through a local Ollama on CPU, every passage sent with no instruction as the embedding step sends a chunk; and, to see what moves with the embedder, nomic-embed-text-v1.5 (F16).
- **The method.** The shipped `NearestNeighbourGraph.build` and `Communities.of`, run from the built classes over the passages in the order of §4; the alternatives in a port of the same code, checked equal to it on a fixture.
- **Seed sets**, each seed a file of passages, a seed's path naming its first subject:
  - *scaled*: 5, 10, 20, 50, 100 and 200 seeds, three draws of each, each seed holding one subject (70%), two (20%) or three (10%) and 1 to 15 passages of each, over 3, 4, 7, 14, 27 and 34 subjects: 24 to 1,496 passages; and two draws of 177 and 194 seeds holding 2,702 passages;
  - *one seed of many subjects*: one file of 8 subjects of 6 passages, one of 15 of 4, and the first beside 4 seeds of a subject each;
  - *many seeds of one subject*: 20 seeds of 3 passages of `crude`, 60 of 3 of `acq`, and 12 seeds of `crude` or `coffee`;
  - *a very short seed*: a draw of 20 seeds and one seed of a single short passage, six times.
- **Agreement** with the subjects is the adjusted Rand index (ARI), 1 for the subjects exactly and 0 for chance; a *family* is the draws of one shape and size.

**k and the resolution.** Each of k = 5, 8, 10, 15, 20 and 30 at each resolution of 0.5, 0.75, 1.0, 1.5 and 2.0, over every set. The mean over the twelve families of more than one subject:

| k, resolution | mean ARI | mean normalised mutual information | |
| --- | --- | --- | --- |
| **10, 1.0** | **0.576** | **0.737** | the highest of the thirty |
| 15, 1.5 | 0.535 | 0.707 | |
| 5, 0.75 | 0.532 | 0.720 | |
| 15, 1.0 (ADR-087's for clusters) | 0.507 | 0.670 | |
| 30, 1.0 | 0.432 | 0.597 | |

By size, the scaled families:

| Seeds | Passages | Subjects | ARI at 10, 1.0 | ARI at 15, 1.0 | Topics at 10, 1.0 |
| --- | --- | --- | --- | --- | --- |
| 5 | 24 to 41 | 3 | 0.707 | 0.452 | 3 to 4 |
| 10 | 53 to 63 | 4 | 0.688 | 0.598 | 4 |
| 20 | 121 to 164 | 7 | 0.728 | 0.730 | 5 to 8 |
| 50 | 311 to 404 | 14 | 0.649 | 0.581 | 10 |
| 100 | 702 to 714 | 27 | 0.459 | 0.405 | 13 to 15 |
| 200 | 1,273 to 1,496 | 34 | 0.458 | 0.432 | 15 to 18 |
| 177 and 194 | 2,702 | 34 | 0.532 | 0.544 | 18 to 20 |

- **A large k fails a small seed set.** Where k reaches most of the passages the graph is nearly complete and holds no structure: at 24 to 41 passages, k = 15 at a resolution of 1.5 gives 5 to 12 topics for 3 subjects, and k = 20 or 30 up to 37.
- **A large seed set is divided coarser than its subjects**, 13 to 20 topics for 27 to 34, at every k; a resolution of 2.0 comes nearer the count there (ARI 0.53 to 0.58) and over-divides the small sets. No one pair is best at every size, and none was fitted to the size: 10 and 1.0 is the best over all of them.
- **A subject with fewer passages than k is not kept apart**: each passage must link to k, so some lie outside its subject. One seed of 8 subjects of 6 passages gives 4 topics (ARI 0.500), and of 15 subjects of 4 passages 4 topics (ARI 0.143); k = 5 at 2.0 gives 8 and 0.727 for the first.
- **Many seeds of one subject** are divided into its sub-subjects: 20 seeds of `crude` into 4 topics, each 18% to 38% of the passages, and 60 of `acq` into 5, each 13% to 25%. The 12 seeds of `crude` or `coffee` give the 2 subjects exactly.
- **A very short seed is never a topic of its own.** Every passage keeps k neighbours, so it lies in the topic of its nearest, at every k and resolution measured: where other seeds cover its subject, with them (in the one draw where they did, all 21 of them); where none does, in a topic of another subject (the five others). No set at 10, 1.0 gave a topic of one passage, and the narrowest topic held 0.6% of its set's passages.

**Determinism and order.** At 10 and 1.0, every set visited twice in the same order gave the same topics. Visited in the reverse order, the topics agreed with the first at ARI 0.589 to 1.000 across 24 sets (the scaled draws, the two single seeds of many subjects and the two seed sets of one subject), and their count changed in 10 of them.

**Changes of the seed set**, over the scaled draws of 10 to 200 seeds, 15 sets, at 10 and 1.0; ARI over the passages both versions have:

| Change | ARI, mean (lowest) | Sets whose topic count changed |
| --- | --- | --- |
| one seed renamed to sort last | 0.984 (0.761) | 1 of 15 |
| every seed's name exchanged for another's | 0.905 (0.752) | 5 |
| a seed removed | 0.911 (0.750) | 5 |
| the seed sorting first removed | 0.881 (0.634) | 6 |
| a seed of 5 passages added, sorting first | 0.933 (0.783) | 3 |
| a seed's content added again under a second name | 0.932 (0.786) | 4 |

At 15 and 1.0, exchanging every seed's name gave 0.825 (0.673) and changed the count in 9 of 15.

**The key.** Of the topics a change kept (each matched to the topic sharing at least half its passages), how many kept their key:

| Change | first passage, by seed path and chunk index | medoid passage | ordinal by first appearance |
| --- | --- | --- | --- |
| a seed added | 0.82 | 0.93 | 0.51 |
| a seed removed | 0.91 | 0.92 | 0.73 |
| the first seed removed | 0.77 | 0.88 | 0.33 |
| a seed's content added again | 0.95 | 0.96 | 0.85 |

The medoid is the passage most like the rest of its topic, by the sum of its similarities.

**Alternatives**, under Qwen, on eight families (the scaled draws up to 200 seeds, and the two single seeds of many subjects), against the shipped method at 10 and 1.0, mean ARI 0.542:

| Method | Mean ARI | Topics on 20 and 60 seeds of one subject | |
| --- | --- | --- | --- |
| the graph's edges weighted by their cosine | 0.534 | 4 and 7 | the reverse order agreeing at ARI 0.537 to 1.000, the shipped graph's at 0.589 to 1.000 on the same sets |
| average-linkage agglomerative clustering, cosine distance threshold 0.60 | 0.579 | 11 and 46 | its best threshold per family 0.60 to 0.70 |
| the same at 0.70 | 0.444 | 2 and 4 | |
| complete linkage, at its best threshold, 0.70 | 0.493 | 10 and 30 | |

**The second embedder.** The same sets and measures under nomic-embed-text-v1.5:

| | Qwen3-Embedding-0.6B | nomic-embed-text-v1.5 |
| --- | --- | --- |
| the graph at 10, 1.0, over the twelve families: mean ARI, and its rank of the thirty pairs | 0.576, first | 0.503, fourth (the first, k = 5 at 1.0, 0.517) |
| the graph at 15, 1.0, the same | 0.507, thirteenth | 0.465, thirteenth |
| over the nine families of more than one subject among the scaled draws and the single seeds: the graph at 10, 1.0 | 0.540 | 0.461 |
| average linkage there, at its own best threshold | 0.555, at 0.60 | 0.499, at 0.35 |
| its best threshold, family by family | 0.60 to 0.70 | 0.30 to 0.40 |
| average linkage at the other embedder's best | 0.000 at 0.35, every set one topic | 0.000 at 0.60, every set one topic |
| topics of the graph at 10, 1.0 on 20 and 60 seeds of one subject | 4 and 5 | 3 and 4 |

Under both, the graph at 10 and 1.0 gave much the same counts on every scaled set (3 to 21 topics under nomic, 3 to 20 under Qwen), every set visited twice in one order gave the same topics, and the reverse order agreed at ARI 0.406 to 1.000 under nomic.

**Cost.** One thread of a CPU, the passages' vectors resident: 1.4 to 1.8 seconds at 1,273 to 1,496 passages, 6.1 to 6.4 at 2,702, growing as their square. No call to the embedder or to any model is made: the passages' vectors are the ones the embedding step already stored.

**Not measured**: the shipped 8B embedder; seeds of long documents of many sections, where a seed's chunks are many and alike; a seed set past 2,702 passages; operator seed sets, which no agent reads. The ARI values are against Reuters' categories and would differ against another truth; the comparisons between methods and numbers on one truth are what the decision rests on.

## Decision

### 1. A passage is a stored seed chunk

**A passage is one chunk of a usable seed**, as the embedding step cut it (`HybridChunker` at `ChunkingRule.DEFAULT`, ADR-029) and embedded it (`ChunkEmbedder`), its vector the one stored under the content hash, the chunker's identity, the rule's identity, the chunk's ordinal and the embedder identity the scoring run reads (ADR-085). Its *chunk index* is that ordinal. The vectors are those the relevance-scoring step already holds resident for every usable seed (`RelevanceScoring.residentSeedVectors`, ADR-231), so finding the topics costs no embedding call and no read of its own.

**A seed of the same content identity as a seed before it in §4's order feeds no passage.** Its content identity is the content hash seed extraction recorded for it under the measurement run (ADR-206 §3), the key the scoring step already reads its vectors by; no other hash is taken. Of two or more seeds of one content, the first in §4's order feeds, its path and chunk indexes being the passages', and the rest feed the topics it feeds: none of them is reported as feeding no topic for want of passages of its own (ADR-396 §1). Each is still a seed relevance is scored against, as before (ADR-020): a survivor's score and winning seed are unchanged by this.

A seed with no chunk, which scoring leaves out (ADR-231 §2a), has no passage and feeds no topic.

### 2. The method is ADR-087's, over the passages

**The topics are the modularity communities of a k-nearest-neighbour graph over the passages' vectors**: `NearestNeighbourGraph.build` keeps each passage's k nearest by cosine, and `Communities.of` finds the communities, each edge weighing 1 whichever end kept it, with no edge similarity floor and no random number generator. The two classes are used as they are, so that a change to them for topics would move the clusters with it and is a decision of its own.

- **How many topics there are falls out**; nothing gives a count (ADR-396 §1).
- **Every passage lies in exactly one topic.** A seed may feed several, and several seeds one. The breadth floor of [#827](https://github.com/algernon28/vespera/issues/827) is applied to what this finds.
- **A seed set of one passage has one topic**, k being floored to one less than the passages, as ADR-087 floors it.
- **No pairwise matrix is stored.** The graph holds k neighbours of each passage, its vectors read in ADR-087's blocks from what is resident.

### 3. Ten neighbours and a resolution of 1.0, as presets

| Preset | Ships | Profile key |
| --- | --- | --- |
| `vespera.topics.neighbours`, the k of the topics' graph | `10` | none |
| `vespera.topics.resolution`, the modularity resolution of its communities | `1.0` | none |

They are presets under ADR-330 §8, *every candidate is a preset*, and apart from clustering's `vespera.clustering.neighbours` and `vespera.clustering.resolution`, whose 15 and 1.0 stand for clusters. **Neither has a profile key**: how many topics there are is never set by the operator (ADR-396 §1), and the resolution is the number that would set it. A value no run can be made under stops the invocation in one line, as ADR-330 §9 refuses every preset's: `vespera.topics.neighbours is <value>: a whole number from 1 to 2147483647`, and `vespera.topics.resolution is <value>: a number above 0`.

### 4. How §9's order compares two passages

**By each seed's path relative to the seed folder, then by each chunk's index** (ADR-396 §9). The path is the one the seed walk stores (ADR-051): `/` between its names, no case folded, nothing normalised. Two paths are compared whole, code point by code point, which is the order of their UTF-8 bytes and SQLite's `BINARY`, and not by UTF-16 code unit, as Java's `String.compareTo` compares them, nor one folder's name at a time. Two chunk indexes are compared as numbers. The step sorts the passages itself, whatever order they are handed to it in.

### 5. The topic key

**A topic's key is the seed path and the chunk index of its first passage in that order.** It is unique, since a passage lies in one topic; the same on every re-walk, since it names no occurrence id (ADR-112's reason); and no name, which a change of naming model changes while the topics stand (ADR-396 §4). It is what `Communities` already numbers by: the topic of ordinal n is the n-th topic to appear in the visiting order, and the ordinal is no key, since a topic before it lost or gained changes it. The top level's ties are broken on it (ADR-396 §4); where it is stored is #832's.

### 6. Where the step runs, and which run it names

**The topics are found at the head of the relevance-scoring step**, stage 5d, under the embedding-scoring run: after the resident seed vectors are read and before the first survivor is scored, so that placement ([#828](https://github.com/algernon28/vespera/issues/828)) may read them in the same pass. No step is added: the job keeps its fifteen. Where the step finds no resident seed vector it ends as it does now, and finds no topic. A re-run of the step discards the topics it wrote with the rest of its work (ADR-116).

**The two presets enter the embedding-scoring run's identity**, as two members appended to its configuration after `sampledPerBand`, `topicResolution` a number and then `topicNeighbours` a whole number, each `@JsonInclude(NON_NULL)` and written only where the value resolved is not the one that ships, `1.0` and `10`, as ADR-330 §9 writes clustering's. The breadth floor's member is #827's. The relevance score and verdicts read under that run are unchanged by any topic number (ADR-020, ADR-396 §2).

### 7. What the implementation owes

- In `embedding`: `SeedPassage(String seedPath, int chunkIndex)`, comparable in §4's order; `TopicKey(SeedPassage firstPassage)`; `Topic(TopicKey key, List<SeedPassage> passages)`, its passages in visiting order; `SeedVectors(String contentHash, List<float[]> chunkVectors)`, a seed's content hash and its chunks' vectors in chunk order, as `residentSeedVectors` holds them; and `TopicInference.over(Map<String, SeedVectors> bySeedPath, int neighbours, double resolution)`, returning the topics in the order of their keys, a seed of an earlier seed's content hash feeding no passage.
- In `pipeline`: `Presets.topicNeighbours()` and `Presets.topicResolution()`, their refusals in `Presets.refusal`, the two members of the scoring run's configuration, and the call at the head of `RelevanceScoringTasklet`, a seed's path read from the seed walk's occurrences.
- `application.yaml` gains the two presets under `vespera.topics`, each with its comment; the control panel shows them read-only, as ADR-330 §12 shows a preset with no profile key.

## Alternatives refused

- **ADR-087's numbers for clusters, k = 15.** Mean ARI 0.507 against 0.576, and 0.452 against 0.707 on the sets of 5 seeds; thirteenth of the thirty pairs under both embedders, where 10 and 1.0 was first and fourth; at a resolution above 1 a small seed set falls apart into many topics. Clusters keep 15: a seed partition of thousands of documents is not a seed set.
- **Edges weighted by their cosine.** No better (mean ARI 0.534 against 0.542), and its weights put the embedder's similarity scale into the topics, where an unweighted graph reads only which passages are nearest.
- **Average-linkage agglomerative clustering at a cosine distance threshold.** At its best threshold it agrees with the subjects a little better (0.555 against 0.540 under one embedder, 0.499 against 0.461 under the other), but that threshold is a number on one embedder's scale: its best value lay at 0.60 to 0.70 under one embedder and 0.30 to 0.40 under the other, each one's best giving the other a single topic for every set, and it divides a seed set of one subject into 11 and 46 topics at 0.60, where the graph gives 4 and 5. It needs every pair's distance held, N²/2 numbers, where ADR-085 stores no pairwise matrix and the graph holds N·k. It would be an unmeasured threshold per embedder, which *observe before enforce* refuses. Complete linkage did worse at every threshold.
- **A k or a resolution that grows with the seed set.** The measurement shows larger sets coming nearer their subjects at a higher resolution, but a law of the size fitted on one corpus and one truth would be a guess presented as a measurement; the presets let a machine name another value, and the run say so.
- **The medoid as the key.** It survives a seed added or removed a little more often (0.88 to 0.96 against 0.77 to 0.95), at the cost of a sum of similarities per passage and a key that depends on floating-point arithmetic. Topics re-formed by a changed seed set are written under a new run in any case.
- **The ordinal as the key.** It moves with every topic that appears before it: kept by 0.33 to 0.85 of the topics a change kept.
- **Another algorithm**, Leiden or density clustering: a dependency no record requires (ADR-046), for a step `Communities` already performs.

## Consequences

- **Finding the topics costs seconds and no model call.** The vectors are resident already; the graph's pass grows as the square of the passages, 6 seconds at 2,702 on one thread.
- **A seed set larger than tens of subjects is divided coarser than its subjects**, measured at 13 to 20 topics for 27 to 34. A topic may then span subjects the operator would part; placement, clustering within the topic and the level below it ([#831](https://github.com/algernon28/vespera/issues/831)) divide it further.
- **A subject with fewer passages than ten is not kept apart from its neighbours**, and a seed set of one subject is divided into several topics. Which of them stand is the breadth floor's (#827).
- **A very short seed never forms a topic of its own**, so it is not under the breadth floor by being alone: it lies in the topic of its nearest passages, and feeds that topic. #827 and #834 read it so.
- **A seed renamed, added or removed re-forms some topics**: ARI 0.88 to 0.98 between the two, and the count changing in up to 6 of 15 sets. ADR-396 §9 counts each as a change of input.
- **A seed's content under a second name changes no topic**, where counting its passages again moved the topics as much as a seed added (ARI 0.932, the count changing in 4 of 15 sets), and would count them twice in each topic's breadth. A seed renamed so that it sorts before its twin moves which path the topics name, and so the keys that name it. Relevance still scores against both.
- **Clusters do not move**: `Communities` and `NearestNeighbourGraph` are unchanged, and clustering keeps its presets.
- **ADR-330 and ADR-396 carry a note *"Partly amended — see ADR-399"*** at their heads.
- **Run ids**: the embedding-scoring stage's run id moves with the code that builds this record, as ADR-396 §12 says of the children that build it; at the shipped values no member is written, so its configuration's text stays what `RunIdentityGoldenTest` holds. The walk, stages 1 to 4 and seed measurement do not move.

## Tests

| Class | What it holds |
| --- | --- |
| `SeedPassageTest.ordersBySeedPathByCodePointThenByChunkIndex` | an upper-case letter before a lower-case one, a hyphen before a slash, U+FF5E before U+1F4C4, chunk 2 before chunk 10, and every chunk of a seed before the next seed's (§4) |
| `TopicInferenceTest.visitsThePassagesInSeedPathThenChunkOrderWhateverOrderTheyAreHandedOver` | nine passages of five seeds, whose topics differ where the seeds are visited in the reverse order, form the two topics of §4's order whichever order they are handed over in, each keyed by its first passage, its passages in that order (§2, §4, §5) |
| `TopicInferenceTest.aSeedOfTheContentOfAnEarlierOneFeedsNoPassage` | the fixture's upper-case seed's content added under a path sorting last gives the topics of the set without it, keys and passages alike; under a path sorting first, the same topics, that path's passages in place of the upper-case seed's and keying both (§1) |
| `TopicInferenceTest.findsHowManyTopicsThereAreWithoutBeingTold` | three families of passages give three topics and two give two, with no count given (§2) |
| `TheTopicPresetsTest.shipsTheMeasuredValues` | `application.yaml` names 10 neighbours and a resolution of 1.0 for the topics (§3) |
| `TheTopicPresetsTest.refusesValuesNoRunCanBeMadeUnder` | no neighbours and a resolution of 0 are refused, each in its line (§3) |
| `TheTopicPresetsNameTheScoringRunInvocationTest.bothValuesEnterTheScoringRun` | a machine naming a resolution of 2.5 and 7 neighbours gets a scoring run whose configuration names both, the resolution first (§6) |
| `TheTopicsAreFoundAtTheHeadOfRelevanceScoringInvocationTest.areFoundFromTheResidentSeedVectorsBeforeTheFirstSurvivorIsScored` | one seed of one passage gives one topic, found after the seed's stored vectors are read and before the one survivor is scored, the embedder answering no call between the step's first line and its topic line (§1, §6) |

`RunIdentityGoldenTest` holds, unchanged, that at the shipped values no member is written.

## What this does not decide

- **The breadth floor's value** and what becomes of a topic under it and of its passages: #827.
- **The placement function** and whether it reads the topics in the scoring pass: #828.
- **Where the topics and their keys are stored**, and the table's run: #832.
- **How the topics are found for seeds of long documents of many sections**, which nothing here measured: whether a seed's many alike chunks crowd a topic, and whether the shipped 8B embedder moves the numbers of §3.
