package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.net.URI;
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
 * What Vespera runs to bring its two sidecars up when they are not answering, and what it refuses with when it cannot
 * (ADR-323 §2 to §6 and §8).
 *
 * <p>No Docker is needed: the starter is given a {@link ScriptedDocker}, which records every command and answers each
 * from a script, and a {@link ScriptedSidecarHealth}. The compose files are the repository's own, read where the tests
 * run, so the images a configuration is compared with are the ones the files build. What Docker itself does with the
 * commands is ADR-323's probes, not something a unit test can show.
 *
 * <p>Each expected command is written out in full, so a command the starter should not run, a recreate, a stop or a
 * removal among them, fails the claim that lists what it ran.
 */
@Epic("Pipeline")
@Feature("The sidecars")
@Issue("652")
@Link(name = "ADR-323", url = Adr.VESPERA_STARTS_DOCKER_AND_THE_SIDECARS_AND_STAGE_2_WAITS_FOR_A_STARTING_CONVERTER, type = "adr")
class SidecarStartTest {

    /** The repository root, which is where Maven runs the tests from and where the compose files lie. */
    private static final Path REPOSITORY = Path.of("").toAbsolutePath();

    private static final Path COMPOSE_FILE = REPOSITORY.resolve("compose.yaml");

    private static final Path GPU_FILE = REPOSITORY.resolve("compose.gpu.yaml");

    /** Where docling-serve and Ollama are looked for on this machine, as the shipped configuration has them. */
    private static final URI DOCLING_HERE = URI.create("http://localhost:5001");

    private static final URI OLLAMA_HERE = URI.create("http://127.0.0.1:11434");

    /** A converter on another machine, which Docker on this one cannot make answer. */
    private static final URI DOCLING_ELSEWHERE = URI.create("http://10.20.30.40:5001");

    /** An image neither compose file builds. */
    private static final String IMAGE_NO_FILE_BUILDS = "vespera/docling-serve-somebody-elses:v0";

    /** How long these tests wait for the engine or a sidecar to answer: one second, so a wait that runs out is quick. */
    private static final Duration WAIT_AT_MOST = Duration.ofSeconds(1);

    /** How often they ask meanwhile. */
    private static final Duration CHECK_EVERY = Duration.ofMillis(20);

    /** Engine checks the engine fails before it answers, in the tests where Vespera starts it. */
    private static final int ENGINE_CHECKS_FAILED = 2;

    private static final List<String> DESKTOP_START = List.of("docker", "desktop", "start", "--detach");

    private static final List<String> SYSTEMCTL_START = List.of("systemctl", "--no-ask-password", "start", "docker");

    private static final List<String> OPEN_DOCKER_APP = List.of("open", "-a", "Docker");

    /** What Docker Desktop's command prints where the plugin is not installed, and what polkit says to a non-root user. */
    private static final String NO_DESKTOP_PLUGIN = "docker: 'desktop' is not a docker command.";

    private static final String POLKIT_REFUSAL = "Failed to start docker.service: Interactive authentication required.";

    /** What writing a file reports on a full drive. */
    private static final String NO_SPACE = "No space left on device";

    /** What starting {@code systemctl} reports on a machine that has none. */
    private static final String NO_SYSTEMCTL = "Cannot run program \"systemctl\": error=2, No such file or directory";

    /**
     * A host name many machines' hosts files map to the loopback address, and which is still not {@code localhost}
     * written out.
     */
    private static final String NAME_NO_LOOKUP_RESOLVES = "localhost.localdomain";

    /** How long a test holds the sidecar lock: longer than {@link #WAIT_AT_MOST}, so a bounded wait would have ended. */
    private static final Duration HELD_FOR = Duration.ofMillis(1500);

    /** The last line Compose prints where the GPU file asks for a device the machine does not have. */
    private static final String COMPOSE_REFUSAL =
            "Error response from daemon: could not select device driver \"nvidia\" with capabilities: [[gpu]]";

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
    @Story("Vespera starts the sidecars only where they are not answering")
    @DisplayName("When both sidecars answer, nothing is run: Docker is not asked anything")
    void bothAnsweringRunsNothing() {
        ScriptedDocker docker = ScriptedDocker.engineRunning();

        SidecarStarter.Outcome outcome =
                starter(settings(cpuImage), docker, ScriptedSidecarHealth.bothAnswering(), HostSystem.LINUX)
                        .ensureRunning();

        claim("the starter says the sidecars already answer", () -> assertThat(outcome)
                .isEqualTo(SidecarStarter.Outcome.ALREADY_ANSWERING));
        claim("and it ran no command at all", () -> assertThat(docker.commands()).isEmpty());
    }

