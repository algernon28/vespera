package io.algernon.vespera.engine.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The check on a topic's overview (ADR-402 §2): every sentence carries a citation, and every citation is a number the
 * call sent. A number within the topic's clusters that the call did not send, an unwritten cluster's, is turned down
 * as a number past every cluster is, and both as {@link ClusterFaultKind#CITATION_NOT_IN_RANGE}, with the detail §7
 * gives.
 */
@Epic("Synthesis")
@Feature("Writing over a topic")
@Issue("830")
@Link(name = "ADR-402", url = Adr.A_TOPICS_OVERVIEW_CITES_ITS_WRITTEN_CLUSTERS, type = "adr")
class OverviewCheckTest {

    /**
     * The numbers a call over a topic of five clusters sent, the third and fourth unwritten: the gaps are where a
     * number within the topic was not sent.
     */
    private static final SortedSet<Integer> CLUSTERS_ONE_TWO_AND_FIVE_SENT = new TreeSet<>(List.of(1, 2, 5));

    @Test
    @Story("A topic's overview cites only groups it was given, in every sentence")
    @DisplayName("Writing whose every sentence cites a group it was given passes")
    void writingWhoseEverySentenceCitesAClusterSentPasses() {
        String writing = "The groups share a harvest [1][2]. The last adds its cost [5].";

        claim(
                "two sentences citing only groups 1, 2 and 5, all of them sent, are not turned down",
                () -> assertThat(OverviewCheck.faultIn(writing, CLUSTERS_ONE_TWO_AND_FIVE_SENT)).isEmpty());
    }

    @Test
    @Story("A topic's overview cites only groups it was given, in every sentence")
    @DisplayName("A sentence citing no group turns the writing down, saying which sentence of how many")
    void aSentenceCitingNothingIsTurnedDown() {
        String writing = "The groups share a harvest [1]. They differ on cost. The last adds its schedule [5].";

        claim(
                "the second of three sentences cites nothing, and the writing is turned down for its citations,"
                        + " naming that sentence and the three",
                () -> assertThat(OverviewCheck.faultIn(writing, CLUSTERS_ONE_TWO_AND_FIVE_SENT))
                        .contains(new ClusterFault(
                                ClusterFaultKind.CITATION_NOT_IN_RANGE, "sentence 2 of 3 cites no group")));
    }

    @Test
    @Story("A topic's overview cites only groups it was given, in every sentence")
    @DisplayName("A number within the topic's groups that the call did not send turns the writing down")
    void aNumberWithinTheTopicButNotSentIsTurnedDown() {
        String writing = "The groups share a harvest [1]. The fourth adds its cost [4].";

        claim(
                "group 4 lies within the topic's five but was not sent, so citing it turns the writing down,"
                        + " naming the number and the groups sent",
                () -> assertThat(OverviewCheck.faultIn(writing, CLUSTERS_ONE_TWO_AND_FIVE_SENT))
                        .contains(new ClusterFault(
                                ClusterFaultKind.CITATION_NOT_IN_RANGE, "citation [4] against groups 1, 2, 5 sent")));
    }

    @Test
    @Story("A topic's overview cites only groups it was given, in every sentence")
    @DisplayName("A number past every group of the topic turns the writing down")
    void aNumberPastEveryClusterIsTurnedDown() {
        String writing = "The groups share a harvest [1]. A ninth adds its cost [9].";

        claim(
                "group 9 is past the topic's five, so citing it turns the writing down, naming the number and the"
                        + " groups sent",
                () -> assertThat(OverviewCheck.faultIn(writing, CLUSTERS_ONE_TWO_AND_FIVE_SENT))
                        .contains(new ClusterFault(
                                ClusterFaultKind.CITATION_NOT_IN_RANGE, "citation [9] against groups 1, 2, 5 sent")));
    }
}
