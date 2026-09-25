# ADR-154 — A stage reads the upstream run this invocation arrived at, and an approval opens only this invocation's arrangement

- **Date**: 2026-09-25
- **Status**: accepted
- **Amends**: [ADR-099](0099-a-stages-upstream-run-is-looked-up-by-stage-and-walk-not-recomputed-and-two-candidates-stop-the-run.md), in its rule and in two of its reasons. A stage no longer looks its upstream run up over the walk; it takes the run of the stage before it that this invocation minted or continued. The "two or more stop the run" refusal goes with the lookup. The reason at its line 23 (a process starting part-way down the cascade would not hold the earlier ids) and the prohibition its Consequences keep ("a stage must not depend on having run in the same invocation as its predecessor") no longer hold. Its first reason, that recomputing an ancestor's id drifts silently, stands: nothing here recomputes anything. Its obligation at lines 63–68 and 85 is discharged in §1.
- **Amends**: [ADR-107](0107-the-arrangement-gate-approves-a-named-6a-run-and-the-path-becomes-five-invocations.md), in how the approval is matched. It was matched against every arrangement of the walk, and is now matched against the one arrangement this invocation made or continued. "A prefix matching two stops the run" goes, because there is only one arrangement to match. Its twelve characters, its gate shape and its five invocations stand.
- **Amends**: [ADR-115](0115-a-re-walk-that-observed-nothing-new-is-discarded-and-a-run-is-continued-under-its-own-id.md), for one page. "A skipped step writes no report" stops being true of `arrangement.html` (§2).
- **Amends**: [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md), in its Consequences only. It priced a new build in compute. §3 records what a build costs an operator part-way through an archive, now that walks are reused.
- **Rests on**: [ADR-115](0115-a-re-walk-that-observed-nothing-new-is-discarded-and-a-run-is-continued-under-its-own-id.md) and [ADR-116](0116-a-runs-completion-is-recorded-per-step-because-several-steps-share-one-run.md) (every invocation passes through every step, and a step whose work is recorded still derives its run's id), [ADR-080](0080-the-boilerplate-floor-is-a-gate-applied-before-signatures-are-computed.md) (a gated step mints no run), [ADR-117](0117-the-relevance-floor-joins-the-scoring-runs-identity-so-a-changed-threshold-is-a-different-run.md) (a changed floor re-arranges the documents), [ADR-048](0048-walk-and-run-identity.md) (a run id is a total function of its inputs).
- **Settles** [#290](https://github.com/algernon28/vespera/issues/290) and [#296](https://github.com/algernon28/vespera/issues/296).

## Context

### 1. The fault ADR-099 said could not arise has arisen

ADR-099 made each stage look up the run of the stage before it over the walk, and stop the run if it found two. It said this "cannot arise today" because every invocation minted a fresh walk. It gave the remedy to whichever change made walks reusable: "whatever makes a walk reusable must ship the means of choosing, at which point this fault becomes a gate."

ADR-115 made walks reusable and left ADR-099's rule alone. From then on, over an unchanged archive, anything that gives one stage a second run id leaves the walk holding two runs of that stage, and the next stage refuses. Traced in #290 and reproduced by the tests below on the tree at `afb647f`:

| What changed between two invocations | Second run of | Refused by | Measured refusal |
|---|---|---|---|
| A build with a commit to `corpus` | byte-level reduction | extraction | `walk 9 holds 2 runs of stage "byte-level-reduction"` |
| A build with a commit to `extraction` | extraction | content census | `holds 2 runs of stage "extraction"` |
| A build with a commit to `similarity` | extraction | content census | `holds 2 runs of stage "extraction"` |
| A build with a commit to `pipeline` | content census | content redundancy | `holds 2 runs of stage "content-census"` |
| `degenerateOutputConfidenceFloor` set after invocation 1 | extraction | content census | `holds 2 runs of stage "extraction"` |
| `boilerplateDocumentFrequencyFloor` retuned after stage 5 ran | content redundancy | seed measurement | `holds 2 runs of stage "content-redundancy"` |

A Docling image or version change (ADR-147) is the confidence-floor route by another door: it changes the extractor identity stage 2's run is named by.

The refusal advised "Run a fresh walk", which ADR-115 makes impossible for an unchanged archive. Reverting the build or the value does not help either, because both run rows stay: the test that puts the confidence floor back is refused on the third invocation exactly as on the second. The only ways out were to change the archive or delete the working directory. The fourth route is on the README's own path: that floor is read off `confidence-distribution.html`, which invocation 1 writes.

A commit to `embedding` or `synthesis` alone does not reach this. The stages after seed measurement already take their upstream run from the bean that minted it in the same invocation (`ScoringRun` from `SeedMeasurementRun`, `ArrangementRun` from `ScoringRun`), which is the shape this record extends to the rest.

### 2. The approval has the same defect one stage later

`ArrangementGate.approvedArrangement` matched the approval against every arrangement of the walk. Run rows are never deleted and an unchanged archive keeps its walk, so the arrangement an earlier approval named stays an arrangement of that walk after a changed relevance floor has made a new one (ADR-117). Measured by the tests below:

- Approve arrangement A, change `relevanceScoreFloor`, invoke. The invocation makes arrangement A′ and writes `arrangement.html` for it, and the gate opens on A: a generation run naming A was minted and the documents were written up over the arrangement the operator had approved before.
- If A was already written up, the closing line read "Every value the profile asks for is answered, including the arrangement you approved. Nothing is left to set. The deliverable is at …" and named A's tree. The operator had never seen or approved A′.
- Put the floor back. The invocation continues A, which is finished, so the arrangement step writes nothing (ADR-115), and `arrangement.html` still shows A′. The closing line names A′ too, because it reads the arrangement written last. An operator who copies that name approves an arrangement the documents are not in.

That contradicts ADR-107:19, "Naming the run means a re-arrangement closes the gate again", and ADR-117's consequence that an approval "expires exactly when the thing it approved changed".

### 3. What is in hand, checked step by step

ADR-099's reason for a lookup was that a process starting part-way down the cascade would not hold the earlier ids. Since ADR-115 no process does. The job is fifteen steps chained linearly with no flows or deciders (`CensusJobConfiguration`), every invocation starts at census, and a step whose work is recorded still derives its run's id before deciding to do nothing. Checked against the code at `afb647f`:

| Run | Where its id is derived | Derived when its step skips as finished? | When no id exists this invocation |
|---|---|---|---|
| byte-level reduction | inline in `ByteLevelReductionTasklet.execute`, before the `stepFinished` check | yes | never: the step has no gate |
| extraction | `ExtractionRun`, step-scoped, built when the step's reader is built | yes: the reader asks `stepFinished` of it | never: no gate; a breaker stops the whole job |
| content census | `ContentCensusRun`, step-scoped; the tasklet asks it before `stepFinished` | yes | never: no gate |
| content redundancy | `RedundancyRun`, job-scoped, shared by both steps | yes | while the boilerplate floor is unset (ADR-080) |
| seed measurement | `SeedMeasurementRun`, job-scoped | yes | while the seed folder, the usable-seed gate or the boilerplate floor is shut |
| scoring | `ScoringRun`, job-scoped | yes | while the embedding model is unset, or seed measurement is shut |
| arrangement | `ArrangementRun`, job-scoped; the tasklet asks it before `stepFinished` | yes | while stage 5's preamble is shut, or no survivor was grouped |

Every stage that needs an upstream id runs only where that upstream stage minted or continued its run earlier in the same invocation. Seed measurement needs content redundancy's run and extraction's: its constructor already refuses unless the boilerplate floor is set, which is exactly when content redundancy mints. Nothing downstream of a shut gate mints. So the id is in hand in every case, and the leaning this record was asked to verify holds.

One case needs care, and it is the arrangement. `GenerationTasklet` must not reach `ArrangementRun` itself, because `ArrangementRun` is job-scoped and reaching it would mint an arrangement behind the arrangement step's own gate. It has to be told whether the arrangement step arrived at one.

## Decision

### §1. A stage's upstream run is the run of the stage before it that this invocation minted or continued

**The rule.** Where a stage names an upstream run, or reads another stage's rows by run (seed measurement reads extraction's), the run it names is the one that stage's step minted or continued earlier in the same invocation. Not a lookup over the walk, and not a recomputation.

**Why this is the operator's choice and not a guess.** ADR-099:51 refused to choose because "nothing in the data says which one the operator meant — not the configuration, and not recency either, since an operator may have run the second as an experiment and want the first." Under ADR-115 the configuration does say which one. A run id is a total function of the build, the configuration and the walk (ADR-048, ADR-058). So the run an invocation arrives at is the one the operator's present profile and installed build name. An operator who ran the second as an experiment and wants the first puts the value back. The next invocation re-derives the first run's id, finds it finished, does nothing and names it downstream. The test that puts the confidence floor back measures exactly that: the third invocation completes and records no new run. The profile and the build are the "means of choosing" ADR-099 asked for, and the operator already writes both. No new value is needed.

**So ADR-099's obligation is discharged, and not by a gate.** ADR-099 said the fault would "become a gate naming that means". A gate asks for a value the pipeline requires and does not have (`CONTEXT.md`). Here the pipeline already has it, so there is nothing to ask for, and nothing becomes a gate.

**The refusal goes.** With one id in hand per stage there are never two candidates, so `AmbiguousUpstreamRunException` has no thrower and is deleted along with its "Run a fresh walk" advice. What remains is the case of no id at all. §3 of the Context shows the job's step order and gates make that unreachable, so if it happens it is a defect in how the job is wired, not something an operator can set. Its message says that and names the stage (see *What the implementation owes*, item 2).

**What an invocation holds is exactly one run per stage.** Each stage's run bean is either step-scoped inside one step or job-scoped, and one invocation is one job execution under a resourceless repository (ADR-036), with no restarts. So the record of this invocation's runs is a map from stage to one id, and no id is ever replaced within an invocation.

### §2. The arrangement approval opens 6b's gate only on the arrangement this invocation made or continued

**The rule.** `arrangementApproved` opens the gate when this invocation's arrangement step arrived at an arrangement and that arrangement's id begins with the approval. In every other case the gate is shut: no arrangement this invocation, an approval naming an older arrangement, and an approval naming nothing all stay shut. It is matched against one arrangement, so it can never match two, and `AmbiguousArrangementException` is deleted with ADR-107's "a prefix matching two stops the run".

The prefix rule itself is unchanged: the approval is compared as a prefix, as `runsMatching` compared it. Only the set it is compared against shrinks, from every arrangement of the walk to one.

**This is ADR-107's own rule, restored.** "Naming the run means a re-arrangement closes the gate again." Over a reused walk, closing it means comparing against the arrangement the documents are in now, and that is the one this invocation arrived at.

**Putting a value back re-opens the gate on what was approved under it**, by the same reasoning as §1: the arrangement is continued under its own id, and an approval naming it opens the gate again. The test that puts the relevance floor back and then approves measures that the write-up is made over the first arrangement.

**The closing line (`NextAction`) names this invocation's arrangement, never the one written last.** When the approval does not open the gate, the operator is told which arrangement to approve and what the approval currently holds:

- **Approval set, this invocation arrived at an arrangement, and the approval does not name it** (an older arrangement, or a typo). A new branch, placed where the "Nothing is left to set" branch now catches it:

  > Every value the profile asks for is answered, but arrangementApproved holds "*held*", which is not the arrangement the documents are in now. Next: read arrangement.html, and if that arrangement is the one you want, write "*short name*" into arrangementApproved in profile.yaml -- with what you checked in provenance beside it -- and run again*written with*.

  Here *held* is the value as written, *short name* is `ArrangementGate.shortNameOf` of this invocation's arrangement, and *written with* is the existing `writtenWith(generationModel)` clause. It names no deliverable, because no write-up happened this invocation and an earlier arrangement's tree is not this one's.
- **Approval set, and no arrangement this invocation.** The existing "Nothing was arranged this invocation, so there is nothing to approve yet" sentence, which today is reached only with the approval unset.
- **Approval unset.** Unchanged, except that the name it hands over is this invocation's arrangement.
- **Approval names this invocation's arrangement.** Unchanged: "Nothing is left to set", and the deliverable it names is this invocation's generation run's tree, not the one written last.

The wording in the first bullet is fixed here so that `OperatorTextTest` and a reviewer have one place to check it against. The tests pin its load-bearing parts: `Next:`, the quoted short name, `arrangementApproved`, and the absence of "Nothing is left to set" and of a deliverable.

**`arrangement.html` names the arrangement this invocation arrived at, including when it continued a finished one.** Otherwise the closing line and the page it tells the operator to read disagree whenever a value is put back, and the operator approves a name they did not read. When the arrangement step finds its own work recorded, it still renders the page from the rows recorded under that arrangement, and writes nothing else. This amends ADR-115's "a skipped step writes no report" for this one page, because it is the only page whose name an approval copies. The others stay as ADR-115 left them (see *What this does not decide*).

### §3. What a new build costs an operator part-way through an archive (amends ADR-058's Consequences)

ADR-058 priced a module commit as "one avoidable re-run" of that module's stage. That is still the compute cost. Since ADR-115 there is also an operator cost, and before this record it was not a cost at all but a dead end: the archive was stranded (§1 of the Context). After this record, a build that moves module *M* costs:

- **Every stage whose implementation version names *M* is minted again, and so is every stage downstream of it**, because a run id hashes its upstream ids (ADR-048). The module lists are pinned by `RunIdentityGoldenTest`: `corpus` moves stage 1 and everything after it; `extraction` moves stage 2 and everything after it; `similarity` moves stage 2 and everything after it; `pipeline` moves stages 3 to 6b; `embedding` moves seed measurement to 6b; `synthesis` moves 6a and 6b.
- **Re-minted stage 2** re-hashes every surviving file, but its conversions are extraction-cache hits (ADR-070). **Stages 3 and 4** redo their work from the database. **Stage 5**'s embeddings are vector-cache hits (ADR-085), but its steps re-read the archive where they recompute a content hash.
- **6a mints a new arrangement, so the approval no longer opens the gate (§2).** The operator is asked to read `arrangement.html` again and re-approve, which is one more invocation. This is ADR-107's "an operator who re-arranges must re-approve", paid because of a build rather than a setting.
- **6b then calls the generation model once per cluster again**, and writes a second deliverable tree beside the first (ADR-103).

So a build is paid for **per build, not per commit**. An operator part-way through an archive should take one build that carries several changes, at a corpus boundary where they can.

**A known gap in ADR-058's own rule, recorded and not fixed here.** ADR-058:21 counts "the specific stage-orchestration class in `pipeline` that drives" a stage as part of its version. Stages 1 and 2 do not name `pipeline` (stage 1 names `corpus`; stage 2 names `extraction` and `similarity`), yet rules that shape their output live there: stage 1's content-identity resolution in `ByteLevelReductionTasklet`, and ADR-070's failure classification and ADR-071's timeout streak in `ExtractionItemProcessor`. A change to those re-mints nothing, which is the too-lazy failure ADR-058 exists to prevent. Moving those rules into the modules their stages already name closes it; that is Wave 2 of the architecture-simplification plan (#292), not this record.

## Alternatives rejected

**ADR-099's own answer: a gate naming the run to continue from.** A profile key (or one per stage) holding the run id each stage should read. Rejected for four reasons:

- It asks for a value the pipeline already has. The run the operator means is the one their present profile and build name (§1), so the gate would ask them to copy back an id the invocation just derived.
- It breaks ADR-098's contract that the operator is told the next value and never the stage. Stages 2, 3 and 4 have no page an operator reads their run id off, so asking for one would put stage names and hashes in front of the operator.
- Every build would add a stop. Up to four stages re-mint on one build, and each would ask again.
- Its answer goes stale. The key would outlive the run it named, and a later change would leave it naming a run nobody meant. That is exactly the "approval that outlives what it approved" ADR-107 exists to prevent.

**Narrow the lookup by the current build's implementation version.** Fixes the four build routes and leaves the two configuration routes stranding the operator, since a floor change keeps the version and adds a run. Narrowing by configuration as well would mean reconstructing each upstream stage's `config_consumed` from the profile, which is the recomputation ADR-099 removed for drifting silently. Not enough on its own, and the complete version of it is the thing already rejected.

**Take the run written most recently.** ADR-099 rejected recency, and the put-back case shows why it is wrong as well as unprincipled. After the value is put back, the run this invocation arrived at is the older one, and recency names the newer. Today's `NextAction` does this (`latestRunFor`), and it is why the closing line named A′ after the floor was put back.

**Delete the superseded run.** ADR-099's third path. AGENTS.md describes retuning as "a `DELETE` of one stage's rows plus a re-run", but nothing implements it, and it would destroy the very work that putting a value back now picks up again for free.

**Match the approval against every arrangement of the walk, but refuse when a newer one exists.** It still needs an order over arrangements, which is recency, and it gets the put-back case backwards.

## What the implementation owes

1. **A record of the runs this invocation minted or continued.** One job-scoped instance, keyed by stage, holding one id each. Every place a run is minted records it there at the moment `Ledger.startRun` returns, including stage 1's inline mint. It lives in `pipeline` (ADR-040). If it is a new bean, it joins `@CascadeSliceTest`'s import list; adding a line of wiring there weakens no test.
2. **`UpstreamRuns` answers from that record, not from the ledger.** `ExtractionRun`, `ContentCensusRun`, `RedundancyRun` and `SeedMeasurementRun` take their upstream ids (and seed measurement its extraction id) from it. `AmbiguousUpstreamRunException` is deleted. `NoUpstreamRunException` stays for the unreachable case, and its message stops advising an order of stages. It says instead that this invocation holds no run of that stage, which the job's step order should make impossible, so it is a defect to report and not a value to set. `Ledger.runsOf` and `RecordedRun` lose their only caller and are deleted.
3. **`ArrangementGate` matches the approval against this invocation's arrangement only**, as a prefix of its id, and it is told whether the arrangement step arrived at one. `AmbiguousArrangementException` and `Ledger.runsMatching` are deleted. `GenerationTasklet` never reaches `ArrangementRun` itself, since that would mint an arrangement behind its step's gate. `GenerationRun` names the arrangement the gate opened on. `GenerationTasklet.writeDeliverable` re-derives stage 1's run with `RunId.of`, which is the recomputation ADR-099 removed everywhere else. It takes stage 1's run from the record instead.
4. **The arrangement step renders `arrangement.html` from the rows recorded under its arrangement whenever it arrives at one, including when its work is already recorded.** It writes no rows in that case.
5. **`NextAction` names this invocation's arrangement and this invocation's generation run**, not the ones written last, and gains the branch in §2 worded as there. `Ledger.latestRunFor` loses both callers and is deleted. `VesperaCommand` hands `NextAction` what the job arrived at. How is the implementer's choice, provided it is read off what the invocation recorded, per `NextAction`'s own rule that the line is read off what was written rather than assembled from what steps saw.
6. **Javadoc that becomes false moves with the code.** That covers the four run classes' "The stage must not depend on having run in the same invocation as its predecessor … a query is not in-process state", `UpstreamRuns`' whole class comment, `ArrangementGate.approvedArrangement`, `GenerationRun`'s "Its upstream is the arrangement the operator approved", and `NextAction.arrangementToApprove`'s "The run written last".
7. **Tests this decision moves.** The claims below were true of ADR-099's lookup or of `approvedArrangement(WalkId)`, and the decision changes them. Each is re-expressed with the code, keeping its claim except where noted, and none is weakened:
   - `UpstreamRunsTest`. Its three tests pin the ledger lookup and retire with it. "A stage with no run leaves its successor nothing to name" is re-expressed over the invocation's record. "The one run of a stage over this walk is the id its successor reads" becomes "the run this invocation recorded for the stage is the id its successor reads". "Two runs of one stage over one walk stop the successor" retires, superseded by `UpstreamRunOverAReusedWalkTest`.
   - `ArrangementGateTest`. "Nothing approved closes the gate", "A name matching no arrangement closes the gate" and "A name matching one arrangement opens the gate" are re-expressed against the arrangement handed to the gate. "A name matching two arrangements stops the run" retires: it is unreachable by construction. A fifth claim joins them: a name matching an older arrangement of the walk but not this invocation's closes the gate.
   - `ExtractionRunTest`, `ContentCensusRunTest`, `ContentCensusTaskletTest` and `ExtractionItemProcessorTest`. Only their fixtures change: they construct run beans directly and must hand them the upstream id the invocation's record would hold, instead of seeding a row for a lookup to find. `ContentCensusRunTest.namesTheExtractionRunAsItsUpstream`'s claim "stage 3 works out extraction's own run identity rather than being handed it" becomes "stage 3 names the extraction run this invocation recorded".
   - `LedgerTest`'s two `latestRunFor` tests retire with the method.
8. **The fix re-mints nothing by itself** unless it edits `pipeline`, which it will. That re-mints stages 3 to 6b once, the cost §3 describes, and an operator part-way through should take it at a corpus boundary.

## Tests

`UpstreamRunOverAReusedWalkTest` (#290) and `ArrangementApprovalOverAReusedWalkTest` (#296) pin this record. Each invokes two or more times over one database and one unchanged archive. `SuccessiveBuildsBeans` plays two builds by moving one module's implementation version between invocations. The confidence floor is read into a singleton at context start, so the test drops that singleton before each invocation, which is what the next process does.

On the tree at `afb647f` all ten fail for the reason this record is about. The seven in the first class fail on the ambiguity refusal, each at the stage the table in the Context names. The three in the second fail because the stale approval opened generation over the older arrangement, because the closing line said "Nothing is left to set", and because the page named the newer arrangement after the floor was put back.

## What this does not decide

**A loosened value re-admits nothing over a reused walk. Measured, and left for its own ticket.** `Ledger.survivors` counts a blocking verdict from any run of the walk ("verdicts are not filtered by run"), so a run minted under a looser value still sees what the stricter run removed. Measured on 2026-09-25 with a throwaway probe on `RelevanceFloorInvocationTest`'s fixture: a calibrated relevance floor of 1.5 removed both documents; setting it to 0.1 and invoking again minted a third scoring run that clustered **zero** documents, with both `below-threshold` verdicts still standing. Before ADR-115 each invocation minted a fresh walk, and a fresh walk carries no verdicts, so this could not happen. The remedy this record points toward is that survival be read along the runs this invocation arrived at rather than across the walk. That moves ADR-060, ADR-089 and `CONTEXT.md`'s "Verdicts accumulate" and wants its own decision. It is not taken here.

**The other report pages after a value is put back.** `confidence-distribution.html`, the relevance labelling page and the rest still show the run written last when a finished run is continued, per ADR-115. Only `arrangement.html` gates an approval that copies a name off it, so only it is changed here.

**How short an approval may be.** The prefix rule is ADR-107's and is unchanged. Matching against one arrangement rather than many lowers the chance that a short prefix matches by accident, but does not remove it.

**A start part-way down the cascade.** ADR-099 wanted it and ADR-115 made it unnecessary. Whatever builds one must bring a means of naming each upstream run it does not hold, and that is where ADR-099's gate would belong.
