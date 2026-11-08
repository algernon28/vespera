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
    private static RelevanceLabellingReport.IgnoredFloor noIgnoredFloor() {
        return null;
    }

    private static List<RelevanceLabellingReport.Preview> noPreviews() {
        return List.of();
    }
}
