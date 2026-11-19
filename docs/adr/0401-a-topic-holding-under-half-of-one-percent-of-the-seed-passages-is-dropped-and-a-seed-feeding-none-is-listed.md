# ADR-401 — A topic holding under half of one percent of the seed passages is dropped, and a seed feeding none is listed

> **Partly amended — see [ADR-428](0428-the-presets-with-no-profile-key-are-shown-read-only-in-command-options-a-list-for-each-run-they-enter.md).** Of §6, as ADR-411 left it: the control panel shows `vespera.topics.breadth-floor` read-only in Command options, so *"the control panel shows it read-only, as ADR-330 §12 shows a preset with no profile key"* holds again. The rest of this record stands.

> **Partly amended — see [ADR-411](0411-adr-399-to-401-move-seed-measurement-and-adr-406s-floor-is-not-shown-in-the-control-panel.md).** Of §6: the control panel does not show `vespera.topics.breadth-floor`, and *"the control panel shows it read-only, as ADR-330 §12 shows a preset with no profile key"* is not part of what lands; that showing, which ADR-330 §12 owes for every preset with no profile key, is [#855](https://github.com/algernon28/vespera/issues/855)'s. Of Consequences, its *Run ids*: the code that builds this record touches `embedding`, so it moves seed measurement as well as embedding scoring, the arrangement and generation (ADR-058, `StageModules`); *"The walk, stages 1 to 4 and seed measurement do not move"* reads *"The walk and stages 1 to 4 do not move"*. Of its Amends line: *"table of the members a run's configuration gains gains"* reads *"table of the members of a run's configuration gains"*. The rest of this record stands.

- **Date**: 2026-11-16
- **Status**: accepted on 2026-11-16.
- **Decided**: by the operator, in map [#822](https://github.com/algernon28/vespera/issues/822), as [ADR-396](0396-topics-inferred-from-the-seed-sets-passages-replace-seed-partitions-and-a-survivor-is-placed-apart-from-its-score.md) §1 records it: that a topic's breadth is its share of all seed passages, that a topic under a measured floor on it is no topic, its passages feeding none, that a seed all of whose passages fall under the floor feeds no topic and is reported, and that the floor ships as a preset with no profile key. Under the operator's delegation, in [#827](https://github.com/algernon28/vespera/issues/827): the floor's value (§2), that it is a share alone and no count of passages (§3), how a topic at exactly the floor and the passages it is a share of are read (§1), which seeds are listed and in what order (§4), and which run the value enters (§5).
- **Amends**: [ADR-330](0330-the-control-panel-edits-every-value-of-profile-yaml-showing-the-value-in-use-and-where-it-came-from-and-ten-technical-choices-ship-as-presets-in-application-yaml.md) §8 and §9, whose tables of presets gain a row, `vespera.topics.breadth-floor`, whose table of refusals gains its line, and whose table of the members a run's configuration gains gains `topicBreadthFloor` (§2, §5). It carries a note at its head.
- **Keeps**: ADR-396 §1's breadth and what falls under the floor, which this record gives a value and does not change; [ADR-399](0399-topics-are-modularity-communities-over-a-ten-neighbour-graph-of-the-seed-passages-keyed-by-their-first-passage.md)'s method, its two presets and its §1, a seed of an earlier seed's content feeding no passage, which §1 and §4 read; ADR-020's score, which no topic and no floor enters; [ADR-105](0105-stage-6a-names-the-arrangement-stage-5-already-built-and-unattributed-is-struck.md), every survivor placed, which placement ([#828](https://github.com/algernon28/vespera/issues/828)) keeps over the topics that stand, and §1 keeps where none stands by scoring nothing; [ADR-231](0231-the-embedding-step-stops-unrecorded-where-the-embedding-model-was-pulled-while-it-ran-and-scoring-refuses-a-seed-whose-chunks-have-no-vector.md) §1's shape of a stop that is the operator's to read (§1).
- **Rests on**: the measurements below, on public text and local models only ([ADR-264](0264-the-private-paths-guard-refuses-only-what-reads-or-writes-outside-the-allow-list-and-a-working-directory-is-open-as-the-place-it-lies-in.md)).
- **Settles**: [#827](https://github.com/algernon28/vespera/issues/827).

## Context

ADR-396 §1 has a topic stand only where its breadth, its share of all seed passages, reaches a measured floor, so that the top level is not too fine-grained, and leaves the floor's value to #827. ADR-399 finds the topics as the modularity communities of a ten-neighbour graph over the passages, at a resolution of 1.0, and applies nothing to what it finds. The floor is to keep one seed from becoming one topic again, as one seed was a top-level entry before ADR-396, and is not to drop a subject the seeds do carry. Under ADR-020, which ADR-396 keeps, a floor moves no relevance verdict: a topic dropped costs placement alone, its survivors going to the topics that stand. #823's probe used 5% as a starting point.

### Measured

A probe outside the repository, on public text only, with no seed set and no archive read.

- **Text and embedders.** ADR-399's: Reuters-21578 articles of 40 to 512 words carrying one Reuters category, one article a passage, a passage's *subject* its category; Qwen3-Embedding-0.6B (Q8_0) and nomic-embed-text-v1.5 (F16) through a local Ollama, each passage sent with no instruction. ADR-399's pool of 2,792 articles and 41 short passages is joined by 2,800 more articles of `earn` and `acq`, 1,400 each, so that seed sets reach 5,500 passages: 5,633 passages in all.
- **The topics** are found by the shipped `NearestNeighbourGraph.build` and `Communities.of` at ADR-399's k = 10 and resolution 1.0, in its §4 order; the floor is applied to what they find.
- **Seed sets**, 131 under each embedder, 24 to 5,512 passages and 1 to 203 seeds, each seed a file of passages:
  - *scaled*: ADR-399's draws of 5 to 200 seeds, three of each; and 5 seeds of 52 to 84 passages, 10 of 100 to 258, 50 of 851 to 2,031, 100 of 2,113 to 2,560 and 200 of 2,940 to 5,502, two or three draws each, each seed holding one subject (70%), two (20%) or three (10%), up to 34 subjects;
  - *one seed of many subjects*: one file of 8 subjects of 6 passages, of 15 of 4, of 30 of 5 and of 34 of 14 (476 passages), the first beside 4 seeds of a subject each, and a file of 15 subjects of 4 among 100 seeds, two draws;
  - *many seeds of one subject*: 20 seeds of 3 passages of `crude`, 60 of 3 of `acq`, 12 of `crude` or `coffee`, 200 of 8 of `earn` (1,600 passages) and 100 of 15 of `acq` (1,500);
  - *a very short seed*: a seed of one 25-word passage beside 5, 20, 50 or 200 seeds, 17 sets of 25 to 5,503 passages;
  - *a rare subject*: 3, 5, 8 or 10 passages of a subject no other seed has, in one seed or spread over three, beside 10, 50 or 200 seeds, 64 sets of 59 to 5,512 passages.
- **A subject is recovered** where one topic holds more than half its passages and more than half that topic's passages are of it; a floor **loses** a recovered subject where it drops that topic. A seed is **listed** where none of its passages lies in a topic that stands. Summed over the 131 sets, the inference finds 1,496 topics recovering 851 subjects under Qwen, and 1,497 recovering 779 under nomic.

**The narrowest topics the inference finds**, by the size of the set:

| Passages in the set | Narrowest topic recovering a subject, Qwen / nomic | Narrowest topic of all, Qwen / nomic |
| --- | --- | --- |
| 24 to 499 | 3.15% (15 passages) / 2.52% (12) | 4.59% (10) / 3.24% (12) |
| 500 to 1,999 | 0.94% (14) / 1.27% (19) | 0.90% (13) / 0.97% (14) |
| 2,000 to 5,512 | **0.63%** (27) / **0.56%** (31) | 0.31% (17) / 0.47% (26) |

No set of more than 500 passages had a topic of fewer than 12, and none of any size one of fewer than 4. Every topic under 0.5% recovered no subject: each was a fragment of 17 to 26 passages, 81% to 95% of them of one subject of 84 to 974 in the set, whose largest share, 55 to 828 passages, lay in a topic of its own that stands.

**A share floor**, summed over the 131 sets:

| Floor | Topics dropped, Qwen / nomic | Recovered subjects lost | Seeds listed | Largest share of a set's passages feeding no topic |
| --- | --- | --- | --- | --- |
| 0.3% | 0 / 0 | 0 / 0 | 0 / 0 | 0 / 0 |
| 0.4% | 12 / 0 | 0 / 0 | 1 / 0 | 0.38% / 0 |
| **0.5%** | **13 / 8** | **0 / 0** | **1 / 0** | **0.47% / 0.47%** |
| 0.6% | 16 / 21 | 0 / 13 | 2 / 1 | 0.58% / 1.04% |
| 0.75% | 26 / 24 | 1 / 13 | 2 / 1 | 1.10% / 1.27% |
| 1% | 57 / 34 | 17 / 14 | 42 / 2 | 1.96% / 1.27% |
| 1.5% | 106 / 72 | 30 / 31 | 79 / 39 | 4.1% / 4.0% |
| 2% | 146 / 165 | 53 / 72 | 100 / 94 | 6.4% / 9.4% |
| 3% | 314 / 318 | 167 / 141 | 406 / 238 | 16.3% / 19.4% |
| 5% (#823's) | 595 / 601 | 323 / 305 | 1,229 / 1,055 | 44.3% / 40.9% |

At 0.5% the floor touches only sets of 3,000 passages or more, 13 and 8 of the 131, and drops in them the subject fragments of the paragraph above. The one seed listed under Qwen held one passage, in such a fragment of 17. At 1% it drops topics recovering a subject, such as `housing`, 14 passages in 1,496, and `rubber`, 27 in 4,282.

**A count of passages**, with no share, summed the same way:

| Floor of passages | Topics dropped, Qwen / nomic | Recovered subjects lost | Seeds listed |
| --- | --- | --- | --- |
| 3 | 0 / 0 | 0 / 0 | 0 / 0 |
| 5 | 1 / 1 | 0 / 1 | 1 / 0 |
| 8 | 7 / 9 | 5 / 6 | 6 / 8 |
| 10, the graph's k | 20 / 22 | 14 / 14 | 21 / 16 |

What a count drops lies in sets of under 500 passages, where a topic of 4 to 10 passages is a subject of the set: in one set of 64 passages, 8 passages of a rare subject in one seed formed a topic of their own. Added to a share of 1% or 2%, a count of 3 changes nothing, one of 5 drops one topic more, and 8 or 10 lose 5 to 14 subjects more.

**The shapes, at 0.5%:**

- **One seed of many subjects** is never listed: no topic of those seven sets is under the floor, the narrowest holding 1.5% of its set.
- **Many seeds of one subject** lose no topic: the narrowest held 1.3% (Qwen) and 1.6% (nomic), 20 and 24 passages of 1,500.
- **A very short seed** is never listed. As ADR-399 found, it lies in the topic of its nearest passages, which held 2.3% to 34% of its set's under Qwen and 3.8% to 42% under nomic.
- **A rare subject of 3 to 10 passages** formed a topic of its own only in sets of 64 to 81 passages, at 12% to 23% of them, in one draw under Qwen and three under nomic, alike in one seed or spread over three; in larger sets it lay in a topic of another subject. It was never under the floor.
- **A topic one seed holds**, 80% of its passages or more: 58 of 1,496 topics under Qwen and 61 of 1,497 under nomic, of which 2 and 10 lie in sets of more than 50 seeds. Each of those recovers a subject that one seed carries almost alone, 15 to 31 passages, 0.56% to 2.1% of the set; a floor above 0.55% drops the narrowest.

**Not measured**: the shipped 8B embedder; seeds of long documents of many sections, whose many alike chunks may form a topic of one seed; a seed set past 5,512 passages, where 0.5% exceeds 27 passages; other values of the topics' two presets, a smaller k finding narrower topics; operator seed sets, which no agent reads. The subjects are Reuters' categories, so *recovered* and *lost* are read against that truth.

## Decision

### 1. A topic stands at or above the floor

**A topic stands where its passages, divided by every passage that feeds the topics, are at least the floor**, the quotient taken as a `double`. A topic under it is dropped: its passages feed no topic, and are given to none that stands. The passages a topic's breadth is a share of are those ADR-399 §1 visits, one seed of each content: a seed of the content of a seed before it in ADR-399 §4's order adds none, so that a copy neither widens nor narrows any topic. The topics that stand keep the order of their keys.

**Where no topic stands, the relevance-scoring step stops before it scores a survivor**, so that no survivor is scored with no topic to be placed under and ADR-105's every survivor placed holds. It stops as ADR-231 §1 stops the embedding step: one line at error level, the step failed, no exception thrown, no completion recorded, and the job ends there. The line names the floor and the share of the largest topic found, the one of most passages:

> stage 5's relevance-scoring step stopped: no topic stands at a breadth floor of `<floor>`, the largest of the `<n>` topics found holding `<p>` of the `<total>` seed passages, a share of `<share>`. Nothing was scored and the step is not recorded as finished.

`<floor>` and `<share>` are written as Java writes a `double`, the share being `<p>` divided by `<total>`, the passages of §1. The floor is a preset of the machine's (§2), and the same run stops the same way each time it is asked again; a machine naming a floor at or below the largest share is another run (§5).

### 2. The floor is half of one percent, a preset

| Preset | Ships | Profile key |
| --- | --- | --- |
| `vespera.topics.breadth-floor`, the share of the seed passages a topic must hold to stand | `0.005` | none |

**0.005 is the highest share that lost no recovered subject under either embedder**, with no seed listed under nomic and one under Qwen: the narrowest topic recovering a subject held 0.63% and 0.56%, and every topic under 0.5% was the fragment of a subject that stands elsewhere. It drops nothing in a set of fewer than 3,000 passages measured, where the graph's ten neighbours already keep each topic at 12 passages or more beyond 500. A floor of 0.3% would drop nothing anywhere measured; 0.5% also drops those fragments, which is what a floor on breadth is for.

It is a preset under ADR-330 §8, *every candidate is a preset*, as ADR-396 §1 has it, beside `vespera.topics.neighbours` and `vespera.topics.resolution`. ADR-330 §8's *"A measured floor is no preset"* is of a floor that judges one corpus, ships unset and is the operator's to state; this one judges no document, is measured on public text, and is never the operator's, since how many topics there are is not theirs to set (ADR-396 §1). **It has no profile key.** A value no run can be made under stops the invocation in one line, as ADR-330 §9 refuses every preset's: `vespera.topics.breadth-floor is <value>: a number from 0 and below 1`. At 0 every topic stands; at 1 or above none could, but one holding every passage.

### 3. A share alone, and no count of passages

**The floor is a share and nothing else**, as ADR-396 §1 defines breadth. A count of 3 passages dropped nothing in any set; a count of 5 or more dropped subjects of sets under 500 passages, where a topic of 4 to 10 passages is one of a few subjects, and none of the fragments 0.5% drops. A topic of very few passages in a large set, which a count would be for, did not occur: the graph keeps every passage linked to ten others, and the fragments 0.5% drops hold 17 to 26.

### 4. The seeds feeding no topic

**A seed none of whose passages lies in a topic that stands feeds no topic, and is listed**, the list in ADR-399 §4's order of the seeds' paths. A seed of the same content as a seed before it feeds the topics that seed feeds (ADR-399 §1), so where that seed is listed, so is it, each under its own path. A seed with a passage in a topic that stands is not listed, whatever its other passages do. A seed with no chunk, which has no passage, is not listed here: ADR-231 §2a's unusable seeds already name it. The list is what [#834](https://github.com/algernon28/vespera/issues/834)'s report of seeds feeding no topic reads; where it is stored is [#832](https://github.com/algernon28/vespera/issues/832)'s.

### 5. Where the floor is applied, and which run it names

**The floor is applied where ADR-399 §6 finds the topics**, at the head of the relevance-scoring step under the embedding-scoring run, to the topics found, before any survivor is placed: placement (#828), the order of the top level, names and reports read the topics that stand. **The floor enters the embedding-scoring run's identity** as a member appended to its configuration after `topicNeighbours`, `topicBreadthFloor`, a number, `@JsonInclude(NON_NULL)` and written only where the value resolved is not `0.005`, as ADR-330 §9 writes every preset's. At the shipped value no member is written, so the configuration's text stays what `RunIdentityGoldenTest` holds.

### 6. What the implementation owes

- In `embedding`: `BreadthFloor(double share)`, its `of(List<Topic> found, Map<String, SeedVectors> bySeedPath)` returning `StandingTopics(List<Topic> topics, List<String> seedsFeedingNoTopic)`, the topics that stand in the order found and the seeds listed by §4, a seed's content read from its `SeedVectors` as `TopicInference.over` reads it; and its `refusal(List<Topic> found, Map<String, SeedVectors> bySeedPath)`, an `Optional<String>`, empty where a topic of `found` stands and otherwise §1's line from *no topic stands* to the share, the passages counted as `of` counts them.
- In `pipeline`: `Presets.topicBreadthFloor()` and its refusal in `Presets.refusal`, after the topics' two presets; the member of the scoring run's configuration; and the call after `TopicInference.over` in `RelevanceScoringTasklet`, its topics the ones that stand, and, where `BreadthFloor.refusal` is not empty, §1's stop before the survivors are counted, its line recorded through `RecordedClosingLines` as `EmbeddingScoringTasklet` records ADR-231's.
- `application.yaml` gains `vespera.topics.breadth-floor: 0.005` under `vespera.topics`, with its comment; the control panel shows it read-only, as ADR-330 §12 shows a preset with no profile key.

## Alternatives refused

- **#823's 5%.** It dropped 595 and 601 of about 1,500 topics, lost 323 and 305 recovered subjects, listed over a thousand seeds and left up to 44% of a set's passages feeding no topic. Measured on sets of three topics, it divides the top level of a set of twenty.
- **1% to 2%.** Each loses 14 to 72 recovered subjects, such as 14 passages of `housing` among 1,496, in sets of 700 passages and more.
- **A count of passages, alone or beside the share.** Inert at 3, and from 5 it drops subjects of small sets, which no share under 2% touches (§3).
- **0.3%, a floor that drops nothing measured.** Safe on these sets, but it keeps the subject fragments of 17 to 26 passages in 4,282 to 5,512 that 0.5% drops at no loss, the too fine-grained topics the floor is for.
- **A floor scaled with the size of the set, or with the number of topics.** Fitted on one corpus and one truth, it would be a guess presented as a measurement, as ADR-399 refused a k growing with the set; the preset lets a machine name another value, and the run say so.
- **Refusing a topic one seed holds.** In sets of more than 50 seeds each such topic recovered a subject that seed carried almost alone, 0.56% to 2.1% of the set; refusing it drops a subject the seeds carry. A seed becomes a topic only where it alone carries a subject broad enough to stand, which ADR-396 §1 allows: one seed may feed one topic as it may several.

## Consequences

- **At the shipped value the floor drops nothing below a few thousand passages**, and above them, the fragments of subjects that stand elsewhere: measured, at most one topic a set and 0.47% of its passages.
- **A topic of fewer than 0.5% of the seed passages is dropped, whatever subject it holds**, fewer than 28 passages in a set of 5,500, and the survivors it would have taken are placed under the topics that stand (#828). Its seeds are listed where none of their passages lies in a topic that stands.
- **A seed is listed though its subject stands** where all its passages lie in a fragment the floor drops: once in 131 sets under Qwen.
- **Where a machine's floor exceeds the share of every topic found, no topic stands**, and the relevance-scoring step stops in one line naming the floor and the largest share, scoring nothing (§1). At the shipped value one topic always stands while the inference finds 200 or fewer; it found 2 to 20.
- **ADR-330 carries a note *"Partly amended — see ADR-401"*** at its head.
- **Run ids**: the embedding-scoring stage's run id moves with the code that builds this record, as ADR-396 §12 says of the children that build it; at the shipped value no member is written. The walk, stages 1 to 4 and seed measurement do not move.

## Tests

| Class | What it holds |
| --- | --- |
| `BreadthFloorTest.aTopicUnderTheFloorIsDroppedAndOneAtItStands` | of four topics of five, three, one and one passages in ten, at a floor of three tenths the first two stand, the second at exactly the floor, in the order found; at 0 all four stand (§1) |
| `BreadthFloorTest.aSeedAllOfWhosePassagesFallUnderTheFloorFeedsNoTopic` | the seed whose one passage lies in a dropped topic is listed; one with a passage dropped and one in a topic that stands is not (§4) |
| `BreadthFloorTest.aSeedOfTheContentOfAnEarlierOneFeedsWhatThatOneFeeds` | a later seed of the listed seed's content is listed after it, one of a standing seed's content is not, and the copies add no passage: the topic at the floor still stands (§1, §4) |
| `BreadthFloorTest.noTopicStandingIsRefusedNamingTheFloorAndTheLargestShare` | at a floor of 0.6 over four topics, the widest five of ten passages, the refusal names the floor, the four topics, five of ten and 0.5; at three tenths there is none (§1) |
| `pipeline.PlacementIsReadInTheScoringPassInvocationTest.stopsBeforeScoringWhereNoTopicStands` | two topics of three passages in six at a floor of 0.6: the invocation fails, logs §1's line whole, reaches no survivor, and records no score and no completion under its scoring run (§1) |
| `…placesUnderTheTopicsThatStandAlone` | two topics of four and two passages in six at a floor of 0.6: one kept, the two seeds of the other feeding none, and the one survivor, on the dropped topic's subject, placed under the one topic placement was built over (§1, §5) |
| `embedding.PlacementIsReadInTheScoringPassTest.placesUnderTheTopicsThatStandAlone` | placement built over what the floor lets stand places a survivor nearest the dropped topic under the standing topic whose key comes first, linked from none (§1) |
| `TheBreadthFloorPresetTest.shipsTheMeasuredValue` | `application.yaml` names a breadth floor of 0.005 (§2) |
| `TheBreadthFloorPresetTest.refusesValuesNoRunCanBeMadeUnder` | 1, -0.01 and NaN are refused, each in its line, and 0 is not (§2) |
| `TheBreadthFloorNamesTheScoringRunInvocationTest.theFloorEntersTheScoringRun` | a machine naming 7 neighbours and a floor of 0.02 gets a scoring run whose configuration names both, the floor after the neighbours (§5) |

`RunIdentityGoldenTest` holds, unchanged, that at the shipped value no member is written. That the reports read the topics that stand is held by the tests #832 and #834 add with the tables they read.

## What this does not decide

- **Where a survivor whose nearest topic was dropped is placed**: #828.
- **Where the topics that stand and the seeds listed are stored**, and the report that shows them: #832 and #834.
- **The floor for the shipped 8B embedder, for seeds of long documents of many sections, and for seed sets past 5,512 passages**, none of which was measured.
