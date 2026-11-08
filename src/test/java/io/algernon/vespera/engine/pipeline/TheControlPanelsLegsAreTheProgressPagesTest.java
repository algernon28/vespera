package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.ui.Legs;
import io.algernon.vespera.ui.ProgressFile;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepExecution;

/**
 * The control panel's copy of the legs is the progress page's (ADR-361 §2): the control panel links no class of the jar
 * (ADR-285 §3), so it holds ADR-324 §1's table as text, in {@code Legs}, and reads each leg's state and whether it is
 * open off {@code progress.json} by ADR-324 §3 and §4. This holds the copy to what {@code ProgressPage} draws in
 * {@code progress.html}'s timeline, the table, the states and the legs written open, so that neither changes without
 * the other. It reads the page's drawing and not its constant, which stays private.
 *
 * <p>The page is driven as Spring Batch drives its listener, with executions built by hand on a fixed clock, as
 * {@code ProgressPageTimelineLegsTest} drives it; {@code progress.json} is read with the control panel's own
 * {@code ProgressFile}, as the window reads it. The class path holds both, as
 * {@code TheControlPanelReadsTheProgressTheJarWritesTest}'s does.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("726")
@Link(name = "ADR-361", url = Adr.THE_COCKPITS_STEPS_FOLD_INTO_THE_PROGRESS_PAGES_FOUR_LEGS, type = "adr")
@Link(name = "ADR-324", url = Adr.THE_TIMELINE_FOLDS_INTO_FOUR_LEGS, type = "adr")
@Link(name = "ADR-285", url = Adr.THE_CONTROL_PANEL_IS_A_SECOND_JAR_LINKING_NO_CLASS_OF_THE_JAR, type = "adr")
class TheControlPanelsLegsAreTheProgressPagesTest {

    /** Long enough that no scheduled write lands while a test runs: the writes these tests read are the listener's. */
    private static final Duration NO_SCHEDULED_WRITE_MEANWHILE = Duration.ofMinutes(5);

    private static final ZoneId ZONE = ZoneId.systemDefault();

    /** When each job starts; every step's start and end below is a number of seconds after it. */
    private static final Instant JOB_START = Instant.parse("2026-11-06T08:00:00Z");

    /** Seconds from the job's start to every write these tests make: the page's clock stands still there. */
    private static final long WRITTEN_AT = 100;

    /** The sixteen steps in the order the job runs them. */
    private static final List<String> STEPS_IN_JOB_ORDER = List.of(
            StepNames.CENSUS,
            StepNames.BYTE_LEVEL_REDUCTION,
            StepNames.EXTRACTION,
            StepNames.CONTENT_CENSUS,
            StepNames.REDUNDANCY_SIGNATURE,
            StepNames.CONTENT_REDUNDANCY,
            StepNames.SEED_EXTRACTION,
            StepNames.SEED_CORPUS_COMPARISON,
            StepNames.EMBEDDING_SCORING,
            StepNames.TRANSLATION,
            StepNames.RELEVANCE_SCORING,
            StepNames.RELEVANCE_FLOOR,
            StepNames.CLUSTERING,
            StepNames.RELEVANCE_REPORT,
            StepNames.ARRANGEMENT,
            StepNames.GENERATION);

    /** The five states a leg is drawn in (ADR-324 §3, ADR-265 §1), every one of which the jobs below draw. */
    private static final List<String> THE_FIVE_STATES = List.of(
            "not started", "running", "stopped at a gate", "failed", "finished");

    /** The legs ADR-324 §1 folds the job into: deduplication, translation, clustering and generation. */
    private static final int FOUR_LEGS = 4;

    private static final String CARD_HEADING = "<h2>Timeline of the steps</h2>";

    /** A leg: its name, whether it is written open, its summary, and what follows the summary. */
    private static final Pattern LEG = Pattern.compile(
            "<details class=\"leg\" data-leg=\"([^\"]*)\"( open)?>\\s*<summary>(.*?)</summary>(.*?)</details>",
            Pattern.DOTALL);

    private static final Pattern MARK = Pattern.compile(
            "<g class=\"step\" data-step=\"([^\"]*)\" data-state=\"([^\"]*)\">", Pattern.DOTALL);

    private static final Pattern LEG_SPAN = Pattern.compile("<g class=\"leg-span\" data-state=\"([^\"]*)\">");

    @TempDir
    Path workingDirectory;

    /** What the page drew of its legs at one write, and what {@code Legs} read off the file written with it. */
    record LegsDrawn(Map<String, String> states, List<String> open) {
    }

    @Test
    @Story("The window folds the steps into the progress page's four parts")
    @DisplayName("The control panel's table of the four parts is the progress page's: the same parts in the same order, each holding the same steps in the same order")
    void theControlPanelsTableOfLegsIsTheProgressPages() throws IOException {
        JobExecution job = aJob();
        aStep(job, StepNames.CENSUS, BatchStatus.COMPLETED, 0, 10L);
        aStep(job, StepNames.BYTE_LEVEL_REDUCTION, BatchStatus.FAILED, 10, 15L);
        String page = lastWrite(job, BatchStatus.FAILED, "table").page();

        Map<String, List<String>> byThePage = new LinkedHashMap<>();
        Matcher leg = LEG.matcher(card(page));
        while (leg.find()) {
            List<String> steps = new ArrayList<>();
            Matcher mark = MARK.matcher(leg.group(4));
            while (mark.find()) {
                steps.add(mark.group(1));
            }
            byThePage.put(leg.group(1), steps);
        }
        Map<String, List<String>> byTheWindow = new LinkedHashMap<>();
        Legs.ALL.forEach(each -> byTheWindow.put(each.name(), each.steps()));

        claim("the progress page draws four parts, which together hold each of the job's sixteen steps once, in the order"
                        + " the job runs them",
                () -> {
                    assertThat(byThePage).hasSize(FOUR_LEGS);
                    assertThat(byThePage.values().stream().flatMap(List::stream).toList())
                            .containsExactlyElementsOf(STEPS_IN_JOB_ORDER);
                });
        claim("and the control panel's table holds the same parts, in the same order, each with the same steps in the"
                        + " same order",
                () -> assertThat(byTheWindow).containsExactlyEntriesOf(byThePage));
    }

    @Test
    @Story("The window folds the steps into the progress page's four parts")
    @DisplayName("For each of six jobs, the state of each part and which parts are open, as the control panel reads them off the file written beside the progress page, are what the page draws")
    void eachLegsStateAndWhetherItIsOpenAreThePagesInEveryJob() throws IOException {
        Map<String, Drawn> drawn = new LinkedHashMap<>();
        drawn.put("running past a gate", firstWrite(runningPastAGate(), "running-past-a-gate"));
        drawn.put("failed beside a gate", firstWrite(failedBesideAGate(), "failed-beside-a-gate"));
        drawn.put("between two steps while running", firstWrite(betweenTwoSteps(), "between-running"));
        drawn.put("between two steps at the last write",
                lastWrite(betweenTwoSteps(), BatchStatus.FAILED, "between-last"));
        drawn.put("every step finished", lastWrite(everyStepFinished(), BatchStatus.COMPLETED, "finished"));
        drawn.put("first stopped at a gate in translation",
                lastWrite(firstStoppedAtAGateInTranslation(), BatchStatus.COMPLETED, "gate"));

        Map<String, LegsDrawn> byThePage = new LinkedHashMap<>();
        Map<String, LegsDrawn> byTheWindow = new LinkedHashMap<>();
        drawn.forEach((job, written) -> {
            byThePage.put(job, legsOf(written.page()));
            ProgressFile.Progress progress = ProgressFile.read(written.json());
            byTheWindow.put(job, new LegsDrawn(Legs.statesOf(progress), Legs.openOf(progress)));
        });
        Set<String> statesDrawn = new LinkedHashSet<>();
        byThePage.values().forEach(legs -> statesDrawn.addAll(legs.states().values()));
        Set<String> openFor = new LinkedHashSet<>();
        byThePage.values().forEach(legs -> legs.open().forEach(name -> openFor.add(legs.states().get(name))));

        claim("the six jobs, together, draw a part in each of the five states the page has",
                () -> assertThat(statesDrawn).containsExactlyInAnyOrderElementsOf(THE_FIVE_STATES));
        claim("and open a part running, a part failed and a part holding the first step stopped at a gate",
                () -> assertThat(openFor).containsExactlyInAnyOrder("running", "failed", "stopped at a gate"));
        byThePage.forEach((job, page) -> claim(
                "for the job " + job + ", the control panel reads each part's state as the page draws it",
                () -> assertThat(byTheWindow.get(job).states()).containsExactlyEntriesOf(page.states())));
        byThePage.forEach((job, page) -> claim(
                "for the job " + job + ", the control panel opens the parts the page writes open, in the same order",
                () -> assertThat(byTheWindow.get(job).open()).containsExactlyElementsOf(page.open())));
    }

    /**
     * Deduplication finished; translation's comparison and translation stopped at a gate beside two finished steps;
     * clustering running its floor; generation not started. Read at a write before the last.
     */
    private static JobExecution runningPastAGate() {
        JobExecution job = aJob();
        long second = 0;
        for (String step : STEPS_IN_JOB_ORDER.subList(0, STEPS_IN_JOB_ORDER.indexOf(StepNames.SEED_EXTRACTION))) {
            aStep(job, step, BatchStatus.COMPLETED, second, ++second);
        }
        aStep(job, StepNames.SEED_EXTRACTION, BatchStatus.COMPLETED, second, ++second);
        gated(job, aStep(job, StepNames.SEED_CORPUS_COMPARISON, BatchStatus.COMPLETED, second, ++second));
        aStep(job, StepNames.EMBEDDING_SCORING, BatchStatus.COMPLETED, second, ++second);
        gated(job, aStep(job, StepNames.TRANSLATION, BatchStatus.COMPLETED, second, ++second));
        aStep(job, StepNames.RELEVANCE_SCORING, BatchStatus.COMPLETED, second, ++second);
        aStep(job, StepNames.RELEVANCE_FLOOR, BatchStatus.STARTED, second, null);
        return job;
    }

    /**
     * The census stopped at a gate and byte-level reduction failed beside it; seed extraction stopped at a gate and the
     * comparison running beside it. Read at a write before the last.
     */
    private static JobExecution failedBesideAGate() {
        JobExecution job = aJob();
        gated(job, aStep(job, StepNames.CENSUS, BatchStatus.COMPLETED, 0, 1L));
        aStep(job, StepNames.BYTE_LEVEL_REDUCTION, BatchStatus.FAILED, 1, 2L);
        gated(job, aStep(job, StepNames.SEED_EXTRACTION, BatchStatus.COMPLETED, 2, 3L));
        aStep(job, StepNames.SEED_CORPUS_COMPARISON, BatchStatus.STARTED, 3, null);
        return job;
    }

    /** The census and byte-level reduction finished, and extraction not yet started. */
    private static JobExecution betweenTwoSteps() {
        JobExecution job = aJob();
        aStep(job, StepNames.CENSUS, BatchStatus.COMPLETED, 0, 10L);
        aStep(job, StepNames.BYTE_LEVEL_REDUCTION, BatchStatus.COMPLETED, 10, 15L);
        return job;
    }

    /** Every one of the sixteen steps finished. */
    private static JobExecution everyStepFinished() {
        JobExecution job = aJob();
        long second = 0;
        for (String step : STEPS_IN_JOB_ORDER) {
            aStep(job, step, BatchStatus.COMPLETED, second, ++second);
        }
        return job;
    }

    /** Deduplication and seed extraction finished, and every step from the comparison on stopped at a gate. */
    private static JobExecution firstStoppedAtAGateInTranslation() {
        JobExecution job = aJob();
        long second = 0;
        for (String step : STEPS_IN_JOB_ORDER.subList(0, STEPS_IN_JOB_ORDER.indexOf(StepNames.SEED_CORPUS_COMPARISON))) {
            aStep(job, step, BatchStatus.COMPLETED, second, ++second);
        }
        for (String step : STEPS_IN_JOB_ORDER.subList(STEPS_IN_JOB_ORDER.indexOf(StepNames.SEED_CORPUS_COMPARISON),
                STEPS_IN_JOB_ORDER.size())) {
            gated(job, aStep(job, step, BatchStatus.COMPLETED, second, ++second));
        }
        return job;
    }

    /** The page and the file the jar wrote at one write. */
    record Drawn(String page, String json) {
    }

    /** The first write of {@code job}'s page and file, in a working directory of its own named {@code which}. */
    private Drawn firstWrite(JobExecution job, String which) throws IOException {
        Path directory = Files.createDirectories(workingDirectory.resolve(which));
        ProgressPage progressPage = new ProgressPage(directory, NO_SCHEDULED_WRITE_MEANWHILE, aClock());
        progressPage.beforeJob(job);
        Drawn drawn = read(directory);
        job.setStatus(BatchStatus.COMPLETED);
        progressPage.afterJob(job);
        return drawn;
    }

    /** The last write of {@code job}'s page and file, the job ended {@code ending}, in a working directory named {@code which}. */
    private Drawn lastWrite(JobExecution job, BatchStatus ending, String which) throws IOException {
        Path directory = Files.createDirectories(workingDirectory.resolve(which));
        ProgressPage progressPage = new ProgressPage(directory, NO_SCHEDULED_WRITE_MEANWHILE, aClock());
        progressPage.beforeJob(job);
        job.setStatus(ending);
        progressPage.afterJob(job);
        return read(directory);
    }

    private static Drawn read(Path directory) throws IOException {
        Path page = directory.resolve(ProgressPage.FILE_NAME);
        Path json = directory.resolve(ProgressFile.FILE_NAME);
        claim("the listener wrote the progress page and the file beside it",
                () -> assertThat(List.of(page, json)).allSatisfy(file -> assertThat(file).isRegularFile()));
        return new Drawn(Files.readString(page, StandardCharsets.UTF_8), Files.readString(json, StandardCharsets.UTF_8));
    }

    /** Each leg's state as its bar's group is drawn in, and the legs written open, in the card's order. */
    private static LegsDrawn legsOf(String page) {
        Map<String, String> states = new LinkedHashMap<>();
        List<String> open = new ArrayList<>();
        Matcher leg = LEG.matcher(card(page));
        while (leg.find()) {
            Matcher span = LEG_SPAN.matcher(leg.group(3));
            states.put(leg.group(1), span.find() ? span.group(1) : "");
            if (leg.group(2) != null) {
                open.add(leg.group(1));
            }
        }
        return new LegsDrawn(states, open);
    }

    /** The timeline's card, from its heading to the card's close, or nothing where the page has none. */
    private static String card(String page) {
        int from = page.indexOf(CARD_HEADING);
        if (from < 0) {
            return "";
        }
        int to = page.indexOf("</figure>", from);
        return to < 0 ? page.substring(from) : page.substring(from, to);
    }

    private static Clock aClock() {
        return Clock.fixed(JOB_START.plusSeconds(WRITTEN_AT), ZONE);
    }

    private static LocalDateTime at(long seconds) {
        return LocalDateTime.ofInstant(JOB_START.plusSeconds(seconds), ZONE);
    }

    private static JobExecution aJob() {
        JobExecution job = new JobExecution(1L, new JobInstance(1L, VesperaJobConfiguration.JOB_NAME), new JobParameters());
        job.setStartTime(at(0));
        job.setStatus(BatchStatus.STARTED);
        return job;
    }

    /** A step execution of {@code job} in {@code status}, started and, where {@code end} is not null, ended then. */
    private static StepExecution aStep(JobExecution job, String step, BatchStatus status, long start, Long end) {
        StepExecution execution = new StepExecution(step, job);
        execution.setStatus(status);
        execution.setStartTime(at(start));
        execution.setEndTime(end == null ? null : at(end));
        job.addStepExecution(execution);
        return execution;
    }

    /** Records in {@code job} that {@code step} ended at a shut gate, as the step itself records it. */
    private static StepExecution gated(JobExecution job, StepExecution step) {
        new ShutGates(job.getExecutionContext()).record(step.getStepName());
        return step;
    }
}
