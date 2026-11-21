package io.algernon.vespera.engine.pipeline;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.testcontainers.DockerClientFactory;

/**
 * Has the Docker engine answer before the first test class that starts the test containers, and shuts it down after
 * the last, only where the tests started it (ADR-441): where it did not answer when first asked, starting nothing, and
 * answers afterwards; an engine that answered when first asked is neither started nor stopped. Every test class that
 * imports {@code TestcontainersConfiguration} extends with it, declared before {@code @SpringBootTest} so that it runs
 * before the context and its containers are built.
 *
 * <p>It goes through Vespera's own {@link ContainerStarter}: whether the engine answers is asked starting nothing
 * (ADR-430 §3), the start is the engine's alone, its one command per system and the wait for it (ADR-323 §5, ADR-390
 * §2), and the stop is the shut-down, the containers' {@code stop} and then the engine's (ADR-390 §3). The WSL
 * distribution and the wait are read from the JVM's system properties and the environment, under their names and their
 * earlier ones, as the jar reads them, and {@code containers.lock} is taken in the folder the corpus-root lock folder
 * setting names, as the jar takes it. The lock keeps a start or a stop of the tests' from running its commands at the
 * same moment as a jar's start or stop; it does not keep the engine up under an invocation that is running. Where
 * Testcontainers looks for that engine is the machine's own setting, not this class's.
 *
 * <p>The engine is seen to once for the whole run: the outcome is kept in the root context's store, which closes it
 * once every test class has run. Where the tests started the engine, closing removes this JVM's test containers through
 * Testcontainers' own client, then stops the containers and the engine; Spring's cached contexts, which own those test
 * containers, close only as the JVM ends, after the engine is stopped. A refused start is kept too, so each test class
 * that needs the engine fails with the refusal's line at once, rather than waiting for the engine again.
 */
public final class DockerEngineForIntegrationTests implements BeforeAllCallback {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(DockerEngineForIntegrationTests.class);

    private static final Duration CHECK_EVERY = Duration.ofSeconds(2);

    private static final Logger log = LoggerFactory.getLogger(DockerEngineForIntegrationTests.class);

    @Override
    public void beforeAll(ExtensionContext context) {
        context.getRoot().getStore(NAMESPACE)
                .computeIfAbsent(Engine.class,
                        key -> Engine.seenToBy(
                                onThisMachine(new StandardEnvironment()),
                                DockerEngineForIntegrationTests::removeThisJvmsTestContainers),
                        Engine.class)
                .refuseUnlessItAnswers();
    }

    /**
     * The settings of the engine the test containers run on: both containers on this machine at the jar's own
     * addresses, whatever {@code environment} names, the test containers being this machine's; the WSL distribution and
     * the wait as the jar reads them from {@code environment}.
     */
    static ContainerStartSettings settingsIn(Environment environment) {
        return new ContainerStartSettings(
                URI.create(ContainerStartSettings.DEFAULT_DOCLING),
                URI.create(ContainerStartSettings.DEFAULT_OLLAMA),
                "",
                DurationStyle.detectAndParse(ContainerStartSettings.waitAtMostIn(environment)),
                EarlierSettingNames.read(environment, EarlierSettingNames.WSL_DISTRIBUTION).orElse(""));
    }

    /** The folder {@code containers.lock} is taken in, as the jar reads it from {@code environment}. */
    static Path lockFolderIn(Environment environment) {
        return CorpusRootLock.folderNamedBy(environment.getProperty(CorpusRootLock.FOLDER_PROPERTY));
    }

    /** The starter of the engine the test containers run on, on this machine, as the jar would build it. */
    private static ContainerStarter onThisMachine(Environment environment) {
        ContainerStartSettings settings = settingsIn(environment);
        return new ContainerStarter(
                settings,
                new SystemProcessRunner(Path.of(System.getProperty("java.io.tmpdir"))),
                new HttpContainerHealth(settings),
                HostSystem.current(),
                () -> {
                    throw new UnsupportedOperationException("the integration tests write no compose file out");
                },
                lockFolderIn(environment),
                CHECK_EVERY);
    }

