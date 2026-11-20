package io.algernon.vespera.engine.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
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
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A failure of a plugin's reading of the retrieval records stops that plugin alone (ADR-434 §4, ADR-333 §11): an
 * {@link IOException} out of {@code records}, {@code take} or {@code finish} stops it, its {@code drawing.txt} says
 * {@code failed}, the progress is told its name and the type thrown, and the tree and the other plugin go on whole. A
 * failure of composing a record stops every plugin it was being composed for, the tree going on as it would with none.
 */
@Epic("The deliverable")
@Feature("Retrieval records")
@Issue("890")
@Link(name = "ADR-434", url = Adr.THE_PROFILES_OUTPUTS_CHOOSE_WHAT_IS_DRAWN_AND_RETRIEVAL_RECORDS_ARE_ONE_JSONL_FILE, type = "adr")
class AFailedRetrievalReadingStopsItAloneTest {

    @ParameterizedTest(name = "a plugin throwing at {0}")
    @ValueSource(strings = {"records", "take", "finish"})
    @Story("A plugin's failed reading stops that plugin alone")
    @DisplayName("A plugin that throws at a call stops alone: the tree as with none, the other plugin whole, its state failed")
    void aPluginThatThrowsStopsAlone(String call, @TempDir Path base) throws IOException {
        Path alone = draw(base, List.of(), "tree", RetrievalReadings.inputs(new ArrayList<>()), new ArrayList<>());
        Map<String, String> theTreeWithNoPlugin = filesOutsidePlugins(alone);

        List<String> heard = new ArrayList<>();
        RetrievalReadings.Recording failing = new RetrievalReadings.Recording("aaa", true, heard).failingAt(call);
        RetrievalReadings.Recording whole = new RetrievalReadings.Recording("zzz", true, heard);
        List<String> told = new ArrayList<>();
        Path tree = draw(
                base, List.of(failing, whole), "tree, aaa, zzz", RetrievalReadings.inputs(new ArrayList<>()), told);

        claim("the tree is the one drawn with no plugin, file for file and byte for byte",
                () -> assertThat(filesOutsidePlugins(tree)).isEqualTo(theTreeWithNoPlugin));
        claim("the other plugin was handed every record and finished, its state drawn",
                () -> {
                    assertThat(whole.taken()).hasSize(25);
                    assertThat(Files.readString(tree.resolve("zzz/drawing.txt"))).isEqualTo("drawn\n");
                    assertThat(Files.readString(tree.resolve("zzz/zzz.jsonl"))).isEqualTo("25\n");
                });
        claim("the failing plugin's drawing.txt says failed, and the progress was told its name and the type it threw",
                () -> {
                    assertThat(Files.readString(tree.resolve("aaa/drawing.txt"))).isEqualTo("failed\n");
                    assertThat(told).containsExactly("aaa IOException");
                });
    }

    @org.junit.jupiter.api.Test
    @Story("A failed composing stops every plugin it was being composed for")
    @DisplayName("Inputs that throw stop each plugin reading the records, and the tree is the one drawn with none")
    void composingThatThrowsStopsEveryPluginReadingRecords(@TempDir Path base) throws IOException {
        Path alone = draw(base, List.of(), "tree", RetrievalReadings.inputs(new ArrayList<>()), new ArrayList<>());
        Map<String, String> theTreeWithNoPlugin = filesOutsidePlugins(alone);
        List<String> heard = new ArrayList<>();
        RetrievalReadings.Recording one = new RetrievalReadings.Recording("aaa", true, heard);
        RetrievalReadings.Recording another = new RetrievalReadings.Recording("zzz", false, heard);
        RecordInputs refusing = new RecordInputs() {
            @Override
            public String embedderIdentity() {
                throw new IllegalStateException("the artefact is another");
            }

            @Override
            public float[] vectorOf(String writing) {
                throw new IllegalStateException("not asked");
            }

            @Override
            public void passagesOf(ListedSurvivor member, PassageConsumer passages) {
                throw new IllegalStateException("not asked");
            }
        };
        List<String> told = new ArrayList<>();

        Path tree = draw(base, List.of(one, another), "tree, aaa, zzz", refusing, told);

        claim("the tree is the one drawn with no plugin, byte for byte",
                () -> assertThat(filesOutsidePlugins(tree)).isEqualTo(theTreeWithNoPlugin));
        claim("each plugin reading records is stopped, its state failed, and told once with the type thrown",
                () -> {
                    assertThat(Files.readString(tree.resolve("aaa/drawing.txt"))).isEqualTo("failed\n");
                    assertThat(Files.readString(tree.resolve("zzz/drawing.txt"))).isEqualTo("failed\n");
                    assertThat(told).containsExactly("aaa IllegalStateException", "zzz IllegalStateException");
                    assertThat(heard).isEmpty();
                });
    }

    private static Path draw(Path base, List<Plugin> plugins, String outputs, RecordInputs inputs, List<String> told) {
        return Deliverable.writeTo(
                base.resolve("work"),
                DeliverableRoot.UNNAMED,
                ThreeTopics.provenance(base.resolve("archive").toString()),
                RetrievalReadings.source(),
                ListedCodeSets.NONE,
                SurvivorPictures.none(),
                new ClusterPageCeiling.Unset(),
                plugins,
                Outputs.read(outputs),
                inputs,
                new DeliverableProgress() {
                    @Override
                    public void pluginDrawingFailed(String plugin, String exceptionType) {
                        told.add(plugin + " " + exceptionType);
                    }
                });
    }

    private static Map<String, String> filesOutsidePlugins(Path tree) throws IOException {
        Map<String, String> files = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(tree)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                String name = tree.relativize(file).toString().replace('\\', '/');
                if (!name.startsWith("aaa/") && !name.startsWith("zzz/")) {
                    files.put(name, Files.readString(file));
                }
            }
        }
        return files;
    }
}
