# ADR-319 — A cluster whose answer is turned down the last two times it is asked stands without writing, and the generation step finishes with it

> **Partly amended — see [ADR-321](0321-the-deliverable-is-written-and-drawn-in-the-language-the-profile-names-and-the-question-names-the-answers-two-fields-and-where-its-writing-ends.md).** §5.1's line has an Italian rendering that record fixes, drawn where the deliverable's language is Italian; the English line stands word for word. §4's two stale `synthesis` javadocs are corrected with that record's change, which moves the generation run. The rest of this record stands.

- **Date**: 2026-10-22
- **Status**: accepted on 2026-10-22.
- **Decided**: by the operator, who decided that a cluster whose answer keeps being turned down stands as a visible hole, so that the generation step can be recorded finished and `vespera publish` can send the rendering; what "keeps being turned down" means, the kinds it covers, where the rule sits and what the reader is told are decided under delegation.
- **Amends**: [ADR-116](0116-a-runs-completion-is-recorded-per-step-because-several-steps-share-one-run.md), *"6b keeps ADR-111's finer rule"*: generation's `finished_step` row is written when every cluster of the approved arrangement carries a `synthesis_doc` row, **or** when the walk went through every cluster, found none unsendable, and every fault standing under the run is a repeated fault (§1, §2). Its *"a faulted cluster is never skipped forever"* holds of every fault but a repeated one, which is not asked again under its run once the step finishes (§3). Every other rule of that record stands.
- **Amends**: [ADR-111](0111-a-cluster-fault-is-a-row-in-synthesis-and-a-re-run-under-the-same-id-repairs-rather-than-regenerates.md), the repair pass's *"re-attempts every cluster carrying a `cluster_fault` row"*: while the step is unfinished. Its accepted cost, *"one call per faulted cluster per invocation"*, ends for a cluster at its second turned-down answer in a row, where the step can finish. The repair call stays byte-identical to the first, and nothing of a turned-down answer reaches a later question.
- **Amends**: [ADR-190](0190-stage-6bs-loop-and-6as-lead-document-rule-live-in-synthesis-which-still-knows-no-stage.md) §1.5 and §5, in one case: `ClusterGeneration` keeps its completion rule and returns `LeftUnfinished` as before, and the step records completion on a `LeftUnfinished` whose every standing fault is a repeated fault and which carries nothing unsendable (§4).
- **Corrects**: [ADR-162](0162-writing-that-ends-with-the-answers-closing-brace-is-turned-down-as-malformed.md)'s Consequences, two statements: the rate, *"about 7%"*, and *"nothing measured says one cluster keeps drawing it"* (Context).
- **Keeps**: [ADR-108](0108-6b-sends-one-exemplar-first-call-per-cluster-and-verifies-every-response.md)'s *"holes are expected output"*; [ADR-109](0109-a-citation-is-an-exemplar-ordinal-minted-for-one-call-and-the-check-is-that-it-is-in-range.md) and ADR-162's refusal to repair an answer; ADR-111's five-in-a-row stop; [ADR-121](0121-a-window-with-no-room-for-documents-is-refused-and-no-call-is-ever-made-with-none.md)'s accepted permanence for a cluster nothing could be sent for; [ADR-166](0166-the-serving-engine-counts-a-question-before-it-is-sent-and-an-overflow-is-cut-where-the-count-can-see-it.md) §4a; [ADR-174](0174-a-page-nothing-was-written-over-says-why-in-words-for-a-reader.md)'s seven sentences, its index cell and its §5 rule on words; [ADR-216](0216-nothing-ships-that-no-decision-requires-and-nothing-calls-a-javadoc-states-its-own-contract-and-agents-md-carries-no-history.md) §7; [ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md) §1 and [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md) §5, in what moves a run id; [ADR-308](0308-vespera-publishes-the-confluence-rendering-to-the-operators-cloud-site-itself-on-an-explicit-publish-with-a-token-read-from-the-environment-alone.md) §5, in which rendering a publish sends.

## Context

### What holds before this record

A cluster whose answer is turned down carries a `cluster_fault` row and no `synthesis_doc` row (ADR-111). The row is replaced at each turn-down and deleted when an answer is believed, so it records the reason the last answer was turned down and nothing about how many were. The generation step records completion only when no fault row stands under its run (ADR-116, ADR-190 §1.5), so every invocation asks each faulted cluster again. A publish sends the rendering of the last generation run whose step is recorded finished (ADR-308 §5). A cluster whose every answer is turned down therefore keeps the step unfinished for as long as it is turned down, and the rendering of a run with 53 of its 54 clusters written cannot be published.

### Measured

One generation run over 54 clusters, served by `qwen3:8b`, under the wording that ships (ADR-161), with no temperature and no seed sent, so the serving engine samples each answer afresh at the model's own default (ADR-108's identity carries neither):

