package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.PoolOfTwo;
import io.algernon.vespera.StatementLog;
import io.algernon.vespera.ledger.Ledger;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The confidence distribution reads stage 2's metric rows a page of survivors at a time, and holds no set of
 * the survivors (ADR-211 sections 1, 2 and 7).
 *
 * <p>Every statement the measurement makes is kept, in order, through {@link StatementLog}: the pages of
 * the survivors the ledger reads, and the reads of {@code extraction_metric}. At {@code 4b99a03} the three
 * pages are all read first, into a set, and then one read goes through every metric row of the run. After
 * ADR-211 each page is followed by one read of that page's rows, by key.
 *
 * <p>Over 2,500 survivors, so the survivors come in three pages, and 50 occurrences ruled out under stage 2
 * among them, each with a metric row whose score would land in a grade if it were counted.
 */
@Epic("Extraction")
@Feature("Content census")
@Issue("456")
@Link(name = "ADR-211", url = Adr.NO_CLASS_HOLDS_EVERY_SURVIVOR_OF_A_RUN, type = "adr")
@Link(name = "ADR-075", url = Adr.STAGE_3_WRITES_A_CONFIDENCE_DISTRIBUTION_REPORT, type = "adr")
class ConfidenceDistributionReadsAPageOfSurvivorsAtATimeTest {

    /** Two full pages of the ledger's 1,000 and a short third. */
    private static final int SURVIVORS = 2_500;

    /** One occurrence in every 51 is ruled out under stage 2, so they lie among the survivors' ids. */
    private static final int ONE_IN = 51;

    /** The occurrences recorded: the survivors and one ruled out after every fifty of them. */
    private static final int RECORDED = SURVIVORS + SURVIVORS / (ONE_IN - 1);

    /** The most occurrences a page of the ledger holds, and so the most one read of rows may name. */
    private static final int A_PAGE = 1_000;

    /** A score in each grade, poor, fair, good and excellent, given to the survivors in turn. */
    private static final double[] ONE_SCORE_A_GRADE = {0.30, 0.65, 0.85, 0.95};

    /** How many survivors land in each grade: a quarter of them. */
    private static final long A_QUARTER = SURVIVORS / ONE_SCORE_A_GRADE.length;

    private static final String PAGE = "a page of the survivors";
    private static final String ROWS = "the metric rows";

    @TempDir
    Path folder;

    private PoolOfTwo pool;
    private Ledger ledger;
    private RunId stage2;
    private RunId stage3;

