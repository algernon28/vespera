package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.algernon.vespera.Adr;
import io.algernon.vespera.similarity.TheRunsHashIndex;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sqlite.ProgressHandler;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

/**
 * How many steps of SQLite's virtual machine each statement takes for each row it goes through, under the
 * SQLite the driver bundles (ADR-193 section 3, #411).
 *
 * <p>A counted statement's progress line turns the steps SQLite's progress callback has counted into
 * rows, through a ratio declared beside the statement's SQL. This class is where that ratio is measured.
 * Each test applies the shipped {@code schema.sql} to an empty in-memory database, writes synthetic rows,
 * runs the statement at two sizes with a callback on every step, and holds the difference per row to the
 * ratio exactly: the handful of steps a statement takes whatever its size cancels out. Nothing here opens
 * a working directory.
 *
 * <p><b>It also pins which form each statement takes.</b> A read is counted only where its plan goes
 * through one run's rows by an index on {@code run_id} alone, with no temp B-tree, and where the two
 * statements that bound it cost the same at any size (ADR-193 section 1). A newer SQLite that changes a
 * plan or a step count fails a test here, and that is when a statement changes form or a ratio changes.
 *
 * <p>Green from the start: it measures SQLite, not Vespera. The SQL texts are the ones the code issues,
 * copied; a statement whose text changes in {@code src/main} is measured here again in the same change.
 * {@code StatementStepsPerRowAreTheDeclaredOnesTest} holds each declared ratio to the ones below once the
 * declarations exist.
 */
@Epic("Pipeline")
@Feature("Progress reporting")
@Issue("411")
@Link(name = "ADR-193", url = Adr.STATEMENTS_REPORT_THEIR_PROGRESS, type = "adr")
class StatementStepsPerRowTest {

    /** Steps an index build takes for every row, beyond one for each column it indexes. */
    static final int BUILD_STEPS_BEYOND_COLUMNS = 8;

    /** Stage 4b's read of the occurrences signed under its run. */
    static final int SIGNED_OCCURRENCES_STEPS = 5;

    /** Stage 5b's read of the unusable seeds of its run. */
    static final int UNUSABLE_SEEDS_STEPS = 5;

    /**
     * Stage 5b's read of the seeds' extraction metrics, seven columns. Its read of the corpus survivors' took
     * the same until ADR-211 made it a page of survivors at a time, told the rows it reads and no steps; stage
     * 3's two reads, of the shingle rows and of the extraction metrics, took 7 a row until then, and are not
     * measured here since: no code issues them.
     */
    static final int COMPARISON_METRICS_STEPS = 12;

    /** The interval every handler is set at, in steps (ADR-193 section 2). */
    private static final int STEPS_PER_CALLBACK = 100_000;

    /** The rows of the run read, or of the table built, at the smaller of the two sizes. */
    private static final int FEWER = 1_000;

    /** The same at the larger size. */
    private static final int MORE = 3_000;

    /** The rows the two sizes differ by, which every per-row figure is taken over. */
    private static final int DIFFERENCE = MORE - FEWER;

    /** The run every read is over, 64 characters as a real run id is. */
    private static final String RUN_READ = "b".repeat(64);

    /** An earlier run with rows of its own in the same table, which no read here should go through. */
    private static final String EARLIER_RUN = "a".repeat(64);

    /**
     * How far the steps between the two sizes may stray from the ratio times the rows: a hundredth of a step
     * a row. A b-tree a level deeper at the larger size costs a few steps more in all, which the probe saw as
     * 18,003 steps for 2,000 rows of {@code run}; a ratio one step a row out would be 2,000 out.
     */
    private static final long NEAR_ENOUGH = DIFFERENCE / 100;

    /** One step a callback: every step counted. */
    private static final int EVERY_STEP = 1;

    /**
     * The index stage 4b builds, which {@code schema.sql} does not declare (ADR-182): since ADR-221, over the
     * rows of one stage-2 run, here {@link #RUN_READ}'s.
     */
    private static final String SHINGLE_BY_HASH = TheRunsHashIndex.statementFor(RUN_READ);

