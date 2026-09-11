# ADR-099 — A stage's upstream run is looked up by stage and walk rather than recomputed, and two candidates stop the run

- **Date**: 2026-09-11
- **Status**: accepted

## Context

[ADR-048](0048-walk-and-run-identity.md) made a run id content-derived: the implementation version, the configuration consumed, the walk, and the sorted ids of the runs upstream. [ADR-089](0089-a-stages-run-names-the-immediately-preceding-stages-run-upstream-because-verdicts-are-cumulative.md) then fixed *which* run a stage names upstream. Neither says **how a stage obtains** that id, and the answer the code arrived at, four stages in a row, is that it recomputes it.

### The shape that is there now

Every run-minting bean re-derives the id of every stage before it, from that stage's known-fixed inputs. Ten `RunId.of(...)` call sites across four files — `ExtractionRun` one, `ContentCensusRun` two, `RedundancyRun` three, `SeedMeasurementRun` four — growing by one per stage added. `SeedMeasurementRun`'s first three calls are character-for-character what `RedundancyRun`'s constructor already computed moments earlier in the same job.

The duplication is the visible cost and the smaller one. Two others matter more:

- **The derivation had to be widened to be shared.** `ExtractionRun#configConsumed` and `RedundancyRun#configConsumed` are package-visible *only* so a later stage can call them, each with a javadoc explaining that an independently reimplemented copy of the JSON shape would drift. That is the design stating its own problem in prose, four times over.
- **Drift is silent at the point of the mistake.** A `configConsumed` shape that no longer matches what the earlier stage actually hashed mints an id naming no row. The `run_upstream` foreign key catches it, loudly, which is the single thing making the present arrangement survivable.

