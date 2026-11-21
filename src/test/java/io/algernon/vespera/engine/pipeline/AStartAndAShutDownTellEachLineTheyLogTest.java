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
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What a start and a shut-down of the containers tell the one who asked for them, line by line (ADR-437 §1 to §3): a
 * {@link ContainerStarter} {@link ContainerStarter#telling telling} a {@code Consumer<String>} hands it each line it logs,
 * its words alone, after logging it: what {@code vespera run}'s start logs, {@code up}'s console output among it, and the
 * lines ADR-437 §2 adds, the engine's start, the two stops of the shut-down, and what each of those commands printed once
 * it has exited, NULs and byte-order marks taken out, trailing white space off and blank lines left out. A refusal is
 * thrown, and a shut-down's line returned, and neither is told.
 *
 * <p>No Docker is needed: the starter is given a {@link ScriptedDocker} and a {@link ScriptedContainerHealth}, as {@code
 * ContainerStartTest}'s is. The waits are bounded by one second and asked every 20 ms, so each wait that ends ends
 * within its first second, and its line says 0 seconds.
 */
@Epic("Pipeline")
@Feature("The containers")
@Issue("921")
@Link(name = "ADR-437", url = Adr.START_DOCKERS_AND_SHUT_DOWN_DOCKERS_LINES_ARE_SHOWN_IN_THE_OUTPUT_PANEL, type = "adr")
class AStartAndAShutDownTellEachLineTheyLogTest {

    /** The repository root, which is where Maven runs the tests from and where the compose files lie. */
    private static final Path REPOSITORY = Path.of("").toAbsolutePath();

    private static final Path COMPOSE_FILE = REPOSITORY.resolve("compose.yaml");

    private static final URI DOCLING_HERE = URI.create("http://localhost:5001");

    private static final URI OLLAMA_HERE = URI.create("http://127.0.0.1:11434");

    /** How long these tests wait for the engine or a container: one second, which each line of a wait names. */
    private static final Duration WAIT_AT_MOST = Duration.ofSeconds(1);

    private static final Duration CHECK_EVERY = Duration.ofMillis(20);

    /** Engine checks the engine fails before it answers, where the start starts it. */
    private static final int ENGINE_CHECKS_FAILED = 2;

    private static final List<String> COLIMA_START = List.of("colima", "start");

    /**
     * What {@code colima start} prints, as its logger writes it: two lines, a blank one between, the second with
     * trailing blanks; the blank line is not told and the trailing blanks are taken off.
     */
    private static final String COLIMA_PRINTS = "INFO[0000] starting colima\n\nINFO[0012] done   \n";

    /** What {@code up} prints, a line for each container it started. */
    private static final String UP_PRINTS = " Container vespera-docling-serve-1  Started\n"
            + " Container vespera-ollama-1  Started\n";

    private static final List<String> COMPOSE_STOP = List.of("docker", "compose", "-p", "vespera", "stop");

    private static final List<String> SYSTEMCTL_STOP =
            List.of("systemctl", "--no-ask-password", "stop", "docker.socket", "docker.service");

    /** What {@code docker compose stop} prints, both streams together, ending on a blank line, which is not told. */
    private static final String COMPOSE_STOP_PRINTS = " Container vespera-ollama-1  Stopping\n"
            + " Container vespera-ollama-1  Stopped\n\n";

    /** The Docling image {@code compose.yaml} builds, read from the file as it stands. */
    private static String cpuImage;

    @TempDir
    Path lockFolder;

    @TempDir
    Path profileFolder;

    @BeforeAll
    static void readTheImageTheFileBuilds() throws IOException {
        cpuImage = ContainerStartTest.doclingImageIn(COMPOSE_FILE);
    }

    @Test
    @Story("A start and a shut-down say what they do as they do it")
    @DisplayName("A start tells each line it logs, in order: the engine's start and what it printed, the wait for the engine, the containers' start and each line it printed, and the wait for the containers")
    void aStartTellsEachLineItLogsInOrderTheEnginesStartAndWhatItPrintedAmongThem() {
        ScriptedContainerHealth health = ScriptedContainerHealth.neitherAnswering();
        ScriptedDocker docker = ScriptedDocker.engineAnsweringAfter(ENGINE_CHECKS_FAILED)
                .answering(COLIMA_START, 0, COLIMA_PRINTS)
                .upAnswering(0, UP_PRINTS)
                .whenUpSucceeds(() -> health.answering(true, true));
        List<String> told = new CopyOnWriteArrayList<>();

        ContainerStarter.Outcome outcome =
                starter(docker, health, HostSystem.MAC).telling(told::add).ensureRunning();

        claim("the containers were started", () -> assertThat(outcome).isEqualTo(ContainerStarter.Outcome.STARTED));
        claim("and the lines told were, whole and in order: the engine's start command, the two lines it printed with"
                        + " its blank line left out and its trailing blanks taken off, the wait for the engine and its"
                        + " end, the containers' start command and each line it printed, and the wait for both"
                        + " containers and its end, each wait bounded by the one second these tests give it",
                () -> assertThat(told).containsExactly(
                        "Starting the Docker engine: colima start",
                        "INFO[0000] starting colima",
                        "INFO[0012] done",
                        "Waiting up to 1 seconds for the Docker engine to answer",
                        "The Docker engine answers after 0 seconds",
                        "Starting the containers: docker compose -p vespera -f " + COMPOSE_FILE
                                + " up -d --build --no-recreate",
                        " Container vespera-docling-serve-1  Started",
                        " Container vespera-ollama-1  Started",
                        "Waiting up to 1 seconds for docling-serve and Ollama to answer",
                        "docling-serve and Ollama answer after 0 seconds"));
    }

    @Test
    @Story("A start and a shut-down say what they do as they do it")
    @DisplayName("A start that is refused tells every line it logged before the refusal, and not the refusal, which it throws")
    void aRefusedStartTellsEveryLineBeforeItsRefusalAndNotTheRefusal() {
        String upPrints = "Error response from daemon: pull access denied for vespera/docling-serve\n";
        ScriptedDocker docker = ScriptedDocker.engineRunning().upAnswering(1, upPrints);
        List<String> told = new CopyOnWriteArrayList<>();

        Throwable refused = catchThrowable(() -> starter(docker, ScriptedContainerHealth.neitherAnswering(),
                HostSystem.LINUX).telling(told::add).ensureRunning());

        claim("the start was refused because the containers' start exited 1",
                () -> assertThat(refused).isInstanceOf(ContainersDidNotStartException.class));
        claim("and the lines told were, whole and in order, the containers' start command and the one line it printed;"
                        + " the engine answering, nothing of it was told, and the refusal was not told either",
                () -> assertThat(told).containsExactly(
                        "Starting the containers: docker compose -p vespera -f " + COMPOSE_FILE
                                + " up -d --build --no-recreate",
                        "Error response from daemon: pull access denied for vespera/docling-serve"));
    }

    @Test
    @Story("A start and a shut-down say what they do as they do it")
    @DisplayName("A shut-down tells the stop of the containers and what it printed, then the stop of the engine, and returns its own line without telling it")
    void aShutDownTellsEachStopAndWhatItPrintedAndNotItsOwnLine() {
        ScriptedDocker docker = ScriptedDocker.engineRunning()
                .answering(COMPOSE_STOP, 0, COMPOSE_STOP_PRINTS)
                .answering(SYSTEMCTL_STOP, 0, "");
        List<String> told = new CopyOnWriteArrayList<>();

        ContainerStarter.ShutDown shutDown =
                starter(docker, ScriptedContainerHealth.bothAnswering(), HostSystem.LINUX).telling(told::add).shutDown();

        claim("the shut-down stopped both, in its line", () -> assertThat(shutDown)
                .isEqualTo(new ContainerStarter.ShutDown(true, ContainerStarter.SHUT_DOWN_DONE)));
        claim("and the lines told were, whole and in order, the stop of the containers, the two lines it printed with its"
                        + " blank line left out, and the stop of the engine, which printed nothing; its own line was"
                        + " returned and not told",
                () -> assertThat(told).containsExactly(
                        "Stopping the containers: docker compose -p vespera stop",
                        " Container vespera-ollama-1  Stopping",
                        " Container vespera-ollama-1  Stopped",
                        "Stopping the Docker engine: systemctl --no-ask-password stop docker.socket docker.service"));
    }

    @Test
    @Story("A start and a shut-down say what they do as they do it")
    @DisplayName("On Windows, the line on .wslconfig a start logs is told once, and returned")
    void onWindowsTheWslconfigLineIsToldAsItIsLogged() {
        ScriptedDocker docker = ScriptedDocker.engineRunning();
        List<String> told = new CopyOnWriteArrayList<>();
        ContainerStartSettings settings =
                new ContainerStartSettings(DOCLING_HERE, OLLAMA_HERE, cpuImage, WAIT_AT_MOST, "", profileFolder);

        Optional<String> line = new ContainerStarter(settings, docker, ScriptedContainerHealth.bothAnswering(),
                HostSystem.WINDOWS, () -> ComposeFiles.in(REPOSITORY), lockFolder, CHECK_EVERY)
                .telling(told::add).keepTheWslDistributionUp();

        claim("the profile folder held no .wslconfig, so one was written and its line returned",
                () -> assertThat(line).isPresent().get().asString()
                        .startsWith("Vespera wrote " + profileFolder.resolve(".wslconfig")));
        claim("and that line, whole, is the one line told", () -> assertThat(told).containsExactly(line.orElseThrow()));
        claim("and no command was run", () -> assertThat(docker.commands()).isEmpty());
    }

    private ContainerStarter starter(ScriptedDocker docker, ScriptedContainerHealth health, HostSystem host) {
        ContainerStartSettings settings = new ContainerStartSettings(DOCLING_HERE, OLLAMA_HERE, cpuImage, WAIT_AT_MOST);
        return new ContainerStarter(settings, docker, health, host, () -> ComposeFiles.in(REPOSITORY), lockFolder,
                CHECK_EVERY);
    }
}
