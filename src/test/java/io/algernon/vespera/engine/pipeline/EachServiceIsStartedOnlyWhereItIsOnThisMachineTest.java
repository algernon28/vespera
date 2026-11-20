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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

/**
 * Each container is judged on its own, and Vespera starts, asks and waits for only the ones on this machine (ADR-430 §10,
 * amending ADR-323 §3 and §4 and ADR-421 §1): one here and one on another machine, the one here is asked, brought up by
 * an {@code up} naming its service, and waited for, and the other is asked nothing; a name that resolves to this
 * machine's own address is started as {@code localhost} is; and on Windows {@code .wslconfig} is seen to where one
 * container is here and not where none is.
 *
 * <p>No Docker is needed and no name is resolved: the starter is given a {@link ScriptedDocker}, a
 * {@link ScriptedContainerHealth} and a {@link ThisMachine} whose resolver is the test's. The compose files are the
 * repository's own, as {@code ContainerStartTest} reads them.
 */
@Epic("Pipeline")
@Feature("The containers")
@Issue("889")
@Link(name = "ADR-430", url = Adr.THE_CONTROL_PANEL_CHECKS_DOCKER_AND_STARTS_NOTHING_UNTIL_START_DOCKER, type = "adr")
@Link(name = "ADR-323", url = Adr.VESPERA_STARTS_DOCKER_AND_THE_SIDECARS_AND_STAGE_2_WAITS_FOR_A_STARTING_CONVERTER, type = "adr")
class EachServiceIsStartedOnlyWhereItIsOnThisMachineTest {

    private static final Path REPOSITORY = Path.of("").toAbsolutePath();

    private static final Path COMPOSE_FILE = REPOSITORY.resolve("compose.yaml");

    /** This machine's own address, and a name that resolves to it. */
    private static final InetAddress OWN = InetAddress.ofLiteral("192.168.1.20");

    private static final String OWN_NAME = "my-pc";

    /** A machine on the network that is not this one. */
    private static final String ANOTHER_MACHINE = "gpu-box.lan";

    private static final URI DOCLING_HERE = URI.create("http://localhost:5001");

    private static final URI OLLAMA_ELSEWHERE = URI.create("http://" + ANOTHER_MACHINE + ":11434");

    private static final Duration WAIT_AT_MOST = Duration.ofSeconds(1);

    private static final Duration CHECK_EVERY = Duration.ofMillis(20);

    /** Engine checks the engine fails before it answers. */
    private static final int ENGINE_CHECKS_FAILED = 2;

    private static final List<String> SYSTEMCTL_START = List.of("systemctl", "--no-ask-password", "start", "docker");

    /** The two lines ADR-421 §2 writes where there is no {@code .wslconfig}, each ended by CR LF. */
    private static final String WSLCONFIG = "[general]\r\ninstanceIdleTimeout=-1\r\n";

    private static String cpuImage;

    @TempDir
    Path lockFolder;

    @TempDir
    Path profileFolder;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void readTheImageTheFileBuilds() throws IOException {
        Map<String, Object> compose = new Yaml().load(Files.readString(COMPOSE_FILE));
        Map<String, Object> services = (Map<String, Object>) compose.get("services");
        cpuImage = (String) ((Map<String, Object>) services.get("docling-serve")).get("image");
    }

    @Test
    @Story("Vespera starts only the containers on this machine")
    @DisplayName("With Docling on this machine and Ollama on another, only Docling is asked, brought up by an up naming its service, and waited for")
    void onlyTheContainerOnThisMachineIsAskedBroughtUpAndWaitedFor() {
        ScriptedContainerHealth health = ScriptedContainerHealth.neitherAnswering();
        ScriptedDocker docker = ScriptedDocker.engineAnsweringAfter(ENGINE_CHECKS_FAILED)
                .answering(SYSTEMCTL_START, 0, "")
                .whenUpSucceeds(() -> health.answering(true, false));

        ContainerStarter.Outcome outcome = starter(new ContainerStartSettings(DOCLING_HERE, OLLAMA_ELSEWHERE, cpuImage,
                WAIT_AT_MOST), docker, health, HostSystem.LINUX).ensureRunning();

        claim("the start went through, Docling answering and Ollama, on another machine, never waited for",
                () -> assertThat(outcome).isEqualTo(ContainerStarter.Outcome.STARTED));
        claim("the commands were, in order and whole, the engine check, its start, the " + ENGINE_CHECKS_FAILED
                        + " checks of the wait, the checks for compose and buildx, and an up naming docling-serve alone",
                () -> assertThat(docker.commands()).containsExactly(ScriptedDocker.ENGINE_CHECK, SYSTEMCTL_START,
                        ScriptedDocker.ENGINE_CHECK, ScriptedDocker.ENGINE_CHECK, ScriptedDocker.COMPOSE_CHECK,
                        ScriptedDocker.BUILDX_CHECK, upNaming(List.of("docling-serve"))));
        claim("Docling was asked whether it answers", () -> assertThat(health.doclingQuestions()).isPositive());
        claim("and Ollama, on another machine, was never asked", () -> assertThat(health.ollamaQuestions()).isZero());
    }

