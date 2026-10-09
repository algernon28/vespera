# ADR-204 — Every line of ADR-193's part (b) is written out, and its table is read again against the code

> **Settled since — see [ADR-205](0205-the-relevance-report-counts-the-answers-a-model-gave-as-it-looks-each-up.md).** The loop §7 found with no counter and left to #444, `RelevanceReportTasklet.modelAnswersInThisWalk`, has had one since that record: `Stage 5 (relevance report, model answers matched)`, over the answers a model gave for the seed set. ADR-205 changes nothing this record decided, and §7 and "the loop of §7" under *What this does not decide* stand as what this record knew when it was written.

> **Partly amended — see [ADR-211](0211-no-class-holds-every-survivor-of-a-run-another-tables-rows-are-read-a-page-of-survivors-at-a-time.md).** §3 and §4 change in the lines and constants ADR-211 changes in ADR-193. Of §3's lines: the timed lines of four drains of survivors are struck, stage 3's two, 5b's and 5f's; 5c's and 5d's say *counting* and *counted* where they said *reading* and *read*; stage 3's progress lines over the shingle rows are written under `grouping shingle rows` and say `at least`; and 5b's later reads of the corpus survivors' extraction metrics have lines of their own. Of §4's enums: `ExtractionStatement` loses `SURVIVORS`, `SimilarityStatement` loses `FREQUENCY_SURVIVORS` and declares 45 for `SHINGLE_ROWS` where it declared 7, and `EmbeddingStatement` loses `CORPUS_SURVIVORS` and gains `CORPUS_METRICS_AGAIN` straight after `CORPUS_METRICS`; `EXTRACTION_METRICS` and `CORPUS_METRICS` declare no steps a row, being told the rows read through a callback ADR-211 adds, `rowsRead`. The lines before and after the two reads of extraction metrics stand as §3 wrote them, and so does the silence of a counted statement started with an empty total.

> **Partly amended — see [ADR-220](0220-no-class-holds-every-occurrence-of-a-run-stage-2s-resume-the-census-stage-4b-and-stage-5e-read-a-page-at-a-time-or-ask-by-key-and-what-is-still-held-says-why.md).** Of §3's lines, stage 4b's two timed pairs for `the signature bands` and `the shingle document frequencies` are struck, and 5e's `the scores below the floor` says `is counting` and `counted` where it said `is reading` and `read`. §4's list of `SimilarityStatement`'s constants loses `SIGNATURE_BANDS` and `DOCUMENT_FREQUENCY`. §3's two lines for `the occurrences it could not read` keep their words and now span the count of those occurrences and the writing of the review list as well as the read in path order (ADR-220 §6).

