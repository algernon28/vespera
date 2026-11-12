# ADR-344 — The control panel's window is one cockpit: Run and Stop, the steps as bars beside the output, the progress page's three charts, the profile and the folders to include, starting from the real configuration

> **Partly amended — see [ADR-345](0345-the-cockpit-replaces-the-browser-control-panel-its-tree-writes-the-folders-not-walked-and-publish-offers-each-destination.md).** Of §1: `publish` stands where any destination's rendering is drawn, with `publish-to` beside it; a step's row shows `0`, `5e` and `5` for the census, the relevance floor and the relevance report; and the Inclusions tab's tree writes `foldersNotWalked`, the folders not relevant a list beneath it, which is how its Keeps of ADR-334 §6 reads. Of §3: the working directory no source names is ADR-340 §6's folder. Of §4: `ChildCommand` gains `DESTINATIONS`, the publish of named destinations and `environmentFrom`, and `ARGUMENTS` hold `publish to` and `serve db-dir`. The rest of this record stands.

> **Partly amended — see [ADR-361](0361-the-cockpits-steps-fold-into-the-progress-pages-four-legs-and-its-copy-of-their-table-is-held-to-the-pages-by-a-test.md).** Of §1: the rows of `steps` stand in four legs, `leg-<leg>`, the progress page's, each a leg with one summary bar and its state that folds, open where the job runs, failed or first stopped at a gate; each row `step-<name>` stands inside its leg as this record has it. The rest of this record stands.

> **Partly amended — see [ADR-362](0362-an-invocations-report-is-one-json-file-drawn-as-static-html-through-a-mustache-template-and-no-command-opens-anything.md).** Of §1: the tabs pane gains `live-report`, first, the invocation report drawn natively. Of §2: the window reads `report.json` through `InvocationReportFile`, and `progress.json` is not written. Of §4: `vespera.open-browser` is not among what the window does not offer, nothing reading it. Of §6: `Reports.ALL` holds the twelve analysis reports and none of the four progress pages, `report.html` being opened in no tab; a report opens in its tab, `analysis-report-<name>` in place of `report-<file>`, where §6 places it, as a native view drawn from its own JSON, with no `WebView`, its Reload reading the JSON again, an `analysisReport` cell opening that report's tab and a `path` cell handed to the system as §6 hands a file; the viewer's rules, its Known limits, its Alternatives refused on the viewer and its Tests rows of `TheReportsOpenInTheWindowTest` no longer hold. The rest of this record stands.

> **Partly amended — see [ADR-364](0364-the-output-panel-is-copyable-text-of-the-whole-output-from-a-file-of-its-own-and-every-text-of-the-window-is-copyable.md).** Of §1: `output` is the output panel, a `TextArea` that is not edited, holding the watched invocation's whole output, from `logs/output-<invocation>.log` once its report names it and from the child's stream until then, not the last 200 lines; it follows new lines only while its view is at the bottom and nothing in it is selected. Every text the table names outside a control, the header, `outcome` and each step's words among them, is a `SelectableText` the operator selects and copies; a tab's title, a button's text, a section's title and a leg's summary stay as they are. The rest of this record stands.

> **Partly amended — see [ADR-365](0365-the-cockpits-charts-carry-a-legend-and-a-value-on-each-sample-and-a-running-step-or-leg-says-its-whole-percent.md).** Of §1: a row's state of a step running with a counter of a total reads `running <N>%`, its whole percent rounded down and 99 at most while it runs, every other row its word alone, what the row does still read from the file's state; and each chart carries a legend above its plot where it draws two lines or more, each sample a hit area showing its value alone and the latest sample a marker. The rest of this record stands.

> **Partly amended — see [ADR-370](0370-every-bar-of-the-cockpits-steps-carries-its-whole-percent-drawn-on-it-and-nothing-else-of-the-window-changes.md).** Of §1: each row's one bar carries its whole percent drawn centred on it, `step-percent-<name>`, the percent of the bar it is drawn on, `…` while the step runs with no counter and `—` where it failed with none; the row's other parts, its ids and its state's words are unchanged. The rest of this record stands.

> **Partly amended — see [ADR-372](0372-the-control-panels-window-has-a-light-and-a-dark-theme-switched-in-its-header-and-kept-beside-the-launch-values.md).** Of §1: the header carries the theme switch, `theme-toggle`, at its right, under the word Theme between Light and Dark, the window opening light where no theme is kept. Of What this does not decide: a dark theme is decided there. The rest of this record stands.

