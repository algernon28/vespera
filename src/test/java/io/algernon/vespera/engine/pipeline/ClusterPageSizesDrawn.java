package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.engine.deliverable.ListedTree.cluster;
import static io.algernon.vespera.engine.deliverable.ListedTree.slotOf;
import static io.algernon.vespera.engine.deliverable.ListedTree.survivor;
import static io.algernon.vespera.engine.deliverable.ListedTree.writing;

import io.algernon.vespera.engine.deliverable.ClusterPageCeiling;
import io.algernon.vespera.engine.deliverable.Deliverable;
import io.algernon.vespera.engine.deliverable.DeliverableProgress;
import io.algernon.vespera.engine.deliverable.DeliverableRoot;
import io.algernon.vespera.engine.deliverable.ListedCodeSets;
import io.algernon.vespera.engine.deliverable.ListedSurvivor;
import io.algernon.vespera.engine.deliverable.ListedTree;
import io.algernon.vespera.engine.deliverable.SurvivorPictures;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.synthesis.ClusterSlot;
import io.algernon.vespera.engine.synthesis.RecordedCluster;
import io.algernon.vespera.engine.synthesis.SynthesisDoc;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A tree drawn through {@code deliverable}'s public way in, so that the jar writes {@code cluster-page-sizes.html} and
 * {@code cluster-page-sizes.json} into {@code <base>/work}: one group of long-pathed documents whose page passes a
 * ceiling that splits it, and small groups of one to a few documents. Shared by the tests that read what the jar
 * writes for that report (ADR-362 §9, §10).
 */
public final class ClusterPageSizesDrawn {

    private static final OccurrenceId THE_SEED = new OccurrenceId(1);

    /** Documents of the first group: with paths of near 800 characters, their page passes {@link #A_CEILING}. */
    private static final int LONG_DOCUMENTS = 40;

    /** Groups of one to this many documents each. */
    private static final int SMALL_GROUPS = 3;

    /** A ceiling that splits the first group's page and no other's. */
    public static final long A_CEILING = 12_000;

    private ClusterPageSizesDrawn() {
    }

    /** Draws the tree under {@code base}; the working directory the report lies in is {@code base/work}. */
    public static Path under(Path base) {
        List<RecordedCluster> arrangement = new ArrayList<>();
        List<ListedSurvivor> survivors = new ArrayList<>();
        long[] next = {1_000};
        RecordedCluster largest = cluster(THE_SEED, 0, "Hydrant valves", 1, LONG_DOCUMENTS);
        arrangement.add(largest);
        survivors.addAll(members(0, LONG_DOCUMENTS, true, next));
        for (int small = 1; small <= SMALL_GROUPS; small++) {
            arrangement.add(cluster(THE_SEED, small, "Alarm panel " + small, 1 + small, small));
            survivors.addAll(members(small, small, false, next));
        }
        Map<ClusterSlot, SynthesisDoc> written =
                Map.of(slotOf(largest), writing("Inspection of hydrant valves", "Valves [1] and seals [2].", 1_000, 1_001));
        Deliverable.writeTo(base.resolve("work"), DeliverableRoot.UNNAMED, ListedTree.provenance(base.resolve("archive")),
                ListedTree.of(arrangement, written, survivors, Map.of()), ListedCodeSets.NONE, SurvivorPictures.none(),
                new ClusterPageCeiling.Bytes(A_CEILING), DeliverableProgress.NONE);
        return base.resolve("work");
    }

    private static List<ListedSurvivor> members(int ordinal, int count, boolean longPaths, long[] next) {
        List<ListedSurvivor> members = new ArrayList<>();
        for (int at = 0; at < count; at++) {
            long occurrence = next[0]++;
            String path = (longPaths ? "r/".repeat(395) : "reports/") + occurrence + ".docx";
            members.add(survivor(occurrence, path, "0".repeat(64), THE_SEED, "seeds/Fire Safety.docx", ordinal, 0.9 - at * 1e-4));
        }
        return members;
    }
}
