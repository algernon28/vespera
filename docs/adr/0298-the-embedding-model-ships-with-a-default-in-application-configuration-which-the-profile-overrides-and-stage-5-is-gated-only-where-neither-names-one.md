# ADR-298 — The embedding model ships with a default in application configuration, `bge-m3:latest`, which the profile overrides, and stage 5 is gated only where neither names one

- **Date**: 2026-10-19
- **Status**: accepted on 2026-10-19.
- **Amends**: [ADR-084](0084-the-embedding-model-is-a-profile-gate-and-a-vector-carries-its-whole-embedder-identity.md), three of its parts: *"Which model is a profile key; where it is served stays configuration"*, since the name now has a shipped value in configuration and the profile key overrides it (§1); *"An unset model gates stage 5"*, narrowed to a model named neither in the profile nor in configuration (§2); and *"No default value"*, struck (§1, §7). Its embedder identity, its six-part key, its empty instruction and its re-chunk from `extraction_cache` stand whole.
- **Amends**: [ADR-098](0098-getting-from-a-folder-to-a-curated-archive-is-four-invocations-and-the-operator-is-told-the-next-value-not-the-stage.md), its table's second row, which wants `boilerplateDocumentFrequencyFloor` alone, and the closing line's run values, of which `embeddingModel` is no longer one (§5). The number of invocations does not change.
- **Amends**: [ADR-114](0114-the-generation-model-is-named-in-application-configuration-with-a-code-default-and-is-not-a-gate.md), its *"Everything else that record decided stands unchanged, including every ground on which the embedding model is a gated profile key with no default"* and its section *"Why this is not the embedding model's treatment"*: the four grounds it lists no longer keep the embedding model from a default. They are why that default is changed only by a record (§7).
- **Keeps**: [ADR-228](0228-a-scoring-run-names-the-embedding-models-artefact-and-reads-the-vectors-of-one-embedder-identity.md), a scoring run names the model's digest and weight dtype; [ADR-202](0202-generation-and-embedding-refuse-an-ollama-model-not-served-on-this-machine-before-anything-is-sent.md), a model not served on this machine is refused before anything is sent (§6); [ADR-080](0080-the-boilerplate-floor-is-a-gate-applied-before-signatures-are-computed.md), a gate is checked before its stage's run is minted; [ADR-034](0034-embedding-model-chosen-by-bake-off-not-argument.md), the embedding model is chosen by a bake-off; [ADR-186](0186-the-deliverables-index-states-every-profile-key-read-off-the-profile-record.md) §2, the index shows each value as written (§4); [ADR-107](0107-the-arrangement-gate-approves-a-named-6a-run-and-the-path-becomes-five-invocations.md), a new embedding model closes the arrangement gate.
- **Settles**: that the embedding model ships with a default in `application.yaml`, that the default is `bge-m3:latest`, and that the profile's `embeddingModel` overrides it, which are the operator's decision of 2026-10-19; §2 to §8 are decided under it.

## Context

ADR-084 put the embedding model in the profile with no default, and gated stage 5 while it was unset: the invocation ends, the job succeeds, and no scoring run is minted. Its reasons for no default were three. A default settles by inertia what ADR-034 says a bake-off must settle. A default is invisible, since *"a profile key with a value in it looks exactly like a key somebody answered"*. And an unset model has *"nothing to fall back on"*, ADR-033 having recorded criteria and no choice.

ADR-114 gave the generation model the other shape: a name in `spring.ai.ollama.chat.options.model`, overridden by the profile's `generationModel`, and no gate. Where neither names one, a blanked default, that record stops the run as a misconfiguration and prints no next-value line.

Three things have been built since ADR-084 that bear on its reasons:

- **The scoring run names the weights, not only the tag.** ADR-228 puts the model's digest and weight dtype into the scoring run's recorded settings, so two pulls of one tag that differ are two runs, and no vector is read across them.
- **A model not served here is refused before anything is sent.** ADR-202 asks Ollama about the model before the scoring run is resolved, and a name it does not serve stops the stage with one line naming it.
- **A second candidate costs one re-chunk and one embedding pass**, and leaves the first's chunks and vectors where they are (ADR-084's own Consequences). Naming another model in the profile is that second candidate.

### The model

Ollama's library lists three tags for `bge-m3`, BAAI's M3 embedding model of 567 million parameters: `latest`, `567m` and `567m-fp16`. All three carry the digest 790764642607, at 1.2 GB, in FP16, with an 8K context. `bge-m3:latest` is therefore the FP16 weights, unquantized. Against ADR-033's criteria: its tokenizer is its own and separable from the weights; its weights are pinned by a digest, which the scoring run records (ADR-228); it embeds Italian and English among more than a hundred languages; and its dense embedding asks for no instruction, which is what ADR-084 sends.

