package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.PoolOfTwo;
import io.algernon.vespera.StatementLog;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Stage 5e counts the documents scored below the relevance floor, then reads them a page at a time to write
 * their verdicts, and holds no list of them (ADR-220 section 5). Until ADR-220 it read every one of them into a
 * list first, sorted by the database, as many as nine in ten of the scored documents where a floor is high.
 *
 * <p>2,500 scores under the run asked about, written in the reverse of their documents' order: 1,800 below the
 * floor, one exactly on it, which stays, and the rest above it. Another run scored some of the same documents
 * below the floor, and nothing of it may be read.
 *
 * <p>It names the two methods ADR-220 adds, so the test tree does not compile until they exist.
 */
@Epic("Relevance")
@Feature("Relevance threshold")
@Issue("458")
@Link(name = "ADR-220", url = Adr.NO_CLASS_HOLDS_EVERY_OCCURRENCE_OF_A_RUN, type = "adr")
class ScoresBelowTheFloorAreReadAPageAtATimeTest {

    private static final int SCORED = 2_500;

    /** The first so many, by document, score below the floor. */
    private static final int BELOW = 1_800;

    private static final double FLOOR = 0.5;

    private static final double UNDER = 0.3;

    private static final double OVER = 0.7;

    /** Documents the other run scored below the floor. */
    private static final int OTHER_RUNS_BELOW = 300;

    /** The most documents a page holds. */
    private static final int A_PAGE = 1_000;

    @TempDir
    Path folder;

    private PoolOfTwo pool;
    private RunId scoring;
    private final List<OccurrenceId> belowInTheOrderWritten = new ArrayList<>();

    @BeforeEach
    void scoresOnBothSidesOfTheFloor() throws SQLException, IOException {
        pool = new PoolOfTwo(folder);
        JdbcTemplate jdbcTemplate = pool.jdbcTemplate();
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus-below-the-floor"));
        scoring = ledger.runs().startRun("embedding-scoring", "c214", "{}", walk, List.of());
        RunId other = ledger.runs().startRun("embedding-scoring", "d214", "{}", walk, List.of());
        try (Connection connection = pool.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement occurrence = connection.prepareStatement(
                    "INSERT INTO file_occurrence (walk_id, path, size_bytes, last_modified, creation_time)"
                            + " VALUES (?, ?, 1, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')")) {
                for (int i = 0; i < SCORED; i++) {
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
        long seed = ids.getFirst();
        try (Connection connection = pool.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement score = connection.prepareStatement(
                    "INSERT INTO relevance_score (occurrence_id, run_id, score, winning_seed_occurrence_id)"
                            + " VALUES (?, ?, ?, ?)")) {
                for (int i = SCORED - 1; i >= 0; i--) {
                    double value = i < BELOW ? UNDER : i == BELOW ? FLOOR : OVER;
                    score.setLong(1, ids.get(i));
                    score.setString(2, scoring.value());
                    score.setDouble(3, value);
                    score.setLong(4, seed);
                    score.addBatch();
                    if (i < BELOW) {
                        belowInTheOrderWritten.add(new OccurrenceId(ids.get(i)));
                    }
                }
                for (int i = 0; i < OTHER_RUNS_BELOW; i++) {
                    score.setLong(1, ids.get(SCORED - 1 - i));
                    score.setString(2, other.value());
                    score.setDouble(3, UNDER);
                    score.setLong(4, seed);
                    score.addBatch();
                }
                score.executeBatch();
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
    @Story("The relevance floor writes its verdicts a page at a time")
    @DisplayName("The documents scored below the floor are counted, then handed over a thousand at a time in the order they were scored, and one exactly on the floor is not among them")
    void countsThenHandsOverAPageAtATime() {
        StatementLog log = new StatementLog(pool.jdbcTemplate().getDataSource());
        RelevanceScoring relevance = new RelevanceScoring(
                new VectorCache(log.jdbcTemplate()), new RelevanceScorer(), new RelevanceScoreCache(log.jdbcTemplate()));
        List<List<OccurrenceId>> pages = new ArrayList<>();

        long count = relevance.countScoredBelow(scoring, FLOOR);
        relevance.eachPageScoredBelow(scoring, FLOOR, page -> pages.add(List.copyOf(page)));

        claim(
                "the count is the " + BELOW + " documents scored strictly below the floor: the one scored exactly"
                        + " on it stays, and the other run's scores are not counted",
                () -> assertThat(count).isEqualTo(BELOW));
        claim(
                "the same " + BELOW + " are handed over, in the order their scores were written, and none other",
                () -> assertThat(pages.stream().flatMap(List::stream).toList())
                        .containsExactlyElementsOf(belowInTheOrderWritten));
        claim(
                "in pages of " + A_PAGE + " and the " + (BELOW - A_PAGE) + " left",
                () -> assertThat(pages).extracting(List::size).containsExactly(A_PAGE, BELOW - A_PAGE));
        claim(
                "each page's statement goes through the run's own scores by the index on the run, from the last"
                        + " row read, and sorts nothing, where the list sorted every score below the floor",
                () -> assertThat(log.said().stream()
                                .filter(sql -> sql.contains("FROM relevance_score") && sql.contains(" LIMIT "))
                                .toList())
                        .isNotEmpty()
                        .allSatisfy(sql -> assertThat(planOf(sql))
                                .anyMatch(detail -> detail.contains("relevance_score_by_run_id (run_id=? AND rowid>?)"))
                                .noneMatch(detail -> detail.contains("TEMP B-TREE"))));
    }

    /** The plan SQLite gives {@code sql}, every placeholder bound to a number, which changes no plan here. */
    private List<String> planOf(String sql) {
        Object[] arguments = Collections.nCopies((int) sql.chars().filter(c -> c == '?').count(), (Object) 0L)
                .toArray();
        return pool.jdbcTemplate()
                .query("EXPLAIN QUERY PLAN " + sql, (resultSet, rowNumber) -> resultSet.getString("detail"), arguments);
    }
}
