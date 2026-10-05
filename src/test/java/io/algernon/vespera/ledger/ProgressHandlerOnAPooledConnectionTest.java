package io.algernon.vespera.ledger;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.ProgressHandler;
import org.sqlite.SQLiteConnection;

/**
 * What the connection pool does to SQLite's progress handler (ADR-193 section 2, #411): the facts the
 * mechanism is shaped around, measured on the driver and the pool the application ships, over a database
 * file of the test's own.
 *
 * <p>{@code ProgressHandler.setHandler} accepts only the driver's own connection, so a pooled one has to
 * be unwrapped first. A handler set and not cleared stays on the physical connection when it goes back to
 * the pool, and the next borrower's statements call it. So the handler is cleared after the statement, on
 * every path, and whether one is set can be read back, which is how {@code StatementStepsTest} and the
 * whole-job test check that it was.
 *
 * <p>Green from the start: it measures the driver, not Vespera. A newer driver that unwraps for itself, or
 * that clears a handler when a connection is closed, fails a test here, and the mechanism can then be
 * simpler.
 */
@Epic("Ledger")
@Feature("Progress reporting")
@Issue("411")
@Link(name = "ADR-193", url = Adr.STATEMENTS_REPORT_THEIR_PROGRESS, type = "adr")
class ProgressHandlerOnAPooledConnectionTest {

    /** One step a callback: any statement at all calls the handler. */
    private static final int EVERY_STEP = 1;

    /** A statement of a thousand rows made from nothing, which takes several thousand steps. */
    private static final String A_THOUSAND_ROWS = "WITH RECURSIVE n(x) AS (SELECT 1 UNION ALL SELECT x + 1 FROM n"
            + " WHERE x < 1000) SELECT COUNT(*) FROM n";

    /** What the driver says when it is handed a connection that is not its own. */
    private static final String NOT_ITS_OWN = "connection must be to an SQLite db";

    @TempDir
    Path folder;

    private HikariDataSource pool;
    private long calls;

    @BeforeEach
    void aPoolOfOneOverAFileOfThisTestsOwn() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + folder.resolve("progress.db"));
        config.setMaximumPoolSize(1);
        pool = new HikariDataSource(config);
    }

    @AfterEach
    void closeThePool() {
        pool.close();
    }

    @Test
    @Story("A long statement inside the database reports how far it has gone")
    @DisplayName("The progress handler refuses the pool's connection and accepts it unwrapped")
    void theHandlerRefusesThePoolsConnectionAndAcceptsItUnwrapped() throws SQLException {
        try (Connection pooled = pool.getConnection()) {
            claim(
                    "the connection the pool hands out is not the driver's own, and setting a handler on it is"
                            + " refused",
                    () -> assertThatThrownBy(() -> ProgressHandler.setHandler(pooled, EVERY_STEP, counting()))
                            .isInstanceOf(SQLException.class)
                            .hasMessageContaining(NOT_ITS_OWN));
            claim(
                    "and clearing one through it fails too, on a cast",
                    () -> assertThatThrownBy(() -> ProgressHandler.clearHandler(pooled))
                            .isInstanceOf(ClassCastException.class));

            SQLiteConnection own = pooled.unwrap(SQLiteConnection.class);
            ProgressHandler.setHandler(own, EVERY_STEP, counting());
            claim("unwrapped, the handler is set and can be read back as set", () -> assertThat(isSet(own)).isTrue());
            calls = 0;
            runAThousandRows(pooled);
            claim(
                    "and a statement run through the pool's connection calls it, since both are one connection",
                    () -> assertThat(calls).isPositive());
            ProgressHandler.clearHandler(own);
            claim("cleared, it reads back as not set", () -> assertThat(isSet(own)).isFalse());
        }
    }

    @Test
    @Story("A long statement inside the database reports how far it has gone")
    @DisplayName("A progress handler left on a connection goes back to the pool with it, and the next statement calls it")
    void aHandlerNotClearedIsCalledForTheNextBorrowersStatement() throws SQLException {
        try (Connection first = pool.getConnection()) {
            ProgressHandler.setHandler(first.unwrap(SQLiteConnection.class), EVERY_STEP, counting());
        }

        calls = 0;
        try (Connection next = pool.getConnection()) {
            runAThousandRows(next);
            claim(
                    "the next borrower's statement calls the handler the first borrower left, because the pool"
                            + " hands back the same connection and closing it cleared nothing",
                    () -> assertThat(calls).isPositive());
            claim(
                    "and the connection still reads back as carrying a handler",
                    () -> assertThat(isSet(next.unwrap(SQLiteConnection.class))).isTrue());

            ProgressHandler.clearHandler(next.unwrap(SQLiteConnection.class));
            calls = 0;
            runAThousandRows(next);
            claim("once cleared, the same statement calls nothing", () -> assertThat(calls).isZero());
        }
    }

    private ProgressHandler counting() {
        return new ProgressHandler() {
            @Override
            protected int progress() {
                calls++;
                return 0;
            }
        };
    }

    private static void runAThousandRows(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(A_THOUSAND_ROWS)) {
            rows.next();
        }
    }

    /**
     * Whether the driver holds a progress handler for this connection: {@code NativeDB.getProgressHandler()},
     * not public, is the reference it keeps, and zero when none is set.
     */
    static boolean isSet(SQLiteConnection connection) {
        try {
            Object database = connection.getDatabase();
            Method handler = database.getClass().getDeclaredMethod("getProgressHandler");
            handler.setAccessible(true);
            return ((Long) handler.invoke(database)) != 0L;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("the driver no longer says whether a progress handler is set", e);
        }
    }
}
