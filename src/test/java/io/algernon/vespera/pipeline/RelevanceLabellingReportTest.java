package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.embedding.LabelledSpread;
import io.algernon.vespera.embedding.RelevanceDistribution;
import io.algernon.vespera.ledger.OccurrenceId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The page a person reads before choosing a relevance threshold (ADR-088).
 *
 * <p>The claims here are about what the page refuses to do as much as what it shows. It never
 * declines to proceed on the shape of a distribution, because a mechanical shape test would itself
 * be an unmeasured threshold: a sound seed set very often produces a tight, unimodal spread. And it
 * has to say, in its own words, that a spread with no usable separation is a seed-set problem rather
 * than a threshold problem — otherwise the reader's only available response is to pick a cut anyway.
 */
@Epic("Relevance")
@Feature("Labelling")
@Issue("110")
@Link(name = "ADR-088", url = Adr.RELEVANCE_THRESHOLD_IS_SIXTY_LABELS, type = "adr")
class RelevanceLabellingReportTest {

    /** Wording that would turn the page into a refusal rather than a measurement. */
    private static final List<String> WORDING_THE_REPORT_REFUSES =
            List.of("cannot proceed", "aborted", "refused", "not enough separation to continue");

    @Test
    @Story("A measurement to read, never a refusal")
    @DisplayName("The page reports a tight, featureless distribution without ever declining to proceed")
    void reportsATightDistributionWithoutDeclining() {
        String html = RelevanceLabellingReport.render(aTightDistribution(), noPreviews(), noAnswers(), noIgnoredFloor());

        claim(
                "the page states what it found and stops there: it never tells the operator the run"
                        + " cannot continue, because deciding that mechanically would need a threshold on"
                        + " distribution shape that nobody has measured",
                () -> assertThat(html).doesNotContainIgnoringCase(WORDING_THE_REPORT_REFUSES.toArray(String[]::new)));
        claim(
                "and it still shows the bands it managed to cut, however little separates them, because a"
                        + " reader deciding whether the seeds were any good needs to see the shape rather"
                        + " than be told a verdict about it",
                () -> assertThat(html).contains("Band"));
    }

    @Test
    @Story("A measurement to read, never a refusal")
    @DisplayName("The page says that a spread with no usable separation is a seed-set problem, not a threshold problem")
    void namesAFlatSpreadAsASeedSetProblem() {
        String html = RelevanceLabellingReport.render(aTightDistribution(), noPreviews(), noAnswers(), noIgnoredFloor());

        claim(
                "the page tells the reader what a spread with nothing to separate actually means -- that"
                        + " the seed folder is what to change, and the answer is a different seed set and"
                        + " a re-score rather than a threshold picked out of a distribution that has no"
                        + " boundary in it to find",
                () -> assertThat(html).containsIgnoringCase("seed set"));
    }

    @Test
    @Story("A page that opens anywhere")
    @DisplayName("The page is one whole document that names no other file and fetches nothing")
    void isSelfContained() {
        String html = RelevanceLabellingReport.render(aTightDistribution(), noPreviews(), noAnswers(), noIgnoredFloor());

        claim(
                "it is a whole, standalone HTML document, openable without any other file present",
                () -> assertThat(html).contains("<!DOCTYPE html>").contains("<html").contains("</html>"));
        claim(
                "and it reaches for nothing over the network, so it opens on a laptop with no connection"
                        + " to the machine that wrote it",
                () -> assertThat(html)
                        .doesNotContain("<link")
                        .doesNotContain("<script")
                        .doesNotContain("href=\"http"));
    }

    @Test
    @Story("The sixty are shown in band order")
    @DisplayName("Each sampled document is shown with its score, its closest seed and the opening of its text")
    void showsEachSampledDocumentWithItsContext() {
        String html = RelevanceLabellingReport.render(aTightDistribution(), onePreview(), noAnswers(), noIgnoredFloor());

        claim(
                "the document is named by the path the walk recorded, which is what the person opens",
                () -> assertThat(html).contains("reports/q1.pdf"));
        claim(
                "the seed it scored closest to is named beside it, so the person can see what it was"
                        + " judged similar to rather than only how similar",
                () -> assertThat(html).contains("seeds/exemplar.pdf"));
        claim(
                "and the opening of its extracted text is shown, so the common case is answerable from"
                        + " the page itself without opening sixty files",
                () -> assertThat(html).contains("The quarterly figures"));
    }

    /**
     * A spread where every band is narrow and the counts barely differ — the shape ADR-088 says a
     * sound seed set very often produces, and the one a mechanical go/no-go would wrongly reject.
     */
    private static RelevanceDistribution.Distribution aTightDistribution() {
        List<RelevanceDistribution.Band> bands = List.of(
                new RelevanceDistribution.Band(0, 0.80, 0.82, 40, 12, 0),
                new RelevanceDistribution.Band(1, 0.82, 0.84, 44, 12, 0),
                new RelevanceDistribution.Band(2, 0.84, 0.86, 39, 12, 0),
                new RelevanceDistribution.Band(3, 0.86, 0.88, 41, 12, 0),
                new RelevanceDistribution.Band(4, 0.88, 0.90, 36, 12, 0));
        return new RelevanceDistribution.Distribution(200, 0.80, 0.90, bands, List.of(aSample()));
    }

    private static RelevanceDistribution.Sampled aSample() {
        return new RelevanceDistribution.Sampled(new OccurrenceId(1), 0.83, new OccurrenceId(2), 1);
    }

    /** The page still has to render when nothing could be previewed. */
    /** A page written before anybody has answered anything, which is every first run. */
    private static LabelledSpread.Spread noAnswers() {
        return new LabelledSpread.Spread(List.of(), List.of(), 0);
    }

    /** No threshold is being ignored, which is the ordinary case. */
    private static Optional<RelevanceLabellingReport.IgnoredFloor> noIgnoredFloor() {
        return Optional.empty();
    }

    private static List<RelevanceLabellingReport.Preview> noPreviews() {
        return List.of();
    }

    private static List<RelevanceLabellingReport.Preview> onePreview() {
        return List.of(new RelevanceLabellingReport.Preview(
                new OccurrenceId(1),
                "reports/q1.pdf",
                "seeds/exemplar.pdf",
                "The quarterly figures were tabled on the third."));
    }
}
