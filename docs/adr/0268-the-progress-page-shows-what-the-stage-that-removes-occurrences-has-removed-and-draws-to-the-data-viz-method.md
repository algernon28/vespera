# ADR-268 — The progress page shows what the stage that removes occurrences has removed, and draws to the data-viz method

- **Date**: 2026-10-16
- **Status**: accepted on 2026-10-16.
- **Amends**: [ADR-265](0265-vespera-run-rewrites-a-progress-page-in-the-working-directory-while-it-runs.md), in these places and no others: §1's two strings `Elapsed: <H:MM:SS>` and `Database size: <X.X> MB`, which §5 below makes a tile's label and value and §6 writes in a unit of its own; and §3's "only what the invocation holds in memory", which §2 below keeps and extends to two counts held in memory that are not the job's. [ADR-267](0267-the-progress-page-draws-tiles-a-timeline-of-the-steps-and-charts-over-time-and-its-tables-are-pages-of-their-own.md), in these places and no others: §1's tiles, each "holding its text and no other element", which §5 gives a label, a value and a detail; §1's chart of counts, which §7 draws from the running step's own counter first; §1's anatomy of a chart, a `<circle>` a sample and a value axis "from zero to the largest value drawn", which §7 replaces; §2's sample, which §4 extends; §3's table of samples, which gains the columns of §4; §4's colours and §7's rule that each state colour clears 3:1, which §9 replaces with the palette on record and a word beside every mark; the timeline's `<title>`, which §8 shortens to the heading it now carries; and its alternative refused "Rates per minute on the tiles", which §3 reverses.
- **Keeps**: the rest of ADR-265 and ADR-267, above all ADR-265 §1's rule that the pages name no path, no title and no text of a document, no exception message and no run id, ADR-265 §3's rule that the page makes no query, ADR-265 §4, a write that fails fails nothing, ADR-267 §2's bound of 256 samples and its thinning, ADR-267 §4, the SVG written in Java with no library, no JSON and no template engine, and ADR-267 §5, one fixed script; [ADR-266](0266-every-invocation-names-its-progress-page-first-and-opens-it-and-vespera-label-writes-it-too.md), the page that is opened and the label command's table of answers; [ADR-130](0130-one-report-page-module-owns-the-skeleton-styling-escaping-and-tables.md), one module assembles every page, self-contained; [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md), no dependency a decision does not require; [ADR-057](0057-the-verdict-vocabulary-is-eight-values-a-closed-enum-edited-by-a-pr.md) and [ADR-146](0146-spreadsheets-are-out-of-scope-and-stage-1-removes-them-with-a-verdict-of-their-own.md), the closed vocabulary of verdict kinds, of which this adds none; [ADR-226](0226-the-eight-rules-in-pipeline-live-in-extraction-embedding-synthesis-and-profile-and-no-stages-version-names-pipeline.md), no stage's version names `pipeline`.

## Context

The operator, shown the prototype that preceded ADR-267, said of its outcome part, "this part is also missing". The prototype led with tiles of extraction's outcome: the occurrences not removed, of the number that entered extraction and those not yet converted among them; `extraction-failed` and `degenerate-output`, each with a rate per minute over the last ten minutes; the database's size with its growth in MB a minute; and a line chart of the three over time. It read them from ADR-212's counting script, which queries the database.

Then, reading ADR-267's page during a live run, the operator sent three screenshots and asked "what is this?":

- the chart of the database's size, at 51.7 GB with a few MB of growth in six minutes, was a flat line along the top: its value axis ran from zero; its top label read `1732.3 MB` for 51,732.3 MB, the first digits clipped by the left edge; and it had no title a reader could see, the SVG's `<title>` being a tooltip and an accessible name, never drawn;
- the chart of counts during stage 4b, a tasklet, drew `read 0` and `written 0`, flat, on an axis from 0 to 1, the second line hiding the first: Spring Batch counts nothing for a tasklet, though the step's own counter was logging how far it had got;
- and, of the page as a whole, "improve the readability and the look … it needs axis titles".

The look is specified by the data-viz method: its references on choosing a form, marks and anatomy, anti-patterns, the palette and its formula, and components, and its palette validator.

