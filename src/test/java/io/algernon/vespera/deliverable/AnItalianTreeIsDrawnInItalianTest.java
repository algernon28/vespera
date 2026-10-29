package io.algernon.vespera.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.deliverable.ItalianWordingTest.asRead;
import static io.algernon.vespera.deliverable.ListedTree.cluster;
import static io.algernon.vespera.deliverable.ListedTree.sha256Of;
import static io.algernon.vespera.deliverable.ListedTree.slotOf;
import static io.algernon.vespera.deliverable.ListedTree.survivor;
import static io.algernon.vespera.deliverable.ListedTree.writing;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.synthesis.ClusterSlot;
import io.algernon.vespera.synthesis.DeliverableLanguage;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.algernon.vespera.synthesis.SynthesisDoc;
import io.algernon.vespera.synthesis.Unwritten;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

/**
 * A tree whose deliverable language is Italian, drawn whole, and what every page of it says (ADR-321 §4, §5): the HTML
 * cluster pages and index, and the Confluence rendering's root page, seed partition's page, cluster pages and titles.
 * The writing itself is the model's and is handed in here as it would be written; what is held is every word Vespera
 * writes around it. Every fixture is synthetic: an archive written here, a run id of the ledger's shape.
 *
 * <p><b>One drawing holds every fixed word at once</b>: two clusters of one seed, the first written over, citing three
 * documents of its four and a number its cluster no longer holds, one of its copies gone from the archive, and split a
 * page an entry by a ceiling of one byte; the second turned down for the answer's shape in a run whose writing is
 * finished, so it stands without writing.
 *
 * <p>The report says <em>group</em> where the code says cluster (ADR-122); the pages say <em>gruppo</em>.
 */
@Epic("The deliverable")
@Feature("The tree the operator is handed")
@Link(name = "ADR-321", url = Adr.THE_DELIVERABLE_IS_IN_THE_LANGUAGE_THE_PROFILE_NAMES, type = "adr")
@Link(name = "ADR-303", url = Adr.A_CLUSTERS_PAGE_PAST_THE_CEILING_IS_SPLIT_INTO_PAGES, type = "adr")
@Link(name = "ADR-310", url = Adr.A_CONFLUENCE_PAGES_TITLE_ENDS_WITH_ITS_PLACE_AND_ITS_RUNS_MARKER, type = "adr")
@Link(name = "ADR-328", url = Adr.THE_CONFLUENCE_RENDERING_IS_ONE_TREE_UPDATED_IN_PLACE_WITH_NO_RUN_ID, type = "adr")
@Link(name = "ADR-315", url = Adr.THE_ROOT_PAGE_CARRIES_NEITHER_THE_CORPUS_ROOT_NOR_THE_PROFILE, type = "adr")
class AnItalianTreeIsDrawnInItalianTest {

    private static final DeliverableLanguage ITALIAN = DeliverableLanguage.ITALIAN;

    private static final OccurrenceId THE_SEED = new OccurrenceId(1);

    private static final String SEED_PATH = "seeds/Sicurezza.docx";

    /** How many documents the written cluster holds, and how many entries its list has with the one it no longer holds. */
    private static final int FOUR_DOCUMENTS = 4;

    private static final int FIVE_ENTRIES = 5;

    /** How many documents its call sent: two it holds and one it no longer does. */
    private static final int THREE_SENT = 3;

    /** A ceiling of one byte, which splits a cluster a page an entry (ADR-303). */
    private static final long ONE_BYTE = 1;

    private static final RecordedCluster DOORS = cluster(THE_SEED, 0, "Porte", 1, FOUR_DOCUMENTS);

    private static final RecordedCluster DAMPERS = cluster(THE_SEED, 1, "Serrande", 2, 1);

    /** The title the model wrote, in Italian, as an Italian deliverable asks it to. */
    private static final String DOORS_TITLE = "Porte tagliafuoco e loro ispezione";

    /** Writing citing the three documents the call sent. */
    private static final String PROSE = "Le porte [1] e i telai [2] sono descritti insieme [3].";

    /** A document the call sent that the cluster no longer holds (ADR-133). */
    private static final long NO_LONGER_HELD = 99;

    /** The copy that cannot be made, its original never written into the archive. */
    private static final String GONE_FROM_THE_ARCHIVE = "reports/d.txt";

    /** The key the index lists the language under, as the profile spells it. */
    private static final String THE_KEY = "deliverableLanguage";

