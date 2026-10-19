package io.algernon.vespera.pipeline;

import static io.algernon.vespera.HoverTexts.attributeOf;
import static io.algernon.vespera.HoverTexts.divsOf;
import static io.algernon.vespera.HoverTexts.escapedForItsAttributes;
import static io.algernon.vespera.HoverTexts.titleOf;
import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.Runs;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepExecution;

/**
 * Every {@code <div>} of the progress pages carries a hover text saying what it shows (ADR-275 §1 to §3). The pages
 * are written once for the class, as Spring Batch drives the listener: one invocation for each of the five steps
 * that remove occurrences, that step running with what entered it held, so that every verdict kind's figure is
 * drawn, each written as the job starts and again as it ends with its counts moved and a reading of one run; and
 * the label command's page as it starts and as it ends.
 */
@Epic("Pipeline")
@Feature("The progress page")
@Link(name = "ADR-275", url = Adr.EVERY_DIV_SAYS_ON_HOVER_WHAT_IT_SHOWS_AND_EVERY_STEP_WHAT_IT_DOES, type = "adr")
@Link(name = "ADR-277", url = Adr.CODE_IS_RECOGNISED_BY_A_NAME_ENDING_OR_A_GENERATORS_MARK, type = "adr")
class EveryDivOfTheProgressPagesSaysWhatItShowsTest {

    /** The four tabs of an invocation of the run command. */
    private static final List<String> TABS = List.of(
            ProgressPage.FILE_NAME, ProgressPage.STEPS_FILE_NAME, ProgressPage.SAMPLES_FILE_NAME, ProgressPage.RUNS_FILE_NAME);

    /** Each step that removes occurrences, and the verdict kinds whose figures the page draws while it does. */
    private static final Map<String, List<String>> KINDS_OF_EACH_STEP_THAT_REMOVES = new LinkedHashMap<>();

    static {
        KINDS_OF_EACH_STEP_THAT_REMOVES.put(
                StepNames.BYTE_LEVEL_REDUCTION, List.of("broken", "duplicate-of", "superseded-by", "out-of-scope", "code"));
        KINDS_OF_EACH_STEP_THAT_REMOVES.put(StepNames.EXTRACTION, List.of("extraction-failed", "degenerate-output"));
        KINDS_OF_EACH_STEP_THAT_REMOVES.put(StepNames.CONTENT_REDUNDANCY, List.of("redundant-with"));
        KINDS_OF_EACH_STEP_THAT_REMOVES.put(StepNames.TRANSLATION, List.of("translation-of"));
        KINDS_OF_EACH_STEP_THAT_REMOVES.put(StepNames.RELEVANCE_FLOOR, List.of("below-threshold"));
    }

    /** The figures every write of the run command draws, whatever step runs. */
    private static final List<String> FIGURES_OF_EVERY_WRITE = List.of("steps", "step", "elapsed", "database", "survivors");

    /** How many occurrences entered the step that removes them, held in its step's context. */
    private static final long ENTERED = 10;

    /** How many verdicts of each of its kinds that step has recorded by the last write: none at the first. */
    private static final long RECORDED_BY_THE_LAST_WRITE = 2;

    /** Long enough that no scheduled write lands while the class writes: every write read here is the listener's. */
    private static final Duration NO_SCHEDULED_WRITE_MEANWHILE = Duration.ofMinutes(5);

    /** A run the one reading of the runs holds, so that the tab of runs draws its table at the last write. */
    private static final String A_RUN_HELD_BY_THIS_TEST = "a-run-held-by-this-test";

    /** A figure: the opening tag of its {@code <div>} and the value it shows. */
    private static final Pattern FIGURE = Pattern.compile(
            "(<div[^>]* class=\"tile\"[^>]*>)<span class=\"label\">[^<]*</span><span class=\"value\">([^<]*)</span>");

    @TempDir
    static Path workingDirectories;

    /** Every tab of every invocation of the run command, by what it is, in the order written. */
    private static final Map<String, String> RUN_PAGES = new LinkedHashMap<>();

    /** The overview tab of each of those writes, by what it is. */
    private static final Map<String, String> OVERVIEWS = new LinkedHashMap<>();

    /** The label command's page as it starts and as it ends. */
    private static final Map<String, String> LABEL_PAGES = new LinkedHashMap<>();

