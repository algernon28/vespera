package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.profile.ProfileFixture;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.SimpleJob;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The progress page {@code vespera run} rewrites in the working directory while it runs (ADR-265), reached
 * through whole invocations: what it shows while a step runs, what its last write says, what it never holds,
 * how a step stopped at a gate and a failed step are shown, and that a page that cannot be written fails
 * nothing. The table of steps is read off the page of its own beside it (ADR-267 section 3).
 *
 * <p>The working directory is static, so each test starts by removing the page an earlier one left, and puts
 * back a profile that loads. The interval is a tenth of a second, so that a step this fixture runs lasts long
 * enough to be written more than once; the step is held at its progress line while the test looks, from the
 * thread that logged it, and the page is written by a thread of its own (ADR-265 section 2).
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Pipeline")
@Feature("The progress page")
@Link(name = "ADR-265", url = Adr.THE_RUN_REWRITES_A_PROGRESS_PAGE_WHILE_IT_RUNS, type = "adr")
@Link(name = "ADR-198", url = Adr.EVERY_INVOCATION_WRITES_AN_ACCOUNT_THAT_NAMES_NO_DOCUMENT, type = "adr")
@Link(name = "ADR-267", url = Adr.THE_PROGRESS_PAGE_DRAWS_CHARTS_AND_ITS_TABLES_ARE_PAGES_OF_THEIR_OWN, type = "adr")
class ProgressPageInvocationTest {

    /** The page's name in the working directory. */
    private static final String PAGE = "progress.html";

    /** The page beside it that holds the table of steps (ADR-267 section 3). */
    private static final String STEPS_PAGE = "progress-steps.html";

    /** The page beside it that holds a table of the samples the charts are drawn from (ADR-267 section 3). */
    private static final String SAMPLES_PAGE = "progress-samples.html";

    /** How often the page is rewritten in this class: a tenth of a second, so a short step is seen twice. */
    private static final String INTERVAL = "100ms";

    /** The longest the test waits for the page to show what it is looking for, while the step is held. */
    private static final Duration WAIT = Duration.ofSeconds(10);

    /** How often the test reads the page while it waits. */
    private static final long LOOK_EVERY_MILLIS = 20L;

    /** What a page that reloads itself carries in its head, up to the number of seconds. */
    private static final String RELOAD = "<meta http-equiv=\"refresh\" content=\"";

    private static final String RUNNING = "This invocation is running.";

    private static final String ENDED_COMPLETED = "This invocation has ended: completed.";

    private static final String ENDED_FAILED = "This invocation has ended: failed.";

    /** The table's header row, the seven columns in their order. */
    private static final String HEADER =
            ReportPage.headerRow("Step", "State", "Read", "Written", "Skipped", "Duration", "Progress");

    /** A table row's first cell: the step it is about. A header row's cells are {@code <th>} and are not read. */
    private static final Pattern A_ROWS_STEP = Pattern.compile("<tr><td>([^<]*)</td>");

    private static final Pattern WRITTEN_AT =
            Pattern.compile("Written at ([0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2})");

    private static final Pattern ELAPSED = Pattern.compile("Elapsed: [0-9]+:[0-5][0-9]:[0-5][0-9]");

    private static final Pattern DATABASE_SIZE = Pattern.compile("Database size: [0-9]+\\.[0-9] MB");

    /** What every private name and text in the fixture carries, so one search finds any leak of any of them. */
    private static final String MARKER = "ZXQ-the-operators-private-word";

    private static final String MARKED_FOLDER = MARKER + "-folder";

    private static final List<String> MARKED_READABLE_FILES =
            List.of(MARKER + "-first.txt", MARKER + "-second.txt");

    /** The corpus file the scripted converter fails on while blaming itself. */
    private static final String FAULTED = SeedScriptedExtractionBeans.CONVERTER_FAULT;

    /** A floor of 1.0 opens stage 4's gate, as every invocation test reaching stage 5 sets it. */
    private static final String BOILERPLATE_FLOOR = "1.0";

