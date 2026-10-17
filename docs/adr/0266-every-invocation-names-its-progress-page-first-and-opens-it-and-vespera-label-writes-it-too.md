# ADR-266 — Every invocation names its progress page first and opens it, and `vespera label` writes it too

> **Partly amended — see [ADR-269](0269-the-progress-page-is-four-tabs-draws-a-hover-over-each-chart-and-lists-the-runs-the-working-directory-holds.md).** Of §3: the label page's line saying whether the command runs or has ended also names when it started and when it ended. The rest of this record stands.

- **Date**: 2026-10-16
- **Status**: accepted on 2026-10-16.
- **Amends**: [ADR-265](0265-vespera-run-rewrites-a-progress-page-in-the-working-directory-while-it-runs.md), in these places and no others: §2's sentence "`vespera label` starts no job and writes no page; a page an earlier `vespera run` left is left as it is", which §3 below replaces; and §6, whose README sentence §5 below replaces.
- **Keeps**: the rest of ADR-265: what the page of `vespera run` holds (§1), when it is written (§2), what it reads (§3), that a write that fails fails nothing (§4), and that no run id moves (§5); above all its rule that the page names no path, no title and no text of a document. [ADR-130](0130-one-report-page-module-owns-the-skeleton-styling-escaping-and-tables.md), one module assembles every page; [ADR-223](0223-5fs-size-report-6a-and-6b-go-through-one-seed-partition-at-a-time-and-what-is-still-held-says-why.md) §3 and §6, a page moved over its place in one move; [ADR-226](0226-the-eight-rules-in-pipeline-live-in-extraction-embedding-synthesis-and-profile-and-no-stages-version-names-pipeline.md), no stage's version names `pipeline`; [ADR-261](0261-an-agent-may-read-and-write-a-working-directorys-profile-and-read-its-pages-of-paths-counts-and-reasons-and-nothing-else-in-it.md), whose list of pages an agent may read the page does not join.

## Context

ADR-265's page is there only for an operator who knows to look for it: nothing an invocation prints names it, nothing opens it, and `vespera label` leaves the page of the last `vespera run` in place, so a browser that has it open shows a run that has ended while the label command records answers. The operator wants the page in front of them every time Vespera is started, with no step of their own.

An invocation's console starts with Spring Boot's own start-up lines; the command's first line is `vespera run`'s `Walking <root>, named on the command line` (or `by vespera.corpus-root in configuration`), logged at INFO through `VesperaCommand.Run`'s logger, which the console and `vespera.log` both carry. `vespera label` prints its outcome to standard output and `LabelIngestion` logs the same text at INFO.

`SpringApplication` sets `java.awt.headless` to `true` unless told otherwise, so `java.awt.Desktop.isDesktopSupported()` is `false` in every invocation; and `synthesis`'s `DifferenceHash` decodes pictures through `javax.imageio` in that mode, for a stage whose version names `synthesis`.

`LabelIngestion.ingest` counts the answers it records as new, changed and unchanged, and the questions still blank; `AutoLabelling.run` counts those answered relevant and not relevant, those left blank, and those already answered by a person. Each hands its command an `Outcome` of a refusal flag and a message, and the message names the seed set's path and, for a changed answer, the path of its document.

## Decision

### 1. The first line names the page

**`vespera run` and `vespera label`, with or without `--auto`, log one line at INFO through the command's own logger before any other line the command logs or prints**:

```
Progress page: <path>
```

`<path>` is `progress.html` in the working directory the invocation opened, absolute and normalised, as a plain path in the system's own form (`D:\...\progress.html` on Windows), and not a `file:` URI: a terminal links an absolute path as readily as a URI, a browser's address bar takes one, and a URI percent-encodes the spaces and accented letters an operator reads back. The path ends the line, with nothing after it, so a terminal that links it takes the whole path and no punctuation.

