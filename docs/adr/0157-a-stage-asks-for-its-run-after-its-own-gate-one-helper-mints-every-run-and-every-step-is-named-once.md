# ADR-157 — A stage asks for its run after its own gate, one helper mints every run, and every step is named once

- **Date**: 2026-09-26
- **Status**: accepted
- **Amends**: [ADR-131](0131-one-module-builds-every-plain-tasklet-step.md), on its second reason for keeping the per-stage `*JobConfiguration` classes, "the place each stage's step is named" (§7). [ADR-153](0153-the-whole-job-tests-share-one-slice-and-a-stages-configuration-class-stops-being-their-seam.md) withdrew the first reason and left this one for a later record to weigh. It is weighed here and withdrawn. `TaskletSteps` also gains a third method (§5).
- **Amends**: [ADR-080](0080-the-boilerplate-floor-is-a-gate-applied-before-signatures-are-computed.md), on what keeps its rule. Its rule is that a gated stage leaves no run row. That was kept by bean scoping and `ObjectProvider` indirection. It is now kept by an explicit call made after the stage's own gate (§4).
- **Amends**: [ADR-116](0116-a-runs-completion-is-recorded-per-step-because-several-steps-share-one-run.md) and [ADR-139](0139-a-refused-conversion-leaves-a-fault-row-and-a-step-that-completed-resolves-it-into-a-verdict.md), on one class. `RunCompletion` becomes the single completion listener for both chunk steps that use one, and it reads the run from this invocation's record rather than from a supplier (§6). ADR-139 §4's registration order is unchanged, and so is the reason for it.
- **Keeps**: [ADR-099](0099-a-stages-upstream-run-is-looked-up-by-stage-and-walk-not-recomputed-and-two-candidates-stop-the-run.md) as [ADR-154](0154-a-stage-reads-the-upstream-run-this-invocation-arrived-at-and-an-approval-opens-only-this-invocations-arrangement.md) amended it. Every stage names its upstream run the way it does today, so no amendment is owed (§3). Also kept: [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) and [ADR-048](0048-walk-and-run-identity.md) (every module list and every `config_consumed` text is byte-identical), ADR-154 §1 (`InvocationRuns` stays what it is), [ADR-055](0055-a-walk-is-resumed-under-its-own-id-until-it-finishes.md), [ADR-140](0140-stage-2-converts-eight-file-occurrences-at-a-time-and-consecutive-means-consecutive-on-the-drain.md) and [ADR-115](0115-a-re-walk-that-observed-nothing-new-is-discarded-and-a-run-is-continued-under-its-own-id.md).
- **Settles** the decisions [#303](https://github.com/algernon28/vespera/issues/303) asks for. That is Wave 1 of `handouts/architecture-simplification.md`.

## Context

Everything below was measured on `main` at `5cba9ef`.

**1. Minting is written eight times.** Seven classes mint one stage's run each: `ExtractionRun` (110 lines), `ContentCensusRun` (108), `RedundancyRun` (140), `SeedMeasurementRun` (147), `ScoringRun` (116), `ArrangementRun` (107) and `GenerationRun` (156). That is 884 lines between them. Stage 1 mints its own inline in `ByteLevelReductionTasklet.execute` (lines 104–114). Each of the eight repeats one sequence:

- find the finished walk, or throw `IllegalStateException("no finished walk is recorded for <root>; census must run before <what>")`;
- name the upstream run;
- serialise a private `ConfigConsumed` record with a `JsonMapper` of its own;
- call `Ledger.startRun` with a module list;
- record the id in `InvocationRuns`.

The module lists are ADR-058's table, written in eight places as `*_MODULE` constants.

**2. Scoping is what keeps a gated stage from leaving a run row, and it takes plumbing to do it.** The seven classes are `@JobScope` (five of them) or `@StepScope` (`ExtractionRun`, `ContentCensusRun`), and the run row is written in the constructor. So nothing may reach a run bean before its gate is checked. Thirteen `pipeline` classes hold 34 occurrences of `ObjectProvider<…Run>`, fields and parameters counted together. Two of those classes are run classes themselves: `ScoringRun` reaches `SeedMeasurementRun` through one, and `ArrangementRun` reaches `ScoringRun` through one. Since ADR-115 a second `startRun` with the same inputs continues the row already standing, so the scoping no longer prevents a duplicate. All it buys is "derived once per invocation", and a constructor that runs only when the scoped proxy is first called.

**3. The plan's "four constructors that re-check a gate" is one.** Four constructors do read a gate: `RedundancyRun` reads the boilerplate floor, `SeedMeasurementRun` reads the seed walk and the boilerplate floor, `ScoringRun` reads the embedding model, and `GenerationRun` reads the approved arrangement. In all but one case the value read is an input to the run, either a `config_consumed` field or the upstream run. The one pure re-check is `SeedMeasurementRun`'s read of stage 4's floor. `SeedGate` already requires that floor before it opens, and the floor is not in the measurement run's `config_consumed`.

**4. How each stage finds its upstream run today.** Four classes read it through `UpstreamRuns` over `InvocationRuns`: `ExtractionRun`, `ContentCensusRun`, `RedundancyRun` and `SeedMeasurementRun`. `ScoringRun` and `ArrangementRun` call the upstream run bean itself, which mints the upstream run if nothing has yet. `GenerationRun` asks `ArrangementGate` to match the approval against this invocation's arrangement, read from `InvocationRuns`. `GenerationTasklet` recomputes nothing. There is no `RunId.of` in it any more, and it reads stage 1's run through `UpstreamRuns` at line 213.

**5. Ten configuration classes hold one bean each.** `ArrangementJobConfiguration`, `ByteLevelReductionJobConfiguration`, `ClusteringJobConfiguration`, `ContentCensusJobConfiguration`, `EmbeddingModelJobConfiguration`, `GenerationJobConfiguration`, `RelevanceFloorJobConfiguration`, `RelevanceReportJobConfiguration`, `RelevanceScoringJobConfiguration` and `SeedCorpusComparisonJobConfiguration` come to 235 lines. Each holds one `TaskletSteps.taskletStep` call. The job's fifteen step names are declared in fifteen classes, one each: fourteen as `STAGE`, `STEP`, `STEP_NAME` or `SIGNATURE_STEP` constants, and census's as a bare literal (`"census"`). A sixteenth class holds an alias of one of them (`RedundancyResolutionTasklet.STEP = RedundancyRun.STAGE`). Seven of the fourteen constants, six in run classes and one in `ByteLevelReductionTasklet`, name both a step and the stage of the run it writes under, so today those two persisted names are one declaration. `CensusJobConfiguration` holds the job itself, census's step and the clock. Three configuration classes hold more than a step: `ExtractionJobConfiguration` (318 lines), `RedundancyJobConfiguration` (205) and `SeedExtractionJobConfiguration` (73). They hold readers, writers, listeners and the extractor's own bean.

**6. The skip-if-done shell has four shapes.** Twelve `stepFinished` calls and twelve `finishStep` calls sit in `pipeline`. Nine tasklets open with the same move: "is this step's work under this run recorded? then stop; otherwise discard and work, and record the work". What sits around that move differs in four ways:

- Three tasklets have a path that ends without recording completion: `RelevanceScoringTasklet` when no seed vector is resident, `ClusteringTasklet` when no partition exists, and `GenerationTasklet` when its breaker trips or a fault stands.
- `ClusteringTasklet` checks for an empty partition between the finished check and the discard.
- `ArrangementTasklet`'s "already recorded" branch still writes `arrangement.html` (ADR-154 §2).
- `EmbeddingScoringTasklet` and `GenerationTasklet` discard nothing.

Every "already recorded" branch logs its own sentence through its own logger. Those lines are operator-visible.

**7. Two listeners record a chunk step's completion.** `RunCompletion` does it for extraction. It reaches the run through a `Supplier<RunId>`, has no gate, and is registered before `ExtractionFaultRecorder`, because `afterStep` runs in reverse registration order (ADR-139 §4). `SignatureStepCompletion` is a private class in `RedundancyJobConfiguration` and does it for stage 4a. It re-reads `RedundancyGate` in `afterStep` and reaches the run through an `ObjectProvider`, because `RedundancyRun`'s constructor throws while the gate is shut. `SeedExtractionItemWriter` records seed extraction's completion itself (ADR-155).

**8. `InvocationRuns` is read where a mint must never happen.** `GenerationTasklet` reads this invocation's arrangement from it, to ask whether the approval names that arrangement. Its comment says why it must not reach `ArrangementRun`: that "would mint an arrangement behind the arrangement step's own gate" (ADR-154, Context §3). `NextAction` reads it for the closing line. These readers need a record that cannot mint.

## Decision

### §1. `StageModules` is ADR-058's table, in one place

`StageModules` is a package-private enum in `pipeline` with one constant per kind of run, in cascade order. Each constant carries the persisted stage name and the modules its implementation version spans, in the order `ImplementationVersions.of` is given them today:

| Constant | `stage()` | `modules()` |
| --- | --- | --- |
| `BYTE_LEVEL_REDUCTION` | `byte-level-reduction` | `corpus` |
| `EXTRACTION` | `extraction` | `extraction`, `similarity` |
| `CONTENT_CENSUS` | `content-census` | `similarity`, `extraction`, `pipeline` |
| `CONTENT_REDUNDANCY` | `content-redundancy` | `similarity`, `extraction`, `pipeline` |
| `SEED_MEASUREMENT` | `seed-measurement` | `embedding`, `extraction`, `pipeline` |
| `EMBEDDING_SCORING` | `embedding-scoring` | `embedding`, `extraction`, `pipeline` |
| `ARRANGEMENT` | `arrangement` | `synthesis`, `extraction`, `embedding`, `pipeline` |
| `GENERATION` | `generation` | `synthesis`, `extraction`, `embedding`, `pipeline` |

`modules()` returns an unmodifiable `List<String>` in that order. It is an enum rather than the plan's record with static constants, because a test or a guard can then enumerate the table (`values()`). The name follows the plan and borrows nothing from the instruments' "identity". Every `STAGE` and `*_MODULE` constant in the seven run classes and in `ByteLevelReductionTasklet` goes. `RunIdentityGoldenTest` pins every row of this table as literal text, and it is not edited.

### §2. `RunMint` is the one helper that mints a run

`RunMint` is a package-private final class in `pipeline`. It is not a bean. It is built inline, the shape `UpstreamRuns` and `InvocationRuns` already have: `new RunMint(ledger, implementationVersions, invocationRuns)`. It has three methods:

```java
WalkId finishedWalk(Path canonicalRoot, String beforeWhat);
RunId mint(StageModules stage, Record configConsumed, WalkId walk, Optional<RunId> upstream);
RunId mint(StageModules stage, String configConsumed, WalkId walk, Optional<RunId> upstream);
```

- **`finishedWalk`** throws `IllegalStateException` with the text `"no finished walk is recorded for " + canonicalRoot + "; census must run before " + beforeWhat`. Every current message fits that shape: `stage 1` through `stage 5`, `the documents can be arranged`, and `anything can be generated`. Each caller passes its own ending, so no message changes by a character.
- **`mint(…, Record, …)`** serialises the record with one `JsonMapper.builder().build()`. That is the same default mapper each run class builds for itself today. It then delegates to the string form.
- **`mint(…, String, …)`** calls `ledger.startRun(stage.stage(), implementationVersions.of(<stage.modules() in order>), configConsumed, walk, <upstream as a list of zero or one>)`. It records the returned id in `invocationRuns` before returning it, which is ADR-154 §1's "at the moment `startRun` returns". Stage 1 uses this form with its `"{}"`. An empty record is not used for stage 1, because Jackson refuses to serialise a type with no properties.
- **Upstream is `Optional<RunId>`,** not a list. Measured: no run names more than one upstream run, and ADR-089 says each names exactly one. The method turns it into the list `startRun` takes.

**Each stage keeps its own private `ConfigConsumed` record.** The record's field names and their order are the `config_consumed` text. They differ between stages (`root` in `ScoringRun`, `corpusRoot` in `ArrangementRun`), and merging them would re-mint for nothing. The records move with the code that mints (§3). None is merged or renamed, and no field is reordered.

**Stage 1 mints through `RunMint` from inside its tasklet,** building it from the tasklet's own `Ledger` and `ImplementationVersions` and from the job execution's context in the `ChunkContext`, as it builds `InvocationRuns` today. So `ByteLevelReductionTasklet`'s constructor does not change. Stage 1 has no gate and exactly one consumer, and three test classes run it by hand through that constructor (`ByteLevelReductionTaskletTest`, `ContentCensusTaskletTest` and `ExtractionItemProcessorTest`).

### §3. `StageRuns` hands a stage its run, and `InvocationRuns` stays as it is

**The holder is `StageRuns`,** a package-private `@Component @JobScope` class in `pipeline`. It replaces the seven run beans. It wraps `InvocationRuns` under a new name rather than extending it, for Context §8's reason. `InvocationRuns` is the record of what this invocation arrived at, and it is read in places where reading must never mint. If it gained the power to mint, "what did this invocation arrive at?" and "mint it now" would be one method call apart on one type. That is the mistake ADR-154 Context §3 names at `GenerationTasklet`. So `InvocationRuns` stays a thin record over the job execution's context, not a bean, with its two methods (`record`, `runOf`), and `UpstreamRuns` stays over it.

**One accessor per kind of run from stage 2 on.** Each accessor mints the run the first time it is called in an invocation, remembers it in a field, and returns the same id on every later call. The fields are the memory, and the job scope is what makes them last exactly one invocation, one instance per job execution. The two memories agree by construction: `RunMint` writes the id into `InvocationRuns` at the moment it writes the field.

| Accessor | Replaces | Upstream named as | `finishedWalk` ending |
| --- | --- | --- | --- |
| `extraction()` | `ExtractionRun` | `UpstreamRuns` → `byte-level-reduction` | `stage 2` |
| `contentCensus()` | `ContentCensusRun` | `UpstreamRuns` → `extraction` | `stage 3` |
| `contentRedundancy()` | `RedundancyRun` | `UpstreamRuns` → `content-census` | `stage 4` |
| `seedMeasurement()` | `SeedMeasurementRun` | `UpstreamRuns` → `content-redundancy` | `stage 5` |
| `embeddingScoring()` | `ScoringRun` | `seedMeasurement()` | `stage 5` |
| `arrangement()` | `ArrangementRun` | `embeddingScoring()` | `the documents can be arranged` |
| `generation()` | `GenerationRun` | `ArrangementGate.approvedArrangement(invocationRuns.runOf("arrangement"))` | `anything can be generated` |

**Each stage finds its upstream run exactly as it does today.** Where the class used `UpstreamRuns`, the accessor uses `UpstreamRuns`, so a missing upstream run is still `NoUpstreamRunException`. Where the class called the upstream bean, which mints if nothing has yet, the accessor calls the upstream accessor, which does the same. So this record changes neither how an upstream run is chosen (ADR-154 did that) nor how it is named, and ADR-099 as amended is not amended again.

**What the run beans handed out besides their own id moves as follows:**

- **`contentRedundancyFloor()`** returns the floor the stage-4 run was minted under. `RedundancyBoilerplate` and `RedundancySignatureItemWriter` read that value from `RedundancyRun.floor()` today. Reading `RedundancyGate` again instead would be two readings of one profile value (ADR-120). This accessor calls `contentRedundancy()` first.
- **`upstream(StageModules stage)`** returns `new UpstreamRuns(invocationRuns).runOf(stage.stage())`. It replaces `byteLevelReductionRunId()`, `extractionRunId()`, `stage3RunId()` and `redundancyRunId()`. Each of those was read through `UpstreamRuns` at mint, and `InvocationRuns` never replaces a key, so the value is the same. It only reads, so it may be called before a gate.
- **`canonicalRoot()`** returns `Walk.canonicalRoot(root)` and replaces `ExtractionRun.canonicalRoot()`.

**An input is read when its run is minted, never when the holder is built.** The holder reaches `ExtractorIdentity` through an `ObjectProvider`. That type is not a run, and its bean is `@Lazy` so that the context starts without Docling. The relevance floor is still read fresh from `ProfileStore` in `embeddingScoring()`, which is the freshness ADR-117 needs. The generation model's weights digest is still asked of `OllamaClient` in `generation()`. Each accessor reads its inputs in the order today's constructor does.

**The re-check goes.** `SeedMeasurementRun`'s read of stage 4's floor (Context §3) is not carried into `seedMeasurement()`. The refusal it stood for still happens before any row is written, and more precisely: with stage 4 gated there is no `content-redundancy` run in this invocation, so `UpstreamRuns` throws `NoUpstreamRunException` before `startRun`. The three gate reads that are inputs stay: the floor, the embedding model and the approved arrangement. Each still throws `IllegalStateException` if absent. Where today's message names a class that no longer exists ("`RedundancyRun` must not be instantiated while…"), it names the run instead ("the content-redundancy run must not be minted while…"). Those messages are unreachable past the caller's own gate, and no test asserts them.

**Consumers take `StageRuns` directly.** The scoped proxy is injected where a run bean or an `ObjectProvider<…Run>` was. Building the proxy mints nothing. No `ObjectProvider` of a run type remains in `pipeline`, and no `*Run` bean class remains.

### §4. What keeps "no run row behind a shut gate" (amends ADR-080)

ADR-080's rule is that a gated stage does not run, and no run row stands for it. Until now the rule was kept by where the mint sat: in a scoped bean's constructor, reached only through an `ObjectProvider` or a proxy, so that no one could reach it by accident before the gate. **From this record on, the rule is kept by the order of two statements in each stage's own code: the gate is checked, and only past it does the stage call its `StageRuns` accessor.** A step whose gate is shut returns before that call, so nothing is minted. A step that only reads (the arrangement gate's caller, the closing line, `upstream(…)`) reads `InvocationRuns` and cannot mint.

