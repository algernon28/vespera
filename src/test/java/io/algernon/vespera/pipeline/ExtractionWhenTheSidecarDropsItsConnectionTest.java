package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.extraction.DoclingClient;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.ExtractionBeans;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * What stage 2 does when one document's call fails, and when {@code docling-serve} drops the connection
 * under a call (#326, ADR-175): a problem with one file fails that file and the step goes on, and only a
 * sidecar that stays gone stops the step.
 *
 * <p>The sidecar is real HTTP: the production {@code DoclingClient} (through {@link ExtractionBeans},
 * with the production extractor and cache around it) pointed at a {@link LoopbackSidecar}, which
 * converts every document except the ones a test scripts otherwise.
 *
 * <p>Every claim about which documents were asked for is made on counts per document, never on which
 * call came back first, because eight calls are in flight at once (ADR-140).
 */
@CascadeSliceTest
@Import(ExtractionBeans.class)
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("326")
@Link(name = "ADR-175", url = Adr.A_FILE_THAT_FAILS_IS_MARKED_AND_SKIPPED, type = "adr")
@Link(name = "ADR-071", url = Adr.DOCLING_INVOCATION_CONTRACT_IS_ONE_SYNC_CALL, type = "adr")
@Link(name = "ADR-140", url = Adr.STAGE_2_CONVERTS_EIGHT_AT_A_TIME, type = "adr")
class ExtractionWhenTheSidecarDropsItsConnectionTest {

    /** A loopback port that was free when the class loaded; the test's sidecar listens on it. */
    private static final int SIDECAR_PORT = LoopbackSidecar.aFreePort();

    /** Where the client looks for the sidecar. */
    private static final String SIDECAR = "http://127.0.0.1:" + SIDECAR_PORT;

    /** The corpus: few enough to be one batch, so every document is in flight together. */
    private static final int DOCUMENTS = 4;

    /** The one document a test scripts a failure for. */
    private static final int THE_FAILING_DOCUMENT = 2;

    /** How often a document is posted when its first call was dropped: the call, and the one retry. */
    private static final long TWICE = 2;

    /** How long these tests wait for a converter that dropped a connection to answer its health check. */
    private static final Duration WAIT_AT_MOST = Duration.ofSeconds(1);

    /** How often they ask it meanwhile. */
    private static final Duration CHECK_EVERY = Duration.ofMillis(50);

    /** How stage 2's closing line opens when the step did not complete. */
    private static final String STAGE_2_FAILED = "Stage 2 (extraction) failed and is not recorded as finished";

    /** What the operator is told to do once the cause is fixed, in the words every closing line uses. */
    private static final String RUN_THE_SAME_COMMAND_AGAIN = "run the same command again";

    /** The status docling-serve answers a job that failed inside its worker with. */
    private static final int NOT_FOUND = 404;

    /** The page stage 2 lists the files it could not read on, beside the database. */
    private static final String THE_REVIEW_LIST = "extraction-failures.html";

    /** How many documents in a row may drop the connection twice before extraction stops. */
    private static final int FIVE_IN_A_ROW = 5;

    /** A corpus one document longer than that, so the stage stops with a document still unread. */
    private static final int MORE_THAN_FIVE = FIVE_IN_A_ROW + 1;

    /** Four documents that drop the connection twice, one that converts, and four more that drop. */
    private static final int FOUR_ONE_FOUR = 2 * (FIVE_IN_A_ROW - 1) + 1;

    /** The fifth document of such a corpus: the one between the two fours. */
    private static final int THE_ONE_THAT_CONVERTS = FIVE_IN_A_ROW;

    /** The document read just before the one a test scripts a failure for. */
    private static final int THE_ONE_BEFORE = THE_FAILING_DOCUMENT - 1;

    /** How many timeouts in a row stop being about the file and start being about the converter. */
    private static final int THREE_IN_A_ROW = 3;

    /** The status docling-serve answers with when its own wait for a conversion runs out. */
    private static final int GATEWAY_TIMEOUT = 504;

    /** What docling-serve says beside that status. */
    private static final String CONVERSION_IS_TAKING_TOO_LONG = "{\"detail\":\"Conversion is taking too long."
            + " The maximum wait time is configure as DOCLING_SERVE_MAX_SYNC_WAIT=120.\"}";

    /** A document removed only when the stage completes: the converter refused it for its own reason. */
    private static final int REMOVED_AT_THE_END = 1;

    /** A document removed on its own turn: the converter answered it with an error status. */
    private static final int REMOVED_ON_ITS_TURN = 3;

    /** How many documents that test removes. */
    private static final long BOTH = 2;

    /** How many files a stage that read everything could not read. */
    private static final long NONE = 0;

    /** What the converter says when it refuses a document for a reason of its own. */
    private static final String NO_CAPACITY = "no worker was free";

    /** A refusal the converter blames on itself, in the wire shape it answers with. */
    private static final String REFUSED_FOR_CAPACITY = "{\"status\":\"failure\",\"errors\":[{"
            + "\"component_type\":\"pipeline\",\"module_name\":\"docling.pipeline\","
            + "\"error_message\":\"" + NO_CAPACITY + "\",\"category\":\"capacity\"}],"
            + "\"processing_time\":0.1}";

    /** The step's own count of what it set aside, as its closing line reports it. */
    private static final String NOTHING_SKIPPED = "skipped=0";

    /** How often a document nothing went wrong with is posted. */
    private static final long ONCE = 1;

    /** How stage 2's closing line opens when the step completed. */
    private static final String STAGE_2_FINISHED = "Stage 2 (extraction) finished";

    /** The start of the line the breaker writes for each failure it counts. */
    private static final String BREAKER_COUNTED = "service-scope failure on";

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void sidecarAndWorkingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add("vespera.docling.base-url", () -> SIDECAR);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DoclingExtractor extractor;

    @Value("${vespera.docling.image}")
    private String configuredImage;

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger rootLogger;
    private LoopbackSidecar sidecar;

    @BeforeEach
    void startTheSidecarAndCaptureEveryLine() throws IOException {
        sidecar = LoopbackSidecar.startedOn(SIDECAR_PORT, configuredImage);
        logged = new ListAppender<>();
        logged.start();
        rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(logged);
    }

    @AfterEach
    void stopTheSidecarAndReleaseEveryLine() {
        rootLogger.detachAppender(logged);
        logged.stop();
        sidecar.close();
    }

    @ParameterizedTest(name = "HTTP {0}")
    @ValueSource(ints = {404, 500})
    @Story("A document the converter answers an error status for")
    @DisplayName("When the document converter answers one document with an error status, that document is removed with the status as the reason, nothing is stored for it, and every other document is converted")
    void anErrorStatusOnOneDocumentRemovesThatDocumentAndTheStepGoesOn(int status, @TempDir Path root)
            throws IOException {
        writeTheCorpus(root);
        sidecar.rejecting(THE_FAILING_DOCUMENT, status);

        cli.run("run", root.toString());

        claim(
                "extraction completed: one document's error status did not stop the stage",
                () -> assertThat(lines()).anyMatch(line -> line.startsWith(STAGE_2_FINISHED)));
        claim(
                "document " + THE_FAILING_DOCUMENT + " was removed as one extraction failed on, and the"
                        + " reason says the converter answered HTTP " + status + " for this file",
                () -> assertThat(extractionFailedReasons(root))
                        .containsOnlyKeys(nameOf(THE_FAILING_DOCUMENT))
                        .hasEntrySatisfying(nameOf(THE_FAILING_DOCUMENT), reason -> assertThat(reason)
                                .startsWith("rejected: docling-serve answered HTTP " + status)
                                .contains(LoopbackSidecar.WORDS_BESIDE_AN_ERROR_STATUS)));
        claim(
                "each of the " + DOCUMENTS + " documents was posted once: the rejected one was not asked"
                        + " about again, and the other " + (DOCUMENTS - 1) + " were converted",
                () -> assertThat(sidecar.callsPerDocument())
                        .hasSize(DOCUMENTS)
                        .allSatisfy((document, calls) -> assertThat(calls).isEqualTo(ONCE)));
        claim(
                "nothing was stored for the rejected document, so the next run asks the converter again,"
                        + " while each converted document's answer was stored",
                () -> {
                    assertThat(cachedAnswersFor(root, THE_FAILING_DOCUMENT)).isZero();
                    assertThat(cachedAnswersFor(root, THE_FAILING_DOCUMENT + 1)).isEqualTo(ONCE);
                });
        claim(
                "the rejection was not counted towards stopping extraction for a converter that has"
                        + " stopped answering",
                () -> assertThat(lines()).noneMatch(line -> line.contains(BREAKER_COUNTED)));
    }

    @Test
    @Story("A converter that drops one connection and is back")
    @DisplayName("When the document converter drops the connection under one document and answers its health check again, that document is asked about once more, converted and kept, and nothing is set aside or counted")
    void aDroppedConnectionIsRetriedOnceTheConverterIsBack(@TempDir Path root) throws IOException {
        writeTheCorpus(root);
        sidecar.dropping(THE_FAILING_DOCUMENT, 1);

        cli.run("run", root.toString());

        claim(
                "the invocation succeeded: a connection dropped once did not fail extraction",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "extraction completed with nothing set aside",
                () -> assertThat(lines())
                        .anyMatch(line -> line.startsWith(STAGE_2_FINISHED) && line.contains(NOTHING_SKIPPED)));
        claim(
                "document " + THE_FAILING_DOCUMENT + " was posted " + TWICE + " times, the dropped call and"
                        + " one retry, and each of the other " + (DOCUMENTS - 1) + " once",
                () -> assertThat(sidecar.callsPerDocument())
                        .hasSize(DOCUMENTS)
                        .allSatisfy((document, calls) ->
                                assertThat(calls).isEqualTo(document == THE_FAILING_DOCUMENT ? TWICE : ONCE)));
        claim(
                "no document was removed: the retried one was converted like the rest, and its answer"
                        + " was stored",
                () -> {
                    assertThat(extractionFailedReasons(root)).isEmpty();
                    assertThat(cachedAnswersFor(root, THE_FAILING_DOCUMENT)).isEqualTo(ONCE);
                });
        claim(
                "the dropped connection was not counted towards stopping extraction for a converter that"
                        + " has stopped answering",
                () -> assertThat(lines()).noneMatch(line -> line.contains(BREAKER_COUNTED)));
    }

    @Test
    @Story("A document that makes the converter drop the connection every time")
    @DisplayName("When the document converter drops the connection under the same document twice, that document is removed as one that crashed the converter, nothing is stored for it, and every other document is converted")
    void aDocumentWhoseCallDropsTwiceIsRemovedAndTheStepGoesOn(@TempDir Path root) throws IOException {
        writeTheCorpus(root);
        sidecar.dropping(THE_FAILING_DOCUMENT, LoopbackSidecar.EVERY_CALL);

        cli.run("run", root.toString());

        claim(
                "extraction completed with nothing set aside: one document that drops the connection every"
                        + " time did not stop the stage",
                () -> assertThat(lines())
                        .anyMatch(line -> line.startsWith(STAGE_2_FINISHED) && line.contains(NOTHING_SKIPPED)));
        claim(
                "document " + THE_FAILING_DOCUMENT + " was posted " + TWICE + " times and no more, the"
                        + " dropped call and one retry, and each of the other " + (DOCUMENTS - 1) + " once",
                () -> assertThat(sidecar.callsPerDocument())
                        .hasSize(DOCUMENTS)
                        .allSatisfy((document, calls) ->
                                assertThat(calls).isEqualTo(document == THE_FAILING_DOCUMENT ? TWICE : ONCE)));
        claim(
                "document " + THE_FAILING_DOCUMENT + " alone was removed as one extraction failed on, and"
                        + " the reason says it crashed the converter and what the dropped connection looked"
                        + " like",
                () -> assertThat(extractionFailedReasons(root))
                        .containsOnlyKeys(nameOf(THE_FAILING_DOCUMENT))
                        .hasEntrySatisfying(nameOf(THE_FAILING_DOCUMENT), reason -> assertThat(reason)
                                .startsWith("crashed the converter: docling-serve dropped the connection twice"
                                        + " while converting this file: ")
                                .contains(LoopbackSidecar.DROPPED_CONNECTION)));
        claim(
                "nothing was stored for it, so the next run asks the converter again",
                () -> assertThat(cachedAnswersFor(root, THE_FAILING_DOCUMENT)).isZero());
        claim(
                "neither dropped connection was counted towards stopping extraction for a converter that"
                        + " has stopped answering",
                () -> assertThat(lines()).noneMatch(line -> line.contains(BREAKER_COUNTED)));
    }

    @Test
    @Story("A converter that drops a connection and stays gone")
    @DisplayName("When the document converter drops a connection and does not answer its health check again in time, extraction fails, says the converter did not come back, and removes no document on the strength of it")
    void aConverterThatDoesNotComeBackFailsTheStep(@TempDir Path root) throws IOException {
        writeTheCorpus(root);
        sidecar.dropping(THE_FAILING_DOCUMENT, LoopbackSidecar.EVERY_CALL);
        sidecar.stoppingItsHealthCheckWithADrop();

        cli.run("run", root.toString());

        claim(
                "the invocation failed, because extraction did",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "the closing line says extraction failed, that the converter dropped the connection and did"
                        + " not answer its health check again within the " + WAIT_AT_MOST.toSeconds()
                        + " second(s) this test waits, and to run the same command again",
                () -> assertThat(lines())
                        .anyMatch(line -> line.startsWith(STAGE_2_FAILED)
                                && line.contains("docling-serve dropped the connection and did not answer its"
                                        + " health check again within " + WAIT_AT_MOST.toSeconds() + " seconds")
                                && line.contains(RUN_THE_SAME_COMMAND_AGAIN)));
        claim(
                "document " + THE_FAILING_DOCUMENT + " was posted once: with the converter gone, nothing"
                        + " was asked again",
                () -> assertThat(sidecar.callsPerDocument()).containsEntry(THE_FAILING_DOCUMENT, ONCE));
        claim(
                "no document was removed: a converter that is gone says nothing about any file",
                () -> assertThat(extractionFailedReasons(root)).isEmpty());
    }

    @Test
    @Story("The list of files extraction could not read")
    @DisplayName("When extraction ends having removed a document it could not read, it writes a page listing that document and why, and says how many there are and where the page is")
    void theFilesExtractionCouldNotReadAreListedForReview(@TempDir Path root) throws IOException {
        writeTheCorpus(root);
        sidecar.rejecting(THE_FAILING_DOCUMENT, NOT_FOUND);

        cli.run("run", root.toString());

        Path page = workingDirectory.resolve(THE_REVIEW_LIST);
        claim(
                "the page was written beside the database",
                () -> assertThat(page).isRegularFile());
        String written = Files.readString(page);
        claim(
                "it lists document " + THE_FAILING_DOCUMENT + " by its path under the folder that was read,"
                        + " with the reason it was removed for, and none of the " + (DOCUMENTS - 1)
                        + " documents that were converted",
                () -> assertThat(written)
                        .contains(nameOf(THE_FAILING_DOCUMENT))
                        .contains("rejected: docling-serve answered HTTP " + NOT_FOUND)
                        .doesNotContain(nameOf(THE_FAILING_DOCUMENT + 1)));
        claim(
                "it says each was marked and skipped, that they stay removed under this run, and which of"
                        + " them the next run of the stage asks the converter about again: only those the"
                        + " converter gave no answer about",
                () -> assertThat(written)
                        .contains("Each was marked and skipped; the run went on.")
                        .contains("They stay removed under this run.")
                        .contains("A file whose reason begins with rejected, crashed the converter, timeout,"
                                + " capacity, target_unavailable or internal left nothing stored, so the next"
                                + " run of this stage asks the converter about it again."));
        claim(
                "and the log says that " + ONCE + " file could not be read, and where the list is",
                () -> assertThat(lines())
                        .contains("Stage 2 (extraction): " + ONCE + " file(s) could not be read; they are listed in "
                                + page.toAbsolutePath()));
    }

    @Test
    @Story("The list of files extraction could not read")
    @DisplayName("When extraction ends having read every document, it still writes the page, which says no file failed, and the log says none could not be read")
    void aStageThatReadEverythingStillWritesTheList(@TempDir Path root) throws IOException {
        writeTheCorpus(root);

        cli.run("run", root.toString());

        Path page = workingDirectory.resolve(THE_REVIEW_LIST);
        claim(
                "the page was written, and says no file failed, naming none of the " + DOCUMENTS
                        + " documents",
                () -> assertThat(page).isRegularFile().content().contains("No file failed.").doesNotContain("document-"));
        claim(
                "and the log says that " + NONE + " files could not be read",
                () -> assertThat(lines())
                        .contains("Stage 2 (extraction): " + NONE + " file(s) could not be read; they are listed in "
                                + page.toAbsolutePath()));
    }

    @Test
    @Story("The list of files extraction could not read")
    @DisplayName("A document the converter refused for a reason of its own, set aside during extraction and removed when it completed, is on the page too")
    @Link(name = "ADR-139", url = Adr.A_REFUSED_CONVERSION_LEAVES_A_FAULT_ROW, type = "adr")
    void aDocumentRemovedAtTheEndOfTheStepIsListedToo(@TempDir Path root) throws IOException {
        writeTheCorpus(root);
        sidecar.answering(THE_FAILING_DOCUMENT, REFUSED_FOR_CAPACITY);

        cli.run("run", root.toString());

        claim(
                "extraction completed having set " + ONCE + " document aside, the one the converter refused"
                        + " for want of capacity",
                () -> assertThat(lines())
                        .anyMatch(line -> line.startsWith(STAGE_2_FINISHED) && line.contains("skipped=" + ONCE)));
        claim(
                "that document was removed only once the step had completed, and the page, written after"
                        + " that, lists it with its reason",
                () -> assertThat(workingDirectory.resolve(THE_REVIEW_LIST))
                        .content()
                        .contains(nameOf(THE_FAILING_DOCUMENT))
                        .contains("capacity: " + NO_CAPACITY));
        claim(
                "and the log counts it: " + ONCE + " file could not be read",
                () -> assertThat(lines())
                        .anyMatch(line -> line.startsWith(
                                "Stage 2 (extraction): " + ONCE + " file(s) could not be read")));
    }

    @Test
    @Story("A converter that answers its health check and drops every conversion")
    @DisplayName("When the document converter drops the connection twice under each of five documents in a row while still answering its health check, extraction stops, says so, and removes none of them")
    void aConverterThatDropsEveryDocumentStopsTheStep(@TempDir Path root) throws IOException {
        writeTheCorpus(root, MORE_THAN_FIVE);
        sidecar.droppingEveryCall();

        cli.run("run", root.toString());

        claim(
                "the invocation failed: a converter that drops every conversion is not a fact about any file",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "the closing line says extraction failed because the converter dropped the connection twice"
                        + " on each of " + FIVE_IN_A_ROW + " files in a row while still answering its health"
                        + " check, and to run the same command again",
                () -> assertThat(lines())
                        .anyMatch(line -> line.startsWith(STAGE_2_FAILED)
                                && line.contains("docling-serve dropped the connection twice on each of "
                                        + FIVE_IN_A_ROW + " files in a row while still answering its health check")
                                && line.contains(RUN_THE_SAME_COMMAND_AGAIN)));
        claim(
                "a line names the " + FIVE_IN_A_ROW + " documents by their paths under the folder that was"
                        + " read, so they can be found and moved, and does not name document "
                        + MORE_THAN_FIVE + ", which was never reached",
                () -> assertThat(lines())
                        .anyMatch(line -> line.contains("each dropped the connection twice")
                                && IntStream.rangeClosed(1, FIVE_IN_A_ROW)
                                        .allMatch(document -> line.contains(nameOf(document)))
                                && !line.contains(nameOf(MORE_THAN_FIVE))));
        claim(
                "none of the " + MORE_THAN_FIVE + " documents was removed: the " + (FIVE_IN_A_ROW - 1)
                        + " before the one that stopped the stage were given back with it",
                () -> assertThat(extractionFailedReasons(root)).isEmpty());
    }

    @Test
    @Story("A converter that answers its health check and drops every conversion")
    @DisplayName("Four documents in a row that drop the connection twice, a document that converts, and four more do not stop extraction: only five in a row do")
    void anAnswerBetweenThemEndsTheRunOfDroppedDocuments(@TempDir Path root) throws IOException {
        writeTheCorpus(root, FOUR_ONE_FOUR);
        for (int document = 1; document <= FOUR_ONE_FOUR; document++) {
            if (document != THE_ONE_THAT_CONVERTS) {
                sidecar.dropping(document, LoopbackSidecar.EVERY_CALL);
            }
        }

        cli.run("run", root.toString());

        claim(
                "extraction completed: " + (FIVE_IN_A_ROW - 1) + " documents dropped the connection twice,"
                        + " document " + THE_ONE_THAT_CONVERTS + " was converted, and " + (FIVE_IN_A_ROW - 1)
                        + " more dropped it twice, which is never " + FIVE_IN_A_ROW + " in a row",
                () -> assertThat(lines()).anyMatch(line -> line.startsWith(STAGE_2_FINISHED)));
        claim(
                "every document but the one that converted was removed as one that crashed the converter",
                () -> assertThat(extractionFailedReasons(root))
                        .hasSize(FOUR_ONE_FOUR - 1)
                        .doesNotContainKey(nameOf(THE_ONE_THAT_CONVERTS))
                        .allSatisfy((document, reason) -> assertThat(reason).startsWith("crashed the converter:")));
    }

    @Test
    @Story("A converter that answers its health check and drops every conversion")
    @DisplayName("A document that runs out of time between them does not end the run of dropped documents: the fifth still stops extraction")
    void aTimeoutBetweenThemDoesNotEndTheRunOfDroppedDocuments(@TempDir Path root) throws IOException {
        writeTheCorpus(root, MORE_THAN_FIVE);
        for (int document = 1; document <= MORE_THAN_FIVE; document++) {
            if (document != THE_ONE_THAT_CONVERTS) {
                sidecar.dropping(document, LoopbackSidecar.EVERY_CALL);
            }
        }
        sidecar.rejecting(THE_ONE_THAT_CONVERTS, GATEWAY_TIMEOUT, CONVERSION_IS_TAKING_TOO_LONG);

        cli.run("run", root.toString());

        claim(
                "the invocation failed: document " + THE_ONE_THAT_CONVERTS + " ran out of time in place of"
                        + " converting, which brought no conversion back, so the document after it was the"
                        + " fifth in a row to drop the connection twice",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "and the closing line says extraction stopped for " + FIVE_IN_A_ROW + " files in a row",
                () -> assertThat(lines())
                        .anyMatch(line -> line.startsWith(STAGE_2_FAILED)
                                && line.contains("on each of " + FIVE_IN_A_ROW + " files in a row")));
    }

    @Test
    @Story("A converter that drops one connection and is back")
    @DisplayName("When the retry after a dropped connection is answered with an error status, the document is removed as rejected, and extraction goes on")
    void aRetryThatIsRejectedRemovesTheDocument(@TempDir Path root) throws IOException {
        writeTheCorpus(root);
        sidecar.dropping(THE_FAILING_DOCUMENT, 1);
        sidecar.rejecting(THE_FAILING_DOCUMENT, NOT_FOUND);

        cli.run("run", root.toString());

        claim(
                "extraction completed with nothing set aside",
                () -> assertThat(lines())
                        .anyMatch(line -> line.startsWith(STAGE_2_FINISHED) && line.contains(NOTHING_SKIPPED)));
        claim(
                "document " + THE_FAILING_DOCUMENT + " was posted " + TWICE + " times and removed for the"
                        + " error status its retry was answered with, not for the dropped connection",
                () -> {
                    assertThat(sidecar.callsPerDocument()).containsEntry(THE_FAILING_DOCUMENT, TWICE);
                    assertThat(extractionFailedReasons(root))
                            .containsOnlyKeys(nameOf(THE_FAILING_DOCUMENT))
                            .hasEntrySatisfying(nameOf(THE_FAILING_DOCUMENT), reason -> assertThat(reason)
                                    .startsWith("rejected: docling-serve answered HTTP " + NOT_FOUND));
                });
    }

    @Test
    @Story("A converter that drops one connection and is back")
    @DisplayName("When the retry after a dropped connection runs out of time, the document is removed as a timeout, once, and extraction goes on")
    void aRetryThatTimesOutRemovesTheDocumentAsATimeout(@TempDir Path root) throws IOException {
        writeTheCorpus(root);
        sidecar.rejecting(THE_ONE_BEFORE, GATEWAY_TIMEOUT, CONVERSION_IS_TAKING_TOO_LONG);
        sidecar.dropping(THE_FAILING_DOCUMENT, 1);
        sidecar.rejecting(THE_FAILING_DOCUMENT, GATEWAY_TIMEOUT, CONVERSION_IS_TAKING_TOO_LONG);

        cli.run("run", root.toString());

        claim(
                "extraction completed with nothing set aside. Document " + THE_ONE_BEFORE + " timed out,"
                        + " then document " + THE_FAILING_DOCUMENT + " was dropped once and timed out on its"
                        + " retry: two timeouts in a row. Had the dropped call been counted as a timeout"
                        + " there would be " + THREE_IN_A_ROW + ", and the third is set aside",
                () -> assertThat(lines())
                        .anyMatch(line -> line.startsWith(STAGE_2_FINISHED) && line.contains(NOTHING_SKIPPED)));
        claim(
                "both documents were removed as timeouts, and the reason says the converter gave up on"
                        + " each, which is what happened",
                () -> assertThat(extractionFailedReasons(root))
                        .containsOnlyKeys(nameOf(THE_ONE_BEFORE), nameOf(THE_FAILING_DOCUMENT))
                        .allSatisfy((document, reason) ->
                                assertThat(reason).startsWith("timeout: docling-serve gave up on ")));
    }

    @Test
    @Story("A converter that drops one connection and is back")
    @DisplayName("When the retry after a dropped connection is refused for a reason of the converter's own, the document is set aside like any other so refused")
    void aRetryTheConverterRefusesForItsOwnReasonIsSetAside(@TempDir Path root) throws IOException {
        writeTheCorpus(root);
        sidecar.dropping(THE_FAILING_DOCUMENT, 1);
        sidecar.answering(THE_FAILING_DOCUMENT, REFUSED_FOR_CAPACITY);

        cli.run("run", root.toString());

        claim(
                "extraction completed having set " + ONCE + " document aside",
                () -> assertThat(lines())
                        .anyMatch(line -> line.startsWith(STAGE_2_FINISHED) && line.contains("skipped=" + ONCE)));
        claim(
                "and that document was removed, once the stage had completed, for the converter's own"
                        + " reason and not for the dropped connection",
                () -> assertThat(extractionFailedReasons(root))
                        .containsOnlyKeys(nameOf(THE_FAILING_DOCUMENT))
                        .containsEntry(nameOf(THE_FAILING_DOCUMENT), "capacity: " + NO_CAPACITY));
    }

    @Test
    @Story("The list of files extraction could not read")
    @DisplayName("The page lists the documents by path whatever order they were removed in, and a later run of the same command writes it again with every one of them")
    void theListIsInPathOrderAndIsWrittenAgainByALaterInvocation(@TempDir Path root) throws IOException {
        writeTheCorpus(root);
        sidecar.answering(REMOVED_AT_THE_END, REFUSED_FOR_CAPACITY);
        sidecar.rejecting(REMOVED_ON_ITS_TURN, NOT_FOUND);

        cli.run("run", root.toString());

        Path page = workingDirectory.resolve(THE_REVIEW_LIST);
        String written = Files.readString(page);
        claim(
                "document " + REMOVED_AT_THE_END + " was removed last, when the stage completed, and is"
                        + " listed first, before document " + REMOVED_ON_ITS_TURN + ", because the page is in"
                        + " path order",
                () -> assertThat(written.indexOf(nameOf(REMOVED_AT_THE_END)))
                        .isNotNegative()
                        .isLessThan(written.indexOf(nameOf(REMOVED_ON_ITS_TURN))));

        Files.delete(page);
        logged.list.clear();
        Map<Integer, Long> postedByTheFirstRun = sidecar.callsPerDocument();
        cli.run("run", root.toString());

        claim(
                "running the same command again posts no document to the converter",
                () -> assertThat(sidecar.callsPerDocument()).isEqualTo(postedByTheFirstRun));
        claim(
                "and writes the page again with both documents the first run removed",
                () -> assertThat(page)
                        .isRegularFile()
                        .content()
                        .contains(nameOf(REMOVED_AT_THE_END))
                        .contains(nameOf(REMOVED_ON_ITS_TURN)));
        claim(
                "and the log counts both: " + BOTH + " files could not be read",
                () -> assertThat(lines())
                        .anyMatch(line -> line.startsWith(
                                "Stage 2 (extraction): " + BOTH + " file(s) could not be read")));
    }

    /**
     * The wait for the converter on a clock short enough to run. The shipped one waits three minutes,
     * checking every two seconds; a test that waits for a converter that never comes back cannot.
     */
    @TestConfiguration
    static class AShortWaitForTheConverter {

        @Bean
        @Primary
        SidecarRecovery sidecarRecoveryOnAShortClock(DoclingClient client) {
            return new SidecarRecovery(client, WAIT_AT_MOST, CHECK_EVERY);
        }
    }

    /** {@link #DOCUMENTS} files, each with text of its own so that none is a cache hit for another. */
    private static void writeTheCorpus(Path root) throws IOException {
        writeTheCorpus(root, DOCUMENTS);
    }

    private static void writeTheCorpus(Path root, int documents) throws IOException {
        for (int document = 1; document <= documents; document++) {
            Files.writeString(root.resolve(nameOf(document)), textOf(document, root));
        }
    }

    private static String nameOf(int document) {
        return "document-%02d.txt".formatted(document);
    }

    /** Names the corpus folder too, so that no test's document is a cache hit for another test's. */
    private static String textOf(int document, Path root) {
        return "corpus document %02d of %d, written for the dropped-connection test under %s"
                .formatted(document, DOCUMENTS, root.getFileName());
    }

    private List<String> lines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    /** Each document removed as one extraction failed on, with why, for this test's own walk. */
    private Map<String, String> extractionFailedReasons(Path root) {
        return jdbcTemplate
                .queryForList(
                        "SELECT o.path, v.reason FROM verdict v JOIN file_occurrence o ON o.id = v.occurrence_id"
                                + " JOIN walk w ON w.id = o.walk_id WHERE w.root = ? AND v.kind = 'EXTRACTION_FAILED'",
                        Walk.canonicalRoot(root).toString())
                .stream()
                .collect(Collectors.toMap(row -> (String) row.get("path"), row -> (String) row.get("reason")));
    }

    /** Stored answers whose text is {@code document}'s; the stored answer does not carry the text, the key does. */
    private long cachedAnswersFor(Path root, int document) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM extraction_cache WHERE content_hash = ?",
                Long.class,
                extractor.contentHashFor(root.resolve(nameOf(document))));
    }
}
