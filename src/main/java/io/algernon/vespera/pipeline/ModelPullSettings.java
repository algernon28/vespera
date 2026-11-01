package io.algernon.vespera.pipeline;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.env.Environment;

/**
 * What the pull of a model Ollama does not serve reads from configuration (ADR-329 §4, §6, §9): whether to pull at
 * all ({@code vespera.models.pull}, true unless set), where Ollama is ({@code spring.ai.ollama.base-url}), and how
 * long a pull may go without moving before it is given up ({@code vespera.models.pull-stalled-at-most}, 600
 * seconds unless set). The defaults are written once, here.
 */
record ModelPullSettings(boolean pull, URI ollamaBaseUrl, Duration stalledAtMost) {

    static final String PULL_PROPERTY = "vespera.models.pull";

    static final String STALLED_AT_MOST_PROPERTY = "vespera.models.pull-stalled-at-most";

    /**
     * Ten minutes: Ollama starts a stalled part again without limit, and checks the digest of a large layer after
     * its last byte, which a slow drive takes minutes over (ADR-329 §6). Reasoned, not measured.
     */
    static final String DEFAULT_STALLED_AT_MOST = "600s";

    /** Spring AI's own default for Ollama. */
    private static final String DEFAULT_OLLAMA = "http://localhost:11434";

    static ModelPullSettings from(Environment environment) {
        return new ModelPullSettings(
                environment.getProperty(PULL_PROPERTY, Boolean.class, true),
                URI.create(environment.getProperty("spring.ai.ollama.base-url", DEFAULT_OLLAMA)),
                DurationStyle.detectAndParse(
                        environment.getProperty(STALLED_AT_MOST_PROPERTY, DEFAULT_STALLED_AT_MOST)));
    }
}
