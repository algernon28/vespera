package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The progress page's tab of runs (ADR-269 section 5), reached through two whole invocations over one root, the
 * second under another boilerplate floor so that it mints a second run of content redundancy beside the first's:
 * what the tab lists after them, which runs it marks as the second invocation's, what it shows while that
 * invocation runs, and what it never holds.
 *
 * <p>The two invocations are made once, by whichever test needs them first, and every test reads what they left
 * (the database of the test profile is shared by the classes that share its context, so what the tab should list
 * is read off the ledger itself). The second is held at the start of content redundancy's work, its run already
 * minted, while the tab is read off the working directory; the page is rewritten every tenth of a second by a
 * thread of its own.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Pipeline")
@Feature("The progress page")
@Link(name = "ADR-269", url = Adr.THE_PROGRESS_PAGE_IS_FOUR_TABS_AND_LISTS_THE_RUNS_THE_WORKING_DIRECTORY_HOLDS, type = "adr")
@Link(name = "ADR-265", url = Adr.THE_RUN_REWRITES_A_PROGRESS_PAGE_WHILE_IT_RUNS, type = "adr")
class ProgressPageRunsInvocationTest {

    private static final String RUNS_PAGE = "progress-runs.html";

    private static final String INTERVAL = "100ms";

    /** The longest the held step waits for the tab to show what the test is looking for. */
    private static final Duration WAIT = Duration.ofSeconds(10);

    private static final long LOOK_EVERY_MILLIS = 20L;

    private static final String RUNNING = "This invocation is running.";

    private static final String READ_AS_IT_STARTED = "The runs this working directory held when this invocation started.";

    private static final String READ_AS_IT_ENDED = "The runs this working directory held when this invocation ended.";

    private static final String ARRIVED_AT = "arrived at";

    /** The first invocation's floor opens stage 4's gate; the second's is another value, so stage 4 mints a run anew. */
    private static final String FIRST_FLOOR = "1.0";

    private static final String SECOND_FLOOR = "0.9";

    private static final String MODEL_NAME = "qwen3-embedding:0.6b";

    /** What content redundancy logs as its work starts under its run, after the run is minted. */
    private static final String CONTENT_REDUNDANCY_STARTS = "Stage 4b (redundancy resolution) starting under run ";

    /** The column of the tab that says whether this invocation arrived at the run, counted from nought. */
    private static final int THIS_INVOCATION = 5;

    /** The eight blocking kinds, as the tab heads its columns, in the vocabulary's order. */
    private static final List<String> BLOCKING = List.of(
            "broken", "duplicate-of", "superseded-by", "out-of-scope",
            "extraction-failed", "degenerate-output", "redundant-with", "below-threshold");

    /** The steps that record their completion under a run of each stage, as ADR-269 section 5 tables them. */
    private static final Map<String, List<String>> STEPS_OF_A_STAGE = new LinkedHashMap<>();

    static {
        STEPS_OF_A_STAGE.put(StageModules.BYTE_LEVEL_REDUCTION.stage(), List.of(StepNames.BYTE_LEVEL_REDUCTION));
        STEPS_OF_A_STAGE.put(StageModules.EXTRACTION.stage(), List.of(StepNames.EXTRACTION));
        STEPS_OF_A_STAGE.put(StageModules.CONTENT_CENSUS.stage(), List.of(StepNames.CONTENT_CENSUS));
        STEPS_OF_A_STAGE.put(
                StageModules.CONTENT_REDUNDANCY.stage(), List.of(StepNames.REDUNDANCY_SIGNATURE, StepNames.CONTENT_REDUNDANCY));
        STEPS_OF_A_STAGE.put(
                StageModules.SEED_MEASUREMENT.stage(), List.of(StepNames.SEED_EXTRACTION, StepNames.SEED_CORPUS_COMPARISON));
        STEPS_OF_A_STAGE.put(
                StageModules.EMBEDDING_SCORING.stage(),
                List.of(StepNames.EMBEDDING_SCORING, StepNames.RELEVANCE_SCORING, StepNames.CLUSTERING));
        STEPS_OF_A_STAGE.put(StageModules.ARRANGEMENT.stage(), List.of(StepNames.ARRANGEMENT));
        STEPS_OF_A_STAGE.put(StageModules.GENERATION.stage(), List.of(StepNames.GENERATION));
    }

    private static final String MARKER = "ZXQ-the-operators-private-word";

