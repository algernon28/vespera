package io.algernon.vespera.engine.pipeline;

/**
 * No model is named to name the topics under (ADR-407 section 2).
 *
 * <p>Unset is not this fault: the configured generation default applies. This is the narrower case where
 * {@code vespera.naming.model} is bound blank, or is unset and the chat model it falls back to is blank, leaving no
 * honest name to compose the arrangement run's identity around. The invocation stops where that run is minted.
 *
 * <p>Extends {@link IllegalStateException} as {@link NoGenerationModelNamedException} does, and is named for the
 * fault rather than the lookup that noticed it (ADR-076).
 */
class NoNamingModelNamedException extends IllegalStateException {

    NoNamingModelNamedException(String configurationKey) {
        super("no naming model is named: " + configurationKey + " is blank, so vespera.naming.model names no model"
                + " to compose the arrangement run's identity around");
    }
}
