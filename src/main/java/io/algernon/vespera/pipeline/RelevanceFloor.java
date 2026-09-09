package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.embedding.RelevanceLabel;
import io.algernon.vespera.embedding.RelevanceLabels;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * What stage 5 is entitled to do with the relevance threshold (ADR-088, #112) — three states, only
 * one of which removes anything.
 *
 * <p><b>This is not a gate, and must not be built as one.</b> ADR-080's gate stops stage 4 because a
 * boilerplate floor is an <em>input</em> that stage cannot work without. The relevance threshold is
 * the opposite: the run is what produces the data the threshold is calibrated from, so gating on it
 * would mean never producing the report that lets anyone set it. The run proceeds in all three
 * states; what changes is only whether a verdict is written.
 *
 * <p><b>A threshold is a number on a scale, and the model is the scale.</b> A floor read off one
 * model's scores says nothing about another model's, so applying it after the model changed would
 * write removals against a distribution it was never calibrated on. Which scale a floor belongs to
 * is not something the profile records — and deliberately is not going to be, since that would be a
 * second thing an operator has to keep in step by hand. It is read instead from the labels the
 * number is meant to have been read off: {@code relevance_label} rows carry the embedder identity
 * that was on screen when the answer was given (ADR-088), which is exactly the scale the judgement
 * was made against.
 *
 * <p><b>A floor with no labels behind it is applied.</b> ADR-088 is explicit that nothing checks a
 * threshold was ever labelled — provenance is free text and that is what provenance is for (ADR-031)
 * — so an unlabelled floor is an ordinary operator-supplied number, and there is no recorded scale
 * for it to disagree with. What is refused is not "a floor nobody calibrated" but "a floor whose
 * recorded calibration was against a different model".
 */
@Component
class RelevanceFloor {

    private final ProfileStore profileStore;
    private final RelevanceLabels relevanceLabels;

    RelevanceFloor(ProfileStore profileStore, RelevanceLabels relevanceLabels) {
        this.profileStore = profileStore;
        this.relevanceLabels = relevanceLabels;
    }

    /** What the floor entitles this run to do. */
    sealed interface State {

        /** Nothing is removed, whatever the reason — the two non-judging states share this answer. */
        default boolean removesAnything() {
            return this instanceof Applicable;
        }
    }

    /** Nobody has answered the key. Stage 5 scores, clusters and reports, and removes nothing. */
    record Unset() implements State {}

    /**
     * A number is set, and the labels it was read off were given against another model's scores — or
     * against several models', which is the same answer for the same reason. Treated identically to
     * {@link Unset} — but the report says so, because a value silently ignored is worse than one that
     * was never set.
     *
     * @param calibratedUnder the embedder identity those labels carry, or all of them where they
     *     disagree, so the report can name what the number was actually read off
     */
    record CalibratedElsewhere(double value, String calibratedUnder, String currentIdentity) implements State {}

    /** A number set on this run's own scale. The only state that writes a verdict. */
    record Applicable(double value) implements State {}

    /**
     * The floor's state for a run whose vectors carry {@code currentEmbedderIdentity}.
     *
     * <p>A value that is not a number reads as unset rather than failing the run: the profile is a
     * file a person edits by hand, ADR-047 says an invocation ends having recorded what it learned,
     * and a typo in one key is not a reason to lose a whole scoring pass.
     */
    State stateFor(String currentEmbedderIdentity) {
        Profile profile = profileStore.load();
        if (!profile.relevanceScoreFloor().isSet()) {
            return new Unset();
        }
        double value;
        try {
            value = Double.parseDouble(profile.relevanceScoreFloor().value().trim());
        } catch (NumberFormatException notANumber) {
            return new Unset();
        }
        List<String> calibratedUnder = calibratedUnder();
        if (calibratedUnder.isEmpty() || calibratedUnder.equals(List.of(currentEmbedderIdentity))) {
            return new Applicable(value);
        }
        return new CalibratedElsewhere(value, String.join(", ", calibratedUnder), currentEmbedderIdentity);
    }

    /**
     * The embedder identities the answers for the named seed set were given against, in occurrence
     * order and without repetition.
     *
     * <p>Empty where nobody has answered anything, which is the one case that lets a floor apply
     * unchallenged: there is no recorded scale for it to disagree with, and ADR-088 is explicit that
     * nothing checks a threshold was ever labelled.
     *
     * <p><b>More than one is not the same as none.</b> A label set spanning two models is a
     * half-re-ingested pass, and it is evidence that some of the calibration was done on a scale this
     * run is not using. Reading that as "no recorded scale" and applying the floor would delete
     * archive on an ambiguity, which is the asymmetric failure ADR-042 names: an over-block loses an
     * archive invisibly, while an under-block leaves a document to be removed by a later, better
     * informed run.
     */
    private List<String> calibratedUnder() {
        Optional<String> seedSet = seedSet();
        if (seedSet.isEmpty()) {
            return List.of();
        }
        return relevanceLabels.forSeedSet(seedSet.get()).stream()
                .map(RelevanceLabel::embedderIdentity)
                .distinct()
                .toList();
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
