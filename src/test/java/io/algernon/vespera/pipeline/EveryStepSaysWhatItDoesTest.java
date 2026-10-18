package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * Every step of the job says what it does, from one place (ADR-275 §4): each name {@code StepNames} holds has a
 * description of its own there, and the progress page's timeline opens each step's mark with it, as a tooltip.
 */
@Epic("Pipeline")
@Feature("The progress page")
@Link(name = "ADR-275", url = Adr.EVERY_DIV_SAYS_ON_HOVER_WHAT_IT_SHOWS_AND_EVERY_STEP_WHAT_IT_DOES, type = "adr")
class EveryStepSaysWhatItDoesTest {

    /** How many steps the job runs, each named once in {@code StepNames}. */
    private static final int STEPS_IN_THE_JOB = 16;

    /** A name no step has. */
    private static final String NO_STEP = "a-step-the-job-does-not-have";

    /** Long enough that no scheduled write lands while the test reads: the write read here is the listener's. */
    private static final Duration NO_SCHEDULED_WRITE_MEANWHILE = Duration.ofMinutes(5);

    /** A step's mark on the timeline: its attributes and what it holds. */
    private static final Pattern STEP = Pattern.compile("<g class=\"step\"([^>]*)>(.*?)</g>", Pattern.DOTALL);

    private static final Pattern DATA_STEP = Pattern.compile(" data-step=\"([^\"]*)\"");

    @TempDir
    Path workingDirectory;

    @Test
    @Story("Every step of the timeline says what it does")
    @DisplayName("Each of the sixteen steps of the job has a description of its own, none blank and no two the same, and a name that is no step's has none")
    void everyStepHasOneDescriptionOfItsOwn() {
        List<String> steps = everyStepName();
        List<String> descriptions = steps.stream().map(StepNames::description).toList();

        claim(
                "the step names are the job's " + STEPS_IN_THE_JOB + ", so every step is asked for its description",
                () -> assertThat(steps).hasSize(STEPS_IN_THE_JOB));
        claim(
                "each step's description says something: none is blank",
                () -> assertThat(descriptions).allSatisfy(description -> assertThat(description).isNotBlank()));
        claim(
                "and no two steps share one, so each says what its own step does",
                () -> assertThat(descriptions).doesNotHaveDuplicates());
        claim(
                "a name that is no step's is refused rather than described, so a step cannot be described by a name"
                        + " spelled differently from the job's",
                () -> assertThatThrownBy(() -> StepNames.description(NO_STEP))
                        .isInstanceOf(IllegalArgumentException.class));
    }

    @Test
    @Story("Every step of the timeline says what it does")
    @DisplayName("On the timeline each of the sixteen steps, started or not, opens with a tooltip holding what the step does")
    void everyStepOfTheTimelineSaysWhatTheStepDoes() throws IOException {
        ProgressPage page = new ProgressPage(workingDirectory, NO_SCHEDULED_WRITE_MEANWHILE, Clock.systemDefaultZone());
        JobExecution job = new JobExecution(1L, new JobInstance(1L, VesperaJobConfiguration.JOB_NAME), new JobParameters());
        job.setStartTime(LocalDateTime.now());
        job.setStatus(BatchStatus.STARTED);
        aStep(job, StepNames.CENSUS, BatchStatus.COMPLETED);
        aStep(job, StepNames.BYTE_LEVEL_REDUCTION, BatchStatus.STARTED);

        page.beforeJob(job);
        String overview = Files.readString(workingDirectory.resolve(ProgressPage.FILE_NAME), StandardCharsets.UTF_8);
        job.setStatus(BatchStatus.COMPLETED);
        page.afterJob(job);

        Map<String, String> marks = new LinkedHashMap<>();
        Matcher step = STEP.matcher(overview);
        while (step.find()) {
            Matcher name = DATA_STEP.matcher(step.group(1));
            marks.put(name.find() ? name.group(1) : "", step.group(2));
        }
        claim(
                "the timeline holds one mark for each of the job's " + STEPS_IN_THE_JOB + " steps, finished, running"
                        + " and not started among them",
                () -> assertThat(marks.keySet()).containsExactlyInAnyOrderElementsOf(everyStepName()));
        marks.forEach((name, mark) -> claim(
                name + "'s mark opens with a tooltip holding what the step does, written as text: "
                        + StepNames.description(name),
                () -> assertThat(mark).startsWith("<title>" + asText(StepNames.description(name)) + "</title>")));
    }

    /** Every step name {@code StepNames} holds: its static string constants. */
    private static List<String> everyStepName() {
        List<String> names = new ArrayList<>();
        for (Field field : StepNames.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers) && field.getType() == String.class) {
                try {
                    field.setAccessible(true);
                    names.add((String) field.get(null));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("a step name could not be read", e);
                }
            }
        }
        return names;
    }

    private static void aStep(JobExecution job, String name, BatchStatus status) {
        StepExecution execution = new StepExecution(name, job);
        execution.setStatus(status);
        execution.setStartTime(job.getStartTime());
        execution.setEndTime(status == BatchStatus.COMPLETED ? job.getStartTime() : null);
        job.addStepExecution(execution);
    }

    /** {@code text} as an element's text is written: the ampersand and the angle brackets as entities. */
    private static String asText(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
