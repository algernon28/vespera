package io.algernon.vespera.similarity;

/**
 * What {@link RedundancyResolution#resolve} tells its caller about its loops, as it goes (ADR-192 section
 * 5). {@code similarity} knows no stage and writes no line: the caller owns every counter.
 *
 * <p>Each {@code to...} method is called exactly once each time its loop is reached, with the loop's total,
 * before its first item, zero included; its completion method once after each item. A resolution that
 * returns before any loop is reached (no occurrence is signed) calls none of them.
 *
 * <p>The order is fixed: candidates (the signed occurrences, counted as their pairs are scored), profiles,
 * then components and verdicts (both before the component loop), then containment. Within a component its verdicts are reported before the component is. Containment
 * candidates have no total: they are reported inside the containment loop, after it is announced.
 *
 * <p>It is also a {@link SimilarityStatementProgress}, and is told about two reads among those loops
 * (ADR-193 section 7, ADR-204 section 4, ADR-220 section 4): the signed occurrences ({@code
 * SIGNED_OCCURRENCES}, counted), first of all, before any loop and even where nothing is signed, which is
 * where a resolution that returns before its loops stops reporting; and the near-duplicates' extraction
 * metrics, after the profiles are announced and only where a component holds a member. The signature bands
 * and the shingle document frequencies are no reads of their own: each is a statement a page of signed
 * occurrences or an occurrence, inside a loop that reports.
 */
public interface ResolutionProgress extends SimilarityStatementProgress {

    /** A progress that does nothing, for the callers that want no report. */
    ResolutionProgress NONE = new ResolutionProgress() {
        @Override
        public void toScorePairs(long signedOccurrences) {}

        @Override
        public void candidatesScored() {}

        @Override
        public void toReadProfiles(long occurrences) {}

        @Override
        public void profileRead() {}

        @Override
        public void toResolveComponents(long components) {}

        @Override
        public void componentResolved() {}

        @Override
        public void toWriteNearDuplicateVerdicts(long members) {}

        @Override
        public void nearDuplicateVerdictWritten() {}

        @Override
        public void toCheckForContainment(long occurrences) {}

        @Override
        public void checkedForContainment() {}

        @Override
        public void containmentCandidateGoneThrough() {}
    };

    /**
     * The signed occurrences, whose candidate pairs are scored a page of them at a time. How many pairs there
     * are is known only once the last page has been read, so the loop counts signed occurrences (ADR-220
     * section 4).
     */
    void toScorePairs(long signedOccurrences);

    /**
     * One signed occurrence whose candidate pairs, as their lesser member, have been scored. Told once for
     * each occurrence of a page, after every pair of the page has been scored, so a page's thousand are told
     * together.
     */
    void candidatesScored();

    /** The members of components of two or more. */
    void toReadProfiles(long occurrences);

    /** One occurrence's facts read for the survivor rule. */
    void profileRead();

    /** The components of two or more. */
    void toResolveComponents(long components);

    /** One component resolved. */
    void componentResolved();

    /** The members of those components less one survivor each, summed. */
    void toWriteNearDuplicateVerdicts(long members);

    /** One member written as redundant with its component's survivor. */
    void nearDuplicateVerdictWritten();

    /** The signed occurrences. */
    void toCheckForContainment(long occurrences);

    /** One signed occurrence checked for a container, on every path out of it. */
    void checkedForContainment();

    /** One containment candidate gone through, for any signed occurrence. Has no total. */
    void containmentCandidateGoneThrough();
}
