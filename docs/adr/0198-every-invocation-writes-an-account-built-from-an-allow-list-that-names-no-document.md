# ADR-198 — Every invocation writes an account built from an allow-list, and the account names no document

> **Partly amended — see [ADR-203](0203-the-invocation-accounts-folder-is-judged-by-every-reading-of-its-name-and-a-name-that-cannot-be-followed-is-refused.md).** These passages below are no longer the whole of the decision, and that record says what replaces each: §1's sentence on when the account is written ("is not at or below the working directory this invocation uses, and has no folder at or above it that holds `vespera.db` or `vespera.lock`") and its two warnings, to which a third is added; §6's "with the file it tried to create"; and §7's "The only lines added are the warnings of §1 and §6". Everything else in this record stands.

- **Date**: 2026-10-05
- **Status**: accepted
- **Extends**: [ADR-093](0093-logging-is-explicit-and-process-scoped-console-plus-rolling-file-per-item-and-per-step-at-info.md). Its console and rolling file are untouched. A third output is added beside them, and it is not a Logback appender (§1).
- **Rests on**: [ADR-192](0192-every-loop-in-every-stage-reports-its-progress-and-no-counter-floods-the-log.md), whose progress lines the account carries (§4), and [ADR-076](0076-exception-types-are-named-for-the-fault-and-end-in-exception.md), which is what makes an exception's type alone say what failed (§5), and [ADR-196](0196-no-agent-reads-the-operators-documents-and-an-allow-list-hook-that-fails-closed-refuses-every-other-path.md), whose hook refuses everything inside a working directory and is given no exception for a file name (§1).
- **Makes a narrow exception to**: [ADR-041](0041-ledger-owns-identity-and-verdicts-capabilities-own-their-own-tables.md) (§3).
- **Keeps**: every line an operator reads today, word for word. No existing log call is edited, and `OperatorTextTest` stays as it is (§7).
- **Settles** [#424](https://github.com/algernon28/vespera/issues/424), with the operator's decisions of 2026-10-05 recorded on it.

## Context

Claude is not to see anything from the operator's documents: a path, a file name, a title, an opening or a word of generated prose. Today the only way to diagnose a run is to read `vespera.log` and the reports in the working directory, and all of those carry some. #424 asks for one artefact of an invocation that a hosted model may read, and asks four questions. The answers are below; each says what it weighed.

**Two words in the issue are rejected synonyms here** (`CONTEXT.md`). "Run" is `_Avoid_` for an *invocation*, and "summary" is `_Avoid_` for a *synthesis doc*, so every name this record gives is "invocation account": the class `InvocationAccount`, the files `invocation-account-<start>.txt`. The issue's own title keeps its words, because it is the operator's.

### What the existing lines carry

The code has 71 log calls, and the ones that name a path, a file or a document, read off it, are these:

| Where | What it names |
|---|---|
| `VesperaCommand.Run` | `Walking {corpusRoot}` |
| `CensusTasklet` | the root, `Census merged the profile at {file}`, the seed folder, `Census recorded {what} at {walkRoot}` |
| `ByteLevelReductionTasklet` | the format mix report's file |
| `ContentCensusTasklet` | the confidence report's file |
| `ExtractionItemProcessor` | `stop.recordedPaths()`, and a timeout's own message |
| `ReviewListListener` | the failures page's absolute path |
| `SeedExtractionItemProcessor` | the seed's file and the open's message |
| `RedundancyJobConfiguration`, `SeedExtractionItemWriter` | `StepFailure.named`, which is the failure's own message |
| `TextParts` | a temporary file's path |

Exception messages are the same shape: `the format mix report could not be written to {file}`, `could not read the profile at {file}`, and `MisshapenProfileException`, which names the file and the key. The database holds more: `extraction_fault.detail` is the converter's own message and names the document, `verdict.reason` and `cluster_fault.detail` are free text, `cluster.label` is a document's title and `synthesis_doc` is model-written prose. **The log is therefore not a thing to filter**: its lines were written for an operator, who is meant to see the path, and a line added next month by someone who does not know about the filter is a leak nothing notices.

## Decision

### 1. Where it is written: a folder outside every working directory, one file per invocation that starts the job

Each `vespera run` invocation that starts the job writes `invocation-account-<UTC start, yyyyMMdd'T'HHmmss'Z'>.txt` in **the folder the property `vespera.account-dir` names**. A second invocation in the same second takes `-2`, then `-3`. One file per invocation, so an operator hands over exactly one, and no rotation applies to it. Vespera never deletes one.

**The folder is never a working directory, and never inside one.** ADR-196's hook refuses every path at or below a folder holding `vespera.db` or `vespera.lock`, and it is given no exception keyed on a file name, so an account written beside `vespera.log` would be unreadable to the agent it is for. The property ships unset. The account is written only if the folder is set, is not at or below the working directory this invocation uses, and has no folder at or above it that holds `vespera.db` or `vespera.lock`. Otherwise **no account is written**, and the invocation writes one warning to `vespera.log` saying why: `no invocation account was written: vespera.account-dir is not set` or `... lies inside a working directory`. The operator then adds the folder to the hook's allow list (`.claude/allowed-paths.local.txt`), once, so that the agent may read it.

It is **not** a second Logback appender with a filter, which is the issue's other option:

- A filter selects among lines that already exist, and their authors wrote the arguments for an operator. The allow-list would be a list of logger names and message shapes that are safe today.
- Logback's configuration is read at start-up, so the appender would have to be told the folder twice and the file per invocation would need a discriminator the framework does not have.

Instead one class, `InvocationAccount` in `pipeline`, writes the file, and **its methods take typed values and no free text** (§2).

**An invocation refused before the job starts writes no account**: a second invocation on a locked working directory, a profile that does not load at start-up, a missing root. Each says what is wrong on its own, and there is no job to account for.

### 2. How "safe" is guaranteed: an allow-list at the point of writing, and a test that marks the fixture

**The account is composed from values of types that cannot hold a document's words.**

| Line | Built from |
|---|---|
| `invocation started`, `invocation ended` | an instant, the job's status, a duration |
| `step` | a name from `StepNames` (any other is written as `unknown`), its status, start instant, duration, and Spring Batch's read, write, skip and commit counts, which are `long`s |
| `run` | a stage name from `StageModules` and a run id, which is a hash of the implementation version, the configuration, the walk and the upstream runs (ADR-048) |
| `counts` | `long`s, grouped by a `VerdictKind`, a `FailureCategory` or a `ClusterFaultKind`: the closed enumerations, written by name, and a stored value that is none of them is written as `other` |
| `failure` | a step name and the fully qualified class names of an exception and its causes (§5) |
| `progress` | §4 |

No method takes a message, a path or a `Throwable` to read a message from. A fixture that holds a marker in a name, in a text, in a converter's message or in a title can reach the file only through one of these types, and none of them carries it. **The counts are queries that select a count and a closed column, never `detail`, `reason`, `label`, `title` or a path.** Occurrences are counted, not named.

**What the guarantee rests on, and the tests that hold it**: an invocation over a fixture whose directory names, file names and texts all carry one marker string, with a converter fault, groups arranged from marked names and a step that fails on a message naming the marker, then a claim that the marker, the roots and the working directory occur nowhere in any account the invocation wrote.

### 3. What it holds, per invocation, and a narrow exception to ADR-041

The file is one line per fact, in the form `<UTC instant> <kind> <key>=<value> ...`: the invocation's start and end with its status and duration; each step's name, status, duration and counts; the runs the invocation minted or continued, by stage; the counts below; the progress lines of §4; and one `failure` line per failed step. The counts are scoped to the runs this invocation arrived at (ADR-154): occurrences in the walk the invocation read, verdicts by kind, extraction faults by category, cluster faults by kind, clusters arranged and synthesis docs written.

Those counts read tables that `ledger` (`file_occurrence`, `run`, `verdict`), `extraction` (`extraction_fault`) and `synthesis` (`cluster`, `cluster_fault`, `synthesis_doc`) own. ADR-041 says a capability owns its tables and nothing else reads them. **This record makes one exception, bounded three ways:** the reads are `COUNT`s and nothing else, so they write nothing and carry no row out; each is grouped by a closed column or by none; and they are made in `InvocationAccount`, in the composition root, and nowhere else. The counts are not moved into the owning modules. That would move run ids of stage 2 and cost a replay of stage 2, to buy a count the account needs once per invocation.

### 4. Progress lines are carried by the one logger whose every line is already safe, and checked once more

`StageProgress` writes a loop's progress through its own logger, and every line it writes is a label the stage's own code spelled, then counts (`Stage 2 (extraction): 2,000 of 100,000 (2%)`). While the job runs, `InvocationAccount` attaches a Logback appender to **that logger alone**, and the appender writes a line only when its formatted message matches `Stage <digit>[a-z]? (<lowercase words, digits, commas, hyphens>): <count>` followed by `of <count> (<n>%)` or `so far`. A line that does not match is counted and not written, and the count is the line `progress withheld=<n>`. So a future label built from a document's title is withheld by shape, and the test over the source (below) fails the build.

The census's running count is written by `corpus`, which may not depend on `pipeline` (ADR-192 §6), so it is not in the account. The census step's own line carries its duration and the count of occurrences.

### 5. What an exception keeps: its type, and the types of its causes, and never its message

A failed step writes `failure step=<name> type=<class> cause=<class> cause=<class>`, the first exception beneath Spring Batch's own wrappers (the reading `StepFailure` already makes) and at most three causes. ADR-076 names a type for the fault, so `MisshapenProfileException`, `DoclingKeepsDroppingConnectionsException` and `DatabaseFileLockedException` say what happened without a word of the message.

Alternatives weighed:

- **Scrub the message of paths and text.** Rejected. A pattern for a Windows path with spaces, a UNC share, a non-Latin name and a title that is not a path at all cannot be shown to leave nothing, and a message is free text by construction.
- **Keep the message for a list of exception classes whose messages are made of constants and numbers.** Not adopted: the list has to be kept true as messages change, and an entry is wrong the day someone adds a file name to one. It can be added later, class by class, with a test per class.
- **Write the message to the operator's log only.** That is what happens today and still does.

### 6. A failure to write the account never fails the invocation

If the file cannot be created or written, the invocation goes on and writes one line to `vespera.log`: `the invocation account could not be written to {file}: {exception type}`, with the file it tried to create. That line names a path, in the operator's log, where paths belong. The account is never a gate.

### 7. The operator's own lines do not change

The account is an extra output. No existing log call is edited, `logback-spring.xml` is untouched, and the console and `vespera.log` carry what they carried. The only lines added are the warnings of §1 and §6, which appear only when no account was written.

### 8. When it is written

`InvocationAccount` is a `JobExecutionListener` on the one job. `invocation started` and the progress appender open in `beforeJob`, so **a process killed mid-run leaves only the progress lines it reached**. The step, run, count and failure lines are written in `afterJob`, from the job execution and the database, so a killed process leaves none of them. The operator's log still has those.

## Alternatives weighed, beyond the four questions

- **The account in the working directory beside `vespera.log`**, which is what the issue first asked. Rejected by the operator: the hook refuses everything there, with no exception for a file name.
- **A Logback marker.** Lines written `log.info(SAFE, ...)` go to the file. Rejected for the reason in §1: it is a filter on lines, and the marker is a promise each author makes by hand.
- **Rewriting the existing lines to be safe**, so that one log serves both readers. Rejected: the operator needs the paths, and §7 keeps their lines.
- **A summary built by reading `vespera.log` afterwards.** Rejected: it would read the log, which is what must not be read.

## Consequences

- **Claude can diagnose a run from one file** that names each step, its outcome, its duration, its counts, its progress and the type of what failed, and from nothing else, once the operator has set `vespera.account-dir` and allowed that folder.
- **With the property unset there is no account.** The operator gets one warning in `vespera.log` and nothing else changes.
- **A diagnosis that needs a message needs the operator.** A type says which fault; it does not say which file. That is the cost of §5, taken on purpose.
- **A new count or line is a code change to `InvocationAccount`**, with a type that cannot hold text. Adding free text means adding a method that takes a `String`, which a review will see.
- **Run ids move for stages 3 to 6b.** Their implementation versions span `pipeline` (`StageModules`), and this change edits `pipeline`. Stage 1, which spans `corpus` alone, and stage 2, which spans `extraction` and `similarity`, do not move. An archive's next invocation therefore replays stages 3 to 6b under new run ids, and it **waits for this change as well as for #411**, so the replay is paid once.
- **Nothing in `ledger`, `corpus`, `extraction`, `similarity`, `embedding` or `synthesis` changes.**

## Tests

- **`InvocationAccountInvocationTest`** runs whole invocations over a marked fixture and holds the marker's absence, the steps, the counts, the progress lines and a failure by type.
- **`InvocationAccountTest`** holds the writer alone: a withheld progress line, a run line, an unknown stored value written as `other`, the `-2` suffix, a write that fails without failing the invocation, and an unset or enclosed folder writing nothing.
- **`StageProgressLabelsAreConstantsTest`** reads `src/main` and holds that every progress label is literal text, with `stage` allowed only in `RedundancyResolutionTasklet` and `place` and `of` only in `ClusteringTasklet`. It passes on main and has to go on passing: it holds the premise §4 rests on.

## For the operator to confirm

Decided on #424 on 2026-10-05, and recorded here so that the record is the whole answer:

1. **Where**: a folder named by `vespera.account-dir`, outside every working directory (§1). The hook has to allow that folder.
2. **No exception message at all**, only types (§5).
3. **Run ids are written.**
4. **Step, count and failure lines come at the end of the job**, so a killed process leaves progress lines only (§8).
5. **The census's running count is not carried** (§4).
6. **`vespera label` writes no account.** It runs no job, answers on stdout, and records answers about documents.
7. **Counts are scoped to the runs the invocation arrived at**, not to the whole database.
8. **The file name** is `invocation-account-<UTC>.txt`.

## What this does not decide

- **A retention rule** for old accounts. They are a few kilobytes each.
- **Where the operator's folder is.** It is theirs to choose; this record only says it is not a working directory.
