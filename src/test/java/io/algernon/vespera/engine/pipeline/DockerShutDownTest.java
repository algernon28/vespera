package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the shut-down runs, the control panel's <i>Shut down Docker</i> behind it, and what it says (ADR-390 §3, §6):
 * {@code docker compose -p vespera stop}, then, only where that exited 0, the engine's one stop, and nothing else, never
 * {@code down}; a refused step is one line, the lock's and an output file's among them; and each stop waits for
 * {@code sidecars.lock} where another holds it. And what closing the window runs: the sidecars' stop alone, only where
 * one answers and both are looked for on this machine (ADR-390 §5). Once the sidecars are stopped, an engine stop whose
 * output file fails says so (ADR-395 §6).
 *
 * <p>No Docker is needed: the starter is given a {@link ScriptedDocker}, which records every command and its limit. The
 * refusals' last lines are written as Docker Compose printed its own with the daemon down (ADR-390's probe H) and, for
 * {@code systemctl}, as a synthetic refusal: what polkit prints was not measured.
 */
@Epic("Pipeline")
@Feature("The sidecars")
@Issue("816")
@Link(name = "ADR-390", url = Adr.VESPERA_USES_DOCKER_ENGINE_ALONE_AND_A_BUTTON_SHUTS_IT_DOWN, type = "adr")
class DockerShutDownTest {

    private static final Path REPOSITORY = Path.of("").toAbsolutePath();

    private static final URI DOCLING_HERE = URI.create("http://localhost:5001");

    private static final URI OLLAMA_HERE = URI.create("http://127.0.0.1:11434");

    private static final URI DOCLING_ELSEWHERE = URI.create("http://docling.example.org:5001");

    private static final URI OLLAMA_ELSEWHERE = URI.create("http://ollama.example.org:11434");

    /** Why an output file could not be written, in a test: the system's words for a full drive. */
    private static final String DRIVE_FULL = "No space left on device";

    /** How long a test holds the sidecar lock: longer than {@link #WAIT_AT_MOST}, so a bounded wait would have ended. */
    private static final Duration HELD_FOR = Duration.ofMillis(1500);

    /** The bound every command of the shut-down is killed at: one second in these tests. */
    private static final Duration WAIT_AT_MOST = Duration.ofSeconds(1);

    private static final Duration CHECK_EVERY = Duration.ofMillis(20);

    private static final String AN_IMAGE = "vespera/docling-serve-cpu-libreoffice:any";

    private static final List<String> COMPOSE_STOP = List.of("docker", "compose", "-p", "vespera", "stop");

    private static final List<String> SYSTEMCTL_STOP =
            List.of("systemctl", "--no-ask-password", "stop", "docker.socket", "docker.service");

    private static final List<String> COLIMA_STOP = List.of("colima", "stop");

    private static final List<String> IN_WSL = List.of("wsl", "-u", "root", "--exec");

    private static final String DONE =
            "the sidecars and the Docker engine are stopped; Vespera starts them again when it next needs them.";

    /** What Docker Compose printed last, exit 1, with the daemon down (ADR-390's probe H). */
    private static final String NO_DAEMON = "failed to connect to the docker API at unix:///var/run/docker.sock; check"
            + " if the path is correct and if the daemon is running: dial unix /var/run/docker.sock: connect: no such"
            + " file or directory";

    private static final int REFUSED = 1;

    /** The shell's code for a command not found, which a program that cannot be started counts as (ADR-323 §5). */
    private static final int NOT_FOUND = 127;

    @TempDir
    Path lockFolder;

    @Test
    @Story("A button stops the sidecars and then the Docker engine")
    @DisplayName("The shut-down stops the sidecars, then the engine, each system with its own one command, and runs nothing else")
    void theSidecarsAreStoppedThenTheEngineAndNothingElseIsRun() {
        Map<HostSystem, List<List<String>>> expected = new LinkedHashMap<>();
        expected.put(HostSystem.LINUX, List.of(COMPOSE_STOP, SYSTEMCTL_STOP));
        expected.put(HostSystem.MAC, List.of(COMPOSE_STOP, COLIMA_STOP));
        expected.put(HostSystem.WINDOWS, List.of(concat(IN_WSL, COMPOSE_STOP),
                concat(IN_WSL, List.of("systemctl", "stop", "docker.socket", "docker.service"))));

        for (Map.Entry<HostSystem, List<List<String>>> system : expected.entrySet()) {
            ScriptedDocker docker = ScriptedDocker.engineRunning().otherCommandsExiting(0);

            SidecarStarter.ShutDown shutDown = starter(docker, system.getKey()).shutDown();

            claim("on " + system.getKey() + ", it said both are stopped",
                    () -> assertThat(shutDown).isEqualTo(new SidecarStarter.ShutDown(true, DONE)));
            claim("and ran, in this order and whole, docker compose -p vespera stop and then the system's one stop of"
                            + " the engine, and nothing else: no engine check, no down",
                    () -> assertThat(docker.commands()).containsExactlyElementsOf(system.getValue()));
            claim("and each was given the " + WAIT_AT_MOST.toSeconds() + " second(s) these tests bound every wait"
                            + " by as its limit",
                    () -> assertThat(docker.limits()).containsExactly(WAIT_AT_MOST, WAIT_AT_MOST));
        }
    }

