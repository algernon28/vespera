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
 * <p>No score is written for this fixture, on purpose: an occurrence with no score is absent from the
 * result and is still an item the loop went through, so it is reported. A vector is written for each seed,
 * each seed being given as having one chunk, since a seed with a chunk and no vector stops the read (ADR-231, #496) and is held by {@code
 * ASeedWithNoVectorStopsScoringTest}.
 *
 * <p><b>Part (c) of ADR-192.</b> Does not compile until {@code ScoringProgress} and the two overloads exist;
 * part (c) moves it into {@code src/test}.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Relevance")
@Feature("Progress reporting")
@Issue("412")
@Link(name = "ADR-192", url = Adr.EVERY_LOOP_REPORTS_ITS_PROGRESS, type = "adr")
class ScoringProgressTest {

    private static final String MODEL = "qwen3-embedding:0.6b";
    private static final String CHUNKER_IDENTITY = "docling-hybrid-chunker-v1";
    private static final String CHUNKING_RULE_IDENTITY = "words-512-v1";

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
        List<String> events = new ArrayList<>();

        Map<OccurrenceId, Double> scores = scoring().scoresFor(
                new RunId("no-such-run"),
                List.of(new OccurrenceId(1), new OccurrenceId(2), new OccurrenceId(3)),
                recording(events));

        claim(
                "no occurrence is scored under that run, so none is in the result, and all three are reported",
                () -> {
                    assertThat(scores).isEmpty();
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
