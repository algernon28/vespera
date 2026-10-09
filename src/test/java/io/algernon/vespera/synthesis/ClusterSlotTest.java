package io.algernon.vespera.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The one key a cluster is named by in {@code synthesis}: its winning seed and its ordinal in that
 * seed's partition (ADR-213 §3). Each value that carries a cluster's identity is read into the key in
 * the key's own class, so nothing pairs a seed with an ordinal by hand.
 */
@Epic("Synthesis")
@Feature("The key a cluster is named by")
@Issue("351")
@Link(name = "ADR-213", url = Adr.EACH_RULE_THE_DELIVERABLE_IS_WRITTEN_BY_HAS_ONE_CLASS, type = "adr")
@Link(name = "ADR-174", url = Adr.A_PAGE_NOTHING_WAS_WRITTEN_OVER_SAYS_WHY, type = "adr")
class ClusterSlotTest {

    private static final OccurrenceId THE_SEED = new OccurrenceId(1);

    private static final OccurrenceId ANOTHER_SEED = new OccurrenceId(2);

    private static final int THE_ORDINAL = 4;

    private static final int ANOTHER_ORDINAL = 5;

    @Test
    @Story("One key names a cluster")
    @DisplayName("A cluster, its writing, its fault and each of its members all give the same key")
    void everyValueOfOneClusterGivesOneKey() {
        ClusterSlot expected = new ClusterSlot(THE_SEED, THE_ORDINAL);

        claim(
                "the arranged cluster, the synthesis doc written over it, the fault recorded against it and a"
                        + " survivor in it each give the key of seed 1, ordinal 4, so a map keyed by one is read"
                        + " by any of the others",
                () -> assertThat(List.of(
                                ClusterSlot.of(arranged(THE_SEED, THE_ORDINAL)),
                                ClusterSlot.of(written(THE_SEED, THE_ORDINAL)),
                                ClusterSlot.of(faulted(THE_SEED, THE_ORDINAL)),
                                ClusterSlot.of(member(THE_SEED, THE_ORDINAL))))
                        .containsOnly(expected));
    }

    @Test
    @Story("One key names a cluster")
    @DisplayName("Two clusters differing in seed or in ordinal give different keys")
    void tellsTwoClustersApart() {
        claim(
                "the same ordinal under another seed is another cluster, since an ordinal counts within its"
                        + " partition",
                () -> assertThat(ClusterSlot.of(written(ANOTHER_SEED, THE_ORDINAL)))
                        .isNotEqualTo(ClusterSlot.of(written(THE_SEED, THE_ORDINAL))));
        claim(
                "another ordinal under the same seed is another cluster",
                () -> assertThat(ClusterSlot.of(member(THE_SEED, ANOTHER_ORDINAL)))
                        .isNotEqualTo(ClusterSlot.of(member(THE_SEED, THE_ORDINAL))));
    }

    @Test
    @Story("One key names a cluster")
    @DisplayName("A cluster's key is its identity, not its place, so 6a re-ordering it does not change the key")
    void ignoresWhereTheClusterIsPlaced() {
        RecordedCluster placedFirst = new RecordedCluster(
                new ArrangedCluster(THE_SEED, THE_ORDINAL, 3, 1, 1), new ClusterLabel("A label"));
        RecordedCluster placedLast = new RecordedCluster(
                new ArrangedCluster(THE_SEED, THE_ORDINAL, 3, 2, 9), new ClusterLabel("Another label"));

        claim(
                "order is a judgement 6a makes and identity never moves (ADR-112), so the key reads neither the"
                        + " partition order, the cluster order, the document count nor the label",
                () -> assertThat(ClusterSlot.of(placedFirst)).isEqualTo(ClusterSlot.of(placedLast)));
    }

    private static RecordedCluster arranged(OccurrenceId seed, int ordinal) {
        return new RecordedCluster(new ArrangedCluster(seed, ordinal, 2, 1, 1), new ClusterLabel("Sprinklers"));
    }

    private static RecordedSynthesisDoc written(OccurrenceId seed, int ordinal) {
        return new RecordedSynthesisDoc(seed, ordinal, new SynthesisDoc("Title", "Prose [1].", List.of()));
    }

    private static RecordedClusterFault faulted(OccurrenceId seed, int ordinal) {
        return new RecordedClusterFault(
                seed, ordinal, new ClusterFault(ClusterFaultKind.SCHEMA_VIOLATION, "not the shape asked for"));
    }

    private static ListedSurvivor member(OccurrenceId seed, int ordinal) {
        return new ListedSurvivor(
                new OccurrenceId(10), new OccurrencePath("reports/a.pdf"), "h10", seed, "seeds/s.pdf", ordinal, 0.5);
    }
}
