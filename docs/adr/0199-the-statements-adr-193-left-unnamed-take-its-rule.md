# ADR-199 — The statements ADR-193 left unnamed take its rule, and its table is read again against the code

- **Date**: 2026-10-06
- **Status**: accepted
- **Extends**: [ADR-193](0193-a-statement-sqlite-counts-reports-how-far-it-has-gone-and-one-it-cannot-count-says-how-long-it-took.md) §1, §4, §6 and §7, to the statements its §6 closes on as *"found while making it"* and to three more found since. **It extends one exception ADR-193 drew**: §4.3 makes `<doing>` and `<did>` `reading` and `read` everywhere but `UnrecordedOccurrences.countOver`, which is `counting` and `counted`; the three survivor counts of §2 here take `counting` and `counted` too.
- **Corrects**: one sentence of ADR-193's Tests table, about the removal of `shingle_by_hash` (§7 here), and nothing it decided.
- **Restates**: ADR-193 §9's part (b), as it stands after [ADR-200](0200-stage-1-reads-survivors-by-size-and-holds-one-size-at-a-time.md) and with this record folded into it (§1 here).
- **Answers**: ADR-200 §3 and its "What this does not decide", which left the survivor count inside `ContentIdentityResolution.resolve` to #429 (§2 here).
- **Rests on**: [ADR-040](0040-modules-are-capability-shaped-not-stage-shaped.md) and [ADR-041](0041-ledger-owns-identity-and-verdicts-capabilities-own-their-own-tables.md) (a capability module writes no line), [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) (which run ids move), [ADR-173](0173-every-column-that-references-a-file-occurrence-a-walk-or-a-run-carries-an-index.md) (the indexes `extraction_fault_by_run_id` and `extraction_metric_by_run_id`), [ADR-181](0181-a-stopped-stage-2-resumes-from-what-its-committed-chunks-recorded.md) §1 (the two reads this record counts), [ADR-192](0192-every-loop-in-every-stage-reports-its-progress-and-no-counter-floods-the-log.md), [ADR-197](0197-a-local-model-answers-the-relevance-questions-and-the-floor-is-a-rule-over-the-labels-so-no-document-reaches-a-hosted-model.md) (which added the statements of §2's last three rows) and ADR-200.
- **Keeps**: [ADR-059](0059-schema-version-is-one-row-per-module-checked-and-refused-independently.md). No table, column or index changes.
- **Settles** [#429](https://github.com/algernon28/vespera/issues/429), inside part (b) of [#411](https://github.com/algernon28/vespera/issues/411).

Terms are ADR-193's: a statement is *counted* where SQLite calls back during it and its total is cheap, *timed* where it is not; a *step* is one instruction of SQLite's virtual machine; r is a counted statement's steps a row.

## Context

ADR-193 gave every statement named on #411 and in ADR-192 §7 one of two forms, and landed in two parts. Part (a) is on `main`. Part (b), every other row of its §6, is not built. Three things have happened to part (b) since the record was accepted, and each leaves it something to be told before it is built:

1. **ADR-193 §6 named two statements it did not decide**, `Ledger.survivorCount` and `ExtractionFaults.occurrencesForRun`, and left them to #429. ADR-200 then added a third call of the first, inside `corpus`, and left that to #429 as well.
2. **`main` has moved under §6's table.** It was read off the code at `7b25d04`. ADR-200 struck its two stage 1 rows, and ADR-197 added statements to the relevance report and a command of its own.
3. **Part (a)'s gate, on 2026-10-06, found two things the record says and no test holds**: that each declared ratio is the measured one, and that the removal of `shingle_by_hash` takes the same steps at two sizes.

The operator's sixth decision on #411 is that the archive's next run waits for the whole of it, *"so that run ids are minted once"*. #429 changes `extraction` and `pipeline`, which part (b) changes too. So #429 is not a change of its own: it is part of part (b) (§1).

## Measurements

Synthetic rows only, in memory, under `schema.sql` as shipped at `ffe7066`, on the SQLite of sqlite-jdbc 3.53.2.1, with a callback on every step. The method is `StatementStepsPerRowTest`'s, and the figures are that test's own, at 1,000 and at 3,000 rows of the run read, with 3,000 rows of an earlier run in the same table.

| Statement | Plan | Steps a row |
|---|---|---:|
| `SELECT occurrence_id FROM extraction_fault WHERE run_id = ?` | `SEARCH extraction_fault USING INDEX extraction_fault_by_run_id (run_id=?)`, no temp B-tree | 5 |
| `SELECT occurrence_id FROM extraction_metric WHERE run_id = ?` | `SEARCH extraction_metric USING INDEX extraction_metric_by_run_id (run_id=?)`, no temp B-tree | 5 |
| `SELECT MIN(rowid)` and `SELECT MAX(rowid)` of one run in `extraction_fault` | one descent each | the same steps at both sizes, as on the four tables ADR-193 measured |
| `Ledger.survivorCount`'s `COUNT(*)` | `SEARCH file_occurrence USING COVERING INDEX file_occurrence_by_walk_and_size (walk_id=?)`, then for each occurrence `SEARCH verdict USING INDEX verdict_by_occurrence (occurrence_id=? AND kind=?)` | 43,600 more steps for 2,000 more occurrences of the walk: about 22 an occurrence, with a verdict on every fifth |
| `DROP INDEX shingle_by_hash` | — | 309 steps in all, at 1,000 rows and at 21,000 |

The removal took 300 steps when ADR-193 measured it and takes 309 on `main` now; at either date it took the same number at both sizes. Neither figure is quoted anywhere but here.

## Decision

### 1. One change: #429 is built with part (b)

**Part (b) of ADR-193 and this record are one change, gated and landed together.** It touches `extraction`, `similarity`, `embedding`, `synthesis` and `pipeline`, and nothing in `corpus` or `ledger`.

**Which run ids move**, read off `StageModules`: `extraction` and `similarity` are in stage 2's implementation version, and `pipeline` is in that of stages 3 to 6b, so **the change moves the run ids of stages 2 to 6b. Stage 1's does not move**: its implementation version is `corpus` alone. That is what ADR-200 §7 said part (b) had become, and this record keeps it so (§2, the row for stage 1's second count). It adds no replay to the archive's next run, which waits for part (b) and has stages 1 to 6b to mint again in any case, under ADR-192 and ADR-200.

### 2. The form of each statement no list named

| Statement, where it is issued | Form | Why |
|---|---|---|
| `Ledger.survivorCount` in `ByteLevelReductionTasklet`, sizing the broken check's counter | **timed**, by `pipeline`, around the call | An anti-join over the walk's occurrences: the thing a total would be is the thing being asked (Measurements). ADR-193 §1's *"no cheap total"*. |
| `Ledger.survivorCount` in `ContentIdentityResolution.resolve`, sizing the counter over the sizes read (ADR-200 §3) | **timed**, by `pipeline`, with no change to `corpus`: the line before is written immediately before `pipeline` calls `resolve`, and the line after when `HashingProgress.toSize` arrives | The count is the first thing `resolve` does, and `toSize` is the first callback, called with the count (ADR-200 §3). So the stretch from the call to `toSize` is the count and nothing else, and `corpus` already tells `pipeline` when it ends. |
| `Ledger.survivorCount` in `RedundancySignatureItemWriter`'s constructor, sizing 4a's counter | **timed**, by `pipeline`, around the call | As the first row. |
| `Ledger.survivorCount` inside `UnrecordedOccurrences.countOver` | **already decided**: ADR-193 §6's one timed span over the whole `countOver(…)` expression in `ExtractionItemProcessor`'s constructor | It gets no second pair of lines, and no method of `Ledger` or of `UnrecordedOccurrences` is wrapped. |
| `ExtractionFaults.occurrencesForRun`, in the reader stage 2 opens (`ExtractionJobConfiguration`) | **counted**, r = 5, total the span of the run's rowids in `extraction_fault` | Both conditions of ADR-193 §1 hold: an index on `run_id` alone with no temp B-tree, and a span that is two descents. |
| `ExtractionMetrics.occurrencesForRun`, in the same reader, after the removal of `shingle_by_hash` | **counted**, r = 5, total the span of the run's rowids in `extraction_metric` | The same two conditions, on `extraction_metric_by_run_id`. In no row of ADR-193 §6 and not named on #429; found while #429 was first drafted. It is the longer of the two waits on a resume: one row for every occurrence the stopped invocation's committed chunks converted. |
| `ExtractionMetrics.occurrencesForRun` in the argument of `countOver`, in `ExtractionItemProcessor`'s constructor | **already decided**: inside ADR-193 §6's timed span | It keeps the signature that reports nothing. |
| `RelevanceLabels.modelAnswers`, in `RelevanceReportTasklet` (ADR-197 §3) | **timed**, by `pipeline`, around the call | It reads the answers a model gave for one seed set: no run, so no span, exactly as `RelevanceLabels.forSeedSet` beside it, which ADR-193 §6 times. ADR-197 added it inside a stage's step after §6 was made. |
| `RelevanceLabels.forSeedSet` and `modelAnswers` in `AutoLabelling` | **no line** | `AutoLabelling` is built for `vespera label --auto` and reached from `VesperaCommand`'s `label` command alone. That is not a stage, and ADR-193 §6 already says of `LabelIngestion`'s statements under the same command that nothing outside the job's steps is announced. |

**Why counted and not timed for the two reads of a resume**, where the fault read will almost never write a progress line: at 5 steps a row the first callback falls at 20,000 rows, and a run holds a fault only for a file the converter blamed on itself. The rule is ADR-193 §1's, and it is not a judgement of how often a line is written. What counted buys here is that **a run that holds no row says nothing**, which is every first invocation; a timed statement writes both lines whenever it is issued (ADR-193 §4.3), so timing them would put four lines about reading nothing at the start of every stage 2.

**Stage 1's second count is timed from outside, and that is the one place this record leans on an order another module keeps.** `pipeline` cannot put two lines around a call made inside `corpus`. The other way was ADR-193 §7's: a `CorpusStatement` with one constant, a `CorpusStatementProgress`, and `HashingProgress` extending it. That is what ADR-200 §7 took out of part (b), it would move stage 1's run id for one pair of lines, and it would tell `pipeline` nothing `toSize` does not already tell it. If `resolve` ever does something before its count, the line's duration takes that in too; ADR-200 §3 has `toSize` called once and first, and `StageOneHoldsBoundedMemoryTest` pins the whole sequence.

### 3. Every line of part (b), written out

ADR-193 §6 gives each statement its `<what>`; this section gives each its `<stage>` and adds the rows of §2. **`<stage>` is the name the stage's own starting and finishing lines use.** A timed statement writes ADR-193 §4.3's two lines, a counted one §4.1's two and its progress lines between. `<S>` is seconds to one decimal place, and `<N>` is grouped in threes.

```
<stage> is reading <what>                              <stage> is counting <what>
<stage> read <what> in <S> s                           <stage> counted <what> in <S> s

<stage> is reading <what>, over up to <N> rows
<label>: about <X>% of <N> rows
<stage> read <what> in <S> s
```

In the order each step issues them:

| `<stage>` | Form | `<what>`, and for a counted statement its `<label>` |
|---|---|---|
| `Stage 1 (byte-level reduction)` | timed count | `the survivors to check` |
| `Stage 1 (byte-level reduction)` | timed count | `the survivors to size` |
| `Stage 2 (extraction)` | counted, 5 | `the faults already recorded`; `Stage 2 (extraction, reading faults already recorded)` |
| `Stage 2 (extraction)` | counted, 5 | `the occurrences already measured`; `Stage 2 (extraction, reading occurrences already measured)` |
| `Stage 2 (extraction)` | timed count | `the survivors still to read` |
| `Stage 2 (extraction)` | timed | `the occurrences it could not read` |
| `Stage 3 (content census)` | timed | `stage 2's survivors for the shingle frequencies` |
| `Stage 3 (content census)` | counted, 7 | ADR-191's lines, on `main` since part (a) |
| `Stage 3 (content census)` | timed | `stage 2's survivors for the confidence distribution` |
| `Stage 3 (content census)` | counted, 7 | `the extraction metrics`; `Stage 3 (content census, reading extraction metrics)` |
| `Stage 4a (redundancy signatures)` | timed count | `the survivors to sign` |
| `Stage 4b (redundancy resolution)` | counted, 5 | `the signed occurrences`; `Stage 4b (redundancy resolution, reading signed occurrences)` |
| `Stage 4b (redundancy resolution)` | timed | `the signature bands` |
| `Stage 4b (redundancy resolution)` | timed | `the near-duplicates' extraction metrics` |
| `Stage 4b (redundancy resolution)` | timed | `the shingle document frequencies` |
| `Stage 5b (seed/corpus comparison)` | timed | `the corpus survivors` |
| `Stage 5b (seed/corpus comparison)` | timed | `the seed walk's occurrences` |
| `Stage 5b (seed/corpus comparison)` | counted, 5 | `the unusable seeds`; `Stage 5b (seed/corpus comparison, reading unusable seeds)` |
| `Stage 5b (seed/corpus comparison)` | counted, 12 | `the corpus survivors' extraction metrics`; `Stage 5b (seed/corpus comparison, reading corpus metrics)` |
| `Stage 5b (seed/corpus comparison)` | counted, 12 | `the seeds' extraction metrics`; `Stage 5b (seed/corpus comparison, reading seed metrics)` |
| `Stage 5c (embedding scoring)` | timed, three | `the corpus survivors`, `the seed walk's occurrences`, `the unusable seeds` |
| `Stage 5d (relevance scoring)` | timed, three | `the seed walk's occurrences`, `the unusable seeds`, `the corpus survivors` |
| `Stage 5e (relevance floor)` | timed | `the embedder identities` |
| `Stage 5e (relevance floor)` | timed | `the recorded answers`, issued only where the floor is a number |
| `Stage 5e (relevance floor)` | timed | `the scores below the floor`, issued only where the floor applies |
| `Stage 5f (clustering)` | timed | `the seed partitions` |
| `Stage 5f (clustering)` | timed | `the corpus survivors` |
| `Stage 5f (clustering)` | timed, once a partition | `the members of partition <P> of <M>`, every partition's before the first is clustered |
| `Stage 5f (clustering)` | timed, once a partition that kept a member | `the cluster sizes of partition <P> of <M>` |
| `Stage 5 (relevance report)` | timed | `the scores` |
| `Stage 5 (relevance report)` | timed | `the recorded answers` |
| `Stage 5 (relevance report)` | timed, one call | `the scores against the answers` |
| `Stage 5 (relevance report)` | timed | `the embedder identities`, for `embedderIdentityFor` |
| `Stage 5 (relevance report)` | timed | `the recorded answers` a second time, through `RelevanceFloor`, issued only where the floor is a number |
| `Stage 5 (relevance report)` | timed | `the embedder identities` a second time, for `anyEmbedderIdentity` |
| `Stage 5 (relevance report)` | timed | `the answers a model gave` (§2) |
| `Stage 6a (arrangement)` | timed, two | `the cluster membership`, then `the recorded clusters` on either branch |
| `Stage 6b (generation)` | timed, two | `the cluster membership`, `the recorded clusters` |
| `Stage 6b (generation)` | timed, one call | `the clusters already written` |
| `Stage 6b (generation)` | timed | `the standing faults`, not issued where the walk stops on five answers turned down in a row |
| `Stage 6b (generation)` | timed, two | `the clusters written`, `the faults recorded` |

- **Stage 1's `<what>` are the two ADR-193 §6 gave to the drains ADR-200 deleted.** The words are free again, and they say what each count is for.
- **Stage 2's two counted reads come before `Stage 2 (extraction) resumes run …`**, the fault read's lines first, with ADR-187's lines for the removal of `shingle_by_hash` between the two reads where the index is there. Neither read says anything over a run that holds no row of its table.
- **`RelevanceFloor` is asked by two steps**, 5e and the report, so the read it makes carries the `<stage>` of the step that asked.
- **A statement not issued writes nothing** (ADR-193 §4.3). A step already recorded under its run issues none of the statements inside its work. 6a's two reads are outside that work and are issued on both branches (ADR-154 §2).

### 4. The callbacks

ADR-193 §7 stands, less `corpus` (ADR-200), with two constants more. The four enums, each constant in the order its statement is issued, with r where it is counted:

| Enum | Constants |
|---|---|
| `extraction.ExtractionStatement` | `FAULTED_OCCURRENCES` (5), `RECORDED_OCCURRENCES` (5), `SURVIVORS`, `EXTRACTION_METRICS` (7) |
| `similarity.SimilarityStatement` | `SHINGLE_HASH_INDEX_BUILD` (11), `FREQUENCY_SURVIVORS`, `SHINGLE_ROWS` (7), `SIGNED_OCCURRENCES` (5), `SIGNATURE_BANDS`, `NEAR_DUPLICATE_METRICS`, `DOCUMENT_FREQUENCY` |
| `embedding.EmbeddingStatement` | `CORPUS_SURVIVORS`, `SEED_OCCURRENCES`, `UNUSABLE_SEEDS` (5), `CORPUS_METRICS` (12), `SEED_METRICS` (12) |
| `synthesis.SynthesisStatement` | `WRITTEN`, `STANDING_FAULTS` |

- **Where each interface reaches its method.** `similarity.ResolutionProgress` and `synthesis.GenerationProgress` extend their module's `<Module>StatementProgress`, as `FrequencyProgress` does since part (a). `ConfidenceDistribution.measure` gains an overload taking an `ExtractionStatementProgress`, `SeedCorpusComparison.measure` one taking an `EmbeddingStatementProgress`, and `ExtractionFaults.occurrencesForRun` and `ExtractionMetrics.occurrencesForRun` one each taking an `ExtractionStatementProgress`. Every old signature calls the new with a progress that does nothing.
- **The order, for every statement.** `statementStarting` once before it; `stepsTaken` at each callback of SQLite's handler, for a counted one; `statementEnded` once after it, on every path but one that throws. A statement that is not issued makes no call.
- **The total.** A timed statement is started with an empty total. A counted one is started with its span, and with an empty total where the run holds no row. **`pipeline` writes neither line and counts nothing for a counted statement started with an empty total**, as it does for `SHINGLE_ROWS`; the silence of a first invocation is `pipeline`'s, not the capability module's.
- **The statements `pipeline` issues or calls itself need no callback**: the three timed survivor counts, stage 2's count and its review-list read, and every row of §3 from 5c on but 6b's two inside `ClusterGeneration.write`.

### 5. ADR-193 §6, read again against `main` at `ffe7066`

Every row of §6 was checked against the code. **Each row not named below stands as written**, at the class and method it names. The record quotes two line numbers, in §9, `ExtractionItemProcessor.java` lines 157 and 158, and the expression is still there.

- **The two stage 1 rows** are struck by ADR-200 §7. §2 here gives stage 1 two timed counts, which are not those rows come back: they time a count, where the rows timed a drain.
- **The report issues `RelevanceLabels.forSeedSet` twice where the floor is a number**, once for the answers it matches and once through `RelevanceFloor`, and `the embedder identities` twice, for the two methods §6 lists in one row. §3 writes each out.
- **`RelevanceLabels.modelAnswers`** is new in the report since ADR-197 and is given a form in §2.
- **`AutoLabelling`** is new since ADR-197 and is outside the job's steps (§2).
- **`ResolutionProgress` does not yet extend `SimilarityStatementProgress`**, and `SimilarityStatement` carries two of its seven constants. Part (a) built what its own statements needed, as ADR-193 §9 divided the work.

### 6. The declared ratios are held to the measured ones

ADR-193 §3 has `StatementStepsPerRowTest` measure each r and the module beside the SQL declare it, and its parked `StatementStepsPerRowAreTheDeclaredOnesTest` hold the second to the first. That test cannot compile before part (b), so since part (a) three declarations have stood in `src/main` with nothing holding them. **`DeclaredStepsPerRowOfPartATest` holds those three now**, and the change that moves the parked test into `src/test` deletes it, the parked test making the same three claims.

### 7. One sentence of ADR-193's Tests

ADR-193's Tests table says `StatementStepsPerRowTest` pins *"the drop's steps the same at two sizes and fewer than one callback"*. The test asserted the second, and of the first only that the larger size took fewer than a thousand steps more. **The measurement supports the sentence** (Measurements: 309 and 309), so the test is changed and the sentence stands: the removal's steps at 21,000 rows equal its steps at 1,000.

### 8. What a search of `src/main` found and this record does not decide

#429 asks whether a survey of every statement in `src/main` is owed. **None is built here, and none is a standing duty**: ADR-193 §1 already puts a statement that a change adds into one of the two forms in the same change. What is left is what was already there. A search of `src/main` for SQL text, against ADR-193 §6 and ADR-192 §7, found these. **It is a list of what one search found, not a proof that nothing else is there, and it decides none of them**:

- **The deletes of a step that is run again**, each going through every row of its run in a table: `Ledger.discardVerdicts` and `discardVerdictsAgainst`, and each capability's `discardForRun`. ADR-193 gives a delete no mechanism.
- **`Ledger.discardWalk`**, three deletes, from `WalkRecorder`.
- **Stage 0's comparison of a finished walk with the one before**, `Ledger.occurrencesForWalk` and `AnomalyLog.anomaliesForWalk`, and its counts, `Ledger.occurrenceCount` and `AnomalyLog.anomalyCount`.
- **`Ledger.occurrenceCount` in `SeedExtractionItemProcessor`**, sizing 5a's counter over the seed walk: the sibling of the survivor counts of §2, over a seed folder.
- **The counts `InvocationAccount` makes when the job ends** (ADR-198).
- **A loop, not a statement**: `RelevanceReportTasklet.modelAnswersInThisWalk` looks each answer a model gave up in the ledger, one statement an answer, with no counter. Its sibling over every answer has one (`Stage 5 (relevance report, answers matched)`). That is ADR-192's rule, not this record's.

## Why this shape, and what the others cost

- **An amendment written into ADR-193.** `docs/adr/README.md` reopens a record only by a later record, and ADR-193's own §6 and ADR-200 §3 both point at #429 for these statements. A record of its own is also where part (b)'s lines can be written out once, for the change that builds them.
- **#429 as a change after part (b).** It would move the run ids of stages 2 to 6b a second time, against the operator's sixth decision.
- **Timing the two reads of a resume.** Simpler, with no overload in `extraction`. Not taken: four lines about reading nothing on every first invocation, for reads that meet both of ADR-193 §1's conditions.
- **A `CorpusStatement` for stage 1's second count.** §2 says why not.
- **No line for stage 1's second count**, on the ground that the first pass has just timed the same statement. The operator decided on 2026-10-04 that every stage says what it is doing whatever its duration, and the second count runs over a different set: the first pass has written its verdicts by then.
- **Wrapping `survivorCount` inside `countOver` as well.** Two pairs of lines for one wait.
- **Counting `survivorCount`.** There is nothing cheap to count against.

## Consequences

- **Production code changes in five modules**, in one change (§1). No schema version moves.
- **Lines change by addition.** Beyond ADR-193's: two pairs at stage 1 and one at 4a wherever the step does its work; at stage 2 one pair on an invocation that resumes over a fault and one on an invocation that resumes over a committed chunk; one pair in the relevance report.
- **ADR-193 §6's closing bullet is answered** for the two statements it names. It still says that the survey behind its table was of two lists, and §8 here says what one wider search left.
- **`docs/adr/` no longer reserves a number.**

## Tests

**In `src/test`, green on `main` as it is:**

| Test | Pins |
|---|---|
| `pipeline.StatementStepsPerRowTest`, extended | r = 5 for each of stage 2's two reads on a resume, each through an index on `run_id` alone with no temp B-tree; the bound on `extraction_fault` costing the same at two sizes; the survivor count's plan naming both `file_occurrence` and `verdict`, and its steps growing with the walk; the removal of `shingle_by_hash` taking the same steps at two sizes (§7) |
| `pipeline.DeclaredStepsPerRowOfPartATest` | the three ratios declared on `main` equal to the measured ones (§6) |
| `pipeline.StatementLines` | a helper, not a test: reads a stage's statement lines out of what an invocation logged, for the parked tests below |

**Parked under `docs/adr/0193/tests/b/`, as complete files at the path each takes in the repository, for the change that builds part (b) to move into `src/test`.** ADR-193's Tests say why a test is parked. Six of them are `src/test` files of today with claims added, and each replaces the file of its name; the claims added are red on `main` by assertion, the line not being there, and every other claim in them passes.

| Test | State on `main` | What part (b) must make pass |
|---|---|---|
| `pipeline.StatementStepsPerRowAreTheDeclaredOnesTest` | does not compile: three enums are missing | every declared r equal to the measured one, the two reads of §2 included, and every timed constant declaring none |
| `extraction.ExtractionStatementProgressOrderTest` | does not compile | the callbacks of each of stage 2's two reads and of `ConfidenceDistribution.measure`, in §4's order: an empty total over a run with no row; the span over a few; steps between the start and the end over many, on a pool of two outside a transaction, so on the connection the handler is on; no end after a throw; no handler left |
| `similarity.SimilarityStatementProgressOrderTest` | does not compile | `DocumentFrequency.measure`'s two statements in order; `RedundancyResolution.resolve`'s four among its loops, and none after the first where nothing is signed; the signed-occurrences read reporting steps on a pool of two; the build's three callbacks |
| `embedding.EmbeddingStatementProgressOrderTest` | does not compile | `SeedCorpusComparison.measure`'s five statements in order; the unusable-seeds read started with an empty total where none is recorded; each counted read reporting steps on a pool of two |
| `synthesis.SynthesisStatementProgressOrderTest` | does not compile | `ClusterGeneration.write` starting and ending `WRITTEN` before the walk is announced and `STANDING_FAULTS` after the last cluster |
| `PoolOfTwo` (in the root test package) | compiles; nothing uses it yet | a helper: a pool of two connections over a file of its own under `schema.sql`, and how many of them carry a handler |
| `pipeline.StageOneReportsItsLoopsInvocationTest` | 1 of its 2 tests red | stage 1's two timed counts, once each, in order, each ended before the counter it sizes writes a line |
| `pipeline.StageTwoReportsItsFaultResolutionInvocationTest` | 2 of 2 red | stage 2 on a first invocation: the count of the survivors still to read and the review-list read, once each, in order, and no line of either read of a resume |
| `pipeline.ExtractionResumeInvocationTest` | 2 of 6 red | a resume over committed chunks: the read of the occurrences already measured, over the span of the run's rows, before the count and the review-list read, and no fault read; a resume over one fault: the fault read's two lines first |
| `pipeline.RedundancyResolutionReportsItsProgressInvocationTest` | 1 of 3 red | stage 3's four statements around ADR-191's line; 4a's count; 4b's four reads in order and where the step makes them; no line of 4b's where nothing is signed; none of stage 3's or 4b's where the step is already recorded |
| `pipeline.StageFiveReportsItsProgressInvocationTest` | 6 of 8 red | every row of §3 from 5b to 6a, on a first invocation and, for 5e and the report, on one where the floor is a number |
| `pipeline.GenerationReportsItsProgressInvocationTest` | 2 of 5 red | 6b's six reads on a clean finish, and the same less `the standing faults` where the walk stops |

**How far each parked file was checked.** The six whole-job files were put in place of their namesakes and run against `main` at `ffe7066`: they compile, the 14 tests above fail and the other 12 pass, and every failure is an assertion that found no statement line where one is owed (stage 3's found ADR-191's line alone). A test stops at its first failing claim, so the claims after it in the same test were not reached. The five that name types part (b) adds, and `PoolOfTwo`, were compiled against stand-ins for those types kept outside the repository, and have never run.

**ADR-193's owed tests, and where each went.** Its *"5b and review-list tests"* are claims in `StageFiveReportsItsProgressInvocationTest` and `StageTwoReportsItsFaultResolutionInvocationTest`, which already capture the log of an invocation that runs 5b and writes the review list; `SeedCorpusComparisonInvocationTest` and `ReviewListThatCannotBeWrittenTest` are not changed. Its claim in `StageOneReportsItsLoopsInvocationTest`, withdrawn by ADR-200, is owed again for the two counts of §2. Its contract test in `corpus` stays withdrawn.

**Not pinned, and why:**

- **A progress line of a counted read of part (b) in a whole job.** Every fixture's run holds far fewer rows than one callback covers. The steps reaching the callback are pinned in each module's contract test, and the line and its cadence in `StatementProgressTest`.
- **That 4a's count is not issued where 4a is already recorded.** It is issued in a constructor, and when that runs is Spring Batch's.
- **Which class in `pipeline` writes a timed line.** The helper is the implementation's to name.
- **5f over two partitions.** The fixtures reach one (ADR-192, Tests).

## What this does not decide

- **Every statement and the loop of §8.**
- **Whether the second read by size should tick a counter for the survivors it passes over** (ADR-200 §7).
- **Shortening the build on a spinning disk**: [#427](https://github.com/algernon28/vespera/issues/427).