What the page reads is held in memory (ADR-265 §3): the job's executions and their counts, each step's last progress line, and two file sizes. Who removed what is not: verdicts are appended by `ledger`'s `Verdicts.verdict`, the one statement that inserts into `verdict`, called from `corpus`, `extraction`, `similarity` and `pipeline` on each step's own thread. Each blocking kind is written by one step of the job:

| Step | Kinds it records |
| --- | --- |
| `byte-level-reduction` | `broken`, `duplicate-of`, `superseded-by`, `out-of-scope` |
| `extraction` | `extraction-failed`, `degenerate-output` |
| `content-redundancy` | `redundant-with` |
| `relevance-floor` | `below-threshold` |

`passed` removes nothing and is not shown. No stage's version names `ledger` or `pipeline` (`StageModules`), so a change to either moves no run id.

## Decision

### 1. Which outcomes the page shows

**The outcome step is the latest of the four steps of the table above, in job order, whose execution in this invocation has recorded what entered it (§2).** The page shows that step's outcomes, by verdict kind, and no other step's: one stage removes occurrences at a time, and the step that is removing, or last removed, is the one an operator watches. A step that ended at a shut gate, or was skipped as already recorded, recorded nothing of what entered it and is never the outcome step. Before any step is, the page shows no outcome tile and no chart of verdicts.

A kind is written as `CONTEXT.md` writes it, the constant's name in lower case with `-` for `_`: `extraction-failed`, `degenerate-output`. Those are the vocabulary's own words for the kinds, and no other words for them are made up here.

### 2. Where the counts come from, held in memory

**The page still makes no query (ADR-265 §3), and nothing it shows is read from the database on its thread.** Two counts it did not have are kept in memory by the code that already makes them:

- **Verdicts recorded.** `Verdicts` counts, in memory and for the life of the process, the verdicts of each kind it appends. A delete subtracts nothing. When a verdict is counted depends on the transaction its insert is made in:
  - **in a transaction a chunk commits**, extraction's: when that transaction commits, and not at all where it rolls back, so a chunk rolled back and written again item by item is counted once. `Verdicts` registers **one** `TransactionSynchronization` a transaction, holding a tally by kind of the verdicts inserted in it, and adds the tally to the counts in `afterCommit`; it never registers one a verdict, which over a chunk of a thousand would be a thousand callbacks;
  - **in the one transaction of a step whose whole work is one transaction**, the tasklet steps `TaskletSteps.taskletStep` builds, of which byte-level reduction, content redundancy and the relevance floor record verdicts: **at the insert**. Their transaction commits only when the step ends, so counted on commit their tiles would read zero for the whole of the step and jump at its end, and that step is the one the operator watches. A rollback there fails the step, and the page then shows it `failed` (ADR-265 §1), so a count whose rows were rolled back is shown only beside a step that says it failed. The step's work runs inside `Verdicts.countingAtInsert(Callable<T>)`, which counts every verdict its thread inserts until the work returns or throws, and then counts as before; `TaskletSteps.once`, which holds every such tasklet's work and is handed the `Ledger`, is where it is called, or each of the three tasklets around its own work where that work does not pass through `once`;
  - **with no transaction active**: at once.

  `ProgressPage` reads the eight counts of the blocking kinds at each write, without a lock any writer waits on, and shows each less what it read in `beforeJob`, so the page counts the verdicts this invocation recorded.
- **What entered the outcome step.** Each of the four steps, in each execution that goes on to judge, holds once, on its own thread, after it has discarded what it redoes and before it records its first verdict, how many occurrences it judges in this invocation, in its step execution's context under one key, a constant of `ProgressPage`. The job repository is `ResourcelessJobRepository`, so the context is memory, as `ShutGates` holds its gates in the job's (ADR-265 §3). Two steps reuse the count they already make for their progress line (ADR-093), and make no other:
  - byte-level reduction: the survivors its broken check goes through, which it already counts as a timed statement;
  - extraction: `ExtractionItemProcessor`'s count of the survivors still to read, which is the survivors of its run where it recorded nothing and, on a resumed step, those it has not yet recorded, so that what entered is what this invocation takes in;

  and two make one count each, of the survivors of the run they record under (`Verdicts.survivorCount`), as a timed statement (ADR-193 §4.3): it is an anti-join over every occurrence of the walk, and an operator waiting on it is told so, as on every other count of survivors before a step's work:
  - content redundancy, after its discard and its `starting` line: `Stage 4b (redundancy resolution) is counting the survivors it judges`, then `Stage 4b (redundancy resolution) counted the survivors it judges in <S> s`;
  - the relevance floor, where a threshold applies, over the scoring run, before it counts the scores below the threshold: `Stage 5e (relevance floor) is counting the survivors it judges`, then `Stage 5e (relevance floor) counted the survivors it judges in <S> s`.

  A count not made, because the step is already recorded, its gate is shut or no threshold applies, writes neither line.

