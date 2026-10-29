# ADR-324 — The timeline of the steps folds into four legs, each one bar, open where the job is, and a fold outlives the reload in the page's address

> **Partly amended — see [ADR-327](0327-an-ended-progress-page-keeps-reloading-so-a-tab-left-open-shows-the-next-invocation-and-vespera-open-browser-set-false-opens-nothing.md).** Of §5: the script schedules its reload where the liveness mark carries `data-interval`, which the last write's mark carries too, and the last write carries the `<noscript>` refresh as every write before it. A fold outlives the invocation as it outlives the reload. The rest of this record stands.

- **Date**: 2026-10-23
- **Status**: accepted on 2026-10-23.
- **Decided**: by the operator, who asked that the bars of the timeline fold, on `progress.html` and in the control panel, into four named deduplication, translation, clustering and generation, each folding to one bar, and who approved a drawing of four rows, one open on its steps; which steps each holds, the bar's span and state, which is open, how a fold is kept across the page's reload and what the control panel shows are decided under delegation.
- **Amends**: [ADR-267](0267-the-progress-page-draws-tiles-a-timeline-of-the-steps-and-charts-over-time-and-its-tables-are-pages-of-their-own.md) §1, *"A timeline of the steps: one `<svg class="timeline">` holding, for each of the fifteen steps …"*: the timeline is one card of nine drawings, a time axis and two for each of the four legs of §1 below, each leg a `<details>` holding its own bar and a drawing of its steps; every step's `<g class="step">`, its bar, its state and its `<title>` stay as ADR-267, ADR-268 §8 and ADR-275 §4 have them, inside its leg (§2). Its §4, *"The meta refresh of ADR-265 §1 is how the pages are rewritten in the browser while the invocation runs"*: for the four tabs of `vespera run`, a browser that runs scripts is reloaded by the page's script, and the meta refresh is in `<noscript>` for one that does not (§5).
- **Amends**: [ADR-268](0268-the-progress-page-shows-what-the-stage-that-removes-occurrences-has-removed-and-draws-to-the-data-viz-method.md) §8, *"The timeline stays one `<svg class="timeline">`"*, and its caption: the card keeps its `<h2>Timeline of the steps</h2>` and gains §2's caption, and its drawings are §2's. The rest of §8, each step's name, bar and state word beside the bar, and the time axis titled `Elapsed (h:mm:ss)`, stands.
- **Amends**: [ADR-269](0269-the-progress-page-is-four-tabs-draws-a-hover-over-each-chart-and-lists-the-runs-the-working-directory-holds.md) §3, the script's *"two things"*: it does four, reloading a tab while the invocation runs (§5), the check of the liveness mark, the hover of ADR-269 §4, and keeping the folds in the page's address (§5). Its §1, *"each reloads at the interval while the invocation runs and none after the last write"*, stands, by the script where scripts run.
- **Amends**: [ADR-265](0265-vespera-run-rewrites-a-progress-page-in-the-working-directory-while-it-runs.md) §1, *"While the invocation runs, the page's head carries `<meta http-equiv="refresh" content="<S>">`"*: on the four tabs of `vespera run` the element is inside `<noscript>` (§5); `vespera label`'s page carries it as before.
- **Keeps**: ADR-265 §1's five states, which every leg's state is one of, and its rule that the pages name no path, no title and no text of a document, no exception message and no run id; ADR-265 §4, a write that fails fails nothing; ADR-267 §2's samples and §7, every `<svg>` an image with a `<title>` as its first child, and `progress-steps.html` the text alternative of every bar; ADR-268 §9's palette, whose status colours colour a leg's bar as they colour a step's; ADR-269 §1, four tabs each a page of its own, §3, one constant script holding no data of an invocation, writing text only through `textContent` and parsing no markup, §4, no hover on the timeline beyond each bar's `<title>`, and §7, no `min-width` but the drawings' `917px`; [ADR-275](0275-every-div-of-every-page-vespera-writes-says-on-hover-what-it-shows-and-every-step-of-the-timeline-what-it-does.md) §4, each step's description its mark's first `<title>`; [ADR-302](0302-the-control-panel-is-a-launch-page-and-an-invocation-page-of-server-written-forms-each-report-re-served-read-only-in-a-sandbox-and-the-next-invocations-values-on-pages-of-their-own.md) §3, each report re-served as written under `sandbox allow-scripts`; [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md), the control panel links no class of the jar; [ADR-130](0130-one-report-page-module-owns-the-skeleton-styling-escaping-and-tables.md), one module assembles every page, self-contained; [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md), no dependency a decision does not require; [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md), no stage's version names `pipeline`.

