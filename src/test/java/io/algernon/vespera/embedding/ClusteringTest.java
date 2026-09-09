package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
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
 * other and nothing says how many groups to find — which is the property that lets a partition of
 * eleven documents and one of eleven thousand share this code path.
 *
 * <p><b>Groups of twenty, not of three.</b> k is fifteen and there is no edge similarity floor, so in
 * a partition of a dozen documents every document keeps every other and the graph is complete
 * whatever the vectors say. A fixture small enough to check by hand would therefore check nothing:
 * the structure only becomes visible once each group is larger than k, which is what these partitions
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

    private static final String MODEL = "qwen3-embedding:0.6b";
    private static final String CHUNKER_IDENTITY = "docling-hybrid-chunker-v1";
    private static final String CHUNKING_RULE_IDENTITY = "words-512-v1";
    private static final int DIMENSION = 4;
    private static final String EMBEDDER_IDENTITY =
            "model=" + MODEL + ";digest=d34db33f;dtype=F16;dimension=" + DIMENSION + ";instruction=none";

    /** Larger than k, so which documents a document keeps says something about the vectors. */
    private static final int GROUP_SIZE = 20;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Every survivor lands in exactly one cluster")
    @DisplayName("Two groups of documents become two clusters, and every document is in one of them")
    void groupsAPartitionWithoutBeingToldHowManyGroupsToFind() {
        Clustering clustering = ClusteringBeans.real(jdbcTemplate);
        DocumentClusters clusters = new DocumentClusters(jdbcTemplate);
        long walkId = insertWalk("C:/two-groups");
        RunId run = insertRun(walkId, "clustering-test-two-groups");
        OccurrenceId seed = insertOccurrence(walkId, "seeds/exemplar.pdf");
        Map<OccurrenceId, String> partition = new LinkedHashMap<>();
        partition.putAll(insertGroup(walkId, run, seed, "alike", 0));
        partition.putAll(insertGroup(walkId, run, seed, "unalike", 1));

        clustering.clusterAndRecord(
                run, seed, partition, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, MODEL);

        Map<OccurrenceId, Integer> ordinals = ordinalsOf(clusters, run);
        claim(
                "every one of the " + partition.size() + " documents in the partition carries a cluster,"
                        + " so membership is total: a survivor with no cluster is a hole in the page tree"
                        + " above it",
                () -> assertThat(ordinals.keySet()).containsExactlyInAnyOrderElementsOf(partition.keySet()));
        claim(
                "and each carries exactly one, so membership is disjoint -- a document lands in one"
                        + " cluster rather than being listed under several",
                () -> assertThat(clusters.forRun(run)).hasSize(partition.size()));
        claim(
                "the two groups came out as two clusters, and nothing was told there were two: the count"
                        + " is a property of how the documents resemble each other, which is what lets a"
                        + " partition of eleven and one of eleven thousand share this code path",
                () -> assertThat(clusters.sizesFor(run, seed)).containsExactly(GROUP_SIZE, GROUP_SIZE));
        claim(
                "and the documents of one group are together rather than split across the two clusters,"
                        + " so the grouping followed the vectors and not the order the rows arrived in",
                () -> assertThat(ordinalsOfGroup(ordinals, "alike")).containsOnly(0));
        claim(
                "no in-partition catch-all was invented: there are exactly as many clusters as the"
                        + " documents formed, and an extra bucket would be a page no document belongs to"
                        + " (ADR-022's unattributed sits at the top level, for documents with no winning"
                        + " seed, which is a different condition with a different cause)",
                () -> assertThat(clusters.sizesFor(run, seed)).hasSize(2));
    }

    @Test
    @Story("Every survivor lands in exactly one cluster")
    @DisplayName("A partition of one document is recorded as a cluster of one")
    void recordsAPartitionOfOneAsAClusterOfOne() {
        Clustering clustering = ClusteringBeans.real(jdbcTemplate);
        DocumentClusters clusters = new DocumentClusters(jdbcTemplate);
        long walkId = insertWalk("C:/one-document");
        RunId run = insertRun(walkId, "clustering-test-one-document");
        OccurrenceId seed = insertOccurrence(walkId, "seeds/exemplar.pdf");
        Map<OccurrenceId, String> partition = insertGroup(walkId, run, seed, "solitary", 0, 1);

        clustering.clusterAndRecord(
                run, seed, partition, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, MODEL);

        claim(
                "the one document is a cluster of one rather than a document belonging to nothing: a"
                        + " partition this small is a real outcome, and the alternative is a survivor no"
                        + " page in the tree accounts for",
                () -> assertThat(clusters.sizesFor(run, seed)).containsExactly(1));
    }

    @Test
    @Story("All singletons is an answer, not a failure")
    @DisplayName("A partition of mutually distant documents is still grouped, because k has no distance floor")
    void groupsEvenAPartitionOfMutuallyDistantDocuments() {
        Clustering clustering = ClusteringBeans.real(jdbcTemplate);
        DocumentClusters clusters = new DocumentClusters(jdbcTemplate);
        long walkId = insertWalk("C:/mutually-distant");
        RunId run = insertRun(walkId, "clustering-test-mutually-distant");
        OccurrenceId seed = insertOccurrence(walkId, "seeds/exemplar.pdf");
        Map<OccurrenceId, String> partition = insertMutuallyDistant(walkId, run, seed, GROUP_SIZE);

        clustering.clusterAndRecord(
                run, seed, partition, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, MODEL);

        claim(
                "every document is still recorded, so nothing was dropped for resembling nothing:"
                        + " membership is total whatever the vectors say",
                () -> assertThat(clusters.forRun(run)).hasSize(GROUP_SIZE));
        claim(
                "and they come out grouped rather than as one cluster per document, because k retains a"
                        + " document's fifteen nearest neighbours however far away they are and ADR-087"
                        + " deliberately sets no edge similarity floor -- so a partition of documents"
                        + " sharing nothing is grouped by which of them are least unalike. All singletons"
                        + " is reachable where the neighbour lists are empty, which is a partition of one,"
                        + " and ADR-096 corrects the illustration that said otherwise: what tells this case"
                        + " apart from a partition that really does resemble itself is the spread of the"
                        + " retained edges, which is reported rather than acted on",
                () -> assertThat(clusters.sizesFor(run, seed)).hasSizeLessThan(GROUP_SIZE));
        claim(
                "and nothing merged them into a catch-all either: the clusters hold the whole partition"
                        + " between them, with no bucket beside them holding the leftovers",
                () -> assertThat(clusters.sizesFor(run, seed).stream()
                                .mapToInt(Integer::intValue)
                                .sum())
                        .isEqualTo(GROUP_SIZE));
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
        partition.putAll(insertGroup(walkId, first, seed, "alike", 0));
        partition.putAll(insertGroup(walkId, first, seed, "unalike", 1));
        partition.keySet().forEach(member -> insertScore(second, member, seed));

        clustering.clusterAndRecord(first, seed, partition, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, MODEL);
        clustering.clusterAndRecord(second, seed, partition, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, MODEL);

        claim(
                "every document landed on the same ordinal both times, not merely in the same company:"
                        + " an ordinal is part of a cluster's identity, and a page tree that reshuffled"
                        + " when nothing changed would be worse than a threshold that did, because a"
                        + " person has already reviewed it",
                () -> assertThat(ordinalsOf(clusters, second)).isEqualTo(ordinalsOf(clusters, first)));
        claim(
                "and the second run wrote its own rows beside the first's rather than editing them"
                        + " (ADR-077), so what an earlier run recorded is still readable",
                () -> assertThat(clusters.forRun(first)).hasSize(partition.size()));
    }

    @Test
    @Story("A partition is what the scoring step already recorded")
    @DisplayName("Partitions and their members are read from the scores, in occurrence order")
    void readsPartitionsFromTheScoresInOccurrenceOrder() {
        Clustering clustering = ClusteringBeans.real(jdbcTemplate);
        long walkId = insertWalk("C:/two-seeds");
        RunId run = insertRun(walkId, "clustering-test-two-seeds");
        OccurrenceId firstSeed = insertOccurrence(walkId, "seeds/one.pdf");
        OccurrenceId secondSeed = insertOccurrence(walkId, "seeds/two.pdf");
        List<OccurrenceId> firstMembers = List.copyOf(
                insertGroup(walkId, run, firstSeed, "first-partition", 0, 3).keySet());
        List<OccurrenceId> secondMembers = List.copyOf(
                insertGroup(walkId, run, secondSeed, "second-partition", 1, 2).keySet());

        claim(
                "each seed that won a document is one partition, and a seed that won none is absent"
                        + " rather than present and empty -- clustering runs within a partition and never"
                        + " corpus-wide (ADR-045)",
                () -> assertThat(clustering.partitions(run)).containsExactly(firstSeed, secondSeed));
        claim(
                "a partition holds exactly the documents that seed won",
                () -> assertThat(clustering.membersOf(run, firstSeed)).isEqualTo(firstMembers));
        claim(
                "and hands them over in occurrence order, which is the order ADR-087 fixes for visiting"
                        + " them: an order the database chose freely would make the ordinals an artefact"
                        + " of the query plan rather than a property of the corpus",
                () -> assertThat(clustering.membersOf(run, secondSeed)).isEqualTo(secondMembers));
    }

    @Test
    @Story("The ceiling does not move with the partition or the model")
    @DisplayName("A block is bounded in documents and in bytes, whatever the model's dimension")
    void boundsABlockInBothDocumentsAndBytes() {
        claim(
                "at a narrow dimension the document count binds, at " + Clustering.BLOCK_DOCUMENTS
                        + " -- the figure ADR-087's own table is worked in, kept so that the ceiling the"
                        + " decision tabulated is the ceiling the code has",
                () -> assertThat(Clustering.blockSizeFor(384)).isEqualTo(Clustering.BLOCK_DOCUMENTS));
        claim(
                "at a wide dimension the byte budget binds instead, because a document count alone is"
                        + " not a ceiling: the same 4,096 vectors are 6 MiB at 384 dimensions and 64 MiB"
                        + " at 4,096, chosen by a model nobody has named yet",
                () -> assertThat(Clustering.blockSizeFor(8_192)).isLessThan(Clustering.BLOCK_DOCUMENTS));
        claim(
                "so a block never exceeds the budget at any dimension, which is what makes two blocks a"
                        + " constant rather than a function of the model",
                () -> assertThat((long) Clustering.blockSizeFor(8_192) * 8_192 * Float.BYTES)
                        .isLessThanOrEqualTo(Clustering.BLOCK_BUDGET_BYTES));
        claim(
                "and a model wide enough that one vector exceeds the whole budget still gets a block of"
                        + " one document, which is slow rather than a pass that reads nothing",
                () -> assertThat(Clustering.blockSizeFor(64_000_000)).isEqualTo(1));
    }

    /** Which cluster each document landed in, keyed by document so two runs can be compared. */
    private Map<OccurrenceId, Integer> ordinalsOf(DocumentClusters clusters, RunId run) {
        return clusters.forRun(run).stream()
                .collect(Collectors.toMap(
                        DocumentCluster::occurrenceId, DocumentCluster::clusterOrdinal, (a, b) -> a, LinkedHashMap::new));
    }

    /** The ordinals the documents of one named group landed on, by the path they were inserted under. */
    private List<Integer> ordinalsOfGroup(Map<OccurrenceId, Integer> ordinals, String group) {
        Map<Long, String> paths = jdbcTemplate
                .query(
                        "SELECT id, path FROM file_occurrence",
                        (resultSet, rowNumber) -> Map.entry(resultSet.getLong("id"), resultSet.getString("path")))
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return ordinals.entrySet().stream()
                .filter(member -> paths.get(member.getKey().value()).startsWith(group))
                .map(Map.Entry::getValue)
                .toList();
    }

    /** {@link #GROUP_SIZE} documents pointing the same way, each scored to {@code seed}. */
    private Map<OccurrenceId, String> insertGroup(
            long walkId, RunId run, OccurrenceId seed, String group, int axis) {
        return insertGroup(walkId, run, seed, group, axis, GROUP_SIZE);
    }

    /**
     * {@code size} documents whose vectors point along {@code axis}, so documents of one group
     * resemble each other and barely resemble the other group's.
     */
    private Map<OccurrenceId, String> insertGroup(
            long walkId, RunId run, OccurrenceId seed, String group, int axis, int size) {
        Map<OccurrenceId, String> members = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            String path = group + "-" + i + ".txt";
            String contentHash = group + "-" + i;
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

    /**
     * {@code size} documents pointing in {@code size} different directions, so that every pair of them
     * is exactly as unalike as every other — the closest a fixture gets to "nothing resembles anything".
     */
    private Map<OccurrenceId, String> insertMutuallyDistant(
            long walkId, RunId run, OccurrenceId seed, int size) {
        Map<OccurrenceId, String> members = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            String contentHash = "distant-" + i;
            OccurrenceId occurrenceId = insertOccurrence(walkId, "distant-" + i + ".txt");
            float[] vector = new float[size];
            vector[i] = 1f;
            new VectorCache(jdbcTemplate)
                    .put(
                            contentHash,
                            CHUNKER_IDENTITY,
                            CHUNKING_RULE_IDENTITY,
                            0,
                            "model=" + MODEL + ";digest=d34db33f;dtype=F16;dimension=" + size + ";instruction=none",
                            vector);
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
