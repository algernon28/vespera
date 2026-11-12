# ADR-365 — The cockpit's charts carry a legend and a value on each sample, and a running step or leg says its whole percent

> **Partly amended — see [ADR-370](0370-every-bar-of-the-cockpits-steps-carries-its-whole-percent-drawn-on-it-and-nothing-else-of-the-window-changes.md).** Of *Alternatives refused*, *"A percent for a step running with no counter of a total"* and *"A percent beside `finished` or `stopped at a gate`"*: both hold of the state's words, which carry none still; each bar of the steps chart carries its own whole percent drawn on it, `…` on a step running with no counter, and on a step finished or stopped at a gate its bar's percent, `100%` where no counter stops short. §4 and §5, the state's words, stand, and a running step's or leg's percent on its bar is the `<N>` its words say. The rest of this record stands.

> **Partly amended — see [ADR-374](0374-every-view-inside-the-control-panels-window-follows-its-light-and-dark-switch-at-once-its-popups-included.md).** Of §2: five colours are named, `-vespera-series-1` to `-5`, one for each line a chart draws at most, each defined for the light and the dark theme. Of What this does not decide: the colours and the tooltip's look, in both themes, are that record's. Of *Alternatives refused*, *"A copy of the progress page's palette in the window's stylesheet"*: the dark series take the five values `report.html`'s do, as the window's own tokens, which nothing ties to the page's. The rest of this record stands.

