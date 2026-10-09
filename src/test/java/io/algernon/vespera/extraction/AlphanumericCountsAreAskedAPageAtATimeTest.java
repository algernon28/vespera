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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code ExtractionMetrics.alphanumericCharCounts}, which stage 4b's survivor rule reads for the members of a
 * near-duplicate component, asks at most 1,000 documents in a statement (ADR-220 section 4). Until ADR-220 it
 * named every member of every component in one statement, as many as the run's near-duplicates.
 *
 * <p>It names nothing ADR-220 adds, and so compiles before it; it fails until the method asks in pages.
 */
@Epic("Extraction")
@Feature("Derived metrics")
@Issue("458")
@Link(name = "ADR-220", url = Adr.NO_CLASS_HOLDS_EVERY_OCCURRENCE_OF_A_RUN, type = "adr")
class AlphanumericCountsAreAskedAPageAtATimeTest {

    /** Two full statements' worth of 1,000 documents and a short third. */
    private static final int OCCURRENCES = 2_500;

    /** The most documents one statement may name. */
    private static final int A_PAGE = 1_000;

    /** How many statements the 2,500 take at most 1,000 each. */
    private static final int THREE_STATEMENTS = 3;

    @TempDir
    Path folder;

    private PoolOfTwo pool;
    private RunId run;
    private final List<OccurrenceId> occurrences = new ArrayList<>();
    private final Map<OccurrenceId, Long> written = new HashMap<>();

    @BeforeEach
    void occurrencesWithACountEach() throws SQLException, IOException {
        pool = new PoolOfTwo(folder);
        JdbcTemplate jdbcTemplate = pool.jdbcTemplate();
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus-alphanumeric-pages"));
        run = ledger.runs().startRun("extraction", "n214", "{}", walk, List.of());
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
                            + " VALUES (?, ?, 'success', 1.0, ?, ?, 1, 1, 0, 0)")) {
                for (int i = 0; i < occurrences.size(); i++) {
                    long count = 100L + i;
                    metric.setLong(1, occurrences.get(i).value());
                    metric.setString(2, run.value());
                    metric.setLong(3, count);
                    metric.setLong(4, count);
                    metric.addBatch();
                    written.put(occurrences.get(i), count);
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
    @Story("The text a near-duplicate holds is read a page at a time")
    @DisplayName("The amount of text of thousands of documents is read a thousand documents at a time")
    void asksAThousandAtATime() {
        StatementLog log = new StatementLog(pool.jdbcTemplate().getDataSource());

        Map<OccurrenceId, Long> counts = new ExtractionMetrics(log.jdbcTemplate(), new LanguageDetection())
                .alphanumericCharCounts(run, occurrences);

        claim(
                "every one of the " + OCCURRENCES + " documents is answered with the count recorded for it",
                () -> assertThat(counts).containsExactlyInAnyOrderEntriesOf(written));
        claim(
                "in " + THREE_STATEMENTS + " statements, each naming at most " + A_PAGE + " documents",
                () -> assertThat(log.said().stream().filter(sql -> sql.contains("alphanumeric_char_count")).toList())
                        .hasSize(THREE_STATEMENTS)
                        .allSatisfy(sql -> assertThat(occurrencesNamedBy(sql)).isBetween(1, A_PAGE)));
    }

    /** The placeholders inside the statement's list of occurrences, and no other. */
    private static int occurrencesNamedBy(String sql) {
        int from = sql.indexOf("occurrence_id IN (") + "occurrence_id IN (".length();
        String list = sql.substring(from, sql.indexOf(')', from));
        return (int) list.chars().filter(character -> character == '?').count();
    }
}