    @Test
    @Story("A button stops the sidecars and then the Docker engine")
    @DisplayName("A stop of the sidecars that fails, or whose docker command is missing, stops no engine and says so in one line")
    void aStopThatFailsLeavesTheEngineAsItWas() {
        ScriptedDocker daemonDown = ScriptedDocker.engineRunning()
                .answering(COMPOSE_STOP, REFUSED, NO_DAEMON + "\n")
                .otherCommandsExiting(0);

        SidecarStarter.ShutDown refused = starter(daemonDown, HostSystem.LINUX).shutDown();

        claim("it said the stop's command, its exit and its last line, and that the engine was not stopped",
                () -> assertThat(refused).isEqualTo(new SidecarStarter.ShutDown(false,
                        "Docker was not shut down: docker compose -p vespera stop exited " + REFUSED + ": " + NO_DAEMON
                                + ". The Docker engine was not stopped.")));
        claim("and ran the stop of the sidecars alone: no stop of the engine followed",
                () -> assertThat(daemonDown.commands()).containsExactly(COMPOSE_STOP));

        ScriptedDocker none = ScriptedDocker.notInstalled();
        SidecarStarter.ShutDown missing = starter(none, HostSystem.LINUX).shutDown();
        claim("with no docker command, the stop counts as exit " + NOT_FOUND + " with the reason, and nothing else is"
                        + " run",
                () -> assertThat(List.of(missing, none.commands())).containsExactly(
                        new SidecarStarter.ShutDown(false, "Docker was not shut down: docker compose -p vespera stop"
                                + " exited " + NOT_FOUND + ": Cannot run program \"docker\": error=2, No such file or"
                                + " directory. The Docker engine was not stopped."),
                        List.of(COMPOSE_STOP)));
    }

    @Test
    @Story("A button stops the sidecars and then the Docker engine")
    @DisplayName("An engine stop that is refused, or whose program is missing, leaves the sidecars stopped and names the command")
    void anEngineStopRefusedLeavesTheSidecarsStoppedAndSaysSo() {
        String polkitRefuses = "Failed to stop docker.socket: Access denied";
        ScriptedDocker linux = ScriptedDocker.engineRunning()
                .answering(COMPOSE_STOP, 0, " Container vespera-ollama-1 Stopped\n")
                .answering(SYSTEMCTL_STOP, REFUSED, polkitRefuses + "\n");

        SidecarStarter.ShutDown refused = starter(linux, HostSystem.LINUX).shutDown();

        claim("on Linux, it said the sidecars are stopped and the engine is not, naming systemctl's command, its exit"
                        + " and its last line",
                () -> assertThat(refused).isEqualTo(new SidecarStarter.ShutDown(false,
                        "the sidecars are stopped, but the Docker engine is not: systemctl --no-ask-password stop"
                                + " docker.socket docker.service exited " + REFUSED + ": " + polkitRefuses
                                + ". Stop Docker yourself, or leave it running.")));
        claim("and both commands ran, the sidecars' stop first", () -> assertThat(linux.commands())
                .containsExactly(COMPOSE_STOP, SYSTEMCTL_STOP));

        ScriptedDocker mac = ScriptedDocker.engineRunning()
                .answering(COMPOSE_STOP, 0, "")
                .cannotLaunch(COLIMA_STOP, "Cannot run program \"colima\": error=2, No such file or directory");
        SidecarStarter.ShutDown noColima = starter(mac, HostSystem.MAC).shutDown();
        claim("on macOS without Colima, colima stop counts as exit " + NOT_FOUND + " with its reason, the sidecars"
                        + " stopped before it",
                () -> assertThat(List.of(noColima, mac.commands())).containsExactly(
                        new SidecarStarter.ShutDown(false, "the sidecars are stopped, but the Docker engine is not:"
                                + " colima stop exited " + NOT_FOUND + ": Cannot run program \"colima\": error=2, No"
                                + " such file or directory. Stop Docker yourself, or leave it running."),
                        List.of(COMPOSE_STOP, COLIMA_STOP)));
    }

