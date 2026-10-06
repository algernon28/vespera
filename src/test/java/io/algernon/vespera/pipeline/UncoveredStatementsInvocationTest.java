package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.extraction.ConverterStopsPartwayBeans;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteConnection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * What a whole invocation says around the statements ADR-193 left unnamed (ADR-199, #429): the three
 * survivor counts that size a counter, two at stage 1 and one at stage 4a, each timed, and the two reads
 * that open stage 2's resume, of the faults a stopped run recorded and of the occurrences its committed
 * chunks measured, each counted.
 *
 * <p>The timed ones write two lines each, wherever the step issues the count: one before, and one after
 * with the seconds it took, and nothing between. Stage 1 counts twice, before its broken check and again
 * before it reads sizes, because the broken check removes survivors between the two (ADR-200 section 3);
 * the second count is issued inside {@code corpus}, and {@code pipeline} writes its lines from outside
 * (ADR-199 section 2). Each counted read writes its two lines only where the run holds a row of the table
 * it reads, so a first invocation, whose run holds none, says nothing about either, and a resume says what
 * each reads and how long it took. The rows here are far fewer than the 100,000 steps at which SQLite calls
 * back, so no progress line falls between either pair, and no handler is left on the connection the pool
 * hands out.
 *
 * <p>Each test walks a folder of its own, so each mints its own runs in the working directory the class
 * shares, and the profile is written afresh before each. What the converter answers is scripted by the
 * order documents are first asked about and never by their names, and the script is reset before each
 * test, not only after it.
 *
 * <p>Every claim about a line failed before ADR-199 was built, by assertion: the line was not there.
 */
@CascadeSliceTest
@Import(ConverterStopsPartwayBeans.class)
@ExtendWith(OutputCaptureExtension.class)
@Epic("Pipeline")
@Feature("Progress reporting")
@Issue("429")
@Link(name = "ADR-199", url = Adr.THE_STATEMENTS_ADR_193_LEFT_UNNAMED_TAKE_ITS_RULE, type = "adr")
class UncoveredStatementsInvocationTest {

    /** What stage 4's gate is opened with, so stage 4a runs. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** Stage 1's two lines around the survivor count that sizes its broken check's counter. */
    private static final String COUNTING_FOR_THE_BROKEN_CHECK =
            "Stage 1 (byte-level reduction) is counting the survivors the broken check goes through";

    private static final String COUNTED_FOR_THE_BROKEN_CHECK =
            "Stage 1 (byte-level reduction) counted the survivors the broken check goes through in ";

    /** The first line of the counter the first of stage 1's counts sizes. */
    private static final String BROKEN_CHECK_PROGRESS = "Stage 1 (byte-level reduction, broken check): ";

    /** Stage 1's two lines around the second count, which sizes the counter of the sizes it reads. */
    private static final String COUNTING_FOR_THE_SIZES =
            "Stage 1 (byte-level reduction) is counting the survivors whose sizes it reads";

    private static final String COUNTED_FOR_THE_SIZES =
            "Stage 1 (byte-level reduction) counted the survivors whose sizes it reads in ";

    /** The first line of the counter the second of stage 1's counts sizes. */
    private static final String SIZES_READ_PROGRESS = "Stage 1 (byte-level reduction, sizes read): ";

    /** What stage 1 says in place of its work where that work is already recorded under its run. */
    private static final String STAGE_ONE_ALREADY_RECORDED = "Stage 1 (byte-level reduction) was already recorded under run";

    /** Stage 4a's two lines around the count that sizes its counter. */
    private static final String COUNTING_FOR_THE_SIGNATURES =
            "Stage 4a (redundancy signatures) is counting the survivors it signs";

    private static final String COUNTED_FOR_THE_SIGNATURES =
            "Stage 4a (redundancy signatures) counted the survivors it signs in ";

    /** The line stage 4a writes for each survivor it has signed. */
    private static final String SIGNED_ONE = "[redundancy-signature] finished";

    /** The seconds a timed line ends on: one decimal place. */
    private static final Pattern SECONDS_TO_ONE_DECIMAL = Pattern.compile(" in \\d+\\.\\d s$");

    /** Stage 2's two lines around the read of the faults a stopped run left. */
    private static final String READING_FAULTS = "Stage 2 (extraction) is reading the faults the stopped run recorded";

    private static final String READ_FAULTS = "Stage 2 (extraction) read the faults the stopped run recorded in ";

    /** Stage 2's two lines around the read of the occurrences a stopped run's committed chunks measured. */
    private static final String READING_MEASURED =
            "Stage 2 (extraction) is reading the occurrences the stopped run measured";

    private static final String READ_MEASURED =
            "Stage 2 (extraction) read the occurrences the stopped run measured in ";

    /** Stage 2's line that it resumes a stopped run. */
    private static final String RESUMES = "Stage 2 (extraction) resumes run";

    /** A progress line of any counted statement. */
    private static final String ABOUT = ": about ";

    /** One chunk of stage 2, and three of them: the stop lands after the first fault and before the end. */
    private static final int CHUNK = ExtractionJobConfiguration.CHUNK_SIZE;

    private static final int CORPUS_SIZE = 3 * CHUNK;
    private static final int ANSWERED_BEFORE_THE_STOP = 2 * CHUNK + CHUNK / 2;

    /** The one position the converter fails on while blaming itself, inside the first chunk. */
    private static final List<Integer> CONVERTER_FAULT_AT = List.of(5);

    private static final List<Integer> NOWHERE = List.of();

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void startClean() {
        ConverterStopsPartwayBeans.script(NOWHERE, NOWHERE, NOWHERE);
        jdbcTemplate.update("DELETE FROM extraction_cache");
        profileStore.save(ProfileFixture.profile()
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .build());
    }

    @AfterEach
    void bringTheConverterBack() {
        ConverterStopsPartwayBeans.keepAnswering();
    }

    @Test
    @Story("The survivor count that sizes a stage's counter says how long it took")
    @DisplayName("Stage 1, twice, and stage 4a each say they are counting the survivors before a counter starts, and how long it took after")
    void theThreeSurvivorCountsSayHowLongTheyTook(CapturedOutput output, @TempDir Path root) throws IOException {
        writeCorpus(root, "counts");

        int before = output.getAll().length();
        cli.run("run", root.toString());
        List<String> lines = List.of(output.getAll().substring(before).split("\\R"));

        claim(
                "stage 1 says before its broken check that it is counting the survivors it will go through, once",
                () -> assertThat(lines).filteredOn(line -> line.contains(COUNTING_FOR_THE_BROKEN_CHECK)).hasSize(1));
        claim(
                "and after it how long that took, once, in seconds to one decimal place",
                () -> assertThat(lines)
                        .filteredOn(line -> line.contains(COUNTED_FOR_THE_BROKEN_CHECK))
                        .singleElement()
                        .asString()
                        .containsPattern(SECONDS_TO_ONE_DECIMAL));
        claim(
                "the two come in that order, before the broken check's own counter writes its first line",
                () -> inThisOrder(lines, COUNTING_FOR_THE_BROKEN_CHECK, COUNTED_FOR_THE_BROKEN_CHECK, BROKEN_CHECK_PROGRESS));
        claim(
                "stage 1 counts again once the broken check has removed what it removes, and says before that"
                        + " count that it is counting the survivors whose sizes it reads, once",
                () -> assertThat(lines).filteredOn(line -> line.contains(COUNTING_FOR_THE_SIZES)).hasSize(1));
        claim(
                "and after it how long that took, once, in seconds to one decimal place",
                () -> assertThat(lines)
                        .filteredOn(line -> line.contains(COUNTED_FOR_THE_SIZES))
                        .singleElement()
                        .asString()
                        .containsPattern(SECONDS_TO_ONE_DECIMAL));
        claim(
                "the second pair comes after the first count's closing line, in order, and before the counter of"
                        + " the sizes read writes its first line",
                () -> inThisOrder(
                        lines,
                        COUNTED_FOR_THE_BROKEN_CHECK,
                        COUNTING_FOR_THE_SIZES,
                        COUNTED_FOR_THE_SIZES,
                        SIZES_READ_PROGRESS));
        claim(
                "stage 4a says the same of the survivors it signs, once before and once after, in order, before"
                        + " it signs the first",
                () -> {
                    assertThat(lines).filteredOn(line -> line.contains(COUNTING_FOR_THE_SIGNATURES)).hasSize(1);
                    assertThat(lines).filteredOn(line -> line.contains(COUNTED_FOR_THE_SIGNATURES)).hasSize(1);
                    inThisOrder(lines, COUNTING_FOR_THE_SIGNATURES, COUNTED_FOR_THE_SIGNATURES, SIGNED_ONE);
                });
        claim(
                "a timed statement says nothing between its two lines: no progress line falls between either of"
                        + " stage 1's counting and counted lines, nor between stage 4a's",
                () -> {
                    assertThat(linesBetween(lines, COUNTING_FOR_THE_BROKEN_CHECK, COUNTED_FOR_THE_BROKEN_CHECK))
                            .noneMatch(line -> line.contains(ABOUT));
                    assertThat(linesBetween(lines, COUNTING_FOR_THE_SIZES, COUNTED_FOR_THE_SIZES))
                            .noneMatch(line -> line.contains(ABOUT) || line.contains(SIZES_READ_PROGRESS));
                    assertThat(linesBetween(lines, COUNTING_FOR_THE_SIGNATURES, COUNTED_FOR_THE_SIGNATURES))
                            .noneMatch(line -> line.contains(ABOUT));
                });
    }

    @Test
    @Story("A read of a stopped run's faults reports how far it has gone")
    @Story("A read of the occurrences a stopped run measured reports how far it has gone")
    @DisplayName("A resumed extraction says it is reading the faults the stopped run recorded and the occurrences it measured, and how long each took, and a first invocation says nothing of either")
    void theResumeReadsSayWhatTheyReadOnlyWhereTheRunHoldsARow(CapturedOutput output, @TempDir Path root)
            throws IOException, SQLException {
        ConverterStopsPartwayBeans.script(NOWHERE, NOWHERE, CONVERTER_FAULT_AT);
        writeCorpus(root, "faults");

        ConverterStopsPartwayBeans.stopAnsweringAfter(ANSWERED_BEFORE_THE_STOP);
        int beforeFirst = output.getAll().length();
        cli.run("run", root.toString());
        List<String> first = List.of(output.getAll().substring(beforeFirst).split("\\R"));
        String run = onlyExtractionRunOf(root);
        long faults = faultRowsUnder(run);
        long measured = metricRowSpanUnder(run);

        claim(
                "the first invocation stops with " + CONVERTER_FAULT_AT.size() + " fault recorded under its run:"
                        + " the converter reached the one it was scripted to fail on before it stopped answering",
                () -> assertThat(faults).isEqualTo(CONVERTER_FAULT_AT.size()));
        claim(
                "and with the occurrences of its committed chunks measured under it, " + measured + " rows from"
                        + " its first to its last",
                () -> assertThat(measured).isPositive());
        claim(
                "a run that held no fault when it began says nothing of reading faults: there was nothing to wait"
                        + " for",
                () -> assertThat(first).noneMatch(line -> line.contains(READING_FAULTS) || line.contains(READ_FAULTS)));
        claim(
                "nor, having measured nothing when it began, of reading the occurrences it measured",
                () -> assertThat(first)
                        .noneMatch(line -> line.contains(READING_MEASURED) || line.contains(READ_MEASURED)));

        jdbcTemplate.update("DELETE FROM extraction_cache");
        ConverterStopsPartwayBeans.keepAnswering();
        int beforeSecond = output.getAll().length();
        cli.run("run", root.toString());
        List<String> second = List.of(output.getAll().substring(beforeSecond).split("\\R"));

        claim(
                "the resumed invocation says, once, that it is reading the faults the stopped run recorded, over up"
                        + " to the " + faults + " the run holds",
                () -> assertThat(second)
                        .filteredOn(line -> line.contains(READING_FAULTS))
                        .singleElement()
                        .asString()
                        .endsWith(", over up to " + faults + " rows"));
        claim(
                "and, once, how long that took, in seconds to one decimal place",
                () -> assertThat(second)
                        .filteredOn(line -> line.contains(READ_FAULTS))
                        .singleElement()
                        .asString()
                        .containsPattern(SECONDS_TO_ONE_DECIMAL));
        claim(
                "so few rows take far fewer than the steps at which SQLite calls back, and the read writes no"
                        + " progress line between its two",
                () -> assertThat(linesBetween(second, READING_FAULTS, READ_FAULTS))
                        .noneMatch(line -> line.contains(ABOUT)));
        claim(
                "the resumed invocation says, once, that it is reading the occurrences the stopped run measured,"
                        + " over up to the " + measured + " rows from the run's first to its last",
                () -> assertThat(second)
                        .filteredOn(line -> line.contains(READING_MEASURED))
                        .singleElement()
                        .asString()
                        .endsWith(", over up to " + measured + " rows"));
        claim(
                "and, once, how long that took, in seconds to one decimal place",
                () -> assertThat(second)
                        .filteredOn(line -> line.contains(READ_MEASURED))
                        .singleElement()
                        .asString()
                        .containsPattern(SECONDS_TO_ONE_DECIMAL));
        claim(
                "so few rows write no progress line between them either",
                () -> assertThat(linesBetween(second, READING_MEASURED, READ_MEASURED))
                        .noneMatch(line -> line.contains(ABOUT)));
        claim(
                "the four lines come in the order the stage does the work: the faults read, then the measured"
                        + " occurrences read, and only then the line that says the stage resumes",
                () -> inThisOrder(second, READING_FAULTS, READ_FAULTS, READING_MEASURED, READ_MEASURED, RESUMES));
        claim(
                "stage 1 finished in the first invocation, so the resumed one says it was already recorded and"
                        + " does not count its survivors again: a count that is not made writes neither of its"
                        + " lines, for the broken check or for the sizes",
                () -> {
                    assertThat(second).anyMatch(line -> line.contains(STAGE_ONE_ALREADY_RECORDED));
                    assertThat(second)
                            .noneMatch(line -> line.contains(COUNTING_FOR_THE_BROKEN_CHECK)
                                    || line.contains(COUNTED_FOR_THE_BROKEN_CHECK)
                                    || line.contains(COUNTING_FOR_THE_SIZES)
                                    || line.contains(COUNTED_FOR_THE_SIZES));
                });
        claim(
                "and the handler that counted the reads is cleared from the connection afterwards",
                () -> assertThat(aHandlerIsLeftOnTheConnection()).isFalse());
    }

    private boolean aHandlerIsLeftOnTheConnection() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            Object database = connection.unwrap(SQLiteConnection.class).getDatabase();
            Method handler = database.getClass().getDeclaredMethod("getProgressHandler");
            handler.setAccessible(true);
            return ((Long) handler.invoke(database)) != 0L;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("the driver no longer says whether a progress handler is set", e);
        }
    }

    private static int indexOf(List<String> lines, String fragment) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(fragment)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Holds the first line carrying each of {@code fragments} to be there, and each to come after the one
     * before it. Called from inside a claim, which names what the order means.
     */
    private static void inThisOrder(List<String> lines, String... fragments) {
        int previous = -1;
        String previousFragment = "the start of what the invocation wrote";
        for (String fragment : fragments) {
            int at = indexOf(lines, fragment);
            assertThat(at).as("the line holding \"%s\"", fragment).isNotNegative();
            assertThat(at).as("the line holding \"%s\", after \"%s\"", fragment, previousFragment).isGreaterThan(previous);
            previous = at;
            previousFragment = fragment;
        }
    }

    /**
     * The lines strictly between the first line holding {@code opening} and the first holding {@code closing},
     * failing the claim it is called from where either is missing or they come the wrong way round.
     */
    private static List<String> linesBetween(List<String> lines, String opening, String closing) {
        int from = indexOf(lines, opening);
        int to = indexOf(lines, closing);
        assertThat(from).as("the line holding \"%s\"", opening).isNotNegative();
        assertThat(to).as("the line holding \"%s\", after \"%s\"", closing, opening).isGreaterThan(from);
        return lines.subList(from + 1, to);
    }

    private static void writeCorpus(Path root, String salt) throws IOException {
        for (int position = 1; position <= CORPUS_SIZE; position++) {
            Files.writeString(
                    root.resolve(String.format("document-%02d.txt", position)),
                    "Document " + position + " of the " + salt + " corpus describes harbour cranes, tide tables"
                            + " and the order in which ships were unloaded during the winter of year " + position
                            + ", with notes on weather and cargo.");
        }
    }

    private String onlyExtractionRunOf(Path root) {
        List<String> runs = jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id WHERE w.root = ? AND r.stage = ?"
                        + " ORDER BY w.id, r.rowid",
                String.class,
                Walk.canonicalRoot(root).toString(),
                StageModules.EXTRACTION.stage());
        return runs.getLast();
    }

    private long faultRowsUnder(String run) {
        Long rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM extraction_fault WHERE run_id = ?", Long.class, run);
        return rows == null ? 0 : rows;
    }

    /** The span of {@code run}'s rowids in {@code extraction_metric}, as stage 2 bounds its read: zero where none. */
    private long metricRowSpanUnder(String run) {
        Long span = jdbcTemplate.queryForObject(
                "SELECT MAX(rowid) - MIN(rowid) + 1 FROM extraction_metric WHERE run_id = ?", Long.class, run);
        return span == null ? 0 : span;
    }
}
