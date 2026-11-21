package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

/**
 * What Vespera runs to bring its two containers up when they are not answering, and what it refuses with when it cannot
 * (ADR-323 §2 to §6 and §8, ADR-326), each system starting Docker Engine with its one command (ADR-390 §1, §2); and
 * the start of the engine alone (ADR-390 §4), where at least one container is on this machine (ADR-430 §10).
 *
 * <p>No Docker is needed: the starter is given a {@link ScriptedDocker}, which records every command and answers each
 * from a script, and a {@link ScriptedContainerHealth}. The compose files are the repository's own, read where the tests
 * run, so the images a configuration is compared with are the ones the files build. What Docker itself does with the
 * commands is ADR-323's probes, not something a unit test can show.
 *
 * <p>Each expected command is written out in full, so a command the starter should not run, a recreate, a stop or a
 * removal among them, fails the claim that lists what it ran.
 */
@Epic("Pipeline")
@Feature("The containers")
@Issue("652")
@Link(name = "ADR-323", url = Adr.VESPERA_STARTS_DOCKER_AND_THE_SIDECARS_AND_STAGE_2_WAITS_FOR_A_STARTING_CONVERTER, type = "adr")
@Link(name = "ADR-390", url = Adr.VESPERA_USES_DOCKER_ENGINE_ALONE_AND_A_BUTTON_SHUTS_IT_DOWN, type = "adr")
class ContainerStartTest {

    /** The repository root, which is where Maven runs the tests from and where the compose files lie. */
    private static final Path REPOSITORY = Path.of("").toAbsolutePath();

    private static final Path COMPOSE_FILE = REPOSITORY.resolve("compose.yaml");

    private static final Path GPU_FILE = REPOSITORY.resolve("compose.gpu.yaml");

    /** Where docling-serve and Ollama are looked for on this machine, as the shipped configuration has them. */
    private static final URI DOCLING_HERE = URI.create("http://localhost:5001");

    private static final URI OLLAMA_HERE = URI.create("http://127.0.0.1:11434");

    /** Where they are looked for on another machine, resolved by the test's own resolver alone. */
    private static final URI DOCLING_ELSEWHERE = URI.create("http://docling.example.org:5001");

    private static final URI OLLAMA_ELSEWHERE = URI.create("http://ollama.example.org:11434");

    /** An image neither compose file builds. */
    private static final String IMAGE_NO_FILE_BUILDS = "vespera/docling-serve-somebody-elses:v0";

    /** How long these tests wait for the engine or a container to answer: one second, so a wait that runs out is quick. */
    private static final Duration WAIT_AT_MOST = Duration.ofSeconds(1);

    /** How often they ask meanwhile. */
    private static final Duration CHECK_EVERY = Duration.ofMillis(20);

    /** Engine checks the engine fails before it answers, in the tests where Vespera starts it. */
    private static final int ENGINE_CHECKS_FAILED = 2;

    private static final List<String> SYSTEMCTL_START = List.of("systemctl", "--no-ask-password", "start", "docker");

    private static final List<String> COLIMA_START = List.of("colima", "start");

    /** Run inside WSL's default distribution, as root, with no shell between (ADR-390 §1). */
    private static final List<String> IN_WSL = List.of("wsl", "-u", "root", "--exec");

    /** How long the engine check may run before it is killed (ADR-323 §5). */
    private static final Duration ENGINE_CHECK_LIMIT = Duration.ofSeconds(30);

    /** How long a test holds the container lock: longer than {@link #WAIT_AT_MOST}, so a bounded wait would have ended. */
    private static final Duration HELD_FOR = Duration.ofMillis(1500);

    /** The Docling image each compose file builds, read from the files as they stand. */
    private static String cpuImage;

    private static String gpuImage;

    @TempDir
    Path lockFolder;

    @BeforeAll
    static void readTheImagesTheFilesBuild() throws IOException {
        cpuImage = doclingImageIn(COMPOSE_FILE);
        gpuImage = doclingImageIn(GPU_FILE);
    }

