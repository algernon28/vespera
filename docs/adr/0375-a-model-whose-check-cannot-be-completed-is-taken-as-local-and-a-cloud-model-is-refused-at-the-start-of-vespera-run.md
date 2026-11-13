# ADR-375 — A model whose check cannot be completed is taken as local, and a cloud model is refused at the start of `vespera run`

> **Partly amended — see [ADR-384](0384-whether-the-models-run-locally-or-in-the-cloud-is-the-profiles-to-say-local-by-default-and-cloud-refused-at-the-start.md).** Of §1: no model is taken as local or as anything, and the step's line reads *`<stage>` could not establish whether Ollama serves the model `<name>`, so nothing is pulled and the step goes on*. Of §2: the start reads the profile's `modelsRunOn` alone, asks Ollama nothing and reads no model's name, and refuses `cloud`, or any value but `local`, right after the presets' refusal and before the corpus-root lock and the sidecars' start, in ADR-384 §2's line, exit 1. Of §3: no step refuses a tag ending in `cloud` or a model Ollama reports as remote; a model Ollama does not serve is refused as before. Of §4: `ModelPull` asks `OllamaClient.serves`, pulls on a 404 whatever the name, and reads no reason's words. Of §5: `vespera label --auto` refuses at its start on `modelsRunOn`, and its labeller reads its model's digest alone. Of §7: `embedding` changes, and the versions of seed measurement, embedding scoring, the arrangement and generation move. Of §8: `modelsRunOn` is the configuration, and `cloud` is refused. Of Tests: the tests of §2 and §3 are ADR-384's. §6 stands.