Raised by the two-axis review of [#104](https://github.com/algernon28/vespera/issues/104) and recorded as [#114](https://github.com/algernon28/vespera/issues/114). **Nothing minted today is wrong.** This is a maintenance cost being settled before it becomes a defect.

### What forced the decision was not the duplication

Recomputing stage 4's id requires the configuration of stages 1, 2 and 4, because the id folds in its ancestors. That is affordable exactly while every stage runs in the same invocation as its predecessors, which is true today and is not the intended shape of the tool: **starting from a stage whose predecessors already completed** is wanted, and a process starting at stage 5 does not hold stage 1's configuration. Under recomputation, obtaining stage 4's id means reconstructing the whole ancestry from a profile that may since have been edited. Under lookup, it is one query.

So the question is not which is tidier. It is whether a run's ancestry is **determined by its inputs** or **read off what was recorded** — and only the second survives a start part-way down the cascade.

### One bean already does it the other way

`ScoringRun` names the measurement run as its upstream and does not re-derive it, because gate 3 sits between the two and the measurement run is therefore always present. Its javadoc says so explicitly, contrasting itself with the stages that "re-derive from scratch". The exception exists because the reasoning for recomputation ran out, not because stage 5's scoring half is special.

## Decision

### `UpstreamRuns`, in `pipeline`, and it queries

**One collaborator answers "what is the run of stage *S* over this walk?"** It lives in `pipeline` because [ADR-040](0040-modules-are-capability-shaped-not-stage-shaped.md) forbids `ledger` from knowing what a stage is, and `pipeline` is the composition root and the only module that may name one. It is called `UpstreamRuns` and not `RunChain`: there is no chain object and nothing walks one — each stage asks for its own immediate predecessor, and [ADR-048](0048-walk-and-run-identity.md)'s transitivity carries the rest.

**Behind that seam the answer is a database lookup.** `Ledger` gains `List<RecordedRun> runsOf(String stage, WalkId)`, returning rows that carry the id, the configuration consumed and the implementation version — not bare ids, so that a refusal can say *why* it is refusing.

**Recomputation is removed, not kept as a cross-check.** A second derivation kept only to compare would reintroduce every drift hazard listed above while adding a failure mode of its own: two mechanisms disagreeing, with nothing to say which is right. The `run_upstream` foreign key remains the check, and it is checking the row that actually exists.

### No id changes

This is identity plumbing and nothing else. Every id comes out byte-identical, `RunUpstreamChainTest` passes untouched, and `ContentCensusRunTest` and `ExtractionRunTest` keep pinning the individual derivations of the runs being *minted*. A run id is still content-derived, still computed by `Ledger#startRun` from the four inputs. What changes is only how a stage learns the id of a run that already exists.

### Exactly one row, or the run stops

`UpstreamRuns` accepts one matching row. **Zero rows and two-or-more rows both stop the run**, and both are faults: the process throws and exits non-zero.

**Zero** is the condition the foreign key catches today, arriving earlier and with a better message.

**Two or more** is the case that separates lookup from recomputation, and it deserves its reasoning written down, because the glossary already licenses it. `CONTEXT.md` defines a run as "one execution of one stage under one configuration. **Minted when the configuration changes**" — so stage 4 run twice over one walk, at `boilerplateDocumentFrequencyFloor` 0.4 and then 0.6, produces two rows that are both legitimate, both correct and both permanent, since verdicts accumulate and never replace one another. Recomputation knows which one it means by construction. Lookup does not, and **nothing in the data says which one the operator meant** — not the configuration, and not recency either, since an operator may have run the second as an experiment and want the first.

**Refusing is therefore not a guard against a corrupt database.** It is a refusal to guess on the operator's behalf in a situation their own glossary calls normal.

### It is a fault and not a gate, because there is no value to supply

[ADR-080](0080-the-boilerplate-floor-is-a-gate-applied-before-signatures-are-computed.md)'s gate names a value the operator can set, after which the next invocation proceeds. Here there is no such value: no profile key, no command option, nothing writable that would resolve the ambiguity. A gate naming nothing is a fault wearing a gate's clothes, and would exit 0 on a job that cannot continue.

**The refusal names both runs with their `config_consumed`**, because the configurations are the only thing that tells the reader why there are two, and it states plainly that no option exists to choose between them rather than implying one does.

### When it becomes reachable, and what the operator can do

The condition **cannot arise today**: [ADR-055](0055-a-walk-is-resumed-under-its-own-id-until-it-finishes.md) resumes only an unfinished walk, so every ordinary invocation mints a fresh walk, and a fresh walk holds one run per stage. It becomes reachable the moment walks are reused — which is its own ticket, deliberately not this one.

Three recovery paths exist in principle, and the honest count of what is built is one:

1. **Run the pipeline again.** Available now, and the only one that is. A new walk carries no ambiguity. The cost is repeating the work.
2. **Name the run to continue from.** The real recovery, and unbuilt. **The walk-reuse ticket owns it**: whatever makes a walk reusable must ship the means of choosing, at which point this fault becomes a gate naming that means. Recording the obligation here is the point of stating it.
3. **Delete the superseded run.** `CONTEXT.md` already blesses this shape — "retuning a stage is a delete of that stage's rows and a re-run, never an update in place" — but no code anywhere implements it: `DELETE FROM` does not occur in `src/main`, and eighteen tables carry a `run_id` that `REFERENCES run (id)`. It is a hand-written transaction over eighteen tables, and offering it as a path would be describing a tool that does not exist.

**The refusal is pinned by a test that inserts two rows for one stage over one walk directly through `Ledger`**, since the pipeline cannot produce them. A test for a condition the code cannot yet reach is the point: the refusal must already be in place when walk reuse makes it reachable, so that reuse cannot quietly attach a stage to the wrong parent.

## Consequences

**Four javadoc blocks become false and are rewritten.** `ExtractionRun`, `ContentCensusRun`, `RedundancyRun` and `SeedMeasurementRun` each insist the id is recomputed and never read off any in-process state. The prohibition on in-process state stands — a stage must not depend on having run in the same invocation as its predecessor — and a query is not in-process state. But the reason given for it changes, and leaving the old prose would leave four statements of a superseded rationale in the files a reader reaches first.

**`configConsumed` narrows back to private** in `ExtractionRun` and `RedundancyRun`. Nothing outside the class computing it needs it, which is the only thing package-visibility was bought for.

**Adding a stage stops touching the stages after it.** That is the whole return: stage 6 asks `UpstreamRuns` for stage 5's run, and nothing in stages 1 to 4 is edited. The triangular growth stops at ten call sites.

**A new failure mode replaces an old one.** The drift hazard is gone; in its place, a run whose predecessor's row is missing fails at lookup rather than at insert. Both stop the job and the new message is better, but this is a change of shape rather than a pure deletion of risk.

**ADR-048 is amended in its reasoning, not in its rule.** Ids remain content-derived and remain byte-identical. What is now recorded is that a stage *learns* an ancestor's id by reading the ledger, so ancestry is a matter of what was recorded rather than of what can be recomputed. That is the sentence a future reader would otherwise have to infer from a query.

**One decision is deferred explicitly rather than by omission.** Walk reuse, a start part-way down the cascade, and the means of choosing among candidate upstream runs are one ticket and not this one. This record's obligation on that ticket — turn the fault into a gate by shipping the choice — is the thing most likely to be lost if it were left unwritten.