- **Date**: 2026-11-07
- **Status**: accepted on 2026-11-07.
- **Decided**: by the operator, that a chart of the cockpit that draws two lines or more carries a legend, a swatch in the colour the chart draws each line in beside the line's name, and a chart of one line none, as `progress.html` has it (ADR-268 §7); that each line has its own colour and the swatch is the colour drawn, with no copy of the progress page's palette; that a sampled point shows its value alone under the pointer, through an invisible hit area for each sample and one visible marker at the latest, as the page has them (ADR-268 §7); that a step running with a counter shows its percentage inside its state's words, `running 37%`, rounded down so that `100%` never shows while it runs, and every other row its word alone; and that a leg's state words take the same form where it runs, from the leg's own bar. The legend's place and look, its behaviour as lines come and go, long names, the text of a value and the tests are decided under delegation.
- **Amends**: [ADR-344](0344-the-control-panels-window-is-one-cockpit-run-and-stop-the-steps-as-bars-beside-the-output-the-progress-pages-three-charts-the-profile-and-the-folders-to-include-starting-from-the-real-configuration.md) §1, the run's `steps`, each row's *"its state"*: the state's words of a step running with a counter of a total carry its whole percent (§4); and the charts' row, *"the progress page's three charts, drawn from §2's file"*: each carries a legend where it draws two lines or more, and each sample a hit area showing its value, the latest a marker (§1 to §3). It carries a note at its head.
- **Amends**: [ADR-361](0361-the-cockpits-steps-fold-into-the-progress-pages-four-legs-and-its-copy-of-their-table-is-held-to-the-pages-by-a-test.md) §3, `leg-state-<leg>`, *"the leg's state in words (§4)"*, and §4, *"A leg's state is ADR-324 §3's"*: the words a running leg shows carry the whole percent of its bar (§5); the state itself, `Legs.statesOf`, is unchanged. Its Tests rows of `controlpanel.TheStepsFoldIntoLegsTest` that read a running leg read it with its percent. It carries a note at its head.
- **Keeps**: [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3, the control panel links no class of the jar; [ADR-324](0324-the-timeline-of-the-steps-folds-into-four-legs-each-one-bar-open-where-the-job-is-and-a-fold-outlives-the-reload-in-the-pages-address.md) §3's rule of a leg's state and §4's of which legs open, and ADR-361 §2, the test that holds the window's copy of both to the jar's; ADR-361 §4's bar of a leg and ADR-344 §2's bar of a step; ADR-344 §2, the file the window reads, unchanged, and the series the jar's rules pick; [ADR-342](0342-the-control-panel-is-a-javafx-window-inside-the-vespera-jar-opened-by-the-jar-started-with-no-argument-and-its-loopback-server-and-pages-are-gone.md) §3, every colour, size and space the window draws is its stylesheet's; [ADR-268](0268-the-progress-page-shows-what-the-stage-that-removes-occurrences-has-removed-and-draws-to-the-data-viz-method.md) §7 and §9 and [ADR-322](0322-the-progress-pages-three-line-charts-stand-in-one-row-that-wraps-each-drawn-600-units-wide-and-legible-down-to-500-px.md), the page's charts and their palette, which stay the page's.
- **Read with**: [ADR-362](0362-an-invocations-report-is-one-json-file-drawn-as-static-html-through-a-mustache-template-and-no-command-opens-anything.md) §8, by which the window reads `report.json` in place of `progress.json`, its `Step`, `Chart` and `Line` as ADR-344 §2 has them. What this record reads of the file, a step's `state`, `done` and `total`, a chart's lines and each line's `name` and `values`, it reads of whichever file the window reads.
- **Settles**: no ticket; the operator's request that the cockpit's charts carry a legend, and the percentages beside the state's words.

## Context

**What the cockpit draws of the charts.** `Cockpit` draws three `LineChart`s, `database-chart`, `counts-chart` and `verdicts-chart` (ADR-344 §1). At each reading of the file it replaces each chart's lines with the file's, one series for each `Line`, named by its `name`, its points the `seconds` and `values` (ADR-344 §2). The database chart draws one line, `size`; the counts chart one, `done`, or two, `read` and `written`; the verdicts chart one line for each kind the step that removes occurrences records, five at most, byte-level reduction's (ADR-268 §1, §9). Every chart is made with `setCreateSymbols(false)`, so no sample is a node and a point has nothing a pointer can rest on. No code of the cockpit names a legend, so each chart keeps JavaFX's: `legendVisible` true, at the bottom, an entry for every line drawn, a chart of one line included, and left out by `Chart`'s layout wherever drawing it would leave the plot less than 150 px high. JavaFX gives each line of a chart a style class `default-color0` to `default-color7` by its place among the chart's lines, and the line, its legend's swatch and its samples' nodes the same one, so the stylesheet colours all three by one rule; the window's stylesheet sets no colour of a chart, and modena's eight chart colours apply.

**What the page draws.** A chart of `progress.html` of two series or more carries a legend, one entry a series in the order drawn, a swatch in the series' colour beside its name, between its heading and its drawing; one series has none, its title naming it. Each sample is a transparent circle of radius 12, its tooltip and its hit area, and one visible marker of radius 4 stands at the latest sample alone (ADR-268 §7). Its colours are the data-viz method's palette (ADR-268 §9), which ADR-362 moves into the template's stylesheet.

**What the steps say.** A step's row shows its state as the file's word: `waiting` where the file names no such step, and otherwise `running`, `finished`, `failed`, `not started` or `stopped at a gate`. Its bar is `done` over `total` where both are given, full where it is `finished` or `stopped at a gate`, moving without a fraction where it runs with no counter, and empty otherwise (ADR-344 §2). The same reading decides that the row of the step the file says is `running` shows its `stop-step` and what a stop there costs. A leg's summary shows `Legs.statesOf`'s word and a bar that is the mean of its rows' bars, a row whose bar moves without a fraction counting as nothing (ADR-361 §4). `pipeline.TheControlPanelsLegsAreTheProgressPagesTest` holds `Legs.statesOf`'s words to the page's.

**A fraction read as a percent.** 370 of 1,000 is 37 %. In a `double`, 29 / 100 × 100 is 28.999999999999996, and so is the bar of a leg whose six steps stand at 1, 1, 0.34 and three nothing, taken as a percent, 38.99999999999999: rounded down as binary numbers, each reads one percent short of what the counts say.

## Decision

### 1. Which charts carry a legend

**A chart that draws two lines or more carries a legend; a chart that draws one line or none carries none**, its title naming what it draws, as `progress.html` has it (ADR-268 §7). The legend has one entry for each line the chart draws, in the order the file lists them, which is the order drawn, each the line's swatch and its name, the file's `name` written as the file writes it.

**It is decided again at each reading of the file**, with the lines: a chart whose lines the file takes from two to one loses its legend at that reading, and one whose lines go from one to two gains it. Before any file is read the charts draw nothing and carry none.

### 2. The legend's place, and the colour of a line

**The legend is the chart's own, JavaFX's, set on the side `TOP`**: between the chart's title and its plot, where `progress.html` sets a chart's legend, between its heading and its drawing, so that the operator who reads both finds it in one place. Its entries stand in a row that wraps onto as many rows as the chart's width needs. A name is written whole, never cut, wherever the chart is wider than the name; the names the jar writes are its own words, `size`, `done`, `read`, `written` and the kinds of ADR-268 §1, the longest 17 characters (`extraction-failed`, `degenerate-output`).

**The legend is drawn at the window's opening size with the most lines a chart draws.** JavaFX leaves a legend out where the plot would be left less than 150 px high; the stylesheet gives each chart a height that keeps a legend of five entries drawn in the window ADR-344 §1 opens, 1500 by 960.

**A line's colour is the stylesheet's colour for its place among the chart's lines**, the one JavaFX's style class `default-color<N>` names; the line, its swatch and its samples' nodes (§3) take the same class, so the swatch is the colour the line is drawn in by one rule, and the window holds no table of colours. Eight such colours are named, and a chart of the jar's draws five lines at most, so each line of a chart has a colour no other line of it has. A line keeps its colour for as long as the file keeps its place.

### 3. A sample's value, and the latest sample's marker

**Each sample of each line is a node at its point, a hit area 24 px across**, the page's circle of radius 12, drawn transparent and taking the pointer over the whole of it, which holds a `Tooltip`, installed with `Tooltip.install`, **whose text is the sample's value alone**: no line's name, no time and no unit, the value axis's title naming the unit (ADR-344 §2). Every sample's node carries the style class `sample`.

**The latest sample of each line, its last value, carries one visible marker**, drawn in the line's colour inside its hit area, which takes the pointer and shows its value as every other does; its node carries the style class `latest-sample` beside `sample`. No other sample shows a mark.

**A value is written by its line**:

| The line's values | Each value written | Example |
| --- | --- | --- |
| every one a whole number | as a whole number, `,` between each group of three digits from four digits up, as ADR-268 §6 writes a count | `5`, `1,234`, `1,234,567` |
| any one not a whole number | to two decimal places, rounded to the nearest hundredth, grouped the same, as ADR-268 §6 writes a size in a unit above bytes | `1.50`, `2.00`, `1,234.57` |

The digits are `0` to `9` and the separators `,` and `.`, whatever the machine's locale. The rule reads the line and not the value, so that a line of sizes writes `2.00` between `1.99` and `2.01`, never `2`.

### 4. A step's state words

**A step's row whose file's state is `running` and which carries a counter of a total, `done` and `total` both given and `total` above 0, says `running <N>%`**: the word, one space, the number and `%`. `<N>` is the largest whole number not above 100 × `done` / `total`, worked out on the whole numbers the file gives, so that 370 of 1,000 reads `running 37%`, 375 of 1,000 `running 37%` and 2 of 3 `running 66%`, rounded down and not to the nearest, 29 of 100 `running 29%` and 1,999 of 2,000 `running 99%`; **and while the step runs, `<N>` is 99 at most**, so that a counter at its total, the step not yet finished, reads `running 99%`, and `100%` never shows while it runs.

**Every other row says its word alone**: a step running with no counter, or with a counter of no total or of a total of 0, says `running`; a step the file does not name, `waiting`; and `finished`, `failed`, `not started` and `stopped at a gate`, as the file writes them, whatever counter they carry.

**What a row does is read from the file's state, never from its words**: the row of the step the file says is `running` shows its `stop-step` and its cost whatever its words read, and its bar is ADR-344 §2's. Only the words shown change.

### 5. A leg's state words

**A leg whose state `Legs.statesOf` gives as `running` says `running <N>%`**, `<N>` 100 × the leg's bar, ADR-361 §4's mean of its rows' bars, worked out as a `double` and **rounded down once a millionth of a percent is added to it**: a binary fraction short of a whole percent by less than that millionth is read as that whole percent, so that a deduplication of six steps standing at 1, 1, 0.34 and three nothing, 38.99999999999999 as a `double`, reads `running 39%`. That is the decimal its counts give wherever that decimal stands a millionth of a percent or more from a whole percent, which a leg's counts fail only where they are very large (Known limits). While the leg runs, `<N>` is 99 at most, as a step's is. So a leg running whose rows stand at 1, 1, 0.37 and three nothing reads `running 39%`, and a leg running whose steps carry no counter and none of which is finished reads `running 0%`, as its bar stands empty.

**Every other leg says its word alone**: `waiting`, `not started`, `finished`, `failed` and `stopped at a gate`.

**`Legs.statesOf` and `Legs.openOf` are unchanged**: they give the leg's state as ADR-324 §3 words it, which ADR-361 §2's test holds to the page's, and which legs open; the percent is added to the words the summary shows, and nothing reads it back.

### 6. The modules, and run ids

Only `io.algernon.controlpanel` changes, and the stylesheet it ships. No class of the jar, no file the jar writes and no property changes; the series each chart draws stay the jar's to pick (ADR-344 §2). No stage's version names the control panel. **No run id moves.**

### 7. What the operator is told

The README's paragraph on what the window shows, in the change that builds this, says that a chart of two lines or more names its lines in a legend above it, that a point of a chart shows its value under the pointer, the latest point of each line being marked, and that a running step and a running part say how far they have got, as `running 37%`.

## Alternatives refused

- **A copy of the progress page's palette in the window's stylesheet.** It would be a second table of colours, free to drift from the page's, as ADR-324 §6 names of a copied table, and ADR-362 puts the page's palette in a template the operator may replace; the window's colours are its own stylesheet's, and the legend and the lines take theirs from one rule.
- **A legend for a chart of one line.** The page draws none, the title naming the line; JavaFX's default drew `size` under the database chart.
- **The legend at the bottom, JavaFX's default.** It stands under the time axis, where the page has none; **at the right**, it takes width from three charts side by side, and JavaFX leaves it out where the plot would be left under 200 px wide.
- **A legend the cockpit builds of its own nodes, with ids.** Its swatches would take each line's colour a second time, read from the line's style class; JavaFX's legend is drawn from the same class as the line.
- **The time and the line's name in a sample's text, as the page's `<title>` holds them.** The operator chose the value alone.
- **One tooltip of every line's value at the pointer's time, as ADR-269 §4's crosshair drew it.** It was not asked for, and ADR-362 takes it from the page too.
- **A visible marker on every sample.** ADR-268 refused it as noise at 256 samples.
- **A value written in its own form, whole where it is whole.** A line of sizes would write `2` between `1.99` and `2.01`.
- **The unit beside each value.** The window knows the unit only from the axis's title, words the jar writes, and would parse them.
- **The percent rounded to the nearest whole number.** 1,999 of 2,000 would read `100%` while the step still runs.
- **A percent for a step running with no counter of a total.** There is nothing to divide; its bar moves without a fraction.
- **A percent beside `finished` or `stopped at a gate`.** Each is full by its word, and a count left short of its total by the step's last progress line would contradict it.
- **The percent in a label of its own, after the state.** The operator chose it inside the state's words; a column of its own would widen every row for the one that runs.
- **`Legs.statesOf` giving `running 37%`.** It gives the state the page's leg shows, which ADR-361 §2's test holds to the page's words.
- **A row's or a leg's logic reading its own words.** `running 37%` is not `running`; the file's state decides what a row does, and the words only say it.

## Consequences

- An operator reading the cockpit's charts sees which line is which where a chart draws more than one, in the colour each is drawn in, and reads any sample's value under the pointer, the latest of each line marked.
- A running step with a counter says how far it has got in whole percent, never `100%` before it finishes, and the part it runs in says how far its bar has got.
- A running part whose running step carries no counter reads `running 0%` until a step of it finishes.
- No schema, cache key, verdict kind, property, run id, file the jar writes or class of the jar changes.

## Known limits

- **A chart of more than eight lines** would give its ninth the first's colour; the jar draws five at most.
- **A window test reads a sample's tooltip from its node**, and does not show it under a pointer; when it shows, after JavaFX's delay, is JavaFX's.
- **The legend is held drawn at the window's opening size.** A window made smaller than that can leave a chart too low for its legend, which JavaFX then leaves out.
- **A leg's percent can read one percent high where its counts are very large.** The millionth of a percent §5 adds before rounding down also lifts a leg whose counts give a decimal short of a whole percent by less than that millionth to that whole percent. A leg's counts can stand that close only where its number of steps times the total of a counter short of its total, or the totals of such counters taken together, is above a million, 10^6: for deduplication's six steps, a total above 333,333. Even then only a few counts do: 153,335 of 333,337, the two steps before it finished, gives 40.999999 and reads `running 41%`. The operator accepts it: reading every count exactly would take the mean as a fraction of whole numbers, for a window a millionth of a percent wide. A step's percent (§4) is worked out on whole numbers and never reads high.

## What this does not decide

- **The look beyond §2 and §3**: the colours, modena's eight or the stylesheet's own, the swatch's and the marker's size and shape, the legend's spacing, the tooltip's delay and style, and a dark theme, the stylesheet's (ADR-342 §3).
- **The charts of an analysis report's native view** (ADR-362 §10): that record's.
- **A hover on a leg or a step's row.**
- **A percent anywhere but the cockpit**: the page, `report.html` and the console are their records'.

## Tests

| Method | What it holds |
| --- | --- |
| `controlpanel.TheChartsCarryALegendAndEachSampleItsValueTest.aChartOfTwoLinesOrMoreCarriesALegendAboveItsPlotAndAChartOfOneNone` | §1, §2: read from a file whose database chart draws `size`, its counts chart `read` and `written` and its verdicts chart five kinds, the database chart draws no legend, and the counts and verdicts charts each draw one, its entries their lines' names in the file's order, standing below the chart's title and above its plot |
| `controlpanel.TheChartsCarryALegendAndEachSampleItsValueTest.eachSwatchIsItsLinesColourAndEachLineOfAChartHasItsOwn` | §2: in the verdicts chart's legend of five, each swatch's outermost fill is the colour its line is drawn in, and the five lines are drawn in five colours |
| `controlpanel.TheChartsCarryALegendAndEachSampleItsValueTest.theLegendFollowsTheLinesAsTheFileChangesThem` | §1: read again from a file whose counts chart draws `done` alone and whose verdicts chart draws two kinds, the counts chart draws no legend and the verdicts chart a legend of the two |
| `controlpanel.TheChartsCarryALegendAndEachSampleItsValueTest.eachSampleShowsItsValueAloneWrittenAsItsLineWritesIt` | §3: every sample of the three charts is a node of its own holding a tooltip; `size` at 1.5, 2 and 1,234.567 reads `1.50`, `2.00` and `1,234.57`; `read` at 5, 1,234 and 1,234,567 reads `5`, `1,234` and `1,234,567`; `written` at 0, 12 and 1,200 reads `0`, `12` and `1,200`; the class reads the window with the JVM's default locale set to Italian, whose separators are `.` and `,` the other way round, and restores it after, so that a value written in the machine's locale reads wrong on every machine |
| `controlpanel.TheChartsCarryALegendAndEachSampleItsValueTest.eachSamplesHitAreaTakesThePointerAndOnlyTheLatestIsMarked` | §3: every sample's node is at least 24 px wide and high, takes the pointer on its bounds and is not transparent to the mouse; every fill of a sample's node but the latest's is transparent, and the latest's holds a fill in its line's colour |
| `controlpanel.ARunningStepSaysHowFarItHasGotTest.aRunningStepWithACounterOfATotalSaysItsWholePercentRoundedDown` | §4: extraction running at 370 of 1,000 reads `running 37%`, at 375 of 1,000 `running 37%` and at 2 of 3 `running 66%`, rounded down where the nearest would read 38 and 67, at 29 of 100 `running 29%`, at 1,999 of 2,000 `running 99%` and at 2,000 of 2,000 `running 99%`; at each, its row shows its Stop and its cost |
| `controlpanel.ARunningStepSaysHowFarItHasGotTest.aStepNotRunningOrWithNoCounterOfATotalSaysItsWordAlone` | §4: byte-level reduction running at 5 of no total reads `running`, and extraction running at 5 of a total of 0 `running`; extraction running with no counter reads `running`, its row showing its Stop and its cost, a step the file does not name `waiting`, and one the file says has not started `not started`; the census finished at 10 of 10 reads `finished`, and the seed-corpus comparison stopped at a gate at 2 of 4 `stopped at a gate`; byte-level reduction failed at 3 of 4 reads `failed` |
| `controlpanel.ARunningStepSaysHowFarItHasGotTest.aRunningLegSaysTheWholePercentOfItsBarAndEveryOtherLegItsWordAlone` | §5: with extraction at 370 of 1,000, deduplication reads `running 39%` and the three others `not started`; at 340 of 1,000, `running 39%`; with content redundancy running at its total and the steps before it finished, `running 99%`; with extraction running with no counter after two steps finished, `running 33%`, and translation, none of whose steps the file names, `waiting`; with seed extraction running and no counter, translation reads `running 0%` and deduplication `finished`; first stopped at a gate in translation, `finished`, then `stopped at a gate` three times; with byte-level reduction failed, deduplication reads `failed` |

`controlpanel.TheStepsFoldIntoLegsTest`'s methods `theLegRunningFailedOrFirstStoppedAtAGateIsOpenAndTheOthersFolded`, `aLegsBarIsTheMeanOfItsRowsBarsAndAnOpenLegShowsItsNameAlone` and `aLegTheOperatorOpensOrFoldsStaysSoAsTheProgressIsReadAgain` read a running leg's words as §5 has them: deduplication with extraction at 1 of 4, its bar 0.375, reads `running 37%`, and translation and clustering, each running with no counter and none of its steps finished, `running 0%`. `controlpanel.StopAndCloseAskFirstTest` and `controlpanel.TheStepRowsAlignInColumnsTest`, whose running step carries a counter of 1 of 4 and so reads `running 25%`, find its row's `stop-step` shown and are unchanged: they hold that the row's Stop is read from the file's state and not from its words.

## What the implementation owes

Beside the tests above, which it turns green, the change builds §1 to §5 in `Cockpit` and gives `sample`, `latest-sample` and the charts' height their look in the stylesheet, and says in the README's paragraph on what the window shows what §7 says.
