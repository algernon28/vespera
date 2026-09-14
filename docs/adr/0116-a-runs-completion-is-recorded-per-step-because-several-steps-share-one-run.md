# ADR-116 — A run's completion is recorded per step, because several steps share one run

- **Date**: 2026-09-14
- **Status**: accepted
- **Amends**: [ADR-115](0115-a-re-walk-that-observed-nothing-new-is-discarded-and-a-run-is-continued-under-its-own-id.md) — **its run half's completion rule only**, and only in what the rule is keyed on. *"A step … marks it finished when it completes"* reads as though one step owned one run. Three runs in this system are shared by more than one step, so the flag as that record described it says "all of this run's work is recorded" on the strength of one step of five. Both of its rules survive word for word with *step* where they said *run*: a step whose work is recorded does nothing, and a step whose work is not recorded discards its own rows under that run and does the work again. Its walk half, its `startRun` continuation rule, its `verdict` uniqueness and its 6b exception are untouched.
- **Rests on**: [ADR-048](0048-walk-and-run-identity.md) (run identity is a stage's, and nothing here moves it), [ADR-111](0111-a-cluster-fault-is-a-row-in-synthesis-and-a-re-run-under-the-same-id-repairs-rather-than-regenerates.md) (the one place completion was already finer-grained than a run), [ADR-087](0087-clusters-are-modularity-communities-over-a-k-nearest-neighbour-graph-built-in-blocks.md) and [ADR-086](0086-seed-corpus-mismatch-is-measured-before-the-model-gate-and-reported-never-enforced.md) (which put four steps inside stage 5's scoring run in the first place), [ADR-080](0080-the-boilerplate-floor-is-a-gate-applied-before-signatures-are-computed.md) (a gated step mints no run, so it records no completion either), [ADR-059](0059-schema-version-is-one-row-per-module-checked-and-refused-independently.md) (the schema move this costs).
- **Depends on**: [ADR-117](0117-the-relevance-floor-joins-the-scoring-runs-identity-so-a-changed-threshold-is-a-different-run.md). Recording a step's completion is only safe where every input that step consumes is folded into the run's identity. ADR-117 names the one input in this system that is not, and it is consumed by a step of the most-shared run of all.

## Context

### Measured: fourteen working steps compose onto eight runs, and three runs are shared

The job is fifteen steps long. One of them — census — owns a walk rather than a run. The other fourteen write under eight run identities:

| Run | Steps writing under it |
|---|---|
| stage 1 | `byte-level-reduction` |
| `extraction` | `extraction` |
| `content-census` | `content-census` |
| `content-redundancy` (`RedundancyRun`) | `redundancy-signature`, `content-redundancy` |
| seed measurement (`SeedMeasurementRun`) | `seed-extraction`, `seed-corpus-comparison` |
| `embedding-scoring` (`ScoringRun`) | `embedding-scoring`, `relevance-scoring`, `relevance-floor`, `clustering`, `relevance-report` |
| `arrangement` | `arrangement` |
| `generation` | `generation` |

Three runs are shared, by two, two and five steps. Nothing about that is accidental: [ADR-086](0086-seed-corpus-mismatch-is-measured-before-the-model-gate-and-reported-never-enforced.md) gave stage 5 two runs rather than six, [ADR-087](0087-clusters-are-modularity-communities-over-a-k-nearest-neighbour-graph-built-in-blocks.md) put clustering *"in stage 5's scoring run … because it needs the vectors, and those need the model"*, and both `RedundancyRun` and `ScoringRun` say in their own javadoc that one bean serves every step of their stage. `CONTEXT.md` says the same thing from the other end: a **run** is *"one execution of one stage under one configuration"*, and a **stage** is one step of the *cascade*, not one step of a batch job.

So a run is a stage's identity and always was. What ADR-115 added was a flag whose only consumer is a single step deciding whether to do its own work — and that is a narrower thing than a stage.

### Reproduced: the first of five steps tells the other four they are done

