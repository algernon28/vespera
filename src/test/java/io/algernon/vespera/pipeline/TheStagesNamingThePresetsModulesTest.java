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
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Which stages name the three modules the presets' constants lie in (ADR-330 §10): that an edit of a module moves the
 * stages naming it is ADR-058's, and this holds only what §10's table rests on, one module to a test, read off {@link
 * StageModules}, so that a stage gaining or losing a module says so here. {@code RunIdentityGoldenTest} pins every stage's
 * implementation version as a literal and so fails too; this test names which module moved. No test here edits a module or moves a run.
 */
@Epic("Pipeline")
@Feature("What a piece of work is identified by")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
class TheStagesNamingThePresetsModulesTest {

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

    /** Each module, named by the plain phrase of what it holds, with the stages that name it. */
    static Stream<Arguments> modulesAndTheStagesNamingThem() {
        return Stream.of(
                Arguments.of(
                        Named.of("the module that holds clustering and relevance scoring: the last four stages, the"
                                + " two of the seed and the scoring, the arrangement and the generation", "embedding"),
                        NAMING_EMBEDDING),
                Arguments.of(
                        Named.of("the module that holds the numbers of redundancy: extraction and the two stages after"
                                + " it", "similarity"),
                        NAMING_SIMILARITY),
                Arguments.of(
                        Named.of("the module that holds the converter's numbers: the seven stages from extraction to"
                                + " generation, byte-level reduction the one stage that names it not", "extraction"),
                        NAMING_EXTRACTION));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("modulesAndTheStagesNamingThem")
    @Story("A stage's piece of work is identified by exactly what identified it before")
    @DisplayName("Each module the presets lie in is named by exactly the stages the table of presets gives it, in the cascade's order")
    void eachPresetsModuleIsNamedByItsStages(String module, List<String> naming) {
        claim("the stages that name " + module + " are " + naming + ", in the cascade's order",
                () -> assertThat(stagesNaming(module)).containsExactlyElementsOf(naming));
    }

    private static List<String> stagesNaming(String module) {
        return Arrays.stream(StageModules.values())
                .filter(stage -> stage.modules().contains(module))
                .map(StageModules::stage)
                .toList();
    }
}
