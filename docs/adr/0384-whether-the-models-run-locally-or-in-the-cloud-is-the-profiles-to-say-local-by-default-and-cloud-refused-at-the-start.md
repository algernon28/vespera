# ADR-384 — Whether the models run locally or in the cloud is the profile's to say, local by default, and cloud is refused at the start

- **Date**: 2026-11-13
- **Status**: accepted on 2026-11-13.
- **Decided**: by the operator, on 2026-11-12, that "the decision to use a local or a cloud model should only be via configuration by the operator"; that the configuration "only says which one, cloud still refused for now"; and, of that configuration, "Have it greyed out on the UI, and comment it on the yaml" (§1 to §7). By Claude under the operator's delegation: that the configuration is one profile key, `modelsRunOn`, for every model, taking `local` and `cloud`, local where unset, any other value refused (§1); where the start of `vespera run` reads it, and its lines (§2); what it means for `vespera label --auto` and its labeller (§3, §4); what becomes of the step's check and of `ModelPull` (§5); the comment `profile.yaml` holds, its place and its words, and the control panel's reading of a comment (§6); the Profile tab's row, its control, its holder and its texts (§7); where each part lives (§8); and which run ids move (§9).
- **Amends**: [ADR-375](0375-a-model-whose-check-cannot-be-completed-is-taken-as-local-and-a-cloud-model-is-refused-at-the-start-of-vespera-run.md) §1, whose line takes a model *as local*: no model is taken as anything, and the line says Ollama could not say whether it serves the model (§5); §2, whose start checks each model's tag and asks `/api/show` whether Ollama forwards it: the start reads the profile's `modelsRunOn` alone, asks Ollama nothing, reads no model's name, and comes before the corpus-root lock and the sidecars' start (§2); §3, whose step still refuses a cloud tag and a model reported as remote: neither is refused anywhere (§5); §4, whose `ModelPull` reads the check's reason from its words: it asks Ollama whether it serves the model and pulls on a 404 whatever the name (§5); §5, whose `vespera label --auto` keeps its model checks: it refuses at its start on `modelsRunOn` alone, and its labeller reads its model's digest alone (§3, §4); §7, whose `embedding` is not touched: it is, and four stages' versions move (§9); and §8, whose cloud models have no configuration: `modelsRunOn` is it, and `cloud` is refused (§1, §2). Its §6 stands. It carries a note at its head.
- **Amends**: [ADR-202](0202-generation-and-embedding-refuse-an-ollama-model-not-served-on-this-machine-before-anything-is-sent.md) §1 and §4, whose one check, `LocalOllamaModel.refusalOf`, the labeller and both steps share: it goes, the steps asking `OllamaClient.serves` through `ModelPull` and the labeller reading its digest alone (§4, §5, §8); and §3's first and second cases, a tag ending in `cloud` and a model `/api/show` reports as remote: neither refuses (§5). §3's third case stands in its words; its fourth is ADR-375's, in §5's line. §2 and §5 stand. It carries a note at its head.
- **Amends**: [ADR-329](0329-vespera-pulls-into-ollama-at-the-step-that-needs-it-a-model-ollama-does-not-serve-and-never-pulls-one-it-serves.md) §3, whose pull is sent only where *the name does not end in `cloud`*, and whose check is asked before and after it: a model Ollama does not serve is pulled whatever its name, and what is asked before and after is whether Ollama serves it (§5). It carries a note at its head.
- **Amends**: [ADR-197](0197-a-local-model-answers-the-relevance-questions-and-the-floor-is-a-rule-over-the-labels-so-no-document-reaches-a-hosted-model.md) §6's second and third cases, a tag ending in `cloud` and a model Ollama reports as remote, and a model whose answer to `/api/show` cannot be read: the labeller refuses for none of them, and `vespera label --auto` refuses at its start a profile whose `modelsRunOn` is not local (§3, §4). Its first case, an endpoint not this machine, and its refusal of a digest that cannot be read, stand. It carries a note at its head.
- **Amends**: [ADR-062](0062-census-merges-new-profile-keys-and-never-touches-an-existing-value.md)'s Decision, whose census adds a key the file lacks as `value: null`, `provenance: null`: `modelsRunOn`, while unset, is written as three lines of comment in its place (§6). The merge stands. It carries a note at its head.
- **Amends**: [ADR-295](0295-the-control-panel-writes-profile-values-label-answers-and-code-set-names-holding-the-working-directory-lock-one-operator-action-at-a-time.md) §3's form read, which holds no comment: a whole line that is a comment, at any indent, is in the form and every write keeps it; a comment after a key or a value is still not (§6). It carries a note at its head.
- **Amends**: [ADR-330](0330-the-control-panel-edits-every-value-of-profile-yaml-showing-the-value-in-use-and-where-it-came-from-and-ten-technical-choices-ship-as-presets-in-application-yaml.md) §1, whose control panel edits every value of `profile.yaml`: `modelsRunOn` is shown and not edited (§7); and §6, whose repair removes a whole line that is a comment: such a line is in the form, and the repair keeps it (§6). It carries a note at its head.
- **Read with**: [ADR-377](0377-a-profile-row-is-one-line-its-provenance-in-a-tooltip-a-list-is-tags-a-code-set-named-by-its-folder-and-no-label-file.md) §3 and §4: `modelsRunOn`'s row is a row of §3, its control disabled in a holder carrying the tooltip, and its texts are §7's, in `profile-texts.json` once that file is built. It carries a note at its head.
- **Keeps**: [ADR-195](0195-the-reference-model-and-every-bake-off-candidate-are-served-locally.md) and [ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md): the archives' documents are read only by local models, which is now what the operator's configuration states, `local` being the only value an invocation goes on under (§1, Consequences).
- **Keeps**: [ADR-323](0323-vespera-starts-the-docker-engine-and-the-sidecars-when-they-are-not-answering-recreates-and-stops-none-and-stage-2-waits-for-a-converter-still-starting.md) §1, [ADR-330](0330-the-control-panel-edits-every-value-of-profile-yaml-showing-the-value-in-use-and-where-it-came-from-and-ten-technical-choices-ship-as-presets-in-application-yaml.md) §9 and [ADR-141](0141-the-cli-exits-with-the-commands-exit-code-and-the-scheduler-no-feature-uses-is-removed-at-its-source.md): the refusals at the start keep their order and their lines, the new one right after the presets', and it exits 1 as they do (§2).
- **Keeps**: [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md) §5 and [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md): no stage's version names `profile`, `pipeline` or the control panel, and the key enters no run's identity (§9).

