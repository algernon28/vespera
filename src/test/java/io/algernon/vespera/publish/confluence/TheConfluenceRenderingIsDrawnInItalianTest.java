package io.algernon.vespera.publish.confluence;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.deliverable.AnItalianTreeIsDrawnInItalianTest;
import io.algernon.vespera.engine.deliverable.ItalianWordingOnRecord;
import io.algernon.vespera.engine.deliverable.Plugin;
import io.algernon.vespera.engine.synthesis.DeliverableLanguage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.UncheckedIOException;
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
 * An Italian tree's Confluence rendering, and what every page and title of it says (ADR-321 §4, §5): its root page,
 * its seed partition's page, its cluster pages, its page list's titles and a split cluster's later titles. These
 * claims stood in {@code deliverable}'s {@code AnItalianTreeIsDrawnInItalianTest} and {@code ItalianWordingTest} until
 * the rendering moved into {@code confluence} with its plugin (ADR-333 §1, §2), and stand here as they were, over the
 * same fixture drawn with the Confluence rendering's plugin.
 *
 * <p>Untagged: CI's {@code -DexcludedGroups=confluence} is for the publish's integration tests alone (ADR-320 §6).
 *
 * <p>The report says <em>group</em> where the code says cluster (ADR-122); the pages say <em>gruppo</em>.
 */
@Epic("The deliverable")
@Feature("The Confluence rendering")
@Link(name = "ADR-321", url = Adr.THE_DELIVERABLE_IS_IN_THE_LANGUAGE_THE_PROFILE_NAMES, type = "adr")
@Link(name = "ADR-310", url = Adr.A_CONFLUENCE_PAGES_TITLE_ENDS_WITH_ITS_PLACE_AND_ITS_RUNS_MARKER, type = "adr")
@Link(name = "ADR-328", url = Adr.THE_CONFLUENCE_RENDERING_IS_ONE_TREE_UPDATED_IN_PLACE_WITH_NO_RUN_ID, type = "adr")
@Link(name = "ADR-315", url = Adr.THE_ROOT_PAGE_CARRIES_NEITHER_THE_CORPUS_ROOT_NOR_THE_PROFILE, type = "adr")
@Link(name = "ADR-333", url = Adr.A_PLUGIN_DRAWS_ONE_RENDERING_AND_DELIVERS_IT_TO_ONE_DESTINATION, type = "adr")
class TheConfluenceRenderingIsDrawnInItalianTest {

    private static final DeliverableLanguage ITALIAN = DeliverableLanguage.ITALIAN;

    /** The one plugin the tree is drawn with here: the Confluence rendering's, its publisher never asked to send. */
    private static final List<Plugin> THE_CONFLUENCE_PLUGIN =
            List.of(new ConfluencePlugin(new Publisher("", "", "", "")));

    private static final int FIVE_ENTRIES = AnItalianTreeIsDrawnInItalianTest.FIVE_ENTRIES;

    private static final int FOUR_DOCUMENTS = AnItalianTreeIsDrawnInItalianTest.FOUR_DOCUMENTS;

    private static final int THREE_SENT = AnItalianTreeIsDrawnInItalianTest.THREE_SENT;

    private static final String GONE_FROM_THE_ARCHIVE = AnItalianTreeIsDrawnInItalianTest.GONE_FROM_THE_ARCHIVE;

    private static final String DOORS_TITLE = AnItalianTreeIsDrawnInItalianTest.DOORS_TITLE;

    /** A seed partition's place and a cluster's, as a Confluence title writes them. */
    private static final String PLACE = "1.1";

    /** A cluster's title, as the model wrote it. */
    private static final String A_TITLE = "Porte tagliafuoco";

    /** The second page of a split cluster. */
    private static final int THE_SECOND_PAGE = 2;

    private static final JsonMapper JSON = JsonMapper.builder().build();

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
                        .contains(AnItalianTreeIsDrawnInItalianTest.headRow(ItalianWordingOnRecord.THE_TABLE_OF_CLUSTERS))
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
    @Story("An Italian deliverable's pages are titled in Italian where Vespera titles them")
    @DisplayName("A split group's later Confluence pages say pagina in their titles, and its first page no page at all")
    void aSplitClustersConfluenceTitleSaysPagina() {
        claim(
                "the second page's title is the text, then the place, pagina and its number",
                () -> assertThat(ConfluenceTitles.cluster(A_TITLE, PLACE, THE_SECOND_PAGE, ITALIAN))
                        .isEqualTo(A_TITLE + " (" + PLACE + ItalianWordingOnRecord.PAGE_IN_A_TITLE + THE_SECOND_PAGE
                                + ")"));
        claim(
                "the first page's carries no page, as in English",
                () -> assertThat(ConfluenceTitles.cluster(A_TITLE, PLACE, 1, ITALIAN))
                        .isEqualTo(A_TITLE + " (" + PLACE + ")"));
        claim(
                "and an English rendering's second page still says page",
                () -> assertThat(ConfluenceTitles.cluster(A_TITLE, PLACE, THE_SECOND_PAGE,
                                DeliverableLanguage.ENGLISH))
                        .isEqualTo(A_TITLE + " (" + PLACE + " page " + THE_SECOND_PAGE + ")"));
    }

    @Test
    @Story("An Italian deliverable's pages say in Italian what Vespera writes on them")
    @DisplayName("No Confluence page of an Italian tree carries a fixed English word")
    void noConfluencePageCarriesAFixedEnglishWord(@TempDir Path base) throws IOException {
        Path tree = draw(base);
        Map<String, String> pages = xmlUnder(tree).stream()
                .collect(Collectors.toMap(page -> tree.relativize(page).toString(), page -> asRead(textOf(page))));

        claim(
                "the Confluence rendering's pages are drawn: the root page's, the seed partition's, the written group's"
                        + " five and the unwritten group's one",
                () -> assertThat(pages).hasSize(1 + 1 + FIVE_ENTRIES + 1));
        claim(
                "and they carry none of the English the same tree would carry in English",
                () -> assertThat(pages).allSatisfy((name, page) -> assertThat(page)
                        .as(name)
                        .doesNotContain(AnItalianTreeIsDrawnInItalianTest.ENGLISH_FIXED_WORDS)));
    }

    /** {@code deliverable}'s Italian fixture, drawn with the Confluence rendering's plugin. */
    private static Path draw(Path base) throws IOException {
        return AnItalianTreeIsDrawnInItalianTest.draw(base, THE_CONFLUENCE_PLUGIN);
    }

    /** Every body of the Confluence rendering under {@code tree}, sorted. */
    private static List<Path> xmlUnder(Path tree) throws IOException {
        try (Stream<Path> walk = Files.walk(tree.resolve(ConfluencePages.DIRECTORY_NAME))) {
            return walk.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".xml"))
                    .sorted()
                    .toList();
        }
    }

    /** {@code html} as a reader reads it: the five entities read back, so an escaped apostrophe reads as one. */
    private static String asRead(String html) {
        return html.replace("&#39;", "'")
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
    }

    private static String textOf(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
