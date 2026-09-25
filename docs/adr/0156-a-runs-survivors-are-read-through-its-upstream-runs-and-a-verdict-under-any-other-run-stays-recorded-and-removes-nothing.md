# ADR-156 — A run's survivors are read through its upstream runs, and a verdict under any other run stays recorded and removes nothing

- **Date**: 2026-09-25
- **Status**: accepted
- **Amends**: [ADR-060](0060-survivors-is-a-ledger-owned-item-reader-not-a-view.md), in what `survivors(runId)` means. Its form stands: a `ledger`-owned reader, never a view and never a materialised list, with the SQL inside `ledger`. What changes is which verdicts it counts. ADR-060 already put "the run-chain resolution (ADR-048)" behind this one call. The code never did it: the run named only the walk. §1 does it.
- **Amends**: [ADR-089](0089-a-stages-run-names-the-immediately-preceding-stages-run-upstream-because-verdicts-are-cumulative.md), in its reason and not its rule. A stage's run still names the immediately preceding stage's run as its upstream. ADR-089 gave the reason as "blocking verdicts are counted from every run". After this record they are counted from the run asked about and the runs upstream of it, so the upstream a run names now decides which verdicts its survivors answer to (§3). ADR-089's aim, that two runs with one id never describe two corpora, holds more exactly than it did (§3).
- **Amends**: [ADR-014](0014-verdict-ledger-model.md), in how its "cheap retuning" happens. Retuning is not a delete of one stage's rows and a re-run. It mints a new run of that stage (ADR-117, ADR-154), whose survivors do not answer to the old run's verdicts. Nothing is deleted.
- **Amends**: [ADR-154](0154-a-stage-reads-the-upstream-run-this-invocation-arrived-at-and-an-approval-opens-only-this-invocations-arrangement.md), in one paragraph. Its *What this does not decide* left open whether a loosened value re-admits anything over a reused walk. It is decided here.
- **Amends**: `CONTEXT.md`'s **Verdict** ("Verdicts accumulate; they never replace one another") and **Survivor** ("A file occurrence carrying no blocking verdict") entries, as §4 words them.
- **Rests on**: [ADR-048](0048-walk-and-run-identity.md) (a run id folds in the runs it read), [ADR-115](0115-a-re-walk-that-observed-nothing-new-is-discarded-and-a-run-is-continued-under-its-own-id.md) (a walk is reused, and a run is continued under its own id), [ADR-116](0116-a-runs-completion-is-recorded-per-step-because-several-steps-share-one-run.md) (a step discards its own rows under its run before redoing them), [ADR-117](0117-the-relevance-floor-joins-the-scoring-runs-identity-so-a-changed-threshold-is-a-different-run.md) (a changed relevance floor is a different scoring run), [ADR-154](0154-a-stage-reads-the-upstream-run-this-invocation-arrived-at-and-an-approval-opens-only-this-invocations-arrangement.md) (the upstream a run names is the run of the stage before it that this invocation arrived at).
- **Settles** [#297](https://github.com/algernon28/vespera/issues/297).

## Context

### The measurement

ADR-154 measured this and left it open. On `RelevanceFloorInvocationTest`'s fixture, a calibrated relevance floor of 1.5 removed both documents as `below-threshold`. The floor was then set to 0.1 and the archive invoked again. A third scoring run was minted, as ADR-117 intends, and it scored and clustered **zero** documents.

The cause is one sentence of `Ledger.survivors`' Javadoc: "Verdicts are not filtered by run: a blocking verdict from any run removes an occurrence." The run a caller passes names the walk and nothing else. Both `below-threshold` verdicts were written under the 1.5 run, and they still stood. So the 0.1 run's relevance-scoring step read an empty survivor set, and scored nothing.

### Why it could not happen before ADR-115, and why it is not only the relevance floor

Before ADR-115 every invocation minted a fresh walk, and a fresh walk carries no verdicts. "Any run of the walk" then meant "any run of this invocation", which was always the runs this invocation arrived at. ADR-115 made walks reusable, and the two sets came apart.

The same holds for every verdict-writing stage whose run can be minted a second time over one walk:

| Loosened or changed | Re-minted run | Verdicts that go on removing today |
|---|---|---|
| `relevanceScoreFloor` lowered | scoring | `below-threshold` under the stricter scoring run |
| `degenerateOutputConfidenceFloor` lowered, or a Docling image or version change (ADR-147) | extraction | `degenerate-output` and `extraction-failed` under the earlier extraction run |
| `boilerplateDocumentFrequencyFloor` retuned | content redundancy | `redundant-with` under the earlier redundancy run |
| a build moving `corpus` | byte-level reduction | `broken`, `out-of-scope` and `superseded-by` under the earlier stage 1 run, and every later stage's verdicts as well |

The last row is the widest. A re-minted stage 1 reads its own survivors before judging (`ByteLevelReductionTasklet`), so today it never re-judges anything that any later stage removed. That includes documents removed as irrelevant: stage 1 looks at a broken-file check over a set that scoring decided.

### The same fault read from the other side

"Any run" also lets verdicts reach **backwards**. `ConfidenceDistribution` and `DocumentFrequency` read `survivors(stage2RunId)`. On an invocation after a floor had removed documents, stage 3's document frequency, recomputed under a new census run, would exclude `below-threshold` documents that stage 5 removed. The census would then count its corpus after the relevance floor, which stage 3 has no business knowing about. ADR-089's aim was that "two measurement runs carrying the same run id" never "describe different corpora". Any-run survival breaks that whenever a later or sibling run writes a verdict.

### What ADR-060 already said

ADR-060 put three things behind `survivors(runId)`: "the join logic, the blocking-verdict semantics … and the run-chain resolution (ADR-048)". The code carries the first two and not the third. This record supplies the third, and changes nothing else about ADR-060.

## Decision

### §1. A run's survivors are the occurrences of its walk with no blocking verdict under that run or any run upstream of it

**The rule.** `survivors(runId)` hands out each occurrence of `runId`'s walk that carries no blocking verdict written under `runId` itself or under a run reached from `runId` by following `run_upstream`, however many steps back. `survivorCount(runId)` counts the same set.

**Why this is the arrived-at runs, stated as a property of the run.** Under ADR-154 the upstream a run names is the run of the stage before it that this invocation minted or continued. So the runs reached upstream from a run this invocation arrived at are exactly the runs of the earlier stages that this invocation arrived at, one per stage. Reading survival through them is the same as reading it through `InvocationRuns`, which is what #297 asked for. It is stated over the run rather than over the invocation for three reasons:

- **It keeps the rule in `ledger`.** `InvocationRuns` lives in `pipeline` (ADR-040), and `ledger` depends on no capability module (ADR-041). The upstream rows are already `ledger`'s.
- **It makes a run's survivor set a function of the run.** Two readers naming one run read one set, whichever invocation they belong to. That is ADR-048's and ADR-089's promise about run ids, now kept by the query as well as by the identity.
- **Nothing needs to be handed in.** Each caller already passes a run. §5 lists the three that pass the wrong one.

**The run's own verdicts count.** Several steps write under one run and then read survivors under it: stage 1 reads its own survivors, stage 2's reader is `survivors(extractionRunId)`, and clustering reads `survivors(scoringRunId)` after the floor step wrote `below-threshold` under that same run. Clustering depends on the last of these (ADR-087: "a below-threshold document costs nothing").

### §2. A verdict under a run the invocation did not arrive at stays recorded and removes nothing

**Stays recorded.** Nothing deletes it. The only delete the ledger has is a step discarding its own verdicts under its own run before redoing unfinished work there (ADR-116), and that never reaches another run's verdicts. So every verdict a finished step wrote can still be read for an audit (ADR-014).

**Removes nothing** from the survivors of any run that does not reach its run upstream. Because the upstream a run names is fixed when the run is minted, a verdict's reach is fixed too. A verdict under the 1.5 scoring run removes documents from that run's survivors, from the arrangement over it and from the generation run over that. It removes nothing from the 0.1 scoring run, a sibling that names the same seed-measurement run.

**Putting a value back makes it count again, with nothing recomputed.** Set the floor back to 1.5. The invocation re-derives the 1.5 run's id, continues it (ADR-115), and finds its work recorded. Its `below-threshold` verdicts count again for everything downstream of it, because they were never gone. This is ADR-154 §1's put-back, extended from which run is read to what that run removed.

**Discarding the old run's verdicts is rejected** (see *Alternatives rejected*). It would destroy exactly what the put-back picks up for free.

### §3. The stages and the reports each read survival through the run they already name

Every reader of survivors names a run. After §1 the run it names decides the verdicts it answers to, so each reader must name the **latest run whose verdicts it is meant to see**. This is ADR-089's rule read from the other end. ADR-089 said a stage names its predecessor as upstream because every earlier verdict shapes what it reads. §1 makes that literal: the upstream a stage names is what selects the verdicts it reads.

| Reader | Run it names | Verdicts it answers to | Changes |
|---|---|---|---|
| Stage 1, `ByteLevelReductionTasklet` (count and both reads) | stage 1's own run | stage 1's own | no |
| Stage 2, `ExtractionJobConfiguration`'s reader and `ExtractionItemProcessor`'s count | stage 2's own run | stages 1 and 2 | no |
| `confidence-distribution.html`, `ConfidenceDistribution` | stage 2's run | stages 1 and 2 | no |
| Stage 3, the census, `DocumentFrequency` | stage 2's run | stages 1 and 2 | no |
| Stage 4, `RedundancyJobConfiguration`'s reader and `RedundancySignatureItemWriter`'s count | stage 4's own run | stages 1, 2 and 4 | no |
| Stage 5, `SeedCorpusComparison.measure` | stage 2's run | should be stages 1, 2 and 4 | **yes**: the measurement run |
| Stage 5c, `EmbeddingScoringTasklet` | stage 2's run | should be stages 1, 2 and 4 | **yes**: the measurement run |
| Stage 5d, `RelevanceScoringTasklet` | stage 2's run | should be stages 1, 2 and 4 | **yes**: the measurement run |
| Stage 5f, `ClusteringTasklet` | the scoring run | stages 1, 2, 4 and 5's `below-threshold` | no |

**The census, stage 1 and stage 2.** Stage 1 answers to its own verdicts only, so a re-minted stage 1 judges every occurrence of the walk again, including ones a later stage removed under the earlier stage 1 run. Stage 2 answers to stage 1's run and its own. Its step discards its own verdicts before redoing them (ADR-116), so on a continued run it reads stage 1's survivors, and on a new run the same. The census writes no verdicts. It reads under stage 2's run, so its document frequency is the corpus as stages 1 and 2 left it, never as a later floor left it. That was always the intent (ADR-089's table), and any-run survival broke it on a reused walk.

