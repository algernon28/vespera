package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

/**
 * The NVIDIA Container Toolkit's install (ADR-444 §6, §8): eight commands, each root's, in order, at the version the jar
 * pins, each given ten minutes; on Windows inside the WSL distribution through {@code wsl -u root}, fed nothing; on
 * Linux through {@code sudo -S}, each fed the password the operator typed and a line end on its standard input, and the
 * password in no command word, no logged line and no line told, and cleared once the install returns; each command
 * told before it runs and what it printed after, as a press of Start Docker is told its lines (ADR-437 §2, §3); the first
 * command that fails ends the install, in a line naming it; macOS installs nothing.
 *
 * <p>No command is run and nothing needs root: the starter is given a {@link ScriptedDocker}, which records every command,
 * its limit, and what it was fed. Each expected command and line is written out whole.
 */
@Epic("Pipeline")
@Feature("The containers")
@Issue("860")
@Link(name = "ADR-444", url = Adr.THE_GPU_IMAGE_STARTS_ONLY_WHERE_DOCKER_HAS_THE_NVIDIA_RUNTIME, type = "adr")
class TheNvidiaToolkitIsInstalledAsRootTest {

    private static final Path REPOSITORY = Path.of("").toAbsolutePath();

    private static final URI DOCLING_HERE = URI.create("http://localhost:5001");

    private static final URI OLLAMA_HERE = URI.create("http://localhost:11434");

    private static final Duration WAIT_AT_MOST = Duration.ofSeconds(1);

    private static final Duration CHECK_EVERY = Duration.ofMillis(20);

