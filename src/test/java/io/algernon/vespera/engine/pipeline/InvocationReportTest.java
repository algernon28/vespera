package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.NO_SCHEDULED_WRITE_MEANWHILE;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.STEPS_IN_JOB_ORDER;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.aClock;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.aJob;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.aReport;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.aStep;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepExecution;
import tools.jackson.databind.JsonNode;

/**
 * The invocation report, driven as Spring Batch drives its listener, with executions built by hand on a fixed clock
 * (ADR-362 section 3, section 4): a write that fails, two ids of one second, and each leg's state and whether it is open
 * in six jobs.
 */
@Epic("Pipeline")
@Feature("The invocation report")
@Issue("737")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
@Link(name = "ADR-324", url = Adr.THE_TIMELINE_FOLDS_INTO_FOUR_LEGS, type = "adr")
class InvocationReportTest {

    /** ADR-324 section 1's four legs, written out here and not read from the jar's constant. */
    private static final Map<String, List<String>> THE_FOUR_LEGS = Map.of(
            "deduplication", STEPS_IN_JOB_ORDER.subList(0, 6),
            "translation", STEPS_IN_JOB_ORDER.subList(6, 10),
            "clustering", STEPS_IN_JOB_ORDER.subList(10, 14),
            "generation", STEPS_IN_JOB_ORDER.subList(14, 16));

    private static final List<String> THE_FIVE_STATES =
            List.of("not started", "running", "stopped at a gate", "failed", "finished");

    @TempDir
    Path workingDirectory;

    @Test
    @Story("A report that cannot be written fails nothing")
    @DisplayName("With a folder where report.json goes, the job completes and one warning says the report could not be written")
    void aReportThatCannotBeWrittenFailsNothingAndIsWarnedOfOnce() throws IOException {
        Files.createDirectories(workingDirectory.resolve(InvocationReport.FILE_NAME).resolve("a-file-that-keeps-the-folder-there"));
        InvocationReport report = aReport(workingDirectory);
        JobSampler page = new JobSampler(workingDirectory, NO_SCHEDULED_WRITE_MEANWHILE, aClock());
        page.handTo(report);
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        ch.qos.logback.classic.Logger root =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.addAppender(logged);
        try {
            JobExecution job = aJob();
            aStep(job, StepNames.CENSUS, BatchStatus.COMPLETED, 0, 1L);

            page.beforeJob(job);
            job.setStatus(BatchStatus.COMPLETED);
            page.afterJob(job);
            report.runEnds(0, null, null);
        } finally {
            root.detachAppender(logged);
            logged.stop();
        }

        claim("the job went through its listener to the end with every write of the report failing, which fails nothing",
                () -> assertThat(workingDirectory.resolve(InvocationReport.FILE_NAME)).isDirectory());
        claim("exactly one warning says the report could not be written, naming what failed: the next is warned of"
                        + " only after a write has succeeded",
                () -> assertThat(logged.list)
                        .filteredOn(event -> event.getLevel() == Level.WARN
                                && event.getFormattedMessage().startsWith("the report could not be written: "))
                        .hasSize(1));
        claim("and no line at error says anything of the report",
                () -> assertThat(logged.list)
                        .filteredOn(event -> event.getLevel().isGreaterOrEqual(Level.ERROR))
                        .noneMatch(event -> event.getFormattedMessage().contains("report")));
    }

