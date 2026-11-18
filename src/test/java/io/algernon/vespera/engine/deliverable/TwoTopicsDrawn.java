package io.algernon.vespera.engine.deliverable;

import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.OccurrencePath;
import io.algernon.vespera.engine.synthesis.ArrangedCluster;
import io.algernon.vespera.engine.synthesis.ClusterLabel;
import io.algernon.vespera.engine.synthesis.ClusterSlot;
import io.algernon.vespera.engine.synthesis.DeliverableLanguage;
import io.algernon.vespera.engine.synthesis.OverviewUnwritten;
import io.algernon.vespera.engine.synthesis.RecordedCluster;
import io.algernon.vespera.engine.synthesis.SynthesisDoc;
import io.algernon.vespera.engine.synthesis.TopicKey;
import io.algernon.vespera.engine.synthesis.Unwritten;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;

/**
 * Two topics, drawn alike by the HTML tree, the Confluence rendering and the website rendering, so that each
 * rendering's tests read one arrangement (ADR-415).
 *
 * <p><b>The crowded topic, Pumping Stations</b>, placed first, has two subtopics: <em>Seals and Impellers</em>,
 * placed first though its number within the topic is 1, holding the clusters <em>Seal Wear</em> and <em>Impeller
 * Damage</em>; and <em>Motors</em>, number 0, holding <em>Motor Overheating</em>. In the topic's one sequence the
 * three are 1, 2 and 3. Its overview was written over all three, citing each.
 *
 * <p><b>The undivided topic, Sluice Gates</b>, placed second, has the clusters <em>Gate Hinges</em>, written, and
 * <em>Gate Seals</em>, nothing written over it for want of a document that could be sent. Its overview was written over
 * the one written cluster, so the topic's page discloses that it read one of its two.
 *
 * <p><b>One see-also link</b>: the survivor of Seal Wear also fits Sluice Gates, so Sluice Gates' page links it.
 *
 * <p>The archive is not there: every copy is {@code gone}, which no claim of the renderings' shape reads. Every text is
 * synthetic.
 */
public final class TwoTopicsDrawn {

    /** How many topics are drawn, which pads a topic's place to one digit. */
    public static final int TOPIC_COUNT = 2;

    public static final TopicKey PUMPING = new TopicKey("seeds/pumping.docx", 4);

    public static final String PUMPING_NAME = "Pumping Stations";

    public static final String PUMPING_FOLDER = "1-pumping-stations";

    public static final TopicKey SLUICES = new TopicKey("seeds/sluices.docx", 0);

    public static final String SLUICES_NAME = "Sluice Gates";

    public static final String SLUICES_FOLDER = "2-sluice-gates";

    /** The subtopic placed first, by its number within the topic: not 0, so its number and its order are told apart. */
    public static final int SEALS = 1;

    public static final String SEALS_NAME = "Seals and Impellers";

    public static final String SEALS_FOLDER = PUMPING_FOLDER + "/1-seals-and-impellers";

    /** The subtopic placed second. */
    public static final int MOTORS = 0;

    public static final String MOTORS_NAME = "Motors";

    public static final String MOTORS_FOLDER = PUMPING_FOLDER + "/2-motors";

    /** The crowded topic's three clusters, at 1, 2 and 3 of its one sequence, each folder in its subtopic's. */
    public static final RecordedCluster SEAL_WEAR = cluster(PUMPING, OptionalInt.of(SEALS), 0, 1, 1, 1, "Seal Wear");

    public static final String SEAL_WEAR_FOLDER = SEALS_FOLDER + "/1-seal-wear";

    public static final String SEAL_WEAR_TITLE = "Seals wear out";

    public static final RecordedCluster IMPELLER = cluster(PUMPING, OptionalInt.of(SEALS), 1, 1, 1, 2, "Impeller Damage");

    public static final String IMPELLER_FOLDER = SEALS_FOLDER + "/2-impeller-damage";

    public static final String IMPELLER_TITLE = "Impellers crack";

