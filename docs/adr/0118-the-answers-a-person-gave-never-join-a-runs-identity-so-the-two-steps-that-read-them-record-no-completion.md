# ADR-118 — The answers a person gave never join a run's identity, so the two steps that read them record no completion

- **Date**: 2026-09-15
- **Status**: accepted
- **Amends**: [ADR-116](0116-a-runs-completion-is-recorded-per-step-because-several-steps-share-one-run.md) — **its list of eight steps only**. That record states a governing clause and a list, and they contradict each other: the clause says a completion record is safe *"only where a run's identity names everything that step consumes"*, and two of the eight named steps consume something no run's identity names. The list becomes six. Every other word of ADR-116 survives: the `finished_step` table, its key, the two re-keyed rules, the dropped `run.finished`, the schema move, the 6b clause, and the clause itself — which is not weakened here but applied for the first time.
- **Rests on**: [ADR-097](0097-a-relevance-label-is-keyed-by-the-documents-path-and-the-seed-set-not-by-the-occurrence.md) (a label is keyed by path and seed set, which is why no run names it), [ADR-088](0088-the-relevance-threshold-is-read-off-a-stratified-sample-of-sixty-labels-and-an-unset-floor-does-not-stop-the-run.md) (why a person is asked at all, and why the page must keep counting answers), [ADR-117](0117-the-relevance-floor-joins-the-scoring-runs-identity-so-a-changed-threshold-is-a-different-run.md) (whose general rule this bounds, and whose exclusion of derived state this extends), [ADR-048](0048-walk-and-run-identity.md) (what a run id is a claim about), [ADR-077](0077-a-regenerated-measurement-is-a-fresh-row-set-under-its-own-run-id.md) (rows under an old id are never rewritten, which is what makes an extra run id expensive rather than free).

## Context

### Measured: exactly one input in this system is keyed by neither run nor walk

Every other input a step reads is a ledger row written under a run id, and [ADR-089](0089-a-stages-run-names-the-immediately-preceding-stages-run-upstream-because-verdicts-are-cumulative.md) chains those ids so that a stage's own id names the run whose rows it read. A relevance label is not one of those. ADR-097 keys it by the document's path and the seed set, and `vespera label` writes it under no run at all — the label subcommand mints nothing, which is a claim `LabelIngestionInvocationTest` has pinned since #111.

Two steps read those rows, and both write under `ScoringRun`:

| Step | What it reads answers for |
|---|---|
| `relevance-floor` | `RelevanceFloor.stateFor` reads the embedder identities the answers carry, to decide whether the operator's number was read off this run's own scale |
| `relevance-report` | the page re-bands the answers given so far against this run's scores, so a person sees what they have already judged |

`ScoringRun.configConsumed` names the corpus root, the embedding model, the upstream measurement run and — since ADR-117 — the relevance floor. It does not name an answer, and there is no third thing it omits: the profile's seven keys are now all accounted for, and the labels are not a profile key.

### Reproduced: the record's list, wired as written, is red in the tree

`relevance-report` was wired to a completion record exactly as ADR-116's list says, and `LabelIngestionInvocationTest.countsAnAnswerGivenUnderThePreviousInvocation` went red. The sequence is the labelling loop itself:

1. An invocation writes the page and the label file.
2. A person answers, and `vespera label` records the answers.
3. The next invocation derives the same scoring run id — nothing in the identity moved, and nothing should have.
4. `relevance-report` finds its completion row and does nothing.
5. The page on disk still says nobody has answered anything.

That is ADR-088's mechanism reading back its own silence. The answer was collected, it is a row in the database, and the one artefact a person opens to decide the threshold has stopped counting it.

`relevance-floor` has the same property and is wired that way **now**. `RelevanceFloorTasklet` records completion in all three of the floor's states. So an operator who re-answers the sample under the model this run actually used — turning `CalibratedElsewhere` into `Applicable`, which is precisely the repair that state exists to prompt — invokes again, gets the same run id, finds a completion row, and removes nothing. The step is not merely skipped once: no later invocation under an unchanged profile will ever reach it again. Nothing in the tree covers this today, which is why it survived the wiring that caught its step-mate.

