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
     * What the floor lets the step asked for do, for a run whose vectors carry {@code
     * currentEmbedderIdentity}.
     *
     * <p>The number is the one the scoring run is identified by ({@link RelevanceScoreFloorValue}), so
     * the number that removes is the number that names the run. A value that is not a number reads as
     * unset rather than failing the run: the profile is a file a person edits by hand, and ADR-047 says
     * an invocation ends having recorded what it learned (ADR-120).
     *
     * <p>The one read this makes, of the answers recorded for the seed set, is said under {@code
     * stage}, the name of the step that asked, {@code Stage 5e (relevance floor)} or {@code Stage 5
     * (relevance report)} (ADR-193, ADR-204 section 3). It is issued only where there is an identity, a
     * number and a seed set is named.
     */
    FloorReach reachFor(Optional<String> currentEmbedderIdentity, String stage) {
        Double value = RelevanceScoreFloorValue.readFrom(profileStore).value();
        return FloorReach.of(
                currentEmbedderIdentity,
                value == null ? OptionalDouble.empty() : OptionalDouble.of(value),
                () -> {
                    // The answers recorded for the seed set, none where no seed folder is named.
                    Optional<String> seedSet = seedSet();
                    if (seedSet.isEmpty()) {
                        return List.of();
                    }
                    // Timed: the read has no run, so no span (ADR-193 section 6).
                    return TimedStatement.of(
                            stage, "reading", "read", "the recorded answers",
                            () -> relevanceLabels.forSeedSet(seedSet.get()));
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
