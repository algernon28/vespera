# ADR-269 — The progress page is four tabs, draws a hover over each chart, and lists the runs the working directory holds

- **Date**: 2026-10-17
- **Status**: accepted on 2026-10-17.
- **Amends**: [ADR-265](0265-vespera-run-rewrites-a-progress-page-in-the-working-directory-while-it-runs.md), in these places and no others: §1's two strings saying whether the invocation runs or has ended, which §2 below extends; §3's "Only what the invocation holds in memory … It opens no connection and makes no query", which §5 below keeps for every write but the two readings it adds; and its alternative refused "The page computed from the database", which stands for every write and is set aside for those two readings alone. [ADR-266](0266-every-invocation-names-its-progress-page-first-and-opens-it-and-vespera-label-writes-it-too.md), in one place: §3's two strings of the label command's page, which §2 below extends. [ADR-267](0267-the-progress-page-draws-tiles-a-timeline-of-the-steps-and-charts-over-time-and-its-tables-are-pages-of-their-own.md), in these places and no others: §1's links, which §1 below replaces with a bar of tabs; §3's three pages, their head and the order they are moved in, which §1 below makes four; §5's "The pages of §3 carry no mark and no script", its "The last write carries no `<script>`" and the script's text, which §3 below replaces; and its alternative refused "A crosshair that follows the pointer, with a tooltip of every series at that time", which §4 reverses. [ADR-268](0268-the-progress-page-shows-what-the-stage-that-removes-occurrences-has-removed-and-draws-to-the-data-viz-method.md), in these places and no others: §5's detail of the `survivors` tile, which §6 below extends while the outcome step runs; §7's "there is no crosshair and no hover layer", which §4 reverses; §7's "Each `<svg>` is never drawn narrower than its viewBox: its CSS `min-width` is the viewBox's width in pixels", and §9's "At a phone's width … each chart card scrolls sideways rather than shrink its text below legibility", which §8 below replaces with a width at which text stays legible; and the crosshair of its alternative refused "A crosshair, or a growth chart", whose growth chart stands.
- **Keeps**: the rest of ADR-265, ADR-266, ADR-267 and ADR-268, above all ADR-265 §1's rule that the pages name no path, no title and no text of a document, no exception message and no run id, ADR-265 §4, a write that fails fails nothing, ADR-266 §2, only `progress.html` is opened, and once, ADR-267 §2's bound of 256 samples, ADR-267 §4, the SVG written in Java with no library, no JSON and no template engine, ADR-268 §2, the outcomes counted in memory, and ADR-268 §9's palette; [ADR-130](0130-one-report-page-module-owns-the-skeleton-styling-escaping-and-tables.md), one module assembles every page, self-contained; [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md), no dependency a decision does not require; [ADR-209](0209-the-ledger-is-four-records-behind-one-type-no-capability-module-names-spring-batch-and-a-tables-sql-is-its-owners.md), a table is named only by its owner; [ADR-218](0218-every-statement-whose-temporary-files-grow-with-the-corpus-is-an-exception-to-adr-060-with-its-size.md) and [ADR-224](0224-the-invocation-accounts-counts-by-kind-and-the-two-reads-of-the-embedder-identities-sort-nothing-and-four-of-adr-218s-reads-stay-excepted.md), no statement that sorts without a record; [ADR-223](0223-5fs-size-report-6a-and-6b-go-through-one-seed-partition-at-a-time-and-what-is-still-held-says-why.md) §3 and §6, a page moved over its place in one move; [ADR-226](0226-the-eight-rules-in-pipeline-live-in-extraction-embedding-synthesis-and-profile-and-no-stages-version-names-pipeline.md), no stage's version names `pipeline`.

## Context

The operator found two progress pages. One is Vespera's, `progress.html` and the two pages beside it, in the working directory. The other was kept by a script outside Vespera, in the user's temporary folder: started at logon, it asked the system every ten seconds whether a Vespera process was running, and while one was it ran ADR-212's counting script against the working directory and rewrote a page of its own from what it printed. The operator wants every progress page in the working directory, written by Vespera, and the features of the two merged, in more tabs where they need them. The script is retired.

