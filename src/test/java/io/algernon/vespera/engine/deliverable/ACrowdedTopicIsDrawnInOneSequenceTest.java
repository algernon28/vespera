package io.algernon.vespera.engine.deliverable;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.OccurrencePath;
import io.algernon.vespera.engine.synthesis.ArrangedCluster;
import io.algernon.vespera.engine.synthesis.ClusterLabel;
import io.algernon.vespera.engine.synthesis.ClusterSlot;
import io.algernon.vespera.engine.synthesis.ListedDoc;
import io.algernon.vespera.engine.synthesis.RecordedCluster;
import io.algernon.vespera.engine.synthesis.SynthesisDoc;
import io.algernon.vespera.engine.synthesis.TopicKey;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A crowded topic's clusters are numbered in one sequence (ADR-409 §10), each in the folder of its subtopic (ADR-415
 * §3): subtopic by subtopic, each numbered by its place in that sequence and padded over the topic's clusters, its
 * index row linking to that folder through its subtopic's, and its survivors' {@code documents.csv} rows naming that
 * place in both {@code cluster} and {@code cluster_order}, and their subtopic and its order in {@code subtopic} and
 * {@code subtopic_order} (ADR-415 §6).
 *
 * <p>One topic of two subtopics, {@value #CLUSTERS_IN_EACH_SUBTOPIC} clusters each, so that the topic holds ten and
 * each subtopic fewer than ten: a number padded over one subtopic's clusters is one digit, and over the topic's two.
 * The two subtopics' clusters are alike in all the arrangement stores within a subtopic, ordinal, place and label, so
 * that whatever drew them from those alone would draw two clusters the same. {@link ListedArrangement} names each
 * subtopic {@code Subtopic n} after its order.
 */
@Epic("The deliverable")
@Feature("The tree the operator is handed")
@Issue("832")
@Issue("833")
@Link(name = "ADR-409", url = Adr.TOPICS_AND_ALL_ARRANGED_UNDER_THEM_ARE_KEPT_IN_TABLES_KEYED_BY_THE_TOPIC, type = "adr")
@Link(name = "ADR-415", url = Adr.A_TOPIC_IS_A_PAGE_CARRYING_ITS_OVERVIEW_AND_ITS_SUBTOPICS_ARE_PAGES_BENEATH_IT, type = "adr")
@Link(name = "ADR-112", url = Adr.THE_ARRANGEMENT_IS_ORDERED_BY_SIZE_AND_MEAN_SCORE, type = "adr")
class ACrowdedTopicIsDrawnInOneSequenceTest {

    /** The one topic, by its key. */
    private static final TopicKey THE_TOPIC = new TopicKey("seeds/pumping.docx", 4);

    /** Its recorded name, which names its folder. */
    private static final String THE_TOPIC_NAME = "Pumping Stations";

    /** Its folder: the only topic, so one digit, then its name as a slug. */
    private static final String THE_TOPIC_FOLDER = "1-pumping-stations";

    /** The subtopic the arrangement puts first, by its ordinal: not 0, so the order and the ordinal are told apart. */
    private static final int FIRST_SUBTOPIC = 1;

    /** The subtopic it puts second. */
    private static final int SECOND_SUBTOPIC = 0;

    /** Each subtopic's folder, by its order: its order, then its name, {@code Subtopic n}, as a slug. */
    private static final List<String> SUBTOPIC_FOLDERS = List.of("1-subtopic-1", "2-subtopic-2");

    /** The labels each subtopic's clusters carry, in the order stored within it: the same in both. */
    private static final List<String> LABELS =
            List.of("Seal Wear", "Impeller Damage", "Motor Overheating", "Valve Leaks", "Control Faults");

    /** Those labels as slugs, in the same order. */
    private static final List<String> SLUGS =
            List.of("seal-wear", "impeller-damage", "motor-overheating", "valve-leaks", "control-faults");

    /** How many clusters each subtopic holds. */
    private static final int CLUSTERS_IN_EACH_SUBTOPIC = LABELS.size();

    /** The seed every survivor scored highest against, which no claim here reads. */
    private static final OccurrenceId THE_SEED = new OccurrenceId(1);

    /** The first survivor's occurrence; each cluster holds one, numbered on from it in the sequence's order. */
    private static final long FIRST_SURVIVOR = 101L;

    @Test
    @Story("A crowded topic's groups are numbered in one sequence, each in its subtopic's folder")
    @DisplayName("The groups of a topic's two subtopics are numbered in one sequence over the topic, each in its"
            + " subtopic's folder, and the index and the listing tell them apart")
    void theClustersOfTwoSubtopicsAreNumberedInOneSequenceAndToldApart(@TempDir Path workingDirectory)
            throws IOException {
        List<RecordedCluster> arrangement = new ArrayList<>();
        List<ListedDoc> written = new ArrayList<>();
        List<ListedSurvivor> survivors = new ArrayList<>();
        Map<String, Integer> placeOfSurvivor = new LinkedHashMap<>();
        Map<String, String> subtopicOfSurvivor = new LinkedHashMap<>();
        Map<String, String> subtopicOrderOfSurvivor = new LinkedHashMap<>();
        List<List<String>> folders = List.of(new ArrayList<>(), new ArrayList<>());
        int place = 0;
        for (int subtopic : List.of(FIRST_SUBTOPIC, SECOND_SUBTOPIC)) {
            int subtopicOrder = subtopic == FIRST_SUBTOPIC ? 1 : 2;
            for (int ordinal = 0; ordinal < CLUSTERS_IN_EACH_SUBTOPIC; ordinal++) {
                place++;
                long occurrence = FIRST_SURVIVOR + place - 1;
                String path = "reports/subtopic-" + subtopic + "/cluster-" + ordinal + ".pdf";
                arrangement.add(new RecordedCluster(
                        new ArrangedCluster(
                                THE_TOPIC, OptionalInt.of(subtopic), ordinal, 1, 1, subtopicOrder, ordinal + 1),
                        new ClusterLabel(LABELS.get(ordinal))));
                written.add(new ListedDoc(
                        new ClusterSlot(THE_TOPIC, OptionalInt.of(subtopic), ordinal),
                        new SynthesisDoc(
                                "Written over " + path, "It [1] says so.", List.of(new OccurrenceId(occurrence)))));
                survivors.add(new ListedSurvivor(
                        new OccurrenceId(occurrence),
                        new OccurrencePath(path),
                        "hash" + occurrence,
                        Optional.of(THE_SEED),
                        THE_TOPIC,
                        THE_TOPIC_NAME,
                        OptionalInt.of(subtopic),
                        ordinal,
                        1.0 - place / 100.0));
                placeOfSurvivor.put(path, place);
                subtopicOfSurvivor.put(path, "Subtopic " + subtopicOrder);
                subtopicOrderOfSurvivor.put(path, String.valueOf(subtopicOrder));
                folders.get(subtopicOrder - 1).add(String.format("%02d-%s", place, SLUGS.get(ordinal)));
            }
        }

        Path tree = ListedArrangement.writeTo(
                workingDirectory,
                ListedTree.provenance(workingDirectory.resolve("an-archive-that-is-not-there")),
                arrangement,
                written,
                survivors);
        String index = Files.readString(tree.resolve(Deliverable.INDEX_FILE_NAME));
        Map<String, String> clusterColumn = ListedTree.columnByPath(tree, "cluster");
        Map<String, String> clusterOrderColumn = ListedTree.columnByPath(tree, "cluster_order");
        Map<String, String> topicColumn = ListedTree.columnByPath(tree, "topic");
        Map<String, String> subtopicColumn = ListedTree.columnByPath(tree, "subtopic");
        Map<String, String> subtopicOrderColumn = ListedTree.columnByPath(tree, "subtopic_order");
        List<String> linksInSequence = new ArrayList<>();
        for (int at = 0; at < SUBTOPIC_FOLDERS.size(); at++) {
            for (String folder : folders.get(at)) {
                linksInSequence.add("<a href=\"" + THE_TOPIC_FOLDER + "/" + SUBTOPIC_FOLDERS.get(at) + "/" + folder + "/"
                        + Deliverable.CLUSTER_PAGE_NAME + "\">");
            }
        }

        claim("the topic's folder holds a folder for each of its two subtopics, in their order, and no group's folder",
                () -> assertThat(namesUnder(tree.resolve(THE_TOPIC_FOLDER))).containsExactlyElementsOf(SUBTOPIC_FOLDERS));
        claim("the first subtopic's folder holds its five groups' folders, numbered 01 to 05, each number padded to two"
                        + " digits, the width of the topic's ten",
                () -> assertThat(namesUnder(tree.resolve(THE_TOPIC_FOLDER).resolve(SUBTOPIC_FOLDERS.get(0))))
                        .containsExactlyElementsOf(folders.get(0)));
        claim("and the second's holds its five, numbered on from 06 to 10 in the topic's one sequence, so that two groups"
                        + " alike in label and stored place get two numbers",
                () -> assertThat(namesUnder(tree.resolve(THE_TOPIC_FOLDER).resolve(SUBTOPIC_FOLDERS.get(1))))
                        .containsExactlyElementsOf(folders.get(1)));
        claim("the index lists a row for each of the ten, in that sequence, each linking to its own folder through its"
                        + " subtopic's",
                () -> assertThat(index).containsSubsequence(linksInSequence));
        claim("each survivor's row of the listing names the subtopic its group lies in, and that subtopic's order",
                () -> {
                    assertThat(subtopicColumn).containsExactlyInAnyOrderEntriesOf(subtopicOfSurvivor);
                    assertThat(subtopicOrderColumn).containsExactlyInAnyOrderEntriesOf(subtopicOrderOfSurvivor);
                });
        claim("each survivor's row of the listing names its group's place in that sequence, the number its folder"
                        + " carries, as cluster_order",
                () -> assertThat(clusterOrderColumn).containsExactlyInAnyOrderEntriesOf(asText(placeOfSurvivor)));
        claim("and as cluster, so that a consumer tells the ten groups of the one topic apart by the topic and the"
                        + " group, as it tells apart the groups of a topic with no subtopics",
                () -> assertThat(clusterColumn).containsExactlyInAnyOrderEntriesOf(asText(placeOfSurvivor)));
        claim("no two groups' survivors share a topic and a group in the listing",
                () -> assertThat(new HashSet<>(survivors.stream()
                                .map(survivor -> topicColumn.get(survivor.path().value()) + " "
                                        + clusterColumn.get(survivor.path().value()))
                                .toList()))
                        .hasSize(survivors.size()));
    }

    /** Each value of {@code places} written as the listing writes a number. */
    private static Map<String, String> asText(Map<String, Integer> places) {
        Map<String, String> text = new LinkedHashMap<>();
        places.forEach((path, place) -> text.put(path, String.valueOf(place)));
        return text;
    }

    /** The names of the folders directly beneath {@code directory}, in name order. */
    private static List<String> namesUnder(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.filter(Files::isDirectory)
                    .map(entry -> entry.getFileName().toString())
                    .sorted()
                    .toList();
        }
    }
}
