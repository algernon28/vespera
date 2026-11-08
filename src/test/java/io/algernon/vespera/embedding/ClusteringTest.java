package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.WholeRun;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * One seed partition, clustered end to end over the stored vectors (ADR-087, ADR-045): the graph, the
 * communities and the rows, composed the way stage 5's own step composes them.
 *
 * <p><b>The count is never supplied.</b> The fixtures below say how the documents resemble each
 * other and nothing says how many clusters to find — which is the property that lets a partition of
 * eleven documents and one of eleven thousand share this code path.
 *
 * <p><b>Clusters of twenty, not of three.</b> k is fifteen and there is no edge similarity floor, so in
 * a partition of a dozen documents every document keeps every other and the graph is complete
 * whatever the vectors say. A fixture small enough to check by hand would therefore check nothing:
 * the structure only becomes visible once each cluster is larger than k, which is what these partitions
 * are sized for.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Relevance")
@Feature("Clustering")
@Issue("109")
@Link(name = "ADR-087", url = Adr.CLUSTERS_ARE_MODULARITY_COMMUNITIES, type = "adr")
@Link(name = "ADR-045", url = Adr.CLUSTERING_RUNS_WITHIN_EACH_SEED_PARTITION, type = "adr")
@Link(name = "ADR-096", url = Adr.K_RETAINS_NEIGHBOURS_REGARDLESS_OF_DISTANCE, type = "adr")
class ClusteringTest {

    /** The number of neighbours the application ships (vespera.clustering.neighbours). */
    private static final int NEIGHBOURS = 15;

    /** The resolution the application ships (vespera.clustering.resolution). */
    private static final double RESOLUTION = 1.0;

    private static final String MODEL = "a-comparing-model:0.6b";
    private static final String CHUNKER_IDENTITY = "docling-hybrid-chunker-v1";
    private static final String CHUNKING_RULE_IDENTITY = "words-512-v1";
    private static final int DIMENSION = 4;
    private static final String EMBEDDER_IDENTITY =
            "model=" + MODEL + ";digest=d34db33f;dtype=F16;dimension=" + DIMENSION + ";instruction=none";

    /** Larger than k, so which documents a document keeps says something about the vectors. */
    private static final int CLUSTER_SIZE = 20;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Every survivor lands in exactly one cluster")
    @DisplayName("Two groups of documents become two clusters, and every document is in one of them")
    void clustersAPartitionWithoutBeingToldHowManyClustersToFind() {
        Clustering clustering = ClusteringBeans.real(jdbcTemplate);
        DocumentClusters clusters = new DocumentClusters(jdbcTemplate);
        long walkId = insertWalk("C:/two-groups");
        RunId run = insertRun(walkId, "clustering-test-two-groups");
        OccurrenceId seed = insertOccurrence(walkId, "seeds/exemplar.pdf");
        Map<OccurrenceId, String> partition = new LinkedHashMap<>();
        partition.putAll(insertCluster(walkId, run, seed, "alike", 0));
        partition.putAll(insertCluster(walkId, run, seed, "unalike", 1));

        clustering.clusterAndRecord(
                run, seed, partition, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, EMBEDDER_IDENTITY, NEIGHBOURS, RESOLUTION);

        Map<OccurrenceId, Integer> ordinals = ordinalsOf(clusters, run);
        claim(
                "every one of the " + partition.size() + " documents in the partition carries a cluster,"
                        + " so membership is total: a survivor with no cluster is a hole in the page tree"
                        + " above it",
                () -> assertThat(ordinals.keySet()).containsExactlyInAnyOrderElementsOf(partition.keySet()));
        claim(
                "and each carries exactly one, so membership is disjoint -- a document lands in one"
                        + " cluster rather than being listed under several",
                () -> assertThat(WholeRun.membership(jdbcTemplate,run)).hasSize(partition.size()));
        claim(
                "the two groups came out as two clusters, and nothing was told there were two: the count"
                        + " is a property of how the documents resemble each other, which is what lets a"
                        + " partition of eleven and one of eleven thousand share this code path",
                () -> assertThat(clusters.sizesFor(run, seed)).containsExactly(CLUSTER_SIZE, CLUSTER_SIZE));
        claim(
                "and the documents of one group are together rather than split across the two clusters,"
                        + " so the grouping followed the vectors and not the order the rows arrived in",
                () -> assertThat(ordinalsOfCluster(ordinals, "alike")).containsOnly(0));
    }

