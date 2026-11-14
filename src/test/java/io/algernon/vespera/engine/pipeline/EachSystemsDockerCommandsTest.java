package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The commands Vespera runs on each system to check, start and stop the Docker engine, to stop the sidecars and to
 * bring them up (ADR-389 §1 to §3, ADR-323 §4 and §5): Docker Engine alone, the {@code docker} service on Linux, Docker
 * Engine inside a WSL 2 distribution on Windows, reached through {@code wsl}, and Colima on macOS; one start command
 * on each, and none of Docker Desktop.
 *
 * <p>Each command is written out whole, so a second start command, a Desktop start, an {@code open} or a {@code down}
 * fails the claim that lists what the system gives. What {@code wsl}, Colima and systemd do with them no unit test
 * shows.
 */
@Epic("Pipeline")
@Feature("The sidecars")
@Issue("816")
@Link(name = "ADR-389", url = Adr.VESPERA_USES_DOCKER_ENGINE_ALONE_AND_A_BUTTON_SHUTS_IT_DOWN, type = "adr")
@Link(name = "ADR-323", url = Adr.VESPERA_STARTS_DOCKER_AND_THE_SIDECARS_AND_STAGE_2_WAITS_FOR_A_STARTING_CONVERTER, type = "adr")
class EachSystemsDockerCommandsTest {

    /** No distribution named: WSL's default distribution is used. */
    private static final String THE_DEFAULT_DISTRIBUTION = "";

    /** A distribution an operator names. */
    private static final String A_NAMED_DISTRIBUTION = "Ubuntu-24.04";

    private static final List<String> ENGINE_CHECK = List.of("docker", "version", "--format", "{{.Server.Version}}");

    private static final List<String> COMPOSE_STOP = List.of("docker", "compose", "-p", "vespera", "stop");

    /** Run inside WSL's default distribution, as root, with no shell between. */
    private static final List<String> IN_THE_DEFAULT_DISTRIBUTION = List.of("wsl", "-u", "root", "--exec");

    /** The folder the compose files are written out to, absolute on whatever system the test runs on. */
    private static final Path FOLDER = Path.of("").toAbsolutePath().resolve("vespera-sidecars-written-out");

    private static final Path COMPOSE_FILE = FOLDER.resolve("compose.yaml");

    private static final Path GPU_FILE = FOLDER.resolve("compose.gpu.yaml");

    private static final List<String> UP_FLAGS = List.of("up", "-d", "--build", "--no-recreate");

    /** Words no command of any system holds: Docker Desktop's, the openers', and a removal of the containers. */
    private static final List<String> NEVER_NAMED = List.of("desktop", "Docker Desktop.exe", "cmd", "open", "down");

    /** One system's four commands of the engine and the sidecars' stop. */
    record Commands(List<String> check, List<String> start, List<String> composeStop, List<String> stop) {
    }

    @Test
    @Story("Vespera uses Docker Engine alone")
    @DisplayName("Each system checks, starts and stops its engine with one command each, and no command is Docker Desktop's")
    void eachSystemStartsAndStopsItsEngineWithOneCommandAndNoneIsDockerDesktops() {
        Map<HostSystem, Commands> expected = new LinkedHashMap<>();
        expected.put(HostSystem.LINUX, new Commands(
                ENGINE_CHECK,
                List.of("systemctl", "--no-ask-password", "start", "docker"),
                COMPOSE_STOP,
                List.of("systemctl", "--no-ask-password", "stop", "docker.socket", "docker.service")));
        expected.put(HostSystem.WINDOWS, new Commands(
                inTheDefaultDistribution(ENGINE_CHECK),
                inTheDefaultDistribution(List.of("systemctl", "start", "docker")),
                inTheDefaultDistribution(COMPOSE_STOP),
                inTheDefaultDistribution(List.of("systemctl", "stop", "docker.socket", "docker.service"))));
        expected.put(HostSystem.MAC, new Commands(
                ENGINE_CHECK,
                List.of("colima", "start"),
                COMPOSE_STOP,
                List.of("colima", "stop")));

        for (Map.Entry<HostSystem, Commands> system : expected.entrySet()) {
            HostSystem host = system.getKey();
            claim("on " + host + ", the engine check, the engine's one start, the sidecars' stop and the engine's stop"
                            + " are, whole, the ones listed for it",
                    () -> assertThat(new Commands(
                                    host.engineCheck(THE_DEFAULT_DISTRIBUTION),
                                    host.engineStart(THE_DEFAULT_DISTRIBUTION),
                                    host.composeStop(THE_DEFAULT_DISTRIBUTION),
                                    host.engineStop(THE_DEFAULT_DISTRIBUTION)))
                            .isEqualTo(system.getValue()));
        }

        List<String> everyWord = new ArrayList<>();
        for (HostSystem host : HostSystem.values()) {
            for (String distribution : List.of(THE_DEFAULT_DISTRIBUTION, A_NAMED_DISTRIBUTION)) {
                everyWord.addAll(host.engineCheck(distribution));
                everyWord.addAll(host.engineStart(distribution));
                everyWord.addAll(host.composeStop(distribution));
                everyWord.addAll(host.engineStop(distribution));
                // The folder's path is the test machine's, and could hold any word.
                host.composeUp(distribution, List.of(COMPOSE_FILE, GPU_FILE)).stream()
                        .filter(word -> !word.contains(FOLDER.toString()))
                        .forEach(everyWord::add);
            }
        }
        claim("no word of any command of any system, with or without a distribution named, but the compose files'"
                        + " folder, is or holds desktop,"
                        + " Docker Desktop.exe, cmd, open or down",
                () -> assertThat(everyWord).isNotEmpty().noneMatch(word -> NEVER_NAMED.stream().anyMatch(word::contains)));

        claim("Darwin and Mac OS X are macOS, Windows 11 is Windows, and Linux is Linux",
                () -> assertThat(List.of(HostSystem.named("Darwin"), HostSystem.named("Mac OS X"),
                                HostSystem.named("Windows 11"), HostSystem.named("Linux")))
                        .containsExactly(HostSystem.MAC, HostSystem.MAC, HostSystem.WINDOWS, HostSystem.LINUX));
    }

