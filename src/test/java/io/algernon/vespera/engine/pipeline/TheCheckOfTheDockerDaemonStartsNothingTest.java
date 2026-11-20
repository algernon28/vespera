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
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The control panel's check of the Docker daemon starts nothing (ADR-430 §3): on Windows no command is run into a
 * distribution that is not running, which would boot it, so the running ones are listed first, and WSL's default is the
 * one {@code wsl --list --verbose} marks with {@code *}; on Linux no {@code docker} command meets a socket whose service
 * is stopped, which would start it, so the service is asked first; on macOS the engine check is asked as it is. Where
 * neither container is on this machine the daemon is not asked at all, and the check takes no lock.
 *
 * <p>No Docker is needed and no name is resolved: the starter is given a {@link ScriptedDocker}, whose every command not
 * scripted exits 1, and a {@link ThisMachine} of the test's. {@code wsl.exe} writes its lists in UTF-16, so the lists
 * the test scripts are given as a reader of single bytes sees them, a NUL after each character.
 */
@Epic("Pipeline")
@Feature("The containers")
@Issue("889")
@Link(name = "ADR-430", url = Adr.THE_CONTROL_PANEL_CHECKS_DOCKER_AND_STARTS_NOTHING_UNTIL_START_DOCKER, type = "adr")
class TheCheckOfTheDockerDaemonStartsNothingTest {

    private static final URI DOCLING_HERE = URI.create("http://localhost:5001");

    private static final URI OLLAMA_HERE = URI.create("http://localhost:11434");

    private static final String DISTRIBUTION = "Ubuntu-24.04";

    private static final List<String> RUNNING = List.of("wsl", "--list", "--running", "--quiet");

    private static final List<String> VERBOSE = List.of("wsl", "--list", "--verbose");

    private static final List<String> SERVICE_ACTIVE = List.of("systemctl", "is-active", "--quiet", "docker.service");

    /** WSL's line where no distribution runs. */
    private static final String NONE_RUNNING = "There are no running distributions.\n";

    @TempDir
    Path lockFolder;

    @Test
    @Story("The control panel checks Docker and starts nothing as it opens")
    @DisplayName("On Windows a named distribution that is not running is not asked, so no command boots it, and one that runs is asked inside it")
    void onWindowsADistributionNotRunningIsNotAskedSoWslBootsNothing() {
        ScriptedDocker stopped = ScriptedDocker.engineRunning().answering(RUNNING, 1, NONE_RUNNING);
        ScriptedDocker another = ScriptedDocker.engineRunning().answering(RUNNING, 0, asUtf16("Debian\r\n"));
        ScriptedDocker running = ScriptedDocker.engineRunning()
                .answering(RUNNING, 0, asUtf16("Debian\r\n" + DISTRIBUTION + "\r\n"));

        Optional<Boolean> whenStopped = starter(stopped, HostSystem.WINDOWS, DISTRIBUTION).engineAnswersStartingNothing();
        Optional<Boolean> whenAnotherRuns =
                starter(another, HostSystem.WINDOWS, DISTRIBUTION).engineAnswersStartingNothing();
        Optional<Boolean> whenItRuns = starter(running, HostSystem.WINDOWS, DISTRIBUTION).engineAnswersStartingNothing();

        claim("no distribution running, the daemon does not answer, and the one command run lists the running ones",
                () -> assertThat(List.of(whenStopped, stopped.commands()))
                        .containsExactly(Optional.of(false), List.of(RUNNING)));
        claim("another running and not " + DISTRIBUTION + ", the same: nothing is run into " + DISTRIBUTION,
                () -> assertThat(List.of(whenAnotherRuns, another.commands()))
                        .containsExactly(Optional.of(false), List.of(RUNNING)));
        claim(DISTRIBUTION + " running, read through the NULs of WSL's list, the engine check is run inside it and the"
                        + " daemon answers",
                () -> assertThat(List.of(whenItRuns, running.commands())).containsExactly(Optional.of(true), List.of(
                        RUNNING, inWsl(List.of("-d", DISTRIBUTION), ScriptedDocker.ENGINE_CHECK))));
    }

    @Test
    @Story("The control panel checks Docker and starts nothing as it opens")
    @DisplayName("On Windows with no distribution named, the default is the one WSL marks with a star, and it is asked only while it runs")
    void onWindowsTheDefaultDistributionIsTheOneMarkedAndIsAskedOnlyWhileItRuns() {
        String verbose = asUtf16("  NAME            STATE           VERSION\r\n* Ubuntu          Stopped         2\r\n"
                + "  Debian          Running         2\r\n");
        ScriptedDocker defaultStopped = ScriptedDocker.engineRunning().answering(VERBOSE, 0, verbose)
                .answering(RUNNING, 0, asUtf16("Debian\r\n"));
        ScriptedDocker defaultRunning = ScriptedDocker.engineRunning().answering(VERBOSE, 0, verbose)
                .answering(RUNNING, 0, asUtf16("Debian\r\nUbuntu\r\n"));

        Optional<Boolean> stopped = starter(defaultStopped, HostSystem.WINDOWS, "").engineAnswersStartingNothing();
        Optional<Boolean> running = starter(defaultRunning, HostSystem.WINDOWS, "").engineAnswersStartingNothing();

        claim("the default, Ubuntu, not running while Debian runs: the daemon does not answer, and only the two lists"
                        + " were run",
                () -> assertThat(List.of(stopped, defaultStopped.commands()))
                        .containsExactly(Optional.of(false), List.of(VERBOSE, RUNNING)));
        claim("Ubuntu running: the engine check is run in WSL's default, naming no distribution, and the daemon"
                        + " answers",
                () -> assertThat(List.of(running, defaultRunning.commands())).containsExactly(Optional.of(true),
                        List.of(VERBOSE, RUNNING, inWsl(List.of(), ScriptedDocker.ENGINE_CHECK))));
    }