    @Test
    @Story("Vespera starts the containers only where they are not answering")
    @DisplayName("When both containers answer, nothing is run: Docker is not asked anything")
    void bothAnsweringRunsNothing() {
        ScriptedDocker docker = ScriptedDocker.engineRunning();

        ContainerStarter.Outcome outcome =
                starter(settings(cpuImage), docker, ScriptedContainerHealth.bothAnswering(), HostSystem.LINUX)
                        .ensureRunning();

        claim("the starter says the containers already answer", () -> assertThat(outcome)
                .isEqualTo(ContainerStarter.Outcome.ALREADY_ANSWERING));
        claim("and it ran no command at all", () -> assertThat(docker.commands()).isEmpty());
    }

    @Test
    @Story("The configured converter image chooses the compose files")
    @DisplayName("An image neither compose file builds is refused, naming all three, before Docker is asked anything")
    void anImageNoFileBuildsIsRefusedBeforeDockerIsAsked() {
        ScriptedDocker docker = ScriptedDocker.engineRunning();

        Throwable refused = catchThrowable(() ->
                starter(settings(IMAGE_NO_FILE_BUILDS), docker, ScriptedContainerHealth.neitherAnswering(), HostSystem.LINUX)
                        .ensureRunning());

        claim("the start is refused, naming the image configured and the image each file builds",
                () -> assertThat(refused)
                        .isInstanceOf(NoComposeFileBuildsTheImageException.class)
                        .isInstanceOf(ContainerStartRefusedException.class)
                        .hasMessage("the containers are not answering and Vespera cannot start them for"
                                + " vespera.docling.image " + IMAGE_NO_FILE_BUILDS + ": compose.yaml builds " + cpuImage
                                + " and compose.gpu.yaml builds " + gpuImage + ". Set VESPERA_DOCLING_IMAGE to one of"
                                + " the two, or unset it for the first, and run the same command again."));
        claim("and no command was run", () -> assertThat(docker.commands()).isEmpty());
    }

    @Test
    @Story("Vespera never recreates, stops or removes a container")
    @DisplayName("With the engine down, each system runs exactly the engine check, its one start, the wait's checks, the checks for compose and buildx and an up that builds and recreates nothing")
    @Issue("816")
    @Link(name = "ADR-410", url = Adr.A_MISSING_DOCKER_COMPOSE_OR_BUILDX_IS_NAMED_BEFORE_UP, type = "adr")
    void theEngineIsStartedByItsSystemsOneCommandThenUpRecreatesNothing() {
        List<String> wslCheck = concat(IN_WSL, ScriptedDocker.ENGINE_CHECK);
        Map<HostSystem, List<List<String>>> expected = new java.util.LinkedHashMap<>();
        expected.put(HostSystem.LINUX, List.of(ScriptedDocker.ENGINE_CHECK, SYSTEMCTL_START,
                ScriptedDocker.ENGINE_CHECK, ScriptedDocker.ENGINE_CHECK,
                ScriptedDocker.COMPOSE_CHECK, ScriptedDocker.BUILDX_CHECK, up(COMPOSE_FILE)));
        expected.put(HostSystem.MAC, List.of(ScriptedDocker.ENGINE_CHECK, COLIMA_START,
                ScriptedDocker.ENGINE_CHECK, ScriptedDocker.ENGINE_CHECK,
                ScriptedDocker.COMPOSE_CHECK, ScriptedDocker.BUILDX_CHECK, up(COMPOSE_FILE)));
        expected.put(HostSystem.WINDOWS, List.of(wslCheck, concat(IN_WSL, List.of("systemctl", "start", "docker")),
                wslCheck, wslCheck,
                concat(IN_WSL, ScriptedDocker.COMPOSE_CHECK), concat(IN_WSL, ScriptedDocker.BUILDX_CHECK),
                concat(List.of("wsl", "-u", "root", "--cd", REPOSITORY.toString(), "--exec"),
                        List.of("docker", "compose", "-p", "vespera", "-f", "compose.yaml",
                                "up", "-d", "--build", "--no-recreate"))));

        for (Map.Entry<HostSystem, List<List<String>>> system : expected.entrySet()) {
            HostSystem host = system.getKey();
            List<List<String>> starts = List.of(SYSTEMCTL_START, COLIMA_START,
                    concat(IN_WSL, List.of("systemctl", "start", "docker")));
            ScriptedContainerHealth health = ScriptedContainerHealth.neitherAnswering();
            ScriptedDocker docker = ScriptedDocker.engineAnsweringAfter(ENGINE_CHECKS_FAILED)
                    .whenUpSucceeds(() -> health.answering(true, true));
            starts.forEach(start -> docker.answering(start, 0, ""));

            ContainerStarter.Outcome outcome = starter(settings(cpuImage), docker, health, host).ensureRunning();

            claim("on " + host + ", the containers were started", () -> assertThat(outcome)
                    .isEqualTo(ContainerStarter.Outcome.STARTED));
            claim("and the commands run were, in order and whole, the engine check, the system's one start, the "
                            + ENGINE_CHECKS_FAILED + " checks of the wait for the engine, the last answering, the check"
                            + " for compose and the check for buildx, and an up with --build and --no-recreate; none of"
                            + " Docker Desktop, and no second start",
                    () -> assertThat(docker.commands()).containsExactlyElementsOf(system.getValue()));
            claim("and the engine check and the checks for compose and buildx were given " + ENGINE_CHECK_LIMIT.toSeconds()
                            + " seconds each time and the start the " + WAIT_AT_MOST.toSeconds() + " second(s) every"
                            + " wait of these tests is bounded by",
                    () -> assertThat(docker.limits()).containsExactly(ENGINE_CHECK_LIMIT, WAIT_AT_MOST,
                            ENGINE_CHECK_LIMIT, ENGINE_CHECK_LIMIT, ENGINE_CHECK_LIMIT, ENGINE_CHECK_LIMIT));
        }
    }

