# ADR-111 — A cluster fault is a row in `synthesis`, and a re-run under the same id repairs rather than regenerates

- **Date**: 2026-09-12
- **Status**: accepted
- **Amends**: [ADR-109](0109-a-citation-is-an-exemplar-ordinal-minted-for-one-call-and-the-check-is-that-it-is-in-range.md) — its *"nothing is retried or repaired"*, narrowed to **within one invocation**. Its refusal of regeneration stands where it was aimed; the section below says exactly what changes and what does not.
- **Amends**: [ADR-108](0108-6b-sends-one-exemplar-first-call-per-cluster-and-verifies-every-response.md) and ADR-109 — their shared *"a recorded fault, **not a stopped run**"*, narrowed to the **non-streak** case. One failed cluster still stops nothing; five in a row stop the step.
- **Amends**: [ADR-110](0110-pipeline-hands-synthesis-its-inputs-so-the-module-rule-gains-no-second-exception.md) — **the table count only.** It says `synthesis` owns two tables; it owns three. That record named the gap itself — *"It gives the fault record an obvious sibling. A cluster with a `synthesis_doc` row succeeded; a cluster without one is the case the fault ticket has to name"* — so this fills a deferral rather than reopening a closed set.
- **Rests on**: ADR-108 and ADR-109 (the four failures that fail a cluster), ADR-110 (cluster identity and the `synthesis_doc` table), [ADR-093](0093-logging-is-explicit-and-process-scoped-console-plus-rolling-file-per-item-and-per-step-at-info.md) (the row-or-log-line discriminator), [ADR-071](0071-doclings-invocation-contract-is-one-sync-call-a-local-timeout-and-two-consecutive-streak-breakers.md) (the streak breaker this copies).

## Context

Two records lean on the phrase "a recorded fault, not a stopped run" without saying where the record lands. ADR-108 fails a cluster on three verification failures — `prompt_eval_count` at the ceiling, `done_reason: "length"`, a schema violation — and ADR-109 adds a fourth, a citation out of range or prose carrying none at all. Each leaves a hole in the deliverable headed by the cluster's 6a label.

**The ledger has no shape for it.** A verdict is written against a file occurrence and a cluster is not one; [ADR-042](0042-ledger-owns-the-verdict-vocabulary-not-the-cascade.md)'s vocabulary is closed, and all seven blocking values name something wrong with a *document*. A cluster that failed generation has nothing wrong with its documents.

### The discriminator already exists, and it has to be read carefully

ADR-093 drew the line: *"if a fact is queried later as data about the corpus — a verdict, an anomaly, a metric — it belongs in Ledger/AnomalyLog/the metric tables […]. If a fact is about the pipeline's own execution — start/stop, sidecar health, retries, circuit-breaker state […] — it belongs in a log line, never a row."*

ADR-071's service-scope failures sit on the log side, and the reason matters here: **the sidecar did not answer**. That is transport. ADR-108's four failures are the opposite case — the sidecar answered, the response arrived, and it did not survive checking. That is a fact about content, and facts about content have never been log lines in this project.

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

### A re-run under an existing 6b run id is a repair pass, and what that does not mean

A 6b run id is content-derived ([ADR-048](0048-walk-and-run-identity.md)), so an **unchanged** re-run mints the *same* id — and generation is non-deterministic, so that re-run may succeed where it previously failed. Written naively, the same cluster would end up carrying a `cluster_fault` row and a `synthesis_doc` row under one run.

So 6b under an existing run id:

- **skips** every cluster already carrying a `synthesis_doc` row,
- **re-attempts** every cluster carrying a `cluster_fault` row,
- **deletes** the fault row when the re-attempt succeeds.

**This narrows ADR-109, and the narrowing is narrow.** That record refuses regeneration in terms worth quoting: *"A second call made because the first one's output failed a check is a model checking model output at one remove, which ADR-026 refuses; it also turns a bounded one-call-per-cluster cost into an unbounded one."* Both halves survive here, because two constraints hold:

- **The repair call is byte-identical to the original call.** The failed response, the fault `kind` and the `detail` are **never** inputs to the prompt. Nothing about the first attempt reaches the second, so no model is judging any model's output — the re-attempt is the same question asked again, not a correction round.
- **It is bounded at one call per cluster per invocation**, and it is a fresh invocation that a person started. ADR-109's unbounded cost is a retry loop *inside* a run, and there is none: within one invocation a failed cluster is still written off exactly as ADR-109 says.

What is left of the difference is that a cluster with no `synthesis_doc` row is ungenerated, and the next invocation generates what is missing — which is what it does for a cluster that was never attempted either.

