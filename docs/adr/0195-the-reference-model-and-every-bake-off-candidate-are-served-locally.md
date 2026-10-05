# ADR-195 — The reference model and every bake-off candidate are served locally, never hosted

- **Date**: 2026-10-05
- **Status**: accepted
- **Amends**: [ADR-072](0072-adr-034-is-one-bake-off-the-extraction-engine-has-a-reference-model-not-a-bake-off.md), in three passages:
  - its table row, which reads "One hosted model (OpenAI)";
  - its description of the reference model as "a deliberately larger, hosted model";
  - its statement that "the same hosted model may separately be a bake-off candidate, as ADR-033's candidate list has it".

  Both the reference model and the bake-off candidates are served locally (§1, §2). ADR-072's other findings stand: one engine per run, no in-pipeline fallback, confirmation by a second run compared by a person.
- **Amends**: [ADR-046](0046-the-pom-carries-what-a-recorded-decision-requires.md)'s additions list, which names the "Ollama starter alongside OpenAI". The OpenAI starter is removed (§3), under ADR-046's own rule: no recorded decision requires it any more.
- **Extends**: [ADR-033](0033-embedding-model-criteria-recorded-model-unset.md), adding a sixth requirement to its five: the model is served locally. The five it already records are:
  - a separable tokenizer;
  - pinned weights;
  - Italian and English;
  - judged on Clustering/STS, not Retrieval;
  - OCR tolerance.

  ADR-033's surviving summary names no candidate. The hosted "ceiling model" appears only in `docs/architecture.md` §2's embedding row, cited to ADR-033 and ADR-034, and that row is corrected here.
- **Clarifies**: [ADR-034](0034-embedding-model-chosen-by-bake-off-not-argument.md)'s clause "Abort verdicts must be confirmed against a larger model". It asks for a larger model and says nothing of where that model runs.
- **Settles** [#425](https://github.com/algernon28/vespera/issues/425).

## Context

ADR-072 reads ADR-034's clause as a **reference model**: "a deliberately larger, hosted model that an unfavourable measurement is re-checked against before it is believed". Its table row says "One hosted model (OpenAI)". `docs/architecture.md` §2 also lists "one hosted ceiling model" among the embedding bake-off's candidates. `pom.xml` carries `spring-ai-starter-model-openai`: ADR-046's additions list names it, and ADR-072 explains it as the reference model's ("which is why the starter is in the pom").

No class uses the starter, no profile key or flag selects it, and it has never been run. If it were run, it would send document text to a hosted service: chunks to embed, and prompts if generation were switched too. A hosted bake-off candidate would send the same chunks off the machine to be embedded. The operator's archives can hold sensitive documents, and nothing that reads a document may leave the machine. A hosted reference model or a hosted candidate contradicts that, and the dependency leaves both one configuration change away.

## Decision

### 1. The reference model is served locally

The reference model is the larger model an unfavourable measurement is confirmed against, and it runs on the operator's machine. An example is a larger embedding or chat model served by the operator's Ollama. It is never a hosted service. This replaces "hosted" and "OpenAI" in ADR-072 wherever they describe the reference model.

### 2. Every bake-off candidate is served locally

Every embedding model the bake-off measures (ADR-034) runs on the operator's machine. That includes any ceiling candidate, which may be larger but is never hosted. This is a requirement added to ADR-033's, so a hosted model fails it whatever its scores. Nothing that reads a document leaves the machine: not for selection, not for confirmation, not for a run.

### 3. The OpenAI starter leaves the pom

`spring-ai-starter-model-openai` is removed from `pom.xml`, along with the comment that justified it. ADR-046 keeps a dependency in the pom only while a recorded decision requires it, and after §1 and §2 none does. `spring-ai-starter-model-ollama` serves the embeddings stage 5 computes and the chat model stage 6b generates under, whichever local model is configured. The extraction engine is not reached through Spring AI: which runtime `docling-serve` uses is configuration (ADR-012), Ollama by default (ADR-013), and a larger local model for an extraction confirmation run would be chosen there the same way.

### 4. The documents say it

These all say "local":
- the "reference model" and "Embedding model" rows of `docs/architecture.md` §2;
- the **Reference model** and **Bake-off** entries of `CONTEXT.md`;
- the comment in `application.yaml`.

## Consequences

- Nothing leaves the machine for selection or confirmation, and no API key is ever needed.
- The reference model's size, and the ceiling of the bake-off, are bounded by what the operator's hardware serves.
- Older records keep their text. ADR-072 says "hosted" and "OpenAI". ADR-046's summary and `docs/decision-ledger.md` say "alongside OpenAI". This record amends ADR-072 and ADR-046 as stated above. ADR-033's summary never named a hosted candidate, so for it only `docs/architecture.md` changes.
