package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.algernon.vespera.Adr;
import io.algernon.vespera.extraction.ExtractionStatement;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sqlite.ProgressHandler;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

/**
 * How the statements ADR-193 left unnamed behave under the SQLite the driver bundles, and so which form each
 * takes (ADR-199 section 1, #429).
 *
 * <p>The two reads of stage 2's resume, of a stopped run's faults and of the occurrences its committed chunks
 * measured, each go through one run's rows of one table by an index on {@code run_id} alone, with no temp
 * B-tree, and each one's bound costs the same at any size. The read of the occurrences measured is counted, at
 * the steps a row measured here. The read of the faults is made a page of fault rows at a time since ADR-220,
 * each page's verdicts deleted before the next is read, and is told the rows it has read, so it has no steps a
 * row to measure: its page is held by {@code FaultsAreReadAPageAtATimeTest}, and its bound is still held here,
 * being the total its lines state. The
 * survivor count is an anti-join over two tables, and has no total that is cheap: timed. Every test applies
 * the shipped {@code schema.sql} to an empty in-memory database with synthetic rows and nothing else; nothing
 * here opens a working directory.
 *
 * <p>Green from the start for the same reason {@code StatementStepsPerRowTest} is: it measures SQLite, not
 * Vespera. {@link #theDeclaredStepsAreTheMeasuredOnes} is the one claim about Vespera, and it failed until
 * {@code ExtractionStatement} declared the ratios.
 */
@Epic("Pipeline")
@Feature("Progress reporting")
@Issue("429")
@Link(name = "ADR-199", url = Adr.THE_STATEMENTS_ADR_193_LEFT_UNNAMED_TAKE_ITS_RULE, type = "adr")
class UncoveredStatementsStepsPerRowTest {


    /** Steps SQLite takes for each row of one run of {@code extraction_metric} that the read goes through. */
    static final int RECORDED_OCCURRENCES_STEPS = 5;

    private static final int FEWER = 1_000;
    private static final int MORE = 3_000;
    private static final int DIFFERENCE = MORE - FEWER;
    private static final String RUN_READ = "b".repeat(64);
    private static final String EARLIER_RUN = "a".repeat(64);
    private static final long NEAR_ENOUGH = DIFFERENCE / 100;
    private static final int EVERY_STEP = 1;


    /** The read, as {@code ExtractionMetrics.recordedCount} issues it, as {@code occurrencesForRun} did before ADR-220. */
    private static final String RECORDED_OCCURRENCES = "SELECT occurrence_id FROM extraction_metric WHERE run_id = ?";

    /** The count, as {@code Ledger.survivorCount} issues it over a run with one run upstream. */
    private static final String SURVIVOR_COUNT = "SELECT COUNT(*) FROM file_occurrence"
            + " WHERE walk_id = (SELECT walk_id FROM run WHERE id = ?)"
            + " AND NOT EXISTS (SELECT 1 FROM verdict"
            + " WHERE verdict.occurrence_id = file_occurrence.id"
            + " AND verdict.kind IN ('BROKEN', 'OUT_OF_SCOPE')"
            + " AND verdict.run_id IN (?, ?))";

    private Connection database;
    private long steps;
    private int occurrencesWritten;
    private int faultsWritten;
    private int metricsWritten;

    @BeforeEach
    void anEmptyDatabaseUnderTheShippedSchema() throws SQLException, IOException {
        database = DriverManager.getConnection("jdbc:sqlite::memory:");
        StringBuilder statements = new StringBuilder();
        for (String line : schema().split("\n")) {
            if (!line.strip().startsWith("--")) {
                statements.append(line).append('\n');
            }
        }
        try (Statement statement = database.createStatement()) {
            for (String one : statements.toString().split(";")) {
                if (!one.isBlank()) {
                    statement.executeUpdate(one);
                }
            }
        }
    }

    @AfterEach
    void close() throws SQLException {
        database.close();
    }

    @Test
    @Story("A read of the occurrences a stopped run measured reports how far it has gone")
    @DisplayName("The read of the occurrences a run measured goes through them by an index on the run alone and takes the same steps for every row")
    void theMeasuredReadTakesItsStepsARow() throws SQLException {
        writeMetrics(EARLIER_RUN, MORE);
        writeMetrics(RUN_READ, FEWER);
        long atFewer = readSteps(RECORDED_OCCURRENCES);
        writeMetrics(RUN_READ, DIFFERENCE);
        long atMore = readSteps(RECORDED_OCCURRENCES);
        String plan = planOf(RECORDED_OCCURRENCES, 1);

        claim(
                "`" + RECORDED_OCCURRENCES + "` goes through its run's rows by extraction_metric_by_run_id, an"
                        + " index on the run alone, with no temp B-tree: " + plan,
                () -> assertThat(plan)
                        .isEqualTo("SEARCH extraction_metric USING INDEX extraction_metric_by_run_id (run_id=?)"));
        claim(
                "and takes " + RECORDED_OCCURRENCES_STEPS + " steps for every row of the run it reads, to within "
                        + NEAR_ENOUGH + " steps over " + DIFFERENCE + " rows, whatever the earlier run's rows in the"
                        + " same table",
                () -> assertThat(atMore - atFewer)
                        .isCloseTo((long) RECORDED_OCCURRENCES_STEPS * DIFFERENCE, within(NEAR_ENOUGH)));
    }

