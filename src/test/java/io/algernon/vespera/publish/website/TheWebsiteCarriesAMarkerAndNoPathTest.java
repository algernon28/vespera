package io.algernon.vespera.publish.website;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.publish.website.WebsiteFixture.VESPERA_WEBSITE_JSON;
import static io.algernon.vespera.publish.website.WebsiteFixture.WEBSITE;
import static io.algernon.vespera.publish.website.WebsiteFixture.textOf;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.deliverable.DeliverableRoot;
import io.algernon.vespera.engine.deliverable.Plugin;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * To an agent the website is a tree (ADR-333 §7, ADR-283): {@code vespera-deliverable.txt} at its top, the marker
 * every tree carries, makes the private-paths guard read the folder as outside the allow list. The website's marker
 * holds one line, {@code website}, and names no path, since the operator's sync carries it; nor does {@code
 * vespera-website.json}, the website's own list, name the working directory or the tree it was published from.
 */
@Epic("Operation")
@Feature("The website")
@Link(name = "ADR-333", url = Adr.A_PLUGIN_DRAWS_ONE_RENDERING_AND_DELIVERS_IT_TO_ONE_DESTINATION, type = "adr")
@Link(name = "ADR-283", url = Adr.THE_GUARD_READS_A_DELIVERABLE_TREE_AS_OUTSIDE_THE_ALLOW_LIST, type = "adr")
class TheWebsiteCarriesAMarkerAndNoPathTest {

    @Test
    @Story("The website carries the tree's marker")
    @DisplayName("The website's marker is the tree's file holding the one line website, and neither it nor the website's own list names a path")
    void theMarkerIsOneLineAndNothingNamesAPath(@TempDir Path base) throws IOException {
        Path drawnIn = base.resolve("drawn-7c1e");
        Path tree = WebsiteFixture.drawn(drawnIn);
        Path workingDirectory = Files.createDirectories(base.resolve("working-directory-3a9f"));
        Plugin.Delivery delivery = WebsiteFixture.published(tree, workingDirectory);
        Path website = workingDirectory.resolve(WEBSITE);
        Path marker = website.resolve(DeliverableRoot.MARKER_FILE_NAME);

        claim(
                "the publish delivered, and the website's top holds the marker by the name every tree's carries",
                () -> {
                    assertThat(delivery.delivered()).as(delivery.closingLine()).isTrue();
                    assertThat(DeliverableRoot.MARKER_FILE_NAME).isEqualTo("vespera-deliverable.txt");
                    assertThat(marker).isRegularFile();
                });
        claim(
                "the marker holds one line, website",
                () -> assertThat(Files.readAllLines(marker, StandardCharsets.UTF_8)).containsExactly("website"));
        List<String> spellings = spellingsOf(workingDirectory, tree, drawnIn, base);
        claim(
                "neither the marker nor the website's own list names the working directory, the tree or any folder"
                        + " above them, in any spelling of the path",
                () -> {
                    assertThat(textOf(marker)).doesNotContain(spellings);
                    assertThat(textOf(website.resolve(VESPERA_WEBSITE_JSON))).doesNotContain(spellings);
                });
        claim(
                "nor does either name the folders' own names, which a path would carry",
                () -> {
                    assertThat(textOf(marker)).doesNotContain("working-directory-3a9f", "drawn-7c1e");
                    assertThat(textOf(website.resolve(VESPERA_WEBSITE_JSON)))
                            .doesNotContain("working-directory-3a9f", "drawn-7c1e");
                });
    }

    /** Each path as written, as its real path, with {@code /} between, and with each {@code \} doubled as JSON writes it. */
    private static List<String> spellingsOf(Path... paths) throws IOException {
        List<String> spellings = new ArrayList<>();
        for (Path path : paths) {
            for (String spelt : List.of(path.toAbsolutePath().toString(), path.toRealPath().toString())) {
                spellings.add(spelt);
                spellings.add(spelt.replace('\\', '/'));
                spellings.add(spelt.replace("\\", "\\\\"));
            }
        }
        return spellings;
    }
}
