# ADR-353 — The embedding model's shipped default is Qwen3-Embedding-8B, named as its file on Hugging Face, `hf.co/Qwen/Qwen3-Embedding-8B-GGUF:Q4_K_M`, and only a profile naming no model moves its scoring run

- **Date**: 2026-11-05
- **Status**: accepted on 2026-11-05.
- **Settles**: that the embedding model `application.yaml` ships is Qwen3-Embedding-8B in place of `bge-m3:latest`, named `hf.co/Qwen/Qwen3-Embedding-8B-GGUF:Q4_K_M`, which is the operator's decision of 2026-11-05, and the record ADR-298 §7 asks for before the default changes; and §7's two rules for the tests, also the operator's of 2026-11-05. §2 to §6 are decided under the first.
- **Amends**: [ADR-298](0298-the-embedding-model-ships-with-a-default-in-application-configuration-which-the-profile-overrides-and-stage-5-is-gated-only-where-neither-names-one.md), in these places and no others: its title's and §1's value, `bge-m3:latest`, which is `hf.co/Qwen/Qwen3-Embedding-8B-GGUF:Q4_K_M` (§1); its Context's *"It is not the default here. It stays the candidate ADR-084 recorded, named in the profile where an operator has pulled it"*, and its sentence that `qwen3-embedding:8b` is *"Ollama's own Q4_K_M build of the 8B model, not a file of Qwen's GGUF repository"*, whose model layer is that file byte for byte (§2); §7's *"`bge-m3:latest` is the operator's choice, not a bake-off's winner"*, which is said of Qwen3-Embedding-8B (§1); its refused alternative *"`qwen3-embedding:8b`, ADR-084's candidate"*, refused here on another ground (§2); §7's *"So the shipped value is held exactly by a test, and a different one is a decision of its own"*, its Consequences' *"`ShippedConfigurationTest` reads the shipped file alone, and holds the default there"*, and its Tests row for `ShippedConfigurationTest.namesTheEmbeddingModel`, *"carries `bge-m3:latest` … exactly"*: the record holds which model ships, and no test names it, a test holding only that a name is shipped, one name and not blank (§7); and the third bullet of *What this does not decide*, which model replaces `bge-m3:latest`, decided here.
- **Amends**: [ADR-329](0329-vespera-pulls-into-ollama-at-the-step-that-needs-it-a-model-ollama-does-not-serve-and-never-pulls-one-it-serves.md), its Consequences' first bullet, *"A fresh machine with no models pulls `bge-m3:latest`, 1.2 GB"*: it pulls `hf.co/Qwen/Qwen3-Embedding-8B-GGUF:Q4_K_M`, 4,676,804,928 bytes of weights (§6). Its rule stands whole.
- **Amends**: [ADR-331](0331-the-control-panel-opens-a-window-of-its-own-and-its-pages-are-a-cockpit-with-a-picker-an-example-and-a-hint-on-its-fields.md) §8, its table's example for `embeddingModel`, `bge-m3:latest`, which is the new name, as `generationModel`'s example is its own default (§5).
- **Keeps**: ADR-298 §2 to §6 and §8's rule, the gate, the source of the name outside a run's identity, nothing written into the profile, the closing line; [ADR-228](0228-a-scoring-run-names-the-embedding-models-artefact-and-reads-the-vectors-of-one-embedder-identity.md), a scoring run names the model's name, digest and weight dtype; [ADR-084](0084-the-embedding-model-is-a-profile-gate-and-a-vector-carries-its-whole-embedder-identity.md), the six-part embedder identity and its empty instruction; [ADR-034](0034-embedding-model-chosen-by-bake-off-not-argument.md), a bake-off still decides the embedding model; [ADR-144](0144-a-chunk-the-runtime-refuses-as-too-long-is-split-until-every-piece-fits.md), a chunk refused as too long is split; [ADR-085](0085-vectors-live-in-sqlite-and-the-pairwise-matrix-is-never-materialised.md) and [ADR-087](0087-clusters-are-modularity-communities-over-a-k-nearest-neighbour-graph-built-in-blocks.md), sized at 4,096 dimensions; ADR-329, the pull at the step that needs the model.