- **Date**: 2026-10-06
- **Status**: accepted
- **Extends**: [ADR-193](0193-a-statement-sqlite-counts-reports-how-far-it-has-gone-and-one-it-cannot-count-says-how-long-it-took.md) §4, §6 and §7: every statement of its part (b) is given the `<stage>` its lines open with, the four enums are listed with their constants, and one statement §6 could not have named is given a form (§2).
- **Corrects**: one sentence of ADR-193's Tests table, about the removal of `shingle_by_hash` (§6 here), and nothing it decided.
- **Takes up**: one statement [ADR-199](0199-the-statements-adr-193-left-unnamed-take-its-rule.md) §3 listed and did not decide, `RelevanceLabels.modelAnswers` (§2 here). ADR-199 says of its list that each is given a form *"when a change takes it up"*, and part (b) is that change for this one. Nothing ADR-199 decided changes.
- **Rests on**: ADR-199 (the forms and the lines of the survivor counts and of stage 2's two reads on a resume, `TimedStatement`, and the first two constants of `ExtractionStatement`), [ADR-200](0200-stage-1-reads-survivors-by-size-and-holds-one-size-at-a-time.md) §7 (`corpus` has no statement of ADR-193's), [ADR-197](0197-a-local-model-answers-the-relevance-questions-and-the-floor-is-a-rule-over-the-labels-so-no-document-reaches-a-hosted-model.md) (which added the statements of §2), [ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md) (which run ids move), [ADR-154](0154-a-stage-reads-the-upstream-run-this-invocation-arrived-at-and-an-approval-opens-only-this-invocations-arrangement.md) §2 (6a writes its page on both branches) and [ADR-192](0192-every-loop-in-every-stage-reports-its-progress-and-no-counter-floods-the-log.md).
- **Keeps**: [ADR-059](0059-schema-version-is-one-row-per-module-checked-and-refused-independently.md). No table, column or index changes.
- **Is the record part (b) of [#411](https://github.com/algernon28/vespera/issues/411) is built from**, with ADR-193 and ADR-199.

Terms are ADR-193's: a statement is *counted* where SQLite calls back during it and its total is cheap, *timed* where it is not; a *step* is one instruction of SQLite's virtual machine; r is a counted statement's steps a row.

## Context

ADR-193 gave every statement named on #411 and in ADR-192 §7 one of two forms and landed in two parts. Part (a) is on `main`. Part (b) is every other row of its §6. Since ADR-193 was accepted:

1. **ADR-199 settled #429** and is built: the three survivor counts that size a counter, and the two reads stage 2's reader makes before a resume.
2. **`main` moved under §6's table**, which was read off the code at `7b25d04`. ADR-200 struck its two stage 1 rows, and ADR-197 added statements to the relevance report and an option of the `label` command, `vespera label --auto`, with statements of its own.
3. **Part (a)'s gate, on 2026-10-06, found one sentence the record says and no test held**: that the removal of `shingle_by_hash` takes the same steps at two sizes.

ADR-193 §6 gives each statement its `<what>`. It gives a `<stage>` only to the ones it shows in full, so two readers of it could write `Stage 5d (relevance scoring) is reading …` and `Stage 5d is reading …` and both be following the record. This record writes every line out.

## Measurements

Synthetic rows only, in memory, under `schema.sql` as shipped at `ffe7066`, on the SQLite of sqlite-jdbc 3.53.2.1, with a callback on every step, in `StatementStepsPerRowTest`.

| Statement | Steps |
|---|---:|
| `DROP INDEX shingle_by_hash` over 1,000 shingle rows | 309 in all |
| the same over 21,000 | 309 in all |

It took 300 when ADR-193 measured it. At either date it took the same number at both sizes, and neither figure is quoted anywhere but here.

## Decision

### 1. Which run ids part (b) moves

Part (b) changes `extraction`, `similarity`, `embedding`, `synthesis` and `pipeline`, and nothing in `corpus` or `ledger`. Read off `StageModules`: `extraction` and `similarity` are in stage 2's implementation version, and `pipeline` is in that of stages 3 to 6b, so **part (b) moves the run ids of stages 2 to 6b, and stage 1's does not move**. Those are the stages ADR-199's change moved. The archive's next run waits for part (b) (the operator's sixth decision on #411), so it mints them once.

### 2. Two statements ADR-197 added

| Statement, where it is issued | Form | Why |
|---|---|---|
| `RelevanceLabels.modelAnswers`, in `RelevanceReportTasklet` | **timed**, by `pipeline`, around the call | It reads the answers a model gave for one seed set: no run, so no span, exactly as `RelevanceLabels.forSeedSet` beside it, which ADR-193 §6 times. It is inside a stage's step. |
| `RelevanceLabels.forSeedSet` and `modelAnswers`, in `AutoLabelling` | **no line** | `AutoLabelling` is built for `vespera label --auto` and reached from `VesperaCommand`'s `label` command alone. That is not a stage, and ADR-193 §6 already says of `LabelIngestion`'s statements under the same command that nothing outside the job's steps is announced. |

### 3. Every line of part (b), written out

**The table below is the definition of each `<stage>`.** For stages 1 to 5f and the report it is the name the stage's own starting and finishing lines already use. For 6a and 6b it is not: their own lines say *"the arrangement step"* and *"the generation step"*, and `Stage 6a (arrangement)` and `Stage 6b (generation)` are the new wording ADR-193 §6 gave them, used by their statement lines and their counters and not by those lines. A timed statement writes ADR-193 §4.3's two lines, a counted one §4.1's two and its progress lines between. `<S>` is seconds to one decimal place, and `<N>` is grouped in threes.

```
<stage> is reading <what>                              <stage> is counting <what>
<stage> read <what> in <S> s                           <stage> counted <what> in <S> s

<stage> is reading <what>, over up to <N> rows
<label>: about <X>% of <N> rows
<stage> read <what> in <S> s
```

In the order each step issues them. The rows marked ADR-199 are on `main` already and are here only to show where they fall:

| `<stage>` | Form | `<what>`, and for a counted statement its `<label>` |
|---|---|---|
| `Stage 1 (byte-level reduction)` | timed counts, two | ADR-199 §2 |
| `Stage 2 (extraction)` | counted, two | ADR-199 §2 |
| `Stage 2 (extraction)` | timed count | `the survivors still to read` |
| `Stage 2 (extraction)` | timed | `the occurrences it could not read` |
| `Stage 3 (content census)` | timed | `stage 2's survivors for the shingle frequencies` |
| `Stage 3 (content census)` | counted, 7 | ADR-191's lines, on `main` since part (a) |
| `Stage 3 (content census)` | timed | `stage 2's survivors for the confidence distribution` |
| `Stage 3 (content census)` | counted, 7 | `the extraction metrics`; `Stage 3 (content census, reading extraction metrics)` |
| `Stage 4a (redundancy signatures)` | timed count | ADR-199 §2 |
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

- **The five progress labels in the table are the definition, and no rule derives them from the `<what>`.** Three are the `<what>` less its article: `reading extraction metrics`, `reading signed occurrences`, `reading unusable seeds`. 5b's two shorten theirs, to `reading corpus metrics` and `reading seed metrics`, as ADR-193 §6 wrote them. ADR-199's two labels keep the whole `<what>`, article and all, and stand as that record wrote them.
- **The report's read of the scores is ended where it finds none.** `RelevanceDistribution.measure` answers a run with no score by throwing `NoSuchElementException`, which is the step's gate and not a statement that failed: the read went through and found nothing. So the report writes `Stage 5 (relevance report) read the scores in <S> s` and then its line that it is gated, and makes no other read. Any other exception from that read writes no line after it, as for every timed statement.
- **`RelevanceFloor` is asked by two steps**, 5e and the report, so the read it makes carries the `<stage>` of the step that asked.
- **A statement not issued writes nothing** (ADR-193 §4.3). A step already recorded under its run issues none of the statements inside its work. 6a's two reads are outside that work and are issued on both branches (ADR-154 §2).

### 4. The callbacks

ADR-193 §7 stands, less `corpus` (ADR-200). The four enums, with r where a constant is counted. **The constants are in the order their statements are issued, with one exception**: `SHINGLE_HASH_INDEX_BUILD` stands first in `SimilarityStatement`, and stage 4b issues its build after stage 3 has issued `FREQUENCY_SURVIVORS` and `SHINGLE_ROWS`. It was the first constant written, with part (a), and ADR-193 §7 lists it first while saying of every enum that its constants are *"in the order the statements are issued"*; that sentence is not true of this one constant, and the constant is not moved for it.

| Enum | Constants |
|---|---|
| `extraction.ExtractionStatement` | ADR-199's `FAULTED_OCCURRENCES` (5) and `RECORDED_OCCURRENCES` (5), then `SURVIVORS`, `EXTRACTION_METRICS` (7) |
| `similarity.SimilarityStatement` | `SHINGLE_HASH_INDEX_BUILD` (11), `FREQUENCY_SURVIVORS`, `SHINGLE_ROWS` (7), `SIGNED_OCCURRENCES` (5), `SIGNATURE_BANDS`, `NEAR_DUPLICATE_METRICS`, `DOCUMENT_FREQUENCY` |
| `embedding.EmbeddingStatement` | `CORPUS_SURVIVORS`, `SEED_OCCURRENCES`, `UNUSABLE_SEEDS` (5), `CORPUS_METRICS` (12), `SEED_METRICS` (12) |
| `synthesis.SynthesisStatement` | `WRITTEN`, `STANDING_FAULTS` |

- **`ExtractionStatement` gains a timed constant**, so `stepsPerRow()` answers empty for `SURVIVORS`, as ADR-193 §7 has it for every enum; ADR-199 built the enum with counted constants alone.
- **Where each interface reaches its method.** `similarity.ResolutionProgress` and `synthesis.GenerationProgress` extend their module's `<Module>StatementProgress`, as `FrequencyProgress` does since part (a). `ConfidenceDistribution.measure` gains an overload taking an `ExtractionStatementProgress`, and `SeedCorpusComparison.measure` one taking an `EmbeddingStatementProgress`. Every old signature calls the new with a progress that does nothing.
- **The order, for every statement.** `statementStarting` once before it; `stepsTaken` at each callback of SQLite's handler, for a counted one; `statementEnded` once after it, on every path but one that throws. A statement that is not issued makes no call.
- **The total.** A timed statement is started with an empty total. A counted one is started with its span, and with an empty total where the run holds no row, for which `pipeline` writes neither line and counts nothing (ADR-199 §2, ADR-193 §7).
- **The statements `pipeline` issues or calls itself need no callback**: stage 2's count and its review-list read, and every row of §3 from 5c on but 6b's two inside `ClusterGeneration.write`. `pipeline` writes them through ADR-199's `TimedStatement`.

### 5. ADR-193 §6, read again against `main` at `ffe7066`

Every row of §6 was checked against the code. **Each row not named below stands as written**, at the class and method it names. The record quotes two line numbers, in §9, `ExtractionItemProcessor.java` lines 157 and 158, and the expression is still there.

- **The two stage 1 rows** are struck by ADR-200 §7. ADR-199 gives stage 1 two timed counts, which are not those rows come back: they time a count, where the rows timed a drain.
- **The report issues `RelevanceLabels.forSeedSet` twice where the floor is a number**, once for the answers it matches and once through `RelevanceFloor`, and `the embedder identities` twice, for the two methods §6 lists in one row. §3 writes each out.
- **`RelevanceLabels.modelAnswers`** is new in the report since ADR-197 and is given a form in §2.
- **`AutoLabelling`** is new since ADR-197 and is outside the job's steps (§2).

### 6. One sentence of ADR-193's Tests

ADR-193's Tests table says `StatementStepsPerRowTest` pins *"the drop's steps the same at two sizes and fewer than one callback"*. The test asserted the second, and of the first only that the larger size took fewer than a thousand steps more. **The measurement supports the sentence** (Measurements), so the test is changed and the sentence stands: the removal's steps at 21,000 rows equal its steps at 1,000.

### 7. What this record found and does not decide

ADR-199 §3's list stands, less `RelevanceLabels.modelAnswers`, which §2 takes up. One thing more was found, and it is **a loop, not a statement**: `RelevanceReportTasklet.modelAnswersInThisWalk` looks each answer a model gave up in the ledger, one statement an answer, with no counter. Its sibling over every answer has one (`Stage 5 (relevance report, answers matched)`). That is ADR-192's rule, not this record's, and it is not decided here: it is left to a ticket of its own, [#444](https://github.com/algernon28/vespera/issues/444), *"The relevance report looks up each answer a model gave with no counter"*.

## Why this shape, and what the others cost

- **An amendment written into ADR-193, or into ADR-199.** `docs/adr/README.md` reopens a record only by a later record.
- **Leaving `<stage>` to whoever builds each line.** Then the whole-job tests are where the wording is decided, one stage at a time, and no record says what it is.
- **A line for `AutoLabelling`'s reads.** `vespera label` is not a stage, and ADR-193 §6 has already drawn that edge.
- **Leaving `modelAnswers` in ADR-199 §3's list.** It would be the one read of the report with nothing said around it, beside five that say what they are doing, in the step this part already changes.

## Consequences

- **Production code changes in five modules** (§1). No schema version moves.
- **The run ids of stages 2 to 6b move, and stage 1's does not** (§1).
- **Lines change by addition**: two for every row of §3 not marked ADR-199, wherever its statement is issued, and progress lines between for a counted one that reaches a callback.
- **`AGENTS.md` changes its count and its range line only.** #411 is not a defect.

## Tests

All in `src/test`. Those that name a type part (b) adds were parked under `docs/adr/0193/tests/b/` until it was built, as ADR-193's Tests say a test is.

| Test | Pins |
|---|---|
| `pipeline.StatementStepsPerRowTest`, one assertion tightened | the removal of `shingle_by_hash` taking the same steps at two sizes (§6) |
| `pipeline.StatementStepsPerRowAreTheDeclaredOnesTest` | every declared r equal to the measured one, and every timed constant declaring none, over the four enums of §4 |
| `extraction.ConfidenceDistributionStatementProgressOrderTest` | `ConfidenceDistribution.measure`'s drain started with no total and ended, then its read started with the span of the run's rows, or with an empty total over a run with none; the read's steps between its start and its end over many rows, on a pool of two outside a transaction, so on the connection the handler is on; a read that throws started and never ended, with no handler left: §4's *"on every path but one that throws"*, held for this one statement of part (b) |
| `similarity.SimilarityStatementProgressOrderTest` | `DocumentFrequency.measure`'s two statements in order before its loop is announced; `RedundancyResolution.resolve`'s four among its loops, and none after the first where nothing is signed; the signed-occurrences read reporting steps on a pool of two; the build's two callbacks over a few rows; a read of the signed occurrences that throws started and never ended, with no handler left, and a read of the signature bands that throws started and never ended |
| `embedding.EmbeddingStatementProgressOrderTest` | `SeedCorpusComparison.measure`'s five statements in order; the unusable-seeds read started with an empty total where none is recorded; each counted read reporting steps on a pool of two; a read of the unusable seeds that throws started and never ended, with no handler left, and a drain of the seed walk's occurrences that throws started and never ended |
| `synthesis.SynthesisStatementProgressOrderTest` | `ClusterGeneration.write` starting and ending `WRITTEN` before the walk is announced and `STANDING_FAULTS` after the last cluster |
| `synthesis.SynthesisStatementThatThrowsTest`, on a database file of its own with no Spring context | each of those two statements started and never ended where it throws |
| `pipeline.StageTwoReportsItsFaultResolutionInvocationTest` | stage 2 on a first invocation: the count of the survivors still to read and the review-list read, once each, in order, and no line of either read of a resume |
| `pipeline.ExtractionResumeInvocationTest` | stage 2's whole sequence on a resume: ADR-199's read of the occurrences the stopped run measured, over the span of the run's rows, then the count and the review-list read; and, over one fault, ADR-199's fault read first |
| `pipeline.RedundancyResolutionReportsItsProgressInvocationTest` | stage 3's four statements around ADR-191's line; 4b's four reads in order and where the step makes them; no line of 4b's where nothing is signed; none of stage 3's or 4b's where the step is already recorded; and, 4b done again under its run with 21,000 synthetic signatures among the signed, the one verdict the first invocation wrote under that run written again and no other |
| `pipeline.StageFiveReportsItsProgressInvocationTest` | the rows of §3 from 5b to 6a, on a first invocation and, for 5e and the report, on one where the floor is a number: 5b's with no unusable seed recorded, so its lines for that read absent, and 5f's over one partition |
| the same class, `eachCountedReadSaysHowFarItHasGoneUnderItsOwnLabel`: stage 3, 4b and 5b done again under their runs over 15,000 to 21,000 synthetic rows | each of §3's five progress labels, word for word, on a line `about X% of N rows` whose N is the span of the run's rows; 5b's two lines for its read of the unusable seeds, with its total, between the drains and the reads of the metrics; and 5b's other two totals |
| the same class, `theReportSaysItReadTheScoresWhereItFoundNone`: a corpus whose one file stage 2 removes | the report's line after its read of the scores written where the read finds none, before the line that the step is gated, and no other read made (§3) |
| the same class, `theReportDoesNotSayItReadTheScoresWhereTheReadFails`: the table of scores renamed away between two invocations | the report's line before its read of the scores written and no line after it, where the read fails for a reason other than finding none (§3) |
| the same class, `clusteringOverTwoPartitionsReadsTheMembersOfBothBeforeItGroupsEither`: 5f done again under its run after one document's score row is rewritten to name a second seed | 5f over two partitions: `the members of partition 1 of 2` and `2 of 2` both read before either partition's `the cluster sizes of partition <P> of 2`, each partition's sizes read before it is counted as done; and, in the test of a floor that removes every document, no line about the group sizes of a partition that kept no member |
| `pipeline.GenerationReportsItsProgressInvocationTest` | 6b's six reads on a clean finish, and the same less `the standing faults` where the walk stops |

`pipeline.StatementLines` and `PoolOfTwo` are helpers: the first reads a stage's statement lines out of what an invocation logged, the second is a pool of two connections over a file of its own under `schema.sql`.

**ADR-193's owed tests, and where each went.** Its *"5b and review-list tests"* are claims in `StageFiveReportsItsProgressInvocationTest` and `StageTwoReportsItsFaultResolutionInvocationTest`, which already capture the log of an invocation that runs 5b and writes the review list; `SeedCorpusComparisonInvocationTest` and `ReviewListThatCannotBeWrittenTest` are not changed. Its claim in `StageOneReportsItsLoopsInvocationTest` and its contract test in `corpus`, both withdrawn by ADR-200, stay withdrawn: stage 1's lines are ADR-199's, pinned by `UncoveredStatementsInvocationTest` and `ContentIdentityResolutionCountsItsSurvivorsFirstTest`. The callbacks of stage 2's two reads on a resume are ADR-199's `ExtractionStatementProgressOrderTest`.

**Not pinned, and why** (this list and the rows above were brought up to date with [#411](https://github.com/algernon28/vespera/issues/411), when tests were added for a statement that throws in each module, for 5f over two partitions, for the report's read of the scores that fails, and for 4b's verdicts over synthetic signatures):

- **How many progress lines a counted read of part (b) writes, and at which shares.** Each label is pinned on one line, over rows just past one callback. The cadence over a small and a very large total is `StatementProgressTest`'s, which holds it under a label of its own and not under these: the label is an argument of `StatementProgress` and the cadence does not read it, and holding the cadence under each label in a whole job would take hundreds of thousands of synthetic rows for each read.
- **Every statement of part (b) that throws.** One counted statement of each of `extraction`, `similarity` and `embedding` is made to throw, one timed statement of `similarity` and one of `embedding`, and both of `synthesis`'s, which are timed, that module having no counted one. No timed statement of `extraction` is. The others are not: `DocumentFrequency.measure`'s drain, the read of the near-duplicates' metrics and of the shingle document frequencies in `similarity`, the drain of the corpus survivors and the two reads of the metrics in `embedding`, and `ConfidenceDistribution.measure`'s drain. `StatementStepsTest` holds the helper every counted read runs through to clearing its handler after a throw.
- **Which class in `pipeline` writes a line of a statement a capability module announces.** That is the implementation's to name.
- **5f over two partitions that the scores themselves make.** The two partitions are made by rewriting one score row between two invocations, since both whole-job fixtures answer every document alike; 5f's reads and their lines are held over them, and that a real corpus scored against two seeds divides so is not.

## What this does not decide

- **ADR-199 §3's list, and the loop of §7.**
- **Whether the second read by size should tick a counter for the survivors it passes over** (ADR-200 §7).
- **Shortening the build on a spinning disk**: [#427](https://github.com/algernon28/vespera/issues/427).
