package io.algernon.vespera.publish.confluence;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.deliverable.Plugin;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Confluence rendering's plugin (ADR-333 §1): named {@code confluence}, the destination's name and its folder at
 * the tree's root (ADR-312 §1), its rendering found by its page list, {@code pages.json} (ADR-316 §1), and its delivery
 * {@link Publisher}'s publish, whose signature does not change, its outcome handed back as it came.
 *
 * <p>Untagged: it sends nothing, and CI's {@code -DexcludedGroups=confluence} is for the publish's integration tests
 * alone (ADR-320 §6).
 */
@Epic("Operation")
@Feature("The plugins")
@Link(name = "ADR-333", url = Adr.A_PLUGIN_DRAWS_ONE_RENDERING_AND_DELIVERS_IT_TO_ONE_DESTINATION, type = "adr")
class ConfluencePluginTest {

    /** A publisher that sends nothing, recording what it was asked and answering what it is told to. */
    private static final class Recording extends Publisher {
        final List<String> asked = new ArrayList<>();
        private final Outcome answer;

        Recording(Outcome answer) {
            super("", "", "", "");
            this.answer = answer;
        }

        @Override
        public Outcome publish(Path confluence, String generationRun, Path workingDirectory, Consumer<String> out) {
            asked.add(confluence + " " + generationRun + " " + workingDirectory);
            out.accept("a line of the publish");
            return answer;
        }
    }

    @Test
    @Story("A plugin both draws and delivers")
    @DisplayName("The Confluence plugin is named confluence, and finds its rendering by pages.json")
    void itIsNamedConfluenceAndFindsItsRenderingByItsPageList() {
        Plugin plugin = new ConfluencePlugin(new Recording(new Publisher.Outcome(true, "")));

        claim(
                "its name is confluence, the folder the rendering lies in at the tree's root (ADR-312 §1), which"
                        + " --to confluence and every tree drawn before name, and lowercase letters a to z alone",
                () -> assertThat(plugin.name()).isEqualTo("confluence").matches("[a-z]+"));
        claim(
                "and its list is the page list, pages.json (ADR-316 §1), which every rendering drawn before carries",
                () -> assertThat(plugin.listName()).isEqualTo("pages.json"));
    }

    @Test
    @Story("A plugin both draws and delivers")
    @DisplayName("A delivery to Confluence is the publisher's publish of the rendering handed in, its outcome handed back as it came")
    void aDeliveryIsThePublishersPublish() {
        Path rendering = Path.of("work", "deliverable", "run", "confluence");
        Path workingDirectory = Path.of("work");
        Recording sent = new Recording(new Publisher.Outcome(true, "vespera publish sent 4 pages."));
        Recording stopped = new Recording(new Publisher.Outcome(false, "vespera publish stopped: a reason."));
        List<String> lines = new ArrayList<>();

        Plugin.Delivery delivered = new ConfluencePlugin(sent).deliver(rendering, "run", workingDirectory, lines::add);
        Plugin.Delivery refused = new ConfluencePlugin(stopped).deliver(rendering, "run", workingDirectory, line -> {});

        claim(
                "the publisher was asked once, for that rendering of that run, writing in that working directory, its"
                        + " lines going where the delivery's go",
                () -> {
                    assertThat(sent.asked).containsExactly(rendering + " run " + workingDirectory);
                    assertThat(lines).containsExactly("a line of the publish");
                });
        claim(
                "and the delivery is the publish's outcome: delivered where it went to its end, with its closing line,"
                        + " and not where it stopped",
                () -> assertThat(List.of(delivered, refused)).containsExactly(
                        new Plugin.Delivery(true, "vespera publish sent 4 pages."),
                        new Plugin.Delivery(false, "vespera publish stopped: a reason.")));
    }
}
