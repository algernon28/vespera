package io.algernon.vespera.pipeline;

import io.algernon.vespera.profile.NumericValue;
import io.algernon.vespera.profile.ProfileStore;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Stage 4's own gate (ADR-080): whether an operator has answered {@code
 * Profile.boilerplateDocumentFrequencyFloor}. {@code similarity} may depend only on {@code ledger}
 * (ADR-040), so the profile is read here rather than there, the same shape {@link
 * DegenerateOutputConfidenceFloor} already uses for stage 2's tier-2 key.
 *
 * <p>Checked before anything else stage 4 does — before {@link StageRuns#contentRedundancy} mints a row, before a
 * signature is computed — because a stage-4 run that did nothing should not exist in the {@code run}
 * table (ADR-080's own wording).
 */
@Component
class RedundancyGate {

    private final ProfileStore profileStore;

    RedundancyGate(ProfileStore profileStore) {
        this.profileStore = profileStore;
    }

    /**
     * The key as the profile holds it, which is the one read of {@code profile.yaml} this gate makes.
     *
     * <p>Both the decision and the sentence come from this one value (ADR-120), which is why the caller
     * holds it and passes it to {@link #floorOf} rather than asking this gate twice. Loading once
     * matters rather than merely tidying: {@code ProfileStore.load} re-reads the file on every call, so
     * a gate asked twice could shut on one reading and explain itself from another, and the explanation
     * is the only thing the operator gets.
     */
    NumericValue value() {
        return profileStore.load().boilerplateDocumentFrequencyFloor();
    }

    /**
     * The floor, if an operator has set a readable one — empty while the gate stays closed.
     *
     * <p><b>An unreadable value leaves the gate shut rather than ending the invocation</b> (ADR-120).
     * Until then this called {@code Double.valueOf} with no catch and no trim, so a floor of {@code
     * "0,4"} — or of {@code " 0.4"} — took the whole invocation down mid-run with a
     * {@code NumberFormatException}, while the same mistake in the relevance floor was quietly
     * ignored. A shut gate is what ADR-080 already says this state is, and the operator is told which
     * value was ignored, both by this gate's own line and by the closing one.
     */
    Optional<Double> floor() {
        return floorOf(value());
    }

    /**
     * The number in {@code floor}, where one can be read from it — static so a caller that already
     * holds the value can ask what it means without a second read.
     */
    static Optional<Double> floorOf(NumericValue floor) {
        return floor.reading() instanceof NumericValue.Answered answered
                ? Optional.of(answered.number())
                : Optional.empty();
    }
}