**The survivors the page shows are what entered less the outcome step's verdicts counted since `beforeJob`.** Occurrences not yet judged are among them, as are faults not yet resolved: each carries no blocking verdict, and is a survivor (`CONTEXT.md`). So the number falls as the step goes, chunk by chunk in extraction and verdict by verdict in the three tasklet steps, and is exact when the step ends; a tasklet step that fails may have shown verdicts its rollback removed, beside its `failed`. A resumed step's verdicts of earlier invocations are on no tile, but they are not among what entered it either, so the survivors stay right; what the tiles of kinds count is this invocation's work, which is what a rate is read from.

**A cheap count query on the page's thread was refused.** The survivor count is an anti-join over every occurrence of the walk, and a count by kind a statement over the run; every five seconds on the hot path they would take a connection from the pool the steps write through, and no test can see SQLite's lock contention, since the test profile is in memory with a pool of one. ADR-265's refusal of "the page computed from the database" and ADR-267's of "the history read from the database at each write" stand, and ADR-265 §3 is not amended.

### 3. Rates over the last ten minutes

**A rate is read off two samples: the latest, and the latest held sample taken at least ten minutes before it, or, where none is, the first.** It is the difference of the two values over the minutes between them, and the text says how long that is, as `<H:MM:SS>`: `over the last 0:11:00`. Thinning (ADR-267 §2) spaces the old samples out, so the stretch may be longer than ten minutes, and the text says by how much. Where the two are one sample, or taken in the same second, there is no rate.

- **A kind's rate**, on its tile: `<R> / min over the last <H:MM:SS>`, `<R>` to one decimal place; with no rate, `rate not measured yet`.
- **The database's growth**, on the `database` tile: `<size> / min over the last <H:MM:SS>`, `<size>` written as §6 writes a size, with a `-` where it shrank, as the write-ahead log does at a checkpoint; with no rate, `growth not measured yet`.

ADR-267 refused rates because they "change with the spacing thinning gives them". Read off a sample at least ten minutes back, a rate covers at least ten minutes and says how many more, so the spacing changes how long it covers and never what it claims; and the operator asked for them by name.

### 4. What a sample holds

**ADR-267 §2's sample gains, read at the same write:** the running step's last progress line, as the table of steps shows it, or none; the outcome step, or none; the survivors of §2, where there is an outcome step; and the count of each of the eight blocking kinds since `beforeJob`. **`progress-samples.html`'s header row becomes** `Elapsed`, `Database size`, `Step`, `Read`, `Written`, `Progress`, `Outcome step`, `Survivors`, then the eight kinds in `VerdictKind`'s order, `broken`, `duplicate-of`, `superseded-by`, `out-of-scope`, `extraction-failed`, `degenerate-output`, `redundant-with`, `below-threshold`. `Progress`, `Outcome step` and `Survivors` are empty where the sample has none, and a kind's cell is its count, `0` before anything was recorded. It stays the text alternative of every chart (ADR-267 §7).

### 5. The tiles

**Each tile is `<div class="tile" data-tile="<key>">` holding, in order, `<span class="label">`, `<span class="value">` and, where it has one, `<span class="detail">`, each holding text and no other element**: a label small and in the secondary ink, a value large, and a detail, a rate or what the value is of, small. They are one row of figures at the top of the page, as the method's figure contract has them. A tile's text read in order is its label, its value and its detail.

| Key | Label | Value | Detail |
| --- | --- | --- | --- |
| `steps` | `Steps finished` | `<N> of 15` | none |
| `step` | `Step` | the step ADR-267 §1 names, or `none yet` | its state, where there is a step |
| `elapsed` | `Elapsed` | `<H:MM:SS>` | none |
| `database` | `Database size` | the size of §6 | the growth of §3 |
| `survivors` | `Survivors` | `<N>` | `of <M> when <step> started`, `<M>` what entered the outcome step |
| one per kind the outcome step records, in `VerdictKind`'s order | the kind, as §1 writes it | its count | its rate of §3 |

