# ADR-091 — There is no tokenizer: the runtime counts tokens, and the embedder identity is what Ollama reports plus what we send

- **Date**: 2026-09-07
- **Status**: accepted
- **Amends**: [ADR-044](0044-the-bake-off-re-chunks-per-candidate-model.md) — its "cache key must carry tokenizer identity" keeps the slot and loses the tokenizer; [ADR-084](0084-the-embedding-model-is-a-profile-gate-and-a-vector-carries-its-whole-embedder-identity.md) — its "weights digest" is a manifest digest here, and its "output dimension" is the value sent rather than the value reported

## Context

[ADR-084](0084-the-embedding-model-is-a-profile-gate-and-a-vector-carries-its-whole-embedder-identity.md) settled that a vector's identity is the whole embedder in six parts, and [#94](https://github.com/algernon28/vespera/issues/94) §13 flagged two of them as unobtained: **how the weights digest is learned**, and **which tokenizer** produces the `tokenizer_identity` that `chunk_cache` and therefore `vector` are keyed by. Both were deliberately left for measurement rather than argument ([#103](https://github.com/algernon28/vespera/issues/103)).

Measuring them dissolved one of the two questions entirely. The record is in [`docs/research/embedding-tokenizer-sourcing.md`](../research/embedding-tokenizer-sourcing.md) and [`docs/research/embedding-parameters.md`](../research/embedding-parameters.md); the claims below cite it rather than repeating it.

### Why a chunk needs a ceiling at all

Three separate reasons, and only the first is about tokens:

1. **The model's input limit is hard.** Text past it is truncated and the resulting vector describes only the beginning, with nothing reporting that.
2. **One vector per chunk means a longer chunk discriminates less.** An embedding is a single point for the whole chunk, so the more topics it spans the more it is an average of them.
3. **[ADR-020](0020-relevance-scoring-function.md)'s score is the mean of the top-3 chunk similarities.** That means something only if a document has more than three chunks; unbounded chunks would silently reduce it to "the only chunk there is".

The candidate recorded in ADR-084 has a **32k** context window, against a ceiling of 512. So reason 1 has a 64× margin and constrains nothing, and reasons 2 and 3 — which do constrain it — have nothing to do with tokens. **Tokens entered this design only because overflow is measured in them.**

### What the runtime will and will not do

- **Ollama tokenizes on every embedding request**, internally, from the vocabulary in the GGUF. This was never in doubt and is not what was missing.
- **Ollama will not tokenize on request.** Its API documents fifteen endpoints and none of them tokenizes text or counts tokens. llama.cpp's server has `POST /tokenize`; Ollama does not surface it. Four upstream requests exist ([#3582](https://github.com/ollama/ollama/issues/3582), [#4186](https://github.com/ollama/ollama/issues/4186), [#9229](https://github.com/ollama/ollama/issues/9229) closed; [#12031](https://github.com/ollama/ollama/issues/12031) open) and [PR #12030](https://github.com/ollama/ollama/pull/12030) has been open since 2025-08-22.
- **But it will reject what does not fit.** `/api/embed`'s `truncate` parameter is documented as *"truncates the end of each input to fit within context length. **Returns error if `false` and context length is exceeded.** Defaults to `true`"*.

That last line is the whole decision. Overflow can be enforced *exactly*, by the only component that knows, without anyone predicting anything.

### Why counting tokens ourselves cannot be made correct

Reconstructing the model's tokenizer was the obvious path and it is a dead end, measured rather than argued:

| Finding | Measured |
|---|---|
| The vocabulary **is** retrievable from Ollama — `POST /api/show` with `"verbose": true` returns the full token list and merge table | `qwen3-embedding:0.6b`, 27 KB → 4.1 MB, all 151,669 tokens and 151,387 merges, model never loaded, 51 ms |
| For BPE the GGUF vocabulary is byte-faithful to HuggingFace's | 0 of 151,669 token strings differ; merges identical in order |
| **A vocabulary is not a tokenizer.** HuggingFace's `tokenizer.json` and the live runtime diverge on Unicode normalisation form, which GGUF has no key for | NFD `"Tiếng Việt"`: **81 tokens** by the file, **222** by the runtime — up to **2.94×** |
| A naive count off `tokenizer.json` is wrong anyway | `all-MiniLM`'s file embeds `padding: Fixed 128`, so `encode("hello world")` returns **128** ids |
| The budget cannot be read off reported metadata either | `*.context_length` reported 512, measured 256 |

The normalisation divergence is decisive and it lands on **this** corpus specifically: the normaliser is declared in `tokenizer.json` as `{"type": "NFC"}`, there is no GGUF key for it, and [`docs/research/ntfs-java-file-walking.md`](../research/ntfs-java-file-walking.md) measured NFD text in this archive. A tokenizer of our own would have mis-budgeted accented Italian by up to three times, silently. **No reconstruction from any published artefact can match the runtime, because the deciding step is not published.**