    @Test
    @Story("Closing the control panel stops the sidecars")
    @DisplayName("Closing the window stops the sidecars alone, only where one answers on this machine, and a stop that fails is one line")
    void closingStopsTheSidecarsAloneWhereTheyAnswer() {
        ScriptedDocker answering = ScriptedDocker.engineRunning().otherCommandsExiting(0);
        Optional<String> stopped = starter(answering, HostSystem.LINUX).stopTheSidecars();
        claim("with the sidecars answering, it said nothing and ran docker compose -p vespera stop alone: no engine"
                        + " check and no stop of the engine",
                () -> assertThat(List.of(stopped, answering.commands()))
                        .containsExactly(Optional.empty(), List.of(COMPOSE_STOP)));

        ScriptedDocker silent = ScriptedDocker.engineRunning().otherCommandsExiting(0);
        Optional<String> nothing = starter(silent, HostSystem.LINUX, ScriptedSidecarHealth.neitherAnswering())
                .stopTheSidecars();
        claim("with neither sidecar answering, it said nothing and ran no command, so no engine behind its socket is"
                        + " started by closing the window",
                () -> assertThat(List.of(nothing, silent.commands())).containsExactly(Optional.empty(), List.of()));

        ScriptedDocker failing = ScriptedDocker.engineRunning().answering(COMPOSE_STOP, REFUSED, NO_DAEMON + "\n");
        Optional<String> refused = starter(failing, HostSystem.LINUX).stopTheSidecars();
        claim("a stop that exits " + REFUSED + " is said in one line naming the command, its exit and its last line",
                () -> assertThat(refused).contains("the sidecars were not stopped: docker compose -p vespera stop"
                        + " exited " + REFUSED + ": " + NO_DAEMON + "."));
    }

    @Test
    @Story("Closing the control panel stops the sidecars")
    @DisplayName("Closing the window stops the sidecars where one of the two answers, and runs nothing where one is looked for on another machine")
    @Issue("818")
    void closingStopsTheSidecarsWhereEitherAnswersAndNothingWhereOneIsOnAnotherMachine() {
        for (boolean doclingAnswers : List.of(true, false)) {
            ScriptedSidecarHealth one = ScriptedSidecarHealth.neitherAnswering();
            one.answering(doclingAnswers, !doclingAnswers);
            ScriptedDocker docker = ScriptedDocker.engineRunning().otherCommandsExiting(0);

            Optional<String> stopped = starter(settings(), docker, HostSystem.LINUX, one).stopTheSidecars();

            String which = doclingAnswers ? "docling-serve alone" : "Ollama alone";
            claim("with " + which + " answering, it said nothing and ran docker compose -p vespera stop, once and"
                            + " alone",
                    () -> assertThat(List.of(stopped, docker.commands()))
                            .containsExactly(Optional.empty(), List.of(COMPOSE_STOP)));
        }

        for (SidecarStartSettings elsewhere : List.of(
                new SidecarStartSettings(DOCLING_ELSEWHERE, OLLAMA_HERE, AN_IMAGE, WAIT_AT_MOST),
                new SidecarStartSettings(DOCLING_HERE, OLLAMA_ELSEWHERE, AN_IMAGE, WAIT_AT_MOST))) {
            ScriptedDocker docker = ScriptedDocker.engineRunning().otherCommandsExiting(0);

            Optional<String> nothing = starter(elsewhere, docker, HostSystem.LINUX,
                    ScriptedSidecarHealth.bothAnswering()).stopTheSidecars();

            claim("with docling-serve at " + elsewhere.doclingBaseUrl() + " and Ollama at "
                            + elsewhere.ollamaBaseUrl() + ", one of them on another machine, it said nothing and ran no"
                            + " command, though both answer",
                    () -> assertThat(List.of(nothing, docker.commands())).containsExactly(Optional.empty(), List.of()));
        }
    }

