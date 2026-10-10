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
import java.sql.Statement;
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
 * are in the classes ADR-218 records, as many in each as it lists, and the indexes {@code schema.sql}
 * builds are the ones it lists. ADR-218 makes each of them an exception to ADR-060's bound, or names what
 * else bounds it; a class that gains a statement that sorts, in a form this test reads, fails here until
 * the record has it.
 *
 * <p>ADR-220 section 15 amends what ADR-218 lists: two of its reads are gone, and stage 4b holds two
 * statements that sort and are in none of its tables. One is bounded by one occurrence. The other grows
 * with the corpus and is excepted as it stands by the operator's choice, with no measured size: its size
 * and its bounded form are #476's.
 *
 * <p>ADR-224 amends it again: the two reads of the embedder identities and the invocation account's three
 * counts by kind or category, rows 15, 19 and 20, sort nothing, so {@code RelevanceDistribution} and {@code
 * InvocationAccount} hold no statement that sorts and are in neither map below. Rows 6, 7, 10 and 11 stay
 * excepted by that record, each with its reason.
 *
 * <p>Every text a shipped class holds is read from its compiled form, as {@link
 * EachTableIsNamedOnlyByItsOwnerTest} reads them, so a statement written as several joined literals is one
 * text. A text that opens with a statement's keyword, in either case and after any white space, is planned
 * by the bundled SQLite against the shipped {@code schema.sql}, on a database with no row. Where a
 * statement is built at run time around a value, a list of placeholders or a page size, the compiled text
 * holds a mark in the value's place. A mark that is the whole of a bracketed list after {@code IN} is
 * planned as a list of two bound values, because SQLite plans a list of one as an equality and then finds
 * the rows of one occurrence already in order, which a page of them is not; any other mark is planned as
 * one bound value in brackets.
 *
 * <p><b>Every statement is planned twice</b>, because a working directory is in one of two states and a
 * plan can differ between them: as {@code schema.sql} leaves the database, which is how it stands from
 * stage 2's first chunk until stage 4b, and with {@code shingle_by_hash} built by the statement {@code
 * ShingleHashIndex} ships, which is how it stands from stage 4b until stage 2 next runs (ADR-182). The
 * statements that sort are held for each state.
 *
 * <p>Three things in a plan count as temporary storage: a temp B-tree, a materialised subquery, and the
 * list SQLite builds for {@code IN (SELECT ...)}. A scalar subquery holds one value and does not.
 *
 * <p><b>What this does not hold.</b>
 *
 * <ul>
 *   <li>No size: the bytes a row ADR-218 states were measured by a probe outside the repository, over
 *       millions of rows, and a test of this suite cannot watch the temporary files of the process
 *       (ADR-211 section 12).
 *   <li>No plan over rows: the tables are empty and carry no statistics. A working directory's carry no
 *       statistics either, nothing in {@code src/main} running {@code ANALYZE}, but its tables hold rows.
 *   <li>Which statement: it holds how many statements of a class sort, so one sorting statement put in
 *       the place of another in the same class passes.
 *   <li>A statement completed at run time whose first constant is a statement on its own: text appended
 *       to it by a builder or a second literal is not in that constant, so it is planned without its
 *       tail, and an {@code ORDER BY} added there is not seen.
 *   <li>A statement whose text cannot be planned as it stands. Those are counted in a claim of their own.
 *   <li>A statement that opens with none of the keywords read, one read from a file, and one whose list
 *       is planned differently at two values than at a thousand.
 * </ul>
 */
@Epic("Architecture")
@Feature("Temporary storage")
@Issue("466")
@Issue("458")
@Issue("477")
@Link(name = "ADR-218", url = Adr.EVERY_STATEMENT_WHOSE_TEMPORARY_FILES_GROW_IS_AN_EXCEPTION_WITH_ITS_SIZE, type = "adr")
@Link(name = "ADR-220", url = Adr.NO_CLASS_HOLDS_EVERY_OCCURRENCE_OF_A_RUN, type = "adr")
@Link(name = "ADR-224", url = Adr.THE_ACCOUNTS_COUNTS_AND_THE_EMBEDDER_IDENTITY_READS_SORT_NOTHING, type = "adr")
@Link(name = "ADR-060", url = Adr.SURVIVORS_IS_AN_ITEM_READER, type = "adr")
class EveryStatementThatSortsIsRecordedTest {

