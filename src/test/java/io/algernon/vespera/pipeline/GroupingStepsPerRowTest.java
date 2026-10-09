package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.sqlite.ProgressHandler;

/**
 * How many steps the bundled SQLite takes for each shingle row in stage 3's one grouping statement, which is
 * what its progress lines are counted from (ADR-211 sections 3 and 9).
 *
 * <p>A counted read takes the same steps for every row, and ADR-193 turns steps into rows by that figure. The
 * grouping does not: it reads every row, sorts them, and then goes through them again in order, and what the
 * second time costs depends on how the rows fall into hashes: a row that opens a new hash costs more than
 * one that joins the hash before it, and a hash two occurrences carry is written, which with foreign keys
 * enforced looks its run up as well. So the statement declares a figure <em>above</em> the most steps a row
 * it takes, {@value #GROUPING_STEPS_A_ROW_AT_MOST}, and its progress is the steps taken divided by that. The
 * share stated is then never ahead of the work done, and the last line falls short of a hundred percent.
 *
 * <p><b>The figure belongs to the connection as the application opens it.</b> The database here is a file
 * opened with the URL {@code application.yaml} ships, foreign keys on, under the shipped {@code schema.sql},
 * with the walk, the runs and the occurrences the shingle rows and the frequency rows refer to. With foreign
 * keys off the same statement takes fewer steps for every row it writes, 40.5 a row at most where it takes
 * 44 with them on; a figure measured that way would put the share ahead of the work. The last test holds
 * that difference, so that nobody measures on a bare connection again.
 *
 * <p>A newer SQLite that takes more steps for any of the shapes fails this, and that is when the declared
 * figure changes; one that takes fewer fails nothing, the share only ending a little earlier. {@link
 * StatementStepsPerRowAreTheDeclaredOnesTest} holds the declaration to the figure here. The statement is
 * written out below as ADR-211 section 3 gives it; {@code DocumentFrequencyIsCountedInTheDatabaseTest} holds
 * what the code issues to the same shape.
 *
 * <p>Green before ADR-211 is built: it measures SQLite, not the application.
 */
@Epic("Pipeline")
@Feature("Progress reporting")
@Issue("456")
@Link(name = "ADR-211", url = Adr.NO_CLASS_HOLDS_EVERY_SURVIVOR_OF_A_RUN, type = "adr")
@Link(name = "ADR-193", url = Adr.STATEMENTS_REPORT_THEIR_PROGRESS, type = "adr")
class GroupingStepsPerRowTest {

    /** One more than the most steps a row the grouping was measured to take, which {@code SimilarityStatement.SHINGLE_ROWS} declares. */
    static final int GROUPING_STEPS_A_ROW_AT_MOST = 45;

    /** The rows each shape is measured over, and twice as many to see that the steps grow with the rows. */
    private static final int ROWS = 40_000;

    /** The shingle rows one occurrence carries, so that a hash carried twice is carried by two occurrences. */
    private static final int ROWS_AN_OCCURRENCE = 50;

    /** The callback counts steps in hundreds, so a count is within a hundred steps of the truth. */
    private static final int STEPS_A_CALLBACK = 100;

    /** Multiplying a small number by this spreads it over the 64 bits, as a shingle's hash is spread. */
    private static final long SPREADING = 0x9E3779B97F4A7C15L;

    /**
     * The granularities the rows are filed under: the first alone in every shape but one, and both in the shape
     * that files each hash under two, the grouping's key being the granularity and the hash together.
     */
    private static final List<String> GRANULARITIES = List.of("word:5", "word:3");

    /** How many of them the last shape uses. */
    private static final int TWO_GRANULARITIES = GRANULARITIES.size();

    /** Where the shipped URL points its database. */
    private static final String WORKING_DIRECTORY_PLACEHOLDER = "${vespera.working-dir}";

    private static final String STAGE_2 = "b".repeat(64);
    private static final String STAGE_3 = "c".repeat(64);

    /** ADR-211 section 3's grouping, as written there. */
    private static final String GROUPING = "INSERT INTO shingle_document_frequency (run_id, shingle_parameter_identity,"
            + " shingle_hash, document_count, total_count) SELECT ?, shingle_parameter_identity, shingle_hash,"
            + " COUNT(DISTINCT occurrence_id), COUNT(*) FROM shingle WHERE run_id = ?"
            + " GROUP BY shingle_parameter_identity, shingle_hash HAVING COUNT(DISTINCT occurrence_id) >= 2";

