package io.algernon.vespera.publish.confluence;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.deliverable.AnItalianTreeIsDrawnInItalianTest;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
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
@Link(name = "ADR-378", url = Adr.EACH_DELIVERABLE_LANGUAGE_IS_PUBLISHED_AS_A_TREE_OF_ITS_OWN, type = "adr")
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

    private static final JsonMapper JSON = JsonMapper.builder().build();

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

    @Test
    @Story("Each deliverable language is a tree of its own")
    @DisplayName("An Italian rendering's root page is Vespera_it, and every page is keyed by it and titled with it, a split group's later pages saying pagina")
    void anItalianTreeIsVesperaItAndEveryPageCarriesIt(@TempDir Path base) throws IOException {
        Path tree = draw(base);
        JsonNode list = JSON.readTree(
                tree.resolve(ConfluencePages.DIRECTORY_NAME).resolve(ConfluencePages.PAGE_LIST_FILE_NAME).toFile());
        List<String> pages = list.get("pages").valueStream()
                .map(page -> page.get("key").asString() + " " + page.get("title").asString())
                .toList();
        List<String> expected = new ArrayList<>(List.of("it:root Vespera_it", "it:1 Sicurezza (1 · it)",
                "it:1.1 " + DOORS_TITLE + " (1.1 · it)"));
        for (int page = 2; page <= FIVE_ENTRIES; page++) {
            expected.add("it:1.1." + page + " " + DOORS_TITLE + " (1.1 pagina " + page + " · it)");
        }
        expected.add("it:1.2 Serrande (1.2 · it)");

        claim(
                "the page list names the language the rendering was drawn in, it",
                () -> assertThat(list.path("language").asString("")).isEqualTo("it"));
        claim(
                "in the list's order, each page keyed by it and its place and titled with its place and it: the root"
                        + " page, the seed partition's, the written group's five, its later four saying pagina, and the"
                        + " unwritten group's one",
                () -> assertThat(pages).containsExactlyElementsOf(expected));
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
