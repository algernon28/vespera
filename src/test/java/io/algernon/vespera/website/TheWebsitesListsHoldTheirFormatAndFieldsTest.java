package io.algernon.vespera.website;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.website.WebsiteFixture.FILES_JSON;
import static io.algernon.vespera.website.WebsiteFixture.VESPERA_WEBSITE_JSON;
import static io.algernon.vespera.website.WebsiteFixture.WEBSITE;
import static io.algernon.vespera.website.WebsiteFixture.renderingOf;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.deliverable.Plugin;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The website's two lists are of the shape ADR-339 §3 records: the rendering's {@code files.json}, {@code {format: 1,
 * files: [{path, source, size, sha256, kind}]}}, and the website's own {@code vespera-website.json}, {@code {format: 1,
 * files: [{path, sha256}]}}, each entry naming those fields and no other.
 */
@Epic("Operation")
@Feature("The website")
@Link(name = "ADR-339", url = Adr.AN_ARGUMENT_NAMES_THE_FORM_ITS_CONTROL_STANDS_ON_AND_THE_WEBSITE_IS_BUILT_AS_RECORDED, type = "adr")
@Link(name = "ADR-333", url = Adr.A_PLUGIN_DRAWS_ONE_RENDERING_AND_DELIVERS_IT_TO_ONE_DESTINATION, type = "adr")
class TheWebsitesListsHoldTheirFormatAndFieldsTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    @Story("The website is refreshed in one folder")
    @DisplayName("files.json is format 1 listing each file by its path, source, size, digest and kind, and vespera-website.json format 1 listing each by its path and digest")
    void eachListIsFormatOneOfItsOwnFields(@TempDir Path base) throws IOException {
        Path tree = WebsiteFixture.drawn(base.resolve("drawn"));
        Path workingDirectory = Files.createDirectories(base.resolve("work"));
        Plugin.Delivery delivery = WebsiteFixture.published(tree, workingDirectory);
        JsonNode rendering = JSON.readTree(renderingOf(tree).resolve(FILES_JSON).toFile());
        JsonNode website = JSON.readTree(workingDirectory.resolve(WEBSITE).resolve(VESPERA_WEBSITE_JSON).toFile());

        claim(
                "the publish delivered",
                () -> assertThat(delivery.delivered()).as(delivery.closingLine()).isTrue());
        claim(
                "files.json holds its format, 1, and its files, and nothing else",
                () -> {
                    assertThat(namesOf(rendering)).containsExactlyInAnyOrder("format", "files");
                    assertThat(rendering.get("format").asInt()).isEqualTo(1);
                    assertThat(rendering.get("files").isArray()).isTrue();
                });
        claim(
                "each of its entries names its path, source, size, digest and kind, and nothing else",
                () -> assertThat(entriesOf(rendering))
                        .isNotEmpty()
                        .allSatisfy(entry -> assertThat(namesOf(entry))
                                .containsExactlyInAnyOrder("path", "source", "size", "sha256", "kind")));
        claim(
                "vespera-website.json holds its format, 1, and its files, and nothing else",
                () -> {
                    assertThat(namesOf(website)).containsExactlyInAnyOrder("format", "files");
                    assertThat(website.get("format").asInt()).isEqualTo(1);
                    assertThat(website.get("files").isArray()).isTrue();
                });
        claim(
                "and each of its entries names its path and digest alone, one for each file files.json lists",
                () -> {
                    assertThat(entriesOf(website))
                            .allSatisfy(entry -> assertThat(namesOf(entry)).containsExactlyInAnyOrder("path", "sha256"));
                    assertThat(pathsOf(website)).isEqualTo(pathsOf(rendering));
                });
    }

    private static List<String> namesOf(JsonNode object) {
        return List.copyOf(object.propertyNames());
    }

    private static List<JsonNode> entriesOf(JsonNode list) {
        return list.get("files").valueStream().toList();
    }

    private static Set<String> pathsOf(JsonNode list) {
        Set<String> paths = new TreeSet<>();
        entriesOf(list).forEach(entry -> paths.add(entry.get("path").asString()));
        return paths;
    }
}
