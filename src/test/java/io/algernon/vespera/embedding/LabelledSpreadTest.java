package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The arithmetic that helps a person choose the relevance cut (ADR-088, #112) — and none of the
 * choosing.
 *
 * <p><b>What a cut costs is the one thing nobody can work out in their head.</b> How many documents
 * fall either side of a candidate boundary, and what the ones a person actually read turned out to
 * be, is a join across sixty answers and a whole corpus of scores. What the engine must not do is
 * turn that into a recommendation: which trade is acceptable needs a target proportion, and a target
 * proportion is itself an unmeasured threshold.
 *
 * <p><b>A document sitting exactly on the cut survives it</b>, matching the verdict the floor writes:
 * the step removes a document scoring strictly below the number. The two have to agree, or the page
 * would promise a count the run then contradicts.
 */
@Epic("Relevance")
@Feature("Relevance threshold")
@Issue("112")
@Link(name = "ADR-088", url = Adr.RELEVANCE_THRESHOLD_IS_SIXTY_LABELS, type = "adr")
class LabelledSpreadTest {

    /** The bands the application ships (vespera.relevance.sample.bands). */
    private static final int BANDS = 5;

    @Test
    @Story("The engine does the arithmetic and never writes the number")
    @DisplayName("A document scoring exactly the cut survives it, as the verdict step would leave it")
    void adocumentExactlyOnTheCutSurvives() {
        // Bands over 0.0 to 1.0 are 0.2 wide, so the first cut falls exactly on 0.2.
        Map<OccurrenceId, Boolean> answers = noAnswers();
        LabelledSpread.Spread spread = LabelledSpread.of(
                aDistributionFrom(0.0, 1.0),
                List.of(scored(1, 0.2), scored(2, 0.19999), scored(3, 0.6)),
                answers);

        claim(
                "the document sitting exactly on the cut is counted as surviving, matching the floor the"
                        + " verdict step applies -- which removes a document scoring strictly below the"
                        + " number. If the two disagreed, this page would promise a count the run then"
                        + " contradicts",
                () -> assertThat(spread.cuts().getFirst().surviving()).isEqualTo(2));
    }

    @Test
    @Story("Answers are facts about documents and outlive the run that showed them")
    @DisplayName("Answers are re-banded against this run's scores, not the ones they were given beside")
    void rebandsAnswersAgainstThisRunsScores() {
        Map<OccurrenceId, Boolean> answers = new LinkedHashMap<>();
        answers.put(new OccurrenceId(1), true);

        // The same answer, against a run that scored the document at the other end of the range.
        LabelledSpread.Spread whenScoredHigh =
                LabelledSpread.of(aDistributionFrom(0.0, 1.0), List.of(scored(1, 0.95), scored(2, 0.05)), answers);
        LabelledSpread.Spread whenScoredLow =
                LabelledSpread.of(aDistributionFrom(0.0, 1.0), List.of(scored(1, 0.05), scored(2, 0.95)), answers);

        claim(
                "the answer lands in the top band when this run scores the document high",
                () -> assertThat(whenScoredHigh.bandLabels().getLast().labelledRelevant())
                        .isEqualTo(1));
        claim(
                "and in the bottom band when it scores it low, without anybody being asked again: a label"
                        + " is a fact about a document, so a re-score under a new model re-reads the"
                        + " answers already given and re-bands them (ADR-088's headline consequence)",
                () -> assertThat(whenScoredLow.bandLabels().getFirst().labelledRelevant())
                        .isEqualTo(1));
    }

    /** A distribution over the given range, with the five bands ADR-088 fixes. */
    private static RelevanceDistribution.Distribution aDistributionFrom(double lowest, double highest) {
        double width = (highest - lowest) / BANDS;
        List<RelevanceDistribution.Band> bands = new java.util.ArrayList<>();
        for (int ordinal = 0; ordinal < BANDS; ordinal++) {
            double lowerBound = lowest + ordinal * width;
            bands.add(new RelevanceDistribution.Band(
                    ordinal,
                    lowerBound,
                    ordinal == BANDS - 1 ? highest : lowerBound + width,
                    0,
                    0,
                    0));
        }
        return new RelevanceDistribution.Distribution(0, lowest, highest, List.copyOf(bands), List.of());
    }

    private static RelevanceDistribution.Scored scored(long occurrenceId, double score) {
        return new RelevanceDistribution.Scored(new OccurrenceId(occurrenceId), score, new OccurrenceId(99));
    }

    private static Map<OccurrenceId, Boolean> noAnswers() {
        return Map.of();
    }
}
