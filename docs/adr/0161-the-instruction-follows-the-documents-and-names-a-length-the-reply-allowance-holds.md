# ADR-161 — The instruction follows the documents and names a length the reply allowance holds

- **Date**: 2026-09-26
- **Status**: accepted
- **Amends**: [ADR-159](0159-generation-asks-for-no-thinking-and-the-prompt-names-the-square-brackets-a-citation-is-written-in.md) §2, in one word and one place. The citation sentence moves after the documents, so the numbers it names appear "above", not "below". Its rule is unchanged: the instruction names square brackets, and no number it shows is greater than the number of documents the call sent.
- **Extends**: [ADR-108](0108-6b-sends-one-exemplar-first-call-per-cluster-and-verifies-every-response.md). Its "sent: the cluster's label and its seed partition's name; then its documents" gains a third part after the documents: what to write, and how long.
- **Keeps**: ADR-108's `REPLY_ALLOWANCE` of 1024 and its `done_reason: "length"` check; [ADR-109](0109-a-citation-is-an-exemplar-ordinal-minted-for-one-call-and-the-check-is-that-it-is-in-range.md)'s refusal to regenerate within one call; [ADR-111](0111-a-cluster-fault-is-a-row-in-synthesis-and-a-re-run-under-the-same-id-repairs-rather-than-regenerates.md)'s repair pass, which is the only second asking there is; ADR-159 §1 (`think: false`).
- **Rests on**: 95 calls to `qwen3:8b` on Ollama 0.33.2 (CPU-served, `num_ctx` 8192, `think: false`, `format` = `ANSWER_SCHEMA`) on 2026-09-26. Each call carried a request rebuilt from the database of the GesPOS end-to-end run at `D:\development\vespera-runs\2026-09-26-gespos-e2e`, read-only. The rebuilt exemplars matched the run's recorded `call_exemplar` rows exactly, for all 10 clusters the run wrote. The raw answers (`results.jsonl`, one line per call) and the scripts that made them (`groups.py`, `probe.py`, `plan.py`, `summary.py`) are kept beside that run, in `probes-adr-161-162\`, outside the repository.
- **Settles** [#317](https://github.com/algernon28/vespera/issues/317).

## Context

In invocation 7 of the first end-to-end run, 10 of 11 clusters were written. The 14-document cluster `CARD_SIMPLIGI_ESITI_TLG_20170426_105500`, under `Scontrini_1_19.docx`, was turned down as `ANSWER_RAN_OUT_OF_ROOM` after 1024 tokens. The same cluster had been turned down the same way under `5cba9ef`, when thinking was still on. #317 read this as a long answer outgrowing the allowance, and asked whether to raise the allowance, bound the length in the prompt, or both.

**The answer was not too long. It was not an answer.** All 14 documents fit the call (1,857 words against room for 3,456), and every one is a row-per-record supply file (`AggSoftware 91252536 255505 VILLA SRL …`). The prompt put the whole instruction before the documents, so some 4,000 tokens of records sat between the instruction and the point where the model starts writing. Four of the five out-of-room answers measured below were the model copying records back into `prose` until the cap. Three copied them verbatim under the title "Extracted Data from Text", and one laid them out as an Italian bulleted list. The fifth, under the restatement wording, wrote connecting prose and then repeated ` }` until the cap, 297 times; ADR-162 records it. The finished answers mostly read like a chat reply ("Ecco una raccolta di dati… Se hai bisogno di un'analisi, fammelo sapere"), in Italian, with no citation at all.

### What was measured

Every row is the 14-document cluster unless it says otherwise. "Pass" means the answer finished (`done_reason: "stop"`), read back into the schema, and every citation was in range with at least one present: all of ADR-108's and ADR-109's checks.

| Request | Calls | Ran out of room | Finished, no citation | Pass | Tokens written (finished) | Words (finished) |
| --- | --- | --- | --- | --- | --- | --- |
| Shipped prompt, allowance 1024 | 14 | 3 | 10 | 1 | 78–461 | 30–307 |
| Shipped prompt, allowance 2048 | 6 | 0 | 4 | 2 | 101–629 | 48–219 |
| Shipped prompt, allowance 4096 | 1 | 0 | 1 | 0 | 210 | 100 |
| Shipped prompt plus "Write at most 400 words." before the documents | 8 | 1 | 7 | 0 | 70–263 | 42–162 |
| Shipped prompt plus a restatement after the documents, naming 400 words | 12 | 1 | 2 | 9 | 139–418 | 64–268 |
| **Instruction moved after the documents, naming at most 400 words** | **15** | **0** | **0** | **15** | **232–529** | **136–345** |
| Instruction moved after the documents, naming no length | 8 | 0 | 2 | 6 | 219–400 | 165–274 |

The table leaves out one call: the shipped prompt at 1024, sent once to the 13-document cluster `STB_SF_011_USIN_SPEC_M100_RISPO_E01_01_2010`. It passed at 716 tokens and 427 words. A planned sweep of the shipped prompt over every cluster was stopped by the host after that call and the 14-document one, so no rate over the other clusters is claimed for the shipped prompt.

With the instruction after the documents and a 400-word length, every call on the 14-document cluster cited within `[1]`–`[14]`. The number of distinct documents cited ranged from 2 to 14, and 14 appeared in 3 of 15. The mean wall clock was 44 s per call. The longest answer used 529 of 1,024 tokens.

The same request was then sent 3 times to each of the 11 clusters the run arranged (1 to 14 documents, 27 to 1,857 words of documents, prompts of 244 to 6,808 tokens). All **33 of 33 passed**, at 258–507 tokens and 192–316 words, and none ran out of room.

**What each lever did, read off the table:**

- **Raising the allowance fixed nothing.** At 2048, 4 answers in 6 still cited nothing. The answers that ran out of room were copies, and a copy runs to whatever cap is set. Every token added to the allowance is also taken from `roomForDocumentsIn`, and `num_predict` is in generation's `config_consumed` (ADR-108, ADR-159).
- **A length alone fixed nothing.** "At most 400 words", placed above the documents, made the answers shorter and passed 0 of 8. It was lost with the rest of the instruction.
- **Placement is what fixed it.** Restating the instruction after the documents passed 9 of 12. Moving it there whole passed 6 of 8 with no length named, and 15 of 15 with one.

## Decision

### §1. The instruction comes after the documents

The request keeps its opening: how many documents, the cluster's label, its seed document, and a line saying each document opens below under the number to cite it by. Then come the documents under their ordinals, exactly as ADR-108 and ADR-133 send them. **Then the instruction**: say that these are all *n* documents; connect them, saying what they share, where they differ and what they amount to together; do not summarise them one by one; cite in square brackets as they appear above, with ADR-159's example; use no other citation.

**The rules that tests pin, with the wording left free** as ADR-159 §2 left it free:

1. After the last document's opening, the request names square brackets, shows at least one bracketed number, and shows none greater than the number of documents sent (ADR-159's rule, now applied after the documents).
2. After the last document's opening, the request names a length in words, and that length times `TOKENS_PER_WORD` is no more than `REPLY_ALLOWANCE`.