It is logged only by an invocation that writes the page (§2 of ADR-265, §3 below): `vespera run` once it has refused neither a `--db-dir` that is not the directory opened nor a missing root, and `vespera label` once it has refused neither that `--db-dir` nor `--auto` with a file. Those refusals stay the first and only lines they are. Spring Boot's start-up lines still come first on the console; of the command's lines this is the first.

**The line names the working directory, and that is the operator's own configuration, not a document's path.** It is on the console and in `vespera.log`, as every line of the command is. It is not on the page, which still names no path (ADR-265 §1), and it is not in the invocation account, whose allow-list admits only a counter's lines (ADR-198).

### 2. The page opens itself

**Once in each invocation that writes the page, after its first write of that invocation has put the page in place, `ProgressPage` hands the page's `file:` URI to a `PageOpener`**, a new interface of `pipeline`:

```java
void open(URI page) throws IOException
```

It is called on the invocation's thread, from `beforeJob` for `vespera run` and from the first write of §3 for `vespera label`, outside the lock the page is written under, and never again in the same invocation, however many times the page is rewritten. When that first write failed (ADR-265 §4), nothing is opened in that invocation: there is no page to open, and a page that appears later in a long run is not a reason to open a browser in the middle of it. A second invocation opens it again, in a new tab or window: the tab the last one opened holds a page with no reload.

**There is no setting: the defaults decide.** No property, option or profile value turns opening on or off. The page is opened in every invocation that writes it, and the one way nothing is opened is the rule of `SystemPageOpener` below, where no person could see it.

**The shipped opener, `SystemPageOpener`, starts the system's own command for opening a file with its default program, and does not wait for it**, its output discarded:

| System (`os.name`) | Command |
| --- | --- |
| starts with `Windows` | `rundll32 url.dll,FileProtocolHandler <uri>` |
| starts with `Mac` | `open <uri>` |
| any other | `xdg-open <uri>` |

**It opens nothing where no person could see it**: where the environment variable `CI` is set and not blank, on every system, and on a system other than Windows and macOS where neither `DISPLAY` nor `WAYLAND_DISPLAY` is set and not blank. It says nothing then: the line of §1 has already said where the page is. The choice is one function of `SystemPageOpener`, so that it can be held without a display or a process:

```java
static Optional<List<String>> command(URI page, String osName, Map<String, String> environment)
```

empty where nothing is opened. `open` reads `System.getProperty("os.name")` and `System.getenv()` and starts what `command` returns.

**Opening never fails an invocation.** Whatever `open` throws, an `IOException` or any `RuntimeException`, is caught where it is called and logged once, at WARN:

```
the progress page could not be opened: <exception class name>
```

the class name only, as ADR-265 §4 writes its own warning, and nothing else changes: not the page, not the job's status, not the exit code, and nothing at ERROR.

### 3. `vespera label` writes the page

**`vespera label` and `vespera label --auto` write `progress.html`**, through the same `ProgressPage`, the same `.part` and the same move (ADR-265 §2), and under ADR-265 §4: a write that fails is warned of once and fails nothing. `ProgressPage` is made a bean of `VesperaJobConfiguration`, registered on the job as it is now and injected into `VesperaCommand.Label`; the methods it gains for a command that starts no job are the implementer's to name.

It is written twice, and only twice:

- **when the command starts**, after the refusals of §1, over whatever page an earlier invocation left. It reloads every `vespera.progress-interval` (ADR-265 §1's head, the same rounding), and says `This invocation is running. This page is rewritten when it ends.`; no thread of its own rewrites it in between, because the command holds no step and logs no counter's line;
- **when the command ends**, however it ends, with `ReportPage.render` and no reload.

The page holds the title `Vespera progress`, the line saying whether it runs or has ended, then `Command: vespera label` or `Command: vespera label --auto`, and ADR-265's `Written at`, `Elapsed` (from the command's start, read off the `Clock`) and `Database size` lines. On the last write it says `This invocation has ended: <outcome>.`, the outcome being one of three:

| Outcome | When |
| --- | --- |
| `recorded` | the command recorded the file's answers, including a file whose every answer was already recorded |
| `refused` | the command refused and recorded nothing, for the reason it prints |
| `failed` | an exception left the command |

