package io.algernon.vespera.pipeline;

import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.profile.ProfileValue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Which model stage 6b generates under (ADR-114, #179).
 *
 * <p><b>Not a gate, and that is the decision this class is.</b> {@link EmbeddingModelGate} ends the
 * invocation when nobody has named a model; this resolves one and carries on. The two differ because
 * an embedding model keys every vector and every relevance verdict — a corpus scored under a model
 * nobody chose has verdicts nobody can defend — while a generation model keys a run whose output is
 * prose, which the operator opens and reads. Gating a choice the operator can check afterwards buys a
 * stop and no information.
 *
 * <p>The default lives where Spring AI's own auto-configuration already reads it, rather than under a
 * {@code vespera.*} key, so the chat model bean and the call name one model instead of two.
 */
@Component
class GenerationModel {

    /** Where the shipped default lives, named once so the refusal and the binding cannot disagree. */
    static final String CONFIGURED_DEFAULT_KEY = "spring.ai.ollama.chat.options.model";

    private final ProfileStore profileStore;
    private final String configuredDefault;

    GenerationModel(
            ProfileStore profileStore,
            @Value("${" + CONFIGURED_DEFAULT_KEY + ":}") String configuredDefault) {
        this.profileStore = profileStore;
        this.configuredDefault = configuredDefault;
    }

    /**
     * The model this invocation generates under: what the operator wrote, and otherwise what
     * configuration carries.
     *
     * <p>A blank profile value is unset rather than a fault — {@link ProfileValue#isSet()} already
     * says so — which is what keeps the resolved name from ever being blank, and the instrument rule
     * that an identity refuses a blank value satisfied by construction rather than by a check here.
     */
    String name() {
        Profile profile = profileStore.load();
        if (profile.generationModel().isSet()) {
            return profile.generationModel().value().trim();
        }
        if (configuredDefault == null || configuredDefault.isBlank()) {
            throw new NoGenerationModelNamedException(CONFIGURED_DEFAULT_KEY);
        }
        return configuredDefault;
    }
}