    /**
     * Words every English page of the tree carries and no Italian one may: each fixed English phrase of ADR-321 §4's
     * table, as far as it is told apart from a path or a value, and the navigation's four words as links.
     */
    private static final List<String> ENGLISH_FIXED_WORDS = List.of(
            "The documents in this group",
            "Written from the first",
            "othing was written over this group",
            "othing has been written over this group",
            "Its answer was turned down",
            "a document the writing was made from",
            "What the local model wrote",
            "Page 1 of",
            ">first<",
            ">previous<",
            ">next<",
            ">last<",
            "Deliverable ",
            "Each group's page carries",
            "<th>Group</th>",
            "Written up as",
            "Archive root",
            "Run: ",
            "Walk: ",
            "no longer in the archive",
            "more picture",
            "lang=\"en\"");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    @Story("An Italian deliverable's pages say in Italian what Vespera writes on them")
    @DisplayName("Every HTML page of an Italian tree says it is in Italian")
    void everyHtmlPageNamesItalian(@TempDir Path base) throws IOException {
        Path tree = draw(base);
        List<Path> pages = filesUnder(tree, ".html");

        claim(
                "the index, the written group's " + FIVE_ENTRIES + " pages and the unwritten group's one are drawn",
                () -> assertThat(pages).hasSize(1 + FIVE_ENTRIES + 1));
        claim(
                "and each opens as an HTML page in Italian",
                () -> assertThat(pages).allSatisfy(page -> assertThat(textOf(page))
                        .startsWith("<!DOCTYPE html>\n<html lang=\"" + ItalianWordingOnRecord.LANG + "\">")));
    }

    /** ADR-321 §4: the page sizes are the operator's report, beside the database and outside the tree. */
    @Test
    @Story("An Italian deliverable's pages say in Italian what Vespera writes on them")
    @DisplayName("The report of the groups' page sizes the same drawing writes for the operator stays in English")
    void theOperatorsReportOfPageSizesStaysEnglish(@TempDir Path base) throws IOException {
        draw(base);
        String report = textOf(base.resolve("work").resolve(Deliverable.CLUSTER_PAGE_SIZES_FILE_NAME));

        claim(
                "the report beside the database opens as an English page with its English heading: it is written for"
                        + " the operator, who sets the ceiling, and not for the readers of the tree",
                () -> assertThat(report)
                        .startsWith("<!DOCTYPE html>\n<html lang=\"en\">")
                        .contains("<h1>Group page sizes</h1>"));
    }

    @Test
    @Story("An Italian deliverable's pages say in Italian what Vespera writes on them")
    @DisplayName("A written group's first page heads its list, says what its writing rests on and links its other pages in Italian")
    void theWrittenClustersFirstPageIsInItalian(@TempDir Path base) throws IOException {
        String page = asRead(textOf(doorsPage(draw(base), 1)));

        claim(
                "the writing's block says on hover, in Italian, what it shows",
                () -> assertThat(page).contains("title=\"" + ItalianWordingOnRecord.WRITING_HOVER + "\""));
        claim(
                "the page says in Italian that the writing rests on the first " + THREE_SENT + " of the "
                        + FOUR_DOCUMENTS + " documents",
                () -> assertThat(page).contains(
                        "<p>" + ItalianWordingOnRecord.SUBSET_DISCLOSURE.formatted(THREE_SENT, FOUR_DOCUMENTS) + "</p>"));
        claim(
                "the list is headed in Italian",
                () -> assertThat(page).contains("<h2>" + ItalianWordingOnRecord.MEMBERSHIP_HEADING + "</h2>"));
        claim(
                "and the first of " + FIVE_ENTRIES + " pages links the next and the last in Italian",
                () -> assertThat(page).contains("<p class=\"pages\">"
                        + ItalianWordingOnRecord.PAGE_OF.formatted(1, FIVE_ENTRIES)
                        + link("index-2.html", ItalianWordingOnRecord.NEXT) + ", "
                        + link("index-5.html", ItalianWordingOnRecord.LAST) + "</p>"));
    }

