# ADR-326 — A command whose output file cannot be written or read refuses the start in a line of its own, and only a program that cannot be started is Docker not installed

- **Date**: 2026-10-29
- **Status**: accepted on 2026-10-29.
- **Decided**: under the operator's delegation of 2026-10-29.
- **Amends**: [ADR-323](0323-vespera-starts-the-docker-engine-and-the-sidecars-when-they-are-not-answering-recreates-and-stops-none-and-stage-2-waits-for-a-converter-still-starting.md) §5, whose two readings of a program that cannot be started, *"A `docker` that cannot be started at all … refuses the command with §8's line naming Docker"* and *"A start command whose program cannot be started at all … counts as exit 127"*, are made of nothing else: a temporary file a command's output goes to that cannot be created or read is neither, and refuses the start with a line of its own (§1, §2). §8 gains that line (§3); its *"Neither of the last two is a fault of Docker's"* becomes *"None of the last three"*, and *"The seven exception types"* becomes *"The eight exception types"*. Its Tests gain the claims of Tests below, and `SystemProcessRunnerTest`'s runner is built on a folder of the test's. It carries a note at its head.
- **Keeps**: ADR-323 §5's output to a temporary file and not a pipe, read once the command has exited or been killed, and its 30 s limit on each command; §3's order; and §4's `up`, whose output is still read through a pipe as it is printed.
- **Keeps**: ADR-323 §10. Every class this record adds or changes is in `pipeline`, and none decides a verdict, a cache key, a cluster or text of the deliverable. No stage moves, and no pass is owed.
- **Keeps**: [ADR-141](0141-the-cli-exits-with-the-commands-exit-code-and-the-scheduler-no-feature-uses-is-removed-at-its-source.md): the refusal of §3 exits 1, as every refusal of ADR-323 §8 does.
- **Settles**: [#659](https://github.com/algernon28/vespera/issues/659).

## Context

ADR-323 §5 sends the output of each command of the engine's section, the engine check and each start command, to a temporary file, read once the command has ended, because a pipe would hold the start for as long as a process the command started lives. `ProcessRunner.run` declares one exception, `IOException`, for a program that cannot be started. The file is created, and read, inside the same method, and its failure leaves the method as the same `IOException`. The starter reads every `IOException` from `run` as a program that cannot be started: where the program is `docker` it refuses with *"no docker command was found"*, and for any other program it counts the command as exit 127 and tries the next.

Neither reading is true of the file. Docker is installed, and what failed is the folder. The next start command writes its output to the same folder, so trying it meets the same failure under another name.

The folder is `java.io.tmpdir`, where ADR-323 §4 writes the compose files out at step 4, before any command runs, and a folder that will not take them is already refused there (`ComposeFilesUnwritableException`). What is left to reach a command's file is a drive that fills, or a folder whose permissions change, between step 4 and the command, and a read of the file that fails.

## Decision

### 1. The runner reports the file apart from a program that cannot be started

`ProcessRunner.run` throws `CommandOutputFileException` when the temporary file its command's output goes to cannot be created or cannot be read, and `IOException` only where the program cannot be started. `CommandOutputFileException` is a checked exception of `pipeline` that extends `Exception` and not `IOException`. It carries the command as it was given, the folder the file was to be created in, and the `IOException` that failed as its cause, and `run` declares it beside `IOException`.

- **Created before the program is started.** A file that cannot be created throws it, and nothing is started, whether or not the program exists.
- **Read after the command has ended.** A file that cannot be read once the command has exited, or has been killed and waited for, throws it too. The exit code is not handed back: the starter refuses rather than act on half an outcome.
- **A file that cannot be deleted is not a failure.** It is left to be deleted at exit, as ADR-323's runner already leaves it.
- **The folder is given to the runner.** `SystemProcessRunner` is built on the folder its files are created in, and has no other constructor. The application's is `java.io.tmpdir`, the folder the compose files are written out to; a test gives it one of its own.
- **`runToTheEnd` is unchanged.** `up` has no file: its output is read through a pipe as it is printed (ADR-323 §4).

**Why a type that is not an `IOException`.** A subclass of it is caught by every `catch (IOException …)` that does not name it first, and read as a program that cannot be started again, by the next caller written, with nothing to say so. A checked type of its own makes the compiler ask every caller of `run` what it does with it.

### 2. The starter refuses with it, whatever the program

`SidecarStarter` reads `CommandOutputFileException` from every command of ADR-323 §5, the engine check and each start command alike, and refuses the start at once with §3's line:

- **not as Docker not installed**, where the program is `docker`;
- **not as exit 127**, and the next start command is not tried, because it would write its output to the same folder;
- **not as an engine that did not answer**, where it comes while the engine is waited for.

An `IOException` keeps ADR-323 §5's two readings: `docker` not installed, and any other program exit 127.

### 3. ADR-323 §8's ninth line

- **A command's output file cannot be written or read** (`CommandOutputFileUnusableException`), `<command>` being the command as run, its parts joined by single spaces, `<reason>` the message of what failed, or the kind of failure where it gave none, as for the lock file, and `<folder>` the absolute path of the folder the file was to be created in, `java.io.tmpdir` in the application:
  > the sidecars are not answering and Vespera cannot start them: the temporary file the output of <command> goes to cannot be written or read (<reason>). Make room in <folder>, or make it writable, and run the same command again.

A line of its own and not `ComposeFilesUnwritableException`'s, which says the compose files cannot be written out: by then they have been. The advice is that line's, because the folder is the same. Like the lock file's and the compose files' lines, it names no fault of Docker's, and it is a refusal like the rest: one line on standard error, exit 1, before the job starts or the labeller is reached. `CommandOutputFileUnusableException` extends `SidecarStartRefusedException`, which makes eight types for §8's nine lines, `DockerEngineDidNotStartException` having two.

## Known limits

- **The JDK opens the file a second time.** `ProcessBuilder.start` opens the file it is given for the output before it starts the program, and a failure there leaves it as the `IOException` it throws for a program that cannot be started, the first wrapped in the second. A file the runner created a moment before and that cannot then be opened is still read as ADR-323 §5 reads a program that cannot be started. Telling the two apart would mean reading the cause inside the JDK's exception, whose shape the JDK does not document.

## Alternatives rejected

- **`ComposeFilesUnwritableException`'s line.** It names files that were written out, and the operator would look for a fault in them.
- **`CommandOutputFileException` as a subclass of `IOException`.** §1: one `catch` written in the wrong order reads it as a missing program again.
- **A file that cannot be read handing back the exit code, with the reason as the command's output.** The engine check reads nothing but the exit code, so the start could go on. It would be a second rule for one file, and the next command writes to the same folder that has just failed.
- **The runner throwing the refusal itself.** `ProcessRunner` is the seam between the starter and the operating system and knows no refusal; the lines of ADR-323 §8 are the starter's.
- **Trying the command again.** A full drive or an unwritable folder is not what a second attempt a moment later changes.

## Consequences

- **`ProcessRunner.run` declares `CommandOutputFileException`** beside `IOException`; `ScriptedDocker` declares it too, and a test can script any command to throw it.
- **`SystemProcessRunner` is built on its folder**, and `SidecarStarter.forTheApplication` builds it on `java.io.tmpdir`.
- **`PipelineHoldsOnlyTheRulesOnRecordTest` lists `CommandOutputFileException` and `CommandOutputFileUnusableException`** as holding no rule, with the starter's other classes.
- **No run id moves** (ADR-323 §10).

## What this does not decide

- **An interrupted wait for a command.** `SystemProcessRunner` throws `IOException` where the thread waiting for a command is interrupted, and the starter reads that as a program that cannot be started, as ADR-323 has it.

## Tests

Amended, under `src/test`, in `io.algernon.vespera.pipeline` and needing no Docker:

- **`SidecarStartTest`**:
  - the engine check's output file cannot be written: §3's line, naming the engine check, its reason and its folder, and not Docker not installed; no other command is run;
  - a start command's output file, of a program other than `docker`, cannot be read: §3's line naming that command, not exit 127, and the next start command is not tried.
- **`ScriptedDocker`**: `outputFileFails`, which has a command throw `CommandOutputFileException` with the folder and reason given.
- **`SystemProcessRunnerTest`**, its runner built on a folder of the test's:
  - a folder for the files that is a regular file: `CommandOutputFileException`, which is not an `IOException`, naming the command and that folder, with the `IOException` that failed as its cause; the program is one no system has, so the file was asked for before the program was started;
  - a command that deletes its own output file and exits 0: `CommandOutputFileException`, after the command has run. Not on Windows, where a file another process holds open cannot be deleted;
  - a program that cannot be started still throws an `IOException`, and not `CommandOutputFileException`.
- **`PipelineHoldsOnlyTheRulesOnRecordTest`**: the two classes, as holding no rule.
