package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.StatementLog;
import io.algernon.vespera.engine.extraction.FailureCategory;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.ledger.VerdictKind;
import io.algernon.vespera.engine.synthesis.ClusterFaultKind;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import tools.jackson.databind.JsonNode;

/**
 * The invocation's three counts by a closed enumeration, the verdicts by kind, the extraction faults by category
 * and the cluster faults by kind, are the counts the report holds as {@code counts}, and keep nothing in temporary
 * storage (ADR-224 section 1, ADR-362 section 3): each is one statement that selects counts and no column, names
 * every constant of its enumeration in its own text, and gives {@code other} as the total less the counts it names.
 * The report holds a count for every constant, in the order the enumeration declares them, and {@code other} last.
 *
 * <p>The counts are read from the report the account hands them to, which ADR-362 section 4 made the one place
 * they are written: the account's own file, whose lines this test once read, is retired.
 *
 * <p>The account is fed by hand, as in {@code InvocationAccountTest}: an in-memory database holding only the
 * columns the counts read. The statements it sends are then planned against a second database, the shipped
 * {@code schema.sql} with no row, where the indexes are the shipped ones.
 *
 * <p><b>Not held here.</b> That a stored value differing from a constant's name by a character outside
 * ASCII is counted under {@code other}, where Java's comparison counted it with its kind: no writer stores
 * one. No size, and no plan over rows.
 */
