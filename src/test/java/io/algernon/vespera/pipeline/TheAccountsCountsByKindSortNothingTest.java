package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.StatementLog;
import io.algernon.vespera.extraction.FailureCategory;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.VerdictKind;
import io.algernon.vespera.synthesis.ClusterFaultKind;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
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

/**
 * The invocation account's three counts by a closed enumeration, the verdicts by kind, the extraction
 * faults by category and the cluster faults by kind, write the lines they wrote and keep nothing in
 * temporary storage (ADR-224 section 1): each is one statement that selects counts and no column, names
 * every constant of its enumeration in its own text, and gives {@code other} as the total less the counts
 * it names.
 *
 * <p>Before ADR-224 is built each of the three is a {@code GROUP BY} over the rows of the invocation's
 * runs, which sorts them (ADR-218, rows 19 and 20). The claims on the lines pass against that code and have
 * to go on passing, so that nothing a reader of the account sees changes. The claims that a statement names
 * every constant, and that it is planned without a temporary B-tree, fail against it.
 *
 * <p>The writer is fed by hand, as in {@code InvocationAccountTest}: an in-memory database holding only the
 * columns the counts read. The statements it sends are then planned against a second database, the shipped
 * {@code schema.sql} with no row, where the indexes are the shipped ones.
 *
 * <p><b>Not held here.</b> That a stored value differing from a constant's name by a character outside
 * ASCII is counted under {@code other}, where Java's comparison counted it with its kind: no writer stores
 * one. No size, and no plan over rows.
 */
@Epic("Pipeline")
@Feature("The invocation account")
@Issue("477")
@Link(name = "ADR-224", url = Adr.THE_ACCOUNTS_COUNTS_AND_THE_EMBEDDER_IDENTITY_READS_SORT_NOTHING, type = "adr")
@Link(name = "ADR-198", url = Adr.EVERY_INVOCATION_WRITES_AN_ACCOUNT_THAT_NAMES_NO_DOCUMENT, type = "adr")
class TheAccountsCountsByKindSortNothingTest {

    private static final Instant NOW = Instant.parse("2026-10-10T10:00:00Z");

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

    /** What opens every count line by verdict kind, after the line's instant. */
    private static final String VERDICT_LINE = "counts verdict kind=";

    /** What opens every count line by extraction-fault category. */
    private static final String EXTRACTION_FAULT_LINE = "counts extraction-fault category=";

    /** What opens every count line by cluster-fault kind. */
    private static final String CLUSTER_FAULT_LINE = "counts cluster-fault kind=";

    /** A column or a word a count must never read: free text about an occurrence, or where it is. */
    private static final Pattern FREE_TEXT_OR_A_PATH = Pattern.compile("\\b(detail|reason|path|label|title)\\b");

    @TempDir
    Path workingDirectory;

    @TempDir
    Path accountDirectory;

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
    @Story("A count is written for each kind that has a row, and for no other")
    @DisplayName("A kind no row carries writes no line, and nothing is written as other when every stored value is known")
    void aKindNoRowCarriesWritesNoLine() throws IOException {
        verdict(ONE_RUN, "BROKEN");
        verdict(ANOTHER_RUN, "BROKEN");
        verdict(ANOTHER_RUN, "PASSED");
        extractionFault(ONE_RUN, "timeout");
        clusterFault(ANOTHER_RUN, "SCHEMA_VIOLATION");

        List<String> lines = theAccountOfAnInvocationOverBothRuns();

        claim(
                "the verdicts are counted over both runs under the two kinds stored, two broken and one passed,"
                        + " and no line is written for any kind no row carries, or for other",
                () -> assertThat(linesOpening(lines, VERDICT_LINE))
                        .containsExactly(VERDICT_LINE + "BROKEN count=2", VERDICT_LINE + "PASSED count=1"));
        claim(
                "the one conversion fault stored is the only category written",
                () -> assertThat(linesOpening(lines, EXTRACTION_FAULT_LINE))
                        .containsExactly(EXTRACTION_FAULT_LINE + "timeout count=1"));
        claim(
                "the one fault of a group that could not be written is the only kind written",
                () -> assertThat(linesOpening(lines, CLUSTER_FAULT_LINE))
                        .containsExactly(CLUSTER_FAULT_LINE + "SCHEMA_VIOLATION count=1"));
    }

