package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.controlpanel.StopCosts;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.util.List;
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
 * every one, in the order the job runs them (ADR-302 §7, which ADR-339 keeps and ADR-341 §2 shows beside each step's
 * bar): the steps are read from the job as the jar builds it, and the rows from {@code StopCosts}, where the window
 * reads them.
 */
@CascadeSliceTest
@Import(StubbedExtractionBeans.class)
@Epic("Operation")
@Feature("The control panel")
@Issue("700")
@Link(name = "ADR-341", url = Adr.THE_CONTROL_PANELS_WINDOW_IS_ONE_COCKPIT, type = "adr")
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
}