    @Test
    @Story("The control panel checks Docker and starts nothing as it opens")
    @DisplayName("On Linux the engine is asked only while its service is active, on macOS it is asked as it is, and nothing is started on either")
    void onLinuxTheEngineIsAskedOnlyWhileItsServiceIsActiveAndOnMacosAsItIs() {
        ScriptedDocker inactive = ScriptedDocker.engineRunning().answering(SERVICE_ACTIVE, 3, "");
        ScriptedDocker active = ScriptedDocker.engineRunning().answering(SERVICE_ACTIVE, 0, "");
        ScriptedDocker colimaStopped = ScriptedDocker.engineAnsweringAfter(ScriptedDocker.NEVER);

        Optional<Boolean> whenInactive = starter(inactive, HostSystem.LINUX, "").engineAnswersStartingNothing();
        Optional<Boolean> whenActive = starter(active, HostSystem.LINUX, "").engineAnswersStartingNothing();
        Optional<Boolean> onMac = starter(colimaStopped, HostSystem.MAC, "").engineAnswersStartingNothing();

        claim("on Linux, docker.service inactive: the daemon does not answer, and no docker command was run",
                () -> assertThat(List.of(whenInactive, inactive.commands()))
                        .containsExactly(Optional.of(false), List.of(SERVICE_ACTIVE)));
        claim("docker.service active: the engine check is run, and the daemon answers",
                () -> assertThat(List.of(whenActive, active.commands()))
                        .containsExactly(Optional.of(true), List.of(SERVICE_ACTIVE, ScriptedDocker.ENGINE_CHECK)));
        claim("on macOS, Colima stopped: the engine check alone, and the daemon does not answer",
                () -> assertThat(List.of(onMac, colimaStopped.commands()))
                        .containsExactly(Optional.of(false), List.of(ScriptedDocker.ENGINE_CHECK)));
    }

    @Test
    @Story("The control panel checks Docker and starts nothing as it opens")
    @DisplayName("With neither container on this machine the daemon is not asked, and the check takes no lock")
    void neitherServiceHereAsksNothingAndTheCheckTakesNoLock() throws IOException {
        ScriptedDocker elsewhere = ScriptedDocker.engineRunning();
        ScriptedDocker here = ScriptedDocker.engineRunning();
        Path notAFolder = Files.createFile(lockFolder.resolve("a-file-where-the-lock-folder-would-be"));

        Optional<Boolean> notAsked = new ContainerStarter(new ContainerStartSettings(URI.create("http://gpu-box.lan:5001"),
                URI.create("http://gpu-box.lan:11434"), "", Duration.ofSeconds(1)), elsewhere,
                ScriptedContainerHealth.neitherAnswering(), HostSystem.MAC, () -> {
                    throw new AssertionError("the check reads no compose file");
                }, lockFolder, Duration.ofMillis(20), aMachine()).engineAnswersStartingNothing();
        Optional<Boolean> withNoLock = new ContainerStarter(new ContainerStartSettings(DOCLING_HERE, OLLAMA_HERE, "",
                Duration.ofSeconds(1)), here, ScriptedContainerHealth.neitherAnswering(), HostSystem.MAC, () -> {
                    throw new AssertionError("the check reads no compose file");
                }, notAFolder, Duration.ofMillis(20), aMachine()).engineAnswersStartingNothing();

        claim("both containers on another machine: the daemon is not asked, and no command is run",
                () -> assertThat(List.of(notAsked, elsewhere.commands())).containsExactly(Optional.empty(), List.of()));
        claim("both here, with a lock folder that is a file: the daemon is asked and answers, the container lock never"
                        + " taken", () -> assertThat(withNoLock).isEqualTo(Optional.of(true)));
    }

    private ContainerStarter starter(ScriptedDocker docker, HostSystem host, String distribution) {
        return new ContainerStarter(
                new ContainerStartSettings(DOCLING_HERE, OLLAMA_HERE, "", Duration.ofSeconds(1), distribution),
                docker, ScriptedContainerHealth.neitherAnswering(), host, () -> {
                    throw new AssertionError("the check reads no compose file");
                }, lockFolder, Duration.ofMillis(20), aMachine());
    }

    /** A machine where localhost is a loopback address and gpu-box.lan another machine's. */
    private static ThisMachine aMachine() {
        return new ThisMachine(name -> switch (name) {
            case "localhost" -> new InetAddress[] {InetAddress.ofLiteral("127.0.0.1")};
            case "gpu-box.lan" -> new InetAddress[] {InetAddress.ofLiteral("192.168.1.30")};
            default -> throw new UnknownHostException(name);
        }, () -> List.of(InetAddress.ofLiteral("192.168.1.20")));
    }

    /** {@code command} run inside WSL as root, {@code distribution} being {@code -d <name>} or nothing. */
    private static List<String> inWsl(List<String> distribution, List<String> command) {
        List<String> whole = new ArrayList<>(List.of("wsl"));
        whole.addAll(distribution);
        whole.addAll(List.of("-u", "root", "--exec"));
        whole.addAll(command);
        return List.copyOf(whole);
    }

    /** {@code text} as a reader of single bytes sees what {@code wsl.exe} writes in UTF-16: a NUL after each character. */
    private static String asUtf16(String text) {
        StringBuilder read = new StringBuilder();
        text.chars().forEach(each -> read.append((char) each).append('\u0000'));
        return read.toString();
    }
}