    @Test
    @Story("The same corpus always produces the same clusters")
    @DisplayName("Clustering the same partition twice gives every document the same cluster ordinal")
    void isDeterministicAcrossRuns() {
        Clustering clustering = ClusteringBeans.real(jdbcTemplate);
        DocumentClusters clusters = new DocumentClusters(jdbcTemplate);
        long walkId = insertWalk("C:/twice");
        RunId first = insertRun(walkId, "clustering-test-first-run");
        RunId second = insertRun(walkId, "clustering-test-second-run");
        OccurrenceId seed = insertOccurrence(walkId, "seeds/exemplar.pdf");
        Map<OccurrenceId, String> partition = new LinkedHashMap<>();
        partition.putAll(insertCluster(walkId, first, seed, "alike", 0));
        partition.putAll(insertCluster(walkId, first, seed, "unalike", 1));
        partition.keySet().forEach(member -> insertScore(second, member, seed));

        clustering.clusterAndRecord(first, seed, partition, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, EMBEDDER_IDENTITY, NEIGHBOURS, RESOLUTION);
        clustering.clusterAndRecord(second, seed, partition, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, EMBEDDER_IDENTITY, NEIGHBOURS, RESOLUTION);

        claim(
                "every document landed on the same ordinal both times, not merely in the same company:"
                        + " an ordinal is part of a cluster's identity, and a page tree that reshuffled"
                        + " when nothing changed would be worse than a threshold that did, because a"
                        + " person has already reviewed it",
                () -> assertThat(ordinalsOf(clusters, second)).isEqualTo(ordinalsOf(clusters, first)));
        claim(
                "and the second run wrote its own rows beside the first's rather than editing them"
                        + " (ADR-077), so what an earlier run recorded is still readable",
                () -> assertThat(WholeRun.membership(jdbcTemplate,first)).hasSize(partition.size()));
    }

    /** Which cluster each document landed in, keyed by document so two runs can be compared. */
    private Map<OccurrenceId, Integer> ordinalsOf(DocumentClusters clusters, RunId run) {
        return WholeRun.membership(jdbcTemplate,run).stream()
                .collect(Collectors.toMap(
                        DocumentCluster::occurrenceId, DocumentCluster::clusterOrdinal, (a, b) -> a, LinkedHashMap::new));
    }

    /** The ordinals the documents of one named cluster landed on, by the path they were inserted under. */
    private List<Integer> ordinalsOfCluster(Map<OccurrenceId, Integer> ordinals, String cluster) {
        Map<Long, String> paths = jdbcTemplate
                .query(
                        "SELECT id, path FROM file_occurrence",
                        (resultSet, rowNumber) -> Map.entry(resultSet.getLong("id"), resultSet.getString("path")))
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return ordinals.entrySet().stream()
                .filter(member -> paths.get(member.getKey().value()).startsWith(cluster))
                .map(Map.Entry::getValue)
                .toList();
    }

    /** {@link #CLUSTER_SIZE} documents pointing the same way, each scored to {@code seed}. */
    private Map<OccurrenceId, String> insertCluster(
            long walkId, RunId run, OccurrenceId seed, String cluster, int axis) {
        return insertCluster(walkId, run, seed, cluster, axis, CLUSTER_SIZE);
    }

    /**
     * {@code size} documents whose vectors point along {@code axis}, so documents of one cluster
     * resemble each other and barely resemble the other cluster's.
     */
    private Map<OccurrenceId, String> insertCluster(
            long walkId, RunId run, OccurrenceId seed, String cluster, int axis, int size) {
        Map<OccurrenceId, String> members = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            String path = cluster + "-" + i + ".txt";
            String contentHash = cluster + "-" + i;
            OccurrenceId occurrenceId = insertOccurrence(walkId, path);
            // One chunk per document, so the document's mean vector is that chunk: what is being tested
            // here is the grouping, and a document of several chunks would only test the mean.
            float[] vector = new float[DIMENSION];
            vector[axis] = 1f;
            // A little of a third component, distinct per document, so no two documents are identical --
            // ties are broken by index, and a fixture of identical vectors would be testing that rule
            // rather than the grouping.
            vector[DIMENSION - 1] = 0.01f * (i + 1);
            insertVector(contentHash, vector);
            insertScore(run, occurrenceId, seed);
            members.put(occurrenceId, contentHash);
        }
        return members;
    }

    private long insertWalk(String root) {
        jdbcTemplate.update("INSERT INTO walk (root, finished) VALUES (?, 1)", root);
        return jdbcTemplate.queryForObject("SELECT id FROM walk ORDER BY id DESC LIMIT 1", Long.class);
    }

    private RunId insertRun(long walkId, String runId) {
        jdbcTemplate.update(
                "INSERT INTO run (id, stage, implementation_version, config_consumed, walk_id)"
                        + " VALUES (?, 'embedding-scoring', 'test', '{}', ?)",
                runId,
                walkId);
        return new RunId(runId);
    }

    private OccurrenceId insertOccurrence(long walkId, String path) {
        jdbcTemplate.update(
                "INSERT INTO file_occurrence (walk_id, path, size_bytes, last_modified, creation_time)"
                        + " VALUES (?, ?, 1, ?, ?)",
                walkId,
                path,
                Instant.EPOCH.toString(),
                Instant.EPOCH.toString());
        return new OccurrenceId(
                jdbcTemplate.queryForObject("SELECT id FROM file_occurrence ORDER BY id DESC LIMIT 1", Long.class));
    }

    private void insertVector(String contentHash, float[] vector) {
        new VectorCache(jdbcTemplate)
                .put(contentHash, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, 0, EMBEDDER_IDENTITY, vector);
    }

    private void insertScore(RunId run, OccurrenceId occurrenceId, OccurrenceId seed) {
        new RelevanceScoreCache(jdbcTemplate).record(occurrenceId, run, new RelevanceScore(0.5, seed));
    }
}
