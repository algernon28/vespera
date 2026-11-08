package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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

    /** The number of similarities the application ships (vespera.relevance.top-similarities). */
    private static final int TOP_SIMILARITIES = 3;

    private static final String MODEL = "a-comparing-model:0.6b";

    /** The survivor here is one chunk long, and each identity stores one vector for it. */
    private static final int ONE_CHUNK = 1;
    private static final String CHUNKER_IDENTITY = "docling-hybrid-chunker-v1";
    private static final String CHUNKING_RULE_IDENTITY = "words-512-v1";
    private static final String DIGEST = "d34db33f";
    private static final String AN_EARLIER_PULLS_DIGEST = "0ff0d34d";
    private static final String DTYPE = "F16";

    /** How many components the vectors of the identity the caller names have. */
    private static final int DIMENSION = 4;

    /** The identity scoring and clustering are handed: the one a run names. */
    private static final String THE_IDENTITY_NAMED = identity(DIGEST, DIMENSION);

    /** A second identity of the same model, differing in the digest, as an earlier pull's vectors carry. */
    private static final String AN_EARLIER_PULLS_IDENTITY = identity(AN_EARLIER_PULLS_DIGEST, DIMENSION);

    /** What scoring and clustering are asked to read under. */
    private static final String READ_UNDER = THE_IDENTITY_NAMED;

    /** The cosine of a vector along one axis with a vector along that axis and one other: one over root two. */
    private static final double THE_NAMED_IDENTITYS_SCORE = 1 / Math.sqrt(2);

    private static final double A_ROUNDING_ERROR = 1e-6;

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

    private void score(RunId run, OccurrenceId seed, OccurrenceId survivor) {
        RelevanceScoring scoring = new RelevanceScoring(
                new VectorCache(jdbcTemplate), new RelevanceScorer(), new RelevanceScoreCache(jdbcTemplate));
        Map<OccurrenceId, SeedChunks> seeds = new LinkedHashMap<>();
        seeds.put(seed, new SeedChunks("seed", 1));
        Map<OccurrenceId, List<float[]>> resident =
                scoring.residentSeedVectors(seeds, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, READ_UNDER);
        scoring.scoreAndRecord(
                survivor, run, "survivor", ONE_CHUNK, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, READ_UNDER, resident, TOP_SIMILARITIES);
    }

    private double scoreOf(RunId run, OccurrenceId survivor) {
        return new RelevanceScoreCache(jdbcTemplate)
                .forOccurrence(survivor, run)
                .orElseThrow(() -> new AssertionError("no score was recorded for the document"))
                .score();
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