    @Test
    @Story("An Italian deliverable's pages say in Italian what Vespera writes on them")
    @DisplayName("A later page links every other page in Italian and says in Italian why an entry links to nothing")
    void aLaterPageIsInItalian(@TempDir Path base) throws IOException {
        Path tree = draw(base);
        String third = asRead(textOf(doorsPage(tree, 3)));
        String fifth = asRead(textOf(doorsPage(tree, FIVE_ENTRIES)));

        claim(
                "the third page links the first, the previous, the next and the last in Italian",
                () -> assertThat(third).contains("<p class=\"pages\">"
                        + ItalianWordingOnRecord.PAGE_OF.formatted(3, FIVE_ENTRIES)
                        + link("index.html", ItalianWordingOnRecord.FIRST) + ", "
                        + link("index-2.html", ItalianWordingOnRecord.PREVIOUS) + ", "
                        + link("index-4.html", ItalianWordingOnRecord.NEXT) + ", "
                        + link("index-5.html", ItalianWordingOnRecord.LAST) + "</p>"));
        claim(
                "its entry, the number the writing cited and the group no longer holds, says so in Italian",
                () -> assertThat(third).contains("<em>" + ItalianWordingOnRecord.THE_CLUSTER_NO_LONGER_HOLDS_IT + "</em>"));
        claim(
                "and the fifth page's entry, whose original is gone from the archive, says why in Italian",
                () -> assertThat(fifth).contains(
                        GONE_FROM_THE_ARCHIVE + " <em>" + ItalianWordingOnRecord.GONE + "</em>"));
    }

    @Test
    @Story("An Italian deliverable says in Italian why nothing was written over a group")
    @DisplayName("An unwritten group's page says in Italian why, and that it stands without writing")
    void theUnwrittenClustersPageIsInItalian(@TempDir Path base) throws IOException {
        String page = asRead(textOf(dampersPage(draw(base))));

        claim(
                "beneath the heading, the reason the answer was turned down and, beneath it, that the group stands,"
                        + " each in Italian and in italics",
                () -> assertThat(page).contains(
                        "<p><em>" + ItalianWordingOnRecord.THE_ANSWER_CAME_BACK_IN_ANOTHER_SHAPE + "</em></p>\n"
                                + "<p><em>" + ItalianWordingOnRecord.STANDS_WITHOUT_WRITING + "</em></p>"));
    }

    @Test
    @Story("An Italian deliverable's pages say in Italian what Vespera writes on them")
    @DisplayName("The index is titled, headed and explained in Italian, its profile keys named as the profile spells them")
    void theIndexIsInItalian(@TempDir Path base) throws IOException {
        Path tree = draw(base);
        String index = asRead(textOf(tree.resolve(Deliverable.INDEX_FILE_NAME)));
        Path archive = base.resolve("archive");

        claim(
                "its title and heading name the deliverable in Italian, with the run's id",
                () -> assertThat(index)
                        .contains("<title>" + ItalianWordingOnRecord.THE_DELIVERABLE + ListedTree.RUN_ID + "</title>")
                        .contains("<h1>" + ItalianWordingOnRecord.THE_DELIVERABLE + ListedTree.RUN_ID + "</h1>"));
        claim(
                "its three lines on what produced the tree are in Italian, and the profile's key is the profile's",
                () -> assertThat(index)
                        .contains("<li>" + ItalianWordingOnRecord.RUN + ListedTree.RUN_ID + "</li>")
                        .contains("<li>" + ItalianWordingOnRecord.WALK + ListedTree.WALK + "</li>")
                        .contains("<li>" + ItalianWordingOnRecord.ARCHIVE_ROOT + archive + "</li>")
                        .contains("<li>" + THE_KEY + ": " + ItalianWordingOnRecord.LANG + "</li>"));
        claim(
                "its paragraph on documents.csv is the record's Italian, the column names staying the file's",
                () -> assertThat(index).contains(ItalianWordingOnRecord.THE_INDEXS_PARAGRAPH));
        claim(
                "the table of groups is headed in Italian, and the unwritten group's cell says nothing was written in"
                        + " Italian",
                () -> assertThat(index)
                        .contains(headRow(ItalianWordingOnRecord.THE_TABLE_OF_CLUSTERS))
                        .contains("<em>" + ItalianWordingOnRecord.NOTHING_WAS_WRITTEN_OVER_IT + "</em>"));
    }

    @Test
    @Story("An Italian deliverable's pages say in Italian what Vespera writes on them")
    @DisplayName("The Confluence root page and the seed partition's page are headed and explained in Italian")
    void theConfluenceRootAndPartitionPagesAreInItalian(@TempDir Path base) throws IOException {
        Path confluence = draw(base).resolve(ConfluencePages.DIRECTORY_NAME);
        String root = asRead(textOf(confluence.resolve(ConfluencePages.BODY_NAME)));
        String partition = asRead(textOf(confluence.resolve("1-sicurezza").resolve(ConfluencePages.BODY_NAME)));

        claim(
                "the root page opens with the deliverable named in Italian, without the run's id, and the record's"
                        + " Italian sentence",
                () -> assertThat(root).startsWith("<h1>" + ItalianWordingOnRecord.THE_DELIVERABLE.strip()
                        + "</h1>\n<p>" + ItalianWordingOnRecord.THE_ROOT_PAGES_SENTENCE + "</p>"));
        claim(
                "and both pages head their table of groups in Italian, the unwritten group's cell in Italian",
                () -> assertThat(List.of(root, partition)).allSatisfy(page -> assertThat(page)
                        .contains(headRow(ItalianWordingOnRecord.THE_TABLE_OF_CLUSTERS))
                        .contains("<em>" + ItalianWordingOnRecord.NOTHING_WAS_WRITTEN_OVER_IT + "</em>")));
    }

