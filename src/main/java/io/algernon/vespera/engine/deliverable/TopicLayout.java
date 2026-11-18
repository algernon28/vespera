package io.algernon.vespera.engine.deliverable;

import io.algernon.vespera.engine.synthesis.ClusterSlot;
import io.algernon.vespera.engine.synthesis.RecordedCluster;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Where one topic's folders lie in the HTML tree (ADR-415 section 1, section 3): its own, a folder for each of its
 * subtopics where it is crowded, and a folder for each of its clusters, in its subtopic's where it has subtopics and in
 * its own where it has none. Held for one topic at a time (ADR-223 section 9).
 *
 * <p>The clusters are the topic's in one sequence, each carrying the place the sequence gives it ({@link
 * Deliverable#inOneSequence}), which names its folder and which its row and the overview's citation show.
 */
public final class TopicLayout {

    private final ListedTopic topic;
    private final int topicCount;
    private final List<ListedSubtopic> subtopics;
    private final List<RecordedCluster> clusters;
    private final Map<Integer, ListedSubtopic> subtopicByNumber = new HashMap<>();
    private final Map<ClusterSlot, RecordedCluster> clusterBySlot = new HashMap<>();
    private final Map<Integer, RecordedCluster> clusterByPlace = new HashMap<>();

    /**
     * @param topicCount how many topics the tree has, which pads the topic's folder name
     * @param subtopics the topic's subtopics in their order, none where it has none
     * @param clusters the topic's clusters in one sequence, each carrying its place in it
     */
    public TopicLayout(ListedTopic topic, int topicCount, List<ListedSubtopic> subtopics, List<RecordedCluster> clusters) {
        this.topic = topic;
        this.topicCount = topicCount;
        this.subtopics = List.copyOf(subtopics);
        this.clusters = List.copyOf(clusters);
        this.subtopics.forEach(subtopic -> subtopicByNumber.put(subtopic.subtopic(), subtopic));
        this.clusters.forEach(recorded -> {
            clusterBySlot.put(ClusterSlot.of(recorded), recorded);
            clusterByPlace.put(recorded.cluster().clusterOrder(), recorded);
        });
    }

    ListedTopic topic() {
        return topic;
    }

    List<ListedSubtopic> subtopics() {
        return subtopics;
    }

    List<RecordedCluster> clusters() {
        return clusters;
    }

    /** Whether the topic is divided into subtopics, each holding its clusters. */
    boolean divided() {
        return !subtopics.isEmpty();
    }

    /** The topic's folder in the tree. */
    String topicFolder() {
        return IndexPage.topicDirectoryName(topic, topicCount);
    }

    /** The folder of {@code subtopic} in its topic's, named from its order and its name. */
    String subtopicFolder(ListedSubtopic subtopic) {
        return IndexPage.subtopicDirectoryName(subtopic, subtopics.size());
    }

    /** The subtopic {@code recorded} lies in, empty where its topic has none. */
    Optional<ListedSubtopic> subtopicOf(RecordedCluster recorded) {
        if (recorded.cluster().subtopic().isEmpty()) {
            return Optional.empty();
        }
        int number = recorded.cluster().subtopic().getAsInt();
        ListedSubtopic subtopic = subtopicByNumber.get(number);
        if (subtopic == null) {
            throw new IllegalStateException("cluster " + recorded.cluster().ordinal() + " of topic "
                    + topic.topic().seedPath() + " chunk " + topic.topic().chunkIndex() + " lies in subtopic " + number
                    + ", which the topic's subtopics do not include");
        }
        return Optional.of(subtopic);
    }

    /** The name of {@code recorded}'s folder, in its subtopic's or, where its topic has none, in its topic's. */
    String ownFolder(RecordedCluster recorded) {
        return IndexPage.clusterFolderName(recorded, IndexPage.widthOf(topic.clusterCount()));
    }

    /** The folder of {@code recorded} within its topic's folder: in its subtopic's where it has one. */
    String clusterFolderInTopic(RecordedCluster recorded) {
        String own = ownFolder(recorded);
        return subtopicOf(recorded).map(subtopic -> subtopicFolder(subtopic) + "/" + own).orElse(own);
    }

    /** The folder of {@code recorded} from the tree's root. */
    String clusterFolder(RecordedCluster recorded) {
        return topicFolder() + "/" + clusterFolderInTopic(recorded);
    }

    /** The cluster this topic holds at {@code slot}, empty where it holds none. */
    Optional<RecordedCluster> clusterAt(ClusterSlot slot) {
        return Optional.ofNullable(clusterBySlot.get(slot));
    }

    /** The cluster at {@code place} in the topic's sequence, counted from 1, empty where there is none. */
    Optional<RecordedCluster> clusterAtPlace(int place) {
        return Optional.ofNullable(clusterByPlace.get(place));
    }
}