### The real question is whether the answers should join the run's identity instead

Both symptoms would also go away if `ScoringRun.configConsumed` named the answers. ADR-117 makes that the obvious move — it says, in as many words, that *"every value the profile carries is named in the identity of the run whose steps read it"*, and *"a key a run consumes without being named by is an input that can change while the output keeps its name"*. The answers are an input that changes while the output keeps its name. The shape of the argument is identical.

**It is refused, for four reasons, and the first is the vocabulary's own.**

`CONTEXT.md` defines a relevance label as *"a fact about the document rather than about the run that showed it, so it outlives the score that prompted it and the model that computed that score"*, and a **run** as *"one execution of one stage under one configuration"*. An input that outlives every run and belongs to the document cannot be part of a configuration without one of those two entries becoming false.

**ADR-117's rule is bounded by the profile, and says so.** Its general rule is about the seven keys a person authors. Reading it as "anything a step reads" swallows the ledger: every verdict, every score, every vector is something a step reads, and a run id derived from the content of everything it reads is a hash of the database rather than a name for a configuration. Where those inputs can change, the mechanism that notices is already the upstream chain (ADR-089), not a content hash. Answers are outside that chain only because ADR-097 deliberately put them outside it.

**ADR-117 drew this exact line, and drew it the other way.** It refused to fold `RelevanceFloor.State` into the identity, because that state *"is derived from the embedder identity carried by vectors this run's own steps produce, so folding it into the run's identity would make the identity depend on the run's output."* The answers' whole contribution to the floor is that same derivation, one indirection back: `calibratedUnder()` compares what the answers were given under against what this run's own vectors carry. Folding the answers in re-admits what that clause excluded, and arrives at the same circularity by the longer road.

**And it would make the labelling loop invalidate itself.** ADR-088 asks a person for sixty answers; ADR-097 exists because those answers are given across invocations and outlive the one that asked. If an answer moved the scoring run id, then answering the questions the labelling page asks would mint a new run, re-score and re-cluster the corpus, write a *new* page asking fresh questions, and — through the upstream chain and ADR-107's gate — expire an arrangement the operator had already approved. The report's run id would depend on the reply to the report. A person working through sixty answers in three sittings would leave three scoring runs behind, none of them wrong and all of them kept (ADR-077), for a set of answers that changed no score.

So the answers stay outside every identity, and ADR-116's clause is left to do what it says.

### Two shapes for excluding the steps were considered

**Record completion only in the states where no answer could change the outcome.** `relevance-floor` could record a row in `Applicable` and not in the other two. It is wrong in both directions: an `Applicable` run whose operator then ingests answers given under another model should stop removing, and that is the state transition the row would freeze; and the rule becomes one a reader has to reconstruct from a `switch` rather than one a step states about itself.

**Exclude by a predicate — "a step that reads outside its identity records nothing".** Vaguer than the fact warrants, and it invites the argument above about ledger rows. There is exactly one input in this system keyed by neither run nor walk, so the category can be named rather than described.

## Decision

### A run's identity never names the answers a person gave

`ScoringRun.configConsumed` keeps the four values ADR-117 left it with, and no run in this system names a relevance label, a count of them, or anything derived from them. A label is a fact about a document; identities are names for configurations; the two do not meet.

### A step that reads the answers records no completion, and re-does its work every invocation

**Two steps are named, and they are the only two**: `relevance-floor` and `relevance-report`. Both write under `ScoringRun`, and both read `relevance_label` rows. No other step in the job reads them — `LabelIngestion` is a subcommand rather than a step and writes under no run, and `NextAction` is the message an invocation ends with rather than a step of it.

**ADR-116's list of eight becomes six**: `redundancy-signature`, `content-redundancy`, `seed-extraction`, `embedding-scoring`, `clustering`, `arrangement`. Each of those consumes only what its run's identity names or what the upstream chain names for it.