    @Test
    @Story("The engine alone is started where a container is on this machine")
    @DisplayName("The start of the engine alone makes the engine answer, checks for compose and buildx, and asks no container; it starts the engine for Ollama alone on this machine, and nothing where both are on another machine")
    @Issue("816")
    @Issue("889")
    @Link(name = "ADR-410", url = Adr.A_MISSING_DOCKER_COMPOSE_OR_BUILDX_IS_NAMED_BEFORE_UP, type = "adr")
    @Link(name = "ADR-430", url = Adr.THE_CONTROL_PANEL_CHECKS_DOCKER_AND_STARTS_NOTHING_UNTIL_START_DOCKER, type = "adr")
    void theControlPanelsStartStartsTheEngineAloneAndAsksNoContainer() {
        ScriptedContainerHealth health = ScriptedContainerHealth.neitherAnswering();
        ScriptedDocker docker = ScriptedDocker.engineAnsweringAfter(ENGINE_CHECKS_FAILED)
                .answering(SYSTEMCTL_START, 0, "");

        ContainerStarter.Outcome outcome = starter(settings(cpuImage), docker, health, HostSystem.LINUX)
                .ensureTheEngineRuns();

        claim("the engine was started", () -> assertThat(outcome).isEqualTo(ContainerStarter.Outcome.ENGINE_STARTED));
        claim("and the commands were the engine check, the service's start, the " + ENGINE_CHECKS_FAILED
                        + " checks of the wait, the check for compose and the check for buildx, and no up",
                () -> assertThat(docker.commands()).containsExactly(ScriptedDocker.ENGINE_CHECK, SYSTEMCTL_START,
                        ScriptedDocker.ENGINE_CHECK, ScriptedDocker.ENGINE_CHECK,
                        ScriptedDocker.COMPOSE_CHECK, ScriptedDocker.BUILDX_CHECK));
        claim("and no container was asked anything", () -> assertThat(health.questionsAsked()).isZero());

        ScriptedDocker forOllamaAlone = ScriptedDocker.engineAnsweringAfter(ENGINE_CHECKS_FAILED)
                .answering(SYSTEMCTL_START, 0, "");
        ContainerStarter.Outcome ollamaAlone = starterOnAMachineOfItsOwn(
                new ContainerStartSettings(DOCLING_ELSEWHERE, OLLAMA_HERE, cpuImage, WAIT_AT_MOST), forOllamaAlone,
                health).ensureTheEngineRuns();
        claim("with docling-serve at " + DOCLING_ELSEWHERE + ", on another machine, and Ollama at " + OLLAMA_HERE
                        + ", on this one, the engine was started for Ollama, with the same commands (ADR-430 §10)",
                () -> assertThat(List.of(ollamaAlone, forOllamaAlone.commands())).containsExactly(
                        ContainerStarter.Outcome.ENGINE_STARTED,
                        List.of(ScriptedDocker.ENGINE_CHECK, SYSTEMCTL_START, ScriptedDocker.ENGINE_CHECK,
                                ScriptedDocker.ENGINE_CHECK, ScriptedDocker.COMPOSE_CHECK,
                                ScriptedDocker.BUILDX_CHECK)));

        ScriptedDocker elsewhere = ScriptedDocker.engineAnsweringAfter(ScriptedDocker.NEVER);
        ContainerStarter.Outcome onAnother = starterOnAMachineOfItsOwn(
                new ContainerStartSettings(DOCLING_ELSEWHERE, OLLAMA_ELSEWHERE, cpuImage, WAIT_AT_MOST), elsewhere,
                health).ensureTheEngineRuns();
        claim("with docling-serve at " + DOCLING_ELSEWHERE + " and Ollama at " + OLLAMA_ELSEWHERE + ", both on another"
                        + " machine, it says so and runs nothing",
                () -> assertThat(List.of(onAnother, elsewhere.commands()))
                        .containsExactly(ContainerStarter.Outcome.ON_ANOTHER_MACHINE, List.of()));
        claim("and no container was asked anything, here or there", () -> assertThat(health.questionsAsked()).isZero());
    }