**Where it recorded, the last write holds a table** whose header row is `Answers`, `Count`, and one row for each count the command made, by its words:

| Command | Rows, in this order |
| --- | --- |
| `vespera label` | `new`, `changed`, `unchanged`, `still blank` |
| `vespera label --auto` | `relevant`, `not relevant`, `left blank`, `answered by a person` |

A refused or failed command's page has no table, and **the reason a command refused is not on the page**: it names the label file's path, the seed set's or the working directory's, and it is printed on the console as before. The counts reach the page as numbers: `LabelIngestion.Outcome` and `AutoLabelling.Outcome` carry them beside the message, and the page never reads, parses or copies a message, which names the seed set's path and each changed answer's document. So ADR-265 §1's rule holds for this page as for the run's: no path, no title, no text of a document, no exception message and no run id.

### 4. The modules, and run ids

`PageOpener` and `SystemPageOpener` are classes of `pipeline`, on record in `PipelineHoldsOnlyTheRulesOnRecordTest` as holding no rule, as `ProgressPage` is. **No run id moves**: every class this changes is in `pipeline`, which no stage's version names (ADR-226) — `ProgressPage`, `PageOpener`, `SystemPageOpener`, `VesperaCommand`, `VesperaJobConfiguration`, `LabelIngestion` and `AutoLabelling` — and no property is added. Nothing here changes what `vespera label` records, refuses or prints but the line of §1.

### 5. What the operator is told

The README's list of the working directory's files says `progress.html` is what the command running now has done; its sentence under the list says that every `vespera run` and `vespera label` opens the page in the default browser as it starts and that the command's first line names it, that it reloads itself while `vespera run` runs and that `vespera label` writes it as it starts and ends with the answers it counted, and that it names no path and no document. The Commands section says, beside `vespera.progress-interval`, where nothing is opened: where `CI` is set, and on Linux with no display.

## Alternatives refused

- **A setting to turn opening off**, a `vespera.progress-open` that would ship `true` and be `false` under the test profile. The operator holds that a new flag "makes things needlessly complicated" and that the defaults should decide, so none is added for now; a machine where nobody could see the page already opens nothing (§2), and no test opens a browser without one (Tests).
- **`java.awt.Desktop.browse`.** It is unsupported while `java.awt.headless` is `true`, which Spring Boot sets for every invocation; setting it `false` for the whole application to open one page changes the mode `DifferenceHash` decodes pictures in, for stage 6b, and on a Linux machine with no display makes AWT fail where it now works.
- **`cmd /c start "" <path>` on Windows.** `cmd` parses its command line again, and a `&`, `^` or `%` in a working directory's path would be read as its syntax; `rundll32` is handed the percent-encoded URI as one argument.
- **Opening a browser at each write, or at the first write that succeeds after a first write that did not.** One tab per invocation is what is wanted; a tab opened in the middle of a run that has been going for hours is not.
- **Only printing the path, with no opening.** An operator would still have to copy it into a browser on every invocation.
- **A `file:` URI in the line of §1.** It percent-encodes spaces and accented letters, which an operator reads and types back wrong.
- **The label command's message on its page.** It names the seed set's path and each changed answer's document, and the page is sent to anyone.
- **A rewrite of the label page every interval.** The command holds no step and logs no counter, so a rewrite in between would show only a later time.

## Consequences

- An operator who starts `vespera run` or `vespera label` sees the page open in the default browser, and reads its path on the command's first line; a machine with no display and a CI job open nothing, and neither does any test.
- A browser that has the page open while `vespera label` runs shows the label command and, when it ends, how many answers it recorded, instead of the run before it.
- Each invocation starts at most one process to open the page.
- The guard's list of pages an agent may read (ADR-261) still does not name the page.
- No schema, cache key, verdict kind or run id changes.

## Tests