What the script's page showed that Vespera's does not:

- **a hover layer**: on each line chart, a vertical line at the sample nearest the pointer and one tooltip listing every series' value at that time, drawn by a script from a JSON object inside the page;
- **the runs the working directory holds**, across invocations: every run of every stage, from ADR-212's counts of runs by stage, finished steps by run, verdicts by kind and run, and survivors by run, of which it showed the latest stage reached and whether it had finished;
- **when the invocation started**: `Run started <time> — live, last reading <time>, refreshes every <S>s`, and after the end `Run ended — last reading <time>`;
- **its readings as a table**, newest first, folded into a `<details>` below the charts;
- **the survivors' tile detail** `of <N> entered, incl. unprocessed`, saying that what has not been judged yet is counted among them.

Of the width of the pages the operator said "i don't want horizontal scroll bars in the dashboard: make the width self-adjustable", and then "the scale should adjust itself so the scrollbar is not needed. The panels should be as wide as the browser, and add the scrollbars only when the charts stop being readable". As written, every page of the progress page is one column of at most 900 px, the shared stylesheet's `body { max-width: 900px; }`, and every `<svg>` has `min-width: 1100px` inside a card with `overflow-x: auto` (ADR-268 §7), so every chart scrolls sideways at any width of window, and a table of sixteen columns spills past the column. A chart's viewBox is 1100 units wide and its text 12 units high, so drawn `<W>` px wide its text is `12 × <W> / 1100` px high.

Its clock-time axis, its chart of what extraction kept and removed on one axis, and its word "not dropped" are each a form ADR-267 or ADR-268 chose against. Its staleness mark and its rates over the last ten minutes are already on Vespera's page (ADR-267 §5, ADR-268 §3).

**What reading the runs costs**, measured on a synthetic ledger of `schema.sql`'s `run`, `finished_step` and `verdict` tables with its two indexes on `verdict`: 2,000,000 verdict rows over 12 runs, their rows interleaved, each run counted by kind in ADR-224's form, `SELECT COUNT(*), SUM(kind = 'BROKEN' COLLATE NOCASE), … FROM verdict WHERE run_id = ?`, took 2.7 s together with the cache warm, 1.3 s a million verdicts; SQLite plans it as `SEARCH verdict USING INDEX verdict_by_run_id (run_id=?)`, sorting nothing. The listing `SELECT … FROM run ORDER BY walk_id, rowid` plans `USE TEMP B-TREE FOR ORDER BY`; `ORDER BY rowid` alone sorts nothing. The `verdict` table holds only blocking kinds, since no shipped class writes `passed`, so a run's rows are at most the occurrences it removed. A count of survivors is an anti-join over every occurrence of the run's walk (ADR-268 §2), so one a run, as the script made, is that anti-join as many times as the working directory holds runs.

## Decision

### 1. Four tabs, each a page of its own

**`vespera run` writes four pages, the tabs of one progress page, and each write moves all four into place**:

| Tab | Page | Title |
| --- | --- | --- |
| `Overview` | `progress.html` | `Vespera progress` |
| `Steps` | `progress-steps.html` | `Vespera progress: steps` |
| `Samples` | `progress-samples.html` | `Vespera progress: samples` |
| `Runs` | `progress-runs.html` | `Vespera progress: runs` |

`progress-runs.html` is a fixed name held by a constant of `ProgressPage` whose name ends in `FILE_NAME`, as the other three are. `Overview` holds what ADR-267 §1 and ADR-268 draw, `Steps` and `Samples` the tables ADR-267 §3 gives them, and `Runs` the table of §5.

**Every tab starts, in this order, with its title as an `<h1>`, the bar of tabs, the line saying whether the invocation runs or has ended (§2), and the line with the liveness mark and `Written at` (§3).** The bar is

```html
<nav class="tabs" aria-label="Progress pages"><a href="progress.html">Overview</a><a href="progress-steps.html">Steps</a><a href="progress-samples.html">Samples</a><a href="progress-runs.html">Runs</a></nav>
```

