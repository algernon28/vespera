# ADR-110 — `pipeline` hands `synthesis` its inputs, so the module rule gains no second exception

- **Date**: 2026-09-12
- **Status**: accepted
- **Amends**: [ADR-108](0108-6b-sends-one-exemplar-first-call-per-cluster-and-verifies-every-response.md) — the generator's identity gains the model's artefact digest. ADR-108 read "no digest is available" off the *chat response*, which is true; `/api/tags` carries one, and the client that reads it already exists. Everything else in that record stands, including what 6b sends and how every response is verified.
- **Resolves what ADR-108 left open**: which module owns the generation call. The answer is that no Vespera module does, and the reason is in the Decision below.
- **Rests on**: [ADR-040](0040-modules-are-capability-shaped-not-stage-shaped.md) (the module rule), [ADR-041](0041-ledger-owns-identity-and-verdicts-capabilities-own-their-own-tables.md) (a capability owns its own tables), [ADR-100](0100-docling-reads-the-bytes-too-so-stage-2-sends-a-canonical-extension-derived-from-the-detected-format-and-nothing-else.md) (the one exception, and the bar it set), [ADR-105](0105-stage-6a-names-the-arrangement-stage-5-already-built-and-unattributed-is-struck.md) (what 6a writes), [ADR-106](0106-a-cluster-gets-a-derived-label-from-6a-and-a-generated-title-from-6b.md) (label and title), [ADR-107](0107-the-arrangement-gate-approves-a-named-6a-run-and-the-path-becomes-five-invocations.md) (the gate between the two stages).

## Context

`synthesis` is the eighth module and the only one still owed: a recorded name with no package behind it. Landing it runs straight into the rule that shapes every other module — **a capability module may depend on `ledger` and nothing else horizontal** — because 6a's input is the arrangement, which rests on `document_cluster`, and `document_cluster` is `embedding`'s table.

The rule has exactly one declared exception, and ADR-100 set the bar for earning a second: `extraction` names two of `corpus`'s enumerations, both closed, both holding no behaviour and depending on nothing, because Docling's pipeline choice is derived from them and putting the derivation anywhere else would mean maintaining a copy of the vocabulary. That is a narrow thing to have let through.

### There is a fourth option, and it is already the house pattern

The ticket that raised this offered three ways out: move the clusters into `ledger`, earn a second exception, or read the rows through something `ledger` already exposes. The fourth is what every other stage already does, and `ModuleBoundariesTest` states it as a claim in the report: values are "handed down as plain numbers and paths, never read inside the module that acts on them."

It is not a convention nobody enforces. `embedding` **cannot read `Profile`** — the seed folder, the embedding model and the relevance floor are read in `pipeline` and passed down. Stage 2 composes `extraction` and `similarity` with neither calling the other. Stage 5 composes `extraction` and `embedding` the same way. In each case `pipeline` is the composition root, which is what a composition root is for.

### The Ollama seam is not a seam

ADR-108 closed by leaving open "which module owns the call, since `OllamaClient` belongs to `embedding` and `synthesis` may not name it". That framing overstates the problem in two places.

`OllamaClient` is **not an embedding path** — its own javadoc says so. It reads `/api/tags` for the parts of an embedder identity that are not ours to choose ([ADR-091](0091-there-is-no-tokenizer-the-runtime-counts-tokens-and-the-embedder-identity-is-what-ollama-reports.md)), and nothing else; the embedding calls go through Spring AI's `EmbeddingModel`. And `spring.ai.model.chat: ollama` is already set in `application.yaml`, so `ChatModel` is an injectable bean today.

A Spring AI bean is a third-party dependency, not a Vespera module. `synthesis` injecting `ChatModel` is `ChunkEmbedder` injecting `EmbeddingModel`, one instrument along. There is no boundary to cross.

## Decision

### `pipeline` reads `document_cluster` and hands `synthesis` one cluster at a time

`synthesis` declares `allowedDependencies = "ledger"` like every other capability, and `ModuleBoundariesTest`'s exception map keeps its single entry.

The two rejected options were rejected on their own terms, not by preference:

- **Moving the clusters into `ledger`** fails ADR-041's split. `ledger` owns what exists and what was judged; `document_cluster` is a derivation under a configuration — ADR-077's rule applies to it, a re-run writes its own row set — and derivations live in the capability that computed them.
- **A second declared exception** fails ADR-100's bar by a wide margin. That exception bought two closed enumerations with no behaviour. `DocumentClusters` is a `JdbcTemplate`-backed component with four query methods over a table that changes shape as stage 5 changes.

The seam this forces on `synthesis` is the one ADR-108 wants anyway: **given this cluster's exemplars, produce a synthesis doc**. One call per cluster was already the unit of work, so passing a cluster's worth is not a concession to the rule.

### One module for both stages, and 6a is thin

`synthesis` covers 6a and 6b, as `docs/architecture.md` §1.4 already records. 6a comes out thin — under the decision above, `pipeline` gathers the Docling title, the relevance score and the occurrence facts that ADR-106's label rule needs, leaving `synthesis` holding a table and a fallback chain.

Thin is the right price. The alternative is 6a's rows living in `embedding` beside `document_cluster`, which puts the thing 6b reads inside the module 6b may not name, and recreates this decision one level down.

### `synthesis` owns two tables

**`cluster`** — 6a's row per cluster, keyed `(run_id, winning_seed_occurrence_id, cluster_ordinal)` where `run_id` is the **6a** run. `document_cluster`'s own vocabulary, so the join needs no translation, and **no surrogate id**: ADR-105 added a level to the arrangement, not a second naming scheme. The 6a row carries no column naming the stage-5 run it arranges, because `run_upstream` already holds that.