    @Test
    @Story("An Italian deliverable's pages say in Italian what Vespera writes on them")
    @DisplayName("Each group's Confluence pages say in Italian what its HTML pages say")
    void theClustersConfluencePagesAreInItalian(@TempDir Path base) throws IOException {
        Path folder = draw(base).resolve(ConfluencePages.DIRECTORY_NAME).resolve("1-sicurezza");
        String first = asRead(textOf(folder.resolve("1-porte").resolve(ConfluencePages.BODY_NAME)));
        String third = asRead(textOf(folder.resolve("1-porte").resolve("page-3.xml")));
        String fifth = asRead(textOf(folder.resolve("1-porte").resolve("page-5.xml")));
        String dampers = asRead(textOf(folder.resolve("2-serrande").resolve(ConfluencePages.BODY_NAME)));

        claim(
                "the written group's first page opens on the navigation in Italian, says what the writing rests on"
                        + " and heads its list in Italian",
                () -> assertThat(first)
                        .startsWith("<p>" + ItalianWordingOnRecord.PAGE_OF.formatted(1, FIVE_ENTRIES))
                        .contains("<ac:link-body>" + ItalianWordingOnRecord.NEXT + "</ac:link-body>")
                        .contains("<ac:link-body>" + ItalianWordingOnRecord.LAST + "</ac:link-body>")
                        .contains("<p>" + ItalianWordingOnRecord.SUBSET_DISCLOSURE.formatted(THREE_SENT, FOUR_DOCUMENTS)
                                + "</p>")
                        .contains("<h2>" + ItalianWordingOnRecord.MEMBERSHIP_HEADING + "</h2>"));
        claim(
                "its third page says in Italian that the group no longer holds the document at that number, and its"
                        + " fifth why the copy is not there",
                () -> {
                    assertThat(third).contains("<em>" + ItalianWordingOnRecord.THE_CLUSTER_NO_LONGER_HOLDS_IT + "</em>");
                    assertThat(fifth).contains(GONE_FROM_THE_ARCHIVE + " <em>" + ItalianWordingOnRecord.GONE + "</em>");
                });
        claim(
                "and the unwritten group's page says why and that it stands, in Italian",
                () -> assertThat(dampers).contains(
                        "<p><em>" + ItalianWordingOnRecord.THE_ANSWER_CAME_BACK_IN_ANOTHER_SHAPE + "</em></p>\n"
                                + "<p><em>" + ItalianWordingOnRecord.STANDS_WITHOUT_WRITING + "</em></p>"));
    }

    @Test
    @Story("An Italian deliverable's pages are titled in Italian where Vespera titles them")
    @DisplayName("The page list titles a split group's later pages with pagina, and keeps every other title as it was")
    void thePageListTitlesTheLaterPagesWithPagina(@TempDir Path base) throws IOException {
        Path list = draw(base).resolve(ConfluencePages.DIRECTORY_NAME).resolve(ConfluencePages.PAGE_LIST_FILE_NAME);
        List<String> titles = JSON.readTree(list.toFile()).get("pages").valueStream()
                .map(page -> page.get("title").asString())
                .toList();
        List<String> doorsPages = IntStream.rangeClosed(1, FIVE_ENTRIES)
                .mapToObj(page -> DOORS_TITLE + " (1.1"
                        + (page == 1 ? "" : ItalianWordingOnRecord.PAGE_IN_A_TITLE + page) + ")")
                .toList();

        claim(
                "the written group's five pages are titled by the model's Italian title, the later four saying pagina"
                        + " and their number",
                () -> assertThat(titles).containsAll(doorsPages));
        claim(
                "and the root page, the seed partition's page and the unwritten group's page keep the titles that are"
                        + " names: Vespera, the seed's file name and the group's label",
                () -> assertThat(titles).contains(
                        "Vespera", "Sicurezza (1)", "Serrande (1.2)"));
    }

