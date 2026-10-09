package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * What {@code extraction} tells its caller around {@code ConfidenceDistribution.measure}'s read of the
 * extraction metrics, and in which order (ADR-193 sections 6 to 8, ADR-204 section 4, ADR-211 section 9).
 *
 * <p>Since ADR-211 the read is made a page of stage 2's survivors at a time, and no drain of the survivors
 * comes before it. It is started once, with the span of the run's rows or an empty total where the run holds
 * none; after each page's rows have been read it is told the rows read so far, through {@code rowsRead},
 * and never SQLite's steps; and it is ended once. {@code rowsRead} is written without {@code @Override}: it
 * is the callback ADR-211 adds, and this class compiles before it exists.
 *
 * <p>The two reads stage 2's reader makes before a resume are ADR-199's, and {@code
 * ExtractionStatementProgressOrderTest} holds their callbacks. It runs on {@link PoolOfTwo}.
 */
@Epic("Extraction")
@Feature("Progress reporting")
@Issue("456")
@Link(name = "ADR-193", url = Adr.STATEMENTS_REPORT_THEIR_PROGRESS, type = "adr")
@Link(name = "ADR-204", url = Adr.PART_B_OF_THE_STATEMENTS_WRITTEN_OUT, type = "adr")
@Link(name = "ADR-211", url = Adr.NO_CLASS_HOLDS_EVERY_SURVIVOR_OF_A_RUN, type = "adr")
class ConfidenceDistributionStatementProgressOrderTest {

    /** Two documents, each with a metric row under stage 2's run. */
    private static final int TWO_ROWS = 2;

    /** Two full pages of survivors and a short third, each with a metric row. */
    private static final int MANY = 2_500;

