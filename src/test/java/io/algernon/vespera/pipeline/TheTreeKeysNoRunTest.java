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
 * Which modules key the generation run once the tree is drawn by a module of its own (ADR-273 §5): the ones whose
 * rules decide what the run records, and not the one that draws the tree from those records, so that a page drawn
 * differently never asks the model again.
 */
@Epic("Run identity")
@Feature("What a run is keyed by")
@Link(name = "ADR-273", url = Adr.THE_DELIVERABLE_IS_HTML_WITH_A_VERIFIED_COPY_OF_EVERY_SURVIVOR, type = "adr")
@Link(name = "ADR-226", url = Adr.NO_STAGE_NAMES_PIPELINE_AND_ITS_RULES_LIVE_IN_THE_CAPABILITY_MODULES, type = "adr")
class TheTreeKeysNoRunTest {

    /** What decides the questions generation puts, the answers it keeps and the faults it records. */
    private static final List<String> WHAT_DECIDES_GENERATIONS_ROWS = List.of("synthesis", "extraction", "embedding");

    /** What the arrangement's version spans, unchanged by ADR-273. */
    private static final List<String> ARRANGEMENTS_MODULES = List.of("synthesis", "extraction", "embedding");

    @Test
    @Story("Drawing the tree again never writes it again")
    @DisplayName("The writing step is keyed by the code that decides what it writes, and not by the code that draws the pages")
    void generationIsKeyedByWhatDecidesItsRowsAlone() {
        claim(
                "the writing step's version spans the code that decides its questions and its answers, and not the"
                        + " profile's rule for how its keys are stated on the index, which is a rule of drawing the tree",
                () -> assertThat(StageModules.GENERATION.modules()).containsExactlyElementsOf(WHAT_DECIDES_GENERATIONS_ROWS));
        claim(
                "the arrangement's version spans the same three modules it spanned before",
                () -> assertThat(StageModules.ARRANGEMENT.modules()).containsExactlyElementsOf(ARRANGEMENTS_MODULES));
        claim(
                "and no stage's version spans the code that draws the tree, so a change to it alone mints no run",
                () -> assertThat(Arrays.stream(StageModules.values())
                                .filter(stage -> stage.modules().contains("deliverable"))
                                .toList())
                        .isEmpty());
    }
}
