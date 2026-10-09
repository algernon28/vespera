package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.util.StreamUtils;

/**
 * The statements of {@code src/main} that SQLite plans through temporary storage, or that build an index,
 * are the ones ADR-218 records, class by class, and the indexes {@code schema.sql} builds are the ones it
 * sizes. ADR-218 makes each of them an exception to ADR-060's bound, or names what else bounds it; a
 * statement added later that sorts is in neither list, and fails here until the record has it.
 *
 * <p>Every text a shipped class holds is read from its compiled form, as {@link
 * EachTableIsNamedOnlyByItsOwnerTest} reads them, so a statement written as several joined literals is one
 * text. A text that opens with a statement's keyword is planned by the bundled SQLite against the shipped
 * {@code schema.sql}, on a database with no row. Where a statement is built at run time around a value, a
 * list of placeholders or a page size, the compiled text holds a mark in the value's place. A mark that is
 * the whole of a bracketed list after {@code IN} is planned as a list of two bound values, because SQLite
 * plans a list of one as an equality and then finds the rows of one occurrence already in order, which a
 * page of them is not; any other mark is planned as one bound value in brackets.
 *
 * <p>Three things in a plan count as temporary storage: a temp B-tree, a materialised subquery, and the
 * list SQLite builds for {@code IN (SELECT ...)}. A scalar subquery holds one value and does not.
 *
 * <p><b>What this does not hold.</b> No size: the bytes a row ADR-218 states were measured by a probe
 * outside the repository, over millions of rows, and a test of this suite cannot watch the temporary files
 * of the process (ADR-211 section 12). No plan over rows: the tables are empty and carry no statistics,
 * as a working directory's carry none. And no statement whose text cannot be planned as it stands, which
 * is why those are counted too, in a claim of their own.
 */
@Epic("Architecture")
@Feature("Temporary storage")
@Issue("466")
@Link(name = "ADR-218", url = Adr.EVERY_STATEMENT_WHOSE_TEMPORARY_FILES_GROW_IS_AN_EXCEPTION_WITH_ITS_SIZE, type = "adr")
@Link(name = "ADR-060", url = Adr.SURVIVORS_IS_AN_ITEM_READER, type = "adr")
class EveryStatementThatSortsIsRecordedTest {

    /** A text that is a statement and not a column name, a message or a pattern: it opens with one of these. */
    private static final Pattern STATEMENT =
            Pattern.compile("^(SELECT|INSERT|DELETE|UPDATE|CREATE INDEX|DROP INDEX)\\b.*", Pattern.DOTALL);

    /**
     * Where a compiled text stands in for a value joined in at run time, and the two forms {@code
     * String.format} takes one in. Each is planned as one bound value in brackets.
     */
    private static final Pattern VALUE_JOINED_IN = Pattern.compile("[\\u0001\\u0002]|%s|%d");

    /** A value joined in as the whole of a list: the placeholders of a page of ids, or of the kinds and runs. */
    private static final Pattern LIST_JOINED_IN = Pattern.compile("IN \\((?:[\\u0001\\u0002]|%s)\\)");

    private static final String A_LIST_OF_TWO_BOUND_VALUES = "IN (?, ?)";

    private static final String ONE_BOUND_VALUE = "(?)";

    /** What a plan says where the statement keeps rows in temporary storage. */
    private static final List<String> TEMPORARY_STORAGE_IN_A_PLAN = List.of("TEMP B-TREE", "MATERIALIZE", "LIST SUBQUERY");

    private static final String INDEX_BUILD = "CREATE INDEX";

    private static final Pattern SCHEMA_INDEX =
            Pattern.compile("^CREATE INDEX IF NOT EXISTS ([a-z_]+) ON ", Pattern.MULTILINE);

    private static final String PACKAGE = "io.algernon.vespera.";

