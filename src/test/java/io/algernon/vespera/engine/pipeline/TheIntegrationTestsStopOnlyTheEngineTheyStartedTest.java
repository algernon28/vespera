package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the integration tests do with the Docker engine (ADR-323 §5, ADR-390 §3, ADR-430 §3): where it does not answer
 * before the first test that needs it, it is started, and stopped with the containers after the last; where it already
 * answers, nothing is started and nothing is stopped. A start that is refused fails every test that needs the engine
 * with the refusal's own line, and stops nothing. And every test class that imports the test containers has the engine
 * seen to first.
 *
 * <p>No Docker is needed: the starter is given a {@link ScriptedDocker} on Linux, whose engine is asked only while
 * {@code docker.service} is active, so a script that leaves that question unanswered has an engine that is down.
 */
@Epic("Pipeline")
@Feature("The containers")
@Link(name = "ADR-390", url = Adr.VESPERA_USES_DOCKER_ENGINE_ALONE_AND_A_BUTTON_SHUTS_IT_DOWN, type = "adr")
class TheIntegrationTestsStopOnlyTheEngineTheyStartedTest {

    private static final URI DOCLING_HERE = URI.create(ContainerStartSettings.DEFAULT_DOCLING);

    private static final URI OLLAMA_HERE = URI.create(ContainerStartSettings.DEFAULT_OLLAMA);

    /** The bound every command is killed at: one second in these tests. */
    private static final Duration WAIT_AT_MOST = Duration.ofSeconds(1);

    private static final Duration CHECK_EVERY = Duration.ofMillis(20);

    private static final List<String> SERVICE_ACTIVE = List.of("systemctl", "is-active", "--quiet", "docker.service");

    private static final List<String> ENGINE_START = List.of("systemctl", "--no-ask-password", "start", "docker");

    private static final List<String> COMPOSE_STOP = List.of("docker", "compose", "-p", "vespera", "stop");

    private static final List<String> ENGINE_STOP =
            List.of("systemctl", "--no-ask-password", "stop", "docker.socket", "docker.service");

    private static final int EXITED_WELL = 0;

    private static final int REFUSED = 1;

    /** Where the test sources lie, relative to the repository root every test runs in. */
    private static final Path TEST_SOURCES = Path.of("src/test/java");

    private static final String IMPORTS_THE_CONTAINERS = "@Import(TestcontainersConfiguration.class)";

    private static final String EXTENDS_WITH_THE_ENGINE = "@ExtendWith(DockerEngineForIntegrationTests.class)";

    @TempDir
    Path lockFolder;

    @Test
    @Story("The integration tests leave the Docker engine as they found it")
    @DisplayName("An engine that already answers is neither started nor stopped by the integration tests")
    void anEngineThatAnswersIsLeftAsItIs() {
        ScriptedDocker docker = ScriptedDocker.engineRunning().answering(SERVICE_ACTIVE, EXITED_WELL, "");

        DockerEngineForIntegrationTests.Engine engine = DockerEngineForIntegrationTests.Engine.seenToBy(starter(docker));
        engine.refuseUnlessItAnswers();
        List<List<String>> beforeTheEnd = docker.commands();
        engine.close();

        claim("before the tests it asked whether docker.service is active and whether the engine answers, and"
                        + " started nothing",
                () -> assertThat(beforeTheEnd).containsExactly(SERVICE_ACTIVE, ScriptedDocker.ENGINE_CHECK));
        claim("and after the last test it ran no command at all: neither the containers nor the engine were stopped",
                () -> assertThat(docker.commands()).isEqualTo(beforeTheEnd));
    }

