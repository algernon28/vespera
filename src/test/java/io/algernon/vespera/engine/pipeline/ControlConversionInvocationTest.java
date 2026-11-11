package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.Walk;
import io.algernon.vespera.engine.extraction.DoclingClient;
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
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.provider.Arguments;
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
 * What stage 2 does when five file occurrences in a row fail the same way (ADR-184, #385, #393): it
 * sends the converter the control conversion, alone, and stops only if that does not come back
 * converted. Five files that each kill the converter, or that it refuses every time, are then the files'
 * own failures and are marked; a converter that converts nothing still stops the step.
 *
 * <p>The sidecar is real HTTP: the production {@code DoclingClient}, extractor and cache (through
 * {@link ExtractionBeans}) against a {@link LoopbackSidecar}, which recognises the control conversion by
 * the sentence its PDF carries and, unless a test scripts otherwise, converts it.
 *
 * <p>"In a row" is the order stage 2 reads in, which is the order the walk recorded the folder in: the
 * file system's, not the names' (sorted on NTFS, arbitrary on Linux). Every test that places failures
 * by position learns that order first, from an invocation that walks the folder and stops before it
 * converts anything.
 *
 * <p>Every test here fails against the code this record was written over, at {@code e82edeb}: the step
 * stops where it should go on, completes where it should stop, or stops without ever sending the control
 * conversion; and the control conversion's PDF is not shipped.
 */
@CascadeSliceTest
@Import(ExtractionBeans.class)
@Epic("Extraction")
@Feature("Stage 2 step")
@Link(name = "ADR-184", url = Adr.FIVE_FAILURES_IN_A_ROW_STOP_STAGE_2_ONLY_AFTER_A_FAILED_CONTROL_CONVERSION, type = "adr")
@Link(name = "ADR-175", url = Adr.A_FILE_THAT_FAILS_IS_MARKED_AND_SKIPPED, type = "adr")
@Link(name = "ADR-071", url = Adr.DOCLING_INVOCATION_CONTRACT_IS_ONE_SYNC_CALL, type = "adr")
class ControlConversionInvocationTest {

    /** A loopback port that was free when the class loaded; the test's sidecar listens on it. */
    private static final int SIDECAR_PORT = LoopbackSidecar.aFreePort();

    /** Where the client looks for the sidecar. */
    private static final String SIDECAR = "http://127.0.0.1:" + SIDECAR_PORT;

    /** How long these tests wait for a converter that dropped a connection to answer its health check. */
    private static final Duration WAIT_AT_MOST = Duration.ofSeconds(1);

    /** How often they ask it meanwhile. */
    private static final Duration CHECK_EVERY = Duration.ofMillis(50);

    /** How many failures in a row send the control conversion: the count both stops have always used. */
    private static final int FIVE_IN_A_ROW = 5;

    /** Two rows of five, so the count has to start again after the first control conversion. */
    private static final int TEN_IN_A_ROW = 2 * FIVE_IN_A_ROW;

    /** Ten files that kill the converter, with one that converts before them and one after. */
    private static final int TEN_BETWEEN_TWO = TEN_IN_A_ROW + 2;

    /** A corpus one file longer than a row of five, so the stage stops with a file still unread. */
    private static final int MORE_THAN_FIVE = FIVE_IN_A_ROW + 1;

    /** How often the converter is sent the control conversion when one row of five is reached. */
    private static final long ONCE = 1;

    /** How often it is sent the control conversion for two rows of five. */
    private static final long TWICE = 2;

    /** How stage 2's closing line opens when the step completed. */
    private static final String STAGE_2_FINISHED = "Stage 2 (extraction) finished";

    /** How stage 2's closing line opens when the step did not complete. */
    private static final String STAGE_2_FAILED = "Stage 2 (extraction) failed and is not recorded as finished";

    /** What the stop now says about the control conversion, after what it says about the five. */
    private static final String AND_NOT_THE_CONTROL_EITHER = ", and did not convert the control document either";

    /** What the stop for files that drop the connection says. */
    private static final String FIVE_DROPPED_TWICE = "docling-serve dropped the connection twice on each of "
            + FIVE_IN_A_ROW + " files in a row while still answering its health check";

    /** What the stop for refusals the converter blames on itself says. */
    private static final String FIVE_SET_ASIDE =
            "the extractor set aside " + FIVE_IN_A_ROW + " occurrences in a row without converting any of them";

    /** The line written when the control conversion converted and the stage goes on. */
    private static final String THE_CONTROL_CONVERTED = "Stage 2 (extraction): the converter converted the control"
            + " document after " + FIVE_IN_A_ROW + " files in a row failed, so each failure is the file's own and"
            + " the stage goes on";

    /** What the converter says when it refuses a file for want of room. */
    private static final String NO_CAPACITY = "no worker was free";

    /** A refusal the converter blames on itself, in the wire shape it answers with. */
    private static final String REFUSED_FOR_CAPACITY = "{\"status\":\"failure\",\"errors\":[{"
            + "\"component_type\":\"pipeline\",\"module_name\":\"docling.pipeline\","
            + "\"error_message\":\"" + NO_CAPACITY + "\",\"category\":\"capacity\"}],"
            + "\"processing_time\":0.1}";

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
    @Issue("393")
    @Story("Files side by side that each crash the converter")
    @DisplayName("Ten files in a row that each crash the converter are each removed, because the converter still converts Vespera's own control PDF after every five of them, and the stage completes")
    void tenOccurrencesInARowThatEachCrashTheConverterAreRemovedAndTheStageCompletes(@TempDir Path root)
            throws IOException {
        writeTheCorpus(root, TEN_BETWEEN_TWO);
        List<Integer> asRead = theOrderTheStageReadsIn(root);
        List<Integer> killers = asRead.subList(1, 1 + TEN_IN_A_ROW);
        killers.forEach(file -> sidecar.dropping(file, LoopbackSidecar.EVERY_CALL));

        cli.run("run", root.toString());

        claim(
                "extraction completed: " + TEN_IN_A_ROW + " files in a row crashed the converter, and it still"
                        + " converted the control PDF after each " + FIVE_IN_A_ROW + " of them",
                () -> assertThat(lines()).anyMatch(line -> line.startsWith(STAGE_2_FINISHED)));
        claim(
                "the control PDF was sent " + TWICE + " times, once for each row of " + FIVE_IN_A_ROW
                        + ": the count started again after the first one converted",
                () -> assertThat(sidecar.controlConversions()).isEqualTo(TWICE));
        claim(
                "and a line said so each time",
                () -> assertThat(lines()).filteredOn(THE_CONTROL_CONVERTED::equals).hasSize((int) TWICE));
        claim(
                "exactly the " + TEN_IN_A_ROW + " files that crashed the converter were removed, each with that"
                        + " as its reason, and neither file read before or after them",
                () -> assertThat(extractionFailedReasons(root))
                        .containsOnlyKeys(killers.stream().map(ControlConversionInvocationTest::nameOf).toList())
                        .allSatisfy((file, reason) -> assertThat(reason).startsWith("crashed the converter:")));
    }

    /** The two ways a converter converts nothing, each with what the closing line says of the five in a row. */
    static Stream<Arguments> convertersThatConvertNothing() {
        Consumer<LoopbackSidecar> dropping = LoopbackSidecar::droppingEveryCall;
        Consumer<LoopbackSidecar> refusing = sidecar -> sidecar.answeringEveryCall(REFUSED_FOR_CAPACITY);
        return Stream.of(
                Arguments.of(Named.of("a converter that drops every call", dropping), FIVE_DROPPED_TWICE),
                Arguments.of(Named.of("a converter that refuses every call for want of room", refusing), FIVE_SET_ASIDE));
    }

    @Test
    @Issue("393")
    @Story("A converter that answers its health check and converts nothing")
    @DisplayName("An answer to Vespera's own control PDF that does not carry the PDF's text does not count as converting it, and extraction stops")
    void aControlConversionAnsweredWithoutItsSentenceDidNotConvert(@TempDir Path root) throws IOException {
        writeTheCorpus(root, MORE_THAN_FIVE);
        for (int file = 1; file <= MORE_THAN_FIVE; file++) {
            sidecar.dropping(file, LoopbackSidecar.EVERY_CALL);
        }
        sidecar.answeringTheControlConversion(LoopbackSidecar.CONVERTED);

        cli.run("run", root.toString());

        claim(
                "the control PDF was sent " + ONCE + " time and answered with a conversion of some other text",
                () -> assertThat(sidecar.controlConversions()).isEqualTo(ONCE));
        claim(
                "the invocation failed: a converter that answers without having read the control PDF has not"
                        + " shown that it converts",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "and the closing line says the control PDF was not converted",
                () -> assertThat(lines())
                        .anyMatch(line -> line.startsWith(STAGE_2_FAILED)
                                && line.contains(FIVE_DROPPED_TWICE + AND_NOT_THE_CONTROL_EITHER)));
        claim(
                "no file was removed",
                () -> assertThat(extractionFailedReasons(root)).isEmpty());
    }

    /**
     * The wait for the converter on a clock short enough to run. The shipped one waits three minutes,
     * checking every two seconds.
     */
    @TestConfiguration
    static class AShortWaitForTheConverter {

        @Bean
        @Primary
        SidecarRecovery sidecarRecoveryOnAShortClock(DoclingClient client) {
            return new SidecarRecovery(client, WAIT_AT_MOST, CHECK_EVERY);
        }
    }

    /**
     * The files of {@code root}, by number, in the order extraction reads them: the order the folder was
     * listed in when it was first walked, which is the file system's and not the names'. Learnt from an
     * invocation that walks the folder and stops before it converts anything, because the converter is
     * not answering its health check.
     */
    private List<Integer> theOrderTheStageReadsIn(Path root) {
        sidecar.answeringItsHealthCheck(false);
        cli.run("run", root.toString());
        sidecar.answeringItsHealthCheck(true);
        logged.list.clear();
        return jdbcTemplate
                .queryForList(
                        "SELECT o.path FROM file_occurrence o JOIN walk w ON w.id = o.walk_id WHERE w.root = ?"
                                + " ORDER BY o.id",
                        String.class,
                        Walk.canonicalRoot(root).toString())
                .stream()
                .map(path -> Integer.parseInt(path.replaceAll("\\D", "")))
                .toList();
    }

    private static void writeTheCorpus(Path root, int files) throws IOException {
        for (int file = 1; file <= files; file++) {
            Files.writeString(root.resolve(nameOf(file)), textOf(file, files, root));
        }
    }

    private static String nameOf(int file) {
        return "lighthouse-log-%02d.txt".formatted(file);
    }

    /**
     * Says which file it is in the words {@link LoopbackSidecar} reads, and names the corpus folder, so
     * that no test's file is answered from storage for another test's.
     */
    private static String textOf(int file, int files, Path root) {
        return "lighthouse log, document %02d of %d, kept for the control conversion test under %s"
                .formatted(file, files, root.getFileName());
    }

    private List<String> lines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    /** Each file removed as one extraction failed on, with why, for this test's own walk. */
    private Map<String, String> extractionFailedReasons(Path root) {
        return jdbcTemplate
                .queryForList(
                        "SELECT o.path, v.reason FROM verdict v JOIN file_occurrence o ON o.id = v.occurrence_id"
                                + " JOIN walk w ON w.id = o.walk_id WHERE w.root = ? AND v.kind = 'EXTRACTION_FAILED'",
                        Walk.canonicalRoot(root).toString())
                .stream()
                .collect(Collectors.toMap(row -> (String) row.get("path"), row -> (String) row.get("reason")));
    }
}