    @Test
    @Story("The operator can leave the sidecars to themselves")
    @DisplayName("When starting the sidecars is turned off, neither sidecar is asked anything and nothing is run")
    void turnedOffAsksAndRunsNothing() {
        ScriptedDocker docker = ScriptedDocker.engineRunning();
        ScriptedSidecarHealth health = ScriptedSidecarHealth.neitherAnswering();
        SidecarStartSettings turnedOff =
                new SidecarStartSettings(false, DOCLING_HERE, OLLAMA_HERE, cpuImage, WAIT_AT_MOST);

        SidecarStarter.Outcome outcome = starter(turnedOff, docker, health, HostSystem.LINUX).ensureRunning();

        claim("the starter says it was not asked to start them", () -> assertThat(outcome)
                .isEqualTo(SidecarStarter.Outcome.NOT_ASKED_TO_START));
        claim("no sidecar was asked whether it answers", () -> assertThat(health.questionsAsked()).isZero());
        claim("and no command was run", () -> assertThat(docker.commands()).isEmpty());
    }

    @Test
    @Story("Vespera starts the sidecars only where they are not answering")
    @DisplayName("When the converter is looked for on another machine, nothing is run, because Docker here cannot make it answer")
    void aSidecarOnAnotherMachineRunsNothing() {
        ScriptedDocker docker = ScriptedDocker.engineRunning();
        SidecarStartSettings elsewhere =
                new SidecarStartSettings(true, DOCLING_ELSEWHERE, OLLAMA_HERE, cpuImage, WAIT_AT_MOST);

        SidecarStarter.Outcome outcome =
                starter(elsewhere, docker, ScriptedSidecarHealth.neitherAnswering(), HostSystem.LINUX).ensureRunning();

        claim("the starter says a sidecar is on another machine", () -> assertThat(outcome)
                .isEqualTo(SidecarStarter.Outcome.ON_ANOTHER_MACHINE));
        claim("and it ran no command", () -> assertThat(docker.commands()).isEmpty());
    }

    @Test
    @Story("The configured converter image chooses the compose files")
    @DisplayName("With the processor image configured and the engine running, the engine is checked and the sidecars brought up from compose.yaml alone, without recreating any")
    void theProcessorImageBringsUpComposeYamlAlone() {
        ScriptedSidecarHealth health = ScriptedSidecarHealth.neitherAnswering();
        ScriptedDocker docker = ScriptedDocker.engineRunning().whenUpSucceeds(() -> health.answering(true, true));

        SidecarStarter.Outcome outcome = starter(settings(cpuImage), docker, health, HostSystem.LINUX).ensureRunning();

        claim("the starter says it started them", () -> assertThat(outcome).isEqualTo(SidecarStarter.Outcome.STARTED));
        claim("it checked the engine, then ran the README's up with --no-recreate, naming compose.yaml alone, and"
                        + " nothing else",
                () -> assertThat(docker.commands()).containsExactly(ScriptedDocker.ENGINE_CHECK, up(COMPOSE_FILE)));
        claim("and the repository's compose files are still where they were: only a folder the starter wrote out is"
                        + " ever deleted",
                () -> assertThat(COMPOSE_FILE).isRegularFile());
    }

    @Test
    @Story("The configured converter image chooses the compose files")
    @DisplayName("With the graphics-card image configured, the sidecars are brought up from compose.yaml and then compose.gpu.yaml")
    void theGpuImageBringsUpBothFiles() {
        ScriptedSidecarHealth health = ScriptedSidecarHealth.neitherAnswering();
        ScriptedDocker docker = ScriptedDocker.engineRunning().whenUpSucceeds(() -> health.answering(true, true));

        starter(settings(gpuImage), docker, health, HostSystem.LINUX).ensureRunning();

        claim("the up names compose.yaml first and compose.gpu.yaml after it, as the README's line for a graphics"
                        + " card does",
                () -> assertThat(docker.commands())
                        .containsExactly(ScriptedDocker.ENGINE_CHECK, up(COMPOSE_FILE, GPU_FILE)));
    }

