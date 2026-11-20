package io.algernon.vespera.engine.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How the profile's {@code outputs} is read, and which readings no drawing can draw (ADR-434 §2, §3).
 *
 * <p>The key is split at commas, each name stripped and lower-cased, an empty piece skipped and a name written twice
 * kept once at its first place; unset, blank or naming nothing it is {@code tree, confluence, website}. A name that is
 * neither {@code tree} nor a plugin's is refused first, naming what is known; then a plugin that draws from the HTML
 * tree, named without {@code tree}. The plugins are handed out of the order of their names, so that the list of known
 * names is seen to be put in order and not taken as handed.
 */
@Epic("The deliverable")
@Feature("What is drawn")
@Issue("890")
@Link(name = "ADR-434", url = Adr.THE_PROFILES_OUTPUTS_CHOOSE_WHAT_IS_DRAWN_AND_RETRIEVAL_RECORDS_ARE_ONE_JSONL_FILE, type = "adr")
class TheOutputsAreReadFromTheProfilesKeyTest {

    /** The three outputs drawn where the key names none: the HTML tree, the Confluence rendering and the website. */
    private static final List<String> THE_THREE = List.of("tree", "confluence", "website");

    /** The plugins compiled into the jar, handed in no order of their names: two drawing from the tree, one not. */
    private static final List<Plugin> PLUGINS = List.of(
            new Named("website", true), new Named("retrieval", false), new Named("confluence", true));

    /** What every refusal of an unknown name says is known: the tree first, then each plugin in the order of names. */
    private static final String KNOWN = "it knows tree, confluence, retrieval and website";

    private static final String NAME_AMONG_THOSE =
            "name outputs among those in profile.yaml, or leave it unset for tree, confluence and website";

    @Test
    @Story("The key's default")
    @DisplayName("A key left unset, blank, or holding commas alone draws the pages, Confluence and the website")
    void anUnsetOrBlankKeyDrawsTheTreeConfluenceAndTheWebsite() {
        claim("unset, the key reads as the tree, confluence and website, in that order, and draws the tree",
                () -> {
                    assertThat(Outputs.read(null).names()).containsExactlyElementsOf(THE_THREE);
                    assertThat(Outputs.read(null).drawsTheTree()).isTrue();
                });
        claim("blank, it reads the same", () -> assertThat(Outputs.read("   ").names())
                .containsExactlyElementsOf(THE_THREE));
        claim("holding commas and blanks alone, so naming nothing, it reads the same",
                () -> assertThat(Outputs.read(" , ,").names()).containsExactlyElementsOf(THE_THREE));
        claim("and that is the default the class names",
                () -> assertThat(Outputs.DEFAULT.names()).containsExactlyElementsOf(THE_THREE));
    }

    @Test
    @Story("The key as written")
    @DisplayName("Each name is read once, its blanks stripped and in lower case, an empty piece skipped")
    void eachNameIsReadOnceStrippedAndInLowerCase() {
        Outputs read = Outputs.read(" Retrieval , TREE,,retrieval ");

        claim("the names read are retrieval then tree, in the order first written, the second retrieval dropped",
                () -> assertThat(read.names()).containsExactly("retrieval", "tree"));
        claim("and naming tree, it draws the tree", () -> assertThat(read.drawsTheTree()).isTrue());
        claim("a key naming retrieval alone does not draw the tree",
                () -> assertThat(Outputs.read("retrieval").drawsTheTree()).isFalse());
    }

    @Test
    @Story("Outputs no drawing can draw")
    @DisplayName("A name that is no output is refused, naming each output that is known")
    void aNameThatIsNoOutputIsRefusedNamingWhatIsKnown() {
        claim("one unknown name: the reason names it and every output known, and the advice says how to name them",
                () -> assertThat(Outputs.read("tree, sharepoint").refusal(PLUGINS))
                        .contains(new Outputs.Refusal(
                                "outputs names sharepoint, which is no output Vespera draws: " + KNOWN,
                                NAME_AMONG_THOSE)));
        claim("two unknown names: the reason names both in the order written, and the same advice",
                () -> assertThat(Outputs.read("wiki, retrieval, sharepoint").refusal(PLUGINS))
                        .contains(new Outputs.Refusal(
                                "outputs names wiki and sharepoint, which are no outputs Vespera draws: " + KNOWN,
                                NAME_AMONG_THOSE)));
    }

    @Test
    @Story("Outputs no drawing can draw")
    @DisplayName("A plugin drawn from the pages, named without them, is refused")
    void aPluginDrawnFromTheTreeWithoutTheTreeIsRefused() {
        claim("the website alone: the reason names it and says it draws from the HTML tree, and the advice how to mend it",
                () -> assertThat(Outputs.read("website").refusal(PLUGINS))
                        .contains(new Outputs.Refusal(
                                "outputs names website but not tree, and it draws from the HTML tree",
                                "add tree to outputs in profile.yaml, or take website out of it")));
        claim("the website, retrieval and confluence: the two drawing from the tree named in the order written",
                () -> assertThat(Outputs.read("website, retrieval, confluence").refusal(PLUGINS))
                        .contains(new Outputs.Refusal(
                                "outputs names website and confluence but not tree, and they draw from the HTML tree",
                                "add tree to outputs in profile.yaml, or take website and confluence out of it")));
        claim("retrieval alone, which draws from no tree, is refused nothing",
                () -> assertThat(Outputs.read("retrieval").refusal(PLUGINS)).isEmpty());
        claim("nor is the default, nor the tree with retrieval",
                () -> {
                    assertThat(Outputs.DEFAULT.refusal(PLUGINS)).isEmpty();
                    assertThat(Outputs.read("tree, retrieval").refusal(PLUGINS)).isEmpty();
                });
    }

    @Test
    @Story("Outputs no drawing can draw")
    @DisplayName("Where a name is unknown and the pages are missing too, only the unknown name is told")
    void anUnknownNameIsToldBeforeAMissingTree() {
        claim("the website and sharepoint: the reason is the unknown name's, and nothing is said of the tree",
                () -> assertThat(Outputs.read("website, sharepoint").refusal(PLUGINS))
                        .contains(new Outputs.Refusal(
                                "outputs names sharepoint, which is no output Vespera draws: " + KNOWN,
                                NAME_AMONG_THOSE)));
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
            throw new UnsupportedOperationException("reading the key draws nothing");
        }

        @Override
        public Delivery deliver(Path rendering, String generationRun, Path workingDirectory, Consumer<String> out) {
            throw new UnsupportedOperationException("reading the key delivers nothing");
        }

        @Override
        public Optional<RetrievalRecords> records(DrawingContext context) {
            return Optional.empty();
        }
    }
}
