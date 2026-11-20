# ADR-418 — Every stage's chart is written empty as the job of `vespera run` begins, and frozen only within its invocation

> **Partly amended — see [ADR-432](0432-each-step-has-a-chart-beneath-its-legs-bars-the-output-spans-the-column-above-the-legs-and-the-cockpit-holds-no-tabs.md).** Of §1: the fifteen step files, `chart-step-<step>.json`, are written empty as the job begins, and no stage file; of §2: a step's key is its run, written at its chart's first sample; of §3: a step finished in an earlier invocation freezes holding no sample, `finishedEarlier` true and `finishedAt` null; of §4, §5 and §7: of the census's step file, the window reading the step files and `JobSampler` writing them. The rest of this record stands.

- **Date**: 2026-11-18
- **Status**: accepted on 2026-11-18.
- **Decided**: by the operator on 2026-11-18, on [#880](https://github.com/algernon28/vespera/issues/880): every stage's chart is cleared when an invocation of `vespera run` starts, the stages it only goes through finished among them, which then draw one sample or none; within one invocation a finished stage still freezes and keeps its chart. What *cleared* is and when an invocation starts for it (§1), what a chart is within its invocation (§2), a stage continued from an earlier invocation and one going through finished (§3), stage 0's samples before the census hands the job its walk (§4), what the window draws with no invocation running (§5), and which of ADR-412's rules stand (§6), are decided here.
- **Amends**: [ADR-382](0382-each-stage-has-a-chart-of-its-own-in-two-rows-frozen-once-it-finishes-and-kept-under-its-run-beside-the-report.md) §4, *"one file for each stage whose chart it has drawn"*: one for each of the eight stages, whether or not the invocation draws its chart (§1); *"at each reading at which its chart takes a sample or is started afresh (§5, §7), and only then"*: and, empty, at the job's first reading (§1); and *"The window reads the eight files"*, the job reading none (§1). §5, *"in a chart continued (§7), the last second the file holds, plus the invocation's `vespera.progress-interval`, plus the seconds since its first sample in this invocation"* and *"Where the runs reading at the start failed, a kind's count before this invocation is the last value its line holds in the file under the same key, else 0"*: every chart's seconds count from its first sample in this invocation, and a failed runs reading counts 0 (§2). §6, *"its chart stays as it is, to be continued (§7)"*: it stays as it is until the next job writes it empty, and the invocation that continues its run draws it afresh (§1, §3); and *"A frozen chart is never written again under its key … whatever its stage's steps do in a later invocation under the same runs"*: not again in the invocation that froze it, the next job writing it empty (§1, §2). §7, its first bullet, a run continued continuing its chart and a frozen one staying as it is, struck; its second, a new key starting the chart afresh, struck, a chart being started once in its invocation (§2); its third, a run put back freezing with one sample, now the case of every stage going through finished (§3). Its Consequences' first and second bullets and its third, fourth and fifth Known limits with them. It carries a note at its head.
- **Amends**: [ADR-412](0412-stage-0s-chart-is-kept-under-the-walk-the-census-keeps-and-a-new-key-empties-no-other-stages-chart.md) §1, the walk walked's samples *"held in the invocation's memory"* where the file does not name that walk, and the three cases at the hand-over: written as they are taken, and kept under the walk handed (§4); its *"A census that ends before the hand-over … drops the samples it held"*: writes stage 0's file empty (§4). §3 and §4, a stage's chart compared with what its own file names: struck (§6). Its Consequences and Known limits with them. It carries a note at its head.
- **Keeps**: ADR-382 §1 to §3 and §8 to §10, the window's eight charts, their lines, axes and look; §4's file, its name, its shape, its `format` 1, its write through a temporary file and that the job of `vespera run` alone writes it; §5's samples, when a chart takes one, its `done`, its kinds counted as standing under the stage's runs and its 256 points; §6's freezing and how the window draws a frozen chart. ADR-412 §1's walk walked and hand-over as it defines them, and §2, stage 0's `done` the entries the census's lines said of the walk walked. [ADR-265](0265-vespera-run-rewrites-a-progress-page-in-the-working-directory-while-it-runs.md) §4, a reading that fails failing nothing; [ADR-268](0268-the-progress-page-shows-what-the-stage-that-removes-occurrences-has-removed-and-draws-to-the-data-viz-method.md) §2, a reading making no query; [ADR-269](0269-the-progress-page-is-four-tabs-draws-a-hover-over-each-chart-and-lists-the-runs-the-working-directory-holds.md) §5, the runs read twice in an invocation; [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md), no stage's version naming `pipeline`; [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3, the control panel linking no class of the jar.
- **Settles**: [#880](https://github.com/algernon28/vespera/issues/880).

## Context

**What a chart holds from one invocation to the next.** Under ADR-382 §7, as ADR-412 amends it, a stage's file is kept under its key, the run or two runs its stage works under, the walk for the census, and a later invocation compares the key it reaches with what the file names: under the same key it continues a chart not frozen and leaves a frozen one as it is, and only a key the file does not name starts it afresh. A stage whose run no invocation changes keeps the chart of the invocation that froze it, however many invocations before. Beside a stage drawn afresh, such a chart reads as one of an earlier run, and nothing the window draws says which invocation drew which chart.

**What the sampler reads of the files.** `JobSampler` reads a stage's file the first time an invocation needs its chart, and decides from it: to continue the chart, its seconds after the file's last one; to leave it frozen; or to start it afresh (ADR-382 §5, §7). Before the census hands the job its walk, stage 0's samples are written only over a file naming the walk walked and not frozen, and held in memory otherwise, to be handed over or dropped (ADR-412 §1). Where the runs reading at the invocation's start failed, a kind's count before the invocation is the last value the file holds (ADR-382 §5).

**When an invocation of `vespera run` starts.** The command begins its report, `InvocationReport.begins`, then checks its working directory, its root and its models, and may refuse there with no job. The job begins under the working-directory lock, and its listener, `JobSampler.beforeJob`, makes the job's first reading before any step runs. The sampler is the files' one writer (ADR-382 §4); it is built in every invocation's application, `vespera label`'s and `vespera publish`'s too, and writes only from `beforeJob` on.

**Stage 5's key within one invocation.** Stage 5 works under two runs: the seed-measurement run, which the invocation reaches at seed extraction, and the embedding-scoring run, which it reaches at embedding scoring. The sampler compares the key reached at each reading with the chart's key; a key that gains its second run names a run the chart's key does not, and the chart is started afresh partway through the stage, the samples of seed extraction and of the seed-corpus comparison gone from it.

**The window.** `Cockpit` reads the eight files as it opens, whenever another working directory is named, and at each reading of the report of the invocation it watches, and draws what each holds, whatever invocation runs (ADR-382 §4). A file with no line is drawn as a file not there: its title alone, no line, not frozen.

## Decision

### 1. Every stage's file is written empty as the job begins

**At the job's first reading, which `vespera run`'s job makes as it begins and before any step runs, each of the eight files, `chart-stage-0.json` to `chart-stage-6b.json`, is written with no key, no `finishedAt` and no line**, whether or not it was there and whatever it held, a frozen chart among them:

```json
{ "format": 1, "stage": "2", "key": {}, "finishedAt": null, "lines": [] }
```

Each is written whole, through a temporary file moved over it, as ADR-382 §4 writes every file. **Written empty, not deleted**: ADR-382 §4 has the job write the files and nothing delete them, and the window draws an empty file as it draws none.

- **An invocation of `vespera run` that ends before its job begins**, refused at its start, writes none: the charts stay as the last job left them. `vespera label` and `vespera publish` write none (ADR-382 §4).
- **A file that cannot be written empty** is warned of as ADR-382 §4 warns of a file that cannot be written, once in the invocation, failing nothing; it is written over at its stage's first sample.
- **The sampler reads no file.** Every stage's chart starts the invocation with no point, whatever its file held; no key, `finishedAt`, line or last value of a file is read.

### 2. Within its invocation a chart is started once, and frozen once

Each chart takes its samples as ADR-382 §5 has them and freezes as §6 has it, with four changes:

- **A sample's `seconds` is the seconds since the chart's first sample in this invocation**: every chart starts at 0 in the invocation that draws it, and ADR-382 §5's continued chart is struck.
- **A kind's count before this invocation** is what the runs reading at the invocation's start counted under the stage's runs (ADR-269 §5), as ADR-382 §5 has it; **where that reading failed, 0**.
- **A chart's key is what the invocation has reached of its stage, written as the invocation reaches it**: stage 5's names its seed-measurement run once reached, and its embedding-scoring run beside it once reached; stage 0's as §4 says. **A chart is never started afresh within its invocation**: a key that gains a run, or stage 0's walk handed in place of the walk walked, changes the key and nothing else.
- **A frozen chart is not written again in the invocation that froze it**, whatever its stage's steps do after; the next job's first reading writes it empty (§1).

### 3. A stage continued from an earlier invocation, and a stage going through finished

- **A stage stopped at a gate, failed or stopped** keeps the chart its invocation drew, not frozen, until the next job begins. The invocation that continues its run draws its chart afresh from second 0: `done` as its stage's counters say it in that invocation, and its kinds counted as standing under its run (§2), so that a kind's line starts at what the earlier invocations recorded. ADR-382 §5's time continued across invocations is struck: the time the stage worked in earlier invocations is not drawn.
- **A stage whose steps go through finished**, never seen running, freezes at the first reading at which they stand `finished`, holding that reading's sample: its kinds counted as standing, no `done` unless one of its steps said a counter in this invocation, and for `finishedAt` the latest end of its steps in this invocation. Byte-level reduction already recorded under its run is such a stage; ADR-382 §7's run put back is one too.
- **A stage the invocation does not reach** keeps the empty file §1 wrote.

### 4. Stage 0 is written as the census walks, and kept under the walk it hands the job

ADR-412 §1's walk walked, the first walk the census's own lines name before the hand-over, and its hand-over, the census recording the corpus root's walk for the job, stand as that record defines them. Its samples held in memory go:

- **Before the hand-over**, each sample of the walk walked is written as it is taken, as ADR-382 §5 takes it, under the walk walked, `{"walk": "<walk walked>"}`, from the chart's first point. None is held.
- **At the first reading at which the census has handed the job its walk**, the chart's key becomes the walk handed and its points stay as they are; from then on it takes its samples and freezes under the walk handed, as ADR-382 §5 and §6 have it. A walk the census discards is drawn under the walk it keeps.
- **A census whose step ends with no walk handed**, its corpus root's walk failed, writes stage 0's file empty at the first reading at which its step stands ended, and takes no sample of it: the walk walked may be the seed folder's (ADR-412, Context), and is not left drawn as stage 0's.
- **Stage 0's `done`** is the entries the census's lines last said of the walk walked, as ADR-412 §2 has it.

### 5. The window draws the last job's charts

**The window is unchanged.** It reads the eight files when ADR-382 §4 says, and draws what each holds: a file written empty as a file not there, its title alone, no line, not frozen. **Opened, or opened again, with no invocation running, it draws the charts of the last job of `vespera run`**, each that invocation's alone, the finished ones frozen and dimmed with the time they finished; a `vespera label` or `vespera publish` after it leaves them. While a `vespera run` starts, the window draws the charts its files hold until it reads them after the job's first reading.

### 6. ADR-412's rules that go, and the one that stays

ADR-412 §1's samples held in memory, handed over or dropped, and its three cases at the hand-over, are struck, §4 in their place; §1's thinning of the held samples one at a time is ADR-382 §5's thinning as each sample is taken. ADR-412 §3, a new key writing no other stage's file, and §4, a stage going through finished under the key its file names keeping its file, are struck: no chart is compared with its file, and none is started afresh within its invocation (§1, §2), so no stage's file is written but by its own chart and the job's first reading. ADR-412 §2 stands.

### 7. The modules, and run ids

**In `pipeline`**: `JobSampler` alone, the eight files written empty at its first reading, no file read, each chart started once in its invocation, its key written as it is reached, stage 0 written as the census walks and written empty where the census ends with no walk handed. `CensusTasklet`, `InvocationRuns`, `Verdicts`, `corpus` and `ui` are unchanged. No stage's version names `pipeline` (ADR-222), so **no run id moves**. The file's `format` stays 1; `report.json`, its `format`, and `report.html` are unchanged.

## Alternatives refused

- **Each file deleted.** ADR-382 §4 has the job write the files and nothing delete them; a deletion is a second way of changing a file beside the move every write takes, and the window draws an empty file as it draws none.
- **Only the files that are there written empty.** What the working directory holds after the job's first reading would depend on what it held before; eight small writes leave the same eight files whatever it held.
- **The files emptied as the command begins, before its job.** The command may refuse there, for its root, its working directory or its models: the last job's charts would be emptied though no stage ran. And the sampler, the files' one writer, writes only from `beforeJob` on.
- **The window emptying its charts as it starts a `vespera run`.** The window decides nothing of a chart (ADR-382 §4); a `vespera run` started from a terminal would empty nothing, and the window opened again would draw the files the jar left.
- **The window drawing no chart while no invocation runs.** The files are the one record of how the last job's stages went over time, the database holding no sample (ADR-382, Context); the window opened again would show nothing of the last invocation.
- **A stage continued from an earlier invocation continuing its chart, every other chart emptied.** It would keep a chart drawn by an earlier invocation, for the stages stopped partway alone, and read the file under its key as ADR-382 §7 did; a chart of an earlier invocation is what the operator's decision removes.
- **ADR-412's samples held until the hand-over, the file being empty at every start.** Stage 0 would draw nothing while any census walks, a walk continued across invocations, the longest a census makes, among them: ADR-412's first refused alternative.
- **Stage 0's samples written as they are taken and kept where the census ends with no walk handed.** Where the corpus root's walk said no line, the seed folder's walk would stay drawn as stage 0's after the invocation, until the next job.
- **A kind's count before the invocation read from the file before it is written empty, where the runs reading fails.** A read of eight files kept for one failure, against a rule, §1, by which the sampler reads none.

## Consequences

- **Every chart the window draws once a `vespera run`'s job has written the eight files empty is that invocation's alone**, but for a file that could not be written (Known limits): a stage it has not reached is empty, a stage it works is drawn from 0, and a stage it finds finished is frozen with one sample.
- **A stage that goes through finished**, byte-level reduction recorded under its run among them, draws one sample of its kinds and no `done`, with the time this invocation found it finished.
- **A stage stopped partway and continued later draws, in each invocation, the part of its work done in that invocation**; its kinds start at what was recorded before.
- **Stage 0 is drawn as the census walks, every census**, a first one of a working directory among them, and a walk the census discards is drawn under the walk it keeps.
- **Stage 5's chart keeps the samples of seed extraction and the seed-corpus comparison** once the invocation reaches the embedding-scoring run.
- **The working directory holds the eight files from the first job of `vespera run` on**, of 256 samples a line at most.
- **Stage 5's chart is drawn afresh by every invocation that reaches it**, its relevance floor and its report deciding again on every invocation (ADR-118), so ADR-382's third Known limit, a frozen stage 5 not drawing what they decide later, no longer holds.
- ADR-382's fifth Known limit, a stage's chart drawn in its file's place while a later invocation has not reached it, and ADR-412's first and third, stage 0 drawing nothing until the hand-over and a later stage's chart of an earlier run drawn, no longer hold.

## Known limits

- **A file the job's first reading cannot write empty, or does not reach because the process ends partway through the eight, keeps the last job's chart**: until its stage's first sample in this invocation writes over it, or, where the invocation does not reach its stage, through the whole invocation and after it, the window drawing it as it was.

- **A stage going through finished draws one sample, no `done`, and the end of its steps in this invocation for its time**, not when it first finished; the steps' bars and the runs the report lists say how far it got.
- **A stage continued across invocations draws only the part of its work done in this one.**
- **Until the window reads the files after the job's first reading**, it draws the last job's charts: while the jar starts and checks, and until the window's next reading of the report after the job begins.
- **Where the corpus root's walk says no line and fails**, stage 0's chart draws the seed folder's walk while the census walks it, until the census's step ends; a process that ends there leaves it so until the next job.
- **Where the runs reading at the invocation's start fails**, a kind's line counts this invocation's verdicts alone.
- ADR-412's fourth, the census's `done` stopping at its last running line, stands.

## What this does not decide

- **A record of each invocation's stage charts kept after the next job begins.** `logs/report-<invocation>.json` keeps the three charts ADR-362 §3 gives it, and nothing keeps a stage's chart of an earlier invocation.
- **`report.html`'s charts**, and the window's look, ADR-382 §1 to §3, §6, §8 and §9, unchanged.

## Tests

The job's listener is driven as Spring Batch drives it, with executions built by hand, a sampler of its own for each invocation as a new process has; where a reading has to fall between two events, the test makes the reading the sampler's own thread makes at each interval, on a clock it moves on.

| Method | What it holds |
| --- | --- |
| `pipeline.EachStagesChartIsKeptUnderItsRunTest.everyStagesChartIsWrittenEmptyAsTheJobBegins` | §1, §3: a working directory keeping the census's chart frozen under its walk, byte-level reduction's and the content census's frozen under their runs and the extraction's not finished; at the job's first reading, each of the eight files holds format 1, its stage, no key, no `finishedAt` and no line; the extraction then running under another run and being stopped, the files of the seven stages it did not reach still hold no key and no line |
| `pipeline.EachStagesChartIsKeptUnderItsRunTest.everyInvocationDrawsTheStagesChartAfreshAndOneFindingItFinishedFreezesItWithOneSample` | §2, §3: an invocation whose extraction says 2 of 4, records one `extraction-failed` and is stopped keeps its chart under its run, not finished, at second 0; a second, under the same run, the runs reading counting that verdict, whose extraction says 4 of 4, records one more of each kind and finishes, draws it afresh, its one sample at second 0, `done` 4, `extraction-failed` 2 and `degenerate-output` 1, frozen at the extraction's end; a third, under the same run, the extraction going through finished, the runs reading counting 2 and 1, freezes it at that extraction's end holding one sample, 2 and 1, and no `done` |
| `pipeline.EachStagesChartIsKeptUnderItsRunTest.withinTheInvocationThatFreezesItAStagesChartIsNotWrittenAgain` | §2: an extraction that finishes, a reading freezing its chart, then the content census running, a reading 10 s on and the job's last reading 10 s after: the extraction's file is as the freezing reading wrote it |
| `pipeline.EachStagesChartIsKeptUnderItsRunTest.stage5sChartIsNotStartedAfreshWhenTheInvocationReachesItsSecondRun` | §2: seed extraction under a seed-measurement run says 2 of 4, a reading; embedding scoring under an embedding-scoring run says 3 of 10, a reading 10 s on; the job stopped, its last reading 10 s after: stage 5's chart is keyed by both runs, not finished, `done` 2, 3 and 3 at seconds 0, 10 and 20 |
| `pipeline.EachStagesChartIsKeptUnderItsRunTest.theChartOfTheCensusIsWrittenAsItWalksAndKeptUnderTheWalkItHandsTheJob` | §4, §2: no chart of stage 0; an invocation whose census's lines say 28,000 entries under walk 82 writes, at the reading after them, `done` 28,000 at second 0 under walk 82, then hands the job walk 82 unfinished and is stopped, its last reading 10 s on: 28,000 at seconds 0 and 10, not finished; a second, whose lines say 45,000 under walk 82, writes at the reading after them its chart afresh under walk 82, 45,000 at second 0, then hands the job walk 46, says 2,000 under the seed folder's walk 83 and finishes: the chart under walk 46, frozen at the census's end, 45,000 at seconds 0 and 10 |
| `pipeline.EachStagesChartIsKeptUnderItsRunTest.moreThan256SamplesOfTheCensusAreThinnedAsTheyAreTaken` | §4: no chart of stage 0; a census saying a line under walk 82 before each of 300 readings, one a second, the entries 1,000 times the reading's place, writes before it hands the job any walk 172 points under walk 82: seconds 0 to 256 every other one, then 257 to 299, each with its reading's entries |
| `pipeline.EachStagesChartIsKeptUnderItsRunTest.aCensusThatHandsTheJobNoWalkLeavesItsChartEmpty` | §4: a census whose lines say 2,000 entries under the seed folder's walk 83 alone, a reading after them, which then fails with no walk handed: after the job, stage 0's file holds no key, no `finishedAt` and no line |
| `pipeline.EachStagesChartIsKeptUnderItsRunTest.aRunsReadingThatFailsCountsAKindFromThisInvocationAlone` | §1, §2, and the Known limit of a runs reading that fails: a working directory keeping the extraction's chart under its run, not finished, its `extraction-failed` line ending at 3; an invocation whose runs reading at the start fails, whose extraction runs under that run, records one `extraction-failed` and is stopped: the line ends at 1, nothing of the file counted |
| `ui.EachStageHasAChartTest.aStagesChartWrittenEmptyIsDrawnAsOneWithNoFile` | §5: the window opened with no invocation over a working directory whose stage 1 file is the one §1 writes: its chart titled *1 · Byte-level reduction* alone, not frozen, no line and no point marked latest |

The two methods of `pipeline.EachStagesChartIsKeptUnderItsRunTest` ADR-382's Tests name, and the six ADR-412's name, are renamed, or retired with the rules they held, as those records' Tests sections now say. `ui.EachStageHasAChartTest.aFinishedStagesChartIsDrawnFrozenFromItsFileAsTheWindowOpens` (ADR-382) holds the window drawing the files with no invocation running, unchanged.

## What the implementation owes

- `JobSampler`: at the job's first reading, before its charts are kept, each of the eight files written empty, as §1 shapes it, through `ReplacingMove`, a failure warned of once; `readTheChart` and every comparison with what a file names removed, each stage's chart starting the invocation with no point (§1); a chart started once, its key added to as the invocation reaches it, its seconds from its first sample in the invocation, a failed runs reading counting 0 before it, a frozen chart not written again (§2); stage 0's samples of the walk walked written as they are taken, the key moved to the walk handed at the first reading after the hand-over, the held samples removed, and stage 0's file written empty, once, at the first reading at which the census's step has ended with no walk handed (§4). Its javadoc, and `keepTheCharts`', cite this record beside ADR-382 and ADR-412.
- `JobSampler.scheduledWrite`, the reading its own thread makes at each interval, keeps its name and its lack of parameters: the tests above make a reading through it.
- The tests above passing.
