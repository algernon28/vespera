# ADR-437 — Start Docker's and Shut down Docker's lines are shown in the output panel as each is logged

- **Date**: 2026-11-21
- **Status**: accepted on 2026-11-21.
- **Decided**: under the operator's delegation, in [#921](https://github.com/algernon28/vespera/issues/921): that while *Start Docker* runs, the output panel shows every line the start logs, as `vespera run`'s start logs them, the console output of `docker compose up` among them a line at a time as it prints, and, new, the engine's start command and what it printed; the commands run only to ask, `docker version`, the checks for Compose and buildx and `wsl --list`, showing nothing (§1, §2); that the lines reach the window as a `Consumer<String>`, a type of `java.base`, handed down through `DockerControl` and `DockerEngine` to `ContainerStarter`, so the control panel names no class of the jar but `DockerEngine` and no class names a stream (§3); that the panel is emptied at the press, shows the press's lines from then on, ends on the press's own line where no line told reads the same, and that the next child, or another press, empties it as ADR-364 §7 has a new child do (§4); that *Shut down Docker* has the same treatment, its two stops and what each printed logged and shown, while the download panel and the stop as the window closes put nothing in the output panel (§5, §6); and the words, the seams and the tests.
- **Amends**: [ADR-430](0430-the-control-panel-checks-docker-the-containers-and-the-models-and-starts-nothing-until-start-docker-is-pressed.md) §5's *"Its line, a refusal's or `.wslconfig`'s, is shown in `outcome` where it has one"*: it is still, and the output panel shows the start's lines as each is logged and that line last (§4); §12: `DockerControl.start` and `shutDown` take a `Consumer<String>`, and `DockerEngine` gains a form of `keepTheWslDistributionUp`, `startTheContainers` and `shutDown` taking one (§7). It carries a note at its head.
- **Amends**: [ADR-364](0364-the-output-panel-is-copyable-text-of-the-whole-output-from-a-file-of-its-own-and-every-text-of-the-window-is-copyable.md) §1's *"What it holds is §7's"* and §7's *What the panel shows*: from a press of *Start Docker* or a kept *Shut down Docker* until the next child is watched, the panel holds that press's lines, and neither the child's held lines nor an output file (§4). Its following rule, its appending and §4's rule that no class but `EntryPoint` names a stream stand. It carries a note at its head.
- **Amends**: [ADR-390](0390-vespera-uses-docker-engine-alone-the-control-panel-starts-it-as-it-opens-and-a-button-stops-the-sidecars-then-the-engine.md) §4: `DockerEngine`'s surface gains three methods taking a `Consumer<String>`, still of primitives, `java.base` types and itself (§7); §5: *Shut down Docker* shows its lines in the output panel (§5). It carries a note at its head.
- **Keeps**: [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3 as ADR-390 leaves it: `DockerEngine` is the one class of the jar the control panel names. [ADR-364](0364-the-output-panel-is-copyable-text-of-the-whole-output-from-a-file-of-its-own-and-every-text-of-the-window-is-copyable.md) §4: no class but `EntryPoint` reads `System.out` or `System.err`. [ADR-323](0323-vespera-starts-the-docker-engine-and-the-sidecars-when-they-are-not-answering-recreates-and-stops-none-and-stage-2-waits-for-a-converter-still-starting.md) §5: a command of the engine's start or stop writes its output to a file and not a pipe; §8's and ADR-390 §6's lines keep their words. [ADR-430](0430-the-control-panel-checks-docker-the-containers-and-the-models-and-starts-nothing-until-start-docker-is-pressed.md) §4 and §7: the check, and the download panel, which shows a download's progress in its own rows. ADR-390 §5: the stop of the containers as the window closes. ADR-047: nothing prompts.
- **Settles**: [#921](https://github.com/algernon28/vespera/issues/921).
- **Rests on**: `ContainerStarter` as it stands, read: it logs on its own logger at INFO the wait for the engine, `Starting the containers: <command>`, each line `up` prints, through `ProcessRunner.runToTheEnd`, and the wait for the containers, and at WARN the `.wslconfig` line; it logs nothing of the engine's start command or of a stop; `SystemProcessRunner.run` sends a bounded command's output to a file read once the command has exited (ADR-323 §5), and `runToTheEnd` reads `up`'s from a pipe a line at a time. The window's process starts no Spring context, so its lines are logged where logback's own configuration sends them, its console, and the output panel shows nothing of them.

## Context

**What the operator sees today.** *Start Docker* runs `ControlPanelState.startDocker` on a thread of its own (ADR-430 §5, `Cockpit.startDockerAfterPressing`) and shows the one line it returns in `outcome`. The output panel shows the watched child's held lines or its output file (ADR-364 §7) and nothing else; a start that pulls an image or boots an engine for minutes shows nothing as it goes. *Shut down Docker* is the same: one line in `outcome` once both stops have returned.

**What is logged.** `vespera run`'s start is the same code, `ContainerStarter.ensureRunning`, and its lines are in the run's output because the run's logback writes `ContainerStarter`'s logger to the console and to the output file (ADR-364 §2). In the window's process the same lines go to its console, which nothing of the window shows, and ADR-364 §4 lets no class but `EntryPoint` name a stream. The engine's start command, `systemctl … start docker`, `wsl … systemctl start docker` or `colima start`, and the two stops of the shut-down are run through `ProcessRunner.run`, whose output is read whole once the command has exited, and logged nowhere.

## Decision

### 1. Which lines

**While a press runs, the output panel shows every line `ContainerStarter` logs for it, as its words alone**, in the order logged:

- for *Start Docker*, what `vespera run`'s start logs: the `.wslconfig` line on Windows (ADR-421 §5); §2's line of the engine's start and what that command printed; `Waiting up to <n> seconds for the Docker engine to answer` and `The Docker engine answers after <n> seconds`; `Another Vespera invocation is starting the containers; waiting for it`; `Starting the containers: <command>` and every line `up` prints as it prints it, its build and its pulls among them; `Waiting up to <n> seconds for <names> to answer` and `<names> answer after <n> seconds`;
- for *Shut down Docker*, §2's two stops and what each printed.

**Its words alone**: the line's message as logged, with no time, level, thread or logger, as the operator logger's lines read (ADR-364 §4), a line logged at WARN read as one logged at INFO.

**Not shown**: what the commands run only to ask print, `docker version`, `docker compose version`, `docker buildx version`, `systemctl is-active` and `wsl --list`, each run every 2 s while a wait lasts; the check's every 5 s (ADR-430 §4), which logs nothing.

### 2. What the starter logs that it did not

Three lines and the output of three commands are logged by `ContainerStarter`, on its logger at INFO, so that a `vespera run` has them in its output too:

| Where | Before the command | After it has exited |
|---|---|---|
| the engine's start, ADR-390 §2's command | `Starting the Docker engine: <command>` | each line it printed |
| the shut-down's step 1, `compose stop` | `Stopping the containers: <command>` | each line it printed |
| the shut-down's step 2, the engine's stop | `Stopping the Docker engine: <command>` | each line it printed |

`<command>` is the command as ADR-323 §8's lines write it, its words joined by one space (`ContainerStartRefusedException.asRun`). **Each line it printed** is each line of its output, both streams together as the runner reads them, with every NUL and byte-order mark taken out, as ADR-430 §3 reads `wsl.exe`'s, trailing white space taken off, and the lines then blank left out. They are logged once the command has exited, or been killed at its bound, since its output is a file read then (ADR-323 §5); a command whose program cannot be started prints nothing, and its line is the refusal's. A stop that is not run, step 2 after step 1 exited non-zero, logs nothing. The stop of the containers alone, as the window closes (ADR-390 §3), logs nothing new.

### 3. How the lines reach the window

**A `Consumer<String>`, told each line once it is logged, on the thread that logs it**, handed down by the window: `DockerControl.start` and `shutDown` are given it, `DockerControl.inProcess` gives it to `DockerEngine`, and `DockerEngine` to the `ContainerStarter` it builds, which tells it each line §1 names, its words alone, after logging it. A starter given none tells nothing, as a `vespera run`'s is. `Consumer` is of `java.base`, so `DockerEngine`'s surface stays primitives, `java.base` types and itself, and the control panel names no other class of the jar (ADR-285 §3, ADR-390 §4). No class names a stream (ADR-364 §4), no appender is added to the window's logback, and the lines go on to the window's console as before.

### 4. What the output panel shows

- **At the press**, on the toolkit's thread before the press's own thread begins, the panel is emptied, and from then on holds that press's lines alone: no line of the watched child's held lines, and no output file's, is appended to it, though the child's lines are still read and held (ADR-364 §7).
- **Each line told** is appended within a second, at the next tick of the window's clock, in the order told, by ADR-364 §1's appending and its rule of following.
- **When the press returns**, its line, where it has one, no line told reads the same and the panel still shows that press, no later press or child having emptied it, is appended last, so a refusal ends the panel as a refused run's closing line ends its output; and it is shown in `outcome`, as before (ADR-430 §5, ADR-390 §5). A start that went through has the `.wslconfig` line alone as its line, told already, so it is shown once in the panel.
- **The next child** the window watches, its own or a foreign holder's, empties the panel and shows its own output, as ADR-364 §7 has it; so does the next press. Until then the press's lines stay, after it has returned and whatever the window's clock reads.

### 5. *Shut down Docker*

The same as *Start Docker*: the panel is emptied once its question is answered to go on (ADR-390 §5, ADR-423), before its thread stops the window's child and shuts Docker down, and shows §2's lines and its line last. The child it stops keeps its whole output in its output file (ADR-364 §2). Answered to keep Docker running, nothing is emptied.

### 6. What stays as it is

- **The download panel** (ADR-430 §7) shows a download's progress in its own rows; nothing of a download is put in the output panel, whether the operator clicked a model's *KO* or *Start Docker* opened it.
- **The stop of the containers as the window closes** (ADR-390 §5) has no panel to show anything in; its line is logged as before.
- **The check** (ADR-430 §3) shows nothing in the output panel.
- **`vespera run` and `vespera label --auto`** keep their start, which logs §2's engine line and the start command's output beside its other lines.

### 7. The seams

- **`ContainerStarter.telling(Consumer<String> eachLine)`**, package-private: a starter of the same settings and collaborators, which hands `eachLine` each line §1 names, its words alone, after logging it, for `ensureRunning`, `keepTheWslDistributionUp` and `shutDown`.
- **`DockerEngine`** gains `Optional<String> keepTheWslDistributionUp(URI doclingBaseUrl, URI ollamaBaseUrl, Consumer<String> told)`, `Optional<String> startTheContainers(URI doclingBaseUrl, URI ollamaBaseUrl, String doclingImage, Consumer<String> told)` and `String shutDown(Consumer<String> told)`; the forms without `told` stay and tell nothing.
- **`DockerControl`** gains `Said start(LaunchValues values, Consumer<String> told)` and `Said shutDown(Consumer<String> told)`, which `inProcess` and `NONE` implement, `NONE` telling nothing; `start(LaunchValues)` and `shutDown()` become default methods handing a `told` that drops each line. `inProcess`'s start gives `told` to both `keepTheWslDistributionUp` and `startTheContainers`.
- **`ControlPanelState.startDocker(LaunchValues, Consumer<String>)`** and **`shutDownDocker(Consumer<String>)`** hand it on; **`OutputPanel`** holds the lines told, safe to tell from any thread, and appends them at its tick while it shows a press.

### 8. No run id moves

`ContainerStarter` and `DockerEngine` are in `pipeline`, which no stage's version names (ADR-222); neither decides a verdict, a cache key, a cluster or text of the deliverable. The rest is the control panel's. No stage moves, and no pass is owed.

## Alternatives rejected

- **A logback appender in the window's process, attached to `ContainerStarter`'s logger.** It would name logback in the control panel and catch the lines of every thread logging there, the closing stop's among them, with no tie to the press.
- **The press's lines appended below the last child's output.** A start is no part of the invocation whose output the panel showed; the two would read as one, and the child's output file keeps it whole.
- **The raw output of every command the start runs**, the engine checks of the wait among them. A check run every 2 s for up to `vespera.containers.wait-at-most` would bury the lines that say what the start is doing.
- **The engine's start read from a pipe as it prints.** ADR-323 §5: a daemon the command starts may hold the pipe open for as long as it lives.
- **Lines with the console's time and level.** The panel shows what a press says, as the operator logger's lines read (ADR-364 §4).
- **The download's progress in the output panel too.** The download panel shows it, and a line for each report of bytes would be thousands.

## Consequences

- **A start that pulls an image or boots the engine shows what it is doing** as it does it, and a refused one ends on its refusal in the panel, as in `outcome`.
- **`vespera run`'s output gains `Starting the Docker engine: <command>`** and that command's lines where it starts the engine.
- **The README's "Running it"** says that while *Start Docker* or *Shut down Docker* runs, the output shows what it does, and that the next run's output takes its place.
- **`CONTEXT.md`'s Output panel entry** says that from a press of either until the next child it shows what that press logs.

## What this does not decide

- **A bound on the lines a press holds.** A start holds a few hundred lines at most, `up`'s pulls among them; the output panel's size is ADR-364's known limit.
- **Following a bounded command's output while it runs**, by reading its file as it grows.

## Tests

| Method | What it holds |
|---|---|
| `engine.pipeline.AStartAndAShutDownTellEachLineTheyLogTest.aStartTellsEachLineItLogsInOrderTheEnginesStartAndWhatItPrintedAmongThem` | §1, §2, §3: on macOS with the engine down, the lines told, whole and in order: the engine's start and its two lines, a blank one left out and trailing blanks taken off, the wait for the engine, `up`'s command and its two lines, the wait for the containers |
| `engine.pipeline.AStartAndAShutDownTellEachLineTheyLogTest.aRefusedStartTellsEveryLineBeforeItsRefusalAndNotTheRefusal` | §1, §3: `up` exiting 1, `up`'s command and its line told, the refusal thrown and not told |
| `engine.pipeline.AStartAndAShutDownTellEachLineTheyLogTest.aShutDownTellsEachStopAndWhatItPrintedAndNotItsOwnLine` | §2, §3: the two stops' lines and step 1's output told, step 2 printing nothing; the shut-down's line returned and not told |
| `engine.pipeline.AStartAndAShutDownTellEachLineTheyLogTest.onWindowsTheWslconfigLineIsToldAsItIsLogged` | §1, §3: on Windows, `.wslconfig` written, its line told once and returned |
| `ui.StartDockerAndShutDownDockerShowWhatTheyDoInTheOutputPanelTest.startDockerEmptiesTheOutputPanelShowsEachLineAsItIsToldAndEndsOnItsRefusal` | §4: an ended child's output in the panel; pressed, the panel holds exactly the lines told while the start is held, then those and the refusal last; `outcome` the refusal |
| `ui.StartDockerAndShutDownDockerShowWhatTheyDoInTheOutputPanelTest.aStartThatWentThroughShowsItsWslconfigLineOnce` | §4: the `.wslconfig` line told and returned, in the panel once |
| `ui.StartDockerAndShutDownDockerShowWhatTheyDoInTheOutputPanelTest.theNextRunsOutputTakesThePlaceOfTheStartsLines` | §4: a run started after the press, the panel shows its line and none of the start's |
| `ui.StartDockerAndShutDownDockerShowWhatTheyDoInTheOutputPanelTest.shutDownDockerKeptShowsItsLinesThenItsOwnAndDeclinedLeavesTheOutputPanelAsItWas` | §5: answered to keep Docker running, the panel unchanged and nothing shut down; answered to go on, exactly the lines told while held, then its line last |
| `ui.StartDockerAndShutDownDockerShowWhatTheyDoInTheOutputPanelTest.aStartRefusedAfterAShutDownWasPressedLeavesTheShutDownsOutputAlone` | §4, §5: a start held, *Shut down Docker* answered to go on and returned, then the start refused: the panel holds the shut-down's lines and its line alone, the start's refusal nowhere in it |
| `ui.StartDockerAndShutDownDockerShowWhatTheyDoInTheOutputPanelTest.theDownloadPanelPutsNothingInTheOutputPanel` | §6: a download clicked, reported and done, the panel as it was |
| `ui.TheControlPanelsDockerSettingsTest.onWindowsTheWindowsStartTellsTheWslconfigLineItWrote` | §3, §7: on Windows, `inProcess`'s start tells the `.wslconfig` line and is refused at the container lock, telling nothing more |

Amended: **`ui.RecordingDockerControl`** implements `start` and `shutDown` with `told`, telling the lines a test sets for each before it holds the call.

The existing `ui.TheControlPanelLinksNoClassOfTheJarTest.theDockerEnginesClassIsNarrowAndNamesNoSpring` holds §3's surface, and `ui.TheShutDownDockerButtonTest` the lines in `outcome`, unchanged.

## What the implementation owes

Beside turning the tests above green: `ContainerStarter.telling` and §2's three lines and the output of the three commands; `DockerEngine`'s three forms taking `told`; `DockerControl`'s two, the default one-argument forms, `inProcess` and `NONE`; `ControlPanelState`'s two; `OutputPanel`'s showing of a press, emptied at it, appended at its tick and left by the next child or press; `Cockpit`'s presses handing their lines to it and appending their line last; and every citation of this record in the code naming it ADR-437.