    /** The model the scripted embedder answers for, so the scoring half of the job opens. */
    private static final String MODEL_NAME = "qwen3-embedding:0.6b";

    /** Stage 5a's progress line over this fixture's one seed: a counter's line, logged while the step runs. */
    private static final String SEEDS_RECORDED_LINE =
            "Stage 5a (seed extraction, seeds recorded): 1 of 1 (100%)";

    /** The one warning a page that cannot be written leaves, up to the exception's class name. */
    private static final String COULD_NOT_BE_WRITTEN = "the progress page could not be written: ";

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectoryAndInterval(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
        registry.add("vespera.progress-interval", () -> INTERVAL);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Job vesperaJob;

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger rootLogger;

    /** The page an earlier test left, a file or the folder one test puts in its place, is gone before each. */
    @BeforeEach
    void noPageAndAProfileThatLoads() throws IOException {
        removeThePage();
        profileStore.save(ProfileFixture.profile().build());
        logged = new ListAppender<>();
        logged.start();
        rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(logged);
    }

    @AfterEach
    void releaseTheLinesAndPutAProfileBack() throws IOException {
        rootLogger.detachAppender(logged);
        logged.stop();
        removeThePage();
        profileStore.save(ProfileFixture.profile().build());
    }

    @Test
    @Story("While a step runs, the page names it and how far it has got, and is written again")
    @DisplayName("While seed extraction runs, the page reloads itself, names the step as running with its progress line, states the time, the time elapsed and the database's size, and is written again later")
    void whileAStepRunsThePageNamesItAndIsRewritten(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aMarkedCorpus(root, seeds);
        profileNaming(seeds, BOILERPLATE_FLOOR);
        AtomicBoolean lineLogged = new AtomicBoolean();
        List<String> seen = new CopyOnWriteArrayList<>();
        HoldingAppender holding = new HoldingAppender(event -> {
            if (!SEEDS_RECORDED_LINE.equals(event.getFormattedMessage()) || !lineLogged.compareAndSet(false, true)) {
                return;
            }
            String steps = awaitThePage(STEPS_PAGE, page ->
                    page.contains(row(StepNames.SEED_EXTRACTION, "running")) && page.contains(SEEDS_RECORDED_LINE));
            if (steps == null) {
                return;
            }
            // the table of steps is moved into place before the progress page of the same write
            String first = awaitThePage(PAGE, page -> page.contains(RUNNING));
            if (first == null) {
                return;
            }
            seen.add(first);
            String later = awaitThePage(PAGE, page -> !writtenAt(page).equals(writtenAt(first)));
            if (later != null) {
                seen.add(later);
            }
        });
        holding.setContext(rootLogger.getLoggerContext());
        holding.start();
        rootLogger.addAppender(holding);
        try {
            cli.run("run", root.toString());
        } finally {
            rootLogger.detachAppender(holding);
            holding.stop();
        }

        claim(
                "stage 5a logged its progress line over the one seed, so there was a moment its step was running"
                        + " and held there while the page was looked at",
                () -> assertThat(lineLogged).isTrue());
        claim(
                "while the step was held, the table of steps beside the page was written naming seed extraction"
                        + " as running, with the progress line the step had just logged, and the page itself was there",
                () -> assertThat(seen).isNotEmpty());
        String first = seen.getFirst();
        claim(
                "that page reloads itself and says the invocation is running, and not that it has ended",
                () -> assertThat(first).contains(RELOAD).contains(RUNNING).doesNotContain("This invocation has ended"));
        claim(
                "it states when it was written, the time elapsed since the invocation started, and the size of"
                        + " the database in millions of bytes",
                () -> assertThat(first).containsPattern(WRITTEN_AT).containsPattern(ELAPSED).containsPattern(DATABASE_SIZE));
        claim(
                "and while the step was still held the page was written again, at a later time, without the"
                        + " step having to do anything: it is rewritten every interval",
                () -> assertThat(seen).hasSize(2));
    }

    @Test
    @Story("The last write says how the invocation ended and stops the page reloading")
    @DisplayName("After an invocation that completed, the page says it ended, no longer reloads, and lists every step in order with none of them running")
    void theLastWriteSaysTheInvocationEndedAndStopsTheReload(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aMarkedCorpus(root, seeds);
        profileNaming(seeds, BOILERPLATE_FLOOR);

        cli.run("run", root.toString());

        claim("the invocation reported success", () -> assertThat(cli.getExitCode()).isZero());
        String page = thePage(PAGE);
        claim(
                "the page says the invocation has ended, and how, and carries no reload, so a browser that has"
                        + " it open stops asking for it",
                () -> assertThat(page).contains(ENDED_COMPLETED).doesNotContain(RELOAD).doesNotContain(RUNNING));
        String stepsPage = thePage(STEPS_PAGE);
        List<String> steps = stepsInJobOrder();
        claim(
                "the table of steps beside it has the seven columns in their order, and a row for each of the"
                        + " job's " + steps.size() + " steps in the order the job runs them",
                () -> assertThat(stepsPage)
                        .contains(HEADER)
                        .containsSubsequence(steps.stream().map(ReportPage::textCell).toList()));
        claim(
                "and no step is shown running in a table written after the job ended",
                () -> assertThat(stepsPage).doesNotContain(ReportPage.textCell("running")));
    }

    @Test
    @Story("The page can be sent to anyone: it names no path and no document")
    @DisplayName("A word marked in every name and text the invocation is given occurs nowhere on the page, and neither do the folders it was pointed at")
    void thePageNamesNoPathAndNoTextOfADocument(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aMarkedCorpus(root, seeds);
        profileNaming(seeds, BOILERPLATE_FLOOR);

        cli.run("run", root.toString());

        for (String name : List.of(PAGE, STEPS_PAGE, SAMPLES_PAGE)) {
            String page = thePage(name);
            for (String forbidden : List.of(
                    MARKER,
                    MARKED_FOLDER,
                    FAULTED,
                    SeedScriptedExtractionBeans.CONVERTER_FAULT_MESSAGE,
                    root.toString(),
                    seeds.toString(),
                    workingDirectory.toString())) {
                claim(
                        name + " does not contain '" + forbidden + "', in any letter case, which the invocation was"
                                + " given or read",
                        () -> assertThat(page).doesNotContainIgnoringCase(forbidden));
            }
        }
    }

    @Test
    @Story("A step that ends at a gate is shown stopped there")
    @DisplayName("With the boilerplate floor unset, the redundancy signature step is shown stopped at a gate and the content census before it finished")
    void aStepThatEndsAtAShutGateIsShownStoppedThere(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aMarkedCorpus(root, seeds);
        profileNaming(seeds, null);

        cli.run("run", root.toString());

        claim(
                "the invocation reported success: a value it requires and does not have ends the run there,"
                        + " having recorded everything it learned",
                () -> assertThat(cli.getExitCode()).isZero());
        String page = thePage(STEPS_PAGE);
        claim(
                "the redundancy signature step, whose gate is the boilerplate floor, is shown stopped at a gate,"
                        + " and the content census, which needs no value, finished",
                () -> assertThat(page)
                        .contains(row(StepNames.REDUNDANCY_SIGNATURE, "stopped at a gate"))
                        .contains(row(StepNames.CONTENT_CENSUS, "finished")));
    }

    @Test
    @Story("A failed step is shown failed, and the steps after it not started")
    @DisplayName("A profile that does not load fails the census: the page shows the census failed, the step after it not started, and the invocation ended failed")
    void aFailedStepIsShownFailedAndTheStepsAfterItNotStarted(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aMarkedCorpus(root, seeds);
        Files.writeString(profileStore.file(), MARKER + "-key-written-flat: a value\n");

        cli.run("run", root.toString());

        claim(
                "the invocation failed, because a profile that does not load stops the census step",
                () -> assertThat(cli.getExitCode()).isNotZero());
        String stepsPage = thePage(STEPS_PAGE);
        claim(
                "the census is shown failed and byte-level reduction, which the job never reached, not started",
                () -> assertThat(stepsPage)
                        .contains(row(StepNames.CENSUS, "failed"))
                        .contains(row(StepNames.BYTE_LEVEL_REDUCTION, "not started")));
        String page = thePage(PAGE);
        claim(
                "and the page says the invocation ended failed, carrying no reload",
                () -> assertThat(page).contains(ENDED_FAILED).doesNotContain(RELOAD));
    }

    @Test
    @Story("A page that cannot be written fails nothing")
    @DisplayName("With a folder where the page goes, the run completes, and one warning says the page could not be written")
    void aPageThatCannotBeWrittenFailsNothingAndIsWarnedOfOnce(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aMarkedCorpus(root, seeds);
        profileNaming(seeds, BOILERPLATE_FLOOR);
        Files.createDirectories(workingDirectory.resolve(PAGE).resolve("a-file-that-keeps-the-folder-there"));

        cli.run("run", root.toString());

        claim(
                "the invocation reported success, although no write of the page could take its place",
                () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "exactly one warning says the page could not be written, naming what failed, although every"
                        + " write of the invocation failed: the next is warned of only after one succeeds",
                () -> assertThat(logged.list)
                        .filteredOn(event -> event.getLevel() == Level.WARN
                                && event.getFormattedMessage().startsWith(COULD_NOT_BE_WRITTEN))
                        .hasSize(1));
        claim(
                "and no line at error says anything of the page",
                () -> assertThat(logged.list)
                        .filteredOn(event -> event.getLevel().isGreaterOrEqual(Level.ERROR))
                        .noneMatch(event -> event.getFormattedMessage().contains("progress page")));
    }

    @Test
    @Story("The page is no output of a stage: writing it mints no run")
    @DisplayName("A second invocation over the same root writes the page again and leaves the runs as the first left them")
    void aSecondInvocationRewritesThePageAndMintsNoRun(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aMarkedCorpus(root, seeds);
        profileNaming(seeds, BOILERPLATE_FLOOR);
        cli.run("run", root.toString());
        List<String> runsAfterTheFirst = runIds();
        removeThePage();

        cli.run("run", root.toString());

        claim(
                "the second invocation reported success, and the run table holds the same ids as after the"
                        + " first: everything was recorded, every run was continued, and none was minted",
                () -> {
                    assertThat(cli.getExitCode()).isZero();
                    assertThat(runIds()).isEqualTo(runsAfterTheFirst);
                });
        claim(
                "and the second invocation wrote the page again, ending on a write that says it completed",
                () -> assertThat(workingDirectory.resolve(PAGE))
                        .isRegularFile()
                        .content(StandardCharsets.UTF_8)
                        .contains(ENDED_COMPLETED));
    }

    @Test
    @Story("The page lists the steps the job runs, in the order it runs them")
    @DisplayName("The page has one row for each step of the job, in the job's own order, and no other row")
    void thePageListsExactlyTheJobsStepsInTheJobsOrder(@TempDir Path elsewhere) throws IOException {
        List<String> steps = stepsInJobOrder();
        ProgressPage progressPage = new ProgressPage(elsewhere, Duration.ofSeconds(5), java.time.Clock.systemUTC());
        JobExecution execution = new JobExecution(1L, new JobInstance(1L, VesperaJobConfiguration.JOB_NAME), new JobParameters());

        progressPage.beforeJob(execution);
        execution.setStatus(BatchStatus.COMPLETED);
        progressPage.afterJob(execution);

        List<String> rows = new java.util.ArrayList<>();
        Path stepsPage = elsewhere.resolve(STEPS_PAGE);
        claim("the table of steps was written beside the page", () -> assertThat(stepsPage).isRegularFile());
        Matcher row = A_ROWS_STEP.matcher(Files.readString(stepsPage, StandardCharsets.UTF_8));
        while (row.find()) {
            rows.add(row.group(1));
        }
        claim(
                "the page's rows name the job's " + steps.size() + " steps, read off the job itself, in the order the"
                        + " job runs them, and no other: a step added to the job and not to the page has no row",
                () -> assertThat(rows).containsExactlyElementsOf(steps));
    }

    /** The job's steps in the order it runs them, as the job itself lists them: a simple job of steps in sequence. */
    private List<String> stepsInJobOrder() {
        claim(
                "the job is a sequence of steps, so the order its steps are listed in is the order it runs them",
                () -> assertThat(vesperaJob).isInstanceOf(SimpleJob.class));
        return List.copyOf(((SimpleJob) vesperaJob).getStepNames());
    }

    /** A row's first two cells: the step as the job names it, and its state. */
    private static String row(String step, String state) {
        return ReportPage.textCell(step) + ReportPage.textCell(state);
    }

    /** The page {@code name} as the last write left it, failing with a plain message when there is none. */
    private String thePage(String name) throws IOException {
        Path page = workingDirectory.resolve(name);
        claim(
                "the invocation left " + name + " in the working directory, so there is a page to read",
                () -> assertThat(page).isRegularFile());
        return Files.readString(page, StandardCharsets.UTF_8);
    }

    /** The page {@code name} once it satisfies {@code wanted}, read every few milliseconds, or null after {@link #WAIT}. */
    private static String awaitThePage(String name, Predicate<String> wanted) {
        Path page = workingDirectory.resolve(name);
        long until = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < until) {
            try {
                if (Files.isRegularFile(page)) {
                    String text = Files.readString(page, StandardCharsets.UTF_8);
                    if (wanted.test(text)) {
                        return text;
                    }
                }
            } catch (IOException beingReplaced) {
                // the page is moved over in one move; a read that meets the move reads again
            }
            try {
                Thread.sleep(LOOK_EVERY_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    /** The time a page says it was written, or the empty text where it says none. */
    private static String writtenAt(String page) {
        Matcher matcher = WRITTEN_AT.matcher(page);
        return matcher.find() ? matcher.group(1) : "";
    }

    private List<String> runIds() {
        return jdbcTemplate.queryForList("SELECT id FROM run ORDER BY id", String.class);
    }

    /** The page, or the folder a test put in its place, removed with whatever it holds. */
    private static void removeThePage() throws IOException {
        Path page = workingDirectory.resolve(PAGE);
        if (!Files.exists(page)) {
            return;
        }
        try (Stream<Path> inside = Files.walk(page)) {
            for (Path path : inside.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    /** Marks every directory, file name and text the invocation is given, and one file the converter faults on. */
    private static void aMarkedCorpus(Path root, Path seeds) throws IOException {
        Path folder = Files.createDirectory(root.resolve(MARKED_FOLDER));
        for (String name : MARKED_READABLE_FILES) {
            Files.writeString(folder.resolve(name), "the text of " + name + " says " + MARKER);
        }
        Files.writeString(folder.resolve(FAULTED), "a file the converter faults on, and " + MARKER);
        Files.writeString(seeds.resolve(MARKER + "-seed.txt"), "a seed that says " + MARKER);
    }

    /** The seed folder named, gate 3 open, and stage 4's gate open where {@code boilerplateFloor} is given. */
    private void profileNaming(Path seeds, String boilerplateFloor) {
        ProfileFixture profile = ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profileStore.load().degenerateOutputConfidenceFloor())
                .embeddingModel(MODEL_NAME, "set by this test, so gate 3 is open");
        if (boilerplateFloor != null) {
            profile.boilerplateDocumentFrequencyFloor(boilerplateFloor, "set by this test, so stage 4's gate is open");
        }
        profileStore.save(profile.build());
    }

    /**
     * Hands each event to {@code onEvent} on the thread that logged it, so a step's thread can be held at one
     * of its lines while the page is looked at. Unsynchronised, so a line another thread logs meanwhile is not
     * held behind it.
     */
    private static final class HoldingAppender extends ch.qos.logback.core.UnsynchronizedAppenderBase<ILoggingEvent> {

        private final java.util.function.Consumer<ILoggingEvent> onEvent;

        HoldingAppender(java.util.function.Consumer<ILoggingEvent> onEvent) {
            this.onEvent = onEvent;
        }

        @Override
        protected void append(ILoggingEvent event) {
            onEvent.accept(event);
        }
    }
}
