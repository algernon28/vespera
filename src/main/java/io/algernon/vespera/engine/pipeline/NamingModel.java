package io.algernon.vespera.engine.pipeline;

import org.springframework.core.env.Environment;

/**
 * Which model names the topics (ADR-403 section 2), read as {@code OllamaRelevanceLabeller} reads {@code
 * vespera.label.model}: where configuration names {@value #KEY}, that; else the chat model's default; else the code's
 * own. Not the profile's {@code generationModel}, which is a value of 6b's and would move the arrangement whenever it
 * is first given at the gate.
 *
 * <p>A blank value is no model, and stops the invocation where the arrangement run is minted.
 */
final class NamingModel {

    /** The key, named once so the refusal, the file and the read cannot disagree. */
    static final String KEY = "vespera.naming.model";

    /** The model the code holds where configuration names neither key. */
    static final String THE_CODES_DEFAULT = "qwen3:8b";

    private NamingModel() {}

    /** The model's name as configuration resolves it. */
    static String of(Environment environment) {
        String named = environment.getProperty(KEY);
        if (named != null) {
            return refuseBlank(named, KEY);
        }
        String chat = environment.getProperty(GenerationModel.CONFIGURED_DEFAULT_KEY);
        if (chat != null) {
            return refuseBlank(chat, GenerationModel.CONFIGURED_DEFAULT_KEY);
        }
        return THE_CODES_DEFAULT;
    }

    private static String refuseBlank(String value, String key) {
        if (value.isBlank()) {
            throw new NoNamingModelNamedException(KEY.equals(key) ? KEY : KEY + " (through " + key + ")");
        }
        return value.strip();
    }
}
