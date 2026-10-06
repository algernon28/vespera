package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
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
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The two loops of stage 1 that had no counter: the one that reads each survivor's recorded size to group
 * the survivors, and the one that records each duplicate (ADR-192 sections 3 and 4, #412). Stage 1 already
 * counted its broken check and its hashing (ADR-188).
 *
 * <p>The sizes counter has a total, the survivors the pass will read (ADR-200). The duplicates have none before the
 * loop starts, since how many files are copies of another is known only when every size group has been
 * hashed, so they are a running count, {@code N so far}, whose first line falls at 1,000 (ADR-192 section
 * 8). It is pinned over {@value #COPIES} copies of one file, of which stage 1 records {@code COPIES - 1} as
 * superseded: one line, at 1,000.
 *
 * <p><b>Red until part (a) of ADR-192 lands</b>, at the claims that the counters' lines are there: on main
 * neither loop writes a line. Every line of both counters must come from {@code StageProgress}'s logger. The
 * contract by which {@code corpus} hands the counts over is pinned in {@code
 * ContentIdentityResolutionReportsItsLoopsTest}, which part (a) brings in.
 *
 * <p><b>Parked under {@code docs/adr/0193/tests/b/} with one claim more than the file of this name in {@code
 * src/test}</b>, which it replaces when part (b) of ADR-193 lands (ADR-199 sections 2 and 3, #411, #429):
 * the count of the survivors that sizes each of stage 1's two counters is timed, a line before it and a line
 * after it with the seconds it took. On main that claim is red by assertion, the lines not being written,
 * and every other claim here passes.
 */
@CascadeSliceTest
@Import(ConverterStopsPartwayBeans.class)
@Epic("Pipeline")
@Feature("Progress reporting")
@Issue("412")
@Link(name = "ADR-192", url = Adr.EVERY_LOOP_REPORTS_ITS_PROGRESS, type = "adr")
@Link(name = "ADR-199", url = Adr.THE_STATEMENTS_ADR_193_LEFT_UNNAMED_TAKE_ITS_RULE, type = "adr")
class StageOneReportsItsLoopsInvocationTest {

    /** Stage 1's own name, which its statement lines open with. */
    private static final String STAGE_ONE = "Stage 1 (byte-level reduction)";

    private static final String BROKEN_CHECK = "Stage 1 (byte-level reduction, broken check)";

    /** One original and 1,001 copies: the 1,000th duplicate recorded is the first line, and no second falls. */
    static final int COPIES = 1_002;

    /** Three files of three different sizes, so each is a survivor whose size is read and none is a copy. */
    private static final int THREE_FILES = 3;

    private static final String SIZES = "Stage 1 (byte-level reduction, sizes read)";
    private static final String DUPLICATES = "Stage 1 (byte-level reduction, duplicates recorded)";

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
    @Story("Stage 1 says how far it has got in reading sizes")
    @DisplayName("Reading the sizes of three files to group them is counted, one line each")
    void countsTheSurvivorsWhoseSizeItReads(@TempDir Path root) throws IOException {
        for (int position = 1; position <= THREE_FILES; position++) {
            Files.writeString(root.resolve("0" + position + "-note.txt"), "Note " + position + " " + "x".repeat(position));
        }

        cli.run("run", root.toString());

        stageOneRan();
        List<String> lines =
                logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        claim(
                "stage 1 says it is counting the files left to check, and how long the count took, and then the"
                        + " same of the files left to size: each line once, in that order, and no other line"
                        + " about a count or a read",
                () -> assertThat(StatementLines.of(lines, STAGE_ONE))
                        .containsExactlyElementsOf(StatementLines.inOrder(
                                StatementLines.timedCount(STAGE_ONE, "the survivors to check"),
                                StatementLines.timedCount(STAGE_ONE, "the survivors to size"))));
        claim(
                "each count is said to have ended before the counter it sizes writes its first line",
                () -> assertThat(String.join("\n", lines))
                        .containsSubsequence(
                                STAGE_ONE + " counted the survivors to check in ",
                                BROKEN_CHECK + ": 1 of ",
                                STAGE_ONE + " is counting the survivors to size",
                                STAGE_ONE + " counted the survivors to size in ",
                                SIZES + ": 1 of "));
        claim(
                "the counter reads one, two and three of three, for the three survivors whose size is read to"
                        + " group them: a file whose size no other file shares is counted too, though it is"
                        + " never hashed",
                () -> assertThat(ProgressLines.of(logged.list, SIZES))
                        .containsExactlyElementsOf(ProgressLines.expected(SIZES, THREE_FILES)));
        claim(
                "and every one of those lines was written by the progress counter itself",
                () -> assertThat(ProgressLines.loggersOf(logged.list, SIZES))
                        .isNotEmpty()
                        .containsOnly(ProgressLines.theCountersLogger()));
    }

    @Test
    @Story("Stage 1 says how many duplicates it has recorded")
    @DisplayName("A thousand and one copies of one file are recorded as duplicates, and a running count says so at a thousand")
    void aRunningCountOfTheDuplicatesItRecords(@TempDir Path root) throws IOException {
        for (int copy = 0; copy < COPIES; copy++) {
            Files.writeString(root.resolve(String.format("copy-%04d.txt", copy)), "the same bytes in every copy");
        }

        cli.run("run", root.toString());

        stageOneRan();
        claim(
                "stage 1 records " + (COPIES - 1) + " of the " + COPIES + " files as copies of the one it keeps,"
                        + " so the running count writes exactly one line, at the thousandth: it has no total, so"
                        + " it says how many so far and not what share",
                () -> assertThat(ProgressLines.of(logged.list, DUPLICATES))
                        .containsExactlyElementsOf(ProgressLines.expectedRunning(DUPLICATES, COPIES - 1)));
        claim(
                "and the line was written by the progress counter itself",
                () -> assertThat(ProgressLines.loggersOf(logged.list, DUPLICATES))
                        .isNotEmpty()
                        .containsOnly(ProgressLines.theCountersLogger()));
    }

    /** That stage 1 did its work in this invocation, so a claim about a counter fails for the counter alone. */
    private void stageOneRan() {
        claim(
                "stage 1 ran and finished in this invocation, which its own finishing line says",
                () -> assertThat(logged.list)
                        .extracting(ILoggingEvent::getFormattedMessage)
                        .anyMatch(line -> line.startsWith(STAGE_ONE_FINISHED)));
    }

    private static final String STAGE_ONE_FINISHED = "Stage 1 (byte-level reduction) finished under run ";
}
