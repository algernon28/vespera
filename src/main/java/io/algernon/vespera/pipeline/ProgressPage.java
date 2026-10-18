package io.algernon.vespera.pipeline;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.ReplacingMove;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.Runs;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.core.step.StepExecution;

/**
 * The page {@code vespera run} rewrites in the working directory while it runs (ADR-265): each step's state,
 * counts and last progress line, the time elapsed and the database's size, and (ADR-268) the survivors and
 * the verdicts of the step removing occurrences. It reads only what the job holds in memory, the verdict counts
 * {@code Verdicts} keeps and two file sizes, and names no path, title, document text, exception message or run id.
 *
 * <p>Writes are made by one daemon thread of its own at a fixed delay, so no step thread renders or writes the
 * page; the first is made by {@link #beforeJob} and the last by {@link #afterJob}, under the lock that every
 * write is made under, after which a write that was due writes nothing. A write that fails is warned of once,
 * and again only after one has succeeded; it never fails the invocation (ADR-265 section 4).
 *
 * <p>It holds no rule: it decides no verdict, cache key, cluster or text of the deliverable.
 */
final class ProgressPage implements JobExecutionListener {

    static final String FILE_NAME = "progress.html";

    private static final Logger log = LoggerFactory.getLogger(ProgressPage.class);

    static final String STEPS_FILE_NAME = "progress-steps.html";

    static final String SAMPLES_FILE_NAME = "progress-samples.html";

    /** The key of what entered the outcome step, in its step execution's context (ADR-268 section 2). */
    static final String ENTERED = "progress-page.entered";

    private static final String TITLE = "Vespera progress";

    /** At most this many samples are held; one more thins them (ADR-267 section 2). */
    private static final int MOST_SAMPLES = 256;

    /** A page is stale when it was last rewritten more than this many intervals ago (ADR-267 section 5). */
    private static final int STALE_AFTER_INTERVALS = 3;

    /** A rate is read back to the latest sample at least this long before the latest one (ADR-268 section 3). */
    private static final long RATE_SECONDS = 600;

    static final String RUNS_FILE_NAME = "progress-runs.html";

    /**
     * The one script of every tab of every write (ADR-269 section 3): the same text always, holding no data of an
     * invocation. It checks the liveness mark and draws the hover of each line chart from the attributes of the
     * chart's hit circles; it writes text only through {@code textContent} and parses no markup.
     */
    private static final String SCRIPT =
            "document.querySelectorAll('.liveness[data-written]').forEach(function (mark) {\n"
            + "  var behind = Date.now() - Number(mark.dataset.written);\n"
            + "  if (behind > " + STALE_AFTER_INTERVALS + " * Number(mark.dataset.interval)) {\n"
            + "    mark.className = 'liveness stale';\n"
            + "    mark.textContent = 'not rewritten for ' + Math.floor(behind / 1000) + ' s';\n"
            + "  }\n"
            + "});\n"
            + "document.querySelectorAll('svg.chart').forEach(function (svg) {\n"
            + "  var hits = Array.prototype.slice.call(svg.querySelectorAll('circle.hit'));\n"
            + "  var axis = svg.querySelector('line.axis');\n"
            + "  if (hits.length === 0 || axis === null) { return; }\n"
            + "  var card = svg.parentNode;\n"
            + "  var cross = document.createElementNS('http://www.w3.org/2000/svg', 'line');\n"
            + "  cross.setAttribute('class', 'crosshair');\n"
            + "  cross.setAttribute('y1', axis.getAttribute('y1'));\n"
            + "  cross.setAttribute('y2', axis.getAttribute('y2'));\n"
            + "  cross.style.display = 'none';\n"
            + "  svg.appendChild(cross);\n"
            + "  var tip = document.createElement('div');\n"
            + "  tip.className = 'tooltip';\n"
            + "  tip.style.display = 'none';\n"
            + "  card.appendChild(tip);\n"
            + "  svg.addEventListener('mouseleave', function () {\n"
            + "    cross.style.display = 'none';\n"
            + "    tip.style.display = 'none';\n"
            + "  });\n"
            + "  svg.addEventListener('mousemove', function (event) {\n"
            + "    var box = svg.getBoundingClientRect();\n"
            + "    var scale = svg.viewBox.baseVal.width / box.width;\n"
            + "    var at = (event.clientX - box.left) * scale;\n"
            + "    var best = null;\n"
            + "    hits.forEach(function (hit) {\n"
            + "      var x = Number(hit.getAttribute('cx'));\n"
            + "      if (best === null || Math.abs(x - at) < Math.abs(best - at)) { best = x; }\n"
            + "    });\n"
            + "    var names = [];\n"
            + "    var values = {};\n"
            + "    var when = '';\n"
            + "    hits.forEach(function (hit) {\n"
            + "      if (Number(hit.getAttribute('cx')) !== best) { return; }\n"
            + "      when = hit.dataset.at;\n"
            + "      if (names.indexOf(hit.dataset.series) < 0) { names.push(hit.dataset.series); }\n"
            + "      values[hit.dataset.series] = hit.dataset.value;\n"
            + "    });\n"
            + "    cross.setAttribute('x1', best);\n"
            + "    cross.setAttribute('x2', best);\n"
            + "    cross.style.display = '';\n"
            + "    while (tip.firstChild) { tip.removeChild(tip.firstChild); }\n"
            + "    var heading = document.createElement('div');\n"
            + "    heading.textContent = when;\n"
            + "    tip.appendChild(heading);\n"
            + "    names.forEach(function (name) {\n"
            + "      var line = document.createElement('div');\n"
            + "      line.textContent = name + ': ' + values[name];\n"
            + "      tip.appendChild(line);\n"
            + "    });\n"
            + "    var beside = card.getBoundingClientRect();\n"
            + "    var left = box.left - beside.left + card.scrollLeft + best / scale;\n"
            + "    var onTheLeft = best / scale > box.width / 2;\n"
            + "    tip.style.left = (onTheLeft ? left - 12 : left + 12) + 'px';\n"
            + "    tip.style.transform = onTheLeft ? 'translateX(-100%)' : 'none';\n"
            + "    tip.style.top = (box.top - beside.top + card.scrollTop + 8) + 'px';\n"
            + "    tip.style.display = 'block';\n"
            + "  });\n"
            + "});";

    private static final String[] TAB_NAMES = {"Overview", "Steps", "Samples", "Runs"};

    private static final String[] TAB_FILES = {FILE_NAME, "progress-steps.html", "progress-samples.html", RUNS_FILE_NAME};

    /** The steps that record their completion under a run of each stage, in job order (ADR-269 section 5). */
    private static final Map<String, List<String>> STEPS_OF_A_STAGE = Map.of(
            StageModules.BYTE_LEVEL_REDUCTION.stage(), List.of(StepNames.BYTE_LEVEL_REDUCTION),
            StageModules.EXTRACTION.stage(), List.of(StepNames.EXTRACTION),
            StageModules.CONTENT_CENSUS.stage(), List.of(StepNames.CONTENT_CENSUS),
            StageModules.CONTENT_REDUNDANCY.stage(), List.of(StepNames.REDUNDANCY_SIGNATURE, StepNames.CONTENT_REDUNDANCY),
            StageModules.SEED_MEASUREMENT.stage(), List.of(StepNames.SEED_EXTRACTION, StepNames.SEED_CORPUS_COMPARISON),
            StageModules.EMBEDDING_SCORING.stage(),
            List.of(StepNames.EMBEDDING_SCORING, StepNames.TRANSLATION, StepNames.RELEVANCE_SCORING, StepNames.CLUSTERING),
            StageModules.ARRANGEMENT.stage(), List.of(StepNames.ARRANGEMENT),
            StageModules.GENERATION.stage(), List.of(StepNames.GENERATION));

