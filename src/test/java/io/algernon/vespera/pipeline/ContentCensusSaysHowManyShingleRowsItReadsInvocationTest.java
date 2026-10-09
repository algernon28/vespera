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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * What stage 3 says before it reads one stage-2 run's shingle rows, and when it has measured them
 * (ADR-191, #410).
 *
 * <p>Stage 3 wrote {@code starting under run}, read every shingle row of stage 2's run in one statement,
 * and wrote {@code measured shingle document frequency}. On 2026-10-04 that statement took half an hour on
 * a 16.7 GB database on a USB spinning disk, with nothing in the log while it ran, and the invocation could
 * not be told from one that had stopped moving. ADR-191 section 1 has stage 3 say, before the read, how
 * many rows it is about to read at most and that stopping loses only the time spent, and has the line it
 * already wrote after the measurement state how long that took. Where the run holds no shingle rows, or
 * stage 3 is already recorded and so reads nothing, it says nothing about reading.
 *
 * <p><b>The number is the run's own, not the table's.</b> ADR-187's two announcements state {@code
 * MAX(rowid)} of the whole table, because an index build or removal is over every run's rows. This read is
 * over one run's, so the line states the span of that run's rowids, greatest less least plus one, which
 * SQLite answers in two seeks of {@code shingle_by_run_id} (ADR-191 section 2). The first test puts an
 * earlier run's rows in the table first, so that a line stating the table's greatest rowid does not pass.
 *
 * <p>Each test drives whole invocations over {@link ConverterStopsPartwayBeans}, which converts every
 * occurrence into its own bytes, so stage 2 writes shingles a count of which says something. This
 * context's database is shared between the tests, and every claim about rows names the run it is about.
 *
 * <p>The first test is the one that failed before this was built: stage 3 read the rows and said nothing
 * first. The other two passed then and have to go on passing: they are what stops an implementation from
 * announcing a read that is not made.
 *
 * <p>The number comes from {@code similarity}, which owns the table: stage 3 asks {@code
 * DocumentFrequency.shingleRowsUpTo} for it and writes the line (ADR-191 section 3). What that method
 * answers is pinned beside it, in {@code DocumentFrequencyTest}; what is pinned here is the line.
 */
@CascadeSliceTest
@Import(ConverterStopsPartwayBeans.class)
@ExtendWith(OutputCaptureExtension.class)
@Epic("Pipeline")
@Feature("Progress reporting")
@Issue("410")
@Link(name = "ADR-191", url = Adr.STAGE_3_SAYS_HOW_MANY_SHINGLE_ROWS_IT_IS_ABOUT_TO_READ, type = "adr")
class ContentCensusSaysHowManyShingleRowsItReadsInvocationTest {

    /** How many occurrences the corpus of each test holds before anything is added to it. */
    private static final int CORPUS_SIZE = 3;

    /** How many stage-2 runs the first test leaves over its corpus: one for each of its two invocations. */
    private static final int TWO_RUNS = 2;

    /** How many stage-2 runs the third test leaves over its corpus: one for its one invocation. */
    private static final int ONE_RUN = 1;

    /** The line stage 3 writes when it has work to do. */
    private static final String STARTING = "Stage 3 (content census) starting under run ";

    /** The line stage 3 writes when its work is already recorded under its run. */
    private static final String ALREADY_RECORDED = "Stage 3 (content census) was already recorded under run ";

    /** The start of the line stage 3 writes before it reads; the greatest number of rows it may read follows. */
    private static final String READING = "Stage 3 (content census) is reading up to ";

    /** What follows that number in the same line. */
    private static final String SHINGLE_ROWS = " shingle rows";

    /** What the line before the read says about stopping part-way. */
    private static final String STOPPING_LOSES_ONLY_THE_TIME = "stopping before it ends loses only the time spent";

    /** What ADR-211 section 9 has that line say of the read since the database groups the rows itself. */
    private static final String IN_ONE_STATEMENT = "the database reads them, sorts them in temporary files in the"
            + " working directory and counts them, in one statement";

    /** What it says of that statement's progress lines, which state a floor (ADR-211 section 9). */
    private static final String THE_LEAST_DONE_AND_SHORT_OF_THE_WHOLE =
            "whose progress lines state the least it has done and stop short of 100%";

    /** What it now says the half hour on record was: the reading, which is the first part of that statement. */
    private static final String READING_ALONE_TOOK_HALF_AN_HOUR = "reading them alone took half an hour";

    /** What ADR-191 had it say of the one read the application made itself, which is no longer made. */
    private static final String A_PAGE_AT_A_TIME = "SQLite reads them a page at a time";

