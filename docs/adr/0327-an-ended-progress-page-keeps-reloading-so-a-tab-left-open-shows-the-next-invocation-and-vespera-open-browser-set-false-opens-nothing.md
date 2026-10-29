# ADR-327 — An ended progress page keeps reloading, so a tab left open shows the next invocation, and `vespera.open-browser` set false opens nothing

- **Date**: 2026-10-29
- **Status**: accepted on 2026-10-29.
- **Decided**: by the operator, who chose that a progress page keeps reloading after its invocation has ended, still saying plainly that it has ended, and that a setting stops an invocation from opening the browser, which opens as before unless it is set. The interval after the end, the setting's name, form and reach, the script, the control panel and a page whose file is gone are decided under the operator's delegation of 2026-10-29.
- **Amends**: [ADR-265](0265-vespera-run-rewrites-a-progress-page-in-the-working-directory-while-it-runs.md) §1, *"The last write is rendered with `ReportPage.render` and carries no reload"*, and §2, *"Once more when the job ends … with the outcome of §1 and no reload"*: the last write reloads at the interval as every write before it does (§1 below).
- **Amends**: [ADR-266](0266-every-invocation-names-its-progress-page-first-and-opens-it-and-vespera-label-writes-it-too.md) §2, *"There is no setting: the defaults decide"* and *"A second invocation opens it again, in a new tab or window: the tab the last one opened holds a page with no reload"*, and its alternative refused *"A setting to turn opening off"*: `vespera.open-browser`, true unless set, opens nothing where it is false, and the tab the last invocation opened goes on reloading (§1, §5); §3, *"when the command ends, however it ends, with `ReportPage.render` and no reload"*: the label command's last write reloads too (§1); and §5, whose README sentence §7 below extends.
- **Amends**: [ADR-267](0267-the-progress-page-draws-tiles-a-timeline-of-the-steps-and-charts-over-time-and-its-tables-are-pages-of-their-own.md) §3, *"the last write renders it with no reload"*, and §4, *"the last write carries none"*: the pages of detail and `progress.html` reload after the last write too (§1); and §5's last mark, *"`<span class="liveness">ended</span>`"*, which carries `data-interval` (§2).
- **Amends**: [ADR-269](0269-the-progress-page-is-four-tabs-draws-a-hover-over-each-chart-and-lists-the-runs-the-working-directory-holds.md) §1, *"each reloads at the interval while the invocation runs and none after the last write"*: each reloads after the last write too (§1); §2, whose two strings after the end each gain one sentence (§2); and §3, the last write's mark *"with no attribute"*, which carries `data-interval` and still no `data-written` (§2, §3).
- **Amends**: [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md) §7, *"and no setting turns it off"*, and its Decided here 9, *"The index opens by default (§7), as the progress page does, with no setting"*: `vespera.open-browser` set false opens no index either (§5).
- **Amends**: [ADR-324](0324-the-timeline-of-the-steps-folds-into-four-legs-each-one-bar-open-where-the-job-is-and-a-fold-outlives-the-reload-in-the-pages-address.md) §5, *"The script's first statement, where the liveness mark carries `data-written`, calls `location.reload()`"*: it does so where the mark carries `data-interval`, which the last write's mark carries too (§3); and *"The last write carries neither (ADR-265 §2)"*: the last write carries the `<noscript>` refresh as every write before it (§1).
- **Keeps**: ADR-265 §1's rule that the pages name no path, no title and no text of a document, no exception message and no run id; §2's writes, each moved over its place in one move ([ADR-223](0223-5fs-size-report-6a-and-6b-go-through-one-seed-partition-at-a-time-and-what-is-still-held-says-why.md) §3 and §6); and §4, a write that fails fails nothing. ADR-266 §1, the command's first line names the page; §2's `PageOpener`, asked once in an invocation and after the first write, `SystemPageOpener`'s rule that nothing is opened where `CI` is set or, off Windows and macOS, where no display is, and that opening never fails an invocation. ADR-267 §5's check of the liveness mark, stale after three intervals. ADR-269 §3, one constant script holding no data of an invocation. ADR-324 §5's fragment, its `<noscript>` refresh and its reload scheduled first; and §6, nothing of `io.algernon.controlpanel` changes. [ADR-325](0325-the-control-panel-opens-the-browser-itself-at-a-fixed-port-and-a-tab-of-its-last-start-comes-back-to-the-next-in-place-of-a-new-one.md) §7, the control panel's own opener and its refusal of a switch that turns opening off, and §8, its child runs with `CI=1` and the two openings do not meet. [ADR-302](0302-the-control-panel-is-a-launch-page-and-an-invocation-page-of-server-written-forms-each-report-re-served-read-only-in-a-sandbox-and-the-next-invocations-values-on-pages-of-their-own.md) §3, each report re-served as written. [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md), no stage's version names `pipeline`.
- **Settles**: [#664](https://github.com/algernon28/vespera/issues/664).

## Context

**Today a page stops reloading when its invocation ends.** While an invocation runs, each of the four tabs of `vespera run` is reloaded every `<S>` seconds by its script's `location.reload()`, and by a `<meta http-equiv="refresh">` inside `<noscript>` in a browser that runs no script (ADR-324 §5); `vespera label`'s page, which carries no script, by a plain `<meta http-equiv="refresh">` (ADR-266 §3). `<S>` is `vespera.progress-interval` in whole seconds, rounded up and at least 1, `5s` as shipped (ADR-265 §1). The last write carries no refresh, and its liveness mark, `<span class="liveness">ended</span>`, carries no `data-written`, so the script schedules no reload for it (ADR-269 §3, ADR-324 §5). A page whose invocation was killed before its last write is the exception: its mark still carries `data-written`, so it reloads at `<S>` for as long as its tab is open, and is marked stale after three intervals (ADR-267 §5).

**So a tab left open never shows the next invocation.** Every `vespera run` and `vespera label` opens `progress.html` once, in a new tab or window (ADR-266 §2), and every invocation that writes the deliverable's tree opens its `index.html` once more (ADR-274 §7), drawing a tree already recorded included (ADR-274 §6). An operator who runs Vespera many times in a day is handed a new tab each time, while the tab from the last invocation shows a page that has ended.

**A page opened from a file can tell nothing to the next invocation.** The control panel's tab comes back to its next start by asking the server at a fixed port (ADR-325 §4); a `file:` page makes no request that a jar answers, and the jar runs no server. Nor can a `file:` page ask whether a file is still there: Chromium refuses a page's `fetch` and `XMLHttpRequest` of a `file:` address, and Firefox gives each file an origin of its own, so a page reads no other file and not its own; this is from the browsers' published rules, not measured. A reload of a `file:` address whose file is gone shows the browser's own page for a missing file, which runs none of the page's script and carries no refresh.

**The control panel shows the same file.** `/report/progress.html` is read from the working directory at each request and served as written, under `sandbox allow-scripts` (ADR-302 §3), so the page's script and refresh run there as in a tab of the file; where the working directory holds no such file, the server answers `404` with one line of text, `the working directory holds no progress.html`, which carries neither (`ReportPages`). The jar the control panel starts runs with `CI=1`, so under the control panel the jar opens nothing (ADR-325 §8).

**How a setting of this kind is read.** `vespera.sidecars.start` is an application property and no profile value, true unless set, which an operator turns off as `VESPERA_SIDECARS_START=false` in the environment (ADR-323 §9). Spring Boot binds an environment variable to a property whose name it spells in capitals with `.` and `-` as `_`, as `VESPERA_DELIVERABLE_ROOT` names `vespera.deliverable-root`; a property is also set in `application-local.yaml` or as `--vespera.<name>=<value>` on the command line.

## Decision

### 1. The last write reloads at the same interval

**Every page an invocation writes for the last time reloads at `<S>`, as each of its writes before did**, `<S>` being ADR-265 §1's interval of the invocation that wrote it:

| Page | While the invocation runs | At its last write |
| --- | --- | --- |
| each of the four tabs of `vespera run`, `progress.html`, `progress-steps.html`, `progress-samples.html` and `progress-runs.html` | the script's `location.reload()`, and `<noscript><meta http-equiv="refresh" content="<S>"></noscript>` in the head (ADR-324 §5) | the same |
| `vespera label`'s `progress.html`, with or without `--auto` | `<meta http-equiv="refresh" content="<S>">` in the head, outside any `<noscript>` (ADR-266 §3) | the same |

So the head of a last write is the head of every write before it, and a browser that has any of these pages open goes on reloading it until its tab is closed. The next invocation in the same working directory rewrites the same files (ADR-265 §2, ADR-266 §3), so a tab left open shows that invocation's page within `<S>` of its first write, with no new tab. Which methods of `ReportPage` render a last write are the implementer's.

**The same interval, and not a slower one.** A page left by an invocation killed before its last write already reloads at `<S>` for as long as its tab is open (Context), so a page reloading after its process is gone is a cost already borne. A slower interval would show `This invocation has ended` for up to that long after the next invocation's first write, and that page is the one the operator asked not to be left looking at. And one interval needs no second setting. An operator who wants an ended page reloaded less often sets `vespera.progress-interval` longer, for the invocations that follow.

### 2. The ended page says it has ended, and why it reloads

**The line saying whether the invocation runs or has ended gains one sentence after the end**, `<outcome>`, `<start>`, `<end>` and `<S>` as ADR-265, ADR-266 and ADR-269 §2 have them:

| Page | After the end |
| --- | --- |
| `vespera run`'s four tabs | `This invocation has ended: <outcome>. It started at <start> and ended at <end>. This page is reloaded every <S> s, so it shows the next invocation in this working directory once one starts.` |
| `vespera label`'s page | `This invocation has ended: <outcome>. It started at <start> and ended at <end>. This page is reloaded every <S> s, so it shows the next invocation in this working directory once one starts.` |

The words ADR-269 §2 fixes still open the line, so the page still says first, and plainly, that its invocation has ended; the sentence after them says why a page that has ended goes on reloading. The lines while the invocation runs are unchanged. *This working directory* names no path, so ADR-265 §1's rule holds.

**The last write's liveness mark, on each of the four tabs, is `<span class="liveness" data-interval="<ms>">ended</span>`**: the word `ended`, `data-interval` the interval in milliseconds as the live mark carries it (ADR-267 §5), and no `data-written`.

### 3. The script

**The script's first statement schedules `location.reload()`, `<S>` seconds after the page loaded, for a liveness mark carrying `data-interval`**, whether it says `live` or `ended`; **the check of ADR-267 §5 marks stale only a mark carrying `data-written`**, which a last write's mark does not carry. So a page that has ended is reloaded at the interval and is never called stale for being old, as ADR-269 §3 had it. The script stays one constant of `ProgressPage`, the same on every tab at every write, its reload scheduled before anything else it does (ADR-324 §5).

**A fold outlives the invocation as it outlives the reload.** `location.reload()` keeps the address, its fragment included (ADR-324 §5), so a leg the operator opened or folded on an ended page is drawn so on the next invocation's page, and a leg they left alone is opened where ADR-324 §4 opens it. Nothing of the invocation is written into the fragment.

**`vespera label`'s page carries no script** (ADR-267 §6) and reloads by its refresh alone. A tab showing it reads the fragment again, by the script, once the next `vespera run`'s page is the one it reloads.

**`vespera label` writes `progress.html` alone** (ADR-267 §3), so a tab on `Steps`, `Samples` or `Runs` goes on reloading the last `vespera run`'s page, which says that invocation has ended, until the next `vespera run` rewrites it; its bar of tabs leads to the label command's page.

### 4. A page whose file is gone

**A write never leaves the file missing**: each page is moved over its place in one move (ADR-223 §3 and §6), so a reload finds the page before the write or after it.

**Where the file is gone**, the working directory moved or deleted or `progress.html` deleted, a tab of the file reloads onto the browser's own page for a missing file, which runs none of the page's script and carries no refresh, and stays there. Nothing on the page asks whether the file is there before reloading (Context). Reloading that tab by hand asks the same address again, and shows the page once an invocation has written it there; an invocation in a working directory moved elsewhere writes another file, which that tab never shows.

**Under the control panel**, a reload where the working directory holds no `progress.html` is answered with the server's `404` line, and one after the control panel has ended with the browser's page for an address that does not answer; neither carries a refresh, so the tab stays there, and the tab of a report does not come back with the control panel's next start (ADR-325 §8). While the control panel runs and the file is there, a report's tab of the progress page goes on reloading after the invocation ends, and shows the next one in the same working directory, whether the control panel started it or not.

**An invocation that writes no page**, refused before its job starts (ADR-265 §2, ADR-266 §1), leaves the page as it was, and an open tab goes on reloading it.

### 5. `vespera.open-browser`

**`vespera.open-browser` is an application property, true unless set**, shipped in `application.yaml` as `open-browser: true` beside `progress-interval`, under a comment saying what it does. An operator sets it false in `application-local.yaml`, as `--vespera.open-browser=false` on the command line, or as the environment variable `VESPERA_OPEN_BROWSER=false`. **It is no profile value**: it says how this machine is used, not what has been judged about a corpus, as `vespera.sidecars.start` is not (ADR-323 §9). `application-test.yaml` does not set it, so the tests run with its default.

**Where it is false, no `PageOpener` is asked anything in the invocation**: not for the progress page, by `vespera run` or by `vespera label` with or without `--auto` (ADR-266 §2), and not for the deliverable's `index.html`, by the generation step on any of its four paths (ADR-274 §7). Nothing else changes: the command's first line is still `Progress page: <path>` (ADR-266 §1), the closing line still names the index (ADR-274 §7), and the page is written and reloads as §1 says.

**Where it is true**, ADR-266 §2 and ADR-274 §7 hold as they are: each page is handed to the opener once, and `SystemPageOpener` opens nothing where `CI` is set or, off Windows and macOS, where no display is.

**One setting for both pages.** The operator asked that an invocation not open the browser, and the index is opened by every invocation that writes the tree, a tree already recorded included, so a setting that left it would still hand a new tab to every invocation reaching the generation step. Its name says what it decides, whether the browser is opened, for every page the jar opens; `vespera.progress-open`, the name ADR-266 refused, would name one of the two.

**It reaches the jar alone.** The control panel's own opener has no switch (ADR-325 §7), and the control panel links no class of the jar (ADR-285 §3) and reads none of its configuration files; its tab comes back to its next start in place of a new one (ADR-325 §4), so it does not hand a tab to each start; and the jar it starts runs with `CI=1`, so that jar opens nothing whatever the setting says.

### 6. The modules, and run ids

Only classes of `pipeline` change: `ProgressPage`, `ReportPage` where a method for a last write that reloads is added, the classes that read `vespera.open-browser` and hand it on, among them `VesperaJobConfiguration` and `GenerationTasklet`, and `application.yaml`. None gains a rule: whether a page reloads and whether a browser is opened decide no verdict, cache key, cluster or text of the deliverable. A class the implementer adds to `pipeline` for them is listed in `PipelineHoldsOnlyTheRulesOnRecordTest` as holding none. **No run id moves**: no stage's version names `pipeline` (ADR-222), and `vespera.open-browser`, as `vespera.progress-interval`, is no part of any run's configuration. Nothing of `io.algernon.controlpanel` changes.

### 7. What the operator is told

The README, in the change that builds this, says that every tab of the progress page reloads itself every five seconds (`vespera.progress-interval`) while the command runs and after it has ended, so that a page left open shows the next `vespera run` or `vespera label` in the same working directory once it starts, saying until then that the last one has ended; that a page whose working directory was moved or deleted shows the browser's own error and stops reloading, to be reloaded by hand; and, beside `vespera.progress-interval`, that `vespera.open-browser` set false, in `application-local.yaml`, as `VESPERA_OPEN_BROWSER=false` or as `--vespera.open-browser=false`, opens neither the progress page nor the deliverable's `index.html`, the command's first line still naming the page. `CONTEXT.md`'s entry for the progress page names the setting beside `CI`.

## Alternatives refused

- **A slower interval after the end**, a minute or a multiple of `<S>`. The next invocation's first page would wait that long behind one saying the last has ended, which is what was asked to be rid of; and a page left by a killed invocation already reloads at `<S>`.
- **A second setting for the interval after the end.** One interval serves both, and the operator asked for one setting.
- **Reloading for a while after the end and then stopping.** A tab left open overnight would miss the next morning's invocation, the very tab the operator asked about.
- **A check before each reload that the file is still there.** A `file:` page reads no file, its own included, in the browsers an operator uses (Context), so the check could not be made where it matters.
- **The page telling the next invocation that it is open**, as the control panel's tab does through `/alive` (ADR-325 §4). A `file:` page makes no request a jar answers, and a jar serving one for a page would be a server for an invocation that has none.
- **The working directory remembering that a tab was opened**, so that the next invocation opens none. The jar cannot know whether the tab was closed since; the setting says what the operator wants.
- **A setting for the progress page alone**, the index still opened. An invocation reaching the generation step would still open a tab each time (§5).
- **`false` under the test profile.** The tests would stop holding the default; `RecordingPageOpener` already keeps every test from opening a browser (ADR-266).
- **A profile value.** It would be read with the corpus's judgements, which it is not one of.
- **The fragment forgotten when the invocation changes.** The address and the page would have to carry a mark of the invocation for the script to compare; a fold kept is undone by one click.
- **The control panel's opener under the same setting.** It links no class of the jar and reads none of its configuration files, and its tab already comes back to its next start.

## Consequences

- A tab left open on the progress page shows each new invocation in the same working directory within an interval of its first write; with `vespera.open-browser` false, no other tab is opened, for the progress page or the deliverable's index.
- Every open page that has ended is reloaded from its file every `<S>`, in a background tab too, until its tab is closed, as a page left by a killed invocation already was; a hover over a chart is cleared at each reload until the pointer moves, as it is while the invocation runs.
- A leg opened or folded on an ended page stays so on the next invocation's page.
- After `vespera label`, a tab on `Steps`, `Samples` or `Runs` shows the last `vespera run`'s, saying it has ended, until the next `vespera run`.
- A tab whose working directory was moved or deleted stops on the browser's own error page.
- While the control panel runs, a report's tab of the progress page follows the next invocation in its working directory.
- One application property is added. No schema, cache key, verdict kind, run id or class of the control panel changes.

## What this does not decide

- **How a value of `vespera.open-browser` that is neither true nor false is refused**: Spring Boot's conversion of a boolean property decides it, as it does for `vespera.sidecars.start`.
- **The look beyond §2.**
- **Whether the control panel's launch page offers the setting.** The jar it starts opens nothing under `CI=1` (ADR-325 §8), so there is nothing for it to set.
- **Whether an agent may read the pages**, which stays a record of its own (ADR-265).

## Tests

| Method | What it holds |
| --- | --- |
| `pipeline.ProgressPageTabsTest.everyTabSaysWhetherTheInvocationRunsAndReloadsAfterItHasEndedToo` | §1, §2: each of the four tabs, after the last write, carries one refresh, at the interval, inside `<noscript>`, says it ended, and its mark is `ended` with the interval in milliseconds and no time written; while the job runs, as ADR-269 has it |
| `pipeline.ProgressPageTabsTest.theStatusLineSaysWhenTheInvocationStartedAndWhenItEnded` | §2: every tab's last write says it ended completed, when it started and ended, and that the page is reloaded every interval and shows the next invocation in this working directory once one starts |
| `pipeline.ProgressPageTabsTest.theLabelPageSaysWhenTheCommandStartedAndWhenItEndedAndHasNoTabs` | §2: the label command's last write says the same sentence after when it started and ended |
| `pipeline.ProgressPageTabsTest.theLabelPagesLastWriteReloadsAtTheIntervalWithoutAScript` | §1: the label command's last write, as its first, carries one refresh at the interval, outside any `<noscript>`, and no script |
| `pipeline.ProgressPageTabsTest.theScriptReloadsATabThatHasEndedAndCallsStaleOnlyATabStillRunning` | §3: the script schedules its reload for a mark carrying `data-interval`, and before it names `data-written`; and gives a mark the class `stale` only for one carrying `data-written` |
| `pipeline.BrowserNotOpenedWhenSetInvocationTest.vesperaRunOpensNeitherThePageNorTheIndexAndStillNamesBoth` | §5: with `vespera.open-browser` false, two invocations of `vespera run`, the second writing the tree, ask the opener nothing; the second's first line names the progress page and its last line the index |
| `pipeline.BrowserNotOpenedWhenSetInvocationTest.vesperaLabelOpensNothingAndStillNamesThePageFirst` | §5: with `vespera.open-browser` false, `vespera label` records its answers, asks the opener nothing, and its first line names the progress page |

`ProgressPageOpensInvocationTest`, `TheTreeInHtmlInvocationTest.anInvocationThatWritesTheTreeOpensTwoPages` and `TheTreeInHtmlInvocationTest.theClosingLineNamesTheIndexAndTheIndexIsOpenedOnce` hold the default: they set no `vespera.open-browser`, and each page is opened once.

Eight tests held that a last write carries no reload. `ProgressPageTabsTest.everyTabSaysWhetherTheInvocationRunsAndReloadsUntilTheLastWrite` is the first test above. Four no longer claim it and are named for what they still hold: `ProgressPageTabsTest.theLastWriteKeepsTheChartsAndTheHoverAndCarriesNoReload` is `theLastWriteKeepsTheChartsAndTheHover`, `ProgressPageChartsTest.eachPageOfDetailReloadsWhileTheJobRunsAndNotAfterItEnds` is `eachPageOfDetailReloadsWhileTheJobRunsAndSaysHowItEnded`, `ProgressPageInvocationTest.theLastWriteSaysTheInvocationEndedAndStopsTheReload` is `theLastWriteSaysTheInvocationEnded`, and `LabelWritesTheProgressPageInvocationTest.theLabelCommandWritesWhatItRecordedAndStopsTheReload` is `theLabelCommandWritesWhatItRecorded`. Three keep their names and drop the claim: `ProgressPageInvocationTest.aFailedStepIsShownFailedAndTheStepsAfterItNotStarted`, `LabelWritesTheProgressPageInvocationTest.aRefusedLabelCommandSaysItWasRefusedAndNotWhy` and `LabelCommandThatThrowsTest.anExceptionReachesTheCallerAndThePageEndsFailed`. Each claim §1 makes of a last write is made once, in the tests above.