## Context

**Vespera reads today whether a model runs on this machine.** ADR-202 put every model it sends text to through one check, `LocalOllamaModel.refusalOf` in `embedding`, which refuses a tag ending in `cloud` from the name alone and a model whose `/api/show` answer carries `remote_host` or `remote_model`, read by `OllamaClient.isRemote`. ADR-375 asked the same check at the start of `vespera run` for the embedding and the generation model, kept it at stage 5's scoring step and the generation step for what the start could not see, and took a check Ollama could not complete as local. `ModelPull` in `pipeline` reads the check's reason from its words, and declines to pull a name ending in `cloud` (ADR-329 §3). The labeller, `OllamaRelevanceLabeller` in `embedding`, calls the check at the start of `vespera label --auto` (ADR-197 §6). ADR-375 §8 left configuration for cloud models to a later record.

**The operator decided, on 2026-11-12**, that the choice of a local or a cloud model is made only by the operator's configuration, that the configuration only says which one, cloud still being refused for now, and that it is greyed out in the control panel and commented in the YAML.

**Where the configuration can live.** The profile, `profile.yaml` in the working directory, holds the values the operator gives one archive, each with how it was arrived at (ADR-061), the two models among them, `embeddingModel` and `generationModel`; the control panel's Profile tab draws a row for each of its keys (ADR-330 §1), as ADR-377 redraws them. The application's configuration, `application.yaml` inside the jar, overridden from the environment or the command line, holds the presets of one machine (ADR-330 §9).

