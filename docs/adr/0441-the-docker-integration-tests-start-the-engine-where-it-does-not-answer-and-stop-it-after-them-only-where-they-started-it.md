# ADR-441 — The Docker integration tests start the engine where it does not answer, and stop it after them only where they started it

- **Date**: 2026-11-21
- **Status**: accepted on 2026-11-21.
- **Decided**: by the operator: that the Docker engine is started for the integration tests that need it where it is not running already, and stopped after them (§1, §4); that the engine is the tests' exactly where it did not answer when first asked and answers afterwards, however it came to answer (§2); and that a Vespera invocation started while the integration tests run, on an engine they started, is stopped with it at their end, accepted and not guarded (§5). Under the operator's delegation, and stated here: where the settings and the lock folder are read from (§3), what happens to the test containers before the stop (§4), and where Testcontainers looks for the engine (§6).
- **Amends**: [ADR-323](0323-vespera-starts-the-docker-engine-and-the-sidecars-when-they-are-not-answering-recreates-and-stops-none-and-stage-2-waits-for-a-converter-still-starting.md) §1's *"no test context starts the Docker engine or a container"*: a context built by Spring's test support still has no starter, but a JUnit extension of the test tree, run before the context of each of the four integration tests that need Docker, starts the engine where it does not answer (§1). It carries a note at its head.
- **Amends**: [ADR-390](0390-vespera-uses-docker-engine-alone-the-control-panel-starts-it-as-it-opens-and-a-button-stops-the-sidecars-then-the-engine.md) §7, *how a test keeps Docker out*: the four integration tests that need Docker bring the engine in through that extension, and shut it down after the last of them where they started it (§1, §4); a test that needs no Docker still keeps it out by naming both containers on a host under `.invalid`. It carries a note at its head.
- **Keeps**: ADR-390 §1's rejection of `DOCKER_HOST`, which is for Vespera's own commands (§6); ADR-390 §2 and §3, the engine's one start command and the shut-down, run as they are; [ADR-430](0430-the-control-panel-checks-docker-the-containers-and-the-models-and-starts-nothing-until-start-docker-is-pressed.md) §3, the question that starts nothing; [ADR-433](0433-docling-and-ollama-are-containers-where-vespera-runs-them-and-never-sidecars-and-the-earlier-names-are-still-honoured.md) §5 and §6, the settings' earlier names and the earlier lock; [ADR-164](0164-every-sidecar-restarts-unless-the-operator-stopped-it.md)'s `restart: unless-stopped`; [ADR-395](0395-one-vespera-runs-on-a-machine-at-a-time-and-the-control-panels-own-child-is-let-through.md), whose machine lock the integration tests do not take, being no Vespera.

## Context

Four integration tests need a Docker daemon: `VesperaApplicationIT`, `DoclingClientIT`, `OllamaClientIT` and `ClusterSynthesisIT`, each importing `TestcontainersConfiguration`, whose containers Testcontainers starts on that daemon. They run under failsafe, never under surefire. Where the engine is not running they fail at the first container, and on a machine where Vespera's shut-down has stopped it, Windows with Docker Engine inside WSL among them, that is the usual state.

Vespera already starts and stops the engine: `ContainerStarter` asks whether it answers starting nothing (ADR-430 §3), starts it with its system's one command and waits for it under `containers.lock` (ADR-323 §5, §6, ADR-390 §2), and shuts down the containers and then the engine (ADR-390 §3). ADR-323 §1 kept every test context from starting anything, and ADR-390 §7 says how a test keeps Docker out; neither says what a test that needs Docker does where the engine is down.

Spring's test support caches a context, and the test containers it owns, for the length of the JVM, and closes them only as the JVM ends: after JUnit's root context, whose store closes its values once every test class has run.

## Decision

### 1. An extension of the test tree sees to the engine once for every integration test that needs it

`DockerEngineForIntegrationTests`, a JUnit `BeforeAllCallback` in `src/test/java`, is declared with `@ExtendWith` by every test class that imports `TestcontainersConfiguration`, on a line before its `@SpringBootTest`, so that it runs before the context and its containers are built. It keeps what it found in the root context's store, once for the whole JVM, so the engine is asked and started at most once however many test classes declare it, and stopped, where §4 says, as that store closes after the last of them.

