# ADR-111 — A cluster fault is a row in `synthesis`, and a re-run under the same id repairs rather than regenerates

- **Date**: 2026-09-12
- **Status**: accepted
- **Amends**: [ADR-109](0109-a-citation-is-an-exemplar-ordinal-minted-for-one-call-and-the-check-is-that-it-is-in-range.md) — its *"nothing is **retried**"*, narrowed to **within one invocation**. Its **`Not stripped`** bullet is untouched and stands whole — that clause uses the word *repaired* for deleting an offending citation marker, which nothing here does. The section below says what changes and what does not.
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

A 6b run id is content-derived ([ADR-048](0048-walk-and-run-identity.md)), so an **unchanged** re-run mints the *same* id and finds rows already written beneath it.

**What the pass is chiefly for is resumption, not retrying.** The breaker below stops the step on a fifth consecutive fault, and a machine can die on hour nine of a corpus-scale run; either way the invocation ends with most clusters carrying **no row at all**. Those clusters were never attempted, and generating them is not a retry of anything. That case alone justifies the pass, and it does not depend on determinism.

Re-attempting a *faulted* cluster rides along on the same mechanism, and its value does depend on a question this project has not settled. **ADR-108 leaves determinism open** — *"no Ollama source claims a fixed seed reproduces output"* — and [ADR-103](0103-the-deliverable-is-a-markdown-tree-in-the-working-directory-one-tree-per-run-id.md) declines to claim the bytes are reproducible. So this record does not assert the opposite either, and takes the worse branch deliberately: **if generation turns out to be deterministic, re-attempting a faulted cluster is a no-op that costs one call per faulted cluster per invocation, and that is accepted.** The seed cannot be varied to escape it — ADR-108 puts the options sent into the generator identity and ADR-110 folds that identity into the run id, so a different seed is a different run, which regenerates every cluster rather than the faulted few. The cost is bounded by construction: the breaker caps how many clusters can fault before the step stops.

Written naively, a cluster that succeeded on re-attempt would end up carrying a `cluster_fault` row and a `synthesis_doc` row under one run. So the pass is specified rather than left to fall out.

So 6b under an existing run id:

- **skips** every cluster already carrying a `synthesis_doc` row,
- **re-attempts** every cluster carrying a `cluster_fault` row,
- **deletes** the fault row when the re-attempt succeeds.

**This narrows ADR-109, and the narrowing is narrow.** That record refuses regeneration in terms worth quoting: *"A second call made because the first one's output failed a check is a model checking model output at one remove, which ADR-026 refuses; it also turns a bounded one-call-per-cluster cost into an unbounded one."* Both halves survive here, because two constraints hold:

- **The repair call is byte-identical to the original call.** The failed response, the fault `kind` and the `detail` are **never** inputs to the prompt. Nothing about the first attempt reaches the second, so no model is judging any model's output — the re-attempt is the same question asked again, not a correction round.
- **Each pass is strictly smaller than the last**, because every cluster that succeeded is skipped. ADR-109's unbounded cost is a retry loop *inside* a run, which diverges; a sequence of invocations converges, and that is the opposite property. Repairing three clusters never re-pays for four hundred. Within one invocation a faulted cluster is still written off, as ADR-109's retry clause says — with the one exception the breaker introduces, declared two sections down.

What is left of the difference is that a cluster with no `synthesis_doc` row is ungenerated, and the next invocation generates what is missing — which is what it does for a cluster that was never attempted either.

A changed configuration mints a new run and writes a fresh row set beside the old one, which is [ADR-077](0077-a-regenerated-measurement-is-a-fresh-row-set-under-its-own-run-id.md) untouched: it forbids rewriting *an earlier run's* rows, and a repair pass touches only its own. **One wrinkle for a future reader**: ADR-077's supporting premise is that each invocation mints a new walk, so a re-run gets a distinct run id and a delete keyed by `run_id` would never match. That is true of the stage it was written for and not of 6b, where [ADR-103](0103-the-deliverable-is-a-markdown-tree-in-the-working-directory-one-tree-per-run-id.md) already states the opposite — *"a re-run that changed nothing writes the same path, because it mints the same id."* The rule generalises; the premise does not.

