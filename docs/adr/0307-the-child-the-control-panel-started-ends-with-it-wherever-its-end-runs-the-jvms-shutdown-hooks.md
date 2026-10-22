# ADR-307 — The child the control panel started ends with it, wherever its end runs the JVM's shutdown hooks

- **Date**: 2026-10-21
- **Status**: accepted on 2026-10-21.
- **Settles**: the operator's ruling on [#629](https://github.com/algernon28/vespera/issues/629), the build of ADR-302's screens: the invocation the control panel started ends with the control panel, on every system.
- **Amends**: [ADR-302](0302-the-control-panel-is-a-launch-page-and-an-invocation-page-of-server-written-forms-each-report-re-served-read-only-in-a-sandbox-and-the-next-invocations-values-on-pages-of-their-own.md) §11, whose hard close, by `Ctrl+C` or by closing the terminal, leaves the child running: it ends the child, and only a forced end of the control panel's process leaves it running (§1, §2). It carries a note at its head.
- **Amends**: [ADR-278](0278-the-control-panel-starts-the-jar-as-its-child-reads-its-output-to-the-end-and-stops-only-its-own-child.md) §7, in its hard close alone, which leaves the child running anyway: a hard close that runs the JVM's shutdown hooks ends it (§1); and its refused *orderly exit of the control panel killing its child*, whose cost, the open batch of a long stage, is accepted for that hard close (Consequences). Its orderly exit, asking whether to stop the child, stays undecided (What this does not decide). It carries a note at its head.
- **Keeps**: ADR-278 §6 and [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §5: a child is ended by killing its descendants, then it, and `Child` is the only class of the control panel that ends a process; Stop stands as it is, with its confirmation, for the control panel's own child alone.
- **Keeps**: ADR-278 §4, as ADR-285 §5 and ADR-306 §1 amend it: a child left running is an invocation another start of the control panel finds holding the working directory, its holder line shown and Run and Label disabled, with no Stop offered for it.
- **Keeps**: ADR-285 §3: the control panel names `java.base` and `jdk.httpserver` alone.
- **Keeps**: [ADR-177](0177-one-invocation-per-working-directory-and-a-locked-database-file-is-named.md) §1: a killed holder's lock is released by the operating system, its line left in the file.
- **Rests on**: a reading of `controlpanel.ControlPanel`, `ControlPanelState`, `ControlPanelPages`, `Child` and `ChildCommand`, and of ADR-278's measurements on Windows. No archive, working directory or tree was opened for this record (ADR-196, ADR-283).

## Context

**How the child is started.** `ChildCommand` builds a plain `ProcessBuilder`, and `Child.start` starts it: no console and no process group of its own. The child's standard streams are pipes the control panel reads (ADR-278 §3).

**On Linux and macOS.** The child is in the control panel's process group. A terminal's `Ctrl+C` sends `SIGINT` to its foreground process group, so it reaches the child as well as the control panel, and closing the terminal sends `SIGHUP` to the jobs of its shell, the child among them. A signal sent to the control panel's process alone, a `SIGTERM` from `kill <pid>`, a logout or a service manager, reaches no other process: the child is left running, reparented, and keeps the working directory.

**On Windows.** ADR-278 measured that a parent JVM that exits leaves its child running, and that a grandchild survives its parent's kill unless it is killed first. Which console events reach the child as well as the control panel was not measured by any record.

**What ends a JVM in an orderly way.** On Linux and macOS, `SIGINT`, `SIGTERM` and `SIGHUP`; on Windows, `Ctrl+C`, the close of its console window, and a logoff or shutdown of the system, each of which the JVM receives as a request to end. Each runs the JVM's shutdown hooks before it exits. A **forced end**, `SIGKILL` (`kill -9`), Windows's `TerminateProcess` (`taskkill /F`, Task Manager's End task, `Process.destroy` and `destroyForcibly` on Windows), runs none. What the system allows a hook on Windows before it ends the process, after a console's close, a logoff or a shutdown, is a few seconds, and was not measured by any record.

**ADR-302 §11 said** a hard close leaves the child running, which is not so on Linux for `Ctrl+C` and a closed terminal, and is so for an end of the control panel's process alone, on every system.

## Decision

### 1. The control panel ends its child as its JVM ends

**As it starts, before it serves, the control panel registers one JVM shutdown hook. When its JVM ends in an orderly way, the hook kills the child the control panel started, if that child runs, its descendants first and then the child, each by `destroyForcibly`, and waits up to 2 seconds to see all of them end.**

- **The kill is Stop's**: `Child` kills the descendants the child has at that moment, then the child, as ADR-278 §6 kills them, but does not record it as a Stop; nothing reads how the child ended after the control panel has.
- **The kills are made before any wait.** The bound of **2 seconds** decides only how long the hook waits to see the processes end, which a `destroyForcibly` does within milliseconds (ADR-278 measured 6 to 18 ms on Windows). It is kept short so that the hook ends within the few seconds Windows allows after a console's close, a logoff or a shutdown. A process not seen to end within it is left to end as it was killed; the hook does not wait longer and does not kill it again.
- **A child that has ended already is left as it is**, and where none was started the hook does nothing.
- **No child is started once the hook has run.** The hook holds the control panel's state as Stop and every start hold it (ADR-306 §1): a start being made ends first and its child is then killed, and a Run, a Label, a `label-auto`, a `save-then-run` or a `save-then-label` answered on another thread after it is refused with one line, *`<Run|Label>` is not started: the control panel is ending*.
- **It holds whatever ends the JVM in an orderly way**: `Ctrl+C`, a closed terminal or console window, `SIGTERM` or `SIGHUP` sent to the control panel alone, a logoff or a shutdown. On Linux and macOS, where `Ctrl+C` and a closed terminal reach the child too, the hook finds it ending or ended, and kills what is left.

### 2. A forced end leaves the child running

**A forced end of the control panel's own process runs no hook**: `SIGKILL`, and on Windows `TerminateProcess`, by `taskkill /F`, by Task Manager's End task, or by another program. The child is then left running, as before this record. A control panel started again finds it holding the working directory and shows its holder line as ADR-278 §4 reads it, offering no Stop for it (ADR-278 §6); the operator ends it where it was started, or by its pid.

### 3. Where it is written

All in `io.algernon.controlpanel`, in `java.base` alone:

- **`ControlPanel.serve`**, which `ControlPanel.main` calls, makes the `ControlPanelState`, registers the hook on it with `Runtime.addShutdownHook`, and starts `ControlPanelServer` on `ControlPanelPages.pages(state)`.
- **`ControlPanelState.endWithTheControlPanel`**, `synchronized` on the state, marks it ending, after which `start` refuses with §1's line, and ends a child that runs through `Child.endWithin(END_BOUND)`, `END_BOUND` being 2 seconds.
- **`Child.endWithin(Duration)`** kills as `stop` kills, through one private `kill` both share, and waits for the child and then each descendant within the bound, returning whether all were seen to end.

### 4. No run id moves

No file under `src/main/java/io/algernon/vespera` changes, and no dependency or plugin is added. The control panel names no stage's version.

## Alternatives refused

- **Leaving the child running on a hard close, as ADR-278 §7 had it.** On Linux it was not what happened for `Ctrl+C` and a closed terminal, and on every system it left an invocation the operator could no longer stop from the control panel that started it.
- **Starting the child detached**, in a process group or console of its own (`setsid` on Linux, a new console or process group on Windows), so that it outlives every end of the control panel alike. It is the opposite of the ruling: the child is to end with the control panel.
- **A Windows job object with `JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE`**, which ends the child even on a forced end of the control panel. It needs a call into the Windows API, by JNI, the foreign function API or a library, and native code is outside `java.base` and `jdk.httpserver` (ADR-285 §3).
- **The child watching the control panel's pid and ending itself.** It would change the jar, to which the control panel adds no option and no command (ADR-278 §8), and the jar is started by hand too.
- **A wrapper process between the control panel and the jar.** A second process the control panel starts and has to end, and a forced end of the control panel leaves it running just the same.
- **A longer wait, or a wait until the processes end.** The kills are made first; a longer wait buys nothing on Linux and risks Windows ending the process in the middle of the hook's work.
- **Asking the operator on a hard close.** Nothing is shown while the JVM ends: the browser's page is not the control panel's to open, and the terminal may be gone.

## Consequences

- Ending the control panel with `Ctrl+C`, by closing its terminal or console window, by `kill <pid>`, a logoff or a shutdown ends the invocation it started. What that stop costs is what Stop costs, the table of ADR-302 §7: the work since the running step's last commit, the next `vespera run` continuing from there. On a stage that runs for days, that may be the open batch ADR-278 refused to throw away on an orderly exit.
- After such an end, the killed invocation's working directory is as after Stop: `vespera.lock` keeps its line until the next invocation takes the lock, and `progress.html` says it runs until its own script marks it stale (ADR-278 §6, ADR-267 §5).
- A forced end of the control panel still leaves the invocation running, shown held at the next start (§2).
- The README says both, under *Starting the control panel*.
- No run id moves (§4).

## Tests

| Method | What it holds |
| --- | --- |
| `controlpanel.TheChildEndsWithTheControlPanelTest.endingTheControlPanelInAnOrderlyWayEndsTheChildItStarted` | a control panel started as a process of its own through `ControlPanel.serve`, Run pressed on its launch page, its child alive: `Process.destroy`, a `SIGTERM` to the control panel alone, ends it, and once it has ended its child has ended too (§1). Disabled on Windows, where `destroy` is a forced end and Java sends no console event to another process |
| `controlpanel.TheChildEndsWithTheControlPanelTest.noChildIsStartedOnceTheControlPanelIsEnding` | after `endWithTheControlPanel`, Run is refused with §1's line and no child is started (§1) |
| `controlpanel.ChildTest.theControlPanelsEndLeavesNeitherTheChildNorItsGrandchild` | `Child.endWithin` with the bound of 2 seconds, on a child that started a grandchild: both are seen to end within it, and neither is alive (§1) |

**No test holds the hook on Windows**, nor a forced end leaving the child running (§2), which is the absence of a hook and holds by the JVM's own rule.

## What this does not decide

- **ADR-278 §7's orderly exit**, a quit the control panel would offer on its pages, asking whether to stop the child; no page offers one.
- **Whether Windows lets the hook finish its wait** after a console's close, a logoff or a shutdown; its kills come first.
- **A child left running by a forced end**, which the control panel started again shows as held and does not stop (§2).
- **Ending the child on a forced end**, which needs native code (Alternatives refused).
