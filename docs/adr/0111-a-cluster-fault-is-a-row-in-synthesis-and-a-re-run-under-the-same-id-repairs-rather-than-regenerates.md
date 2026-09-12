# ADR-111 — A cluster fault is a row in `synthesis`, and a re-run under the same id repairs rather than regenerates

- **Date**: 2026-09-12
- **Status**: accepted
- **Amends**: none. [ADR-047](0047-the-pipeline-never-blocks.md) is left intact and the reasoning is below, because the consecutive-fault breaker looks at first glance like a bad result stopping a run, and it is not.
- **Rests on**: [ADR-108](0108-6b-sends-one-exemplar-first-call-per-cluster-and-verifies-every-response.md) and [ADR-109](0109-a-citation-is-an-exemplar-ordinal-minted-for-one-call-and-the-check-is-that-it-is-in-range.md) (the four failures that fail a cluster), [ADR-110](0110-pipeline-hands-synthesis-its-inputs-so-the-module-rule-gains-no-second-exception.md) (cluster identity and the `synthesis_doc` table), [ADR-093](0093-logging-is-explicit-and-process-scoped-console-plus-rolling-file-per-item-and-per-step-at-info.md) (the row-or-log-line discriminator), [ADR-071](0071-doclings-invocation-contract-is-one-sync-call-a-local-timeout-and-two-consecutive-streak-breakers.md) (the streak breaker this copies).

## Context

Two records lean on the phrase "a recorded fault, not a stopped run" without saying where the record lands. ADR-108 fails a cluster on three verification failures — `prompt_eval_count` at the ceiling, `done_reason: "length"`, a schema violation — and ADR-109 adds a fourth, a citation out of range or prose carrying none at all. Each leaves a hole in the deliverable headed by the cluster's 6a label.

**The ledger has no shape for it.** A verdict is written against a file occurrence and a cluster is not one; [ADR-042](0042-ledger-owns-the-verdict-vocabulary-not-the-cascade.md)'s vocabulary is closed, and all seven blocking values name something wrong with a *document*. A cluster that failed generation has nothing wrong with its documents.

### The discriminator already exists, and it has to be read carefully

ADR-093 drew the line: *"if a fact is queried later as data about the corpus — a verdict, an anomaly, a metric — it belongs in Ledger/AnomalyLog/the metric tables. If a fact is about the pipeline's own execution — start/stop, sidecar health, retries, circuit-breaker state — it belongs in a log line, never a row."*

ADR-071's service-scope failures sit on the log side, and the reason matters here: **the sidecar did not answer**. That is transport. ADR-108's four failures are the opposite case — the sidecar answered, the answer arrived, and it was **rejected on its merits**. That is a judgement about content, and judgements about content have never been log lines in this project.

### Two tables already exist for "a fault that is not a verdict"

Neither needed a new verdict kind, and both say so in their own schema comments:

- **`walk_anomaly`** — *"a walk anomaly is not a verdict, so it is not in the ledger"* — `corpus`'s table, keyed by walk, carrying `kind` and `detail`.
- **`unusable_seed`** — *"there is no verdict here and there never will be: every kind in the closed vocabulary (ADR-042) exists to remove a document from publication, and a seed is never published."*

The second sentence transfers exactly. **A cluster fault removes nothing from publication either.**

## Decision

### `synthesis` owns a `cluster_fault` table

Keyed `(run_id, winning_seed_occurrence_id, cluster_ordinal)` — ADR-110's natural key, with `run_id` being the **6b** run — plus a `kind` and a free-text `detail`.

`kind` is a **closed enumeration** owned by `synthesis`, four values today: the prompt-eval ceiling, the truncated answer, the schema violation, and the citation failure. Closed on `VerdictKind`'s own terms — adding a value means a pull request carrying an ADR — because an open registry is precisely where the meaning of a kind stops living in one place.

`detail` carries the number that failed: the count against the ceiling, the ordinal against `k`. A handled failure still has to keep its cause, which is the stage-4 retrospective this map carries in its Notes.

### A re-run under an existing 6b run id is a repair pass

