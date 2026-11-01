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
 * Which stages a change to a module moves (ADR-330 §10, ADR-058): the record says that moving the presets' constants
 * out of {@code extraction}, {@code similarity} and {@code embedding} moves every stage from extraction on, seven of
 * them, and leaves byte-level reduction alone. That rests on which stages name which module, and this holds it, one
 * module to a test, so that a stage gaining or losing a module says so here.
 */
@Epic("Pipeline")
@Feature("What a piece of work is identified by")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
class ThePresetsMoveEveryStageFromExtractionOnTest {

    /** The stages whose implementation version names {@code embedding}, in the cascade's order. */
    private static final List<String> NAMING_EMBEDDING =
            List.of("seed-measurement", "embedding-scoring", "arrangement", "generation");

    /** The stages whose implementation version names {@code similarity}, in the cascade's order. */
    private static final List<String> NAMING_SIMILARITY =
            List.of("extraction", "content-census", "content-redundancy");

    /** The seven stages whose implementation version names {@code extraction}: every stage but byte-level reduction. */
    private static final List<String> NAMING_EXTRACTION = List.of(
            "extraction", "content-census", "content-redundancy", "seed-measurement", "embedding-scoring",
            "arrangement", "generation");

    @Test
    @Story("A stage's piece of work is identified by exactly what identified it before")
    @DisplayName("An edit of the module that holds clustering and relevance scoring moves the last four stages")
    void anEditOfEmbeddingMovesTheLastFourStages() {
        claim("the stages that name embedding are the last four, the two of the seed and the scoring, the arrangement and"
                        + " the generation",
                () -> assertThat(stagesNaming("embedding")).containsExactlyElementsOf(NAMING_EMBEDDING));
    }

    @Test
    @Story("A stage's piece of work is identified by exactly what identified it before")
    @DisplayName("An edit of the module that holds the numbers of redundancy moves extraction and the two stages after it")
    void anEditOfSimilarityMovesExtractionAndTheTwoAfterIt() {
        claim("the stages that name similarity are extraction and the two after it, and each later one names their runs",
                () -> assertThat(stagesNaming("similarity")).containsExactlyElementsOf(NAMING_SIMILARITY));
    }

    @Test
    @Story("A stage's piece of work is identified by exactly what identified it before")
    @DisplayName("An edit of the module that holds the converter's numbers moves the seven stages from extraction on, and leaves byte-level reduction alone")
    void anEditOfExtractionMovesEveryStageFromExtractionOn() {
        claim("the stages that name extraction are the seven from extraction to generation, so byte-level reduction is"
                        + " the one stage the presets leave",
                () -> assertThat(stagesNaming("extraction")).containsExactlyElementsOf(NAMING_EXTRACTION));
    }

    private static List<String> stagesNaming(String module) {
        return Arrays.stream(StageModules.values())
                .filter(stage -> stage.modules().contains(module))
                .map(StageModules::stage)
                .toList();
    }
}