## Context

**The timeline today** is the card `Timeline of the steps` on the `Overview` tab: one `<svg class="timeline">` of sixteen rows, one `<g class="step">` a step in job order, each with its name, its state's word and, where it started, a bar from its start to its end on one axis of time from the job's start to the write (ADR-267 §1, ADR-268 §8). Drawn at its card's width it is about 430 units tall, so on a window of ordinary height the charts below it begin out of sight, and a run of hours shows a column of finished steps above the one that is running.

**The operator asked** for the bars to fold, in `progress.html` and in the control panel, into deduplication, translation, clustering and generation, and approved this drawing:

```
▸ deduplication   [██████] finished
▾ translation
    seed-extraction   [██]
    translation       [███]
▸ clustering      [████] running
▸ generation      not started
```

**The words.** `CONTEXT.md`'s entry for a stage rejects *phase*, and the four are not stages: deduplication spans stages 0 to 4, and translation and clustering divide stage 5. No entry rejects *deduplication*: *duplicate* is rejected as a name for a content identity and for a copy, and names neither here; and the four names are words the page renders for the operator, which ADR-122 leaves free. The vocabulary gains **leg** for one of the four.

**The control panel** shows no timeline of its own. Its invocation page holds the child's state, its output's tail, the last line, a link to `/report/progress.html` and the reports (ADR-302 §2); Stop's confirmation holds a table of the sixteen steps with what a stop at each costs (`InvocationPages.STOP_COSTS`, ADR-302 §7). The progress page it shows is the file in the working directory, re-served as written at `/report/progress.html` under `Content-Security-Policy: sandbox allow-scripts` (ADR-302 §3): the page's script runs, in an opaque origin. A document of an opaque origin is refused `localStorage` and `sessionStorage`, each throwing a `SecurityError` (HTML Standard, Web storage), and holds no cookie of the server's (ADR-302's Context); a browser opening the file from the working directory gives a `file:` page an origin of its own choosing, opaque in some. The address's fragment is the document's own in both: a script reads it from `location.hash` and changes it by a navigation within the same document, which no sandbox flag refuses, and no fragment is ever sent in a request.

**How a tab is reloaded.** While the invocation runs, every tab's head carries `<meta http-equiv="refresh" content="<S>">` (ADR-265 §1). The address that refresh navigates to is the document's address when the element was processed, as the page loaded (HTML Standard, shared declarative refresh steps), so a fragment written after the page loaded is not in it; and a navigation to the document's own address with a fragment is, by the standard's navigate algorithm, a navigation within the document and not a reload. `location.reload()` reloads the document at its current address, the fragment included. Neither was measured in a browser; §5 is written so that nothing rests on how a browser treats a refresh to an address with a fragment.

## Decision

### 1. Four legs

**The timeline folds the sixteen steps into four legs, each the steps of a run of the job in job order, every step in exactly one**:

| Leg | Its steps, in job order | Where it lies in the cascade |
| --- | --- | --- |
| `deduplication` | `census`, `byte-level-reduction`, `extraction`, `content-census`, `redundancy-signature`, `content-redundancy` | stages 0 to 4 |
| `translation` | `seed-extraction`, `seed-corpus-comparison`, `embedding-scoring`, `translation` | stage 5, from the seeds to the translation step |
| `clustering` | `relevance-scoring`, `relevance-floor`, `clustering`, `relevance-report` | stage 5, after the translation step |
| `generation` | `arrangement`, `generation` | stages 6a and 6b |

Each boundary but one is a stage's. The one inside stage 5 lies where the operator's names put it, after the translation step. **The relevance report is clustering's**: it is stage 5's, writing the page the relevance threshold is chosen from (`StepNames`), and generation is stage 6 and nothing else. A leg's name is the one the operator gave it, rendered on the page as it is.

**The legs are a constant of `ProgressPage`**, beside the steps in job order: they are how the page draws, and no other class reads them. Three of the four names, `translation`, `clustering` and `generation`, are also the names of steps, so the constant reads them from `StepNames`' constants and writes none as a literal: `RunsAndNamesHaveOnePlaceTest` fails on a source of `pipeline` outside `StepNames` and `StageModules` that writes a persisted step name, whatever it names. `deduplication` names no step and is written as it is. A step added to the job is put into a leg in the change that adds it; until it is, it is on no leg, and `EveryStepSaysWhatItDoesTest` fails, as it does for a step of `StepNames` the timeline does not draw.

