package io.algernon.vespera.synthesis;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An arrangement of three seed partitions and eleven survivors, as lists: what the tests of ADR-223 hand the
 * tree's writer, and what {@link TheTreeWrittenFromLists} was captured from.
 *
 * <p>Chosen so that every case the record names is in it. The survivors' occurrence ids run 10 to 20 and
 * alternate between the partitions, so occurrence order cuts across them. Seed 2's partition holds five and
 * comes first; seeds 1 and 3 hold three each and are placed by their seeds' paths. Two survivors of one cluster
 * score alike in each partition. Cluster 0 of seed 2 is written from two of its three survivors, neither the
 * highest-scoring, in an order that is not occurrence order. Of the four clusters with no writing, one carries
 * a fault, nothing could be sent for one, one was not reached, and one has no reason given.
 *
 * <p>It names no type ADR-223 adds, so it compiles against the tree the expected bytes were captured from.
 */
final class ThreePartitions {

    static final OccurrenceId ALPHA = new OccurrenceId(1);

    static final OccurrenceId BETA = new OccurrenceId(2);

    static final OccurrenceId GAMMA = new OccurrenceId(3);

    static final String ALPHA_PATH = "seeds/alpha.docx";

    static final String BETA_PATH = "seeds/Beta & Co [2019].pdf";

    static final String GAMMA_PATH = "seeds/gamma.docx";

    /** What stands in the captured files where the corpus root was, which differs on every machine. */
    static final String CORPUS_ROOT_TOKEN = "{CORPUS_ROOT}";

    private ThreePartitions() {}

    static DeliverableProvenance provenance(String corpusRoot) {
        return new DeliverableProvenance(
                "f00d".repeat(16), 7L, corpusRoot, List.of(new NamedValue("seedFolder", "seeds"), new NamedValue("relevanceFloor", "")));
    }

    /** The six clusters, in stored order: partition order, then cluster order. */
    static List<RecordedCluster> arrangement() {
        return List.of(
                cluster(BETA, 0, 3, 1, 1, "Fire doors | inspection"),
                cluster(BETA, 1, 2, 1, 2, "Smoke dampers"),
                cluster(ALPHA, 0, 2, 2, 1, "Stairwells"),
                cluster(ALPHA, 2, 1, 2, 2, "A note on [risers]"),
                cluster(GAMMA, 1, 2, 3, 1, "Sprinklers"),
                cluster(GAMMA, 0, 1, 3, 2, "Hose reels"));
    }

    /** The eleven survivors, in occurrence order. */
    static List<ListedSurvivor> survivors() {
        return List.of(
                survivor(10, "reports/doors a.docx", BETA, BETA_PATH, 0, 0.5),
                survivor(11, "reports/stairs, north.pdf", ALPHA, ALPHA_PATH, 0, 0.7),
                survivor(12, "reports/sprinklers-1.pdf", GAMMA, GAMMA_PATH, 1, 0.4),
                survivor(13, "reports/doors [final].docx", BETA, BETA_PATH, 0, 0.9),
                survivor(14, "reports/dampers.pdf", BETA, BETA_PATH, 1, 0.6),
                survivor(15, "notes/risers.txt", ALPHA, ALPHA_PATH, 2, 0.8),
                survivor(16, "notes/hose reels.txt", GAMMA, GAMMA_PATH, 0, 0.3),
                survivor(17, "reports/doors b.docx", BETA, BETA_PATH, 0, 0.5),
                survivor(18, "reports/stairs, south.pdf", ALPHA, ALPHA_PATH, 0, 0.7),
                survivor(19, "reports/sprinklers-2.pdf", GAMMA, GAMMA_PATH, 1, 0.4),
                survivor(20, "reports/dampers \"old\".pdf", BETA, BETA_PATH, 1, 0.65));
    }

    /** The two synthesis docs, in the order they were written. */
    static List<RecordedSynthesisDoc> written() {
        return List.of(
                new RecordedSynthesisDoc(
                        BETA,
                        0,
                        new SynthesisDoc(
                                "Fire doors & their inspection",
                                "The doors were inspected twice [1]. The second inspection found a fault [2][1].",
                                List.of(new OccurrenceId(17), new OccurrenceId(10)))),
                new RecordedSynthesisDoc(
                        ALPHA,
                        2,
                        new SynthesisDoc(
                                "Risers", "[1] One note covers the risers.", List.of(new OccurrenceId(15)))));
    }

    /** Why three of the four clusters without writing have none; the fourth is given no reason. */
    static Map<ClusterSlot, Unwritten> unwritten() {
        Map<ClusterSlot, Unwritten> why = new LinkedHashMap<>();
        why.put(new ClusterSlot(BETA, 1), Unwritten.of(ClusterFaultKind.SCHEMA_VIOLATION));
        why.put(new ClusterSlot(ALPHA, 0), Unwritten.NO_SENDABLE_DOCUMENT);
        why.put(new ClusterSlot(GAMMA, 1), Unwritten.NOT_REACHED);
        return why;
    }

    private static RecordedCluster cluster(
            OccurrenceId seed, int ordinal, int documentCount, int partitionOrder, int clusterOrder, String label) {
        return new RecordedCluster(
                new ArrangedCluster(seed, ordinal, documentCount, partitionOrder, clusterOrder), new ClusterLabel(label));
    }

    private static ListedSurvivor survivor(
            long occurrence, String path, OccurrenceId seed, String seedPath, int clusterOrdinal, double score) {
        return new ListedSurvivor(
                new OccurrenceId(occurrence),
                new OccurrencePath(path),
                "key" + occurrence,
                seed,
                seedPath,
                clusterOrdinal,
                score);
    }
}
