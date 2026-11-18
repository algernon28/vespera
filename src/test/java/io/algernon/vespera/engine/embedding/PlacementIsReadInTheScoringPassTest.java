package io.algernon.vespera.engine.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Placement is read in the relevance-scoring pass (ADR-400 §5): {@code RelevanceScoring.scoreAndRecord}, handed a
 * {@link TopicPlacement} and each seed occurrence's path, scores and records the survivor as it does handed none, and
 * returns the placement the scorer's own cosines give, each a seed's occurrence mapped to its path for the passage it
 * is of; and built over the topics {@link BreadthFloor} lets stand, it places under no topic the floor dropped (ADR-401
 * §1).
 *
 * <p>The seeds' chunks are unit vectors along five axes: {@code a.md}'s two along the first and second, {@code b.md}'s
 * two along the third and fourth, and {@code c.md}'s one along the fifth, each seed one topic of its own. The survivor's
 * vectors are stored in the vector table and read by the scoring, as the step reads them.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Relevance")
@Feature("Topics")
@Issue("828")
@Link(name = "ADR-400", url = Adr.THE_WINNING_TOPIC_IS_THE_CENTROID_ITS_CHUNKS_ARE_NEAREST_ON_AVERAGE, type = "adr")
@Link(name = "ADR-401", url = Adr.A_TOPIC_UNDER_THE_BREADTH_FLOOR_IS_DROPPED, type = "adr")
class PlacementIsReadInTheScoringPassTest {

    /** The number of similarities the application ships (vespera.relevance.top-similarities). */
    private static final int TOP_SIMILARITIES = 3;

    /** The share of a survivor's chunks a topic must be nearest to for the survivor to fit it, as shipped. */
    private static final double SHARE = 0.15;

    private static final String CHUNKER_IDENTITY = "docling-hybrid-chunker-v1";
    private static final String CHUNKING_RULE_IDENTITY = "words-512-v1";
    private static final int DIMENSION = 5;
    private static final String READ_UNDER =
            EmbedderIdentity.withoutInstruction("a-comparing-model:0.6b", "d34db33f", "F16", DIMENSION).value();

    private static final String FIRST_SEED = "a.md";
    private static final String SECOND_SEED = "b.md";
    private static final String THIRD_SEED = "c.md";

    private static final Topic FIRST_TOPIC = topic(new SeedPassage(FIRST_SEED, 0), new SeedPassage(FIRST_SEED, 1));
    private static final Topic SECOND_TOPIC = topic(new SeedPassage(SECOND_SEED, 0), new SeedPassage(SECOND_SEED, 1));

    /** One passage of the five: under a floor of {@value #FLOOR}. */
    private static final Topic THIRD_TOPIC = topic(new SeedPassage(THIRD_SEED, 0));

    /** A floor two of five passages reach and one of five does not. */
    private static final double FLOOR = 0.3;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Placement reads the similarities the score already takes")
    @DisplayName("Scoring a document places it from the similarities the score takes, and records the score as it does"
            + " placing nothing")
    void placesTheSurvivorFromTheCosinesTheScoreTakes() {
        Fixture fixture = fixture("placed-in-the-pass");
        // Three chunks along the third axis, nearest the second topic; one midway between the first two axes, at 1 to
        // the first topic's centroid: placed under the second on average, and linked from the first by a quarter.
        List<float[]> survivor = List.of(axis(2), axis(2), axis(2), new float[] {1, 1, 0, 0, 0});
        storeSurvivor("placed-in-the-pass", survivor);
        TopicPlacement placement =
                TopicPlacement.of(List.of(FIRST_TOPIC, SECOND_TOPIC, THIRD_TOPIC), bySeedPath(), SHARE);

        Placement placed = scoring().scoreAndRecord(
                fixture.survivor(), fixture.run(), "placed-in-the-pass", survivor.size(), CHUNKER_IDENTITY,
                CHUNKING_RULE_IDENTITY, READ_UNDER, fixture.resident(), TOP_SIMILARITIES, placement, fixture.paths());

        claim("the survivor is placed under the second topic, which three of its four chunks are nearest to, and linked"
                        + " from the first, which the fourth is nearest to",
                () -> assertThat(placed).isEqualTo(new Placement(SECOND_TOPIC.key(), List.of(FIRST_TOPIC.key()))));
        claim("the score recorded is the one scoring gives the same vectors handed no placement",
                () -> assertThat(new RelevanceScoreCache(jdbcTemplate).forOccurrence(fixture.survivor(), fixture.run()))
                        .contains(new RelevanceScorer().score(survivor, fixture.resident(), TOP_SIMILARITIES)));
    }