A changed configuration mints a new run and writes a fresh row set beside the old one, which is [ADR-077](0077-a-regenerated-measurement-is-a-fresh-row-set-under-its-own-run-id.md) untouched: it forbids rewriting *an earlier run's* rows, and a repair pass touches only its own. **One wrinkle for a future reader**: ADR-077's supporting premise is that each invocation mints a new walk, so a re-run gets a distinct run id and a delete keyed by `run_id` would never match. That is true of the stage it was written for and not of 6b, where [ADR-103](0103-the-deliverable-is-a-markdown-tree-in-the-working-directory-one-tree-per-run-id.md) already states the opposite — *"a re-run that changed nothing writes the same path, because it mints the same id."* The rule generalises; the premise does not.

**This is the first row in this schema deleted on success.** Every other table here is insert-per-run. Nothing forbids it — the append-only rules in this project are about decisions (`docs/adr/README.md`) and about verdicts (`CONTEXT.md`, "Verdict"), and `relevance_label`'s never-rewritten rule is scoped to that table by name — but it is an exception to the shape of everything around it, and it is deliberate rather than incidental.

### The operator sees the deliverable, and no sixth report is written

Five HTML pages sit beside the database — `format-mix`, `confidence-distribution`, `seed-corpus-comparison`, `cluster-sizes`, `relevance-labelling` — and ADR-107 adds `arrangement.html`. **Generation gets none.**

Three of them inform a value the operator then supplies (the degeneracy floor, the seed gate, the relevance floor) and two are diagnostic only (`format-mix` points at a line a future floor might be drawn from; `cluster-sizes` shows whether a partition broke into singletons). So "no gate follows 6b" is not on its own a reason to write nothing — a diagnostic-only report is an established precedent here.

The reason is what each page is *for*: **every one of them surfaces a distribution that is invisible in anything else the run produces.** You cannot see the format mix, the confidence spread or the cluster-size spread by reading an output artifact, because the run's outputs at those stages are rows. A cluster fault is not like that. The deliverable is a tree of Markdown a person reads directly, and it carries every fault at full fidelity, cluster by cluster, under the label ADR-108 already gave the hole. A report would be a second rendering of what the primary artifact already says — and [ADR-098](0098-getting-from-a-folder-to-a-curated-archive-is-four-invocations-and-the-operator-is-told-the-next-value-not-the-stage.md) has the operator told the next value, where here there is none left to tell. **The deliverable is the report.**

Concretely: `index.md` lists every cluster, and a faulted one appears under its 6a label as an entry with **no link**, since there is no file to link to — which is what makes a hole legible rather than merely absent. The invocation's final line counts the faults beside the path it wrote.

### A consecutive-fault breaker, and ADR-047 stands

A run that faulted every cluster must not exit zero over an empty deliverable. The check is **not** an all-or-nothing test at the end: it is a streak, copying ADR-071's `ExtractionCircuitBreaker` — **five consecutive faults fail the step outright**, and any success resets the count.

This is what narrows ADR-108's and ADR-109's shared "not a stopped run" to the non-streak case, and the narrowing is the whole of it: one failed cluster stops nothing, as both records say.

**Why this does not touch [ADR-047](0047-the-pipeline-never-blocks.md).** That record terminates the pipeline at a *missing gate input*, not at a bad result, and a bad result still fails only its own cluster here. A streak is a different class of thing: four hundred clusters do not fail in a row by bad luck, they fail because the model, the word budget or the imposed schema is systematically wrong. ADR-071 already took exactly this reading for extraction — a per-item failure is a verdict, a streak is the instrument being broken — and this is that rule one stage along.

Five, matching ADR-071's service-scope count rather than its lower timeout count, because like that one this fires on a mix of kinds.

## Consequences

**The empty deliverable is unreachable, not merely detected.** The breaker fires on cluster five, not after four hundred model calls have been paid for. On this stage that is the difference between a minute and a night.

**A same-run-id re-run now reproduces the deliverable exactly**, which settles a question two records left open. ADR-103 declined to claim the bytes were idempotent and ADR-108 left determinism open because Ollama guarantees none. Neither needed the guarantee: a successful cluster is never regenerated, so the text is stable because it is **read**, not because it was reproduced. A *fresh* run id still makes no such promise, and is not expected to.

**`synthesis` ships with three tables, and its schema version is still 1.** `cluster`, `synthesis_doc` and `cluster_fault` all land in the same slice, before any database exists outside tests, so there is nothing to migrate and [ADR-059](0059-schema-version-is-one-row-per-module-checked-and-refused-independently.md)'s version starts where it starts.

**A fault row outlives the deliverable tree it explains.** Nothing cleans up either, and they are deleted independently — an operator who removes a deliverable directory keeps the rows saying what failed in it. That is the right direction: the rows are small and the explanation is the part worth keeping.

**Nothing here tells the operator a cluster failed while the run is going.** The log does, per ADR-093, and the deliverable does afterwards. The pipeline runs unattended through 6b ([ADR-101](0101-the-run-ends-at-the-generated-documents-there-is-no-publication-stage-and-no-publication-target.md), which supersedes ADR-035 but restates that in its own Context), so reporting at the end is what every other stage in this cascade already does.
