package io.algernon.vespera.similarity;

import java.util.OptionalInt;

/**
 * The statements of {@code similarity} that SQLite counts or that are timed, named so that {@link
 * SimilarityStatementProgress} can say which one it reports on (ADR-193 section 7, ADR-204 section 4). A
 * counted statement declares its steps a row here, beside its SQL, and a timed one declares none. Each
 * ratio is measured against the bundled SQLite by {@code StatementStepsPerRowTest}, and {@code
 * StatementStepsPerRowAreTheDeclaredOnesTest} is what holds each declaration here to that measurement.
 *
 * <p>The constants are in the order ADR-204 section 4 lists them: the build of {@code shingle_by_hash}, stage
 * 3's two reads, then the four reads of stage 4b's resolution. That is the order they are issued in, except
 * that the build stands first and stage 4b issues it after stage 3's two.
 */
public enum SimilarityStatement {

    /** {@link ShingleHashIndex#build}: 8 steps a row and one for each of the index's three columns. */
    SHINGLE_HASH_INDEX_BUILD(11),

    /** {@link DocumentFrequency}'s drain of stage 2's survivors, before its read of the shingle rows. Timed. */
    FREQUENCY_SURVIVORS,

    /** {@link DocumentFrequency}'s read of stage 2's shingle rows. */
    SHINGLE_ROWS(7),

    /** {@link RedundancyResolution}'s read of the occurrences a signature exists for under stage 4's run. */
    SIGNED_OCCURRENCES(5),

    /** {@link RedundancyResolution}'s read of the {@code signature_band} rows its candidate pairs come from. Timed. */
    SIGNATURE_BANDS,

    /** {@link RedundancyResolution}'s read of the extraction metrics of the occurrences in a near-duplicate component. Timed. */
    NEAR_DUPLICATE_METRICS,

    /** {@link RedundancyResolution}'s read of stage 3's shingle document frequencies, for containment. Timed. */
    DOCUMENT_FREQUENCY;

    private final OptionalInt stepsPerRow;

    SimilarityStatement() {
        this.stepsPerRow = OptionalInt.empty();
    }

    SimilarityStatement(int stepsPerRow) {
        this.stepsPerRow = OptionalInt.of(stepsPerRow);
    }

    /** Steps SQLite takes for each row the statement goes through; empty for a timed statement. */
    public OptionalInt stepsPerRow() {
        return stepsPerRow;
    }
}
