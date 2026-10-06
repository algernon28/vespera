package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.BiFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sqlite.SQLiteConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StreamUtils;

/**
 * What {@code extraction} tells its caller around each of stage 2's two resume reads, and in which order
 * (ADR-199 section 2, ADR-193 sections 7 and 8, #429): {@code statementStarting} once before the read,
 * always, with the span of the run's rows or an empty total where it holds none; {@code stepsTaken} at each
 * callback of SQLite's handler; {@code statementEnded} once after it, on every path but one that throws. A
 * read that throws leaves no handler on any connection.
 *
 * <p>This is the caller's own contract test ADR-193 section 2 asks of every class that issues a counted
 * statement. <b>The pool here has two connections, and no transaction is open</b>, as in {@code
 * StatementStepsTest}: a read handed back to a {@code JdbcTemplate} method would borrow the second
 * connection, the handler on the first would count nothing, and the read over many rows below would report
 * no steps.
 *
 * <p>Each test runs once for each read, against a database file of its own under the shipped {@code
 * schema.sql}, with rows of an earlier run in the same table that the read must not go through.
 */
@Epic("Extraction")
@Feature("Progress reporting")
@Issue("429")
@Link(name = "ADR-199", url = Adr.THE_STATEMENTS_ADR_193_LEFT_UNNAMED_TAKE_ITS_RULE, type = "adr")
class ExtractionStatementProgressOrderTest {

    private static final int TWO_CONNECTIONS = 2;

    /** The interval ADR-193 section 2 fixes. */
    private static final long EVERY_HUNDRED_THOUSAND_STEPS = 100_000L;

    /** A few rows: far fewer steps than one callback. */
    private static final int FEW = 3;

    /** Enough rows at 5 steps a row for at least two callbacks of SQLite's handler. */
    private static final int MANY = 50_000;

    private static final RunId RUN_READ = new RunId("b".repeat(64));
    private static final RunId EARLIER_RUN = new RunId("a".repeat(64));

    /** How many callbacks of SQLite's handler the read over many rows must make for its steps to be seen rising. */
    private static final int TWO_CALLBACKS = 2;

    /**
     * Stage 2's two resume reads, each with the words the report names it by, the table it reads and the
     * call that reads it.
     */
    enum ResumeRead {
        FAULTS("the read of a stopped run's faults", ExtractionStatement.FAULTED_OCCURRENCES, "extraction_fault",
                (jdbcTemplate, run) -> progress -> new ExtractionFaults(jdbcTemplate).occurrencesForRun(run, progress)),
        MEASURED("the read of the occurrences a stopped run measured", ExtractionStatement.RECORDED_OCCURRENCES,
                "extraction_metric",
                (jdbcTemplate, run) -> progress -> new ExtractionMetrics(jdbcTemplate, new LanguageDetection())
                        .occurrencesForRun(run, progress));

        final String described;
        final ExtractionStatement statement;
        final String table;
        final BiFunction<JdbcTemplate, RunId, Read> read;

        ResumeRead(
                String described,
                ExtractionStatement statement,
                String table,
                BiFunction<JdbcTemplate, RunId, Read> read) {
            this.described = described;
            this.statement = statement;
            this.table = table;
            this.read = read;
        }

        /** What each run of a test is named by in the report, in place of the constant's name. */
        @Override
        public String toString() {
            return described;
        }
    }

    @FunctionalInterface
    interface Read {
        Set<OccurrenceId> with(ExtractionStatementProgress progress);
    }

    @TempDir
    Path folder;

    private HikariDataSource pool;
    private JdbcTemplate jdbcTemplate;
    private int rowsWritten;

