# ADR-414 — The analysis reports open in a Reports window of their own, a tab each, and the cockpit keeps its list and two tabs

- **Date**: 2026-11-18
- **Status**: accepted on 2026-11-18.
- **Decided**: by the operator, on [#858](https://github.com/algernon28/vespera/issues/858): that the analysis reports are read in **one** Reports window holding them as tabs, and not in a window for each report (§2); that it is a `Stage` of its own as the Profile window is, with no owner and no modality, following the cockpit's stylesheet and its `dark` style class, every text of it selectable and copyable (§2, §6); that the cockpit keeps the sidebar's Reports section, its way in, and keeps the Report tab and the Inclusions tab in its own tabs pane, only the analysis reports' tabs leaving it (§1); that a change of the working directory closes the Reports window, that it keeps its size and place for the cockpit's next opening of it as the Profile window does, and that closing the cockpit closes it (§4, §5). Under the operator's delegation: the window's title and ids, that it opens on the first report opened and on none that cannot be read, that a report open is selected and the window shown again and brought to the front, that closing its last tab closes it and closing it closes its tabs, that each tab keeps its Reload, that the lines said where a report is not opened stay the cockpit's, that every way in opens the report there, the classes, and the tests.
- **Amends**: [ADR-344](0344-the-control-panels-window-is-one-cockpit-run-and-stop-the-steps-as-bars-beside-the-output-the-progress-pages-three-charts-the-profile-and-the-folders-to-include-starting-from-the-real-configuration.md) §6, whose report opens *"in a tab of its own in the pane of the Profile and Inclusions tabs, after them in the order the reports are opened"*, and whose *"every report's tab closes when the field changes"*: it opens in a tab of the Reports window (§2, §3), and the working directory's change closes that window (§4). It carries a note at its head.
- **Amends**: [ADR-362](0362-an-invocations-report-is-one-json-file-drawn-as-static-html-through-a-mustache-template-and-no-command-opens-anything.md) §10, *"Where they sit"*, whose report, pressed, *"opens in a closable tab of its own in the pane of the Report, Profile and Inclusions tabs, after them in the order reports are opened, and is selected; one tab per report, selected again where it is open; every report's tab closing with a change of working directory"*: it opens in a closable tab of the Reports window, as §2 to §4 have it. It carries a note at its head.
- **Amends**: [ADR-389](0389-the-profile-is-edited-in-a-window-of-its-own-opened-from-the-first-section-of-the-sidebar.md) §1, whose tabs pane holds `live-report` and `inclusions` *"and after them a tab for each report opened (ADR-362 §10)"*: it holds the two alone (§1); and §2, whose *"A report link on a row opens the report's tab in the cockpit"*: it opens it in the Reports window (§3). It carries a note at its head.
- **Keeps**: ADR-362 §10's table of a report's tab and its parts, every id in it, `analysis-report-<name>`, `reload-<name>`, `written-<name>`, `headline-<name>`, `section-<name>-<id>`, `chart-<name>-<id>` and `table-<name>-<id>`; how a cell leads, how a report is read, and the lines said where one cannot be; and the drawing by JavaFX's controls from `<name>.json`, never by a `WebView`. ADR-344 §6's Reports section, `reports-list`, a link for each report whose JSON the working directory holds and a grey line for each it does not. [ADR-377](0377-a-profile-row-is-one-line-its-provenance-in-a-tooltip-a-list-is-tags-a-code-set-named-by-its-folder-and-no-label-file.md) §5, a row's links reading their reports' titles. ADR-362 §8, the Report tab first of the cockpit's tabs and not closable, a report's name in it opening that report. ADR-389 §1, Inclusions a tab of the cockpit's, and §2 to §4, the Profile window.
- **Read with**: [ADR-381](0381-every-view-inside-the-control-panels-window-follows-its-light-and-dark-switch-at-once-its-popups-included.md) §1, [ADR-374](0374-every-text-line-and-marker-of-the-window-its-charts-and-each-analysis-reports-native-view-stands-at-aa-in-both-themes.md) and [ADR-364](0364-the-output-panel-is-copyable-text-of-the-whole-output-from-a-file-of-its-own-and-every-text-of-the-window-is-copyable.md) §8, whose rules for the views of the control panel's window hold of the Reports window (§6).
- **Settles**: [#858](https://github.com/algernon28/vespera/issues/858).

## Context

**Where a report is read.** `ReportsPane` opens an analysis report in a tab of the cockpit's tabs pane, after the Report tab and Inclusions, in the order the reports are opened, and selects it; a report whose tab is open has that tab selected, neither opened nor read again; and every report's tab closes when the working-directory field changes (`ReportsPane.closeTabs()`, called by the field's listener in `Cockpit`). The tabs pane lies beneath the steps, the output and the charts, and shares the cockpit's one scroll with them: a report selected hides the Report tab, and a report and the run are not seen together. The Profile window was moved out of the same pane for the same reason (ADR-389's Context).

**The ways in.** Each but a cell opens a report by its name through one call, `Cockpit.openReport(String)`, which today opens it through `ReportsPane`; a cell leads through `ReportsPane.leadTo`, below:

| Way in | Where it is pressed |
| --- | --- |
| a report's link in the sidebar's Reports section, `reports-list` | the cockpit |
| a report's name in the Report tab, `report-analysis` (`LiveReport`) | the cockpit |
| a row's link to a report its key is read off, `<prefix><key>.report-<name>` | the Profile window (`ProfileForm`), the cockpit's `wanted` (`WantedValues`), and Seed folder's row, `seed-` |
| an `analysisReport` cell of a report's table (`AnalysisReportView`) | the report's own tab |

Seed folder's row is handed the same call, but `seedFolder` is read off no report (`Reports.OF_A_KEY`), so it draws no link. A cell leads through `ReportsPane.leadTo`, which opens the report where its JSON is written and otherwise has `outcome` say *`<name>` is not written yet, so it is not opened*.

**The Profile window** (ADR-389 §2, §3) is a `Stage` of its own, titled, with no owner and no modality, one at a time, brought to the front where it is opened again, its size and place kept by the cockpit from its closing to its next opening and written nowhere; its root carries the cockpit's stylesheet and follows its `dark` style class (ADR-389 §7).

## Decision

### 1. The cockpit

**The sidebar is unchanged**: its last section, `reports`, titled Reports, holds `reports-list`, a link for each report whose JSON the working directory holds and a grey line for each it does not, as ADR-344 §6 and ADR-362 §10 have it.

**The cockpit's tabs pane holds `live-report` and `inclusions`, and nothing else.** No analysis report's tab opens there, and the cockpit's scene holds no `analysis-report-<name>` and no node of a report's view.

### 2. The Reports window

**An analysis report opens in the Reports window**: a `Stage` of its own, titled **Reports**, whose scene's root holds one `TabPane`, `analysis-report-tabs`, each of its tabs a report's, drawn as ADR-362 §10's table has it, every id within it unchanged, and closable. It has **no owner and no modality**, so the cockpit, the Profile window and it are each moved, resized, minimized and brought in front of the others by the operator, and none blocks another; and it is resizable.

**There is one at a time**, and **it opens on the first report opened**: no Reports window stands before a report is opened, and none opens for a report that cannot be read (§3).

### 3. Opening a report

**Every way in opens the report in the Reports window**: the sidebar's link, the Report tab's name, and a row's link in the Profile window, in `wanted` and in Seed folder's row, each through `Cockpit.openReport(String)` as before; and an `analysisReport` cell of a report open in the Reports window through the window's own lead, as it went through `ReportsPane.leadTo` before, which opens the report there where its JSON is written.

| The report | What the window does |
| --- | --- |
| its tab is open in the Reports window | that tab is selected; the window is shown again where it is minimized, brought to the front and given the focus; the report is neither opened nor read again |
| no tab is open for it, and its JSON is read | its tab is added after the tabs open, in the order the reports are opened, and selected; the Reports window is opened where none is, as §5 places it, or shown again where it is minimized, and brought to the front and given the focus |
| its JSON cannot be read, is not that JSON or is of another format | no tab opens, and no Reports window opens; one open stays as it is; `outcome` says ADR-362 §10's line, *`<name>.json` could not be read* or *`<name>.json` is of format `<N>`, which this window does not read* |
| an `analysisReport` cell names it, and its JSON is not there | nothing opens; `outcome` says *`<name>` is not written yet, so it is not opened* |
| no working directory is named | nothing opens and nothing is said, as before |

**The lines are said in the cockpit's `outcome`**, the one line the last action said (ADR-344 §1), whichever window the press was made in; the Reports window carries no line of its own.

**Each tab keeps its Reload**, `reload-<name>`, which reads `<name>.json` again and draws the tab again, titled by the title read; a JSON it cannot read leaves the tab as it was drawn, `outcome` saying the line above.

### 4. Closing it

- **A tab closed by its close button** closes that report. **Closing the last tab closes the Reports window**: the window is there to hold reports, and with none it would show nothing and offer no way to open one, every link to a report lying in the cockpit or the Profile window.
- **Closing the Reports window by the system's close button closes it at once**, asking nothing, since nothing in it is edited, **and closes its tabs with it**: opened again, it holds the report it is opened for, alone. The window is open exactly while it holds a tab.
- **Another working directory closes the Reports window**, its tabs with it, whenever the working-directory field's value changes, where ADR-344 §6 closed every report's tab: each tab was drawn from the working directory the field named as it opened.
- **Closing the cockpit closes the Reports window**, asking nothing, once the cockpit closes, as the Profile window closes with it (ADR-389 §4).

### 5. Its size and place

**The Reports window's size and place are kept by the cockpit that opened it**, from its closing, by any of §4's ways, to its next opening, so it opens again where and as large as it was left; and **they are written nowhere**: no file, no preference of the user's and no launch value holds them, and a control panel started again opens it as at its first opening. Its first size and place are the implementation's.

### 6. Its theme, its texts and its contrast

The Reports window is a view of the control panel's, and ADR-381 §1, ADR-374 and ADR-364 §8 hold of it as of the cockpit and the Profile window (ADR-389 §7): **its root carries the cockpit's stylesheet and takes the `dark` style class at once whenever the cockpit's root does, and loses it with it**, nothing reopened, a popup over it, a table's context menu among them, following as one over the cockpit does; every text of a report's view stands at 4.5 : 1 and every bar, line, marker and swatch of its charts at 3 : 1 in either theme; every text it shows outside a control is a `SelectableText`; and a table copies the rows selected as ADR-364 has it.

### 7. What changes, by class, and run ids

- **`ReportsPane`** keeps the sidebar's list, `pane()` and `draw()`, and is no longer handed the cockpit's tabs pane.
- **A new class of `ui` holds the Reports window**: it opens it, adds, selects and reads a report's tab, its Reload and its cell's lead, brings it back, keeps its size and place, follows the cockpit's theme, and closes it, with its tabs, where its last tab closes and where it is told to. It is named `AnalysisReportsWindow`, since `CONTEXT.md`'s Analysis report entry refuses *report* alone in a name added from ADR-362 on.
- **`Cockpit`**: `openReport(String)` opens through it; the working-directory field's listener closes it where it called `reports.closeTabs()`; `closed()` closes it beside the Profile window; and its tabs pane holds the two tabs of §1.
- The javadocs of `Cockpit`, `ReportsPane` and `LiveReport` say where a report opens.
- **Documents**: the README's paragraphs on the reports' titles and on the Reports section, `docs/architecture.md`'s paragraph on the control panel, and `CONTEXT.md`'s entries Analysis report and Control panel say the reports open in the Reports window.

`io.algernon.vespera.ui` is in no module and no class of the jar changes, so **no run id moves**.

## Consequences

- A report is read beside the run: the cockpit's steps, output, charts and Report tab stay in view while a report is open, and the cockpit's tabs pane no longer grows with the reports opened.
- A report opened from the cockpit or the Profile window comes in front of the window it was opened from; bringing that window back is the operator's.
- A line said where a cell's report is not written is read in the cockpit's `outcome`, which may lie behind the Reports window the cell was pressed in.
- Closing the Reports window loses its tabs: each is opened again by its link, and read afresh as it opens.
- ADR-344, ADR-362 and ADR-389 carry a note at their heads pointing here.

## Alternatives refused

- **A window for each report.** The operator chose one window with tabs.
- **Moving the Reports section, the Report tab or Inclusions into the Reports window.** The operator kept them in the cockpit.
- **A Reports window left open with no tab.** It would show nothing and offer no way to open a report.
- **Keeping its tabs while it is closed, for its next opening.** It would open again on reports read when it was last open, beside the one it is opened for, which the operator had closed.
- **The cockpit as its owner, or a modal window.** As ADR-389's Alternatives refused have it for the Profile window: an owned window is held above its owner on some systems and minimized with it, and a modal one blocks the cockpit while a report is read.
- **A line of the Reports window's own for the lines §3 says.** `outcome` is the one line the last action said (ADR-344 §1); a second would hold some of those lines and not others.

## What this does not decide

- **The Reports window's first size and place**, and its stylesheet's sizes and spaces, the implementation's under ADR-342 §3.
- **Whether its title names the report selected.**
- **Whether a link pressed in the Reports window brings the cockpit to the front.**
- **Moving a tab to another place among the tabs, or out into a window of its own.**

## Tests

| Method | What it holds |
| --- | --- |
| `ui.TheAnalysisReportsWindowTest.theAnalysisReportsOpenInOneWindowOfTheirOwnATabEach` | §1, §2, §3: no Reports window before a report is opened, and the sidebar holding `reports-list`; a report's link opens one window, with no owner and no modality, resizable, titled Reports, holding that report's tab alone; a second report adds its tab after it, selected; the window minimized and the first link pressed again, after its file is written with another title, opens no other window, shows the one again, selects the first tab, adds none and keeps the title it was opened with; its Reload reads the new title; and the cockpit holds `live-report` and `inclusions` alone |
| `ui.TheAnalysisReportsWindowTest.everyWayInOpensItsAnalysisReportInTheAnalysisReportsWindow` | §3: a report's name in the Report tab, a link of a row of `wanted` and an `analysisReport` cell each open their report's tab in the Reports window, after the tabs open, selected; a cell naming a report whose JSON is not there opens no tab and `outcome` says it is not written yet; the cockpit holds its two tabs alone and nothing is handed to the system |
| `ui.TheAnalysisReportsWindowTest.closingItsLastTabClosesItAndItsPlaceAndSizeAreKeptUntilTheControlPanelExits` | §4, §5: one of two tabs closed leaves the window open with the other; the last closed closes it; a report opened after opens it where and as large as it was left, its tab alone; closed by its close button with two tabs it closes, and the report opened next is its one tab; and a control panel started again over the same launch values file does not open it there and so large |
| `ui.TheAnalysisReportsWindowTest.anotherWorkingDirectoryAndClosingTheCockpitCloseTheAnalysisReportsWindow` | §4: another working directory named in the field closes the window open with two tabs; a report opened after opens alone, read from the new working directory; and closing the cockpit closes the window with it |
| `ui.TheAnalysisReportsWindowTest.theAnalysisReportsWindowFollowsTheThemeSwitch` | §6: the window's root carries the cockpit's stylesheet, and takes and loses `dark` as the switch moves the cockpit, while it is open |
| `ui.TheAnalysisReportsOpenNativelyTest.anAnalysisReportsLinkOpensItsNativeViewInATabOfTheWindow` | §2: amended so that the report's tab, its headline, chart and table are read in the Reports window, its one tab, and the cockpit keeps its two tabs |
| `ui.TheAnalysisReportsOpenNativelyTest.aJsonThatCannotBeReadOrIsOfAnotherFormatOpensNothing` | §3: amended so that neither JSON opens a Reports window either |
| `ui.ARowsAnalysisReportsReadTheirTitlesTest.aLinkOpensItsAnalysisReportsNativeView` | §3: amended so that a row's link in the Profile window opens its report's tab in the Reports window |
| `ui.EveryBarOfTheStepsCarriesItsPercentTest.aNativeViewNamesNoColourOfItsOwnAndStandsAtAaInBothThemes` | §6: amended so that the report's view is measured in the Reports window, in both themes |
| `ui.EveryTextOfTheWindowCanBeCopiedTest.everyTextShownOutsideAControlIsSelectableText` | §6: amended so that the walk opens a report and reaches the Reports window from its root, its headline and its paragraph among the texts reached |

That opening a report brings the window to the front and gives it the focus is held by no test: the Headless platform reports every window focused and keeps no order of windows a test can read; that it shows the window again where it is minimized is held. A popup over the Reports window following the switch, a table's context menu among them, is held by no test, as ADR-389's Tests say of the Profile window: `ThemeSwitch.restylePopups` restyles the popups whose owner is the cockpit's window, and a popup over the Reports window hides as the cockpit's switch is clicked, so the operator cannot move the switch while one shows. Seed folder's row draws no link to a report, so no test presses one.

## What the implementation owes

§7's classes and javadocs, with each test above green.
