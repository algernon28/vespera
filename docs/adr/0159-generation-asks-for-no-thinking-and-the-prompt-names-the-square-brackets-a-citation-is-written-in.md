# ADR-159 — Generation asks for no thinking, and the prompt names the square brackets a citation is written in

- **Date**: 2026-09-26
- **Status**: accepted
- **Amends**: [ADR-108](0108-6b-sends-one-exemplar-first-call-per-cluster-and-verifies-every-response.md), in what its generator identity's "the options actually sent" means. It means the fields of Ollama's `options` object: `num_ctx` and `num_predict` today, and `temperature` and `seed` if a call ever sends them. It does not mean every field of the request. A top-level request field such as `think` or `format` is not one of them. See *Generation's run identity* below.
- **Extends**: ADR-108 (what every generation call is made under gains one more stated option) and [ADR-109](0109-a-citation-is-an-exemplar-ordinal-minted-for-one-call-and-the-check-is-that-it-is-in-range.md) (its "the instruction reserves square brackets for citations" is made literal in the prompt).
- **Keeps**: ADR-109's check, unchanged and not widened; [ADR-026](0026-generated-content-verified-mechanically-and-by-human-review.md) and ADR-109's refusal to repair model output; [ADR-111](0111-a-turned-down-answer-is-a-cluster-fault-and-five-in-a-row-stop-the-step.md)'s five-in-a-row stop; [ADR-114](0114-the-generation-model-is-named-in-application-configuration-with-a-code-default-and-is-not-a-gate.md) (the generation model is configuration with a code default, and `qwen3:8b` stays that default); [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) (what a code change does to a run's identity).
- **Rests on**: the first end-to-end run over a real corpus, and a probe of the exact call against the same serving engine, both on 2026-09-26 and both measured below; Ollama's `server/routes.go` at tag `v0.33.2`; Spring AI 2.0.0's `OllamaChatOptions`, `OllamaApi.ChatRequest` and `ThinkOption`.

## Context

### The measurement

Invocation 5 over the GesPOS corpus, with the jar built from `5cba9ef`, the shipped generation model `qwen3:8b`, and Ollama 0.33.2 serving it on CPU. 6b called 9 clusters:

- **8 were turned down** as `CITATION_NOT_IN_RANGE` with the detail *no citation at all in the writing*;
- **1 was turned down** as `ANSWER_RAN_OUT_OF_ROOM` with *the answer stopped after 1024 token(s)*;
- and the step then stopped on five turn-downs in a row (ADR-111). The deliverable carried no synthesis doc at all.

The call was then reproduced directly against `/api/chat` with `ClusterSynthesis`'s exact prompt template, the answer schema as `format`, `num_ctx` 8192, `num_predict` 1024, and three real GesPOS opening chunks:

