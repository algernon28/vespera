# ADR-422 — A line of one sample is drawn as a marker in its colour, on a frozen chart too

- **Date**: 2026-11-18
- **Status**: accepted on 2026-11-18.
- **Decided**: by the operator on 2026-11-18: a stage's chart whose lines each hold a single sample, frozen or not, draws a visible marker for each line, holding its value, and keeps its legend. Under the operator's delegation: that the rule is each line's, whatever the chart's other lines hold (§1); the marker's look, and its dimming on a frozen chart (§2); and the tests.
- **Amends**: [ADR-382](0382-each-stage-has-a-chart-of-its-own-in-two-rows-frozen-once-it-finishes-and-kept-under-its-run-beside-the-report.md) §6, *"no sample carries `latest-sample`: every sample keeps its hit area 24 px across and its value (ADR-365 §3), and none is marked"*: on a frozen chart the sample of a line that holds one sample alone is marked, by `only-sample` (§1, §2); and its fourth Known limit, *"A run put back draws one sample"*: that sample is drawn. It carries a note at its head.
- **Amends**: [ADR-365](0365-the-cockpits-charts-carry-a-legend-and-a-value-on-each-sample-and-a-running-step-or-leg-says-its-whole-percent.md) §3, *"No other sample shows a mark"*: a line's only sample shows the latest sample's mark, on a frozen chart too (§2). It carries a note at its head.
- **Keeps**: ADR-382 §4, the window draws what each file holds and decides none of it, and §6's dimming to 0.85 of a frozen chart's lines and swatches, its title and its style class `frozen`; ADR-382 §9, every series colour at 3 : 1 against the plot and the legend at that opacity; ADR-365 §1, a legend for two lines or more and none for one, and §3, each sample's hit area 24 px across holding its value alone; [ADR-412](0412-stage-0s-chart-is-kept-under-the-walk-the-census-keeps-and-a-new-key-empties-no-other-stages-chart.md) §3 and §4, which keep a frozen chart's file as it is and repair none; [ADR-381](0381-every-view-inside-the-control-panels-window-follows-its-light-and-dark-switch-at-once-its-popups-included.md) §2, a chart's n-th line in `-vespera-series-<n>`, every colour a token; [ADR-374](0374-every-text-line-and-marker-of-the-window-its-charts-and-each-analysis-reports-native-view-stands-at-aa-in-both-themes.md) §1, every marker of a chart at 3 : 1 against its plot.

## Context

**What a stage's file can hold.** A run put back, its stage's steps going through finished, starts its chart afresh and freezes it at that reading: one sample of its kinds and no `done` (ADR-382 §7). Stages that go through finished between two readings are started afresh and frozen so too, each holding one sample, its kinds counted at second 0, and no `done` (ADR-412 Context, item 3). ADR-412 §3 keeps such a file as it is and §4 repairs none, so a working directory can keep, for stage 1, a frozen chart of five lines of one sample each.

**What the window draws of it.** A `LineChart` draws a series' line as one path from its first point to its last, so a series of one point draws no line. The window creates no symbols of its own (`setCreateSymbols(false)`); it gives each sample a node, a hit area 24 px across drawn transparent, holding the sample's value in its tooltip, and marks the latest sample of each line, the last, by `latest-sample`, which the stylesheet draws as a square 8 px across in the line's colour (ADR-365 §3). A frozen chart marks none (ADR-382 §6). So a frozen chart whose lines hold one sample each draws its title, *finished at* and all, its axes and its legend, and nothing in its plot: each line's value lies under a hit area that shows nothing. A chart not frozen of one sample a line draws each, the one sample being the latest.

## Decision

### 1. A line of one sample shows it

**A line whose file holds one sample draws that sample as a marker**, frozen or not, whatever the chart's other lines hold: its node carries the style class `only-sample` beside `sample`. A line of two samples or more is drawn as before: its latest sample marked on a chart not frozen, and none of its samples on a frozen one (ADR-382 §6). On a chart not frozen the one sample of a line is also its latest, and carries `latest-sample` as well, drawn alike.

### 2. The marker