### 2. What the card holds

**The card stays one `<figure class="chart-card">` headed `<h2>Timeline of the steps</h2>`, and holds, in this order, its caption, the time axis, and the four legs in the table's order**:

```html
<figure class="chart-card">
<h2>Timeline of the steps</h2>
<p class="caption">The steps fall into four parts. Each part's bar runs from the start of its first step to the end of its last, or to this write while it runs, and is labelled with its state; open a part to see each step's bar.</p>
<svg class="timeline-axis" role="img" viewBox="0 0 1100 <H>"><title>Timeline of the steps</title>…</svg>
<details class="leg" data-leg="deduplication">
<summary><svg class="timeline-leg" role="img" viewBox="0 0 1100 <H>"><title>deduplication: finished</title><g class="leg-span" data-state="finished"><text …>deduplication</text><text class="state" …>finished</text><rect …><title>deduplication: finished, 0:41:07</title></rect></g></svg></summary>
<svg class="timeline" role="img" viewBox="0 0 1100 <H>"><title>Steps of deduplication</title><g class="step" data-step="census" data-state="finished">…</g>…</svg>
</details>
<details class="leg" data-leg="translation" open>
…
</details>
…
</figure>
```

- **The time axis is drawn once, above the legs**: `<svg class="timeline-axis">`, its `<title>` the card's heading, holding the ticks `0:00:00` and the time elapsed at the write and the title `Elapsed (h:mm:ss)`, as ADR-268 §8 has them under the single drawing. Above the legs it stays in one place whatever is folded.
- **A leg is `<details class="leg" data-leg="<leg>">`**, with `open` after `data-leg` where §4 opens it. Its first child is its `<summary>`, holding one drawing, `<svg class="timeline-leg">`, whose `<title>` is `<leg>: <state>` and which holds one `<g class="leg-span" data-state="<state>">`: the leg's name, its state's word in `<text class="state">`, and its bar where §3 draws one. After the summary, one `<svg class="timeline">` titled `Steps of <leg>` holds the leg's steps, each `<g class="step">` as ADR-267 §1, ADR-268 §8 and ADR-275 §4 have it, in job order.
- **Every drawing of the card has a `viewBox` 1,100 units wide and the steps' mapping of time**: the leg's bar, every step's bar and the axis's ticks put an instant at the same horizontal place, so that drawn at the card's width they line up. Heights, rows and spacing are the renderer's.
- **A browser that runs no script folds and unfolds each leg by its summary**: `<details>` is the fold, and nothing of it needs the script.
- **While a leg is open its summary shows its name and nothing else**, as the drawing the operator approved shows it: the stylesheet hides the bar and the state's word of a `details.leg[open]`'s summary, and its steps, below it, show where the leg is.

### 3. A leg's bar and its state

**A leg's bar runs from the earliest start of its steps to the latest end**, a step that has started and not ended ending at the write, as a running step's bar does; a step not started counts for nothing. It is drawn by the steps' mapping, at least as wide as a step's shortest bar, filled by its state's colour through the `g[data-state]` rules of the stylesheet. Its `<title>` is `<leg>: <state>, <H:MM:SS>`, `<H:MM:SS>` the time from that start to that end. **A leg none of whose steps has started has no bar**, as a step not started has none.

**A leg's state is one of ADR-265 §1's five, read off its steps' states**, at the write, by the first rule that holds:

| A leg is | Where |
| --- | --- |
| `not started` | none of its steps has started |
| `finished` | every one of its steps is `finished` |
| `failed` | any one of its steps is `failed` |
| `stopped at a gate` | any one is `stopped at a gate` |
| `running` | any one is `running` |
| `running` | on a write before the last: some of its steps are `finished` and the rest `not started`, the job being between two of them |
| `failed` | on the last write, the same: the job ended before the leg's next step started, which only a job that did not complete does |

**A failure outranks a gate, and a gate outranks a step running**: a leg holding a failed step is read for the failure, and one stopped at a gate, the job having gone on past the gate, says so rather than say `running` while a later step of it runs. A leg is `finished` only when every step of it is; a leg some of whose steps ended at a gate and the rest finished is `stopped at a gate`. The `Steps finished` tile and the `step` tile are unchanged (ADR-267 §1, ADR-268 §5).

