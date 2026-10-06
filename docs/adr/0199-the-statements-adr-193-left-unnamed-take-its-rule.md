# ADR-199 — The statements ADR-193 left unnamed take its rule

- **Date**: 2026-10-05
- **Status**: accepted
- **Extends**: [ADR-193](0193-a-statement-sqlite-counts-reports-how-far-it-has-gone-and-one-it-cannot-count-says-how-long-it-took.md) §1, §4 and §7 to the two statements its §6 closes on as *"found while making it"*, to a third in the same reader that it did not name, and to the second survivor count [ADR-200](0200-stage-1-reads-survivors-by-size-and-holds-one-size-at-a-time.md) §3 added at stage 1 and left to this record. **It extends one exception ADR-193 drew**: §4.3 makes `<doing>` and `<did>` `reading` and `read` everywhere but `UnrecordedOccurrences.countOver`, which is `counting` and `counted`; the three survivor counts of §1 below take `counting` and `counted` too. Nothing else ADR-193 decided changes, and its text is not edited.
- **Rests on**: [ADR-040](0040-modules-are-capability-shaped-not-stage-shaped.md) and [ADR-041](0041-ledger-owns-identity-and-verdicts-capabilities-own-their-own-tables.md) (a capability module writes no line), [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) (which run ids move), [ADR-173](0173-every-column-that-references-a-file-occurrence-a-walk-or-a-run-carries-an-index.md) (the indexes `extraction_fault_by_run_id` and `extraction_metric_by_run_id`), [ADR-181](0181-a-stopped-stage-2-resumes-from-what-its-committed-chunks-recorded.md) §1 (the two reads this record counts), [ADR-192](0192-every-loop-in-every-stage-reports-its-progress-and-no-counter-floods-the-log.md), and ADR-200 §3 and §7 (`toSize` is called once, first, with the count; `corpus` has no statement interface).
- **Keeps**: [ADR-059](0059-schema-version-is-one-row-per-module-checked-and-refused-independently.md). No table, column or index changes.
- **Settles** [#429](https://github.com/algernon28/vespera/issues/429).

Terms are ADR-193's: a statement is *counted* where SQLite calls back during it and its total is cheap, *timed* where it is not, and a *step* is one instruction of SQLite's virtual machine.

**This record was drafted on 2026-10-05 and brought onto `main` at `ffe7066` on 2026-10-06, after ADR-200 had landed.** What ADR-200 changed under it is taken in here: stage 1 has no drain, and it counts its survivors twice.

## Context

ADR-193's survey covered the statements named on #411 and in ADR-192 §7, not every statement in `src/main`. Three statements turned up that no list named, each issued inside a stage's work with nothing said around it. #429 asks whether they take ADR-193's treatment and in which form, and whether a survey of all of `src/main` is owed.

- `Ledger.survivorCount`, which sizes a progress counter before its loop starts: `ByteLevelReductionTasklet` (stage 1's broken check), `RedundancySignatureItemWriter` (stage 4a, in its constructor), and `UnrecordedOccurrences.countOver` (stage 2, when nothing is recorded yet).
- `ExtractionFaults.occurrencesForRun`, in the reader stage 2 opens, before its first chunk (`ExtractionJobConfiguration`), in the discard of an unfinished run's faults (ADR-181 §1).

A fourth was found while this record was gated: **`ExtractionMetrics.occurrencesForRun`, in the same reader**, after the fault read and the drop of `shingle_by_hash`, reading every `extraction_metric` row the stopped run's committed chunks wrote (ADR-181 §1). ADR-193 §6 times its other call, inside the argument of `countOver` in `ExtractionItemProcessor`'s constructor; it did not name this one. It is on the same resume path as the fault read and reads far more rows, so this record decides it beside that read.

A fifth was added by ADR-200 after this record was drafted: **a second `Ledger.survivorCount` at stage 1, in `ContentIdentityResolution.resolve`**, whose answer is the total `toSize` hands the counter of the sizes read. It is issued inside `corpus`, where `pipeline` cannot put two lines around the statement itself, and ADR-200 §3 left its form to #429. Stage 1 counts twice because the broken check removes survivors between the two counts, so the first answer is not the second's.

## Measurements

Synthetic rows only, in memory, under `schema.sql` as shipped, on the SQLite of sqlite-jdbc 3.53.2.1, with a callback on every step; the method is `StatementStepsPerRowTest`'s, in `UncoveredStatementsStepsPerRowTest`. Rows were written for an earlier run first, so each read has rows it must not go through.

| Statement | Plan | Steps a row |
|---|---|---:|
| `SELECT occurrence_id FROM extraction_fault WHERE run_id = ?` | `SEARCH extraction_fault USING INDEX extraction_fault_by_run_id (run_id=?)`, no temp B-tree | 5 |
| `SELECT occurrence_id FROM extraction_metric WHERE run_id = ?` | `SEARCH extraction_metric USING INDEX extraction_metric_by_run_id (run_id=?)`, no temp B-tree | 5 |
| `SELECT MIN(rowid)` and `SELECT MAX(rowid)` on either table for the run | one descent each | the same few steps at 1,000 and at 3,000 rows of the run |
| `Ledger.survivorCount`'s `COUNT(*)` | goes through the walk's occurrences and looks each one up in `verdict` | grows with the walk's occurrences; no bound of one table in one run is its total |

The figure for each read is a whole number of steps a row at both sizes, to within a hundredth of a step a row, as every figure of ADR-193's was.

**What stands between a call of `ContentIdentityResolution.resolve` and its first callback**, measured on 2026-10-06 against `corpus` as it is at `ffe7066`, with no change to it: `ContentIdentityResolutionCountsItsSurvivorsFirstTest` runs the resolution over three synthetic survivors through a ledger that writes down each thing asked of it. The first thing asked is the survivor count; the next thing that happens is `toSize(3)`; the count is asked once in the whole resolution; and where the count throws, `toSize` is never called and nothing else is asked. Both of its tests pass. So the time from the call of `resolve` to `toSize` is the count's and nothing else's.

## Decision

### 1. Each statement's form

| Statement | Form | Why |
|---|---|---|
| `Ledger.survivorCount`, at stage 1's broken check and at stage 4a | **timed** | It is the count ADR-193 §1 means by *"no cheap total"*: an anti-join, so the thing a total would be is the thing being asked. Nothing is counted for it. |
| `Ledger.survivorCount` in `ContentIdentityResolution.resolve`, stage 1's second count (ADR-200 §3) | **timed**, by `pipeline`, from outside `corpus` (§2) | The same statement over the same run, and the same reason. It is a cost that grows with a table, so ADR-193 §1 puts it in a form; it is not left for later. |
| `Ledger.survivorCount` inside `UnrecordedOccurrences.countOver` | **already decided: ADR-193 §6's single timed span** over the whole `countOver(…)` expression at `ExtractionItemProcessor`'s constructor | The call is inside that span, and the operator sees one wait. **It gets no second pair of lines**, so no method of `Ledger` or of `UnrecordedOccurrences` is wrapped. |
| `ExtractionFaults.occurrencesForRun`, in stage 2's reader | **counted**, at 5 steps a row | It goes through one run's rows by an index on `run_id` alone with no temp B-tree, and its span is two descents: ADR-193 §1's two conditions both hold. |
| `ExtractionMetrics.occurrencesForRun`, in stage 2's reader | **counted**, at 5 steps a row | The same two conditions, on `extraction_metric_by_run_id`. |
| `ExtractionMetrics.occurrencesForRun` inside `countOver`'s argument at `ExtractionItemProcessor` | **already decided: ADR-193 §6's timed span**, as above | It keeps the signature that reports nothing. |

**Why counted and not timed for the fault read**, where it will almost never write a progress line: 5 steps a row put the first callback at 20,000 faults, and a run holds the files the converter refused, eight on the first real corpus (#265). The rule is ADR-193 §1's and it is not a judgement of how often a line is written. What counted buys over timed here is the one that matters: **a run that holds no fault says nothing**, which is every first invocation, where a timed statement writes both lines whenever it is issued (ADR-193 §4.3). A resume over a few faults still says what it is reading and how long it took, and over 20,000 or more would report progress as every other counted read does.

**The read of the measured occurrences is the one a resume waits on.** It goes through one row for every occurrence the stopped run's committed chunks converted, so a resume late in a large corpus reads hundreds of thousands, and passes 20,000 rows, and so its first progress line, early. A first invocation says nothing of it, as of the fault read.

### 2. The lines, and who writes them

- **A timed survivor count** writes ADR-193 §4.3's two lines, with `<doing>` and `<did>` `counting` and `counted`:
  - `Stage 1 (byte-level reduction) is counting the survivors the broken check goes through`, and `… counted the survivors the broken check goes through in <S> s`;
  - `Stage 1 (byte-level reduction) is counting the survivors whose sizes it reads`, and `… counted the survivors whose sizes it reads in <S> s`;
  - `Stage 4a (redundancy signatures) is counting the survivors it signs`, and `… counted the survivors it signs in <S> s`.
  Each of stage 1's two names the loop its count sizes, because stage 1 issues the same count twice, and two statements one after the other do not share a name. Neither takes the `<what>` of a row ADR-200 §7 struck from ADR-193 §6.
- **Stage 1's second count is timed from outside `corpus`, and `corpus` does not change.** `resolve` issues the count before it asks the ledger for anything else and hands the answer to `toSize`, which ADR-200 §3 fixes as called once and first (Measurements). So `pipeline` writes the line before **immediately before it calls `ContentIdentityResolution.resolve`**, in `ByteLevelReductionTasklet.resolveDuplicates`, and the line after **when `toSize` is called**, before it opens the counter of the sizes read. `<S>` is the time between the two. No `CorpusStatement` is written, `corpus` gets no statement interface and `HashingProgress` no new method: ADR-200 §7 stands. `TimedStatement` gains a form in two halves for this, since the statement is not `pipeline`'s to run: one call that writes the line before and hands back what the caller ends, and its ending, which writes the line after. Its one-call form, `of`, is unchanged for the other two counts.
- **The counted reads** write ADR-193 §4.1's lines, `<stage>` being `Stage 2 (extraction)`, and only where the run holds a row of the table read:
  - `Stage 2 (extraction) is reading the faults the stopped run recorded, over up to <N> rows`, and `… read the faults the stopped run recorded in <S> s`, with progress between as `Stage 2 (extraction, reading the faults the stopped run recorded): about <X>% of <N> rows`;
  - `Stage 2 (extraction) is reading the occurrences the stopped run measured, over up to <N> rows`, and `… read the occurrences the stopped run measured in <S> s`, with progress between as `Stage 2 (extraction, reading the occurrences the stopped run measured): about <X>% of <N> rows`;
  - progress on ADR-193 §5's cadence. Both pairs come before `Stage 2 (extraction) resumes run …`, the fault read's first.
  `<N>` is the span of the run's rowids in the table read, asked as two statements, as ADR-191 §2 asks it of `shingle`.
- **`pipeline` writes every line, through `StatementProgress` for the counted reads and a small `TimedStatement` for the three timed counts.** `extraction` writes none. It gains:
  - `ExtractionStatement`, with `FAULTED_OCCURRENCES` and `RECORDED_OCCURRENCES`, in that order, the order the reader issues them, 5 steps a row each, declared beside the SQL. The constants ADR-193 §7 gives the same enum for its part (b), `SURVIVORS` and `EXTRACTION_METRICS`, follow these two when that part is built, being issued after them;
  - `ExtractionStatementProgress`, on ADR-193 §7's shape, with a `NONE` that does nothing;
  - `ExtractionFaults.occurrencesForRun(RunId, ExtractionStatementProgress)` and `ExtractionMetrics.occurrencesForRun(RunId, ExtractionStatementProgress)`, each returning the `Set<OccurrenceId>` its one-argument form returns. Each asks its own span, as two statements, and runs its read through `ledger.StatementSteps`. The one-argument forms stay and call the new with `ExtractionStatementProgress.NONE`, so `ExtractionItemProcessor`'s call is untouched.
  `ExtractionJobConfiguration`'s reader calls the two-argument form of each, handing each a progress of its own that writes that read's lines.
- **The callbacks are ADR-193 §7's, in this order.** `statementStarting` is called once before each read, **always**: with the span where the run holds a row, and empty where it holds none. `stepsTaken` follows at each callback of SQLite's handler, and `statementEnded` once after the read, on every path but one that throws. A read that throws leaves no handler on its connection (ADR-193 §8). **`pipeline` writes neither line, and counts nothing, for an empty total**, as it does for `SHINGLE_ROWS` (ADR-193 §7); the silence of a first invocation is `pipeline`'s, not `extraction`'s.
- **A count not issued writes nothing.** Both of stage 1's are inside the step's work, which does not run where the step is already recorded; 4a's is in its writer's constructor, which runs at the step's first chunk (ADR-193 §6 says the same of the boilerplate read).
- **A count that throws writes no line after it**, as any timed statement: the step's own failure says so. For stage 1's second count that is `toSize` never being called (Measurements).

### 3. What a search of `src/main` found

A survey of **every** statement in `src/main` is not a standing duty and is not built here. ADR-193 §1 already puts a change that adds a statement whose cost grows with a table into one of the two forms *in the same change*; what is left is the statements already there. This record searched `src/main` for SQL text on 2026-10-05, against ADR-193 §6's table and ADR-192 §7, and on 2026-10-06 searched the same way what `ed41587`, `377f6e9`, `f41a279` and `27576f9` had added to it since. **It is a list of what those searches found, not a proof that nothing else is there**: the gate found `ExtractionMetrics.occurrencesForRun` in stage 2's reader, now decided in §1, and the reads of `WalkRecorder` below, which the first pass missed. The list **decides none of what it names**. Each is given a form by ADR-193's rule when a change takes it up:

- **The deletes of a step that is run again**, each going through every row of its run in a table: `Ledger.discardVerdicts` (stage 1's `BROKEN`, `OUT_OF_SCOPE` and `SUPERSEDED_BY`, 4b's `REDUNDANT_WITH`, 5e's `BELOW_THRESHOLD`), `Ledger.discardVerdictsAgainst` and `ExtractionFaults.discardForRun` (stage 2, the two statements beside the fault read decided here), `DetectedFormats.discardForRun` and `ContentIdentity.discardForRun` (stage 1), `DocumentFrequency.discardForRun` and `ConfidenceDistribution.discardForRun` (stage 3), `RedundancySignatures.discardForRun` (4a, over `minhash_signature` and `signature_band`, the largest tables a run writes) and `RedundancyResolution.discardForRun` (4b), the scoring, comparison, `DocumentClusters` and `Clusters` discards (stage 5 and 6a), and `ExtractionMetrics.discardForRun` and `UnusableSeeds.discardForRun` (seed extraction).
- **`Ledger.discardWalk`**, three deletes (`file_occurrence`, `walk_anomaly`, `walk`), from `WalkRecorder`; #368 measured it at 4.5 minutes before ADR-173's indexes.
- **`WalkRecorder.sawTheSameThing`**, stage 0's comparison of a finished walk with the one before: `Ledger.occurrencesForWalk` and `AnomalyLog.anomaliesForWalk`, each issued for both walks, every occurrence and anomaly row of each held in memory and compared.
- **`Ledger.occurrenceCount`**, a count over a walk's occurrences: at `WalkRecorder.reconcile` (stage 0's excludes-nothing check, ADR-056, with **`AnomalyLog.anomalyCount`** beside it over the walk's anomalies), and at `SeedExtractionItemProcessor` (stage 5a, sizing its counter over the seed walk), where it is the sibling of `survivorCount`.
- **The counts and groupings `InvocationAccount` makes when an invocation ends** (ADR-198): `GROUP BY` over `verdict`, `extraction_fault` and `cluster_fault`, and `COUNT(*)` over `cluster`, `synthesis_doc` and a walk's `file_occurrence`.
- **`RelevanceLabels.modelAnswers`** (ADR-197), a read of every `relevance_label_provenance` row of a seed set, ordered by path: twice in `AutoLabelling` and once in `RelevanceReportTasklet`. It is the sibling of `RelevanceLabels.forSeedSet`, which ADR-193 §6 times.

Statements inside a loop that reports (ADR-192), keyed lookups of one row, and the `INSERT`s a loop writes are not in the list: the first is the loop's counter, and the other two are not a cost that grows with a table. **The page statement of `Ledger.survivorsBySize`** (ADR-200 §1) is of the first kind, in each of stage 1's two reads by size, and ADR-200 §7 says why a page of it is not timed.

### 4. Which run ids move

Read off `StageModules`: `extraction` is in stage 2's implementation version and in every later stage's, and `pipeline` is in stages 3 to 6b, so **the run ids of stages 2 to 6b move**. **Stage 1's does not**: its implementation version is `corpus`'s alone, nothing in `corpus` changes, and all four of stage 1's new lines are written by `pipeline`, which stage 1's version does not name. `ledger` is in no stage's, and this change adds nothing to it.

**This adds no replay to the archive's next run, because the ids it moves are ones that run mints anew in any case, and not because none moves.** That run waits for both parts of ADR-193 (the operator's decision 6 on #411). Part (b) is not built at `ffe7066`, and since ADR-200 §7 took `corpus` out of it, it moves stages 2 to 6b: the same stages as this record. The draft of this section said part (b) *"moves stage 1 on as well"*; ADR-200 made that false, and stage 1's id was moved by ADR-200's own change instead. Were this record built after that run and not before it, stages 2 to 6b would be minted once more.

## Why this shape, and what the others cost

- **Timing the two reads too.** Simpler, with no new interface in `extraction`. Not taken: it writes four lines about reading nothing at the start of every stage 2, and they would be the reads of a run's rows in an index on `run_id` alone that were not counted, which is the exception ADR-193 §1 was written not to have.
- **Leaving `ExtractionMetrics.occurrencesForRun` to a later change**, as §3 leaves the rest. Not taken: it is in the reader this record already changes, on the resume path the fault read is on, and it is the longer of the two waits there.
- **Saying nothing from `extraction` where the run holds no row**, rather than an empty total. Not taken: ADR-193 §7 gives every module's interface the empty total for that case, and one module that skips the call would make `pipeline`'s silence depend on which module it is listening to.
- **Counting `survivorCount`.** There is no cheap total to count against, ADR-193 §1's own case.
- **Wrapping `survivorCount` inside `UnrecordedOccurrences.countOver` as well.** Two nested pairs of lines for one wait, and a method of `Ledger` that writes lines; ADR-193 §6 has already decided that use.
- **A `CorpusStatement` and a `CorpusStatementProgress` for stage 1's second count, which `HashingProgress` extends**, as ADR-193 §7 first had it. Not taken: that shape is for a statement `pipeline` cannot see the two ends of, and it can see both of this one, its own call and `toSize`. It would put back the enum and the interface ADR-200 §7 took out, for one statement, and change `corpus`, so stage 1's run id would move for two log lines `pipeline` can already write.
- **`pipeline` taking the count itself and handing the number to `resolve`.** Not taken: it changes `resolve`'s signature and ADR-200 §3's contract, in `corpus`, and moves stage 1's run id, to time a statement that is already first.
- **One count for both of stage 1's counters.** Not taken, and not this record's to take: the broken check removes survivors between the two, so the first answer is too large for the second counter. ADR-200 §3 chose two counts and says so.
- **Leaving stage 1's second count in §3's list.** Not taken: with the first timed, it would be the one survivor count in a stage that says nothing, in the stage that issues the same statement a moment earlier with two lines round it; and it costs `pipeline` two lines and `corpus` nothing.
- **A survey of all of `src/main` as part of this change.** §3 records what the searches found. Building the forms for the deletes and for stage 0's comparison is a decision about a larger set than #429 asks about, with a mechanism ADR-193 §4.4 does not give the deletes.

## Consequences

- **Production code changes in `extraction` and `pipeline`**, and in no other module: `corpus` is not touched. No schema version moves.
- **The run ids of stages 2 to 6b move, and stage 1's does not** (§4).
- **Lines change by addition**: four lines at stage 1 and two at stage 4a on every invocation that runs them, and at stage 2's start two on one that resumes over a fault and two on one that resumes over a committed chunk. A first invocation writes none new at stage 2.
- **The timed span of stage 1's second count rests on an order inside `corpus`**: the count first, then `toSize`. ADR-200 §3 states that `toSize` is called once and first with the count; that nothing else is asked of the ledger before it is this record's, and `ContentIdentityResolutionCountsItsSurvivorsFirstTest` holds `corpus` to it. A change that puts anything the ledger is asked for ahead of `toSize` fails that test, and is the change that would need the callback this record did not write.
- **ADR-200's *"How `Ledger.survivorCount` is announced where it is called from inside `corpus`"* is answered** (§1, §2).
- **ADR-193 §6's closing bullet is answered** for the two statements it names, and for the read beside them. It still says that the survey behind its table was of two lists; §3 above is a wider search, and names what that search left for later.
- **ADR-193's parked part (b) test depends on this record.** `StatementStepsPerRowAreTheDeclaredOnesTest`, under `docs/adr/0193/tests/b/`, treats every `ExtractionStatement` constant missing from its map of measured figures as timed, and asserts it declares no steps a row. Its map carries `FAULTED_OCCURRENCES` and `RECORDED_OCCURRENCES` with `UncoveredStatementsStepsPerRowTest`'s figures, nine pairs of `Map.of`'s ten; a further counted statement moves it to `Map.ofEntries`. It reads four enums since ADR-200, with no `CorpusStatement`, and this record adds none: the three timed counts are in no enum.
- **`AGENTS.md` changes its count and its range line only.** #429 is not a defect, so its paragraph of closed defects does not change.

## Tests

- `UncoveredStatementsStepsPerRowTest` pins each read's plan and steps a row, each bound's cost, that the survivor count grows with the walk, and that `ExtractionStatement` declares the measured figures.
- `ExtractionStatementProgressOrderTest` pins the callbacks of §2 for both reads: their order, the empty total, the steps reported from the connection the read runs on, and a read that throws.
- `ContentIdentityResolutionCountsItsSurvivorsFirstTest`, in `corpus`, pins what §2 times stage 1's second count by: the count is the first thing asked of the ledger and `toSize` the next thing that happens, the count is asked once, and a count that throws calls nothing. Green before anything is built, since `corpus` does not change.
- `UncoveredStatementsInvocationTest` pins the lines of a whole invocation: the three timed counts, each pair once and in order with no progress line between, stage 1's second pair after its first and before the first line of the sizes read; and both counted reads silent on a first invocation and said once each, in order, before the resume line, on a resume. It also pins §2's *"a count not issued writes nothing"* for stage 1: the resumed invocation, whose stage 1 is already recorded, writes neither pair of its counting lines. Stage 4a's half of that sentence is not pinned.

**Not pinned, and why:** what any of the three counts costs on the whole-archive database, which no test can see and ADR-200 did not measure either; and a progress line from either counted read in a whole invocation, whose rows here are far below one callback, the cadence being `StatementProgressTest`'s.