    /**
     * How many statements of each shipped class sort or build an index, as ADR-218's tables list them.
     * Section 1's rows 1 to 20 are the ones that grow with the corpus; section 2's are bounded by a page
     * or by the seed set.
     */
    private static final Map<String, Integer> RECORDED = new TreeMap<>(Map.ofEntries(
            // Row 1, the grouping ADR-211 excepted, and section 2's three statements of one page: the count
            // of a page's shingled survivors, the take-off, and the keyed delete with its list.
            Map.entry("similarity.DocumentFrequency", 4),
            // Row 2: the build of shingle_by_hash.
            Map.entry("similarity.ShingleHashIndex", 1),
            // Row 6: the containment candidates.
            Map.entry("similarity.RedundancyResolution", 1),
            // Row 7: the files stage 2 could not read, by path.
            Map.entry("ledger.Verdicts", 1),
            // Rows 8 and 15: the scores in occurrence order, and the two reads of the embedder identities.
            Map.entry("embedding.RelevanceDistribution", 3),
            // Rows 9 to 11: the scores below the floor, the partitions, the members of one.
            Map.entry("embedding.RelevanceScoreCache", 3),
            // Rows 12 to 14: the cluster sizes of one partition, the membership, the partitions.
            Map.entry("embedding.DocumentClusters", 3),
            // Section 2: the unusable seeds, bounded by the seed set.
            Map.entry("embedding.UnusableSeeds", 1),
            // Row 16: the recorded clusters in the arrangement's order.
            Map.entry("synthesis.Clusters", 1),
            // Row 17: the synthesis docs in the order they were written.
            Map.entry("synthesis.SynthesisDocs", 1),
            // Row 18: the cluster faults in the order they were written.
            Map.entry("synthesis.ClusterFaults", 1),
            // Rows 19 and 20: the account's three counts by kind or category.
            Map.entry("pipeline.InvocationAccount", 3)));

    /**
     * How many statements of each shipped class cannot be planned as their compiled text stands, because
     * a table's name is joined in at run time. None of them sorts: each is the least or the greatest rowid
     * of one table, read by hand for ADR-218. Two classes ask for both ends of a run's span, and start-up
     * asks for a table's greatest.
     */
    private static final Map<String, Integer> BUILT_AROUND_A_TABLES_NAME = new TreeMap<>(Map.of(
            "embedding.SeedCorpusComparison", 2,
            "similarity.RedundancyResolution", 2,
            "pipeline.StartUpIndexAnnouncement", 1));

    /** The indexes of {@code schema.sql} ADR-218 section 1 sizes in rows 3 to 5: thirty-one, by name. */
    private static final List<String> SCHEMA_INDEXES_RECORDED = List.of(
            "call_exemplar_by_occurrence_id",
            "call_exemplar_by_winning_seed_occurrence_id",
            "cluster_by_winning_seed_occurrence_id",
            "cluster_fault_by_winning_seed_occurrence_id",
            "content_hash_by_run_id",
            "detected_format_by_run_id",
            "document_cluster_by_run_id",
            "document_cluster_by_winning_seed_occurrence_id",
            "extraction_cache_key_by_run_id",
            "extraction_fault_by_run_id",
            "extraction_metric_by_run_id",
            "file_occurrence_by_walk_and_size",
            "minhash_signature_by_run_id",
            "redundant_with_by_redundant_with_occurrence_id",
            "redundant_with_by_run_id",
            "relevance_label_by_run_id",
            "relevance_label_provenance_by_run_id",
            "relevance_score_by_run_id",
            "relevance_score_by_winning_seed_occurrence_id",
            "run_by_walk_id",
            "run_upstream_by_upstream_run_id",
            "shingle_by_occurrence",
            "shingle_by_run_id",
            "signature_band_by_bucket",
            "superseded_by_by_representative_occurrence_id",
            "superseded_by_by_run_id",
            "synthesis_doc_by_winning_seed_occurrence_id",
            "unusable_seed_by_run_id",
            "verdict_by_occurrence",
            "verdict_by_run_id",
            "walk_anomaly_by_walk_id");

    @Test
    @Story("A statement that sorts on disk is one somebody measured")
    @DisplayName("The shipped statements that sort or build an index are the recorded ones, class by class")
    void theStatementsThatSortAreTheRecordedOnes() throws Exception {
        Survey survey = survey();
        Map<String, Integer> counted = new TreeMap<>();
        survey.sorting().forEach((shipped, statements) -> counted.put(shipped, statements.size()));

        claim(
                "the shipped classes hold statements the database could plan, so an answer naming none that"
                        + " sorts would not be an empty scan",
                () -> assertThat(survey.planned()).isPositive());
        claim(
                "the classes whose statements sort or build an index, and how many each holds, are the ones"
                        + " written down beside this test: a class or a count that differs is a statement"
                        + " whose temporary files nobody has measured, or one that no longer sorts",
                () -> assertThat(counted)
                        .as("the statements found, by class: %s", survey.sorting())
                        .containsExactlyInAnyOrderEntriesOf(RECORDED));
    }

