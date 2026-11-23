# ADR-421 — On Windows, Vespera writes `.wslconfig` with no idle timeout where there is none, and changes no file that is there

> **Partly amended — see [ADR-430](0430-the-control-panel-checks-docker-the-containers-and-the-models-and-starts-nothing-until-start-docker-is-pressed.md).** Of §1: the control panel sees to `.wslconfig` when *Start Docker* is pressed, not as its window opens, and its check writes and reads nothing; a start sees to it where at least one container is on this machine, a host now resolved and not read as written. Of §6: `DockerControl.inProcess`'s start calls `keepTheWslDistributionUp` and then `DockerEngine.startTheContainers`. The rest of this record stands.

> **Partly amended — see [ADR-444](0444-the-gpu-image-starts-only-where-docker-has-the-nvidia-runtime-and-the-control-panel-offers-the-toolkit-or-the-cpu-image.md).** Of Consequences: for the GPU image, the one-time setup on Windows also holds the NVIDIA Container Toolkit, which the control panel installs inside the distribution once the operator confirms it; `.wslconfig` is no longer the one file Vespera writes outside the working directory and the deliverable root, the install writing NVIDIA's keyring, its package source and `/etc/docker/daemon.json` inside the distribution and restarting Docker there. The rest of this record stands.

- **Date**: 2026-11-18
- **Status**: accepted on 2026-11-18.
- **Decided**: by the operator, in [#881](https://github.com/algernon28/vespera/issues/881): that on Windows Vespera creates `%UserProfile%\.wslconfig` where it does not exist, holding `[general]` and `instanceIdleTimeout=-1`, in place of leaving it to ADR-390 §1's one-time setup. Under the operator's delegation: where and when it is written (§1); its bytes, and that no file there is written over or changed (§2); what a file that is there is read for (§3); that Vespera runs no `wsl --shutdown` (§4); the lines, and that none refuses a start (§5); what the control panel calls and shows (§6); how a test keeps the real profile folder out (§7).
- **Amends**: [ADR-390](0390-vespera-uses-docker-engine-alone-the-control-panel-starts-it-as-it-opens-and-a-button-stops-the-sidecars-then-the-engine.md) §1, whose one-time setup on Windows is three things *"the operator's to make and Vespera's to neither write nor read"*: that holds of Docker Engine and of `systemd=true` in `/etc/wsl.conf`; `instanceIdleTimeout=-1` in `%UserProfile%\.wslconfig` is Vespera's to write where there is no such file, and to read where there is one (§1 to §3). §4: `DockerEngine.of` takes the profile folder as a fourth argument, its three-argument form gone, and `DockerEngine` gains `keepTheWslDistributionUp`, its surface still of `java.base` types and itself (§6). §5: as the window opens, its start calls `keepTheWslDistributionUp` before `startTheEngine`, and a start that went through shows its line where it has one (§6). Its Consequence *"On Windows, Vespera needs a WSL 2 distribution with Docker Engine, systemd and `instanceIdleTimeout=-1`"* holds, the last now written by Vespera where no file is there. Its *What this does not decide* loses *"A check of the WSL setup"* for `.wslconfig` (§3); `/etc/wsl.conf` stays unchecked. It carries a note at its head.
- **Amends**: [ADR-323](0323-vespera-starts-the-docker-engine-and-the-sidecars-when-they-are-not-answering-recreates-and-stops-none-and-stage-2-waits-for-a-converter-still-starting.md) §3: between step 2, a base URL on another machine, and step 3, both sidecars answering, on Windows `.wslconfig` is written or read (§1). It runs no command and takes no lock, so step 3's *"No command is run"* stands. It carries a note at its head.
- **Keeps**: ADR-390 §2, §3, §6 and §7: each system's one start, the stops, which write and read nothing here (§1), their lines, and the `.invalid` host that keeps Docker out of a test, which keeps this file out too (§7). [ADR-410](0410-a-missing-docker-compose-or-buildx-is-named-with-the-command-that-installs-it-before-up.md) §2: the checks for Compose and buildx, after the engine answers, unchanged.
- **Keeps**: [ADR-047](0047-the-pipeline-never-blocks.md): nothing prompts, so nothing asks before it writes, and nothing stops WSL to make the file take effect (§4).
- **Keeps**: [ADR-054](0054-a-corpus-is-its-root-path-the-database-lives-in-a-configured-working-directory.md): nothing is written into the archive; the profile folder is the user's, as the folder of corpus-root locks is (ADR-279).
- **Settles**: [#881](https://github.com/algernon28/vespera/issues/881).
- **Rests on**: Microsoft's documentation of `.wslconfig` and of the 8-second rule, read and not measured; and `user.home` read on Windows 11 by a JDK 26 JVM, below.

## Context

On Windows, Docker Engine runs inside a WSL 2 distribution, and Vespera reaches it through `wsl … --exec` commands that end as each returns; a run reaches the sidecars through the ports WSL forwards, so nothing of Vespera's keeps that distribution running while a run talks to them (ADR-390 §1). ADR-390 §1 made `instanceIdleTimeout=-1` under `[general]` in `%UserProfile%\.wslconfig` part of the operator's one-time setup, and left its check undecided.

A `vespera run` on Windows 11 with WSL 2.5.9.0, `systemd=true` under `[boot]` in the distribution's `/etc/wsl.conf` and no `%UserProfile%\.wslconfig` stopped at stage 2 when ADR-175's bound of 180 s ran out, `http://localhost:5001/health` refusing the connection: WSL had shut the distribution down for being idle, after its default of 15 s, and Docker Engine and both sidecars with it.

### Read, and measured

**Read, not measured.** Microsoft's documentation of WSL's settings:

- `.wslconfig` lies at `%UserProfile%\.wslconfig` and *"does not exist by default"*; *"If the file is missing or malformed (improper markup formatting), WSL will continue to launch as normal without the configuration settings applied."*
- Under `[general]`, `instanceIdleTimeout`, 15000 by default: *"The number of milliseconds that a distro is idle, before it is shut down. Set to -1 to disable auto shutdown."* Under `[wsl2]`, `vmIdleTimeout`, 60000 by default, on Windows 11 alone: *"The number of milliseconds that a VM is idle, before it is shut down."*
- The settings *"will be applied on start up of the WSL VM"*: *"you may need to run `wsl --shutdown` to shut down the WSL 2 VM and then restart your WSL instance for these changes to take effect"*, and `wsl --shutdown` *"will shut down all running distributions, so use wisely"*.
- Its example file writes `localhostforwarding=true` for the key its table names `localhostForwarding`, and its comments open with `#`.

**Measured.** On Windows 11, a JDK 26 JVM's `user.home` and the environment's `USERPROFILE` named the same folder, `C:\Users\<user>`.

**Not measured:** whether WSL reads sections and keys without regard to case, a byte-order mark or a comment opening with `;`, which §3 assumes; whether a file written while WSL's machine runs takes effect at a restart of the distribution alone; that the machine stops `vmIdleTimeout` after its last distribution, which §4 relies on where WSL is not running; and anything of a run under `instanceIdleTimeout=-1` over hours, as ADR-390 left it.

## Decision

### 1. Where and when it is written

**On Windows alone**, the system `HostSystem` reads as Windows (ADR-323 §5). Linux and macOS have no WSL, and nothing is looked for there.

- **`vespera run` and `vespera label --auto`**: in the start of the sidecars, after ADR-323 §3's step 2 and before its step 3. A base URL naming another machine writes and reads nothing, as it runs nothing. Otherwise the file is seen to before the sidecars are asked, so a start whose sidecars answer, which runs no command, sees to it too: the distribution that WSL shuts down in the middle of a run is one whose sidecars answered when the run began.
- **The control panel's start as its window opens**: through `DockerEngine.keepTheWslDistributionUp`, which the window's start calls before `startTheEngine`, with the same step 2 first (§6).
- **Before any `wsl` command, and outside `sidecars.lock`.** It runs no command and needs no lock (§2's creation writes over nothing). Seen to before a start's first `wsl` command, the file is read by a WSL that command starts.
- **Not the stops.** The shut-down and the window's stop of the sidecars (ADR-390 §3) write and read nothing. `ensureTheEngineRuns` does not see to it either: the window calls it apart, so it is seen to once per start.
- **Every start, nothing remembered.** One look for a file, and a read of a file of a few lines; a remembered answer would outlive the operator's next edit of the file.

### 2. The file Vespera writes

- **Where**: `.wslconfig` in the Windows profile folder, the JVM's `user.home`, which on Windows is the folder `%UserProfile%` names (Measured). The folder is never created: where it is not there, the write fails (§5).
- **What**: two lines, the operator's, ended by CR LF, in ASCII, with no byte-order mark and nothing else, 35 bytes:

```
[general]
instanceIdleTimeout=-1
```

- **Only where none is there.** Vespera looks for `.wslconfig`; anything of that name, a file, a folder or a link, is one that is there, and is read (§3). Where none is, the file is created as new, so one that appears between the look and the write is never written over: the creation fails, and that file is read as one that is there.
- **Never changed once there**, whoever wrote it, Vespera included: never written over, appended to, edited, renamed or deleted. A file Vespera created and could not finish writing is left as it is, and the next start reads it as any file there.

### 3. A file that is there is read for the setting, and never written

It is read whole, as UTF-8, a malformed byte read as the replacement character and a byte-order mark at its head skipped, and split into lines at CR LF, LF or CR. Each line is stripped of blanks at both ends; an empty line, and one opening with `#` or `;`, is skipped. A line `[<name>]` opens the section `<name>`, stripped. Within the section `general`, a line holding `=` whose key, the text before the first `=`, stripped, is `instanceIdleTimeout` gives its value, the text after that `=`, stripped. Sections and keys are compared without regard to case, as the documentation's own example writes a key (Read).

**The file sets it where the last value so given is `-1`.** Anything else is a file that lacks it: no `[general]`, no such key there, the key under another section, another value, a value with more after it, or an empty file. Where it lacks it, §5's line is logged, and the start goes on.

**Why read, and not repair.** The operator decided a write where no file is there. A file that is there is the operator's or WSL Settings', and may hold other settings for the operator's other distributions; an edit of it can lose what it holds, and two writers of one file disagree. Reading it costs a few lines, and the line it gives says what to add.

### 4. No `wsl --shutdown`

Vespera runs neither `wsl --shutdown` nor `wsl --terminate`, after writing the file or ever.

- **It stops every WSL distribution and every container in them**, the operator's own as well as Vespera's, and nothing prompts (ADR-047), so there is no asking first as *Shut down Docker* asks (ADR-390 §5).
- **Where the file matters most, it takes effect without one.** A start sees to the file before its first `wsl` command (§1). Where WSL is not running, as once it has shut the distribution down idle and its machine has stopped after it, that command starts WSL with the file read (Read; Not measured).
- **Where WSL is running, it takes effect at WSL's next start.** §5's line says so and names the command, for the operator to run when nothing of theirs runs in WSL.

### 5. The lines, none a refusal

Each is one line, logged at WARN by the sidecar starter: on the console, in `vespera.log` and in the invocation's output file (ADR-364), so the control panel's output panel shows a child's. None refuses a start and none is an exception: the start goes on as it would have, the same commands in the same order (ADR-323 §3, ADR-390 §2, ADR-410 §2). A file that sets the setting gives no line.

`<file>` is the absolute path of `.wslconfig`; `<reason>` is the message of what refused it, or the simple name of its type where it has none, as ADR-323 §8's line of a lock that cannot be opened.

- **Written**:
  > Vespera wrote &lt;file&gt;, setting instanceIdleTimeout=-1 under [general], so that WSL does not shut its distribution down while idle, and Docker and the sidecars with it. It applies from WSL's next start; if WSL is running now, wsl --shutdown starts it afresh, and stops every WSL distribution and the containers in them.
- **A file there lacks it**:
  > &lt;file&gt; does not set instanceIdleTimeout=-1 under [general], so WSL may shut its distribution down while idle, and Docker and the sidecars with it, in the middle of a run. Vespera changes no file that is there: add it yourself, then run wsl --shutdown, which stops every WSL distribution and the containers in them.
- **It cannot be written**, the profile folder not there among the reasons:
  > Vespera could not write &lt;file&gt; (&lt;reason&gt;), so WSL may shut its distribution down while idle, and Docker and the sidecars with it, in the middle of a run. Create it with the two lines [general] and instanceIdleTimeout=-1, then run wsl --shutdown, which stops every WSL distribution and the containers in them.
- **One there cannot be read**, a folder of that name among the reasons:
  > Vespera could not read &lt;file&gt; (&lt;reason&gt;) to see whether it sets instanceIdleTimeout=-1 under [general]; without it WSL may shut its distribution down while idle, and Docker and the sidecars with it, in the middle of a run. See that it does, then run wsl --shutdown, which stops every WSL distribution and the containers in them.
- **No profile folder is known**, `user.home` blank or unset:
  > Vespera cannot tell where the Windows profile folder is, so it cannot see whether %UserProfile%\.wslconfig sets instanceIdleTimeout=-1 under [general]; without it WSL may shut its distribution down while idle, and Docker and the sidecars with it, in the middle of a run. See that it does, then run wsl --shutdown, which stops every WSL distribution and the containers in them.

**Why no refusal.** A missing setting costs a run only where WSL shuts its distribution down in the middle of it. A machine where something else keeps the distribution up, or an operator who chose another value, still starts; and a file Vespera cannot write is the same machine it was before this record.

### 6. The control panel

- **`DockerEngine.of(String wslDistribution, Duration waitAtMost, Path lockFolder, Path profileFolder)`**, the three-argument `of` gone, so no caller builds one that reads a profile folder it was not given.
- **`public Optional<String> keepTheWslDistributionUp(URI doclingBaseUrl, URI ollamaBaseUrl)`**: §1 to §3 for the two addresses, giving §5's line; empty where the file sets it, the system is not Windows, or a base URL names another machine. Its surface is `java.base` types, as ADR-390 §4 requires.
- **`DockerControl.Settings`** gains the profile folder, after the lock folder; **`ControlPanel.dockerSettings`** takes it, and **`ControlPanel.open`** gives it `user.home`, as it reads the lock folder's default from it.
- **`DockerControl.inProcess`'s start** calls `keepTheWslDistributionUp` and then `startTheEngine`, for the same two addresses. A refusal of the second is `Said(false, <its line>)`, as before; a start that went through is `Said(true, <§5's line>)`, or `Said(true, "")` where there is none.
- **The window shows a start's line in `outcome` whenever it has one**: a refusal's, as ADR-390 §5 has it, and now the line of a start that went through. A refusal's line is the one shown where both are; §5's line is then in the control panel's log alone.

### 7. How a test keeps the real profile folder out

- **`SidecarStartSettings` gains `profileFolder`**, a `Path`, null where none is known. `SidecarStartSettings.from` reads `user.home` from the `Environment` it is given, a blank or missing one being none; its four- and five-argument constructors give none, so a test that builds the starter without naming a folder gets §5's last line and writes nothing.
- **`DockerEngine.of` and `DockerControl.Settings` take it**, so `SidecarStartSettings.from` and `ControlPanel.open` are the only code that reads `user.home` for it, and no test of this record calls either with the JVM's own.
- **A test that means to see a write names a folder of JUnit's `@TempDir`**, and none sets `user.home`.
- **No Spring test context has a starter** (ADR-323 §1), and the jar's launches in the integration tests name the sidecars on `sidecars.invalid` (ADR-390 §7), step 2, which writes nothing.

### 8. No run id moves

`WslIdleTimeout`, the class that writes and reads the file, `SidecarStarter`, `SidecarStartSettings` and `DockerEngine` are in `pipeline`, which no stage's version names (ADR-222); `DockerControl`, `ControlPanel` and `Cockpit` are the control panel's. None decides a verdict, a cache key, a cluster or text of the deliverable. No stage moves, and no pass is owed.

## Alternatives rejected

- **Leave it to the operator, as ADR-390 §1 did.** The operator decided against it: a machine without the file loses a run at stage 2 to it.
- **Add the setting to a file that is there.** §3: the operator decided a write where none is; a file there is someone else's, and an edit can lose what it holds.
- **Run `wsl --shutdown` after writing, where the engine does not answer.** §4: it stops the operator's own distributions and containers with no asking; and where the engine does not answer, WSL has usually stopped by itself.
- **Refuse the start where the setting is missing.** §5: it would stop a machine that runs well without it.
- **Read `USERPROFILE` from the environment.** `user.home` is set on every system and named the same folder (Measured): one source.
- **Write under `sidecars.lock`.** It runs no command, and creation as new never writes over a file another start wrote meanwhile.
- **Remember that the file was seen to.** §1: a second file to keep, outliving the operator's next edit, to save one look.
- **Check `systemd=true` in `/etc/wsl.conf` too.** Not asked; reading it is a `wsl` command into the distribution, which would start WSL before the file is seen to; and a distribution without systemd already refuses the engine's start in ADR-390 §2's line, naming `systemctl`.

## Consequences

- **A Windows machine without `.wslconfig` gets one** at the first start of Vespera that looks for its sidecars on this machine, or as the control panel's window opens, and says so in one line.
- **The one-time setup on Windows is Docker Engine, with Compose and buildx, and `systemd=true`**; `instanceIdleTimeout=-1` is Vespera's where there is no `.wslconfig`, and the operator's to add where there is one.
- **The README's "Running it"** says, in the setup on Windows, that Vespera writes `%UserProfile%\.wslconfig` with `instanceIdleTimeout=-1` under `[general]` where you have none, never changes one you have, and says so when yours lacks it.
- **`CONTEXT.md`'s Archive and Working directory entries** name `.wslconfig` in the user's profile folder among what is written outside the working directory, and its Control panel entry says the window sees to it before it starts the engine.
- **`docs/architecture.md`'s technology table** says the starts write `.wslconfig` on Windows where there is none, citing this record.
- **`PipelineHoldsOnlyTheRulesOnRecordTest`** lists `WslIdleTimeout` as holding no rule.

## What this does not decide

- **`/etc/wsl.conf`'s `systemd=true`**, which stays the operator's and unchecked.
- **Whether WSL reads a file as §3 does.** Where WSL reads case, a byte-order mark or a `;` comment otherwise (Not measured), a file gets a line it does not need, or lacks one it needs.
- **`vmIdleTimeout`**: WSL's machine is idle only once its last distribution has stopped, which this setting keeps from happening for being idle.
- **A file WSL Settings rewrites.** What it writes over is the operator's, as any file there is.
- **A profile folder `%UserProfile%` names other than `user.home`.** None was found; the line names the file Vespera looked at.

## Tests

| Method | What it holds |
|---|---|
| `pipeline.TheWslconfigIsWrittenOnlyWhereThereIsNoneTest.whereThereIsNoneItIsWrittenWithTheTwoLinesAndSaysSo` | §2, §5: the file's 35 bytes, whole, the only file in the folder, and the line *Written*, whole |
| `pipeline.TheWslconfigIsWrittenOnlyWhereThereIsNoneTest.aFileThereIsReadForTheSettingAndNeverWritten` | §3, §5: files that set it, with other sections, another case, blanks, comments and a byte-order mark, give no line; files that lack it, another value, another section, a comment, a later value, an empty file, give the line *lacks it*, whole; every file's bytes unchanged |
| `pipeline.TheWslconfigIsWrittenOnlyWhereThereIsNoneTest.aFileThatCannotBeWrittenOrReadOrNoFolderIsALineAndNothingIsMade` | §2, §5: a profile folder not there, the lines *cannot be written*, the folder not made; a folder named `.wslconfig`, *cannot be read*, the folder left empty; no folder known, the last line, whole |
| `pipeline.TheWslconfigIsWrittenOnlyWhereThereIsNoneTest.onWindowsAStartWritesItBeforeAskingTheContainersAndRunsWhatItRanBefore` (named for the container by ADR-433, #891) | §1, §4: sidecars answering, the file there when they were first asked and no command run; the engine down, the same commands, whole, as before, none naming `--shutdown` or `--terminate` |
| `pipeline.TheWslconfigIsWrittenOnlyWhereThereIsNoneTest.nothingIsWrittenOffWindowsOrForContainersOnAnotherMachine` (named for the container by ADR-433, #891) | §1: on Linux and macOS, and on Windows with a base URL on another machine, the folder stays empty and the control panel's call says nothing |
| `pipeline.TheWslconfigIsWrittenOnlyWhereThereIsNoneTest.aWriteThatFailsRefusesNoStart` | §5: with no profile folder there, or none known, the start of the sidecars runs the same commands and starts them |
| `pipeline.TheWslconfigIsWrittenOnlyWhereThereIsNoneTest.theControlPanelsCallWritesItAndTheEnginesStartAndTheStopsWriteNothing` | §1, §6: the call writes it and gives the line, a second gives none; the engine's start alone, the stop of the sidecars and the shut-down leave the folder empty |
| `pipeline.TheWslconfigIsWrittenOnlyWhereThereIsNoneTest.theProfileFolderIsUserHomeAsTheEnvironmentGivesIt` | §2, §7: `user.home` from the environment given, none where blank or missing, and none from the constructors of four and five arguments |
| `ui.TheControlPanelsDockerSettingsTest.onWindowsTheWindowsStartWritesItInTheProfileFolderItIsGiven` | §6, §7: guarded to Windows; the window's start writes the 35 bytes in the folder its settings name, and a refusal of the engine's start is the line it gives |
| `ui.TheShutDownDockerButtonTest.aStartThatWentThroughShowsTheLineItSaid` | §6: a start that went through with a line shows it in `outcome` |

Amended:

- **`ui.TheControlPanelsDockerSettingsTest.theDistributionAndTheWaitAreReadFromTheSourcesInTheirOrder`**: the settings carry the profile folder given.
- **`ui.TheControlPanelsDockerSettingsTest.aConverterTheEnvironmentNamesOnAnotherMachineStartsNothingWhereTheLaunchValuesAreBlank`**: its settings name a profile folder of the test's, which the start on another machine leaves empty.
- **`PipelineHoldsOnlyTheRulesOnRecordTest`** lists `WslIdleTimeout`.

What no unit test shows is what WSL does with the file; the documentation is the evidence (Read).

## What the implementation owes

Beside turning the tests above green: `WslIdleTimeout`, whose `keepTheDistributionUp(Path profileFolder)` does §2 and §3 and gives §5's line; `SidecarStartSettings`'s `profileFolder`, read from `user.home` by `from` and none in its shorter constructors; `SidecarStarter.keepTheWslDistributionUp`, Windows and both base URLs on this machine first, logging its line at WARN, and `ensureRunning` calling it after step 2 and before step 3; `DockerEngine.of` of four arguments and `DockerEngine.keepTheWslDistributionUp`; `DockerControl.Settings`'s profile folder, `ControlPanel.dockerSettings` of three arguments and `ControlPanel.open` giving `user.home`; `DockerControl.inProcess`'s start; and `Cockpit.startDocker` showing a line of a start that went through.