    @Test
    @Story("The configured converter image chooses the compose files")
    @DisplayName("An image neither compose file builds is refused, naming all three, before Docker is asked anything")
    void anImageNoFileBuildsIsRefusedBeforeDockerIsAsked() {
        ScriptedDocker docker = ScriptedDocker.engineRunning();

        Throwable refused = catchThrowable(() ->
                starter(settings(IMAGE_NO_FILE_BUILDS), docker, ScriptedSidecarHealth.neitherAnswering(), HostSystem.LINUX)
                        .ensureRunning());

        claim("the start is refused, naming the image configured and the image each file builds",
                () -> assertThat(refused)
                        .isInstanceOf(NoComposeFileBuildsTheImageException.class)
                        .isInstanceOf(SidecarStartRefusedException.class)
                        .hasMessage("the sidecars are not answering and Vespera cannot start them for"
                                + " vespera.docling.image " + IMAGE_NO_FILE_BUILDS + ": compose.yaml builds " + cpuImage
                                + " and compose.gpu.yaml builds " + gpuImage + ". Set VESPERA_DOCLING_IMAGE to one of"
                                + " the two, or unset it for the first, and run the same command again."));
        claim("and no command was run", () -> assertThat(docker.commands()).isEmpty());
    }

    @Test
    @Story("Two invocations do not start the sidecars twice")
    @DisplayName("Sidecars that answer when asked again, once the start lock is held, are not started: another invocation started them")
    void sidecarsAnsweringOnceTheLockIsHeldAreNotStarted() {
        ScriptedDocker docker = ScriptedDocker.engineRunning();

        SidecarStarter.Outcome outcome =
                starter(settings(cpuImage), docker, ScriptedSidecarHealth.doclingAnsweringAfter(1), HostSystem.LINUX)
                        .ensureRunning();

        claim("the converter, not answering the first time, answered when asked again, so the starter says they"
                        + " already answer",
                () -> assertThat(outcome).isEqualTo(SidecarStarter.Outcome.ALREADY_ANSWERING));
        claim("and it ran no command", () -> assertThat(docker.commands()).isEmpty());
    }

    @Test
    @Story("Vespera starts the Docker engine when it is not running")
    @DisplayName("On Windows, an engine that is not running is started through Docker Desktop's command, waited for, and then the sidecars are brought up")
    void onWindowsTheEngineIsStartedThroughDockerDesktop() {
        assertTheEngineIsStartedBy(HostSystem.WINDOWS, DESKTOP_START);
    }

    @Test
    @Story("Vespera starts the Docker engine when it is not running")
    @DisplayName("On macOS, an engine that is not running is started through Docker Desktop's command, waited for, and then the sidecars are brought up")
    void onMacOsTheEngineIsStartedThroughDockerDesktop() {
        assertTheEngineIsStartedBy(HostSystem.MAC, DESKTOP_START);
    }

    @Test
    @Story("Vespera starts the Docker engine when it is not running")
    @DisplayName("On Linux, an engine that is not running is started as the docker service, without asking for a password, waited for, and then the sidecars are brought up")
    void onLinuxTheEngineIsStartedAsAService() {
        assertTheEngineIsStartedBy(HostSystem.LINUX, SYSTEMCTL_START);
    }

    @Test
    @Story("Vespera starts the Docker engine when it is not running")
    @DisplayName("On Windows, where Docker Desktop's command does not start it, the Docker Desktop application is started instead")
    void onWindowsTheApplicationIsStartedWhereTheCommandIsNotThere() {
        List<List<String>> ran = commandsWhereTheFirstStartFails(HostSystem.WINDOWS, DESKTOP_START);

        claim("after Docker Desktop's command, the one other command run before the engine is checked again starts"
                        + " the Docker Desktop application through cmd's start",
                () -> assertThat(ran.get(2))
                        .startsWith("cmd", "/c", "start", "")
                        .last(STRING)
                        .endsWith("\\Docker\\Docker\\Docker Desktop.exe"));
    }