ADR-084 named Qwen3-Embedding-8B as the leading candidate. Ollama serves it as `qwen3-embedding:8b`, which names the same digest as `qwen3-embedding:8b-q4_K_M` and `qwen3-embedding:latest` (digest 64b933495768, 4.7 GB, Q4_K_M, 40K context): Ollama's own Q4_K_M build of the 8B model, not a file of Qwen's GGUF repository. It is not the default here. It stays the candidate ADR-084 recorded, named in the profile where an operator has pulled it.

`application.yaml` names no embedding model today. Spring AI's `OllamaEmbeddingModel` is built with whatever its options carry, and `ChunkEmbedder` names the model on every request, so the bean's own default has never been read.

## Decision

### 1. Where the name comes from, in order

1. **`spring.ai.ollama.embedding.options.model` in `application.yaml`**, carrying `bge-m3:latest`. It sits where Spring AI's auto-configuration reads it, beside `spring.ai.ollama.chat.options.model`, for ADR-114's reason: the bean and the call name one model, not two.
2. **The profile's `embeddingModel`**, when set, overriding it. A corpus scored under another model is a judgement about that corpus, and the profile is where a judgement travels with its provenance (ADR-061). A blank value is unset, as `ProfileValue.isSet()` already reads one, so `embeddingModel: ""` resolves to the default.
3. **`EmbeddingModelGate` resolves the two**, reading the key as `${spring.ai.ollama.embedding.options.model:}`, and answers the name or none. `StageRuns.embeddingModelNamed()`, `StageFiveGates` and the scoring run's mint read it as they read it now. Nothing else in how the name reaches the scoring run changes.

### 2. The gate stays, and is shut only where neither names a model

`EmbeddingModelGate` answers none only where the profile's `embeddingModel` is unset and the configured key is blank or absent. Then stage 5 is gated exactly as ADR-084 gated it: before the scoring run is minted, the invocation ends, the job succeeds, and each step's shut sentence is the one it is now, *"is gated: no embedding model is named"*. With the configuration as shipped no operator meets it.

It stays a gate, rather than becoming ADR-114's stop, for three reasons:

- **It is the state `CONTEXT.md` defines a gate as**: a value the pipeline requires and does not have. Supply it and no gate occurs; leave it unset and the run ends, having recorded everything it learned.
- **Blanking the key is something an operator can mean.** For the generation model a blank default asks for nothing, so ADR-114 calls it a misconfiguration. For the embedding model it is how an operator holds scoring until they have chosen a candidate, which is ADR-084's own posture made available by choice: the corpus walked, extracted and deduplicated, and nothing embedded under a model nobody picked.
- **A stop would fail the job.** An operator who blanked the key to wait would get a failed invocation for doing so.

### 3. A scoring run is identified by the name resolved, not by where it was named

`EmbeddingScoringConfigConsumed` is unchanged: the name resolved, the digest and weight dtype Ollama reports for it, and the rest as ADR-228 and ADR-273 left them. A profile naming `bge-m3:latest` and a profile naming nothing, under the shipped default, arrive at one scoring run. A profile naming another model is another scoring run (ADR-084, ADR-228). Where the name came from is in no run's identity, as an instrument's identity is never of where it is served: two sources naming the same weights are one instrument.

### 4. Nothing is written into the profile when the default applies

The key stays unset, with no provenance, as `generationModel` does under ADR-114. The profile is authored by a person (`CONTEXT.md`), and writing the default into it is the one thing ADR-084's second reason objects to: a key with a value nobody wrote, looking answered. Unset, the key still reads as nobody having chosen, which is true.

The name the survivors were scored under is recorded where it was recorded before: the scoring run's settings, the translations page, and the label file's stamp. The deliverable's index states `embeddingModel` as written (ADR-186 §2), so it reads unset where the default applied.

### 5. The closing line no longer asks for the embedding model

`embeddingModel` leaves `NextAction`'s run values. It is never named as a value wanted, and never credited as set, as `generationModel` is neither. ADR-098's second row wants `boilerplateDocumentFrequencyFloor` alone: that floor is read off the page stage 3 writes, so it still stops invocation 1, and the number of invocations does not move.

Where the gate is shut, the key blanked and the profile naming none, the invocation ends on the line it ends on today when no questions were written, *"Next: fix what the gated line above names, and run again"*, and the gated line above names the model. No log text changes.

### 6. A default nobody pulled is refused, not gated

An operator whose Ollama does not serve `bge-m3:latest` reaches stage 5 with the seed and floor gates open and meets ADR-202's refusal: one line naming the model and saying it is not served here, the stage failed, nothing sent, and no scoring run minted. Where they met a gate before, they meet a stop, because a value is now named and the engine cannot serve it. `ollama pull bge-m3:latest`, or another model named in the profile, is the remedy.

### 7. The default is changed only by a record

ADR-114 lets the generation default change without a record, in ADR-013's terms. This one does not. A changed default changes, for every profile that names no model, the scoring run and every run after it, re-chunks and re-embeds the corpus, closes the arrangement gate (ADR-107), and puts every relevance verdict under another model. Those are ADR-084's grounds, kept: the embedding model keys every vector and every relevance verdict, and its quality cannot be read off the output. So the shipped value is held exactly by a test, and a different one is a decision of its own.

