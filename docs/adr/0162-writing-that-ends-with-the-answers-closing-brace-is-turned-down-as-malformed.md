# ADR-162 — Writing that ends with the answer's closing brace is turned down as malformed

- **Date**: 2026-09-26
- **Status**: accepted
- **Extends**: [ADR-108](0108-6b-sends-one-exemplar-first-call-per-cluster-and-verifies-every-response.md)'s schema check. An answer can read back into `ANSWER_SCHEMA` and still carry, inside its writing, the brace that closes the JSON object. That is a malformed answer, recorded as `SCHEMA_VIOLATION` with a detail of its own.
- **Keeps**: [ADR-109](0109-a-citation-is-an-exemplar-ordinal-minted-for-one-call-and-the-check-is-that-it-is-in-range.md) and [ADR-159](0159-generation-asks-for-no-thinking-and-the-prompt-names-the-square-brackets-a-citation-is-written-in.md) §3 (no rule repairs model output); [ADR-111](0111-a-cluster-fault-is-a-row-in-synthesis-and-a-re-run-under-the-same-id-repairs-rather-than-regenerates.md) (a turned-down answer is a `cluster_fault`, the repair pass asks again, five in a row stop the step); `ClusterFaultKind`'s closed set of four, which gains nothing; [ADR-124](0124-both-fields-of-a-parsed-answer-are-required-and-missing-writing-is-a-schema-violation-rather-than-an-uncited-one.md) and ADR-125 (details keep the ways an answer fails apart).
- **Rests on**: the stored writing of both generation runs in the GesPOS end-to-end run's database, and the 95 probe calls [ADR-161](0161-the-instruction-follows-the-documents-and-names-a-length-the-reply-allowance-holds.md) records, all against `qwen3:8b` on Ollama 0.33.2 on 2026-09-26. The raw answers are kept in `D:\development\vespera-runs\2026-09-26-gespos-e2e\probes-adr-161-162\`, outside the repository.
- **Settles** [#318](https://github.com/algernon28/vespera/issues/318).

## Context

One page of the first end-to-end run's deliverable ends its writing with `… card portfolio management process. }`. The `}` is the last character of the stored `prose`. `parseAnswer` took the field as it came, and no check looks at its content except for citations.

### How often it happens, measured

- **Stored writing**: 11 synthesis docs across the run's two generation runs. 1 ends in ` }` (cluster `AcquiringBancario_ProcessiGestioneParco_1_3`, run `31ef531e…`). The other 10 carry no brace anywhere.
- **Probe calls**: of the 95, 90 finished and read back into the schema. **3 of the 90** had writing ending in a brace: `…[13][14] } `, `…operational oversight.} ` and `…documented and standardized. } `. In the raw answer each is followed by the real closing `"}`. None of the 90 had a `{` anywhere in its writing, and none had any other JSON fragment in it: no `"title":`, no `", "`, no escaped newline.
- **By wording**: all 3 came under ADR-161's wording (3 of 45 finished answers). Under the shipped wording the probes found 0 of 19, and the run found 1 of 10. The debris shows up under both wordings, at a few per cent.
- **One out-of-room answer, not among the 90**: under the restatement wording ADR-161 rejected, the model wrote connecting prose, cited `[1]`…`[14]`, and then repeated ` }` 297 times until it reached the cap. ADR-108's out-of-room check turns that answer down before this check is reached. It is the same tendency in a runaway shape, and it is why stripping is not the small repair it looks: a strip rule would have faced 297 braces, not one.

So in the finished answers the debris is one shape, at one place: the model closes its own object inside the string, at the very end of the writing, and then closes it again for real.

### The three options #318 names

- **Strip it.** It is a rule that repairs model output, which ADR-109 refused: *"every such rule is a judgement about which part of the output to believe"*. ADR-159 §3 applied the same refusal to reading `{n}` as a citation. A stray `}` carries no claim, which makes stripping it look harmless. But a rule that deletes characters the model wrote has to decide which ones are debris, and the next debris will not be exactly a brace. That is the slope ADR-109 declined to start down.
- **Leave it.** The reader of the deliverable is handed writing that visibly ends in a piece of the machinery that produced it. Nothing else in the page is unverified in that way, and the page gives no sign of it.
- **Turn it down.** The answer is malformed: the model's JSON closed inside the writing. ADR-108 already turns down an answer that does not arrive in the imposed shape, and this is the one case the parser cannot see, because the brace was typed inside a well-formed string.

## Decision

**Writing whose last non-blank character is `}`, and that holds more `}` than `{`, is turned down.** It is recorded as a `cluster_fault` of kind `SCHEMA_VIOLATION` with the detail:

> the answer's writing ends with a closing brace that belongs to no opening one

It is checked in `parseAnswer`, after the writing is established as present and not blank (ADR-124), so it stays among the schema checks and runs before the citation check, as ADR-111's order requires.

**The rule is narrow on purpose, and three things follow from that.**

- **A balanced brace is content.** `… set {HOST} and {PORT}` ends in `}` and is believed. A technical corpus can hold braces, and nothing measured says a balanced pair is debris.
- **A brace elsewhere never turns an answer down on its own; it is counted only to decide whether a final `}` is balanced.** Every piece of debris measured was at the end. A rule for a position nothing measured would be a guess about which output to believe.
- **The rule names debris; it does not recover writing.** Nothing is deleted. A turned-down answer leaves the hole ADR-111 already describes, and the next invocation's repair pass asks the same question with nothing of this answer in it. At 3 in 45 under ADR-161's wording, the expected re-ask succeeds.

**No fifth fault kind.** `SCHEMA_VIOLATION` already means *what came back is not the shape the call imposed*, and a JSON frame closed inside a field is a case of that. A detail of its own keeps it apart from the other schema failures, which is ADR-124's rule.

## Alternatives rejected

- **Strip a trailing `}`.** See the Context: a repair rule, and ADR-109 refused those.
- **Leave it.** See the Context: the reader gets the machinery with nothing to say it is there.
- **Forbid braces in `prose` through a `pattern` in `ANSWER_SCHEMA`.** A side probe, not part of the 95 calls, sent `qwen3:8b` the prompt "Write two sentences about configuration files. Put the placeholder {HOST} and the placeholder {PORT} in them exactly like that, with the curly braces, and end the second sentence with } ." three times under the answer schema with `prose` a plain string, and three times with `prose` carrying `"pattern": "^[^{}]*$"` (`num_ctx` 2048, `num_predict` 256, `think: false`). Under the plain schema, 3 of 3 finished with the braces in `prose`. Under the pattern, no answer had a brace in `prose`, so Ollama 0.33.2 does enforce it, but none of the three was usable. One finished with `prose` broken into repeated escaped newlines and string-concatenation fragments. One ran out of room repeating `\n\n`. One ran out of room after moving the braced sentence into `title`, which the pattern does not cover. The raw output (`pattern.out`) and the script (`pattern.py`) are kept outside the repository, beside the GesPOS end-to-end run in `D:\development\vespera-runs\2026-09-26-gespos-e2e\probes-adr-161-162\`, with the 95 calls' `results.jsonl`. A constraint that trades a stray character for a runaway answer is worse. It would also forbid braces a technical corpus legitimately holds.
- **A fifth `ClusterFaultKind`.** See above.

## Consequences

**More clusters are left unwritten on a first pass.** The rate that matters is under ADR-161's wording, which is what ships with this record: 3 in 45 finished answers, about 7%. All three debris answers came under it; under the shipped wording the probes found 0 of 19 and the run 1 of 10. At about 7%, a corpus of 400 clusters has about 27 turned down on its first invocation for this alone, and about 2 still turned down after one repair pass. ADR-111's five-in-a-row stop is not at risk from this alone: five debris answers in a row at 1 in 15 is odds of about 1 in 760,000.

**That cost is accepted.** Under the same wording the measurement found no answer that ran out of room and none that cited nothing, in 45 calls, so a turned-down brace is the only loss measured there. The three braces fell on three different clusters, so nothing measured says one cluster keeps drawing it, and the repair pass asks each of them again.

**The one page that shipped with a `}` would not have shipped.** Its cluster would have been a `cluster_fault`, and the repair pass would have asked again.

**Other debris is still believed.** None was measured in 101 pieces of writing (90 probe answers and 11 stored docs). If some turns up, it is recorded against this decision and decided on its own measurement, with no rule written in advance.

**No run identity moves beyond ADR-058's.** The check is code in `synthesis`, so it moves the implementation version, which ADR-161 moves in the same change.

## Tests

`JsonDebrisInWritingTest` (unit, scripted `ChatModel`):

- Writing ending in ` }` after its last sentence, as the shipped page did, is turned down as `SCHEMA_VIOLATION` with this record's detail. **Fails today**: it is believed.
- Writing ending in `}` followed by blank space, as the probe answers did, is turned down the same way. **Fails today**.
- Writing ending in a balanced `{PORT}` is believed, and the writing is kept exactly as written. Holds before and after: it pins the narrowness.
- A believed answer's writing is stored exactly as the model wrote it, with nothing trimmed or removed. Holds before and after: it pins the refusal to strip.
