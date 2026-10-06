package io.algernon.vespera.similarity;

import io.algernon.vespera.ledger.StatementSteps;
import java.sql.Statement;
import java.time.Duration;
import java.util.OptionalLong;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code shingle_by_hash}, the one index on {@code shingle} that containment retrieval reads and that
 * stage 2 must not maintain (ADR-182). {@code similarity} owns {@code shingle} and its indexes (ADR-041),
 * so the two statements that drop and build this one are written here, once, beside the table. {@code
 * pipeline} calls them at the two points ADR-182 §2.2 and §2.3 name; this class knows no stage.
 *
 * <p><b>The index exists only from the build until the next drop.</b> {@code schema.sql} does not create
 * it, because it runs at every start (ADR-173 §3) and would build the whole index during start-up only
 * for the next stage 2 to drop it. Row by row, at random places in an index far larger than any page
 * cache, it cost a stage-2 chunk 3.9 s against 96 ms without it (ADR-182 Context). Built once over
 * every row the table then holds, a one-column index took 15 seconds over 20,000,000 synthetic rows written
 * in order on a USB spinning disk (ADR-193's probe), and this one took 39 minutes over the 42,833,917 rows
 * of the archive's own {@code shingle} table on the same kind of disk, rows that were neither new nor in
 * order: the worst measured (ADR-193 section 4.2). What it costs elsewhere is not known, so no rate is
 * stated here.
 *
 * <p>Both statements are decided from what {@code sqlite_master} holds at the moment, through {@code IF
 * EXISTS} and {@code IF NOT EXISTS}, never from a record of what an earlier invocation did (ADR-182 §3).
 * Neither is run inside a transaction of the caller's: a drop inside a chunk that rolled back would be
 * restored, and a build inside the resolution's transaction would be undone with it (ADR-182 §2.2, §2.3).
 */
public class ShingleHashIndex {

    /** The index's name, unchanged since ADR-081. */
    static final String NAME = "shingle_by_hash";

    private static final String DROP = "DROP INDEX IF EXISTS " + NAME;

    private static final String BUILD =
            "CREATE INDEX IF NOT EXISTS " + NAME + " ON shingle (run_id, shingle_parameter_identity, shingle_hash)";

    private final JdbcTemplate jdbcTemplate;

    public ShingleHashIndex(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Removes the index if it is there, for a stage 2 that is about to write shingles (ADR-182 §2.2).
     * Removes no row. Costs a read of every page of the index, which SQLite puts on its free list, and
     * says nothing.
     */
    public void drop() {
        jdbcTemplate.execute(DROP);
    }

    /** Whether the index is there at this moment, as {@code sqlite_master} holds it. */
    public boolean exists() {
        Long found = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND tbl_name = 'shingle' AND name = ?",
                Long.class,
                NAME);
        return found != null && found > 0;
    }

    /**
     * An upper bound on the rows {@code shingle} holds: its greatest rowid, which SQLite answers without
     * reading a row. {@code COUNT(*)} would read a whole index of the largest table in the database before
     * the build read it again. Exact unless rows have been deleted (ADR-182 §2.4).
     */
    public long shingleRowsUpTo() {
        Long greatest = jdbcTemplate.queryForObject("SELECT MAX(rowid) FROM shingle", Long.class);
        return greatest == null ? 0 : greatest;
    }

    /**
     * Builds the index over every row the table holds, and returns the wall clock of that one statement
     * (ADR-182 §2.4). A no-op, timed all the same, where the index is already there: {@link #exists}
     * is how a caller decides whether there is anything to announce.
     */
    public Duration build() {
        return build(SimilarityStatementProgress.NONE);
    }

    /**
     * As {@link #build()}, and tells {@code progress} the table's {@link #shingleRowsUpTo} once before the
     * statement, the steps SQLite has taken at each of its callbacks while it runs, and that it has ended
     * (ADR-193 sections 2 and 7). The statement runs on one connection, outside any transaction of the
     * caller's, with the progress handler set on it and cleared after.
     */
    public Duration build(SimilarityStatementProgress progress) {
        progress.statementStarting(SimilarityStatement.SHINGLE_HASH_INDEX_BUILD, OptionalLong.of(shingleRowsUpTo()));
        long started = System.nanoTime();
        StatementSteps.counted(
                jdbcTemplate,
                steps -> progress.stepsTaken(SimilarityStatement.SHINGLE_HASH_INDEX_BUILD, steps),
                connection -> {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute(BUILD);
                    }
                    return null;
                });
        Duration took = Duration.ofNanos(System.nanoTime() - started);
        progress.statementEnded(SimilarityStatement.SHINGLE_HASH_INDEX_BUILD);
        return took;
    }
}