    public static final RecordedCluster MOTOR = cluster(PUMPING, OptionalInt.of(MOTORS), 0, 1, 2, 1, "Motor Overheating");

    /** The second subtopic's one cluster: 3 in the topic's sequence, though it is the first of its subtopic. */
    public static final String MOTOR_FOLDER = MOTORS_FOLDER + "/3-motor-overheating";

    public static final String MOTOR_TITLE = "Motors run hot";

    /** The undivided topic's two clusters, directly in its folder. */
    public static final RecordedCluster HINGES = cluster(SLUICES, OptionalInt.empty(), 0, 2, 0, 1, "Gate Hinges");

    public static final String HINGES_FOLDER = SLUICES_FOLDER + "/1-gate-hinges";

    public static final String HINGES_TITLE = "Hinges rust";

    public static final RecordedCluster GATE_SEALS = cluster(SLUICES, OptionalInt.empty(), 1, 2, 0, 2, "Gate Seals");

    public static final String GATE_SEALS_FOLDER = SLUICES_FOLDER + "/2-gate-seals";

    /** The crowded topic's overview, citing its three clusters by their places. */
    public static final String PUMPING_OVERVIEW = "Seals and impellers wear together [1][2]. Motors run hot [3].";

    /** The undivided topic's overview, written over its one written cluster of two. */
    public static final String SLUICES_OVERVIEW = "Hinges rust in winter [1].";

    /** The survivor of Seal Wear that also fits Sluice Gates, by its path. */
    public static final String THE_LINKED_PATH = "reports/seal and gate.pdf";

    public static final OccurrenceId THE_SEED = new OccurrenceId(1);

    private TwoTopicsDrawn() {}

    /** The arrangement, in the order the topics and their clusters are stored. */
    public static List<RecordedCluster> arrangement() {
        return List.of(SEAL_WEAR, IMPELLER, MOTOR, HINGES, GATE_SEALS);
    }

    /** One survivor a cluster, Seal Wear's being the one Sluice Gates links. */
    public static List<ListedSurvivor> survivors() {
        return List.of(
                survivor(101, THE_LINKED_PATH, PUMPING, PUMPING_NAME, OptionalInt.of(SEALS), 0),
                survivor(102, "reports/impeller.pdf", PUMPING, PUMPING_NAME, OptionalInt.of(SEALS), 1),
                survivor(103, "reports/motor.pdf", PUMPING, PUMPING_NAME, OptionalInt.of(MOTORS), 0),
                survivor(104, "reports/hinges.pdf", SLUICES, SLUICES_NAME, OptionalInt.empty(), 0),
                survivor(105, "reports/gate seals.pdf", SLUICES, SLUICES_NAME, OptionalInt.empty(), 1));
    }

    /** The writing over every cluster but Gate Seals. */
    public static Map<ClusterSlot, SynthesisDoc> written() {
        Map<ClusterSlot, SynthesisDoc> written = new LinkedHashMap<>();
        written.put(ClusterSlot.of(SEAL_WEAR), ListedTree.writing(SEAL_WEAR_TITLE, "Seals wear [1].", 101));
        written.put(ClusterSlot.of(IMPELLER), ListedTree.writing(IMPELLER_TITLE, "Impellers crack [1].", 102));
        written.put(ClusterSlot.of(MOTOR), ListedTree.writing(MOTOR_TITLE, "Motors run hot [1].", 103));
        written.put(ClusterSlot.of(HINGES), ListedTree.writing(HINGES_TITLE, "Hinges rust [1].", 104));
        return written;
    }

    /** The two overviews as recorded: the crowded topic's over its three clusters, the other's over one of two. */
    public static Map<TopicKey, ListedOverview> overviews() {
        return Map.of(
                PUMPING, new ListedOverview.Written(PUMPING_OVERVIEW, 3),
                SLUICES, new ListedOverview.Written(SLUICES_OVERVIEW, 1));
    }