    @Test
    @Story("A button stops the sidecars and then the Docker engine")
    @DisplayName("A sidecar lock that cannot be opened, or a stop of the sidecars whose output file cannot be written, is said in one line by both stops, and nothing else is run")
    @Issue("818")
    void anUnusableLockOrOutputFileBeforeTheSidecarsStopIsOneLineOfEachStop() throws IOException {
        Path notAFolder = Files.createFile(lockFolder.resolve("a-file-where-the-folder-would-be"));
        String lockUnusable = "the lock file " + notAFolder.resolve("sidecars.lock").toAbsolutePath()
                + " cannot be opened (" + notAFolder + ")";

        ScriptedDocker forTheShutDown = ScriptedDocker.engineRunning().otherCommandsExiting(0);
        SidecarStarter.ShutDown noLock = new SidecarStarter(settings(), forTheShutDown,
                ScriptedSidecarHealth.bothAnswering(), HostSystem.LINUX, () -> ComposeFiles.in(REPOSITORY), notAFolder,
                CHECK_EVERY).shutDown();
        claim("a lock folder that is a file: the shut-down says Docker was not shut down, naming the lock file and"
                        + " why, and runs no command",
                () -> assertThat(List.of(noLock, forTheShutDown.commands())).containsExactly(
                        new SidecarStarter.ShutDown(false, "Docker was not shut down: " + lockUnusable + "."),
                        List.of()));

        ScriptedDocker forTheClosing = ScriptedDocker.engineRunning().otherCommandsExiting(0);
        Optional<String> noLockToStop = new SidecarStarter(settings(), forTheClosing,
                ScriptedSidecarHealth.bothAnswering(), HostSystem.LINUX, () -> ComposeFiles.in(REPOSITORY), notAFolder,
                CHECK_EVERY).stopTheSidecars();
        claim("and the stop of the sidecars alone says they were not stopped, naming the lock file and why, and runs"
                        + " no command",
                () -> assertThat(List.of(noLockToStop, forTheClosing.commands())).containsExactly(
                        Optional.of("the sidecars were not stopped: " + lockUnusable + "."), List.of()));

        String outputUnusable = "the temporary file the output of docker compose -p vespera stop goes to cannot be"
                + " written or read (" + DRIVE_FULL + ")";
        ScriptedDocker fullForTheShutDown = ScriptedDocker.engineRunning()
                .outputFileFails(COMPOSE_STOP, lockFolder, DRIVE_FULL)
                .otherCommandsExiting(0);
        SidecarStarter.ShutDown noOutput = starter(fullForTheShutDown, HostSystem.LINUX).shutDown();
        claim("the stop of the sidecars' output file unwritable: the shut-down says Docker was not shut down, naming"
                        + " the command and why, and stops no engine",
                () -> assertThat(List.of(noOutput, fullForTheShutDown.commands())).containsExactly(
                        new SidecarStarter.ShutDown(false, "Docker was not shut down: " + outputUnusable + "."),
                        List.of(COMPOSE_STOP)));

        ScriptedDocker fullForTheClosing = ScriptedDocker.engineRunning()
                .outputFileFails(COMPOSE_STOP, lockFolder, DRIVE_FULL)
                .otherCommandsExiting(0);
        Optional<String> noOutputToStop = starter(fullForTheClosing, HostSystem.LINUX).stopTheSidecars();
        claim("and the stop of the sidecars alone says they were not stopped, naming the command and why",
                () -> assertThat(List.of(noOutputToStop, fullForTheClosing.commands())).containsExactly(
                        Optional.of("the sidecars were not stopped: " + outputUnusable + "."), List.of(COMPOSE_STOP)));
    }

    @Test
    @Story("A button stops the sidecars and then the Docker engine")
    @DisplayName("Once the sidecars are stopped, an engine stop whose output file cannot be written says the sidecars were stopped and Docker was not shut down")
    @Issue("818")
    @Link(name = "ADR-395", url = Adr.ONE_VESPERA_RUNS_ON_A_MACHINE_AT_A_TIME, type = "adr")
    void anEngineStopsUnusableOutputFileSaysTheSidecarsWereStopped() {
        ScriptedDocker docker = ScriptedDocker.engineRunning()
                .outputFileFails(SYSTEMCTL_STOP, lockFolder, DRIVE_FULL)
                .otherCommandsExiting(0);

        SidecarStarter.ShutDown shutDown = starter(docker, HostSystem.LINUX).shutDown();

        claim("it said the sidecars were stopped but Docker was not shut down, naming the engine stop's command and"
                        + " why its output file failed",
                () -> assertThat(shutDown).isEqualTo(new SidecarStarter.ShutDown(false, "The sidecars were stopped,"
                        + " but Docker was not shut down: the temporary file the output of systemctl --no-ask-password"
                        + " stop docker.socket docker.service goes to cannot be written or read (" + DRIVE_FULL
                        + ").")));
        claim("after both commands ran, the sidecars' stop first",
                () -> assertThat(docker.commands()).containsExactly(COMPOSE_STOP, SYSTEMCTL_STOP));
    }

