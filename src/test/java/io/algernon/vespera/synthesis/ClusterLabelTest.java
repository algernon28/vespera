package io.algernon.vespera.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrencePath;
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

    /** The path of the document leading the cluster in every case below. */
    private static final OccurrencePath LEAD_DOCUMENT =
            new OccurrencePath("audits/2019/site-safety-audit.pdf");

    /**
     * A lead document whose whole filename is an extension, which leaves no stem to fall back to.
     * The third tier exists for this and is reached by nothing else.
     */
    private static final OccurrencePath NAMED_ONLY_BY_ITS_EXTENSION = new OccurrencePath("exports/.pdf");

    /** Any ordinal at all: what these cases are about is which tier answers, not which cluster asked. */
    private static final int ORDINAL = 3;

    /** A second ordinal, so two clusters in one partition can be compared. */
    private static final int ANOTHER_ORDINAL = 8;

    /** One title, handed to two clusters, so the question is what the second one does about it. */
    private static final String SHARED_TITLE = "Annual Report";

    @Test
    @Story("A cluster is named after the document that leads it")
    @DisplayName("A cluster is named after the title of its highest-scoring document")
    void takesTheTitleOfTheHighestScoringDocument() {
        ClusterLabel label = ClusterLabel.derivedFrom("2019 Site Safety Audit", LEAD_DOCUMENT, ORDINAL);

        claim(
                "the name comes from the lead document's own title, so a reviewer can open that"
                        + " document and judge whether the grouping deserves the name",
                () -> assertThat(label.value()).isEqualTo("2019 Site Safety Audit"));
    }

    @Test
    @Story("A cluster is named after the document that leads it")
    @DisplayName("A lead document with no title is named by its own filename instead")
    void fallsBackToTheLeadDocumentsFilenameStem() {
        ClusterLabel label = ClusterLabel.derivedFrom(null, LEAD_DOCUMENT, ORDINAL);

        claim(
                "a lead document carrying no title falls back to what its file is called, without its"
                        + " folders and without its extension -- the ordinary case for a plain text file"
                        + " and for a scan that read as undifferentiated body text, not an error",
                () -> assertThat(label.value()).isEqualTo("site-safety-audit"));
    }

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

    @Test
    @Story("A cluster is named after the document that leads it")
    @DisplayName("Two groups led by documents with the same title keep the same name")
    void doesNotDisambiguateTwoClustersSharingALeadTitle() {
        ClusterLabel first = ClusterLabel.derivedFrom(SHARED_TITLE, LEAD_DOCUMENT, ORDINAL);
        ClusterLabel second = ClusterLabel.derivedFrom(SHARED_TITLE, LEAD_DOCUMENT, ANOTHER_ORDINAL);

        claim(
                "neither name gains a number to tell it from the other: two groups led by documents"
                        + " carrying the same title is a fact about the archive that the person reviewing"
                        + " it should see, and the two are told apart by their number, never by their name",
                () -> assertThat(first.value()).isEqualTo(second.value()));
    }
}
