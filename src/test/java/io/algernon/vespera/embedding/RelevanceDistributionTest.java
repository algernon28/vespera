package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The spread of relevance scores across a corpus, and the bounded sample of documents a person is
 * asked to judge (ADR-088).
 *
 * <p>The bands are cut over the scores that are actually there, never over nought to one. A cosine
 * similarity over one archive very often occupies a narrow part of the possible range, and bands cut
 * over the whole of it would put every document in one or two of them and leave the rest empty —
 * which tells a reader nothing about where the boundary between relevant and irrelevant might lie.
 *
 * <p>Fixtures write score rows directly. Every claim here is about how those scores are divided and
 * sampled, so producing them by embedding and comparing real documents would put a great deal of
 * machinery between the fixture and the thing being claimed.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Relevance")
@Feature("Score distribution")
@Issue("110")
@Link(name = "ADR-088", url = Adr.RELEVANCE_THRESHOLD_IS_SIXTY_LABELS, type = "adr")
class RelevanceDistributionTest {

    /**
     * Five scores whose lowest and highest are 0.20 and 0.70, so the observed range is 0.50 wide and
     * each of the five bands is 0.10 wide. Worked out by hand rather than by the code being tested.
     */
    private static final double[] SCORES = {0.20, 0.30, 0.45, 0.60, 0.70};

    /** The lowest score present, and therefore where the first band starts. */
    private static final double LOWEST_SCORE = 0.20;

    /** The highest score present, and therefore where the last band ends. */
    private static final double HIGHEST_SCORE = 0.70;

    /** ADR-088 fixes five bands, so a corpus is always divided the same number of ways. */
    private static final int BANDS = 5;

    /** The width each band gets from the five scores above: (0.70 - 0.20) / 5. */
    private static final double BAND_WIDTH = 0.10;

    /** ADR-088 fixes twelve per band: enough for a proportion whose direction is legible. */
    private static final int PER_BAND = 12;

    /** Twelve in each of five bands. Sixty at roughly two minutes each is about one sitting. */
    private static final int SAMPLE_SIZE = 60;

    /** Comfortably more than twelve in every band, so nothing is short and no shortfall arises. */
    private static final int DOCUMENTS_PER_FULL_BAND = 20;

    /** The band left deliberately short, so the shortfall rule has something to report. */
    private static final int SHORT_BAND = 1;

    /** How many documents that band is left holding, which is fewer than a full band supplies. */
    private static final int DOCUMENTS_IN_THE_SHORT_BAND = 4;

    /** Scores are compared with a tolerance because they are stored and read back as doubles. */
    private static final Offset<Double> ROUNDING = Offset.offset(1e-9);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("The bands are cut over the scores that are there")
    @DisplayName("Five equal bands are cut between the lowest and highest score present, not over the whole possible range")
    void cutsFiveEqualBandsOverTheObservedRange() {
        RunId scoringRun = aScoringRunScoring(SCORES);

        RelevanceDistribution.Distribution distribution = new RelevanceDistribution(jdbcTemplate).measure(scoringRun);

        claim(
                "a corpus is divided five ways, so two archives are always described the same way",
                () -> assertThat(distribution.bands()).hasSize(BANDS));
        claim(
                "the first band starts at the lowest score anything actually got, rather than at nought:"
                        + " scores here occupy half the possible range, and bands cut over the whole of it"
                        + " would leave most of them empty and say nothing about where the boundary lies",
                () -> assertThat(distribution.bands().getFirst().lowerBound()).isCloseTo(LOWEST_SCORE, ROUNDING));
        claim(
                "and the last band ends at the highest score anything actually got",
                () -> assertThat(distribution.bands().getLast().upperBound()).isCloseTo(HIGHEST_SCORE, ROUNDING));
        claim(
                "the five bands are equal in width, each " + BAND_WIDTH + " of the 0.50 the scores span,"
                        + " so the proportion relevant in one is comparable with the proportion in another",
                () -> assertThat(distribution.bands())
                        .allSatisfy(band -> assertThat(band.upperBound() - band.lowerBound())
                                .isCloseTo(BAND_WIDTH, ROUNDING)));
        claim(
                "each score falls in exactly one band -- a band takes scores from its lower bound up to"
                        + " but not including its upper one, and the last band also takes its own upper"
                        + " bound, so the highest-scoring document is counted rather than dropped",
                () -> assertThat(distribution.bands().stream()
                                .map(RelevanceDistribution.Band::documentCount)
                                .toList())
                        .containsExactly(1, 1, 1, 0, 2));
        claim(
                "and every scored document is accounted for across the bands, none twice and none lost",
                () -> assertThat(distribution.bands().stream()
                                .mapToInt(RelevanceDistribution.Band::documentCount)
                                .sum())
                        .isEqualTo(SCORES.length));
    }

