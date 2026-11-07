# ADR-361 — The cockpit's steps fold into the progress page's four legs, and its copy of their table is held to the page's by a test

> **Partly amended — see [ADR-362](0362-an-invocations-report-is-one-json-file-drawn-as-static-html-through-a-mustache-template-and-no-command-opens-anything.md).** Of §1: `Legs.statesOf` and `Legs.openOf` take an `InvocationReportFile.Report` where they took a `ProgressFile.Progress`, the window reading `report.json` in place of `progress.json`. Of *Alternatives refused*, its first, `progress.json` naming each step's leg, or each leg's state and fold: it no longer holds of `report.json`, which carries each step's `leg` and each leg's `state` and `open`, since the shipped template computes nothing and cannot derive the legs, and the window's copy needs no refusal of the file's help once a test holds its rules equal to the jar's. Of §2: the test that holds `Legs` to the jar's is `pipeline.TheControlPanelsLegsAreTheReportsTest`, which holds `Legs.ALL`, each leg's state and which legs are open to the `legs` and the steps' `leg` of the `report.json` the jar writes, no `progress.html` being written from that record's step 11 on; it settles the re-homing that *What this does not decide* leaves to it. Of its *What this does not decide*, the remark that a report's tab shows the progress page folding by its own script: no such page is written. §3 to §8 stand, as do the table `Legs.ALL` and its place in the window. The rest of this record stands.

