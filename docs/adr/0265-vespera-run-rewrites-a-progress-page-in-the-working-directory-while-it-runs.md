# ADR-265 — `vespera run` rewrites a progress page in the working directory while it runs

> **Partly amended — see [ADR-266](0266-every-invocation-names-its-progress-page-first-and-opens-it-and-vespera-label-writes-it-too.md).** Of §2: `vespera label` writes the page too, when it starts and when it ends, with the answers it counted. Of §6: the README also says the page opens itself and that the command's first line names it. Every invocation that writes the page names it first and opens it once. The rest of this record stands.

- **Date**: 2026-10-15
- **Status**: accepted on 2026-10-15.
- **Keeps**: [ADR-130](0130-one-report-page-module-owns-the-skeleton-styling-escaping-and-tables.md), one module assembles every page; [ADR-198](0198-every-invocation-writes-an-account-built-from-an-allow-list-that-names-no-document.md), the invocation account and its allow-list of progress lines, which this page reads and does not copy; [ADR-223](0223-5fs-size-report-6a-and-6b-go-through-one-seed-partition-at-a-time-and-what-is-still-held-says-why.md) §3 and §6, a page moved over its place in one move; [ADR-226](0226-the-eight-rules-in-pipeline-live-in-extraction-embedding-synthesis-and-profile-and-no-stages-version-names-pipeline.md), no stage's version names `pipeline`; [ADR-261](0261-an-agent-may-read-and-write-a-working-directorys-profile-and-read-its-pages-of-paths-counts-and-reasons-and-nothing-else-in-it.md), whose list of pages an agent may read this page does not join.

## Context

An invocation over a large archive runs for hours, and what it has reached is told only by `vespera.log`: a line when a step starts and ends, and a stage's progress lines on the cadence of ADR-093 and ADR-192 §8. An operator who has the jar and not this repository, and no other program, has no view of an invocation but that log. The invocation account (ADR-198) holds each step and how it ended, and the progress lines whose whole text has a counter's shape, but it is written for a hosted model, outside every working directory, and it says nothing until the invocation ends.

What the steps have reached is already held in memory while the job runs: Spring Batch's `JobExecution` holds each step execution it has started, with its status, start and end times and its read, write and skip counts, and the progress counters `StageProgress` and `StatementProgress` log every line through one logger each, which `InvocationAccount` already listens to. None of it needs the database.

## Decision

### 1. The page

**`vespera run` writes `progress.html` in the working directory**, a fixed name held by the constant `ProgressPage.FILE_NAME`. It is one self-contained page through `ReportPage` (ADR-130), titled `Vespera progress`, and holds:

- a line saying whether the invocation is running or has ended: `This invocation is running. This page is rewritten every <S> s.` while it runs, and `This invocation has ended: <outcome>.` on the last write (§2), the outcome being the job's `BatchStatus` in lower case (`completed`, `failed`, `stopped`);
- `Written at <yyyy-MM-dd HH:mm:ss>`, the time of the write in the machine's zone, read off the job's `Clock`;
- `Elapsed: <H:MM:SS>`, from the job's start to the write;
- `Database size: <X.X> MB`, the size of `vespera.db` and of `vespera.db-wal` where it is there, added, in millions of bytes to one decimal place (`Locale.ROOT`), each read by a file system call and never by a query;
- a table whose header row is `Step`, `State`, `Read`, `Written`, `Skipped`, `Duration`, `Progress`, and one row for each of the job's fifteen steps in the order the job runs them, each step named as `StepNames` holds it.

A row's state is one of five, and the cell holds its words:

| State | When |
| --- | --- |
| `not started` | the job holds no execution of the step |
| `running` | its execution's status is `STARTING` or `STARTED`, or its thread is still inside it (below) |
| `stopped at a gate` | its execution completed and the step recorded that it ended at a shut gate (§3) |
| `finished` | its execution completed otherwise, a step skipped as already recorded among them |
| `failed` | any other status |

**A step's status turns terminal before its thread leaves it.** In the Spring Batch this build runs, a step's own `afterStep` listener, `SeedExtractionItemWriter.afterStep` among them, runs after the step execution is already `COMPLETED`, and may log a progress line from the step's thread. So a step is also shown `running` while its status is terminal and a progress line has been logged from its thread since that status was reached, until a later step has an execution or the last write (§2) is made; the state it is then shown in is the one its status gives.

