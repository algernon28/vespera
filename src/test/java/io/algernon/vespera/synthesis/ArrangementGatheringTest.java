package io.algernon.vespera.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Gathering the documents the previous stage grouped into the two levels the arrangement orders
 * (ADR-105, ADR-110, #175) — and refusing to gather them at all where one of them carries no score.
 *
 * <p><b>The arrangement covers every document or it is not an arrangement.</b> A document that was
 * grouped without being scored cannot be placed: the order of the groups is computed from their
 * members' scores, so one member short of a score makes its group's place a guess. The alternative —
 * a miscellaneous bucket for whatever could not be placed — would render that guess as a section of
 * the finished deliverable, where nobody reading it could tell it apart from a real one.
 *
 * <p>It is a broken invariant rather than a case to handle, which is why it stops here rather than
 * being recorded: nothing upstream can produce it, and if something ever does, the place to find out
 * is the run that produced it and not the archive six months later.
 */
@Epic("Arrangement")
@Feature("Gathering the documents into groups")
@Issue("175")
@Link(name = "ADR-105", url = Adr.STAGE_6A_NAMES_THE_ARRANGEMENT_STAGE_5_BUILT, type = "adr")
@Link(name = "ADR-110", url = Adr.PIPELINE_HANDS_SYNTHESIS_ITS_INPUTS, type = "adr")
class ArrangementGatheringTest {

    /** The seed both documents below were matched to. */
    private static final long SEED = 1L;

    /** How that seed is named, which is the only handle anyone has on the order of the top level. */
    private static final String SEED_PATH = "safety.docx";

    /** The group both documents below were put in. */
    private static final int ORDINAL = 3;

    /** A score, as close to its seed as any document in these fixtures gets. */
    private static final double SCORE = 0.8;

    /** The document deliberately left without a score, so that the refusal has something to name. */
    private static final long UNSCORED_DOCUMENT = 42L;

    @Test
    @Story("The documents that were grouped are gathered into the two levels that get ordered")
    @DisplayName("Documents sharing a seed and a group are gathered into one group under one seed")
    void gathersDocumentsSharingASeedAndAGroup() {
        List<Partition> partitions = Arrangement.partitionsOf(List.of(
                clustered(10L, ORDINAL, SCORE), clustered(11L, ORDINAL, SCORE)));

        claim(
                "the two documents arrive as one group under one seed, because what gets ordered is"
                        + " groups and seeds rather than documents -- and a group split in two here would"
                        + " be two entries in the finished work where the archive holds one",
                () -> assertThat(partitions)
                        .singleElement()
                        .satisfies(partition -> {
                            assertThat(partition.seed()).isEqualTo(new OccurrenceId(SEED));
                            assertThat(partition.seedPath()).isEqualTo(SEED_PATH);
                            assertThat(partition.clusters()).singleElement().satisfies(cluster -> {
                                assertThat(cluster.ordinal()).isEqualTo(ORDINAL);
                                assertThat(cluster.documentCount()).isEqualTo(2);
                            });
                        }));
    }

    @Test
    @Story("A document that was grouped but never scored stops the arrangement")
    @DisplayName("A grouped document carrying no score stops the arrangement and is named")
    void stopsWhereAGroupedDocumentCarriesNoScore() {
        List<ClusteredDocument> documents =
                List.of(clustered(10L, ORDINAL, SCORE), clustered(UNSCORED_DOCUMENT, ORDINAL, null));

        claim(
                "it stops, and it names the document that could not be placed -- so the answer to what"
                        + " went wrong is in the message rather than in a query somebody has to think of"
                        + " writing",
                () -> assertThatThrownBy(() -> Arrangement.partitionsOf(documents))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining(String.valueOf(UNSCORED_DOCUMENT)));
    }

    private static ClusteredDocument clustered(long occurrenceId, int ordinal, Double score) {
        return new ClusteredDocument(
                new OccurrenceId(occurrenceId), new OccurrenceId(SEED), SEED_PATH, ordinal, score);
    }
}
