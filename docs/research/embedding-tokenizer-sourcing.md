# Where an embedding model's tokenizer vocabulary actually comes from

Research record for [issue #103](https://github.com/algernon28/vespera/issues/103). Facts only — no decisions.

## Scope and method

Two claims were put to me to verify or refute:

1. *The weights come from Ollama as GGUF, but the vocabulary needed to count tokens cannot be read out of it from
   Java, so an HF `tokenizer.json` must be supplied separately.*
2. *`ai.djl.huggingface:tokenizers` could shift counts on a version bump even with an unchanged vocabulary file,
   because it wraps the Rust `tokenizers` crate.*

Every claim below is either (a) quoted from a primary source — the GGUF specification, llama.cpp source, Ollama
source, Ollama's own API documentation, a release note or a pull request — or (b) **measured** on the machine
described below and labelled `MEASURED`. Where I could not find a primary source, the claim is labelled
`UNSOURCED` and says so.

Measurement environment:

| | |
|---|---|
| OS | Windows 11 Pro, build 10.0.26200 |
| JDK | OpenJDK 26.0.2.1+1-7 (matches `<java.version>26</java.version>` in `pom.xml`) |
| Docker | Server 29.6.1 |
| Ollama | image `ollama/ollama:latest`, digest `sha256:08ddf4b4dbfdc4fc1f5b0fe535915998581085402e307b57bb308db68460e372`, created 2026-08-26; `/api/version` reports `0.33.0` |
| Container | `docker run -d --name vespera-tokprobe -p 11435:11434 ollama/ollama:latest` |
| Models pulled | `all-minilm:latest` (45 MB), `nomic-embed-text:latest` (274 MB), `qwen3-embedding:0.6b` (639 MB) |
| Reference tokenizer | Python `tokenizers` 0.23.2 — the *same* Rust crate `ai.djl.huggingface:tokenizers` wraps, used here as the HF reference implementation |

`tokenizer.json` reference files were fetched from the owning model repositories:
[`sentence-transformers/all-MiniLM-L6-v2`](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/blob/main/tokenizer.json)
and [`Qwen/Qwen3-Embedding-0.6B`](https://huggingface.co/Qwen/Qwen3-Embedding-0.6B/blob/main/tokenizer.json).

Ollama source citations are to commit
[`83ed7d9`](https://github.com/ollama/ollama/tree/83ed7d9965b1ee07e0f0b29fd46e47c31f0fcab8), the `main` tip at the
time of writing. llama.cpp citations are to `master` at the same time.

---

## 1. The premise is refuted: Ollama serves the full tokenizer vocabulary

**The vocabulary *is* retrievable from Ollama over HTTP, with no HuggingFace download and no GGUF parsing in
Java.** `POST /api/show` with `"verbose": true` returns `tokenizer.ggml.tokens` and `tokenizer.ggml.merges` in
full, as JSON arrays, in the `model_info` map. This is stated in Ollama's own API documentation and I measured it
on all three embedding models.

Everything else in this section is detail about *what* that gets you, and §3 is about what it does **not** get
you — which turns out to be the part that matters.

### What the GGUF spec says is in the file

The [GGUF specification](https://github.com/ggml-org/ggml/blob/master/docs/gguf.md) §Tokenizer defines the
embedded vocabulary keys:

> - `tokenizer.ggml.model: string`: The name of the tokenizer model.
>   - `llama`: Llama style SentencePiece (tokens and scores extracted from HF `tokenizer.model`)
>   - `replit`: Replit style SentencePiece (tokens and scores extracted from HF `spiece.model`)
>   - `gpt2`: GPT-2 / GPT-NeoX style BPE (tokens extracted from HF `tokenizer.json`)
>   - `rwkv`: RWKV tokenizer
> - `tokenizer.ggml.tokens: array[string]`: A list of tokens indexed by the token ID used by the model.
> - `tokenizer.ggml.scores: array[float32]`: If present, the score/probability of each token. If not present, all
>   tokens are assumed to have equal probability. If present, it must have the same length and index as `tokens`.
> - `tokenizer.ggml.token_type: array[int32]`: The token type (1=normal, 2=unknown, 3=control, 4=user defined,
>   5=unused, 6=byte). If present, it must have the same length and index as `tokens`.
> - `tokenizer.ggml.merges: array[string]`: If present, the merges of the tokenizer. If not present, the tokens
>   are assumed to be atomic.
> - `tokenizer.ggml.added_tokens: array[string]`: If present, tokens that were added after training.

plus `bos_token_id`, `eos_token_id`, `unknown_token_id`, `separator_token_id`, `padding_token_id`, all `uint32`.

So the *token list and the merge table are specified GGUF content*, not an accident.

Three things about that spec section are worth having in front of you, because they are the whole of §3:

> GGML supports an embedded vocabulary that enables inference of the model, but implementations of tokenization
> using this vocabulary (i.e. `llama.cpp`'s tokenizer) **may have lower accuracy than the original tokenizer used
> for the model. When a more accurate tokenizer is available and supported, it should be used instead.**

> It is not guaranteed to be standardized across models, and may change in the future. It is recommended that
> model authors use a more standardized tokenizer if possible.

And the spec provides a key for carrying the HF file itself:

> #### Hugging Face
>
> Hugging Face maintains their own `tokenizers` library that supports a wide variety of tokenizers. If your
> executor uses this library, it may be able to use the model's tokenizer directly.
>
> - `tokenizer.huggingface.json: string`: the entirety of the HF `tokenizer.json` for a given model […] Included
>   for compatibility with executors that support HF tokenizers directly.

`tokenizer.ggml.pre` is **not in the spec.** It is a de-facto llama.cpp key; see §3.3.

### `tokenizer.ggml.normalizer.*` is also not in the spec

llama.cpp reads two normalizer keys that the spec does not document
([`src/llama-arch.cpp`](https://github.com/ggml-org/llama.cpp/blob/master/src/llama-arch.cpp)):

```cpp
{ LLM_KV_TOKENIZER_NORMALIZER_LOWERCASE,     "tokenizer.ggml.normalizer.lowercase"     },
{ LLM_KV_TOKENIZER_NORMALIZER_STRIP_ACCENTS, "tokenizer.ggml.normalizer.strip_accents" },
```

Neither appeared in any of the three models I probed (§1 `MEASURED` tables below).

### How Ollama decides to send it or elide it

`/api/show` is a *pure file-metadata read* — no runner, no weights loaded. The chain is
`ShowHandler` → `GetModelInfo` → `getModelData(m.ModelPath, req.Verbose)`
([`server/routes.go`](https://github.com/ollama/ollama/blob/83ed7d9965b1ee07e0f0b29fd46e47c31f0fcab8/server/routes.go#L1605-L1627)),
and `verbose` becomes an array-size budget:

```go
func getModelData(digest string, verbose bool) (ggml.KV, ggml.Tensors, error) {
	maxArraySize := 0
	if verbose {
		maxArraySize = -1
	}
	data, err := llm.LoadModel(digest, maxArraySize)
```

[`llm/server.go`](https://github.com/ollama/ollama/blob/83ed7d9965b1ee07e0f0b29fd46e47c31f0fcab8/llm/server.go#L87-L104)
documents the contract:

```go
// LoadModel will load a model from disk. The model must be in the GGML format.
//
// It collects array values for arrays with a size less than or equal to
// maxArraySize. If maxArraySize is 0, the default value of 1024 is used. If
// the maxArraySize is negative, all arrays are collected.
```

(The "default value of 1024" sentence is stale — `newArray` implements no such default; `0` means "collect
nothing".) The elision itself is in
[`fs/ggml/gguf.go`](https://github.com/ollama/ollama/blob/83ed7d9965b1ee07e0f0b29fd46e47c31f0fcab8/fs/ggml/gguf.go#L457-L483):

```go
type array[T any] struct {
	// size is the actual size of the array
	size int

	// values is the array of values. this is nil if the array is larger than configured maxSize
	values []T
}

func (a *array[T]) MarshalJSON() ([]byte, error) {
	return json.Marshal(a.values)
}

func newArray[T any](size uint64, maxSize int) (*array[T], error) {
	...
	a := array[T]{size: n}
	if maxSize < 0 || n <= maxSize {
		a.values = make([]T, n)
	}
	return &a, nil
}
```

A nil slice marshals as `null`, which is exactly what a non-verbose response contains. Note that the bytes are
read off disk either way and merely discarded
([`readGGUFArrayData`](https://github.com/ollama/ollama/blob/83ed7d9965b1ee07e0f0b29fd46e47c31f0fcab8/fs/ggml/gguf.go#L542-L554)),
so non-verbose saves memory and response size, not I/O.

[`docs/api.md`](https://github.com/ollama/ollama/blob/83ed7d9965b1ee07e0f0b29fd46e47c31f0fcab8/docs/api.md)
documents the parameter and the behaviour:

> - `verbose`: (optional) if set to `true`, returns full data for verbose response fields

```json5
    "tokenizer.ggml.merges": [], // populates if `verbose=true`
    "tokenizer.ggml.model": "gpt2",
    "tokenizer.ggml.pre": "llama-bpe",
    "tokenizer.ggml.token_type": [], // populates if `verbose=true`
    "tokenizer.ggml.tokens": [], // populates if `verbose=true`
```

The documented example shows `[]`; the current decoder emits `null`. My measurement below records `null`.

Ollama caches show responses in-process keyed on `{Model, Verbose}`
([`server/model_show_cache.go`](https://github.com/ollama/ollama/blob/83ed7d9965b1ee07e0f0b29fd46e47c31f0fcab8/server/model_show_cache.go)),
whose own comment gives the cost model:

> Local show responses stay lazy because even non-verbose show must load GGUF metadata that is expensive for
> large model stores.

### `MEASURED` — what actually comes back

`POST /api/show` against the container above, model never loaded (`/api/ps` reported `{"models":[]}` throughout;
a verbose call for `qwen3-embedding:0.6b` returned in 51 ms).

Response body size, `model_info` only differing by the arrays:

| model | `verbose` absent | `"verbose": true` |
|---|---|---|
| `all-minilm:latest` | 30,844 bytes | **627,026 bytes** |
| `nomic-embed-text:latest` | 32,080 bytes | **628,262 bytes** |
| `qwen3-embedding:0.6b` | 26,785 bytes | **4,148,605 bytes** |

`tokenizer.ggml.*` keys present, and their contents with `verbose: true`:

| key | `all-minilm` | `nomic-embed-text` | `qwen3-embedding:0.6b` |
|---|---|---|---|
| `model` | `"bert"` | `"bert"` | `"gpt2"` |
| `pre` | *absent* | *absent* | `"qwen2"` |
| `tokens` | **array, 30522** | **array, 30522** | **array, 151669** |
| `merges` | *absent* | *absent* | **array, 151387** |
| `token_type` | array, 30522 | array, 30522 | array, 151669 |
| `scores` | array, 30522 (all `-1000`) | array, 30522 (all `-1000`) | *absent* |
| `token_type_count` | `2` | `2` | *absent* |
| `bos_token_id` / `eos_token_id` | `101` / `102` | `101` / `102` | `151643` / `151643` |
| `cls_token_id` / `mask_token_id` | `101` / `103` | `101` / `103` | *absent* |
| `seperator_token_id` *(sic)* | `102` | `102` | *absent* |
| `padding_token_id` | `0` | `0` | `151643` |
| `unknown_token_id` | `100` | `100` | *absent* |
| `eot_token_id` | *absent* | *absent* | `151645` |
| `add_bos_token` / `add_eos_token` | *absent* | *absent* | `false` / `true` |
| `normalizer.lowercase` / `.strip_accents` | *absent* | *absent* | *absent* |

Without `verbose`, `tokens`, `merges`, `token_type` and `scores` all come back as JSON `null`; every scalar key
above is present in both modes.

Nothing is truncated. `all-minilm` returns all 30,522 tokens ending correctly; `qwen3-embedding:0.6b` returns all
151,669 tokens **and** all 151,387 merge rules. `token_type` for the BERT models is `3` (control) for exactly the
5 special tokens and `1` (normal) for the other 30,517; `scores` is the constant `-1000` for every entry, i.e.
carries no information.

The earlier probe note that "every non-tokenizer key is arch-prefixed" is consistent with this: the
non-tokenizer keys for `all-minilm` are `bert.*` (8 of them) plus `general.architecture`, `general.file_type`,
`general.parameter_count`.

**So the claim that "the vocabulary cannot be read out of it from Java" is false at the level it was stated.** A
Java client that can issue one HTTP POST and parse a JSON array has the token list and the merge table. No
HuggingFace download is *required* to obtain a vocabulary. Whether that vocabulary is sufficient to reproduce the
model's token counts is a different question, and is §3.

---

## 2. Reaching it from the JVM

### 2.1 Through the dependency this project already has

`pom.xml` already declares `spring-ai-starter-model-ollama` at `spring-ai.version` 2.0.0, and Spring AI's
`OllamaApi` covers `/api/show` **including the `verbose` flag**
([`OllamaApi.java` @ v2.0.0](https://github.com/spring-projects/spring-ai/blob/ef502dab692e26b953a75be4029dba7f1acdc88c/models/spring-ai-ollama/src/main/java/org/springframework/ai/ollama/api/OllamaApi.java#L727-L739)):

```java
	@JsonInclude(Include.NON_NULL)
	public record ShowModelRequest(
			@JsonProperty("model") String model,
			@JsonProperty("system") @Nullable String system,
			@JsonProperty("verbose") @Nullable Boolean verbose,
			@JsonProperty("options") @Nullable Map<String, Object> options
	) {
		public ShowModelRequest(String model) {
			this(model, null, null, null);
		}
	}
```

Note the convenience constructor leaves `verbose` **null**, and `@JsonInclude(NON_NULL)` then omits the key
entirely — so `new ShowModelRequest(model)` gets the elided response; the 4-arg canonical constructor is required.
`verbose` is present in 1.0.0, 1.1.0 and 2.0.0 alike; the only 2.0.0-era change is the JSpecify `@Nullable`
annotations.

The response side keeps `model_info` untyped
([lines 741-756](https://github.com/spring-projects/spring-ai/blob/ef502dab692e26b953a75be4029dba7f1acdc88c/models/spring-ai-ollama/src/main/java/org/springframework/ai/ollama/api/OllamaApi.java#L741-L756)):

```java
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ShowModelResponse(
			...
			@JsonProperty("model_info") Map<String, Object> modelInfo,
			@JsonProperty("projector_info") Map<String, Object> projectorInfo,
			@JsonProperty("capabilities") List<String> capabilities,
			@JsonProperty("modified_at") Instant modifiedAt
	) { }
```

So the token list arrives as a `List<Object>` inside a `Map<String, Object>`. There is no convenience accessor for
tokenizer keys, no truncation, and no cap — the record has an empty body. `tensors` is *not* a component, so
Ollama's `tensors` array is discarded by `@JsonIgnoreProperties(ignoreUnknown = true)`.

`OllamaApi.listModels()` covers `/api/tags`, and its `Model` record does expose the manifest digest
([lines 699-726](https://github.com/spring-projects/spring-ai/blob/ef502dab692e26b953a75be4029dba7f1acdc88c/models/spring-ai-ollama/src/main/java/org/springframework/ai/ollama/api/OllamaApi.java#L699-L726)):
`@JsonProperty("digest") String digest`. `MEASURED`: `/api/tags` returned
`qwen3-embedding:0.6b` → `ac6da0dfba84a81fdbfbaf330198c33cd77c4cdfc53e8bc50eb581914a15621d`,
`nomic-embed-text:latest` → `0a109f422b47e3a30ba2b10eca18548e944e8a23073ee3f3e947efcf3c45e59f`,
`all-minilm:latest` → `1b226e2802dbb772b5fc32a58f103ca1804ef7501331012de126ab22f67475ef`. Recorded here only
because it is the same probe; it belongs to part 2 of the ticket, not to this question.

### 2.2 Libraries that parse GGUF directly

Nothing on Maven Central carries "gguf" in its coordinates — a Central search for `gguf` and for `a:gguf` both
return `numFound: 0`. Every JVM GGUF reader is bundled inside an inference library under another name.

| library | coordinates | on Central | metadata KV | vocab arrays | tokenizes from GGUF vocab | stars / last push |
|---|---|---|---|---|---|---|
| [GPULlama3.java](https://github.com/beehive-lab/GPULlama3.java) | `io.github.beehive-lab:gpu-llama3:1.0.0-jdk25` | [yes](https://repo1.maven.org/maven2/io/github/beehive-lab/gpu-llama3/maven-metadata.xml) (2026-07-28) | yes | yes | **yes, pure Java** | 272 / 2026-08-28 |
| [integrallis/models](https://github.com/integrallis/models) | `com.integrallis:backend-java:0.3.31` | [yes](https://repo1.maven.org/maven2/com/integrallis/models/maven-metadata.xml) (2026-09-06) | yes | yes | **yes, pure Java** | 3 / 2026-09-07 |
| [java-llama.cpp](https://github.com/kherud/java-llama.cpp) | `de.kherud:llama:4.2.0` | [yes](https://repo1.maven.org/maven2/de/kherud/llama/maven-metadata.xml) (2025-06-20) | **no** | no | yes, via native llama.cpp | 434 / 2025-06-20 |
| [storch-gguf](https://github.com/bytedeco/storch-gguf) (Scala 3) | `io.github.mullerhai:storch-gguf_3:0.0.3` | [yes](https://repo1.maven.org/maven2/io/github/mullerhai/storch-gguf_3/maven-metadata.xml) (2025-09-25) | yes | raw arrays only | no | 0 / 2025-09-25 |
| [gguf4j](https://github.com/ilopezluna/gguf4j) | `1.0-SNAPSHOT` only | **no** | yes | raw KV only | no | 3 / 2025-06-09 |
| [llama3.java](https://github.com/mukel/llama3.java) | — (single file) | **no** | yes | yes | **yes, pure Java** | 816 / 2026-04-24 |
| [Jlama](https://github.com/tjake/Jlama) | `com.github.tjake:jlama-core:0.8.4` | yes | — | — | — | 1304 / 2025-10-12 |

Specifics worth having:

- **GPULlama3.java** is the most credible published option.
  [`GGUF.java`](https://github.com/beehive-lab/GPULlama3.java/blob/main/src/main/java/org/beehive/gpullama3/tensor/GGUF.java)
  exposes `public static GGUF loadGGUFMetadata(Path modelPath)` and `public Map<String, Object> getMetadata()` —
  a metadata-only read with no tensor load — and
  [`Vocabulary.java`](https://github.com/beehive-lab/GPULlama3.java/blob/main/src/main/java/org/beehive/gpullama3/tokenizer/Vocabulary.java)
  reads `tokenizer.ggml.tokens` and `tokenizer.ggml.scores` out of that map. It is reachable transitively through
  [`langchain4j-gpu-llama3`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-gpu-llama3/pom.xml).
- **`de.kherud:llama`** can tokenize (`public native int[] encode(String prompt)`) but exposes **no** GGUF
  metadata, and requires loading the whole model into llama.cpp to do it. It has not shipped in ~15 months.
- **Jlama cannot read GGUF at all** — a code search for `gguf` in the repo returns zero hits; its format layer is
  safetensors.
- **Spring AI and LangChain4j have no GGUF-metadata API of their own.** LangChain4j's only GGUF parsing is the
  transitive `gpu-llama3` dependency.
- `mukel/llama3.java` is the cleanest reference implementation of the whole path (metadata + vocab + merges + a
  byte-level BPE tokenizer, `loadModel(path, ctx, loadWeights=false)`), but it is one 3,425-line file that is not
  published anywhere and supports GGUF version 3 only.

Note that none of these is needed to obtain the vocabulary if Ollama is the source — §1 gets it over HTTP. They
matter only for reading a GGUF file directly off disk.

### 2.3 `ai.djl.huggingface:tokenizers` accepts a vocabulary from memory

Latest release **0.36.0**, published 2025-12-16
([Central metadata](https://repo1.maven.org/maven2/ai/djl/huggingface/tokenizers/maven-metadata.xml)); the jar is
~18.7 MB because the native libraries are bundled. It reads `tokenizer.json` (or an HF hub identifier, or
`vocab.json` + `merges.txt`). **It cannot read GGUF.**

It does not require a file on disk.
[`HuggingFaceTokenizer`](https://github.com/deepjavalibrary/djl/blob/master/extensions/tokenizers/src/main/java/ai/djl/huggingface/tokenizers/HuggingFaceTokenizer.java)
has an `InputStream` overload that goes straight to the native `createTokenizerFromString`:

```java
    public static HuggingFaceTokenizer newInstance(InputStream is, Map<String, String> options)
```

which reads the stream to a `String` and calls `TokenizersLibrary.LIB.createTokenizerFromString(json)`. The
`newInstance(Path vocab, Path merges, Map options)` overload does *not* have an in-memory equivalent — it demands
real files. So a GGUF-sourced vocabulary would have to be assembled into a synthetic `tokenizer.json` string and
handed to the `InputStream` overload; and §3 is the list of fields such a synthesis would have to invent, because
GGUF does not carry them.

The native library is a Rust `cdylib` wrapping HuggingFace's `tokenizers` crate
([`Cargo.toml`](https://github.com/deepjavalibrary/djl/blob/master/extensions/tokenizers/rust/Cargo.toml)). Which
crate version, per DJL release, is §4.1.

---

## 3. What the GGUF vocabulary does not carry — and what that costs

`tokenizer.json` is a *complete tokenizer*: a normalizer, a pre-tokenizer, a model (vocab + merges), a
post-processor and a decoder, plus optional truncation and padding settings. The GGUF keys carry the **model**
part and a handful of scalars. Everything else is either absent or compressed into one opaque label.

### 3.1 The vocabulary itself is faithful for BPE — `MEASURED`

For `qwen3-embedding:0.6b` versus
[`Qwen/Qwen3-Embedding-0.6B/tokenizer.json`](https://huggingface.co/Qwen/Qwen3-Embedding-0.6B/blob/main/tokenizer.json):

| | HF `tokenizer.json` | Ollama GGUF |
|---|---|---|
| model type | `BPE` | `tokenizer.ggml.model = "gpt2"` |
| vocab entries | 151,643 + 26 added tokens = 151,669 ids | `tokens`, 151,669 |
| merges | 151,387 | `merges`, 151,387 |

- **Token strings differ at 0 of 151,669 ids.** Every id maps to the identical string in both.
- **Merges are identical as an *ordered* list**, not merely as a set. The only difference is encoding: HF stores
  each merge as a two-element array `["Ġ","Ġ"]`, GGUF as the space-joined string `"Ġ Ġ"`.

So for a BPE model, `tokenizer.ggml.tokens` + `tokenizer.ggml.merges` is a byte-faithful copy of the HF vocab and
merge table. This matches the spec's own note that `gpt2`-type tokens are "extracted from HF `tokenizer.json`".

### 3.2 For BERT/WordPiece the vocabulary is *rewritten* — `MEASURED`

For `all-minilm:latest` versus
[`sentence-transformers/all-MiniLM-L6-v2/tokenizer.json`](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/blob/main/tokenizer.json),
both have 30,522 entries, but **29,523 of the 30,522 token strings differ from HF at the same id.** Examples:

| id | HF | GGUF |
|---|---|---|
| 999 | `!` | `▁!` |
| 1000 | `"` | `▁"` |
| 1005 | `'` | `▁'` |

The cause is in Ollama's converter, not in the spec —
[`convert/convert_bert.go`](https://github.com/ollama/ollama/blob/83ed7d9965b1ee07e0f0b29fd46e47c31f0fcab8/convert/convert_bert.go):

```go
	kv["tokenizer.ggml.model"] = "bert"
	kv["tokenizer.ggml.token_type_count"] = uint32(2)

	// convert to phantom space tokens
	for i, e := range t.Tokens {
		if strings.HasPrefix(e, "[") && strings.HasSuffix(e, "]") {
			// noop
		} else if strings.HasPrefix(e, "##") {
			t.Tokens[i] = e[2:]
		} else {
			t.Tokens[i] = "▁" + e
		}
	}
```

Classifying every one of the 30,522 entries against those three branches accounts for all of them exactly, with
no residue:

| branch | count |
|---|---|
| `[bracketed]` — left alone | 999 |
| `##`-prefixed — prefix stripped | 5,828 |
| otherwise — `▁` (U+2581) prepended | 23,695 |
| unexplained | **0** |

The transformation is total and mechanically reversible, and it preserves ids, so a consumer that knows the rule
can recover the HF vocabulary. But **`tokenizer.ggml.tokens` for a BERT model is not a WordPiece vocabulary** and
cannot be handed to a WordPiece tokenizer as-is: the `##` continuation marker that WordPiece dispatches on has
been deleted, and `continuing_subword_prefix` (`"##"`), `unk_token` (`"[UNK]"`) and `max_input_chars_per_word`
(`100`) — all present in the HF file's `model` object — are recorded nowhere in GGUF.

Neither is the normalizer. The HF file carries:

```json
"normalizer": {"type":"BertNormalizer","clean_text":true,"handle_chinese_chars":true,"strip_accents":null,"lowercase":true}
"pre_tokenizer": {"type":"BertPreTokenizer"}
```

None of `clean_text`, `handle_chinese_chars`, `strip_accents` or `lowercase` is in the GGUF metadata for either
BERT model (§1 table: the two `tokenizer.ggml.normalizer.*` keys are absent). llama.cpp therefore falls back to
its struct defaults ([`src/llama-vocab.h`](https://github.com/ggml-org/llama.cpp/blob/master/src/llama-vocab.h)):

```cpp
    struct normalizer_options {
        bool lowercase     = true;
        bool strip_accents = true;
        // TODO: clean_text, handle_chinese_chars
    };
```

and, when reading the keys, couples the two the way HF does
([`src/llama-vocab.cpp`](https://github.com/ggml-org/llama.cpp/blob/master/src/llama-vocab.cpp)):

```cpp
        // BertNormalizer options
        ml.get_key(LLM_KV_TOKENIZER_NORMALIZER_LOWERCASE,     normalizer_opts.lowercase,     false);
        normalizer_opts.strip_accents = normalizer_opts.lowercase;
        ml.get_key(LLM_KV_TOKENIZER_NORMALIZER_STRIP_ACCENTS, normalizer_opts.strip_accents, false);
```

For `all-MiniLM-L6-v2` the defaults happen to match the model (`lowercase: true`, and HF's `strip_accents: null`
means "follow `lowercase`"). That is a coincidence of this model, not a guarantee: a cased BERT model whose GGUF
omits the key would be lowercased by default. `UNSOURCED`: I found no written statement anywhere that the GGUF
`bert` vocabulary is *intended* to be lossy in this way — the phantom-space rewrite is documented only by the
three-line comment quoted above, and the missing normalizer flags only by the `TODO` above.

### 3.3 `tokenizer.ggml.pre` exists precisely because the vocabulary is not enough

The key is absent from the spec but defined in llama.cpp
([`src/llama-arch.cpp`](https://github.com/ggml-org/llama.cpp/blob/master/src/llama-arch.cpp)):

```cpp
{ LLM_KV_TOKENIZER_PRE,                      "tokenizer.ggml.pre"                      },
```

Its purpose is stated verbatim in the converter that writes it
([`convert_hf_to_gguf_update.py`](https://github.com/ggml-org/llama.cpp/blob/master/convert_hf_to_gguf_update.py)):

> is specific for the BPE pre-tokenizer used by the model
> we will use this unique identifier to write a "tokenizer.ggml.pre" entry in the GGUF file which we can
> use in llama.cpp to implement the same pre-tokenizer

So the field is an *identifier for a pre-tokenizer that llama.cpp reimplements*, chosen by hashing the reference
tokenizer's output over a fixed test string. The pre-tokenizer regex is not stored in the file; it is compiled
into llama.cpp and selected by this label. `tokenizer.ggml.pre = "qwen2"` — the value my probe found on
`qwen3-embedding:0.6b` — resolves to
([`src/llama-vocab.cpp`](https://github.com/ggml-org/llama.cpp/blob/master/src/llama-vocab.cpp)):

```cpp
            case LLAMA_VOCAB_PRE_TYPE_QWEN2:
                regex_exprs = {
                    // original regex from tokenizer.json
                    // "(?i:'s|'t|'re|'ve|'m|'ll|'d)|[^\\r\\n\\p{L}\\p{N}]?\\p{L}+|\\p{N}| ?[^\\s\\p{L}\\p{N}]+[\\r\\n]*|\\s*[\\r\\n]+|\\s+(?!\\S)|\\s+"
                    "(?:'[sS]|'[tT]|'[rR][eE]|'[vV][eE]|'[mM]|'[lL][lL]|'[dD])|[^\\r\\n\\p{L}\\p{N}]?\\p{L}+|\\p{N}| ?[^\\s\\p{L}\\p{N}]+[\\r\\n]*|\\s*[\\r\\n]+|\\s+(?!\\S)|\\s+",
                };
                break;
```

The comment is the evidence: llama.cpp keeps the model's real regex as a comment and runs a **hand-rewritten
version** of it, because its regex engine lacks the `(?i:…)` inline-flag group. I confirmed the probed model's
`tokenizer.json` carries exactly the regex in that comment, and the `(?i:)` expansion is exhaustive for those
seven contractions, so for this model the two are equivalent — consistent with the mixed-case-contraction row of
the table in §3.4. That is a property of this particular rewrite, not of the mechanism.

**When the label is missing, llama.cpp says so in the loudest terms available:**

```cpp
            if (tokenizer_pre.empty()) {
                LLAMA_LOG_WARN("%s: missing pre-tokenizer type, using: 'default'\n", __func__);
                LLAMA_LOG_WARN("%s:                                             \n", __func__);
                LLAMA_LOG_WARN("%s: ************************************        \n", __func__);
                LLAMA_LOG_WARN("%s: GENERATION QUALITY WILL BE DEGRADED!        \n", __func__);
                LLAMA_LOG_WARN("%s: CONSIDER REGENERATING THE MODEL             \n", __func__);
                LLAMA_LOG_WARN("%s: ************************************        \n", __func__);
```

"CONSIDER REGENERATING THE MODEL" is the point: a GGUF file's tokenization behaviour depends on llama.cpp's
compiled-in tables, and a file can be *wrong* relative to the running version. §4.2 is the history of that.

Note also that the two BERT embedding models carry **no `tokenizer.ggml.pre` at all** (§1 table). For a WPM
vocabulary llama.cpp does not take the BPE path, so the warning above does not fire; the missing information
there is the normalizer flags of §3.2, not a pre-tokenizer label.

### 3.4 Do the two produce identical token counts? Mostly — with one large, quantified exception

I could not call a tokenizer inside Ollama 0.33.0: `/api/tokenize` and `/api/detokenize` both return
`404 page not found` (`MEASURED`), and `/api/embed` returns no token count. So I used the context-limit boundary
as an oracle. `POST /api/embed` with `"truncate": false` fails with
`{"error":"the input length exceeds the context length"}` once the runtime's own count exceeds the budget, so
binary-searching the largest accepted input measures the runtime's count in units of a known 1-token filler.

Calibration (`MEASURED`), `qwen3-embedding:0.6b`: the largest accepted input made of the 1-token filler `hello`
is 4,094 fillers, whose HF token count is 4,095; 4,095 fillers (HF count 4,096) is rejected. The budget is
therefore 4,095 tokens — Ollama's default `num_ctx` of 4,096, **not** the `qwen3.context_length` of 32,768 that
`/api/show` reports. For `all-minilm` the boundary sat at HF count 256 accepted / 257 rejected with a 1-token
filler, and 250 accepted / 258 rejected with an 8-token filler (`antidisestablishmentarianism`) — consistent with
a 256-token budget counted in tokens, and again not the `bert.context_length` of 512 that `/api/show` reports.
**A token budget cannot be read off `*.context_length`.**

First trap, unrelated to GGUF but fatal to a naive count: `all-MiniLM-L6-v2`'s `tokenizer.json` ships truncation
and padding *inside the file*:

```json
"truncation": {"max_length":128,"strategy":"LongestFirst","stride":0}
"padding": {"strategy":{"Fixed":128},"direction":"Right","pad_to_multiple_of":null,"pad_id":0,"pad_token":"[PAD]"}
```

`MEASURED`: `encode("hello world")` returns **128** ids — `['[CLS]','hello','world','[SEP]','[PAD]',…]` — until
padding and truncation are switched off, after which it returns 4. A count taken straight from a loaded
`tokenizer.json` can be the padding width rather than the token count. (`Qwen3-Embedding-0.6B`'s file has both
`null`.)

With padding and truncation off, prefixes padded to the measured boundary, `qwen3-embedding:0.6b`:

| prefix | HF `tokenizers` 0.23.2 | Ollama/llama.cpp runtime | ratio |
|---|---|---|---|
| `"café "` × 40, **NFC** | 42 | 42 | 1.00 |
| `"café "` × 40, **NFD** | 42 | **82** | **1.95** |
| `"café"`, NFC | 2 | 2 | 1.00 |
| `"café"`, NFD | 2 | **3** | **1.50** |
| `"Tiếng Việt "` × 20, NFC | 81 | 81 | 1.00 |
| `"Tiếng Việt "` × 20, **NFD** | 81 | **222** | **2.74** |
| `"Å "` × 30, **NFD** | 31 | **91** | **2.94** |
| `"IT'S it'S It'Re THEY'VE "` × 20 | 182 | 182 | 1.00 |
| `"東京都渋谷区 "` × 20 | 141 | 141 | 1.00 |
| `"👨‍👩‍👧‍👦 "` × 20 (ZWJ sequence) | 221 | 221 | 1.00 |
| `"a\tb\n\n\nc   d "` × 20 | 122 | 122 | 1.00 |
| `"1234567890 "` × 20 | 221 | 221 | 1.00 |

**They agree exactly on everything except Unicode normalisation form, where they diverge by up to 2.94×.** The
cause is one line of `tokenizer.json` that GGUF has no key for:

```json
"normalizer": {"type": "NFC"}
```

The HF tokenizer normalises to NFC first, so NFD and NFC input give identical counts (`MEASURED`: `encode` gives
the same two tokens `['ca','fÃ©']` for both forms of `café`). llama.cpp's BPE path applies no Unicode
normalisation, so each combining mark survives pre-tokenization and gets its own token or tokens.

That matters for this corpus specifically: §5 of the [NTFS research record](./ntfs-java-file-walking.md) measured
that NFC and NFD names coexist on NTFS and that NFD text reaches the corpus from macOS-created files and unpacked
archives — and extracted text inherits whatever form the source had.

`UNSOURCED`: I found no llama.cpp issue or GGUF spec statement acknowledging the missing Unicode normalizer for
BPE models. The divergence is measured only, and only for this model pair.

---

## 4. Are token counts stable across tokenizer library versions?

**No. There is a documented incident, it is sharp, it was shipped in a *patch* release, and the version boundary
it sits on falls inside `ai.djl.huggingface:tokenizers`' own history.** I reproduced it. This section is the
evidence, not the plausibility argument.

### 4.1 `ignore_merges`: HuggingFace `tokenizers` 0.19.0 → 0.19.1

The mechanism. [PR #1493 "Add more support for tiktoken based tokenizers"](https://github.com/huggingface/tokenizers/pull/1493)
(merged 2024-04-15, shipped in v0.19.0) added an `ignore_merges` flag to the BPE model —

> Adds a check before using merges, returing the token if it is part of the vocab

— implemented in
[commit `914576f7`](https://github.com/huggingface/tokenizers/commit/914576f7edcba7116b3d22fd0f2b8f727d652c3a) as:

```rust
if self.ignore_merges {
    if let Some(id) = self.vocab.get(sequence) {
        return Ok(vec![Token::new(*id, sequence.to_string().clone(), (0, 0))]);
    }
}
```

The commit's own tests state the size of the effect: `".:.:"` is **1 token** with the flag on and **2** with it
off; `"Ġbelirtilen"` is **1** versus **4**.

The version boundary. The flag was not wired into (de)serialization until
[PR #1504 "add serialization for `ignore_merges`"](https://github.com/huggingface/tokenizers/pull/1504), merged
two days later. v0.19.0 shipped before it; v0.19.1 after. The entire
[v0.19.1 release note](https://github.com/huggingface/tokenizers/releases/tag/v0.19.1) is that one line. At the
`v0.19.0` tag `tokenizers/src/models/bpe/serialization.rs` has no `ignore_merges` arm and its field match ends in
`_ => {}` — unknown JSON keys are silently discarded. So **≤0.19.0 silently ignores `"ignore_merges": true` in an
unchanged `tokenizer.json`, and 0.19.1 honours it.**

Real published files carry the flag. `MEASURED`:
[`NousResearch/Meta-Llama-3-8B/tokenizer.json`](https://huggingface.co/NousResearch/Meta-Llama-3-8B/blob/main/tokenizer.json)
— 9,085,698 bytes, 128,000 vocab entries, 280,147 merges — has `"ignore_merges": true`.

`MEASURED`, my own reproduction: one downloaded file, byte-for-byte identical, loaded with
`Tokenizer.from_file` in `python:3.11-slim` containers differing only in the pip-installed crate version.

| input | 0.19.0 | 0.19.1 |
|---|---|---|
| `model.ignore_merges` attribute | `False` | `True` |
| `"The quick brown fox jumps over the lazy dog and keeps running."` | 14 | 14 |
| `"Tiếng Việt là ngôn ngữ chính thức của Việt Nam ngày nay."` | **20** | **16** |
| `"Türkiye'nin başkenti Ankara'dır ve İstanbul en büyük şehridir."` | **18** | **16** |
| `"اللغة العربية هي إحدى أكثر اللغات انتشارا في العالم."` | 17 | 17 |
| `"a\n\nb"` | 4 | 4 |
| `" Việt"` | **4** — ids `[11655, 26298, 83]` | **2** — id `[101798]` |

(Counts include the `128000` BOS the file's post-processor adds.) Latin-script English is untouched; the shift is
concentrated in diacritic and non-Latin scripts, at up to 20% of the count on ordinary prose.

Independently, llama.cpp hit the identical bug from the other side and described it in the same terms —
[PR #7193 "fix : lookup word in vocab before doing BPE merges"](https://github.com/ggml-org/llama.cpp/pull/7193)
(merged 2024-05-11):

> For llama-3, I found there is an inconsistency between llama.cpp's tokenizer and Huggingface's tokenizers.
> Example: ` Việt` — llama.cpp: `11655 -> ' Vi'` / `26298 -> 'ệ'` / `83 -> 't'`; Huggingface's tokenizers with
> tokenizer.json from llama-3: `101798`. […] it seems that Huggingface's tokenizers will try to lookup a split
> word in the vocabulary first […] In llama.cpp, we always do the byte-level merge, hence the inconsistency.

Those are the same three token ids my 0.19.0 run produced and the same single id my 0.19.1 run produced, from two
different codebases.

**Where DJL sits on that boundary.** DJL does not pin the crate in `Cargo.toml` for most of its history — it is a
path dependency and the real version comes from `extensions/tokenizers/build.sh`, which clones the crate at the
`tokenizers_version` in `gradle.properties` (≤ v0.28.0) or `gradle/libs.versions.toml` (v0.29.0+); from v0.31.1 it
resolves from crates.io, and from v0.33.0 a `Cargo.lock` fixes it exactly.

| `ai.djl.huggingface:tokenizers` | bundled crate | | | |
|---|---|---|---|---|
| v0.20.0 | 0.13.1 | | v0.28.0 | **0.19.1** |
| v0.21.0, v0.22.1 | 0.13.2 | | v0.29.0 | 0.19.1 (inferred) |
| v0.23.0, v0.24.0 | 0.13.3 | | v0.30.0 | 0.20.0 |
| v0.25.0 | 0.14.1 | | v0.31.1 | 0.20.3 |
| v0.26.0, v0.27.0 | **0.15.0** | | v0.32.0 | 0.21.0 |
| | | | v0.33.0 | 0.21.1 |
| | | | v0.34.0 – v0.37.0 | 0.21.4 |

**DJL v0.27.0 → v0.28.0 crosses the boundary** (crate 0.15.0 → 0.19.1), skipping 0.16–0.18 entirely. DJL's
release note for v0.28.0 says only *"[tokenizers] Updates tokenizer to 0.19.1 by @frankfliu … #3143"*; no DJL
release note anywhere mentions a change in tokenization behaviour. The crate bump commits are
[0.15.0](https://github.com/deepjavalibrary/djl/commit/41f49ba925d377a9552dcad623f90212e51650d0),
[0.19.1](https://github.com/deepjavalibrary/djl/commit/228760918cf83acbd39cefe2d75b691e15ba0fa6),
[the crates.io switch](https://github.com/deepjavalibrary/djl/commit/bc31aff5a56fba9f91d834f6675f4128d08ffdf2),
and a [lockfile-only 0.21.1 → 0.21.4](https://github.com/deepjavalibrary/djl/commit/c0c0f82c2303b88c03cf921558633dce84ec758e)
whose release note says only "Fix tokenizer cpu and cuda build". Caveat: DJL's `v0.24.0` and `v0.29.0` tags both
point at the same commit, so those two rows are derived from commit-versus-release dates rather than read off the
tag.

### 4.2 Other version-dependent behaviour on an unchanged file

- **Offsets changed with no count change**, 0.20.0 → 0.20.1, via
  [PR #1640 "[`ignore_merges`] Fix offsets"](https://github.com/huggingface/tokenizers/pull/1640); the
  [v0.20.1 note](https://github.com/huggingface/tokenizers/releases/tag/v0.20.1) reads *"The most awaited
  `offset` issue with `Llama` is fixed 🥳"*. Relevant to anything that maps chunk boundaries back to character
  offsets, not to counting.
- **Loading forward is not guaranteed.** A `tokenizer.json` written by a newer crate can fail to load on an older
  one. `MEASURED`: `tokenizers` 0.15.2 refused
  [`Qwen/Qwen3-Embedding-0.6B/tokenizer.json`](https://huggingface.co/Qwen/Qwen3-Embedding-0.6B/blob/main/tokenizer.json)
  outright — `Exception: data did not match any variant of untagged enum ModelWrapper at line 757532 column 3`.
  The maintainer position on this is on record in
  [transformers #31139](https://github.com/huggingface/transformers/issues/31139): *"The issue lies with the
  `tokenizers` version, not `transformers`. And as such this is expected. … there are going to be differences with
  added tokens if there are any."*
- **`Metaspace` was a documented breaking change that did *not* change these counts.**
  [PR #1476 "Refactor metaspace"](https://github.com/huggingface/tokenizers/pull/1476) shipped in v0.19.0 and is
  listed in [the release note](https://github.com/huggingface/tokenizers/releases/tag/v0.19.0) as
  `🚨🚨 BREAKING CHANGE 🚨🚨: (add_prefix_space dropped everything is using prepend_scheme enum instead)`, with a
  14-versus-10-token example in the body. But on published files (`google-t5/t5-small`, `huggyllama/llama-7b`)
  across 0.15.2 / 0.19.0 / 0.19.1 / 0.21.4 / 0.22.1, token counts and strings came back byte-identical, because
  the v0.19.0 deserializer defaults an absent `prepend_scheme` to `Always` and an absent `split` to `true`,
  reproducing the old behaviour. **`ignore_merges` is the mechanism to cite; Metaspace is not.**
- Further breaking changes in the release notes, listed for completeness rather than because I demonstrated a
  count change: `#1513` "[BREAKING CHANGE] Ignore added_tokens … in the decoder" and its revert `#1569` (0.20.0
  line); *"There was a breaking change in 0.20.3 for tuple inputs of `encode_batch`!"*
  ([v0.20.3](https://github.com/huggingface/tokenizers/releases/tag/v0.20.3)); `#1617` "🚨 breaking: Fix training
  with special tokens" (0.21.2); v0.23.1's note that `add_tokens` now normalizes content at insertion so a
  re-saved `tokenizer.json` may differ in its `added_tokens` block. Older analogues in
  `bindings/python/CHANGELOG.md`: `[0.10.2] #656 Fix BPE continuing_subword_prefix`, whose body says *"This bug
  was introduced in `python-v0.8.1`"*, and `[0.10.3] #707 Fix stripping strings containing Unicode characters`.

### 4.3 No drift in the recent window, for these two models — `MEASURED`

Against the counter-hypothesis that this is a permanent hazard: I ran a 25-case battery (ASCII prose, NFC/NFD
pairs, mixed-case contractions, leading and trailing spaces, tabs and newline runs, CJK, an emoji ZWJ sequence,
digits, a URL, source code, mixed scripts, bare combining marks, zero-width characters, NBSP, control
characters, a 3× repeated long word, a 20-space run, Cyrillic caps, Turkish dotted/dotless i, an astral-plane
pair, typographic quotes and the empty string) against both embedding models' `tokenizer.json`, on
`tokenizers` **0.20.3, 0.21.1 and 0.23.2**:

**50 case/model combinations, 0 differences** — identical counts *and* identical id sequences on all three
versions. (0.13.3 and 0.19.1 have no wheel for the Python 3.13 on this machine, and 0.15.2 could not load the
Qwen file at all — see §4.2; the 0.19.0/0.19.1 pair was therefore run in Docker on Python 3.11 instead, §4.1.)

So the hazard is real and documented but not continuous: it fired once in the range examined, on a specific
`tokenizer.json` field, and the three most recent crate versions agree exactly on these two models.

### 4.4 The llama.cpp side: existing GGUF files became silently wrong

This is the same class of event with a stronger consequence, and it is what
[PR #6920 "llama : improve BPE pre-processing + LLaMA 3 and Deepseek support"](https://github.com/ggml-org/llama.cpp/pull/6920)
(ggerganov, merged 2024-04-29, commit `f4ab2a41`) exists to fix. From the body:

> The state so far has been that for all BPE-based models, `llama.cpp` applied a default pre-tokenization
> inherited back from GPT-2 […] there are cases where this fails […] This leads to poor generation quality because
> the model starts to work with out-of-distribution data when the pre-tokenization splits the input string in the
> wrong way

and the decisive sentence:

> Old GGUF models using BPE tokenizers, generated before this change, will fallback to the "default"
> pre-tokenization, which in almost all cases is wrong. A warning is printed in the output

Asked directly whether reconversion was needed
([comment](https://github.com/ggml-org/llama.cpp/pull/6920#issuecomment-2079867608)):

> All BPE-based models would require re-convert and pre-tokenization support implemented in order to function
> correctly. Old models would still work, though correctness is not guaranteed, because default pre-tokenization
> will be used. It seems to work in most of the cases, but there can be subtle differences that can sometimes
> degrade the quality

Measured by others in the thread on unchanged, pre-existing GGUF files: perplexity *"went from 8.9 -> 11.5, after
I reconverted the model it went to 8.3"*
([comment](https://github.com/ggml-org/llama.cpp/pull/6920#issuecomment-2083371625)), and on Llama-3-8B-Instruct
Q6_K *"wiki.test.raw from 11.9 down to 8.4. - Private English dataset from 14.9 down to 7.6"*
([comment](https://github.com/ggml-org/llama.cpp/pull/6920#issuecomment-2084596286)). Knock-on effects reached
other artefacts: *"You will need to regenerate importance matrices since they depend on how the input text was
tokenized"*
([comment](https://github.com/ggml-org/llama.cpp/pull/6920#issuecomment-2085374853)).

This PR is where `tokenizer.ggml.pre` and the `CONSIDER REGENERATING THE MODEL` warning quoted in §3.3 came from.

Follow-ups establish that this was not a one-off:

- [PR #8228 "llama : fix pre-tokenization of non-special added tokens"](https://github.com/ggml-org/llama.cpp/pull/8228)
  — *"This also fixes Gemma's tokenization of HTML tags, but **it requires re-conversion** because the added tokens
  were previously not using the correct types…"*
- [PR #7627 "Add tokenizer.ggml.pre to gguf-new-metadata.py"](https://github.com/ggml-org/llama.cpp/pull/7627) —
  *"It can be useful to fix older models without need to reconvert"*; a tool built specifically to patch stale
  files in place.
- Around 22 merged PRs correct individual pre-tokenizers (#6965 llama3 regex, #7063 Command-R, #7114 Qwen2,
  #7132 DBRX, #7375 SPM/phi-3, #7500 WPM/bert-bge+jina, #7530 BPE, #7713 Poro, #8135 Viking, #8579 Tekken,
  #8609 SmolLM, #8850 BLOOM, #12532 SuperBPE, #15030/#15045 hash fixes). The #7375/#7500/#7530 series states its
  goal as *"Modifications to make BPE/WPM/SPM tokenizer match AutoTokenizer"* — i.e. previously it did not.
  **#7500 is the WPM one, which is the path the two BERT embedding models here take.**
- A hash collision once stamped published Llama-3 GGUFs as `smaug-bpe`
  ([#7724](https://github.com/ggml-org/llama.cpp/issues/7724),
  [#7881](https://github.com/ggml-org/llama.cpp/issues/7881)); the files were repaired in place with
  `gguf-new-metadata.py --pre-tokenizer llama-bpe`.
- [Issue #23840](https://github.com/ggml-org/llama.cpp/issues/23840) states the count case outright: *"For some
  vocabularies the bug also causes a different **token count** (when HF's vocab has a directly-usable `▁word`
  token … that HF emits as a single id while `llama.cpp` splits to two)."*
- Earlier reports of llama.cpp diverging from the HF reference:
  [#3502](https://github.com/ggml-org/llama.cpp/issues/3502) (filed by ggerganov — *"llama.cpp BPE tokenization of
  wiki.test does not match the HF tokenization"* … *"The results are pretty close, but not exactly the same"*),
  [#6914](https://github.com/ggml-org/llama.cpp/issues/6914) (the report that triggered #6920),
  [#6104](https://github.com/ggml-org/llama.cpp/issues/6104),
  [#7049](https://github.com/ggml-org/llama.cpp/issues/7049) (added-token adjacency: HF `[1, 32001, 2188]` versus
  llama.cpp `[32001, 1838]`), [#11054](https://github.com/ggml-org/llama.cpp/issues/11054),
  [#21675](https://github.com/ggml-org/llama.cpp/issues/21675).

The differential test suite exists for exactly this. `tests/test-tokenizer-random.py` opens with
`# Test libllama tokenizer == AutoTokenizer.` and imports `from transformers import AutoTokenizer`; #6920 checked
in golden `.inp`/`.out` fixtures for 17 vocabularies generated from the HF reference; and #15032 added
`.github/workflows/pre-tokenizer-hashes.yml` because the hash list drifts.

Also from the converter, and the reason `tokenizer.ggml.pre` can go stale without the vocabulary changing
(`conversion/base.py`):

```python
logger.warning("**          - the pre-tokenization config has changed upstream")
...
raise NotImplementedError("BPE pre-tokenizer was not recognized - update get_vocab_base_pre()")
```

with the accompanying comment *"if you get an error here, you need to update the convert_hf_to_gguf_update.py
script or pull the latest version of the model from Huggingface — don't edit the hashes manually!"*, and
ggerganov in-thread: *"Probably need to pull the latest HF repo - the tokenizer config was updated recently there
which causes changes in the generated tokens for `chktxt`."*

### 4.5 What I could not find

- **No repo-wide announcement** from llama.cpp telling users to re-download their GGUF files. The reconversion
  instruction exists only in the #6920 body, ggerganov's comment on it, and the in-binary warning. Searched
  releases and tags for 2024-04-29 to 2024-05-10, README history in that window, and full-text PR/issue search
  for `re-convert`, `reconvert`, `re-download`, `requires re-conversion`.
- **No DJL release note** connecting any crate bump to a change in tokenization output; the v0.28.0 note is a
  bare dependency line.
- **No incident affecting the two embedding models probed here.** Every measured behaviour change above is on
  Llama-3's BPE `tokenizer.json`. `UNSOURCED` / untested: whether any historical crate version changes counts for
  `all-MiniLM-L6-v2` or `Qwen3-Embedding-0.6B` specifically — I could only test 0.20.3 / 0.21.1 / 0.23.2 for
  those (§4.3), because older wheels do not build on this machine's Python and 0.15.2 cannot load the Qwen file.
- **No `tokenizers` CHANGELOG for the Rust crate.** `bindings/python/CHANGELOG.md` covers the Python binding and
  stops being maintained after the 0.13 line; from then on the GitHub release notes are the only changelog, and
  they are one-line PR lists.

---

## 5. The two claims, answered

**Claim 1 — "the vocabulary cannot be read out of GGUF from Java, so an HF `tokenizer.json` must be supplied
separately."**

*Refuted as stated, in two parts.*

- The vocabulary **is** readable, and not even by parsing GGUF: `POST /api/show` with `"verbose": true` returns
  the complete token list and merge table, and `spring-ai-starter-model-ollama` 2.0.0 — already in `pom.xml` —
  models both the request flag and the untyped `model_info` map (§1, §2.1). For BPE it is a byte-faithful copy of
  the HF vocab and merges, in the same order (§3.1).
- **But "vocabulary" is not "tokenizer."** GGUF carries the vocab and merges plus a handful of scalars; the
  normalizer, the pre-tokenizer, the WordPiece continuation prefix, `unk_token`,
  `max_input_chars_per_word` and the post-processor are either absent or reduced to the opaque
  `tokenizer.ggml.pre` label that names a pre-tokenizer llama.cpp reimplements in C++ (§3.2, §3.3). The measured
  consequence is a token-count divergence of up to **2.94×** between an HF `tokenizer.json` and the running
  Ollama model, caused by `"normalizer": {"type": "NFC"}`, which GGUF has no key for (§3.4). The GGUF spec says
  this itself: implementations using the embedded vocabulary *"may have lower accuracy than the original
  tokenizer used for the model."*

**Claim 2 — "a version bump of `ai.djl.huggingface:tokenizers` could shift counts with an unchanged vocabulary
file."**

*Confirmed, with a specific incident, reproduced here.* HuggingFace `tokenizers` **0.19.0 → 0.19.1** — a patch
release whose entire note is one line — began honouring `"ignore_merges": true`, a field present in published
`tokenizer.json` files. On one unchanged file, measured on this machine, ordinary Vietnamese prose went from 20
tokens to 16 and Turkish from 18 to 16, while English was unchanged (§4.1). That boundary sits inside DJL's own
history at **v0.27.0 → v0.28.0** (crate 0.15.0 → 0.19.1), announced only as a dependency bump. Against that: the
three most recent crate versions produce byte-identical ids for both embedding models across a 25-case battery,
so the hazard is episodic rather than continuous (§4.3). llama.cpp had the same bug independently (#7193) and,
in #6920, a larger one that made **existing GGUF files silently wrong and requiring re-conversion** (§4.4).

---

## What I could not source

- **The GGUF `bert` vocabulary rewrite.** Ollama's phantom-space transformation is documented only by the
  three-line comment in `convert_bert.go`. No spec text, ADR, issue or design note says the `bert` vocabulary is
  meant to be a rewritten form, nor that `##`/`unk_token`/`max_input_chars_per_word` are dropped deliberately.
  Measured, not sourced.
- **The missing Unicode normalizer for BPE.** No llama.cpp issue and no spec statement acknowledges that a
  `tokenizer.json` `"normalizer": {"type": "NFC"}` has no GGUF equivalent. The 2.94× divergence is measured only,
  and only for `Qwen3-Embedding-0.6B`.
- **`tokenizer.ggml.pre` and `tokenizer.ggml.normalizer.*` are undocumented keys.** They exist in llama.cpp's
  `llama-arch.cpp` and in Ollama's converters, not in the
  [GGUF spec](https://github.com/ggml-org/ggml/blob/master/docs/gguf.md).
- **Whether any crate version changes counts for the two embedding models here.** Untestable in the range that
  matters: 0.13.3 and 0.19.1 have no wheel for this machine's Python 3.13, and 0.15.2 cannot load the Qwen file
  at all. Only 0.20.3 / 0.21.1 / 0.23.2 were compared for them (§4.3).
- **Ollama's runtime token count, directly.** Ollama 0.33.0 exposes no `/api/tokenize`; every runtime count here
  is inferred from the `truncate: false` context-limit boundary (§3.4). The method is sound for counting but
  cannot show *which* tokens the runtime chose.
- **Why the runtime budget is 256 for `all-minilm` and 4,096 for `qwen3-embedding:0.6b`** when `/api/show`
  reports `bert.context_length` 512 and `qwen3.context_length` 32,768. Measured; not traced to a source.
- **No repo-wide llama.cpp announcement** of the #6920 re-conversion requirement, and **no DJL release note**
  connecting a crate bump to changed tokenization (§4.5).
- **No changelog for the Rust `tokenizers` crate.** `bindings/python/CHANGELOG.md` covers the Python binding only
  and lapses after the 0.13 line; the GitHub release notes are one-line PR lists.
- **Nothing about a chosen model.** [ADR-034](https://github.com/algernon28/vespera/blob/main/docs/adr/README.md)
  leaves the embedding model to a bake-off and `Profile.embeddingModel` ships unset, so every number here is from
  three models pulled to exercise the mechanism — one WordPiece BERT, one nomic-BERT, one Qwen BPE — not from the
  model this project will use.

## Reproducing the measurements

```bash
docker run -d --name vespera-tokprobe -p 11435:11434 ollama/ollama:latest
docker exec vespera-tokprobe ollama pull all-minilm
docker exec vespera-tokprobe ollama pull nomic-embed-text
docker exec vespera-tokprobe ollama pull qwen3-embedding:0.6b

# elided vs. full vocabulary (30 KB vs 627 KB; 27 KB vs 4.1 MB)
curl -s http://localhost:11435/api/show -d '{"model":"all-minilm"}'
curl -s http://localhost:11435/api/show -d '{"model":"all-minilm","verbose":true}'
curl -s http://localhost:11435/api/show -d '{"model":"qwen3-embedding:0.6b","verbose":true}'

# the runtime-count oracle: binary-search the largest accepted input
curl -s http://localhost:11435/api/embed \
  -d '{"model":"all-minilm","input":"<text>","truncate":false}'
# -> {"error":"the input length exceeds the context length"} past the budget

# the crate boundary of section 4.1
docker run --rm -v "$PWD:/w" python:3.11-slim \
  sh -c 'pip install -q tokenizers==0.19.0 && python /w/probe.py'
docker run --rm -v "$PWD:/w" python:3.11-slim \
  sh -c 'pip install -q tokenizers==0.19.1 && python /w/probe.py'
```

Reference `tokenizer.json` files: `https://huggingface.co/<repo>/resolve/main/tokenizer.json` for
`sentence-transformers/all-MiniLM-L6-v2`, `Qwen/Qwen3-Embedding-0.6B` and `NousResearch/Meta-Llama-3-8B`. Every
`tokenizer.json` count in this record was taken with `no_padding()` and `no_truncation()` applied first — see
§3.4 for why that matters.
