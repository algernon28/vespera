# ADR-424 — The command line, the jar's reports and the operator's documents call no label an answer, and no document to label a question

- **Date**: 2026-11-19
- **Status**: accepted on 2026-11-19.
- **Decided**: by the operator, on [#879](https://github.com/algernon28/vespera/issues/879): that what the operator writes in `relevance-labels.yaml`, the `relevant` of an entry included, is a label wherever the operator reads of it, and an entry of the file a document to label, never a question (§1); that the rule reaches every text the jar writes for the operator, `vespera label`'s usage, its lines, refusals and closing lines, `report.html`, `relevance-labelling.html` and the titles of `relevance-labelling.json`, the README and `docs/running-stage-by-stage.md`, and the window's texts ADR-413 left saying *question*, and not `docs/architecture.md` or a javadoc (§1); that `CONTEXT.md`'s Relevance label keeps its term, its definition and two other entries reworded, and `answer` is not added to its `_Avoid_` line (§7); that the command line keeps the verb *record*, and `vespera label recorded nothing:` and `vespera label --auto recorded nothing:` begin a refusal as they do (§2); and that every stored key, id and class name keeps its name, a stored text the operator is shown changing (§8). Under the operator's delegation: the words of each text (§2 to §7).
- **Amends**: [ADR-413](0413-save-my-labels-and-auto-label-are-a-labels-section-of-the-sidebar-and-the-window-calls-no-label-an-answer.md), whose *What this does not decide* leaves the command line's words and the window's *question* as they were: §2 to §6 word them. It carries a note at its head.
- **Amends**: [ADR-169](0169-the-label-file-shows-the-answers-already-recorded-and-a-changed-answer-replaces-the-old-one-and-is-reported.md) §3 and §4, whose lines of `vespera label` count answers and questions and refuse answers about another seed set: they are §3's. It carries a note at its head.
- **Amends**: [ADR-197](0197-a-local-model-answers-the-relevance-questions-and-the-floor-is-a-rule-over-the-labels-so-no-document-reaches-a-hosted-model.md) §6, whose refusal names answers not yet recorded and whose line counts questions and those answered by a person: they are §3's. It carries a note at its head.
- **Amends**: [ADR-266](0266-every-invocation-names-its-progress-page-first-and-opens-it-and-vespera-label-writes-it-too.md) §3, whose counts of `vespera label --auto` end `answered by a person`: the last is `labelled by a person` (§3). It carries a note at its head.
- **Amends**: [ADR-295](0295-the-control-panel-writes-profile-values-label-answers-and-code-set-names-holding-the-working-directory-lock-one-operator-action-at-a-time.md) §6, whose refusal says there is no question to answer: it is §5's. It carries a note at its head.
- **Amends**: [ADR-334](0334-the-default-output-folder-named-is-none-an-empty-field-shows-the-value-in-use-relevance-is-answered-by-folders-not-relevant-the-profile-is-one-form-and-a-code-set-folder-may-lie-outside-the-corpus-root.md) §6, whose refusal of the corpus root says it holds every question: it is §5's. It carries a note at its head.
- **Keeps**: [ADR-294](0294-the-control-panel-learns-what-the-next-invocation-wants-from-a-file-the-jar-writes-with-the-closing-line.md) §1, the key `answer` of `next-invocation.properties` and of `report.json`'s `next`; [ADR-362](0362-an-invocations-report-is-one-json-file-drawn-as-static-html-through-a-mustache-template-and-no-command-opens-anything.md) §3, `report.json`'s `label.answers` and its `words` and `count`; ADR-413 §4, the warning id `<prefix><key>.answers` and the class `LabelAnswers`; [ADR-122](0122-the-vocabulary-binds-our-names-not-the-prose-rendered-for-an-outside-reader.md), by which a text written for the operator is free of `CONTEXT.md`'s lists.
- **Settles**: [#879](https://github.com/algernon28/vespera/issues/879).

## Context

ADR-413 took *answer* out of the control panel's windows: the buttons are Save my labels and Auto Label, and the window speaks of labels set and saved. It left the rest as it was. The same act still reads, on the command line, as answers recorded: `vespera label`'s usage says *Records the answers written into the relevance label file*, its `--auto` *Have the local model answer the questions in the label file*, the closing line of a run that wrote the label file *Next: answer the questions in relevance-labels.yaml*, and `vespera label`'s own line *recorded 2 answer(s) about the seed set at …*. `report.html` heads the counts of a `vespera label` *Answers recorded*; `relevance-labelling.html` has a column *Being asked about* and a section *What the answers say so far*; the README's invocation 3 says *Answer them, then run `vespera label`*. The window and the command line speak of one file in two ways.

The frame the operator refused in the window is a question and answer: the label file asks a question of each document it lists, and the operator answers it. Two words carry it, *answer* for the label and *question* for the entry, and a third, *ask about*, for drawing a document into the sample.

The words also name things that are no label. A sidecar answers its address or does not; the converter answers about a file; a local model gives an answer, or none, when it is asked about a document or a group; a dialog has answers; a profile value is answered or unset, and `NumericValue.Answered` is that state. None of them is a question and answer put to the operator about a document.

Beneath the texts, the names of ours that say *answer*: the class `LabelAnswers` and the record `LabelFileReader.Answers`, the methods `RelevanceLabels.answerFor` and `RelevanceLabeller.answer`, `NextAction.answersRecordedAgainst`, and about a hundred more in `embedding` and `pipeline`; and the stored ones, `next-invocation.properties`'s `answer`, `report.json`'s `next.answer` and `label.answers`, the warning id `<prefix><key>.answers`, the section id `what-the-answers-say-so-far` and the column id `asked` of `relevance-labelling.json`. `CONTEXT.md`'s Relevance label is defined as *A recorded answer about one document*, and its `_Avoid_` line, *annotation, ground truth, judgement, rating*, does not hold *answer*.

## Decision

### 1. The rule, and what it reaches

**No text written for the operator calls a relevance label an answer, setting one answering, an entry of the label file a question, or drawing a document into the label file asking about it.** Where such a text said *answer*, it says *label*; where it said *question*, *document* or *document to label*; where it said *asks about*, *draws*.

The rule reaches:

- `vespera label`'s usage, as picocli prints it (§2);
- every line the jar writes for the operator on the console and in `logs/vespera.log`: a refusal, the line `vespera label` and `vespera label --auto` end on, and the closing line of every command (§3);
- `report.html`, as the shipped template draws it, `relevance-labelling.html`, and the titles, paragraphs and column names of `relevance-labelling.json`, which the control panel draws natively (§4);
- the texts of the control panel's windows ADR-413 left saying *question* (§5);
- the README and `docs/running-stage-by-stage.md` (§6);
- `CONTEXT.md`'s entries that call a label an answer (§7).

It does not reach `docs/architecture.md`, a javadoc, a comment, a test's report-visible text, or any name of ours (§8). A text whose *answer*, *question* or *ask* is another thing's stays: a sidecar's or a model's answer, a group asked about, a dialog's answers, a profile value answered, *the answer is a different seed folder*, a band short of the documents it was asked for.

### 2. The command line

| Where | Was | Is |
| --- | --- | --- |
| `vespera label`'s description | Records the answers written into the relevance label file. | Records the labels set in the relevance label file. |
| `--auto`'s description | Have the local model answer the questions in the label file, then set relevanceScoreFloor by the rule that loses no documentation (ADR-197). An answer a person gave is never replaced. | Have the local model label the documents in the label file, then set relevanceScoreFloor by the rule that loses no documentation (ADR-197). A label a person set is never replaced. |
| the README's line of `vespera label` under *Commands* | record the answers you wrote into the label file | record the labels you set in the label file |

**The verb stays *record*.** The window's button saves, and the command records: it writes the labels into the ledger, which is what *recorded* says in every line it ends on. **A refusal still begins `vespera label recorded nothing: ` or `vespera label --auto recorded nothing: `**, which says no *answer*; only what follows the colon changes, where §3 has it.

### 3. The jar's lines

| Class | Was | Is |
| --- | --- | --- |
| `LabelFileReader` | the label file names no run, so there is no way to tell which sample it was answered against; regenerate it and label the new file | the label file names no run, so there is no way to tell which sample it was labelled against; regenerate it and label the new file |
| `LabelFileReader` | the label file was generated under run ‹r›, but the current sample is run ‹s›; answers about one sample are not answers about another, so nothing in it was recorded | the label file was generated under run ‹r›, but the current sample is run ‹s›; labels about one sample are not labels about another, so nothing in it was recorded |
| `LabelFileReader` | the label file has no answers in it yet: ‹n› document(s) are still waiting, so nothing was recorded | the label file has no labels in it yet: ‹n› document(s) are still waiting, so nothing was recorded |
| `LabelIngestion`, no file | … run vespera against the corpus first, answer the questions in the file, then run this again. | … run vespera against the corpus first, label the documents in the file, then run this again. |
| `LabelIngestion`, no seed folder | no seed folder is named in the profile, so there is no seed set for these answers to be about. Nothing was recorded. | no seed folder is named in the profile, so there is no seed set for these labels to be about. Nothing was recorded. |
| `LabelIngestion`, another seed set | …, but the profile now names the seed set at ‹p›; answers about one seed set are not answers about another, so nothing in it was recorded | …, but the profile now names the seed set at ‹p›; labels about one seed set are not labels about another, so nothing in it was recorded |
| `LabelIngestion`, recorded | recorded ‹n› answer(s) about the seed set at ‹p›: … | recorded ‹n› label(s) about the seed set at ‹p›: … |
| `LabelIngestion`, nothing new | recorded nothing new about the seed set at ‹p›: ‹n› answer(s) unchanged, none new | recorded nothing new about the seed set at ‹p›: ‹n› label(s) unchanged, none new |
| `LabelIngestion`, blanks | ; ‹n› question(s) are still blank | ; ‹n› document(s) are still blank |
| `AutoLabelling`, unrecorded | the label file holds ‹n› answer(s) not yet recorded; run vespera label first | the label file holds ‹n› label(s) not yet recorded; run vespera label first |
| `AutoLabelling`, the labeller stopped | ‹labeller› did not answer: ‹why›. Answers recorded before it stopped are kept; run the same command again. | ‹labeller› did not answer: ‹why›. Labels recorded before it stopped are kept; run the same command again. |
| `AutoLabelling`, its line | labelled ‹n› of ‹m› question(s) with ‹labeller›: ‹a› relevant, ‹b› not relevant; ‹c› left blank because no opening could be read or the model gave no answer; ‹d› already answered by a person and left as they are | labelled ‹n› of ‹m› document(s) with ‹labeller›: ‹a› relevant, ‹b› not relevant; ‹c› left blank because no opening could be read or the model gave no answer; ‹d› already labelled by a person and left as they are |
| `AutoLabelling`, its counts | `relevant`, `not relevant`, `left blank`, `answered by a person` | `relevant`, `not relevant`, `left blank`, `labelled by a person`, in that order |
| `NextAction`, no label file written | ‹what is set› No questions were written this invocation, so there is nothing to answer yet. Next: fix what the gated line above names, and run again. | ‹what is set› No documents to label were written this invocation, so there is nothing to label yet. Next: fix what the gated line above names, and run again. |
| `NextAction`, labels wanted | ‹what is set› Next: answer the questions in relevance-labels.yaml -- relevance-labelling.html says what each one is asking and what your answers are for -- then record them with vespera label. | ‹what is set› Next: label the documents in relevance-labels.yaml -- relevance-labelling.html shows each one and what your labels are for -- then record them with vespera label. |
| `NextAction`, what is set | …, and 1 answer is recorded …; …, and ‹n› answers are recorded … | …, and 1 label is recorded …; …, and ‹n› labels are recorded … |

*the model gave no answer* and *did not answer* stay: they are the model's answer, not the operator's. The line saying what is set keeps *No value in the profile is answered yet* and *Every value the profile asks for is answered*, a profile value's state.

### 4. The reports

**`report.html`**, the shipped `report.html.mustache`:

| Where | Was | Is |
| --- | --- | --- |
| the label section's heading | Answers recorded | Labels recorded |
| its caption, and its table's `title` | The answers this invocation counted, by their words. | The labels this invocation counted, by their words. |
| its table's first column | Answer | Labels |
| the next invocation's line, where `next.answer` is true | Answers are wanted. | Labels are wanted. |

The section is still `data-section="answers"`, drawn from `drawn.answers` and `drawn.next.answer` (§8).

**`relevance-labelling.html` and `relevance-labelling.json`**, `RelevanceLabellingReport`:

| Where | Was | Is |
| --- | --- | --- |
| the headline figure `sampled` | Documents put to a person | Documents to label |
| `threshold-not-applied`, page and JSON | The answers this number was read off were given while ‹model› was in use | The labels this number was read off were set while ‹model› was in use |
| `score-spread`'s column `asked`, page and JSON | Being asked about | To label |
| `score-spread`, the page | A proportion read off four answers is not the same evidence as one read off ‹n› | A proportion read off four labels is not the same evidence as one read off ‹n› |
| `what-the-answers-say-so-far`'s title, page and JSON | What the answers say so far | What the labels say so far |
| the same, nothing labelled, JSON | Nobody has answered any of the questions yet. | No document has been labelled yet. |
| the same, nothing labelled, page | Nobody has answered any of the questions yet. Fill in the label file beside this page, run the tool again, and this section will say what the answers came to and what each possible cut would cost. | No document has been labelled yet. Fill in the label file beside this page, run the tool again, and this section will say what the labels came to and what each possible cut would cost. |
| `the-documents-to-judge`, JSON | Each one below is drawn from a band, and the same run always asks about the same documents. | Each one below is drawn from a band, and the same run always draws the same documents. |
| `the-documents-to-judge`, the page | Each one below is drawn from a band, and the same run always asks about the same documents, so you can stop and come back. Write your answers in the label file beside this page. | Each one below is drawn from a band, and the same run always draws the same documents, so you can stop and come back. Set their labels in the label file beside this page. |

*fewer documents than were asked for* and *The answer is a different seed folder* stay: the first is the preset's count, the second no label. `relevance-labelling.json`'s `FORMAT` is not raised: no field is removed, renamed or retyped, and the ids stay.

### 5. The control panel's windows

| Class | Was | Is |
| --- | --- | --- |
| `NotRelevantPane`, the corpus root typed as a folder not relevant | the folder ‹typed› is not added: the corpus root holds every question; name a folder below it | the folder ‹typed› is not added: the corpus root holds every document to label; name a folder below it |
| `LabelAnswers.rewrite`, no label file | relevance-labels.yaml is not there, so there is no question to answer; vespera run writes it | relevance-labels.yaml is not there, so there is no document to label; vespera run writes it |
| `LabelAnswers.rewrite`, a path the file does not list | relevance-labels.yaml holds no question about ‹path› | relevance-labels.yaml lists no document at ‹path› |

ADR-413 §3's texts stand as they are.

### 6. The operator's documents

**The README**:

| Where | Was | Is |
| --- | --- | --- |
| invocation 3 of *The path is five invocations* | your answers, sixty as Vespera ships, in `relevance-labels.yaml` … records your answers. | your labels, sixty as Vespera ships, in `relevance-labels.yaml` … records your labels. |
| `clusterResolution` | the answers you recorded are kept, an answer typed into the file and not yet recorded goes with the file | the labels you recorded are kept, a label typed into the file and not yet recorded goes with the file |
| *Invocation 3 in more detail*, the label file | A document you have already answered, against the same seed folder and from the same corpus root, shows your answer instead | A document you have already labelled, against the same seed folder and from the same corpus root, shows your label instead |
| the same | Answer them, then run `vespera label`. | Label them, then run `vespera label`. |
| the same | the same run always asks about the same documents, so you can stop and come back. | the same run always draws the same documents, so you can stop and come back. |
| the same | **Your answers outlive the run that asked.** They are keyed to the documents, so changing the embedding model later re-scores the archive and re-reads the answers you already gave, without asking you anything twice. | **Your labels outlive the run that drew the documents.** They are keyed to the documents, so changing the embedding model later re-scores the archive and re-reads the labels you already set, without having you label anything twice. |
| *Where things live*, `not-relevant-folders.yaml` | the control panel writes it and answers the questions from it | the control panel writes it and sets the labels of relevance-labels.yaml from it |
| *Where things live*, `relevance-labels.yaml` | the questions you answer, sixty as Vespera ships | the documents you label, sixty as Vespera ships |
| what `vespera label` writes in `report.json` | the table of the answers it recorded | the table of the labels it recorded |
| the working directory's lock | for the moment it saves a value, answers or names into the working directory | for the moment it saves a value, labels or names into the working directory |
| `vespera.db-wal` | once it reaches the relevance questions it writes `relevance-labels.yaml` anew: if you are answering them, record your answers with `vespera label` first. | once it reaches the documents to label it writes `relevance-labels.yaml` anew: if you are labelling them, record your labels with `vespera label` first. |
| switching to the card | The answers you already wrote into the label file are kept | The labels you already set in the label file are kept |
| the models | It answers the questions with `qwen3:8b` | It labels the documents with `qwen3:8b` |
| pulling the embedding model again | until the sample is answered again from the new label file | until the sample is labelled again from the new label file |
| the control panel's folders | When **Let the local model answer** answers nothing, because there is no label file or one holds answers not yet recorded, the command says why in one line. | When **Auto Label** labels nothing, because there is no label file or one holds labels not yet recorded, the command says why in one line. |

And the line of `vespera label` under *Commands*, §2's. The last row is a text ADR-413 §3 reached, the README's section on the control panel naming the button by its former name, and left.

**`docs/running-stage-by-stage.md`**:

| Where | Was | Is |
| --- | --- | --- |
| 5e, resume | because the answers you gave with `vespera label` can change what it should do … When an answer changes what it removes | because the labels you recorded with `vespera label` can change what it should do … When a label changes what it removes |
| the heading of the relevance report's step | The questions | The documents to label |
| its *What it does* | each waiting for your answer. | each waiting for your label. |
| its *Resume* | from the answers recorded so far. **Record your answers with `vespera label` before you run `vespera run` again** | from the labels recorded so far. **Record your labels with `vespera label` before you run `vespera run` again** |
| `vespera label`, resume | Answer some, run `vespera label`, answer more another day, run it again. The same run always asks about the same documents. Your answers are kept by document | Label some, run `vespera label`, label more another day, run it again. The same run always draws the same documents. Your labels are kept by document |
| `vespera label`, refusals | then answer that one. | then label that one. |
| `vespera label --auto` | has the local model answer the questions in the label file … It never replaces an answer a person gave … while the file holds answers you have not recorded | has the local model label the documents in the label file … It never replaces a label a person set … while the file holds labels you have not recorded |
| an approval is for one arrangement | The same holds when your answers change what the floor removes … going back to the earlier answers arrives at the earlier one again. | The same holds when your labels change what the floor removes … going back to the earlier labels arrives at the earlier one again. |
| the table of what a change redoes, seven rows | your answers | your labels |
| the same, `seedFolder` | the answers given for the old folder stay with the old folder | the labels set for the old folder stay with the old folder |
| what is kept | until you answer the sample again from the new label file | until you label the sample again from the new label file |
| the archive changed | Your recorded answers still apply. | Your recorded labels still apply. |

*conversions already answered*, the converter's, stays.

### 7. `CONTEXT.md`

**Relevance label keeps its term, and its `_Avoid_` line stays *annotation, ground truth, judgement, rating*.** Its definition is reworded so that it calls the label no answer and an entry no question:

> What is recorded about one document: relevant to the seed set, or not. A person's, unless its provenance row names the local model that set it (ADR-197); a person's label always wins over a model's. Not a verdict — it removes nothing and no stage writes it — and not a measurement, because no re-run can produce it a second time. It is a fact about the document rather than about the run that showed it, so it outlives the score that prompted it and the model that computed that score. The label file shows the label already recorded, for the seed set it names, beside each document it lists; a different label set later replaces the recorded one and is reported, and a blank entry takes nothing back (ADR-169). The control panel sets the labels by folder: a document of the label file lying in a folder the operator lists as not relevant is labelled not relevant, and every other relevant (ADR-334).

**Working directory**'s *the folders not relevant the control panel answers it by (ADR-334)* reads *the folders not relevant the control panel labels it by (ADR-334)*; **Folder not walked**'s *whose questions the control panel answers not relevant (ADR-334)* reads *whose documents in the label file the control panel labels not relevant (ADR-334)*.

`answer` is not added to the `_Avoid_` line. Under ADR-122 a rejected synonym in a name of ours is a defect, so the addition would make every name §8 keeps one, and move about a hundred names in `embedding` and `pipeline` that no operator reads.

### 8. What keeps its name

Every stored key and id, and every name of ours:

- `next-invocation.properties`'s `answer`, and `report.json`'s `next.answer` (ADR-294 §1);
- `report.json`'s `label.answers`, each `{words, count}`, and `report.html`'s `data-section="answers"`, with the template's `drawn.answers` and `drawn.next.answer`;
- `relevance-labelling.json`'s section id `what-the-answers-say-so-far`, its column id `asked` and its figure key `sampled`;
- the warning id `<prefix><key>.answers` (ADR-377 §3, ADR-413 §4);
- the classes, records and methods that say *answer* or *question*: `LabelAnswers` and `LabelAnswers.Question`, `LabelFileReader.Answers` and `LabelFileReader.Answer`, `LabelQuestion`, `RelevanceLabeller.answer`, `RelevanceLabels.answerFor` and `modelAnswers`, `NextAction.answersRecordedAgainst`, `NextInvocation.answer`, and the rest.

A stored text the operator is shown is not a key: the count `answered by a person`, a `words` of `label.answers` that `report.html` draws, is `labelled by a person` (§3). No schema of `docs/report/` changes: none enumerates a count's words or a section's title.

### 9. What changes, by class

- **`VesperaCommand.Label`**: §2's two descriptions.
- **`LabelFileReader`**, **`LabelIngestion`**, **`AutoLabelling`**, **`NextAction`**: §3's words.
- **`report.html.mustache`**: §4's four texts.
- **`RelevanceLabellingReport`**: §4's texts, in the page and in the JSON alike.
- **`NotRelevantPane`**, **`LabelAnswers`**: §5's three texts.
- **`README.md`**, **`docs/running-stage-by-stage.md`**: §6.
- **`CONTEXT.md`**: §7.

No stage's rule changes: a report's words and a line's words decide no verdict, cache key, cluster or text of the deliverable (ADR-222), so no stage's version changes and no run id moves.

## Consequences

- The window, the command line, the reports and the operator's documents speak of one file in one way: the operator sets the labels of the documents to label in `relevance-labels.yaml`; the window saves them and the command line records them.
- A script that matches `vespera label recorded nothing:` still matches it. One that matches what follows the colon, or `vespera label`'s line of what it recorded, meets *label(s)* where it met *answer(s)*.
- The control panel reads `next-invocation.properties` and `report.json` as it did: no key moved.
- The code and the operator's texts say different words for the same thing: a label is `answerFor` in `RelevanceLabels` and *label* on the console. That is ADR-122's line between a name of ours and a text for a reader outside the project.

## Alternatives refused

- **Keeping *answer* for the `relevant` of one entry.** The window already calls it a label; a second word for the same value is the split this record closes.
- **Keeping *question* for an entry of the label file.** The operator refused the window being a question and answer; *question* is the other half of that frame.
- **Adding *answer* to Relevance label's `_Avoid_` line.** §7: the names it would make defects are read by no operator.
- **Saying *save* on the command line, as the window does.** `vespera label` records into the ledger, and every line it ends on says *recorded*; the window's Save my labels starts that command.
- **Renaming the stored keys with the words.** A rename of `answer` or `label.answers` is a change of the files' format that the control panel reads, for a word no operator reads.

## Tests

| Method | What it holds |
| --- | --- |
| `TheVocabularyCallsNoRelevanceLabelAnAnswerTest.theRelevanceLabelIsDefinedWithNoAnswerAndNoQuestion` | `CONTEXT.md`'s Relevance label opens with §7's first sentence, says neither *answer* nor *question* in any form, and keeps its `_Avoid_` line, which holds no *answer* (§7) |
| `TheVocabularyCallsNoRelevanceLabelAnAnswerTest.theWorkingDirectoryAndAFolderNotWalkedSayTheControlPanelLabels` | Working directory and Folder not walked say §7's words, and not the ones they replace (§7) |
| `TheOperatorsDocumentsCallNoLabelAnAnswerTest.theReadmeSaysLabelWhereItSaidAnswer` | each row of §6's README table and §2's line under *Commands*: the README holds what it is, and not what it was (§2, §6) |
| `TheOperatorsDocumentsCallNoLabelAnAnswerTest.runningStageByStageSaysLabelWhereItSaidAnswer` | each row of §6's table for `docs/running-stage-by-stage.md`, the seven rows of *your answers* among them: the file holds what it is, and not what it was (§6) |
| `pipeline.TheLabelCommandCallsNoLabelAnAnswerTest.theUsageSaysLabels` | picocli's model of `vespera label` describes it, and its `--auto`, in §2's words (§2) |
| `pipeline.TheLabelCommandCallsNoLabelAnAnswerTest.aLabelFileRefusedSaysLabels` | `LabelFileReader.read`'s three refusals, a file naming no run, another run, and no label set, read as §3 has them (§3) |
| `pipeline.TheLabelCommandCallsNoLabelAnAnswerTest.theClosingLinesSayLabels` | the closing line of a run that wrote the label file and recorded no label ends on §3's *Next: label the documents …*; of one that wrote none, says *No documents to label were written …*; with one recorded, says *1 label is recorded*, and with sixty, *60 labels are recorded*; none says *answer* or *question* (§3) |
| `pipeline.TheLabelCommandCallsNoLabelAnAnswerTest.theReportsLabelSectionSaysLabels` | the shipped `report.html.mustache` draws the label section as §4 has it, its id and keys as they were, and the next invocation's line *Labels are wanted.* (§4, §8) |
| `pipeline.TheLabelCommandsLinesCallNoLabelAnAnswerInvocationTest.theLabelCommandsLinesSayLabels` | over a scored corpus of one document: `vespera label` with its one entry false says *recorded 1 label(s) about the seed set at ‹p›: 1 new, 0 changed, 0 unchanged*; `vespera label --auto` after it says §3's line with *1 already labelled by a person*, and its `report.json` counts `relevant`, `not relevant`, `left blank` and `labelled by a person` in that order, under `label.answers`; a label typed and not recorded makes `--auto` refuse with *the label file holds 1 label(s) not yet recorded; run vespera label first* (§3, §8) |
| `pipeline.LabelFileShowsRecordedAnswersInvocationTest.aFileWrittenUnderAnotherSeedSetIsRefused`, its words changed | the refusal of a file about another seed set ends *labels about one seed set are not labels about another, so nothing in it was recorded* (§3) |
| `pipeline.TheRelevanceLabellingReportCallsNoLabelAnAnswerTest.withNothingLabelledThePageAndItsJsonSayLabels` | with nothing labelled, the page and the JSON say §4's texts: the headline *Documents to label*, the column *To label*, *What the labels say so far*, *No document has been labelled yet.*, *the same run always draws the same documents*, and neither says *answer* or *question* but in *The answer is a different seed folder*; the ids `asked` and `what-the-answers-say-so-far` stay (§4, §8) |
| `pipeline.TheRelevanceLabellingReportCallsNoLabelAnAnswerTest.aThresholdNotAppliedSaysTheLabelsWereSetUnderAnotherModel` | with a threshold not applied, the page and the JSON say *The labels this number was read off were set while ‹model› was in use* (§4) |
| `ui.TheControlPanelsWindowTest.theCorpusRootAsAFolderNotRelevantIsRefusedInLabelsWords` | the corpus root typed as a folder not relevant is refused with §5's line (§5) |
| `ui.LabelAnswersCallsNoDocumentAQuestionTest.aRewriteRefusedSaysDocumentToLabel` | `LabelAnswers.rewrite` with no label file, and with a path the file does not list, throws with §5's two lines (§5) |

## What the implementation owes

§9, with each test above green; nothing else moves.

## What this does not decide

- **The other senses of *answer*.** A sidecar's, the converter's, a model's, a dialog's and a profile value's answers, as §1 lists them, stay. A text that mixes the senses is read by what its *answer* names.
- ***Judge*.** `relevance-labelling.html`'s *The documents to judge*, *Judged* and *Judged relevant*, and a profile row's *documents for you to judge*, are no question and answer, and stay.
- **`docs/architecture.md`, the javadocs and `docs/report/`'s schemas.** They describe the code to a reader of the code, and keep its words.
- **The names of ours.** §8: no rename of a class, a method or a key follows from this record.