    @Test
    @Story("A topic too narrow for the top level is not a topic")
    @DisplayName("Placement built over the topics that stand at the breadth floor places a document nearest a dropped"
            + " topic under one that stands")
    void placesUnderTheTopicsThatStandAlone() {
        Fixture fixture = fixture("nearest-a-dropped-topic");
        // Every chunk along the fifth axis: at 1 to the third topic's one passage, at 0 to every other.
        List<float[]> survivor = List.of(axis(4), axis(4));
        storeSurvivor("nearest-a-dropped-topic", survivor);
        List<Topic> found = List.of(FIRST_TOPIC, SECOND_TOPIC, THIRD_TOPIC);
        StandingTopics standing = new BreadthFloor(FLOOR).of(found, bySeedPath());
        TopicPlacement placement = TopicPlacement.of(standing.topics(), bySeedPath(), SHARE);

        Placement placed = scoring().scoreAndRecord(
                fixture.survivor(), fixture.run(), "nearest-a-dropped-topic", survivor.size(), CHUNKER_IDENTITY,
                CHUNKING_RULE_IDENTITY, READ_UNDER, fixture.resident(), TOP_SIMILARITIES, placement, fixture.paths());

        claim("the third topic, one passage of five, is under the floor of " + FLOOR + " and does not stand",
                () -> assertThat(standing.topics()).containsExactly(FIRST_TOPIC, SECOND_TOPIC));
        claim("the survivor, nearest the dropped topic and at 0 to both that stand, is placed under the one whose key"
                        + " comes first, and linked from no topic: its cosines to the dropped topic's passage count"
                        + " toward none",
                () -> assertThat(placed).isEqualTo(new Placement(FIRST_TOPIC.key(), List.of())));
    }

    /** The occurrences, the run and the resident seed vectors one test scores under. */
    private record Fixture(
            RunId run,
            OccurrenceId survivor,
            Map<OccurrenceId, List<float[]>> resident,
            Map<OccurrenceId, String> paths) {}

    /** A walk and a scoring run named for {@code name}, the three seeds' occurrences and the survivor's. */
    private Fixture fixture(String name) {
        jdbcTemplate.update("INSERT INTO walk (root, finished) VALUES (?, 1)", "C:/" + name);
        long walkId = jdbcTemplate.queryForObject("SELECT id FROM walk ORDER BY id DESC LIMIT 1", Long.class);
        jdbcTemplate.update(
                "INSERT INTO run (id, stage, implementation_version, config_consumed, walk_id)"
                        + " VALUES (?, 'embedding-scoring', 'test', '{}', ?)",
                name,
                walkId);
        Map<OccurrenceId, List<float[]>> resident = new LinkedHashMap<>();
        Map<OccurrenceId, String> paths = new LinkedHashMap<>();
        bySeedPath().forEach((path, vectors) -> {
            OccurrenceId seed = insertOccurrence(walkId, "seeds/" + path);
            resident.put(seed, vectors.chunkVectors());
            paths.put(seed, path);
        });
        return new Fixture(new RunId(name), insertOccurrence(walkId, name + ".txt"), resident, paths);
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

    /** The survivor's chunk vectors, stored under {@code contentHash} as the embedding step stores them. */
    private void storeSurvivor(String contentHash, List<float[]> chunks) {
        VectorCache vectors = new VectorCache(jdbcTemplate);
        for (int chunk = 0; chunk < chunks.size(); chunk++) {
            vectors.put(contentHash, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, chunk, READ_UNDER, chunks.get(chunk));
        }
    }

    private RelevanceScoring scoring() {
        return RelevanceScoringBeans.real(jdbcTemplate);
    }

    /** The three seeds, each of a content of its own, in path order. */
    private static Map<String, SeedVectors> bySeedPath() {
        Map<String, SeedVectors> seeds = new LinkedHashMap<>();
        seeds.put(FIRST_SEED, new SeedVectors("the content of " + FIRST_SEED, List.of(axis(0), axis(1))));
        seeds.put(SECOND_SEED, new SeedVectors("the content of " + SECOND_SEED, List.of(axis(2), axis(3))));
        seeds.put(THIRD_SEED, new SeedVectors("the content of " + THIRD_SEED, List.of(axis(4))));
        return seeds;
    }

    private static float[] axis(int index) {
        float[] vector = new float[DIMENSION];
        vector[index] = 1;
        return vector;
    }

    private static Topic topic(SeedPassage... passages) {
        return new Topic(new TopicKey(passages[0]), List.of(passages));
    }
}