**How `profile.yaml` is written.** The jar writes it whole, through Jackson, from the `Profile` record, every key present and an unset one as `value: null` and `provenance: null` (ADR-061, ADR-062), and keeps no comment. The control panel rewrites one key's lines and keeps every other byte, in a file it reads only in the form the jar writes it (ADR-295 §3), which holds no comment: a file holding one is refused, and its repair removes a whole line that is one (ADR-330 §6). A key written flat, `seedFolder: D:\archive`, is refused by both, as the README says.

**What a change of each module moves.** `StageModules` names `embedding` in the versions of seed measurement, embedding scoring, the arrangement and generation. No stage's version names `profile` (ADR-274 §5), `pipeline` (ADR-222) or the control panel.

## Decision

### 1. One profile key, `modelsRunOn`, says where the models run

**`modelsRunOn` is a key of the profile**, a `TextValue`, the component right after `generationModel`. It is a profile key and not a property because it is said of the models the profile names, beside them, with how it was arrived at, and because the Profile tab is where the operator sees the profile's values; a property would live in a file inside the jar or in the environment, and the Profile tab draws no property.

**It is one key, for every model Vespera sends a document's text to**: the embedding model, the generation model and the labeller's, `vespera.label.model`. A key for each would be three answers to one question while one answer is accepted.

**It takes `local` or `cloud`**, read as `deliverableLanguage` is, the blanks around it ignored and in any case:

| `modelsRunOn` | What it means |
| --- | --- |
| unset or blank | local: the models run on this machine |
| `local` | the same |
| `cloud` | the models run in the cloud, which is not supported yet: the invocation is refused at its start (§2, §3) |
| anything else | refused at the start too: no reading is guessed for a value that is neither |

It enters no run's identity, since `local` is the one value an invocation goes on under; it is no gate and has no `Measurement` pointer. The deliverable's index states it as it states every key (ADR-186, ADR-359 §7), empty where it is unset.

**Vespera never infers whether a model is local or in the cloud.** No model is refused for its name, a tag ending in `cloud` among them, nor for what `/api/show` says of where it runs, at the start of `vespera run`, at stage 5's scoring step, at the generation step or in `vespera label --auto`. Where `modelsRunOn` is unset or `local`, every model the profile and the configuration name is taken to run on this machine, as the operator states.

### 2. The start of `vespera run` reads the key, and nothing else

`ModelsAtTheStart.refusal()` reads the profile's `modelsRunOn` and answers §1's refusal, or nothing. It reads no model's name and asks Ollama nothing. A profile that cannot be read refuses nothing here and is left to the job's own refusal of it, as ADR-375 §2 has it.

**The lines**, one on standard error, the invocation's report recording it as it records every refusal at the start, and exit code 1:

```
vespera run stopped at its start: modelsRunOn is cloud, and models that run in the cloud are not supported yet, so nothing was walked and no run was minted -- set modelsRunOn to local in profile.yaml, or leave it unset, and the models run on this machine
```

and, for any other value, `<value>` being the value as written, the blanks around it removed:

```
vespera run stopped at its start: modelsRunOn is "<value>", which is neither local nor cloud, so nothing was walked and no run was minted -- set modelsRunOn to local in profile.yaml, or leave it unset, and the models run on this machine
```

**Where.** Right after the presets' refusal (ADR-330 §9), before the corpus-root lock is taken, the sidecars are started (ADR-323 §1), the file of ADR-330 §1 is written and the job is started, so that a profile saying cloud takes no lock, starts no container, walks nothing and mints no run. ADR-375 §2 placed it after the sidecars so that the Ollama just started was the one asked; nothing is asked now. The refusals before it keep their order and their lines.