    @Test
    @Story("Two invocations do not start the containers twice")
    @DisplayName("A start that finds the container lock held waits for it, without a bound of its own, and asks the containers again once it has it")
    void aStartWaitsForTheLockAnotherHolds() throws Exception {
        ScriptedContainerHealth health = ScriptedContainerHealth.neitherAnswering();
        ScriptedDocker docker = ScriptedDocker.engineRunning();
        ContainerStarter starter = starter(settings(cpuImage), docker, health, HostSystem.LINUX);
        java.nio.file.Files.createDirectories(lockFolder);
        java.util.concurrent.CompletableFuture<ContainerStarter.Outcome> started;
        try (java.nio.channels.FileChannel held = java.nio.channels.FileChannel.open(
                        lockFolder.resolve("containers.lock"),
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.WRITE);
                java.nio.channels.FileLock ignored = held.lock(Long.MAX_VALUE - 1, 1, false)) {
            started = java.util.concurrent.CompletableFuture.supplyAsync(starter::ensureRunning);
            Thread.sleep(HELD_FOR.toMillis());

            claim("while the lock is held, longer than the " + WAIT_AT_MOST.toSeconds() + " second(s) every other wait"
                            + " of these tests lasts, the start neither refused nor finished",
                    () -> assertThat(started).isNotDone());
            claim("and it ran no command", () -> assertThat(docker.commands()).isEmpty());
            health.answering(true, true);
        }

        claim("once the holder let go, the start asked the containers again, found them answering, and ran nothing",
                () -> assertThat(started.get(WAIT_AT_MOST.multipliedBy(5).toMillis(),
                                java.util.concurrent.TimeUnit.MILLISECONDS))
                        .isEqualTo(ContainerStarter.Outcome.ALREADY_ANSWERING));
        claim("no command was run at any point", () -> assertThat(docker.commands()).isEmpty());
    }