    /** The source the renderings are drawn from, in {@code language}, with these {@code overviews}. */
    public static ArrangedSurvivors source(DeliverableLanguage language, Map<TopicKey, ListedOverview> overviews) {
        List<RecordedCluster> arrangement = arrangement();
        List<ListedSurvivor> survivors = survivors();
        Map<ClusterSlot, SynthesisDoc> written = written();
        Map<ClusterSlot, ArrangedCluster> placeByCluster = new LinkedHashMap<>();
        arrangement.forEach(recorded -> placeByCluster.put(ClusterSlot.of(recorded), recorded.cluster()));
        Consumer<Consumer<List<ListedSurvivor>>> everySurvivorInOnePage = page -> page.accept(survivors);
        return ArrangedSurvivors.reading(
                () -> ListedTree.topicsOf(arrangement, survivors),
                survivors::size,
                topic -> arrangement.stream().filter(recorded -> recorded.cluster().topic().equals(topic.topic())).toList(),
                topic -> survivors.stream().filter(survivor -> survivor.topic().equals(topic.topic())).toList(),
                cluster -> Optional.ofNullable(written.get(cluster)),
                cluster -> cluster.equals(ClusterSlot.of(GATE_SEALS))
                        ? Optional.of(Unwritten.NO_SENDABLE_DOCUMENT)
                        : Optional.empty(),
                clusters -> {
                    Map<ClusterSlot, ArrangedCluster> places = new LinkedHashMap<>();
                    clusters.forEach(cluster -> {
                        if (placeByCluster.containsKey(cluster)) {
                            places.put(cluster, placeByCluster.get(cluster));
                        }
                    });
                    return places;
                },
                topic -> topic.topic().equals(PUMPING)
                        ? List.of(new ListedSubtopic(SEALS, SEALS_NAME, 1), new ListedSubtopic(MOTORS, MOTORS_NAME, 2))
                        : List.of(),
                topic -> overviews.getOrDefault(
                        topic.topic(), new ListedOverview.Unwritten(OverviewUnwritten.NOT_REACHED)),
                (topic, page) -> {
                    if (topic.topic().equals(SLUICES)) {
                        page.accept(List.of(new ListedSeeAlso(new OccurrencePath(THE_LINKED_PATH), ClusterSlot.of(SEAL_WEAR))));
                    }
                },
                everySurvivorInOnePage,
                everySurvivorInOnePage,
                true,
                language);
    }

    /** The two topics drawn in {@code workingDirectory} in English, {@code plugins} drawing beside the HTML tree. */
    public static Path drawn(Path workingDirectory, List<Plugin> plugins) {
        return drawn(workingDirectory, plugins, overviews());
    }

    /** As {@link #drawn(Path, List)}, each topic's overview the one {@code overviews} records for it. */
    public static Path drawn(Path workingDirectory, List<Plugin> plugins, Map<TopicKey, ListedOverview> overviews) {
        return Deliverable.writeTo(
                workingDirectory,
                DeliverableRoot.UNNAMED,
                ListedTree.provenance(workingDirectory.resolve("an-archive-that-is-not-there")),
                source(DeliverableLanguage.ENGLISH, overviews),
                ListedCodeSets.NONE,
                SurvivorPictures.none(),
                new ClusterPageCeiling.Unset(),
                plugins,
                DeliverableProgress.NONE);
    }

    private static RecordedCluster cluster(
            TopicKey topic, OptionalInt subtopic, int ordinal, int topicOrder, int subtopicOrder, int clusterOrder,
            String label) {
        return new RecordedCluster(
                new ArrangedCluster(topic, subtopic, ordinal, 1, topicOrder, subtopicOrder, clusterOrder),
                new ClusterLabel(label));
    }

    private static ListedSurvivor survivor(
            long occurrence, String path, TopicKey topic, String topicName, OptionalInt subtopic, int ordinal) {
        return new ListedSurvivor(
                new OccurrenceId(occurrence),
                new OccurrencePath(path),
                "hash" + occurrence,
                Optional.of(THE_SEED),
                topic,
                topicName,
                subtopic,
                ordinal,
                1.0 - occurrence / 1000.0);
    }
}
