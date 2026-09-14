package io.algernon.vespera.pipeline;

/**
 * Neither the profile nor application configuration names a model to generate under (ADR-114).
 *
 * <p>Unset in the profile is not this fault — it means the configured default applies. This is the
 * narrower case where that default has been blanked as well, leaving no honest name to compose a
 * generator identity around.
 *
 * <p><b>It exists so that swallowing it is precise.</b> The closing line asks for the model it would
 * generate under and must not turn a misconfiguration into a stack trace on an invocation that has
 * otherwise succeeded, so it catches this and drops one clause. Catching {@link IllegalStateException}
 * there instead would swallow anything else the profile read could raise, which is a wider silence
 * than the decision asked for.
 *
 * <p>Extends {@link IllegalStateException} rather than {@link RuntimeException}: the run that would
 * actually generate refuses on it, and what it means to a caller that has not singled it out is
 * unchanged. Named for the fault rather than the lookup that noticed it (ADR-076).
 */
class NoGenerationModelNamedException extends IllegalStateException {

    NoGenerationModelNamedException(String configurationKey) {
        super("no generation model is named: " + configurationKey + " carries no value and generationModel is"
                + " unanswered in the profile, so there is no honest name to compose a generator identity around");
    }
}