    @Test
    @Story("A statement that sorts on disk is one somebody measured")
    @DisplayName("The only shipped statements this check cannot plan are the ones written down as read by hand")
    void theStatementsThatCannotBePlannedAreTheRecordedOnes() throws Exception {
        Survey survey = survey();
        Map<String, Integer> counted = new TreeMap<>();
        survey.unplanned().forEach((shipped, statements) -> counted.put(shipped, statements.size()));

        claim(
                "the statements the database could not plan from their text alone are the ones written down"
                        + " beside this test, each of which was read by hand: one more is a statement this"
                        + " check passed without having looked at",
                () -> assertThat(counted)
                        .as("the statements that could not be planned, by class: %s", survey.unplanned())
                        .containsExactlyInAnyOrderEntriesOf(BUILT_AROUND_A_TABLES_NAME));
    }

    @Test
    @Story("A statement that sorts on disk is one somebody measured")
    @DisplayName("The indexes the schema builds are the ones whose build was measured")
    void theIndexesOfTheSchemaAreTheRecordedOnes() throws Exception {
        List<String> built = new ArrayList<>();
        Matcher index = SCHEMA_INDEX.matcher(schema());
        while (index.find()) {
            built.add(index.group(1));
        }

        claim(
                "every index the schema builds is one whose build was measured, and none measured is gone:"
                        + " an index named on one side only is built over the rows already there, on a"
                        + " database that lacks it, with temporary files nobody has sized",
                () -> assertThat(new TreeSet<>(built)).containsExactlyElementsOf(new TreeSet<>(SCHEMA_INDEXES_RECORDED)));
    }

    /** Plans every statement the shipped classes hold, against the shipped schema with no row in it. */
    private static Survey survey() throws Exception {
        Map<String, List<String>> sorting = new TreeMap<>();
        Map<String, List<String>> unplanned = new TreeMap<>();
        int planned = 0;
        try (Connection database = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            ScriptUtils.executeSqlScript(database, new ClassPathResource("schema.sql"));
            for (Map.Entry<String, List<String>> shipped : ShippedClasses.stringsByClass().entrySet()) {
                String name = shipped.getKey().substring(PACKAGE.length());
                for (String text : shipped.getValue()) {
                    if (!STATEMENT.matcher(text).matches()) {
                        continue;
                    }
                    String lists = LIST_JOINED_IN.matcher(text).replaceAll(Matcher.quoteReplacement(A_LIST_OF_TWO_BOUND_VALUES));
                    String statement = VALUE_JOINED_IN.matcher(lists).replaceAll(Matcher.quoteReplacement(ONE_BOUND_VALUE));
                    try {
                        if (statement.startsWith(INDEX_BUILD) | keepsRowsInTemporaryStorage(database, statement)) {
                            sorting.computeIfAbsent(name, ignored -> new ArrayList<>()).add(statement);
                        }
                        planned++;
                    } catch (SQLException notAStatementAsItStands) {
                        unplanned.computeIfAbsent(name, ignored -> new ArrayList<>()).add(statement);
                    }
                }
            }
        }
        return new Survey(sorting, unplanned, planned);
    }

    private static boolean keepsRowsInTemporaryStorage(Connection database, String statement) throws SQLException {
        try (PreparedStatement plan = database.prepareStatement("EXPLAIN QUERY PLAN " + statement)) {
            int values = plan.getParameterMetaData().getParameterCount();
            for (int value = 1; value <= values; value++) {
                plan.setInt(value, 1);
            }
            boolean kept = false;
            try (ResultSet steps = plan.executeQuery()) {
                while (steps.next()) {
                    String step = steps.getString("detail");
                    kept |= TEMPORARY_STORAGE_IN_A_PLAN.stream().anyMatch(step::contains);
                }
            }
            return kept;
        }
    }

    private static String schema() throws Exception {
        return StreamUtils.copyToString(new ClassPathResource("schema.sql").getInputStream(), StandardCharsets.UTF_8);
    }

    /** What the scan found: the statements that sort and the ones it could not plan, by class, and how many it planned. */
    private record Survey(Map<String, List<String>> sorting, Map<String, List<String>> unplanned, int planned) {}
}
