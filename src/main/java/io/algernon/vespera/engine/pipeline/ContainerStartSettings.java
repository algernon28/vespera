package io.algernon.vespera.engine.pipeline;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.env.Environment;

/**
 * What the container starter reads from configuration (ADR-323 §2, §7, ADR-390 §1, §7): the WSL distribution Docker
 * runs in on Windows ({@code vespera.containers.wsl-distribution}, blank for WSL's default), how long to wait for the engine or a container
 * ({@code vespera.containers.wait-at-most}, 180 seconds unless set), the converter image that chooses the compose
 * files ({@code vespera.docling.image}), and where each container is looked for.
 */
record ContainerStartSettings(
        URI doclingBaseUrl,
        URI ollamaBaseUrl,
        String doclingImage,
        Duration waitAtMost,
        String wslDistribution,
        Path profileFolder) {

    static final String WSL_DISTRIBUTION_PROPERTY = "vespera.containers.wsl-distribution";

    static final String USER_HOME_PROPERTY = "user.home";

    /** The settings with no WSL distribution named: WSL's default is used where it is read. */
    ContainerStartSettings(URI doclingBaseUrl, URI ollamaBaseUrl, String doclingImage, Duration waitAtMost) {
        this(doclingBaseUrl, ollamaBaseUrl, doclingImage, waitAtMost, "");
    }

    /** The settings with no profile folder known (ADR-421 §7): {@code .wslconfig} is looked for nowhere. */
    ContainerStartSettings(
            URI doclingBaseUrl, URI ollamaBaseUrl, String doclingImage, Duration waitAtMost, String wslDistribution) {
        this(doclingBaseUrl, ollamaBaseUrl, doclingImage, waitAtMost, wslDistribution, null);
    }

    static final String WAIT_AT_MOST_PROPERTY = "vespera.containers.wait-at-most";

    /**
     * How long a container is given to answer unless {@code vespera.containers.wait-at-most} says otherwise, written
     * once (ADR-323 §7, amending ADR-175 §2). On the whole archive on 2026-09-28 a killed container was back in 6
     * to 9 seconds (ADR-175's evidence); three minutes also covers one that reloads its models first.
     */
    static final String DEFAULT_WAIT_AT_MOST = "180s";

    static final String DEFAULT_DOCLING = "http://localhost:5001";

    /** Spring AI's own default for Ollama. */
    static final String DEFAULT_OLLAMA = "http://localhost:11434";

    /** The wait as {@code environment} gives it, under its new name or the earlier one (ADR-433 §5), else the code's own. */
    static String waitAtMostIn(Environment environment) {
        return EarlierSettingNames.read(environment, EarlierSettingNames.WAIT_AT_MOST).orElse(DEFAULT_WAIT_AT_MOST);
    }

    static ContainerStartSettings from(Environment environment) {
        String home = environment.getProperty(USER_HOME_PROPERTY);
        return new ContainerStartSettings(
                URI.create(environment.getProperty("vespera.docling.base-url", DEFAULT_DOCLING)),
                URI.create(environment.getProperty("spring.ai.ollama.base-url", DEFAULT_OLLAMA)),
                environment.getProperty("vespera.docling.image", ""),
                DurationStyle.detectAndParse(waitAtMostIn(environment)),
                EarlierSettingNames.read(environment, EarlierSettingNames.WSL_DISTRIBUTION).orElse(""),
                home == null || home.isBlank() ? null : Path.of(home.strip()));
    }
}
