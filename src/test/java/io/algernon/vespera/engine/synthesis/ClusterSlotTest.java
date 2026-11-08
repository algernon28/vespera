package io.algernon.vespera.engine.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
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

    private static final int THE_ORDINAL = 4;

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
}
