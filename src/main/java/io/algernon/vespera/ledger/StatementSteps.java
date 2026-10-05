package io.algernon.vespera.ledger;

import java.util.function.LongConsumer;
import org.sqlite.ProgressHandler;
import org.sqlite.SQLiteConnection;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Runs one SQL statement with SQLite's progress handler set on the connection it runs on, and clears the
 * handler after (ADR-193 section 2, #411). Plumbing of the SQLite driver that every module issuing a
 * counted statement needs, of the kind ADR-177 put in {@code ledger} as {@link
 * LockedDatabaseFileTranslator}. It holds no total, no ratio, no label and no line: what the steps mean is
 * the caller's.
 *
 * <p><b>The statement is run on the connection it is handed.</b> {@link JdbcTemplate#execute(ConnectionCallback)}
 * hands over the connection of the transaction the caller is in, or borrows one outside any. The handler goes
 * on that connection unwrapped to {@link SQLiteConnection}, which is the same physical connection, because
 * the pool's own wrapper is refused by the driver. A caller must run its statement on that connection and
 * never hand it back to a {@code JdbcTemplate} method inside the callback: outside a transaction that
 * would borrow a second pooled connection, and the handler would count nothing.
 *
 * <p><b>The handler never ends a statement.</b> {@code progress()} returns 0 on every call, and an
 * exception thrown by the consumer is dropped, so a line that cannot be written never stops a statement.
 * Nothing the consumer does may touch the database: SQLite forbids using a connection from inside its own
 * progress callback.
 */
public final class StatementSteps {

    /** SQLite's progress callback is called once every this many virtual-machine steps (ADR-193 section 2). */
    public static final int STEPS_PER_CALLBACK = 100_000;

    private StatementSteps() {}

    /**
     * Runs {@code statement}, telling {@code stepsTaken} the steps taken so far at each callback, a whole
     * number of {@link #STEPS_PER_CALLBACK}. The handler is cleared in a {@code finally}, so a statement that
     * fails leaves none on the pooled connection, and its failure is the template's to translate as any
     * statement's.
     */
    public static <T> T counted(JdbcTemplate jdbcTemplate, LongConsumer stepsTaken, ConnectionCallback<T> statement) {
        T answer = jdbcTemplate.execute((ConnectionCallback<T>) connection -> {
            SQLiteConnection sqlite = connection.unwrap(SQLiteConnection.class);
            ProgressHandler.setHandler(sqlite, STEPS_PER_CALLBACK, new Reporting(stepsTaken));
            try {
                return statement.doInConnection(connection);
            } finally {
                ProgressHandler.clearHandler(sqlite);
            }
        });
        return answer;
    }

    /** Counts its own calls, because SQLite does not say how many steps it has taken. */
    private static final class Reporting extends ProgressHandler {

        private final LongConsumer stepsTaken;
        private long calls;

        Reporting(LongConsumer stepsTaken) {
            this.stepsTaken = stepsTaken;
        }

        @Override
        protected int progress() {
            calls++;
            try {
                stepsTaken.accept(calls * STEPS_PER_CALLBACK);
            } catch (RuntimeException droppedBecauseALineThatCannotBeWrittenNeverStopsAStatement) {
                // A failure in the database is the step's to report, and a failure to log is not.
            }
            return 0;
        }
    }
}
