package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.STEPS_IN_JOB_ORDER;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ui.InvocationReportFile;
import io.algernon.vespera.ui.Legs;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The control panel's copy of the legs is the report's (ADR-362 section 8, ADR-361 section 2): the control panel links
 * no class of the jar (ADR-285 section 3), so it holds ADR-324 section 1's table as text, in {@code Legs}, and reads each
 * leg's state and whether it is open off the report by ADR-324 section 3 and section 4. This holds the copy to the
 * {@code legs} and the {@code leg} of each step in the {@code report.json} the jar writes, so that neither changes
 * without the other. The six jobs are {@code SixJobsForTests}', the report's own test's, driven as Spring Batch drives
 * the listener; {@code report.json} is read with the control panel's own {@code InvocationReportFile}, as the window reads
 * it.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("739")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
@Link(name = "ADR-361", url = Adr.THE_COCKPITS_STEPS_FOLD_INTO_THE_PROGRESS_PAGES_FOUR_LEGS, type = "adr")
@Link(name = "ADR-324", url = Adr.THE_TIMELINE_FOLDS_INTO_FOUR_LEGS, type = "adr")
class TheControlPanelsLegsAreTheReportsTest {

    /** The legs ADR-324 section 1 folds the job into: deduplication, translation, clustering and generation. */
    private static final int FOUR_LEGS = 4;

    /** The five states a leg is drawn in (ADR-324 section 3, ADR-265 section 1), every one of which the six jobs draw. */
    private static final List<String> THE_FIVE_STATES = List.of(
            "not started", "running", "stopped at a gate", "failed", "finished");

    @TempDir
    Path workingDirectory;

    @Test
    @Story("The window folds the steps into the report's four parts")
    @DisplayName("The control panel's table of the four parts is the report's: the same parts in the same order, each step in the part the report gives it, and the four together are the job's steps once")
    void theControlPanelsTableOfLegsIsTheReports() throws IOException {
        InvocationReportFile.Report report = InvocationReportFile.read(
                SixJobsForTests.sixReports(workingDirectory).values().iterator().next());

        Map<String, List<String>> byTheReport = new LinkedHashMap<>();
        report.legs().forEach(leg -> byTheReport.put(leg.name(), new ArrayList<>()));
        report.steps().forEach(step -> byTheReport.get(step.leg()).add(step.name()));
        Map<String, List<String>> byTheWindow = new LinkedHashMap<>();
        Legs.ALL.forEach(each -> byTheWindow.put(each.name(), each.steps()));

        claim("the report names four parts, which together hold each of the job's sixteen steps once, in the order the"
                        + " job runs them",
                () -> {
                    assertThat(byTheReport).hasSize(FOUR_LEGS);
                    assertThat(byTheReport.values().stream().flatMap(List::stream).toList())
                            .containsExactlyElementsOf(STEPS_IN_JOB_ORDER);
                });
        claim("and the control panel's table holds the same parts, in the same order, each with the same steps in the"
                        + " same order",
                () -> assertThat(byTheWindow).containsExactlyEntriesOf(byTheReport));
    }

    @Test
    @Story("The window folds the steps into the report's four parts")
    @DisplayName("For each of six jobs, the state of each part and which parts are open, as the control panel reads them off the report's steps, are the report's")
    void eachLegsStateAndWhetherItIsOpenAreTheReportsInEveryJob() throws IOException {
        Map<String, InvocationReportFile.Report> written = new LinkedHashMap<>();
        SixJobsForTests.sixReports(workingDirectory)
                .forEach((job, text) -> written.put(job, InvocationReportFile.read(text)));

        Set<String> statesDrawn = new LinkedHashSet<>();
        Set<String> openFor = new LinkedHashSet<>();
        written.forEach((job, report) -> {
            Map<String, String> byTheReport = new LinkedHashMap<>();
            List<String> openByTheReport = new ArrayList<>();
            report.legs().forEach(leg -> {
                byTheReport.put(leg.name(), leg.state());
                if (leg.open()) {
                    openByTheReport.add(leg.name());
                    openFor.add(leg.state());
                }
            });
            statesDrawn.addAll(byTheReport.values());
            claim("for the job " + job + ", the control panel reads each part's state as the report gives it",
                    () -> assertThat(Legs.statesOf(report)).containsExactlyEntriesOf(byTheReport));
            claim("for the job " + job + ", the control panel opens the parts the report writes open, in the same order",
                    () -> assertThat(Legs.openOf(report)).containsExactlyElementsOf(openByTheReport));
        });

        claim("the six jobs, together, draw a part in each of the five states the report has",
                () -> assertThat(statesDrawn).containsExactlyInAnyOrderElementsOf(THE_FIVE_STATES));
        claim("and open a part running, a part failed and a part holding the first step stopped at a gate",
                () -> assertThat(openFor).containsExactlyInAnyOrder("running", "failed", "stopped at a gate"));
    }
}
