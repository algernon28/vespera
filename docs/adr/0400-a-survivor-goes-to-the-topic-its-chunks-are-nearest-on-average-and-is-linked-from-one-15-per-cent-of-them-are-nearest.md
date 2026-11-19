# ADR-400 — A survivor goes to the topic its chunks are nearest on average, and is linked from one 15% of them are nearest

> **Partly amended — see [ADR-427](0427-the-presets-with-no-profile-key-are-shown-read-only-in-command-options-a-list-for-each-run-they-enter.md).** Of §6, as ADR-411 left it: the control panel shows `vespera.topics.see-also-share` read-only in Command options, so *"the control panel shows it read-only, as ADR-330 §12 shows a preset with no profile key"* holds again. The rest of this record stands.

> **Partly amended — see [ADR-409](0409-topics-placement-names-overviews-and-clusters-within-topics-are-kept-in-new-tables-keyed-by-a-topics-first-passage.md).** §6's line *"…, the placement held and not yet stored (#832)"*: the placement is stored under the embedding-scoring run, each scored document's winning topic in `winning_topic` and each other topic it fits in `see_also`, as it is scored, and the line reads `Stage 5d (relevance scoring) placed <n> survivor(s) under the <t> topic(s) that stand`. The rest of this record stands.

> **Partly amended — see [ADR-411](0411-adr-399-to-401-move-seed-measurement-and-adr-406s-floor-is-not-shown-in-the-control-panel.md).** Of §6: the control panel does not show `vespera.topics.see-also-share`, and *"the control panel shows it read-only, as ADR-330 §12 shows a preset with no profile key"* is not part of what lands; that showing, which ADR-330 §12 owes for every preset with no profile key, is [#855](https://github.com/algernon28/vespera/issues/855)'s. Of Consequences, its *Run ids*: the code that builds this record touches `embedding`, so it moves seed measurement as well as embedding scoring, the arrangement and generation (ADR-058, `StageModules`); *"The walk, stages 1 to 4 and seed measurement do not move"* reads *"The walk and stages 1 to 4 do not move"*. Of its Amends line: *"table of the members a run's configuration gains gains"* reads *"table of the members of a run's configuration gains"*. The rest of this record stands.

- **Date**: 2026-11-16
- **Status**: accepted on 2026-11-16.
- **Decided**: by the operator, in map [#822](https://github.com/algernon28/vespera/issues/822), as [ADR-396](0396-topics-inferred-from-the-seed-sets-passages-replace-seed-partitions-and-a-survivor-is-placed-apart-from-its-score.md) §2 records it: that each survivor is placed under one topic, its winning topic, and copied once; that every survivor is placed, one no topic fits well under its nearest; that every other topic it fits carries a "see also" link to it; and, in [#823](https://github.com/algernon28/vespera/issues/823), that relevance stays ADR-020's, the topics placing and not scoring. In [#828](https://github.com/algernon28/vespera/issues/828), on the measurements below: the placement function (§1), what a survivor fits (§2), that no count caps the links (§3), the preset (§4), and where and under which run the step runs (§5).
- **Amends**: [ADR-330](0330-the-control-panel-edits-every-value-of-profile-yaml-showing-the-value-in-use-and-where-it-came-from-and-ten-technical-choices-ship-as-presets-in-application-yaml.md) §8 and §9, whose table of presets gains `vespera.topics.see-also-share`, and whose table of the members a run's configuration gains gains `seeAlsoShare` (§4). It carries a note at its head.
- **Keeps**: ADR-020's score and winning seed, which no topic enters (§5); [ADR-085](0085-vectors-live-in-sqlite-and-the-pairwise-matrix-is-never-materialised.md)'s memory contract, one survivor's chunks against the resident seeds at a time and no pairwise matrix (§5); [ADR-399](0399-topics-are-modularity-communities-over-a-ten-neighbour-graph-of-the-seed-passages-keyed-by-their-first-passage.md)'s topics, keys and passages, a seed of an earlier seed's content feeding none (§1); [ADR-105](0105-stage-6a-names-the-arrangement-stage-5-already-built-and-unattributed-is-struck.md)'s total arrangement and [ADR-280](0280-a-clusters-folder-holds-its-page-and-a-copy-of-each-of-its-survivors-under-the-originals-own-name-suffixed-only-on-a-collision.md)'s one copy (§1, §2).
- **Rests on**: the measurements below, on public text and local models only ([ADR-264](0264-the-private-paths-guard-refuses-only-what-reads-or-writes-outside-the-allow-list-and-a-working-directory-is-open-as-the-place-it-lies-in.md)).
- **Settles**: [#828](https://github.com/algernon28/vespera/issues/828).

## Context

ADR-396 §2 gives each survivor a winning topic by a step apart from its relevance score, every survivor placed and each other topic it fits linking to it, and leaves to #828 the placement function and the threshold over which a survivor fits another topic. #823's comparison placed the relevant documents of two seed sets of 54 and 73 passages better by each topic's centroid than by ADR-020's mean of the top 3 similarities taken per topic: normalised mutual information 0.543 against 0.474, and 0.476 against 0.403. ADR-399 finds the topics at the head of the relevance-scoring step, from the seed vectors already resident, so that placement may be read in the same pass.

A survivor is not one passage. It is cut into chunks (ADR-029), and a document of twelve chunks may give two of them to a second subject. A function that places a one-passage news article well may place such a document by its odd chunk, and a threshold on one embedder's cosine scale may link twice as much under another.

### Measured

A probe outside the repository, on public text only, with no seed set and no archive read.

- **Text and seed sets.** #826's pool of Reuters-21578 articles, one article one passage, and its seed sets: three draws each of 5, 10, 20, 50, 100 and 200 seeds (24 to 1,496 passages), one seed of 8 subjects alone and beside 4 others, one seed of 15 subjects, 20 and 60 seeds of one subject, and 12 seeds of two: 24 sets, 2 to 18 topics each. The topics are ADR-399's, ten neighbours and a resolution of 1.0, in its visiting order, with no breadth floor.
- **Corpus**, per set, from the pool articles the set does not draw, a chunk being an article: every such article as a document of one chunk; documents of 2 to 4 and of 8 to 16 chunks of one subject; documents of 2 or 3 chunks of one subject and 1 of another, of 8 to 12 and 2 to 4, and of 2 and 2; and documents of 4 to 12 chunks of a subject no seed holds. 2,001 to 3,592 documents a set. The survivors are those at or over the cut-off on ADR-020's score that best separates documents of the set's subjects from the rest: 98 to 2,767 a set.
- **Truth.** A topic is *of* a subject when the subject holds most of its passages, or the topic holds most of the subject's. A survivor is placed right when its winning topic is of its subject, or of the subject most of its chunks are on. A link is *right* when the topic is of a subject of the survivor's that its winning topic is not, *sibling* when it is of the subject its winning topic already is, and *wrong* otherwise.
- **Embedders.** Qwen3-Embedding-0.6B (Q8_0) and nomic-embed-text-v1.5 (F16), through a local Ollama on CPU, as in ADR-399.

**The function.** For a survivor of chunks *x₁…xₘ* and a topic of passages *p₁…pₙ*, each candidate reads only the cosines ADR-020's scoring already takes between a survivor chunk and a seed chunk:

| Function | | Placed right: one chunk / 2–4 chunks, one subject / 8–16, one subject / 2–3 + 1 / 8–12 + 2–4 / 2 + 2 | Mutual information, one-chunk survivors |
| --- | --- | --- | --- |
| ADR-020's, per topic: the mean of the top 3 cosines | Qwen | 0.907 / 0.920 / 0.950 / 0.641 / 0.680 / 0.950 | 0.669 |
| | nomic | 0.857 / 0.874 / 0.900 / 0.607 / 0.648 / 0.900 | 0.635 |
| the centroid, at the survivor's nearest chunk (#823's) | Qwen | 0.921 / 0.937 / 0.943 / 0.626 / 0.607 / 0.968 | 0.711 |
| | nomic | 0.868 / 0.897 / 0.924 / 0.602 / 0.607 / 0.924 | 0.660 |
| **the centroid, averaged over the survivor's chunks** | **Qwen** | **0.921 / 0.955 / 0.962 / 0.881 / 0.944 / 0.976** | **0.711** |
| | **nomic** | **0.868 / 0.910 / 0.945 / 0.828 / 0.908 / 0.918** | **0.660** |
| the mean of every cosine, no centroid | Qwen | 0.897 / 0.944 / 0.949 / 0.797 / 0.875 / 0.952 | 0.675 |
| | nomic | 0.845 / 0.889 / 0.921 / 0.740 / 0.832 / 0.899 | 0.627 |

Means over the 24 sets. On a survivor of one chunk the two centroid functions are one; a survivor of two subjects is placed elsewhere than under a topic of the subject most of its chunks are on 37% to 40% of the time by its nearest chunk, and 6% to 17% of the time by the average.

**What a survivor fits**, under the centroid averaged over chunks. Links per survivor, over every survivor of every set of more than one topic; the off-subject documents of 4 to 12 chunks counted whatever their score, as a floor set low lets them through:

| Rule | Links per survivor, Qwen / nomic | Right link, 2–3 + 1 and 8–12 + 2–4 (recall), Qwen / nomic | Links that are not right, a survivor of 8–16 chunks of one subject, Qwen / nomic | Links, an off-subject survivor of 4–12 chunks |
| --- | --- | --- | --- | --- |
| a topic within 0.01 below the winning topic's score | 0.096 / 0.177 | 0.02 and 0.01 / 0.05 and 0.03 | 0.04 / 0.10 | 0.20 / 0.30 |
| the same, 0.03 below | 0.324 / 0.612 | 0.08 and 0.05 / 0.19 and 0.13 | 0.20 / 0.35 | 0.67 / 1.08 |
| a topic one chunk is as near as the survivor is to its winning topic | 0.839 / 1.023 | 0.67 and 0.83 / 0.68 and 0.87 | 0.72 / 1.13 | 1.86 / 2.16 |
| a topic nearest to any one chunk | 0.725 / 0.756 | 0.87 and 0.97 / 0.82 and 0.96 | 0.88 / 1.07 | 1.57 / 1.46 |
| a topic nearest to at least 10% of its chunks | 0.654 / 0.679 | 0.87 and 0.89 / 0.82 and 0.86 | 0.62 / 0.73 | 1.38 / 1.30 |
| **at least 15%** | **0.573 / 0.603** | **0.87 and 0.80 / 0.82 and 0.76** | **0.34 / 0.46** | **0.99 / 0.95** |
| at least 20% | 0.516 / 0.548 | 0.87 and 0.59 / 0.82 and 0.55 | 0.23 / 0.37 | 0.78 / 0.77 |
| at least 25% | 0.460 / 0.493 | 0.87 and 0.36 / 0.82 and 0.35 | 0.14 / 0.25 | 0.56 / 0.57 |

- **A margin below the winning topic's score finds near ties, not a second subject.** At every margin measured it found at most 19% of the minority subjects, most of its links went to a single-subject survivor's neighbouring topics, and the same margin made 1.8 to 1.9 times the links under nomic as under Qwen: the margin is a number on one embedder's cosine scale, as average linkage's threshold was in ADR-399.
- **A share of the survivor's chunks** reads which topic each chunk would be placed under, and no cosine number: the two embedders differ by at most 0.04 links a survivor at every share. A survivor of one chunk gets no link under any share, its one chunk being nearest to its winning topic. A survivor of 2 to 4 chunks gets the same links at every share up to 25%, one chunk being a quarter of it.
- **The share trades a minority subject against stray links on a long survivor.** From 10% to 15%, the right link to a minority of 2 to 4 chunks of 10 to 16 falls from 0.89 and 0.86 to 0.80 and 0.76, and the links a single-subject survivor of 8 to 16 chunks gets that are not right fall by 45% and 37%. From 15% to 20%, that recall falls by 0.21 under both embedders, and the links not right by 0.11 and 0.09.
- **A survivor of no seed's subject** lies near no topic, its chunks scatter, and it gets about one link at 15% under either embedder. It is a document the relevance floor let through.

**A cap**, at a share of 15%:

| Cap | Links per survivor, Qwen / nomic | Recall, 8–12 + 2–4 | Recall, 2 + 2 or 2–3 + 1 | Most links a survivor had |
| --- | --- | --- | --- | --- |
| none | 0.573 / 0.603 | 0.80 / 0.76 | 0.87 / 0.82 | 5 / 4 |
| 3 | 0.573 / 0.602 | 0.80 / 0.76 | 0.87 / 0.82 | 3 |
| 2 | 0.558 / 0.588 | 0.79 / 0.75 | 0.87 / 0.82 | 2 |
| 1 | 0.453 / 0.468 | 0.73 / 0.66 | 0.80 / 0.73 | 1 |

**Cost.** Every function reads the cosines the scoring pass already takes, one per survivor chunk and seed chunk: the centroid's adds each to a sum for its chunk and topic, and divides by one number per topic, the length of the sum of its passages' unit vectors, taken once from the resident seed vectors. No embedding call and no model call is added, and no second cosine of any pair is taken.

**Not measured**: the shipped 8B embedder; survivors of more than 16 chunks, or of chunks of a real document's sections; operator seed sets and archives, which no agent reads; a breadth floor, which #827 sets and which removes topics before placement. The values are against Reuters' categories and would differ against another truth; the comparisons between functions and rules on one truth are what the decision rests on.

## Decision

### 1. The winning topic is the centroid a survivor's chunks are nearest on average

**A survivor's score at a topic is the mean, over its chunks, of each chunk's cosine to the topic's centroid**, the centroid being the mean of the unit vectors of the topic's passages (ADR-399 §1). It is read off the cosines the relevance-scoring step takes between each survivor chunk and each seed chunk: for chunk *x* and topic *T* of passages *p₁…pₙ*,

> cos(*x*, centroid of *T*) = (cos(*x*, *p₁*) + … + cos(*x*, *pₙ*)) / ‖*p̂₁* + … + *p̂ₙ*‖

where *p̂* is a passage's vector divided by its length. The denominator is one number per topic, taken once from the resident seed vectors before the first survivor is scored.

**The winning topic is the topic of the highest score**, among the topics that stand once #827's breadth floor has been applied. Every scored document has one: the arrangement is total (ADR-105), and a document no topic fits well takes the one it is nearest to. Where no topic stands, no document is scored: the relevance-scoring step stops before the first, in one line naming the floor and the largest topic's share ([ADR-401](0401-a-topic-holding-under-half-of-one-percent-of-the-seed-passages-is-dropped-and-a-seed-feeding-none-is-listed.md) §1). Two topics of equal score are decided for the one whose key comes first (ADR-399 §5), and never by a topic's name or ordinal.

- **A passage that lies in no topic counts toward none**: a passage of a seed of an earlier seed's content, which feeds no passage (ADR-399 §1), and a passage of a topic under the breadth floor. Its cosines are still taken for relevance (ADR-020).
- **The score at a topic places, and is not a relevance score.** It is on another scale from ADR-020's, and no floor, label or report reads it.

### 2. A survivor fits each other topic at least 15% of its chunks are nearest

**Each chunk of a survivor is nearest to the topic of its highest cosine to a centroid**, ties decided by key as in §1. **A survivor fits every topic other than its winning topic that at least `vespera.topics.see-also-share` of its chunks are nearest to**, 0.15 as shipped: a chunk count at or over 15% of the survivor's chunks. Each topic it fits carries a "see also" link to it, and no copy (ADR-280).

- **A survivor of one chunk fits no other topic**: its one chunk is nearest to its winning topic.
- **No cosine number decides a link.** A margin below the winning topic's score was measured and refused (Alternatives refused).

### 3. No count caps the links

**A survivor links from every topic it fits.** The share bounds them already: at most ⌊1 / 0.15⌋ = 6 topics hold 15% of a survivor's chunks each, so no survivor is linked from more than six at the shipped value. A cap of 3 changed the links per survivor by at most 0.001 and no recall, and a cap of 1 or 2 lost links to a second subject; a cap would be a second number with nothing measured to choose it by.

### 4. The share is a preset

| Preset | Ships | Profile key |
| --- | --- | --- |
| `vespera.topics.see-also-share`, the share of a survivor's chunks a topic must be nearest to for the survivor to fit it | `0.15` | none |

It is a preset under ADR-330 §8, beside ADR-399's two. It has no profile key, as the topics' numbers have none (ADR-396 §1). A value no run can be made under stops the invocation in one line, as ADR-330 §9 refuses every preset's: `vespera.topics.see-also-share is <value>: a number above 0 and at most 1`. At 1, no survivor of more than one chunk fits a topic other than its winning one unless every chunk is nearest to it, which cannot be, so no link is made.

### 5. Where the step runs, and which run it names

**Placement is read in the relevance-scoring step's pass**, stage 5d, under the embedding-scoring run: as each scored document's chunks are taken against the resident seeds, each cosine of a passage that lies in a topic is added to the sum for its chunk and topic, and the document's winning topic and the topics it fits are taken from those sums once its chunks are done. No step is added, no embedding call is made, and no cosine is taken twice. What one document's placement holds, a sum for each of its chunks and each topic, is held for that document alone and dropped before the next (ADR-085).

**Every document the step scores is placed**, not only those the relevance floor passes: a floor decided again runs the floor step again and scores nothing again (ADR-118), so the placement of every document it may pass is already written.

**What must be stored**, for each scored document under the embedding-scoring run: its winning topic's key, and the key of each topic it fits. No score at a topic need be stored. The table, its columns and how a re-run discards them are [#832](https://github.com/algernon28/vespera/issues/832)'s.

**The share enters the embedding-scoring run's identity**, as the member `seeAlsoShare`, a number, appended to its configuration after the members before it, `@JsonInclude(NON_NULL)` and written only where the value resolved is not `0.15`, as ADR-330 §9 writes clustering's. The relevance score and verdicts read under that run are unchanged by it, and by any topic (ADR-020, ADR-396 §2).

### 6. What the implementation owes

- In `embedding`: `Placement(TopicKey winningTopic, List<TopicKey> seeAlso)`, the topics a survivor fits in the order of their keys, its winning topic never `null`; `TopicPlacement.of(List<Topic> topics, Map<String, SeedVectors> bySeedPath, double seeAlsoShare)`, taking each topic's denominator from the vectors and refusing an empty list of topics with an `IllegalArgumentException`, and its `topicCount()`, how many topics it places under; `TopicPlacement.forSurvivor(int chunks)`, returning a `TopicPlacement.Tally` that takes `add(int survivorChunk, SeedPassage passage, double cosine)` for each pair and gives `placement()`. The tally reads cosines alone, so that no survivor vector reaches it.
- In `embedding`, the scoring pass: `RelevanceScorer` hands each cosine it takes to the survivor's tally, a seed's occurrence mapped to its path for the `SeedPassage`, and `RelevanceScoring.scoreAndRecord` returns or records the placement beside the score, where #832 says.
- In `pipeline`: `Presets.seeAlsoShare()`, its refusal in `Presets.refusal`, the member of the scoring run's configuration, and `TopicPlacement.of` built at the head of `RelevanceScoringTasklet`, after the topics, over the topics that stand (ADR-401 §5). Until #832 stores placement, the step says what it placed in one line after the survivors are scored, its second number read off the placement built: `Stage 5d (relevance scoring) placed <n> survivor(s) under the <t> topic(s) that stand, the placement held and not yet stored (#832)`.
- `application.yaml` gains the preset under `vespera.topics`, with its comment; the control panel shows it read-only, as ADR-330 §12 shows a preset with no profile key.

## Alternatives refused

- **ADR-020's function per topic, the mean of the top 3 cosines.** Placed right 0.641 and 0.607 of the two-subject survivors where the centroid averaged over chunks placed 0.881 and 0.828, and below it on every kind of survivor under both embedders. It reads the topic's few nearest passages, so a topic that holds one passage near a survivor's odd chunk wins it.
- **The centroid at the survivor's nearest chunk** (#823's). The same on a survivor of one chunk, as nearly every document of #823's corpus was; on a survivor of 8 to 12 chunks and 2 to 4 of another subject, placed right 0.607 where the average placed 0.944 and 0.908: one chunk decides.
- **The mean of every cosine, with no centroid's length dividing it.** Below the centroid on every kind under both embedders: it favours a topic whose passages lie close together over one whose passages a survivor is as near to.
- **A margin below the winning topic's score.** At 0.01 to 0.03 it found 1% to 19% of the minority subjects, its links went mostly to a single-subject survivor's neighbouring topics, and the same margin made nearly twice the links under one embedder as under the other.
- **A topic one chunk is as near to as the survivor is to its winning topic.** More links than the share at every point, and twice as many on an off-subject survivor; and a survivor of many chunks has many chances for one of them to be near.
- **A cap of 1, 2 or 3 links.** Measured above: 3 changed nothing measurable, 1 and 2 cost second subjects, and the share bounds the links already.
- **A profile key for the share.** How the topics divide is not the operator's to set (ADR-396 §1); a machine names another value as a preset, and the run says so.

## Consequences

- **A survivor of two subjects is placed by the one most of it is on**, and linked from the other where at least 15% of its chunks are on it: 76% to 87% of the minority subjects measured, under either embedder.
- **A survivor of one chunk is never linked from a second topic.** A short document on two subjects lies under one.
- **A long survivor of one subject is linked from a neighbouring topic now and then**: 0.34 to 0.46 links each at 8 to 16 chunks, about half to a topic of another subject and half to another topic of its own, where ADR-399 divided the subject.
- **A survivor of no seed's subject that the relevance floor lets through** is placed under its nearest topic and linked from about one other.
- **Placement costs one addition per cosine already taken**, a sum for each of a survivor's chunks and each topic, and one number per topic taken before the first survivor.
- **ADR-330 carries a note *"Partly amended — see ADR-400"*** at its head.
- **Run ids**: the embedding-scoring stage's run id moves with the code that builds this record, as ADR-396 §12 says of the children that build it; at the shipped value no member is written, so its configuration's text stays what `RunIdentityGoldenTest` holds. The walk, stages 1 to 4 and seed measurement do not move.

## Tests

| Class | What it holds |
| --- | --- |
| `TopicPlacementTest.placesASurvivorUnderTheTopicItsChunksAreNearestOnAverage` | three chunks near one topic and one chunk exactly on another's centroid place the survivor under the first, which its nearest chunk would not (§1) |
| `TopicPlacementTest.placesBySeveralPassagesAndNotByTheNearestOne` | a survivor whose nearest passage lies in one topic, and which is nearer to the other's passages as a whole, is placed under the other (§1) |
| `TopicPlacementTest.placesASurvivorNoTopicFitsUnderTheTopicWhoseKeyComesFirst` | a survivor at a cosine of 0 to every passage, the topics handed over last key first, is placed under the first key, and fits no other (§1, §2) |
| `TopicPlacementTest.countsNoPassageThatLiesInNoTopic` | a cosine of 0.99 to a passage of no topic changes nothing (§1) |
| `TopicPlacementTest.linksFromATopicOneChunkInSixIsNearestToAndNotOneInSeven` | one chunk of six on the other topic fits it; one of seven does not, at 0.15 (§2) |
| `TopicPlacementTest.linksFromEveryTopicItFitsWithNoCap` | a survivor of six chunks, each nearest to another of six topics, fits the five that are not its winning topic, in the order of their keys (§2, §3) |
| `TheSeeAlsoSharePresetTest.shipsTheMeasuredValue` | `application.yaml` names a share of 0.15 (§4) |
| `TheSeeAlsoSharePresetTest.refusesValuesNoRunCanBeMadeUnder` | a share of 0 and a share of 1.5 are refused, each in its line (§4) |
| `TheSeeAlsoShareNamesTheScoringRunInvocationTest.theShareEntersTheScoringRun` | a machine naming a share of 0.3 gets a scoring run whose configuration names it (§5) |
| `TopicPlacementTest.refusesToPlaceUnderNoTopic` | placement over no topic is refused (§1, §6) |
| `RelevanceScorerTest.handsOnEveryCosineItTakesOnceAndScoresAsBefore` | two survivor chunks against three seed chunks hand on six cosines, one for each pair, each with its chunk, seed and seed chunk, and the score is the one handing on none gives (§5) |
| `PlacementIsReadInTheScoringPassTest.placesTheSurvivorFromTheCosinesTheScoreTakes` | `RelevanceScoring.scoreAndRecord`, handed a placement and the seeds' paths, returns the placement the stored vectors' cosines give and records the score it records handed none (§1, §2, §5) |
| `…placesUnderTheTopicsThatStandAlone` | built over the topics the breadth floor lets stand, it places a survivor nearest a dropped topic under the standing one whose key comes first (§1) |
| `PlacementIsReadInTheScoringPassInvocationTest.isReadFromTheScorersOwnCosinesWithNoEmbeddingCall` | the step says it found the topics, scored the one survivor and placed it under the one topic, in that order, and the embedder answered no call from the step's first line to the last of these (§5) |
| `…placesUnderTheTopicsThatStandAlone` | of two topics found, one stands, and the one survivor, nearest the other, is placed under the one topic placement was built over (§1, §5) |

`RunIdentityGoldenTest` holds, unchanged, that at the shipped value no member is written.

## What this does not decide

- **Where the winning topic and the topics a survivor fits are stored**, and how a re-run discards them: #832.
- **What a topic's page shows of its links**, and in what order: [#833](https://github.com/algernon28/vespera/issues/833).
- **Whether the clusters within a topic read the survivors it is linked from**: they do not under ADR-396 §3, which clusters a topic's placed survivors alone, and [#831](https://github.com/algernon28/vespera/issues/831) may ask again.
- **How the share behaves on survivors of tens of chunks**, and under the shipped 8B embedder, which nothing here measured.
