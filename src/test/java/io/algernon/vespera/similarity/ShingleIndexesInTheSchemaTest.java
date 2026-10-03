package io.algernon.vespera.similarity;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * Which indexes the shipped schema puts on {@code shingle}, and which of them SQLite answers each
 * reader of {@code shingle} through (ADR-182, #381).
 *
 * <p>{@code shingle_by_hash} is not created by {@code schema.sql}: stage 2 writes shingles without it,
 * because maintaining it row by row was most of what a stage-2 chunk cost on a large database, and stage
 * 4b builds it before containment retrieval, its one reader, needs it. {@code shingle_by_run_id} takes
 * its place as the index {@code run_id} leads, which ADR-173's rule needs, and it is also the index
 * stage 3 reads a run's rows through.
 *
 * <p>Read on a database of this test's own, made by running the shipped {@code schema.sql} into a fresh
 * in-memory SQLite, the way a start runs it into an empty working directory. A shared test database
 * would carry whatever indexes another test's invocations had built or dropped. Nothing here runs
 * {@code ANALYZE}, so the planner chooses from the schema alone, as it does on a working directory.
 *
 * <p>Fails today on the first three tests: the shipped schema creates {@code shingle_by_hash} and no
 * {@code shingle_by_run_id}, so stage 3's read goes through the by-hash index. The last test passes today
 * and has to go on passing: {@code containmentRetrievalNeedsTheIndexOnTheHash} pins the premise the
 * whole decision rests on, that containment retrieval reads every row of the run unless the by-hash
 * index has been built before it.
 */
@Epic("Redundancy")
@Feature("Shingling")
@Issue("381")
@Link(name = "ADR-182", url = Adr.STAGE_4B_BUILDS_THE_BY_HASH_INDEX_STAGE_2_WRITES_WITHOUT, type = "adr")
class ShingleIndexesInTheSchemaTest {

    /** The index containment retrieval reads, under the name and columns ADR-081 gave it. */
    private static final String BY_HASH = "shingle_by_hash";

    /** The one-column index ADR-173's rule asks for on {@code shingle.run_id}. */
    private static final String BY_RUN_ID = "shingle_by_run_id";

    /** The statement stage 4b builds the by-hash index with (ADR-182 §2.3). */
    private static final String BUILD_BY_HASH = "CREATE INDEX IF NOT EXISTS shingle_by_hash"
            + " ON shingle (run_id, shingle_parameter_identity, shingle_hash)";

    /** Stage 3's read of every shingle row under one stage-2 run, as {@code DocumentFrequency} sends it. */
    private static final String STAGE_3_READ =
            "SELECT occurrence_id, shingle_parameter_identity, shingle_hash FROM shingle WHERE run_id = ?";

    /** How many rare shingles containment retrieval asks about for one occurrence (ADR-081). */
    private static final int RARE_SHINGLES = 32;

    /** How many of those another occurrence must hold to be a containment candidate (ADR-081). */
    private static final int HITS_FOR_A_CANDIDATE = 24;

    private Connection connection;

    @BeforeEach
    void aDatabaseFromTheShippedSchema() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
    }

    @AfterEach
    void close() throws SQLException {
        connection.close();
    }

    @Test
    @Story("When the index on word-sequence hashes exists")
    @DisplayName("A new database has no index ordered by word-sequence hash, so the extraction writes without one")
    void theShippedSchemaCreatesNoIndexOnTheShingleHash() throws SQLException {
        List<String> indexes = indexesOnShingle();

        claim(
                "the schema does put indexes on the word-sequence table, so the claim below is about a table"
                        + " that has some",
                () -> assertThat(indexes).isNotEmpty());
        claim(
                "and none of them has the hash among its columns: an index whose key holds the hash takes"
                        + " every new row at a random place in it, which is the cost the extraction no longer"
                        + " pays while it writes",
                () -> assertThat(indexes).allSatisfy(index -> assertThat(columnsOf(index)).doesNotContain("shingle_hash")));
        claim(
                "and the index the later redundancy check builds is not among them",
                () -> assertThat(indexes).doesNotContain(BY_HASH));
    }

    @Test
    @Story("When the index on word-sequence hashes exists")
    @DisplayName("A new database indexes word sequences by the run that recorded them, and by nothing else in that index")
    void theShippedSchemaIndexesShinglesOnTheRunAlone() throws SQLException {
        claim(
                "the word-sequence table has an index on the run column alone, the one-column shape every"
                        + " reference to a run is given",
                () -> assertThat(columnsOf(BY_RUN_ID)).containsExactly("run_id"));
    }

    @Test
    @Story("When the index on word-sequence hashes exists")
    @DisplayName("Measuring how often each word sequence recurs reads a run's rows in the order they were written")
    void stageThreeReadsARunThroughTheIndexOnTheRun() throws SQLException {
        String withoutTheHashIndex = planOf(STAGE_3_READ, "a-run");
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(BUILD_BY_HASH);
        }
        String withTheHashIndexBuilt = planOf(STAGE_3_READ, "a-run");

        claim(
                "reading every word sequence of one run is answered through the index on the run, which keeps"
                        + " the rows in the order they were written, and not through an index ordered by hash,"
                        + " which fetches each row from a different place: \"" + withoutTheHashIndex + "\"",
                () -> assertThat(withoutTheHashIndex).contains(BY_RUN_ID).doesNotContain(BY_HASH));
        claim(
                "and that stays so after the redundancy check has built its index on the hash: \""
                        + withTheHashIndexBuilt + "\"",
                () -> assertThat(withTheHashIndexBuilt).contains(BY_RUN_ID).doesNotContain(BY_HASH));
    }

    @Test
    @Story("When the index on word-sequence hashes exists")
    @DisplayName("Looking for documents that contain another is answered through the hash index once it is built, and cannot be without it")
    void containmentRetrievalNeedsTheIndexOnTheHash() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP INDEX IF EXISTS " + BY_HASH);
        }
        String withoutIt = planOf(containmentQuery(), containmentArguments());
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(BUILD_BY_HASH);
        }
        String withIt = planOf(containmentQuery(), containmentArguments());

        claim(
                "without the hash index, the search for documents holding " + RARE_SHINGLES + " given word"
                        + " sequences has no index to look the hashes up in, so it reads every row of the run:"
                        + " \"" + withoutIt + "\"",
                () -> assertThat(withoutIt).doesNotContain(BY_HASH).doesNotContain("shingle_hash="));
        claim(
                "and once the redundancy check has built it, the same search looks each hash up in it: \""
                        + withIt + "\"",
                () -> assertThat(withIt).contains(BY_HASH).contains("shingle_hash="));
    }

    /** {@code RedundancyResolution.containmentCandidates}' query, for {@link #RARE_SHINGLES} hashes. */
    private static String containmentQuery() {
        return "SELECT occurrence_id FROM shingle"
                + " WHERE run_id = ? AND shingle_parameter_identity = ? AND shingle_hash IN ("
                + String.join(",", Collections.nCopies(RARE_SHINGLES, "?")) + ")"
                + " GROUP BY occurrence_id HAVING COUNT(DISTINCT shingle_hash) >= ?";
    }

    private static Object[] containmentArguments() {
        List<Object> arguments = new ArrayList<>();
        arguments.add("a-run");
        arguments.add(ShingleParameters.DEFAULT.identity());
        for (long hash = 1; hash <= RARE_SHINGLES; hash++) {
            arguments.add(hash);
        }
        arguments.add(HITS_FOR_A_CANDIDATE);
        return arguments.toArray();
    }

    private List<String> indexesOnShingle() throws SQLException {
        List<String> names = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT name FROM pragma_index_list('shingle')")) {
            while (result.next()) {
                names.add(result.getString(1));
            }
        }
        return names;
    }

    private List<String> columnsOf(String index) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (PreparedStatement statement =
                        connection.prepareStatement("SELECT name FROM pragma_index_info(?) ORDER BY seqno")) {
            statement.setString(1, index);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    columns.add(result.getString(1));
                }
            }
        }
        return columns;
    }

    /** SQLite's plan for {@code query}, every row of it joined into one line. */
    private String planOf(String query, Object... arguments) throws SQLException {
        List<String> details = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("EXPLAIN QUERY PLAN " + query)) {
            for (int i = 0; i < arguments.length; i++) {
                statement.setObject(i + 1, arguments[i]);
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    details.add(result.getString("detail"));
                }
            }
        }
        return String.join(" | ", details);
    }
}