    /**
     * A text that is a statement and not a column name or a pattern: it opens with one of these, in
     * either case, after any white space. A sentence that opens with one of the words is read as a
     * statement too, cannot be planned, and is counted among the texts read by hand.
     */
    private static final Pattern STATEMENT = Pattern.compile(
            "^\\s*(SELECT|INSERT|DELETE|UPDATE|REPLACE|WITH|CREATE\\s+(UNIQUE\\s+)?INDEX|DROP\\s+INDEX"
                    + "|CREATE\\s+(TEMP\\s+|TEMPORARY\\s+)?TABLE)\\b.*",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static final Pattern INDEX_BUILD =
            Pattern.compile("^\\s*CREATE\\s+(UNIQUE\\s+)?INDEX\\b.*", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

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

    private static final Pattern SCHEMA_INDEX =
            Pattern.compile("^CREATE INDEX IF NOT EXISTS ([a-z_]+) ON ", Pattern.MULTILINE);

    private static final String PACKAGE = "io.algernon.vespera.";

    /** What stands for the run id in the index build applied for the second planning: 64 lowercase hex characters. */
    private static final String A_RUN_ID_OF_THE_MINTED_FORM = "a".repeat(64);

    /** The class whose one index build is applied for the second planning. */
    private static final String THE_CLASS_THAT_BUILDS_THE_HASH_INDEX = PACKAGE + "similarity.ShingleHashIndex";

    /**
     * How many statements of each shipped class sort or build an index on a database as {@code
     * schema.sql} leaves it, as ADR-218 lists them under Measured: the numbered rows are those of its
     * table of statements whose temporary files grow with the corpus, and "bounded" is its table of what
     * sorts and is bounded by something else. Row 14 is struck in the record and has no statement here.
     * Rows 8 and 9 are gone since ADR-220, whose section 15 also records the two statements of stage 4b's
     * that are in neither table. Rows 15, 19 and 20 are gone since ADR-224: the classes that held them,
     * {@code embedding.RelevanceDistribution} and {@code pipeline.InvocationAccount}, hold no statement
     * that sorts, and a class that holds none has no entry.
     */
    private static final Map<String, Integer> RECORDED = new TreeMap<>(Map.ofEntries(
            // Row 1, the grouping ADR-211 excepted, and three bounded by one page: the count of a page's
            // shingled survivors, the take-off, and the keyed delete with its list.
            Map.entry("similarity.DocumentFrequency", 4),
            // Row 2: the build of shingle_by_hash.
            Map.entry("similarity.ShingleHashIndex", 1),
            // Row 6, the containment candidates, and the two of ADR-220 section 15: an occurrence's rarest
            // shingles, bounded by one occurrence's shingle rows; and a page's candidate pairs, whose rows
            // grow with the signed occurrences that share a bucket, excepted as it stands by the operator's
            // choice, which nobody has measured: its size and its bounded form are #476's.
            Map.entry("similarity.RedundancyResolution", 3),
            // Row 7: the files stage 2 could not read, by path.
            Map.entry("ledger.Verdicts", 1),
            // Rows 10 and 11: the partitions, and the members of one. Row 9, the scores below the floor in
            // occurrence order, is gone: 5e reads a page of them at a time by row number (ADR-220 section 5).
            Map.entry("embedding.RelevanceScoreCache", 2),
            // Rows 12 and 13: the cluster sizes of one partition, and the membership.
            Map.entry("embedding.DocumentClusters", 2),
            // Bounded by the seed set: the unusable seeds.
            Map.entry("embedding.UnusableSeeds", 1),
            // Row 16: the recorded clusters in the arrangement's order.
            Map.entry("synthesis.Clusters", 1),
            // Row 17: the synthesis docs in the order they were written.
            Map.entry("synthesis.SynthesisDocs", 1),
            // Row 18: the cluster faults in the order they were written.
            Map.entry("synthesis.ClusterFaults", 1)));

    /**
     * The same once stage 4b has built {@code shingle_by_hash}, which is what ADR-218 says of the two
     * states under Measured.
     */
    private static final Map<String, Integer> RECORDED_WITH_THE_HASH_INDEX = new TreeMap<>(RECORDED);

    /**
     * How many texts of each shipped class open as a statement does and cannot be planned as they stand,
     * each read by hand for ADR-218, and none of them a statement that sorts. Five are the least or the
     * greatest rowid of a table whose name is joined in at run time: two classes ask for both ends of a
     * run's span, and start-up asks for a table's greatest. The sixth, the ledger's, is no statement: it
     * is the message of an exception, which opens with the word "Insert".
     */
    private static final Map<String, Integer> READ_BY_HAND = new TreeMap<>(Map.of(
            "embedding.SeedCorpusComparison", 2,
            "similarity.RedundancyResolution", 2,
            "pipeline.StartUpIndexAnnouncement", 1,
            "ledger.Walks", 1));

    /** The indexes of {@code schema.sql} that rows 3 to 5 of ADR-218's table list: thirty-one, by name. */
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
    @DisplayName("The shipped statements that sort or build an index are in the recorded classes, as many in each as recorded")
    void theStatementsThatSortAreTheRecordedOnes() throws Exception {
        Survey survey = survey(false);

        claim(
                "the shipped classes hold statements the database could plan, so an answer naming none that"
                        + " sorts would not be an empty scan",
                () -> assertThat(survey.planned()).isPositive());
        claim(
                "the classes whose statements sort or build an index, and how many each holds, are the ones"
                        + " written down beside this test: a class or a count that differs is a statement"
                        + " whose temporary files nobody has measured, or one that no longer sorts",
                () -> assertThat(survey.sortingByClass())
                        .as("the statements found, by class: %s", survey.sorting())
                        .containsExactlyInAnyOrderEntriesOf(RECORDED));
    }

    @Test
    @Story("A statement that sorts on disk is one somebody measured")
    @DisplayName("The same holds once the index a later stage builds over the text fragments is there")
    void theStatementsThatSortWithTheHashIndexAreTheRecordedOnes() throws Exception {
        Survey survey = survey(true);

        claim(
                "with the index over the text fragments built, as a database has it from the stage that"
                        + " builds it until the stage that removes it, the classes whose statements sort, and"
                        + " how many each holds, are the ones written down for that state: a difference is a"
                        + " statement planned another way once the index exists, which nobody has recorded",
                () -> assertThat(survey.sortingByClass())
                        .as("the statements found, by class: %s", survey.sorting())
                        .containsExactlyInAnyOrderEntriesOf(RECORDED_WITH_THE_HASH_INDEX));
    }

    @Test
    @Story("A statement that sorts on disk is one somebody measured")
    @DisplayName("The only shipped statements this check cannot plan are the ones written down as read by hand")
    void theStatementsThatCannotBePlannedAreTheRecordedOnes() throws Exception {
        Survey survey = survey(false);
        Map<String, Integer> counted = new TreeMap<>();
        survey.unplanned().forEach((shipped, statements) -> counted.put(shipped, statements.size()));

        claim(
                "the texts that open as a statement does and that the database could not plan are the ones"
                        + " written down beside this test, each of which was read by hand: one more is a"
                        + " statement this check passed without having looked at",
                () -> assertThat(counted)
                        .as("the statements that could not be planned, by class: %s", survey.unplanned())
                        .containsExactlyInAnyOrderEntriesOf(READ_BY_HAND));
    }

    @Test
    @Story("A statement that sorts on disk is one somebody measured")
    @DisplayName("The indexes the schema builds are the recorded ones")
    void theIndexesOfTheSchemaAreTheRecordedOnes() throws Exception {
        List<String> built = new ArrayList<>();
        Matcher index = SCHEMA_INDEX.matcher(schema());
        while (index.find()) {
            built.add(index.group(1));
        }

        claim(
                "every index the schema builds is one the record lists, and none listed is gone: an index"
                        + " named on one side only is built over the rows already there, on a database that"
                        + " lacks it, with temporary files nobody has looked at",
                () -> assertThat(new TreeSet<>(built)).containsExactlyElementsOf(new TreeSet<>(SCHEMA_INDEXES_RECORDED)));
    }

    /**
     * Plans every statement the shipped classes hold, against the shipped schema with no row in it, and
     * with the index stage 4b builds where {@code withTheHashIndex} says so.
     */
    private static Survey survey(boolean withTheHashIndex) throws Exception {
        Map<String, List<String>> strings = ShippedClasses.stringsByClass();
        Map<String, List<String>> sorting = new TreeMap<>();
        Map<String, List<String>> unplanned = new TreeMap<>();
        int planned = 0;
        try (Connection database = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            ScriptUtils.executeSqlScript(database, new ClassPathResource("schema.sql"));
            if (withTheHashIndex) {
                String build = strings.get(THE_CLASS_THAT_BUILDS_THE_HASH_INDEX).stream()
                        .filter(text -> INDEX_BUILD.matcher(text).matches())
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "the class that builds the index holds no statement that builds one"));
                // Since ADR-221 the statement carries the stage-2 run it is built for, joined in at run time,
                // so the compiled text holds a mark where the run id goes: an id of the minted form is put
                // there. Every statement below is planned with whole numbers bound, so none of them names
                // that run and the index is another run's to each: the plans of this state are the plans
                // with an index SQLite may not use, and what the index does for its own run's containment
                // read is ShingleIndexesInTheSchemaTest's.
                String forARun = VALUE_JOINED_IN.matcher(build).replaceAll(A_RUN_ID_OF_THE_MINTED_FORM);
                try (Statement statement = database.createStatement()) {
                    statement.execute(forARun);
                }
            }
            for (Map.Entry<String, List<String>> shipped : strings.entrySet()) {
                String name = shipped.getKey().substring(PACKAGE.length());
                for (String text : shipped.getValue()) {
                    if (!STATEMENT.matcher(text).matches()) {
                        continue;
                    }
                    String lists = LIST_JOINED_IN.matcher(text).replaceAll(Matcher.quoteReplacement(A_LIST_OF_TWO_BOUND_VALUES));
                    String statement = VALUE_JOINED_IN.matcher(lists).replaceAll(Matcher.quoteReplacement(ONE_BOUND_VALUE));
                    try {
                        // An index build is counted for what it is and is not planned: ADR-221's carries no
                        // IF NOT EXISTS, so with the index already there it could not be.
                        if (INDEX_BUILD.matcher(statement).matches() || keepsRowsInTemporaryStorage(database, statement)) {
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
    private record Survey(Map<String, List<String>> sorting, Map<String, List<String>> unplanned, int planned) {

        Map<String, Integer> sortingByClass() {
            Map<String, Integer> counted = new TreeMap<>();
            sorting.forEach((shipped, statements) -> counted.put(shipped, statements.size()));
            return counted;
        }
    }
}
