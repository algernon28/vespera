package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which stages a change to a module moves (ADR-330 §10, ADR-058): the record says that moving clustering's two numbers
 * out of {@code embedding} moves four stages and leaves four, and why the numbers of {@code similarity} and
 * {@code extraction} stay in code. That rests on which stages name which module, and this holds it, so that a stage
 * gaining a module says so here.
 */
@Epic("Pipeline")
@Feature("What a piece of work is identified by")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_CLUSTERING_SHIPS_AS_PRESETS, type = "adr")
class ThePresetsMoveOnlyTheStagesNamingEmbeddingTest {

    /** The stages whose implementation version names {@code embedding}, in the cascade's order. */
    private static final List<String> NAMING_EMBEDDING =
            List.of("seed-measurement", "embedding-scoring", "arrangement", "generation");

    /** The stages an edit of {@code similarity} would move by naming it, and every later one by naming its run. */
    private static final List<String> NAMING_SIMILARITY =
            List.of("extraction", "content-census", "content-redundancy");

    @Test
    @Story("A stage's piece of work is identified by exactly what identified it before")
    @DisplayName("An edit of the module that holds clustering moves the last four stages and leaves the first four")
    void anEditOfEmbeddingMovesTheLastFourStagesAndLeavesTheFirstFour() {
        claim("the stages that name embedding are the last four, the two of the seed and the scoring, the arrangement and"
                        + " the generation",
                () -> assertThat(stagesNaming("embedding")).containsExactlyElementsOf(NAMING_EMBEDDING));
    }

    @Test
    @Story("A stage's piece of work is identified by exactly what identified it before")
    @DisplayName("An edit of the module that holds the numbers of redundancy would reach every stage from extraction on")
    void anEditOfSimilarityWouldReachEveryStageFromExtractionOn() {
        claim("the stages that name similarity are extraction and the two after it, and each later one names their runs",
                () -> assertThat(stagesNaming("similarity")).containsExactlyElementsOf(NAMING_SIMILARITY));
    }

    private static List<String> stagesNaming(String module) {
        return Arrays.stream(StageModules.values())
                .filter(stage -> stage.modules().contains(module))
                .map(StageModules::stage)
                .toList();
    }
}
