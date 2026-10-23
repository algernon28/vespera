package io.algernon.vespera.deliverable;

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
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.synthesis.ClusterSlot;
import io.algernon.vespera.synthesis.DeliverableLanguage;
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
import java.util.Base64;
import java.util.HashMap;
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
 * <p><b>The report says <em>group</em> and the code says <em>cluster</em></b> (ADR-122).
 */
@Epic("The deliverable")
@Feature("The Confluence rendering")
@Issue("620")
@Link(name = "ADR-301", url = Adr.A_CONFLUENCE_RENDERING_IS_DRAWN_BESIDE_THE_HTML_TREE, type = "adr")
@Link(name = "ADR-309", url = Adr.A_CITATION_ON_A_CONFLUENCE_PAGE_IS_AN_ANCHOR_LINK, type = "adr")
@Link(name = "ADR-310", url = Adr.A_CONFLUENCE_PAGES_TITLE_ENDS_WITH_ITS_PLACE_AND_ITS_RUNS_MARKER, type = "adr")
@Link(name = "ADR-312", url = Adr.THE_CONFLUENCE_RENDERING_LIES_UNDER_CONFLUENCE, type = "adr")
@Link(name = "ADR-313", url = Adr.A_CLUSTERS_CONFLUENCE_PAGE_CARRIES_ITS_COPIES_AS_FILES, type = "adr")
@Link(name = "ADR-314", url = Adr.EACH_PICTURE_IS_A_FILE_OF_ITS_OWN_IN_THE_CONFLUENCE_RENDERING, type = "adr")
@Link(name = "ADR-315", url = Adr.THE_ROOT_PAGE_CARRIES_NEITHER_THE_CORPUS_ROOT_NOR_THE_PROFILE, type = "adr")
@Link(name = "ADR-316", url = Adr.THE_PAGE_LIST_IS_PAGES_JSON, type = "adr")
class TheConfluenceRenderingTest {

    private static final OccurrenceId THE_SEED = new OccurrenceId(1);

    private static final String SEED_PATH = "seeds/Fire Safety.docx";

    /** The marker ADR-310 derives from {@link ListedTree#RUN_ID}: its first twelve characters. */
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

    /** The picture's file's name, from the first sixteen characters of its digest (ADR-314 §3). */
    private static final String THE_DIAGRAMS_FILE = "picture-" + sha256Of(THE_DIAGRAM).substring(0, 16) + ".png";

    private static final byte[] A_SCAN = anImage("png", 5, 5);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final Pattern AN_ANCHOR_MACRO = Pattern.compile(
            "<li><ac:structured-macro ac:name=\"anchor\"><ac:parameter ac:name=\"\">document-(\\d+)</ac:parameter>"
                    + "</ac:structured-macro>");

    private static final Pattern A_STORAGE_CITATION = Pattern.compile(
            "<ac:link ac:anchor=\"document-(\\d+)\"><ri:page ri:content-title=\"([^\"]*)\" />"
                    + "<ac:plain-text-link-body><!\\[CDATA\\[\\[(\\d+)\\]\\]\\]></ac:plain-text-link-body></ac:link>");

    private static final Pattern AN_HTML_CITATION = Pattern.compile("<a href=\"[^\"]*#document-(\\d+)\">\\[(\\d+)\\]</a>");

    private static final Pattern AN_HTML_ENTRY = Pattern.compile("<li id=\"document-(\\d+)\">");

    private static final Pattern AN_HTML_PICTURE = Pattern.compile("<img src=\"data:image/png;base64,([^\"]+)\"");

    private static final Pattern A_STORAGE_PICTURE =
            Pattern.compile("<ac:image ac:alt=\"[^\"]*\"><ri:attachment ri:filename=\"([^\"]+)\" />");

    @Test
    @Story("Every page is titled by its text, its place and its run")
    @DisplayName("The root page, the seed partition's page and each group's page are titled as ADR-310 has it")
    void everyPageIsTitledByItsTextItsPlaceAndItsRunsMarker(@TempDir Path base) throws IOException {
        Path tree = draw(base, SurvivorPictures.none());

        claim(
                "the page list titles the root page Vespera and the marker, the seed partition's page by its seed's stem"
                        + " unslugged, a written group by its title and an unwritten one by its label, each with its"
                        + " place and the marker",
                () -> assertThat(titlesIn(pageList(tree))).containsExactly(
                        "Vespera " + MARKER,
                        "Fire Safety (1 · " + MARKER + ")",
                        DOORS_TITLE + " (1.1 · " + MARKER + ")",
                        "Smoke dampers (1.2 · " + MARKER + ")"));
    }

