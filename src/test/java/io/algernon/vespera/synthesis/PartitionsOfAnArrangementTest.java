package io.algernon.vespera.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * An arrangement read one seed partition at a time (ADR-223 section 4): which partitions it holds, in the
 * order stored, and one partition's clusters, in the order stored, with nothing of another partition or of
 * another arrangement among them.
 *
 * <p>Until ADR-223 the only read of the table gave every cluster of the arrangement at once, sorted by the
 * database. Stage 6a's page and stage 6b's walk and tree now ask for a partition's clusters as they come to
 * it. The order is still the stored one (ADR-112): the rows are written here out of it, so that a read which
 * answered in the order they were written, or in the order of their ordinals, is told apart.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Arrangement")
@Feature("Recording the arrangement")
@Issue("472")
@Link(name = "ADR-223", url = Adr.THE_LAST_THREE_STAGES_GO_THROUGH_ONE_SEED_PARTITION_AT_A_TIME, type = "adr")
@Link(name = "ADR-112", url = Adr.THE_ARRANGEMENT_IS_ORDERED_BY_SIZE_AND_MEAN_SCORE, type = "adr")
class PartitionsOfAnArrangementTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Clusters clusters;
    private OccurrenceId first;
    private OccurrenceId second;
    private RunId arrangement;

    /**
     * Two partitions under one arrangement. The seed recorded first is placed second and holds two clusters,
     * written with the later one first; the seed recorded second is placed first and holds one. A second
     * arrangement holds a cluster under the first seed, which no read of the first arrangement may return.
     */
    @BeforeEach
    void twoPartitionsWrittenOutOfOrder() {
        clusters = new Clusters(jdbcTemplate);
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walk = ledger.walks().startWalk(Path.of("C:/corpus-partitions-" + System.nanoTime()));
        first = anOccurrence(ledger, walk, "seeds/first.docx");
        second = anOccurrence(ledger, walk, "seeds/second.docx");
        arrangement = ledger.runs().startRun("arrangement", "a" + System.nanoTime(), "{}", walk, List.of());
        RunId another = ledger.runs().startRun("arrangement", "b" + System.nanoTime(), "{}", walk, List.of());

        clusters.record(arrangement, new ArrangedCluster(first, 0, 2, 2, 2), new ClusterLabel("Placed last"));
        clusters.record(arrangement, new ArrangedCluster(first, 5, 1, 2, 1), new ClusterLabel("Placed second"));
        clusters.record(arrangement, new ArrangedCluster(second, 0, 3, 1, 1), new ClusterLabel("Placed first"));
        clusters.record(another, new ArrangedCluster(first, 9, 4, 1, 1), new ClusterLabel("Of another arrangement"));
    }

    @Test
    @Story("An arrangement is read one exemplar at a time")
    @DisplayName("The exemplars of an arrangement come back in the order the arrangement placed them")
    void theSeedsComeInStoredOrder() {
        claim(
                "the exemplar placed first comes first although it was recorded last, and each comes once however"
                        + " many groups sit under it",
                () -> assertThat(clusters.seedsOf(arrangement)).containsExactly(second, first));
    }

    @Test
    @Story("An arrangement is read one exemplar at a time")
    @DisplayName("Each exemplar comes back with its place, how many groups sit under it and how many documents")
    void thePartitionsComeWithTheirCounts() {
        claim(
                "the first holds one group of three documents, the second two groups of three documents between"
                        + " them, and the other arrangement's group of four is counted under neither",
                () -> assertThat(clusters.partitionsOf(arrangement))
                        .containsExactly(new ArrangedPartition(second, 1, 1, 3), new ArrangedPartition(first, 2, 2, 3)));
    }

    @Test
    @Story("An arrangement is read one exemplar at a time")
    @DisplayName("One exemplar's groups come back in the order the arrangement placed them, and no other exemplar's")
    void onePartitionsClustersComeInStoredOrder() {
        claim(
                "the two groups under the exemplar placed second come back in their stored order, which is"
                        + " neither the order they were recorded in nor the order of their numbers, each with"
                        + " its name, its size and both its places",
                () -> assertThat(clusters.ofPartition(arrangement, first))
                        .containsExactly(
                                new RecordedCluster(new ArrangedCluster(first, 5, 1, 2, 1), new ClusterLabel("Placed second")),
                                new RecordedCluster(new ArrangedCluster(first, 0, 2, 2, 2), new ClusterLabel("Placed last"))));
        claim(
                "and the other exemplar's one group comes back on its own",
                () -> assertThat(clusters.ofPartition(arrangement, second))
                        .containsExactly(
                                new RecordedCluster(new ArrangedCluster(second, 0, 3, 1, 1), new ClusterLabel("Placed first"))));
    }

    @Test
    @Story("An arrangement is read one exemplar at a time")
    @DisplayName("An arrangement that recorded nothing has no exemplar, and an exemplar with nothing under it has no group")
    void nothingRecordedAnswersNothing() {
        RunId empty = new Ledger(jdbcTemplate)
                .runs()
                .startRun("arrangement", "c" + System.nanoTime(), "{}", theWalkOf(first), List.of());

        claim(
                "an arrangement with no group answers no exemplar and no partition, where a stop would hide"
                        + " that it had simply arranged nothing",
                () -> {
                    assertThat(clusters.seedsOf(empty)).isEmpty();
                    assertThat(clusters.partitionsOf(empty)).isEmpty();
                    assertThat(clusters.ofPartition(empty, first)).isEmpty();
                });
    }

    private static OccurrenceId anOccurrence(Ledger ledger, WalkId walk, String path) {
        ledger.occurrences().fileOccurrence(walk, new OccurrencePath(path), 1, Instant.EPOCH, Instant.EPOCH);
        return ledger.occurrences().occurrenceId(walk, new OccurrencePath(path)).orElseThrow();
    }

    private WalkId theWalkOf(OccurrenceId occurrence) {
        return new WalkId(jdbcTemplate.queryForObject(
                "SELECT walk_id FROM file_occurrence WHERE id = ?", Long.class, occurrence.value()));
    }
}
