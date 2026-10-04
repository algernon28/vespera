package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.extraction.ConverterStopsPartwayBeans;
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
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * What start-up says when {@code schema.sql} builds an index over rows that are already there (ADR-187
 * section 3, #402).
 *
 * <p>{@code schema.sql} runs at every start, and {@code CREATE INDEX IF NOT EXISTS} builds an index an
 * older database lacks (ADR-173 section 3). On 2026-10-04 that took 12 minutes 24 seconds on a 13.4 GB
 * database and wrote no line, before any stage had logged anything, and the operator took the invocation
 * for one that had stopped moving. ADR-187 keeps every index in {@code schema.sql} and has start-up say,
 * before each index it is about to build on a table that holds rows, which index, on which table and
 * over how many rows at most, and say when it has built it and how long that took. An index on a table
 * with no rows costs nothing and gets no line, so a new working directory starts as quietly as before.
 *
 * <p><b>The database is on disk, and is older than the context.</b> The {@code test} profile's datasource
 * is in memory and empty at every start, so nothing a start-up does there can meet existing rows. This
 * class points the same slice at a file in a directory of its own, and makes that file before the context
 * starts: the shipped schema, rows in two tables, and four indexes removed, three on the tables that hold
 * rows and one on a table that holds none. The rows are written on a connection that does not enforce
 * foreign keys, so they need no walk, occurrence or run behind them; start-up reads none of those.
 *
 * <p><b>Three indexes on two tables, on purpose.</b> ADR-187 has start-up read what to announce out of
 * {@code schema.sql}, so an index added there later is announced with no further change. One index would
 * be satisfied by a line written for that index alone.
 *
 * <p>The start under test is the one that builds this class's context, so what it wrote is in the output
 * captured from before the first test, and every test reads the same start.
 *
 * <p>{@code @DirtiesContext} after the class, because the cached context keeps the database file open and
 * Windows refuses to delete a directory holding an open file.
 *
 * <p>The first test is the one that failed before this was built: start-up built the indexes and said
 * nothing. The other two passed then and have to go on passing: they are what stops an implementation
 * from writing a line for every one of the schema's indexes at every start.
 */
@CascadeSliceTest
@Import(ConverterStopsPartwayBeans.class)
@ExtendWith(OutputCaptureExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Epic("Pipeline")
@Feature("Progress reporting")
@Issue("402")
@Link(name = "ADR-187", url = Adr.A_DATABASE_STATEMENT_THAT_CAN_TAKE_MINUTES_IS_ANNOUNCED, type = "adr")
class StartUpSaysWhichIndexItBuildsTest {

    /** The database file's name under the working directory, as the shipped datasource names it. */
    private static final String DATABASE_FILE = "vespera.db";

    /** How many verdict rows the database holds before the start. */
    private static final int VERDICTS_ALREADY_THERE = 2;

    /** How many shingle rows it holds: a different number, so each line's row count is its own table's. */
    private static final int SHINGLES_ALREADY_THERE = 3;

    /** The two tables that hold rows, in the order {@code schema.sql} creates them. */
    private static final String VERDICT = "verdict";

    private static final String SHINGLE = "shingle";

    /** The indexes {@code schema.sql} puts on them that this database lacks, in the order it names them. */
    private static final String VERDICT_BY_RUN = "verdict_by_run_id";

    private static final String VERDICT_BY_OCCURRENCE = "verdict_by_occurrence";

    private static final String SHINGLE_BY_RUN = "shingle_by_run_id";

    /** A table that holds no rows in this database. */
    private static final String TABLE_WITHOUT_ROWS = "walk_anomaly";

    /** The index {@code schema.sql} puts on that table, also removed before the start. */
    private static final String MISSING_OVER_NOTHING = "walk_anomaly_by_walk_id";

    /** An index on a table with rows that was never removed, so the start has nothing to build for it. */
    private static final String ALREADY_THERE = "shingle_by_occurrence";

    /** How the line start-up writes before it builds an index begins; the index's name follows. */
    private static final String BUILDING = "Start-up is building index ";

    /** How the line it writes once an index is built begins; the index's name and the seconds it took follow. */
    private static final String BUILT = "Start-up built index ";

    /** What each line before a build says about stopping part-way. */
    private static final String STOPPING_UNDOES_IT = "stopping before it ends undoes it";

    /** A line after a build, with the time it took: seconds to one decimal place. */
    private static final String BUILT_IN_SECONDS_TO_ONE_DECIMAL =
            "Start-up built index verdict_by_run_id in \\d+\\.\\d s";

    @TempDir
    static Path workingDirectory;

    /** The indexes the three tables carried when the database was handed to the start. */
    private static List<String> indexesBeforeTheStart;

    @DynamicPropertySource
    static void aDatabaseFileOfItsOwn(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + databaseFile() + "?foreign_keys=on");
    }

    /** A database an earlier build left: every table, rows in two of them, and four of today's indexes absent. */
    @BeforeAll
    static void aDatabaseAnEarlierBuildLeft() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile())) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO verdict (occurrence_id, run_id, kind) VALUES (?, 'an-earlier-run', 'a-kind')")) {
                for (int occurrence = 1; occurrence <= VERDICTS_ALREADY_THERE; occurrence++) {
                    insert.setLong(1, occurrence);
                    insert.executeUpdate();
                }
            }
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO shingle"
                    + " (occurrence_id, run_id, shingle_parameter_identity, shingle_hash) VALUES (1, 'an-earlier-run',"
                    + " 'a-granularity', ?)")) {
                for (int hash = 1; hash <= SHINGLES_ALREADY_THERE; hash++) {
                    insert.setLong(1, hash);
                    insert.executeUpdate();
                }
            }
            try (Statement statement = connection.createStatement()) {
                for (String index : List.of(VERDICT_BY_RUN, VERDICT_BY_OCCURRENCE, SHINGLE_BY_RUN, MISSING_OVER_NOTHING)) {
                    statement.executeUpdate("DROP INDEX " + index);
                }
            }
            indexesBeforeTheStart = new ArrayList<>();
            for (String table : List.of(VERDICT, SHINGLE, TABLE_WITHOUT_ROWS)) {
                indexesBeforeTheStart.addAll(indexesOn(connection, table));
            }
        }
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A long wait inside the database is announced")
    @DisplayName("Start-up says which index it is building over rows already saved, over how many at most, and when it has built it, one index at a time")
    void startUpSaysWhichIndexItBuildsOverExistingRowsAndWhenItHasBuiltIt(CapturedOutput output) {
        String sinceTheStart = output.getAll();

        claim(
                "the database handed to the start held " + VERDICTS_ALREADY_THERE + " verdicts and "
                        + SHINGLES_ALREADY_THERE + " saved word sequences, and lacked three of the indexes the"
                        + " schema puts on those two tables, as a database made by an earlier build does",
                () -> assertThat(indexesBeforeTheStart)
                        .doesNotContain(VERDICT_BY_RUN, VERDICT_BY_OCCURRENCE, SHINGLE_BY_RUN)
                        .contains(ALREADY_THERE));
        claim(
                "the start built all three, and left the rows as they were",
                () -> {
                    assertThat(columnsOf(VERDICT_BY_RUN)).isNotEmpty();
                    assertThat(columnsOf(VERDICT_BY_OCCURRENCE)).isNotEmpty();
                    assertThat(columnsOf(SHINGLE_BY_RUN)).isNotEmpty();
                    assertThat(rowsIn(VERDICT)).isEqualTo(VERDICTS_ALREADY_THERE);
                    assertThat(rowsIn(SHINGLE)).isEqualTo(SHINGLES_ALREADY_THERE);
                });
        claim(
                "before building each one the start said which index it was building, on which table, and how"
                        + " many rows it covers at most, " + VERDICTS_ALREADY_THERE + " for the verdicts and "
                        + SHINGLES_ALREADY_THERE + " for the word sequences, so a build lasting minutes is not"
                        + " mistaken for an invocation that has stopped moving",
                () -> assertThat(sinceTheStart)
                        .contains(building(VERDICT_BY_RUN, VERDICT, VERDICTS_ALREADY_THERE))
                        .contains(building(VERDICT_BY_OCCURRENCE, VERDICT, VERDICTS_ALREADY_THERE))
                        .contains(building(SHINGLE_BY_RUN, SHINGLE, SHINGLES_ALREADY_THERE)));
        claim(
                "after each one it said that it had built it and how long that took, before it went on to the"
                        + " next, in the order the schema names them, so the time stated is that index's own",
                () -> assertThat(sinceTheStart)
                        .containsSubsequence(
                                building(VERDICT_BY_RUN, VERDICT, VERDICTS_ALREADY_THERE),
                                built(VERDICT_BY_RUN),
                                building(VERDICT_BY_OCCURRENCE, VERDICT, VERDICTS_ALREADY_THERE),
                                built(VERDICT_BY_OCCURRENCE),
                                building(SHINGLE_BY_RUN, SHINGLE, SHINGLES_ALREADY_THERE),
                                built(SHINGLE_BY_RUN)));
        claim(
                "and it said each of the six lines once",
                () -> assertThat(sinceTheStart)
                        .containsOnlyOnce(BUILDING + VERDICT_BY_RUN)
                        .containsOnlyOnce(built(VERDICT_BY_RUN))
                        .containsOnlyOnce(BUILDING + VERDICT_BY_OCCURRENCE)
                        .containsOnlyOnce(built(VERDICT_BY_OCCURRENCE))
                        .containsOnlyOnce(BUILDING + SHINGLE_BY_RUN)
                        .containsOnlyOnce(built(SHINGLE_BY_RUN)));
        claim(
                "a line before a build warns that stopping before it ends undoes it, and the line after gives"
                        + " the time it took in seconds, to one decimal place",
                () -> assertThat(sinceTheStart)
                        .contains(STOPPING_UNDOES_IT)
                        .containsPattern(BUILT_IN_SECONDS_TO_ONE_DECIMAL));
    }

    @Test
    @Story("A long wait inside the database is announced")
    @DisplayName("Start-up builds an index on a table with nothing in it without a word, because that takes no time")
    void startUpSaysNothingAboutAnIndexOnATableThatHoldsNoRows(CapturedOutput output) {
        String sinceTheStart = output.getAll();

        claim(
                "the database handed to the start lacked an index on a table that held no rows",
                () -> {
                    assertThat(indexesBeforeTheStart).doesNotContain(MISSING_OVER_NOTHING);
                    assertThat(rowsIn(TABLE_WITHOUT_ROWS)).isZero();
                });
        claim(
                "the start built that index too",
                () -> assertThat(columnsOf(MISSING_OVER_NOTHING)).isNotEmpty());
        claim(
                "and wrote no line naming it: an index over nothing is built at once, and a new working"
                        + " directory, where every table is empty, would otherwise open with a line for each index",
                () -> assertThat(sinceTheStart).doesNotContain(MISSING_OVER_NOTHING));
    }

    @Test
    @Story("A long wait inside the database is announced")
    @DisplayName("Start-up says nothing about an index the database already has")
    void startUpSaysNothingAboutAnIndexThatIsAlreadyThere(CapturedOutput output) {
        String sinceTheStart = output.getAll();

        claim(
                "the database handed to the start already had the other index on the word-sequence table",
                () -> assertThat(indexesBeforeTheStart).contains(ALREADY_THERE));
        claim(
                "and the start wrote no line about building it, because it built nothing: only an index that"
                        + " is missing is announced, so every later start is as quiet as it was",
                () -> assertThat(sinceTheStart)
                        .doesNotContain(BUILDING + ALREADY_THERE)
                        .doesNotContain(BUILT + ALREADY_THERE));
    }

    /** The line before a build, up to and including the number of rows. */
    private static String building(String index, String table, int rows) {
        return BUILDING + index + " on " + table + ", over up to " + rows + " rows";
    }

    /** The line after a build, up to the seconds it took. */
    private static String built(String index) {
        return BUILT + index + " in ";
    }

    private static String databaseFile() {
        return workingDirectory.resolve(DATABASE_FILE).toString();
    }

    private static List<String> indexesOn(Connection connection, String table) throws SQLException {
        List<String> names = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = ?")) {
            select.setString(1, table);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    names.add(rows.getString(1));
                }
            }
        }
        return names;
    }

    private List<String> columnsOf(String index) {
        return jdbcTemplate.queryForList("SELECT name FROM pragma_index_info(?) ORDER BY seqno", String.class, index);
    }

    private long rowsIn(String table) {
        Long rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return rows == null ? 0 : rows;
    }
}
