package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.algernon.vespera.PoolOfTwo;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * What {@code extraction} tells its caller around each of its statements, and in which order (ADR-193
 * sections 7 and 8, ADR-199 sections 2 and 4, #411, #429): {@code statementStarting} once before the
 * statement, with the span of the run's rows for a counted one, an empty total where the run holds none,
 * and an empty total for a timed one; {@code stepsTaken} at each callback of SQLite's handler; {@code
 * statementEnded} once after it, on every path but one that throws.
 *
 * <p>The statements are the two reads stage 2's reader makes before a resume, of the occurrences its run
 * already holds a fault for and of those it already holds a metric row for, and the two of {@code
 * ConfidenceDistribution.measure}: its drain of stage 2's survivors, timed, and its read of the extraction
 * metrics, counted.
 *
 * <p><b>Parked under {@code docs/adr/0193/tests/b/}</b>: it names {@code ExtractionStatement}, {@code
 * ExtractionStatementProgress} and three overloads part (b) adds, and does not compile before. It runs on
 * {@link PoolOfTwo}, so a read handed back to the template would report no steps over many rows.
 */
@Epic("Extraction")
@Feature("Progress reporting")
@Issue("411")
@Link(name = "ADR-193", url = Adr.STATEMENTS_REPORT_THEIR_PROGRESS, type = "adr")
@Link(name = "ADR-199", url = Adr.THE_STATEMENTS_ADR_193_LEFT_UNNAMED_TAKE_ITS_RULE, type = "adr")
class ExtractionStatementProgressOrderTest {

    /** The interval ADR-193 section 2 fixes. */
    private static final long EVERY_HUNDRED_THOUSAND_STEPS = 100_000L;

    /** A few rows: far fewer steps than one callback. */
    private static final int FEW = 3;

    /** Enough rows, at 5 or at 7 steps a row, for at least two callbacks of SQLite's handler. */
    private static final int MANY = 50_000;

    private static final RunId RUN_READ = new RunId("b".repeat(64));
    private static final RunId EARLIER_RUN = new RunId("a".repeat(64));

    /** Stage 2's two reads before a resume, each with the table it reads and the call that reads it. */
    enum ResumeRead {
        FAULTS(ExtractionStatement.FAULTED_OCCURRENCES, "extraction_fault") {
            @Override
            Set<OccurrenceId> read(JdbcTemplate jdbcTemplate, RunId run, ExtractionStatementProgress progress) {
                return new ExtractionFaults(jdbcTemplate).occurrencesForRun(run, progress);
            }
        },
        MEASURED(ExtractionStatement.RECORDED_OCCURRENCES, "extraction_metric") {
            @Override
            Set<OccurrenceId> read(JdbcTemplate jdbcTemplate, RunId run, ExtractionStatementProgress progress) {
                return new ExtractionMetrics(jdbcTemplate, new LanguageDetection()).occurrencesForRun(run, progress);
            }
        };

        final ExtractionStatement statement;
        final String table;

        ResumeRead(ExtractionStatement statement, String table) {
            this.statement = statement;
            this.table = table;
        }

        abstract Set<OccurrenceId> read(JdbcTemplate jdbcTemplate, RunId run, ExtractionStatementProgress progress);
    }

    @TempDir
    Path folder;

    private PoolOfTwo pool;
    private JdbcTemplate jdbcTemplate;
    private long rowsWritten;

    @BeforeEach
    void aPoolOfTwoUnderTheShippedSchema() throws SQLException, IOException {
        pool = new PoolOfTwo(folder);
        jdbcTemplate = pool.jdbcTemplate();
    }

    @AfterEach
    void closeThePool() {
        pool.close();
    }

    @ParameterizedTest
    @EnumSource(ResumeRead.class)
    @Story("A read of what an interrupted extraction left reports how far it has gone")
    @DisplayName("A run that holds no row is told with an empty total, then that the read ended, and nothing between")
    void aRunThatHoldsNoRowIsToldWithAnEmptyTotal(ResumeRead resumeRead) throws SQLException {
        write(resumeRead.table, EARLIER_RUN, FEW);
        Recorder recorder = new Recorder();

        Set<OccurrenceId> read = resumeRead.read(jdbcTemplate, RUN_READ, recorder);

        claim("the read finds nothing under a run that holds no row", () -> assertThat(read).isEmpty());
        claim(
                "the caller is told the read is starting, with an empty total, and then that it ended, and"
                        + " nothing else: the caller, not extraction, decides that nothing is said",
                () -> assertThat(recorder.calls)
                        .containsExactly(
                                starting(resumeRead.statement, OptionalLong.empty()), ended(resumeRead.statement)));
        claim("and neither connection of the pool carries a handler afterwards", () -> assertThat(pool.handlersLeft())
                .isZero());
    }

