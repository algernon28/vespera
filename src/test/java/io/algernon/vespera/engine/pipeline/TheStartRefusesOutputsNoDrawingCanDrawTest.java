package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.deliverable.Drawing;
import io.algernon.vespera.engine.deliverable.Plugin;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The line that ends the start of {@code vespera run} where the profile's {@code outputs} names what no drawing can
 * draw (ADR-434 §3): {@code vespera run stopped at its start: <reason>, so nothing was walked and no run was minted --
 * <advice>}, the shape of ADR-384 §2's, read from {@code profile.yaml} in the working directory. Which reasons there are
 * is {@code deliverable.TheOutputsAreReadFromTheProfilesKeyTest}'s; this holds the line and where the key is read from.
 */
@Epic("Pipeline")
@Feature("What is drawn")
@Issue("890")
@Link(name = "ADR-434", url = Adr.THE_PROFILES_OUTPUTS_CHOOSE_WHAT_IS_DRAWN_AND_RETRIEVAL_RECORDS_ARE_ONE_JSONL_FILE, type = "adr")
class TheStartRefusesOutputsNoDrawingCanDrawTest {

    /** The plugins compiled into the jar, two drawing from the HTML tree and one not. */
    private static final List<Plugin> PLUGINS = List.of(
            new Named("confluence", true), new Named("retrieval", false), new Named("website", true));

    @Test
    @Story("Outputs no drawing can draw")
    @DisplayName("A profile naming the website without the pages stops the run at its start, in one line saying why and what to do")
    void aProfileNamingTheWebsiteAloneStopsTheStartInOneLine(@TempDir Path workingDirectory) throws IOException {
        ProfileStore store = new ProfileStore(workingDirectory);
        Files.writeString(store.file(), """
                outputs:
                  value: website
                  provenance: the website alone
                """);

        claim("the line names the website, says it draws from the HTML tree, that nothing was walked and no run"
                        + " minted, and how to mend the key",
                () -> assertThat(new OutputsAtTheStart(store, PLUGINS).refusal())
                        .contains("vespera run stopped at its start: outputs names website but not tree, and it draws"
                                + " from the HTML tree, so nothing was walked and no run was minted -- add tree to"
                                + " outputs in profile.yaml, or take website out of it"));
    }

    @Test
    @Story("Outputs no drawing can draw")
    @DisplayName("A profile that leaves the key unset stops nothing")
    void aProfileLeavingTheKeyUnsetStopsNothing(@TempDir Path workingDirectory) throws IOException {
        ProfileStore store = new ProfileStore(workingDirectory);
        Files.writeString(store.file(), """
                relevanceScoreFloor:
                  value: "0.42"
                  provenance: read off the labelling page
                """);

        claim("no line: unset, the key draws the pages, Confluence and the website, which any drawing can",
                () -> assertThat(new OutputsAtTheStart(store, PLUGINS).refusal()).isEmpty());
    }

    /** A plugin by its name, drawing from the HTML tree or not; nothing else of it is asked. */
    private record Named(String name, boolean fromTheTree) implements Plugin {

        @Override
        public String listName() {
            return name + ".list";
        }

        @Override
        public boolean drawsFromTheTree() {
            return fromTheTree;
        }

        @Override
        public Drawing drawing(DrawingContext context) {
            throw new UnsupportedOperationException("the start draws nothing");
        }

        @Override
        public Delivery deliver(Path rendering, String generationRun, Path workingDirectory, Consumer<String> out) {
            throw new UnsupportedOperationException("the start delivers nothing");
        }
    }
}