    @Test
    @Story("Every page is titled by its text, its place and its run")
    @DisplayName("A title past 255 characters is cut in its text, the suffix kept whole, and control characters and runs of spaces fold to one space")
    void aLongTitleIsCutInItsTextAndItsSuffixIsKeptWhole() {
        String suffix = " (03.12 page 2 · " + MARKER + ")";
        String cut = ConfluenceTitles.cluster("x".repeat(300), "03.12", 2, MARKER, DeliverableLanguage.ENGLISH);
        String astral = ConfluenceTitles.cluster("😀".repeat(200), "03.12", 2, MARKER, DeliverableLanguage.ENGLISH);
        String folded = ConfluenceTitles.cluster(
                " Doors\tand\u0007\n frames  ", "1.1", 1, MARKER, DeliverableLanguage.ENGLISH);

        claim(
                "a title of 300 letters is 255 code units: the text cut, an ellipsis, and the suffix whole",
                () -> assertThat(cut).hasSize(255).endsWith("…" + suffix).startsWith("xxx"));
        claim(
                "a cut never splits a surrogate pair, so the title is at most 255 code units and every pair is whole",
                () -> {
                    assertThat(astral.length()).isLessThanOrEqualTo(255);
                    assertThat(astral).endsWith("…" + suffix);
                    assertThat(astral.substring(0, astral.length() - suffix.length() - 1).codePoints())
                            .allMatch(point -> point == "😀".codePointAt(0));
                });
        claim(
                "control characters become spaces, runs of whitespace one space, and the text is trimmed",
                () -> assertThat(folded).isEqualTo("Doors and frames (1.1 · " + MARKER + ")"));
        claim(
                "a text empty after folding leaves the suffix alone",
                () -> assertThat(ConfluenceTitles.cluster(" \t ", "1.1", 1, MARKER, DeliverableLanguage.ENGLISH)).isEqualTo("(1.1 · " + MARKER + ")"));
    }

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
    @Story("A citation lands on its document")
    @DisplayName("Each entry opens with its anchor macro, and each citation is an ac:link to its anchor naming its page's title and no space")
    void eachCitationIsAnAnchorLinkNamingItsEntrysPage(@TempDir Path base) throws IOException {
        Path tree = draw(base, SurvivorPictures.none());
        String body = Files.readString(doorsFolder(tree).resolve(ConfluencePages.BODY_NAME), StandardCharsets.UTF_8);
        String title = DOORS_TITLE + " (1.1 · " + MARKER + ")";

        claim(
                "the four entries open with anchor macros named document-1 to document-4, in order",
                () -> assertThat(groups(AN_ANCHOR_MACRO, body, 1)).containsExactly("1", "2", "3", "4"));
        claim(
                "each citation of the writing is an ac:link to its anchor naming this page's title, [9] which no entry"
                        + " has included, its text the number in CDATA",
                () -> {
                    assertThat(groups(A_STORAGE_CITATION, body, 1)).containsExactly("1", "2", "3", "9", "4", "1");
                    assertThat(groups(A_STORAGE_CITATION, body, 2)).containsOnly(Html.escape(title));
                });
        claim(
                "no link names a space, and no anchor is derived from a heading: every anchor named is document-n",
                () -> {
                    assertThat(body).doesNotContain("ri:space-key");
                    assertThat(groups(Pattern.compile("ac:anchor=\"([^\"]*)\""), body, 1))
                            .allMatch(anchor -> anchor.matches("document-\\d+"));
                });
    }