| Request | Time | Reasoning trace | `eval_count` | Citations in the prose |
| --- | --- | --- | --- | --- |
| `think` unset (Ollama's default: on for `qwen3`) | 113 s | 2,486 characters | 435 | every document, as `{1}`, `{2}`, `{3}` |
| `think: false` | 26 s | none | — | every document, as `{1}`, `{2}`, `{3}` |
| `think: false`, and the first wording of the citation sentence: "exactly as they appear above, such as [1] or [2][3]" | 31–43 s | none | — | `[n]`, in range, in 3 calls of 3 |

The first wording had two faults, found in review and not in the measurement. It showed `[2][3]` to every call, including a call that sent one document or two. A cluster of one is an expected outcome, and a model copying the example would cite past what it was sent. And it said the numbers appear "above" when the prompt puts the documents below the instruction. So the sentence §2 decides was measured again the same way (same engine, model, schema, `num_ctx` and `num_predict`, `think: false`, real GesPOS opening chunks). The word was "below", and the example was built from the call's own document count:

| Documents sent | Example shown | Where the numbers are said to be | Time | Result |
| --- | --- | --- | --- | --- |
| 1 | `[1]` | below | 21–42 s | 3 calls of 3 cite only `[1]` |
| 1 | `[1]` | above | 30–39 s | 3 calls of 3 cite only `[1]` |
| 2 | `[1] or [1][2]` | below | 33–53 s | 3 calls of 3 cite `[1]` and `[2]`, nothing else |
| 3 | `[1] or [2][3]` | below | 25–35 s | 3 calls of 3 cite within `[1]`–`[3]` |

In all 12 calls every citation was in range and none was written in curly braces. Across both probes that makes 15 calls with no brace citation, where the unamended prompt produced braces every time. "Above" and "below" both worked for one document, so the word is chosen because "below" is true of the prompt, not because it measured better.

### Two causes, independent of each other

**The model cites in curly braces.** The prompt said *"Cite with the bracketed numbers, inline, and use no other citation of any kind"*, and the documents arrive under `[1]`, `[2]`, `[3]`. With thinking on and with it off, `qwen3:8b` wrote `{1}` for each of them — every document cited, none of them in a form ADR-109's check reads (`\[(\d+)\]`), so every answer was uncited writing. The likely reason is the answer schema: the answer is decoded as a JSON object, and a model writing into a JSON string reaches for braces. That is an explanation, not a measurement; the measurement is that naming the form fixed it.

**Thinking spends the answer's room and time.** `ClusterSynthesis.optionsFor` never said whether to think. Ollama 0.33.2 sets `think` to true for any model whose capabilities list `thinking` when the request leaves it unset (`routes.go`, both the chat and the generate handler), and `qwen3:8b` lists it. The reasoning tokens are counted against `num_predict`, which is `REPLY_ALLOWANCE` (1024), so a long enough reasoning trace leaves the answer no room — the one `ANSWER_RAN_OUT_OF_ROOM` above. It also costs more than four times the wall clock per call on the hardware the tool runs on, and nothing in the deliverable ever reads the trace.

## Decision

### §1. Every generation call asks for no thinking

`ClusterSynthesis.optionsFor` adds `.disableThinking()`, which Spring AI 2.0.0 carries as `ThinkOption.ThinkBoolean.DISABLED` onto `ChatRequest.think` and serialises as `"think": false`. Serialising the request `OllamaChatModel` builds from the shipped options, with no call made, reads `{"model":"qwen3:8b",…,"options":{"num_predict":1024,"num_ctx":8192},"think":false}` after this change, and carried no `think` key at all before it. It is sent on every call, the way `num_ctx` is sent (ADR-108): left unsaid, whether the model thinks is decided by the engine per model, not by this code.

**A model that cannot think is not refused for it.** Ollama 0.33.2 refuses a request only when `think` is *true* for a model without the capability (`"%q does not support thinking"`); `false` for such a model passes. So an operator naming a non-thinking model in the profile (ADR-114) is unaffected.

**It is a code constant, not configuration.** The operator decided it ("if thinking is a problem, disable it"), and ADR-108 and ADR-114 already refuse a knob nobody can reason about: a reasoning trace this tool never reads is not something an operator can tune toward a better deliverable, and turning it back on reopens the room it spends inside a fixed `REPLY_ALLOWANCE`.

### §2. The prompt names the citation form, with an example

The citation sentence becomes the one the second probe measured:

> Cite with the bracketed numbers, inline, written in square brackets exactly as they appear below, such as *example*, and use no other citation of any kind.

**The example comes from the call's own document count**, `n`, the number of documents the call carries:

| `n` | *example* |
| --- | --- |
| 1 | `[1]` |
| 2 | `[1] or [1][2]` |
| 3 or more | `[1] or [2][3]` |

**The rule is that no number the instruction shows is greater than `n`.** A model that copies the example literally then cites only documents it was sent, and ADR-109's range check has nothing to turn down. That is the part tests pin. The exact wording is not pinned: it may change without a new record so long as the instruction still names square brackets and every `[k]` it shows has `k ≤ n`.

**"Below" because the documents are below.** The instruction comes before the documents in the prompt, and the sentence before it already says "each document opens below under the number to cite it by".

The rest of the prompt is unchanged.

### §3. The check is not widened to read `{n}`

ADR-109 defines a citation as `[n]` inline, and the deliverable rewrites each `[n]` into a link to entry `n` of the membership list. Reading `{n}` too would be a rule that repairs model output — deciding that braces the model wrote *meant* a citation — which ADR-109 refused on the same grounds as stripping ("every such rule is a judgement about which part of the output to believe"). It would also need a second rewrite in the deliverable, or leave `{2}` reaching a reader as text pointing nowhere, and it would read as citations every brace-wrapped number in a technical corpus's prose. Asking for the right form is the fix; an answer in any other form is still uncited and still turned down.

## Alternatives rejected

- **Raise `REPLY_ALLOWANCE` to fit the reasoning.** It fixes the one run-out-of-room case and neither the braces nor the time; every token of allowance is subtracted from the room for documents (`roomForDocumentsIn`), and it is part of generation's `config_consumed`, so it would re-mint the run for nothing a reader sees.
- **Make thinking a configuration key.** See §1: nothing reads the trace, so the key would select between a slower answer with less room and the one this record ships.
- **Read `{n}` as a citation.** §3.

## Consequences

**Generation's run identity, and what this record amends in ADR-108.** ADR-108 made the generator's identity "the model name plus the options actually sent — `num_ctx`, `num_predict`, `temperature`, `seed`". After this change `think: false` is sent on every call, so a reader could take it to be one of "the options actually sent" and expect it in `config_consumed`. It is not one of them. The four fields ADR-108 lists are all fields of Ollama's `options` object, and "options" in that sentence means that object. `think` is a top-level request field, like `format`, which carries the answer schema and has never been in `config_consumed`. Both are code constants. A code constant outside the `options` object reaches a run's identity through the implementation version (ADR-058), not through `config_consumed`. The prompt text is code too and reaches it the same way.

One code constant is in `config_consumed` anyway: `REPLY_ALLOWANCE`, sent as `num_predict`. It is there because ADR-108 names `num_predict` and it sits in the `options` object, not because it can vary between invocations of one build. That is left as it is. Taking it out would change `config_consumed` and the golden text for no gain. Keeping it costs nothing either: a changed allowance is a code change, which already changes the implementation version, so its place in `config_consumed` never mints a run that would not have been minted anyway.

So `config_consumed` is unchanged and the golden text in `RunIdentityGoldenTest` does not move. The implementation version does: this change touches `synthesis`, whose last-commit SHA is part of both generation's and arrangement's implementation version (`synthesis+extraction+embedding+pipeline`). **Over an existing working directory, the first invocation on the new build therefore mints a new arrangement run and a new generation run**: the arrangement stops at its gate again and has to be approved by its new id (ADR-107, ADR-154 §2) before 6b runs. That is ADR-058's accepted cost of a change to the module, landing where it should — the writing is different, so the run is too.

**Twelve calls are a small sample.** The sentence §2 decides was measured three times each on calls carrying one, two and three documents, one model, one engine, and all 12 passed. The earlier wording passed 3 of 3 as well. A model or a cluster that still writes some other form is turned down as before, with the detail saying there was no citation. A recurrence therefore shows up in `cluster_fault` and is not silent.

**The example no longer invites an out-of-range citation.** The first wording showed `[2][3]` even to a call that sent one document. A model copying it would have been turned down as `CITATION_NOT_IN_RANGE`, and because clusters of one are an expected outcome, enough of those in a row could have reached ADR-111's five-in-a-row stop and halted 6b. §2's rule removes that: no number the instruction shows is greater than the number of documents the call sent. A model can still cite out of range on its own. ADR-109 turns that down as it always has.

**The writing is produced without a reasoning pass.** Whether that makes it worse is a judgement about prose, and no check here makes it (ADR-026). What is measured is that it now arrives cited, in 21–53 s across the 12 calls, where the unfixed call took 113 s.

## Tests

- `ThinkingModelWritesNoCitationTest` — the call carries thinking disabled. For calls carrying one, two and three documents, the instruction names square brackets and every `[k]` it shows has `k` no greater than the number of documents sent. Writing citing `{1}` and `{2}` is still turned down as having no citation. The think test fails without §1. The one- and two-document instruction tests fail on the first wording, which showed `[2][3]` to every call. The last test fails neither way: it pins §3.
- `ThinkingModelWritesNoCitationIT` — against a real Ollama 0.33.2 in a container, a model the engine lists as able to think (`qwen3:0.6b`) answers a call made under `optionsFor` with no reasoning trace, so the option survives the framework onto the wire.
