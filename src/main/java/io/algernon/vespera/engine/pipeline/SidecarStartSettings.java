package io.algernon.vespera.engine.pipeline;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.env.Environment;

/**
 * What the sidecar starter reads from configuration (ADR-323 §2, §7, ADR-390 §1, §7): the WSL distribution Docker
 * runs in on Windows ({@code vespera.sidecars.wsl-distribution}, blank for WSL's default), how long to wait for the engine or a sidecar
 * ({@code vespera.sidecars.wait-at-most}, 180 seconds unless set), the converter image that chooses the compose
 * files ({@code vespera.docling.image}), and where each sidecar is looked for.
 */
record SidecarStartSettings(
        URI doclingBaseUrl, URI ollamaBaseUrl, String doclingImage, Duration waitAtMost, String wslDistribution) {

    static final String WSL_DISTRIBUTION_PROPERTY = "vespera.sidecars.wsl-distribution";

    /** The settings with no WSL distribution named: WSL's default is used where it is read. */
    SidecarStartSettings(URI doclingBaseUrl, URI ollamaBaseUrl, String doclingImage, Duration waitAtMost) {
        this(doclingBaseUrl, ollamaBaseUrl, doclingImage, waitAtMost, "");
    }

    static final String WAIT_AT_MOST_PROPERTY = "vespera.sidecars.wait-at-most";

    /**
     * How long a sidecar is given to answer unless {@code vespera.sidecars.wait-at-most} says otherwise, written
     * once (ADR-323 §7, amending ADR-175 §2). On the whole archive on 2026-09-28 a killed container was back in 6
     * to 9 seconds (ADR-175's evidence); three minutes also covers one that reloads its models first.
     */
    static final String DEFAULT_WAIT_AT_MOST = "180s";

    static final String DEFAULT_DOCLING = "http://localhost:5001";

    /** Spring AI's own default for Ollama. */
    static final String DEFAULT_OLLAMA = "http://localhost:11434";

    static SidecarStartSettings from(Environment environment) {
        return new SidecarStartSettings(
                URI.create(environment.getProperty("vespera.docling.base-url", DEFAULT_DOCLING)),
                URI.create(environment.getProperty("spring.ai.ollama.base-url", DEFAULT_OLLAMA)),
                environment.getProperty("vespera.docling.image", ""),
                DurationStyle.detectAndParse(environment.getProperty(WAIT_AT_MOST_PROPERTY, DEFAULT_WAIT_AT_MOST)),
                environment.getProperty(WSL_DISTRIBUTION_PROPERTY, ""));
    }
}