## Context

ADR-298 shipped `bge-m3:latest`, BAAI's M3 model of 567 million parameters, FP16, 1.2 GB, 1,024 dimensions, and kept Qwen3-Embedding-8B the candidate ADR-084 had weighed, named in the profile. Its §7 lets the default change only by a record, because a changed default changes, for every profile naming no model, the scoring run and every run after it.

### What the two names of the model hold

Read on 2026-11-05 from Hugging Face's API and the two registries' manifests:

- **Qwen's repository**, `Qwen/Qwen3-Embedding-8B-GGUF` at revision 69d0e58a13e463cd99a9b83e3f5fee7c10265fab, serves `Qwen3-Embedding-8B-Q4_K_M.gguf`, 4,676,804,928 bytes, sha256 3fcd3febec8b3fd64435204db75bf0dd73b91e8d0661e0331acfe7e7c3120b85.
- **The Ollama manifest Hugging Face's registry serves for `:Q4_K_M`** names that file as its model layer, a template layer, sha256:e94a8ecb9327ded799604a2e478659bc759230fe316c50d686358f932f52776c, and a config, sha256:6cd342cb4e4d0e18f669aca8b7ffe09014c9591d72b1466e3d3d53fb65620c9f, whose `file_type` is `unknown`.
- **The manifest of Ollama's library tag `qwen3-embedding:8b`** names the same model layer, sha256:3fcd3febec8b3fd64435204db75bf0dd73b91e8d0661e0331acfe7e7c3120b85, no template layer, and another config, sha256:0a3d61b01340ca9dceb4a661e21b3dfe1418ed5206d91291ab933a3762f8bb89, whose `file_type` is `Q4_K_M`.
- **Qwen's `config.json`** for `Qwen/Qwen3-Embedding-8B` gives a hidden size of 4,096 and 40,960 positions.

So the two names carry one set of weights under two manifests.

### What a scoring run is identified by

`StageRuns.mintEmbeddingScoring` records `EmbeddingScoringConfigConsumed`: the root, the name `EmbeddingModelGate.modelName()` resolved, and the digest and weight dtype `OllamaClient.artefactOf` reads off `/api/tags` for that name, then the measurement run, the floors and the presets (ADR-228 §1, ADR-330 §9). The digest is the manifest's, over its layers and its config (`ModelArtefact`), not the model layer's. The weight dtype is `/api/tags`'s `quantization_level`, which Ollama 0.33.2, the image `compose.yaml` names, reads from the config's `file_type` and, where that is `unknown`, from the GGUF file's own `general.file_type` (`server/model_list_cache.go`), read from its source and not run. The stage's implementation version is that of the modules `StageModules` names for it, `embedding` and `extraction` (ADR-058), each the last commit touching `src/main/java/io/algernon/vespera/<module>` (`.mvn/scripts/implementation-versions.groovy`).

A vector is stored under an embedder identity of the model's name, the same digest and dtype, the vector's length and the instruction (`ChunkEmbedder`, `EmbedderIdentity`). A chunk is cut by `ChunkingRule.DEFAULT`, 512 words, `words-512-v1`, which names no model.

## Decision

### 1. The default is `hf.co/Qwen/Qwen3-Embedding-8B-GGUF:Q4_K_M`

`spring.ai.ollama.embedding.options.model` in `application.yaml` carries `hf.co/Qwen/Qwen3-Embedding-8B-GGUF:Q4_K_M`, written unquoted, as Ollama names a model file on Hugging Face and sent as written (ADR-329). Nothing else in how the name is resolved, gated or recorded changes (ADR-298 §1 to §5).

