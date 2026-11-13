# ADR-382 — Each stage has a chart of its own, in two rows, frozen once it finishes and kept under its run beside the report

- **Date**: 2026-11-12
- **Status**: accepted on 2026-11-12.
- **Decided**: by the operator on 2026-11-12, on [#794](https://github.com/algernon28/vespera/issues/794)'s Part B, with no prototype: in place of the cockpit's three charts, the database's size, the counts of the running step and the verdicts by kind, one chart for each stage, in two rows, stages 0 to 3 above and 4 to 6b below (§1); each stage's chart plots its own counts over time, and a stage that judges plots its verdicts by kind as well (§2); the database's size leaves the charts and is a small line of the header (§3); once a stage finishes, its chart is frozen for the same run, its lines dimmed, *finished at hh:mm* in its title and no latest sample marked (§6); the same run is a run continued, after a restart of Vespera or of the control panel too, so a run continued keeps its stages' frozen charts, kept in a file for each stage beside the report in the working directory, keyed by that stage's run, since the database holds no sample over time (§4, §7); a new run of a stage starts its chart afresh, and a run continued continues it (§7); below a least width the charts wrap or scroll sideways, one of the two (§8). Which steps each stage's chart draws, its lines and their values, its time axis, the file's shape and who writes it, how a frozen chart is dimmed, the sixth series colour, the least width, and the tests are decided here.
- **Amends**: [ADR-344](0344-the-control-panels-window-is-one-cockpit-run-and-stop-the-steps-as-bars-beside-the-output-the-progress-pages-three-charts-the-profile-and-the-folders-to-include-starting-from-the-real-configuration.md) §1, the header's row, *"`where`, `child`"*, which gains `database-size` (§3), and the charts' row, *"`database-chart`, `counts-chart`, `verdicts-chart` | the progress page's three charts, drawn from §2's file"*, which are §1's eight charts drawn from §4's files; and §2, *"The series are what the page draws … the window draws them and decides none"*: the series of a stage's chart are the jar's, written in its own file (§4, §5). It carries a note at its head.
- **Amends**: [ADR-362](0362-an-invocations-report-is-one-json-file-drawn-as-static-html-through-a-mustache-template-and-no-command-opens-anything.md) §8, *"the steps' bars and the three charts, as ADR-344 §1 and §2 draw them, from `steps` and `charts`"*: the steps' bars from `steps`, and the stages' charts from §4's files, read by the window at each reading of the report and whenever it reads its working directory's files. `report.json`'s `charts` and `database`, and `report.html`'s three charts, stand as §3 and §6 of that record have them. It carries a note at its head.
- **Amends**: [ADR-365](0365-the-cockpits-charts-carry-a-legend-and-a-value-on-each-sample-and-a-running-step-or-leg-says-its-whole-percent.md) §2, *"a chart of the jar's draws five lines at most"* and *"The legend is drawn at the window's opening size with the most lines a chart draws"*: a stage's chart draws six at most, a legend of six held drawn at the least width of §8; and §3, *"The latest sample of each line, its last value, carries one visible marker"*: not on a frozen chart (§6). It carries a note at its head.
- **Amends**: [ADR-381](0381-every-view-inside-the-control-panels-window-follows-its-light-and-dark-switch-at-once-its-popups-included.md) §2, §4 and §5, the five series tokens: each theme rule gains a sixth, `-vespera-series-6`, and `CHART_COLOR_6` is set from it (§9); its §3's row of the n-th line, whose latest-sample rules name `-vespera-series-1` to `-6`; its Known limits, *"A chart of six lines or more"*, now of seven or more; and its What this does not decide, *"A chart for each stage"*, decided here. It carries a note at its head.
- **Keeps**: ADR-365 §1, a legend for two lines or more and none for one, and §3, a sample's hit area and its value alone; [ADR-371](0371-a-size-of-memory-is-written-in-powers-of-1024-and-only-a-count-axis-of-the-cockpit-is-shortened-to-k-m-b-and-t.md) §1, a size in powers of 1,024, §3 and §4, the ticks of a count and of time; ADR-381 §1 to §3, every colour a token of the two theme rules, and §5's ratios of every other token; [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3, the control panel links no class of the jar; [ADR-265](0265-vespera-run-rewrites-a-progress-page-in-the-working-directory-while-it-runs.md) §4, a reading that fails never fails the invocation; [ADR-268](0268-the-progress-page-shows-what-the-stage-that-removes-occurrences-has-removed-and-draws-to-the-data-viz-method.md) §2, the sampler reads the verdict counts held in memory and makes no query at a reading; [ADR-269](0269-the-progress-page-is-four-tabs-draws-a-hover-over-each-chart-and-lists-the-runs-the-working-directory-holds.md) §5, the runs read twice in an invocation, and no more; [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md), no stage's version names `pipeline`; ADR-344 §1's other parts and ADR-361's legs.
- **Settles**: [#794](https://github.com/algernon28/vespera/issues/794)'s Part B. Parts A, C and D are ADR-370, ADR-380 and ADR-381, and ADR-379.

## Context

**What the cockpit draws.** `ProgressCharts` stands three `LineChart`s side by side beneath the steps and the output, `database-chart`, `counts-chart` and `verdicts-chart`, each drawn whole again at each reading of `report.json` from its `charts` (ADR-344 §1, ADR-362 §8): the database file and its write-ahead log's size at each sample; the counter of the latest step a sample names, its last label alone, or that step's read and written counts where it said no counter; and the verdicts the latest step that removes occurrences recorded in this invocation, by kind (ADR-268). Each line's samples are held by `JobSampler` in the invocation's memory, 256 at most, thinned to every other one past that (ADR-267 §2), and cleared as the next job begins. A step that finished in an earlier invocation is gone from every chart; and the charts are empty until the window watches an invocation, since it draws them only from the report of the invocation it watches.

**What the database holds of time.** Nothing. Of `schema.sql`'s forty tables, `walk`, `run`, `finished_step` and `verdict`, the four that say what a walk, a run and its steps did, carry no time; no table holds a sample, a count at an instant or the instant a step finished. The samples live in the sampler's memory alone, and `logs/report-<invocation>.json` keeps, of each invocation, only the three charts it drew, keyed by the invocation and not by any run. The database cannot draw a stage's chart again, nor say when the stage finished; a chart kept past its invocation has to be written as it is drawn.

**The job's steps, by stage.** The job runs sixteen steps (`InvocationReport.STEPS_IN_JOB_ORDER`), and each step's progress label opens with its stage's number (`StageProgress.LABEL_SHAPE`): the census, `Stage 0`; byte-level reduction, `Stage 1`; extraction, `Stage 2`; content census, `Stage 3`; the redundancy signatures and content redundancy, `Stage 4a` and `Stage 4`; the seed extraction, the seed-corpus comparison, the embedding scoring, the translations, the relevance scoring, the relevance floor, the clustering and the relevance report, `Stage 5a` to `Stage 5f` and `Stage 5`; the arrangement, `Stage 6a`; the generation, `Stage 6b`. The verdicts each stage records (`docs/architecture.md` §1.2): stage 1 five kinds, `broken`, `duplicate-of`, `superseded-by`, `out-of-scope` and `code`; stage 2 two, `extraction-failed` and `degenerate-output`; stage 4 one, `redundant-with`; stage 5 two, `translation-of` and `below-threshold`; stages 0, 3, 6a and 6b none.

**The runs a stage's steps work under.** The census works under a walk, not a run. Each other stage's steps record their completion under a run of one of eight run stages (`InvocationReport.STEPS_OF_A_STAGE`): stage 5's under two, `seed-measurement` for its first two steps and `embedding-scoring` for the scoring, the translations, the relevance scoring and the clustering, its floor and report recording none (ADR-118). A run's id is derived from its version, its configuration, its walk and its upstream runs (`RunId.of`), so a new walk makes every run new, and a new run of a stage makes every run after it new. The job hands the sampler the runs it minted or continued (`InvocationRuns`), and the runs reading at the invocation's start counts each run's verdicts by kind (ADR-269 §5). `Verdicts` keeps in memory the verdicts recorded by kind, and *"a delete subtracts nothing"*: a step that does its unfinished work again from the start, as byte-level reduction, content redundancy and the translations do, and the relevance floor on every invocation, discards its run's verdicts first (ADR-116, ADR-118).

**The room.** At the window's opening size, 1,500 by 960, the charts' row is 1,040 px wide beside the sidebar, measured on the Headless platform; four charts side by side with the stylesheet's 8 px between them stand 254 px wide each. A `LineChart` of the longest title §6 gives, *1 · Byte-level reduction, finished at 14:32*, and a legend of six names, drawn 340 px high, measured there, its title let wrap in the first three rows:

| Width | Title | Legend | Plot |
| --- | --- | --- | --- |
| 240 px | on two lines | 133 px high | 128 × 87 px |
| 250 px | on two lines | 70 px high, three rows | 138 × 150 px |
| 254 px | on two lines | 70 px high | 142 × 150 px |
| 320 px, the title not let wrap | on one line, cut short at *finished a…* | 70 px high | 208 × 173 px |

At 240 px wide and 300 high JavaFX leaves the legend out. A title that does not wrap is cut short at every width measured, 200 to 320 px, *finished at* lost with it first.

**The series colours.** ADR-381 names five series tokens, one for each line a chart drew at most. A chart of stage 1 draws six: its counts and five kinds.

## Decision

### 1. One chart for each stage, in two rows

**The charts' row of the cockpit holds eight `LineChart`s, one for each stage**, `stage-chart-0`, `stage-chart-1`, `stage-chart-2`, `stage-chart-3`, `stage-chart-4`, `stage-chart-5`, `stage-chart-6a` and `stage-chart-6b`, **in two rows**: `stage-charts-row-1` holding stages 0 to 3 and `stage-charts-row-2` stages 4 to 6b, each in that order, the first above the second, both inside `stage-charts` (§8). Each carries the style class `progress-chart`, is 340 px high as ADR-365 §2 has it, and takes an equal share of its row's width. `database-chart`, `counts-chart` and `verdicts-chart` are gone.

**Each chart's title is its stage's number and name**, as `docs/architecture.md` §1.2 names them, the bracketed words left out: *0 · Census*, *1 · Byte-level reduction*, *2 · Extraction*, *3 · Content census*, *4 · Content redundancy*, *5 · Relevance*, *6a · Arrangement* and *6b · Generation*, with §6's words after it once frozen. **The title wraps** onto a second line where the chart is too narrow for it. The window holds the eight as text of its own, since it links no class of the jar (ADR-285 §3).

### 2. What a stage's chart plots

**Each stage's chart draws the steps of its stage**, by the number their progress labels open with: stage 0 the census; 1 byte-level reduction; 2 extraction; 3 content census; 4 the redundancy signatures and content redundancy; 5 the seed extraction, the seed-corpus comparison, the embedding scoring, the translations, the relevance scoring, the relevance floor, the clustering and the relevance report; 6a the arrangement; 6b the generation. Every step is in one chart.

**Its lines, in this order**: first `done`, its stage's counts over time; then, for a stage that judges, one line for each kind of verdict it records, named as `CONTEXT.md` writes the kind, in `VerdictKind`'s order: for stage 1 `broken`, `duplicate-of`, `superseded-by`, `out-of-scope` and `code`; for 2 `extraction-failed` and `degenerate-output`; for 4 `redundant-with`; for 5 `translation-of` and `below-threshold`. A kind's line is drawn from the stage's first sample, at 0 where none is recorded. **A chart draws six lines at most**, stage 1's.

**The value axis is titled *Done and verdicts (count)* on a stage that judges, 1, 2, 4 and 5, and *Done (count)* on the others**, its ticks shortened as ADR-371 §3 has a count's. A chart of one line, stages 0, 3, 6a and 6b, carries no legend, its value axis's title naming its line; the others carry one (ADR-365 §1). **The time axis is titled *Elapsed*** and ticked in the unit its spacing fits (ADR-371 §4); it is the stage's own time, §5.

### 3. The database's size is a line of the header

**The header's third line, `database-size`, under `where` and `child`, reads *Database size: `<size>`***, the sum of `vespera.db` and `vespera.db-wal` in the working directory the window names, each read by a file system call and never opened, written as ADR-371 §1 writes a size, `4.00 KB` for 4,096 bytes; and *Database size: none yet* where neither file is there. It is read as the window opens and again at each tick of the window's clock, every second, whether or not an invocation runs, and is copyable text as every text of the window is (ADR-364). `report.json`'s `database` and its `charts`, and `report.html`'s three charts, are unchanged.

### 4. A stage's chart is kept in a file of its own, written by the jar

**`vespera run`'s job writes, at the top of the working directory beside `report.json`, one file for each stage whose chart it has drawn, `chart-stage-<stage>.json`**: `chart-stage-0.json`, `chart-stage-1.json` and so on to `chart-stage-6b.json`. `vespera label` and `vespera publish` write none. The file is written whole, to a temporary file moved over it as `report.json` is, at each reading at which its chart takes a sample or is started afresh (§5, §7), and only then; a file that cannot be written is warned of once in the invocation, and fails nothing (ADR-265 §4). Nothing else writes it, and nothing deletes it. It holds, in JSON:

```json
{
  "format": 1,
  "stage": "2",
  "key": { "extraction": "<run id>" },
  "finishedAt": "2026-11-12T13:32:05Z",
  "lines": [
    { "name": "done", "seconds": [0, 5, 10], "values": [120, 260, 400] },
    { "name": "extraction-failed", "seconds": [0, 5, 10, 300], "values": [0, 3, 4, 4] },
    { "name": "degenerate-output", "seconds": [0, 5, 10, 300], "values": [0, 1, 1, 2] }
  ]
}
```

- `format`: 1, raised by a change that breaks a reader; a reader draws a file of another format as no file.
- `stage`: the stage, `0` to `6b`.
- `key`: what the chart is kept under (§7): for the census `{"walk": "<walk id>"}`; for stage 5 `{"seed-measurement": "<run id>", "embedding-scoring": "<run id>"}`; for each other stage its one run, under the run stage it is minted as, `byte-level-reduction`, `extraction`, `content-census`, `content-redundancy`, `arrangement` or `generation`. A run or a walk the job has not reached yet is not named.
- `finishedAt`: the instant the stage finished, to the second in UTC, once its chart is frozen (§6); `null` until then.
- `lines`: §2's lines in §2's order, each with its own `seconds` and `values`, of one length, oldest first.

**The window reads the eight files** as it reads its working directory's files, as it opens and whenever another working directory is named, and again at each reading of the report (ADR-362 §8), through a class of its own built on its `JsonReader`. A chart whose file is not there, cannot be read, is not that JSON or is of another `format` is drawn empty under its title, and nothing is said. The window draws what each file holds and decides none of it: the series, the freezing and the key are the jar's.

### 5. The samples

**A chart not frozen takes a sample** at each reading of the job (ADR-267 §2) at which a step of its stage is running; at the reading at which it freezes (§6); and at the invocation's last reading, where a step of its stage ran in this invocation. A frozen chart takes none (§6).

- **`done`** at a sample is the count done of the counter the latest of its stage's steps to have said one in this invocation said last, `N` of its progress line *`N of T (P%)`* or *`N so far`*, whatever the counter's label; for the census, the entries walked that the census's own line, *`Stage 0 (census): N entries walked so far, …, under walk W`*, says of the corpus root's walk, which the sampler admits beside the counters' lines. Where none of its steps has said one in this invocation, `done` has no point at that sample. A step's counters each count from 0, so `done` climbs once for each.
- **A kind's value** at a sample is the count of that kind standing under the stage's runs: what the runs reading at the invocation's start counted of it under those runs (ADR-269 §5), 0 for a run that reading does not list, plus the verdicts of that kind recorded in this invocation, less those of it discarded. **`Verdicts` counts in memory the verdicts it discards, by kind**, as it counts those it records (ADR-268 §2), each discard counting the rows it deletes, so that a step that discards its run's verdicts and records them again is counted once. Where the runs reading at the start failed, a kind's count before this invocation is the last value its line holds in the file under the same key, else 0.
- **`seconds`** at a sample is the stage's own time: in a chart begun in this invocation, the seconds since its first sample in it; in a chart continued (§7), the last second the file holds, plus the invocation's `vespera.progress-interval`, plus the seconds since its first sample in this invocation. Time between two invocations is not drawn, and the axis of every chart starts at 0.
- **A chart keeps 256 samples at most**: one more thins it to every other one, oldest first, as ADR-267 §2 thins the sampler's, each line keeping its points at the samples kept.

### 6. A frozen chart

**A chart freezes at the first reading at which every step of its stage stands `finished`**, after it takes that reading's sample: its `finishedAt` is the latest end of its stage's steps in this invocation. A stage stopped at a gate, failed or stopped has not finished, and its chart stays as it is, to be continued (§7).

**A frozen chart is never written again under its key**: it takes no sample and its time does not move, whatever its stage's steps do in a later invocation under the same runs, a finished step passing through, the relevance floor and the relevance report deciding again on every invocation (ADR-118), or the clustering forming its clusters again (ADR-230). Only a new key starts it afresh (§7).

**The window draws a chart frozen where its file's `finishedAt` is given**:

- **its title** is its stage's, then *, finished at `HH:mm`*, the instant in the machine's zone, on the 24-hour clock: *2 · Extraction, finished at 14:32*;
- **no sample carries `latest-sample`**: every sample keeps its hit area 24 px across and its value (ADR-365 §3), and none is marked;
- **it carries the style class `frozen`**, and the stylesheet **dims its lines and its legend's swatches**: each line and swatch at opacity 0.85, and each line 1.5 px wide where Modena draws it 3 px. 0.85 is the least opacity at which every series colour of both themes still stands at 3 : 1 against the plot (§9);
- its lines, values and legend are drawn as a chart not frozen draws them.

### 7. The same run, and a new one

**A stage's chart is kept under its key**, §4's: the corpus root's walk the census walked or continued, which `CensusTasklet` hands the job beside its runs and which the census's own lines name before it; and the run or the two runs the invocation minted or continued for the stage, as `InvocationRuns` holds them.

- **A run continued, in a later invocation after a restart of Vespera or of the control panel too, continues its stage's chart**: a chart not frozen takes its samples after the ones the file holds (§5), and a frozen one stays as it is (§6). The window draws the file as it opens, whether or not an invocation runs, so a restart of the control panel draws every chart the working directory keeps.
- **A new key starts the chart afresh**: at the first reading at which the invocation has recorded a run or a walk of the stage that the file's `key` names otherwise, the file is written over with the new key, no `finishedAt` and no point but that reading's sample; **and the file the working directory holds of every stage after it is written over with no key, no `finishedAt` and no line**, since a later stage's run names the run before it upstream and is new too. A stage whose run the invocation has not reached keeps its file until it does.
- **A value put back returns an earlier run** (ADR-156) whose chart a later run replaced: its stage's steps, finished, pass through, and its chart, begun afresh, freezes at that reading with that invocation's end of its steps for its time, holding one sample of its kinds and no `done`.

### 8. Below their least width, the rows scroll sideways

**Each chart is at least 250 px wide**, so that its title stands on two lines, a legend of six names in three rows and its plot 150 px high (Context). **The two rows stand in `stage-charts`, a `ScrollPane` that fits them to its width and scrolls sideways where it is narrower than their least width**, 4 × 250 px and three spaces of 8 px, 1,024 px: the rows keep their four charts each, side by side, and scroll together. At the window's opening size the charts' row is 1,040 px wide, so every chart stands whole on screen and no scroll bar shows. **The rows do not wrap.**

### 9. A sixth series colour

**Each theme rule gains `-vespera-series-6`**, after `-vespera-series-5`, and sets `CHART_COLOR_6` from it; the latest-sample rule of `default-color5` names it, as the five before name theirs:

| Token | Light | Dark |
| --- | --- | --- |
| `-vespera-series-6` | `#8f5bd6` | `#9b74e6` |

A violet, the hue none of the five holds, standing as the five do: the light at 4.53 : 1 on the surface and 4.12 : 1 on the ground; the dark at 4.44 : 1 on the surface, 5.00 : 1 on the ground and 4.66 : 1 on a control's inside. **The six series tokens stand at 3 : 1 or more against the surface and the ground in both themes**, as ADR-381 §5 holds the five.

**Dimmed to 0.85**, each series colour drawn over the plot's surface stands at, worked out from the tokens as WCAG's relative luminance has it, the colour blended with the surface at that opacity:

| Theme | 1 | 2 | 3 | 4 | 5 | 6 |
| --- | --- | --- | --- | --- | --- | --- |
| light, on `#ffffff` | 3.45 | 3.51 | 3.44 | **3.32** | 3.46 | 3.50 |
| dark, on `#25232f` | 3.45 | 3.23 | 3.65 | 4.02 | **3.19** | 3.60 |

At 0.80 the dark fifth stands at 2.98 : 1 and the light fourth at 3.06 : 1; at 0.75, five of the twelve are under 3 : 1.

### 10. The modules, and run ids

**In `ui`**: `ProgressCharts`, `Cockpit`, a reader of §4's file, and `control-panel.css`. **In `pipeline`**: `JobSampler`, which builds and writes the stages' charts, or a class of `pipeline` it hands them to, listed in `PipelineHoldsOnlyTheRulesOnRecordTest` as holding no rule; `InvocationRuns` and `CensusTasklet`, the census's walk handed to the job. **In `ledger`**: `Verdicts`, its count of the verdicts discarded. No stage's version names `pipeline` (ADR-222), `ledger` or the control panel; `corpus`, whose census line the sampler reads, is unchanged. **No run id moves.** `report.json`, its `format` and `report.html` are unchanged.

## Alternatives refused

- **The charts rebuilt from the database.** It holds no sample and no time (Context).
- **The charts kept by the window, from the reports it reads.** It reads a report only while it watches an invocation, so an invocation run without it, by `vespera run` in a terminal, would leave nothing; and it writes into the working directory only while no invocation it started runs (ADR-295).
- **The stages' charts carried in `report.json`.** A frozen chart outlives the invocation that drew it, and `report.json` is the account of one invocation; the window draws the files before any invocation runs.
- **A line for each step, or each counter's label.** Stage 5 has eight steps and stage 1 four counters; with its kinds, a chart would draw up to ten lines, past the series colours and the legend's room at the least width.
- **A stage's progress in percent beside its verdicts.** Percent and counts on one value axis read on no scale.
- **A frozen chart continued by the relevance floor and report deciding again.** They decide again on every invocation, so stage 5's chart would never stay frozen; the operator asked for a chart frozen for the same run.
- **A kind's line counted from the invocation's start alone.** A run continued would start its kinds again at 0 halfway along its chart.
- **The runs' verdicts counted by a query at each reading.** ADR-268 §2 and ADR-269 §5 keep the readings off the database; the runs reading at the start and the discards counted in memory give the same count.
- **The lines dimmed below 0.85.** A frozen chart is still read; under 0.82 a series stands under 3 : 1 against the plot (§9).
- **The rows wrapping below their least width.** They would make three rows or four, the stages no longer reading 0 to 3 above 4 to 6b, and push the tabs a chart's height lower in a window that already scrolls.
- **The database's size from `report.json`.** The report is read only while an invocation is watched; the header reads the two files at every tick.
- **A file holding every run's chart of a stage.** One file of the stage's last run is what the window draws; a run put back is the case §7 names.

## Consequences

- The operator sees, at once and after any restart, the chart of every stage the working directory's runs have reached, the finished ones dimmed with the time they finished, and the database's size in the header.
- A stage stopped partway and continued later draws one chart across its invocations, its time axis counting the time it worked.
- A threshold retuned starts its stage's chart afresh, and empties the charts after it until the invocation reaches them.
- The working directory holds up to eight more small files, of 256 samples a line at most.
- `Verdicts` counts the verdicts it discards, in memory, beside those it records.

## Known limits

- **A stage's `done` climbs once for each counter**, from 0, so a stage of several counters draws a saw; the value under the pointer and the steps' bars say which.
- **A step that says no counter draws no `done`**, the census of fewer than 1,000 entries among them; its kinds are drawn.
- **A frozen stage 5 does not draw what its floor and report decide in a later invocation** under the same runs; the steps' bars, `report.json` and the relevance report do.
- **A run put back** draws one sample and the time of the invocation that found it finished, not the time it first finished (§7).
- **A stage's chart is drawn in its file's place while a later invocation has not reached it**: a chart the invocation will start afresh shows its earlier run's until then, unless a stage before it began afresh (§7).
- **The dimmed ratios are those of the tokens**, blended as WCAG's luminance is worked out, not of the pixels JavaFX draws.

## What this does not decide

- **`report.html`'s charts**, ADR-362 §6's three, unchanged.
- **A chart's colours by stage or by kind**: a line takes the colour of its place among its chart's lines (ADR-365 §2), so a kind's colour differs between charts.
- **The window's other sizes and spaces**, the stylesheet's under ADR-342 §3.

## Tests

| Method | What it holds |
| --- | --- |
| `ui.EachStageHasAChartTest.oneChartForEachStageInTwoRowsScrollingSidewaysAndTheDatabaseSizeInTheHeader` | §1, §3, §8: the window drawn 1,500 px wide over a working directory whose database is 3,072 bytes and its log 1,024: `stage-charts-row-1` holds `stage-chart-0` to `-3` and `stage-charts-row-2` `stage-chart-4` to `-6b`, in order, each a `LineChart`, the first row above the second, both in `stage-charts`; none of `database-chart`, `counts-chart` and `verdicts-chart`; `database-size` reads *Database size: 4.00 KB*, after `where` and `child`; every chart at least 250 px wide and no sideways scroll bar; drawn 1,100 px wide, every chart still at least 250 px wide, four to a row, and `stage-charts`' sideways scroll bar showing |
| `ui.EachStageHasAChartTest.aFinishedStagesChartIsDrawnFrozenFromItsFileAsTheWindowOpens` | §4, §6, §7: a working directory keeping stage 1's chart finished and stage 2's not, the window opened over it with no invocation: stage 1's title ends *, finished at `HH:mm`* in the machine's zone, it carries `frozen`, its six lines are the file's, in its order, at its seconds and values, none of its samples carries `latest-sample`, and each line and swatch stands at opacity 0.85, each line 1.5 px wide; stage 2's title is its name alone, its lines' last samples carry `latest-sample`, at opacity 1; stage 3's, with no file, draws no line under its title |
| `pipeline.EachStagesChartIsKeptUnderItsRunTest.anInvocationUnderTheSameRunContinuesTheChartAndOneFinishedStaysFrozen` | §4 to §7: an invocation whose extraction, under one run, says 2 of 4 and records one `extraction-failed` and stops writes `chart-stage-2.json` keyed by that run, not finished, its three lines at second 0; a second invocation, a sampler of its own, the runs reading counting that verdict, whose extraction says 4 of 4, records one more of each kind and finishes, continues it at the interval's second, `done` 2 then 4, `extraction-failed` 1 then 2, `degenerate-output` 0 then 1, frozen at the extraction's end; a third, under the same run, the extraction passing through, leaves the file as it was |
| `pipeline.EachStagesChartIsKeptUnderItsRunTest.aNewRunStartsTheStagesChartAfreshAndEmptiesTheChartsAfterIt` | §7: a working directory keeping stage 1's, stage 2's and stage 3's charts, frozen under their runs; an invocation whose extraction runs under another run writes stage 2's afresh under it, its one sample this invocation's and no `finishedAt`, writes stage 3's over with no key and no line, and leaves stage 1's as it was |

`ui.TheControlPanelsThemeTest.everyColourOfTheWindowIsAThemeTokenAndStandsAtItsContrastInBothThemes` holds six series tokens in each theme, each at 3 : 1 or more against the surface and the ground (§9), in place of five; `ui.TheControlPanelsThemeTest.everyViewOfTheWindowAndAPopupShowingOverItTakeTheThemeAsTheSwitchFlips` draws `stage-chart-1` of six lines from its file, in place of `verdicts-chart` of five handed a report, and reads its six lines in their six series colours. `ui.TheCockpitsValueTicksTest` holds ADR-371's ticks, of `ProgressCharts`, unchanged. ADR-344's `controlpanel.TheControlPanelsWindowTest.theProgressPagesThreeChartsAreInTheWindow` is retired (ADR-368), and `ui.EachStageHasAChartTest.oneChartForEachStageInTwoRowsScrollingSidewaysAndTheDatabaseSizeInTheHeader` holds the charts' row in its place.

## What the implementation owes

- `ProgressCharts`: the eight charts of §1 in their two rows inside `stage-charts`, §2's axes, §6's look, drawn from §4's files; `tickText`, `timeTick` and `shortened` kept as they are. `Cockpit`: `database-size` in the header, read at each tick (§3); the stages' files read as §4 says, at opening, on another working directory and at each reading of the report; the charts no longer drawn from `report.json`'s `charts`, which `InvocationReportFile` keeps reading.
- `control-panel.css`: `-vespera-series-6` in both theme rules and `CHART_COLOR_6` (§9); the latest-sample rule of `default-color5`; the title's wrapping, the charts' least width, and `.progress-chart.frozen`'s lines and swatches at opacity 0.85 and its lines 1.5 px wide (§6, §8), every colour through the tokens (ADR-381 §2).
- `JobSampler` and, where it hands them on, a class of `pipeline`: §4's files, §5's samples, §6's freezing and §7's keys, its readings still making no query; `CensusTasklet` handing the corpus root's walk to the job through `InvocationRuns`; the census's line admitted beside the counters'. `Verdicts`: the discards counted by kind, read as `recorded()` is.
- The README's table of what the working directory holds gains `chart-stage-<stage>.json`, and its paragraph on what the window shows says that each stage has a chart, frozen and dimmed with the time it finished once the stage has, kept across restarts for the same run, and that the database's size is in the header. `CONTEXT.md`'s entry for the control panel, where it names the charts, says the same.
- The tests above passing.
