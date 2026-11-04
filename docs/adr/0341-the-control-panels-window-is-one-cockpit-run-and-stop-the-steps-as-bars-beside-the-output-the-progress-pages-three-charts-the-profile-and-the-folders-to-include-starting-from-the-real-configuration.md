# ADR-341 — The control panel's window is one cockpit: Run and Stop, the steps as bars beside the output, the progress page's three charts, the profile and the folders to include, starting from the real configuration

- **Date**: 2026-11-04
- **Status**: accepted on 2026-11-04.
- **Decided**: by the operator, the layout, variant B of [#700](https://github.com/algernon28/vespera/issues/700), as §1 sets it down; that the window shows the progress page's three charts and the steps as a chart of a bar each beside the live output (§1, §2); that its starting values come from the real configuration, the environment, `.env`, `application.yaml` and the working directory's files (§3); and that the options of `run`, `label`, `label --auto` and `publish` are set in the window, with the command lines they make shown (§4). By Claude for the operator: the ids, the file the jar writes for the charts and the steps, the order the starting values are taken in, which options are offered and which are not, how a report is opened, closing the window while a child runs, and the tests.
- **Amends**: [ADR-278](0278-the-control-panel-starts-the-jar-as-its-child-reads-its-output-to-the-end-and-stops-only-its-own-child.md) §1, whose command lines gain the options §4 passes. It carries a note at its head.
- **Amends**: [ADR-330](0330-the-control-panel-edits-every-value-of-profile-yaml-showing-the-value-in-use-and-where-it-came-from-and-ten-technical-choices-ship-as-presets-in-application-yaml.md) §1, whose control panel reads no configuration of the jar's: it reads the jar's own `application.yaml` and the environment for the values of §3, and `effective-values.properties` stays what lies beneath the profile. It carries a note at its head.
- **Amends**: [ADR-336](0336-the-launch-page-starts-label-auto-beside-label-and-every-argument-of-the-commands-the-control-panel-starts-is-set-by-a-control-of-the-launch-page-or-listed-as-never-passed-with-why.md) §1, whose button is named `Let the local model answer`; §2, whose `control` names a control of the window by its id, `label <file>` set by `label-file`, and whose What the table does not cover is §4's options; and §3, whose `label <file>` is offered. It carries a note at its head.
- **Amends**: [ADR-302](0302-the-control-panel-is-a-launch-page-and-an-invocation-page-of-server-written-forms-each-report-re-served-read-only-in-a-sandbox-and-the-next-invocations-values-on-pages-of-their-own.md) §7, whose table names no running step: the window knows it from §2's file and shows the running step's cost beside its own Stop. It carries a note at its head, beside [ADR-339](0339-the-control-panel-is-a-javafx-window-inside-the-vespera-jar-opened-by-the-jar-started-with-no-argument-and-its-loopback-server-and-pages-are-gone.md)'s.
- **Keeps**: [ADR-330](0330-the-control-panel-edits-every-value-of-profile-yaml-showing-the-value-in-use-and-where-it-came-from-and-ten-technical-choices-ship-as-presets-in-application-yaml.md)'s refusal of a launch value for a preset that enters a run's identity: no option §4 offers enters one. [ADR-292](0292-the-control-panel-remembers-its-launch-values-in-a-file-of-the-users-own-under-the-temp-directory-and-passes-five-through-the-childs-environment.md): seven launch values, passed as before; §4's options are not launch values and are not remembered. [ADR-265](0265-vespera-run-rewrites-a-progress-page-in-the-working-directory-while-it-runs.md) to [ADR-268](0268-the-progress-page-shows-what-the-stage-that-removes-occurrences-has-removed-and-draws-to-the-data-viz-method.md) and [ADR-322](0322-the-progress-pages-three-line-charts-stand-in-one-row-that-wraps-each-drawn-600-units-wide-and-legible-down-to-500-px.md): the progress page and the rules that pick each chart's series stay the jar's (§2).
- **Keeps**: [ADR-334](0334-the-default-output-folder-named-is-none-an-empty-field-shows-the-value-in-use-relevance-is-answered-by-folders-not-relevant-the-profile-is-one-form-and-a-code-set-folder-may-lie-outside-the-corpus-root.md) §6: the Inclusions tab writes `not-relevant-folders.yaml` as `/labels` wrote it.
- **Read with**: ADR-339, the window and its tests' harness; [ADR-340](0340-the-note-saved-with-a-profile-value-is-filled-in-for-the-operator-set-in-the-control-panel-or-the-provenance-already-saved-and-a-value-changed-under-its-old-note-is-saved-with-the-filled-in-one.md), the note beside each value.
- **Settles**: [#700](https://github.com/algernon28/vespera/issues/700), with ADR-339 and ADR-340.

## Context

ADR-339 makes the control panel one JavaFX window inside the jar and keeps everything it reads, writes and starts. What the browser panel spread over pages, `/`, `/invocation`, `/progress`, `/next`, `/labels`, `/code-sets` and `/profile`, the window shows on one screen.

**What the progress page holds.** `ProgressPage` rewrites `progress.html` in the working directory every `vespera.progress-interval`, 5 seconds as shipped, from what the invocation holds in memory: each step's state, its counts and last progress line, and up to 256 samples, from which it draws three line charts in one row (ADR-322): the database's size over time; the count of the latest step a sample names, from its counter where its progress line has one and from its read and write counts otherwise; and the verdicts the latest step that removes occurrences recorded, by kind (ADR-268). The rules that pick each series are `ProgressPage`'s; nothing outside the jar's process holds the samples.

**What the jar reads per machine.** Spring reads a property from, highest first, the command line's `--<property>=<value>`, the environment by its relaxed name (`VESPERA_DOCLING_IMAGE` for `vespera.docling.image`), `application-local.yaml` and `application.yaml`. The window's child inherits the window's environment, and the launch values not empty are added to it (ADR-292 §4). `.env` is a file of `NAME=value` lines that `compose.yaml` and the IDE's run configurations read; the jar does not.

## Decision

### 1. The layout

One window, opening at 1500 by 960, its root drawn by `ControlPanelWindow.drawn(ControlPanelState)`, a class of the control panel that extends `Application` and that `ControlPanel.main` launches (ADR-339 §2). Its parts are found by these ids:

| Part | Id | What it holds |
| --- | --- | --- |
| The header | `where`, `child` | the corpus root and the working directory; the state of the window's child, as ADR-278 §5 names it |
| The outcome | `outcome` | the one line the last action said, a refusal among them |
| The action card | `run`, `stop` | Run and Stop, side by side in one row; Stop enabled only while the window's child runs |
| | `publish` | Publish, in the same row, where a Confluence rendering is drawn (ADR-308 §6) |
| | `wanted` | each value the next invocation wants (ADR-294), edited in place with its note (ADR-340), its reports and its Save |
| | `label`, `label-auto` | **Record my answers**, which starts `label`, and **Let the local model answer**, which starts `label --auto`, each with one sentence beside it saying what it does |
| The run | `steps` | a chart of the job's sixteen steps in order, a row each: its stage, its name, a bar of how far it has got, its state; on the running step's row its own Stop, `stop-step`, with what a stop there costs (ADR-302 §7) |
| | `output` | the child's output, the last 200 lines (ADR-285 §5), newest last and in view |
| The charts | `database-chart`, `counts-chart`, `verdicts-chart` | the progress page's three charts, drawn from §2's file |
| The tabs | `profile`, `inclusions` | **Profile** and **Inclusions** |
| The sidebar | `preflight`, `folders`, `services`, `seed-folder`, `code-sets`, `command-options`, `reports` | seven sections in this order |

`steps` and `output` stand side by side, in one split the operator can move; the charts beneath them; the tabs beneath the charts; the sidebar to the left of all.

- **Preflight**: the corpus root and the working directory found or not, the sidecars' last answers (ADR-302 §5), the two locks' holders (ADR-278 §4), free space (ADR-302 §6).
- **Folders**: the corpus root, the working directory and the output folder (ADR-334 §1), by their launch values' properties as ids, `vespera.corpus-root`, `vespera.working-dir`, `vespera.deliverable-root`, each with a Browse that opens the system's folder dialog, its status (ADR-331 §6), and the line *Starting value from `<source>`* (§3).
- **Services**: the converter's image, offered as **GPU** or **CPU** in words with each image's name in small type, an image neither names offered as a third (ADR-334 §11); the converter's address, Ollama's address and the label model, by their properties as ids, each with its starting value's source.
- **Seed folder**: `seedFolder`, a profile value, with its note (ADR-340) and its Save.
- **Code sets**: one list of folders, built with Browse and `+`. A folder under the corpus root gets an optional name for the deliverable (`code-set-names.yaml`, ADR-295 §7); one outside it is added to `codeSetFolders` (ADR-334 §5).
- **Command options**: §4.
- **Reports**: §6.

**The Profile tab** is one form of every key (ADR-334 §7), a row each: the key's descriptive name, the key beneath it in small type, a plain description and which steps a change makes again (`ProfileKey.cost()`); a control that constrains the value by its kind, a number picker bounded where the jar bounds the key and a box saying whether it is set at all, a pick-list for a model, a language and the approval, a list built with `+` for name endings and languages; and the value's note (ADR-340). `seedFolder` and `codeSetFolders`, edited in their sidebar sections, are shown read-only here with a link that opens their section. One **Save** writes every key ADR-340 §2 says to write, by ADR-330 §5; **Reset** puts the form back to the file as last saved.

**The Inclusions tab** is the corpus root's folders as a tree, each with a box, every one ticked at first. The operator unticks a folder holding nothing they want; its box then stands unticked with every folder below it, and a folder with one below it unticked stands half-ticked. A folder's subfolders are listed when it is first opened, never before, so a corpus root is never walked to draw it, and no file's name is listed. Save writes the highest unticked folder of each branch to `not-relevant-folders.yaml`, as ADR-334 §6 has it; Reset puts the tree back to the file.

**A section still missing a value** carries a badge, *needs a value*, in its title: Folders while the corpus root or the working directory is empty, Services while an address or the label model is, Seed folder while `seedFolder` is unset, and the Profile tab while the next invocation wants a value.

**Every button has the one style** of the stylesheet (ADR-339 §3).

### 2. What the jar writes for the window

**At each write of the progress page, `ProgressPage` writes `progress.json` beside it**, in the working directory, whole, to a temporary file moved over it, as it writes each of its pages. It holds, in JSON:

- `ended`: whether the invocation has ended, as the page's last write says;
- `steps`: each step of the job, in order, with its `name`, its `state` as the page shows it, and `done` and `total` where its progress line carries a counter, `null` otherwise;
- `charts`: for each of the three charts the page draws now, its `id`, `database`, `counts` or `verdicts`, its `title` and its value axis's `axis` title as the page writes them, the `seconds` of its samples, and its `lines`, each a `name` and its `values`, in the unit the axis names.

The series are what the page draws, picked by the page's own rules; the window draws them and decides none. It names no path, no title and no text of a document, as the page names none. `vespera label`, which writes the page as it starts and as it ends, writes the file with it.

**The window reads it** every `vespera.progress-interval` while its child runs, and once as it ends, with the control panel's own `JsonReader`. A step's bar is `done` over `total` where both are given, full once the step has completed, and moving without a fraction while it runs with no counter. Before any file is there, `steps` lists the sixteen steps of ADR-302 §7's table, each waiting, and the three charts are empty. A file that is not there, cannot be read or is not that JSON leaves the window as it was.

A class added to `pipeline` for it is listed in `PipelineHoldsOnlyTheRulesOnRecordTest` as holding no rule; no stage's version names `pipeline` (ADR-222), so no run id moves.

### 3. The starting values

**Each launch value and each option of §4 starts from the first of these that names it**, and the line beneath its field says which:

1. **the launch values file**, as last stored when an invocation started (ADR-292), for a launch value;
2. **the window's environment**, by the property's relaxed name;
3. **`.env` in the current directory the jar was started from**, read as `NAME=value` lines, a line starting `#` and a line with no `=` skipped, a value in matching quotes taken without them, never written;
4. **the jar's own `application.yaml`**, read from the window's class path with Jackson's YAML reader, a value holding `${` not taken;
5. nothing: the field starts empty, and ADR-334 §2's *in use* shows what the child would use.

Where none names the working directory, it starts empty, as ADR-292 §5 has it. `ControlPanel.main` reads the five sources and hands the window's state what they say, each with its source; the window reads no environment and no file of configuration of its own, so a window a test draws starts from what the test gives it. The profile's values start from the working directory's `profile.yaml`, what lies beneath them from `effective-values.properties` (ADR-330 §1), the folders not relevant from `not-relevant-folders.yaml` and the code sets' names from `code-set-names.yaml`. Nothing of these is written but by a Save, under ADR-295's lock.

### 4. The options of the commands, and the command lines they make

**The Command options section offers, beside the launch values:**

| Id | Passed to | As |
| --- | --- | --- |
| `label-file` | `label` | `label`'s `<file>`, absolute; never to `label --auto`, which refuses one (ADR-197 §6) |
| `vespera.progress-interval` | `run`, `label` | `--vespera.progress-interval=<value>` |
| `vespera.sidecars.start` | `run`, `label --auto` | `--vespera.sidecars.start=<value>` |
| `vespera.sidecars.wait-at-most` | `run`, `label --auto` | `--vespera.sidecars.wait-at-most=<value>` |
| `vespera.models.pull` | `run` | `--vespera.models.pull=<value>` |
| `vespera.models.pull-stalled-at-most` | `run` | `--vespera.models.pull-stalled-at-most=<value>` |

Each starts from §3's value, with a control of its kind: a box for a flag, a number picker of seconds for a duration, a file dialog for the label file. **An option is passed only where its value differs from what the child would read without it**, the environment's, else `application.yaml`'s, after the command's own arguments, in the table's order. Options are not remembered: each start of the window begins from §3. **Back to the defaults** puts each back.

**Not offered**, each for its reason, which `ChildCommand` records beside the table:

- `vespera.open-browser`: the child runs with `CI=1` and opens no browser whatever it says (ADR-278 §1).
- `vespera.relevance.sample.bands` and `vespera.relevance.sample.per-band`: each enters the scoring run's identity where it is not its default, and ADR-330 refuses a value of that kind passed from a file named for a machine.
- `vespera.corpus-root-lock-folder`: the window reads the corpus-root lock in the folder `VESPERA_CORPUS_ROOT_LOCK_FOLDER` names (ADR-279), and a folder passed to the child alone would hide its lock from the window; a path on the command line also reaches a child on Windows through the ANSI code page (ADR-292).
- The datasource's and Hikari's properties, and every preset of ADR-330 §8: the jar's own.

**The command lines are shown** in the section, one each, `command-line-run`, `command-line-label`, `command-line-label-auto` and `command-line-publish`, each `vespera` followed by exactly the arguments the child would be started with after `-jar <jar>`, an argument holding a space in double quotes, changing as a field or an option changes. What is shown is what is started: both are built by `ChildCommand`.

**`ChildCommand.ARGUMENTS`** (ADR-336 §2) names the window's ids in `control`: `vespera.corpus-root`, `vespera.working-dir`, `label-auto`, and `label-file` for `label <file>`; `vespera help` and `vespera version` stay never passed, and are the only two. A test of the window holds that each `control` the table names is a control of the window.

### 5. The words

The two label commands are named **Record my answers** and **Let the local model answer**. Beside the first: you answered in `relevance-labels.yaml`, and this saves those answers for the next Run. Beside the second: the local model, named, reads the documents, answers for you and sets the cut-off, and your own answers are never replaced. The converter's image is offered as *GPU: needs an NVIDIA graphics card. Much faster.* and *CPU: runs on any machine. Slower.* The words of every other label and hint are ADR-331 §8's where it gives them, and the implementation's otherwise.

### 6. Reports

The Reports section lists every report the README's table of values names, `progress` and `cluster-page-sizes` among them (ADR-306 §2), in that table's order, and each key's row on the Profile tab and in `wanted` lists that key's reports (ADR-306 §3). A report the working directory holds opens in the system's default browser, through JavaFX's `HostServices.showDocument` with its file's URI; one it does not hold is drawn grey, *not written yet*. The window serves nothing and reads no report.

### 7. Closing the window while its child runs

Closing the window while its child runs asks once, with ADR-285 §5's sentence and two answers, **Stop and close** and **Keep the window**. Stop and close ends the JVM, whose hook ends the child (ADR-307). A close the system forces is ADR-307 §2's.

## Consequences

- The operator sees the run, its steps, its charts and the next value it wants on one screen, and edits the profile and the folders to include beside it.
- The progress page's numbers reach the window through one more file the jar writes beside the page, and the window draws them by the page's rules.
- A field the operator has never filled starts from the machine's own configuration, and says where from.
- The window passes options the browser panel never passed, and shows each command line it starts.

## Known limits

- **A label file whose path the system's code page cannot spell** reaches a child on Windows mangled, as ADR-278 measured of every argument, and the jar refuses it as not found.
- **`.env` is read only in the current directory** the jar is started from; a double click starts it in the jar's folder.
- **A value `application-local.yaml` sets** is not among §3's sources: the child reads it, and the window shows it only where `effective-values.properties` does.
- **The charts follow the file**, so they move once every `vespera.progress-interval`.

## Alternatives refused

- **The window reading `progress.html`'s numbers.** Its markup is for a browser, and the window would hold a second reading of the page's rules.
- **The window counting from the child's output.** The verdicts by kind are not in it, and the database's size the window could measure would not be the page's.
- **A `WebView` of the progress page.** ADR-339 refuses `javafx-web`.
- **Every option the jar reads, offered.** Three would pass what the window cannot honour or a record refuses (§4); the rest are the jar's own.
- **Options remembered with the launch values.** ADR-292 keeps seven, and an option left set from a week ago is passed unseen.
- **The environment above the launch values file.** The window passes a launch value in the child's environment over the one it inherits (ADR-292 §4), so the value last started with is what the child used; a variable above it would bring back at the next start a value the operator had changed.

## Tests

| Method | What it holds |
| --- | --- |
| `controlpanel.TheControlPanelsWindowTest.runAndStopStandSideBySideAndStopIsDisabledWithNoChild` | `run` and `stop` in one row, `stop` disabled where no child runs (§1) |
| `controlpanel.TheControlPanelsWindowTest.theStepsAreAChartOfABarEachBesideTheOutput` | `steps` holds sixteen bars, and stands in one split with `output` (§1, §2) |
| `controlpanel.TheControlPanelsWindowTest.theProgressPagesThreeChartsAreInTheWindow` | `database-chart`, `counts-chart` and `verdicts-chart`, each a line chart (§1) |
| `controlpanel.TheControlPanelsWindowTest.theTwoLabelCommandsAreNamedAsTheOperatorChose` | `label` reads *Record my answers* and `label-auto` *Let the local model answer* (§5) |
| `controlpanel.TheControlPanelsWindowTest.theProfileAndTheFoldersToIncludeAreTwoTabs` | `profile` and `inclusions`, tabs of one pane (§1) |
| `controlpanel.TheControlPanelsWindowTest.theSidebarHoldsItsSevenSectionsInOrder` | the seven ids of §1, in order (§1) |
| `controlpanel.TheControlPanelsWindowTest.everyControlTheArgumentsTableNamesIsAControlOfTheWindow` | each `control` of `ChildCommand.ARGUMENTS` is a node of the window (§4) |
| `pipeline.TheControlPanelAccountsForEveryArgumentOfTheJarsCommandsTest.eachArgumentIsSetByALaunchControlOrNeverPassedAndSaysWhy` | `label <file>` set by `label-file`; `vespera help` and `vespera version` the only two never passed (§4) |

## What the implementation owes

Beside the tests above, which it turns green, the change that builds the window holds by tests of its own: `progress.json`'s contract, the jar's `ProgressPage` written and the control panel's `JsonReader` reading the same steps and series; §3's order, each source above the next; §4's rule that an option at the value in use is not passed and that the line shown is the command started; the Inclusions tree writing the file `/labels` wrote; and a save of the Profile tab writing what ADR-340 §2 says. It changes the README's section on starting the control panel and its paragraph on provenance, `AGENTS.md`'s line on the control panel, and `CONTEXT.md`'s entries for the control panel and the profile.

## What this does not decide

- **The words of each hint**, beyond §5 and ADR-331 §8.
- **The stylesheet's colours and sizes**, the implementation's under ADR-339 §3.
- **A dark theme.**
