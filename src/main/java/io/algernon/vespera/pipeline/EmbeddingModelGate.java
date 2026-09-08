package io.algernon.vespera.pipeline;

import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Gate 3 (ADR-084, #107): whether an operator has named an embedding model. {@code embedding} may
 * depend only on {@code ledger} (ADR-040), so the profile is read here rather than there, the same
 * shape {@link SeedGate} and {@link RedundancyGate} already use for their own keys.
 *
 * <p>Checked before {@code ScoringRun} mints a row, because a scoring run that did nothing should
 * not exist in the {@code run} table (ADR-080's rule, applied a third time).
 */
@Component
class EmbeddingModelGate {

    private final ProfileStore profileStore;

    EmbeddingModelGate(ProfileStore profileStore) {
        this.profileStore = profileStore;
    }

    /** The model an operator has named, if any — empty while the gate stays closed. */
    Optional<String> modelName() {
        Profile profile = profileStore.load();
        if (!profile.embeddingModel().isSet()) {
            return Optional.empty();
        }
        return Optional.of(profile.embeddingModel().value());
    }
}
