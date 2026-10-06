package io.algernon.vespera.pipeline;

import io.algernon.vespera.embedding.EmbeddingStatement;
import io.algernon.vespera.embedding.EmbeddingStatementProgress;
import io.algernon.vespera.extraction.ExtractionStatement;
import io.algernon.vespera.extraction.ExtractionStatementProgress;
import io.algernon.vespera.similarity.SimilarityStatement;
import io.algernon.vespera.similarity.SimilarityStatementProgress;
import io.algernon.vespera.synthesis.SynthesisStatement;
import io.algernon.vespera.synthesis.SynthesisStatementProgress;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Writes the lines of the statements a capability module announces through the callbacks it owns (ADR-193
 * sections 4 and 7, ADR-199 sections 3 and 4), in the words the caller gives each. It is every module's
 * {@code <Module>StatementProgress} at once, since each interface's methods take that module's own enum and
 * so do not collide; a caller hands it where a module asks for its interface, or, where the module's
 * interface also reports a loop, forwards the three statement callbacks to it. A statement it was given no
 * words for is ignored, which is how stage 3 leaves its read of the shingle rows to ADR-191's own lines.
 *
 * <p>What it writes, for a statement started and ended:
 *
 * <ul>
 *   <li><b>A timed one</b> (declaring no steps a row): {@code <stage> is reading <what>} and {@code <stage>
 *       read <what> in <S> s}, always.
 *   <li><b>A counted one started with a total</b>: {@code <stage> is reading <what>, over up to <N> rows},
 *       then {@link StatementProgress}' lines under the caller's label as SQLite calls back, then {@code
 *       <stage> read <what> in <S> s}.
 *   <li><b>A counted one started with an empty total</b>, a run that holds no row: nothing at all, neither
 *       line and no progress. That silence is {@code pipeline}'s to keep (ADR-199 section 4); the capability
 *       module only reports that it started.
 * </ul>
 *
 * <p>A statement that throws is never ended, so it leaves its first line and no second, as a timed
 * statement does. Statements do not nest, so one statement is held at a time. Not thread-safe: SQLite calls
 * back on the statement's own thread, and a step runs on one.
 */
final class ReportedStatements
        implements ExtractionStatementProgress,
                SimilarityStatementProgress,
                EmbeddingStatementProgress,
                SynthesisStatementProgress {

    /** What a statement is called in the lines, and for a counted one the label its progress lines carry. */
    private record Words(String stage, String what, String label) {}

    private final Map<Enum<?>, Words> words;

    private TimedStatement lines;
    private StatementProgress progress;

    private ReportedStatements(Map<Enum<?>, Words> words) {
        this.words = words;
    }

    /** Starts the words of one caller. */
    static Builder saying() {
        return new Builder();
    }

    /** The words of each statement one caller reports, in the order it adds them. */
    static final class Builder {

        private final Map<Enum<?>, Words> words = new HashMap<>();

        /** A timed statement: {@code <stage> is reading <what>}, {@code <stage> read <what> in <S> s}. */
        Builder timed(Enum<?> statement, String stage, String what) {
            words.put(statement, new Words(stage, what, null));
            return this;
        }

        /** A counted statement, whose progress lines carry {@code label} (ADR-193 section 4.1). */
        Builder counted(Enum<?> statement, String stage, String what, String label) {
            words.put(statement, new Words(stage, what, label));
            return this;
        }

        ReportedStatements build() {
            return new ReportedStatements(Map.copyOf(words));
        }
    }

    @Override
    public void statementStarting(ExtractionStatement statement, OptionalLong rowsUpTo) {
        starting(statement, statement.stepsPerRow(), rowsUpTo);
    }

    @Override
    public void stepsTaken(ExtractionStatement statement, long steps) {
        stepsTaken(steps);
    }

    @Override
    public void statementEnded(ExtractionStatement statement) {
        ended();
    }

    @Override
    public void statementStarting(SimilarityStatement statement, OptionalLong rowsUpTo) {
        starting(statement, statement.stepsPerRow(), rowsUpTo);
    }

    @Override
    public void stepsTaken(SimilarityStatement statement, long steps) {
        stepsTaken(steps);
    }

    @Override
    public void statementEnded(SimilarityStatement statement) {
        ended();
    }

    @Override
    public void statementStarting(EmbeddingStatement statement, OptionalLong rowsUpTo) {
        starting(statement, statement.stepsPerRow(), rowsUpTo);
    }

    @Override
    public void stepsTaken(EmbeddingStatement statement, long steps) {
        stepsTaken(steps);
    }

    @Override
    public void statementEnded(EmbeddingStatement statement) {
        ended();
    }

    @Override
    public void statementStarting(SynthesisStatement statement, OptionalLong rowsUpTo) {
        starting(statement, statement.stepsPerRow(), rowsUpTo);
    }

    @Override
    public void stepsTaken(SynthesisStatement statement, long steps) {
        stepsTaken(steps);
    }

    @Override
    public void statementEnded(SynthesisStatement statement) {
        ended();
    }

    private void starting(Enum<?> statement, OptionalInt stepsPerRow, OptionalLong rowsUpTo) {
        lines = null;
        progress = null;
        Words said = words.get(statement);
        if (said == null) {
            return;
        }
        if (stepsPerRow.isEmpty()) {
            lines = TimedStatement.readStarted(said.stage(), said.what());
        } else if (rowsUpTo.isPresent()) {
            lines = TimedStatement.readStartedOver(said.stage(), said.what(), rowsUpTo.getAsLong());
            progress = StatementProgress.ofRead(said.label(), rowsUpTo.getAsLong(), stepsPerRow.getAsInt());
        }
    }

    private void stepsTaken(long steps) {
        if (progress != null) {
            progress.stepsTaken(steps);
        }
    }

    private void ended() {
        if (lines != null) {
            lines.ended();
        }
        lines = null;
        progress = null;
    }
}
