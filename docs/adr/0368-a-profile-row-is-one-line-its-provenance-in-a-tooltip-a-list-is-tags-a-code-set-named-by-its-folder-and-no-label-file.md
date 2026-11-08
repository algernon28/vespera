# ADR-368 — A profile row is one line, its provenance in a tooltip; a list is tags, a code set named by its folder, and no label file

- **Date**: 2026-11-08
- **Status**: accepted on 2026-11-08.
- **Decided**: by the operator, on [#764](https://github.com/algernon28/vespera/issues/764): that the operator never writes or edits a provenance in the window, and a value changed there is saved with `set in the control panel`, as ADR-343 has it (§1, §2); that a value's provenance shows in its title's tooltip alone, no line of the row showing it, and the code sets' shared provenance likewise (§1, §8); that a profile row is one line: its title, with its key in brackets just beneath it, its control on the same line, and a ▸ that opens its short description, its cost line and its reports (§3); that the title's tooltip holds the full explanation, the edge cases and where the value came from (§1, §4); that the description is what makes a row clear to the operator (§4); the title and the short description of each key, one sentence each saying what the value is and what it does (§4's table); that the cost line is one sentence on what a change redoes and what is kept, with a one-line warning only where it applies, approval, answers or new sample, and no time estimate anywhere (§4); that the words the window uses for the work name no stage and say no "invocation", "cluster" or "embedding" (§4); that the line of the value in use stays, reading *Left empty, Vespera uses ‹value› (‹where it comes from›)* (§3, §4); that a row's report links open ADR-362's native views and each shows its report's title, not its file name (§5); that every one-line text of the window cut short shows "…", copying still taking the whole text, which amends ADR-364 §8 for every such text (§6); that a code set which was renamed, given its folder's name again, is told in one log line naming the old name and the new one (§9); that a tooltip's text is help and is not copied, ADR-364's copy rule covering visible text alone (§1, §3); that a list value shows each item as a removable tag, with one box where Enter adds an item, the free-text list box and the `+` box gone (§7); that code sets are no longer renamed: the name field, `CodeSetRenames` and its file go, a code set is always named by its folder, and renames already saved are no longer read (§8, §9); that the label file goes from Command options, relevance being answered in the window by folder in the Inclusions tab (ADR-334 §6), and the command line keeping `vespera label <file>` (§10); that the launch fields and the folder not relevant stay as they are; that the Profile tab is to be more compact; and that `CONTEXT.md`'s Profile entry avoids "note" for a value's provenance (§11). Claude's readings of those decisions: the line of the value in use stands with the short description behind the ▸, where the decision to keep it places it no nearer (§3); a tooltip does not name its own key, which stands beneath the title (§4); a key whose file states no provenance, or a blank one, shows none in its tooltip (§1); `extractionAttempt`'s short description says which files a new try reads, so that no text of the window says every file whose reading failed is tried again (ADR-183) (§4's table); `clusterPageByteCeiling`, which no run enters but whose change draws the group pages again, takes a cost line of its own and not the line of the keys a change of which redoes no work (§4); and the names of ours that call a provenance a note are renamed (§11). Claude decided the rest, as details of the build: that the texts of each key are data in `profile-texts.json` beside `ProfileKey` (§4); that the ▸ is a toggle before the title, and a row of `wanted` starts open (§3); the width the titles take (§3); how a code set renamed is found, from the tree's earlier `code.csv`, and the line's words (§9); that the "…" is drawn by `SelectableText.Line` itself (§6); how a tag is removed by the keyboard and what is still typed in the box at a Save (§7); that a folder under the corpus root is refused in the Code sets section (§8); that a `code-set-names.yaml` left in a working directory is read, written and deleted by nothing (§9); the tooltips' words beyond each key's own text (§1, §3, §7); the ids; the steps of the build; and the tests.
- **Amends**: [ADR-343](0343-the-note-saved-with-a-profile-value-is-filled-in-for-the-operator-set-in-the-control-panel-or-the-provenance-already-saved-and-a-value-changed-under-its-old-note-is-saved-with-the-filled-in-one.md) §1, whose field captioned *Note saved with it*, filled in, with a hint beneath it, stands beside each value: no field holds a provenance, and the one the file states is in the title's tooltip (§1); §2, whose table reads a note the operator may edit: no note is edited, so its rows are two (§2); §3, whose `FILLED_IN_NOTE`, `noteShown` and `noteToSave` are renamed, `noteShown` then giving way to `provenanceStated` and `provenanceToSave` losing its note (§2, §11); and §4, whose floor's note changed by the operator is no longer a case (§2). It carries a note at its head.
- **Amends**: [ADR-344](0344-the-control-panels-window-is-one-cockpit-run-and-stop-the-steps-as-bars-beside-the-output-the-progress-pages-three-charts-the-profile-and-the-folders-to-include-starting-from-the-real-configuration.md) §1, whose Profile tab's row shows the key in small type and a plain description beneath the name, which steps a change makes again, a list built with `+` and the value's note: the row is §3's, its texts §4's, a list value §7's tags, and `wanted`'s and Seed folder's rows are drawn so; and whose Code sets section gives a folder under the corpus root an optional name: it lists the folders outside the corpus root alone (§8); §3, whose code sets' names start from `code-set-names.yaml`: nothing reads it (§9); §4, whose Command options offer `label-file`: they offer the five presets alone, `ChildCommand.withLabelFile` goes and `label <file>` is never passed (§10); and §6, whose key's report is a link reading its file name: it reads the report's title (§5). It carries a note at its head.
- **Amends**: [ADR-364](0364-the-output-panel-is-copyable-text-of-the-whole-output-from-a-file-of-its-own-and-every-text-of-the-window-is-copyable.md) §8, whose shrunk `Line` shows its text cut at its edge with no ellipsis drawn: every `Line` whose text runs past its edge shows "…" there (§6); and whose texts of a profile row are its title, key, description, value in use, cost, and the heading and hint of its provenance's field: they are §3's (§3). It carries a note at its head.
- **Amends**: [ADR-284](0284-code-is-kept-in-code-sets-each-below-the-nearest-folder-under-the-corpus-root-holding-a-build-file.md) §9, whose `code-set-names.yaml` renames a set and `CodeSetNames` reads it, and §10's dormant entries: a set is named by its folder alone, the file is read by nothing and written by nothing, and `CodeSetNames` goes (§9). It carries a note at its head.
- **Amends**: [ADR-295](0295-the-control-panel-writes-profile-values-label-answers-and-code-set-names-holding-the-working-directory-lock-one-operator-action-at-a-time.md) §2, whose control panel writes `code-set-names.yaml`, §7, its code-set names, and §9's `CodeSetRenames`: the file is not among those it writes, and §7 and the class go (§8, §9). It carries a note at its head.
- **Amends**: [ADR-296](0296-each-code-set-is-copied-whole-under-code-its-paths-kept-and-each-file-checked-against-the-size-and-time-the-walk-recorded.md) §3.1, whose set's folder in `code/` starts from its typed name and ranks a typed name first: it starts from the default name, the lowest member occurrence id the one key that tells two alike apart; and §6.2, whose names file adds a clause of dormant names to the closing line and a warning of a refused file: neither is written, and a set whose folder in `code/` changed is told in one line (§9). It carries a note at its head.
- **Amends**: [ADR-334](0334-the-default-output-folder-named-is-none-an-empty-field-shows-the-value-in-use-relevance-is-answered-by-folders-not-relevant-the-profile-is-one-form-and-a-code-set-folder-may-lie-outside-the-corpus-root.md) §4, whose table puts a rename of a set in `code-set-names.yaml`, and §5.5, whose names file spells an outside set's folder absolute: no file holds a rename (§9); and §6, whose folders not relevant are, in the window, how relevance is answered: they are the only way, Command options offering no label file, and `vespera label <file>` stays the command line's (§10). It carries a note at its head.
- **Amends**: [ADR-331](0331-the-control-panel-opens-a-window-of-its-own-and-its-pages-are-a-cockpit-with-a-picker-an-example-and-a-hint-on-its-fields.md) §8, whose table gives a hint to `new-name` and `name-<i>` and to `provenance` and `<key>.provenance`, and whose profile key's hint is drawn beside its field: a set takes no name, no field holds a provenance, and a key's row shows §4's texts, its hint among none of them (§1, §3, §8). It carries a note at its head.
- **Amends**: [ADR-336](0336-the-launch-page-starts-label-auto-beside-label-and-every-argument-of-the-commands-the-control-panel-starts-is-set-by-a-control-of-the-launch-page-or-listed-as-never-passed-with-why.md) §2 and §3, whose `label <file>` is set by `label-file` since ADR-344: it is never passed (§10). It carries a note at its head.
- **Amends**: [ADR-342](0342-the-control-panel-is-a-javafx-window-inside-the-vespera-jar-opened-by-the-jar-started-with-no-argument-and-its-loopback-server-and-pages-are-gone.md) §4, whose row of ADR-295's writes names `CodeSetRenames`: it goes (§9). It carries a note at its head.
- **Amends**: [ADR-345](0345-the-cockpit-replaces-the-browser-control-panel-its-tree-writes-the-folders-not-walked-and-publish-offers-each-destination.md) §4, whose entries never passed are three: they are four, `label <file>` among them (§10). It carries a note at its head.
- **Read with**: [ADR-197](0197-a-local-model-answers-the-relevance-questions-and-the-floor-is-a-rule-over-the-labels-so-no-document-reaches-a-hosted-model.md) §6, whose `vespera label [<file>]` and refusal of `--auto` with a file stand on the command line; the control panel passes none (§10). It carries a note at its head.
- **Read with**: [ADR-362](0362-an-invocations-report-is-one-json-file-drawn-as-static-html-through-a-mustache-template-and-no-command-opens-anything.md) §9 and §10, whose analysis report's `title` titles its native view's tab, which a row's link opens (§5).
- **Keeps**: [ADR-061](0061-the-profile-is-yaml-typed-java-records-one-object-per-value.md): every value carries its provenance, free text, under its key, and one written by hand in `profile.yaml` is read as before. [ADR-330](0330-the-control-panel-edits-every-value-of-profile-yaml-showing-the-value-in-use-and-where-it-came-from-and-ten-technical-choices-ship-as-presets-in-application-yaml.md) §1 and §5: the value in use is shown where the jar names one, and a save that changes nothing writes nothing. [ADR-295](0295-the-control-panel-writes-profile-values-label-answers-and-code-set-names-holding-the-working-directory-lock-one-operator-action-at-a-time.md) §4: every write refuses a blank provenance and the auto rule's words for the floor; and §5: what a change costs, `ProfileKey.cost()`, which §4's words say. [ADR-183](0183-the-extraction-cache-keeps-only-answers-about-the-document-and-a-refusal-the-converter-blamed-on-itself-is-asked-again.md): a new try reads again only the files whose reading the reader's crash or time-out ended, never one that failed by the file itself. [ADR-334](0334-the-default-output-folder-named-is-none-an-empty-field-shows-the-value-in-use-relevance-is-answered-by-folders-not-relevant-the-profile-is-one-form-and-a-code-set-folder-may-lie-outside-the-corpus-root.md) §5.1: a folder `codeSetFolders` names lies outside the corpus root. [ADR-296](0296-each-code-set-is-copied-whole-under-code-its-paths-kept-and-each-file-checked-against-the-size-and-time-the-walk-recorded.md) §6.3: a folder in `code/` the drawing did not write is counted as left behind. [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md) §5: the tree keys no run.
- **Settles**: [#764](https://github.com/algernon28/vespera/issues/764).

## Context

**A profile row today** (`ProfileRow`, ADR-344 §1) draws, top to bottom: the key's descriptive name, `key-name`, which `ProfileRow.nameOf` makes by splitting the key's words; the key in small type; a description, `ProfileKey.about()` and *Read from* `ProfileKey.hint()`, wrapping; the value's control; the line *In use where the profile names none: ‹value› (‹source›)*, where `effective-values.properties` names a value beneath the profile (ADR-330 §1), the source the file's code, `shipped`, `local-file`, `command-line`, `environment`, `code` or `unknown`; the cost line, `ProfileRow.costOf`, *A change makes again:* and the stage names of `ProfileKey.cost().runs()`, then a sentence for each of its three flags; the reports, *Reports:* and a link reading each report's file name, or the name grey; then a row of the caption *Note saved with it* and a `TextField`, `<prefix><key>.provenance`, filled by `ProfileEdit.noteShown` with the provenance the file states or `set in the control panel` (ADR-343 §1); and beneath it the hint *Saved beside the value in profile.yaml so that whoever reads it later knows where the value came from. You may write how you chose the value instead.*, wrapping. The same row stands in the Profile tab (`profile-`), in `wanted` (`wanted-`) and in Seed folder (`seed-`). Its words, but for the note's, are no record's: ADR-344 §5 leaves them to the implementation.

**`ProfileKey`** holds for each key its name in plain words, `plainName()`, a line saying what it is, `about()`, and what it is read from, `hint()`, which `TheWindowFollowsTheReadmesTableOfValuesTest` holds to the README's table of values. Of the cost's runs, no key's earliest is content census or arrangement; the five Confluence keys and `clusterPageByteCeiling` enter none (ADR-303 §9, ADR-359 §1).

**A list value** (`codeNameEndings`, `outOfScopeNameEndings`, `preferredLanguages`) is drawn by `listControl()` as a `TextField` holding the whole list, `<prefix><key>`, a second `TextField`, `<prefix><key>.add`, and a button `+`, `<prefix><key>.plus`, which appends the second field's text to the first. The value is the first field's text; `ProfileEdit` splits it on white space and commas (`PIECES`).

**The Code sets section** (`Cockpit.codeSetsPane()`) holds a list of entries, a folder field `code-set-folder`, a name field `code-set-name` (*A name for the deliverable (optional)*), Browse and `+`, a field `code-set-note` captioned *Note saved with the folders outside*, Save and an outcome line. A folder under the corpus root becomes an entry with the name typed, and its Save writes the entries named into `code-set-names.yaml` through `CodeSetRenames.render` (ADR-295 §7); an entry under the root given no name writes nothing. A folder outside the root goes through `CodeSetFolderChecks.refusal` and into `codeSetFolders` (ADR-334 §5), saved with the note by ADR-343 §2. Each load reads the file back through `CodeSetRenames.read`.

**The jar's side of the renames.** `deliverable.CodeSetNames` reads `code-set-names.yaml` (ADR-284 §9); `Deliverable` hands its entries to `CodeCopies`, where `CodeSetFolderNames.of(sets, typed)` starts each set's folder in `code/` from its typed name or its default name, a typed name ranking first, and `CodeSetFolderNames.dormant` counts the entries naming no set of the run (ADR-296 §3.1, §6.2). `DeliverableProgress.dormantCodeSetNames` and `codeSetNamesRefused` tell `GenerationTasklet`, which records the count through `InvocationRuns` and logs a warning for a refused file; `NextAction` adds *"; D names in code-set-names.yaml name no code set of this run"* to the closing line. `CodeCopies` writes `code.csv` at the tree's root, a row for each member, whose `code_set` column is the set's folder in the archive and whose `copy` column is `code/<folder in code/>/<kept path>` for a member copied. No stage reads either file, and no stage's version names `deliverable` (`StageModules`).

**The label file option.** Command options holds `label-file`, *The label file for Record my answers (optional)*, with Browse; the window's plan carries it as `ControlPanelState.Plan.labelFile`, and `ChildCommand.withLabelFile` puts it, absolute, as `label`'s `<file>` (ADR-344 §4), never for `label --auto`, which refuses one (ADR-197 §6). With none, `vespera label` records the answers of the working directory's `relevance-labels.yaml`, which the Inclusions tab answers from the folders not relevant (ADR-334 §6, ADR-345). `ChildCommand.ARGUMENTS` names `label-file` as the control of `label <file>`.

**Two records not built yet.** ADR-364 §8 makes every text of the window outside a control a `SelectableText`, a `SelectableText.Line` for a text on one line and a `SelectableText.Wrapping` for one that wraps, not editable and copied by selecting it; a tooltip's text is outside that rule, and a shrunk `Line` shows its text cut at its edge with no ellipsis. ADR-362 §10 opens each analysis report natively, in a tab `analysis-report-<name>` titled by the `title` of its `<name>.json`, from the Reports section and from a key's row alike, a report whose JSON the working directory does not hold drawn grey, *not written yet*.

## Decision

### 1. A value's provenance is in its title's tooltip, and the operator writes none

**No field, line or box of the window holds a value's provenance.** The caption *Note saved with it*, its `TextField` and the hint beneath it go from every row.

**The provenance the file states for the key, where it is not blank, is the last part of the title's tooltip** (§3): after the key's own tooltip text (§4), an empty line, then *Where this value came from: ‹provenance›*, the provenance whole, as the file states it. Where the file states none for the key, or a blank one, the tooltip is the key's own text alone. The tooltip is drawn again whenever the row is loaded, so after a Save that writes it shows what was written.

A tooltip's text is help and is not copied (ADR-364 §8). The provenance is read in the window, and copied from `profile.yaml`.

### 2. What a save writes

**A key is written by the first of two rows that matches it:**

| The value, against the file | What is written |
| --- | --- |
| as the file states | nothing: the key is not written |
| changed | the value, with **`set in the control panel`** |

The value is compared as ADR-330 §5 compares it, and a value emptied, which unsets the key, is a value changed (ADR-343 §2). So a Save of a form left alone writes nothing, and a value changed in the window never keeps the old provenance beneath it. A provenance written by hand in `profile.yaml` stays as written until its value is changed in the window.

`ProfileEdit.provenanceToSave(ProfileKey key, String value, Optional<Stated> inFile)` answers the table: empty for the first row, `FILLED_IN_PROVENANCE` for the second. ADR-343 §4 stands for the floor `vespera label --auto` set: changed in the window, it is saved with `set in the control panel` and is the operator's from that Save on. `ProfileEdit.refusalOf`'s refusals stand (ADR-295 §4); the window, which sends no provenance but `set in the control panel`, never meets the refusal of a blank one or of the auto rule's words.

### 3. A profile row is one line

**A row's first line holds, left to right, its toggle, its title over its key, and its control.**

- **The toggle**, `<prefix><key>.more`, a `ToggleButton` in the style class `more`, drawn flat, reads *▸* while the row is closed and *▾* while it is open, its tooltip *Show what this does, what a change redoes and its reports* or *Hide them*. It is reached by Tab, and Space presses it.
- **The title**, `<prefix><key>.title`, a `SelectableText.Line` in the style class `key-name`, holds the key's title (§4). Its tooltip holds the key's own tooltip text and the provenance (§1).
- **The key**, `<prefix><key>.key`, a `SelectableText.Line` in the style class `small`, holds the key as `profile.yaml` spells it, in brackets: *(seedFolder)*. It stands just beneath the title, its left edge the title's.
- **The control** stands on the same line, to the right of the title and the key, its top within the title's line. A key edited in a section of the sidebar or in the Inclusions tab shows there its value, read-only, and its link to that place, *Edit it in its section* or *Edit it in the Inclusions tab* (ADR-344 §1, ADR-345).

**The titles of one list take one column**: the title and key of every row of the Profile tab stand in a column as wide as the widest title of that tab's rows, at most 45 % of the row's width, so that every control of the tab starts at one edge; `wanted`'s rows take a column of their own by the same rule, and Seed folder's one row its title's width. A title wider than its column is cut with "…" (§6).

**Behind the toggle**, `<prefix><key>.details`, a `VBox` beneath the first line, not visible and not managed while the row is closed, holds, top to bottom:

1. the short description, `<prefix><key>.short`, a `SelectableText.Wrapping` (§4);
2. the line of the value in use, `<prefix><key>.in-use`, a `SelectableText.Line`, where `effective-values.properties` names a value beneath the profile for the key, as ADR-330 §1 has it, and none for a key the jar names nothing beneath (§4);
3. the cost line, `<prefix><key>.cost`, a `SelectableText.Wrapping`, and its warnings beneath it, `<prefix><key>.approval`, `<prefix><key>.answers` and `<prefix><key>.new-sample`, each a `SelectableText.Wrapping` of its own and each only where the cost applies it (§4);
4. the reports, `<prefix><key>.reports`, *Reports:* and a link for each, where the key has any (§5).

**A row of the Profile tab or of Seed folder starts closed, and a row of `wanted` starts open**, since the value it shows is the one the next start of Vespera wants answered. A row stays open or closed as the operator left it across a Save and a Reset; rows drawn anew, for another working directory or another wanted value, start as this paragraph says.

The key in small type above the description, the description with *Read from*, and every line beneath the control that the row drew go; nothing of the row stands outside its first line and its details. Each text of a row is a `SelectableText` (ADR-364 §8); the tooltips are not. The rows of `wanted` and Seed folder are drawn by the same class.

### 4. The row's texts

**Each key's title, short description and tooltip text are data, in `src/main/resources/io/algernon/controlpanel/profile-texts.json`**, beside `ProfileKey`: one JSON object whose names are the keys as `profile.yaml` spells them, in `ProfileKey`'s order, each value an object of three strings, `title`, `short` and `tooltip`. `ProfileKey` reads it once, through the control panel's `JsonReader` (ADR-285 §3), and answers `title()`, `shortDescription()` and `tooltip()`; `plainName()` and `about()` go, and `ProfileRow.nameOf` with them. `hint()` stays, held to the README's table as it is, and no row shows it. A file the class path does not hold, or one missing a key, fails `ProfileKey`'s first read with an `IllegalStateException` naming what is missing, which `TheProfileTextsTest` keeps from shipping.

The texts are data so that the words the operator settles change in one file, held by tests to the rules below, and in no class.

**The titles and short descriptions** are these:

| Key | Title | Short description |
| --- | --- | --- |
| `seedFolder` | Seed folder | The folder of documents you know you want kept; Vespera keeps your other documents by how close they are to these. |
| `degenerateOutputConfidenceFloor` | Lowest text quality | PDFs and scans whose text came out with a quality mark below this number are left out of the pages. |
| `degenerateOutputWordFloor` | Fewest words a file must hold | A file with fewer words than this is left out of the pages. |
| `boilerplateDocumentFrequencyFloor` | Repeated text | How common text must be, like a letterhead or footer, to be ignored when finding documents that say the same thing. |
| `embeddingModel` | Comparing model | The AI model that measures how close documents are to your seed documents and to each other, which decides the groups. |
| `relevanceScoreFloor` | Relevance cut-off | Documents less close to your seed documents than this number get no page; higher leaves out more. |
| `arrangementApproved` | Approved grouping | Vespera writes the pages only for the grouping of your documents you approve here. |
| `generationModel` | Writing model | The AI model that writes the short introduction on each page; it does not change which documents are kept. |
| `generationContextWindow` | Writing model's reading size | How much of each group's text the writing model reads before writing that group's page. |
| `clusterPageByteCeiling` | Largest group page | A group's page larger than this many bytes is split into several pages. |
| `logTimestampShareFloor` | Leave out logs | A text file with at least this share of lines starting with a time stamp is left out as a log. |
| `extractionAttempt` | Try number for unread files | Raise this by one and start Vespera again to read again the files whose reading crashed or took too long. |
| `codeNameEndings` | Name endings that mark code | Text files ending in one of these, such as .java, are kept as code in the code/ folder, not in the pages. |
| `outOfScopeNameEndings` | Leave out files by name ending | Files whose name ends in one of these, such as .ok or .tmp, are left out and get no page. |
| `translationSimilarityFloor` | Translation match | How alike two documents in different languages must be to count as translations; lower finds more but may pair wrong ones. |
| `preferredLanguages` | Preferred languages | When a document exists in several languages, only the copy in the first listed language that has one is kept. |
| `configurationSettingShareFloor` | Spotting configuration files | Text files made mostly of program settings are left out and get no page; this sets how mostly. |
| `deliverableLanguage` | Language of the pages | The language your pages are written in: English or Italian. |
| `clusterResolution` | Grouping detail | A higher number makes more, shorter pages; a lower number makes fewer, longer ones. |
| `codeSetFolders` | Code folders outside the documents folder | Code folders kept outside your documents folder; Vespera copies their code into the code/ folder in the Output folder. |
| `foldersNotWalked` | Folders not read | Folders Vespera skips completely, so nothing in them reaches your pages; choose them in the Inclusions tab. |
| `confluenceBaseUrl` | Confluence site address | The web address of the Confluence Cloud site that Publish sends your pages to. |
| `confluenceSpaceKey` | Confluence space | The short code of the Confluence space your pages are published in, such as DOCS. |
| `confluenceFolderId` | Confluence page or folder to publish under, by its number | The number, from its web address, of the Confluence page or folder that Publish puts your pages under. |
| `confluenceFolderTitle` | Confluence page or folder to publish under, by title | The title of the Confluence page or folder that Publish puts your pages under; fill in this or the number, not both. |
| `confluenceEmail` | Confluence sign-in email | The email of the Atlassian account that Publish uses to put your pages on Confluence. |

**The tooltips** are the operator's texts settled on #764, one for each key: the full explanation of the value, its units and ranges, what empty, 0, 1 or a fraction does, which kinds of file it leaves alone, which report helps choose it, and the facts behind the warnings. A tooltip does not name its own key, which stands beneath the title. The provenance is added to it by §1.

**The cost line is one sentence, chosen by the earliest work a change redoes**, `ProfileKey.cost().runs()`' first:

| The earliest run | The cost line |
| --- | --- |
| none, for the five Confluence keys | Changing it redoes no work; it takes effect the next time you press Publish. |
| none, for `clusterPageByteCeiling` | Changing it rewrites only the group pages (a split group's pictures are read again); everything else is kept. |
| byte-level reduction | Changing it redoes everything from sorting your files to writing the pages; text already read out of your files is kept. |
| extraction | Changing it redoes everything after reading the text out of your files; the text already read is kept. |
| content redundancy | Changing it redoes the search for documents that say the same thing, the groups and the pages; your files are not read again. |
| seed measurement | Changing it redoes everything from reading your seed documents to writing the pages; your other files are not read again. |
| embedding scoring | Changing it redoes the measuring of closeness, the grouping and the writing of the pages; your files are not read again. |
| generation | Changing it redoes only the writing of the pages; everything before it is kept. |

No key's earliest run is content census or arrangement, and the table gives them no line: a key that came to enter one first would need a line of its own, which `TheCostLineTest` asks for.

**A warning stands, one line each, only where `ProfileKey.cost()` applies it**, in this order:

| Where | The warning |
| --- | --- |
| `approvalCloses` | Approval: a grouping you approved must be approved again in the Approved grouping row. |
| `labelsRefused`, `seedFolder`'s | Answers: press Record my answers first; after the change the old answers file is refused. |
| `sampleDrawnAgain`, where `labelsRefused` is not | Answers: press Record my answers first, or unsaved answers in relevance-labels.yaml are lost. |
| `sampleDrawnAgain` | New sample: Vespera picks new documents for you to judge in relevance-labels.yaml. |

**The line of the value in use** reads *Left empty, Vespera uses ‹value› (‹where it comes from›).*, the source in plain words: `shipped`, *Vespera's settings*; `local-file`, *your local settings file*; `command-line`, *the command that started Vespera*; `environment`, *this computer's environment*; `code`, *Vespera's built-in value*. A source `unknown`, or any other, gives the line with no brackets: *Left empty, Vespera uses ‹value›.*

**The words, everywhere in these texts.** The work is named in plain words, *sorting your files*, *reading the text out of your files*, *finding documents that say the same thing*, *reading your seed documents*, *measuring how close each document is to your seed documents*, *grouping the documents*, *writing the pages*, and never by a stage's name. No title, short description, cost line, warning or line of the value in use says *invocation*, *run*, *cluster*, *embedding*, *arrangement*, *deliverable*, *converter*, *step* or *makes again*; a tooltip says none of them but *run* and *step*, which it may use as plain words, a key's, a file's or a model's name aside. `ProfileRow.costOf(ProfileKey)`, `ProfileRow.warningsOf(ProfileKey)` and `ProfileRow.inUseOf(EffectiveValues.Value)` give the cost line, its warnings and the line of the value in use. **No text states a time**: no estimate of how long a change takes, and no measurement of how long one took. **No text says that every file whose reading failed is read again**: a text that speaks of trying a file again says that only the files whose reading the reader's crash or time-out ended are tried again, and a file that failed by the file itself never is (ADR-183).

### 5. A row's reports

**Each report of the key, `Reports.OF_A_KEY`, is a link reading the report's title**, `<prefix><key>.report-<name>`, `<name>` the report's as ADR-362 §9 names it, `document-frequency` for `document-frequency.html`. The title is `Reports.TITLES`' for the name, one for each analysis report a key's row links, which is the `title` the report's `<name>.json` carries (ADR-362 §9); a test holds the two to one text. No text of a row ends `.html`.

- **Pressed, it opens the report's native view**, ADR-362 §10's tab `analysis-report-<name>`, or selects it where it is open, as the report's link in the Reports section does.
- **A report whose JSON the working directory does not hold** is not a link: `<prefix><key>.report-<name>` is a `SelectableText.Line` in the style class `grey` reading *‹title› (not written yet)*.

The Reports section of the sidebar stays as ADR-362 §10 has it.

### 6. A one-line text cut short shows "…"

**Every `SelectableText.Line` whose text runs past its right edge shows "…" there**, which amends ADR-364 §8's shrunk `Line`, *with no ellipsis drawn*, for every `Line` of the window. The "…" is a `javafx.scene.text.Text` of the `Line`'s own font and colour, on the `Line`'s background, laid over its right edge inside its padding: it takes no mouse event, holds no part of the `Line`'s text, and is visible exactly while the end of the text is out of view. So a `Line` reads as a `Label` cut with an ellipsis reads, its whole text is still selected, by dragging or Select All, and copied, and the "…" goes while a selection scrolls the end of the text into view and comes back when ADR-364 §8 puts the caret back at 0.

A `Label` that stays a control's text, a tab's title or a button's, cuts with JavaFX's own ellipsis, as it does.

### 7. A list value is tags

**`codeNameEndings`, `outOfScopeNameEndings` and `preferredLanguages` each show their items as tags**, in `<prefix><key>`, a `FlowPane` in the style class `tags`, in the order the file lists them, and after the last **one box**, `<prefix><key>.add`, a `TextField` whose prompt is *e.g. `<first piece of the key's example>`, then Enter*: *e.g. .java, then Enter*, *e.g. .ok, then Enter* and *e.g. it, then Enter* for the three.

- **A tag**, `<prefix><key>.tag-<i>`, `<i>` from 1 in order, an `HBox` in the style class `tag`, holds its item as a `SelectableText.Line` and **its remove button**, `<prefix><key>.remove-<i>`, a `Button` reading *×*, its tooltip *Remove `<item>`*.
- **Enter in the box adds what it holds**: its text split by `ProfileEdit`'s pieces, white space and commas, each piece not already a tag, compared exactly, added as a tag after the last in the order typed; the box is emptied. Enter in an empty box adds nothing.
- **A tag is removed by its button**, pressed with the mouse, or from the keyboard: each remove button is reached by Tab, in the tags' order before the box, and Space or Enter presses it. **Backspace in the box, while it is empty, removes the last tag.** After a removal the box has the focus.
- **The value** is the tags' items joined by one space, then the pieces still typed in the box and not added, so that a Save loses nothing typed. A load draws a tag for each piece the file's value holds, by the same split, and empties the box.

The list field, `<prefix><key>` as a `TextField`, and the button `+`, `<prefix><key>.plus`, go. What a list may hold is checked at a Save by `ProfileEdit`, as it is (ADR-295 §4): an ending without its dot, or a language code of three letters, is refused there with its line.

### 8. The Code sets section

**It opens with `codeSetFolders`' title**, `code-set-title`, a `SelectableText.Line` in the style class `key-name` reading the key's title, its tooltip the key's tooltip text and the provenance the file states for `codeSetFolders`, by §1's rule. No field, line or box holds that provenance, and the caption *Note saved with the folders outside* goes.

**It lists the folders `codeSetFolders` states, outside the corpus root, and nothing else**: `code-set-list`, a row for each, its whole path and **Remove**. Beneath it, `code-set-folder`, Browse and `+`, `code-set-add`, add a folder, through `CodeSetFolderChecks.refusal` as a folder outside the root goes today. **A folder under the corpus root is refused by the same check**, with its line *the folder `<folder>` is not added: it is inside the corpus root `<root>`*: stage 1 finds the code sets under the corpus root by their build files (ADR-284 §2), and nothing names them.

The name field `code-set-name` goes, and so do the entries under the corpus root and the words *named* and *(outside the corpus root)* of a row.

**Its Save**, `code-sets-save`, writes `codeSetFolders` by §2 and nothing else, through `ProfileRow.save`'s checks and words: *nothing was written: every value is as profile.yaml states it* where it changed nothing, and *codeSetFolders saved in `<file>`*, with `set in the control panel`, where it wrote.

### 9. A code set is named by its folder

**No class of the jar or the control panel reads, writes or names `code-set-names.yaml`.**

- **In the control panel**, `CodeSetRenames` goes, and the file leaves `WorkingDirectoryWrite.FILES`, which holds `profile.yaml`, `relevance-labels.yaml` and `not-relevant-folders.yaml`; a write naming it is refused as any file not among them is.
- **In the jar**, `deliverable.CodeSetNames` goes. `CodeSetFolderNames.of(sets)` starts each set's folder in `code/` from **its default name**, its folder's last element (ADR-284 §9, ADR-334 §5.5), made safe and cut as ADR-296 §3.1 has it, and tells two alike apart by the lowest member occurrence id alone: the lower keeps the name, each other takes `~` and its own. `CodeSetFolderNames.dormant`, `DeliverableProgress.dormantCodeSetNames` and `codeSetNamesRefused`, `GenerationTasklet`'s count and warning, `InvocationRuns`' record of the count and `NextAction`'s clause of dormant names go.

**A `code-set-names.yaml` left in a working directory stays as it is**: nothing reads it, writes it, moves it or deletes it, and nothing says it is there. It is the operator's file, and it changes nothing. The README says it is no longer read and can be deleted by hand.

**A set whose folder in `code/` changed is told in one line.** Before it writes its own listing, `CodeCopies` reads the `code.csv` the tree's root holds from the drawing before, where there is one, and takes for each set folder of its `code_set` column the folder in `code/` of the first `copy` cell of its rows that is not empty. For each set of this drawing whose folder that gives is not the one `CodeSetFolderNames.of` gives it, in the order the sets are drawn, it calls `DeliverableProgress.codeSetFolderChanged(String setFolder, String earlier, String now)`, and `GenerationTasklet` logs one line at INFO:

> *Code set `<set folder>` is now in code/`<now>`, no longer in code/`<earlier>`, which is left as it is.*

A set the operator had renamed takes its folder's name at the first drawing after this change, and is told once: the drawing after it reads the new name from the listing. A tree with no earlier `code.csv`, a set the earlier listing does not hold or holds with no copy, and a listing that cannot be read or whose header names neither column, are told nothing, and the drawing goes on. Reading the listing keys nothing, and its rows decide no name.

**A tree drawn again** over a run with a set the file renamed gives that set its default name. Its folder of the earlier name in `code/` is one this drawing did not write, counted on the closing line as left behind by ADR-296 §6.3, and `code.csv` and the index name the set by its new folder.

### 10. The label file option goes

**Command options offers the five presets of ADR-344 §4 and nothing else.** `label-file`, its Browse `label-file.browse` and its heading *Label file* go; **Back to the defaults** puts the presets back. `ControlPanelState.Plan` loses `labelFile`, `ChildCommand.withLabelFile` goes, and **Record my answers** starts `vespera label --db-dir=<working directory>` with the presets passed, whose label file is the working directory's `relevance-labels.yaml`, answered in the Inclusions tab by folder (ADR-334 §6). `command-line-label` shows that line.

**`ChildCommand.ARGUMENTS`** names no control for `label <file>`: it is never passed, its reason *Never passed: the window answers the questions in the Inclusions tab, by folder (ADR-334 §6), in the working directory's label file, which label reads where no file is named; the command line keeps it (ADR-368).* The entries never passed are four: `vespera help`, `vespera version`, `serve db-dir` and `label <file>`.

**The command line keeps `vespera label <file>`** (ADR-197 §6): a file named there is read, and `label --auto` with one is refused, as they are.

### 11. A provenance is not a note, in our names

`CONTEXT.md`'s Profile entry avoids "note" for a value's provenance, so the names of ours that call it one are renamed, behaviour unchanged, at step 1; what step 3 then does with each is in the last column:

| Was | Is, at step 1 | At step 3 |
| --- | --- | --- |
| `ProfileEdit.FILLED_IN_NOTE` | `ProfileEdit.FILLED_IN_PROVENANCE` | stays |
| `ProfileEdit.noteShown(Optional<Stated>)` | `ProfileEdit.provenanceShown(Optional<Stated>)` | goes; `ProfileEdit.provenanceStated(Optional<Stated>)`, the provenance the file states where it is not blank, empty otherwise, in its place |
| `ProfileEdit.noteToSave(ProfileKey, String, String, Optional<Stated>)` | `ProfileEdit.provenanceToSave(ProfileKey, String, String, Optional<Stated>)` | `ProfileEdit.provenanceToSave(ProfileKey, String, Optional<Stated>)`, §2's |
| `ProfileRow.note`, `ProfileRow.noteToSave()` | `ProfileRow.provenance`, `ProfileRow.provenanceToSave()` | the field goes; `provenanceToSave()` stays |
| `Cockpit.codeSetNote`, id `code-set-note` | `Cockpit.codeSetProvenance`, id `code-set-provenance` | goes, `code-set-title` holding the provenance in its tooltip (§8) |
| `controlpanel.TheNoteSavedWithAValueTest` and its methods | `controlpanel.TheProvenanceSavedWithAValueTest`, the Tests below | as the Tests below |

`Adr.THE_NOTE_SAVED_WITH_A_PROFILE_VALUE_IS_FILLED_IN` keeps its name, which spells ADR-343's title. Text written for a reader outside this project, a `@Story` or a `@DisplayName` among it, and the window's texts of §4, are free of the lists (ADR-122).

### 12. The modules, and run ids

**No run id moves.** The classes changed are of the control panel, `ProfileKey`, `ProfileRow`, `Reports`, `Cockpit`, `ProfileEdit`, `ControlPanelState`, `ChildCommand`, `WorkingDirectoryWrite` and `SelectableText`, `CodeSetRenames` removed, the stylesheet and `profile-texts.json` added; of `deliverable`, `CodeSetFolderNames`, `CodeCopies`, `Deliverable` and `DeliverableProgress`, `CodeSetNames` removed; and of `pipeline`, `GenerationTasklet`, `InvocationRuns` and `NextAction`. No stage's version names `deliverable` or `pipeline` (ADR-222, ADR-274 §5, `StageModules`), and none of them decides a verdict, a cache key or a cluster; the names of `code/` are the drawing's, and the earlier `code.csv` it reads decides no name and keys no run. No class is added to `pipeline`.

### 13. The build, in steps that each land green

1. **The names** (§11, its second column): the renames, the tests renamed with them, and the Tests rows of ADR-343 and ADR-344 naming them corrected in place. It needs nothing of ADR-364's.
2. **The texts** (§4): `profile-texts.json`, `ProfileKey`'s `title()`, `shortDescription()` and `tooltip()` in place of `plainName()` and `about()`, the cost line and its warnings, and the line of the value in use, drawn in the row as it stands, its title, its description with no *Read from*, its cost and its in-use line. It needs nothing of ADR-364's.
3. **The row** (§1, §2, §3, §8's title, §11's third column): the one line, the toggle and the details, the title's tooltip with the provenance, the provenance's field and hint gone, and §2's save, in `ProfileRow` and in the Code sets section. It lands after step 2 and after ADR-364's step 5, which builds `SelectableText`.
4. **The "…"** (§6), in `SelectableText.Line`. It lands after ADR-364's step 5.
5. **The tags** (§7). It lands after ADR-364's step 5.
6. **The reports' titles** (§5): `Reports.TITLES`, the row's links by id and title. It lands after step 3 and after ADR-362's step 7, by which every analysis report a key's row links writes its JSON and opens natively.
7. **Code sets named by their folders** (§8's list, §9): the control panel's and the jar's parts together, so that no step leaves a file one side writes and the other no longer reads, and the line of a set whose folder changed; with the README's and `CONTEXT.md`'s lines on the file in the same change, since the claims check reads the README's files against the jar's `FILE_NAME` constants. It needs nothing of ADR-364's.
8. **The label file option** (§10). It needs nothing of ADR-364's.

## Consequences

- **A Profile row is one line**, its title, its key and its control, where it was its control and seven lines at least; what it does, what a change costs and its reports are one press of ▸ away, and its explanation and provenance a pointer's rest on the title.
- **The operator writes no provenance in the window.** A value changed there is saved with `set in the control panel`; a provenance written by hand in `profile.yaml` stays until its value is changed in the window.
- **The texts the operator reads are one file**, `profile-texts.json`, and the cost line and its warnings one table each, so a word changes in one place and the tests hold every text to the same rules.
- **Every cut line of the window says it is cut**, with "…", and still copies whole.
- **A list reads as its items**, each removed by itself, and is added to with Enter.
- **A report a row links reads as its title**, and opens drawn natively.
- **The Code sets section is the folders outside the corpus root**, the one thing the operator sets there; the code sets under the root are stage 1's to find and their folders' to name.
- **A tree drawn again renames in `code/` a set the operator had renamed**, to its folder's name, tells it in one line, and counts the old folder as left behind; a working directory's `code-set-names.yaml` stays and changes nothing.
- **Record my answers records the working directory's label file alone**; a label file kept elsewhere is recorded from the command line, `vespera label <file>`.
- **The closing line loses its clause of dormant names**, and the generation step its warning of a refused names file.

## Known limits

- **A provenance cannot be copied from the window**: a tooltip is not copied (ADR-364 §8), and no line shows it. It is copied from `profile.yaml`.
- **A provenance shows only while the pointer rests on the title**, so a row does not say at a glance which values the operator set by hand.
- **The tooltips are long**, the longest some 300 words; JavaFX's tooltip wraps them at the width the stylesheet gives it and shows them for as long as the pointer rests.
- **A list item holding a space or a comma cannot be a tag**: the pieces split it, as `ProfileEdit` reads a list today.
- **A set the operator had renamed takes its folder's name**, told once in a line of the output; a tree drawn into a new Output folder, which holds no earlier `code.csv`, tells nothing, its old tree keeping the old name.
- **The line of a set's folder changing is told for any change**, a `~` and an occurrence id gained or lost among them, and not only for a rename undone.
- **Two code sets whose folders end alike** are told apart by `~` and an occurrence id alone, which the operator can no longer replace with a name.

## Alternatives refused

- **A provenance line beside each value, with a link that opens it in an editable box.** The operator writes no provenance in the window, and a line on each row would take the room the compact tab gives back.
- **The provenance written by Vespera itself, with who, when and from which tab.** ADR-343 refused a provenance dated or naming the user, which would make every save a change of each provenance.
- **A row stacked over several lines, its description, cost and reports always shown.** It is the length the operator wanted shortened.
- **The Profile tab as one table of four columns, setting, value, why and what a change redoes.** The cost shrinks to a fragment there, and a list's tags and a folder's Browse do not fit a cell.
- **A list of keys beside one pane showing the selected key whole.** One key at a time hides the values the operator compares across rows.
- **The texts as constants of `ProfileKey`.** Some 30 KB of the operator's prose would sit among the key's type and runs, and a change of a word would be a change of the enum.
- **The texts as a `.properties` file.** Its escapes and its line continuations make a long text hard to read and to compare with what was settled, and the control panel already reads JSON.
- **The tooltip naming its key.** The key stands beneath the title, in brackets.
- **The cost line as the stage names**, *A change makes again: …*. It names the work by words the operator does not use.
- **A time on the cost line.** Any figure is one measurement of one corpus, and reads as a promise for every other.
- **Report links reading the file name.** A file name is the jar's, and the title is what the native view's tab reads.
- **The jar reading `code-set-names.yaml` to tell the renames it no longer applies.** Its renames are no longer read, as decided, and the tree's own listing says where each set stood.
- **The jar still reading `code-set-names.yaml` while the window stops writing it.** A set would carry a name nobody could change from the window, and the drawing two sources of a set's name.
- **The file deleted, or a warning on each invocation while it is there.** The file is the operator's, and a line repeated for a file that changes nothing is noise; the README says it once.
- **A folder under the corpus root kept as an entry with no name.** It writes nothing and changes nothing, so the section would list folders that do nothing.
- **A tag removed by a click on its text.** A click on the text selects it, to be copied.
- **The label file kept as a field, or moved to the Inclusions tab.** The operator answers in the window by folder, and a file kept elsewhere is the command line's.

## Tests

Owed by the build. The first column is the step of §13 that adds the test, and its green is that step's: no row lands before the code it holds. Each window test draws the window through `WindowForTests` on the Headless platform (ADR-342 §6).

| Step | Method | What it holds |
| --- | --- | --- |
| 1 | `controlpanel.TheProvenanceIsNamedSoTest.noNameOfTheControlPanelCallsAProvenanceANote` | the sources of `src/main/java/io/algernon/controlpanel`, their comments taken out: no identifier and no string naming an id holds the word note, alone or as a part of a name, as `note`, `Note…`, `…Note` or `…_NOTE`, and the scan reads `ProfileEdit`'s `FILLED_IN_PROVENANCE`, so that a scan of nothing cannot pass (§11) |
| 1 | `controlpanel.TheProvenanceSavedWithAValueTest.theProvenanceShownIsTheOneTheFileStatesOrTheFilledInOne` | ADR-343's rows of it, under the new names (§11) |
| 1 | `controlpanel.TheProvenanceSavedWithAValueTest.eachValueIsWrittenAsTheTableSays` | ADR-343 §2's table, row by row, under the new names (§11) |
| 1 | `controlpanel.TheProvenanceSavedWithAValueTest.aFloorTheLocalModelSetChangedUnderItsProvenanceBecomesTheOperators` | ADR-343 §4, under the new names (§2, §11) |
| 2 | `controlpanel.TheProfileTextsTest.everyKeyHasATitleAShortDescriptionAndATooltipAndNoOtherKeyIsListed` | `profile-texts.json` read from the class path: its names exactly `ProfileKey`'s keys, in its order; each entry's `title`, `short` and `tooltip` not blank, the title and the short description on one line each, the short description one sentence ending in a stop; and `ProfileKey.SEED_FOLDER.title()` *Seed folder* (§4) |
| 2 | `controlpanel.TheProfileTextsTest.aTooltipDoesNotNameItsOwnKey` | no key's tooltip holds the key as `profile.yaml` spells it (§4) |
| 2 | `controlpanel.TheProfileTextsTest.noTextNamesAStageAnInvocationAClusterOrAnEmbedding` | every title, short description, cost line and warning, and the line of the value in use for each source: none holds, as a whole word in any case, a stage's name of `ProfileKey.Run.stageName()`, *invocation*, *run*, *cluster*, *embedding*, *arrangement*, *deliverable*, *converter*, *step* or *makes again*; every tooltip, each key's name, each file name ending `.html`, `.yaml` or `.csv` and each word holding a `/` taken out: none holds them but *run* and *step* (§4) |
| 2 | `controlpanel.TheProfileTextsTest.noTextStatesATime` | every title, short description, tooltip, cost line, warning and line of the value in use: none holds a number followed by *ms*, *s*, *sec*, *second*, *seconds*, *min*, *minute*, *minutes*, *h*, *hour* or *hours*, nor *~* before a number; a clock time such as *10:15:30* is no time taken (§4) |
| 2 | `controlpanel.TheProfileTextsTest.aTextThatSpeaksOfTryingAFileAgainSaysOnlyCrashesAndTimeOutsAre` | every title, short description, tooltip and cost line holding *retry*, *retried*, *tried again*, *try again* or *read again the files* also holds *crash* and *took too long* or *time-out* or *timeout*; and `extractionAttempt`'s short description and tooltip are among them, so that a check of nothing cannot pass (§4, ADR-183) |
| 2 | `ShippedConfigurationTest.theComparingModelsTooltipNamesTheShippedModel` | `embeddingModel`'s tooltip holds the embedding model `application.yaml` ships, as `ProfileKey.EMBEDDING_MODEL.hint()` is held to it (§4) |
| 2 | `controlpanel.TheCostLineTest.eachKeysCostLineIsTheLineOfItsEarliestRun` | for each key, `ProfileRow.costOf(key)` is §4's line for its earliest run, the five Confluence keys' and `clusterPageByteCeiling`'s each their own; and no key's earliest run is content census or arrangement, which the table gives no line (§4) |
| 2 | `controlpanel.TheCostLineTest.aWarningStandsOnlyWhereTheCostAppliesIt` | for each key, `ProfileRow.warningsOf(key)` is, in order, the approval's where `approvalCloses`, `seedFolder`'s answers line where `labelsRefused`, the other answers line where `sampleDrawnAgain` and not `labelsRefused`, and the new sample's where `sampleDrawnAgain`, and nothing else; `generationModel`'s none (§4) |
| 2 | `controlpanel.TheCostLineTest.theLineOfTheValueInUseSaysWhereItComesFromInPlainWords` | `ProfileRow.inUseOf` for `qwen3:8b` and each source: *Left empty, Vespera uses qwen3:8b (Vespera's settings).* and the four others as §4 gives them; `unknown` and `nowhere` *Left empty, Vespera uses qwen3:8b.* (§4) |
| 3 | `controlpanel.TheProfileRowIsOneLineTest.aRowsFirstLineIsItsToggleItsTitleOverItsKeyAndItsControl` | the row of `seedFolder` on the Profile tab and of `boilerplateDocumentFrequencyFloor`, closed, after a layout pass: `…more` a `ToggleButton` reading ▸, then `…title` a `SelectableText.Line` reading the key's title, `…key` one reading the key in brackets, *(seedFolder)*, beneath the title, its top at the title's bottom or below and its left within a pixel of the title's; the control's top within the title's height, its left right of the title's right; `…details` neither visible nor managed (§3) |
| 3 | `controlpanel.TheProfileRowIsOneLineTest.everyControlOfTheTabStartsAtOneEdge` | at the window's 1500 by 960, every row's control on the Profile tab starts at one left edge, within a pixel, and no title is cut: each title's text no wider than its `Line` (§3) |
| 3 | `controlpanel.TheProfileRowIsOneLineTest.theToggleOpensItsDescriptionInUseLineCostWarningsAndReports` | the toggle of `relevanceScoreFloor` pressed: it reads ▾, `…details` visible and managed, holding in order `…short`, `…cost`, `…approval`, `…answers`, `…new-sample` and `…reports`, each text a `SelectableText`; pressed again, closed. The toggle of `generationModel`, in a working directory whose `effective-values.properties` names its value with the source `shipped`: `…in-use` after `…short`, reading *Left empty, Vespera uses …(Vespera's settings).*, and no warning. The toggle reached by Tab, and Space opening it (§3, §4) |
| 3 | `controlpanel.TheProfileRowIsOneLineTest.theTitlesTooltipHoldsTheExplanationAndWhereTheValueCameFrom` | a profile stating `boilerplateDocumentFrequencyFloor` with the provenance *picked off the frequency table*: `…title`'s tooltip `ProfileKey.tooltip()`, an empty line, then *Where this value came from: picked off the frequency table*; a key absent from the file and one with a blank provenance: the tooltip `ProfileKey.tooltip()` alone (§1) |
| 3 | `controlpanel.TheProfileRowIsOneLineTest.noLineOfTheRowShowsTheProvenanceAndNoFieldHoldsOne` | a profile stating a provenance holding a marker for every key: every row open, walked as ADR-364 §8 walks the window, no text holds the marker; no node `…provenance`, `…provenance-box` or `…edit-reason`; no text *Note saved with it*, none beginning *Saved beside the value*, none equal to the key alone without brackets and none holding *Read from* (§1, §3) |
| 3 | `controlpanel.TheProfileRowIsOneLineTest.theSeedFolderAndAWantedValueAreDrawnAsTheProfileTabsRows` | `seed-seedFolder` and a `wanted-` row after a child that ended wanting a value: each its toggle, title, key and control on one line and its title's tooltip; the Seed folder row closed and the `wanted-` row open (§3) |
| 3 | `controlpanel.TheProvenanceSavedWithAValueTest.theProvenanceStatedIsTheFilesWhereNotBlank` | `provenanceStated` for a provenance stated, none, `null` and blank: the stated one, else empty (§11) |
| 3 | `controlpanel.TheProvenanceSavedWithAValueTest.aValueChangedInTheWindowIsSavedWithSetInTheControlPanel` | a value stated with a provenance the operator wrote, changed in its control, and Save: `profile.yaml` holds the new value with `set in the control panel`, and the title's tooltip then ends *Where this value came from: set in the control panel*; a value emptied the same; a value left as it is, beside it, not written (§2) |
| 3 | `controlpanel.TheCodeSetsSectionTest.theCodeSetsProvenanceIsInTheirTitlesTooltip` | `codeSetFolders` stated with a provenance: `code-set-title` a `SelectableText.Line` reading the key's title, its tooltip the key's tooltip text, an empty line and *Where this value came from: ‹provenance›*; no node `code-set-note` or `code-set-provenance`, and no text *Note saved with the folders outside* (§8) |
| 4 | `controlpanel.ACutShortLineShowsAnEllipsisTest.aLineNarrowerThanItsTextShowsAnEllipsisOverItsEnd` | a `SelectableText.Line` of 300 characters laid out 200 px wide: its "…" visible, its right edge within a pixel of the `Line`'s inner right edge; a `Line` of ten characters as wide: none visible; Select All then Copy giving the 300 characters, with no "…" (§6) |
| 4 | `controlpanel.ACutShortLineShowsAnEllipsisTest.theEllipsisTakesNoMouseEventAndIsNoPartOfTheText` | the "…" mouse-transparent, the `Line`'s text unchanged by it, and a press over it placing the caret in the `Line` (§6) |
| 4 | `controlpanel.ACutShortLineShowsAnEllipsisTest.itGoesWhileTheEndIsInViewAndComesBackWithTheCaretAtTheStart` | Select All with the focus in the cut `Line`: the "…" not visible; the focus moved away: the caret at 0 and the "…" visible again (§6, ADR-364 §8) |
| 4 | `controlpanel.ACutShortLineShowsAnEllipsisTest.theWindowsCutLinesShowIt` | the window at 700 by 960, where `where`'s path is wider than the header: `where`'s "…" visible; at 1500 by 960, where it fits: not (§6) |
| 5 | `controlpanel.AListValueIsTagsTest.eachItemTheFileListsIsATagInItsOrder` | `codeNameEndings` stated `.java .sql, .kt`: `profile-codeNameEndings` a `FlowPane` holding three tags, `.tag-1` to `.tag-3`, reading `.java`, `.sql` and `.kt` as `SelectableText.Line`s, then `profile-codeNameEndings.add`, a `TextField`, last (§7) |
| 5 | `controlpanel.AListValueIsTagsTest.enterAddsEachPieceTypedAsATagOnceAndEmptiesTheBox` | `.py, .java .rs` typed and Enter, `.java` a tag already: tags `.py` and `.rs` added after the last in that order, `.java` not twice, the box empty; Enter in the empty box adding nothing (§7) |
| 5 | `controlpanel.AListValueIsTagsTest.aTagIsRemovedByItsButton` | `profile-codeNameEndings.remove-2` fired: the second tag gone, the others in order, the box holding the focus (§7) |
| 5 | `controlpanel.AListValueIsTagsTest.backspaceInTheEmptyBoxRemovesTheLastTag` | Backspace pressed in the empty box: the last tag gone; Backspace in the box holding `.p`: no tag gone and the box `.` (§7) |
| 5 | `controlpanel.AListValueIsTagsTest.eachRemoveButtonIsReachedByTabAndRemovesItsTagFromTheKeyboard` | each remove button focus-traversable, in the tags' order before the box; focus on `.remove-1`, then Space: the first tag gone (§7) |
| 5 | `controlpanel.AListValueIsTagsTest.aSaveWritesTheTagsJoinedByOneSpaceAndWhatIsStillTyped` | tags `.java` and `.sql`, `.kt` typed and not entered, then Save: `profile.yaml`'s `codeNameEndings` value `.java .sql .kt` with `set in the control panel`; and a list left as the file states it writing nothing (§7, §2) |
| 5 | `controlpanel.AListValueIsTagsTest.theThreeListValuesAreTagsAndTheListBoxAndThePlusAreGone` | `codeNameEndings`, `outOfScopeNameEndings` and `preferredLanguages`, on the Profile tab: each `profile-<key>` a `FlowPane`, its box's prompt *e.g. .java, then Enter*, *e.g. .ok, then Enter* and *e.g. it, then Enter*; and no node `profile-<key>.plus` (§7) |
| 6 | `controlpanel.ARowsReportsReadTheirTitlesTest.eachLinkReadsItsReportsTitle` | `relevanceScoreFloor`'s row open, its three reports' JSON in the working directory: `…report-relevance-labelling`, `…report-cluster-sizes` and `…report-seed-corpus-comparison`, `Hyperlink`s reading `Reports.TITLES`' titles for them, in `Reports.OF_A_KEY`'s order; no text of the row ends `.html` (§5) |
| 6 | `controlpanel.ARowsReportsReadTheirTitlesTest.aLinkOpensItsReportsNativeView` | `profile-boilerplateDocumentFrequencyFloor.report-document-frequency` fired: the tab `analysis-report-document-frequency` selected, holding no `WebView`, and nothing handed to the system (§5, ADR-362 §10) |
| 6 | `controlpanel.ARowsReportsReadTheirTitlesTest.aReportNotWrittenIsItsTitleGrey` | the working directory holding no `document-frequency.json`: `…report-document-frequency` a `SelectableText.Line` in the style class `grey`, reading the title and *(not written yet)*, and no `Hyperlink` in the row (§5) |
| 6 | `pipeline.EachAnalysisReportsNativeViewShowsWhatItsPageShowsTest.eachReportsTitleIsTheOneTheControlPanelLinksItBy` | for each report of `Reports.OF_A_KEY`, the JSON its writer writes over the class's fixture carries as its `title` `Reports.TITLES`' title for its name; and `Reports.TITLES` names those reports and no other (§5) |
| 7 | `controlpanel.TheCodeSetsSectionTest.itOffersNoNameAndListsTheFoldersOutsideTheCorpusRootAlone` | a `codeSetFolders` of two folders outside the corpus root: `code-set-list` holds their two paths, each with Remove, and no word *named*; no node `code-set-name` (§8) |
| 7 | `controlpanel.TheCodeSetsSectionTest.aFolderUnderTheCorpusRootIsRefusedAsInsideIt` | a folder under the corpus root typed and `+` pressed: `code-set-outcome` reads *the folder `<folder>` is not added: it is inside the corpus root `<root>`*, and the list unchanged (§8) |
| 7 | `controlpanel.TheCodeSetsSectionTest.aNamesFileLeftInTheWorkingDirectoryIsNeitherReadNorWritten` | a working directory holding a `code-set-names.yaml` naming a folder under the corpus root: no row of `code-set-list` shows the folder or the name; a folder outside added and Save pressed: `codeSetFolders` written, and `code-set-names.yaml` the same bytes and the same last-modified time as before (§8, §9) |
| 7 | `controlpanel.TheCodeSetsSectionTest.theSaveWritesCodeSetFoldersAloneAndSaysSo` | Save with the list as the file states it: *nothing was written: every value is as profile.yaml states it*, the file unchanged; a folder removed then Save: *codeSetFolders saved in `<file>`* and the value without it, with `set in the control panel` (§8) |
| 7 | `deliverable.CodeSetsInTheTreeTest.aNamesFileLeftInTheWorkingDirectoryRenamesNothing` | a tree drawn with a `code-set-names.yaml` in the working directory naming one of two sets: both sets in `code/` under their default names, the index and `code.csv` naming those, no warning told and no count of dormant names (§9) |
| 7 | `deliverable.CodeSetsInTheTreeTest.aSetWhoseFolderInCodeChangedIsToldOnce` | a tree whose `code.csv` puts set `core`'s members in `code/billing-core/` and set `kernel`'s in `code/kernel/`, drawn again: `codeSetFolderChanged` called once, with `core`'s set folder, `billing-core` and `core`, and not for `kernel`; drawn a third time: not called (§9) |
| 7 | `deliverable.CodeSetsInTheTreeTest.anEarlierListingAbsentOrUnreadableTellsNothing` | a tree with no `code.csv`, one cut short in a quoted cell, and one whose header names no `copy`: no call of `codeSetFolderChanged`, and every set copied as without it (§9) |
| 7 | `pipeline.TheCodeSetsInTheTreeInvocationTest.aSetWhoseFolderInCodeChangedIsOneLineAtInfo` | an invocation drawing a tree whose earlier `code.csv` puts two of its sets in folders of `code/` other than their default names: two lines at INFO on `GenerationTasklet`'s logger, in the order the sets are drawn, each *Code set `<set folder>` is now in code/`<now>`, no longer in code/`<earlier>`, which is left as it is.*, before the closing line, and no warning; the next invocation drawing the same tree: none (§9) |
| 7 | `deliverable.CodeSetFolderNamesTest.amongDefaultNamesAlikeTheLowestMemberKeepsIt` | sets `core` (lowest member 5), `core` (3), `Core` (2) and `kernel` (9): `Core`, `core~3`, `core~5` and `kernel`, whatever order the sets are handed in (§9) |
| 7 | `pipeline.NoShippedClassNamesTheCodeSetNamesFileTest.noClassOfTheJarOrTheControlPanelNamesIt` | every source of `src/main/java/io/algernon`: none holds `code-set-names`, and the scan read `CodeSetFolderNames`, so that a scan of nothing cannot pass (§9) |
| 8 | `controlpanel.TheCommandOptionsTest.recordMyAnswersIsGivenNoLabelFile` | `ChildCommand.label` for Record my answers: its arguments `label`, `--db-dir=<working directory>` and the presets passed, no other; and `ChildCommand` declaring no `withLabelFile` (§10) |
| 8 | `controlpanel.TheControlPanelsWindowTest.theCommandOptionsOfferNoLabelFile` | the window: no node `label-file` or `label-file.browse`, the section's text *Label file* nowhere, and `command-line-label` reading `vespera label --db-dir=<working directory>` (§10) |

### Tests that change or go

Each existing test this record changes, at the step that does it. Each record whose Tests section names a class or method removed or renamed here is corrected in place by the step that does it, the name followed by *(removed: …, ADR-368)*, or the new name given first and the old after it, *named `<old>` until ADR-368*, as the claims check reads a name that is gone.

| Step | Test | What happens |
| --- | --- | --- |
| 1 | `controlpanel.TheNoteSavedWithAValueTest` | is renamed `controlpanel.TheProvenanceSavedWithAValueTest`, its methods as the Tests above, its calls `provenanceShown` and `provenanceToSave`, its claims unchanged |
| 1 | `controlpanel.TheNoteSavedWithAValueTest.aValueChangedUnderTheProvenanceTheFileStatesIsNotRefused` | is renamed `controlpanel.TheProvenanceSavedWithAValueTest.aValueChangedUnderTheProvenanceTheFileStatesIsNotRefused`, its claims unchanged |
| 1 | `controlpanel.TheWindowSavesTheProfileTest.theProfileTabsSaveWritesEachValueAndNoteAsTheTableSays` | is renamed `controlpanel.TheWindowSavesTheProfileTest.theProfileTabsSaveWritesEachValueAsTheTableSays`; its body changes at step 3 |
| 1 | `controlpanel.TheValuesTheNextInvocationWantsTest` | its calls of `FILLED_IN_NOTE` and `noteShown` renamed; its claims unchanged |
| 3 | `controlpanel.TheProvenanceSavedWithAValueTest.theProvenanceShownIsTheOneTheFileStatesOrTheFilledInOne` | removed with `provenanceShown`, the Tests' row `theProvenanceStatedIsTheFilesWhereNotBlank` in its place |
| 3 | `controlpanel.TheProvenanceSavedWithAValueTest.eachValueIsWrittenAsTheTableSays` | holds §2's two rows, a key the file states nothing for among them, through `provenanceToSave(key, value, inFile)` |
| 3 | `controlpanel.TheProvenanceSavedWithAValueTest.aFloorTheLocalModelSetChangedUnderItsProvenanceBecomesTheOperators` | calls `provenanceToSave` with no provenance; its claims of the value changed unchanged, its case of a note changed by the operator gone |
| 3 | `controlpanel.TheProvenanceSavedWithAValueTest.aValueChangedUnderTheProvenanceTheFileStatesIsNotRefused` | unchanged |
| 3 | `controlpanel.TheWindowSavesTheProfileTest.theProfileTabsSaveWritesEachValueAsTheTableSays` | sets no provenance field, which is gone; each value changed is written with `set in the control panel`, and each left alone not written |
| 3 | `controlpanel.TheWindowSavesTheProfileTest.aSeedFolderOfferedFromTheLastOneIsWrittenBySave`, `controlpanel.TheValuesTheNextInvocationWantsTest.theArrangementTheNextInvocationNamesIsOfferedAsTheApprovalItWants` | unchanged: each value is saved with `set in the control panel` |
| 3 | ADR-364's `controlpanel.EveryTextOfTheWindowCanBeCopiedTest.aWheelOverASelectableTextScrollsTheTabItLiesIn` | opens a profile row's toggle and gives the `ScrollEvent` to its short description, a wrapping `SelectableText` behind it |
| 3 | ADR-364's `controlpanel.EveryTextOfTheWindowCanBeCopiedTest.everyTextShownOutsideAControlIsSelectableText` | opens every profile row's toggle before its walk; it reaches the title, the key and the details' texts, and not the description with *Read from* or the provenance's heading and hint, which are gone |
| 4 | ADR-364's `controlpanel.EveryTextOfTheWindowCanBeCopiedTest.whereSpaceIsTightASelectableTextShrinksAsTheLabelWould` | holds `where`'s "…" visible while it is shrunk, its other claims unchanged |
| 6 | `controlpanel.TheValuesTheNextInvocationWantsTest.aReportTheWorkingDirectoryHoldsIsALinkBesideAWantedValue` | holds the wanted row's link reading the report's title, presses it by its id, and holds its native view's tab opened |
| 6 | ADR-362's `controlpanel.TheAnalysisReportsOpenNativelyTest.theSameAnalysisReportOpenedTwiceSelectsItsOneTab`, and `controlpanel.TheReportsOpenInTheWindowTest.theSameReportOpenedTwiceSelectsItsOneTab` where it still stands | press the row's link by its id, `profile-boilerplateDocumentFrequencyFloor.report-document-frequency`, and not by its text; `WindowForTests` gains `pressRowReportLink(root, prefix, key, name)` |
| 7 | `controlpanel.CodeSetRenamesTest` removed | with `CodeSetRenames` |
| 7 | `deliverable.CodeSetNamesTest` removed | with `CodeSetNames` |
| 7 | `pipeline.TheJarReadsWhatTheControlPanelWritesTest.codeSetNamesReadsTheListTheControlPanelWrites` removed | neither side reads or writes the file |
| 7 | `pipeline.TheJarReadsWhatTheControlPanelWritesTest.theJarReadsEachScalarTheControlPanelReadsToTheSameText` | loses its samples of `code-set-names.yaml`, its samples of `profile.yaml` unchanged; its javadoc names three formats in place of four |
| 7 | `deliverable.CodeSetFolderNamesTest.aTypedNameOutranksADefaultNameAndAmongAlikeTheLowestMemberKeepsIt` | is renamed `deliverable.CodeSetFolderNamesTest.amongDefaultNamesAlikeTheLowestMemberKeepsIt`, the Tests' row above |
| 7 | `deliverable.CodeSetFolderNamesTest.anEntryNamingNoSetOfTheRunIsDormantAndCounted` removed | no entry is dormant |
| 7 | `deliverable.CodeSetFolderNamesTest.aNameIsMadeSafeAndCutAndOneLeftEmptyIsTheLowestMemberOccurrence` | the three names, `...`, `CON` and the name of 120 units, given as the sets' default names; its claims unchanged |
| 7 | `deliverable.CodeSetFolderNamesTest.namesAreToldApartOnceTheyAreMadeSafe`, `deliverable.CodeSetFolderNamesTest.aSuffixedNameThatStillCollidesTakesTheSuffixAgain` | call `of(sets)`; their claims unchanged |
| 7 | `deliverable.CodeSetsInTheTreeTest.theTypedNamesAreAppliedAndTheDormantOnesToldAsACount`, `deliverable.CodeSetsInTheTreeTest.aNamesFileThatIsRefusedLeavesTheDefaultNamesAndIsToldOnce`, removed | no name is applied or refused, the Tests' row `aNamesFileLeftInTheWorkingDirectoryRenamesNothing` holding the file read by nothing |
| 7 | `deliverable.CodeSetsInTheTreeTest.theIndexListsEachSetAfterTheGroupsAndNeverCallsItAGroup` | writes no names file, and holds the index naming each set by its default name |
| 7 | `pipeline.TheClosingLineCountsTheFilesOfCodeNotCopiedTest.countsTheDormantNamesBeforeWhatAnEarlierDrawingLeft` removed | with the clause; the class's helper loses its count of dormant names |
| 7 | `pipeline.CodeSetTaskletTest.aRenameIsNotReadByStageOne` removed | no class reads the file, which the Tests' row `noClassOfTheJarOrTheControlPanelNamesIt` holds |
| 7 | `controlpanel.WorkingDirectoryWriteTest.onlyTheFourFilesAreWritten` | is renamed `controlpanel.WorkingDirectoryWriteTest.onlyTheThreeFilesAreWritten`: `profile.yaml`, `relevance-labels.yaml` and `not-relevant-folders.yaml`, and `code-set-names.yaml` refused as any other file |
| 7 | `controlpanel.WorkingDirectoryWriteTest.aWorkingDirectoryThatDoesNotExistIsRefusedForTheOtherTwoFiles` | is renamed `controlpanel.WorkingDirectoryWriteTest.aWorkingDirectoryThatDoesNotExistIsRefusedForTheLabelFile`, `relevance-labels.yaml` alone in its loop |
| 7 | `PipelineHoldsOnlyTheRulesOnRecordTest` | `deliverable.CodeSetNames` leaves the collaborators it lists for `GenerationTasklet` |
| 7 | ADR-364's `controlpanel.EveryTextOfTheWindowCanBeCopiedTest.everyTextShownOutsideAControlIsSelectableText` | its working directory holds no code set renamed, a file nothing reads |
| 8 | `controlpanel.TheCommandOptionsTest.theLabelFileIsPassedToLabelAndNeverToLabelAuto` removed | with `withLabelFile`, the Tests' row `recordMyAnswersIsGivenNoLabelFile` in its place |
| 8 | `pipeline.TheControlPanelAccountsForEveryArgumentOfTheJarsCommandsTest.eachArgumentIsSetByAControlOfTheControlPanelOrNeverPassedAndSaysWhy` | `label <file>` among the never passed, which are four, and no longer set by `label-file` |
| 8 | `controlpanel.ControlPanelStateTest` | its helper `passing` builds a `Plan` with no label file |
| 8 | `controlpanel.TheControlPanelsWindowTest.everyControlTheArgumentsTableNamesIsAControlOfTheWindow` | unchanged: the table names no `label-file` |

## What the implementation owes

Beside turning the tests above green, step by step:

- at step 2, `profile-texts.json` written from §4's table and from the tooltips settled on #764, each without the opening that names its key;
- the stylesheet's `more`, `tags` and `tag`, the toggle drawn as a small flat button, a tag as a rounded box of the window's own colours and its *×* as a small flat button, every button else keeping the one style (ADR-342 §3), and a tooltip wrapping at a width the stylesheet gives it;
- at step 3, the README's paragraph on provenance and its paragraph on the Profile tab, saying a row is one line with its details behind ▸, its provenance in its title's tooltip, and a value changed in the window saved with `set in the control panel`, a provenance of one's own being written in `profile.yaml`;
- at step 5, the README's lines on a list value, saying an item is added with Enter and removed by its *×*;
- at step 6, the README's sentence on a key's reports, which read their titles;
- at step 7, the README's row of `code-set-names.yaml` in *Where things live* removed, its sentence on adding a code set's folder saying a folder outside the corpus root alone is added, its paragraph on code in the tree saying a set is named for its folder, telling in one line a set whose folder changed, and dropping the clause of dormant names, and one sentence saying a `code-set-names.yaml` left in a working directory is no longer read and can be deleted by hand; `CONTEXT.md`'s Code set entry, whose set is renamed in `code-set-names.yaml` and named by its typed or default name, and its Working directory entry, which holds the code-set names; `docs/architecture.md`'s paragraph on the control panel, whose four files and `WorkingDirectoryWriteTest.onlyTheFourFilesAreWritten` become three and `onlyTheThreeFilesAreWritten`, rendered again;
- at step 8, the README's paragraph on Command options, which offers no label file, and its line that a label file kept elsewhere is recorded with `vespera label <file>`;
- this record's number taken out of `docs/check-claims.mjs`'s `NOT_YET_BUILT` by the step that builds its last row.

## What this does not decide

- **The words of the analysis reports' titles.** A row's link reads each report's `title` as the jar writes it; a title rewritten for the operator is changed in the report's writer and in `Reports.TITLES` together, which §5's test holds to one text.
- **The words of the tooltips beyond §4's rules**: they are the operator's, changed in `profile-texts.json`.
- **The launch fields and the folder not relevant**: they stay as they are, made copyable by ADR-364.
- **The Reports section's links**, which ADR-362 §10 draws.
- **How long a tooltip waits before it shows**: JavaFX's own.
- **A name for a code set by any other means**, should the operator want one again: a record of its own.