That makes the rule a property of each call site rather than of wiring, so it is pinned by behaviour at every gate but one (Tests below; the exception is #309, where today's code already breaks it), not by the shape of a bean. The one check a scoped constructor gave for free is kept as an input check instead: an accessor whose input is a gate's value throws `IllegalStateException` if called while that value is absent. So a caller that forgets its gate fails loudly, before `startRun`, rather than minting.

### §5. `TaskletSteps.once(...)` is the skip-if-done shell, as a function

```java
static RepeatStatus once(Ledger ledger, RunId run, String step,
                         StepAction alreadyRecorded, StepAction discard, StepWork work) throws Exception
```

The body: if `ledger.stepFinished(run, step)`, run `alreadyRecorded` and return `FINISHED`. Otherwise run `discard`, then `work`, and call `ledger.finishStep(run, step)` only if `work` returned `true`. Return `FINISHED`. Two package-private functional interfaces sit beside it in `TaskletSteps`. `StepAction` is `void run() throws Exception`. `StepWork` is `boolean run() throws Exception`, and returns `true` when the step's work under this run is complete.

**It has six parameters, not the plan's five,** and `work` returns a value, for two of Context §6's measurements:

- Every "already recorded" branch logs an operator-visible sentence through its own logger, and `arrangement.html` is written from that branch (ADR-154 §2). A shell that logged a sentence of its own would change operator text. `alreadyRecorded` keeps each sentence and each logger where it is.
- Three tasklets have a path that ends without recording completion. `work` returning `false` keeps that path. Clustering's empty-partition check sits between the finished check and the discard, so clustering passes a `discard` that does nothing and discards at the head of its own `work`, after the check. The order of the statements is then exactly today's.

It is a function and not a base class, because clustering and arrangement check their gates in a different order from the others and after different reads. Each tasklet keeps its own gate, its own `StageRuns` call and its own preamble, and calls `once` last.

All nine tasklets that hold the shell today use it: byte-level reduction, content census, redundancy resolution, seed/corpus comparison, embedding scoring, relevance scoring, clustering, arrangement and generation. Embedding scoring and generation pass a `discard` that does nothing. The three chunk steps are not tasklets and do not use it. Each keeps the `stepFinished` check in its reader or writer, where it is the same expression that decides whether anything is read (ADR-139).

**One ordering moves by a statement:** a step's "finished under run …" line is logged at the end of `work`, just before `once` writes the completion row, where today it is logged just after. The text of the line does not change, and nothing reads between the two.

`once` goes in `TaskletSteps` because that module already knows what a tasklet step is (ADR-131). It is the third method there. It does not absorb any completion listener.

### §6. One completion listener, gated by what this invocation holds

`SignatureStepCompletion` is deleted. `RunCompletion` becomes the one listener for both chunk steps that record completion through a listener:

```java
RunCompletion(Ledger ledger, StageModules stage, String step)
```

Its `afterStep` records `ledger.finishStep(run, step)` only when two things hold. The step's exit status must be `COMPLETED`. And this invocation must hold a run of `stage`, which `afterStep` reads as `new InvocationRuns(stepExecution.getJobExecution().getExecutionContext()).runOf(stage.stage())`. It holds no supplier and no provider, and it has no scope. It cannot mint, because it only reads.

The gate it applies is exactly the one needed. A step whose gate was shut minted nothing this invocation, so it records nothing, which is ADR-116's "a step that is gated records nothing". A step whose work was already recorded still called its accessor to find that out, so its run is held, and it records the same true thing again, which `finishStep` tolerates. For extraction, which has no gate, the run is always held once the reader was opened. For stage 4a it is held exactly when the reader passed the gate.

That is narrower than `SignatureStepCompletion` in one case only. If the profile's floor were edited from unreadable to a number while stage 4a was running, the old listener would read the new value in `afterStep`, mint a run and record a completion over a step that signed nothing. The new one records nothing, because the reader minted nothing.

**ADR-139 §4's order is unchanged.** `extractionStep` registers `RunCompletion` where it registers `extractionRunCompletion` today: after `extractionHealthCheckListener` and before `extractionFaultRecorder`. So in `afterStep` the fault recorder still runs first. `RunCompletion` is built in the step's bean method rather than as a `@StepScope` bean of its own, since it needs nothing scoped. `redundancySignatureStep` registers it where `SignatureStepCompletion` is, after `SignatureStepBoundaryLog`.

### §7. Every step is named once, and the job is one configuration (amends ADR-131)

**`StepNames`** is a package-private final class in `pipeline` holding the fifteen persisted step names as `static final String` constants, in job order, each value byte-identical to today's:

`CENSUS` `census` · `BYTE_LEVEL_REDUCTION` `byte-level-reduction` · `EXTRACTION` `extraction` · `CONTENT_CENSUS` `content-census` · `REDUNDANCY_SIGNATURE` `redundancy-signature` · `CONTENT_REDUNDANCY` `content-redundancy` · `SEED_EXTRACTION` `seed-extraction` · `SEED_CORPUS_COMPARISON` `seed-corpus-comparison` · `EMBEDDING_SCORING` `embedding-scoring` · `RELEVANCE_SCORING` `relevance-scoring` · `RELEVANCE_FLOOR` `relevance-floor` · `CLUSTERING` `clustering` · `RELEVANCE_REPORT` `relevance-report` · `ARRANGEMENT` `arrangement` · `GENERATION` `generation`.

Seven of these values are also stage names in `StageModules`. They are two persisted facts that happen to be equal: a `finished_step.step` value and a `run.stage` value. Each is declared in its own table, and neither is defined as the other. Every `STEP`, `STEP_NAME`, `SIGNATURE_STEP` and step-naming `STAGE` constant goes, together with the `"census"` literal. After the change, no `pipeline` source outside `StageModules` and `StepNames` holds a string literal equal to a persisted stage or step name. Wherever a name is used, including the three chunk-step configurations, the `stepFinished` and `finishStep` calls, and `StageRuns`, it is read from one of those two tables.

**`CensusJobConfiguration` is renamed `VesperaJobConfiguration`,** for what it holds: the job named `vespera`, its fifteen steps in order, and the clock. The ten one-bean classes of Context §5 are folded into it as ten `@Bean Step` methods. Each is still one `TaskletSteps.taskletStep(StepNames.X, …)` call, and each bean keeps its name (`byteLevelReductionStep` and the rest), so `vesperaJob`'s parameters and order are untouched. Census's step keeps `taskletStepOutsideAnyTransaction` (ADR-055).

**The three configurations that hold more than a step stay,** under their names: `ExtractionJobConfiguration`, `RedundancyJobConfiguration` and `SeedExtractionJobConfiguration`. They hold a chunk step's reader, writer, listeners and, for extraction, the extractor's bean and its constants (`CHUNK_SIZE`, `CONVERSION_CONCURRENCY`, ADR-140). Folding them would move three hundred lines of wiring to no purpose. They take their step names from `StepNames`.

**ADR-131's second reason is withdrawn.** It kept a class per stage as "the place each stage's step is named". A step's name now has one place, `StepNames`. A plain step's construction has one place too, `VesperaJobConfiguration`, which also holds the job's order. A class that held one line naming a step it did not own the name of was a third place, not the first. ADR-131's `TaskletSteps` decision stands, and grows by §5.

### §8. What does not move

- **Run ids.** Every `config_consumed` text and every module list is byte-identical, and `RunIdentityGoldenTest` passes unedited. This change edits `pipeline`, which stages 3 to 6b name in their implementation versions. So those runs are minted once afresh under the new build, and everything downstream of them follows (ADR-058, ADR-048). Stages 1 and 2 name no `pipeline`, and this change touches neither `corpus`, `extraction` nor `similarity`, so their runs continue. Land it at a corpus boundary.
- **Persisted names.** Every `run.stage` and every `finished_step.step` value is unchanged (§1, §7).
- **Gates, faults and exit codes.** Every gate shuts where it shuts, and every refusal refuses before `startRun`. `NoUpstreamRunException` stays the one "cannot name one run" exception (ADR-154). Exit codes are ADR-141's.
- **Operator text.** Every log line and gate sentence keeps its text and its logger. Only §5's single reordering moves, and the moved line's text does not change. The `IllegalStateException` texts that name a deleted class change (§3). They are defect messages that no gate path reaches.
- **The job's shape.** The step order, ADR-055's transaction attribute, ADR-140's chunk size and concurrency, and ADR-139's listener order are unchanged. Nothing new writes from a worker thread (ADR-127).

## Alternatives rejected

**Extending `InvocationRuns` into the holder.** Rejected for Context §8's reason. It would also turn ADR-154 §1's deliberately bean-less record into a job-scoped bean with a dozen collaborators, and every test that builds one with `new InvocationRuns(new ExecutionContext())` would need all of them.

**Using `InvocationRuns` as the holder's only memory.** Checking `runOf(stage)` before minting would have given the holder no fields. That form cannot remember the stage-4 floor the run was minted under, and there is no second place to keep that value. Keeping fields and recording ids through `RunMint` is two memories that agree by construction.

**Putting each stage's mint in its first consumer.** This spreads the private records back across the tasklets, and it ties "who mints" to step order. Five steps write under the scoring run, and two under the redundancy and measurement runs, so the first of them in the job would have to mint and the rest would have to know it had.

**Keeping `ObjectProvider` beside the explicit call, "so that losing `@JobScope` cannot silently mint".** `GenerationRun` carries that argument in its javadoc. The holder has no constructor that mints, so losing its scope would mint nothing behind a gate. It would instead hand a later invocation in the same process the runs of the first one. Every whole-job test that runs two invocations over two archives in one context would fail on that (`EarlyStageRunsInvocationTest` among them). The behaviour pins at every gate are the guard against an early mint, and they are pinned directly.

**A step shell as a base class.** Rejected by the plan, and for its reason: clustering and arrangement check their gates in different orders.

**Merging the three chunk-step configurations too.** Rejected in §7.

## What the implementation owes

- `StageModules`, `RunMint`, `StageRuns`, `StepNames`, `TaskletSteps.once` (with `StepAction` and `StepWork`), the new `RunCompletion`, and `VesperaJobConfiguration`, all named as above. The test slice names these classes, so another name is a change to this record.
- The seven `*Run` classes, the ten one-bean configuration classes and `SignatureStepCompletion`, deleted.
- Every consumer taking `StageRuns` in place of a run bean or an `ObjectProvider<…Run>`, and calling its accessor after its own gate.
- Every name read from `StageModules` or `StepNames`.
- The javadoc that explains a behaviour by `@JobScope`, by the `ObjectProvider` or by a run class (`InvocationRuns`, `UpstreamRuns`, `ExtractionFaultRecorder`, the gates), rewritten to say §4.
- The measured `src/main` line delta, stated in the pull request against the plan's estimate of about −1,100. The classes deleted outright come to 1,119 lines (884 + 235), before `SignatureStepCompletion` and the plumbing, and before the four new classes are added.

## Tests

**Pinned before the refactor, green on `5cba9ef`, and not edited by it:**

- `RunIdentityGoldenTest`: every `config_consumed` text and every module list, unedited.
- `NoRunBehindAShutGateTest` is new. At each gate a stage stands behind, one invocation with that gate shut leaves exactly the runs in front of the gate, and none behind it. Each case opens every gate except the one it is about. The cases are: stage 4's floor unset with a seed folder and a model named; no seed folder named with a model named; the embedding model unset with every earlier gate open; nothing grouped; and no approval.
- **One gate is left unpinned, because today's code breaks the rule there.** With a model named and no seed producing text, `RelevanceReportTasklet` checks only `StageFiveGates.modelAndSeedWalk` and then reaches the scoring run. That mints a `seed-measurement` run and an `embedding-scoring` run behind ADR-083's gate, and the invocation still exits 0. Measured, and filed as [#309](https://github.com/algernon28/vespera/issues/309). `SeedExtractionInvocationTest` pins "no run" for that gate only with no model named, which is why it never showed. This record keeps every gate's outcome, and the holder reproduces this one faithfully: the step calls `embeddingScoring()` after its own gate, as it calls `scoringRun.getObject()` today. The fix is #309's to decide, because it changes which sentence the step logs.
- `FinishedStepsInvocationTest` is new. Over two invocations with every gate open and the arrangement approved between them, the `finished_step` rows are exactly the twelve `(run.stage, step)` pairs the job records today. That is every step but three: census, which has no run, and `relevance-floor` and `relevance-report`, which record none (ADR-118). It pins `once`, the merged `RunCompletion` and every persisted step name at once. No test asserted stage 4a's completion before this one.
- `ExtractionFaultInvocationTest.theVerdictIsCommittedBeforeTheStepIsRecordedAsComplete`, through `StepCompletionOrderProbe`: ADR-139 §4's order, unchanged.
- `RepeatedInvocationTest`: a second invocation repeats nothing, and a step whose work is missing does it again.

**Moved by the analyst, so that no claim depends on a class this record deletes:**

- Every test that named a persisted stage or step through a production constant (`ArrangementRun.STAGE`, `SeedExtractionJobConfiguration.STEP_NAME` and the rest, in thirteen test classes and `StepCompletionOrderProbe`) now writes the literal. A persisted name is pinned by its value, not by a constant that moves with the code.
- `ExtractionRunTest` (four tests) and `ContentCensusRunTest` (two) tested classes this record deletes. Their claims are in `EarlyStageRunsInvocationTest`'s five tests, asserted against the rows a whole invocation writes: the engine and the confidence floor recorded, each run naming this invocation's upstream, and each archive read as itself. The two "whichever archive" tests became one, because a single pair of invocations shows both stages reading their own archive. That class sets the floor to 0.5 in `profile.yaml` before its context starts, so the floor is read the way an operator's would be.
- `GenerationRunTest` (four tests) called `GenerationRun.configConsumed` directly. Its model-name, digest and serving-address claims were already made against the row by `GenerationIdentityInvocationTest`. Its window and approved-arrangement claims are now there too.
- `ContentCensusTaskletTest` and `ExtractionItemProcessorTest` construct the class under test by hand. Each now names a deleted type in its fixture only. In `ContentCensusTaskletTest` that is `contentCensusOver` and the `ExtractionRun` line in `walkedThroughExtractionWithScores`. In `ExtractionItemProcessorTest` it is `corpusOf`, `processorOver` and the `Corpus` record's `stage2` component. Every claim reads its run ids through `InvocationRuns`, and none reads a run bean.

**Changed in the implementing change, by the analyst, before the gate.** Three test files cannot be written today in a form that compiles both before and after this change, because they name, as Java types, classes that exist on only one side of it. None of them carries a claim:

- `CascadeSliceTest`'s `@Import` list: take out the seven run classes, the ten configuration classes and `CensusJobConfiguration`, and add `StageRuns` and `VesperaJobConfiguration`. ADR-153's Consequences already name this edit.
- The fixture lines in `ContentCensusTaskletTest` and `ExtractionItemProcessorTest` named above.

Plan §4.10 gives moving a test to the analyst and never to the implementer. So the implementing branch goes back to the analyst for exactly these lines before the architect sees it.

**Checked once the change exists, and written then, green:** a guard that no `pipeline` source names `ObjectProvider<` of a type in `pipeline` whose simple name ends in `Run`, and a guard that no `pipeline` source outside `StageModules` and `StepNames` holds a string literal equal to a persisted stage or step name. Neither can pass on `5cba9ef`, so neither is in the tree before the change it checks.

## Consequences

**Minting is one sequence, written once.** A new stage adds one row to `StageModules`, one accessor with its private record to `StageRuns`, and one name to `StepNames`, not one more class with its own mapper and module constants.

**No run row behind a shut gate is now a claim each call site makes, and a behaviour test checks each one.** That is weaker structurally, since nothing in the wiring stops an accessor being called too early, and stronger where it is observed, since every gate but #309's now has its own pin. Pinning them is also how #309 was found: the old wiring's guarantee had a hole in it that no test looked for. An early call fails loudly where the accessor needs a gate's value. It would pass silently only where the accessor needs none: extraction and content census, which have no gate.

**`InvocationRuns` keeps meaning one thing.** It is what this invocation arrived at, read without side effects. `StageRuns` is where a stage asks for its run.

**Reversal is expensive, and not by line count.** Undoing this re-mints stages 3 to 6b a second time, since both directions edit `pipeline`.
