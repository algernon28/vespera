# ADR-281 — The closing line is the last line of output, and two library loggers write to the console only at WARN

> **Partly amended — see [ADR-289](0289-a-plain-tasklet-step-that-fails-with-an-exception-ends-the-output-on-one-closing-line-of-its-own.md).** Of What this does not decide, its first entry: a plain tasklet step that fails with an exception ends the output on a closing line of its own.

- **Date**: 2026-10-19
- **Status**: accepted on 2026-10-19.
- **Keeps**: [ADR-158](0158-the-operator-starts-the-sidecars-from-compose-yaml-and-the-packaged-jar-starts-none.md)'s *The last line*: the operator reads the last line of the output for the value to set next. §1 holds it of the packaged jar.
- **Keeps**: [ADR-270](0270-every-log-vespera-writes-is-in-the-working-directorys-logs-folder-the-invocation-account-among-them.md) and [ADR-093](0093-logging-is-explicit-and-process-scoped-console-plus-rolling-file-per-item-and-per-step-at-info.md)'s two appenders: `logs/vespera.log` keeps every line at INFO, the two loggers of §1 among them. Only the console loses lines.
- **Keeps**: [ADR-141](0141-the-cli-exits-with-the-commands-exit-code-and-the-scheduler-no-feature-uses-is-removed-at-its-source.md): `SpringApplication.exit` still closes the context after the command returns, and the process still exits with the command's exit code.
- **Amends**: [ADR-278](0278-the-control-panel-starts-the-jar-as-its-child-reads-its-output-to-the-end-and-stops-only-its-own-child.md) Context's *What follows the closing line*, §5's sentence on #579, and its *What this does not decide* entry on the closing line. It carries a note at its head.
- **Read with**: ADR-278 §5, which shows how an invocation ended with the last line of output that is not blank.
- **Settles** [#579](https://github.com/algernon28/vespera/issues/579).

## Context

**The closing line is the contract.** The README tells the operator to read the last line of the output, which names what to set next (README, *You will be told which value is missing, every time*; ADR-158), and `docs/running-stage-by-stage.md` says the same under *How to tell where you are*. The control panel shows the last line of output that is not blank, standard error merged into standard output, as how an invocation ended (ADR-278 §5). Each invocation has one closing line of its own: `vespera run`'s line naming the next value, logged by `VesperaCommand$Run`; `vespera label`'s two lines on standard output; a refusal's one line on standard error; and, when stage 2 fails, `ExtractionHealthCheckListener`'s line saying it failed and is not recorded as finished.

**Without §1, the packaged jar prints lines after it at two moments**, both through the root logger, which is INFO on the console and on the file:

1. **When the context closes.** `VesperaApplication.main` calls `SpringApplication.exit`, which closes the context after the command has returned and printed its closing line. Closing the datasource makes Hikari log two INFO lines, `HikariPool-1 - Shutdown initiated...` and `HikariPool-1 - Shutdown completed.`, through `com.zaxxer.hikari`. They follow the closing line of every invocation that has built a context: exit 0, exit 1 and a `label` refusal, exit 2.
2. **When the job ends.** `jobOperator.start` returns only after Spring Batch's `TaskExecutorJobLauncher` has logged `Job: [...] completed with the following parameters: [...] and the following status: [...]` at INFO. A step that fails logs its closing line inside the job, so on a failure of stage 2, exit 1, the job's line comes after `ExtractionHealthCheckListener`'s, and Hikari's two after that.

The console appender is the one `logback-spring.xml` declares for the root logger; no in-process test reads it, since a test JVM reads `logback-test.xml`.

## Decision

### 1. Two loggers write to the console only at WARN

`logback-spring.xml` declares a second console appender, `CONSOLE_WARN`, with a `ThresholdFilter` at `WARN` and the console's pattern, and two loggers with `additivity="false"`, each writing to `CONSOLE_WARN` and to `FILE`:

- `com.zaxxer.hikari`;
- `org.springframework.batch.core.launch.support.TaskExecutorJobLauncher`.

Their levels are not set, so they log at the root's INFO. On the console a line from either is shown at WARN or above; in `logs/vespera.log` every line from either is written (ADR-270). Every other logger is unchanged.

### 2. The closing line is the last line that is not blank

On standard output and standard error together, the last line that is not blank is the invocation's closing line: `run`'s line naming the next value at exit 0, stage 2's failure line at exit 1, `label`'s line naming the next value when it records answers, and its refusal at exit 2.

## Alternatives refused

- **The two loggers held to WARN for both appenders.** Hikari's and the job's lines would leave `logs/vespera.log`, which keeps every line an invocation logs (ADR-270), and the job's line is how the file says a job ended and with what status.
- **The closing line printed after the context closes**, by `VesperaApplication.main` once `SpringApplication.exit` returns. There are three paths that print one, `run`'s logger and `label`'s and the refusals' standard output and standard error, and each would carry its line out of the command to `main`; `run`'s line would be logged after the context, and the logging it configured, had closed; and at exit 1 the job's line would still follow stage 2's failure line unless that line moved out of the listener too.
- **The control panel taking the lines out of its tail.** ADR-278 refused it: it ties the control panel to the wording of libraries, and the operator at a terminal still reads the wrong last line.

## Consequences

- **Hikari's start-up lines leave the console too**, `HikariPool-1 - Starting...`, its added connection and `Start completed.`, as does the job's `launched with the following parameters` line. All stay in `logs/vespera.log`. A warning or an error from Hikari or from the job's launch is still on the console.
- **`logs/vespera.log` holds every line Vespera and its libraries log, these lines among them**; `label`'s printed lines and the refusals are on the console only.
- **No run id moves.** Only `src/main/resources/logback-spring.xml` changes; a stage's version is the last commit touching its module's folder under `src/main/java` (ADR-058, ADR-272), and no resource is in one. `StageModules` is unchanged.
- **The control panel needs no change** to show the closing line as how an invocation ended (ADR-278 §5).

## What this does not decide

- **A step of another stage that fails without a line of Vespera's saying so.** Stage 2 has `ExtractionHealthCheckListener`; a stage whose failing step logs no closing line of its own ends its output on whatever line came last before the job's, which the console does not show. Giving every stage's failure a closing line is a decision of its own.
- **Stage 1's and stage 4a's failure lines.** `ByteLevelReductionTasklet` and `RedundancyJobConfiguration` each log a line saying their stage failed and is not recorded as finished; §1 takes the job's line from after them as it does from after stage 2's, so they are the last line too, but no test holds it.
- **A line another library logs at INFO after the closing line**, should one appear. Each is held to WARN on the console by name, as §1 does, and only when it is seen.

## Tests

| Method | What it holds |
| --- | --- |
| `pipeline.TheClosingLineIsTheLastLineIT.aRunEndingOnAValueToSetPrintsItsClosingLineLast` | the packaged jar, `run` ending on a value to set: exit 0, and the last line that is not blank is `VesperaCommand$Run`'s line naming `relevance-labels.yaml` as what to answer next (§2) |
| `pipeline.TheClosingLineIsTheLastLineIT.aRunWhoseStageTwoFailsPrintsItsClosingLineLast` | `run` with no converter listening: exit 1, and the last line is `ExtractionHealthCheckListener`'s line saying stage 2 failed and is not recorded as finished, ending on its advice (§1's second logger, §2) |
| `pipeline.TheClosingLineIsTheLastLineIT.aLabelThatRecordsPrintsItsClosingLineLast` | `label` recording answers: exit 0, and the last line says the answers are recorded and names the floor to set next (§2) |
| `pipeline.TheClosingLineIsTheLastLineIT.aLabelRefusedForAMissingFilePrintsItsRefusalLast` | `label` naming a label file that is not there: exit 2, and the last line is its refusal naming that file (§2) |

Each launches the packaged jar as the control panel does (ADR-278 §1), standard error merged into standard output, against a loopback converter and a loopback stand-in of Ollama's three endpoints the run calls, so it needs no Docker daemon. The four are the invocations Context names: Hikari's lines would follow the closing line in each, and the job's line in the second.
