package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.Runs;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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
 * What the progress page shows of code (ADR-277 §10): byte-level reduction's tiles gain one for code, labelled as
 * kept, its fifth series on the chart of its verdicts, and the tab of runs and the page of samples a column for it,
 * after out-of-scope. The page is driven as Spring Batch drives its listener, with the verdicts recorded handed to it
 * as the ledger hands them, so each write is one the test made.
 */
@Epic("Pipeline")
@Feature("The progress page")
@Issue("558")
@Link(name = "ADR-277", url = Adr.CODE_IS_RECOGNISED_BY_A_NAME_ENDING_OR_A_GENERATORS_MARK, type = "adr")
@Link(name = "ADR-268", url = Adr.THE_PROGRESS_PAGE_SHOWS_WHAT_IS_REMOVED_AND_DRAWS_TO_THE_DATA_VIZ_METHOD, type = "adr")
class ProgressPageShowsCodeTest {

    private static final String PAGE = "progress.html";

    private static final String SAMPLES_PAGE = "progress-samples.html";

    private static final String RUNS_PAGE = "progress-runs.html";

    /** Long enough that no scheduled write lands while the test runs: the writes it reads are the listener's. */
    private static final Duration NO_SCHEDULED_WRITE_MEANWHILE = Duration.ofMinutes(5);

    /** How many were survivors when byte-level reduction started. */
    private static final long ENTERED = 10;

    /** A run of byte-level reduction the working directory holds. */
    private static final String A_RUN = "a-run-held-by-this-test";

    /** The ten blocking kinds as the tab of runs and the page of samples head their columns, code after out-of-scope. */
    private static final List<String> BLOCKING = List.of(
            "broken", "duplicate-of", "superseded-by", "out-of-scope", "code",
            "extraction-failed", "degenerate-output", "redundant-with", "translation-of", "below-threshold");

    /** A tile: its key, label, value and, where it has one, detail (ADR-268 section 5). */
    private static final Pattern TILE = Pattern.compile("<div class=\"tile\" data-tile=\"([a-z-]+)\">"
            + "<span class=\"label\">([^<]*)</span><span class=\"value\">([^<]*)</span>"
            + "(?:<span class=\"detail\">([^<]*)</span>)?</div>");

    private static final Pattern A_KINDS_RATE =
            Pattern.compile("[0-9][0-9,]*\\.[0-9] / min over the last [0-9]+:[0-5][0-9]:[0-5][0-9]|rate not measured yet");

    private static final Pattern END_LABEL = Pattern.compile("<text class=\"end-label\"[^>]*>([^<]*)</text>");

    private static final Pattern A_ROW = Pattern.compile("<tr>(<td.*?)</tr>", Pattern.DOTALL);

    private static final Pattern A_CELL = Pattern.compile("<td[^>]*>([^<]*)</td>");

    @TempDir
    Path workingDirectory;

