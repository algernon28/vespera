# ADR-442 — The Architecture window draws the reference picture and lights what runs, from what the cockpit reads

- **Date**: 2026-11-22
- **Status**: accepted on 2026-11-22.
- **Decided**: by the operator, on [#878](https://github.com/algernon28/vespera/issues/878): that the cockpit opens, from a sidebar section of its own, a window that draws the reference picture of the architecture, its parts placed as the picture places them, and lights the part running (§1, §2, §4); that the part lit is read from what the cockpit already reads, and not from a source of its own (§3); that a state is told by a colour, an outline and a word, never by a colour alone (§5); that every wire is an arrow pointing the way data goes, arrows running along it while it carries data, and that only a running part and those arrows move, behind a switch that follows the system's setting (§6); that it fits the window, or stands at its full size with scrolling (§7); and that nothing in it is pressed to act (§8). Under the operator's delegation: the window's title and ids, the words of the parts, its first size, the classes and the tests.
- **Keeps**: [ADR-389](0389-the-profile-is-edited-in-a-window-of-its-own-opened-from-the-first-section-of-the-sidebar.md) §2, §3 and §7 and [ADR-414](0414-the-analysis-reports-open-in-a-reports-window-of-their-own-a-tab-each-and-the-cockpit-keeps-its-list-and-two-tabs.md) §2, §5 and §6, the own window, whose rules the Architecture window follows as the Profile and Reports windows do; [ADR-361](0361-the-cockpits-steps-fold-into-the-progress-pages-four-legs-and-its-copy-of-their-table-is-held-to-the-pages-by-a-test.md) §4, a leg's state from its steps' states, whose rules a stage of the drawing follows; [ADR-387](0387-a-step-still-running-in-the-report-of-a-child-that-ended-is-drawn-stopped-or-failed.md) §1, a step still running in the report of a child that has ended; [ADR-433](0433-docling-and-ollama-are-containers-where-vespera-runs-them-and-never-sidecars-and-the-earlier-names-are-still-honoured.md), the containers' word.
- **Read with**: [ADR-381](0381-every-view-inside-the-control-panels-window-follows-its-light-and-dark-switch-at-once-its-popups-included.md) §1, [ADR-374](0374-every-text-line-and-marker-of-the-window-its-charts-and-each-analysis-reports-native-view-stands-at-aa-in-both-themes.md) and [ADR-364](0364-the-output-panel-is-copyable-text-of-the-whole-output-from-a-file-of-its-own-and-every-text-of-the-window-is-copyable.md) §8, whose rules for the views of the control panel hold of this window.
- **Settles**: [#878](https://github.com/algernon28/vespera/issues/878).

## Context

**The operator keeps a picture of the architecture**: the inputs, the document archive and the seed folder; the operator's machine, holding the control, the control panel and the Vespera CLI, the application's stages 0 to 6b, with 6a and 6b at the human gates, the SQLite ledger beneath them, and the Docker containers, Docling, Ollama and the GPU; the outputs, the deliverable and the analysis reports; the connectors, Confluence and the website; and a legend of four colours. Nothing in the cockpit shows which of those parts is at work while an invocation runs: the steps chart says which step runs, not what that step reads, calls or writes.

**What the cockpit already reads.** Each reading of the working directory's `report.json` gives the steps chart every step's state and, for a child that ended before its last write, the word a step still running reads instead (ADR-387 §1). The cockpit also holds which command it last started, whether its child is alive and how it ended (ADR-278 §5), the header's last check of Docling and Ollama (ADR-430 §3), the converter's image in force, and the destinations ticked beside Publish.

**What each step touches, as the code has it.** Read from the tasklets and the step configurations of `engine.pipeline`, not from their imports alone:

| Step | Reads or calls | Writes, beside the ledger |
| --- | --- | --- |
| `census` | the document archive and the seed folder, walked | |
| `byte-level-reduction` | the document archive, hashed | `format-mix` |
| `extraction` | the document archive, through Docling | `extraction-failures`, `extraction-survivors` |
| `content-census` | | `confidence-distribution`, `document-frequency`, `word-count-distribution` |
| `redundancy-signature`, `content-redundancy` | | |
| `seed-extraction` | the seed folder, through Docling | |
| `seed-corpus-comparison` | | `seed-corpus-comparison` |
| `embedding-scoring` | Ollama, embedding the stored conversions, which it reads from the extraction cache and does not ask Docling for | |
| `relevance-scoring` | Ollama, embedding the topics' names | |
| `relevance-floor` | | |
| `clustering` | | `cluster-sizes` |
| `relevance-report` | | `relevance-labelling` |
| `arrangement` | Ollama, naming the topics | `topics`, `arrangement` |
| `generation` | Ollama, writing each group; the document archive, copied | the deliverable, `cluster-page-sizes` |

`vespera label --auto` asks Ollama; `vespera label` reads the label file alone; both write the ledger. `vespera publish` reads the deliverable and sends it to the destinations named. The containers are started with `compose.gpu.yaml` beside `compose.yaml`, which gives Docling and Ollama the GPU, exactly where the converter's image in force is the image `compose.gpu.yaml` builds (`ContainerStarter`).

## Decision

### 1. The way in, and the window

**The sidebar holds a twelfth section, the last, `architecture`, titled Architecture**, holding one button, `open-architecture`, *Open the architecture*. It opens **the Architecture window**: a `Stage` of its own, titled **Architecture**, with **no owner and no modality**, resizable, one at a time, shown again, brought to the front and given the focus where it is opened again, its size and place kept by the cockpit from its closing to its next opening and written nowhere, following the cockpit's stylesheet and its `dark` style class at once, as ADR-389 §2, §3 and §7 and ADR-414 §5 and §6 have it of the Profile and Reports windows. **Closing it loses nothing** and asks nothing; **closing the cockpit closes it**.

### 2. The drawing

**The window draws the reference picture, its parts where the picture places them**: the Inputs, the document archive and the seed folder; the operator's machine, holding the control, the control panel and the Vespera CLI, the Vespera application, its eight stages 0, 1, 2, 3, 4, 5, 6a and 6b in a row, 6a and 6b in the human gate's colour, the SQLite ledger beneath them, and the Docker containers, Docling, Ollama and the GPU; the Outputs, the deliverable and the reports; the Connectors, Confluence and the website; wires between the parts that pass data; and the legend. Each part is its own node, `architecture-<part>`, and each wire its own, `architecture-wire-<wire>`.

**The picture's places are the drawing's own, and what lies behind them is one table.** Which stage of the picture each step of the job lies in is read from `StopCosts.stageOf`, the stage under whose runs the jar records the step; what each step touches is the table of the Context, held in the drawing's class as one row a step, in the order of `StopCosts.ROWS`. A step added to `StopCosts.ROWS` and not to that table, or the other way, fails a test; a stage added to the jar needs a box placed in the drawing too.

### 3. What lights a part, and from what

The window is handed the report and the word the steps chart is handed at the same place (ADR-387 §1), and is cleared where the steps chart is, as a new invocation is first watched, until that invocation's report is read; and it reads, as it draws, the command the cockpit last started, whether its child is alive and how it ended, the header's last check, the converter's image in force and the destinations the last publish was started with, as ticked when it started and not as ticked since. **The converter's image in force is the one the header's last check found Docling running** (its `/version`'s `vespera-image`, ADR-440); where no check kept one, the Services field's, or where that is blank the one the sources start from. Its own reading of those last is made once a second while it is shown.

| Part | Lit, and how |
| --- | --- |
| a stage | its steps' states folded as a leg's are (ADR-361 §4): waiting, finished, failed, stopped, stopped at a gate or running; a step still running in the report of a child that ended reads as the steps chart reads it (ADR-387 §1) |
| the Vespera CLI | running while the cockpit's child is alive; once it ends, how it ended: finished where it ended, stopped where Stop ended it, failed otherwise (ADR-278 §5) |
| the SQLite ledger | running while a step runs, or while `vespera label` runs |
| the document archive, the seed folder, the reports, the deliverable | running while the step running touches it (§2's table); the deliverable also while `vespera publish` runs |
| Docling, Ollama | down where the header's last check found it not answering; else running while the step running calls it, Ollama also while `vespera label --auto` runs |
| the GPU | down where the containers do not run with `compose.gpu.yaml`, the converter's image in force not being the GPU image; else running while Docling or Ollama runs |
| Confluence, the website | only for a `vespera publish` that names it: running while it runs, then how it ended, as the CLI |
| the control panel | never: it is the window the operator is looking at |

**A line of selectable text above the drawing, `architecture-now`, says the same in words**: the stage, the step and how far it has got, what it calls and that it writes the ledger, while a step runs; the command while a label or a publish runs; how the last command ended, and at which step, once it ended; *nothing is running* otherwise.

### 4. Parts are drawn as the picture draws them, wires as it draws arrows

The legend keeps the picture's colours, a kind each: pipeline stage, local AI container, input, storage and output, control and human gate, and connector. **Each is drawn in the themes' series colour of its hue** (ADR-381), violet, blue, green, orange and pink, which each theme sets at 3 : 1 or more against the surface (ADR-374), so the window adds no colour of its own to either theme; every part is filled with the surface, as the cockpit's cards are.

### 5. A state is a colour, an outline and a word

| State | Outline | Word |
| --- | --- | --- |
| running | the accent's, thick, the box filled with the rule's colour | ▶ running |
| finished | the part's own, thicker | ✓ finished |
| failed | the colour of a fault, dashed | ✕ failed |
| stopped | the faint colour, dotted | ■ stopped |
| stopped at a gate | the human gate's, double | ⏸ at a gate |
| down | the faint colour | ○ down |
| waiting, or not at work | the part's own | none |

**Every word is drawn in the ink colour**, so its contrast does not rest on the state's colour, and **every text of the window stands at 4.5 : 1 against the box it lies in, in either theme** (ADR-374). **Every text of the window outside a control is a `SelectableText`** (ADR-364 §8): it is selected and copied, and not edited.

### 6. Motion

**Every wire is an arrow pointing the way data goes**: from the inputs into the application, from the control panel to the CLI and on into the application, from each stage to the ledger, from the application to the outputs and from the outputs to the connectors, its arrowhead at the end data arrives at, `architecture-arrow-<wire>`. **Data goes both ways between the application and Docling, the application and Ollama, and each of the two and the GPU**, a request out and an answer back, so those wires carry an arrowhead at each end, the second `architecture-arrow-<wire>-back`. The application's wires to Docling and Ollama leave it beneath the stages and pass over the ledger, so that none says data goes between the ledger and a container. A wire carrying data, whose part at its far end is running, is drawn in the accent, its arrowheads with it; **a container's wire to the GPU carries data only while that container runs on it**, and **the wire into the deliverable only while a step runs**, a publish reading the deliverable and writing none.

**Two things move, and nothing else**: the outline of a running part pulses, and along a wire carrying data run arrows, chevrons a gap apart pointing and moving the way data goes, in a lane `architecture-runners-<wire>`; where data goes both ways, in two lanes side by side, each to the right of the way it goes, the second `architecture-runners-<wire>-back`. A wire too short to hold a runner between its arrowheads, a stage's to the ledger and Ollama's to the GPU, says its flow by its accent and arrowheads alone. A finished, failed, stopped or waiting part never moves, and with motion off the drawing reads the same, the runners of a flowing wire standing still where they point the way data goes. **The window's switch, `architecture-motion`, reads *Motion: as the system*, *Motion: on* or *Motion: off***, *as the system* first, which follows the system's reduced-motion setting as JavaFX reads it (`Platform.Preferences.reducedMotion`). **The motion stops while the window is hidden or minimized**, and the once-a-second reading of §3 with it.

### 7. Size

**The drawing fits the window**, scaled down or up as the window is resized, by default; **a toggle, `architecture-full-size`, *100%*, draws it at its own size**, the window scrolling over it.

### 8. Out of scope

Nothing in the window is pressed to act: no part opens, starts or stops anything, nothing is published from it, and the drawing is not edited from the cockpit.

### 9. What changes, by class, and run ids

- **New classes of `ui`**: `Architecture`, the parts, the step table, the wires and the lighting of §3, which links no JavaFX class; `ArchitectureView`, the drawing, its arrows and its motion; `ArchitectureWindow`, the window of §1, its line, its switch and its toggle.
- **`ArchitectureWindow`** follows the system's reduced-motion setting through a listener the setting holds weakly, from the window's first opening until the cockpit closes it: the setting lives as long as the platform, and a listener it held strongly would keep every window, and through its readings every control panel, built since the platform started.
- **`Cockpit`**: the twelfth section, the window made with the readings of §3, the report and the word handed to it where the steps chart is handed them and the drawing cleared where the steps chart is, the destinations of the publish it started kept as it started, and its closing beside the Profile and Reports windows'.
- **`control-panel.css`**: the window's rules, under its own style classes, every colour one of the two themes' own.
- **Documents**: the README's paragraph on what the window shows says twelve sections, Architecture the last.

`io.algernon.vespera.ui` is in no module and no class of the jar changes, so **no run id moves**.

## Consequences

- The operator sees which part of the architecture is at work, and what it touches, without reading the step names.
- A step whose touches change in the jar draws wrongly until the table of §2 is changed with it: no test reads the tasklets.
- A foreign invocation's report lights the stages, the ledger and what its running step touches, but not the CLI, which follows the cockpit's own child.

## Alternatives refused

- **A drawing generated from the stage table, and a sentence with a small map beneath it**, the prototype's other two drawings. The operator chose the reference picture.
- **The picture as an image.** Nothing in it could be lit.
- **A part lit from a source of the window's own**, as Docker's containers asked directly. The cockpit already reads what lights it; a second reading could disagree with the cockpit's.
- **A state told by colour alone.** It fails readers who do not tell the colours apart, and the words copy.
- **Motion everywhere, a transition from stage to stage or a mark settling on a finished stage.** Only what is happening now moves, so that nothing moves without saying something.

## What this does not decide

- **The window's first size and place**, and its stylesheet's sizes and spaces, the implementation's.
- **Whether a foreign invocation lights the CLI.**
- **Whether the switch and the toggle are kept** from one opening of the window to the next beyond the cockpit's life.

## Tests

| Method | What it holds |
| --- | --- |
| `ui.TheArchitectureTest.theDrawingsStepsAreTheJobsStepsEachInADrawnStage` | §2: the table's steps are `StopCosts.ROWS`'s, in its order, each in a stage of the drawing, and what each touches is the Context's table |
| `ui.TheArchitectureTest.aStageFoldsItsStepsAsALegDoes` | §3: over reports of every state, a stage's state folded from a leg's steps is the leg's state `Legs` reads |
| `ui.TheArchitectureTest.aRunALabelAndAPublishLightTheirParts` | §3: a step running lights its stage, the ledger, the CLI and what it touches, and nothing else; Docling and Ollama read down where the check found them not answering, and the GPU where the image is not the GPU one; `vespera label --auto` lights the ledger and Ollama; a publish to Confluence lights the deliverable and Confluence, not the website, and then how it ended; a child stopped leaves its running step's stage stopped; the control panel is never lit; and the line says each. §6: Docling's wire to the GPU carries data while extraction runs on it, Ollama's not, and neither on the CPU; a publish's wire to Confluence carries data and the wire into the deliverable none. A step the report names with no state reads as not begun |
| `ui.TheArchitectureWindowTest.theArchitectureOpensFromTheLastSectionInAWindowOfItsOwn` | §1: the sidebar's last section opens one window, with no owner and no modality, resizable, titled Architecture; opened again, the same one; closed and opened, where it was left; and closed with the cockpit |
| `ui.TheArchitectureWindowTest.aReportReadLightsTheDrawingAndTheLine` | §3, §5: a report the cockpit reads with a step running lights that step's stage running in the window, with its word, the line naming the step; every text in the window is a selectable text, not editable; and the window follows the theme switch, every word at 4.5 : 1 against its box in either theme |
| `ui.TheArchitectureWindowTest.motionFollowsTheSwitchStopsWhileHiddenAndTheDrawingFitsOrStandsAtFullSize` | §6, §7: the drawing moves with motion on, stands still with it off, and stops while the window is hidden; it is scaled to the window, and at its own size with the toggle |
| `ui.TheArchitectureWindowTest.dataRunsAlongItsWiresAsArrowsTheWayItGoes` | §6: every wire data goes along both ways has its second arrowhead, the short one between Ollama and the GPU too; extraction running, the arrowheads in the accent are those of the wires it sends data along, Docling's at both ends, Ollama's not; the archive's runners point into the application; Docling's point down to it in one lane and up from it in the other, and move that way as the cycle goes on; Ollama's show none |
| `ui.TheArchitectureTest.theGpuIsInUseWhereTheCheckFoundDoclingRunningTheGpuImage` | §3: the check's image decides the GPU, whatever the field says; with no image kept, the field, a blank one the preselected GPU image |
| `ui.TheArchitectureWindowTest.aPublishLightsTheDestinationsItWasStartedWithAndNoBoxTickedSince` | §3: a publish started with Confluence alone lights Confluence, and the website's box ticked while it runs lights nothing |
| `ui.TheArchitectureWindowTest.aNewInvocationClearsTheDrawingAsItClearsTheStepsChart` | §3: a run stopped leaves its stage stopped; a new run watched, its report not yet read, leaves that stage waiting |
| `ui.TheArchitectureWindowTest.aWindowNothingHoldsIsLetGo` | §9: a window built and then held by nothing is collected, the system's motion setting holding it not |
| `ui.TheOutputsSectionTest`, `ui.TheProfileWindowTest`, `ui.EveryTextOfTheWindowCanBeCopiedTest` | §1: amended so that the sidebar's sections are twelve, Architecture the last |

That opening the window again brings it to the front and gives it the focus is held by no test, as ADR-414's Tests say of the Reports window. That *Motion: as the system* follows the system's setting is held by no test: the Headless platform reports no reduced motion and offers no way to set it.

## What the implementation owes

§9's classes, rules and document, with each test above green.
