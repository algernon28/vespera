package io.algernon.vespera.engine.deliverable;

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
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A plugin's failed drawing stops that plugin alone (ADR-333 §11): an {@link IOException} or a {@link
 * RuntimeException} out of any call of a plugin's drawing or its entries, or out of opening its drawing, is caught;
 * {@code Deliverable} closes that drawing, swallowing what closing it throws, calls it no more and goes on, so the tree
 * is drawn as it is with no plugin and every other plugin draws whole. It writes {@code failed} in that plugin's
 * {@code drawing.txt}, tells {@link DeliverableProgress} the plugin's name and the type of what it threw, and the next
 * drawing that finishes writes {@code drawn} there again. An {@link Error} is not caught.
 *
 * <p>The failing plugin is {@code alpha}, the first called at every place, so that what it throws is thrown before
 * {@code zulu} hears the same call. Each case draws the fixture three times in one place: with no plugin, then with
 * the two and {@code alpha} failing, then with the two and neither failing; a tree drawn again in one place is the
 * same tree byte for byte (ADR-223 §6), so the first and the second are compared.
 *
 * <p>What the closing line says of it is {@code pipeline}'s, and what a publish then does is held by {@code
 * pipeline.PublishRefusesARenderingWhoseDrawingDidNotFinishTest}.
 */
@Epic("The deliverable")
@Feature("The plugins")
@Link(name = "ADR-333", url = Adr.A_PLUGIN_DRAWS_ONE_RENDERING_AND_DELIVERS_IT_TO_ONE_DESTINATION, type = "adr")
class APluginsFailedDrawingStopsItAloneTest {

    private static final String FAILING = "alpha";

    private static final String WHOLE = "zulu";

    /**
     * One call of the drawing proper and one of its closing, each with a kind of exception (ADR-333 §11: any call
     * of its entries).
     */
    static Stream<Arguments> everyCallAndEachKindOfException() {
        return Stream.of(
                Arguments.of(ScriptedPlugin.ENTRIES_OF, ScriptedPlugin.Failure.IO_EXCEPTION),
                Arguments.of(ScriptedPlugin.FINISH, ScriptedPlugin.Failure.RUNTIME_EXCEPTION));
    }

    @ParameterizedTest(name = "{1} at {0}")
    @MethodSource("everyCallAndEachKindOfException")
    @Story("A plugin's failed drawing stops that plugin alone")
    @DisplayName("A plugin that throws at a call of its drawing stops alone: the tree is drawn as with none, the other plugin whole, its state failed, and drawn after the next drawing")
    void aPluginThatThrowsStopsAlone(String call, ScriptedPlugin.Failure failure, @TempDir Path base)
            throws IOException {
        Path alone = TwoClustersWithPictures.draw(base, List.of(), DeliverableProgress.NONE);
        Map<String, byte[]> theTreeWithNoPlugin = everyFileOf(alone);

        List<String> heard = new ArrayList<>();
        ScriptedPlugin failing = new ScriptedPlugin(FAILING, heard).failingAt(call, failure);
        ScriptedPlugin whole = new ScriptedPlugin(WHOLE, heard);
        List<List<String>> told = new ArrayList<>();
        Path tree = TwoClustersWithPictures.draw(base, List.of(failing, whole), hearing(told));
        Map<String, byte[]> theTreeWithTheTwo = everyFileOf(tree);
        String stateAfterTheFailure = stateOf(tree, FAILING);
        List<String> failingHeard = failing.calls();

        claim(
                "the drawing went to its end at the same tree, and every file of it outside the plugins' folders is"
                        + " the one the drawing with no plugin wrote, byte for byte",
                () -> {
                    assertThat(tree).isEqualTo(alone);
                    assertThat(theTreeWithTheTwo.keySet())
                            .containsExactlyInAnyOrderElementsOf(theTreeWithNoPlugin.keySet());
                    theTreeWithNoPlugin.forEach((name, bytes) ->
                            assertThat(theTreeWithTheTwo.get(name)).as(name).isEqualTo(bytes));
                });
        claim(
                "the other plugin heard every call of the drawing, in order, and went to its end",
                () -> {
                    assertThat(TwoClustersWithPictures.withoutEntriesClosed(whole.calls()))
                            .containsExactlyElementsOf(TwoClustersWithPictures.EVERY_CALL);
                    assertThat(stateOf(tree, WHOLE)).isEqualTo("drawn");
                    assertThat(tree.resolve(WHOLE).resolve(ScriptedPlugin.ITS_FILE)).isRegularFile();
                });
        claim(
                "the failing plugin was called no more after it threw, but to be closed where its drawing was open",
                () -> {
                    int thrownAt = indexOfTheFirst(failingHeard, call);
                    assertThat(thrownAt).as("where %s threw", FAILING).isNotNegative();
                    List<String> after = TwoClustersWithPictures.withoutEntriesClosed(
                            failingHeard.subList(thrownAt + 1, failingHeard.size()));
                    if (call.equals(ScriptedPlugin.DRAWING) || call.equals(ScriptedPlugin.CLOSE)) {
                        assertThat(after).isEmpty();
                    } else {
                        assertThat(after).containsExactly(ScriptedPlugin.CLOSE);
                    }
                });
        claim(
                "its drawing.txt says failed, and the progress was told once, of that plugin and the type it threw,"
                        + " and never of the other",
                () -> {
                    assertThat(stateAfterTheFailure).isEqualTo("failed");
                    assertThat(told).containsExactly(List.of(FAILING, failure.type));
                });

        failing.healthy();
        heard.clear();
        Path again = TwoClustersWithPictures.draw(base, List.of(failing, whole), DeliverableProgress.NONE);

        claim(
                "the next drawing, in which it throws nothing, draws it whole and its drawing.txt says drawn",
                () -> {
                    assertThat(TwoClustersWithPictures.withoutEntriesClosed(failing.calls()))
                            .containsExactlyElementsOf(TwoClustersWithPictures.EVERY_CALL);
                    assertThat(stateOf(again, FAILING)).isEqualTo("drawn");
                });
    }

    /** A progress telling {@code told} each plugin whose drawing failed, as its name and the type it threw. */
    private static DeliverableProgress hearing(List<List<String>> told) {
        return new DeliverableProgress() {
            @Override
            public void pluginDrawingFailed(String plugin, String exceptionType) {
                told.add(List.of(plugin, exceptionType));
            }
        };
    }

    /** What {@code plugin}'s drawing.txt in {@code tree} says, stripped. */
    private static String stateOf(Path tree, String plugin) {
        try {
            return Files.readString(
                    tree.resolve(plugin).resolve(EveryPluginDrawsOnEveryDrawingTest.THE_STATE_OF_THE_DRAWING),
                    StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** Every file of {@code tree} outside the two plugins' folders, by its path in the tree, with its bytes. */
    private static Map<String, byte[]> everyFileOf(Path tree) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        for (String name : ListedTree.filesUnder(tree)) {
            if (!name.startsWith(FAILING + "/") && !name.startsWith(WHOLE + "/")) {
                files.put(name, Files.readAllBytes(tree.resolve(name)));
            }
        }
        return files;
    }

    /** Where the first call of kind {@code call} is among {@code calls}, or -1. */
    private static int indexOfTheFirst(List<String> calls, String call) {
        for (int at = 0; at < calls.size(); at++) {
            if (calls.get(at).equals(call) || calls.get(at).startsWith(call + " ")) {
                return at;
            }
        }
        return -1;
    }
}
