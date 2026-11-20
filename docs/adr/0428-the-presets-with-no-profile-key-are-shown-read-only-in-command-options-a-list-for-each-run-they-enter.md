# ADR-428 — The presets with no profile key are shown read-only in Command options, a list for each run they enter

> **Partly amended — see [ADR-435](0435-the-profile-may-list-the-topics-by-name-a-survivor-going-to-the-name-its-chunks-are-nearest-and-none-listed-infers-them.md).** Of §2: once `vespera.topics.low-similarity-floor` ships, with the record that measures it, the embedding-scoring run's list shows it after the four of the topics, its record ADR-435. The rest of this record stands.

- **Date**: 2026-11-19
- **Status**: accepted on 2026-11-19.
- **Decided**: by the operator, on [#855](https://github.com/algernon28/vespera/issues/855): that the control panel shows the presets with no profile key, read-only, in the Command options section, as [ADR-330](0330-the-control-panel-edits-every-value-of-profile-yaml-showing-the-value-in-use-and-where-it-came-from-and-ten-technical-choices-ship-as-presets-in-application-yaml.md) §12 and [ADR-344](0344-the-control-panels-window-is-one-cockpit-run-and-stop-the-steps-as-bars-beside-the-output-the-progress-pages-three-charts-the-profile-and-the-folders-to-include-starting-from-the-real-configuration.md) §4 promise, no record being amended to drop the promise; that the values are read from `effective-values.properties`; that the block is divided by stage, each row showing the key and the value in use, a tooltip giving its provenance and the record behind the preset; that a row the jar has not said a value for says so in the words the window already uses; that after an invocation each row shows what it used, a value given on its command line included; and that the twenty presets of §2 are the ones shown. Under the operator's delegation on #855: the ids, the order, the headings, the tooltip's words, a key the file does not name keeping its row, and when the rows are read.
- **Amends**: ADR-330 §12, its `machine-values` *"lists `vespera.sidecars.start`, … in that order"* and *"a key the file does not name is left out"*: the presets with no profile key are shown in the cockpit's Command options as §1 to §5 say, in §2's order, and a key the file does not name keeps its row (§3). It carries a note at its head.
- **Amends**: [ADR-399](0399-topics-are-modularity-communities-over-a-ten-neighbour-graph-of-the-seed-passages-keyed-by-their-first-passage.md) §7, [ADR-400](0400-a-survivor-goes-to-the-topic-its-chunks-are-nearest-on-average-and-is-linked-from-one-15-per-cent-of-them-are-nearest.md) §6, [ADR-401](0401-a-topic-holding-under-half-of-one-percent-of-the-seed-passages-is-dropped-and-a-seed-feeding-none-is-listed.md) §6 and [ADR-406](0406-a-topic-of-300-survivors-or-more-is-divided-into-subtopics-its-own-communities-and-its-clusters-are-found-within-each.md) §7, as [ADR-411](0411-adr-399-to-401-move-seed-measurement-and-adr-406s-floor-is-not-shown-in-the-control-panel.md) §3 left them: the control panel shows each one's presets read-only, as §1 to §5 say (§7). Each carries a note at its head.
- **Amends**: ADR-411 §3, *"No class shows `vespera.topics.neighbours`, … or `vespera.clustering.subtopic-floor`"*, and its Consequences' *"an operator learns their values from `application.yaml` or `effective-values.properties`"*: the cockpit shows them (§7). It carries a note at its head.
- **Keeps**: ADR-344 §4's table of the four presets passed as options of the command line, each with its control, and its *Not offered*: no preset shown here is offered or passed, and the datasource's and Hikari's properties are shown *"where `effective-values.properties` names them"*, which it names none of (§6).
- **Keeps**: ADR-330 §1, the file's closed list, its sources and its staleness unchanged; ADR-330 §12's *"until then are shown and not edited"*; [ADR-377](0377-a-profile-row-is-one-line-its-provenance-in-a-tooltip-a-list-is-tags-a-code-set-named-by-its-folder-and-no-label-file.md) §4, the words a source is said in; [ADR-364](0364-the-output-panel-is-copyable-text-of-the-whole-output-from-a-file-of-its-own-and-every-text-of-the-window-is-copyable.md) §8, every text of the window selectable; [ADR-407](0407-topics-are-named-at-the-head-of-the-arrangement-step-by-a-naming-model-of-their-own-from-their-strongest-passages.md) §10's line of the naming model, whose sentence for a value not said §3 reuses and whose moments of reading §5 follows.
- **Settles**: [#855](https://github.com/algernon28/vespera/issues/855).

## Context

**What the jar says.** `EffectiveValuesFile` writes, as `vespera run` and `vespera label` start, every preset of ADR-330 §9's table as the application resolves it, each with its `<key>.source`: `vespera.clustering.neighbours` and `vespera.clustering.subtopic-floor`, the fourteen from `vespera.docling.ocr-preset` to `vespera.relevance.sample.per-band`, and the four of the topics, `vespera.topics.neighbours`, `vespera.topics.resolution`, `vespera.topics.breadth-floor` and `vespera.topics.see-also-share`. The resolution is written under the profile's own name, `clusterResolution`. A key nothing names, or one bound blank, is left out with its source. The file holds no path, so not the datasource's URL, and none of Hikari's properties.

**What the cockpit shows of it.** `EffectiveValues` reads the file. The Profile window shows, beneath a profile key whose `ProfileKey.hasAValueBeneath()` holds, the value the file names, `clusterResolution` among them; the Services section shows the label model and the naming model in one line each, `in-use-vespera.label.model` and `in-use-vespera.naming.model`, in ADR-377 §4's words, or *"The jar has not said yet what it would use; run an invocation to see it."* where the file names none. Command options holds the four presets passed as options of the command line, each a control whose id is its property, and the four command lines. No node shows any of the twenty presets above.

**Which run each enters.** By ADR-330 §9 and the records that added to its table: the OCR preset, the timeouts in a row and the shingle words enter the extraction run; MinHash's four numbers and stage 4's four cuts the content-redundancy run; the number of similarities, the sample's two numbers, the cluster neighbours, the subtopic floor and the four of the topics the embedding-scoring run, stage 5's.

## Decision

### 1. One block in Command options

**The Command options section ends with a block, `presets-in-use`**, beneath the four command lines: the heading *Presets in use*, one line beneath it, *Vespera's settings name these, and no control here changes them.*, and a list for each run its presets enter, `presets-in-use-<run>`, `<run>` being the run's stage name as `ProfileKey.Run.stageName()` gives it. Each list is its heading and its rows:

| Id | Heading |
| --- | --- |
| `presets-in-use-extraction` | *Stage 2: reading the text out of your files* |
| `presets-in-use-content-redundancy` | *Stage 4: finding documents that say the same thing* |
| `presets-in-use-embedding-scoring` | *Stage 5: measuring closeness, the topics and the groups* |

The lists stand in that order, the cascade's, as the block's own children.

### 2. The twenty presets, and their order

| List | Rows, in order |
| --- | --- |
| extraction | `vespera.docling.ocr-preset`, `vespera.docling.timeouts-in-a-row`, `vespera.redundancy.shingle-words` |
| content-redundancy | `vespera.redundancy.minhash.permutations`, `vespera.redundancy.minhash.bands`, `vespera.redundancy.minhash.rows-per-band`, `vespera.redundancy.minhash.seed`, `vespera.redundancy.near-duplicate-jaccard`, `vespera.redundancy.containment-index`, `vespera.redundancy.rare-shingle-sample`, `vespera.redundancy.rare-shingle-hits` |
| embedding-scoring | `vespera.relevance.top-similarities`, `vespera.topics.neighbours`, `vespera.topics.resolution`, `vespera.topics.breadth-floor`, `vespera.topics.see-also-share`, `vespera.clustering.neighbours`, `vespera.clustering.subtopic-floor`, `vespera.relevance.sample.bands`, `vespera.relevance.sample.per-band` |

The first two lists keep ADR-330 §9's order. The third follows stage 5's work: the score, the topics as they are found, kept and fitted, the clusters, and the relevance report's sample, drawn after them. These are every preset of ADR-330 §9's table, as ADR-399, ADR-400, ADR-401 and ADR-406 extend it, that has no profile key: ADR-330 §12's fifteen, the four of the topics and the subtopic floor.

### 3. A row

**Each row is one `SelectableText`, `in-use-<key>`, not editable**, reading the key, two spaces and the value the file names for it, as the file states it: `vespera.topics.breadth-floor  0.005`. It wraps where the sidebar is narrower than its text. **Where the working directory holds no file, the file cannot be read, or it names no value for the key**, the row reads the key, two spaces and *The jar has not said yet what it would use; run an invocation to see it.*, the sentence ADR-407 §10's line says in that case; every row stands whatever the file names, so the twenty are always listed, where ADR-330 §12 left out a key the file does not name.

### 4. Its tooltip

**Each row carries a tooltip of its own**: *From* and the words ADR-377 §4 gives the value's source, then a full stop, a space and *Decided in* the record behind the preset, with a full stop: `From your local settings file. Decided in ADR-401.` Where no value is said, or its source is none of ADR-377 §4's five, the tooltip is the second sentence alone. The record behind a preset is the one that made it a preset at the value it ships:

| Preset | Record |
| --- | --- |
| `vespera.clustering.neighbours` and the fourteen from `vespera.docling.ocr-preset` to `vespera.relevance.sample.per-band` | ADR-330 |
| `vespera.topics.neighbours`, `vespera.topics.resolution` | ADR-399 |
| `vespera.topics.see-also-share` | ADR-400 |
| `vespera.topics.breadth-floor` | ADR-401 |
| `vespera.clustering.subtopic-floor` | ADR-406 |

So a value given on the command line of the invocation that wrote the file reads `From the command that started Vespera.`, and one at the shipped copy's value `From Vespera's settings.`

### 5. When the rows are read

**The rows are read from the file as the window opens, whenever another working directory is chosen, and whenever the window's own child ends**, as ADR-407 §10's line of the naming model is. So after an invocation each row says what it used, a value its command line gave among them. A file older than the jar is shown as any other: the cockpit marks no staleness anywhere, and this block marks none (Known limits).

### 6. What the block does not show

- **`clusterResolution`**, the one preset with a profile key: the Profile window shows it beneath its row (ADR-330 §2).
- **The four presets passed as options of the command line**, `vespera.progress-interval`, `vespera.sidecars.wait-at-most`, `vespera.models.pull` and `vespera.models.pull-stalled-at-most`: each is a control of the same section (ADR-344 §4).
- **The Docling image, the two addresses, the label model and the naming model**: the Services section's (ADR-344 §1, ADR-407 §10).
- **The datasource's and Hikari's properties**: ADR-344 §4 shows them where the file names them, and the file's closed list names none (ADR-330 §1); this record adds none to it.

### 7. The four records' showing, restored

**ADR-399 §7's *"the control panel shows them read-only, as ADR-330 §12 shows a preset with no profile key"*, and ADR-400 §6's, ADR-401 §6's and ADR-406 §7's *"shows it read-only"*, which ADR-411 §3 struck, hold again**, the showing being this record's. ADR-411 §3's *"No class shows"* those five presets, and its Consequences' line that an operator learns their values from `application.yaml` or the file, no longer hold.

### 8. The modules, and run ids

**In `ui` alone**: `OptionsPane` and the class holding §2's table, and `Cockpit`'s reading of the file at §5's moments. The jar, `effective-values.properties`, its closed list and its writer are unchanged. **No run id moves.**

## Alternatives refused

- **One list in ADR-330 §12's order**, the cluster neighbours first. Twenty rows in one list carry no sign of what each shapes; the stage tells the operator what a value given by hand would redo.
- **A key the file does not name left out**, as ADR-330 §12 had it. The list would change length from one working directory to another, and a key bound blank on a machine would vanish without a word.
- **A shorter sentence on each row where nothing is said**, such as *not known until a run*. The window would then say one case in two ways; ADR-407 §10's sentence is already the window's.
- **The tooltip naming the record behind each value**, ADR-081 for MinHash's numbers, ADR-082 for stage 4's cuts. Each preset's shipped value rests on the record that made it a preset and on those it amends; the first is the one place the reasons of all of them are gathered.
- **A spinner or a box per preset, disabled.** A disabled control reads as a value the operator could unlock; selectable text says the value and nothing more.

## Consequences

- An operator sees in Command options every preset a run of this working directory was made under, and where each value came from, without opening `application.yaml`.
- A value given by hand, in `application-local.yaml`, the environment or a command line typed outside the control panel, shows on its row once an invocation has written the file.
- ADR-330, ADR-399, ADR-400, ADR-401, ADR-406 and ADR-411 carry a note at their heads.

## Known limits

- **A file older than the jar is not marked**, as nowhere in the cockpit is one; ADR-330 §1's staleness stays unshown.
- **Another process's invocation ending does not read the rows again**; choosing the working directory again does.
- **The rows say what the jar would use where the profile names nothing**, which for a preset with no profile key is what a run uses; `vespera publish` writes no file, so its own command line is never shown.

## What this does not decide

- **Whether any of the twenty gains a profile key**, and so a control: ADR-330 §12 leaves it to a record measuring what it moves.
- **Whether the datasource's or Hikari's properties join the file's closed list.**
- **Whether the cockpit marks a stale file**, here or anywhere.

## Tests

| Method | What it holds |
| --- | --- |
| `ui.ThePresetsInUseAreShownAndNotEditedTest.everyPresetIsARowOfItsStagesListWithTheValueTheFileNames` | §1 to §4: over a file naming each of the twenty at a value the shipped copy does not hold, from `local-file`, the block stands in Command options, its three lists in order, each holding its rows in §2's order and no other, twenty in all, each row reading its key, two spaces and the file's value, and its tooltip `From your local settings file. Decided in <record>.` |
| `ui.ThePresetsInUseAreShownAndNotEditedTest.withNoFileEveryRowSaysTheJarHasNotSaidYet` | §3 and §4: with no file every row stands, reading its key, two spaces and the sentence, its tooltip `Decided in <record>.` alone |
| `ui.ThePresetsInUseAreShownAndNotEditedTest.aValueGivenOnTheCommandLineIsShownWithItsSource` | §3 and §4: a file naming the breadth floor from `command-line` and the subtopic floor from `shipped`: the first row reads the value given and *From the command that started Vespera.*, the second *From Vespera's settings.*, and the OCR preset, not named, reads the sentence |
| `ui.ThePresetsInUseAreShownAndNotEditedTest.nothingOfTheBlockCanBeChanged` | §3: every row is selectable text, not editable; the block holds no control but such text; no node of the window carries one of the twenty keys as its id |
| `ui.ThePresetsInUseAreShownAndNotEditedTest.theWindowsOwnChildEndedIsReadAgain` | §5: a window opened on no file shows the sentence on the breadth floor's row; once its own child has ended, the file then naming the breadth floor from `command-line`, the row reads that value and its tooltip that source |

## What the implementation owes

- **A class of `ui`, `PresetsInUse`**, package-private, holding §2's table as an ordered map from `ProfileKey.Run` to the ordered keys of its list, §1's headings and §4's records, and building the block: `Node block()`, the `VBox` `presets-in-use` of §1, and `void show(Optional<EffectiveValues> said)`, setting each row's text and tooltip by §3 and §4, an empty `said` being no file. Each row is `SelectableText.wrapping`, its id `in-use-<key>`, its tooltip set by `setTooltip`, built once and its text and tooltip's text set by `show`.
- **The words of a source** read from ADR-377 §4's map in `ProfileRow`, which a package-private `static Optional<String> sourceInWords(String source)` there gives, and §3's sentence taken from one constant shared with `Cockpit.modelInUse`.
- **`OptionsPane.pane()`** adding the block after the four command lines; **`Cockpit.showModelsInUse`**, or a method it calls, calling `show` with `state.effectiveValues(folders.workingDirectory())`, so that §5's three moments read the rows.
- No id of the block is a property's name, so no existing test of Command options or of the naming model changes.
- The tests above passing.
