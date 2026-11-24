# ADR-449 — The census's chart is held emptied only once its empty file is written

- **Date**: 2026-11-24
- **Status**: accepted on 2026-11-24.
- **Release**: patch
- **Decided**: on [#937](https://github.com/algernon28/vespera/issues/937), as the issue sets it out: that the census's chart is held emptied only once the write of its file emptied succeeds, and that an emptying write that failed is made again at the next reading. This record's own: that every reading at which the census's step stands ended with no walk handed tries it until one write is made, the job's last reading among them (§1), that it is warned of as any chart's write is, once in the invocation (§2), and the test.
- **Amends**: [ADR-418](0418-every-stages-chart-is-written-empty-as-the-job-of-vespera-run-begins-and-frozen-only-within-its-invocation.md) §4, its bullet *"A census whose step ends with no walk handed, its corpus root's walk failed, writes stage 0's file empty at the first reading at which its step stands ended, and takes no sample of it"*, the file being the census's step file since [ADR-432](0432-each-step-has-a-chart-beneath-its-legs-bars-the-output-spans-the-column-above-the-legs-and-the-cockpit-holds-no-tabs.md) §4: at the first reading at which its step stands ended with no walk handed and the write of its file emptied succeeds; until then every such reading writes it empty again, the job's last reading included (§1).
- **Keeps**: ADR-418 §4's other bullets, the walk walked's samples written as they are taken and the key moved to the walk handed, and that the census's chart ended with no walk handed takes no sample. ADR-432 §4's file, its shape and `format` 1, written whole through a temporary file moved over it, a file that cannot be written warned of once in the invocation and failing nothing. [ADR-265](0265-vespera-run-rewrites-a-progress-page-in-the-working-directory-while-it-runs.md) §4, a write that fails warned of once and failing nothing, the next made on time. [ADR-448](0448-a-steps-chart-is-frozen-only-once-its-frozen-file-is-written.md), a step's chart frozen only once its frozen file is written, of which this record is the census's emptying's like. [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md), no stage's version naming `pipeline`.
- **Settles**: [#937](https://github.com/algernon28/vespera/issues/937).

## Context

**How the census's chart is emptied.** Before the census hands the job its walk, each reading writes the census's file, `chart-step-census.json`, under the walk walked, its key `{"walk": "<walk walked>"}`, holding each sample as it is taken (ADR-418 §4, ADR-432 §4). Where the census's step ends with no walk handed, its corpus root's walk failed, the walk walked may be the seed folder's (ADR-412, Context), and the reading at which the step stands ended writes the file empty: no key, no `finishedAt`, `finishedEarlier` `false` and no line. The chart, once emptied, is not written again in the invocation. A file that cannot be written is warned of once in the invocation, `a step's chart could not be kept beside the report`, and fails nothing (ADR-265 §4, ADR-432 §4).

**An emptying write that fails.** ADR-418 §4 held the chart emptied at that reading, whatever its write did. Where the write threw, every later reading of the invocation found the chart emptied and wrote it no more: the file kept what the reading before wrote, the chart of the walk walked, and the window drew the seed folder's walk as the census's chart for the rest of the invocation. It is the defect ADR-448 mended for a step's freezing write, on the census's emptying write.

## Decision

### 1. The census's chart is emptied only once its file emptied is written

**A census whose step ends with no walk handed has its file written empty at the first reading at which its step stands ended and that write succeeds.** Where the write fails, the chart is not held emptied, and the next reading at which the step stands ended with no walk handed writes it empty afresh, as though that reading were the first.

- **The reading whose emptying write fails** leaves the file as the reading before wrote it, the chart of the walk walked, and takes no sample of it.
- **Until it is emptied, the file is written empty at every reading at which the census's step stands ended with no walk handed**, the job's last reading among them, until one write is made. Where none is made in the invocation, the file ends it as the last write made left it.
- **Once emptied, it is not written again in the invocation**, as ADR-418 §4 has it.

### 2. The write that fails is warned of once

**The reading whose emptying write fails is warned of as any write of a chart is**: `a step's chart could not be kept beside the report: <exception class name>`, once in the invocation for all the files of charts together (ADR-265 §4, ADR-432 §4, ADR-448 §2), and it fails nothing. A later emptying write that fails again is not warned of again.

### 3. The modules, and run ids

**In `pipeline`**: `JobSampler` alone; it stays listed in `PipelineHoldsOnlyTheRulesOnRecordTest` as holding no rule, a chart deciding no verdict, cache key, cluster or text of the deliverable. No stage's version names `pipeline` (ADR-222), so **no run id moves**. The chart file's `format` stays 1, and `report.json`, its `format` and `report.html` are unchanged.

## Alternatives refused

- **The chart held emptied at the reading, as ADR-418 §4 has it, and its write tried again within that reading.** A write that fails on a folder standing in its way or a full disk fails again at once; the next reading, an interval later, is when ADR-265 §4 makes the next write, as ADR-448 has it for a freezing write.
- **A warning at each emptying write that fails.** A file that cannot be written for minutes would warn at every interval, against ADR-265 §4's once.

## Consequences

- **An emptying write that fails costs the census's chart one interval**: the next reading at which the file can be written empties it, and the window draws it empty from then on.

## Known limits

- **Where the job's last reading is the one whose emptying write fails**, or the file cannot be written for the rest of the invocation, the chart of the walk walked stays in the census's file until the next job writes it empty as it begins (ADR-418 §1); the window, drawing with no invocation running the last job's charts (ADR-418 §5), draws it until then.

## Tests

| Method | What it holds |
| --- | --- |
| `engine.pipeline.ACensusEmptyingThatFailsIsMadeAgainAtTheNextReadingTest.aCensusFileThatCouldNotBeWrittenEmptyIsWrittenEmptyAtTheNextReading` | §1: the census walks the seed folder and a reading falls, its file holding the seed folder's walk and one `done` line of one point, 2,000 entries; the census fails with no walk handed, and the reading 10 s on cannot write its file, a folder standing where its temporary file goes: the file stays as the reading while it walked wrote it; the folder taken away, the next reading writes it empty, `format` 1, its step, no key, no `finishedAt`, `finishedEarlier` false and no line |

No test here reads §2's warning, which is ADR-265 §4's and ADR-432 §4's, unchanged.

## What the implementation owes

- `JobSampler`: `beforeTheHandOver` holding the census's chart emptied only once `writeTheChart` writing it empty returns; a write that throws leaves it not held emptied, warned of as §2 has it, and the next reading writes it empty afresh. Its javadoc cites this record.
- The test above passing.
