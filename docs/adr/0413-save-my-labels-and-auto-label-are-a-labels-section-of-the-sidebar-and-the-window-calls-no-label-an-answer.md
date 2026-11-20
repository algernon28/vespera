# ADR-413 — Save my labels and Auto Label are a Labels section of the sidebar, and the window calls no label an answer

> **Partly amended — see [ADR-435](0435-the-profile-may-list-the-topics-by-name-a-survivor-going-to-the-name-its-chunks-are-nearest-and-none-listed-infers-them.md).** Of §1: the sidebar is ten sections, `topics`, titled Topics, standing after `seed-folder` and before `labels`. The rest of this record stands.

> **Partly amended — see [ADR-424](0424-command-line-the-jars-reports-and-the-operators-documents-call-no-label-an-answer-and-no-document-to-label-a-question.md).** Of *What this does not decide*: `vespera label`'s usage, the jar's lines, the invocation report, the README's sections on the command and the label file, `CONTEXT.md`'s Relevance label and the window's *question* call no label an answer and no document to label a question; and the README's section on the control panel names Auto Label where it still named Let the local model answer.

> **Partly amended — see [ADR-430](0430-the-control-panel-checks-docker-the-containers-and-the-models-and-starts-nothing-until-start-docker-is-pressed.md).** Of §1: Save my labels and Auto Label each stand on a wrapper carrying its caption, shown on mouse over; `label-words` and `label-auto-words` are no longer texts of the section. §3's words are the captions, unchanged. The rest of this record stands.

> **Partly amended — see [ADR-432](0432-each-step-has-a-chart-beneath-its-legs-bars-the-output-spans-the-column-above-the-legs-and-the-cockpit-holds-no-tabs.md).** Of `label <file>`'s reason it quotes: the window sets the labels in the Folders section, by folder; and the words beneath the Inclusions tab's tree stand beneath the Folders section's, the tab gone. The rest of this record stands.

