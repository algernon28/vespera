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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The one helper that sets SQLite's progress handler on the connection a statement runs on, and clears it
 * after (ADR-193 section 2, #411).
 *
 * <p>Written before {@code StatementSteps} existed and kept under {@code docs/adr/0193/tests/a/}, where it
 * could not stop the test tree compiling. It moved here with part (a) of ADR-193, which it has held green
 * since.
 *
 * <p><b>The pool here has two connections, and no transaction is open</b>, on purpose. A statement handed
 * back to a {@code JdbcTemplate} method inside the callback would then borrow the second connection, and
 * the handler, on the first, would count nothing: the test profile's pool of one cannot see that mistake.
 * After each statement both connections are taken from the pool and neither may carry a handler.
 */
@Epic("Ledger")
@Feature("Progress reporting")
@Issue("411")
@Link(name = "ADR-193", url = Adr.STATEMENTS_REPORT_THEIR_PROGRESS, type = "adr")
class StatementStepsTest {

    /** Two connections, so that a statement run on the wrong one is told apart. */
    private static final int TWO_CONNECTIONS = 2;

    /** The interval ADR-193 section 2 fixes. */
    private static final long EVERY_HUNDRED_THOUSAND_STEPS = 100_000L;

    /** Three hundred thousand rows made from nothing: millions of steps, so dozens of callbacks. */
    private static final String MANY_ROWS = "WITH RECURSIVE n(x) AS (SELECT 1 UNION ALL SELECT x + 1 FROM n"
            + " WHERE x < 300000) SELECT COUNT(*) FROM n";

    /** What that statement answers. */
    private static final long THREE_HUNDRED_THOUSAND = 300_000L;

    @TempDir
    Path folder;

    private HikariDataSource pool;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void aPoolOfTwoOverAFileOfThisTestsOwn() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + folder.resolve("steps.db"));
        config.setMaximumPoolSize(TWO_CONNECTIONS);
        config.setMinimumIdle(TWO_CONNECTIONS);
        pool = new HikariDataSource(config);
        jdbcTemplate = new JdbcTemplate(pool);
    }

    @AfterEach
    void closeThePool() {
        pool.close();
    }

    @Test
    @Story("A long statement inside the database reports how far it has gone")
    @DisplayName("A statement run on the connection the handler is set on reports its steps, a hundred thousand at a time, and leaves no handler behind")
    void aStatementReportsItsStepsAndLeavesNoHandler() throws SQLException {
        List<Long> reported = new ArrayList<>();
        Long answer = StatementSteps.counted(jdbcTemplate, reported::add, connection -> countOf(connection));

        claim("the statement ran and answered", () -> assertThat(answer).isEqualTo(THREE_HUNDRED_THOUSAND));
        claim(
                "it reported its steps while it ran, outside any transaction and with a second connection in"
                        + " the pool, so it ran on the connection the handler was set on",
                () -> assertThat(reported).isNotEmpty());
        claim(
                "each report is a whole number of a hundred thousand steps, one more each time",
                () -> {
                    for (int i = 0; i < reported.size(); i++) {
                        assertThat(reported.get(i)).isEqualTo((i + 1) * EVERY_HUNDRED_THOUSAND_STEPS);
                    }
                });
        claim("and neither connection of the pool carries a handler afterwards", () -> assertThat(handlersLeft()).isZero());
    }

    @Test
    @Story("A long statement inside the database reports how far it has gone")
    @DisplayName("A statement that fails leaves no handler behind, and its failure is the application's to translate")
    void aStatementThatFailsLeavesNoHandler() throws SQLException {
        claim(
                "a statement that fails inside the helper fails as the template reports any statement's failure",
                () -> assertThatThrownBy(() -> StatementSteps.counted(jdbcTemplate, steps -> {}, connection -> {
                            countOf(connection);
                            try (Statement statement = connection.createStatement()) {
                                statement.executeQuery("SELECT * FROM no_such_table");
                            }
                            return null;
                        }))
                        .isInstanceOf(DataAccessException.class));
        claim("and neither connection of the pool carries a handler afterwards", () -> assertThat(handlersLeft()).isZero());
    }

    @Test
    @Story("A long statement inside the database reports how far it has gone")
    @DisplayName("A report that cannot be made never stops the statement")
    void aReportThatThrowsNeverStopsTheStatement() throws SQLException {
        Long answer = StatementSteps.counted(
                jdbcTemplate,
                steps -> {
                    throw new IllegalStateException("the line could not be written");
                },
                connection -> countOf(connection));

        claim(
                "the statement ran to its end and answered, though every report of its steps threw: the"
                        + " handler never stops a statement",
                () -> assertThat(answer).isEqualTo(THREE_HUNDRED_THOUSAND));
        claim("and neither connection of the pool carries a handler afterwards", () -> assertThat(handlersLeft()).isZero());
    }

    private static Long countOf(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(MANY_ROWS)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    /** How many of the pool's connections carry a progress handler, all of them taken at once. */
    private int handlersLeft() throws SQLException {
        int carrying = 0;
        try (Connection first = pool.getConnection();
                Connection second = pool.getConnection()) {
            for (Connection connection : List.of(first, second)) {
                if (isSet(connection.unwrap(SQLiteConnection.class))) {
                    carrying++;
                }
            }
        }
        return carrying;
    }

    private static boolean isSet(SQLiteConnection connection) {
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
