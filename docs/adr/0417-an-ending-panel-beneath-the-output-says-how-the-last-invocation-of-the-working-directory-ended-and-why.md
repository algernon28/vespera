# ADR-417 — An ending panel beneath the output says how the last invocation of the working directory ended, and why

- **Date**: 2026-11-18
- **Status**: accepted on 2026-11-18.
- **Decided**: by the operator, on [#874](https://github.com/algernon28/vespera/issues/874): that a small panel that scrolls stands directly beneath the output panel, in the output's side of `steps-and-output`, behind a divider of its own, and that nothing else of the cockpit changes, the outcome line and its Open profile staying exactly as they are; that the sidebar's width, the height of the steps beside the output, the divider between the two and the divider above the panel are each sized by the operator (§1); that every ending shows it, failed or refused, a defect of the control panel, the operator's own Stop and ended; that it shows the last ending of the working directory chosen, one that ended before the window opened among them, dated where it is not the ending of the child the window has just watched end, and nothing where that working directory never had an invocation; that it cannot be dismissed, is cleared as the next invocation starts and shows nothing while one runs (§2); that it takes nothing new from the child, reading the invocation report's `refusals`, `faults` and `closing` and the exit code, and, where the child wrote no report of its own, the last line of its output (§3); what it says of each ending (§3); its three buttons, and that it words no advice (§4); that Open report opens nothing outside the window, selecting the Report tab, which draws the report of the ending the panel shows, an earlier invocation's too, and follows the window's child again as it starts, and that Open report is offered only where that ending has a `report.json` of its own (§4); that an earlier invocation whose report never ended, held by nobody, is shown as *ended without its last write*, in the accent (§2, §3); its colours, from the palette's tokens alone, at 4.5 : 1 or more in both themes, and its texts, every one selectable (§5); and that ADR-383's warning stays as it is and the panel records that ending as any other (§6). Under the operator's delegation: the ids, the words of each kind, the forms of the times, of the duration and of a fault, the step an ending is said to have stopped at, an ending without its last write dated by its last write and its facts, the grips and their least sizes, what Copy details copies and in what order, the classes, and the tests.
- **Amends**: [ADR-344](0344-the-control-panels-window-is-one-cockpit-run-and-stop-the-steps-as-bars-beside-the-output-the-progress-pages-three-charts-the-profile-and-the-folders-to-include-starting-from-the-real-configuration.md) §1, whose *"`steps` and `output` stand side by side, in one split the operator can move; the charts beneath them; the tabs beneath the charts; the sidebar to the left of all"*: the output's side of that split is a split of its own, `output` above the ending panel, a grip beside the sidebar sizes its width and a grip beneath the split its height (§1). It carries a note at its head.
- **Amends**: [ADR-362](0362-an-invocations-report-is-one-json-file-drawn-as-static-html-through-a-mustache-template-and-no-command-opens-anything.md) §8, whose Report tab draws a report *"only where it is that invocation's"*, the watched one: while no invocation runs, it also draws the report of the ending the ending panel shows, an earlier invocation's among them, and Open report selects it (§4). It carries a note at its head.
- **Keeps**: [ADR-278](0278-the-control-panel-starts-the-jar-as-its-child-reads-its-output-to-the-end-and-stops-only-its-own-child.md) §5, as [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §5 widens it: a child stopped by the window's own Stop is stopped, exit code 0 ended, 2 a defect of the control panel, and every other failed or refused; the panel's four kinds are these, in `HowItEnded`'s words (§3). [ADR-362](0362-an-invocations-report-is-one-json-file-drawn-as-static-html-through-a-mustache-template-and-no-command-opens-anything.md) §8: a report is the child's only where its `invocation.pid` and `invocation.started` are the child's; the `closing` of the child's own report is shown in place of its last line of output, and a child with no report of its own is shown with its last line that is not blank and does not start `Report: `; the rows, the legs and the charts are drawn as that record has them, the watched invocation's alone; a report still `running` once its child has ended is shown as *ended without its last write* and never marked stale; and `report.html`'s path is shown as text, never opened, the Report tab being its view. [ADR-383](0383-a-parents-title-naming-no-page-or-several-stops-a-control-panel-publish-with-its-own-code-and-a-warning-asks-for-another.md): its warning, its words, its field and what it does, for a publish of the window's own ending with 3 or 4 (§6). [ADR-389](0389-the-profile-is-edited-in-a-window-of-its-own-opened-from-the-first-section-of-the-sidebar.md) §6, the jar's lines naming *the control panel's Profile window*, and §1's `outcome-open-profile`, standing beside `outcome` while a line names the Profile window: the panel's Open profile follows the same words (§4). [ADR-364](0364-the-output-panel-is-copyable-text-of-the-whole-output-from-a-file-of-its-own-and-every-text-of-the-window-is-copyable.md): the output panel, `output`, unchanged, and §8, every text shown outside a control a `SelectableText` (§5). [ADR-198](0198-every-invocation-writes-an-account-built-from-an-allow-list-that-names-no-document.md) §5: a fault is its step, its exception's type and at most three causes' types, never a message (§3).
- **Read with**: [ADR-379](0379-the-control-panels-window-has-a-light-and-a-dark-theme-switched-in-its-header-and-kept-beside-the-launch-values.md), [ADR-381](0381-every-view-inside-the-control-panels-window-follows-its-light-and-dark-switch-at-once-its-popups-included.md) and [ADR-374](0374-every-text-line-and-marker-of-the-window-its-charts-and-each-analysis-reports-native-view-stands-at-aa-in-both-themes.md), the tokens and their contrast (§5); [ADR-393](0393-a-working-directory-is-named-for-its-corpus-root-and-day-chosen-from-a-list-and-publish-takes-what-the-last-run-reached.md) §4 and §5, the working directory chosen from the list and the row each shows; [ADR-404](0404-the-values-wanted-are-read-from-the-working-directory-shown-as-the-window-opens-another-is-chosen-or-an-invocation-ends.md) §5, when the window reads a working directory's files again (§2).
- **Settles**: [#874](https://github.com/algernon28/vespera/issues/874).

## Context

**How the window says an invocation ended.** The header's `child` line says the command and how its child ended, in `HowItEnded`'s words, with the `closing` of the child's own report or, where it wrote none, its last line of output (ADR-362 §8). `StepsChart` marks a step still running when the child ended `stopped` or `failed`. `outcome`, the one line the last action said, has `outcome-open-profile` beside it while it or `child` holds the words *Profile window* (ADR-389 §1, §6). A publish of the window's own ending with 3 or 4 opens ADR-383's warning. Why an invocation ended is read nowhere else but in the output, whose last line does not always say it.

**What the report already holds.** `report.json`, which every `vespera run`, `vespera label` and `vespera publish` holding the working-directory lock rewrites, holds `refusals`, the jar's own lines whole, in the order printed; `faults`, each a step, its exception's type and at most three causes' types, never a message (ADR-198 §5); `closing`, the closing line; and the invocation's `started`, `ended`, `state`, `outcome` and `exitCode` (ADR-362 §3). `InvocationReportFile` reads all of them. A command refused before it takes the working-directory lock writes no report: its line exists only on its output.

**What the window reads of a working directory before any child of its own.** As it opens and as another working directory is chosen, the window reads that directory's `next-invocation.properties` (ADR-404) and each row of the list reads its `report.json` for *`<command>` on `<day>`, `<outcome>`* (ADR-393 §5); it draws no report but the watched invocation's (ADR-362 §8).

**A Stop leaves its report running.** Stop kills the child and its descendants (ADR-278 §6), so the report's last write is not made and its `state` stays `running`, which ADR-362 §8 shows as *ended without its last write*. Only the window that pressed Stop knows it was a Stop.

## Decision

### 1. Where it stands, and what the operator sizes

| Part | Id | What it is |
| --- | --- | --- |
| The output's side | `output-and-ending` | a `SplitPane` split up and down, the second item of `steps-and-output`: `output` above, as before, and beneath it the ending panel's scroll, the divider between them moved by the operator |
| The ending panel | `ending-panel` | the stripe, then the texts and the buttons, drawn on the surface; inside a scroll that scrolls up and down and never sideways |
| The stripe | `ending-stripe` | a narrow bar down the panel's left edge, in the ending's colour (§5) |
| The texts | `ending-when`, `ending-kind`, `ending-line`, `ending-faults`, `ending-facts` | top to bottom in this order (§3), each a `SelectableText` |
| The buttons | `ending-open-profile`, `ending-open-report`, `ending-copy` | in one row beneath the texts (§4) |
| The sidebar's grip | `sidebar-grip` | a bar between the sidebar and the main column |
| The steps' grip | `steps-and-output-grip` | a bar directly beneath `steps-and-output` |

**The panel stands beneath the output, in its column.** `steps-and-output` keeps the steps' scroll as its first item and its divider, and its second item is `output-and-ending`. Where the panel has nothing to show (§2), it is not shown, and `output` fills the output's side alone, no divider standing beneath it.

**Every part is sized by the operator.** Dragging `sidebar-grip` sideways moves the sidebar's right edge by as much as the pointer moves, the sidebar no narrower than 220 px; dragging `steps-and-output-grip` up or down moves the bottom edge of `steps-and-output` by as much, no lower than 150 px; the divider between the steps and the output, and the one between the output and the panel, move as a split's divider does. The sidebar opens 420 px wide and `steps-and-output` 300 px high, as today; each size stays as the operator leaves it while the window is open, the working directory changing or not, and is written nowhere.

**Nothing else of the cockpit changes**: the header, `child`, `outcome` and `outcome-open-profile` beside it, the action card, the steps, the charts and the tabs stand and say what they said.

### 2. Which ending it shows, and when

**The ending it shows is the last of the working directory chosen**, the row chosen in `working-directories`:

1. **the window's own child's**, once it has ended, where the child was started on the working directory chosen and no other process has been seen holding that working directory since; or else
2. **the ending `report.json` records** at the top of the working directory chosen, where the file is there, `InvocationReportFile` reads it, and its invocation has ended: its `state` is `ended` and its `exitCode` is set. Such an ending is **dated**: `ending-when` reads `Last invocation, <day and minute>`, the day and minute being `invocation.ended` in the system's time zone, written `EEE d MMM HH:mm` with English names, as *Last invocation, Mon 16 Nov 18:02*; or else
3. **an earlier invocation that ended without its last write**: `report.json` read as in 2, its `state` not `ended`, and no process holding the working directory chosen, as after a Stop, a kill or a crash that the report cannot tell apart. It is dated as in 2 by `invocation.writtenAt`, its last write; or else
4. **none**: where `report.json` is not there, cannot be read, is not that JSON or is of a format the window does not read, the panel is not shown, and nothing is said.

The child's own ending is never dated. **Every ending is shown**: failed or refused, a defect of the control panel, the operator's own Stop, ended, and ended without its last write (§3).

**When it reads.** As the window opens; as another working directory is chosen; as the window's own child ends, once its report has been read for the last time (ADR-362 §8); and at the tick where a hold of the working directory chosen by another process is seen to end, as ADR-404 §5 reads `next-invocation.properties` again.

**Nothing shows while an invocation runs.** The panel is not shown from the moment the window starts a child, by Run, Publish, Save my labels or Auto Label, nor while the window's child runs, nor while another process's holder line is held on the working directory chosen. So it is cleared as the next invocation starts. It has no button that closes it, and cannot be dismissed.

### 3. What it says

| How it ended | `ending-kind` | Colour | `ending-line` | `ending-facts` |
| --- | --- | --- | --- | --- |
| **failed or refused**: a child the window did not stop ending with any code but 0 and 2, or an earlier invocation whose `exitCode` is neither | `Failed or refused` | error | the cause | `Stopped at <step> · exit <code> · ended <time>`, or `Stopped before any step · exit <code> · ended <time>` |
| **a defect of the control panel**: exit code 2 | `Defect of the control panel` | error | the cause | `This is a bug of the control panel, not of your settings.` |
| **stopped** by the window's own Stop | `Stopped` | accent | `Stopped by you at <step>`, or `Stopped by you before any step`; and beneath it, where the child's own report has a `closing`, that line | not shown |
| **ended**: exit code 0 | `Ended` | accent | the cause | `ended <time> · took <duration>`; `ended <time>` where the ending is read from no report |
| **ended without its last write**: an earlier invocation as §2's third case has it, in ADR-362 §8's words | `Ended without its last write` | accent | the cause, from its report alone | `At <step> · last written <time>`, or `Before any step · last written <time>`, `<time>` being its `invocation.writtenAt` |

**The cause** is read from the report the ending is read from, the child's own as ADR-362 §8 accepts it or the earlier invocation's: each of its `refusals`, whole and word for word, a line each in the order written; then its `closing`, where it is none of them. Where the ending is the window's child's and that report holds neither, or the child wrote no report of its own, as one refused before the lock, the cause is its last line of output that is not blank and does not start `Report: `, as `child` shows it today. Where there is none, `ending-line` is not shown.

**`ending-faults`** holds each of that report's `faults`, a line each, in order: `<step>: <type>`, followed by ` (caused by <cause>, <cause>)` where it names causes, and `<type>` alone where it names no step. No message is shown, as none is written. Where there is none, it is not shown.

**The step it stopped at** is, of that report's `steps`, the first whose `state` is `failed`; else the last whose `state` is `running`; else the `step` of the first fault naming one; else none, said *before any step*. A child that wrote no report of its own has none.

**The exit code** is the child's, or the earlier invocation's `exitCode`. **`<time>`** is the report's `invocation.ended`, where the ending is read from a report holding one, and otherwise the instant the window saw its child end, as `HH:mm:ss` in the system's time zone. **`<duration>`** is `invocation.ended` less `invocation.started`, as `<h> h <m> min <s> s`, the hours written only from one hour and the minutes only from one minute: *took 1 h 2 min 3 s*, *took 4 min 0 s*, *took 12 s*.

**The panel words no advice.** Every line it shows is the jar's, the output's, or one of the fixed words above; what to do next is said only where the jar's own line says it.

### 4. Its buttons

- **`ending-open-profile`, Open profile**, is shown, and laid out, only where `ending-line` holds the words *Profile window*, as ADR-389 §6 has the jar write them and as `outcome-open-profile` reads them; it opens the Profile window as the sidebar's Open profile does.
- **`ending-open-report`, Open report**, is shown, and laid out, only where the ending shown has a `report.json` of its own: the window's child's own report, as ADR-362 §8 accepts it, or the earlier invocation's the ending is read from (§2). It is not shown for a child of the window's that wrote no report of its own, as one refused before the lock, whose working directory's `report.json` is an earlier invocation's; whether `report.html` is there plays no part. Pressed, it **selects the Report tab**, `live-report`, and opens nothing outside the window: no browser and nothing handed to the system.
- **`ending-copy`, Copy details**, is shown whenever the panel is. It puts on the system's clipboard the panel's texts shown, `ending-when` to `ending-facts` in that order, a line each, a text not shown left out, and nothing else.

**The Report tab draws the report of the ending the panel shows.** While the panel shows an ending read from a report of its own, the Report tab draws that report, an earlier invocation's among them, as it draws the watched invocation's once its child has ended (ADR-362 §8): its status line, the display and what was opened, the gates, the refusals, the faults, the closing line, the next value, the analysis reports and the files, never marked stale, and a report still `running` said to have ended without its last write. It does so as the panel reads (§2), so the tab and the panel name the same invocation. Where the panel shows the window's child's ending and the child wrote no report of its own, the tab says *this invocation wrote no report*, as today. **As a child starts, the tab follows that child again**, at the moment the panel clears: it says *waiting for this invocation's report* until the child's own report is read, as ADR-362 §8 has it. The steps' rows, the legs and the charts are not drawn from an earlier invocation's report: they stay the watched invocation's, as ADR-362 §8 has them.

### 5. Its colours and its texts

The panel is drawn on **`-vespera-surface`**; its stripe and its kind in **`-vespera-bad`** where the ending failed or was refused or is a defect of the control panel, and in **`-vespera-accent`** where it was stopped, ended, or ended without its last write; `ending-line` and `ending-faults` in `-vespera-ink`; `ending-when` and `ending-facts` in `-vespera-dim`; the buttons in the stylesheet's one style (ADR-344 §1). No token is added, every colour is the stylesheet's, and no class sets one (ADR-381 §2).

Measured on the surface, as WCAG 2 has the ratio:

| Text | Light | Dark |
| --- | --- | --- |
| `-vespera-bad` | 6.45 : 1 | 7.06 : 1 |
| `-vespera-ink` | 16.78 : 1 | 13.05 : 1 |
| `-vespera-dim` | 5.77 : 1 | 6.86 : 1 |
| `-vespera-accent` | 7.92 : 1 | 7.16 : 1 |

so every text the panel shows stands at 4.5 : 1 or more in both themes, and a test holds it as drawn. **Every text of the panel is a `SelectableText`**, its kind's words among them, and none is a `Label` (ADR-364 §8).

### 6. ADR-383's warning

The warning for a publish of the window's own ending with 3 or 4 is unchanged: its words, its field, and what a title entered, Cancel and blanks do. The panel records that ending as any other failed or refused one, beneath the output, while the warning asks.

### 7. What changes, by class, and run ids

- **A new class of `ui`, `EndingPanel`**, draws the panel and its parts, works out what it shows from an `Ending`, an `InvocationReportFile.Report` and the working directory chosen, as §2 to §4 have it, and copies its texts.
- **`Cockpit`** puts `output` and the panel in `output-and-ending`, adds the two grips, tells the panel as a child starts and ends, as another working directory is chosen and as another process's hold ends, and has the Report tab draw the report of the ending the panel shows, and follow a child again as it starts.
- **`LiveReport`** draws a report that is no watched invocation's, as an ended one, never stale, a report still `running` said to have ended without its last write; its javadoc says so.
- **`control-panel.css`** gains the panel's, the stripe's and the grips' rules, from the existing tokens; the sidebar's 420 px and `steps-and-output`'s 300 px are the sizes each opens at.
- **Documents**: the README's *What the window shows* and `docs/architecture.md`'s paragraph on the control panel say the panel is there; `CONTEXT.md` gains the entry Ending panel.

`io.algernon.vespera.ui` is in no module and no class of the jar changes, so **no run id moves**.

## Consequences

- Why an invocation ended is read beside its output, without scrolling it, and as the window opens the last ending of the working directory chosen is there, dated.
- A command refused before the lock leaves nothing a later opening of the window can read: the panel then shows the last ending a report records, which is older, and dated.
- `child`, `outcome` and its Open profile say what they said, so a closing line can be read in the header and in the panel at once.
- A publish ending with 3 or 4 shows the warning and the panel together.
- The Report tab, as the window opens, can show an invocation that ended before it, the one the panel shows; it no longer stands empty until a child or another process is watched.
- An earlier Stop and an earlier crash read alike, *ended without its last write*: only the window that pressed Stop says *Stopped by you*, and only until another working directory is chosen or the window closes.
- ADR-344 and ADR-362 carry a note at their heads pointing here.

## Alternatives refused

- **A card in the middle of the window, over the cockpit dimmed, dismissed by a button.** It hides the run it explains, and dismissed it leaves nothing to read.
- **A panel in the middle of the column, above the steps and the output, or a third column between them, or one beneath both at the window's width.** Each moves parts of the cockpit the operator reads in place; beneath the output, the panel stands beside the lines it explains.
- **A cause handed over by the child**: a structured closing line, or a file of its own beside the report. The report already holds the refusals, the faults, the closing line and the exit code, and a new contract with the child is one more thing the window and the jar must agree on.
- **What to do next, worded by the panel.** The window would claim to know a remedy the jar did not state, and be wrong where it guessed; the jar's lines already name the key and the Profile window where they can.
- **The panel for failures alone.** An ending that went well, and the operator's own Stop, would leave the panel holding an earlier failure.
- **The output's last lines repeated in the panel.** The output panel stands right above it.
- **Open report handing `report.html` to the system, or opening it in a browser.** The control panel opens no report outside its window, and the Report tab is `report.html`'s view (ADR-362 §8).
- **Open report offered wherever `report.html` is there.** For a child that wrote no report of its own, that page is an earlier invocation's, and would be opened as the child's.

## What this does not decide

- **The two dividers' opening positions and the panel's least height**, the implementation's under ADR-342 §3, and whether any size is kept from one opening of the window to the next.

## Tests

| Method | What it holds |
| --- | --- |
| `ui.TheEndingPanelTest.theLastEndingOfTheWorkingDirectoryChosenIsShownDatedAndNothingWhereItHasNone` | §2, §3, §4: as the window opens on a working directory whose last invocation was refused after the lock, the panel shows it dated, Failed or refused, the refusal once although it is also the closing line, before any step, its exit code and its time, Open report, its report being its own, and no Open profile; Copy details copies its texts a line each; a working directory with no report shows no panel; one ended at a gate shows Ended, its closing line, its time and how long it took, and Open report with no `report.html` there; the first chosen again shows its ending again |
| `ui.TheEndingPanelTest.aFailedChildIsShownWithItsLineItsFaultTheStepItStoppedAtItsExitCodeAndItsTime` | §2, §3: no panel on a working directory with no report; the window's child failing with a report of its own shows, undated, its closing line, its fault with its causes, the step that failed, its exit code and its report's time |
| `ui.TheEndingPanelTest.aChildRefusedBeforeTheLockIsShownWithItsLastLineOfOutput` | §2, §3: the earlier invocation's ending, dated, gives way to the window's child's, undated, its last line of output, before any step, its exit code and the time the window saw it end, and no Open report, the `report.json` there being the earlier invocation's |
| `ui.TheEndingPanelTest.aDefectOfTheControlPanelSaysItIsABugOfTheControlPanelNotOfYourSettings` | §3, §5: exit code 2 shows Defect of the control panel, the child's line and the fixed sentence, its kind in the error colour |
| `ui.TheEndingPanelTest.theOperatorsStopIsShownAsStoppedByYouAtItsStepAndNothingShowsWhileTheChildRuns` | §2, §3, §5: the earlier ending shows as the window opens and nothing while its child runs; the operator's Stop shows Stopped by you at the step its report said was running, no facts, Open report, its kind in the accent |
| `ui.TheEndingPanelTest.anEndedChildIsShownWithItsClosingLineWhenItEndedAndHowLongItTook` | §3, §4: exit code 0 shows Ended, its closing line, its time and *took 1 h 2 min 3 s*, Open profile where the line names the Profile window, and Open report, its report being its own, with no `report.html` there; Open profile opens the Profile window |
| `ui.TheEndingPanelTest.openReportSelectsTheReportTabDrawingTheEndingsReportAndAChildStartingTakesItBack` | §4: Open report selects the Report tab, which draws the earlier invocation's report the panel shows, its closing line and its status; another working directory chosen, the tab draws that one's; a child started, the panel clears and the tab waits for the child's report, the earlier closing line gone |
| `ui.TheEndingPanelTest.anEarlierInvocationWhoseReportNeverEndedIsShownAsEndedWithoutItsLastWrite` | §2, §3, §4, §5: a report still `running`, the working directory held by nobody, shows dated by its last write, Ended without its last write, `At extraction · last written <time>`, Open report and Copy details, its stripe and kind in the accent; Open report selects the Report tab, which says it ended without its last write and marks it not stale |
| `ui.TheEndingPanelTest.theEndingPanelLiesBeneathTheOutputAndTheSidebarAndTheStepsAreDraggedToSize` | §1: `steps-and-output` keeps the steps first, its second item `output-and-ending`, split up and down, `output` above and the panel beneath; `sidebar-grip` dragged 80 px widens the sidebar by 80 px and far left leaves it 220 px wide; `steps-and-output-grip` dragged 80 px down heightens the split by 80 px and far up leaves it 150 px high |
| `ui.TheEndingPanelTest.everyTextOfTheEndingPanelIsSelectableAndStandsAtAaInBothThemes` | §5: in both themes, for an ending failed or refused and one ended, the panel on the surface, its stripe and kind in the error colour or the accent, every text at 4.5 : 1 or more on the surface, and no text a label |
| `ui.PublishToConfluenceAsksForTheTokenTest.aTitleNamingSeveralAsksTooAndCancelOrBlanksSaveAndStartNothing` | §6: amended so that each publish ending with 4 also shows the panel, Failed or refused, before any step, exit 4 and its time, beside the warning, which is unchanged |

The panel's least height is held by no test, being undecided. That the panel is shown again at the tick where another process's hold ends is held by no test: `TheValuesWantedFollowTheWorkingDirectoryTest` holds that tick for the values wanted, and the panel is told at the same place in `Cockpit`.

## What the implementation owes

§7's classes, `control-panel.css`'s rules and the documents, with each test above green.
