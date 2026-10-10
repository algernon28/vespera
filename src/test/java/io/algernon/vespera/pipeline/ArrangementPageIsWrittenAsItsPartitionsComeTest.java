package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Stage 6a's page written as its seed partitions come (ADR-223 section 3): what stands before the first
 * partition, each partition's heading, counts and table as that partition is handed over, and what stands
 * after the last. It is byte for byte the page that was rendered whole from a list of every partition.
 *
 * <p><b>The expected pages are captured, not composed here.</b> {@link TheArrangementPageRenderedWhole} holds
 * what {@code ArrangementReport.render} returned at {@code 5b0cc20}, the commit before ADR-223 was built, for
 * {@link ThreePartitionsOnThePage}'s partitions and for none. That method is gone with this record, so nothing
 * in the tree can compute the page whole any more.
 *
 * <p>Pure: the page is written into a {@code StringBuilder}. That the tasklet writes it to a file beside
 * {@code arrangement.html} and moves it into place needs an invocation, and is not held here.
 */
@Epic("Arrangement")
@Feature("Arranging the documents")
@Issue("472")
@Link(name = "ADR-223", url = Adr.THE_LAST_THREE_STAGES_GO_THROUGH_ONE_SEED_PARTITION_AT_A_TIME, type = "adr")
@Link(name = "ADR-112", url = Adr.THE_ARRANGEMENT_IS_ORDERED_BY_SIZE_AND_MEAN_SCORE, type = "adr")
@Link(name = "ADR-107", url = Adr.THE_ARRANGEMENT_GATE_APPROVES_A_NAMED_RUN, type = "adr")
class ArrangementPageIsWrittenAsItsPartitionsComeTest {

    @Test
    @Story("The page the operator approves is the same page, written a part at a time")
    @DisplayName("Written one exemplar at a time, the page for three exemplars is byte for byte the page that was written whole")
    void threePartitionsWrittenOneAtATimeAreTheSamePage() throws IOException {
        StringBuilder page = new StringBuilder();
        ArrangementReport.open(page, ThreePartitionsOnThePage.APPROVAL_NAME, ThreePartitionsOnThePage.CORPUS_ROOT);
        for (ArrangementReport.Partition partition : ThreePartitionsOnThePage.partitions()) {
            ArrangementReport.partition(page, partition);
        }
        ArrangementReport.close(page, ThreePartitionsOnThePage.partitions().size());

        claim(
                "every character is the one the whole page had: what the operator is asked, the name to approve,"
                        + " each exemplar's heading, its line of counts and its table, in the order the"
                        + " arrangement gives them, and how to read the page",
                () -> assertThat(page.toString()).isEqualTo(TheArrangementPageRenderedWhole.threePartitions()));
    }

    @Test
    @Story("The page the operator approves is the same page, written a part at a time")
    @DisplayName("What stands before the first exemplar is already on the page before any exemplar is handed over")
    void whatComesBeforeTheFirstPartitionIsWrittenFirst() throws IOException {
        StringBuilder page = new StringBuilder();
        ArrangementReport.open(page, ThreePartitionsOnThePage.APPROVAL_NAME, ThreePartitionsOnThePage.CORPUS_ROOT);
        String opened = page.toString();
        List<String> afterEach = new ArrayList<>();
        for (ArrangementReport.Partition partition : ThreePartitionsOnThePage.partitions()) {
            ArrangementReport.partition(page, partition);
            afterEach.add(page.toString());
        }

        claim(
                "the opening is the start of the whole page and carries the name to approve, so it needs nothing"
                        + " of any exemplar to be written",
                () -> assertThat(TheArrangementPageRenderedWhole.threePartitions())
                        .startsWith(opened)
                        .satisfies(whole -> assertThat(opened).contains(ThreePartitionsOnThePage.APPROVAL_NAME)));
        claim(
                "and after each exemplar the page so far is still the start of the whole page, each one longer"
                        + " than the last: an exemplar is written when it is handed over and never revisited",
                () -> assertThat(afterEach)
                        .allSatisfy(soFar -> assertThat(TheArrangementPageRenderedWhole.threePartitions()).startsWith(soFar))
                        .isSortedAccordingTo((shorter, longer) -> Integer.compare(shorter.length(), longer.length()))
                        .doesNotHaveDuplicates());
    }

    @Test
    @Story("The page the operator approves is the same page, written a part at a time")
    @DisplayName("With no exemplar handed over, the page closes on the sentence that nothing was arranged, as the whole page did")
    void noPartitionIsTheSamePage() throws IOException {
        StringBuilder page = new StringBuilder();
        ArrangementReport.open(page, ThreePartitionsOnThePage.APPROVAL_NAME, ThreePartitionsOnThePage.CORPUS_ROOT);
        ArrangementReport.close(page, 0);

        claim(
                "every character is the one the whole page had for an arrangement of nothing",
                () -> assertThat(page.toString()).isEqualTo(TheArrangementPageRenderedWhole.noPartition()));
    }
}
