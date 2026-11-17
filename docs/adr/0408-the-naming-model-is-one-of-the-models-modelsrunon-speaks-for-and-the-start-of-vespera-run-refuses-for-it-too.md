# ADR-408 — The naming model is one of the models `modelsRunOn` speaks for, and the start of `vespera run` refuses for it too

- **Date**: 2026-11-17
- **Status**: accepted on 2026-11-17.
- **Decided**: by the operator, in [#847](https://github.com/algernon28/vespera/issues/847), part of map [#822](https://github.com/algernon28/vespera/issues/822): that the naming model, `vespera.naming.model` ([ADR-407](0407-topics-are-named-at-the-head-of-the-arrangement-step-by-a-naming-model-of-their-own-from-their-strongest-passages.md) §2), joins both the models the start of `vespera run` refuses for ([ADR-375](0375-a-model-whose-check-cannot-be-completed-is-taken-as-local-and-a-cloud-model-is-refused-at-the-start-of-vespera-run.md) §2) and the models `modelsRunOn` speaks for ([ADR-384](0384-whether-the-models-run-locally-or-in-the-cloud-is-the-profiles-to-say-local-by-default-and-cloud-refused-at-the-start.md) §1). What joining each means follows from ADR-384, which stands: the start reads `modelsRunOn` alone and no model's name, and no model is refused for its name (§1 to §3).
- **Amends**: [ADR-375](0375-a-model-whose-check-cannot-be-completed-is-taken-as-local-and-a-cloud-model-is-refused-at-the-start-of-vespera-run.md) §2, its *Which models*, *"the embedding model … and the generation model"*, and its *Whether or not the invocation reaches the step*: the naming model is the third model the start refuses for, and a profile whose `modelsRunOn` is not `local` stops an invocation that would reach the arrangement step as one that would reach generation. ADR-384's amendment of §2 stands: the start reads no model's name, the naming model's among them, and asks Ollama nothing (§2). It carries a second note at its head.
- **Amends**: [ADR-384](0384-whether-the-models-run-locally-or-in-the-cloud-is-the-profiles-to-say-local-by-default-and-cloud-refused-at-the-start.md) §1, *"It is one key, for every model Vespera sends a document's text to: the embedding model, the generation model and the labeller's"*: the naming model is one of them (§1); and *"No model is refused for its name … at the start of `vespera run`, at stage 5's scoring step, at the generation step or in `vespera label --auto`"*: nor at the arrangement step (§3). It carries a second note at its head.
- **Settles**: [ADR-407](0407-topics-are-named-at-the-head-of-the-arrangement-step-by-a-naming-model-of-their-own-from-their-strongest-passages.md)'s *What this does not decide*, its fourth point: whether `vespera run` checks the naming model at its start, and how `modelsRunOn` holds of it. It carries a note at its head.
- **Keeps**: [ADR-384](0384-whether-the-models-run-locally-or-in-the-cloud-is-the-profiles-to-say-local-by-default-and-cloud-refused-at-the-start.md) §2's line, its place right after the presets' refusal and before the corpus-root lock and the sidecars' start, and exit code 1; §3, `vespera label --auto`, which sends the naming model nothing; §5, the step asking Ollama only whether it serves the model; and §6 to §9. [ADR-407](0407-topics-are-named-at-the-head-of-the-arrangement-step-by-a-naming-model-of-their-own-from-their-strongest-passages.md) §1, §2 and §10: the naming model's pull at the arrangement step, its resolution and its stop on a blank value where the arrangement run is minted, and the control panel's read-only line. [ADR-388](0388-where-the-models-run-is-worded-under-a-profile-rows-rules-and-a-change-of-it-takes-effect-the-next-time-vespera-starts.md) §1's texts, which name no model.
- **Keeps**: [ADR-195](0195-the-reference-model-and-every-bake-off-candidate-are-served-locally.md) and [ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md): the archives' documents are read only by local models, as far as the operator's configuration states it (ADR-384, Consequences).
- **Settles**: [#847](https://github.com/algernon28/vespera/issues/847).

## Context

**Two lists name the models Vespera sends a document's text to.** ADR-375 §2 has the start of `vespera run` refuse for every model the profile and the configuration name for the invocation, and names two: the embedding model and the generation model. ADR-384 §2 amended it so that the start reads the profile's `modelsRunOn` alone, reads no model's name and asks Ollama nothing, refusing `cloud`, or any value but `local`, before the corpus-root lock and the sidecars' start, in one line that names no model. ADR-384 §1 makes `modelsRunOn` one key for every model Vespera sends a document's text to, and names three: the embedding model, the generation model and the labeller's, `vespera.label.model`.

**ADR-407 adds a fourth.** The naming model, `vespera.naming.model`, else `spring.ai.ollama.chat.options.model`, else `qwen3:8b`, is pulled by the arrangement step where Ollama does not serve it, through `ModelPull.refusalOf`, and is sent each topic's strongest seed passages, whole, under a budget of 1,000 words: the text of the operator's seed documents. Neither list names it, and ADR-407 leaves open whether the start checks it and how `modelsRunOn` holds of it.

## Decision

### 1. `modelsRunOn` speaks for the naming model

`modelsRunOn` is one key for every model Vespera sends a document's text to: **the embedding model, the generation model, the naming model and the labeller's**. Where it is unset or `local`, the naming model is taken to run on this machine, as the operator states, as ADR-384 §1 takes the others. Where it is `cloud`, or any other value, nothing is sent to the naming model, because every invocation that would send it anything is refused at its start (§2).

It has no key of its own, and no reading of its own of the key: ADR-384's *a key for each model* stays rejected.

### 2. The start of `vespera run` refuses for the naming model as for the others

ADR-375 §2's *Which models*, as ADR-384 §2 leaves it, is every model the profile and the configuration name for the invocation: **the embedding model, the generation model and the naming model**. The start refuses for all three through `modelsRunOn` alone. It resolves none of them, reads no name and asks Ollama nothing, so the naming model is neither resolved nor asked about there:

- a blank naming model is met where the arrangement run is minted, naming `vespera.naming.model` (ADR-407 §2);
- a naming model Ollama does not serve is met at the arrangement step's pull, pulled or refused as ADR-329 and ADR-384 §5 say (ADR-407 §1).

**Whether or not the invocation reaches the step** holds of the naming model as of the generation model: a profile whose `modelsRunOn` is `cloud` stops the first invocation, though the arrangement step comes invocations after it, and stops an invocation that would reach the arrangement step. Nothing is walked and no run is minted; the naming model is not asked whether Ollama serves it, not pulled, and put no question.

**The line, its place and its exit code are ADR-384 §2's, unchanged.** They name no model, so the naming model needs no word of its own there. `vespera label --auto` sends the naming model nothing and keeps ADR-384 §3's refusal as it is.

### 3. The arrangement step refuses the naming model only where Ollama does not serve it

ADR-384 §1's *no model is refused for its name, a tag ending in `cloud` among them, nor for what `/api/show` says of where it runs* holds at the arrangement step. The naming model goes through `ModelPull.refusalOf` as ADR-407 §1 has it, which asks Ollama only whether it serves the model (ADR-384 §5). So a naming model whose tag ends in `cloud`, where `modelsRunOn` is unset or `local`, is pulled where Ollama does not serve it and is sent the seed passages, as an embedding or generation model of such a tag is. ADR-388 §1's tooltip already says what that costs the operator: *one whose name ends in 'cloud' would be sent your documents' text.*

### 4. Where it lives, and run ids

The decision changes no behaviour of the code ADR-384 and ADR-407 describe: `ModelsAtTheStart` reads the key alone (ADR-384 §8), and `ModelPull` reads no name (ADR-384 §5). `ModelsAtTheStart`'s javadoc cites this record beside ADR-384.

**No run id moves.** `ModelsAtTheStart` and `ModelPull` lie in `pipeline`, which no stage's version names (ADR-222); `modelsRunOn` enters no run's identity (ADR-384 §1); the naming model enters the arrangement run already (ADR-407 §3), and nothing here adds to it.

## Alternatives rejected

- **A start that reads the naming model's name, or asks Ollama about it.** ADR-384 §1 has Vespera never infer where a model runs, and §2 has the start read no name. A check of the naming model's tag would bring back, for one model of four, the reading ADR-384 removed for all of them.
- **A key of its own for the naming model.** ADR-384 rejects a key for each model: answers to one question of which one is accepted.
- **Leaving the naming model out of `modelsRunOn`.** The arrangement step sends it the seed documents' text, so the key that says where a document's text may go would not speak for every model it goes to.
- **Resolving the naming model at the start, so that a blank one is met before the walk.** ADR-407 §2 stops a blank one where the arrangement run is minted, as ADR-114 stops a blank generation default, and the start reads no model's name.

## Consequences

- **A profile saying `cloud` stops `vespera run` at its start before the naming model is asked anything**, in ADR-384 §2's line, exit code 1, whichever step the invocation would reach.
- **A naming model whose tag ends in `cloud` is sent seed passages where `modelsRunOn` is unset or `local`**, as the embedding and generation models of such a tag are sent text.
- **ADR-375, ADR-384 and ADR-407 carry a note at their heads** pointing here.
- **No run id moves** (§4).

## What this does not decide

- **An Ollama address not this machine for the naming model**, which ADR-384 leaves open for the embedding and generation models, ADR-202 §5 having settled it for the labeller alone.
- **How a cloud model is named, reached and paid for** once cloud is supported, the naming model among them: the later record ADR-384 leaves it to.

## Tests

| Method | What it holds |
| --- | --- |
| `pipeline.ModelsRunOnSpeaksForTheNamingModelInvocationTest.cloudInTheProfileStopsTheStartBeforeTheNamingModelIsAskedAnything` | §1, §2: a naming model named in configuration with a tag ending in `cloud`, and a profile with every gate open as far as the arrangement whose `modelsRunOn` is `cloud`: `vespera run` ends with exit code 1 and one line on standard error that is the start's refusal, nothing walked, Ollama not asked about the naming model and no question put to it |
| `pipeline.ModelsRunOnSpeaksForTheNamingModelInvocationTest.aCloudTaggedNamingModelNamesTheTopicsWhereModelsRunOnIsUnset` | §1, §3: the same naming model and profile with `modelsRunOn` unset: the start refuses nothing, the corpus is walked once, the invocation ends at the arrangement's approval with exit code 0, Ollama is asked whether it serves the naming model and the arrangement step does not refuse it, and the topics are named by the naming model and no other |

ADR-384's tests stand unchanged: `EmbeddingRefusesAModelNotServedHereInvocationTest.cloudInTheProfileIsRefusedAtTheStartBeforeTheWalk` holds the start's lines for every value refused, whatever model the profile names.

## What the implementation owes

Beside the tests above: `ModelsAtTheStart`'s javadoc citing this record (§4). The notes at the heads of ADR-375, ADR-384 and ADR-407 are written with this record.