This is the part that is not obvious, and it falls out of a collision. A 6b run id is content-derived ([ADR-048](0048-walk-and-run-identity.md)), so an **unchanged** re-run mints the *same* id — and generation is non-deterministic, so that re-run may succeed where it previously failed. Written naively, the same cluster would end up carrying a `cluster_fault` row and a `synthesis_doc` row under one run.

So 6b under an existing run id:

- **skips** every cluster already carrying a `synthesis_doc` row,
- **re-attempts** every cluster carrying a `cluster_fault` row,
- **deletes** the fault row when the re-attempt succeeds.

A changed configuration mints a new run and writes a fresh row set beside the old one, which is [ADR-077](0077-a-regenerated-measurement-is-a-fresh-row-set-under-its-own-run-id.md) untouched.

This is the return on ADR-110's `synthesis_doc` table landing one ticket earlier. Without it, repairing three clusters means re-paying for four hundred, and [ADR-103](0103-the-deliverable-is-a-markdown-tree-in-the-working-directory-one-tree-per-run-id.md)'s *"rewriting it is idempotent rather than destructive"* stays a claim about **paths** while the bytes underneath churn.

### The operator sees the deliverable, and no sixth report is written

Five HTML pages sit beside the database — `format-mix`, `confidence-distribution`, `seed-corpus-comparison`, `cluster-sizes`, `relevance-labelling` — and ADR-107 adds `arrangement.html`. **Generation gets none.**

Every one of those pages is read *before* the operator supplies something: a floor, a threshold, an approval. ADR-107 puts the arrangement gate **before** 6b, so by the time a cluster fails, the last gate has closed and there is no next value to inform — and [ADR-098](0098-getting-from-a-folder-to-a-curated-archive-is-four-invocations-and-the-operator-is-told-the-next-value-not-the-stage.md) has the operator told the next value, never the stage. There is no next value. **The deliverable is the report.**

Concretely: `index.md` lists the failed clusters among the rest, each under its 6a label, and the invocation's final line counts them beside the path it wrote. ADR-108 already gives the hole a heading and ADR-103 already makes `index.md` a mechanical listing, so this costs no new machinery.

### A consecutive-fault breaker, and ADR-047 stands

A run that faulted every cluster must not exit zero over an empty deliverable. The check is **not** an all-or-nothing test at the end: it is a streak, copying ADR-071's `ExtractionCircuitBreaker` — **five consecutive faults fail the step outright**, and any success resets the count.

**Why this does not touch ADR-047.** That record terminates the pipeline at a *missing gate input*, not at a bad result, and a bad result still fails only its own cluster here. A streak is a different class of thing: four hundred clusters do not fail in a row by bad luck, they fail because the model, the word budget or the imposed schema is systematically wrong. ADR-071 already took exactly this reading for extraction — a per-item failure is a verdict, a streak is the instrument being broken — and this is that rule one stage along.

Five, matching ADR-071's service-scope count rather than its lower timeout count, because like that one this fires on a mix of kinds.

## Consequences

**The empty deliverable is unreachable, not merely detected.** The breaker fires on cluster five, not after four hundred model calls have been paid for. On this stage that is the difference between a minute and a night.

**A same-run-id re-run now reproduces the deliverable exactly**, which settles a question two records left open. ADR-103 declined to claim the bytes were idempotent and ADR-108 left determinism open because Ollama guarantees none. Neither needed the guarantee: a successful cluster is never regenerated, so the text is stable because it is **read**, not because it was reproduced. A *fresh* run id still makes no such promise, and is not expected to.

**`synthesis` ships with three tables, and its schema version is still 1.** `cluster`, `synthesis_doc` and `cluster_fault` all land in the same slice, before any database exists outside tests, so there is nothing to migrate and [ADR-059](0059-schema-version-is-one-row-per-module-checked-and-refused-independently.md)'s version starts where it starts.

**A fault row outlives the deliverable tree it explains.** Nothing cleans up either, and they are deleted independently — an operator who removes a deliverable directory keeps the rows saying what failed in it. That is the right direction: the rows are small and the explanation is the part worth keeping.

**Nothing here tells the operator a cluster failed while the run is going.** The log does, per ADR-093, and the deliverable does afterwards. A stage that ran unattended for a night (ADR-035) reports at the end, which is what every other stage in this cascade already does.
