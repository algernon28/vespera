package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.JOB_START;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.NO_SCHEDULED_WRITE_MEANWHILE;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.WRITTEN_AT;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.ZONE;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.aJob;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.aReport;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.aStep;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.at;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.WalkRecorder;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;
import tools.jackson.databind.json.JsonMapper;

/**
 * Issue 937: the reading that writes the census's file empty, as its step ends with no walk handed, held the file
 * emptied before it was written, so that a write that failed left the chart of the walk walked in the file for the rest
 * of the invocation. The census's file is held emptied only once the empty file is written, and the next reading writes
 * it again (ADR-449 §1, amending ADR-418 §4; ADR-265 §4).
 *
 * <p>The write is made to fail as a write of the file can: the temporary file the census's chart is written to before it
 * is moved over its place cannot be written, a folder standing where it goes, until the test takes the folder away.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("937")
@Link(name = "ADR-449", url = Adr.THE_CENSUSS_CHART_IS_HELD_EMPTIED_ONLY_ONCE_ITS_EMPTY_FILE_IS_WRITTEN, type = "adr")
@Link(name = "ADR-418", url = Adr.EVERY_STAGES_CHART_IS_WRITTEN_EMPTY_AS_THE_JOB_BEGINS, type = "adr")
@Link(name = "ADR-265", url = Adr.THE_RUN_REWRITES_A_PROGRESS_PAGE_WHILE_IT_RUNS, type = "adr")
class ACensusEmptyingThatFailsIsMadeAgainAtTheNextReadingTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** The entries the census's line says of the seed folder's walk, the only walk it names. */
    private static final long SEED_FOLDER_WALKED = 2_000;

    /** The seed folder's walk. */
    private static final long THE_SEED_FOLDERS_WALK = 83;

    /** When the census ends, in seconds after the job's start. */
    private static final long CENSUS_ENDS = 25;

    /** How far the test moves the sampler's clock on between two of its readings, in seconds. */
    private static final long BETWEEN_READINGS = 10;

    @TempDir
    Path workingDirectory;

    @Test
    @Story("The window shows how each step has gone")
    @DisplayName("A census that ends with no walk handed, whose file could not be written empty at one reading, has it written empty at the next")
    void aCensusFileThatCouldNotBeWrittenEmptyIsWrittenEmptyAtTheNextReading() throws IOException {
        MovingClock clock = new MovingClock();
        JobSampler sampler = aSampler(clock);
        JobExecution job = aJob();
        sampler.beforeJob(job);

        // the corpus root's walk fails before its first line; the census walks the seed folder, a reading falls while
        // it does, and it fails with no walk handed
        StepExecution census = aStep(job, StepNames.CENSUS, BatchStatus.STARTED, 0, null);
        censusSays(SEED_FOLDER_WALKED, THE_SEED_FOLDERS_WALK);
        readsOnce(sampler);
        String whileItWalked = Files.readString(fileOfTheCensus(), UTF_8);

        // the reading 10 s on cannot write its file
        census.setStatus(BatchStatus.FAILED);
        census.setEndTime(at(CENSUS_ENDS));
        Path blocking = Files.createDirectories(partOfTheCensus());
        clock.moveOn(BETWEEN_READINGS);
        readsOnce(sampler);
        String afterTheFailedWrite = Files.readString(fileOfTheCensus(), UTF_8);

        // the file can be written again, and the next reading falls 10 s later
        Files.delete(blocking);
        clock.moveOn(BETWEEN_READINGS);
        readsOnce(sampler);

        claim("while the census walked, its file held the seed folder's walk and one line, done, of one sample at second"
                        + " 0: the seed folder's 2,000 entries",
                () -> {
                    Map<String, Object> walking = read(whileItWalked);
                    assertThat(walking.get("key")).as("its key").isEqualTo(Map.of("walk",
                            String.valueOf(THE_SEED_FOLDERS_WALK)));
                    assertThat(linesOf(walking)).as("its lines, each its name, its seconds and its values")
                            .containsExactly(new Line("done", List.of(0.0), List.of((double) SEED_FOLDER_WALKED)));
                });
        claim("the reading whose write failed leaves the census's file as the reading while it walked wrote it",
                () -> assertThat(afterTheFailedWrite).isEqualTo(whileItWalked));
        claim("the next reading writes it empty: format 1, its step, no key, no finishing time, not finished in an"
                        + " earlier invocation and no line",
                () -> assertThat(read(Files.readString(fileOfTheCensus(), UTF_8))).isEqualTo(empty()));
    }

    // ---- the job

    private JobSampler aSampler(Clock clock) {
        JobSampler sampler = new JobSampler(workingDirectory, NO_SCHEDULED_WRITE_MEANWHILE, clock, Map::of,
                () -> new JobSampler.RunsRead(List.of(), Map.of(), Map.of()));
        InvocationReport report = aReport(workingDirectory);
        sampler.handTo(report);
        report.begins(InvocationReport.RUN);
        return sampler;
    }

    /** The census's own line, as it says it of {@code walk} at {@code entries} walked, on the logger it says it on. */
    private static void censusSays(long entries, long walk) {
        LoggerFactory.getLogger(WalkRecorder.class).info(
                "Stage 0 (census): {} entries walked so far, {} directories entered, under walk {}",
                String.format(Locale.ROOT, "%,d", entries),
                String.format(Locale.ROOT, "%,d", entries / 10),
                walk);
    }

    /** One reading as the sampler's own thread makes it at each interval, made on the test's thread. */
    private static void readsOnce(JobSampler sampler) {
        try {
            Method reading = JobSampler.class.getDeclaredMethod("scheduledWrite");
            reading.setAccessible(true);
            reading.invoke(sampler);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("the sampler's scheduled reading could not be made", e);
        }
    }

    /** The sampler's clock, at the instant the report's clock stands at until the test moves it on. */
    private static final class MovingClock extends Clock {

        private volatile Instant now = JOB_START.plusSeconds(WRITTEN_AT);

        void moveOn(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    // ---- the files

    private Path fileOfTheCensus() {
        return workingDirectory.resolve("chart-step-" + StepNames.CENSUS + ".json");
    }

    /** The temporary file the census's chart is written to before it is moved over its place. */
    private Path partOfTheCensus() {
        return workingDirectory.resolve("chart-step-" + StepNames.CENSUS + ".json.part");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> read(String text) {
        return JSON.readValue(text, Map.class);
    }

    /** One line of a chart: its name, and its points' seconds and values in order. */
    private record Line(String name, List<Double> seconds, List<Double> values) {
    }

    /** The lines a chart file read holds, in order. */
    @SuppressWarnings("unchecked")
    private static List<Line> linesOf(Map<String, Object> chart) {
        List<Line> lines = new ArrayList<>();
        for (Object each : (List<Object>) chart.getOrDefault("lines", List.of())) {
            Map<String, Object> line = (Map<String, Object>) each;
            lines.add(new Line((String) line.get("name"), numbers(line.get("seconds")), numbers(line.get("values"))));
        }
        return lines;
    }

    @SuppressWarnings("unchecked")
    private static List<Double> numbers(Object read) {
        return read == null ? List.of() : ((List<Number>) read).stream().map(Number::doubleValue).toList();
    }

    /** What the census's file holds once written empty. */
    private static Map<String, Object> empty() {
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("format", 1);
        empty.put("step", StepNames.CENSUS);
        empty.put("key", Map.of());
        empty.put("finishedAt", null);
        empty.put("finishedEarlier", false);
        empty.put("lines", List.of());
        return empty;
    }
}
