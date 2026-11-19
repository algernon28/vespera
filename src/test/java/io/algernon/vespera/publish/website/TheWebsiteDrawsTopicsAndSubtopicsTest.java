package io.algernon.vespera.publish.website;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.engine.deliverable.TwoTopicsDrawn.MOTORS_FOLDER;
import static io.algernon.vespera.engine.deliverable.TwoTopicsDrawn.MOTOR_FOLDER;
import static io.algernon.vespera.engine.deliverable.TwoTopicsDrawn.PUMPING_FOLDER;
import static io.algernon.vespera.engine.deliverable.TwoTopicsDrawn.SEALS_FOLDER;
import static io.algernon.vespera.engine.deliverable.TwoTopicsDrawn.SEAL_WEAR_FOLDER;
import static io.algernon.vespera.engine.deliverable.TwoTopicsDrawn.SLUICES_FOLDER;
import static io.algernon.vespera.publish.website.WebsiteFixture.SEARCH_INDEX;
import static io.algernon.vespera.publish.website.WebsiteFixture.listedIn;
import static io.algernon.vespera.publish.website.WebsiteFixture.renderingOf;
import static io.algernon.vespera.publish.website.WebsiteFixture.textOf;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.deliverable.Plugin;
import io.algernon.vespera.engine.deliverable.TwoTopicsDrawn;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The website rendering draws {@link TwoTopicsDrawn} as the HTML tree does (ADR-415 §8): a topic's page and a
 * subtopic's at the HTML tree's places, carrying what the tree's carry, each listed in {@code files.json}; and its
 * search names each cluster's topic and subtopic.
 *
 * <p><b>The report says <em>group</em> and the code says <em>cluster</em></b> (ADR-122).
 */
@Epic("The deliverable")
@Feature("The website rendering")
@Issue("833")
@Link(name = "ADR-415", url = Adr.A_TOPIC_IS_A_PAGE_CARRYING_ITS_OVERVIEW_AND_ITS_SUBTOPICS_ARE_PAGES_BENEATH_IT, type = "adr")
@Link(name = "ADR-333", url = Adr.A_PLUGIN_DRAWS_ONE_RENDERING_AND_DELIVERS_IT_TO_ONE_DESTINATION, type = "adr")
class TheWebsiteDrawsTopicsAndSubtopicsTest {

    private static final List<Plugin> THE_WEBSITE_PLUGIN = List.of(new WebsitePlugin());

    /** A page's file in its folder. */
    private static final String PAGE = "index.html";

    /** The search index's script: one global set to a JSON array. */
    private static final Pattern THE_INDEX_SET = Pattern.compile(
            "^\\s*(?:globalThis|window|self)\\.VESPERA_SEARCH_INDEX\\s*=\\s*(\\[.*\\])\\s*;?\\s*$", Pattern.DOTALL);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    @Story("A topic and a subtopic each have a page on the website")
    @DisplayName("The website holds a page for each topic and each subtopic, and the divided topic's groups' pages, where the HTML tree holds them, each listed as a page")
    void eachTopicsAndSubtopicsPageIsDrawnAndListed(@TempDir Path base) throws IOException {
        Path rendering = renderingOf(TwoTopicsDrawn.drawn(base.resolve("work"), THE_WEBSITE_PLUGIN));
        Map<String, WebsiteFixture.Listed> listed = listedIn(rendering);
        List<String> pages = List.of(
                PUMPING_FOLDER + "/" + PAGE,
                SEALS_FOLDER + "/" + PAGE,
                MOTORS_FOLDER + "/" + PAGE,
                SLUICES_FOLDER + "/" + PAGE,
                MOTOR_FOLDER + "/" + PAGE);

        claim("each topic's page, each subtopic's and the divided topic's group numbered 3, in its subtopic's folder,"
                        + " are files of the website rendering",
                () -> assertThat(pages).allSatisfy(page -> assertThat(rendering.resolve(page)).isRegularFile()));
        claim("and each is listed in files.json as a page",
                () -> assertThat(pages).allSatisfy(page -> assertThat(listed.get(page))
                        .as(page)
                        .isNotNull()
                        .extracting(WebsiteFixture.Listed::kind)
                        .isEqualTo("page")));
    }

