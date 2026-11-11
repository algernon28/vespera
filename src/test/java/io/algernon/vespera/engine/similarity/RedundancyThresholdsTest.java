package io.algernon.vespera.engine.similarity;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
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
}
