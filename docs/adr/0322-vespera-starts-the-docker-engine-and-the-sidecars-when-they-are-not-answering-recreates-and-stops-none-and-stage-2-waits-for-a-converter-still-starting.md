# ADR-322 — Vespera starts the Docker engine and the sidecars when they are not answering, recreates and stops none, and stage 2 waits for a converter still starting

- **Date**: 2026-10-23
- **Status**: accepted on 2026-10-23.
- **Decided**: by the operator, who decided that Vespera starts the Docker engine where it is not running, Docker Desktop on Windows and macOS and the service on Linux, then brings the sidecars up with the README's `up` line, `compose.gpu.yaml` included where the configuration asks for the GPU, and never stops them; and that stage 2's start check waits for a converter that is still starting. How the GPU is known, which commands start them, the per-system commands, the bounds, the refusals, the opt-out and the locks are decided under delegation.
- **Amends**: [ADR-179](0179-no-entry-point-starts-the-sidecars-the-docling-sidecar-reports-the-image-it-runs-and-ollamas-models-live-in-a-volume.md). Its first clause, *"No entry point starts the sidecars"*, and §1's *"The README's `up` line is the one way the sidecars are started, on every machine and from every entry point"* stop being true: `vespera run` and `vespera label --auto` start them when they are not answering (§1). What §1 decided stands: neither Docker Compose artifact is in `pom.xml`, Spring Boot's compose lifecycle is on no classpath, and nothing Spring runs starts, reconciles or stops a container. §2 to §5 stand. §3's image check is what catches a container this record finds and leaves as it is (§6).
- **Amends**: [ADR-158](0158-the-operator-starts-the-sidecars-from-compose-yaml-and-the-packaged-jar-starts-none.md). *"The jar does not start or stop them"* holds of stopping alone: the jar starts them (§1) and still stops none (§4). The jar still nests neither compose artifact and needs nothing from the checkout, because it carries the compose files it starts them from (§4). Its rejection of shipping compose support, *"It puts Docker in front of every command"*, is met rather than overturned: two commands ask, and only when a sidecar does not answer (§1, §3). Every compose command still names `-p vespera`.
- **Amends**: [ADR-071](0071-doclings-invocation-contract-is-one-sync-call-a-local-timeout-and-two-consecutive-streak-breakers.md). Its health check *"once, immediately before stage 2's step begins processing its first occurrence"* is still asked once; a converter that gives no answer to it is then waited for within a bound, and only a converter that still gives none, or one that answers with an error, fails the step (§7).
- **Amends**: [ADR-175](0175-a-file-that-fails-is-marked-and-skipped-and-only-a-sidecar-that-stays-gone-stops-stage-2.md) §2 and its *What this does not decide*. The 180 s bound becomes the application property `vespera.sidecars.wait-at-most`, 180 seconds unless set, and it bounds every wait for a sidecar or the Docker engine: §2's wait after a dropped connection, stage 2's start check (§7), the wait for the engine (§5) and the wait after `up` (§3). The 2 s between checks and the 5 s each check waits stay code defaults. §3's message is unchanged.
- **Amends**: [ADR-164](0164-every-sidecar-restarts-unless-the-operator-stopped-it.md)'s *"The operator still starts the sidecars from `compose.yaml` and stops them when the curation is done"*: Vespera starts them too, and a container the operator stopped is started again by the next `vespera run` (§3). `restart: unless-stopped` stands on every service.
- **Amends**: [ADR-170](0170-docling-runs-on-the-gpu-under-compose-gpu-yaml-with-an-image-tag-of-its-own.md) §3 and [ADR-165](0165-ollama-is-given-an-nvidia-gpu-by-an-override-file-and-compose-yaml-alone-asks-for-none.md) §1. `VESPERA_DOCLING_IMAGE` naming the GPU tag is also what makes Vespera's `up` name `compose.gpu.yaml` (§2). `compose.yaml` on its own still asks for no device.
- **Keeps**: [ADR-147](0147-the-docling-sidecar-is-a-derived-image-with-libreoffice-writer-and-impress-and-the-image-joins-the-extractor-identity.md): the Docling image is built from `docker/docling-serve`, never pulled, and Vespera's `up` keeps `--build` for that reason (§4).
- **Keeps**: [ADR-177](0177-one-invocation-per-working-directory-and-a-locked-database-file-is-named.md) and [ADR-279](0279-one-vespera-run-per-corpus-root-by-a-lock-on-a-file-named-for-its-real-path-outside-every-working-directory.md): both locks are taken before anything here runs, and the lock this record adds lies in ADR-279's folder (§6).
- **Keeps**: [ADR-302](0302-the-control-panel-is-a-launch-page-and-an-invocation-page-of-server-written-forms-each-report-re-served-read-only-in-a-sandbox-and-the-next-invocations-values-on-pages-of-their-own.md) §5: the control panel starts, stops and pulls nothing itself; the jar it starts as its child does what §1 says.
- **Keeps**: [ADR-141](0141-the-cli-exits-with-the-commands-exit-code-and-the-scheduler-no-feature-uses-is-removed-at-its-source.md): a refusal of §8 exits 1, as a corpus root in use does.
- **Restores**: the start half of [ADR-011](0011-managed-containers-the-tool-owns-its-sidecars.md)'s *"The app starts/stops its own dependencies"*, for `vespera run` and `vespera label --auto`. The stop half stays gone.
- **Settles**: [#652](https://github.com/algernon28/vespera/issues/652).
- **Settles**: [#651](https://github.com/algernon28/vespera/issues/651).
- **Rests on**: probes A to G below, run on Linux against Docker Engine 29.9.1 and Docker Compose v5.6.0, in a throwaway Compose project of two services shaped as `compose.yaml`'s: one built from a `Containerfile` under an `image:` name of its own with `VESPERA_IMAGE` as a build argument and `init: true`, one pulled, both `restart: unless-stopped`, one with a named volume, and an override file that changes the built service's image and build argument and both services' environment, as `compose.gpu.yaml` changes `docling-serve`'s.

## Context

### What holds before this record

The operator starts the sidecars with the README's `up` line, and no Vespera process starts or stops one (ADR-179 §1). The last time something else ran `up`, it was Spring Boot's compose support, from an IDE run, from `compose.yaml` alone, and four things followed (ADR-179, Context):

| What happened | What rules it out here |
|---|---|
| The GPU docling-serve was replaced by the CPU one, and Ollama replaced too | No `up` runs where both sidecars answer (§3), and the `up` that does run carries `--no-recreate`, which starts a stopped container as it was and recreates none (§4, probe C) |
| 1,398 conversions were cached under the GPU identity although the CPU image made them | ADR-179 §3 stops every step that would record under an image the sidecar does not run. A container Vespera creates runs the image `vespera.docling.image` names, because that value alone chooses the files (§2) |
| Ollama's models went with its container | ADR-179 §5's volume, and no container is replaced (§4) |
| Every container was stopped when the run ended | Nothing here stops, removes or restarts a container or the engine, and nothing is done at exit (§4) |

### What happened at stage 2's start

Two `vespera run` invocations, 30 s apart, failed at stage 2's health check because docling-serve was still starting ([#651](https://github.com/algernon28/vespera/issues/651)). The first got `Connection refused`, the container not listening yet; the second `HTTP/1.1 header parser received no bytes`, the port open and the server not up. docling-serve logged `Application startup complete` 5 s after the second. Census and stage 1 completed both times and stage 2 read nothing, so nothing was lost, but each needed the operator to notice and run the command again. ADR-175 §2 already waits out the same failure when it comes in the middle of the step. Once Vespera starts the sidecars itself, every first start meets this window.

### Measured

**A. A built image that is missing, without `--build`, is asked of a registry first.** With the built service's image absent and no `pull_policy`, `up -d` printed `Image p320probe/conv-cpu:r1 Pulling`, then `pull access denied for p320probe/conv-cpu, repository does not exist or may require 'docker login'`, then `Building`. With `up -d --build` it printed `Building` and `Built`, and no pull. `compose.yaml`'s Docling image is `vespera/docling-serve-cpu-libreoffice:…`, so an `up` without `--build` on a machine that lacks it asks Docker Hub's `vespera` namespace for it first. That namespace held no repository when read (`hub.docker.com/v2/namespaces/vespera/repositories`: `count` 0). It is not this project's.

**B. `--build` over running containers whose build is cached leaves them alone.** `up -d --build` a second time: `Building`, `Built`, then `Running` for both, and the same two container ids before and after.

**C. `--no-recreate` keeps the container another set of files made.** Both services brought up with both files, so the built one ran the override's image:

| Then | Containers | Image the built one runs |
|---|---|---|
| `up -d --build --no-recreate` with the base file alone, both running | `Running`, the same ids | the override's |
| both stopped, then the same `up` | `Starting`, `Started`, the same ids | the override's, with the override's environment |

**D. Without `--no-recreate`, the same `up` replaces them.** Both stopped, then `up -d --build` with the base file alone: `Recreate` and `Recreated` for both, new ids, and the built one on the base file's image. This is ADR-179's first consequence again.

**E. Two `up`s at once on a project that does not exist yet collide.** Two `up -d --build --no-recreate` started together, three times: each time one exited 0 and the other exited 1 with `Conflict. The container name "/p320-conv-1" is already in use by container …`. The containers ended up running each time.

**F. The engine.** With the daemon stopped, `docker version --format '{{.Server.Version}}'` exited 1 and `docker compose … up` exited 1 with `failed to connect to the docker API at unix:///var/run/docker.sock`. `systemctl start docker` exited 1 on the probe machine, which has no systemd (`System has not been booted with systemd as init system`), so the daemon was started directly. It answered `docker version` 1.5 s later, and the project's two containers, running when it stopped, were running under the same ids by then, with no `up`.

**G. The files' folder is not part of what a container is compared with.** The same compose files and build context, copied to another folder, then `up -d --build` there with the same project name and without `--no-recreate`: `Running` for both, the same ids. `stop` and `down` given the project name alone, from a folder holding no compose file, stopped and removed them.

**Not measured:** Docker Desktop on Windows or macOS: how long it takes to answer from cold, and `docker desktop start`, which Docker documents with `--detach` (return without waiting) and `--timeout`; the message `systemctl` gives a user polkit refuses; how long Ollama takes to answer after its container starts.

## Decision

### 1. `vespera run` and `vespera label --auto` start the sidecars, from every entry point

**Two commands ask for the sidecars, because only they use one.** `vespera run` converts at stage 2 and asks Ollama at stage 5 and 6b. `vespera label --auto` asks Ollama's model for the answers (ADR-197). `vespera label` with a file, `vespera publish`, `--help` and `--version` use no sidecar and start nothing, so ADR-158's objection to Docker in front of every command does not arise.

- **`vespera run`** asks after the corpus-root lock is taken (ADR-279) and before the job starts. A run refused for its root, its working directory, its deliverable root or a missing root starts nothing.
- **`vespera label --auto`** asks once its arguments are checked (`--auto` takes no file) and before the labeller is looked up.

**Every entry point that goes through `VesperaApplication.main` does it**: the jar, an IDE run of `VesperaApplication` (the `Local SpringApp` and `Local Vespera Label` configurations), the Maven `headless` profile, which runs the jar, and the control panel, the Maven `gui` profile's included, whose child is the jar (ADR-278) and which passes `VESPERA_DOCLING_IMAGE` to it (ADR-292). The control panel itself starts nothing: its launch page goes on reading the sidecars and reports one not answering as it does today, and Run stays enabled.

**The starter is registered by `VesperaApplication.main` alone**, as the corpus-root lock is (ADR-279 §7), and the two commands reach it as an optional collaborator. A context built by Spring's test support has none, so no test context starts the Docker engine or a container. A test that means to exercise the starter builds it with a process runner of its own (Tests). The integration tests that launch the jar pass `-Dvespera.sidecars.start=false` (§9).

### 2. The GPU is chosen by `vespera.docling.image`, the value the identity already uses

`compose.yaml` builds one Docling image and `compose.gpu.yaml` another, each named in its `docling-serve.image` (ADR-170). The starter reads both names from the files it carries (§4) and compares `vespera.docling.image` with them, character for character:

- **The image `compose.yaml` builds**: `up` names `compose.yaml` alone.
- **The image `compose.gpu.yaml` builds**: `up` names `compose.yaml` and then `compose.gpu.yaml`.
- **Anything else**: no file builds it, so nothing is started, and the command is refused with §8's line before Docker is asked anything.

`vespera.docling.image` is `application.yaml`'s CPU default unless `VESPERA_DOCLING_IMAGE` or any other property source sets it. The IDE's run configurations read `.env` into the environment, so a `.env` line reaches the starter the way it reaches the identity; the jar reads no `.env` (ADR-170 §3), and neither does the starter.

**Why one value, and not a key of its own.** The identity takes the image from `vespera.docling.image` (ADR-147, ADR-179 §3). A second setting, `vespera.sidecars.gpu` say, could say GPU while the image said CPU, which is ADR-179's poisoned cache by another road, stopped only by §3's check once the wrong containers were up. With one value, a container Vespera creates runs the image the identity names, by construction. Ollama follows the same choice, because `compose.gpu.yaml` gives both services the GPU.

### 3. What the starter does, in order

1. **`vespera.sidecars.start` is false**: nothing more. No sidecar is asked and no command is run (§9).
2. **Either base URL names another machine**: nothing more. `vespera.docling.base-url` and `spring.ai.ollama.base-url` are each read for its host; a host that is not a loopback name or address (`localhost`, `127.0.0.0/8`, `::1`) is a sidecar Docker on this machine cannot make answer. One INFO line says so, and a sidecar that does not answer fails where it fails today.
3. **Both sidecars answer**: nothing more. docling-serve is asked `GET /health`, Ollama `GET /api/version`, each waiting at most 5 s for its answer, the bound `DoclingClient.HEALTH_CHECK_TIMEOUT` sets. Any 2xx is an answer. No command is run, so a machine whose sidecars are up never meets Docker here, whoever started them and however.
4. **No compose file builds `vespera.docling.image`**: refused (§2, §8).
5. **The sidecar-start lock is taken** (§6), and both sidecars are asked again. Both answer: nothing more. That is another invocation having started them while this one waited.
6. **The engine is made to answer** (§5).
7. **`up` is run** (§4), to its end, every line it prints passed to the log at INFO. It exits 0, or the command is refused with its last line (§8).
8. **Both sidecars are waited for**: each asked every 2 s, as in step 3, until both have answered or `vespera.sidecars.wait-at-most` has passed since `up` exited. One INFO line says the wait begins and one that it ended, with the seconds; nothing is logged per check. A sidecar still not answering at the bound refuses the command, naming it (§8).
9. **The lock is released.**

A refusal is printed as one line on standard error, and the command exits 1, before the job starts or the labeller is reached. Nothing has been walked or recorded.

### 4. The `up` line, its files, and what is never run

**The line is the README's, with `--no-recreate`:**

```
docker compose -p vespera -f <folder>/compose.yaml up -d --build --no-recreate
docker compose -p vespera -f <folder>/compose.yaml -f <folder>/compose.gpu.yaml up -d --build --no-recreate
```

- **`--no-recreate`**: a container that exists, running or stopped, is started as it is, whichever files made it, and only a missing one is created (C). Without it, a stopped container made from both files is replaced by one made from `compose.yaml` alone (D). A container this leaves on another image than the one configured is ADR-179 §3's to catch, and it stops the step and names both images before anything is converted. Vespera never decides which of the two was the mistake, because only the operator knows (ADR-179 §3).
- **`--build`, kept**: without it, a missing Docling image is first asked of Docker Hub under a namespace that is not this project's (A), and a stranger's image there would become the converter that reads the operator's documents. With it, the image is built, and a cached build leaves running containers alone (B). It costs a pass over the build cache each time Vespera brings the sidecars up, which is only when one is not answering. The first start on a machine builds the image, which takes minutes, and longer for the CUDA base. `up` has no bound of Vespera's for that reason: it ends by itself, and its output is in the log as it goes.
- **Never run**: `--force-recreate`, `down`, `stop`, `rm`, `kill`, `restart`, `pull`, or any command that changes a container other than `up` as above. No shutdown hook is registered, and nothing runs at exit. An engine Vespera started is left running too.

**The files are the jar's own.** The build copies `compose.yaml`, `compose.gpu.yaml` and everything under `docker/` into the jar's resources, under `sidecars/`, byte for byte. The starter writes them out to a new temporary folder when it reaches step 7, names that folder's files in `-f`, and deletes the folder once `up` has exited. Files read where they already lie, as a test reads the repository's, are never deleted: only a folder the starter wrote out is. The build context, `docker/docling-serve`, is relative to `compose.yaml`, so it resolves inside the folder. The jar needs nothing from the checkout, as ADR-158 has it, and an IDE run reads the same resources from `target/classes`. The folder is not part of what Compose compares a container with, and `-p vespera` names the same project from any folder (G), so containers the operator started from the checkout and containers Vespera started from a temporary folder are one set, and `docker compose -p vespera stop`, `exec` and `down` reach them from anywhere.

**Why not the checkout's files.** They are not there when the jar runs elsewhere, and a jar built before the checkout moved on would start images its own `vespera.docling.image` does not name.

### 5. The engine, per system

**Is it running.** `docker version --format {{.Server.Version}}`, which exits 0 when the engine answers and non-zero when it does not (F). A `docker` that cannot be started at all, the program missing from `PATH`, refuses the command with §8's line naming Docker.

**Starting it.** The commands below, tried in order until one exits 0:

| System | First | Then |
|---|---|---|
| Windows | `docker desktop start --detach` | `cmd /c start "" "<ProgramFiles>\Docker\Docker\Docker Desktop.exe"`, `<ProgramFiles>` being the environment variable `ProgramFiles`, `C:\Program Files` where it is unset |
| macOS | `docker desktop start --detach` | `open -a Docker` |
| Linux | `systemctl --no-ask-password start docker` | `docker desktop start --detach` |

`--detach` because the wait is Vespera's own, under its own bound. `--no-ask-password` because nothing here prompts (ADR-047): where polkit would ask, the command exits non-zero and the next is tried. Nothing is run under `sudo`. When every command exits non-zero, the command is refused naming each one, its exit code and the last line it printed (§8).

**Waiting for it.** `docker version` again every 2 s, until it exits 0 or `vespera.sidecars.wait-at-most` has passed since the start command returned; one INFO line when the wait begins, one when it ends. At the bound, refused (§8). Every command of this section is killed if it has not exited after 30 s, and counts as exited non-zero, so the wait ends no later than the bound and one such command after it. After the engine answers, containers that were running when it stopped come back by themselves under `restart: unless-stopped` (F, ADR-164), and `up --no-recreate` leaves them so.

**What is not covered.** Rootless Docker under `systemctl --user`, Podman, and a Docker context pointing at another machine. For the last, `docker version` answers and `up` runs there, but the loopback URLs of step 2 reach nothing, so step 8 refuses at the bound. An operator on any of them starts the sidecars and sets `vespera.sidecars.start` to false.

### 6. Locks, and the image check

**ADR-177's working-directory lock and ADR-279's corpus-root lock are held before the starter runs**, so no two invocations on one working directory or one root reach it together.

**Two invocations on different working directories can, and two `up`s at once collide** (E). So steps 5 to 9 hold a lock of their own: an operating-system lock on `sidecars.lock` in the folder ADR-279's locks lie in (`vespera.corpus-root-lock-folder`, `~/.vespera-root-locks` unless set), taken as ADR-177 takes its own, on one byte past the end of the file. Unlike those two, it is waited for rather than refused, because the other invocation is doing what this one would: when it is held, one INFO line says another Vespera invocation is starting the sidecars, and the lock is waited for. The re-check of step 5 then finds them answering. It is released at step 9, and by the operating system however the process ends.

**ADR-179 §3 is unchanged and is the backstop.** Containers the starter found, running or stopped, are left on whatever image they run. Where that is not `vespera.docling.image`, stage 2 stops before its run is minted and names both images, as it does today.

### 7. Stage 2's start check waits for a converter that gives no answer yet

`ExtractionHealthCheckListener.beforeStep` asks `/health` once through the conversion client, as ADR-071 has it, and reads what comes back:

- **It answers 2xx**: the step goes on, as today.
- **It answers with an HTTP error status**: the step fails at once with that failure, today's message and today's closing line. A converter that answers is up, and waiting for it to change its mind is not this record's.
- **It gives no answer**, the transport failing with no status (refused, reset, closed with no status line, or silent past the timeout): one WARN line, then `SidecarRecovery` waits for it, asking `/health` on its own short clock every 2 s until it answers or `vespera.sidecars.wait-at-most` has passed:
  - **It answers within the bound**: one INFO line with the seconds, and the step goes on.
  - **The bound passes**: the listener asks `/health` once more through the conversion client, and whatever that throws fails the step, so its message and the closing line are today's. Should it answer, the step goes on.

The lines, in the stage's own voice (ADR-093):

```
Stage 2 (extraction) docling-serve is not answering its health check yet (<what the transport reported>); waiting up to <bound> seconds for it to start
Stage 2 (extraction) docling-serve answers its health check after <n> seconds
```

`<what the transport reported>` is the failure as the ERROR line of today's failed check already writes it, its `toString()`. The first line is WARN and the second INFO. Nothing is logged per check, as ADR-192 asks of every loop.

**One bound, in one class.** `SidecarRecovery` already holds ADR-175's wait, and the start check reuses it rather than a second copy: the same 2 s between checks, the same 5 s each check waits, the same bound. The bound becomes `vespera.sidecars.wait-at-most` because a test that launches the jar against a converter that never answers cannot otherwise end in less than three minutes. It stays an application property and is no profile value: it says how long a machine takes to start a container, not what the operator has judged about a corpus (ADR-175 §2). 180 seconds covers the measured restart of 6 to 9 seconds (ADR-175), the measured first answer of 6.4 seconds (ADR-164, G), and the 30 seconds and more of #651 with room for a GPU image loading its models.

**It covers docling-serve, and Ollama only through §3.** Stage 2's start is where a converter still starting has been met, and seed extraction already waits on its first refused call through ADR-175 §6. Ollama is waited for at step 8 whenever Vespera started the sidecars. An Ollama the operator started a moment before a command that reaches it first, a resumed run whose stage 2 is finished or `vespera label --auto`, is not waited for: its first question fails as it does today. That is left open (*What this does not decide*).

**The message is today's on purpose.** #651 asks that an error answer, or no answer once the bound runs out, fail with today's message, and the closing line ADR-071, #311 and ADR-177 built for this failure stays the one the operator reads. `DoclingDidNotComeBackException` is not thrown here: its words are about a dropped connection.

### 8. The lines a refusal prints

Each is one line on standard error, exit code 1. `<command>` is a command line as run, its parts joined by single spaces; `<last line>` is the last line of its output that is not blank, or `no output` where there is none; `<n>` is `vespera.sidecars.wait-at-most` in whole seconds.

- **No `docker` command** (`DockerIsNotInstalledException`):
  > the sidecars are not answering and Vespera cannot start them: no docker command was found. Install Docker -- Docker Desktop on Windows and macOS, Docker Engine on Linux -- and run the same command again, or start the sidecars yourself and set VESPERA_SIDECARS_START=false.
- **No start command worked** (`DockerEngineDidNotStartException`), one `<command> exited <code>: <last line>` per command tried, joined by `; `:
  > the Docker engine is not running and Vespera could not start it: <command> exited <code>: <last line>; <command> exited <code>: <last line>. Start Docker yourself and run the same command again.
- **The engine did not answer in time** (`DockerEngineDidNotStartException`):
  > the Docker engine was started but did not answer within <n> seconds. Wait until Docker has finished starting and run the same command again.
- **No file builds the configured image** (`NoComposeFileBuildsTheImageException`):
  > the sidecars are not answering and Vespera cannot start them for vespera.docling.image <configured>: compose.yaml builds <cpu image> and compose.gpu.yaml builds <gpu image>. Set VESPERA_DOCLING_IMAGE to one of the two, or unset it for the first, and run the same command again.
- **`up` exited non-zero** (`SidecarsDidNotStartException`):
  > docker compose could not start the sidecars: <command> exited <code>: <last line>. Fix what that names and run the same command again.
- **A sidecar did not answer after `up`** (`SidecarsDidNotAnswerException`), `<which>` being `docling-serve`, `Ollama`, or `docling-serve and Ollama`:
  > the sidecars were started but <which> did not answer within <n> seconds; docker compose -p vespera logs says why. Run the same command again once they answer.

The five exception types extend `SidecarStartRefusedException`, the one type the two commands catch. The lines carry no record id: the operator reading them has none of these records.

### 9. The opt-out

`vespera.sidecars.start`, true unless set, `VESPERA_SIDECARS_START=false` in the environment. False, the starter does nothing at all (§3, step 1), and the sidecars are the operator's as before this record. It is for a machine whose sidecars are started some other way (rootless Docker, Podman, another machine), for an operator who wants a stopped sidecar to stay stopped, and for the integration tests that launch the jar. It is an application property and no profile value, for §7's reason.

ADR-179 removed Spring's compose support rather than switching it off, because *"a property can be turned back on"*. That reasoning was about turning a harmful start back on. This property turns off a start that recreates nothing, so the cost of setting it wrongly is ADR-179's behaviour, not its incident.

### 10. No run id moves

Every class this record adds or changes is in `pipeline`: the starter and its collaborators, `ExtractionHealthCheckListener` and `SidecarRecovery`. No stage's version names `pipeline` (ADR-222), and none of them decides a verdict, a cache key, a cluster or text of the deliverable. `DoclingClient` and the rest of `extraction` are unchanged. No stage moves, and no pass is owed.

## Alternatives rejected

- **Spring Boot's compose support back, pointed at both files with `start-only`.** ADR-179 §1's reasons stand: a second copy of the start command, and the support reconciles running containers to the files it is given, which is D.
- **Always run `up`, without asking the sidecars first.** It puts Docker in front of every `run` on a machine whose sidecars are up, and on one whose sidecars are on another machine or started without Docker.
- **`up` without `--no-recreate`.** D: a stopped container from another set of files is replaced.
- **`up` without `--build`.** A: the Docling image is asked of a registry namespace that is not this project's before it is built.
- **`docker compose start` for stopped containers and `up` only for missing ones.** Two commands where `--no-recreate` is one, and a question of Docker's state the starter would have to read and parse.
- **A key of its own for the GPU, or reading `.env`.** §2: a second source that can disagree with the identity's.
- **The control panel starts them.** It would be a second starter in a second jar that links no class of the first (ADR-285), and its child starts them anyway.
- **Start Docker before picocli reads the command line, in an environment listener.** Every command would pay for it, `--help` included.
- **Wait only at stage 2's start, and start nothing.** That is #651 alone. The operator decided Vespera starts them.
- **A wait of its own at stage 2's start, with its own bound.** Two bounds for one question, which would drift apart.
- **Wait out an error answer too.** A converter answering 503 at start is up; ADR-071's check and #651 both fail it at once, and `ExtractionWithTheSidecarDownTest` holds that.
- **`sudo systemctl start docker`.** It prompts, and the pipeline never blocks (ADR-047).
- **Pull Ollama's models after `up`.** Not asked for: the operator's scope is the engine and the sidecars. Ollama serves what it has been given, and ADR-202's refusal names a model it does not serve.

## Consequences

- **A fresh `vespera run` on a machine with Docker and nothing running starts Docker, builds the Docling image if it is missing, and brings both sidecars up**, then goes on. The first build takes minutes, in the log as it goes.
- **A stopped sidecar comes back.** `docker compose -p vespera stop` at the end of a curation lasts until the next `vespera run` or `vespera label --auto`. An operator who wants them to stay stopped sets `VESPERA_SIDECARS_START=false`.
- **Nothing stops at exit.** The sidecars and the engine are left running, as the README tells the operator to leave them across the five invocations.
- **A machine with a GPU still needs `VESPERA_DOCLING_IMAGE` set to the GPU tag**, now for two reasons: the identity (ADR-170 §3) and the files Vespera's `up` names (§2). Left unset, Vespera brings up the CPU sidecars where none were running, which is a correct run on the CPU and not the one the operator meant; where the GPU sidecars were already running, they are left alone and ADR-179 §3 stops stage 2 naming both images.
- **The README's "Running it" is rewritten**: Vespera starts Docker and the sidecars when they are not answering, from every way of running it; the `up` lines stay for an operator who starts them by hand; `VESPERA_SIDECARS_START=false` keeps them the operator's; on Linux the service must be startable without a password, or started by hand; nothing stops them. Its sentence *"Nothing in Vespera starts them or stops them"* goes. `docs/check-claims.mjs` keeps requiring `-p vespera` on every compose line in the section.
- **`compose.yaml`'s header comment changes** where it says *"No entry point reads a running container back or starts one"*: Vespera's `run` and `label --auto` start them, with `--no-recreate`, and nothing reads a port back. Nothing else in either compose file changes.
- **`docs/architecture.md`'s technology table** says the sidecars are started by `vespera run` and `vespera label --auto` when they are not answering and stopped by nothing, citing this record.
- **`application.yaml` gains `vespera.sidecars.start: true` and `vespera.sidecars.wait-at-most: 180s`**, each with a comment citing this record.
- **The build copies the compose files and `docker/` into the jar** under `sidecars/`. `PackagedJarIT` keeps holding that no compose artifact is nested.
- **The integration tests that launch the jar pass `-Dvespera.sidecars.start=false`**, and `TheClosingLineIsTheLastLineIT` also `-Dvespera.sidecars.wait-at-most=1s`, since its run against a converter that is not listening now waits the bound before its closing line.
- **`ExtractionWithTheSidecarDownTest`'s unreachable converter is waited for**, on a short clock, before the step fails with today's line; its unhealthy converter still fails at once.
- **`SidecarsAreStartedByTheOperatorTest` becomes `SpringsComposeSupportIsOnNoClasspathTest`**, its claims unchanged: ADR-179 §1 is what it holds, and its old name is what this record makes false.

## What this does not decide

- **Waiting for an Ollama the operator started a moment ago**, where a command reaches Ollama before anything waited for it (§7).
- **Rootless Docker, Podman, and remote Docker contexts** (§5).
- **Pulling Ollama's models.**
- **Whether a Docling container found on another image than the one configured should be replaced.** ADR-179 §3 stops the step and the operator chooses.
- **The bound measured on Docker Desktop.** 180 seconds is reasoned from the restarts measured and not from a cold start of Docker Desktop on Windows or macOS, which was not measured.

## Tests

New, under `src/test`, all in `io.algernon.vespera.pipeline` and needing no Docker:

- **`SidecarStartTest`** (unit). The starter is built with a scripted `ProcessRunner` that records every command and answers each from a script, a scripted `SidecarHealth`, a `HostSystem`, `ComposeFiles` read from the repository's own files, and a short bound:
  - both sidecars answering: no command is run;
  - `vespera.sidecars.start` false: no sidecar is asked and no command is run;
  - a base URL on another machine: no command is run;
  - the CPU image configured, the engine running: the commands are exactly the engine check and §4's `up` naming `compose.yaml` alone;
  - the GPU image configured: the `up` names both files, `compose.yaml` first;
  - an image no file builds: §8's line, and no command is run;
  - the sidecars answering when asked again after the lock: no command is run, and they were asked twice;
  - the engine not running on Windows, macOS and Linux: the first start command of §5's table for each, then the engine is asked until it answers, then `up`;
  - the first start command exiting non-zero: the second is tried, on Windows the one ending in `Docker Desktop.exe`, on macOS `open -a Docker`, on Linux `docker desktop start --detach`;
  - every start command exiting non-zero: §8's line naming each;
  - the engine never answering: §8's line with the bound;
  - no `docker` command: §8's line;
  - `up` exiting non-zero: §8's line with its last line;
  - a sidecar not answering after `up`: §8's line naming it;
  - over every scenario, no command but the engine check, the start commands and `up`, and every `up` carries `--build` and `--no-recreate`;
  - after a start, `sidecars.lock` lies in the lock folder.
- **`ComposeFilesTest`** (unit): the compose files written out from the classpath are the repository's `compose.yaml`, `compose.gpu.yaml` and every file under `docker/`, byte for byte and no other; and each file's Docling image is read as its `docling-serve.image` says.
- **`SidecarStartSettingsTest`** (unit): `vespera.sidecars.start` is true unless set and false when set so; the bound is 180 s unless `vespera.sidecars.wait-at-most` says otherwise; the image is `vespera.docling.image`; Ollama's URL is `spring.ai.ollama.base-url`, `http://localhost:11434` unless set.
- **`StageTwoWaitsForAConverterStillStartingTest`** (unit, the listener with a scripted client and a `SidecarRecovery` on a short clock): a converter that gives no answer and then answers is waited for, with §7's two lines; one that answers with an error fails at once with that failure and is not waited for; one that never answers is asked once more at the bound and that failure is what the step fails with; one that answers at once is not waited for.
- **`SidecarStartInvocationTest`** (`@CascadeSliceTest`, the starter built with a scripted runner in which no `docker` command exists and sidecars that do not answer): `vespera run` exits 1, prints §8's line on standard error, and records no walk; `vespera label --auto` exits 1 with the same line; `vespera label` with a file never asks the sidecars.

Amended:

- **`ExtractionWithTheSidecarDownTest`**: a `SidecarRecovery` on a short clock, so its unreachable converter is waited for and then fails with the line it has today.
- **`PipelineHoldsOnlyTheRulesOnRecordTest`**: the starter's classes are on record as holding no rule. The two production collaborators the implementation adds, the process runner that starts real processes and the health that asks over HTTP, join the list in the change that adds them.
- **`ClosingLineOverALockedDatabaseFileTest`**: builds the listener with its new second argument, the wait, which its claims never reach.
- **The integration tests that launch the jar**: `-Dvespera.sidecars.start=false`, and `TheClosingLineIsTheLastLineIT` `-Dvespera.sidecars.wait-at-most=1s`.
- **`SidecarsAreStartedByTheOperatorTest`** is renamed `SpringsComposeSupportIsOnNoClasspathTest`.

What no unit test shows is what Docker and Docker Desktop do with these commands. Probes A to G are the evidence for Docker Engine and Compose; Docker Desktop's start is not measured.
