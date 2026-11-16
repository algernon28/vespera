package io.algernon.vespera.engine.pipeline;

/**
 * Neither {@code vespera.naming.model} nor the chat model's default names a model to name the topics under (ADR-403
 * section 2): the value was blanked, which leaves no honest name to compose the arrangement run's identity around.
 */
class NoNamingModelNamedException extends IllegalStateException {

    NoNamingModelNamedException(String key) {
        super("no naming model is named: " + key + " is blank, so there is no honest name to compose the"
                + " arrangement run's identity around");
    }
}
