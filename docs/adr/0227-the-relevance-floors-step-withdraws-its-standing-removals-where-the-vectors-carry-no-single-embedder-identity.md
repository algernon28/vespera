# ADR-227 — The relevance floor's step withdraws its standing removals where the vectors carry no single embedder identity, as in every other case, and `FloorReach` no longer answers whether to

> **Partly amended — see [ADR-228](0228-a-scoring-run-names-the-embedding-models-artefact-and-reads-the-vectors-of-one-embedder-identity.md).** The rule stands: the step withdraws every standing removal in every case. What no longer holds as written, as of that record's build, is when the vectors carry no single embedder identity: a scoring run names the digest and weight dtype of the embedding model it was scored under, and the step reads the identity under those, so the vectors another pull left under the same name neither withdraw a run's removals nor keep the floor from applying. That touches Context's *"Whether the vectors carry one identity is a fact about the database and not about the run"* and *"Nothing recorded says so"*, §1's case, the first and third Consequences, one row of Tests and the first bullet of *What this does not decide*, each quoted in ADR-228.

- **Date**: 2026-10-10
- **Status**: accepted on 2026-10-10, on two kinds of answer kept apart in §6: **the operator's answers**, and **the build-level calls** of the session that drove #486, to which the operator handed every further decision on the ticket. The commit that carries this record writes it and its tests and no line of `src/main`; *What the commit that builds `src/main` owes* lists the rest. Until that commit two tests fail (Tests); the test tree compiles.
- **Built**: on 2026-10-10 in the change that carries this record (#486), in two commits after the one that wrote it (021fcce9 and 248477a7 after f83510bb), the second putting the writing branch first on the gate's amendment; the build edited no test; `./mvnw verify` ran 1,667 unit tests and 23 integration tests with none failed or skipped after the first, and `./mvnw test` the same 1,667 after the second.
- **Amends**: [ADR-226](0226-the-eight-rules-in-pipeline-live-in-extraction-embedding-synthesis-and-profile-and-no-stages-version-names-pipeline.md), in these sentences and no others. §1's table, row 2, 3: *"and that nothing is removed or withdrawn where the vectors carry no single embedder identity"*. §3: the bullet *"`boolean withdrawsStandingRemovals()`: false only where there is no single embedder identity"*; *"`embedding.FloorReach` answers the two actions the step takes"*; *"A type that answers the actions leaves the step a straight line: withdraw if told to, remove below the number if one is given"*; *"withdraws `BELOW_THRESHOLD` where told to"*; *"each chosen by what the reach answered"*, of the step's four lines, the one for no single identity being chosen by the identity the step read (§3); and the paragraph *"**Today's behaviour is kept exactly**, including that the case with no single identity withdraws nothing, which leaves removals standing after a pull that adds a second identity. That is a defect in the direction ADR-042 warns of"*, both the behaviour and the citation (§1, §4). §9: *"it withdraws and writes the removals it is told to"*, the step now withdrawing without being told to. Tests: the rows for `embedding.FloorReachTest`, `pipeline.RelevanceFloorTest` and `MethodsNothingShippedCallsAreGoneTest` (Tests). *What this does not decide*: its first bullet, #486, which this record decides. Its homes, its other seven rules, `FloorReach.of`, `removesBelow()`, `floor()`, `answeredUnder()` and `REASON` stand.
- **Applies, and does not amend**: [ADR-118](0118-the-answers-a-person-gave-never-join-a-runs-identity-so-the-two-steps-that-read-them-record-no-completion.md)'s *"discarded **before the floor's state is consulted rather than inside the one branch that writes**. So the removals standing under a run always reflect the answers and the number as they are now"*, which the step did not do in one case; and [ADR-088](0088-the-relevance-threshold-is-read-off-a-stratified-sample-of-sixty-labels-and-an-unset-floor-does-not-stop-the-run.md) §3, *"Set under a *different* `embedder_identity` is treated identically to unset"*, read here for a scale that is not known to be one. ADR-226's *Keeps* line on ADR-118, *"the floor's step withdraws every removal it has standing before it removes any"*, is true once this record is built.
- **Rests on**: [ADR-216](0216-nothing-ships-that-no-decision-requires-and-nothing-calls-a-javadoc-states-its-own-contract-and-agents-md-carries-no-history.md), nothing ships that nothing calls; [ADR-117](0117-the-relevance-floor-joins-the-scoring-runs-identity-so-a-changed-threshold-is-a-different-run.md) and [ADR-156](0156-a-runs-survivors-are-read-through-its-upstream-runs-and-a-verdict-under-any-other-run-stays-recorded-and-removes-nothing.md), for how a run with removals standing is arrived at again; [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) and [ADR-048](0048-walk-and-run-identity.md), for which run ids move; [ADR-042](0042-ledger-owns-the-verdict-vocabulary-not-the-cascade.md), for the direction it warns of (§4). No archive, working directory, database, log, report or deliverable of the operator's was opened for this record ([ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md)).
- **Decides** [#486](https://github.com/algernon28/vespera/issues/486).

## Context

`RelevanceFloorTasklet` reads the embedder identity the vectors of the profile's embedding model carry (`RelevanceDistribution.embedderIdentityFor`), which is empty where none or more than one answers to the name. Where it is empty the step logs that it removed nothing and returns, **before** `discardVerdicts(scoring, BELOW_THRESHOLD)`. So the `below-threshold` verdicts the scoring run already carries stay, and their occurrences stay removed, in an invocation whose own line says the threshold cannot be applied. ADR-226 §3 moved that behaviour into `embedding` unchanged, as `FloorReach.withdrawsStandingRemovals()`, and named #486.

**Confirmed by a failing test, not only read.** `RelevanceFloorInvocationTest.aSecondEmbedderIdentityWithdrawsTheRemovals`: one identity, a floor above every score, an answer under that identity, removals written; every stored vector then written once more under a second identity of the same embedding model; a third invocation. The two occurrences of its corpus still carry `below-threshold`.

**Whether the vectors carry one identity is a fact about the database and not about the run.** `vector` has no run id, and `embedderIdentityFor` reads every row whose identity begins with the embedding model's name. `relevance_score` has no column for the identity a score was computed under. A run with removals standing has its `embedding-scoring` step recorded as finished, so it never writes a second identity itself. The second one is written by another run over the same database: the embedding model is pulled again and reports a new digest; a run whose embedding step is not finished, a new scoring run after the floor was edited (ADR-117) among them, embeds under the new identity, `ChunkEmbedder` finding nothing under it; and the floor put back arrives at the first run again (ADR-156), with its removals. That run's scores were all computed under the first identity. Nothing recorded says so, and the step cannot tell that run from one whose scores were not.

## Decision

### 1. The step withdraws every `below-threshold` removal the scoring run has standing, in every case

Where the vectors of the profile's embedding model carry no single embedder identity, the step withdraws the removals standing under the scoring run and makes none, as it does where no number is set and where the number was answered for under another identity. There is no case left in which the step ends with a removal it did not make in that invocation.

The grounds are ADR-118's and ADR-088's. A removal stands under a run only while the step can say, now, that the number is on the scale the run's scores are on. With no single identity it cannot say which scale that is, and ADR-088 §3 treats a number on a scale that is not this one as unset: nothing is removed.

### 2. `FloorReach` no longer answers whether to withdraw

`withdrawsStandingRemovals()` and the field behind it go (ADR-216): with one answer in every case, no caller branches on it. `FloorReach` answers one action, `OptionalDouble removesBelow()`, with `floor()`, `answeredUnder()` and `REASON` as they are. `FloorReach.of(Optional<String>, OptionalDouble, Supplier<List<RelevanceLabel>>)` keeps its signature and its three cases: with no identity the answers are not read and nothing is removed.

### 3. The step, in `pipeline`

`RelevanceFloorTasklet` reads the embedder identities, asks `RelevanceFloor.reachFor`, and then calls `ledger.verdicts().discardVerdicts(scoring, VerdictKind.BELOW_THRESHOLD)` once, before any branch. The order of what it reads is unchanged: the identities, the recorded answers where there is an identity and a number, then the withdrawal.

**It writes a removal where `reach.removesBelow()` is present, and on no other condition.** That is `embedding`'s answer, and `FloorReachTest` holds that it is empty with no identity. Which of the three other lines is written is chosen by what was read and decides no verdict: the line for no single identity where the identity is empty, then the line for no number, then the line for a number answered for elsewhere.

**The four lines stay word for word** (§6, call 2). The line for no single identity says the step *"removed nothing"*, which is now true of what stands as well as of what the invocation did. It does not say that removals were withdrawn or how many, as none of the others does; ADR-118 left that message out for the case it decided, under *What this does not decide*.

`pipeline` still holds no rule (ADR-222 §1). An unconditional withdrawal of the step's own rows is ADR-116's second rule as ADR-118 kept it, and ADR-226 §10 already lists the kind the step withdraws as wiring.

### 4. The direction, stated plainly

ADR-042's surviving sentence is about the verdict vocabulary: *"the failure mode of drift is asymmetric (over-publishing)"*. A removal left standing publishes less. Withdrawing it publishes more. **So this decision moves in the direction ADR-042 warns of, and not away from it.** ADR-226 §3's *"a defect in the direction ADR-042 warns of"*, and #486's *"ADR-042's direction"*, cite it the wrong way round, and are amended here as a miscitation.

It is taken all the same, for §1's reason: the alternative keeps occurrences out of the deliverable on a number the step itself says it cannot place on a scale, where ADR-088 chose, for exactly that doubt, the direction that loses no part of the archive. The operator was told which way ADR-042 cuts when the question was put (§6, answer 1). What it costs is in Consequences.

### 5. Which run ids move

A stage's implementation version is the last commit touching a module `StageModules` names for it (ADR-058), and a run's id is a hash of its upstream runs' too (ADR-048).

**The build touches two modules**: `embedding` (`FloorReach`) and `pipeline` (`RelevanceFloorTasklet`). It touches nothing of `corpus`, `extraction`, `similarity`, `synthesis`, `profile` or `ledger`.

**Read against `StageModules` at `066040d`**, where byte-level reduction names `corpus`; extraction `extraction` and `similarity`; content census and content redundancy `similarity` and `extraction`; seed measurement and embedding scoring `embedding` and `extraction`; arrangement `synthesis`, `extraction` and `embedding`; generation `synthesis`, `extraction`, `embedding` and `profile`:

- **Byte-level reduction, extraction, content census and content redundancy keep their run ids.** None names `embedding`, and no stage names `pipeline`.
- **Seed measurement and embedding scoring are minted again**, by the commit to `embedding`.
- **Arrangement and generation are minted again**, each naming `embedding`, and through their upstream runs.

**What that costs on its own**, on a working directory an earlier build had run: stage 5 runs again under new runs and finds its vectors in the cache where the embedder identity is the one they were stored under; 6a mints a new arrangement, which `arrangementApproved` must name; and 6b asks for every synthesis doc again.

**It does not ship on its own.** By the operator's decision #486 ships in the one build with #476, whose record is ADR-225 and whose change is to `similarity`; no build is cut until both are on `main` (§6, answer 3). ADR-225 is not on the branch this record was written on and was not read. As far as `StageModules` shows, a commit to `similarity` mints extraction, content census and content redundancy again, and through their upstream runs every stage after them, which are the four this record moves. So in that one build the moves coincide and this record adds none, **on the condition that no build with one change and without the other is run over a working directory first**; nothing in the repository prevents that.

**A removal standing under an earlier scoring run needs no repair.** The new scoring run starts with none, and the earlier run's verdicts remove nothing from a run that does not name it upstream (ADR-156).

**What does not move**: no DDL, so no schema version; no cache key; no `run.stage` value, no `finished_step.step` value, no recorded setting, no verdict reason and no line of the log.

### 6. The operator's answers, and the build-level calls

**The operator's answers, given on 2026-10-10** through the session that drove #486, each the recommendation put:

1. **The case with no single identity always withdraws the standing removals**, and `withdrawsStandingRemovals()` and its field go. The question said that withdrawing publishes more, the direction ADR-042 warns of, and that ADR-118 and ADR-088 §3 are what support it. Put and not taken: keeping the removals and amending ADR-118 to except the case; and recording the identity a run's scores were computed under and deciding on that, which needs DDL and is #488's ground.
2. **The two findings of the design pass are filed and not decided here**: [#488](https://github.com/algernon28/vespera/issues/488) and [#489](https://github.com/algernon28/vespera/issues/489) (*What this does not decide*).
3. **#486 ships in the one build with #476**, and no build is cut until both are on `main`.
4. **Every further decision on the ticket is the session's.**

**The build-level calls**, made by that session and reported to the operator:

1. **The direction is recorded as it is** (§4), and ADR-226 §3's sentence on ADR-042 is amended as a miscitation.
2. **The step's line for this case stays word for word**, and no line is added for a withdrawal.
3. **`FloorReach.of` keeps its first case**, so no answer is read where there is no identity, and the withdrawal is one unconditional call in `pipeline`, not an answer of `FloorReach` that is always true.
4. **The step writes on `removesBelow()` alone** (§3), so that no condition of `pipeline`'s stands between `embedding`'s answer and a verdict.
5. **The invocation test stores the second identity itself**, by copying every vector under it, and takes it away again. It stands for the sequence of Context, which no fixture here can make: the scripted embedder reports one identity.
6. **Withdrawal is held by invocation and not on `FloorReach`**, which no longer answers it (Tests).
7. **ADR-118 and ADR-088 are applied and not amended**; ADR-226 carries a note at its head, as ADR-222 does for ADR-226.
8. **`docs/decision-ledger.md` is not edited.** It is closed to new entries; this record is indexed in `docs/adr/README.md` alone.

## Consequences

- **A second embedder identity for the profile's embedding model anywhere in the database un-removes every `below-threshold` occurrence of the scoring run the invocation arrives at.** That includes a run whose scores were all computed under one identity and whose number was read off that identity's answers: the step cannot tell, and withdraws. **The only thing that says so is the line the step already writes**, *"stage 5's relevance-floor step removed nothing: the vectors under … carry no single embedder identity …"*. It names no count and does not say that anything was withdrawn.
- **More is published, not less.** That is the direction ADR-042 warns of (§4).
- **The removals do not come back by themselves.** While two identities answer to the embedding model's name the floor removes nothing under any run, whatever the number and the answers.
- **The step's four lines now each describe what stands**, and ADR-118's sentence holds without exception.
- **Run ids move once, for seed measurement and every stage after it**, and in the one build with #476 they move with that change's (§5).
- **`FloorReach` answers one action.** A change to when the floor removes is still a change to `embedding`, which moves the run ids it should; when the step withdraws is no longer a question anything answers.
- **Each test edited keeps what it claimed of removing.** What four of `FloorReachTest`'s claims and one of `RelevanceFloorTest`'s said of withdrawing is held by invocation for two of the four cases, and by nothing for the other two (Tests).

## Tests

Written with this record, before `src/main`. The test tree compiles against `src/main` as it stands: no test names a type or method the build adds.

| Class | What it holds |
| --- | --- |
| `pipeline.RelevanceFloorInvocationTest` | a test added, `aSecondEmbedderIdentityWithdrawsTheRemovals`: with the floor's removals standing and every vector then stored under a second identity of the same embedding model, the next invocation leaves no `below-threshold` verdict, and the scoring runs are the ones there were before, so the removals were withdrawn by the run that made them |
| `embedding.FloorReachTest` | with no single identity nothing is removed and no answer is read; with no number nothing is removed and no answer is read. Its four claims on `withdrawsStandingRemovals()` are gone with the method; every claim on `removesBelow()`, `floor()`, `answeredUnder()`, `REASON` and how often the answers are read stands |
| `pipeline.RelevanceFloorTest` | its seventh claim is that with no single identity the floor removes nothing; *"neither removes nor withdraws"* is gone |
| `MethodsNothingShippedCallsAreGoneTest` | that `FloorReach` declares `removesBelow` and `floor`, and neither `removesAnything` nor `withdrawsStandingRemovals`; that `RelevanceFloor$State` is gone |

**Two tests fail until the build**: `RelevanceFloorInvocationTest.aSecondEmbedderIdentityWithdrawsTheRemovals`, on *"nothing stands removed any more"*, and `MethodsNothingShippedCallsAreGoneTest.theFloorNoLongerSaysWhetherItRemovesAnything`, naming the method that is still there.

`PipelineHoldsOnlyTheRulesOnRecordTest` and `EveryStatementThatSortsIsRecordedTest` are not edited: `RelevanceFloorTasklet` names the same types after the build, and no statement is added to a class or changed.

**What no test holds**: that the removals are withdrawn where no number is set, or where there is a number on this run's own scale before it removes again, since a changed number is another scoring run (ADR-117) and the second leaves the same verdicts either way; the withdrawal is one call before any branch, and the two invocation tests that reach it, this record's and ADR-118's `anAnswerGivenUnderAnotherModelWithdrawsTheRemovals`, hold it for the other two cases. That the line for no single identity is written, or word for word: no test held it before. The sequence of Context through a real pull.

## What the commit that builds `src/main` owes

- **`embedding/FloorReach`**: `withdrawsStandingRemovals()` and the `singleIdentity` field and constructor parameter gone. `of`, `removesBelow()`, `floor()`, `answeredUnder()` and `REASON` unchanged, in signature and in what they answer. The class javadoc's first paragraph no longer says *"the two actions"*, and its last, *"Where the vectors carry no single embedder identity nothing is decided either way: no removal is made and none already standing is withdrawn, and the answers are not read"*, says instead that no removal is made and the answers are not read.
- **`pipeline/RelevanceFloorTasklet`**: `ledger.verdicts().discardVerdicts(scoring, VerdictKind.BELOW_THRESHOLD)` called once, after `relevanceFloor.reachFor(currentIdentity, STAGE)` and before any branch; no `return` between the two. The branch that counts and writes entered on `reach.removesBelow()` present and on nothing else. The four lines word for word, the one for no single identity being: `"stage 5's relevance-floor step removed nothing: the vectors under {} carry no single embedder identity, so there is no one scale for a threshold to be on. A threshold is only applied where the scale it was read off is known to be this one."`, with the embedding model's name. The comment above the withdrawal keeps its reason (ADR-118).
- **Javadoc that says otherwise, where it stands**: `pipeline/RelevanceFloor`, should it name the two actions.
- **Edit no test.** A test the build finds it has to edit is a finding for the analyst.
- Verify with `./mvnw verify` under Java 26, and the docs gates.

## What this does not decide

- **[#488](https://github.com/algernon28/vespera/issues/488)**: a run scored or clustered after such a pull reads the vectors of both identities for each content hash, `VectorCache.vectorsFor` matching by the embedding model's name and not by the identity. Read from the code, not reproduced. Whether a run should record the identity its scores were computed under, which would let the step tell the run of Consequences' first bullet from the others, is that ticket's.
- **[#489](https://github.com/algernon28/vespera/issues/489)**: a survivor the floor's step un-removes after the clustering step recorded its completion has no cluster. What 5f, 6a and 6b do with it is not traced. ADR-118's withdrawal already reaches that state; this record adds one more way into it.
- **Whether the operator should be told that removals were withdrawn, and how many.** ADR-118 left it open and it stays open.
- **`RelevanceReportTasklet`'s notice of a floor not applied**, which is not written where there is no single identity. It is unchanged.
- **When the build ships**, beyond with #476.
