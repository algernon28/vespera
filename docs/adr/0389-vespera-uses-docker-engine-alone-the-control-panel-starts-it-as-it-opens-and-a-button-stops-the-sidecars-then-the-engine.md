# ADR-389 — Vespera uses Docker Engine alone, the control panel starts it as it opens, and a button stops the sidecars then the engine

- **Date**: 2026-11-14
- **Status**: accepted on 2026-11-14.
- **Decided**: by the operator, in [#816](https://github.com/algernon28/vespera/issues/816): that Vespera uses Docker Engine alone and never Docker Desktop (§1, §2); that the engine is Docker Engine inside a WSL 2 distribution on Windows, started headlessly through `wsl`, and Colima on macOS, started and stopped by Vespera (§1, §2, §3); that the engine is started when the control panel's window opens, and by `vespera run` and `vespera label --auto` as before, `publish` and `serve` starting nothing (§5); that Stop is unchanged, and a separate button beside it, usable whether or not a child runs, warns that stopping the engine stops every container on it, then runs `docker compose -p vespera stop`, never `down`, and then stops the engine, whoever started it (§3, §5); that the control panel calls the Docker code in its own process, naming one narrow class of the jar (§4); that closing the window stops the sidecars and not the engine (§5); and that `vespera.sidecars.start` is removed, so Vespera always starts the engine and the sidecars on this machine where they are not answering, sidecars on another machine still starting nothing (§7). By Claude under the operator's delegation: which WSL distribution is used (§1); how a command reaches the engine inside it (§1); what keeps that distribution running (§1); the commands, their order and their limits (§2, §3); the class the control panel names and where it takes its settings from (§4); what the button does with a child running, what closing runs where no sidecar answers, and what each shows when a command is refused (§3, §5); the lines (§6); and how a test keeps Docker out (§7).
- **Amends**: [ADR-323](0323-vespera-starts-the-docker-engine-and-the-sidecars-when-they-are-not-answering-recreates-and-stops-none-and-stage-2-waits-for-a-converter-still-starting.md) §5, whose table of start commands, Docker Desktop's on all three systems, is §2's of one command each; whose *"Every command of this section is killed if it has not exited after 30 s"* holds of the engine check alone, a start command being given `vespera.sidecars.wait-at-most` (§2); whose reading of a program that cannot be started, as ADR-326 §2 leaves it, names the engine check's program, `wsl` on Windows (§2); and whose *What is not covered* no longer ends on an opt-out (§7). §4: `up` is run inside the distribution on Windows (§1), and its never-run list loses `stop`, which the shut-down and the window's closing alone run, and *"An engine Vespera started is left running too"*, which holds until the operator presses the button (§3, §5). §8: Docker not installed is §6's line, and the shut-down and the closing have lines of their own (§6). §9, the opt-out, is struck, with §3's step 1 and §1's paragraph on `TestVesperaApplication`; its Consequence *"An operator who wants them to stay stopped sets `VESPERA_SIDECARS_START=false`"* has no object (§7). Its *What this does not decide* loses *"The bound measured on Docker Desktop"*: nothing starts Docker Desktop. It carries a note at its head.
- **Amends**: [ADR-326](0326-a-command-whose-output-file-cannot-be-written-or-read-refuses-the-start-in-a-line-of-its-own-and-only-a-program-that-cannot-be-started-is-docker-not-installed.md) §2's *"where the program is `docker`"*: where it is the engine check's program, `docker`, or `wsl` on Windows (§2). It carries a note at its head.
- **Amends**: [ADR-285](0285-the-control-panel-is-a-second-jar-of-this-build-under-a-package-of-its-own-outside-the-nine-modules-linking-no-class-of-the-jar.md) §3, as [ADR-342](0342-the-control-panel-is-a-javafx-window-inside-the-vespera-jar-opened-by-the-jar-started-with-no-argument-and-its-loopback-server-and-pages-are-gone.md) §3 leaves it, *"It names nothing of `io.algernon.vespera`"*: the control panel names one class of the jar, `DockerEngine` of `pipeline`, and no other (§4). Its reason, a second jar with Spring on its class path, went when ADR-342 put the control panel inside the jar. No class of the jar names the control panel or JavaFX, as before. Both carry a note at their heads.
- **Amends**: [ADR-302](0302-the-control-panel-is-a-launch-page-and-an-invocation-page-of-server-written-forms-each-report-re-served-read-only-in-a-sandbox-and-the-next-invocations-values-on-pages-of-their-own.md) §5, kept by ADR-323 and ADR-342, *"Nothing is started, stopped or pulled"*: the control panel starts the engine as its window opens, stops the sidecars as it closes, and stops the sidecars and the engine when the operator presses the button (§5). Its two checks still only read, and nothing is pulled. It carries a note at its head.
- **Amends**: [ADR-344](0344-the-control-panels-window-is-one-cockpit-run-and-stop-the-steps-as-bars-beside-the-output-the-progress-pages-three-charts-the-profile-and-the-folders-to-include-starting-from-the-real-configuration.md) §4: its five presets are four, `vespera.sidecars.start` gone with its row and its box (§7). It carries a note at its head.
- **Amends**: [ADR-362](0362-an-invocations-report-is-one-json-file-drawn-as-static-html-through-a-mustache-template-and-no-command-opens-anything.md) §2's one exception, *"starting Docker Desktop"*: none is left, and `HostSystem` names no way to open anything (§2). It carries a note at its head.
- **Amends**: [ADR-158](0158-the-operator-starts-the-sidecars-from-compose-yaml-and-the-packaged-jar-starts-none.md)'s *"The jar does not start or stop them"*, as ADR-323 left it, *"still stops none"*: the jar's code stops them, with `stop` and nothing harder, as the control panel's window closes and when the operator presses its button (§3, §5). It carries a note at its head.
- **Amends**: [ADR-179](0179-no-entry-point-starts-the-sidecars-the-docling-sidecar-reports-the-image-it-runs-and-ollamas-models-live-in-a-volume.md)'s *"No entry point starts or stops a sidecar"*, as ADR-323 left it, *"nothing recreates, stops or removes a container"*: the closing and the shut-down stop them (§3, §5); nothing recreates or removes one. It carries a note at its head.
- **Amends**: [ADR-164](0164-every-sidecar-restarts-unless-the-operator-stopped-it.md)'s note that a stopped container is started again *"unless `VESPERA_SIDECARS_START=false`"*: there is no such exception (§7). It carries a note at its head.
- **Restores**: the stop half of [ADR-011](0011-managed-containers-the-tool-owns-its-sidecars.md)'s *"The app starts/stops its own dependencies"*, as the window closes and on the operator's press (§3, §5). ADR-011 is a reconstituted record and carries no note.
- **Keeps**: ADR-323 §1, §2, §3's steps 2 to 9, §6, §7 and §10: which commands start the sidecars and when, a base URL naming another machine starting nothing, the order of a start, the locks, stage 2's wait, and no run id moving. ADR-323 §4's `up` with `--build` and `--no-recreate`, and its never-run `--force-recreate`, `down`, `rm`, `kill`, `restart` and `pull`.
- **Keeps**: ADR-164's `restart: unless-stopped` on every service; a container the shut-down stopped is one the operator stopped (§3).
- **Keeps**: [ADR-278](0278-the-control-panel-starts-the-jar-as-its-child-reads-its-output-to-the-end-and-stops-only-its-own-child.md) §1: the control panel starts the jar as `run`, `label` and `publish` alone; nothing of Docker is a child.
- **Keeps**: [ADR-047](0047-the-pipeline-never-blocks.md): nothing prompts; nothing runs under `sudo`.
- **Settles**: [#816](https://github.com/algernon28/vespera/issues/816).
- **Rests on**: probe H below, run on Linux against Docker Engine 29.9.2 and Docker Compose v5.6.1, in a throwaway project of two services shaped as ADR-323's probes were; and, for WSL, Microsoft's documentation of `wsl.exe` and of `.wslconfig`, and two reports on WSL's own tracker.

## Context

ADR-323 §5 starts the engine with Docker Desktop on Windows and macOS, and tries Docker Desktop after `systemctl` on Linux. The operator runs Docker Engine alone, so those commands start nothing that is wanted, and on a machine with Docker Desktop installed they start a product the operator does not use. ADR-323 §4 and ADR-179 have nothing in Vespera stop a sidecar or the engine; the operator wants the sidecars stopped when the control panel closes, and a way to stop both from it when a curation is done, without changing what Stop does. ADR-323 §9's opt-out let a machine keep Vespera from starting anything; the operator wants Vespera always to start what it needs.

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

**Not measured:** anything on Windows or macOS: `wsl --cd` with a Windows folder, `wsl --exec`, how long the distribution and its daemon take to answer from cold, whether `instanceIdleTimeout=-1` keeps them across hours with no Windows process attached, and port forwarding to the sidecars; `colima start` and `colima stop`, and how long each takes; the message `systemctl` gives a user polkit refuses; the reactivation of `docker.service` through `docker.socket`, which is read from the unit files Docker Engine's packages install; and that a converter's address Testcontainers registers comes before a JVM system property naming the same key, which is read from Spring's documentation of dynamic properties.

## Decision

### 1. Docker Engine on each system, and how a command reaches it

| System | The engine | How a `docker` command reaches it |
|---|---|---|
| Linux | Docker Engine, the `docker` service under systemd | run as it is |
| Windows | Docker Engine inside a WSL 2 distribution, with systemd as its init | run inside the distribution: `wsl [-d <distribution>] -u root --exec docker …` |
| macOS | Colima | run as it is; `colima start` points the `docker` command-line client at Colima's engine |

**Which distribution.** `vespera.sidecars.wsl-distribution`, `VESPERA_SIDECARS_WSL_DISTRIBUTION` in the environment, names it, and `-d <distribution>` is passed; unset or blank, no `-d` is passed and WSL's default distribution is used. It is read on Windows alone, and is an application property and no profile value, for ADR-323 §7's reason: it says where Docker runs on one machine, not what the operator judged about a corpus. `application.yaml` ships it empty.

**`wsl … --exec docker`, not `DOCKER_HOST`.** Every `docker` command Vespera runs on Windows, the engine check, `up` and `stop`, runs inside the distribution:

- **`-u root`**: the socket answers root without the operator joining the `docker` group, and WSL asks no password for it.
- **`--exec`**: no shell stands between, so an argument such as `{{.Server.Version}}` reaches `docker` as given.
- **`up` is given its folder with `--cd`**: `wsl [-d <distribution>] -u root --cd <folder> --exec docker compose -p vespera -f compose.yaml [-f compose.gpu.yaml] up -d --build --no-recreate`, `<folder>` being the Windows path of the folder ADR-323 §4 writes the files out to, which `wsl` turns into the distribution's own, and the files named in it by their names. The build context, relative to `compose.yaml`, resolves in the same folder.

A `docker` client on Windows pointed at the engine by `DOCKER_HOST` would need a client installed on Windows beside the one in the distribution, and the engine listening on a TCP port, which without TLS gives every local process root inside the distribution, or a relay of the socket from a third-party tool. `wsl` is on every Windows that runs WSL 2, and the client is the distribution's.

**What keeps the distribution running.** Vespera's own `wsl` commands end as each returns, and a run reaches the sidecars through the forwarded ports and not through `wsl`, so nothing of Vespera's keeps the distribution up while a run talks to it. A distribution WSL shuts down takes the engine and the sidecars with it, and stage 2 then waits ADR-175's bound and fails. So the one-time setup on Windows is three things, the operator's to make and Vespera's to neither write nor read:

- Docker Engine installed in the distribution;
- `systemd=true` under `[boot]` in its `/etc/wsl.conf`, so that `systemctl` starts and stops the engine (§2, §3);
- `instanceIdleTimeout=-1` under `[general]` in `%UserProfile%\.wslconfig`, so that WSL does not shut the distribution down for being idle, which systemd alone does not prevent (Measured);

each taking effect once WSL has been shut down with `wsl --shutdown`.

**The sidecars' addresses are unchanged.** WSL 2 forwards a port published inside the distribution to Windows's `localhost`, so the shipped loopback addresses reach the sidecars and ADR-323 §3's step 2 still reads them as this machine's.

### 2. The engine is started with one command on each system

| System | Engine check | Start |
|---|---|---|
| Linux | `docker version --format {{.Server.Version}}` | `systemctl --no-ask-password start docker` |
| Windows | `wsl [-d <distribution>] -u root --exec docker version --format {{.Server.Version}}` | `wsl [-d <distribution>] -u root --exec systemctl start docker` |
| macOS | `docker version --format {{.Server.Version}}` | `colima start` |

**One command, no second.** Each system has one way its engine is started, and no fallback is tried. A start command that exits non-zero refuses with ADR-323 §8's line naming it. No command of Vespera's names Docker Desktop, `cmd`, `open` or `Docker Desktop.exe`.

**Limits.** The engine check is killed after 30 s, as ADR-323 §5 has it. A start command is killed after `vespera.sidecars.wait-at-most`: `colima start` returns only once its machine answers, and `systemctl start` once `dockerd` says it is ready, so each may take most of what the wait would. The wait for the engine then follows, as before.

**A program that cannot be started.** Where it is the engine check's, `docker`, or `wsl` on Windows, the start is refused as Docker not installed (§6). A start command's program that cannot be started, `systemctl` without systemd or `colima` not installed, counts as exit 127 with its reason, ADR-323 §5's rule, and, being the one command, refuses with the line naming it.

### 3. Stopping: the sidecars, then the engine

**The shut-down** runs, in this order:

1. **The sidecars**: `docker compose -p vespera stop`, inside the distribution on Windows (`wsl [-d <distribution>] -u root --exec docker compose -p vespera stop`). It names the project alone, which reaches the containers from any folder (ADR-323's G, and H).
2. **The engine**, only where step 1 exited 0:

| System | Stop |
|---|---|
| Linux | `systemctl --no-ask-password stop docker.socket docker.service` |
| Windows | `wsl [-d <distribution>] -u root --exec systemctl stop docker.socket docker.service` |
| macOS | `colima stop` |

**The stop of the sidecars alone**, which closing the window runs (§5), is step 1, and only where a sidecar answers on this machine: where both base URLs name this machine and either sidecar answers ADR-323 §3's question, the command is run; where a base URL names another machine, or neither answers, no command is run. Asking the sidecars over HTTP touches no Docker socket, so closing the window never starts an engine stopped behind its socket on Linux, nor boots a WSL distribution.

**`stop`, never anything harder.** `stop` keeps the containers and their volume, so the next start's `up --no-recreate` starts the same containers as they were (H) and ADR-323 §4's guarantee holds. `down`, `rm`, `kill`, `restart`, `--force-recreate` and `pull` stay never-run, by the stops as by the start.

**Containers first.** A container stopped by `stop` stays stopped when the engine starts again; one the engine stopped as it ended comes back with it (H). Stopping the engine alone would leave Vespera's sidecars to come back at the next start of the engine, the control panel's own among them.

**`docker.socket` with the service.** Docker Engine's packages install a socket unit that starts `docker.service` again at the next connection to the socket, so stopping the service alone lasts until the next `docker` command. Both are stopped in one `systemctl` call; `systemctl start docker` starts both again.

**No engine check before the shut-down.** On Linux the check would itself start an engine stopped behind its socket. Where the engine is not running, step 1 says so by its own exit, and nothing is stopped.

**Any running engine.** The shut-down stops the engine whoever started it, and with it every container on it, Vespera's or not. The control panel's warning says so (§5).

**Both hold `sidecars.lock`** (ADR-323 §6), waited for as a start waits for it, so a stop and a start never run their commands together.

**Limits.** Each command is killed after `vespera.sidecars.wait-at-most`: `stop` waits for each container's stop and `systemctl stop` for the engine's, and a command killed counts as exit -1, as ADR-323 §5's do.

**When a command is refused.** Each is one line (§6), and nothing else fails:

- **Step 1 exits non-zero, or its program cannot be started** (exit 127): the engine is not stopped, so a refusal of step 1 leaves both as they were.
- **Step 2 exits non-zero**: the sidecars stay stopped, and the line names the command and its last line, `systemctl` refused by polkit on Linux, `wsl` or `colima` failing. The next start brings them up again, with the engine where it is not running.

### 4. The control panel calls the engine's code in its own process

**`DockerEngine`**, a public final class of `pipeline`, is the one class of the jar the control panel names. It names no type of Spring, of JavaFX or of the control panel, and its public constructors and methods take and give only primitives, types of `java.base` and itself, so the control panel names no other type of the jar through it:

```java
public static DockerEngine of(String wslDistribution, Duration waitAtMost, Path lockFolder);
public Optional<String> startTheEngine(URI doclingBaseUrl, URI ollamaBaseUrl);   // the refusal's line; empty where it answers or nothing was to be done
public Optional<String> stopTheSidecars(URI doclingBaseUrl, URI ollamaBaseUrl);  // the refusal's line; empty where stopped or nothing was to be done
public String shutDown();                                                        // §6's line, SHUT_DOWN_DONE where both stopped
public static final String SHUT_DOWN_DONE;                                        // §6's done line
```

Whether a shut-down stopped both is read by its line equalling `SHUT_DOWN_DONE`: a result type of its own would be a second type the control panel names.

- **`startTheEngine`**: ADR-323 §3's step 2, a base URL on another machine doing nothing, then, holding `sidecars.lock`, §2's engine check, its start and ADR-323 §5's wait. No sidecar is asked and no `up` is run.
- **`stopTheSidecars`**: §3's stop of the sidecars alone.
- **`shutDown`**: §3's shut-down.

It is built over `SidecarStarter`, with `HostSystem.current()`, the runner of real processes on `java.io.tmpdir` and the health that asks over HTTP, as `SidecarStarter.forTheApplication` builds them; its lines are ADR-323 §8's and §6's. Its log lines go where the control panel's process sends them, its console.

**Where the control panel takes its settings.** Each from what the window already reads (ADR-344 §3): the WSL distribution from the window's environment, `.env` and the jar's own `application.yaml`, in that order; `vespera.sidecars.wait-at-most` from Command options' preset, which starts from the same sources; the lock folder from the corpus-root lock folder the window's state is given (ADR-279); and the two addresses from the Docling and Ollama launch values as the window holds them at the call, a blank one being the address its checks ask (ADR-302 §5, as ADR-330 amends it).

**Why in process.** ADR-285 §3 kept the control panel from naming the jar because it was a second jar, with Spring on its class path; since ADR-342 the control panel runs inside the jar, and one class with a surface of `java.base` types keeps the boundary narrow and checked. Running the commands itself would be ADR-323's rejected second starter, its table, lock and lines copied; a child of the jar would add a command to the jar for the control panel alone.

**The control panel's seam.** `DockerControl`, an interface of the control panel's, with `start(values)`, `shutDown()` and `stopTheSidecars(values)`, each answering a `Said`: whether it went through, and its line. `DockerControl.inProcess(settings)`, over `DockerEngine`, is the window's, given to `ControlPanelState`'s constructor of five arguments by `ControlPanel.open`; `DockerControl.NONE`, which runs nothing and answers done with no line, is the one the constructor of four gives.

### 5. The window

**As it opens.** Once the window is shown, `start` is called once, on a thread of its own: the window does not wait for it, and Run stays enabled. An invocation started meanwhile meets `sidecars.lock` and waits for it (ADR-323 §6). Where it did not go through, its line is shown in the line the window says what happened in (`outcome`); where it did, nothing is shown.

**The button.** *Shut down Docker*, id `shut-down-docker`, stands in the action row after Stop. It is enabled whether or not a child runs, and disabled only while a shut-down it started runs. Stop is unchanged.

Pressed, it asks first, with *Shut down Docker* to go on and *Keep Docker running* not to:

> Shutting down Docker stops Vespera's sidecars and then the Docker engine, even if Vespera did not start it, and with the engine every other container running on it.

and, while a child runs, the same sentence followed by:

> The invocation running now is stopped first. Stopping loses the work done since the running step's last commit, and the next vespera run continues from there.

the second sentence being Stop's own (ADR-278 §6). Kept running, nothing is run. Gone on with: the child, where one runs, is stopped as Stop stops it and waited for; then `shutDown` runs; then its line is shown in `outcome`, and the button is enabled again. Nothing else of the window changes, whatever the line.

**Why the child is stopped first, and the button not disabled.** The operator wants it usable while a child runs. A child left running would lose its sidecars partway, and stage 2 would wait ADR-175's bound and fail; and a child still starting would bring them up again with its own `up` right after the shut-down. Stopped first, it loses what Stop loses, and the warning says so.

**Closing the window.** The confirmation of closing is unchanged: where a child runs, Stop's sentence, *Stop and close* or *Keep the window*. Once the close goes on, the window stays shown while, on a thread of its own, the child, where one runs, is stopped and waited for, and then `stopTheSidecars` runs for the launch values held; then the window closes. The engine is not stopped. A line it gives is written to the control panel's standard error, the window being gone. An end of the control panel's process that does not close the window, `Ctrl+C` or a closed terminal running ADR-307's hook, stops no sidecar: the hook keeps to its 2 seconds.

### 6. The lines

Each is one line; `<command>`, `<code>` and `<last line>` are as in ADR-323 §8, and `<program>` is `docker`, or `wsl` on Windows.

- **Docker not installed** (`DockerIsNotInstalledException`), replacing ADR-323 §8's:
  > Vespera cannot start Docker: no &lt;program&gt; command was found. Install Docker Engine -- in a WSL 2 distribution on Windows, with Colima on macOS, as the docker service on Linux -- and run the same command again.
- **The shut-down done**:
  > the sidecars and the Docker engine are stopped; Vespera starts them again when it next needs them.
- **Its step 1 refused**:
  > Docker was not shut down: &lt;command&gt; exited &lt;code&gt;: &lt;last line&gt;. The Docker engine was not stopped.
- **Its step 2 refused**:
  > the sidecars are stopped, but the Docker engine is not: &lt;command&gt; exited &lt;code&gt;: &lt;last line&gt;. Stop Docker yourself, or leave it running.
- **Its lock, or a command's output file, unusable**, `<reason>` being what ADR-323 §8's and ADR-326's lines say of each:
  > Docker was not shut down: &lt;reason&gt;.
- **The stop of the sidecars alone refused**:
  > the sidecars were not stopped: &lt;command&gt; exited &lt;code&gt;: &lt;last line&gt;.

ADR-323 §8's other lines are unchanged, and `startTheEngine` refuses with them.

### 7. No opt-out, and how a test keeps Docker out

**`vespera.sidecars.start` is removed**, with `VESPERA_SIDECARS_START`, its line in `application.yaml`, `SidecarStartSettings.start`, the starter's first step and outcome, and Command options' preset (ADR-344 §4). `vespera run`, `vespera label --auto` and the control panel's window always start the engine, and the run and the label the sidecars, where they are not answering on this machine.

What it was for is covered without it: sidecars that answer, however they were started, are left alone (ADR-323 §3's step 3); sidecars on another machine start nothing (step 2). What goes is keeping a stopped sidecar stopped across the next `vespera run`.

**A test keeps Docker out by naming both sidecars on a host under `.invalid`**: `http://sidecars.invalid:5001` for `vespera.docling.base-url` and `http://sidecars.invalid:11434` for `spring.ai.ollama.base-url`. `.invalid` is reserved never to resolve (RFC 6761), and ADR-323 §3's step 2 reads a host as written and never looks it up, so the starter takes them for another machine's and runs no command; a converter so named is never reached, which is what a test of a run with no converter needs. No shipped configuration names such a host, and no setting of its own is added.

- **The integration tests that launch the jar**, `CliExitIT`'s two launches, pass both as `-D` options where they passed `-Dvespera.sidecars.start=false`. `PackagedJarIT` reads the jar and launches none.
- **`TestVesperaApplication`** sets both as JVM system properties before its context, where it set `vespera.sidecars.start`. The starter reads them before Testcontainers has published its own (ADR-323 §1), so it starts nothing; the converter's address Testcontainers registers, and Ollama's connection detail, come before them once published. Not held by a test: building that entry point needs Docker.
- **A test that opened the window from the jar** would name them in the environment, `VESPERA_DOCLING_BASE_URL` and `SPRING_AI_OLLAMA_BASE_URL`, from which the window's fields start (ADR-344 §3). None does: `CliExitIT`'s start with no argument fails the toolkit before a window is shown.

### 8. No run id moves

`HostSystem`, `SidecarStarter`, `SidecarStartSettings` and `DockerEngine` are in `pipeline`, which no stage's version names (ADR-222), and the rest is the control panel's. None decides a verdict, a cache key, a cluster or text of the deliverable. No stage moves, and no pass is owed.

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
- **A `vespera docker` command the control panel starts as a child.** The operator chose the code called in process: a command of the jar for the control panel alone.
- **The control panel runs the commands itself.** §4: a second copy of `HostSystem`'s table, the lock and the lines.
- **Closing runs `stop` whatever the sidecars answer.** On Linux it would start an engine stopped behind its socket, and on Windows boot the distribution, for nothing to stop.
- **Stop the engine as the window closes.** The operator decided the sidecars alone.
- **A test-only setting, or a `docker` of the test's own on `PATH`.** The first is an opt-out by another name, which a shipped file could set; the second would have to answer every command the starter may run. The `.invalid` host uses a rule the starter already has.

## Consequences

- **No command of Vespera names Docker Desktop.** `HostSystem` holds §2's and §3's commands, and ADR-362 §2 has no exception left.
- **On Windows, Vespera needs a WSL 2 distribution with Docker Engine, systemd and `instanceIdleTimeout=-1`**, the one-time setup of §1; a GPU there also needs NVIDIA's container toolkit in the distribution, as on Linux. **On macOS, Colima and the `docker` command-line client**, and the first `colima start`, which downloads Colima's machine, is the operator's.
- **Opening the control panel starts the engine** where it is not running and the sidecars are looked for on this machine; **closing it stops the sidecars** where one answers; a shut-down lasts until the next start: the next `vespera run` or `vespera label --auto` starts the engine and the sidecars again, and the next opening of the window the engine.
- **Nothing keeps Vespera from starting Docker on this machine** but naming the sidecars on another.
- **The README's "Running it"** says the control panel starts Docker as it opens, stops the sidecars as it closes, and that *Shut down Docker* stops the sidecars and then the engine, every container on it with it; drops Docker Desktop, *"Nothing in Vespera stops them"* and `VESPERA_SIDECARS_START`; and keeps the one-time setup on Windows of §1.
- **`CONTEXT.md`'s Control panel entry** says it starts the Docker engine as its window opens, stops the sidecars as it closes, and stops the sidecars and the engine on its button.
- **`docs/architecture.md`'s technology table** says the sidecars are stopped by the control panel's closing and its *Shut down Docker*, citing this record.
- **`application.yaml` gains `vespera.sidecars.wsl-distribution`**, empty, and loses `vespera.sidecars.start`, each change with its comment citing this record.
- **`DockerIsNotInstalledException` takes the program** the line names.

## What this does not decide

- **A check of the WSL setup**: whether Vespera should read `.wslconfig` or `/etc/wsl.conf` and warn where §1's setup is missing.
- **Rootless Docker, Podman, remote Docker contexts and a Colima profile other than the default**, as ADR-323 §5 leaves the first three, now with no opt-out.
- **The bound measured on WSL and Colima.** 180 seconds is ADR-323's, reasoned from restarts on Linux; neither cold start was measured.
- **Shutting down the WSL distribution, or Colima's machine beyond `colima stop`.** The shut-down stops the engine; the distribution stays up under §1's setting.

## Tests

| Method | What it holds |
|---|---|
| `pipeline.EachSystemsDockerCommandsTest.eachSystemStartsAndStopsItsEngineWithOneCommandAndNoneIsDockerDesktops` | §2, §3: each system's engine check, start, compose stop and engine stop, whole; no command of any system names `desktop`, `Docker Desktop.exe`, `cmd`, `open` or `down`; `os.name` read as ADR-323 §5 reads it |
| `pipeline.EachSystemsDockerCommandsTest.onWindowsEveryDockerCommandRunsInsideTheNamedWslDistribution` | §1: `-d <distribution>` where one is named and none where it is blank; `up` given its folder by `--cd` and its files by their names; on Linux and macOS `up` names the files' paths |
| `pipeline.SidecarStartTest.theEngineIsStartedByItsSystemsOneCommandThenUpRecreatesNothing` | §2, ADR-323 §4: the engine down on each system, the commands are exactly the check, the system's one start, the checks of the wait and `up`, the start given the bound |
| `pipeline.SidecarStartTest.theControlPanelsStartStartsTheEngineAloneAndAsksNoSidecar` | §4's `startTheEngine`: the engine down, the check, the start and the wait's checks, no `up` and no sidecar asked; nothing run where a sidecar is on another machine |
| `pipeline.DockerShutDownTest.theSidecarsAreStoppedThenTheEngineAndNothingElseIsRun` | §3, §6: on each system, exactly the compose stop then the engine stop, each limited by the bound, and the done line |
| `pipeline.DockerShutDownTest.aStopThatFailsLeavesTheEngineAsItWas` | §3, §6: the compose stop exiting 1, or its program not found, runs no engine stop, and step 1's line |
| `pipeline.DockerShutDownTest.anEngineStopRefusedLeavesTheSidecarsStoppedAndSaysSo` | §3, §6: `systemctl` refused, and `colima` not found as exit 127, and step 2's line naming the command and its last line |
| `pipeline.DockerShutDownTest.closingStopsTheSidecarsAloneWhereTheyAnswer` | §3, §6: the sidecars answering, the compose stop alone; neither answering, no command; a stop that fails, its line |
| `ui.TheControlPanelLinksNoClassOfTheJarTest.theDockerEnginesClassIsNarrowAndNamesNoSpring` | §4: `DockerEngine` is in the jar, its public surface is `java.base` and itself, and it names no Spring, JavaFX or control-panel type |
| `ui.TheShutDownDockerButtonTest.theEngineIsStartedOnceAsTheWindowOpens` | §5: one start for the launch values held, no shut-down, and a refusal's line shown in `outcome` |
| `ui.TheShutDownDockerButtonTest.theButtonWarnsFirstAndRunsNothingWhenKept` | §5: the button stands after Stop; the warning's words and answers, whole; kept running, no shut-down |
| `ui.TheShutDownDockerButtonTest.withAChildRunningItIsStoppedFirstThenTheShutDownsLineIsShown` | §5: the warning with Stop's sentence; the child ended before the shut-down ran; the refusal's line in `outcome`, the button and Run enabled again |
| `ui.TheShutDownDockerButtonTest.closingTheWindowStopsTheSidecarsAndNotTheEngine` | §5: closing asks Stop's question, stops the child, then stops the sidecars once for the launch values held, shuts no engine down, and closes |

Amended:

- **`pipeline.SidecarStartTest.theOnlyCommandsAreTheEngineCheckItsStartAndAnUpThatRecreatesNothing`** becomes `theEngineIsStartedByItsSystemsOneCommandThenUpRecreatesNothing`, above.
- **`ui.TheControlPanelLinksNoClassOfTheJarTest.theControlPanelNamesOnlyTypesOfItsOwnPackageTheJdksBaseJavaFxAndJackson`** admits `DockerEngine` and no other class of the jar.
- **`pipeline.NoCommandOpensAnythingTest.noClassOfTheJarNamesAWayToOpenAFile`** exempts no class: `HostSystem` loads none of `rundll32`, `xdg-open`, `cmd` and `open`.
- **`ScriptedDocker`** reads a command ending in the engine check as one, inside `wsl` or not, takes `wsl` for a program that is not there where Docker is not installed, and records each command's limit.
- **`SidecarStartSettings` is built with no start flag** by every test that builds one.
- **`CliExitIT` and `TestVesperaApplication`** name the sidecars on `sidecars.invalid` (§7).

What no unit test shows is what `wsl`, Colima and systemd do with these commands; probe H is the evidence for Docker Engine and Compose on Linux.

## What the implementation owes

Beside turning the tests above green: `HostSystem`'s five commands taking the distribution, and `engineStartCommands` gone; `SidecarStartSettings` without `start` and with `wslDistribution`, blank where its four-argument constructor builds it; `SidecarStarter.ensureTheEngineRuns`, its outcomes `ENGINE_ANSWERING` and `ENGINE_STARTED`, `NOT_ASKED_TO_START` gone, `SidecarStarter.stopTheSidecars`, and `SidecarStarter.shutDown`, giving a `ShutDown` of whether both stopped and the line; `DockerEngine`; `DockerIsNotInstalledException`'s line; `DockerControl` and the five-argument `ControlPanelState`, and `ControlPanel.open` building its state with `DockerControl.inProcess`; the button, the start as the window opens and the stop as it closes; `ChildCommand.OPTIONS` and Command options without `vespera.sidecars.start`; `application.yaml`'s two changes; and the README, `CONTEXT.md` and `docs/architecture.md` lines of Consequences.
