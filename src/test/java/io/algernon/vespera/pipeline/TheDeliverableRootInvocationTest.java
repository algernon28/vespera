package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
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
import picocli.CommandLine;

/**
 * Whole invocations with a deliverable root named (ADR-276): the tree is drawn at the root and the closing line names
 * its index, the same generation's tree is drawn again in place, a root holding another generation's tree is not drawn
 * over, and a root that is not empty, or lies inside the corpus root, is refused before anything is walked.
 *
 * <p>The root is bound for the class, at {@code <base>/the deliverable root}, and taken away before each test, so each
 * starts from a root not made yet. One static working directory serves the class, as in {@code
 * TheTreeInHtmlInvocationTest}, and every claim about a tree is scoped to the generation run this test's corpus minted.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("The deliverable")
@Feature("Where the tree is drawn")
@Issue("557")
@Link(name = "ADR-276", url = Adr.THE_DELIVERABLE_IS_DRAWN_AT_A_ROOT_THE_OPERATOR_MAY_NAME, type = "adr")
class TheDeliverableRootInvocationTest {

    private static final String APPLICATION_LOGGER = "io.algernon.vespera";

    private static final String BOILERPLATE_FLOOR = "1.0";

    private static final String EMBEDDING_MODEL_NAME = "qwen3-embedding:0.6b";

    private static final String A_FLOOR_THAT_REMOVES_NOTHING = "0.0";

    private static final String THE_READING_WINDOW = "4096";

    /** The corpus's two documents, by their paths beneath the corpus root. */
    private static final String FIRST = "corpus.txt";

    private static final String SECOND = "another-corpus-document.txt";

    /** What the tree's marker is called, at the top of every tree. */
    private static final String MARKER = "vespera-deliverable.txt";

    /** A generation run's id of the shape the ledger mints, which no corpus of this class mints. */
    private static final String ANOTHER_GENERATION_RUN = "e".repeat(48) + "0123456789abcdef";

    /** The exit code of a refused invocation: 1. */
    private static final int REFUSED = CommandLine.ExitCode.SOFTWARE;

    /** The whole of what a refusal shows the operator: one line. */
    private static final int ONE_LINE = 1;

    /** How many times the index is opened in an invocation that writes the tree. */
    private static final int ONCE = 1;

    /** One piece of writing stands behind a corpus's tree, however many invocations draw it. */
    private static final int ONE_RUN = 1;

    @TempDir
    static Path workingDirectory;

    /** Where the named root lies, and the corpus root of the test that names a root inside the corpus root. */
    @TempDir
    static Path base;

    @DynamicPropertySource
    static void workingDirectoryAndDeliverableRoot(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add(VesperaCommand.Run.DELIVERABLE_ROOT_PROPERTY, () -> deliverableRoot().toString());
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
    void startFromARootNotMadeYet() throws IOException {
        GenerationScriptedBeans.forgetScriptedAnswers();
        opener.forget();
        takeAway(deliverableRoot());
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
    @Story("The tree is drawn at the folder the operator names, or in the working directory")
    @DisplayName("With a folder named, the tree is drawn there and the last line names its index, which is opened once")
    @Link(name = "ADR-274", url = Adr.THE_DELIVERABLE_IS_HTML_WITH_A_VERIFIED_COPY_OF_EVERY_SURVIVOR, type = "adr")
    void aNamedRootHoldsTheTreeAndTheClosingLineNamesItsIndex(@TempDir Path corpusRoot, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpus(corpusRoot, seeds);
        opener.forget();
        logged.list.clear();

        cli.run("run", corpusRoot.toString());

        Path index = deliverableRoot().toAbsolutePath().normalize().resolve("index.html");
        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the tree's index and listing are at the top of the named folder",
                () -> {
                    assertThat(index).isRegularFile();
                    assertThat(deliverableRoot().resolve("documents.csv")).isRegularFile();
                });
        claim(
                "its marker names this working directory and the generation this corpus minted",
                () -> assertThat(Files.readString(deliverableRoot().resolve(MARKER), StandardCharsets.UTF_8))
                        .isEqualTo("working-directory=" + workingDirectory.toRealPath() + "\n"
                                + "generation-run=" + generationRuns(corpusRoot).getLast() + "\n"));
        claim(
                "nothing was drawn under the working directory's deliverable folder",
                () -> assertThat(workingDirectory.resolve("deliverable")).doesNotExist());
        claim(
                "the last line the invocation writes names the index at the named folder by its whole path",
                () -> assertThat(operatorLines().getLast())
                        .contains(index + ", with 0 groups left unwritten and 0 documents not copied."));
        claim(
                "and the opener was asked " + ONCE + " time for an index, that one, with the page in place",
                () -> {
                    List<RecordingPageOpener.Request> indexOpened = indexRequests();
                    assertThat(indexOpened).hasSize(ONCE);
                    assertThat(indexOpened.getFirst().page()).isEqualTo(index.toUri());
                    assertThat(indexOpened.getFirst().thePageWasThere()).isTrue();
                });
    }

    @Test
    @Story("A folder that holds anything but this tree is refused")
    @DisplayName("An invocation of the same generation draws its tree again in the folder that holds it")
    void aSecondInvocationOfTheSameRunRedrawsTheRootInPlace(@TempDir Path corpusRoot, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpus(corpusRoot, seeds);
        cli.run("run", corpusRoot.toString());
        Path index = deliverableRoot().resolve("index.html");
        claim("the precondition: the first invocation drew the index, which is taken away below", () -> assertThat(index)
                .isRegularFile());
        Files.delete(index);

        cli.run("run", corpusRoot.toString());

        claim("the second invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim("it drew the index again in the same folder", () -> assertThat(index).isRegularFile());
        claim(
                "under the " + ONE_RUN + " generation that drew the folder's tree before",
                () -> assertThat(generationRuns(corpusRoot)).hasSize(ONE_RUN));
    }

    @Test
    @Story("A folder that holds anything but this tree is refused")
    @DisplayName("A folder holding another generation's tree is not drawn over, and the invocation says so in one line and exits 1")
    void aRootHoldingAnotherRunsTreeIsNotDrawnOver(@TempDir Path corpusRoot, @TempDir Path seeds) throws IOException {
        Files.createDirectories(deliverableRoot());
        Files.writeString(
                deliverableRoot().resolve(MARKER),
                "working-directory=" + workingDirectory.toRealPath() + "\n" + "generation-run=" + ANOTHER_GENERATION_RUN + "\n",
                StandardCharsets.UTF_8);
        Files.writeString(deliverableRoot().resolve("index.html"), "another generation's index", StandardCharsets.UTF_8);
        Map<String, String> before = everythingIn(deliverableRoot());
        anApprovedCorpus(corpusRoot, seeds);
        opener.forget();
        logged.list.clear();

        cli.run("run", corpusRoot.toString());

        claim("the invocation exits " + REFUSED, () -> assertThat(cli.getExitCode()).isEqualTo(REFUSED));
        claim(
                "the one line it writes at error level says the folder holds another generation's tree and nothing was"
                        + " drawn",
                () -> assertThat(logged.list.stream()
                                .filter(event -> event.getLevel() == Level.ERROR)
                                .map(ILoggingEvent::getFormattedMessage)
                                .toList())
                        .hasSize(ONE_LINE)
                        .containsExactly("the deliverable root " + deliverableRoot().toRealPath() + " holds the tree of"
                                + " another generation run, so no tree was drawn. Name an empty folder in"
                                + " vespera.deliverable-root, and run again."));
        claim(
                "the folder holds exactly what it held, the other generation's index and marker as they were",
                () -> assertThat(everythingIn(deliverableRoot())).containsExactlyEntriesOf(before));
        claim("no index was opened", () -> assertThat(indexRequests()).isEmpty());
        claim(
                "and the tree was not drawn under the working directory instead",
                () -> assertThat(workingDirectory.resolve("deliverable")).doesNotExist());
    }

    @Test
    @Story("A folder that holds anything but this tree is refused")
    @DisplayName("A folder that holds a file and no tree is refused in one line before anything is walked, and exits 1")
    void aRootThatIsNotEmptyAndHoldsNoTreeIsRefusedBeforeAnythingIsWalked(@TempDir Path corpusRoot) throws IOException {
        Files.writeString(corpusRoot.resolve(FIRST), "a corpus document");
        Files.createDirectories(deliverableRoot());
        Path theOperatorsNotes = deliverableRoot().resolve("notes.txt");
        Files.writeString(theOperatorsNotes, "the operator's notes", StandardCharsets.UTF_8);

        String printed = standardErrorOf(() -> cli.run("run", corpusRoot.toString()));

        claim("the invocation exits " + REFUSED, () -> assertThat(cli.getExitCode()).isEqualTo(REFUSED));
        claim(
                "and says why in " + ONE_LINE + " line on standard error",
                () -> assertThat(printed.lines().toList())
                        .hasSize(ONE_LINE)
                        .containsExactly("vespera run refused the deliverable root " + deliverableRoot().toRealPath()
                                + ": it is not empty and holds no tree of this working directory. Name an empty folder in"
                                + " vespera.deliverable-root."));
        claim("nothing was walked: no walk of the corpus root is recorded", () -> assertThat(walksOver(corpusRoot)).isZero());
        claim(
                "and the folder holds the operator's file alone, as it was",
                () -> {
                    assertThat(everythingIn(deliverableRoot())).containsOnlyKeys("notes.txt");
                    assertThat(theOperatorsNotes).hasContent("the operator's notes");
                });
    }

    @Test
    @Story("A folder over the archive is refused before anything is walked")
    @DisplayName("A folder inside the corpus root is refused in one line before anything is walked, and exits 1")
    void aRootInsideTheCorpusRootIsRefusedBeforeAnythingIsWalked() throws IOException {
        Path corpusRoot = base;
        Files.writeString(corpusRoot.resolve(FIRST), "a corpus document");

        String printed = standardErrorOf(() -> cli.run("run", corpusRoot.toString()));

        claim("the invocation exits " + REFUSED, () -> assertThat(cli.getExitCode()).isEqualTo(REFUSED));
        claim(
                "and says why in " + ONE_LINE + " line on standard error, naming the folder and the corpus root it lies in",
                () -> assertThat(printed.lines().toList())
                        .hasSize(ONE_LINE)
                        .containsExactly("vespera run refused the deliverable root "
                                + corpusRoot.toRealPath().resolve(deliverableRoot().getFileName())
                                + ": it is inside the corpus root " + corpusRoot.toRealPath()
                                + ". Name a folder outside the corpus root and the seed folder in vespera.deliverable-root."));
        claim("nothing was walked: no walk of the corpus root is recorded", () -> assertThat(walksOver(corpusRoot)).isZero());
        claim("and the folder was not made", () -> assertThat(deliverableRoot()).doesNotExist());
    }

    /** The folder named as the deliverable root for the whole class. */
    private static Path deliverableRoot() {
        return base.resolve("the deliverable root");
    }

    /** Takes {@code folder} and everything beneath it away, where it is there. */
    private static void takeAway(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> entries = Files.walk(folder)) {
            for (Path entry : entries.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(entry);
            }
        }
    }

    /** What {@code invocation} wrote on standard error. */
    private static String standardErrorOf(Runnable invocation) {
        PrintStream original = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            invocation.run();
            return captured.toString(StandardCharsets.UTF_8);
        } finally {
            System.setErr(original);
        }
    }

    /** Every entry beneath {@code root} by relative path: a folder as {@code folder}, a file as its text. */
    private static Map<String, String> everythingIn(Path root) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path entry : walk.sorted().toList()) {
                if (!entry.equals(root)) {
                    entries.put(
                            root.relativize(entry).toString(),
                            Files.isDirectory(entry) ? "folder" : Files.readString(entry, StandardCharsets.UTF_8));
                }
            }
        }
        return entries;
    }

    private List<String> operatorLines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    /** Every request the opener was given for a tree's index since it was last told to forget. */
    private List<RecordingPageOpener.Request> indexRequests() {
        return opener.requests().stream()
                .filter(request -> request.page().getPath().endsWith("/index.html"))
                .toList();
    }

    /** How many walks of {@code corpusRoot} are recorded. */
    private long walksOver(Path corpusRoot) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM walk WHERE root = ?", Long.class, Walk.canonicalRoot(corpusRoot).toString());
    }

    /** A corpus of two documents, walked once, with the arrangement it produced approved. */
    private void anApprovedCorpus(Path corpusRoot, Path seeds) throws IOException {
        Files.writeString(corpusRoot.resolve(FIRST), "a corpus document");
        Files.writeString(corpusRoot.resolve(SECOND), "a second corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EMBEDDING_MODEL_NAME, "set by this test, so gate 3 is open")
                .relevanceScoreFloor(A_FLOOR_THAT_REMOVES_NOTHING, "set by this test, so nothing earlier is asked for")
                .generationContextWindow(THE_READING_WINDOW, "set by this test, so every test here reads alike")
                .build());
        cli.run("run", corpusRoot.toString());
        String approval = ArrangementGate.shortNameOf(theLatestArrangement(corpusRoot));
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .arrangementApproved(approval, "read by this test")
                .build());
    }

    private RunId theLatestArrangement(Path corpusRoot) {
        return new RunId(jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid DESC LIMIT 1",
                String.class,
                "arrangement",
                Walk.canonicalRoot(corpusRoot).toString()));
    }

    /** Every generation run over {@code corpusRoot}'s walks, oldest first. */
    private List<String> generationRuns(Path corpusRoot) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid",
                String.class,
                "generation",
                Walk.canonicalRoot(corpusRoot).toString());
    }
}