The first four are always there, and the outcome tiles only where §1 names an outcome step. The label command's page has `command`, labelled `Command`, whose value is `vespera label` or `vespera label --auto`, then `elapsed` and `database`, with no detail on any (ADR-267 §6).

### 6. Numbers and sizes

**A count is a whole number with `,` between each group of three digits from four digits up (`51,732,300`), on every tile, label, tooltip and table cell of the three pages.** A rate of a count has one decimal place, grouped the same way.

**A size is written in the largest of `B`, `KB`, `MB`, `GB` and `TB`, in powers of 1,000 as ADR-265 counts megabytes, whose value is at least 1**: bytes as a whole number (`0 B`, `512 B`), every other unit to two decimal places (`2.05 KB`, `412.30 MB`, `51.73 GB`), grouped as a count is. A value that rounds to 1,000 of a unit is written in the next (`1.00 MB`, never `1,000.00 KB`). The same writing is used for the `database` tile, the growth, every end label and tooltip of the database chart and the `Database size` cells of the table of samples. **The value axis of a size is written in a unit of its own**: the largest of the five in which the axis's span, margins included, is at least 1, which its title names; its ticks are plain numbers in that unit, grouped by thousands, with as many decimal places as the step between ticks needs and no more, and whole bytes in `B`. So 51.7 GB growing by 4 MB is an axis titled `Database size (MB)` whose ticks read `51,700`, `51,701`, … `51,704`, where two decimals of GB would print one tick five times; and a constant size, whose span is one byte, is an axis in `B` with its one tick at the size, `51,700,000,000`.

### 7. The charts

**Three charts over time, each in the order below, each drawn as the anatomy that follows.**

| `data-chart` | Title | Caption | Value axis title | Value axis |
| --- | --- | --- | --- | --- |
| `database` | `Database size over time` | `The size of the database and its write-ahead log on disk at each sample.` | `Database size (<unit>)` | the values' own range |
| `counts` | of the running step, below | below | below | from zero |
| `verdicts` | `Verdicts recorded by <step> over time` | `The verdicts <step> recorded in this invocation, by kind, at each sample.` | `Verdicts (count)` | from zero |

**The chart of counts is of the step of the latest sample that names a step (ADR-267 §1), drawn from what that step says of itself first:**

