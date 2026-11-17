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
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Once the Docker engine answers, and before {@code up}, Vespera asks the {@code docker} client it reaches whether it
 * has Compose and buildx, and refuses the start in one line naming what is missing, where, and the command that
 * installs it on that system (ADR-410); the control panel's start of the engine asks the same and refuses in the same
 * line (ADR-410 §2, ADR-390 §4).
 *
 * <p>No Docker is needed: the starter is given a {@link ScriptedDocker}, whose checks for Compose and buildx answer as
 * a {@code docker} client without them answers (ADR-410, Measured), and a {@link ScriptedSidecarHealth} with neither
 * sidecar answering, so the start reaches the engine. The compose files are the repository's own. Each expected line
 * and command is written out whole.
 */
@Epic("Pipeline")
@Feature("The sidecars")
@Issue("859")
@Link(name = "ADR-410", url = Adr.A_MISSING_DOCKER_COMPOSE_OR_BUILDX_IS_NAMED_BEFORE_UP, type = "adr")
class AMissingComposeOrBuildxIsNamedBeforeUpTest {

    /** The repository root, where Maven runs the tests from and where the compose files lie. */
    private static final Path REPOSITORY = Path.of("").toAbsolutePath();

    private static final URI DOCLING_HERE = URI.create("http://localhost:5001");

    private static final URI OLLAMA_HERE = URI.create("http://localhost:11434");

    /** How long these tests wait for anything: one second, so a wait that runs out is quick. */
    private static final Duration WAIT_AT_MOST = Duration.ofSeconds(1);

    private static final Duration CHECK_EVERY = Duration.ofMillis(20);

    /** No distribution named: WSL's default distribution is used. */
    private static final String THE_DEFAULT_DISTRIBUTION = "";

    /** The distribution an operator names, as Ubuntu 24.04 is named when WSL installs it. */
    private static final String A_NAMED_DISTRIBUTION = "Ubuntu-24.04";

    /** Run inside WSL's default distribution, as root, with no shell between (ADR-390 §1). */
    private static final List<String> IN_THE_DEFAULT_DISTRIBUTION = List.of("wsl", "-u", "root", "--exec");

    /** Run inside {@link #A_NAMED_DISTRIBUTION}. */
    private static final List<String> IN_THE_NAMED_DISTRIBUTION =
            List.of("wsl", "-d", A_NAMED_DISTRIBUTION, "-u", "root", "--exec");

    /** What a command killed at its limit exits with (ADR-323 §5). */
    private static final int KILLED = -1;

    /** The Docling image {@code compose.yaml} builds, so that the start reaches the engine. */
    private static String cpuImage;

    @TempDir
    Path lockFolder;

    /** One start: the system, the distribution named, the client's answers, and what the start is to do. */
    record Case(String what, HostSystem host, String distribution, Supplier<ScriptedDocker> docker,
            List<String> prefix, String line) {
    }

    @BeforeAll
    static void readTheImageComposeYamlBuilds() {
        try (ComposeFiles files = ComposeFiles.in(REPOSITORY)) {
            cpuImage = files.cpuImage();
        }
    }

