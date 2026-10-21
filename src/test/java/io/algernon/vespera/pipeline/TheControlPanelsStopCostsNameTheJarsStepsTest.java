package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.controlpanel.StopCostsShown;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
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
 * The rows of what a stop costs on the control panel's Stop confirmation name the steps of the job {@code vespera
 * run} starts, every one, in the order the job runs them (ADR-302 §7): the steps are read from the job as the jar
 * builds it, and the rows from the page the control panel serves while a {@code run} child of its own runs.
 */
@CascadeSliceTest
@Import(StubbedExtractionBeans.class)
@Epic("Operation")
@Feature("The control panel")
@Issue("561")
@Link(name = "ADR-302", url = Adr.THE_CONTROL_PANELS_SCREENS_ARE_SERVER_WRITTEN_FORMS, type = "adr")
class TheControlPanelsStopCostsNameTheJarsStepsTest {

    /** How many steps the job runs, which is how many rows Stop's confirmation holds. */
    private static final int SIXTEEN = 16;

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
    @DisplayName("What a stop costs is said for each of the sixteen steps a run goes through, in the order it goes through them, and for no other")
    void theStopCostsNameEachStepOfTheJobInTheOrderItRunsThem(@TempDir Path folder) throws IOException {
        List<String> steps = List.copyOf(((SimpleJob) vesperaJob).getStepNames());
        List<String> rows = StopCostsShown.stepsOfTheStopPage(folder);

        claim("the job a run starts has " + SIXTEEN + " steps",
                () -> assertThat(steps).hasSize(SIXTEEN));
        claim("and the confirmation of Stop has a row for each, named as the job names it, in the order the job runs"
                        + " them, and no other row",
                () -> assertThat(rows).containsExactlyElementsOf(steps));
    }
}