`Read`, `Written` and `Skipped` are the execution's read, write and skip counts, as numbers; `Duration` is from the step's start to its end, or to the write while it runs. `Progress` is the last progress line logged while that step ran (§3), or empty. A `not started` row has its `Read`, `Written`, `Skipped`, `Duration` and `Progress` cells empty.

**A refusal is not a gate.** A step that ends because the embedding model or the generation model the profile names is not served here (ADR-202) fails, and is shown `failed`: the value is there, and what it names cannot be used. Only a value that is not there, or is unreadable, shuts a gate.

**The page names no path**: not the corpus root, the working directory, the seed folder or any file of the archive. It holds no title and no text of a document, no exception message and no run id. What it holds is step names, the five states, counts, durations, times and a size, and progress lines admitted by the allow-list of §3, so it may be copied out of the working directory to anyone.

While the invocation runs, the page's head carries `<meta http-equiv="refresh" content="<S>">`, so a browser that opened it reloads it, `<S>` being the interval of §2 in whole seconds, rounded up and at least 1. `ReportPage` gains the one method that writes that head:

```java
static String reloading(String title, String body, int seconds)
```

The last write is rendered with `ReportPage.render` and carries no reload.

### 2. When it is written

- **Once when the job starts**, from `beforeJob`, with every step `not started`, over whatever page an earlier invocation left.
- **Every `vespera.progress-interval` while it runs**, a duration that ships as `5s` in `application.yaml` and that an operator may set as any other `vespera.*` property. The writes are made by one daemon thread of the page's own, at a fixed delay after the last write ended, so no step thread renders or writes the page and a slow disk delays only the next write.
- **Once more when the job ends**, from `afterJob`, on the thread that ran the job, with the outcome of §1 and no reload. From then on a scheduled write that was due writes nothing: the page renders and writes under one lock, and the last write marks it ended under the same lock, so no write of the running page can land over the last one.

Each write renders the whole page into `progress.html.part` beside it and moves it over `progress.html` through `ledger`'s `ReplacingMove`, as every page is moved (ADR-223 §3 and §6), so a browser reloading it finds the old page or the new.

`vespera label` starts no job and writes no page; a page an earlier `vespera run` left is left as it is. An invocation that stops before the job starts, refused a working directory in use, a root it cannot walk or a `--db-dir` that is not the one opened, writes none either.

### 3. What it reads

**Only what the invocation holds in memory.** The writer reads the `JobExecution` the listener was handed: its start time, its status, and the step executions it holds, copied before they are read. It opens no connection and makes no query, so a write takes nothing from the pool of the steps.

**A step that ends at a shut gate says so**, in the job execution's context, as `RunMint` records a run there in `InvocationRuns` (ADR-157 §2): a new class of `pipeline`, `ShutGates`, over that context, with

```java
void record(String step)

boolean shutAt(String step)
```

and every place a step ends having done nothing for want of a value, its own or one an earlier step's gate required, calls `record` with the step's name. The gates are where they are; none moves and none changes what it decides.

**A progress line belongs to the step running on the thread that logged it.** The page listens to the loggers of `StageProgress` and `StatementProgress`, and keeps for each step the last line whose whole text has one of two shapes: the shape of a counter's line that `InvocationAccount` admits (ADR-198), read from the one pattern both classes name and not from a copy of it, and the shape of `StatementProgress`'s line over rows, `<label>: about <P>% of <N> rows` or `<label>: at least <P>% of <N> rows`, with the label of the same form. A line of any other text is not kept. The census's own counter, which logs through `corpus`, and a line no counter wrote are not on the page.

### 4. A write that fails

**A progress page never fails an invocation.** A write that throws, the render, the `.part` or the move, is caught on the thread that made it, and the next write is attempted on time. The first write that fails after one that did not, or after none, logs one warning:

```
the progress page could not be written: <exception class name>
```

and no further warning until a write has succeeded again, so a page that cannot be written leaves one line in `vespera.log` and not one every interval. A thread that cannot be started, or an interval of zero or less, is the same failure: the warning is logged, and the first and the last write are still made. Nothing the page does changes the job's status, its exit code or what any step records.

### 5. The module, and run ids

**`ProgressPage` is a class of `pipeline`**, a `JobExecutionListener` registered on the job in `VesperaJobConfiguration` beside `InvocationAccount`, and it is on record in `PipelineHoldsOnlyTheRulesOnRecordTest` as holding no rule, as `ShutGates` is: neither decides a verdict, a cache key, a cluster or text of the deliverable. `pipeline` is the only module that names a Spring Batch type, and the page reads `JobExecution` and `StepExecution`, so it could live nowhere else.

