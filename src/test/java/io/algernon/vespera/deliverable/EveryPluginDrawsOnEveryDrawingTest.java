package io.algernon.vespera.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
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
 * Every plugin handed to a drawing draws on it (ADR-333 §2, §4): {@code Deliverable.writeTo} opens each plugin's
 * drawing, calls it at each of the five places {@code ConfluencePages} was called at, in the order of the plugins'
 * names, and hands {@code ClusterPage} one set of entries that passes each call on to every plugin still drawing. Two
 * scripted plugins, handed in the reverse of their names' order, write every call they hear into one list.
 *
 * <p>The state of each drawing is written by {@code Deliverable} in {@code <plugin folder>/drawing.txt} (ADR-333 §11),
 * {@code drawn} once its {@code finish} and {@code close} have returned. That a recorded generation step draws the
 * plugins too, the path {@code vespera run} takes over a run whose writing is recorded (ADR-333 §12), is {@code
 * pipeline.EveryPluginDrawsOnTheRecordedPathInvocationTest}'s: this module knows no step.
 */
@Epic("The deliverable")
@Feature("The plugins")
@Link(name = "ADR-333", url = Adr.A_PLUGIN_DRAWS_ONE_RENDERING_AND_DELIVERS_IT_TO_ONE_DESTINATION, type = "adr")
class EveryPluginDrawsOnEveryDrawingTest {

    /** The file of a plugin's folder in which the drawing writes the state of that plugin's drawing (ADR-333 §11). */
    static final String THE_STATE_OF_THE_DRAWING = "drawing.txt";

    @Test
    @Story("Every plugin draws on every drawing")
    @DisplayName("Two plugins each hear every call of the drawing, in order, and each call is made to both in the order of their names")
    void twoPluginsEachHearEveryCallInOrder(@TempDir Path base) throws IOException {
        List<String> heard = new ArrayList<>();
        ScriptedPlugin alpha = new ScriptedPlugin("alpha", heard);
        ScriptedPlugin zulu = new ScriptedPlugin("zulu", heard);

        TwoClustersWithPictures.draw(base, List.of(zulu, alpha), DeliverableProgress.NONE);

        claim(
                "each plugin opens its drawing, hears the seed partition start, each group's entries opened, every"
                        + " entry, its copy shown, its picture and its picture not shown, each group drawn with the"
                        + " entries it opened, the seed partition end and the finish once documents.csv stands, and"
                        + " closes",
                () -> {
                    assertThat(TwoClustersWithPictures.withoutEntriesClosed(alpha.calls()))
                            .as("what alpha heard")
                            .containsExactlyElementsOf(TwoClustersWithPictures.EVERY_CALL);
                    assertThat(TwoClustersWithPictures.withoutEntriesClosed(zulu.calls()))
                            .as("what zulu heard")
                            .containsExactlyElementsOf(TwoClustersWithPictures.EVERY_CALL);
                });
        claim(
                "every call up to the finish is made to alpha and then to zulu before the drawing goes on, though zulu"
                        + " was handed in first: the plugins are called in the order of their names",
                () -> {
                    List<String> interleaved = new ArrayList<>();
                    for (String call : TwoClustersWithPictures.EVERY_CALL.subList(
                            0, TwoClustersWithPictures.EVERY_CALL.size() - 1)) {
                        interleaved.add("alpha:" + call);
                        interleaved.add("zulu:" + call);
                    }
                    assertThat(TwoClustersWithPictures.withoutEntriesClosed(heard).stream()
                                    .filter(call -> !call.endsWith(":" + ScriptedPlugin.CLOSE))
                                    .toList())
                            .containsExactlyElementsOf(interleaved);
                });
    }

    @Test
    @Story("Every plugin draws on every drawing")
    @DisplayName("Each plugin's drawing is handed the tree, the generation run, the language and the set of folders written, and its folder says drawn")
    void eachPluginIsHandedTheTreeAndItsFolderSaysDrawn(@TempDir Path base) throws IOException {
        List<String> heard = new ArrayList<>();
        ScriptedPlugin alpha = new ScriptedPlugin("alpha", heard);
        ScriptedPlugin zulu = new ScriptedPlugin("zulu", heard);

        Path tree = TwoClustersWithPictures.draw(base, List.of(alpha, zulu), DeliverableProgress.NONE);

        claim(
                "each drawing is opened on the tree being drawn, for its generation run, in the deliverable language,"
                        + " with a way to move a file into place and the folders the drawing has written, to which the"
                        + " plugin added its own",
                () -> assertThat(List.of(alpha, zulu)).allSatisfy(plugin -> {
                    Plugin.DrawingContext context = plugin.context();
                    assertThat(context.tree()).isEqualTo(tree);
                    assertThat(context.generationRun()).isEqualTo(ListedTree.RUN_ID);
                    assertThat(context.language()).isEqualTo(io.algernon.vespera.synthesis.DeliverableLanguage.ENGLISH);
                    assertThat(context.moves()).isNotNull();
                    assertThat(context.written()).contains(plugin.name());
                }));
        claim(
                "each plugin's folder lies at the tree's root under its name, and its drawing.txt says drawn",
                () -> assertThat(List.of("alpha", "zulu")).allSatisfy(name -> assertThat(
                                Files.readString(tree.resolve(name).resolve(THE_STATE_OF_THE_DRAWING),
                                        StandardCharsets.UTF_8).strip())
                        .as("the state of %s's drawing", name)
                        .isEqualTo("drawn")));
        claim(
                "and each went to its end, writing the file it writes at its finish",
                () -> assertThat(List.of("alpha", "zulu")).allSatisfy(name ->
                        assertThat(tree.resolve(name).resolve(ScriptedPlugin.ITS_FILE)).isRegularFile()));
    }

    @Test
    @Story("Every plugin draws on every drawing")
    @DisplayName("A plugin's folder is a folder every drawing writes, so it is never counted as left behind")
    void aPluginsFolderIsNeverCountedAsLeftBehind(@TempDir Path base) throws IOException {
        long[] told = {-1};
        DeliverableProgress hearing = new DeliverableProgress() {
            @Override
            public void leftBehind(boolean documentsFolder, long otherFolders) {
                told[0] = otherFolders;
            }
        };

        TwoClustersWithPictures.draw(
                base, List.of(new ScriptedPlugin("alpha", new ArrayList<>()).addingNoFolderOfItsOwn()), hearing);

        claim(
                "a first drawing with a plugin that adds nothing to what this drawing wrote counts no folder left by"
                        + " an earlier drawing: the drawing itself takes the plugin's folder as this drawing's, so no"
                        + " plugin need (ADR-312 §4 as ADR-333 §2 has it of every plugin)",
                () -> assertThat(told[0]).isZero());
    }

    @Test
    @Story("Every plugin draws on every drawing")
    @DisplayName("A drawing handed no plugin draws the tree alone and makes no plugin's folder")
    void aDrawingHandedNoPluginDrawsTheTreeAlone(@TempDir Path base) throws IOException {
        Path tree = TwoClustersWithPictures.draw(base, List.of(), DeliverableProgress.NONE);

        claim(
                "the tree's top holds the index, the manifest, the marker and the seed partition's folder, and no"
                        + " drawing.txt lies anywhere in it",
                () -> {
                    assertThat(ListedTree.filesUnder(tree)).noneMatch(file -> file.endsWith(THE_STATE_OF_THE_DRAWING));
                    assertThat(tree.resolve(Deliverable.INDEX_FILE_NAME)).isRegularFile();
                    assertThat(tree.resolve(Deliverable.MANIFEST_FILE_NAME)).isRegularFile();
                });
    }
}