    @Test
    @Story("Sixty documents, chosen the same way every time")
    @DisplayName("Twelve documents are drawn from each of the five bands, and the same run always draws the same sixty")
    void drawsTwelvePerBandAndTheSameSixtyEveryTime() {
        RunId scoringRun = aScoringRunScoring(scoresFillingEveryBand());
        RelevanceDistribution distribution = new RelevanceDistribution(jdbcTemplate);

        List<OccurrenceId> firstDraw = occurrencesOf(distribution.measure(scoringRun));
        List<OccurrenceId> secondDraw = occurrencesOf(distribution.measure(scoringRun));

        claim(
                "sixty documents are asked for in total -- at roughly two minutes each that is about two"
                        + " hours, the outer edge of one sitting, and the ceiling is the point: an"
                        + " open-ended task gets abandoned halfway and leaves a threshold calibrated from"
                        + " a partial pass with nothing recording that it was partial",
                () -> assertThat(firstDraw).hasSize(SAMPLE_SIZE));
        claim(
                "and they are spread " + PER_BAND + " to a band rather than taken from wherever the"
                        + " documents happen to be, so the proportion found relevant in one band can be"
                        + " compared with the proportion in another",
                () -> assertThat(distribution.measure(scoringRun).bands())
                        .allSatisfy(band -> assertThat(band.sampledCount()).isEqualTo(PER_BAND)));
        claim(
                "asking twice over the same run gives the same sixty documents in the same order, so a"
                        + " person who labels half of them today and half tomorrow is answering about the"
                        + " same documents both times",
                () -> assertThat(secondDraw).containsExactlyElementsOf(firstDraw));
        claim(
                "and they are genuinely drawn from across each band rather than being the ones it happens"
                        + " to hold first -- a sample taken in storage order would be reproducible too, and"
                        + " would still show a reader only one corner of each band",
                () -> assertThat(firstDraw).isNotEqualTo(theLowestScoringSixtyInStorageOrder(scoringRun)));
    }

    @Test
    @Story("Sixty documents, chosen the same way every time")
    @DisplayName("A band with too few documents gives what it has, the shortfall is recorded, and no neighbour makes it up")
    void aShortBandGivesWhatItHasAndRecordsTheShortfall() {
        RunId scoringRun = aScoringRunScoring(scoresLeavingTheSecondBandShort());

        RelevanceDistribution.Distribution distribution = new RelevanceDistribution(jdbcTemplate).measure(scoringRun);

        RelevanceDistribution.Band shortBand = distribution.bands().get(SHORT_BAND);
        claim(
                "the short band offers every document it holds and no more, because there are no others"
                        + " in it to offer",
                () -> assertThat(shortBand.sampledCount()).isEqualTo(DOCUMENTS_IN_THE_SHORT_BAND));
        claim(
                "the " + (PER_BAND - DOCUMENTS_IN_THE_SHORT_BAND) + " it could not supply are recorded as"
                        + " missing rather than passed over in silence: a reader comparing proportions"
                        + " across bands has to know which of them rests on a handful of answers",
                () -> assertThat(shortBand.shortfall()).isEqualTo(PER_BAND - DOCUMENTS_IN_THE_SHORT_BAND));
        claim(
                "no other band is asked to make up the difference -- every other band supplies exactly"
                        + " twelve, so the total falls short of sixty rather than being topped up from a"
                        + " neighbour whose documents answer a different question",
                () -> assertThat(distribution.bands().stream()
                                .filter(band -> band.ordinal() != SHORT_BAND)
                                .map(RelevanceDistribution.Band::sampledCount)
                                .toList())
                        .containsOnly(PER_BAND));
        claim(
                "so the sitting is shorter than sixty documents, by exactly what the short band lacked",
                () -> assertThat(distribution.sample())
                        .hasSize(SAMPLE_SIZE - (PER_BAND - DOCUMENTS_IN_THE_SHORT_BAND)));
    }