    @Test
    @Story("Vespera starts the Docker engine when it is not running")
    @DisplayName("On macOS, where Docker Desktop's command does not start it, the Docker application is opened instead")
    void onMacOsTheApplicationIsOpenedWhereTheCommandIsNotThere() {
        List<List<String>> ran = commandsWhereTheFirstStartFails(HostSystem.MAC, DESKTOP_START);

        claim("after Docker Desktop's command, the Docker application is opened, and the engine is checked again",
                () -> assertThat(ran.subList(0, 4))
                        .containsExactly(ScriptedDocker.ENGINE_CHECK, DESKTOP_START, OPEN_DOCKER_APP, ScriptedDocker.ENGINE_CHECK));
    }

    @Test
    @Story("Vespera starts the Docker engine when it is not running")
    @DisplayName("On Linux, where the docker service cannot be started without a password, Docker Desktop's command is tried instead")
    void onLinuxDockerDesktopIsTriedWhereTheServiceIsRefused() {
        List<List<String>> ran = commandsWhereTheFirstStartFails(HostSystem.LINUX, SYSTEMCTL_START);

        claim("after the service, Docker Desktop's command is run, and the engine is checked again",
                () -> assertThat(ran.subList(0, 4))
                        .containsExactly(ScriptedDocker.ENGINE_CHECK, SYSTEMCTL_START, DESKTOP_START, ScriptedDocker.ENGINE_CHECK));
    }

    @Test
    @Story("Vespera refuses, naming why, when it cannot start them")
    @DisplayName("When no command starts the engine, the start is refused, naming each command tried, its exit code and the last line it printed")
    void anEngineNothingStartsIsRefusedNamingEachCommandTried() {
        ScriptedDocker docker = ScriptedDocker.engineAnsweringAfter(ScriptedDocker.NEVER)
                .answering(SYSTEMCTL_START, 1, "\n" + POLKIT_REFUSAL + "\n\n")
                .answering(DESKTOP_START, 1, NO_DESKTOP_PLUGIN + "\n");

        Throwable refused = catchThrowable(() ->
                starter(settings(cpuImage), docker, ScriptedSidecarHealth.neitherAnswering(), HostSystem.LINUX)
                        .ensureRunning());

        claim("the start is refused with the line naming both commands, in the order tried, each with its exit code"
                        + " and the last line it printed that is not blank",
                () -> assertThat(refused)
                        .isInstanceOf(DockerEngineDidNotStartException.class)
                        .isInstanceOf(SidecarStartRefusedException.class)
                        .hasMessage("the Docker engine is not running and Vespera could not start it: "
                                + String.join(" ", SYSTEMCTL_START) + " exited 1: " + POLKIT_REFUSAL + "; "
                                + String.join(" ", DESKTOP_START) + " exited 1: " + NO_DESKTOP_PLUGIN
                                + ". Start Docker yourself and run the same command again."));
        claim("and no up was run", () -> assertThat(docker.commands()).noneMatch(SidecarStartTest::isAnUp));
    }

    @Test
    @Story("Vespera refuses, naming why, when it cannot start them")
    @DisplayName("When the engine was started but never answers, the start is refused once the bound has passed, saying how long was waited")
    void anEngineThatNeverAnswersIsRefusedAtTheBound() {
        ScriptedDocker docker = ScriptedDocker.engineAnsweringAfter(ScriptedDocker.NEVER).answering(DESKTOP_START, 0, "");

        Throwable refused = catchThrowable(() ->
                starter(settings(cpuImage), docker, ScriptedSidecarHealth.neitherAnswering(), HostSystem.WINDOWS)
                        .ensureRunning());

        claim("the start is refused, saying the engine did not answer within the " + WAIT_AT_MOST.toSeconds()
                        + " second(s) these tests wait",
                () -> assertThat(refused)
                        .isInstanceOf(DockerEngineDidNotStartException.class)
                        .hasMessage("the Docker engine was started but did not answer within " + WAIT_AT_MOST.toSeconds()
                                + " seconds. Wait until Docker has finished starting and run the same command again."));
        claim("the engine was checked more than once after it was started, before the wait gave up",
                () -> assertThat(docker.commands().stream().filter(ScriptedDocker.ENGINE_CHECK::equals).count())
                        .isGreaterThan(2));
        claim("and no up was run", () -> assertThat(docker.commands()).noneMatch(SidecarStartTest::isAnUp));
    }