`bge-m3:latest` is the operator's choice, not a bake-off's winner. A bake-off still decides the embedding model (ADR-034); a candidate is named in the profile, and its scoring run stands beside the default's.

### 8. Run ids

The change is to `pipeline` (`EmbeddingModelGate`, `NextAction`), to `application.yaml`, and to javadoc in `profile`. No stage's version names `pipeline` or `profile` (`StageModules`), and the application configuration is in none, so no run id moves. A profile with `embeddingModel` set arrives at the runs it did. A profile without one, under the shipped default, mints its first scoring run once the seed and floor gates are open: runs minted, none moved.

## Alternatives refused

- **No gate at all, a blank key a stop, as ADR-114 has it.** It fails the job of an operator who blanked the key on purpose (§2), and every test context that leaves the model unset would need a model or reach an Ollama under the shipped name.
- **A key under `vespera.*`.** It names the model twice, once for Spring AI's bean and once for the call (ADR-114).
- **Writing the default into the profile when census drafts it, or when stage 5 resolves it.** A value nobody wrote would look answered (§4).
- **Where the name came from in the scoring run's identity.** One model's weights would be two runs.
- **`qwen3-embedding:8b`, ADR-084's candidate.** It is not pulled on the operator's machine, and a default nobody pulled stops stage 5 (§6). It stays a candidate for the profile.

## Consequences

- **`application-test.yaml` blanks `spring.ai.ollama.embedding.options.model`**, so a test context scores only under a model a test names, the gate shuts in every test that names none as it does now, and no test reaches an Ollama under the shipped name. `ShippedConfigurationTest` reads the shipped file alone, and holds the default there.
- **README** names `bge-m3:latest` as the default in its value table and in its pull instructions, and drops `embeddingModel` from the second row of its invocation table, in the change that ships the default.
- **The comment above `spring.ai.ollama.chat.options.model`**, which says the embedding model has no default, and the javadoc of `EmbeddingModelGate`, `GenerationModel`, `StageFiveGates`' `EMBEDDING_MODEL`, `StageRuns.embeddingScoring()` and `Profile`'s `embeddingModel`, are corrected in the same change (ADR-216). `Profile`'s `embeddingModel` becomes a key whose unset state means a default applies, the third after `generationModel` and `generationContextWindow`.
- **`docs/running-stage-by-stage.md`'s** *"Needs: `embeddingModel` set"* becomes the model resolved and pulled.
- **`CONTEXT.md` gains nothing.** "Model" stays the tools' word, and the Gate entry describes the narrowed gate as it stands.

## Tests

| Method | What it holds |
| --- | --- |
| `pipeline.EmbeddingModelGateTest.resolvesTheConfiguredDefaultWhenTheProfileNamesNone` | an unset `embeddingModel` resolves to the configured key's value (§1) |
| `pipeline.EmbeddingModelGateTest.prefersTheProfileOverTheConfiguredDefault` | a value in the profile wins over the configured one (§1) |
| `pipeline.EmbeddingModelGateTest.treatsABlankProfileValueAsUnanswered` | `embeddingModel: ""` resolves to the configured value (§1) |
| `pipeline.EmbeddingModelGateTest.opensOnTheProfileAloneWhenConfigurationNamesNone` | a blank configured key and a named model open the gate on the profile's name (§2) |
| `pipeline.EmbeddingModelGateTest.closesOnlyWhenNeitherTheProfileNorConfigurationNamesAModel` | a blank configured key and an unset profile answer none (§2) |
| `pipeline.ADefaultAndTheSameModelInTheProfileAreOneScoringRunTest.theModelsSourceIsNotPartOfTheScoringRunsIdentity` | a model named in the profile and the same name as the configured default arrive at one scoring run (§3) |
| `ShippedConfigurationTest.namesTheEmbeddingModel` | `application.yaml` carries `bge-m3:latest` under `spring.ai.ollama.embedding.options.model`, exactly (§1, §7) |
| `pipeline.NextActionTest.withNothingSetTheLineNamesEveryValueTheNextInvocationNeeds` | with nothing set, the line names the boilerplate floor and not `embeddingModel` (§5) |
| `pipeline.NextActionTest.withTheRunValuesSetTheNextActionIsToLabel` | the run values credited as set are the seed folder and the floor (§5) |
| `pipeline.NextActionTest.withOnlyTheSeedFolderSetTheLineCreditsIt` | with the seed folder set, the floor is the one value still wanted (§5) |
| `pipeline.NextActionTest.aMistypedFloorIsReportedAndNothingElseIsAskedFor` | a mistyped floor beside a set seed folder is reported, and `embeddingModel` is not asked for (§5) |

## What this does not decide

- **Whether the closing line names the model the next invocation will score with**, as ADR-114 has it name the model 6b will write with.
- **Whether the deliverable's index names the embedding model the survivors were scored under**, beside the profile's keys as written.
- **The bake-off** (ADR-034), and which model, if any, replaces `bge-m3:latest` as the default.
