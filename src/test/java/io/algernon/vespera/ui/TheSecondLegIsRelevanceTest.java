package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
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
 * The control panel's table of legs once the translation step is gone (ADR-392 §3, amending ADR-324 §1 and ADR-361
 * §1): the second leg is {@code relevance}, the seeds' two steps and the embedding scoring, and the other three are as
 * they were. {@code pipeline.TheControlPanelsLegsAreTheReportsTest} holds this table to the report's, so the report's
 * legs are held to it too.
 *
 * <p>It fails while the second leg is named {@code translation} or holds the translation step.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("771")
@Issue("770")
@Link(name = "ADR-392", url = Adr.VESPERA_FINDS_NO_TRANSLATION_SETS, type = "adr")
@Link(name = "ADR-324", url = Adr.THE_TIMELINE_FOLDS_INTO_FOUR_LEGS, type = "adr")
@Link(name = "ADR-361", url = Adr.THE_COCKPITS_STEPS_FOLD_INTO_THE_PROGRESS_PAGES_FOUR_LEGS, type = "adr")
class TheSecondLegIsRelevanceTest {

    /** The four legs and their steps in job order, ADR-392 §3's table: fifteen steps, each in one leg. */
    private static final Map<String, List<String>> THE_FOUR_LEGS = theFourLegs();

    private static Map<String, List<String>> theFourLegs() {
        Map<String, List<String>> legs = new LinkedHashMap<>();
        legs.put("deduplication", List.of("census", "byte-level-reduction", "extraction", "content-census",
                "redundancy-signature", "content-redundancy"));
        legs.put("relevance", List.of("seed-extraction", "seed-corpus-comparison", "embedding-scoring"));
        legs.put("clustering", List.of("relevance-scoring", "relevance-floor", "clustering", "relevance-report"));
        legs.put("generation", List.of("arrangement", "generation"));
        return legs;
    }

    @Test
    @Story("The window folds the steps into the report's four parts")
    @DisplayName("The four parts are deduplication, relevance, clustering and generation, relevance holding the seed extraction, the seed comparison and the embedding scoring")
    void theFourLegsAreDeduplicationRelevanceClusteringAndGeneration() {
        Map<String, List<String>> byTheWindow = new LinkedHashMap<>();
        Legs.ALL.forEach(leg -> byTheWindow.put(leg.name(), leg.steps()));

        claim("the window's four parts are deduplication, relevance, clustering and generation, in that order, the"
                        + " second holding the seed extraction, the seed comparison and the embedding scoring, and"
                        + " each part its steps in the order the job runs them",
                () -> assertThat(byTheWindow).containsExactlyEntriesOf(THE_FOUR_LEGS));
    }
}