    @Test
    @Story("A missing docker compose or buildx is named before the sidecars are started")
    @DisplayName("Where compose or buildx is missing, the start of the sidecars is refused after both checks and before up, in one line naming what is missing, where, and the command that installs it on that system")
    void aMissingComposeOrBuildxRefusesTheStartBeforeUpInOneLineSayingHowToInstallIt() {
        List<Case> cases = List.of(
                new Case("on Windows, in a distribution named, with neither", HostSystem.WINDOWS, A_NAMED_DISTRIBUTION,
                        () -> ScriptedDocker.engineRunning().withoutCompose().withoutBuildx(),
                        IN_THE_NAMED_DISTRIBUTION,
                        "Vespera cannot start the sidecars: the Docker engine answers, but docker compose and docker"
                                + " buildx are not installed in the WSL distribution Ubuntu-24.04. Install them with"
                                + " wsl -d Ubuntu-24.04 -u root --exec apt-get install -y docker-compose-v2"
                                + " docker-buildx, or docker-compose-plugin docker-buildx-plugin where Docker Engine"
                                + " came from Docker's own repository, and run the same command again."),
                new Case("on Windows, in the default distribution, without compose", HostSystem.WINDOWS,
                        THE_DEFAULT_DISTRIBUTION, () -> ScriptedDocker.engineRunning().withoutCompose(),
                        IN_THE_DEFAULT_DISTRIBUTION,
                        "Vespera cannot start the sidecars: the Docker engine answers, but docker compose is not"
                                + " installed in WSL's default distribution. Install it with wsl -u root --exec"
                                + " apt-get install -y docker-compose-v2, or docker-compose-plugin where Docker Engine"
                                + " came from Docker's own repository, and run the same command again."),
                new Case("on Linux, a WSL distribution named changing nothing, without buildx", HostSystem.LINUX,
                        A_NAMED_DISTRIBUTION,
                        () -> ScriptedDocker.engineRunning().withoutBuildx(), List.of(),
                        "Vespera cannot start the sidecars: the Docker engine answers, but docker buildx is not"
                                + " installed on this machine. Install it with sudo apt-get install -y docker-buildx,"
                                + " or docker-buildx-plugin where Docker Engine came from Docker's own repository, and"
                                + " run the same command again."),
                new Case("on Linux, with neither", HostSystem.LINUX, THE_DEFAULT_DISTRIBUTION,
                        () -> ScriptedDocker.engineRunning().withoutCompose().withoutBuildx(), List.of(),
                        "Vespera cannot start the sidecars: the Docker engine answers, but docker compose and docker"
                                + " buildx are not installed on this machine. Install them with sudo apt-get install"
                                + " -y docker-compose-v2 docker-buildx, or docker-compose-plugin docker-buildx-plugin"
                                + " where Docker Engine came from Docker's own repository, and run the same command"
                                + " again."),
                new Case("on macOS, with neither", HostSystem.MAC, THE_DEFAULT_DISTRIBUTION,
                        () -> ScriptedDocker.engineRunning().withoutCompose().withoutBuildx(), List.of(),
                        "Vespera cannot start the sidecars: the Docker engine answers, but docker compose and docker"
                                + " buildx are not installed on this machine. Install them with brew install"
                                + " docker-compose docker-buildx, add $HOMEBREW_PREFIX/lib/docker/cli-plugins to"
                                + " cliPluginsExtraDirs in ~/.docker/config.json, and run the same command again."),
                new Case("on macOS, without compose", HostSystem.MAC, THE_DEFAULT_DISTRIBUTION,
                        () -> ScriptedDocker.engineRunning().withoutCompose(), List.of(),
                        "Vespera cannot start the sidecars: the Docker engine answers, but docker compose is not"
                                + " installed on this machine. Install it with brew install docker-compose, add"
                                + " $HOMEBREW_PREFIX/lib/docker/cli-plugins to cliPluginsExtraDirs in"
                                + " ~/.docker/config.json, and run the same command again."),
                new Case("on Linux, with the check for compose killed at its limit", HostSystem.LINUX,
                        THE_DEFAULT_DISTRIBUTION,
                        () -> ScriptedDocker.engineRunning().answering(ScriptedDocker.COMPOSE_CHECK, KILLED, ""),
                        List.of(),
                        "Vespera cannot start the sidecars: the Docker engine answers, but docker compose is not"
                                + " installed on this machine. Install it with sudo apt-get install -y"
                                + " docker-compose-v2, or docker-compose-plugin where Docker Engine came from Docker's"
                                + " own repository, and run the same command again."));

        for (Case one : cases) {
            ScriptedDocker docker = one.docker().get();

            Throwable refused = catchThrowable(() -> starter(one, docker).ensureRunning());

            claim(one.what() + ", the start of the sidecars is refused, in this line, whole",
                    () -> assertThat(refused)
                            .isInstanceOf(SidecarStartRefusedException.class)
                            .hasMessage(one.line()));
            claim("and the commands run were, in order and whole, the engine check, the check for compose and the"
                            + " check for buildx, both asked whatever the first answered, and no up",
                    () -> assertThat(docker.commands()).containsExactly(
                            concat(one.prefix(), ScriptedDocker.ENGINE_CHECK),
                            concat(one.prefix(), ScriptedDocker.COMPOSE_CHECK),
                            concat(one.prefix(), ScriptedDocker.BUILDX_CHECK)));
        }
    }

    @Test
    @Story("A missing docker compose or buildx is named before the sidecars are started")
    @DisplayName("The control panel's start of the engine asks for compose and buildx too, and refuses in the same line")
    void theControlPanelsStartRefusesInTheSameLine() {
        Case windows = new Case("on Windows", HostSystem.WINDOWS, A_NAMED_DISTRIBUTION,
                () -> ScriptedDocker.engineRunning().withoutCompose().withoutBuildx(), IN_THE_NAMED_DISTRIBUTION,
                "Vespera cannot start the sidecars: the Docker engine answers, but docker compose and docker buildx are"
                        + " not installed in the WSL distribution Ubuntu-24.04. Install them with wsl -d Ubuntu-24.04"
                        + " -u root --exec apt-get install -y docker-compose-v2 docker-buildx, or"
                        + " docker-compose-plugin docker-buildx-plugin where Docker Engine came from Docker's own"
                        + " repository, and run the same command again.");
        ScriptedDocker docker = windows.docker().get();

        Throwable refused = catchThrowable(() -> starter(windows, docker).ensureTheEngineRuns());

        claim("on Windows, with neither in the distribution named, the control panel's start of the engine is refused"
                        + " in the line the start of the sidecars gives, whole",
                () -> assertThat(refused)
                        .isInstanceOf(SidecarStartRefusedException.class)
                        .hasMessage(windows.line()));
        claim("after the engine check, the check for compose and the check for buildx, each inside that distribution",
                () -> assertThat(docker.commands()).containsExactly(
                        concat(IN_THE_NAMED_DISTRIBUTION, ScriptedDocker.ENGINE_CHECK),
                        concat(IN_THE_NAMED_DISTRIBUTION, ScriptedDocker.COMPOSE_CHECK),
                        concat(IN_THE_NAMED_DISTRIBUTION, ScriptedDocker.BUILDX_CHECK)));
    }

