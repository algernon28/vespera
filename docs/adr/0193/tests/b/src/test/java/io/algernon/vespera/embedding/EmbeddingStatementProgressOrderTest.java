package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * What {@code embedding} tells its caller around each statement of {@code SeedCorpusComparison.measure}, and
 * in which order (ADR-193 sections 6 to 8, ADR-199 section 4, #411): its two drains, of the corpus survivors
 * and of the seed walk's occurrences, each started with no total and ended; then its three counted reads, of
 * the unusable seeds, of the corpus survivors' extraction metrics under stage 2's run and of the seeds' under
 * the measurement run, each started with the span of its run's rows, or with an empty total where the run
 * holds none, and ended.
 *
 * <p><b>Parked under {@code docs/adr/0193/tests/b/}</b>: it names {@code EmbeddingStatement}, {@code
 * EmbeddingStatementProgress} and an overload of {@code measure} that part (b) adds, and does not compile
 * before. It runs on {@link PoolOfTwo}, so a counted read handed back to the template would report no steps
 * over many rows.
 */
@Epic("Relevance")
@Feature("Progress reporting")
@Issue("411")
@Link(name = "ADR-193", url = Adr.STATEMENTS_REPORT_THEIR_PROGRESS, type = "adr")
@Link(name = "ADR-199", url = Adr.THE_STATEMENTS_ADR_193_LEFT_UNNAMED_TAKE_ITS_RULE, type = "adr")
class EmbeddingStatementProgressOrderTest {

    /** Enough rows, at 5 or at 12 steps a row, for at least one callback of SQLite's handler in each read. */
    private static final int MANY = 30_000;

    private static final String METRIC_ROW = "INSERT INTO extraction_metric (occurrence_id, run_id, status,"
            + " processing_time, character_count, alphanumeric_char_count, word_count,"
            + " word_character_length_total, vowelless_word_count, single_character_word_count)"
            + " VALUES (?, ?, 'success', 0.5, 600, 500, 100, 500, 0, 0)";

    @TempDir
    Path folder;

    private PoolOfTwo pool;
    private JdbcTemplate jdbcTemplate;
    private Ledger ledger;
    private WalkId corpusWalk;
    private WalkId seedWalk;
    private RunId extractionRun;
    private RunId measurementRun;

    @BeforeEach
    void aPoolOfTwoACorpusWalkAndASeedWalk() throws SQLException, IOException {
        pool = new PoolOfTwo(folder);
        jdbcTemplate = pool.jdbcTemplate();
        ledger = new Ledger(jdbcTemplate);
        corpusWalk = ledger.startWalk(Path.of("C:/corpus/statements"));
        seedWalk = ledger.startWalk(Path.of("C:/seeds/statements"));
        extractionRun = ledger.startRun("extraction", "extraction-statements", "{}", corpusWalk, List.of());
        measurementRun = ledger.startRun(
                "seed-measurement", "measurement-statements", "{}", corpusWalk, List.of(extractionRun));
    }

    @AfterEach
    void closeThePool() {
        pool.close();
    }

    @Test
    @Story("Comparing the seeds with the collection says what it is reading")
    @DisplayName("The comparison starts and ends its two drains with no total, and then its three reads with the span of each run's rows")
    void theFiveStatementsAreReportedInTheOrderTheyAreIssued() {
        measured(corpusWalk, "kept.txt", extractionRun);
        measured(seedWalk, "seed.txt", measurementRun);
        OccurrenceId unusable = measured(seedWalk, "empty.pdf", measurementRun);
        new UnusableSeeds(jdbcTemplate).record(unusable, measurementRun, "recorded unusable by this test");
        Recorder recorder = new Recorder();

        new SeedCorpusComparison(jdbcTemplate, ledger).measure(measurementRun, extractionRun, seedWalk, recorder);

        claim(
                "each statement is started and ended once, in the order the comparison issues them: the two drains"
                        + " with no total, the one unusable seed, the one metric row under stage 2's run, and"
                        + " the two under the measurement run, the unusable seed's among them",
                () -> assertThat(recorder.calls)
                        .containsExactly(
                                starting(EmbeddingStatement.CORPUS_SURVIVORS, OptionalLong.empty()),
                                ended(EmbeddingStatement.CORPUS_SURVIVORS),
                                starting(EmbeddingStatement.SEED_OCCURRENCES, OptionalLong.empty()),
                                ended(EmbeddingStatement.SEED_OCCURRENCES),
                                starting(EmbeddingStatement.UNUSABLE_SEEDS, OptionalLong.of(1)),
                                ended(EmbeddingStatement.UNUSABLE_SEEDS),
                                starting(EmbeddingStatement.CORPUS_METRICS, OptionalLong.of(1)),
                                ended(EmbeddingStatement.CORPUS_METRICS),
                                starting(EmbeddingStatement.SEED_METRICS, OptionalLong.of(2)),
                                ended(EmbeddingStatement.SEED_METRICS)));
    }

    @Test
    @Story("Comparing the seeds with the collection says what it is reading")
    @DisplayName("With no unusable seed recorded, that read is started with an empty total and ended")
    void withNoUnusableSeedThatReadIsStartedWithAnEmptyTotal() {
        measured(corpusWalk, "kept.txt", extractionRun);
        measured(seedWalk, "seed.txt", measurementRun);
        Recorder recorder = new Recorder();

        new SeedCorpusComparison(jdbcTemplate, ledger).measure(measurementRun, extractionRun, seedWalk, recorder);

        claim(
                "the read of the unusable seeds is started with an empty total and ended, between the drains and"
                        + " the reads of the metrics: the caller, not embedding, decides that nothing is said",
                () -> assertThat(recorder.calls)
                        .containsSubsequence(
                                ended(EmbeddingStatement.SEED_OCCURRENCES),
                                starting(EmbeddingStatement.UNUSABLE_SEEDS, OptionalLong.empty()),
                                ended(EmbeddingStatement.UNUSABLE_SEEDS),
                                starting(EmbeddingStatement.CORPUS_METRICS, OptionalLong.of(1))));
    }

    @Test
    @Story("Comparing the seeds with the collection says what it is reading")
    @DisplayName("Each of the comparison's three reads over many rows reports its steps between its start and its end")
    void eachCountedReadOverManyRowsReportsItsSteps() throws SQLException {
        measured(corpusWalk, "kept.txt", extractionRun);
        measured(seedWalk, "seed.txt", measurementRun);
        many("INSERT INTO unusable_seed (occurrence_id, run_id, reason) VALUES (?, ?, 'reason')", measurementRun);
        many(METRIC_ROW, extractionRun);
        many(METRIC_ROW, measurementRun);
        Recorder recorder = new Recorder();

        new SeedCorpusComparison(jdbcTemplate, ledger).measure(measurementRun, extractionRun, seedWalk, recorder);

        for (EmbeddingStatement read :
                List.of(EmbeddingStatement.UNUSABLE_SEEDS, EmbeddingStatement.CORPUS_METRICS, EmbeddingStatement.SEED_METRICS)) {
            claim(
                    "the steps of " + read + " are reported after it is started and before it is ended, a"
                            + " hundred thousand first: with a second connection in the pool and no transaction,"
                            + " the read ran on the one the handler was set on",
                    () -> {
                        int started = indexOfCallOpening(recorder.calls, "statementStarting(" + read + ", ");
                        int ended = recorder.calls.indexOf(ended(read));
                        assertThat(started).isNotNegative();
                        assertThat(ended).isGreaterThan(started + 1);
                        assertThat(recorder.calls.get(started + 1)).isEqualTo("stepsTaken(" + read + ", 100000)");
                        assertThat(recorder.calls.subList(started + 1, ended))
                                .allMatch(call -> call.startsWith("stepsTaken(" + read + ", "));
                    });
        }
        claim("and neither connection of the pool carries a handler afterwards", () -> assertThat(pool.handlersLeft())
                .isZero());
    }

    private static int indexOfCallOpening(List<String> calls, String opening) {
        for (int i = 0; i < calls.size(); i++) {
            if (calls.get(i).startsWith(opening)) {
                return i;
            }
        }
        return -1;
    }

    private static String starting(EmbeddingStatement statement, OptionalLong rowsUpTo) {
        return "statementStarting(" + statement + ", " + rowsUpTo + ")";
    }

    private static String ended(EmbeddingStatement statement) {
        return "statementEnded(" + statement + ")";
    }

    /** Every callback, in the order it came, written as the call it was. */
    private static final class Recorder implements EmbeddingStatementProgress {

        final List<String> calls = new ArrayList<>();

        @Override
        public void statementStarting(EmbeddingStatement statement, OptionalLong rowsUpTo) {
            calls.add(starting(statement, rowsUpTo));
        }

        @Override
        public void stepsTaken(EmbeddingStatement statement, long steps) {
            calls.add("stepsTaken(" + statement + ", " + steps + ")");
        }

        @Override
        public void statementEnded(EmbeddingStatement statement) {
            calls.add(ended(statement));
        }
    }

    /** An occurrence of {@code walk} at {@code path}, with a metric row under {@code run}. */
    private OccurrenceId measured(WalkId walk, String path, RunId run) {
        ledger.fileOccurrence(
                walk, new OccurrencePath(path), 1L, Instant.parse("2026-09-06T10:15:30Z"),
                Instant.parse("2026-09-01T08:00:00Z"));
        OccurrenceId occurrence = ledger.occurrenceId(walk, new OccurrencePath(path)).orElseThrow();
        jdbcTemplate.update(METRIC_ROW, occurrence.value(), run.value());
        return occurrence;
    }

    /**
     * {@link #MANY} rows under {@code run}, each against an occurrence number no walk here holds, so the read
     * goes through them and the comparison counts none; foreign keys are not enforced on this pool.
     */
    private void many(String insertRow, RunId run) throws SQLException {
        try (Connection connection = pool.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement(insertRow)) {
                for (int row = 1; row <= MANY; row++) {
                    insert.setLong(1, 1_000_000L + row);
                    insert.setString(2, run.value());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            connection.commit();
            connection.setAutoCommit(true);
        }
    }
}