    @Test
    @Story("Vespera uses Docker Engine alone")
    @DisplayName("On Windows every docker command runs inside the WSL distribution named, or the default one, and up is given its folder")
    void onWindowsEveryDockerCommandRunsInsideTheNamedWslDistribution() {
        List<String> inTheNamedOne = List.of("wsl", "-d", A_NAMED_DISTRIBUTION, "-u", "root", "--exec");

        claim("with a distribution named, the engine check and the sidecars' stop run in it, as root, with no shell"
                        + " between",
                () -> assertThat(List.of(
                                HostSystem.WINDOWS.engineCheck(A_NAMED_DISTRIBUTION),
                                HostSystem.WINDOWS.composeStop(A_NAMED_DISTRIBUTION)))
                        .containsExactly(concat(inTheNamedOne, ENGINE_CHECK), concat(inTheNamedOne, COMPOSE_STOP)));
        claim("and up, with both files, is given the folder they lie in and names them by their names, compose.yaml"
                        + " first",
                () -> assertThat(HostSystem.WINDOWS.composeUp(A_NAMED_DISTRIBUTION, List.of(COMPOSE_FILE, GPU_FILE)))
                        .containsExactlyElementsOf(concat(
                                List.of("wsl", "-d", A_NAMED_DISTRIBUTION, "-u", "root", "--cd", FOLDER.toString(),
                                        "--exec", "docker", "compose", "-p", "vespera",
                                        "-f", "compose.yaml", "-f", "compose.gpu.yaml"),
                                UP_FLAGS)));
        claim("with none named, up with compose.yaml alone runs in the default distribution, passing no -d",
                () -> assertThat(HostSystem.WINDOWS.composeUp(THE_DEFAULT_DISTRIBUTION, List.of(COMPOSE_FILE)))
                        .containsExactlyElementsOf(concat(
                                List.of("wsl", "-u", "root", "--cd", FOLDER.toString(), "--exec",
                                        "docker", "compose", "-p", "vespera", "-f", "compose.yaml"),
                                UP_FLAGS)));

        List<String> upAsItIs = concat(
                List.of("docker", "compose", "-p", "vespera", "-f", COMPOSE_FILE.toString(), "-f", GPU_FILE.toString()),
                UP_FLAGS);
        for (HostSystem host : List.of(HostSystem.LINUX, HostSystem.MAC)) {
            claim("on " + host + ", a distribution named changes nothing: the engine check is run as it is, and up"
                            + " names each file by its path",
                    () -> assertThat(List.of(
                                    host.engineCheck(A_NAMED_DISTRIBUTION),
                                    host.composeUp(A_NAMED_DISTRIBUTION, List.of(COMPOSE_FILE, GPU_FILE))))
                            .containsExactly(ENGINE_CHECK, upAsItIs));
        }
    }

    private static List<String> inTheDefaultDistribution(List<String> command) {
        return concat(IN_THE_DEFAULT_DISTRIBUTION, command);
    }

    private static List<String> concat(List<String> first, List<String> then) {
        List<String> whole = new ArrayList<>(first);
        whole.addAll(then);
        return List.copyOf(whole);
    }
}
