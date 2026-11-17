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
import java.util.OptionalInt;
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
 * counts as — each built so that a rule doing otherwise names a different document. A cluster is named by its
 * topic, its subtopic and its ordinal since ADR-409.
 *
 * <p><b>The title is asked for once, and only of the lead</b>: reading it hashes the lead's file, so a
 * rule that asked of every member, or asked twice, would read the archive more than it did.
 */
@Epic("Arrangement")
@Feature("Arranging the documents")
@Issue("408")
@Link(name = "ADR-190", url = Adr.STAGE_6_RULES_LIVE_IN_SYNTHESIS, type = "adr")
@Link(name = "ADR-106", url = Adr.A_CLUSTER_GETS_A_DERIVED_LABEL_AND_A_GENERATED_TITLE, type = "adr")
@Link(name = "ADR-409", url = Adr.TOPICS_AND_ALL_ARRANGED_UNDER_THEM_ARE_KEPT_IN_TABLES_KEYED_BY_THE_TOPIC, type = "adr")
class LeadDocumentTest {

    private static final TopicKey TOPIC = new TopicKey("seeds/100.docx", 0);
    private static final TopicKey ANOTHER_TOPIC = new TopicKey("seeds/200.docx", 0);

    /** The subtopic every test's cluster lies in. */
    private static final OptionalInt SUBTOPIC = OptionalInt.of(0);

    /** Another subtopic of the same topic, whose cluster of the same number holds a document scoring higher. */
    private static final OptionalInt ANOTHER_SUBTOPIC = OptionalInt.of(1);

    /** The cluster every test asks about: ordinal 1 of {@link #TOPIC}'s subtopic 0. */
    private static final int ORDINAL = 1;

    /** Another cluster of the same subtopic, holding a document that scores higher than any in ours. */
    private static final int ANOTHER_ORDINAL = 2;

    private static final OccurrenceId FIRST = new OccurrenceId(1);
    private static final OccurrenceId SECOND = new OccurrenceId(2);
    private static final OccurrenceId IN_ANOTHER_CLUSTER = new OccurrenceId(3);
    private static final OccurrenceId UNDER_ANOTHER_TOPIC = new OccurrenceId(4);
    private static final OccurrenceId IN_ANOTHER_SUBTOPIC = new OccurrenceId(5);

    private static final ArrangedCluster THE_CLUSTER = new ArrangedCluster(TOPIC, SUBTOPIC, ORDINAL, 2, 1, 1, 1);

    @Test
    @Story("A group is named after the document that leads it")
    @DisplayName("A group is led by its own highest-scoring document, not by a higher one in a neighbouring group")
    void isLedByItsOwnHighestScoringDocument() {
        List<ClusteredDocument> documents = List.of(
                member(FIRST, TOPIC, SUBTOPIC, ORDINAL, 0.5),
                member(IN_ANOTHER_CLUSTER, TOPIC, SUBTOPIC, ANOTHER_ORDINAL, 0.9),
                member(SECOND, TOPIC, SUBTOPIC, ORDINAL, 0.7),
                member(UNDER_ANOTHER_TOPIC, ANOTHER_TOPIC, SUBTOPIC, ORDINAL, 0.95),
                member(IN_ANOTHER_SUBTOPIC, TOPIC, ANOTHER_SUBTOPIC, ORDINAL, 0.97));

        claim(
                "the group is led by the better of its own two documents, though a document of another group"
                        + " under the same topic, one of the group at the same place under another topic, and one"
                        + " of the group at the same place in another part of the same topic, each score higher",
                () -> assertThat(LeadDocument.of(THE_CLUSTER, documents)).isEqualTo(SECOND));
    }

    private static ClusteredDocument member(
            OccurrenceId occurrence, TopicKey topic, OptionalInt subtopic, int ordinal, Double score) {
        return new ClusteredDocument(occurrence, topic, subtopic, ordinal, score);
    }
}
