# ADR-267 — The progress page draws tiles, a timeline of the steps and charts over time, and its tables are pages of their own

> **Partly amended — see [ADR-268](0268-the-progress-page-shows-what-the-stage-that-removes-occurrences-has-removed-and-draws-to-the-data-viz-method.md).** Of §1: a tile holds a label, a value and a detail; the outcome step's survivors and verdicts by kind join the tiles, with rates; the chart of counts is drawn from the running step's own counter first; a chart of verdicts is added; every chart has a visible heading, a caption, titled axes, a size axis over its values' own range, one marker at its latest point and a legend for two series or more. Of §2 and §3: a sample holds the progress line, the outcome step, its survivors and the eight kinds' counts, and the table of samples gains their columns. Of §4 and §7: the colours are the data-viz method's palette, checked by its validator, and a state's word beside each bar replaces the 3:1 rule for state colours. Of the alternatives refused: rates per minute are on the tiles. The rest of this record stands.

- **Date**: 2026-10-16
- **Status**: accepted on 2026-10-16.
- **Amends**: [ADR-265](0265-vespera-run-rewrites-a-progress-page-in-the-working-directory-while-it-runs.md), in these places and no others: §1's list of what the page holds, whose table moves to a page of its own (§3 below) and which gains what §1 below draws; §2's sentence "Each write renders the whole page into `progress.html.part` beside it and moves it over `progress.html`", which §3 below extends to three pages; two of its alternatives refused, "A page that marks itself stale with a script", which §5 reverses for one fixed script, and "A history kept for charts over time", which §2 reverses for a bounded one; and the first item of its What this does not decide, "any bar or chart beside a count", which this decides.
- **Keeps**: the rest of ADR-265, above all §1's rule that the page names no path, no title and no text of a document, no exception message and no run id, the strings §1 fixes, the table's columns and its five states, §3's rule that the page reads only what the invocation holds in memory and makes no query, and §4, a write that fails fails nothing; [ADR-266](0266-every-invocation-names-its-progress-page-first-and-opens-it-and-vespera-label-writes-it-too.md), the label command's page and its table of answers, and the one page that is opened; [ADR-130](0130-one-report-page-module-owns-the-skeleton-styling-escaping-and-tables.md), one module assembles every page, self-contained; [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md), no template engine and no dependency a decision does not require; [ADR-223](0223-5fs-size-report-6a-and-6b-go-through-one-seed-partition-at-a-time-and-what-is-still-held-says-why.md) §3 and §6, a page moved over its place in one move; [ADR-226](0226-the-eight-rules-in-pipeline-live-in-extraction-embedding-synthesis-and-profile-and-no-stages-version-names-pipeline.md), no stage's version names `pipeline`.

## Context

ADR-265's page is a table: a row for each of the fifteen steps, and four lines above it. Read while an invocation runs for hours, it says where the job is now and nothing of how it got there or how fast it goes, and the step that is running is one row among fifteen. The operator, having read it live, asked for charts in its place, with no new setting, and for the tables of detail in files of their own.

A throwaway prototype outside the build drew the look the operator had in mind: tiles of a few large numbers, line charts over time with each series' latest value printed at its end and a tooltip on hover, light and dark colours through CSS custom properties, and a mark saying whether the page is still being rewritten. It drew its SVG in the browser, with a script, from a JSON object written inside the page.

What the page reads is already held at each write: every step's execution with its start, its end and its counts, the database's size, read by a file system call (ADR-265 §1), and the running step's last progress line, of which a counter's has the shape `<label>: <N> of <M> (<P>%)` or `<label>: <N> so far` and a statement's `<label>: about <P>% of <N> rows` (ADR-265 §3). A step's counts at an earlier write are not: the job holds each count as it is now.

## Decision

### 1. What `progress.html` draws while `vespera run` runs

**`progress.html` holds the line saying whether the invocation runs or has ended, the line with `Written at`, and then, in this order, tiles, a bar of the running step's count, a timeline of the steps, two line charts, and links to the two pages of §3.** It holds no table.

**Tiles.** Four tiles, each one `<div class="tile" data-tile="<key>">` holding its text and no other element:

| Key | Text |
| --- | --- |
| `steps` | `Steps finished: <N> of 15`, `<N>` the steps shown `finished` or `stopped at a gate` |
| `step` | `Step: <step> (<state>)`, the step shown `running`, the last in job order where more than one is; where none is, the last step in job order that has an execution, with its state; where none has, `Step: none yet` |
| `elapsed` | ADR-265's `Elapsed: <H:MM:SS>`, unchanged |
| `database` | ADR-265's `Database size: <X.X> MB`, unchanged |

