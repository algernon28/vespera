# ADR-222 — A stage's version names `pipeline` only while `pipeline` holds a rule that shapes its output: content census, content redundancy and arrangement stop naming it, and seed measurement, embedding scoring and generation keep it for the eight rules named here

- **Date**: 2026-10-10
- **Status**: accepted.
- **Built**: §2's edit to `StageModules` was built on 2026-10-10 in the change that carries this record (#353), in the commit after the one that wrote it; `RunIdentityGoldenTest`, `PipelineHoldsOnlyTheRulesOnRecordTest` and `StageThreeMeetsTheHashIndexInvocationTest` were red between the two.
- **Amends**: [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md), its Decision's parenthesis *"(plus the specific stage-orchestration class in `pipeline` that drives it, if that file lives outside the module's own path)"*. Since ADR-157 §1 that parenthesis has been applied as the whole of `pipeline`, named by each of the six stages from content census to generation. It now reads: a stage's version names `pipeline` only while a class of `pipeline` holds a rule that shapes that stage's output, and the table of rules under *The survey*, which §3 makes the allowance, is the record of which do.
- **Amends**: [ADR-157](0157-a-stage-asks-for-its-run-after-its-own-gate-one-helper-mints-every-run-and-every-step-is-named-once.md) §1's table as `StageModules` carries it, in three rows: `content-census`, `content-redundancy` and `arrangement` lose `pipeline`. The other five rows stand.
- **Amends**: [ADR-219](0219-stage-3s-grouping-names-the-index-on-the-run-and-the-clause-ships-with-the-next-change-to-similarity.md), the first row of its Sequences, *"An invocation reaches stage 4b; a build that moves `pipeline` alone is installed; the next invocation"*, answered *Yes*. With §2 built the answer is no: content census no longer names `pipeline`, so that invocation arrives at the content census already finished and groups nothing. Its other *Yes*, a stage 3 stopped over one corpus root, stands, and so do its clause and when the clause ships. Its Tests note is corrected in place for the test this changes.
- **Keeps**: ADR-058's mechanism, the last commit touching a module's path, and its accepted failure, that a comment moves a version; [ADR-188](0188-stage-1s-verdict-rules-and-content-identity-live-in-corpus-which-still-knows-no-stage.md) §3, [ADR-189](0189-stage-2s-judging-rules-and-the-extractor-identitys-composition-live-in-extraction-which-still-knows-no-stage.md) §4 and §5, and [ADR-190](0190-stage-6bs-loop-and-6as-lead-document-rule-live-in-synthesis-which-still-knows-no-stage.md) §5, in what each says stays in `pipeline`; [ADR-048](0048-walk-and-run-identity.md)'s chain of upstream runs.
- **Decides** [#353](https://github.com/algernon28/vespera/issues/353), in the form its body gives for the case found: *"whether any stage keeps `pipeline` because Wave 2 left a rule behind. If one does, that stage stays as it is, and the ADR names the rule."* Three do.

## Context

Six stages name `pipeline` in their implementation version (`pipeline/StageModules.java`, read at `90b02e5`): content census, content redundancy, seed measurement, embedding scoring, arrangement and generation. So a change to any file of `pipeline`, a progress line or a command-line option among them, mints all six again. Generation has no cache, so each such change costs its calls again.

ADR-188, ADR-189 and ADR-190 moved the rules [#320](https://github.com/algernon28/vespera/issues/320) tabled out of `pipeline`, and #353 takes `pipeline` out of the six lists once none is left. Its prerequisite is that none is left, and its question, for every class of `pipeline`, is: **could a change to this class alter a verdict, a cache key, a cluster or a sentence of the deliverable?** #320's table was of stages 1, 2 and 6. Nothing had asked the question of stages 3 to 5, nor of what ADR-190 §5 left in `GenerationTasklet`.

Taking `pipeline` out of a stage whose rule is still there is the failure ADR-058 exists to prevent: the rule changes, the run id does not, and a verdict or a page written by the old rule is served as the new one's.

## The survey

Every class under `src/main/java/io/algernon/vespera/pipeline/` at `90b02e5`, ninety-five and `package-info`. The classes that run stages 3 to 6b, their gates and `StageRuns` were read as code, every line that is not a comment. The rest were classed by what they write and name: no class outside the tables below writes a verdict, a cluster, a vector, a cache row or the deliverable, by a search of every write the capability modules offer and of every use of `VerdictKind`; and what stays in `pipeline` of stages 1 and 2 is what ADR-188 §3 and ADR-189 §4 say stays.

**A class holds a rule** when it decides something itself, by a condition, a mapping, an order, a constant or a text of its own, and what it decides ends in a verdict (which occurrence, which kind, which reason), a cache key, a cluster or the text of the deliverable. **A class is wiring** when it hands a capability module the recorded values that module's rule asks for and acts on the answer. Wiring can be wrong, and a wrong wiring changes output; that is a defect a behaviour test holds, as ADR-188 §3 says of its adapter, and not a rule.

### The rules found, by the stage each shapes

| # | Stage | Class | The rule | What it ends in |
|---|---|---|---|---|
| 1 | seed measurement | `SeedConversions.formatFor` | A seed the cross-format floor stopped is converted as `UNRECOGNISED` (ADR-092, ADR-100) | What a seed is sent to the converter as, so its text and every score against it |
| 2 | embedding scoring | `RelevanceFloor.stateFor` | The floor is applied only where no answer is recorded for the seed set, or every recorded answer was given under the embedder identity this run scored under (ADR-088) | Whether any `below-threshold` verdict is written |
| 3 | embedding scoring | `RelevanceFloorTasklet` | The reason every `below-threshold` verdict carries, and that nothing is removed where the vectors carry no single embedder identity | The verdict's reason, and whether one is written |
| 4 | generation | `GenerationTasklet.exemplarsOf`, `openingChunkOf` | A document is sent as its leading chunk, and one nothing was chunked from is not sent (ADR-190 §5 keeps both here) | The text every synthesis doc is written from |
| 5 | generation | `GenerationTasklet.whyUnwritten` | What this invocation found stands over a fault on record, and a cluster with neither is *not reached* (ADR-174) | The sentence under an unwritten cluster's heading |
| 6 | generation | `GenerationTasklet.survivorPictures` | A document detected as an image, a BMP or a video lists no picture | The pictures under a membership entry |
| 7 | generation | `GenerationTasklet.profileValues`, `textOf` | The index states every key of the profile, read off its record, and one with no value as empty (ADR-186) | The index's table of profile keys |
| 8 | generation | `GenerationTasklet.survivorsFor` | A survivor with no score on record is listed with 0.0 | A row of `documents.csv` |

A reworded verdict reason counts, as ADR-188's Context counts it for stage 1.

### The stages with no rule in `pipeline`

- **Content census.** `ContentCensusTasklet` calls `DocumentFrequency.measure` and `ConfidenceDistribution.measure` with the two run ids, and writes a page for the operator. `ConfidenceDistributionReport` is that page.
- **Content redundancy.** `RedundancyGate` reads the floor, which the run records. `RedundancyBoilerplate`, `RedundancySignatureItemWriter` and `RedundancyResolutionTasklet` hand it and the run ids to `BoilerplateShingles`, `RedundancySignatures` and `RedundancyResolution`. `RedundancyJobConfiguration` reads the run's survivors and builds the index stage 4b reads through.
- **Arrangement.** `ArrangementTasklet` hands `Arrangement` and `LeadDocument` the membership, the scores and two lookups, and records what comes back. `titleOf` is a lookup where `GenerationTasklet.openingChunkOf` is a rule, because it has no branch of its own: it hands back what `DocumentTitles` holds under the recorded cache key, and what a cluster is called where no title is on record is `LeadDocument`'s to decide. `ArrangementGate` decides whether generation runs, and the arrangement it approves is in generation's recorded settings. `ArrangementReport` is a page for the operator.

### Every other class

| What it is | Classes | Why it holds no rule |
|---|---|---|
| Run identity | `StageModules`, `StageRuns`, `RunMint`, `UpstreamRuns`, `InvocationRuns`, `RunCompletion`, `TaskletSteps`, `StepNames` | What a run records it was derived from. A change to it moves the run id itself, and `RunIdentityGoldenTest` holds every character |
| A profile value read into a run's recorded settings, or a gate | `GenerationModel`, `GenerationContextWindow`, `RelevanceScoreFloorValue`, `DegenerateOutputConfidenceFloor`, `ExtractionAttempt`, `RedundancyGate`, `EmbeddingModelGate`, `SeedGate`, `UsableSeedGate`, `ArrangementGate`, `StageFiveGates` | The value read is recorded on the run, so a different reading is a different run. A gate decides whether a stage runs and never what it writes |
| Stage 5's steps that hold no rule | `SeedExtractionItemProcessor`, `SeedExtractionItemWriter`, `SeedExtractionOutcome`, `SeedExtractionJobConfiguration`, `SeedCorpusComparisonTasklet`, `EmbeddingScoringTasklet`, `RelevanceScoringTasklet`, `ClusteringTasklet`, `RelevanceReportTasklet` | Each hands `extraction`'s and `embedding`'s rules their values: `UsableText`, `SeedCorpusComparison`, `HybridChunker`, `ChunkEmbedder`, `RelevanceScoring`, `Clustering`. The reason of a seed whose file would not open is recorded against a seed, which is no verdict, and stops stage 5 |
| The census and stages 1 and 2 | `CensusTasklet`, `ByteLevelReductionTasklet`, `ExtractionJobConfiguration`, `ExtractionItemProcessor`, `ExtractionItemWriter`, `ExtractionOutcome`, `ExtractionCircuitBreaker`, `ExtractionFaultRecorder`, `ExtractionHealthCheckListener`, `ConversionDispatch`, `PendingConversions`, `SidecarRecovery`, `DoclingControlConversion`, `OccurrenceReader`, `UnrecordedOccurrences`, `ReviewListListener` | ADR-188 §3 and ADR-189 §4. Neither stage names `pipeline`, and ADR-189 §5 names the three things here that can still change stage 2's outcome. This record changes nothing of them |
| Labelling | `AutoLabelling`, `LabelIngestion`, `LabelFileReader`, `RelevanceLabelFile`, `DocumentOpening` | A relevance label is no verdict. The floor `AutoLabelling` writes is `LoseNoDocumentationFloor`'s, in `embedding`, and the number is recorded on the scoring run |
| Pages and lines for the operator | `ReportPage`, `ArrangementReport`, `ClusterSizeReport`, `ConfidenceDistributionReport`, `FormatMixReport`, `RelevanceLabellingReport`, `ReviewListReport`, `SeedCorpusComparisonReport`, `NextAction`, `InvocationAccount`, `StageProgress`, `StatementProgress`, `ReportedStatements`, `TimedStatement`, `StartUpIndexAnnouncement`, `StepFailure` | Written for the operator, or as the invocation account, never in the deliverable |
| The command and start-up | `VesperaCli`, `VesperaCommand`, `VesperaJobConfiguration`, `CorpusRootCheck`, `ProfileShapeCheck`, `MisshapenProfileRefusal`, `WorkingDirectoryOption`, `WorkingDirectoryPreparer`, `WorkingDirectoryLock`, `WorkingDirectoryInUseRefusal`, `TemporaryFilesInTheWorkingDirectory` | They run before any stage or refuse to run one |
| Exceptions | `ArchiveGoneException`, `DoclingDidNotComeBackException`, `DoclingKeepsDroppingConnectionsException`, `DoclingRunsAnotherImageException`, `ExtractorStoppedAnsweringException`, `NoGenerationModelNamedException`, `NoUpstreamRunException`, `ServiceScopeFailureException`, `WorkingDirectoryInUseException` | Each stops a step and writes nothing |

### Wiring that can still change an outcome

Named so that nobody reads the survey as having closed it, as ADR-189 §5 names its three:

- **Which run a step reads.** Which run's survivors are gone through, and under which run a cache key is looked up, are arguments `pipeline` passes (`stageRuns.upstream(…)`, `survivors(measurementRun)`).
- **The usable seeds.** `EmbeddingScoringTasklet` and `RelevanceScoringTasklet` each take the seed walk's occurrences less the unusable seeds on record.
- **`ChunkingRule.DEFAULT`.** Four classes name it at the call that chunks. The rule and its identity are `extraction`'s, and the identity is part of a vector's key.
- **What a step discards when it starts again.** A step that finds its run unfinished discards that run's verdicts of the kind it writes before it writes any: `RedundancyResolutionTasklet` the `redundant-with` ones, `ByteLevelReductionTasklet` stage 1's three kinds, and `ExtractionJobConfiguration` the `extraction-failed` ones that resolved a fault. Which kind is named there is `pipeline`'s, and a wrong one would leave a stale verdict or remove a standing one.

## Decision

### 1. The rule

A stage's version names `pipeline` only while a class of `pipeline` holds a rule that shapes that stage's output, and the table of rules under *The survey*, which §3 makes the allowance, says which. A stage that names it for no rule on record is too eager, and one with a rule on record that does not name it is too lazy; the guard of §4 fails on both.

### 2. Three stages stop naming `pipeline`

| Stage | Modules before | Modules after |
|---|---|---|
| content-census | `similarity`, `extraction`, `pipeline` | `similarity`, `extraction` |
| content-redundancy | `similarity`, `extraction`, `pipeline` | `similarity`, `extraction` |
| arrangement | `synthesis`, `extraction`, `embedding`, `pipeline` | `synthesis`, `extraction`, `embedding` |
| seed-measurement | `embedding`, `extraction`, `pipeline` | unchanged, for rule 1 |
| embedding-scoring | `embedding`, `extraction`, `pipeline` | unchanged, for rules 2 and 3 |
| generation | `synthesis`, `extraction`, `embedding`, `pipeline` | unchanged, for rules 4 to 8 |

The order of the modules left is unchanged. `byte-level-reduction` and `extraction` are not edited.

### 3. The eight rules are the allowance

The table of rules above is the whole of what `pipeline` may hold. A ninth is added to it by a record, with the stage it shapes, and that stage names `pipeline` from the same change. A rule leaves the table in the change that moves it out of `pipeline`, and a stage whose last rule leaves stops naming `pipeline` in that change, under this record and with no new one.

Where each would go, were it moved. This record moves none:

| # | Home | What stands in the way |
|---|---|---|
| 1 | `extraction`, beside `DoclingExtractor.convert` | Nothing: `extraction` names `corpus`'s detection enumerations already (ADR-100) |
| 2, 3 | `embedding`, beside `RelevanceLabels` and `RelevanceScoring.scoredBelow`, handed the floor's number | Nothing |
| 5, 8 | `synthesis`, beside `Unwritten` and `ListedSurvivor` | Nothing |
| 6 | `extraction`, beside `DocumentPictures` | Nothing |
| 4 | none today | The leading chunk is `extraction`'s to read, the score is `embedding`'s and the question is `synthesis`'s, and none of the three may name another. ADR-190 made it a callback `pipeline` implements for that reason |
| 7 | none today | `synthesis` may not name `profile`, and no stage's version names `profile` |

So generation keeps `pipeline` after every move that needs no new boundary, until a record decides rules 4 and 7.

### 4. The guard is a listed allowance, read off the compiled classes

`PipelineHoldsOnlyTheRulesOnRecordTest`, beside `OnlyPipelineNamesSpringBatchTest` and reading the classes as it does:

- **Every class of `pipeline` is on record**, as holding one of §3's rules or as holding none. A class added to `pipeline` fails the test until the change that adds it answers #353's question for it and lists it. The answer is a person's; the test makes it impossible to add a class without giving one.
- **The classes of `pipeline` that name `VerdictKind`, and the ones that name `Deliverable`, are the ones on record.** Eight name `VerdictKind`. Two name `Deliverable`: `GenerationTasklet` calls it, and `NextAction` reads the name of its directory for the closing line and writes nothing there. A class that starts to write or to choose a verdict, or to write the deliverable, fails it.
- **A stage names `pipeline` exactly while a rule of it is on record.** Read off `StageModules` and the allowance: this is the test that was red until §2 was built, and it turns red again when a rule is added without its stage, or moved out and its stage left naming `pipeline`.
- **The classes of the three stages that stopped naming `pipeline` name the capability types on record, and no other.** Those stages are the only place a rule can now be added and change no run id, so their eight classes are held closer: `ContentCensusTasklet`, `RedundancyGate`, `RedundancyBoilerplate`, `RedundancySignatureItemWriter`, `RedundancyJobConfiguration`, `RedundancyResolutionTasklet`, `ArrangementTasklet` and `ArrangementGate`. The test lists, for each, every type of `corpus`, `extraction`, `similarity`, `embedding` and `synthesis` its compiled form names. A new collaborator fails it until the question is answered for what the class does with it. A type no longer named fails it too, so the list does not go stale.

**What it cannot see**: a rule added to a class already on record that names no verdict kind, does not write the deliverable and, in one of those eight, needs no capability type the class did not name already: a condition on values it already holds, or a constant. In the classes of seed measurement, embedding scoring and generation a missed rule still moves the run id, because those stages name `pipeline`. In the eight it does not, and there the question is asked by whoever reads the change; the lists are what tell them where to ask it.

**Considered and not taken.** A pinned list of every numeric constant in `pipeline` would catch a threshold given a name. It would also fail on a page size, and draft pull request 478 adds one to `UnrecordedOccurrences`. A review item alone fails on nothing.

**A class another change adds to `pipeline`** is listed by whichever of the two changes lands second, when the merge with `main` turns the first test red. Pull request 478 as read on 2026-10-10 adds no class to `pipeline` and no use of `VerdictKind` in a class that had none. Of the eight classes held closer it edits `RedundancyResolutionTasklet` alone, where it stops naming two constants of `SimilarityStatement` and renames one method of `ResolutionProgress` that the class implements, and names no type it did not name. So the guard passes with it merged.

**When a merge turns the fourth test red**, whoever merges second reads what the class now does with the type the failure names. If the class only hands it values or acts on its answer, the type is added to that class's list. If the class decides something by it, that is a ninth rule: it goes to the capability module, or a record adds it to the allowance and the stage names `pipeline` again in the same change.

## Consequences

- **Run ids move once, for content census and every stage after it.** Content census, content redundancy and arrangement are minted again because their lists change. Seed measurement, embedding scoring and generation are minted again because each names its upstream run (ADR-048), and because the edit to `StageModules` is itself a commit to `pipeline`, which they still name. Byte-level reduction and extraction keep their run ids. The arrangement is a new one, so `arrangementApproved` must name it, and generation's calls are made again.
- **A change to `pipeline` alone now spares content census and content redundancy, and nothing after them.** It still mints seed measurement and embedding scoring, which name `pipeline`, and through them arrangement and generation. What #353's comment of 2026-10-07 asks for, that a change to a command-line option no longer costs generation's calls, is not reached by this record: it is reached when rules 1 to 3 have moved and rules 4 to 8 have moved or been decided otherwise.
- **Persisted names, cache keys and operator text are unchanged.** No `run.stage` value, no recorded setting, no cache key and no line an operator reads differs. The conversions and vectors on record are found in their caches under the new runs.
- **Stage 3 no longer meets `shingle_by_hash` after a build that moves `pipeline` alone**, one of the two sequences ADR-219 found. It still meets it when it was stopped over one corpus root while another reached stage 4b, so ADR-219's clause is still owed.
- **No test is weakened.** `RunIdentityGoldenTest` changes in three module lists and nowhere else. Two tests that rested on a commit to `pipeline` minting content census again are changed with the decision, as Tests says.

## Tests

- **`RunIdentityGoldenTest`**: `contentCensus` and `contentRedundancy` expect `similarity+extraction`, and `arrangement` expects `synthesis+extraction+embedding`. The three were red until §2 was built. Its other five tests are not edited.
- **`PipelineHoldsOnlyTheRulesOnRecordTest`** (new): §4's four tests. The first two passed before §2 was built, and the third was red until it was. The fourth was added after §2 was built and holds the three stages it freed.
- **`StageThreeMeetsTheHashIndexInvocationTest`**: its first test claimed that a build moving `pipeline` alone has stage 3 run again with `shingle_by_hash` there. It now claims that such a build leaves content census's run and content redundancy's as they were and groups nothing. It was red until §2 was built. ADR-219's claim about the plan of the grouping with the index present moves, word for word, to the end of the third test, where the index is also present.
- **`UpstreamRunOverAReusedWalkTest`**: the row for a commit to `pipeline` named content census as the first stage it moves and content redundancy as the one after. It names seed measurement and embedding scoring, which is true before and after §2, so the test stays green.

## What this does not decide

- **Whether and when the eight rules move.** §3 says where six could go. [#479](https://github.com/algernon28/vespera/issues/479) holds it.
- **Rules 4 and 7**, which have no home under today's module boundaries. Which boundary changes for them, if any, is #479's open decision.
- **Stage 2's three**, which ADR-189 §5 left in `pipeline` under a version that does not name it.
- **`docs/architecture.md`**, which states no stage's module list and is not edited.
