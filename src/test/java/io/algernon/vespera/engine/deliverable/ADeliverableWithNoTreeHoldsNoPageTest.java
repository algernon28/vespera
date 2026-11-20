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
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Where {@code outputs} does not name {@code tree}, the drawing writes the marker and each named plugin's folder with
 * its {@code drawing.txt}, and nothing of the tree (ADR-434 §9): no {@code index.html}, no topic or group folder, no
 * copy, no {@code documents.csv}, no {@code code/}. What an earlier drawing left is kept, as nothing beneath a
 * deliverable root is deleted.
 */
@Epic("The deliverable")
@Feature("Retrieval records")
@Issue("890")
@Link(name = "ADR-434", url = Adr.THE_PROFILES_OUTPUTS_CHOOSE_WHAT_IS_DRAWN_AND_RETRIEVAL_RECORDS_ARE_ONE_JSONL_FILE, type = "adr")
class ADeliverableWithNoTreeHoldsNoPageTest {

    @Test
    @Story("No tree is drawn")
    @DisplayName("Outputs naming retrieval alone draw the marker and retrieval/ with its state, no page, no copy and no manifest")
    void aDrawingWithNoTreeHoldsTheMarkerAndThePluginsFolderAlone(@TempDir Path base) throws IOException {
        List<String> heard = new ArrayList<>();
        RetrievalReadings.Recording records = new RetrievalReadings.Recording("retrieval", true, heard);

        Path tree = draw(base, records, "retrieval");

        claim("the tree holds the marker, retrieval's file and its drawing.txt, and nothing else",
                () -> assertThat(filesUnder(tree)).containsExactly(
                        "retrieval/drawing.txt", "retrieval/retrieval.jsonl", "vespera-deliverable.txt"));
        claim("the plugin's drawing.txt says drawn", () -> assertThat(Files.readString(tree.resolve("retrieval/drawing.txt")))
                .isEqualTo("drawn\n"));
        claim("the plugin was handed the records all the same, the tree not being drawn",
                () -> assertThat(records.taken()).hasSize(2 + 1 + 22));
        claim("and no working-directory report of the cluster pages' sizes was written",
                () -> assertThat(base.resolve("work").resolve(Deliverable.CLUSTER_PAGE_SIZES_FILE_NAME)).doesNotExist());
    }

    @Test
    @Story("No tree is drawn")
    @DisplayName("A drawing with no tree keeps the files an earlier drawing with the tree left")
    void anEarlierDrawingsFilesAreKept(@TempDir Path base) throws IOException {
        RetrievalReadings.Recording records = new RetrievalReadings.Recording("retrieval", true, new ArrayList<>());
        Path first = draw(base, records, "tree, retrieval");
        List<String> withTheTree = filesUnder(first);
        String indexBefore = Files.readString(first.resolve(Deliverable.INDEX_FILE_NAME));

        Path second = draw(base, new RetrievalReadings.Recording("retrieval", true, new ArrayList<>()), "retrieval");

        claim("the tree is the same folder, and every file the first drawing wrote stands, the index as it was",
                () -> {
                    assertThat(second).isEqualTo(first);
                    assertThat(filesUnder(second)).containsExactlyElementsOf(withTheTree);
                    assertThat(Files.readString(second.resolve(Deliverable.INDEX_FILE_NAME))).isEqualTo(indexBefore);
                });
        claim("the first drawing did hold what the second left out: the index, the manifest and a topic's folder",
                () -> assertThat(withTheTree)
                        .contains(Deliverable.INDEX_FILE_NAME, Deliverable.MANIFEST_FILE_NAME)
                        .anyMatch(name -> name.contains("alpha")));
    }

    private static Path draw(Path base, Plugin plugin, String outputs) {
        return Deliverable.writeTo(
                base.resolve("work"),
                DeliverableRoot.UNNAMED,
                ThreeTopics.provenance(base.resolve("archive").toString()),
                RetrievalReadings.source(),
                ListedCodeSets.NONE,
                SurvivorPictures.none(),
                new ClusterPageCeiling.Unset(),
                List.of(plugin),
                Outputs.read(outputs),
                RetrievalReadings.inputs(new ArrayList<>()),
                DeliverableProgress.NONE);
    }

    private static List<String> filesUnder(Path tree) throws IOException {
        try (Stream<Path> walk = Files.walk(tree)) {
            return walk.filter(Files::isRegularFile)
                    .map(file -> tree.relativize(file).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
    }
}
