package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.extraction.DoclingClient;
import io.algernon.vespera.extraction.ExtractionBeans;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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

    /** A corpus in which five refusals sit at every other position, never two of them side by side. */
    private static final int TEN = 10;

    /** Four that drop, the one answered from storage, and four more that drop. */
    private static final int FOUR_ONE_FOUR = 2 * (FIVE_IN_A_ROW - 1) + 1;

    /** The position, in the order the stage reads, of the one file between two rows of four. */
    private static final int THE_MIDDLE = FIVE_IN_A_ROW - 1;

    /** The position, in read order, of the one file that drops the connection among refusals. */
    private static final int THIRD = 2;

    /** How often the converter is sent the control conversion when one row of five is reached. */
    private static final long ONCE = 1;

    /** How often it is sent the control conversion for two rows of five. */
    private static final long TWICE = 2;

    /** How often a file nothing new was asked about is posted. */
    private static final long NOT_AT_ALL = 0;

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

    /** Where the control conversion's PDF is shipped. */
    private static final String THE_CONTROL_PDF = "io/algernon/vespera/pipeline/control-conversion.pdf";

    /** How every PDF opens. */
    private static final String PDF_HEADER = "%PDF-";

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
    }

    @AfterEach
    void stopTheSidecarAndReleaseEveryLine() {
        rootLogger.detachAppender(logged);
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

    @Test
    @Issue("393")
    @Story("A converter that answers its health check and converts nothing")
    @DisplayName("A converter that drops every call, Vespera's own control PDF included, still stops extraction after five files in a row, says the control PDF failed too, and removes nothing")
    void aConverterThatDropsTheControlConversionTooStillStopsTheStage(@TempDir Path root) throws IOException {
        writeTheCorpus(root, MORE_THAN_FIVE);
        sidecar.droppingEveryCall();

        cli.run("run", root.toString());

        claim(
                "the invocation failed: a converter that converts nothing is not a fact about any file",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "the control PDF was sent " + ONCE + " time, after the fifth file in a row",
                () -> assertThat(sidecar.controlConversions()).isEqualTo(ONCE));
        claim(
                "the closing line says " + FIVE_IN_A_ROW + " files in a row dropped the connection twice and"
                        + " that the control PDF was not converted either",
                () -> assertThat(lines())
                        .anyMatch(line -> line.startsWith(STAGE_2_FAILED)
                                && line.contains(FIVE_DROPPED_TWICE + AND_NOT_THE_CONTROL_EITHER)));
        claim(
                "no file was removed",
                () -> assertThat(extractionFailedReasons(root)).isEmpty());
    }

    @Test
    @Issue("385")
    @Story("A resume that reads its refused files again together")
    @DisplayName("Five files the converter refuses every time, spread through a completed extraction and read again side by side when it resumes, are removed again, and the resume completes")
    void fiveRepeatRefusalsReadTogetherOnAResumeAreRemovedAndTheResumeCompletes(@TempDir Path root)
            throws IOException {
        writeTheCorpus(root, TEN);
        List<Integer> asRead = theOrderTheStageReadsIn(root);
        List<Integer> refused = List.of(asRead.get(0), asRead.get(2), asRead.get(4), asRead.get(6), asRead.get(8));
        refused.forEach(file -> sidecar.answering(file, REFUSED_FOR_CAPACITY));

        cli.run("run", root.toString());
        String run = onlyExtractionRunOf(root);

        claim(
                "the first invocation completes extraction: the " + refused.size() + " refusals sit at every"
                        + " other position, so a converted file always came between two of them",
                () -> {
                    assertThat(stageTwoFinished(run)).isTrue();
                    assertThat(extractionFailedReasons(root)).hasSize(refused.size());
                });

        jdbcTemplate.update("DELETE FROM finished_step WHERE run_id = ? AND step = ?", run, StepNames.EXTRACTION);
        Map<Integer, Long> postedBeforeTheResume = sidecar.callsPerDocument();
        logged.list.clear();
        cli.run("run", root.toString());
        Map<Integer, Long> postedByTheResume = postedSince(postedBeforeTheResume);

        claim(
                "with the record that extraction finished gone, the resume reads those " + refused.size()
                        + " files again, one after another, and completes",
                () -> {
                    assertThat(lines()).anyMatch(line -> line.startsWith(STAGE_2_FINISHED));
                    assertThat(stageTwoFinished(run)).isTrue();
                });
        claim(
                "it posted each of the " + refused.size() + " once, the control PDF " + ONCE + " time after the"
                        + " fifth, and no other file",
                () -> {
                    assertThat(postedByTheResume).containsEntry(LoopbackSidecar.CONTROL, ONCE);
                    refused.forEach(file -> assertThat(postedByTheResume).containsEntry(file, ONCE));
                    assertThat(postedByTheResume).hasSize(refused.size() + 1);
                });
        claim(
                "and the " + refused.size() + " are removed again, each for the converter's want of room, as"
                        + " the first invocation removed them",
                () -> assertThat(extractionFailedReasons(root))
                        .containsOnlyKeys(refused.stream().map(ControlConversionInvocationTest::nameOf).toList())
                        .allSatisfy((file, reason) -> assertThat(reason).isEqualTo("capacity: " + NO_CAPACITY)));
    }

    @Test
    @Issue("385")
    @Story("A converter that answers its health check and converts nothing")
    @DisplayName("A converter that refuses every call for want of room, Vespera's own control PDF included, still stops extraction after five files in a row and says the control PDF failed too")
    void aConverterThatRefusesTheControlConversionTooStillStopsTheStage(@TempDir Path root) throws IOException {
        writeTheCorpus(root, MORE_THAN_FIVE);
        sidecar.answeringEveryCall(REFUSED_FOR_CAPACITY);

        cli.run("run", root.toString());

        claim(
                "the invocation failed",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "the control PDF was sent " + ONCE + " time, after the fifth refusal in a row",
                () -> assertThat(sidecar.controlConversions()).isEqualTo(ONCE));
        claim(
                "the closing line says " + FIVE_IN_A_ROW + " files in a row were set aside and that the control"
                        + " PDF was not converted either",
                () -> assertThat(lines())
                        .anyMatch(line -> line.startsWith(STAGE_2_FAILED)
                                && line.contains(FIVE_SET_ASIDE + AND_NOT_THE_CONTROL_EITHER)));
        claim(
                "no file was removed: a stopped extraction resolves none of what it set aside",
                () -> assertThat(extractionFailedReasons(root)).isEmpty());
    }

    @Test
    @Issue("393")
    @Story("A converter that answers its health check and converts nothing")
    @DisplayName("A file answered from storage between files that drop the connection does not end their row: with the converter converting nothing, extraction stops after five of them and removes nothing")
    void anAnswerFromStorageDoesNotEndARowOfDroppedConnections(@TempDir Path base) throws IOException {
        Path root = Files.createDirectory(base.resolve("read-with-one-stored"));
        writeTheCorpus(root, FOUR_ONE_FOUR);
        List<Integer> asRead = theOrderTheStageReadsIn(root);
        int stored = asRead.get(THE_MIDDLE);
        Path elsewhere = Files.createDirectory(base.resolve("stored-from-here-first"));
        Files.copy(root.resolve(nameOf(stored)), elsewhere.resolve(nameOf(stored)));
        cli.run("run", elsewhere.toString());
        long postedBefore = sidecar.callsPerDocument().getOrDefault(stored, NOT_AT_ALL);
        logged.list.clear();

        sidecar.droppingEveryCall();
        cli.run("run", root.toString());

        claim(
                "the invocation failed: " + (FIVE_IN_A_ROW - 1) + " files dropped the connection twice, the one"
                        + " read next was answered from storage, which says nothing about the converter now, and"
                        + " the one after it made " + FIVE_IN_A_ROW,
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "the file answered from storage was not posted",
                () -> assertThat(sidecar.callsPerDocument().getOrDefault(stored, NOT_AT_ALL)).isEqualTo(postedBefore));
        claim(
                "the closing line says " + FIVE_IN_A_ROW + " files in a row dropped the connection twice",
                () -> assertThat(lines())
                        .anyMatch(line -> line.startsWith(STAGE_2_FAILED) && line.contains(FIVE_DROPPED_TWICE)));
        claim(
                "no file was removed",
                () -> assertThat(extractionFailedReasons(root)).isEmpty());
    }

    @Test
    @Issue("393")
    @Story("A converter that answers its health check and converts nothing")
    @DisplayName("A file that drops the connection twice between refusals does not end their row: with the converter refusing everything, extraction stops after the fifth refusal")
    void aDroppedConnectionDoesNotEndARowOfRefusals(@TempDir Path root) throws IOException {
        writeTheCorpus(root, MORE_THAN_FIVE);
        List<Integer> asRead = theOrderTheStageReadsIn(root);
        sidecar.answeringEveryCall(REFUSED_FOR_CAPACITY);
        sidecar.dropping(asRead.get(THIRD), LoopbackSidecar.EVERY_CALL);

        cli.run("run", root.toString());

        claim(
                "the invocation failed: two refusals, a file that dropped the connection twice, which is no"
                        + " answer about anything, and three more refusals make " + FIVE_IN_A_ROW,
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "the control PDF was sent " + ONCE + " time and refused like everything else",
                () -> assertThat(sidecar.controlConversions()).isEqualTo(ONCE));
        claim(
                "the closing line says " + FIVE_IN_A_ROW + " files in a row were set aside",
                () -> assertThat(lines())
                        .anyMatch(line -> line.startsWith(STAGE_2_FAILED) && line.contains(FIVE_SET_ASIDE)));
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

    @Test
    @Story("Vespera's own control PDF")
    @DisplayName("Vespera ships its control PDF, and the PDF carries its one sentence where the bytes sent show it")
    void theControlPdfIsShippedAndCarriesItsSentenceUncompressed() throws IOException {
        byte[] pdf;
        try (InputStream shipped = getClass().getClassLoader().getResourceAsStream(THE_CONTROL_PDF)) {
            claim(
                    "the control PDF is on the classpath at " + THE_CONTROL_PDF,
                    () -> assertThat(shipped).isNotNull());
            pdf = shipped.readAllBytes();
        }
        String bytes = new String(pdf, StandardCharsets.ISO_8859_1);
        claim(
                "it is a PDF: it opens with " + PDF_HEADER,
                () -> assertThat(bytes).startsWith(PDF_HEADER));
        claim(
                "and its one line of text, \"" + LoopbackSidecar.CONTROL_SENTENCE + "\", is written uncompressed,"
                        + " as a literal string, so the bytes sent carry it",
                () -> assertThat(bytes).contains("(" + LoopbackSidecar.CONTROL_SENTENCE + ")"));
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

    /** What was posted since {@code before}, per file, leaving out every file posted no more often. */
    private Map<Integer, Long> postedSince(Map<Integer, Long> before) {
        return sidecar.callsPerDocument().entrySet().stream()
                .filter(posted -> posted.getValue() > before.getOrDefault(posted.getKey(), NOT_AT_ALL))
                .collect(Collectors.toMap(
                        Map.Entry::getKey, posted -> posted.getValue() - before.getOrDefault(posted.getKey(), NOT_AT_ALL)));
    }

    /** The one stage-2 run over {@code root}, claimed to be the only one. */
    private String onlyExtractionRunOf(Path root) {
        List<String> runs = jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id WHERE w.root = ? AND r.stage = ?",
                String.class,
                Walk.canonicalRoot(root).toString(),
                StageModules.EXTRACTION.stage());
        claim(
                "extraction over this folder has one run so far, so every count below is about that run",
                () -> assertThat(runs).hasSize(1));
        return runs.getFirst();
    }

    private boolean stageTwoFinished(String run) {
        Long rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM finished_step WHERE run_id = ? AND step = ?",
                Long.class,
                run,
                StepNames.EXTRACTION);
        return rows != null && rows > NOT_AT_ALL;
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
