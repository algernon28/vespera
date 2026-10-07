package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.extraction.DoclingClient;
import io.algernon.vespera.extraction.ExtractionBeans;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
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
 * What stage 2 does with a file that stage 1 hashed and that is gone, or locked, by the time its call is
 * placed, and with a folder being read that is gone by then (ADR-210, #452).
 *
 * <p>The production {@code DoclingClient} posts a file it reads as it sends, so a file the file system no
 * longer hands over fails the call before any byte reaches the converter, and the client reports that as
 * a lost connection, as it reports a converter that closed the socket. ADR-210 tells the two apart on the
 * first such failure: it asks whether the folder being read can still be listed, and then whether the
 * file opens. A file that does not open earns its own reason, is not asked about again, waits for no
 * health check and is no evidence about the converter, so it neither ends nor extends either row of five
 * (ADR-184 section 4). A folder that cannot be listed stops the step and removes nothing.
 *
 * <p>Every file here is the same length, so stage 1 hashes all of them and stage 2 hashes none: the
 * failure each test makes is met where the call is placed, never where a file is hashed. The files are
 * taken away when stage 2 asks the converter for its health, which it does once, after stage 1 has
 * finished and before it reads anything (seam: {@link LoopbackSidecar#beforeEachHealthCheck}). Taking
 * them away between two invocations would not do: the next walk would see them gone, and they would not
 * be read at all. The whole folder, in the one test that takes it away, goes later: just before stage 2
 * places its first call ({@link AMomentBeforeTheFirstCallIsPlaced}), because a folder already gone when
 * stage 2 sets itself up fails the step before it reads anything, which is not this record's case.
 *
 * <p>The sidecar is real HTTP, as in {@link ExtractionWhenTheSidecarDropsItsConnectionTest}, whose
 * conventions this follows. Every test that needs files in a row learns the order stage 2 reads in
 * first, because that order is the file system's and not the names'.
 */
@CascadeSliceTest
@Import(ExtractionBeans.class)
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("452")
@Link(name = "ADR-210", url = Adr.A_FILE_THAT_CANNOT_BE_READ_IS_MARKED_AND_THE_STEP_GOES_ON, type = "adr")
@Link(name = "ADR-175", url = Adr.A_FILE_THAT_FAILS_IS_MARKED_AND_SKIPPED, type = "adr")
@Link(name = "ADR-184", url = Adr.FIVE_FAILURES_IN_A_ROW_STOP_STAGE_2_ONLY_AFTER_A_FAILED_CONTROL_CONVERSION, type = "adr")
class AFileGoneBeforeItIsSentInvocationTest {

    /** A loopback port that was free when the class loaded; the test's sidecar listens on it. */
    private static final int SIDECAR_PORT = LoopbackSidecar.aFreePort();

    /** Where the client looks for the sidecar. */
    private static final String SIDECAR = "http://127.0.0.1:" + SIDECAR_PORT;

    /** How many files in a row stop extraction, when the converter then fails the control conversion too. */
    private static final int FIVE_IN_A_ROW = 5;

    /** One file more than that, so a row of five and one file besides it can both be arranged. */
    private static final int SIX_FILES = FIVE_IN_A_ROW + 1;

    /** Where, in the order stage 2 reads, the gone file sits among the others: two before it and three after. */
    private static final int THIRD_TO_BE_READ = 2;

    /** A folder of four files, every one sent when the folder is gone. */
    private static final int FOUR_FILES = 4;

    /** The one health check stage 2 makes before it reads anything: a gone file adds no wait to it. */
    private static final int ONLY_THE_CHECK_BEFORE_THE_STAGE = 1;

    /** How often a file nothing went wrong with is posted. */
    private static final long ONCE = 1;

    /** How long these tests wait for a converter that dropped a connection to answer its health check. */
    private static final Duration WAIT_AT_MOST = Duration.ofSeconds(1);

    /** How often they ask it meanwhile. */
    private static final Duration CHECK_EVERY = Duration.ofMillis(50);

    /** How the reason begins for a file stage 2 could not read off the disk. */
    private static final String COULD_NOT_BE_READ = "could not be read: ";

    /** How the reason begins for a file whose call dropped the connection twice. */
    private static final String CRASHED_THE_CONVERTER = "crashed the converter:";

    /** How stage 2's closing line opens when the step completed. */
    private static final String STAGE_2_FINISHED = "Stage 2 (extraction) finished";

    /** How stage 2's closing line opens when the step did not complete. */
    private static final String STAGE_2_FAILED = "Stage 2 (extraction) failed and is not recorded as finished";

    /** What the closing line tells the operator when the folder being read is gone. */
    private static final String RECONNECT = "Reconnect the archive and run the same command again.";

    /** The words of the line that names the files of a row of five that each dropped the connection twice. */
    private static final String NAMES_THE_FIVE = "each dropped the connection twice";

    /** A refusal the converter blames on itself, in the wire shape it answers with. */
    private static final String REFUSED_FOR_CAPACITY = "{\"status\":\"failure\",\"errors\":[{"
            + "\"component_type\":\"pipeline\",\"module_name\":\"docling.pipeline\","
            + "\"error_message\":\"no worker was free\",\"category\":\"capacity\"}],"
            + "\"processing_time\":0.1}";

    /** The closing words of the breaker's stop once the control conversion failed too. */
    private static final String THE_BREAKERS_STOP =
            "set aside 5 occurrences in a row without converting any of them, and did not convert the control"
                    + " document either";

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
    @Story("A file that is gone by the time it is sent")
    @DisplayName("Five files that are gone by the time they are sent are each removed as files that could not be read, without being asked about again, without waiting for the converter, and without being counted against it")
    void filesGoneBeforeTheyAreSentAreMarkedAndNotCountedAgainstTheConverter(@TempDir Path parent)
            throws IOException {
        Path root = theFolderBeingRead(parent);
        writeTheFiles(root, SIX_FILES);
        List<Integer> asRead = theOrderTheStageReadsIn(root);
        List<Integer> gone = asRead.subList(0, FIVE_IN_A_ROW);
        int readLast = asRead.get(SIX_FILES - 1);
        // Were the five counted as files that crashed the converter, they would be five in a row, and a
        // control conversion that does not convert would then stop the stage.
        sidecar.droppingTheControlConversion();
        sidecar.beforeEachHealthCheck(() -> gone.forEach(file -> deleteIfThere(root.resolve(nameOf(file)))));
        int checksBefore = sidecar.healthChecks();

        cli.run("run", root.toString());

        claim(
                "extraction completed: " + FIVE_IN_A_ROW + " files that were gone did not stop it",
                () -> assertThat(lines()).anyMatch(line -> line.startsWith(STAGE_2_FINISHED)));
        claim(
                "each of the " + FIVE_IN_A_ROW + " was removed, and its reason says it could not be read, not"
                        + " that it crashed the converter",
                () -> assertThat(extractionFailedReasons(root))
                        .containsOnlyKeys(gone.stream().map(AFileGoneBeforeItIsSentInvocationTest::nameOf).toList())
                        .allSatisfy((file, reason) -> assertThat(reason)
                                .startsWith(COULD_NOT_BE_READ)
                                .doesNotStartWith(CRASHED_THE_CONVERTER)));
        claim(
                "the converter was never asked to convert the control document: files that could not be read"
                        + " say nothing about the converter, so they are not a row of failures to check it for",
                () -> assertThat(sidecar.controlConversions()).isZero());
        claim(
                "and nothing waited for the converter: its health was asked once, before the stage read"
                        + " anything, and never again after a file that could not be sent",
                () -> assertThat(sidecar.healthChecks() - checksBefore).isEqualTo(ONLY_THE_CHECK_BEFORE_THE_STAGE));
        claim(
                "the file read last, which was still there, was posted once and kept",
                () -> {
                    assertThat(sidecar.callsPerDocument()).containsEntry(readLast, ONCE);
                    assertThat(extractionFailedReasons(root)).doesNotContainKey(nameOf(readLast));
                });
    }

    @Test
    @Story("A file that is gone by the time it is sent")
    @DisplayName("A file that is gone by the time it is sent neither ends nor joins a row of files that each made the converter drop the connection twice")
    void aFileGoneBeforeItIsSentNeitherEndsNorJoinsARowOfDroppedFiles(@TempDir Path parent) throws IOException {
        Path root = theFolderBeingRead(parent);
        writeTheFiles(root, SIX_FILES);
        List<Integer> asRead = theOrderTheStageReadsIn(root);
        int gone = asRead.get(THIRD_TO_BE_READ);
        List<Integer> dropped = asRead.stream().filter(file -> file != gone).toList();
        dropped.forEach(file -> sidecar.dropping(file, LoopbackSidecar.EVERY_CALL));
        sidecar.droppingTheControlConversion();
        sidecar.beforeEachHealthCheck(() -> deleteIfThere(root.resolve(nameOf(gone))));

        cli.run("run", root.toString());

        claim(
                "extraction stopped for " + FIVE_IN_A_ROW + " files in a row: the gone file, read third, did"
                        + " not end the row the two before it began, so the three after it made five",
                () -> assertThat(lines())
                        .anyMatch(line -> line.startsWith(STAGE_2_FAILED)
                                && line.contains("on each of " + FIVE_IN_A_ROW + " files in a row")));
        claim(
                "and the line naming the " + FIVE_IN_A_ROW + " names the five that dropped the connection and"
                        + " not the gone file, which did not join the row either",
                () -> assertThat(lines())
                        .filteredOn(line -> line.contains(NAMES_THE_FIVE))
                        .singleElement()
                        .satisfies(line -> {
                            assertThat(line).doesNotContain(nameOf(gone));
                            assertThat(dropped).allSatisfy(file -> assertThat(line).contains(nameOf(file)));
                        }));
    }

    /**
     * Green before ADR-210 and after it: before, a file gone by the time it is sent was removed as one
     * that crashed the converter, which leaves the breaker's row as it is (ADR-184 section 4), and so does
     * a file that could not be read. It is here so that an implementation that counted such a file as an
     * answer, and so as the end of a row, fails.
     */
    @Test
    @Story("A file that is gone by the time it is sent")
    @DisplayName("A file that is gone by the time it is sent does not end a row of files the converter refused for a reason of its own")
    void aFileGoneBeforeItIsSentDoesNotEndARowOfRefusals(@TempDir Path parent) throws IOException {
        Path root = theFolderBeingRead(parent);
        writeTheFiles(root, SIX_FILES);
        List<Integer> asRead = theOrderTheStageReadsIn(root);
        int gone = asRead.get(THIRD_TO_BE_READ);
        asRead.stream().filter(file -> file != gone).forEach(file -> sidecar.answering(file, REFUSED_FOR_CAPACITY));
        sidecar.answeringTheControlConversion(REFUSED_FOR_CAPACITY);
        sidecar.beforeEachHealthCheck(() -> deleteIfThere(root.resolve(nameOf(gone))));

        cli.run("run", root.toString());

        claim(
                "extraction stopped because " + FIVE_IN_A_ROW + " files in a row were refused and the control"
                        + " document was refused too: the gone file, read third, did not end the row",
                () -> assertThat(lines())
                        .anyMatch(line -> line.startsWith(STAGE_2_FAILED) && line.contains(THE_BREAKERS_STOP)));
    }

    @Test
    @Story("The folder being read is gone")
    @DisplayName("When the folder being read is gone by the time its files are sent, extraction stops, names the folder, says to reconnect it, and removes nothing")
    void aFolderGoneBeforeItsFilesAreSentStopsTheStep(@TempDir Path parent) throws IOException {
        Path root = theFolderBeingRead(parent);
        writeTheFiles(root, FOUR_FILES);
        String canonicalRoot = Walk.canonicalRoot(root).toString();
        Path movedTo = parent.resolve("moved-away");
        AS_STAGE_2_LOOKS_UP_THE_FIRST_KEY.set(() -> moveIfThere(root, movedTo));

        cli.run("run", root.toString());

        claim(
                "the invocation failed: a folder that is gone is not a fact about any one file",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "the closing line says extraction failed, names the folder that can no longer be listed, and"
                        + " says to reconnect it and run the same command again",
                () -> assertThat(lines())
                        .anyMatch(line -> line.startsWith(STAGE_2_FAILED)
                                && line.contains("the corpus root " + canonicalRoot + " can no longer be listed")
                                && line.contains(RECONNECT)));
        claim(
                "no file was removed: none of the " + FOUR_FILES + " is marked for the folder having gone",
                () -> assertThat(extractionFailedReasons(canonicalRoot)).isEmpty());
        claim(
                "and nothing waited for the converter, which was never the problem: its health was asked once,"
                        + " before the stage read anything",
                () -> assertThat(sidecar.healthChecks()).isEqualTo(ONLY_THE_CHECK_BEFORE_THE_STAGE));
    }

    /**
     * The wait for the converter on a clock short enough to run. Nothing here stops answering its health
     * check, so it returns at once wherever it is reached; the shipped one would too.
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
     * The real record of content identity, and a moment just before stage 2 first looks up the key stage
     * 1 recorded for a file, which it does as it reads the file's occurrence and right before it places
     * the call. The folder being read cannot be taken away any earlier in stage 2: the step's own setup
     * resolves the folder's canonical path, and a folder already gone then fails the step before it reads
     * anything, a case ADR-210 leaves open.
     */
    @TestConfiguration
    static class AMomentBeforeTheFirstCallIsPlaced {

        @Bean
        @Primary
        ContentIdentity contentIdentityWithAMomentBeforeTheFirstKey(JdbcTemplate jdbcTemplate) {
            return new ContentIdentity(jdbcTemplate) {
                @Override
                public Optional<String> hashFor(OccurrenceId occurrenceId, RunId runId) {
                    AS_STAGE_2_LOOKS_UP_THE_FIRST_KEY.getAndSet(NOTHING).run();
                    return super.hashFor(occurrenceId, runId);
                }
            };
        }
    }

    private static final Runnable NOTHING = () -> {};

    /** Done once, the first time stage 2 looks up the key stage 1 recorded for a file. */
    private static final AtomicReference<Runnable> AS_STAGE_2_LOOKS_UP_THE_FIRST_KEY = new AtomicReference<>(NOTHING);

    /** Disarmed before as well as after: the seam is static, and which test ran last is not fixed. */
    @BeforeEach
    @AfterEach
    void disarm() {
        AS_STAGE_2_LOOKS_UP_THE_FIRST_KEY.set(NOTHING);
    }

    /** A folder of its own beneath {@code parent}, so a test can move the whole of it away and still clean up. */
    private static Path theFolderBeingRead(Path parent) throws IOException {
        return Files.createDirectory(parent.resolve("corpus"));
    }

    /**
     * The files of {@code root}, by number, in the order extraction reads them. That is the order the
     * folder was listed in when it was first walked, which is the file system's and not the names'. It is
     * learnt from an invocation that walks the folder, runs stage 1, which hashes every file here since all
     * are one length, and stops before it converts anything, because the converter is not answering its
     * health check. The next invocation finds the same walk and stage 1 finished (ADR-115).
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

    /** {@code files} files, each with text of its own and every one the same length, so stage 1 hashes each. */
    private static void writeTheFiles(Path root, int files) throws IOException {
        for (int file = 1; file <= files; file++) {
            Files.writeString(root.resolve(nameOf(file)), textOf(file, root));
        }
    }

    private static String nameOf(int file) {
        return "document-%02d.txt".formatted(file);
    }

    /**
     * Names the folder above the one being read, so that no test's file is a cache hit for another
     * test's; says {@code document N of}, which is how the sidecar tells the files apart.
     */
    private static String textOf(int file, Path root) {
        return "corpus document %02d of %d, written for the gone-before-it-is-sent test under %s"
                .formatted(file, SIX_FILES, root.getParent().getFileName());
    }

    private static void deleteIfThere(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void moveIfThere(Path from, Path to) {
        try {
            if (Files.exists(from)) {
                Files.move(from, to);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<String> lines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    /** Each file removed as one extraction failed on, with why, for this test's own walk. */
    private Map<String, String> extractionFailedReasons(Path root) {
        return extractionFailedReasons(Walk.canonicalRoot(root).toString());
    }

    /** The same, for a folder that may be gone, by the canonical path it was walked under. */
    private Map<String, String> extractionFailedReasons(String canonicalRoot) {
        return jdbcTemplate
                .queryForList(
                        "SELECT o.path, v.reason FROM verdict v JOIN file_occurrence o ON o.id = v.occurrence_id"
                                + " JOIN walk w ON w.id = o.walk_id WHERE w.root = ? AND v.kind = 'EXTRACTION_FAILED'",
                        canonicalRoot)
                .stream()
                .collect(Collectors.toMap(row -> (String) row.get("path"), row -> (String) row.get("reason")));
    }
}