@Epic("Pipeline")
@Feature("The invocation report")
@Issue("477")
@Link(name = "ADR-224", url = Adr.THE_ACCOUNTS_COUNTS_AND_THE_EMBEDDER_IDENTITY_READS_SORT_NOTHING, type = "adr")
@Link(name = "ADR-198", url = Adr.EVERY_INVOCATION_WRITES_AN_ACCOUNT_THAT_NAMES_NO_DOCUMENT, type = "adr")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class TheAccountsCountsByKindSortNothingTest {

    /** A run id is a 64-character hexadecimal digest. */
    private static final String ONE_RUN = "0123456789abcdef".repeat(4);

    /** A second run of the same invocation, so that every count is over a list of two runs. */
    private static final String ANOTHER_RUN = "fedcba9876543210".repeat(4);

    /** A stored value that is in none of the three enumerations. */
    private static final String NOT_IN_ANY_ENUMERATION = "a-value-nobody-declared";

    /**
     * The first constant of an enumeration is stored once, the second twice, and so on: a constant's rows
     * are its ordinal plus this, so that none has no row and no two have as many.
     */
    private static final int PLACES_ARE_COUNTED_FROM = 1;

    /** The members of the report's {@code counts} that hold the three counts. */
    private static final String VERDICTS = "verdicts";

    private static final String EXTRACTION_FAULTS = "extractionFaults";

    private static final String CLUSTER_FAULTS = "clusterFaults";

    /** The count of the rows no constant names: the total less the counts that do. */
    private static final String OTHER = "other";

    /** A column or a word a count must never read: free text about an occurrence, or where it is. */
    private static final Pattern FREE_TEXT_OR_A_PATH = Pattern.compile("\\b(detail|reason|path|label|title)\\b");

    @TempDir
    Path workingDirectory;

    private SingleConnectionDataSource database;
    private SingleConnectionDataSource shippedSchema;
    private StatementLog log;
    private JdbcTemplate jdbc;

    @BeforeEach
    void anEmptyLedgerAndTheShippedSchema() throws SQLException {
        database = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        log = new StatementLog(database);
        jdbc = log.jdbcTemplate();
        jdbc.execute("CREATE TABLE file_occurrence (id INTEGER PRIMARY KEY, walk_id INTEGER)");
        jdbc.execute("CREATE TABLE run (id TEXT PRIMARY KEY, walk_id INTEGER)");
        jdbc.execute("CREATE TABLE verdict (run_id TEXT, kind TEXT)");
        jdbc.execute("CREATE TABLE extraction_fault (run_id TEXT, category TEXT)");
        jdbc.execute("CREATE TABLE cluster_fault (run_id TEXT, kind TEXT)");
        jdbc.execute("CREATE TABLE cluster (run_id TEXT)");
        jdbc.execute("CREATE TABLE synthesis_doc (run_id TEXT)");
        shippedSchema = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        try (Connection connection = shippedSchema.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
        }
    }

    @AfterEach
    void closeBothDatabases() {
        database.destroy();
        shippedSchema.destroy();
    }

    @Test
    @Story("A count is held for each kind, and is nothing where no row carries it")
    @DisplayName("A kind no row carries is counted as zero, and nothing is counted as other when every stored value is known")
    void aKindNoRowCarriesIsZero() {
        verdict(ONE_RUN, "BROKEN");
        verdict(ANOTHER_RUN, "BROKEN");
        verdict(ANOTHER_RUN, "PASSED");
        extractionFault(ONE_RUN, "timeout");
        clusterFault(ANOTHER_RUN, "SCHEMA_VIOLATION");

        JsonNode counts = theCountsOfAnInvocationOverBothRuns();

        claim(
                "the verdicts are counted over both runs under the two kinds stored, two broken and one passed,"
                        + " and every other kind, other among them, is zero",
                () -> assertThat(nonZero(counts, VERDICTS)).containsExactly(Map.entry("broken", 2L), Map.entry("passed", 1L)));
        claim(
                "the one conversion fault stored is the only category counted",
                () -> assertThat(nonZero(counts, EXTRACTION_FAULTS)).containsExactly(Map.entry("timeout", 1L)));
        claim(
                "the one fault of a group that could not be written is the only kind counted",
                () -> assertThat(nonZero(counts, CLUSTER_FAULTS)).containsExactly(Map.entry("schema-violation", 1L)));
    }

    @Test
    @Story("A count is held for each kind, and is nothing where no row carries it")
    @DisplayName("With no row at all under the invocation's runs, every count by kind is zero")
    void noRowCountsNothing() {
        JsonNode counts = theCountsOfAnInvocationOverBothRuns();

        claim(
                "no verdict, no conversion fault and no fault of a group is counted: a count of nothing is a zero",
                () -> assertThat(nonZero(counts, VERDICTS))
                        .isEmpty());
        claim("nor is any conversion fault", () -> assertThat(nonZero(counts, EXTRACTION_FAULTS)).isEmpty());
        claim("nor any fault of a group", () -> assertThat(nonZero(counts, CLUSTER_FAULTS)).isEmpty());
    }

    @Test
    @Story("A stored value outside a closed vocabulary is never held")
    @DisplayName("A value that is no declared kind is counted as other, and one that differs only in letter case is counted with its kind")
    void anUndeclaredValueIsOtherAndLetterCaseIsNotADifference() {
        verdict(ONE_RUN, "BROKEN");
        verdict(ONE_RUN, "broken");
        verdict(ANOTHER_RUN, "Broken");
        verdict(ONE_RUN, NOT_IN_ANY_ENUMERATION);
        verdict(ANOTHER_RUN, NOT_IN_ANY_ENUMERATION);
        extractionFault(ONE_RUN, "timeout");
        extractionFault(ONE_RUN, "TIMEOUT");
        extractionFault(ANOTHER_RUN, NOT_IN_ANY_ENUMERATION);
        clusterFault(ONE_RUN, "schema_violation");
        clusterFault(ONE_RUN, NOT_IN_ANY_ENUMERATION);

        JsonNode counts = theCountsOfAnInvocationOverBothRuns();

        claim(
                "the three verdicts stored as broken in three letter cases are one count of three, and the two"
                        + " whose value nobody declared are counted as other",
                () -> assertThat(nonZero(counts, VERDICTS)).containsExactly(Map.entry("broken", 3L), Map.entry(OTHER, 2L)));
        claim(
                "the two conversion faults stored as timeout in two letter cases are one count of two, and the"
                        + " undeclared one is other",
                () -> assertThat(nonZero(counts, EXTRACTION_FAULTS))
                        .containsExactly(Map.entry("timeout", 2L), Map.entry(OTHER, 1L)));
        claim(
                "the fault of a group stored in lower case is counted under its kind's own name, and the"
                        + " undeclared one is other",
                () -> assertThat(nonZero(counts, CLUSTER_FAULTS))
                        .containsExactly(Map.entry("schema-violation", 1L), Map.entry(OTHER, 1L)));
        claim(
                "the undeclared value's own text is nowhere in the report",
                () -> assertThat(TheReportFile.textIn(workingDirectory)).doesNotContain(NOT_IN_ANY_ENUMERATION));
    }

    @Test
    @Story("The counts come in one order")
    @DisplayName("The counts of each group come in the order the kinds are declared, other last, whatever order the rows were stored in")
    void theCountsComeInTheOrderTheKindsAreDeclared() {
        for (String kind : List.of("PASSED", NOT_IN_ANY_ENUMERATION, "EXTRACTION_FAILED", "BROKEN")) {
            verdict(ONE_RUN, kind);
        }
        for (String category : List.of("unknown", "policy", NOT_IN_ANY_ENUMERATION, "internal", "backend_failure")) {
            extractionFault(ANOTHER_RUN, category);
        }

        JsonNode counts = theCountsOfAnInvocationOverBothRuns();

        claim(
                "the verdict kinds are counted in the order they are declared, and other comes last",
                () -> assertThat(countsOf(counts, VERDICTS).keySet())
                        .containsExactlyElementsOf(namesOf(VerdictKind.values())));
        claim(
                "the conversion faults' categories are counted in the order they are declared, other last",
                () -> assertThat(countsOf(counts, EXTRACTION_FAULTS).keySet())
                        .containsExactlyElementsOf(namesOf(FailureCategory.values())));
    }

    @Test
    @Story("A count by kind reads its rows once and sorts none of them")
    @DisplayName("Each of the three counts is one statement that names every declared kind, reads no free text, and keeps no row in temporary storage")
    void eachCountIsOneStatementNamingEveryConstantAndSortingNothing() {
        verdict(ONE_RUN, "BROKEN");
        extractionFault(ONE_RUN, "timeout");
        clusterFault(ANOTHER_RUN, "SCHEMA_VIOLATION");
        log.clear();

        theCountsOfAnInvocationOverBothRuns();
        List<String> ofVerdicts = sentOver("verdict");
        List<String> ofExtractionFaults = sentOver("extraction_fault");
        List<String> ofClusterFaults = sentOver("cluster_fault");
        List<String> allThree =
                Stream.of(ofVerdicts, ofExtractionFaults, ofClusterFaults).flatMap(List::stream).toList();

        claim(
                "one statement was sent over each of the three tables: the rows of the invocation's runs are"
                        + " read once for each count, not once for each kind",
                () -> assertThat(List.of(ofVerdicts.size(), ofExtractionFaults.size(), ofClusterFaults.size()))
                        .containsExactly(1, 1, 1));
        claim(
                "the statement over the verdicts names every declared kind of verdict in its own text, so"
                        + " the database counts each and what is left is other",
                () -> assertThat(ofVerdicts).allSatisfy(sql -> assertThat(sql).contains(quotedNamesOf(VerdictKind.values()))));
        claim(
                "the statement over the conversion faults names every declared category",
                () -> assertThat(ofExtractionFaults)
                        .allSatisfy(sql -> assertThat(sql).contains(quotedNamesOf(FailureCategory.values()))));
        claim(
                "the statement over the faults of groups names every declared kind",
                () -> assertThat(ofClusterFaults)
                        .allSatisfy(sql -> assertThat(sql).contains(quotedNamesOf(ClusterFaultKind.values()))));
        claim(
                "none of the three reads a column of free text, or a path",
                () -> assertThat(allThree).noneMatch(sql -> FREE_TEXT_OR_A_PATH.matcher(sql).find()));
        claim(
                "against the shipped schema the database plans none of the three through a temporary B-tree:"
                        + " a count that did would sort a row for every verdict or fault of the invocation's runs",
                () -> assertThat(allThree)
                        .allSatisfy(sql -> assertThat(planOf(sql))
                                .as("the plan of: %s", sql)
                                .noneMatch(detail -> detail.contains("TEMP B-TREE"))));
    }

    /**
     * The statement answers one count for each constant by position, and the writer reads them back by
     * position too. A constant moved in an enumeration without the text following would write one
     * constant's count under another's name; one put in without it would make the read of the counts fail
     * and end the counting there, with none of that count. So every constant is given a count no other
     * has, and every constant's count is claimed: either slip fails here.
     */
    @Test
    @Story("A count is held under the name of the kind it counts")
    @DisplayName("With a different number of rows stored for every declared kind, each line carries its own kind's number")
    void eachCountCarriesItsOwnConstantsNumber() {
        for (VerdictKind kind : VerdictKind.values()) {
            for (int row = 0; row < rowsStoredFor(kind); row++) {
                verdict(row % 2 == 0 ? ONE_RUN : ANOTHER_RUN, kind.name());
            }
        }
        for (FailureCategory category : FailureCategory.values()) {
            for (int row = 0; row < rowsStoredFor(category); row++) {
                extractionFault(row % 2 == 0 ? ONE_RUN : ANOTHER_RUN, category.name().toLowerCase(Locale.ROOT));
            }
        }
        for (ClusterFaultKind kind : ClusterFaultKind.values()) {
            for (int row = 0; row < rowsStoredFor(kind); row++) {
                clusterFault(row % 2 == 0 ? ONE_RUN : ANOTHER_RUN, kind.name());
            }
        }

        JsonNode counts = theCountsOfAnInvocationOverBothRuns();

        claim(
                "every declared kind of verdict is counted, and each carries the number stored for that kind,"
                        + " which is its place in the declaration counted from one, so no two kinds share a"
                        + " number and a count held under a neighbour's name would show",
                () -> assertThat(countsOf(counts, VERDICTS)).containsExactlyEntriesOf(theCountsExpectedOf(VerdictKind.values())));
        claim(
                "every declared category of conversion fault is counted, carrying its own number",
                () -> assertThat(countsOf(counts, EXTRACTION_FAULTS))
                        .containsExactlyEntriesOf(theCountsExpectedOf(FailureCategory.values())));
        claim(
                "every declared kind of fault of a group is counted, carrying its own number",
                () -> assertThat(countsOf(counts, CLUSTER_FAULTS))
                        .containsExactlyEntriesOf(theCountsExpectedOf(ClusterFaultKind.values())));
    }

    /** How many rows the test stores for {@code constant}: its place in the declaration, counted from one. */
    private static int rowsStoredFor(Enum<?> constant) {
        return constant.ordinal() + PLACES_ARE_COUNTED_FROM;
    }

    /** The name the report counts a constant under: lower case, with a hyphen for each underscore. */
    private static String nameOf(Enum<?> constant) {
        return constant.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** The names of the constants, in the order declared, and {@code other} after them. */
    private static List<String> namesOf(Enum<?>[] constants) {
        List<String> names = new ArrayList<>(Arrays.stream(constants).map(TheAccountsCountsByKindSortNothingTest::nameOf).toList());
        names.add(OTHER);
        return names;
    }

    /** One count for each constant with the number stored for it, in the order declared, and no row under other. */
    private static Map<String, Long> theCountsExpectedOf(Enum<?>[] constants) {
        Map<String, Long> expected = new LinkedHashMap<>();
        for (Enum<?> constant : constants) {
            expected.put(nameOf(constant), (long) rowsStoredFor(constant));
        }
        expected.put(OTHER, 0L);
        return expected;
    }

    private void verdict(String run, String kind) {
        jdbc.update("INSERT INTO verdict (run_id, kind) VALUES (?, ?)", run, kind);
    }

    private void extractionFault(String run, String category) {
        jdbc.update("INSERT INTO extraction_fault (run_id, category) VALUES (?, ?)", run, category);
    }

    private void clusterFault(String run, String kind) {
        jdbc.update("INSERT INTO cluster_fault (run_id, kind) VALUES (?, ?)", run, kind);
    }

    /**
     * The counts the report holds of an invocation that arrived at {@link #ONE_RUN} and {@link #ANOTHER_RUN}, after
     * the account has handed them over and the invocation has ended.
     */
    private JsonNode theCountsOfAnInvocationOverBothRuns() {
        InvocationReport report = SixJobsForTests.aReport(workingDirectory);
        report.begins(InvocationReport.RUN);
        InvocationAccount account = new InvocationAccount(workingDirectory, null, jdbc, report);
        JobExecution job = new JobExecution(1L, new JobInstance(1L, "vespera"), new JobParameters());
        job.setStatus(BatchStatus.COMPLETED);
        StepExecution step = new StepExecution(StepNames.CENSUS, job);
        step.setStatus(BatchStatus.COMPLETED);
        InvocationRuns runs = new InvocationRuns(job.getExecutionContext());
        runs.record(StageModules.EXTRACTION.stage(), new RunId(ONE_RUN));
        runs.record(StageModules.GENERATION.stage(), new RunId(ANOTHER_RUN));

        account.beforeJob(job);
        account.afterJob(job);
        report.runEnds(0, null, null);

        return TheReportFile.in(workingDirectory).path("counts");
    }

    /** One group of the report's counts, each member in the order the report holds it. */
    private static Map<String, Long> countsOf(JsonNode counts, String group) {
        Map<String, Long> held = new LinkedHashMap<>();
        counts.path(group).properties().forEach(member -> held.put(member.getKey(), member.getValue().asLong()));
        return held;
    }

    /** The members of a group that are not zero, in the order held. */
    private static List<Map.Entry<String, Long>> nonZero(JsonNode counts, String group) {
        return countsOf(counts, group).entrySet().stream().filter(member -> member.getValue() != 0).toList();
    }

    /** The statements the account sent that read {@code table}, and no table whose name only begins so. */
    private List<String> sentOver(String table) {
        Pattern reads = Pattern.compile("\\bFROM " + table + "\\b");
        return log.said().stream().filter(sql -> reads.matcher(sql).find()).toList();
    }

    /** Each constant's name between single quotes, as a statement's text names a stored value. */
    private static String[] quotedNamesOf(Enum<?>[] constants) {
        return Arrays.stream(constants).map(constant -> "'" + constant.name() + "'").toArray(String[]::new);
    }

    /** The plan SQLite gives {@code sql} against the shipped schema, every placeholder bound to a text. */
    private List<String> planOf(String sql) {
        Object[] arguments = Collections.nCopies((int) sql.chars().filter(c -> c == '?').count(), (Object) "x")
                .toArray();
        return new JdbcTemplate(shippedSchema)
                .query("EXPLAIN QUERY PLAN " + sql, (resultSet, rowNumber) -> resultSet.getString("detail"), arguments);
    }
}
