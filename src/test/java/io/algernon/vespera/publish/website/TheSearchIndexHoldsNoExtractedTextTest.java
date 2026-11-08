package io.algernon.vespera.publish.website;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.publish.website.WebsiteFixture.A_MARKED_SENTENCE;
import static io.algernon.vespera.publish.website.WebsiteFixture.DAMPERS_FOLDER;
import static io.algernon.vespera.publish.website.WebsiteFixture.DAMPERS_LABEL;
import static io.algernon.vespera.publish.website.WebsiteFixture.DAMPER_SCAN;
import static io.algernon.vespera.publish.website.WebsiteFixture.DOORS_FOLDER;
import static io.algernon.vespera.publish.website.WebsiteFixture.DOORS_LABEL;
import static io.algernon.vespera.publish.website.WebsiteFixture.DOORS_TITLE;
import static io.algernon.vespera.publish.website.WebsiteFixture.DOOR_SURVEY;
import static io.algernon.vespera.publish.website.WebsiteFixture.FIRST_SENTENCE;
import static io.algernon.vespera.publish.website.WebsiteFixture.GONE;
import static io.algernon.vespera.publish.website.WebsiteFixture.INSPECTION_LOG;
import static io.algernon.vespera.publish.website.WebsiteFixture.PARTITION_NAME;
import static io.algernon.vespera.publish.website.WebsiteFixture.SEARCH_INDEX;
import static io.algernon.vespera.publish.website.WebsiteFixture.SECOND_SENTENCE;
import static io.algernon.vespera.publish.website.WebsiteFixture.filesUnder;
import static io.algernon.vespera.publish.website.WebsiteFixture.renderingOf;
import static io.algernon.vespera.publish.website.WebsiteFixture.textOf;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The search is the website's own, in the browser, over titles, labels, the synthesis writing and entries' names, its
 * index drawn at stage 6b (ADR-333 §9): {@code search-index.js} holds, for each cluster, its seed partition's name, its
 * title, its label, the text of its synthesis doc, each entry's name as its page shows it and each entry's link. It
 * reads no document's extracted text: a sentence marked in a document's bytes is in no file of the rendering. A
 * cluster with no synthesis doc is found by its label and its entries.
 *
 * <p><b>The index's shape</b>, which {@code src/test/website/website.test.mjs} reads too: the script sets one global,
 * {@code VESPERA_SEARCH_INDEX}, to a JSON array, a cluster an element, each an object of {@code partition}, {@code
 * title}, {@code label}, {@code writing}, {@code link} and {@code entries}, each entry an object of {@code name} and
 * {@code link}, every link relative to the website's root. A cluster with no writing has no {@code title} and no
 * {@code writing}, or each empty or null. It is set and never fetched, since a page opened at a {@code file:} address
 * may not fetch (ADR-333 Decided here 3).
 */
@Epic("The deliverable")
@Feature("The website rendering")
@Link(name = "ADR-333", url = Adr.A_PLUGIN_DRAWS_ONE_RENDERING_AND_DELIVERS_IT_TO_ONE_DESTINATION, type = "adr")
class TheSearchIndexHoldsNoExtractedTextTest {

    /** The script as a whole: one global set to a JSON array. */
    private static final Pattern THE_INDEX_SET = Pattern.compile(
            "^\\s*(?:globalThis|window|self)\\.VESPERA_SEARCH_INDEX\\s*=\\s*(\\[.*\\])\\s*;?\\s*$", Pattern.DOTALL);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    @Story("The website's search reads no document")
    @DisplayName("search-index.js holds each group's partition, title, label, writing and entries' names and links, and no document's text")
    void theIndexHoldsWhatPagesShowAndNoExtractedText(@TempDir Path base) throws IOException {
        Path rendering = renderingOf(WebsiteFixture.drawn(base));
        String script = textOf(rendering.resolve(SEARCH_INDEX));
        Matcher set = THE_INDEX_SET.matcher(script);

        claim(
                "the script sets VESPERA_SEARCH_INDEX to a JSON array, and does nothing else",
                () -> assertThat(set.matches()).as(script).isTrue());
        List<JsonNode> clusters = JSON.readTree(set.group(1)).valueStream().toList();
        claim(
                "it holds one element for each of the two groups, in their order",
                () -> assertThat(clusters).hasSize(2));
        JsonNode doors = clusters.get(0);
        JsonNode dampers = clusters.get(1);

        claim(
                "the doors are named by their seed partition, their title and their label",
                () -> {
                    assertThat(doors.path("partition").asString()).contains(PARTITION_NAME);
                    assertThat(doors.path("title").asString()).isEqualTo(DOORS_TITLE);
                    assertThat(doors.path("label").asString()).isEqualTo(DOORS_LABEL);
                });
        claim(
                "the doors carry the text of their synthesis doc",
                () -> assertThat(doors.path("writing").asString()).contains(FIRST_SENTENCE, SECOND_SENTENCE));
        claim(
                "the doors link their page, from the website's root",
                () -> assertThat(doors.path("link").asString()).isEqualTo(DOORS_FOLDER + "/index.html"));
        claim(
                "each of the doors' entries is named as its page shows it, the one whose copy did not stand among them",
                () -> assertThat(namesOf(doors)).containsExactly(DOOR_SURVEY, INSPECTION_LOG, GONE));
        claim(
                "the dampers, nothing written over them, have no title and no writing, and are found by their label and"
                        + " their entry",
                () -> {
                    assertThat(textOr(dampers, "title")).isEmpty();
                    assertThat(textOr(dampers, "writing")).isEmpty();
                    assertThat(dampers.path("label").asString()).isEqualTo(DAMPERS_LABEL);
                    assertThat(namesOf(dampers)).containsExactly(DAMPER_SCAN);
                    assertThat(dampers.path("link").asString()).isEqualTo(DAMPERS_FOLDER + "/index.html");
                });
        for (JsonNode cluster : clusters) {
            for (JsonNode entry : cluster.path("entries").valueStream().toList()) {
                URI link = URI.create(entry.path("link").asString());
                Path page = rendering.resolve(link.getPath()).normalize();
                claim(
                        entry.path("name").asString() + "'s link, followed from the website's root, names its group's"
                                + " page and the id of its entry there",
                        () -> {
                            assertThat(link.isAbsolute()).isFalse();
                            assertThat(page).isEqualTo(rendering.resolve(cluster.path("link").asString()).normalize());
                            assertThat(link.getFragment()).startsWith("document-");
                            assertThat(textOf(page)).contains("id=\"" + link.getFragment() + "\"");
                        });
            }
        }
        claim(
                "and the sentence marked in a document's bytes is in no file of the rendering, the index and the pages"
                        + " among them: nothing of a document's text is read for the search",
                () -> assertThat(filesUnder(rendering).values()).allSatisfy(file -> assertThat(textOf(file))
                        .as(file.toString())
                        .doesNotContain(A_MARKED_SENTENCE)));
    }

    /** {@code node}'s {@code field} as text, or empty where it is absent or null. */
    private static String textOr(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asString() : "";
    }

    private static List<String> namesOf(JsonNode cluster) {
        return cluster.path("entries").valueStream().map(entry -> entry.path("name").asString()).toList();
    }
}