    @Test
    @Story("Vespera refuses, naming why, when it cannot start them")
    @DisplayName("When there is no docker command at all, the start is refused, saying to install Docker or to start the sidecars by hand")
    void noDockerCommandIsRefused() {
        Throwable refused = catchThrowable(() -> starter(
                        settings(cpuImage), ScriptedDocker.notInstalled(), ScriptedSidecarHealth.neitherAnswering(),
                        HostSystem.WINDOWS)
                .ensureRunning());

        claim("the start is refused with the line naming Docker and the setting that leaves the sidecars to the"
                        + " operator",
                () -> assertThat(refused)
                        .isInstanceOf(DockerIsNotInstalledException.class)
                        .isInstanceOf(SidecarStartRefusedException.class)
                        .hasMessage("the sidecars are not answering and Vespera cannot start them: no docker command"
                                + " was found. Install Docker -- Docker Desktop on Windows and macOS, Docker Engine on"
                                + " Linux -- and run the same command again, or start the sidecars yourself and set"
                                + " VESPERA_SIDECARS_START=false."));
    }

    @Test
    @Story("Vespera refuses, naming why, when it cannot start them")
    @DisplayName("When up exits with an error, the start is refused, naming the command, its exit code and the last line it printed")
    void anUpThatFailsIsRefusedWithItsLastLine() {
        ScriptedDocker docker = ScriptedDocker.engineRunning()
                .upAnswering(1, " Container vespera-ollama-1 Creating\n" + COMPOSE_REFUSAL + "\n");

        Throwable refused = catchThrowable(() ->
                starter(settings(gpuImage), docker, ScriptedSidecarHealth.neitherAnswering(), HostSystem.LINUX)
                        .ensureRunning());

        claim("the start is refused with the line naming the up as run, its exit code and Compose's last line",
                () -> assertThat(refused)
                        .isInstanceOf(SidecarsDidNotStartException.class)
                        .isInstanceOf(SidecarStartRefusedException.class)
                        .hasMessage("docker compose could not start the sidecars: "
                                + String.join(" ", up(COMPOSE_FILE, GPU_FILE)) + " exited 1: " + COMPOSE_REFUSAL
                                + ". Fix what that names and run the same command again."));
    }

    @Test
    @Story("Vespera refuses, naming why, when it cannot start them")
    @DisplayName("When the converter still does not answer once up has succeeded and the bound has passed, the start is refused naming the converter alone")
    void aSidecarThatDoesNotAnswerAfterUpIsRefusedByName() {
        ScriptedSidecarHealth health = ScriptedSidecarHealth.neitherAnswering();
        ScriptedDocker docker = ScriptedDocker.engineRunning().whenUpSucceeds(() -> health.answering(false, true));

        Throwable refused = catchThrowable(() ->
                starter(settings(cpuImage), docker, health, HostSystem.LINUX).ensureRunning());

        claim("the start is refused, naming docling-serve and not Ollama, which answered, and the "
                        + WAIT_AT_MOST.toSeconds() + " second(s) these tests wait",
                () -> assertThat(refused)
                        .isInstanceOf(SidecarsDidNotAnswerException.class)
                        .isInstanceOf(SidecarStartRefusedException.class)
                        .hasMessage("the sidecars were started but docling-serve did not answer within "
                                + WAIT_AT_MOST.toSeconds() + " seconds; docker compose -p vespera logs says why. Run the"
                                + " same command again once they answer."));
    }