`./mvnw test` on `claude/191-implement`, second invocation over an unchanged corpus, with `relevance-scoring` and `seed-corpus-comparison` wired to ADR-115's rule and their step-mates not:

```
Executing step: [seed-extraction]
  A PRIMARY KEY constraint failed (UNIQUE constraint failed:
      extraction_metric.occurrence_id, extraction_metric.run_id)
Executing step: [seed-corpus-comparison]
  Stage 5b (seed/corpus comparison) was already recorded under run e5abeadb…
Executing step: [relevance-scoring]
  Stage 5d (relevance scoring) was already recorded under run 7da31795…
Executing step: [clustering]
  Encountered an error executing step clustering in job vespera
  A PRIMARY KEY constraint failed (UNIQUE constraint failed:
      document_cluster.occurrence_id, document_cluster.run_id)
```

Both halves of the defect are in that trace. `seed-corpus-comparison` marked the **shared** measurement run finished on the first invocation, and `seed-extraction`, which writes under the same run, is one wiring away from being told its work is recorded when it is not. `clustering` is the same hazard one step later and one stage along, already fatal.

The third case is the plainest: `redundancy-signature` collides on `minhash_signature` for want of any rule at all, and wiring it to the flag would make it skip on the strength of `content-redundancy` — the step *after* it.

### Three shapes were considered, and what decides between them is which way each fails

**One run per step.** Abolishes the sharing by abolishing the runs. It moves every id it touches — `ArrangementRun` names the scoring run upstream, `SeedMeasurementRun` re-derives stage 4's, [ADR-089](0089-a-stages-run-names-the-immediately-preceding-stages-run-upstream-because-verdicts-are-cumulative.md) chains them — and it re-decides ADR-086, ADR-087 and `CONTEXT.md`'s Run entry to fix a bookkeeping problem. A stage remains one execution under one configuration whatever its composition root does with threads; the run is not the thing that is wrong here.

**Only the last step of a run marks it finished.** Keeps the column and needs no schema move. It fails in the direction that cannot be noticed: add a sixth step to stage 5 after `relevance-report`, forget to move the marker, and that step is skipped on every invocation after the first, silently and forever. A step that runs when it need not costs time; a step that never runs again costs the archive.

**Ask the rows instead of recording a flag.** ADR-111 already does something like it for 6b — a cluster carrying a `synthesis_doc` row is not re-attempted — so the shape has a precedent. It does not generalise: `relevance-floor` writes no row at all while the threshold is unset, which is [ADR-088](0088-the-relevance-threshold-is-read-off-a-stratified-sample-of-sixty-labels-and-an-unset-floor-does-not-stop-the-run.md)'s whole point, and *no rows* is then indistinguishable from *never ran*. That is the same argument ADR-115 made for spending a column rather than inferring: a partial thing that looks complete is the failure worth spending a column on.

## Decision

### Completion is a row per step, not a flag per run

**`finished_step` holds one row per `(run_id, step)`.** A row means: this step's work under this run is all recorded. No row means it is not, and that is the honest answer for a step that has never run, a step that failed, and a step nobody has written yet alike.

**`run.finished` is dropped.** A boolean on `run` would now be a summary of the rows in `finished_step`, and ADR-115 already refused a summary of something it also records — *"comparing it as well would state one fact twice, which is how two statements of one thing drift apart."* Nothing asks whether a whole stage is done: the only consumer of completion is a step asking about itself.

**A step is named by its own step name** — the string the job already gives it, the one in every log line above, and the one that equals `run.stage` for the five runs with a single step under them. `ledger` therefore holds a string `pipeline` owns, exactly as `run.stage` already is: the run table has never derived that value, only stored it.

### The two rules of ADR-115, re-keyed

- **A step whose completion is recorded does no work and writes no rows.** ADR-048's stated purpose, now at the granularity of the thing that would do the work.
- **A step whose completion is not recorded discards its own rows under that run and does the work again.** Its own rows: the rows that step writes, under that run id, and no others. This is the half nothing implements today, and it is the direct cause of both primary-key violations above.
- **The row is written on success only**, by the step that did the work, after it has done it — `RunCompletion`'s existing rule, unchanged and now applied to every step that writes under a run rather than to one step per stage.