    /** The rows read once each page of 1,000 survivors has been read: one, two and two and a half pages. */
    private static final List<Long> ROWS_AFTER_EACH_PAGE = List.of(1_000L, 2_000L, 2_500L);

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
        walk = ledger.walks().startWalk(Path.of("C:/corpus-statements"));
        stage2 = ledger.runs().startRun("extraction", "abc123", "{}", walk, List.of());
        stage3 = ledger.runs().startRun("content-census", "def456", "{}", walk, List.of(stage2));
    }

    @AfterEach
    void closeThePool() {
        pool.close();
    }

    @Test
    @Story("Measuring the confidence spread says what it is reading")
    @DisplayName("The confidence spread's read of the metrics is started with the span of the run's rows, told the rows it read, and ended, with nothing before it")
    void theReadIsStartedToldItsRowsAndEnded() {
        for (String path : List.of("a.txt", "b.txt")) {
            ledger.occurrences().fileOccurrence(walk, new OccurrencePath(path), 1, Instant.EPOCH, Instant.EPOCH);
            jdbcTemplate.update(
                    METRIC_ROW, ledger.occurrences().occurrenceId(walk, new OccurrencePath(path)).orElseThrow().value(), stage2.value());
        }
        Recorder recorder = new Recorder();

        new ConfidenceDistribution(jdbcTemplate, ledger).measure(stage3, stage2, recorder);

        claim(
                "the read of the metrics is the first thing the caller hears of, started over up to the " + TWO_ROWS
                        + " rows stage 2's run holds; it is told the " + TWO_ROWS + " rows read once the one page"
                        + " of survivors has been read; and it is ended. No drain of the survivors is reported",
                () -> assertThat(recorder.calls)
                        .containsExactly(
                                starting(ExtractionStatement.EXTRACTION_METRICS, OptionalLong.of(TWO_ROWS)),
                                rowsReadCall(ExtractionStatement.EXTRACTION_METRICS, TWO_ROWS),
                                ended(ExtractionStatement.EXTRACTION_METRICS)));
    }

    @Test
    @Story("Measuring the confidence spread says what it is reading")
    @DisplayName("With no metric row and no surviving document, the read of the metrics is started with an empty total and ended, and nothing comes between")
    void overNoRowTheReadIsStartedWithAnEmptyTotal() {
        Recorder recorder = new Recorder();

        new ConfidenceDistribution(jdbcTemplate, ledger).measure(stage3, stage2, recorder);

        claim(
                "the read is started with an empty total and ended, with no rows between: the caller, not"
                        + " extraction, decides that nothing is said",
                () -> assertThat(recorder.calls)
                        .containsExactly(
                                starting(ExtractionStatement.EXTRACTION_METRICS, OptionalLong.empty()),
                                ended(ExtractionStatement.EXTRACTION_METRICS)));
    }

    @Test
    @Story("Measuring the confidence spread says what it is reading")
    @DisplayName("Over three pages of surviving documents, the read of the metrics is told the rows it has read after each page, and nothing of the database's own steps")
    void theReadOverManySurvivorsIsToldItsRowsAfterEachPage() throws SQLException {
        try (Connection connection = pool.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement occurrence = connection.prepareStatement(
                    "INSERT INTO file_occurrence (walk_id, path, size_bytes, last_modified, creation_time)"
                            + " VALUES (?, ?, 1, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')")) {
                for (int row = 0; row < MANY; row++) {
                    occurrence.setLong(1, walk.value());
                    occurrence.setString(2, "f" + row + ".txt");
                    occurrence.addBatch();
                }
                occurrence.executeBatch();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO extraction_metric (occurrence_id, run_id, status, processing_time, character_count,"
                            + " alphanumeric_char_count, word_count, word_character_length_total,"
                            + " vowelless_word_count, single_character_word_count)"
                            + " SELECT id, ?, 'success', 1.0, 1, 1, 1, 1, 0, 0 FROM file_occurrence WHERE walk_id = ?")) {
                insert.setString(1, stage2.value());
                insert.setLong(2, walk.value());
                insert.executeUpdate();
            }
            connection.commit();
            connection.setAutoCommit(true);
        }
        Recorder recorder = new Recorder();

        new ConfidenceDistribution(jdbcTemplate, ledger).measure(stage3, stage2, recorder);

        List<String> expected = new ArrayList<>();
        expected.add(starting(ExtractionStatement.EXTRACTION_METRICS, OptionalLong.of(MANY)));
        ROWS_AFTER_EACH_PAGE.forEach(rows -> expected.add(rowsReadCall(ExtractionStatement.EXTRACTION_METRICS, rows)));
        expected.add(ended(ExtractionStatement.EXTRACTION_METRICS));
        claim(
                "the read is started over up to " + MANY + " rows, told " + ROWS_AFTER_EACH_PAGE + " rows read as"
                        + " each page of a thousand survivors is done, and ended: the rows read so far each time,"
                        + " and no step of SQLite's between",
                () -> assertThat(recorder.calls).containsExactlyElementsOf(expected));
        claim("and neither connection of the pool carries a handler afterwards", () -> assertThat(pool.handlersLeft())
                .isZero());
    }

    /**
     * ADR-204 section 4, "on every path but one that throws", for the read of the metrics: the table is
     * dropped once the read has been announced, so the read itself is what fails.
     */
    @Test
    @Story("Measuring the confidence spread says what it is reading")
    @DisplayName("A read of the metrics that throws is not said to have ended, and leaves no handler behind")
    void aReadThatThrowsIsNotSaidToHaveEnded() {
        ledger.occurrences().fileOccurrence(walk, new OccurrencePath("a.txt"), 1, Instant.EPOCH, Instant.EPOCH);
        jdbcTemplate.update(
                METRIC_ROW, ledger.occurrences().occurrenceId(walk, new OccurrencePath("a.txt")).orElseThrow().value(), stage2.value());
        Recorder recorder = new Recorder() {
            @Override
            public void statementStarting(ExtractionStatement statement, OptionalLong rowsUpTo) {
                super.statementStarting(statement, rowsUpTo);
                if (statement == ExtractionStatement.EXTRACTION_METRICS) {
                    // Between the bound and the read, so the read itself is what fails.
                    jdbcTemplate.execute("DROP TABLE extraction_metric");
                }
            }
        };

        claim(
                "the measurement fails as the template reports any statement's failure, once the table is gone",
                () -> assertThatThrownBy(() -> new ConfidenceDistribution(jdbcTemplate, ledger)
                                .measure(stage3, stage2, recorder))
                        .isInstanceOf(DataAccessException.class));
        claim(
                "the caller was told the read started, over the one row the run held, and never that it read a row"
                        + " or that it ended",
                () -> assertThat(recorder.calls)
                        .containsExactly(starting(ExtractionStatement.EXTRACTION_METRICS, OptionalLong.of(1))));
        claim("and neither connection of the pool carries a handler afterwards", () -> assertThat(pool.handlersLeft())
                .isZero());
    }

    private static String starting(ExtractionStatement statement, OptionalLong rowsUpTo) {
        return "statementStarting(" + statement + ", " + rowsUpTo + ")";
    }

    private static String rowsReadCall(ExtractionStatement statement, long rows) {
        return "rowsRead(" + statement + ", " + rows + ")";
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
            calls.add("stepsTaken(" + statement + ", " + steps + ")");
        }

        /** ADR-211's callback, written without {@code @Override} so the class compiles before it exists. */
        public void rowsRead(ExtractionStatement statement, long rows) {
            calls.add(rowsReadCall(statement, rows));
        }

        @Override
        public void statementEnded(ExtractionStatement statement) {
            calls.add(ended(statement));
        }
    }
}