the same four links in this order on every tab, relative, so the four pages copied out of the working directory together still link, and the link to the tab itself carrying `aria-current="page"`, written as `<a href="progress-steps.html" aria-current="page">Steps</a>`, and no other link carrying it. The current tab is drawn apart from the others by its weight and an underline in the stylesheet ADR-267 §4 gives these pages, not by colour alone. The links of ADR-267 §1 and the link back of ADR-267 §3 are replaced by the bar.

**Each tab is one self-contained page through `ReportPage`, as ADR-267 §3 has it**: each reloads at the interval while the invocation runs and none after the last write; each is written at every write from the same reading; and the four are moved through their own `.part` and `ReplacingMove` in the order `progress-steps.html`, `progress-samples.html`, `progress-runs.html`, `progress.html`, the first that throws ending that write as ADR-267 §3 says. Only `progress.html` is opened (ADR-266 §2).

**Separate pages, not tabs drawn inside one page.** A tab is a link to a file, so each page holds only what it shows, a reload rewrites and reads one page of tens of kilobytes rather than one of all four, a browser's back button and address bar name the tab, and no script or CSS selector is needed to switch between them.

**`vespera label`'s page has no bar of tabs** and writes no tab of runs: it is the one page of a command that starts no job (ADR-267 §6), and tabs an earlier `vespera run` left are left as they are.

### 2. When the invocation started, and when it ended

**The line saying whether the invocation runs or has ended names the time it started and, once it has ended, the time it ended**, each as `yyyy-MM-dd HH:mm:ss` in the machine's zone, the form of `Written at`:

| Page | While it runs | On the last write |
| --- | --- | --- |
| `vespera run`'s four tabs | `This invocation is running. It started at <start>, and this page is rewritten every <S> s.` | `This invocation has ended: <outcome>. It started at <start> and ended at <end>.` |
| `vespera label`'s page | `This invocation is running. It started at <start>, and this page is rewritten when it ends.` | `This invocation has ended: <outcome>. It started at <start> and ended at <end>.` |

`<start>` is the job's start time, or the command's start (ADR-266 §3), as `Elapsed` reads it; `<end>` is the last write's instant, read off the job's `Clock`, so that `<end>` less `<start>` is the last write's `Elapsed`. A job that holds no start time is started at the first write's instant. `<S>` and `<outcome>` are ADR-265's and ADR-266's. The date is written whole, so an invocation that runs past midnight is not read as one that ended before it started. The words ADR-265 and ADR-266 fix, `This invocation is running.` and `This invocation has ended: <outcome>.`, still open the line.

### 3. The liveness mark and the one script, on every tab

**Every tab of `vespera run` carries ADR-267 §5's liveness mark at the head of its line with `Written at`, and one `<script>`, at every write, the last included.** The script's text is one constant of `ProgressPage`, the same on every tab and at every write of every invocation, and it does two things: ADR-267 §5's check of the mark, and the hover of §4. While the invocation runs the mark is `<span class="liveness" data-written="<ms>" data-interval="<ms>">live</span>`, as ADR-267 §5 has it; on the last write it is `<span class="liveness">ended</span>`, with no attribute, and the script marks stale only a mark that has `data-written`, so a page left open after the invocation ended is not called stale for being old, which is what ADR-267 §5's last write without a script was for. The script is on the last write because the hover of §4 is wanted most on the page that stays open.

**No data of the invocation is inside the `<script>`**: it reads attributes the page already carries and holds no number, name or time of its own invocation. It writes text into the page only through `textContent` and builds what it draws with `createElement` and `createElementNS`; it never sets `innerHTML`, `outerHTML` or `insertAdjacentHTML`, never calls `document.write`, `eval` or `Function`, so no value it reads is ever parsed as markup. A browser that runs no script shows every tooltip of ADR-267 §1 and the mark as written.

### 4. A hover over each line chart

