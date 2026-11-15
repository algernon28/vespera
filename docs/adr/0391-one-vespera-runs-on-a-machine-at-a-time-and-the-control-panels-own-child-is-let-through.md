# ADR-391 — One Vespera runs on a machine at a time, and the control panel's own child is let through

- **Date**: 2026-11-15
- **Status**: accepted on 2026-11-15.
- **Decided**: by the operator, in [#818](https://github.com/algernon28/vespera/issues/818): that only one Vespera runs on a machine at a time, a second control panel refusing to open and `vespera run`, `vespera label` and `vespera publish` started in a terminal refusing while a control panel or another invocation is active (§1, §4); that the control panel's own child is always let through (§3); that `vespera serve` is exempt, using no sidecar (§4); and that a shut-down whose engine stop meets an unusable output file once the sidecars are stopped says so, in the form ADR-390 §6 already gives a refused engine stop (§6). By Claude under the operator's delegation: how the guard is held, a lock file in the folder the corpus-root locks lie in (§2); how the control panel's child is let through, and what holds the machine once the control panel that started it is gone (§3); where the lock is taken and what a refusal leaves behind (§4); that the control panel gives its child the folder of the locks as an absolute path, and that a value of the folder's variable that is no path refuses only a start that takes the lock (§2); the lines (§5); and how the control panel reads `vespera.sidecars.wait-at-most` (§9).
- **Amends**: [ADR-390](0390-vespera-uses-docker-engine-alone-the-control-panel-starts-it-as-it-opens-and-a-button-stops-the-sidecars-then-the-engine.md) §5: the window opens only once the control panel holds the machine lock, so the engine is started as it opens, and the sidecars stopped as it closes and by the button, with no other Vespera running on the machine but the control panel's own child, which each stops first (§7). §6: the line of a shut-down whose engine stop cannot write or read its output file, once the sidecars are stopped, is *"The sidecars were stopped, but Docker was not shut down: &lt;reason&gt;."* (§6). §4: the control panel reads `vespera.sidecars.wait-at-most` in the forms the jar reads it in, a bare number being milliseconds and no longer seconds, a value no form reads 180 s and a value under a second one second (§9). It carries a note at its head.
- **Amends**: [ADR-342](0342-the-control-panel-is-a-javafx-window-inside-the-vespera-jar-opened-by-the-jar-started-with-no-argument-and-its-loopback-server-and-pages-are-gone.md) §2: the jar's entry point takes the machine lock before it hands a start to the command line or to the window, and a start refused writes one line on standard error and exits 1 (§4). It carries a note at its head.
- **Amends**: [ADR-279](0279-one-vespera-run-per-corpus-root-by-a-lock-on-a-file-named-for-its-real-path-outside-every-working-directory.md) §3: the folder of the corpus-root locks also holds `machine.lock` and `machine-child.lock` (§2), as it holds `sidecars.lock` (ADR-323 §6). Neither is named `vespera.lock` or `vespera.db`, nor by sixteen hexadecimal digits, so neither is taken for a working directory or a corpus root's file. It carries a note at its head.
- **Amends**: [ADR-278](0278-the-control-panel-starts-the-jar-as-its-child-reads-its-output-to-the-end-and-stops-only-its-own-child.md) §4 and §7: a control panel does not open while another invocation is active, so §4's invocation the control panel did not start, and §7's control panel started again beside the child it left, are refused at the opening (§4). §4's reading stays, for what the machine lock does not see (Consequences). §1, as ADR-292 leaves it: the child's environment also holds `VESPERA_CORPUS_ROOT_LOCK_FOLDER`, the control panel's folder of the locks as an absolute path (§2). It carries a note at its head.
- **Amends**: [ADR-307](0307-the-child-the-control-panel-started-ends-with-it-wherever-its-end-runs-the-jvms-shutdown-hooks.md) §2: a control panel started again while a child of the one force-ended still runs refuses to open, quoting that child's line (§3, §5). It carries a note at its head.
- **Keeps**: [ADR-177](0177-one-invocation-per-working-directory-and-a-locked-database-file-is-named.md), ADR-279 §1 to §2 and §4 to §9, and [ADR-323](0323-vespera-starts-the-docker-engine-and-the-sidecars-when-they-are-not-answering-recreates-and-stops-none-and-stage-2-waits-for-a-converter-still-starting.md) §6: the working-directory lock, the corpus-root lock and `sidecars.lock` are unchanged, each still taken where it was. The control panel's start of the engine and its child's start of the sidecars still meet at `sidecars.lock`.
- **Keeps**: [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3 as ADR-390 §4 leaves it: the control panel names `DockerEngine` and no other class of the jar. The entry point, which names both (ADR-342 §2), takes the lock for it.
- **Keeps**: [ADR-333](0333-a-plugin-inside-the-jar-draws-one-rendering-and-delivers-it-to-one-destination-a-publish-names-its-destinations-and-the-website-is-refreshed-in-one-folder-and-served-on-loopback.md) §8: `vespera serve` is handed on before any lock, and takes none.
- **Keeps**: [ADR-292](0292-the-control-panel-remembers-its-launch-values-in-a-file-of-the-users-own-under-the-temp-directory-and-passes-five-through-the-childs-environment.md) §4: the folder of the locks is no launch value; it is given to the child beside the five, and is neither shown nor stored.
- **Keeps**: [ADR-047](0047-the-pipeline-never-blocks.md): a refusal is one line and an exit; nothing waits for the lock or asks a question.
- **Keeps**: [ADR-141](0141-the-cli-exits-with-the-commands-exit-code-and-the-scheduler-no-feature-uses-is-removed-at-its-source.md): a refused start exits 1.
- **Settles**: [#818](https://github.com/algernon28/vespera/issues/818).
- **Rests on**: the measurements below, made on Linux with JDK 26.

## Context

ADR-390 §5 stops the sidecars as the control panel's window closes. A second control panel, or a `vespera run` started in a terminal, may be using them then; closing the window stops them anyway, and that invocation's stage 2 waits out ADR-175's bound and fails. ADR-177 and ADR-279 exclude a second invocation on one working directory or one corpus root, and two invocations on two of each share the sidecars all the same.

ADR-390 §6's line for an unusable output file says *"Docker was not shut down: &lt;reason&gt;"* even where the file that failed is the engine stop's, after `docker compose -p vespera stop` exited 0 and the sidecars are stopped.

### Measured

On Linux, JDK 26, with real processes and files in a temporary folder:

| What was run | What was seen |
| --- | --- |
| a JVM holding a lock on one byte of `machine.lock` starts `java -cp … ` with `ProcessBuilder` | the child's `ProcessHandle.current().parent()` is the holder's pid; its `tryLock` on the same byte answers `null`; its lock on `machine-child.lock` is taken |
| the same child started through `sh -c "true; java …"` | its parent is the shell's pid, not the holder's |
| the same child started through `bash -c "java …"` | its parent is the holder's pid: `bash` runs a lone command in its own process |
| the holder ended by `SIGKILL` while its child holds `machine-child.lock` | the child runs on, its parent now pid 1; a third process takes `machine.lock` and finds `machine-child.lock` held |
| `Files.createDirectories` on a path that is a regular file | `FileAlreadyExistsException`, its message the path |
| `Path.of` on a string holding the character NUL | `InvalidPathException`, *"Nul character not allowed: …"*; `<>\|?*"` is a relative path on Linux |

**Not measured**: anything on Windows or macOS: that `ProcessHandle.parent()` names the control panel for the child `java.exe` it starts on Windows, which the JDK reads from the system's process snapshot, and on macOS; that a lock on a byte past the end of a file one process holds answers `null` to another on macOS, which ADR-177 measured on Windows and Linux; that `Path.of` refuses `<>|?*"` on Windows, which its file names do not hold.

## Decision

### 1. One Vespera on a machine

At most one of these runs at a time on a machine, for a user: a control panel, with its own child (§3); or a `vespera run`, a `vespera label` or a `vespera publish` started anywhere else. A second is refused as it starts (§4, §5). `vespera serve`, `--help`, `--version` and a start of the command line with no command take nothing and are never refused by it, whatever the folder's variable holds: a value that is no path refuses only a start that takes the lock (§2).

### 2. The machine lock

The **machine lock** is the operating system's lock on one byte of `machine.lock`, `tryLock(Long.MAX_VALUE - 1, 1, false)`, as ADR-177 §1 and ADR-279 §4 take theirs: the holder line stays readable to every other process while it is held, the lock is held for the life of the process, the operating system releases it however the process ends, and the file is never deleted, its existence meaning nothing.

**The folder** is the one the corpus-root locks lie in, read the way the control panel reads it (ADR-302 §2): the one `VESPERA_CORPUS_ROOT_LOCK_FOLDER` in the environment names where it is not blank, and `<user home>/.vespera-root-locks` otherwise. It is read from the environment alone, because the lock is taken before any configuration is read (§4). The folder is created where it is not there.

**The child is given the folder, absolute.** The control panel's child starts in its working directory (ADR-278 §1), so a relative value it inherited would name another folder than the control panel's, and the child would find neither the control panel's machine lock nor its corpus-root locks. When the control panel starts a child it sets `VESPERA_CORPUS_ROOT_LOCK_FOLDER` in the child's environment to the absolute path of the folder it resolved itself, a relative value resolved against the control panel's own current directory, as it gives the deliverable root absolute (ADR-292 §4). So the two read one folder.

**A value that is no path** this system can hold, as `<>|?*"` is none on Windows, is read as a path only when the lock is taken: a start that takes none (§1) never reads it, and one that takes it is refused in §5's second line, `<folder>` being the value as written. The control panel's window, which reads the same variable, is not reached: its start was refused first (§4).

**The holder line** is ADR-177's three fields, then the kind:

```
pid=<pid> started=<ISO-8601 date and time, with offset, to the second> command=<the arguments, joined by single spaces> kind=<kind>
```

one UTF-8 line ended by the platform's line separator, line breaks in the arguments written as spaces, and `<kind>` one of `control-panel`, `run`, `label` and `publish`, the last field, so that it is read as the line's last word whatever the arguments hold.

**Taking it.** The holder takes `machine.lock`; then takes `machine-child.lock`, on the same byte, and lets it go at once, to learn that no child of a control panel still holds it (§3); then writes its line. Where either is held by another process, or by this one, the start is refused (§5), and a lock it took is let go. Where the folder or a file cannot be created, opened or locked, the start is refused too (§5): nothing starts unguarded.

### 3. The control panel's own child

A start that finds `machine.lock` held is **let through** where the holder line's `kind` is `control-panel` and its `pid` is this process's parent, `ProcessHandle.current().parent()`: the control panel starts its child with `ProcessBuilder` and nothing between (ADR-278 §1, ADR-288). The holder being alive, as its lock shows, its pid is no other process's. A process whose parent is not given, or is another, or a holder line that does not parse, is refused (§5), as another Vespera, in §5's first line: a `pid` too large for a number is a line that does not parse, and not a lock that cannot be taken.

Let through, the child takes `machine-child.lock`, as §2 takes `machine.lock`, writes its own holder line there, and holds it until it ends; where another holds it, it is refused (§5). It never writes `machine.lock`.

**Why the child holds a file of its own.** A forced end of the control panel runs no hook and leaves its child running (ADR-307 §2), and the control panel's lock goes with its process (Measured). Without a lock of the child's, the next start would find the machine free while that child still uses the sidecars, and a control panel opened then would stop them as it closed. With it, every start until that child ends is refused, its line naming the child (§5).

**Why its parent, and nothing passed.** A variable or a token the control panel set in its child's environment could be set by hand in a terminal, and is inherited by whatever the child starts. The parent is a fact of the process, read from the system, and no argument, option or variable of the child changes for it (ADR-278 §1).

### 4. Where it is taken

**By the jar's entry point, `EntryPoint`**, before it hands a start on:

- **A start of the command line whose first argument**, once a first `--headless` is dropped (ADR-342 §2), **is `run`, `label` or `publish`** takes it with that kind, before `VesperaApplication.main` is called: before the context, the working-directory lock, the working directory's creation, `vespera.db`, `logs/vespera.log` and `report.json`. A refused start writes nothing anywhere and opens nothing. The first argument is read as `vespera serve`'s is (ADR-333 §8), and picocli's model is not built: no option of the root command but `--help` and `--version` exists, so a command line that names a command names it first.
- **A start with no argument**, once a display is found and before `ControlPanel.open()`, takes it as `control-panel`. A refused start tries no window, writes the line on standard error and exits 1, as a start with no jar does (ADR-342 §2). With no display it takes nothing: the jar then answers as its command line with no command.
- **`vespera serve`** is handed on before either (ADR-333 §8), and takes nothing: it uses no sidecar.

The lock is held until the process ends. `VesperaApplication.main` started on its own, by an IDE's run configuration, and a context built by Spring's test support take none, as ADR-279 §7 says of the corpus-root lock.

**The control panel names nothing new.** `EntryPoint` is the one class that names both the control panel and the jar's code (ADR-342 §2), so the control panel keeps to `DockerEngine` alone (ADR-390 §4). `MachineLock`, `AnotherVesperaRunsException` and `MachineLockUnavailableException` are in `pipeline`.

### 5. The lines

Each is one line on standard error, written by `EntryPoint` itself and not logged, since no working directory is open; the exit code is 1. `<then>` is `run the same command again` for a command and `open the control panel again` for the control panel.

- **Another Vespera holds the machine**, `<holder line>` being the line of `machine.lock`, or of `machine-child.lock` where that one is held, quoted whole, and `its details could not be read` where it is blank or cannot be read:
  > another Vespera is running on this machine (&lt;holder line&gt;); wait for it to end, or stop it or close its control panel, then &lt;then&gt;. One Vespera runs on a machine at a time, so that none stops the sidecars another is using.
- **The lock cannot be taken**, `<folder>` absolute, or the variable's value as written where it is no path (§2), and `<reason>` the exception's message, or its class's simple name where it gives none:
  > the machine lock could not be taken in the folder &lt;folder&gt; (&lt;reason&gt;); make that folder one this user can write, or name another in VESPERA_CORPUS_ROOT_LOCK_FOLDER, then &lt;then&gt;. Vespera does not start while it cannot tell whether another Vespera is running.

### 6. ADR-390 §6: an engine stop's output file

Once step 1 of the shut-down has exited 0, the sidecars are stopped. Where step 2's command then cannot write or read its output file, the line is:

> The sidecars were stopped, but Docker was not shut down: &lt;reason&gt;.

`<reason>` being ADR-326's, as ADR-390 §6 has it. The lock is taken before step 1, so a lock that cannot be opened is always said before any command, by ADR-390 §6's *"Docker was not shut down: &lt;reason&gt;."*, as is step 1's own output file. The stop of the sidecars alone keeps its line.

### 7. The window, as ADR-390 §5 leaves it

Nothing of the window's code changes. It opens only once the control panel holds the machine lock (§4), so the start as it opens, the stop as it closes and the button each run with no other Vespera on the machine but the window's own child, which closing and the button stop first, as ADR-390 §5 says. Closing therefore never stops sidecars another invocation is using, and is not made to skip its stop for another holder of a lock.

### 8. No run id moves

`MachineLock` and its two exceptions are in `pipeline`, which no stage's version names (ADR-222), and `EntryPoint` is in no module. None decides a verdict, a cache key, a cluster or text of the deliverable. No stage moves, and no pass is owed.

### 9. ADR-390 §4: the wait, read as the jar reads it

The control panel reads `vespera.sidecars.wait-at-most`, from the sources ADR-390 §4 names, in the forms the jar reads it in through Spring's `DurationStyle.detectAndParse` in `SidecarStartSettings.from`, which the control panel may not name (ADR-390 §4):

- a bare number is milliseconds;
- a number followed by `ns`, `us`, `ms`, `s`, `m`, `h` or `d`, in any case, is that many of the unit;
- a value beginning with `P`, in either case, after an optional sign, is ISO-8601, as `Duration.parse` reads it;
- a value neither form reads, a number too large for a long among them, is 180 s, the jar's own default;
- a value under 1 s is 1 s.

`ControlPanel.waitAtMostOf` holds it. It breaks the control panel's earlier reading, a bare number in seconds with `s`, `m` or `h` in lower case and anything else 180 s, which disagreed with the jar's: `180` was three minutes to the window and 180 ms to the jar.

## Alternatives rejected

- **Closing skips its stop while another lock is held** (the working-directory, a corpus-root or `sidecars.lock`). Two control panels and terminal invocations would still share the sidecars, the button would still stop them under another invocation, and a holder of none, a `vespera label --auto` between its steps, would be missed.
- **The lock taken by each command once picocli has parsed it**, as the corpus-root lock is. A refused start would by then have created its working directory, opened `vespera.db` and written `report.json` and `logs/vespera.log`, which the control panel reads for an invocation that never ran.
- **The lock in an environment listener**, as the working-directory lock is. It needs the command before the context, which is what the entry point already reads.
- **Waiting for the lock**, as `sidecars.lock` is waited for. A control panel can stay open for days; an invocation would wait for it without a word.
- **A variable or token in the child's environment.** §3: a terminal could set it, and what the child starts would inherit it.
- **Only the control panel's lock, the child holding none.** §3: a forced end of the control panel would leave the machine free while its child runs.
- **The child left to inherit the variable.** §2: a relative value would be read against the child's working directory, another folder than the control panel's.
- **The wait's earlier reading in the control panel, a bare number in seconds.** §9: one value would be two waits, the window's and the jar's.
- **The folder read through the jar's configuration.** The control panel reads it from the environment alone (ADR-302 §2), and a folder named only in a configuration file would put the two in different folders, where neither sees the other.
- **The machine-wide folder, `C:\ProgramData` or `/var/lock`.** ADR-279's reason: a file one user creates there cannot be locked by another without access-control work; and the folder of the corpus-root locks is already the user's.
- **A dialog for a second control panel.** It needs the toolkit started, which a start with no jar does not do either; the line and exit 1 are that start's.

## Consequences

- **A second control panel does not open**, and a `vespera run`, `vespera label` or `vespera publish` started while a control panel or another of them is active is refused in one line, exit 1, having written nothing.
- **`vespera run --help`, `vespera label --help` and `vespera publish --help`** are refused too while another Vespera runs: their first argument is the command.
- **The control panel's own child runs**, and, where a forced end left one running, holds the machine until it ends.
- **The control panel's window shows an invocation it did not start** (ADR-278 §4) only where the machine lock did not see it: two users of one machine, each with a folder of their own; a folder named only by a configuration file or a system property; or `VesperaApplication.main` started on its own. These are its known limits, as ADR-279's are.
- **The README's "Starting the control panel"** opens on it: one Vespera runs on a machine at a time, the control panel's own invocations and `vespera serve` excepted. It quotes the refusal by its first words, *another Vespera is running on this machine*, and not whole.
- **The README's "Where things live"** names `machine.lock` and `machine-child.lock`, in the folder of the corpus-root locks.
- **A wait written as a bare number is read by the control panel in milliseconds** (§9): `180` is one second there now, the least it takes, as the jar has read it all along. A wait written with its unit, as the jar's `application.yaml` writes `180s`, reads as before.
- **`CONTEXT.md` gains the machine lock**, beside the working-directory lock and the corpus-root lock.
- **`AGENTS.md`'s "Where it stands"** names the machine lock where it names the control panel and `vespera serve`.

## What this does not decide

- **Two users on one machine.** Each has a folder of their own, and the sidecars are the machine's; ADR-279 leaves the same case.
- **A check that the control panel's child is a Vespera**, beyond its parent: the control panel starts nothing else that takes the lock.
- **Showing the refusal of a second control panel in a window.**
- **A control panel force-ended between its child's two `tryLock`s.** The child has found `machine.lock` held by its parent and has not yet taken `machine-child.lock`; a start in that interval finds the machine free and `machine-child.lock` free, and runs beside the child.
- **A dead control panel's pid reused.** Between a new holder's lock on `machine.lock` and its writing its line, the file still holds the line of the control panel that held it before; a start whose parent was given that control panel's pid by the system is let through.

## Tests

| Method | What it holds |
| --- | --- |
| `pipeline.MachineLockTest.takingTheMachineRecordsTheHolderAndItsKind` | §2: the folder is created and holds only `machine.lock` and `machine-child.lock`; `machine.lock` holds one line of §2's shape, this pid, the start to the second, the arguments with their breaks as spaces, and the kind |
| `pipeline.MachineLockTest.aSecondTakeInThisProcessIsRefusedInTheLineOfItsKind` | §2, §5: refused with the line whole, `then` by kind, the holder line left as it was; a blank holder line said as `its details could not be read` |
| `pipeline.MachineLockTest.aMachineAnotherProcessHoldsIsRefusedWhateverTheKinds` | §1, §5: a second process holding as `run` refuses the control panel; one holding as `control-panel` refuses a start whose parent is another |
| `pipeline.MachineLockTest.theControlPanelsOwnChildIsLetThroughAndHoldsTheChildsFile` | §3: a start whose parent holds as `control-panel` is let through, writes `machine-child.lock` and leaves `machine.lock` as it was; a real child of such a holder is let through |
| `pipeline.MachineLockTest.aChildOfAnInvocationThatIsNoControlPanelIsRefused` | §3: a start whose parent holds as `run` is refused |
| `pipeline.MachineLockTest.aChildLeftRunningByAControlPanelForcedToEndHoldsTheMachine` | §3: the holder killed, its child running, a start is refused naming the child's line; the child killed, it is taken |
| `pipeline.MachineLockTest.aKilledHolderLeavesTheMachineFree` | §2: after a kill the next take succeeds and rewrites the line |
| `pipeline.MachineLockTest.aFolderThatCannotBeMadeRefusesTheStart` | §2, §5: a regular file in the folder's place refuses in the line naming the folder and the variable |
| `pipeline.MachineLockTest.aChildWhoseFileAnotherHoldsIsRefusedQuotingThatHolder` | §3: a start the control panel's line lets through, `machine-child.lock` held by the control panel's own child, is refused as another Vespera quoting that child's line, which is left as it was |
| `pipeline.MachineLockTest.aStartTheHolderLineCannotLetThroughIsRefusedAsAnotherVespera` | §3, §5: a start with no parent given, a holder line that is not blank and does not parse, and a control panel's line whose `pid` is too large for a long, each refused in the first line, quoting the line whole |
| `pipeline.MachineLockTest.aFolderValueThatIsNoPathRefusesTheTakeQuotingTheValue` | §2, §5: the lock named by a value holding NUL is made without reading it, and refuses a command and a control panel in the second line, the value as written |
| `pipeline.MachineLockTest.aLockWhoseHolderLineCannotBeWrittenIsLetGo` | §2: `machine.lock`, and for a start let through `machine-child.lock`, linked to `/dev/full`: refused in the second line, and the lock it took let go; skipped where the system has no `/dev/full` or makes no link |
| `pipeline.MachineLockTest.onlyRunLabelAndPublishNameAKind` | §1, §4: the first argument `run`, `label` or `publish` names its kind; `serve`, `--help`, `--version`, none and any other name none |
| `TheEntryPointTakesTheMachineLockTest.aCommandThatUsesTheSidecarsTakesTheMachineLockFirst` | §4: `run`, `label` and `publish`, with `--headless` first or not, ask the lock for their kind before the command line starts; `--version`, `--help`, `serve` and `--headless` alone ask nothing |
| `TheEntryPointTakesTheMachineLockTest.aRefusedCommandSaysOneLineAndExitsOneBeforeTheCommandLine` | §4, §5: the line alone on standard error, exit 1, no command line |
| `TheEntryPointTakesTheMachineLockTest.theControlPanelTakesTheMachineLockAfterTheDisplayAndBeforeTheWindow` | §4: with a display, the lock as `control-panel`, then the window; with none, no lock |
| `TheEntryPointTakesTheMachineLockTest.aSecondControlPanelIsRefusedInOneLineAndTriesNoWindow` | §4, §5: the line, exit 1, no window and no command line |
| `TheEntryPointTakesTheMachineLockTest.aFolderThatIsNoPathRefusesOnlyAStartThatTakesTheLock` | §1, §4: a lock that would refuse for `<>\|?*"`: `--version`, `--help` and a start with no display ask nothing and start the command line; `run` is refused in its line, exit 1 |
| `ui.TheChildIsGivenTheLockFolderAbsoluteTest.aRelativeLockFolderReachesTheChildAbsolute` | §2: a control panel's state given a relative folder starts a child whose environment names it absolute, against the control panel's current directory |
| `ui.TheControlPanelsDockerSettingsTest.theWaitIsReadInTheJarsFormsAndThreeMinutesWhereNoFormReadsIt` | §9: `PT3M`, `5M` and `2d` read; `180` and `500ms` one second; a value of no form and `99999999999999999999` 180 s |
| `CliExitIT.aCommandStartedWhileAControlPanelRunsIsRefusedBeforeItOpensAWorkingDirectory` | §4, §5: the packaged jar, with a second process holding as `control-panel` in the folder its environment names: `run` exits 1, its one line of the jar's own on standard error quoting the holder line, and its working directory never made; `serve` is not refused by it |
| `pipeline.DockerShutDownTest.anEngineStopsUnusableOutputFileSaysTheSidecarsWereStopped` | §6: the engine stop's output file failing after the compose stop, the line whole, both commands run |

Added beside them, holding ADR-390 as it stands, each linked to it:

| Method | What it holds |
| --- | --- |
| `pipeline.DockerShutDownTest.closingStopsTheSidecarsWhereEitherAnswersAndNothingWhereOneIsOnAnotherMachine` | ADR-390 §3: one sidecar answering, the compose stop alone; a base URL on another machine, no command |
| `pipeline.DockerShutDownTest.anUnusableLockOrOutputFileBeforeTheSidecarsStopIsOneLineOfEachStop` | ADR-390 §6: the lock's line and the compose stop's output file's line, for the shut-down and for the stop of the sidecars alone |
| `pipeline.DockerShutDownTest.bothStopsWaitForTheSidecarLockAnotherHolds` | ADR-390 §3: each stop waits for `sidecars.lock`, past the bound, and runs its commands once it has it |
| `pipeline.SidecarStartTest.dockerNotInstalledNamesTheEngineChecksProgramOnEachSystem` | ADR-390 §6: Docker not installed names `docker` on Linux and macOS and `wsl` on Windows, for the start of the sidecars and the control panel's |
| `ui.TheControlPanelsDockerSettingsTest.theDistributionAndTheWaitAreReadFromTheSourcesInTheirOrder` | ADR-390 §4: the environment, then `.env`, then the jar's `application.yaml` |
| `ui.TheControlPanelsDockerSettingsTest.aConverterTheEnvironmentNamesOnAnotherMachineStartsNothingWhereTheLaunchValuesAreBlank` | ADR-390 §4: a blank address is the environment's, and on another machine no lock is taken and nothing is run |

and `ui.TheShutDownDockerButtonTest.closingTheWindowStopsTheSidecarsAndNotTheEngine` holds that the window is still shown at the moment the sidecars' stop is called.

Amended:

- **`EntryPointTest`** calls `EntryPoint.start` with the lock and the exit as seams.
- **`CliExitIT`** names a folder of its own in `VESPERA_CORPUS_ROOT_LOCK_FOLDER` for every launch.
- **`PipelineHoldsOnlyTheRulesOnRecordTest`** lists `MachineLock`, `AnotherVesperaRunsException` and `MachineLockUnavailableException` as holding no rule.

`pipeline.HoldsTheMachine` is the second process the unit and integration tests start, and `pipeline.TakesTheMachine` the child it starts; `ui.ALockFolderWritingChildForTests` is the child the control panel's state starts, writing the folder it was given. Every test names a temporary folder.

What no test shows is §3 on Windows and macOS (Measured).

## What the implementation owes

Beside turning the tests above green: in `pipeline`, `MachineLock`, with `FILE_NAME`, `CHILD_FILE_NAME`, `FOLDER_VARIABLE`, the enum `MachineLock.Kind` (`CONTROL_PANEL`, `RUN`, `LABEL`, `PUBLISH`, `word()`, `ofTheCommand(String[])`), `MachineLock(Path)`, a package-private `MachineLock(Path, OptionalLong)` naming the parent's pid, `ofThisProcess()`, `take(Kind, String[])`, `refusal(Kind, String[])` answering the line or empty, and a package-private `release()`; `AnotherVesperaRunsException` and `MachineLockUnavailableException`, each with §5's line as its message; `EntryPoint.start(String[], Supplier<DisplayPresence.Answer>, Function<MachineLock.Kind, Optional<String>>, Supplier<ControlPanel.Opening>, Consumer<String[]>, PrintStream, IntConsumer)`, whose last seam is the exit, and `EntryPoint.main` passing `MachineLock.ofThisProcess()`'s refusal and `System::exit`; `SidecarStarter.shutDown`'s line of §6; and the README, `CONTEXT.md` and `AGENTS.md` lines of Consequences. Then, for §1 to §3 and §9: a package-private `MachineLock.ofTheFolderNamed(String named, String userHome)`, which `ofThisProcess()` calls with the environment's value and `user.home`, never throwing, and reading the value as a path only in `take`, a value that is no path refused in §5's second line quoting it as written; a holder line whose `pid` does not parse as a long read as one that lets nothing through; the child's lock on `machine-child.lock` let go where its holder line cannot be written; `VESPERA_CORPUS_ROOT_LOCK_FOLDER` set to the absolute folder in the environment of every child the control panel starts, `ChildCommandTest`'s claims on the whole environment amended where it is added through `ChildCommand`; `ControlPanel.waitAtMostOf` reading a number too large for a long as 180 s; and the two README lines of Consequences.