- **Date**: 2026-11-06
- **Status**: accepted on 2026-11-06.
- **Decided**: by the operator, that the cockpit's steps fold into legs as `progress.html`'s timeline folds them ([#726](https://github.com/algernon28/vespera/issues/726)), and that the control panel holds its own copy of the legs' table, which a test holds equal to the progress page's; the ids, what a leg's bar shows, its state before any `progress.json` is read, which legs open, how long a fold the operator makes lasts, the columns and the tests are decided under delegation.
- **Partly reopens**: [ADR-324](0324-the-timeline-of-the-steps-folds-into-four-legs-each-one-bar-open-where-the-job-is-and-a-fold-outlives-the-reload-in-the-pages-address.md) §6, *"Its own pages get no legs … the control panel links no class of the jar (ADR-285), so legs on its pages would be a second copy of §1's table, free to drift from the first"*: the cockpit's steps chart folds into §1's four legs, from a copy of §1's table the control panel holds as text, and the drift §6 names is closed by a test that fails the build where the copy and the page differ (§2), not by linking the jar. §6's first paragraph, the progress page shown as the file, stands, as ADR-344 §6 shows it in a report's tab. It carries a note at its head.
- **Amends**: [ADR-344](0344-the-control-panels-window-is-one-cockpit-run-and-stop-the-steps-as-bars-beside-the-output-the-progress-pages-three-charts-the-profile-and-the-folders-to-include-starting-from-the-real-configuration.md) §1, the run's `steps`, *"a chart of the job's steps in the order `StopCosts.ROWS` names them, a row each, `step-<name>`"*: the rows stand in four legs, `leg-<leg>`, each row inside its leg as §1 has it, its stage, its name, one bar, its state and, on the running step's row, `stop-step` with what a stop there costs (§3). It carries a note at its head.
- **Keeps**: [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3, the control panel links no class of the jar, which `TheControlPanelLinksNoClassOfTheJarTest` holds; ADR-324 §1's table, §3's rule for a leg's state and §4's rule for which legs open, the page's and now the window's, and ADR-324 §5, which keeps the page's folds in its address; ADR-344 §2, `progress.json` as the jar writes it, unchanged; ADR-344 §1's rows and the columns they line up in; [ADR-345](0345-the-cockpit-replaces-the-browser-control-panel-its-tree-writes-the-folders-not-walked-and-publish-offers-each-destination.md) §3, the stage each row shows; [ADR-302](0302-the-control-panel-is-a-launch-page-and-an-invocation-page-of-server-written-forms-each-report-re-served-read-only-in-a-sandbox-and-the-next-invocations-values-on-pages-of-their-own.md) §7's costs, `StopCosts.ROWS`, one a step; [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md), no stage's version names `pipeline`.
- **Settles**: [#726](https://github.com/algernon28/vespera/issues/726).

## Context

**What the cockpit shows.** The run's `steps` is sixteen rows, one under the other, each `step-<name>`: its stage, its name, one bar of how far it has got and its state, and, on the running step's row, its own Stop and what a stop there costs (ADR-344 §1, ADR-345 §3). The rows line up in columns: across the sixteen, each row's stage, name, bar and state start at one place and every bar is one width, `StepColumns` giving every stage and every name the width of the widest of its column before the rows are laid out. A run of hours shows a column of finished rows above the one that is running, as the progress page's timeline did before ADR-324 folded it.

**What the window reads.** `progress.json` holds `ended` and, for each step in job order, its name, its state in the page's words and, where its progress line has a counter, `done` and `total` (ADR-344 §2). It names no leg. Before any file is read the window draws `StopCosts.ROWS`, each row `waiting`.

**Why ADR-324 §6 gave the window no legs.** The legs are a constant of `ProgressPage`, `LEGS`, private, beside the steps in job order, and the control panel links no class of the jar (ADR-285 §3). Legs drawn by the window would be a second copy of the table, free to drift from the first. The same holds today of three tables the window keeps as text and a test holds to the jar's: the steps of `StopCosts.ROWS`, held to the job's steps by `pipeline.TheStopCostsNameTheJarsStepsTest`; the stage each row shows, `StopCosts.stageOf`, held to `ProgressPage.STEPS_OF_A_STAGE` by the same test; and `progress.json` itself, which `ProgressFile` reads, held by `pipeline.TheControlPanelReadsTheProgressTheJarWritesTest`. Each of those tests lies in the jar's package `pipeline` of the test tree, whose class path holds both jars' classes.

## Decision

### 1. The control panel's table of legs

**`Legs`, a class of the control panel, holds ADR-324 §1's table as text**:

```java
public final class Legs {
    public record Leg(String name, List<String> steps) {}
    public static final List<Leg> ALL;                                    // the four, in the page's order
    public static Map<String, String> statesOf(ProgressFile.Progress progress);  // by leg, in ALL's order (§4)
    public static List<String> openOf(ProgressFile.Progress progress);           // the legs §5 opens, in ALL's order
}
```

`ALL` is `deduplication`, `translation`, `clustering` and `generation`, each with its steps in job order, as ADR-324 §1's table names them, every step of `StopCosts.ROWS` in exactly one, the four together `StopCosts.ROWS` in its order. A leg's name is written as the page writes it. `Legs` names no class of the jar, as no class of the control panel does.

### 2. The copy held to the page

**`pipeline.TheControlPanelsLegsAreTheProgressPagesTest` holds `Legs` to `ProgressPage`**, driving the page as Spring Batch drives its listener, with executions built by hand, and reading what the page draws:

- **the table**: the legs of `progress.html`'s timeline, each `<details class="leg" data-leg="<leg>">` in order with the `data-step` of each step it draws, are `Legs.ALL`, leg by leg and step by step;
- **the states and the folds**: for each of six jobs, at the write that job is read at, the `data-state` of each leg's `<g class="leg-span">` is what `Legs.statesOf` reads off the `progress.json` written with that page, and the legs written `open` are `Legs.openOf`'s, in order; the six together draw each of ADR-324 §3's five states and open a leg for each of §4's three reasons.

**It reads the page's drawing, not its constant**, so nothing of `ProgressPage` is widened for it: `LEGS` stays private, and a change to it, to the rule of its state or to which legs it opens shows in the page this test reads. A leg or a step added to one table and not the other, or a rule changed in one and not the other, fails the build. That is the drift ADR-324 §6 names, closed without linking the jar. The test reads `progress.json` with `ProgressFile`, as the window does.

### 3. The legs in the window

**`steps` holds four legs, its children in `Legs.ALL`'s order, each a `TitledPane`, the pane JavaFX gives a control that folds**:

| Part | Id | What it holds |
| --- | --- | --- |
| A leg | `leg-<leg>` | a `TitledPane`, style class `leg`, folded or opened by its title as the operator presses it |
| Its summary | | the pane's graphic, an `HBox` of style class `leg-summary`, holding the three parts below, and no step's row |
| | `leg-name-<leg>` | the leg's name, as `Legs.ALL` writes it |
| | `leg-bar-<leg>` | the leg's one bar, a `ProgressBar` (§4) |
| | `leg-state-<leg>` | the leg's state in words (§4) |
| Its steps | `step-<name>` | the pane's content, the rows of its steps in job order, each row as ADR-344 §1 and ADR-345 §3 have it, its ids, parts and words unchanged |

The pane's text is empty: its summary is its graphic. **The legs are no `Accordion`**: an accordion opens one pane at a time, and §5 may open two, the leg where the job first stopped at a gate and the leg it runs in.

### 4. A leg's bar and its state

**A leg's bar is the mean of its rows' bars**: each row counts as its bar is drawn, `done` over `total` where it has both, full where it is `finished` or `stopped at a gate`, nothing where it is `waiting`, `not started` or `failed` with no counter, and nothing where its bar moves without a fraction, a step running with no counter. So a leg whose every row is full is full, and a leg of six steps, two finished and one running at 1 of 4, stands at 0.375. The page's leg bar is a span of time; `progress.json` carries no time, and how far a leg has got is what the window's bars show.

**A leg's state is ADR-324 §3's, read off its steps' states as `progress.json` gives them, `ended` standing for the last write**, by the first rule of that table that holds. A step the file does not name is read `waiting`, as its row is. **A leg every one of whose steps reads `waiting` says `waiting`**, as its rows do, before any file is read and wherever the file names none of its steps; otherwise a step read `waiting` counts as `not started`. A file the jar writes names every step, so for it the leg says what the page's leg says (§2).

**While a leg is open its summary shows its name alone**: its bar and its state are not shown, as ADR-324 §2's drawing has the open leg, its rows below showing where it is. A folded leg shows all three.

### 5. Which legs are open

**At each reading of `progress.json`, a leg is open where its state is `running` or `failed`, or where it holds the first step in job order whose state is `stopped at a gate`, and folded otherwise**: ADR-324 §4's rule, `Legs.openOf`. Before any file is read every leg is folded, and says `waiting`.

### 6. A fold the operator makes

**A leg the operator opens or folds stays as they left it for as long as the window is open**, across every later reading of `progress.json` and every later invocation, as ADR-324 §5 keeps a fold in the page's address and ADR-327 across the page's next invocation. A leg the operator has not opened or folded is opened and folded by §5 at each reading, so the leg the job reaches opens, and a leg §5 opened folds again once the job leaves it. Every change of a leg's `expanded` the cockpit does not make itself is the operator's. Nothing of it is written to a file: a fold is no launch value (ADR-292).

### 7. The columns

**The rows keep their columns across the legs**: with every leg open, each of the sixteen rows' stage, name, bar and state starts at one place in the window and every row's bar is one width, the widest stage and the widest name taken over all sixteen rows, whichever leg holds them, as `StepColumns` takes them now, and every leg's content set in by the same width. **The four summaries line up among themselves**: each summary's name, bar and state start at one place across the four and the four bars are one width. A summary's columns are not held to the rows': the pane's title draws its arrow before the graphic, and its place is the look's.

### 8. Stop

**The running step's row keeps its `stop-step` and its cost**, inside its leg, which §5 opens while it runs. Where the operator has folded that leg, the row is out of sight with it, and the action card's `stop` stops the child as before. No Stop is put on a leg's summary: a stop is of the invocation, and its cost is the running step's.

### 9. The modules, and run ids

Only `io.algernon.controlpanel` changes, and the stylesheet it ships. `ProgressPage`, `progress.json` and every class of the jar's are unchanged; no stage's version names `pipeline` (ADR-222), and none names the control panel. **No run id moves.** No property is added: the legs, the rules of §4 and §5 and the lasting of a fold are fixed.

### 10. What the operator is told

The README's paragraph on what the window shows, in the change that builds this, says that the job's steps fold into four parts, deduplication, translation, clustering and generation, each one bar of how far it has got with its state, that the part the job is in, where it failed or where it first stopped at a gate is open, and that a part opened or folded stays so while the window is open.

## Alternatives refused

- **`progress.json` naming each step's leg, or each leg's state and fold.** The window draws its rows before any file is there, from `StopCosts.ROWS`, so it would fold nothing until the first reading, and it would hold the rule of a leg's state for a file of a jar that wrote none; and the jar would change its file for a drawing of the window's.
- **The window linking `ProgressPage`'s table.** ADR-285 §3 refuses it; `TheControlPanelLinksNoClassOfTheJarTest` fails on it.
- **`ProgressPage.LEGS` made visible to the package for the test**, as `STEPS_OF_A_STAGE` is. The table alone would be held; the state and the folds are a rule, and only the page's drawing shows the rule the page applies.
- **An `Accordion` of the legs.** It opens one pane at a time (§3).
- **A pane that folds for each stage.** Seven panes, four of one step, where the page has four legs.
- **A fold the operator makes kept until the next Run, or never kept.** Never kept, it would be undone at the next reading, every few seconds; kept until the next Run, the window would fold where the page, the same operator's view of the same job, does not.
- **The summary's bar and state shown while its leg is open.** The drawing ADR-324 approved shows an open leg by its name, its rows saying where it is.

## Consequences

- An operator who runs the job from the window sees four bars, one a leg, each with its state, and the leg the job is in, where it failed or where it first met a shut gate open on its rows, the Stop of the running row among them.
- A leg the operator opens or folds stays so while the window is open.
- The window holds a fourth table of the jar's as text, `Legs.ALL`, beside `StopCosts.ROWS`, `StopCosts.stageOf` and `ProgressFile`'s reading, and a test of the jar's package holds it, with the rules of §4 and §5, to the page's.
- No schema, cache key, verdict kind, property, run id, file the jar writes or class of the jar changes.

## What this does not decide

- **The look beyond §3 and §7**: the summary's spacing, the colour of a leg's bar, the rows' heights and the gap between legs, the stylesheet's (ADR-342 §3).
- **A description on hover for a leg**, as ADR-324 leaves the page's.
- **Report tabs of the progress page**: the page shown in a report's tab folds by its own script (ADR-344 §6), and nothing here joins its folds to the window's.
- **What holds the copy once the page is gone.** The draft of [#721](https://github.com/algernon28/vespera/issues/721) and [#724](https://github.com/algernon28/vespera/issues/724), which retires `progress.html` and moves the legs into `report.json`, would take away the page `TheControlPanelsLegsAreTheProgressPagesTest` parses (§2). The record that decides it re-homes that test, or the copy itself, so that the copy stays held to the jar's legs.

## Tests

| Method | What it holds |
| --- | --- |
| `pipeline.TheControlPanelsLegsAreTheProgressPagesTest.theControlPanelsTableOfLegsIsTheProgressPages` | §1, §2: the legs `progress.html`'s timeline draws, in order, each with its steps in order, are `Legs.ALL`, and the four together are the job's steps once each |
| `pipeline.TheControlPanelsLegsAreTheProgressPagesTest.eachLegsStateAndWhetherItIsOpenAreThePagesInEveryJob` | §2, §4, §5: for six jobs, running past a gate, failed beside a gate, between two steps while running and at the last write, every step finished, and first stopped at a gate in translation, each leg's state and the legs open, read by `Legs` from `progress.json`, are the page's; the six draw all five states and open a leg running, one failed and one holding the first step stopped at a gate |
| `controlpanel.TheStepsFoldIntoLegsTest.theStepRowsStandInFourLegsInOrderEachWithOneSummaryBar` | §3, §4, §5: `steps` holds `leg-deduplication`, `leg-translation`, `leg-clustering` and `leg-generation`, in that order, each a `TitledPane` whose content holds its steps' rows in job order and whose summary, outside its content, holds its name, one bar and its state; before any file each says `waiting` and is folded |
| `controlpanel.TheStepsFoldIntoLegsTest.theLegRunningFailedOrFirstStoppedAtAGateIsOpenAndTheOthersFolded` | §4, §5: read in turn, a file with extraction running opens deduplication alone, saying `running`; one with byte-level reduction failed at the last write opens deduplication alone, saying `failed`; one whose job first stopped at a gate in translation, every later step stopped too, opens translation alone, clustering and generation folded saying `stopped at a gate` and deduplication folded saying `finished` |
| `controlpanel.TheStepsFoldIntoLegsTest.aLegsBarIsTheMeanOfItsRowsBarsAndAnOpenLegShowsItsNameAlone` | §4: with the census and byte-level reduction finished and extraction running at 1 of 4, deduplication's bar stands at 0.375 and translation's at nothing; deduplication, open, shows neither its bar nor its state, and translation, folded, shows both |
| `controlpanel.TheStepsFoldIntoLegsTest.aLegTheOperatorOpensOrFoldsStaysSoAsTheProgressIsReadAgain` | §6: deduplication running and open, the operator folds it and opens generation; read again with translation running, deduplication stays folded, generation open and translation opens; read again with clustering running, translation, which §5 opened, folds, clustering opens, and the operator's two stay as they left them |
| `controlpanel.TheStepsFoldIntoLegsTest.theFourSummariesLineUpInColumns` | §7: across the four folded legs, each summary's name, bar and state start at one x, and the four bars are one width |
| `controlpanel.TheStepRowsAlignInColumnsTest.theStepRowsLineUpInColumns` | §7: with every leg opened, as the operator can, each of the sixteen rows' stage, name, bar and state start at one x across the legs, and every bar is one width |
| `controlpanel.TheStepRowsAlignInColumnsTest.withOneStepRunningInANarrowPaneTheBarsStayOneWidthAndTheStatesStartAtOneX` | §7, §8: with every leg opened and extraction running in a steps pane narrower than its row asks for, its row alone is laid out short, every bar stays one width and every state starts at one x |

`controlpanel.TheControlPanelsWindowTest.theStepsAreAChartOfABarEachBesideTheOutput` and `controlpanel.StopAndCloseAskFirstTest.stopAsksWithItsSentenceAndStopsTheChildOnlyOnTheAnswerThatGoesOn` find each row by its id under `steps`, wherever its leg puts it, and are unchanged: a row's id, its one bar and its `stop-step` are §3's. The two methods of `TheStepRowsAlignInColumnsTest` open every leg before they read the rows, which a folded leg's content would otherwise lay out out of sight.

## What the implementation owes

Beside the tests above, which it turns green, the change adds `Legs` (§1) and builds §3 to §8 in `Cockpit`, the stylesheet giving `leg` and `leg-summary` their look. It changes the README's paragraph on what the window shows as §10 says, and `CONTEXT.md`'s entries for a leg, which the progress page's timeline and the cockpit both fold the job into, and for the control panel, whose steps fold into the legs.