    /** The line stage 3 has always written once the document frequency is measured. */
    private static final String MEASURED = "Stage 3 (content census) measured shingle document frequency";

    /** That line with the time the measurement took: seconds to one decimal place. */
    private static final String MEASURED_IN_SECONDS_TO_ONE_DECIMAL =
            "Stage 3 \\(content census\\) measured shingle document frequency in \\d+\\.\\d s";

    /** Nothing at all. */
    private static final long NONE = 0;

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

    /**
     * The converter forgets every scripted and pinned outcome, answers, and counts from zero, and the
     * profile is the one no test has written to. The script, the pins and the count are static and shared
     * with every other class importing the converter, and class order differs between machines.
     */
    @BeforeEach
    void startClean() {
        ConverterStopsPartwayBeans.script(List.of(), List.of(), List.of());
        ConverterStopsPartwayBeans.keepAnswering();
        profileStore.save(ProfileFixture.profile().build());
    }

    @AfterEach
    void bringTheConverterBack() {
        ConverterStopsPartwayBeans.keepAnswering();
    }

    /**
     * An occurrence added between the invocations makes the second a different observation, hence a
     * different walk, a different stage-2 run and a different stage-3 run (ADR-115), so the second stage 3
     * has rows to read, and they are not the first rows in the table.
     */
    @Test
    @Story("A long wait inside the database is announced")
    @DisplayName("A content census with saved word sequences to read says how many at most before it reads them, and how long the measurement took")
    @Link(name = "ADR-211", url = Adr.NO_CLASS_HOLDS_EVERY_SURVIVOR_OF_A_RUN, type = "adr")
    void aStageThreeWithShingleRowsToReadSaysHowManyAtMostBeforeItReadsThem(
            CapturedOutput output, @TempDir Path root) throws IOException {
        writeCorpus(root, "read announced");
        cli.run("run", root.toString());

        Files.writeString(
                root.resolve("99-added-later.txt"),
                "A later addition about harbour pilots, tide tables and the order in which the buoys were"
                        + " repainted each spring before the first sailing.");
        int before = output.getAll().length();
        cli.run("run", root.toString());
        String second = output.getAll().substring(before);

        List<String> extractionRuns = runsOf(root, StageModules.EXTRACTION);
        claim(
                "the second invocation extracts under a run of its own, because the corpus it reads is not the"
                        + " one the first read, so the table holds the rows of " + TWO_RUNS + " extractions",
                () -> assertThat(extractionRuns).hasSize(TWO_RUNS).doesNotHaveDuplicates());

        String secondRun = extractionRuns.getLast();
        long span = rowSpanOf(secondRun);
        claim(
                "the second extraction saved word sequences, so the content census after it has rows to read",
                () -> assertThat(span).isGreaterThan(NONE));
        claim(
                "its rows were written one after another with no other run's between them, so the span of its"
                        + " row numbers is exactly how many rows it holds",
                () -> assertThat(span).isEqualTo(rowsOf(secondRun)));
        claim(
                "and that is fewer than the greatest row number in the table, which counts the first"
                        + " extraction's rows too, so a line stating the table's size is told apart from one"
                        + " stating this extraction's",
                () -> assertThat(span).isLessThan(greatestShingleRow()));

        claim(
                "the second content census starts and measures, which are the two lines the read falls between",
                () -> assertThat(second).containsSubsequence(STARTING, MEASURED));
        claim(
                "between them it says that it is reading the saved word sequences, so a read lasting half an"
                        + " hour is not mistaken for an invocation that has stopped moving",
                () -> assertThat(second).containsSubsequence(STARTING, READING, MEASURED));
        claim(
                "that line says it reads up to " + span + " rows, the span of the row numbers the extraction"
                        + " it reads wrote",
                () -> assertThat(second).contains(READING + span + SHINGLE_ROWS));
        claim("and it says so once", () -> assertThat(second).containsOnlyOnce(READING));
        claim(
                "the line before the read says that stopping before it ends loses only the time spent, and the"
                        + " line after the measurement gives the time it took in seconds, to one decimal place",
                () -> assertThat(second)
                        .contains(STOPPING_LOSES_ONLY_THE_TIME)
                        .containsPattern(MEASURED_IN_SECONDS_TO_ONE_DECIMAL));
        String theLine = second.lines().filter(line -> line.contains(READING)).findFirst().orElse("");
        claim(
                "since the database groups the rows itself, the line says it reads them, sorts them in temporary"
                        + " files in the working directory and counts them in one statement, that the progress"
                        + " lines to come state the least done and stop short of the whole, and that the half"
                        + " hour on record was the reading alone; it no longer says the rows are read a page at"
                        + " a time, which described a read the application made itself",
                () -> assertThat(theLine)
                        .contains(IN_ONE_STATEMENT)
                        .contains(THE_LEAST_DONE_AND_SHORT_OF_THE_WHOLE)
                        .contains(READING_ALONE_TOOK_HALF_AN_HOUR)
                        .doesNotContain(A_PAGE_AT_A_TIME));
    }