    @Test
    @Story("Vespera starts only the containers on this machine")
    @DisplayName("Containers named by a name resolving to this machine's own address are started as localhost is, where reading the name as written took them for another machine's")
    void aNameResolvingToThisMachinesOwnAddressIsStartedAsLocalhostIs() {
        ScriptedContainerHealth health = ScriptedContainerHealth.neitherAnswering();
        ScriptedDocker docker = ScriptedDocker.engineAnsweringAfter(ENGINE_CHECKS_FAILED)
                .answering(SYSTEMCTL_START, 0, "")
                .whenUpSucceeds(() -> health.answering(true, true));

        ContainerStarter.Outcome outcome = starter(new ContainerStartSettings(URI.create("http://" + OWN_NAME + ":5001"),
                URI.create("http://" + OWN_NAME + ":11434"), cpuImage, WAIT_AT_MOST), docker, health, HostSystem.LINUX)
                .ensureRunning();

        claim("the containers named " + OWN_NAME + ", which resolves to this machine's own address, were started",
                () -> assertThat(outcome).isEqualTo(ContainerStarter.Outcome.STARTED));
        claim("with the commands a start for localhost runs, whole, and an up naming no service",
                () -> assertThat(docker.commands()).containsExactly(ScriptedDocker.ENGINE_CHECK, SYSTEMCTL_START,
                        ScriptedDocker.ENGINE_CHECK, ScriptedDocker.ENGINE_CHECK, ScriptedDocker.COMPOSE_CHECK,
                        ScriptedDocker.BUILDX_CHECK, upNaming(List.of())));
    }

    @Test
    @Story("Vespera starts only the containers on this machine")
    @DisplayName("On Windows .wslconfig is written where one container is on this machine, and nothing is run or written where neither is")
    void onWindowsTheWslconfigIsSeenToWhereOneContainerIsHereAndNotWhereNone() throws IOException {
        ScriptedContainerHealth answering = ScriptedContainerHealth.bothAnswering();
        ScriptedDocker oneHere = ScriptedDocker.engineRunning();
        Path neitherFolder = Files.createDirectory(lockFolder.resolve("another-profile"));

        ContainerStarter.Outcome withOneHere = starter(new ContainerStartSettings(DOCLING_HERE, OLLAMA_ELSEWHERE, cpuImage,
                WAIT_AT_MOST, "", profileFolder), oneHere, answering, HostSystem.WINDOWS).ensureRunning();
        ScriptedDocker noneHere = ScriptedDocker.engineRunning();
        ContainerStarter.Outcome withNoneHere = starter(new ContainerStartSettings(
                URI.create("http://" + ANOTHER_MACHINE + ":5001"), OLLAMA_ELSEWHERE, cpuImage, WAIT_AT_MOST, "",
                neitherFolder), noneHere, ScriptedContainerHealth.neitherAnswering(), HostSystem.WINDOWS).ensureRunning();

        claim("with Docling here answering, the start found it answering and ran no command",
                () -> assertThat(List.of(withOneHere, oneHere.commands().size()))
                        .containsExactly(ContainerStarter.Outcome.ALREADY_ANSWERING, 0));
        claim("and .wslconfig was written in the profile folder, its two lines whole",
                () -> assertThat(Files.readString(profileFolder.resolve(".wslconfig"), StandardCharsets.US_ASCII))
                        .isEqualTo(WSLCONFIG));
        claim("with neither container here, the start says they are on another machine and runs no command",
                () -> assertThat(List.of(withNoneHere, noneHere.commands().size()))
                        .containsExactly(ContainerStarter.Outcome.ON_ANOTHER_MACHINE, 0));
        claim("and writes nothing in its profile folder", () -> {
            try (Stream<Path> written = Files.list(neitherFolder)) {
                assertThat(written).isEmpty();
            }
        });
    }

    /** The starter over the repository's compose files, the test's lock folder, and a machine whose own address is {@link #OWN}. */
    private ContainerStarter starter(ContainerStartSettings settings, ScriptedDocker docker, ScriptedContainerHealth health,
            HostSystem host) {
        ThisMachine thisMachine = new ThisMachine(name -> switch (name) {
            case "localhost" -> new InetAddress[] {InetAddress.ofLiteral("127.0.0.1")};
            case OWN_NAME -> new InetAddress[] {OWN};
            case ANOTHER_MACHINE -> new InetAddress[] {InetAddress.ofLiteral("192.168.1.30")};
            default -> throw new UnknownHostException(name);
        }, () -> List.of(OWN));
        return new ContainerStarter(settings, docker, health, host, () -> ComposeFiles.in(REPOSITORY), lockFolder,
                CHECK_EVERY, thisMachine);
    }

    /** ADR-323 §4's up over {@code compose.yaml}, naming {@code services} after its options (ADR-430 §10). */
    private static List<String> upNaming(List<String> services) {
        List<String> command = new ArrayList<>(List.of("docker", "compose", "-p", "vespera", "-f",
                COMPOSE_FILE.toString(), "up", "-d", "--build", "--no-recreate"));
        command.addAll(services);
        return List.copyOf(command);
    }
}