    /**
     * Steps stage 4b's build takes for a row of the run it is built for: the eight of any build and one for
     * each of its two columns, and two more to read the row's run and compare it (ADR-221 section 5).
     */
    static final int HASH_INDEX_STEPS_A_ROW_OF_ITS_RUN = 12;

    /** Steps the same build takes for a row of any other run, which it reads, compares and leaves out. */
    static final int HASH_INDEX_STEPS_A_ROW_OF_ANOTHER_RUN = 3;

    /** An index statement of {@code schema.sql}, as {@code StartUpIndexAnnouncement} reads them. */
    private static final Pattern INDEX_STATEMENT = Pattern.compile(
            "^[ \\t]*(CREATE[ \\t]+INDEX[ \\t]+IF[ \\t]+NOT[ \\t]+EXISTS\\s+(\\w+)\\s+ON\\s+(\\w+)\\s*\\(([^)]*)\\)[^;]*);",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private Connection database;
    private long steps;
    private final Map<String, Integer> rowsWritten = new LinkedHashMap<>();

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
    @Story("A long statement inside the database reports how far it has gone")
    @DisplayName("Building an index takes eight steps for every row, and one more for every column it indexes")
    void anIndexBuildTakesEightStepsARowAndOneForEachColumn() throws SQLException, IOException {
        List<IndexStatement> builds = new ArrayList<>(indexStatementsOf(schema()));
        Set<String> tables = new LinkedHashSet<>();
        builds.forEach(build -> tables.add(build.table()));

        for (String table : tables) {
            write(table, RUN_READ, FEWER);
        }
        Map<String, Long> atFewer = new LinkedHashMap<>();
        for (IndexStatement build : builds) {
            atFewer.put(build.index(), buildSteps(build));
        }
        for (String table : tables) {
            write(table, RUN_READ, DIFFERENCE);
        }
        Map<String, Long> atMore = new LinkedHashMap<>();
        for (IndexStatement build : builds) {
            atMore.put(build.index(), buildSteps(build));
        }

        for (IndexStatement build : builds) {
            int expected = BUILD_STEPS_BEYOND_COLUMNS + build.columns();
            claim(
                    "building " + build.index() + " over " + build.columns() + " column(s) takes " + expected
                            + " steps for every row of " + build.table() + ", eight and one a column, to within "
                            + NEAR_ENOUGH + " steps over " + DIFFERENCE + " rows",
                    () -> assertThat(atMore.get(build.index()) - atFewer.get(build.index()))
                            .isCloseTo((long) expected * DIFFERENCE, within(NEAR_ENOUGH)));
        }
        claim(
                "every index the schema declares was measured, the one on a run's shingle rows among them; the"
                        + " one stage 4b builds is over part of a table's rows, takes other steps, and has a"
                        + " check of its own",
                () -> assertThat(builds)
                        .extracting(IndexStatement::index)
                        .contains("shingle_by_run_id")
                        .doesNotContain("shingle_by_hash"));
    }

    /**
     * ADR-221 section 5: the build reads every row of the table and keeps the rows of one run, so its steps
     * are not one figure a row of the table. Twelve for a row of the run is the most a row takes, which is
     * what the build declares, so that steps divided by it are never ahead of the rows read.
     */
    @Test
    @Story("A long statement inside the database reports how far it has gone")
    @DisplayName("Building the index over one run's text fragments takes twelve steps for each of that run's rows and three for each row of another run")
    void theBuildOverOneRunsRowsTakesTwelveStepsARowOfTheRunAndThreeARowOfAnother() throws SQLException {
        IndexStatement build = new IndexStatement(SHINGLE_BY_HASH, "shingle_by_hash", "shingle", 2);
        write("shingle", RUN_READ, FEWER);
        long atFewer = buildSteps(build);
        write("shingle", RUN_READ, DIFFERENCE);
        long atMore = buildSteps(build);
        write("shingle", EARLIER_RUN, DIFFERENCE);
        long withAnotherRunsRows = buildSteps(build);

        claim(
                "building the index over one run's rows takes " + HASH_INDEX_STEPS_A_ROW_OF_ITS_RUN
                        + " steps for every row of that run, to within " + NEAR_ENOUGH + " steps over "
                        + DIFFERENCE + " rows",
                () -> assertThat(atMore - atFewer)
                        .isCloseTo((long) HASH_INDEX_STEPS_A_ROW_OF_ITS_RUN * DIFFERENCE, within(NEAR_ENOUGH)));
        claim(
                "and " + HASH_INDEX_STEPS_A_ROW_OF_ANOTHER_RUN + " for every row of another run in the same"
                        + " table, which it reads and leaves out, to within " + NEAR_ENOUGH + " steps over "
                        + DIFFERENCE + " such rows",
                () -> assertThat(withAnotherRunsRows - atMore)
                        .isCloseTo((long) HASH_INDEX_STEPS_A_ROW_OF_ANOTHER_RUN * DIFFERENCE, within(NEAR_ENOUGH)));
    }

    @Test
    @Story("A long statement inside the database reports how far it has gone")
    @DisplayName("Each read whose rows are counted takes the same number of steps for every row of its run")
    void eachCountedReadTakesItsStepsARow() throws SQLException {
        measureRead("minhash_signature", "SELECT DISTINCT occurrence_id FROM minhash_signature WHERE run_id = ?",
                "minhash_signature_by_run_id", SIGNED_OCCURRENCES_STEPS);
        measureRead("unusable_seed", "SELECT occurrence_id FROM unusable_seed WHERE run_id = ?",
                "unusable_seed_by_run_id", UNUSABLE_SEEDS_STEPS);
        measureRead(
                "extraction_metric",
                "SELECT occurrence_id, primary_language, mean_score, word_count, page_count,"
                        + " vowelless_word_count, single_character_word_count FROM extraction_metric"
                        + " WHERE run_id = ?",
                "extraction_metric_by_run_id",
                COMPARISON_METRICS_STEPS);
    }

    @Test
    @Story("A long statement inside the database reports how far it has gone")
    @DisplayName("The bound on a counted read costs the same however many rows its run holds, and the bound on a timed one does not")
    void theBoundOfACountedReadIsCheapAndTheBoundOfATimedOneIsNot() throws SQLException {
        List<String> cheap = List.of("shingle", "minhash_signature", "extraction_metric", "unusable_seed");
        List<String> dear = List.of("signature_band", "shingle_document_frequency", "call_exemplar");
        for (String table : concat(cheap, dear)) {
            write(table, EARLIER_RUN, MORE);
            write(table, RUN_READ, FEWER);
        }
        Map<String, Long> atFewer = new LinkedHashMap<>();
        for (String table : concat(cheap, dear)) {
            atFewer.put(table, boundSteps(table));
        }
        for (String table : concat(cheap, dear)) {
            write(table, RUN_READ, DIFFERENCE);
        }
        for (String table : cheap) {
            long more = boundSteps(table);
            claim(
                    "the first and last row number of one run in " + table + ", asked as two statements, cost"
                            + " the same " + atFewer.get(table) + " steps at " + FEWER + " rows of the run as at "
                            + MORE + ": one descent of an index on the run alone each",
                    () -> assertThat(more).isEqualTo(atFewer.get(table)));
        }
        for (String table : dear) {
            long more = boundSteps(table);
            claim(
                    "the same two statements on " + table + ", whose only index leading with the run has more"
                            + " columns after it, cost more steps with every row the run holds, so the bound"
                            + " costs as much as the read it would be stated for",
                    () -> assertThat(more - atFewer.get(table)).isGreaterThanOrEqualTo(DIFFERENCE));
        }
    }

    @Test
    @Story("A long statement inside the database reports how far it has gone")
    @DisplayName("Each read that sorts after going through its rows is timed, because its plan sorts in a structure of its own")
    void eachSortingReadSortsInATempBTree() throws SQLException {
        List<String> sorting = List.of(
                "SELECT occurrence_id FROM relevance_score WHERE run_id = ? AND score < ? ORDER BY occurrence_id",
                "SELECT DISTINCT winning_seed_occurrence_id FROM relevance_score WHERE run_id = ?"
                        + " ORDER BY winning_seed_occurrence_id",
                "SELECT occurrence_id, score, winning_seed_occurrence_id FROM relevance_score"
                        + " WHERE run_id = ? ORDER BY occurrence_id",
                "SELECT occurrence_id, winning_seed_occurrence_id, cluster_ordinal FROM document_cluster"
                        + " WHERE run_id = ? ORDER BY occurrence_id",
                "SELECT winning_seed_occurrence_id, cluster_ordinal, label, document_count,"
                        + " partition_order, cluster_order FROM cluster WHERE run_id = ?"
                        + " ORDER BY partition_order, cluster_order",
                "SELECT winning_seed_occurrence_id, cluster_ordinal, title, prose"
                        + " FROM synthesis_doc WHERE run_id = ? ORDER BY rowid",
                "SELECT winning_seed_occurrence_id, cluster_ordinal, kind, detail FROM cluster_fault"
                        + " WHERE run_id = ? ORDER BY rowid",
                "SELECT occurrence_id, reason FROM unusable_seed WHERE run_id = ? ORDER BY occurrence_id",
                "SELECT v.occurrence_id, o.path, v.reason FROM verdict v"
                        + " JOIN file_occurrence o ON o.id = v.occurrence_id"
                        + " WHERE v.run_id = ? AND v.kind = ? ORDER BY o.path");
        for (String sql : sorting) {
            String plan = planOf(sql);
            claim(
                    "the plan of `" + sql + "` sorts or removes repeats in a temp B-tree after it has gone"
                            + " through the rows, so its steps do not count rows at one rate: " + plan,
                    () -> assertThat(plan).contains("USE TEMP B-TREE"));
        }
    }

    @Test
    @Story("A statement SQLite gives no count for says nothing between its two lines")
    @DisplayName("Removing an index takes the same few steps however large it is, fewer than one callback")
    void removingAnIndexTakesFewerStepsThanOneCallback() throws SQLException {
        write("shingle", RUN_READ, FEWER);
        long atFewer = dropSteps();
        write("shingle", RUN_READ, DIFFERENCE * 10);
        long atMore = dropSteps();

        claim(
                "removing shingle_by_hash over " + FEWER + " rows takes " + atFewer + " steps, fewer than the "
                        + STEPS_PER_CALLBACK + " at which a handler is called",
                () -> assertThat(atFewer).isLessThan(STEPS_PER_CALLBACK));
        claim(
                "and over " + (FEWER + DIFFERENCE * 10) + " rows it takes " + atMore + ", the same number of"
                        + " steps: SQLite frees the index in steps that do not grow with it",
                () -> assertThat(atMore).isLessThan(STEPS_PER_CALLBACK).isEqualTo(atFewer));
    }

    private void measureRead(String table, String sql, String index, int expected) throws SQLException {
        rowsWritten.clear();
        anEmptyDatabaseUnderTheShippedSchemaQuietly();
        write(table, EARLIER_RUN, MORE);
        write(table, RUN_READ, FEWER);
        long atFewer = readSteps(sql);
        write(table, RUN_READ, DIFFERENCE);
        long atMore = readSteps(sql);
        String plan = planOf(sql);

        claim(
                "`" + sql + "` goes through its run's rows by " + index + ", an index on the run alone,"
                        + " with no temp B-tree: " + plan,
                () -> assertThat(plan)
                        .isEqualTo("SEARCH " + table + " USING INDEX " + index + " (run_id=?)"));
        claim(
                "and takes " + expected + " steps for every row of the run it reads, to within " + NEAR_ENOUGH
                        + " steps over " + DIFFERENCE + " rows, whatever the earlier run's rows in the same table",
                () -> assertThat(atMore - atFewer).isCloseTo((long) expected * DIFFERENCE, within(NEAR_ENOUGH)));
    }

    private void anEmptyDatabaseUnderTheShippedSchemaQuietly() throws SQLException {
        try {
            database.close();
            anEmptyDatabaseUnderTheShippedSchema();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
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

    private long boundSteps(String table) throws SQLException {
        return readSteps("SELECT MIN(rowid) FROM " + table + " WHERE run_id = ?")
                + readSteps("SELECT MAX(rowid) FROM " + table + " WHERE run_id = ?");
    }

    private long buildSteps(IndexStatement build) throws SQLException {
        try (Statement statement = database.createStatement()) {
            statement.executeUpdate("DROP INDEX IF EXISTS " + build.index());
        }
        return counted(() -> {
            try (Statement statement = database.createStatement()) {
                statement.executeUpdate(build.sql());
            }
        });
    }

    private long dropSteps() throws SQLException {
        try (Statement statement = database.createStatement()) {
            statement.executeUpdate(SHINGLE_BY_HASH);
        }
        return counted(() -> {
            try (Statement statement = database.createStatement()) {
                statement.executeUpdate("DROP INDEX IF EXISTS shingle_by_hash");
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

    private String planOf(String sql) throws SQLException {
        List<String> details = new ArrayList<>();
        try (PreparedStatement explain = database.prepareStatement("EXPLAIN QUERY PLAN " + sql);
                ResultSet rows = explain.executeQuery()) {
            while (rows.next()) {
                details.add(rows.getString("detail"));
            }
        }
        return String.join(" | ", details);
    }

    /**
     * Writes {@code rows} synthetic rows into {@code table} under {@code run}, a value for every column by
     * its declared type and a fresh number for every integer, so no key repeats. Foreign keys are not
     * enforced on this connection, so no row elsewhere is needed.
     */
    private void write(String table, String run, int rows) throws SQLException {
        List<String> columns = new ArrayList<>();
        List<String> types = new ArrayList<>();
        try (Statement statement = database.createStatement();
                ResultSet info = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (info.next()) {
                String name = info.getString("name");
                String type = info.getString("type").toUpperCase();
                if (name.equals("id") && type.equals("INTEGER") && info.getInt("pk") > 0) {
                    // the table's own surrogate key numbers its rows
                    continue;
                }
                columns.add(name);
                types.add(type);
            }
        }
        int first = rowsWritten.merge(table, rows, Integer::sum) - rows;
        String names = String.join(", ", columns);
        String marks = String.join(", ", columns.stream().map(column -> "?").toList());
        database.setAutoCommit(false);
        try (PreparedStatement insert =
                database.prepareStatement("INSERT INTO " + table + " (" + names + ") VALUES (" + marks + ")")) {
            for (int row = first; row < first + rows; row++) {
                for (int i = 0; i < columns.size(); i++) {
                    String column = columns.get(i);
                    String type = types.get(i);
                    if (column.equals("run_id")) {
                        insert.setString(i + 1, run);
                    } else if (column.equals("content_hash")) {
                        // extraction_cache_key takes only a SHA-256 written as 64 lowercase hexadecimal
                        // characters (ADR-206 section 1), so every content hash here has that shape, a
                        // different one for each row.
                        insert.setString(i + 1, String.format("%064x", row));
                    } else if (column.equals("shingle_parameter_identity")) {
                        insert.setString(i + 1, "granularity");
                    } else if (column.equals("winning_seed_occurrence_id")) {
                        insert.setLong(i + 1, row % 19);
                    } else if (type.contains("INT")) {
                        insert.setLong(i + 1, row + 1L);
                    } else if (type.contains("REAL")) {
                        insert.setDouble(i + 1, (row % 97) / 97.0);
                    } else {
                        insert.setString(i + 1, column + "-" + row);
                    }
                }
                insert.addBatch();
            }
            insert.executeBatch();
        }
        database.commit();
        database.setAutoCommit(true);
    }

    private static List<IndexStatement> indexStatementsOf(String sql) {
        List<IndexStatement> found = new ArrayList<>();
        Matcher statements = INDEX_STATEMENT.matcher(sql);
        while (statements.find()) {
            int columns = statements.group(4).split(",").length;
            found.add(new IndexStatement(statements.group(1), statements.group(2), statements.group(3), columns));
        }
        return found;
    }

    private static List<String> concat(List<String> first, List<String> second) {
        List<String> both = new ArrayList<>(first);
        both.addAll(second);
        return both;
    }

    private static String schema() throws IOException {
        return StreamUtils.copyToString(new ClassPathResource("schema.sql").getInputStream(), StandardCharsets.UTF_8);
    }

    private record IndexStatement(String sql, String index, String table, int columns) {}

    @FunctionalInterface
    private interface SqlWork {
        void run() throws SQLException;
    }
}