**Each transparent `<circle class="hit">` of a line chart (ADR-268 §7) carries three attributes beside its `<title>`**: `data-series`, the name of its series; `data-at`, its sample's elapsed time as `<H:MM:SS>`, the time its `<title>` opens with; and `data-value`, its value as its `<title>` writes it, so that `<circle class="hit" … data-series="read" data-at="0:12:05" data-value="4,120"><title>0:12:05: 4,120</title></circle>`. A series' name is its legend's: `read` and `written`, `done`, and each kind as ADR-268 §1 writes it; the database chart's one series, whose legend and end label ADR-268 §7 leave unnamed, is named `size`. Each value is a number, a size as ADR-268 §6 writes it or a count grouped by thousands, and every attribute goes through `ReportPage.escape`.

**The script draws, while the pointer is over a chart's `<svg>`, one vertical line and one tooltip**: the line, `class="crosshair"`, at the `cx` of the hit circles nearest the pointer on the time axis, from the top of the value axis to its bottom, read off the chart's first `<line class="axis">`; and the tooltip, an HTML `<div class="tooltip">` the script adds to the chart's card, beside the line, holding that sample's `data-at` and then, in the order the series are drawn, each series' `data-series` and `data-value` at it. Both are hidden when the pointer leaves the chart. Their colours are the pages' `--axis`, `--surface` and `--text` (ADR-268 §9). The timeline has no hover beyond the `<title>` of each bar.

**The hover adds to what is there and replaces nothing**: each point keeps its `<title>`, a tooltip with no script and a name for a screen reader, and `progress-samples.html` stays the text alternative of every chart (ADR-267 §7). There is no keyboard hover: a keyboard reaches the samples' tab instead.

ADR-267 refused a crosshair because it needed "a script over the samples", a second copy of the page's data inside a `<script>`, where HTML escaping does not apply. Read from attributes the page already carries, escaped like every other attribute, the hover needs no copy and no data in the script; the build reads the attributes as text, and the drawing in Java is unchanged.

### 5. The tab of runs

**`progress-runs.html` lists every run the working directory holds**, of every walk and stage, whatever invocation minted it, in one table whose header row is `Walk`, `Stage`, `Run`, `Steps finished`, `State`, `This invocation`, then the eight blocking kinds in `VerdictKind`'s order as ADR-268 §4 writes them: `broken`, `duplicate-of`, `superseded-by`, `out-of-scope`, `extraction-failed`, `degenerate-output`, `redundant-with`, `below-threshold`.

| Column | Cell |
| --- | --- |
| `Walk` | the number of the walk the run is over, its `walk.id`, as a count |
| `Stage` | the run's `run.stage` |
| `Run` | the run's ordinal among the runs of the same stage over the same walk, from `1`, in the order they were minted, which is the order of the `run` table's rows |
| `Steps finished` | `<N> of <M>`: `<M>` the steps that record their completion under a run of that stage (the table below), `<N>` how many of them have their `finished_step` row under this run |
| `State` | `finished` where `<N>` is `<M>`, else `not finished` |
| `This invocation` | `arrived at` where this invocation minted or continued the run, as `InvocationRuns` holds it (ADR-154 §1), else empty |
| each kind | the verdicts of that kind recorded under the run, a count grouped by thousands, `0` where none |

| `run.stage` | The steps that record completion under its runs, in job order |
| --- | --- |
| `byte-level-reduction` | `byte-level-reduction` |
| `extraction` | `extraction` |
| `content-census` | `content-census` |
| `content-redundancy` | `redundancy-signature`, `content-redundancy` |
| `seed-measurement` | `seed-extraction`, `seed-corpus-comparison` |
| `embedding-scoring` | `embedding-scoring`, `relevance-scoring`, `clustering` |
| `arrangement` | `arrangement` |
| `generation` | `generation` |

The relevance floor and the relevance report record no completion (ADR-118), and the census mints no run. A run of a stage the table does not name has its `Steps finished` and `State` cells empty.

**The rows are in the order of the walks' numbers, within a walk in `StageModules`' order of the stages, a stage it does not name after them by name, and within a stage by ordinal.** The order is made in memory, over rows read in the `run` table's own order: the runs of a working directory are tens of rows, and an `ORDER BY` over two columns is a temporary B-tree (Context).

**No run id is on the tab**, nor anything ADR-265 §1 keeps off the pages: a run is told by its walk's number, its stage and its ordinal, and the ids read are held in memory only to be matched against `InvocationRuns`. A walk's number names no path.

