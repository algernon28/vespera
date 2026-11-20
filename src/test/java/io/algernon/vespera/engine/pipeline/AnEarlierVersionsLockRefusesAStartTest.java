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
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

/**
 * The lock file an earlier version of Vespera takes around a start of the containers, held, refuses each of the four
 * actions that take the lock of this version, before any command; nobody holding it, it refuses nothing; and it is kept
 * beside this version's lock for the length of the action and released after, so that an earlier version starting
 * meanwhile waits for it (ADR-433 §6, amending ADR-323 §6).
 *
 * <p>No Docker is needed: the starter is given a {@link ScriptedDocker}, which records every command, and a {@link
 * ScriptedContainerHealth}. The earlier lock is held by this JVM, on the byte ADR-323 §6 locks, which the starter meets
 * as another lock of this process; a lock is probed through a channel kept open for the length of the test, since on
 * some systems closing a channel releases every lock the process holds on its file.
 */
@Epic("Pipeline")
@Feature("The containers")
@Issue("891")
@Link(name = "ADR-433", url = Adr.DOCLING_AND_OLLAMA_ARE_CONTAINERS_WHERE_VESPERA_RUNS_THEM, type = "adr")
@Link(name = "ADR-323", url = Adr.VESPERA_STARTS_DOCKER_AND_THE_SIDECARS_AND_STAGE_2_WAITS_FOR_A_STARTING_CONVERTER, type = "adr")
class AnEarlierVersionsLockRefusesAStartTest {

    /** The repository root, where Maven runs the tests from and where the compose files lie. */
    private static final Path REPOSITORY = Path.of("").toAbsolutePath();

    /** The lock file of this version and the earlier one's, in the folder of the corpus-root locks. */
    private static final String THE_LOCK = "containers.lock";

    private static final String THE_EARLIER_LOCK = "sidecars.lock";

    /** The byte both versions lock, one past the end of the file (ADR-323 §6). */
    private static final long LOCKED_BYTE = Long.MAX_VALUE - 1;

    /** Where docling-serve and Ollama are looked for on this machine, as the shipped configuration has them. */
    private static final URI DOCLING_HERE = URI.create("http://localhost:5001");

    private static final URI OLLAMA_HERE = URI.create("http://127.0.0.1:11434");

    /** How long these tests wait for a container to answer: one second, so a wait that runs out is quick. */
    private static final Duration WAIT_AT_MOST = Duration.ofSeconds(1);

    /** How often they ask meanwhile. */
    private static final Duration CHECK_EVERY = Duration.ofMillis(20);

    /** What a probe of a lock finds. */
    private static final String HELD_HERE = "held by this process";

    private static final String FREE = "free";

    /** The Docling image {@code compose.yaml} builds, read from the file as it stands. */
    private static String cpuImage;

    @TempDir
    Path lockFolder;

    @BeforeAll
    static void readTheImageTheFileBuilds() throws IOException {
        cpuImage = doclingImageIn(REPOSITORY.resolve("compose.yaml"));
    }

    @Test
    @Story("An earlier Vespera and this one never start the containers at once")
    @DisplayName("While an earlier version holds its lock, a start of the containers is refused in one line, before any command, and leaves this version's lock free")
    void aLiveEarlierLockRefusesAStartBeforeAnyCommand() throws IOException {
        ScriptedDocker docker = ScriptedDocker.engineRunning();
        ContainerStarter starter = starter(docker, ScriptedContainerHealth.neitherAnswering());
        Throwable refused;
        try (FileChannel earlier = channelOn(THE_EARLIER_LOCK);
                FileLock held = earlier.lock(LOCKED_BYTE, 1, false)) {
            refused = catchThrowable(starter::ensureRunning);
        }

        claim("the start is refused as a start of the containers is, so the command prints its one line and exits 1",
                () -> assertThat(refused).isInstanceOf(ContainerStartRefusedException.class));
        claim("its line names the earlier lock file and says what to do, whole",
                () -> assertThat(refused.getMessage()).isEqualTo(theStartsRefusal()));
        claim("and no command was run: not the engine's check, not up", () -> assertThat(docker.commands()).isEmpty());
        try (FileChannel probe = channelOn(THE_LOCK)) {
            claim("this version's lock was released with the refusal",
                    () -> assertThat(whoHolds(probe)).isEqualTo(FREE));
        }
    }

    @Test
    @Story("An earlier Vespera and this one never start the containers at once")
    @DisplayName("While an earlier version holds its lock, the engine's start, the stop of the containers and the shut-down are each refused in their own line, before any command")
    void aLiveEarlierLockRefusesTheEnginesStartTheStopAndTheShutDown() throws IOException {
        ScriptedDocker forTheEngine = ScriptedDocker.engineRunning();
        ScriptedDocker forTheStop = ScriptedDocker.engineRunning();
        ScriptedDocker forTheShutDown = ScriptedDocker.engineRunning();
        Throwable engine;
        Optional<String> stopped;
        ContainerStarter.ShutDown shutDown;
        try (FileChannel earlier = channelOn(THE_EARLIER_LOCK);
                FileLock held = earlier.lock(LOCKED_BYTE, 1, false)) {
            engine = catchThrowable(
                    starter(forTheEngine, ScriptedContainerHealth.neitherAnswering())::ensureTheEngineRuns);
            // The stop asks for the lock only where a container on this machine answers (ADR-390 §3).
            stopped = starter(forTheStop, ScriptedContainerHealth.bothAnswering()).stopTheContainers();
            shutDown = starter(forTheShutDown, ScriptedContainerHealth.bothAnswering()).shutDown();
        }
        String held = "an earlier version of Vespera holds the lock file " + theEarlierLockFile();

        claim("the engine's start, Start Docker's first part, is refused in the same line as a start of the containers",
                () -> assertThat(engine).isInstanceOf(ContainerStartRefusedException.class)
                        .hasMessage(theStartsRefusal()));
        claim("the stop of the containers says they were not stopped, and why, whole",
                () -> assertThat(stopped).contains("the containers were not stopped: " + held + "."));
        claim("the shut-down is not done, and says Docker was not shut down, and why, whole",
                () -> assertThat(shutDown).isEqualTo(
                        new ContainerStarter.ShutDown(false, "Docker was not shut down: " + held + ".")));
        claim("none of the three ran a command",
                () -> assertThat(List.of(forTheEngine.commands(), forTheStop.commands(), forTheShutDown.commands()))
                        .containsExactly(List.of(), List.of(), List.of()));
    }