    @Test
    @Story("A read of a stopped run's faults reports how far it has gone")
    @Story("A read of the occurrences a stopped run measured reports how far it has gone")
    @DisplayName("The bound on each of stage 2's two resume reads costs the same however many rows the run holds")
    void theBoundsOfTheResumeReadsAreCheap() throws SQLException {
        writeFaults(EARLIER_RUN, MORE);
        writeMetrics(EARLIER_RUN, MORE);
        writeFaults(RUN_READ, FEWER);
        writeMetrics(RUN_READ, FEWER);
        long faultsAtFewer = boundSteps("extraction_fault");
        long metricsAtFewer = boundSteps("extraction_metric");
        writeFaults(RUN_READ, DIFFERENCE);
        writeMetrics(RUN_READ, DIFFERENCE);
        long faultsAtMore = boundSteps("extraction_fault");
        long metricsAtMore = boundSteps("extraction_metric");

        claim(
                "the first and last row number of one run in extraction_fault, asked as two statements, cost the"
                        + " same " + faultsAtFewer + " steps at " + FEWER + " faults of the run as at " + MORE
                        + ": one descent of an index on the run alone each",
                () -> assertThat(faultsAtMore).isEqualTo(faultsAtFewer));
        claim(
                "and the same holds in extraction_metric: " + metricsAtFewer + " steps at " + FEWER
                        + " rows of the run, as at " + MORE,
                () -> assertThat(metricsAtMore).isEqualTo(metricsAtFewer));
    }

    @Test
    @Story("The survivor count that sizes a stage's counter says how long it took")
    @DisplayName("The survivor count is an anti-join over two tables, so no bound of one table is its total")
    void theSurvivorCountHasNoCheapTotal() throws SQLException {
        String plan = planOf(SURVIVOR_COUNT, 3);

        claim(
                "the plan of the survivor count goes through the occurrences of the walk and looks each one up in"
                        + " the verdicts, so the rows it goes through are not one table's in one run: " + plan,
                () -> assertThat(plan).contains("file_occurrence").contains("verdict"));
        long occurrences = FEWER;
        writeOccurrences(occurrences);
        long atFewer = countSteps();
        writeOccurrences(DIFFERENCE);
        long atMore = countSteps();
        claim(
                "its steps grow with the occurrences of the walk, " + (atMore - atFewer) + " more for "
                        + DIFFERENCE + " more occurrences, so it is a cost that grows with a table and is in scope",
                () -> assertThat(atMore - atFewer).isGreaterThanOrEqualTo(DIFFERENCE));
    }

    @Test
    @Story("A read of a stopped run's faults reports how far it has gone")
    @Story("A read of the occurrences a stopped run measured reports how far it has gone")
    @DisplayName("The steps each resume read takes for a row are declared beside its SQL, and are the ones measured here")
    @Link(name = "ADR-220", url = Adr.NO_CLASS_HOLDS_EVERY_OCCURRENCE_OF_A_RUN, type = "adr")
    void theDeclaredStepsAreTheMeasuredOnes() {
        claim(
                "the extraction module declares the measured read's steps a row beside its SQL, and the figure is"
                        + " the " + RECORDED_OCCURRENCES_STEPS + " this class measures",
                () -> assertThat(ExtractionStatement.RECORDED_OCCURRENCES.stepsPerRow())
                        .hasValue(RECORDED_OCCURRENCES_STEPS));
        claim(
                "and the fault read declares none: it is read a page of fault rows at a time and told the rows it"
                        + " has read, not counted by SQLite's steps",
                () -> assertThat(ExtractionStatement.FAULTED_OCCURRENCES.stepsPerRow()).isEmpty());
    }

    private long boundSteps(String table) throws SQLException {
        return readSteps("SELECT MIN(rowid) FROM " + table + " WHERE run_id = ?")
                + readSteps("SELECT MAX(rowid) FROM " + table + " WHERE run_id = ?");
    }

    private long countSteps() throws SQLException {
        return counted(() -> {
            try (PreparedStatement count = database.prepareStatement(SURVIVOR_COUNT)) {
                count.setString(1, RUN_READ);
                count.setString(2, RUN_READ);
                count.setString(3, EARLIER_RUN);
                try (ResultSet rows = count.executeQuery()) {
                    while (rows.next()) {
                        // the one row of the count
                    }
                }
            }
        });
    }

