package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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

/**
 * What a whole campaign leaves of ADR-274: an HTML tree with a copy of every survivor, drawn again by an invocation
 * whose writing is already recorded without a question put, named on the closing line and opened once, and naming
 * no document outside itself.
 *
 * <p><b>Every claim is scoped to the run this corpus minted</b>, as in {@code DeliverableInvocationTest}: one static
 * working directory serves the class, and trees are found through the runs of this corpus's own walk.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("The deliverable")
@Feature("The tree the operator is handed")
@Link(name = "ADR-274", url = Adr.THE_DELIVERABLE_IS_HTML_WITH_A_VERIFIED_COPY_OF_EVERY_SURVIVOR, type = "adr")
class TheTreeInHtmlInvocationTest {

    private static final String APPLICATION_LOGGER = "io.algernon.vespera";

    private static final String BOILERPLATE_FLOOR = "1.0";

    private static final String EMBEDDING_MODEL_NAME = "qwen3-embedding:0.6b";

    private static final String A_FLOOR_THAT_REMOVES_NOTHING = "0.0";

    private static final String THE_READING_WINDOW = "4096";

    /** The corpus's two documents, by their paths beneath the root. */
    private static final String FIRST = "corpus.txt";

    private static final String SECOND = "another-corpus-document.txt";

    /** One piece of writing stands behind this corpus's tree, however many invocations draw it. */
    private static final int ONE_RUN = 1;

    /** The one document of the corpus taken away before the tree is written, so not copied. */
    private static final int ONE_NOT_COPIED = 1;

    /** How many times the index is opened in an invocation that writes the tree. */
    private static final int ONCE = 1;

    /** What each line the generation step writes of the tree's copies opens with. */
    private static final String COPY_LINES = "Stage 6b (generation, copies)";

    /** One line before the first copy and one after the last. */
    private static final int TWO_COPY_LINES = 2;

    /** Both of the corpus's documents. */
    private static final int BOTH = 2;

    /** The progress page the job rewrites while it runs, in the working directory. */
    private static final String PROGRESS_PAGE = "progress.html";

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

    @Autowired
    private Ledger ledger;

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
    @Story("Every document listed is copied into the tree")
    @DisplayName("A whole campaign leaves an HTML tree holding a copy of each document, and the archive as it was")
    void aCampaignLeavesAnHtmlTreeWithACopyOfEverySurvivor(@TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedCorpus(root, seeds);
        Map<String, String> archiveBefore = everythingIn(root);

        cli.run("run", root.toString());

        claim("the invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the tree opens on index.html, and holds no Markdown",
                () -> {
                    assertThat(theTreeOf(root).resolve("index.html")).isRegularFile();
                    assertThat(everyFileIn(theTreeOf(root))).noneMatch(file -> file.toString().endsWith(".md"));
                });
        claim(
                "both documents are listed as copied, each with the copy it names",
                () -> assertThat(columnByPath(root, "copy_outcome"))
                        .containsExactlyInAnyOrderEntriesOf(Map.of(FIRST, "copied", SECOND, "copied")));
        claim(
                "each copy the listing names holds exactly the bytes of its document in the archive",
                () -> columnByPath(root, "copy").forEach((path, copy) -> assertThat(theTreeOf(root).resolve(copy))
                        .hasSameBinaryContentAs(root.resolve(path))));
        claim(
                "and the archive holds what it held before, each file with its bytes and its time: it was only read",
                () -> assertThat(everythingIn(root)).containsExactlyEntriesOf(archiveBefore));
    }

    @Test
    @Story("Drawing the tree again never writes it again")
    @DisplayName("An invocation whose writing is already recorded draws the tree again without asking the model anything")
    void aFinishedGenerationIsDrawnAgainWithoutAQuestion(@TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedCorpus(root, seeds);
        cli.run("run", root.toString());
        Path index = theTreeOf(root).resolve("index.html");
        claim(
                "the precondition: the first invocation finished the writing, so the next one finds it recorded",
                () -> assertThat(stepFinished(root)).isTrue());
        claim("and it wrote the tree's index, which is taken away below", () -> assertThat(index).isRegularFile());
        Path firstCopy = theTreeOf(root).resolve(columnByPath(root, "copy").get(FIRST));
        claim(
                "and it copied the first document, whose copy is taken away below too",
                () -> assertThat(firstCopy).hasSameBinaryContentAs(root.resolve(FIRST)));
        Files.delete(index);
        Files.delete(firstCopy);
        int questionsBefore = GenerationScriptedBeans.callsMade();
        opener.forget();

        cli.run("run", root.toString());

        claim("the second invocation reports success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "it wrote the index again, though the writing was already recorded finished",
                () -> assertThat(index).isRegularFile());
        claim(
                "and it put no question to the model: the tree is drawn from what was recorded",
                () -> assertThat(GenerationScriptedBeans.callsMade()).isEqualTo(questionsBefore));
        claim(
                "under the " + ONE_RUN + " piece of writing that stood before, the tree's name unchanged",
                () -> assertThat(generationRuns(root)).hasSize(ONE_RUN));
        claim(
                "the copy taken away was made again from the archive, and its row says copied",
                () -> {
                    assertThat(firstCopy).hasSameBinaryContentAs(root.resolve(FIRST));
                    assertThat(columnByPath(root, "copy_outcome")).containsEntry(FIRST, "copied");
                });
        claim(
                "and the opener was asked " + ONCE + " time for an index, this tree's, with the page in place: the"
                        + " path that finds the writing recorded opens what it drew as the other three do",
                () -> {
                    List<RecordingPageOpener.Request> indexOpened = indexRequests();
                    assertThat(indexOpened).hasSize(ONCE);
                    assertThat(indexOpened.getFirst().page()).isEqualTo(index.toUri());
                    assertThat(indexOpened.getFirst().thePageWasThere()).isTrue();
                });
    }

    @Test
    @Story("The last thing an invocation says is what it produced and what is missing from it")
    @DisplayName("The closing line names the tree's index, and the index is opened once, once it is in place")
    @Link(name = "ADR-266", url = Adr.EVERY_INVOCATION_NAMES_AND_OPENS_ITS_PROGRESS_PAGE, type = "adr")
    void theClosingLineNamesTheIndexAndTheIndexIsOpenedOnce(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpus(root, seeds);
        opener.forget();
        logged.list.clear();

        cli.run("run", root.toString());

        Path index = theTreeOf(root).resolve("index.html");
        List<RecordingPageOpener.Request> indexOpened = opener.requests().stream()
                .filter(request -> request.page().getPath().endsWith("/index.html"))
                .toList();
        claim(
                "the last line the invocation writes names the index by its whole path, and says that no group was"
                        + " left unwritten and no document left uncopied, stating both counts at nothing",
                () -> assertThat(operatorLines().getLast())
                        .contains(index + ", with 0 groups left unwritten and 0 documents not copied."));
        claim(
                "the opener was asked " + ONCE + " time for an index, and it was this tree's, with the page in place",
                () -> {
                    assertThat(indexOpened).hasSize(ONCE);
                    assertThat(indexOpened.getFirst().page()).isEqualTo(index.toUri());
                    assertThat(indexOpened.getFirst().thePageWasThere()).isTrue();
                });
    }

    @Test
    @Story("Nothing outside the tree names a document")
    @DisplayName("A document gone before the tree is written is listed as gone, and no line or page outside the tree names it")
    void aSurvivorGoneBeforeTheTreeIsNamedNowhereOutsideIt(@TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedCorpus(root, seeds);
        Path gone = root.resolve(FIRST);
        AtomicInteger linesBeforeItWent = new AtomicInteger(-1);
        GenerationScriptedBeans.duringEachCall(() -> {
            try {
                Files.deleteIfExists(gone);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            linesBeforeItWent.compareAndSet(-1, logged.list.size());
        });

        cli.run("run", root.toString());

        claim(
                "the precondition: the document went while the model was asked, after the walk and before the tree",
                () -> {
                    assertThat(gone).doesNotExist();
                    assertThat(linesBeforeItWent.get()).isNotNegative();
                });
        claim(
                "its row says it is gone and names no copy, and the other document is copied",
                () -> {
                    assertThat(columnByPath(root, "copy_outcome"))
                            .containsExactlyInAnyOrderEntriesOf(Map.of(FIRST, "gone", SECOND, "copied"));
                    assertThat(columnByPath(root, "copy")).containsEntry(FIRST, "");
                });
        claim(
                "the closing line counts the " + ONE_NOT_COPIED + " document whose copy does not stand, in the singular",
                () -> assertThat(operatorLines().getLast())
                        .endsWith("with 0 groups left unwritten and " + ONE_NOT_COPIED + " document not copied."));
        claim(
                "no line written after it went, at any level, names it",
                () -> assertThat(operatorLines().subList(linesBeforeItWent.get(), operatorLines().size()))
                        .noneMatch(line -> line.contains(FIRST)));
        claim(
                "and the progress page, which is there, does not name it",
                () -> {
                    assertThat(workingDirectory.resolve(PROGRESS_PAGE)).isRegularFile();
                    assertThat(Files.readString(workingDirectory.resolve(PROGRESS_PAGE), StandardCharsets.UTF_8))
                            .doesNotContain(FIRST);
                });
    }

    @Test
    @Story("The last thing an invocation says is what it produced and what is missing from it")
    @DisplayName("An invocation that writes the tree opens two pages, the progress page first and the index last")
    @Link(name = "ADR-266", url = Adr.EVERY_INVOCATION_NAMES_AND_OPENS_ITS_PROGRESS_PAGE, type = "adr")
    void anInvocationThatWritesTheTreeOpensTwoPages(@TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedCorpus(root, seeds);
        opener.forget();

        cli.run("run", root.toString());

        claim(
                "the opener was asked for exactly two pages: the progress page, when the job started, and the tree's"
                        + " index, once it was in place",
                () -> assertThat(opener.requests())
                        .extracting(request -> Path.of(request.page()))
                        .containsExactly(
                                workingDirectory.resolve(PROGRESS_PAGE).toAbsolutePath().normalize(),
                                theTreeOf(root).resolve("index.html").toAbsolutePath().normalize()));
    }

    @Test
    @Story("Nothing outside the tree names a document")
    @DisplayName("The tree's copies are told in two lines, one before the first and one after the last, and each carries counts only")
    void theCopiesAreToldBeforeAndAfterInCountsOnly(@TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedCorpus(root, seeds);
        long bothDocumentsBytes = Files.size(root.resolve(FIRST)) + Files.size(root.resolve(SECOND));
        logged.list.clear();

        cli.run("run", root.toString());

        List<ILoggingEvent> copyLines = logged.list.stream()
                .filter(event -> event.getFormattedMessage().startsWith(COPY_LINES))
                .toList();
        claim(
                "the invocation wrote " + TWO_COPY_LINES + " lines of the copies, and every argument of each is a"
                        + " number",
                () -> {
                    assertThat(copyLines).hasSize(TWO_COPY_LINES);
                    assertThat(copyLines)
                            .allSatisfy(line -> assertThat(line.getArgumentArray()).allMatch(Number.class::isInstance));
                });
        claim(
                "the first, before any copy, counts the " + BOTH + " documents about to be copied and the bytes they"
                        + " hold, then the drive's free bytes",
                () -> {
                    Object[] before = copyLines.getFirst().getArgumentArray();
                    assertThat(before).hasSize(3);
                    assertThat(((Number) before[0]).longValue()).isEqualTo(BOTH);
                    assertThat(((Number) before[1]).longValue()).isEqualTo(bothDocumentsBytes);
                    assertThat(((Number) before[2]).longValue()).isNotNegative();
                });
        claim(
                "the second, after the last, counts the copies by outcome, the " + BOTH + " copied first and none of"
                        + " the five others, then the bytes copied",
                () -> assertThat(copyLines.getLast().getArgumentArray())
                        .extracting(argument -> ((Number) argument).longValue())
                        .containsExactly((long) BOTH, 0L, 0L, 0L, 0L, 0L, bothDocumentsBytes));
        claim(
                "and neither names a document, the archive or the tree",
                () -> assertThat(copyLines)
                        .extracting(ILoggingEvent::getFormattedMessage)
                        .noneMatch(line -> line.contains(FIRST)
                                || line.contains(SECOND)
                                || line.contains(root.toString())
                                || line.contains(workingDirectory.toString())));
    }

    /** The tree this corpus's one piece of writing drew. */
    private Path theTreeOf(Path root) {
        return workingDirectory.resolve("deliverable").resolve(generationRuns(root).getLast());
    }

    /** The listing's cells of one column, keyed by each row's path, the columns found by the header's names. */
    private Map<String, String> columnByPath(Path root, String column) throws IOException {
        List<String> lines = Files.readAllLines(theTreeOf(root).resolve("documents.csv")).stream()
                .filter(line -> !line.isBlank())
                .toList();
        List<String> header = cellsOf(lines.getFirst());
        int path = header.indexOf("path");
        int wanted = header.indexOf(column);
        if (wanted < 0) {
            throw new IllegalStateException("documents.csv has no column " + column + ": " + header);
        }
        Map<String, String> byPath = new LinkedHashMap<>();
        for (String row : lines.subList(1, lines.size())) {
            List<String> cells = cellsOf(row);
            byPath.put(cells.get(path), cells.get(wanted));
        }
        return byPath;
    }

    /** One CSV line's cells, with quotes stripped and a doubled quote read as one. */
    private static List<String> cellsOf(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int at = 0; at < line.length(); at++) {
            char c = line.charAt(at);
            if (c == '"' && quoted && at + 1 < line.length() && line.charAt(at + 1) == '"') {
                cell.append('"');
                at++;
            } else if (c == '"') {
                quoted = !quoted;
            } else if (c == ',' && !quoted) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString());
        return cells;
    }

    private static List<Path> everyFileIn(Path directory) throws IOException {
        try (Stream<Path> entries = Files.walk(directory)) {
            return entries.filter(Files::isRegularFile).toList();
        }
    }

    /** Every entry beneath {@code root} by relative path: a folder as {@code folder}, a file as its bytes and time. */
    private static Map<String, String> everythingIn(Path root) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path entry : walk.sorted().toList()) {
                entries.put(
                        root.relativize(entry).toString(),
                        Files.isDirectory(entry)
                                ? "folder"
                                : Files.readString(entry, StandardCharsets.UTF_8) + " " + Files.getLastModifiedTime(entry));
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

    /** Whether the writing step is recorded finished under this corpus's one generation run. */
    private boolean stepFinished(Path root) {
        return ledger.runs().stepFinished(new RunId(generationRuns(root).getLast()), StepNames.GENERATION);
    }

    /** A corpus of two documents, walked once, with the arrangement it produced approved. */
    private void anApprovedCorpus(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve(FIRST), "a corpus document");
        Files.writeString(root.resolve(SECOND), "a second corpus document");
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

    /** Every generation run over {@code root}'s walks, oldest first. */
    private List<String> generationRuns(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid",
                String.class,
                "generation",
                Walk.canonicalRoot(root).toString());
    }
}
