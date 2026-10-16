package io.algernon.vespera.pipeline;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.algernon.vespera.ledger.ReplacingMove;
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

    /** The one script of a running page: the same text on every write, reading two attributes of the mark. */
    private static final String LIVENESS_SCRIPT =
            "document.querySelectorAll('.liveness[data-written]').forEach(function (mark) {\n"
            + "  var behind = Date.now() - Number(mark.dataset.written);\n"
            + "  if (behind > " + STALE_AFTER_INTERVALS + " * Number(mark.dataset.interval)) {\n"
            + "    mark.className = 'liveness stale';\n"
            + "    mark.textContent = 'not rewritten for ' + Math.floor(behind / 1000) + ' s';\n"
            + "  }\n"
            + "});";

    private static final Pattern COUNT_OF_TOTAL = Pattern.compile(": ([0-9,]+) of ([0-9,]+) \\([0-9]+%\\)$");

    /** A counter's line: its label, how far it has got and, where it has one, its total. */
    private static final Pattern COUNTER = Pattern.compile(
            "^(" + StageProgress.LABEL_SHAPE + "): ([0-9,]+)(?: of ([0-9,]+) \\([0-9]+%\\)| so far)$");

    private static final int CHART_WIDTH = 1100;
    private static final int CHART_HEIGHT = 260;
    private static final int PLOT_TOP = 36;
    private static final int PLOT_HEIGHT = 168;
    private static final int PLOT_RIGHT = 740;
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
            StepNames.RELEVANCE_SCORING,
            StepNames.RELEVANCE_FLOOR,
            StepNames.CLUSTERING,
            StepNames.RELEVANCE_REPORT,
            StepNames.ARRANGEMENT,
            StepNames.GENERATION);

    /**
     * The blocking kinds as {@code CONTEXT.md} writes them, in {@code VerdictKind}'s order (ADR-268 section 1). Names,
     * not the enum: the page decides no verdict, and no class of {@code pipeline} that does not is to name a kind.
     */
    private static final List<String> BLOCKING = List.of(
            "broken", "duplicate-of", "superseded-by", "out-of-scope",
            "extraction-failed", "degenerate-output", "redundant-with", "below-threshold");

    /** The steps that remove occurrences, in job order, and the kinds each records (ADR-268 section 1). */
    private static final Map<String, List<String>> OUTCOME_STEPS = new LinkedHashMap<>();

    static {
        OUTCOME_STEPS.put(
                StepNames.BYTE_LEVEL_REDUCTION, List.of("broken", "duplicate-of", "superseded-by", "out-of-scope"));
        OUTCOME_STEPS.put(StepNames.EXTRACTION, List.of("extraction-failed", "degenerate-output"));
        OUTCOME_STEPS.put(StepNames.CONTENT_REDUNDANCY, List.of("redundant-with"));
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
        this.workingDirectory = workingDirectory.toAbsolutePath().normalize();
        this.page = this.workingDirectory.resolve(FILE_NAME);
        this.interval = interval;
        this.clock = clock;
        this.opener = opener;
        this.recorded = recorded;
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
                String[] pages = render(last); // steps, samples, progress: the order they are moved into place
                put(STEPS_FILE_NAME, pages[0]);
                put(SAMPLES_FILE_NAME, pages[1]);
                put(FILE_NAME, pages[2]);
            } else {
                put(FILE_NAME, renderCommand(last, outcome, counts));
            }
            warned = false;
            return true;
        } catch (IOException | RuntimeException e) {
            for (String name : List.of(STEPS_FILE_NAME, SAMPLES_FILE_NAME, FILE_NAME)) {
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
        Instant started = job.getStartTime() == null ? now : job.getStartTime().atZone(zone).toInstant();
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
                survivors -=kinds[BLOCKING.indexOf(kind)];
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

        String status = last
                ? "This invocation has ended: " + job.getStatus().name().toLowerCase(Locale.ROOT) + "."
                : "This invocation is running. This page is rewritten every " + seconds + " s.";
        String writtenAt = "Written at " + WRITTEN_AT.format(LocalDateTime.ofInstant(now, zone));
        String head = ReportPage.paragraph(ReportPage.escape(status));
        String detailHead = head
                + ReportPage.paragraph(writtenAt)
                + ReportPage.paragraph("<a href=\"" + FILE_NAME + "\">Back to the progress page</a>");

        String stepsBody = ReportPage.heading(1, TITLE + ": steps")
                + detailHead
                + ReportPage.table(
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
        String samplesBody = ReportPage.heading(1, TITLE + ": samples")
                + detailHead
                + ReportPage.table(ReportPage.headerRow(sampleHeaders), sampleRows.toString());

        String mark = last
                ? "<span class=\"liveness\">ended</span> "
                : "<span class=\"liveness\" data-written=\"" + now.toEpochMilli() + "\" data-interval=\""
                        + interval.toMillis() + "\">live</span> ";
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
                    "survivors", "Survivors", count(survivors), "of " + count(entered) + " when " + outcomeStep + " started"));
            for (String kind : OUTCOME_STEPS.get(outcomeStep)) {
                int at = BLOCKING.indexOf(kind);
                tiles.append(tile(kind, kind,count(latest.kinds()[at]), kindRate(base, latest, at)));
            }
        }
        String progressBody = ReportPage.heading(1, TITLE)
                + head
                + ReportPage.paragraph(mark + writtenAt)
                + "<div class=\"tiles\">\n" + tiles + "</div>\n"
                + bar
                + timeline(states, executions, started, now, zone)
                + databaseChart()
                + countsChart()
                + verdictsChart()
                + ReportPage.paragraph("<a href=\"" + STEPS_FILE_NAME + "\">The steps as a table</a> &middot; <a href=\""
                        + SAMPLES_FILE_NAME + "\">The samples as a table</a>")
                + (last ? "" : "<script>\n" + LIVENESS_SCRIPT + "\n</script>\n");
        return new String[] {
            page("steps", stepsBody, last, seconds), page("samples", samplesBody, last, seconds),
            page("", progressBody, last, seconds)
        };
    }

    private static String page(String which, String body, boolean last, int seconds) {
        return last ? ReportPage.drawn(titled(which), body) : ReportPage.drawnReloading(titled(which), body, seconds);
    }

    private static String titled(String which) {
        return which.isEmpty() ? TITLE : TITLE + ": " + which;
    }

    /** A tile: a label, a value and, where it has one, a detail, each text and no other element (ADR-268 section 5). */
    private static String tile(String key, String label, String value, String detail) {
        return "<div class=\"tile\" data-tile=\"" + key + "\"><span class=\"label\">" + ReportPage.escape(label)
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
                    .append("\"><text x=\"4\" y=\"").append(y + 16).append("\">").append(ReportPage.escape(step))
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
        int unit = unitOf(most);
        double divisor = Math.pow(1000, unit);
        double margin = Math.max(most - min, 1) * 0.05;
        Axis axis = sizeAxis("Database size (" + SIZE_UNITS[unit] + ")", (min - margin) / divisor, (most + margin) / divisor);
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
    private static Axis sizeAxis(String title, double lo, double hi) {
        double step = niceStep(hi - lo);
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
        int plot = PLOT_RIGHT - left;
        int bottom = PLOT_TOP + PLOT_HEIGHT;
        StringBuilder svg = new StringBuilder(figureHead(title, caption, lines));
        svg.append("<svg class=\"chart\" data-chart=\"").append(key).append("\" role=\"img\" viewBox=\"0 0 ")
                .append(CHART_WIDTH).append(' ').append(CHART_HEIGHT).append("\"><title>")
                .append(ReportPage.escape(title)).append("</title>\n");
        svg.append("<text class=\"axis-title y\" x=\"").append(left).append("\" y=\"16\">")
                .append(ReportPage.escape(axis.title())).append("</text>\n");
        for (int i = 0; i < axis.ticks().size(); i++) {
            double y = bottom - (axis.ticks().get(i) - axis.lo()) / (axis.hi() - axis.lo()) * PLOT_HEIGHT;
            svg.append("<line class=\"grid\" x1=\"").append(left).append("\" y1=\"").append(f1(y)).append("\" x2=\"")
                    .append(PLOT_RIGHT).append("\" y2=\"").append(f1(y)).append("\"/><text class=\"tick value\" text-anchor=\"end\" x=\"")
                    .append(left - 6).append("\" y=\"").append(f1(y + 4)).append("\">")
                    .append(ReportPage.escape(tickTexts.get(i))).append("</text>\n");
        }
        svg.append("<line class=\"axis\" x1=\"").append(left).append("\" y1=\"").append(PLOT_TOP).append("\" x2=\"")
                .append(left).append("\" y2=\"").append(bottom).append("\"/><line class=\"axis\" x1=\"").append(left)
                .append("\" y1=\"").append(bottom).append("\" x2=\"").append(PLOT_RIGHT).append("\" y2=\"")
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
        List<double[]> ends = new ArrayList<>();
        List<String> endTexts = new ArrayList<>();
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
                circles.append("<circle class=\"hit\" cx=\"").append(f1(x)).append("\" cy=\"").append(f1(y))
                        .append("\" r=\"12\" fill=\"transparent\"><title>")
                        .append(clockTime(Duration.ofSeconds(seconds.get(i)))).append(": ")
                        .append(ReportPage.escape(show.apply(line.values()[i]))).append("</title></circle>");
            }
            svg.append("<polyline class=\"line ").append(line.css()).append("\" fill=\"none\" stroke-width=\"2\""
                    + " stroke-linejoin=\"round\" stroke-linecap=\"round\"")
                    .append(s == 0 ? "" : " stroke-dasharray=\"" + DASHES[Math.min(s, DASHES.length - 1)] + "\"")
                    .append(" points=\"").append(points).append("\"/>").append(circles)
                    .append("<circle class=\"end ").append(line.css()).append("\" cx=\"").append(f1(x)).append("\" cy=\"")
                    .append(f1(y)).append("\" r=\"4\" stroke-width=\"2\"/>\n");
            ends.add(new double[] {x, y});
            endTexts.add((line.name().isEmpty() || lines.size() == 1 ? "" : line.name() + " ")
                    + show.apply(line.values()[line.values().length - 1]));
        }
        // end labels in the text ink; two within 14 units of each other are one label holding both
        List<Integer> byHeight = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            byHeight.add(i);
        }
        byHeight.sort((a, b) -> Double.compare(ends.get(a)[1], ends.get(b)[1]));
        List<List<Integer>> groups = new ArrayList<>();
        for (int index : byHeight) {
            List<Integer> group = groups.isEmpty() ? null : groups.getLast();
            if (group != null && ends.get(index)[1] - ends.get(group.getLast())[1] <= 14) {
                group.add(index);
            } else {
                groups.add(new ArrayList<>(List.of(index)));
            }
        }
        for (List<Integer> group : groups) {
            group.sort(null);
            List<String> texts = new ArrayList<>();
            for (int index : group) {
                texts.add(endTexts.get(index));
            }
            svg.append("<text class=\"end-label\" x=\"").append(f1(ends.get(group.getFirst())[0] + 10)).append("\" y=\"")
                    .append(f1(ends.get(group.getFirst())[1] + 4)).append("\">")
                    .append(ReportPage.escape(String.join(", ", texts))).append("</text>\n");
        }
        return svg.append("</svg>\n</figure>\n").toString();
    }

    /** The page of a command that starts no job (ADR-266 section 3): numbers and fixed words, nothing it was given. */
    private String renderCommand(boolean last, String outcome, Map<String, Integer> counts) {
        Instant now = clock.instant();
        int seconds = (int) Math.max(1, (interval.toMillis() + 999) / 1000);
        String status = last
                ? "This invocation has ended: " + outcome + "."
                : "This invocation is running. This page is rewritten when it ends.";
        StringBuilder rows = new StringBuilder();
        counts.forEach((words, value) ->
                rows.append(ReportPage.row(ReportPage.textCell(words), ReportPage.numberCell(count(value)))));
        String body = ReportPage.heading(1, TITLE)
                + ReportPage.paragraph(ReportPage.escape(status))
                + ReportPage.paragraph("Written at " + WRITTEN_AT.format(LocalDateTime.ofInstant(now, ZoneId.systemDefault())))
                + "<div class=\"tiles\">\n"
                + tile("command", "Command", command, null)
                + tile("elapsed", "Elapsed", clockTime(Duration.between(commandStarted, now)), null)
                + tile("database", "Database size", size(databaseBytes()), null)
                + "</div>\n"
                + (counts.isEmpty() ? "" : ReportPage.table(ReportPage.headerRow("Answers", "Count"), rows.toString()));
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
