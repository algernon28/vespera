package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.embedding.FloorReach;
import io.algernon.vespera.embedding.RelevanceLabels;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.springframework.stereotype.Component;

/**
 * The wiring that asks {@code embedding} what the relevance threshold lets stage 5e do (ADR-088,
 * #112, ADR-226): it hands {@link FloorReach} the floor's number, the embedder identity and a read of
 * the recorded answers, and decides nothing itself.
 *
 * <p><b>This is not a gate, and must not be built as one.</b> ADR-080's gate stops stage 4 because a
 * boilerplate floor is an <em>input</em> that stage cannot work without. The relevance threshold is
 * the opposite: the run is what produces the data the threshold is calibrated from, so gating on it
 * would mean never producing the report that lets anyone set it. The run proceeds whatever the floor
 * lets it do; what changes is only whether a verdict is written.
 *
 * <p><b>A threshold is a number on a scale, and the model is the scale.</b> Which scale a floor
 * belongs to is not something the profile records, since that would be a second thing an operator has
 * to keep in step by hand. It is read instead from the labels the number is meant to have been read
 * off: {@code relevance_label} rows carry the embedder identity that was on screen when the answer was
 * given (ADR-088), and {@link FloorReach} compares them with the run's.
 */
@Component
class RelevanceFloor {

    private final ProfileStore profileStore;
    private final RelevanceLabels relevanceLabels;

    RelevanceFloor(ProfileStore profileStore, RelevanceLabels relevanceLabels) {
        this.profileStore = profileStore;
        this.relevanceLabels = relevanceLabels;
    }

    /**
     * What the floor lets the step asked for do, for the scoring run whose vectors carry {@code
     * currentEmbedderIdentity}, by {@code floor}, the number that run was minted under and not the profile's
     * now, which an operator may have saved since (ADR-117, ADR-228). A value in the profile that is not a
     * number was minted as unset ({@link RelevanceScoreFloorValue}), so it removes nothing rather than failing
     * the run (ADR-120).
     *
     * <p>The answers read are those {@link RelevanceLabels#forSeedSet(String, String)} gives for that identity,
     * so that answers given under the run's scale say which scale the number is on (ADR-244, ADR-228 section 5,
     * ADR-088 section 3). That one read is said under {@code stage}, the name of the step that asked, {@code
     * Stage 5e (relevance floor)} or {@code Stage 5 (relevance report)} (ADR-193, ADR-204 section 3). It is
     * issued only where there is an identity, a number and a seed set is named.
     */
    FloorReach reachFor(Optional<String> currentEmbedderIdentity, String stage, OptionalDouble floor) {
        return FloorReach.of(
                currentEmbedderIdentity,
                floor,
                () -> {
                    // The answers recorded for the seed set, none where no seed folder is named.
                    Optional<String> seedSet = seedSet();
                    if (seedSet.isEmpty()) {
                        return List.of();
                    }
                    // Timed: the read has no run, so no span (ADR-193 section 6).
                    return TimedStatement.of(
                            stage, "reading", "read", "the recorded answers",
                            () -> relevanceLabels.forSeedSet(seedSet.get(), currentEmbedderIdentity.orElseThrow()));
                });
    }

    /** The seed folder the answers are about, canonicalised the way every other reader of it is. */
    private Optional<String> seedSet() {
        Profile profile = profileStore.load();
        if (!profile.seedFolder().isSet()) {
            return Optional.empty();
        }
        return Optional.of(Walk.canonicalRoot(Path.of(profile.seedFolder().value())).toString());
    }
}