    @Test
    @Story("Vespera never recreates, stops or removes a sidecar")
    @DisplayName("Across every start, the only commands run are the engine check, the engine's start commands and an up that builds and recreates nothing")
    void theOnlyCommandsAreTheEngineCheckItsStartAndAnUpThatRecreatesNothing() {
        List<List<String>> ran = new ArrayList<>();
        for (HostSystem host : HostSystem.values()) {
            for (String image : List.of(cpuImage, gpuImage)) {
                ScriptedSidecarHealth health = ScriptedSidecarHealth.neitherAnswering();
                ScriptedDocker docker = ScriptedDocker.engineAnsweringAfter(ENGINE_CHECKS_FAILED)
                        .answering(DESKTOP_START, 0, "")
                        .answering(SYSTEMCTL_START, 0, "")
                        .whenUpSucceeds(() -> health.answering(true, true));
                starter(settings(image), docker, health, host).ensureRunning();
                ran.addAll(docker.commands());
            }
        }
        List<List<String>> allowed = List.of(ScriptedDocker.ENGINE_CHECK, DESKTOP_START, SYSTEMCTL_START,
                up(COMPOSE_FILE), up(COMPOSE_FILE, GPU_FILE));

        claim("every command run, on Windows, macOS and Linux and for both images, is the engine check, Docker"
                        + " Desktop's start, the service's start, or an up with --build and --no-recreate; none"
                        + " recreates, stops, removes, restarts or pulls a container",
                () -> assertThat(ran).isNotEmpty().allMatch(allowed::contains));
    }

    @Test
    @Story("Two invocations do not start the sidecars twice")
    @DisplayName("Starting the sidecars takes a lock file in the folder the corpus-root locks lie in")
    void startingTakesTheSidecarLockInTheLockFolder() {
        ScriptedSidecarHealth health = ScriptedSidecarHealth.neitherAnswering();
        ScriptedDocker docker = ScriptedDocker.engineRunning().whenUpSucceeds(() -> health.answering(true, true));

        starter(settings(cpuImage), docker, health, HostSystem.LINUX).ensureRunning();

        claim("sidecars.lock lies in the lock folder", () -> assertThat(lockFolder.resolve("sidecars.lock"))
                .isRegularFile());
    }

    @Test
    @Story("Vespera starts the sidecars only where they are not answering")
    @DisplayName("A loopback address other than localhost, 127.x.y.z or ::1 written out, is this machine, and the sidecars there are started")
    void aLoopbackAddressWrittenOutIsThisMachine() {
        for (URI here : List.of(URI.create("http://127.1.2.3:5001"), URI.create("http://[::1]:5001"))) {
            ScriptedSidecarHealth health = ScriptedSidecarHealth.neitherAnswering();
            ScriptedDocker docker = ScriptedDocker.engineRunning().whenUpSucceeds(() -> health.answering(true, true));
            SidecarStartSettings settings = new SidecarStartSettings(true, here, OLLAMA_HERE, cpuImage, WAIT_AT_MOST);

            SidecarStarter.Outcome outcome = starter(settings, docker, health, HostSystem.LINUX).ensureRunning();

            claim("the converter looked for at " + here + " is on this machine, so the sidecars were started",
                    () -> assertThat(outcome).isEqualTo(SidecarStarter.Outcome.STARTED));
        }
    }

    @Test
    @Story("Vespera starts the sidecars only where they are not answering")
    @DisplayName("A host name other than localhost is read as another machine without being looked up, even localhost.localdomain, which many hosts files map to this machine")
    void aHostNameIsAnotherMachineWithoutALookup() {
        ScriptedDocker docker = ScriptedDocker.engineRunning();
        SidecarStartSettings named = new SidecarStartSettings(
                true, URI.create("http://" + NAME_NO_LOOKUP_RESOLVES + ":5001"), OLLAMA_HERE, cpuImage, WAIT_AT_MOST);

        SidecarStarter.Outcome outcome =
                starter(named, docker, ScriptedSidecarHealth.neitherAnswering(), HostSystem.LINUX).ensureRunning();

        claim("a name that is not localhost is another machine: only a loopback name or address written out is"
                        + " this one, and nothing is resolved to decide",
                () -> assertThat(outcome).isEqualTo(SidecarStarter.Outcome.ON_ANOTHER_MACHINE));
        claim("and no command was run", () -> assertThat(docker.commands()).isEmpty());
    }

