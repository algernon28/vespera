package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the shut-down runs, the control panel's <i>Shut down Docker</i> behind it, and what it says (ADR-389 §3, §6):
 * {@code docker compose -p vespera stop}, then, only where that exited 0, the engine's one stop, and nothing else, never
 * {@code down}; a refused step is one line. And what closing the window runs: the sidecars' stop alone, only
 * where one answers (ADR-389 §5).
 *
 * <p>No Docker is needed: the starter is given a {@link ScriptedDocker}, which records every command and its limit. The
 * refusals' last lines are written as Docker Compose printed its own with the daemon down (ADR-389's probe H) and, for
 * {@code systemctl}, as a synthetic refusal: what polkit prints was not measured.
 */
@Epic("Pipeline")
@Feature("The sidecars")
@Issue("816")
@Link(name = "ADR-389", url = Adr.VESPERA_USES_DOCKER_ENGINE_ALONE_AND_A_BUTTON_SHUTS_IT_DOWN, type = "adr")
class DockerShutDownTest {

    private static final Path REPOSITORY = Path.of("").toAbsolutePath();

    private static final URI DOCLING_HERE = URI.create("http://localhost:5001");

    private static final URI OLLAMA_HERE = URI.create("http://127.0.0.1:11434");

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

    /** What Docker Compose printed last, exit 1, with the daemon down (ADR-389's probe H). */
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

    private SidecarStarter starter(ScriptedDocker docker, HostSystem host) {
        return starter(docker, host, ScriptedSidecarHealth.bothAnswering());
    }

    private SidecarStarter starter(ScriptedDocker docker, HostSystem host, ScriptedSidecarHealth health) {
        Supplier<ComposeFiles> theRepositorysFiles = () -> ComposeFiles.in(REPOSITORY);
        return new SidecarStarter(settings(), docker, health, host, theRepositorysFiles, lockFolder, CHECK_EVERY);
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