**The three stage-5 readers that must change.** They pass `measurementRun.extractionRunId()` today. Under any-run survival the run only named the walk, so any run of the walk did the job, and stage 2's was to hand. Under §1 that would drop stage 4's `redundant-with` verdicts from what stage 5 measures, embeds and scores, which is the very defect ADR-089 exists to prevent. They name the **seed-measurement run** instead, whose upstream is stage 4's run (ADR-089). Stage 2's id stays in their hands for what it is really for: naming the run whose `extraction_metric` rows carry the corpus side of the comparison (ADR-092).

The seed-measurement run, and not the scoring run, is named by 5c and 5d for two reasons. The scoring run's own `below-threshold` verdicts are not what these steps should skip: they are the input to that floor. And measurement, embedding and scoring are all "what stage 4 left standing", which is exactly the measurement run.

**The stages that mint no run.** A step whose gate is shut mints nothing (ADR-080) and reads no survivors, so it has nothing to scope. The seed side reads `occurrencesOf(seedWalk)`, never `survivors` (ADR-083), and is unaffected. Arrangement (6a) and generation (6b) read clusters under the scoring run and the arrangement run, never `survivors`. Those clusters were drawn from the scoring run's survivors, so they are already scoped. The closing line (`NextAction`) reads no survivor set.