It is the operator's choice, not a bake-off's winner, as `bge-m3:latest` was. ADR-084 weighed it against ADR-033's criteria and named it the leading candidate; a bake-off still decides (ADR-034), its candidates named in the profile.

`bge-m3:latest` stays usable, named in the profile's `embeddingModel` or in a machine's own configuration, and a corpus scored under it arrives at its scoring run as before (§3).

### 2. Named by its file on Hugging Face, not by Ollama's library tag

**A scoring run follows the name and the manifest, not the weights.** `qwen3-embedding:8b` and `hf.co/Qwen/Qwen3-Embedding-8B-GGUF:Q4_K_M` hold one model layer, and are two names and two manifests: their configs differ, and the hf.co manifest has a template layer the library's has not. So they are two scoring runs, and two embedder identities, whose vectors are not read across (ADR-228 §1, §4). Neither can be had for the other by choosing a default.

**The hf.co name is the one `profile.yaml`'s `embeddingModel` names on the operator's machine.** Shipped as the default, it leaves a working directory whose profile names it at the scoring run that profile arrives at, whether the key stays or is cleared (§3), and its Ollama serving it already, nothing is pulled (ADR-329). The library tag would put every such corpus at a new scoring run that embeds every chunk again under weights it already has vectors for.

**It is named as the file of Qwen's own repository**, whose sha256 anyone can set beside Hugging Face's listing, which is the artefact ADR-084 weighed.

**`Q4_K_M`, not another quantization**, because it is the quantization the operator serves. Another is another name, another manifest and another run, as ADR-084 records of `Q8_0`.

ADR-298 refused `qwen3-embedding:8b` because it was not pulled on the operator's machine and a default nobody pulled stopped stage 5 (its §6). ADR-329 pulls a model Ollama does not serve at the step that needs it, so that ground no longer holds for any name; this record refuses the library tag on the ground above.

### 3. Which run ids move

**No implementation version moves.** The change is to `application.yaml`, to javadoc in `pipeline` (`EmbeddingModelGate`) and in `profile` (`Profile`), to the visibility of `pipeline`'s `EffectiveValuesFile.THE_CODES_LABEL_MODEL`, package-private for its test (§7), and to `io.algernon.controlpanel.ProfileKey`. No stage's version names `pipeline` or `profile` (`StageModules`, ADR-222, ADR-274 §5); `io.algernon.controlpanel` lies outside `src/main/java/io/algernon/vespera`, whose folders are the modules the build versions; and `application.yaml` is in none. So a run id moves only where its recorded settings do.

**A profile whose `embeddingModel` is unset, or blank, under the shipped configuration**:

- **Its embedding-scoring run is minted again.** Its recorded `embeddingModel` is the new name, and `embeddingModelDigest` and `embeddingModelWeightDtype` are what Ollama reports for it.
- **Its arrangement and generation runs are minted again**, through their upstream runs: `ArrangementConfigConsumed` names the scoring run's id, and the generation run the approved arrangement's.
- **Byte-level reduction, extraction, content census, content redundancy and seed measurement keep their run ids.** None of their recorded settings names the embedding model (`ExtractionConfigConsumed`, `ContentCensusConfigConsumed`, `ContentRedundancyConfigConsumed`, `SeedMeasurementConfigConsumed`), and no module they name changes. The census is a walk, and no walk moves.

**A profile that names `hf.co/Qwen/Qwen3-Embedding-8B-GGUF:Q4_K_M`**: no run id moves. The name resolved is the same, Ollama reports the same digest and dtype for it while it serves the same pull, and no implementation version moves. Clearing the key afterwards arrives at the same runs, the source of the name being in no run's identity (ADR-298 §3).

**A profile that names any other model**, `bge-m3:latest` and `qwen3-embedding:8b` among them, and a machine whose own configuration names one over the shipped file: no run id moves.

