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
 * The contract of {@code ScoringProgress} (ADR-192 section 5, #412), through which {@code RelevanceScoring}
 * reports its two loops: {@code toReadSeedVectors(long)} and {@code seedVectorsRead()} for {@code
 * residentSeedVectors}, and {@code toReadScores(long)} and {@code scoreRead()} for {@code scoresFor}. Each
 * {@code to...} is called once, with the loop's total, before its first item, and each completion after an
 * item, whatever became of it.
 *
 * <p>One of the three occurrences whose scores are read is scored and the other two are not: an occurrence
 * with no score is absent from the result and is still an item the loop went through, so it is reported,
 * and so is the one that is scored. A vector is written for each seed,
 * each seed being given as having one chunk, since a seed with a chunk and no vector stops the read (ADR-231, #496) and is held by {@code
 * ASeedWithNoVectorStopsScoringTest}.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Relevance")
@Feature("Progress reporting")
@Issue("412")
@Link(name = "ADR-192", url = Adr.EVERY_LOOP_REPORTS_ITS_PROGRESS, type = "adr")
class ScoringProgressTest {

    private static final String MODEL = "a-comparing-model:0.6b";
    private static final String CHUNKER_IDENTITY = "docling-hybrid-chunker-v1";
    private static final String CHUNKING_RULE_IDENTITY = "words-512-v1";

    /** The score recorded for the one occurrence of three that is scored. */
    private static final double A_SCORE = 0.5;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("Scoring tells its caller how many seeds and scores it will read")
    @DisplayName("Reading the stored vectors of two seeds is announced once with two, and each seed is reported as it is read")
    @Issue("496")
    @Link(name = "ADR-231", url = Adr.A_PULL_WHILE_THE_EMBEDDING_STEP_RUNS_STOPS_IT_AND_SCORING_REFUSES_A_SEED_WHOSE_CHUNKS_HAVE_NO_VECTOR, type = "adr")
    void announcesTheSeedsOnceAndReportsEachOneAsItIsRead() {
        Map<OccurrenceId, SeedChunks> seeds = new LinkedHashMap<>();
        seeds.put(new OccurrenceId(1), new SeedChunks("seed-one", 1));
        seeds.put(new OccurrenceId(2), new SeedChunks("seed-two", 1));
        for (SeedChunks seed : seeds.values()) {
            new VectorCache(jdbcTemplate)
                    .put(seed.contentHash(), CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, 0, MODEL, new float[] {1f, 0f});
        }
        List<String> events = new ArrayList<>();

        Map<OccurrenceId, List<float[]>> resident = scoring().residentSeedVectors(
                seeds, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, MODEL, recording(events));

        claim(
                "each of the two seeds has a stored vector, so both are resident, the total of two is"
                        + " announced once before the first, and each is reported once",
                () -> {
                    assertThat(resident).hasSize(seeds.size());
                    assertThat(events).containsExactly("to-read-seeds 2", "seed-read", "seed-read");
                });
    }

    @Test
    @Story("Scoring tells its caller how many seeds and scores it will read")
    @DisplayName("Reading the scores of three occurrences is announced once with three, and each is reported, scored or not")
    void announcesTheScoresOnceAndReportsEachOneWhetherOrNotItIsScored() {
        long walkId = insertWalk();
        RunId run = insertRun(walkId);
        OccurrenceId seed = insertOccurrence(walkId, "seeds/exemplar.pdf");
        OccurrenceId unscoredBefore = insertOccurrence(walkId, "a.txt");
        OccurrenceId scored = insertOccurrence(walkId, "b.txt");
        OccurrenceId unscoredAfter = insertOccurrence(walkId, "c.txt");
        new RelevanceScoreCache(jdbcTemplate).record(scored, run, new RelevanceScore(A_SCORE, seed));
        List<String> events = new ArrayList<>();

        Map<OccurrenceId, Double> scores =
                scoring().scoresFor(run, List.of(unscoredBefore, scored, unscoredAfter), recording(events));

        claim(
                "only the middle occurrence is scored under that run, so it alone is in the result, and all"
                        + " three are reported, the scored one as much as the two that are not",
                () -> {
                    assertThat(scores).containsExactly(Map.entry(scored, A_SCORE));
                    assertThat(events).containsExactly("to-read-scores 3", "score-read", "score-read", "score-read");
                });
    }

    @Test
    @Story("Scoring tells its caller how many seeds and scores it will read")
    @DisplayName("A loop with nothing to read is announced with zero and reports nothing")
    void aLoopOfZeroItemsIsAnnouncedWithZero() {
        List<String> events = new ArrayList<>();

        scoring().residentSeedVectors(
                new LinkedHashMap<>(), CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, MODEL, recording(events));
        scoring().scoresFor(new RunId("no-such-run"), List.of(), recording(events));

        claim(
                "each method is reached with an empty set, so each announces zero, once, and reports nothing",
                () -> assertThat(events).containsExactly("to-read-seeds 0", "to-read-scores 0"));
    }

    private long insertWalk() {
        jdbcTemplate.update("INSERT INTO walk (root, finished) VALUES (?, 1)", "C:/scoring-progress");
        return jdbcTemplate.queryForObject("SELECT id FROM walk ORDER BY id DESC LIMIT 1", Long.class);
    }

    private RunId insertRun(long walkId) {
        String runId = "scoring-progress-test-" + System.nanoTime();
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

    private RelevanceScoring scoring() {
        return new RelevanceScoring(
                new VectorCache(jdbcTemplate), new RelevanceScorer(), new RelevanceScoreCache(jdbcTemplate));
    }

    private static ScoringProgress recording(List<String> events) {
        return new ScoringProgress() {
            @Override
            public void toReadSeedVectors(long seeds) {
                events.add("to-read-seeds " + seeds);
            }

            @Override
            public void seedVectorsRead() {
                events.add("seed-read");
            }

            @Override
            public void toReadScores(long occurrences) {
                events.add("to-read-scores " + occurrences);
            }

            @Override
            public void scoreRead() {
                events.add("score-read");
            }
        };
    }
}