    @Test
    @Story("An Italian deliverable's pages say in Italian what Vespera writes on them")
    @DisplayName("No page of an Italian tree carries a fixed English word")
    void noPageCarriesAFixedEnglishWord(@TempDir Path base) throws IOException {
        Path tree = draw(base);
        Map<String, String> pages = Stream.concat(filesUnder(tree, ".html").stream(), filesUnder(tree, ".xml").stream())
                .collect(Collectors.toMap(page -> tree.relativize(page).toString(), page -> asRead(textOf(page))));

        claim(
                "the " + pages.size() + " HTML and Confluence pages carry none of the English the same tree would carry"
                        + " in English",
                () -> assertThat(pages).allSatisfy((name, page) -> assertThat(page)
                        .as(name)
                        .doesNotContain(ENGLISH_FIXED_WORDS)));
    }

    /** The fixture drawn into {@code base}: its archive, its working directory and its tree, in Italian. */
    private static Path draw(Path base) throws IOException {
        Path archive = base.resolve("archive");
        Files.createDirectories(archive.resolve("reports"));
        Files.createDirectories(archive.resolve("notes"));
        Files.writeString(archive.resolve("reports/a.txt"), "il primo documento");
        Files.writeString(archive.resolve("reports/b.txt"), "il secondo documento");
        Files.writeString(archive.resolve("reports/c.txt"), "il terzo documento");
        Files.writeString(archive.resolve("notes/serranda.txt"), "una serranda");
        List<ListedSurvivor> survivors = List.of(
                survivor(10, "reports/a.txt", sha256Of(bytes("il primo documento")), THE_SEED, SEED_PATH, 0, 0.9),
                survivor(11, "reports/b.txt", sha256Of(bytes("il secondo documento")), THE_SEED, SEED_PATH, 0, 0.8),
                survivor(12, "reports/c.txt", sha256Of(bytes("il terzo documento")), THE_SEED, SEED_PATH, 0, 0.7),
                survivor(13, GONE_FROM_THE_ARCHIVE, sha256Of(bytes("never there")), THE_SEED, SEED_PATH, 0, 0.6),
                survivor(20, "notes/serranda.txt", sha256Of(bytes("una serranda")), THE_SEED, SEED_PATH, 1, 0.5));
        Map<ClusterSlot, SynthesisDoc> written =
                Map.of(slotOf(DOORS), writing(DOORS_TITLE, PROSE, 10, 11, NO_LONGER_HELD));
        Map<ClusterSlot, Unwritten> unwritten = Map.of(slotOf(DAMPERS), Unwritten.FAULT_SCHEMA_VIOLATION);
        return Deliverable.writeTo(
                base.resolve("work"),
                DeliverableRoot.UNNAMED,
                new DeliverableProvenance(
                        ListedTree.RUN_ID, ListedTree.WALK, archive.toString(),
                        List.of(new NamedValue(THE_KEY, ItalianWordingOnRecord.LANG))),
                ListedTree.of(List.of(DOORS, DAMPERS), written, survivors, unwritten, true, ITALIAN),
                ListedCodeSets.NONE,
                SurvivorPictures.none(),
                new ClusterPageCeiling.Bytes(ONE_BYTE),
                DeliverableProgress.NONE);
    }

    /** Page {@code page} of the written cluster's HTML pages, counted from 1 (ADR-303 §4). */
    private static Path doorsPage(Path tree, int page) {
        return tree.resolve("1-sicurezza").resolve("1-porte").resolve(ClusterPage.nameOfPage(page));
    }

    private static Path dampersPage(Path tree) {
        return tree.resolve("1-sicurezza").resolve("2-serrande").resolve(Deliverable.INDEX_FILE_NAME);
    }

    /** A link of a cluster page's navigation to the page named {@code name}, reading {@code word}. */
    private static String link(String name, String word) {
        return "<a href=\"./" + name + "\">" + word + "</a>";
    }

    /** The head row of a table headed by {@code heads}, as the tree writes it. */
    private static String headRow(List<String> heads) {
        return "<tr>" + heads.stream().map(head -> "<th>" + head + "</th>").collect(Collectors.joining()) + "</tr>";
    }

    /** Every file under {@code tree} whose name ends with {@code ending}, sorted. */
    private static List<Path> filesUnder(Path tree, String ending) throws IOException {
        try (Stream<Path> walk = Files.walk(tree)) {
            return walk.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(ending))
                    .sorted()
                    .toList();
        }
    }

    private static String textOf(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
