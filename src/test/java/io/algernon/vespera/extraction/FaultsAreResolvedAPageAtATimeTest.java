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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * At the end of a stage 2 that completed, its fault rows are resolved into verdicts a page of rows at a time,
 * nothing holding every fault of the run (ADR-214, §14). The rows were each written when their set-aside was
 * heard; the end of the stage reads them back by row number, a thousand at a time, and writes a verdict for each.
 *
 * <p>2,500 fault rows of the run, and some of another run's, which nothing may resolve. The resolution runs in one
 * transaction of its own, as {@code ExtractionFaultRecorder} runs it.
 *
 * <p>It names nothing ADR-214 adds beyond what {@code ExtractionFaultResolutionTest} names, and fails until the
 * resolution reads the run's rows a page at a time.
 */
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("458")
@Link(name = "ADR-214", url = Adr.NO_CLASS_HOLDS_EVERY_OCCURRENCE_OF_A_RUN, type = "adr")
@Link(name = "ADR-139", url = Adr.A_REFUSED_CONVERSION_LEAVES_A_FAULT_ROW, type = "adr")
class FaultsAreResolvedAPageAtATimeTest {

    /** Two full pages of 1,000 and a short third. */
    private static final int FAULTS = 2_500;

    /** The most rows one read of the faults may go through. */
    private static final int A_PAGE = 1_000;

    /** Fault rows of another run of the same step. */
    private static final int ANOTHER_RUNS_FAULTS = 100;

    @TempDir
    Path folder;

    private PoolOfTwo pool;
    private RunId run;

    @BeforeEach
    void faultRowsOfTwoRuns() throws SQLException, IOException {
        pool = new PoolOfTwo(folder);
        JdbcTemplate jdbcTemplate = pool.jdbcTemplate();
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus-faults-resolved"));
        run = ledger.runs().startRun("extraction", "m214", "{}", walk, List.of());
        RunId another = ledger.runs().startRun("extraction", "o214", "{}", walk, List.of());
        try (Connection connection = pool.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement occurrence = connection.prepareStatement(
                    "INSERT INTO file_occurrence (walk_id, path, size_bytes, last_modified, creation_time)"
                            + " VALUES (?, ?, 1, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')")) {
                for (int i = 0; i < FAULTS; i++) {
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
            try (PreparedStatement fault = connection.prepareStatement(
                    "INSERT INTO extraction_fault (occurrence_id, run_id, category, detail) VALUES (?, ?, ?, ?)")) {
                for (int i = 0; i < ids.size(); i++) {
                    fault.setLong(1, ids.get(i));
                    fault.setString(2, run.value());
                    fault.setString(3, "internal");
                    fault.setString(4, "fault " + i);
                    fault.addBatch();
                }
                for (int i = 0; i < ANOTHER_RUNS_FAULTS; i++) {
                    fault.setLong(1, ids.get(i));
                    fault.setString(2, another.value());
                    fault.setString(3, "internal");
                    fault.setString(4, "another run's fault");
                    fault.addBatch();
                }
                fault.executeBatch();
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
    @Story("A file set aside is recorded when it is set aside, and resolved at the end of the stage")
    @DisplayName("A completed stage resolves thousands of recorded faults, reading them back a thousand at a time")
    void resolvesThousandsReadingAPageAtATime() {
        StatementLog log = new StatementLog(pool.jdbcTemplate().getDataSource());
        JdbcTemplate logged = log.jdbcTemplate();
        ExtractionFaultResolution resolution = new ExtractionFaultResolution(new ExtractionFaults(logged), new Ledger(logged));
        List<String> events = new ArrayList<>();

        new TransactionTemplate(new JdbcTransactionManager(logged.getDataSource()))
                .executeWithoutResult(status -> resolution.resolve(run, true, new FaultResolutionProgress() {
                    @Override
                    public void toResolve(long faults) {
                        events.add("to-resolve " + faults);
                    }

                    @Override
                    public void faultResolved() {
                        events.add("resolved");
                    }
                }));

        claim(
                "each of the run's " + FAULTS + " faults is resolved into extraction-failed with its own reason, and"
                        + " no other run's",
                () -> {
                    assertThat(pool.jdbcTemplate().queryForObject(
                                    "SELECT COUNT(*) FROM verdict WHERE run_id = ? AND kind = 'EXTRACTION_FAILED'"
                                            + " AND reason LIKE 'internal: fault %'",
                                    Integer.class,
                                    run.value()))
                            .isEqualTo(FAULTS);
                    assertThat(pool.jdbcTemplate().queryForObject("SELECT COUNT(*) FROM verdict", Integer.class))
                            .isEqualTo(FAULTS);
                });
        claim(
                "the loop is announced once with the run's " + FAULTS + " faults, and each is reported once",
                () -> {
                    assertThat(events.getFirst()).isEqualTo("to-resolve " + FAULTS);
                    assertThat(events.subList(1, events.size())).hasSize(FAULTS).containsOnly("resolved");
                });
        claim(
                "every read of the run's fault rows goes through at most " + A_PAGE + " of them, going on from the"
                        + " last row read; none reads them all",
                () -> assertThat(log.said().stream()
                                .filter(sql -> sql.contains("FROM extraction_fault"))
                                .filter(sql -> !sql.contains("COUNT(") && !sql.contains("MIN(") && !sql.contains("MAX("))
                                .toList())
                        .isNotEmpty()
                        .allSatisfy(sql -> assertThat(sql).contains(" LIMIT " + A_PAGE)));
    }
}