    @ParameterizedTest
    @EnumSource(ResumeRead.class)
    @Story("A read of what an interrupted extraction left reports how far it has gone")
    @DisplayName("A run that holds a few rows is told the span of its rows before the read, then that it ended")
    void aRunThatHoldsAFewRowsIsToldItsSpan(ResumeRead resumeRead) throws SQLException {
        write(resumeRead.table, EARLIER_RUN, FEW);
        List<OccurrenceId> written = write(resumeRead.table, RUN_READ, FEW);
        Recorder recorder = new Recorder();

        Set<OccurrenceId> read = resumeRead.read(jdbcTemplate, RUN_READ, recorder);

        claim(
                "the read finds the run's own " + FEW + " occurrences and none of the earlier run's",
                () -> assertThat(read).containsExactlyInAnyOrderElementsOf(written));
        claim(
                "the caller is told the read is starting, over up to the " + FEW + " rows the run holds, and then"
                        + " that it ended: so few rows make no callback between",
                () -> assertThat(recorder.calls)
                        .containsExactly(
                                starting(resumeRead.statement, OptionalLong.of(FEW)), ended(resumeRead.statement)));
    }

    @ParameterizedTest
    @EnumSource(ResumeRead.class)
    @Story("A read of what an interrupted extraction left reports how far it has gone")
    @DisplayName("A read over many rows reports its steps between its start and its end, so it ran on the connection the handler was set on")
    void aReadOverManyRowsReportsItsStepsBetween(ResumeRead resumeRead) throws SQLException {
        write(resumeRead.table, RUN_READ, MANY);
        Recorder recorder = new Recorder();

        Set<OccurrenceId> read = resumeRead.read(jdbcTemplate, RUN_READ, recorder);

        claim("the read finds every one of the run's " + MANY + " occurrences", () -> assertThat(read).hasSize(MANY));
        theStepsLieBetween(recorder.calls, resumeRead.statement, MANY);
        claim("and neither connection of the pool carries a handler afterwards", () -> assertThat(pool.handlersLeft())
                .isZero());
    }