**The tab is read twice in each invocation of `vespera run`, on the thread that runs the job, and at no other write**:

- **once as the job starts**, in `beforeJob`, after the first write has been made and the page opened (ADR-266 §2), so that neither waits for it, and before the first step runs, so that no step's connection waits for it;
- **once as the job ends**, in `afterJob`, before the last write, after the last step has ended.

Every write in between renders the tab from the reading held, and fills `This invocation` from `InvocationRuns` in memory at that write, so a run the invocation continues is marked as it reaches it; a run this invocation mints is on the tab from the last write. The thread that rewrites the pages every interval (ADR-265 §2) never reads the database, so ADR-265 §3 and its refusal of "the page computed from the database" stand for every write but these two readings. Under the tab's heading one paragraph says which reading it shows:

| Reading | Paragraph |
| --- | --- |
| none yet | `The runs this working directory holds are read before the first step starts.`, and no table |
| the first | `The runs this working directory held when this invocation started.` |
| the second | `The runs this working directory held when this invocation ended.` |
| either, holding no run | the paragraph of that reading, then `No run is recorded in this working directory.` in place of the table |

**A reading that fails fails nothing**: whatever it throws is caught where it is made, and logged once in the invocation, at WARN, as `the runs on the progress page could not be read: <exception class name>`, the class name only, as ADR-265 §4 writes its warning; the tab then says the paragraph of that reading and `The runs could not be read.` in place of the table, and every other write and page is made as before. The tab shows the latest reading's outcome: a reading at the end that fails replaces one at the start that did not, since a table of the runs as they were when the invocation started, under a last write, would be read as the runs it left.

**What a reading asks, through `ledger`, which owns the three tables (ADR-209)**: the runs, as `run`'s id, stage and walk in the table's own order, `ORDER BY rowid`; the `finished_step` rows; and, for each run, its count of each blocking kind in ADR-224's form, one statement a run, `WHERE run_id = ?` through `verdict_by_run_id`. None of them sorts, so `EveryStatementThatSortsIsRecordedTest` holds them as it holds every statement of `ledger`. At the measured 1.3 s a million verdicts, a reading costs about a second for each million occurrences the working directory's runs have removed, twice an invocation.

**No survivors are counted a run.** One a run is the anti-join of ADR-268 §2 as many times as there are runs, before the first step and after the last; the survivors of the stage removing occurrences now are on the `Overview` tab from counts held in memory (ADR-268), which is what the script's page showed of them.

### 6. The survivors' tile while its step runs

**While the outcome step is shown `running`, the detail of the `survivors` tile is `of <M> when <step> started, including those not yet judged`**; once the step is shown in any other state it is ADR-268 §5's `of <M> when <step> started`. An occurrence the step has not reached carries no blocking verdict and is a survivor (`CONTEXT.md`), and the number falls as the step goes (ADR-268 §2); the clause says so while it is true of the number shown.

### 8. As wide as the browser, and a scroll bar only where a chart would be unreadable

**Every page of the progress page, the four tabs and the label command's, is as wide as the browser's window and never scrolls sideways as a page.** Its head carries `<meta name="viewport" content="width=device-width, initial-scale=1">`, so a phone lays it out at its own width; the stylesheet ADR-267 §4 gives these pages sets `body { max-width: none; }` over the shared one's 900 px, with a margin of 16 px; and no rule of it gives anything a `min-width` but the charts' below, nor a `max-width` but `none` to the body and 600 px to the bar of a running step's count, which a narrower window narrows. Every other page `ReportPage` writes keeps the shared stylesheet as it is.

