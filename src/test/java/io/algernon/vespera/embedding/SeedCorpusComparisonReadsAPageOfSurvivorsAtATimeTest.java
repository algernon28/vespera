package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.PoolOfTwo;
import io.algernon.vespera.StatementLog;
import io.algernon.vespera.extraction.ExtractionMetrics;
import io.algernon.vespera.extraction.LanguageDetection;
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
import java.util.Collection;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.LongConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The seed/corpus comparison asks for the corpus side's measurements a page of survivors at a time, by name,
 * and holds no set of the survivors (ADR-211 sections 1 and 2).
 *
 * <p>The comparison reads {@code extraction}'s rows through the {@link MeasuredForms} it is handed (ADR-209
 * section 3.2). The one handed here reads them as {@link RecordedForms} does and puts a note among the
 * statements {@link StatementLog} keeps each time it is asked, so the order of the ledger's pages of
 * survivors and of the comparison's questions can be read back. At {@code 4b99a03} the three pages are all
 * read first, into a set, and then every row of stage 2's run is asked for through {@code each}. After
 * ADR-211 each page is followed by one {@code eachOf} naming that page's survivors, and {@code each} is asked
 * only for the seeds' rows under the measurement run.
 *
 * <p>{@code eachOf} is written without {@code @Override}: it is the method ADR-211 adds to {@link
 * MeasuredForms}, and this class compiles before it exists and implements it after.
 */
@Epic("Relevance")
@Feature("Seed/corpus comparison")
@Issue("456")
@Link(name = "ADR-211", url = Adr.NO_CLASS_HOLDS_EVERY_SURVIVOR_OF_A_RUN, type = "adr")
class SeedCorpusComparisonReadsAPageOfSurvivorsAtATimeTest {

    /** Two full pages of the ledger's 1,000 and a short third. */
    private static final int SURVIVORS = 2_500;

    /** One occurrence in every 51 is ruled out under stage 2, so they lie among the survivors' ids. */
    private static final int ONE_IN = 51;

    /** The occurrences recorded: the survivors and one ruled out after every fifty of them. */
    private static final int RECORDED = SURVIVORS + SURVIVORS / (ONE_IN - 1);

    /** The most occurrences a page of the ledger holds, and so the most one question may name. */
    private static final int A_PAGE = 1_000;

    private static final String METRIC_ROW = "INSERT INTO extraction_metric (occurrence_id, run_id, status,"
            + " processing_time, character_count, alphanumeric_char_count, word_count,"
            + " word_character_length_total, vowelless_word_count, single_character_word_count)"
            + " VALUES (?, ?, 'success', 0.5, 600, 500, 100, 500, 0, 0)";

    private static final String PAGE = "a page of the survivors";
    private static final String NAMED = "eachOf";
    private static final String EVERY = "each";

    @TempDir
    Path folder;

    private PoolOfTwo pool;
    private WalkId seedWalk;
    private RunId extractionRun;
    private RunId measurementRun;