The two strings ADR-265 §1 fixes are each a tile's whole text, so they are on the page once, unbroken by markup.

**A bar of the running step's count**, where a step is shown `running` and its last progress line is a counter's with `<N> of <M>` and `<M>` above zero: one `<progress value="<N>" max="<M>">`, `<N>` and `<M>` the line's two numbers without their thousands separators, beside the line itself. A total of zero, `0 of 0 (100%)` as a counter over nothing logs it, draws no bar: there is nothing to fill. A counter's `so far` line and a statement's line over rows draw no bar: the first has no total, and the second's percentage is an estimate of rows read, not a count.

**A timeline of the steps**: one `<svg class="timeline">` holding, for each of the fifteen steps in the order the job runs them, one `<g class="step" data-step="<step>" data-state="<state>">`, `<step>` as `StepNames` holds it and `<state>` one of ADR-265 §1's five, with the step's name as text and, where the step has a start, a bar from its start to its end, or to the write while it runs, on one axis of time from the job's start to the write, filled with its state's colour and carrying a `<title>` of `<step>: <state>, <H:MM:SS>`, the duration of ADR-265's table. A step not started has no bar. So the running step's bar grows at each write, and a step skipped as already recorded is a mark of the length it took.

**Two line charts over time**, drawn from the samples of §2:

- `<svg class="chart" data-chart="database">`: the database's size at each sample, in millions of bytes;
- `<svg class="chart" data-chart="counts">`: the read count and the write count, as two series, at each sample taken while the step of the latest sample that names a step was running, titled with that step's name. It is not drawn while no sample names a step.

Each series is a line through its samples and, for each sample, one `<circle>` whose `<title>` is the sample's elapsed time as `<H:MM:SS>`, then its value: `<circle …><title>0:12:05: 412.3 MB</title></circle>`. That title is the tooltip a browser shows on hover. The series' latest value is printed as text at the end of its line. The time axis runs from the first sample to the latest, the value axis from zero to the largest value drawn; ticks, gridlines, sizes and spacing are the renderer's.

**The links**: `<a href="progress-steps.html">` and `<a href="progress-samples.html">`, relative, so the three pages may be copied out of the working directory together and still link.

### 2. Where the history comes from

