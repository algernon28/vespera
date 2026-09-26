package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.extraction.ConversionStatus;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.DoclingResponse;
import io.algernon.vespera.extraction.ScriptedExtractor;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * What stage 2 and stage 4a say at the end of a step that failed part-way through (#311).
 *
 * <p>Both steps log their end line (ADR-093) from a step listener's {@code afterStep}, and Spring Batch
 * calls {@code afterStep} from a {@code finally}, after a failed step as well as a completed one
 * ({@code AbstractStep.execute}, spring-batch-core 6.0.5). Neither line looked at the step's exit
 * status, so a step that had just failed was reported as having <em>finished</em>, directly under
 * Spring Batch's own error for it. The counts were true; the verb was not, and it is the one word an
 * operator reading a failed invocation's log looks for. The listeners that record completion ({@link
 * RunCompletion} and stage 4a's own) already check for {@code COMPLETED}, so nothing but the line was
 * wrong (ADR-116).
 *
 * <p>Each test fails its step after one whole chunk has been written, so the counts on the line are
 * not zero and a line that merely drops the counts would not pass.
 *
 * <ul>
 *   <li><b>Stage 2</b> fails the way ADR-071 fails it: the converter stops answering, every call times
 *       out, and the circuit breaker stops the step. {@link ScriptedExtractor} answers one chunk's worth
 *       of conversions and then only times out.
 *   <li><b>Stage 4a</b> has no external call to lose, so the failure is a write the database refuses: a
 *       trigger that aborts every signature insert once one chunk of signatures is stored, standing in
 *       for a disk that filled up. Every document is given text of its own, because text shared by
 *       every document is boilerplate at a floor of 1.0 and stage 4a would then sign nothing.
 * </ul>
 *
 * <p>Both invocations fail, and Spring Batch logs each step's failure with its stack trace at {@code
 * ERROR}. That is the step failing as it should, not noise from the test.
 *
 * <p>{@code @DirtiesContext} per method, as {@link ExtractionStepTest} has it and for its reason: each
 * test scripts the one {@link ScriptedExtractor} from {@link StubbedExtractionBeans}, and a queue left
 * over from one test would answer the next. A fresh context is also a fresh in-memory database, so
 * the trigger the second test installs never reaches another test.
 */
@CascadeSliceTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(StubbedExtractionBeans.class)
@Epic("Pipeline")
@Feature("Progress reporting")
@Issue("311")
@Link(name = "ADR-093", url = Adr.LOGGING_IS_EXPLICIT_AND_PROCESS_SCOPED, type = "adr")
@Link(name = "ADR-116", url = Adr.A_RUNS_COMPLETION_IS_RECORDED_PER_STEP, type = "adr")
class FailedStepSaysItFailedTest {

    /** The logger every operator-facing line in this application is written through. */
    private static final String APPLICATION_LOGGER = "io.algernon.vespera";

    /** How stage 2's end line opens, followed by the verb that says how the step ended. */
    private static final String STAGE_2 = "Stage 2 (extraction) ";

    /** How stage 4a's end line opens, followed by the verb that says how the step ended. */
    private static final String STAGE_4A = "Stage 4a (redundancy signatures) ";

    /** The verb that is false about a step that failed. */
    private static final String FINISHED = "finished";

    /** The verb that is true about it. */
    private static final String FAILED = "failed";

    /** What the operator should be told to do once the cause is fixed, in the words #306's line uses. */
    private static final String RUN_THE_SAME_COMMAND_AGAIN = "run the same command again";

    /**
     * What stage 2's circuit breaker names as the failure once the converter has stopped answering:
     * the start of {@code ExtractorStoppedAnsweringException}'s message, so the line names the cause
     * beneath Spring Batch's wrappers rather than the wrapper.
     */
    private static final String THE_EXTRACTOR_STOPPED_ANSWERING = "the extractor set aside";

    /** What the database says when the trigger refuses a signature, and so what stage 4a's line names. */
    private static final String THE_DISK_FILLED_UP = "stands in for a disk that filled up";

    /**
     * Documents in the stage 2 corpus: two whole chunks, so the first can be written before the
     * converter stops and the second holds more calls than the timeout streak and the breaker need
     * between them to stop the step.
     */
    private static final int STAGE_2_DOCUMENTS = 2 * ExtractionJobConfiguration.CHUNK_SIZE;

    /** Conversions answered before the converter stops: exactly the first chunk. */
    private static final int ANSWERED_BEFORE_THE_CONVERTER_STOPS = ExtractionJobConfiguration.CHUNK_SIZE;

    /** Signatures the database accepts before it refuses the rest: exactly the first chunk. */
    private static final int SIGNED_BEFORE_THE_DISK_FILLS = RedundancyJobConfiguration.CHUNK_SIZE;

    /** Documents in the stage 4a corpus: one chunk more than a few, so the second chunk is reached. */
    private static final int STAGE_4A_DOCUMENTS = RedundancyJobConfiguration.CHUNK_SIZE + 5;

