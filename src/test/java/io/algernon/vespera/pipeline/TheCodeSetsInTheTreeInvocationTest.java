package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.profile.Profile;
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
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * What a whole campaign over an archive holding a code set tells of ADR-296: the generation step's two lines of the
 * code copies (§7), one before the first and one after the last, every argument a number, and the counter over the
 * members between them; and the closing line's count of the files of code not copied (§6.1), which the generation
 * step hands to the closing line through {@code InvocationRuns}.
 *
 * <p>The archive is {@code TheTreeInHtmlInvocationTest}'s two documents and a code set at {@link #THE_SET} of two
 * members, its build file and one file listed as code. Each test approves the arrangement in a first invocation, which
 * stops at it, and claims what the second, which draws the tree, writes. One static working directory serves the
 * class, and every claim is scoped to this corpus's own lines and runs.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("The deliverable")
@Feature("Code in the deliverable")
@Issue("573")
@Link(name = "ADR-296", url = Adr.EACH_CODE_SET_IS_COPIED_WHOLE_UNDER_CODE, type = "adr")
class TheCodeSetsInTheTreeInvocationTest {

    private static final String APPLICATION_LOGGER = "io.algernon.vespera";

    private static final String BOILERPLATE_FLOOR = "1.0";

    private static final String EMBEDDING_MODEL_NAME = "qwen3-embedding:0.6b";

    private static final String A_FLOOR_THAT_REMOVES_NOTHING = "0.0";

    private static final String THE_READING_WINDOW = "4096";

    /** The ending listed as code, so that {@link #THE_CODE} is code by its name. */
    private static final String CODE_ENDING = ".java";

    /** The corpus's two documents, by their paths beneath the root. */
    private static final String FIRST = "corpus.txt";

    private static final String SECOND = "another-corpus-document.txt";

    /** The code set's folder, and its two members: its build file and a file listed as code. */
    private static final String THE_SET = "officina";

    private static final String THE_BUILD_FILE = THE_SET + "/Makefile";

    private static final String THE_CODE = THE_SET + "/src/Attrezzo.java";

    /** The one code set of the archive, and its two members. */
    private static final int ONE_SET = 1;

    private static final int BOTH_MEMBERS = 2;

    /** What each of the two lines of the code copies opens with. */
    private static final String CODE_COPY_LINES = "Stage 6b (generation, code copies)";

    /** One line before the first code copy and one after the last. */
    private static final int TWO_CODE_COPY_LINES = 2;

    /** The counter over the members, and its lines over two members: one at each, the cadence being one item. */
    private static final String CODE_COUNTER = "Stage 6b (generation, code copied)";

    private static final List<String> THE_COUNTERS_LINES =
            List.of(CODE_COUNTER + ": 1 of 2 (50%)", CODE_COUNTER + ": 2 of 2 (100%)");

    /** The one member whose time is moved after the walk, so the one file of code not copied. */
    private static final int ONE_NOT_COPIED = 1;

    /** A last-modified time no file of the archive was walked with. */
    private static final Instant MOVED_TO = Instant.parse("2001-02-03T04:05:06Z");

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

    @Autowired
    private RecordingPageOpener opener;

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger applicationLogger;

