package io.algernon.vespera.embedding;

import io.algernon.vespera.ledger.OccurrenceId;
import java.util.List;
import java.util.Map;

/**
 * The arithmetic a person needs in order to choose the relevance threshold, and none of the choosing
 * (ADR-088, #112).
 *
 * <p><b>The engine does the arithmetic; it never writes the number.</b> What is worked out here is
 * the one thing nobody can do in their head — how many documents each candidate cut keeps and
 * discards, and what the labelled ones on either side of it turned out to be. What is not worked out
 * here is which cut to make: that needs a target proportion, and a target proportion is itself an
 * unmeasured threshold.
 *
 * <p><b>Counted in documents, never in statistics vocabulary.</b> The trade an operator is making is
 * "this many go, and of the ones I read, this many were worth keeping" — so the numbers that reach
 * the page are counts, and the proportions are stated as one number out of another rather than as a
 * rate. Twelve labels per band is a thin measurement, and "9 in 12" says so where "75%" does not.
 */
public final class LabelledSpread {

    private LabelledSpread() {}

    /**
     * What the answers in one band came to.
     *
     * @param bandOrdinal which band, counting from zero at the lowest-scoring end
     * @param labelled how many of its documents a person answered about
     * @param labelledRelevant how many of those answers said relevant
     */
    public record BandLabels(int bandOrdinal, int labelled, int labelledRelevant) {}

    /**
     * What one candidate cut would do.
     *
     * @param score the cut itself — a band boundary, since those are where the labels are stratified
     * @param surviving how many scored documents sit at or above it, and would be kept
     * @param discarded how many sit below it, and would be removed
     * @param labelledAbove how many of the survivors a person answered about
     * @param labelledRelevantAbove how many of those answers said relevant — the keep that is earned
     * @param labelledBelow how many of the discarded a person answered about
     * @param labelledRelevantBelow how many of those said relevant — the loss the cut would cause,
     *     which is the number a person is really deciding about
     */
    public record CandidateCut(
            double score,
            int surviving,
            int discarded,
            int labelledAbove,
            int labelledRelevantAbove,
            int labelledBelow,
            int labelledRelevantBelow) {}

    /**
     * The bands with their answers, and every cut those bands offer.
     *
     * @param bandLabels one entry per band, in band order
     * @param cuts the candidate cuts, lowest first
     * @param labelled how many answers were counted at all
     */
    public record Spread(List<BandLabels> bandLabels, List<CandidateCut> cuts, int labelled) {}

    /**
     * The spread of {@code answers} across {@code distribution}.
     *
     * <p><b>The answers are matched to this run's scores, not to the ones they were given beside.</b>
     * That is ADR-088's headline consequence made arithmetic: a label is a fact about a document, so a
     * re-score under a new model re-reads the answers already given and re-bands them against the new
     * scores, without a person being asked anything again. An answer about a document this run did not
     * score contributes nothing rather than being counted in a band it is not in.
     */
    public static Spread of(
            RelevanceDistribution.Distribution distribution,
            List<RelevanceDistribution.Scored> scored,
            Map<OccurrenceId, Boolean> answers) {
        double lowest = distribution.lowestScore();
        double highest = distribution.highestScore();
        double width = (highest - lowest) / distribution.bands().size();

        int[] labelled = new int[distribution.bands().size()];
        int[] relevant = new int[distribution.bands().size()];
        int counted = 0;
        for (RelevanceDistribution.Scored document : scored) {
            Boolean answer = answers.get(document.occurrenceId());
            if (answer == null) {
                continue;
            }
            int band = RelevanceDistribution.bandOf(document.score(), lowest, width, distribution.bands().size());
            labelled[band]++;
            if (answer) {
                relevant[band]++;
            }
            counted++;
        }

        List<BandLabels> bandLabels = new java.util.ArrayList<>();
        for (int band = 0; band < distribution.bands().size(); band++) {
            bandLabels.add(new BandLabels(band, labelled[band], relevant[band]));
        }

        // A cut at the lowest bound discards nothing and is not a choice anybody is making, so the
        // candidates are the boundaries between the bands rather than every boundary there is.
        List<CandidateCut> cuts = new java.util.ArrayList<>();
        for (int band = 1; band < distribution.bands().size(); band++) {
            cuts.add(cutAt(distribution.bands().get(band).lowerBound(), scored, answers));
        }
        return new Spread(List.copyOf(bandLabels), List.copyOf(cuts), counted);
    }

    /** What cutting at {@code score} would keep and remove, in documents and in answers. */
    private static CandidateCut cutAt(
            double score, List<RelevanceDistribution.Scored> scored, Map<OccurrenceId, Boolean> answers) {
        int surviving = 0;
        int discarded = 0;
        int labelledAbove = 0;
        int relevantAbove = 0;
        int labelledBelow = 0;
        int relevantBelow = 0;
        for (RelevanceDistribution.Scored document : scored) {
            // At or above the cut survives, matching the floor the verdict step applies: it removes a
            // document scoring strictly below the number, so a document sitting exactly on it stays.
            boolean survives = document.score() >= score;
            if (survives) {
                surviving++;
            } else {
                discarded++;
            }
            Boolean answer = answers.get(document.occurrenceId());
            if (answer == null) {
                continue;
            }
            if (survives) {
                labelledAbove++;
                relevantAbove += answer ? 1 : 0;
            } else {
                labelledBelow++;
                relevantBelow += answer ? 1 : 0;
            }
        }
        return new CandidateCut(
                score, surviving, discarded, labelledAbove, relevantAbove, labelledBelow, relevantBelow);
    }
}
