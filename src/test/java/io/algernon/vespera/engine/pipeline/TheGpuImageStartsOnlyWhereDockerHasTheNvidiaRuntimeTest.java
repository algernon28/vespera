package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ch.qos.logback.classic.Level;
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
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

/**
 * For the GPU image, once the Docker engine answers and Compose and buildx are found, and before {@code up}, Vespera asks
 * the engine for its {@code nvidia} runtime; where it is not listed, it reads the graphics cards and refuses the start in
 * one line, naming NVIDIA's toolkit and the CPU image where an NVIDIA card is there or none could be read, and saying that
 * the containers run on NVIDIA cards alone where the card is AMD's or Intel's. On macOS the GPU image is refused before
 * any command. Where the runtime is listed, the cards are asked, a driver or a card too old is warned of, and told as a
 * press of Start Docker is told its lines (ADR-437 §3), and {@code up} runs. The CPU image asks nothing of the card (ADR-444 §1 to §4).
 *
 * <p>No Docker is needed: the starter is given a {@link ScriptedDocker}, which answers the runtime check as an engine with
 * or without the toolkit answers it, and a {@link ScriptedContainerHealth}. The compose files are the repository's own.
 * Each expected line and command is written out whole.
 */
@Epic("Pipeline")
@Feature("The containers")
@Issue("860")
@Link(name = "ADR-444", url = Adr.THE_GPU_IMAGE_STARTS_ONLY_WHERE_DOCKER_HAS_THE_NVIDIA_RUNTIME, type = "adr")
class TheGpuImageStartsOnlyWhereDockerHasTheNvidiaRuntimeTest {

    /** The repository root, where Maven runs the tests from and where the compose files lie. */
    private static final Path REPOSITORY = Path.of("").toAbsolutePath();

    private static final URI DOCLING_HERE = URI.create("http://localhost:5001");

    private static final URI OLLAMA_HERE = URI.create("http://localhost:11434");

    /** How long these tests wait for anything: one second, so a wait that runs out is quick. */
    private static final Duration WAIT_AT_MOST = Duration.ofSeconds(1);

    private static final Duration CHECK_EVERY = Duration.ofMillis(20);

    /** The engine check's limit, which the runtime check and the card query are given too (ADR-444 §1, §4). */
    private static final Duration THIRTY_SECONDS = Duration.ofSeconds(30);

    /** No distribution named: WSL's default distribution is used. */
    private static final String THE_DEFAULT_DISTRIBUTION = "";

    /** The distribution an operator names, as Ubuntu 24.04 is named when WSL installs it. */
    private static final String A_NAMED_DISTRIBUTION = "Ubuntu-24.04";

    private static final List<String> IN_THE_DEFAULT_DISTRIBUTION = List.of("wsl", "-u", "root", "--exec");

    private static final List<String> IN_THE_NAMED_DISTRIBUTION =
            List.of("wsl", "-d", A_NAMED_DISTRIBUTION, "-u", "root", "--exec");

    /** The read of the display adapters on Windows, run on Windows itself and not inside WSL (ADR-444 §3). */
    private static final List<String> WINDOWS_CARDS = List.of("powershell", "-NoProfile", "-NonInteractive",
            "-Command", "Get-CimInstance Win32_VideoController | ForEach-Object PNPDeviceID");

    /** The read of the display controllers' vendors on Linux (ADR-444 §3). */
    private static final List<String> LINUX_CARDS = List.of("sh", "-c",
            "for d in /sys/bus/pci/devices/*; do case \"$(cat \"$d/class\")\" in 0x03*) cat \"$d/vendor\";; esac; done");

    /** The query of the cards, with no path inside WSL, where it is not on root's path (ADR-444 §4). */
    private static final List<String> CARD_QUERY =
            List.of("--query-gpu=name,driver_version,compute_cap", "--format=csv,noheader");

    /** An NVIDIA card and Intel's integrated one, as Windows lists them on a laptop. */
    private static final String NVIDIA_AND_INTEL_ON_WINDOWS = "PCI\\VEN_10DE&DEV_2D04&SUBSYS_205E1458&REV_A1\\4&2A3F5D3B&0&0008\r\n"
            + "PCI\\VEN_8086&DEV_A780&SUBSYS_7D981462&REV_04\\3&11583659&0&10\r\n";

    private static String cpuImage;

    private static String gpuImage;

    @TempDir
    Path lockFolder;

    private ListAppender<ILoggingEvent> logged;

