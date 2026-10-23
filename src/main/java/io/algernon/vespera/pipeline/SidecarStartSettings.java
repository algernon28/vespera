package io.algernon.vespera.pipeline;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.env.Environment;

/**
 * What the sidecar starter reads from configuration (ADR-323 §2, §7, §9): whether to start the sidecars at all
 * ({@code vespera.sidecars.start}, true unless set), how long to wait for the engine or a sidecar
 * ({@code vespera.sidecars.wait-at-most}, 180 seconds unless set), the converter image that chooses the compose
 * files ({@code vespera.docling.image}), and where each sidecar is looked for.
 */
record SidecarStartSettings(
        boolean start, URI doclingBaseUrl, URI ollamaBaseUrl, String doclingImage, Duration waitAtMost) {

    static final String START_PROPERTY = "vespera.sidecars.start";

    static final String WAIT_AT_MOST_PROPERTY = "vespera.sidecars.wait-at-most";

    private static final String DEFAULT_WAIT_AT_MOST = "180s";

    private static final String DEFAULT_DOCLING = "http://localhost:5001";

    /** Spring AI's own default for Ollama. */
    private static final String DEFAULT_OLLAMA = "http://localhost:11434";

    static SidecarStartSettings from(Environment environment) {
        return new SidecarStartSettings(
                environment.getProperty(START_PROPERTY, Boolean.class, true),
                URI.create(environment.getProperty("vespera.docling.base-url", DEFAULT_DOCLING)),
                URI.create(environment.getProperty("spring.ai.ollama.base-url", DEFAULT_OLLAMA)),
                environment.getProperty("vespera.docling.image", ""),
                DurationStyle.detectAndParse(environment.getProperty(WAIT_AT_MOST_PROPERTY, DEFAULT_WAIT_AT_MOST)));
    }
}