    @Test
    @Story("A missing docker compose or buildx is named before the sidecars are started")
    @DisplayName("A check whose output file cannot be written refuses the start in that file's line, and nothing is run after it")
    void aCheckWhoseOutputFileFailsRefusesInThatFilesLineAndRunsNothingAfter() {
        Path folder = lockFolder.resolve("temporary-files");
        ScriptedDocker docker = ScriptedDocker.engineRunning()
                .outputFileFails(ScriptedDocker.COMPOSE_CHECK, folder, "No space left on device");
        Case linux = new Case("on Linux", HostSystem.LINUX, THE_DEFAULT_DISTRIBUTION, () -> docker, List.of(), "");

        Throwable refused = catchThrowable(() -> starter(linux, docker).ensureRunning());

        claim("the check for compose, its output file failing, refuses the start as a temporary file that cannot be"
                        + " written or read, naming that check, and not as compose missing",
                () -> assertThat(refused)
                        .isInstanceOf(CommandOutputFileUnusableException.class)
                        .hasMessage("the sidecars are not answering and Vespera cannot start them: the temporary file"
                                + " the output of docker compose version goes to cannot be written or read (No space"
                                + " left on device). Make room in " + folder.toAbsolutePath() + ", or make it"
                                + " writable, and run the same command again."));
        claim("and the commands run were the engine check and the check for compose: no check for buildx and no up",
                () -> assertThat(docker.commands()).containsExactly(
                        ScriptedDocker.ENGINE_CHECK, ScriptedDocker.COMPOSE_CHECK));
    }

    @Test
    @Story("A missing docker compose or buildx is named before the sidecars are started")
    @DisplayName("A check whose program cannot be started refuses the start as Docker not installed, naming the engine check's program, and nothing is run after it")
    void aCheckWhoseProgramCannotBeStartedIsDockerNotInstalled() {
        List<Case> cases = List.of(
                new Case("on Linux", HostSystem.LINUX, THE_DEFAULT_DISTRIBUTION, ScriptedDocker::engineRunning,
                        List.of(), ""),
                new Case("on Windows", HostSystem.WINDOWS, THE_DEFAULT_DISTRIBUTION, ScriptedDocker::engineRunning,
                        IN_THE_DEFAULT_DISTRIBUTION, ""));

        for (Case one : cases) {
            List<String> composeCheck = concat(one.prefix(), ScriptedDocker.COMPOSE_CHECK);
            String program = concat(one.prefix(), ScriptedDocker.ENGINE_CHECK).getFirst();
            ScriptedDocker docker = one.docker().get().cannotLaunch(composeCheck, "Cannot run program \"" + program
                    + "\": error=2, No such file or directory");

            Throwable refused = catchThrowable(() -> starter(one, docker).ensureRunning());

            claim(one.what() + ", the check for compose, its program not starting, refuses the start as Docker not"
                            + " installed, in the line naming " + program + ", whole, and not as compose missing",
                    () -> assertThat(refused)
                            .isInstanceOf(DockerIsNotInstalledException.class)
                            .hasMessage("Vespera cannot start Docker: no " + program + " command was found. Install"
                                    + " Docker Engine -- in a WSL 2 distribution on Windows, with Colima on macOS, as"
                                    + " the docker service on Linux -- and run the same command again."));
            claim("and the commands run were the engine check and the check for compose: no check for buildx and no up",
                    () -> assertThat(docker.commands()).containsExactly(
                            concat(one.prefix(), ScriptedDocker.ENGINE_CHECK), composeCheck));
        }
    }

    private SidecarStarter starter(Case one, ScriptedDocker docker) {
        SidecarStartSettings settings =
                new SidecarStartSettings(DOCLING_HERE, OLLAMA_HERE, cpuImage, WAIT_AT_MOST, one.distribution());
        return new SidecarStarter(settings, docker, ScriptedSidecarHealth.neitherAnswering(), one.host(),
                () -> ComposeFiles.in(REPOSITORY), lockFolder, CHECK_EVERY);
    }

    private static List<String> concat(List<String> first, List<String> then) {
        List<String> whole = new ArrayList<>(first);
        whole.addAll(then);
        return List.copyOf(whole);
    }
}
