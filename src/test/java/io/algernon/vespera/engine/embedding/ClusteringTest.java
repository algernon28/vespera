package io.algernon.vespera.engine.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.WholeRun;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.RunId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * One topic, clustered end to end over the stored vectors (ADR-087, ADR-045, ADR-409 section 8): the graph, the
 * communities and the rows, composed the way stage 5's own step composes them. The topic is under the subtopic
 * floor, so it is clustered whole, as a seed partition was, except where a test lowers the floor to divide it
 * (ADR-406, ADR-416 section 5).
 *
 * <p><b>The count is never supplied.</b> The fixtures below say how the documents resemble each
 * other and nothing says how many clusters to find — which is the property that lets a topic of
 * eleven documents and one of eleven thousand share this code path.
 *
 * <p><b>Clusters of twenty, not of three.</b> k is fifteen and there is no edge similarity floor, so in
 * a topic of a dozen documents every document keeps every other and the graph is complete
 * whatever the vectors say. A fixture small enough to check by hand would therefore check nothing:
 * the structure only becomes visible once each cluster is larger than k, which is what these topics
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
@Link(name = "ADR-409", url = Adr.TOPICS_AND_ALL_ARRANGED_UNDER_THEM_ARE_KEPT_IN_TABLES_KEYED_BY_THE_TOPIC, type = "adr")
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

    /**
     * The subtopic floor the application ships (vespera.clustering.subtopic-floor): far above the forty documents
     * clustered here, so the topic is not divided and its clusters are found over it whole (ADR-406 section 1).
     */
    private static final int SUBTOPIC_FLOOR = 300;

    /** A subtopic floor the thirty-six documents of the divided topic reach, so it is divided (ADR-406 section 1). */
    private static final int A_LOW_FLOOR = 2;

    /** How far each document of a tight group leans off its axis, a step a document. */
    private static final float TIGHT = 0.01f;

    /** How far each document of a loose group leans off its axis: three times a tight one's. */
    private static final float LOOSE = 0.03f;

    /**
     * A loose group's size: other than a tight one's, so the two subtopics are not of as many, and k + 1, so each of
     * its documents keeps every other and the group stays one community.
     */
    private static final int LOOSE_SIZE = NEIGHBOURS + 1;

    /** The topic every document here is placed under (ADR-409 section 1). */
    private static final TopicKey THE_TOPIC = new TopicKey(new SeedPassage("exemplar.pdf", 0));

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Every survivor lands in exactly one cluster")
    @DisplayName("Two groups of documents become two clusters, and every document is in one of them")
    void clustersATopicWithoutBeingToldHowManyClustersToFind() {
        Clustering clustering = ClusteringBeans.real(jdbcTemplate);
        DocumentClusters clusters = new DocumentClusters(jdbcTemplate);
        long walkId = insertWalk("C:/two-groups");
        RunId run = insertRun(walkId, "clustering-test-two-groups");
        OccurrenceId seed = insertOccurrence(walkId, "seeds/exemplar.pdf");
        Map<OccurrenceId, String> members = new LinkedHashMap<>();
        members.putAll(insertCluster(walkId, run, seed, "alike", 0));
        members.putAll(insertCluster(walkId, run, seed, "unalike", 1));

        clustering.clusterAndRecord(
                run, THE_TOPIC, members, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, EMBEDDER_IDENTITY, NEIGHBOURS,
                RESOLUTION, SUBTOPIC_FLOOR);

        Map<OccurrenceId, Integer> ordinals = ordinalsOf(clusters, run);
        claim(
                "every one of the " + members.size() + " documents in the topic carries a cluster,"
                        + " so membership is total: a survivor with no cluster is a hole in the page tree"
                        + " above it",
                () -> assertThat(ordinals.keySet()).containsExactlyInAnyOrderElementsOf(members.keySet()));
        claim(
                "and each carries exactly one, so membership is disjoint -- a document lands in one"
                        + " cluster rather than being listed under several",
                () -> assertThat(WholeRun.membership(jdbcTemplate,run)).hasSize(members.size()));
        claim(
                "the two groups came out as two clusters, and nothing was told there were two: the count"
                        + " is a property of how the documents resemble each other, which is what lets a"
                        + " topic of eleven and one of eleven thousand share this code path",
                () -> assertThat(clusters.sizesFor(run, THE_TOPIC)).containsExactly(CLUSTER_SIZE, CLUSTER_SIZE));
        claim(
                "and the documents of one group are together rather than split across the two clusters,"
                        + " so the grouping followed the vectors and not the order the rows arrived in",
                () -> assertThat(ordinalsOfCluster(ordinals, "alike")).containsOnly(0));
    }

    @Test
    @Story("The same corpus always produces the same clusters")
    @DisplayName("Clustering the same topic twice gives every document the same cluster ordinal")
    void isDeterministicAcrossRuns() {
        Clustering clustering = ClusteringBeans.real(jdbcTemplate);
        DocumentClusters clusters = new DocumentClusters(jdbcTemplate);
        long walkId = insertWalk("C:/twice");
        RunId first = insertRun(walkId, "clustering-test-first-run");
        RunId second = insertRun(walkId, "clustering-test-second-run");
        OccurrenceId seed = insertOccurrence(walkId, "seeds/exemplar.pdf");
        Map<OccurrenceId, String> members = new LinkedHashMap<>();
        members.putAll(insertCluster(walkId, first, seed, "alike", 0));
        members.putAll(insertCluster(walkId, first, seed, "unalike", 1));
        members.keySet().forEach(member -> insertScore(second, member, seed));

        clustering.clusterAndRecord(
                first, THE_TOPIC, members, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, EMBEDDER_IDENTITY, NEIGHBOURS,
                RESOLUTION, SUBTOPIC_FLOOR);
        clustering.clusterAndRecord(
                second, THE_TOPIC, members, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, EMBEDDER_IDENTITY, NEIGHBOURS,
                RESOLUTION, SUBTOPIC_FLOOR);

        claim(
                "every document landed on the same ordinal both times, not merely in the same company:"
                        + " an ordinal is part of a cluster's identity, and a page tree that reshuffled"
                        + " when nothing changed would be worse than a threshold that did, because a"
                        + " person has already reviewed it",
                () -> assertThat(ordinalsOf(clusters, second)).isEqualTo(ordinalsOf(clusters, first)));
        claim(
                "and the second run wrote its own rows beside the first's rather than editing them"
                        + " (ADR-077), so what an earlier run recorded is still readable",
                () -> assertThat(WholeRun.membership(jdbcTemplate,first)).hasSize(members.size()));
    }

    @Test
    @Issue("834")
    @Link(name = "ADR-406", url = Adr.A_CROWDED_TOPIC_IS_DIVIDED_INTO_SUBTOPICS, type = "adr")
    @Link(name = "ADR-416", url = Adr.THE_TOPICS_ANALYSIS_REPORT_LISTS_WHAT_FEEDS_EACH_TOPIC, type = "adr")
    @Story("Each subtopic's row of the group sizes shows the spread of its own graph")
    @DisplayName("A divided topic gives each subtopic, by its number, the spread its own documents give alone")
    void eachSubtopicsSpreadIsTheOneOfItsOwnGraph() {
        Clustering clustering = ClusteringBeans.real(jdbcTemplate);
        DocumentClusters clusters = new DocumentClusters(jdbcTemplate);
        long walkId = insertWalk("C:/two-subtopics");
        RunId divided = insertRun(walkId, "clustering-test-divided");
        RunId tightAlone = insertRun(walkId, "clustering-test-tight-alone");
        RunId looseAlone = insertRun(walkId, "clustering-test-loose-alone");
        OccurrenceId seed = insertOccurrence(walkId, "seeds/exemplar.pdf");
        Map<OccurrenceId, String> tight = insertCluster(walkId, divided, seed, "tight", 0, CLUSTER_SIZE, TIGHT);
        Map<OccurrenceId, String> loose = insertCluster(walkId, divided, seed, "loose", 1, LOOSE_SIZE, LOOSE);
        Map<OccurrenceId, String> members = new LinkedHashMap<>();
        members.putAll(tight);
        members.putAll(loose);
        tight.keySet().forEach(member -> insertScore(tightAlone, member, seed));
        loose.keySet().forEach(member -> insertScore(looseAlone, member, seed));

        Clustering.Spreads spreads = clustering.clusterAndRecordWithSubtopicSpreads(
                divided, THE_TOPIC, members, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, EMBEDDER_IDENTITY, NEIGHBOURS,
                RESOLUTION, A_LOW_FLOOR, ClusteringProgress.NONE);
        Optional<RetainedEdgeSpread> tightsOwn = clustering.clusterAndRecord(
                tightAlone, THE_TOPIC, tight, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, EMBEDDER_IDENTITY, NEIGHBOURS,
                RESOLUTION, SUBTOPIC_FLOOR);
        Optional<RetainedEdgeSpread> loosesOwn = clustering.clusterAndRecord(
                looseAlone, THE_TOPIC, loose, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, EMBEDDER_IDENTITY, NEIGHBOURS,
                RESOLUTION, SUBTOPIC_FLOOR);
        Map<OccurrenceId, Integer> subtopicOf = clusters.membersOf(divided, THE_TOPIC).stream()
                .filter(member -> member.subtopic().isPresent())
                .collect(Collectors.toMap(DocumentCluster::occurrenceId, member -> member.subtopic().getAsInt()));
        Set<Integer> tightsSubtopics = tight.keySet().stream().map(subtopicOf::get).collect(Collectors.toSet());
        Set<Integer> loosesSubtopics = loose.keySet().stream().map(subtopicOf::get).collect(Collectors.toSet());

        claim("the topic of " + members.size() + " documents, at a subtopic floor of " + A_LOW_FLOOR + ", is divided"
                        + " into two subtopics, the " + CLUSTER_SIZE + " tight documents all in one and the "
                        + LOOSE_SIZE + " loose ones all in the other, each with a spread returned by its number",
                () -> {
                    assertThat(subtopicOf).as("every document's subtopic").hasSize(members.size());
                    assertThat(tightsSubtopics).as("the tight documents' subtopics").hasSize(1);
                    assertThat(loosesSubtopics).as("the loose documents' subtopics").hasSize(1)
                            .doesNotContainAnyElementsOf(tightsSubtopics);
                    assertThat(spreads.subtopics()).as("the subtopics' spreads, by number")
                            .containsOnlyKeys(0, 1);
                });
        int tightsSubtopic = tightsSubtopics.iterator().next();
        int loosesSubtopic = loosesSubtopics.iterator().next();
        claim("the two groups' own graphs keep edges of different spreads, so a spread handed to the wrong subtopic"
                        + " would show",
                () -> assertThat(tightsOwn)
                        .as("the tight documents' spread clustered alone")
                        .isPresent()
                        .isNotEqualTo(loosesOwn));
        claim("subtopic " + tightsSubtopic + ", the tight documents', carries the spread their own graph gives,"
                        + " the one they give clustered alone (ADR-416 section 5)",
                () -> assertThat(spreads.subtopics().get(tightsSubtopic))
                        .as("subtopic %d's spread", tightsSubtopic)
                        .isEqualTo(tightsOwn));
        claim("and subtopic " + loosesSubtopic + ", the loose documents', carries theirs",
                () -> assertThat(spreads.subtopics().get(loosesSubtopic))
                        .as("subtopic %d's spread", loosesSubtopic)
                        .isPresent()
                        .isEqualTo(loosesOwn));
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
        return insertCluster(walkId, run, seed, cluster, axis, CLUSTER_SIZE, TIGHT);
    }

    /**
     * {@code size} documents whose vectors point along {@code axis}, so documents of one cluster
     * resemble each other and barely resemble the other cluster's; the {@code i}th leans {@code scatter} times
     * {@code i + 1} towards the last component, so a larger {@code scatter} makes the documents less alike.
     */
    private Map<OccurrenceId, String> insertCluster(
            long walkId, RunId run, OccurrenceId seed, String cluster, int axis, int size, float scatter) {
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
            vector[DIMENSION - 1] = scatter * (i + 1);
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