### 4. Which legs are open

**A leg is written `open` where its state is `running` or `failed`, or where it holds the first step in job order shown `stopped at a gate`; every other leg is written folded.** So while the job runs the leg it is in is open and the others are one bar each; the leg where a job failed is open on the page that stays after it; and the leg where the job first met a shut gate is open, while the later legs, whose steps stopped at the same gate's consequence, are folded and say `stopped at a gate` on their bar. A job whose every step finished shows four folded legs, four bars.

### 5. How a fold outlives the reload

**The page's address keeps what the operator folded and opened, in its fragment**, `#open=<legs>&folded=<legs>`, each `<legs>` the names of legs separated by `,`, either part absent or empty: `#open=deduplication&folded=clustering`. A leg the fragment names is drawn as it says; a leg it does not name is drawn as §4 writes it. So a leg the operator opened stays open, one they folded stays folded, and a leg they left alone opens when the job reaches it and folds when it finishes.

**The script does it, from the page's own attributes**:

- **as the page loads**, it reads `location.hash` and, for each `details.leg`, sets `open` where the fragment's `open=` names its `data-leg` and clears it where its `folded=` does;
- **when the operator opens or folds a leg by its summary**, it records that leg as opened or folded and writes the fragment with `location.replace`, a navigation within the document that adds no entry to the browser's history and no request;
- **on every tab, while the fragment holds `open=` or `folded=`**, it carries the fragment onto each link of the bar of tabs, by its `href` attribute, so that `Steps` and back to `Overview` keeps the folds;
- a call that throws is caught and does nothing more, so a fold the address could not keep lasts until the next reload, and the rest of the script runs.

It reads no leg's name but from `data-leg`, holds none of an invocation's data (ADR-269 §3), writes no text but through `textContent` and sets no markup. **It uses no storage**: no `localStorage`, `sessionStorage`, IndexedDB or cookie, which the control panel's sandbox refuses (Context). The fragment is never sent, so the control panel answers the same request with the same file.

**While the invocation runs, a tab that runs scripts is reloaded by its script, and only a browser that runs none by the meta refresh.** The script's first statement, where the liveness mark carries `data-written`, calls `location.reload()` once, `<S>` seconds after the page loaded, `<S>` being ADR-265 §1's, `data-interval` in whole seconds rounded up and at least 1; a reload keeps the address, its fragment included. The head of each of the four tabs carries ADR-265's element inside `<noscript>`:

```html
<noscript><meta http-equiv="refresh" content="<S>"></noscript>
```

so a browser that runs scripts never processes it, and one that runs none reloads as before. The last write carries neither (ADR-265 §2). `vespera label`'s page, which carries no script (ADR-267 §6), carries the element as ADR-265 §1 has it. Placed first, the reload is scheduled before anything else the script does can fail.

**Where no script runs**, each leg folds and unfolds by its summary, and every reload draws the legs as §4 writes them: the fold lasts until the next write is shown.

### 6. The control panel

**The control panel shows the folds by showing the file**: `/report/progress.html` is `progress.html` as written (ADR-302 §3), so its legs, its script and its fragment are the working directory's page's. Under `sandbox allow-scripts` the script runs; the fragment is the document's own; the reload is the script's `location.reload()`; and nothing of §5 asks for storage or a request the sandbox refuses. **Nothing of `io.algernon.controlpanel` changes.**

**Its own pages get no legs.** The invocation page draws no step: it shows the child, its output and a link to the progress page. **Stop's table of the sixteen steps stays one row a step**: it is read once, to learn what a stop at the step running costs, and every row of it is the answer to that question for one step; folding it would hide the row asked for behind a click. And the control panel links no class of the jar (ADR-285), so legs on its pages would be a second copy of §1's table, free to drift from the first.

### 7. Text alternatives, colour and width

- **Every drawing of the card is an image with a `<title>` as its first child** (ADR-267 §7): the axis titled with the card's heading, a leg's summary with its name and state, a leg's steps `Steps of <leg>`. `progress-steps.html` stays the text alternative of every step's bar, and a leg's bar adds nothing a reader of that table lacks.
- **A leg's state is never told by colour alone**: its word is beside its bar, as a step's is (ADR-268 §8).
- **No rule gives anything a `min-width` but `svg`** (ADR-269 §7): every drawing of the card, `svg.timeline-axis`, `svg.timeline-leg` and `svg.timeline`, fills its width and keeps the 917 px floor, the card scrolling sideways below it. The floor is set by `min-width`, the property ADR-269 §7 names, and by no synonym of it such as `min-inline-size`.
- **The look beyond what §2 fixes** is the renderer's: the summary's marker, the rows' heights, the gap between legs, and how the stylesheet hides an open leg's bar.

