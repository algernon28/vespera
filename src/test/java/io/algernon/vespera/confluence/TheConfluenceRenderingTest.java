package io.algernon.vespera.confluence;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.deliverable.ListedTree.anImage;
import static io.algernon.vespera.deliverable.ListedTree.cluster;
import static io.algernon.vespera.deliverable.ListedTree.sha256Of;
import static io.algernon.vespera.deliverable.ListedTree.slotOf;
import static io.algernon.vespera.deliverable.ListedTree.survivor;
import static io.algernon.vespera.deliverable.ListedTree.writing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.algernon.vespera.Adr;
import io.algernon.vespera.deliverable.ClusterPageCeiling;
import io.algernon.vespera.deliverable.Deliverable;
import io.algernon.vespera.deliverable.DeliverableProgress;
import io.algernon.vespera.deliverable.DeliverableProvenance;
import io.algernon.vespera.deliverable.DeliverableRoot;
import io.algernon.vespera.deliverable.ListedCodeSets;
import io.algernon.vespera.deliverable.ListedPicture;
import io.algernon.vespera.deliverable.ListedSurvivor;
import io.algernon.vespera.deliverable.ListedTree;
import io.algernon.vespera.deliverable.NamedValue;
import io.algernon.vespera.deliverable.Plugin;
import io.algernon.vespera.deliverable.SurvivorPictures;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.synthesis.ClusterSlot;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.algernon.vespera.synthesis.SynthesisDoc;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Confluence rendering drawn under {@code confluence/} beside the HTML tree (ADR-301, ADR-309, ADR-310, ADR-312 to
 * ADR-316): its pages' titles, their storage-format bodies, the files each carries, the page list, and what an earlier
 * drawing left in it. Every fixture is synthetic: an archive written here, a run id of the ledger's shape.
 *
 * <p><b>Drawn by its plugin</b> (ADR-333 §1, §2): the rendering and this class moved from {@code deliverable} into
 * {@code confluence}, and the tree is drawn through {@code Deliverable}'s public door with the Confluence plugin handed
 * in, as {@code pipeline} hands it the beans. Every claim stands as it stood. Untagged: CI's {@code
 * -DexcludedGroups=confluence} is for the publish's integration tests alone (ADR-320 §6).
 *
 * <p><b>The report says <em>group</em> and the code says <em>cluster</em></b> (ADR-122).
 */
@Epic("The deliverable")
@Feature("The Confluence rendering")
@Issue("620")
@Link(name = "ADR-333", url = Adr.A_PLUGIN_DRAWS_ONE_RENDERING_AND_DELIVERS_IT_TO_ONE_DESTINATION, type = "adr")
@Link(name = "ADR-301", url = Adr.A_CONFLUENCE_RENDERING_IS_DRAWN_BESIDE_THE_HTML_TREE, type = "adr")
@Link(name = "ADR-309", url = Adr.A_CITATION_ON_A_CONFLUENCE_PAGE_IS_AN_ANCHOR_LINK, type = "adr")
@Link(name = "ADR-310", url = Adr.A_CONFLUENCE_PAGES_TITLE_ENDS_WITH_ITS_PLACE_AND_ITS_RUNS_MARKER, type = "adr")
@Link(name = "ADR-312", url = Adr.THE_CONFLUENCE_RENDERING_LIES_UNDER_CONFLUENCE, type = "adr")
@Link(name = "ADR-313", url = Adr.A_CLUSTERS_CONFLUENCE_PAGE_CARRIES_ITS_COPIES_AS_FILES, type = "adr")
@Link(name = "ADR-314", url = Adr.EACH_PICTURE_IS_A_FILE_OF_ITS_OWN_IN_THE_CONFLUENCE_RENDERING, type = "adr")
@Link(name = "ADR-315", url = Adr.THE_ROOT_PAGE_CARRIES_NEITHER_THE_CORPUS_ROOT_NOR_THE_PROFILE, type = "adr")
@Link(name = "ADR-316", url = Adr.THE_PAGE_LIST_IS_PAGES_JSON, type = "adr")
@Link(name = "ADR-328", url = Adr.THE_CONFLUENCE_RENDERING_IS_ONE_TREE_UPDATED_IN_PLACE_WITH_NO_RUN_ID, type = "adr")
class TheConfluenceRenderingTest {

    private static final OccurrenceId THE_SEED = new OccurrenceId(1);

    private static final String SEED_PATH = "seeds/Fire Safety.docx";

    /** The marker ADR-310 derived from {@link ListedTree#RUN_ID}, its first twelve characters; no page carries it (ADR-328 §2). */
    private static final String MARKER = "aaaaaaaaaaaa";

    /** A name of the corpus root, the working directory and a profile value no page may carry (ADR-315 §1). */
    private static final String THE_CORPUS_ROOT = "corpus-root-5f1c9e";

    private static final String THE_WORKING_DIRECTORY = "working-directory-2b7d40";

    private static final String THE_PROFILE_KEY = "profileKeyNamedC81";