**A value saved during an invocation** is read at the start of the next one: no step reads the key.

### 3. `vespera label --auto` reads it at its start

`vespera label --auto` asks `ModelsAtTheStart.refusal()` first, before the sidecars' start and before the labeller is asked whether it may be used, and where it answers ends with one line on standard error, exit code 1, no question put and the label file byte for byte as it was:

```
vespera label --auto recorded nothing: modelsRunOn is cloud, and models that run in the cloud are not supported yet -- set modelsRunOn to local in profile.yaml, or leave it unset, and the models run on this machine
```

or, for any other value, `vespera label --auto recorded nothing: modelsRunOn is "<value>", which is neither local nor cloud -- ` and the same advice. `vespera label <file>` and `vespera publish` send no model anything and do not read the key.

### 4. The labeller asks Ollama for its model's digest alone

`OllamaRelevanceLabeller` refuses in two cases, in this order:

1. **Its endpoint is not this machine** (ADR-197 §6's first case, unchanged). It reads the address the configuration names for Ollama, not the model, and an address that is not this machine is the configuration saying the models run elsewhere, which is refused for now.
2. **Its model's digest cannot be read from `/api/tags`**: Ollama lists no model of that name, lists one with no digest, or does not answer. The refusal reads *the identity of the model `<name>` could not be read from Ollama (`<cause>`), so none of its answers could be recorded*, an identity with no digest being one that cannot be recorded (ADR-197 §3).

It asks `/api/show` nothing, and reads no tag.

### 5. The step asks Ollama whether it serves the model, and nothing of where it runs

At stage 5's scoring step and the generation step, `ModelPull.refusalOf` asks `OllamaClient.serves(name)`, which posts `/api/show` and answers `true` for a 2xx answer and `false` for a 404, and raises for any other answer or none. It reads nothing of the answer's body.

- **Served**: no reason, and the step goes on.
- **Not served**: where pulling is on, the model is pulled as ADR-329 §3 to §8 pull it, **whatever its name**, and `serves` is asked again: served, the step goes on; not served, the step is stopped with ADR-202 §3's third reason, unchanged, *Ollama serves no model named `<name>`: it has not been pulled on this machine, so nothing is sent to it*. Where pulling is off, it is stopped with that reason at once. A pull that fails stops it as ADR-329 says.
- **No answer**, whether the first question or the one after a pull: Ollama is asked nothing more, nothing is pulled, and the step goes on, as ADR-375 §1 has it, writing one line at WARN in its own voice:

```
<stage> could not establish whether Ollama serves the model <name>, so nothing is pulled and the step goes on
```

An Ollama that is down is then met by reading the identity from `/api/tags` or by the first call, as ADR-375 §1 says.

### 6. `profile.yaml` holds the key commented out while it is unset

**The jar's `ProfileStore.save`** writes `modelsRunOn`, where its value and its provenance are each unset or blank and it has no measurement, as these three lines in its place, right after `generationModel`'s lines and before `generationContextWindow`'s:

```
# Where the models run: local, on this machine. Cloud is not supported yet.
# modelsRunOn:
#   value: local
```

Where the key holds a value or a provenance, it is written as every key is (ADR-061), and the three lines are not. So a profile census writes for a new working directory holds them, and so does every profile the jar writes again while the operator has not set the key.

They are three lines so that the last two, uncommented, are a key in ADR-061's form, which the jar and the control panel read; a single line `# modelsRunOn: local`, uncommented, is a key written flat, which both refuse. No line carries a comment after a value, which the control panel does not read. No other YAML holds the key: `application.yaml` holds no profile key, and no profile ships in the jar.

The jar reads a file holding the three lines as a profile whose `modelsRunOn` is unset, YAML taking a comment for nothing; the two lines uncommented read as `local`, and `value: cloud` as `cloud`, which §2 refuses.

**The control panel's form** (ADR-295 §3) admits a whole line that is a comment, its first character after any spaces `#`: at the margin it belongs to no key's block, and inside a block to that block. Every write keeps it, byte for byte: `ProfileEdit.rewrite` replaces only a key's own lines, and `ProfileEdit.without` takes out a block's lines and keeps a comment at the margin after them. `ProfileEdit.read` states nothing for a key whose lines are all comment. A comment after a key or after a value on its line is still outside the form, refused and repaired as ADR-330 §6 says; the repair removes no whole line that is a comment, since such a line is in the form.

### 7. The Profile tab shows the row greyed out

**`ProfileKey` gains `MODELS_RUN_ON`**, `modelsRunOn`, after `GENERATION_MODEL`: of kind `TEXT`, entering no run, its example `local`, with no value beneath it (`hasAValueBeneath()` false), and its hint the README's row's second cell, *nothing: the models run on this machine, `local`, and `cloud` is not supported yet*.

**Its row** is drawn as every row of the tab, ADR-377 §3's once that is built, with one difference, its control:

- **The control**, `<prefix>modelsRunOn`, a `ComboBox<String>` offering `local` and `cloud`, **disabled**. It shows the value the file states, as written, where it states one that is not blank, and `local` where it states none, so that the operator sees the value the next start goes on under or refuses.
- **Its holder**, `<prefix>modelsRunOn.holder`, the `HBox` it stands in on the row's line, is not disabled and carries the tooltip **Cloud is not supported yet.**, since JavaFX shows no tooltip over a disabled control.
- **No Save writes it**: the tab's Save passes over the row, whatever the control shows, so that a key the file holds commented out stays so. A load or a Reset draws the control's value from the file again. A value is changed in `profile.yaml` alone.

It is no row of `wanted`, since it is no gate. Its cost line is its own, since the window does not change it: *It cannot be changed here; Vespera checks it each time it starts, and it redoes no work.* ADR-377 §4's table holds it.

**Its texts.** Its title is *Where the models run*. Its short description: *The AI models that read your documents run on this machine; running them in the cloud is not supported yet.* Its tooltip: *Vespera sends your documents' text only to AI models that run on this machine. Running them in the cloud is not supported yet, so this value cannot be changed here; profile.yaml shows it as a comment.* They stand in `profile-texts.json` once ADR-377 §4 builds it; until then `plainName()` is the title and `about()` the short description.

### 8. Where each part lives

- **`profile`**: `Profile` gains `modelsRunOn`; `ProfileStore.save` writes §6's three lines.
- **`pipeline`**: `ModelsAtTheStart`, built by Spring from `ProfileStore` alone, holds §1's reading and answers §2's and §3's reason; `VesperaCommand.Run` asks it where §2 places it, and `VesperaCommand.Label` where §3 places it, each taking it as an optional collaborator, as `Run` takes it today; `ModelPull` holds §5, names `OllamaClient` alone, and holds the never-pulled reason's words and the warning's.
- **`embedding`**: `LocalOllamaModel` goes; `OllamaClient.isRemote` gives way to `OllamaClient.serves`, its javadoc saying what §5 says; `OllamaRelevanceLabeller` holds §4, its javadoc corrected with it.
- **The control panel**: `ProfileKey.MODELS_RUN_ON`, `ProfileRow`'s control and holder, the Save passing over it, `ProfileEdit`'s form and `ProfileRepair` (§6, §7).
- **The README**: its table of values gains `modelsRunOn`'s row, which `docs/check-claims.mjs` reads against `Profile`, with a sentence saying the models run on this machine and cloud is not supported yet.

### 9. Run ids

The key enters no run's identity, and `profile`, `pipeline` and the control panel are named by no stage's version (ADR-274 §5, ADR-222): `ModelsAtTheStart` and `ModelPull` decide no verdict, cache key, cluster or text of the deliverable. The index's new row is drawing and keys no run (ADR-274 §5).

**`embedding` changes, so the versions of seed measurement, embedding scoring, the arrangement and generation move**, as `StageModules` names them; byte-level reduction, extraction, content census and content redundancy do not. Nothing these four stages record changes with it.

## Alternatives rejected

- **A property, `vespera.models.run-on`, commented in `application.yaml`.** It would be said once for every archive of a machine, away from the models it is said of, in a file inside the jar the operator does not edit, and the Profile tab draws no property.
- **A key for each model.** Three answers to one question, of which one is accepted; a later record may split it once cloud is supported.
- **A boolean, `cloudModels: false`.** It says whether, not which one; a third place would need another key.
- **Keeping the tag and `remote_host` checks as a backstop at the step.** The decision is that the configuration alone says it.
- **Leaving `embedding` as it is until a change of its own touches it**, `vespera label --auto` keeping its tag and `/api/show` checks meanwhile, so that no run id moves now. The labeller would go on inferring where its model runs, which the decision rules out in `vespera label --auto` as everywhere.
- **Writing the key uncommented, `value: local`.** It would read as a value the operator set, with no provenance of theirs; and the YAML was to hold it commented.
- **One line, `# modelsRunOn: local   # cloud is not supported yet`.** Uncommented, it is a key written flat, which the jar refuses, and its value carries a comment, which the control panel does not read.
- **Hiding the row, or disabling the row whole.** It was to be greyed out, its value seen; and a tooltip on a disabled node does not show.
- **Taking an unreadable value as local.** Where the documents go is not guessed from a value that names neither.

## Consequences

- **Where the models run rests on what `profile.yaml` states.** A model Ollama forwards to a hosted service, a tag ending in `cloud` among them, is sent text where `modelsRunOn` is unset or `local`; Vespera no longer refuses it on its own reading. The documents are read only by local models as far as the configuration states it.
- **A profile saying cloud is refused before the corpus-root lock and the sidecars' start**, in one line, exit code 1, and `vespera label --auto` likewise before it starts the sidecars.
- **The start asks Ollama nothing**, so it waits for no answer from it.
- **A model Ollama does not serve is pulled whatever its name**, a tag ending in `cloud` among them.
- **A step still goes on where Ollama cannot say whether it serves the model**, with one warning.
- **A new profile holds three lines of comment**, and keeps them until the operator sets the key; the control panel keeps every whole line that is a comment, the operator's own among them, and its repair no longer removes one.
- **The Profile tab shows where the models run, greyed out**, with its tooltip, and its Save never writes it.
- **The next `vespera run` mints new runs of seed measurement, embedding scoring, the arrangement and generation** (§9).

## What this does not decide

- **How a cloud model is named, reached and paid for** once it is supported: a later record, which also says what `cloud` lets through.
- **An Ollama address not this machine for the embedding and generation models**, which ADR-202 §5 left to the labeller alone.
- **The read timeout on `/api/show`**, which ADR-375 §6 keeps.

## Tests

| Method | What it holds |
| --- | --- |
| `pipeline.EmbeddingRefusesAModelNotServedHereInvocationTest.cloudInTheProfileIsRefusedAtTheStartBeforeTheWalk` | §1, §2: `modelsRunOn` `cloud`, ` Cloud ` and `on-premises` each end `vespera run` with exit code 1 and §2's line, whole, once on standard error, nothing walked and Ollama asked about no model |
| `pipeline.EmbeddingRefusesAModelNotServedHereInvocationTest.noModelIsRefusedForItsNameOrForAnUnansweredQuestion` | §1, §5: an embedding model whose tag ends in `cloud`, with `modelsRunOn` unset, and one Ollama cannot answer about, with `modelsRunOn` `local`, are each scored: exit code 0, one scoring run minted, chunks sent, no refusal at the start and no line at ERROR |
| `pipeline.GenerationRefusesAModelNotServedHereInvocationTest.aCloudTaggedGenerationModelDoesNotStopTheStart` | §1, §2: a generation model whose tag ends in `cloud` does not stop the start: the invocation ends at the approval with exit code 0, the corpus walked once, Ollama not asked about it |
| `pipeline.ModelPullTest.whereAModelRunsIsReadNeitherFromItsNameNorFromOllama` | §5: a model Ollama answers for as forwarded is not refused or pulled, asked about once; a model whose tag ends in `cloud` that Ollama does not serve is pulled by its name and not refused; nothing at WARN |
| `pipeline.ModelPullTest.aCheckOllamaCannotCompleteLetsTheStepGoOn` | §5: a 500 from `/api/show` and an Ollama nobody listens for each let the step go on, asked once and nothing pulled, with §5's line, whole, at WARN, once each |
| `embedding.LocalLabellerTest.theLabellerAsksOllamaForTheModelsDigestAlone` | §4: a model whose tag ends in `cloud` that `/api/tags` lists answers, its identity carrying the digest; a model it does not list is refused in §4's words with no call made; Ollama asked `/api/tags` alone |
| `pipeline.RefusingLabellerInvocationTest.cloudInTheProfileEndsLabelAutoAtItsStart` | §3: `modelsRunOn` `cloud` ends `vespera label --auto` with exit code 1 and §3's line, whole, once on standard error, the labeller asked nothing and the label file byte for byte as it was |
| `profile.ProfileStoreTest.writesACompleteSkeletonForAFreshCorpus` | §6: the file census writes names every key at its top level but `modelsRunOn`, whose three lines stand right before `generationContextWindow` |
| `pipeline.TheJarReadsWhatTheControlPanelWritesTest.theCommentedKeyTheJarWritesIsKeptByTheControlPanel` | §6: the control panel reads the jar's file holding the three lines, states no `modelsRunOn`, keeps the lines through a rewrite and a `without`, and the jar loads what it wrote with `modelsRunOn` unset |
| `ui.WhereTheModelsRunIsShownAndNotEditedTest.theRowShowsItsValueGreyedOutAndASaveWritesNothingOfIt` | §7: the control shows `local` and is disabled, its holder is not and carries *Cloud is not supported yet.*, and a Save leaves the file byte for byte as it was |

Amended:

- **`ui.ProfileKeyTest.aChangeCostsItsRunAndEveryLaterOne`** gains `modelsRunOn`, costing no run (§7).
- **`ui.ProfileEditTest.aFileInAnotherFormIsRefusedWhole`** loses its case of a whole line that is a comment (§6).
- **`ui.EveryTextOfTheWindowCanBeCopiedTest.everyTextShownOutsideAControlIsSelectableText`**'s file not in the form holds a comment after a key, in place of a whole line that is a comment (§6).
- **`PipelineHoldsOnlyTheRulesOnRecordTest`** has `ModelPull` name `embedding.OllamaClient` alone and `ModelsAtTheStart` name `profile.Profile`, `profile.ProfileStore` and `profile.TextValue` (§8).
- **`embedding.LocalOllamaModelTest`** is deleted with the class it held (§8).
- **`EmbeddingScriptedBeans`**' client overrides `serves` in place of `isRemote` and scripts no model as forwarded; `ProfileFixture` gains `modelsRunOn`.

`EmbeddingRefusesAModelNotServedHereInvocationTest.anEmbeddingModelNotServedHereIsRefusedBeforeAnyChunk` and `ModelPullTest`'s other tests stand unchanged.

## What the implementation owes

Beside the tests above, which it turns green: §8 whole; and, where ADR-377 §4's `profile-texts.json` is built, `modelsRunOn`'s three texts of §7 in it. The notes at the heads of ADR-375, ADR-202, ADR-329, ADR-197, ADR-062, ADR-295, ADR-330 and ADR-377 are written with this record.