    @TempDir
    Path workingDirectory;

    private Connection connection;

    @BeforeEach
    void aDatabaseOpenedAsTheApplicationOpensIt() throws SQLException {
        connection = DriverManager.getConnection(shippedUrl());
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO walk (root, finished) VALUES ('C:/corpus-grouping-steps', 1)");
            for (String[] run : new String[][] {{STAGE_2, "extraction"}, {STAGE_3, "content-census"}}) {
                statement.executeUpdate("INSERT INTO run (id, stage, implementation_version, config_consumed, walk_id)"
                        + " VALUES ('" + run[0] + "', '" + run[1] + "', 'v', '{}', 1)");
            }
        }
        try (PreparedStatement occurrence = connection.prepareStatement(
                "INSERT INTO file_occurrence (walk_id, path, size_bytes, last_modified, creation_time)"
                        + " VALUES (1, ?, 1, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')")) {
            for (int i = 0; i <= 2 * ROWS / ROWS_AN_OCCURRENCE; i++) {
                occurrence.setString(1, "g" + i + ".pdf");
                occurrence.addBatch();
            }
            occurrence.executeBatch();
        }
        connection.commit();
    }

    @AfterEach
    void close() throws SQLException {
        connection.close();
    }

    @Test
    @Story("A long statement inside the database reports how far it has gone")
    @DisplayName("Grouping the saved word sequences never takes more steps for a row than the figure its progress is counted by, whatever shape the rows have")
    void theGroupingNeverTakesMoreStepsARowThanItDeclares() throws SQLException {
        claim(
                "the connection enforces foreign keys, as every connection of the application does: the figure"
                        + " below is measured the way the statement will run",
                () -> assertThat(foreignKeysOn(connection)).isTrue());
        Map<String, Integer> differentHashesOf = new LinkedHashMap<>();
        differentHashesOf.put("every row a different word sequence, so none is written", ROWS);
        differentHashesOf.put("every word sequence carried by two documents, so all are written", ROWS / 2);
        differentHashesOf.put("two word sequences in three carried by two documents and the rest by one", 3 * ROWS / 5);
        differentHashesOf.put("every word sequence carried by four documents", ROWS / 4);
        differentHashesOf.put("a thousand word sequences among all the rows", 1_000);
        differentHashesOf.put("one word sequence in every row", 1);
        Map<String, Double> stepsARow = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> shape : differentHashesOf.entrySet()) {
            stepsARow.put(shape.getKey(), stepsARowOver(connection, ROWS, shape.getValue()));
        }
        stepsARow.put(
                "every word sequence saved at two lengths, and at each length carried by two documents, so all"
                        + " are written, once for each length",
                stepsARowOver(connection, ROWS, ROWS / (2 * TWO_GRANULARITIES), TWO_GRANULARITIES));

        stepsARow.forEach((shape, steps) -> claim(
                "with " + shape + ", the grouping takes " + steps + " steps a row, fewer than the "
                        + GROUPING_STEPS_A_ROW_AT_MOST + " its progress is counted by, so the share its lines"
                        + " state is never ahead of the work done and never reaches the whole",
                () -> assertThat(steps).isLessThan(GROUPING_STEPS_A_ROW_AT_MOST)));
    }

    @Test
    @Story("A long statement inside the database reports how far it has gone")
    @DisplayName("Twice the rows of one shape take twice the steps, through the index on the extraction and two sorts")
    void theStepsGrowWithTheRowsAndThePlanSortsTwice() throws SQLException {
        double atFewer = stepsARowOver(connection, ROWS, ROWS / 2);
        double atTwice = stepsARowOver(connection, 2 * ROWS, ROWS);

        claim(
                "over " + ROWS + " rows and over " + 2 * ROWS + " of the same shape the grouping takes the same"
                        + " steps a row, so steps divided by a fixed figure follow the rows however many there are",
                () -> assertThat(atTwice).isCloseTo(atFewer, within(0.05)));
        List<String> plan = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("EXPLAIN QUERY PLAN "
                        + GROUPING.replaceFirst("SELECT \\?", "SELECT 'c'").replace("run_id = ?", "run_id = 'b'"))) {
            while (rows.next()) {
                plan.add(rows.getString("detail"));
            }
        }
        claim(
                "the statement reads the extraction's rows through the index on the extraction alone, and sorts"
                        + " them in structures of its own, once to group them and once to count each word"
                        + " sequence's different documents: that sorting is what is written to temporary files",
                () -> assertThat(plan)
                        .anyMatch(detail -> detail.contains("shingle_by_run_id (run_id=?)"))
                        .anyMatch(detail -> detail.contains("USE TEMP B-TREE FOR GROUP BY"))
                        .anyMatch(detail -> detail.contains("USE TEMP B-TREE FOR count(DISTINCT)")));
    }

    @Test
    @Story("A long statement inside the database reports how far it has gone")
    @DisplayName("The steps a row depend on whether the connection enforces foreign keys, so they are measured on one that does")
    void withForeignKeysOffTheSameRowsTakeFewerSteps() throws SQLException {
        double enforced = stepsARowOver(connection, ROWS, ROWS / 2);
        double notEnforced;
        try (Connection bare = DriverManager.getConnection(shippedUrl().replace("foreign_keys=on", "foreign_keys=off"))) {
            bare.setAutoCommit(false);
            claim(
                    "a second connection to the same database, opened without foreign keys, does not enforce them",
                    () -> assertThat(foreignKeysOn(bare)).isFalse());
            notEnforced = stepsARowOver(bare, ROWS, ROWS / 2);
        }

        claim(
                "where every word sequence is written, each row written looks its extraction up when foreign keys"
                        + " are enforced, so the same rows take more steps a row with them (" + enforced + ") than"
                        + " without (" + notEnforced + "): a figure measured on a connection that does not"
                        + " enforce them would be too low for the application's",
                () -> assertThat(enforced).isGreaterThan(notEnforced));
    }

    /** The shipped datasource URL, pointed at this test's own directory. */
    private String shippedUrl() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yaml"));
        yaml.afterPropertiesSet();
        return String.valueOf(yaml.getObject().get("spring.datasource.url"))
                .replace(WORKING_DIRECTORY_PLACEHOLDER, workingDirectory.toString().replace('\\', '/'));
    }

    private static boolean foreignKeysOn(Connection asked) throws SQLException {
        try (Statement statement = asked.createStatement();
                ResultSet answer = statement.executeQuery("PRAGMA foreign_keys")) {
            return answer.next() && answer.getInt(1) == 1;
        }
    }

    /** {@link #stepsARowOver(Connection, int, int, int)} with every row under the one granularity, {@code word:5}. */
    private static double stepsARowOver(Connection on, int rows, int differentHashes) throws SQLException {
        return stepsARowOver(on, rows, differentHashes, 1);
    }

    /**
     * The steps a row the grouping takes, on {@code on}, over {@code rows} rows, fifty rows to an occurrence.
     * The rows take {@code granularities} granularities in turn, {@code word:5} and then {@code word:3}, and
     * each run of that many rows takes the next of {@code differentHashes} hashes in turn: so with one
     * granularity each row takes the next hash, and with two each hash is filed under both, in two rows side by
     * side, and comes round again {@code differentHashes} pairs later, in another occurrence. The rows and what
     * the grouping wrote are rolled back.
     */
    private static double stepsARowOver(Connection on, int rows, int differentHashes, int granularities)
            throws SQLException {
        try (PreparedStatement row = on.prepareStatement(
                "INSERT INTO shingle (occurrence_id, run_id, shingle_parameter_identity, shingle_hash)"
                        + " VALUES (?, ?, ?, ?)")) {
            for (int i = 0; i < rows; i++) {
                row.setLong(1, 1 + i / ROWS_AN_OCCURRENCE);
                row.setString(2, STAGE_2);
                row.setString(3, GRANULARITIES.get(i % granularities));
                row.setLong(4, ((i / granularities) % differentHashes) * SPREADING);
                row.addBatch();
            }
            row.executeBatch();
        }
        long[] callbacks = {0};
        ProgressHandler.setHandler(on, STEPS_A_CALLBACK, new ProgressHandler() {
            @Override
            protected int progress() {
                callbacks[0]++;
                return 0;
            }
        });
        try (PreparedStatement grouping = on.prepareStatement(GROUPING)) {
            grouping.setString(1, STAGE_3);
            grouping.setString(2, STAGE_2);
            grouping.executeUpdate();
        } finally {
            ProgressHandler.clearHandler(on);
        }
        on.rollback();
        return (double) callbacks[0] * STEPS_A_CALLBACK / rows;
    }
}