    @Test
    @Story("Vespera refuses, naming why, when it cannot start them")
    @DisplayName("A start command whose program cannot be started counts as exiting 127 with the reason it could not, and the next command is tried")
    void aStartCommandThatCannotBeLaunchedCountsAsExit127() {
        ScriptedDocker docker = ScriptedDocker.engineAnsweringAfter(ScriptedDocker.NEVER)
                .cannotLaunch(SYSTEMCTL_START, NO_SYSTEMCTL)
                .answering(DESKTOP_START, 1, NO_DESKTOP_PLUGIN);

        Throwable refused = catchThrowable(() ->
                starter(settings(cpuImage), docker, ScriptedSidecarHealth.neitherAnswering(), HostSystem.LINUX)
                        .ensureRunning());

        claim("Docker Desktop's command was still tried after the service's could not be started",
                () -> assertThat(docker.commands()).contains(DESKTOP_START));
        claim("and the refusal names the service's command as exiting 127, the shell's code for a command not found,"
                        + " with the reason it could not be started",
                () -> assertThat(refused)
                        .isInstanceOf(DockerEngineDidNotStartException.class)
                        .hasMessage("the Docker engine is not running and Vespera could not start it: "
                                + String.join(" ", SYSTEMCTL_START) + " exited 127: " + NO_SYSTEMCTL + "; "
                                + String.join(" ", DESKTOP_START) + " exited 1: " + NO_DESKTOP_PLUGIN
                                + ". Start Docker yourself and run the same command again."));
    }

