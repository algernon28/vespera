package io.algernon.vespera.pipeline;

import io.algernon.vespera.profile.NumericValue;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;

/**
 * {@code pipeline}'s reading of the profile's {@code relevanceScoreFloor} key, for {@link ScoringRun}'s
 * own identity (ADR-117) — {@code embedding} may depend only on {@code ledger} (ADR-040), so the
 * profile is read here rather than there, the same shape {@link DegenerateOutputConfidenceFloor}
 * already uses for stage 2's tier-2 key.
 *
 * <p><b>The value the step will act on, never the text the operator typed.</b> {@code null} where the
 * key ships unset, and {@code null} too where it holds something no number can be read from: {@link
 * RelevanceFloor} already treats an unreadable value as unset, and a run's identity has to agree with
 * the step that reads it, so two profiles that both mean "no threshold applied" must derive one run
 * however differently they are misspelt. {@code 0.30} and {@code 0.3} are one value for the same
 * reason — parsed, not compared as text.
 *
 * <p><b>Never the derived {@link RelevanceFloor.State}.</b> Whether a set floor turns out to be
 * calibrated on this run's own scale is a fact about the run's own output (the embedder identity its
 * vectors carry), and folding that into the run's identity would make the identity depend on what the
 * run itself produces. This record carries only the operator's number.
 *
 * <p><b>Read directly by {@link ScoringRun}'s own constructor, never as a bean of its own.</b>
 * {@code ScoringRun} is {@code @JobScope} precisely so a changed profile between invocations is seen;
 * a singleton bean handing out this value would cache whatever the profile said the first time it was
 * asked, which is the one thing ADR-117 exists to stop. A record is also final, so a scoped bean
 * wrapping one would need a CGLIB proxy that cannot be built over it — {@code extraction}'s own
 * {@code ExtractorIdentity} is the same shape for the same reason, deliberately not scoped either.
 */
record RelevanceScoreFloorValue(Double value) {

    /**
     * Reads today's profile value, fresh on every call.
     *
     * <p>Unreadable is unset, because that is what {@link RelevanceFloor} does with it (ADR-117): the
     * identity has to agree with the step, so two profiles that both mean "no threshold applied"
     * derive one run however differently they are misspelt. Since ADR-120 the agreement is structural
     * — both read the same {@link NumericValue.Reading} and both keep only {@link
     * NumericValue.Answered} — rather than two catches written to match.
     */
    static RelevanceScoreFloorValue readFrom(ProfileStore profileStore) {
        Profile profile = profileStore.load();
        return new RelevanceScoreFloorValue(
                profile.relevanceScoreFloor().reading() instanceof NumericValue.Answered answered
                        ? answered.number()
                        : null);
    }
}
