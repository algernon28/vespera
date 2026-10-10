package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
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
 * What stage 5f keeps of one seed partition for its page (ADR-223 section 2): five numbers taken from the
 * partition's cluster sizes while they are in hand, and not the sizes.
 *
 * <p>The page has one row a partition and always showed these five. Until ADR-223 each row carried the size
 * of every cluster of its partition until the page was rendered, so the step held the size of every cluster
 * of the run. {@code ClusterSizeReportTest} goes on holding what the page says of them; this holds that the
 * numbers are the ones the sizes gave, the middle one included, which is the upper of the two middle sizes
 * where there is an even number.
 */
@Epic("Relevance")
@Feature("Clustering")
@Issue("472")
@Link(name = "ADR-223", url = Adr.THE_LAST_THREE_STAGES_GO_THROUGH_ONE_SEED_PARTITION_AT_A_TIME, type = "adr")
@Link(name = "ADR-087", url = Adr.CLUSTERS_ARE_MODULARITY_COMMUNITIES, type = "adr")
class ClusterSizesPageKeepsFiveNumbersAPartitionTest {

    /** Six groups, out of size order as the database hands them: 40, 1, 12, 1, 3 and 1 documents. */
    private static final List<Integer> SIX_SIZES = List.of(40, 1, 12, 1, 3, 1);

    private static final int DOCUMENTS = 58;

    private static final int GROUPS = 6;

    private static final int LARGEST = 40;

    /** Of 1, 1, 1, 3, 12 and 40 the two middle sizes are 1 and 3, and the page has always shown the upper. */
    private static final int MIDDLE = 3;

    private static final int GROUPS_OF_ONE = 3;

    @Test
    @Story("The sizes page keeps five numbers for each exemplar")
    @DisplayName("An exemplar's row keeps how many documents and groups it has, its largest and middle group and its groups of one")
    void keepsTheFiveNumbersOfItsSizes() {
        ClusterSizeReport.Partition row = ClusterSizeReport.Partition.of("seeds/contracts.pdf", SIX_SIZES, Optional.empty());

        claim(
                "the six sizes sum to " + DOCUMENTS + " documents in " + GROUPS + " groups",
                () -> {
                    assertThat(row.documentCount()).isEqualTo(DOCUMENTS);
                    assertThat(row.clusterCount()).isEqualTo(GROUPS);
                });
        claim(
                "the largest group holds " + LARGEST + ", the middle one " + MIDDLE + ", the upper of the two"
                        + " middle sizes, and " + GROUPS_OF_ONE + " groups hold one document",
                () -> {
                    assertThat(row.largest()).isEqualTo(LARGEST);
                    assertThat(row.median()).isEqualTo(MIDDLE);
                    assertThat(row.singletons()).isEqualTo(GROUPS_OF_ONE);
                });
    }

    @Test
    @Story("The sizes page keeps five numbers for each exemplar")
    @DisplayName("An exemplar with no group keeps five zeroes")
    void anExemplarWithNoGroupKeepsZeroes() {
        ClusterSizeReport.Partition row = ClusterSizeReport.Partition.of("seeds/empty.pdf", List.of(), Optional.empty());

        claim(
                "with no size to take them from, every one of the five numbers is zero, as the page showed",
                () -> assertThat(List.of(
                                row.documentCount(), row.clusterCount(), row.largest(), row.median(), row.singletons()))
                        .containsOnly(0));
    }

    @Test
    @Story("The sizes page keeps five numbers for each exemplar")
    @DisplayName("The page's row shows those five numbers, in the order of its columns")
    void thePageShowsTheFiveNumbers() {
        String page = ClusterSizeReport.render(
                List.of(ClusterSizeReport.Partition.of("seeds/contracts.pdf", SIX_SIZES, Optional.empty())));

        claim(
                "the row reads the exemplar, then " + DOCUMENTS + ", " + GROUPS + ", " + LARGEST + ", " + MIDDLE
                        + " and " + GROUPS_OF_ONE + ", cell after cell, as it did when the row carried every size",
                () -> assertThat(page)
                        .contains(ReportPage.textCell("seeds/contracts.pdf")
                                + ReportPage.numberCell(DOCUMENTS)
                                + ReportPage.numberCell(GROUPS)
                                + ReportPage.numberCell(LARGEST)
                                + ReportPage.numberCell(MIDDLE)
                                + ReportPage.numberCell(GROUPS_OF_ONE)));
    }
}
