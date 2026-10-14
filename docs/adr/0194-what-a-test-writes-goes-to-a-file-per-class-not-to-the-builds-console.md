# ADR-194 — What a test writes goes to a file per class, not to the build's console

- **Date**: 2026-10-05
- **Status**: accepted
- **Extends**: [ADR-093](0093-logging-is-explicit-and-process-scoped-console-plus-rolling-file-per-item-and-per-step-at-info.md), in the test JVM only. Its console is still the one `src/test/resources/logback-test.xml` configures, with the same pattern, levels and logger entries. What changes is where Surefire and Failsafe send what that console writes: to a file per test class, not to the build's console (§1). Production logging is untouched.
- **Rests on**: [ADR-065](0065-the-walk-algorithm-is-tested-on-an-in-memory-filesystem-identity-stays-on-ntfs.md), for CI's two jobs. Each uploads the files when it fails (§2).
- **Keeps**: every test that asserts a line's presence or absence, whether through a `ListAppender` or through `CapturedOutput`. No test is edited, and §3 says why none can see the change.
- **Settles** [#415](https://github.com/algernon28/vespera/issues/415).

## Context

A build in which every test passes printed errors and stack traces that read like failures. A quiet (`-q`) `verify` on Windows in which every test passed wrote a log of 54,992 lines: 125 `ERROR` lines, 282 `WARN` lines and 8,690 lines of stack frames (`at ...`). One of them, stage 2's health check failing against a loopback port nothing listens on, reads as a failing test. It comes from `ExtractionWithTheSidecarDownTest`, which points the production client at that port on purpose and then checks what Vespera says about it. All 125 `ERROR` lines come from 17 test classes in `pipeline` that provoke a fault on purpose, `ControlConversionInvocationTest` alone writing 38.

A reader of a green log cannot tell an error a test meant to cause from one it did not, and a real failure's output is buried among about 8,700 expected stack frames.

**Lowering the noisy loggers' levels does not work, and why is recorded here.** `ExtractionWithTheSidecarDownTest` attaches a `ListAppender` to the root logger and proves that Spring Batch's "Exception in afterStep callback" and "Exception while closing step execution resources" do not appear. With `AbstractStep` silenced that proof passes whatever the code does. A level belongs to the logger, so it changes what every appender receives.

## Decision

### 1. Surefire and Failsafe write each test class's output to a file

`pom.xml` sets the property `maven.test.redirectTestOutputToFile` to `true`, which both plugins read. Everything a test JVM writes to standard output and standard error while a test class runs goes to `<class>-output.txt` in `target/surefire-reports` or `target/failsafe-reports`, not to the build's console. This covers all of it: every level, from every test, passing or failing.

The console keeps Maven's own lines, the plugins' per-class result lines, and, for a test that fails, its assertion and stack trace. What a failing test *logged* is in its class's file.

It is a property and not a value in either plugin's `<configuration>`, because a property can be overridden: `-Dmaven.test.redirectTestOutputToFile=false` puts one run's test output back on the console. A value written into `<configuration>` would ignore the flag.

### 2. CI uploads the files when a job fails

Both jobs in `.github/workflows/build.yml` gain a step that runs only when the job fails. A job cancelled by its timeout or by a newer push reports as cancelled, not failed, and uploads nothing.

- **Linux job:** uploads `target/surefire-reports` and `target/failsafe-reports` as `test-output-linux`.
- **NTFS job:** uploads `target/surefire-reports` as `test-output-ntfs`.

A green run uploads nothing it did not upload before.

### 3. No test can see the change

The redirect happens where Surefire's forked JVM hands `System.out` and `System.err` to the plugin, below everything a test does.

- **`ListAppender` tests:** an appender a test attaches receives what it always did. No logger, level or appender changes.
- **`CapturedOutput` tests:** Spring Boot's `OutputCaptureExtension` lays its tee over the stream Surefire installed and forwards to it. The eight classes that read `CapturedOutput` see every line, as before.

### 4. No guard on the console

There is no check that a green build's console carries no `ERROR` line. A test's output cannot reach the console while the property is `true`, and turning it off is a one-line change to `pom.xml` that names this record.

## Measurements

Each column is an offline `verify` on Windows, JDK 26: before, without this change and quiet (`-q`); after, with it and in batch mode (`-B`), with no Docker daemon running.

| | Before (`-q`) | After (`-B`) |
|---|---:|---:|
| `ERROR` log lines on the console | 125 | 0 |
| `WARN` log lines on the console | 282 | 0 |
| `INFO` log lines on the console | 44,404 | 0 |

What the console still prints with this change:

- **Lines Maven writes about the build**, prefixed `[INFO]`.
- **The JVM's own `WARNING:` lines**, about `sun.misc.Unsafe` and native access, plus one `OpenJDK 64-Bit Server VM warning` line. These are printed by each forked JVM at start-up, before any test runs.
- **The integration tests' failures.** An integration test that errors, as one does when no Docker daemon is running, prints its failure's message and stack trace on the console, and its logged lines are in `target/failsafe-reports/<class>-output.txt`. That is the second half of this record working as intended: a failing test still shows that it failed and why.

## Alternatives weighed

- **Lower the loggers' levels in `logback-test.xml`.** Rejected, above: it blinds the absence proofs.
- **A JUnit extension, registered for every test class, that holds a test's console output and replays it only if the test fails.** Rejected:
  - Registering it for every class turns on JUnit's extension autodetection, which also activates Allure's own extension and adds fixtures to the report.
  - A test whose instance cannot be created leaves its begin and end unpaired, crashing a build that is already red.
  - It decides pass or fail before `@TempDir` deletion and `@AutoClose` can still fail the test, so those failures lose their output.

  It would have shown a failing test's log on the console itself. That is the one thing this record gives up (Consequences).
- **A JUnit launcher `TestExecutionListener` doing the same.** It sees each test's final result, so it avoids the decision made before `@TempDir` deletion and `@AutoClose`. Not adopted: it needs `junit-platform-launcher` declared in the pom and its order checked against Surefire's own listener, to buy only the console replay.
- **An annotation each fault test declares, muting the console for it, or a console filter keyed on an MDC value or a marker.** Rejected:
  - Every future fault test has to remember the declaration.
  - An MDC value belongs to one thread, and the sidecar stubs and the batch executors log from others.
  - Anything that mutes or filters the console appender empties what `CapturedOutput` reads.
- **Holding only `ERROR`, `WARN` and stack traces.** Not possible with a redirect, which takes everything. Accepted as the price: nobody reads a green build's `INFO` lines, and a quiet console is chosen over keeping them.

## Consequences

- **A green build's console carries no line a test logged.** Someone reading it, a person or an agent, sees Maven's lines, the plugins' results and the JVM's start-up warnings.
- **A failing test shows on the console that it failed and why.** What it logged is in `<class>-output.txt` locally, and in the failed job's `test-output-*` artifact in CI.
- **A passing test's output is in the same file, and still in the XML report.** Surefire's and Failsafe's `TEST-*.xml` reports go on carrying each test's `<system-out>`, as they did before.
- **IDE runs are untouched** where the IDE runs JUnit itself, as IntelliJ does unless told to delegate to Maven. Such a run shows what every test writes.
- **Nothing under `src/main` changes, so no run id moves.**

## What this does not decide

- **The forked JVMs' own start-up warnings** (`sun.misc.Unsafe`, native access, class sharing). They are not test output, and silencing them takes JVM flags with trade-offs of their own.
- **Whether `trimStackTrace` should change** what a failing test's report shows. It is untouched.