    @Test
    @Story("The page shows what the step removing occurrences has removed, by kind, and how many survive")
    @DisplayName("After byte-level reduction kept three files as code, its tiles end with a code tile labelled as kept, its chart draws code as the fifth series, and the tab of runs and the page of samples count code in a column after out-of-scope")
    void byteLevelReductionShowsCodeAsAKindOfItsOwn() throws IOException {
        AtomicReference<Map<String, Long>> recorded = new AtomicReference<>(Map.of());
        ProgressPage progressPage = new ProgressPage(
                workingDirectory, NO_SCHEDULED_WRITE_MEANWHILE, Clock.systemDefaultZone(), null, recorded::get,
                () -> new ProgressPage.RunsRead(
                        List.of(new Runs.Listed(A_RUN, StageModules.BYTE_LEVEL_REDUCTION.stage(), 1L)),
                        Map.of(),
                        Map.of(A_RUN, Map.of("out-of-scope", 2L, "code", 3L))));
        JobExecution execution = anExecution();
        LocalDateTime start = execution.getStartTime();
        aStep(execution, StepNames.CENSUS, BatchStatus.COMPLETED, start, start.plusSeconds(1));
        StepExecution stageOne = aStep(
                execution, StepNames.BYTE_LEVEL_REDUCTION, BatchStatus.COMPLETED, start.plusSeconds(1), start.plusSeconds(2));
        stageOne.getExecutionContext().putLong(ProgressPage.ENTERED, ENTERED);

        progressPage.beforeJob(execution);
        recorded.set(Map.of("broken", 1L, "out-of-scope", 2L, "code", 3L));
        execution.setStatus(BatchStatus.COMPLETED);
        progressPage.afterJob(execution);

        String page = read(PAGE);
        List<String[]> tiles = tiles(page);
        claim(
                "byte-level reduction's tiles are the survivors and its five kinds, code the last of them",
                () -> assertThat(tiles).extracting(tile -> tile[0]).containsExactly(
                        "steps", "step", "elapsed", "database", "survivors",
                        "broken", "duplicate-of", "superseded-by", "out-of-scope", "code"));
        claim(
                "the code tile counts the three, is labelled as kept, and carries its rate or a word that it has none",
                () -> {
                    String[] code = tile(tiles, "code");
                    assertThat(code[1]).isEqualTo("code (kept)");
                    assertThat(code[2]).isEqualTo("3");
                    assertThat(code[3]).matches(A_KINDS_RATE);
                });
        claim(
                "code is not a survivor: of the ten that entered, the one broken, the two out of scope and the three"
                        + " kept as code are taken from the survivors, which leaves four",
                () -> assertThat(tile(tiles, "survivors")[2]).isEqualTo("4"));
        String verdicts = between(page, "data-chart=\"verdicts\"");
        claim(
                "the chart of byte-level reduction's verdicts draws code as its fifth series, ending at three",
                () -> {
                    assertThat(verdicts).contains("class=\"line s5\"");
                    assertThat(String.join(", ", all(END_LABEL, verdicts))).contains("code 3");
                });
        claim(
                "the page of samples heads a column for code, after out-of-scope",
                () -> assertThat(read(SAMPLES_PAGE)).contains(ReportPage.headerRow(withSampleHeads())));
        String runs = read(RUNS_PAGE);
        List<String> headers = new ArrayList<>(List.of("Walk", "Stage", "Run", "Steps finished", "State", "This invocation"));
        headers.addAll(BLOCKING);
        claim(
                "the tab of runs heads a column for code, after out-of-scope",
                () -> assertThat(runs).contains(ReportPage.headerRow(headers.toArray(new String[0]))));
        List<List<String>> rows = rows(runs);
        claim(
                "and its row for the run counts the three kept as code in that column",
                () -> {
                    assertThat(rows).hasSize(1);
                    assertThat(rows.getFirst().get(6 + BLOCKING.indexOf("code"))).isEqualTo("3");
                    assertThat(rows.getFirst().get(6 + BLOCKING.indexOf("out-of-scope"))).isEqualTo("2");
                });
    }

    private static String[] withSampleHeads() {
        List<String> heads = new ArrayList<>(List.of(
                "Elapsed", "Database size", "Step", "Read", "Written", "Progress", "Outcome step", "Survivors"));
        heads.addAll(BLOCKING);
        return heads.toArray(new String[0]);
    }

    private static JobExecution anExecution() {
        JobExecution execution =
                new JobExecution(1L, new JobInstance(1L, VesperaJobConfiguration.JOB_NAME), new JobParameters());
        execution.setStartTime(LocalDateTime.now());
        execution.setStatus(BatchStatus.STARTED);
        return execution;
    }

    private static StepExecution aStep(
            JobExecution job, String step, BatchStatus status, LocalDateTime start, LocalDateTime end) {
        StepExecution execution = new StepExecution(step, job);
        execution.setStatus(status);
        execution.setStartTime(start);
        execution.setEndTime(end);
        job.addStepExecution(execution);
        return execution;
    }

    private String read(String name) throws IOException {
        Path page = workingDirectory.resolve(name);
        claim("the listener wrote " + name + " in the working directory", () -> assertThat(page).isRegularFile());
        return Files.readString(page, StandardCharsets.UTF_8);
    }

    private static List<String[]> tiles(String page) {
        List<String[]> tiles = new ArrayList<>();
        Matcher tile = TILE.matcher(page);
        while (tile.find()) {
            tiles.add(new String[] {tile.group(1), tile.group(2), tile.group(3), tile.group(4) == null ? "" : tile.group(4)});
        }
        return tiles;
    }

    private static String[] tile(List<String[]> tiles, String key) {
        return tiles.stream().filter(tile -> tile[0].equals(key)).findFirst().orElse(new String[] {key, "", "", ""});
    }

    private static List<String> all(Pattern pattern, String text) {
        List<String> found = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    private static List<List<String>> rows(String page) {
        List<List<String>> rows = new ArrayList<>();
        Matcher row = A_ROW.matcher(page);
        while (row.find()) {
            rows.add(all(A_CELL, row.group(1)));
        }
        return rows;
    }

    private static String between(String page, String opening) {
        int from = page.indexOf(opening);
        if (from < 0) {
            return "";
        }
        int to = page.indexOf("</svg>", from);
        return to < 0 ? page.substring(from) : page.substring(from, to);
    }
}