    @BeforeEach
    void aPoolOfTwoOverAFileOfThisTestsOwnUnderTheShippedSchema() throws SQLException, IOException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + folder.resolve("resume-reads.db"));
        config.setMaximumPoolSize(TWO_CONNECTIONS);
        config.setMinimumIdle(TWO_CONNECTIONS);
        pool = new HikariDataSource(config);
        jdbcTemplate = new JdbcTemplate(pool);
        StringBuilder statements = new StringBuilder();
        for (String line : schema().split("\n")) {
            if (!line.strip().startsWith("--")) {
                statements.append(line).append('\n');
            }
        }
        try (Connection connection = pool.getConnection();
                Statement statement = connection.createStatement()) {
            for (String one : statements.toString().split(";")) {
                if (!one.isBlank()) {
                    statement.executeUpdate(one);
                }
            }
        }
    }

    @AfterEach
    void closeThePool() {
        pool.close();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ResumeRead.class)
    @Story("A read of a stopped run's faults reports how far it has gone")
    @Story("A read of the occurrences a stopped run measured reports how far it has gone")
    @DisplayName("A run that holds no row is told with an empty total, then that the read ended, and nothing between")
    void aRunThatHoldsNoRowIsToldWithAnEmptyTotal(ResumeRead resumeRead) throws SQLException {
        writeRows(resumeRead, EARLIER_RUN, FEW);
        Recorder recorder = new Recorder();

        Set<OccurrenceId> read = resumeRead.read.apply(jdbcTemplate, RUN_READ).with(recorder);

        claim("the read finds nothing under a run that holds no row", () -> assertThat(read).isEmpty());
        claim(
                "the caller is told the read is starting, with an empty total, and then that it ended, and"
                        + " nothing else: it is the caller, not the code that reads, that decides nothing is said",
                () -> assertThat(recorder.calls)
                        .containsExactly(starting(resumeRead, OptionalLong.empty()), ended(resumeRead)));
        claim("and neither connection of the pool carries a handler afterwards", () -> assertThat(handlersLeft()).isZero());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ResumeRead.class)
    @Story("A read of a stopped run's faults reports how far it has gone")
    @Story("A read of the occurrences a stopped run measured reports how far it has gone")
    @DisplayName("A run that holds a few rows is told the span of its rows before the read, then that it ended")
    void aRunThatHoldsAFewRowsIsToldItsSpan(ResumeRead resumeRead) throws SQLException {
        writeRows(resumeRead, EARLIER_RUN, FEW);
        List<OccurrenceId> written = writeRows(resumeRead, RUN_READ, FEW);
        Recorder recorder = new Recorder();

        Set<OccurrenceId> read = resumeRead.read.apply(jdbcTemplate, RUN_READ).with(recorder);

        claim(
                "the read finds the run's own " + FEW + " occurrences and none of the earlier run's",
                () -> assertThat(read).containsExactlyInAnyOrderElementsOf(written));
        claim(
                "the caller is told the read is starting, over up to the " + FEW + " rows the run holds, and then"
                        + " that it ended: so few rows make no callback between",
                () -> assertThat(recorder.calls)
                        .containsExactly(starting(resumeRead, OptionalLong.of(FEW)), ended(resumeRead)));
        claim("and neither connection of the pool carries a handler afterwards", () -> assertThat(handlersLeft()).isZero());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ResumeRead.class)
    @Story("A read of a stopped run's faults reports how far it has gone")
    @Story("A read of the occurrences a stopped run measured reports how far it has gone")
    @DisplayName("A read over many rows reports its steps between its start and its end, so it ran on the connection the handler was set on")
    void aReadOverManyRowsReportsItsStepsBetween(ResumeRead resumeRead) throws SQLException {
        writeRows(resumeRead, EARLIER_RUN, FEW);
        writeRows(resumeRead, RUN_READ, MANY);
        Recorder recorder = new Recorder();

        Set<OccurrenceId> read = resumeRead.read.apply(jdbcTemplate, RUN_READ).with(recorder);

        claim("the read finds every one of the run's " + MANY + " occurrences", () -> assertThat(read).hasSize(MANY));
        claim(
                "the caller is told first that the read is starting, over up to " + MANY + " rows, and last that"
                        + " it ended",
                () -> {
                    assertThat(recorder.calls).first().isEqualTo(starting(resumeRead, OptionalLong.of(MANY)));
                    assertThat(recorder.calls).last().isEqualTo(ended(resumeRead));
                });
        claim(
                "and between them only the steps SQLite took, a hundred thousand more each time, at least "
                        + TWO_CALLBACKS + " times, which " + MANY + " rows at " + resumeRead.statement.stepsPerRow().getAsInt()
                        + " steps a row are enough for: with a second connection in the pool and no transaction,"
                        + " the read ran on the one the handler was set on",
                () -> {
                    List<String> between = recorder.calls.subList(1, recorder.calls.size() - 1);
                    assertThat(between).hasSizeGreaterThanOrEqualTo(TWO_CALLBACKS);
                    for (int i = 0; i < between.size(); i++) {
                        assertThat(between.get(i)).isEqualTo(stepsTaken(resumeRead, (i + 1) * EVERY_HUNDRED_THOUSAND_STEPS));
                    }
                });
        claim("and neither connection of the pool carries a handler afterwards", () -> assertThat(handlersLeft()).isZero());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ResumeRead.class)
    @Story("A read of a stopped run's faults reports how far it has gone")
    @Story("A read of the occurrences a stopped run measured reports how far it has gone")
    @DisplayName("A read that throws is not said to have ended, and leaves no handler behind")
    void aReadThatThrowsIsNotSaidToHaveEnded(ResumeRead resumeRead) throws SQLException {
        writeRows(resumeRead, RUN_READ, FEW);
        Recorder recorder = new Recorder() {
            @Override
            public void statementStarting(ExtractionStatement statement, OptionalLong rowsUpTo) {
                super.statementStarting(statement, rowsUpTo);
                // Between the bound and the read, so the read itself is what fails.
                jdbcTemplate.execute("DROP TABLE " + resumeRead.table);
            }
        };

        claim(
                "the read fails as the template reports any statement's failure, once its table is gone",
                () -> assertThatThrownBy(() -> resumeRead.read.apply(jdbcTemplate, RUN_READ).with(recorder))
                        .isInstanceOf(DataAccessException.class));
        claim(
                "the caller was told the read was starting, and never that it ended",
                () -> assertThat(recorder.calls).containsExactly(starting(resumeRead, OptionalLong.of(FEW))));
        claim("and neither connection of the pool carries a handler afterwards", () -> assertThat(handlersLeft()).isZero());
    }

    private static String starting(ResumeRead resumeRead, OptionalLong rowsUpTo) {
        return "statementStarting(" + resumeRead.statement + ", " + rowsUpTo + ")";
    }

    private static String stepsTaken(ResumeRead resumeRead, long steps) {
        return "stepsTaken(" + resumeRead.statement + ", " + steps + ")";
    }

    private static String ended(ResumeRead resumeRead) {
        return "statementEnded(" + resumeRead.statement + ")";
    }

    /** Every callback, in the order it came, written as the call it was. */
    private static class Recorder implements ExtractionStatementProgress {

        final List<String> calls = new ArrayList<>();

        @Override
        public void statementStarting(ExtractionStatement statement, OptionalLong rowsUpTo) {
            calls.add("statementStarting(" + statement + ", " + rowsUpTo + ")");
        }

        @Override
        public void stepsTaken(ExtractionStatement statement, long steps) {
            calls.add("stepsTaken(" + statement + ", " + steps + ")");
        }

        @Override
        public void statementEnded(ExtractionStatement statement) {
            calls.add("statementEnded(" + statement + ")");
        }
    }

    /**
     * Rows under {@code run} in the read's table, each a fresh occurrence, with every column that may not be
     * null set; foreign keys are not enforced on this pool's connections. Returns the occurrences written.
     */
    private List<OccurrenceId> writeRows(ResumeRead resumeRead, RunId run, int rows) throws SQLException {
        String insert = switch (resumeRead) {
            case FAULTS -> "INSERT INTO extraction_fault (occurrence_id, run_id, category, detail)"
                    + " VALUES (?, ?, 'category', 'detail')";
            case MEASURED -> "INSERT INTO extraction_metric (occurrence_id, run_id, status, processing_time,"
                    + " character_count, alphanumeric_char_count, word_count, word_character_length_total,"
                    + " vowelless_word_count, single_character_word_count)"
                    + " VALUES (?, ?, 'success', 1.0, 1, 1, 1, 1, 0, 0)";
        };
        List<OccurrenceId> written = new ArrayList<>();
        try (Connection connection = pool.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(insert)) {
                for (int row = rowsWritten; row < rowsWritten + rows; row++) {
                    statement.setLong(1, row + 1L);
                    statement.setString(2, run.value());
                    statement.addBatch();
                    written.add(new OccurrenceId(row + 1L));
                }
                statement.executeBatch();
            }
            connection.commit();
            connection.setAutoCommit(true);
        }
        rowsWritten += rows;
        return written;
    }

    /** How many of the pool's connections carry a progress handler, both taken at once. */
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

    private static String schema() throws IOException {
        return StreamUtils.copyToString(new ClassPathResource("schema.sql").getInputStream(), StandardCharsets.UTF_8);
    }
}
