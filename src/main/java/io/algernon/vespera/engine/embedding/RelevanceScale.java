package io.algernon.vespera.engine.embedding;

/**
 * The scale a relevance score is on (ADR-088 section 3, amended by ADR-330 section 9): the embedder identity, and,
 * where the number of similarities a score is the mean of is not 3, {@code ;top:<n>} after it.
 *
 * <p>A relevance floor is a number on a scale, and changing the model or the mean's number changes the scale. The
 * answers recorded for a seed set carry the scale on screen when each was given ({@code generatedUnderEmbedder} in the
 * label file, the {@code embedder_identity} column of the answers), and {@link FloorReach}, {@link RelevanceLabels} and
 * {@link LoseNoDocumentationFloor} compare it with the run's. It is composed here, in {@code embedding}, because it
 * decides whether a floor removes (ADR-222).
 *
 * <p>The vector's own identity, which keys {@code vector}, is not this text and does not move: nothing is embedded
 * again for a number that changes no vector. At 3 the scale is the embedder identity byte for byte, so no answer
 * recorded before the number was a preset moves.
 */
public final class RelevanceScale {

    /** The number of similarities every score was the mean of before it was a preset, and the one that adds nothing. */
    private static final int THE_NUMBER_THAT_ADDS_NOTHING = 3;

    private RelevanceScale() {}

    /**
     * The scale of a score from vectors of {@code embedderIdentity}, the mean of the {@code topSimilarities} highest
     * similarities.
     */
    public static String of(String embedderIdentity, int topSimilarities) {
        return topSimilarities == THE_NUMBER_THAT_ADDS_NOTHING
                ? embedderIdentity
                : embedderIdentity + ";top:" + topSimilarities;
    }
}