**What a moved scoring run costs**, as ADR-228 §7 gives it for a changed artefact: the embedding step sends every chunk to Ollama again and stores its vector under the new embedder identity, the vectors under `bge-m3:latest` staying where they are and read by nothing while no run names them (ADR-229); no chunk is cut again, `chunk_cache` being keyed by no model; every survivor is scored again; `relevanceScoreFloor` removes nothing under the new run until the sample is answered from the label file that run writes, the earlier answers carrying `bge-m3:latest`'s identity (ADR-088 §3, ADR-228 §5); the arrangement must be approved again, unless `arrangementApproved` is `auto` (ADR-107); and every synthesis doc is asked for again. The runs under `bge-m3:latest` stay recorded and are arrived at again where the profile names it (ADR-156).

**`translationSimilarityFloor` is a number on the embedding model's scale** (ADR-271): set where it was read off `bge-m3:latest`'s similarities, it enters the new scoring run as written, and is applied to Qwen3-Embedding-8B's.

### 4. What the width of a vector changes, and what holds

Qwen3-Embedding-8B returns vectors of 4,096 components where `bge-m3:latest` returned 1,024. No `dimensions` option is sent (ADR-084, `ChunkEmbedder`), so the identity names `dimension=4096`.

- **The schema holds.** `vector.embedding` is a BLOB of little-endian float32 at full dimension, and no column, constraint or index names a width; `schema.sql` already sizes it at 16 KiB per 4,096-dimension vector. No DDL, no schema version.
- **The `vector` table grows four times as fast**: 16 KiB of BLOB for each chunk, where it was 4 KiB.
- **Clustering's block holds.** `Clustering.BLOCK_BUDGET_BYTES` is 64 MiB, set at ADR-087's table's widest dimension; the widest vector at which a block keeps its whole `BLOCK_DOCUMENTS`, 4,096, is that budget divided among them, 4,096 components, so at Qwen3-Embedding-8B's width a block is whole and fills the budget exactly, and two blocks stay 128 MiB. The translation step's mutual nearest neighbours take the same block (`TranslationOf`).
- **The seed side is four times as large**: ADR-085's 16 KiB per resident seed chunk is now the shipped width, and its 314 MiB for 20,000 documents' vectors the shipped case, where `bge-m3:latest` gave 80 MiB.
- **The chunks hold.** 512 words a chunk, named by no model, measured against no tokenizer (ADR-091).
- **The limit on one chunk holds as ADR-144 left it.** The limit it measured is Ollama's batch, 2,048 tokens by default, and not the model's context, 40,960 positions here; Vespera sends neither `num_ctx` nor `num_batch`. Qwen's tokenizer is not `bge-m3`'s, so which chunks are refused differs; a refused chunk is split until every piece fits, whichever model refuses it.
- **No batch size ships.** Each embed call carries one chunk (`ChunkEmbedder`).
- **The instruction stays empty** (ADR-084), on both sides of ADR-020's comparison, though Qwen's card asks for one on the side that queries.
- **Nothing else of `application.yaml` changes.** The generation model, `generationContextWindow` and the ten presets of ADR-330 name no embedding model.

### 5. The control panel names the new default

`ProfileKey.EMBEDDING_MODEL`'s hint becomes *"whichever model you can serve locally, if you want one other than hf.co/Qwen/Qwen3-Embedding-8B-GGUF:Q4_K_M"*, the README's row as ADR-331 §8 has it, and its example becomes `hf.co/Qwen/Qwen3-Embedding-8B-GGUF:Q4_K_M`, the shipped default, as `generationModel`'s is `qwen3:8b`. A field left empty still shows the value in use the jar wrote, not the example (ADR-334 §1).

### 6. A fresh machine pulls 4.7 GB at invocation 2

A machine whose Ollama serves no embedding model pulls `hf.co/Qwen/Qwen3-Embedding-8B-GGUF:Q4_K_M` inside stage 5's scoring step of invocation 2: 4,676,804,928 bytes of weights and the 1,573 bytes of its template, where `bge-m3:latest` was 1.2 GB. ADR-329's 600 seconds without a byte more stand: they bound a pull that stops moving, not its length.

