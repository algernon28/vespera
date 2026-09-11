package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.embedding.RetainedEdgeSpread;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The page showing how each seed partition broke into clusters (ADR-087).
 *
 * <p>The cluster count is not chosen, so the only way to know what shape a page tree will have is to
 * look — and the number deciding whether that tree is worth building is how many of its pages would
 * hold a single document. The claims here are about that number reaching the page, and about the page
 * reporting an awkward outcome rather than repairing it.
 */
@Epic("Relevance")
@Feature("Clustering")
@Issue("109")
@Link(name = "ADR-087", url = Adr.CLUSTERS_ARE_MODULARITY_COMMUNITIES, type = "adr")
class ClusterSizeReportTest {

    /** One partition of 55 documents in five clusters: 40, 12, and three documents each alone. */
    private static final ClusterSizeReport.Partition ONE_LARGE_GROUP_AND_THREE_ALONE =
            new ClusterSizeReport.Partition("seeds/contracts.pdf", List.of(40, 12, 1, 1, 1), Optional.empty());


    /** A partition whose kept links run from 0.62 up to 0.95 — documents that genuinely resemble each other. */
    private static final ClusterSizeReport.Partition GROUPED_BY_RESEMBLANCE = new ClusterSizeReport.Partition(
            "seeds/contracts.pdf",
            List.of(18, 9),
            Optional.of(new RetainedEdgeSpread(0.62, 0.81, 0.95, 240)));

    /** A partition whose strongest link is 0.04 — forty documents k forced together. */
    private static final ClusterSizeReport.Partition GROUPED_BY_K = new ClusterSizeReport.Partition(
            "seeds/minutes.pdf", List.of(21, 19), Optional.of(new RetainedEdgeSpread(0.01, 0.02, 0.04, 240)));

    /** One document under its own exemplar: no pair, so no link and nothing to measure. */
    private static final ClusterSizeReport.Partition ONE_DOCUMENT_ALONE =
            new ClusterSizeReport.Partition("seeds/lonely.pdf", List.of(1), Optional.empty());

    @Test
    @Story("The spread of cluster sizes is reported per partition")
    @DisplayName("A partition reports its documents, its clusters and how their sizes are spread")
    void reportsOnePartitionsSpread() {
        String html = ClusterSizeReport.render(List.of(ONE_LARGE_GROUP_AND_THREE_ALONE));

        claim(
                "the partition names the exemplar whose documents it holds, so a reader can tell which"
                        + " heading of the eventual page tree these groups would sit under",
                () -> assertThat(html).contains("seeds/contracts.pdf"));
        claim(
                "and reports the 55 documents it grouped, so the sizes beside them read as a share of"
                        + " something rather than as bare counts",
                () -> assertThat(html).contains(">55<"));
        claim(
                "the 5 clusters they formed, which nobody supplied: the count is a property of how the"
                        + " documents resemble each other, and the whole reason a page like this exists",
                () -> assertThat(html).contains(">5<"));
        claim(
                "the largest at 40, so a reader can see one group swallowing most of a partition",
                () -> assertThat(html).contains(">40<"));
        claim(
                "and the 3 groups holding one document each, which is the number that decides whether a"
                        + " page tree built over this partition is worth building",
                () -> assertThat(html).contains(">3<"));
    }


    @Test
    @Story("The retained-edge spread is reported beside the sizes")
    @DisplayName("A partition reports how alike the documents on its kept links actually were")
    void reportsTheRetainedEdgeSpread() {
        String html = ClusterSizeReport.render(List.of(GROUPED_BY_RESEMBLANCE));

        claim(
                "the weakest link in the partition is shown, which is the number that says whether the"
                        + " grouping had to reach for documents that resemble nothing",
                () -> assertThat(html).contains(">0.62<"));
        claim(
                "so is the middle one, so a reader sees where the bulk of the links sit rather than"
                        + " only the two ends",
                () -> assertThat(html).contains(">0.81<"));
        claim(
                "and the strongest, because links running from 0.95 down to 0.62 is a different"
                        + " partition from links all sitting at 0.62",
                () -> assertThat(html).contains(">0.95<"));
    }