    @Test
    @Story("A count is written for each kind that has a row, and for no other")
    @DisplayName("With no row at all under the invocation's runs, no count by kind is written")
    void noRowWritesNoLine() throws IOException {
        List<String> lines = theAccountOfAnInvocationOverBothRuns();

        claim(
                "no line counts a verdict, a conversion fault or a fault of a group: a count of nothing is"
                        + " not written as a zero",
                () -> assertThat(lines)
                        .noneMatch(line -> line.startsWith(VERDICT_LINE)
                                || line.startsWith(EXTRACTION_FAULT_LINE)
                                || line.startsWith(CLUSTER_FAULT_LINE)));
    }

    @Test
    @Story("A stored value outside a closed vocabulary is never written")
    @DisplayName("A value that is no declared kind is counted as other, and one that differs only in letter case is counted with its kind")
    void anUndeclaredValueIsOtherAndLetterCaseIsNotADifference() throws IOException {
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

        List<String> lines = theAccountOfAnInvocationOverBothRuns();

        claim(
                "the three verdicts stored as broken in three letter cases are one count of three, and the two"
                        + " whose value nobody declared are counted as other",
                () -> assertThat(linesOpening(lines, VERDICT_LINE))
                        .containsExactly(VERDICT_LINE + "BROKEN count=3", VERDICT_LINE + "other count=2"));
        claim(
                "the two conversion faults stored as timeout in two letter cases are one count of two, written"
                        + " in lower case, and the undeclared one is other",
                () -> assertThat(linesOpening(lines, EXTRACTION_FAULT_LINE))
                        .containsExactly(EXTRACTION_FAULT_LINE + "other count=1", EXTRACTION_FAULT_LINE + "timeout count=2"));
        claim(
                "the fault of a group stored in lower case is counted under its kind's own name, and the"
                        + " undeclared one is other",
                () -> assertThat(linesOpening(lines, CLUSTER_FAULT_LINE))
                        .containsExactly(CLUSTER_FAULT_LINE + "SCHEMA_VIOLATION count=1", CLUSTER_FAULT_LINE + "other count=1"));
        claim(
                "the undeclared value's own text is nowhere in the account",
                () -> assertThat(String.join("\n", lines)).doesNotContain(NOT_IN_ANY_ENUMERATION));
    }

    @Test
    @Story("The count lines come in one order")
    @DisplayName("The lines of each count come in ascending order of the name written, other among them")
    void theLinesComeInAscendingOrderOfTheNameWritten() throws IOException {
        for (String kind : List.of("PASSED", NOT_IN_ANY_ENUMERATION, "EXTRACTION_FAILED", "BROKEN")) {
            verdict(ONE_RUN, kind);
        }
        for (String category : List.of("unknown", "policy", NOT_IN_ANY_ENUMERATION, "internal", "backend_failure")) {
            extractionFault(ANOTHER_RUN, category);
        }

        List<String> lines = theAccountOfAnInvocationOverBothRuns();

        claim(
                "the verdict kinds are written in the order of their names, whatever order the rows were"
                        + " stored in, and other comes last because a small letter sorts after a capital",
                () -> assertThat(linesOpening(lines, VERDICT_LINE))
                        .containsExactly(
                                VERDICT_LINE + "BROKEN count=1",
                                VERDICT_LINE + "EXTRACTION_FAILED count=1",
                                VERDICT_LINE + "PASSED count=1",
                                VERDICT_LINE + "other count=1"));
        claim(
                "the conversion faults' categories, written in lower case, come in the order of their names,"
                        + " and other falls among them where its own name puts it",
                () -> assertThat(linesOpening(lines, EXTRACTION_FAULT_LINE))
                        .containsExactly(
                                EXTRACTION_FAULT_LINE + "backend_failure count=1",
                                EXTRACTION_FAULT_LINE + "internal count=1",
                                EXTRACTION_FAULT_LINE + "other count=1",
                                EXTRACTION_FAULT_LINE + "policy count=1",
                                EXTRACTION_FAULT_LINE + "unknown count=1"));
    }

