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
 * 3's grouping, then the two reads of stage 4b's resolution (ADR-220 section 4). That is the order they are issued in, except
 * that the build stands first and stage 4b issues it after stage 3's grouping.
 */
public enum SimilarityStatement {

    /**
     * {@link ShingleHashIndex#buildFor}: 12 steps for a row of the run the index is built for, the most a row
     * takes, and 3 for a row of any other run (ADR-221 section 5).
     */
    SHINGLE_HASH_INDEX_BUILD(12),

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

    /** {@link RedundancyResolution}'s read of the extraction metrics of the occurrences in a near-duplicate component. Timed. */
    NEAR_DUPLICATE_METRICS;

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