### 7. How the tests hold the default

Two rules, the operator's of 2026-11-05, bind every test:

1. **No test holds a model's name as what is expected**, of any model, the embedding model, the generation model or the label model: not the shipped default, not the control panel's example or hint, not a fixture's own choice written out a second time. A test hands a model a name of its own, a name no registry serves, as input, and expects that name back through the one constant it handed in; no claim is about which model ships. An integration test that pulls a model into a real Ollama names a real one, as input; a javadoc that says which model a measurement was taken on names it, as the measurement's.
2. **A test sets the values it needs itself**, as a test property, a profile, an environment it builds or a model name of its own, and claims what happens under them. It expects nothing of how the shipped `application.yaml` or the machine is configured. A claim that two places agree with the shipped file reads the shipped value and compares the two; it names no value.

So **this record, and not a test, holds which model ships** (ADR-298 §7 as amended): a changed default is a decision of its own because it is a record of its own, and it changes `application.yaml`, the README's lines and `ProfileKey`, and no test. What the tests hold of the default is its form and its agreement:

- **A name is shipped, one name with no space in it, and not blank** (`ShippedConfigurationTest.namesTheEmbeddingModel`), as the generation model's is (ADR-114).
- **`ProfileKey.EMBEDDING_MODEL`'s example is whatever the shipped file names, and its hint names it** (§5), read from the file.
- **The README's row is the hint** (ADR-331 §8), and **the README's model sentence and `ollama pull` line are the shipped file's**, read by `docs/check-claims.mjs` from `application.yaml`.
- **A test of the control panel or of the effective values names a model of its own**, as the value the jar said, and claims the page and the reading show that one.
- **A test of the clustering block takes a width it computes**, the widest the byte budget allows a whole block, and no model's.
- **A test of what the jar says it ships reads the shipped value**: the generation model `effective-values.properties` names from the source `shipped` is the one `application.yaml` names, read from it.
- **The code's own fallback for the label model**, which no configuration a test supplies can stand for, is read from the code: `EffectiveValuesFile.THE_CODES_LABEL_MODEL` is package-private, as `SidecarStartSettings`' default addresses are, and the test of the effective values names the constant.

`application-test.yaml` keeps the embedding model blank (ADR-298 Consequences), so no test context scores under the shipped name: a test that scores names its model.

## Alternatives refused

- **`qwen3-embedding:8b`, the same weights under Ollama's library tag.** Another name and another manifest are another scoring run and another embedder identity (§2), so every corpus the operator's profile now scores under the hf.co name would be embedded again for nothing.
- **Another quantization of the hf.co file, `Q8_0` or `F16`.** Not the weights the operator serves; another run (§2).
- **Keeping `bge-m3:latest` and naming Qwen3-Embedding-8B in each profile.** A profile naming no model would go on being scored under a model its operator does not use.

## Consequences

- **`application.yaml`**: `spring.ai.ollama.embedding.options.model: hf.co/Qwen/Qwen3-Embedding-8B-GGUF:Q4_K_M`, unquoted, the comment above it citing ADR-298 and this record. `docs/check-claims.mjs` reads the value unquoted and holds the README's *"That is …, unless you set `embeddingModel`"* and its `ollama pull` lines to it.
- **`ProfileKey.EMBEDDING_MODEL`**: its hint and example as §5 writes them.
- **Javadoc corrected in the same change** (ADR-216): `EmbeddingModelGate`'s *"shipped as `bge-m3:latest`"* and `Profile`'s `embeddingModel`, *"application configuration carries `bge-m3:latest`"*, each naming the new default and citing this record.
- **`README.md`**: the table's row for `embeddingModel`, the sentence naming the model documents are scored under, and the first `ollama pull` line name the new default; its example of a model file on Hugging Face stays `Q8_0`, another quantization.
- **`docs/architecture.md`'s row for the embedding model** and **`docs/running-stage-by-stage.md`'s** rows for 5c and 5d, its *Set first* line for invocation 2 and its *Needs* line for the scoring step name the new default.
- **The tests are brought under §7's rules where they hold a model**: `ShippedConfigurationTest`'s two, `ClusteringTest`'s block, `TheJarWritesItsEffectiveValuesInvocationTest`'s generation model, read from the shipped file, and every unit test that named a real model, embedding, generation or label, as a fixture's input or expected value, each given a name of its own.
- **ADR-298, ADR-329 and ADR-331 carry a note at their heads** naming this record. No other line of those files is edited.
- **`CONTEXT.md` gains nothing.**
- **Every corpus whose profile names no model is scored again**, at the cost §3 gives. A profile that names the new default, or any model, is untouched.