- **First answers**: 8 of 54 turned down for writing that ends with a closing brace belonging to no opening one (ADR-162), about 15%. ADR-162 measured 3 of 45, about 7%, under the same wording.
- **One cluster** was turned down four times out of four, over four invocations, each time by the same check with the same detail.
- **One cluster** was turned down the first two times it was asked and believed the third.
- **53 of 54** clusters carry writing; the one left keeps the step unfinished.

So a cluster can keep drawing the brace, which ADR-162 found no measurement of, and the repair pass does not converge for it.

### What the ledger can say, without a new column

At the moment a cluster is asked, the ledger says whether a fault stood against it under the run, and of which kind. A fault standing then means the last time it was asked its answer was turned down: a believed answer deletes the row and writes a `synthesis_doc`, and a cluster with one is never asked again. Nothing records how many times before that. A count would be a column of `cluster_fault`, which `synthesis` owns (ADR-041), and a change to `synthesis` moves the generation run (§7).

## Decision

### 1. A repeated fault

**A repeated fault** is a fault recorded against a cluster when both of these hold:

- when this invocation asked it, a fault already stood against it under the run, of one of the three kinds below;
- and this invocation's answer was turned down, by one of the same three.

The three kinds are the checks on what an answer wrote: `ANSWER_RAN_OUT_OF_ROOM`, `SCHEMA_VIOLATION` and `CITATION_NOT_IN_RANGE`. The two need not be the same kind. Two answers in a row turned down for what they wrote is what "keeps being turned down" means here, whichever check turned each down; a cluster turned down for a different check each time would otherwise keep the step unfinished as surely as one turned down for the same.

**`PROMPT_EVALUATION_CEILING` is never one.** It is a check on the question's length against the window (ADR-108, ADR-166 §4, #332), and the same question has the same length each time it is asked, so a second one says the window is too small for the cluster, which the operator can change, and not that the model keeps answering it badly. It is also the only kind recorded for a cluster no answer was asked for (ADR-166 §4a). A cluster nothing could be sent for (ADR-121) carries no row and is never one either.

**Two, and not more.** Two is the most the ledger can tell without a new column (Context), and a column moves the generation run. It also reaches the measured cluster the next time it is asked. Its cost is measured too: a cluster turned down at a rate *p* each time it is asked, independently, stands though a third answer would be believed with probability *p*²(1−*p*), about 1.9% of clusters at 15% and about 0.5% at 7%; the measured cluster that was turned down twice and believed the third time would have stood. The hole is a visible one with every document listed beneath it, which ADR-108 has holes be. A count, and a rule over more than two, can come with the next change that moves the generation run for another reason (What this does not decide).

### 2. The step records completion when every fault standing is a repeated fault

After the walk, the generation step records its completion under the run when:

- the walk went through every cluster (it did not stop on five turned-down answers in a row);
- it found no cluster unsendable;
- and every fault standing under the run is a repeated fault recorded by this invocation.

Otherwise it records nothing, as before. With no fault standing the step finishes as it always has.

Since the walk went through every cluster, every fault standing after it was recorded or replaced by this invocation, so the third condition reads: every cluster this invocation turned down is a repeated fault. The fault rows stay, each saying why its cluster stands without writing.

**The five-in-a-row stop is unchanged.** A repeated fault is a turned-down answer, so it counts towards the streak; five in a row stop the step, and a stopped step records no completion (ADR-111).

### 3. The hole stands under its run

Once the step is recorded finished, no later invocation of that run asks the cluster again: a finished step does no work (ADR-116). This is the price of the decision, and it is paid on purpose:

- **A finished step that kept asking** would make *finished* mean *finished unless*, and the rendering a publish sent could change under the same run behind it.
- **Each later invocation** reaching the tree, as every `vespera run` over a finished run does (ADR-274 §6), would pay a call for every hole, for good: the unbounded cost ADR-109 refused.
- **The hole is filled by a generation run of other settings**: another generation model or window, or a change to `synthesis`, each of which asks every cluster afresh.

Before it stands a cluster is asked again, as ADR-111 says, and a believed answer fills it: a cluster turned down once is asked by the next invocation, and so is a repeated fault while another cluster keeps the step unfinished.

### 4. The rule sits in `pipeline`, and decides whether the step works again, not what it writes

`ClusterGeneration` is unchanged: it still returns `LeftUnfinished` whenever a fault stands. The generation step, in `pipeline`, records completion on that outcome in the one case of §2. It knows which faults are repeated from what the walk already hands it: the fault standing against a cluster, read by its key through `ClusterFaults.forCluster` when the walk asks the step for the cluster's material, before its question is put; and the fault this invocation's answer earned, through `GenerationProgress.answerTurnedDown`, which hears only of answers asked for.

