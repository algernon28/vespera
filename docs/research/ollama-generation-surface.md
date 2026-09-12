# What Ollama's generation surface offers, and what it constrains

Research record for [issue #157](https://github.com/algernon28/vespera/issues/157), under map
[#151](https://github.com/algernon28/vespera/issues/151). Facts only — no decisions. Feeds the design tickets it
blocks ([#158](https://github.com/algernon28/vespera/issues/158),
[#161](https://github.com/algernon28/vespera/issues/161)).

Which model to serve, and whether its output is any good, are out of scope and are not touched below.

## Scope and method

Every claim is either (a) quoted from a primary source — Ollama's published documentation, Ollama's own source
at the tag this repo pins, Spring AI's source at the version this repo pins, or an upstream issue — or (b)
labelled **UNSOURCED**, meaning I looked and the sources are silent. **Nothing here is measured.** Unlike
[`embedding-tokenizer-sourcing.md`](embedding-tokenizer-sourcing.md) and
[`ntfs-java-file-walking.md`](ntfs-java-file-walking.md), no probe was run against a live runtime; where a number
would have to be measured to be trusted, this record says so rather than quoting a document that cannot know.

Versions, read out of this repo rather than assumed:

| | | Where |
|---|---|---|
| Ollama | **0.33.2** | `compose.yaml`, `image: 'ollama/ollama:0.33.2'` |
| Spring AI | **2.0.0** | `pom.xml`, `<spring-ai.version>2.0.0</spring-ai.version>` |
| Spring Boot | **4.1.1** | `pom.xml`, `spring-boot-starter-parent` |
| Java | **26** | `pom.xml`, `<java.version>26</java.version>` |

Ollama source citations are to tag
[`v0.33.2`](https://github.com/ollama/ollama/tree/v0.33.2) — the tag behind the pinned image, not `main`. Where
`main` (v0.34.0, released 2026-09-05) differs on a fact below, it is called out; for everything in this record
the two are identical.

Spring AI source citations are to tag [`v2.0.0`](https://github.com/spring-projects/spring-ai/tree/v2.0.0),
cross-checked against the `-sources.jar` actually resolved into the local repository for that version.

**A note on which Ollama document is authoritative.** The API reference now lives at
[docs.ollama.com](https://docs.ollama.com/api/generate), published as an OpenAPI 3.1 document; the older
[`docs/api.md`](https://github.com/ollama/ollama/blob/main/docs/api.md) in the repository says on its own first
lines that the docs are moving there. Both are cited below. Several request fields exist in
[`api/types.go`](https://github.com/ollama/ollama/blob/v0.33.2/api/types.go) and appear in **neither**; those are
flagged as undocumented, because a caller relying on them is relying on source, not on a contract.

---

## 1. The generation endpoints

### There are two, and Spring AI 2.0.0 reaches only one

Ollama serves **`POST /api/generate`** ([docs](https://docs.ollama.com/api/generate)) and **`POST /api/chat`**
([docs](https://docs.ollama.com/api/chat)).

Spring AI 2.0.0's `OllamaApi` has **no `/api/generate` method at all**. The full set of endpoints it reaches is
`/api/chat` (twice — blocking and streaming), `/api/embed`, `/api/tags`, `/api/show`, `/api/copy`, `/api/delete`
and `/api/pull`
([`OllamaApi.java`](https://github.com/spring-projects/spring-ai/blob/v2.0.0/models/spring-ai-ollama/src/main/java/org/springframework/ai/ollama/api/OllamaApi.java)).
So through the dependency this repo already has, **generation means `/api/chat`**; `/api/generate` would need a
client of our own, which is the shape `OllamaClient` already has for `/api/tags`
(`src/main/java/io/algernon/vespera/embedding/OllamaClient.java`).

`/api/chat` is also the endpoint Ollama's own structured-output documentation uses in every local example
([structured outputs](https://docs.ollama.com/capabilities/structured-outputs)).

### What the request carries

| Field | `/api/generate` | `/api/chat` | Reachable from Spring AI 2.0.0 |
|---|---|---|---|
| `model` | required | required | yes — `ChatRequest.model`, and `Assert.state(model != null)` |
| `prompt` | yes | — | n/a |
| `messages` (`role` ∈ `system`/`user`/`assistant`/`tool`, `content`, `images`, `tool_calls`, `tool_name`, `thinking`) | — | required | yes — `ChatRequest.messages` |
| `suffix` (fill-in-the-middle) | yes | — | n/a |
| `system`, `template`, `raw`, `context` (deprecated) | yes | — | n/a |
| `images` (base64) | yes | per message | yes |
| `tools` | — | yes | yes — `ChatRequest.tools`, wired through `ToolCallingManager` |
| `format` (`"json"` or a JSON Schema object) | yes | yes | yes — `ChatRequest.format` |
| `options` (map) | yes | yes | yes — `ChatRequest.options` |
| `stream` (default `true`) | yes | yes | yes — blocking path asserts `!stream`, streaming path asserts `stream` |
| `keep_alive` (default `5m`) | yes | yes | yes — `ChatRequest.keepAlive` |
| `think` (bool, or `low`/`medium`/`high`/`max`) | yes | yes | yes — `ChatRequest.think`, `ThinkOption` |
| `logprobs`, `top_logprobs` (0–20) | yes | yes | **no** |
| **`truncate`** | yes (undocumented) | yes (undocumented) | **no** — see below |
| **`shift`** | yes (undocumented) | yes (undocumented) | **no** — see below |
| `_debug_render_only` | yes | yes | no |

Sources: [generate](https://docs.ollama.com/api/generate), [chat](https://docs.ollama.com/api/chat),
[`api/types.go` `GenerateRequest`/`ChatRequest`](https://github.com/ollama/ollama/blob/v0.33.2/api/types.go#L62-L180),
[`OllamaApi.ChatRequest`](https://github.com/spring-projects/spring-ai/blob/v2.0.0/models/spring-ai-ollama/src/main/java/org/springframework/ai/ollama/api/OllamaApi.java),
[`OllamaChatModel.ollamaChatRequest`](https://github.com/spring-projects/spring-ai/blob/v2.0.0/models/spring-ai-ollama/src/main/java/org/springframework/ai/ollama/OllamaChatModel.java).

**`truncate` and `shift` are real and undocumented.** Both are top-level request fields on `GenerateRequest`
*and* `ChatRequest` in
[`api/types.go`](https://github.com/ollama/ollama/blob/v0.33.2/api/types.go#L111-L117), with these comments:

> `// Truncate is a boolean that, when set to true, truncates the chat history messages if the rendered prompt exceeds the context length limit.`

> `// Shift is a boolean that, when set to true, shifts the chat history when hitting the context length limit instead of erroring.`

Neither appears in the OpenAPI document for `/api/generate` or `/api/chat`, nor in `docs/api.md`. They are the
only overflow controls the generation endpoints have, and §2 is about what they do.

**Spring AI 2.0.0 cannot send either.** `OllamaApi.ChatRequest` has no component for them, and `truncate` cannot
smuggle through `options`: `OllamaChatOptions.toMap()` does emit `"truncate"`, but
`ChatRequest.Builder.options(...)` passes everything through
`OllamaChatOptions.filterNonSupportedFields(...)`, whose `NON_SUPPORTED_FIELDS` is
`List.of("model", "format", "keep_alive", "truncate")` — so the key is stripped before the request is built. The
`spring.ai.ollama.chat.truncate` property therefore exists, binds, and has no effect on a chat call.

### What the response carries

Non-streaming, both endpoints ([generate](https://docs.ollama.com/api/generate),
[chat](https://docs.ollama.com/api/chat)):

| Field | Meaning | In Spring AI's `ChatResponse` record |
|---|---|---|
| `model` | the model name — echoed **verbatim from the request**, `Model: req.Model` ([`routes.go`](https://github.com/ollama/ollama/blob/v0.33.2/server/routes.go)) | yes |
| `created_at` | ISO 8601 | yes (`Instant`) |
| `response` (generate) / `message` (chat) | the generated text; chat nests `role`, `content`, `thinking`, `tool_calls` | yes (`message`) |
| `thinking` | reasoning trace when `think` is on | yes, via `Message.thinking` |
| `done`, `done_reason` | whether final, and why it stopped | yes |
| `total_duration`, `load_duration`, `prompt_eval_duration`, `eval_duration` | **nanoseconds** | yes |
| `prompt_eval_count`, `eval_count` | prompt tokens, response tokens | yes |
| `prompt_eval_cached_count` | prompt tokens served from cache | **no** |
| `context` (generate only, deprecated) | token encoding of the conversation | n/a |
| `logprobs` | when requested | **no** |

`done_reason` is a small closed set on the generation path:
[`llm/server.go`](https://github.com/ollama/ollama/blob/v0.33.2/llm/server.go#L248-L266) defines
`DoneReasonStop` → `"stop"`, `DoneReasonLength` → `"length"`, and `DoneReasonConnectionClosed` → `""`. The
handlers add `"load"` and `"unload"` for the empty-prompt calls that only load or evict a model
([generate docs](https://docs.ollama.com/api/generate)). **`"length"` is the only signal that output was cut
short**, and §2 explains why that is also the only signal a caller gets.

Spring AI maps `done_reason` onto `ChatGenerationMetadata.finishReason(...)` — but **only when both
`promptEvalCount` and `evalCount` are non-null**; otherwise the generation carries
`ChatGenerationMetadata.NULL` and the reason is dropped
([`OllamaChatModel`](https://github.com/spring-projects/spring-ai/blob/v2.0.0/models/spring-ai-ollama/src/main/java/org/springframework/ai/ollama/OllamaChatModel.java)).
`prompt_eval_count`/`eval_count` become a `DefaultUsage`, and both counts plus the four durations are also put on
the response metadata under `eval-count`, `prompt-eval-count` and friends.

Streaming is NDJSON (`application/x-ndjson`), one object per chunk, the last carrying `done: true` and the
statistics ([generate docs](https://docs.ollama.com/api/generate)).

---

## 2. Context window

### How the limit is set

`num_ctx` is a **load-time** option, not a sampling one — it sits in the `Runner` struct, documented as "options
which must be set when the model is loaded into memory"
([`api/types.go`](https://github.com/ollama/ollama/blob/v0.33.2/api/types.go#L588-L597)). It is sent inside
`options`, and the OpenAPI document lists it there as "context length in tokens"
([generate](https://docs.ollama.com/api/generate), [chat](https://docs.ollama.com/api/chat)).

Precedence, in the order Ollama applies it
([`routes.go` `modelOptionsWithEmbeddingBatchDefault`](https://github.com/ollama/ollama/blob/v0.33.2/server/routes.go#L129-L163)):

1. `api.DefaultOptions()`
2. the model's `GenerationDefaults`
3. the model's own `Modelfile` options (`PARAMETER num_ctx …`)
4. **the request's `options` map** — last, so an explicit `num_ctx` wins

### The default is not a constant

`DefaultOptions()` sets `NumCtx: int(envconfig.ContextLength())`
([`api/types.go`](https://github.com/ollama/ollama/blob/v0.33.2/api/types.go#L1106-L1133)), and
`ContextLength = Uint("OLLAMA_CONTEXT_LENGTH", 0)`
([`envconfig/config.go`](https://github.com/ollama/ollama/blob/v0.33.2/envconfig/config.go#L229-L230)) — so
**unset means 0**, and 0 means "the server decides". The server decides once, at startup, from total VRAM
([`routes.go`](https://github.com/ollama/ollama/blob/v0.33.2/server/routes.go#L2046-L2058)):

```go
case totalVRAM >= 47*format.GibiByte:  s.defaultNumCtx = 262144
case totalVRAM >= 23*format.GibiByte:  s.defaultNumCtx = 32768
default:                               s.defaultNumCtx = 4096
```

and substitutes it wherever `opts.NumCtx == 0`. The documentation states the same tiers in prose — "< 24 GiB
VRAM: 4k context", "24-48 GiB VRAM: 32k context", ">= 48 GiB VRAM: 256k context"
([context length](https://docs.ollama.com/context-length)); the thresholds in code are one GiB lower, with a
comment saying they are deliberately loosened "to account for small differences in the exact value".

**The consequence for identity: the effective `num_ctx` of an unspecified request is a property of the host, not
of the model or the request.** The same call against a 16 GB laptop and a 48 GB workstation is two different
computations under one model name.

**Spring AI's documented default of 2048 is stale.** Both the reference documentation table and the Javadoc on
`OllamaChatOptions.numCtx` say "(Default: 2048)"
([`OllamaChatOptions`](https://github.com/spring-projects/spring-ai/blob/v2.0.0/models/spring-ai-ollama/src/main/java/org/springframework/ai/ollama/api/OllamaChatOptions.java),
[reference](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html)). That is documentation of
Ollama's old default, not behaviour Spring AI implements: `toMap()` emits `num_ctx` only when the field is
non-null, and the map is filtered of nulls, so **Spring AI sends nothing and the server's own default applies**.
The same staleness affects `repeat-penalty` (Spring AI says 1.1; `DefaultOptions()` says `RepeatPenalty: 1.0`)
and `num-predict` (the `OllamaChatOptions` Javadoc says 128; `DefaultOptions()` and the reference table both say
`-1`).

### Asking for more than the model was trained on is silently reduced

On load ([`llm/server.go`](https://github.com/ollama/ollama/blob/v0.33.2/llm/server.go#L112-L115)):

```go
trainCtx := f.KV().ContextLength()
if opts.NumCtx > int(trainCtx) && trainCtx > 0 {
    slog.Warn("requested context size too large for model", "num_ctx", opts.NumCtx, "n_ctx_train", trainCtx)
    opts.NumCtx = int(trainCtx)
}
```

A warning in the **server's** log, and the value is lowered. Nothing in the response says so.

### What happens when the input exceeds it

Three distinct mechanisms, in the order a request meets them.

**(a) Chat history truncation — `/api/chat` only.** Default on (`truncate` unset is treated as `true`:
`req.Truncate == nil || *req.Truncate`,
[`routes.go`](https://github.com/ollama/ollama/blob/v0.33.2/server/routes.go)). `chatPrompt`
([`server/prompt.go`](https://github.com/ollama/ollama/blob/v0.33.2/server/prompt.go)) renders and tokenizes the
message list, then **drops messages from the front** until it fits `opts.NumCtx`, re-collecting any `system`
messages from the dropped region so they survive. Two things matter:

- It logs `"truncating input messages which exceed context length"` at **debug** level, and returns nothing in
  the response to say it happened.
- It always keeps the last message — the comment reads "Must always include at least the last message" — so an
  oversized final user message is **passed through unshortened** rather than rejected here. It then meets (b).

**(b) The prompt-length check.**
[`llm/llama_server.go` `completionPromptForRequest`](https://github.com/ollama/ollama/blob/v0.33.2/llm/llama_server.go#L279-L318)
tokenizes the rendered prompt and compares it against `fullPromptLimit := s.options.NumCtx - 1`. If it does not
fit:

- **with context shift off** — an `api.StatusError` with `http.StatusBadRequest` and the message
  `"the prompt is longer than the context length currently available to the model; shorten the prompt, adjust the context length in settings, or use a model with a longer context length"`
- **with context shift on** — it keeps `num_keep` tokens from the front (default `NumKeep: 4`, "a minimal
  num_keep to avoid issues on context shifts"), discards from the middle down to roughly half the remaining
  window, and logs `slog.Warn("truncating input prompt", …)`. The response says nothing.

Whether shift is on when the caller did not ask
([`server/sched.go`](https://github.com/ollama/ollama/blob/v0.33.2/server/sched.go#L129-L146)):

```go
func resolveContextShift(shift *bool, m *Model) bool {
	if shift != nil { return *shift }
	return supportsContextShift(m)
}
```

and `supportsContextShift` returns `true` for everything except the `deepseek2` family. **So the default on
Ollama 0.33.2 is: overflow is silently shifted, not refused.** A caller who wants the refusal has to send
`"shift": false` — which Spring AI 2.0.0 cannot do (§1).

**(c) Running out of room while generating.** The response ends with `done_reason: "length"`
([`llm/server.go`](https://github.com/ollama/ollama/blob/v0.33.2/llm/server.go#L248-L266)). That is a normal
`200` with a complete-looking body; only the field distinguishes it from a finished answer.

**The overall shape, stated plainly: on the generation endpoints, overflow is silent by default.** Every
mechanism that discards input logs it on the server and reports nothing to the caller. This is the opposite of
the embedding endpoint, where `truncate: false` makes the runtime refuse — the contract
[ADR-091](../adr/0091-there-is-no-tokenizer-the-runtime-counts-tokens-and-the-embedder-identity-is-what-ollama-reports.md)
was built on. The equivalent lever for generation exists (`shift: false`), is undocumented, and is unreachable
through Spring AI.

### How a caller learns a served model's limit

Three readings, none of them the same number:

| Reading | Endpoint | What it actually is |
|---|---|---|
| `<arch>.context_length` inside `model_info` | `POST /api/show` | the **trained** length from the GGUF, e.g. `gemma4.context_length: 131072` ([docs](https://docs.ollama.com/api-reference/show-model-details)) |
| `num_ctx` inside `parameters` | `POST /api/show` | what the model's own `Modelfile` pins, if anything ([docs](https://docs.ollama.com/api-reference/show-model-details)) |
| `context_length` | `GET /api/ps` | the **active** context window of a **currently loaded** model ([docs](https://docs.ollama.com/api/ps)) |

`/api/show`'s key is architecture-prefixed, so a reader must fetch `general.architecture` first and then index an
untyped map — exactly the trap ADR-091 recorded for `embedding_length`. `/api/ps` gives the effective number but
only while the model is resident, and `expires_at`/`keep_alive` govern that.

Spring AI reaches `/api/show` and exposes `model_info` as an untyped `Map<String, Object>` and `capabilities` as
a `List<String>` (`ShowModelResponse`), but has **no `/api/ps` method at all**, so the effective window is not
readable through it.

Two standing caveats:

- **A reported number is not a measured one.** ADR-091 records a measurement on this exact family of fields:
  `*.context_length` reported 512 where the runtime behaved as 256. That was an embedding model; nothing in this
  record measures the generation case, and **UNSOURCED**: no primary source states that `/api/ps`'s
  `context_length` is the exact number the prompt check uses.
- **A caller cannot pre-measure the prompt.** Ollama exposes no tokenize endpoint — ADR-091 §"What the runtime
  will and will not do", with the four upstream requests it cites
  ([#3582](https://github.com/ollama/ollama/issues/3582),
  [#4186](https://github.com/ollama/ollama/issues/4186),
  [#9229](https://github.com/ollama/ollama/issues/9229),
  [#12031](https://github.com/ollama/ollama/issues/12031),
  [PR #12030](https://github.com/ollama/ollama/pull/12030)). So the budget can be compared against the limit only
  after the fact, via `prompt_eval_count`.

---

## 3. Structured output

### A schema can be imposed, on both endpoints

`format` takes either the literal string `"json"` or a JSON Schema object
([generate](https://docs.ollama.com/api/generate), [chat](https://docs.ollama.com/api/chat)). The documentation
words it as: "Provide a JSON schema to the `format` field", and describes the feature as letting you "enforce a
JSON schema on model responses so you can reliably extract structured data"
([structured outputs](https://docs.ollama.com/capabilities/structured-outputs)).

### The mechanism is constrained decoding, not post-hoc validation

[`llm/llama_server.go`](https://github.com/ollama/ollama/blob/v0.33.2/llm/llama_server.go) passes the schema down
to the inference server rather than checking the output against it:

- on the completion path, `"json"` becomes a built-in BNF grammar (`grammarJSON`) and a schema object becomes the
  `json_schema` field of the llama-server request — the file's own header comment explains the schema goes via
  "its json_schema field (avoiding the CGO SchemaToGrammar dependency)", with "Raw BNF grammars … passed via the
  grammar field";
- on the chat path, `llamaServerChatResponseFormat` maps `"json"` to `{"type": "json_object"}` and a schema to
  `{"type": "json_schema", "json_schema": {"name": "schema", "schema": …}}`.

Anything else is rejected before generation starts, with
`invalid format: %q; expected "json" or a valid JSON Schema object`.

### How reliably — what is and is not claimed

- The docs say the model "will generate a response that matches the schema"
  ([generate](https://docs.ollama.com/api/generate)) and, for bare `"json"` on chat, that "the output will always
  be a well-formed JSON object" ([`docs/api.md`](https://github.com/ollama/ollama/blob/main/docs/api.md)).
- **No document states a validation guarantee.** The launch post frames it comparatively — structured outputs are
  "more reliable and consistent than JSON mode" — and hedges its own advice under "for reliable use"
  ([blog](https://ollama.com/blog/structured-outputs)). **Every official example validates client-side
  afterwards** (`model_validate_json()` in Python, `Country.parse(JSON.parse(...))` in JavaScript), in both the
  blog post and the current docs page.
- Two documented tips, both first-party: it is "ideal to also pass the JSON schema as a string in the prompt to
  ground the model's response", and one should lower "the temperature (e.g., set it to `0`) for more
  deterministic completions" ([structured outputs](https://docs.ollama.com/capabilities/structured-outputs)).
- One documented limitation: "Ollama's Cloud currently does not support structured outputs" (same page). Not
  relevant to a locally served runtime, recorded because it is on the same page as the feature.
- **UNSOURCED**: what happens when the model cannot satisfy the schema — whether generation errors, loops, or
  returns truncated JSON — is not stated anywhere I could find. `done_reason: "length"` is the only related
  signal in the response shape.
- Structured output is separately reported to **break seeded reproducibility**:
  [#12559](https://github.com/ollama/ollama/issues/12559) reports that the same request with `format` is
  non-deterministic across runs with the same seed while the same request without `format` is deterministic. See
  §4.

### Through Spring AI 2.0.0

`OllamaChatOptions` offers three ways in, all landing on the same `format` field:
`.format("json")`, `.format(Map)`, and `.outputSchema(String)` — the last parsing the JSON schema string into a
map (`jsonHelper.fromJsonToMap`). The reference documentation calls the schema form the one "recommended for
production" and shows it composed with `BeanOutputConverter.getJsonSchema()`, noting that
`@JsonProperty(required = true, …)` is needed "for generating a schema that accurately marks fields as required"
([reference](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html)). `format` is excluded from
the `options` map (`NON_SUPPORTED_FIELDS`) and set at the top level of the request, which is where Ollama reads
it — the mirror image of the `dimensions` trap ADR-091 recorded for embeddings.

---

## 4. Determinism

### The two knobs, and what is claimed for them

| Option | Default | What the sources say |
|---|---|---|
| `seed` | `-1` ([`DefaultOptions()`](https://github.com/ollama/ollama/blob/v0.33.2/api/types.go#L1106-L1133)) | "For reproducible results" ([generate](https://docs.ollama.com/api/generate)); Spring AI's Javadoc goes further: "Setting this to a specific number will make the model generate the same text for the same prompt." |
| `temperature` | `0.8` (same source) | "Larger values yield more randomness"; the first-party tip is to "set it to `0`) for more deterministic completions" ([structured outputs](https://docs.ollama.com/capabilities/structured-outputs)) |

Both are ordinary `options` keys and both are reachable from Spring AI (`seed`, `temperature` in
`OllamaChatOptions.toMap()`).

### In practice: prompt caching is on, unconditionally, and it is the known cause of seed non-determinism

In Ollama 0.33.2 the llama-server request is built with `CachePrompt: true` hardcoded on the completion path and
`"cache_prompt": true` hardcoded on the chat path
([`llm/llama_server.go`](https://github.com/ollama/ollama/blob/v0.33.2/llm/llama_server.go)). **There is no
public option that turns it off.**

That matters because the prompt cache is the mechanism upstream identifies as the root cause of seeded
non-determinism. [Issue #16635](https://github.com/ollama/ollama/issues/16635) states it directly — "the prompt
cache has been identified as the root cause of seeded non-determinism for a long time" — and cites the chain of
reports behind it: [#5321](https://github.com/ollama/ollama/issues/5321),
[#12559](https://github.com/ollama/ollama/issues/12559), [#4990](https://github.com/ollama/ollama/issues/4990),
and upstream [ggml-org/llama.cpp#2838](https://github.com/ggml-org/llama.cpp/issues/2838). Older reports on the
same behaviour: [#586](https://github.com/ollama/ollama/issues/586), "`/api/generate` with fixed seed and
temperature=0 doesn't produce deterministic results" — opened in 2023 by `jmorganca`, the repository's own owner,
and since closed; [#1749](https://github.com/ollama/ollama/issues/1749) (identical replies for the first few
calls after a fresh `ollama serve`, divergence afterwards);
[#4660](https://github.com/ollama/ollama/issues/4660), "Changing seed does not change response" (closed);
[#5321](https://github.com/ollama/ollama/issues/5321), "Llama3: Generated outputs inconsistent despite seed and
temperature" (open — first execution differs from all subsequent ones, and the settled result differs across
platforms); [#12559](https://github.com/ollama/ollama/issues/12559), "Non-deterministic structured output with
same seed" (open). Upstream, [ggml-org/llama.cpp#2838](https://github.com/ggml-org/llama.cpp/issues/2838) is
"CUDA non-determinism on identical requests" (closed) and
[#4990](https://github.com/ollama/ollama/issues/4990) is "First value different on CUDA/ROCM when setting
`seed`" (open).

`#16635` is **closed**, and the code above is why the resolution matters to read precisely: at v0.33.2 (and at
`main`/v0.34.0) `cache_prompt` is no longer wired to the public `shift` option — it is simply `true`. The
coupling the issue complained about is gone; the caching, and with it the reported non-determinism, is now
unconditional and has no public switch.

### What can and cannot be said

- **Sourced**: `seed` is documented as the reproducibility control; temperature 0 is documented as the way to get
  "more deterministic" output; prompt caching is unconditionally enabled; prompt caching is identified upstream
  as the cause of seeded non-determinism.
- **Sourced, and worth stating on its own**: *no Ollama document claims that a repeated call with a fixed seed
  returns identical text.* The strongest published wording is "For reproducible results" as a one-line parameter
  gloss. Spring AI's stronger Javadoc sentence is Spring AI's, describing an Ollama behaviour Ollama does not
  itself assert.
- **UNSOURCED**: whether a repeated call on one host, one loaded runner, fixed seed, temperature 0 and an
  explicit `num_ctx` is byte-identical in Ollama 0.33.2. The issue history says sometimes, and says the cache is
  why it is sometimes. **Only measurement can settle it**, and nothing here measured it.
- A second, independent source of drift, already established in §2: with `num_ctx` unsent the effective window is
  chosen from host VRAM and may be clamped to `n_ctx_train`, so "the same request" is not the same computation
  across hosts. Sending `num_ctx` explicitly removes that variable — which is also the workaround reported from
  outside the project ([rasbt/LLMs-from-scratch#249](https://github.com/rasbt/LLMs-from-scratch/issues/249),
  cited as a third-party report, not as a primary source).

---

## 5. Failure surfaces, as a Java caller sees them

### What Ollama returns

Every error is a JSON object with an `error` string; the status varies by handler
([`server/routes.go`](https://github.com/ollama/ollama/blob/v0.33.2/server/routes.go)). The generation-relevant
ones:

| Condition | Status | Body `error` |
|---|---|---|
| model not pulled / unknown name | **404** | `model 'NAME' not found` |
| model missing at schedule time | **404** | `model "NAME" not found, try pulling it first` (`handleScheduleError`) |
| model cannot generate (e.g. an embedding model) | **400** | `"NAME" does not support generate` / `… does not support chat` |
| prompt longer than the window, shift off | **400** | `the prompt is longer than the context length currently available to the model; …` (§2) |
| empty body | **400** | `missing request body` |
| malformed JSON | **400** | the binding error, echoed |
| bad `format` value | **500** unless raised as an `api.StatusError` | `invalid format: …` |
| client disconnected | **499** | `request canceled` |
| server queue full (`OLLAMA_MAX_QUEUE`, default 512) | **503** | the queue error |
| anything else | **500** | `err.Error()` |

Error mapping after generation has been scheduled is status-preserving **only** for `api.StatusError`; other
errors are emitted without a `status` key and default to 500
([`routes.go` generate handler and `streamResponse`](https://github.com/ollama/ollama/blob/v0.33.2/server/routes.go#L2097-L2130)).

### Partial responses

- **Non-streaming (`stream: false`, which is the only mode Spring AI's blocking `call()` allows — `OllamaApi.chat`
  asserts `!chatRequest.stream()`)**: the handler drains the channel, and on an error frame calls
  `c.JSON(status, gin.H{"error": msg})` with the carried status or 500. **A failure mid-generation therefore
  arrives as a real HTTP error status, never as a 200 with a broken body.**
- **Streaming**: `streamResponse` sets the status only "for errors that are streamed before any content"; once
  bytes have been written it appends `{"error": …}` as one more NDJSON line under the already-sent **200**
  ([`routes.go`](https://github.com/ollama/ollama/blob/v0.33.2/server/routes.go#L2097-L2130)). So a streaming
  failure is a well-formed 200 whose last frame is an error object.
- A **reading of the Spring AI code, not a measurement**: `OllamaApi.ChatResponse` is
  `@JsonIgnoreProperties(ignoreUnknown = true)` and has no `error` component, so such a frame deserialises to a
  `ChatResponse` with every field null; `OllamaChatModel` then dereferences `ollamaResponse.message().toolCalls()`
  with no null guard. The expected outcome is a `NullPointerException` rather than an exception naming the
  server's error. **UNSOURCED** — this is inferred from the two files, and only a probe would confirm it.

### What reaches the Java caller

Spring AI's `OllamaApi` is built with `RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER` unless a `ResponseErrorHandler`
bean overrides it
([`OllamaApiAutoConfiguration`](https://github.com/spring-projects/spring-ai/blob/v2.0.0/auto-configurations/models/spring-ai-autoconfigure-model-ollama/src/main/java/org/springframework/ai/model/ollama/autoconfigure/OllamaApiAutoConfiguration.java)).
That handler
([`RetryUtils`](https://github.com/spring-projects/spring-ai/blob/v2.0.0/spring-ai-retry/src/main/java/org/springframework/ai/retry/RetryUtils.java))
reads the whole body and throws:

- **4xx → `NonTransientAiException`**, message `"<status> - <body>"` — so a 404 model-not-pulled and a 400
  prompt-too-long both surface as that one type, distinguishable only by parsing the message string;
- **anything else in error → `TransientAiException`**, same message format.

`OllamaChatModel` wraps each call in `RetryUtils.DEFAULT_RETRY_TEMPLATE` by default. That template retries
`TransientAiException` **and** `ResourceAccessException` (which is what a connect/read timeout arrives as) with
**10 retries, 2 s initial delay, ×5 multiplier, capped at 3 minutes** — so a persistently failing 5xx or a dead
sidecar is retried for on the order of ten minutes before the call gives up, logging a warning per attempt.
`RetryUtils.execute` unwraps `RetryException` and rethrows the cause when it is a `RuntimeException`. A 404 is
*not* retried.

### Timeouts

**Spring AI sets none.** `OllamaApi` builds its `RestClient` from an injected `RestClient.Builder` and its
`WebClient` from an injected `WebClient.Builder`, adding only base URL, headers and the error handler; the
auto-configuration passes Boot's beans through
(`restClientBuilderProvider.getIfAvailable(RestClient::builder)`). So the HTTP timeouts are whatever Boot 4.1.1's
auto-configured builder carries — i.e. configured on the Boot side, not on `spring.ai.ollama.*`. **UNSOURCED**:
no Spring AI property sets a per-call generation timeout, and I found none documented on the Ollama chat
reference page. (The one timeout Spring AI does own, `spring.ai.ollama.init.timeout`, default `5m`, is for model
*pulling*, not generation.)

Relevant Ollama-side deadlines, for completeness:
`OLLAMA_LOAD_TIMEOUT` — "How long to allow model loads to stall before giving up (default \"5m\")" — and
`OLLAMA_MAX_QUEUE`, default 512
([`envconfig/config.go`](https://github.com/ollama/ollama/blob/v0.33.2/envconfig/config.go)).

### Model-not-pulled, specifically

The 404 can be pre-empted rather than handled: `spring.ai.ollama.init.pull-model-strategy` (`never`, default;
`when_missing`; `always`), with `init.timeout` `5m` and `init.max-retries` `0`
([`OllamaInitializationProperties`](https://github.com/spring-projects/spring-ai/blob/v2.0.0/auto-configurations/models/spring-ai-autoconfigure-model-ollama/src/main/java/org/springframework/ai/model/ollama/autoconfigure/OllamaInitializationProperties.java),
[reference](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html)). The reference documentation
warns against it in anger: "Due to potential delays while downloading models, automatic pulling is not
recommended for production environments", and "The application will not complete its initialization until all
specified models are available in Ollama."

This repo's `application.yaml` sets `spring.ai.model.chat: ollama` and leaves the init strategy at its default,
so today a missing model is a 404 at call time, not a startup failure.

---

## 6. What the model reports about itself

The rule is `CONTEXT.md` **Instrument**: an identity is "composed of what the instrument's runtime reports about
itself plus what we asked of it, never of where it is served", and it refuses a blank.
[ADR-090](../adr/0090-the-extractor-identity-is-the-sidecars-version-map-and-the-options-the-client-sends-never-its-url.md)
set it for the extractor;
[ADR-091](../adr/0091-there-is-no-tokenizer-the-runtime-counts-tokens-and-the-embedder-identity-is-what-ollama-reports.md)
applied it to `EmbedderIdentity`, whose five parts are `modelName`, `artefactDigest`, `weightDtype`,
`outputDimension` (the value **sent**, checked against what came back) and `instruction`.
`ExtractorIdentity` carries the whole composed string and refuses a blank. This section records **only what is
available** for the same construction on the generation side; which parts belong in it is a decision, not a fact.

### What the runtime reports

| Candidate part | Where it is reported | Notes |
|---|---|---|
| artefact digest | `GET /api/tags` → `models[].digest` | The **manifest** digest, over the layers *and* the modelfile; the only endpoint carrying one — `/api/show` has none. Already read by `OllamaClient.artefactOf`, unchanged by anything in this record. |
| weight dtype | `GET /api/tags` → `models[].details.quantization_level` | Also already read. |
| model family / parameter size / format | `/api/tags` and `/api/show` → `details` | `parent_model`, `format`, `family`, `families`, `parameter_size` ([tags](https://docs.ollama.com/api/tags), [show](https://docs.ollama.com/api-reference/show-model-details)) |
| **capabilities** | `POST /api/show` → `capabilities` | A list of strings; the documented example shows `completion` and `vision`. This is new relative to the embedder case: it is how the runtime says whether a model can generate at all, and it is the field whose absence produces the `"NAME" does not support generate` 400 of §5. Spring AI's `ShowModelResponse` **does** carry it. |
| trained context length | `POST /api/show` → `model_info["<arch>.context_length"]` | Architecture-prefixed; needs `general.architecture` first (§2). |
| **effective context length** | `GET /api/ps` → `context_length` | Only while loaded; **not reachable through Spring AI 2.0.0**, which has no `/api/ps` call. |
| model name, as the server echoes it | the generation response's `model` field | **Echoed verbatim from the request** (`Model: req.Model`), not resolved — so it is a copy of what we asked for, and adds nothing a digest does. |
| prompt/response token counts | `prompt_eval_count`, `eval_count` | Per call, not identity; ADR-091 already rejected `prompt_eval_count` as a stable contract. |

**Nothing in a `/api/generate` or `/api/chat` response carries a digest, a dtype, or any weight identity.** The
full response field list is in §1; a generator identity has to come from the metadata endpoints, exactly as the
embedder's does.

### What we ask of it

The generation-side analogues of ADR-091's "output dimension is the value sent":

- `num_ctx` — **the value sent**. Two facts from §2 make it unlike the embedding dimension: sending nothing means
  the host's VRAM tier decides, and sending too much means silent reduction to `n_ctx_train`. And there is no
  returned artefact to check it against within the same call — the only read-back is `/api/ps`, a separate call
  against a loaded runner, unreachable from Spring AI.
- the sampling options actually sent — `seed`, `temperature`, `num_predict`, `top_k`/`top_p`, `stop`
- `format` — whether a schema was imposed, and which one; the schema text is part of what was asked (and per §3
  it changes the sampler, not just the parsing)
- `think` — whether a reasoning trace was requested, and at which level
- the prompt/system/template shape, which for `/api/chat` is the message roles rather than a single string

### What is excluded by the rule

`spring.ai.ollama.base-url` (default `http://localhost:11434`) and anything else naming *where* — the same
exclusion ADR-090 made for the sidecar's URL.

### Spring AI's DTO gaps, re-checked at 2.0.0

ADR-091's finding holds for the generation case and gains one correction: `ShowModelResponse` still carries no
`digest` (because `/api/show` returns none) and `Model.Details` still omits `embedding_length`, and both records
are `@JsonIgnoreProperties(ignoreUnknown = true)` so the omissions are silent. `ShowModelResponse` *does* expose
`capabilities` (typed) and `model_info` (untyped `Map<String, Object>`). There is no `/api/ps` binding at all.
The reason `OllamaClient` exists — read the identity where a blank can be refused — applies unchanged.

---

## 7. Where the sources are silent

Stated explicitly, because a gap read as a fact is the failure this record exists to prevent:

1. **`truncate` and `shift` are undocumented.** They are in `api/types.go` for both generation endpoints and in
   neither the OpenAPI document nor `docs/api.md`. A design resting on them rests on source, at a pinned tag.
2. **No documented status codes for `/api/generate` or `/api/chat`.** The OpenAPI documents list exactly one
   response each — `200`. Every error code in §5 comes from reading `server/routes.go`.
3. **No documented statement of what overflow does on the generation endpoints.** The context-length page
   describes tiers, the app slider and `OLLAMA_CONTEXT_LENGTH`, and says nothing about truncation, shifting or
   errors. §2 is entirely source-derived.
4. **No guarantee of schema conformance**, and no documented behaviour for a model that cannot satisfy the
   schema.
5. **No claim anywhere that a fixed seed reproduces output.** The published wording is "For reproducible
   results"; the issue history says the prompt cache breaks it; the cache cannot be switched off.
6. **Nothing measured here.** Every number in this record is a number a document or a source file states. The
   three questions that need a probe before anyone relies on them are: whether a fixed-seed repeat is actually
   identical on one loaded runner (§4); whether `/api/ps`'s `context_length` equals the limit the prompt check
   applies (§2); and what Spring AI's streaming path actually throws on a mid-stream error frame (§5).
