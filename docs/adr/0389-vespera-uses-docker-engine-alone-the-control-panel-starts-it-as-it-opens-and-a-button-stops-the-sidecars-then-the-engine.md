# ADR-389 — Vespera uses Docker Engine alone, the control panel starts it as it opens, and a button stops the sidecars then the engine

- **Date**: 2026-11-14
- **Status**: accepted on 2026-11-14.
- **Decided**: by the operator, in [#816](https://github.com/algernon28/vespera/issues/816): that Vespera uses Docker Engine alone and never Docker Desktop (§1, §2); that the engine is Docker Engine inside a WSL 2 distribution on Windows, started headlessly through `wsl`, and Colima on macOS, started and stopped by Vespera (§1, §2, §3); that the engine is started when the control panel's window opens, and by `vespera run` and `vespera label --auto` as before, `publish` and `serve` starting nothing (§5); that Stop is unchanged, and a separate button beside it, usable whether or not a child runs, warns that stopping the engine stops every container on it, then runs `docker compose -p vespera stop`, never `down`, and then stops the engine, whoever started it (§3, §5). By Claude under the operator's delegation: which WSL distribution is used (§1); how a command reaches the engine inside it (§1); what keeps that distribution running (§1); the commands, their order and their limits (§2, §3); how the control panel reaches them (§4); what the button does with a child running, and what it shows when a command is refused (§3, §5); the lines (§6).
- **Amends**: [ADR-323](0323-vespera-starts-the-docker-engine-and-the-sidecars-when-they-are-not-answering-recreates-and-stops-none-and-stage-2-waits-for-a-converter-still-starting.md) §5, whose table of start commands, Docker Desktop's on all three systems, is §2's of one command each; whose *"Every command of this section is killed if it has not exited after 30 s"* holds of the engine check alone, a start command being given `vespera.sidecars.wait-at-most` (§2); and whose reading of a program that cannot be started, as ADR-326 §2 leaves it, names the engine check's program, `wsl` on Windows (§2). §4: `up` is run inside the distribution on Windows (§1), and its never-run list loses `stop`, which the shut-down alone runs, and *"An engine Vespera started is left running too"*, which holds until the operator presses the button (§3). §8: Docker not installed is §6's line, and the shut-down and `vespera docker` have lines of their own (§6). Its *What this does not decide* loses *"The bound measured on Docker Desktop"*: nothing starts Docker Desktop. It carries a note at its head.
- **Amends**: [ADR-326](0326-a-command-whose-output-file-cannot-be-written-or-read-refuses-the-start-in-a-line-of-its-own-and-only-a-program-that-cannot-be-started-is-docker-not-installed.md) §2's *"where the program is `docker`"*: where it is the engine check's program, `docker`, or `wsl` on Windows (§2). It carries a note at its head.
- **Amends**: [ADR-302](0302-the-control-panel-is-a-launch-page-and-an-invocation-page-of-server-written-forms-each-report-re-served-read-only-in-a-sandbox-and-the-next-invocations-values-on-pages-of-their-own.md) §5, kept by ADR-323 and ADR-342, *"Nothing is started, stopped or pulled"*: the control panel starts the engine as its window opens, and stops the sidecars and the engine when the operator presses the button, each through a child of the jar (§4, §5). Its two checks still only read, and nothing is pulled. It carries a note at its head.
- **Amends**: [ADR-278](0278-the-control-panel-starts-the-jar-as-its-child-reads-its-output-to-the-end-and-stops-only-its-own-child.md) §1's *"nor in any other way than these"*: the control panel also starts the jar's `docker start` and `docker shut-down` (§4), which are no invocation it watches. It carries a note at its head.
- **Amends**: [ADR-362](0362-an-invocations-report-is-one-json-file-drawn-as-static-html-through-a-mustache-template-and-no-command-opens-anything.md) §2's one exception, *"starting Docker Desktop"*: none is left, and `HostSystem` names no way to open anything (§2). It carries a note at its head.
- **Amends**: [ADR-158](0158-the-operator-starts-the-sidecars-from-compose-yaml-and-the-packaged-jar-starts-none.md)'s *"The jar does not start or stop them"*, as ADR-323 left it, *"still stops none"*: the jar stops them, with `stop` and nothing harder, only when the operator asks it to (§3). It carries a note at its head.
- **Amends**: [ADR-179](0179-no-entry-point-starts-the-sidecars-the-docling-sidecar-reports-the-image-it-runs-and-ollamas-models-live-in-a-volume.md)'s *"No entry point starts or stops a sidecar"*, as ADR-323 left it, *"nothing recreates, stops or removes a container"*: the shut-down stops them (§3); nothing recreates or removes one. It carries a note at its head.
- **Restores**: the stop half of [ADR-011](0011-managed-containers-the-tool-owns-its-sidecars.md)'s *"The app starts/stops its own dependencies"*, on the operator's press alone (§3, §5). ADR-011 is a reconstituted record and carries no note.
- **Keeps**: ADR-323 §1 to §3, §6, §7, §9 and §10: which commands start the sidecars and when, the order of a start, the locks, stage 2's wait, the opt-out for every start, and no run id moving. ADR-323 §4's `up` with `--build` and `--no-recreate`, and its never-run `--force-recreate`, `down`, `rm`, `kill`, `restart` and `pull`.
- **Keeps**: [ADR-164](0164-every-sidecar-restarts-unless-the-operator-stopped-it.md): `restart: unless-stopped` on every service; a container the shut-down stopped is one the operator stopped (§3).
- **Keeps**: [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3: the control panel links no class of the jar, and reaches the engine through a child of it (§4).
- **Keeps**: [ADR-047](0047-the-pipeline-never-blocks.md): nothing prompts; nothing runs under `sudo`.
- **Settles**: [#816](https://github.com/algernon28/vespera/issues/816).
- **Rests on**: probe H below, run on Linux against Docker Engine 29.9.2 and Docker Compose v5.6.1, in a throwaway project of two services shaped as ADR-323's probes were; and, for WSL, Microsoft's documentation of `wsl.exe` and of `.wslconfig`, and two reports on WSL's own tracker.

## Context

ADR-323 §5 starts the engine with Docker Desktop on Windows and macOS, and tries Docker Desktop after `systemctl` on Linux. The operator runs Docker Engine alone, so those commands start nothing that is wanted, and on a machine with Docker Desktop installed they start a product the operator does not use. ADR-323 §4 and ADR-179 have nothing in Vespera stop a sidecar or the engine; the operator wants a way to stop both from the control panel when a curation is done, without changing what Stop does.

### Measured

**H. `stop` keeps the containers, and they stay stopped across a restart of the engine until `up` starts them.**

| Step | What it printed, and exit | Containers after |
|---|---|---|
| `docker compose -p p816 stop`, run from a folder holding no compose file, both running | `Stopping`, `Stopped` for each; 0, in 0.2 s | both `Exited`, the same two ids; the named volume kept |
| the same `stop` again | the same lines; 0 | unchanged |
| `stop` of a project that has no container | nothing; 0 | — |
| the daemon stopped, then the same `stop` | `failed to connect to the docker API at unix:///var/run/docker.sock; check if the path is correct and if the daemon is running: …`; 1 | — |
| the daemon started again | — | both still `Exited`, the same ids |
| `up -d --build --no-recreate` | `Starting`, `Started` for each; 0 | both running, the same ids |

Contrast, measured the same way: with both containers running and no `stop`, the daemon ended in 1.3 s of `SIGTERM`, and both were running again under the same ids 2 s after it was started, with no `up`, as ADR-323's probe F found. So stopping the containers first is what keeps them stopped across the engine's next start; stopping only the engine leaves them to come back with it.

`systemctl --no-ask-password stop docker.socket docker.service` and `systemctl --no-ask-password start docker` each exited 1 with `Failed to connect to bus: Host is down` on the probe machine, which has no systemd.

**WSL, read and not measured.** Microsoft's `.wslconfig` documentation gives `instanceIdleTimeout`, under `[general]`, 15000 ms by default: *"The number of milliseconds that a distro is idle, before it is shut down. Set to -1 to disable auto shutdown"*; `systemd=true` under `[boot]` of the distribution's `/etc/wsl.conf` makes systemd its init; and `localhostForwarding`, true by default, makes *"ports bound to wildcard or localhost in the WSL 2 VM … connectable from the host via `localhost:port`"*. WSL's tracker holds a report that a distribution with systemd and Docker Engine running a container was shut down once its shell exited (microsoft/WSL#8854), and one that from WSL 2.6.1 a running systemd service no longer keeps a distribution up, where on 2.5.10 it did (microsoft/WSL#13416). So systemd alone does not keep the distribution, and its daemon, running on every version.

**Not measured:** anything on Windows or macOS: `wsl --cd` with a Windows folder, `wsl --exec`, how long the distribution and its daemon take to answer from cold, whether `instanceIdleTimeout=-1` keeps them across hours with no Windows process attached, and port forwarding to the sidecars; `colima start` and `colima stop`, and how long each takes; the message `systemctl` gives a user polkit refuses; the reactivation of `docker.service` through `docker.socket`, which is read from the unit files Docker Engine's packages install.

## Decision

### 1. Docker Engine on each system, and how a command reaches it

| System | The engine | How a `docker` command reaches it |
|---|---|---|
| Linux | Docker Engine, the `docker` service under systemd | run as it is |
| Windows | Docker Engine inside a WSL 2 distribution, with systemd as its init | run inside the distribution: `wsl [-d <distribution>] -u root --exec docker …` |
| macOS | Colima | run as it is; `colima start` points the `docker` command-line client at Colima's engine |

**Which distribution.** `vespera.sidecars.wsl-distribution`, `VESPERA_SIDECARS_WSL_DISTRIBUTION` in the environment, names it, and `-d <distribution>` is passed; unset or blank, no `-d` is passed and WSL's default distribution is used. It is read on Windows alone, and is an application property and no profile value, for ADR-323 §7's reason: it says where Docker runs on one machine, not what the operator judged about a corpus. It is no launch value of the control panel's, which passes it on only as its child inherits the environment.

**`wsl … --exec docker`, not `DOCKER_HOST`.** Every `docker` command Vespera runs on Windows, the engine check, `up` and the shut-down's `stop`, runs inside the distribution:

- **`-u root`**: the socket answers root without the operator joining the `docker` group, and WSL asks no password for it.
- **`--exec`**: no shell stands between, so an argument such as `{{.Server.Version}}` reaches `docker` as given.
- **`up` is given its folder with `--cd`**: `wsl [-d <distribution>] -u root --cd <folder> --exec docker compose -p vespera -f compose.yaml [-f compose.gpu.yaml] up -d --build --no-recreate`, `<folder>` being the Windows path of the folder ADR-323 §4 writes the files out to, which `wsl` turns into the distribution's own, and the files named in it by their names. The build context, relative to `compose.yaml`, resolves in the same folder.

A `docker` client on Windows pointed at the engine by `DOCKER_HOST` would need a client installed on Windows beside the one in the distribution, and the engine listening on a TCP port, which without TLS gives every local process root inside the distribution, or a relay of the socket from a third-party tool. `wsl` is on every Windows that runs WSL 2, and the client is the distribution's.

**What keeps the distribution running.** Vespera's own `wsl` commands end as each returns, and a run reaches the sidecars through the forwarded ports and not through `wsl`, so nothing of Vespera's keeps the distribution up while a run talks to it. A distribution WSL shuts down takes the engine and the sidecars with it, and stage 2 then waits ADR-175's bound and fails. So the one-time setup on Windows is three things, the operator's to make and Vespera's to neither write nor read:

- Docker Engine installed in the distribution;
- `systemd=true` under `[boot]` in its `/etc/wsl.conf`, so that `systemctl` starts and stops the engine (§2, §3);
- `instanceIdleTimeout=-1` under `[general]` in `%UserProfile%\.wslconfig`, so that WSL does not shut the distribution down for being idle, which systemd alone does not prevent (Measured).

**The sidecars' addresses are unchanged.** WSL 2 forwards a port published inside the distribution to Windows's `localhost`, so the shipped loopback addresses reach the sidecars and ADR-323 §3's step 2 still reads them as this machine's.

### 2. The engine is started with one command on each system

| System | Engine check | Start |
|---|---|---|
| Linux | `docker version --format {{.Server.Version}}` | `systemctl --no-ask-password start docker` |
| Windows | `wsl [-d <distribution>] -u root --exec docker version --format {{.Server.Version}}` | `wsl [-d <distribution>] -u root --exec systemctl start docker` |
| macOS | `docker version --format {{.Server.Version}}` | `colima start` |

**One command, no second.** Each system has one way its engine is started, and no fallback is tried. A start command that exits non-zero refuses with ADR-323 §8's line naming it. No command of Vespera's names Docker Desktop, `cmd`, `open` or `Docker Desktop.exe`.

**Limits.** The engine check is killed after 30 s, as ADR-323 §5 has it. A start command is killed after `vespera.sidecars.wait-at-most`: `colima start` returns only once its machine answers, and `systemctl start` once `dockerd` says it is ready, so each may take most of what the wait would. The wait for the engine then follows, as before.

**A program that cannot be started.** Where it is the engine check's, `docker`, or `wsl` on Windows, the command is refused as Docker not installed (§6). A start command's program that cannot be started, `systemctl` without systemd or `colima` not installed, counts as exit 127 with its reason, ADR-323 §5's rule, and, being the one command, refuses with the line naming it.

### 3. The shut-down: the sidecars, then the engine

The jar's `docker shut-down` (§4) runs, in this order:

1. **The sidecars**: `docker compose -p vespera stop`, inside the distribution on Windows (`wsl [-d <distribution>] -u root --exec docker compose -p vespera stop`). It names the project alone, which reaches the containers from any folder (ADR-323's G, and H).
2. **The engine**, only where step 1 exited 0:

| System | Stop |
|---|---|
| Linux | `systemctl --no-ask-password stop docker.socket docker.service` |
| Windows | `wsl [-d <distribution>] -u root --exec systemctl stop docker.socket docker.service` |
| macOS | `colima stop` |

**`stop`, never anything harder.** `stop` keeps the containers and their volume, so the next start's `up --no-recreate` starts the same containers as they were (H) and ADR-323 §4's guarantee holds. `down`, `rm`, `kill`, `restart`, `--force-recreate` and `pull` stay never-run, by the shut-down as by the start.

**Containers first.** A container stopped by `stop` stays stopped when the engine starts again; one the engine stopped as it ended comes back with it (H). Stopping the engine alone would leave Vespera's sidecars to come back at the next start of the engine, the control panel's own among them.

**`docker.socket` with the service.** Docker Engine's packages install a socket unit that starts `docker.service` again at the next connection to the socket, so stopping the service alone lasts until the next `docker` command. Both are stopped in one `systemctl` call; `systemctl start docker` starts both again.

**No engine check first.** On Linux the check would itself start an engine stopped behind its socket. Where the engine is not running, step 1 says so by its own exit, and nothing is stopped.

**Any running engine.** The shut-down stops the engine whoever started it, and with it every container on it, Vespera's or not. The control panel's warning says so (§5).

**It runs whatever `vespera.sidecars.start` says.** The opt-out governs what Vespera starts unasked (ADR-323 §9); the shut-down is the operator's own act.

**It holds `sidecars.lock`** (ADR-323 §6), waited for as a start waits for it, so a shut-down and a start never run their commands together.

**Limits.** Each of the two commands is killed after `vespera.sidecars.wait-at-most`: `stop` waits for each container's stop and `systemctl stop` for the engine's, and a command killed counts as exit -1, as ADR-323 §5's do.

**When a command is refused.** Each is one line, exit 1 (§6), and nothing else fails:

- **Step 1 exits non-zero, or its program cannot be started** (exit 127): the engine is not stopped, so a refusal of step 1 leaves both as they were.
- **Step 2 exits non-zero**: the sidecars stay stopped, and the line names the command and its last line, `systemctl` refused by polkit on Linux, `wsl` or `colima` failing. The next start brings them up again, with the engine where it is not running.

### 4. The control panel reaches the engine through a child of the jar

`EntryPoint.main` hands `docker` on before `VesperaApplication.main`, as it hands `serve` on (ADR-333 §8): `DockerCommand`, a public class of `pipeline`, runs with no Spring context, no datasource, no log file and no lock but `sidecars.lock`, writing its one line to the console. It takes one argument:

- **`docker start`**: ADR-323 §3's steps 1 and 2, `vespera.sidecars.start` false or a base URL on another machine doing nothing, then, holding `sidecars.lock`, §2's engine check, its start and ADR-323 §5's wait. No sidecar is asked and no `up` is run. It exits 0, printing nothing, where the engine answers or nothing was to be done, and 1 with ADR-323 §8's line otherwise.
- **`docker shut-down`**: §3, exiting 0 or 1 with §6's line.
- **Anything else**: §6's usage line, exit 1.

It reads `vespera.sidecars.start`, `vespera.sidecars.wait-at-most`, `vespera.sidecars.wsl-distribution`, `vespera.corpus-root-lock-folder`, `vespera.docling.base-url` and `spring.ai.ollama.base-url` from its arguments, given as `--<property>=<value>`, then the JVM's system properties, then the environment, which is `serve`'s order, and reads no file of configuration; a base URL set nowhere is this machine's, as `application.yaml`'s are. `HostSystem`, the starter's commands, the lock and the lines are the ones `vespera run` uses, so the control panel holds no second copy of the table.

**Why a command of the jar.** The control panel links no class of the jar (ADR-285 §3), so it cannot call the starter; running `docker`, `wsl`, `systemctl` or `colima` itself would be ADR-323's rejected second starter, its table, lock and lines copied. A child of the jar is the contract the two already have (ADR-278). Handed on before Spring, it takes no working-directory lock, so an invocation the operator starts while it runs is not refused.

**The child.** `ChildCommand.docker(java, jar, values, action)`: `java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar <jar> docker <action>`, every path absolute, no `--db-dir`, its environment the control panel's less ADR-340 §6's eight, with `CI=1` and the launch values' variables added, as an invocation's is, so `VESPERA_DOCLING_BASE_URL`, `SPRING_AI_OLLAMA_BASE_URL` and any `VESPERA_SIDECARS_*` the operator set reach it. Its output is read to its end, as ADR-278 §3 reads an invocation's, and its last line that is not blank is what it said. It is not the child the window watches: no report is read for it, its output is not in the output panel, and Run and Stop do not see it. `DockerControl`, an interface of the control panel's, starts it: `start(values)` and `shutDown(values)` each run the child to its end for the launch values held and answer a `Said`, whether it exited 0 and its line. `DockerControl.throughTheJar(runtime)` is the window's, given to `ControlPanelState`'s constructor of five arguments by `ControlPanel.open`; `DockerControl.NONE`, which starts nothing and answers done with no line, is the one the constructor of four gives.

### 5. The window

**As it opens.** Once the window is shown, `docker start` is started once, on a thread of its own: the window does not wait for it, and Run stays enabled. An invocation started meanwhile meets `sidecars.lock` and waits for it (ADR-323 §6). Where the child exits 1, its line is shown in the line the window says what happened in (`outcome`); where it exits 0, nothing is shown.

**The button.** *Shut down Docker*, id `shut-down-docker`, stands in the action row after Stop. It is enabled whether or not a child runs, and disabled only while a shut-down it started runs. Stop, and the confirmation of closing the window, are unchanged: closing the window shuts nothing down.

Pressed, it asks first, with *Shut down Docker* to go on and *Keep Docker running* not to:

> Shutting down Docker stops Vespera's sidecars and then the Docker engine, even if Vespera did not start it, and with the engine every other container running on it.

and, while a child runs, the same sentence followed by:

> The invocation running now is stopped first. Stopping loses the work done since the running step's last commit, and the next vespera run continues from there.

the second sentence being Stop's own (ADR-278 §6). Kept running, nothing is run. Gone on with: the child, where one runs, is stopped as Stop stops it and waited for; then `docker shut-down` runs; then its line is shown in `outcome`, and the button is enabled again. Nothing else of the window changes, whatever the line.

**Why the child is stopped first, and the button not disabled.** The operator wants it usable while a child runs. A child left running would lose its sidecars partway, and stage 2 would wait ADR-175's bound and fail; and a child still starting would bring them up again with its own `up` right after the shut-down. Stopped first, it loses what Stop loses, and the warning says so.

### 6. The lines

Each is one line; `<command>`, `<code>` and `<last line>` are as in ADR-323 §8, and `<program>` is `docker`, or `wsl` on Windows.

- **Docker not installed** (`DockerIsNotInstalledException`), replacing ADR-323 §8's:
  > Vespera cannot start Docker: no &lt;program&gt; command was found. Install Docker Engine -- in a WSL 2 distribution on Windows, with Colima on macOS, as the docker service on Linux -- and run the same command again, or start the sidecars yourself and set VESPERA_SIDECARS_START=false.
- **The shut-down done**, exit 0, on standard output:
  > the sidecars and the Docker engine are stopped; Vespera starts them again when it next needs them.
- **Step 1 refused**, exit 1, on standard error:
  > Docker was not shut down: &lt;command&gt; exited &lt;code&gt;: &lt;last line&gt;. The Docker engine was not stopped.
- **Step 2 refused**, exit 1, on standard error:
  > the sidecars are stopped, but the Docker engine is not: &lt;command&gt; exited &lt;code&gt;: &lt;last line&gt;. Stop Docker yourself, or leave it running.
- **The lock, or a command's output file, unusable**, exit 1, on standard error, `<reason>` being what ADR-323 §8's and ADR-326's lines say of each:
  > Docker was not shut down: &lt;reason&gt;.
- **`docker` given no action or another**, exit 1:
  > vespera docker takes one argument: start or shut-down.

ADR-323 §8's other lines are unchanged, and `docker start` refuses with them.

### 7. No run id moves

`HostSystem`, `SidecarStarter`, `SidecarStartSettings` and `DockerCommand` are in `pipeline`, which no stage's version names (ADR-222), and the rest is the control panel's. None decides a verdict, a cache key, a cluster or text of the deliverable. No stage moves, and no pass is owed.

## Alternatives rejected

- **Keep Docker Desktop as a fallback.** The operator does not use it, and starting it where installed starts a product the operator chose against.
- **`DOCKER_HOST` on Windows.** §1: a second client, and an engine on a TCP port without TLS or behind a third-party relay.
- **Dockerd as a Windows service.** Docker Engine for Windows runs Windows containers; both sidecars are Linux images.
- **`service docker start` inside the distribution.** With systemd as init, which §1 requires, it hands over to `systemctl`, and `service docker stop` would leave `docker.socket` to start the engine again.
- **A process of Vespera's holding the distribution up** (`wsl … sleep infinity` for as long as the control panel or a run lives). It would not cover an operator's own `docker compose`, and ends with the process that holds it; the WSL setting is documented and lasts.
- **Stop the engine alone.** H: the sidecars come back with its next start.
- **`docker compose down`.** It removes the containers, and the next `up` creates them from whichever files it names, which is ADR-179's incident.
- **Stop the engine even where `stop` failed.** The operator asked for containers first; a failed `stop` is shown and changes nothing.
- **Stop only an engine Vespera started.** The operator decided it stops any.
- **Disable the button while a child runs.** The operator wants it usable either way.
- **The option as a box beside Stop, or in Stop's confirmation.** The operator chose a separate button, and Stop unchanged.
- **The control panel runs the commands itself.** §4: a second copy of `HostSystem`'s table, the lock and the lines, in a package that links no class of the jar.
- **A command booted through Spring.** It would take the working-directory lock and refuse, or be refused by, the invocation the operator starts as the window opens.

## Consequences

- **No command of Vespera names Docker Desktop.** `HostSystem` holds §2's and §3's commands, and ADR-362 §2 has no exception left.
- **On Windows, Vespera needs a WSL 2 distribution with Docker Engine, systemd and `instanceIdleTimeout=-1`**, the one-time setup of §1; a GPU there also needs NVIDIA's container toolkit in the distribution, as on Linux. **On macOS, Colima and the `docker` command-line client**, and the first `colima start`, which downloads Colima's machine, is the operator's.
- **Opening the control panel starts the engine** where it is not running and the sidecars are looked for on this machine, unless `VESPERA_SIDECARS_START=false` is in the environment: `docker start` reads no file of configuration, so `vespera.sidecars.start: false` set only in `application-local.yaml` does not reach it, as a value set only there does not reach `serve`.
- **A shut-down lasts until the next start**: the next `vespera run` or `vespera label --auto` starts the engine and the sidecars again, and the next opening of the window the engine.
- **The README's "Running it"** says the control panel starts Docker as it opens and that *Shut down Docker* stops the sidecars and then the engine, every container on it with it; drops Docker Desktop and *"Nothing in Vespera stops them"*; names the one-time setup on Windows of §1; and names `vespera docker start` and `vespera docker shut-down` beside `vespera serve`.
- **`CONTEXT.md`'s Control panel entry** says it starts the Docker engine as its window opens and stops the sidecars and the engine on its button, through the jar.
- **`docs/architecture.md`'s technology table** says the sidecars are stopped by the control panel's *Shut down Docker* alone, citing this record.
- **`application.yaml` gains `vespera.sidecars.wsl-distribution`**, empty, with its comment citing this record.
- **`DockerIsNotInstalledException` takes the program** the line names.

## What this does not decide

- **A check of the WSL setup**: whether Vespera should read `.wslconfig` or `/etc/wsl.conf` and warn where §1's setup is missing.
- **Rootless Docker, Podman, remote Docker contexts and a Colima profile other than the default**, as ADR-323 §5 leaves the first three.
- **The bound measured on WSL and Colima.** 180 seconds is ADR-323's, reasoned from restarts on Linux; neither cold start was measured.
- **Shutting down the WSL distribution, or Colima's machine beyond `colima stop`.** The shut-down stops the engine; the distribution stays up under §1's setting.

## Tests

| Method | What it holds |
|---|---|
| `pipeline.EachSystemsDockerCommandsTest.eachSystemStartsAndStopsItsEngineWithOneCommandAndNoneIsDockerDesktops` | §2, §3: each system's engine check, start, compose stop and engine stop, whole; no command of any system names `desktop`, `Docker Desktop.exe`, `cmd`, `open` or `down`; `os.name` read as ADR-323 §5 reads it |
| `pipeline.EachSystemsDockerCommandsTest.onWindowsEveryDockerCommandRunsInsideTheNamedWslDistribution` | §1: `-d <distribution>` where one is named and none where it is blank; `up` given its folder by `--cd` and its files by their names; on Linux and macOS `up` names the files' paths |
| `pipeline.SidecarStartTest.theEngineIsStartedByItsSystemsOneCommandThenUpRecreatesNothing` | §2, ADR-323 §4: the engine down on each system, the commands are exactly the check, the system's one start, the checks of the wait and `up` |
| `pipeline.SidecarStartTest.theControlPanelsStartStartsTheEngineAloneAndAsksNoSidecar` | §4's `docker start`: the engine down, the check, the start and the wait's checks, no `up` and no sidecar asked; nothing run where `vespera.sidecars.start` is false or a sidecar is on another machine |
| `pipeline.DockerShutDownTest.theSidecarsAreStoppedThenTheEngineAndNothingElseIsRun` | §3, §6: on each system, exactly the compose stop then the engine stop, each limited by the bound; exit 0 and the done line; and the same where `vespera.sidecars.start` is false |
| `pipeline.DockerShutDownTest.aStopThatFailsLeavesTheEngineAsItWas` | §3, §6: the compose stop exiting 1, or its program not found, runs no engine stop, exit 1 and step 1's line |
| `pipeline.DockerShutDownTest.anEngineStopRefusedLeavesTheSidecarsStoppedAndSaysSo` | §3, §6: `systemctl` refused, and `colima` not found as exit 127, exit 1 and step 2's line naming the command and its last line |
| `ui.ChildCommandTest.dockerIsTheJarsDockerCommandWithNoWorkingDirectory` | §4: the child's command line and environment |
| `ui.TheShutDownDockerButtonTest.theEngineIsStartedOnceAsTheWindowOpens` | §5: one `docker start` for the launch values held, no shut-down, and a refusal's line shown in `outcome` |
| `ui.TheShutDownDockerButtonTest.theButtonWarnsFirstAndRunsNothingWhenKept` | §5: the button stands after Stop; the warning's words and answers, whole; kept running, no shut-down |
| `ui.TheShutDownDockerButtonTest.withAChildRunningItIsStoppedFirstThenTheShutDownsLineIsShown` | §5: the warning with Stop's sentence; the child ended before the shut-down ran; the refusal's line in `outcome`, the button and Run enabled again |

Amended:

- **`pipeline.SidecarStartTest.theOnlyCommandsAreTheEngineCheckItsStartAndAnUpThatRecreatesNothing`** becomes `theEngineIsStartedByItsSystemsOneCommandThenUpRecreatesNothing`, above.
- **`pipeline.NoCommandOpensAnythingTest.noClassOfTheJarNamesAWayToOpenAFile`** exempts no class: `HostSystem` loads none of `rundll32`, `xdg-open`, `cmd` and `open`.
- **`ScriptedDocker`** reads a command ending in the engine check as one, inside `wsl` or not, takes `wsl` for a program that is not there where Docker is not installed, and records each command's limit.

What no unit test shows is what `wsl`, Colima and systemd do with these commands; probe H is the evidence for Docker Engine and Compose on Linux.

## What the implementation owes

Beside turning the tests above green: `HostSystem`'s five commands taking the distribution, and `engineStartCommands` gone; `SidecarStartSettings.wslDistribution`, blank where its five-argument constructor builds it; `SidecarStarter.ensureTheEngineRuns`, its outcomes `ENGINE_ANSWERING` and `ENGINE_STARTED`, and `SidecarStarter.shutDown`, giving a `ShutDown` of an exit code and a line; `DockerCommand` and its hand-on in `EntryPoint`; `DockerIsNotInstalledException`'s line; `ChildCommand.docker`, `DockerControl` and the five-argument `ControlPanelState`, and `ControlPanel.open` building its state with `DockerControl.throughTheJar`; the button and the start as the window opens; `application.yaml`'s key; and the README, `CONTEXT.md` and `docs/architecture.md` lines of Consequences.