    @BeforeEach
    void forgetWhatAnEarlierClassScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
        opener.forget();
        logged = new ListAppender<>();
        logged.start();
        applicationLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(APPLICATION_LOGGER);
        applicationLogger.addAppender(logged);
    }

    @AfterEach
    void forgetWhatWasScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
        opener.forget();
        applicationLogger.detachAppender(logged);
        logged.stop();
    }

    @Test
    @Story("Nothing outside the tree names a document")
    @DisplayName("The code copies are told in two lines, one before the first and one after the last, each carrying counts only, with a counter over the files between them")
    void theCodeCopiesAreToldBeforeAndAfterInCountsOnly(@TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedCorpusWithACodeSet(root, seeds);
        long bothMembersBytes = Files.size(root.resolve(THE_BUILD_FILE)) + Files.size(root.resolve(THE_CODE));
        logged.list.clear();

        cli.run("run", root.toString());

        List<ILoggingEvent> codeCopyLines = logged.list.stream()
                .filter(event -> event.getFormattedMessage().startsWith(CODE_COPY_LINES))
                .toList();
        claim(
                "the invocation wrote " + TWO_CODE_COPY_LINES + " lines of the code copies, and every argument of each"
                        + " is a number",
                () -> {
                    assertThat(codeCopyLines).hasSize(TWO_CODE_COPY_LINES);
                    assertThat(codeCopyLines)
                            .allSatisfy(line -> assertThat(line.getArgumentArray()).allMatch(Number.class::isInstance));
                });
        claim(
                "the first, before any copy, counts the " + ONE_SET + " code set, its " + BOTH_MEMBERS + " files about"
                        + " to be copied and the bytes recorded for them when the archive was walked, then the"
                        + " drive's free bytes",
                () -> {
                    Object[] before = codeCopyLines.getFirst().getArgumentArray();
                    assertThat(before).hasSize(4);
                    assertThat(((Number) before[0]).longValue()).isEqualTo(ONE_SET);
                    assertThat(((Number) before[1]).longValue()).isEqualTo(BOTH_MEMBERS);
                    assertThat(((Number) before[2]).longValue()).isEqualTo(bothMembersBytes);
                    assertThat(((Number) before[3]).longValue()).isNotNegative();
                });
        claim(
                "the second, after the last, counts the copies by outcome, the " + BOTH_MEMBERS + " copied first and"
                        + " none of the five others, then the bytes copied",
                () -> assertThat(codeCopyLines.getLast().getArgumentArray())
                        .extracting(argument -> ((Number) argument).longValue())
                        .containsExactly((long) BOTH_MEMBERS, 0L, 0L, 0L, 0L, 0L, bothMembersBytes));
        claim(
                "between them the counter over the code copies moves on once for each of the " + BOTH_MEMBERS
                        + " files, and only between them",
                () -> {
                    List<String> lines = operatorLines();
                    List<String> counted = lines.stream().filter(line -> line.startsWith(CODE_COUNTER)).toList();
                    assertThat(counted).containsExactlyElementsOf(THE_COUNTERS_LINES);
                    assertThat(lines.indexOf(counted.getFirst()))
                            .isGreaterThan(lines.indexOf(codeCopyLines.getFirst().getFormattedMessage()));
                    assertThat(lines.indexOf(counted.getLast()))
                            .isLessThan(lines.indexOf(codeCopyLines.getLast().getFormattedMessage()));
                });
        claim(
                "and no line of the code copies names the set, a file of it, the archive or the tree",
                () -> assertThat(operatorLines())
                        .filteredOn(line -> line.startsWith(CODE_COPY_LINES) || line.startsWith(CODE_COUNTER))
                        .noneMatch(line -> line.contains(THE_SET)
                                || line.contains("Makefile")
                                || line.contains("Attrezzo")
                                || line.contains(root.toString())
                                || line.contains(workingDirectory.toString())));
    }

    @Test
    @Story("The last thing an invocation says is what it produced and what is missing from it")
    @DisplayName("The closing line of an invocation that drew a tree holding a code set counts the files of code not copied")
    void theClosingLineCountsTheFilesOfCodeTheTreeDidNotCopy(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpusWithACodeSet(root, seeds);
        Path moved = root.resolve(THE_CODE);
        AtomicBoolean movedAfterTheWalk = new AtomicBoolean();
        GenerationScriptedBeans.duringEachCall(() -> {
            try {
                Files.setLastModifiedTime(moved, FileTime.from(MOVED_TO));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            movedAfterTheWalk.set(true);
        });
        logged.list.clear();

        cli.run("run", root.toString());

        claim(
                "the precondition: the file of code's time was moved while the model was asked, after the walk and"
                        + " before the tree, so its copy is refused as changed",
                () -> {
                    assertThat(movedAfterTheWalk).isTrue();
                    assertThat(Files.getLastModifiedTime(moved).toInstant()).isEqualTo(MOVED_TO);
                });
        claim(
                "the invocation reports success, and the last line it writes, after the documents' count, counts the "
                        + ONE_NOT_COPIED + " file of code whose copy does not stand, in the singular",
                () -> {
                    assertThat(cli.getExitCode()).isZero();
                    assertThat(operatorLines().getLast())
                            .endsWith("with 0 groups left unwritten and 0 documents not copied, and " + ONE_NOT_COPIED
                                    + " file of code not copied.");
                });
    }

    private List<String> operatorLines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    /**
     * A corpus of two documents and a code set of two members, walked once, with the arrangement it produced
     * approved.
     */
    private void anApprovedCorpusWithACodeSet(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve(FIRST), "a corpus document");
        Files.writeString(root.resolve(SECOND), "a second corpus document");
        Files.createDirectories(root.resolve(THE_CODE).getParent());
        Files.writeString(root.resolve(THE_BUILD_FILE), "all:\n\tjavac src/Attrezzo.java\n");
        Files.writeString(root.resolve(THE_CODE), "class Attrezzo {\n    int misura() { return 1; }\n}\n");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EMBEDDING_MODEL_NAME, "set by this test, so gate 3 is open")
                .relevanceScoreFloor(A_FLOOR_THAT_REMOVES_NOTHING, "set by this test, so nothing earlier is asked for")
                .generationContextWindow(THE_READING_WINDOW, "set by this test, so every test here reads alike")
                .codeNameEndings(CODE_ENDING, "set by this test, so the archive holds a code set")
                .build());
        cli.run("run", root.toString());
        String approval = ArrangementGate.shortNameOf(theLatestArrangement(root));
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .arrangementApproved(approval, "read by this test")
                .build());
    }

    private RunId theLatestArrangement(Path root) {
        return new RunId(jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid DESC LIMIT 1",
                String.class,
                "arrangement",
                Walk.canonicalRoot(root).toString()));
    }
}