- **Date**: 2026-11-04
- **Status**: accepted on 2026-11-04.
- **Decided**: by the operator, the layout, variant B of [#700](https://github.com/algernon28/vespera/issues/700), as §1 sets it down; that the window shows the progress page's three charts and the steps as a chart of a bar each beside the live output (§1, §2); that its starting values come from the real configuration, the environment, `.env`, `application.yaml` and the working directory's files (§3); and that the options of `run`, `label`, `label --auto` and `publish` are set in the window, with the command lines they make shown (§4); and that a report opens inside the window and never in the browser, in a closable tab of its own beside Profile and Inclusions, selected again where it is open, with a control that reloads it (§6). Claude recommended, and the operator accepted as written: the ids, the file the jar writes for the charts and the steps, the order the starting values are taken in and what of them is passed and kept, which presets are offered and which are not, where a link inside a report leads and that a link to a document is handed to the system, that the report tabs close with a change of working directory, closing the window while a child runs, and the tests.
- **Amends**: [ADR-292](0292-the-control-panel-remembers-its-launch-values-in-a-file-of-the-users-own-under-the-temp-directory-and-passes-five-through-the-childs-environment.md) §2, whose file is written with every value an invocation started with: it keeps only a value that came from it or that the operator changed in the window (§3); §4, whose launch value not empty is passed: one is passed where it is kept or the child would read another without it, the corpus root and the working directory always (§3); and §5, whose missing file starts every field empty: each field starts from §3's sources, the GPU preselection among them. It carries a note at its head.
- **Amends**: [ADR-334](0334-the-default-output-folder-named-is-none-an-empty-field-shows-the-value-in-use-relevance-is-answered-by-folders-not-relevant-the-profile-is-one-form-and-a-code-set-folder-may-lie-outside-the-corpus-root.md) §2, whose services' fields stand empty where no launch value names one: each starts from §3's sources, and passes nothing where it holds, unchosen, what the child reads anyway (§3); its *in use* stays beside a field no source names; and §11, whose GPU image is preselected wherever no launch value names one: an image the environment or `.env` names comes before it (§3). It carries a note at its head.
- **Amends**: [ADR-306](0306-the-control-panel-reads-a-holder-line-of-its-own-process-as-held-by-nobody-and-serves-the-cluster-page-sizes-as-a-sixteenth-report.md) §2, whose sixteenth report is served at `/report/`: it is listed in the Reports section and opened in a tab of the window (§6); and §3, whose `/profile` links each key's reports: the Profile tab lists them beside each key (§6). It carries a note at its head.
- **Amends**: [ADR-278](0278-the-control-panel-starts-the-jar-as-its-child-reads-its-output-to-the-end-and-stops-only-its-own-child.md) §1, whose command lines gain the options §4 passes. It carries a note at its head.
- **Amends**: [ADR-330](0330-the-control-panel-edits-every-value-of-profile-yaml-showing-the-value-in-use-and-where-it-came-from-and-ten-technical-choices-ship-as-presets-in-application-yaml.md) §1, whose control panel reads no configuration of the jar's: it reads the jar's own `application.yaml` and the environment for the values of §3, and `effective-values.properties` stays what lies beneath the profile; §8's "Control panel" column, whose `vespera.sidecars.start`, `vespera.sidecars.wait-at-most` and `vespera.progress-interval` are "Later, as a launch value": they are set now, as presets passed as options of the command line, and not as launch values (§4); and §12, whose one edit of a preset that enters no run is a launch value passed in the child's environment: §4's five are passed on the command line and kept nowhere. It carries a note at its head.
- **Amends**: [ADR-336](0336-the-launch-page-starts-label-auto-beside-label-and-every-argument-of-the-commands-the-control-panel-starts-is-set-by-a-control-of-the-launch-page-or-listed-as-never-passed-with-why.md) §1, whose button is named `Let the local model answer`; §2, whose `control` names a control of the window by its id, `label <file>` set by `label-file`, and whose What the table does not cover is §4's options; and §3, whose `label <file>` is offered. It carries a note at its head.
- **Amends**: [ADR-302](0302-the-control-panel-is-a-launch-page-and-an-invocation-page-of-server-written-forms-each-report-re-served-read-only-in-a-sandbox-and-the-next-invocations-values-on-pages-of-their-own.md) §7, whose table names no running step: the window knows it from §2's file and shows the running step's cost beside its own Stop. It carries a note at its head, beside [ADR-342](0342-the-control-panel-is-a-javafx-window-inside-the-vespera-jar-opened-by-the-jar-started-with-no-argument-and-its-loopback-server-and-pages-are-gone.md)'s.
- **Keeps**: [ADR-330](0330-the-control-panel-edits-every-value-of-profile-yaml-showing-the-value-in-use-and-where-it-came-from-and-ten-technical-choices-ship-as-presets-in-application-yaml.md)'s refusal of a launch value for a preset that enters a run's identity: no option §4 offers enters one. ADR-292 §1, §3 and §6: seven launch values and the last seed folder, in a file of the user's own; §4's presets are not launch values and are not remembered. [ADR-265](0265-vespera-run-rewrites-a-progress-page-in-the-working-directory-while-it-runs.md) to [ADR-268](0268-the-progress-page-shows-what-the-stage-that-removes-occurrences-has-removed-and-draws-to-the-data-viz-method.md) and [ADR-322](0322-the-progress-pages-three-line-charts-stand-in-one-row-that-wraps-each-drawn-600-units-wide-and-legible-down-to-500-px.md): the progress page and the rules that pick each chart's series stay the jar's (§2).
- **Keeps**: [ADR-334](0334-the-default-output-folder-named-is-none-an-empty-field-shows-the-value-in-use-relevance-is-answered-by-folders-not-relevant-the-profile-is-one-form-and-a-code-set-folder-may-lie-outside-the-corpus-root.md) §6: the Inclusions tab writes `not-relevant-folders.yaml` as `/labels` wrote it.
- **Keeps**: [ADR-104](0104-the-surviving-originals-stay-where-they-are-and-the-deliverable-references-them.md): each lead document `arrangement.html` links is still opened from its link (§6). [ADR-264](0264-the-private-paths-guard-refuses-only-what-reads-or-writes-outside-the-allow-list-and-a-working-directory-is-open-as-the-place-it-lies-in.md), [ADR-283](0283-the-private-paths-guard-reads-a-deliverable-tree-as-outside-the-allow-list-wherever-it-lies-by-its-marker.md) and ADR-342 §4: the window reads no document and no file of a deliverable tree (§6).
- **Read with**: ADR-342, the window and its tests' harness; [ADR-343](0343-the-note-saved-with-a-profile-value-is-filled-in-for-the-operator-set-in-the-control-panel-or-the-provenance-already-saved-and-a-value-changed-under-its-old-note-is-saved-with-the-filled-in-one.md), the note beside each value.
- **Settles**: [#700](https://github.com/algernon28/vespera/issues/700), with ADR-342 and ADR-343.

## Context

ADR-342 makes the control panel one JavaFX window inside the jar and keeps everything it reads, writes and starts. What the browser panel spread over pages, `/`, `/invocation`, `/progress`, `/next`, `/labels`, `/code-sets` and `/profile`, the window shows on one screen.

**What the progress page holds.** `ProgressPage` rewrites `progress.html` in the working directory every `vespera.progress-interval`, 5 seconds as shipped, from what the invocation holds in memory: each step's state, its counts and last progress line, and up to 256 samples, from which it draws three line charts in one row (ADR-322): the database's size over time; the count of the latest step a sample names, from its counter where its progress line has one and from its read and write counts otherwise; and the verdicts the latest step that removes occurrences recorded, by kind (ADR-268). The rules that pick each series are `ProgressPage`'s; nothing outside the jar's process holds the samples.

**What the jar reads per machine.** Spring reads a property from, highest first, the command line's `--<property>=<value>`, the environment by its relaxed name (`VESPERA_DOCLING_IMAGE` for `vespera.docling.image`), `application-local.yaml` and `application.yaml`. The window's child inherits the window's environment, and the launch values not empty are added to it (ADR-292 §4). `.env` is a file of `NAME=value` lines that `compose.yaml` and the IDE's run configurations read; the jar does not.

## Decision

### 1. The layout

One window, opening at 1500 by 960, its root drawn by `ControlPanelWindow.drawn(ControlPanelState)`, the one class of the control panel that extends `Application`, which `ControlPanel.open()` launches (ADR-342 §2). Its parts are found by these ids:

| Part | Id | What it holds |
| --- | --- | --- |
| The header | `where`, `child` | the corpus root and the working directory; the state of the window's child, as ADR-278 §5 names it |
| The outcome | `outcome` | the one line the last action said, a refusal among them |
| The action card | `run`, `stop` | Run and Stop, side by side in one row; Stop enabled only while the window's child runs |
| | `publish` | Publish, in the same row, where a Confluence rendering is drawn (ADR-308 §6) |
| | `wanted` | each value the next invocation wants (ADR-294), edited in place with its note (ADR-343), its reports and its Save |
| | `label`, `label-auto` | **Record my answers**, which starts `label`, and **Let the local model answer**, which starts `label --auto`, each with one sentence beside it saying what it does |
| The run | `steps` | a chart of the job's steps in the order `StopCosts.ROWS` names them, a row each, `step-<name>`: its stage, its name, one bar of how far it has got, its state; on the running step's row its own Stop, `stop-step`, with what a stop there costs (ADR-302 §7) |
| | `output` | the child's output, the last 200 lines (ADR-285 §5), newest last and in view |
| The charts | `database-chart`, `counts-chart`, `verdicts-chart` | the progress page's three charts, drawn from §2's file |
| The tabs | `profile`, `inclusions`, `report-<file name>` | **Profile** and **Inclusions**, and after them a tab for each report opened (§6) |
| The sidebar | `preflight`, `folders`, `services`, `seed-folder`, `code-sets`, `command-options`, `reports` | seven sections in this order, the panes of one accordion |

`steps` and `output` stand side by side, in one split the operator can move; the charts beneath them; the tabs beneath the charts; the sidebar to the left of all.

- **Preflight**: the corpus root and the working directory found or not, the sidecars' last answers (ADR-302 §5), the two locks' holders (ADR-278 §4), free space (ADR-302 §6).
- **Folders**: the corpus root, the working directory and the deliverable root (ADR-334 §1), by their launch values' properties as ids, `vespera.corpus-root`, `vespera.working-dir`, `vespera.deliverable-root`, each with a Browse that opens the system's folder dialog, its status (ADR-331 §6), and the line *Starting value from `<source>`* (§3).
- **Services**: the converter's image, offered as **GPU** or **CPU** in words with each image's name in small type, an image neither names offered as a third (ADR-334 §11); the converter's address, Ollama's address and the label model, by their properties as ids, each with its starting value's source.
- **Seed folder**: `seedFolder`, a profile value, with its note (ADR-343) and its Save.
- **Code sets**: one list of folders, built with Browse and `+`. A folder under the corpus root gets an optional name for the deliverable (`code-set-names.yaml`, ADR-295 §7); one outside it is added to `codeSetFolders` (ADR-334 §5).
- **Command options**: §4.
- **Reports**: §6.

**The Profile tab** is one form of every key (ADR-334 §7), a row each: the key's descriptive name, the key beneath it in small type, a plain description and which steps a change makes again (`ProfileKey.cost()`); a control that constrains the value by its kind, a number picker bounded where the jar bounds the key and a box saying whether it is set at all, a pick-list for a model, a language and the approval, a list built with `+` for name endings and languages; and the value's note (ADR-343). `seedFolder` and `codeSetFolders`, edited in their sidebar sections, are shown read-only here with a link that opens their section. One **Save** writes every key ADR-343 §2 says to write, by ADR-330 §5; **Reset** puts the form back to the file as last saved.

**The Inclusions tab** is the corpus root's folders as a tree, each with a box, every one ticked at first. The operator unticks a folder holding nothing they want; its box then stands unticked with every folder below it, and a folder with one below it unticked stands half-ticked. A folder's subfolders are listed when it is first opened, never before, so a corpus root is never walked to draw it, and no file's name is listed. Save writes the highest unticked folder of each branch to `not-relevant-folders.yaml`, as ADR-334 §6 has it; Reset puts the tree back to the file.

**A section still missing a value** carries a badge, *needs a value*, in its title: Folders while the corpus root or the working directory is empty, Services while an address or the label model is, Seed folder while `seedFolder` is unset, and the Profile tab while the next invocation wants a value.

**Every button has the one style** of the stylesheet (ADR-342 §3).

### 2. What the jar writes for the window

**At each write of the progress page, `ProgressPage` writes `progress.json` beside it**, in the working directory, whole, to a temporary file moved over it, as it writes each of its pages. It holds, in JSON:

- `ended`: whether the invocation has ended, as the page's last write says;
- `steps`: each step of the job, in order, with its `name`, its `state` in the words the page's timeline marks it with, `finished`, `failed` or `not started` among them, and `done` and `total` where its progress line carries a counter, `null` otherwise;
- `charts`: for each of the three charts the page draws now, its `id`, `database`, `counts` or `verdicts`, its `title` and its value axis's `axis` title as the page writes them, the `seconds` of its samples, and its `lines`, each a `name` and its `values`, in the unit the axis names.

The series are what the page draws, picked by the page's own rules; the window draws them and decides none. It names no path, no title and no text of a document, as the page names none. `vespera label`, which writes the page as it starts and as it ends, writes the file with it.

**The window reads it** every `vespera.progress-interval` while its child runs, and once as it ends, through `ProgressFile`, a class of the control panel built on its own `JsonReader`:

```java
public static final String FILE_NAME = "progress.json";
public record Progress(boolean ended, List<Step> steps, List<Chart> charts) {}
public record Step(String name, String state, Long done, Long total) {}          // done, total null without a counter
public record Chart(String id, String title, String axis, List<Long> seconds, List<Line> lines) {}
public record Line(String name, List<Double> values) {}
public static Progress read(String json);                                        // IllegalArgumentException where it is not that JSON
```

A step's bar is `done` over `total` where both are given, full once the step has completed, and moving without a fraction while it runs with no counter. Before any file is there, `steps` lists the steps of ADR-302 §7's table, `StopCosts.ROWS` (§8), each waiting, and the three charts are empty. A file that is not there, cannot be read or is not that JSON leaves the window as it was.

A class added to `pipeline` for it is listed in `PipelineHoldsOnlyTheRulesOnRecordTest` as holding no rule; no stage's version names `pipeline` (ADR-222), so no run id moves.

### 3. The starting values

**Each launch value and each preset of §4 starts from the first of these that names it, not blank**, and the line beneath its field says which:

1. **the launch values file** (ADR-292), for a launch value;
2. **the window's environment**, by the property's relaxed name: in capitals, each `.` and `-` an `_`;
3. **`.env` in the current directory the jar was started from**, read as `NAME=value` lines, the white space around both taken off, a line starting `#` and a line with no `=` skipped, a value in matching single or double quotes taken without them, a value holding `=` kept whole; never written;
4. **for `vespera.docling.image` alone, the GPU image**, `compose.gpu.yaml`'s, as ADR-334 §11 preselects it, so that it comes before the CPU image the shipped `application.yaml` names;
5. **the jar's own `application.yaml`**, read from the window's class path with Jackson's YAML reader, each scalar by its dotted name, a value holding `${` not taken;
6. nothing: the field starts empty, and ADR-334 §2's *in use* shows what the child would use.

Where none names the working directory, it starts empty, as ADR-292 §5 has it.

**What is kept.** A launch value is **kept** in the launch values file, as an invocation starts, only where it is not blank and came from that file or the operator changed it in the window, `remembered` below: one left as the environment, `.env`, the GPU preselection or `application.yaml` started it is never stored, so no shipped default is frozen into the file, and the GPU image posted back names no image, as ADR-334 §11 has it.

**What is passed.** A field's value is **passed** to the child where it is not blank and either is kept, or differs from what the child reads with nothing passed: the environment's, else `application.yaml`'s. So a value the operator set, now or at an earlier start, is passed whatever it equals, and takes the place of `application-local.yaml`'s, as ADR-292's Consequences have a launch value do; a value from `.env`, or the GPU image, which the child does not read, is passed, so that the child uses what the window shows, as ADR-334 §11 passes the GPU image; and a value left as the environment or `application.yaml` started it is not passed, so `application-local.yaml`, which the child reads above `application.yaml`, is not overridden by a value nobody chose.

**What `passed` governs.** The five launch values passed in the child's environment, the Docling image, the two addresses, the label model and the deliverable root (ADR-292 §4), and §4's presets. The corpus root and the working directory are always passed, as the command's own arguments, `run <root>` and `--db-dir` (ADR-278 §1, ADR-292 §4), whatever their source.

**The class.** `StartingValues`, of the control panel:

```java
public enum Source { LAUNCH_VALUE, ENVIRONMENT, DOT_ENV, GPU_PRESELECTION, APPLICATION_YAML, NONE }
public record Starting(String value, Source source) {}
public record Sources(Map<String, String> launchValues,      // by property, as the launch values file holds them
                      Map<String, String> environment,       // by variable
                      Map<String, String> dotEnv,            // by variable
                      Map<String, String> applicationYaml) {} // by dotted property
public static final String DOCLING_IMAGE_GPU;                 // compose.gpu.yaml's image, moved from ControlPanelPage
public static Starting of(String property, Sources sources);
public static String variableOf(String property);
public static Map<String, String> readDotEnv(String text);
public static Map<String, String> readApplicationYaml(String text);
public static boolean remembered(String value, Starting starting);    // not blank, and a launch value or changed
public static boolean passed(String property, String value, Starting starting, Sources sources);
                                       // not blank, and remembered or not what the child reads with nothing passed
```

`ControlPanel.open()` reads the four sources and hands the window's state what they say; the window reads no environment and no file of configuration of its own, so a window a test draws starts from what the test gives it. The profile's values start from the working directory's `profile.yaml`, what lies beneath them from `effective-values.properties` (ADR-330 §1), the folders not relevant from `not-relevant-folders.yaml` and the code sets' names from `code-set-names.yaml`. Nothing of these is written but by a Save, under ADR-295's lock.

### 4. The options of the commands, and the command lines they make

**The Command options section offers, beside the launch values, the label file and five presets, passed as options of the command line:**

| Id | Passed to | As |
| --- | --- | --- |
| `label-file` | `label` | `label`'s `<file>`, absolute, before `--db-dir`; never to `label --auto`, which refuses one (ADR-197 §6) |
| `vespera.progress-interval` | `run`, `label`, `label --auto` | `--vespera.progress-interval=<value>` |
| `vespera.sidecars.start` | `run`, `label --auto` | `--vespera.sidecars.start=<value>` |
| `vespera.sidecars.wait-at-most` | `run`, `label --auto` | `--vespera.sidecars.wait-at-most=<value>` |
| `vespera.models.pull` | `run` | `--vespera.models.pull=<value>` |
| `vespera.models.pull-stalled-at-most` | `run` | `--vespera.models.pull-stalled-at-most=<value>` |

Each preset starts from §3's value, with a control of its kind: a box for a flag, a number picker of seconds for a duration; the label file, empty, with a file dialog. **A preset is passed only where its value differs from what the child would read without it**, by §3's `passed`, a preset being kept nowhere and so never `remembered`, after the command's own arguments, in the table's order. None is remembered: each start of the window begins from §3. **Back to the defaults** puts each back.

**`ChildCommand`** holds the table and builds the line, its three factories unchanged:

```java
public record Option(String property, List<String> commands, String reason) {}   // commands among run, label, label --auto
public static final List<Option> OPTIONS;                                        // the five, in the table's order
public ChildCommand withOptions(Map<String, String> passed);   // --<property>=<value> for each the command is in OPTIONS for
public ChildCommand withLabelFile(Path file);                   // label's <file>; label --auto, run and publish unchanged
public String shown();                                          // vespera and the arguments after -jar <jar>
```

`withOptions` is given the presets §3's `passed` lets through and adds those the command is listed for; a property not in `OPTIONS` is not passed.

**Not offered**, each for its reason, which `ChildCommand` records beside the table:

- `vespera.open-browser`: the child runs with `CI=1` and opens no browser whatever it says (ADR-278 §1).
- `vespera.relevance.sample.bands` and `vespera.relevance.sample.per-band`: each enters the scoring run's identity where it is not its default, and ADR-330 refuses a value of that kind passed from a file named for a machine.
- `vespera.corpus-root-lock-folder`: the window reads the corpus-root lock in the folder `VESPERA_CORPUS_ROOT_LOCK_FOLDER` names (ADR-279), and a folder passed to the child alone would hide its lock from the window; a path on the command line also reaches a child on Windows through the ANSI code page (ADR-292).
- The datasource's and Hikari's properties, and every other preset of ADR-330 §8: the jar's own, shown where `effective-values.properties` names them and edited by no control.

**The command lines are shown** in the section, one each, `command-line-run`, `command-line-label`, `command-line-label-auto` and `command-line-publish`, each `vespera` followed by exactly the arguments the child would be started with after `-jar <jar>`, an argument holding a space in double quotes, changing as a field or an option changes. What is shown is what is started: both are built by `ChildCommand`.

**`ChildCommand.ARGUMENTS`** (ADR-336 §2) names the window's ids in `control`: `vespera.corpus-root`, `vespera.working-dir`, `label-auto`, and `label-file` for `label <file>`; `vespera help` and `vespera version` stay never passed, and are the only two. A test of the window holds that each `control` the table names is a control of the window.

### 5. The words

The two label commands are named **Record my answers** and **Let the local model answer**. Beside the first: you answered in `relevance-labels.yaml`, and this saves those answers for the next Run. Beside the second: the local model, named, reads the documents, answers for you and sets the cut-off, and your own answers are never replaced. The converter's image is offered as *GPU: needs an NVIDIA graphics card. Much faster.* and *CPU: runs on any machine. Slower.* The words of every other label and hint are ADR-331 §8's where it gives them, and the implementation's otherwise.

### 6. Reports

The Reports section lists every report the jar writes in the working directory, `Reports.ALL`, sixteen today, `progress.html` and `cluster-page-sizes.html` among them (ADR-306 §2), and each key's row on the Profile tab and in `wanted` lists that key's reports, `Reports.OF_A_KEY`, as the README's table of values names them in its order (ADR-306 §3). A report the working directory holds is a link; one it does not hold is drawn grey, *not written yet*.

**A report opens inside the window, never in a browser.** Pressing a report's link, in the Reports section or on a key's row, opens the report in a tab of its own in the pane of the Profile and Inclusions tabs, after them in the order the reports are opened, and selects it:

| Part | Id | What it holds |
| --- | --- | --- |
| The tab | `report-<file name>` | titled by the report's file name; closable, where Profile and Inclusions are not |
| Its Reload | `reload-<file name>` | reads the report again from its file |
| Its viewer | `viewer-<file name>` | JavaFX's `WebView`, filling the tab beneath Reload, loading the report's file by its `file:` address |

- **One tab per report.** A report whose tab is open is selected, neither opened again nor read again; its Reload reads it again.
- **The tabs are the working directory's.** A tab loads the report of the working directory the field names as it opens, and every report's tab closes when the field changes, as the rest of the window reads the field's working directory (§3).
- **The viewer runs the report's own script and adds none**: no script, stylesheet or bridge to Java is put into a page the jar wrote. So the progress page's four tabs reload themselves every `vespera.progress-interval`, keep their legs' folds in their address's fragment (ADR-324 §5, ADR-327), and show a chart's values under the pointer, as in a browser (ADR-342's Context measures each). A report's request for a window of its own is answered with none; no report makes one.

**Where a link inside a report leads** is decided by its address before the page changes: the viewer's `WebEngine` names the address in its location as the load begins, and a load the viewer does not follow is cancelled there, which leaves the viewer on the report it showed (ADR-342's Context measures it). **The viewer then loads its own report again**, from its file, by the file's address alone: it shows the report as the file holds it now, at its top, with no fragment, so the place the operator had scrolled to is left behind, and the progress page's legs fold as the page first draws them (corrected in place, [#700](https://github.com/algernon28/vespera/issues/700)).

| The address | What the window does |
| --- | --- |
| the report the tab shows, with or without a fragment, its reload among them | the viewer follows it |
| another report of `Reports.ALL` in the same working directory, as the progress page's bar of tabs links its companions by their bare file names | the report stays in the window: it opens in its own tab, or its tab is selected, as its link in the Reports section does; the tab the link was pressed in stays on its report |
| such a report the working directory does not hold | nothing opens, and `outcome` says *`<file name>` is not written yet, so it is not opened* |
| any other `file:` address: a document where it lies in the archive, as `arrangement.html` links each lead document (ADR-104), a file of a deliverable, a file of the working directory that is no report of `Reports.ALL`, as `profile.yaml`, a file named as a report in a folder other than the working directory, or any other file | **handed to the operating system**, which opens it with the program it gives that kind of file; the viewer does not load it and stays on its report |
| an address of any other scheme, `http:` and `https:` among them, or a `file:` address that names no path of this computer | followed nowhere, and `outcome` says *not opened: `<address>` is not a file of this computer*, the address as the link gave it; no report the jar writes holds one |

**Why a document is handed to the system, and neither shown nor refused.** ADR-264 and ADR-283 bind what an agent reads; the window is the operator's, and ADR-342 §4 binds it to read no document. Shown in the viewer, a document would be read inside the window's process, and an HTML document of the archive would run its own script there. Handed to the system, no byte of it passes through the window, as none passed through the browser's tab of the report when the browser followed the same link. Refused, a lead document could again be opened only from a file manager, as ADR-302 §3 had it, when ADR-104 links it so that the operator can open it.

**`HostServices.showDocument` is used for that hand-off alone.** `Dialogs.handToTheSystem(URI)` takes the address, and the dialogs `ControlPanelWindow.start` builds answer it with `getHostServices().showDocument` and the address's text; `Dialogs.open(Path)`, which opened a report in the default browser, goes. No other class of the control panel names `HostServices`. A window a test draws hands nothing to the system: its dialogs answer.

The window serves nothing. Its viewer loads the reports of `Reports.ALL` in the working directory and nothing else, and it names no `netscape.javascript` type (ADR-342 §3).

### 7. Closing the window while its child runs

Closing the window while its child runs asks once, with ADR-285 §5's sentence and two answers, **Stop and close** and **Keep the window**. Stop and close ends the JVM, whose hook ends the child (ADR-307). A close the system forces is ADR-307 §2's.

### 8. Where the rules the pages held go

The pages go (ADR-342 §5), and the rules they held that the window keeps move into classes of the control panel that link no page, each named so that a test reads it without a window:

| Rule | Record | From | To |
| --- | --- | --- | --- |
| What a stop costs at each step, in the job's order | ADR-302 §7 | `InvocationPages` | `StopCosts.ROWS`, a `List<StopCost>` of `record StopCost(String step, String cost)` |
| The reports, and each key's | ADR-302 §3, §8, ADR-306 §2, §3 | `ControlPanelPages.REPORTS`, `NextPages.REPORTS_OF_A_KEY` | `Reports.ALL`, a `List<String>` of file names, and `Reports.OF_A_KEY`, a `Map<String, List<String>>` by key |
| A folder field's status | ADR-331 §6 | `FolderPages.statusOf` | `FolderStatus.of(String value, boolean createdByTheJar)`, a `record FolderStatus(String state, String words)`, the states `not-chosen`, `found`, `will-be-created` and `no-such-folder` |
| A launch value's example and hint | ADR-331 §8 | `LaunchPages` | `LaunchValues.exampleOf(String property)` and `LaunchValues.hintOf(String property)` |
| The two Docling images | ADR-331 §7, ADR-334 §11 | `ControlPanelPage` | `StartingValues.DOCLING_IMAGE_GPU`, and the CPU image beside it |
| The hook that ends the child with the JVM | ADR-307 §1 | `ControlPanel`, private | `ControlPanel.endChildWithThisJvm(ControlPanelState)`, of the package |

The words each holds are the pages' as the records give them.

## Consequences

- The operator sees the run, its steps, its charts and the next value it wants on one screen, and edits the profile and the folders to include beside it.
- The progress page's numbers reach the window through one more file the jar writes beside the page, and the window draws them by the page's rules.
- A field the operator has never filled starts from the machine's own configuration, and says where from.
- The window passes options the browser panel never passed, and shows each command line it starts.
- The operator reads every report inside the window, the progress page's tabs reloading there as in a browser, and opens a lead document from its link in `arrangement.html` in the program the system gives it.

## Known limits

- **A label file whose path the system's code page cannot spell** reaches a child on Windows mangled, as ADR-278 measured of every argument, and the jar refuses it as not found.
- **`.env` is read only in the current directory** the jar is started from; a double click starts it in the jar's folder.
- **A value `application-local.yaml` sets** is not among §3's sources: the child reads it, and the window shows it only where `effective-values.properties` does. A field left as the environment or `application.yaml` started it passes nothing and leaves it in force; a value from `.env`, the GPU preselection, the launch values or one the operator typed is passed over it.
- **The charts follow the file**, so they move once every `vespera.progress-interval`.
- **A report is drawn by OpenJFX 26.0.2's WebKit**, not by the operator's browser; what its script does there was measured on the progress page of this version, and a later report's script is held by no test of the viewer.
- **A document handed to the system opens in whatever program the system gives its kind**: an HTML document of the archive opens in the default browser, as a document and not as a report.
- **A link not followed takes its report back to the top** (§6): the viewer loads the report again by its file's address alone, so where the operator had scrolled to, and a fold of the progress page's legs kept in its fragment, is lost with each press of such a link.

## Alternatives refused

- **The window reading `progress.html`'s numbers.** Its markup is for a browser, and the window would hold a second reading of the page's rules.
- **The window counting from the child's output.** The verdicts by kind are not in it, and the database's size the window could measure would not be the page's.
- **A `WebView` of the progress page in place of the three charts.** The page is laid out for a browser's window and wraps its charts at ADR-322's widths; the window draws them at its own size from §2's file, and the page itself opens in a report's tab (§6).
- **A report opened in the default browser.** It leaves the window for another program, a tab of it for each press; the operator refused it.
- **A link inside a report followed in the tab it was pressed in.** The tab would show a report other than the one it is named for, and a report could stand open in two tabs.
- **A document linked from a report shown in the viewer, or refused.** Shown, the window would read it (§6); refused, a lead document could be opened only from a file manager (§6).
- **A script put into each report to catch its links.** The viewer would change a page the jar wrote; the engine's location, its load cancelled before the page changes, decides the same with nothing added (ADR-342's Context).
- **Report tabs kept across a change of working directory.** A tab would show a report of a working directory the window no longer reads, beside the same report of the one it does.
- **Every option the jar reads, offered.** Three would pass what the window cannot honour or a record refuses (§4); the rest are the jar's own.
- **Options remembered with the launch values.** ADR-292 keeps seven, and an option left set from a week ago is passed unseen.
- **The environment above the launch values file.** The window passes a launch value in the child's environment over the one it inherits (ADR-292 §4), so the value last started with is what the child used; a variable above it would bring back at the next start a value the operator had changed.

## Tests

| Method | What it holds |
| --- | --- |
| `controlpanel.TheControlPanelsWindowTest.runAndStopStandSideBySideAndStopIsDisabledWithNoChild` | `run` and `stop` in one horizontal box, at one height, Stop to the right; `stop` disabled where no child runs (§1) |
| `controlpanel.TheControlPanelsWindowTest.theStepsAreAChartOfABarEachBesideTheOutput` | `steps` holds a row `step-<name>` for each step of `StopCosts.ROWS`, in its order, one bar each, in one horizontal split with `output` (§1, §2) |
| `controlpanel.TheControlPanelsWindowTest.theProgressPagesThreeChartsAreInTheWindow` | `database-chart`, `counts-chart` and `verdicts-chart`, each a line chart (§1) |
| `controlpanel.TheControlPanelsWindowTest.theTwoLabelCommandsAreNamedAsTheOperatorChose` | `label` reads *Record my answers* and `label-auto` *Let the local model answer* (§5) |
| `controlpanel.TheControlPanelsWindowTest.theProfileAndTheFoldersToIncludeAreTwoTabs` | `profile` and `inclusions`, tabs of one pane (§1) |
| `controlpanel.TheControlPanelsWindowTest.theSidebarHoldsItsSevenSectionsInOrder` | the seven ids of §1, the panes of one accordion, in order (§1) |
| `controlpanel.TheControlPanelsWindowTest.everyControlTheArgumentsTableNamesIsAControlOfTheWindow` | each `control` of `ChildCommand.ARGUMENTS` is a node of the window (§4) |
| `pipeline.TheControlPanelAccountsForEveryArgumentOfTheJarsCommandsTest.eachArgumentIsSetByAControlOfTheControlPanelOrNeverPassedAndSaysWhy`, named `eachArgumentIsSetByALaunchControlOrNeverPassedAndSaysWhy` until ADR-345 (#704) | `label <file>` set by `label-file`; `vespera help` and `vespera version` the only two never passed (§4); by ADR-345 §4, `publish to` by `publish-to` and `serve db-dir` a third never passed |
| `pipeline.TheControlPanelReadsTheProgressTheJarWritesTest.theProgressTheJarWritesIsReadWhole` is now `pipeline.TheControlPanelReadsTheReportTheJarWritesTest.theReportTheJarWritesIsReadWhole` (ADR-362 step 4) | `ProgressPage` writes `progress.json` and `ProgressFile` reads it: `ended`, the sixteen steps in order with their states, `done` and `total` null without a counter, the database chart's title, axis, seconds and one line (§2) |
| `controlpanel.StartingValuesTest.eachValueStartsFromTheFirstSourceThatNamesIt` | launch values, environment, `.env`, `application.yaml`, none, a blank skipped (§3) |
| `controlpanel.StartingValuesTest.theDoclingImageIsTheGpuOneBeforeApplicationYaml` | the GPU image, `compose.gpu.yaml`'s, before `application.yaml`'s and after `.env` (§3) |
| `controlpanel.StartingValuesTest.theVariableIsThePropertysRelaxedName` | the relaxed name (§3) |
| `controlpanel.StartingValuesTest.dotEnvIsReadAsNameEqualsValueLines` | `.env`'s lines, comments, blanks, quotes and a value holding `=` (§3) |
| `controlpanel.StartingValuesTest.applicationYamlIsReadByDottedNamesAndAPlaceholderIsNotTaken` | the shipped `application.yaml` by dotted names, `vespera.working-dir`'s placeholder left out (§3) |
| `controlpanel.StartingValuesTest.aValueIsPassedWhereItIsKeptOrTheChildWouldReadAnother` | passed where set and kept or not what the child reads: a launch value equal to `application.yaml`'s, and one changed back to it, passed; a value left as the environment or `application.yaml` started it, not (§3, §4) |
| `controlpanel.StartingValuesTest.aValueIsRememberedOnlyWhereTheOperatorChoseIt` | kept only where it came from the launch values or was changed (§3) |
| `controlpanel.TheCommandOptionsTest.theOptionsAreFivePresetsTheJarShips` | the five, their commands, each in the shipped `application.yaml` (§4) |
| `controlpanel.TheCommandOptionsTest.eachOptionIsPassedOnlyToTheCommandsItIsForAfterTheirArguments` | each preset after the command's arguments, in order, to its commands alone (§4) |
| `controlpanel.TheCommandOptionsTest.theLabelFileIsPassedToLabelAndNeverToLabelAuto` | the label file to `label`, none to `label --auto` (§4) |
| `controlpanel.TheCommandOptionsTest.theLineShownIsTheCommandStarted` | `shown()` is `vespera` and the child's arguments, a space quoted (§4) |

What the implementation owes is held, through the window shown by `ControlPanelWindow.shownOn(Stage, ControlPanelState, Dialogs)` on the Headless platform, its questions answered by the test through `Dialogs.confirm(String, String, String)` (ADR-342 §6), by these (added in place, [#700](https://github.com/algernon28/vespera/issues/700)):

| Method | What it holds |
| --- | --- |
| `controlpanel.TheFoldersToIncludeTest.aFolderUntickedIsWrittenAloneThoughItsSubfoldersWereListed` | a folder unticked stands unticked with every folder below it and is written alone (§1, ADR-334 §6) |
| `controlpanel.TheFoldersToIncludeTest.untickingEverySubfolderLeavesTheFolderHalfTickedAndWritesOnlyThem` | the only subfolder, or every one, unticked: the folder half-ticked, those subfolders written and it not (§1) |
| `controlpanel.TheFoldersToIncludeTest.aListedSubfolderComesBackUnchanged` | a listed `A/B`, with `A` holding `B` alone or `B` and `C`: drawn unticked under a half-ticked `A`, the file unchanged by a Save (§1) |
| `controlpanel.TheFoldersToIncludeTest.aListedFolderNoLongerThereIsKept` | a listed folder no longer there, at the top and below a folder: kept by a Save (§1) |
| `controlpanel.TheFoldersToIncludeTest.anotherCorpusRootSetInTheFieldIsTheTreeDrawnAndSaved` | a window drawn over one corpus root, its field set to another: the tree draws the other's folders and none of the first's, and Save writes a path under the other (§1, §3) |
| `controlpanel.TheWindowSavesTheProfileTest.theProfileTabsSaveWritesEachValueAndNoteAsTheTableSays` | the Profile tab's Save writes ADR-343 §2's table, row by row, a key the file never stated left unwritten (§1, ADR-343 §2) |
| `controlpanel.TheWindowSavesTheProfileTest.aSeedFolderOfferedFromTheLastOneIsWrittenBySave` | the seed folder offered from `last-seed-folder`, which the profile does not state, is written by the section's Save with the filled-in note (§1, ADR-343 §2) |
| `controlpanel.TheWorkingDirectoryInTheFoldersFieldTest.theFieldsWorkingDirectoryIsReadAndWrittenBeforeARunAndAfterIt` | the working directory from the environment or `.env`, kept nowhere: its profile, folders not relevant and reports read, its `progress.json` read as the Run ends, its profile and folders not relevant written after it (§1, §2, §3) |
| `controlpanel.TheWorkingDirectoryInTheFoldersFieldTest.anotherWorkingDirectoryTypedInTheFieldIsTheOneRead` | another working directory typed in the field: its profile, folders not relevant and reports read (§1, §3) |
| `controlpanel.TheWorkingDirectoryInTheFoldersFieldTest.publishStandsInTheActionRowWhereTheFieldsWorkingDirectoryHoldsARendering` | the working directory from the environment or `.env`, kept nowhere, holding a rendering in a run's folder under its `deliverable`: `publish` in the row of `run` and `stop`, before a Run that kept nothing and after it (§1, §3, ADR-308 §6) |
| `controlpanel.TheWorkingDirectoryInTheFoldersFieldTest.publishStandsInTheActionRowWhereTheOutputFolderFromTheEnvironmentHoldsARendering` | the output folder from the environment, holding a rendering at its top: `publish` in the row of `run` and `stop` (§1, §3, ADR-308 §6) |
| `controlpanel.ControlPanelStateTest.aStartIsRefusedWhereTheWorkingDirectoryItPassesIsHeld` | a start is refused where the working directory passed is held, and not for the one held before (§3, ADR-278 §4) |
| `controlpanel.StopAndCloseAskFirstTest.stopAsksWithItsSentenceAndStopsTheChildOnlyOnTheAnswerThatGoesOn` | the running step's row, as `progress.json` names it, alone shows its `stop-step`, looked up inside the row; `stop` and that `stop-step` ask with ADR-285 §5's sentence; no stops nothing, yes stops the child (§1, ADR-278 §6) |
| `controlpanel.StopAndCloseAskFirstTest.closingWhileTheChildRunsAsksOnceAndKeepTheWindowKeepsIt` | closing while the child runs asks once, with the sentence, **Stop and close** and **Keep the window**; Keep the window keeps both; no child, no question (§7) |
| `controlpanel.TheValuesTheNextInvocationWantsTest.aReportTheWorkingDirectoryHoldsIsALinkBesideAWantedValue` | a wanted key's report the working directory holds is a link in `wanted`, opening that report in its tab of the window, selected, and handing nothing to the system (§1, §6) |
| `controlpanel.TheValuesTheNextInvocationWantsTest.theArrangementTheNextInvocationNamesIsOfferedAsTheApprovalItWants` | `arrangementApproved` wanted with `arrangement` named, the profile stating none: `wanted-arrangementApproved` offered the name, and `wanted-save` writing it with `set in the control panel` (§1, ADR-294 §4, ADR-343 §2) |
| `controlpanel.TheValuesTheNextInvocationWantsTest.theLastSeedFolderIsOfferedAsTheSeedFolderItWants` | `seedFolder` wanted, the profile stating none: `wanted-seedFolder` offered the last seed folder (§1, ADR-292 §6) |
| `pipeline.TheStopCostsNameTheJarsStepsTest.theStageShownBesideEachStepIsTheJarsOrTheNumberTheRecordsGiveIt`, named `theStageShownBesideEachStepIsTheOneTheJarRecordsItUnder` until ADR-345 (#704) | `StopCosts.stageOf`, the stage each `step-<name>` row shows, is for each step of the job the stage the progress page's tab of runs records its completion under, `InvocationReport.STEPS_OF_A_STAGE` (corrected in place from `ProgressPage.STEPS_OF_A_STAGE`, which ADR-362 step 2 moved, [#737](https://github.com/algernon28/vespera/issues/737)), and empty for the census, the relevance floor and the relevance report, which it records under none (§1, ADR-269 §5); by ADR-345 §3, `0`, `5e` and `5` for those three |

The tests of §8's moved rules are ADR-342 §6's, in its Tests section.

Since ADR-345 §2, `controlpanel.TheFoldersToIncludeTest`'s rows hold the tree writing `foldersNotWalked` in `profile.yaml` in place of `not-relevant-folders.yaml`, and `controlpanel.TheWorkingDirectoryInTheFoldersFieldTest`'s the tree drawn from that working directory's `foldersNotWalked` (corrected in place, #704).

## What the implementation owes

Beside the tests above and ADR-342's, which it turns green, the change that builds the window holds by tests of its own that the Inclusions tree writes the file `/labels` wrote and that a save of the Profile tab writes what ADR-343 §2 says. It lists the class it adds to `pipeline` for `progress.json`, if it adds one, in `PipelineHoldsOnlyTheRulesOnRecordTest` as holding no rule. It adds `progress.json` to the README's table of the files written beside the database, as ADR-265 §6 has the page itself listed, the claims check reading that table against the jar's `FILE_NAME` constants. It changes the README's section on starting the control panel and its paragraph on provenance, `AGENTS.md`'s line on the control panel, and `CONTEXT.md`'s entries for the control panel and the profile. Of §6, it declares `javafx-web` as ADR-342 §1 has it, replaces `Dialogs.open(Path)` with `Dialogs.handToTheSystem(URI)`, and says in the README's paragraph on Reports, in `docs/architecture.md`'s paragraph on the control panel and in `CONTEXT.md`'s entry for it that a report opens in a tab of the window and a link inside one to anything but a report is handed to the system.

## What this does not decide

- **The words of each hint**, beyond §5 and ADR-331 §8.
- **The stylesheet's colours and sizes**, the implementation's under ADR-342 §3.
- **A dark theme.**