- **Tiles, the bar of tabs, legends and paragraphs wrap**: the tiles stay ADR-268 §9's grid of columns at least 170 px wide, as many as fit; the bar of tabs, `nav.tabs`, is a row that wraps; a progress line and any text breaks at a space and, where a word is longer than its cell, inside it (`overflow-wrap: anywhere`).
- **Each chart fills its card**: every `<svg>` keeps its viewBox and is `width: 100%; height: auto`, so it widens and narrows with the window and its text with it.
- **Below 917 px a chart stops narrowing and its card scrolls, and only its card.** Every `<svg>` has `min-width: 917px`, the width at which its 12-unit text is drawn 10 px high (`1100 × 10 / 12`, rounded up), and its card, `.chart-card`, has `overflow-x: auto`, so a window too narrow for a chart to be read shows one scroll bar under that chart and none under the page. From a card 917 px wide up there is no scroll bar anywhere. 10 px is the floor because the ticks and end labels are numbers whose digits a reader must tell apart, and below it a `3` and an `8` of the system sans are not told apart at a glance.
- **Each table is in a card of its own, `<div class="table-card">`, with `overflow-x: auto`**, its cells wrapping as above: a table of sixteen columns fits the window's width wherever its numbers fit, and where they cannot, its card, and not the page, scrolls.

A wider window draws each chart larger, its text more than 12 px high: the operator asked for the panels as wide as the browser, and the chart's text grows with it rather than leave the chart a strip in a wide card.

### 9. The modules, and run ids

`ProgressPage` and `ReportPage` of `pipeline` change, and `ledger` gains the reads of §5, whose names, and the shape `ProgressPage` is handed them in, are the implementer's; `VesperaJobConfiguration` hands the page what it reads them through, as it hands it `Verdicts`' counts now. A class added to `pipeline` for the tab is listed in `PipelineHoldsOnlyTheRulesOnRecordTest` as holding no rule: the tab decides no verdict, cache key, cluster or text of the deliverable. **No run id moves**: no stage's version names `pipeline` or `ledger` (ADR-226, `StageModules`). No property is added: the four tabs, the two readings and the hover are fixed, as the operator asked for no setting ("Just use defaults").

### 10. What the operator is told

The README's list of the working directory's files names `progress-runs.html` beside the other three, as `every run this working directory holds, as a table`, in the change that adds its constant, which `docs/check-claims.mjs` reads. Its sentence on `progress.html` says that the progress page is four tabs, `Overview`, `Steps`, `Samples` and `Runs`, that every tab says when the invocation started and, once it has, when it ended, that each chart shows every series' value at the time under the pointer, and that the tab of runs lists every run the working directory holds, read when the invocation starts and when it ends, and that the pages fill the browser's window, a chart scrolling sideways only in a window too narrow to read it.

## Alternatives refused

- **The four tabs drawn inside one page**, switched by `:target` or by radio buttons and CSS. Each reload would rewrite and read all four, the table of samples alone tens of kilobytes; the address would not name the tab, so a reload under `:target` keeps it but one under radio buttons returns to the first; and a link to a tab from outside the page would need a fragment as well as the file.
- **The hover drawn from a JSON object in a `<script>`**, as the script's page did. ADR-267's reason stands: the data would be a second copy inside a script, where HTML escaping does not apply. The attributes are the one copy, escaped.
- **The hover's script only while the invocation runs.** The page that stays open after the last write is the one read longest, and a mark with no `data-written` keeps it from being called stale.
- **The runs read at every write.** A statement for every run every five seconds, through the steps' pool while they write, is what ADR-265 and ADR-268 refused for the counts of one run; the runs change only when a run is minted or a step finishes, and the two readings and `InvocationRuns` show both.
- **The runs read on the page's own thread.** It would take a connection while a step holds one, and in the test profile's pool of one it would wait on the step; the thread that runs the job holds none before the first step and after the last.
- **Survivors a run.** §5: one anti-join over every occurrence a run, twice an invocation.
- **A run's id, or a part of it, on the tab.** ADR-265 §1 keeps run ids off the pages; a walk's number, a stage and an ordinal say which run it is to whoever reads the tab.
- **A run's time of minting.** The `run` table holds none, and none is added for a page: the ordinal says which came first.
- **The readings in a `<details>` below the charts**, newest first. ADR-267's reason stands: a table of up to 256 rows would be most of the page; it is one tab away, and it lists the samples in the order the charts draw them, as their text alternative.
- **A time axis of clock times**, as the script's page drew. Elapsed time is read the same in every zone and across midnight, and the status line now names the start.
- **Survivors and verdicts on one chart**, as the script's page drew, and its word "not dropped". ADR-268 refused both: one axis flattens the kinds, and the vocabulary's word is survivor.
- **No scroll bar at any width, every chart shrinking with the window**, as the operator first asked. At a phone's width of 360 px a chart's 12-unit text is drawn under 4 px high; the operator then asked for the scroll bar where a chart stops being readable.
- **A second, narrower drawing of each chart for narrow windows**, chosen by a media query. Every point and its hover would be written twice, and the build would hold two layouts of every chart; the scroll bar under a chart below 917 px is what the operator asked for.
- **Tables restacked as one card a row on narrow windows.** It needs a label on every cell; a table that does not fit scrolls in its own card, as a chart does.
- **A setting for any of it.** The operator asked for none.

