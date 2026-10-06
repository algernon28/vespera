package io.algernon.vespera.embedding;

import java.util.OptionalInt;

/**
 * The statements of {@code embedding} that SQLite counts or that are timed, named so that {@link
 * EmbeddingStatementProgress} can say which one it reports on (ADR-193 section 7, ADR-199 section 4). A
 * counted statement declares its steps a row here, beside its SQL, and a timed one declares none. Each
 * ratio is measured against the bundled SQLite by {@code StatementStepsPerRowTest}, and {@code
 * StatementStepsPerRowAreTheDeclaredOnesTest} is what holds each declaration here to that measurement.
 *
 * <p>The constants are the five statements of {@link SeedCorpusComparison#measure}, in the order it issues
 * them.
 */
public enum EmbeddingStatement {

    /** The drain of the survivors under the measurement run, the corpus side of the comparison. Timed. */
    CORPUS_SURVIVORS,

    /** The drain of the seed walk's occurrences, the seed side of the comparison. Timed. */
    SEED_OCCURRENCES,

    /** The read of the {@code unusable_seed} rows under the measurement run. */
    UNUSABLE_SEEDS(5),

    /** The read of the {@code extraction_metric} rows under stage 2's run, the corpus side's measurements. */
    CORPUS_METRICS(12),

    /** The read of the {@code extraction_metric} rows under the measurement run, the seeds' measurements. */
    SEED_METRICS(12);

    private final OptionalInt stepsPerRow;

    EmbeddingStatement() {
        this.stepsPerRow = OptionalInt.empty();
    }

    EmbeddingStatement(int stepsPerRow) {
        this.stepsPerRow = OptionalInt.of(stepsPerRow);
    }

    /** Steps SQLite takes for each row the statement goes through; empty for a timed statement. */
    public OptionalInt stepsPerRow() {
        return stepsPerRow;
    }
}
