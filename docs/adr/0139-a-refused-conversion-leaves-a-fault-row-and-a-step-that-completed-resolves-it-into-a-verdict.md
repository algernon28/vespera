# ADR-139 — A refused conversion leaves a fault row, and a step that completed resolves it into a verdict

- **Date**: 2026-09-22
- **Status**: accepted
- **Amends**: [ADR-070](0070-extraction-failed-splits-on-doclings-status-degenerate-output-is-a-two-tier-floor.md) — one sentence of its Decision §"A service-scope failure fails the step and writes nothing" (*"leave the occurrence with no verdict row at all, so a later run examines it again"*), and the consequence resting on it (*"a sidecar outage costs throughput, not archive"*). Everything else in that record stands, the two-tier floor included: §5 below is a measurement of tier 1 doing exactly what it was written to do. Also amends [ADR-071](0071-doclings-invocation-contract-is-one-sync-call-a-local-timeout-and-two-consecutive-streak-breakers.md), which is unchanged in what it counts but gains a second consequence: whether the streak tripped is now read, after the fact, as the answer to a question ADR-070 asked per occurrence.
- **Rests on**: the live database this tool has been run against — `A&RD_GesPOS_BaseDocumentale` (129 occurrences) and `A&RD_ProdCard_BaseDocumentale` (106) — read on 2026-09-22 through a scratch JDBC client. The rows are in §Context; nothing was re-run for them, the run that produced them being the one under explanation.
- **Settles** [#265](https://github.com/algernon28/vespera/issues/265).

## Context

An invocation over 129 files completed stage 2 (`read=118, written=33, skipped=8, filtered=77`) and then failed at stage 5d:

```
java.lang.IllegalStateException: occurrence 1 has no stored chunk vectors to score;
a corpus survivor with no chunks was confirmed impossible by construction (#108)
-- if this is reached, stage 2's no-text floor let one through
```

Everything before that step was committed; the invocation produced no relevance report and reported failure.

**Eight occurrences of that walk carry neither an `extraction_metric` row nor a verdict row — 1, 54, 55, 56, 106, 112, 113 and 114 — and they are exactly the eight the step logged a service-scope failure for.** Seven are `OLE_COMPOUND`/`LEGACY_SPREADSHEET`, one is `OLE_COMPOUND` with no subtype. The arithmetic closes: 129 walked, 110 with a metric row, 19 never extracted, of which 11 are `SUPERSEDED_BY` — removed with a verdict — and 8 are these, removed by nothing.

A survivor is an occurrence carrying no blocking verdict. These eight therefore stayed survivors, and stage 5 asked the first of them for chunk vectors it could not have.

### What the converter actually reported, and which rule routed it

The message is the same on all eight, up to the extension ADR-100 sends. It is quoted as it was logged on 2026-09-22, which is before §3, and the same refusal does not log this line any more: the category appears twice there because the processor prefixed it into the detail and the exception's own format prefixed it again, and §3's normalisation leaves `(unknown): An unexpected error…`. Read the doubling as the evidence for that paragraph rather than as current output.

```
occurrence 54: service-scope failure (unknown): unknown: An unexpected error
occurred while opening the document document.xls.
```

So the response carried `status` of `failure` with an `errors[]` entry categorised **`unknown`** — the one category ADR-070 routes away from any verdict, on the ground that an uncategorised error "is not evidence about a document, and the safe reading of no evidence is 'not judged yet.'" That reading is sound as a statement about evidence. What it did not anticipate is that **"not judged yet" and "judged and passed" are the same answer to the survivors query**, so an occurrence no stage examined travels the rest of the cascade as one every stage approved of.

Nor does a later run recover it. The refusal is a property of a container the converter cannot open, so the same eight fail identically on every invocation, and the skip leaves nothing behind for a re-run to use.

### The asymmetry, and it is the same response on both sides

That same `failure` response reaches two passes of this system. On the **seed** side, `SeedExtractionItemProcessor` reads the text out of it, finds none, and returns an unusable seed: a row in `unusable_seed`, a line in the report, and the run continues (ADR-083). On the **corpus** side it becomes a Spring Batch skip, and `ExtractionJobConfiguration`'s own comment states what that costs — *"A skip in that chunk rolls the transaction"* — so whatever the processor wrote about that occurrence goes back with it. An unreadable seed is recorded and the run goes on; a corpus document the converter cannot open is recorded nowhere and stops the invocation four stages later. ADR-100 settled the neighbouring case, in this same stage, the seed side's way: a missing `detected_format` row "fails the occurrence, not the run."

That asymmetry is recorded nowhere, and it is what #265 reports.

### The second route does not exist, and that is measured rather than argued

#265 names a second path to the same throw: 22 occurrences with `status='success'`, `character_count = 0` and `word_count = 0`, which have a metric row but no text, therefore no chunks, therefore no vectors. The counts are right. The inference drawn from them is not, and the column that settles it is the one tier 1 actually reads:

| measured, 2026-09-22 | rows |
| --- | --- |
| `extraction_metric` rows with `status='success'` and `character_count = 0` | 22 |
| of those, with `alphanumeric_char_count = 0` | 22 |
| of those, carrying a `degenerate-output` verdict | **22** |
| of those, reaching stage 5 as a survivor | **0** |

ADR-070's tier-1 floor removed every one of them, which is what it exists to do. Eleven sit under this walk's extraction run and eleven under the second corpus's; eighteen are `.xlsx`, and the remaining four — two `.csv` and two `.png` — are worth naming only because the case is wider than spreadsheets. The pipeline already pins this end to end: `RelevanceScoringInvocationTest.aDocumentWithNoUsableTextIsNeverScored` drives a no-text conversion through the whole job and claims the invocation succeeds and no score row exists for it.

**So there is one route, not two**, and the sentence the error message ends with — *"stage 2's no-text floor let one through"* — is false about every occurrence it could be read against. The floor let nothing through. What reached stage 5 was an occurrence the floor never saw, because nothing was ever measured for it.

## Decision

### 1. A refused conversion leaves an `extraction_fault` row

`extraction` gains a table, on the precedent of `unusable_seed` (ADR-083) and `cluster_fault` (ADR-111):

```sql
CREATE TABLE IF NOT EXISTS extraction_fault (
    occurrence_id INTEGER NOT NULL REFERENCES file_occurrence (id),
    run_id TEXT NOT NULL REFERENCES run (id),
    category TEXT NOT NULL,
    detail TEXT NOT NULL,
    PRIMARY KEY (occurrence_id, run_id)
);
```

One row per occurrence per run, carrying the category the response reported and the message it came with. It is **not** a verdict and removes nothing on its own: what it records is that stage 2 got no answer about this occurrence, which is exactly what ADR-070 means by service scope, made into a row rather than left as an absence. `ExtractionSchema.VERSION` moves to 5 in the same commit.

### 2. The row is written at the end of the step, from memory, never inside the chunk transaction

The recorder is one step-scoped listener playing two roles: as a `SkipListener` it holds each service-scope skip in memory, and as a `StepExecutionListener` it writes them all in `afterStep`, in a transaction of its own. Nothing is written at skip time.

**Writing at skip time in a second, `REQUIRES_NEW` transaction was considered and refused on the store.** SQLite admits one writer: a second connection writing while the chunk transaction holds the write lock is exactly the contention ADR-127's busy timeout exists to survive, bought here for nothing. Under the test profile it would not even be contention — the pool is one connection, so the nested write would wait for a connection its own caller is holding, and every test that drove it would deadlock.

What that costs is small and honest: an invocation killed mid-step records no faults. It also records no completion for that step, so a re-run discards this run's rows and does the work again (ADR-115, ADR-116) — and the refusal is deterministic, so it is faulted again. **That second sentence is true only under section 4's listener order**, which is what puts the fault write ahead of the completion write; reverse that order and this paragraph becomes false, in the one direction that costs archive. Section 4 keeps the reason.

**The reader's discard gains `extraction_fault`**, alongside the metric rows, the shingles and the two verdict kinds it already clears, for the reason it clears those: the primary key would otherwise collide on the second write.

**It goes in the reader rather than in the recorder's own `beforeStep`, and that is decided against a working alternative rather than for want of one.** Putting it in `ExtractionFaultRecorder.beforeStep` runs at a correct moment: `AbstractStep.execute` calls `getCompositeListener().beforeStep(...)` on the line immediately above `open(...)` (measured, `spring-batch-core` 6.0.5), so every listener's `beforeStep` precedes the reader's stream being opened, and both sites sit outside every chunk transaction. Timing is not what separates them. Two other things are. First, the discard is one decision — *this run's stage-2 rows start clean before stage 2 redoes any of them* — and the reader is where that decision already exists as one enumerated list; splitting it means the next person to add a table keyed by `(occurrence_id, run_id)` reads that list, finds it complete, and ships the collision it exists to prevent. Second, the guard is `ledger.stepFinished(runId, STAGE)`, and in the reader it is the same expression that already decides whether to yield anything at all; evaluated again in another class it becomes two independent readings of one fact that have to agree, with nothing making them. Cohesion with the writer is the argument on the other side, and it loses to those two: the recorder's business is the skips it holds, and an unrelated run-hygiene delete in its `beforeStep` is a second job in a listener that otherwise does nothing until `afterStep`.

What that costs is a fifth parameter on `extractionReader` and therefore a change to `ExtractionRunTest`, which calls that method directly as plain Java at both call sites in `readsWhicheverArchiveItIsGiven`. The parameter is a `JdbcTemplate`, and the reader builds its own `ExtractionFaults` from it exactly as the `extractionFaultRecorder` bean method already does — `ExtractionFaults` is deliberately not a `@Component` (§1's precedent, `ClusterFaults`), so there is no bean to inject and every caller that needs one holds a `JdbcTemplate` already. `ExtractionRunTest` holds one too, beside the `ExtractionMetrics` and `Shingler` it already constructs by hand, so the change there is one argument at two call sites. A pinned parameter list is a reason to change a test, not a reason to keep one decision in two places.

### 3. A step that completed resolves every fault under its run into `extraction-failed`

In that same `afterStep`, and only where the step's exit status is `COMPLETED`, each fault row's occurrence earns `EXTRACTION_FAILED`, its reason composed by the recorder as `category + ": " + detail` — the shape `ExtractionItemProcessor.reasonFor` already gives every document-scope verdict, so a reason does not read differently for having been written by a listener.

**Composed rather than copied, and the case that decides it is the one this record was written about.** `detail` is the converter's own message and nothing else, so the category reaches the reason only if something puts it there. Where the response reported no categorised error at all, `detail` is *"no categorized error was reported"* and the category is `unknown` — the one ADR-070 reads as no evidence rather than as evidence of anything, and therefore the reading an operator most needs off a removal nothing else explains. A reason copied from `detail` alone is exactly where that reading is lost. What composing costs is that `detail` has to be the bare message at every throw site, which not every site currently hands it. `ExtractionItemProcessor` prefixes the category through `reasonFor` twice before an exception is constructed: at the service-scope-category site, and on the path into `resolveTimeout` that carries a Docling-reported timeout — where that same prefix already reaches a document-scope verdict reason as `timeout: timeout: …`. The other two sites — the uncategorised response this record was written about, and the client's-own-silence timeout — already hand the bare message. Normalising `detail` removes a doubling rather than introducing one, and it is what makes section 1's description of the `detail` column — *"the message it came with"* — true of the rows as well as of the sentence.

**Where the step failed, the fault rows stand and no verdict is written — and that branch is reachable but unpinned.** `AbstractStep.execute` calls `afterStep` from a `finally`, so a step the breaker stopped does reach this listener; nothing in the suite drives it there. That is the gap `ExtractionStepTest`'s class javadoc already names and gives the reason for: failing a step has Spring Batch log the whole cause chain at `ERROR`, a passing build that prints stack traces teaches its reader to skim them, and the real one then goes past with the rest. The rule the breaker enforces is pinned where it can be asserted quietly (`ExtractionCircuitBreakerTest`); what stays unasserted is this listener's behaviour under it. Read the branch as decided and unmeasured, not as measured.

**The discriminator is ADR-071's breaker, and it introduces no new number.** ADR-070 asks, per occurrence, whether a failure is about the document or about the sidecar, and at the moment of the failure nothing can answer that — the evidence is what happens to the rest of the step. A step that completed is a step in which five service-scope failures never landed consecutively: the sidecar answered for this occurrence's neighbours, so the refusal is a property of what was uploaded. A step the breaker stopped is the other reading, and there no occurrence is judged on the strength of a sidecar that had stopped answering.

That is ADR-070's own criterion, applied at the first moment it can be evaluated, and it is why the line is 5 rather than a number this record invents.

### 4. The failure stays a Spring Batch skip, and the streak counts exactly what it counted

ADR-071's consecutive-streak breaker counts service-scope skips, of any mix of categories, reset by any completed process. **None of that changes**, and that is a decision rather than an omission. #265's third option — have the processor return a removing verdict instead of throwing — is refused precisely here: `SkipListener.onSkipInProcess` is the breaker's only seam, so a failure that stops being a skip empties the breaker, and a dead sidecar would then condemn an entire corpus as `extraction-failed`, one silent verdict per document. That is the over-blocking ADR-042 and ADR-070 are both written against, at corpus scale. Keeping the skip is what keeps the breaker able to fire.

`ExtractionItemProcessor`'s judgement is therefore unchanged, and every existing pin on it stands. The one edit section 3 makes to that class is to what it hands `ServiceScopeFailureException` as `detail` — the bare message rather than the message with its own category prefixed — which changes no routing, no category and no count.

**What to notice when editing this step**: `.skip(ExtractorStoppedAnsweringException.class)` would now do more than let a dead sidecar produce a successful run, which is the hazard `ExtractionStepTest` already names in its own words. It would make that run write a verdict against every document the dead sidecar was asked about.

**The fault recorder is registered *after* `extractionRunCompletion`, and that is not a tidiness the next edit may undo.** What has to be true is an ordering of two writes: the fault rows and their verdicts commit, and only then does `RunCompletion.afterStep` write the `finished_step` row. The framework fact that makes registration order the lever is measured, from `spring-batch-core` 6.0.5's own sources:

- `CompositeStepExecutionListener.beforeStep` walks `list.iterator()`, forwards.
- `CompositeStepExecutionListener.afterStep` walks `list.reverse()`, backwards. Its javadoc says so in as many words — *"Call the registered listeners in reverse order"*.

So in `afterStep` the listener registered **last** runs **first**, and the recorder has to be registered last of the two. The instruction this record first carried said the opposite, and the opposite is not a near miss: registering the recorder first makes `RunCompletion.afterStep` run first, so the step is recorded as holding all of its work *before* a single fault or verdict is written. A crash in that window leaves a `finished_step` row over a step with no faults and no verdicts under it — and `RunCompletion`'s own javadoc says what that is worth, that recording completion for a step that did not finish "would make a partial invocation indistinguishable from a complete one — and every later invocation would skip it forever." Those occurrences would be unexamined survivors that no re-run ever revisits, which is #265 exactly, reintroduced inside a crash window by the line written to close it.

**`@Order` and `Ordered` are refused as the mechanism, on two measured properties of `OrderedComposite`** — the list behind that composite, and the reason a reader cannot check this by intuition. First, `add` places ordered items ahead of unordered ones in `list`, and `reverse()` therefore runs them **last** in `afterStep`; its own javadoc says "the `Ordered` items come last". An annotation that reads as "first" would do the opposite of what the reader of it expects, in the direction that costs archive. Second, both listeners are `@StepScope`, which is `proxyMode = TARGET_CLASS`, so what reaches the builder is a CGLIB subclass — and `OrderedComposite.add` detects a class-level `@Order` through `AnnotationUtils.isAnnotationDeclaredLocally`, which a generated subclass does not satisfy. The annotation would be silently ignored and the order would stay whatever registration made it, with a line of code in the tree claiming otherwise. Implementing `Ordered` does survive the proxy, but it buys a method on a class to say a thing the registration line already says, and it still reads backwards under `reverse()`.

**The pin is a test, because this is invisible in a green run.** Nothing reads `finished_step` again within one step execution, so every assertion in this suite passes under either order. `ExtractionFaultInvocationTest.theVerdictIsCommittedBeforeTheStepIsRecordedAsComplete` observes the order where it is observable — it watches what `finished_step` could see of `extraction_fault` at the moment stage 2's completion was recorded, and fails if that is nothing.

### 5. A survivor with no text is not a survivor, and ADR-070 has no bug here

This is #265's substantive question, and the measurement above answers it: a document that yielded no text is **removed**, by `degenerate-output`, and it was removed in all 22 cases the ticket found. It is not a survivor scoring zero, and nothing here scores one. Tier 1 is the rule, it is enforced, and this record changes nothing about it.

What the eight occurrences show is not a hole in that rule but a hole beside it. **Survival means examined and not removed**; it never meant unexamined. Sections 1 to 3 close the one path by which an unexamined occurrence reached a later stage as a survivor, which is what makes that sentence true of the ledger rather than merely intended.

### 6. Stage 5 keeps asserting, and its message stops naming the floor

`RelevanceScoring.scoreAndRecord` goes on throwing for a survivor with no stored chunk vectors. It gets no `cluster_fault` equivalent, for two reasons. The measured one: with section 5 and sections 1 to 3, no route reaches it — the no-text case never gets there, and the refused case is removed before stage 3 queries survivors at all. The structural one: ADR-111's faults record an answer that came back from a model and did not survive checking, which is a fact about that answer. A survivor with no vectors is not a fact about anything outside this system; it is the ledger disagreeing with itself, and a row for it would turn a broken invariant into a statistic.

The message changes, because it accuses a rule this record measured innocent. It names what is actually possible — an occurrence no stage examined, or vectors a changed embedding model never wrote — and stops naming tier 1.

### 7. It is visible to an operator, not only to a query

The occurrence earns a verdict whose reason carries the category and the converter's own message, and a fault row carrying the two in columns of their own. The stage-2 log line already names each one as it happens. The confidence-distribution page — the one page written over stage 2's own measurements — gains a line stating how many occurrences the converter refused to open under this run, so the count sits on a page the operator already opens rather than only in the database.

### 8. The vocabulary gains an entry

`CONTEXT.md` gains **extraction fault**, beside **unusable seed** and **cluster fault**, so the row has a name in our own prose. The word *unreadable* is deliberately not used for it: `CONTEXT.md` has already claimed that word for a profile value, the same way **gate** and **checkpoint** divide.

## Consequences

**A document the converter cannot open is removed rather than left unexamined, and that removal outlives its run.** `survivors` is an anti-join over every blocking verdict regardless of run, so an `extraction-failed` row written here keeps that occurrence out under a later extraction run too — as every other `extraction-failed` row already does. ADR-070's "a later run examines it again" is what this trades away, and the trade is made knowing that a later run examined those eight again and got the same refusal every time. The retune path is unchanged and is the one the ledger model already has: delete that stage's rows for the run, and run it again.

**A sidecar outage can now cost archive, where ADR-070 promised it could not.** The case is a run that completes with fewer than five consecutive service-scope failures in it, some of which were genuinely the sidecar's — a capacity blip between two good documents. Those occurrences are removed. What makes it acceptable rather than merely accepted: the cost is bounded by the breaker; the category is on the fault row and in the verdict's reason, so a `capacity` removal is distinguishable from an `unknown` one by a query rather than by re-converting — and those are the two sides of this very case, the sidecar blip and the eight containers of §Context. The pair a reader might reach for first is not available here and would be a false example: `backend_failure` is unconditional document scope under ADR-071's call shape, never throws service scope, and so can never leave a row in this table at all. The categories that can are exactly five: `capacity`, `target_unavailable` and `internal`, which ADR-070 reads as service scope; `unknown`, which it reads as no evidence at all; and `timeout`, once ADR-071's streak has flipped it out of document scope; and the alternative is what this ticket reports, a document neither removed nor examined, which cost an entire invocation.

**Stage 2 can report what it did not judge.** "How much of the corpus has stage 2 actually looked at" was a question about absent rows, which ADR-070 recorded as a consequence. It is a count over two tables now.

**Nothing here changes the seed side.** ADR-083's `unusable_seed` path already did what this record brings to the corpus side, and a seed still earns no verdict of any kind.

**Nothing here addresses stage 2's serial latency** ([#264](https://github.com/algernon28/vespera/issues/264)) **or the backtick in `marked`** ([#261](https://github.com/algernon28/vespera/issues/261)). Both were open when this was written, and both are untouched by it.