    private static final Pattern ROW = Pattern.compile("<tr>(.*?)</tr>", Pattern.DOTALL);

    private static final Pattern CELL = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL);

    @TempDir
    static Path workingDirectory;

    @TempDir
    static Path root;

    @TempDir
    static Path seeds;

    /** What the two invocations left, made by the first test that asks for it. */
    private static TwoInvocations twoInvocations;

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

    @Test
    @Story("The tab of runs lists every run the working directory holds")
    @DisplayName("After two invocations over one root, the second under another boilerplate floor, the tab of runs lists every run of the ledger in order, each with its walk, stage, ordinal, steps finished, state and verdicts by kind")
    void afterTwoInvocationsTheRunsTabListsEveryRunTheWorkingDirectoryHolds() throws IOException {
        TwoInvocations made = theTwoInvocations();

        claim(
                "the second invocation, under another floor, minted a run of content redundancy of its own, so the"
                        + " working directory holds two runs of one stage",
                () -> assertThat(made.mintedBySecond()).extracting(Run::stage)
                        .contains(StageModules.CONTENT_REDUNDANCY.stage()));
        claim(
                "the ledger holds a verdict under some run, so the tab's counts are held to a number other than nought",
                () -> assertThat(made.kinds().values()).anySatisfy(counts -> assertThat(Arrays.stream(counts).sum()).isPositive()));
        claim(
                "the tab says it shows the runs as they were when the second invocation ended",
                () -> assertThat(made.lastPage()).contains(READ_AS_IT_ENDED));
        String header = ReportPage.headerRow(headers());
        claim(
                "its table's header row is the walk, the stage, the run's ordinal, the steps finished, the state, whether"
                        + " this invocation arrived at it, and the eight kinds",
                () -> assertThat(made.lastPage()).contains(header));
        List<List<String>> expected = new ArrayList<>();
        for (Run run : made.afterSecond()) {
            List<String> cells = new ArrayList<>();
            cells.add(count(run.walk()));
            cells.add(run.stage());
            cells.add(count(run.ordinal()));
            List<String> steps = STEPS_OF_A_STAGE.get(run.stage());
            if (steps == null) {
                cells.add("");
                cells.add("");
            } else {
                Set<String> finished = made.finished().getOrDefault(run.id(), Set.of());
                long done = steps.stream().filter(finished::contains).count();
                cells.add(done + " of " + steps.size());
                cells.add(done == steps.size() ? "finished" : "not finished");
            }
            for (long kind : made.kinds().getOrDefault(run.id(), new long[BLOCKING.size()])) {
                cells.add(count(kind));
            }
            expected.add(cells);
        }
        List<List<String>> shown = rows(made.lastPage()).stream().map(row -> {
            List<String> cells = new ArrayList<>(row);
            if (cells.size() > THIS_INVOCATION) {
                cells.remove(THIS_INVOCATION);
            }
            return cells;
        }).toList();
        claim(
                "and its rows are the " + expected.size() + " runs of the ledger, by walk, then by stage in the cascade's"
                        + " order, then by ordinal, each with its steps finished of those its stage records, its state"
                        + " and its verdicts of each kind as the ledger holds them",
                () -> assertThat(shown).containsExactlyElementsOf(expected));
    }

    @Test
    @Story("The tab of runs marks the runs this invocation arrived at")
    @DisplayName("After the second invocation, the runs it minted and the stage-1 run it continued are marked as arrived at, the first invocation's run of content redundancy is not, and no stage or other walk has a run marked twice")
    void theRunsTabMarksTheRunsThisInvocationArrivedAt() throws IOException {
        TwoInvocations made = theTwoInvocations();
        Map<String, String> markOf = new HashMap<>();
        for (List<String> row : rows(made.lastPage())) {
            markOf.put(String.join("|", row.subList(0, Math.min(3, row.size()))),
                    row.size() > THIS_INVOCATION ? row.get(THIS_INVOCATION) : null);
        }
        Run firstStageOne = made.mintedByFirst().stream()
                .filter(run -> run.stage().equals(StageModules.BYTE_LEVEL_REDUCTION.stage())).findFirst().orElseThrow();
        Run firstRedundancy = made.mintedByFirst().stream()
                .filter(run -> run.stage().equals(StageModules.CONTENT_REDUNDANCY.stage())).findFirst().orElseThrow();

        claim(
                "every run the second invocation minted is marked as arrived at",
                () -> assertThat(made.mintedBySecond()).allSatisfy(run -> assertThat(markOf.get(run.key())).isEqualTo(ARRIVED_AT)));
        claim(
                "the stage-1 run the first invocation minted, which the second continued, is marked as arrived at",
                () -> assertThat(markOf.get(firstStageOne.key())).isEqualTo(ARRIVED_AT));
        claim(
                "the first invocation's run of content redundancy, which the second's floor left behind, is not",
                () -> assertThat(markOf.get(firstRedundancy.key())).isEmpty());
        List<Run> marked = made.afterSecond().stream().filter(run -> ARRIVED_AT.equals(markOf.get(run.key()))).toList();
        claim(
                "no stage has two runs marked, and every run marked is over the walk the second invocation made its"
                        + " stage-1 run over",
                () -> {
                    assertThat(marked.stream().map(Run::stage).toList()).doesNotHaveDuplicates();
                    assertThat(marked).allSatisfy(run -> assertThat(run.walk()).isEqualTo(firstStageOne.walk()));
                });
        claim(
                "and every other row's mark is empty",
                () -> assertThat(markOf.values()).allSatisfy(mark -> assertThat(mark).isIn(ARRIVED_AT, "")));
    }

    @Test
    @Story("The tab of runs is read as the invocation starts and as it ends, and at no other write")
    @DisplayName("While the second invocation is held at the start of content redundancy's work, its run already minted, the tab lists the runs the first left and marks the stage-1 run as arrived at; its last write lists the new run too")
    void whileTheInvocationRunsTheRunsTabIsTheReadingMadeAsItStarted() throws IOException {
        TwoInvocations made = theTwoInvocations();
        Run firstStageOne = made.mintedByFirst().stream()
                .filter(run -> run.stage().equals(StageModules.BYTE_LEVEL_REDUCTION.stage())).findFirst().orElseThrow();

        claim(
                "while content redundancy's work was held, a write of the tab said the invocation was running, showed"
                        + " the runs as they were when it started, and marked the stage-1 run it had continued",
                () -> assertThat(made.whileSecondRuns()).isNotNull().contains(RUNNING).contains(READ_AS_IT_STARTED));
        List<String> heldKeys = rows(made.whileSecondRuns()).stream()
                .map(row -> String.join("|", row.subList(0, Math.min(3, row.size())))).toList();
        claim(
                "that write lists exactly the runs the first invocation left, in order, and not the run of content"
                        + " redundancy the second had just minted: the tab is not read again at every write",
                () -> assertThat(heldKeys).containsExactlyElementsOf(made.afterFirst().stream().map(Run::key).toList()));
        claim(
                "it marks the stage-1 run as arrived at all the same: whether this invocation reached a run is known"
                        + " without reading the database",
                () -> assertThat(rows(made.whileSecondRuns())).anySatisfy(row -> {
                    assertThat(String.join("|", row.subList(0, 3))).isEqualTo(firstStageOne.key());
                    assertThat(row.get(THIS_INVOCATION)).isEqualTo(ARRIVED_AT);
                }));
        List<String> lastKeys = rows(made.lastPage()).stream()
                .map(row -> String.join("|", row.subList(0, Math.min(3, row.size())))).toList();
        claim(
                "and the last write, read as the invocation ended, lists every run, the ones the second minted among them",
                () -> assertThat(lastKeys)
                        .containsExactlyElementsOf(made.afterSecond().stream().map(Run::key).toList())
                        .containsAll(made.mintedBySecond().stream().map(Run::key).toList()));
    }

    @Test
    @Story("The tab of runs can be sent to anyone: it names no run id, no path and no document")
    @DisplayName("No run id of the ledger, no marked name or text, root, seed folder or working directory is on the tab of runs, while the invocation runs or after it ends")
    void theRunsTabNamesNoRunIdNoPathAndNoTextOfADocument() throws IOException {
        TwoInvocations made = theTwoInvocations();
        Map<String, String> pages = new LinkedHashMap<>();
        pages.put("the tab written while the second invocation ran", made.whileSecondRuns() == null ? "" : made.whileSecondRuns());
        pages.put("the tab of the last write", made.lastPage());
        List<String> forbidden = new ArrayList<>(List.of(
                MARKER,
                SeedScriptedExtractionBeans.CONVERTER_FAULT,
                SeedScriptedExtractionBeans.CONVERTER_FAULT_MESSAGE,
                root.toString(),
                seeds.toString(),
                workingDirectory.toString()));
        for (Run run : made.afterSecond()) {
            forbidden.add(run.id());
        }
        claim(
                "both tabs list runs, so there is something on them that could have named one",
                () -> assertThat(pages.values()).allSatisfy(page -> assertThat(rows(page)).isNotEmpty()));
        pages.forEach((which, page) -> claim(
                which + " holds none of the " + forbidden.size() + " run ids of the ledger, names and paths the"
                        + " invocations were given, in any letter case",
                () -> assertThat(forbidden).allSatisfy(word -> assertThat(page).doesNotContainIgnoringCase(word))));
    }

    /** The two invocations, made once for the class. */
    private TwoInvocations theTwoInvocations() throws IOException {
        synchronized (ProgressPageRunsInvocationTest.class) {
            if (twoInvocations == null) {
                twoInvocations = makeTheTwoInvocations();
            }
            return twoInvocations;
        }
    }

    private TwoInvocations makeTheTwoInvocations() throws IOException {
        aMarkedCorpus();
        profileStore.save(ProfileFixture.profile().build());
        List<Run> before = runsInTheLedger();

        profileNaming(FIRST_FLOOR);
        cli.run("run", root.toString());
        claim("the first invocation reported success", () -> assertThat(cli.getExitCode()).isZero());
        List<Run> afterFirst = runsInTheLedger();
        List<Run> mintedByFirst = minted(before, afterFirst);
        claim(
                "the first invocation minted a stage-1 run and a run of content redundancy, over a walk of its own root",
                () -> assertThat(mintedByFirst).extracting(Run::stage).contains(
                        StageModules.BYTE_LEVEL_REDUCTION.stage(), StageModules.CONTENT_REDUNDANCY.stage()));

        profileNaming(SECOND_FLOOR);
        AtomicBoolean held = new AtomicBoolean();
        AtomicReference<String> whileRunning = new AtomicReference<>();
        String stageOne = mintedByFirst.stream()
                .filter(run -> run.stage().equals(StageModules.BYTE_LEVEL_REDUCTION.stage())).findFirst()
                .map(Run::key).orElse("");
        ch.qos.logback.classic.Logger rootLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        HoldingAppender holding = new HoldingAppender(event -> {
            String message = event.getFormattedMessage();
            if (message != null && message.startsWith(CONTENT_REDUNDANCY_STARTS) && held.compareAndSet(false, true)) {
                // the step's thread waits here while the tab is rewritten by the page's own thread
                whileRunning.set(awaitTheRunsPage(page -> page.contains(RUNNING) && rows(page).stream().anyMatch(row ->
                        row.size() > THIS_INVOCATION
                                && String.join("|", row.subList(0, 3)).equals(stageOne)
                                && row.get(THIS_INVOCATION).equals(ARRIVED_AT))));
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
        claim("the second invocation reported success", () -> assertThat(cli.getExitCode()).isZero());
        claim(
                "the second invocation reached content redundancy's work and was held there while the tab was read",
                () -> assertThat(held).isTrue());
        List<Run> afterSecond = runsInTheLedger();

        Map<String, long[]> kinds = new HashMap<>();
        jdbcTemplate.query("SELECT run_id, kind, COUNT(*) AS n FROM verdict GROUP BY run_id, kind", resultSet -> {
            int at = BLOCKING.indexOf(resultSet.getString("kind").toLowerCase(Locale.ROOT).replace('_', '-'));
            if (at >= 0) {
                kinds.computeIfAbsent(resultSet.getString("run_id"), id -> new long[BLOCKING.size()])[at] =
                        resultSet.getLong("n");
            }
        });
        Map<String, Set<String>> finished = new HashMap<>();
        jdbcTemplate.query("SELECT run_id, step FROM finished_step", resultSet -> {
            finished.computeIfAbsent(resultSet.getString("run_id"), id -> new HashSet<>()).add(resultSet.getString("step"));
        });
        Path runsPage = workingDirectory.resolve(RUNS_PAGE);
        String lastPage = Files.isRegularFile(runsPage) ? Files.readString(runsPage, StandardCharsets.UTF_8) : "";
        return new TwoInvocations(
                afterFirst, afterSecond, mintedByFirst, minted(afterFirst, afterSecond), kinds, finished,
                whileRunning.get(), lastPage);
    }

    /**
     * Every run of the ledger, in the order the tab lists them: by walk, by stage in the cascade's order (a stage it
     * does not name after them, by name), and by its ordinal among its stage's runs over its walk, in minting order.
     */
    private List<Run> runsInTheLedger() {
        List<String[]> rows = new ArrayList<>();
        jdbcTemplate.query("SELECT id, stage, walk_id FROM run ORDER BY rowid", resultSet -> {
            rows.add(new String[] {resultSet.getString("id"), resultSet.getString("stage"), resultSet.getString("walk_id")});
        });
        Map<String, Integer> ordinals = new HashMap<>();
        List<Run> runs = new ArrayList<>();
        for (String[] row : rows) {
            long walk = Long.parseLong(row[2]);
            int ordinal = ordinals.merge(walk + "|" + row[1], 1, Integer::sum);
            runs.add(new Run(row[0], row[1], walk, ordinal));
        }
        List<String> cascade = Arrays.stream(StageModules.values()).map(StageModules::stage).toList();
        runs.sort(Comparator.comparingLong(Run::walk)
                .thenComparingInt(run -> cascade.contains(run.stage()) ? cascade.indexOf(run.stage()) : cascade.size())
                .thenComparing(Run::stage)
                .thenComparingInt(Run::ordinal));
        return runs;
    }

    private static List<Run> minted(List<Run> before, List<Run> after) {
        Set<String> known = new HashSet<>();
        before.forEach(run -> known.add(run.id()));
        return after.stream().filter(run -> !known.contains(run.id())).toList();
    }

    private static String[] headers() {
        List<String> headers = new ArrayList<>(List.of("Walk", "Stage", "Run", "Steps finished", "State", "This invocation"));
        headers.addAll(BLOCKING);
        return headers.toArray(new String[0]);
    }

    /** Each body row of the page's tables, as the text of its cells; a header row's cells are {@code <th>}. */
    private static List<List<String>> rows(String page) {
        List<List<String>> rows = new ArrayList<>();
        if (page == null) {
            return rows;
        }
        Matcher row = ROW.matcher(page);
        while (row.find()) {
            List<String> cells = new ArrayList<>();
            Matcher cell = CELL.matcher(row.group(1));
            while (cell.find()) {
                cells.add(cell.group(1));
            }
            if (!cells.isEmpty()) {
                rows.add(cells);
            }
        }
        return rows;
    }

    private static String count(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    /** The tab of runs once it satisfies {@code wanted}, read every few milliseconds, or null after {@link #WAIT}. */
    private static String awaitTheRunsPage(Predicate<String> wanted) {
        Path page = workingDirectory.resolve(RUNS_PAGE);
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

    /** Marks every directory, file name and text the invocations are given, and one file the converter faults on. */
    private static void aMarkedCorpus() throws IOException {
        Path folder = Files.createDirectories(root.resolve(MARKER + "-folder"));
        for (String name : List.of(MARKER + "-first.txt", MARKER + "-second.txt")) {
            Files.writeString(folder.resolve(name), "the text of " + name + " says " + MARKER);
        }
        Files.writeString(folder.resolve(SeedScriptedExtractionBeans.CONVERTER_FAULT),
                "a file the converter faults on, and " + MARKER);
        Files.writeString(seeds.resolve(MARKER + "-seed.txt"), "a seed that says " + MARKER);
    }

    /** The seed folder named, gate 3 open, and stage 4's gate open at {@code boilerplateFloor}. */
    private void profileNaming(String boilerplateFloor) {
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .degenerateOutputConfidenceFloor(profileStore.load().degenerateOutputConfidenceFloor())
                .embeddingModel(MODEL_NAME, "set by this test, so gate 3 is open")
                .boilerplateDocumentFrequencyFloor(boilerplateFloor, "set by this test, so stage 4's gate is open")
                .build());
    }

    /** A run of the ledger, and how the tab tells it: its walk's number, its stage and its ordinal. */
    private record Run(String id, String stage, long walk, int ordinal) {

        String key() {
            return count(walk) + "|" + stage + "|" + count(ordinal);
        }
    }

    /** What the two invocations left: the runs after each, those each minted, the counts and the tab as it was written. */
    private record TwoInvocations(
            List<Run> afterFirst,
            List<Run> afterSecond,
            List<Run> mintedByFirst,
            List<Run> mintedBySecond,
            Map<String, long[]> kinds,
            Map<String, Set<String>> finished,
            String whileSecondRuns,
            String lastPage) {}

    /**
     * Hands each event to {@code onEvent} on the thread that logged it, so a step's thread can be held at one of
     * its lines while the tab is looked at. Unsynchronised, so a line another thread logs meanwhile is not held.
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
