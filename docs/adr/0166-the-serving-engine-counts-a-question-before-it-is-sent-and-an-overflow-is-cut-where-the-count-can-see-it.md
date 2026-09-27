# ADR-166 — The serving engine counts a question before it is sent, and an overflow is cut where the count can see it

- **Date**: 2026-09-27
- **Status**: accepted
- **Amends**: [ADR-108](0108-6b-sends-one-exemplar-first-call-per-cluster-and-verifies-every-response.md) — its Context states that overflow on the generation endpoints is shifted rather than refused, and that `prompt_eval_count` at or above the window is how a shifted prompt shows; both are false on the engine it names, measured below. Its "one call per cluster" becomes one *answer* per cluster, preceded by counting calls; its word budget stops being the guard against overflow and becomes the first proposal of what to send; its ceiling moves from the window to the window less one; and `num_keep` joins the options sent and so the generator identity. Everything else it decided stands: exemplar-first, closest to the seed first, a cluster too large sends what fits and says so, every answer is checked before it is believed.
- **Amends**: [ADR-121](0121-a-window-with-no-room-for-documents-is-refused-and-no-call-is-ever-made-with-none.md) — only in what `TOKENS_PER_WORD` is for. Its floor, its refusal of an empty fill and its unsendable branch are untouched, and so is every number in its table.
- **Amends**: [ADR-111](0111-a-cluster-fault-is-a-row-in-synthesis-and-a-re-run-under-the-same-id-repairs-rather-than-regenerates.md) — its stop at five consecutive faults no longer caps a cluster faulted because no document of it fits by the engine's count (§4a): such a fault neither adds to the streak nor clears it, so any number of them can be recorded in one invocation, and a repair pass pays their counting calls again on every invocation under the same run id. Its sentence *"The cost is bounded by construction: the breaker caps how many clusters can fault before the step stops"* is therefore no longer true of every fault, and carries an amendment note saying so. Everything else it decided stands: a fault is a row against the cluster, a re-run repairs rather than regenerates, and every other fault still counts toward the five.
- **Extends**: [ADR-091](0091-there-is-no-tokenizer-the-runtime-counts-tokens-and-the-embedder-identity-is-what-ollama-reports.md) — its rule that the runtime is the only component that counts tokens, reached for the embedding endpoint through `truncate: false`, now reaches the generation endpoint through a counting call and `num_keep: -1`. Nothing it decided moves.
- **Rests on**: [ADR-110](0110-pipeline-hands-synthesis-its-inputs-so-the-module-rule-gains-no-second-exception.md) (`synthesis` reaches the engine through Spring AI's `ChatModel` and nothing of this project's), [ADR-111](0111-a-cluster-fault-is-a-row-in-synthesis-and-a-re-run-under-the-same-id-repairs-rather-than-regenerates.md) (a turned-down cluster is a row, and the run carries on), [ADR-114](0114-the-generation-model-is-named-in-application-configuration-with-a-code-default-and-is-not-a-gate.md) (the generation model changes without a new record), [ADR-123](0123-an-answer-carrying-nothing-is-a-schema-violation-and-only-guaranteed-response-fields-are-dereferenced.md) (an absent count reads as `0`, and `0` faults nothing), [ADR-144](0144-a-chunk-the-runtime-refuses-as-too-long-is-split-until-every-piece-fits.md) (a refusal on length is told apart from every other refusal by its text).
- **Found by**: [#332](https://github.com/algernon28/vespera/issues/332) and its comment of 2026-09-27.
- **Corrects**: [`docs/research/ollama-generation-surface.md`](../research/ollama-generation-surface.md) §2, which now carries the measurements below beside the claims they overturn.

## Context

### Two overflows, one loud and one silent

**The loud one.** Under a GGUF chat model pulled from `hf.co` with no Ollama template, on Ollama 0.33.2 and the shipped window of 8192, stage 6b's first call — the largest cluster of the GesPOS arrangement — came back HTTP 400: `request (8280 tokens) exceeds the available context size (8192 tokens)`, `exceed_context_size_error`. Spring AI raised it as `NonTransientAiException` out of `ClusterSynthesis.docFor`. Nothing caught it, so the step failed, its transaction rolled back every fault row recorded in it, and the invocation exited 1. The same cluster would have ended every re-run the same way, and no cluster after it would ever have been written.

**The silent one, which is the worse of the two.** Under the shipped `qwen3:8b` nothing is refused. The GesPOS run of 2026-09-27 sent its largest cluster — *Scontrini*, twelve documents, ten of them `.OK` records — four times, and Ollama's log recorded each call being cut:

```
13:31:29Z truncating input prompt limit=4098 prompt=12333 keep=4 new=4098
```

The first three answers were turned down under ADR-162. The fourth was accepted, and that cluster's page was written from about a third of what it was sent, with nothing anywhere saying so. ADR-108's check did not fire, because it faults a count *at or above the window*, and the count came back at 4,098.

### Measured: why the check never fired

Against Ollama 0.33.2 as `compose.yaml` pins it (GPU, [ADR-165](0165-ollama-is-given-an-nvidia-gpu-by-an-override-file-and-compose-yaml-alone-asks-for-none.md)), `qwen3:8b`, `/api/chat`, `num_ctx` 8192, from a probe outside the repository:

**Ollama serves a chat model down one of two paths, and they overflow differently.** The header of `llm/llama_server.go` at `v0.33.2` says so: *"Models with explicit Ollama renderers/parsers, Harmony handling, MLX, or an enabled Go TEMPLATE layer still render prompts in Go and call /completion. Other GGUF chat models use llama-server's chat_template handling through /v1/chat/completions."* A library model such as `qwen3:8b` takes the first path, where `completionPromptForRequest` shifts an overlong prompt silently. A GGUF pulled from `hf.co` with no Ollama template takes the second, where llama-server itself refuses it — the refusal above. So which overflow a model gets is a property of how it was packaged, and a model swap under ADR-114 can change it with nothing in this code changing.

**A shifted prompt is counted at about half the window, never at it.** The shift keeps `num_keep` tokens from the front and the tail, cut to

```go
func contextShiftPromptLimit(numCtx, numKeep int) int {
	return numCtx - max((numCtx-numKeep)/2, 1)
}
```

which at `num_ctx` 8192 and the default `num_keep` of 4 is **4,098** — the number in every log line above. Sent 13,034 words of the GesPOS leading chunks, the response read `prompt_eval_count: 4098`, `done_reason: "stop"`, and a fluent answer. ADR-108's premise that a shifted prompt reports a count *at the window* was false on the engine it cited: **its ceiling could not have fired on a shift, on any call, ever.**

**#317's figure was this cut.** `LargestClusterRunsOutOfRoomTest` records the count the largest cluster of the first end-to-end run reported as *"well inside the window"*: 4,098. The shift keeps the tail and the first four tokens, and that run's instruction sat *before* the documents — so the instruction was what the cut removed, which is exactly the "model lost the instruction under some 4,000 tokens of record rows" ADR-161 measured, and why moving it to the end fixed it. ADR-161's placement stands on its own measurement and is not reopened. Whether #318's stray brace came of the same cut is not established; the logs of that run no longer exist.

**`num_keep: -1` turns the silent cut into one the count shows.** With `num_keep` negative, `completionPromptForRequest` keeps the whole prompt up to `num_ctx − 1`, so `contextShiftPromptLimit` returns `num_ctx − 1`: the head is kept, the tail is dropped, and the count reads **8,191** in a window of 8192 — every overlong call that reported a count at all, five of five, with and without the answer schema, and under `qwen3:0.6b` too, the model `ClusterSynthesisIT` pulls, which Ollama's default cut to 4,098. A prompt that fits is counted the same with and without it (241 and 241). And unlike `shift` and `truncate`, which Spring AI 2.0.0 strips (research §1), `num_keep` is an ordinary member of `OllamaChatOptions` and reaches the engine.

**It is not enough on the answering call alone.** A prompt kept to `num_ctx − 1` leaves the answer no room, and generation then shifts the context as it writes: of four such calls with `num_predict` 1024, two came back `done: true, done_reason: "length", prompt_eval_count: 8191`, and two came back `done: false` with **no counts at all** — which Spring AI turns into `0`, and `0` passes the ceiling by ADR-123's rule. So an overlong question must never be *answered*; it has to be found before the answering call is made.

**Counting a question costs one evaluation, not two.** `prompt_eval_count` is not reduced by the engine's prompt cache — identical calls repeated were counted identically (4,098 and 4,098; 8,191 and 8,191; 241 and 241) — while `prompt_eval_duration` is: the same prompt sent a second time evaluated in 0.03 s against 3.69 s the first time. So a call that asks for one token and reads the count, followed by the same question asked in earnest, pays for evaluating the question once.

**There is no direct signal.** Nothing in the response says a prompt was cut: not `done_reason`, not a field Spring AI drops, not `prompt_eval_cached_count` (absent from every response above). The cut is in the server's log and nowhere else. The only levers that would refuse instead, `shift: false` and `truncate: false`, cannot be sent through Spring AI 2.0.0.

### Measured: why no ratio of ours can replace the count

`qwen3:8b`'s own count of the 84 leading chunks in the GesPOS working directory, one call each, less the chat template's 17 tokens (chunks of 20 words or more):

| Per | Least | Median | 90th percentile | Most |
|---|---|---|---|---|
| whitespace word | 1.32 | 2.71 | 7.18 | 14.0 |
| UTF-8 byte | 0.22 | 0.38 | 0.72 | 0.93 |

The issue's own measurements add 19.6 tokens a word for a test log, and the model that refused put 1,857 words of the refused cluster at 8,280 tokens — 4.3 a word, where `qwen3:8b` would have counted fewer. The full archive holds 6,319 `.xml`, 858 `.html` and 718 `.json` files beside the prose. So:

- **`TOKENS_PER_WORD = 2.0` was never pessimistic.** Its javadoc says it is *"deliberately more than one word costs"*; for this model it is less than the median chunk costs, and a seventh of what a record costs.
- **No per-word ratio is an upper bound**, because a word is as long as the text makes it.
- **One token per byte is**, for every byte-level or byte-fallback tokenizer, since no token covers less than one byte — and it would pack prose at a median of 0.38 of what it spends, wasting some sixty per cent of every window.
- **A ratio per content kind or per model** is the tokenizer reconstruction ADR-091 refused, one number at a time: a table that is right for the models someone measured and silently wrong for the next one ADR-114 lets an operator name.
- **A larger window does not help.** The packer fills a larger window at the same ratio, so a record-heavy cluster overflows it by the same factor.

### Measured: the counting it takes

The decision below, run against `qwen3:8b` with the shipped prompt over GesPOS chunks:

| Cluster | Proposed at 2.0 a word | First count | Counting calls | Sent | Answering call |
|---|---|---|---|---|---|
| 14 record-like chunks | 3,430 words | 8,191 (cut) | 5, 8.0 s | 2 documents, 1,024 words, 4,981 tokens | `stop`; evaluation 0.03 s |
| 6 prose chunks | 3,072 words | 7,201 (33 past the room) | 4, 4.3 s | 5 documents, 2,560 words, 5,916 tokens | `stop`; evaluation 0.02 s |

## Decision

**The serving engine counts every question before it is answered, and what is sent is the longest leading run of the proposed documents that the engine counts inside the window less the reply allowance. Every call sends `num_keep: -1`, so a question the engine cuts is counted at the window less one, and the answer check's ceiling sits there.**

### 1. A counting call precedes every answering call

A **counting call** is the answering call's own request — the same model, the same prompt text, the same `num_ctx`, `num_keep`, `format` and `think` — with `num_predict: 1`. Its one token of answer is never read; what is read is how long the engine counted the question, or its refusal. It goes through the same `ChatModel` as every other call ([ADR-110](0110-pipeline-hands-synthesis-its-inputs-so-the-module-rule-gains-no-second-exception.md)); no client is written.

Identical to the answering call on purpose: the engine's prompt cache serves the answering call's evaluation from the counting call's, so a cluster that fits costs one evaluation, as it did before. A counting call built with a different `format` was measured to miss the cache (3.69 s against 0.03 s).

**A question fits when its count is no more than `window − REPLY_ALLOWANCE`**, 7,168 in the shipped window — **the room**, as the rest of this record calls it. That is not a new number: `REPLY_ALLOWANCE` is sent as `num_predict` and is already reserved out of the window (ADR-108), so a question longer than the window less the allowance is one whose answer cannot have its allowance — and at 8,191 is one the engine cut. **A refusal on length does not fit**, told apart from every other refusal by its text as ADR-144 tells the embedder's apart: `exceeds the available context size` from llama-server, `the prompt is longer than the context length` from Ollama's own check. Any other refusal is about the model rather than the question and reaches the step unchanged. **An absent count fits**: it arrives as `0` (ADR-123), and a count nobody reported says nothing about length, so the proposal stands as it was estimated — which is exactly what happened before this record, and no worse.

### 2. What is sent is found by halving, from the top of the proposal

The proposal is `whatFitsIn` exactly as ADR-108 and ADR-121 define it — closest to the seed first, a document larger than the estimated room passed over, the fill stopping at the first document that does not fit what is left. Then:

1. **Count the whole proposal.** If it fits, it is what is sent.
2. **Otherwise, halve for the longest leading run that fits**: between a run known to fit (at first, none) and one known not to (at first, the whole proposal), count the run halfway, and keep the half it says. The documents dropped are always the furthest from the seed.
3. **If even the first document alone does not fit**, it is passed over — it can never be sent in this window with the answer's allowance left over, which is ADR-108's own rule for a document too large for an empty call, decided now by the engine's count rather than the estimate — and a new proposal is filled from the documents left, back to step 1.
4. **If every document has been passed over**, the cluster faults (§4).

Halving rather than scaling by the count, because a count at the ceiling says only that the question was *at least* that long: scaling by it took six counts on the record-like cluster above where halving took five, and halving's bound does not depend on how far past the window the question was. **A proposal of *n* documents costs at most `1 + ⌈log₂ n⌉` counting calls** until a document is passed over — five for the fourteen above.

The ordinals are minted over what is sent, so ADR-109's range check, ADR-133's record of what the call carried and the page's "written from the *k* highest-scoring of *n*" read it unchanged.

### 3. `num_keep: -1` on every call, and the ceiling is the window less one

`ClusterSynthesis.optionsFor` sends `num_keep: -1`, and the counting call inherits it. Its only effect is on a question too long for the window: the engine keeps its head up to `num_ctx − 1` rather than its first four tokens and its tail, so the count reads `window − 1`. A question that fits is counted and answered exactly as without it.

**The answer check's ceiling moves from `count ≥ window` to `count ≥ window − 1`.** `window − 1` is Ollama's own `fullPromptLimit`: the most a question can be counted at whole, and the count every cut question now reports. A whole question of exactly `window − 1` tokens leaves no room for a single token of answer, so nothing passing this check could have been believed anyway. Behind the counting call this check should never fire; it stays as the backstop for a question the engine counted one way and answered another.

`num_keep` is a member of Ollama's `options` object, so it is one of "the options actually sent" ADR-108 composed the generator identity from and ADR-159 bounded: **it joins `GenerationConfigConsumed`** as `numKeep`, as `temperature` and `seed` are recorded to join it in the ticket that first sends them. The counting call's `num_predict: 1` does not: it is not the options the answer was written under. The new implementation version mints a new 6b run regardless, so the 2026-09-27 deliverable's *Scontrini* page, written from a cut question, is written again rather than kept by a repair pass.

### 4. Which fault, when a cluster still cannot be sent

**`PROMPT_EVALUATION_CEILING`, and no fifth kind.** It already names the one thing all three cases are — the question did not reach the model whole, or could not:

- **every document passed over (§2.4)**, the detail saying no document of the cluster fits the room by the serving engine's own count, and carrying what the engine said of the last question counted — its count, or its refusal verbatim, which is where the refusal's `8280` is;
- **the answering call refused on length** after its question was counted as fitting — the debugger's handling of #332, kept as a backstop, with the engine's message in the detail;
- **the answering call counted at `window − 1` or above** (§3).

A fifth kind would change no behaviour and no remedy — the cluster keeps its hole, the operator's remedy is a larger window or another model, either of which mints a new run — which is the ground [ADR-121](0121-a-window-with-no-room-for-documents-is-refused-and-no-call-is-ever-made-with-none.md) and [ADR-123](0123-an-answer-carrying-nothing-is-a-schema-violation-and-only-guaranteed-response-fields-are-dereferenced.md) refused one on three times, and it holds a fourth.

**These are cluster faults, not ADR-121's unsendable cluster.** ADR-121 draws its line at a call never made. Here calls were made and came back — a count is a response, and so is a refusal — and what came back did not survive checking. `CONTEXT.md`'s entry is widened to say that a count or refusal of the question is part of what came back.

**The first case repeats on every re-run under the same run id, and that is correct.** It is a fact of the model, the window and the documents, and all three are in the run's own identity; the debugger's handling faulted the *whole proposal* on every re-run, where this faults only a cluster none of whose documents fits at all.

### 4a. A cluster the engine finds no room for neither adds to the streak of five nor clears it

ADR-111 stops the step at five turned-down answers in a row, because five in a row read as the model, the budget or the answer's shape being wrong for the archive, and every call after the fifth buys another copy of the same wrong answer. **The first case of §4 is not evidence of that, and does not count.** No answer was asked for: what faulted it is that every document it holds is counted past the room by the engine, which is a fact about those documents under this window — the same fact ADR-121's unsendable cluster is, established by the engine rather than the estimate. `GenerationTasklet` already walks past that cluster without touching the streak, in either direction; this one is walked past the same way, its fault row recorded.

**Counting it would starve the repair pass.** The fault is deterministic — the same model, window and documents give the same counts — and a repair pass re-attempts faulted clusters in arrangement order (ADR-111). Five such clusters in a row would therefore stop the step at the same place on every pass under the run's id, and no cluster after them would ever be written, which is #332's crash at the same cluster on every re-run, reached by another route.

**Nor does it clear the count.** It is no evidence that the model and the shape are right either: nothing was answered. An archive whose answers are all wrong, with such clusters scattered between them, must still stop.

**What is lost, and what it costs instead.** Stopping is the cheaper outcome, and §4a gives it up for this fault. A window set far too small for the archive's documents is no longer stopped after five clusters: every cluster faults, none is asked for an answer, and each pays the counting that found no room — for a cluster where nothing fits, every document proposed is passed over in turn, so the full bound of §2 applies, up to `(p + 1) × (1 + ⌈log₂ n⌉)` counting calls with `p` every document ever proposed for it and `n` the largest proposal. A repair pass under the same run id pays that again on every invocation, because the fault is re-attempted like any other (ADR-111) and comes back the same. What bounds it is the cluster count and the documents the estimate lets into a proposal, not the breaker; what is bought is that the clusters after such a run of them are written, and each faulted one keeps a row naming the window. ADR-121's floor still refuses the window that leaves room for nothing at all.

The other two cases of §4 — a refusal on length of the answering call, and an answer counted at the ceiling — do count, as every answering-call fault does: they happen only after the engine counted the question as fitting, so they are an engine disagreeing with itself, which is what a streak is for.

**Pinned by** `GenerationBreakerInvocationTest.doesNotStopForFiveClustersTheEngineFindsNoRoomFor` and `GenerationBreakerInvocationTest.doesNotClearTheCountForAClusterTheEngineFindsNoRoomFor`. It needs `GenerationTasklet` to tell this fault from the other two without reading its detail, so `ClusterFaultException` (or what it carries) has to say that no answer was asked for; how is the implementation's to choose.

### 5. `TOKENS_PER_WORD` stays 2.0, and becomes a proposal

It no longer guards against overflow — the engine's count does — so it only decides what is proposed first, and through `roomForDocumentsIn` where ADR-121's floor sits. **Kept at 2.0 rather than raised.** Raised to 2.5 it would spare most prose clusters a round of counting, and would send about the same documents in the end (the prose row above settled on 2,560 words; 2.5 proposes 2,764); it would also move ADR-121's smallest window from 1282 to 1283 and ADR-161's arithmetic behind `WORD_LIMIT`, for no change in what is sent. **What is owed is its javadoc**, which calls it pessimistic and says an overrun is silent: neither is true now.

**No per-model and no per-content ratio**, for the reasons measured above. The count is the engine's own, for the model it is serving, so a model swap is measured by the model it swapped to.

## Consequences

**A cluster carrying records is written, from as many of its documents as the engine can read, under the shipped model and under any other.** Neither cut nor refused: the question that is answered is one the engine has just counted as whole. A model whose runner refuses, and one whose runner shifts, are handled by the same path.

**The cost is counting calls, paid mostly by clusters that would have overflowed.** A cluster that fits pays one extra round trip and one token, its evaluation reused. One that overflows pays up to `1 + ⌈log₂ n⌉` evaluations of a question up to a window long — 8.0 s and 4.3 s in the two rows above, beside answers of 5 to 7 s on a GPU; more on a processor. **That bound is per proposal.** Each document passed over (§2.3) starts a new proposal with its own bound, so the worst case is about `(p + 1) × (1 + ⌈log₂ n⌉)` counting calls for `p` documents passed over — every one of them a document the engine counts past the room by itself, which the archive has to hold several of in one cluster, in score order, before the multiplier is felt. Bounded, and spent only where the alternative was a page written from part of what it names.

**ADR-108's "one call per cluster" is one answer per cluster.** The rollup ADR-108 refused cost a *generation* per document; a counting call generates one token nobody reads. ADR-109's "not regenerated" is not touched: nothing is asked twice because an answer failed a check — no answer exists until the question has been counted.

**The checks keep their order and their four kinds**, with the ceiling one token lower. The absent-count direction of ADR-123 holds for the count as for the ceiling.

**An engine that reports no counts gets today's behaviour, and a detection that rests on `num_keep` rests on Ollama's source.** Both are pinned where a double cannot pin them: `ClusterSynthesisIT` puts an overlong question to a real engine under `countingOptionsFor` and claims the count comes back at `window − 1`, and under Ollama's defaults at `contextShiftPromptLimit`. Under `countingOptionsFor` rather than `optionsFor` on purpose: the two differ only in `num_predict`, and an overlong question asked for 1,024 tokens came back with no counts at all in two calls of four (Context), so asking for one token is what makes the claim one a real engine answers every time. If an image bump changes either, that test is what says so.

**`docs/research/ollama-generation-surface.md` §2 was right about the mechanism and wrong about its consequence.** It carries the correction beside the sentence it corrects rather than a rewrite of it, as a research record should.

**Owed in `src/main`** (none of it written here): `ClusterSynthesis` counts before it answers (§1–§2), sends `num_keep: -1` from `optionsFor` and exposes `countingOptionsFor` beside it, checks the ceiling at `window − 1`, and faults as §4 says; the javadocs of `TOKENS_PER_WORD`, `INSTRUCTION_RESERVE`, `CONTEXT_WINDOW`, `whatFitsIn`, `checkPromptEvaluationCeiling`, `docFor` and `ClusterFaultKind.PROMPT_EVALUATION_CEILING` are corrected to it; `StageRuns.GenerationConfigConsumed` gains `numKeep`. And, added by the review of this record (§4a): a cluster faulted because no document fits by the engine's count is told apart from an answering-call fault without parsing its detail, and `GenerationTasklet` neither adds it to the consecutive-fault streak nor clears the streak for it.