| Method | What it holds |
| --- | --- |
| `pipeline.ProgressPageOpensInvocationTest.theRunOpensThePageOnceAfterItsFirstWrite` | with the page rewritten every tenth of a second, `vespera run` asks the opener once, for the page's URI, when the page is already there and says the invocation is running |
| `pipeline.ProgressPageOpensInvocationTest.theFirstLineTheRunLogsNamesThePagesPath` | the first line `vespera run` logs is `Progress page: ` and the page's absolute path, at INFO |
| `pipeline.ProgressPageOpensInvocationTest.theLabelCommandNamesThePageFirstAndOpensItOnceItIsWritten` | `vespera label`'s first line names the page's path, and it asks the opener once, when its first write is there and says the invocation is running |
| `pipeline.ProgressPageOpensInvocationTest.anOpenerThatThrowsFailsNothingAndIsOneLineOfTheLog` | an opener that throws: the run completes, its page ends `completed`, one warning names the exception's class and no error names the page |
| `pipeline.LabelWritesTheProgressPageInvocationTest.theLabelCommandWritesWhatItRecordedAndStopsTheReload` | after `vespera label` records one new answer, the page says it ended `recorded`, has no reload, names the command, and counts 1 new, 0 changed, 0 unchanged, 0 still blank |
| `pipeline.LabelWritesTheProgressPageInvocationTest.aChangedAnswerIsCountedAndNoPathOrTextIsOnThePage` | a changed answer is counted as changed, and no marked name or text, the root, the seed folder or the working directory is on the page |
| `pipeline.LabelWritesTheProgressPageInvocationTest.aRefusedLabelCommandSaysItWasRefusedAndNotWhy` | a label file with no answers: the page says it ended `refused`, has no table, and names no path |
| `pipeline.SystemPageOpenerTest.eachSystemIsAskedThroughItsOwnCommand` | Windows is asked through `rundll32 url.dll,FileProtocolHandler`, macOS through `open`, a Linux with a display through `xdg-open`, each with the URI |
| `pipeline.SystemPageOpenerTest.aPathWithCharactersAShellReadsIsHandedOverAsOneArgument` | a page under a folder whose name holds a space, `&`, `%`, `^` and an accented letter: three arguments on Windows and two on macOS and Linux, the address holding no space and reading back as the page's path |
| `pipeline.LabelCommandThatThrowsTest.anExceptionReachesTheCallerAndThePageEndsFailed` | an exception from `LabelIngestion.ingest` reaches the label command's caller, and the page ends `failed` with no reload, no table of answers and not the exception's message |
| `pipeline.LocalLabellingInvocationTest.theAutoPageCountsTheAnswersInOrder` | after `vespera label --auto`, the page names the command with `--auto`, ends `recorded`, and counts `relevant`, `not relevant`, `left blank` and `answered by a person` in that order |
| `pipeline.SystemPageOpenerTest.withNoDisplayOrUnderCiNothingIsOpened` | a Linux with neither display variable, and every system with `CI` set, open nothing |
| `pipeline.NoTestOpensABrowserTest.theWholeApplicationsOpenerIsTheRecordingOne` | the whole application a test builds holds `SystemPageOpener`, and the `PageOpener` it hands out is `RecordingPageOpener` |

`PipelineHoldsOnlyTheRulesOnRecordTest` holds `PageOpener` and `SystemPageOpener` on record as holding no rule.

**No test opens a browser**, with no setting to turn opening off. `pipeline.RecordingPageOpener`, in the test tree, is a `@Primary @Component` under the application's package: it records what it is asked and opens nothing, every whole application a test builds scans it and hands it to every invocation in place of `SystemPageOpener`, and `CascadeSliceTest` imports it by name, so the slice never holds the system's opener. Every test that starts the packaged jar as a child process into an invocation, `CliExitIT`, `ProfileThatDoesNotLoadIT` and `WorkingDirectoryInUseIT`, sets `CI` in the child's environment, so `SystemPageOpener` chooses no command there.

## What this does not decide

- **The look of the label page** beyond the strings §3 fixes.
- **Which browser opens**: the system's default program for an `.html` file is the operator's.
- **Whether an agent may read the page**, which stays a record of its own (ADR-265).