- where any of its samples holds a counter's line, `<label>: <N> of <M> (<P>%)` or `<label>: <N> so far`, it draws `<N>` for each of its samples whose line has the label of the latest such line, as one series named `done`, titled `<label> over time`, captioned `How many of <M> it had done at each sample.` with `<M>` the latest line's total, or `How many it had done at each sample.` for a line with none, its value axis titled `Done (count)` and running from zero to `<M>` where there is one;
- else, where any of its samples has a read or write count above zero, it draws ADR-267's two series, `read` and `written`, titled `Read and written by <step> over time`, captioned `The step's read count and write count at each sample.`, its axis titled `Items (count)`;
- else it draws no chart, and in its place one paragraph, `<step> reports no count to draw.`

A tasklet's counts are Spring Batch's and stay zero, while its counter says how far it has got; a statement's line over rows is an estimate of a percentage and no count, so it is not drawn.

**The chart of verdicts draws one series a kind the outcome step records, named as §1 writes it**, and is not drawn where §1 names no outcome step. The survivors are not on it: thousands against tens on one axis would flatten every kind into its baseline, and a second scale beside it is refused below. They are on their tile and in the table of samples.

**The anatomy of every chart over time**, in this order inside one `<figure class="chart-card">`:

1. `<h2>` holding the title, which is also the SVG's `<title>`, word for word;
2. `<p class="caption">` holding the caption, one sentence;
3. for two series or more, a legend, `<ul class="legend">`, one `<li>` a series in the order drawn, a key in the series' colour beside its name; one series has no legend, its title names it;
4. `<svg class="chart" data-chart="<key>" role="img" viewBox="…">`, its first child its `<title>`, holding:
   - **one value axis, never two scales**; its title above the axis at the plot's top left, `<text class="axis-title y">`, naming what is drawn and its unit in parentheses; the time axis titled below its ticks, `<text class="axis-title x">Elapsed (h:mm:ss)</text>`;
   - **a value axis over a size spanning the values' own range**: from the smallest value drawn to the largest, with a margin of a twentieth of the span at each end, and a span of at least one byte, centred on a constant value so that it is drawn at mid-height; its lower end is never below zero, so a database of a few bytes has an axis from `0`. The span has no floor but that byte: a floor of a hundredth of the largest size, tried once, draws 4 MB of growth on 51.7 GB as a twentieth of the plot, the flat line the operator asked about. A size's zero is not a value the database has been at in the invocation, and a growth of megabytes on gigabytes is what the chart is read for. **A value axis over counts runs from zero** to the largest value drawn, or to `<M>`, and at least to 1: a count starts at zero within its step, and its height against zero is what it says;
   - the value axis is the chart's first `<line class="axis">`, from the plot's top to its bottom, so the plot's height is read off it;
   - at most five value ticks at round values, each `<text class="tick value" text-anchor="end" x="<X>">` holding a number and nothing else, written as §6 writes an axis, **`<X>` at least eight units of the viewBox for each character of its text**, so that no label is clipped at the left edge; at most five time ticks as `<H:MM:SS>`; a solid hairline gridline at each value tick and solid hairline axes, in the grid and axis colours of §9;
   - for each series, one `<polyline>` 2 units wide with round joins and caps, the first solid and **each after it carrying a `stroke-dasharray` of its own**, so that two series of equal values both show; for each sample, a transparent `<circle>` of radius 12 holding the `<title>` ADR-267 §1 gives it, `<H:MM:SS>: <value>`, as the tooltip and the hit area; and **one visible marker, at the latest sample only**, `<circle class="end …">` of radius 4 in the series' colour with a 2-unit ring in the surface colour, with no `<title>`;
   - at the end of each line, `<text class="end-label">` holding `<name> <value>`, or the value alone for one series, in the text ink and never in the series' colour; where two end labels would lie within 14 units of each other, one label holds both, `<name> <value>, <name> <value>`, so that none is stacked away from its line. **No end label is clipped at the right edge**: the plot leaves to its right eight units for each character of the longest end label it draws, and ends there.

**Each `<svg>` is never drawn narrower than its viewBox**: its CSS `min-width` is the viewBox's width in pixels, so its 12-pixel text stays 12 pixels, and at a phone's width the card scrolls sideways (§9).

**The page holds no table and no script but ADR-267 §5's; there is no crosshair and no hover layer.** A crosshair needs a script over the samples, which ADR-267 refused; the transparent circles give every sample a tooltip a pointer can land on, and a screen reader a name.

**A chart of the database's growth was refused**: a difference between two thinned samples five seconds apart swings with every checkpoint of the write-ahead log, while the size over its own range shows the slope, and the `database` tile says the rate.

### 8. The timeline

**The timeline stays one `<svg class="timeline">` (ADR-267 §1), inside a `<figure class="chart-card">` with `<h2>Timeline of the steps</h2>`, the SVG's `<title>` the same words, and `<p class="caption">Each step's bar runs from its start to its end, or to this write while it runs, and is labelled with its state.</p>`.** Each step's `<g>` holds its name, its bar where it started, and `<text class="state">` holding its state, one of ADR-265 §1's five, in the text ink beside the bar, so that a state is never told by colour alone. Its time axis is titled `Elapsed (h:mm:ss)`. Names and states are in the size of the other text, on rows tall enough that neither overlaps the next.

### 9. Colour and type

**The page's colours are the data-viz method's reference palette, as written there**, defined as custom properties in the stylesheet ADR-267 §4 gives these pages, one set for the light scheme and one under `@media (prefers-color-scheme: dark)`:

| Property | Light | Dark | Role |
| --- | --- | --- | --- |
| `--page` | `#f9f9f7` | `#0d0d0d` | page plane |
| `--surface` | `#fcfcfb` | `#1a1a19` | tiles and charts |
| `--text` | `#0b0b0b` | `#f0efec` | primary ink |
| `--muted` | `#52514e` | `#c3c2b7` | secondary ink: labels, ticks, captions |
| `--grid` | `#e1e0d9` | `#2c2c2a` | gridlines |
| `--axis` | `#c3c2b7` | `#383835` | axes and borders |
| `--series-1` … `--series-5` | `#2a78d6`, `#eb6834`, `#1baf7a`, `#eda100`, `#e87ba4` | `#3987e5`, `#d95926`, `#199e70`, `#c98500`, `#d55181` | series, in this fixed order |
| `--finished` | `#0ca30c` | `#0ca30c` | status good |
| `--gate` | `#fab219` | `#fab219` | status warning |
| `--failed` | `#d03b3b` | `#d03b3b` | status critical |
| `--running` | `#2a78d6` | `#3987e5` | a step running |