### They keep the second of ADR-116's two rules and lose the first

ADR-116's rules separate cleanly, and only one of them is unsafe here:

- **Lost — "a step whose completion is recorded does no work".** These two have no completion record, so they always work.
- **Kept, and now unconditional — "a step discards its own rows under that run and does the work again".** For `relevance-floor` that is the `below-threshold` verdicts under this scoring run, discarded **before the floor's state is consulted rather than inside the one branch that writes**. So the removals standing under a run always reflect the answers and the number as they are now: an answer that turns an applicable floor into one calibrated elsewhere retracts the removals it no longer justifies, which is the same event as the one that grants them, run backwards. For `relevance-report` there are no rows at all — it writes two files, and rewriting a file is its own discard.

**This is affordable, which is why it is the answer rather than a compromise.** `relevance-report` re-renders two files over stored scores and cached text. `relevance-floor` re-runs one comparison over scores this run already holds. Neither calls Ollama, neither calls Docling, and neither is the work ADR-115 set out to stop repeating: what that record was paying for was walks and embeddings.

### `generation` is unchanged, and is owed rather than done

ADR-116's 6b clause stands word for word: generation's `finished_step` row is written only when every cluster of the approved arrangement carries a `synthesis_doc` row (ADR-111). It is not in the list of six because it was never in the list of eight — its rule is ADR-111's finer one rather than a rule it gains. Nothing in the tree writes that row today, because 6b generates nothing yet: the stage is its gate, its run and its wiring. **The clause is a claim about work that belongs to #179, and until that work lands, the record describes the design rather than the tree.** `GenerationRun` names the approved arrangement run and the generation model, reads no answers, and needs no exception here.

## Consequences

**`RelevanceFloorTasklet` loses its skip and gains an unconditional discard.** Both its completion calls go, and `discardVerdicts` moves ahead of the state switch. That is a correction of wiring landed under a list this record has narrowed, not a re-decision of anything: the step's behaviour in each of the floor's three states is exactly what ADR-088 and ADR-117 already specify.

**`vespera run` under an unchanged profile now removes what the answers currently justify, every time.** Previously the first invocation's verdict set was frozen under that run id. A verdict set that tracks the answers is the same promise ADR-048 makes for the run id, kept at the one point where the identity could not keep it.

**An operator who re-answers the sample under the current model gets the floor applied on the next invocation.** No profile edit, no new run id, no re-score. That is the repair `CalibratedElsewhere` exists to prompt, and it was unreachable for as long as the step could record completion.

**The labelling page counts every answer given so far, on every invocation.** Which is ADR-088's headline consequence, and the claim the red test was defending.

**Adding a step that reads the answers now costs a decision.** There are two, they are named here, and a third would be a step whose skip rule cannot be derived from its run id. The question to ask of a new step is the one ADR-117 phrased for profile keys — *which run's identity names this* — with a second answer now legitimate: *none, and the step therefore records no completion*.

**ADR-116's clause is now load-bearing rather than cautionary.** It was written as a warning about one key that ADR-117 then closed. It turns out to name a second and larger case that ADR-117 could not close, and it decided that case correctly in advance. Nothing about it changes; it acquires the two steps it was always about.

## What this does not decide

**Whether `ScoringRun` should name the whole embedder identity rather than the model's name.** ADR-117 left it open, for the reason it gives — deriving a run id would mean asking a live sidecar — and it stays open. It is the same class of defect and a different cost.

**Whether an operator should be told that an answer changed what the floor removed.** The step already logs which state it is in and how many survivors it removed, on every invocation, which is more than it said when it skipped. Inventing a second message about the change would be inventing a requirement.

**Whether the answers should be reachable from a run at all.** They are reachable today — the labelling page is written by a step of a run, and a label carries the embedder identity that was on screen. That is provenance rather than identity, and this record is only about identity.

**What `generation`'s completion costs to implement.** ADR-116 decided the rule and #179 owns the work. Nothing here brings it forward.