### A library version really does move boundaries

The remaining question was whether a tokenizer library could shift counts under an unchanged vocabulary. It can, with a reproduced incident: HuggingFace `tokenizers` **0.19.0 → 0.19.1** — a patch release with a one-line note — began honouring `"ignore_merges": true`, taking Vietnamese prose from **20 to 16** tokens and Turkish from **18 to 16** on one unchanged file. That boundary sits inside `ai.djl.huggingface:tokenizers`' own history at **v0.27.0 → v0.28.0**, shipped as a bare dependency bump. llama.cpp met the same bug from the other side ([PR #7193](https://github.com/ggml-org/llama.cpp/pull/7193)), and [PR #6920](https://github.com/ggml-org/llama.cpp/pull/6920) records that unconverted GGUF files "will fallback to the 'default' pre-tokenization, which in almost all cases is wrong", with perplexity moving 8.9 → 11.5.

Recorded against it: across crate versions 0.20.3, 0.21.1 and 0.23.2 a 25-case battery found **0 differences in 50 combinations**. The hazard is episodic, not continuous — which is exactly what makes it dangerous, because it would be met once, years apart, as a threshold that quietly stopped matching its data.

## Decision

### There is no tokenizer in this system

**Nothing in Vespera handles a token.** No tokenizer abstraction, no vocabulary, no `tokenizer.json`, no JVM tokenizer library, no GGUF reader. The two jobs tokens were standing in for are separated and each given the mechanism that can actually do it:

| Job | Mechanism |
|---|---|
| **Overflow must not happen silently** | `/api/embed` is called with **`truncate: false`**. The runtime rejects what will not fit, using its own tokenizer. A rejected chunk is split and retried. |
| **Chunks must be small enough to discriminate** | A **deterministic size budget** in a unit we can measure without a model — **whitespace-separated words** — enforced by the chunker. |

Words rather than characters, of the two units available: a word count is the closer proxy to a token count, so the divergence this decision has to report stays small, and it is the unit the boundaries are already cut at — the chunker packs words, never characters. Neither property is load-bearing, which is the point: the choice is cheap because nothing rests on its precision. The budget does **not** need to be token-exact, because it is no longer protecting against overflow. It needs to be *deterministic*, which is the property `chunk_cache` exists to persist.

`ai.djl.huggingface:tokenizers` is therefore **not** added to the pom, and [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md)'s rule is satisfied by absence: no decision requires it.

### ADR-044's key slot survives; its occupant changes

ADR-044 requires that "cache key must carry tokenizer identity". That was right about the **slot** and wrong only about what fills it: the key must carry whatever determines the boundaries, and that is now the budgeting rule. So the slot stays, holding a **chunking-rule identity** — the rule and its value — beside the chunker's own `docling-hybrid-chunker-v1`. Between them the two fully determine a chunk's boundaries, which is what the key was always for.

Keeping the name `tokenizer_identity` over a value that is not a tokenizer is the drift this decision exists to prevent, so it is renamed rather than reinterpreted.

### The chunk ceiling stays 512 words and stays a code default

A hardcoded operational number, [ADR-082](0082-stage-4-judges-on-its-first-run-its-thresholds-are-code-defaults-and-it-ships-no-report.md)'s precedent, **and openly unmeasured**. The context window cannot narrow it (64× margin), and the two reasons that could — discrimination, and ADR-020's top-3 mean — need a real corpus. The value is arbitrary; it was always arbitrary; what changes is that it no longer claims a precision it never had.

### The embedder identity is what Ollama reports plus what we send

[ADR-090](0090-the-extractor-identity-is-the-sidecars-version-map-and-the-options-the-client-sends-never-its-url.md)'s rule, one instrument along. Each part, and where it comes from:

| Part | Source | Note |
|---|---|---|
| model name, as resolved | the profile key | ADR-084 |
| **artefact digest** | `/api/tags` → `digest` | A **manifest** digest over the layers *and* the modelfile — never on `/api/show`, which carries no digest at all. It covers strictly more than the weights, so it satisfies ADR-084's purpose; a mutable tag re-pulled after upstream republishes therefore mints a new identity and re-embeds the corpus, which is correct and expensive. A mutable tag is **reported, never gated** ([ADR-006](0006-census-measure-before-judging.md)). |
| **weight dtype** | `/api/tags` → `details.quantization_level` | Recorded beside the digest so a reader can tell a quantization change from a modelfile-only change without diffing manifests. The field is a dtype rather than a quantization — it reads `F16` for an unquantized model — and ADR-084's `Q4_K_M`/`Q8_0` case is exactly what it distinguishes. |
| **output dimension** | **the value sent**, verified against the returned vector's length | See below. |
| instruction text | **`instruction=none`**, an explicit sentinel | Distinct from `instruction=` (supplied but empty), which ADR-084's "empty is a value, not an absence" requires be distinguishable. An omitted field would read as a field that failed to populate, which is what the refuse-a-blank discipline exists to catch. |
| `content_hash`, `chunker_identity`, chunking-rule identity, `ordinal` | `chunk_cache`'s key | ADR-084, with the third part renamed above |