    @ParameterizedTest
    @EnumSource(ResumeRead.class)
    @Story("A read of what an interrupted extraction left reports how far it has gone")
    @DisplayName("A read that throws is not said to have ended, and leaves no handler behind")
    void aReadThatThrowsIsNotSaidToHaveEnded(ResumeRead resumeRead) throws SQLException {
        write(resumeRead.table, RUN_READ, FEW);
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
                () -> assertThatThrownBy(() -> resumeRead.read(jdbcTemplate, RUN_READ, recorder))
                        .isInstanceOf(DataAccessException.class));
        claim(
                "the caller was told the read was starting, and never that it ended",
                () -> assertThat(recorder.calls)
                        .containsExactly(starting(resumeRead.statement, OptionalLong.of(FEW))));
        claim("and neither connection of the pool carries a handler afterwards", () -> assertThat(pool.handlersLeft())
                .isZero());
    }

    @Test
    @Story("Measuring the confidence spread says what it is reading")
    @DisplayName("The confidence spread's drain is started and ended with no total, and then its read of the metrics with the span of the run's rows")
    void theConfidenceDistributionStartsAndEndsItsDrainAndThenItsRead() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.startWalk(Path.of("C:/corpus-statements"));
        RunId stage2 = ledger.startRun("extraction", "abc123", "{}", walk, List.of());
        RunId stage3 = ledger.startRun("content-census", "def456", "{}", walk, List.of(stage2));
        for (String path : List.of("a.txt", "b.txt")) {
            ledger.fileOccurrence(walk, new OccurrencePath(path), 1, Instant.EPOCH, Instant.EPOCH);
            jdbcTemplate.update(
                    METRIC_ROW, ledger.occurrenceId(walk, new OccurrencePath(path)).orElseThrow().value(), stage2.value());
        }
        Recorder recorder = new Recorder();

        new ConfidenceDistribution(jdbcTemplate, ledger).measure(stage3, stage2, recorder);

        claim(
                "the drain of the documents stage 2 left is started with no total and ended, and only then is the"
                        + " read of the metrics started, over up to the two rows stage 2's run holds, and ended",
                () -> assertThat(recorder.calls)
                        .containsExactly(
                                starting(ExtractionStatement.SURVIVORS, OptionalLong.empty()),
                                ended(ExtractionStatement.SURVIVORS),
                                starting(ExtractionStatement.EXTRACTION_METRICS, OptionalLong.of(2)),
                                ended(ExtractionStatement.EXTRACTION_METRICS)));
    }

    @Test
    @Story("Measuring the confidence spread says what it is reading")
    @DisplayName("With no metric row under the run, the read of the metrics is started with an empty total")
    void theConfidenceDistributionOverNoRowStartsItsReadWithAnEmptyTotal() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.startWalk(Path.of("C:/corpus-no-metric"));
        RunId stage2 = ledger.startRun("extraction", "abc124", "{}", walk, List.of());
        RunId stage3 = ledger.startRun("content-census", "def457", "{}", walk, List.of(stage2));
        Recorder recorder = new Recorder();

        new ConfidenceDistribution(jdbcTemplate, ledger).measure(stage3, stage2, recorder);

        claim(
                "the drain is started and ended as ever, and the read is started with an empty total and ended",
                () -> assertThat(recorder.calls)
                        .containsExactly(
                                starting(ExtractionStatement.SURVIVORS, OptionalLong.empty()),
                                ended(ExtractionStatement.SURVIVORS),
                                starting(ExtractionStatement.EXTRACTION_METRICS, OptionalLong.empty()),
                                ended(ExtractionStatement.EXTRACTION_METRICS)));
    }

    @Test
    @Story("Measuring the confidence spread says what it is reading")
    @DisplayName("The confidence spread's read over many rows reports its steps between its start and its end")
    void theConfidenceDistributionsReadOverManyRowsReportsItsSteps() throws SQLException {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.startWalk(Path.of("C:/corpus-many-metrics"));
        RunId stage2 = ledger.startRun("extraction", "abc125", "{}", walk, List.of());
        RunId stage3 = ledger.startRun("content-census", "def458", "{}", walk, List.of(stage2));
        write("extraction_metric", stage2, MANY);
        Recorder recorder = new Recorder();

        new ConfidenceDistribution(jdbcTemplate, ledger).measure(stage3, stage2, recorder);

        List<String> read = recorder.calls.subList(2, recorder.calls.size());
        theStepsLieBetween(read, ExtractionStatement.EXTRACTION_METRICS, MANY);
        claim("and neither connection of the pool carries a handler afterwards", () -> assertThat(pool.handlersLeft())
                .isZero());
    }

    /** That {@code calls} is a start over {@code rows}, then steps a hundred thousand apart, at least two, then an end. */
    private static void theStepsLieBetween(List<String> calls, ExtractionStatement statement, long rows) {
        claim(
                "the caller is told first that the read is starting, over up to " + rows + " rows, and last that it"
                        + " ended",
                () -> {
                    assertThat(calls).first().isEqualTo(starting(statement, OptionalLong.of(rows)));
                    assertThat(calls).last().isEqualTo(ended(statement));
                });
        claim(
                "and between them only the steps SQLite took, a hundred thousand more each time, at least twice:"
                        + " with a second connection in the pool and no transaction, the read ran on the one the"
                        + " handler was set on",
                () -> {
                    List<String> between = calls.subList(1, calls.size() - 1);
                    assertThat(between).hasSizeGreaterThanOrEqualTo(2);
                    for (int i = 0; i < between.size(); i++) {
                        assertThat(between.get(i))
                                .isEqualTo(stepsTaken(statement, (i + 1) * EVERY_HUNDRED_THOUSAND_STEPS));
                    }
                });
    }

    private static String starting(ExtractionStatement statement, OptionalLong rowsUpTo) {
        return "statementStarting(" + statement + ", " + rowsUpTo + ")";
    }

    private static String stepsTaken(ExtractionStatement statement, long steps) {
        return "stepsTaken(" + statement + ", " + steps + ")";
    }

    private static String ended(ExtractionStatement statement) {
        return "statementEnded(" + statement + ")";
    }

    /** Every callback, in the order it came, written as the call it was. */
    private static class Recorder implements ExtractionStatementProgress {

        final List<String> calls = new ArrayList<>();

        @Override
        public void statementStarting(ExtractionStatement statement, OptionalLong rowsUpTo) {
            calls.add(starting(statement, rowsUpTo));
        }

        @Override
        public void stepsTaken(ExtractionStatement statement, long steps) {
            calls.add(ExtractionStatementProgressOrderTest.stepsTaken(statement, steps));
        }

        @Override
        public void statementEnded(ExtractionStatement statement) {
            calls.add(ended(statement));
        }
    }

    private static final String METRIC_ROW = "INSERT INTO extraction_metric (occurrence_id, run_id, status,"
            + " processing_time, character_count, alphanumeric_char_count, word_count,"
            + " word_character_length_total, vowelless_word_count, single_character_word_count)"
            + " VALUES (?, ?, 'success', 1.0, 1, 1, 1, 1, 0, 0)";

    private static final String FAULT_ROW =
            "INSERT INTO extraction_fault (occurrence_id, run_id, category, detail) VALUES (?, ?, 'category', 'detail')";

    /**
     * Rows under {@code run} in {@code table}, each against an occurrence number no other row here uses;
     * foreign keys are not enforced on this pool's connections. Returns the occurrences written.
     */
    private List<OccurrenceId> write(String table, RunId run, int rows) throws SQLException {
        List<OccurrenceId> written = new ArrayList<>();
        try (Connection connection = pool.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert =
                    connection.prepareStatement(table.equals("extraction_fault") ? FAULT_ROW : METRIC_ROW)) {
                for (int row = 0; row < rows; row++) {
                    long occurrence = 1_000_000L + ++rowsWritten;
                    insert.setLong(1, occurrence);
                    insert.setString(2, run.value());
                    insert.addBatch();
                    written.add(new OccurrenceId(occurrence));
                }
                insert.executeBatch();
            }
            connection.commit();
            connection.setAutoCommit(true);
        }
        return written;
    }
}
