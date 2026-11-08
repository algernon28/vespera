package io.algernon.vespera.engine.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.OccurrencePath;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What stage 6a calls a cluster (ADR-106): derived from the cluster's own members, before anything
 * has been generated, so the name points at a document a reviewer can open and disagree with.
 *
 * <p>The three tiers are one chain and all three are the common path, not an error case. A plain
 * {@code .txt} file and a scan that OCR'd into undifferentiated body text carry no title item at
 * all, so the chain is what a corpus like this one actually exercises.
 *
 * <p><b>What must be shown not to happen matters here.</b> Two clusters led by identically-titled
 * documents keep both labels: identity is the ordinal, and a suffix appended to tell them apart
 * would hide a fact about the corpus that the reviewer is the one who should see it.
 */
@Epic("Arrangement")
@Feature("Naming a cluster")
@Issue("175")
@Link(name = "ADR-106", url = Adr.A_CLUSTER_GETS_A_DERIVED_LABEL_AND_A_GENERATED_TITLE, type = "adr")
class ClusterLabelTest {

    /**
     * A lead document whose whole filename is an extension, which leaves no stem to fall back to.
     * The third tier exists for this and is reached by nothing else.
     */
    private static final OccurrencePath NAMED_ONLY_BY_ITS_EXTENSION = new OccurrencePath("exports/.pdf");

    /** Any ordinal at all: what these cases are about is which tier answers, not which cluster asked. */
    private static final int ORDINAL = 3;

    @Test
    @Story("A cluster is named after the document that leads it")
    @DisplayName("A lead document named only by its extension falls back to the cluster's number")
    void fallsBackToTheOrdinalWhenTheFilenameSaysNothing() {
        ClusterLabel label = ClusterLabel.derivedFrom(null, NAMED_ONLY_BY_ITS_EXTENSION, ORDINAL);

        claim(
                "a file whose whole name is its extension leaves nothing to call the group after, so the"
                        + " group is called by its number -- the last tier of the chain, and one a reader"
                        + " can still tell apart from every other group in the same partition",
                () -> assertThat(label.value()).isEqualTo("Cluster 3"));
    }
}
