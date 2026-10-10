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
import java.util.ArrayList;
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
 * The contract of {@code ClusteringProgress} (ADR-192 sections 3 and 5, #412): the pass over pairs of blocks in
 * {@code NearestNeighbourGraph.build} calls {@code toCompareBlocks(long)} once, with b(b + 1)/2 for the
 * partition's b blocks, before the first pair, and {@code blockPairCompared()} after each pair of blocks; and
 * {@code Clustering.clusterAndRecord} hands it on once per partition, so {@code pipeline} opens a counter per
 * partition.
 *
 * <p>The graph is built directly over vectors held in the test, with a block size of two, so a partition of
 * five documents is three blocks and six pairs: the fixture the previous draft of ADR-192 found no way to
 * reach through the 4,096-document block a real partition needs.
 *
 * <p><b>Part (c) of ADR-192.</b> Does not compile until {@code ClusteringProgress} and the overloads of {@code
 * build} and {@code clusterAndRecord} exist; part (c) moves it into {@code src/test}.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Relevance")
@Feature("Progress reporting")
@Issue("412")
@Link(name = "ADR-192", url = Adr.EVERY_LOOP_REPORTS_ITS_PROGRESS, type = "adr")
class ClusteringProgressTest {

    private static final String MODEL = "qwen3-embedding:0.6b";
    private static final String CHUNKER_IDENTITY = "docling-hybrid-chunker-v1";
    private static final String CHUNKING_RULE_IDENTITY = "words-512-v1";
    private static final int DIMENSION = 4;
    private static final String EMBEDDER_IDENTITY =
            "model=" + MODEL + ";digest=d34db33f;dtype=F16;dimension=" + DIMENSION + ";instruction=none";

    private static final int NEIGHBOURS = 15;

    /** Five documents in blocks of two: three blocks, so three pairs of a block with itself and three across. */
    private static final int FIVE_DOCUMENTS = 5;

    private static final int BLOCKS_OF_TWO = 2;

    private static final int SIX_PAIRS_OF_BLOCKS = 6;

    /** Three documents in a block of 4,096: one block, compared with itself. */
    private static final int THREE_DOCUMENTS = 3;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Grouping tells its caller how many pairs of blocks it will compare")
    @DisplayName("Five documents in blocks of two are announced as six pairs of blocks, and each pair is reported")
    void threeBlocksAreSixPairs() {
        List<String> events = new ArrayList<>();

        NearestNeighbourGraph.build(vectors(FIVE_DOCUMENTS), NEIGHBOURS, BLOCKS_OF_TWO, recording(events));

        claim(
                "three blocks make three pairs of a block with itself and three of two blocks, b(b + 1)/2 for b of"
                        + " three: announced once with six, before the first, and six reported",
                () -> {
                    assertThat(events.getFirst()).isEqualTo("to-compare " + SIX_PAIRS_OF_BLOCKS);
                    assertThat(events.subList(1, events.size())).hasSize(SIX_PAIRS_OF_BLOCKS).allMatch("compared"::equals);
                });
    }

    @Test
    @Story("Grouping tells its caller how many pairs of blocks it will compare")
    @DisplayName("Documents that fit one block are one pair of blocks")
    void oneBlockIsOnePair() {
        List<String> events = new ArrayList<>();

        NearestNeighbourGraph.build(vectors(THREE_DOCUMENTS), NEIGHBOURS, Clustering.BLOCK_DOCUMENTS, recording(events));

        claim("one block is compared with itself, once", () -> assertThat(events).containsExactly("to-compare 1", "compared"));
    }

    @Test
    @Story("Grouping tells its caller how many pairs of blocks it will compare")
    @DisplayName("Two partitions grouped one after the other are each announced on their own")
    void eachPartitionIsAnnouncedOnItsOwn() {
        Clustering clustering = ClusteringBeans.real(jdbcTemplate);
        long walkId = insertWalk("C:/progress-two-partitions");
        RunId run = insertRun(walkId, "clustering-progress-two-partitions");
        OccurrenceId firstSeed = insertOccurrence(walkId, "seeds/first.pdf");
        OccurrenceId secondSeed = insertOccurrence(walkId, "seeds/second.pdf");
        Map<OccurrenceId, String> first = partition(walkId, run, firstSeed, "first", THREE_DOCUMENTS);
        Map<OccurrenceId, String> second = partition(walkId, run, secondSeed, "second", THREE_DOCUMENTS);
        List<String> events = new ArrayList<>();

        clustering.clusterAndRecord(run, firstSeed, first, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, EMBEDDER_IDENTITY, recording(events));
        clustering.clusterAndRecord(run, secondSeed, second, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, EMBEDDER_IDENTITY, recording(events));

        claim(
                "each partition's pass is announced once with its own total and reports its own pair: the caller"
                        + " is told twice, once for each partition, and not once with a sum",
                () -> assertThat(events).containsExactly("to-compare 1", "compared", "to-compare 1", "compared"));
    }

    @Test
    @Story("Grouping tells its caller how many pairs of blocks it will compare")
    @DisplayName("A partition with no member announces nothing, because it returns before the pass")
    void aPartitionWithNoMemberAnnouncesNothing() {
        Clustering clustering = ClusteringBeans.real(jdbcTemplate);
        long walkId = insertWalk("C:/progress-empty");
        RunId run = insertRun(walkId, "clustering-progress-empty");
        OccurrenceId seed = insertOccurrence(walkId, "seeds/exemplar.pdf");
        List<String> events = new ArrayList<>();

        clustering.clusterAndRecord(
                run, seed, new LinkedHashMap<>(), CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, EMBEDDER_IDENTITY, recording(events));

        claim(
                "no member means no vector width to size a block by: the pass is not reached, so it is not"
                        + " announced, not even with zero",
                () -> assertThat(events).isEmpty());
    }

    /** {@code count} vectors held in the test, each pointing a little differently. */
    private static NearestNeighbourGraph.MeanVectors vectors(int count) {
        List<float[]> all = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            all.add(new float[] {1f, 0.1f * (i + 1), 0f, 0f});
        }
        return new NearestNeighbourGraph.MeanVectors() {
            @Override
            public int count() {
                return all.size();
            }

            @Override
            public List<float[]> block(int from, int size) {
                return all.subList(from, Math.min(from + size, all.size()));
            }
        };
    }

    private Map<OccurrenceId, String> partition(long walkId, RunId run, OccurrenceId seed, String name, int size) {
        Map<OccurrenceId, String> members = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            String contentHash = name + "-" + i;
            OccurrenceId occurrenceId = insertOccurrence(walkId, name + "-" + i + ".txt");
            float[] vector = new float[DIMENSION];
            vector[0] = 1f;
            vector[DIMENSION - 1] = 0.01f * (i + 1);
            new VectorCache(jdbcTemplate)
                    .put(contentHash, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, 0, EMBEDDER_IDENTITY, vector);
            new RelevanceScoreCache(jdbcTemplate).record(occurrenceId, run, new RelevanceScore(0.5, seed));
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

    private static ClusteringProgress recording(List<String> events) {
        return new ClusteringProgress() {
            @Override
            public void toCompareBlocks(long blockPairs) {
                events.add("to-compare " + blockPairs);
            }

            @Override
            public void blockPairCompared() {
                events.add("compared");
            }
        };
    }
}