**This is the first row in this schema deleted on success.** Every other table here is insert-per-run. Nothing forbids it — the append-only rules in this project are about decisions (`docs/adr/README.md`) and about verdicts (`CONTEXT.md`, "Verdict"), and `relevance_label`'s never-rewritten rule is scoped to that table by name — but it is an exception to the shape of everything around it, and it is deliberate rather than incidental.

### The operator sees the deliverable, and no sixth report is written

Five HTML pages sit beside the database — `format-mix`, `confidence-distribution`, `seed-corpus-comparison`, `cluster-sizes`, `relevance-labelling` — and ADR-107 adds `arrangement.html`. **Generation gets none.**

Three of them inform a value the operator then supplies (the degeneracy floor, the seed gate, the relevance floor) and two are diagnostic only (`format-mix` points at a line a future floor might be drawn from; `cluster-sizes` shows whether a partition broke into singletons). So "no gate follows 6b" is not on its own a reason to write nothing — a diagnostic-only report is an established precedent here.

The reason is **temporal**, and `ClusteringTasklet` already states it for its own page: the size distribution is written *"because the cluster count is not chosen and therefore cannot be known in advance: whoever builds the page tree above these clusters needs to see what shape they came out in **before they build it**."* Every one of the five is written at a stage where **no readable artifact yet exists** — the run's output there is rows, so a page is the only way to see anything at all. A cluster fault arises at the stage that *produces* the readable artifact. The deliverable is a tree of Markdown a person reads directly, carrying every fault at full fidelity, cluster by cluster, under the label ADR-108 already gave the hole. Nothing has to be rendered for it to be seen. And [ADR-098](0098-getting-from-a-folder-to-a-curated-archive-is-four-invocations-and-the-operator-is-told-the-next-value-not-the-stage.md) has the operator told the next value, where here there is none left to tell. **The deliverable is the report.**

Concretely: `index.md` lists every cluster, and a faulted one appears under its 6a label as an entry with **no link**, since there is no file to link to — which is what makes a hole legible rather than merely absent. The invocation's final line counts the faults beside the path it wrote.

### A consecutive-fault breaker, and ADR-047 stands

A run that faulted every cluster must not exit zero over an empty deliverable. The check is **not** an all-or-nothing test at the end: it is a streak, copying ADR-071's `ExtractionCircuitBreaker` — **five consecutive faults fail the step outright**, and any success resets the count.

This is what narrows ADR-108's and ADR-109's shared "not a stopped run" to the non-streak case, and the narrowing is the whole of it: one failed cluster stops nothing, as both records say.

**Why this does not touch [ADR-047](0047-the-pipeline-never-blocks.md).** That record terminates the pipeline at a *missing gate input*, not at a bad result, and a bad result still fails only its own cluster here. A streak is a different class of thing: four hundred clusters do not fail in a row by bad luck, they fail because the model, the word budget or the imposed schema is systematically wrong. ADR-071 already took exactly this reading for extraction — a per-item failure is a verdict, a streak is the instrument being broken — and this is that rule one stage along.

Five, matching ADR-071's service-scope count rather than its lower timeout count, because like that one this fires on a mix of kinds.

## Consequences

**The empty deliverable is unreachable, not merely detected.** The breaker fires on cluster five, not after four hundred model calls have been paid for. On this stage that is the difference between a minute and a night.

**A same-run-id re-run now reproduces the deliverable exactly**, which settles a question two records left open. ADR-103 declined to claim the bytes were idempotent and ADR-108 left determinism open because Ollama guarantees none. Neither needed the guarantee: a successful cluster is never regenerated, so the text is stable because it is **read**, not because it was reproduced. A *fresh* run id still makes no such promise, and is not expected to.

**`synthesis` ships with three tables.** `cluster`, `synthesis_doc` and `cluster_fault` landed one per slice rather than together as this record assumed, so the schema version moved with each — 1, then 2, then 3 — rather than starting and staying at 1. [ADR-059](0059-schema-version-is-one-row-per-module-checked-and-refused-independently.md)'s rule is unaffected: each move is a real table added and the version tracks it correctly.