    private static final String THE_PROFILE_VALUE = "profile-value-93ad17";

    private static final RecordedCluster DOORS = cluster(THE_SEED, 0, "Fire doors", 1, 4);

    private static final RecordedCluster DAMPERS = cluster(THE_SEED, 1, "Smoke dampers", 2, 1);

    /** Cites each entry, a number no entry has, and one entry twice; a list and a heading among them. */
    private static final String PROSE = "Doors [1], frames [2] & closers [3]; see [9].\n\n## On [4]\n\n- first [1]\n- second";

    private static final String DOORS_TITLE = "Fire doors & their inspection";

    private static final byte[] THE_DIAGRAM = anImage("png", 4, 3);

    private static final byte[] A_SCAN = anImage("png", 5, 5);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** The one plugin the tree is drawn with here: the Confluence rendering's, its publisher never asked to send. */
    private static final List<Plugin> THE_CONFLUENCE_PLUGIN =
            List.of(new ConfluencePlugin(new Publisher("", "", "", "")));

    @Test
    @Story("Every body is storage format")
    @DisplayName("Every body of the rendering is well-formed XML once its two prefixes are declared")
    void everyBodyIsWellFormedXml(@TempDir Path base) throws IOException {
        Path tree = draw(base, picturesOfTheFirst());
        List<Path> bodies = bodiesUnder(tree.resolve(ConfluencePages.DIRECTORY_NAME));

        claim(
                "four bodies are drawn, the root page's, the seed partition's and each group's",
                () -> assertThat(bodies).hasSize(4));
        claim(
                "each parses as XML inside an element declaring ac and ri, its escaped ampersands, CDATA and"
                        + " self-closed elements included",
                () -> assertThat(bodies).allSatisfy(body -> assertThatCode(() -> parsed(body)).doesNotThrowAnyException()));
    }

    @Test
    @Story("The root page keeps the operator's machine and profile off the site")
    @DisplayName("Neither the page list nor any body names the corpus root, the working directory or a profile key or value")
    void nothingNamesTheCorpusRootTheWorkingDirectoryOrAProfileValue(@TempDir Path base) throws IOException {
        Path tree = draw(base, picturesOfTheFirst());
        List<Path> written = new ArrayList<>(bodiesUnder(tree.resolve(ConfluencePages.DIRECTORY_NAME)));
        written.add(tree.resolve(ConfluencePages.DIRECTORY_NAME).resolve(ConfluencePages.PAGE_LIST_FILE_NAME));

        claim(
                "the HTML index does state the corpus root and the profile, so the strings are in reach of the drawing",
                () -> assertThat(Files.readString(tree.resolve(Deliverable.INDEX_FILE_NAME)))
                        .contains(THE_CORPUS_ROOT, THE_PROFILE_KEY, THE_PROFILE_VALUE));
        claim(
                "no body and not the page list holds any of the four distinctive strings",
                () -> assertThat(written).allSatisfy(file -> assertThat(Files.readString(file, StandardCharsets.UTF_8))
                        .doesNotContain(THE_CORPUS_ROOT, THE_WORKING_DIRECTORY, THE_PROFILE_KEY, THE_PROFILE_VALUE)));
    }

    @Test
    @Story("No page carries the run's id")
    @DisplayName("No title, key, body or file name of the rendering holds the run's id or its first twelve characters, and the HTML index still names the run")
    void noPageCarriesTheRunsIdOrItsMarkerAndTheHtmlIndexStillNamesTheRun(@TempDir Path base) throws IOException {
        Path tree = draw(base, picturesOfTheFirst());
        JsonNode list = pageList(tree);
        List<String> sent = new ArrayList<>();
        for (JsonNode page : list.get("pages")) {
            sent.add(page.get("key").asString());
            sent.add(page.get("title").asString());
            page.get("files").valueStream().forEach(file -> sent.add(file.get("name").asString()));
        }
        for (Path body : bodiesUnder(tree.resolve(ConfluencePages.DIRECTORY_NAME))) {
            sent.add(Files.readString(body, StandardCharsets.UTF_8));
        }

        claim(
                "no key, title, file name or body a publish sends holds the run's first twelve characters, with which"
                        + " its whole id begins",
                () -> assertThat(sent).isNotEmpty().allSatisfy(text -> assertThat(text).doesNotContain(MARKER)));
        claim(
                "the HTML index still opens with the run's whole id under its heading, the HTML tree being unchanged",
                () -> assertThat(Files.readString(tree.resolve(Deliverable.INDEX_FILE_NAME), StandardCharsets.UTF_8))
                        .contains("<h1>Deliverable " + ListedTree.RUN_ID + "</h1>"));
    }

