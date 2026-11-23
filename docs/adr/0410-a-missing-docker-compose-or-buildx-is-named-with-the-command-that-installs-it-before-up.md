# ADR-410 — A missing docker compose or buildx is named, with the command that installs it, before up

> **Partly amended — see [ADR-444](0444-the-gpu-image-starts-only-where-docker-has-the-nvidia-runtime-and-the-control-panel-offers-the-toolkit-or-the-cpu-image.md).** Of §2: once Compose and buildx are found, and for the GPU image alone, `docker info --format {{json .Runtimes}}` asks the engine for its `nvidia` runtime before `up`, and a runtime not listed refuses the start in a line of its own. Of *Alternatives rejected*, *"Install them"*, and of *Keeps* ADR-047, *"nothing is installed"*: the control panel installs the NVIDIA Container Toolkit, and nothing else, once the operator confirms it, on Linux through `sudo` with the password the operator types. The rest of this record stands.

- **Date**: 2026-11-17
- **Status**: accepted on 2026-11-17.
- **Decided**: by the operator, in [#859](https://github.com/algernon28/vespera/issues/859): that before `up` Vespera asks whether the engine it reaches has `docker compose`, and buildx for `--build`; that where either is missing the start is refused in one line naming the missing one, the distribution on Windows, and the command that installs it on that system, never `docker --help`'s text; that this holds for `vespera run`, `vespera label --auto` and the control panel's start as its window opens; and that nothing else of ADR-390 and ADR-323 moves. Under the operator's delegation: the two commands, how their answer is read, and their limit (§1); where they run in the start's order (§2); the line and each system's install command (§3).
- **Amends**: [ADR-323](0323-vespera-starts-the-docker-engine-and-the-sidecars-when-they-are-not-answering-recreates-and-stops-none-and-stage-2-waits-for-a-converter-still-starting.md) §3: between step 6, the engine made to answer, and step 7, `up`, Compose and buildx are asked for, and one missing refuses the start (§2). §8 gains a line, `ComposeOrBuildxIsNotInstalledException`'s, a type that extends `SidecarStartRefusedException` (§3). It carries a note at its head.
- **Amends**: [ADR-390](0390-vespera-uses-docker-engine-alone-the-control-panel-starts-it-as-it-opens-and-a-button-stops-the-sidecars-then-the-engine.md) §2: each system has two checks more, run as the engine check is, inside the distribution on Windows, and killed after the engine check's 30 s (§1). §4: `startTheEngine` asks for both once the engine answers, and gives §3's line where one is missing (§2). §6: §3's line is one of the lines `startTheEngine` refuses with. It carries a note at its head.
- **Keeps**: ADR-323 §3's other steps, §4's `up`, with `--build` and `--no-recreate`, and its never-run list, and §6's `sidecars.lock`; ADR-390 §1, §3 and §5: how a command reaches the engine on each system, the shut-down and the window's stop of the sidecars, which run neither check, and where the window shows a refusal's line.
- **Keeps**: [ADR-326](0326-a-command-whose-output-file-cannot-be-written-or-read-refuses-the-start-in-a-line-of-its-own-and-only-a-program-that-cannot-be-started-is-docker-not-installed.md) §2 as ADR-390 §2 leaves it: the checks' program is the engine check's, `docker`, or `wsl` on Windows, so one that cannot be started is Docker not installed; an output file that cannot be written or read refuses in ADR-326's line, whatever the check.
- **Keeps**: [ADR-047](0047-the-pipeline-never-blocks.md): nothing prompts and nothing is installed; the line gives the command, and the operator runs it.
- **Keeps**: [ADR-141](0141-the-cli-exits-with-the-commands-exit-code-and-the-scheduler-no-feature-uses-is-removed-at-its-source.md): the refusal exits 1, as every line of ADR-323 §8 does.
- **Settles**: [#859](https://github.com/algernon28/vespera/issues/859).
- **Rests on**: the measurements below, on Windows 11 with a `docker` client 29.6.1 and in a WSL 2 distribution of Ubuntu 24.04.1 with Ubuntu's `docker.io` 29.1.3; and, read and not measured, Ubuntu's and Docker's package lists and Homebrew's formulae.

## Context

On Windows, with Docker Engine inside a WSL 2 distribution of Ubuntu 24.04 as ADR-390 §1 has it, a start of the sidecars found the engine answering, then ran `up`, which exited 125 with `unknown shorthand flag: 'p' in -p`. Ubuntu's `docker.io` package installs the engine and the `docker` client with no Compose: its folder of client plugins, `/usr/libexec/docker/cli-plugins`, holds `docker-trust` alone, so `docker compose` is a command the client does not know, and it reads `-p` as a flag of its own. ADR-323 §8's line for a failed `up` gives the command's last line, here `Run 'docker --help' for more information`, which names nothing missing, and nothing in the line says the engine had started. `up --build` also builds through buildx, Ubuntu's `docker-buildx`, which the same package leaves out.

### Measured

**A. The client answers a subcommand it has no plugin for in one way, whatever the subcommand.** A `docker` client 29.6.1 on Windows, which has both plugins, asked of a subcommand no plugin gives:

| Command | Exit | In | What it printed |
|---|---|---|---|
| `docker nosuchplugin version` | 1 | 0.2 s | `docker: unknown command: docker nosuchplugin`, a blank line, `Run 'docker --help' for more information` |
| `docker nosuchplugin -p vespera up` | 125 | 0.15 s | `unknown shorthand flag: 'p' in -p`, the client's usage line, `Run 'docker --help' for more information` |
| `docker compose version` | 0 | 0.2 s | `Docker Compose version v5.3.0` |
| `docker buildx version` | 0 | 0.4 s | `github.com/docker/buildx v0.35.0-desktop.2 …` |

The second row is #859's line, exit code and all: a missing Compose is a subcommand the client does not know. Neither `version` asks the engine.

**B. Inside the distribution, through `wsl -d Ubuntu -u root --exec`**, Ubuntu's `docker.io` 29.1.3-0ubuntu3~24.04.2 with `docker-compose-v2` 2.40.3+ds1-0ubuntu1~24.04.1 and `docker-buildx` 0.30.1-0ubuntu1~24.04.1 installed:

| Command | Exit | In | What it printed |
|---|---|---|---|
| `docker compose version` | 0 | 0.4 s | `Docker Compose version 2.40.3+ds1-0ubuntu1~24.04.1` |
| `docker buildx version` | 0 | 0.6 s | `github.com/docker/buildx 0.30.1 0.30.1-0ubuntu1~24.04.1` |
| `docker nosuchplugin version` | 1 | 0.25 s | as in A |

`/usr/libexec/docker/cli-plugins` then held `docker-buildx` and `docker-compose` beside `docker-trust`, and `dpkg-query` named the two packages. Each time includes `wsl`'s own start of the command.

**Read, not measured.** Ubuntu 24.04's `universe` carries `docker-compose-v2` and `docker-buildx`, built against `docker.io`. Docker's own repository installs Docker Engine as `docker-ce`, and the two as `docker-compose-plugin` and `docker-buildx-plugin`; Docker lists `docker.io` and `docker-compose-v2` as packages that conflict with its own. Homebrew's `docker-compose` and `docker-buildx` formulae install each as a client plugin, and each says, after it is installed: *"For Docker to find the plugin, add "cliPluginsExtraDirs" to ~/.docker/config.json: "cliPluginsExtraDirs": [ "$HOMEBREW_PREFIX/lib/docker/cli-plugins" ]"*. Compose builds through buildx, Bake being its default way to build since 2.37.

**Not measured:** a distribution without Compose answering `docker compose version`, which A stands for, the client having one way to answer a subcommand it lacks; Colima and a `docker` client installed by Homebrew on macOS; Docker Engine on Linux outside WSL; Debian, whose archive names its packages otherwise (*What this does not decide*).

## Decision

### 1. Two checks, read by their exit code

| System | Compose | buildx |
|---|---|---|
| Linux | `docker compose version` | `docker buildx version` |
| Windows | `wsl [-d <distribution>] -u root --exec docker compose version` | `wsl [-d <distribution>] -u root --exec docker buildx version` |
| macOS | `docker compose version` | `docker buildx version` |

Each is run as ADR-390 §1 runs the engine check: inside the distribution on Windows, `-d` passed where `vespera.sidecars.wsl-distribution` names one, and as it is elsewhere.

- **Exit 0 is installed; any other exit is not.** A command killed at its limit, exit -1, is one not installed: a client that cannot say its version in 30 s is not one `up` can use. What a check printed is not read, and not shown.
- **Killed after 30 s**, the engine check's limit (ADR-390 §2). Each answered in under a second (A, B); neither asks the engine, so neither waits on it.
- **A program that cannot be started** is Docker not installed, ADR-390 §6's line: the program is the engine check's, which has just answered.
- **An output file that cannot be written or read** refuses in ADR-326's line, naming the check, and nothing is run after it.

**Why buildx always.** ADR-323 §4's `up` always carries `--build`, kept so that the Docling image is never asked of a registry, and Compose builds through buildx.

### 2. Where they run: after the engine answers, before `up`, under `sidecars.lock`

In ADR-323 §3's order, between step 6 and step 7: the engine made to answer, then the check for Compose, then the check for buildx, then `up`. Both are run whatever the first answered, so one line names both where both are missing. Both run under `sidecars.lock`, taken at step 5, as every command of a start does.

So neither runs where the start ends before step 6: a base URL on another machine (step 2), both sidecars answering (step 3, or step 5 under the lock), an image no compose file builds (step 4), or Docker not installed, which the engine check finds first. A `vespera run` or `vespera label --auto` on a machine whose sidecars answer never meets them; the control panel's start, which asks no sidecar, does.

**The control panel's start** (`startTheEngine`, ADR-390 §4) runs the same two, under the same lock, once the engine answers: a start of the engine with no Compose still leaves the next `vespera run` unable to start the sidecars, and the window says so as it opens rather than at that run's start. Where one is missing, its line is the refusal's, shown where ADR-390 §5 shows one.

**Not the stops.** The shut-down and the window's stop of the sidecars (ADR-390 §3) run neither: they stop what is running, and are unchanged.

**Why after the engine.** The engine check is what says whether there is a `docker`, or a `wsl`, at all, and it keeps its line for that; a check of a plugin before it would answer for a client that is not there. Under the lock, a start of the sidecars that waits for another finds the sidecars answering and runs neither.

**Why every start.** The operator installs what the line names and runs the same command again; a remembered answer would need a file of its own and would outlive the next change to the distribution. Two commands under a second each, only where Vespera is about to start something.

### 3. The line

`ComposeOrBuildxIsNotInstalledException`, extending `SidecarStartRefusedException`, one line, exit 1:

> Vespera cannot start the sidecars: the Docker engine answers, but &lt;missing&gt; &lt;is|are&gt; not installed &lt;where&gt;. Install &lt;it|them&gt; with &lt;install&gt;, and run the same command again.

- **`<missing>`**: `docker compose`, `docker buildx`, or `docker compose and docker buildx`; `is` and `it` for one, `are` and `them` for both.
- **`<where>`**: on Windows, `in the WSL distribution <distribution>`, the name `vespera.sidecars.wsl-distribution` gives, stripped, or `in WSL's default distribution` where it is blank; elsewhere `on this machine`.
- **`<install>`**, naming only what is missing, Compose's first:

| System | `<install>` | Compose's, buildx's |
|---|---|---|
| Windows | `wsl [-d <distribution>] -u root --exec apt-get install -y <packages>, or <Docker's packages> where Docker Engine came from Docker's own repository` | `docker-compose-v2`, `docker-buildx`; Docker's: `docker-compose-plugin`, `docker-buildx-plugin` |
| Linux | `sudo apt-get install -y <packages>, or <Docker's packages> where Docker Engine came from Docker's own repository` | as on Windows |
| macOS | `brew install <formulae>, add $HOMEBREW_PREFIX/lib/docker/cli-plugins to cliPluginsExtraDirs in ~/.docker/config.json` | `docker-compose`, `docker-buildx` |

Two of them whole. On Windows, with `Ubuntu-24.04` named and both missing:

> Vespera cannot start the sidecars: the Docker engine answers, but docker compose and docker buildx are not installed in the WSL distribution Ubuntu-24.04. Install them with wsl -d Ubuntu-24.04 -u root --exec apt-get install -y docker-compose-v2 docker-buildx, or docker-compose-plugin docker-buildx-plugin where Docker Engine came from Docker's own repository, and run the same command again.

On macOS, with Compose missing:

> Vespera cannot start the sidecars: the Docker engine answers, but docker compose is not installed on this machine. Install it with brew install docker-compose, add $HOMEBREW_PREFIX/lib/docker/cli-plugins to cliPluginsExtraDirs in ~/.docker/config.json, and run the same command again.

**Why it says the engine answers.** The line #859 met gave no way to tell whether Docker had started; this one says it has, and that what is missing is the client's.

**Why Ubuntu's packages first.** ADR-390 §1's distribution is the operator's, Ubuntu by the README's example, and Ubuntu's `docker.io` is the engine that comes without them (Context). An engine from Docker's own repository comes with both as Docker documents it, so its names are the alternative; the two sets conflict, so the line names the one that fits each source. `wsl … --exec` runs `apt-get` as root inside the distribution as every command of ADR-390 §1 runs, with no shell between. `sudo` on Linux is the operator's, typed by the operator: Vespera runs nothing under it (ADR-047).

**The same line everywhere.** `vespera run` and `vespera label --auto` print it on standard error and exit 1 before the job starts or the labeller is reached, as ADR-323 §8's lines; the control panel shows it as `startTheEngine`'s refusal (ADR-390 §4, §5).

### 4. No run id moves

`HostSystem`, `SidecarStarter` and the new exception are in `pipeline`, which no stage's version names (ADR-222), and none decides a verdict, a cache key, a cluster or text of the deliverable. No stage moves, and no pass is owed.

## Alternatives rejected

- **Read `up`'s exit and rewrite its line.** Exit 125 with an unknown flag is the client's answer to any subcommand it lacks, or to any flag it does not know (A); reading Compose's absence from it is a guess at text that is Docker's, and buildx missing fails later, mid-build, in Compose's own words.
- **Check Compose alone, and leave buildx to `up`.** The operator would meet the second missing package one start later, after installing the first.
- **Learn where Docker Engine came from, with `dpkg-query` or `/etc/os-release`, and name one set of packages.** A third command at every start, reading a package database or a file whose shape is the distribution's, for one line; the line names both sets instead.
- **`docker info`, whose `ClientInfo.Plugins` lists both.** It asks the engine, and its answer is JSON in a shape Docker's; the two `version`s ask only the client and are read by their exit.
- **Install them.** Nothing of Vespera's installs a package or runs under `sudo` (ADR-047); what a machine has installed is the operator's.
- **Check before the engine.** §2: the engine check is what tells Docker not installed apart, and keeps its line.
- **Check once and remember.** §2: a file to keep, outliving the next change to the distribution, for under two seconds saved where Vespera is about to start something.
- **Show the check's output in the line.** It is the `docker --help` text #859 asked not to see.

## Consequences

- **A start that would have failed at `up` with `docker --help`'s text** is refused before it, in a line that says the engine answers, what is missing, where, and how to install it.
- **Every start that reaches the engine runs two commands more**, each under a second as measured, the control panel's start as its window opens among them; a start that finds the sidecars answering runs none.
- **The README's "Running it"** names, in the one-time setup on Windows, Compose and buildx beside Docker Engine, with Ubuntu's two packages; and its list of what stands in the way of a start names `docker compose` or `docker buildx` not installed.
- **`ScriptedDocker`** answers the two checks as installed unless a test takes one away, as the client answers a subcommand it has no plugin for.
- **`PipelineHoldsOnlyTheRulesOnRecordTest`** lists `ComposeOrBuildxIsNotInstalledException` as holding no rule.

## What this does not decide

- **Debian's own archive.** Debian names Compose `docker-compose`, where Ubuntu names it `docker-compose-v2`, so on a Debian distribution whose engine is Debian's `docker.io`, the first command the line names is refused by `apt-get`; the operator installs Debian's. A Linux without `apt-get` is the same case.
- **A buildx too old for the Compose installed.** Presence alone is checked; a Compose that refuses the buildx it finds fails `up` in its own line, which names buildx.
- **The stops with Compose missing.** The shut-down's and the closing's `docker compose -p vespera stop` give ADR-390 §6's lines, with the client's last line, as before.
- **A check of the rest of ADR-390 §1's setup**, as ADR-390 leaves it.

## Tests

| Method | What it holds |
|---|---|
| `pipeline.AMissingComposeOrBuildxIsNamedBeforeUpTest.aMissingComposeOrBuildxRefusesTheStartBeforeUpInOneLineSayingHowToInstallIt` | §1 to §3: on Windows with a distribution named and with the default one, on Linux and on macOS, with Compose, buildx or both missing, and with the check for Compose killed at its limit, the start of the sidecars is refused in §3's line, whole, after exactly the engine check and both checks, and no `up` |
| `pipeline.AMissingComposeOrBuildxIsNamedBeforeUpTest.theControlPanelsStartRefusesInTheSameLine` | §2: the control panel's start of the engine runs both checks inside the distribution named and refuses in the same line |
| `pipeline.AMissingComposeOrBuildxIsNamedBeforeUpTest.aCheckWhoseOutputFileFailsRefusesInThatFilesLineAndRunsNothingAfter` | §1: the check for Compose's output file failing refuses in ADR-326's line, whole, and no check for buildx or `up` follows |
| `pipeline.AMissingComposeOrBuildxIsNamedBeforeUpTest.aCheckWhoseProgramCannotBeStartedIsDockerNotInstalled` | §1: on Linux and on Windows, the check for Compose's program not starting refuses as Docker not installed, in ADR-390 §6's line, whole, naming `docker` or `wsl`, and no check for buildx or `up` follows |

Amended:

- **`pipeline.ContainerStartTest.theEngineIsStartedByItsSystemsOneCommandThenUpRecreatesNothing` (named for the container by ADR-433, #891)**: on each system, the check for Compose and the check for buildx come between the wait's last engine check and `up`, inside the distribution on Windows, each given 30 s.
- **`pipeline.ContainerStartTest.theControlPanelsStartStartsTheEngineAloneAndAsksNoContainer` (named for the container by ADR-433, #891)**: the two checks follow the wait's checks, and no `up`.
- **`ScriptedDocker`** gains the two checks, `withoutCompose` and `withoutBuildx`.
- **`PipelineHoldsOnlyTheRulesOnRecordTest`** lists the new exception.

`pipeline.DockerShutDownTest`'s claims, each listing a stop's commands whole, hold that neither stop runs a check. `ContainerStartTest.bothAnsweringRunsNothing` (named for the container by ADR-433, #891) and `dockerNotInstalledNamesTheEngineChecksProgramOnEachSystem` hold that sidecars answering, or no `docker`, run neither.

What no unit test shows is what a `docker` client without Compose answers, which A stands for.

## What the implementation owes

Beside turning the tests above green: `HostSystem`'s two checks, run inside the distribution on Windows as `engineCheck` is; `SidecarStarter` running both, after the engine answers and before `up` in `ensureRunning`, and after it answers in `ensureTheEngineRuns`, each killed after the engine check's limit, a program that cannot be started read as Docker not installed and an output file that fails as ADR-326's refusal; and `ComposeOrBuildxIsNotInstalledException`, its line built from the system, the distribution and what is missing.