**The breaker fails the step without throwing, which [#184](https://github.com/algernon28/vespera/issues/184) found it had to.** Everything stage 6b writes is inside the step's own transaction, so an exception leaving `GenerationTasklet.execute` rolls back the very rows that say why it stopped — measured, not argued: with the breaker throwing, the invocation that turned five answers down left **zero** fault rows behind, where this record's whole point is that the operator is handed an account of what the model answered. Writing them through a nested transaction instead is not open either, and that was measured too, against the shipped configuration: **SQLite locks the database file for a write**, so a second connection opening its own transaction while the step's holds that lock fails with `[SQLITE_BUSY] The database file is locked (database is locked)` after SQLite's own busy timeout — **three seconds**, the driver's default, which nothing in this project sets. `journal_mode` is `delete` rather than WAL, so there is no writer concurrency to fall back on. Hikari's five-minute `connection-timeout` is never reached and is not what governs this: it times the wait for a connection *from the pool*, and the pool hands one over immediately. So the breaker records `BatchStatus.FAILED` on the step's own execution and returns: the transaction commits, Spring Batch only ever upgrades a step's status afterwards and never lowers it, the job ends unsuccessfully and the invocation exits non-zero. ADR-071's breaker one stage back can throw because a chunk-oriented step has already committed the chunks before it.

**What that rests on, named exactly, and what guards it.** `AbstractStep.execute` ends a step that returned normally with `stepExecution.upgradeStatus(BatchStatus.COMPLETED)`, and `BatchStatus.upgradeTo` returns the more severe of the two whenever either is more severe than `STARTED` — `FAILED` is, so it stands. That is documented behaviour of `BatchStatus` and an internal call in `AbstractStep`, and the second half of it is not ours. It is therefore not defended by a comment but by an invocation-level pin: `GenerationBreakerInvocationTest` claims a **non-zero exit code from the command**, not a `BatchStatus` on a step execution, so a future Spring Batch that lowered the status would fail that claim in the terms the operator actually experiences rather than pass a test written against the mechanism.

**Only an answer that was believed clears the count.** Two things in the loop are neither: a cluster skipped because an earlier invocation of the same run already wrote it ([ADR-115](0115-a-re-walk-that-observed-nothing-new-is-discarded-and-a-run-is-continued-under-its-own-id.md), [ADR-116](0116-a-runs-completion-is-recorded-per-step-because-several-steps-share-one-run.md)), and a cluster no call could be made for at all ([ADR-121](0121-a-window-with-no-room-for-documents-is-refused-and-no-call-is-ever-made-with-none.md)). Neither adds to the streak and neither clears it, because **no call was made**: nothing was learned there about the model, the word budget or the imposed schema, in either direction. Clearing on them would be the worse error of the two — an archive holding a scattering of unsendable clusters, or a repair pass walking past clusters it had already written, would never reach five in a row however wrong the model was, which is exactly the case the breaker exists for.

**The streak is one invocation's, not the run's.** It is counted in the loop and dropped when the step ends, so an invocation that stopped on five leaves nothing behind that could stop the next one before it has made a call. This follows from what the count is evidence of: five turned down *in a row, just now* says this model under this configuration is answering wrongly right now, where five fault rows standing from an earlier invocation may be exactly what the operator has just changed the model, the window or the allowance to repair. The repair pass is ADR-121's and #185's, and it must be free to make its own five calls before anything concludes anything about it.

**A fault row outlives the deliverable tree it explains.** Nothing cleans up either, and they are deleted independently — an operator who removes a deliverable directory keeps the rows saying what failed in it. That is the right direction: the rows are small and the explanation is the part worth keeping.

**Nothing here tells the operator a cluster failed while the run is going.** The log does, per ADR-093, and the deliverable does afterwards. The pipeline runs unattended through 6b ([ADR-101](0101-the-run-ends-at-the-generated-documents-there-is-no-publication-stage-and-no-publication-target.md), which supersedes ADR-035 but restates that in its own Context), so reporting at the end is what every other stage in this cascade already does.
