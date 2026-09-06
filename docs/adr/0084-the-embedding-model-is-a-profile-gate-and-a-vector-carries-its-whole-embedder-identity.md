# ADR-084 — The embedding model is a profile gate, and a vector carries its whole embedder identity

- **Date**: 2026-09-06
- **Status**: accepted
- **Amends**: widens [ADR-032](0032-embeddings-are-durable-the-index-is-disposable.md)'s "chunk hash + model identity" — the key is right in shape and short by three parts; places [ADR-033](0033-embedding-model-criteria-recorded-model-unset.md)'s unset model in the profile and gives it ADR-080's gate

## Context

[ADR-033](0033-embedding-model-criteria-recorded-model-unset.md) records the criteria and ships the embedding model **unset**; [ADR-034](0034-embedding-model-chosen-by-bake-off-not-argument.md) says it is chosen by a bake-off, and that bake-off is out of scope for the stage-5 map ([#78](https://github.com/algernon28/vespera/issues/78)) because it needs a real corpus and the human labels [ADR-028](0028-relevance-threshold-human-labelling-gated-by-score-distribution.md)'s gate produces. So stage 5 ships without a model, and what has never been settled is **where the model is named, what happens when nobody has named one, and what identity the vectors carry once somebody has**.

[ADR-032](0032-embeddings-are-durable-the-index-is-disposable.md) is the only surviving record on the last of those, and it is one clause of a reconstituted digest: "vectors cached (chunk hash + model identity)". The shape is right. This decision finds it short by three parts, and short in a way that would only surface as a corrupted bake-off.

### What the code already says

- **The corpus is chunked today under `word-count-v1`.** `ExtractionJobConfiguration` wires a single `Tokenizer` bean, and `WordCountTokenizer` says so in its own javadoc: "no embedding model is chosen yet, so `pipeline` supplies a deterministic stand-in — one token per whitespace-separated word". [ADR-044](0044-the-bake-off-re-chunks-per-candidate-model.md) requires chunking aligned to the candidate's own tokenizer. Every chunk in `chunk_cache` is therefore budgeted by a word counter no embedding model uses.
- **No profile key names a model.** `application.yaml` carries `spring.ai.model.embedding: ollama` — a runtime, not a model — and `Profile` has three keys, none of them this one.
- **[ADR-073](0073-stage-2-writes-the-derived-metric-columns-tokenizer-and-shingle-dependent-values-live-under-their-own-identity.md) already refused the one-column version of this question**, one layer down: chunk count is not a property of an occurrence, because "a single `chunk_count` column would assert one number for a value that has one per candidate." A vector is the same object one layer up.

### The candidate that made the identity question concrete

**Qwen3-Embedding-8B-GGUF**, weighed against ADR-033's recorded criteria: separable tokenizer ✅, pinnable weights ✅ (a GGUF file has a digest), Italian and English ✅ (100+ languages), OCR tolerance plausible, and it is judged on the multilingual leaderboards ADR-033 asked for rather than on retrieval alone.

It passes the criteria, and it is **not thereby chosen** — ADR-034 says by bake-off, not by argument. What it did do is make the identity question answerable, because this one model produces *different vectors under an unchanged name* in three separate ways: Matryoshka truncation (4096, 2048, 1024 and 512 are all valid outputs), quantization (`Q4_K_M` and `Q8_0` are different weights), and its instruction prefix, which changes every vector it is applied to. A key of "chunk hash + model name" cannot tell any of those apart.

## Decision

### Which model is a profile key; where it is served stays configuration

[ADR-012](0012-extraction-engine-is-configurable.md) drew this line for the extraction engine — "the serving runtime is config, not code" — and the same line falls here, on the other side.

**`Profile` gains an embedding-model key.** "This corpus is scored under *this* model, chosen because *this*" is a judgement about the corpus with provenance behind it, which is what `ProfileValue` exists to hold ([ADR-061](0061-the-profile-is-yaml-typed-java-records-one-object-per-value.md), ADR-031's "per-value provenance"). It has to travel with the database, because a score is meaningless without it.

**`spring.ai.model.embedding`, base URLs and API keys stay in `application.yaml`.** Which runtime serves the named model is deployment, and deployment does not travel with the corpus.

### An unset model gates stage 5, in the shape stage 4 already built

**The invocation ends there, the job succeeds, and no stage-5 run is minted** — [ADR-080](0080-the-boilerplate-floor-is-a-gate-applied-before-signatures-are-computed.md) verbatim, for its reason: a run row for a stage that did nothing reads as a pass that found nothing. The check happens before the run is minted, not inside the step, which is the mistake ADR-080's own wording exists to prevent.

This is a gate for the correct reason under `CONTEXT.md`'s definition — "a value the pipeline requires and does not have". Unlike [ADR-083](0083-the-seed-set-is-extracted-by-stage-5-and-an-unusable-seed-is-recorded-not-a-gate.md)'s unreadable seed, which is content quality and is therefore recorded rather than gated, an unnamed model is an absent input with nothing to fall back on.

### The model ships unset, and Qwen3-Embedding-8B is recorded as the leading candidate rather than as a default

**No default value.** A shipped default is the choice nobody ever revisits: it would settle by inertia the one thing ADR-034 says must be settled by measurement, and it would do so invisibly, since a profile key with a value in it looks exactly like a key somebody answered.

Recording the candidate above is the whole benefit a default would have offered — the bake-off starts from a shortlist and a criteria check rather than from a blank page — with none of its cost.

### A vector's identity is the whole embedder, not the model's name

**Six parts, and every one of them can change the numbers while the others hold still.** Four address the input, three address the embedder:

| Part | Why it is in the key |
|---|---|
| `content_hash`, `chunker_identity`, `tokenizer_identity`, `ordinal` | `chunk_cache`'s own key — this is what ADR-032's "chunk hash" has to mean, since a chunk is not addressable without the instruments that cut it (ADR-029, ADR-044, ADR-073) |
| model name, as resolved | the obvious half of ADR-032's "model identity" |
| weights digest | pins quantization, which ADR-033 asked for by name. A moving registry tag then mints new rows instead of mixing two models' output under one identity |
| output dimension | Matryoshka truncation makes one model's 1024-dimension vector and its 4096-dimension vector **incomparable numbers under an unchanged name**. Not metadata — the key |
| instruction text | an instruction-aware model embeds the same text differently under a different instruction. **Empty is a value, not an absence**, and is recorded as one |

The embedder's parts are composed into one `embedder_identity` string, the discipline `ExtractorIdentity` and `TokenizerIdentity` already apply: a record that carries a composed value and refuses a blank one, composed by whoever mints the run.

**Where a runtime reports nothing that pins the weights, the identity records that fact explicitly** rather than omitting it — so an unpinnable deployment is visible in the ledger instead of being indistinguishable from a pinned one. It is not a second gate: measuring rather than refusing is this pipeline's posture ([ADR-006](0006-census-measure-before-judging.md)), the same argument ADR-083 lost and then won.

**A vector row is per chunk, never per document.** ADR-020 scores over chunk similarities, so a per-document vector would have to be an aggregate nothing in the scoring function asks for.

### Both sides of ADR-020's comparison are embedded identically, with no instruction

[ADR-020](0020-relevance-scoring-function.md) compares a corpus chunk against a **seed chunk** — two documents, not a query against a document. An instruction-aware model's asymmetric query/document mode would make a seed's similarity to a corpus chunk differ from the reverse, and ADR-020's maximum over seeds has no way to express a direction.

So both sides get the same treatment and no instruction. The empty instruction sits in the identity regardless, so trying an instructed variant later is a **different vector set** rather than a silent re-meaning of this one.

### Naming a model re-chunks the corpus, and never re-extracts it

The first model named invalidates every `word-count-v1` chunk for scoring purposes, because ADR-044's alignment is the point: boundaries budgeted by a word counter make the token budget meaningless and bias any future bake-off toward the incumbent boundaries.

**Stage 5 re-chunks from `extraction_cache`.** That cache is keyed by content hash plus extractor identity and knows nothing about walks or runs, so the re-chunk reads stored text and **Docling is never called again** — the same content-addressing that made ADR-083's seed reuse free. New rows land under the model's tokenizer identity; the `word-count-v1` rows are left exactly where they are, which is what `chunk_cache`'s key was built for.

## Consequences

**Naming a model for the first time costs a full re-chunk of the corpus, and no extraction.** CPU over already-stored text, paid once per tokenizer. That is the price of ADR-044, and it is named here so that nobody later reads it as a defect.

**This is what makes a bake-off affordable.** A second candidate costs one re-chunk and one embedding pass, and invalidates nothing: the first candidate's chunks and vectors stay addressable under their own identities, and the two can be compared afterwards rather than serially rebuilt. If the identity were the model's name alone, comparing two candidates would mean embedding everything twice and being unable to prove which numbers came from which.

**Storage is now a decision with a number attached, and it belongs to [#81](https://github.com/algernon28/vespera/issues/81).** A 4096-dimension vector is 16 KiB at four bytes per component — roughly 16 GB at a million chunks, against 4 GB truncated to 1024. Dimension being in the identity is precisely what lets that decision be taken later: truncating mints a new row set rather than reinterpreting the stored one.

**Stage 5's `configConsumed` names the embedder identity**, so a corpus scored under a different model, quantization, dimension or instruction is a different run ([ADR-048](0048-walk-and-run-identity.md)) — the same property every prior stage's run identity carries, and the one that keeps two candidates' scores from ever being confused for each other.

**`ExtractorIdentity` has the gap this decision was careful not to repeat.** It is composed as `"docling-serve;base-url=" + baseUrl` — the URL and nothing else — while ADR-012 requires the cache key to carry the *full* extractor identity. Point the same Docling at a different OCR backend and every cached extraction, shingle, confidence score and chunk silently keeps serving the old backend's text under an unchanged identity. It is latent today because there is one OCR path, and it goes live the moment anyone configures a VLM OCR engine. That is an extraction decision rather than a stage-5 one, and is raised separately rather than folded in.

**`CONTEXT.md` gains nothing.** "Model", "quantization" and "dimension" are the vocabulary of the tools rather than of the domain, and the glossary is kept free of implementation detail. The domain words this decision leans on — **bake-off**, **reference model**, **gate**, **profile** — are already there and unchanged.

**Nothing here fixes the tables.** What the vector table looks like, whether Chroma is populated at all, and what is resident while a partition is scored are [#81](https://github.com/algernon28/vespera/issues/81)'s and the hand-off spec's ([#85](https://github.com/algernon28/vespera/issues/85)), the same deferral every prior slice made.
