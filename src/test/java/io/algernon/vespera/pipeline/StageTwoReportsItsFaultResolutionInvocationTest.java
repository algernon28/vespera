package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.profile.Profile;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Stage 2's end-of-step resolution of the faults it recorded (ADR-139, ADR-189, ADR-220 section 14), counted (ADR-192 section 4,
 * #412): each fault the step set aside is written as a row, and as {@code extraction-failed} where the step
 * completed, in {@code extraction.ExtractionFaultResolution.resolve}, which tells {@code pipeline} the total
 * and each fault resolved through {@code FaultResolutionProgress}.
 *
 * <p><b>Part (b) of ADR-192.</b> This compiles against main and is red there at the claim that the
 * counter's line is there; part (b) moves it into {@code src/test} and turns it green. The fixture is {@code
 * SeedScriptedExtractionBeans}, whose converter fails on {@code CONVERTER_FAULT} while blaming itself, which
 * is what a recorded fault is; {@code ExtractionFaultInvocationTest} pins the row and the verdict.
 *
 * <p><b>Two claims came with part (b) of ADR-193</b> (ADR-193 section 6, ADR-204 section 3, #411): on a first
 * invocation stage 2 times its count of the survivors still to read and, at its end, its read of the
 * occurrences it could not read for the review list, and says nothing of the two reads a resume makes, which
 * are ADR-199's.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Pipeline")
@Feature("Progress reporting")
@Issue("412")
@Link(name = "ADR-192", url = Adr.EVERY_LOOP_REPORTS_ITS_PROGRESS, type = "adr")
@Link(name = "ADR-193", url = Adr.STATEMENTS_REPORT_THEIR_PROGRESS, type = "adr")
@Link(name = "ADR-204", url = Adr.PART_B_OF_THE_STATEMENTS_WRITTEN_OUT, type = "adr")
class StageTwoReportsItsFaultResolutionInvocationTest {

    /** Stage 2's own name, which its statement lines open with, and which its counter over the files is labelled with. */
    private static final String STAGE_TWO = "Stage 2 (extraction)";

    /**
     * That stage 2 timed its count and its read for the review list, once each, in the order it issues them,
     * around the work between: the count before the first file is counted as read, the read before the line
     * that says where the list is.
     */
    private void stageTwoTimedItsCountAndItsReviewListRead() {
        List<String> lines =
                logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        claim(
                "stage 2 says it is counting the files it still has to read, and how long that took, and at its"
                        + " end that it is reading the ones it could not read, and how long that took: each line"
                        + " once, in that order, and nothing about what an earlier invocation left, there being"
                        + " none",
                () -> assertThat(StatementLines.of(lines, STAGE_TWO))
                        .containsExactlyElementsOf(StatementLines.inOrder(
                                StatementLines.timedCount(STAGE_TWO, "the survivors still to read"),
                                StatementLines.timedRead(STAGE_TWO, "the occurrences it could not read"))));
        claim(
                "the count ends before the first file is counted as read, and the read for the list ends before"
                        + " the line that says where the list is",
                () -> assertThat(String.join("\n", lines))
                        .containsSubsequence(
                                STAGE_TWO + " counted the survivors still to read in ",
                                STAGE_TWO + ": 1 of ",
                                STAGE_TWO + " read the occurrences it could not read in ",
                                "could not be read; they are listed in "));
    }

    private static final String BOILERPLATE_FLOOR = "1.0";
    private static final String MODEL_NAME = "qwen3-embedding:0.6b";

    /** The corpus file the scripted converter fails on while blaming itself: one recorded fault. */
    private static final String FAULTED = SeedScriptedExtractionBeans.CONVERTER_FAULT;

    private static final String READABLE = "readable.txt";

    private static final String FAULTS_RESOLVED = "Stage 2 (extraction, faults resolved)";

    /** One fault recorded, so one line: a total under forty is a line per item. */
    private static final int ONE_FAULT = 1;

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

    @BeforeEach
    void captureOperatorLines() {
        SeedScriptedExtractionBeans.stopMovingAway();
        logged = new ListAppender<>();
        logged.start();
        applicationLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("io.algernon.vespera");
        applicationLogger.addAppender(logged);
    }

    @AfterEach
    void releaseOperatorLines() {
        SeedScriptedExtractionBeans.stopMovingAway();
        applicationLogger.detachAppender(logged);
        logged.stop();
    }

    @Test
    @Story("Stage 2 says how many of the faults it recorded it has resolved")
    @DisplayName("One file the converter failed on while blaming itself is one fault resolved, and one line says so")
    void countsTheFaultItResolves(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve(FAULTED), "a file the converter faults on");
        Files.writeString(root.resolve(READABLE), "a corpus document with real text in it");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        claim("the invocation reported success", () -> assertThat(cli.getExitCode()).isZero());
        stageTwoTimedItsCountAndItsReviewListRead();
        claim(
                "the step recorded one fault and resolved it at its end, and the counter reads one of one",
                () -> assertThat(ProgressLines.of(logged.list, FAULTS_RESOLVED))
                        .containsExactlyElementsOf(ProgressLines.expected(FAULTS_RESOLVED, ONE_FAULT)));
        claim(
                "and the line was written by the progress counter itself",
                () -> assertThat(ProgressLines.loggersOf(logged.list, FAULTS_RESOLVED))
                        .isNotEmpty()
                        .containsOnly(ProgressLines.theCountersLogger()));
    }

    @Test
    @Story("Stage 2 says how many of the faults it recorded it has resolved")
    @DisplayName("A stage 2 that recorded no fault resolves nothing and writes no fault counter")
    void writesNothingWhereNoFaultWasRecorded(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve(READABLE), "a corpus document with real text in it");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profile(seeds);

        cli.run("run", root.toString());

        claim("the invocation reported success", () -> assertThat(cli.getExitCode()).isZero());
        stageTwoTimedItsCountAndItsReviewListRead();
        claim(
                "no fault was recorded, so the resolution is never reached and no counter line is written: that"
                        + " passes on main and has to go on passing",
                () -> assertThat(ProgressLines.of(logged.list, FAULTS_RESOLVED)).isEmpty());
    }

    private void profile(Path seeds) {
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(MODEL_NAME, "set by this test, so gate 3 is open")
                .build());
    }
}
