package io.algernon.vespera.publish.confluence;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.deliverable.DeliverableProgress;
import io.algernon.vespera.engine.deliverable.FixedWords;
import io.algernon.vespera.engine.deliverable.Html;
import io.algernon.vespera.engine.deliverable.Plugin;
import io.algernon.vespera.engine.deliverable.TwoTopicsDrawn;
import io.algernon.vespera.engine.synthesis.DeliverableLanguage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Confluence rendering draws {@link TwoTopicsDrawn} as the HTML tree does (ADR-415 §7): a page for each topic
 * carrying its overview above its table, a page for each subtopic of a crowded topic, and that topic's clusters' pages
 * beneath their subtopic's, each page keyed and titled by its place (ADR-328, ADR-378).
 *
 * <p><b>The report says <em>group</em> and the code says <em>cluster</em></b> (ADR-122).
 */
@Epic("The deliverable")
@Feature("The Confluence rendering")
@Issue("833")
@Link(name = "ADR-415", url = Adr.A_TOPIC_IS_A_PAGE_CARRYING_ITS_OVERVIEW_AND_ITS_SUBTOPICS_ARE_PAGES_BENEATH_IT, type = "adr")
@Link(name = "ADR-328", url = Adr.THE_CONFLUENCE_RENDERING_IS_ONE_TREE_UPDATED_IN_PLACE_WITH_NO_RUN_ID, type = "adr")
@Link(name = "ADR-309", url = Adr.A_CITATION_ON_A_CONFLUENCE_PAGE_IS_AN_ANCHOR_LINK, type = "adr")
class TheConfluenceRenderingDrawsTopicsAndSubtopicsTest {

    private static final List<Plugin> THE_CONFLUENCE_PLUGIN =
            List.of(new ConfluencePlugin(new Publisher("", "", "", "")));

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Each page's title, by its place: its text, its place and the language's code (ADR-378 §1). */
    private static final String PUMPING = "Pumping Stations (1 · en)";

    private static final String SEALS = "Seals and Impellers (1.1 · en)";

    private static final String SEAL_WEAR = "Seals wear out (1.1.1 · en)";

    private static final String IMPELLER = "Impellers crack (1.1.2 · en)";

    private static final String MOTORS = "Motors (1.2 · en)";

    private static final String MOTOR = "Motors run hot (1.2.3 · en)";

    private static final String SLUICES = "Sluice Gates (2 · en)";

    private static final String HINGES = "Hinges rust (2.1 · en)";

    private static final String GATE_SEALS = "Gate Seals (2.2 · en)";

    @Test
    @Story("A crowded topic's subtopics are pages beneath it")
    @DisplayName("Each page is keyed by its place, titled by its text and place, and listed under its parent: a topic's under the root page, a subtopic's under its topic's, and its groups' under the subtopic's, numbered over the topic")
    void eachPageIsKeyedTitledAndParentedByItsPlace(@TempDir Path base) throws IOException {
        Path tree = TwoTopicsDrawn.drawn(base.resolve("work"), THE_CONFLUENCE_PLUGIN);
        List<JsonNode> pages = pagesOf(tree);
        List<String> listed = new ArrayList<>();
        pages.forEach(page -> listed.add(page.get("key").asString() + " under "
                + (page.get("parent").isNull() ? "nothing" : page.get("parent").asString()) + ": "
                + page.get("title").asString()));
        List<String> parentsBeforeTheirChildren = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode page : pages) {
            if (!page.get("parent").isNull() && !seen.contains(page.get("parent").asString())) {
                parentsBeforeTheirChildren.add(page.get("key").asString());
            }
            seen.add(page.get("key").asString());
        }

