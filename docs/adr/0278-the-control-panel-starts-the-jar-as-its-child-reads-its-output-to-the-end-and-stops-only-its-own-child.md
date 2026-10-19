# ADR-278 — The control panel starts the jar as its child, reads its output to the end, and stops only its own child

> **Partly amended — see [ADR-281](0281-the-closing-line-is-the-last-line-of-output-and-two-library-loggers-write-to-the-console-only-at-warn.md).** Of Context's *What follows the closing line* and §5: the last line of output that is not blank is the command's closing line; Hikari's lines and Spring Batch's job line are on the console only at WARN and stay in `logs/vespera.log`. Of What this does not decide: the closing line as the last line is decided there. The rest of this record stands.

> **Partly amended — see [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md).** Of What this does not decide and the Tests section: the control panel's code lives under `io.algernon.controlpanel` and is built as a second jar, and ADR-285's Tests section names the methods holding Held, Draining, Stop, How it ended, No connection and The command line; Run and Label disabled while a line is held, and an orderly exit of the control panel while its child runs (§7), wait for the screens. Of §4: a lock file absent, empty or whose first line is blank is held by nobody, and a first line that is not blank and does not parse counts as held, shown whole. Of §5: any other exit code of a child the control panel did not stop is failed or refused, with its code. The rest of this record stands.

