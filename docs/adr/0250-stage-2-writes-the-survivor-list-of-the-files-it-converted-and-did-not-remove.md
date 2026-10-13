# ADR-250 — Stage 2 writes the survivor list of the files it converted and did not remove, beside the review list

> **Partly amended — see [ADR-251](0251-one-statement-of-the-ledger-is-as-long-as-a-walk-the-survivor-lists-further-columns-are-undecided-and-its-listeners-scope-is-stated-in-the-code.md).** Of §3: the one statement of `ledger` whose rows can be as many as a walk's is the `ORDER BY o.path` of `Verdicts.eachExtractionFailure`; `Occurrences.pathsOf` and `Verdicts.survivingAmong` are not handed out by keyset either, ask by key, and are as long as what they are given. Of *Alternatives refused*: a column for the converter's status or the confidence grade is not refused; it is not decided. Of §4: ADR-175 §7 states no scope, and its case, no page for a step that failed having read nothing, is held by a guard in each listener; the scope and its reason are stated in `ExtractionJobConfiguration`.

- **Date**: 2026-10-13
- **Status**: accepted on 2026-10-13.
- **Extends**: [ADR-175](0175-a-file-that-fails-is-marked-and-skipped-and-only-a-sidecar-that-stays-gone-stops-stage-2.md) §7, which gave stage 2 one page at its end, the review list. Stage 2 now writes two. Nothing §7 says of the review list changes.
- **Applies, and does not amend**: [ADR-181](0181-a-stopped-stage-2-resumes-from-what-its-committed-chunks-recorded-and-redoes-only-the-rest.md) §5 (a report about stage 2 reads the ledger under the run); [ADR-220](0220-no-class-holds-every-occurrence-of-a-run-stage-2s-resume-the-census-stage-4b-and-stage-5e-read-a-page-at-a-time-or-ask-by-key-and-what-is-still-held-says-why.md) §6 (a page is written as its rows come, and its timed lines span its writing); [ADR-223](0223-5fs-size-report-6a-and-6b-go-through-one-seed-partition-at-a-time-and-what-is-still-held-says-why.md) §3 (written beside its place, moved there once whole); [ADR-242](0242-a-page-moved-over-its-place-is-moved-again-a-few-times-where-the-move-is-refused-as-access-denied.md) with ADR-244, ADR-245, ADR-246 and ADR-247 (the move that is attempted again); [ADR-193](0193-a-statement-sqlite-counts-reports-how-far-it-has-gone-and-one-it-cannot-count-says-how-long-it-took.md) §1 (a statement whose cost grows with a table is counted or timed in the change that adds it); [ADR-209](0209-the-ledger-is-four-records-behind-one-type-no-capability-module-names-spring-batch-and-a-tables-sql-is-its-owners.md) (a table is named in SQL only by its owner); [ADR-211](0211-no-class-holds-every-survivor-of-a-run-another-tables-rows-are-read-a-page-of-survivors-at-a-time.md) (another table's rows are read a page of survivors at a time); [ADR-216](0216-nothing-ships-that-no-decision-requires-and-nothing-calls-a-javadoc-states-its-own-contract-and-agents-md-carries-no-history.md) §1, of which §6 here is the check.
- **Rests on**: the plan measured below, on a synthetic ledger; the tests below.

## Context

Stage 2 ends with one page for a person to open, the review list (ADR-175 §7): the file occurrences it could not read. Nothing lists the ones it converted and kept. The survivors of stage 2 are a question the ledger answers, and the only way to ask it is SQL; a person who wants to know which files went on to stage 3 has no page that says.

What a page of them can show without opening a file of the archive is what two tables hold: `file_occurrence` (`ledger`) holds the path, and `extraction_metric` (`extraction`) holds one row for each response a document came back for, with its status, page count, character and word counts and confidence scores. `detected_format` (`corpus`) holds what stage 1 found the bytes to be.

Three facts of the code shape the decision:

- **A survivor of stage 2's run is not always a file stage 2 converted.** `Verdicts.survivors` answers the file occurrences carrying no blocking verdict under the run or a run upstream of it. Where the step completed, every one of them was converted. Where the step stopped partway, the file occurrences it never reached carry no verdict either, and are survivors that did not come through anything.
- **A commit that touches `extraction` or `corpus` moves run ids.** A stage's version is the last commit touching each module it names ([ADR-058](0058-a-stages-implementation-version-is-the-last-commit-touching-its-module.md)); stage 2 names `extraction` and `similarity`, stage 1 names `corpus`. No stage names `ledger` or `pipeline` ([ADR-222](0222-a-stages-version-names-pipeline-only-while-pipeline-holds-a-rule-that-shapes-its-output.md), ADR-216 §7).
- **An invocation that finds stage 2 finished writes its page again** (ADR-175 §7): the step runs in its place, reads nothing, completes, and its `afterStep` listeners run. A page added without moving stage 2's run id is therefore written for a finished stage 2 by the next invocation, with no file asked about again.

## Measured

The read of §3, on SQLite 3.49.1, against `schema.sql`, on a synthetic in-memory ledger: empty; then holding 60,000 file occurrences over two walks with no statistics; then the same after `ANALYZE`. The plan is the same in all three:

```
SEARCH file_occurrence USING COVERING INDEX sqlite_autoindex_file_occurrence_1 (walk_id=? AND path>?)
SCALAR SUBQUERY 1
SEARCH run USING INDEX sqlite_autoindex_run_1 (id=?)
CORRELATED SCALAR SUBQUERY 2
SEARCH verdict USING INDEX verdict_by_occurrence (occurrence_id=? AND kind=?)
```

No temp B-tree: `UNIQUE (walk_id, path)` (ADR-051) is an index in path order within a walk, so a page is a range of it that starts after the last path read. Nothing was timed; no size was measured, there being no temporary file to measure.

## Decision

### 1. Who is on the page

**The survivor list holds every file occurrence that is a survivor of stage 2's run and carries an `extraction_metric` row under that run.**

- *Survivor* is the ledger's one definition, `Verdicts`' anti-join over the run and the runs upstream of it (ADR-156). The page asks it and writes no second one.
- *Carries a metrics row* is what makes "came through" true of a stage that stopped partway: a file occurrence the step never reached is a survivor and has no row, and is not listed. Where the step completed, every survivor has a row and the condition removes none.
- **A file occurrence carrying `degenerate-output` is not on it.** It was converted, and it was removed: it does not go on to stage 3, and a survivor is what goes on. It is not on the review list either, which is the files stage 2 *could not read*. The page says so in its opening paragraph, so that the two pages' counts are not read as the whole of what entered.
- A file occurrence carrying `extraction-failed` is on the review list and not on this one. The two lists share no file.

### 2. The page

`extraction-survivors.html`, beside the database and beside `extraction-failures.html`. `SurvivorListReport.write(Writer out, long count, Consumer<Consumer<Row>> eachSurvivor)` renders it through `ReportPage` (ADR-130), as its rows come, building no string of the whole; a row is `Row(String path, Integer pages, int words)`.

- **Title and heading**: `Files stage 2 read and kept`.
- **With none**: the paragraph `No file came through. Stage 2 kept none of the files it was given.`, and no table.
- **With N**: the paragraph `Files that came through: N. Each was converted to text and not removed by this stage, so the stages after it read it. A file that could not be read is listed in extraction-failures.html. A file that was converted and then removed as degenerate output is on neither list.`, then one table.
- **Columns**: `Path`, `Pages`, `Words`. The path as the walk recorded it, beneath the corpus root. The page count, right-aligned, and `&mdash;` where the row holds none, which is every file the simple pipeline converted (`.docx`, `.txt`). The word count, right-aligned.
- **Order**: by path, ascending, as SQLite compares text: the review list's order.

Three columns, because they are what a reader going down thousands of rows uses: which file, how large, and whether text came out of it. They are also the three that an existing read hands over (§3). The status, the confidence grade, the character count and the detected format are not on the page: each needs a new read in `extraction` or `corpus`, and §6 says what that costs.

### 3. How it is read

The listener asks four things, three of which exist:

- **The count**: `Verdicts.eachPageOfSurvivors(run, page)` and, of each page, `ExtractionMetrics.recordedAmong(run, page)`, summing the sizes. Both exist. `Verdicts.survivorCount` is not the count: on a stopped step it counts the file occurrences not yet reached.
- **The rows**: a new read of `ledger`, `Verdicts.eachPageOfSurvivorsByPath(RunId runId, Consumer<List<OccurrenceAtPath>> page)`, hands the run's survivors a page of at most 1,000 at a time, in path order, each as a new record `OccurrenceAtPath(OccurrenceId occurrenceId, OccurrencePath path)`. It is `KeysetPages` over the statement measured above: the anti-join `survivors` runs, with the same runs in scope and the same blocking kinds, keyed and ordered by `path`, the first page starting after the empty string, which no path is. `walk_id` is compared as a column here, not as a value: the index wanted is the walk's.
- **Of each page**, `ExtractionMetrics.eachMeasuredFormOf(run, occurrences, row)`, which exists, hands the page count and the word count of each file occurrence that has a row. A file occurrence of the page it hands no row for is not written. The rows are written in the page's order, not in the order `extraction_metric` hands them.

At most a page of paths and a page of counts is held. A corpus of 10^5 survivors is about a hundred pages, each two statements, in each of two passes: a page of survivors and `recordedAmong` for the count, a page by path and `eachMeasuredFormOf` for the rows. Each pass also asks once which runs are in scope, a statement for each run in scope.

**Nothing sorts.** `EveryStatementThatSortsIsRecordedTest`'s counts do not move, and ADR-218's table gains no row. The review list's read by path stays the excepted statement it is, and `KeysetPages`' javadoc says so: it is the pager of the reads `ledger` pages, and `Verdicts.eachExtractionFailure`, one sorted statement, is the read it does not page.

`pipeline` joins the two modules' answers in memory, a page at a time, because no statement may name both tables (ADR-209).

### 4. When it is written, and what is said

`SurvivorListListener` is a second `StepExecutionListener` on the extraction step, step-scoped as `ReviewListListener` is and for the same reason (ADR-175 §7): built only when the step first calls it. Its constructors take `(Ledger, ExtractionMetrics, StageRuns, Path workingDirectory)` and the same with a last `ReplacingMove`; its `FILE_NAME` is the page's name.

- **Its place among the step's listeners is immediately before `ReviewListListener`.** `afterStep` runs in the reverse of that order, so the survivor list is written after the review list and after `ExtractionFaultRecorder`'s transaction, so the page is read from the verdicts as they stand at the end of the step; a faulted file occurrence carries no metrics row and is on the page in neither order. The row is written where a conversion, a failure blamed on the document, or a timeout short of the third in a row that came with a response is judged, in `OccurrenceJudge`, and its two set-asides, a failure the converter blamed on itself and the third timeout in a row, write none; `ExtractionItemWriter` writes verdicts only. The order is therefore the review list's kept, not a condition of this page being right. A listener of its own, and not a second page in `ReviewListListener`, so that neither page's failure costs the other and nothing ADR-175 §7 pins is rewritten.
- **A step that failed having read nothing writes no page and asks for no run**, as the review list does not: asking would call the converter that had just failed its check.
- **A step that completed, or failed after reading a file occurrence, writes it.** So does an invocation that finds the stage finished.
- **Beside its place, then moved**: written to `extraction-survivors.html.part` and moved over `extraction-survivors.html` by `ReplacingMove`, so a failure partway leaves the page before it, or none.
- **Timed** (ADR-193 §1, ADR-220 §6): `Stage 2 (extraction) is reading the survivors it converted` before the count, and `Stage 2 (extraction) read the survivors it converted in S s` after the move. A page that could not be written leaves the first line and not the second.
- **One line when written**, at INFO: `Stage 2 (extraction): N file(s) came through; their list is <absolute path>`.
- **A failure is said in one ERROR line and none is thrown**: `Stage 2 (extraction): the files that came through could not be counted, and their list could not be written to <absolute path>: <what was thrown>` where the count could not be made, and `Stage 2 (extraction): N file(s) came through, and their list could not be written to <absolute path>: <what was thrown>` where the file, the read of its rows or the move could not. The wording shares no phrase with the review list's three lines, which tests find by `they are listed in` and `the list of them could not be written`.

### 5. Names

`CONTEXT.md` gains **Survivor list**. The two classes carry it. The file's name is the operator's and is no term, as `extraction-failures.html` is not.

### 6. The ADR-216 check: no run id moves

**The change touches `pipeline` and `ledger`, which no stage names, and no other module.** That is why it is shaped as it is. `StageModules` gives each stage the modules its version names:

| Stage | Its version names | Moved by this change |
|---|---|---|
| 1, byte-level reduction | `corpus` | no |
| 2, extraction | `extraction`, `similarity` | no |
| 3, content census | `similarity`, `extraction` | no |
| 4, content redundancy | `similarity`, `extraction` | no |
| 5, seed measurement | `embedding`, `extraction` | no |
| 5, embedding and scoring | `embedding`, `extraction` | no |
| 6a, arrangement | `synthesis`, `extraction`, `embedding` | no |
| 6b, generation | `synthesis`, `extraction`, `embedding`, `profile` | no |

The build's script takes a module's version from the last commit touching its folder (ADR-058), so a change confined to `pipeline`, `ledger`, `src/test` and `docs` changes no line of `implementation-versions.properties` that a stage reads. `RunIdentityGoldenTest` holds each stage's list. No DDL, no schema version, no cache key, no recorded setting and no verdict kind changes.

**A column an existing read does not hand over would move them.** A read added to `ExtractionMetrics` for a status or a grade column moves stage 2 and every stage after it; one in `DetectedFormats` moves stage 1 and every stage after it. A new stage-2 run reads every file again: conversions and failures the converter blamed on the document come from the extraction cache, and every timeout, rejection and failure the converter blamed on itself is asked again (ADR-183).

## Alternatives refused

- **Survivors in id order, with `Occurrences.pathsOf`.** No new read in `ledger`. Refused because the page would be in the order the walk recorded files, which is the file system's and is not the review list's; a reader looking for one file in thousands of rows wants it where its name puts it.
- **A join of `file_occurrence`, `verdict` and `extraction_metric` in one statement, sorted by path.** One statement where this has four a thousand survivors. Refused: it names two modules' tables in one module (ADR-209), and it sorts.
- **`Verdicts.survivorCount` for the heading.** One statement. Refused: wrong on a stopped step, by as many file occurrences as the step never reached.
- **Both pages in `ReviewListListener`.** Refused under §4.
- **The `degenerate-output` files on this page under a flag.** Refused: the page would then hold files that do not go on, under a term that means they do.
- **A status and a confidence grade beside the three columns.** Refused for now under §6: each costs a new stage-2 run over the whole corpus, for a page that otherwise costs none.

## Consequences

- **A person has a list of what stage 2 kept**, and an invocation that finds stage 2 finished writes it without converting anything.
- **`extraction-survivors.html` joins the files written beside the database.** `docs/check-claims.mjs` reads every `FILE_NAME = "…"` in `src/main` against the README's *Where things live*, which carries the line `extraction-survivors.html        the files that were read and kept`.
- **Stage 2 says two more timed lines and one more line at its end**, on every invocation that reads a file occurrence or finds the stage finished.
- **The files converted and then removed as `degenerate-output` are on neither page.** The survivor list's paragraph says that such files exist; no page names them.
- **`ledger` gains a third paged statement over a run's survivors**, after the ones by id and by size, and the first keyed by a text column.
- **Eight classes render a page through `ReportPage`**, and its javadoc says eight: `ArrangementReport`, `ClusterSizeReport`, `ConfidenceDistributionReport`, `FormatMixReport`, `RelevanceLabellingReport`, `ReviewListReport`, `SeedCorpusComparisonReport` and `SurvivorListReport`.
- **`CONTEXT.md` gains *Survivor list*.**

## Tests

| Class | What it holds |
| --- | --- |
| `ledger.SurvivorsByPathTest`, on SQLite | the read hands a run's survivors by path where the order recorded disagrees with it; leaves out a file occurrence removed under the run as `extraction-failed`, one removed under it as `degenerate-output` (§1), one removed under a run upstream, and another walk's; keeps one removed only under a sibling run; hands the same file occurrences `survivors` does; compares paths byte by byte, a path in capitals coming before every one in small letters and a path before the path it begins, so that a read made without regard to case does not pass; and over 2,500 file occurrences issues three statements, each planned through `sqlite_autoindex_file_occurrence_1` after the last path read, none with a temp B-tree |
| `pipeline.SurvivorListReportTest` | the page is, character for character, the one `ReportPage` makes of the title, the paragraph and the rows of §2; a missing page count is a dash; a path's markup is escaped; with none there is no table and the rows are not asked for; a writer that refuses a write while rows are handed over is an `IOException` |
| `pipeline.SurvivorListListenerTest`, no database | the page lists the survivors that carry a row, in the order of their paths, and not one that carries none; no `.part` is left; the INFO line states the count and the place; the reads are made between the two timed lines; a step that ended in failure having read nothing writes no page and asks for no run; one that ended so after reading writes it; a move refused twice as access denied puts the page in place on the third attempt; a read that stops partway, a count that cannot be made, and a move refused on every attempt each leave the page before, say one ERROR line, throw nothing, and leave the line before the reading with no line after it |
| `pipeline.SurvivorListInvocationTest`, whole invocations against `LoopbackSidecar` | of three files, one answered 404, the survivor list names the other two in path order and the review list names the one; the line states 2; a second invocation that finds the stage finished writes the page again |
| `pipeline.StageTwoReportsItsFaultResolutionInvocationTest`, `pipeline.ExtractionResumeInvocationTest` | stage 2's timed statements are its count, its read for the review list, and after it its read for the survivor list |
| `PipelineHoldsOnlyTheRulesOnRecordTest` | `SurvivorListListener` and `SurvivorListReport` hold no rule |

## What this does not decide

- **Whether the files converted and then removed as `degenerate-output` get a list**, as a page of their own or otherwise. A page of them by path with the verdict's reason, read as the review list is, needs only `ledger` and `pipeline` and would move no run id.
- **Any column an existing read does not hand over**: the status, the confidence grade, the character count, the detected format. §6 says what each costs; the moment that cost is nothing is a change that already moves `extraction`'s version.
- A list of the survivors of any other stage. Stage 6b's `documents.csv` lists the survivors the deliverable arranges; stages 1, 3, 4 and 5 have none.
- Whether the review list and the survivor list link to each other. Each names the other's file in prose at most.
