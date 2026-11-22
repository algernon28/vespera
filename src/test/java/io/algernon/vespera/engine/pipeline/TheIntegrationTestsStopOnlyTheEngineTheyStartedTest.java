package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.mock.env.MockEnvironment;

/**
 * What the integration tests do with the Docker engine (ADR-441, ADR-323 §5, ADR-390 §3, ADR-430 §3): where it does
 * not answer before the first test that needs it, it is started, and after the last this JVM's test containers are
 * removed and the containers and the engine stopped, an engine the question under the lock brings up among them; where
 * it answers when first asked, nothing is started and nothing is stopped. A start that is refused fails every test
 * that needs the engine with the refusal's own line, and stops nothing unless the engine answers once refused. The settings and the lock folder are read as the jar
 * reads them, and every test class that imports the test containers has the engine seen to first.
 *
 * <p>No Docker is needed: the starter is given a {@link ScriptedDocker} on Linux, whose engine is asked only while
 * {@code docker.service} is active, so a script that leaves that question unanswered has an engine that is down; and
 * the removal of the test containers is a {@link Removals} that counts the commands run before it.
 */
@Epic("Pipeline")
@Feature("The containers")
@Link(name = "ADR-390", url = Adr.VESPERA_USES_DOCKER_ENGINE_ALONE_AND_A_BUTTON_SHUTS_IT_DOWN, type = "adr")
@Link(name = "ADR-441", url = Adr.THE_INTEGRATION_TESTS_STOP_ONLY_THE_ENGINE_THEY_STARTED, type = "adr")
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

    /** A line that opens with the import of the test containers, alone or in an array. */
    private static final Pattern IMPORTS_THE_CONTAINERS =
            Pattern.compile("^@Import\\(\\s*\\{?[^)]*TestcontainersConfiguration\\.class", Pattern.MULTILINE);

    /** A line that opens with the extension of the engine, alone or in an array. */
    private static final Pattern EXTENDS_WITH_THE_ENGINE =
            Pattern.compile("^@ExtendWith\\(\\s*\\{?[^)]*DockerEngineForIntegrationTests\\.class", Pattern.MULTILINE);

    /** A line that opens with the Spring Boot test's annotation. */
    private static final Pattern IS_A_SPRING_BOOT_TEST = Pattern.compile("^@SpringBootTest\\b", Pattern.MULTILINE);

    @TempDir
    Path lockFolder;

    @Test
    @Story("The integration tests leave the Docker engine as they found it")
    @DisplayName("An engine that already answers is neither started nor stopped by the integration tests")
    void anEngineThatAnswersIsLeftAsItIs() {
        ScriptedDocker docker = ScriptedDocker.engineRunning().answering(SERVICE_ACTIVE, EXITED_WELL, "");
        Removals removals = new Removals(docker);

        DockerEngineForIntegrationTests.Engine engine =
                DockerEngineForIntegrationTests.Engine.seenToBy(starter(docker), removals);
        engine.refuseUnlessItAnswers();
        List<List<String>> beforeTheEnd = docker.commands();
        engine.close();

        claim("before the tests it asked whether docker.service is active and whether the engine answers, and"
                        + " started nothing",
                () -> assertThat(beforeTheEnd).containsExactly(SERVICE_ACTIVE, ScriptedDocker.ENGINE_CHECK));
        claim("and after the last test it ran no command at all: neither the containers nor the engine were stopped",
                () -> assertThat(docker.commands()).isEqualTo(beforeTheEnd));
        claim("and it removed no test container",
                () -> assertThat(removals.commandsBeforeEach()).isEmpty());
    }

    @Test
    @Story("The integration tests leave the Docker engine as they found it")
    @DisplayName("An engine that does not answer is started before the integration tests and stopped with the containers after")
    void anEngineThatIsDownIsStartedAndThenStopped() {
        ScriptedDocker docker = ScriptedDocker.engineAnsweringAfter(1)
                .answering(ENGINE_START, EXITED_WELL, "")
                .answering(COMPOSE_STOP, EXITED_WELL, "")
                .answering(ENGINE_STOP, EXITED_WELL, "");
        Removals removals = new Removals(docker);

        DockerEngineForIntegrationTests.Engine engine =
                DockerEngineForIntegrationTests.Engine.seenToBy(starter(docker), removals);
        engine.refuseUnlessItAnswers();
        List<List<String>> beforeTheEnd = docker.commands();
        engine.close();

        claim("before the tests it found docker.service inactive, asked the engine once, started it with systemctl"
                        + " and asked until it answered, then asked for Compose and buildx",
                () -> assertThat(beforeTheEnd).containsExactly(
                        SERVICE_ACTIVE, ScriptedDocker.ENGINE_CHECK, ENGINE_START, ScriptedDocker.ENGINE_CHECK,
                        ScriptedDocker.COMPOSE_CHECK, ScriptedDocker.BUILDX_CHECK));
        claim("after the last test it removed the test containers once, before any command of the stop",
                () -> assertThat(removals.commandsBeforeEach()).containsExactly(beforeTheEnd.size()));
        claim("and then stopped the containers and then the engine, and nothing else",
                () -> assertThat(docker.commands().subList(beforeTheEnd.size(), docker.commands().size()))
                        .containsExactly(COMPOSE_STOP, ENGINE_STOP));
    }

    @Test
    @Story("The integration tests leave the Docker engine as they found it")
    @DisplayName("An engine that did not answer at first and answers once asked under the lock is stopped after the integration tests")
    void anEngineBroughtUpByTheQuestionUnderTheLockIsStopped() {
        ScriptedDocker docker = ScriptedDocker.engineRunning()
                .answering(COMPOSE_STOP, EXITED_WELL, "")
                .answering(ENGINE_STOP, EXITED_WELL, "");
        Removals removals = new Removals(docker);

        DockerEngineForIntegrationTests.Engine engine =
                DockerEngineForIntegrationTests.Engine.seenToBy(starter(docker), removals);
        engine.refuseUnlessItAnswers();
        List<List<String>> beforeTheEnd = docker.commands();
        engine.close();

        claim("before the tests it found docker.service inactive, then asked the engine under the lock, which answered"
                        + " with no start command run",
                () -> assertThat(beforeTheEnd).containsExactly(
                        SERVICE_ACTIVE, ScriptedDocker.ENGINE_CHECK, ScriptedDocker.COMPOSE_CHECK,
                        ScriptedDocker.BUILDX_CHECK));
        claim("after the last test it removed the test containers once, before any command of the stop",
                () -> assertThat(removals.commandsBeforeEach()).containsExactly(beforeTheEnd.size()));
        claim("and then stopped the containers and then the engine: it did not answer before the tests, so it is"
                        + " theirs to stop",
                () -> assertThat(docker.commands().subList(beforeTheEnd.size(), docker.commands().size()))
                        .containsExactly(COMPOSE_STOP, ENGINE_STOP));
    }

    @Test
    @Story("The integration tests leave the Docker engine as they found it")
    @DisplayName("An engine that cannot be started fails the integration tests with the refusal's own line and is not stopped")
    void anEngineThatCannotBeStartedFailsTheTestsAndStopsNothing() {
        ScriptedDocker docker = ScriptedDocker.engineAnsweringAfter(ScriptedDocker.NEVER)
                .answering(ENGINE_START, REFUSED, "Failed to start docker.service: Access denied\n");
        Removals removals = new Removals(docker);

        DockerEngineForIntegrationTests.Engine engine =
                DockerEngineForIntegrationTests.Engine.seenToBy(starter(docker), removals);
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
        claim("and it removed no test container",
                () -> assertThat(removals.commandsBeforeEach()).isEmpty());
    }

    @Test
    @Story("The integration tests leave the Docker engine as they found it")
    @DisplayName("An engine started before a refusal still fails the integration tests with the refusal's line, and is stopped after them")
    void anEngineStartedBeforeARefusalIsStillStopped() {
        ScriptedDocker docker = ScriptedDocker.engineAnsweringAfter(2)
                .withoutCompose()
                .answering(SERVICE_ACTIVE, EXITED_WELL, "")
                .answering(ENGINE_START, EXITED_WELL, "")
                .answering(COMPOSE_STOP, EXITED_WELL, "")
                .answering(ENGINE_STOP, EXITED_WELL, "");
        Removals removals = new Removals(docker);

        DockerEngineForIntegrationTests.Engine engine =
                DockerEngineForIntegrationTests.Engine.seenToBy(starter(docker), removals);
        List<List<String>> beforeTheEnd = docker.commands();

        claim("before the tests it started the engine, which answered, then found no Compose and asked the engine"
                        + " again, starting nothing",
                () -> assertThat(beforeTheEnd).containsExactly(
                        SERVICE_ACTIVE, ScriptedDocker.ENGINE_CHECK, ScriptedDocker.ENGINE_CHECK, ENGINE_START,
                        ScriptedDocker.ENGINE_CHECK, ScriptedDocker.COMPOSE_CHECK, ScriptedDocker.BUILDX_CHECK,
                        SERVICE_ACTIVE, ScriptedDocker.ENGINE_CHECK));
        claim("every test that needs the engine is failed with the line naming what is missing",
                () -> assertThatThrownBy(engine::refuseUnlessItAnswers)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("the Docker engine answers, but docker compose is not installed on this"
                                + " machine"));
        engine.close();
        claim("after the last test it removed the test containers once, before any command of the stop",
                () -> assertThat(removals.commandsBeforeEach()).containsExactly(beforeTheEnd.size()));
        claim("and then stopped the containers and then the engine it started",
                () -> assertThat(docker.commands().subList(beforeTheEnd.size(), docker.commands().size()))
                        .containsExactly(COMPOSE_STOP, ENGINE_STOP));
    }

    @Test
    @Story("The integration tests leave the Docker engine as they found it")
    @DisplayName("A removal of the test containers that fails still lets the containers and the engine be stopped")
    void aRemovalThatFailsStillLetsTheEngineStop() {
        ScriptedDocker docker = ScriptedDocker.engineAnsweringAfter(1)
                .answering(ENGINE_START, EXITED_WELL, "")
                .answering(COMPOSE_STOP, EXITED_WELL, "")
                .answering(ENGINE_STOP, EXITED_WELL, "");
        Removals removals = new Removals(docker).failing("Status 500: the daemon refused the removal");

        DockerEngineForIntegrationTests.Engine engine =
                DockerEngineForIntegrationTests.Engine.seenToBy(starter(docker), removals);
        List<List<String>> beforeTheEnd = docker.commands();

        claim("closing after a removal that throws throws nothing itself",
                () -> assertThatCode(engine::close).doesNotThrowAnyException());
        claim("the removal was tried once, before any command of the stop",
                () -> assertThat(removals.commandsBeforeEach()).containsExactly(beforeTheEnd.size()));
        claim("and the containers and then the engine were stopped all the same",
                () -> assertThat(docker.commands().subList(beforeTheEnd.size(), docker.commands().size()))
                        .containsExactly(COMPOSE_STOP, ENGINE_STOP));
    }

    @Test
    @Story("The integration tests leave the Docker engine as they found it")
    @DisplayName("The integration tests read the WSL distribution, the wait and the lock folder as the jar reads them")
    void theSettingsAndTheLockFolderAreReadAsTheJarReadsThem() {
        MockEnvironment named = new MockEnvironment()
                .withProperty(EarlierSettingNames.WSL_DISTRIBUTION.earlier(), "Ubuntu-24.04")
                .withProperty(ContainerStartSettings.WAIT_AT_MOST_PROPERTY, "5m")
                .withProperty(CorpusRootLock.FOLDER_PROPERTY, lockFolder.toString())
                .withProperty("vespera.docling.base-url", "http://elsewhere.invalid:5001")
                .withProperty("spring.ai.ollama.base-url", "http://elsewhere.invalid:11434");
        MockEnvironment nothingNamed = new MockEnvironment();

        ContainerStartSettings settings = DockerEngineForIntegrationTests.settingsIn(named);
        ContainerStartSettings defaults = DockerEngineForIntegrationTests.settingsIn(nothingNamed);

        claim("the distribution is read under its earlier name where the new one is not set, and the wait under its"
                        + " own, as the jar reads them",
                () -> assertThat(List.of(settings.wslDistribution(), settings.waitAtMost()))
                        .containsExactly("Ubuntu-24.04", Duration.ofMinutes(5)));
        claim("the containers are asked for on this machine whatever addresses are configured, the test containers"
                        + " being this machine's",
                () -> assertThat(List.of(settings.doclingBaseUrl(), settings.ollamaBaseUrl()))
                        .containsExactly(DOCLING_HERE, OLLAMA_HERE));
        claim("with nothing set, the distribution is WSL's default and the wait the jar's own",
                () -> assertThat(List.of(defaults.wslDistribution(), defaults.waitAtMost()))
                        .containsExactly("", DurationStyle.detectAndParse(ContainerStartSettings.DEFAULT_WAIT_AT_MOST)));
        claim("the lock folder is the one the corpus-root lock folder setting names, and else the jar's own",
                () -> assertThat(List.of(
                                DockerEngineForIntegrationTests.lockFolderIn(named),
                                DockerEngineForIntegrationTests.lockFolderIn(nothingNamed)))
                        .containsExactly(lockFolder, CorpusRootLock.folderNamedBy(null)));
    }

    @Test
    @Story("The integration tests leave the Docker engine as they found it")
    @DisplayName("Every test class that starts the test containers has the Docker engine seen to before them")
    void everyTestClassOfTheContainersSeesToTheEngineFirst() throws IOException {
        List<Path> importing;
        try (Stream<Path> files = Files.walk(TEST_SOURCES)) {
            importing = files.filter(file -> file.toString().endsWith(".java"))
                    .filter(file -> IMPORTS_THE_CONTAINERS.matcher(read(file)).find())
                    .toList();
        }

        claim("at least one test class imports the test containers, so the check below looks at something",
                () -> assertThat(importing).isNotEmpty());
        for (Path file : importing) {
            String source = read(file);
            claim(file + " extends with the engine's extension on a line of its own, before the line of its Spring"
                            + " Boot test",
                    () -> assertThat(firstLineOf(EXTENDS_WITH_THE_ENGINE, source))
                            .isNotNegative()
                            .isLessThan(firstLineOf(IS_A_SPRING_BOOT_TEST, source)));
        }
    }

    /** Where {@code pattern} first matches in {@code source}, or -1 where it matches nowhere. */
    private static int firstLineOf(Pattern pattern, String source) {
        Matcher matcher = pattern.matcher(source);
        return matcher.find() ? matcher.start() : -1;
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

    /**
     * The removal of this JVM's test containers as these tests see it: each time it runs, how many commands the
     * {@link ScriptedDocker} had been given by then, and, where it is made to fail, the exception it throws after.
     */
    private static final class Removals implements Runnable {

        private final ScriptedDocker docker;
        private final List<Integer> commandsBeforeEach = new ArrayList<>();
        private String failure;

        Removals(ScriptedDocker docker) {
            this.docker = docker;
        }

        Removals failing(String why) {
            failure = why;
            return this;
        }

        @Override
        public void run() {
            commandsBeforeEach.add(docker.commands().size());
            if (failure != null) {
                throw new IllegalStateException(failure);
            }
        }

        List<Integer> commandsBeforeEach() {
            return List.copyOf(commandsBeforeEach);
        }
    }
}