- **Date**: 2026-11-17
- **Status**: accepted on 2026-11-17.
- **Decided**: by the operator, on [#857](https://github.com/algernon28/vespera/issues/857): that the button starting `vespera label` is named **Save my labels** and the one starting `vespera label --auto` **Auto Label** (§2); that the two, each with its caption, leave the action card for the sidebar, with the other sections of configuration (§1); that every other text of the window saying *answers*, *Record my answers* or *Let the local model answer* is worded in terms of labels, Save my labels' caption saying that it reads `relevance-labels.yaml` in the working directory (§3); and that the two start the commands they start, no label file passed (§2). Under the operator's delegation: that the two are a section of their own, titled Labels, after Seed folder (§1); the words of each text reworded, and which texts the rule reaches (§3); that the ids stay (§4); and the tests.
- **Amends**: [ADR-344](0344-the-control-panels-window-is-one-cockpit-run-and-stop-the-steps-as-bars-beside-the-output-the-progress-pages-three-charts-the-profile-and-the-folders-to-include-starting-from-the-real-configuration.md) §1, whose action card holds `label` and `label-auto`, each with one sentence beside it: they are the sidebar's Labels section (§1); and §5, whose label commands are named **Record my answers** and **Let the local model answer**, with the sentences it gives beside them: they are named and captioned as §2 and §3 have it. It carries a note at its head.
- **Amends**: [ADR-377](0377-a-profile-row-is-one-line-its-provenance-in-a-tooltip-a-list-is-tags-a-code-set-named-by-its-folder-and-no-label-file.md) §4, whose second table gives the two warnings of the label file as *Answers:* lines naming Record my answers, and whose tooltips of eight keys speak of answers saved: they are §3's; and §10, whose **Record my answers** starts `vespera label`: **Save my labels** starts it, as there. It carries a note at its head.
- **Amends**: [ADR-389](0389-the-profile-is-edited-in-a-window-of-its-own-opened-from-the-first-section-of-the-sidebar.md) §1, whose sidebar is eight sections: it is nine, `labels` after `seed-folder` (§1). It carries a note at its head.
- **Amends**: [ADR-393](0393-a-working-directory-is-named-for-its-corpus-root-and-day-chosen-from-a-list-and-publish-takes-what-the-last-run-reached.md) §5, whose row's next value reads `answers to record` where `answer` is true: it reads `labels to save` (§3). It carries a note at its head.
- **Keeps**: ADR-377 §10: the label commands' button starts `vespera label --db-dir=<working directory>` with the presets passed and no label file, `command-line-label` shows that line, and the command line keeps `vespera label <file>`. [ADR-336](0336-the-launch-page-starts-label-auto-beside-label-and-every-argument-of-the-commands-the-control-panel-starts-is-set-by-a-control-of-the-launch-page-or-listed-as-never-passed-with-why.md) §1 and ADR-344 §4: the second starts `vespera label --auto`, with the presets ADR-344 §4 lists for it. ADR-344 §1: Run, the two label commands and Publish are disabled while the window's own child runs or another holds the working directory. [ADR-334](0334-the-default-output-folder-named-is-none-an-empty-field-shows-the-value-in-use-relevance-is-answered-by-folders-not-relevant-the-profile-is-one-form-and-a-code-set-folder-may-lie-outside-the-corpus-root.md) §6: the folders not relevant set the label file's entries by folder, as they do. ADR-377 §3: the id of a row's warning of the label file, `<prefix><key>.answers`. [ADR-294](0294-the-control-panel-learns-what-the-next-invocation-wants-from-a-file-the-jar-writes-with-the-closing-line.md) §1: the key `answer` of `next-invocation.properties`, and of `report.json`'s `next`, which no text shows. [ADR-122](0122-the-vocabulary-binds-our-names-not-the-prose-rendered-for-an-outside-reader.md): a text written for the operator is free of `CONTEXT.md`'s lists.
- **Settles**: [#857](https://github.com/algernon28/vespera/issues/857).

## Context

**The action card** (`Cockpit.mainPane()`) holds, top to bottom: Run, Stop, Shut down Docker, Publish where a rendering is drawn and the boxes of its destinations; the values the next invocation wants (`wanted`); then a row of `label`, *Record my answers*, beside a caption with no id, *Press this after you have answered the documents in relevance-labels.yaml, in the working directory, by setting each one's relevant to true or false. It saves your answers; it reads no document and answers nothing itself. Then press Run.*; and a row of `label-auto`, *Let the local model answer*, beside `label-auto-words`, *The local model, ‹model›, reads the documents, answers for you and sets the cut-off, and your own answers are never replaced.*, the model named while the label model's field names one. A press calls `Cockpit.begin` with the button's name, and the outcome reads *‹name› started*.

**The sidebar** is eight sections, `profile`, `preflight`, `folders`, `services`, `seed-folder`, `code-sets`, `command-options`, `reports`, Folders open as the window opens (ADR-389 §1). Each holds values the commands are started with, or reports and checks of them; the action card holds what starts and stops the commands.

**What the window calls an answer.** A relevance label is, in `CONTEXT.md`'s words, a recorded answer about one document, and the label file asks a question of each document it lists. The window carries that frame into what the operator reads: beside the buttons, in two warnings of a profile row (ADR-377 §4), in the tooltips of eight keys in `profile-texts.json`, in the label model's hint (`LaunchValues.hintOf`), beneath the Inclusions tab's tree and in the line it says once a folder is listed as not relevant (`NotRelevantPane`), and in a working directory's next value in the Folders section's list (`WorkingDirectoryRow`, ADR-393 §5). `ChildCommand.ARGUMENTS` gives two reasons in the same words, and `ControlPanelState`'s javadoc names the two buttons; neither is shown. The README's section on the control panel names both buttons and describes the folders not relevant answering the label file's questions.

## Decision

### 1. The Labels section

**The sidebar is nine sections, in this order**: `profile`, `preflight`, `folders`, `services`, `seed-folder`, **`labels`**, `code-sets`, `command-options`, `reports`. Folders is still the section open as the window opens.

**`labels`**, a `TitledPane` of the sidebar's accordion titled **Labels**, holds, top to bottom:

1. `label`, the button **Save my labels**;
2. `label-words`, its caption, a `SelectableText.Wrapping` (§3);
3. `label-auto`, the button **Auto Label**;
4. `label-auto-words`, its caption, a `SelectableText.Wrapping` (§3), naming the label model as it does.

**Each of the four stands once in the window.** The action card holds Run, Stop, Shut down Docker, Publish with its boxes, and `wanted`, and none of them. The section carries no badge: it holds no value, and nothing it holds can be missing.

It stands after Seed folder because the labels say which documents are relevant to the seed set that section names, and before the code sets, which no label concerns.

### 2. The names, and what each starts

| Id | Name | Starts | The outcome, once started |
| --- | --- | --- | --- |
| `label` | **Save my labels** | `vespera label --db-dir=<working directory>` and the presets passed, no label file (ADR-377 §10) | *Save my labels started*, and the lines of the launch values stored, as before |
| `label-auto` | **Auto Label** | `vespera label --auto --db-dir=<working directory>` and the presets passed (ADR-344 §4) | *Auto Label started*, and the same lines |

`Cockpit.begin` is given the button's name, so the outcome names the button pressed. Both are disabled as before, while the window's own child runs or another holds the working directory, and `command-line-label` and `command-line-label-auto` show the lines they start.

### 3. The words

**No text of the control panel's windows calls a relevance label an answer, or setting one answering, and none names *Record my answers* or *Let the local model answer*.** Where such a text said *answer*, it says *label*. A text whose *answer* is another thing's, a sidecar answering its address or a dialog's answers, stays.

| Where | Was | Is |
| --- | --- | --- |
| `label` | Record my answers | Save my labels |
| `label-auto` | Let the local model answer | Auto Label |
| `outcome`, once a press starts one | Record my answers started; Let the local model answer started | Save my labels started; Auto Label started |
| `label-words` | Press this after you have answered the documents in relevance-labels.yaml, in the working directory, by setting each one's relevant to true or false. It saves your answers; it reads no document and answers nothing itself. Then press Run. | Reads relevance-labels.yaml in the working directory, where each document's relevant is set to true or false, and saves those labels for the next Run. It reads no document and sets no label itself. |
| `label-auto-words`, the field blank | The local model reads the documents, answers for you and sets the cut-off, and your own answers are never replaced. | The local model reads the documents, sets their labels and the cut-off, and never replaces a label you set. |
| `label-auto-words`, the field naming ‹model› | The local model, ‹model›, reads the documents, answers for you and sets the cut-off, and your own answers are never replaced. | The local model, ‹model›, reads the documents, sets their labels and the cut-off, and never replaces a label you set. |
| a row's warning, `labelsRefused` (ADR-377 §4) | Answers: press Record my answers first; after the change the old answers file is refused. | Labels: press Save my labels first; after the change the old relevance-labels.yaml is refused. |
| a row's warning, `sampleDrawnAgain` where `labelsRefused` is not | Answers: press Record my answers first, or unsaved answers in relevance-labels.yaml are lost. | Labels: press Save my labels first, or labels in relevance-labels.yaml not yet saved are lost. |
| `hint-vespera.label.model` | The local model that answers the questions when you ask it to. | The local model that sets the labels when you press Auto Label. |
| the words beneath the Inclusions tab's tree | Folders not relevant are still read: the relevance questions below them are answered not relevant, and no run changes. | Folders not relevant are still read: the documents of relevance-labels.yaml below them are labelled not relevant, and no run changes. |
| `not-relevant-outcome`, the label file there | the folders not relevant are saved in ‹file›, and the ‹n› questions answered: ‹m› not relevant, ‹n − m› relevant | the folders not relevant are saved in ‹file›, and the ‹n› documents of relevance-labels.yaml labelled: ‹m› not relevant, ‹n − m› relevant |
| `not-relevant-outcome`, no label file | the folders not relevant are saved in ‹file›; there are no questions to answer yet | the folders not relevant are saved in ‹file›; relevance-labels.yaml holds no document to label yet |
| a working directory's next value in the Folders section's list (ADR-393 §5) | answers to record | labels to save |

**The tooltips** of `profile-texts.json` that spoke of answers take these sentences in place of theirs, the rest of each tooltip unchanged:

| Key | Was | Is |
| --- | --- | --- |
| `embeddingModel` | The new set to judge is about sixty documents; answers you already saved with Record my answers are kept. | The new set to judge is about sixty documents; labels you already saved with Save my labels are kept. |
| `relevanceScoreFloor` | The cut-off is used only when every relevant / not relevant answer you saved was given under the current comparing model, or when you saved none. | The cut-off is used only when every relevant / not relevant label you saved was set under the current comparing model, or when you saved none. |
| `relevanceScoreFloor` | If you change the comparing model, mark the new documents, press Record my answers, then set this number again. | If you change the comparing model, mark the new documents, press Save my labels, then set this number again. |
| `relevanceScoreFloor` | Vespera may fill this number in itself from your answers, choosing the highest number that leaves out no document you marked relevant; a number you typed yourself is never overwritten. | Vespera may fill this number in itself from your labels, choosing the highest number that leaves out no document you marked relevant; a number you typed yourself is never overwritten. |
| `generationModel` | Your approval and your saved answers are kept. | Your approval and your saved labels are kept. |
| `extractionAttempt` | Nothing from the earlier try is deleted, and answers you saved in relevance-labels.yaml are kept. | Nothing from the earlier try is deleted, and labels you saved in relevance-labels.yaml are kept. |
| `outOfScopeNameEndings` | Answers you saved with Record my answers are kept when a new sample is picked. | Labels you saved with Save my labels are kept when a new sample is picked. |
| `configurationSettingShareFloor`, `codeSetFolders`, `foldersNotWalked` | Answers you already saved with Record my answers are kept. | Labels you already saved with Save my labels are kept. |

Every new text keeps ADR-377 §4's rule of words: none says *invocation*, *run*, *cluster* or the others it lists, but in a file's name.

**Two texts no operator reads** take the same words: `ChildCommand.ARGUMENTS`' reason for `label auto`, *Passed by Auto Label; Save my labels passes none.*, and for `label <file>`, *Never passed: the window sets the labels in the Inclusions tab, by folder (ADR-334 §6), in the working directory's label file, which label reads where no file is named; the command line keeps it (ADR-377).*; and `ControlPanelState`'s javadoc names Run, Save my labels, Auto Label and Publish.

**The README's section on the control panel** reads as the window: the sidebar's nine sections, Labels among them with its two buttons and what each starts; the action card without them; *Run, Save my labels, Auto Label and Publish* where it names the buttons that pass the working directory; Command options' Save my labels saving the labels of the working directory's `relevance-labels.yaml`, which the Inclusions tab sets by folder; and the folders not relevant labelling the label file's documents.

### 4. What changes, by class

- **`Cockpit`**: draws `labels` after `seed-folder` with §1's four parts, `label-words` the caption's id; the action card loses them; the buttons, `begin`'s names and the two captions are §3's; its javadoc says nine sections.
- **`ProfileRow`**: `warningsOf` gives §3's two warnings; the warning's id is still `answers`, told now by its first word, *Labels:*.
- **`profile-texts.json`**: §3's sentences in the tooltips of eight keys.
- **`LaunchValues`**, **`NotRelevantPane`**, **`WorkingDirectoryRow`**, **`ChildCommand`**, **`ControlPanelState`**: §3's words.

The ids stay: `label`, `label-auto`, `label-auto-words`, `command-line-label`, `command-line-label-auto` and `<prefix><key>.answers`; so do `ControlPanelState.Start.LABEL` and `LABEL_AUTO` and `LabelAnswers`, names of ours that no operator reads. No class of the jar changes, and `io.algernon.vespera.ui` is in no module: no run id moves.

## Consequences

- The action card holds what starts and stops the job and the values it wants next; the label commands are reached in the sidebar, which shows one section at a time, so opening Labels closes the section open before it.
- The window and the command line speak differently of the same file: the window of labels saved, the command line's usage, the README's sections on `vespera label` and the jar's lines of answers recorded (What this does not decide).

## Alternatives refused

- **Renaming the buttons and leaving them in the action card.** The operator chose the sidebar.
- **Putting them in Seed folder or Command options.** Seed folder's Save writes `profile.yaml` and Command options holds the presets every command is passed; a section of their own keeps each section one kind of thing.
- **Renaming the ids with the words.** An id names no text the operator reads, and every test and record that finds a part by its id would move for nothing.

## Tests

| Method | What it holds |
| --- | --- |
| `ui.TheControlPanelsWindowTest.theLabelCommandsAreTheSidebarsLabelsSection` | the sidebar holds `labels`, titled Labels, holding `label` and `label-auto` in that order, each once in the window, each on its wrapper, whose caption is since ADR-430 §8 what this section's text was ([#889](https://github.com/algernon28/vespera/issues/889)); the buttons read Save my labels and Auto Label; the two captions read as §3 has them, Auto Label's with the field blank and naming a model; pressed, each says *‹name› started* and starts `label` and `label --auto` (§1, §2, §3) |
| `ui.TheControlPanelsWindowTest.theWindowSaysLabelWhereItSaidAnswer` | the caption of the list of folders not relevant, since ADR-430 §8 no longer text beneath the tree ([#889](https://github.com/algernon28/vespera/issues/889)), reads as §3 has it and none of the tab's texts says *answer*; a folder listed as not relevant with no label file, and one listed over a label file of three documents, say §3's two lines; the label model's hint, its field's caption, and a working directory's next value read as §3 has them; and no text of the window names Record my answers or Let the local model answer (§3) |
| `ui.TheProfileTextsTest.noTextOfARowCallsALabelAnAnswer` | no title, short description, tooltip, cost line, warning or line of the value in use says *answer* in any of its forms; the seed folder's warnings and the tooltips of `embeddingModel` and `relevanceScoreFloor` name Save my labels (§3) |
| `ui.TheCostLineTest.aWarningStandsOnlyWhereTheCostAppliesIt`, its two warnings changed | each key's warnings, the two of the label file in §3's words (§3) |
| `ui.TheProfileWindowTest.theProfileIsTheSidebarsFirstSectionAndOpensOneWindowOfItsOwn`, its sections changed | the sidebar's nine sections in §1's order |
| `ui.EveryTextOfTheWindowCanBeCopiedTest.everyTextShownOutsideAControlIsSelectableText`, its section titles changed | the sidebar's titles, Labels after Seed folder, are the titled panes' own (§1) |
| `ui.TheCommandOptionsTest.saveMyLabelsIsGivenNoLabelFile`, named `recordMyAnswersIsGivenNoLabelFile` until this record | unchanged but for its words: the label commands' button starts `label`, `--db-dir=<working directory>` and the presets passed, no label file (§2) |
| `ui.TheControlPanelsWindowTest.theCommandOptionsOfferNoLabelFile`, its words changed | unchanged but for its words (§2) |

## What the implementation owes

§4, with each test above green; nothing else moves.

## What this does not decide

- **The command line's words.** `vespera label`'s usage, the README's sections on the command and on the label file, the jar's closing lines and the invocation report say the answers are recorded, and `CONTEXT.md`'s Relevance label entry calls a label an answer; none is a text of the window.
- **The window's *question*.** A line that calls an entry of the label file a question without saying *answer*, as the refusal *the corpus root holds every question; name a folder below it*, stays as it is.
- **The refusals' name.** A refusal of either button still begins *Label is not started*, `ControlPanelState.Start`'s name for both.
- **How each caption stands beside its button** within the section, beyond §1's order.
