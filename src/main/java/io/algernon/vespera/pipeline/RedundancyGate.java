package io.algernon.vespera.pipeline;

import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Stage 4's own gate (ADR-080): whether an operator has answered {@code
 * Profile.boilerplateDocumentFrequencyFloor}. {@code similarity} may depend only on {@code ledger}
 * (ADR-040), so the profile is read here rather than there, the same shape {@link
 * DegenerateOutputConfidenceFloor} already uses for stage 2's tier-2 key.
 *
 * <p>Checked before anything else stage 4 does — before {@link RedundancyRun} mints a row, before a
 * signature is computed — because a stage-4 run that did nothing should not exist in the {@code run}
 * table (ADR-080's own wording).
 */
@Component
class RedundancyGate {

    private final ProfileStore profileStore;

    RedundancyGate(ProfileStore profileStore) {
        this.profileStore = profileStore;
    }

    /** The floor, if an operator has set one — empty while the gate stays closed. */
    Optional<Double> floor() {
        Profile profile = profileStore.load();
        if (!profile.boilerplateDocumentFrequencyFloor().isSet()) {
            return Optional.empty();
        }
        return Optional.of(Double.valueOf(profile.boilerplateDocumentFrequencyFloor().value()));
    }
}
