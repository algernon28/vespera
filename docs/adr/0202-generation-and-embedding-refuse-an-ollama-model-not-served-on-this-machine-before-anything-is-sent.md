# ADR-202 — Generation and embedding refuse an Ollama model not served on this machine, through the labeller's own check, before anything is sent

- **Date**: 2026-10-05
- **Status**: accepted
- **Extends**: [ADR-197](0197-a-local-model-answers-the-relevance-questions-and-the-floor-is-a-rule-over-the-labels-so-no-document-reaches-a-hosted-model.md) §6. Two of the labeller's refusals, a tag that ends in `cloud` and a model `/api/show` reports as remote, become one check that every call Vespera makes to Ollama passes: the labeller's, stage 5's embedding scoring and stage 6b's generation (§1). The labeller's third refusal, an endpoint that is not this machine, stays its own (§5). ADR-197 left the generation and embedding half to [#431](https://github.com/algernon28/vespera/issues/431); this is that half. No decision of ADR-197 changes: the labeller refuses what it refused, in the same words, and still reads `/api/show` through `OllamaClient`. Only the order of two of its refusals changes, for one combination (§4).
- **Extends**: [ADR-114](0114-the-generation-model-is-named-in-application-configuration-with-a-code-default-and-is-not-a-gate.md), whose two stops for stage 6b, a blanked default and a name the engine never pulled, gain a third: a generation model not served on this machine. It is a stop and not a gate, as the other two are, so "not a gate" stands.
- **Rests on**: [ADR-195](0195-the-reference-model-and-every-bake-off-candidate-are-served-locally.md) and [ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md) (the archives' documents are read only by local models, and a guard that cannot decide refuses), [ADR-040](0040-modules-are-capability-shaped-not-stage-shaped.md) and [ADR-110](0110-pipeline-hands-synthesis-its-inputs-so-the-module-rule-gains-no-second-exception.md) (where the check can live), [ADR-084](0084-the-embedding-model-is-a-profile-gate-and-a-vector-carries-its-whole-embedder-identity.md), [ADR-080](0080-the-boilerplate-floor-is-a-gate-applied-before-signatures-are-computed.md) and [ADR-157](0157-a-stage-asks-for-its-run-after-its-own-gate-one-helper-mints-every-run-and-every-step-is-named-once.md) (a stage asks for its run after its gate, and a run that did nothing should not exist), and [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) (which run ids move).
- **Settles** [#431](https://github.com/algernon28/vespera/issues/431).

## Context

The operator decided on 2026-10-05 that the archives' documents are read only by local models (ADR-195, ADR-196). A loopback address does not guarantee that. Ollama serves **cloud models**, with tags such as `gpt-oss:120b-cloud`, and any model created from one. The local daemon serves them and forwards every request to ollama.com, so a request to `http://localhost:11434` can carry document text off the machine.

ADR-197 §6 closed this for the relevance labeller. Two other paths send text to Ollama and are still open:

- **Stage 6b's generation**, which puts each cluster's exemplars to the generation model. Its name is `generationModel` in the profile, or else `spring.ai.ollama.chat.options.model`. Both are free text.
- **Stage 5's embedding scoring**, which sends every chunk of every survivor and every usable seed to the embedding model the profile names. That name is free text too.

### What the code does today

- `OllamaRelevanceLabeller` (`embedding`) refuses, before anything is sent: a model whose name ends in `cloud`; an endpoint that is not this machine (`LocalEndpoint`); a model `OllamaClient.isRemote` reports as remote; and a model about which `/api/show` or `/api/tags` cannot be read. It decides once and keeps the answer.
- `OllamaClient.isRemote` (`embedding`) posts `{"model": <name>}` to `/api/show` and reads `remote_host` and `remote_model`. Those are the field names of `ShowResponse` in Ollama's `api/types.go` at `v0.33.2`, the version `compose.yaml` pins: both strings, both `omitempty`, both absent for a model whose weights are local. They were checked again for this record against the source at that tag. `/api/tags` (`ListModelResponse`) carries the same two fields, and nothing reads them there.
- `EmbeddingScoringTasklet` (`pipeline`) reads the embedding model's name once, through stage 5's gate preamble, resolves its run and then passes that one name to `ChunkEmbedder.embed` for every chunk. `ChunkEmbedder` calls the embedding model for every chunk, a cached one included, because the vector's length is part of the identity.
- `GenerationTasklet` (`pipeline`) mints its run through `StageRuns.generation()`, which reads the generation model's name and its digest from `/api/tags`. Inside the step it reads the name a second time and hands it to `ClusterGeneration` in `synthesis`, which calls `ClusterSynthesis` with it. `synthesis` may depend on `ledger` alone and never names `OllamaClient` (ADR-110).
- Nothing else in `src/main` calls a chat model or an embedding model.

## Decision

### 1. Where the check lives: `embedding`, beside `OllamaClient`, and `pipeline` calls it for the two stages

**The check is one public class in `embedding`, beside `LocalEndpoint`**, with one static method that takes the name of an Ollama model and an `OllamaClient` and returns the reason it must not be used, or nothing. `OllamaRelevanceLabeller` calls it. `pipeline` calls it for embedding scoring and for generation. There is one implementation of the rule.

It lives in `embedding` because each module that has to reach it can reach `embedding` and nothing else would do:

- **The labeller is in `embedding`**, and `embedding` may depend on `ledger` alone (ADR-040), so the check cannot live in `pipeline`.
- **`synthesis` may not name `embedding`** (ADR-110), so it cannot hold the check or call it. It does not need to. `pipeline` already reads the generation model's digest through `OllamaClient` and hands `synthesis` a plain string, and it now checks the name the same way before it hands that name over. `synthesis` is not touched.
- **`ledger` owns identity and verdicts** (ADR-041) and holds no client of a service. A check there would need `OllamaClient`, which `ledger` cannot reach.

A static method rather than a bean, so no wiring changes: the labeller already holds an `OllamaClient`, and `pipeline` injects the one bean every stage already shares.

`ChunkEmbedder` and `ClusterSynthesis` do not check. Each sends under a name its caller gives it, and the caller is the stage that checked that name. The guarantee is held at the three places a name is chosen, the labeller and the two stages, and a test pins each.

### 2. When it runs: once per stage, after the stage's gates and before its run is resolved

**Embedding scoring and generation each ask once per invocation**, at the start of the step and before any call. Not per call:

- **The name cannot change under the stage.** Embedding scoring sends every chunk under the one name its preamble read. Generation reads the name twice today, once to mint its run and once to send. From now on the step reads it once, checks that name, and sends under that name and no other, so a `profile.yaml` edited while the stage runs cannot put an unchecked name on a call.
- **`/api/show` is a round trip.** Per chunk it would cost one for every chunk of the corpus, to learn what the first one learned.

**It runs after the stage's gates.** A gated stage sends nothing, so there is nothing to check, and the operator is not asked to start Ollama to be told a gate is shut.

**It runs before the stage's run is resolved.** A refused stage mints no run, which is ADR-080's rule as ADR-084 and ADR-114 already place their stops: a run row for a stage that did nothing reads as one that found nothing. For generation it also means no digest is read from `/api/tags` for a name that is refused.

The check therefore runs where embedding scoring's run is already recorded and no chunk would be sent. That costs one round trip, and it means an invocation with Ollama down now stops at embedding scoring with the refusal line, where before it ran on through relevance scoring and the arrangement. Starting Ollama is the remedy. The alternative, checking only once the stage has work, would put the check after the run is minted, which leaves a run behind each refusal.

The labeller keeps its own timing: it decides once, before `vespera label --auto` opens the label file, and keeps the answer.

### 3. What a refusal does: one line naming the model, the stage fails, and nothing is sent

The check refuses in three cases, in this order:

1. **The model's tag ends in `cloud`.** Decided from the name, with no request to Ollama.
2. **`/api/show` reports the model as remote**: `remote_host` or `remote_model` is present and not blank.
3. **`/api/show` cannot be read**: the daemon is down, it answers an error, or it answers nothing. **The check fails closed**, as ADR-196's guard and the labeller do: a model whose remoteness cannot be established is refused, never assumed local.

The reasons are worded as the labeller words them today, so its reasons keep their words: *the model `<name>` is a cloud model…*, *Ollama reports the model `<name>` as remote…*, *it could not be established that the model `<name>` runs on this machine…*.

**In embedding scoring and generation a refusal stops the stage the way generation's five-in-a-row stop does** (ADR-111): one line at error level, naming the stage, the model and the reason; the step's status set to failed; no exception thrown, so no stack trace stands in place of the line. The invocation ends non-zero, no later step runs, no run is minted, and nothing has been sent to the model. In the labeller a refusal is what ADR-197 §6 says it is.

Failing closed costs nothing in the case it most often meets. With the daemon down, embedding scoring fails on its first chunk today and generation fails on reading its digest. The refusal names the model and the reason in place of a connection error.

### 4. One implementation: the labeller's model checks become the shared check

`OllamaRelevanceLabeller` keeps three things of its own: its endpoint check (§5), the digest it reads for its identity, and its single decision. Its tag check and its `/api/show` read are replaced by a call to the shared check, made after the endpoint check and before the digest is read. A digest that cannot be read is still refused by the labeller, as ADR-197 §6 says. Its tests stand as they are.

One order changes. Today the labeller tests the tag before the endpoint, so a cloud-tagged model behind an endpoint that is not this machine is refused for its tag. From now on it is refused for its endpoint. Both refusals come before any request, and no test or record depends on which of the two is named.

ADR-197 is extended, not amended. Every sentence it says about the labeller stays true; what changes is that the check it describes is now shared, which this record says. `docs/adr/README.md` keeps records append-only, so ADR-197 is not edited.

### 5. The endpoint stays the labeller's own check

The issue asks for two refusals: the tag, and what `/api/show` reports. It does not ask that generation and embedding refuse an endpoint that is not loopback, and this record does not add that refusal. ADR-197 made loopback-only the labeller's rule with the operator's consent, and accepted that it refuses an Ollama on another machine of the operator's own. Extending that to the two stages is a new restriction on where an operator may serve them, and it is left under "What this does not decide".

## Consequences

- **No text reaches a hosted service through Ollama on any path.** The labeller, embedding scoring and generation each refuse a cloud tag, a model Ollama reports as remote, and a model whose remoteness cannot be read, before anything is sent. `EmbeddingRefusesAModelNotServedHereInvocationTest` and `GenerationRefusesAModelNotServedHereInvocationTest` pin the two stages, and `LocalLabellerTest` and `OllamaRefusalInvocationTest` still pin the labeller.
- **Stages 3 to 6b move their run ids.** The change touches `embedding` (the shared check, and the labeller's call to it) and `pipeline` (the two stages that call it). A stage's implementation version is the last commit touching the modules `StageModules` names for it (ADR-058). The runs that move are `content-census` and `content-redundancy`, which name `pipeline`; `seed-measurement` and `embedding-scoring`, which name `embedding` and `pipeline`; and `arrangement` and `generation`, which name both as well as `synthesis`. `byte-level-reduction` names `corpus` alone and `extraction` names `extraction` and `similarity`, so stages 1 and 2 keep their run ids. `synthesis` is not touched, and touching it would move no further run, since the two stages that name it move already. For an existing working directory this means:
  - the arrangement's id changes, so an `arrangementApproved` written before it no longer names the arrangement and the gate closes again; the operator reads the new `arrangement.html` and writes its name;
  - stage 6b generates again, under a run of its own;
  - stored vectors are not lost, since a vector is keyed by its embedder identity and not by a run (ADR-085), though embedding scoring calls the embedding model again for every chunk, as it does for any new run;
  - labels survive, because they are keyed by path and seed set (ADR-097).
- **An invocation that reaches embedding scoring needs Ollama up**, even where every chunk is embedded already (§2). One that reaches generation needed it already, for the digest.
- **The generation step reads the generation model's name once.** The name it checks is the name it sends under. `StageRuns.generation()` still reads it for the run's identity, and a profile edited between that read and the step's own is the same hazard it was before this record; it sends nothing.
- **The test fixture's Ollama client answers `/api/show`.** `EmbeddingScriptedBeans` reports every name as served on this machine unless a test scripts it otherwise, records the names it was asked about, and counts embedding calls. Every invocation test that reaches stage 5 passes through the check, so without this the shared fixture would refuse every one of them.
- **`CONTEXT.md` gains no term.** "Model" still never travels alone: the check speaks of an Ollama model, and its callers of the generation model and the embedding model.

## What the operator decided and what this record decided

The operator decided the rule on 2026-10-05: the archives' documents are read only by local models, and a cloud tag and a model Ollama reports as remote are refused on every path ([#431](https://github.com/algernon28/vespera/issues/431)). The operator asked for this record under `/implement` and left these four to it, each with its reason above: where the check lives (§1), when it runs (§2), that it fails closed (§3), and that the labeller's checks become the shared one (§4). Any of them can be reopened by a later record that references this one.

## What this does not decide

- **Loopback-only for generation and embedding** (§5). Whether the two stages should also refuse an Ollama endpoint that is not this machine, as the labeller does, is the operator's to decide. If they should, the shared check is where it goes.
- **Which daemon `/api/show` is asked.** `OllamaClient` is built from `spring.ai.ollama.base-url`, while Spring AI's chat and embedding models call the base URL their connection details name. ADR-197 §6 tested the endpoint against the connection details for that reason. Where the two differ, the check and the digest are both read from a daemon other than the one that serves the calls. This is the condition the digest has had since ADR-114, and this record does not change it.
- **A guard against a fourth call site.** Nothing stops a later class from injecting a chat model or an embedding model and calling it unchecked. An architecture test that names the classes allowed to do so would hold it, and is not written here.
