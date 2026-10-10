package io.algernon.vespera.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The arrangement's order, given one seed partition at a time (ADR-223 section 3, ADR-112): the partitions
 * are put in order from one row each, a size and a seed's path, and each partition's clusters are then ordered
 * on their own. What comes out is the arrangement the two rules gave when every partition was gathered and
 * ordered at once.
 *
 * <p><b>The expected arrangement is the one the whole-run rule gave.</b> {@link ThreePartitions#arrangement()}
 * states the places of six clusters, and the program that captured {@link TheTreeWrittenFromLists} checked
 * them against {@code Arrangement.order(Arrangement.partitionsOf(...))} over the same eleven survivors at
 * {@code 5b0cc20}, the commit before ADR-223 was built: they were equal. That whole-run {@code order} is gone
 * with this record.
 *
 * <p>Pure: no database.
 */
@Epic("Arrangement")
@Feature("Ordering the arrangement")
@Issue("472")
@Link(name = "ADR-223", url = Adr.THE_LAST_THREE_STAGES_GO_THROUGH_ONE_SEED_PARTITION_AT_A_TIME, type = "adr")
@Link(name = "ADR-112", url = Adr.THE_ARRANGEMENT_IS_ORDERED_BY_SIZE_AND_MEAN_SCORE, type = "adr")
class SeedPartitionsAreOrderedFromTheirSizesTest {

    /** Each seed's own path beneath the seed folder, which breaks a tie between partitions of one size. */
    private static final Map<OccurrenceId, String> THE_SEED_PATHS = Map.of(
            ThreePartitions.ALPHA, ThreePartitions.ALPHA_PATH,
            ThreePartitions.BETA, ThreePartitions.BETA_PATH,
            ThreePartitions.GAMMA, ThreePartitions.GAMMA_PATH);

    @Test
    @Story("The top level is ordered from one row for each exemplar")
    @DisplayName("Exemplars are ordered by how many documents sit under each, and equal ones by their own file names, from a count and a name alone")
    void ordersThePartitionsFromTheirSizesAndSeedPaths() {
        Map<OccurrenceId, Integer> memberCounts = new LinkedHashMap<>();
        memberCounts.put(ThreePartitions.GAMMA, 3);
        memberCounts.put(ThreePartitions.ALPHA, 3);
        memberCounts.put(ThreePartitions.BETA, 5);

        List<OccurrenceId> inOrder = Arrangement.inOrder(memberCounts, THE_SEED_PATHS);

        claim(
                "the exemplar with five documents comes first, and the two with three each follow in the order"
                        + " of their file names, whatever order they were offered in: nothing of any document"
                        + " under them was needed to say so",
                () -> assertThat(inOrder)
                        .containsExactly(ThreePartitions.BETA, ThreePartitions.ALPHA, ThreePartitions.GAMMA));
    }

    @Test
    @Story("Each exemplar's groups are ordered on their own")
    @DisplayName("Ordered one exemplar at a time, six groups under three exemplars get the places they got when all were ordered at once")
    void orderingOnePartitionAtATimeGivesTheSameArrangement() {
        Map<OccurrenceId, Integer> memberCounts = new LinkedHashMap<>();
        for (OccurrenceId seed : List.of(ThreePartitions.GAMMA, ThreePartitions.ALPHA, ThreePartitions.BETA)) {
            memberCounts.put(seed, membersUnder(seed).size());
        }
        List<OccurrenceId> inOrder = Arrangement.inOrder(memberCounts, THE_SEED_PATHS);

        List<ArrangedCluster> arranged = new ArrayList<>();
        for (int place = 0; place < inOrder.size(); place++) {
            List<Partition> gathered = Arrangement.partitionsOf(membersUnder(inOrder.get(place)));
            int partitionOrder = place + 1;
            claim(
                    "the documents of exemplar " + partitionOrder + " alone gather into one partition",
                    () -> assertThat(gathered).hasSize(1));
            arranged.addAll(Arrangement.order(gathered.getFirst(), partitionOrder));
        }

        claim(
                "each group has the exemplar's place and its own place under it exactly as the whole arrangement"
                        + " had them: groups of several documents ahead of a lone document, the closer group"
                        + " first, and two documents scoring alike changing nothing",
                () -> assertThat(arranged)
                        .containsExactlyElementsOf(ThreePartitions.arrangement().stream()
                                .map(RecordedCluster::cluster)
                                .toList()));
    }

    /** One partition's members as stage 6a hands them over: in occurrence order, each with its score. */
    private static List<ClusteredDocument> membersUnder(OccurrenceId seed) {
        return ThreePartitions.survivors().stream()
                .filter(survivor -> survivor.winningSeed().equals(seed))
                .map(survivor -> new ClusteredDocument(
                        survivor.occurrence(),
                        survivor.winningSeed(),
                        survivor.seedPath(),
                        survivor.clusterOrdinal(),
                        survivor.score()))
                .toList();
    }
}