    /** The runs a working directory holds, as one reading of the ledger made them (ADR-269 section 5). */
    record RunsRead(List<Runs.Listed> listed, Map<String, Set<String>> finished, Map<String, Map<String, Long>> kinds) {}

    /** What the job thread holds of the runs: which reading (1 as the invocation started, 2 as it ended) and its outcome. */
    private record HeldRuns(int reading, RunsRead read) {}

    private static final Pattern COUNT_OF_TOTAL = Pattern.compile(": ([0-9,]+) of ([0-9,]+) \\([0-9]+%\\)$");

    /** A counter's line: its label, how far it has got and, where it has one, its total. */
    private static final Pattern COUNTER = Pattern.compile(
            "^(" + StageProgress.LABEL_SHAPE + "): ([0-9,]+)(?: of ([0-9,]+) \\([0-9]+%\\)| so far)$");

    private static final int CHART_WIDTH = 1100;
    private static final int CHART_HEIGHT = 260;
    private static final int PLOT_TOP = 36;
    private static final int PLOT_HEIGHT = 168;
    private static final int TIMELINE_STATES = 190;
    private static final int TIMELINE_LABELS = 320;
    private static final int TIMELINE_ROW = 24;

    private static final String[] SIZE_UNITS = {"B", "KB", "MB", "GB", "TB"};

    /** The dash of each series after the first: a second line of equal values is seen beside the first. */
    private static final String[] DASHES = {"", "6 4", "2 3", "8 3 2 3", "1 4"};

    private static final String DATABASE_FILE = "vespera.db";

    private static final String WRITE_AHEAD_LOG_FILE = "vespera.db-wal";

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

    /** What the row of tiles on the progress page shows (ADR-275 section 2). */
    private static final String JOB_TILES_HOVER = "The figures of the job so far.";

    /** What the row of tiles on the label page shows (ADR-275 section 2). */
    private static final String INVOCATION_TILES_HOVER = "The figures of this invocation.";

    /** What each tile shows, as hover text: one for each tile key and each blocking kind (ADR-275 section 2). */
    private static final Map<String, String> TILE_HOVER = Map.ofEntries(
            Map.entry("steps", "How many of the steps of the job have finished."),
            Map.entry("step", "The step running now, or the last one to run, with its state."),
            Map.entry("elapsed", "Time since this invocation started."),
            Map.entry("database", "Size of the working directory's database and, where shown, how fast it has grown per minute lately."),
            Map.entry("survivors", "Occurrences the latest step that removes them has not removed, of those that entered it."),
            Map.entry("command", "The vespera command this page reports on."),
            Map.entry("broken", "Occurrences removed because the file is broken and cannot be read."),
            Map.entry("duplicate-of", "Occurrences removed because another file has exactly the same bytes."),
            Map.entry("superseded-by", "Occurrences removed because a newer version of the same file exists."),
            Map.entry("out-of-scope", "Occurrences removed because their format or place is outside what the job reads."),
            Map.entry("extraction-failed", "Occurrences removed because the document converter could not turn them into text."),
            Map.entry("degenerate-output", "Occurrences removed because the converted text is too poor to use."),
            Map.entry("redundant-with", "Occurrences removed because their text repeats or is contained in another document."),
            Map.entry("translation-of", "Occurrences removed because they are a translation of a document kept in another language."),
            Map.entry("below-threshold", "Occurrences removed because their relevance scores below the threshold."));

    /**
     * The blocking kinds as {@code CONTEXT.md} writes them, in {@code VerdictKind}'s order (ADR-268 section 1). Names,
     * not the enum: the page decides no verdict, and no class of {@code pipeline} that does not is to name a kind.
     */
    private static final List<String> BLOCKING = List.of(
            "broken", "duplicate-of", "superseded-by", "out-of-scope",
            "extraction-failed", "degenerate-output", "redundant-with", "translation-of", "below-threshold");

    /** The steps that remove occurrences, in job order, and the kinds each records (ADR-268 section 1). */
    private static final Map<String, List<String>> OUTCOME_STEPS = new LinkedHashMap<>();

    static {
        OUTCOME_STEPS.put(
                StepNames.BYTE_LEVEL_REDUCTION, List.of("broken", "duplicate-of", "superseded-by", "out-of-scope"));
        OUTCOME_STEPS.put(StepNames.EXTRACTION, List.of("extraction-failed", "degenerate-output"));
        OUTCOME_STEPS.put(StepNames.CONTENT_REDUNDANCY, List.of("redundant-with"));
        OUTCOME_STEPS.put(StepNames.TRANSLATION, List.of("translation-of"));
        OUTCOME_STEPS.put(StepNames.RELEVANCE_FLOOR, List.of("below-threshold"));
    }

    /** The whole text of a statement's line over rows: its label in the form of a counter's, then the percentage. */
    private static final Pattern STATEMENT_LINE =
            Pattern.compile(StageProgress.LABEL_SHAPE + ": (about|at least) [0-9]+% of [0-9,]+ rows");

    private static final DateTimeFormatter WRITTEN_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    private final Path page;
    private final Path workingDirectory;
    private final Duration interval;
    private final Clock clock;

    /** The last progress line admitted for each step, written by the step's own thread and read by the writer. */
    private final Map<String, String> lastLine = new ConcurrentHashMap<>();

    /** Steps whose execution is complete while their own listener still runs on the step's thread. */
    private final java.util.Set<String> stillInside = ConcurrentHashMap.newKeySet();

    private final Object writing = new Object();

    /**
     * One reading made at a write (ADR-267 section 2, ADR-268 section 4): {@code survivors} is -1 and
     * {@code outcomeStep} null where no step has recorded what entered it.
     */
    private record Sample(
            long seconds, long databaseBytes, String step, long read, long written, String progress,
            String outcomeStep, long survivors, long[] kinds) {}

    /** Guarded by {@link #writing}: at most {@link #MOST_SAMPLES}, oldest first. */
    private final List<Sample> samples = new ArrayList<>();

    /** A line of a chart: the class its colour comes from, what it is called, and its value at each sample drawn. */
    private record Line(String css, String name, double[] values) {}

    /** A value axis: its title, its range, and the values at which it has a tick and gridline. */
    private record Axis(String title, double lo, double hi, List<Double> ticks, int decimals) {}

    /** The verdicts recorded in this process by kind, read at each write without a lock any writer waits on. */
    private final Supplier<Map<String, Long>> recorded;

    /** Guarded by {@link #writing}: {@link #recorded} when the invocation began, so the page counts its own. */
    private Map<String, Long> baseline = Map.of();

    private volatile JobExecution jobExecution;
    private ScheduledExecutorService writer;
    private final List<ch.qos.logback.classic.Logger> listenedTo = new ArrayList<>();
    private final List<LineAppender> appenders = new ArrayList<>();

    /** Guarded by {@link #writing}. */
    private boolean ended;

    /** Guarded by {@link #writing}: whether the last write failed, so that the warning is not said again. */
    private boolean warned;

    private final PageOpener opener;

    private final Supplier<RunsRead> runsReader;

    /** The reading of the runs the tab shows, set on the job's thread and read by the writer; null before the first. */
    private volatile HeldRuns heldRuns;

    /** Whether the failure of a reading was logged in this invocation. */
    private volatile boolean warnedOfRuns;

    /** Guarded by {@link #writing}: the instant of the first write, the start of a job that holds none. */
    private Instant firstWrite;

    /** Guarded by {@link #writing}: the command a command-shaped invocation is about, or null for the job's. */
    private String command;

    private Instant commandStarted;

    ProgressPage(Path workingDirectory, Duration interval, Clock clock) {
        this(workingDirectory, interval, clock, null);
    }

    ProgressPage(Path workingDirectory, Duration interval, Clock clock, PageOpener opener) {
        this(workingDirectory, interval, clock, opener, Map::of);
    }