    @Test
    @Story("The integration tests leave the Docker engine as they found it")
    @DisplayName("An engine that does not answer is started before the integration tests and stopped with the containers after")
    void anEngineThatIsDownIsStartedAndThenStopped() {
        ScriptedDocker docker = ScriptedDocker.engineAnsweringAfter(1)
                .answering(ENGINE_START, EXITED_WELL, "")
                .answering(COMPOSE_STOP, EXITED_WELL, "")
                .answering(ENGINE_STOP, EXITED_WELL, "");

        DockerEngineForIntegrationTests.Engine engine = DockerEngineForIntegrationTests.Engine.seenToBy(starter(docker));
        engine.refuseUnlessItAnswers();
        List<List<String>> beforeTheEnd = docker.commands();
        engine.close();

        claim("before the tests it found docker.service inactive, asked the engine once, started it with systemctl"
                        + " and asked until it answered, then asked for Compose and buildx",
                () -> assertThat(beforeTheEnd).containsExactly(
                        SERVICE_ACTIVE, ScriptedDocker.ENGINE_CHECK, ENGINE_START, ScriptedDocker.ENGINE_CHECK,
                        ScriptedDocker.COMPOSE_CHECK, ScriptedDocker.BUILDX_CHECK));
        claim("and after the last test it stopped the containers and then the engine, and nothing else",
                () -> assertThat(docker.commands().subList(beforeTheEnd.size(), docker.commands().size()))
                        .containsExactly(COMPOSE_STOP, ENGINE_STOP));
    }

    @Test
    @Story("The integration tests leave the Docker engine as they found it")
    @DisplayName("An engine that cannot be started fails the integration tests with the refusal's own line and is not stopped")
    void anEngineThatCannotBeStartedFailsTheTestsAndStopsNothing() {
        ScriptedDocker docker = ScriptedDocker.engineAnsweringAfter(ScriptedDocker.NEVER)
                .answering(ENGINE_START, REFUSED, "Failed to start docker.service: Access denied\n");

        DockerEngineForIntegrationTests.Engine engine = DockerEngineForIntegrationTests.Engine.seenToBy(starter(docker));
        List<List<String>> beforeTheEnd = docker.commands();

        claim("every test that needs the engine is failed with a line naming the start's command, its exit and its"
                        + " last line",
                () -> assertThatThrownBy(engine::refuseUnlessItAnswers)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("systemctl --no-ask-password start docker exited " + REFUSED
                                + ": Failed to start docker.service: Access denied"));
        engine.close();
        claim("and after the last test it ran no command: there is nothing it started to stop",
                () -> assertThat(docker.commands()).isEqualTo(beforeTheEnd));
    }

    @Test
    @Story("The integration tests leave the Docker engine as they found it")
    @DisplayName("Every test class that starts the test containers has the Docker engine seen to before them")
    void everyTestClassOfTheContainersSeesToTheEngineFirst() throws IOException {
        List<Path> importing;
        try (Stream<Path> files = Files.walk(TEST_SOURCES)) {
            importing = files.filter(file -> file.toString().endsWith(".java"))
                    .filter(file -> read(file).contains(IMPORTS_THE_CONTAINERS))
                    .toList();
        }

        claim("at least one test class imports the test containers, so the check below looks at something",
                () -> assertThat(importing).isNotEmpty());
        for (Path file : importing) {
            String source = read(file);
            claim(file + " extends with the engine's extension, declared before its Spring Boot test",
                    () -> assertThat(source.indexOf(EXTENDS_WITH_THE_ENGINE))
                            .isNotNegative()
                            .isLessThan(source.indexOf("@SpringBootTest")));
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException unreadable) {
            throw new IllegalStateException("cannot read " + file, unreadable);
        }
    }

    private ContainerStarter starter(ScriptedDocker docker) {
        ContainerStartSettings settings = new ContainerStartSettings(DOCLING_HERE, OLLAMA_HERE, "", WAIT_AT_MOST);
        return new ContainerStarter(settings, docker, ScriptedContainerHealth.bothAnswering(), HostSystem.LINUX, () -> {
            throw new UnsupportedOperationException("the integration tests write no compose file out");
        }, lockFolder, CHECK_EVERY);
    }
}