## Tests

| Method | What it holds |
| --- | --- |
| `ShippedConfigurationTest.namesTheEmbeddingModel` | `application.yaml` names a model under `spring.ai.ollama.embedding.options.model`, one name with no space in it, not blank; which one it is no test holds (§1, §7) |
| `ShippedConfigurationTest.theControlPanelsExampleAndHintNameTheShippedEmbeddingModel` | `ProfileKey.EMBEDDING_MODEL`'s example is the name the shipped file carries, read from it, and its hint names it as the one to replace (§5, §7) |
| `controlpanel.FieldHintsScreenTest.everyProfileKeyHasTheHintTheReadmesTableGivesIt` | the hint the pages show for `embeddingModel` is the README's row (§5) |
| `pipeline.ADefaultAndTheSameModelInTheProfileAreOneScoringRunTest.theModelsSourceIsNotPartOfTheScoringRunsIdentity` | a profile naming the default's name and a profile naming none arrive at one scoring run, so clearing the key moves nothing (§3) |
| `pipeline.APullOfTheEmbeddingModelIsADifferentScoringRunInvocationTest.aPullIsADifferentScoringRunThatNamesItsDigest` | another digest under one name is another scoring run, the ground on which the library tag is another run (§2) |
| `embedding.ClusteringTest.upToTheWidthTheBudgetAllowsAWholeBlockABlockKeepsEveryDocument` | at the width it computes from the budget and the document count a block is whole and within the budget, and one component wider it holds fewer (§4) |
| `controlpanel.ProfileEditorScreenTest`, `pipeline.TheControlPanelReadsTheEffectiveValuesTheJarWritesTest` | each names an embedding model of its own as the value the jar said, and claims the page and the reading show that one (§7) |
| `pipeline.TheJarWritesItsEffectiveValuesInvocationTest.anInvocationWritesTheValuesBeneathTheProfile` | the generation model the jar says it ships, from the source `shipped`, is the one `application.yaml` names, read from it (§7) |

`docs/check-claims.mjs`, in the claims job, holds the README's model sentence and pull lines to `application.yaml`, read from it (§1, §7).

**What no test holds**: which model ships, which this record holds (§7); that Ollama reports another digest for the two names, and the dtype it reports for the hf.co name, which no fixture serves; the size of the pull; that Qwen3-Embedding-8B's width is 4,096; and that a chunk of 512 words fits Qwen's tokenizer under Ollama's batch. None was run against Ollama.

## What this does not decide

- **Whether Qwen3-Embedding-8B scores a corpus better than `bge-m3:latest`.** That is a bake-off's (ADR-034).
- **An instruction on the query side**, which Qwen's card recommends and ADR-084 does not send.
- **A truncated width**, which Qwen3-Embedding-8B allows: another identity, beside this one (ADR-085).
- **How long embedding takes under an 8-billion-parameter model, and how much of a graphics card's memory it needs.** Neither is measured.
- **Which other tests change under §7's rules.** The rules bind every test; this record brings under them those that hold a model, and changes no test that holds another shipped value.