    ProgressPage(
            Path workingDirectory,
            Duration interval,
            Clock clock,
            PageOpener opener,
            Supplier<Map<String, Long>> recorded) {
        this(workingDirectory, interval, clock, opener, recorded, () -> new RunsRead(List.of(), Map.of(), Map.of()));
    }

    ProgressPage(
            Path workingDirectory,
            Duration interval,
            Clock clock,
            PageOpener opener,
            Supplier<Map<String, Long>> recorded,
            Supplier<RunsRead> runsReader) {
        this.workingDirectory = workingDirectory.toAbsolutePath().normalize();
        this.page = this.workingDirectory.resolve(FILE_NAME);
        this.interval = interval;
        this.clock = clock;
        this.opener = opener;
        this.recorded = recorded;
        this.runsReader = runsReader;
    }

    /** What the ledger holds of every run, asked statement by statement and sorting nothing (ADR-269 section 5). */
    static RunsRead readRuns(Ledger ledger) {
        List<Runs.Listed> listed = ledger.runs().listed();
        Map<String, Map<String, Long>> kinds = new java.util.HashMap<>();
        for (Runs.Listed run : listed) {
            kinds.put(run.id(), ledger.verdicts().countsOfRun(new RunId(run.id())));
        }
        return new RunsRead(listed, ledger.runs().finishedSteps(), kinds);
    }

    /**
     * Reads the runs on the thread that runs the job (ADR-269 section 5); whatever it throws is logged once in the
     * invocation, as the exception class's name only, and the tab says the runs could not be read.
     */
    private void readTheRuns(int reading) {
        RunsRead read = null;
        try {
            read = runsReader.get();
        } catch (RuntimeException e) {
            if (!warnedOfRuns) {
                warnedOfRuns = true;
                log.warn("the runs on the progress page could not be read: {}", e.getClass().getName());
            }
        }
        heldRuns = new HeldRuns(reading, read);
    }

    /** The page's absolute, normalised path in {@code workingDirectory} (ADR-266 section 1). */
    static Path pathIn(Path workingDirectory) {
        return workingDirectory.toAbsolutePath().normalize().resolve(FILE_NAME);
    }

    /**
     * Holds in the running step's context what entered it: the survivors of the run it records under, counted once
     * on the step's own thread before its first verdict (ADR-268 section 2). Nothing where no step is bound.
     */
    static void entered(long survivors) {
        StepContext context = StepSynchronizationManager.getContext();
        if (context != null) {
            context.getStepExecution().getExecutionContext().putLong(ENTERED, survivors);
        }
    }

    /** A command that starts no job writes its first page (reloading) and has it opened once (ADR-266 section 3). */
    void commandStarts(String commandLine) {
        boolean written;
        synchronized (writing) {
            command = commandLine;
            commandStarted = clock.instant();
            firstWrite = null;
            ended = false;
            written = write(false);
        }
        openOnce(written);
    }

    /** The last page of a command that starts no job: no reload, and its counts as a table when it recorded. */
    void commandEnds(String outcome, Map<String, Integer> counts) {
        synchronized (writing) {
            ended = true;
            write(true, outcome, counts);
        }
    }

