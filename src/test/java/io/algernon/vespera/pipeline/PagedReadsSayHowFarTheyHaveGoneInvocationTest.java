package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * What a whole invocation says while stage 3 and stage 5b read a page of survivors at a time, over a
 * collection of more than one page (ADR-211 sections 3, 4 and 9).
 *
 * <p>Three kinds of line that no smaller collection writes. Stage 3's running count of the files it checks
 * against the ledger says nothing before its thousandth. A read of extraction metrics made a page at a time
 * is told its rows after each page, so over 1,100 rows it writes two progress lines, one a page. And stage 5b
 * reads the collection's metrics a second time only where a signal has more than 1,000 values that are not
 * all one, which takes more than a thousand documents of different lengths.
 *
 * <p>The first test is {@code StageOneReportsItsLoopsInvocationTest}'s collection: a thousand and two copies
 * of one file, of which stage 1 keeps one. The copies stay in the walk, and stage 3 checks every file of the
 * walk, so its count passes a thousand while only one document is converted.
 *
 * <p>The second is 1,100 texts of made-up words, between 30 and 199 words each, and one seed. Every word has
 * a vowel and more than one letter, so of the comparison's four signals only the word count varies. Stage 4's
 * gate is open and no embedding model is named, so the invocation ends at the model gate, after the
 * comparison.
 *
 * <p><b>Red until ADR-211 is built</b>, at the claims about the lines. The claims before them, that the
 * stages ran over the collection described, pass at {@code 4b99a03}.
 */
@CascadeSliceTest
@Import(ConverterStopsPartwayBeans.class)
@Epic("Pipeline")
@Feature("Progress reporting")
@Issue("456")
@Link(name = "ADR-211", url = Adr.NO_CLASS_HOLDS_EVERY_SURVIVOR_OF_A_RUN, type = "adr")
@Link(name = "ADR-193", url = Adr.STATEMENTS_REPORT_THEIR_PROGRESS, type = "adr")
@Link(name = "ADR-192", url = Adr.EVERY_LOOP_REPORTS_ITS_PROGRESS, type = "adr")
class PagedReadsSayHowFarTheyHaveGoneInvocationTest {

    /** One original and 1,001 copies: the walk holds 1,002 files, so the thousandth checked is the one line. */
    private static final int COPIES = 1_002;

    /** One full page of the ledger's 1,000 and a short second. */
    private static final int TEXTS = 1_100;

    /** The fewest words a text holds, and how many different lengths there are above it. */
    private static final int FEWEST_WORDS = 30;

    private static final int DIFFERENT_LENGTHS = 170;

    /** What stage 4's gate is opened with, so the comparison is reached. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    private static final String STAGE_THREE_FINISHED = "Stage 3 (content census) finished under run ";
    private static final String STAGE_FIVE_B = "Stage 5b (seed/corpus comparison)";
    private static final String CORPUS_METRICS = "the corpus survivors' extraction metrics";
    private static final String CORPUS_METRICS_AGAIN = CORPUS_METRICS + " again";

    private static final String OCCURRENCES_CHECKED = "Stage 3 (content census, files checked)";
    private static final String READING_EXTRACTION_METRICS = "Stage 3 (content census, reading extraction metrics)";
    private static final String READING_CORPUS_METRICS = "Stage 5b (seed/corpus comparison, reading corpus metrics)";
    private static final String READING_CORPUS_METRICS_AGAIN =
            "Stage 5b (seed/corpus comparison, reading corpus metrics again)";

    /** After the first page of 1,000 of the 1,100 rows, and after the second. */
    private static final List<String> NINETY_THEN_A_HUNDRED_PERCENT =
            List.of(": about 90% of 1,100 rows", ": about 100% of 1,100 rows");

    /** The most times the comparison reads the collection's metrics again: four reads in all. */
    private static final int AT_MOST_THREE_TIMES_AGAIN = 3;

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

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger applicationLogger;

    /** The converter's script is static and shared with every class importing it, so it is reset before as well as after. */
    @BeforeEach
    void startClean() {
        ConverterStopsPartwayBeans.script(List.of(), List.of(), List.of());
        ConverterStopsPartwayBeans.keepAnswering();
        profileStore.save(ProfileFixture.profile().build());
        logged = new ListAppender<>();
        logged.start();
        applicationLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("io.algernon.vespera");
        applicationLogger.addAppender(logged);
    }

    @AfterEach
    void bringTheConverterBack() {
        ConverterStopsPartwayBeans.keepAnswering();
        applicationLogger.detachAppender(logged);
        logged.stop();
    }

    @Test
    @Story("Stage 3 says how many files it has checked")
    @DisplayName("Checking a thousand and two files against the ledger, the content census says so at the thousandth")
    void theContentCensusCountsTheOccurrencesItChecks(@TempDir Path root) throws IOException {
        for (int copy = 0; copy < COPIES; copy++) {
            Files.writeString(
                    root.resolve(String.format("copy-%04d.txt", copy)),
                    "the same bytes in every copy, about lock keepers, sluice gates and the order of greasing");
        }

        cli.run("run", root.toString());

        theStageRan(STAGE_THREE_FINISHED);
        claim(
                "the walk holds " + COPIES + " files and the content census checks each against the ledger, the"
                        + " copies stage 1 set aside among them, so its running count writes exactly one line,"
                        + " at the thousandth: it has no total, so it says how many so far",
                () -> assertThat(ProgressLines.of(logged.list, OCCURRENCES_CHECKED))
                        .containsExactlyElementsOf(ProgressLines.expectedRunning(OCCURRENCES_CHECKED, COPIES)));
        claim(
                "and the line was written by the progress counter itself",
                () -> assertThat(ProgressLines.loggersOf(logged.list, OCCURRENCES_CHECKED))
                        .isNotEmpty()
                        .containsOnly(ProgressLines.theCountersLogger()));
    }