Series take the slots in order, `s1` the first series of a chart, and never more than five: the byte-level reduction's four kinds are the most any chart draws. Status colours mean a state and nothing else, are the same in both schemes, and colour only the timeline's bars, each beside its state's word.

**The palette was checked with the method's validator** (`validate_palette.js`), adjacent pairs, which lines are:

- light, on `#fcfcfb`: lightness band, chroma floor, CVD separation (worst adjacent ΔE 9.1, `#eda100`↔`#1baf7a`, protanopia), normal-vision floor (worst 19.6) pass; contrast against the surface is a WARN, `#1baf7a` 2.74, `#eda100` 2.11 and `#e87ba4` 2.62 below 3:1, which obliges a relief channel: every chart has its legend, its end labels and the table of samples, so a value is never read off a line's colour alone;
- dark, on `#1a1a19`: every check passes (worst CVD ΔE 8.4, normal-vision 19.3, all five at least 3:1);
- no check FAILs in either scheme.

**Text clears WCAG 2.2's 4.5:1 (1.4.3) in both schemes**: `--text` 19.17 and `--muted` 7.73 on the light surface, 15.15 and 9.72 on the dark. ADR-267 §7's rule that each state colour clears 3:1 is replaced: `--gate` is 1.79:1 on the light surface by the palette's own design, and the state's word beside every bar is the mitigation the method prescribes for a status colour.

**Type**: the system sans throughout; numbers in a column or on an axis tabular, a tile's value proportional; spacing in multiples of four pixels; tiles and charts on the surface, a hairline border and the same corner on each. **At a phone's width** the tiles wrap into fewer columns and each chart card scrolls sideways rather than shrink its text below legibility; nothing scrolls inside a card vertically.

### 10. What does not change

- **Nothing the pages hold names a path, a title or text of a document, a message or a run id**: the additions are numbers, sizes, times, step names, kind names, fixed words and the progress lines ADR-265 §3 admits.
- **The final write keeps everything**: the last write of `progress.html` draws the tiles and charts of every write before it and its own (ADR-267 §4).
- **The modules, and run ids.** `ProgressPage` and `ReportPage` of `pipeline`, the four steps' one count each, in `pipeline`, and `Verdicts`'s counts in `ledger`. No stage's version names `pipeline` or `ledger` (ADR-226, `StageModules`), and none of the counts decides a verdict, a cache key, a cluster or text of the deliverable. **No run id moves.** No property is added: the ten minutes, the margins, the bound and the palette are constants, as the operator asked for none ("Just use defaults").
- **The README**, in the change that builds this, says in its sentence on `progress.html` that the page shows the survivors and the verdicts of the stage removing occurrences, with their rates, and the database's growth.

## Alternatives refused

- **The counts read from the database on the page's thread, or by ADR-212's script.** §2: a statement every five seconds over a million occurrences, through the steps' pool, which ADR-265 and ADR-267 refused.
- **Counters kept by each verdict's caller.** Verdicts are appended from four modules on several paths, faults resolved at a step's end among them; one count where the one insert is made misses none and counts a rolled back chunk not at all.
- **Every stage's outcomes at once.** Fifteen tiles and four charts for one stage working at a time; the timeline and the table of samples hold the rest.
- **"Not dropped"**, the prototype's word. The vocabulary's word for an occurrence carrying no blocking verdict, judged or not, is survivor.
- **Survivors on the chart of verdicts, or on a second value axis.** §7: one flattens the kinds; two scales invent a correlation, the method's first anti-pattern.
- **A value axis from zero for sizes, or the size's own range for counts.** §7: the operator's flat line on one side; a count's zero is meaningful on the other.
- **A marker on every sample.** It is noise at 256 samples; one marker at the latest point, and a transparent target for each tooltip.
- **Text in the series' colours.** Three light slots are below 3:1 as text; identity is the key beside the text.
- **A crosshair, or a growth chart.** §7.
- **A setting for any of it.** The operator asked for none.

## Consequences

