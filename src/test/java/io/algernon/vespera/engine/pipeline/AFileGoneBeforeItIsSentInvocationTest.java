package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.ContentIdentity;
import io.algernon.vespera.engine.corpus.Walk;
import io.algernon.vespera.engine.extraction.DoclingClient;
import io.algernon.vespera.engine.extraction.ExtractionBeans;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.RunId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
 * <p>One test does not take its files away: it shuts their contents away and leaves the files where
 * they are, their size still readable. They are texts too large to send whole, which are converted in
 * parts (ADR-178), and what stage 2 reads of such a file before it sends any part is its size, then its
 * first bytes, then the whole of it; a file gone by then is not cut at all and is sent whole, so a file
 * whose size reads and whose contents do not is how that last read is reached. On Windows a lock over
 * the whole file is held; elsewhere its read permission is removed, and the test is skipped where that
 * does not stop a read, as it does not for the superuser.
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

    /** How stage 2's closing line opens when the step completed. */
    private static final String STAGE_2_FINISHED = "Stage 2 (extraction) finished";

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
                                .startsWith(COULD_NOT_BE_READ)));
        claim(
                "the converter was never asked to convert the control document: files that could not be read"
                        + " say nothing about the converter, so they are not a row of failures to check it for",
                () -> assertThat(sidecar.controlConversions()).isZero());
        claim(
                "and nothing waited for the converter: its health was asked once, before the stage read"
                        + " anything, and never again after a file that could not be sent",
                () -> assertThat(sidecar.healthChecks() - checksBefore).isEqualTo(ONLY_THE_CHECK_BEFORE_THE_STAGE));
        claim(
                "the file read last, which was still there, was posted once (and, being none of the "
                        + FIVE_IN_A_ROW + " removed above, kept)",
                () -> assertThat(sidecar.callsPerDocument()).containsEntry(readLast, ONCE));
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

    /** What undoes each shutting away a test did, run after it so its folder can be cleaned up. */
    private final List<Runnable> reopenings = new CopyOnWriteArrayList<>();

    /** The files already shut away, so a second health check shuts nothing twice. */
    private final Set<Path> shut = ConcurrentHashMap.newKeySet();

    @AfterEach
    void reopenWhatWasShut() {
        reopenings.forEach(Runnable::run);
        reopenings.clear();
        shut.clear();
    }

    private static boolean posix() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
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
