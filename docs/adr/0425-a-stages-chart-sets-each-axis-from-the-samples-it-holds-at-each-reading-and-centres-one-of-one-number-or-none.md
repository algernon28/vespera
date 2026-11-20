# ADR-425 — A stage's chart sets each axis from the samples it holds at each reading, and centres one of one number or none

> **Partly amended — see [ADR-432](0432-each-step-has-a-chart-beneath-its-legs-bars-the-output-spans-the-column-above-the-legs-and-the-cockpit-holds-no-tabs.md).** Of §1 to §5: a step's chart, each axis set as they say; a step finished in an earlier invocation holds no sample and draws both axes as §4 draws an axis of none; of its Consequences' first bullet: it draws no marker. The rest of this record stands.

- **Date**: 2026-11-19
- **Status**: accepted on 2026-11-19.
- **Decided**: on [#884](https://github.com/algernon28/vespera/issues/884), a defect of [ADR-382](0382-each-stage-has-a-chart-of-its-own-in-two-rows-frozen-once-it-finishes-and-kept-under-its-run-beside-the-report.md) §4, *"the window draws what each file holds"*, which none of its Known limits covers: that each reading sets both axes of a stage's chart from that chart's samples alone (§1); that an axis of two numbers or more is ranged as before (§2); the span and ticks of an axis of one number (§3) and of none (§4); and that no tick below 0 carries a label (§5). #884's other cause, a line of one sample drawn as nothing on a frozen chart, is [ADR-422](0422-a-line-of-one-sample-is-drawn-as-a-marker-in-its-colour-on-a-frozen-chart-too.md)'s.
- **Amends**: ADR-382 §5, *"the axis of every chart starts at 0"*: the time axis of a chart whose samples stand at one second is centred on it, and that of a chart of no sample on 0, each running below 0 unlabelled (§3 to §5); an axis of two seconds or more starts at 0 as before. It carries a note at its head.
- **Amends**: [ADR-371](0371-a-size-of-memory-is-written-in-powers-of-1024-and-only-a-count-axis-of-the-cockpit-is-shortened-to-k-m-b-and-t.md) §3 and §4, *"each tick reads"* and *"Each tick is written in that unit"*: on a stage's chart, a tick below 0 is drawn with no label (§5); every other tick reads as those sections write it. It carries a note at its head.
- **Keeps**: ADR-382 §2, each chart's lines and its axes' titles, *Elapsed* and *Done (count)* or *Done and verdicts (count)*; §4, the window drawing what each file holds and deciding none of it; §6, a frozen chart's title, dimming and style class; ADR-371 §3 and §4 for every tick at 0 or above; ADR-422 §1 and §2, the marker of a line's only sample; [ADR-365](0365-the-cockpits-charts-carry-a-legend-and-a-value-on-each-sample-and-a-running-step-or-leg-says-its-whole-percent.md) §1 and §3, the legend and each sample's hit area 24 px across holding its value; [ADR-418](0418-every-stages-chart-is-written-empty-as-the-job-of-vespera-run-begins-and-frozen-only-within-its-invocation.md), what each stage's file holds.
- **Settles**: [#884](https://github.com/algernon28/vespera/issues/884).

## Context

**What a stage's file can hold.** Since ADR-418 every stage's file is written with no line as the job of `vespera run` begins (§1), and a stage going through finished freezes holding one sample a line at second 0 and a `done` of none (§3). So the window draws, at one reading or another, charts of no sample, charts whose samples all stand at one second, and charts whose samples all hold one value, beside charts of curves.

**What the window does with them.** `ProgressCharts` builds each chart's two `NumberAxis` once, auto-ranging and not forced to take in 0, and at each reading replaces the chart's lines. A `NumberAxis` auto-ranges from the numbers its chart's samples give it, and from nothing else only where those numbers differ. Measured on the Headless platform with JavaFX 26.0.2, a `ProgressCharts` drawn once over curves to 421 s and 70,000, and again:

| The chart drawn next holds | Its time axis | Its value axis |
| --- | --- | --- |
| no sample: no file, a file of no line, or lines of none | the earlier drawing's, 0 to 450 s; or that widened by a tick at a reading, 0 to 45 s, then 50 s, then 55 s | the earlier drawing's, or widened likewise, 0 to 80,000, then 90,000, then 100,000 |
| one sample, at 0 s, of 604 | the earlier drawing's, 0 to 450 s | 597.5 to 612.5, ticked every 2.5 |
| two samples, at 0 s, both 0 | 0 to 0 | 0 to 0 |
| two lines of one sample, at 5 s, of 3 and 80 | 4.95 to 5.05 s, ticked every 0.01 s | 0 to 90 |
| one sample at 0 s, of 7, on a chart not drawn before | 0 to 110 s, as a chart drawn first has it | 6.93 to 7.07, ticked every 0.01 |

An axis whose samples differ in nothing is thus kept from the last drawing, collapsed to a point, or spanned by a fiftieth of its number, whose ticks ADR-371 §4 writes to one decimal, so that 4.96 s and 4.97 s both read `5 s`.

**An axis of two numbers carries the last drawing too.** While it ranges itself, a `NumberAxis` measures the labels of the ticks it tries, and the window's time formatter writes a tick in the unit the axis's tick spacing fits (ADR-371 §4), which is still the last drawing's. Measured on a chart 170 px wide, a flat line at 0 s and 5 s drew its time axis from 0 to 6 s, ticked every second, on a chart not drawn before, and from 0 to 7.5 s, ticked every 2.5 s, after a curve to 421 s ticked every 100 s, whose labels it measured in minutes; with the tick spacing put back to 5, a `NumberAxis`'s own before it is first drawn, it drew 0 to 6 s again.

**Where a sample at an axis's end is drawn.** A `LineChart` clips its plot's content to the plot. A sample at an axis's lower bound is centred on the plot's edge, so half its hit area, and half of ADR-422's marker, lie outside it and are not drawn: measured, a sample's node 10 px across at 0 s on an axis from 0 stands from 5 px left of the plot's edge to 5 px right of it.

## Decision

### 1. Each reading sets both axes from the chart's own samples

**At each reading the window sets each stage's chart's time axis from the seconds of every sample its lines hold, and its value axis from their values**, those of all its lines together. Nothing of an earlier drawing moves either axis: not the last reading's, another working directory's or an earlier invocation's chart. A chart drawn from a file is drawn alike whatever the window drew before it.

### 2. An axis of two numbers or more is ranged as before

**An axis whose samples hold two different numbers or more is ranged by the chart from them**, from the least to the greatest with the chart's own padding, not forced to take in 0 (ADR-382 §2), and as it ranges a chart not drawn before: its tick spacing is put back to a `NumberAxis`'s own, 5, before it ranges, so that the labels it measures are not written in the last drawing's unit. A time axis of samples from 0 s to 421 s still starts at 0.

### 3. An axis of one number is centred on it

**An axis whose samples all hold one number, *n*, spans from *n* − *d* to *n* + *d*, ticked every *d***, where *d* is the largest power of ten not above the magnitude of *n*, and 1 where that magnitude is below 1. Its middle tick is *n*, and every sample stands at its middle, so that no edge of the plot cuts its marker along that axis. So:

| *n* | *d* | The axis | Its ticks read |
| --- | --- | --- | --- |
| 0 s | 1 | −1 s to 1 s | `0 s`, `1 s` (§5) |
| 7 | 1 | 6 to 8 | `6`, `7`, `8` |
| 604 | 100 | 504 to 704 | `504`, `604`, `704` |
| 1,031 | 1,000 | 31 to 2,031 | `31`, `1K`, `2K` |
| 421 s | 100 | 321 s to 521 s | `5.4 min`, `7 min`, `8.7 min` |

Each tick reads as ADR-371 §3 and §4 write it, by the axis's spacing, *d*. A chart whose samples all stand at one second, as a stage gone through finished leaves it, draws its time axis so; its value axis by §2 where its lines hold different values, and by §3 where they hold one.

### 4. An axis of no number is drawn as one of 0

**An axis whose chart holds no sample spans from −1 to 1, ticked every 1**, as §3 draws an axis of 0 alone. A chart of no sample, whether the working directory keeps no file of it, its file holds no line, or its lines hold none, draws both axes so at every reading, whatever it drew before: its time axis reads `0 s` and `1 s`, its value axis `0` and `1`.

### 5. No tick below 0 carries a label

**On a stage's chart a tick below 0 is drawn with no label.** Neither axis holds a number below 0, seconds counting from a stage's first sample and values being counts; only §3 and §4 draw an axis below 0, to centre 0 on it. Every tick at 0 or above reads as ADR-371 §3 and §4 write it.

### 6. The modules, and run ids

**In `ui` alone**: `ProgressCharts`. The jar, the stages' files, `report.json` and `report.html` are unchanged. **No run id moves.**

## Alternatives refused

- **The axes reset before each reading and left to auto-range.** An axis of one number would still be ranged as JavaFX ranges it: collapsed to 0 to 0, or spanned by a fiftieth of its number, ticks reading alike.
- **An axis of one number spanned by a share of it, *n* ± 10%.** Whole counts would read fractional ticks, `543.6` and `664.4` about 604.
- **An axis of one number spanned *n* ± 1.** Shortened ticks would read alike: 999,999, 1,000,000 and 1,000,001 all read `1M` (ADR-371 §3).
- **A time axis of one second starting at 0, as ADR-382 §5 had every axis start.** A stage gone through finished holds its one second at 0, so every one of its samples would stand at the plot's left edge, half its marker not drawn.
- **A chart of no sample drawing no axes, or no tick labels.** Its plot would move and change size as its first sample arrived, and stand unlike the seven others.
- **A chart built anew at each reading.** It would still range an axis of one number as JavaFX does, and drop the style classes ADR-422 has a sample's node keep.

## Consequences

- A stage gone through finished draws its kinds' markers at the middle of a time axis reading `0 s` and `1 s`, each whole, beside a value axis ranged from its counts.
- A chart of no sample reads `0` and `1` on both axes whatever the window drew before it, and a chart drawn twice from one file is drawn alike both times.
- A flat line, every sample one value, stands at the middle of its value axis, *n* between *n* − *d* and *n* + *d*.

## Known limits

- **A sample at the least value of an axis §2 ranges from 0 stands at the plot's lower edge**, half its marker cut: a kind of 1 beside a kind of 1,031, on a value axis from 0 to 1,250, is drawn on the edge. §2 ranges such an axis as before.
- **A tick between *n* − *d* and *n* + *d* reads *n* as ADR-371 §3 shortens it**, so 1,031 reads `1K`; the sample's hit area holds its value whole, `1,031` (ADR-365 §3).
- **An axis of one number that is not whole would not tick it**: JavaFX starts the ticks of a whole spacing at the whole number above the lower bound, measured −0.63 to 1.37 ticked at −0.63, 0, 1 and 1.37. A stage's file holds whole seconds and whole counts.

## What this does not decide

- **`report.html`'s charts**, ADR-362 §6's, unchanged.
- **What a stage's file holds**: ADR-382 §5, ADR-412 and ADR-418.
- **The marker of a line's only sample**: ADR-422.

## Tests

| Method | What it holds |
| --- | --- |
| `ui.EachStageHasAChartTest.eachAxisIsSetFromTheSamplesItsChartHoldsAndNotFromAnEarlierDrawing` | §1 to §5: a window opened over a working directory keeping stages 0 to 5 drawn as curves to 421 s and 70,000, then the operator choosing a second working directory keeping, as a stage gone through finished leaves it, stage 1 frozen with five lines of one sample each at 0 s, stage 2 frozen with a `done` of none and two kinds at 0 s of 3,176 and 1,974, stage 3 frozen with a `done` of none, stage 4 frozen with a `done` of none and one kind at 0 s of 604, stage 0 a flat `done` of 7 at 0 s and 5 s, and no file of stage 5: each chart's two axes, their bounds and the tick labels drawn, once laid out, are those a window opened over the second directory alone draws; there, stages 1 to 4's time axes run from −1 s to 1 s, labelled `0 s` and `1 s`; stage 4's value axis from 504 to 704, labelled `504`, `604` and `704`; stage 0's from 6 to 8, labelled `6`, `7` and `8`; stages 3 and 5 draw both axes from −1 to 1, labelled `0 s` and `1 s`, and `0` and `1`; and the marker of stage 4's one sample, 8 px across, lies whole inside its plot |

## What the implementation owes

- `ProgressCharts.show`: for each chart, before its lines are set, its time axis's numbers are the distinct seconds of every sample of every line it draws, and its value axis's their distinct values. An axis of two numbers or more is set auto-ranging (`setAutoRanging(true)`), its tick unit first set to 5, `NumberAxis`'s default, so that ranging measures its labels as an axis not drawn before does; one of one number *n*, or of none, taking *n* = 0, is set not auto-ranging, its lower bound *n* − *d*, upper bound *n* + *d* and tick unit *d*, *d* as §3 gives it. A chart the window holds no file of, `drawn == null`, is drawn as one of no sample.
- `ProgressCharts.chart`: both axes' tick formatters draw a tick below 0 as the empty text, and every other tick as `timeTick` and `tickText` write it; `timeTick`, `tickText` and `shortened` themselves unchanged.
- Whatever else it takes for a chart drawn after another working directory's to stand as one drawn first, its bounds and tick labels alike, once laid out: the test compares the two, stage 0's flat line among them.
- The test above passing.