- An operator sees, at the top of the page, how many occurrences survive the stage removing them, of how many entered, how many of each kind it has removed and how fast, and how fast the database grows; and, below, charts with a heading, a caption and titled axes, a size chart that shows growth on gigabytes, a chart of counts that moves for a tasklet, and a timeline that says each step's state in words.
- Each write reads eight counters and one context value more, and renders a few more elements; no write makes a query. A chunk's transaction carries one callback more; a tasklet step's verdicts are on the page as they are inserted, and one that fails may have shown some its rollback removed.
- Content redundancy and the relevance floor, where they judge, each make one count of survivors more, before their first verdict, and say so in two lines of their own.
- Tests that read a tile's text or ADR-265's two strings read the label and value spans instead.
- The guard's list of pages an agent may read (ADR-261) names none of the three.
- No schema, cache key, verdict kind, property or run id changes.

## Tests

| Method | What it holds |
| --- | --- |
| `pipeline.ProgressPageOutcomesInvocationTest.theLastWriteShowsWhatExtractionRemovedByKind` | over five occurrences of which the converter refuses one and one holds page headers and footers alone, with stage 4's gate shut, the last write's tiles are the four of §5, `survivors` (`3`, `of 5 when extraction started`), `extraction-failed` (`1`) and `degenerate-output` (`1`), each kind with a rate or `rate not measured yet`, and no tile of another kind; the chart of verdicts is titled `Verdicts recorded by extraction over time` and ends at `extraction-failed 1` and `degenerate-output 1`; the last row of the table of samples holds `extraction`, `3` and the eight kinds' counts |
| `pipeline.ProgressPageOutcomesInvocationTest.theOutcomePagesNameNoPathAndNoTextOfADocument` | after the same invocation, no name of the corpus, the converter's message, the root, the seed folder or the working directory is on any of the three pages |
| `pipeline.ProgressPageReadabilityTest.aSizeIsWrittenInTheLargestUnitThatKeepsItAtLeastOne` | sizes of nothing, 2,050 bytes and 1,234,567 bytes are `0 B`, `2.05 KB` and `1.23 MB` on the `database` tile, in the table of samples and in the database chart's tooltips, and the chart's value axis is titled `Database size (MB)` |
| `pipeline.ProgressPageReadabilityTest.theDatabaseGrowthIsReadOverTheLastTenMinutesOfSamples` | the first write's growth is `growth not measured yet`; after samples at 0, 60, 300, 660 and 720 seconds, the last write's is `1.00 MB / min over the last 0:11:00`, read off the samples at 60 and 720 seconds |
| `pipeline.ProgressPageReadabilityTest.aSizeChartSpansItsValuesOwnRange` | sizes of 10, 10.5 and 10.25 MB are drawn at least half the chart's height apart, the middle one midway |
| `pipeline.ProgressPageReadabilityTest.aConstantSizeIsDrawnAtMidHeight` | three samples of the same size are drawn at one height in the middle third of the chart, on an axis titled `Database size (B)` whose one tick is the size in whole bytes, grouped by thousands |
| `pipeline.ProgressPageReadabilityTest.megabytesOfGrowthOnGigabytesFillTheChart` | a database of 51.7 GB that grows by 2 MB and then by 2 MB more is drawn with its latest point at least half the plot's height above its first, on an axis titled `Database size (MB)` whose ticks are whole megabytes, each different, grouped by thousands, while its tooltips say `51.70 GB` |
| `pipeline.ProgressPageOutcomesInvocationTest.aSampleInTheMiddleOfATaskletCountsItsVerdicts` | while byte-level reduction is held at its broken check's last line, its one transaction still open, the page already shows the `out-of-scope` verdict it recorded, and `2` survivors of the `3` that entered |
| `ledger.VerdictsCountedInMemoryTest.aChunksVerdictsAreCountedOnceWhenItCommitsByOneCallback` | three verdicts of two kinds in one transaction count nothing until it commits and then three, by kind, and the transaction carries one synchronization for them, not three |
| `ledger.VerdictsCountedInMemoryTest.aRolledBackTransactionCountsNothing` | a verdict whose transaction rolls back is never counted |
| `ledger.VerdictsCountedInMemoryTest.aVerdictOutsideAnyTransactionIsCountedAtOnce` | a verdict inserted with no transaction active is counted when the insert returns |
| `ledger.VerdictsCountedInMemoryTest.workCountingAtInsertCountsEachVerdictAsItIsInserted` | inside `countingAtInsert`, in an open transaction, a verdict is counted at its insert and stays counted when that transaction rolls back; after the work returns, a verdict in a transaction is counted on commit again |
| `pipeline.ProgressPageReadabilityTest.everyValueLabelFitsAndACountIsGroupedByThousands` | a read count of 51,732,300 is `51,732,300` in the table of steps and the chart's end label, and every value tick lies at least eight units a character from the chart's left edge |
| `pipeline.ProgressPageReadabilityTest.everyDrawingHasAVisibleTitleACaptionAndAxisTitles` | every `<svg>` on `progress.html` follows an `<h2>` holding its `<title>` and a caption; every chart and the timeline titles its time axis `Elapsed (h:mm:ss)`, and the database chart, over a constant size, and the chart of counts title their value axis with a unit, `Database size (B)` and `Items (count)` |
| `pipeline.ProgressPageReadabilityTest.aStepThatCountsItselfIsChartedByItsOwnCounter` | a tasklet step whose counter logs `3 of 7` and then `5 of 7`, its read and write counts zero, is charted as `done` at 3 and 5, titled with its counter's label, on an axis from 0 to 7 titled `Done (count)`, and draws no `read` or `written` |
| `pipeline.ProgressPageReadabilityTest.aStepThatCountsNothingDrawsNoChartOfCounts` | a running step with no counter's line and read and write counts of zero draws no chart of counts, and the page says `content-redundancy reports no count to draw.` |
| `pipeline.ProgressPageReadabilityTest.twoEqualSeriesAreBothDrawnLegendedAndLabelled` | read and written both 5 are drawn as a solid and a dashed line, in a legend of two, with end labels holding both values, and no text of the chart wears a series' colour |
| `pipeline.ProgressPageReadabilityTest.aChartOfOneSeriesHasNoLegendAndMarksOnlyItsLatestPoint` | the database chart, over three samples, has no legend and one visible marker, and a tooltip for each sample |
| `pipeline.ProgressPageReadabilityTest.theTimelineSaysEveryStepsStateInWords` | each of the timeline's fifteen marks holds its state's word as text, equal to its `data-state` |
| `pipeline.ProgressPageReadabilityTest.theColoursAreThePaletteOnRecordAndTextClearsContrastInBothSchemes` | the stylesheet's light and dark properties are §9's values, and `--text` and `--muted` clear 4.5:1 against `--surface` and `--page` in both schemes |