## Consequences

- An operator opens `progress.html`, and moves between `Overview`, `Steps`, `Samples` and `Runs` by the bar at the top of each; each tab says when the invocation started, whether it is live, and when it ended; a pointer over a chart shows every series at that time; and the tab of runs shows every run of every stage the working directory holds, which finished, which this invocation has reached, and what each removed.
- No progress page is kept outside the working directory: the script that kept one is retired, and the four tabs are written by Vespera alone.
- Each invocation of `vespera run` reads the working directory's runs twice, before its first step and after its last, at about a second a million verdicts recorded; no other write reads the database.
- The pages fill the browser's window; a window narrower than about 950 px shows a scroll bar under each chart, and never under a page.
- Each write moves four pages instead of three; every tab of the run carries the one script, the last write too.
- The guard's list of pages an agent may read (ADR-261) names none of the four.
- No schema, cache key, verdict kind, property or run id changes.

## Tests

| Method | What it holds |
| --- | --- |
| `pipeline.ProgressPageTabsTest.everyPageOfTheRunHoldsTheSameFourTabsEachLinkingAPageThatIsThere` | at the first write and the last, each of the four pages holds one bar of tabs, `Overview`, `Steps`, `Samples` and `Runs` in that order, linking `progress.html`, `progress-steps.html`, `progress-samples.html` and `progress-runs.html`, each a file in the working directory |
| `pipeline.ProgressPageTabsTest.eachPageMarksItsOwnTabAndNoOther` | on each of the four pages exactly one link carries `aria-current="page"`, the one to that page |
| `pipeline.ProgressPageTabsTest.everyTabSaysWhetherTheInvocationRunsAndReloadsUntilTheLastWrite` | each of the four tabs, while the job runs, reloads at the interval, says it is running and carries a liveness mark with the write's instant and `Written at`; after the last write none reloads, each says it ended and its mark says `ended` with nothing for a script to read |
| `pipeline.ProgressPageTabsTest.theStatusLineSaysWhenTheInvocationStartedAndWhenItEnded` | a job started at one time and ended an hour later: every tab's first write says it started then and is rewritten every interval, and every tab's last write says it ended completed, started then and ended at the last write's time |
| `pipeline.ProgressPageTabsTest.theLabelPageSaysWhenTheCommandStartedAndWhenItEndedAndHasNoTabs` | the label command's two writes say when it started, and the last when it ended, and neither has a bar of tabs; no tab of runs is written |
| `pipeline.ProgressPageTabsTest.theScriptIsOneFixedTextOnEveryTabAtEveryWrite` | the first and last writes of two invocations at different times over different steps: every tab carries exactly one `<script>`, of one text, which sets text only through `textContent` and never parses markup |
| `pipeline.ProgressPageTabsTest.theLastWriteKeepsTheChartsAndTheHoverAndCarriesNoReload` | the last write of `progress.html` holds the tiles, the timeline and the database chart, carries no reload, carries the one script, and its mark says `ended` with no `data-written` |
| `pipeline.ProgressPageTabsTest.eachPointOfALineChartCarriesTheSeriesTheTimeAndTheValueItsTooltipSays` | every hit circle of the database chart and of a chart of read and written counts carries `data-series` (`size`, `read`, `written`), `data-at` and `data-value` equal to the time and the value its `<title>` says, and the script reads the hit circles and those three attributes |
| `pipeline.ProgressPageTabsTest.everyPageIsAsWideAsTheWindowAndOnlyAChartOrATableScrolls` | every tab and the label page carry the viewport tag; their stylesheet sets the body's `max-width` to `none` and no `min-width` on it, gives no rule a `min-width` but `svg`'s `917px`, draws every `<svg>` `width: 100%; height: auto` with a viewBox, and scrolls sideways only `.chart-card` and `.table-card`, around every chart and every table |
| `pipeline.ProgressPageTabsTest.theRunsAreReadTwiceOnTheJobsThreadAndAtNoOtherWrite` | with the pages rewritten every hundredth of a second over dozens of writes, the runs are read exactly twice, both on the thread that runs the job, and the last write's tab shows the reading made as it ended |
| `pipeline.ProgressPageTabsTest.aReadingOfTheRunsThatFailsFailsNothingAndIsWarnedOfOnce` | a reading that throws at the start and at the end: the first write's tab says when the runs are read and holds no table, the last says it shows the reading as the invocation ended and that the runs could not be read, one warning names the exception's class, no page holds its message, and the progress page ends `completed` |
| `pipeline.ProgressPageRunsInvocationTest.afterTwoInvocationsTheRunsTabListsEveryRunTheWorkingDirectoryHolds` | after two invocations over one root, the second under another boilerplate floor so that it mints a second run of content redundancy, the tab's header row is §5's and its rows are every run of the ledger, in §5's order, each with its walk, stage, ordinal, steps finished of the table's, state and verdicts by kind as the ledger holds them |
| `pipeline.ProgressPageRunsInvocationTest.theRunsTabMarksTheRunsThisInvocationArrivedAt` | after the same two, the runs the second invocation minted and the stage-1 run it continued are `arrived at`, the run of content redundancy the first minted is not, no stage has two runs arrived at, and no run of another walk is |
| `pipeline.ProgressPageRunsInvocationTest.whileTheInvocationRunsTheRunsTabIsTheReadingMadeAsItStarted` | while the second invocation is held at the start of content redundancy's work, its run already minted, the tab says it shows the runs held when the invocation started, lists exactly the runs the first left, and marks the stage-1 run `arrived at`; its last write says it shows the runs held when it ended and lists the new run too |
| `pipeline.ProgressPageRunsInvocationTest.theRunsTabNamesNoRunIdNoPathAndNoTextOfADocument` | after the two invocations, no run id of the ledger, no marked name or text, root, seed folder or working directory is on the tab |
| `pipeline.ProgressPageOutcomesInvocationTest.aSampleInTheMiddleOfATaskletCountsItsVerdicts` | amended: while byte-level reduction runs, the `survivors` tile's detail is `of 3 when byte-level-reduction started, including those not yet judged` |