    private long readSteps(String sql) throws SQLException {
        return counted(() -> {
            try (PreparedStatement read = database.prepareStatement(sql)) {
                read.setString(1, RUN_READ);
                try (ResultSet rows = read.executeQuery()) {
                    while (rows.next()) {
                        // every row is read, as the code reads them
                    }
                }
            }
        });
    }

    private long counted(SqlWork work) throws SQLException {
        steps = 0;
        ProgressHandler.setHandler(database, EVERY_STEP, new ProgressHandler() {
            @Override
            protected int progress() {
                steps++;
                return 0;
            }
        });
        try {
            work.run();
        } finally {
            ProgressHandler.clearHandler(database);
        }
        return steps;
    }

    private String planOf(String sql, int parameters) throws SQLException {
        List<String> details = new ArrayList<>();
        try (PreparedStatement explain = database.prepareStatement("EXPLAIN QUERY PLAN " + sql)) {
            for (int i = 1; i <= parameters; i++) {
                explain.setString(i, RUN_READ);
            }
            try (ResultSet rows = explain.executeQuery()) {
                while (rows.next()) {
                    details.add(rows.getString("detail"));
                }
            }
        }
        return String.join(" | ", details);
    }

    /** Fault rows, one a fresh occurrence, under {@code run}; foreign keys are not enforced on this connection. */
    private void writeFaults(String run, int rows) throws SQLException {
        database.setAutoCommit(false);
        try (PreparedStatement insert = database.prepareStatement(
                "INSERT INTO extraction_fault (occurrence_id, run_id, category, detail) VALUES (?, ?, ?, ?)")) {
            for (int row = faultsWritten; row < faultsWritten + rows; row++) {
                insert.setLong(1, row + 1L);
                insert.setString(2, run);
                insert.setString(3, "category");
                insert.setString(4, "detail-" + row);
                insert.addBatch();
            }
            insert.executeBatch();
        }
        faultsWritten += rows;
        database.commit();
        database.setAutoCommit(true);
    }

    /** Metric rows, one a fresh occurrence, under {@code run}, with every column that may not be null set. */
    private void writeMetrics(String run, int rows) throws SQLException {
        database.setAutoCommit(false);
        try (PreparedStatement insert = database.prepareStatement("INSERT INTO extraction_metric"
                + " (occurrence_id, run_id, status, processing_time, character_count, alphanumeric_char_count,"
                + " word_count, word_character_length_total, vowelless_word_count, single_character_word_count)"
                + " VALUES (?, ?, 'success', 1.0, 1, 1, 1, 1, 0, 0)")) {
            for (int row = metricsWritten; row < metricsWritten + rows; row++) {
                insert.setLong(1, row + 1L);
                insert.setString(2, run);
                insert.addBatch();
            }
            insert.executeBatch();
        }
        metricsWritten += rows;
        database.commit();
        database.setAutoCommit(true);
    }

    /** Occurrences of one walk, and the run that reads it, with a blocking verdict on every fifth. */
    private void writeOccurrences(long rows) throws SQLException {
        database.setAutoCommit(false);
        try (Statement statement = database.createStatement()) {
            statement.executeUpdate("INSERT OR IGNORE INTO walk (id, root) VALUES (1, 'root')");
            statement.executeUpdate("INSERT OR IGNORE INTO run (id, stage, implementation_version, config_consumed,"
                    + " walk_id) VALUES ('" + RUN_READ + "', 'byte-level-reduction', 'v', '{}', 1)");
        }
        try (PreparedStatement occurrence = database.prepareStatement(
                        "INSERT INTO file_occurrence (walk_id, path, size_bytes, last_modified, creation_time)"
                                + " VALUES (1, ?, 1, 1, 1)");
                PreparedStatement verdict = database.prepareStatement(
                        "INSERT INTO verdict (occurrence_id, run_id, kind, reason) VALUES (?, ?, 'BROKEN', 'x')")) {
            for (int row = occurrencesWritten; row < occurrencesWritten + rows; row++) {
                occurrence.setString(1, "path-" + row);
                occurrence.addBatch();
            }
            occurrence.executeBatch();
            for (int row = occurrencesWritten; row < occurrencesWritten + rows; row += 5) {
                verdict.setLong(1, row + 1L);
                verdict.setString(2, RUN_READ);
                verdict.addBatch();
            }
            verdict.executeBatch();
        }
        occurrencesWritten += (int) rows;
        database.commit();
        database.setAutoCommit(true);
    }

    private static String schema() throws IOException {
        return StreamUtils.copyToString(new ClassPathResource("schema.sql").getInputStream(), StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    private interface SqlWork {
        void run() throws SQLException;
    }
}