    /** Each command of the install is killed after ten minutes (ADR-444 §6). */
    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);

    private static final String A_NAMED_DISTRIBUTION = "Ubuntu-24.04";

    private static final List<String> IN_THE_NAMED_DISTRIBUTION =
            List.of("wsl", "-d", A_NAMED_DISTRIBUTION, "-u", "root", "--exec");

    private static final List<String> THROUGH_SUDO = List.of("sudo", "-S");

    /** A password with a space, a backslash and a quote, which a command line or a shell would have had to quote. */
    private static final String PASSWORD = "c0rrect horse\\batt'ry";

    /** The install's eight commands, root's, as they are run after the prefix of the system (ADR-444 §6). */
    private static final List<List<String>> THE_INSTALL = List.of(
            List.of("apt-get", "update"),
            List.of("apt-get", "install", "-y", "--no-install-recommends", "ca-certificates", "curl", "gnupg2"),
            List.of("bash", "-c", "set -euo pipefail; curl -fsSL https://nvidia.github.io/libnvidia-container/gpgkey"
                    + " | gpg --batch --yes --dearmor -o /usr/share/keyrings/nvidia-container-toolkit-keyring.gpg"),
            List.of("bash", "-c", "set -euo pipefail; curl -fsSL"
                    + " https://nvidia.github.io/libnvidia-container/stable/deb/nvidia-container-toolkit.list"
                    + " | sed 's#deb https://#deb [signed-by=/usr/share/keyrings/nvidia-container-toolkit-keyring.gpg]"
                    + " https://#g' > /etc/apt/sources.list.d/nvidia-container-toolkit.list"),
            List.of("apt-get", "update"),
            List.of("apt-get", "install", "-y", "nvidia-container-toolkit=1.20.2-1",
                    "nvidia-container-toolkit-base=1.20.2-1", "libnvidia-container-tools=1.20.2-1",
                    "libnvidia-container1=1.20.2-1"),
            List.of("nvidia-ctk", "runtime", "configure", "--runtime=docker"),
            List.of("systemctl", "restart", "docker"));

    /** The same commands as the confirmation lists them: words joined by spaces, a word holding a space quoted. */
    private static final List<String> AS_LISTED = List.of(
            "apt-get update",
            "apt-get install -y --no-install-recommends ca-certificates curl gnupg2",
            "bash -c \"set -euo pipefail; curl -fsSL https://nvidia.github.io/libnvidia-container/gpgkey | gpg --batch"
                    + " --yes --dearmor -o /usr/share/keyrings/nvidia-container-toolkit-keyring.gpg\"",
            "bash -c \"set -euo pipefail; curl -fsSL"
                    + " https://nvidia.github.io/libnvidia-container/stable/deb/nvidia-container-toolkit.list | sed"
                    + " 's#deb https://#deb [signed-by=/usr/share/keyrings/nvidia-container-toolkit-keyring.gpg]"
                    + " https://#g' > /etc/apt/sources.list.d/nvidia-container-toolkit.list\"",
            "apt-get update",
            "apt-get install -y nvidia-container-toolkit=1.20.2-1 nvidia-container-toolkit-base=1.20.2-1"
                    + " libnvidia-container-tools=1.20.2-1 libnvidia-container1=1.20.2-1",
            "nvidia-ctk runtime configure --runtime=docker",
            "systemctl restart docker");

    private static final String INSTALLED =
            "The NVIDIA Container Toolkit 1.20.2-1 is installed, and Docker was restarted with its nvidia runtime.";

    @TempDir
    Path lockFolder;

    private ListAppender<ILoggingEvent> logged;

    private Logger rootLogger;

    @BeforeEach
    void captureEveryLine() {
        logged = new ListAppender<>();
        logged.start();
        rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(logged);
    }

    @AfterEach
    void releaseEveryLine() {
        rootLogger.detachAppender(logged);
        logged.stop();
    }

    @Test
    @Story("The control panel installs NVIDIA's toolkit on the operator's confirmation")
    @DisplayName("On Windows each command of the install runs as root inside the WSL distribution, fed nothing, each given ten minutes")
    void onWindowsEachCommandRunsAsRootInsideTheDistributionWithNothingOnItsInput() {
        ScriptedDocker docker = ScriptedDocker.engineRunning().otherCommandsExiting(0);

        String line = starter(HostSystem.WINDOWS, docker).installTheNvidiaToolkit(null);

        claim("the install said it is done, in this line, whole", () -> assertThat(line).isEqualTo(INSTALLED));
        claim("the eight commands ran, in order and whole, each inside the distribution named, as root",
                () -> assertThat(docker.commands()).containsExactlyElementsOf(prefixed(IN_THE_NAMED_DISTRIBUTION)));
        claim("each was run feeding its standard input, and fed nothing",
                () -> assertThat(docker.fed()).extracting(ScriptedDocker.Fed::input)
                        .hasSize(THE_INSTALL.size()).containsOnlyNulls());
        claim("and each was given ten minutes", () -> assertThat(docker.limits())
                .hasSize(THE_INSTALL.size()).containsOnly(TEN_MINUTES));
    }

    @Test
    @Story("The control panel installs NVIDIA's toolkit on the operator's confirmation")
    @DisplayName("On Linux each command of the install runs through sudo, fed the password on its standard input and nowhere else, and the password is cleared after")
    void onLinuxEachCommandRunsThroughSudoFedThePasswordOnItsInputAndNowhereElse() {
        ScriptedDocker docker = ScriptedDocker.engineRunning().otherCommandsExiting(0);
        char[] typed = PASSWORD.toCharArray();
        List<String> told = new ArrayList<>();

        String line = starter(HostSystem.LINUX, docker).telling(told::add).installTheNvidiaToolkit(typed);

        claim("the install said it is done, in this line, whole", () -> assertThat(line).isEqualTo(INSTALLED));
        claim("the eight commands ran, in order and whole, each through sudo -S",
                () -> assertThat(docker.commands()).containsExactlyElementsOf(prefixed(THROUGH_SUDO)));
        claim("the lines told were exactly one before each command, naming it as the confirmation lists it, none of"
                        + " them printing anything, and not the done line",
                () -> assertThat(told).containsExactlyElementsOf(
                        AS_LISTED.stream().map(TheNvidiaToolkitIsInstalledAsRootTest::installing).toList()));
        claim("each was fed the password and one line end on its standard input",
                () -> assertThat(docker.fed()).extracting(ScriptedDocker.Fed::input)
                        .hasSize(THE_INSTALL.size()).containsOnly(PASSWORD + "\n"));
        claim("no word of any command holds the password, nor any part of it",
                () -> assertThat(docker.commands().stream().flatMap(List::stream))
                        .noneMatch(word -> word.contains(PASSWORD) || word.contains("c0rrect")));
        claim("no line logged while it ran holds it either",
                () -> assertThat(logged.list).extracting(ILoggingEvent::getFormattedMessage)
                        .noneMatch(said -> said.contains("c0rrect")));
        claim("nor any line told", () -> assertThat(told).noneMatch(said -> said.contains("c0rrect")));
        claim("and once the install returned, every character of the array given was cleared",
                () -> assertThat(new String(typed)).isEqualTo("\0".repeat(PASSWORD.length())));
    }

    @Test
    @Story("The control panel installs NVIDIA's toolkit on the operator's confirmation")
    @DisplayName("The first command of the install that fails ends it, in one line naming that command, its exit and its last line")
    void theFirstCommandThatFailsEndsTheInstallAndItsLineNamesIt() {
        record Failing(String what, HostSystem host, ScriptedDocker docker, int ran, String line, List<String> told) {
        }
        List<String> sudoUpdate = concat(THROUGH_SUDO, THE_INSTALL.get(0));
        List<String> sudoPrerequisites = concat(THROUGH_SUDO, THE_INSTALL.get(1));
        List<String> wslUpdate = concat(IN_THE_NAMED_DISTRIBUTION, THE_INSTALL.get(0));
        List<Failing> cases = List.of(
                new Failing("on Linux, a package apt-get cannot find", HostSystem.LINUX,
                        ScriptedDocker.engineRunning().otherCommandsExiting(0)
                                .answering(sudoPrerequisites, 100, "Reading package lists...\nE: Unable to locate package gnupg2\n"),
                        2,
                        "The NVIDIA Container Toolkit was not installed: apt-get install -y --no-install-recommends"
                                + " ca-certificates curl gnupg2 exited 100: E: Unable to locate package gnupg2. Nothing"
                                + " after it was run.",
                        List.of(installing(AS_LISTED.get(0)), installing(AS_LISTED.get(1)), "Reading package lists...",
                                "E: Unable to locate package gnupg2")),
                new Failing("on Linux, sudo refusing the password", HostSystem.LINUX,
                        ScriptedDocker.engineRunning().otherCommandsExiting(0)
                                .answering(sudoUpdate, 1, "Sorry, try again.\nsudo: 1 incorrect password attempt\n"),
                        1,
                        "The NVIDIA Container Toolkit was not installed: apt-get update exited 1: sudo: 1 incorrect"
                                + " password attempt. Nothing after it was run.",
                        List.of(installing(AS_LISTED.get(0)), "Sorry, try again.",
                                "sudo: 1 incorrect password attempt")),
                new Failing("on Windows, wsl not found", HostSystem.WINDOWS,
                        ScriptedDocker.engineRunning().otherCommandsExiting(0)
                                .cannotLaunch(wslUpdate, "Cannot run program \"wsl\": error=2, No such file or directory"),
                        1,
                        "The NVIDIA Container Toolkit was not installed: apt-get update exited 127: Cannot run program"
                                + " \"wsl\": error=2, No such file or directory. Nothing after it was run.",
                        List.of(installing(AS_LISTED.get(0)))));

        for (Failing one : cases) {
            char[] typed = PASSWORD.toCharArray();
            List<String> told = new ArrayList<>();

            String line = starter(one.host(), one.docker()).telling(told::add).installTheNvidiaToolkit(typed);

            claim(one.what() + ", the install said so in this line, whole",
                    () -> assertThat(line).isEqualTo(one.line()));
            claim("the lines told were exactly each command run, named as listed, and what it printed, blank lines"
                            + " left out; a program that could not be started printed nothing, and the line was not told",
                    () -> assertThat(told).containsExactlyElementsOf(one.told()));
            claim("and no command after the one that failed ran: " + one.ran() + " of " + THE_INSTALL.size(),
                    () -> assertThat(one.docker().commands()).hasSize(one.ran()));
            claim("and the password given was cleared all the same",
                    () -> assertThat(new String(typed)).isEqualTo("\0".repeat(PASSWORD.length())));
        }
    }

    @Test
    @Story("The control panel installs NVIDIA's toolkit on the operator's confirmation")
    @DisplayName("The commands the confirmation lists are the ones run, at the toolkit's pinned version, and macOS lists and installs nothing")
    void theCommandsListedAreTheOnesRunAtThePinnedVersionAndMacosInstallsNothing() {
        ScriptedDocker onMac = ScriptedDocker.engineRunning().otherCommandsExiting(0);

        List<String> listedOnWindows = starter(HostSystem.WINDOWS, ScriptedDocker.engineRunning()).nvidiaToolkitCommands();
        List<String> listedOnLinux = starter(HostSystem.LINUX, ScriptedDocker.engineRunning()).nvidiaToolkitCommands();
        List<String> listedOnMac = starter(HostSystem.MAC, onMac).nvidiaToolkitCommands();
        String installedOnMac = starter(HostSystem.MAC, onMac).installTheNvidiaToolkit(null);

        claim("on Windows and on Linux the confirmation lists the eight commands, whole, as root runs them, the toolkit"
                        + " pinned at 1.20.2-1, with no prefix of the system's",
                () -> assertThat(List.of(listedOnWindows, listedOnLinux)).containsOnly(AS_LISTED));
        claim("on macOS it lists none", () -> assertThat(listedOnMac).isEmpty());
        claim("and the install there says, in this line, whole, that it installs on Windows and Linux alone",
                () -> assertThat(installedOnMac)
                        .isEqualTo("Vespera installs the NVIDIA Container Toolkit on Windows and Linux alone."));
        claim("and runs no command", () -> assertThat(onMac.commands()).isEmpty());
    }

    private ContainerStarter starter(HostSystem host, ScriptedDocker docker) {
        ContainerStartSettings settings = new ContainerStartSettings(DOCLING_HERE, OLLAMA_HERE, "", WAIT_AT_MOST,
                A_NAMED_DISTRIBUTION);
        return new ContainerStarter(settings, docker, ScriptedContainerHealth.neitherAnswering(), host,
                () -> ComposeFiles.in(REPOSITORY), lockFolder, CHECK_EVERY);
    }

    /** The line told and logged before {@code command}, as the confirmation lists it, is run (ADR-444 §6). */
    private static String installing(String command) {
        return "Installing the NVIDIA Container Toolkit: " + command;
    }

    private static List<List<String>> prefixed(List<String> prefix) {
        return THE_INSTALL.stream().map(command -> concat(prefix, command)).toList();
    }

    private static List<String> concat(List<String> first, List<String> then) {
        List<String> whole = new ArrayList<>(first);
        whole.addAll(then);
        return Collections.unmodifiableList(whole);
    }
}