    @Test
    @Story("An earlier Vespera and this one never start the containers at once")
    @DisplayName("An earlier version's lock file that nobody holds refuses nothing: the start goes on as before")
    void anEarlierLockNobodyHoldsRefusesNothing() throws IOException {
        Files.createDirectories(lockFolder);
        Files.createFile(lockFolder.resolve(THE_EARLIER_LOCK));
        ScriptedDocker docker = ScriptedDocker.engineRunning();
        // Docling fails the check made before the locks and answers the one made under them, Ollama answering
        // throughout: a start that went past the earlier lock finds both answering and runs nothing.
        ScriptedContainerHealth health = ScriptedContainerHealth.doclingAnsweringAfter(1);

        ContainerStarter.Outcome outcome = starter(docker, health).ensureRunning();

        claim("the start went past the earlier lock, asked the containers again under the locks and found them"
                        + " answering", () -> assertThat(outcome).isEqualTo(ContainerStarter.Outcome.ALREADY_ANSWERING));
        claim("and ran no command", () -> assertThat(docker.commands()).isEmpty());
    }

    @Test
    @Story("An earlier Vespera and this one never start the containers at once")
    @DisplayName("A start keeps the earlier version's lock beside its own while it brings the containers up, so an earlier version waits, and releases both when it ends")
    void theEarlierLockIsKeptForTheLengthOfAStartAndReleasedAfter() throws IOException {
        List<String> duringUp = new ArrayList<>();
        Throwable ended;
        try (FileChannel earlierProbe = channelOn(THE_EARLIER_LOCK); FileChannel probe = channelOn(THE_LOCK)) {
            ScriptedDocker docker = ScriptedDocker.engineRunning().whenUpSucceeds(() -> {
                duringUp.add(whoHolds(probe));
                duringUp.add(whoHolds(earlierProbe));
            });
            // Neither container ever answers, so the start runs up, waits the second it is given, and is refused.
            ended = catchThrowable(starter(docker, ScriptedContainerHealth.neitherAnswering())::ensureRunning);

            claim("as up ran, the start held its own lock and the earlier version's, both in this process",
                    () -> assertThat(duringUp).containsExactly(HELD_HERE, HELD_HERE));
            claim("the start ended as one whose containers did not answer within the wait",
                    () -> assertThat(ended).isInstanceOf(ContainersDidNotAnswerException.class));
            claim("and once it had ended, both locks were free",
                    () -> assertThat(List.of(whoHolds(probe), whoHolds(earlierProbe))).containsExactly(FREE, FREE));
        }
    }

    private ContainerStarter starter(ScriptedDocker docker, ScriptedContainerHealth health) {
        return new ContainerStarter(new ContainerStartSettings(DOCLING_HERE, OLLAMA_HERE, cpuImage, WAIT_AT_MOST),
                docker, health, HostSystem.LINUX, () -> ComposeFiles.in(REPOSITORY), lockFolder, CHECK_EVERY);
    }

    private String theEarlierLockFile() {
        return lockFolder.resolve(THE_EARLIER_LOCK).toAbsolutePath().toString();
    }

    /** ADR-433 §6's line for a start, and for the engine's start, refused at the earlier lock. */
    private String theStartsRefusal() {
        return "the containers are not answering and Vespera cannot start them: an earlier version of Vespera holds the"
                + " lock file " + theEarlierLockFile() + ", and may be starting or stopping them. Let it finish, or end"
                + " it, and run the same command again.";
    }

    private FileChannel channelOn(String name) throws IOException {
        Files.createDirectories(lockFolder);
        return FileChannel.open(lockFolder.resolve(name), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    }

    /** Who holds the locked byte of {@code channel}'s file: this process, another, or nobody, who is let go at once. */
    private static String whoHolds(FileChannel channel) {
        try {
            FileLock lock = channel.tryLock(LOCKED_BYTE, 1, false);
            if (lock == null) {
                return "held by another process";
            }
            lock.release();
            return FREE;
        } catch (OverlappingFileLockException heldHere) {
            return HELD_HERE;
        } catch (IOException unlockable) {
            return "cannot be locked: " + unlockable.getMessage();
        }
    }

    @SuppressWarnings("unchecked")
    private static String doclingImageIn(Path composeFile) throws IOException {
        Map<String, Object> compose = new Yaml().load(Files.readString(composeFile));
        Map<String, Object> services = (Map<String, Object>) compose.get("services");
        return (String) ((Map<String, Object>) services.get("docling-serve")).get("image");
    }
}
