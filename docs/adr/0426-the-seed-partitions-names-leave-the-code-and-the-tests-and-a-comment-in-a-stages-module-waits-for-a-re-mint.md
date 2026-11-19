# ADR-426 — The seed partition's names leave the code and the tests, and a comment in a stage's module waits for a re-mint

- **Date**: 2026-11-19
- **Status**: accepted on 2026-11-19.
- **Decided**: by the operator, in map [#822](https://github.com/algernon28/vespera/issues/822) as [ADR-396](0396-topics-inferred-from-the-seed-sets-passages-replace-seed-partitions-and-a-survivor-is-placed-apart-from-its-score.md) §10 records it: that "seed partition" retires. Under the operator's delegation, in [#895](https://github.com/algernon28/vespera/issues/895): which names change and what each becomes (§1, §2), how a record naming a renamed test keeps naming a test that exists (§3), which comments are reworded now and which wait (§4), and the wording of the vocabulary (§5).
- **Amends**: ADR-396 §10, its *"**Seed partition** and **Seed partition's page** are retired, each saying what took its place"*: the Seed partition entry leaves `CONTEXT.md`, its words standing on the Topic entry's `_Avoid_` line (§5); and its Consequences' *"What describes the seed partition today describes it until its child is built"*, for the names of the code and the tests (§1). It carries a note at its head.
- **Amends**: [ADR-192](0192-every-loop-in-every-stage-reports-its-progress-and-no-counter-floods-the-log.md), its table's row for `Stage 6b (generation, partitions written)`, and [ADR-223](0223-5fs-size-report-6a-and-6b-go-through-one-seed-partition-at-a-time-and-what-is-still-held-says-why.md), its row C7's `(…, partitions written)`: the label is `Stage 6b (generation, topics written)`, over the topics of the arrangement (§2). Each carries a note at its head.
- **Keeps**: [ADR-337](0337-every-claim-can-fail-a-decision-is-held-once-and-a-records-tests-name-tests-that-exist.md) §5 and `docs/adr/README.md`'s rule, by which a record's note on the tests that hold it is corrected in place when a test it names is renamed, in that change, citing the ticket (§3); [ADR-368](0368-the-test-suite-holds-at-most-seven-hundred-tests-and-seven-integration-tests.md)'s `docs/retired-tests.txt`, for the tests that record deleted and nothing else (§3).
- **Keeps**: [ADR-216](0216-nothing-ships-that-no-decision-requires-and-nothing-calls-a-javadoc-states-its-own-contract-and-agents-md-carries-no-history.md) §1 and §7, the ride-along rule: a comment is corrected in a change that re-mints its module for its own reasons, and is never the only reason for the re-mint (§4); [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md)'s table, `StageModules` as it is (§6).
- **Keeps**: [ADR-122](0122-the-vocabulary-binds-our-names-not-the-prose-rendered-for-an-outside-reader.md): the `_Avoid_` lists bind the names we give and not the prose rendered for a reader outside the project (§1).
- **Settles**: [#895](https://github.com/algernon28/vespera/issues/895).

## Context

**ADR-396 §10 retired the seed partition** from `CONTEXT.md` and put the topic in its place, and left each name of the code that still said partition to the child of map #822 that rebuilt that code. [ADR-409](0409-topics-placement-names-overviews-and-clusters-within-topics-are-kept-in-new-tables-keyed-by-a-topics-first-passage.md) and [ADR-415](0415-a-topic-is-a-page-carrying-its-overview-its-subtopics-are-pages-beneath-it-and-a-see-also-link-lands-on-a-groups-page.md) rebuilt it and left the names to the map's last child, which brought the documents alone to ADR-396. What still said partition for the seed partition:

- **In `src/main`, three identifiers and a label.** `deliverable.DeliverableProgress`'s `toWritePartitions(long partitions)` and `partitionWritten()`, called by `Deliverable` and implemented by `pipeline.GenerationTasklet`'s tree progress, whose field `partitions` counts the label `Stage 6b (generation, partitions written)`. Each counts the topics of the arrangement now.
- **In `src/main`, 71 comment lines**: 16 in `deliverable`, 13 in `pipeline`, 1 in `profile`, 36 in seven classes of `embedding` and 5 in five classes of `synthesis`.
- **In `src/test`, five classes and the names inside them**: `deliverable.ThreePartitions`, `deliverable.PartitionNoSurvivorNamesStopsTheWriterTest`, `synthesis.PartitionsOfAnArrangementTest`, `deliverable.TreeIsTheSameWrittenAPartitionAtATimeTest` and `confluence.TheConfluenceRenderingIsTheSameWrittenAPartitionAtATimeTest`, a method of `confluence.PublishUpdatesOneTreeInPlaceIT`, and fixtures' constants and locals.
- **`CONTEXT.md`'s Seed partition entry**, which said each of these names was settled by the child that rebuilds its code, when no child was left.

**Eleven settled records name four of those classes in their `## Tests` sections**, and one of them the method: ADR-213, ADR-223, ADR-241, ADR-274, ADR-276, ADR-303, ADR-328, ADR-333, ADR-338, ADR-409 and ADR-415. `docs/check-claims.mjs` reads every Tests section from ADR-050 on, and fails on a name of a test class or method that no file under `src/test/java` holds; it follows no rename from one record to the next. `docs/retired-tests.txt` lists the names of the tests ADR-368 deleted, for the records before it, and refuses a line for ADR-368 or later.

**Renaming a test is already provided for.** ADR-337 §5 says a record whose section names a test that is renamed is corrected in place, in the change that renames it, citing the ticket, and `docs/adr/README.md` says the same: a record's note on which tests hold it is not a decision. [ADR-320](0320-a-publish-sends-each-page-whole-its-files-before-its-body-before-it-creates-the-next-and-says-how-far-it-has-got.md) and [ADR-339](0339-an-argument-of-the-control-panels-table-names-the-form-its-control-stands-on-six-members-of-cluster-page-are-public-for-the-website-and-its-lists-stops-and-pages-are-as-recorded.md) corrected the Tests rows of earlier records so. ADR-368's list is for deletions, and for its own records only.

**A comment costs a re-mint where a stage's version names its module.** A stage's version is the last commit touching the modules its row of `StageModules` names (ADR-058): seed measurement and embedding scoring name `embedding` and `extraction`, the arrangement and generation `synthesis`, `extraction` and `embedding`. A change to a comment in `embedding` would move every run from seed measurement on, and one in `synthesis` the arrangement's and generation's, for no change of behaviour; ADR-216 §1 and §7 forbid exactly that. No stage's version names `deliverable`, `pipeline`, `profile`, `confluence`, `website` or a class outside the eleven modules.

## Decision

### 1. No name of ours says partition for the seed partition

**No identifier under `src/main/java` or `src/test/java`, and no string or text block under `src/main/java`, holds the word partition, in any case or inside a longer word**, but for three kinds of name:

- **A constant of `Adr`**, which names a record by the title the record keeps: `CLUSTERING_RUNS_WITHIN_EACH_SEED_PARTITION`, `THE_LAST_THREE_STAGES_GO_THROUGH_ONE_SEED_PARTITION_AT_A_TIME`, `A_PARTITIONS_READS_SEEK_ONLY_ITS_RUNS_ROWS`, `TOPICS_REPLACE_SEED_PARTITIONS` and this record's.
- **A name of the retirement itself**, which also says *retire*, as ADR-396 §10 allows a name to say it "but to name its retirement": `TheVocabularyNamesTopicsAndRetiresTheSeedPartitionTest` and its methods, and the test of this record.
- **The MXF header partition's key** of SMPTE ST 377-1, `corpus.BrokenCheck`'s `MXF_HEADER_PARTITION_KEY`, a file format's word that names no seed partition.

A string the jar holds is text it prints, a progress line's label among them, and a label saying partition is false of what it counts. **A test's strings and comments are not bound**: a test may say what is no longer there, as the website's tests say the search index carries no field named partition, and a fixture of an older database creates its `cluster` table with the `partition_order` column that table had. The JavaScript tests under `src/test` hold no identifier that says it; their fixtures' folder names say topic.

### 2. What each name becomes

| Was | Is |
| --- | --- |
| `DeliverableProgress.toWritePartitions(long partitions)` | `toWriteTopics(long topics)` |
| `DeliverableProgress.partitionWritten()` | `topicWritten()` |
| `GenerationTasklet`'s tree progress, its field `partitions` | `topics` |
| the label `Stage 6b (generation, partitions written)` | `Stage 6b (generation, topics written)` |
| `deliverable.ThreePartitions` | `deliverable.ThreeTopics` |
| `deliverable.PartitionNoSurvivorNamesStopsTheWriterTest`, its `stopsBeforeAnyPartitionIsWrittenOrAnnounced` | `deliverable.TopicNoSurvivorNamesStopsTheWriterTest`, its `stopsBeforeAnyTopicIsWrittenOrAnnounced` |
| `synthesis.PartitionsOfAnArrangementTest` | `synthesis.TopicsOfAnArrangementTest` |
| `deliverable.TreeIsTheSameWrittenAPartitionAtATimeTest` | `deliverable.TreeIsTheSameWrittenATopicAtATimeTest` |
| `confluence.TheConfluenceRenderingIsTheSameWrittenAPartitionAtATimeTest` | `confluence.TheConfluenceRenderingIsTheSameWrittenATopicAtATimeTest` |
| `confluence.PublishUpdatesOneTreeInPlaceIT.aSeedPartitionWhoseClusterHoldsAPersonsPageIsLeftWithIt` | `aTopicWhoseClusterHoldsAPersonsPageIsLeftWithIt` |
| the fixtures' `THE_PARTITION_DIRECTORY`, `PARTITION_FOLDER`, `PARTITION_NAME`, `PARTITION`, `PARTITION_TITLE`, `PARTITION_TEXT`, `THE_EARLIER_PARTITION_TITLE`, `FORMER_PARTITION_TITLE`, `FORMER_PARTITION_KEY`, `partitionTitle()`, `partitionBody`, `anEarlierPartition`, `aSourceThatFailsAtItsSecondPartition` and the locals `partition` | the same names saying topic |

The two methods of `DeliverableProgress` keep their place in its contract, called once before the loop over the topics with how many there are, and once after each topic's folder and pages are written. `TopicNoSurvivorNamesStopsTheWriterTest` records every call of the progress, whatever its method, so it names neither and holds that none is made.

### 3. A record naming a renamed test is corrected in place

As ADR-337 §5 and `docs/adr/README.md` have it: **each Tests section that names a test §2 renames names it by its new name, in place, with the words "named for the topic by ADR-426, #895" beside it**, in the change that renames it. The eleven records the Context lists are corrected so, and where a section names `ThreePartitions`, which the claims check does not read as a test, it is corrected too. What each record decided is unchanged, and its other sections keep the old names, as they keep every name of the code they were written against. `docs/check-claims.mjs` needs nothing new, and `docs/retired-tests.txt` gains no line: nothing is deleted.

A rule carrying a name across records, a list of renames the check reads beside the retired one, was refused: the records would go on naming a test no file holds, and a reader following a name would find nothing under it.

### 4. A comment is reworded now where it moves no run, and later in `embedding` and `synthesis`

**The comments that say partition in `deliverable`, `pipeline` and `profile` are reworded now**, in terms of topics: no stage's version names those modules, so rewording them moves no run. **The 41 lines in `embedding` and `synthesis` wait** under ADR-216 §1, and are reworded by the first change that re-mints that module for its own reasons:

- **`embedding`**: `Clustering`, `ClusteringProgress`, `Communities`, `EmbeddingStatement`, `NearestNeighbourGraph`, `RelevanceScoring` and `RetainedEdgeSpread`.
- **`synthesis`**: `Cluster`, `ClusterExemplars` (its `Arrangement.partitionsOf`, a method no class holds, whose rule `pipeline.ArrangementTasklet`'s class comment now states), `ClusterGeneration`, `ClusterLabel` and `FilenameStem`.

A comment of a class in a module no stage's version names, or of one outside the eleven modules, says topic from this record on.

**The comment on `clustering.resolution` in the shipped copy of `application.yaml`**, `src/main/beside-the-jar/application.yaml`, which said the documents *under each exemplar*, is reworded with this record in terms of topics: a comment, it lies in no module and moves no run, and since [ADR-397](0397-the-yaml-configuration-lies-beside-the-jar-and-the-jar-writes-its-shipped-copy-where-a-file-is-missing.md) §5 compares `application.yaml` with its shipped copy by its keys, it writes no `.new` beside an operator's file.

### 5. The vocabulary

**`CONTEXT.md`'s Seed partition entry goes.** Its words stand on the Topic entry's `_Avoid_` line, which lists *seed partition and partition*, says ADR-396 retired them, and names this record as the one by which no name of ours says either but a record's title and the names of the retirement. The Seed partition's page entry stays as ADR-396 left it, retired.

### 6. Run ids

**None moves.** The identifiers, the label and the comments this record changes now lie in `deliverable`, `pipeline` and `profile`, which no row of `StageModules` names; `corpus`, which byte-level reduction names, keeps the one use of the word it holds; and nothing in `embedding`, `synthesis`, `extraction` or `similarity` changes until §4's change re-mints it for its own reasons.

## Consequences

- **A progress line reads `Stage 6b (generation, topics written)`** where it read `(…, partitions written)`; it counts what it counted.
- **The claims check passes over the renamed tests by their new names**, and a record read today names a test that exists.
- **41 comment lines keep saying partition** in `embedding` and `synthesis` until a change re-mints each module, and say of a topic what they said of a seed partition, which was the unit clustering ran within as a topic is now.
- **A new name that says partition fails the build**, but one that names the retirement, a record's title or the MXF header partition.

## Alternatives refused

- **Leave the settled names as they are.** The records would stay readable without a change, but the code would keep naming a thing that no longer exists, against ADR-396 §10, and the map's retrospective would stay open.
- **A list of renames the claims check reads beside `docs/retired-tests.txt`.** It would carry an old name across records without editing them, but every record so carried would name a test no file holds, which is what ADR-337 §5 forbids, and the in-place rule already covers a rename.
- **Reword every comment now.** It would move seed measurement, embedding scoring, the arrangement and generation for no change of behaviour, the sweep ADR-216 §7 refused.

## Tests

- `pipeline.TheSeedPartitionIsRetiredFromEveryNameTest`, three tests: `…noNameOrTextOfTheJarsCodeSaysTheRetiredWord`, the identifiers and literals of `src/main/java` (§1, §2); `…noNameOfTheTestsSaysTheRetiredWordButARecordsOrItsRetirements`, the identifiers of `src/test/java` but the records' constants and the names saying retire (§1); `…noCommentOfAModuleNoStageNamesSaysTheRetiredWord`, the comments of every class in a module `StageModules` does not name, or in none (§4).
- `TheVocabularyNamesTopicsAndRetiresTheSeedPartitionTest.theSeedPartitionHasNoEntryAndTheTopicsAvoidLineRetiresIt`: no entry is headed Seed partition, and the Topic entry's one `_Avoid_` line lists the seed partition and names this record (§5). Its `…noDefinitionButTheRetiredOnesNamesASeedPartition` reads the Seed partition's page entry as the one retired entry left.
- `pipeline.TheGenerationStepCountsItsTopicOverviewsInvocationTest.theTreesTopicsAreCountedUnderTheirOwnLabel`: through 6b over one topic, the tree's counter writes its one line under `Stage 6b (generation, topics written)` (§2).
- `deliverable.TopicNoSurvivorNamesStopsTheWriterTest.stopsBeforeAnyTopicIsWrittenOrAnnounced`: the progress the writer is handed records every call of every method, and none is made (§2).
- `docs/check-claims.mjs`'s check of the Tests sections holds §3, as ADR-337 §5 has it.

## What this does not decide

- **When `embedding` and `synthesis` are next re-minted**: §4's lines wait for whatever change that is.
- **The words of the decision records**: a record keeps the names of the code it was written against, but where §3 corrects its Tests section.
