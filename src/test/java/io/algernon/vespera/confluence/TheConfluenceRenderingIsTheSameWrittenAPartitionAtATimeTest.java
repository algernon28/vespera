package io.algernon.vespera.confluence;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.deliverable.ArrangedSurvivors;
import io.algernon.vespera.deliverable.ClusterPageCeiling;
import io.algernon.vespera.deliverable.Deliverable;
import io.algernon.vespera.deliverable.DeliverableProgress;
import io.algernon.vespera.deliverable.DeliverableRoot;
import io.algernon.vespera.deliverable.ListedArrangement;
import io.algernon.vespera.deliverable.ListedCodeSets;
import io.algernon.vespera.deliverable.Plugin;
import io.algernon.vespera.deliverable.SurvivorPictures;
import io.algernon.vespera.deliverable.ThreePartitions;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Confluence rendering stage 6b draws one seed partition and one page of survivors at a time (ADR-223 sections 6
 * and 7) does not depend on where the pages fall: every file under {@code confluence/} is byte for byte the one drawn
 * with every survivor handed over at once.
 *
 * <p>This claim stood in {@code deliverable.TreeIsTheSameWrittenAPartitionAtATimeTest} while {@code deliverable} drew
 * the rendering itself. It draws none now, since it names no plugin (ADR-333 §1), so the claim is made here, over the
 * same {@link ThreePartitions} lists, through the public way in that is handed the plugins, with {@link
 * ConfluencePlugin} the one plugin. Its publisher is never asked to send.
 *
 * <p>Pure: no database. The archive is not there, so every copy is {@code gone} in both drawings.
 */
@Epic("The deliverable")
@Feature("The Confluence rendering")
@Issue("472")
@Link(name = "ADR-223", url = Adr.THE_LAST_THREE_STAGES_GO_THROUGH_ONE_SEED_PARTITION_AT_A_TIME, type = "adr")
@Link(name = "ADR-301", url = Adr.A_CONFLUENCE_RENDERING_IS_DRAWN_BESIDE_THE_HTML_TREE, type = "adr")
@Link(name = "ADR-312", url = Adr.THE_CONFLUENCE_RENDERING_LIES_UNDER_CONFLUENCE, type = "adr")
@Link(name = "ADR-333", url = Adr.A_PLUGIN_DRAWS_ONE_RENDERING_AND_DELIVERS_IT_TO_ONE_DESTINATION, type = "adr")
class TheConfluenceRenderingIsTheSameWrittenAPartitionAtATimeTest {

    /** How many survivors the source hands over at once: fewer than any partition here holds. */
    private static final int TWO_SURVIVORS_IN_A_PAGE = 2;

    /**
     * The files of the rendering: the root page, the page list, a page for each of the three seed partitions and the
     * six groups, and the state of the drawing (ADR-333 §11).
     */
    private static final int TWELVE_FILES = 12;

    private static final String FOLDER = "confluence/";

    @Test
    @Story("The tree is the same tree, written one exemplar at a time")
    @DisplayName("Every file of the Confluence rendering of three exemplars is byte for byte the file drawn with every document in one page")
    void everyFileOfTheRenderingIsTheSame(@TempDir Path base) throws IOException {
        String corpusRoot = base.resolve("archive").toString();

        Map<String, String> paged = renderingOf(drawn(
                base.resolve("paged"),
                corpusRoot,
                ListedArrangement.of(
                        ThreePartitions.arrangement(),
                        ThreePartitions.written(),
                        ThreePartitions.survivors(),
                        ThreePartitions.unwritten(),
                        TWO_SURVIVORS_IN_A_PAGE)));
        Map<String, String> whole = renderingOf(drawn(
                base.resolve("whole"),
                corpusRoot,
                ListedArrangement.of(
                        ThreePartitions.arrangement(),
                        ThreePartitions.written(),
                        ThreePartitions.survivors(),
                        ThreePartitions.unwritten())));

        claim(
                "the rendering holds the same " + TWELVE_FILES + " files and nothing else: the root page, the page"
                        + " list, a page for each of the three seed partitions and the six groups, and drawing.txt,"
                        + " with no file left half written beside any of them",
                () -> {
                    assertThat(whole)
                            .hasSize(TWELVE_FILES)
                            .containsKeys(
                                    FOLDER + "page.xml",
                                    FOLDER + Publisher.PAGE_LIST_FILE_NAME,
                                    FOLDER + Plugin.DRAWING_STATE_FILE_NAME);
                    assertThat(whole.keySet()).noneMatch(name -> name.endsWith(Deliverable.PART_SUFFIX));
                    assertThat(paged.keySet()).containsExactlyElementsOf(whole.keySet());
                });
        claim(
                "both drawings finished: drawing.txt says drawn in each, the word ADR-333 §11 has a finished drawing"
                        + " write",
                () -> {
                    assertThat(paged.get(FOLDER + Plugin.DRAWING_STATE_FILE_NAME).strip()).isEqualTo("drawn");
                    assertThat(whole.get(FOLDER + Plugin.DRAWING_STATE_FILE_NAME).strip()).isEqualTo("drawn");
                });
        for (Map.Entry<String, String> file : whole.entrySet()) {
            claim(
                    file.getKey() + " is byte for byte what it was",
                    () -> assertThat(paged.get(file.getKey()))
                            .as("the contents of %s", file.getKey())
                            .isEqualTo(file.getValue()));
        }
    }

    /** The tree of {@code source} drawn for {@code work}, the Confluence plugin the one plugin. */
    private static Path drawn(Path work, String corpusRoot, ArrangedSurvivors source) {
        return Deliverable.writeTo(
                work,
                DeliverableRoot.UNNAMED,
                ThreePartitions.provenance(corpusRoot),
                source,
                ListedCodeSets.NONE,
                SurvivorPictures.none(),
                new ClusterPageCeiling.Unset(),
                List.of(new ConfluencePlugin(new Publisher("", "", "", ""))),
                DeliverableProgress.NONE);
    }

    /** Every file beneath {@code tree}'s {@code confluence/}, by its path relative to the tree with {@code /} between. */
    private static Map<String, String> renderingOf(Path tree) throws IOException {
        Map<String, String> files = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(tree.resolve(Publisher.RENDERING_FOLDER))) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                files.put(
                        tree.relativize(file).toString().replace('\\', '/'),
                        Files.readString(file, StandardCharsets.UTF_8));
            }
        }
        return files;
    }
}