    @Test
    @Story("Both renderings carry the same entries and citations")
    @DisplayName("A group's HTML page and its Confluence page carry the same entries, citations and pictures in the same order")
    void bothRenderingsCarryTheSameEntriesAndCitations(@TempDir Path base) throws IOException {
        Path tree = draw(base, picturesOfTheFirst());
        String html = Files.readString(tree.resolve("1-fire-safety/1-fire-doors/index.html"), StandardCharsets.UTF_8);
        String storage = Files.readString(doorsFolder(tree).resolve(ConfluencePages.BODY_NAME), StandardCharsets.UTF_8);

        claim(
                "the entries are the same numbers in the same order",
                () -> assertThat(groups(AN_ANCHOR_MACRO, storage, 1)).isEqualTo(groups(AN_HTML_ENTRY, html, 1)));
        claim(
                "the citations name the same entries in the same order",
                () -> assertThat(groups(A_STORAGE_CITATION, storage, 1)).isEqualTo(groups(AN_HTML_CITATION, html, 1)));
        claim(
                "the pictures are the same in the same order, the image document's copy among them: each file shown"
                        + " holds the bytes the HTML page inlines, a picture's file beside the body and a copy in the"
                        + " HTML tree",
                () -> {
                    List<String> inlined = groups(AN_HTML_PICTURE, html, 1);
                    List<String> files = groups(A_STORAGE_PICTURE, storage, 1);
                    assertThat(files).hasSameSizeAs(inlined).hasSize(2);
                    for (int at = 0; at < files.size(); at++) {
                        Path file = files.get(at).startsWith("picture-")
                                ? doorsFolder(tree).resolve(files.get(at))
                                : tree.resolve("1-fire-safety/1-fire-doors").resolve(files.get(at));
                        assertThat(Files.readAllBytes(file)).isEqualTo(Base64.getDecoder().decode(inlined.get(at)));
                    }
                });
    }

    @Test
    @Story("A group's page carries its copies as files")
    @DisplayName("Each copy the HTML tree carries for a group is a file of that group's page under the same name, its entry linking it")
    void eachCopyIsAFileOnItsClustersPage(@TempDir Path base) throws IOException {
        Path tree = draw(base, SurvivorPictures.none());
        JsonNode doors = pageKeyed(pageList(tree), ListedTree.RUN_ID + ":1.1");
        String body = Files.readString(doorsFolder(tree).resolve(ConfluencePages.BODY_NAME), StandardCharsets.UTF_8);

        claim(
                "the page lists the three copies that stand, by their names, at their place in the HTML tree, and not"
                        + " the document whose original is gone",
                () -> assertThat(filesOf(doors)).containsExactly(
                        List.of("a.docx", "../1-fire-safety/1-fire-doors/a.docx", "copy"),
                        List.of("b.png", "../1-fire-safety/1-fire-doors/b.png", "copy"),
                        List.of("d.txt", "../1-fire-safety/1-fire-doors/d.txt", "copy")));
        claim(
                "each entry with a copy links it by ri:attachment, and the one without names its path and says why",
                () -> assertThat(body)
                        .contains("<ac:link><ri:attachment ri:filename=\"a.docx\" /><ac:link-body>reports/a.docx</ac:link-body></ac:link>")
                        .contains("reports/c.docx <em>"));
    }

    @Test
    @Story("A group's pictures are files of its page")
    @DisplayName("An image document's copy is shown by its own name and not written again, and a picture whose name meets a copy's takes its entry's number")
    void anImageSurvivorsCopyIsShownAndAPictureCollidingWithACopyTakesItsEntrysNumber(@TempDir Path base)
            throws IOException {
        Path tree = draw(base, picturesOfTheFirst());
        String body = Files.readString(doorsFolder(tree).resolve(ConfluencePages.BODY_NAME), StandardCharsets.UTF_8);

        claim(
                "the image document's copy, b.png, is shown by ac:image over its own name, and no picture's file is"
                        + " written for it",
                () -> {
                    assertThat(body).contains("<ac:image ac:alt=\"\"><ri:attachment ri:filename=\"b.png\" /></ac:image>");
                    assertThat(ListedTree.filesUnder(doorsFolder(tree)))
                            .containsExactlyInAnyOrder(ConfluencePages.BODY_NAME, THE_DIAGRAMS_FILE);
                });
        claim(
                "the picture's file is named by its digest and listed on the page as a picture, with its size and digest",
                () -> assertThat(pageKeyed(pageList(tree), ListedTree.RUN_ID + ":1.1").get("files").valueStream()
                                .filter(file -> file.get("kind").asString().equals(PageFile.PICTURE))
                                .map(file -> List.of(file.get("name").asString(), file.get("size").asLong(),
                                        file.get("sha256").asString()))
                                .toList())
                        .containsExactly(List.of(THE_DIAGRAMS_FILE, (long) THE_DIAGRAM.length, sha256Of(THE_DIAGRAM))));

        Path collided = draw(base.resolve("collided"), picturesOfTheFirst(), THE_DIAGRAMS_FILE);
        claim(
                "where a copy on the same page bears the picture's name, the copy keeps it and the picture takes ~1,"
                        + " its entry's number",
                () -> assertThat(ListedTree.filesUnder(doorsFolder(collided))).contains(
                        "picture-" + sha256Of(THE_DIAGRAM).substring(0, 16) + "~1.png"));
    }

