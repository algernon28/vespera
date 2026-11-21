# ADR-438 — A step's chart is kept under its run as it freezes, and an invocation finding the step finished earlier draws it

- **Date**: 2026-11-21
- **Status**: accepted on 2026-11-21.
- **Decided**: under the operator's delegation, on [#922](https://github.com/algernon28/vespera/issues/922): a step's chart, as it freezes in the invocation that ran the step to its end, written also to `charts/<run id>/chart-step-<step>.json` in the working directory, for the twelve steps that record their completion under a run, never written empty and never deleted (§1); an invocation finding the step finished under the run it reached for it drawing that chart, frozen, with the instant the step finished, and saying *finished in an earlier invocation* only where no chart is kept under that run (§2); the chart kept being that of the invocation in which the step finished, the parts earlier invocations drew not kept and not joined to it (§3); a frozen chart's title naming the day as well as the time where the step finished on another day than the one the window draws it on (§4); the fifteen files beside the report still written empty as the job begins, and the census, the relevance floor and the relevance report unchanged (§5).
- **Amends**: [ADR-432](0432-each-step-has-a-chart-beneath-its-legs-bars-the-output-spans-the-column-above-the-legs-and-the-cockpit-holds-no-tabs.md) §4, `finishedAt`, *"`null` until then, and for a step finished in an earlier invocation"*: for such a step, the instant the chart kept under its run holds, `null` where none is kept; and `lines`, *"none for a step finished in an earlier invocation"*: the lines of the chart kept under its run, none where none is kept (§2). §5, its table's row *Finished in an earlier invocation*, *"no point, no line, no legend; and, centred in its plot, finished in an earlier invocation"*: that row is the file of such a step holding no line; a file of such a step holding lines is drawn as a frozen chart, with no words (§4); and its bullet *"the chart freezes holding no sample, `finishedAt` `null`, `finishedEarlier` `true` and no line"*: it freezes holding the chart kept under its run, where one is (§2). §6, *"finished at `HH:mm`"*: and the day before the time where it is another day's (§4). Its refused alternative *"The earlier invocation's samples drawn for a step finished in an earlier invocation"*: they are drawn, from the chart kept under the run (§1, §2). It carries a note at its head.
- **Amends**: [ADR-418](0418-every-stages-chart-is-written-empty-as-the-job-of-vespera-run-begins-and-frozen-only-within-its-invocation.md) §1, *"The sampler reads no file"*: it reads one, the chart kept under the run of a step it finds finished in an earlier invocation, at the reading that freezes that step's chart (§2); §7, *"`JobSampler` alone … no file read"*: as §6 here says. Its Consequences' first bullet, *"Every chart the window draws … is that invocation's alone"*: but for a step finished in an earlier invocation under a run whose chart of it is kept (§2). Its *What this does not decide*, *"nothing keeps a stage's chart of an earlier invocation"*: a step's chart is kept under its run (§1). It carries a note at its head.
- **Keeps**: ADR-418 §1, the fifteen files beside the report written empty at the job's first reading whatever they held; §2, a chart started once and frozen once within its invocation, its seconds from its first sample in that invocation; §3, a step stopped at a gate, failed or stopped drawing in each invocation the part of its work done in that one; §4, the census's chart. ADR-432 §3's lines, §4's file and its shape, the step's key and its `format` 1, §5's other three states and its words, §6's freezing and look, and §5's last bullet, a step gone through finished where the runs reading failed drawn as one that ran. [ADR-382](0382-each-stage-has-a-chart-of-its-own-in-two-rows-frozen-once-it-finishes-and-kept-under-its-run-beside-the-report.md) §4, a chart's file written whole through a temporary file moved over it, a file that cannot be written warned of once in the invocation and failing nothing, and the job of `vespera run` alone writing one. [ADR-118](0118-the-answers-a-person-gave-never-join-a-runs-identity-so-the-two-steps-that-read-them-record-no-completion.md), the relevance floor and the relevance report recording no completion. [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md), no stage's version naming `pipeline`.
- **Settles**: [#922](https://github.com/algernon28/vespera/issues/922).

## Context

**What a step finished in an earlier invocation draws.** Under ADR-432 §5 the job tells such a step from the runs reading made as the invocation starts, which lists, of every run, the steps whose completion is recorded under it (`Runs.finishedSteps`, the `finished_step` table, ADR-269 §5): at the first reading at which the step stands `finished`, where that reading lists it finished under the run this invocation reached for it, the step's chart freezes holding no sample, `finishedAt` `null`, `finishedEarlier` `true` and no line, and the window says *finished in an earlier invocation* in its plot. ADR-432 refused drawing the earlier invocation's samples: ADR-418 keeps no chart past the next job, and the database holds no sample (ADR-382, Context).

**Where a chart lies.** Each step's chart is `chart-step-<step>.json` at the top of the working directory, beside `report.json`, and each of the fifteen is written empty at the job's first reading, whatever it held (ADR-418 §1, ADR-432 §4). The report's copy, `logs/report-<invocation>.json`, keeps the three charts of ADR-362 §3 and no step's. The chart a step drew while it ran is so kept until the next job of `vespera run` begins, and no later.

**Which steps record their completion, and under which run.** `InvocationReport.STEPS_OF_A_STAGE` lists twelve: byte-level reduction, the extraction, the content census, the arrangement and the generation, each under its own stage's run; the redundancy signatures and content redundancy under the content-redundancy run; seed extraction and the seed-corpus comparison under the seed-measurement run; embedding scoring, relevance scoring and the clustering under the embedding-scoring run. The census works under a walk and records none; the relevance floor and the relevance report record none and decide again on every invocation (ADR-118). The clustering, recorded finished, does its work again under the same run where the floor's step has changed its survivors since (ADR-230). A step's chart is keyed by the run its run stage names, a run id of 8 to 128 hexadecimal characters (`JobSampler.keyOf`).

**A step worked over several invocations.** A step stopped and continued under the same run draws, in each invocation, the part of its work done in that one, from second 0 (ADR-418 §3): the chart frozen at its end is that of the invocation in which it finished.

## Decision

### 1. A step's chart is kept under its run as it freezes

**At the reading at which a step's chart freezes in the invocation that ran it**, its `finishedAt` given and `finishedEarlier` `false` (ADR-432 §6), **the job writes the same JSON, after the file beside the report, to `charts/<run id>/chart-step-<step>.json` in the working directory**: `<run id>` the run the chart's key names under its step's run stage (ADR-432 §4), the two folders made where they are not there. It is written whole, through a temporary file moved over it, as ADR-382 §4 writes every chart's file; one that cannot be written is warned of as a chart beside the report is, once in the invocation for both, and fails nothing.

- **Only the twelve steps `InvocationReport.STEPS_OF_A_STAGE` lists** keep a chart under their run. The census, keyed by a walk, and the relevance floor and the relevance report, which record no completion and are never found finished in an earlier invocation (ADR-432 §5), keep none.
- **A chart that has not frozen keeps none**: a step stopped at a gate, failed or stopped has not finished (ADR-432 §6).
- **A chart frozen as finished in an earlier invocation (§2) writes none**: its chart is the one kept.
- **A step finished again under the same run**, the clustering's of ADR-230, writes over the chart kept under that run as its chart freezes again.
- **Nothing writes a chart kept under a run empty, and nothing deletes one.** The job's first reading writes the fifteen files beside the report empty (ADR-418 §1) and no file under `charts/`; `vespera label` and `vespera publish` write none.

### 2. A step found finished in an earlier invocation draws the chart kept under its run

**At the first reading at which a step stands `finished`, where the runs reading made as the invocation started lists it finished under the run this invocation reached for it** (ADR-432 §5), the job reads `charts/<that run>/chart-step-<step>.json`. **Where that file is of `format` 1, its `step` the step, its `key` the step's run stage alone naming that run, its `finishedAt` an instant, its `finishedEarlier` `false`, and its `lines` each a name and two lists of numbers of one length**, the chart beside the report freezes holding that key, that `finishedAt`, `finishedEarlier` `true`, and the kept chart's lines as it holds them, in its order:

```json
{
  "format": 1,
  "step": "extraction",
  "key": { "extraction": "<run id>" },
  "finishedAt": "2026-11-20T10:35:05Z",
  "finishedEarlier": true,
  "lines": [
    { "name": "done", "seconds": [0, 5, 10], "values": [120, 260, 400] },
    { "name": "extraction-failed", "seconds": [0, 5, 10], "values": [0, 3, 4] },
    { "name": "degenerate-output", "seconds": [0, 5, 10], "values": [0, 1, 1] }
  ]
}
```

- **Where no such file is kept**, not there, not readable, not that JSON, of another `format`, naming another step or another key, or holding no `finishedAt`, the chart freezes as ADR-432 §5 freezes it: no line, `finishedAt` `null`, `finishedEarlier` `true`. Nothing is warned of: a run finished before this record keeps no chart.
- **Either way the chart is not written again in the invocation** (ADR-418 §2), and the chart kept under the run is not written.
- **The run is matched by its id alone**: the run the step's key names, the one the runs reading lists the step finished under. A chart kept under another run of the same stage is never drawn for it.
- **Where the runs reading at the invocation's start failed**, no step is found finished in an earlier invocation and no kept chart is read: the step is drawn as one that ran, as ADR-432 §5's last bullet has it.

### 3. The chart kept is that of the invocation in which the step finished

**Of a step worked over several invocations under one run, the chart kept is the one the invocation in which it finished drew**: the part of the step's work done in that invocation, from second 0 (ADR-418 §3). The parts earlier invocations drew, stopped, are not kept, and no chart is joined to another.

### 4. The window draws a kept chart frozen, and names the day where it is another

The window still tells a chart's state from its file alone (ADR-432 §5), through `StepChartFile`, which reads `finishedEarlier` and `lines` as it does:

| State | The file | The chart |
| --- | --- | --- |
| **Finished in an earlier invocation, its chart kept** | `finishedEarlier` `true` and a line given | its lines, frozen as ADR-432 §6 draws a chart where `finishedAt` is given; no words |
| **Finished in an earlier invocation, no chart kept** | `finishedEarlier` `true` and no line | as ADR-432 §5: its title alone, its axes, no point, no line, no legend, and *finished in an earlier invocation* centred in its plot |

The other three states are ADR-432 §5's.

**A frozen chart's title** is its step's name and *, finished at `HH:mm`*, in the machine's zone (ADR-432 §6), **where `finishedAt` falls, in that zone, on the day the window draws the chart; otherwise *, finished at `d MMM HH:mm`***, the month in three English letters, as in *extraction, finished at 17 Nov 13:32*. The rule is every frozen chart's, whichever invocation froze it.

### 5. What stands of ADR-418 and ADR-432

ADR-418 §1's fifteen files written empty at the job's first reading stand, and every chart that the invocation draws of a step it runs is its own (ADR-418 §2, §3); the one file the sampler reads is §2's, and only at the reading that freezes the chart of a step found finished in an earlier invocation. ADR-432 §5's words stand for a step finished in an earlier invocation under a run that keeps no chart of it. The census, the relevance floor and the relevance report are unchanged: none records a completion, so none is ever found finished in an earlier invocation, and each draws its chart afresh in every invocation that reaches it.

### 6. The modules, and run ids

**In `pipeline`**: `JobSampler` alone, writing the chart kept under its run as a chart freezes and reading it for a step found finished in an earlier invocation; it stays listed in `PipelineHoldsOnlyTheRulesOnRecordTest` as holding no rule. **In `ui`**: `ProgressCharts`, its words and its frozen title; `StepChartFile` unchanged. No stage's version names `pipeline` (ADR-222) or the control panel, so **no run id moves**. The file's `format` stays 1; `report.json`, its `format`, its files and `report.html` are unchanged.

## Alternatives refused

- **The file beside the report read before the job's first reading writes it empty, and written back where its step is found finished under the run it names.** A job that reaches the step under another run of its stage, a retuned value put back (ADR-156) among them, writes over it, and a process that ends between the empty write and the step's reading loses it; and the sampler would read all fifteen files on every invocation, against ADR-418 §1.
- **A table of the ledger holding the samples.** A table owned by `ledger` (ADR-209) for what the window draws from files, a schema change and a question for the tree's ledger (ADR-429 §4), with nothing gained over a file the window's reader already reads.
- **Each invocation's part of a step's work kept, and the parts joined end to end.** It brings back ADR-382 §5's time continued across invocations, which ADR-418 §3 struck, and a chart kept for every stopped invocation; the chart the step drew as it finished is what the operator saw of it.
- **A chart kept for the census, the relevance floor and the relevance report.** None is ever found finished in an earlier invocation, so none would be read.
- **The chart drawn with `finishedEarlier` `false`.** It would read as a step this invocation ran; only the jar knows which steps its runs had recorded finished (ADR-432, Alternatives refused).
- **The day named only on a step finished in an earlier invocation.** A chart frozen before midnight, or drawn by a window opened the next day over the last job's files, would read as today's.
- **The charts kept under a run deleted with the run, or after some time.** Nothing deletes a run's rows (ADR-116, ADR-229), and a chart kept under a run is what a later invocation of that run draws.

## Consequences

- **A step finished in an earlier invocation draws, while the run is the same, the chart it drew as it finished**, frozen, with the day and time it finished; one whose run kept no chart of it says *finished in an earlier invocation*.
- **The working directory gains `charts/`**, a folder for each run under which a step finished, each holding one file for each of its steps that finished, three at most, of 256 samples a line at most; nothing removes them.
- **A run put back draws its steps' charts again**, a retuned value restored (ADR-156) finding its run's steps finished and their charts kept.

## Known limits

- **A step whose run finished it before this record** says *finished in an earlier invocation*: no chart was kept under its run.
- **A step worked over several invocations draws only the part of its work done in the invocation in which it finished** (§3).
- **A chart that could not be kept under its run**, its write warned of, is said *finished in an earlier invocation* by every later invocation that finds its step finished.
- **A working directory moved or copied without its `charts/` folder** draws the words for every step it finds finished.

## What this does not decide

- **Removing the charts kept under runs**, which nothing does.
- **`report.html`'s charts**, ADR-362 §6's, unchanged.

## Tests

| Method | What it holds |
| --- | --- |
| `engine.pipeline.EachStepsChartIsKeptBesideTheReportTest.everyInvocationDrawsTheStepsChartAfreshAndOneFindingItFinishedEarlierDrawsTheChartKeptUnderItsRun` (in place of ADR-432's `everyInvocationDrawsTheStepsChartAfreshAndOneFindingItFinishedEarlierSaysSoWithNoSample`) | §1, §2, §3: the extraction stopped, then finished, then gone through under a run the runs reading lists it finished under: the stopped invocation keeps no chart under the run; the one in which it finished keeps under the run the file it froze beside the report, byte for byte; the third freezes the file beside the report with the kept chart's lines and `finishedAt`, `finishedEarlier` true |
| `engine.pipeline.EachStepsChartIsKeptBesideTheReportTest.aStepFinishedEarlierUnderARunThatKeepsNoChartOfItSaysSoWithNoSample` | §2: the extraction gone through under a run the runs reading lists it finished under, with no chart kept under it, then with one kept under another run, then one under it naming another run, then one of another `format`, then one of that step and run holding lines but itself `finishedEarlier` true: each time no line, `finishedAt` null, `finishedEarlier` true |
| `engine.pipeline.EachStepsChartIsKeptBesideTheReportTest.theJobsFirstReadingWritesNoChartKeptUnderARun` | §1: a chart kept under the extraction's run; a job whose extraction runs under another run and is stopped: the kept chart as it was, byte for byte |
| `engine.pipeline.EachStepsChartIsKeptBesideTheReportTest.onlyAStepThatRecordsItsCompletionAndFinishedKeepsAChartUnderItsRun` | §1: the census finishing, the extraction stopped, the relevance floor and the clustering finishing under the embedding-scoring run: `charts/` holds the clustering's chart alone, the same as its file beside the report |
| `engine.pipeline.EachStepsChartIsKeptBesideTheReportTest.aStepFinishedAgainUnderItsRunWritesOverTheChartKeptUnderIt` | §1: a chart of the clustering kept under the embedding-scoring run; the clustering runs again under it and finishes: the kept chart is the one this invocation froze |
| `ui.EachStepHasAChartBeneathItsLegTest.aStepFinishedInAnEarlierInvocationWithItsChartKeptIsDrawnFrozenWithTheDayItFinished` | §4: the extraction's file `finishedEarlier` true with three lines, finished three days before: its lines drawn, frozen, no words, its title *extraction, finished at `d MMM HH:mm`* |
| `ui.EachStepHasAChartBeneathItsLegTest.aFinishedStepsChartIsDrawnFrozenFromItsFileAsTheWindowOpens` | §4 with ADR-432 §6: byte-level reduction's chart finished on the day the window draws it, titled with the time alone |
| `ui.EachStepHasAChartBeneathItsLegTest.aChartSaysNothingBeforeItsStepStartsAndSaysWhyItDrawsNoCount` | §4: a step finished in an earlier invocation with no line still says *finished in an earlier invocation*, unchanged |

## What the implementation owes

- `JobSampler`: as a chart of one of `STEPS_OF_A_STAGE`'s steps freezes having run (`finishedAt` given, `finishedEarlier` false), the same JSON written after the file beside the report to `charts/<run id>/chart-step-<step>.json`, its folders made, through a temporary file and `ReplacingMove`, a failure warned of with the other charts' warning; for a step found finished in an earlier invocation, that file read and, where §2's conditions hold, the chart frozen with its key, its `finishedAt`, `finishedEarlier` true and its lines, else as now; no file under `charts/` written at the job's first reading. Its javadoc cites this record beside ADR-432 and ADR-418.
- `ProgressCharts`: no words on a chart whose file is `finishedEarlier` and holds a line; a frozen chart's title *, finished at `HH:mm`* on the day the window draws it and *, finished at `d MMM HH:mm`*, `Locale.ENGLISH`, on another, both in the machine's zone. Its javadoc cites this record.
- The README's table of what the working directory holds gains `charts/<run id>/chart-step-<step>.json`, and its paragraph on what the window shows, `CONTEXT.md`'s Control panel entry where it speaks of the charts, and `docs/architecture.md` say what §1 to §4 have, in the change that implements this.
- The tests above passing.
