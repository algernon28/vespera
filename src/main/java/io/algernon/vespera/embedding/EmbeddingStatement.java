package io.algernon.vespera.embedding;

import java.util.OptionalInt;

/**
 * The statements of {@code embedding} that SQLite counts or that are timed, named so that {@link
 * EmbeddingStatementProgress} can say which one it reports on (ADR-193 section 7, ADR-204 section 4). A
 * counted statement declares its steps a row here, beside its SQL, and one that declares none is timed, or
 * is a read made a page of survivors at a time and reported by the rows read (ADR-211 section 9). Each ratio
 * is measured against the bundled SQLite by {@code StatementStepsPerRowTest}, and {@code
 * StatementStepsPerRowAreTheDeclaredOnesTest} is what holds each declaration here to that measurement.
 *
 * <p>The first five constants are the statements of {@link SeedCorpusComparison#measure}, in the order it
 * issues them; the last two are stage 6b's, which makes those reads itself.
 */
public enum EmbeddingStatement {

    /** The drain of the seed walk's occurrences, the seed side of the comparison. Timed. */
    SEED_OCCURRENCES,

    /** The read of the {@code unusable_seed} rows under the measurement run. */
    UNUSABLE_SEEDS(5),

    /**
     * The first read of the {@code extraction_metric} rows under stage 2's run, the corpus side's
     * measurements, made a page of the corpus survivors at a time.
     */
    CORPUS_METRICS,

    /** Each later read of the same rows, which the corpus side's exact quartiles may need (ADR-211 section 4). */
    CORPUS_METRICS_AGAIN,

    /** The read of the {@code extraction_metric} rows under the measurement run, the seeds' measurements. */
    SEED_METRICS(12),

    /**
     * Stage 6b's read of every arranged occurrence, a page at a time through {@link DocumentClusters#eachPage},
     * for the furniture rule's first pass over the tree (ADR-223 section 8). Told by the caller, which makes
     * the read.
     */
    ARRANGED_OCCURRENCES_FOR_PICTURES,

    /** The same read, made again for the manifest after the tree's last partition (ADR-223 section 8). */
    ARRANGED_OCCURRENCES_FOR_THE_MANIFEST;

    private final OptionalInt stepsPerRow;

    EmbeddingStatement() {
        this.stepsPerRow = OptionalInt.empty();
    }

    EmbeddingStatement(int stepsPerRow) {
        this.stepsPerRow = OptionalInt.of(stepsPerRow);
    }

    /** Steps SQLite takes for each row the statement goes through; empty for one not counted by steps. */
    public OptionalInt stepsPerRow() {
        return stepsPerRow;
    }
}
