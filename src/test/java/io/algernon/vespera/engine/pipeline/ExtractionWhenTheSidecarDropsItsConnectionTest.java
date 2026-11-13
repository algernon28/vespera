package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.Walk;
import io.algernon.vespera.engine.extraction.DoclingClient;
import io.algernon.vespera.engine.extraction.DoclingExtractor;
import io.algernon.vespera.engine.extraction.ExtractionBeans;
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

    /** How many documents in a row may drop the connection twice before extraction stops. */
    private static final int FIVE_IN_A_ROW = 5;

    /** A corpus one document longer than that, so the stage stops with a document still unread. */
    private static final int MORE_THAN_FIVE = FIVE_IN_A_ROW + 1;

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
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(OperatorLines.LOGGER_NAME)).addAppender(logged);
    }

    @AfterEach
    void stopTheSidecarAndReleaseEveryLine() {
        rootLogger.detachAppender(logged);
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(OperatorLines.LOGGER_NAME)).detachAppender(logged);
        logged.stop();
        sidecar.close();
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
                "a line names " + FIVE_IN_A_ROW + " of the " + MORE_THAN_FIVE + " documents by their paths"
                        + " under the folder that was read, so they can be found and moved: the "
                        + FIVE_IN_A_ROW + " that were read, and not the one that was never reached",
                () -> assertThat(lines())
                        .filteredOn(line -> line.contains("each dropped the connection twice"))
                        .singleElement()
                        .satisfies(line -> assertThat(IntStream.rangeClosed(1, MORE_THAN_FIVE)
                                        .filter(document -> line.contains(nameOf(document)))
                                        .count())
                                .isEqualTo(FIVE_IN_A_ROW)));
        claim(
                "none of the " + MORE_THAN_FIVE + " documents was removed: the " + (FIVE_IN_A_ROW - 1)
                        + " before the one that stopped the stage were given back with it",
                () -> assertThat(extractionFailedReasons(root)).isEmpty());
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
