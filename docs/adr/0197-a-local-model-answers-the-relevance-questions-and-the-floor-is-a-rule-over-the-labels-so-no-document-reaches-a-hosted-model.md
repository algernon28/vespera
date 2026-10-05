# ADR-197 — A local model answers the relevance questions and the floor is a rule over the labels, so no document reaches a hosted model

- **Date**: 2026-10-05
- **Status**: accepted
- **Amends**: [ADR-088](0088-the-relevance-threshold-is-read-off-a-stratified-sample-of-sixty-labels-and-an-unset-floor-does-not-stop-the-run.md) in two sentences. A label is "a person's answer", and becomes: a person's answer, or a local model's answer that says it is one (§3). "Nothing writes the threshold into the profile" becomes: nothing does, except the rule in §4, run on request, over a key that is unset or that the rule itself wrote last (§4).
- **Amends**: [ADR-028](0028-relevance-threshold-human-labelling-gated-by-score-distribution.md)'s "human labelling" in the same way. The sample, its sixty documents and its bands are ADR-088's and stay as they are.
- **Amends**: [ADR-059](0059-schema-version-is-one-row-per-module-checked-and-refused-independently.md) and [ADR-049](0049-verdict-rows-and-schema-versioning-without-a-migration-tool.md) in one respect. A table that is new and that no row of an existing table depends on does not bump its module's schema version, and ADR-049's trigger for a migration tool does not fire for it (§3).
- **Extends**: [ADR-169](0169-the-label-file-shows-the-answers-already-recorded-and-a-changed-answer-replaces-the-old-one-and-is-reported.md). The label file shows who set an answer beside it (§3), and `vespera label` reports a changed answer exactly as it does now, which is also how a model's mistakes are counted (§1).
- **Rests on**: [ADR-097](0097-a-relevance-label-is-keyed-by-the-documents-path-and-the-seed-set-not-by-the-occurrence.md) (a label is keyed by path and seed set), [ADR-118](0118-the-answers-a-person-gave-never-join-a-runs-identity-so-the-two-steps-that-read-them-record-no-completion.md) (the floor decides again on every invocation), [ADR-107](0107-the-arrangement-gate-approves-a-named-6a-run-and-the-path-becomes-five-invocations.md) (an approval names an arrangement), [ADR-114](0114-the-generation-model-is-named-in-application-configuration-with-a-code-default-and-is-not-a-gate.md) (an identity records what actually answered), [ADR-195](0195-the-reference-model-and-every-bake-off-candidate-are-served-locally.md) (what is served locally), and [ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md) (no agent reads the operator's documents).
- **Keeps**: [ADR-047](0047-the-pipeline-never-blocks.md): `vespera label` stays the deliberate act and `vespera run` stays unattended.
- **Settles** [#423](https://github.com/algernon28/vespera/issues/423).

## Context

The operator's archives can hold documents that must not leave the machine. On 2026-09-26 the operator asked a Claude session to compile the relevance labels. On 2026-09-27 that session did so for the GesPOS run by reading `relevance-labelling.html`, which carries each sampled document's path and its first 400 characters. Those openings went to a hosted model. Three steps in this pipeline are done by a person reading documents:

- **the relevance labels**, which ADR-088 asks of a person for a stratified sample of sixty;
- **the choice of `relevanceScoreFloor`**, which the operator reads off those labels and the candidate-cut table;
- **`arrangementApproved`**, which ADR-107 asks the operator to write after reading `arrangement.html`. That page names each group after its highest-scoring document and lists paths.

From now on a model that runs on the operator's machine labels, and a rule sets the floor, inside Vespera, and nothing that reads a document is handed to an agent (ADR-196) or a hosted service. This record decides how. It decides no model, because the issue says a measurement does.

**A loopback address does not by itself keep a document on the machine.** An Ollama cloud model is served by the local daemon, which forwards the request to ollama.com. A labeller that checked only the endpoint would send openings to a hosted service while looking local. §6 closes that.

### What the code does today

- `RelevanceLabels` (`embedding`) holds `relevance_label`, keyed by `(path, seed_set)`. `record` is an upsert, so the last answer ingested replaces the earlier one (ADR-169 §2). Nothing in a row says who answered.
- `LabelIngestion` (`pipeline`) is `vespera label`'s whole logic: it reads the completed file, compares each answer with the one recorded and says which are new, unchanged and changed.
- `RelevanceLabelFile` writes `relevance-labels.yaml`: the run, embedder and seed set it was generated under, then one entry per sampled document with `path`, `score`, `band`, `closestSeed` and `relevant`.
- `RelevanceFloor` reads `relevanceScoreFloor` and applies it only to labels given under the same embedder identity. The labelling page's table of candidate cuts says what each would cost and recommends none.
- `ArrangementGate` accepts an `arrangementApproved` that is a prefix of the id of the arrangement this invocation made.
- `ClusterSynthesis` calls `qwen3:8b` through Spring AI's `OllamaChatModel`, with thinking switched off (ADR-159). The endpoint Spring AI calls is the one its `OllamaConnectionDetails` names, which comes from `spring.ai.ollama.base-url` and defaults to Ollama's own `http://localhost:11434`.
- `OllamaClient` (`embedding`) already reads what Ollama reports about a model: its digest and quantization from `/api/tags` (ADR-091).

## Decision

The six questions, in the issue's order.

### 1. Which local model labels: neither is chosen here, and the labeller sits behind an interface

`embedding` gains a public interface, `RelevanceLabeller`, which takes one question (the document's path, the path of the winning seed, and its opening, which may be absent) and answers relevant, not relevant or nothing. It also names itself (§3) and may refuse to be used (§6). `pipeline` composes it; `embedding` neither reads the profile nor knows a stage, as its module rule says.

**The first implementation is `OllamaRelevanceLabeller`: `qwen3:8b` through the `OllamaChatModel` `ClusterSynthesis` already uses**, asked for one word, thinking off, a handful of tokens of reply. It is first because it is already served, not because it beat anything: ADR-013 chose Ollama on friction, and this is the same kind of choice. A reply that is not exactly one of the two words is **no answer**, and the entry stays blank for the operator. Nothing is guessed from a longer reply.

**The second implementation, later, is a Laya typed-decision head.** The correction on the issue says what it needs: the ModernBERT backbone from `fr0stbit3/laya-typed-decisions-gguf` is served by LM Studio or Ollama as an embedding model (`modern-bert`, pooling none), and the decision head runs outside llama.cpp. The head is either a small local Python sidecar or a port into Java, and the port is possible only if the head is a small classifier over the backbone's vectors, which is to be checked against its weights. That check is not made here. The interface above is the whole of what this record asks of that work: one method that answers a question from the same three inputs, a name, and the refusal of §6.

**The protocol that decides between them is the operator's, on a sample.**

1. A labeller labels the sample (§6) and records its answers marked as its own (§3).
2. The operator reads the file, corrects every answer they disagree with and runs `vespera label`. Its existing line, `N changed`, is the number of answers the model got wrong on that sample: ADR-169 §3 already reports it, and nothing new is built for the first model.
3. The sample the operator has now checked is the set both models are scored against. A later labeller answers the same entries without recording, and is scored by agreement with the checked labels. That scoring command is built with the second implementation and not before, since there is nothing to compare against yet.
4. What counts first is the error that loses documentation: a document the operator checked as relevant that the model called not relevant. The floor rule (§4) is the lowest score among relevant labels, so such a mistake on a low-scoring document moves the floor up and takes documents with it. The opposite mistake keeps a document and costs only a lower floor. Agreement overall is read second.
5. Sixty documents in five bands is thin, and the report of the scoring says how thin rather than hiding it, as ADR-088 does for the bands.

Alternatives weighed:

- **A typed-decision encoder first.** Rejected as a first step: it needs a new sidecar and a new client before one label exists, and the backbone alone does not answer anything. It stays the planned second implementation.
- **The configured generation model, whatever it is.** Rejected: the generation model is a profile key an operator may point at anything, and a labeller whose identity moves with it would make labels incomparable across runs. The labeller's model is its own setting (§6).
- **A hosted model for speed.** Refused, whatever its quality. It is the thing this record exists to prevent (§6).

### 2. What the model is told: the operator's rule, the document's path and opening, and the winning seed's path

The prompt states the operator's rule exactly and asks one question:

- Only documentation is relevant. Specifications, manuals, architecture, flow layouts and process descriptions are relevant.
- Operational records, `.url` shortcuts, logs and screenshots are not relevant, even when they are on the subject of the seed documents.
- Then the document's path, its opening (the same 400 characters the labelling page shows, and the ones ADR-088 puts in front of a person), the path of the winning seed, which is the seed its score was taken against, and the question: *relevant or irrelevant, one word*. The code asks for those two words, and the reader accepts them and `not relevant`.

The rule is a constant in code, because it is the operator's definition of relevance for this archive. If a second archive wants another rule it becomes a profile key then. Making it one now would add a key nobody has answered differently.

**The seeds' own text is not sent**, only the path of the winning seed. That is what the person labelling saw, so the model and the person answer the same question from the same view. It also keeps a call small enough that sixty of them are minutes, not an hour.

An entry whose opening could not be read (the file would not open, or no conversion is on record) is put to the labeller with no opening, and `OllamaRelevanceLabeller` answers nothing for it. A model answering from a path alone would be guessing in a column the operator reads as an answer.

Alternatives weighed:

- **Send the seed set's text.** Rejected for now: it multiplies the call and the leakage surface by the seed set's size. Measured against the protocol in §1, if the model's errors turn out to need it, it is a change to a prompt and not to a design.
- **Put the rule in the profile.** Rejected, above.

### 3. How a label says a model set it: a provenance row beside the answer, and a person's answer always wins

**Provenance is its own table: `relevance_label_provenance(path, seed_set, labelled_by, run_id)`**, one row per label that a model set, keyed as the label is. `labelled_by` is the labeller's identity, which carries the model's name and **its weights' digest**, as ADR-114 records for generation: `ollama:qwen3:8b;digest=<digest>` for the first. Two models that share a name and differ in weights do not share an identity. **A label with no provenance row was set by a person**, which is every row that exists today.

A column on `relevance_label` was the alternative. It was rejected because the table already exists on every working directory, `CREATE TABLE IF NOT EXISTS` does not add a column to it, and ADR-049 says there is no migration tool. A new table is added by the same statement that adds every table.

**This amends ADR-059 and ADR-049, and nothing more of them.** ADR-059's version guard exists to refuse a database whose tables this code cannot read. A new table that no existing row depends on leaves every existing table readable, and a database written before it gets it from `schema.sql` on first start, so `EmbeddingSchema.VERSION` stays 7 and its javadoc now says when a table does bump. ADR-049 names a point at which a migration tool is built: a change that cannot be made by `CREATE TABLE IF NOT EXISTS`. This one can, so that trigger does not fire.

**The downgrade case is stated and not defended.** An older build that ingests a changed answer writes `relevance_label` and does not know the provenance table, so it leaves the model's row behind. The answer then reads as the model's, and `vespera label --auto` on this build would overwrite it. Nothing here promises to support running an older build over a working directory this one has written.

Three rules keep it honest:

1. **A model's answer never replaces a person's.** `--auto` asks about an entry only where nobody has answered it or a model has. An entry a person answered is left alone, and the command says how many.
2. **A person's answer replaces a model's, and removes its provenance row.** `RelevanceLabels.record`, which `vespera label` uses, clears the row when the answer is new or changed. An answer that is unchanged keeps its row: the label file shows the model's answers (below), and ingesting an unedited file must not turn a model's answers into a person's. This is ADR-169 §2's *new, unchanged, changed* applied to provenance.
3. **A model's answer replaces an earlier model's**, so a second labeller can relabel the sample, and the row names the one that answered last.

**`relevance-labels.yaml` shows it.** An entry a model set carries `labelledBy: <identity>` beside `relevant`. The file reader ignores the key, as it ignores `band` and `closestSeed`. A person overrules by editing `relevant` and running `vespera label`: a changed answer is the person's from then on, and the key is gone the next time the file is written. The relevance report step writes the key too, so a later `vespera run` keeps it.

**`profile.yaml` shows it through the floor's provenance** (§4), which is the profile's own free-text field (ADR-031). The floor's provenance says the rule that set it, how many labels it rests on, and how many of them a model set and which model.

### 4. The floor: a rule over the labels, and the rule is "lose no documentation"

**The floor is the lowest score among the labels that say relevant**, taken over the labels given under the embedder identity this run scores with, whoever set them. Every document scoring below it is `below-threshold`, as today: the floor decides on `score < floor`, so the lowest relevant document itself stays.

On GesPOS the operator chose 0.579, which loses no documentation, over 0.636, which makes the fewest mistakes. This rule is that choice stated as a rule, and it fits ADR-042's asymmetry: an over-block loses an archive without a trace, where an under-block leaves a document for a later, better-informed run to remove. The rule's own risk is the one ADR-088 already states: the floor is read off sixty documents, and a relevant document scoring below the lowest relevant one sampled is lost. The labelling page's table says what each candidate cut costs, and stays beside the rule.

**The model is not in the floor.** It only answered some of the questions the rule reads.

`vespera label --auto` writes the rule's value into `relevanceScoreFloor` **only when that key is unset or was last written by this rule**, which its provenance says. A value the operator wrote stays whatever the labels say. Where there is no relevant label there is no floor, and the key stays unset.

Alternatives weighed:

- **The fewest-mistakes cut.** Rejected as the rule, kept on the page: it loses documentation, which the operator said they would not do.
- **A margin below the lowest relevant score.** Rejected: any margin is an unmeasured number, and observe before enforce refuses it.
- **A model choosing the number.** Rejected: nothing about a number on a scale is a language question, and a number nobody can re-derive from the labels is one the provenance cannot defend.

### 5. Arrangement approval: it stays by hand

The operator approves the arrangement by hand, as ADR-107 has it: they read `arrangement.html` and write the arrangement's twelve-character name into `arrangementApproved`. Neither a model nor a rule approves it in this record. A model reading the arrangement would add a second reading of document titles to a check whose purpose is that someone looked at the shape, and there is no sample to measure its approvals against. A rule that approved would be a delegation dressed as a check, since what it could verify holds by construction. Whether to delegate approval at all is left under "What this does not decide".

### 6. The surface: `vespera label --auto`, with no hosted endpoint reachable

**`vespera label --auto [--root <path>]`** labels the sample with the local labeller, records the answers marked as its own, rewrites the label file so the operator can read and correct them, and sets the floor by §4's rule. It is an option on `label` and not a profile key, because `label` is the deliberate act (ADR-047) and a key would make an unattended `run` call a model.

- **`--root` falls back to `vespera.corpus-root`, and an invocation with neither refuses** (ADR-066), because the openings are read from the corpus and a root is never guessed.
- **`--auto` with a file refuses.** It works on the label file the last run wrote, and the combination is a usage error.
- **The labeller's model is its own setting: `vespera.label.model`**, defaulting to the model `spring.ai.ollama.chat.options.model` names, which is `qwen3:8b`.

**The refusal comes first and nothing is read before it.** `--auto` asks the labeller whether it may be used before it opens the label file, let alone a document. The labeller refuses, and `--auto` exits 1 naming the reason, asking no question and leaving the label file byte for byte as it was, in each of these cases:

1. **The endpoint is not this machine.** The endpoint tested is the one Spring AI will actually call, its `OllamaConnectionDetails` base URL, and not a property that may differ from it. Only `localhost`, `127.0.0.1` and `::1` count. An Ollama on another machine of the operator's own would be refused too, which the operator accepted.
2. **The model's tag ends in `cloud`.** Such a model is forwarded to a hosted service whatever the endpoint is.
3. **Ollama reports the model as remote.** `/api/show` names a forwarded model by `remote_host` and `remote_model`; the check reads both through `OllamaClient`. A model whose answer cannot be read is refused too, and is never assumed local. So is a model whose weights' digest cannot be read afterwards (§3), because an identity with no digest cannot be recorded. The field names are those of `ShowResponse` in Ollama's `api/types.go` at `v0.33.2`, read, not run against the image; the test pins them against a stub.

It takes the Ollama chat model and no other: `OllamaRelevanceLabeller` is constructed from an `OllamaChatModel`, and no property names a URL or a key for the labeller. The same refusal for generation and embedding is [#431](https://github.com/algernon28/vespera/issues/431) and is not decided here.

**`--auto` refuses while the label file holds answers not yet recorded.** An entry whose `relevant` is not blank and either has no recorded answer or differs from the recorded one is a person's, typed and not yet ingested. `--auto` exits 1 with `the label file holds N answer(s) not yet recorded; run vespera label first`, asks no question, and writes nothing. Without this, rewriting the file with the recorded answers would overwrite what the person typed.

**What the operator sees.** Every line that exists today is unchanged. `--auto` adds these, before the closing line, which `NextAction` writes as it does after any `vespera label`:

> labelled 52 of 60 question(s) with ollama:qwen3:8b;digest=...: 31 relevant, 21 not relevant; 6 left blank because no opening could be read or the model gave no answer; 2 already answered by a person and left as they are

> relevanceScoreFloor set to 0.579 by the rule that loses no documentation, over 41 relevant label(s) of which 31 were set by ollama:qwen3:8b;digest=...

or, where the key is not the rule's to write:

> relevanceScoreFloor left as it is: it was written by a person

or, where the rule wrote it and no label says relevant any more:

> relevanceScoreFloor left as it is: no label says relevant any more

or, where nothing relevant was labelled and the key is unset:

> relevanceScoreFloor left unset: no label says relevant

## For the operator to confirm

The operator decided each of these on 2026-10-05, on [#423](https://github.com/algernon28/vespera/issues/423). None is open.

1. **The model.** `qwen3:8b` is the first labeller because it is already served. The measurement protocol in §1 decides between it and a Laya head. *Decided: accepted.*
2. **The rule given to the model.** The operator's exact rule: documentation only; specifications, manuals, architecture, flow layouts and process descriptions are relevant; operational records, `.url` shortcuts, logs and screenshots are not, even on the seeds' subject. The model sees the winning seed's path only. *Decided.*
3. **A model that is not sure is blank.** *Decided: accepted.*
4. **Identity.** It includes the weights' digest, as ADR-114 records for generation. *Decided: changed from the draft, which recorded the name only.*
5. **Loopback only.** *Decided: accepted.* A model is also refused if its tag ends in `cloud` or Ollama reports it as remote (§6).
6. **The floor rule.** The lowest score among labels that say relevant, with no margin, whoever set the label. *Decided: accepted.*
7. **A floor the operator wrote is never overwritten.** *Decided: accepted.*
8. **Approval.** The operator approves by hand. *Decided: `--auto-approve` is left out and goes under "What this does not decide".*
9. **The surface.** `vespera label --auto`. *Decided: accepted.*
10. **Confirming a model's answer.** An answer left unchanged in the file stays marked as the model's. *Decided: accepted.*
11. **Unrecorded answers.** `--auto` refuses while the label file holds answers not yet recorded, and never overwrites an answer a person typed (§6). *Decided.*

## Consequences

- **No document reaches a hosted service on this path.** The labeller injects Ollama's chat model, takes no URL or key, and refuses a non-local endpoint, a `cloud` tag and a model Ollama reports as remote, before it reads a document. A test pins each.
- **Stages 3 to 6b move their run ids.** The change touches `pipeline` and `embedding`, which `StageModules` names in the identity of stages 3 to 6b, and a stage's implementation version is the last commit touching its modules ([ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md)). Stages 1 and 2 name neither and are spared. Three things follow for an existing working directory:
  - the arrangement's id changes, so an `arrangementApproved` written before it no longer names the arrangement and the gate closes again; the operator reads the new `arrangement.html` and writes the new name;
  - stage 6b generates again, under a run of its own;
  - labels survive, because they are keyed by path and seed set and no run names them (ADR-097, ADR-118), and the floor decides again on the next invocation.
- **The schema version does not move.** `relevance_label_provenance` is created by `schema.sql` on first start.
- **An older build over a newer working directory reads a model's label as a person's** and may leave its provenance row behind (§3). That is a downgrade, which nothing here supports.
- **`CONTEXT.md`'s *relevance label* entry says a person answers.** It is amended with this record to say a person or a named local model does. Its `_Avoid_` terms are unchanged.
- **The label file gains a key a reader ignores**, so a file written before this record reads as it did.
- **A second labeller is an implementation of one interface.** Neither the command nor the ingestion nor the file changes for it.

## What this does not decide

- **Which model labels in the end.** §1's measurement does.
- **Whether the Laya decision head can be ported to Java.** Its weights are to be read for that.
- **The scoring command that compares a labeller with the operator's checked labels,** which is built with the second implementation.
- **`--auto-approve`, or any delegation of the arrangement's approval.** It is not built and not decided. The arrangement is approved by hand.
- **The same refusal for generation and embedding.** [#431](https://github.com/algernon28/vespera/issues/431).
- **Whether a sample larger than sixty is wanted.** ADR-088's number stands.
