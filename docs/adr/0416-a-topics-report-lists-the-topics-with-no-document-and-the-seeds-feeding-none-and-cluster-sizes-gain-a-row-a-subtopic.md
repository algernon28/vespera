# ADR-416 — A topics report lists the topics with no document and the seeds feeding none, and cluster sizes gain a row a subtopic

- **Date**: 2026-11-18
- **Status**: accepted on 2026-11-18.
- **Decided**: by the operator, on [#834](https://github.com/algernon28/vespera/issues/834): that a new analysis report, `topics`, is written at 6a once the topics are named, holding the seeds feeding no topic, the topics no survivor is placed under, each with its seeds, their paths and passage counts in ADR-399 §4's order, and one row of counts a topic, its seed passages, their share of all seed passages, the seeds feeding it, the survivors placed under it and the documents linked from it; counts and paths only (ADR-264); one of the analysis reports, with a tab in ADR-414's Reports window (§1 to §4); that `cluster-sizes` has a row a topic and, under a divided topic, a row a subtopic, each with its own five numbers and the spread of its own graph, and no table of its own for a seed (§5); that ADR-045's alarm on a seed partition owning 60% is retired as a threshold, and each topic's share of the survivors is a column of `cluster-sizes`, with no threshold (§6); and that the overviews written and turned down are counted in 6b's closing line alone (§7). Under the operator's delegation: the report's name, title, sections, columns, words and format; the rows' order; how each count is read; which report carries the share; the cluster sizes' format, keys and words; the closing line's words; and the tests.
- **Amends**: [ADR-409](0409-topics-placement-names-overviews-and-clusters-within-topics-are-kept-in-new-tables-keyed-by-a-topics-first-passage.md) §8, *"`cluster-sizes.html` has one row a topic until #834 settles what it shows of topics"*, and its format 2: a divided topic's row is followed by a row for each subtopic, every row carries the subtopic and a share of the documents grouped, and `cluster-sizes.json` is of format 3 (§5, §6). It carries a note at its head.
- **Amends**: [ADR-223](0223-5fs-size-report-6a-and-6b-go-through-one-seed-partition-at-a-time-and-what-is-still-held-says-why.md) §2, five numbers a partition, as ADR-409 §13 made it five a topic: 5f keeps five numbers and a spread for each topic and for each subtopic of a divided topic; and §9, whose list gains one row a subtopic at 5f and one row a topic with its seeds at 6a (§8). It carries a note at its head.
- **Amends**: [ADR-362](0362-an-invocations-report-is-one-json-file-drawn-as-static-html-through-a-mustache-template-and-no-command-opens-anything.md) §8 and §9, as [ADR-392](0392-vespera-finds-no-translation-sets-the-translation-step-its-verdict-its-report-and-its-two-profile-keys-go.md) §5 left them, the eleven analysis reports: they are twelve, `topics` among them (§4); and ADR-392 §5's *"The analysis reports are eleven"*, and its Tests row, corrected in place for the test renamed (ADR-337 §5). Each carries a note at its head.
- **Amends**: [ADR-377](0377-a-profile-row-is-one-line-its-provenance-in-a-tooltip-a-list-is-tags-a-code-set-named-by-its-folder-and-no-label-file.md) §5, the analysis reports' titles: `topics` is titled *Topics found in your seed documents* (§4); its Tests row is corrected in place for the test renamed. It carries a note at its head.
- **Keeps**: [ADR-085](0085-vectors-live-in-sqlite-and-the-pairwise-matrix-is-never-materialised.md), whose dominant partition is *"minutes and a number in a report rather than a wall"*: the number is §6's share; [ADR-087](0087-clusters-are-modularity-communities-over-a-k-nearest-neighbour-graph-built-in-blocks.md) and [ADR-096](0096-k-retains-neighbours-regardless-of-distance-so-the-retained-edge-similarities-are-reported.md)'s spread of the kept links, now taken of each graph a row is of; [ADR-396](0396-topics-inferred-from-the-seed-sets-passages-replace-seed-partitions-and-a-survivor-is-placed-apart-from-its-score.md) §2, a topic no survivor is placed under has no folder; [ADR-399](0399-topics-are-modularity-communities-over-a-ten-neighbour-graph-of-the-seed-passages-keyed-by-their-first-passage.md) §4's order of seed paths and §5's key; [ADR-401](0401-a-topic-holding-under-half-of-one-percent-of-the-seed-passages-is-dropped-and-a-seed-feeding-none-is-listed.md) §1's share and §4's list; [ADR-406](0406-a-topic-of-300-survivors-or-more-is-divided-into-subtopics-its-own-communities-and-its-clusters-are-found-within-each.md) §4's order of subtopics; every table of ADR-409, none changing shape and none added; ADR-414's Reports window, which draws the new report from its JSON with no view of its own; [ADR-218](0218-every-statement-whose-temporary-files-grow-with-the-corpus-is-an-exception-to-adr-060-with-its-size.md), no statement added sorting.
- **Settles**: [#834](https://github.com/algernon28/vespera/issues/834); with it what ADR-396's table and *What this does not decide*, ADR-401 §4, ADR-402's and ADR-406's *What this does not decide*, and ADR-409 §4 and *What this does not decide* leave to it. ADR-045 is a reconstituted record and is not edited: its head note's *"How the alarm on a seed owning 60% reads for a topic is left to #834"* is answered by §6.

## Context

Stage 5d records the topics that stand, their passages and the seeds feeding none (ADR-409 §2), each scored document's winning topic and the topics it fits; 5f records each survivor's subtopic and cluster; 6a names every topic that stands, a topic no survivor is placed under among them (ADR-409 §4). Nothing reads `seed_feeding_no_topic`, nor a topic's seeds and their passages, nor counts what links to a topic. A topic no survivor is placed under has no `arranged_cluster` row (ADR-409 §3), so no folder, no heading on the 6a page and no row of `cluster-sizes`: it is shown nowhere.

`cluster-sizes.html` has one row a topic, its five numbers taken across its subtopics and its spread over the whole topic's graph (ADR-409 §8), so a divided topic's subtopics, each clustered over a graph of its own (ADR-406 §2), are not seen. ADR-045 tied clustering's bound to a partition, and an alarm to a seed owning 60% of the corpus; ADR-085 measured that a dominant partition costs minutes and no wall, and left it *"a number in a report"*; no alarm was built, and the relevance report has no figure by partition. ADR-402 records an overview's writing or its fault, and nothing counts them.

## Decision

### 1. A twelfth analysis report, `topics`, written at 6a once the names stand

**The arrangement step writes `topics.html`, and `topics.json` beside it, in the working directory**, through `ReportPage` (ADR-130) and `AnalysisReportJson` (ADR-362 §9), the JSON moved into place before the page. It is written once every topic's name stands under the arrangement run, on both of the step's branches: where it arranges, after the names are given and before `arrangement.html`; and where the arrangement step is already recorded, from the rows, as `arrangement.html` is (ADR-154 §2). It is not written where the step is gated before naming, where no survivor is grouped under any topic, or where the naming stops (ADR-409 §6): a page from an earlier invocation stays where it lies, as every report's does.

**It shows counts, paths, keys and the names recorded, and no text of a document** (ADR-264). A name is what the 6a gate already shows (ADR-409 §7).

### 2. What it holds

**One row a topic that stands**, in the top level's order, the survivors placed most first, ties on the topic key (ADR-396 §4, ADR-409 §3), and then the topics no survivor is placed under, in key order. Each row holds:

| Column | What it is |
| --- | --- |
| Topic | its name recorded under the arrangement run |
| First passage | its key, `<path> chunk <i>`, as `cluster-sizes` writes it (ADR-409 §8) |
| Seed passages | the topic's passages in `topic_passage`, summed over its seeds — which is `topic.passage_count` by ADR-409 §2 |
| Share of seed passages | that count over **every seed passage**, counted as the breadth floor counts it (ADR-401 §1): the chunks of one seed of each content, the first by path in ADR-399 §4's order |
| Seed documents | the seeds a passage of the topic is of, in `topic_passage` |
| Documents placed | the survivors placed under it that the clustering step grouped, as 6a counts them for the order (ADR-409 §13) |
| Linked documents | the survivors with a `see_also` row naming it, those that stand under the scoring run as 5f filters its members (ADR-409 §8) |

**The topics no survivor is placed under**, those whose *Documents placed* is 0, in key order, each with **one row for each seed feeding it**: the topic's name and key, the seed's path and how many of the topic's passages are of it, the seeds in ADR-399 §4's order of paths.

**The seeds feeding no topic**, `seed_feeding_no_topic`'s paths under the scoring run, in ADR-399 §4's order (ADR-401 §4).

**A seed of the same content as an earlier one** has no passage (ADR-399 §1, ADR-409 §2): it is counted under no topic and among no topic's seeds, adds nothing to the denominator, and is listed as feeding no topic only where the seed whose content it shares is, as ADR-401 §4 has it.

### 3. Its page and its JSON

**Its name is `topics`, its title *Topics found in your seed documents*, and its JSON is of format 1.** The page reads, the words in English whatever the deliverable language (ADR-321 §4):

- `<h1>` the title, then the first paragraph, whole:

  > The topics are the subjects the passages of your seed documents fall into, found across every seed document's passages rather than one for each file, so that one seed document can feed several topics and several seed documents one. Every document that survived was placed under one of them. Each topic's row gives its name, the seed document its first passage is in and that passage's number, counted from 0, how many passages it holds and their share of all the seed passages, how many seed documents feed it, how many documents were placed under it, and how many documents placed under another topic are linked from it.

  then the table of §2's columns.
- `<h2>` *Topics no document was placed under*, then, where there are some, *"A topic no document was placed under has no folder and no group in what is written. It is listed here with each seed document feeding it and how many of the topic's passages that seed document gives, so that you can see which part of your seed documents found nothing in the archive."* and the table, its columns *Topic*, *First passage*, *Seed document* and *Passages*; where there are none, *"Every topic has at least one document placed under it."* and no table.
- `<h2>` *Seed documents feeding no topic*, then, where there are some, *"A seed document none of whose passages lies in a topic is listed here: its passages fell in topics too narrow to keep. Documents are still scored for relevance against it, as against every seed document, but no topic comes from it."* and the table, its one column *Seed document*; where there are none, *"Every seed document feeds at least one topic."* and no table.
- `<h2>` *How to read this*, then two paragraphs, the first two words of each in `<em>`:

  > *Share of seed passages* is the topic's passages out of every passage of your seed documents, a seed document whose text is the same as an earlier one's counted once, under the earlier. The shares add up to less than the whole where a topic was too narrow to keep.

  > *Linked documents* are documents placed under another topic that also fit this one: each is copied once, under its own topic, and this topic carries a link to it rather than a second copy.

A share is shown as a percentage to one place, `66.7%`; a count as a number.

**`topics.json`** holds the same, as every analysis report's does (ADR-362 §9):

- **headline**: `topics`, *Topics*, the topics that stand; `seed-passages`, *Seed passages*, the denominator; `topics-with-no-document`, *Topics with no document*; `seeds-feeding-no-topic`, *Seed documents feeding no topic*; each a `count`.
- **sections**, in the page's order: `topics`, titled by the title, its text the first paragraph, its table §2's columns, `topic` *Topic* `text`, `first-passage` *First passage* `text`, `seed-passages` *Seed passages* `count`, `share-of-seed-passages` *Share of seed passages* `share`, `seed-documents` *Seed documents* `count`, `documents-placed` *Documents placed* `count`, `linked-documents` *Linked documents* `count`; `topics-with-no-document`, its columns `topic`, `first-passage`, `seed-document` *Seed document* `path` and `passages` *Passages* `count`; `seeds-feeding-no-topic`, its one column `seed-document`; `how-to-read`. A section with none holds its one sentence as its text and a `null` table; the other two hold their paragraph and their table. A share is a fraction of one, rounded to three places.
- `docs/report/topics.schema.json` is its schema, of format 1.

### 4. It is one of the analysis reports

`Reports.ALL` lists twelve: `topics` after `relevance-labelling` and before `arrangement`, and `Reports.TITLES` titles it as §3 does; `InvocationReport`'s list of the analysis reports holds it after `cluster-sizes` and before `arrangement`, the order the job writes them, with its format; `AnalysisReportFile.FORMATS` reads it at 1. It opens in a tab of the Reports window (ADR-414), drawn from its JSON by the view every analysis report shares. **No profile key's row links it** (`Reports.OF_A_KEY`): the topics are shown at the gate and not approved (ADR-396), and the breadth floor is a preset with no key (ADR-401 §2).

**`report.json` stays of format 2**: an analysis report added to its list removes, renames and retypes no field (ADR-362 §3). `docs/report/report.schema.json` allows `topics` among the analysis reports' names, and says twelve.

### 5. `cluster-sizes` has a row a topic and a row a subtopic

**Every topic the clustering step grouped has its row, as ADR-409 §8 has it, and a divided topic's row is followed directly by a row for each of its subtopics**, in ADR-406 §4's order, most survivors first, two of as many by their number. A topic's row keeps its five numbers across all its clusters and the spread of its whole graph; **a subtopic's row has the five numbers of its own clusters and the spread of its own graph**, the one its clusters were found over (ADR-406 §2). A topic not divided has its row alone.

**A row is keyed by its topic and its subtopic**: the first column holds the topic's key, `<path> chunk <i>`, on every row, its own and its subtopics'; a second column, *Subtopic*, holds the subtopic's number as `cluster_member` records it, counted from 0, and is empty on a topic's row. No name is shown: 5f writes before 6a names (ADR-407 §1).

**The headline is unchanged**, its four figures taken over the topics' rows alone, so that no document or group is counted twice.

**Under *How to read this*, after the five paragraphs ADR-409 §8 gives, a sixth and a seventh**, whole, the first two words of the seventh in `<em>`:

> A crowded topic is divided into subtopics before its documents are grouped. Its row counts every group across its subtopics, and its links are those of the documents of the whole topic; directly under it, each subtopic has a row of its own, numbered as the division numbered it, counted from 0, which counts that subtopic's groups alone, its links being those of the documents its groups were found among. A topic that is not divided has its own row alone.

> *Share of documents* is how many of the documents grouped on this page lie in the topic, or in the subtopic. No share is too large: a topic holding most of the documents takes the grouping longer, and nothing else, and its share is here so that you can see where the documents went, not as a warning.

The first paragraph, *What this page cannot see* and the other paragraphs stand.

**`cluster-sizes.json` is of format 3** (ADR-362 §9): a column inserted moves every later one. Its first section's columns are `topic` *Topic* `text`, `subtopic` *Subtopic* `count`, `documents` *Documents* `count`, `share-of-documents` *Share of documents* `share`, `groups`, `largest-group`, `middle-group`, `groups-of-one`, `weakest-link`, `middle-link` and `strongest-link`, as before; a topic's row holds `null` as its subtopic. `docs/report/cluster-sizes.schema.json` is of format 3 with this record, and the window reads `cluster-sizes` at 3: a `cluster-sizes.json` an earlier build wrote is of a format it does not read (ADR-362 §3) until the clustering step writes it again, as it does on the first invocation of this build, the scoring run moving (§9).

### 6. ADR-045's alarm is retired, and the share is a column

**No threshold is set on how much of the archive one topic holds**, and nothing warns of it: no line, no gate, no mark on a row. ADR-085 measured that a dominant partition costs time and no wall, and a topic is no different. **Each row of `cluster-sizes` shows its share of the documents grouped**, *Share of documents*, the row's documents over the documents of every topic grouped, shown as a percentage to one place and written in the JSON as a fraction of one rounded to three places. It is on `cluster-sizes`, where the clustering's cost is, and not on `topics`, where *Documents placed* is a count. The relevance report keeps no figure by partition or by topic: its numbers are of the scores, scored against the seeds (ADR-396 §2).

### 7. 6b's closing line counts the overviews

**The generation step's line on finishing says how many topic overviews are recorded under its run, written and turned down**: the rows of `topic_overview` and of `topic_overview_fault` under the generation run. The line ADR-111's walk gives reads

> The generation step finished under `<run>`, over the arrangement approved as `<name>`: `<n>` synthesis doc(s) written under model `<model>` in a window of `<window>`, `<k>` already recorded by an earlier invocation of this run and left as they were; `<w>` topic overview(s) written and `<t>` turned down -- the deliverable tree is at `<tree>`

and the line ADR-319 §2 gives, where groups stand without writing, gains `; <w> topic overview(s) written and <t> turned down` after its description of them and before its tree. No report, no invocation account and no other line counts them.

### 8. The reads, what sorts and what is held

- **`embedding` answers 6a's reads**, the owner of the tables (ADR-209 §3): a topic's seeds with their passages, from `topic_passage` grouped on the seed under one topic, by `topic_passage_by_run_and_topic`; the seeds feeding no topic, by `seed_feeding_no_topic`'s primary key; and a topic's linked survivors, read from `see_also` a page of 1,000 at a time by `see_also_by_run_topic_and_occurrence`, each page asked of `Verdicts.survivingAmong` and counted, none held past its page. A subtopic's cluster sizes are read from `cluster_member` by `cluster_member_by_run_topic_subtopic_and_cluster`. **No statement added sorts**, and `EveryStatementThatSortsIsRecordedTest` is unchanged.
- **6a holds one row a topic that stands, with its seeds**: at most two hundred at the shipped floor (ADR-409 §13), each seed bounded by the seed set, as ADR-223 §9's row a seed is. The denominator is counted from the seeds' cache keys and chunk counts, which the report reads again for itself, on both of the step's branches, under the counter `Stage 6a (arrangement, seed passages counted)`; it is not taken from what 6a reads for the names.
- **5f holds one row a topic and one a subtopic** until the page is written after the last topic, as it held one a topic. A subtopic holds at least one survivor, so at worst there is a row for each survivor of a divided topic; a row is eleven values. Why it must: the JSON is moved into place before the page, which lists every row (ADR-362 §9), and each spread exists only in the pass that built its graph (ADR-096).

### 9. Which run ids move

**The code that builds this record touches `embedding`**, for §8's reads and each subtopic's spread, **`synthesis`**, for the count of overviews, `pipeline` and `ui`, and no class of `corpus`, `extraction`, `similarity`, `ledger` or `profile`. `StageModules` names `embedding` for seed measurement, embedding scoring, the arrangement and generation, and `synthesis` for the last two. So **seed measurement, embedding scoring, the arrangement and generation move**: the arrangement is approved again unless `arrangementApproved` holds `auto`, and generation writes every cluster again. The walk, byte-level reduction, extraction, content census and content redundancy do not move. No run's configuration changes, and no table changes shape.

## Alternatives refused

- **Topics no survivor is placed under on the 6a page, or a heading for each.** The page is what the operator approves, and such a topic has nothing to arrange; and the gate would mix a view of the seeds into the view of the documents.
- **The seeds feeding no topic in the relevance report or in `seed-corpus-comparison`.** Each is written before 6a, so neither can name a topic; the operator chose one report, at 6a.
- **A table of each seed's partition, as the relevance report never had.** The operator folded it into §5: no number of it is a topic's.
- **The share on `topics`.** *Documents placed* is a count there, and the share's use is the clustering's cost, which `cluster-sizes` describes.
- **A threshold at 60%, or any, on a topic's share.** ADR-085 measured no wall to put it at; a warning nobody can act on is noise.
- **The subtopics on a page of their own.** A subtopic is read against its topic, and its row under the topic's says what the division made.
- **Names on `cluster-sizes`.** 5f writes before the names are given (ADR-407 §1); naming at 5f would put a model call before the relevance floor's report and before the arrangement run that keeps names.
- **The overviews counted in the invocation account or the report.** The operator kept them to the closing line: they are generation's outcome, as its synthesis docs' count is.
- **Counting every seed passage again in a table of 5d's.** The denominator is a count of the seeds' chunks, which 6a reads again from their cache keys (§8); a table would add a thirty-ninth table keyed by a run for one number.

## Consequences

- **A topic no survivor is placed under is seen**, with its seeds, as are the seeds feeding no topic; the operator reads, before approving, which part of the seed set found nothing.
- **A divided topic's subtopics are seen at 5f**, each with its own sizes and links.
- **No alarm on a dominant topic**, and its share stands beside its sizes.
- **The window refuses a `cluster-sizes.json` an earlier build wrote**, until the clustering step writes it again.
- **Seed measurement, embedding scoring, the arrangement and generation move**, the arrangement approved again.
- **ADR-223, ADR-362, ADR-377, ADR-392 and ADR-409 carry a note at their heads**; ADR-377's and ADR-392's Tests rows are corrected in place for the tests renamed.
- **`README.md`'s list of the files written beside the database gains its `topics.html` and `topics.json` lines with this record**, since the claims check reads that list against the files the jar writes. What the rest of `README.md`, `docs/architecture.md` and `AGENTS.md` say of the reports, of `cluster-sizes` and of ADR-045's alarm is brought to this record by [#835](https://github.com/algernon28/vespera/issues/835).

## What this does not decide

- **How the three renderings draw a topic no survivor is placed under**: none, by ADR-396 §2; and how they draw the rest: [#833](https://github.com/algernon28/vespera/issues/833).
- **Whether a topic's subtopics are named on `cluster-sizes`** once a later record names them before 5f.
- **A seed's passages that lie in no topic that stands**, which no table keeps (ADR-409 §2).

## Tests

| Class | What it holds |
| --- | --- |
| `pipeline.TheTopicsAnalysisReportTest.thePageListsEachTopicsCountsInTheOrderGiven` | two topics, one of two seeds and one document placed, one of one seed, none placed and one linked, of six seed passages: the page's title and first paragraph, whole; the table's header; each topic's row, its name, its key, its passages, its share to one place, its seeds, its documents and its links (§2, §3) |
| `…aTopicWithNoDocumentIsListedWithEachOfItsSeeds` | the topic none is placed under: its section's paragraph, whole, and a row for each of its seeds in the order given, with its passages (§2, §3) |
| `…theSeedsFeedingNoTopicAreListed` | two seeds feeding none: the section's paragraph, whole, and their rows in the order given (§2, §3) |
| `…whereNothingIsMissingEachSectionSaysSo` | every topic with a document and every seed feeding one: the two sentences, whole, and no table under either (§3) |
| `…theHowToReadParagraphsSayWhatTheShareAndTheLinksAre` | the two paragraphs, whole (§3) |
| `…theJsonHoldsWhatThePageShowsAndFollowsItsSchema` | the headline's four figures; the sections' ids; the counts table's columns and rows, the share a fraction to three places; the other two tables' rows; format 1, the schema's const; the JSON following `docs/report/topics.schema.json`, and the schema using only the eight keywords (§3) |
| `pipeline.TheTopicsAnalysisReportIsWrittenAtTheArrangementInvocationTest.aTopicNoDocumentIsPlacedUnderIsListedWithItsSeeds` | three seeds on sluices and three on pumps, one document placed under sluices and linked from pumps: `topics.json` after 6a, the topic on sluices first with its one document, its three seeds and three passages, a half of six, and no link; the topic on pumps second, named first, with no document and one link; the topic on pumps' three seeds, one passage each, in path order; no seed feeding none; the headline 2, 6, 1, 0; and the JSON following its schema (§1, §2) |
| `…aSeedFeedingNoTopicIsListed` | four seeds on sluices and two on pumps at a floor of 0.4: one topic of four passages, 0.667 of six, four seeds, one document; the two on pumps listed in path order; no topic without a document; the headline 1, 6, 0, 2 (§1, §2) |
| `…anInvocationFindingTheArrangementRecordedWritesTheTopicsAnalysisReportAgain` | the first test's fixture run to the approval twice on one profile, `topics.html` and `topics.json` deleted in between: the second invocation logs the step already recorded, both files are written again, and the JSON's rows are those the first wrote (§1) |
| `…aStopWhileNamingWritesNoTopicsAnalysisReport` | the first test's fixture, the serving engine refusing every question about sluices, the two files deleted first: the invocation fails, and neither file is written (§1) |
| `embedding.TheSeedPassagesAreCountedOnceAContentTest.aSecondPathOfOneContentAddsNothingToTheSeedPassages` | two paths of one content of three chunks each and one of two: five seed passages, not eight, and the same count without the second path of the shared content (§2, §8) |
| `pipeline.TheClusterSizesPageHasARowForEachSubtopicTest.aDividedTopicsRowIsFollowedByARowForEachSubtopic` | an undivided topic of four documents and a divided one of two subtopics given most documents first: the rows on the page, whole, in that order, the topic's subtopic cell empty, each row's five numbers its own and its share of the eight documents to one place (§5, §6) |
| `…eachRowShowsTheSpreadOfItsOwnGraph` | a divided topic whose own spread and whose subtopics' spreads differ: each row's three links its own (§5) |
| `…theHowToReadParagraphsSayWhatASubtopicsRowAndTheShareAre` | the sixth and seventh paragraphs, whole, after the fifth (§5, §6) |
| `…theJsonIsOfFormatThreeAndFollowsItsSchema` | the columns, the rows with `null` as a topic's subtopic and the shares to three places; the headline counting the topics' rows alone; format 3, the schema's const; the JSON following `docs/report/cluster-sizes.schema.json`, and the schema using only the eight keywords (§5) |
| `pipeline.TheClusterSizesPageSpeaksOfTopicsTest.theWordsThatSaidExemplarSpeakOfTopics` | amended: the header `Topic`, `Subtopic`, `Documents`, `Share of documents`; format 3 (§5) |
| `pipeline.ClusteringDividesEachTopicInvocationTest.aTopicAtTheFloorIsDividedAndItsClustersAreNumberedWithinEachSubtopic` | amended: `cluster-sizes.json` holds the topic's row, its three documents, a share of 1, its links those of its whole graph, then the three subtopics' rows, 0, 1 and 2, a document each, a third each, and no link (§5, §6) |
| `…aTopicUnderTheFloorHasNoSubtopic` | amended: `cluster-sizes.json` holds the topic's row alone, its subtopic `null` (§5) |
| `pipeline.TheGenerationStepCountsItsTopicOverviewsInvocationTest.aWrittenOverviewIsCountedInTheClosingLine` | the line on finishing ends `they were; 1 topic overview(s) written and 0 turned down -- the deliverable tree is at ` and the tree (§7) |
| `…aTurnedDownOverviewIsCountedInTheClosingLine` | an overview turned down at both askings: `0 topic overview(s) written and 1 turned down` (§7) |
| `pipeline.TheReportIsOfFormatTwoTest.theAnalysisReportsAreTwelve` | renamed from `theAnalysisReportsAreEleven`: the jar's list and the window's, `topics` in each order (§4) |
| `pipeline.EachAnalysisReportsNativeViewShowsWhatItsPageShowsTest.eachReportsTitleIsTheOneTheControlPanelLinksItBy` | amended: twelve writers, `topics`' title on its page, heading and JSON (§3, §4) |
| `pipeline.TheControlPanelKnowsEachAnalysisReportsFormatTest.eachFormatTheWindowReadsIsTheOneTheJarWrites` | amended: `topics` read at its writer's format, and `cluster-sizes` at 3 (§4, §5) |
| `pipeline.TheReportFollowsItsSchemaTest.everyFieldWrittenIsInTheSchemaAndEveryRequiredFieldIsWritten` | amended: twelve analysis reports in a run's report (§4) |
| `ui.ARowsAnalysisReportsReadTheirTitlesTest.theTwelveTitlesAreTheRecords` | renamed from `theElevenTitlesAreTheRecords`: `topics` titled *Topics found in your seed documents*, after `relevance-labelling` (§4) |
| `PipelineHoldsOnlyTheRulesOnRecordTest.everyClassOfPipelineIsOnRecord` | amended: `TopicsAnalysisReport` on record as holding no rule |

The line ADR-319 §2 gives, where groups stand without writing, is held by no test of its count of overviews: it is the same clause as §7's, and the fixture of a repeated fault asks no overview it could turn down twice.

## What the implementation owes

- **In `pipeline`**: `TopicsAnalysisReport`, `NAME` `topics`, `FORMAT` 1, its records `Seed(String path, int passages)` and `Topic(String name, String seedPath, int chunkIndex, List<Seed> seeds, int documentsPlaced, int linkedDocuments)`, `render(long seedPassages, List<Topic>, List<String> seedsFeedingNoTopic)` and `document(long seedPassages, List<Topic>, List<String> seedsFeedingNoTopic, Instant writtenAt)`, drawing the topics in the order given; `ArrangementTasklet` writing it as §1 has it, from the reads of §8, its two files named by `TOPICS_FILE_NAME` and `TOPICS_JSON_FILE_NAME`, the constants the claims check reads `README.md`'s list of files against; `ClusterSizeReport` at `FORMAT` 3, `ClusterSizeReport.Subtopic.of(int ordinal, List<Integer> sizes, Optional<RetainedEdgeSpread> spread)` and `ClusterSizeReport.Topic.divided(String seedPath, int chunkIndex, List<Subtopic> subtopics, Optional<RetainedEdgeSpread> spread)` beside `Topic.of`, the subtopics drawn in the order given, and its two paragraphs and two columns; `ClusteringTasklet` handing each divided topic's subtopics in ADR-406 §4's order with their sizes and spreads; `GenerationTasklet`'s two lines of §7; `InvocationReport`'s list of the analysis reports and their formats; and `TopicsAnalysisReport` on `PipelineHoldsOnlyTheRulesOnRecordTest`'s record, which this record's tests put there.
- **In `embedding`**: §8's reads, in `Topics`, `Placements` and `DocumentClusters`; and each subtopic's spread from the pass that built its graph, `TopicDivision` and `Clustering` keeping what `Clustering.clusterAndRecord` returns today, the whole topic's spread, as `ClusteringTest` holds it.
- **In `synthesis`**: the count of a generation run's overviews written and turned down.
- **In `ui`**: `Reports.ALL` and `Reports.TITLES` with `topics`, `AnalysisReportFile.FORMATS` with `topics` at 1 and `cluster-sizes` at 3; the javadocs that say eleven.
- **The documents** of §4 and §5's schemas are written with this record: `docs/report/topics.schema.json`, `docs/report/cluster-sizes.schema.json` and `docs/report/report.schema.json`.
