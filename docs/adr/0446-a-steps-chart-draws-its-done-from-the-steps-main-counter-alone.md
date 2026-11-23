# ADR-446 — A step's chart draws its `done` from the step's main counter alone

- **Date**: 2026-11-23
- **Status**: accepted on 2026-11-23.
- **Release**: patch
- **Decided**: by the operator, on [#934](https://github.com/algernon28/vespera/issues/934), its second part, of the three ways it set out: that the `done` of each step's chart follows that step's main counter alone, one counter named for each step (§1), and not a line for each counter's label, nor a `done` started again whenever the label changes; that a line of another counter of the step moves it nowhere, so that a sample taken before the main counter has said a count in the invocation holds no `done`, and one taken after holds the main counter's latest count, whatever counter spoke last (§2); that a step of one counter keeps it; which counter is each step's main one, §1's table; and that the census's chart and the report's own counts of a step are not touched (§3). This record's own: that a statement's line moves `done` no more than another counter's (§2), that the seed-corpus comparison, which says no counter, has no main counter (§1), the labels matched whole (§1), and the tests.
- **Amends**: [ADR-432](0432-each-step-has-a-chart-beneath-its-legs-bars-the-output-spans-the-column-above-the-legs-and-the-cockpit-holds-no-tabs.md) §3, its bullet *"**`done`** at a sample is the count done of the counter the step itself said last in this invocation, `N` of *`N of T (P%)`* or *`N so far`* … Where the step has said none in this invocation, `done` has no point at that sample"*: the count done of the step's main counter, §1's, as it said it last in this invocation; where that counter has said none in this invocation, `done` has no point at that sample, whatever else the step said (§2). The census's `done` in the same bullet stands.
- **Keeps**: ADR-432 §3's lines and their order, its kinds' values and its six lines at most; §4's file, its shape and `format` 1; §5's four states and their words, *no count reported* among them; §6's freezing. [ADR-438](0438-a-steps-chart-is-kept-under-its-run-as-it-freezes-and-an-invocation-finding-the-step-finished-earlier-draws-it.md), a chart kept under its run as it freezes and drawn by an invocation finding the step finished. [ADR-418](0418-every-stages-chart-is-written-empty-as-the-job-of-vespera-run-begins-and-frozen-only-within-its-invocation.md) §4 and [ADR-412](0412-stage-0s-chart-is-kept-under-the-walk-the-census-keeps-and-a-new-key-empties-no-other-stages-chart.md) §2, the census's chart and its entries walked. [ADR-268](0268-the-progress-page-shows-what-the-stage-that-removes-occurrences-has-removed-and-draws-to-the-data-viz-method.md) §7 and [ADR-362](0362-an-invocations-report-is-one-json-file-drawn-as-static-html-through-a-mustache-template-and-no-command-opens-anything.md) §3, the report's chart of counts, drawn from the label of the latest counter the running step said, and the report's step, its progress line and its counted and total, read off the step's last line. [ADR-192](0192-every-loop-in-every-stage-reports-its-progress-and-no-counter-floods-the-log.md) §4, the counters, their labels and their lines. [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md), no stage's version naming `pipeline`.
- **Settles**: [#934](https://github.com/algernon28/vespera/issues/934), its second part.

## Context

**Where a chart's `done` comes from.** `JobSampler` keeps, for each step, the last line its thread said whose whole text is a counter's, *`<label>: N of T (P%)`* or *`<label>: N so far`*, or a statement's, *`<label>: about P% of R rows`* (`lastLine`, filled by its `LineAppender` from `StageProgress`'s and `StatementProgress`'s loggers). At each sample of a step's chart, `takeASample` reads `done` from that line where it is a counter's, whatever its label, and holds no `done` where it is a statement's (ADR-432 §3).

**A step says several counters.** Each counter is a `StageProgress` over one loop, labelled with the stage and, after a comma, the unit it counts (ADR-192 §4, `StageProgress.over`). Of the shipped steps:

| Step | The counters it says |
| --- | --- |
| `byte-level-reduction` | broken check, sizes read, content hash, duplicates recorded |
| `extraction` | `Stage 2 (extraction)`, and, in its listener once the step's execution is complete, faults resolved |
| `content-census` | files checked, and a statement's line |
| `redundancy-signature` | one |
| `content-redundancy` | near-duplicate candidates, occurrence profiles, near-duplicate components, near-duplicate verdicts, containment, containment candidates, and the lines of its statements |
| `seed-extraction` | `Stage 5a (seed extraction)`, seeds recorded |
| `seed-corpus-comparison` | none: its statements' labels hold a `/`, which no label of a counter's or statement's shape admits (`StageProgress.LABEL_SHAPE`), so the sampler keeps none of its lines |
| `embedding-scoring` | corpus survivors, corpus survivor chunks, seeds, seed chunks |
| `relevance-scoring` | seed cache keys read, corpus survivors, seed vectors read |
| `relevance-floor` | below-threshold verdicts |
| `clustering` | topics, and for each topic its cache keys read and its comparison blocks |
| `relevance-report` | sampled survivors, labels matched, model labels matched |
| `arrangement` | scores read, members gathered, seed passages counted, seed cache keys read, page topics, and for each topic its clusters and its page rows |
| `generation` | cluster documents opened, clusters, survivors copied, pictures listed, code copied, topics written, cluster files written, membership entries, manifest rows, survivors listed |

Each counts its own loop to its own total, and several of them interleave. A chart whose `done` is the last counter said follows one loop, then another: content redundancy's climbs over its near-duplicate candidates to their total, falls to the first occurrence profiles' count, climbs again, and so on through its six, and the extraction's, once every document is read, falls to the faults its listener resolves. The line reads as work undone.

## Decision

### 1. Each step has one main counter, named by its label

| Step | Its main counter's label | Said by |
| --- | --- | --- |
| `census` | none: its chart's `done` is the entries walked its own line says (ADR-412 §2), unchanged | `WalkRecorder` |
| `byte-level-reduction` | `Stage 1 (byte-level reduction, content hash)` | `ByteLevelReductionTasklet` |
| `extraction` | `Stage 2 (extraction)` | `ExtractionItemProcessor` |
| `content-census` | `Stage 3 (content census, files checked)` | `ContentCensusTasklet` |
| `redundancy-signature` | `Stage 4a (redundancy signatures)` | `RedundancySignatureItemWriter` |
| `content-redundancy` | `Stage 4b (redundancy resolution, near-duplicate candidates)` | `RedundancyResolutionTasklet` |
| `seed-extraction` | `Stage 5a (seed extraction)` | `SeedExtractionItemProcessor` |
| `seed-corpus-comparison` | none: it says no counter | — |
| `embedding-scoring` | `Stage 5c (embedding scoring, corpus survivors)` | `EmbeddingScoringTasklet` |
| `relevance-scoring` | `Stage 5d (relevance scoring, corpus survivors)` | `RelevanceScoringTasklet` |
| `relevance-floor` | `Stage 5e (relevance floor, below-threshold verdicts)` | `RelevanceFloorTasklet` |
| `clustering` | `Stage 5f (clustering, topics)` | `ClusteringTasklet` |
| `relevance-report` | `Stage 5 (relevance report, labels matched)` | `RelevanceReportTasklet` |
| `arrangement` | `Stage 6a (arrangement, members gathered)` | `ArrangementTasklet` |
| `generation` | `Stage 6b (generation, survivors copied)` | `GenerationTasklet` |

- **A line is the main counter's where its whole label**, the text before its `: `, **is the one the table names for the step whose thread said it**, character for character: a label that begins as the main one and goes on, as `Stage 5f (clustering, cache keys read, topic 1 of 2)` does, is another counter's. The label is matched as said, whether its class writes it whole or, as content redundancy's does, from a prefix and the unit after it.
- **Every other counter of a step is another counter**, and so is a counter whose label the table does not name: what moves `done` is the table, not the counters a step happens to say.
- **The seed-corpus comparison has no main counter**, and the census keeps its own line: neither is moved by this record.

### 2. A chart's `done` is the main counter's latest count in the invocation

**At each sample of a step's chart, `done` is the count `N` of the latest line the step's main counter said in this invocation**, *`N of T (P%)`* or *`N so far`*.

- **Where the main counter has said no line in this invocation**, the sample holds no `done`, whatever other counter or statement the step said: a step that has so far said only another counter draws no `done` point at that sample.
- **Once the main counter has said a line, no later line of the step moves `done`** but another of the main counter's: neither another counter's, nor a statement's, nor one said in the step's listener after its execution is complete. `done` stays the main counter's latest count until it says the next.
- **A step of one counter**, the content census, the redundancy signatures and the relevance floor, draws `done` as it did.
- **A line said in an earlier invocation is never the main counter's latest**: each job begins with none kept, as it begins with no last line kept now.
- **A step that ran and whose main counter said nothing in the invocation** holds no `done` point at any sample, and its chart says *no count reported* (ADR-432 §5), its kinds' lines drawn as they are.

### 3. What this does not move

**The step's chart alone.** The report keeps reading each step's last line, whatever its label, for the report's step, its `progress` line and its `counted` and `total` (`JobSampler.reading`, `InvocationReport.Step`, ADR-362 §3), from which the control panel draws each step's bar and its percent; and the report's chart of counts keeps drawing the samples of the label of the latest counter the running step said (`JobSampler.countsData`, ADR-268 §7, ADR-362 §3). Each is as it is now.

**The census's chart**, whose `done` is the entries walked (ADR-412 §2, ADR-418 §4), **and the kinds' lines of every chart** (ADR-432 §3) are unchanged.

**A chart kept under its run before this record** (ADR-438 §1) is drawn as it was kept, its `done` as it was drawn then.

### 4. The modules, and run ids

**In `pipeline`**: `JobSampler` alone, which names each step's main counter and keeps each step's latest line of it beside the last line it keeps now; it stays listed in `PipelineHoldsOnlyTheRulesOnRecordTest` as holding no rule, a chart deciding no verdict, cache key, cluster or text of the deliverable. **No counter's label changes**, so no class that says one changes. No stage's version names `pipeline` (ADR-222), so **no run id moves**. The chart file's `format` stays 1, and `report.json`, its `format` and `report.html` are unchanged.

## Alternatives refused

- **A `done` line for each counter's label.** A chart of content redundancy would draw six lines of `done` over different totals, and ADR-432 §3's six lines at most would not hold; the window's legend and its colours are set for one `done` and the kinds.
- **A `done` started again whenever the label changes.** The line would still fall at each change, to the next loop's first count, and read as work undone; it would follow whichever loop spoke last, as now.
- **The counter of the largest total taken as each step's main one.** Totals change between corpora, and between invocations of one step; the chart would change counter under the operator's eyes.
- **A statement's line keeping the effect ADR-432 §3 gives it, `done` holding no point at its sample.** A statement says a share of rows of a read, not a count of the step's items; a hole in the line where one is said reads as the step stopping.
- **The report's counted and total, and its chart of counts, moved with the step's chart.** They say what the step is doing now, the last loop it reports, which is what the bar's percent and the report's progress line are for; the operator settled the chart's `done` alone.

## Consequences

- **A step's chart draws one count, climbing**, the count of the step's main loop, whatever else the step reports between its lines.
- **A step whose main counter has not spoken yet draws `done` from the reading after it first does**: byte-level reduction's chart holds no `done` while it checks for broken files and reads sizes, and the generation's while it writes its pages before it copies.
- **A step whose main counter says nothing in an invocation says *no count reported*** even where another of its counters spoke: content redundancy where no occurrence is signed, the generation where it copies no survivor.
- **The chart's `done` and the bar's percent can disagree** while a step reports another loop: the bar follows the last counter said (§3), the chart the main one.

## Known limits

- **The table is kept by hand.** A counter renamed in its class, or a step whose main loop moves to another counter, draws no `done` until `JobSampler`'s table is changed with it; the tests below hold each label as the shipped step says it.
- **A chart kept under its run before this record** keeps the `done` it was drawn with (§3).

## What this does not decide

- **The report's step, its progress line, its counted and total and its chart of counts**, still read off the step's last line (§3).
- **A `done` for the seed-corpus comparison**, which says no counter.

## Tests

| Method | What it holds |
| --- | --- |
| `engine.pipeline.EachStepsDoneFollowsItsMainCounterAloneTest.eachStepsDoneIsItsMainCountersAndNoOtherCounterOfTheStepMovesIt` | §1, §2: each of the thirteen steps says its main counter, by the label the shipped step says, then, for the ten that say another, that one: each chart's `done` is its main counter's count at the reading while it ran and at the one that froze it, never the other's; the content census, the redundancy signatures and the relevance floor, of one counter, keep theirs |
| `engine.pipeline.EachStepsDoneFollowsItsMainCounterAloneTest.aLaterLineOfAnotherCounterOrAStatementLeavesDoneAtTheMainCountersLatestCount` | §2: content redundancy's near-duplicate candidates say 3, its occurrence profiles 7, a statement's line, then the candidates 5, a reading after each: `done` 3, 3, 3 and 5 at seconds 0, 10, 20 and 30 |
| `engine.pipeline.EachStepsDoneFollowsItsMainCounterAloneTest.aSampleBeforeTheMainCounterHasSaidAnythingHoldsNoDone` | §2: byte-level reduction's sizes read say 4, a reading: `done` holds no point; its content hash then says 2, a reading 10 s on: `done` holds 2 at second 10 alone |
| `engine.pipeline.EachStepsDoneFollowsItsMainCounterAloneTest.theExtractionsFaultsResolvedLeaveItsDoneAtTheDocumentsRead` | §2: the extraction reads its four documents, then, its execution complete, its listener resolves two faults, and the job's last reading freezes its chart: `done` 4 at seconds 0, 10 and 20 |
| `engine.pipeline.EachStepsChartIsKeptBesideTheReportTest.eachStepsChartPlotsItsOwnDoneAndTheKindsOfVerdictItRecords` | ADR-432 §3 with §1 here: the eight steps it works each say their main counter's label |
| `engine.pipeline.EachStepsChartIsKeptBesideTheReportTest.twoStepsOfOneRunEachDrawAChartOfTheirOwn` | ADR-432 §4 and §6 with §1 here: embedding scoring and relevance scoring under one embedding-scoring run, each saying its main counter, in place of seed extraction and the seed-corpus comparison, which says no counter |

## What the implementation owes

- `JobSampler`: the table of §1, each step's main counter's label; its appender keeping, for the step whose thread said a counter's line whose whole label is that step's main one, that line as the step's latest of its main counter, beside `lastLine`, both cleared as the job begins; `takeASample` reading a step's `done` from that line alone, none where there is none; `lastLine`, the report's step, its progress line, its counted and total and `countsData` unchanged. Its javadoc cites this record beside ADR-432.
- The tests above passing.