**The marker is the latest sample's** (ADR-365 §3): a square 8 px across, its corners rounded at 4 px, in the line's colour, `-vespera-series-<n>` for the chart's n-th line, inside the sample's hit area 24 px across, which takes the pointer and shows the sample's value alone, a whole count written as the window writes one, `1,031`. **On a frozen chart it is dimmed to 0.85**, as the chart's lines and its legend's swatches are (ADR-382 §6); at that opacity every series colour of both themes stands at 3 : 1 or more against the plot (ADR-382 §9), so ADR-374 §1 holds of the marker as of the lines.

### 3. The legend and the title

**Unchanged**: a chart of two lines or more carries its legend, naming its lines in the file's order, and one of one line none, its title naming it (ADR-365 §1); a frozen chart's title ends with *, finished at `HH:mm`* (ADR-382 §6).

### 4. The modules, and run ids

**In `ui` alone**: `ProgressCharts` and `control-panel.css`. The jar, the stages' files, `report.json` and `report.html` are unchanged. **No run id moves.**

## Alternatives refused

- **The rule a chart's, where all its lines hold one sample.** A frozen chart whose `done` holds two samples and a kind one would still hide that kind's value; the line's rule covers the chart of one sample a line and that one alike.
- **The one sample marked `latest-sample` on a frozen chart.** A frozen chart has no latest sample (ADR-382 §6); `latest-sample` keeps meaning the sample a chart still moving took last.
- **JavaFX's own symbols on a frozen chart.** They mark every sample of every line, up to 256 a line (ADR-382 §5), on every frozen chart.
- **A short level line drawn from the one sample.** It would draw time the stage did not take.
- **The file repaired, or a second sample written by the jar.** The window draws what each file holds (ADR-382 §4), ADR-412 §4 repairs no file, and a second sample's time would be made up.

## Consequences

- A stage gone through finished, a run put back among them, shows each of its kinds' counts as a mark at 0 s, its value under the pointer, beneath its title and above its legend.
- On a frozen chart mixing lines of one sample and of more, the first show a mark and the others none.

## Known limits

- **Lines of one sample at the same second and the same value draw their marks at one point**, the last line's over the others'; the pointer there shows that one value, which is each of theirs.

## What this does not decide

- **Which charts hold one sample a line**: ADR-382 §7 and ADR-412 decide what the stages' files hold.
- **A line of no sample**, drawn as before: nothing.
- **`report.html`'s charts**, ADR-362 §6's, unchanged.

## Tests

| Method | What it holds |
| --- | --- |
| `ui.EachStageHasAChartTest.aLineOfOneSampleIsDrawnAsAMarkerFrozenOrNot` | §1 to §3: the window opened with no invocation over a working directory keeping stage 1's chart frozen with five lines of one sample each at 0 s and no `done`, as a run put back leaves it; stage 2's frozen with two lines of two samples and one of one; and stage 4's not frozen with two lines of one sample: on stage 1's, frozen, each line shows exactly one marker, drawn in the colour its line is drawn in, 8 px across inside a hit area 24 px across, at opacity 0.85 as its lines are, holding its value alone, *3*, *80*, *1*, *12* and *1,031*, and the legend shown names the five lines in the file's order; on stage 2's, its two lines of two samples show none and its line of one sample one; on stage 4's, each of its two lines one |

`ui.EachStageHasAChartTest.aFinishedStagesChartIsDrawnFrozenFromItsFileAsTheWindowOpens` holds, unchanged, that a frozen chart whose lines hold several samples marks none of them as the latest, and `ui.EveryBarOfTheStepsCarriesItsPercentTest.everyTextOfTheWindowAndEveryMarkOfItsChartsStandsAtAaInBothThemes` the series colours at 3 : 1 at the opacity a frozen chart draws them at (ADR-374, ADR-382 §9).

## What the implementation owes

- `ProgressCharts.show`: the node of the one sample of a line holding one gives itself `only-sample` beside `sample`, and gives it again whenever the chart sets its classes anew, as it keeps `sample`; frozen or not.
- `control-panel.css`: `.progress-chart .chart-line-symbol.only-sample` drawn as `latest-sample` is, its corners at 4 px and its fill inset 8 px, in `-vespera-series-<n>` for `default-color<n-1>`, and `.progress-chart.frozen .chart-line-symbol.only-sample` at opacity 0.85; every colour a token (ADR-381 §2). The stylesheet's comment on a sample's hit area names the marker of a line's only sample beside the latest's.
- The test above passing.