    @Test
    @Story("Two invocations do not start the sidecars twice")
    @DisplayName("A start that finds the sidecar lock held waits for it, without a bound of its own, and asks the sidecars again once it has it")
    void aStartWaitsForTheLockAnotherHolds() throws Exception {
        ScriptedSidecarHealth health = ScriptedSidecarHealth.neitherAnswering();
        ScriptedDocker docker = ScriptedDocker.engineRunning();
        SidecarStarter starter = starter(settings(cpuImage), docker, health, HostSystem.LINUX);
        java.nio.file.Files.createDirectories(lockFolder);
        java.util.concurrent.CompletableFuture<SidecarStarter.Outcome> started;
        try (java.nio.channels.FileChannel held = java.nio.channels.FileChannel.open(
                        lockFolder.resolve("sidecars.lock"),
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

        claim("once the holder let go, the start asked the sidecars again, found them answering, and ran nothing",
                () -> assertThat(started.get(WAIT_AT_MOST.multipliedBy(5).toMillis(),
                                java.util.concurrent.TimeUnit.MILLISECONDS))
                        .isEqualTo(SidecarStarter.Outcome.ALREADY_ANSWERING));
        claim("no command was run at any point", () -> assertThat(docker.commands()).isEmpty());
    }

    @Test
    @Story("Vespera refuses, naming why, when it cannot start them")
    @DisplayName("When the sidecar lock cannot be opened, the start is refused in one line naming the lock file, and not with a stack trace")
    void aLockThatCannotBeOpenedIsRefused() throws IOException {
        Path notAFolder = Files.writeString(lockFolder.resolve("a-file-not-a-folder"), "");
        ScriptedDocker docker = ScriptedDocker.engineRunning();
        SidecarStarter starter = new SidecarStarter(settings(cpuImage), docker, ScriptedSidecarHealth.neitherAnswering(),
                HostSystem.LINUX, () -> ComposeFiles.in(REPOSITORY), notAFolder, CHECK_EVERY);

        Throwable refused = catchThrowable(starter::ensureRunning);

        claim("the start is refused with the line naming the lock file it could not open and saying how to give it one"
                        + " it can, whatever the system's reason",
                () -> assertThat(refused)
                        .isInstanceOf(SidecarLockUnavailableException.class)
                        .isInstanceOf(SidecarStartRefusedException.class)
                        .message()
                        .matches(java.util.regex.Pattern.quote("the sidecars are not answering and Vespera cannot start"
                                        + " them: the lock file " + notAFolder.resolve("sidecars.lock") + " cannot be"
                                        + " opened (")
                                + ".+"
                                + java.util.regex.Pattern.quote("). Make its folder writable, or name another with"
                                        + " vespera.corpus-root-lock-folder, and run the same command again.")));
        claim("and no command was run", () -> assertThat(docker.commands()).isEmpty());
    }

    @Test
    @Story("Vespera refuses, naming why, when it cannot start them")
    @DisplayName("When the compose files cannot be written out, the start is refused in one line saying so, and not with a stack trace")
    void composeFilesThatCannotBeWrittenAreRefused() {
        ScriptedDocker docker = ScriptedDocker.engineRunning();
        SidecarStarter starter = new SidecarStarter(settings(cpuImage), docker, ScriptedSidecarHealth.neitherAnswering(),
                HostSystem.LINUX, () -> {
                    throw new java.io.UncheckedIOException(new IOException(NO_SPACE));
                }, lockFolder, CHECK_EVERY);

        Throwable refused = catchThrowable(starter::ensureRunning);

        claim("the start is refused with the line naming the reason and the temporary folder they are written to",
                () -> assertThat(refused)
                        .isInstanceOf(ComposeFilesUnwritableException.class)
                        .isInstanceOf(SidecarStartRefusedException.class)
                        .hasMessage("the sidecars are not answering and Vespera cannot start them: the compose files it"
                                + " starts them from cannot be written out (" + NO_SPACE + "). Make room in "
                                + System.getProperty("java.io.tmpdir") + ", or make it writable, and run the same"
                                + " command again."));
        claim("and no command was run", () -> assertThat(docker.commands()).isEmpty());
    }

    private void assertTheEngineIsStartedBy(HostSystem host, List<String> startCommand) {
        ScriptedSidecarHealth health = ScriptedSidecarHealth.neitherAnswering();
        ScriptedDocker docker = ScriptedDocker.engineAnsweringAfter(ENGINE_CHECKS_FAILED)
                .answering(startCommand, 0, "")
                .whenUpSucceeds(() -> health.answering(true, true));

        SidecarStarter.Outcome outcome = starter(settings(cpuImage), docker, health, host).ensureRunning();

        claim("the starter says it started them", () -> assertThat(outcome).isEqualTo(SidecarStarter.Outcome.STARTED));
        claim("it checked the engine, started it with " + String.join(" ", startCommand) + ", checked it again"
                        + " until it answered, after failing " + ENGINE_CHECKS_FAILED + " checks, and then ran up",
                () -> assertThat(docker.commands()).containsExactly(
                        ScriptedDocker.ENGINE_CHECK,
                        startCommand,
                        ScriptedDocker.ENGINE_CHECK,
                        ScriptedDocker.ENGINE_CHECK,
                        up(COMPOSE_FILE)));
    }

    /** The commands run where the engine is not running, {@code firstStart} exits 1 and every other command exits 0. */
    private List<List<String>> commandsWhereTheFirstStartFails(HostSystem host, List<String> firstStart) {
        ScriptedSidecarHealth health = ScriptedSidecarHealth.neitherAnswering();
        ScriptedDocker docker = ScriptedDocker.engineAnsweringAfter(ENGINE_CHECKS_FAILED)
                .answering(firstStart, 1, NO_DESKTOP_PLUGIN)
                .otherCommandsExiting(0)
                .whenUpSucceeds(() -> health.answering(true, true));
        starter(settings(cpuImage), docker, health, host).ensureRunning();
        return docker.commands();
    }

    private SidecarStarter starter(
            SidecarStartSettings settings, ScriptedDocker docker, ScriptedSidecarHealth health, HostSystem host) {
        Supplier<ComposeFiles> theRepositorysFiles = () -> ComposeFiles.in(REPOSITORY);
        return new SidecarStarter(settings, docker, health, host, theRepositorysFiles, lockFolder, CHECK_EVERY);
    }

    private static SidecarStartSettings settings(String image) {
        return new SidecarStartSettings(true, DOCLING_HERE, OLLAMA_HERE, image, WAIT_AT_MOST);
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

    private static boolean isAnUp(List<String> command) {
        return command.contains("compose") && command.contains("up");
    }

    @SuppressWarnings("unchecked")
    private static String doclingImageIn(Path composeFile) throws IOException {
        Map<String, Object> compose = new Yaml().load(Files.readString(composeFile));
        Map<String, Object> services = (Map<String, Object>) compose.get("services");
        return (String) ((Map<String, Object>) services.get("docling-serve")).get("image");
    }
}