    @Test
    @Story("A topic and a subtopic each have a page on the website")
    @DisplayName("A topic's page on the website carries its overview, its citations' links, its subtopics' links and a See also link as the HTML tree's page does")
    void aTopicsPageCarriesItsOverviewAndItsLinksAsTheTreesDoes(@TempDir Path base) throws IOException {
        Path rendering = renderingOf(TwoTopicsDrawn.drawn(base.resolve("work"), THE_WEBSITE_PLUGIN));
        String pumping = textOf(rendering.resolve(PUMPING_FOLDER).resolve(PAGE));
        String sluices = textOf(rendering.resolve(SLUICES_FOLDER).resolve(PAGE));

        claim("the divided topic's page is headed by its name, carries its overview with each citation linking its"
                        + " group's page through its subtopic's folder, then links its subtopics' pages",
                () -> assertThat(pumping).containsSubsequence(
                        "<h1>" + TwoTopicsDrawn.PUMPING_NAME + "</h1>",
                        "<a href=\"1-seals-and-impellers/1-seal-wear/" + PAGE + "\">[1]</a>",
                        "<a href=\"1-seals-and-impellers/2-impeller-damage/" + PAGE + "\">[2]</a>",
                        "<a href=\"2-motors/3-motor-overheating/" + PAGE + "\">[3]</a>",
                        "<a href=\"1-seals-and-impellers/" + PAGE + "\">Seals and Impellers</a>",
                        "<a href=\"2-motors/" + PAGE + "\">Motors</a>"));
        claim("the undivided topic's page says its overview read one of its two groups, and links the document of the"
                        + " other topic that fits it, under See also",
                () -> assertThat(sluices).containsSubsequence(
                        "<p><em>Written from what was written on 1 of the 2 groups in this topic.</em></p>",
                        "<h2>See also</h2>",
                        "<li><a href=\"../" + SEAL_WEAR_FOLDER + "/" + PAGE + "\">" + TwoTopicsDrawn.THE_LINKED_PATH
                                + "</a> (" + TwoTopicsDrawn.PUMPING_NAME + ")</li>"));
    }

    @Test
    @Story("The website's search names each group's topic and subtopic")
    @DisplayName("Each group's row of the search index names its topic and its subtopic, the undivided topic's subtopic empty, and none says partition")
    void theSearchIndexNamesEachGroupsTopicAndSubtopic(@TempDir Path base) throws IOException {
        Path rendering = renderingOf(TwoTopicsDrawn.drawn(base.resolve("work"), THE_WEBSITE_PLUGIN));
        Matcher set = THE_INDEX_SET.matcher(textOf(rendering.resolve(SEARCH_INDEX)));
        claim("the script sets the search index to a JSON array", () -> assertThat(set.matches()).isTrue());
        List<JsonNode> clusters = JSON.readTree(set.group(1)).valueStream().toList();
        List<String> named = clusters.stream()
                .map(cluster -> cluster.path("label").asString() + ": " + cluster.path("topic").asString() + " / "
                        + cluster.path("subtopic").asString())
                .toList();

        claim("each of the five groups, in the order drawn, names its topic and its subtopic, the undivided topic's"
                        + " groups an empty subtopic",
                () -> assertThat(named).containsExactly(
                        "Seal Wear: Pumping Stations / Seals and Impellers",
                        "Impeller Damage: Pumping Stations / Seals and Impellers",
                        "Motor Overheating: Pumping Stations / Motors",
                        "Gate Hinges: Sluice Gates / ",
                        "Gate Seals: Sluice Gates / "));
        claim("and no row carries the retired field partition",
                () -> assertThat(clusters).noneMatch(cluster -> cluster.has("partition")));
        claim("each group's link leads to its page through its subtopic's folder where its topic is divided",
                () -> assertThat(clusters.get(2).path("link").asString()).isEqualTo(MOTOR_FOLDER + "/" + PAGE));
    }
}