    @BeforeEach
    void survivorsWithMetricRowsAndSomeRuledOutAmongThem() throws SQLException, IOException {
        pool = new PoolOfTwo(folder);
        JdbcTemplate jdbcTemplate = pool.jdbcTemplate();
        ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus-confidence-pages"));
        stage2 = ledger.runs().startRun("extraction", "x211", "{}", walk, List.of());
        stage3 = ledger.runs().startRun("content-census", "y211", "{}", walk, List.of(stage2));
        try (Connection connection = pool.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement occurrence = connection.prepareStatement(
                    "INSERT INTO file_occurrence (walk_id, path, size_bytes, last_modified, creation_time)"
                            + " VALUES (?, ?, 1, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')")) {
                for (int i = 0; i < RECORDED; i++) {
                    occurrence.setLong(1, walk.value());
                    occurrence.setString(2, "f" + i + ".pdf");
                    occurrence.addBatch();
                }
                occurrence.executeBatch();
            }
            connection.commit();
            connection.setAutoCommit(true);
        }
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM file_occurrence WHERE walk_id = ? ORDER BY id", Long.class, walk.value());
        try (Connection connection = pool.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement metric = connection.prepareStatement(
                            "INSERT INTO extraction_metric (occurrence_id, run_id, status, mean_score, processing_time,"
                                    + " character_count, alphanumeric_char_count, word_count,"
                                    + " word_character_length_total, vowelless_word_count, single_character_word_count)"
                                    + " VALUES (?, ?, 'success', ?, 1.0, 1, 1, 1, 1, 0, 0)");
                    PreparedStatement ruledOut = connection.prepareStatement(
                            "INSERT INTO verdict (occurrence_id, run_id, kind, reason)"
                                    + " VALUES (?, ?, 'DEGENERATE_OUTPUT', 'ruled out by this test')")) {
                int survivor = 0;
                for (int i = 0; i < ids.size(); i++) {
                    boolean out = i % ONE_IN == ONE_IN - 1;
                    metric.setLong(1, ids.get(i));
                    metric.setString(2, stage2.value());
                    // A ruled-out occurrence carries an excellent score, so counting it would show.
                    metric.setDouble(3, out ? ONE_SCORE_A_GRADE[3] : ONE_SCORE_A_GRADE[survivor++ % 4]);
                    metric.addBatch();
                    if (out) {
                        ruledOut.setLong(1, ids.get(i));
                        ruledOut.setString(2, stage2.value());
                        ruledOut.addBatch();
                    }
                }
                metric.executeBatch();
                ruledOut.executeBatch();
            }
            connection.commit();
            connection.setAutoCommit(true);
        }
    }

    @AfterEach
    void closeThePool() {
        pool.close();
    }

    @Test
    @Story("The corpus-wide confidence distribution")
    @DisplayName("The confidence spread reads the scores of one page of surviving documents at a time, and counts only theirs")
    void readsTheScoresOfOnePageOfSurvivorsAtATime() {
        StatementLog log = new StatementLog(pool.jdbcTemplate().getDataSource());
        JdbcTemplate logged = log.jdbcTemplate();

        ConfidenceDistribution.Distribution distribution =
                new ConfidenceDistribution(logged, new Ledger(logged)).measure(stage3, stage2);

        Map<String, Long> perGrade = distribution.buckets().stream()
                .collect(Collectors.toMap(ConfidenceDistribution.Bucket::grade, ConfidenceDistribution.Bucket::documentCount));
        claim(
                "each of the four grades counts a quarter of the " + SURVIVORS + " surviving documents, " + A_QUARTER
                        + ", and none of the ruled-out ones, which all scored excellent",
                () -> assertThat(perGrade)
                        .containsEntry("poor", A_QUARTER)
                        .containsEntry("fair", A_QUARTER)
                        .containsEntry("good", A_QUARTER)
                        .containsEntry("excellent", A_QUARTER));
        List<String> reads = log.said().stream().map(ConfidenceDistributionReadsAPageOfSurvivorsAtATimeTest::kind)
                .filter(kind -> !kind.isEmpty()).toList();
        claim(
                "each page of the survivors is followed by one read of that page's metric rows, before the next"
                        + " page is asked for: three of each, alternating, so no more than a page of survivors is"
                        + " ever held",
                () -> assertThat(reads).containsExactly(PAGE, ROWS, PAGE, ROWS, PAGE, ROWS));
        claim(
                "each read of the metric rows names the documents it wants, at most " + A_PAGE + " of them, and"
                        + " goes through no other document's row",
                () -> assertThat(log.said().stream().filter(sql -> kind(sql).equals(ROWS)).toList())
                        .isNotEmpty()
                        .allSatisfy(sql -> {
                            assertThat(sql).contains("occurrence_id IN (");
                            assertThat(occurrencesNamedBy(sql)).isBetween(1, A_PAGE);
                        }));
    }

    private static String kind(String sql) {
        if (sql.contains("FROM file_occurrence") && sql.contains("NOT EXISTS") && sql.contains(" LIMIT ")) {
            return PAGE;
        }
        if (sql.contains("FROM extraction_metric") && sql.contains("mean_score")) {
            return ROWS;
        }
        return "";
    }

    /** The placeholders inside the statement's list of occurrences, and no other. */
    private static int occurrencesNamedBy(String sql) {
        int from = sql.indexOf("occurrence_id IN (") + "occurrence_id IN (".length();
        String list = sql.substring(from, sql.indexOf(')', from));
        return (int) list.chars().filter(character -> character == '?').count();
    }
}
