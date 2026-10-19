package io.algernon.vespera.pipeline;

import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Gate 3 (ADR-084, ADR-286): whether an embedding model is named anywhere. {@code embedding} may
 * depend only on {@code ledger} (ADR-040), so the profile is read here rather than there, the same
 * shape {@link SeedGate} and {@link RedundancyGate} already use for their own keys.
 *
 * <p>The name resolves in order: the profile's {@code embeddingModel}, then the configured {@value
 * #CONFIGURED_DEFAULT_KEY} (shipped as {@code bge-m3:latest}), then none. A blank value in either is
 * unset. The gate is shut only where neither names one, which no operator meets with the shipped
 * configuration; blanking the key holds scoring until a candidate is chosen.
 *
 * <p>Checked before {@link StageRuns#embeddingScoring} mints a row, because a scoring run that did nothing should
 * not exist in the {@code run} table (ADR-080's rule, applied a third time).
 */
@Component
class EmbeddingModelGate {

    /** Where the shipped default lives, where Spring AI's own auto-configuration reads it. */
    static final String CONFIGURED_DEFAULT_KEY = "spring.ai.ollama.embedding.options.model";

    private final ProfileStore profileStore;
    private final String configuredDefault;

    EmbeddingModelGate(
            ProfileStore profileStore,
            @Value("${" + CONFIGURED_DEFAULT_KEY + ":}") String configuredDefault) {
        this.profileStore = profileStore;
        this.configuredDefault = configuredDefault;
    }

    /** The model that will score, if any named — empty only while the gate stays closed. */
    Optional<String> modelName() {
        Profile profile = profileStore.load();
        if (profile.embeddingModel().isSet()) {
            return Optional.of(profile.embeddingModel().value());
        }
        if (configuredDefault == null || configuredDefault.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(configuredDefault.trim());
    }
}