        claim("the page list holds the root page, each topic's, the crowded topic's two subtopics' and every group's,"
                        + " each keyed by its place, unpadded, with the language's code; each group of the crowded topic"
                        + " under its subtopic's page and numbered 1 to 3 over the topic",
                () -> assertThat(listed).containsExactlyInAnyOrder(
                        "en:root under nothing: Vespera_en",
                        "en:1 under en:root: " + PUMPING,
                        "en:1.1 under en:1: " + SEALS,
                        "en:1.1.1 under en:1.1: " + SEAL_WEAR,
                        "en:1.1.2 under en:1.1: " + IMPELLER,
                        "en:1.2 under en:1: " + MOTORS,
                        "en:1.2.3 under en:1.2: " + MOTOR,
                        "en:2 under en:root: " + SLUICES,
                        "en:2.1 under en:2: " + HINGES,
                        "en:2.2 under en:2: " + GATE_SEALS));
        claim("and every page comes after its parent, so a publish makes the parent first",
                () -> assertThat(parentsBeforeTheirChildren).isEmpty());
    }

    @Test
    @Story("A topic's page carries its overview above its table")
    @DisplayName("A divided topic's page carries its overview, each numbered citation a link to the cited group's page by its title, and then its table of subtopics, each linking its page")
    void aTopicsPageCarriesItsOverviewAboveItsTable(@TempDir Path base) throws IOException {
        Path tree = TwoTopicsDrawn.drawn(base.resolve("work"), THE_CONFLUENCE_PLUGIN);
        String body = bodyOf(tree, "en:1");

        claim("the overview's words and its three citations, each a link to the page of the group at that place by its"
                        + " title, reading the citation's number, in the overview's order",
                () -> assertThat(body).containsSubsequence(
                        "Seals and impellers wear together ",
                        citation(SEAL_WEAR, 1),
                        citation(IMPELLER, 2),
                        "Motors run hot ",
                        citation(MOTOR, 3)));
        claim("then the table of its two subtopics, in their order, each linking its page with its counts of groups and"
                        + " documents",
                () -> assertThat(body).containsSubsequence(
                        citation(MOTOR, 3),
                        "<tr><th>Subtopic</th><th>Groups</th><th>Documents</th></tr>",
                        "<tr><td>" + ConfluencePages.link(SEALS, "Seals and Impellers") + "</td><td>2</td><td>2</td></tr>",
                        "<tr><td>" + ConfluencePages.link(MOTORS, "Motors") + "</td><td>1</td><td>1</td></tr>"));
        claim("and no line saying how many groups the overview read, it having read all three",
                () -> assertThat(body).doesNotContain("Written from what was written on"));
    }

    @Test
    @Story("A topic's page carries its overview above its table")
    @DisplayName("An undivided topic's page discloses that its overview read one of its two groups, numbers each group in its table, and links the document of another topic that fits it")
    void anOverviewOverFewerGroupsAndASeeAlsoLinkAreDrawnOnTheTopicsPage(@TempDir Path base) throws IOException {
        Path tree = TwoTopicsDrawn.drawn(base.resolve("work"), THE_CONFLUENCE_PLUGIN);
        String body = bodyOf(tree, "en:2");
        String nothing = Html.escape(FixedWords.nothingWasWrittenOverIt(DeliverableLanguage.ENGLISH));

        claim("the overview cites its one written group, the line saying it read one of the two stands beneath it in"
                        + " italics, and the table follows",
                () -> assertThat(body).containsSubsequence(
                        "Hinges rust in winter ",
                        citation(HINGES, 1),
                        "<p><em>Written from what was written on 1 of the 2 groups in this topic.</em></p>",
                        "<tr><th>No.</th><th>Group</th><th>Documents</th><th>Written up as</th></tr>"));
        claim("the table numbers its two groups 1 and 2, each label linking its page, the unwritten one saying nothing"
                        + " was written over it",
                () -> assertThat(body).containsSubsequence(
                        "<tr><td>1</td><td>" + ConfluencePages.link(HINGES, "Gate Hinges") + "</td><td>1</td><td>"
                                + TwoTopicsDrawn.HINGES_TITLE + "</td></tr>",
                        "<tr><td>2</td><td>" + ConfluencePages.link(GATE_SEALS, "Gate Seals") + "</td><td>1</td><td><em>"
                                + nothing + "</em></td></tr>"));
        claim("after the table, See also, its sentence, and the one document of the other topic that fits this one, its"
                        + " path a link to its group's page by title, followed by the name of the topic it is placed under",
                () -> assertThat(body).containsSubsequence(
                        "</table>",
                        "<h2>See also</h2>",
                        "<p>Placed under another topic, these documents also speak of this one.</p>",
                        "<li>" + ConfluencePages.link(SEAL_WEAR, TwoTopicsDrawn.THE_LINKED_PATH) + " ("
                                + TwoTopicsDrawn.PUMPING_NAME + ")</li>"));
    }

    @Test
    @Story("A crowded topic's subtopics are pages beneath it")
    @DisplayName("A subtopic's page names the topic it is part of, linking its page, and lists its one group under the number it has among the topic's groups")
    void aSubtopicsPageLinksItsTopicAndListsItsGroups(@TempDir Path base) throws IOException {
        Path tree = TwoTopicsDrawn.drawn(base.resolve("work"), THE_CONFLUENCE_PLUGIN);
        String body = bodyOf(tree, "en:1.2");

        claim("the subtopic's page says which topic it is part of, the topic's name a link to the topic's page by title",
                () -> assertThat(body).contains(
                        "<p>In the topic " + ConfluencePages.link(PUMPING, TwoTopicsDrawn.PUMPING_NAME) + ".</p>"));
        claim("and lists its one group as 3, its place among the topic's three, linking its page",
                () -> assertThat(body).contains("<tr><td>3</td><td>" + ConfluencePages.link(MOTOR, "Motor Overheating")
                        + "</td><td>1</td><td>" + TwoTopicsDrawn.MOTOR_TITLE + "</td></tr>"));
    }

    @Test
    @Story("A crowded topic's subtopics are pages beneath it")
    @DisplayName("A folder an earlier drawing left inside a subtopic's folder of the Confluence rendering is counted among the folders left behind")
    void aFolderLeftInsideASubtopicsFolderOfTheRenderingIsCountedAsLeftBehind(@TempDir Path base) throws IOException {
        Path work = base.resolve("work");
        Path tree = TwoTopicsDrawn.drawn(work, THE_CONFLUENCE_PLUGIN);
        Files.createDirectories(tree.resolve(ConfluencePages.DIRECTORY_NAME).resolve(TwoTopicsDrawn.SEALS_FOLDER)
                .resolve("9-a-group-an-earlier-drawing-left"));
        long[] leftBehind = {-1};

        TwoTopicsDrawn.drawn(work, THE_CONFLUENCE_PLUGIN, new DeliverableProgress() {
            @Override
            public void leftBehind(boolean documentsFolder, long otherFolders) {
                leftBehind[0] = otherFolders;
            }
        });

        claim("the drawing of the same tree again counts the one folder it did not write, in the rendering's folder of"
                        + " the divided topic's first subtopic, as it counts one in the tree's",
                () -> assertThat(leftBehind[0]).isEqualTo(1));
    }

    /** A citation of an overview: a link to the page titled {@code title}, reading {@code [n]}, with no anchor. */
    private static String citation(String title, int number) {
        return "<ac:link><ri:page ri:content-title=\"" + Html.escape(title) + "\" /><ac:plain-text-link-body><![CDATA[["
                + number + "]]]></ac:plain-text-link-body></ac:link>";
    }

    /** The page list's pages, in its order. */
    private static List<JsonNode> pagesOf(Path tree) throws IOException {
        JsonNode list = JSON.readTree(
                tree.resolve(ConfluencePages.DIRECTORY_NAME).resolve(ConfluencePages.PAGE_LIST_FILE_NAME).toFile());
        List<JsonNode> pages = new ArrayList<>();
        list.get("pages").forEach(pages::add);
        return pages;
    }

    /** The body of the page keyed {@code key}, read from the file the page list names. */
    private static String bodyOf(Path tree, String key) throws IOException {
        Map<String, String> bodies = new LinkedHashMap<>();
        for (JsonNode page : pagesOf(tree)) {
            bodies.put(page.get("key").asString(), page.get("body").asString());
        }
        if (!bodies.containsKey(key)) {
            throw new IllegalStateException("the page list holds no page keyed " + key + ": " + bodies.keySet());
        }
        return Files.readString(
                tree.resolve(ConfluencePages.DIRECTORY_NAME).resolve(bodies.get(key)), StandardCharsets.UTF_8);
    }
}