ADR-267's `ProgressPageChartsTest.theScriptIsOneFixedTextOnEveryPage`, which held that the pages of detail carry no script, and `ProgressPageChartsTest.theLastWriteKeepsTheChartsAndCarriesNoReloadAndNoScript`, which held that the last write carries none, are replaced by `theScriptIsOneFixedTextOnEveryTabAtEveryWrite` and `theLastWriteKeepsTheChartsAndTheHoverAndCarriesNoReload`. `ProgressPageChartsTest.theTablesAreOnTwoPagesOfTheirOwnThatTheProgressPageLinks` and `ProgressPageInvocationTest.thePageNamesNoPathAndNoTextOfADocument` are unchanged: the bar of tabs holds the links the first reads, and the second reads the three pages it named. `EveryStatementThatSortsIsRecordedTest` and `EachTableIsNamedOnlyByItsOwnerTest` hold the reads of §5 as they hold every statement of `ledger`, and `EveryPageIsMovedIntoPlaceByTheMoveThatTriesAgainTest` the fourth page's move.

## What this does not decide

- **The look beyond what §1 and §4 fix**: the bar's spacing, the tooltip's placement and shape, the crosshair's dash and width, and the words of the tabs' `aria-label`, are the renderer's.
- **Whether an agent may read the pages**, which stays a record of its own (ADR-265).
- **A count of survivors a run**, should one ever be cheap: a record of its own, with its measurement.