    @Test
    @Story("Each invocation keeps a copy of its own")
    @DisplayName("Two invocations whose ids fall in one second take the ids of that second and that second with -2, and each keeps its copy")
    void aSecondInvocationInTheSameSecondTakesASuffix() throws IOException {
        List<String> begun = new java.util.ArrayList<>();
        List<Boolean> copyUnwrittenWhenBegun = new java.util.ArrayList<>();
        InvocationReport report = SixJobsForTests.aReport(workingDirectory, invocation -> {
            begun.add(invocation);
            Path copy = workingDirectory.resolve("logs").resolve("report-" + invocation + ".json");
            try {
                copyUnwrittenWhenBegun.add(!Files.exists(copy) || Files.size(copy) == 0);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });

        report.begins("label");
        report.labelStarts();
        report.begins("label");
        report.labelStarts();

        JsonNode second = TheReportFile.in(workingDirectory);
        List<String> copies = TheReportFile.copiesIn(workingDirectory).stream()
                .map(copy -> copy.getFileName().toString()).toList();
        claim("the second invocation's id is the first's with -2, and each of the two has a copy in logs named by its id",
                () -> {
                    assertThat(second.get("invocation").get("id").asString()).isEqualTo("20261106T080140Z-2");
                    assertThat(copies).containsExactly("report-20261106T080140Z-2.json", "report-20261106T080140Z.json");
                });
        claim("each invocation's output is begun once, with the id its report names, before that report's first write:"
                        + " the copy named by the id still empty when the output is begun (ADR-364 section 3)",
                () -> {
                    assertThat(begun).containsExactly("20261106T080140Z", "20261106T080140Z-2");
                    assertThat(copyUnwrittenWhenBegun).containsExactly(true, true);
                });
        claim("a copy is not rewritten by a later invocation: the first's, begun and written once, is the first's alone",
                () -> assertThat(Files.readString(workingDirectory.resolve("logs").resolve("report-20261106T080140Z.json")))
                        .contains("\"id\":\"20261106T080140Z\""));
    }

    @Test
    @Story("The report says which parts of the job are open and in what state")
    @DisplayName("For six jobs, each leg's state and whether it is open are ADR-324's, the six draw all five states and open a leg for each of three reasons, and each step carries its leg")
    void eachLegsStateAndWhetherItIsOpenAreAdr324sInEveryJob() throws IOException {
        Map<String, JsonNode> written = new LinkedHashMap<>();
        SixJobsForTests.sixReports(workingDirectory).forEach((job, text) -> written.put(job, TheReportFile.parse(text)));

        Set<String> statesDrawn = new LinkedHashSet<>();
        Set<String> openFor = new LinkedHashSet<>();
        written.forEach((job, report) -> {
            Map<String, String> states = new LinkedHashMap<>();
            report.get("steps").forEach(step -> states.put(step.get("name").asString(), step.get("state").asString()));
            boolean last = "ended".equals(report.get("invocation").get("state").asString());
            claim("for the job " + job + ", the report names the four legs in order",
                    () -> assertThat(names(report.get("legs"))).containsExactly(
                            "deduplication", "translation", "clustering", "generation"));
            String firstGated = STEPS_IN_JOB_ORDER.stream()
                    .filter(step -> "stopped at a gate".equals(states.get(step))).findFirst().orElse(null);
            for (JsonNode leg : report.get("legs")) {
                String name = leg.get("name").asString();
                List<String> steps = THE_FOUR_LEGS.get(name);
                String state = expectedState(steps.stream().map(states::get).toList(), last);
                boolean open = state.equals("running") || state.equals("failed")
                        || (firstGated != null && steps.contains(firstGated));
                claim("for the job " + job + ", the leg " + name + " is " + state + (open ? " and open" : " and not open"),
                        () -> {
                            assertThat(leg.get("state").asString()).isEqualTo(state);
                            assertThat(leg.get("open").asBoolean()).isEqualTo(open);
                        });
                statesDrawn.add(state);
                if (open) {
                    openFor.add(state.equals("running") || state.equals("failed")
                            ? state : "stopped at a gate");
                }
            }
            claim("for the job " + job + ", each step carries the leg ADR-324 section 1 puts it in",
                    () -> report.get("steps").forEach(step -> assertThat(THE_FOUR_LEGS.get(step.get("leg").asString()))
                            .contains(step.get("name").asString())));
        });

        claim("the six jobs, together, draw a leg in each of the five states", 
                () -> assertThat(statesDrawn).containsExactlyInAnyOrderElementsOf(THE_FIVE_STATES));
        claim("and open a leg running, a leg failed and a leg holding the first step stopped at a gate",
                () -> assertThat(openFor).containsExactlyInAnyOrder("running", "failed", "stopped at a gate"));
    }

    /** ADR-324 section 3, written out again: the first rule that holds. */
    private static String expectedState(List<String> steps, boolean last) {
        if (steps.stream().allMatch("not started"::equals)) {
            return "not started";
        }
        if (steps.stream().allMatch("finished"::equals)) {
            return "finished";
        }
        if (steps.contains("failed")) {
            return "failed";
        }
        if (steps.contains("stopped at a gate")) {
            return "stopped at a gate";
        }
        if (steps.contains("running")) {
            return "running";
        }
        return last ? "failed" : "running";
    }

    private static List<String> names(JsonNode array) {
        List<String> names = new ArrayList<>();
        array.forEach(each -> names.add(each.get("name").asString()));
        return names;
    }
}