    @Test
    @Story("A read made a page at a time reports how far it has gone")
    @DisplayName("Over eleven hundred documents, the reads of their measurements say how far they have gone after each page, and the comparison says each time it reads them again")
    void theReadsOfExtractionMetricsSayHowFarAfterEachPage(@TempDir Path root, @TempDir Path seeds) throws IOException {
        writeTexts(root);
        Files.writeString(seeds.resolve("seed.txt"), "a seed about harbour pilots and the tide tables they kept");
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .build());

        cli.run("run", root.toString());

        theStageRan(STAGE_THREE_FINISHED);
        claim(
                "no document of the collection was removed by any stage, so all " + TEXTS + " are measured and"
                        + " all reach the comparison",
                () -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM verdict v JOIN file_occurrence f ON f.id = v.occurrence_id"
                                        + " JOIN walk w ON w.id = f.walk_id WHERE w.root = ?",
                                Long.class,
                                Walk.canonicalRoot(root).toString()))
                        .isZero());
        List<String> comparisonSaid = StatementLines.of(operatorLines(), STAGE_FIVE_B);
        claim(
                "the comparison ran, and read the collection's measurements over up to the " + TEXTS + " rows the"
                        + " extraction wrote",
                () -> assertThat(comparisonSaid)
                        .containsSubsequence(StatementLines.countedRead(STAGE_FIVE_B, CORPUS_METRICS, TEXTS)));

        claim(
                "the content census says how far its read of the measurements has gone after each of its two"
                        + " pages: 1,000 of the 1,100 rows, then all of them",
                () -> assertThat(ProgressLines.of(logged.list, READING_EXTRACTION_METRICS))
                        .containsExactlyElementsOf(under(READING_EXTRACTION_METRICS, 1)));
        claim(
                "the comparison says the same of its first read of the collection's measurements",
                () -> assertThat(ProgressLines.of(logged.list, READING_CORPUS_METRICS))
                        .containsExactlyElementsOf(under(READING_CORPUS_METRICS, 1)));
        List<String> again = ProgressLines.of(logged.list, READING_CORPUS_METRICS_AGAIN);
        int timesAgain = again.size() / NINETY_THEN_A_HUNDRED_PERCENT.size();
        claim(
                "the documents' " + DIFFERENT_LENGTHS + " different lengths are more than a first reading can"
                        + " settle the quartiles of, so the comparison reads the measurements again, at least"
                        + " once and at most " + AT_MOST_THREE_TIMES_AGAIN + " times, and each time says how far"
                        + " it has gone after each page, under a name that says it is reading them again",
                () -> {
                    assertThat(timesAgain).isBetween(1, AT_MOST_THREE_TIMES_AGAIN);
                    assertThat(again).containsExactlyElementsOf(under(READING_CORPUS_METRICS_AGAIN, timesAgain));
                });
        List<String> expectedAgain = new ArrayList<>();
        for (int time = 0; time < timesAgain; time++) {
            expectedAgain.addAll(StatementLines.countedRead(STAGE_FIVE_B, CORPUS_METRICS_AGAIN, TEXTS));
        }
        claim(
                "and each of those reads has its line before, over up to the same " + TEXTS + " rows, and its"
                        + " line after with the time it took",
                () -> assertThat(comparisonSaid.stream().filter(line -> line.contains(CORPUS_METRICS_AGAIN)).toList())
                        .containsExactlyElementsOf(expectedAgain));
    }

    /** The two progress lines of one read over the 1,100 rows, {@code times} times over, under {@code label}. */
    private static List<String> under(String label, int times) {
        List<String> lines = new ArrayList<>();
        for (int time = 0; time < times; time++) {
            NINETY_THEN_A_HUNDRED_PERCENT.forEach(line -> lines.add(label + line));
        }
        return lines;
    }

    /** {@value #TEXTS} texts of made-up words, each word with a vowel and at least two letters, no two texts alike. */
    private static void writeTexts(Path root) throws IOException {
        String[] syllables = {"ka", "lo", "mi", "ter", "san", "vu", "pel", "dor", "ri", "nas", "ob", "ef", "gal", "tu"};
        for (int text = 0; text < TEXTS; text++) {
            Random random = new Random(456L * (text + 1));
            int words = FEWEST_WORDS + (text * 37) % DIFFERENT_LENGTHS;
            StringBuilder written = new StringBuilder();
            for (int word = 0; word < words; word++) {
                if (word > 0) {
                    written.append(' ');
                }
                int length = 2 + random.nextInt(3);
                for (int syllable = 0; syllable < length; syllable++) {
                    written.append(syllables[random.nextInt(syllables.length)]);
                }
            }
            Files.writeString(root.resolve(String.format("text-%04d.txt", text)), written.toString());
        }
    }

    /** That the stage did its work in this invocation, so a claim about its lines fails for the lines alone. */
    private void theStageRan(String line) {
        claim(
                "the stage ran, which its own line says (" + line.strip() + " ...)",
                () -> assertThat(operatorLines()).anyMatch(written -> written.startsWith(line)));
    }

    private List<String> operatorLines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
