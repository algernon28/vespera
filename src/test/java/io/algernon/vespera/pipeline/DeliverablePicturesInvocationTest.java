package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.synthesis.ClusterFaults;
import io.algernon.vespera.synthesis.Deliverable;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A survivor's pictures, carried by a whole invocation from the extraction cache to its cluster file
 * (ADR-149, #285).
 *
 * <p>{@code DeliverablePicturesTest} holds every claim about what the writer does with pictures it is
 * handed. What only an invocation can show is that it is handed them at all: that stage 2's cached
 * response for a document is found again at stage 6b through that document's own file, and that what
 * comes out of it lands on the right page, under the right entry, as a file the page links to.
 *
 * <p><b>Why a sibling class with its own converter double.</b> The double the rest of this package
 * shares answers every document with one response, so every document would carry the same pictures,
 * and the furniture rule would rightly remove all of them: a test built on it would pass on a writer
 * that never shows a picture. {@link PictureScriptedExtractionBeans} answers by file name instead, so
 * exactly one corpus document carries a picture the other does not, and both carry one picture in
 * common. Changing the shared double's default answer would have put pictures into every tree this
 * package writes.
 *
 * <p><b>One static working directory serves the whole class</b>, as in every invocation test here, so
 * the tree is found through the run recorded against this test's own walk, never by listing the
 * directory.
 *
 * <p><b>The report says <em>group</em> and the code says <em>cluster</em></b> (ADR-122).
 */
@CascadeSliceTest
@Import({ClusterFaults.class, PictureScriptedExtractionBeans.class})
@Epic("Synthesis")
@Feature("The pictures a document carries")
@Issue("285")
@Link(name = "ADR-149", url = Adr.A_SURVIVORS_PICTURES_REACH_ITS_CLUSTER_FILE, type = "adr")
@Link(name = "ADR-103", url = Adr.THE_DELIVERABLE_IS_A_MARKDOWN_TREE_PER_RUN, type = "adr")
@Link(name = "ADR-135", url = Adr.A_MEMBERSHIP_ENTRY_LINKS_RELATIVELY_OR_NOT_AT_ALL, type = "adr")
class DeliverablePicturesInvocationTest {

    /** A floor of 1.0 opens stage 4's gate the way every other invocation test opens it. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The embedding model this fixture names, so gate 3 and everything behind it open. */
    private static final String EMBEDDING_MODEL_NAME = "qwen3-embedding:0.6b";

    /** A relevance floor of zero, which removes nothing, so both corpus documents reach the tree. */
    private static final String A_FLOOR_THAT_REMOVES_NOTHING = "0.0";

    /** How much every call in this class may read. */
    private static final String THE_READING_WINDOW = "4096";

    /** What one archive, read one way, is worth: one piece of work, and one tree named after it. */
    private static final int ONE_RUN = 1;

    /** The two corpus documents, both of which survive into the one group this corpus arranges. */
    private static final int TWO_DOCUMENTS = 2;

    /** The two documents and a third, a standalone image file or a report, where a test adds one. */
    private static final int THREE_DOCUMENTS = 3;

    /** The standalone image file's size in pixels: any real PNG, which stage 1 detects as an image. */
    private static final int SCREENSHOT_WIDTH = 16;

    private static final int SCREENSHOT_HEIGHT = 16;

    /** How many pages the tree holds: this corpus arranges one group. */
    private static final int ONE_PAGE = 1;

    /** How many hexadecimal characters of a picture's digest name its file. */
    private static final int NAME_LENGTH = 16;

    /** An image on a page, as its alt text and its destination. */
    private static final Pattern AN_IMAGE = Pattern.compile("!\\[((?:\\\\.|[^\\]\\\\])*)\\]\\(([^)]*)\\)");

    /** Where one membership entry begins: its number and its anchor. */
    private static final Pattern AN_ENTRY = Pattern.compile("(?m)^\\d+\\. <a id=\"document-\\d+\"></a>");

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

    /**
     * Drops what an earlier class scripted, before each test as well as after it: the script is static
     * on a fixture this whole package shares, and class order is not a thing any test here decides.
     */
    @BeforeEach
    void forgetWhatAnEarlierClassScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    @AfterEach
    void forgetWhatWasScripted() {
        GenerationScriptedBeans.forgetScriptedAnswers();
    }

    @Test
    @Story("A picture only one document carries is shown under that document")
    @DisplayName("A picture one document's conversion carried reaches that document's entry as a file beside the page, and one both carried is left out")
    void carriesAPictureFromTheConversionToItsDocumentsEntryAndLeavesOutTheOneEveryDocumentCarries(
            @TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedCorpus(root, seeds);

        cli.run("run", root.toString());

        claim(
                "the invocation reports success, and exactly " + ONE_RUN + " piece of work stands behind the"
                        + " tree the claims below read",
                () -> {
                    assertThat(cli.getExitCode()).isZero();
                    assertThat(generationRuns(root)).hasSize(ONE_RUN);
                });
        Path page = theOnePageOf(root);
        String text = Files.readString(page);
        String stem = page.getFileName().toString().replaceFirst("\\.md$", "");
        String diagramName = nameOf(PictureScriptedExtractionBeans.THE_DIAGRAM);
        String letterheadName = nameOf(PictureScriptedExtractionBeans.THE_LETTERHEAD);
        Path diagramFile = page.resolveSibling(stem).resolve(diagramName);

        claim(
                "both corpus documents reached the group's page, which the claims below depend on: a"
                        + " picture can only be left out as shared if both documents that share it are there",
                () -> assertThat(text)
                        .contains(PictureScriptedExtractionBeans.THE_DOCUMENT_WITH_A_DIAGRAM)
                        .contains(PictureScriptedExtractionBeans.THE_DOCUMENT_WITH_ONLY_THE_LETTERHEAD));
        claim(
                "the diagram only one document's conversion carried is written into the tree, in a"
                        + " directory beside the group's page named after it, holding exactly the pixels the"
                        + " converter returned: read out of what stage 2 stored, with no conversion asked"
                        + " for again",
                () -> assertThat(diagramFile).isRegularFile().hasBinaryContent(PictureScriptedExtractionBeans.THE_DIAGRAM));
        claim(
                "and the page shows it inside the entry of the document it came from, and not inside the"
                        + " other document's entry",
                () -> assertThat(theEntryNaming(text, PictureScriptedExtractionBeans.THE_DOCUMENT_WITH_A_DIAGRAM))
                        .contains("](" + stem + "/" + diagramName + ")"));
        claim(
                "and the only image on the page leads, by a relative path followed from the page's own"
                        + " directory, to that file: nothing outside the tree and nothing on a network is"
                        + " needed to see it",
                () -> assertThat(whereEveryImageLeads(page)).containsExactly(diagramFile));
        claim(
                "the letterhead both documents' conversions carried is written nowhere in the tree and"
                        + " linked from nowhere on the page: a picture every document carries is furniture,"
                        + " not information",
                () -> {
                    assertThat(everyFileIn(theTreeOf(root)))
                            .noneMatch(file -> file.getFileName().toString().equals(letterheadName));
                    assertThat(text).doesNotContain(letterheadName);
                });
    }

    /**
     * ADR-150 §4: a standalone image file that survives shows its entry and nothing else, although its
     * conversion carries a picture that recurs nowhere. That picture is a re-encoded crop of the
     * original, and writing it would be the copy of an original ADR-104 refuses. The picture's bytes
     * are no image, so no rule that decodes a picture can be what leaves it out: only the file's
     * detected format can.
     */
    @Test
    @Story("A standalone image file shows its entry and nothing else")
    @DisplayName("An image file that survived shows its entry, and none of the pictures its conversion carried")
    @Issue("286")
    @Link(name = "ADR-150", url = Adr.A_PDFS_PICTURES_ARE_ASKED_FOR_AS_EMBEDDED_PIXELS, type = "adr")
    @Link(name = "ADR-104", url = Adr.THE_ORIGINALS_STAY_WHERE_THEY_ARE_AND_ARE_REFERENCED, type = "adr")
    void showsAnImageFileAsItsEntryAndNothingElse(@TempDir Path root, @TempDir Path seeds) throws IOException {
        anApprovedCorpusWithAScreenshot(root, seeds);

        cli.run("run", root.toString());

        claim(
                "the invocation reports success",
                () -> assertThat(cli.getExitCode()).isZero());
        Path page = theOnePageOf(root);
        String text = Files.readString(page);
        String cropName = nameOf(PictureScriptedExtractionBeans.THE_SCREENSHOTS_CROP);

        claim(
                "the image file reached the group's page as a membership entry of its own",
                () -> assertThat(text).contains(PictureScriptedExtractionBeans.THE_SCREENSHOT));
        claim(
                "but the picture its conversion carried is written nowhere in the tree, although no other"
                        + " document carries it: it is a copy of the original, which stays in the archive",
                () -> assertThat(everyFileIn(theTreeOf(root)))
                        .noneMatch(file -> file.getFileName().toString().equals(cropName)));
        claim(
                "and the image file's entry shows no image at all",
                () -> assertThat(theEntryNaming(text, PictureScriptedExtractionBeans.THE_SCREENSHOT))
                        .doesNotContain("!["));
        claim(
                "while the diagram of the other document is still shown, so the image file costs its"
                        + " neighbours nothing",
                () -> assertThat(text).contains(nameOf(PictureScriptedExtractionBeans.THE_DIAGRAM)));
    }

    /**
     * ADR-150 §3(d) and §5, through a whole invocation: a report converted as Docling converts a PDF
     * carries a header picture cropped from pages one and two at the same box, and one chart. The two
     * crops are different bytes, and their hashes are too far apart for a near-copy, so only where they
     * sat can make them furniture: that is read from each picture's first position in the cached
     * response, carried into the picture records, and compared by the writer. The boxes' edges share
     * their whole-number parts and none equals a page number, so a reading that took an edge for the
     * page would put both crops on one page, and they would be written.
     */
    @Test
    @Story("A picture repeated at one place in its document is furniture and is left out")
    @DisplayName("A report converted as a PDF shows its chart, and leaves out the header picture it repeats at one place on two pages")
    @Issue("286")
    @Link(name = "ADR-150", url = Adr.A_PDFS_PICTURES_ARE_ASKED_FOR_AS_EMBEDDED_PIXELS, type = "adr")
    void showsAPdfsChartAndLeavesOutTheHeaderItRepeatsAtOnePlace(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        anApprovedCorpusWithAReport(root, seeds);

        cli.run("run", root.toString());

        claim(
                "the invocation reports success",
                () -> assertThat(cli.getExitCode()).isZero());
        Path page = theOnePageOf(root);
        String text = Files.readString(page);
        String stem = page.getFileName().toString().replaceFirst("\\.md$", "");
        String chartName = nameOf(PictureScriptedExtractionBeans.THE_CHART);
        Path chartFile = page.resolveSibling(stem).resolve(chartName);
        List<String> headerNames = List.of(
                nameOf(PictureScriptedExtractionBeans.THE_HEADER_ON_PAGE_ONE),
                nameOf(PictureScriptedExtractionBeans.THE_HEADER_ON_PAGE_TWO));

        claim(
                "the header's two crops are different bytes, so the rule for a picture whose bytes recur"
                        + " cannot be what leaves them out",
                () -> assertThat(PictureScriptedExtractionBeans.THE_HEADER_ON_PAGE_ONE)
                        .isNotEqualTo(PictureScriptedExtractionBeans.THE_HEADER_ON_PAGE_TWO));
        claim(
                "the chart is written into the tree beside the group's page, holding exactly the pixels the"
                        + " converter returned",
                () -> assertThat(chartFile).isRegularFile().hasBinaryContent(PictureScriptedExtractionBeans.THE_CHART));
        claim(
                "and it is shown inside the report's own entry",
                () -> assertThat(theEntryNaming(text, PictureScriptedExtractionBeans.THE_REPORT))
                        .contains("](" + stem + "/" + chartName + ")"));
        claim(
                "while neither crop of the header is written anywhere in the tree or linked from the page:"
                        + " each sits on another page within a point of the other's box, and a picture that"
                        + " repeats at one place from page to page is page furniture",
                () -> {
                    assertThat(everyFileIn(theTreeOf(root)))
                            .noneMatch(file -> headerNames.contains(file.getFileName().toString()));
                    assertThat(text).doesNotContain(headerNames.get(0)).doesNotContain(headerNames.get(1));
                });
    }

    /** The one membership entry of {@code page} naming {@code document}, from its number to the next. */
    private static String theEntryNaming(String page, String document) {
        Matcher entry = AN_ENTRY.matcher(page);
        List<Integer> starts = new ArrayList<>();
        while (entry.find()) {
            starts.add(entry.start());
        }
        starts.add(page.length());
        for (int at = 0; at < starts.size() - 1; at++) {
            String body = page.substring(starts.get(at), starts.get(at + 1));
            if (body.contains(document)) {
                return body;
            }
        }
        throw new IllegalStateException("no entry on the page names " + document);
    }

    /**
     * Where every image on {@code page} leads, each destination refused if it carries a scheme or is
     * rooted, then decoded and followed from the page's own directory, as a renderer follows it.
     */
    private static List<Path> whereEveryImageLeads(Path page) throws IOException {
        Matcher image = AN_IMAGE.matcher(Files.readString(page));
        List<Path> targets = new ArrayList<>();
        while (image.find()) {
            URI destination = URI.create(image.group(2));
            if (destination.isAbsolute() || destination.getPath().startsWith("/")) {
                throw new IllegalStateException("an image on " + page + " is not relative: " + destination);
            }
            targets.add(page.getParent().resolve(destination.getPath()).normalize());
        }
        return List.copyOf(targets);
    }

    /** The file name ADR-149 gives a picture: sixteen hex characters of its digest, then {@code .png}. */
    private static String nameOf(byte[] pixels) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(pixels);
            return HexFormat.of().formatHex(digest).substring(0, NAME_LENGTH) + ".png";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
    }

    /** The tree this corpus's one piece of work wrote, found by that work's own name. */
    private Path theTreeOf(Path root) {
        return workingDirectory.resolve(Deliverable.DIRECTORY_NAME).resolve(generationRuns(root).getLast());
    }

    /** The one cluster page of that tree: the one Markdown file that is not the index. */
    private Path theOnePageOf(Path root) throws IOException {
        List<Path> pages = everyFileIn(theTreeOf(root)).stream()
                .filter(file -> file.getFileName().toString().endsWith(".md"))
                .filter(file -> !file.getFileName().toString().equals(Deliverable.INDEX_FILE_NAME))
                .toList();
        if (pages.size() != ONE_PAGE) {
            throw new IllegalStateException(
                    "this corpus arranges one group and so writes one page, and the tree holds " + pages.size());
        }
        return pages.getFirst();
    }

    private static List<Path> everyFileIn(Path directory) throws IOException {
        try (Stream<Path> entries = Files.walk(directory)) {
            return entries.filter(Files::isRegularFile).toList();
        }
    }

    /**
     * The two corpus documents, a seed, every gate before 6b open, walked once, and the arrangement it
     * produced approved: the state the first test here starts from.
     */
    private void anApprovedCorpus(Path root, Path seeds) throws IOException {
        theTwoDocumentsIn(root);
        approveTheCorpus(root, seeds, TWO_DOCUMENTS);
    }

    /**
     * {@link #anApprovedCorpus(Path, Path)}, with a standalone image file beside the two documents
     * (ADR-150 §4): a real PNG, which stage 1 detects as an image.
     */
    private void anApprovedCorpusWithAScreenshot(Path root, Path seeds) throws IOException {
        theTwoDocumentsIn(root);
        ImageIO.write(
                new BufferedImage(SCREENSHOT_WIDTH, SCREENSHOT_HEIGHT, BufferedImage.TYPE_INT_RGB),
                "png",
                root.resolve(PictureScriptedExtractionBeans.THE_SCREENSHOT).toFile());
        approveTheCorpus(root, seeds, THREE_DOCUMENTS);
    }

    /**
     * {@link #anApprovedCorpus(Path, Path)}, with a report beside the two documents whose conversion
     * answers as Docling answers for a PDF, pictures, pages and boxes included.
     */
    private void anApprovedCorpusWithAReport(Path root, Path seeds) throws IOException {
        theTwoDocumentsIn(root);
        Files.writeString(root.resolve(PictureScriptedExtractionBeans.THE_REPORT), "a report with a chart");
        approveTheCorpus(root, seeds, THREE_DOCUMENTS);
    }

    private static void theTwoDocumentsIn(Path root) throws IOException {
        Files.writeString(root.resolve(PictureScriptedExtractionBeans.THE_DOCUMENT_WITH_A_DIAGRAM), "a document with a diagram");
        Files.writeString(
                root.resolve(PictureScriptedExtractionBeans.THE_DOCUMENT_WITH_ONLY_THE_LETTERHEAD),
                "a document with only the letterhead");
    }

    /**
     * A seed, every gate before 6b open, the corpus in {@code root} walked once, and the arrangement it
     * produced approved, once it is shown to hold all {@code arranged} corpus documents.
     */
    private void approveTheCorpus(Path root, Path seeds, int arranged) throws IOException {
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        Profile profile = profileStore.load();
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profile.degenerateOutputConfidenceFloor())
                .boilerplateDocumentFrequencyFloor(BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EMBEDDING_MODEL_NAME, "set by this test, so gate 3 is open")
                .relevanceScoreFloor(A_FLOOR_THAT_REMOVES_NOTHING, "set by this test, so nothing earlier is asked for")
                .generationContextWindow(THE_READING_WINDOW, "set by this test")
                .build());
        cli.run("run", root.toString());
        String approval = ArrangementGate.shortNameOf(theLatestArrangement(root));
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .arrangementApproved(approval, "read by this test")
                .build());
        claim(
                "the first invocation arranged every one of the " + arranged + " corpus documents, so the"
                        + " approved arrangement holds every document whose pictures the claims below read",
                () -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM document_cluster WHERE run_id ="
                                        + " (SELECT upstream_run_id FROM run_upstream WHERE run_id = ?)",
                                Integer.class,
                                theLatestArrangement(root).value()))
                        .isEqualTo(arranged));
    }

    /** The arrangement the most recent invocation over {@code root} recorded. */
    private RunId theLatestArrangement(Path root) {
        return new RunId(jdbcTemplate.queryForObject(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid DESC LIMIT 1",
                String.class,
                "arrangement",
                Walk.canonicalRoot(root).toString()));
    }

    /** Every record of stage 6b having run over {@code root}, oldest first, scoped to this walk. */
    private List<String> generationRuns(Path root) {
        return jdbcTemplate.queryForList(
                "SELECT r.id FROM run r JOIN walk w ON w.id = r.walk_id"
                        + " WHERE r.stage = ? AND w.root = ? ORDER BY r.rowid",
                String.class,
                "generation",
                Walk.canonicalRoot(root).toString());
    }
}