### §2. The length is 400 words, chosen by measurement and held under the allowance

`REPLY_ALLOWANCE / TOKENS_PER_WORD` is 512 words. At ADR-108's own pessimistic ratio, a length of 512 is the most the allowance can hold with no room left for the title or the JSON frame. **400** leaves about a fifth of the allowance for those two. It is what was measured, and the longest measured answer (345 words, 529 tokens) came to barely half the allowance. The constant carries its own reasoning and, like `REPLY_ALLOWANCE`, is a code constant: it reaches the run's identity through the implementation version (ADR-058, ADR-159), not through `config_consumed`.

**The model is not bound by it, and nothing checks it.** An answer longer than 400 words that still finishes inside the allowance is believed, because words are not what ADR-108 verifies. The check is still `done_reason`. The length is a request that measurably keeps the answer to the task. It is not a limit.

### §3. `REPLY_ALLOWANCE` stays 1024

Raising it was measured and fixed nothing (see above). Leaving it where it is changes neither `roomForDocumentsIn` nor `config_consumed`, and the `RunIdentityGoldenTest` golden text does not move.

### §4. An answer that runs out of room is not retried within the invocation

ADR-109 refuses a second call made *because* an answer failed a check. ADR-111 narrowed that refusal to one invocation, and only on the terms that the second call is byte-identical to the first and nothing of the failed answer reaches it. A retry "with a tighter instruction" breaks both terms: the failure chooses the new prompt. It is the correction round those records refuse. Measured, it is also not needed. With §1 and §2, 45 calls of 45 finished inside the allowance, 15 of them on the cluster that ran out of room (12 calls to it alone, and 3 in the 33-call sweep). An out-of-room answer that still occurs remains a `cluster_fault`, and the next invocation's repair pass asks the same question again.