    @BeforeAll
    static void writeEveryPage() throws IOException {
        for (Map.Entry<String, List<String>> removing : KINDS_OF_EACH_STEP_THAT_REMOVES.entrySet()) {
            String step = removing.getKey();
            Path directory = Files.createDirectories(workingDirectories.resolve(step));
            AtomicReference<Map<String, Long>> recorded = new AtomicReference<>(Map.of());
            ProgressPage page = new ProgressPage(
                    directory,
                    NO_SCHEDULED_WRITE_MEANWHILE,
                    Clock.systemDefaultZone(),
                    null,
                    recorded::get,
                    () -> new ProgressPage.RunsRead(
                            List.of(new Runs.Listed(A_RUN_HELD_BY_THIS_TEST, StageModules.EXTRACTION.stage(), 1L)),
                            Map.of(),
                            Map.of(A_RUN_HELD_BY_THIS_TEST, Map.of())));
            JobExecution job = new JobExecution(1L, new JobInstance(1L, VesperaJobConfiguration.JOB_NAME), new JobParameters());
            job.setStartTime(LocalDateTime.now());
            job.setStatus(BatchStatus.STARTED);
            StepExecution running = new StepExecution(step, job);
            running.setStatus(BatchStatus.STARTED);
            running.setStartTime(job.getStartTime());
            running.getExecutionContext().putLong(ProgressPage.ENTERED, ENTERED);
            job.addStepExecution(running);

            page.beforeJob(job);
            read(directory, "the first write while " + step + " runs");
            Map<String, Long> counted = new TreeMap<>();
            removing.getValue().forEach(kind -> counted.put(kind, RECORDED_BY_THE_LAST_WRITE));
            recorded.set(counted);
            job.setStatus(BatchStatus.COMPLETED);
            page.afterJob(job);
            read(directory, "the last write after " + step + " ran");
        }

        Path labelDirectory = Files.createDirectories(workingDirectories.resolve("label"));
        ProgressPage labelPage = new ProgressPage(labelDirectory, NO_SCHEDULED_WRITE_MEANWHILE, Clock.systemDefaultZone());
        labelPage.commandStarts("vespera label");
        LABEL_PAGES.put("the label command's page as it starts", text(labelDirectory.resolve(ProgressPage.FILE_NAME)));
        Map<String, Integer> answers = new LinkedHashMap<>();
        answers.put("new", 1);
        answers.put("changed", 0);
        answers.put("unchanged", 0);
        answers.put("still blank", 0);
        labelPage.commandEnds("recorded", answers);
        LABEL_PAGES.put("the label command's page as it ends", text(labelDirectory.resolve(ProgressPage.FILE_NAME)));
    }

    @Test
    @Story("Every part of a page says what it shows when the pointer rests on it")
    @DisplayName("Every box of the four tabs carries a description shown on hover, at the first write and the last, whichever step is removing documents")
    void everyDivOfTheFourTabsCarriesAHoverTextAtTheFirstWriteAndTheLast() {
        Set<String> classesSeen = new LinkedHashSet<>();
        RUN_PAGES.values().forEach(page -> divsOf(page).forEach(tag -> classesSeen.add(attributeOf(tag, "class"))));
        claim(
                "the pages written hold the row of figures, the figures and the cards the tables stand in, so the"
                        + " claims below are about every kind of box the tabs draw",
                () -> assertThat(classesSeen).contains("tiles", "tile", "table-card"));
        RUN_PAGES.forEach((what, page) -> claim(
                "every box of " + what + " carries a description that is not blank",
                () -> assertThat(divsOf(page)).allSatisfy(tag -> assertThat(titleOf(tag)).as(tag).isNotBlank())));
    }

