package io.algernon.vespera.extraction;

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code ExtractionMetrics.recordedAmong}: which of a page of occurrences a stopped stage 2 already measured
 * under its run, asked by key (ADR-214 section 2). A resumed stage 2 asks it of each page of survivors, where it
 * read every occurrence the run had measured into a set, twice an invocation.
 *
 * <p>Over 2,500 occurrences, every other one measured under the run asked about and every one measured under
 * an earlier run of the same stage, as the table keeps after a run id moves: an occurrence measured only under
 * the earlier run is not one the run asked about recorded.
 *
 * <p>It names a method ADR-214 adds, so the test tree does not compile until that method exists.
 */
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("458")
@Link(name = "ADR-214", url = Adr.NO_CLASS_HOLDS_EVERY_OCCURRENCE_OF_A_RUN, type = "adr")
class RecordedAmongTest {

    /** Two full statements' worth of 1,000 occurrences and a short third. */
    private static final int OCCURRENCES = 2_500;

    /** The most occurrences one statement may name. */
    private static final int A_PAGE = 1_000;

    /** How many statements the 2,500 take at most 1,000 each. */
    private static final int THREE_STATEMENTS = 3;

    @TempDir
    Path folder;

    private PoolOfTwo pool;
    private RunId asked;
    private final List<OccurrenceId> occurrences = new ArrayList<>();
    private final Set<OccurrenceId> measuredUnderTheRun = new HashSet<>();

    @BeforeEach
    void occurrencesMeasuredUnderTwoRuns() throws SQLException, IOException {
        pool = new PoolOfTwo(folder);
        JdbcTemplate jdbcTemplate = pool.jdbcTemplate();
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus-recorded-among"));
        RunId earlier = ledger.runs().startRun("extraction", "e214", "{}", walk, List.of());
        asked = ledger.runs().startRun("extraction", "a214", "{}", walk, List.of());
        try (Connection connection = pool.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement occurrence = connection.prepareStatement(
                    "INSERT INTO file_occurrence (walk_id, path, size_bytes, last_modified, creation_time)"
                            + " VALUES (?, ?, 1, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')")) {
                for (int i = 0; i < OCCURRENCES; i++) {
                    occurrence.setLong(1, walk.value());
                    occurrence.setString(2, "f" + i + ".pdf");
                    occurrence.addBatch();
                }
                occurrence.executeBatch();
            }
            connection.commit();
            connection.setAutoCommit(true);
        }
        for (Long id : jdbcTemplate.queryForList(
                "SELECT id FROM file_occurrence WHERE walk_id = ? ORDER BY id", Long.class, walk.value())) {
            occurrences.add(new OccurrenceId(id));
        }
        try (Connection connection = pool.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement metric = connection.prepareStatement(
                    "INSERT INTO extraction_metric (occurrence_id, run_id, status, processing_time, character_count,"
                            + " alphanumeric_char_count, word_count, word_character_length_total,"
                            + " vowelless_word_count, single_character_word_count)"
                            + " VALUES (?, ?, 'success', 1.0, 1, 1, 1, 1, 0, 0)")) {
                for (int i = 0; i < occurrences.size(); i++) {
                    metric.setLong(1, occurrences.get(i).value());
                    metric.setString(2, earlier.value());
                    metric.addBatch();
                    if (i % 2 == 0) {
                        metric.setLong(1, occurrences.get(i).value());
                        metric.setString(2, asked.value());
                        metric.addBatch();
                        measuredUnderTheRun.add(occurrences.get(i));
                    }
                }
                metric.executeBatch();
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
    @Story("A resumed extraction asks which documents it already read")
    @DisplayName("Asked which of thousands of documents a stopped run already read, it answers those the run read, a thousand at a time, each looked up by number")
    void answersTheRunsOwnMeasuredOccurrencesAPageAtATimeByKey() {
        StatementLog log = new StatementLog(pool.jdbcTemplate().getDataSource());
        ExtractionMetrics metrics = new ExtractionMetrics(log.jdbcTemplate(), new LanguageDetection());

        Set<OccurrenceId> recorded = metrics.recordedAmong(asked, occurrences);

        claim(
                "the answer is the " + measuredUnderTheRun.size() + " documents the run asked about read, and none"
                        + " of those only an earlier run of the same step read",
                () -> assertThat(recorded).containsExactlyInAnyOrderElementsOf(measuredUnderTheRun));
        List<String> asks = log.said().stream().filter(sql -> sql.contains("FROM extraction_metric")).toList();
        claim(
                "the " + OCCURRENCES + " documents were asked about in " + THREE_STATEMENTS + " statements, each"
                        + " naming the documents it asks about, at most " + A_PAGE + " of them",
                () -> {
                    assertThat(asks).hasSize(THREE_STATEMENTS);
                    assertThat(asks).allSatisfy(sql -> {
                        assertThat(sql).contains("occurrence_id IN (");
                        assertThat(occurrencesNamedBy(sql)).isBetween(1, A_PAGE);
                    });
                });
        claim(
                "each statement looks the documents up by the table's own key, the document and the run, and"
                        + " sorts nothing",
                () -> assertThat(asks).allSatisfy(sql -> assertThat(planOf(sql))
                        .anyMatch(detail -> detail.contains("sqlite_autoindex_extraction_metric_1 (occurrence_id=? AND run_id=?)"))
                        .noneMatch(detail -> detail.contains("TEMP B-TREE"))));
    }

    @Test
    @Story("A resumed extraction asks which documents it already read")
    @DisplayName("Asked about no document, it answers nothing and asks the database nothing")
    void asksNothingAboutNothing() {
        StatementLog log = new StatementLog(pool.jdbcTemplate().getDataSource());
        ExtractionMetrics metrics = new ExtractionMetrics(log.jdbcTemplate(), new LanguageDetection());

        Set<OccurrenceId> recorded = metrics.recordedAmong(asked, List.of());

        claim(
                "asking about no document answers nothing, and no statement is issued",
                () -> {
                    assertThat(recorded).isEmpty();
                    assertThat(log.said()).isEmpty();
                });
    }

    /** The plan SQLite gives {@code sql}, every placeholder bound to a number, which changes no plan here. */
    private List<String> planOf(String sql) {
        Object[] arguments = Collections.nCopies((int) sql.chars().filter(c -> c == '?').count(), (Object) 0L)
                .toArray();
        return pool.jdbcTemplate()
                .query("EXPLAIN QUERY PLAN " + sql, (resultSet, rowNumber) -> resultSet.getString("detail"), arguments);
    }

    /** The placeholders inside the statement's list of occurrences, and no other. */
    private static int occurrencesNamedBy(String sql) {
        int from = sql.indexOf("occurrence_id IN (") + "occurrence_id IN (".length();
        String list = sql.substring(from, sql.indexOf(')', from));
        return (int) list.chars().filter(character -> character == '?').count();
    }
}