### 8. The modules, and run ids

Only `ProgressPage` and `ReportPage` of `pipeline` change, and neither gains a rule: the legs, their states and their folds decide no verdict, cache key, cluster or text of the deliverable. A class the implementer adds to `pipeline` for them is listed in `PipelineHoldsOnlyTheRulesOnRecordTest` as holding none. **No run id moves**: no stage's version names `pipeline` (ADR-222). No property is added: the legs, the rule of §4 and the fragment are fixed, as the operator asked for no setting.

### 9. What the operator is told

The README's sentence on `progress.html`, in the change that builds this, says that the timeline folds the steps into four parts, deduplication, translation, clustering and generation, each one bar from its first step's start to its last step's end with its state, that the part the job is in, or where it failed or first stopped at a gate, is open, that a part the operator opens or folds stays so as the page reloads, and that the same holds on the progress page the control panel shows.

## Alternatives refused

- **The one drawing kept, its rows hidden by the script.** A browser that runs no script would fold nothing; the script would move every row below a fold on every toggle, a layout the build could not read; and the page would have no fold at all where its script fails.
- **The fold kept in `localStorage` or `sessionStorage`.** Both throw in the control panel's sandbox, its origin being opaque, and a `file:` page's origin is the browser's choice; the fold would be kept on one of the two places the operator reads the page and not the other.
- **The fold kept in `window.name` or a cookie.** A browser empties `window.name` on a navigation between sites, which a reload between two opaque origins is; a sandboxed page holds no cookie, and one it could set would be sent to the control panel with every request.
- **The fragment, with the meta refresh left as it is.** The refresh navigates to the address the page loaded with, so a fold made after the page loaded would be undone by the next reload, and a refresh to an address with a fragment is, by the standard, no reload at all.
- **The fragment holding the whole set of open legs.** A leg the job reaches after the operator's first fold would stay as the fragment had it, so the leg running would stay folded; recording only what the operator changed lets §4 go on opening the leg the job is in.
- **The time axis below the legs**, where the single drawing had it. It would move as each leg opens and folds.
- **The relevance report in generation.** It is stage 5's and writes the page the relevance threshold is chosen from; generation is stage 6, the arrangement and the writing.
- **A leg for every stage.** Seven folds, four of one step, where the operator named four.
- **The legs on Stop's table in the control panel**, or on the `Steps` tab's table. Stop's table answers what a stop at one step costs (§6); the `Steps` tab is the text alternative of every bar, and a table of sixteen rows is read whole.
- **A setting for the legs, their names or which is open.** The operator asked for none.

## Consequences

- An operator who opens `progress.html`, from the working directory or from the control panel, sees four bars, one a leg, each saying its state, and the leg the job is in, or where it failed or first met a shut gate, open on its steps; the charts below the card are on the first screen of an ordinary window.
- A leg the operator opens or folds stays so across reloads and across the tabs, in a browser that runs scripts, whether the page is a file or the control panel's; the page's address shows it, and an address copied with its fragment opens with the same folds.
- While the invocation runs, a tab is reloaded by its script where scripts run; a page whose script does not run is reloaded by its `<noscript>` refresh.
- Each write of `progress.html` draws nine drawings in the card where it drew one, and four `<details>`; the other tabs and the label page are unchanged but for the `<noscript>` around the four tabs' refresh.
- The guard's list of pages an agent may read (ADR-261) names none of the four tabs.
- No schema, cache key, verdict kind, property, run id or class of the control panel changes.

## What this does not decide

- **The look beyond §2 and §7**, as §7 says.
- **A description on hover for a leg**, as ADR-275 §4 gives each step: a leg's summary says its name and state, and its steps say what each does.
- **Whether an agent may read the pages**, which stays a record of its own (ADR-265).

## Tests