**No run id moves.** The page is not output of a stage, and every class this changes is in `pipeline`, which no stage's version names (ADR-226): `ProgressPage` and `ShutGates`, the gates' calls to `record`, `ReportPage`, `VesperaJobConfiguration`, and `StageProgress`, which holds the counter's pattern `InvocationAccount` and the page both read; and `application.yaml`'s new property, which no stage reads. `vespera.progress-interval` is no part of any run's configuration.

### 6. What the operator is told

The README names `progress.html` among the files of the working directory, with one sentence: open it in a browser while `vespera run` runs, it reloads itself every five seconds, and it names no path and no document, so it can be sent to anyone. It names `vespera.progress-interval` beside `vespera.account-dir`. The README's list is checked against the `FILE_NAME` constants of `src/main` (`docs/check-claims.mjs`), so it gains the name in the change that adds the constant.

## Alternatives refused

- **A `vespera progress` command, or a script that polls the database.** An operator would start a second program beside the invocation, and a poll of `vespera.db` takes a connection from the invocation's pool and reads tables its steps are writing. The page needs only what the job holds.
- **The page computed from the database.** The verdict counts by kind that `InvocationAccount` counts at the end cost a statement each over every run of the invocation; once every five seconds on the hot path they would slow the stage being watched.
- **Writes on the step threads**, at each progress line or at each step's start and end. A slow disk or a browser holding the file would slow the stage; the writer's thread takes that cost.
- **A page that marks itself stale with a script**, for a process killed before its last write. The page would carry code; the time it was written, beside the interval, says the same to whoever reads it.
- **A history kept for charts over time.** It grows with the invocation's length; the page holds the state now.
- **Paths of the working directory or the root on the page.** The operator knows them; the page is safe to send only while it holds none.

## Consequences

- An operator opens `progress.html` in a browser and sees each step's state and counts, the current step's last progress line, the elapsed time and the database's size, rewritten every five seconds and ending on a page that says how the invocation ended.
- Each invocation of `vespera run` writes the page about once every interval and twice more; each write reads the size of two files and moves one.
- The guard's list of pages an agent may read (ADR-261) does not name the page, so an agent may not read it.
- No schema, cache key, verdict kind or run id changes.

## Tests

| Method | What it holds |
| --- | --- |
| `pipeline.ProgressPageInvocationTest.whileAStepRunsThePageNamesItAndIsRewritten` | during stage 5a, the page reloads itself, says the invocation is running, names `seed-extraction` as `running` with the step's progress line, states the time written, the elapsed time and the database's size, and is written again with a later time |
| `pipeline.ProgressPageInvocationTest.theLastWriteSaysTheInvocationEndedAndStopsTheReload` | after an invocation that completed, the page carries no reload, says it ended `completed`, has the header row of §1, and no step `running` |
| `pipeline.ProgressPageInvocationTest.thePageNamesNoPathAndNoTextOfADocument` | a marked word in every name and text of the corpus and the seeds, the converter's message, the root, the seed folder and the working directory occur nowhere on the page |
| `pipeline.ProgressPageInvocationTest.aStepThatEndsAtAShutGateIsShownStoppedThere` | with the boilerplate floor unset, stage 4a's row is `stopped at a gate` and stage 3's `finished` |
| `pipeline.ProgressPageInvocationTest.aFailedStepIsShownFailedAndTheStepsAfterItNotStarted` | a profile that does not load fails the census: its row is `failed`, stage 1's `not started`, and the page says it ended `failed` |
| `pipeline.ProgressPageInvocationTest.aPageThatCannotBeWrittenFailsNothingAndIsWarnedOfOnce` | with a folder in the page's place, the run completes, one warning names the failure and no error does |
| `pipeline.ProgressPageInvocationTest.aSecondInvocationRewritesThePageAndMintsNoRun` | a second invocation over the same root writes the page again and leaves the `run` table's ids as the first left them |

`PipelineHoldsOnlyTheRulesOnRecordTest` holds `ProgressPage` and `ShutGates` on record as holding no rule.

## What this does not decide

- **The page's look beyond §1**: its styling, any bar or chart beside a count, and the words around the strings §1 fixes are the renderer's.
- **Whether an agent may read the page.** It holds what ADR-261's pages hold, and joining that list is a record of its own.
- **The census's progress on the page.** Its counter logs through `corpus` with a walk's id; the census's row shows its state and duration only.