**The reports.** Every page that counts survivors names a run in the table above, and so shows the survivors of the run this invocation arrived at for that stage. The relevance labelling page, `cluster-sizes.html` and `arrangement.html` read scores, clusters or the arrangement under a named run, whose membership §1 has already scoped. ADR-154 §2's rule that `arrangement.html` names this invocation's arrangement is unchanged. Which run a page shows when its step finds its work recorded is ADR-115's question and ADR-154's *What this does not decide*, and it stays open.

### §4. What "Verdicts accumulate" becomes

In `CONTEXT.md`:

> **Verdict**: A recorded judgement against one file occurrence by one stage, carrying its reason, under the run that made it. Verdicts accumulate down the cascade: a run's survivors answer to its own verdicts and to those of every run upstream of it. They never replace one another, and none is deleted except by the step that wrote it, redoing its own unfinished work under the same run (ADR-116). A verdict under a run the present profile and build no longer arrive at stays recorded and removes nothing, until putting a value back arrives at that run again.

> **Survivor**: A file occurrence carrying no blocking verdict under a given run or any run upstream of it. A question the ledger answers of one run, not a place documents are moved to.

"Accumulate" keeps its meaning down the cascade: stage 4's survivors still exclude what stages 1 and 2 removed. It stops meaning "across every run the walk has ever held", which it only ever meant by accident, before ADR-115 let a walk hold more than one run of a stage.

