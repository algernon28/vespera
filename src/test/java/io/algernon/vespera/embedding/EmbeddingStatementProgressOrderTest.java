package io.algernon.vespera.embedding;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * What {@code embedding} tells its caller around each statement of {@code SeedCorpusComparison.measure}, and
 * in which order (ADR-193 sections 6 to 8, ADR-204 section 4, #411): its two drains, of the corpus survivors
 * and of the seed walk's occurrences, each started with no total and ended; then its three counted reads, of
 * the unusable seeds, of the corpus survivors' extraction metrics under stage 2's run and of the seeds' under
 * the measurement run, each started with the span of its run's rows, or with an empty total where the run
 * holds none, and ended.
 *
 * <p>It runs on {@link PoolOfTwo}, so a counted read handed back to the template would report no steps over
 * many rows.
 */
@Epic("Relevance")
@Feature("Progress reporting")
@Issue("411")
@Link(name = "ADR-193", url = Adr.STATEMENTS_REPORT_THEIR_PROGRESS, type = "adr")
@Link(name = "ADR-204", url = Adr.PART_B_OF_THE_STATEMENTS_WRITTEN_OUT, type = "adr")
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
        corpusWalk = ledger.walks().startWalk(Path.of("C:/corpus/statements"));
        seedWalk = ledger.walks().startWalk(Path.of("C:/seeds/statements"));
        extractionRun = ledger.runs().startRun("extraction", "extraction-statements", "{}", corpusWalk, List.of());
        measurementRun = ledger.runs().startRun(
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

        new SeedCorpusComparison(jdbcTemplate, ledger).measure(measurementRun, extractionRun, seedWalk, RecordedForms.over(jdbcTemplate), recorder);

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

        new SeedCorpusComparison(jdbcTemplate, ledger).measure(measurementRun, extractionRun, seedWalk, RecordedForms.over(jdbcTemplate), recorder);

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

        new SeedCorpusComparison(jdbcTemplate, ledger).measure(measurementRun, extractionRun, seedWalk, RecordedForms.over(jdbcTemplate), recorder);

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

    /**
     * ADR-204 section 4, "on every path but one that throws", for a counted statement of {@code embedding}:
     * the table is dropped once the read has been announced, so the read itself is what fails.
     */
    @Test
    @Story("Comparing the seeds with the collection says what it is reading")
    @DisplayName("A read of the unusable seeds that throws is not said to have ended, and leaves no handler behind")
    void aCountedReadThatThrowsIsNotSaidToHaveEnded() throws SQLException {
        measured(corpusWalk, "kept.txt", extractionRun);
        OccurrenceId unusable = measured(seedWalk, "empty.pdf", measurementRun);
        new UnusableSeeds(jdbcTemplate).record(unusable, measurementRun, "recorded unusable by this test");
        Recorder recorder = new DroppingATableWhenStarted(EmbeddingStatement.UNUSABLE_SEEDS, "unusable_seed");

        claim(
                "the comparison fails as the template reports any statement's failure, once the table is gone",
                () -> assertThatThrownBy(() -> new SeedCorpusComparison(jdbcTemplate, ledger)
                                .measure(measurementRun, extractionRun, seedWalk, RecordedForms.over(jdbcTemplate), recorder))
                        .isInstanceOf(DataAccessException.class));
        claim(
                "the caller was told both drains started and ended and the read started, over the "
                        + ONE_UNUSABLE_SEED + " row the run held, and never that the read ended",
                () -> assertThat(recorder.calls)
                        .containsExactly(
                                starting(EmbeddingStatement.CORPUS_SURVIVORS, OptionalLong.empty()),
                                ended(EmbeddingStatement.CORPUS_SURVIVORS),
                                starting(EmbeddingStatement.SEED_OCCURRENCES, OptionalLong.empty()),
                                ended(EmbeddingStatement.SEED_OCCURRENCES),
                                starting(EmbeddingStatement.UNUSABLE_SEEDS, OptionalLong.of(ONE_UNUSABLE_SEED))));
        claim("and neither connection of the pool carries a handler afterwards", () -> assertThat(pool.handlersLeft())
                .isZero());
    }

    /**
     * The same for a timed statement, which has no handler to leave: the drain of the seed walk's
     * occurrences, which fails once the table it reads them from is gone.
     */
    @Test
    @Story("Comparing the seeds with the collection says what it is reading")
    @DisplayName("A drain of the seed folder's files that throws is not said to have ended")
    void aTimedDrainThatThrowsIsNotSaidToHaveEnded() {
        measured(corpusWalk, "kept.txt", extractionRun);
        Recorder recorder = new DroppingATableWhenStarted(EmbeddingStatement.SEED_OCCURRENCES, "file_occurrence");

        claim(
                "the comparison fails on the database's own refusal, the table the drain reads being gone",
                () -> assertThatThrownBy(() -> new SeedCorpusComparison(jdbcTemplate, ledger)
                                .measure(measurementRun, extractionRun, seedWalk, RecordedForms.over(jdbcTemplate), recorder))
                        .hasRootCauseInstanceOf(SQLException.class));
        claim(
                "the caller was told the first drain started and ended and the second started, with no total, and"
                        + " never that it ended; no read was announced after it",
                () -> assertThat(recorder.calls)
                        .containsExactly(
                                starting(EmbeddingStatement.CORPUS_SURVIVORS, OptionalLong.empty()),
                                ended(EmbeddingStatement.CORPUS_SURVIVORS),
                                starting(EmbeddingStatement.SEED_OCCURRENCES, OptionalLong.empty())));
    }

    /** The one unusable seed the test of a read that throws records. */
    private static final int ONE_UNUSABLE_SEED = 1;

    /** A recorder that drops {@code table} when {@code statement} is announced, so that statement is what fails. */
    private final class DroppingATableWhenStarted extends Recorder {

        private final EmbeddingStatement statement;
        private final String table;

        DroppingATableWhenStarted(EmbeddingStatement statement, String table) {
            this.statement = statement;
            this.table = table;
        }

        @Override
        public void statementStarting(EmbeddingStatement started, OptionalLong rowsUpTo) {
            super.statementStarting(started, rowsUpTo);
            if (started == statement) {
                jdbcTemplate.execute("DROP TABLE " + table);
            }
        }
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
    private static class Recorder implements EmbeddingStatementProgress {

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
        ledger.occurrences().fileOccurrence(
                walk, new OccurrencePath(path), 1L, Instant.parse("2026-09-06T10:15:30Z"),
                Instant.parse("2026-09-01T08:00:00Z"));
        OccurrenceId occurrence = ledger.occurrences().occurrenceId(walk, new OccurrencePath(path)).orElseThrow();
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
