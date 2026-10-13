package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.context.annotation.Bean;

/**
 * The scope of the listeners that write at the end of stage 2's step is stated in {@link
 * ExtractionJobConfiguration} and in no record (ADR-251 section 3): the fault recorder, and the two that
 * each write a page, are built in the step's scope, so none is built before the step first calls it.
 *
 * <p>It reads the bean methods' annotations. That a step that failed having read nothing asks for no run is
 * held by {@code SurvivorListListenerTest} and the tests of the review list, not here.
 */
@Epic("Extraction")
@Feature("Stage 2 step")
@Link(name = "ADR-251", url = Adr.ONE_STATEMENT_OF_THE_LEDGER_IS_AS_LONG_AS_A_WALK, type = "adr")
class TheListenersAtTheEndOfStageTwoAreStepScopedTest {

    /** The bean methods whose javadocs give the scope, each by reference to the one before it. */
    private static final List<String> THE_LISTENERS_BUILT_WHEN_THE_STEP_FIRST_CALLS_THEM =
            List.of("extractionFaultRecorder", "reviewListListener", "survivorListListener");

    @Test
    @Story("A page written when extraction ends is written by a part built only once extraction has started")
    @DisplayName("The parts that write when extraction ends are each built only once extraction has started")
    void eachListenerIsBuiltInTheStepsScope() {
        List<String> stepScoped = Arrays.stream(ExtractionJobConfiguration.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Bean.class))
                .filter(method -> method.isAnnotationPresent(StepScope.class))
                .map(Method::getName)
                .toList();

        claim(
                "the fault recorder, the review list's listener and the survivor list's listener are each a bean"
                        + " of the step's scope, so each is built when the step first calls it and not when the"
                        + " application starts; a name missing here is a listener that lost the scope",
                () -> assertThat(stepScoped).containsAll(THE_LISTENERS_BUILT_WHEN_THE_STEP_FIRST_CALLS_THEM));
    }
}