ADR-265's and ADR-267's tests that read a tile's text, the strings `Elapsed: …` and `Database size: … MB`, or the five columns of the table of samples, read §5's spans, §6's sizes and §4's header row instead: `ProgressPageChartsTest`'s `theTilesSayTheStepsFinishedTheStepRunningTheTimeAndTheSize`, `theSamplesAreThinnedToAtMost256KeepingTheFirstAndTheLatest`, `theTablesAreOnTwoPagesOfTheirOwnThatTheProgressPageLinks` and `theLabelPageHasTilesAndNoTimelineChartLinkOrScript`, `ProgressPageInvocationTest.whileAStepRunsThePageNamesItAndIsRewritten` and `LocalLabellingInvocationTest.theAutoPageCountsTheAnswersInOrder`. `ProgressPageChartsTest.theChartOfCountsIsOfTheLatestStepASampleNames` gives the census and extraction read counts, so that each is charted by §7's second rule, and waits for the `step` tile naming the census as running, in §5's spans. The two timed counts of §2 are in the statements that `RedundancyResolutionReportsItsProgressInvocationTest`'s `countsEveryLoop` and `countsNothingWhenNoDocumentWasSigned` and `StageFiveReportsItsProgressInvocationTest.theRelevanceFloorCountsTheVerdictsItWrites`, over a threshold that applies, expect: first among content redundancy's, and before the floor's count of the scores below the threshold; extraction's count is the one `ExtractionResumeInvocationTest` and `StageTwoReportsItsFaultResolutionInvocationTest` already expect, and they are unchanged.

## What this does not decide

- **The look beyond what §5, §7, §8 and §9 fix**: sizes, the exact dash patterns, the tick values, the words of the links, are the renderer's.
- **The census's progress**, which still logs through `corpus` (ADR-265): its chart of counts is §7's paragraph unless the census moves a count.
- **Whether an agent may read the pages**, which stays a record of its own.