    @Test
    @Story("Vespera starts the containers only where they are not answering")
    @DisplayName("With no program to ask the engine, docker or wsl on Windows, a start of the containers and the control panel's start are each refused as Docker not installed, naming that program")
    @Issue("818")
    void dockerNotInstalledNamesTheEngineChecksProgramOnEachSystem() {
        Map<HostSystem, List<String>> checks = new java.util.LinkedHashMap<>();
        checks.put(HostSystem.LINUX, ScriptedDocker.ENGINE_CHECK);
        checks.put(HostSystem.MAC, ScriptedDocker.ENGINE_CHECK);
        checks.put(HostSystem.WINDOWS, concat(IN_WSL, ScriptedDocker.ENGINE_CHECK));

        for (Map.Entry<HostSystem, List<String>> system : checks.entrySet()) {
            String program = system.getValue().getFirst();
            String line = "Vespera cannot start Docker: no " + program + " command was found. Install Docker Engine --"
                    + " in a WSL 2 distribution on Windows, with Colima on macOS, as the docker service on Linux -- and"
                    + " run the same command again.";

            ScriptedDocker forTheContainers = ScriptedDocker.notInstalled();
            Throwable containers = catchThrowable(() -> starter(settings(cpuImage), forTheContainers,
                    ScriptedContainerHealth.neitherAnswering(), system.getKey()).ensureRunning());
            claim("on " + system.getKey() + ", the start of the containers is refused as Docker not installed, in the"
                            + " line naming " + program + ", after the engine check alone",
                    () -> assertThat(List.of(containers.getClass(), containers.getMessage(), forTheContainers.commands()))
                            .containsExactly(DockerIsNotInstalledException.class, line, List.of(system.getValue())));

            ScriptedDocker forTheWindow = ScriptedDocker.notInstalled();
            Throwable engine = catchThrowable(() -> starter(settings(cpuImage), forTheWindow,
                    ScriptedContainerHealth.neitherAnswering(), system.getKey()).ensureTheEngineRuns());
            claim("and so is the control panel's start of the engine, in the same line, after the same check",
                    () -> assertThat(List.of(engine.getClass(), engine.getMessage(), forTheWindow.commands()))
                            .containsExactly(DockerIsNotInstalledException.class, line, List.of(system.getValue())));
        }
    }

    private ContainerStarter starter(
            ContainerStartSettings settings, ScriptedDocker docker, ScriptedContainerHealth health, HostSystem host) {
        Supplier<ComposeFiles> theRepositorysFiles = () -> ComposeFiles.in(REPOSITORY);
        return new ContainerStarter(settings, docker, health, host, theRepositorysFiles, lockFolder, CHECK_EVERY);
    }

    /**
     * The starter on a machine whose resolver is the test's, so no name is looked up (ADR-430 §10): the two {@code
     * example.org} hosts resolve to documentation addresses of another machine, and the machine holds no address of its
     * own beyond the loopback.
     */
    private ContainerStarter starterOnAMachineOfItsOwn(
            ContainerStartSettings settings, ScriptedDocker docker, ScriptedContainerHealth health) {
        ThisMachine thisMachine = new ThisMachine(name -> switch (name) {
            case "docling.example.org" -> new InetAddress[] {InetAddress.ofLiteral("192.0.2.10")};
            case "ollama.example.org" -> new InetAddress[] {InetAddress.ofLiteral("192.0.2.11")};
            default -> throw new UnknownHostException(name);
        }, List::of);
        return new ContainerStarter(settings, docker, health, HostSystem.LINUX, () -> ComposeFiles.in(REPOSITORY),
                lockFolder, CHECK_EVERY, thisMachine);
    }

    private static ContainerStartSettings settings(String image) {
        return new ContainerStartSettings(DOCLING_HERE, OLLAMA_HERE, image, WAIT_AT_MOST);
    }

    /** ADR-323 §4's up, naming {@code files} in the order given. */
    private static List<String> up(Path... files) {
        List<String> command = new ArrayList<>(List.of("docker", "compose", "-p", "vespera"));
        for (Path file : files) {
            command.add("-f");
            command.add(file.toString());
        }
        command.addAll(List.of("up", "-d", "--build", "--no-recreate"));
        return List.copyOf(command);
    }

    private static List<String> concat(List<String> first, List<String> then) {
        List<String> whole = new ArrayList<>(first);
        whole.addAll(then);
        return List.copyOf(whole);
    }

    /** The image {@code composeFile} builds for docling-serve, as the file stands; {@code AStartAndAShutDownTellEachLineTheyLogTest} reads it too. */
    @SuppressWarnings("unchecked")
    static String doclingImageIn(Path composeFile) throws IOException {
        Map<String, Object> compose = new Yaml().load(Files.readString(composeFile));
        Map<String, Object> services = (Map<String, Object>) compose.get("services");
        return (String) ((Map<String, Object>) services.get("docling-serve")).get("image");
    }
}