**This is no rule that shapes generation's output, in ADR-222 §1's sense as ADR-274 §5 narrowed it.** ADR-274 §5 has a stage's version name the modules whose rules decide what its run records, and for the generation run that is the questions it put, the answers it believed and the faults it kept. No such row is decided outside `synthesis`: within a walk, which clusters are asked, what is asked, which answer is believed, which fault is kept and what it says are `synthesis`'s rules exactly as before. What the rule in `pipeline` decides is one row of `ledger`'s, the step's `finished_step` row; and the line the page carries for a cluster that stands (§5.1) is drawing, which keys no run (ADR-274 §5). So the class that holds it, `RepeatedFaults`, is listed in `PipelineHoldsOnlyTheRulesOnRecordTest` as holding none, and no stage names `pipeline`.

**This departs from ADR-190, and says so.** ADR-190's Context classes the step's completion with the label, the synthesis doc, the fault and the stop as *"a rule about clusters"*, and its §1.5 moved the completion rule into `synthesis` on that ground. That ground was the module rule, not the run id: ADR-190 was written while generation's version named `pipeline`, so where the rule sat moved no run id either way. This record takes the one case of §2 back into `pipeline`, on ADR-274 §5's reading that completion is no row the run records about a cluster, and leaves the rest of the completion rule where ADR-190 put it.

**Two javadoc statements in `synthesis` go stale, and stay so until `synthesis` is next changed for a reason that moves the generation run** (ADR-216 §7): `GenerationOutcome.LeftUnfinished`'s *"the step must not record completion"*, and `ClusterGeneration`'s *"Completion needs two things"*. Correcting them now would move the run this record exists to let finish.

### 5. What the reader is told

#### 5.1 The page says the group stands

The page of a cluster with a fault on record, in a tree drawn of a run whose generation step is recorded finished, carries, on a line of its own beneath the sentence ADR-174 gives its kind, in italics:

> *Its answer was turned down each time it was asked, so this group stands without writing; running the same command again asks nothing more about it.*

It is true of every such page: in a finished run every fault standing is a repeated fault (§2), every answer the cluster got was turned down, since a believed one would have written it, and the same command arrives at the same run, which asks nothing. It holds ADR-174 §5's rule: it says *group*, and none of *permanent*, *never* or *always*, and describes this run alone.

It is drawn on both the HTML page and the Confluence rendering's page of the cluster, by every drawing of a finished run: the one in the invocation that records completion and every later one (ADR-274 §6). The index cell keeps *nothing was written over this group* with no link (ADR-174 §3), and `documents.csv` is unchanged. `deliverable` is handed, as a plain value, whether the generation step's work under the run it draws is recorded finished, or is being recorded by the invocation drawing it, and words the line itself.

#### 5.2 The account counts the clusters standing

The invocation account (ADR-198) gains one line, after `counts clusters arranged=… written=…`:

```
counts clusters standing-without-writing=N
```

where this invocation's generation step ended on its run recorded finished and N, the clusters standing without writing, is greater than nought. N reaches the account through the job's execution context, as the copy counts reach the closing line (ADR-274 §7); no statement is added to the account's six counts (ADR-209). Why each stands is already there: the `counts cluster-fault kind=…` lines, which in a finished run count repeated faults alone.

#### 5.3 The log line

The line the step writes when it finishes says how many clusters stand without writing, and, for each, its place and the kind and detail of the fault, as the lines for a turned-down answer do (ADR-093).

#### 5.4 The closing line is unchanged

*"… with N groups left unwritten …"* counts the clusters with no `synthesis_doc` row, standing ones among them (ADR-274 §7).

### 6. The publish

Unchanged: it sends the rendering of the last generation run whose step is recorded finished and whose tree holds a page list (ADR-308 §5). A run that finishes under §2 is one.

### 7. The run ids that move

**None.** The change is to `pipeline` and `deliverable`, which no stage's version names (ADR-222, ADR-226, ADR-274 §5); `StageModules.GENERATION` names `synthesis`, `extraction` and `embedding`, and none of them, the schema or the profile is changed. A generation run left unfinished by a cluster whose answer was turned down is continued under its own id, and its unwritten clusters are asked again under §1 and §2.

## Consequences

