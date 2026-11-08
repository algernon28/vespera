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
 * its place as the index {@code run_id} leads, which ADR-173's rule needs, and it is also the index a
 * plain read of a run's rows goes through.
 *
 * <p><b>What this class holds of stage 3, corrected for #473 (ADR-219).</b> When ADR-182 was written stage
 * 3 read a run's rows with a plain {@code SELECT}, and this class held that the read goes through {@code
 * shingle_by_run_id} with {@code shingle_by_hash} built or not. Since ADR-211 stage 3 sends one grouping
 * instead, and SQLite answers that one through {@code shingle_by_hash} wherever it is built. So the third
 * test below is of the plain read, which nothing ships, and is described as that. It is kept for the
 * contrast (ADR-219, Tests): the same rows of the same run, read with nothing grouped, are not drawn to
 * {@code shingle_by_hash}, so it is the {@code GROUP BY} that draws the planner there. The fifth holds what the
 * grouping does without ADR-219's clause, which is why ADR-219 pins it; and the fourth holds that the
 * grouping with the clause, {@code INDEXED BY shingle_by_run_id}, is answered through the index on the run
 * in both states. The clause is in {@code DocumentFrequency}: it shipped with the change to {@code
 * similarity} that ADR-220 records (ADR-219 section 2).
 *
 * <p><b>Since ADR-221 the index is over the rows of one stage-2 run</b>, on the granularity and the hash,
 * and every test here that builds it builds that one, for a run id of the minted form. Three things are held
 * of it: that SQLite keeps its statement as issued, which is how stage 4b tells whose index it finds; that
 * containment retrieval, sent with the run as a bound value, is answered through it for that run and not
 * through another run's; and that a read of one occurrence's shingles asked for {@code DISTINCT} is still
 * drawn to it, the premise of #277's guard. The fifth test keeps ADR-219's finding about the index over
 * every run's rows, building that form by its own statement, and adds that the grouping without the clause
 * is not drawn to the index of ADR-221.
 *
 * <p>Read on a database of this test's own, made by running the shipped {@code schema.sql} into a fresh
 * in-memory SQLite, the way a start runs it into an empty working directory. A shared test database
 * would carry whatever indexes another test's invocations had built or dropped. Nothing here runs
 * {@code ANALYZE}, so the planner chooses from the schema alone, as it does on a working directory.
 *
 * <p>{@code containmentRetrievalNeedsTheIndexOnTheHash} pins the premise ADR-182 rests on, that
 * containment retrieval reads every row of the run unless the by-hash index has been built before it.
 */
@Epic("Redundancy")
@Feature("Shingling")
@Issue("381")
@Issue("468")
@Link(name = "ADR-221", url = Adr.THE_HASH_INDEX_IS_OVER_THE_ROWS_OF_THE_RUN_IN_HAND, type = "adr")
@Link(name = "ADR-182", url = Adr.STAGE_4B_BUILDS_THE_BY_HASH_INDEX_STAGE_2_WRITES_WITHOUT, type = "adr")
@Link(name = "ADR-219", url = Adr.STAGE_3S_GROUPING_IS_PINNED_TO_THE_INDEX_ON_THE_RUN, type = "adr")
class ShingleIndexesInTheSchemaTest {

    /** The window the application ships: five words (vespera.redundancy.shingle-words). */
    private static final ShingleParameters FIVE_WORD_SHINGLES = new ShingleParameters(5);

    /** The index containment retrieval reads, under the name ADR-081 gave it; its columns are ADR-221's. */
    private static final String BY_HASH = "shingle_by_hash";

    /** A stage-2 run, in the form a run id is minted in, and another. */
    private static final String A_RUN = "a".repeat(64);

    private static final String ANOTHER_RUN = "b".repeat(64);

    /** The statement stage 4b builds the by-hash index with for {@link #A_RUN} (ADR-221 section 1). */
    private static final String BUILD_BY_HASH = TheRunsHashIndex.statementFor(A_RUN);