It builds a `ContainerStarter` as the jar does, with real processes, real HTTP questions and `HostSystem.current()`, with both containers at the jar's own `localhost` addresses whatever the configuration names, the test containers being this machine's, and no compose file: it runs the engine's start alone and never `up`.

Where the engine does not answer and its start is refused, the refusal's line is kept, and every test class that declares the extension fails at once with *"The integration tests need the Docker engine, and it does not answer: "* and that line, rather than waiting for the engine again.

### 2. What counts as a start of the tests'

The extension asks whether the engine answers, starting nothing (ADR-430 §3). Where it answers, nothing is started and nothing will be stopped: an engine that answered when first asked is never the tests'. Where it does not, `ensureTheEngineRuns` is called, and the engine is the tests' exactly where it answers afterwards:

- **`ensureTheEngineRuns` returns**, whatever its outcome. `ENGINE_STARTED`, the start command run under the lock, and `ENGINE_ANSWERING`, the engine answering when asked again under the lock with no start command run, are alike: it did not answer before the tests and answers now. So an engine the question under the lock itself brings up is the tests' too: on Linux where `docker.socket` listens while `docker.service` is inactive, the question starts the service through the socket, and on Windows where the distribution was not running, the question boots it, and an engine enabled at its boot can answer at once;
- **or a start is refused and the engine answers when asked again**, starting nothing: a refusal after the start, Compose or buildx not installed, leaves an engine that answers, and it is stopped as §4 says while the refusal's line still fails every test class that needs it.

An engine another process brought up between the first question and the lock, a Vespera invocation's start among them, answers afterwards too, and is stopped with the tests' as §5 accepts.

### 3. The settings and the lock folder are read as the jar reads them

From a `StandardEnvironment`, the JVM's system properties and the process's environment:

- **the WSL distribution** as `EarlierSettingNames.read` reads it, `vespera.containers.wsl-distribution` and, where it is not set, its earlier name (ADR-433 §5), blank being WSL's default;
- **the wait** as `ContainerStartSettings.waitAtMostIn` reads it, under either name, else the jar's own 180 s, parsed as the jar parses it;
- **the lock folder** as `CorpusRootLock.folderNamedBy` reads `vespera.corpus-root-lock-folder`, else the jar's own in the user's home.

So `containers.lock`, and the earlier lock ADR-433 §6 keeps beside it, are taken in the folder a Vespera invocation on the same machine takes them in. The lock keeps a start or a stop of the tests' from running its commands at the same moment as a jar's; it does not keep the engine up under an invocation that is running (§5). The `application.yaml` beside a jar is not read: the integration tests run from the source tree, beside no jar.

### 4. After the last test: the test containers removed, then the shut-down

Where the tests started the engine (§2), closing the store:

1. **removes this JVM's test containers**, through Testcontainers' own client: every container, running or stopped, carrying the label Testcontainers marks this JVM's containers with, each removed with force and with its volumes. A removal that fails is logged at WARN and does not keep the stop from running. Without it, the cached contexts, closing only as the JVM ends and after the engine is stopped, would leave their containers exited on that engine;
2. **runs the shut-down** (ADR-390 §3): `docker compose -p vespera stop`, then the engine's stop, each line logged, at INFO where both stopped and at WARN where either did not.

Where the tests did not start the engine, nothing is removed and nothing is stopped.

### 5. An invocation on an engine the tests started is stopped with it

A Vespera invocation started while the integration tests run, on an engine the tests started, loses its containers and its engine when the tests shut it down after their last class: accepted, not guarded. So does one that started the engine between the tests' first question and their lock (§2). Guarding it, by skipping the stop where Vespera's own containers answer, would leave the engine up whenever containers with a restart policy came back on their own, which ADR-164's `restart: unless-stopped` makes every container of Vespera's. An invocation that started the engine before the tests asked is not touched: they find it answering and stop nothing.

### 6. Where Testcontainers looks for the engine stays the machine's own setting

The extension starts the engine Vespera starts; Testcontainers finds it through its own configuration, `~/.testcontainers.properties` or `DOCKER_HOST`, which the extension neither reads nor sets. ADR-390 §1's rejection of `DOCKER_HOST` is for Vespera's own commands, which run inside the distribution on Windows; Testcontainers on Windows reaches an engine inside WSL only by its TCP address or a named pipe, which is that machine's setting.

## Alternatives rejected