- **ADR-162's Consequences are corrected.** The rate measured under the shipped wording and model is 8 of 54 first answers, about 15%, where it said about 7%; and one cluster drew the brace four times out of four, where it said nothing measured showed one cluster keep drawing it. Its decision, turning such writing down, stands.
- **A run whose only unwritten clusters are repeated faults finishes**, and its rendering can be published, each such cluster's page saying it stands without writing.
- **The repair pass ends at a cluster's second turned-down answer in a row** once nothing else keeps the step unfinished. A repair pass that leaves only repeated faults is the last one under its run.
- **A later change to `RepeatedFaults` moves no run id, and reopens no run already finished.** Three answers in a row instead of two, for instance, decided in `pipeline` alone, mints no generation run, so it applies only to runs whose step is not yet recorded finished: a run finished under two keeps its `finished_step` row, and a finished step does no work (ADR-116), so its standing clusters are not asked again. That is the price of the rule sitting where it moves no run id; a record that wants such a run reopened says so and how.
- **Five repeated faults in a row still stop the step**, since each is a turned-down answer; the breaker says the model, the window or the schema is wrong, and the operator acts on it as before.
- **A cluster nothing could be sent for, and a question longer than the room, keep the step unfinished**, as ADR-121 and ADR-166 left them. ADR-121's *"the permanent case — a hole that is known, recorded and finished with"* is decided here for turned-down answers alone.
- **`CONTEXT.md`** gains **Repeated fault**; its **Repair pass** and **Unwritten cluster** entries say where a repair pass ends and what a standing cluster's page carries.
- **`README.md`** says, beside its sentence on a page saying why nothing was written, in words for the operator and so of a *group*: that a cluster whose answer was turned down the last two times it was asked stands without writing, its page says so, running again asks nothing more about it, and the rendering can be published.
- **Grounding the imposed schema in the question** is the expected next record about the brace: a change to `synthesis`, so a new generation run that asks every cluster afresh. It is not decided here.

## What this does not decide

- **A rule over more than two answers.** It needs a count of turned-down answers per cluster, a column `synthesis` owns, so a change that moves the generation run; the record that next moves it for another reason can carry one.
- **A cluster nothing could be sent for, or a question longer than the room.** Both still keep the step unfinished.
- **The progress page.** It shows each step, its counters and its last progress line, and nothing of any one cluster; it is unchanged.
- **Asking a standing cluster again under the same run.** There is no lever, as ADR-116 has none for any step; the lever is a setting the generation run's identity is derived from.

## Tests

| Test | What it holds |
| --- | --- |
| `pipeline.RepeatedFaultInvocationTest.recordsTheWorkAsDoneWhenTheOnlyClusterLeftIsTurnedDownAgain` | an answer turned down for the closing brace leaves the step unfinished; turned down again by the next invocation, the step is recorded finished under the same run, one question put on each invocation and one fault kept, the second (§1, §2) |
| `pipeline.RepeatedFaultInvocationTest.standsWhenTurnedDownForADifferentCheckTheSecondTime` | a second answer turned down by another of the three checks is a repeated fault (§1) |
| `pipeline.RepeatedFaultInvocationTest.aQuestionLongerThanTheRoomTwiceLeavesTheWorkUnfinished` | two answers turned down for the question's length leave the step unfinished, and the page carries no standing line (§1) |
| `pipeline.RepeatedFaultInvocationTest.aQuestionLongerThanTheRoomThenAWordingFaultLeavesTheWorkUnfinished` | an answer turned down for the question's length, then one turned down for what it wrote, leave the step unfinished: the fault standing at the second question is not of the three kinds (§1) |
| `pipeline.RepeatedFaultInvocationTest.aClusterNothingCouldBeSentForKeepsTheWorkUnfinishedBesideARepeatedFault` | a repeated fault beside a cluster nothing could be sent for leaves the step unfinished (§2) |
| `pipeline.RepeatedFaultInvocationTest.asksNothingMoreOnceTheClusterStands` | an invocation after the step finished puts no question, and draws the page with the standing line (§3, §5.1) |
| `pipeline.RepeatedFaultInvocationTest.theClustersPageSaysItStandsBeneathWhyItsAnswerWasTurnedDown` | the HTML page carries the kind's sentence and the standing line beneath it, the Confluence page both, the index cell its plain line; a page turned down once carries no standing line (§5.1) |
| `pipeline.RepeatedFaultInvocationTest.theAccountCountsTheClustersStandingWithoutWriting` | the account of the invocation that finishes counts one cluster standing without writing; the one before carries no such line (§5.2) |
| `pipeline.RepeatedFaultInvocationTest.thePublishFindsTheRunFinishedWithAClusterStanding` | the publish asks for the rendering of the run finished with a cluster standing (§6) |
| `deliverable.UnwrittenPageWordingTest.theStandingLineKeepsTheRulesEverySentenceKeeps` | the standing line says group, none of the three words, no name only the code knows, one plain line (§5.1) |
| `PipelineHoldsOnlyTheRulesOnRecordTest` | `RepeatedFaults`, where the rule sits, holds no rule, and no stage names `pipeline` (§4) |