    private static List<OccurrenceId> occurrencesOf(RelevanceDistribution.Distribution distribution) {
        return distribution.sample().stream()
                .map(RelevanceDistribution.Sampled::occurrenceId)
                .toList();
    }

    /** What a sample would be if it simply took the documents the lowest scores belong to. */
    private List<OccurrenceId> theLowestScoringSixtyInStorageOrder(RunId scoringRun) {
        return jdbcTemplate
                .query(
                        "SELECT occurrence_id FROM relevance_score WHERE run_id = ? ORDER BY score, occurrence_id",
                        (resultSet, rowNumber) -> new OccurrenceId(resultSet.getLong("occurrence_id")),
                        scoringRun.value())
                .stream()
                .limit(SAMPLE_SIZE)
                .toList();
    }

    /** Twenty documents in each of the five bands, so every band can supply its twelve. */
    private static double[] scoresFillingEveryBand() {
        List<Double> scores = new ArrayList<>();
        for (int band = 0; band < BANDS; band++) {
            for (int within = 0; within < DOCUMENTS_PER_FULL_BAND; within++) {
                // Placed a little inside the band, so which band a score belongs to is never in doubt.
                scores.add(LOWEST_SCORE
                        + band * BAND_WIDTH
                        + BAND_WIDTH * (within + 1) / (DOCUMENTS_PER_FULL_BAND + 2));
            }
        }
        // The range has to be exactly the one the constants describe, so the band edges are known.
        scores.set(0, LOWEST_SCORE);
        scores.set(scores.size() - 1, HIGHEST_SCORE);
        return asArray(scores);
    }

    /** The same, except the second band is left holding fewer documents than a full sample needs. */
    private static double[] scoresLeavingTheSecondBandShort() {
        List<Double> kept = new ArrayList<>();
        int seenInShortBand = 0;
        for (double score : scoresFillingEveryBand()) {
            boolean inShortBand = score >= LOWEST_SCORE + SHORT_BAND * BAND_WIDTH
                    && score < LOWEST_SCORE + (SHORT_BAND + 1) * BAND_WIDTH;
            if (inShortBand && ++seenInShortBand > DOCUMENTS_IN_THE_SHORT_BAND) {
                continue;
            }
            kept.add(score);
        }
        return asArray(kept);
    }

    private static double[] asArray(List<Double> values) {
        double[] array = new double[values.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = values.get(i);
        }
        return array;
    }

    /** A scoring run over a walk holding one occurrence per score, each scored as given. */
    private RunId aScoringRunScoring(double[] scores) {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.startWalk(Path.of("C:/corpus"));
        OccurrenceId seed = anOccurrence(ledger, walkId, "seed.txt");
        RunId scoringRun = ledger.startRun("embedding-scoring", "abc123", "{}", walkId, List.of());
        for (int i = 0; i < scores.length; i++) {
            OccurrenceId scored = anOccurrence(ledger, walkId, "document-" + i + ".txt");
            jdbcTemplate.update(
                    "INSERT INTO relevance_score (occurrence_id, run_id, score, winning_seed_occurrence_id)"
                            + " VALUES (?, ?, ?, ?)",
                    scored.value(),
                    scoringRun.value(),
                    scores[i],
                    seed.value());
        }
        return scoringRun;
    }

    private OccurrenceId anOccurrence(Ledger ledger, WalkId walkId, String path) {
        ledger.fileOccurrence(
                walkId,
                new OccurrencePath(path),
                1,
                Instant.parse("2026-08-29T10:15:30Z"),
                Instant.parse("2026-08-20T08:00:00Z"));
        return ledger.occurrenceId(walkId, new OccurrencePath(path)).orElseThrow();
    }
}