    @Test
    @Story("Every part of a page says what it shows when the pointer rests on it")
    @DisplayName("No two figures of a page share a description, a figure's description stays the same while its value changes, and every kind of removal has one")
    void everyTileSaysWhatItShowsInWordsOfItsOwn() {
        Map<String, Set<String>> descriptions = new TreeMap<>();
        Map<String, Set<String>> values = new TreeMap<>();
        OVERVIEWS.forEach((what, page) -> {
            List<String> onThisPage = new ArrayList<>();
            Matcher figure = FIGURE.matcher(page);
            while (figure.find()) {
                String key = attributeOf(figure.group(1), "data-tile");
                String description = titleOf(figure.group(1));
                onThisPage.add(description);
                descriptions.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(String.valueOf(description));
                values.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(figure.group(2));
            }
            claim(
                    "no two figures of " + what + " share a description: each says what its own figure shows",
                    () -> assertThat(onThisPage).isNotEmpty().doesNotHaveDuplicates());
        });
        List<String> everyKind = KINDS_OF_EACH_STEP_THAT_REMOVES.values().stream().flatMap(List::stream).toList();
        claim(
                "the figures drawn over every write are the five every write of the run command draws and one for"
                        + " each of the " + everyKind.size() + " kinds of removal: " + everyKind,
                () -> assertThat(descriptions.keySet()).containsAll(FIGURES_OF_EVERY_WRITE).containsAll(everyKind));
        claim(
                "each kind's figure showed " + 0 + " at the first write and " + RECORDED_BY_THE_LAST_WRITE + " at the"
                        + " last, so its value changed between the writes compared below",
                () -> everyKind.forEach(kind ->
                        assertThat(values.get(kind)).as(kind).containsExactlyInAnyOrder("0", String.valueOf(RECORDED_BY_THE_LAST_WRITE))));
        claim(
                "and every figure has one description over every write, whatever its value: a description holds no"
                        + " count, size or time of the invocation",
                () -> descriptions.forEach((key, written) -> assertThat(written).as(key).hasSize(1)));
    }

    @Test
    @Story("Every part of a page says what it shows when the pointer rests on it")
    @DisplayName("Every box of the label command's page, its figures and the card of its answers, carries a description shown on hover, as it starts and as it ends")
    void everyDivOfTheLabelPageCarriesAHoverText() {
        claim(
                "the label command's page as it ends holds the card its table of answers stands in",
                () -> assertThat(divsOf(LABEL_PAGES.get("the label command's page as it ends")))
                        .anySatisfy(tag -> assertThat(attributeOf(tag, "class")).isEqualTo("table-card")));
        LABEL_PAGES.forEach((what, page) -> claim(
                what + " holds its row of figures and its figures, and every box on it carries a description that is"
                        + " not blank",
                () -> assertThat(divsOf(page))
                        .anySatisfy(tag -> assertThat(attributeOf(tag, "class")).isEqualTo("tile"))
                        .allSatisfy(tag -> assertThat(titleOf(tag)).as(tag).isNotBlank())));
    }

    @Test
    @Story("Every part of a page says what it shows when the pointer rests on it")
    @DisplayName("Every description stands in its box's tag escaped: no angle bracket, no quote and no bare ampersand in any value")
    void everyHoverTextIsEscapedForTheAttributeItStandsIn() {
        Map<String, String> everyPage = new LinkedHashMap<>(RUN_PAGES);
        everyPage.putAll(LABEL_PAGES);
        claim(
                "the pages written hold boxes, so the claims below are about descriptions that stand somewhere",
                () -> assertThat(everyPage.values().stream().mapToInt(page -> divsOf(page).size()).sum()).isPositive());
        everyPage.forEach((what, page) -> claim(
                "every box of " + what + " carries a description and opens with attributes each a name and a value in"
                        + " double quotes holding no angle bracket and no quote, every ampersand opening an entity, so"
                        + " no description ends its attribute or its tag",
                () -> assertThat(divsOf(page)).allSatisfy(tag -> {
                    assertThat(titleOf(tag)).as(tag).isNotNull();
                    assertThat(escapedForItsAttributes(tag)).as(tag).isTrue();
                })));
    }

    /** The four tabs in {@code directory} as they are now, each under what it is, the overview also among {@link #OVERVIEWS}. */
    private static void read(Path directory, String write) throws IOException {
        for (String tab : TABS) {
            String page = text(directory.resolve(tab));
            RUN_PAGES.put(tab + " at " + write, page);
            if (tab.equals(ProgressPage.FILE_NAME)) {
                OVERVIEWS.put(tab + " at " + write, page);
            }
        }
    }

    /** A page's text, or nothing where it is not there, so that a missing page fails its claim. */
    private static String text(Path page) throws IOException {
        return Files.isRegularFile(page) ? Files.readString(page, StandardCharsets.UTF_8) : "";
    }
}
