package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.within;

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
 * Scoring and clustering read a document's vectors under the one embedder identity they are handed, and
 * under no other identity of the same embedding model (ADR-228, #488).
 *
 * <p>Each fixture stores a document's chunks twice: under the identity the caller names, and under a second
 * identity of the same model name, as a pull that changed the model leaves them. The second set is chosen so
 * that reading both gives another answer than reading the first alone, or no answer at all.
 *
 * <p><b>What reading both sets gives</b>, measured once on the code before ADR-228, which read by the model's
 * name: the score came out 0.569 where the named identity's own is 0.707; the identity of two components
 * stopped scoring with an {@code ArrayIndexOutOfBoundsException}; and the twenty documents built alike were
 * split between two clusters. ADR-228's Context records it.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Relevance")
@Feature("Embedder identity")
@Issue("488")
@Link(name = "ADR-228", url = Adr.A_SCORING_RUN_NAMES_THE_EMBEDDING_MODELS_ARTEFACT_AND_READS_ONE_IDENTITY, type = "adr")
class ARunReadsTheVectorsOfOneEmbedderIdentityTest {

    private static final String MODEL = "qwen3-embedding:0.6b";
    private static final String CHUNKER_IDENTITY = "docling-hybrid-chunker-v1";
    private static final String CHUNKING_RULE_IDENTITY = "words-512-v1";
    private static final String DIGEST = "d34db33f";
    private static final String AN_EARLIER_PULLS_DIGEST = "0ff0d34d";
    private static final String DTYPE = "F16";

    /** How many components the vectors of the identity the caller names have. */
    private static final int DIMENSION = 4;

    /** Fewer components than {@link #DIMENSION}, for an identity that differs in nothing else. */
    private static final int A_NARROWER_DIMENSION = 2;

    /** The identity scoring and clustering are handed: the one a run names. */
    private static final String THE_IDENTITY_NAMED = identity(DIGEST, DIMENSION);

    /** A second identity of the same model, differing in the digest, as an earlier pull's vectors carry. */
    private static final String AN_EARLIER_PULLS_IDENTITY = identity(AN_EARLIER_PULLS_DIGEST, DIMENSION);

    /** A second identity of the same model, digest and dtype, differing in the dimension alone. */
    private static final String A_NARROWER_IDENTITY = identity(DIGEST, A_NARROWER_DIMENSION);

    /** What scoring and clustering are asked to read under. */
    private static final String READ_UNDER = THE_IDENTITY_NAMED;

    /** The cosine of a vector along one axis with a vector along that axis and one other: one over root two. */
    private static final double THE_NAMED_IDENTITYS_SCORE = 1 / Math.sqrt(2);

    private static final double A_ROUNDING_ERROR = 1e-6;

    /** Larger than the fifteen neighbours a document keeps, so that the vectors decide the clusters. */
    private static final int CLUSTER_SIZE = 20;

    /** Large beside the named identity's vectors of length about one, so that a mean taken over both follows it. */
    private static final float FAR_LARGER = 100f;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A document is scored on the vectors of one pull of the embedding model")
    @DisplayName("A score is computed from the vectors stored under the identity named, though the same document has vectors under another")
    void aScoreIsComputedFromTheNamedIdentitysVectorsOnly() {
        long walkId = insertWalk("C:/one-identity-scored");
        RunId run = insertRun(walkId, "one-identity-scored");
        OccurrenceId seed = insertOccurrence(walkId, "seeds/exemplar.pdf");
        OccurrenceId survivor = insertOccurrence(walkId, "a-document.txt");
        store("seed", THE_IDENTITY_NAMED, 1f, 1f, 0f, 0f);
        store("survivor", THE_IDENTITY_NAMED, 1f, 0f, 0f, 0f);
        // Under the earlier pull the two are the same vector, so a score that read it would be raised.
        store("seed", AN_EARLIER_PULLS_IDENTITY, 0f, 0f, 1f, 0f);
        store("survivor", AN_EARLIER_PULLS_IDENTITY, 0f, 0f, 1f, 0f);

        score(run, seed, survivor);

        claim(
                "the score is the one the named identity's two vectors give, one over the square root of two:"
                        + " the vectors the same two documents carry under the earlier pull, which are alike,"
                        + " were not read with them, so the score is on one scale",
                () -> assertThat(scoreOf(run, survivor)).isCloseTo(THE_NAMED_IDENTITYS_SCORE, within(A_ROUNDING_ERROR)));
    }

    @Test
    @Story("A document is scored on the vectors of one pull of the embedding model")
    @DisplayName("Vectors of another length under the same model are not read with the identity named, and scoring does not stop on them")
    void vectorsOfAnotherDimensionAreNotReadWithTheNamedIdentitys() {
        long walkId = insertWalk("C:/two-dimensions-scored");
        RunId run = insertRun(walkId, "two-dimensions-scored");
        OccurrenceId seed = insertOccurrence(walkId, "seeds/exemplar.pdf");
        OccurrenceId survivor = insertOccurrence(walkId, "a-document.txt");
        store("seed", THE_IDENTITY_NAMED, 1f, 1f, 0f, 0f);
        store("survivor", THE_IDENTITY_NAMED, 1f, 0f, 0f, 0f);
        store("seed", A_NARROWER_IDENTITY, 0f, 1f);
        store("survivor", A_NARROWER_IDENTITY, 0f, 1f);

        claim(
                "scoring goes through: a vector of " + A_NARROWER_DIMENSION + " components stored for the same"
                        + " document is never compared with one of " + DIMENSION + ", which would read past its end",
                () -> assertThatCode(() -> score(run, seed, survivor)).doesNotThrowAnyException());
        claim(
                "and the score is the one the named identity's two vectors give, with nothing of the shorter"
                        + " vectors in it",
                () -> assertThat(scoreOf(run, survivor)).isCloseTo(THE_NAMED_IDENTITYS_SCORE, within(A_ROUNDING_ERROR)));
    }

    @Test
    @Story("Documents are grouped on the vectors of one pull of the embedding model")
    @DisplayName("Documents are grouped by the vectors stored under the identity named, though each has vectors under another that would group them otherwise")
    void documentsAreClusteredByTheNamedIdentitysVectorsOnly() {
        Clustering clustering = ClusteringBeans.real(jdbcTemplate);
        long walkId = insertWalk("C:/one-identity-clustered");
        RunId run = insertRun(walkId, "one-identity-clustered");
        OccurrenceId seed = insertOccurrence(walkId, "seeds/exemplar.pdf");
        Map<OccurrenceId, String> alike = insertCluster(walkId, run, seed, "alike", 0);
        Map<OccurrenceId, String> unalike = insertCluster(walkId, run, seed, "unalike", 1);
        Map<OccurrenceId, String> partition = new LinkedHashMap<>(alike);
        partition.putAll(unalike);

        clustering.clusterAndRecord(run, seed, partition, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, READ_UNDER);

        Map<OccurrenceId, Integer> ordinals = WholeRun.membership(jdbcTemplate, run).stream()
                .collect(Collectors.toMap(DocumentCluster::occurrenceId, DocumentCluster::clusterOrdinal));
        claim(
                "all " + partition.size() + " documents were grouped",
                () -> assertThat(ordinals.keySet()).containsExactlyInAnyOrderElementsOf(partition.keySet()));
        claim(
                "the " + CLUSTER_SIZE + " documents built alike under the named identity are in one group: the"
                        + " vectors they carry under the earlier pull, which set every second one of them apart"
                        + " and are far larger, took no part in the mean each document is placed by",
                () -> assertThat(ordinalsOf(ordinals, alike.keySet())).hasSize(1));
        claim(
                "and the " + CLUSTER_SIZE + " built unalike to them are in one other group",
                () -> assertThat(ordinalsOf(ordinals, unalike.keySet()))
                        .hasSize(1)
                        .doesNotContainAnyElementsOf(ordinalsOf(ordinals, alike.keySet())));
    }

    /**
     * ADR-230 clusters a partition again under the scoring run it was clustered under. Here the first pass
     * is made while each document has one identity's vectors, the second after a second identity's are
     * stored, and both are handed the first identity, as a run that names its digest hands it (ADR-228).
     */
    @Test
    @Story("Documents are grouped on the vectors of one pull of the embedding model")
    @DisplayName("Grouping the same documents again after vectors under another identity were stored puts every document where it was")
    @Issue("489")
    void clusteringAgainAfterASecondIdentityIsStoredReadsTheSameVectors() {
        Clustering clustering = ClusteringBeans.real(jdbcTemplate);
        long walkId = insertWalk("C:/clustered-twice");
        RunId first = insertRun(walkId, "clustered-before-the-second-identity");
        RunId again = insertRun(walkId, "clustered-after-the-second-identity");
        OccurrenceId seed = insertOccurrence(walkId, "seeds/exemplar.pdf");
        Map<OccurrenceId, String> partition = new LinkedHashMap<>();
        for (int axis = 0; axis < 2; axis++) {
            for (int i = 0; i < CLUSTER_SIZE; i++) {
                String contentHash = "twice-" + axis + "-" + i;
                OccurrenceId occurrenceId = insertOccurrence(walkId, contentHash + ".txt");
                float[] named = new float[DIMENSION];
                named[axis] = 1f;
                named[DIMENSION - 1] = 0.01f * (i + 1);
                store(contentHash, THE_IDENTITY_NAMED, named);
                new RelevanceScoreCache(jdbcTemplate).record(occurrenceId, first, new RelevanceScore(0.5, seed));
                new RelevanceScoreCache(jdbcTemplate).record(occurrenceId, again, new RelevanceScore(0.5, seed));
                partition.put(occurrenceId, contentHash);
            }
        }

        clustering.clusterAndRecord(first, seed, partition, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, READ_UNDER);
        int document = 0;
        for (String contentHash : partition.values()) {
            float[] earlier = new float[DIMENSION];
            earlier[document++ % 2 == 0 ? 2 : 3] = FAR_LARGER;
            store(contentHash, AN_EARLIER_PULLS_IDENTITY, earlier);
        }
        clustering.clusterAndRecord(again, seed, partition, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, READ_UNDER);

        claim(
                "the first pass grouped all " + partition.size() + " documents",
                () -> assertThat(ordinalsUnder(first)).hasSize(partition.size()));
        claim(
                "and the second put each where the first did: the vectors stored between the two, under another"
                        + " identity of the same embedding model, were not read",
                () -> assertThat(ordinalsUnder(again)).isEqualTo(ordinalsUnder(first)));
    }

    private Map<OccurrenceId, Integer> ordinalsUnder(RunId run) {
        return WholeRun.membership(jdbcTemplate, run).stream()
                .collect(Collectors.toMap(DocumentCluster::occurrenceId, DocumentCluster::clusterOrdinal));
    }

    private void score(RunId run, OccurrenceId seed, OccurrenceId survivor) {
        RelevanceScoring scoring = new RelevanceScoring(
                new VectorCache(jdbcTemplate), new RelevanceScorer(), new RelevanceScoreCache(jdbcTemplate));
        Map<OccurrenceId, String> seeds = new LinkedHashMap<>();
        seeds.put(seed, "seed");
        Map<OccurrenceId, List<float[]>> resident =
                scoring.residentSeedVectors(seeds, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, READ_UNDER);
        scoring.scoreAndRecord(
                survivor, run, "survivor", CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, READ_UNDER, resident);
    }

    private double scoreOf(RunId run, OccurrenceId survivor) {
        return new RelevanceScoreCache(jdbcTemplate)
                .forOccurrence(survivor, run)
                .orElseThrow(() -> new AssertionError("no score was recorded for the document"))
                .score();
    }

    private static Set<Integer> ordinalsOf(Map<OccurrenceId, Integer> ordinals, Set<OccurrenceId> documents) {
        return documents.stream().map(ordinals::get).collect(Collectors.toSet());
    }

    /**
     * {@link #CLUSTER_SIZE} documents whose vectors under the named identity point along {@code axis}, as
     * {@code ClusteringTest} builds them, each with a second vector under the earlier pull's identity that
     * points one way for every even document and another for every odd one.
     */
    private Map<OccurrenceId, String> insertCluster(long walkId, RunId run, OccurrenceId seed, String cluster, int axis) {
        Map<OccurrenceId, String> members = new LinkedHashMap<>();
        for (int i = 0; i < CLUSTER_SIZE; i++) {
            String contentHash = cluster + "-" + i;
            OccurrenceId occurrenceId = insertOccurrence(walkId, contentHash + ".txt");
            float[] named = new float[DIMENSION];
            named[axis] = 1f;
            // Distinct per document, so that no two are identical and no tie is broken by index.
            named[DIMENSION - 1] = 0.01f * (i + 1);
            store(contentHash, THE_IDENTITY_NAMED, named);
            float[] earlier = new float[DIMENSION];
            earlier[i % 2 == 0 ? 2 : 3] = FAR_LARGER;
            store(contentHash, AN_EARLIER_PULLS_IDENTITY, earlier);
            new RelevanceScoreCache(jdbcTemplate).record(occurrenceId, run, new RelevanceScore(0.5, seed));
            members.put(occurrenceId, contentHash);
        }
        return members;
    }

    /** One chunk's vector for {@code contentHash} under {@code embedderIdentity}. */
    private void store(String contentHash, String embedderIdentity, float... vector) {
        new VectorCache(jdbcTemplate)
                .put(contentHash, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, 0, embedderIdentity, vector);
    }

    private static String identity(String digest, int dimension) {
        return EmbedderIdentity.withoutInstruction(MODEL, digest, DTYPE, dimension).value();
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
}
