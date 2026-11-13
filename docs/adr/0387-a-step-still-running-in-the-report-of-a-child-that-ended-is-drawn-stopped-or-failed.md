# ADR-387 — A step still running in the report of a child that ended is drawn stopped or failed

- **Date**: 2026-11-13
- **Status**: accepted on 2026-11-13.
- **Decided**: under delegation, on [#788](https://github.com/algernon28/vespera/issues/788): what the steps chart draws of a step the last report of the window's own child still says is `running` once that child has ended.
- **Amends**: [ADR-365](0365-the-cockpits-charts-carry-a-legend-and-a-value-on-each-sample-and-a-running-step-or-leg-says-its-whole-percent.md) §4, *"A step's row shows its state as the file's word"*, and §5, a leg's words: a step or a leg the report of a child that has ended still gives as `running` says `stopped` or `failed` (§1). It carries a note at its head.
- **Amends**: [ADR-370](0370-every-bar-of-the-cockpits-steps-carries-its-whole-percent-drawn-on-it-and-nothing-else-of-the-window-changes.md) §2, the table of what a step's bar reads: a step drawn `stopped` with no fraction reads `—`, as one `failed` with no fraction does (§2). It carries a note at its head.
- **Keeps**: [ADR-362](0362-an-invocations-report-is-one-json-file-drawn-as-static-html-through-a-mustache-template-and-no-command-opens-anything.md) §8, a report still `running` once its child has ended shown *ended without its last write* and never as live, of which this record is the steps chart's part; [ADR-344](0344-the-control-panels-window-is-one-cockpit-run-and-stop-the-steps-as-bars-beside-the-output-the-progress-pages-three-charts-the-profile-and-the-folders-to-include-starting-from-the-real-configuration.md) §2, a step's bar `done` over `total` where both are given, moving without a fraction only while it runs; [ADR-361](0361-the-cockpits-steps-fold-into-the-progress-pages-four-legs-and-its-copy-of-their-table-is-held-to-the-pages-by-a-test.md) §4 to §6, a leg's bar as the mean of its rows' and which legs are open, `Legs.statesOf` and `Legs.openOf` unchanged; [ADR-278](0278-the-control-panel-starts-the-jar-as-its-child-reads-its-output-to-the-end-and-stops-only-its-own-child.md) §5 and §6, how a child ended and how Stop ends it; [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3, the control panel links no class of the jar.
- **Settles**: [#788](https://github.com/algernon28/vespera/issues/788).

## Context

**What the operator saw.** Stop pressed during the census ends the child: the line above the output says *Stopped*, the outcome line *the invocation was stopped*, the output ends and Stop is greyed. The steps chart does not move: the census's row reads `running`, its bar moves without a fraction, its percent reads `…` and its own Stop, `stop-step` (ADR-344 §2), is still shown. Read together, the window looks as though Stop did nothing.

**Why.** The steps chart draws each row from the last `report.json` the child wrote (ADR-362 §8), each row's word the file's (ADR-365 §4). A child killed by Stop, or one that crashed, never makes its last write, so its last report still says the step it was in is `running`; the window reads it once as the child ends and draws it as it reads it, and reads that child's report no more. The Report tab already shows such a report *ended without its last write* (ADR-362 §8); the steps chart had no rule.

## Decision

### 1. A step still running once its child has ended

**Where the window's own child has ended and the report the window reads of it as it ends was not its last write, its `state` not `ended`, each step that report gives as `running` is drawn as a step that runs no more, its word `stopped` where the child was ended by the window's own Stop and `failed` where it ended any other way** (ADR-278 §5's `STOPPED` against the rest). Its row shows that word alone, as ADR-365 §4 has every word but `running`; neither `stop-step` nor what a stop there costs is shown; and its bar never moves without a fraction.

**A leg that report would leave `running`**, by `Legs.statesOf`, says the same word, `stopped` or `failed`, alone, and its percent is 100 at most, as every leg's not running (ADR-370 §3). Its bar is still the mean of its rows' bars, and the legs open are still `Legs.openOf`'s, so the leg the job was stopped in stays open.

A report that made its last write, and the report of an invocation the window did not start (ADR-278 §4), are drawn as before.

### 2. What the bar of such a step reads

As ADR-370 §2 has it for a step not running: `done` over `total` where both are given and `total` is above 0, its percent 100 at most; and otherwise an empty bar reading **`—`**, U+2014, for `stopped` as for `failed`: no counter says how far it got.

### 3. The modules, and run ids

`StepsChart.show` takes the word a step still running is drawn with, or none; `Cockpit` gives it where the child it watches has just ended and its report did not. The control panel links no class of the jar; nothing of the jar changes, and no run id moves.

## Alternatives refused

- **Leaving the rows as the file has them.** It is what the operator took for Stop doing nothing.
- **`stopped` whatever way the child ended.** A child that crashed was not stopped; `failed` is the word a leg that ended part-way already takes (`Legs`).
- **Drawing the step `waiting` or `not started`.** It ran; the window would hide how far it got.
- **Having the jar write a last report when it is stopped.** Stop kills the child (ADR-278 §6); no write can be counted on, and a crash makes none.

## What this does not decide

- **The Report tab and the line saying how the child ended**: ADR-362 §8's, unchanged.
- **A foreign holder's report**: read as before while its holder line is held.

## Tests

| Method | What it holds |
| --- | --- |
| `ui.AStepStoppedMidwayIsNotDrawnRunningTest.aStepRunningWhenStopEndedTheChildIsDrawnStopped` | §1, §2: with the census running with no counter in the report of a sleeping child, the row reads `running`, `…`, a moving bar and its Stop shown, deduplication `running 0%`; once Stop, confirmed, has ended the child and the window says *Stopped*, the census reads `stopped`, its bar empty and its percent `—`, its Stop gone, and deduplication reads `stopped` |

## What the implementation owes

Beside the test above, which it turns green, the change builds §1 and §2 in `StepsChart` and `Cockpit`.
