# ADR-123 — An answer carrying nothing is a schema violation, and only guaranteed response fields are dereferenced

- **Date**: 2026-09-19
- **Status**: accepted
- **Amends**: [ADR-108](0108-6b-sends-one-exemplar-first-call-per-cluster-and-verifies-every-response.md) — its four checks stand in the order it states them, and this record inserts a fifth point between the first and the second — after the prompt-evaluation ceiling and before the answer running out of room — where the response is first required to carry a generation at all. The kinds ADR-108 closed stay closed at four; nothing it decided about what is sent, or about the order the checks run in, moves.
- **Rests on**: [ADR-109](0109-a-citation-is-an-exemplar-ordinal-minted-for-one-call-and-the-check-is-that-it-is-in-range.md) (two failures under one kind are told apart by their detail), [ADR-111](0111-a-cluster-fault-is-a-row-in-synthesis-and-a-re-run-under-the-same-id-repairs-rather-than-regenerates.md) (a rejected answer faults its cluster and the run keeps going), [ADR-110](0110-pipeline-hands-synthesis-its-inputs-so-the-module-rule-gains-no-second-exception.md) (`synthesis` reasons about plain values, never about which serving engine answered), [ADR-121](0121-a-window-with-no-room-for-documents-is-refused-and-no-call-is-ever-made-with-none.md) (the line between a call never made and a call that came back, and the third refusal of a fifth fault kind).
- **Found by a Qodana run on 2026-09-19**, not by a failing test: `DataFlowIssue` at `ClusterSynthesis.java:183` and `:199`, `ConstantValue` at `:169`. Nothing in the tree reaches these states. Charted as [#222](https://github.com/algernon28/vespera/issues/222), under the stage 6a/6b map [#175](https://github.com/algernon28/vespera/issues/175).

## Context

### Three of the four checks dereference a response before anything established there is one

Spring AI's `ChatResponse.getResult()` hands back the first generation, or `null` when the response carries none; `getOutput().getText()` is nullable in its own right. So `checkAnswerDidNotRunOutOfRoom` reading `response.getResult().getMetadata().getFinishReason()`, and `parseAnswer` reading `response.getResult().getOutput().getText()`, each throw a `NullPointerException` out of `docFor` when a call comes back carrying nothing.

### Why that costs more than the cluster it happens to

ADR-111 settles that a rejected answer faults its cluster and the run keeps going: `ClusterFaultException`, caught by `GenerationTasklet`, recorded as a `cluster_fault` row, and on to the next cluster. An `NPE` is not a `ClusterFaultException`, so it escapes the tasklet as a step failure — and a step failure rolls back the step's transaction, taking with it every `cluster_fault` row written earlier in that step. The rows exist to preserve exactly that evidence. One empty response therefore destroys the record of every fault before it, which is the same shape [#216](https://github.com/algernon28/vespera/issues/216) documents, reached through a different hole.

### Which of Ollama's response fields this code may assume

Measured against Spring AI 2.0.0's sources, and recorded here so the guards and the bare dereferences stop disagreeing:

| Read | Nullable? | Why |
|---|---|---|
| `ChatResponse.getMetadata()` | no | `ChatResponse` initialises it, never null |
| `ChatResponseMetadata.getUsage()` | no | initialised to `EmptyUsage` |
| `Usage.getPromptTokens()` | **no** | declared `Integer` with no `@Nullable` in an `@NullMarked` package; `DefaultUsage` stores `promptTokens != null ? promptTokens : 0`; `EmptyUsage` returns `0`; `OllamaChatModel.getDefaultUsage` builds from `Optional.ofNullable(response.promptEvalCount()).orElse(0)`. `OpenAiChatModel`, also on this tree, falls back to `EmptyUsage` and otherwise builds a `DefaultUsage`, so no model here can hand back a null count |
| `Usage.getCompletionTokens()` | no | same four reasons |
| `ChatResponse.getResult()` | **yes** | the first generation, or `null` when the response carries none |
| `Generation.getMetadata()` | no | `ChatGenerationMetadata.NULL` when nothing is known |
| `ChatGenerationMetadata.getFinishReason()` | yes, and already handled | compared against a constant with `equals` on the constant's side |
| `AssistantMessage.getText()` | **yes** | nullable in its own right |

### Measured: a null text escapes the step today even though `parseAnswer` looks guarded

Jackson 3's `ObjectMapper.readValue` asserts its argument before parsing and throws `IllegalArgumentException: argument "content" is null`, which is **not** a `JacksonException` and so is not caught by `parseAnswer`'s existing `catch`. An empty string does reach that catch, and is recorded as a `SCHEMA_VIOLATION` whose detail describes end-of-input at line 1 column 0 — the right kind, with a detail describing a parser rather than an answer.

## Decision

### An answer carrying nothing is a `SCHEMA_VIOLATION`, and `ClusterFaultKind` stays at four values

`SCHEMA_VIOLATION` already names *what came back could not be read into the shape the call imposed*, and nothing at all is the limiting case of that rather than a different case. A fifth kind would change no behaviour and no remedy: the cluster keeps its hole, and a repair pass asks again, identically. ADR-121 refused a fifth kind on that ground, #216 refuses one again for the title case, and this is the third refusal on the same ground.

It is also not an `IllegalStateException`. `ClusterFaultKind`'s four values are things that happen to **a call that came back**, and ADR-121 drew that line in those terms. A response carrying nothing is a call that came back, and nothing `GenerationTasklet` could check first would prevent it.

### What the checks become

ADR-108's order stands — ceiling, room, schema, citations, first failure winning — and the two empty cases land at different points in it, each at the earliest place its own evidence is complete.

1. `checkPromptEvaluationCeiling` — **unchanged, and still first.** It reads only response-level metadata, every field of which is guaranteed non-null by the table above. A shifted prompt explains everything downstream of it, so a call that both overran and came back empty still records the ceiling.
2. **New: the response carries no generation** → `ClusterFaultException(SCHEMA_VIOLATION, "the call came back carrying no answer at all")`. It sits here because this is the first point a generation is needed. The `Generation` is established once and handed to the two checks below rather than fetched again. **That it sits after the ceiling and not before it is a decision, not an accident**, so a call that both overran and came back empty is pinned by a test of its own rather than resting on the order the code happens to read in.
3. `checkAnswerDidNotRunOutOfRoom` — **unchanged**, now over a generation established to exist.
4. `parseAnswer` — **before** `JSON_MAPPER.readValue`, a text that is `null` or blank → `ClusterFaultException(SCHEMA_VIOLATION, "the answer came back empty")`. Otherwise Jackson exactly as today, keeping its existing detail. The guard goes in front of `readValue` rather than being left to the catch around it, because of the measurement above: a null text does not reach that catch.
5. `checkCitations` — unchanged.

Three distinct `detail` strings under one kind, which is ADR-109's shape applied again — its two citation failures are told apart the same way.

### The guard at `:169` goes

`promptTokens != null` is removed: it is dead four times over, for the four reasons the table gives.

**The signal is not unanimous, and the removal is chosen in spite of that.** Spring AI's own `Usage.getTotalTokens()` default method does `promptTokens != null ? promptTokens : 0` — the framework defends against the null its annotations say cannot arrive. Against that stand the two concrete implementations and both models on this tree, none of which can produce one, and the unboxing sits in the first check, where an exception would cost the whole step's rows. The four measured reasons carry it; the contradiction is recorded here rather than left for a reader to rediscover in `Usage.java`. What replaces it is a statement rather than a branch, recorded here so it is chosen rather than inherited: **an absent `prompt_eval_count` arrives as `0`, `0` is below every window, so a call whose token telemetry is missing is not faulted for the ceiling.** That is the safe direction — a missing count says nothing about whether the prompt was shifted, and faulting on silence would cost a cluster its writing over an unreported number.

## Consequences

- A `ChatResponse` carrying no generation, and one whose output text is `null` or blank, each fault their cluster and the run continues. The `cluster_fault` rows written earlier in the step survive.
- An operator reading a `cluster_fault` row can tell an empty answer from an unreadable one from a call that answered nothing at all, by the detail alone.
- No `schema.sql` change and no `SynthesisSchema.VERSION` bump: the kinds and the row shape are untouched.
- **Compatible with the record-level tickets by construction.** #216 (a missing or blank `title`) and #224 (a missing `prose`) both edit `parseAnswer`, and all three land as `SCHEMA_VIOLATION` with distinct details. This one adds the response-level cases before and around `JSON_MAPPER.readValue`; those two add the record-level cases after it. Whichever lands second adds to the method rather than rewriting it.

### Named rather than repaired: a null `prose`

An answer of `{"title":"x"}` reads back as a record whose `prose()` is null, and `checkCitations` hands it straight to `Pattern.matcher`, which throws — the same run-level failure, reached through the parsed record rather than through the response.

It is **the one known exception to the rule this record states**, so it is named here rather than left as a silent hole, and it is owned: [#224](https://github.com/algernon28/vespera/issues/224) carries it. It was not closed in #222 because `parseAnswer`'s post-`readValue` half belongs to the record-level tickets — this record adds the response-level cases before and around `JSON_MAPPER.readValue`, and whichever record-level ticket lands adds to the method rather than rewriting it. It is deliberately not handed to #216, whose scope is `title` alone.

### Recorded, not repaired: a cut-off answer can still pass unnoticed

`docs/research/ollama-generation-surface.md` §1 records that Spring AI maps `done_reason` onto `finishReason` **only when both `promptEvalCount` and `evalCount` are non-null**, and otherwise the generation carries `ChatGenerationMetadata.NULL` and the reason is dropped. An answer genuinely cut off at its allowance therefore passes `checkAnswerDidNotRunOutOfRoom` silently whenever either count is missing. That is a hole in ADR-108's second check, not in this one. It is not closed here because the only available signal is a null finish reason, and faulting on that would turn down every answer from any serving engine that reports none — which ADR-110's seam does not let this module reason about. Its own ticket, if it is worth one.
