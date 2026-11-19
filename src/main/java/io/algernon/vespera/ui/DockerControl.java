package io.algernon.vespera.ui;

import io.algernon.vespera.engine.pipeline.DockerEngine;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * What the window asks of Docker (ADR-390 §4): start the engine as the window opens, stop the sidecars as it closes,
 * and shut down the sidecars and the engine on its button. Each answers a {@link Said}: whether it went through, and
 * its line. {@link #inProcess} is the window's, over {@link DockerEngine}, the one class of the jar the control panel
 * names; {@link #NONE} runs nothing.
 */
interface DockerControl {

    /** What a call came to: whether it went through, and the one line it said (empty where it went through with nothing to say). */
    record Said(boolean done, String line) {
    }

    /** Makes the engine answer, for the addresses {@code values} hold; no sidecar is asked and no {@code up} is run. */
    Said start(LaunchValues values);

    /** Stops the sidecars and then the engine. */
    Said shutDown();

    /** Stops the sidecars alone, where one answers on this machine, for the addresses {@code values} hold. */
    Said stopTheSidecars(LaunchValues values);

    /** Runs nothing and answers done with no line: what a state built with no Docker holds. */
    DockerControl NONE = new DockerControl() {
        @Override
        public Said start(LaunchValues values) {
            return new Said(true, "");
        }

        @Override
        public Said shutDown() {
            return new Said(true, "");
        }

        @Override
        public Said stopTheSidecars(LaunchValues values) {
            return new Said(true, "");
        }
    };

    /**
     * Where the engine's code takes its settings from (ADR-390 §4).
     *
     * @param wslDistribution the WSL distribution, blank for WSL's default
     * @param waitAtMost the bound of every start and stop command
     * @param lockFolder the folder the corpus-root locks lie in, where {@code sidecars.lock} lies too
     * @param profileFolder the Windows profile folder {@code .wslconfig} lies in (ADR-421 §6)
     */
    record Settings(String wslDistribution, Duration waitAtMost, Path lockFolder, Path profileFolder,
            StartingValues.Sources sources) {
    }

    /**
     * The jar's own default addresses, which a blank one stands for where the environment, {@code .env} and the jar's
     * {@code application.yaml} name none either (ADR-390 §4, §7).
     */
    String DEFAULT_DOCLING = "http://localhost:5001";

    String DEFAULT_OLLAMA = "http://localhost:11434";

    String DOCLING_PROPERTY = "vespera.docling.base-url";

    String OLLAMA_PROPERTY = "spring.ai.ollama.base-url";

    /** The control: {@link DockerEngine}, called in this process. */
    static DockerControl inProcess(Settings settings) {
        DockerEngine engine = DockerEngine.of(
                settings.wslDistribution(), settings.waitAtMost(), settings.lockFolder(), settings.profileFolder());
        return new DockerControl() {
            @Override
            public Said start(LaunchValues values) {
                URI docling = address(values.doclingBaseUrl(), DOCLING_PROPERTY, DEFAULT_DOCLING);
                URI ollama = address(values.ollamaBaseUrl(), OLLAMA_PROPERTY, DEFAULT_OLLAMA);
                Optional<String> wslLine = engine.keepTheWslDistributionUp(docling, ollama);
                Optional<String> refusal = engine.startTheEngine(docling, ollama);
                if (refusal.isPresent()) {
                    return new Said(false, refusal.get());
                }
                return new Said(true, wslLine.orElse(""));
            }

            @Override
            public Said shutDown() {
                String line = engine.shutDown();
                return new Said(DockerEngine.SHUT_DOWN_DONE.equals(line), line);
            }

            @Override
            public Said stopTheSidecars(LaunchValues values) {
                return said(engine.stopTheSidecars(address(values.doclingBaseUrl(), DOCLING_PROPERTY, DEFAULT_DOCLING),
                        address(values.ollamaBaseUrl(), OLLAMA_PROPERTY, DEFAULT_OLLAMA)));
            }

            private static Said said(Optional<String> refusal) {
                return new Said(refusal.isEmpty(), refusal.orElse(""));
            }

            /** The held address, or where it is blank the one the window's fields and checks start from (ADR-390 §4). */
            private URI address(String held, String property, String blank) {
                String named = held == null || held.isBlank() ? StartingValues.of(property, settings.sources()).value() : held;
                try {
                    return URI.create(named == null || named.isBlank() ? blank : named.strip());
                } catch (IllegalArgumentException unreadable) {
                    return URI.create(blank);
                }
            }
        };
    }
}