    /** The same for {@link #ANOTHER_RUN}: the index as another run's stage 4b leaves it. */
    private static final String BUILD_ANOTHER_RUNS = TheRunsHashIndex.statementFor(ANOTHER_RUN);

    /**
     * Containment retrieval's read of the occurrences that carry one hash, a page of them at a time in
     * occurrence order, as {@code RedundancyResolution} sends it since ADR-225 section 2, with {@link #A_RUN}
     * written into its text as stage 4b writes its stage-2 run since ADR-237.
     */
    private static final String ONE_HASHS_OCCURRENCES = "SELECT DISTINCT occurrence_id FROM shingle"
            + " WHERE run_id = '" + A_RUN + "' AND shingle_parameter_identity = ? AND shingle_hash = ?"
            + " AND occurrence_id > ? ORDER BY occurrence_id LIMIT 1000";

    /** What SQLite's plan says wherever it keeps rows in temporary storage to sort them. */
    private static final String SORTS = "TEMP B-TREE";

    /** A hash and an occurrence to bind in a plan, which depends on neither. */
    private static final long ANY = 1L;

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

    /**
     * ADR-237, amending ADR-221 section 2: the search is sent with the run written into its text, as the index's
     * own text has it, so SQLite sees when it prepares the statement that the index may answer it, and binding a
     * hash does not have it prepared again. Planned here as {@code RedundancyResolution} sends it. The third
     * claim is why stage 4b's check of whose index it finds has to be exact.
     *
     * <p>Since ADR-225 section 2 the search is one read for each hash, of that hash's occurrences in order, and
     * the index ends in the occurrence (section 3): the read is answered from the index alone and sorts nothing.
     */
    @Test
    @Story("When the index on word-sequence hashes exists")
    @DisplayName("Looking for documents that contain another is answered through the hash index once it is built for that run's rows, in order and with nothing sorted, and cannot be without it or with another run's")
    void containmentRetrievalNeedsTheIndexOnTheHash() throws SQLException {
        Object[] arguments = {FIVE_WORD_SHINGLES.identity(), ANY, ANY};
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP INDEX IF EXISTS " + BY_HASH);
        }
        String withoutIt = planOf(ONE_HASHS_OCCURRENCES, arguments);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(BUILD_BY_HASH);
        }
        String withIt = planOf(ONE_HASHS_OCCURRENCES, arguments);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP INDEX " + BY_HASH);
            statement.executeUpdate(BUILD_ANOTHER_RUNS);
        }
        String withAnotherRuns = planOf(ONE_HASHS_OCCURRENCES, arguments);

        claim(
                "without the hash index, the search for the documents holding one given word sequence has no"
                        + " index to look the hash up in, so it reads every row of the run: \"" + withoutIt + "\"",
                () -> assertThat(withoutIt).doesNotContain(BY_HASH).doesNotContain("shingle_hash="));
        claim(
                "and once the redundancy check has built it over the rows of that run, the same search, sent"
                        + " with the run written into its text as the index's is, looks the hash up in it: \""
                        + withIt + "\"",
                () -> assertThat(withIt).contains(BY_HASH).contains("shingle_hash="));
        claim(
                "and reads that hash's documents from the index alone, already in order, going on from the"
                        + " last one read, so nothing is sorted and nothing is kept in temporary storage",
                () -> assertThat(withIt)
                        .contains("COVERING INDEX " + BY_HASH)
                        .contains("occurrence_id>?")
                        .doesNotContain(SORTS));
        claim(
                "an index of that name built over another run's rows does not serve it: the search reads every"
                        + " row of its run again, as it does with no index, so an index left by another run"
                        + " has to be told apart from this run's and built again: \"" + withAnotherRuns + "\"",
                () -> assertThat(withAnotherRuns).doesNotContain(BY_HASH).doesNotContain("shingle_hash="));
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