    private ch.qos.logback.classic.Logger starterLogger;

    /** One start: the system, the distribution named, the client's answers, and what the start is to say. */
    record Case(String what, HostSystem host, String distribution, ScriptedDocker docker, List<String> prefix,
            List<String> cards, String line) {
    }

    @BeforeAll
    static void readTheImagesTheComposeFilesBuild() {
        try (ComposeFiles files = ComposeFiles.in(REPOSITORY)) {
            cpuImage = files.cpuImage();
            gpuImage = files.gpuImage();
        }
    }

    @BeforeEach
    void captureTheStartersLines() {
        logged = new ListAppender<>();
        logged.start();
        starterLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ContainerStarter.class);
        starterLogger.addAppender(logged);
    }

    @AfterEach
    void releaseTheStartersLines() {
        starterLogger.detachAppender(logged);
        logged.stop();
    }

    @Test
    @Story("The GPU image starts only where Docker can give the containers the card")
    @DisplayName("Where Docker has no nvidia runtime, the start on the GPU image is refused before up, in one line naming NVIDIA's toolkit and the CPU image")
    void whereTheRuntimeIsMissingTheStartIsRefusedBeforeUpNamingTheToolkitAndTheCpuImage() {
        List<Case> cases = List.of(
                new Case("on Windows, in a distribution named, an NVIDIA card and Intel's read", HostSystem.WINDOWS,
                        A_NAMED_DISTRIBUTION,
                        ScriptedDocker.engineRunning().withoutTheNvidiaRuntime()
                                .answering(WINDOWS_CARDS, 0, NVIDIA_AND_INTEL_ON_WINDOWS),
                        IN_THE_NAMED_DISTRIBUTION, WINDOWS_CARDS, runtimeMissing("in the WSL distribution Ubuntu-24.04")),
                new Case("on Windows, in the default distribution, the cards not read", HostSystem.WINDOWS,
                        THE_DEFAULT_DISTRIBUTION,
                        ScriptedDocker.engineRunning().withoutTheNvidiaRuntime()
                                .answering(WINDOWS_CARDS, 1, "Get-CimInstance : Access denied"),
                        IN_THE_DEFAULT_DISTRIBUTION, WINDOWS_CARDS, runtimeMissing("in WSL's default distribution")),
                new Case("on Linux, an NVIDIA card read", HostSystem.LINUX, THE_DEFAULT_DISTRIBUTION,
                        ScriptedDocker.engineRunning().withoutTheNvidiaRuntime().answering(LINUX_CARDS, 0, "0x10de\n"),
                        List.of(), LINUX_CARDS, runtimeMissing("on this machine")),
                new Case("on Linux, the read of the cards not started", HostSystem.LINUX, THE_DEFAULT_DISTRIBUTION,
                        ScriptedDocker.engineRunning().withoutTheNvidiaRuntime()
                                .cannotLaunch(LINUX_CARDS, "Cannot run program \"sh\": error=2, No such file or directory"),
                        List.of(), LINUX_CARDS, runtimeMissing("on this machine")));

        for (Case one : cases) {
            Throwable refused = catchThrowable(() -> starter(one, gpuImage).ensureRunning());

            claim(one.what() + ", the start on the GPU image is refused as the nvidia runtime missing, in this line,"
                            + " whole",
                    () -> assertThat(refused)
                            .isInstanceOf(NvidiaRuntimeIsMissingException.class)
                            .isInstanceOf(ContainerStartRefusedException.class)
                            .hasMessage(one.line()));
            claim("after exactly the engine check, the checks for compose and buildx, the runtime check and the read of"
                            + " the cards, and no up",
                    () -> assertThat(one.docker().commands()).containsExactly(
                            concat(one.prefix(), ScriptedDocker.ENGINE_CHECK),
                            concat(one.prefix(), ScriptedDocker.COMPOSE_CHECK),
                            concat(one.prefix(), ScriptedDocker.BUILDX_CHECK),
                            concat(one.prefix(), ScriptedDocker.RUNTIME_CHECK),
                            one.cards()));
        }
    }

    @Test
    @Story("The GPU image starts only where Docker can give the containers the card")
    @DisplayName("Where the card is AMD's or Intel's, or none is NVIDIA's, the start on the GPU image is refused in one line saying the containers run on NVIDIA cards alone")
    void aMachineWhoseCardIsNotNvidiasIsToldItRunsOnTheProcessor() {
        List<Case> cases = List.of(
                new Case("on Windows, an AMD card", HostSystem.WINDOWS, THE_DEFAULT_DISTRIBUTION,
                        ScriptedDocker.engineRunning().withoutTheNvidiaRuntime().answering(WINDOWS_CARDS, 0,
                                "PCI\\VEN_1002&DEV_744C&SUBSYS_E4711DA2&REV_C8\\6&2B5E9C0A&0&00000019\r\n"),
                        IN_THE_DEFAULT_DISTRIBUTION, WINDOWS_CARDS,
                        noNvidiaCard("this machine's graphics card is AMD's")),
                new Case("on Windows, an Intel card", HostSystem.WINDOWS, THE_DEFAULT_DISTRIBUTION,
                        ScriptedDocker.engineRunning().withoutTheNvidiaRuntime().answering(WINDOWS_CARDS, 0,
                                "pci\\ven_8086&dev_7d55&subsys_0c111028&rev_08\\3&11583659&0&10\r\n"),
                        IN_THE_DEFAULT_DISTRIBUTION, WINDOWS_CARDS,
                        noNvidiaCard("this machine's graphics card is Intel's")),
                new Case("on Windows, an AMD card and an Intel one", HostSystem.WINDOWS, THE_DEFAULT_DISTRIBUTION,
                        ScriptedDocker.engineRunning().withoutTheNvidiaRuntime().answering(WINDOWS_CARDS, 0,
                                "PCI\\VEN_8086&DEV_A780&SUBSYS_7D981462&REV_04\\3&11583659&0&10\r\n"
                                        + "PCI\\VEN_1002&DEV_744C&SUBSYS_E4711DA2&REV_C8\\6&2B5E9C0A&0&00000019\r\n"),
                        IN_THE_DEFAULT_DISTRIBUTION, WINDOWS_CARDS,
                        noNvidiaCard("this machine's graphics cards are AMD's and Intel's")),
                new Case("on Windows, the basic display adapter alone", HostSystem.WINDOWS, THE_DEFAULT_DISTRIBUTION,
                        ScriptedDocker.engineRunning().withoutTheNvidiaRuntime()
                                .answering(WINDOWS_CARDS, 0, "ROOT\\BASICDISPLAY\\0000\r\n"),
                        IN_THE_DEFAULT_DISTRIBUTION, WINDOWS_CARDS,
                        noNvidiaCard("no NVIDIA graphics card was found on this machine")),
                new Case("on Linux, an AMD card", HostSystem.LINUX, THE_DEFAULT_DISTRIBUTION,
                        ScriptedDocker.engineRunning().withoutTheNvidiaRuntime().answering(LINUX_CARDS, 0, "0x1002\n"),
                        List.of(), LINUX_CARDS, noNvidiaCard("this machine's graphics card is AMD's")),
                new Case("on Linux, a read that exits 0 naming only a vendor none of the three is, QEMU's standard VGA",
                        HostSystem.LINUX, THE_DEFAULT_DISTRIBUTION,
                        ScriptedDocker.engineRunning().withoutTheNvidiaRuntime().answering(LINUX_CARDS, 0, "0x1234\n"),
                        List.of(), LINUX_CARDS, noNvidiaCard("no NVIDIA graphics card was found on this machine")));

        for (Case one : cases) {
            Throwable refused = catchThrowable(() -> starter(one, gpuImage).ensureRunning());

            claim(one.what() + ", the start on the GPU image is refused as no NVIDIA card, in this line, whole",
                    () -> assertThat(refused)
                            .isInstanceOf(NoNvidiaCardException.class)
                            .isInstanceOf(ContainerStartRefusedException.class)
                            .hasMessage(one.line()));
            claim("and the last command run was the read of the cards: no up",
                    () -> assertThat(one.docker().commands()).last().isEqualTo(one.cards()));
        }
    }

    @Test
    @Story("The GPU image starts only where Docker can give the containers the card")
    @DisplayName("On macOS the start on the GPU image is refused before any command, saying Docker reaches no graphics card there")
    void onMacosTheGpuImageIsRefusedBeforeAnyCommand() {
        Case mac = new Case("on macOS", HostSystem.MAC, THE_DEFAULT_DISTRIBUTION, ScriptedDocker.engineRunning(),
                List.of(), List.of(), noNvidiaCard("on macOS Docker reaches no graphics card"));

        Throwable refused = catchThrowable(() -> starter(mac, gpuImage).ensureRunning());

        claim("on macOS, the start on the GPU image is refused in this line, whole",
                () -> assertThat(refused).isInstanceOf(NoNvidiaCardException.class).hasMessage(mac.line()));
        claim("and no command was run, the engine not asked", () -> assertThat(mac.docker().commands()).isEmpty());
    }

    @Test
    @Story("The GPU image starts only where Docker can give the containers the card")
    @DisplayName("Where Docker has the nvidia runtime, the cards are asked and up names both compose files")
    void whereTheRuntimeIsListedTheCardsAreAskedAndUpNamesBothFiles() {
        ScriptedContainerHealth windowsHealth = ScriptedContainerHealth.neitherAnswering();
        Case windows = new Case("on Windows, in a distribution named", HostSystem.WINDOWS, A_NAMED_DISTRIBUTION,
                ScriptedDocker.engineRunning().whenUpSucceeds(() -> windowsHealth.answering(true, true)),
                IN_THE_NAMED_DISTRIBUTION,
                concat(IN_THE_NAMED_DISTRIBUTION, concat(List.of("/usr/lib/wsl/lib/nvidia-smi"), CARD_QUERY)), "");
        ScriptedContainerHealth linuxHealth = ScriptedContainerHealth.neitherAnswering();
        Case linux = new Case("on Linux", HostSystem.LINUX, THE_DEFAULT_DISTRIBUTION,
                ScriptedDocker.engineRunning().whenUpSucceeds(() -> linuxHealth.answering(true, true)), List.of(),
                concat(List.of("nvidia-smi"), CARD_QUERY), "");

        ContainerStarter.Outcome onWindows = starter(windows, gpuImage, windowsHealth).ensureRunning();
        ContainerStarter.Outcome onLinux = starter(linux, gpuImage, linuxHealth).ensureRunning();

        claim("on Windows the containers were started", () -> assertThat(onWindows).isEqualTo(ContainerStarter.Outcome.STARTED));
        claim("after the engine check, the checks for compose and buildx, the runtime check and the query of the cards,"
                        + " each inside the distribution, and up naming compose.yaml and compose.gpu.yaml",
                () -> assertThat(windows.docker().commands()).containsExactly(
                        concat(IN_THE_NAMED_DISTRIBUTION, ScriptedDocker.ENGINE_CHECK),
                        concat(IN_THE_NAMED_DISTRIBUTION, ScriptedDocker.COMPOSE_CHECK),
                        concat(IN_THE_NAMED_DISTRIBUTION, ScriptedDocker.BUILDX_CHECK),
                        concat(IN_THE_NAMED_DISTRIBUTION, ScriptedDocker.RUNTIME_CHECK),
                        windows.cards(),
                        List.of("wsl", "-d", A_NAMED_DISTRIBUTION, "-u", "root", "--cd", REPOSITORY.toString(), "--exec",
                                "docker", "compose", "-p", "vespera", "-f", "compose.yaml", "-f", "compose.gpu.yaml",
                                "up", "-d", "--build", "--no-recreate")));
        claim("and each command before up was given the engine check's thirty seconds",
                () -> assertThat(windows.docker().limits()).containsOnly(THIRTY_SECONDS));
        claim("on Linux the containers were started", () -> assertThat(onLinux).isEqualTo(ContainerStarter.Outcome.STARTED));
        claim("after the same checks, the query of the cards by nvidia-smi on the path, and up naming both files",
                () -> assertThat(linux.docker().commands()).containsExactly(
                        ScriptedDocker.ENGINE_CHECK,
                        ScriptedDocker.COMPOSE_CHECK,
                        ScriptedDocker.BUILDX_CHECK,
                        ScriptedDocker.RUNTIME_CHECK,
                        linux.cards(),
                        List.of("docker", "compose", "-p", "vespera", "-f", REPOSITORY.resolve("compose.yaml").toString(),
                                "-f", REPOSITORY.resolve("compose.gpu.yaml").toString(), "up", "-d", "--build",
                                "--no-recreate")));
    }

    @Test
    @Story("The GPU image starts only where Docker can give the containers the card")
    @DisplayName("A driver under CUDA 12.8's floor and a card before Turing are each warned of in one line, and the start goes on")
    void anOldDriverOrACardBeforeTuringIsWarnedAndTheStartGoesOn() {
        List<String> windowsQuery = concat(IN_THE_NAMED_DISTRIBUTION,
                concat(List.of("/usr/lib/wsl/lib/nvidia-smi"), CARD_QUERY));
        List<String> linuxQuery = concat(List.of("nvidia-smi"), CARD_QUERY);
        record Warned(String what, HostSystem host, String distribution, List<String> query, String answer, int exit,
                List<String> lines) {
        }
        List<Warned> cases = List.of(
                new Warned("on Windows, a GeForce GTX 1080 on driver 552.44", HostSystem.WINDOWS, A_NAMED_DISTRIBUTION,
                        windowsQuery, "NVIDIA GeForce GTX 1080, 552.44, 6.1\n", 0, List.of(
                                "The NVIDIA driver of NVIDIA GeForce GTX 1080 is 552.44, and the GPU image's CUDA 12.8"
                                        + " needs 570.65 or later, so the containers may not start on the card: update"
                                        + " the driver, on Windows, or choose the CPU image.",
                                "NVIDIA GeForce GTX 1080 has compute capability 6.1, and the GPU image's converter needs"
                                        + " 7.5 or later, a Turing card (GeForce RTX 20 or GTX 16) or newer, so it may not"
                                        + " convert on the card: choose the CPU image if it does not.")),
                new Warned("on Linux, a Tesla V100 on driver 535.247.01", HostSystem.LINUX, THE_DEFAULT_DISTRIBUTION,
                        linuxQuery, "Tesla V100-PCIE-16GB, 535.247.01, 7.0\n", 0, List.of(
                                "The NVIDIA driver of Tesla V100-PCIE-16GB is 535.247.01, and the GPU image's CUDA 12.8"
                                        + " needs 570.26 or later, so the containers may not start on the card: update"
                                        + " the driver or choose the CPU image.",
                                "Tesla V100-PCIE-16GB has compute capability 7.0, and the GPU image's converter needs 7.5"
                                        + " or later, a Turing card (GeForce RTX 20 or GTX 16) or newer, so it may not"
                                        + " convert on the card: choose the CPU image if it does not.")),
                new Warned("on Windows, a GeForce RTX 5060 Ti on driver 576.02", HostSystem.WINDOWS,
                        A_NAMED_DISTRIBUTION, windowsQuery, "NVIDIA GeForce RTX 5060 Ti, 576.02, 12.0\n", 0, List.of()),
                new Warned("on Linux, a query that fails", HostSystem.LINUX, THE_DEFAULT_DISTRIBUTION, linuxQuery,
                        "NVIDIA-SMI has failed because it couldn't communicate with the NVIDIA driver.\n", 9, List.of()));

        for (Warned one : cases) {
            logged.list.clear();
            ScriptedContainerHealth health = ScriptedContainerHealth.neitherAnswering();
            ScriptedDocker docker = ScriptedDocker.engineRunning().answering(one.query(), one.exit(), one.answer())
                    .whenUpSucceeds(() -> health.answering(true, true));
            Case start = new Case(one.what(), one.host(), one.distribution(), docker, List.of(), one.query(), "");

            List<String> told = new ArrayList<>();

            ContainerStarter.Outcome outcome = starter(start, gpuImage, health).telling(told::add).ensureRunning();

            List<String> warnings = logged.list.stream()
                    .filter(event -> event.getLevel() == Level.WARN)
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(TheGpuImageStartsOnlyWhereDockerHasTheNvidiaRuntimeTest::isOfTheCard)
                    .toList();
            claim(one.what() + ", the starter warned in exactly these lines, whole"
                            + (one.lines().isEmpty() ? ": none" : ""),
                    () -> assertThat(warnings).containsExactlyElementsOf(one.lines()));
            claim("and told the same lines, in the same order, as a press of Start Docker shows them",
                    () -> assertThat(told.stream().filter(TheGpuImageStartsOnlyWhereDockerHasTheNvidiaRuntimeTest::isOfTheCard))
                            .containsExactlyElementsOf(one.lines()));
            claim("and the start went on: up ran after the query, and the containers were started",
                    () -> assertThat(List.of(outcome, docker.commands().get(docker.commands().size() - 2)))
                            .containsExactly(ContainerStarter.Outcome.STARTED, one.query()));
        }
    }

    @Test
    @Story("The GPU image starts only where Docker can give the containers the card")
    @DisplayName("The CPU image asks Docker nothing of the card: no runtime check and no query of the cards")
    void theCpuImageAsksNothingOfTheCard() {
        ScriptedContainerHealth health = ScriptedContainerHealth.neitherAnswering();
        Case linux = new Case("on Linux", HostSystem.LINUX, THE_DEFAULT_DISTRIBUTION,
                ScriptedDocker.engineRunning().withoutTheNvidiaRuntime().whenUpSucceeds(() -> health.answering(true, true)),
                List.of(), List.of(), "");

        starter(linux, cpuImage, health).ensureRunning();

        claim("the CPU image, the commands were the engine check, the checks for compose and buildx, and up naming"
                        + " compose.yaml alone",
                () -> assertThat(linux.docker().commands()).containsExactly(
                        ScriptedDocker.ENGINE_CHECK,
                        ScriptedDocker.COMPOSE_CHECK,
                        ScriptedDocker.BUILDX_CHECK,
                        List.of("docker", "compose", "-p", "vespera", "-f", REPOSITORY.resolve("compose.yaml").toString(),
                                "up", "-d", "--build", "--no-recreate")));
    }

    @Test
    @Story("The GPU image starts only where Docker can give the containers the card")
    @DisplayName("The control panel tells the line of the runtime missing and the line of a card that is not NVIDIA's from every other refusal")
    void theControlPanelTellsTheTwoRefusalsFromEveryOther() {
        String composeMissing = new ComposeOrBuildxIsNotInstalledException(HostSystem.LINUX, "", true, false).getMessage();
        String noFileBuildsIt = new NoComposeFileBuildsTheImageException("another/image:1", cpuImage, gpuImage).getMessage();

        claim("the line of the nvidia runtime missing offers the toolkit",
                () -> assertThat(DockerEngine.offersTheNvidiaToolkit(runtimeMissing("in WSL's default distribution")))
                        .contains(true));
        claim("the lines of a card that is not NVIDIA's, and of macOS, offer the CPU image alone",
                () -> assertThat(List.of(
                        DockerEngine.offersTheNvidiaToolkit(noNvidiaCard("this machine's graphics card is AMD's")),
                        DockerEngine.offersTheNvidiaToolkit(noNvidiaCard("on macOS Docker reaches no graphics card"))))
                        .containsExactly(Optional.of(false), Optional.of(false)));
        claim("and the lines of compose missing and of no compose file building the image are no refusal for the card",
                () -> assertThat(List.of(DockerEngine.offersTheNvidiaToolkit(composeMissing),
                        DockerEngine.offersTheNvidiaToolkit(noFileBuildsIt)))
                        .containsExactly(Optional.empty(), Optional.empty()));
    }

    /** Whether {@code line} is one of §4's warnings: each names NVIDIA's driver or a compute capability. */
    private static boolean isOfTheCard(String line) {
        return line.contains("NVIDIA") || line.contains("compute capability");
    }

    /** §2's line, for an engine {@code where}. */
    private static String runtimeMissing(String where) {
        return "The graphics card cannot be used: the Docker engine " + where + " answers but has no nvidia runtime,"
                + " which the NVIDIA Container Toolkit gives it. Install the toolkit, which Start Docker in the control"
                + " panel offers to do, or as NVIDIA's guide at"
                + " https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/latest/install-guide.html has it;"
                + " or set VESPERA_DOCLING_IMAGE to " + cpuImage + ", which converts on the processor and starts new"
                + " runs from extraction on; and run the same command again.";
    }

    /** §3's line, for {@code cards}. */
    private static String noNvidiaCard(String cards) {
        return "The graphics card cannot be used: " + cards + ", and Vespera runs the containers on NVIDIA cards alone."
                + " Set VESPERA_DOCLING_IMAGE to " + cpuImage + ", which converts on the processor and starts new runs"
                + " from extraction on, and run the same command again.";
    }

    private ContainerStarter starter(Case one, String image) {
        return starter(one, image, ScriptedContainerHealth.neitherAnswering());
    }

    private ContainerStarter starter(Case one, String image, ScriptedContainerHealth health) {
        ContainerStartSettings settings =
                new ContainerStartSettings(DOCLING_HERE, OLLAMA_HERE, image, WAIT_AT_MOST, one.distribution());
        return new ContainerStarter(settings, one.docker(), health, one.host(), () -> ComposeFiles.in(REPOSITORY),
                lockFolder, CHECK_EVERY);
    }

    private static List<String> concat(List<String> first, List<String> then) {
        List<String> whole = new ArrayList<>(first);
        whole.addAll(then);
        return Collections.unmodifiableList(whole);
    }
}
