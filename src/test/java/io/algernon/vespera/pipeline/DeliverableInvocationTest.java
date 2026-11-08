package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.deliverable.Deliverable;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.synthesis.Clusters;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

/**
 * What an invocation of stage 6b actually leaves on disk (ADR-103, ADR-104, ADR-111, ADR-112, ADR-274, #186):
 * a tree named for the run that wrote it, the index that opens it, the listing beside it, a copy of every
 * survivor it lists, and the line that tells the operator where the index is.
 *
 * <p>Driven end to end rather than against the writer, because every claim here is about something
 * the writer is handed rather than something it decides: which run the tree is named for, which
 * values the index states, which documents the listing carries, and whether a second invocation
 * writes beside the first or over it. {@code DeliverableTest} holds the claims about the rendering
 * itself, including the two that no invocation can reach.
 *
 * <p><b>Every claim here is scoped to the run this method minted.</b> One static working directory
 * serves the whole class, so a tree written by an earlier method is still on disk when the next one
 * runs — and because the tree is named for the run, "this invocation wrote no tree" is a claim that
 * passes on somebody else's. Two devices keep that honest: the trees this corpus produced are found
 * through the run ids recorded against this corpus's own walk, and the one claim that is genuinely
 * about absence compares the whole directory before and after rather than looking for nothing.
 *
 * <p><b>The report says <em>group</em> and the code says <em>cluster</em></b> (ADR-122). The tree is
 * one of that record's outside-facing surfaces, and so is every word of this report.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Synthesis")
@Feature("The tree the operator is handed")
@Issue("186")
@Link(name = "ADR-103", url = Adr.THE_DELIVERABLE_IS_A_MARKDOWN_TREE_PER_RUN, type = "adr")
@Link(name = "ADR-104", url = Adr.THE_ORIGINALS_STAY_WHERE_THEY_ARE_AND_ARE_REFERENCED, type = "adr")
@Link(name = "ADR-112", url = Adr.THE_ARRANGEMENT_IS_ORDERED_BY_SIZE_AND_MEAN_SCORE, type = "adr")
@Link(name = "ADR-274", url = Adr.THE_DELIVERABLE_IS_HTML_WITH_A_VERIFIED_COPY_OF_EVERY_SURVIVOR, type = "adr")
@Link(name = "ADR-280", url = Adr.A_CLUSTERS_FOLDER_HOLDS_ITS_PAGE_AND_ITS_SURVIVORS_COPIES, type = "adr")
class DeliverableInvocationTest {

    /** The logger every operator-facing line in this application is written through. */
    private static final String APPLICATION_LOGGER = "io.algernon.vespera";

    /**
     * A relevance floor of zero, which removes nothing and leaves the corpus as it was.
     *
     * <p>Answered by the fixture because the closing line names the value an operator still owes, and
     * a threshold nobody answered is owed before anything about a tree is: without it the last line
     * of every invocation here would be about labelling, and two tests would claim against a sentence
     * the operator never reaches on the path this stage sits at the end of.
     */
    private static final String A_FLOOR_THAT_REMOVES_NOTHING = "0.0";

    /** How much every call in this class may read, stated so the profile cannot carry another's. */
    private static final String THE_READING_WINDOW = "4096";

    /** What one archive, read one way, is worth: one piece of work, and one tree named after it. */
    private static final int ONE_RUN = 1;

    /** The two documents this fixture's corpus holds, which are the two the listing has to carry. */
    private static final int TWO_DOCUMENTS = 2;

    /** And how many this corpus arranges on its own, which is how many pages its tree holds. */
    private static final int ONE_CLUSTER = 1;

    /** What the corpus documents of this fixture hold, so a copy of one is recognisable as a copy. */
    private static final String WHAT_A_CORPUS_DOCUMENT_SAYS = "a corpus document";

    /** And what the second holds. */
    private static final String WHAT_THE_OTHER_CORPUS_DOCUMENT_SAYS = "a second corpus document";

    /** The column of the listing that says what became of each document's copy (ADR-274 §1.4). */
    private static final String THE_COPY_OUTCOME_COLUMN = "copy_outcome";

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
    private Clusters clusters;

    /** The extraction double this class imports, which records each file it is asked to hash. */
    @Autowired
    private DoclingExtractor doclingExtractor;

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger applicationLogger;

    /**
     * Drops what an earlier class scripted and what it counted, before each test as well as after it.
     *
     * <p>The count and the script are static on a fixture eighteen classes in this package share, so
     * clearing only afterwards leaves this class trusting whichever class ran before it to have done
     * the same — and class order is not a thing any test here decides.
     */
    @BeforeEach
    void forgetWhatAnEarlierClassScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    @AfterEach
    void forgetWhatWasScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    @BeforeEach
    void captureOperatorLines() {
        logged = new ListAppender<>();
        logged.start();
        applicationLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(APPLICATION_LOGGER);
        applicationLogger.addAppender(logged);
    }

    @AfterEach
    void releaseOperatorLines() {
        applicationLogger.detachAppender(logged);
        logged.stop();
    }

    @Test
    @Story("The operator is handed a tree named after the work that produced it")
    @DisplayName("With nothing approved, the invocation leaves every tree on disk exactly as it found it")
    void writesNoTreeWhenNothingIsApproved(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds);
        approve(null);
        List<String> before = everyTreeOnDisk();

        cli.run("run", root.toString());

        claim(
                "the invocation succeeded: a person who has not yet read the arrangement has done nothing"
                        + " wrong",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "and the trees on disk are exactly the ones that were there before it -- compared before"
                        + " and after rather than counted, because one working directory serves every test"
                        + " in this class and a claim that simply looked for nothing would be satisfied by"
                        + " another test's tree just as well as by this one having written none",
                () -> assertThat(everyTreeOnDisk()).isEqualTo(before));
    }

    @Test
    @Story("Running again changes nothing, and changing something writes beside what is there")
    @DisplayName("An invocation that changed nothing writes the same tree again rather than a second beside it")
    void writesTheSamePathWhenNothingChanged(@TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedCorpus(root, seeds);
        cli.run("run", root.toString());
        Path firstIndex = theIndexFileOf(root);
        Files.delete(firstIndex);

        cli.run("run", root.toString());

        claim(
                "the second invocation reports success, having met its own earlier work rather than"
                        + " fallen over it",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "both invocations worked under " + ONE_RUN + " name, because a name is worked out from"
                        + " what the work read and nothing either of them read had changed: this is a"
                        + " claim about what the work is called, and the path follows from it",
                () -> assertThat(generationRuns(root)).hasSize(ONE_RUN));
        claim(
                "so the second wrote the very path the first did, rather than a near-copy beside it: the"
                        + " first tree's index, deleted between the two invocations, stands there again."
                        + " Running again is meant to leave the operator with what they already had, not"
                        + " with two trees they have to tell apart by timestamp",
                () -> assertThat(firstIndex).isRegularFile());
    }

    @Test
    @Story("Every document listed is copied into the tree, and the archive is left as it was")
    @DisplayName("Each document is copied into the tree byte for byte, and the archive is left exactly as it was")
    void copiesEverySurvivorIntoTheTreeAndLeavesTheArchiveAsItWas(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpus(root, seeds);

        cli.run("run", root.toString());

        List<Path> copies = theCopiesOf(root);
        claim(
                "the tree's copies, in the group's folder beside its page, are exactly the " + TWO_DOCUMENTS
                        + " documents of the archive, byte for byte, and nothing else in the tree carries a"
                        + " document's text -- not the text pulled out of one, which would be a third version of"
                        + " every document",
                () -> {
                    assertThat(copies)
                            .extracting(DeliverableInvocationTest::contentOf)
                            .containsExactlyInAnyOrder(WHAT_A_CORPUS_DOCUMENT_SAYS, WHAT_THE_OTHER_CORPUS_DOCUMENT_SAYS);
                    assertThat(everyFileIn(theTreeOf(root)))
                            .filteredOn(file -> !copies.contains(file))
                            .allSatisfy(file -> assertThat(Files.readString(file))
                                    .doesNotContain(WHAT_A_CORPUS_DOCUMENT_SAYS)
                                    .doesNotContain(WHAT_THE_OTHER_CORPUS_DOCUMENT_SAYS));
                });
        claim(
                "and every row of the listing says its document was copied",
                () -> assertThat(columnOfTheListing(root, THE_COPY_OUTCOME_COLUMN).values())
                        .hasSize(TWO_DOCUMENTS)
                        .containsOnly("copied"));
        claim(
                "and the archive holds exactly the " + TWO_DOCUMENTS + " documents it held before, with the"
                        + " bytes they held: the originals are read and never written, and nothing here writes"
                        + " inside the archive at all",
                () -> assertThat(everyFileIn(root))
                        .extracting(DeliverableInvocationTest::contentOf)
                        .containsExactlyInAnyOrder(WHAT_A_CORPUS_DOCUMENT_SAYS, WHAT_THE_OTHER_CORPUS_DOCUMENT_SAYS));
    }

    /** The tree this corpus's one piece of work wrote, found by that work's own name. */
    private Path theTreeOf(Path root) {
        return workingDirectory
                .resolve(Deliverable.DIRECTORY_NAME)
                .resolve(generationRuns(root).getLast());
    }

    /** The index of that tree, which the closing line names (ADR-274 §7). */
    private Path theIndexFileOf(Path root) {
        return theTreeOf(root).resolve(Deliverable.INDEX_FILE_NAME);
    }

    /** What {@code file} holds, read as text. */
    private static String contentOf(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /**
     * The names of every tree on disk, whichever test wrote it, sorted so two readings compare.
     *
     * <p>The one claim about absence is made by comparing this before and after, because a claim
     * scoped to a run that was never minted is a claim about nothing and passes on an empty database
     * exactly as it passes on a correct one.
     */
    private List<String> everyTreeOnDisk() throws IOException {
        Path deliverables = workingDirectory.resolve(Deliverable.DIRECTORY_NAME);
        if (!Files.isDirectory(deliverables)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(deliverables)) {
            return entries.map(entry -> entry.getFileName().toString()).sorted().toList();
        }
    }

    /** Every file anywhere beneath {@code directory}, which is what a claim about the copies counts. */
    private static List<Path> everyFileIn(Path directory) throws IOException {
        try (Stream<Path> entries = Files.walk(directory)) {
            return entries.filter(Files::isRegularFile).toList();
        }
    }

    /** Each listed document's cell of {@code column}, keyed by the path the listing gives it, read as above. */
    private Map<String, String> columnOfTheListing(Path root, String column) throws IOException {
        List<String> lines = Files.readAllLines(theTreeOf(root).resolve(Deliverable.MANIFEST_FILE_NAME)).stream()
                .filter(line -> !line.isBlank())
                .toList();
        List<String> header = cellsOf(lines.getFirst());
        int path = header.indexOf("path");
        int contentHash = header.indexOf(column);
        Map<String, String> byPath = new LinkedHashMap<>();
        for (String row : lines.subList(1, lines.size())) {
            List<String> cells = cellsOf(row);
            byPath.put(cells.get(path), cells.get(contentHash));
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

    /**
     * Where that page sits, which a claim about a destination relative to it has to follow it from
     * (ADR-135).
     */
    private Path theClusterPageFileOf(Path root) throws IOException {
        Path tree = theTreeOf(root);
        List<Path> pages = everyFileIn(tree).stream()
                .filter(file -> file.getFileName().toString().endsWith(".html"))
                .filter(file -> !file.equals(theIndexFileOf(root)))
                // The HTML tree's pages alone: the plugins' renderings beside it are their own (ADR-333 §3).
                .filter(file -> !tree.relativize(file).getName(0).toString().matches("website|confluence"))
                .toList();
        if (pages.size() != ONE_CLUSTER) {
            throw new IllegalStateException(
                    "this corpus arranges one group and so writes one page, and the tree holds " + pages.size());
        }
        return pages.getFirst();
    }

    /**
     * The copies of the tree this corpus's work wrote: every file in its one group's folder but the page, which is
     * where a copy stands (ADR-280 §0).
     */
    private List<Path> theCopiesOf(Path root) throws IOException {
        Path page = theClusterPageFileOf(root);
        return everyFileIn(page.getParent()).stream().filter(file -> !file.equals(page)).toList();
    }

    /**
     * A corpus of two documents, walked once, with the arrangement it produced approved — the state
     * every test here starts from, none of them being about the gate in front of it.
     */
    private void anApprovedCorpus(Path root, Path seeds) throws IOException {
        aCorpus(root, seeds);
        cli.run("run", root.toString());
        approve(ArrangementGate.shortNameOf(theLatestArrangement(root)));
    }

    /** Two documents, an exemplar, and every gate before this one open. */
    private void aCorpus(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), WHAT_A_CORPUS_DOCUMENT_SAYS);
        Files.writeString(root.resolve("another-corpus-document.txt"), WHAT_THE_OTHER_CORPUS_DOCUMENT_SAYS);
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .relevanceScoreFloor(A_FLOOR_THAT_REMOVES_NOTHING, "set by this test, so nothing earlier is asked for")
                .generationContextWindow(THE_READING_WINDOW, "set by this test, so every test here reads alike")
                .build());
    }

    /** Writes the approval, leaving every other key as the fixture left it. */
    private void approve(String approval) {
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .arrangementApproved(approval, approval == null ? null : "read by this test")
                .build());
    }

    /** The arrangement the most recent invocation over {@code root} recorded. */
    private RunId theLatestArrangement(Path root) {
        return new RunsOver(jdbcTemplate, root).latest("arrangement");
    }

    /**
     * Every record of this step having run over {@code root}, oldest first.
     *
     * <p>Scoped to the walk of this corpus rather than counted across the table. One working directory
     * serves the whole class and the database outlives each method, so an unscoped count would be a
     * claim about every corpus any method here ever walked.
     */
    private List<String> generationRuns(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid",
                String.class,
                "generation",
                Walk.canonicalRoot(root).toString());
    }
}
