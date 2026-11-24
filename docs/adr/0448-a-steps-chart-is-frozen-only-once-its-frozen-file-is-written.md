# ADR-448 — A step's chart is frozen only once its frozen file is written

- **Date**: 2026-11-24
- **Status**: accepted on 2026-11-24.
- **Release**: patch
- **Decided**: on [#934](https://github.com/algernon28/vespera/issues/934), its first part, as the issue sets it out: that a step's chart stays unfrozen until the write of its file holding it frozen succeeds, and that a freezing write that failed is made again at the next reading. This record's own: that it holds alike for a step that finishes in the invocation and for one found finished in an earlier invocation (§1), that the reading whose freezing write fails adds nothing to the chart (§1), that it is warned of as any chart's write is, once in the invocation (§2), that no chart is kept under the step's run until the chart is frozen (§3), and the tests.
- **Amends**: [ADR-432](0432-each-step-has-a-chart-beneath-its-legs-bars-the-output-spans-the-column-above-the-legs-and-the-cockpit-holds-no-tabs.md) §6, *"A step's chart freezes at the first reading at which the step stands `finished`, after it takes that reading's sample"*: at the first reading at which the step stands `finished` and its file holding it frozen is written; until then it is not frozen (§1). §5, its bullet *"at the first reading at which the step stands `finished`, where the runs reading made as the invocation started lists the step finished under the run this invocation reached for it, the chart freezes holding no sample, `finishedAt` `null`, `finishedEarlier` `true` and no line, and is not written again in the invocation"*: at the first such reading at which its file holding it frozen is written (§1).
- **Amends**: [ADR-438](0438-a-steps-chart-is-kept-under-its-run-as-it-freezes-and-an-invocation-finding-the-step-finished-earlier-draws-it.md) §2, *"At the first reading at which a step stands `finished`, where the runs reading made as the invocation started lists it finished under the run this invocation reached for it"*, the chart beside the report freezing holding the chart kept under its run: at the first such reading at which its file holding it frozen is written, the chart kept under its run read again at each such reading until then (§1); and §5, *"only at the reading that freezes the chart of a step found finished in an earlier invocation"*: at each reading at which the chart of such a step is written frozen, until one such write is made (§1).
- **Keeps**: ADR-432 §3's lines, §4's file, its shape and `format` 1, written whole through a temporary file moved over it, a file that cannot be written warned of once in the invocation and failing nothing, and §6's look of a frozen chart. [ADR-418](0418-every-stages-chart-is-written-empty-as-the-job-of-vespera-run-begins-and-frozen-only-within-its-invocation.md) §2, a chart started once and frozen once within its invocation, and not written again in it once frozen. ADR-438 §1, the chart kept under its run written after the file beside the report as the chart freezes, and §2's chart kept and what it must hold to be drawn. [ADR-265](0265-vespera-run-rewrites-a-progress-page-in-the-working-directory-while-it-runs.md) §4, a write that fails warned of once and failing nothing, the next made on time. [ADR-446](0446-a-steps-chart-draws-its-done-from-the-steps-main-counter-alone.md), each step's `done`. [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md), no stage's version naming `pipeline`.
- **Settles**: [#934](https://github.com/algernon28/vespera/issues/934), its first part, of a freezing write that fails; not what left three of its charts blank with no warning logged (What this does not decide).

## Context

**How a step's chart freezes.** At each reading `JobSampler.keepTheChart` takes each step's sample and writes its file, `chart-step-<step>.json`, whole through a temporary file moved over it (ADR-432 §4). At the first reading at which the step stands `finished` the chart freezes: its `finishedAt` the step's end (ADR-432 §6), or, for a step the runs reading made as the invocation started lists finished under the run this invocation reached for it, `finishedEarlier` `true` and the chart kept under that run, or no line where none is kept (ADR-432 §5, ADR-438 §2). A frozen chart is not written again in the invocation (ADR-418 §2). A file that cannot be written is warned of once in the invocation, `a step's chart could not be kept beside the report`, and fails nothing (ADR-265 §4, ADR-432 §4).

**A freezing write that fails.** ADR-432 freezes the chart at the reading, whatever that reading's write does. Where the write throws, every later reading of the invocation finds the chart frozen and writes it no more: the file keeps what the reading before wrote, for a step found finished in an earlier invocation an empty chart, or one holding the samples of the readings that found it running, as a reading can catch the step running before it stands `finished`, and the samples before its end for a step that finished, and the window draws a chart not finished, with no *finished at* and no words, beside a bar that says `finished`.

**What #934 saw.** In one invocation three steps found finished in an earlier invocation, the extraction, the content census and the redundancy signatures, each going through in under a second between two readings, drew a blank chart, while byte-level reduction's, found finished too, said *finished in an earlier invocation*; and no warning of a chart was logged, which a write that throws would have logged. A test of several such steps going through between two readings freezes each as ADR-432 §5 and ADR-438 §2 have it.

## Decision

### 1. A chart is frozen only once its file holding it frozen is written

**A step's chart freezes at the first reading at which the step stands `finished` and the write of its file holding it frozen succeeds.** At a reading at which the step stands `finished`, the chart is written frozen, as ADR-432 §5 and §6 and ADR-438 §2 have it; where that write fails, the chart is not frozen, and the next reading at which the step stands `finished` freezes it afresh, as though that reading were the first.

- **A step that finishes in this invocation**: the reading whose freezing write fails adds nothing to the chart; its sample and its `finishedAt` go with the write. The next reading takes its own sample and freezes the chart at the step's end: the frozen chart holds the samples of the readings before the one whose write failed, then the freezing reading's.
- **A step found finished in an earlier invocation**: the reading whose freezing write fails leaves the file as the reading before wrote it, the chart not frozen and `finishedEarlier` `false`; the next reading reads the chart kept under its run again (ADR-438 §2) and freezes it afresh, holding that chart or, where none is kept, no line.
- **Until it is frozen, the chart is written frozen at every reading at which its step stands `finished`**, the job's last reading among them, until one write is made. Where none is made in the invocation, the chart ends it not frozen, its file as the last write made left it.
- **Once frozen, it is not written again in the invocation** (ADR-418 §2).

### 2. The write that fails is warned of once

**The reading whose freezing write fails is warned of as any write of a chart is**: `a step's chart could not be kept beside the report: <exception class name>`, once in the invocation for all the files of charts beside the report and under the runs together (ADR-265 §4, ADR-432 §4, ADR-438 §1), and it fails nothing. A later freezing write that fails again is not warned of again.

### 3. No chart is kept under the run until the chart is frozen

**The chart kept under its run, `charts/<run id>/chart-step-<step>.json` (ADR-438 §1), is written at the reading at which the chart freezes, after the file beside the report, and at no reading whose freezing write failed**: what it holds is the JSON the file beside the report holds frozen. A chart frozen as found finished in an earlier invocation writes none, as ADR-438 §1 has it.

### 4. The modules, and run ids

**In `pipeline`**: `JobSampler` alone; it stays listed in `PipelineHoldsOnlyTheRulesOnRecordTest` as holding no rule, a chart deciding no verdict, cache key, cluster or text of the deliverable. No stage's version names `pipeline` (ADR-222), so **no run id moves**. The chart file's `format` stays 1, and `report.json`, its `format` and `report.html` are unchanged.

## Alternatives refused

- **The chart frozen at the reading, as ADR-432 has it, and its write tried again within that reading.** A reading is made under the lock every reading is made under; a write that fails on a folder standing in its way or a full disk fails again at once, and the next reading, an interval later, is when ADR-265 §4 makes the next write.
- **The sample of the reading whose freezing write failed kept in the chart.** It and the freezing reading's are both taken after the step's end; the frozen chart's last sample is the freezing reading's, as it is where no write fails.
- **A warning at each freezing write that fails.** A file that cannot be written for minutes would warn at every interval, against ADR-265 §4's once.

## Consequences

- **A freezing write that fails costs the chart one interval**: the next reading at which the file can be written freezes it, and the window draws it frozen from then on.
- **A chart kept under its run is the frozen chart beside the report**, never one written at a reading whose freezing write failed.

## Known limits

- **A chart whose file cannot be written for the rest of the invocation ends it not frozen**, its file as the last write made left it: for a step found finished in an earlier invocation empty, or holding the samples of the readings that found it running, and the samples before its end for a step that finished. No chart is kept under its run, so a later invocation finding the step finished says *finished in an earlier invocation* (ADR-438, Known limits).

## What this does not decide

- **What left three charts blank in the invocation #934 reports**, the extraction's, the content census's and the redundancy signatures', with no warning of a chart logged. This record mends a freezing write that fails, which would have been warned of; the trigger seen there is not found, and the test of several steps found finished going through between two readings below freezes each.
- **A reading at which the step runs and whose write fails**: its sample stays in the chart and is written by the next reading that writes it, as ADR-432 §4 has it.

## Tests

| Method | What it holds |
| --- | --- |
| `engine.pipeline.AFreezingWriteThatFailsIsMadeAgainAtTheNextReadingTest.aStepFoundFinishedEarlierIsWrittenFrozenAtTheReadingAfterAWriteThatFailed` | §1: the extraction found finished under a run that keeps no chart of it; the reading after it goes through cannot write its file, a folder standing where its temporary file goes: the file stays as the job's first reading wrote it; the folder taken away, the next reading writes it frozen under the run, `finishedEarlier` true, with no time and no line |
| `…aStepThatFinishesIsWrittenFrozenAtTheReadingAfterAWriteThatFailed` | §1, §3: the extraction reads its four documents and a reading falls at second 0; it finishes, and the reading at second 10 cannot write its file: the file stays as the reading at second 0 wrote it, and no chart is kept under its run; the reading at second 20 writes it frozen at the extraction's end, its samples at seconds 0 and 20, and keeps under the run the very file it froze |
| `engine.pipeline.StepsFoundFinishedEarlierBetweenTwoReadingsFreezeTest.severalStepsFinishedEarlierGoingThroughBetweenTwoReadingsEachFreeze` | §1 with ADR-432 §5 and ADR-438 §2: the census walks and a reading falls; byte-level reduction, the extraction, the content census and the redundancy signatures go through finished before the next, the content census's chart kept under its run: at the next reading, each is frozen under its run, `finishedEarlier` true, the content census holding the chart kept and the time it finished there, the three others no line and no time |

No test here reads §2's warning, which is ADR-265 §4's and ADR-432 §4's, unchanged.

## What the implementation owes

- `JobSampler`: `keepTheChart` holding a chart frozen, its `finishedAt` or its `finishedEarlier`, only once the write of its file holding it frozen is made; a freezing write that fails warned of as §2 has it and writing no chart under its run. For a step that finishes, the chart keeps the samples it held before that reading, and that reading's sample and its `finishedAt` are dropped; for a step found finished in an earlier invocation, the chart is left not frozen, `finishedEarlier` `false`, and the chart read under its run is dropped; its file stays as the reading before wrote it, and the next reading rebuilds it whole. The next reading freezes it afresh. Its javadoc cites this record.
- The tests above passing.
