package io.algernon.vespera.similarity;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Stage 4's two cuts and the containment path's two counts as a run names them (ADR-082, ADR-330 §9). The text is what
 * the run's configuration holds where it is not the shipped one's, so that a run says which cuts judged it; the type
 * refuses what judges nothing, whose words are the start of a run's to say.
 */
@Epic("Redundancy")
@Feature("Resolution")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
class RedundancyThresholdsTest {

    /** What the application ships, ADR-081's measured numbers. */
    private static final double JACCARD = 0.80;

    private static final double CONTAINMENT = 0.95;

    private static final int SAMPLE = 32;

    private static final int HITS = 24;

    @Test
    @Story("A run says which cuts judged it")
    @DisplayName("The numbers the application ships read as the text every earlier run is compared with, each as Java writes it")
    void theShippedNumbersReadAsTheirIdentity() {
        claim("near-duplicate, containment, sample and hits in that order, the 0.80 written 0.8",
                () -> assertThat(new RedundancyThresholds(JACCARD, CONTAINMENT, SAMPLE, HITS).identity())
                        .isEqualTo("near-duplicate:0.8;containment:0.95;sample:32;hits:24"));
    }

    @Test
    @Story("A run says which cuts judged it")
    @DisplayName("Each number stands in its own place of the text")
    void eachNumberStandsInItsOwnPlace() {
        claim("four numbers all different, each where its name is",
                () -> assertThat(new RedundancyThresholds(0.9, 1.0, 16, 12).identity())
                        .isEqualTo("near-duplicate:0.9;containment:1.0;sample:16;hits:12"));
    }

    @Test
    @Story("A value no run can be made under stops the invocation")
    @DisplayName("A cut that judges every pair or no pair is refused, at either end, for each cut")
    void aCutOutsideAboveZeroToOneIsRefused() {
        for (double noCut : List.of(0.0, -0.1, 1.5, Math.nextUp(1.0), Double.NaN, Double.POSITIVE_INFINITY)) {
            claim("a near-duplicate cut of " + noCut + " is refused",
                    () -> assertThatThrownBy(() -> new RedundancyThresholds(noCut, CONTAINMENT, SAMPLE, HITS))
                            .isInstanceOf(IllegalArgumentException.class));
            claim("and so is a containment cut of " + noCut,
                    () -> assertThatThrownBy(() -> new RedundancyThresholds(JACCARD, noCut, SAMPLE, HITS))
                            .isInstanceOf(IllegalArgumentException.class));
        }
    }

    @Test
    @Story("A value no run can be made under stops the invocation")
    @DisplayName("A sample of no shingles, no hits, and more hits than the sample are refused")
    void countsThatRetrieveNothingAreRefused() {
        claim("a sample of no shingles retrieves nothing",
                () -> assertThatThrownBy(() -> new RedundancyThresholds(JACCARD, CONTAINMENT, 0, 0))
                        .isInstanceOf(IllegalArgumentException.class));
        claim("no hits would retrieve every document as a container",
                () -> assertThatThrownBy(() -> new RedundancyThresholds(JACCARD, CONTAINMENT, SAMPLE, 0))
                        .isInstanceOf(IllegalArgumentException.class));
        claim("and more hits than the sample holds retrieve no container",
                () -> assertThatThrownBy(() -> new RedundancyThresholds(JACCARD, CONTAINMENT, SAMPLE, SAMPLE + 1))
                        .isInstanceOf(IllegalArgumentException.class));
    }

    @Test
    @Story("A value no run can be made under stops the invocation")
    @DisplayName("The nearest values to each bound are allowed")
    void theNearestValuesAreAllowed() {
        claim("the smallest double above 0, and exactly 1, for each cut; one hit, and as many hits as the sample",
                () -> assertThatCode(() -> {
                            new RedundancyThresholds(Double.MIN_VALUE, Double.MIN_VALUE, SAMPLE, HITS);
                            new RedundancyThresholds(1.0, 1.0, SAMPLE, HITS);
                            new RedundancyThresholds(JACCARD, CONTAINMENT, 1, 1);
                            new RedundancyThresholds(JACCARD, CONTAINMENT, SAMPLE, SAMPLE);
                        })
                        .doesNotThrowAnyException());
    }
}