    @Test
    @Story("The page list names every page and file")
    @DisplayName("The page list lists every page parents first, and every path in it names a file whose size and digest it states")
    void thePageListListsEveryPageParentsFirstAndEveryPathStands(@TempDir Path base) throws IOException {
        Path tree = draw(base, picturesOfTheFirst());
        JsonNode list = pageList(tree);
        Path confluence = tree.resolve(ConfluencePages.DIRECTORY_NAME);

        claim(
                "the list is format 1 of this generation run, its marker the run's first twelve characters",
                () -> {
                    assertThat(list.get("format").asInt()).isEqualTo(1);
                    assertThat(list.get("generationRun").asString()).isEqualTo(ListedTree.RUN_ID);
                    assertThat(list.get("marker").asString()).isEqualTo(MARKER);
                });
        claim(
                "the pages are keyed by run and place, the root first, the seed partition's before its groups', each"
                        + " naming a parent listed before it",
                () -> {
                    List<String> keys = list.get("pages").valueStream().map(page -> page.get("key").asString()).toList();
                    assertThat(keys).containsExactly(
                            ListedTree.RUN_ID + ":root", ListedTree.RUN_ID + ":1", ListedTree.RUN_ID + ":1.1",
                            ListedTree.RUN_ID + ":1.2");
                    List<String> seen = new ArrayList<>();
                    for (JsonNode page : list.get("pages")) {
                        if (seen.isEmpty()) {
                            assertThat(page.get("parent").isNull()).isTrue();
                        } else {
                            assertThat(seen).contains(page.get("parent").asString());
                        }
                        seen.add(page.get("key").asString());
                    }
                });
        claim(
                "every body and file path resolves from confluence/ to a file whose size and SHA-256 are the ones listed",
                () -> {
                    for (JsonNode page : list.get("pages")) {
                        byte[] body = Files.readAllBytes(confluence.resolve(page.get("body").asString()));
                        assertThat(page.get("bodySha256").asString()).isEqualTo(sha256Of(body));
                        for (JsonNode file : page.get("files")) {
                            byte[] bytes = Files.readAllBytes(confluence.resolve(file.get("path").asString()).normalize());
                            assertThat(file.get("size").asLong()).isEqualTo(bytes.length);
                            assertThat(file.get("sha256").asString()).isEqualTo(sha256Of(bytes));
                        }
                    }
                });
        claim(
                "no .part file is left in confluence/",
                () -> assertThat(ListedTree.filesUnder(confluence)).noneMatch(name -> name.endsWith(".part")));
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
    @Story("The root page keeps the operator's machine and profile off the site")
    @DisplayName("The root page links every seed partition's page and every group's page by its title, and carries documents.csv as a file")
    void theRootPageLinksEveryPageAndCarriesDocumentsCsv(@TempDir Path base) throws IOException {
        Path tree = draw(base, SurvivorPictures.none());
        JsonNode list = pageList(tree);
        String root = Files.readString(
                tree.resolve(ConfluencePages.DIRECTORY_NAME).resolve(ConfluencePages.BODY_NAME), StandardCharsets.UTF_8);
        byte[] manifest = Files.readAllBytes(tree.resolve(Deliverable.MANIFEST_FILE_NAME));

        claim(
                "the root page opens with the run's id under the index's heading and the one sentence",
                () -> assertThat(root).startsWith("<h1>Deliverable " + ListedTree.RUN_ID + "</h1>\n<p>"
                        + Html.escape(ConfluencePages.THE_ROOT_PAGES_SENTENCE) + "</p>\n"));
        claim(
                "it links each page the list lists but itself, by its title",
                () -> assertThat(groups(Pattern.compile("<ri:page ri:content-title=\"([^\"]*)\" />"), root, 1))
                        .containsExactlyInAnyOrderElementsOf(titlesIn(list).subList(1, 4).stream().map(Html::escape).toList()));
        claim(
                "the unwritten group's row says so in the index's words",
                () -> assertThat(root).contains("<em>" + Deliverable.NOTHING_WAS_WRITTEN_OVER_IT + "</em>"));
        claim(
                "documents.csv is the root page's one file, a listing, with the HTML tree's bytes' size and digest",
                () -> assertThat(filesOf(list.get("pages").get(0))).containsExactly(
                        List.of(Deliverable.MANIFEST_FILE_NAME, "../" + Deliverable.MANIFEST_FILE_NAME, PageFile.LISTING)));
        claim(
                "and its size and digest are the manifest's",
                () -> {
                    JsonNode file = list.get("pages").get(0).get("files").get(0);
                    assertThat(file.get("size").asLong()).isEqualTo(manifest.length);
                    assertThat(file.get("sha256").asString()).isEqualTo(sha256Of(manifest));
                });
        claim(
                "no page lists the marker, code.csv, anything under code/ or a file an earlier drawing left",
                () -> assertThat(list.get("pages").valueStream()
                                .flatMap(page -> page.get("files").valueStream())
                                .map(file -> file.get("path").asString()))
                        .noneMatch(path -> path.contains(DeliverableRoot.MARKER_FILE_NAME)
                                || path.contains(Deliverable.CODE_LISTING_FILE_NAME) || path.contains("/code/")));
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

    @Test
    @Story("What an earlier drawing left in confluence/ is counted and kept")
    @DisplayName("confluence/ is never counted as left behind, and a folder inside it this drawing did not write is counted and kept")
    void whatAnEarlierDrawingLeftInConfluenceIsCountedAndKept(@TempDir Path base) throws IOException {
        long[] fresh = new long[1];
        draw(base.resolve("fresh"), SurvivorPictures.none(), hearing(fresh));
        Path tree = draw(base, SurvivorPictures.none());
        Path confluence = tree.resolve(ConfluencePages.DIRECTORY_NAME);
        Files.createDirectories(confluence.resolve("9-an-old-seed"));
        Files.createDirectories(confluence.resolve("1-fire-safety/9-an-old-group"));
        Files.createDirectories(confluence.resolve("1-fire-safety/1-fire-doors/deeper"));
        Files.writeString(confluence.resolve("stray.txt"), "a file");
        long[] told = new long[1];
        draw(base, SurvivorPictures.none(), hearing(told));

        claim(
                "a first drawing counts nothing left behind: confluence/ and its folders are this drawing's",
                () -> assertThat(fresh[0]).isZero());
        claim(
                "a seed partition's folder and a group's folder this drawing did not write are counted, nothing deeper and"
                        + " no file is",
                () -> assertThat(told[0]).isEqualTo(2));
        claim(
                "and every one of them is kept",
                () -> assertThat(List.of(
                                confluence.resolve("9-an-old-seed"), confluence.resolve("1-fire-safety/9-an-old-group"),
                                confluence.resolve("1-fire-safety/1-fire-doors/deeper"), confluence.resolve("stray.txt")))
                        .allMatch(Files::exists));
    }

    @Test
    @Story("A citation lands on its document")
    @DisplayName("A group split into pages is drawn as pages titled with their number, and a citation names the page its entry is on")
    void aSplitClustersCitationNamesThePageItsEntryIsOn(@TempDir Path base) throws IOException {
        Path tree = drawSplit(base);
        Path folder = doorsFolder(tree);
        String first = Files.readString(folder.resolve(ConfluencePages.BODY_NAME), StandardCharsets.UTF_8);
        String second = Files.readString(folder.resolve("page-2.xml"), StandardCharsets.UTF_8);
        String title = DOORS_TITLE + " (1.1 · " + MARKER + ")";
        String pageTwo = DOORS_TITLE + " (1.1 page 2 · " + MARKER + ")";

        claim(
                "a page an entry, the HTML tree's split: four Confluence pages, as four HTML pages",
                () -> assertThat(ListedTree.filesUnder(folder)).containsExactlyInAnyOrder(
                        "page.xml", "page-2.xml", "page-3.xml", "page-4.xml"));
        claim(
                "[2] on the first page names page 2's title, [1] and [9] the first page's",
                () -> {
                    Matcher citation = A_STORAGE_CITATION.matcher(first);
                    Map<String, String> named = new HashMap<>();
                    while (citation.find()) {
                        named.putIfAbsent(citation.group(1), citation.group(2));
                    }
                    assertThat(named).containsEntry("1", Html.escape(title))
                            .containsEntry("2", Html.escape(pageTwo))
                            .containsEntry("9", Html.escape(title));
                });
        claim(
                "the second page lists entry 2 alone, numbered on, and no writing",
                () -> {
                    assertThat(groups(AN_ANCHOR_MACRO, second, 1)).containsExactly("2");
                    assertThat(second).contains("<ol start=\"2\">").doesNotContain("ac:plain-text-link-body");
                });
        claim(
                "the page list keys page 2 by its place and number, under the group's first page",
                () -> {
                    JsonNode page = pageKeyed(pageList(tree), ListedTree.RUN_ID + ":1.1.2");
                    assertThat(page.get("parent").asString()).isEqualTo(ListedTree.RUN_ID + ":1.1");
                    assertThat(page.get("title").asString()).isEqualTo(pageTwo);
                });
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
                pictures,
                progress);
    }

    /** The doors alone, split a page an entry by a ceiling of one byte (ADR-303). */
    private static Path drawSplit(Path base) throws IOException {
        Path archive = base.resolve(THE_CORPUS_ROOT);
        Files.createDirectories(archive.resolve("reports"));
        List<ListedSurvivor> survivors = new ArrayList<>();
        for (int at = 0; at < 4; at++) {
            Files.writeString(archive.resolve("reports/" + at + ".txt"), "document " + at);
            survivors.add(survivor(10 + at, "reports/" + at + ".txt", sha256Of(bytes("document " + at)), THE_SEED,
                    SEED_PATH, 0, 0.9 - at / 10.0));
        }
        return ListedTree.writeWithCeiling(
                base.resolve(THE_WORKING_DIRECTORY), archive, List.of(DOORS),
                Map.of(slotOf(DOORS), writing(DOORS_TITLE, PROSE, 10, 11, 12, 13)), survivors, Map.of(),
                SurvivorPictures.none(), new ClusterPageCeiling.Bytes(1), DeliverableProgress.NONE);
    }

    /** The first document carries the diagram; no other carries a picture. */
    private static SurvivorPictures picturesOfTheFirst() {
        return survivor -> survivor.occurrence().value() == 10
                ? List.of(new ListedPicture("image/png", THE_DIAGRAM, false, "a diagram of <doors> & frames"))
                : List.of();
    }

    private static DeliverableProgress hearing(long[] told) {
        return new DeliverableProgress() {
            @Override
            public void leftBehind(boolean documentsFolder, long otherFolders) {
                told[0] = otherFolders;
            }
        };
    }

    private static Path doorsFolder(Path tree) {
        return tree.resolve(ConfluencePages.DIRECTORY_NAME).resolve("1-fire-safety/1-fire-doors");
    }

    private static JsonNode pageList(Path tree) throws IOException {
        return JSON.readTree(tree.resolve(ConfluencePages.DIRECTORY_NAME).resolve(ConfluencePages.PAGE_LIST_FILE_NAME).toFile());
    }

    private static JsonNode pageKeyed(JsonNode list, String key) {
        return list.get("pages").valueStream().filter(page -> page.get("key").asString().equals(key)).findFirst()
                .orElseThrow();
    }

    private static List<String> titlesIn(JsonNode list) {
        return list.get("pages").valueStream().map(page -> page.get("title").asString()).toList();
    }

    private static List<List<String>> filesOf(JsonNode page) {
        return page.get("files").valueStream()
                .map(file -> List.of(file.get("name").asString(), file.get("path").asString(), file.get("kind").asString()))
                .toList();
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