- **Date**: 2026-11-12
- **Status**: accepted on 2026-11-12.
- **Decided**: by the operator, that a model whose check cannot be completed is taken as local, and that a cloud model is refused earlier in the invocation than the step that would send to it; and that configuration for cloud models may be added by a later record and is not added now. Where the start's refusal lies, its line and exit code, which models it checks, what it means for `vespera label --auto` and for a model named in a later invocation's profile, what becomes of the step's check and of `ModelPull`, and the read timeout on `/api/show` were decided by Claude under the operator's delegation.
- **Amends**: [ADR-202](0202-generation-and-embedding-refuse-an-ollama-model-not-served-on-this-machine-before-anything-is-sent.md) §3's fourth case, *"The check fails closed … a model whose remoteness cannot be established is refused, never assumed local"*, for stage 5's scoring step and the generation step: such a model is taken as local there (§1). Its §2, *"Embedding scoring and generation each ask once per invocation"*: `vespera run` asks too, at its start, for every model it names (§2). Its Consequences' *"An invocation that reaches embedding scoring needs Ollama up"* and §3's *"Failing closed costs nothing"*: an Ollama that is down no longer stops the step at the check, and is met at the first call that needs it (§1). §1's one implementation of the rule, `LocalOllamaModel.refusalOf`, is unchanged.
- **Amends**: [ADR-329](0329-vespera-pulls-into-ollama-at-the-step-that-needs-it-a-model-ollama-does-not-serve-and-never-pulls-one-it-serves.md) §3, its step 4's *"Any other answer, 2xx, another status or none, means the reason was something else … and the check's reason stands"*: where the check's reason is that the model's place could not be established, it no longer stands, Ollama is not asked again, nothing is pulled, and the step goes on (§4). A 404 is still the one answer from which a model is pulled.
- **Keeps**: [ADR-197](0197-a-local-model-answers-the-relevance-questions-and-the-floor-is-a-rule-over-the-labels-so-no-document-reaches-a-hosted-model.md) §6 whole: `vespera label --auto` refuses its model at its own start, as it did, and still refuses one whose check cannot be completed (§5).
- **Keeps**: [ADR-195](0195-the-reference-model-and-every-bake-off-candidate-are-served-locally.md) and [ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md): the archives' documents are read only by local models. A model whose tag ends in `cloud`, or that Ollama reports as remote, is sent nothing on any path (§2, §3).
- **Keeps**: [ADR-323](0323-vespera-starts-the-docker-engine-and-the-sidecars-when-they-are-not-answering-recreates-and-stops-none-and-stage-2-waits-for-a-converter-still-starting.md) §1 and §3, [ADR-330](0330-the-control-panel-edits-every-value-of-profile-yaml-showing-the-value-in-use-and-where-it-came-from-and-ten-technical-choices-ship-as-presets-in-application-yaml.md) §1 and §9, and [ADR-141](0141-the-cli-exits-with-the-commands-exit-code-and-the-scheduler-no-feature-uses-is-removed-at-its-source.md): the refusals at the start keep their order, the new one comes after the sidecars are started and before the file of ADR-330 §1 is written, and it exits 1 as they do (§2).
- **Keeps**: [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md) and [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md): every class this record changes is in `pipeline`, and `embedding` is not touched, so no run id moves (§7).
- **Settles**: [#725](https://github.com/algernon28/vespera/issues/725).

## Context

ADR-202 put every model Vespera sends text to through one check, `LocalOllamaModel.refusalOf`, asked by stage 5's scoring step and the generation step once their gates are open and before their run is resolved, and by the labeller before it opens the label file. The check refuses in four cases: a tag that ends in `cloud`, decided from the name; a model `/api/show` reports as remote; a 404, a model never pulled, which ADR-329 pulls where pulling is on; and any other failure to answer, which **fails closed**: *"it could not be established that the model `<name>` runs on this machine (`<cause>`), and a model is not assumed to be local"*.

Failing closed meets an Ollama that is busy as one that is down. Ollama serving a model it has kept in memory for hours of scoring, and releasing it to load another, can take longer to answer `/api/show` than the client waits: the read timed out at about 10 s, twice, once for the check and once for `ModelPull`'s second question, each as a reactor-netty `ReadTimeoutException`, and the generation step was refused with the cause `I/O error on POST request for http://localhost:11434/api/show: null`. Ollama did serve the model: `/api/tags` listed it, and `/api/show` answered for it in 3 ms a moment later. The timeout is not in `application.yaml` or in `embedding`: it is the read timeout of the HTTP client Spring gives `RestClient` where reactor-netty is on the class path.

A refusal at the step also comes late. The generation step is reached invocations after the walk, and stage 5's scoring step after the extraction of the whole corpus. A model whose tag ends in `cloud` is known for one from its name alone the moment the profile names it, and Ollama can say whether it forwards a model before anything is walked.

## Decision

### 1. A check that cannot be completed takes the model as local at the two steps

At stage 5's scoring step and at the generation step, a model whose check cannot be completed is **taken as local**, and the step goes on: its run is resolved, its identity read from `/api/tags` and its text sent, as for a model the check passed. *Cannot be completed* is ADR-202 §3's fourth case whole: Ollama not reachable, a read past the client's timeout, an answer whose status is neither 2xx nor 404, and an answer with no body.

The step says so in one line at WARN, in its own voice, `<stage>` being `Stage 5c (embedding scoring)` or `Stage 6b (generation)`:

```
<stage> could not establish whether the model <name> runs on this machine, so it is taken as local and the step goes on
```

Nothing is pulled for it, and Ollama is not asked about it a second time. An Ollama that is down, and not only busy, is then met by what follows the check: reading the model's identity from `/api/tags`, or the first call, each of which stops the step as it did before ADR-202 put the check in front of it.

### 2. `vespera run` refuses, at its start, a model it names that is not local

**Which models.** Every model the profile and the configuration name for the invocation, each resolved as its step resolves it: the embedding model as `EmbeddingModelGate` resolves it (the profile's `embeddingModel`, else `spring.ai.ollama.embedding.options.model`), and the generation model as `GenerationModel` resolves it (the profile's `generationModel`, else `spring.ai.ollama.chat.options.model`). A model that resolves to nothing is not checked: the gate of ADR-298 §2 or the stop of ADR-114 meets it where it meets it today. A profile that cannot be read names no model here, and is left to the job's own refusal of it. The labeller's model, `vespera.label.model`, is not `vespera run`'s, and is not checked by it.

**Whether or not the invocation reaches the step.** Both models are checked at the start of every `vespera run`, whichever gate the invocation will stop at. A cloud model named as `generationModel` stops the first invocation, though generation comes four invocations after it. A model named in the profile after an invocation has started is that invocation's only where its step reads the profile again, and is checked there by §3; it is checked at the start of the next invocation.

**What refuses.** For each model, in that order, the embedding model first:

1. **Its tag ends in `cloud`**, read lower-cased as ADR-202 §3 reads it: refused, with no request to Ollama.
2. **Otherwise ADR-202's check is asked**, `LocalOllamaModel.refusalOf`, once. A model it reports as remote is refused. Every other answer refuses nothing at the start: a model served here; a 404, a model Ollama does not serve, which its step pulls or refuses as ADR-329 says; and a check that cannot be completed, which is taken as local as §1 takes it.

So a start where Ollama does not answer refuses nothing on that account.

**The line and the exit code.** The first model refused ends the start. One line on standard error, the invocation's report recording it as it records every refusal at the start, and exit code 1, as every refusal at the start of `vespera run` exits (ADR-141, ADR-323 §8, ADR-330 §9):

```
vespera run stopped at its start: the <role> <name> was refused, so nothing was walked and no run was minted -- <reason>
```

`<role>` is `embedding model` or `generation model`, and `<reason>` is the check's words, unchanged: *the model `<name>` is a cloud model: Ollama forwards it to a hosted service, and no document's text may be sent there*, or *Ollama reports the model `<name>` as remote, so it is forwarded to a hosted service, and no document's text may be sent there*. The line names no record and offers no setting, because there is none: §8.

**Where.** After the working-directory lock, the corpus-root lock and the sidecars' start (ADR-323 §1), so that an Ollama Vespera has just started is the one asked; and before the file of ADR-330 §1 is written and the job is started, so that nothing is walked, no run is minted and no stage runs. The refusals that come before it keep their order and their lines.

### 3. The step's check stays, and is met only by what the start could not see

Stage 5's scoring step and the generation step still put their model through `ModelPull.refusalOf`, as ADR-329 has them, and still refuse a tag that ends in `cloud`, a model reported as remote and, where pulling is off or the pull fails, a model Ollama does not serve, each in ADR-202 §3's words and the step's stop line. With §2 in front of it, the first two are met at the step only where the start could not see them: Ollama did not answer at the start and answers at the step that it forwards the model, or the profile was saved during the invocation, after the start, naming another model. In each of those the text would otherwise leave the machine.

### 4. `ModelPull` asks once more only for a 404, and pulls only on one

`ModelPull.refusalOf` asks the check as ADR-329 §3 says, with one change to its step 4: where the check's reason is its fourth, the one ADR-202 §3 words as *it could not be established that the model `<name>` runs on this machine…*, `ModelPull` writes §1's line, answers no reason, and asks Ollama nothing more. Its other reasons go through ADR-329 §3 unchanged: a cloud tag stands; `/api/show` is asked again, a 404 is pulled where pulling is on, and any other answer leaves the reason standing, a model reported as remote among them.

`ModelPull` reads which reason the check gave from its words, which ADR-202 §3 fixes, because the check is in `embedding` and `embedding` is not touched (§7). `ModelPullTest` fails where those words change.

### 5. `vespera label --auto` is unchanged

Its model is already checked at the start of its own invocation, before the label file is opened (ADR-197 §6), which is where §2 puts the refusal for `vespera run`; a cloud tag and a model reported as remote are refused there in ADR-197 §6's words, and the command exits 1. It goes on refusing a model whose check cannot be completed. It calls `LocalOllamaModel.refusalOf` directly, which this record leaves as it is, and a labeller that cannot read the model's digest from the same daemon could not record an identity for its answers anyway. It checks neither the embedding model nor the generation model, and `vespera run` does not check its model. `vespera label` with a file and `vespera publish` ask Ollama nothing.

### 6. The read timeout on `/api/show` stays the client's

`OllamaClient` keeps the client Spring builds for it, and the read timeout that client has. With §1, a read past it no longer stops a step: it delays the step by its length, about 10 s, and the start by as much for each model asked, and only where Ollama does not answer. A longer timeout would hold the start longer where Ollama is down; a shorter one would let the start miss more often a model Ollama would report as remote, which §3 then meets at the step.

### 7. Where it lives, and no run id moves

Every change is in `pipeline`:

- **`ModelsAtTheStart`**, a class of `pipeline` built by Spring from `EmbeddingModelGate`, `GenerationModel` and the `OllamaClient` bean, holds §2: `refusal()` answers the line, or nothing.
- **`VesperaCommand.Run`** takes it as an optional collaborator, as it takes `Presets`, and refuses with its line where §2 places it. A command built without it, as some tests build it, checks nothing at its start.
- **`ModelPull`** holds §1's line and §4.

`embedding` is not touched. `LocalOllamaModel` answers what it answered, and the labeller calls it as it did. The javadoc of `OllamaClient.isRemote`, which says a model whose answer cannot be read is refused by the caller, holds of the labeller alone from now on; it is corrected in the next change that touches `embedding` for a reason of its own (ADR-216 §7), since a change to it here would move the run ids of seed measurement, embedding scoring, the arrangement and generation, which name `embedding` in `StageModules`. No stage's version names `pipeline` (ADR-222), and none of the three classes decides a verdict, a cache key, a cluster or text of the deliverable. No run id moves, and no pass is owed.

### 8. Cloud models stay out

Nothing in the profile, the configuration or the environment lets a model whose tag ends in `cloud`, or one Ollama reports as remote, be sent text. A later record may add configuration for them; this one adds none.

## Alternatives rejected

- **Refuse at the start only, and take the step's check away.** A model Ollama reports as remote would be sent text whenever Ollama was too busy to answer at the start, and a profile saved during an invocation could name a cloud model the start never saw.
- **Keep the step's check only to log.** The same two cases, with a line saying the text was sent.
- **Fail closed at the start, refusing where Ollama does not answer.** A busy Ollama would stop the invocation at its start, as it stopped the step.
- **Check the tag before the locks and the sidecars, with the presets of ADR-330 §9, and the remote report after.** Two places for one refusal and two lines for it; one place costs a cloud-tagged model the start of the sidecars first, and nothing more.
- **Check only the models the invocation will reach.** The step it will reach is known only once the job runs, through its gates; and an operator told at invocation 5 of a cloud model named at invocation 1 is the lateness this record removes.
- **Give `LocalOllamaModel` a typed answer, or take the fourth case as local inside it.** Either changes `embedding`, and moves the run ids of four stages for a change that decides nothing they record (§7).
- **Pull a model whose check cannot be completed.** ADR-329 pulls only on a 404, so that a model Ollama serves is never pulled again; a check that cannot be completed says nothing about whether Ollama serves it.
- **A shorter, or configurable, read timeout.** §6.

## Consequences

- **A busy Ollama no longer stops a step.** The step waits the client's timeout once, writes §1's line and goes on.
- **A cloud model is refused before anything is walked**, at whichever invocation first names it, in one line on standard error and exit code 1; the embedding scoring and generation steps meet one only in §3's cases.
- **`vespera run` asks Ollama about each model it names at its start**, once each: a few milliseconds where Ollama answers, up to the client's timeout each where it does not, and nothing for a cloud tag. Where `VESPERA_SIDECARS_START` is false and Ollama is not running, the start refuses nothing and goes on to the walk, and the first step that needs Ollama meets it.
- **An Ollama that is down is met at the first call that needs it**, in that call's words, and not in the check's. ADR-202's *"An invocation that reaches embedding scoring needs Ollama up, even where every chunk is embedded already"* holds still, for the identity the step reads from `/api/tags`.
- **The control panel** starts `vespera run` as its child, which refuses at its start as from the command line; the control panel shows the line as it shows every refusal of its child.

## What this does not decide

- **The labeller taking an unanswered check as local.** It needs `embedding` changed, and its identity's digest from the same daemon.
- **A model Ollama forwards under another name in the profile's resolution**, such as a model created from a cloud model under a tag that does not end in `cloud`, where Ollama does not answer at the start or at the step. It is the case ADR-202 left: the check reads only what Ollama says.
- **Configuration for cloud models** (§8).

## Tests

| Method | What it holds |
| --- | --- |
| `pipeline.ModelPullTest.aCheckOllamaCannotCompleteLetsTheStepGoOn` | §1, §4: a 500 from `/api/show`, and an Ollama nobody listens for, each answer no reason with pulling on; the Ollama that answered 500 was asked `/api/show` once and nothing else; and `ModelPull` wrote §1's line, whole, at WARN, once for each |
| `pipeline.EmbeddingRefusesAModelNotServedHereInvocationTest.anEmbeddingModelNotLocalIsRefusedAtTheStartBeforeTheWalk` (removed by ADR-384, whose `cloudInTheProfileIsRefusedAtTheStartBeforeTheWalk` holds the start's refusal) | §2: an embedding model whose tag ends in `cloud`, and one Ollama reports as remote, each end `vespera run` with exit code 1 and §2's line, whole, once on standard error, with no walk of the corpus recorded; Ollama is asked about the second and not about the first |
| `pipeline.EmbeddingRefusesAModelNotServedHereInvocationTest.anEmbeddingModelOllamaCannotAnswerAboutIsTakenAsLocal` (removed by ADR-384, whose `noModelIsRefusedForItsNameOrForAnUnansweredQuestion` holds its case) | §1, §2: an embedding model Ollama cannot answer `/api/show` about is refused neither at the start nor at the scoring step: exit code 0, one scoring run minted over the corpus, chunks sent to the embedding model, and no line at ERROR |
| `pipeline.GenerationRefusesAModelNotServedHereInvocationTest.aCloudTaggedGenerationModelIsRefusedAtTheStartOfAnInvocationThatWouldNotReachIt` (removed by ADR-384, whose `aCloudTaggedGenerationModelDoesNotStopTheStart` holds the reverse) | §2: a cloud-tagged `generationModel` in a profile with no arrangement approved ends the first invocation with exit code 1 and §2's line, whole, once on standard error, nothing walked, and Ollama not asked about it |

Amended:

- **`EmbeddingRefusesAModelNotServedHereInvocationTest.anEmbeddingModelNotServedHereIsRefusedBeforeAnyChunk`** loses its cloud-tagged case, which the start now refuses (§2); its never-pulled case, refused at the step, stands.
- **`GenerationRefusesAModelNotServedHereInvocationTest`**'s one test, a cloud-tagged generation model refused at the generation step with the step's line, is replaced by the test above.

`ModelPullTest.aCloudModelIsNotPulled` (removed by ADR-384) held §3's refusal of a cloud tag at the step unchanged. `LocalOllamaModelTest` (deleted by ADR-384) and `LocalLabellerTest` were unchanged (§5). No test waits for a read past the client's timeout: a connection refused raises the same exception through the same client, and the timeout would hold the test for its 10 s.

## What the implementation owes

Beside the tests above, which it turns green: `ModelsAtTheStart` in `engine.pipeline`, listed in `PipelineHoldsOnlyTheRulesOnRecordTest` as holding no rule, and imported by `CascadeSliceTest` beside `ModelPull`; `VesperaCommand.Run`'s call to it where §2 places it; `ModelPull`'s §1 line and §4. The notes at the heads of ADR-202 and ADR-329 are written with this record.