Beside the key it carries the ADR-106 label, and **`partition_order` and `cluster_order` as explicit integers, distinct from the identity ordinal**. Identity never moves; order is a judgement 6a makes. Conflating them would mean a re-ordering rewrote primary keys.

**`synthesis_doc`** — 6b's row per cluster, keyed by the 6b run plus the same natural key, holding the model's answer: the generated title and the generated prose.

Storing the answer is [ADR-039](0039-chroma-is-derived-sqlite-is-authoritative-for-vectors.md)'s shape one instrument along — SQLite authoritative, the projection disposable. Three things make it worth a table:

- **Generation is the most expensive call in the system**, and [ADR-103](0103-the-deliverable-is-a-markdown-tree-in-the-working-directory-one-tree-per-run-id.md)'s idempotency promise is about the *path*: an unchanged re-run writes the same directory, but re-calls the model and may get different prose. That tension is real and currently unanswerable; a stored answer is what makes it answerable without a schema change.
- **The file is a rendering, not a copy.** [ADR-109](0109-a-citation-is-an-exemplar-ordinal-minted-for-one-call-and-the-check-is-that-it-is-in-range.md) rewrites each `[n]` marker into a link at write time, and the membership list is composed there. The row holds what the model said; the file holds the document.
- **It gives the fault record an obvious sibling.** A cluster with a `synthesis_doc` row succeeded; a cluster without one is the case the fault ticket has to name.

The cost is hundreds of clusters of generated prose in SQLite, which is small beside the vectors already there.

### The generator's identity gains the artefact digest

ADR-108's "no digest is available" is a true statement about the chat response and a wrong conclusion about the identity. `/api/tags` carries the manifest digest, `OllamaClient.artefactOf` already reads it, and it is the same second call [ADR-084](0084-the-embedding-model-is-a-profile-gate-and-a-vector-carries-its-whole-embedder-identity.md) and ADR-091 already pay for the embedder.

Without it, a mutable tag re-pulled after upstream republishes mints **the same 6b run id over different weights** — precisely the failure ADR-084 exists to refuse. `synthesis` may not name `OllamaClient`, so `pipeline` calls it and passes the digest down as a plain string, which is this record's own rule applied to itself.

### The mechanical consequences

- `SynthesisSchema.VERSION = 1`, `MODULE = "synthesis"`, both tables under it ([ADR-059](0059-schema-version-is-one-row-per-module-checked-and-refused-independently.md)).
- `pipeline`'s `allowedDependencies` gains `"synthesis"`.
- Two new steps on the one job, taking it from thirteen to fifteen: **`arrangementStep`** then **`generationStep`**. `arrangement.html` is written inside the arrangement tasklet, following `cluster-sizes.html` inside `ClusteringTasklet` rather than the separate `relevanceReportStep`, because it is that step's own gate page and not a report over somebody else's run. `generationStep` gates on ADR-107's `arrangementApproved` and no-ops otherwise, the shape `EmbeddingModelGate`, `SeedGate` and `UsableSeedGate` already use three times.
- `STAGE` is **`"arrangement"`** and **`"generation"`** — capability-named with no ordinals, matching the six that exist.
- **Two runs, not one.** ADR-107 settled this in substance: `arrangementApproved` holds the first twelve characters of the 6a run id, and no 6b run is minted until approval lands. A single run id could not have been printed at the gate before generation happened.
- Implementation versions follow `ScoringRun`'s precedent of naming every module the pass invokes, not only those it writes to: **both passes name the same four** — `of("synthesis", "extraction", "embedding", "pipeline")`. 6a reads the Docling titles ADR-106's label rule needs; 6b reads the leading chunks ADR-108 sends and the `word_count` it budgets against, both of which live in `chunk_cache`, which is `extraction`'s table. The transitive argument — that 6b chains the 6a run upstream, so ADR-048 already folds `extraction` into its id — would have excused omitting it from 6a as well, and is not used there. No pom change is needed — `.mvn/scripts/implementation-versions.groovy` lists module directories, so `synthesis` gets its line the moment the package exists.
- **The recorded module list drops to eight.** `ModuleBoundariesTest.RECORDED_MODULES` still holds `publication`, which [ADR-101](0101-the-run-ends-at-the-generated-documents-there-is-no-publication-stage-and-no-publication-target.md) struck. The list is an allow-list, so the stale entry is invisible until someone creates the package it permits — which is exactly when it would do harm.

## Consequences

**The module rule survives the last module with one exception, not two.** That was the thing at risk here: a rule with two exceptions is a rule people start arguing with, and the second one would have been far weaker than the first.

**`pipeline` grows, and that is where the growth belongs.** Both new tasklets gather from three modules before calling into `synthesis`. A reader looking for what stage 6 reads finds it in the composition root, which is the one place in this codebase that is allowed to know.

**`synthesis` is testable without a database or a sidecar.** Its interface takes plain values and returns a document, so the label rule, the exemplar budget and the citation check are all exercisable in unit tests. That is the return on threading values down, and it is why the shortcut of widening `allowedDependencies` costs more than it looks.

**Determinism is now answerable, and still unanswered.** `synthesis_doc` means a re-run *could* rewrite the same deliverable from stored rows instead of re-calling the model. Whether it should is not settled here, and ADR-108 left it open for the same reason: no Ollama source claims a fixed seed reproduces output.

**A re-pulled generation tag re-generates the corpus, correctly and expensively.** Same trade ADR-084 took for embedding, and the same one that makes the identity worth composing at all.