    @Test
    @Story("A long wait inside the database is announced")
    @DisplayName("A content census whose measurement is already recorded says nothing about reading")
    void aStageThreeAlreadyRecordedSaysNothingAboutReading(CapturedOutput output, @TempDir Path root)
            throws IOException {
        writeCorpus(root, "already recorded and nothing read");
        cli.run("run", root.toString());

        int before = output.getAll().length();
        cli.run("run", root.toString());
        String second = output.getAll().substring(before);

        claim(
                "the second invocation finds the content census already recorded, so it reads nothing",
                () -> assertThat(second).contains(ALREADY_RECORDED).doesNotContain(STARTING));
        claim(
                "and says nothing about reading or about having measured",
                () -> assertThat(second).doesNotContain(READING).doesNotContain(MEASURED));
    }

    /**
     * A text of fewer words than one shingle spans yields none, so stage 2 writes no row for it. That is
     * the case of a run with nothing for stage 3 to read: it has no first row and no last, and nothing to
     * wait for.
     */
    @Test
    @Story("A long wait inside the database is announced")
    @DisplayName("A content census over an extraction that saved no word sequences says nothing about reading them")
    void aStageThreeOverARunWithNoShingleRowsSaysNothingAboutReading(CapturedOutput output, @TempDir Path root)
            throws IOException {
        for (int position = 1; position <= CORPUS_SIZE; position++) {
            Files.writeString(root.resolve(String.format("%02d-brief.txt", position)), "Memo " + position + " brief");
        }

        int before = output.getAll().length();
        cli.run("run", root.toString());
        String invocation = output.getAll().substring(before);

        List<String> extractionRuns = runsOf(root, StageModules.EXTRACTION);
        claim("the invocation extracts under one run", () -> assertThat(extractionRuns).hasSize(ONE_RUN));
        claim(
                "that extraction saved no word sequences, because each text is shorter than one sequence",
                () -> assertThat(rowsOf(extractionRuns.getFirst())).isEqualTo(NONE));
        claim(
                "the content census starts and measures all the same",
                () -> assertThat(invocation).containsSubsequence(STARTING, MEASURED));
        claim(
                "and says nothing about reading rows, because there are none to wait for",
                () -> assertThat(invocation).doesNotContain(READING));
    }

    /**
     * Writes {@link #CORPUS_SIZE} texts whose names sort in position order, each with bytes of its own, so
     * stage 1 removes none as a copy of another, and each long enough to shingle. {@code salt} keeps one
     * test's corpus from sharing content with another's.
     */
    private static void writeCorpus(Path root, String salt) throws IOException {
        for (int position = 1; position <= CORPUS_SIZE; position++) {
            Files.writeString(
                    root.resolve(String.format("%02d-plain.txt", position)),
                    "Entry " + position + " of the " + salt + " corpus describes lighthouse keepers, lamp oil"
                            + " and the order in which the lamps were trimmed during the autumn of year " + position
                            + ", with notes on fog and shipping.");
        }
    }

    /** Every run of {@code stage} over {@code root}, oldest walk first. */
    private List<String> runsOf(Path root, StageModules stage) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE w.root = ? AND r.stage = ? ORDER BY w.id",
                String.class,
                Walk.canonicalRoot(root).toString(),
                stage.stage());
    }

    /**
     * The span of the rowids {@code run} wrote in {@code shingle}: greatest less least, plus one, and zero
     * where it wrote none. The bound the line states (ADR-191 section 2).
     */
    private long rowSpanOf(String run) {
        Long least = jdbcTemplate.queryForObject("SELECT MIN(rowid) FROM shingle WHERE run_id = ?", Long.class, run);
        Long greatest =
                jdbcTemplate.queryForObject("SELECT MAX(rowid) FROM shingle WHERE run_id = ?", Long.class, run);
        return least == null || greatest == null ? NONE : greatest - least + 1;
    }

    /** How many rows {@code run} holds in {@code shingle}, counted. */
    private long rowsOf(String run) {
        Long rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM shingle WHERE run_id = ?", Long.class, run);
        return rows == null ? NONE : rows;
    }

    /** The greatest rowid in {@code shingle}, over every run's rows. */
    private long greatestShingleRow() {
        Long greatest = jdbcTemplate.queryForObject("SELECT MAX(rowid) FROM shingle", Long.class);
        return greatest == null ? NONE : greatest;
    }
}
