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
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which document leads a cluster, and the label derived from it, as {@code synthesis} decides them over
 * its own {@link ClusteredDocument} (ADR-106, ADR-190).
 *
 * <p><b>The rule moved and did not change.</b> Until ADR-190 it was {@code ArrangementTasklet.leadDocumentOf},
 * over {@code embedding}'s membership rows and a map of scores, and it was asked twice per cluster: once
 * to label it and once to draw the page. Every claim below is about something that rule did and this one
 * must go on doing — which member of which cluster leads, what a tie does, what a member without a score
 * counts as — each built so that a rule doing otherwise names a different document.
 *
 * <p><b>The title is asked for once, and only of the lead</b>: reading it hashes the lead's file, so a
 * rule that asked of every member, or asked twice, would read the archive more than it did.
 */
@Epic("Arrangement")
@Feature("Arranging the documents")
@Issue("408")
@Link(name = "ADR-190", url = Adr.STAGE_6_RULES_LIVE_IN_SYNTHESIS, type = "adr")
@Link(name = "ADR-106", url = Adr.A_CLUSTER_GETS_A_DERIVED_LABEL_AND_A_GENERATED_TITLE, type = "adr")
class LeadDocumentTest {

    private static final OccurrenceId SEED = new OccurrenceId(100);
    private static final OccurrenceId ANOTHER_SEED = new OccurrenceId(200);

    /** The cluster every test asks about: ordinal 1 of {@link #SEED}'s partition. */
    private static final int ORDINAL = 1;

    /** Another cluster of the same partition, holding a document that scores higher than any in ours. */
    private static final int ANOTHER_ORDINAL = 2;

    private static final OccurrenceId FIRST = new OccurrenceId(1);
    private static final OccurrenceId SECOND = new OccurrenceId(2);
    private static final OccurrenceId IN_ANOTHER_CLUSTER = new OccurrenceId(3);
    private static final OccurrenceId UNDER_ANOTHER_SEED = new OccurrenceId(4);

    private static final ArrangedCluster THE_CLUSTER = new ArrangedCluster(SEED, ORDINAL, 2, 1, 1);

    @Test
    @Story("A group is named after the document that leads it")
    @DisplayName("A group is led by its own highest-scoring document, not by a higher one in a neighbouring group")
    void isLedByItsOwnHighestScoringDocument() {
        List<ClusteredDocument> documents = List.of(
                member(FIRST, SEED, ORDINAL, 0.5),
                member(IN_ANOTHER_CLUSTER, SEED, ANOTHER_ORDINAL, 0.9),
                member(SECOND, SEED, ORDINAL, 0.7),
                member(UNDER_ANOTHER_SEED, ANOTHER_SEED, ORDINAL, 0.95));

        claim(
                "the group is led by the better of its own two documents, though a document of another group"
                        + " under the same exemplar, and one of the group at the same place under another"
                        + " exemplar, each score higher",
                () -> assertThat(LeadDocument.of(THE_CLUSTER, documents)).isEqualTo(SECOND));
    }

    private static ClusteredDocument member(OccurrenceId occurrence, OccurrenceId seed, int ordinal, Double score) {
        return new ClusteredDocument(occurrence, seed, "seeds/" + seed.value() + ".docx", ordinal, score);
    }
}
