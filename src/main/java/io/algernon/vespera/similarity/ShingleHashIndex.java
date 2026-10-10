package io.algernon.vespera.similarity;

import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.StatementSteps;
import java.sql.Statement;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code shingle_by_hash}, the one index on {@code shingle} that containment retrieval reads and that
 * stage 2 must not maintain (ADR-182). {@code similarity} owns {@code shingle} and its indexes (ADR-041),
 * so the statements that drop and build this one are written here, once, beside the table. {@code
 * pipeline} calls them at the two points ADR-182 §2.2 and §2.3 name; this class knows no stage.
 *
 * <p><b>The index exists only from the build until the next drop, and is over the rows of one stage-2
 * run</b> (ADR-221 §1): {@code (shingle_parameter_identity, shingle_hash) WHERE run_id = '<the run>'}.
 * There is one index of that name in a database. {@code schema.sql} does not create it, because it runs
 * at every start (ADR-173 §3) and would build the index during start-up only for the next stage 2 to drop
 * it. Row by row, at random places in an index far larger than any page cache, it cost a stage-2 chunk
 * 3.9 s against 96 ms without it (ADR-182 Context).
 *
 * <p>{@link #buildFor} decides from what {@code sqlite_master} holds at the moment, never from a record
 * of what an earlier invocation did (ADR-182 §3): the index is the run's only where its statement equals,
 * character for character, the one written for that run, and is otherwise dropped and built again
 * (ADR-221 §3). Neither it nor the drop is run inside a transaction of the caller's: a drop inside a
 * chunk that rolled back would be restored, and a build inside the resolution's transaction would be
 * undone with it (ADR-182 §2.2, §2.3).
 */
public class ShingleHashIndex {

    /** The index's name, unchanged since ADR-081. */
    static final String NAME = "shingle_by_hash";

    private static final String DROP = "DROP INDEX IF EXISTS " + NAME;

    /** The form {@code RunId.of} mints (ADR-221 §4); the only value ever joined into the build's text. */
    private static final Pattern A_MINTED_RUN_ID = Pattern.compile("[0-9a-f]{64}");

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
     * Makes the index the one over the rows of {@code stage2RunId} alone, and returns the wall clock from
     * before the drop to the end of the build; empty, having told {@code progress} nothing, where the index
     * already is that run's (ADR-221 §3).
     *
     * <p>Refuses with an {@link IllegalArgumentException}, before reading or issuing anything, an id that is
     * not 64 lowercase hexadecimal characters: SQLite takes no bound value in an index's {@code WHERE}, so
     * the id is written into the statement's text and nothing else is (ADR-221 §4). Otherwise tells {@code
     * progress} the table's {@link #shingleRowsUpTo} once, drops any index of the name outside the counted
     * statement, runs the build on one connection outside any transaction of the caller's with the
     * progress handler set on it and cleared after, telling {@code progress} the steps taken at each
     * callback, and that it has ended (ADR-193 sections 2 and 7).
     */
    public Optional<Duration> buildFor(RunId stage2RunId, SimilarityStatementProgress progress) {
        if (!A_MINTED_RUN_ID.matcher(stage2RunId.value()).matches()) {
            throw new IllegalArgumentException("A stage-2 run id is 64 lowercase hexadecimal characters");
        }
        String build = "CREATE INDEX " + NAME + " ON shingle (shingle_parameter_identity, shingle_hash)"
                + " WHERE run_id = '" + stage2RunId.value() + "'";
        boolean theRuns = jdbcTemplate
                .queryForList(
                        "SELECT sql FROM sqlite_master WHERE type = 'index' AND tbl_name = 'shingle' AND name = ?",
                        String.class,
                        NAME)
                .contains(build);
        if (theRuns) {
            return Optional.empty();
        }
        progress.statementStarting(SimilarityStatement.SHINGLE_HASH_INDEX_BUILD, OptionalLong.of(shingleRowsUpTo()));
        long started = System.nanoTime();
        drop();
        StatementSteps.counted(
                jdbcTemplate,
                steps -> progress.stepsTaken(SimilarityStatement.SHINGLE_HASH_INDEX_BUILD, steps),
                connection -> {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute(build);
                    }
                    return null;
                });
        Duration took = Duration.ofNanos(System.nanoTime() - started);
        progress.statementEnded(SimilarityStatement.SHINGLE_HASH_INDEX_BUILD);
        return Optional.of(took);
    }
}