**A step resuming alone reads what its step-mates already committed**, so partial resumption inside a stage needs no special case. Stages never call each other; they read and write only through the ledger, and a step of one stage is no different — `clustering` reads the scores `relevance-scoring` committed and the survivors `relevance-floor` left, whether those were written in this invocation or the last one.

### 6b keeps ADR-111's finer rule, and gains nothing from this one

Generation is a single-step stage, so its `finished_step` row is one row about one step — and it is written only when **every cluster of the approved arrangement carries a `synthesis_doc` row**, which is ADR-115's clause unchanged. A run that stopped on the consecutive-fault breaker or completed with faults records no completion, so the next invocation is the repair pass ADR-111 describes, and a faulted cluster is never skipped forever.

### This rule is safe only where a run's identity names everything its steps consume

Recording that a step's work is done is a claim that re-deriving the same identity means re-deriving the same work. That claim is exactly as strong as the run id, and one input in this system is consumed by a step whose run id does not name it: the relevance floor, read by `relevance-floor`, which writes under `ScoringRun`. [ADR-117](0117-the-relevance-floor-joins-the-scoring-runs-identity-so-a-changed-threshold-is-a-different-run.md) closes it, and it has to land with this or before it — a skip rule over an identity that cannot see a changed threshold is how an operator's new number is silently never applied.

## Consequences

**`ledger`'s schema version moves again, 3 to 4, inside one slice.** `run.finished` is dropped and `finished_step` added, and `CREATE TABLE IF NOT EXISTS` replay neither drops a column nor notices a new table's absence from an old database. ADR-059's per-module version is what catches it and its manual path — delete the module's tables and re-run census — applies, for the second time in this slice. Two moves in one slice is worth stating rather than hiding: the first was written under a rule this record found to be short.

**Adding a step to a stage is safe by default.** A new step has no row in `finished_step`, so it does its work. That is the direction this mechanism has to fail in, and it is the whole reason the row is keyed by the step rather than the run.

**A renamed step re-does its work once.** Its old completion row names a step nothing asks about, and the new name has no row, so the step discards its own rows under that run and runs again. Harmless, self-correcting after one invocation, and cheaper than an identity that tries to survive a rename.

**Eight steps gain a rule they do not have today**, and none of them by re-deciding anything: `redundancy-signature`, `content-redundancy`, `seed-extraction`, `embedding-scoring`, `relevance-floor`, `clustering`, `relevance-report`, `arrangement`. Each asks about itself, skips if its own work is recorded, and deletes its own rows first if it is not.

**`embedding-scoring` records completion and deletes nothing**, because it writes no row keyed by a run: vectors are keyed by chunk and embedder identity ([ADR-085](0085-vectors-live-in-sqlite-and-the-pairwise-matrix-is-never-materialised.md)), outside the run, on purpose. That is not an exception to the rule — it is the rule over an empty set of tables — and it is also why re-running stage 5 under a new identity costs recomputation rather than a second call to Ollama.

**A step that is gated records nothing**, because a gated step never mints the run at all (ADR-080's `ObjectProvider` rule). Opening the gate later therefore finds no completion and does the work, which is what an operator who has just supplied a value expects.

**The failures a step-mate used to cause are now the tests that defend it.** Every invocation test in the tree that invokes twice meets this rule, and two of the three that fail today fail inside a shared run.

## What this does not decide

**Which tables each step calls its own.** That is a fact about each step, discoverable from the step, and writing the list into a decision record would fix it here and let the code drift from it. The rule names the shape — this step's rows, this run's id — and the implementation names the tables.

**Whether a completion row should record when it was written.** Nothing asks. A timestamp here would be a second answer to "when was this measured", which `refreshedAt` already gives for the thing an operator actually reads (ADR-075).

**Whether a step should ever be forced to run again.** Unchanged from ADR-115: there is no flag, and the lever is changing something the identity is derived from.
