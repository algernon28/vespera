package io.algernon.vespera.embedding;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Which cluster each survivor landed in (ADR-087), inside the seed partition its winning seed
 * defines.
 *
 * <p><b>A cluster exists as the set of rows carrying its identity</b> — the run, the winning seed
 * and the ordinal — rather than as a row of its own that membership would have to be kept in step
 * with. There is nothing to go stale, and a cluster with no members simply has no rows.
 *
 * <p><b>No in-partition unattributed bucket.</b> ADR-022's unattributed bucket sits at the top level,
 * for documents with no winning seed at all, which is a different condition with a different cause. A
 * second one inside a partition would fabricate a page no document belongs to.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Relevance")
@Feature("Clustering")
@Issue("109")
@Link(name = "ADR-087", url = Adr.CLUSTERS_ARE_MODULARITY_COMMUNITIES, type = "adr")
class DocumentClustersTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Every survivor lands in exactly one cluster")
    @DisplayName("Each document is recorded once, in one cluster of its own partition")
    void recordsEachDocumentOnceInOneCluster() {
        DocumentClusters clusters = new DocumentClusters(jdbcTemplate);
        OccurrenceId seed = anOccurrence("seeds/exemplar.pdf");
        OccurrenceId first = anOccurrence("a.txt");
        OccurrenceId second = anOccurrence("b.txt");
        RunId run = aRun(seed);

        clusters.record(run, first, seed, 0);
        clusters.record(run, second, seed, 0);

        claim(
                "both documents are recorded, so membership is total over the partition -- every"
                        + " survivor has to land somewhere for the page tree above it to be complete",
                () -> assertThat(clusters.forRun(run)).hasSize(2));
        claim(
                "and each appears once, in one cluster: membership is disjoint by construction, since a"
                        + " document carries a single ordinal rather than appearing in a list per cluster",
                () -> assertThat(clusters.forRun(run))
                        .extracting(DocumentCluster::occurrenceId)
                        .containsExactlyInAnyOrder(first, second));
        claim(
                "the cluster is identified by the seed whose partition it sits in and its ordinal there,"
                        + " so two partitions may both hold a cluster 0 without collision",
                () -> assertThat(clusters.forRun(run))
                        .allSatisfy(member -> assertThat(member.winningSeedOccurrenceId()).isEqualTo(seed)));
    }

    @Test
    @Story("Every survivor lands in exactly one cluster")
    @DisplayName("A document alone in its cluster is recorded as a cluster of one")
    void recordsAClusterOfOne() {
        DocumentClusters clusters = new DocumentClusters(jdbcTemplate);
        OccurrenceId seed = anOccurrence("seeds/exemplar.pdf");
        OccurrenceId alone = anOccurrence("solitary.txt");
        OccurrenceId other = anOccurrence("grouped.txt");
        RunId run = aRun(seed);

        clusters.record(run, alone, seed, 0);
        clusters.record(run, other, seed, 1);

        claim(
                "a cluster of one is a cluster: the document belongs somewhere, and the alternative is a"
                        + " survivor that no page in the tree accounts for",
                () -> assertThat(clusters.sizesFor(run, seed)).containsExactly(1, 1));
    }

    @Test
    @Story("The spread of cluster sizes is reportable per partition")
    @DisplayName("Cluster sizes are read back per partition, in ordinal order")
    void readsBackClusterSizesPerPartition() {
        DocumentClusters clusters = new DocumentClusters(jdbcTemplate);
        OccurrenceId firstSeed = anOccurrence("seeds/one.pdf");
        OccurrenceId secondSeed = anOccurrence("seeds/two.pdf");
        RunId run = aRun(firstSeed);
        for (int i = 0; i < 3; i++) {
            clusters.record(run, anOccurrence("first-partition-" + i + ".txt"), firstSeed, 0);
        }
        clusters.record(run, anOccurrence("first-partition-alone.txt"), firstSeed, 1);
        clusters.record(run, anOccurrence("second-partition.txt"), secondSeed, 0);

        claim(
                "the first partition reports its two clusters by size, in ordinal order, which is what a"
                        + " reader needs to see whether one cluster swallowed the partition or it broke"
                        + " into singletons",
                () -> assertThat(clusters.sizesFor(run, firstSeed)).containsExactly(3, 1));
        claim(
                "and a partition is reported on its own, never folded in with another seed's: what makes"
                        + " a cluster is the seed it sits under as much as the ordinal",
                () -> assertThat(clusters.sizesFor(run, secondSeed)).containsExactly(1));
    }

    @Test
    @Story("What clustering does not write")
    @DisplayName("No table holds a document mean vector")
    void storesNoDocumentMeanVectors() {
        List<String> tables = jdbcTemplate.query(
                "SELECT name FROM sqlite_master WHERE type = 'table'",
                (resultSet, rowNumber) -> resultSet.getString("name"));

        claim(
                "the mean vectors clustering compares are computed and discarded, never stored: they are"
                        + " derived from vectors already held, and a second copy would be a cache nothing"
                        + " reads that has to be invalidated when the chunking or the model changes",
                () -> assertThat(tables).noneSatisfy(table -> assertThat(table).contains("mean")));
    }

    private OccurrenceId anOccurrence(String path) {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.startWalk(Path.of("C:/corpus"));
        ledger.fileOccurrence(
                walkId,
                new OccurrencePath(path),
                1,
                Instant.parse("2026-08-29T10:15:30Z"),
                Instant.parse("2026-08-20T08:00:00Z"));
        return ledger.occurrenceId(walkId, new OccurrencePath(path)).orElseThrow();
    }

    private RunId aRun(OccurrenceId anyOccurrenceInTheWalk) {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = new WalkId(jdbcTemplate.queryForObject(
                "SELECT walk_id FROM file_occurrence WHERE id = ?", Long.class, anyOccurrenceInTheWalk.value()));
        return ledger.startRun("embedding-scoring", "abc" + System.nanoTime(), "{}", walkId, List.of());
    }
}