- **Date**: 2026-10-18
- **Status**: accepted on 2026-10-18.
- **Keeps**: [ADR-158](0158-the-operator-starts-the-sidecars-from-compose-yaml-and-the-packaged-jar-starts-none.md), whose What this does not decide leaves a program that starts the jar for the operator to a decision of its own: this is that decision for the **control panel**, and this record changes nothing in the packaged jar, its commands or what it starts; ADR-279 adds the corpus-root lock to `run`.
- **Keeps**: [ADR-047](0047-the-pipeline-never-blocks.md), the CLI sized to two commands. The control panel adds no command and no option to it.
- **Keeps**: [ADR-141](0141-the-cli-exits-with-the-commands-exit-code-and-the-scheduler-no-feature-uses-is-removed-at-its-source.md): the process exits with the command's exit code, which, with the last line of output, is what the control panel reads (§5).
- **Keeps**: [ADR-177](0177-one-invocation-per-working-directory-and-a-locked-database-file-is-named.md) §1: every invocation takes `vespera.lock`, `--help` and `--version` included, and a killed holder's lock is released by the operating system while its line stays in the file.
- **Keeps**: [ADR-265](0265-vespera-run-rewrites-a-progress-page-in-the-working-directory-while-it-runs.md)'s refusal of "a script that polls the database". Its reason, that a poll of `vespera.db` takes a connection from the invocation's pool and reads tables its steps are writing, does not hold of the control panel, which opens no connection to `vespera.db` while it watches (§3).
- **Keeps**: [ADR-266](0266-every-invocation-names-its-progress-page-first-and-opens-it-and-vespera-label-writes-it-too.md) §2 and [ADR-274](0274-the-deliverable-is-html-with-a-verified-copy-of-every-survivor-it-lists-and-its-rendering-keys-no-run.md) §7: `SystemPageOpener` opens nothing where `CI` is set, which the control panel relies on (§2).
- **Settles** [#559](https://github.com/algernon28/vespera/issues/559).

## Context

### What the jar offers a program that starts it

`VesperaApplication.main` ends in `System.exit(SpringApplication.exit(…))` (ADR-141), so the exit code is the command's: 0 when it ended, 1 when it failed or was refused, 2 when picocli refused its command line. Every invocation takes `vespera.lock` in its working directory before it opens `vespera.db` (ADR-177), `--help` and `--version` included: each of those boots the context, takes the lock, creates a `.vespera` under the current directory when no `--db-dir` is named, and `--version` prints no version. A killed invocation leaves a working directory the next one continues from, losing the work since the last commit of the step that was running (`docs/running-stage-by-stage.md`, ADR-139). `CI` set to any value that is not blank stops the shipped opener on every system (`SystemPageOpener`).

### What was measured

On Windows 11, JDK 26.0.2.1, locale it-IT, ANSI code page 1252, OEM code page 850, with the packaged jar started as a child whose standard streams are pipes, on synthetic files, with no sidecar reachable or a stub one:

- **Encoding of the child's output.** With no flag, `stdout.encoding` and `stderr.encoding` are `Cp1252`, the `native.encoding`, and not the OEM code page 850. `System.out` and `System.err`, by `println` and `printf`, write windows-1252, `ł` and `漢` each replaced by `?` (0x3F). Logback's lines are UTF-8, since its encoder names no charset and the default charset is UTF-8, so one stream carries two encodings. `-Dfile.encoding=UTF-8` changes nothing. `-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8` make every print path UTF-8 on both streams, losing nothing, and change no byte of Logback's output. In the jar the print paths are the refusals on standard error (the working directory in use, label's refusals), label's outcome and next-action lines, and picocli's usage.
- **Encoding of the child's command line.** A child JVM receives its arguments through the ANSI code page (`sun.jnu.encoding` is `Cp1252`): an argument holding `ł` or `漢` arrives with each replaced by `?`, and `è`, which windows-1252 holds, arrives whole. `-Dsun.jnu.encoding=UTF-8` changes nothing, and an `@` argument file is read in the same code page. An environment variable holding `ł`, `漢` and `è` arrives whole.
- **A kill.** `Process.supportsNormalTermination()` is false. `destroy()` and `destroyForcibly()` both end the child at once, and `waitFor()` returns 1 within about 6 to 18 ms. No shutdown hook runs, and neither Hikari's lines nor Spring Batch's job line is printed. A grandchild survives its parent's kill unless it is killed first. A parent JVM that exits leaves its child running. After a kill, a new invocation on the same working directory is accepted at once; `vespera.lock` keeps the dead holder's line until then; `vespera.db-wal` and `vespera.db-shm` remain and the next open reads them; `progress.html` still says the invocation is running, with its 5-second refresh; and the invocation account holds only `invocation started`.
- **`vespera.lock` while held.** One UTF-8 line, ended by the platform's line separator (CRLF here), `pid=<pid> started=<offset date and time, to the second> command=<arguments>`, readable by another process while it is held. `started` is about 2 seconds after the JVM process starts.
- **What follows the closing line.** Exit 0: Hikari's two shutdown lines. A failure of stage 2, exit 1: Spring Batch's job line, then Hikari's two lines. A label refusal, exit 2: Hikari's two lines after the line on standard error. A working directory in use: one line on standard error and nothing else.
- **Start-up.** Every invocation prints four JDK `WARNING` lines about a restricted method on standard error as it starts (sqlite-jdbc's `System::load`). They are lines like any other to the control panel, kept in its tail and never the last one.

## Decision

### 1. Start

The control panel starts the packaged jar with `ProcessBuilder` as one of:

```
java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar <jar> run <corpus root> --db-dir=<working directory>
java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar <jar> label [--auto] --db-dir=<working directory>
```

with `CI=1` in the child's environment and the working directory as the child's current directory. Every path it passes is made absolute first. It never calls the jar with `--version` or `--help`, nor in any other way than these two. One child at a time: Run and Label are disabled while it lives. How the jar and a JDK 26 are found is #567's; which values are passed, and which are remembered, is #564's.

### 2. The pages

`CI=1` stops `SystemPageOpener` from opening `progress.html` and the deliverable's `index.html` in the default browser. The control panel shows or links `<working directory>/progress.html` and the index itself; where on its screen is the prototype's (#561).

### 3. Watching

Standard error is merged into standard output (`redirectErrorStream(true)`), and one thread reads the merged stream to its end, always, whatever else the control panel is doing: the jar's logging to standard output is synchronous, so a pipe nobody drains stalls the step that is running. The last 200 or so lines are kept in memory only, and nothing of them is written to disk: `logs/vespera.log` holds every line already, and lines of errors name archive paths.

Watching opens no connection to `vespera.db`, ever. It reads only the child's output, the holder line of `vespera.lock`, the holder line of the **corpus-root lock**, and `progress.html`. Whether a later decision reads `vespera.db` between invocations is that decision's.

### 4. A working directory held by an invocation the control panel did not start

When the control panel starts, and whenever its working directory or corpus root field changes, it reads the holder line of `vespera.lock` in that working directory and of that corpus root's corpus-root lock. Both are readable while held, in UTF-8. A line is **held** when:

- the process its `pid` names is alive, **and**
- that process's start instant is no later than `started` plus 1 second.

`started` is the time the lock was taken, to the second, about 2 seconds after the process started; a process reusing the pid always starts after its holder died, and so after `started`; and the line a kill leaves behind fails the first condition. A start instant the system does not give counts as held.

While a line is held, Run and Label are disabled, and the control panel shows the holder line and that invocation's `progress.html`. It takes no lock and makes no call to the jar to find this out.

### 5. How it ended

| The child | Shown as |
| --- | --- |
| killed by the control panel's own Stop | **Stopped**, which the control panel records itself, since a kill exits 1 as a failure does |
| exit 0 | ended |
| exit 1 | failed or refused |
| exit 2 | the jar refused the command line the control panel built, shown as a defect of the control panel |

Each with the last line of output that is not blank. Once #579 is fixed that is the command's closing line; until then Hikari's two shutdown lines follow it, and on a failure Spring Batch's job line first. The control panel does not take those lines out of its tail; the jar is fixed instead.

### 6. Stop

Stop is offered for the control panel's own child only. For any other holder the control panel shows the holder line and says to stop that invocation where it was started.

Stop asks to be confirmed with one sentence that is true at every step: stopping loses the work since the running step's last commit, and the next `vespera run` continues from there. Confirmed, it kills the child's descendants, then the child.

After a stop the control panel shows its own **Stopped** and does not present `progress.html` as live: a killed child leaves the page saying the invocation is running, with its refresh, until the page's own script marks it stale (ADR-267 §5).

### 7. The control panel ends while its child runs

An orderly exit asks whether to stop the child or leave it running, leaving it by default. A hard close leaves it running anyway. A control panel started again finds that invocation held (§4) and offers no Stop for it (§6).

### 8. What changes in the jar

Nothing, by this record: it adds no option, command or output to the jar, and no module of `src/main` changes for it, so no run id moves. The same ticket adds the corpus-root lock to `vespera run` by [ADR-279](0279-one-vespera-run-per-corpus-root-by-a-lock-on-a-file-named-for-its-real-path-outside-every-working-directory.md), whose holder line §3 and §4 read.

## Alternatives refused

- **The child started without `CI`.** Each Run and each Label opens a browser tab, and they pile up beside the control panel.
- **No encoding flags, and each line's encoding guessed.** Anything outside windows-1252 is lost before the control panel reads it, and a guess between UTF-8 and windows-1252 is wrong on some lines.
- **The two streams read apart, or the output copied to a file.** Two threads where one does, and a second copy of `vespera.log` naming archive paths.
- **Killing the child alone.** A grandchild survives it.
- **A cost of stopping per step, read from Spring Batch's `Executing step: [name]` line.** It ties the control panel to a third party's log format, and to a second copy of the stage-by-stage guide.
- **Hikari's and Spring Batch's lines taken out of the tail.** It ties the control panel to the wording of libraries; the last line is the jar's to keep (#579).
- **A held working directory learned only from Run's refusal.** A control panel started again could not show the child it left running.
- **Stop for a holder the control panel did not start, by its pid.** A pid read from a file may be reused, and `ProcessHandle.Info`'s command line is often empty on Windows.
- **An orderly exit of the control panel killing its child.** It throws away the open batch of a stage that can run for days.

## Consequences

- An invocation started by the control panel prints UTF-8 on both streams, opens no page, and is read by one thread from its first line to its last.
- An invocation the control panel did not start, or started before it was closed, is shown and never stopped by it.
- The control panel opens no connection to `vespera.db` while it watches, so it slows no step.
- A path holding a character outside the ANSI code page reaches the jar as an argument with that character replaced by `?` (measured above). §1 passes paths as arguments, so such a path does not reach the jar whole.

## Tests

| Method | What it holds |
| --- | --- |
| `pipeline.TheControlPanelsChildIT.itsRefusalOfAWorkingDirectoryInUseIsUtf8OnTheMergedStream` | the packaged jar, started with the two encoding flags and `CI=1`, its standard error merged into standard output, on a working directory another invocation holds whose name holds `ł` and `漢`: one line, strict UTF-8, naming the directory whole, exit 1 |
| `pipeline.WhatTheControlPanelReadsOfAnInvocationTest.theHoldersStartIsNoLaterThanStartedPlusOneSecond` | a second JVM holding a working directory: its process's start instant is no later than its holder line's `started` plus 1 second |
| `pipeline.WhatTheControlPanelReadsOfAnInvocationTest.ciSetToOneOpensNoPage` | `CI=1`, a display set, on Windows, macOS and Linux: `SystemPageOpener` starts no command |
| ADR-177's `pipeline.WorkingDirectoryLockTest` and `pipeline.WorkingDirectoryInUseIT` | the holder line's shape and readability while held, a killed holder's working directory taken at once by the next invocation, and the refusal's one line and exit 1 |

The control panel's own code has no home yet: where it lives and how it is built is #562's, after the prototype (#561). Its build carries these tests, each against its own code:

- **Held**: a line whose pid is alive and whose process started no later than `started` plus 1 second is held; one whose pid is dead is not; one whose pid is alive but whose process started more than 1 second after `started`, a reused pid, is not; one whose process gives no start instant is held. Run and Label are disabled while a line is held, and no lock is taken and no jar started to find it.
- **Draining**: a child that writes more than a pipe's buffer to standard error, and nothing to standard output, ends; one thread reads the merged stream; the tail holds the last 200 or so lines and nothing is written to disk.
- **Stop**: the child's descendants are killed before the child, and a grandchild is gone once Stop returns; Stop is not offered for a holder the control panel did not start; its confirmation is the one sentence of §6.
- **How it ended**: a child killed by Stop is shown as Stopped although it exits 1; exit 0, 1 and 2 are shown as §5's table says, with the last line of output that is not blank.
- **No connection**: watching an invocation from its start to its end opens no connection to `vespera.db`.
- **The command line**: the control panel starts the jar only as §1's two commands, with the two encoding flags, `CI=1`, absolute paths and the working directory as current directory, and never with `--version` or `--help`.
- **Ending while a child runs**: an orderly exit leaves the child running unless Stop is chosen.

## What this does not decide

- **How the jar and a JDK 26 are found** (#567), **which values are passed and remembered** (#564), **where the pages sit on screen** (#561), and **where the control panel's code lives and how it is built** (#562).
- **The corpus-root lock** itself: where its file lies, its name and its refusal are [ADR-279](0279-one-vespera-run-per-corpus-root-by-a-lock-on-a-file-named-for-its-real-path-outside-every-working-directory.md)'s. This record reads its holder line as it reads `vespera.lock`'s.
- **The closing line as the last line** (#579), a fix in the jar.
- **A path the ANSI code page cannot hold.** How the control panel passes, or refuses, a working directory or corpus root holding such a character is not decided here.
- **Reading `vespera.db` between invocations.**