### What this record does not decide

**Whether a turned-down cluster's page says why nothing was written.** Today the page says only *nothing was written over this group*. The reason is recorded in `cluster_fault`, in the step's log line, and in the invocation's closing count. Putting it on the page would pass the fault rows into `Deliverable`. That is a change to the deliverable's contract, which is separate from why the largest cluster got no prose. The question is left undecided here, and the operator has been asked whether to file a ticket for it.

## Alternatives rejected

- **Raise `REPLY_ALLOWANCE`.** Measured at 2048: 2 passes in 6. It re-mints generation's run through `config_consumed` and shrinks the room for documents, for nothing.
- **Bound the length where the instruction already was.** Measured: 0 passes in 8.
- **Restate the instruction after the documents and keep it before them too.** Measured: 9 in 12, below moving it (15 in 15). It also asks for the same thing twice in two wordings, which is two places to drift.
- **Retry once, tighter.** See §4.

## Consequences

**The request is longer by about ten words.** The instruction itself is under a hundred words: 96 by whitespace count in the one-document case, 192 tokens at the pessimistic ratio. That is still inside `INSTRUCTION_RESERVE`'s 256 tokens, so `roomForDocumentsIn` is unchanged.

**The implementation version moves, as for any change to `synthesis`.** Over an existing working directory, the first invocation on the new build mints a new arrangement run and a new generation run, and the arrangement has to be approved again by its new id before 6b runs (ADR-058, ADR-107, ADR-154 §2). ADR-159 accepted the same cost for the same reason.

**The sample is one model, one engine, one corpus.** 45 calls under the decided wording, all passing, and 15 of them on the cluster that failed. A different model or corpus can still run out of room or cite nothing. Either one is turned down and recorded as before, and is never silent.

**The trailing brace #318 reports showed up under this wording**: 3 of the 45 finished answers under it. [ADR-162](0162-writing-that-ends-with-the-answers-closing-brace-is-turned-down-as-malformed.md) decides what happens to those answers. It does not change this decision: the brace costs a cluster at a measured 3 in 45, where the shipped prompt cost the largest cluster 13 times in 14.

## Tests

`LargestClusterRunsOutOfRoomTest` (unit, scripted `ChatModel`):

- At 1, 2, 3 and 14 documents, the text after the last document's opening names square brackets and shows at least one bracketed number, none past *n*. **Fails today**: the shipped request ends with the last document.
- After the last document's opening, the request names a length in words, and that length times `TOKENS_PER_WORD` is at most 1024. **Fails today**: no length is named.
- The call's `num_predict` is still 1024. Holds before and after: it pins §3.
- A scripted `done_reason: "length"` answer is turned down as `ANSWER_RAN_OUT_OF_ROOM` with the unchanged detail, and costs exactly one call. Holds before and after: it pins §4.

`ThinkingModelWritesNoCitationTest` needs no change: it reads the whole request with the documents taken out, so it holds wherever the sentence sits.