    @Test
    @Story("The page list names every page and file")
    @DisplayName("Two drawings of one generation run give every page the same key, title and body")
    void aRedrawingOfTheSameRunKeepsEveryKey(@TempDir Path base) throws IOException {
        Path tree = draw(base, picturesOfTheFirst());
        String first = Files.readString(tree.resolve(ConfluencePages.DIRECTORY_NAME).resolve(ConfluencePages.PAGE_LIST_FILE_NAME));
        Path again = draw(base, picturesOfTheFirst());
        String second = Files.readString(again.resolve(ConfluencePages.DIRECTORY_NAME).resolve(ConfluencePages.PAGE_LIST_FILE_NAME));

        claim(
                "the page list is the same, byte for byte, so each key, title and body digest is kept",
                () -> assertThat(second).isEqualTo(first));
    }

    /** The fixture drawn into {@code base}: its archive, its working directory and its tree. */
    private static Path draw(Path base, SurvivorPictures pictures) throws IOException {
        return draw(base, pictures, "d.txt", DeliverableProgress.NONE);
    }

    private static Path draw(Path base, SurvivorPictures pictures, String fourthName) throws IOException {
        return draw(base, pictures, fourthName, DeliverableProgress.NONE);
    }

    private static Path draw(Path base, SurvivorPictures pictures, DeliverableProgress progress) throws IOException {
        return draw(base, pictures, "d.txt", progress);
    }

    /**
     * Two groups of one seed: the doors, written over and citing its four documents, one an image, one gone from the
     * archive, the fourth named {@code fourthName}; and the dampers, nothing written over them.
     */
    private static Path draw(Path base, SurvivorPictures pictures, String fourthName, DeliverableProgress progress)
            throws IOException {
        return draw(base, pictures, fourthName, progress, THE_CONFLUENCE_PLUGIN);
    }

    /** As above, {@code plugins} drawing beside the tree. */
    private static Path draw(
            Path base, SurvivorPictures pictures, String fourthName, DeliverableProgress progress, List<Plugin> plugins)
            throws IOException {
        Path archive = base.resolve(THE_CORPUS_ROOT);
        Files.createDirectories(archive.resolve("reports"));
        Files.createDirectories(archive.resolve("notes"));
        Files.writeString(archive.resolve("reports/a.docx"), "the first document");
        Files.write(archive.resolve("reports/b.png"), A_SCAN);
        Files.writeString(archive.resolve("reports/" + fourthName), "the fourth document");
        Files.writeString(archive.resolve("notes/damper.txt"), "a damper");
        List<ListedSurvivor> survivors = List.of(
                survivor(10, "reports/a.docx", sha256Of(bytes("the first document")), THE_SEED, SEED_PATH, 0, 0.9),
                survivor(11, "reports/b.png", sha256Of(A_SCAN), THE_SEED, SEED_PATH, 0, 0.8),
                survivor(12, "reports/c.docx", sha256Of(bytes("never there")), THE_SEED, SEED_PATH, 0, 0.7),
                survivor(13, "reports/" + fourthName, sha256Of(bytes("the fourth document")), THE_SEED, SEED_PATH, 0, 0.6),
                survivor(20, "notes/damper.txt", sha256Of(bytes("a damper")), THE_SEED, SEED_PATH, 1, 0.5));
        Map<ClusterSlot, SynthesisDoc> written = Map.of(slotOf(DOORS), writing(DOORS_TITLE, PROSE, 10, 11, 12, 13));
        return Deliverable.writeTo(
                base.resolve(THE_WORKING_DIRECTORY),
                DeliverableRoot.UNNAMED,
                new DeliverableProvenance(
                        ListedTree.RUN_ID, ListedTree.WALK, archive.toString(),
                        List.of(new NamedValue(THE_PROFILE_KEY, THE_PROFILE_VALUE))),
                ListedTree.of(List.of(DOORS, DAMPERS), written, survivors, Map.of()),
                ListedCodeSets.NONE,
                pictures,
                new ClusterPageCeiling.Unset(),
                plugins,
                progress);
    }

    /** The first document carries the diagram; no other carries a picture. */
    private static SurvivorPictures picturesOfTheFirst() {
        return survivor -> survivor.occurrence().value() == 10
                ? List.of(new ListedPicture("image/png", THE_DIAGRAM, false, "a diagram of <doors> & frames"))
                : List.of();
    }

    private static JsonNode pageList(Path tree) throws IOException {
        return JSON.readTree(tree.resolve(ConfluencePages.DIRECTORY_NAME).resolve(ConfluencePages.PAGE_LIST_FILE_NAME).toFile());
    }

    private static List<Path> bodiesUnder(Path confluence) throws IOException {
        try (Stream<Path> walk = Files.walk(confluence)) {
            return walk.filter(file -> file.getFileName().toString().endsWith(".xml")).sorted().toList();
        }
    }

    private static Object parsed(Path body) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        String whole = "<body xmlns:ac=\"urn:ac\" xmlns:ri=\"urn:ri\">" + Files.readString(body, StandardCharsets.UTF_8)
                + "</body>";
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(whole.getBytes(StandardCharsets.UTF_8)));
    }

    private static List<String> groups(Pattern pattern, String text, int group) {
        Matcher matcher = pattern.matcher(text);
        List<String> found = new ArrayList<>();
        while (matcher.find()) {
            found.add(matcher.group(group));
        }
        return found;
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