    /** A floor of 1.0 opens stage 4's gate while stripping only a shingle every document carries. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private DoclingExtractor doclingExtractor;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger applicationLogger;

    @BeforeEach
    void captureOperatorLines() {
        logged = new ListAppender<>();
        logged.start();
        applicationLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(APPLICATION_LOGGER);
        applicationLogger.addAppender(logged);
    }

    @AfterEach
    void releaseOperatorLines() {
        applicationLogger.detachAppender(logged);
        logged.stop();
    }

    @Test
    @Story("A step that failed says it failed")
    @DisplayName("When the converter stops answering part-way through extraction, its closing line says the stage failed, with its counts, and not that it finished")
    @Link(name = "ADR-071", url = Adr.DOCLING_INVOCATION_CONTRACT_IS_ONE_SYNC_CALL, type = "adr")
    void extractionThatFailedPartWaySaysItFailed(@TempDir Path root) throws IOException {
        for (int i = 0; i < STAGE_2_DOCUMENTS; i++) {
            Files.writeString(root.resolve("document-" + i + ".txt"), "content of document " + i);
        }
        scripted()
                .answering(ANSWERED_BEFORE_THE_CONVERTER_STOPS, withText("stubbed but real content"))
                .timingOut(STAGE_2_DOCUMENTS);

        cli.run("run", root.toString());

        claim(
                "the invocation failed, because stage 2 did: a converter that stops answering stops the"
                        + " stage once enough calls in a row have gone unanswered",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "no line says stage 2 finished: it stopped part-way, and saying it finished beside the"
                        + " error leaves the operator unable to tell from the stage's own line how it ended",
                () -> assertThat(operatorLines()).noneMatch(line -> line.startsWith(STAGE_2 + FINISHED)));
        claim(
                "a line says stage 2 failed, still carries the counts -- the first "
                        + ANSWERED_BEFORE_THE_CONVERTER_STOPS + " documents, one whole batch, were measured"
                        + " before the converter stopped, and each is counted as filtered: a document that"
                        + " converts cleanly earns no removal, so nothing about it is handed to the part of"
                        + " the stage that writes removals -- names what failed it, and says to run the same"
                        + " command again",
                () -> assertThat(operatorLines())
                        .anyMatch(line -> line.startsWith(STAGE_2 + FAILED)
                                && line.contains("filtered=" + ANSWERED_BEFORE_THE_CONVERTER_STOPS)
                                && line.contains(THE_EXTRACTOR_STOPPED_ANSWERING)
                                && line.contains(RUN_THE_SAME_COMMAND_AGAIN)));
    }

    @Test
    @Story("A step that failed says it failed")
    @DisplayName("When a write fails part-way through redundancy signatures, its closing line says the stage failed, with its counts, and not that it finished")
    void signaturesThatFailedPartWaySayTheyFailed(@TempDir Path root) throws IOException {
        for (int i = 0; i < STAGE_4A_DOCUMENTS; i++) {
            Files.writeString(root.resolve("document-" + i + ".txt"), "content of document " + i);
            // Text of its own for each document, in whichever order the conversions are asked for:
            // every five-word window carries this document's own number, so none is boilerplate.
            scripted().answering(withText("entry" + i + " alpha" + i + " bravo" + i + " charlie" + i
                    + " delta" + i + " echo" + i + " foxtrot" + i + " golf" + i));
        }
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .build());
        jdbcTemplate.execute("CREATE TRIGGER the_disk_fills_up BEFORE INSERT ON minhash_signature"
                + " WHEN (SELECT COUNT(*) FROM minhash_signature) >= " + SIGNED_BEFORE_THE_DISK_FILLS
                + " BEGIN SELECT RAISE(ABORT, 'this test''s trigger " + THE_DISK_FILLED_UP + "'); END");

        cli.run("run", root.toString());

        claim(
                "the invocation failed, because stage 4a did: the database refused a signature once "
                        + SIGNED_BEFORE_THE_DISK_FILLS + ", one whole batch, were stored",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "no line says stage 4a finished",
                () -> assertThat(operatorLines()).noneMatch(line -> line.startsWith(STAGE_4A + FINISHED)));
        claim(
                "a line says stage 4a failed, still carries the counts -- the " + SIGNED_BEFORE_THE_DISK_FILLS
                        + " signatures written before the refusal -- names what failed it, and says to run the"
                        + " same command again",
                () -> assertThat(operatorLines())
                        .anyMatch(line -> line.startsWith(STAGE_4A + FAILED)
                                && line.contains("written=" + SIGNED_BEFORE_THE_DISK_FILLS)
                                && line.contains(THE_DISK_FILLED_UP)
                                && line.contains(RUN_THE_SAME_COMMAND_AGAIN)));
    }

    private ScriptedExtractor scripted() {
        return (ScriptedExtractor) doclingExtractor;
    }

    /** A successful conversion carrying {@code text}, in the shape the real sidecar answers with. */
    private static DoclingResponse withText(String text) {
        return new DoclingResponse(
                ConversionStatus.SUCCESS,
                List.of(),
                0d,
                null,
                "{\"document\":{\"json_content\":{\"texts\":[{\"text\":\"" + text + "\"}]}}}");
    }

    private List<String> operatorLines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