    @BeforeEach
    void survivorsWithMetricRowsAndOneSeed() throws SQLException, IOException {
        pool = new PoolOfTwo(folder);
        JdbcTemplate jdbcTemplate = pool.jdbcTemplate();
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId corpusWalk = ledger.walks().startWalk(Path.of("C:/corpus/comparison-pages"));
        seedWalk = ledger.walks().startWalk(Path.of("C:/seeds/comparison-pages"));
        extractionRun = ledger.runs().startRun("extraction", "x211", "{}", corpusWalk, List.of());
        measurementRun = ledger.runs().startRun("seed-measurement", "m211", "{}", corpusWalk, List.of(extractionRun));
        try (Connection connection = pool.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement occurrence = connection.prepareStatement(
                    "INSERT INTO file_occurrence (walk_id, path, size_bytes, last_modified, creation_time)"
                            + " VALUES (?, ?, 1, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')")) {
                for (int i = 0; i < RECORDED; i++) {
                    occurrence.setLong(1, corpusWalk.value());
                    occurrence.setString(2, "f" + i + ".txt");
                    occurrence.addBatch();
                }
                occurrence.executeBatch();
            }
            connection.commit();
            connection.setAutoCommit(true);
        }
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM file_occurrence WHERE walk_id = ? ORDER BY id", Long.class, corpusWalk.value());
        try (Connection connection = pool.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement metric = connection.prepareStatement(METRIC_ROW);
                    PreparedStatement ruledOut = connection.prepareStatement(
                            "INSERT INTO verdict (occurrence_id, run_id, kind, reason)"
                                    + " VALUES (?, ?, 'DEGENERATE_OUTPUT', 'ruled out by this test')")) {
                for (int i = 0; i < ids.size(); i++) {
                    metric.setLong(1, ids.get(i));
                    metric.setString(2, extractionRun.value());
                    metric.addBatch();
                    if (i % ONE_IN == ONE_IN - 1) {
                        ruledOut.setLong(1, ids.get(i));
                        ruledOut.setString(2, extractionRun.value());
                        ruledOut.addBatch();
                    }
                }
                metric.executeBatch();
                ruledOut.executeBatch();
            }
            connection.commit();
            connection.setAutoCommit(true);
        }
        ledger.occurrences().fileOccurrence(
                seedWalk, new OccurrencePath("seed.txt"), 1L, Instant.parse("2026-09-06T10:15:30Z"),
                Instant.parse("2026-09-01T08:00:00Z"));
        OccurrenceId seed = ledger.occurrences().occurrenceId(seedWalk, new OccurrencePath("seed.txt")).orElseThrow();
        jdbcTemplate.update(METRIC_ROW, seed.value(), measurementRun.value());
    }

    @AfterEach
    void closeThePool() {
        pool.close();
    }

    @Test
    @Story("Comparing the seeds with the collection")
    @DisplayName("The comparison asks for the measurements of one page of surviving documents at a time, by name")
    void asksForTheCorpusSideOnePageOfSurvivorsAtATime() {
        StatementLog log = new StatementLog(pool.jdbcTemplate().getDataSource());
        JdbcTemplate logged = log.jdbcTemplate();
        Asked asked = new Asked(log, new ExtractionMetrics(logged, new LanguageDetection()));

        SeedCorpusComparison.Comparison comparison =
                new SeedCorpusComparison(logged, new Ledger(logged)).measure(measurementRun, extractionRun, seedWalk, asked);

        claim(
                "the corpus side is the " + SURVIVORS + " surviving documents with a measurement, and none of the"
                        + " ruled-out ones",
                () -> assertThat(comparison.corpusDocumentCount()).isEqualTo(SURVIVORS));
        List<String> order = log.said().stream()
                .map(SeedCorpusComparisonReadsAPageOfSurvivorsAtATimeTest::kind)
                .filter(kind -> !kind.isEmpty())
                .toList();
        claim(
                "each page of the survivors is followed by one question naming that page's documents, before the"
                        + " next page is asked for, three of each; and only then are every row of a run asked for,"
                        + " once, for the seeds",
                () -> assertThat(order).containsExactly(PAGE, NAMED, PAGE, NAMED, PAGE, NAMED, EVERY));
        claim(
                "the one question for every row of a run is about the seeds' run, never about the run that"
                        + " measured the collection",
                () -> assertThat(asked.everyRowOf).containsExactly(measurementRun));
        claim(
                "each question by name names at most " + A_PAGE + " documents, and together they name the "
                        + SURVIVORS + " survivors",
                () -> {
                    assertThat(asked.namedAtOnce).allSatisfy(named -> assertThat(named).isBetween(1, A_PAGE));
                    assertThat(asked.namedAtOnce.stream().mapToInt(Integer::intValue).sum()).isEqualTo(SURVIVORS);
                });
    }

    private static String kind(String said) {
        if (said.contains("FROM file_occurrence") && said.contains("NOT EXISTS") && said.contains(" LIMIT ")) {
            return PAGE;
        }
        if (said.startsWith(NAMED + " ")) {
            return NAMED;
        }
        if (said.startsWith(EVERY + " ")) {
            return EVERY;
        }
        return "";
    }

    /** {@code extraction}'s rows, read as {@link RecordedForms} reads them, with a note each time it is asked. */
    private static final class Asked implements MeasuredForms {

        private final StatementLog log;
        private final ExtractionMetrics metrics;
        final List<RunId> everyRowOf = new ArrayList<>();
        final List<Integer> namedAtOnce = new ArrayList<>();

        Asked(StatementLog log, ExtractionMetrics metrics) {
            this.log = log;
            this.metrics = metrics;
        }

        @Override
        public OptionalLong rowsUpTo(RunId runId) {
            return metrics.metricRowsUpTo(runId);
        }

        @Override
        public void each(RunId runId, LongConsumer stepsTaken, Row row) {
            log.note(EVERY + " " + runId.value());
            everyRowOf.add(runId);
            metrics.eachMeasuredForm(runId, stepsTaken, row::read);
        }

        /** The rows of the named occurrences under {@code runId}, picked from the run's rows as they are read. */
        public void eachOf(RunId runId, Collection<OccurrenceId> occurrences, Row row) {
            log.note(NAMED + " " + runId.value());
            namedAtOnce.add(occurrences.size());
            Set<OccurrenceId> named = Set.copyOf(occurrences);
            metrics.eachMeasuredForm(
                    runId,
                    ignored -> {},
                    (occurrence, language, meanScoreIsNull, words, pages, vowelless, singleCharacter) -> {
                        if (named.contains(occurrence)) {
                            row.read(occurrence, language, meanScoreIsNull, words, pages, vowelless, singleCharacter);
                        }
                    });
        }
    }
}