    @Test
    @Story("Two invocations do not start the sidecars twice")
    @DisplayName("Both stops wait for a sidecar lock another holds, without a bound of their own, and run their commands once they have it")
    @Issue("818")
    void bothStopsWaitForTheSidecarLockAnotherHolds() throws Exception {
        ScriptedDocker forTheShutDown = ScriptedDocker.engineRunning().otherCommandsExiting(0);
        SidecarStarter shuttingDown = starter(forTheShutDown, HostSystem.LINUX);
        Object shutDown = whileTheLockIsHeld(() -> shuttingDown.shutDown(), forTheShutDown, "the shut-down");
        claim("once the holder let go, the shut-down stopped the sidecars and then the engine, and said so",
                () -> assertThat(List.of(shutDown, forTheShutDown.commands())).containsExactly(
                        new SidecarStarter.ShutDown(true, DONE), List.of(COMPOSE_STOP, SYSTEMCTL_STOP)));

        ScriptedDocker forTheClosing = ScriptedDocker.engineRunning().otherCommandsExiting(0);
        SidecarStarter closing = starter(forTheClosing, HostSystem.LINUX);
        Object stopped = whileTheLockIsHeld(() -> closing.stopTheSidecars(), forTheClosing,
                "the stop of the sidecars alone");
        claim("and the stop of the sidecars alone, once it had the lock, ran docker compose -p vespera stop alone",
                () -> assertThat(List.of(stopped, forTheClosing.commands()))
                        .containsExactly(Optional.empty(), List.of(COMPOSE_STOP)));
    }

    /**
     * Holds {@code sidecars.lock} in this process for {@link #HELD_FOR}, longer than {@link #WAIT_AT_MOST}, while {@code
     * stop} runs on a thread of its own; claims it neither ended nor ran a command meanwhile, lets go, and answers what
     * it came to.
     */
    private Object whileTheLockIsHeld(Supplier<Object> stop, ScriptedDocker docker, String which) throws Exception {
        Files.createDirectories(lockFolder);
        CompletableFuture<Object> running;
        try (FileChannel held = FileChannel.open(lockFolder.resolve("sidecars.lock"), StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE);
                FileLock ignored = held.lock(Long.MAX_VALUE - 1, 1, false)) {
            running = CompletableFuture.supplyAsync(stop);
            Thread.sleep(HELD_FOR.toMillis());

            claim("while the lock is held, longer than the " + WAIT_AT_MOST.toSeconds() + " second(s) every command of"
                            + " these tests is bounded by, " + which + " neither refused nor ended, and ran no command",
                    () -> assertThat(List.of(running.isDone(), docker.commands())).containsExactly(false, List.of()));
        }
        return running.get(WAIT_AT_MOST.multipliedBy(5).toMillis(), TimeUnit.MILLISECONDS);
    }

    private SidecarStarter starter(ScriptedDocker docker, HostSystem host) {
        return starter(docker, host, ScriptedSidecarHealth.bothAnswering());
    }

    private SidecarStarter starter(ScriptedDocker docker, HostSystem host, ScriptedSidecarHealth health) {
        return starter(settings(), docker, host, health);
    }

    private SidecarStarter starter(
            SidecarStartSettings settings, ScriptedDocker docker, HostSystem host, ScriptedSidecarHealth health) {
        Supplier<ComposeFiles> theRepositorysFiles = () -> ComposeFiles.in(REPOSITORY);
        return new SidecarStarter(settings, docker, health, host, theRepositorysFiles, lockFolder, CHECK_EVERY);
    }

    private static SidecarStartSettings settings() {
        return new SidecarStartSettings(DOCLING_HERE, OLLAMA_HERE, AN_IMAGE, WAIT_AT_MOST);
    }

    private static List<String> concat(List<String> first, List<String> then) {
        List<String> whole = new java.util.ArrayList<>(first);
        whole.addAll(then);
        return List.copyOf(whole);
    }
}
