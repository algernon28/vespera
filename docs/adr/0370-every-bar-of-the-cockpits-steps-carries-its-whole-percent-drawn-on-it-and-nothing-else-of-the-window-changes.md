# ADR-370 — Every bar of the cockpit's steps carries its whole percent drawn on it, and nothing else of the window changes

> **Partly amended — see [ADR-374](0374-every-view-inside-the-control-panels-window-follows-its-light-and-dark-switch-at-once-its-popups-included.md).** Of §4 and What this does not decide: a bar's fill is `-vespera-accent` and its track's inside `-vespera-surface`, the colour of the percent's halo, so that the percent stands on its halo against the fill and on the track's own colour against the rest, in both themes. The rest of this record stands.

- **Date**: 2026-11-10
- **Status**: accepted on 2026-11-10.
- **Decided**: by the operator, in the prototype of [#791](https://github.com/algernon28/vespera/issues/791), variant A: the whole percent is drawn on every bar of the cockpit's steps, each step's row's and each leg's summary's, centred on the bar; what it reads in each state, as the verdict on #791 lists it; and that nothing else of the window changes, the state's words included, `running <N>%` as ADR-365 has it. The ids, the percent of a bar whose counter stops short of its total, the halo that keeps the text legible on the bar, and the tests are decided under delegation.
- **Amends**: [ADR-344](0344-the-control-panels-window-is-one-cockpit-run-and-stop-the-steps-as-bars-beside-the-output-the-progress-pages-three-charts-the-profile-and-the-folders-to-include-starting-from-the-real-configuration.md) §1, the run's `steps`, each row's *"one bar of how far it has got"*: the bar carries its whole percent, `step-percent-<name>`, drawn on it (§1, §2). It carries a note at its head.
- **Amends**: [ADR-361](0361-the-cockpits-steps-fold-into-the-progress-pages-four-legs-and-its-copy-of-their-table-is-held-to-the-pages-by-a-test.md) §3, the table of a leg's parts and its steps' rows *"its ids, parts and words unchanged"*: the summary's bar carries `leg-percent-<leg>` and each row's bar `step-percent-<name>` (§1); §4, *"While a leg is open its summary shows its name alone"*: its percent is not shown either, as its bar is not (§3); §7, the columns: a bar and its percent stand in the bar's place and count as the bar, every bar one width and every state after it starting at one x (§1). It carries a note at its head.
- **Amends**: [ADR-365](0365-the-cockpits-charts-carry-a-legend-and-a-value-on-each-sample-and-a-running-step-or-leg-says-its-whole-percent.md), two of its *Alternatives refused*, *"A percent for a step running with no counter of a total"* and *"A percent beside `finished` or `stopped at a gate`"*: both hold of the state's words, which carry no percent still; the bar of a step running with no counter carries `…`, and the bar of a step finished or stopped at a gate carries its own percent (§2). It carries a note at its head.
- **Keeps**: ADR-365 §4 and §5, the words of a step's and a leg's state, `running <N>%` while it runs and its word alone otherwise, unchanged; [ADR-344](0344-the-control-panels-window-is-one-cockpit-run-and-stop-the-steps-as-bars-beside-the-output-the-progress-pages-three-charts-the-profile-and-the-folders-to-include-starting-from-the-real-configuration.md) §2's bar of a step and ADR-361 §4's bar of a leg, unchanged, each percent read from its bar; ADR-361 §5 to §8, which legs open, a fold the operator makes, the columns and Stop; `Legs.statesOf` and `Legs.openOf`; [ADR-342](0342-the-control-panel-is-a-javafx-window-inside-the-vespera-jar-opened-by-the-jar-started-with-no-argument-and-its-loopback-server-and-pages-are-gone.md) §3, every colour, size and space the window draws is its stylesheet's; [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3, the control panel links no class of the jar.
- **Settles**: [#791](https://github.com/algernon28/vespera/issues/791).

## Context

**What the steps chart draws.** `StepsChart` holds the four legs, each a `TitledPane` whose summary is its name, `leg-name-<leg>`, one `ProgressBar`, `leg-bar-<leg>`, and its state's words, `leg-state-<leg>`, and whose content is its steps' rows, each `step-<name>`: its stage, its name, one `ProgressBar` with no id, its state's words and, on the running step's row, `stop-step` and what a stop there costs (ADR-344 §1, ADR-361 §3). The stylesheet gives every bar of a row or a summary one width, 160 px. The rows' stages and names and the summaries' names are each as wide as the widest of their column, so every bar, and every state after it, starts at one x (ADR-361 §7).

**What a bar shows.** A step's bar is `done` over `total` where both are given and `total` is above 0, a full bar at most; failing that, full where the step is `finished` or `stopped at a gate`, moving without a fraction where it is `running`, and empty otherwise (ADR-344 §2, as `StepsChart.show` reads it). The counter comes first, so a step finished, stopped at a gate or failed whose last progress line left its counter short of its total shows a bar short of full: the seed-corpus comparison stopped at a gate at 2 of 4 stands at half. A leg's bar is the mean of its rows' bars, a bar moving without a fraction counting as nothing (ADR-361 §4). No number is drawn on any bar; only a running step with a counter of a total and a running leg say a number, in their state's words, `running <N>%` (ADR-365 §4, §5).

**The prototype.** The branch `claude/prototype-791-progress-percent`, never for `main`, drew three variants on the real `StepsChart` and stylesheet, fed a run that walked every step: A, the percent centred on the bar, the bar 160 px wide and 20 px high, the percent bold at 11 px; B, the percent in a column of its own after the bar; C, a large number before a thin bar. The operator chose A, and that nothing else of the window changes. The prototype's run finished every step at its counter's total, so it never drew a step finished or stopped at a gate whose counter stops short.

**The colours under the text.** The stylesheet sets no colour of a bar, so modena's apply: the bar's fill is `-fx-accent`, `#0096C9`, in a gradient down to 9 % darker, and its track is drawn from the theme's control background. The window's ink is `#1e1b2e` in the light theme and `#ecebf3` in the dark (ADR-342 §3). Against the fill the light ink stands at 4.2 to 5.0 : 1 and the dark ink at 2.9 to 3.4 : 1; WCAG AA asks 4.5 : 1 of text of this size. A percent centred on a bar lies on the fill and on the track at once, wherever the bar stands between a quarter and three quarters, so no one colour of text stands at AA against both in the dark theme. The stylesheet's head says that every pair of its tokens stands at AA; the ink against the surface does, at 16.8 : 1 in the light theme and 13.1 : 1 in the dark.

## Decision

### 1. Where the percent stands

**Each bar of the steps chart and its percent stand together in one `StackPane`, style class `bar-with-percent`, which takes the bar's place among its row's or its summary's parts**, between the name and the state's words: the bar fills it, and the percent, a `Label` of style class `bar-percent`, stands centred on the bar, inside its bounds.

| Part | Id | What it holds |
| --- | --- | --- |
| A step's percent | `step-percent-<name>` | on the bar of the row `step-<name>`, that bar's percent (§2) |
| A leg's percent | `leg-percent-<leg>` | on the bar `leg-bar-<leg>` of the leg's summary, that bar's percent (§3) |

**Nothing else of a row or a summary changes**: its parts stand in the order they did, the bar where it stood; every id ADR-344 §1 and ADR-361 §3 give keeps its node, and no part gains an id but the percent; the state's words, the Stop and its cost are as they were. **The columns hold as ADR-361 §7 has them**: a bar with its percent is one bar's width, every bar of the rows one width and every bar of the summaries one width, and every state after a bar starts at one x across the rows and across the summaries.

### 2. What a step's bar reads

**A step's percent is its bar's**, written as the bar stands:

| The row's bar | Its percent | Example |
| --- | --- | --- |
| a fraction, `done` and `total` given and `total` above 0 | `<N>%`: `<N>` the largest whole number not above 100 × `done` / `total`, worked out on the whole numbers the file gives, 100 at most; and **99 at most where the file says the step is `running`**, so that it is the `<N>` its words say (ADR-365 §4) | running at 375 of 1,000, `37%`; at 2,000 of 2,000, `99%`; failed at 3 of 4, `75%` |
| full with no fraction: `finished` or `stopped at a gate` | `100%` | |
| moving without a fraction: `running` with no counter, or a counter of no total or of a total of 0 | `…`, the one character U+2026 | |
| empty, `failed` with no fraction | `—`, the one character U+2014: no counter says how far it got | |
| empty, `waiting`, a step the file does not name, or `not started` with no fraction | `0%` | |

Before any file is read every row is `waiting`, and its bar reads `0%`.

**A percent never contradicts the bar it is drawn on.** A step finished at 3 of 4 stands at three quarters, and reads `75%`; the seed-corpus comparison stopped at a gate at 2 of 4 stands at half, and reads `50%`. The verdict on #791 lists `100%` for `finished` and `stopped at a gate`; that is what the prototype drew where it drew those states, every counter at its total, and it is what §2 draws wherever the counter stands at its total or there is none. Where a counter stops short, the bar already stands short of full (ADR-344 §2), and the state's word beside it says `finished`.

### 3. What a leg's bar reads

**A leg's percent is `<N>%`, `<N>` 100 × its bar, ADR-361 §4's mean, worked out as a `double` and rounded down once a millionth of a percent is added to it**, as ADR-365 §5 works out a running leg's; **99 at most where `Legs.statesOf` gives the leg as `running`**, so that it is the `<N>` its words say, and 100 at most otherwise. A leg's bar is always a fraction, a row moving without one counting as nothing, so a leg's percent is never `…` or `—`: a running leg none of whose steps is finished, its running step with no counter, reads `0%`, as its words read `running 0%`; a leg failed reads how far its bar got, the census finished and byte-level reduction failed at 3 of 4 standing deduplication at `29%`; a leg every one of whose steps is finished or stopped at a gate with no counter short of its total reads `100%`. Before any file is read every leg reads `0%`.

**An open leg shows no percent**: its summary shows its name alone, as ADR-361 §4 has it, the percent hidden with its bar; a folded leg shows its name, its bar with its percent, and its state's words.

### 4. The look, and the text's contrast

**The look is the stylesheet's** (ADR-342 §3), as the prototype drew variant A: each bar of a row or a summary keeps its 160 px width and is 20 px high, so that the percent fits inside it; the percent is bold, at 11 px. No class sets a style of its own: the prototype's inline styles are not carried over.

**The percent's text is the theme's ink, `-vespera-ink`, with a halo in the theme's surface, `-vespera-surface`**: a drop shadow centred on the text, its offsets 0, its blur at least 2 px across and at least 0.8 of it solid, opaque. The text then stands against its halo at 16.8 : 1 in the light theme and 13.1 : 1 in the dark, whatever of the bar's fill or its track lies under it, where with no halo the dark theme's ink stands at 2.9 : 1 on the fill. The issue asked for AA in both themes, and the verdict left the means open; the halo changes no colour of the bar.

### 5. Nothing else changes

The state's words (ADR-365 §4, §5), the bars' values (ADR-344 §2, ADR-361 §4), which legs open and the operator's folds (ADR-361 §5, §6), the columns (§1), the Stop and its cost (ADR-361 §8), the charts and every other part of the window are as they were. `Legs` is unchanged. What a row does is read from the file's state, never from its words or its percent.

### 6. The modules, and run ids

Only `io.algernon.vespera.ui` changes, and the stylesheet it ships. No class of the jar's other packages, no file the jar writes and no property changes; no stage's version names the control panel. **No run id moves.**

### 7. What the operator is told

The README's paragraph on what the window shows, in the change that builds this, says that each bar of the steps and of the parts carries how far it has got as a whole percent, `…` on a step that runs with no count and `—` on a step that failed with none.

## Alternatives refused

- **B, the percent in a column of its own after the bar, and C, a large number before a thin bar.** The operator chose A; a column of its own would widen every row, as ADR-365 refused of a percent after the state.
- **`100%` on every bar of a step `finished` or `stopped at a gate`, whatever its counter.** It would be drawn on a bar standing short of full and contradict it; the prototype never drew the case, and the state's word beside it already says the step is done.
- **`—` on a failed leg.** A leg's bar is a mean of its rows and always a fraction; its percent says how far it got, as the bar does.
- **The percent's text in one colour, with no halo.** On modena's fill the dark theme's ink stands at 2.9 : 1, under AA.
- **The bar's fill in a colour of the window's own, for contrast.** It would change the bar's look, which the operator kept; and a text that lies on the fill and the track at once would still need a colour standing at AA against both.
- **A percent on an open leg's summary.** ADR-361 §4 shows an open leg by its name alone, its rows below saying where it is.
- **The percent in every state's words.** The operator kept the words as they are.

## Consequences

- Every bar of the cockpit's steps says how far it has got: a step's row and a folded part's summary carry its whole percent on the bar, a step running with no count `…` and a step failed with none `—`.
- A running step's or part's percent is the number its words say, never `100%` while it runs.
- No schema, cache key, verdict kind, property, run id, file the jar writes or class outside `io.algernon.vespera.ui` changes.

## Known limits

- **A leg's percent can read one percent high where its counts are very large**, as its words can (ADR-365 *Known limits*): the percent and the words are worked out alike.
- **The test reads the text's colour and its halo as the stylesheet sets them**, not the pixels drawn: a halo the platform drew thinner than its effect asks would not be seen.

## What this does not decide

- **A percent anywhere but the bars of the steps chart**: the charts, `report.html` and the console are their records'.
- **A hover on a bar or its percent.**
- **The look beyond §4**: the percent's font family, the bar's colours and the halo's exact blur, the stylesheet's (ADR-342 §3).

## Tests

| Method | What it holds |
| --- | --- |
| `ui.EveryBarOfTheStepsCarriesItsPercentTest.eachStepsBarCarriesItsWholePercentAndItsStateWordsAreUnchanged` | §1, §2, §5: before any file every step's bar reads `0%`; with the census finished at 10 of 10, byte-level reduction finished with no count and extraction running at 375 of 1,000, the bars read `100%`, `100%` and `37%`, the three later steps of deduplication not started `0%` and seed extraction, not named, `0%`, their words `finished`, `finished`, `running 37%`, `not started` and `waiting`; extraction running at 29 of 100 reads `29%` and `running 29%`, at 2,000 of 2,000 `99%` and `running 99%`, with no counter `…` and `running`, and at 5 of a total of 0 `…` and `running`; byte-level reduction failed at 3 of 4 reads `75%` and `failed`, and the census failed with no count `—` and `failed`; first stopped at a gate in translation, seed extraction finished reads `100%`, the seed-corpus comparison stopped at a gate at 2 of 4 `50%`, embedding scoring stopped at a gate with no count `100%` and extraction finished at 3 of 4 `75%`, their words `finished`, `stopped at a gate` twice and `finished` |
| `ui.EveryBarOfTheStepsCarriesItsPercentTest.aFoldedLegsBarCarriesTheWholePercentOfItsBarAndAnOpenLegsNone` | §3: before any file each of the four legs, folded, shows `0%`; with extraction at 340 of 1,000, deduplication, open, shows no percent and the three others `0%`; folded by the operator, deduplication shows `39%`, its words `running 39%`; with content redundancy running at its total, `99%` and `running 99%`; with byte-level reduction failed at 3 of 4, `29%` and `failed`; with seed extraction running with no count, translation, folded by the operator, `0%` and `running 0%`, and deduplication `100%` and `finished`; first stopped at a gate in translation, extraction having finished at 3 of 4, `95%`, `87%`, `100%` and `100%`, their words `finished` and `stopped at a gate` three times |
| `ui.EveryBarOfTheStepsCarriesItsPercentTest.eachPercentStandsCentredOnItsBarAndTheBarsAndStatesKeepTheirColumns` | §1: with every leg folded each summary's percent is shown, and with every leg opened each of the sixteen rows' percent is; each lies inside its bar's bounds, centred on it within a pixel; the four summaries' bars are one width and their states start at one x, and so are and do the sixteen rows' |
| `ui.EveryBarOfTheStepsCarriesItsPercentTest.eachPercentStandsAtWcagAaAgainstItsHaloInBothThemes` | §4: in the light theme and in the dark, extraction's percent and translation's are drawn in an opaque colour with a drop shadow of offsets 0, a blur of at least 2 px and a spread of at least 0.8, in an opaque colour the text stands against at 4.5 : 1 or more |

Each method draws the window from a first start's state on the Headless platform and hands it reports of `report.json`'s shape built by hand, their steps alone, as the cockpit hands it each reading (ADR-362). A row's state words are read as the part of the row right after the part holding its bar, which reads the same with the bar alone and with its percent.

## What the implementation owes

Beside the tests above, which it turns green, the change builds §1 to §3 in `StepsChart`, gives `bar-with-percent` and `bar-percent` their look in the stylesheet as §4 says, the bar 20 px high in rows and summaries, and says in the README's paragraph on what the window shows what §7 says.
