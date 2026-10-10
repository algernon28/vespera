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
 * similarity} that ADR-220 records (ADR-219 section 2), and {@code
 * DocumentFrequencyIsCountedInTheDatabaseTest} holds that the grouping sent carries it.
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

    /** The index containment retrieval reads, under the name and columns ADR-081 gave it. */
    private static final String BY_HASH = "shingle_by_hash";

    /** The one-column index ADR-173's rule asks for on {@code shingle.run_id}. */
    private static final String BY_RUN_ID = "shingle_by_run_id";

    /** A stage-2 run, in the form a run id is minted in, and another. */
    private static final String A_RUN = "a".repeat(64);

    private static final String ANOTHER_RUN = "b".repeat(64);

    /** The statement stage 4b builds the by-hash index with for {@link #A_RUN} (ADR-221 section 1). */
    private static final String BUILD_BY_HASH = TheRunsHashIndex.statementFor(A_RUN);

    /** The same for {@link #ANOTHER_RUN}: the index as another run's stage 4b leaves it. */
    private static final String BUILD_ANOTHER_RUNS = TheRunsHashIndex.statementFor(ANOTHER_RUN);

    /** The index over every run's rows that ADR-182 built, until ADR-221. */
    private static final String BUILD_THE_WHOLE_TABLE_FORM = TheRunsHashIndex.THE_WHOLE_TABLE_FORM;

    /** A read of one occurrence's distinct hashes, which nothing ships: what #277 took out of stage 4's reads. */
    private static final String A_DISTINCT_READ_OF_ONE_OCCURRENCE = "SELECT DISTINCT shingle_hash FROM shingle"
            + " WHERE occurrence_id = ? AND run_id = ? AND shingle_parameter_identity = ?";

    /**
     * A plain read of every shingle row under one stage-2 run: what {@code DocumentFrequency} sent when
     * ADR-182 was written, and has not sent since ADR-211. Nothing in {@code src/main} sends it today.
     */
    private static final String A_PLAIN_READ_OF_A_RUN =
            "SELECT occurrence_id, shingle_parameter_identity, shingle_hash FROM shingle WHERE run_id = ?";

    /**
     * Stage 3's grouping of a run's rows without its insert and without ADR-219's clause: what {@code
     * DocumentFrequency} sent from ADR-211 until the clause shipped. Nothing in {@code src/main} sends it.
     */
    private static final String THE_GROUPING = "SELECT ?, shingle_parameter_identity, shingle_hash,"
            + " COUNT(DISTINCT occurrence_id), COUNT(*) FROM shingle WHERE run_id = ?"
            + " GROUP BY shingle_parameter_identity, shingle_hash HAVING COUNT(DISTINCT occurrence_id) >= 2";

    /** The clause ADR-219 decides for it, after the table's name. */
    private static final String THE_PIN = "INDEXED BY shingle_by_run_id";

    /** The grouping as ADR-219 section 1 writes it and {@code DocumentFrequency} sends it, without its insert. */
    private static final String THE_GROUPING_PINNED =
            THE_GROUPING.replace("FROM shingle WHERE", "FROM shingle " + THE_PIN + " WHERE");

    /** What SQLite's plan says where it sorts the rows for the grouping, in temporary storage. */
    private static final String SORTS_FOR_THE_GROUPING = "USE TEMP B-TREE FOR GROUP BY";

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
    @DisplayName("A plain read of one run's word sequences, with nothing counted, reads them in the order they were written")
    void aPlainReadOfARunGoesThroughTheIndexOnTheRun() throws SQLException {
        String withoutTheHashIndex = planOf(A_PLAIN_READ_OF_A_RUN, A_RUN);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(BUILD_BY_HASH);
        }
        String withTheHashIndexBuilt = planOf(A_PLAIN_READ_OF_A_RUN, A_RUN);

        claim(
                "reading every word sequence of one run, and only reading them, is answered through the index on"
                        + " the run, which keeps the rows in the order they were written, and not through an"
                        + " index ordered by hash, which fetches each row from a different place: \""
                        + withoutTheHashIndex + "\"",
                () -> assertThat(withoutTheHashIndex).contains(BY_RUN_ID).doesNotContain(BY_HASH));
        claim(
                "and that stays so after the redundancy check has built its index on the hash: \""
                        + withTheHashIndexBuilt + "\"",
                () -> assertThat(withTheHashIndexBuilt).contains(BY_RUN_ID).doesNotContain(BY_HASH));
    }

    @Test
    @Story("When the index on word-sequence hashes exists")
    @DisplayName("Counting how often each word sequence recurs, told which index to read through, reads a run's rows in the order they were written whether or not the hash index is built")
    void theGroupingWithItsPinGoesThroughTheIndexOnTheRunInBothStates() throws SQLException {
        String withoutTheHashIndex = planOf(THE_GROUPING_PINNED, "a-stage-3-run", A_RUN);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(BUILD_BY_HASH);
        }
        String withTheHashIndexBuilt = planOf(THE_GROUPING_PINNED, "a-stage-3-run", A_RUN);

        claim(
                "the count planned below is written with the words that tell the database which index to read"
                        + " through, placed after the table's name; it is otherwise the same count that"
                        + " another check plans without those words",
                () -> assertThat(THE_GROUPING_PINNED).contains("FROM shingle " + THE_PIN + " WHERE run_id = ?"));
        claim(
                "with no hash index, the count told to read through the index on the run does, and sorts the"
                        + " rows itself to count them: \"" + withoutTheHashIndex + "\"",
                () -> assertThat(withoutTheHashIndex)
                        .contains(BY_RUN_ID)
                        .contains(SORTS_FOR_THE_GROUPING)
                        .doesNotContain(BY_HASH));
        claim(
                "and once the redundancy check has built its index on the hash the plan is the same, word for"
                        + " word, so the count takes the same course whichever state it finds the database in",
                () -> assertThat(withTheHashIndexBuilt).isEqualTo(withoutTheHashIndex));
    }

    /**
     * Why ADR-219's clause is needed, and so true of SQLite with the clause shipped or not: this is the
     * grouping without it. It plans this text and not what {@code DocumentFrequency} sends, which carries
     * the clause, so it holds what SQLite would do were the clause taken out again.
     */
    @Test
    @Story("When the index on word-sequence hashes exists")
    @DisplayName("Counting how often each word sequence recurs, left to choose, reads through the hash index once it is built, fetching each row from a different place")
    void theGroupingWithoutAPinGoesThroughTheHashIndexOnceItIsBuilt() throws SQLException {
        String withoutTheHashIndex = planOf(THE_GROUPING, "a-stage-3-run", A_RUN);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(BUILD_THE_WHOLE_TABLE_FORM);
        }
        String withTheHashIndexBuilt = planOf(THE_GROUPING, "a-stage-3-run", A_RUN);

        claim(
                "with no hash index, the count left to choose reads through the index on the run and sorts the"
                        + " rows itself: \"" + withoutTheHashIndex + "\"",
                () -> assertThat(withoutTheHashIndex)
                        .contains(BY_RUN_ID)
                        .contains(SORTS_FOR_THE_GROUPING)
                        .doesNotContain(BY_HASH));
        claim(
                "once the redundancy check has built its index on the hash, the same count reads through that"
                        + " index instead, which hands it the rows already in order, so it sorts nothing for"
                        + " the count and fetches each row from wherever it was written: \""
                        + withTheHashIndexBuilt + "\"",
                () -> assertThat(withTheHashIndexBuilt)
                        .contains("USING INDEX " + BY_HASH)
                        .doesNotContain(SORTS_FOR_THE_GROUPING));

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP INDEX " + BY_HASH);
            statement.executeUpdate(BUILD_BY_HASH);
        }
        String withTheRunsIndexBuilt = planOf(THE_GROUPING, "a-stage-3-run", A_RUN);
        claim(
                "that was the index over every run's rows; with the index built over this run's rows alone,"
                        + " as it is built now, the count left to choose is not drawn to it and takes the"
                        + " course it takes with no hash index: \"" + withTheRunsIndexBuilt + "\"",
                () -> assertThat(withTheRunsIndexBuilt).isEqualTo(withoutTheHashIndex));
    }

    /**
     * ADR-221 section 2: the search is sent with the run as a bound value, and SQLite matches that value to
     * the one the index was built for when it plans the statement again with its values bound (SQLite 3.20.0,
     * "The query planner examines the values of bound parameters to help determine if a partial index is
     * usable"). Planned here as {@code RedundancyResolution} sends it, values bound before the plan is read.
     * The third claim is why stage 4b's check of whose index it finds has to be exact.
     */
    @Test
    @Story("When the index on word-sequence hashes exists")
    @DisplayName("Looking for documents that contain another is answered through the hash index once it is built for that run's rows, and cannot be without it or with another run's")
    void containmentRetrievalNeedsTheIndexOnTheHash() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP INDEX IF EXISTS " + BY_HASH);
        }
        String withoutIt = planOf(containmentQuery(), containmentArguments());
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(BUILD_BY_HASH);
        }
        String withIt = planOf(containmentQuery(), containmentArguments());
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP INDEX " + BY_HASH);
            statement.executeUpdate(BUILD_ANOTHER_RUNS);
        }
        String withAnotherRuns = planOf(containmentQuery(), containmentArguments());

        claim(
                "without the hash index, the search for documents holding " + RARE_SHINGLES + " given word"
                        + " sequences has no index to look the hashes up in, so it reads every row of the run:"
                        + " \"" + withoutIt + "\"",
                () -> assertThat(withoutIt).doesNotContain(BY_HASH).doesNotContain("shingle_hash="));
        claim(
                "and once the redundancy check has built it over the rows of that run, the same search, sent"
                        + " with the run as a value and not written into its text, looks each hash up in it: \""
                        + withIt + "\"",
                () -> assertThat(withIt).contains(BY_HASH).contains("shingle_hash="));
        claim(
                "an index of that name built over another run's rows does not serve it: the search reads every"
                        + " row of its run again, as it does with no index, so an index left by another run"
                        + " has to be told apart from this run's and built again: \"" + withAnotherRuns + "\"",
                () -> assertThat(withAnotherRuns).doesNotContain(BY_HASH).doesNotContain("shingle_hash="));
    }

    /** What the index is, as SQLite keeps it: two columns, and only the rows of one run (ADR-221 section 1). */
    @Test
    @Story("When the index on word-sequence hashes exists")
    @DisplayName("The hash index holds the measurement settings and the hash of one run's rows, and the database keeps the statement it was built with word for word")
    void theHashIndexIsOverTheRowsOfOneRunAndItsStatementIsKeptAsIssued() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(BUILD_BY_HASH);
        }

        claim(
                "the index holds the measurement settings and the hash, in that order, and not the run, which"
                        + " every row in it shares",
                () -> assertThat(columnsOf(BY_HASH)).containsExactly("shingle_parameter_identity", "shingle_hash"));
        claim(
                "it is an index over part of the table's rows",
                () -> assertThat(partialIndexesOnShingle()).containsExactly(BY_HASH));
        claim(
                "and the database keeps the statement that built it exactly as it was issued, which is how the"
                        + " run it was built for is read back",
                () -> assertThat(storedStatementOf(BY_HASH)).isEqualTo(BUILD_BY_HASH));
        claim(
                "a statement for another run differs from it, so the two are told apart by comparing them",
                () -> assertThat(BUILD_ANOTHER_RUNS).isNotEqualTo(BUILD_BY_HASH));
    }

    /**
     * The premise of #277's guard, {@code RedundancyResolutionTest.readsOneDocumentsShinglesThroughItsOwnIndex},
     * under ADR-221's index: that guard reads its plans with the run's index built so that a {@code DISTINCT}
     * put back into a read of one occurrence's shingles would show.
     */
    @Test
    @Story("When the index on word-sequence hashes exists")
    @DisplayName("One document's word sequences are read through the index on the document with the hash index built, unless they are asked for distinct, which reads the whole run's index instead")
    void aReadOfOneOccurrenceGoesThroughTheIndexOnTheOccurrenceUnlessAskedForDistinct() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(BUILD_BY_HASH);
        }
        String distinct = planOf(A_DISTINCT_READ_OF_ONE_OCCURRENCE, 1L, A_RUN, ShingleParameters.DEFAULT.identity());
        String plain = planOf(
                A_DISTINCT_READ_OF_ONE_OCCURRENCE.replace("SELECT DISTINCT", "SELECT"),
                1L,
                A_RUN,
                ShingleParameters.DEFAULT.identity());

        claim(
                "the read as it ships, with nothing made distinct, goes through the index on the document: \""
                        + plain + "\"",
                () -> assertThat(plain).contains("shingle_by_occurrence").doesNotContain(BY_HASH));
        claim(
                "the same read asked for distinct hashes is drawn to the hash index, which is in hash order,"
                        + " and looks up no hash in it, so it goes through every row of the run for one"
                        + " document, as it did with the index over every run's rows: \"" + distinct + "\"",
                () -> assertThat(distinct)
                        .contains("USING INDEX " + BY_HASH)
                        .doesNotContain("shingle_hash=")
                        .doesNotContain("shingle_by_occurrence"));
    }

    private List<String> partialIndexesOnShingle() throws SQLException {
        List<String> names = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet result =
                        statement.executeQuery("SELECT name FROM pragma_index_list('shingle') WHERE partial = 1")) {
            while (result.next()) {
                names.add(result.getString(1));
            }
        }
        return names;
    }

    private String storedStatementOf(String index) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT sql FROM sqlite_master WHERE type = 'index' AND name = ?")) {
            statement.setString(1, index);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        }
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
        arguments.add(A_RUN);
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