**The dimension is the value sent, checked against the value received, refusing on mismatch.** Metadata reports only a model's *native* dimension, so it would record 4096 for a run that asked for 1024. And the runtime invites the opposite error: `dimensions` is honoured **only at the top level** of the request — placed inside `options`, where Spring AI's convention puts model options, it is **silently ignored and a full-length vector is returned with HTTP 200**, as is any unknown key. Recording the sent value alone would stamp 1024 across 4096-dimension rows; measuring alone would faithfully describe data nobody intended and leave the misconfiguration invisible. Only the pair, checked, is safe.

Measured for the record: a truncated vector is **not a prefix** of the full one but is re-normalised — both L2 norms exactly 1.0, different values — so ADR-084's "incomparable numbers under an unchanged name" is now demonstrated rather than asserted.

### Identity metadata is read by a client of our own; Spring AI does the embedding

Spring AI 2.0.0 reaches both endpoints, and **its typed DTOs cannot compose this identity**: `Model.Details` omits `embedding_length`, `ShowModelResponse` carries no `digest`, and both records are `@JsonIgnoreProperties(ignoreUnknown = true)`, so the omissions are silent rather than errors. The `/api/show` fallback is worse still — the dimension key is architecture-prefixed (`bert.embedding_length`, `nomic-bert.embedding_length`), so a reader must fetch `general.architecture` first and then index an untyped map.

`/api/tags` carries the digest, the dtype and the dimension as first-class fields in one call. A small client over `RestClient`, beside the existing `DoclingClient`, reads it. **Spring AI keeps the embedding calls** — that is what it is for, and [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md) already justified its presence.

An identity assembled through DTOs that silently drop fields is the one place silence is unacceptable: a future upgrade that stopped surfacing `digest` would compose a blank where a digest belongs, which the refuse-a-blank discipline turns into a hard failure at best and a differently-keyed vector set at worst.

## Consequences

**The corpus is not chunked until a model is named.** Chunking under a stand-in nobody reads is work guaranteed to be discarded — nothing reads `chunk_cache` today — so the stand-in stops running and stage 5's re-chunk from `extraction_cache` becomes the only chunking that happens. This moves chunking out of stage 2's open-document pass, so [ADR-073](0073-stage-2-writes-the-derived-metric-columns-tokenizer-and-shingle-dependent-values-live-under-their-own-identity.md)'s "open the document once" no longer covers it — a cost ADR-084 already accepted knowingly when it specified the re-chunk.

**A rejected chunk costs a round trip.** With a 32k window and a 512-unit budget, rejection should be vanishingly rare; if it is not, the budget is wrong and the rejection rate is the measurement that says so.

**One rule today, and a second is a new identity rather than a migration.** A budget that varies by document kind — a spreadsheet, a slide deck and a three-hundred-page report plausibly wanting different boundaries — is not foreclosed by this decision: what is required of the budget is that it be *deterministic*, not that it be uniform. Such a variant is a **chunking rule**, never a tokenizer, and it lands in the chunking-rule identity as a sibling value — `words-2048-tabular-v1` beside `words-512-v1` — minting its own rows beside the first rule's rather than invalidating them. It is not built now because there is nothing to build it from: the 512 is recorded above as arbitrary and openly unmeasured, and per-kind budgets would be several arbitrary numbers in place of one. ADR-006 applies. It becomes a decision the moment stage 5 reports rejection rates and chunk counts per document kind, and the seam is shaped so that it can.

**Boundaries now depend on the server, not on a library we pin.** An Ollama upgrade that changed context handling could shift them. That is the same hazard a tokenizer library carried, relocated — and it is already covered, because the model digest is in the embedder identity and a re-cut chunk lands under a chunking-rule identity we control.

**The known divergence is reported, not hidden.** Our budget unit and the runtime's tokens are different measures, quantified above. The run's own report states that, so a reader sees two independently-pinned instruments and is told what is not checked between them. Verifying via `/api/embed`'s `prompt_eval_count` was rejected: that count is not a stable contract and depends on server build and templating, so a mismatch could not distinguish a wrong measure from a templating difference. It becomes the right check if `PR #12030` ever merges.

**The instruction caveat in the model card does not apply here.** Qwen3-Embedding-8B's card warns that omitting an instruct costs "approximately 1% to 5%" — explicitly *"in most retrieval scenarios"*, on *"the query side"*. ADR-020 compares a seed chunk against a corpus chunk, which has no query side, and ADR-033 asked for judgement on Clustering/STS rather than Retrieval. Recorded because the sentence is easy to find later and easy to mistake for an oversight.
