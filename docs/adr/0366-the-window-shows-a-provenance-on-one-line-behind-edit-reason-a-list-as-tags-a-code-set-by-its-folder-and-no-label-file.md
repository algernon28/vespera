# ADR-366 — The window shows a provenance on one line behind Edit reason, a list as tags, a code set by its folder, and no label file

- **Date**: 2026-11-07
- **Status**: accepted on 2026-11-07.
- **Decided**: by the operator, on [#764](https://github.com/algernon28/vespera/issues/764): that a value's provenance is shown with no caption, on one line cut with "…", as text that is selected and copied and not edited, its tooltip holding its whole text and what it is, saved beside the value in `profile.yaml`; that **Edit reason** opens it whole in an editable box; that a changed value saved with no reason is saved with `set in the control panel`, as ADR-343 has it; and that the hint beneath it goes (§1, §2); that the code sets' shared provenance follows the same rule (§5); that a profile row shows its title, its control, its cost line, its reports and its provenance, the title's tooltip holding the key and the description (§3); that a tooltip's text is help and is not copied, ADR-364's copy rule covering visible text alone (§1, §3); that a list value shows each item as a removable tag, with one box where Enter adds an item, the free-text list box and the `+` box gone (§4); that code sets are no longer renamed: the name field, `CodeSetRenames` and its file go, a code set is always named by its folder, and renames already saved are no longer read (§5, §6); that the label file goes from Command options, relevance being answered in the window by folder in the Inclusions tab (ADR-334 §6), and the command line keeping `vespera label <file>` (§7); that the launch fields and the folder not relevant stay as they are; that the Profile tab is to be more compact; and that `CONTEXT.md`'s Profile entry avoids "note" for a value's provenance (§8). Claude's readings of those decisions: the line of the value in use, which the list of what a row shows does not name, stays where the jar names one, since its going would reopen ADR-330 §1 (§3); ADR-364's line, which draws no ellipsis, is given one drawn over its end, which is no part of its text (§1); a key whose provenance the file does not state shows Edit reason alone (§1); and the names of ours that call a provenance a note are renamed (§8). Claude decided the rest, as details of the build: that Edit reason is a link and its box takes the line's place (§1); how the box takes Enter, Escape and a pasted line break (§1); how a tag is removed by the keyboard and what is still typed in the box at a Save (§4); that a folder under the corpus root is refused in the Code sets section (§5); that a `code-set-names.yaml` left in a working directory is read, written and deleted by nothing (§6); the tooltips' words (§1, §3, §4); the ids; the steps of the build; and the tests.
- **Amends**: [ADR-343](0343-the-note-saved-with-a-profile-value-is-filled-in-for-the-operator-set-in-the-control-panel-or-the-provenance-already-saved-and-a-value-changed-under-its-old-note-is-saved-with-the-filled-in-one.md) §1, whose field captioned *Note saved with it*, filled in, with a hint beneath it, stands beside each value: the provenance is a line with no caption and Edit reason opens a box filled in as the field was, the box being what §2's table reads (§1, §2); and §3, whose `FILLED_IN_NOTE`, `noteShown` and `noteToSave` are renamed, and the names of its Tests with them (§8). Its §2's table stands, a box never opened being left as shown. It carries a note at its head.
- **Amends**: [ADR-344](0344-the-control-panels-window-is-one-cockpit-run-and-stop-the-steps-as-bars-beside-the-output-the-progress-pages-three-charts-the-profile-and-the-folders-to-include-starting-from-the-real-configuration.md) §1, whose Profile tab's row shows the key in small type and a plain description beneath the name, a list built with `+` and the value's note: the row is §3's, a list value §4's tags, and `wanted`'s and Seed folder's values show their provenance by §1; and whose Code sets section gives a folder under the corpus root an optional name: it lists the folders outside the corpus root alone, with their one provenance (§5); §3, whose code sets' names start from `code-set-names.yaml`: nothing reads it (§6); and §4, whose Command options offer `label-file`: they offer the five presets alone, `ChildCommand.withLabelFile` goes and `label <file>` is never passed (§7). It carries a note at its head.
- **Amends**: [ADR-284](0284-code-is-kept-in-code-sets-each-below-the-nearest-folder-under-the-corpus-root-holding-a-build-file.md) §9, whose `code-set-names.yaml` renames a set and `CodeSetNames` reads it, and §10's dormant entries: a set is named by its folder alone, the file is read by nothing and written by nothing, and `CodeSetNames` goes (§6). It carries a note at its head.
- **Amends**: [ADR-295](0295-the-control-panel-writes-profile-values-label-answers-and-code-set-names-holding-the-working-directory-lock-one-operator-action-at-a-time.md) §2, whose control panel writes `code-set-names.yaml`, §7, its code-set names, and §9's `CodeSetRenames`: the file is not among those it writes, and §7 and the class go (§5, §6). It carries a note at its head.
- **Amends**: [ADR-296](0296-each-code-set-is-copied-whole-under-code-its-paths-kept-and-each-file-checked-against-the-size-and-time-the-walk-recorded.md) §3.1, whose set's folder in `code/` starts from its typed name and ranks a typed name first: it starts from the default name, the lowest member occurrence id the one key that tells two alike apart; and §6.2, whose names file adds a clause of dormant names to the closing line and a warning of a refused file: neither is written (§6). It carries a note at its head.
- **Amends**: [ADR-334](0334-the-default-output-folder-named-is-none-an-empty-field-shows-the-value-in-use-relevance-is-answered-by-folders-not-relevant-the-profile-is-one-form-and-a-code-set-folder-may-lie-outside-the-corpus-root.md) §4, whose table puts a rename of a set in `code-set-names.yaml`, and §5.5, whose names file spells an outside set's folder absolute: no file holds a rename (§6); and §6, whose folders not relevant are, in the window, how relevance is answered: they are the only way, Command options offering no label file, and `vespera label <file>` stays the command line's (§7). It carries a note at its head.
- **Amends**: [ADR-331](0331-the-control-panel-opens-a-window-of-its-own-and-its-pages-are-a-cockpit-with-a-picker-an-example-and-a-hint-on-its-fields.md) §8, whose table gives a hint to `new-name` and `name-<i>` and to `provenance` and `<key>.provenance`, and whose profile key's hint is drawn beside its field: a set takes no name, a provenance is §1's line with no hint, and a key's hint stands in its title's tooltip (§1, §3, §5). It carries a note at its head.
- **Amends**: [ADR-336](0336-the-launch-page-starts-label-auto-beside-label-and-every-argument-of-the-commands-the-control-panel-starts-is-set-by-a-control-of-the-launch-page-or-listed-as-never-passed-with-why.md) §2 and §3, whose `label <file>` is set by `label-file` since ADR-344: it is never passed (§7). It carries a note at its head.
- **Amends**: [ADR-342](0342-the-control-panel-is-a-javafx-window-inside-the-vespera-jar-opened-by-the-jar-started-with-no-argument-and-its-loopback-server-and-pages-are-gone.md) §4, whose row of ADR-295's writes names `CodeSetRenames`: it goes (§6). It carries a note at its head.
- **Amends**: [ADR-345](0345-the-cockpit-replaces-the-browser-control-panel-its-tree-writes-the-folders-not-walked-and-publish-offers-each-destination.md) §4, whose entries never passed are three: they are four, `label <file>` among them (§7). It carries a note at its head.
- **Amends**: ADR-364 §8, whose texts of a profile row are its title, key, description, value in use, cost, and the heading and hint of its provenance's field, and whose shrunk line draws no ellipsis: a row's texts are §3's, and the provenance's line draws one over its end (§1, §3).
- **Read with**: [ADR-197](0197-a-local-model-answers-the-relevance-questions-and-the-floor-is-a-rule-over-the-labels-so-no-document-reaches-a-hosted-model.md) §6, whose `vespera label [<file>]` and refusal of `--auto` with a file stand on the command line; the control panel passes none (§7). It carries a note at its head.
- **Keeps**: [ADR-061](0061-the-profile-is-yaml-typed-java-records-one-object-per-value.md): every value carries its provenance, free text, under its key. [ADR-330](0330-the-control-panel-edits-every-value-of-profile-yaml-showing-the-value-in-use-and-where-it-came-from-and-ten-technical-choices-ship-as-presets-in-application-yaml.md) §1 and §5: the value in use is shown where the jar names one, and a save that changes nothing writes nothing. [ADR-295](0295-the-control-panel-writes-profile-values-label-answers-and-code-set-names-holding-the-working-directory-lock-one-operator-action-at-a-time.md) §4: every write refuses a blank provenance and the auto rule's words for the floor. [ADR-334](0334-the-default-output-folder-named-is-none-an-empty-field-shows-the-value-in-use-relevance-is-answered-by-folders-not-relevant-the-profile-is-one-form-and-a-code-set-folder-may-lie-outside-the-corpus-root.md) §5.1: a folder `codeSetFolders` names lies outside the corpus root. [ADR-296](0296-each-code-set-is-copied-whole-under-code-its-paths-kept-and-each-file-checked-against-the-size-and-time-the-walk-recorded.md) §6.3: a folder in `code/` the drawing did not write is counted as left behind. [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md) §5: the tree keys no run.
- **Settles**: [#764](https://github.com/algernon28/vespera/issues/764).

## Context

**A profile row today** (`ProfileRow`, ADR-344 §1) draws, top to bottom: the key's descriptive name, `key-name`; the key in small type; a description, `ProfileKey.about()` and *Read from* `ProfileKey.hint()`, wrapping; the value's control; the line *In use where the profile names none: …*, where `effective-values.properties` names a value beneath the profile (ADR-330 §1); the cost line, *A change makes again: …*; the reports, *Reports:* and a link or a grey name for each; then a row of the caption *Note saved with it* and a `TextField`, `<prefix><key>.provenance`, filled by `ProfileEdit.noteShown` with the provenance the file states or `set in the control panel` (ADR-343 §1); and beneath it the hint *Saved beside the value in profile.yaml so that whoever reads it later knows where the value came from. You may write how you chose the value instead.*, wrapping. The same row stands in the Profile tab (`profile-`), in `wanted` (`wanted-`) and in Seed folder (`seed-`).

**A list value** (`codeNameEndings`, `outOfScopeNameEndings`, `preferredLanguages`) is drawn by `listControl()` as a `TextField` holding the whole list, `<prefix><key>`, a second `TextField`, `<prefix><key>.add`, and a button `+`, `<prefix><key>.plus`, which appends the second field's text to the first. The value is the first field's text; `ProfileEdit` splits it on white space and commas (`PIECES`).

**The Code sets section** (`Cockpit.codeSetsPane()`) holds a list of entries, a folder field `code-set-folder`, a name field `code-set-name` (*A name for the deliverable (optional)*), Browse and `+`, a field `code-set-note` captioned *Note saved with the folders outside*, Save and an outcome line. A folder under the corpus root becomes an entry with the name typed, and its Save writes the entries named into `code-set-names.yaml` through `CodeSetRenames.render` (ADR-295 §7); an entry under the root given no name writes nothing. A folder outside the root goes through `CodeSetFolderChecks.refusal` and into `codeSetFolders` (ADR-334 §5), saved with the note by ADR-343 §2. Each load reads the file back through `CodeSetRenames.read`.

**The jar's side of the renames.** `deliverable.CodeSetNames` reads `code-set-names.yaml` (ADR-284 §9); `Deliverable` hands its entries to `CodeCopies`, where `CodeSetFolderNames.of(sets, typed)` starts each set's folder in `code/` from its typed name or its default name, a typed name ranking first, and `CodeSetFolderNames.dormant` counts the entries naming no set of the run (ADR-296 §3.1, §6.2). `DeliverableProgress.dormantCodeSetNames` and `codeSetNamesRefused` tell `GenerationTasklet`, which records the count through `InvocationRuns` and logs a warning for a refused file; `NextAction` adds *"; D names in code-set-names.yaml name no code set of this run"* to the closing line. No stage reads the file, and no stage's version names `deliverable` (`StageModules`).

**The label file option.** Command options holds `label-file`, *The label file for Record my answers (optional)*, with Browse; the window's plan carries it as `ControlPanelState.Plan.labelFile`, and `ChildCommand.withLabelFile` puts it, absolute, as `label`'s `<file>` (ADR-344 §4), never for `label --auto`, which refuses one (ADR-197 §6). With none, `vespera label` records the answers of the working directory's `relevance-labels.yaml`, which the Inclusions tab answers from the folders not relevant (ADR-334 §6, ADR-345). `ChildCommand.ARGUMENTS` names `label-file` as the control of `label <file>`.

**ADR-364 §8**, accepted and not yet built, makes every text of the window outside a control a `SelectableText`, a `SelectableText.Line` for a text on one line and a `SelectableText.Wrapping` for one that wraps, not editable and copied by selecting it; a tooltip's text is outside that rule, and a shrunk `Line` shows its text cut at its edge with no ellipsis.

## Decision

### 1. A value's provenance is one line, and Edit reason opens it

**Beside each value, where the file states a provenance for its key that is not blank, stands that provenance on one line, with no caption**: `<prefix><key>.provenance`, a `SelectableText.Line` holding the whole provenance as the file states it, in the style class `provenance`, small. It takes the width its row leaves; where its text is wider, **"…" is drawn over its end**, a `javafx.scene.text.Text` in its font laid over the line's right edge, taking no mouse event and no part of its text, so that the line reads as a label cut with an ellipsis does, while its whole text is still selected, by dragging or Select All, and copied. Its caret goes back to 0 as ADR-364 §8 has a `Line` do.

**Its tooltip** holds the whole provenance, then an empty line, then *Saved beside the value in profile.yaml as its provenance, saying where the value came from.* A tooltip's text is help and is not copied (ADR-364 §8).

**At the line's end, in the same row, stands Edit reason**, `<prefix><key>.edit-reason`, a `Hyperlink` reading *Edit reason*, its tooltip *Change the reason saved beside the value in profile.yaml; it is written when you press Save.* Where the file states no provenance for the key, or a blank one, there is no line and **Edit reason stands alone**.

**Pressing Edit reason puts the box in the line's place**: the line and the link are hidden and take no room, and `<prefix><key>.provenance-box`, a `TextArea`, editable, wrapping its text, two lines high at least, in the style class `provenance-box`, stands where they stood, takes the focus and puts its caret at its end. It holds the provenance shown, `ProfileEdit.provenanceShown` (§8): the file's, or `set in the control panel` where the file states none, as ADR-343 §1 fills it in. Its text is selected and copied as any editable text is.

- **A provenance is one line of text**: Enter in the box adds no line break, and a line break or a tab pasted or typed into it is written there as one space, by a `TextFormatter` on the box. Tab moves the focus on.
- **Escape** puts the box's text back to what it held when it was opened, hides it, and shows the line and the link again.
- Otherwise **the box stays open until the row is loaded again**: by a Save that writes, by Reset, or by another working directory in the field. A Save that is refused leaves it open with its text.

**The box is the field ADR-343 §2's table reads.** Each load fills it with the provenance shown, whether or not it is ever opened, so a box never opened is left as shown.

The caption *Note saved with it* goes, and so does the hint beneath it.

### 2. What a save writes

ADR-343 §2's table stands, *the note* being the box's text (§1). So:

- **a value changed with no reason edited**, its box never opened or left as shown, is saved with **`set in the control panel`**;
- a box emptied is saved as `set in the control panel`, and a reason edited is saved as written, its white space around removed, beside a value changed or not;
- a key whose value and box are both as they were is not written, and a Save of a form left alone writes nothing (ADR-330 §5).

`ProfileEdit.refusalOf`'s refusals stand (ADR-295 §4).

### 3. A profile row

**A row shows, top to bottom: its title; its control; the line of the value in use, where the jar names one; its cost line; its reports; and its provenance and Edit reason (§1).**

- **The title**, `<prefix><key>.title`, the key's descriptive name in the style class `key-name`, carries a tooltip holding **the key**, then an empty line, then **its description**, `ProfileKey.about()`, *Read from* and `ProfileKey.hint()`, as the row drew it. The key in small type and the description no longer stand in the row.
- **The line of the value in use**, *In use where the profile names none: …*, stays where `effective-values.properties` names one, as ADR-330 §1 has it; a row the jar names nothing beneath shows none.
- **The cost line** and **the reports** are as they are (ADR-330 §9, ADR-344 §6).

Each text of a row is a `SelectableText` (ADR-364 §8); the tooltips are not. The rows of `wanted` and Seed folder are drawn so too, by the same class. A key edited in a section of the sidebar or in the Inclusions tab keeps its read-only value and its link to that place, *Edit it in its section* or *Edit it in the Inclusions tab* (ADR-344 §1, ADR-345).

### 4. A list value is tags

**`codeNameEndings`, `outOfScopeNameEndings` and `preferredLanguages` each show their items as tags**, in `<prefix><key>`, a `FlowPane` in the style class `tags`, in the order the file lists them, and after the last **one box**, `<prefix><key>.add`, a `TextField` whose prompt is *e.g. `<first piece of the key's example>`, then Enter*: *e.g. .java, then Enter*, *e.g. .ok, then Enter* and *e.g. it, then Enter* for the three.

- **A tag**, `<prefix><key>.tag-<i>`, `<i>` from 1 in order, an `HBox` in the style class `tag`, holds its item as a `SelectableText.Line` and **its remove button**, `<prefix><key>.remove-<i>`, a `Button` reading *×*, its tooltip *Remove `<item>`*.
- **Enter in the box adds what it holds**: its text split by `ProfileEdit`'s pieces, white space and commas, each piece not already a tag, compared exactly, added as a tag after the last in the order typed; the box is emptied. Enter in an empty box adds nothing.
- **A tag is removed by its button**, pressed with the mouse, or from the keyboard: each remove button is reached by Tab, in the tags' order before the box, and Space or Enter presses it. **Backspace in the box, while it is empty, removes the last tag.** After a removal the box has the focus.
- **The value** is the tags' items joined by one space, then the pieces still typed in the box and not added, so that a Save loses nothing typed. A load draws a tag for each piece the file's value holds, by the same split, and empties the box.

The list field, `<prefix><key>` as a `TextField`, and the button `+`, `<prefix><key>.plus`, go. What a list may hold is checked at a Save by `ProfileEdit`, as it is (ADR-295 §4): an ending without its dot, or a language code of three letters, is refused there with its line.

### 5. The Code sets section

**It lists the folders `codeSetFolders` states, outside the corpus root, and nothing else**: `code-set-list`, a row for each, its whole path and **Remove**. Beneath it, `code-set-folder`, Browse and `+`, `code-set-add`, add a folder, through `CodeSetFolderChecks.refusal` as a folder outside the root goes today. **A folder under the corpus root is refused by the same check**, with its line *the folder `<folder>` is not added: it is inside the corpus root `<root>`*: stage 1 finds the code sets under the corpus root by their build files (ADR-284 §2), and nothing names them.

The name field `code-set-name` goes, and so do the entries under the corpus root and the words *named* and *(outside the corpus root)* of a row.

**Its one provenance, `codeSetFolders`', follows §1**: `code-set-provenance`, `code-set-edit-reason` and `code-set-provenance-box`, read and saved as §1 and §2 have it, the line's tooltip reading *Saved beside codeSetFolders in profile.yaml as its provenance, saying where the folders outside the corpus root came from.* The caption *Note saved with the folders outside* goes.

**Its Save**, `code-sets-save`, writes `codeSetFolders` by ADR-343 §2 and nothing else, through `ProfileRow.save`'s checks and words: *nothing was written: every value is as profile.yaml states it* where it changed nothing, and *codeSetFolders saved in `<file>`* where it wrote.

### 6. A code set is named by its folder

**No class of the jar or the control panel reads, writes or names `code-set-names.yaml`.**

- **In the control panel**, `CodeSetRenames` goes, and the file leaves `WorkingDirectoryWrite.FILES`, which holds `profile.yaml`, `relevance-labels.yaml` and `not-relevant-folders.yaml`; a write naming it is refused as any file not among them is.
- **In the jar**, `deliverable.CodeSetNames` goes. `CodeSetFolderNames.of(sets)` starts each set's folder in `code/` from **its default name**, its folder's last element (ADR-284 §9, ADR-334 §5.5), made safe and cut as ADR-296 §3.1 has it, and tells two alike apart by the lowest member occurrence id alone: the lower keeps the name, each other takes `~` and its own. `CodeSetFolderNames.dormant`, `DeliverableProgress.dormantCodeSetNames` and `codeSetNamesRefused`, `GenerationTasklet`'s count and warning, `InvocationRuns`' record of the count and `NextAction`'s clause of dormant names go.

**A `code-set-names.yaml` left in a working directory stays as it is**: nothing reads it, writes it, moves it or deletes it, and nothing says it is there. It is the operator's file, and it changes nothing. The README says it is no longer read and can be deleted by hand.

**A tree drawn again** over a run with a set the file renamed gives that set its default name. Its folder of the earlier name in `code/` is one this drawing did not write, counted on the closing line as left behind by ADR-296 §6.3, and `code.csv` and the index name the set by its new folder.

### 7. The label file option goes

**Command options offers the five presets of ADR-344 §4 and nothing else.** `label-file`, its Browse `label-file.browse` and its heading *Label file* go; **Back to the defaults** puts the presets back. `ControlPanelState.Plan` loses `labelFile`, `ChildCommand.withLabelFile` goes, and **Record my answers** starts `vespera label --db-dir=<working directory>` with the presets passed, whose label file is the working directory's `relevance-labels.yaml`, answered in the Inclusions tab by folder (ADR-334 §6). `command-line-label` shows that line.

**`ChildCommand.ARGUMENTS`** names no control for `label <file>`: it is never passed, its reason *Never passed: the window answers the questions in the Inclusions tab, by folder (ADR-334 §6), in the working directory's label file, which label reads where no file is named; the command line keeps it (ADR-366).* The entries never passed are four: `vespera help`, `vespera version`, `serve db-dir` and `label <file>`.

**The command line keeps `vespera label <file>`** (ADR-197 §6): a file named there is read, and `label --auto` with one is refused, as they are.

### 8. A provenance is not a note, in our names

`CONTEXT.md`'s Profile entry avoids "note" for a value's provenance, so the names of ours that call it one are renamed, behaviour unchanged:

| Was | Is |
| --- | --- |
| `ProfileEdit.FILLED_IN_NOTE` | `ProfileEdit.FILLED_IN_PROVENANCE` |
| `ProfileEdit.noteShown(Optional<Stated>)` | `ProfileEdit.provenanceShown(Optional<Stated>)` |
| `ProfileEdit.noteToSave(ProfileKey, String, String, Optional<Stated>)` | `ProfileEdit.provenanceToSave(ProfileKey, String, String, Optional<Stated>)` |
| `ProfileRow.note`, `ProfileRow.noteToSave()` | `ProfileRow.provenance`, `ProfileRow.provenanceToSave()` |
| `Cockpit.codeSetNote`, id `code-set-note` | `Cockpit.codeSetProvenance`, id `code-set-provenance` |
| `controlpanel.TheNoteSavedWithAValueTest` and its methods | `controlpanel.TheProvenanceSavedWithAValueTest`, the Tests below |

`Adr.THE_NOTE_SAVED_WITH_A_PROFILE_VALUE_IS_FILLED_IN` keeps its name, which spells ADR-343's title. Text written for a reader outside this project, a `@Story` or a `@DisplayName` among it, is free of the lists (ADR-122).

### 9. The modules, and run ids

**No run id moves.** The classes changed are of the control panel, `ProfileRow`, `Cockpit`, `ProfileEdit`, `ControlPanelState`, `ChildCommand` and `WorkingDirectoryWrite`, `CodeSetRenames` removed, and the stylesheet; of `deliverable`, `CodeSetFolderNames`, `CodeCopies`, `Deliverable` and `DeliverableProgress`, `CodeSetNames` removed; and of `pipeline`, `GenerationTasklet`, `InvocationRuns` and `NextAction`. No stage's version names `deliverable` or `pipeline` (ADR-222, ADR-274 §5, `StageModules`), and none of them decides a verdict, a cache key or a cluster; the names of `code/` are the drawing's, which keys no run. No class is added to `pipeline`.

### 10. The build, in steps that each land green

1. **The names** (§8): the renames, the tests renamed with them, and the Tests rows of ADR-343 and ADR-344 naming them corrected in place. It needs nothing of ADR-364's.
2. **The row and its provenance** (§1, §2, §3, §5's provenance): the line, Edit reason and the box, the caption and the hint gone, the title's tooltip, in `ProfileRow` and in the Code sets section. It lands after ADR-364's step 5, which builds `SelectableText`.
3. **The tags** (§4). It lands after ADR-364's step 5.
4. **Code sets named by their folders** (§5's list, §6): the control panel's and the jar's parts together, so that no step leaves a file one side writes and the other no longer reads; with the README's and `CONTEXT.md`'s lines on the file in the same change, since the claims check reads the README's files against the jar's `FILE_NAME` constants. It needs nothing of ADR-364's.
5. **The label file option** (§7). It needs nothing of ADR-364's.

## Consequences

- **A Profile row is its control and four lines, where it was its control and seven at least**: its title, its cost, its reports and one line of provenance, with the value in use where the jar names one. The key and the description are a pointer's rest away, on the title.
- **The operator sees at a glance what each value was saved with**, and changes it only by asking to; a value changed and saved without asking is saved with `set in the control panel`, as before.
- **A key the profile states nothing for shows Edit reason alone**, and no words it was never saved with.
- **A list reads as its items**, each removed by itself, and is added to with Enter.
- **The Code sets section is the folders outside the corpus root**, the one thing the operator sets there; the code sets under the root are stage 1's to find and their folders' to name.
- **A tree drawn again renames in `code/` a set the operator had renamed**, to its folder's name, and counts the old folder as left behind; a working directory's `code-set-names.yaml` stays and changes nothing.
- **Record my answers records the working directory's label file alone**; a label file kept elsewhere is recorded from the command line, `vespera label <file>`.
- **The closing line loses its clause of dormant names**, and the generation step its warning of a refused names file.

## Known limits

- **The line shows the provenance the file holds until a Save**: a value changed with no reason edited still shows the old provenance beside it, and is saved with `set in the control panel`, which the line shows once the Save has written.
- **A tooltip is not copied** (ADR-364 §8): the whole provenance is copied from its line, by Select All, or from its box.
- **A list item holding a space or a comma cannot be a tag**: the pieces split it, as `ProfileEdit` reads a list today.
- **A set the operator had renamed takes its folder's name**, and nothing in the window or the closing line says why its folder in `code/` changed but the count of folders left behind.
- **Two code sets whose folders end alike** are told apart by `~` and an occurrence id alone, which the operator can no longer replace with a name.

## Alternatives refused

- **Edit reason as a button.** A button on every row reads as an action that writes, beside the one Save that does; a link reads as opening something in place.
- **The box beside the line, or beneath it, rather than in its place.** It would add a line to each row opened, against the compact tab the operator asked for.
- **The provenance written by Vespera itself, with who, when and from which tab.** ADR-343 refused a note dated or naming the user, which would make every save a change of each provenance; the operator kept the filled-in words.
- **A line that shows `set in the control panel` for a key the file states nothing for.** It would show on most rows words no file holds.
- **A tag removed by a click on its text.** A click on the text selects it, to be copied.
- **The jar still reading `code-set-names.yaml` while the window stops writing it.** A set would carry a name nobody could change from the window, and the drawing two sources of a set's name.
- **The file deleted, or a warning on each invocation while it is there.** The file is the operator's, and a line repeated for a file that changes nothing is noise; the README says it once.
- **A folder under the corpus root kept as an entry with no name.** It writes nothing and changes nothing, so the section would list folders that do nothing.
- **The label file kept as a field, or moved to the Inclusions tab.** The operator answers in the window by folder, and a file kept elsewhere is the command line's.

## Tests

Owed by the build. The first column is the step of §10 that adds the test, and its green is that step's: no row lands before the code it holds. Each window test draws the window through `WindowForTests` on the Headless platform (ADR-342 §6).

| Step | Method | What it holds |
| --- | --- | --- |
| 1 | `controlpanel.TheProvenanceIsNamedSoTest.noNameOfTheControlPanelCallsAProvenanceANote` | the sources of `src/main/java/io/algernon/controlpanel`, their comments taken out: no identifier and no string naming an id holds the word note, alone or as a part of a name, as `note`, `Note…`, `…Note` or `…_NOTE`, and the scan reads `ProfileEdit`'s `provenanceShown`, so that a scan of nothing cannot pass (§8) |
| 1 | `controlpanel.TheProvenanceSavedWithAValueTest.theProvenanceShownIsTheOneTheFileStatesOrTheFilledInOne` | ADR-343's rows of it, under the new names (§8) |
| 1 | `controlpanel.TheProvenanceSavedWithAValueTest.eachValueAndProvenanceIsWrittenAsTheTableSays` | ADR-343 §2's table, row by row, under the new names (§2, §8) |
| 1 | `controlpanel.TheProvenanceSavedWithAValueTest.aFloorTheLocalModelSetChangedUnderItsProvenanceBecomesTheOperators` | ADR-343 §4, under the new names (§8) |
| 2 | `controlpanel.AValuesProvenanceIsOneLineTest.theProvenanceIsOneLineOfSelectableTextWithNoCaptionAndNoHint` | a profile stating a provenance for `boilerplateDocumentFrequencyFloor`: `profile-boilerplateDocumentFrequencyFloor.provenance` is a `SelectableText.Line`, not editable, holding that provenance whole; the row holds no text *Note saved with it* and none beginning *Saved beside the value*; and `profile-boilerplateDocumentFrequencyFloor.edit-reason` is a `Hyperlink` reading *Edit reason*, in the line's row, after it (§1) |
| 2 | `controlpanel.AValuesProvenanceIsOneLineTest.aLongProvenanceIsCutWithAnEllipsisAndCopiedWhole` | a provenance of 300 characters at the window's 1500 by 960: the line no wider than its row less Edit reason, its "…" visible over its end; Select All then Copy giving the 300 characters; and a provenance of ten characters with no "…" visible (§1) |
| 2 | `controlpanel.AValuesProvenanceIsOneLineTest.itsTooltipHoldsTheWholeProvenanceAndWhatItIs` | the line's tooltip: the whole provenance, an empty line, then *Saved beside the value in profile.yaml as its provenance, saying where the value came from.* (§1) |
| 2 | `controlpanel.AValuesProvenanceIsOneLineTest.aKeyTheFileStatesNoProvenanceForShowsEditReasonAlone` | a key absent from the file and one with a blank provenance: no line, or none visible and managed, and Edit reason in the row (§1) |
| 2 | `controlpanel.AValuesProvenanceIsOneLineTest.theCodeSetsShareOneProvenanceLineByTheSameRule` | `codeSetFolders` stated with a provenance: `code-set-provenance` a `SelectableText.Line` holding it, its tooltip §5's, `code-set-edit-reason` beside it, no text *Note saved with the folders outside*, and no node `code-set-note` (§5) |
| 2 | `controlpanel.EditReasonOpensTheProvenanceTest.editReasonOpensTheWholeProvenanceInAnEditableBoxInTheLinesPlace` | Edit reason pressed: the line and the link not visible and not managed, `…provenance-box` a `TextArea`, editable, wrapping, visible, holding the whole provenance with the focus and its caret at its end, and laid out where the line stood, its top within a pixel of the line's; for a key the file states none for, the box holding `set in the control panel` (§1) |
| 2 | `controlpanel.EditReasonOpensTheProvenanceTest.enterAddsNoLineBreakAndAPastedLineBreakIsASpace` | Enter typed in the box, then `a\nb\tc` pasted: the box's text holds no line break and no tab, the pasted text reading `a b c` (§1) |
| 2 | `controlpanel.EditReasonOpensTheProvenanceTest.escapePutsTheBoxBackAndShowsTheLineAgain` | the box's text changed, then Escape: the box hidden, the line and the link shown, the line's text the file's; and a Save then writing nothing for the key (§1) |
| 2 | `controlpanel.EditReasonOpensTheProvenanceTest.aValueChangedWithNoReasonEditedIsSavedAsSetInTheControlPanel` | a value stated with a provenance the operator wrote, changed in its control, Edit reason never pressed, and Save: `profile.yaml` holds the new value with `set in the control panel`; and once written the row's line shows `set in the control panel` (§2) |
| 2 | `controlpanel.EditReasonOpensTheProvenanceTest.aReasonEditedIsSavedWithTheValue` | Edit reason pressed and `  read off the format mix  ` typed, the value changed, then Save: the value with `read off the format mix`; and a reason edited beside a value left as it was, saved with its value as the file states it (§2) |
| 2 | `controlpanel.EditReasonOpensTheProvenanceTest.aRefusedSaveLeavesTheBoxOpenWithItsText` | a box opened and changed beside a value `ProfileEdit` refuses, then Save: the outcome the refusal's line, the box still visible with its text (§1) |
| 2 | `controlpanel.TheProfileRowIsCompactTest.aRowShowsItsTitleControlCostReportsAndProvenanceAlone` | the rows of `boilerplateDocumentFrequencyFloor`, a key with no value beneath it, and of a key `effective-values.properties` names: walked as ADR-364 §8 walks the window, each row's texts, in order, are its title, its control's, the in-use line for the second alone, its cost line, *Reports:* and its reports where it has any, and its provenance; no text equal to the key alone and none holding *Read from* (§3) |
| 2 | `controlpanel.TheProfileRowIsCompactTest.theTitlesTooltipHoldsTheKeyAndItsDescription` | `profile-boilerplateDocumentFrequencyFloor.title`'s tooltip: the key, an empty line, then `ProfileKey.about()`, *Read from* and `ProfileKey.hint()` and a stop, as the row drew them (§3) |
| 2 | `controlpanel.TheProfileRowIsCompactTest.theSeedFolderAndAWantedValueAreDrawnAsTheProfileTabsRows` | `seed-seedFolder` and a `wanted-` row after a child that ended wanting a value: each with its title's tooltip, `…provenance` or Edit reason alone, and no node `…provenance` that is a `TextField` (§3) |
| 3 | `controlpanel.AListValueIsTagsTest.eachItemTheFileListsIsATagInItsOrder` | `codeNameEndings` stated `.java .sql, .kt`: `profile-codeNameEndings` a `FlowPane` holding three tags, `.tag-1` to `.tag-3`, reading `.java`, `.sql` and `.kt` as `SelectableText.Line`s, then `profile-codeNameEndings.add`, a `TextField`, last (§4) |
| 3 | `controlpanel.AListValueIsTagsTest.enterAddsEachPieceTypedAsATagOnceAndEmptiesTheBox` | `.py, .java .rs` typed and Enter, `.java` a tag already: tags `.py` and `.rs` added after the last in that order, `.java` not twice, the box empty; Enter in the empty box adding nothing (§4) |
| 3 | `controlpanel.AListValueIsTagsTest.aTagIsRemovedByItsButton` | `profile-codeNameEndings.remove-2` fired: the second tag gone, the others in order, the box holding the focus (§4) |
| 3 | `controlpanel.AListValueIsTagsTest.backspaceInTheEmptyBoxRemovesTheLastTag` | Backspace pressed in the empty box: the last tag gone; Backspace in the box holding `.p`: no tag gone and the box `.` (§4) |
| 3 | `controlpanel.AListValueIsTagsTest.eachRemoveButtonIsReachedByTabAndRemovesItsTagFromTheKeyboard` | each remove button focus-traversable, in the tags' order before the box; focus on `.remove-1`, then Space: the first tag gone (§4) |
| 3 | `controlpanel.AListValueIsTagsTest.aSaveWritesTheTagsJoinedByOneSpaceAndWhatIsStillTyped` | tags `.java` and `.sql`, `.kt` typed and not entered, then Save: `profile.yaml`'s `codeNameEndings` value `.java .sql .kt`; and a list left as the file states it writing nothing (§4) |
| 3 | `controlpanel.AListValueIsTagsTest.theThreeListValuesAreTagsAndTheListBoxAndThePlusAreGone` | `codeNameEndings`, `outOfScopeNameEndings` and `preferredLanguages`, on the Profile tab: each `profile-<key>` a `FlowPane`, its box's prompt *e.g. .java, then Enter*, *e.g. .ok, then Enter* and *e.g. it, then Enter*; and no node `profile-<key>.plus` (§4) |
| 4 | `controlpanel.TheCodeSetsSectionTest.itOffersNoNameAndListsTheFoldersOutsideTheCorpusRootAlone` | a `codeSetFolders` of two folders outside the corpus root: `code-set-list` holds their two paths, each with Remove, and no word *named*; no node `code-set-name` (§5) |
| 4 | `controlpanel.TheCodeSetsSectionTest.aFolderUnderTheCorpusRootIsRefusedAsInsideIt` | a folder under the corpus root typed and `+` pressed: `code-set-outcome` reads *the folder `<folder>` is not added: it is inside the corpus root `<root>`*, and the list unchanged (§5) |
| 4 | `controlpanel.TheCodeSetsSectionTest.aNamesFileLeftInTheWorkingDirectoryIsNeitherReadNorWritten` | a working directory holding a `code-set-names.yaml` naming a folder under the corpus root: no row of `code-set-list` shows the folder or the name; a folder outside added and Save pressed: `codeSetFolders` written, and `code-set-names.yaml` the same bytes and the same last-modified time as before (§5, §6) |
| 4 | `controlpanel.TheCodeSetsSectionTest.theSaveWritesCodeSetFoldersAloneAndSaysSo` | Save with the list as the file states it: *nothing was written: every value is as profile.yaml states it*, the file unchanged; a folder removed then Save: *codeSetFolders saved in `<file>`* and the value without it, with `set in the control panel` (§5) |
| 4 | `deliverable.CodeSetsInTheTreeTest.aNamesFileLeftInTheWorkingDirectoryRenamesNothing` | a tree drawn with a `code-set-names.yaml` in the working directory naming one of two sets: both sets in `code/` under their default names, the index and `code.csv` naming those, no warning told and no count of dormant names (§6) |
| 4 | `deliverable.CodeSetFolderNamesTest.amongDefaultNamesAlikeTheLowestMemberKeepsIt` | sets `core` (lowest member 5), `core` (3), `Core` (2) and `kernel` (9): `Core`, `core~3`, `core~5` and `kernel`, whatever order the sets are handed in (§6) |
| 4 | `pipeline.NoShippedClassNamesTheCodeSetNamesFileTest.noClassOfTheJarOrTheControlPanelNamesIt` | every source of `src/main/java/io/algernon`: none holds `code-set-names`, and the scan read `CodeSetFolderNames`, so that a scan of nothing cannot pass (§6) |
| 5 | `controlpanel.TheCommandOptionsTest.recordMyAnswersIsGivenNoLabelFile` | `ChildCommand.label` for Record my answers: its arguments `label`, `--db-dir=<working directory>` and the presets passed, no other; and `ChildCommand` declaring no `withLabelFile` (§7) |
| 5 | `controlpanel.TheControlPanelsWindowTest.theCommandOptionsOfferNoLabelFile` | the window: no node `label-file` or `label-file.browse`, the section's text *Label file* nowhere, and `command-line-label` reading `vespera label --db-dir=<working directory>` (§7) |

### Tests that change or go

Each existing test this record changes, at the step that does it. Each record whose Tests section names a class or method removed or renamed here is corrected in place by the step that does it, the name followed by *(removed: …, ADR-366)*, or the new name given first and the old after it, *named `<old>` until ADR-366*, as the claims check reads a name that is gone.

| Step | Test | What happens |
| --- | --- | --- |
| 1 | `controlpanel.TheNoteSavedWithAValueTest` | is renamed `controlpanel.TheProvenanceSavedWithAValueTest`, its methods as the Tests above, its calls `provenanceShown` and `provenanceToSave`, its claims unchanged |
| 1 | `controlpanel.TheNoteSavedWithAValueTest.aValueChangedUnderTheProvenanceTheFileStatesIsNotRefused` | is renamed `controlpanel.TheProvenanceSavedWithAValueTest.aValueChangedUnderTheProvenanceTheFileStatesIsNotRefused`, its claims unchanged |
| 1 | `controlpanel.TheWindowSavesTheProfileTest.theProfileTabsSaveWritesEachValueAndNoteAsTheTableSays` | is renamed `controlpanel.TheWindowSavesTheProfileTest.theProfileTabsSaveWritesEachValueAndProvenanceAsTheTableSays`; its body changes at step 2 |
| 1 | `controlpanel.TheValuesTheNextInvocationWantsTest` | its calls of `FILLED_IN_NOTE` and `noteShown` renamed; its claims unchanged |
| 2 | `controlpanel.TheWindowSavesTheProfileTest.theProfileTabsSaveWritesEachValueAndProvenanceAsTheTableSays` | presses `…edit-reason` and sets `…provenance-box` where it set the `…provenance` field; its claims unchanged |
| 2 | `controlpanel.TheWindowSavesTheProfileTest.aSeedFolderOfferedFromTheLastOneIsWrittenBySave`, `controlpanel.TheValuesTheNextInvocationWantsTest.theArrangementTheNextInvocationNamesIsOfferedAsTheApprovalItWants` | unchanged: neither opens a box, and each value is saved with `set in the control panel` |
| 2 | ADR-364's `controlpanel.EveryTextOfTheWindowCanBeCopiedTest.aWheelOverASelectableTextScrollsTheTabItLiesIn` | a profile row's cost line, a wrapping `SelectableText`, takes the description's place, which no longer stands in the row |
| 2 | ADR-364's `controlpanel.EveryTextOfTheWindowCanBeCopiedTest.everyTextShownOutsideAControlIsSelectableText` | unchanged in what it holds; it reaches the provenance's line, and not the key, the description or the provenance's heading and hint, which are gone |
| 4 | `controlpanel.CodeSetRenamesTest` removed | with `CodeSetRenames` |
| 4 | `deliverable.CodeSetNamesTest` removed | with `CodeSetNames` |
| 4 | `pipeline.TheJarReadsWhatTheControlPanelWritesTest.codeSetNamesReadsTheListTheControlPanelWrites` removed | neither side reads or writes the file |
| 4 | `pipeline.TheJarReadsWhatTheControlPanelWritesTest.theJarReadsEachScalarTheControlPanelReadsToTheSameText` | loses its samples of `code-set-names.yaml`, its samples of `profile.yaml` unchanged; its javadoc names three formats in place of four |
| 4 | `deliverable.CodeSetFolderNamesTest.aTypedNameOutranksADefaultNameAndAmongAlikeTheLowestMemberKeepsIt` | is renamed `deliverable.CodeSetFolderNamesTest.amongDefaultNamesAlikeTheLowestMemberKeepsIt`, the Tests' row above |
| 4 | `deliverable.CodeSetFolderNamesTest.anEntryNamingNoSetOfTheRunIsDormantAndCounted` removed | no entry is dormant |
| 4 | `deliverable.CodeSetFolderNamesTest.aNameIsMadeSafeAndCutAndOneLeftEmptyIsTheLowestMemberOccurrence` | the three names, `...`, `CON` and the name of 120 units, given as the sets' default names; its claims unchanged |
| 4 | `deliverable.CodeSetFolderNamesTest.namesAreToldApartOnceTheyAreMadeSafe`, `deliverable.CodeSetFolderNamesTest.aSuffixedNameThatStillCollidesTakesTheSuffixAgain` | call `of(sets)`; their claims unchanged |
| 4 | `deliverable.CodeSetsInTheTreeTest.theTypedNamesAreAppliedAndTheDormantOnesToldAsACount`, `deliverable.CodeSetsInTheTreeTest.aNamesFileThatIsRefusedLeavesTheDefaultNamesAndIsToldOnce`, removed | no name is applied or refused, the Tests' row `aNamesFileLeftInTheWorkingDirectoryRenamesNothing` holding the file read by nothing |
| 4 | `deliverable.CodeSetsInTheTreeTest.theIndexListsEachSetAfterTheGroupsAndNeverCallsItAGroup` | writes no names file, and holds the index naming each set by its default name |
| 4 | `pipeline.TheClosingLineCountsTheFilesOfCodeNotCopiedTest.countsTheDormantNamesBeforeWhatAnEarlierDrawingLeft` removed | with the clause; the class's helper loses its count of dormant names |
| 4 | `pipeline.CodeSetTaskletTest.aRenameIsNotReadByStageOne` removed | no class reads the file, which the Tests' row `noClassOfTheJarOrTheControlPanelNamesIt` holds |
| 4 | `controlpanel.WorkingDirectoryWriteTest.onlyTheFourFilesAreWritten` | is renamed `controlpanel.WorkingDirectoryWriteTest.onlyTheThreeFilesAreWritten`: `profile.yaml`, `relevance-labels.yaml` and `not-relevant-folders.yaml`, and `code-set-names.yaml` refused as any other file |
| 4 | `controlpanel.WorkingDirectoryWriteTest.aWorkingDirectoryThatDoesNotExistIsRefusedForTheOtherTwoFiles` | is renamed `controlpanel.WorkingDirectoryWriteTest.aWorkingDirectoryThatDoesNotExistIsRefusedForTheLabelFile`, `relevance-labels.yaml` alone in its loop |
| 4 | `PipelineHoldsOnlyTheRulesOnRecordTest` | `deliverable.CodeSetNames` leaves the collaborators it lists for `GenerationTasklet` |
| 4 | ADR-364's `controlpanel.EveryTextOfTheWindowCanBeCopiedTest.everyTextShownOutsideAControlIsSelectableText` | its working directory holds no code set renamed, a file nothing reads |
| 5 | `controlpanel.TheCommandOptionsTest.theLabelFileIsPassedToLabelAndNeverToLabelAuto` removed | with `withLabelFile`, the Tests' row `recordMyAnswersIsGivenNoLabelFile` in its place |
| 5 | `pipeline.TheControlPanelAccountsForEveryArgumentOfTheJarsCommandsTest.eachArgumentIsSetByAControlOfTheControlPanelOrNeverPassedAndSaysWhy` | `label <file>` among the never passed, which are four, and no longer set by `label-file` |
| 5 | `controlpanel.ControlPanelStateTest` | its helper `passing` builds a `Plan` with no label file |
| 5 | `controlpanel.TheControlPanelsWindowTest.everyControlTheArgumentsTableNamesIsAControlOfTheWindow` | unchanged: the table names no `label-file` |

## What the implementation owes

Beside turning the tests above green, step by step:

- the stylesheet's `provenance`, `provenance-box`, `tags` and `tag`, a tag drawn as a rounded box of the window's own colours and its *×* as a small flat button, every button else keeping the one style (ADR-342 §3);
- at step 2, the README's paragraph on provenance and its paragraph on the Profile tab, saying a value's provenance stands on one line beside it and Edit reason opens it, and that a value changed without opening it is saved with `set in the control panel`;
- at step 3, the README's lines on a list value, saying an item is added with Enter and removed by its *×*;
- at step 4, the README's row of `code-set-names.yaml` in *Where things live* removed, its sentence on adding a code set's folder saying a folder outside the corpus root alone is added, its paragraph on code in the tree saying a set is named for its folder and dropping the clause of dormant names, and one sentence saying a `code-set-names.yaml` left in a working directory is no longer read and can be deleted by hand; `CONTEXT.md`'s Code set entry, whose set is renamed in `code-set-names.yaml` and named by its typed or default name, and its Working directory entry, which holds the code-set names; `docs/architecture.md`'s paragraph on the control panel, whose four files and `WorkingDirectoryWriteTest.onlyTheFourFilesAreWritten` become three and `onlyTheThreeFilesAreWritten`, rendered again;
- at step 5, the README's paragraph on Command options, which offers no label file, and its line that a label file kept elsewhere is recorded with `vespera label <file>`;
- ADR-364's head note naming this record's amendment of its §8, added once ADR-364 is in the tree;
- this record's number taken out of `docs/check-claims.mjs`'s `NOT_YET_BUILT` by the step that builds its last row.

## What this does not decide

- **The launch fields and the folder not relevant**: they stay as they are, made copyable by ADR-364.
- **The words of the cost line and of the reports' line.**
- **How long a tooltip waits before it shows**: JavaFX's own.
- **A name for a code set by any other means**, should the operator want one again: a record of its own.