    @Test
    @Story("A count by kind reads its rows once and sorts none of them")
    @DisplayName("Each of the three counts is one statement that names every declared kind, reads no free text, and keeps no row in temporary storage")
    void eachCountIsOneStatementNamingEveryConstantAndSortingNothing() throws IOException {
        verdict(ONE_RUN, "BROKEN");
        extractionFault(ONE_RUN, "timeout");
        clusterFault(ANOTHER_RUN, "SCHEMA_VIOLATION");
        log.clear();

        theAccountOfAnInvocationOverBothRuns();
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
     * and end the account there, with no line of that count. So every constant is given a count no other
     * has, and every constant's line is claimed: either slip fails here.
     */
    @Test
    @Story("A count is written under the name of the kind it counts")
    @DisplayName("With a different number of rows stored for every declared kind, each line carries its own kind's number")
    void eachLineCarriesItsOwnConstantsCount() throws IOException {
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

        List<String> lines = theAccountOfAnInvocationOverBothRuns();

        claim(
                "every declared kind of verdict has a line, and each carries the number stored for that kind,"
                        + " which is its place in the declaration counted from one, so no two kinds share a"
                        + " number and a count written under a neighbour's name would show",
                () -> assertThat(linesOpening(lines, VERDICT_LINE))
                        .containsExactlyElementsOf(theLinesExpectedOf(VERDICT_LINE, VerdictKind.values(), false)));
        claim(
                "every declared category of conversion fault has a line, in lower case, carrying its own number",
                () -> assertThat(linesOpening(lines, EXTRACTION_FAULT_LINE))
                        .containsExactlyElementsOf(theLinesExpectedOf(EXTRACTION_FAULT_LINE, FailureCategory.values(), true)));
        claim(
                "every declared kind of fault of a group has a line carrying its own number",
                () -> assertThat(linesOpening(lines, CLUSTER_FAULT_LINE))
                        .containsExactlyElementsOf(theLinesExpectedOf(CLUSTER_FAULT_LINE, ClusterFaultKind.values(), false)));
    }

    /** How many rows the test stores for {@code constant}: its place in the declaration, counted from one. */
    private static int rowsStoredFor(Enum<?> constant) {
        return constant.ordinal() + PLACES_ARE_COUNTED_FROM;
    }

    /** One line for each constant with the number stored for it, in ascending order of the name written. */
    private static List<String> theLinesExpectedOf(String opening, Enum<?>[] constants, boolean lowerCase) {
        return Arrays.stream(constants)
                .map(constant -> opening
                        + (lowerCase ? constant.name().toLowerCase(Locale.ROOT) : constant.name())
                        + " count=" + rowsStoredFor(constant))
                .sorted()
                .toList();
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
     * Writes the account of an invocation that arrived at {@link #ONE_RUN} and {@link #ANOTHER_RUN}.
     *
     * @return the account's lines, each without the instant it opens with
     */
    private List<String> theAccountOfAnInvocationOverBothRuns() throws IOException {
        InvocationAccount account =
                new InvocationAccount(workingDirectory, accountDirectory, jdbc, Clock.fixed(NOW, ZoneOffset.UTC));
        JobExecution job = new JobExecution(1L, new JobInstance(1L, "vespera"), new JobParameters());
        job.setStatus(BatchStatus.COMPLETED);
        StepExecution step = new StepExecution(StepNames.CENSUS, job);
        step.setStatus(BatchStatus.COMPLETED);
        InvocationRuns runs = new InvocationRuns(job.getExecutionContext());
        runs.record(StageModules.EXTRACTION.stage(), new RunId(ONE_RUN));
        runs.record(StageModules.GENERATION.stage(), new RunId(ANOTHER_RUN));

        account.beforeJob(job);
        account.afterJob(job);

        List<Path> written;
        try (Stream<Path> files = Files.list(accountDirectory)) {
            written = files.toList();
        }
        claim("exactly one account was written", () -> assertThat(written).hasSize(1));
        return Files.readAllLines(written.getFirst()).stream()
                .map(line -> line.substring(line.indexOf(' ') + 1))
                .toList();
    }

    private static List<String> linesOpening(List<String> lines, String opening) {
        return lines.stream().filter(line -> line.startsWith(opening)).toList();
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
