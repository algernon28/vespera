package io.algernon.vespera.similarity;

import java.util.OptionalInt;

/**
 * The statements of {@code similarity} that SQLite counts or that are timed, named so that {@link
 * SimilarityStatementProgress} can say which one it reports on (ADR-193 section 7). A counted statement
 * declares its steps a row here, beside its SQL, and a timed one declares none. Each ratio is pinned
 * against the bundled SQLite by {@code StatementStepsPerRowTest}.
 */
public enum SimilarityStatement {

    /** {@link ShingleHashIndex#build}: 8 steps a row and one for each of the index's three columns. */
    SHINGLE_HASH_INDEX_BUILD(11),

    /** {@link DocumentFrequency}'s read of stage 2's shingle rows. */
    SHINGLE_ROWS(7);

    private final OptionalInt stepsPerRow;

    SimilarityStatement(int stepsPerRow) {
        this.stepsPerRow = OptionalInt.of(stepsPerRow);
    }

    /** Steps SQLite takes for each row the statement goes through; empty for a timed statement. */
    public OptionalInt stepsPerRow() {
        return stepsPerRow;
    }
}