| Method | What it holds |
| --- | --- |
| `pipeline.ProgressPageTimelineLegsTest.theTimelineFoldsTheSixteenStepsIntoFourLegsInJobOrder` | §1, §2: the card holds four `<details class="leg">`, `deduplication`, `translation`, `clustering`, `generation` in that order, each opening on a `<summary>` that holds one drawing of the leg and no step, then one drawing titled for the leg holding exactly §1's steps in job order; the four together are the sixteen steps, once each |
| `pipeline.ProgressPageTimelineLegsTest.aLegsBarRunsFromItsFirstStartToItsLastEnd` | §3: while extraction runs, deduplication's bar starts where the census's does and ends where extraction's does, at the write, titled with its state and the time between; after extraction failed, it ends where extraction's bar ends and says `failed` and that time; a leg none of whose steps started has no bar |
| `pipeline.ProgressPageTimelineLegsTest.aLegsStateIsAFailureThenAGateThenAStepRunning` | §3: one leg of finished steps is `finished`; one of finished steps and steps stopped at a gate is `stopped at a gate`; one with a step running beside a finished one is `running`; one with a failed step beside a gated one is `failed`; one with no step started is `not started`; each with its word beside its bar and its `data-state` |
| `pipeline.ProgressPageTimelineLegsTest.aLegBetweenTwoOfItsStepsIsRunningUntilTheLastWrite` | §3: with two steps of deduplication finished and the rest not started, deduplication is `running` while the job runs and `failed` on the last write of a job that failed |
| `pipeline.ProgressPageTimelineLegsTest.theLegRunningFailedOrFirstStoppedAtAGateIsOpenAndTheOthersFolded` | §4: a job first stopped at a gate in translation, its later steps stopped too, opens translation alone; a job running in clustering opens clustering alone; a job that failed in deduplication opens deduplication alone; a job whose every step finished opens none |
| `pipeline.ProgressPageTimelineLegsTest.theTimeAxisIsDrawnOnceAboveTheLegsAndEveryDrawingSharesItsWidth` | §2: the card titles one time axis `Elapsed (h:mm:ss)`, drawn before the first leg, titled with the card's heading, and every drawing of the card has a viewBox 1,100 units wide |
| `pipeline.ProgressPageTimelineLegsTest.theScriptKeepsTheFoldsInTheAddressReloadsTheTabAndUsesNoStorage` | §5: the one script reads `location.hash`, writes it with `location.replace`, reloads with `location.reload()` before it does anything else, and names no `localStorage`, `sessionStorage`, `indexedDB`, `document.cookie` or `history.pushState`; and holds no backslash, so that one escape written outside a string cannot stop the whole script, its reload with it, parsing |
| `pipeline.ProgressPageTabsTest.everyPageIsAsWideAsTheWindowAndOnlyAChartOrATableScrolls` | §7: on every tab and the label page, the rules giving a `min-width` are exactly `svg.timeline-axis`, `svg.timeline-leg` and `svg.timeline` at 917 px and `svg.chart` at 500 px, and no rule declares `min-inline-size` |
| `pipeline.ProgressPageTimelineLegsTest.aTabOfTheRunRefreshesWithoutAScriptOnlyAndTheLabelPageAsBefore` | §5: while the job runs each of the four tabs carries one meta refresh, at the interval, inside `<noscript>`; `vespera label`'s page carries its refresh outside any `<noscript>` |

ADR-267's `ProgressPageChartsTest.theTimelineHasOneMarkPerStepInJobOrderWithItsState`, and ADR-268's `ProgressPageReadabilityTest.theTimelineSaysEveryStepsStateInWords` and `ProgressPageReadabilityTest.everyDrawingHasAVisibleTitleACaptionAndAxisTitles`, read the timeline's card from its heading to its close, and the last counts the cards drawn rather than the drawings, three, each opening on its heading and caption and a drawing titled as its heading: what they hold holds of one drawing and of §2's nine alike. `EveryStepSaysWhatItDoesTest.everyStepOfTheTimelineSaysWhatTheStepDoes` reads every step's mark wherever it lies, and is unchanged. `ProgressPageTabsTest.everyPageIsAsWideAsTheWindowAndOnlyAChartOrATableScrolls` changes as the table above says: it listed `svg.timeline` and `svg.chart` alone, and read `min-width` alone, so a floor on the card's two new drawings, or one set by a synonym, would have passed it unread. `controlpanel.ReportScreenTest.eachReportIsServedAsWrittenInASandboxAtItsOwnPath` holds §6: the page is served as written, under `sandbox allow-scripts` and no other flag.
