package io.algernon.vespera.engine.pipeline;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.convert.DurationStyle;

/**
 * Has the Docker engine answer before the first test class that starts the test containers, and stops it after the
 * last, where it did not answer before the first; an engine that already answered is neither started nor stopped.
 * Every test class that imports {@code TestcontainersConfiguration} extends with it, declared before
 * {@code @SpringBootTest} so that it runs before the context and its containers are built.
 *
 * <p>It goes through Vespera's own {@link ContainerStarter}: whether the engine answers is asked starting nothing
 * (ADR-430 §3), the start is the engine's alone, its one command per system and the wait for it (ADR-323 §5, ADR-390
 * §2), and the stop is the shut-down, the containers' {@code stop} and then the engine's (ADR-390 §3). It takes
 * {@code containers.lock} in the folder an invocation of the jar takes it in, so a test run and a Vespera invocation
 * on the same machine do not start or stop the engine under each other. On Windows the engine is in WSL's default
 * distribution, or the one the system property {@code vespera.containers.wsl-distribution} names. Where Testcontainers
 * looks for that engine is the machine's own setting, not this class's.
 *
 * <p>The engine is seen to once for the whole run: the outcome is kept in the root context's store, which closes it,
 * and so stops what was started, once every test class has run. A refused start is kept too, so each test class that
 * needs the engine fails with the refusal's line at once, rather than waiting for the engine again.
 */
public final class DockerEngineForIntegrationTests implements BeforeAllCallback {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(DockerEngineForIntegrationTests.class);

    private static final Duration CHECK_EVERY = Duration.ofSeconds(2);

    @Override
    public void beforeAll(ExtensionContext context) {
        context.getRoot().getStore(NAMESPACE)
                .computeIfAbsent(Engine.class, key -> Engine.seenToBy(onThisMachine()), Engine.class)
                .refuseUnlessItAnswers();
    }

    /** The starter of the engine the test containers run on, on this machine, as the jar would build it. */
    private static ContainerStarter onThisMachine() {
        ContainerStartSettings settings = new ContainerStartSettings(
                URI.create(ContainerStartSettings.DEFAULT_DOCLING),
                URI.create(ContainerStartSettings.DEFAULT_OLLAMA),
                "",
                DurationStyle.detectAndParse(ContainerStartSettings.DEFAULT_WAIT_AT_MOST),
                System.getProperty(ContainerStartSettings.WSL_DISTRIBUTION_PROPERTY, ""));
        return new ContainerStarter(
                settings,
                new SystemProcessRunner(Path.of(System.getProperty("java.io.tmpdir"))),
                new HttpContainerHealth(settings),
                HostSystem.current(),
                () -> {
                    throw new UnsupportedOperationException("the integration tests write no compose file out");
                },
                CorpusRootLock.folderNamedBy(null),
                CHECK_EVERY);
    }

    /** The engine as the tests found and left it: whether they started it, or the line of the start's refusal. */
    static final class Engine implements AutoCloseable {

        private static final Logger log = LoggerFactory.getLogger(Engine.class);

        private final ContainerStarter starter;
        private final boolean startedHere;
        private final String refusal;

        private Engine(ContainerStarter starter, boolean startedHere, String refusal) {
            this.starter = starter;
            this.startedHere = startedHere;
            this.refusal = refusal;
        }

        /**
         * Asks whether the engine answers, starting nothing, and starts it where it does not. Where neither container
         * is on this machine, which the {@code localhost} addresses {@link #onThisMachine} gives never are, nothing is
         * asked or started.
         */
        static Engine seenToBy(ContainerStarter starter) {
            if (starter.engineAnswersStartingNothing().orElse(true)) {
                return new Engine(starter, false, null);
            }
            try {
                starter.ensureTheEngineRuns();
                log.info("The integration tests started the Docker engine, and stop it after the last of them");
                return new Engine(starter, true, null);
            } catch (ContainerStartRefusedException refused) {
                return new Engine(starter, false, refused.getMessage());
            }
        }

        /** Fails the test class asking, with the refusal's line, where the engine could not be started. */
        void refuseUnlessItAnswers() {
            if (refusal != null) {
                throw new IllegalStateException(
                        "The integration tests need the Docker engine, and it does not answer: " + refusal);
            }
        }

        /** Stops the containers and the engine, where the tests started the engine; does nothing otherwise. */
        @Override
        public void close() {
            if (!startedHere) {
                return;
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