**At every write, `ProgressPage` takes one sample, in memory**: the time elapsed from the job's start to the write, read off the same instant as `Written at`; the database's size, the same reading the `database` tile shows and no second one; and the step shown `running` (the `step` tile's step, or none), with its read count and its write count. The first write, every scheduled write and the last write each take one, under the lock every write is made under (ADR-265 §2). A write that fails after rendering keeps its sample: the reading was made.

**The samples are held for one invocation**: `beforeJob` clears them, so a second invocation in the same process starts at one sample. No query is made and no file but the three pages is written; nothing of the samples outlives the process.

**At most 256 samples are held, and the whole invocation is always drawn.** When a sample makes them 257, it is added and then every sample at an odd position, counted from zero, is dropped, leaving 129: the first sample, the job's start, and the latest, the write just made, are kept, and every stretch of the history is kept at a coarser spacing. At the shipped interval of five seconds the first thinning comes after about 21 minutes; after it the oldest stretches are the coarsest. A window of the last 256 would lose the invocation's start, and the shape of a run of hours is what the chart is read for. 256 points a series keeps each chart's markup, and the page of samples, to tens of kilobytes at any length of invocation.

**`vespera label` takes no samples.** Its page is written twice (ADR-266 §3), and two points are not a chart.

### 3. The tables are pages of their own

**Each write of `vespera run` writes two more pages beside `progress.html`, `progress-steps.html` and `progress-samples.html`**, fixed names held by constants of `ProgressPage` whose names end in `FILE_NAME`, as the README's list of files is checked against (`docs/check-claims.mjs`). Each is one self-contained page through `ReportPage` and holds, in order: its title, `Vespera progress: steps` or `Vespera progress: samples`; the line saying whether the invocation runs or has ended and the line with `Written at`, as `progress.html` has them at the same write; `<a href="progress.html">`; and its table.

| Page | Header row | Rows |
| --- | --- | --- |
| `progress-steps.html` | ADR-265 §1's `Step`, `State`, `Read`, `Written`, `Skipped`, `Duration`, `Progress` | one for each of the fifteen steps in job order, each cell as ADR-265 §1 has it |
| `progress-samples.html` | `Elapsed`, `Database size`, `Step`, `Read`, `Written` | one for each sample held, in the order they were taken: its elapsed time as `H:MM:SS`, its size as `<X.X> MB`, and its step's name, read count and write count, the three empty where no step was running |

**Each is written at every write, from the same reading as `progress.html`, and under ADR-265's rules**: it reloads at the same interval, through the same refresh, while the invocation runs, and the last write renders it with no reload, each through the methods of `ReportPage` that §4 gives these pages. A write renders the three pages and moves each over its place through its own `.part` and `ledger`'s `ReplacingMove`, in the order `progress-steps.html`, `progress-samples.html`, `progress.html`, so a browser that follows a link from a page just written finds the page it names written at the same time or later. The first of the three that throws ends that write: it is the one failure of ADR-265 §4, warned of once, and the next write is attempted on time. ADR-266 §2 is unchanged: once in each invocation, after its first write, `progress.html` is opened, and only it; a first write that failed opens nothing.

**They hold nothing `progress.html` may not**: step names, the five states, counts, durations, times, sizes and the progress lines ADR-265 §3 admits. So ADR-265 §1's rule holds for each: no path, no title, no text of a document, no exception message and no run id, and each may be sent to anyone with `progress.html`.

**`vespera label` writes neither**, and its page links neither. Its table of answers stays on `progress.html` (ADR-266 §3): it is the whole of what the command did, four numbers, and no chart stands for it. Pages of detail an earlier `vespera run` left are left as they are.

### 4. How it is drawn

**`ProgressPage` writes the SVG in Java, into the page, as it writes the rest of it.** Each page stays one self-contained file through `ReportPage` (ADR-130): no stylesheet, script, font or picture outside it, nothing fetched, no library and no template engine (ADR-046). The build reads the pages as text, so what they draw is held by the tests below and not only by a browser.

**Nothing enters the SVG that ADR-265 does not already let onto the page**: step names as `StepNames` holds them, the five state words, numbers, times and durations as `H:MM:SS`, the database's size, and fixed words. Every text goes through `ReportPage.escape`. Every attribute value is quoted with `"` and is a step name, a state word, a number, a page's fixed name or a fixed word, none of which can hold a `"`. The progress line beside the bar is the line ADR-265 §3 admits and the table of steps shows.

**The pages' stylesheet holds its colours as CSS custom properties**, one set for a light scheme and one under `@media (prefers-color-scheme: dark)`, and every colour the charts, tiles and tables use is one of them, so the three pages follow the browser's scheme. `ReportPage` writes it after the shared stylesheet for these pages only; every other page's head is as it is now. The method it gains for that is the implementer's to name.

**The meta refresh of ADR-265 §1 is how the pages are rewritten in the browser while the invocation runs; the last write carries none, and `progress.html` draws the same tiles, timeline and charts**, from the samples up to and including its own, so the page that stays open shows the whole invocation.

### 5. Whether the page is still being rewritten

**While the invocation runs, the line with `Written at` on `progress.html` starts with one `<span class="liveness" data-written="<ms>" data-interval="<ms>">live</span>`**, `data-written` the write's instant in milliseconds since the epoch, read off the job's `Clock`, and `data-interval` `vespera.progress-interval` in milliseconds. **The page carries one `<script>`, whose text is one constant of `ProgressPage`, the same on every write of every invocation**: when the page loads, it reads the two numbers off that mark, and where the browser's clock is more than three intervals past the write, it gives the mark the class `stale` and the text `not rewritten for <S> s`, `<S>` the whole seconds since the write. Three intervals, because a write is made a fixed delay after the last one ended (ADR-265 §2) and a slow disk may hold one back; a page not rewritten for three is one whose process has most likely stopped before its last write.

**No data of the invocation is inside the `<script>`**: it reads two attributes, so nothing written into it needs escaping, and no text the page holds can close it. A browser that runs no script shows `live` and the time written, which say what ADR-265 said they would. The pages of §3 carry no mark and no script: they are reached from `progress.html`.

**The last write carries no `<script>`**, and its mark is `<span class="liveness">ended</span>`: the page that stays open after the invocation is not marked stale for being old.

### 6. The page of `vespera label`

**It holds three tiles, and no timeline, chart, link, liveness mark or script**: `command`, holding ADR-266 §3's `Command: vespera label` or `Command: vespera label --auto`; `elapsed`; and `database`, each as §1 has them. Its table of answers stays below them, as ADR-266 §3 has it. It is written only when the command starts and when it ends, so a mark of liveness would call a long command's page stale while it is still running.

### 7. Text alternatives and colour

**Each `<svg>` has `role="img"` and a `<title>` as its first child**, naming what it draws, and the pages of §3 are their text alternatives: `progress-steps.html` holds every mark of the timeline with its state and its duration, and `progress-samples.html` every point of the two charts with its value. `progress.html` links both, the charts' latest values are printed as text, and each sample's value is in its point's `<title>`.

**Colour is never the only carrier**: each step's state is its mark's `<title>` and its row's cell. Each of the five state colours and each series' colour has a contrast ratio of at least 3:1 against the page's background in both schemes (WCAG 2.2, 1.4.11), and the text colours at least 4.5:1 (1.4.3); the values are the renderer's.

### 8. The modules, and run ids

Only `ProgressPage` and `ReportPage` change, both of `pipeline`, which no stage's version names (ADR-226), and neither gains a rule: the samples decide no verdict, cache key, cluster or text of the deliverable. A class the implementer adds to `pipeline` for the samples or the drawing is listed in `PipelineHoldsOnlyTheRulesOnRecordTest` as holding none. **No run id moves.** No property is added: the bound of §2 and the three intervals of §5 are constants of `ProgressPage`, as the operator asked for no setting.

### 9. What the operator is told

The README's list of the working directory's files names `progress-steps.html` and `progress-samples.html` beside `progress.html`, in the change that adds their constants. Its sentence on `progress.html` says that it shows how many steps have finished, the step running and how far it has got, a timeline of the steps and charts of the database's size and the running step's counts over time, that the two pages beside it hold the same as tables, and that a page no longer rewritten says so.

## Alternatives refused

- **A script that draws the charts from a JSON object in the page**, as the prototype did. The build would hold the JSON and never the drawing, which only a browser would run; and the object would be a second copy of the page's data inside a `<script>`, where HTML escaping does not apply and `</`, U+2028 and U+2029 would each need an escape of their own. Written in Java, every text goes through the one escape table, and the drawing is text a test reads.
- **A charting library**, inline or fetched from a CDN. Inline, it is a dependency with no decision behind it (ADR-046); fetched, the page needs the network and is no longer self-contained (ADR-130).
- **A crosshair that follows the pointer, with a tooltip of every series at that time.** It needs a script over the samples, which is the first alternative again. A `<title>` gives each point a tooltip with no script, and gives it to a screen reader too.
- **The tables kept below the charts, or folded into a `<details>`.** The operator asked for them in files of their own; on one page the table of fifteen rows pushes the charts' neighbours down, and a table of 256 samples would be most of the page.
- **One page of detail holding both tables.** The two are read for different things, the steps for where the job is and the samples as the charts' alternative, and a link names the one it leads to.
- **The label command's table of answers on a page of its own.** It is all the command has to show; `progress.html` would be three tiles and a link.
- **The history read from the database at each write.** ADR-265 §3 and its alternatives refused stand: a query every five seconds takes a connection from the steps' pool.
- **A fixed window of the latest samples, or no bound.** A window loses the invocation's start; no bound grows the pages with the invocation.
- **A mark of liveness drawn with a CSS animation and no script.** The meta refresh reloads the same file, and the animation starts again on every reload, so a page whose process has stopped would look live for ever.
- **A setting for the bound, the three intervals or the charts themselves.** The operator asked for none; the defaults decide.
- **Rates per minute on the tiles**, as the prototype had. They are read off two samples and change with the spacing thinning gives them; the chart's slope says the same.

## Consequences

- An operator who opens `progress.html` sees how many steps have finished, which step runs and how far through its count it is, a bar for each step on one axis of time, and how the database's size and the running step's counts grew; the last write keeps them. The table of steps, and every sample, are one link away.
- A page whose process stopped before its last write says so, in a browser that runs scripts, within three intervals of its last write.
- Each write of `vespera run` moves three pages into place instead of one, and renders a few hundred more elements.
- The guard's list of pages an agent may read (ADR-261) names none of the three.
- No schema, cache key, verdict kind, property or run id changes.

## Tests

| Method | What it holds |
| --- | --- |
| `pipeline.ProgressPageChartsTest.theTimelineHasOneMarkPerStepInJobOrderWithItsState` | over a job whose census finished and whose byte-level reduction failed, the last write's timeline has one mark per step, in the order of the rows of the table of steps, each with its state: the census `finished` with a bar, byte-level reduction `failed`, and the thirteen others `not started` with no bar; the timeline is an image with a title |
| `pipeline.ProgressPageChartsTest.theTilesSayTheStepsFinishedTheStepRunningTheTimeAndTheSize` | with the census finished and byte-level reduction running, the first write's tiles are, in order, `Steps finished: 1 of 15`, `Step: byte-level-reduction (running)`, the elapsed time and the database's size |
| `pipeline.ProgressPageChartsTest.eachWriteAddsASampleAndANewInvocationStartsAgain` | the first write's database chart and page of samples have one sample and the last write's two, and a second invocation through the same page starts again at one |
| `pipeline.ProgressPageChartsTest.theSamplesAreThinnedToAtMost256KeepingTheFirstAndTheLatest` | after more than 400 writes, a second apart on the page's clock, the database chart has more than 128 points and at most 256, the first at `0:00:00` and the last at the time the page says has elapsed, in rising order, and the page of samples a row for each point, in the same order |
| `pipeline.ProgressPageChartsTest.oneThinningLeavesTheSamplesAtEvenPositions` | 257 writes a second apart leave exactly the 129 samples at even positions, `0:00:00` to `0:04:16` every two seconds, and the next write adds one more, at `0:04:17` |
| `pipeline.ProgressPageChartsTest.theChartOfCountsIsOfTheLatestStepASampleNames` | the first write, before any step ran, draws no chart of counts; a write while the census runs draws one titled with the census; the last write, whose sample names extraction, draws extraction's |
| `pipeline.ProgressPageChartsTest.aCountOfATotalOfNoneDrawsNoBar` | a step running whose last progress line is `… 0 of 0 (100%)` draws no `<progress>`, though the table of steps shows the line |
| `pipeline.ProgressPageChartsTest.aWriteThatFailsPartWayStopsBeforeTheProgressPageAndIsWarnedOfOnce` | with a folder where `progress-samples.html` goes, `progress-steps.html` is written by both writes, `progress.html` by neither, one warning is logged, and no `.part` is left |
| `pipeline.ProgressPageChartsTest.aRunningStepsCountOfATotalIsDrawnAsABar` | a step running whose last progress line is `… 3 of 7 (42%)` is drawn with `<progress value="3" max="7">` |
| `pipeline.ProgressPageChartsTest.theScriptIsOneFixedTextOnEveryPage` | the first writes of two invocations, at different times over different steps, each carry one `<script>`, of the same text, and a liveness mark whose `data-written` is the write's instant; neither page of detail carries a script |
| `pipeline.ProgressPageChartsTest.theLastWriteKeepsTheChartsAndCarriesNoReloadAndNoScript` | the last write of `progress.html` holds the tiles, the timeline and the database chart, carries no reload and no `<script>`, and its liveness mark says `ended` |
| `pipeline.ProgressPageChartsTest.theTablesAreOnTwoPagesOfTheirOwnThatTheProgressPageLinks` | `progress.html` holds no table and links `progress-steps.html` and `progress-samples.html`, each of which holds its header row and links back to `progress.html` |
| `pipeline.ProgressPageChartsTest.eachPageOfDetailReloadsWhileTheJobRunsAndNotAfterItEnds` | after the first write both pages of detail reload at the page's interval and say the invocation is running; after the last, neither reloads and both say how it ended |
| `pipeline.ProgressPageChartsTest.theLabelPageHasTilesAndNoTimelineChartLinkOrScript` | both writes of `vespera label`'s page hold the `command`, `elapsed` and `database` tiles in that order, and no timeline, chart, link to a page of detail, liveness mark or `<script>`, and no page of detail is written |

ADR-265's tests that read the table now read it off `progress-steps.html`: `ProgressPageInvocationTest`'s `whileAStepRunsThePageNamesItAndIsRewritten`, `theLastWriteSaysTheInvocationEndedAndStopsTheReload`, `aStepThatEndsAtAShutGateIsShownStoppedThere`, `aFailedStepIsShownFailedAndTheStepsAfterItNotStarted` and `thePageListsExactlyTheJobsStepsInTheJobsOrder`, and `ProgressPageListenerTest`'s two of the last write. `ProgressPageInvocationTest.thePageNamesNoPathAndNoTextOfADocument` reads the whole of the last write of all three pages, so it holds the tiles, the timeline, the charts, their titles and both tables to ADR-265's rule: no marked name or text, root, seed folder or working directory is on any of them. No progress line can carry a `</script>`, as ADR-265 §3's two shapes admit no `<`; `theScriptIsOneFixedTextOnEveryPage` holds that nothing of an invocation reaches the script at all.

## What this does not decide

- **The look beyond what §1, §3 and §5 fix**: sizes, spacing, fonts, ticks, gridlines, the exact colours, the words of the links, and the words around the strings fixed here, are the renderer's.
- **Whether an agent may read the pages**, which stays a record of its own (ADR-265).
- **The census's progress on the page.** Its counter still logs through `corpus` (ADR-265), so its tile, mark and samples show its state, its duration and the database's size, and no count.
