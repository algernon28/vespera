# ADR-230 — The clusters under a scoring run are of its survivors as they stand: the clustering step does its work again where the floor's step changed them, and the arrangement's identity names the removals standing

- **Date**: 2026-10-10
- **Status**: accepted on 2026-10-10, on two kinds of answer kept apart in §8: **the operator's word**, which is one sentence and decides nothing of the design, and **the session's calls**, which are every decision below. The change that carries this record builds it. **Amended the same day, in that change**: the first build made the three pass and failed two tests this record had not named, and §2's test of a count gave way to the two questions it asks now (Context, *What the first build showed*; §8, calls 12 to 15).
- **Built**: in the change that carries this record: `Verdicts.verdictsUnder`, `StageRuns`' `standingRemovals`, and `ClusteringTasklet` with §2's two questions. `./mvnw verify`: 1,676 unit and 23 integration tests, none failed or skipped.
- **Amends**: [ADR-118](0118-the-answers-a-person-gave-never-join-a-runs-identity-so-the-two-steps-that-read-them-record-no-completion.md), in one sentence: *"Each of those consumes only what its run's identity names or what the upstream chain names for it"*, said of the six steps left with a completion record. It does not hold of `clustering`, which reads the survivors the floor's step leaves, and those follow the answers (§1). Its two named steps, its refusal to put the answers in a run's identity and its rule for the floor's step stand. Its Consequence *"No profile edit, no new run id, no re-score"* stands for the scoring run and the scores; an arrangement run may now be minted (§3).
- **Amends**: [ADR-116](0116-a-runs-completion-is-recorded-per-step-because-several-steps-share-one-run.md)'s first rule, *"A step whose completion is recorded does no work and writes no rows"*, for `clustering` alone and in one case (§2). [ADR-087](0087-clusters-are-modularity-communities-over-a-k-nearest-neighbour-graph-built-in-blocks.md)'s *"Per ADR-077, a re-run writes a fresh row set under its own run id"*, for the same case: the row set is written again under the same scoring run, as the floor's verdicts are since ADR-118. **[ADR-077](0077-a-regenerated-measurement-is-a-fresh-row-set-under-its-own-run-id.md) itself is not amended**: its decision is that an earlier run's rows are never rewritten or deleted by a later run's, and no run's rows are touched here but the scoring run's own. [ADR-157](0157-a-stage-asks-for-its-run-after-its-own-gate-one-helper-mints-every-run-and-every-step-is-named-once.md) §2 as `StageRuns` carries it, *"Stage 6a's own `ConfigConsumed`, unchanged"*: it gains one member, written only where it is not zero (§3).
- **Amends**: [ADR-229](0229-every-runs-rows-are-kept-and-the-database-file-is-not-made-smaller-a-run-over-an-earlier-walk-is-never-arrived-at-again-and-is-still-read.md), in two sentences and in nothing it decides (§6). §1's *"a step discarding its own unfinished work under its own run (ADR-116)"*, and, without *under its own run*, the guard's javadoc: two shipped deletes are also of work that was finished, the floor's step withdrawing a finished invocation's removals (ADR-118) and the clustering step's. And Consequences' *"A table that gains or loses a reference to `run` fails `EveryTableKeyedByARunIsOnRecordTest`"*: only a reference written `run_id TEXT NOT NULL REFERENCES run (id)`, as its own *What no test holds* says.
- **Amends**: `AGENTS.md`, in *"a step discarding only its own unfinished work under its own run (ADR-116)"*, for the same reason, and in its count of decisions; and `CONTEXT.md`'s Verdict entry, in *"redoing its own unfinished work under the same run (ADR-116)"*, which has not held of the floor's step since ADR-118 (§8, call 18).
- **Applies, and does not amend**: [ADR-107](0107-the-arrangement-gate-approves-a-named-6a-run-and-the-path-becomes-five-invocations.md), an approval is of one named arrangement; [ADR-111](0111-a-cluster-fault-is-a-row-in-synthesis-and-a-re-run-under-the-same-id-repairs-rather-than-regenerates.md), a synthesis doc is never written twice or discarded; [ADR-156](0156-a-runs-survivors-are-read-through-its-upstream-runs-and-a-verdict-under-any-other-run-stays-recorded-and-removes-nothing.md), a run arrived at again finds its work recorded; [ADR-088](0088-the-relevance-threshold-is-read-off-a-stratified-sample-of-sixty-labels-and-an-unset-floor-does-not-stop-the-run.md), no part of the archive is lost to a number that does not apply; [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md), for which side of `pipeline` the change falls on (§5).
- **Rests on**: the code at `35a5a6ba`; a probe of four invocation sequences run for this record and then deleted (Context); [ADR-227](0227-the-relevance-floors-step-withdraws-its-standing-removals-where-the-vectors-carry-no-single-embedder-identity.md), which filed this question; [ADR-042](0042-ledger-owns-the-verdict-vocabulary-not-the-cascade.md), for the direction it warns of (§7). No archive, working directory, database, log, report or deliverable of the operator's was opened ([ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md)).
- **Decides** [#489](https://github.com/algernon28/vespera/issues/489).

## Context

The floor's step records no completion and decides again on every invocation (ADR-118). It withdraws the `below-threshold` verdicts standing under the scoring run, then writes them again where the number is on this run's scale (ADR-227). The number is in the scoring run's identity (ADR-117) and the scores are fixed under it, so a scoring run has two sets of survivors and no more: every scored occurrence, or those less the ones scoring below the number. Which of the two stands follows the answers, which no identity names.

`clustering` records its completion under the same run (`TaskletSteps.once`) and filters by `Verdicts.survivingAmong` when it works (ADR-087). After it, nothing asks the ledger which occurrences survive: the size report, `ArrangementTasklet` and `GenerationTasklet` read `document_cluster` and `cluster` rows, and `survivingAmong` has no other caller in stages 5 to 6b.

So the survivors can change under a scoring run whose clusters are recorded, in two directions:

- **A, the ticket's.** Removals are withdrawn: an answer is given under another embedder identity (ADR-118), or a second identity appears for the embedding model (ADR-227). The occurrences are survivors with a score and no cluster.
- **B, not in the ticket.** The removals are made: the number was answered for under another identity when the clusters were formed, and is then answered for under this run's. ADR-118 names this as the repair an operator is prompted to make. The occurrences are removed and still clustered.

### Measured, not only read

A throwaway invocation test, over the fixtures of `RelevanceFloorInvocationTest` and `GenerationInvocationTest`, printed the rows after each invocation. It was deleted; three tests of this record failed on the same facts until the build (Tests). Every score in those fixtures is the same, so a floor takes all of a corpus or none; the last row writes one score under the floor by hand.

| Sequence | What stood afterwards, under the scoring run with the floor |
| --- | --- |
| **A**: 2 documents removed, clusters formed over none and recorded; then an answer under another identity | 0 removals, 2 scores, **0 cluster rows**; the clustering step did nothing and `cluster-sizes.html` was not written again; 6a ended *"gated: no survivor was grouped"*, minting no arrangement; 6b was gated; exit code 0. `arrangement.html` still named the arrangement of the earlier scoring run, and approving it opened nothing |
| **B**, written before the change: 2 documents clustered, arranged, approved, written over; then answered under this identity | 2 removals, **2 removed occurrences still clustered**; the arrangement and the generation both *"already recorded"*; `documents.csv` not written again, still listing both; exit code 0 |
| **B**, approved after the change: the same, the approval given after the removals | 6b minted a generation run over the arrangement and wrote **a synthesis doc from 2 removed documents**; `documents.csv` lists both; exit code 0 |
| **B**, one of three: one score written under the floor, then answered under this identity, then approved | 1 removal; 3 cluster rows, the arrangement still of 3; the synthesis doc **sent 3 documents, the removed one among them**; `documents.csv` 3 rows |

**Read, not run**: direction A with some documents clustered and some not. `ArrangementTasklet` counts and reads `document_cluster` rows alone, so it would arrange the clustered ones and say nothing of the rest, and 6b would write over that.

**So, to the ticket's first question**: a survivor with a score and no cluster is left out by the size report, 6a and 6b, with no line and no stop. And in the other direction a removed document is arranged, written from and listed.

### What the first build showed

§2 was first written as a count: the step did its work again where, for any seed partition, the members that survive did not number the `document_cluster` rows for that seed. Built so, `./mvnw test` ran 1,675 tests and failed two that this record had not named.

- **`ArrangementGoesThroughTwoPartitionsInvocationTest.aStepThatFailsPartwayLeavesThePageThatWasThere`** deletes one clustered document's `relevance_score` row and expects 6a to stop on it, a document with no score being one it cannot place (ADR-105). A partition's members are read from the scores, so the document was no longer a member, the counts differed, the clusters were formed again without it, and 6a finished. **A survivor that used to stop the run was left out with no line**, which is the silence this record is about, let in by the count. The first writing of *What no test holds* had argued that case away as one nothing does. The test does it.
- **`StageFiveReportsItsProgressInvocationTest.theReportDoesNotSayItReadTheScoresWhereTheReadFails`** renames `relevance_score` away and expects the relevance report to fail reading it. The clustering step runs before the report and now reads that table on every invocation, so it failed first and the report never started.

## Decision

### 1. The clusters standing under a scoring run are of its survivors as they are now

ADR-118 gave the floor's verdicts that property: *"the removals standing under a run always reflect the answers and the number as they are now"*. The clusters are formed over what those removals leave, so they have the same dependence one step on, and ADR-116's own clause, *"This rule is safe only where a run's identity names everything its steps consume"*, does not cover them. ADR-118 listed `clustering` among the steps it does cover. That is the sentence amended.

### 2. The clustering step does its work again where its rows are not of those survivors

`clustering` keeps its completion record. Forming the clusters is the N²/2 pass ADR-085 priced, and doing it on every invocation, as the floor's step does its own work, is refused.

Where the record is found, the step asks two questions before it honours it, of every seed partition of the scoring run, and they are the two directions of Context and nothing else:

- **A: is there a member of the partition that survives under the run and has no `document_cluster` row under it for that seed?** A member is an occurrence with a score under the run for that seed.
- **B: is there a `document_cluster` row under the run for that seed whose occurrence does not survive under the run?**

- **Neither, for every partition**: the step does nothing, with the line it writes today.
- **Either, for any partition**: the step says so in one line, discards its rows under the run (`DocumentClusters.discardForRun`), forms the clusters again over the survivors as they are, and writes `cluster-sizes.html` again. Its completion row stays; nothing deletes one.

**Not a count** (amended; *What the first build showed*). A count also fires where a clustered occurrence has lost its score, and forming the clusters again then leaves that occurrence out and takes from 6a the stop it makes on it. Neither question is about a score that is missing: an occurrence with a cluster row and no score survives and is no member, so it answers no to both, its row stays, and 6a stops on it as it did before this record.

**Where no occurrence has a score under the run, there is no partition to ask about, and the recorded rows stay.** The step ends as a recorded step does. So the work is never begun again with no partition, and the order the first build chose inside the work, the discard after the check for no partition, is right and is never reached on this path. Rows whose scores are all gone are not this record's to remove: 6a reads its partitions from the scores as well and arranges nothing of them, as it did before.

**It costs reads on every invocation**, for each partition: its members (`Clustering.membersOf`), its cluster rows (`DocumentClusters.membersOf`), and which of each survive (`Verdicts.survivingAmong`, twice). All ship, so `embedding` is not touched. While it asks, the step holds five collections as large as a partition: the members read, those of them that survive, the cluster rows, their occurrences as a list, and the same as a set. It lets go of each before the next partition. While it works it holds four, as it did: the members read, those that survive, those in the order read, and their content hashes. No vector is read and no model is called.

### 3. The arrangement's identity names the removals standing under the scoring run

With §2 alone the clusters change under a scoring run whose arrangement is recorded and approved. The arrangement run's id would be the same, so its `cluster` rows would describe clusters that are gone, the approval would still open the gate (ADR-107 matches the approval against the id), and a synthesis doc written for an ordinal would stand over other documents. Nothing may discard a synthesis doc (ADR-111, ADR-229).

So the two sets of survivors are two arrangements. **`ArrangementConfigConsumed` gains a third member, `standingRemovals`: how many verdicts stand under the scoring run when the arrangement run is minted. It is written where it is not zero and left out where it is.** After the floor's step has run in the invocation, as it has by 6a, that number is zero or the number of occurrences below the floor, so it names which set the arrangement is of.

- **An arrangement over a scoring run with no removal standing keeps the id it has.** Its recorded settings are byte for byte what they were.
- **An arrangement over a scoring run with removals standing is another run**, with its own rows, its own page and its own approval. `GenerationConfigConsumed` names the approved arrangement, so its synthesis docs are another generation run's.
- **Changing back arrives at the first again** (ADR-156's property, by the same means): the clusters are formed again over the same survivors from the same vectors, in the same order (ADR-087), and the arrangement run found is the one recorded over them. An approval of the other does not open it.
- **This holds of arrangements minted by this build, and not of one an earlier build minted over a scoring run with removals standing** (§5, *An arrangement an earlier build minted*).

It is not ADR-118's refused move. That record refused to put the answers in the **scoring** run's identity: the run would re-score in reply to its own question, and its identity would depend on its own output (ADR-117). The arrangement run is minted after stage 5 has ended, re-scores nothing, and names a fact about the run upstream of it that the upstream id cannot name. An answer that changes no removal changes no id.

### 4. What 5f, 6a and 6b do with a survivor that has no cluster

They are never handed one. The floor's step and the clustering step run in that order behind the same gates in every invocation, so by the size report the clusters are of the survivors (§2), and 6a and 6b read an arrangement of those clusters (§3). No stop, no gate, no listing of unclustered documents and no bucket for them is added: ADR-087 refused an unattributed bucket inside a partition, and with §2 there is nothing to put in one.

### 5. Where it lives, and which run ids move

**The build touches `pipeline` and `ledger`, and no other module.**

- `pipeline/ClusteringTasklet`: §2.
- `pipeline/StageRuns`: §3.
- `ledger/Verdicts`: one count, how many verdicts stand under a run, so that `StageRuns` names no `VerdictKind` and `PipelineHoldsOnlyTheRulesOnRecordTest`'s list of the classes that name one stays as it is.

**Neither change is a rule in ADR-222's sense**, which is what decides a verdict, a cache key, a cluster or text of the deliverable. Which occurrences share a cluster stays `embedding.Clustering`'s, given the survivors; when a step does its work again is ADR-116's wiring, already `pipeline`'s in `TaskletSteps`; and what a run's identity names is composed in `StageRuns`, where `ExtractionAttempt`'s member already is one written only when it is not the default. No class is added to `pipeline`. `ClusteringTasklet` names one type of `embedding` more, `DocumentCluster`, the row `DocumentClusters.membersOf` hands back, and `PipelineHoldsOnlyTheRulesOnRecordTest`'s list for the class gains it: the class reads which occurrences the rows name and decides no cluster by it.

**Read against `StageModules` at `35a5a6ba`**: no stage lists `pipeline` or `ledger`, so no stage's implementation version moves (ADR-058).

| Stage | Modules it names | Minted again by the build |
| --- | --- | --- |
| byte-level reduction (1) | `corpus` | no |
| extraction (2) | `extraction`, `similarity` | no |
| content census (3) | `similarity`, `extraction` | no, and no run upstream of it moves |
| content redundancy (4) | `similarity`, `extraction` | no, the same way |
| seed measurement (5) | `embedding`, `extraction` | no, the same way |
| embedding scoring (5) | `embedding`, `extraction` | no, the same way |
| arrangement (6a) | `synthesis`, `extraction`, `embedding` | **yes, where its scoring run has a removal standing**: its recorded settings gain `standingRemovals`. No, where none stands |
| generation (6b) | `synthesis`, `extraction`, `embedding`, `profile` | **yes, over an arrangement that is**: it names the approved arrangement, in its settings and as its upstream run. No otherwise |

**What that costs**, on a working directory an earlier build ran with a floor that removes: 6a arranges again under a new run, whose clusters are the ones it had; `arrangementApproved` must name it; and 6b asks for every synthesis doc again. A working directory whose floor is unset, removes nothing or does not apply is not touched.

**An arrangement an earlier build minted.** An earlier build recorded an arrangement over a scoring run **with** removals standing under the id that names no `standingRemovals`, since no id named one. If this change ran over such a working directory under the same scoring run and the removals were then withdrawn, the clusters would be formed over every survivor, 6a would arrive at that id, find it recorded and honour `cluster` rows of the smaller set, and an approval of it would still open generation: direction A again, with no line. **Nothing in the code prevents it.** It is unreachable on two grounds, of different kinds (next paragraph): a build with this change mints every scoring run of a build cut before ADR-227's change again, and no build was cut between that change and this one.

**In one build with ADR-227's change.** ADR-227's change moves `embedding`, so it mints seed measurement, embedding scoring, arrangement and generation again.

- **From the commit graph, checked**: that change, `35a5a6ba`, is an ancestor of this one. Any build cut from `main` that carries this record's change carries it too, so over a working directory a build from before `35a5a6ba` ran, the scoring run that build's arrangement is over is not arrived at, nor is the arrangement.
- **From the operator, and checked by no agent**: that no build was cut between `35a5a6ba` and this change. ADR-227 §6, answer 3, holds its build for #476, and this change joins that build (§8, the operator's second answer). The repository has no release and one tag, `pre-220`, which says nothing of what was built or run.

In that build this record's change adds no move to those four, and the case above cannot be reached. Where that build ends is not this record's. **Nothing in the repository holds the changes to one build.**

**What does not move**: no DDL, so no schema version; no cache key; no `run.stage` or `finished_step.step` value; no verdict reason.

### 6. What ADR-229's guard requires, and the two notes owed to it

`EveryTableKeyedByARunIsOnRecordTest` fails on a table that gains `run_id TEXT NOT NULL REFERENCES run (id)`, on a shipped delete from one of the twenty-six that does not begin `WHERE run_id = ?`, on a first delete from `shingle`, `run_upstream`, `finished_step`, `relevance_label` or `synthesis_doc`, and on any shipped `VACUUM`.

This decision asks none of it:

- **No table and no column.** The second arrangement is another row of `run`.
- **No new delete.** The clustering step's discard is `DELETE FROM document_cluster WHERE run_id = ?`, which ships and which the guard reads as it did.
- **No delete from `finished_step`.** The completion row is kept and asked about (§2); taking it back was the other shape and is refused for this reason among others (*Alternatives refused*).
- **No delete from `synthesis_doc` or `cluster`.** §3 exists so that none is needed.

**Two notes are owed to that record, and are carried here and at its head:**

1. Its §1 calls the shipped deletes by a run *"a step discarding its own unfinished work under its own run (ADR-116)"*, and the guard's javadoc said the same without *under its own run*. Its §4 has no such sentence. Two are not of unfinished work. `Verdicts.discardVerdicts(scoring, BELOW_THRESHOLD)` has withdrawn a finished invocation's removals on every invocation since ADR-118. `DocumentClusters.discardForRun` discards clusters whose step is recorded (§2). Both are a step discarding its own rows under its own run, which is what the guard reads; the guard's javadoc now says both kinds.
2. Its Consequences' *"A table that gains or loses a reference to `run` fails"* the guard is unqualified. The guard counts a table only where the reference is written `run_id TEXT NOT NULL REFERENCES run (id)`.

`EveryStatementThatSortsIsRecordedTest` is not edited: the count added to `ledger.Verdicts` has no `ORDER BY` and no grouping, and `verdict_by_run_id` answers it. The build checks that with the test, not with this sentence.

### 7. The direction, stated plainly

ADR-042 warns of over-publishing.

- **Direction B was over-publishing, and is closed**: a synthesis doc was written from documents the same scoring run had removed, and the listing carried them.
- **Direction A was the opposite, and is closed in the direction ADR-042 warns of**: documents left out with no word are now clustered and arranged. They reach the deliverable only through a new arrangement, which a person approves from a page that lists them (ADR-107). ADR-088 chose that direction for a number that does not apply, and ADR-227 took it for the withdrawal; this record carries the withdrawal as far as the gate and no further.

### 8. The operator's word, and the session's calls

**The operator's word**, of 2026-10-10, as the coordinating session quoted it to the session that wrote this record: *"tell the agents to make all decisions regarding their tickets"*. The author did not hear the operator.

**The operator's second answer**, of 2026-10-10, as the coordinating session reported it: asked by that session whether #489 joins the one build held for #476, the operator answered *"Join the #476 build"*. So this change ships in the build held for #476 (ADR-227 §6, answer 3), in which ADR-227's change to `embedding` ships for the first time. The author did not hear this either. §5 leans on it for one thing, that no build was cut between ADR-227's change and this one. Nothing else here is the operator's.

**The session's calls**, each made on that word and none put to the operator:

1. **Both directions are decided here**, B being the same defect from the other side and the one that publishes what was removed.
2. **The clustering step does its work again in place, under the scoring run** (§2), and keeps its completion record.
3. **The test was a count for each partition**, not the two sets compared. Withdrawn by call 12.
4. **The arrangement's identity gains `standingRemovals`, left out at zero** (§3), so that the arrangements over a scoring run with no removal standing keep their ids. Which of the two states a working directory is in is not known to any agent.
5. **Nothing is added to the size report, 6a or 6b** (§4).
6. **The build is confined to `pipeline` and `ledger`**, and neither change is a rule of ADR-222's kind (§5). `embedding` and `synthesis` are not touched, so `DocumentClusters.discardForRun`'s javadoc, *"for a step whose completion under this run is not recorded"*, is left as it is until a change that already mints `embedding` again (ADR-216 §7).
7. **The count is a method of `ledger`, of every verdict under a run**, not of one kind, so that `StageRuns` names no `VerdictKind` (§5).
8. **`anArrangementWithTheRemovalsStandingIsNotTheOneWithout` writes one score under the floor itself, as `theClustersAreComparedAsSetsAndNotAsCounts` does**, standing for a document that scored there; no fixture can make one, every scripted vector being the same.
9. **ADR-118 and ADR-229 carry a note at their heads**, as ADR-226 does for ADR-227. ADR-116, ADR-087 and ADR-157 are amended in the sentences quoted above and carry none.
10. **`docs/decision-ledger.md` is not edited**: it is closed to new entries, and this record is indexed in `docs/adr/README.md` alone.
11. **No line is pinned by a test.** The clustering step's new line is given word for word below, and held by nothing, as the floor's four are.

**Four more, made the same day on what the first build showed**, on the same word and none put to the operator:

12. **The count gives way to the two questions of §2.** The cheaper course was to keep the count, change how the arrangement's test makes 6a fail, and record that a clustered document whose score is gone is dropped when the clusters are formed again. It is refused: that is a survivor left out with no line where a stop stood, the fault this record exists to close, and the two questions need nothing `embedding` does not ship, so the stricter course moves no run id either.
13. **`ArrangementGoesThroughTwoPartitionsInvocationTest` is not edited.** It is what holds call 12: it fails under the count and passes under the two questions.
14. **`StageFiveReportsItsProgressInvocationTest.theReportDoesNotSayItReadTheScoresWhereTheReadFails` renames the column `score` away, not the table.** The clustering step's reads of the table are owed by §2 on every invocation and name its other columns; with no number set, the report's is the first statement of an invocation to name the score. What the test claims is unchanged.
15. **Where no occurrence has a score under the run the recorded rows stay**, and the discard stays after the check for no partition (§2).

**Three more, on the gate's reading of the built change**, on the same word:

16. **The arrangement step gains no check of its rows against the clusters.** The one case it would find is kept out by the build this ships in (§5), and is stated where it bears and under *What no test holds*.
17. **ADR-077 is not amended** (the header says why), and the change's first commit message, which says it is, is wrong in that.
18. **`CONTEXT.md`'s Verdict entry is corrected in this change**: *"redoing its own unfinished work under the same run (ADR-116)"* gains the floor's step, which has decided again on every invocation since ADR-118. It is the note §6 carries to ADR-229, made where the vocabulary states it. The clustering step's rows are not verdicts and the entry says nothing of them.

## Alternatives refused

- **Leave it and report.** Direction B writes over removed documents on the path ADR-118 tells the operator to take.
- **A stop or a gate, with a line.** For direction B the operator has no value to change that gets past it: the number is the one they want, and no flag does a step's work again (ADR-116). The run would stop for good on the repair ADR-118 prompts.
- **List the unclustered survivors in the deliverable.** It is ADR-087's refused bucket, it does nothing for direction B, and it leaves a finished arrangement's labels and counts describing other documents.
- **Filter the clusters by survival where 6a and 6b read them.** The recorded `cluster` rows keep their counts and their labels, a cluster emptied keeps its page, and a synthesis doc already written still cites what was removed.
- **`clustering` records no completion**, as the floor's step does not. The N²/2 pass on every invocation.
- **The floor's step takes back the clustering step's completion.** A delete from `finished_step`, which nothing ships and the guard holds (§6), and one step answering for another, which ADR-116 has no case of.
- **Do the clustering again and the arrangement again, under the ids they have.** The approval would open on an arrangement nobody read, and the synthesis docs of the generation run would be of other clusters (§3).
- **Put the floor's state in the scoring run's identity.** ADR-117 and ADR-118 refused it, and their reasons stand.
- **Name the number of clustered documents in every arrangement's identity.** It needs no count from `ledger`, and it moves every arrangement run and every generation run of every working directory.

## Consequences

- **An answer that changes what the floor removes now forms the clusters again in the same invocation**, and the invocation ends at the arrangement gate with a new page, or with the page of an arrangement recorded earlier. No profile edit is needed and nothing is scored again.
- **An approval given before such a change opens nothing after it.** The operator approves the arrangement the page now names.
- **A deliverable written before the change stays on disk** under its generation run's folder, listing what it listed. Nothing rewrites or removes it, as nothing does when a floor is edited.
- **A second embedder identity for the embedding model (ADR-227) now forms the clusters again as well**, over the survivors the withdrawal brings back. What vectors that pass reads is [#488](https://github.com/algernon28/vespera/issues/488)'s (*What this does not decide*).
- **Every invocation that passes stage 5's gates reads each partition's members and its cluster rows and asks which of each survive**, where it used to read one row of `finished_step`. Not measured.
- **A clustered occurrence with no score still stops 6a where the floor's decision has not changed in the same invocation** (*What no test holds*), and a `relevance_score` table that cannot be read now fails the clustering step, before the report and before 6a.
- **On a working directory whose floor removes, 6a and 6b do their work again once** after the build (§5).
- **A step's completion record is, for `clustering`, a record of work done over the survivors there were**, and is asked about with them. The other five steps of ADR-118's list are as they were.

## Tests

Written with this record, before `src/main` was. The test tree compiled against `src/main` as it stood then: no test named a type or method the build added.

| Class | What it holds |
| --- | --- |
| `pipeline.ClustersAreOfTheSurvivorsAsTheyStandInvocationTest`, new, four tests, `theClustersAreComparedAsSetsAndNotAsCounts` and the claim on the size report added after the build on the tester's reading of it and passing as written | by whole invocations. `survivorsTheFloorNoLongerRemovesAreClusteredAndArranged`: direction A over a corpus the floor removed whole, the clusters having been formed over none; after the withdrawal both documents have a cluster row under the same scoring run and one arrangement of that run holds both, with no scoring run added, and `cluster-sizes.html`, deleted by the test before that invocation, is there again. `theClustersAreComparedAsSetsAndNotAsCounts`: what a count would pass. With one of three clustered documents' `document_cluster` row deleted by hand, the next invocation clusters all three and the arrangement is the one there was; then, with one score written under the floor, the floor answered for under this run's identity and the same row deleted again, the invocation begins its clustering step with two survivors and two rows that are not of the same two, and ends with the removed occurrence in no cluster and the other clustered. The deleted row is a state no shipped path is known to reach, the step's discard and its rows being in one transaction. `occurrencesTheFloorRemovesAfterClusteringLeaveTheirClusters`: direction B; no occurrence is both removed and clustered, and approving the arrangement made before the removals mints no generation run. `anArrangementWithTheRemovalsStandingIsNotTheOneWithout`: one of three documents removed after all three were arranged, approved and written over; the clusters hold two, a second arrangement over the same scoring run holds two and records `"standingRemovals":1` after its two other settings, the first records what it did, the earlier approval writes nothing more, the new one once approved is written over from two documents, and the answer changed back arrives at the first arrangement again, which `arrangement.html` names, with what was written over it untouched |
| `pipeline.RunIdentityGoldenTest`, not edited | its `arrangement` test holds the recorded settings of an arrangement over a scoring run with no removal standing, so it holds that such an arrangement's id does not move |
| `EveryTableKeyedByARunIsOnRecordTest`, javadoc only | §6's first note. No claim of it changes |
| `pipeline.ArrangementGoesThroughTwoPartitionsInvocationTest`, not edited | its `aStepThatFailsPartwayLeavesThePageThatWasThere` holds that a clustered document whose score is gone still stops 6a, so that the clustering step does not form its clusters again for it (§2, *Not a count*) |
| `pipeline.StageFiveReportsItsProgressInvocationTest` | `theReportDoesNotSayItReadTheScoresWhereTheReadFails` renames the column `score` away where it renamed the table; its two claims are word for word what they were (§8, call 14) |
| `PipelineHoldsOnlyTheRulesOnRecordTest` | `ClusteringTasklet`'s collaborators on record gain `embedding.DocumentCluster` (§5) |

**Two tests failed over the first build and pass over §2 as amended**:

- `ArrangementGoesThroughTwoPartitionsInvocationTest.aStepThatFailsPartwayLeavesThePageThatWasThere`, on *"the invocation failed: a document with no score cannot be placed"*.
- `PipelineHoldsOnlyTheRulesOnRecordTest.theClassesOfTheFreedStagesNameOnlyTheCollaboratorsOnRecord`, `ClusteringTasklet` not then naming `embedding.DocumentCluster`.

**Three tests failed until the first build**, each on a claim and none on an error, and pass over it:

- `ClustersAreOfTheSurvivorsAsTheyStandInvocationTest.survivorsTheFloorNoLongerRemovesAreClusteredAndArranged`, on *"and both documents are in a group under the same scoring"*: 0 where 2 is expected.
- `ClustersAreOfTheSurvivorsAsTheyStandInvocationTest.occurrencesTheFloorRemovesAfterClusteringLeaveTheirClusters`, on *"and no removed document is still in a group"*: 2 where 0 is expected.
- `ClustersAreOfTheSurvivorsAsTheyStandInvocationTest.anArrangementWithTheRemovalsStandingIsNotTheOneWithout`, on *"and the groups are formed again over the 2 it leaves"*: 3 and 1 where 2 and 0 are expected.

`EveryStatementThatSortsIsRecordedTest` is not edited (§6), and of `PipelineHoldsOnlyTheRulesOnRecordTest` only the one list above (§5).

**What no test holds.**

- **That the recorded rows stay where no occurrence has a score under the run** (§2). Read from the step: with no partition neither question is asked.
- **A `document_cluster` row under a seed that has no score left under the run.** The step asks about the seeds the scores name, so it does not see the row; nor does 6a.
- **ADR-227's way into direction A**, a second embedder identity. The step it reaches is the one `survivorsTheFloorNoLongerRemovesAreClusteredAndArranged` reaches by an answer.
- **The clustering step's new line**, that it is written or word for word.
- **That the clusters formed again are the ones formed before over the same survivors.** `anArrangementWithTheRemovalsStandingIsNotTheOneWithout` holds how many documents they hold and that the arrangement arrived at is the first. The order is ADR-087's.
- **What the read of §2 costs** on a corpus of any size.
- **What `cluster-sizes.html` says once it is written again.** `survivorsTheFloorNoLongerRemovesAreClusteredAndArranged` holds that the file is there.
- **That the step's discard and its rows are one transaction**, on which the javadoc of `theClustersAreComparedAsSetsAndNotAsCounts` leans. Read from `TaskletSteps.taskletStep`; no test stops the step between the two.
- **A build with this change over a working directory an earlier build ran.** §5 is read from `StageModules` and from what a run's id hashes.
- **An arrangement an earlier build minted over a scoring run with removals standing, arrived at again once they are withdrawn** (§5). No test can mint a run as an earlier build did, and nothing in the code refuses the case.
- **A clustered occurrence with no score where the floor's decision changes in the same invocation.** The clusters are then formed again from the scores, the occurrence is in none, and 6a does not stop on it. `ArrangementGoesThroughTwoPartitionsInvocationTest` holds the stop where nothing else changed.

## What the commit that built `src/main` owed

**Built as written below.** What the amendment owed over the first build was `pipeline/ClusteringTasklet.clustersAreOfTheSurvivors(RunId)` and nothing else:

- For each `winningSeed` of `clustering.partitions(scoring)`:
  - `scored` is `clustering.membersOf(scoring, winningSeed)`, and `surviving` is `ledger.verdicts().survivingAmong(scoring, scored)`.
  - `clustered` is the occurrence ids of `documentClusters.membersOf(scoring, winningSeed)`.
  - Answer `false` where `surviving` holds an occurrence `clustered` does not (A), or where `ledger.verdicts().survivingAmong(scoring, clustered).size() != clustered.size()` (B).
- Answer `true` otherwise, and so where there is no partition.
- `documentClusters.sizeOf` is no longer called by this class. The read of the cluster rows keeps the timed name the build gave the count, *"the clustered documents of a partition"*.
- The method's javadoc and the class javadoc's paragraph say the two questions and not *"number"*, and that an occurrence with a cluster row and no score answers neither, so that 6a still stops on it. The comment *"The work discards the run's rows at its head, past the empty-partition check"* stays and is right (§2).
- Nothing under `embedding` is edited: `DocumentClusters.membersOf` and `DocumentCluster` ship.

**As first written:**

- **`ledger/Verdicts`**: `public long verdictsUnder(RunId runId)`, how many verdicts are recorded under `runId` itself, of any kind and not through its upstream runs: `SELECT COUNT(*) FROM verdict WHERE run_id = ?`.
- **`pipeline/StageRuns`**: `ArrangementConfigConsumed(String corpusRoot, String scoringRunId, @JsonInclude(JsonInclude.Include.NON_NULL) Long standingRemovals)`. In `arrangement()`, `standingRemovals` is `ledger.verdicts().verdictsUnder(scoringRunId)` read when the run is minted, and `null` where that is zero. The class keeps the `Ledger` it is constructed with. Its javadoc on the record no longer says *"unchanged"*, and says what the member names and why it is left out at zero (this record, §3).
- **`pipeline/ClusteringTasklet`**: past its gates, where `ledger.runs().stepFinished(scoring, StepNames.CLUSTERING)`, it asks §2's two questions of each seed of `clustering.partitions(scoring)`, as written above. (First written, and built, as a count: `survivingAmong(...).size()` against `documentClusters.sizeOf(scoring, seed)`.)
  - Neither, for every partition: the line *"Stage 5f (clustering) was already recorded under scoring run {}"* and nothing else, as today.
  - Either, for any partition: this line, then `documentClusters.discardForRun(scoring)` and the work an unrecorded step does, the size report included: `"Stage 5f (clustering) was recorded under scoring run {} over other survivors than the run has now: the relevance-floor step has decided again since. The step does its work again."`
  - The reads are timed and named as the step's other reads are (`TimedStatement`).
  - How the step reaches its work a second time is the build's. `TaskletSteps.once` keeps its signature and its other eight callers. `Runs.finishStep` is `INSERT OR IGNORE`, so recording completion again is harmless.
  - The class javadoc says that the completion record is honoured only while the rows are of the survivors, citing this record. `RelevanceFloorTasklet`'s paragraph *"It runs before clustering, and that ordering is the whole of ADR-087's 'costs nothing'"* gains that clustering follows a later change of the floor's decision too.
- **Touch no file under `embedding` or `synthesis`**, and none under `corpus`, `extraction`, `similarity` or `profile`. A change the build finds it needs there is a finding for the analyst: it moves run ids this record says do not move.
- **No DDL and no new `DELETE`.**
- **Edit no test and no Markdown.** A test the build finds it has to edit is a finding for the analyst. `RunIdentityGoldenTest` passes unedited.
- Verify with `./mvnw verify` under Java 26, and the docs gates.

## What this does not decide

- **[#488](https://github.com/algernon28/vespera/issues/488)**: which vectors a clustering pass reads once two embedder identities answer to the embedding model's name. If that pass reads other vectors than the first did, the clusters formed again over the same survivors may not be the ones an arrangement recorded earlier was made of, and §3's *"arrives at the first again"* would find rows that describe other clusters. Whether a run should record the identity it was clustered under is that ticket's.
- **Whether `arrangement.html` should be removed or rewritten where an invocation arrives at no arrangement.** Measured: after a floor that removes every document, the page on disk names the arrangement of an earlier scoring run. Approving it opens nothing.
- **Whether the operator is told that a deliverable on disk is of an arrangement no longer arrived at.**
- **Whether the operator should be told how many removals were withdrawn or made again.** ADR-118 and ADR-227 left it open, and it stays open; the clustering step's line says only that the survivors changed.
- **Whether the arrangement step should check its recorded rows against the clusters** before it honours its own completion. With §3, one embedder identity and arrangements minted by this build it has no case to find. It would have one over an arrangement an earlier build minted with removals standing (§5), which is kept out by the commit graph and by the operator's statement that no build was cut in between, and by nothing in the code.
