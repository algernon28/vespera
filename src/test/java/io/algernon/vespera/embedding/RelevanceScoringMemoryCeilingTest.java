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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.ActiveProfiles;

/**
 * ADR-085's memory contract, on a partition too large for the disallowed alternative (#108): a
 * materialised matrix — every corpus survivor's vectors held resident at once, the mistake ADR-085
 * measured at 3.1 GiB per 200,000 chunks with no relief from a smaller dimension.
 *
 * <p><b>Asserted on the shape of memory use, not on a byte count.</b> ADR-085 itself explains why a
 * raw measurement does not belong in a committed test: "a throughput measurement from one machine is
 * a fact about that machine — useful for choosing a design, misleading if committed as a test that
 * would fail on different hardware." What is asserted instead is the invariant a materialised matrix
 * would violate: no single read from the vector table ever returns more than one document's own
 * chunks, however many survivors the partition holds. A corpus-wide {@code SELECT} would return one
 * gigantic batch, once; streaming returns one small batch, {@link #CORPUS_DOCUMENT_COUNT} times. The
 * partition size below is chosen to make that distinction obvious, not to threaten this JVM's heap —
 * the point survives at any scale, which is the argument for testing the shape rather than the bytes.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Relevance")
@Feature("Relevance scoring")
@Issue("108")
@Link(name = "ADR-085", url = Adr.VECTORS_LIVE_IN_SQLITE, type = "adr")
class RelevanceScoringMemoryCeilingTest {

    private static final int CORPUS_DOCUMENT_COUNT = 20_000;
    private static final int SEED_DOCUMENT_COUNT = 4;
    private static final int CHUNKS_PER_SEED_DOCUMENT = 10;
    private static final int DIMENSION = 4;
    private static final String MODEL = "qwen3-embedding:0.6b";
    private static final String CHUNKER_IDENTITY = "docling-hybrid-chunker-v1";
    private static final String CHUNKING_RULE_IDENTITY = "words-512-v1";
    private static final String EMBEDDER_IDENTITY_SUFFIX =
            ";digest=d34db33f;dtype=F16;dimension=" + DIMENSION + ";instruction=none";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("The corpus streams one survivor at a time, never all of it at once")
    @DisplayName(
            "Scoring 20,000 corpus survivors against a resident seed reads one document's own chunks"
                    + " per query, never the whole partition")
    void scoresALargePartitionWithoutEverReadingMoreThanOneDocumentsChunksAtOnce() {
        RowCountingJdbcTemplate counting = new RowCountingJdbcTemplate(jdbcTemplate);
        VectorCache vectorCache = new VectorCache(counting);
        RelevanceScoreCache scoreCache = new RelevanceScoreCache(counting);
        RelevanceScoring relevanceScoring = new RelevanceScoring(vectorCache, new RelevanceScorer(), scoreCache);

        long walkId = insertWalk();
        RunId runId = insertRun(walkId);
        Map<OccurrenceId, String> seedContentHashes = insertSeeds(walkId);
        List<OccurrenceId> corpusOccurrences = insertCorpusDocuments(walkId);

        Map<OccurrenceId, List<float[]>> residentSeedVectors = relevanceScoring.residentSeedVectors(
                seedContentHashes, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, MODEL);
        for (int i = 0; i < corpusOccurrences.size(); i++) {
            relevanceScoring.scoreAndRecord(
                    corpusOccurrences.get(i),
                    runId,
                    "corpus-" + i,
                    CHUNKER_IDENTITY,
                    CHUNKING_RULE_IDENTITY,
                    MODEL,
                    residentSeedVectors);
        }

        claim(
                "the resident seed side holds every one of the " + SEED_DOCUMENT_COUNT
                        + " seed documents' chunks",
                () -> assertThat(residentSeedVectors).hasSize(SEED_DOCUMENT_COUNT));
        claim(
                "all " + CORPUS_DOCUMENT_COUNT + " survivors were scored -- the partition was not too"
                        + " large to finish, only too large to materialise",
                () -> assertThat(relevanceScoreRowCount()).isEqualTo(CORPUS_DOCUMENT_COUNT));
        claim(
                "no single read from the vector table ever returned more than one seed document's own"
                        + " chunks (" + CHUNKS_PER_SEED_DOCUMENT + ") -- never the " + CORPUS_DOCUMENT_COUNT
                        + "-document partition a materialised matrix would have pulled back in one go",
                () -> assertThat(counting.maxRowsInOneQuery()).isEqualTo(CHUNKS_PER_SEED_DOCUMENT));
    }

    private long relevanceScoreRowCount() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM relevance_score", Long.class);
        return count == null ? 0 : count;
    }

    private long insertWalk() {
        jdbcTemplate.update("INSERT INTO walk (root, finished) VALUES (?, 1)", "C:/corpus");
        Long id = jdbcTemplate.queryForObject("SELECT id FROM walk ORDER BY id DESC LIMIT 1", Long.class);
        return id;
    }

    private RunId insertRun(long walkId) {
        String runId = "relevance-scoring-memory-ceiling-test-run";
        jdbcTemplate.update(
                "INSERT INTO run (id, stage, implementation_version, config_consumed, walk_id)"
                        + " VALUES (?, 'embedding-scoring', 'test', '{}', ?)",
                runId,
                walkId);
        return new RunId(runId);
    }

    /** {@link #SEED_DOCUMENT_COUNT} seed occurrences, each with {@link #CHUNKS_PER_SEED_DOCUMENT} resident chunks. */
    private Map<OccurrenceId, String> insertSeeds(long walkId) {
        Map<OccurrenceId, String> seedContentHashes = new LinkedHashMap<>();
        for (int seed = 0; seed < SEED_DOCUMENT_COUNT; seed++) {
            OccurrenceId occurrenceId = insertFileOccurrence(walkId, "seed-" + seed + ".txt");
            String contentHash = "seed-" + seed;
            seedContentHashes.put(occurrenceId, contentHash);
            for (int ordinal = 0; ordinal < CHUNKS_PER_SEED_DOCUMENT; ordinal++) {
                insertVector(contentHash, ordinal, similarityFingerprint(0.5f));
            }
        }
        return seedContentHashes;
    }

    /** {@link #CORPUS_DOCUMENT_COUNT} corpus survivors, each with exactly one of its own chunks. */
    private List<OccurrenceId> insertCorpusDocuments(long walkId) {
        List<OccurrenceId> occurrences = new ArrayList<>(CORPUS_DOCUMENT_COUNT);
        List<Object[]> vectorRows = new ArrayList<>(CORPUS_DOCUMENT_COUNT);
        for (int i = 0; i < CORPUS_DOCUMENT_COUNT; i++) {
            occurrences.add(insertFileOccurrence(walkId, "corpus-" + i + ".txt"));
            vectorRows.add(new Object[] {
                "corpus-" + i,
                CHUNKER_IDENTITY,
                CHUNKING_RULE_IDENTITY,
                0,
                "model=" + MODEL + EMBEDDER_IDENTITY_SUFFIX,
                littleEndianFloat32(similarityFingerprint(0.5f))
            });
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO vector"
                        + " (content_hash, chunker_identity, chunking_rule_identity, ordinal, embedder_identity, embedding)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                vectorRows);
        return occurrences;
    }

    private OccurrenceId insertFileOccurrence(long walkId, String path) {
        jdbcTemplate.update(
                "INSERT INTO file_occurrence (walk_id, path, size_bytes, last_modified, creation_time)"
                        + " VALUES (?, ?, 1, ?, ?)",
                walkId,
                path,
                Instant.EPOCH.toString(),
                Instant.EPOCH.toString());
        Long id = jdbcTemplate.queryForObject("SELECT id FROM file_occurrence ORDER BY id DESC LIMIT 1", Long.class);
        return new OccurrenceId(id);
    }

    private void insertVector(String contentHash, int ordinal, float[] vector) {
        jdbcTemplate.update(
                "INSERT INTO vector"
                        + " (content_hash, chunker_identity, chunking_rule_identity, ordinal, embedder_identity, embedding)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                contentHash,
                CHUNKER_IDENTITY,
                CHUNKING_RULE_IDENTITY,
                ordinal,
                "model=" + MODEL + EMBEDDER_IDENTITY_SUFFIX,
                littleEndianFloat32(vector));
    }

    /** Any fixed-shape vector: what these vectors compare to is not this test's claim, only how many rows come back. */
    private static float[] similarityFingerprint(float value) {
        float[] vector = new float[DIMENSION];
        vector[0] = value;
        return vector;
    }

    private static byte[] littleEndianFloat32(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float component : vector) {
            buffer.putFloat(component);
        }
        return buffer.array();
    }

    /** Tracks the largest single result set any {@code query(...)} call on the delegate returned. */
    private static final class RowCountingJdbcTemplate extends JdbcTemplate {

        private final AtomicInteger maxRowsInOneQuery = new AtomicInteger();

        RowCountingJdbcTemplate(JdbcTemplate delegate) {
            super(delegate.getDataSource());
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            List<T> result = super.query(sql, rowMapper, args);
            maxRowsInOneQuery.updateAndGet(current -> Math.max(current, result.size()));
            return result;
        }

        int maxRowsInOneQuery() {
            return maxRowsInOneQuery.get();
        }
    }
}