    @Test
    @Story("The retained-edge spread is reported beside the sizes")
    @DisplayName("The page says what a low spread means, in documents rather than in statistics")
    void saysWhatALowSpreadMeans() {
        String html = ClusterSizeReport.render(List.of(GROUPED_BY_K));

        claim(
                "the page explains a low spread as what it is about the documents -- that they were put"
                        + " together because every document is joined to its nearest few whether or not"
                        + " they are close, and not because they resemble each other",
                () -> assertThat(html).contains("whether or not"));
        claim(
                "and says what such a group will read as to a person opening it, which is the thing the"
                        + " number is for",
                () -> assertThat(html).containsIgnoringCase("unrelated"));
        claim(
                "without naming a threshold: no number here is a cut, and a page that suggested one"
                        + " would be the unmeasured floor ADR-087 refused",
                () -> assertThat(html).doesNotContainIgnoringCase("threshold"));
    }

    @Test
    @Story("The retained-edge spread is reported beside the sizes")
    @DisplayName("A partition of one has no links, and says so rather than showing a resemblance of zero")
    void aPartitionWithNoLinksSaysSo() {
        String html = ClusterSizeReport.render(List.of(ONE_DOCUMENT_ALONE));

        claim(
                "a partition of one document reports no resemblance at all: there is no pair of"
                        + " documents for a number to describe, and 0.00 would read as a document"
                        + " resembling nothing when what is true is that it has nothing to resemble",
                () -> assertThat(html).doesNotContain(">0.00<"));
        claim(
                "and the row is still there, holding the one document it holds",
                () -> assertThat(html).contains("seeds/lonely.pdf"));
    }

    @Test
    @Story("The spread of cluster sizes is reported per partition")
    @DisplayName("Each partition is reported on its own, never folded in with another exemplar's")
    void reportsEachPartitionSeparately() {
        String html = ClusterSizeReport.render(List.of(
                ONE_LARGE_GROUP_AND_THREE_ALONE,
                new ClusterSizeReport.Partition("seeds/invoices.pdf", List.of(3, 2), Optional.empty())));

        claim(
                "both exemplars appear, each with its own row: clustering runs within a partition and"
                        + " never across two, so a total over both would describe a grouping that was"
                        + " never computed",
                () -> assertThat(html).contains("seeds/contracts.pdf").contains("seeds/invoices.pdf"));
    }

    @Test
    @Story("All singletons is an answer, not a failure")
    @DisplayName("A partition of nothing but one-document clusters is reported, not repaired")
    void reportsAPartitionOfSingletonsWithoutRepairingIt() {
        String html = ClusterSizeReport.render(
                List.of(new ClusterSizeReport.Partition("seeds/exemplar.pdf", List.of(1, 1, 1, 1), Optional.empty())));

        claim(
                "the page says outright that nothing was removed, merged or renamed: merging small"
                        + " communities into a catch-all would be two unmeasured thresholds and a page no"
                        + " document belongs to",
                () -> assertThat(html).contains("removed, merged or renamed"));
        claim(
                "and it tells the reader in its own words that a document alone in its group is a real"
                        + " answer about the exemplar rather than a mistake to be fixed -- reporting it is"
                        + " the response to that outcome, and there is no other",
                () -> assertThat(html).contains("real answer"));
        claim(
                "all four groups are still there to be counted, unmerged",
                () -> assertThat(html).contains(">4<"));
    }

    @Test
    @Story("The spread of cluster sizes is reported per partition")
    @DisplayName("The middle cluster is reported rather than an average")
    void reportsTheMiddleClusterRatherThanAnAverage() {
        ClusterSizeReport.Partition oneLargeGroupAndFourAlone =
                new ClusterSizeReport.Partition("seeds/exemplar.pdf", List.of(100, 1, 1, 1, 1), Optional.empty());

        claim(
                "the middle group is 1 rather than the mean of 20.8: one group holding most of a"
                        + " partition pulls an average upwards and leaves a reader with the impression of"
                        + " evenly-sized pages that are not there",
                () -> assertThat(oneLargeGroupAndFourAlone.median()).isEqualTo(1));
        claim(
                "and the largest is reported beside it, so both ends of the spread are visible rather"
                        + " than one number standing in for the shape",
                () -> assertThat(oneLargeGroupAndFourAlone.largest()).isEqualTo(100));
    }

    @Test
    @Story("The spread of cluster sizes is reported per partition")
    @DisplayName("A run that grouped nothing writes a page saying so")
    void reportsThatNothingWasGrouped() {
        String html = ClusterSizeReport.render(List.of());

        claim(
                "the page says no document was matched to an exemplar, rather than showing an empty table"
                        + " a reader would have to interpret",
                () -> assertThat(html).contains("nothing to group"));
    }
}