### §5. The signatures stay, and three call sites change

`Ledger.survivors(RunId)` and `Ledger.survivorCount(RunId)` keep their signatures and their return types. Only their meaning changes, as §1 states. A signature taking `InvocationRuns`, or a set of run ids, is rejected (see *Alternatives rejected*).

Three callers change the run they pass, as §3 says: `SeedCorpusComparison.measure`, `EmbeddingScoringTasklet` and `RelevanceScoringTasklet`. Every other caller already passes the run §3 wants.

### §6. What the operator sees

- **Loosening a value brings documents back on the next invocation.** Lower `relevanceScoreFloor` from 1.5 to 0.1 and the next `vespera run` scores, clusters and arranges the documents the stricter floor removed. `arrangement.html` shows them, and since this is a new arrangement, the approval no longer opens 6b (ADR-154 §2) and the operator re-approves. The same goes for a lowered `degenerateOutputConfidenceFloor` and a retuned `boilerplateDocumentFrequencyFloor`, each from its own stage onward.
- **Tightening again removes them again, and costs nothing.** Put 1.5 back and the next invocation continues the 1.5 scoring run, whose verdicts were never deleted.
- **The counts on each page are the arrived-at run's.** "Scoring *N* corpus survivor(s)" in stage 5d's log, and the corpus side of the seed/corpus comparison, count what this invocation's runs left standing. They do not count what some earlier configuration left standing.
- **A re-minted stage 2 asks about files an earlier run failed.** Under any-run survival a new extraction run never saw a file an earlier one had removed as `extraction-failed` or `degenerate-output`, so a Docling image that could now open it (ADR-147) never got the chance. What this record adds is that those files are now among the files asked. What each costs depends on why the run was re-minted. A new image or version changes the extractor identity (ADR-147), so every survivor misses the extraction cache and is converted again, failed or not. A lowered `degenerateOutputConfidenceFloor` leaves the extractor identity unchanged: there a conversion that came back as a failure response is an extraction-cache hit (ADR-070) and costs no call, and only files that earlier left an extraction fault are asked again.
- **Nothing new to set, and no new message.** No key, gate or closing line is added. The operator already chooses which run applies by writing the profile and installing the build (ADR-154 §1).

## Alternatives rejected

**Discard a superseded run's verdicts when a new run of the same stage is minted.** This was #297's other option, and `docs/architecture.md`'s old "a `DELETE` of one stage's rows plus a re-run". Rejected for three reasons:

- It destroys what putting a value back re-uses. The continued run would find its work recorded and its verdicts gone, and would either remove nothing, which is wrong, or have to redo the work, which ADR-115 exists to avoid.
- It needs a rule for which run is superseded. That is recency, which ADR-099 and ADR-154 both rejected.
- It deletes another run's finished verdicts, which nothing in the ledger does today. The one delete there is a step discarding its own unfinished work under its own run (ADR-116). It would break the audit ADR-014 gives.

**Pass `InvocationRuns`, or a set of run ids, into `survivors`.** Reads the same set as §1 for any run this invocation minted, but:

- It puts a `pipeline` type into `ledger`'s signature, or makes every caller assemble a set that the upstream rows already hold.
- A run's survivors would then depend on who asked, not on the run. That reopens ADR-089's "two readers of one run see two corpora".
- It changes thirteen call sites to fix three.

**Filter by the run passed only, not the runs upstream of it.** Stage 4's survivors would then include what stage 1 removed. That is not a cascade.

**Filter by stage, taking the newest run of each stage.** This is recency again, and it gets the put-back backwards (ADR-154).

**Keep any-run survival and document it.** This leaves a lowered floor with no effect short of deleting the working directory, and a re-minted stage 1 judging a set that stage 5 chose. It is the defect #297 reports.

## Consequences

**ADR-089's rule becomes load-bearing twice.** Before, naming the immediate predecessor made the run id fold in every verdict that shaped a read. Now it also decides which verdicts are read. A stage that named the wrong upstream would read the wrong survivors, not just carry a wrong id. `RunUpstreamChainTest` already checks, after one full invocation, that no run's upstream skips a stage.

**A wrong run passed to `survivors` now changes behaviour, not just a log line.** Under any-run survival every caller could pass any run of the walk. After this record the run passed is the scope. The three stage-5 call sites in §3 are the ones that were only right by accident, and the third test under `SurvivalOverAReusedWalkTest` in *Tests* guards each of them with a claim of its own.

