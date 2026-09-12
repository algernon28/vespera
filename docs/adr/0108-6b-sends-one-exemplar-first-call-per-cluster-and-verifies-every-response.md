# ADR-108 — 6b sends one exemplar-first call per cluster, and verifies every response

- **Date**: 2026-09-12
- **Status**: accepted
- **Rests on**: [`docs/research/ollama-generation-surface.md`](../research/ollama-generation-surface.md) — the facts this record acts on, pinned to Ollama 0.33.2 and Spring AI 2.0.0, cited line by line. It lands on `main` with this decision rather than staying on the branch it was written on, because the decision is unreadable without it.

## Context

A synthesis doc makes a cluster's survivors coherent by connecting them, and is explicitly not a per-document summary ([ADR-021](0021-synthesis-exists-to-make-the-survivor-set-coherent.md)). A cluster may hold hundreds of documents. No context window holds them, so what is sent has to be chosen — and the research turned up that choosing badly fails **silently**.

### The failure this record is mostly about

On Ollama's generation endpoints, overflow is not refused. With context shift on — the default for every model family but `deepseek2` — a prompt longer than `num_ctx - 1` keeps its first four tokens, discards from the middle, logs a warning server-side, and returns **nothing in the response** to say it happened. The caller gets a confident answer written over half a cluster.

That is the exact inverse of the embedding endpoint's `truncate: false` contract, which [ADR-091](0091-there-is-no-tokenizer-the-runtime-counts-tokens-and-the-embedder-identity-is-what-ollama-reports.md) leaned on: there, the runtime refuses rather than truncating. The equivalent lever for generation exists (`shift: false`), is documented nowhere, and **cannot be sent through Spring AI 2.0.0** — `filterNonSupportedFields` strips it, as it strips `truncate`.

Two related facts shape the rest: **`num_ctx` unsent means the host decides** (4096, 32768 or 262144, picked from total VRAM at server startup and silently lowered past the model's trained length), and **a caller cannot pre-measure a prompt** — there is no tokenizer on this side, which ADR-091 already settled, and every reading of a served model's window through `/api/show` or `/api/ps` is a different number from the one the prompt check uses.

## Decision

**One call per cluster. It carries the cluster's highest-scoring documents, leading chunk each, filled in score order until a word budget runs out. Every response is verified before its text is believed.**

### Exemplar-first, not a rollup

Sent: the cluster's **label** and its seed partition's name; then its documents in **relevance-score order**, each contributing its **leading chunk**, which `HybridChunker` guarantees is heading-led so an exemplar arrives titled rather than starting mid-paragraph; and the count it is working from.

There is no fixed N. A cluster of eight short documents sends all eight; a cluster of four hundred sends what fits.

**The rollup alternative** — summarise each document, then synthesise the summaries — reads everything, and is refused on two grounds. It costs one model call *per document*, hundreds of thousands across a corpus this size against one per cluster. And it builds exactly the per-document summary ADR-021 exists to refuse; that the artifact would be an intermediate rather than the output does not stop it being made.

The exemplars are ordered by the only relevance signal this system has — the same signal that decided each document survived at all.

### A cluster larger than the budget is still written, and the document says so

*"Written from the 40 highest-scoring of 412 documents"* appears in the synthesis doc itself.

Refusing instead would leave the largest clusters — the ones most worth synthesising — as the only ones with no synthesis. And nothing is concealed: the cluster file's membership list is complete regardless ([ADR-104](0104-the-surviving-originals-stay-where-they-are-and-the-deliverable-references-them.md)), so a reader sees all 412 documents with links, beneath prose written from 40.

### `num_ctx` is sent, and the budget is counted in words

**Vespera sends `num_ctx` explicitly**, because unsent it is a property of whichever machine is serving — a corpus synthesised under a window that changes with the hardware is not a corpus anyone can reason about. Spring AI *can* send this one.

It ships as a code default, overridable in the profile, and **it is not a gate**: unset means the default, not a stop. The operator's path is already five invocations ([ADR-107](0107-the-arrangement-gate-approves-a-named-6a-run-and-the-path-becomes-five-invocations.md)), and a sixth required value, for a number most operators cannot reason about, is not worth the stop.

**The budget is in words, converted at a deliberately pessimistic ratio.** ADR-091 settled that this project has no tokenizer and the runtime is what counts tokens; `chunk_cache.word_count` is already stored, so the sum is a query rather than a pass over text. The input budget is `num_ctx` less what the answer needs (`num_predict`) less an allowance for the instruction, with the ratio and the reserve as named constants carrying their reasoning.

### Every response is verified, because the failure is silent

The request cannot be made safe, so the response is checked:

- **`prompt_eval_count` at or above the ceiling** — the prompt was shifted, and some of the cluster was discarded. **Fails the cluster.**
- **`done_reason: "length"`** — the answer ran out of room, and arrives as a complete-looking `200`. **Fails the cluster.**
- **A response that fails its schema** — a schema is imposed and validated **client-side**, because no primary source claims Ollama guarantees conformance and its own examples validate after the fact. **Fails the cluster.**

Both counts reach the caller: Spring AI maps `prompt_eval_count` and `eval_count` onto its usage record.

**A failed cluster is a recorded fault, not a stopped run.** The deliverable keeps a hole, and the hole has a heading — the cluster's 6a label, which [ADR-106](0106-a-cluster-gets-a-derived-label-from-6a-and-a-generated-title-from-6b.md) already made the fallback.

### The response carries the title as well as the prose

ADR-106 puts a cluster's title in 6b's hands, so the call asks for both and the schema covers both.

### The generator's identity

The model name plus the options actually sent — `num_ctx`, `num_predict`, `temperature`, `seed` — and never the URL, following [ADR-090](0090-the-extractor-identity-is-the-sidecars-version-map-and-the-options-the-client-sends-never-its-url.md) and ADR-091.

**No digest is available**: nothing in a generate or chat response carries one, and the `model` field is the request string echoed back. **The token counts are not identity** either: they are per-call facts, and ADR-091 already rejected `prompt_eval_count` as a stable contract.

## Consequences

**A synthesis doc that exists can be trusted to have been written over what it claims.** That is the whole return on the verification, and it is bought at the price of clusters that fail where a less careful implementation would have produced confident text nobody could audit.

**The deliverable may be incomplete, visibly.** Holes are expected output, not an error state, and the index shows them.

**Generation goes through `/api/chat`, and no new HTTP client is written.** Spring AI 2.0.0 has no `/api/generate` binding — `OllamaApi` in the resolved `spring-ai-ollama-2.0.0.jar` exposes `chat`, `streamingChat`, `embed`, `listModels`, `showModel`, `copyModel`, `deleteModel` and `pullModel`, and nothing else — but `/api/chat` needs none: it is fully bound, it carries `format` for the imposed schema, and it is the endpoint Ollama's own structured-output documentation uses in every example. A client of this project's own would only be owed if 6b wanted `/api/generate`, and it does not. What remains open is which module owns the call, since `OllamaClient` belongs to `embedding` and `synthesis` may not name it under the module rule — that is the module-seam decision, not this one.

**Determinism stays open.** No Ollama source claims a fixed seed reproduces output, and `cache_prompt` is hardcoded on in 0.33.2 — so what a re-run under the same 6b run id writes is not settled by this record.

**The pessimistic word ratio will sometimes waste window.** A conservative constant means some clusters send fewer exemplars than would have fit. The alternative is discovering the ceiling by crossing it, which the silent-shift behaviour makes undetectable except after the fact — and this record prefers a wasted third of a window to a synthesis doc about a corpus that was quietly cut in half.
