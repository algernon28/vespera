package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.corpus.DetectedFormats;
import io.algernon.vespera.corpus.DetectedSubtype;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.extraction.ConversionStatus;
import io.algernon.vespera.extraction.DoclingClient;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.DoclingResponse;
import io.algernon.vespera.extraction.PathScriptedExtractor;
import io.algernon.vespera.extraction.SidecarVersionReport;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A file the file system will not hand over when stage 1 or stage 2 hashes it is marked, or left for the
 * next stage, and the step goes on; a folder being read that can no longer be listed stops the step and
 * removes nothing (ADR-210, #452).
 *
 * <p>Before ADR-210 such a file stopped the step: stage 2 failed on {@code could not hash <path>}, and
 * stage 1 failed and rolled the whole stage back (ADR-207 section 4). And a folder gone during stage 1's
 * first pass left every file after it {@code broken} and the stage finished.
 *
 * <p>Each moment at which a test takes something away has a seam of its own here, armed by the one test
 * that needs it and disarmed before and after every test, since the beans outlive a test and the order
 * tests run in is not fixed. Each acts once per arming, the first time it is reached, so a step that
 * reaches it again meets what was done and nothing more:
 *
 * <ul>
 *   <li>stage 2's hash of one named file: the extraction double's {@code beforeHashing}, the seam
 *       ADR-155's tests move a seed away with;
 *   <li>stage 1's hashing: {@code corpus}'s content-identity record, which runs after each file is
 *       hashed, so the next file of the same length meets what was done;
 *   <li>stage 1's first pass: {@code corpus}'s detected-format record, which runs after each file is
 *       checked, so the next file checked meets it.
 * </ul>
 *
 * <p>The folder being read is a folder of its own beneath a temporary one, so that a test can move the
 * whole of it away and the temporary folder is still cleaned up.
 */
@CascadeSliceTest
@Import(AFileThatCannotBeReadIsMarkedInvocationTest.InterruptibleArchiveBeans.class)
@ExtendWith(OutputCaptureExtension.class)
@Epic("Extraction")
@Feature("A file that cannot be read")
@Issue("452")
@Link(name = "ADR-210", url = Adr.A_FILE_THAT_CANNOT_BE_READ_IS_MARKED_AND_THE_STEP_GOES_ON, type = "adr")
class AFileThatCannotBeReadIsMarkedInvocationTest {

    /** The one file stage 2 hashes whose hashing a test can interrupt: its length is shared by no other file. */
    private static final String HASHED_ONLY_BY_STAGE_2 = "hashed-only-by-stage-2.txt";

    /** A file nothing happens to, of a length of its own. */
    private static final String AN_ORDINARY_FILE = "an-ordinary-file.txt";

    /** Two files of one length, so stage 1 hashes both (ADR-067). */
    private static final List<String> TWO_OF_ONE_LENGTH = List.of("same-length-1.txt", "same-length-2.txt");

    /** Three files of three lengths, so stage 1's first pass checks each and its hashing has none to hash. */
    private static final List<String> THREE_OF_THREE_LENGTHS = List.of("first.txt", "second-file.txt", "third-file-of-three.txt");

    /** How many files stage 1 hashes when two share a length: both, the one it could not read included. */
    private static final long BOTH = 2;

    /** How the reason begins for a file stage 2 could not read off the disk. */
    private static final String COULD_NOT_BE_READ = "could not be read: ";

    /** Stage 1's counter over the files it hashes. */
    private static final String CONTENT_HASH = "Stage 1 (byte-level reduction, content hash)";

    /** How stage 1's finishing line opens. */
    private static final String STAGE_1_FINISHED = "Stage 1 (byte-level reduction) finished under run ";

    /** How stage 1's line opens when the step did not complete. */
    private static final String STAGE_1_FAILED = "Stage 1 (byte-level reduction) failed and is not recorded as finished";

    /** How stage 2's closing line opens when the step completed. */
    private static final String STAGE_2_FINISHED = "Stage 2 (extraction) finished";

    /** How stage 2's closing line opens when the step did not complete. */
    private static final String STAGE_2_FAILED = "Stage 2 (extraction) failed and is not recorded as finished";

    /** What a closing line tells the operator when the folder being read is gone. */
    private static final String RECONNECT = "Reconnect the archive and run the same command again.";

    /** The step's own count of what it set aside, as its closing line reports it. */
    private static final String NOTHING_SET_ASIDE = "skipped=0";

    /** The start of the line the breaker writes for each failure it counts. */
    private static final String BREAKER_COUNTED = "service-scope failure on";

    /** The page stage 2 lists the files it could not read on, beside the database. */
    private static final String THE_REVIEW_LIST = "extraction-failures.html";

    private static final Consumer<Path> NOTHING_AT_THE_HASH = file -> {};
    private static final Consumer<String> NOTHING_AFTER_A_HASH = sha256 -> {};
    private static final Runnable NOTHING_AFTER_A_CHECK = () -> {};

    /**
     * Done once, to {@link #HASHED_ONLY_BY_STAGE_2}, just before stage 2 first hashes it. Stage 2 may hash
     * the file more than once, in its reader and again in its processor, and a second move of a file or a
     * folder already moved would fail inside the move itself; so a later hash does nothing here and meets
     * what the first one did: the file, or the whole folder, is still gone.
     */
    private static final AtomicReference<Consumer<Path>> AT_STAGE_2S_HASH = new AtomicReference<>(NOTHING_AT_THE_HASH);

    /** Done once, after stage 1 records the first hash it takes, given that hash. */
    private static final AtomicReference<Consumer<String>> AFTER_STAGE_1S_FIRST_HASH =
            new AtomicReference<>(NOTHING_AFTER_A_HASH);

    /** Done once, after stage 1's first pass records what the first file it checked is. */
    private static final AtomicReference<Runnable> AFTER_STAGE_1S_FIRST_CHECK =
            new AtomicReference<>(NOTHING_AFTER_A_CHECK);

    @TestConfiguration
    static class InterruptibleArchiveBeans {

        /** Real text and a title, so nothing here is removed for what the converter said. */
        private static final String WITH_TEXT = "{\"document\":{\"json_content\":{\"texts\":["
                + "{\"text\":\"A Stubbed Document\",\"label\":\"title\"},"
                + "{\"text\":\"stubbed but real content\"}]}}}";

        @Bean
        DoclingExtractor doclingExtractor(JdbcTemplate jdbcTemplate) {
            return new PathScriptedExtractor()
                    .cachingInto(jdbcTemplate)
                    .beforeHashing(
                            HASHED_ONLY_BY_STAGE_2,
                            file -> AT_STAGE_2S_HASH.getAndSet(NOTHING_AT_THE_HASH).accept(file))
                    .otherwiseAnswering(new DoclingResponse(ConversionStatus.SUCCESS, List.of(), 0d, null, WITH_TEXT));
        }

        /** Never reached over HTTP; the version map is what the extractor identity is composed from. */
        @Bean
        DoclingClient doclingClient(@Value("${vespera.docling.image}") String configuredImage) {
            return new DoclingClient("unused") {
                @Override
                public void checkHealth() {}

                @Override
                public Map<String, String> version() {
                    return SidecarVersionReport.runningImage(configuredImage);
                }
            };
        }

        /** The real record, and then whatever a test armed to happen after stage 1's first hash. */
        @Bean
        @Primary
        ContentIdentity contentIdentityWithAMomentAfterTheFirstHash(JdbcTemplate jdbcTemplate) {
            return new ContentIdentity(jdbcTemplate) {
                @Override
                public void recordHash(OccurrenceId occurrenceId, RunId runId, String sha256) {
                    super.recordHash(occurrenceId, runId, sha256);
                    AFTER_STAGE_1S_FIRST_HASH.getAndSet(NOTHING_AFTER_A_HASH).accept(sha256);
                }
            };
        }

        /** The real record, and then whatever a test armed to happen after stage 1's first check. */
        @Bean
        @Primary
        DetectedFormats detectedFormatsWithAMomentAfterTheFirstCheck(JdbcTemplate jdbcTemplate) {
            return new DetectedFormats(jdbcTemplate) {
                @Override
                public void record(OccurrenceId occurrenceId, RunId runId, DetectedFormat format, DetectedSubtype subtype) {
                    super.record(occurrenceId, runId, format, subtype);
                    AFTER_STAGE_1S_FIRST_CHECK.getAndSet(NOTHING_AFTER_A_CHECK).run();
                }
            };
        }
    }

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
    private ch.qos.logback.classic.Logger rootLogger;

    /** Disarmed before as well as after: the seams are static, and which test ran last is not fixed. */
    @BeforeEach
    void disarmAndCaptureEveryLine() {
        disarm();
        profileStore.save(ProfileFixture.profile().build());
        logged = new ListAppender<>();
        logged.start();
        rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(logged);
    }

    @AfterEach
    void disarmAndReleaseEveryLine() {
        disarm();
        rootLogger.detachAppender(logged);
        logged.stop();
    }

    private static void disarm() {
        AT_STAGE_2S_HASH.set(NOTHING_AT_THE_HASH);
        AFTER_STAGE_1S_FIRST_HASH.set(NOTHING_AFTER_A_HASH);
        AFTER_STAGE_1S_FIRST_CHECK.set(NOTHING_AFTER_A_CHECK);
    }

    @Test
    @Story("A file stage 2 cannot hash")
    @DisplayName("A file moved away as extraction hashes it is removed as one that could not be read, is listed for review, leaves nothing measured or stored, and extraction goes on")
    void aFileMovedAwayAsStageTwoHashesItIsMarkedAndTheStepGoesOn(@TempDir Path parent, @TempDir Path elsewhere)
            throws IOException {
        Path root = theFolderBeingRead(parent);
        String stamp = parent.getFileName().toString();
        Files.writeString(root.resolve(AN_ORDINARY_FILE), "one ordinary file in " + stamp);
        Files.writeString(
                root.resolve(HASHED_ONLY_BY_STAGE_2),
                "a file of a length no other file here has, moved away as extraction hashes it, in " + stamp);
        Path movedTo = elsewhere.resolve(HASHED_ONLY_BY_STAGE_2);
        AT_STAGE_2S_HASH.set(file -> move(file, movedTo));

        cli.run("run", root.toString());

        claim(
                "extraction completed, with nothing set aside, and is recorded as finished: one file it could"
                        + " not read did not stop it",
                () -> {
                    assertThat(lines()).anyMatch(line -> line.startsWith(STAGE_2_FINISHED) && line.contains(NOTHING_SET_ASIDE));
                    assertThat(finishedSteps(root)).contains("extraction");
                });
        claim(
                "the moved file alone was removed, and its reason says it could not be read",
                () -> assertThat(extractionFailedReasons(root))
                        .containsOnlyKeys(HASHED_ONLY_BY_STAGE_2)
                        .hasEntrySatisfying(HASHED_ONLY_BY_STAGE_2, reason -> assertThat(reason).startsWith(COULD_NOT_BE_READ)));
        claim(
                "nothing was measured for it and no key was recorded for it, and it was not set aside to be"
                        + " decided when the stage ends: the converter was never asked about it",
                () -> {
                    assertThat(rowsFor(root, HASHED_ONLY_BY_STAGE_2, "extraction_metric")).isZero();
                    assertThat(rowsFor(root, HASHED_ONLY_BY_STAGE_2, "extraction_cache_key")).isZero();
                    assertThat(rowsFor(root, HASHED_ONLY_BY_STAGE_2, "extraction_fault")).isZero();
                });
        claim(
                "nothing was stored for its bytes, so a later run of the stage reads it again",
                () -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM extraction_cache WHERE content_hash = ?",
                                Long.class,
                                sha256Of(movedTo)))
                        .isZero());
        claim(
                "it was not counted towards stopping extraction for a converter that has stopped answering",
                () -> assertThat(lines()).noneMatch(line -> line.contains(BREAKER_COUNTED)));
        claim(
                "the page of files extraction could not read lists it, with its reason",
                () -> assertThat(workingDirectory.resolve(THE_REVIEW_LIST))
                        .content()
                        .contains(HASHED_ONLY_BY_STAGE_2)
                        .contains(COULD_NOT_BE_READ));
    }

    @Test
    @Story("A file stage 1 cannot hash")
    @DisplayName("A file deleted while byte-level reduction hashes the files of its length is left unhashed, the stage finishes and its count of hashes reaches its total, and extraction then removes the file as one that could not be read")
    void aFileDeletedAsStageOneHashesItIsLeftForStageTwo(@TempDir Path parent) throws IOException {
        Path root = theFolderBeingRead(parent);
        writeTwoOfOneLength(root, parent.getFileName().toString());
        AFTER_STAGE_1S_FIRST_HASH.set(firstHash -> TWO_OF_ONE_LENGTH.stream()
                .map(root::resolve)
                .filter(file -> Files.exists(file) && !sha256Of(file).equals(firstHash))
                .forEach(AFileThatCannotBeReadIsMarkedInvocationTest::delete));

        cli.run("run", root.toString());

        claim(
                "byte-level reduction finished and is recorded as finished: the file it could not hash did not"
                        + " stop it, and nothing it had done was undone",
                () -> {
                    assertThat(lines()).anyMatch(line -> line.startsWith(STAGE_1_FINISHED));
                    assertThat(finishedSteps(root)).contains("byte-level-reduction");
                });
        claim(
                "its count of the files it hashes reached " + BOTH + " of " + BOTH + ": the file it could not"
                        + " hash is counted as gone through, so the count does not stop short of its total",
                () -> assertThat(ProgressLines.of(logged.list, CONTENT_HASH))
                        .containsExactlyElementsOf(ProgressLines.expected(CONTENT_HASH, BOTH)));
        String deleted = theOneWithNoHash(root);
        claim(
                "the deleted file has no hash recorded and was not removed by byte-level reduction: it is not"
                        + " called broken, and not called a copy of the other",
                () -> assertThat(stageOneVerdictsAgainst(root, deleted)).isEmpty());
        claim(
                "extraction met it next, removed it as one that could not be read, and completed",
                () -> {
                    assertThat(extractionFailedReasons(root))
                            .containsOnlyKeys(deleted)
                            .hasEntrySatisfying(deleted, reason -> assertThat(reason).startsWith(COULD_NOT_BE_READ));
                    assertThat(finishedSteps(root)).contains("extraction");
                });
    }

    @Test
    @Story("The folder being read is gone")
    @DisplayName("When the folder being read is gone while byte-level reduction hashes its files, the stage stops, names the folder, says to reconnect it, and removes nothing")
    void aFolderGoneAsStageOneHashesStopsTheStage(CapturedOutput output, @TempDir Path parent) throws IOException {
        Path root = theFolderBeingRead(parent);
        writeTwoOfOneLength(root, parent.getFileName().toString());
        String canonicalRoot = Walk.canonicalRoot(root).toString();
        AFTER_STAGE_1S_FIRST_HASH.set(firstHash -> move(root, parent.resolve("moved-away")));

        cli.run("run", root.toString());

        aStageOneThatStoppedForTheFolder(output, canonicalRoot);
    }

    @Test
    @Story("The folder being read is gone")
    @DisplayName("When the folder being read is gone while byte-level reduction checks its files for damage, the stage stops, names the folder, says to reconnect it, and calls no file broken")
    void aFolderGoneAsStageOneChecksStopsTheStage(CapturedOutput output, @TempDir Path parent) throws IOException {
        Path root = theFolderBeingRead(parent);
        for (String name : THREE_OF_THREE_LENGTHS) {
            Files.writeString(root.resolve(name), name + " in " + parent.getFileName());
        }
        String canonicalRoot = Walk.canonicalRoot(root).toString();
        AFTER_STAGE_1S_FIRST_CHECK.set(() -> move(root, parent.resolve("moved-away")));

        cli.run("run", root.toString());

        aStageOneThatStoppedForTheFolder(output, canonicalRoot);
    }

    @Test
    @Story("The folder being read is gone")
    @DisplayName("When the folder being read is gone as extraction hashes a file, extraction stops, names the folder, says to reconnect it, and removes nothing")
    void aFolderGoneAsStageTwoHashesStopsTheStep(@TempDir Path parent) throws IOException {
        Path root = theFolderBeingRead(parent);
        String stamp = parent.getFileName().toString();
        Files.writeString(root.resolve(AN_ORDINARY_FILE), "one ordinary file in " + stamp);
        Files.writeString(
                root.resolve(HASHED_ONLY_BY_STAGE_2),
                "a file of a length no other file here has, whose folder goes as extraction hashes it, in " + stamp);
        String canonicalRoot = Walk.canonicalRoot(root).toString();
        Path movedTo = parent.resolve("moved-away");
        AT_STAGE_2S_HASH.set(file -> move(root, movedTo));

        cli.run("run", root.toString());

        claim("the invocation failed", () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "extraction's closing line says it failed, names the folder that can no longer be listed, and"
                        + " says to reconnect it and run the same command again",
                () -> assertThat(lines())
                        .anyMatch(line -> line.startsWith(STAGE_2_FAILED)
                                && line.contains(theFolderCannotBeListed(canonicalRoot))
                                && line.contains(RECONNECT)));
        claim(
                "byte-level reduction, which came first, is recorded as finished, and extraction is not",
                () -> assertThat(finishedSteps(canonicalRoot))
                        .contains("byte-level-reduction")
                        .doesNotContain("extraction"));
        claim(
                "no file was removed by extraction for the folder having gone",
                () -> assertThat(extractionFailedReasons(canonicalRoot)).isEmpty());
    }

    /** What every stage-1 stop for a folder that is gone leaves: the same claims, wherever stage 1 was. */
    private void aStageOneThatStoppedForTheFolder(CapturedOutput output, String canonicalRoot) {
        claim("the invocation failed", () -> assertThat(cli.getExitCode()).isNotZero());
        claim(
                "byte-level reduction says it failed, names the folder that can no longer be listed, and says"
                        + " to reconnect it and run the same command again",
                () -> assertThat(output.getAll())
                        .contains(STAGE_1_FAILED)
                        .contains(theFolderCannotBeListed(canonicalRoot))
                        .contains(RECONNECT));
        claim(
                "byte-level reduction is not recorded as finished",
                () -> assertThat(finishedSteps(canonicalRoot)).doesNotContain("byte-level-reduction"));
        claim(
                "and no file is removed or called anything: not broken, not a copy, nothing",
                () -> assertThat(verdictsUnder(canonicalRoot)).isEmpty());
    }

    /** The words that name a folder being read that can no longer be listed. */
    private static String theFolderCannotBeListed(String canonicalRoot) {
        return "the corpus root " + canonicalRoot + " can no longer be listed";
    }

    /** A folder of its own beneath {@code parent}, so a test can move the whole of it away and still clean up. */
    private static Path theFolderBeingRead(Path parent) throws IOException {
        return Files.createDirectory(parent.resolve("corpus"));
    }

    /** {@link #TWO_OF_ONE_LENGTH}, each with bytes of its own, of one length, so neither is a copy of the other. */
    private static void writeTwoOfOneLength(Path root, String stamp) throws IOException {
        for (String name : TWO_OF_ONE_LENGTH) {
            Files.writeString(root.resolve(name), name + ", a file one length with another, in " + stamp);
        }
    }

    /** Which of {@link #TWO_OF_ONE_LENGTH} stage 1 recorded no hash for; exactly one, or the claim on it is moot. */
    private String theOneWithNoHash(Path root) {
        List<String> hashed = jdbcTemplate.queryForList(
                "SELECT o.path FROM content_hash h JOIN file_occurrence o ON o.id = h.occurrence_id"
                        + " JOIN walk w ON w.id = o.walk_id WHERE w.root = ?",
                String.class,
                Walk.canonicalRoot(root).toString());
        List<String> notHashed = TWO_OF_ONE_LENGTH.stream().filter(name -> !hashed.contains(name)).toList();
        claim(
                "stage 1 recorded a hash for exactly one of the two files of one length: the one still there",
                () -> assertThat(notHashed).hasSize(1));
        return notHashed.getFirst();
    }

    /** The steps recorded as finished under any run over the walk of the folder walked as {@code canonicalRoot}. */
    private List<String> finishedSteps(String canonicalRoot) {
        return jdbcTemplate.queryForList(
                "SELECT f.step FROM finished_step f JOIN run r ON r.id = f.run_id JOIN walk w ON w.id = r.walk_id"
                        + " WHERE w.root = ?",
                String.class,
                canonicalRoot);
    }

    private List<String> finishedSteps(Path root) {
        return finishedSteps(Walk.canonicalRoot(root).toString());
    }

    /** Every verdict against a file of the walk of {@code canonicalRoot}, as path and kind. */
    private List<String> verdictsUnder(String canonicalRoot) {
        return jdbcTemplate.queryForList(
                "SELECT fo.path || ' -> ' || v.kind FROM verdict v JOIN file_occurrence fo ON fo.id = v.occurrence_id"
                        + " JOIN walk w ON w.id = fo.walk_id WHERE w.root = ?",
                String.class,
                canonicalRoot);
    }

    /** Every verdict stage 1 wrote against {@code path}, and whether a hash is recorded for it, as text. */
    private List<String> stageOneVerdictsAgainst(Path root, String path) {
        String canonicalRoot = Walk.canonicalRoot(root).toString();
        List<String> found = new java.util.ArrayList<>(jdbcTemplate.queryForList(
                "SELECT v.kind FROM verdict v JOIN file_occurrence fo ON fo.id = v.occurrence_id"
                        + " JOIN walk w ON w.id = fo.walk_id JOIN run r ON r.id = v.run_id"
                        + " WHERE w.root = ? AND fo.path = ? AND r.stage = 'byte-level-reduction'",
                String.class,
                canonicalRoot,
                path));
        found.addAll(jdbcTemplate.queryForList(
                "SELECT 'a recorded hash' FROM content_hash h JOIN file_occurrence fo ON fo.id = h.occurrence_id"
                        + " JOIN walk w ON w.id = fo.walk_id WHERE w.root = ? AND fo.path = ?",
                String.class,
                canonicalRoot,
                path));
        return found;
    }

    /** Each file removed as one extraction failed on, with why, for the walk of {@code canonicalRoot}. */
    private Map<String, String> extractionFailedReasons(String canonicalRoot) {
        return jdbcTemplate
                .queryForList(
                        "SELECT o.path, v.reason FROM verdict v JOIN file_occurrence o ON o.id = v.occurrence_id"
                                + " JOIN walk w ON w.id = o.walk_id WHERE w.root = ? AND v.kind = 'EXTRACTION_FAILED'",
                        canonicalRoot)
                .stream()
                .collect(Collectors.toMap(row -> (String) row.get("path"), row -> (String) row.get("reason")));
    }

    private Map<String, String> extractionFailedReasons(Path root) {
        return extractionFailedReasons(Walk.canonicalRoot(root).toString());
    }

    /** How many rows {@code table} holds against {@code path} in the walk of {@code root}. */
    private long rowsFor(Path root, String path, String table) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " t JOIN file_occurrence o ON o.id = t.occurrence_id"
                        + " JOIN walk w ON w.id = o.walk_id WHERE w.root = ? AND o.path = ?",
                Long.class,
                Walk.canonicalRoot(root).toString(),
                path);
    }

    private List<String> lines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    /** The SHA-256 of {@code file}'s bytes, by the JDK, as 64 lowercase hexadecimal characters. */
    private static String sha256Of(Path file) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void move(Path from, Path to) {
        try {
            Files.move(from, to);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void delete(Path file) {
        try {
            Files.delete(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