- **Leave the engine to the operator.** The four integration tests then fail on every machine where a shut-down stopped it.
- **Count only `ENGINE_STARTED` as the tests' start.** Where the question under the lock itself brings the engine up, a WSL distribution booted or `docker.socket` starting the service, the engine was not running before the tests and would be left running after them, against the operator's request; and the race it would guard, an invocation starting the engine between the two questions, §5 accepts.
- **Stop the engine where it answered when first asked.** The operator asked for an engine left as it was found.
- **Skip the stop where Vespera's containers answer.** §5: with `restart: unless-stopped`, they answer whenever the engine is up.
- **Let the cached contexts stop their own containers.** They close after the root store, on an engine already stopped.
- **A copy of the starter's commands in the test tree.** It would be ADR-390 §4's rejected second copy of `HostSystem`'s table, the lock and the lines.
- **Read the settings from system properties alone.** An operator who names the distribution in the environment, as `VESPERA_CONTAINERS_WSL_DISTRIBUTION`, or under an earlier name, would have the tests start the engine in another distribution than the jar does.

## Consequences

- **The unit tests are unchanged**: none of the four runs under surefire, and the extension's unit test runs with no Docker.
- **Where the engine is down, the integration tests start it and leave it down after them**, the containers Vespera runs stopped too, and nothing of the test containers left on it.
- **An engine the question under the lock brings up is stopped after the tests** (§2): on Windows the distribution stays up and its engine is stopped; on Linux `docker.socket` is stopped with the service, as ADR-390 §3 has it.
- **A Vespera invocation on an engine the tests started stops with it**, one that started the engine between the tests' two questions included (§5).
- **`AGENTS.md`'s line on the integration tests** names the extension.
- **No run id moves**: nothing under `src/main/java` changes.

## What this does not decide

- **How Testcontainers is pointed at a WSL engine on Windows** (§6).

## Tests

| Test | Holds |
|---|---|
| `pipeline.TheIntegrationTestsStopOnlyTheEngineTheyStartedTest.anEngineThatAnswersIsLeftAsItIs` | §2, §4: the engine answering when first asked, the two questions alone, no command after the last test and no removal |
| `…anEngineThatIsDownIsStartedAndThenStopped` | §2, §4: the start, the wait and the checks for Compose and buildx; after the last test the removal once, before the compose stop and the engine's stop, and nothing else |
| `…anEngineBroughtUpByTheQuestionUnderTheLockIsStopped` | §2, §4: the first question finding the service inactive, the engine answering under the lock with no start command run, then after the last test the removal once, the compose stop and the engine's stop |
| `…anEngineThatCannotBeStartedFailsTheTestsAndStopsNothing` | §1, §2: the start refused, every test class failed with the line naming the command, its exit and its last line; nothing stopped and nothing removed |
| `…anEngineStartedBeforeARefusalIsStillStopped` | §2, §4: Compose missing after a start that worked, the engine asked again, the refusal's line kept, then the removal and the shut-down |
| `…aRemovalThatFailsStillLetsTheEngineStop` | §4: a removal that throws, closing throwing nothing, the compose stop and the engine's stop run all the same |
| `…theSettingsAndTheLockFolderAreReadAsTheJarReadsThem` | §1, §3: the distribution under its earlier name, the wait under its own, the jar's own of each where none is set, the `localhost` addresses whatever is configured, and the lock folder named or the jar's own |
| `…everyTestClassOfTheContainersSeesToTheEngineFirst` | §1: every class of `src/test/java` with a line opening on `@Import` of `TestcontainersConfiguration`, alone or in an array, has a line opening on `@ExtendWith` of the extension before the line opening on `@SpringBootTest` |

What no unit test shows: that Testcontainers' client removes this JVM's containers and that the engine they ran on is the one Vespera starts, which only the four integration tests, run with the engine down, show; and §5, which is accepted and has nothing to hold.

## What the implementation owes

Nothing under `src/main/java`. In the test tree: `DockerEngineForIntegrationTests`, with `settingsIn` and `lockFolderIn` read from an `Environment`, `Engine.seenToBy` taking the removal as a `Runnable`, and the real removal through `DockerClientFactory.instance().client()` filtered on `DockerClientFactory.TESTCONTAINERS_SESSION_ID_LABEL` equal to `DockerClientFactory.SESSION_ID`; the four integration tests declaring it before `@SpringBootTest`; `AGENTS.md`'s line; and the notes at the heads of ADR-323 and ADR-390.
