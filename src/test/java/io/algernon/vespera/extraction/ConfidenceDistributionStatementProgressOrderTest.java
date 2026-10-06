package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.PoolOfTwo;
import io.algernon.vespera.ledger.Ledger;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * What {@code extraction} tells its caller around the two statements of {@code
 * ConfidenceDistribution.measure}, and in which order (ADR-193 sections 6 to 8, ADR-204 section 4, #411):
 * its drain of stage 2's survivors, timed, started with no total and ended; then its read of the extraction
 * metrics, counted, started with the span of the run's rows, or with an empty total where the run holds
 * none, with {@code stepsTaken} at each callback of SQLite's handler, and ended.
 *
 * <p>The two reads stage 2's reader makes before a resume are ADR-199's, and {@code
 * ExtractionStatementProgressOrderTest} holds their callbacks. It runs on {@link PoolOfTwo}, so a read
 * handed back to the template would report no steps over many rows.
 */
@Epic("Extraction")
@Feature("Progress reporting")
@Issue("411")
@Link(name = "ADR-193", url = Adr.STATEMENTS_REPORT_THEIR_PROGRESS, type = "adr")
@Link(name = "ADR-204", url = Adr.PART_B_OF_THE_STATEMENTS_WRITTEN_OUT, type = "adr")
class ConfidenceDistributionStatementProgressOrderTest {

    /** The interval ADR-193 section 2 fixes. */
    private static final long EVERY_HUNDRED_THOUSAND_STEPS = 100_000L;

    /** Two documents, each with a metric row under stage 2's run. */
    private static final int TWO_ROWS = 2;

    /** Enough rows, at 7 steps a row, for at least two callbacks of SQLite's handler. */
    private static final int MANY = 50_000;

    private static final String METRIC_ROW = "INSERT INTO extraction_metric (occurrence_id, run_id, status,"
            + " processing_time, character_count, alphanumeric_char_count, word_count,"
            + " word_character_length_total, vowelless_word_count, single_character_word_count)"
            + " VALUES (?, ?, 'success', 1.0, 1, 1, 1, 1, 0, 0)";

    @TempDir
    Path folder;

    private PoolOfTwo pool;
    private JdbcTemplate jdbcTemplate;
    private Ledger ledger;
    private WalkId walk;
    private RunId stage2;
    private RunId stage3;

    @BeforeEach
    void aPoolOfTwoAndTwoRunsOverOneWalk() throws SQLException, IOException {
        pool = new PoolOfTwo(folder);
        jdbcTemplate = pool.jdbcTemplate();
        ledger = new Ledger(jdbcTemplate);
        walk = ledger.startWalk(Path.of("C:/corpus-statements"));
        stage2 = ledger.startRun("extraction", "abc123", "{}", walk, List.of());
        stage3 = ledger.startRun("content-census", "def456", "{}", walk, List.of(stage2));
    }

    @AfterEach
    void closeThePool() {
        pool.close();
    }

    @Test
    @Story("Measuring the confidence spread says what it is reading")
    @DisplayName("The confidence spread's drain is started and ended with no total, and then its read of the metrics with the span of the run's rows")
    void theDrainIsStartedAndEndedAndThenTheRead() {
        for (String path : List.of("a.txt", "b.txt")) {
            ledger.fileOccurrence(walk, new OccurrencePath(path), 1, Instant.EPOCH, Instant.EPOCH);
            jdbcTemplate.update(
                    METRIC_ROW, ledger.occurrenceId(walk, new OccurrencePath(path)).orElseThrow().value(), stage2.value());
        }
        Recorder recorder = new Recorder();

        new ConfidenceDistribution(jdbcTemplate, ledger).measure(stage3, stage2, recorder);

        claim(
                "the drain of the documents stage 2 left is started with no total and ended, and only then is the"
                        + " read of the metrics started, over up to the " + TWO_ROWS + " rows stage 2's run holds,"
                        + " and ended",
                () -> assertThat(recorder.calls)
                        .containsExactly(
                                starting(ExtractionStatement.SURVIVORS, OptionalLong.empty()),
                                ended(ExtractionStatement.SURVIVORS),
                                starting(ExtractionStatement.EXTRACTION_METRICS, OptionalLong.of(TWO_ROWS)),
                                ended(ExtractionStatement.EXTRACTION_METRICS)));
    }

    @Test
    @Story("Measuring the confidence spread says what it is reading")
    @DisplayName("With no metric row under the run, the read of the metrics is started with an empty total")
    void overNoRowTheReadIsStartedWithAnEmptyTotal() {
        Recorder recorder = new Recorder();

        new ConfidenceDistribution(jdbcTemplate, ledger).measure(stage3, stage2, recorder);

        claim(
                "the drain is started and ended as ever, and the read is started with an empty total and ended:"
                        + " the caller, not extraction, decides that nothing is said",
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
    void theReadOverManyRowsReportsItsSteps() throws SQLException {
        try (Connection connection = pool.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement(METRIC_ROW)) {
                for (int row = 1; row <= MANY; row++) {
                    insert.setLong(1, 1_000_000L + row);
                    insert.setString(2, stage2.value());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            connection.commit();
            connection.setAutoCommit(true);
        }
        Recorder recorder = new Recorder();

        new ConfidenceDistribution(jdbcTemplate, ledger).measure(stage3, stage2, recorder);

        List<String> read = recorder.calls.subList(2, recorder.calls.size());
        claim(
                "after the drain, the caller is told that the read is starting, over up to " + MANY + " rows, and"
                        + " last that it ended",
                () -> {
                    assertThat(read)
                            .first()
                            .isEqualTo(starting(ExtractionStatement.EXTRACTION_METRICS, OptionalLong.of(MANY)));
                    assertThat(read).last().isEqualTo(ended(ExtractionStatement.EXTRACTION_METRICS));
                });
        claim(
                "and between them only the steps SQLite took, a hundred thousand more each time, at least twice:"
                        + " with a second connection in the pool and no transaction, the read ran on the one the"
                        + " handler was set on",
                () -> {
                    List<String> between = read.subList(1, read.size() - 1);
                    assertThat(between).hasSizeGreaterThanOrEqualTo(2);
                    for (int i = 0; i < between.size(); i++) {
                        assertThat(between.get(i))
                                .isEqualTo("stepsTaken(" + ExtractionStatement.EXTRACTION_METRICS + ", "
                                        + (i + 1) * EVERY_HUNDRED_THOUSAND_STEPS + ")");
                    }
                });
        claim("and neither connection of the pool carries a handler afterwards", () -> assertThat(pool.handlersLeft())
                .isZero());
    }

    private static String starting(ExtractionStatement statement, OptionalLong rowsUpTo) {
        return "statementStarting(" + statement + ", " + rowsUpTo + ")";
    }

    private static String ended(ExtractionStatement statement) {
        return "statementEnded(" + statement + ")";
    }

    /** Every callback, in the order it came, written as the call it was. */
    private static final class Recorder implements ExtractionStatementProgress {

        final List<String> calls = new ArrayList<>();

        @Override
        public void statementStarting(ExtractionStatement statement, OptionalLong rowsUpTo) {
            calls.add(starting(statement, rowsUpTo));
        }

        @Override
        public void stepsTaken(ExtractionStatement statement, long steps) {
            calls.add("stepsTaken(" + statement + ", " + steps + ")");
        }

        @Override
        public void statementEnded(ExtractionStatement statement) {
            calls.add(ended(statement));
        }
    }
}