    /** Hands the page to the opener, outside the lock; nothing is opened after a first write that failed. */
    private void openOnce(boolean written) {
        if (!written || opener == null) {
            return;
        }
        try {
            opener.open(page.toUri());
        } catch (IOException | RuntimeException e) {
            log.warn("the progress page could not be opened: {}", e.getClass().getName());
        }
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        this.jobExecution = jobExecution;
        lastLine.clear();
        stillInside.clear();
        for (Class<?> source : List.of(StageProgress.class, StatementProgress.class)) {
            if (LoggerFactory.getLogger(source) instanceof ch.qos.logback.classic.Logger logger) {
                LineAppender appender = new LineAppender();
                appender.setContext(logger.getLoggerContext());
                appender.start();
                logger.addAppender(appender);
                listenedTo.add(logger);
                appenders.add(appender);
            }
        }
        boolean written;
        synchronized (writing) {
            command = null;
            ended = false;
            baseline = recorded.get();
            samples.clear();
            firstWrite = null;
            heldRuns = null;
            warnedOfRuns = false;
            written = write(false);
        }
        openOnce(written);
        try {
            if (interval.isZero() || interval.isNegative()) {
                throw new IllegalArgumentException("vespera.progress-interval is not above zero");
            }
            writer = Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "progress-page");
                thread.setDaemon(true);
                return thread;
            });
            writer.scheduleWithFixedDelay(this::scheduledWrite, interval.toNanos(), interval.toNanos(), TimeUnit.NANOSECONDS);
        } catch (RuntimeException | OutOfMemoryError e) {
            synchronized (writing) {
                failed(e);
            }
        }
        readTheRuns(1);
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        if (writer != null) {
            writer.shutdown();
        }
        for (int i = 0; i < listenedTo.size(); i++) {
            listenedTo.get(i).detachAppender(appenders.get(i));
            appenders.get(i).stop();
        }
        listenedTo.clear();
        appenders.clear();
        readTheRuns(2);
        synchronized (writing) {
            ended = true;
            this.jobExecution = jobExecution;
            write(true);
        }
    }

    private void scheduledWrite() {
        synchronized (writing) {
            if (!ended) {
                write(false);
            }
        }
    }

    /** One write, under {@link #writing}; a failure is caught here and never reaches the job. */
    private boolean write(boolean last) {
        return write(last, null, Map.of());
    }

    private boolean write(boolean last, String outcome, Map<String, Integer> counts) {
        try {
            if (command == null) {
                String[] pages = render(last); // steps, samples, runs, progress: the order they are moved into place
                put(STEPS_FILE_NAME, pages[0]);
                put(SAMPLES_FILE_NAME, pages[1]);
                put(RUNS_FILE_NAME, pages[2]);
                put(FILE_NAME, pages[3]);
            } else {
                put(FILE_NAME, renderCommand(last, outcome, counts));
            }
            warned = false;
            return true;
        } catch (IOException | RuntimeException e) {
            for (String name : List.of(STEPS_FILE_NAME, SAMPLES_FILE_NAME, RUNS_FILE_NAME, FILE_NAME)) {
                try {
                    Files.deleteIfExists(workingDirectory.resolve(name + ".part"));
                } catch (IOException | RuntimeException ignored) {
                    // the next write overwrites it
                }
            }
            failed(e);
            return false;
        }
    }

    /** One page rendered into its {@code .part} and moved over its place in one move. */
    private void put(String name, String text) throws IOException {
        Path part = workingDirectory.resolve(name + ".part");
        Files.writeString(part, text, StandardCharsets.UTF_8);
        ReplacingMove.ATOMIC.intoPlace(part, workingDirectory.resolve(name));
    }

    private void failed(Throwable e) {
        if (!warned) {
            warned = true;
            log.warn("the progress page could not be written: {}", e.getClass().getName());
        }
    }

    /** The three pages of one write, from one reading: steps, samples, progress. */
    private String[] render(boolean last) {
        JobExecution job = jobExecution;
        Instant now = clock.instant();
        ZoneId zone = ZoneId.systemDefault();
        if (firstWrite == null) {
            firstWrite = now;
        }
        Instant started = job.getStartTime() == null ? firstWrite : job.getStartTime().atZone(zone).toInstant();
        Map<String, StepExecution> executions = new java.util.HashMap<>();
        for (StepExecution execution : new ArrayList<>(job.getStepExecutions())) {
            executions.put(execution.getStepName(), execution);
        }
        ShutGates shutGates = new ShutGates(job.getExecutionContext());
        int seconds = (int) Math.max(1, (interval.toMillis() + 999) / 1000);

        StringBuilder rows = new StringBuilder();
        Map<String, String> states = new LinkedHashMap<>();
        int finished = 0;
        String running = null;
        String latestStarted = null;
        for (String step : STEPS_IN_JOB_ORDER) {
            StepExecution execution = executions.get(step);
            boolean inside = !last && stillInside.contains(step) && !anExecutionAfter(step, executions);
            String state = stateOf(execution, shutGates.shutAt(step), inside);
            states.put(step, state);
            rows.append(row(step, execution, state, now, zone));
            if (state.equals("finished") || state.equals("stopped at a gate")) {
                finished++;
            }
            if (state.equals("running")) {
                running = step;
            }
            if (execution != null) {
                latestStarted = step;
            }
        }
        String shown = running != null ? running : latestStarted;
        Duration elapsed = Duration.between(started, now);
        long bytes = databaseBytes();
        StepExecution runningExecution = running == null ? null : executions.get(running);

        long[] kinds = new long[BLOCKING.size()];
        Map<String, Long> nowRecorded = recorded.get();
        for (int i = 0; i < kinds.length; i++) {
            String kind = BLOCKING.get(i);
            kinds[i] = Math.max(0, nowRecorded.getOrDefault(kind, 0L) - baseline.getOrDefault(kind, 0L));
        }
        String outcomeStep = null;
        long entered = 0;
        for (String step : OUTCOME_STEPS.keySet()) {
            StepExecution execution = executions.get(step);
            if (execution != null && execution.getExecutionContext().containsKey(ENTERED)) {
                outcomeStep = step;
                entered = execution.getExecutionContext().getLong(ENTERED);
            }
        }
        long survivors = -1;
        if (outcomeStep != null) {
            survivors = entered;
            for (String kind : OUTCOME_STEPS.get(outcomeStep)) {
                survivors -= kinds[BLOCKING.indexOf(kind)];
            }
            survivors = Math.max(0, survivors);
        }
        samples.add(new Sample(
                Math.max(0, elapsed.getSeconds()),
                bytes,
                running,
                runningExecution == null ? 0 : runningExecution.getReadCount(),
                runningExecution == null ? 0 : runningExecution.getWriteCount(),
                running == null ? null : lastLine.get(running),
                outcomeStep,
                survivors,
                kinds));
        if (samples.size() > MOST_SAMPLES) {
            List<Sample> kept = new ArrayList<>();
            for (int i = 0; i < samples.size(); i += 2) {
                kept.add(samples.get(i));
            }
            samples.clear();
            samples.addAll(kept);
        }
        Sample latest = samples.getLast();
        Sample base = rateBase();

        String startedAt = WRITTEN_AT.format(LocalDateTime.ofInstant(started, zone));
        String status = last
                ? "This invocation has ended: " + job.getStatus().name().toLowerCase(Locale.ROOT) + ". It started at "
                        + startedAt + " and ended at " + WRITTEN_AT.format(LocalDateTime.ofInstant(now, zone)) + "."
                : "This invocation is running. It started at " + startedAt + ", and this page is rewritten every "
                        + seconds + " s.";
        String writtenAt = "Written at " + WRITTEN_AT.format(LocalDateTime.ofInstant(now, zone));
        String mark = last
                ? "<span class=\"liveness\">ended</span> "
                : "<span class=\"liveness\" data-written=\"" + now.toEpochMilli() + "\" data-interval=\""
                        + interval.toMillis() + "\">live</span> ";
        String[] heads = new String[TAB_NAMES.length];
        for (int tab = 0; tab < heads.length; tab++) {
            heads[tab] = ReportPage.heading(1, tab == 0 ? TITLE : TITLE + ": " + TAB_NAMES[tab].toLowerCase(Locale.ROOT))
                    + tabBar(tab)
                    + ReportPage.paragraph(ReportPage.escape(status))
                    + ReportPage.paragraph(mark + writtenAt);
        }

        String stepsBody = heads[1]
                + ReportPage.tableCard(
                        "Each step of the job with its state, the rows it read and wrote, and how long it took.",
                        ReportPage.headerRow("Step", "State", "Read", "Written", "Skipped", "Duration", "Progress"),
                        rows.toString());
        StringBuilder sampleRows = new StringBuilder();
        for (Sample sample : samples) {
            String[] cells = new String[8 + BLOCKING.size()];
            cells[0] = ReportPage.textCell(clockTime(Duration.ofSeconds(sample.seconds())));
            cells[1] = ReportPage.textCell(size(sample.databaseBytes()));
            cells[2] = ReportPage.textCell(sample.step() == null ? "" : sample.step());
            cells[3] = ReportPage.numberCell(sample.step() == null ? "" : count(sample.read()));
            cells[4] = ReportPage.numberCell(sample.step() == null ? "" : count(sample.written()));
            cells[5] = ReportPage.textCell(sample.progress() == null ? "" : sample.progress());
            cells[6] = ReportPage.textCell(sample.outcomeStep() == null ? "" : sample.outcomeStep());
            cells[7] = ReportPage.numberCell(sample.survivors() < 0 ? "" : count(sample.survivors()));
            for (int i = 0; i < BLOCKING.size(); i++) {
                cells[8 + i] = ReportPage.numberCell(count(sample.kinds()[i]));
            }
            sampleRows.append(ReportPage.row(cells));
        }
        String[] sampleHeaders = new String[8 + BLOCKING.size()];
        System.arraycopy(
                new String[] {"Elapsed", "Database size", "Step", "Read", "Written", "Progress", "Outcome step", "Survivors"},
                0, sampleHeaders, 0, 8);
        for (int i = 0; i < BLOCKING.size(); i++) {
            sampleHeaders[8 + i] = BLOCKING.get(i);
        }
        String samplesBody = heads[2] + ReportPage.tableCard(
                "One line per sample of the job: elapsed time, database size, the running step and the verdict counts.",
                ReportPage.headerRow(sampleHeaders), sampleRows.toString());
        String runsBody = heads[3] + runsTab(job);

        String bar = "";
        if (runningExecution != null) {
            Matcher counted = COUNT_OF_TOTAL.matcher(lastLine.getOrDefault(running, ""));
            if (counted.find() && !counted.group(2).replace(",", "").matches("0+")) {
                bar = ReportPage.paragraph("<progress value=\"" + counted.group(1).replace(",", "") + "\" max=\""
                        + counted.group(2).replace(",", "") + "\"></progress> "
                        + ReportPage.escape(lastLine.get(running)));
            }
        }
        StringBuilder tiles = new StringBuilder();
        tiles.append(tile("steps", "Steps finished", finished + " of " + STEPS_IN_JOB_ORDER.size(), null));
        tiles.append(shown == null
                ? tile("step", "Step", "none yet", null)
                : tile("step", "Step", shown, states.get(shown)));
        tiles.append(tile("elapsed", "Elapsed", clockTime(elapsed), null));
        tiles.append(tile("database", "Database size", size(bytes), growth(base, latest)));
        if (outcomeStep != null) {
            tiles.append(tile(
                    "survivors", "Survivors", count(survivors),
                    "of " + count(entered) + " when " + outcomeStep + " started"
                            + ("running".equals(states.get(outcomeStep)) ? ", including those not yet judged" : "")));
            for (String kind : OUTCOME_STEPS.get(outcomeStep)) {
                int at = BLOCKING.indexOf(kind);
                tiles.append(tile(kind, kind, count(latest.kinds()[at]), kindRate(base, latest, at)));
            }
        }
        String progressBody = heads[0]
                + "<div class=\"tiles\" title=\"" + ReportPage.escapeAttribute(JOB_TILES_HOVER) + "\">\n" + tiles + "</div>\n"
                + bar
                + timeline(states, executions, started, now, zone)
                + databaseChart()
                + countsChart()
                + verdictsChart();
        return new String[] {
            page("steps", stepsBody, last, seconds), page("samples", samplesBody, last, seconds),
            page("runs", runsBody, last, seconds), page("", progressBody, last, seconds)
        };
    }

    /** The bar of the four tabs, relative links, the page's own marked (ADR-269 section 1). */
    private static String tabBar(int current) {
        StringBuilder bar = new StringBuilder("<nav class=\"tabs\" aria-label=\"Progress pages\">");
        for (int tab = 0; tab < TAB_NAMES.length; tab++) {
            bar.append("<a href=\"").append(TAB_FILES[tab]).append('"').append(tab == current ? " aria-current=\"page\"" : "")
                    .append('>').append(TAB_NAMES[tab]).append("</a>");
        }
        return bar.append("</nav>\n").toString();
    }

    /** The tab of runs below its heading: which reading it shows, and the table of the held reading (section 5). */
    private String runsTab(JobExecution job) {
        HeldRuns held = heldRuns;
        if (held == null) {
            return ReportPage.paragraph("The runs this working directory holds are read before the first step starts.");
        }
        String shows = ReportPage.paragraph(
                held.reading() == 1
                        ? "The runs this working directory held when this invocation started."
                        : "The runs this working directory held when this invocation ended.");
        if (held.read() == null) {
            return shows + ReportPage.paragraph("The runs could not be read.");
        }
        if (held.read().listed().isEmpty()) {
            return shows + ReportPage.paragraph("No run is recorded in this working directory.");
        }
        Set<String> arrivedAt = new java.util.HashSet<>(new InvocationRuns(job.getExecutionContext()).all().values());
        Map<String, Integer> ordinals = new java.util.HashMap<>();
        List<Object[]> runs = new ArrayList<>();
        for (Runs.Listed run : held.read().listed()) {
            int ordinal = ordinals.merge(run.walk() + "|" + run.stage(), 1, Integer::sum);
            runs.add(new Object[] {run, ordinal});
        }
        List<String> cascade = Arrays.stream(StageModules.values()).map(StageModules::stage).toList();
        runs.sort(java.util.Comparator.<Object[]>comparingLong(r -> ((Runs.Listed) r[0]).walk())
                .thenComparingInt(r -> {
                    int rank = cascade.indexOf(((Runs.Listed) r[0]).stage());
                    return rank < 0 ? cascade.size() : rank;
                })
                .thenComparing(r -> ((Runs.Listed) r[0]).stage())
                .thenComparingInt(r -> (Integer) r[1]));
        StringBuilder rows = new StringBuilder();
        for (Object[] entry : runs) {
            Runs.Listed run = (Runs.Listed) entry[0];
            List<String> steps = STEPS_OF_A_STAGE.get(run.stage());
            Set<String> done = held.read().finished().getOrDefault(run.id(), Set.of());
            long finished = steps == null ? 0 : steps.stream().filter(done::contains).count();
            String[] cells = new String[6 + BLOCKING.size()];
            cells[0] = ReportPage.numberCell(count(run.walk()));
            cells[1] = ReportPage.textCell(run.stage());
            cells[2] = ReportPage.numberCell(count((Integer) entry[1]));
            cells[3] = ReportPage.textCell(steps == null ? "" : finished + " of " + steps.size());
            cells[4] = ReportPage.textCell(steps == null ? "" : finished == steps.size() ? "finished" : "not finished");
            cells[5] = ReportPage.textCell(arrivedAt.contains(run.id()) ? "arrived at" : "");
            Map<String, Long> kinds = held.read().kinds().getOrDefault(run.id(), Map.of());
            for (int i = 0; i < BLOCKING.size(); i++) {
                cells[6 + i] = ReportPage.numberCell(count(kinds.getOrDefault(BLOCKING.get(i), 0L)));
            }
            rows.append(ReportPage.row(cells));
        }
        List<String> headers = new ArrayList<>(
                List.of("Walk", "Stage", "Run", "Steps finished", "State", "This invocation"));
        headers.addAll(BLOCKING);
        return shows + ReportPage.tableCard(
                "Each run this working directory holds: its walk and stage, how many of its steps finished, whether this invocation arrived at it, and what it removed by kind.",
                ReportPage.headerRow(headers.toArray(new String[0])), rows.toString());
    }

    private static String page(String which, String body, boolean last, int seconds) {
        String withScript = body + "<script>\n" + SCRIPT + "\n</script>\n";
        return last ? ReportPage.drawn(titled(which), withScript) : ReportPage.drawnReloading(titled(which), withScript, seconds);
    }

    private static String titled(String which) {
        return which.isEmpty() ? TITLE : TITLE + ": " + which;
    }

    /** A tile: a label, a value and, where it has one, a detail, each text and no other element (ADR-268 section 5). */
    private static String tile(String key, String label, String value, String detail) {
        String hover = TILE_HOVER.get(key);
        if (hover == null) {
            throw new IllegalStateException("no hover text for tile " + key);
        }
        return "<div class=\"tile\" data-tile=\"" + key + "\" title=\"" + ReportPage.escapeAttribute(hover) + "\"><span class=\"label\">" + ReportPage.escape(label)
                + "</span><span class=\"value\">" + ReportPage.escape(value) + "</span>"
                + (detail == null ? "" : "<span class=\"detail\">" + ReportPage.escape(detail) + "</span>")
                + "</div>\n";
    }

    private static String f1(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /** A count: whole, with {@code ,} between groups of three digits (ADR-268 section 6). */
    private static String count(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    /** The index in {@link #SIZE_UNITS} of the largest unit in which {@code bytes} is at least 1, or 0. */
    private static int unitOf(double bytes) {
        int unit = 0;
        double value = bytes;
        while (unit < SIZE_UNITS.length - 1 && value >= 1000) {
            value /= 1000;
            unit++;
            // a value that rounds to 1,000 of a unit is written in the next
            if (unit < SIZE_UNITS.length - 1 && Math.round(value * 100) >= 100_000) {
                value /= 1000;
                unit++;
            }
        }
        return unit;
    }

    /** A size in the largest unit of B, KB, MB, GB and TB whose value is at least 1 (ADR-268 section 6). */
    private static String size(long bytes) {
        int unit = unitOf(bytes);
        if (unit == 0) {
            return count(bytes) + " B";
        }
        return String.format(Locale.ROOT, "%,.2f", bytes / Math.pow(1000, unit)) + " " + SIZE_UNITS[unit];
    }

    /** The latest held sample at least ten minutes before the latest, or the first; null where there is no rate. */
    private Sample rateBase() {
        Sample latest = samples.getLast();
        Sample base = null;
        for (Sample sample : samples.subList(0, samples.size() - 1)) {
            if (latest.seconds() - sample.seconds() >= RATE_SECONDS) {
                base = sample;
            }
        }
        if (base == null) {
            base = samples.getFirst();
        }
        return base == latest || base.seconds() == latest.seconds() ? null : base;
    }

    private static String over(Sample base, Sample latest) {
        return " / min over the last " + clockTime(Duration.ofSeconds(latest.seconds() - base.seconds()));
    }

    private static String growth(Sample base, Sample latest) {
        if (base == null) {
            return "growth not measured yet";
        }
        double perMinute = (latest.databaseBytes() - base.databaseBytes()) * 60.0 / (latest.seconds() - base.seconds());
        return (perMinute < 0 ? "-" : "") + size(Math.round(Math.abs(perMinute))) + over(base, latest);
    }

    private static String kindRate(Sample base, Sample latest, int kind) {
        if (base == null) {
            return "rate not measured yet";
        }
        double perMinute = (latest.kinds()[kind] - base.kinds()[kind]) * 60.0 / (latest.seconds() - base.seconds());
        return String.format(Locale.ROOT, "%,.1f", perMinute) + over(base, latest);
    }

    /** One bar a step on one axis of time from the job's start to this write (ADR-267 section 1, ADR-268 section 8). */
    private static String timeline(
            Map<String, String> states, Map<String, StepExecution> executions, Instant started, Instant now, ZoneId zone) {
        long total = Math.max(1000, Duration.between(started, now).toMillis());
        int plot = CHART_WIDTH - TIMELINE_LABELS - 20;
        int height = STEPS_IN_JOB_ORDER.size() * TIMELINE_ROW + 48;
        String title = "Timeline of the steps";
        StringBuilder svg = new StringBuilder(figureHead(
                title, "Each step's bar runs from its start to its end, or to this write while it runs, and is labelled with its state.", null));
        svg.append("<svg class=\"timeline\" role=\"img\" viewBox=\"0 0 ").append(CHART_WIDTH).append(' ').append(height)
                .append("\"><title>").append(title).append("</title>\n");
        int row = 0;
        for (String step : STEPS_IN_JOB_ORDER) {
            String state = states.get(step);
            int y = row++ * TIMELINE_ROW;
            svg.append("<g class=\"step\" data-step=\"").append(step).append("\" data-state=\"").append(state)
                    .append("\"><title>").append(ReportPage.escape(StepNames.description(step))).append("</title><text x=\"4\" y=\"")
                    .append(y + 16).append("\">").append(ReportPage.escape(step))
                    .append("</text><text class=\"state\" x=\"").append(TIMELINE_STATES).append("\" y=\"").append(y + 16)
                    .append("\">").append(ReportPage.escape(state)).append("</text>");
            StepExecution execution = executions.get(step);
            if (execution != null && execution.getStartTime() != null) {
                Instant start = execution.getStartTime().atZone(zone).toInstant();
                Instant end = execution.getEndTime() == null ? now : execution.getEndTime().atZone(zone).toInstant();
                long from = Math.clamp(Duration.between(started, start).toMillis(), 0, total);
                long to = Math.clamp(Duration.between(started, end).toMillis(), from, total);
                svg.append("<rect x=\"").append(f1(TIMELINE_LABELS + (double) from / total * plot))
                        .append("\" y=\"").append(y + 5).append("\" width=\"")
                        .append(f1(Math.max(2.0, (double) (to - from) / total * plot))).append("\" height=\"14\"><title>")
                        .append(ReportPage.escape(step + ": " + state + ", " + clockTime(Duration.between(start, end))))
                        .append("</title></rect>");
            }
            svg.append("</g>\n");
        }
        int axis = STEPS_IN_JOB_ORDER.size() * TIMELINE_ROW + 16;
        svg.append("<text class=\"tick\" x=\"").append(TIMELINE_LABELS).append("\" y=\"").append(axis).append("\">0:00:00</text>")
                .append("<text class=\"tick\" text-anchor=\"end\" x=\"").append(TIMELINE_LABELS + plot).append("\" y=\"")
                .append(axis).append("\">").append(clockTime(Duration.between(started, now))).append("</text>\n")
                .append("<text class=\"axis-title x\" text-anchor=\"middle\" x=\"").append(TIMELINE_LABELS + plot / 2)
                .append("\" y=\"").append(axis + 20).append("\">Elapsed (h:mm:ss)</text>\n</svg>\n</figure>\n");
        return svg.toString();
    }

    /** The database's size at each sample, over its own range (ADR-268 section 7). */
    private String databaseChart() {
        double[] values = samples.stream().mapToDouble(Sample::databaseBytes).toArray();
        double min = Arrays.stream(values).min().orElse(0);
        double most = Arrays.stream(values).max().orElse(0);
        // the values' own range with a twentieth of the span at each end, a span of at least a byte centred on a
        // constant value, and never below zero; no other floor (ADR-268 section 7)
        double span = Math.max(most - min, 1);
        double lo = Math.max(0, min - (span - (most - min)) / 2 - span * 0.05);
        double hi = most + (span - (most - min)) / 2 + span * 0.05;
        // the axis is written in the largest unit in which its span is at least 1
        int unit = 0;
        while (unit < SIZE_UNITS.length - 1 && (hi - lo) / Math.pow(1000, unit + 1) >= 1) {
            unit++;
        }
        double divisor = Math.pow(1000, unit);
        Axis axis = sizeAxis("Database size (" + SIZE_UNITS[unit] + ")", lo / divisor, hi / divisor, unit == 0);
        return chart(
                "database",
                "Database size over time",
                "The size of the database and its write-ahead log on disk at each sample.",
                samples.stream().map(Sample::seconds).toList(),
                List.of(new Line("s1", "", Arrays.stream(values).map(v -> v / divisor).toArray())),
                axis,
                v -> size(Math.round(v * divisor)));
    }

    /** The chart of counts of the latest step a sample names, from what that step says of itself first (section 7). */
    private String countsChart() {
        String step = null;
        for (Sample sample : samples) {
            if (sample.step() != null) {
                step = sample.step();
            }
        }
        if (step == null) {
            return "";
        }
        String named = step;
        List<Sample> ofStep = samples.stream().filter(s -> named.equals(s.step())).toList();
        String label = null;
        String total = null;
        for (Sample sample : ofStep) {
            Matcher counter = sample.progress() == null ? null : COUNTER.matcher(sample.progress());
            if (counter != null && counter.matches()) {
                label = counter.group(1);
                total = counter.group(3);
            }
        }
        if (label != null) {
            List<Long> at = new ArrayList<>();
            List<Double> done = new ArrayList<>();
            for (Sample sample : ofStep) {
                Matcher counter = sample.progress() == null ? null : COUNTER.matcher(sample.progress());
                if (counter != null && counter.matches() && counter.group(1).equals(label)) {
                    at.add(sample.seconds());
                    done.add(Double.parseDouble(counter.group(2).replace(",", "")));
                }
            }
            double wanted = total == null ? 0 : Double.parseDouble(total.replace(",", ""));
            return chart(
                    "counts",
                    label + " over time",
                    total == null
                            ? "How many it had done at each sample."
                            : "How many of " + total + " it had done at each sample.",
                    at,
                    List.of(new Line("s1", "done", done.stream().mapToDouble(Double::doubleValue).toArray())),
                    countAxis("Done (count)", Math.max(wanted, done.stream().mapToDouble(Double::doubleValue).max().orElse(0))),
                    v -> count(Math.round(v)));
        }
        if (ofStep.stream().anyMatch(s -> s.read() > 0 || s.written() > 0)) {
            double[] read = ofStep.stream().mapToDouble(Sample::read).toArray();
            double[] written = ofStep.stream().mapToDouble(Sample::written).toArray();
            double most = Math.max(Arrays.stream(read).max().orElse(0), Arrays.stream(written).max().orElse(0));
            return chart(
                    "counts",
                    "Read and written by " + step + " over time",
                    "The step's read count and write count at each sample.",
                    ofStep.stream().map(Sample::seconds).toList(),
                    List.of(new Line("s1", "read", read), new Line("s2", "written", written)),
                    countAxis("Items (count)", most),
                    v -> count(Math.round(v)));
        }
        return ReportPage.paragraph(ReportPage.escape(step + " reports no count to draw."));
    }

    /** The verdicts of the outcome step by kind at each sample, from zero; nothing where no step is the outcome step. */
    private String verdictsChart() {
        String step = samples.getLast().outcomeStep();
        if (step == null) {
            return "";
        }
        List<Line> lines = new ArrayList<>();
        double most = 0;
        for (String kind : OUTCOME_STEPS.get(step)) {
            int at = BLOCKING.indexOf(kind);
            double[] values = samples.stream().mapToDouble(s -> s.kinds()[at]).toArray();
            most = Math.max(most, Arrays.stream(values).max().orElse(0));
            lines.add(new Line("s" + (lines.size() + 1), kind, values));
        }
        return chart(
                "verdicts",
                "Verdicts recorded by " + step + " over time",
                "The verdicts " + step + " recorded in this invocation, by kind, at each sample.",
                samples.stream().map(Sample::seconds).toList(),
                lines,
                countAxis("Verdicts (count)", most),
                v -> count(Math.round(v)));
    }

    /** An axis of counts from zero to {@code most}, at least 1, with the top as a tick (ADR-268 section 7). */
    private static Axis countAxis(String title, double most) {
        double top = Math.max(1, most);
        double step = Math.max(1, niceStep(top));
        List<Double> ticks = new ArrayList<>(List.of(0.0));
        for (double tick = step; tick <= top * 0.88; tick += step) {
            ticks.add(tick);
        }
        ticks.add(top);
        return new Axis(title, 0, top, ticks, 0);
    }

    /** An axis over {@code lo} to {@code hi}, in the unit its title names, with round ticks. */
    private static Axis sizeAxis(String title, double lo, double hi, boolean wholeBytes) {
        double step = niceStep(hi - lo);
        if (wholeBytes) {
            step = Math.max(1, step % 1 == 0 ? step : step * 2); // 2.5 -> 5: bytes are whole
        }
        List<Double> ticks = new ArrayList<>();
        for (long k = (long) Math.ceil(lo / step - 1e-9); k * step <= hi + step * 1e-9; k++) {
            ticks.add(k * step);
        }
        int decimals = 0;
        while (decimals < 9 && Math.abs(step * Math.pow(10, decimals) - Math.rint(step * Math.pow(10, decimals))) > 1e-6) {
            decimals++;
        }
        return new Axis(title, lo, hi, ticks, decimals);
    }

    /** A round step (1, 2, 2.5 or 5 times a power of ten) that puts at most five ticks on {@code range}. */
    private static double niceStep(double range) {
        double raw = range / 4;
        double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
        for (double multiple : new double[] {1, 2, 2.5, 5}) {
            if (multiple * magnitude >= raw * (1 - 1e-9)) {
                return multiple * magnitude;
            }
        }
        return 10 * magnitude;
    }

    /** What opens a card: its heading, its caption and, for two lines or more, its legend (ADR-268 section 7). */
    private static String figureHead(String title, String caption, List<Line> lines) {
        StringBuilder head = new StringBuilder("<figure class=\"chart-card\">\n<h2>" + ReportPage.escape(title) + "</h2>\n"
                + "<p class=\"caption\">" + ReportPage.escape(caption) + "</p>\n");
        if (lines != null && lines.size() >= 2) {
            head.append("<ul class=\"legend\">");
            for (Line line : lines) {
                head.append("<li><span class=\"key ").append(line.css()).append("\"></span>")
                        .append(ReportPage.escape(line.name())).append("</li>");
            }
            head.append("</ul>\n");
        }
        return head.toString();
    }

    /**
     * A line chart over time (ADR-268 section 7): a heading and a caption, a legend for two lines or more, one value
     * axis and a time axis, each titled, a hairline gridline at each tick, a line a series, a transparent circle a
     * sample as its tooltip and one visible marker at the latest, and an end label in the text ink.
     */
    private static String chart(
            String key, String title, String caption, List<Long> seconds, List<Line> lines, Axis axis, DoubleFunction<String> show) {
        long from = seconds.getFirst();
        long span = Math.max(1, seconds.getLast() - from);
        List<String> tickTexts = new ArrayList<>();
        for (double tick : axis.ticks()) {
            tickTexts.add(String.format(Locale.ROOT, "%,." + axis.decimals() + "f", tick + 0.0));
        }
        int longest = tickTexts.stream().mapToInt(String::length).max().orElse(1);
        int left = Math.max(56, 8 * longest + 8);
        int bottom = PLOT_TOP + PLOT_HEIGHT;
        // end labels: the text each line ends in, and two within 14 units of each other as one label holding both
        List<String> endTexts = new ArrayList<>();
        double[] endY = new double[lines.size()];
        for (int s = 0; s < lines.size(); s++) {
            Line line = lines.get(s);
            double last = line.values()[line.values().length - 1];
            endY[s] = bottom - (last - axis.lo()) / (axis.hi() - axis.lo()) * PLOT_HEIGHT;
            endTexts.add((line.name().isEmpty() || lines.size() == 1 ? "" : line.name() + " ") + show.apply(last));
        }
        List<Integer> byHeight = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            byHeight.add(i);
        }
        byHeight.sort((a, b) -> Double.compare(endY[a], endY[b]));
        List<List<Integer>> groups = new ArrayList<>();
        int longestLabel = 0;
        for (int index : byHeight) {
            List<Integer> group = groups.isEmpty() ? null : groups.getLast();
            if (group != null && endY[index] - endY[group.getLast()] <= 14) {
                group.add(index);
            } else {
                groups.add(new ArrayList<>(List.of(index)));
            }
        }
        List<String> labels = new ArrayList<>();
        for (List<Integer> group : groups) {
            group.sort(null);
            List<String> texts = new ArrayList<>();
            for (int index : group) {
                texts.add(endTexts.get(index));
            }
            labels.add(String.join(", ", texts));
            longestLabel = Math.max(longestLabel, labels.getLast().length());
        }
        // the plot leaves eight units for each character of the longest end label, and ends there (ADR-268 section 7)
        int plotRight = CHART_WIDTH - 12 - 8 * longestLabel;
        int plot = plotRight - left;
        StringBuilder svg = new StringBuilder(figureHead(title, caption, lines));
        svg.append("<svg class=\"chart\" data-chart=\"").append(key).append("\" role=\"img\" viewBox=\"0 0 ")
                .append(CHART_WIDTH).append(' ').append(CHART_HEIGHT).append("\"><title>")
                .append(ReportPage.escape(title)).append("</title>\n");
        svg.append("<text class=\"axis-title y\" x=\"").append(left).append("\" y=\"16\">")
                .append(ReportPage.escape(axis.title())).append("</text>\n");
        for (int i = 0; i < axis.ticks().size(); i++) {
            double y = bottom - (axis.ticks().get(i) - axis.lo()) / (axis.hi() - axis.lo()) * PLOT_HEIGHT;
            svg.append("<line class=\"grid\" x1=\"").append(left).append("\" y1=\"").append(f1(y)).append("\" x2=\"")
                    .append(plotRight).append("\" y2=\"").append(f1(y)).append("\"/><text class=\"tick value\" text-anchor=\"end\" x=\"")
                    .append(left - 6).append("\" y=\"").append(f1(y + 4)).append("\">")
                    .append(ReportPage.escape(tickTexts.get(i))).append("</text>\n");
        }
        svg.append("<line class=\"axis\" x1=\"").append(left).append("\" y1=\"").append(PLOT_TOP).append("\" x2=\"")
                .append(left).append("\" y2=\"").append(bottom).append("\"/><line class=\"axis\" x1=\"").append(left)
                .append("\" y1=\"").append(bottom).append("\" x2=\"").append(plotRight).append("\" y2=\"")
                .append(bottom).append("\"/>\n");
        java.util.Set<Long> timeTicks = new java.util.TreeSet<>();
        for (int i = 0; i <= 4; i++) {
            timeTicks.add(from + Math.round(span * i / 4.0));
        }
        for (long tick : timeTicks) {
            svg.append("<text class=\"tick time\" text-anchor=\"middle\" x=\"")
                    .append(f1(left + (double) (tick - from) / span * plot)).append("\" y=\"").append(bottom + 18).append("\">")
                    .append(clockTime(Duration.ofSeconds(tick))).append("</text>");
        }
        svg.append("\n<text class=\"axis-title x\" text-anchor=\"middle\" x=\"").append(left + plot / 2).append("\" y=\"")
                .append(bottom + 40).append("\">Elapsed (h:mm:ss)</text>\n");
        double endX = left;
        for (int s = 0; s < lines.size(); s++) {
            Line line = lines.get(s);
            StringBuilder points = new StringBuilder();
            StringBuilder circles = new StringBuilder();
            double x = left;
            double y = bottom;
            for (int i = 0; i < seconds.size(); i++) {
                x = left + (double) (seconds.get(i) - from) / span * plot;
                y = bottom - (line.values()[i] - axis.lo()) / (axis.hi() - axis.lo()) * PLOT_HEIGHT;
                points.append(points.isEmpty() ? "" : " ").append(f1(x)).append(',').append(f1(y));
                String at = clockTime(Duration.ofSeconds(seconds.get(i)));
                String shown = ReportPage.escape(show.apply(line.values()[i]));
                // the database chart's one series has no legend or end label, and is named size (ADR-269 section 4)
                circles.append("<circle class=\"hit\" cx=\"").append(f1(x)).append("\" cy=\"").append(f1(y))
                        .append("\" r=\"12\" fill=\"transparent\" data-series=\"")
                        .append(ReportPage.escape(line.name().isEmpty() ? "size" : line.name()))
                        .append("\" data-at=\"").append(at).append("\" data-value=\"").append(shown)
                        .append("\"><title>").append(at).append(": ").append(shown).append("</title></circle>");
            }
            svg.append("<polyline class=\"line ").append(line.css()).append("\" fill=\"none\" stroke-width=\"2\""
                    + " stroke-linejoin=\"round\" stroke-linecap=\"round\"")
                    .append(s == 0 ? "" : " stroke-dasharray=\"" + DASHES[Math.min(s, DASHES.length - 1)] + "\"")
                    .append(" points=\"").append(points).append("\"/>").append(circles)
                    .append("<circle class=\"end ").append(line.css()).append("\" cx=\"").append(f1(x)).append("\" cy=\"")
                    .append(f1(y)).append("\" r=\"4\" stroke-width=\"2\"/>\n");
            endX = x;
        }
        for (int g = 0; g < groups.size(); g++) {
            svg.append("<text class=\"end-label\" x=\"").append(f1(endX + 10)).append("\" y=\"")
                    .append(f1(endY[groups.get(g).getFirst()] + 4)).append("\">")
                    .append(ReportPage.escape(labels.get(g))).append("</text>\n");
        }
        return svg.append("</svg>\n</figure>\n").toString();
    }

    /** The page of a command that starts no job (ADR-266 section 3): numbers and fixed words, nothing it was given. */
    private String renderCommand(boolean last, String outcome, Map<String, Integer> counts) {
        Instant now = clock.instant();
        int seconds = (int) Math.max(1, (interval.toMillis() + 999) / 1000);
        String startedAt = WRITTEN_AT.format(LocalDateTime.ofInstant(commandStarted, ZoneId.systemDefault()));
        String status = last
                ? "This invocation has ended: " + outcome + ". It started at " + startedAt + " and ended at "
                        + WRITTEN_AT.format(LocalDateTime.ofInstant(now, ZoneId.systemDefault())) + "."
                : "This invocation is running. It started at " + startedAt + ", and this page is rewritten when it ends.";
        StringBuilder rows = new StringBuilder();
        counts.forEach((words, value) ->
                rows.append(ReportPage.row(ReportPage.textCell(words), ReportPage.numberCell(count(value)))));
        String body = ReportPage.heading(1, TITLE)
                + ReportPage.paragraph(ReportPage.escape(status))
                + ReportPage.paragraph("Written at " + WRITTEN_AT.format(LocalDateTime.ofInstant(now, ZoneId.systemDefault())))
                + "<div class=\"tiles\" title=\"" + ReportPage.escapeAttribute(INVOCATION_TILES_HOVER) + "\">\n"
                + tile("command", "Command", command, null)
                + tile("elapsed", "Elapsed", clockTime(Duration.between(commandStarted, now)), null)
                + tile("database", "Database size", size(databaseBytes()), null)
                + "</div>\n"
                + (counts.isEmpty() ? "" : ReportPage.tableCard(
                "How many answers this invocation recorded, by kind.", ReportPage.headerRow("Answers", "Count"), rows.toString()));
        return last ? ReportPage.drawn(TITLE, body) : ReportPage.drawnReloading(TITLE, body, seconds);
    }

    private static boolean anExecutionAfter(String step, Map<String, StepExecution> executions) {
        return STEPS_IN_JOB_ORDER.subList(STEPS_IN_JOB_ORDER.indexOf(step) + 1, STEPS_IN_JOB_ORDER.size()).stream()
                .anyMatch(executions::containsKey);
    }

    private static String stateOf(StepExecution execution, boolean shutAtAGate, boolean inside) {
        if (execution == null) {
            return "not started";
        }
        BatchStatus status = execution.getStatus();
        return status == BatchStatus.STARTING || status == BatchStatus.STARTED || inside
                ? "running"
                : status == BatchStatus.COMPLETED ? (shutAtAGate ? "stopped at a gate" : "finished") : "failed";
    }

    private String row(String step, StepExecution execution, String state, Instant now, ZoneId zone) {
        if (execution == null) {
            return ReportPage.row(
                    ReportPage.textCell(step),
                    ReportPage.textCell(state),
                    ReportPage.numberCell(""),
                    ReportPage.numberCell(""),
                    ReportPage.numberCell(""),
                    ReportPage.textCell(""),
                    ReportPage.textCell(""));
        }
        String duration = "";
        if (execution.getStartTime() != null) {
            Instant start = execution.getStartTime().atZone(zone).toInstant();
            Instant end = execution.getEndTime() == null ? now : execution.getEndTime().atZone(zone).toInstant();
            duration = clockTime(Duration.between(start, end));
        }
        return ReportPage.row(
                ReportPage.textCell(step),
                ReportPage.textCell(state),
                ReportPage.numberCell(count(execution.getReadCount())),
                ReportPage.numberCell(count(execution.getWriteCount())),
                ReportPage.numberCell(count(execution.getSkipCount())),
                ReportPage.textCell(duration),
                ReportPage.textCell(lastLine.getOrDefault(step, "")));
    }

    private static String clockTime(Duration duration) {
        long seconds = Math.max(0, duration.getSeconds());
        return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    /** The database file and its write-ahead log, each read by a file system call and never by a query. */
    private long databaseBytes() {
        long bytes = 0;
        for (String name : List.of(DATABASE_FILE, WRITE_AHEAD_LOG_FILE)) {
            try {
                bytes += Files.size(workingDirectory.resolve(name));
            } catch (NoSuchFileException absent) {
                // the log is there only while the database is open
            } catch (IOException unreadable) {
                // a size that cannot be read counts for nothing
            }
        }
        return bytes;
    }

    /**
     * Keeps, for the step running on the thread that logged, the last line whose whole text is a counter's or a
     * statement's. It logs nothing and takes no lock the writer holds.
     */
    private final class LineAppender extends AppenderBase<ILoggingEvent> {

        @Override
        protected void append(ILoggingEvent event) {
            String message = event.getFormattedMessage();
            JobExecution job = jobExecution;
            if (message == null
                    || job == null
                    || !(StageProgress.COUNTER_LINE.matcher(message).matches()
                            || STATEMENT_LINE.matcher(message).matches())) {
                return;
            }
            // The step bound to this thread; a step's own listener runs after its status is COMPLETED, and a
            // line it logs there is still the step's, so the step is held as inside until a later one starts.
            StepContext context = StepSynchronizationManager.getContext();
            if (context == null) {
                return;
            }
            StepExecution execution = context.getStepExecution();
            lastLine.put(execution.getStepName(), message);
            if (execution.getStatus() != BatchStatus.STARTED && execution.getStatus() != BatchStatus.STARTING) {
                stillInside.add(execution.getStepName());
            }
        }
    }
}
