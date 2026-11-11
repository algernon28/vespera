package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.ui.StopCosts;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.SimpleJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The control panel's table of what a stop costs at each step names the steps of the job {@code vespera run} starts,
 * every one, in the order the job runs them (ADR-302 §7, which ADR-342 keeps and ADR-344 §2 shows beside each step's
 * bar): the steps are read from the job as the jar builds it, and the rows from {@code StopCosts}, where the window
 * reads them.
 */
@CascadeSliceTest
@Import(StubbedExtractionBeans.class)
@Epic("Operation")
@Feature("The control panel")
@Issue("700")
@Link(name = "ADR-344", url = Adr.THE_CONTROL_PANELS_WINDOW_IS_ONE_COCKPIT, type = "adr")
@Link(name = "ADR-302", url = Adr.THE_CONTROL_PANELS_SCREENS_ARE_SERVER_WRITTEN_FORMS, type = "adr")
class TheStopCostsNameTheJarsStepsTest {

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private Job vesperaJob;

    @Test
    @Story("The operator stops an invocation from the control panel")
    @DisplayName("What a stop costs is said for each step a run goes through, in the order it goes through them, and for no other, each in words")
    void theStopCostsNameEachStepOfTheJobInTheOrderItRunsThem() {
        List<String> steps = List.copyOf(((SimpleJob) vesperaJob).getStepNames());
        List<String> rows = StopCosts.ROWS.stream().map(StopCosts.StopCost::step).toList();

        claim("the table has a row for each step of the job a run starts, named as the job names it, in the order the"
                        + " job runs them, and no other row",
                () -> assertThat(rows).containsExactlyElementsOf(steps));
        claim("and each row says in words what a stop there costs",
                () -> assertThat(StopCosts.ROWS).allSatisfy(row -> assertThat(row.cost()).isNotBlank()));
    }

    /**
     * The three steps the jar records under no stage's run, and the stage the window shows beside each (ADR-345 §3): the
     * census, which mints none, as stage 0, and the relevance floor and the relevance report, which record no completion
     * (ADR-118), as 5e and 5, the numbers the records give them.
     */
    private static final Map<String, String> NUMBERED = Map.of(
            "census", "0", "relevance-floor", "5e", "relevance-report", "5");

    /**
     * Each row of the window's steps shows its stage (ADR-344 §1, ADR-345 §3): the stage under whose runs the jar records
     * the step's completion, as the progress page's tab of runs reads it (ADR-269 §5), and, for the three steps the jar
     * records under no stage's run, the number the records give their stage. The window holds the rule as text, linking
     * no class of the jar (ADR-285 §3); this holds it to the jar's.
     */
    @Test
    @Story("The operator stops an invocation from the control panel")
    @DisplayName("The stage the window shows beside each step is the one the jar records the step's completion under, and the census, the relevance floor and the relevance report show 0, 5e and 5")
    @Link(name = "ADR-269", url = Adr.THE_PROGRESS_PAGE_IS_FOUR_TABS_AND_LISTS_THE_RUNS_THE_WORKING_DIRECTORY_HOLDS, type = "adr")
    @Link(name = "ADR-345", url = Adr.THE_COCKPIT_REPLACES_THE_BROWSER_CONTROL_PANEL_AND_ITS_TREE_WRITES_THE_FOLDERS_NOT_WALKED, type = "adr")
    void theStageShownBesideEachStepIsTheJarsOrTheNumberTheRecordsGiveIt() {
        Map<String, String> byTheJar = new LinkedHashMap<>();
        Map<String, String> byTheWindow = new LinkedHashMap<>();
        for (String step : ((SimpleJob) vesperaJob).getStepNames()) {
            byTheJar.put(step, InvocationReport.STEPS_OF_A_STAGE.entrySet().stream()
                    .filter(stage -> stage.getValue().contains(step)).map(Map.Entry::getKey).findFirst().orElse(""));
            byTheWindow.put(step, StopCosts.stageOf(step));
        }
        Map<String, String> expected = new LinkedHashMap<>(byTheJar);
        expected.putAll(NUMBERED);

        claim("the jar records each of its steps under a stage but the census, the relevance floor and the relevance"
                        + " report, so those three are the ones the window numbers",
                () -> assertThat(byTheJar.entrySet().stream().filter(each -> each.getValue().isEmpty())
                        .map(Map.Entry::getKey).toList())
                        .containsExactly("census", "relevance-floor", "relevance-report"));
        claim("and for each step of the job the window shows the stage the jar records it under, and for the census 0,"
                        + " the relevance floor 5e and the relevance report 5",
                () -> assertThat(byTheWindow).containsExactlyEntriesOf(expected));
    }
}
