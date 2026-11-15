package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The job is fifteen steps once the translation step is gone (ADR-392 §3): {@code StepNames} holds the fifteen names
 * ADR-157 §7 lists, in that order, the report names them in job order, and a scoring run records three of them.
 *
 * <p>{@code StepNames}' names are read from its constants by reflection, so that a sixteenth constant fails the claim
 * whatever else names it. Both claims fail while {@code translation} is a step.
 */
@Epic("Pipeline")
@Feature("How a stage reaches its run")
@Issue("771")
@Link(name = "ADR-392", url = Adr.VESPERA_FINDS_NO_TRANSLATION_SETS, type = "adr")
@Link(name = "ADR-157", url = Adr.A_STAGE_ASKS_FOR_ITS_RUN_AFTER_ITS_OWN_GATE, type = "adr")
class TheJobRunsFifteenStepsTest {

    /** The fifteen steps of the job, in the order it runs them: ADR-157 §7's list, which ADR-392 §3 restores. */
    private static final List<String> THE_FIFTEEN_STEPS = List.of(
            "census", "byte-level-reduction", "extraction", "content-census", "redundancy-signature",
            "content-redundancy", "seed-extraction", "seed-corpus-comparison", "embedding-scoring",
            "relevance-scoring", "relevance-floor", "clustering", "relevance-report", "arrangement", "generation");

    /** The three steps a scoring run records its completion under, in job order (ADR-392 §3). */
    private static final List<String> THE_SCORING_RUNS_THREE_STEPS =
            List.of("embedding-scoring", "relevance-scoring", "clustering");

    @Test
    @Story("Every step is named once")
    @DisplayName("The job runs fifteen steps, from the census to generation, in order, and none finds copies of a document in other languages")
    void theJobRunsTheFifteenStepsInOrderAndNoTranslationStep() throws IllegalAccessException {
        List<String> named = new ArrayList<>();
        for (Field field : StepNames.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                field.setAccessible(true);
                named.add((String) field.get(null));
            }
        }

        claim("the step names held in one place are the fifteen, from the census to generation, in the order they are"
                        + " declared, with no step between the embedding scoring and the relevance scoring",
                () -> assertThat(named).containsExactlyElementsOf(THE_FIFTEEN_STEPS));
        claim("and the report lists the same fifteen in the order the job runs them",
                () -> assertThat(InvocationReport.STEPS_IN_JOB_ORDER).containsExactlyElementsOf(THE_FIFTEEN_STEPS));
    }

    @Test
    @Story("Every step is named once")
    @DisplayName("A scoring run records three steps: the embedding scoring, the relevance scoring and the clustering")
    void aScoringRunRecordsThreeSteps() {
        claim("the steps that record their completion under a scoring run are the embedding scoring, the relevance"
                        + " scoring and the clustering, in that order, and no step between the first two",
                () -> assertThat(InvocationReport.STEPS_OF_A_STAGE.get(StageModules.EMBEDDING_SCORING.stage()))
                        .containsExactlyElementsOf(THE_SCORING_RUNS_THREE_STEPS));
    }
}