**The first invocation after this ships re-mints what its own module list says** (ADR-154 §3). The change edits `ledger` and `pipeline`, and `embedding` for `SeedCorpusComparison`. `ledger` is in no stage's implementation version. `pipeline` re-mints stages 3 to 6b, and `embedding` re-mints seed measurement to 6b. Stage 1 and 2's runs are continued. An operator part-way through an archive should take the build at a corpus boundary.

**The survivors query gains a walk over `run_upstream`.** That is a handful of rows per run: one upstream per stage, seven stages. The anti-join over `verdict` stays the expensive part, as ADR-060 said. Nothing here adds a scan per occurrence.

## What the implementation owes

1. **`Ledger.survivors(RunId)` and `Ledger.survivorCount(RunId)` count a blocking verdict only when its `run_id` is `runId` or a run reached from `runId` through `run_upstream`, transitively.** Both keep their signatures and return types. Resolve the set of runs inside `ledger`, either as a recursive common table expression in the anti-join (SQLite supports `WITH RECURSIVE` in a subquery), or by reading the set before building the reader and binding it as parameters. The implementer chooses. The reader stays a `JdbcPagingItemReader` (ADR-060). Both methods must answer the same set, and `SurvivorsTest` pins that.
2. **Three call sites pass the seed-measurement run:**
   - `embedding/SeedCorpusComparison.measure`: `ledger.survivors(extractionRunId)` becomes `ledger.survivors(measurementRunId)`. `extractionRunId` stays a parameter for `metricRows`.
   - `pipeline/EmbeddingScoringTasklet`: `ledger.survivors(measurementRun.extractionRunId())` becomes `ledger.survivors(measurementRun.runId())`.
   - `pipeline/RelevanceScoringTasklet`: the same change.
   Every other caller stays as it is: `ByteLevelReductionTasklet` (three calls), `ExtractionJobConfiguration.occurrenceReader`, `ExtractionItemProcessor`, `RedundancyJobConfiguration`, `RedundancySignatureItemWriter`, `ClusteringTasklet`, `ConfidenceDistribution` and `DocumentFrequency`.
3. **Javadoc that becomes false moves with the code.** That covers `Ledger.survivors` ("Verdicts are not filtered by run: a blocking verdict from any run removes an occurrence"), `Ledger.survivorCount`, `Ledger.verdict` ("retuning a stage is a delete of that stage's rows and a re-run"), `SeedMeasurementRun`'s class comment ("`Ledger#survivors` counts blocking verdicts from every run"), and `SeedCorpusComparison.measure` ("resemble `extractionRunId`'s survivors").
4. **Tests this decision moves.** `SurvivorsTest.survivalIsCumulativeAcrossRuns` keeps its claim: its later run names the earlier one upstream, so the earlier verdict still removes. No other existing test's claim changes. `SeedCorpusComparisonTest`, `ConfidenceDistributionTest` and `DocumentFrequencyTest` write verdicts under the run they read or under a run it names upstream, so they are unaffected.

## Tests

- **`SurvivorsTest`** (ledger, `@JdbcTest`) gains four claims:
  - a verdict under a sibling run, one naming the same upstream, removes nothing from the other's survivors;
  - a verdict under a later run removes nothing from an earlier run's survivors;
  - a verdict two runs upstream still removes;
  - `survivorCount` agrees with `survivors` in the sibling case.
  The first, second and fourth fail on the tree at `25eab18`, because any-run survival counts the verdict. The third passes there and pins that the rule still reaches upstream.
- **`SurvivalOverAReusedWalkTest`** (pipeline, `@CascadeSliceTest`, #297's fixture) invokes over one database and one unchanged archive:
  - floor 1.5, then 0.1: the 0.1 run scores, clusters and arranges both documents. Both `below-threshold` verdicts under the 1.5 run are still recorded, and still remove both documents from the 1.5 run's survivors. This fails on `25eab18`: the 0.1 run scores and clusters zero.
  - floor 1.5, then 0.1, then 1.5 again: the third invocation mints no scoring run, and the documents stand removed under the run it arrived at. This passes on `25eab18` and pins §2's put-back.
  - a `redundant-with` verdict written under stage 4's run between invocations is kept out of the seed/corpus comparison, out of embedding and out of scoring when the next invocation opens stage 5. Each has its own claim: the comparison's corpus document count, the absence of any `vector` row for the redundant document's content hash, and the count of relevance scores. Scoring's count alone would not guard embedding, because a redundant document embedded under stage 2's survivors still leaves scoring one document to score. This passes on `25eab18`, where any run removes. It fails if any of §5's three call sites is left naming stage 2's run after item 1 is built, and that is what it guards.