    /**
     * Removes, with their volumes, every container Testcontainers labelled as this JVM's, running or stopped, through
     * Testcontainers' own client.
     */
    private static void removeThisJvmsTestContainers() {
        DockerClient client = DockerClientFactory.instance().client();
        List<Container> ours = client.listContainersCmd()
                .withShowAll(true)
                .withLabelFilter(Map.of(
                        DockerClientFactory.TESTCONTAINERS_SESSION_ID_LABEL, DockerClientFactory.SESSION_ID))
                .exec();
        for (Container container : ours) {
            client.removeContainerCmd(container.getId()).withForce(true).withRemoveVolumes(true).exec();
        }
        log.info("The integration tests removed their {} test containers before stopping the Docker engine",
                ours.size());
    }

    /** The engine as the tests found and left it: whether they started it, or the line of the start's refusal. */
    static final class Engine implements AutoCloseable {

        private static final Logger log = LoggerFactory.getLogger(Engine.class);

        private final ContainerStarter starter;
        private final boolean startedHere;
        private final String refusal;
        private final Runnable removeTheTestContainers;

        private Engine(ContainerStarter starter, boolean startedHere, String refusal, Runnable removeTheTestContainers) {
            this.starter = starter;
            this.startedHere = startedHere;
            this.refusal = refusal;
            this.removeTheTestContainers = removeTheTestContainers;
        }

        /**
         * Asks whether the engine answers, starting nothing, and has it answer where it does not. The tests started it
         * exactly where that first question found it not answering and it answers afterwards: once {@link
         * ContainerStarter#ensureTheEngineRuns} returns, whatever its outcome, or, where the start was refused, where it
         * answers when asked again, starting nothing. An engine the first question found answering is never theirs.
         * Where neither container is on this machine, which the {@code localhost} addresses {@link #settingsIn} gives
         * never are, nothing is asked or started.
         *
         * @param removeTheTestContainers what closing runs before the stop, where the tests started the engine
         */
        static Engine seenToBy(ContainerStarter starter, Runnable removeTheTestContainers) {
            if (starter.engineAnswersStartingNothing().orElse(true)) {
                return new Engine(starter, false, null, removeTheTestContainers);
            }
            try {
                starter.ensureTheEngineRuns();
                log.info("The integration tests started the Docker engine, and stop it after the last of them");
                return new Engine(starter, true, null, removeTheTestContainers);
            } catch (ContainerStartRefusedException refused) {
                boolean answersNow = starter.engineAnswersStartingNothing().orElse(false);
                if (answersNow) {
                    log.info("The integration tests started the Docker engine before the start was refused, and stop it"
                            + " after the last of them");
                }
                return new Engine(starter, answersNow, refused.getMessage(), removeTheTestContainers);
            }
        }

        /** Fails the test class asking, with the refusal's line, where the engine could not be started. */
        void refuseUnlessItAnswers() {
            if (refusal != null) {
                throw new IllegalStateException(
                        "The integration tests need the Docker engine, and it does not answer: " + refusal);
            }
        }

        /**
         * Where the tests started the engine, removes this JVM's test containers, a removal that fails being logged and
         * the stop going on, then stops the containers and the engine; does nothing otherwise.
         */
        @Override
        public void close() {
            if (!startedHere) {
                return;
            }
            try {
                removeTheTestContainers.run();
            } catch (RuntimeException failed) {
                log.warn("The integration tests could not remove their test containers, and stop the Docker engine all"
                        + " the same: {}", failed.toString());
            }
            ContainerStarter.ShutDown shutDown = starter.shutDown();
            if (shutDown.done()) {
                log.info("The integration tests stopped the Docker engine they started: {}", shutDown.line());
            } else {
                log.warn("The integration tests started the Docker engine and could not stop it: {}", shutDown.line());
            }
        }
    }
}
