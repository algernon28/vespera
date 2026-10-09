package io.algernon.vespera.similarity;

import java.util.OptionalInt;

/**
 * The statements of {@code similarity} that SQLite counts or that are timed, named so that {@link
 * SimilarityStatementProgress} can say which one it reports on (ADR-193 section 7, ADR-204 section 4). A
 * counted statement declares its steps a row here, beside its SQL, and a timed one declares none. Each
 * ratio is measured against the bundled SQLite by {@code StatementStepsPerRowTest}, and {@code
 * StatementStepsPerRowAreTheDeclaredOnesTest} is what holds each declaration here to that measurement,
 * except {@code SHINGLE_ROWS}, whose 45 is a ceiling held by {@code GroupingStepsPerRowTest}.
 *
 * <p>The constants are in the order ADR-204 section 4 lists them: the build of {@code shingle_by_hash}, stage
 * 3's grouping, then the four reads of stage 4b's resolution. That is the order they are issued in, except
 * that the build stands first and stage 4b issues it after stage 3's grouping.
 */
public enum SimilarityStatement {

    /** {@link ShingleHashIndex#build}: 8 steps a row and one for each of the index's three columns. */
    SHINGLE_HASH_INDEX_BUILD(11),

    /**
     * {@link DocumentFrequency}'s one grouping of stage 2's shingle rows, which sorts them in temporary files.
     * Its steps a row are not a constant: they depend on how the rows fall into hashes, and 19.06 to 44.0 were
     * measured on a connection that enforces foreign keys. So 45 is declared, a figure above the most
     * measured and not a measurement, so that a count of steps divided by it is never ahead of the rows done
     * and the share ends short of a hundred (ADR-211 section 9, held by {@code GroupingStepsPerRowTest}).
     */
    SHINGLE_ROWS(45),

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
