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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
 * counts and last progress line, the time elapsed and the database's size. It reads only what the job holds in
 * memory and two file sizes, and names no path, title, document text, exception message or run id.
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

    private static final String TITLE = "Vespera progress";

    /** At most this many samples are held; one more thins them (ADR-267 section 2). */
    private static final int MOST_SAMPLES = 256;

    /** A page is stale when it was last rewritten more than this many intervals ago (ADR-267 section 5). */
    private static final int STALE_AFTER_INTERVALS = 3;

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

    private static final int PLOT_LEFT = 60;
    private static final int PLOT_WIDTH = 700;
    private static final int CHART_WIDTH = 900;
    private static final int CHART_HEIGHT = 220;
    private static final int PLOT_TOP = 16;
    private static final int PLOT_HEIGHT = 170;
    private static final int TIMELINE_LABELS = 190;
    private static final int TIMELINE_ROW = 20;

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

    /** One reading made at a write (ADR-267 section 2). */
    private record Sample(long seconds, long databaseBytes, String step, long read, long written) {}

    /** Guarded by {@link #writing}: at most {@link #MOST_SAMPLES}, oldest first. */
    private final List<Sample> samples = new ArrayList<>();

    /** A line of a chart: the class its colour comes from, what it is called, and how a value of it is shown. */
    private record Series(String css, String name, boolean whole, String unit, java.util.function.ToDoubleFunction<Sample> value) {

        String show(double v) {
            return (whole ? Long.toString((long) v) : f1(v)) + unit;
        }
    }

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
        this.workingDirectory = workingDirectory.toAbsolutePath().normalize();
        this.page = this.workingDirectory.resolve(FILE_NAME);
        this.interval = interval;
        this.clock = clock;
        this.opener = opener;
    }

    /** The page's absolute, normalised path in {@code workingDirectory} (ADR-266 section 1). */
    static Path pathIn(Path workingDirectory) {
        return workingDirectory.toAbsolutePath().normalize().resolve(FILE_NAME);
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
        Map<String, String> states = new java.util.LinkedHashMap<>();
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
        String stepTile = shown == null ? "Step: none yet" : "Step: " + shown + " (" + states.get(shown) + ")";
        Duration elapsed = Duration.between(started, now);
        long bytes = databaseBytes();
        StepExecution runningExecution = running == null ? null : executions.get(running);
        samples.add(new Sample(
                Math.max(0, elapsed.getSeconds()),
                bytes,
                running,
                runningExecution == null ? 0 : runningExecution.getReadCount(),
                runningExecution == null ? 0 : runningExecution.getWriteCount()));
        if (samples.size() > MOST_SAMPLES) {
            List<Sample> kept = new ArrayList<>();
            for (int i = 0; i < samples.size(); i += 2) {
                kept.add(samples.get(i));
            }
            samples.clear();
            samples.addAll(kept);
        }

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
            sampleRows.append(ReportPage.row(
                    ReportPage.textCell(clockTime(Duration.ofSeconds(sample.seconds()))),
                    ReportPage.textCell(megabytes(sample.databaseBytes())),
                    ReportPage.textCell(sample.step() == null ? "" : sample.step()),
                    ReportPage.numberCell(sample.step() == null ? "" : Long.toString(sample.read())),
                    ReportPage.numberCell(sample.step() == null ? "" : Long.toString(sample.written()))));
        }
        String samplesBody = ReportPage.heading(1, TITLE + ": samples")
                + detailHead
                + ReportPage.table(
                        ReportPage.headerRow("Elapsed", "Database size", "Step", "Read", "Written"),
                        sampleRows.toString());

        String mark = last
                ? "<span class=\"liveness\">ended</span> "
                : "<span class=\"liveness\" data-written=\"" + now.toEpochMilli() + "\" data-interval=\""
                        + interval.toMillis() + "\">live</span> ";
        String bar = "";
        if (runningExecution != null) {
            Matcher count = COUNT_OF_TOTAL.matcher(lastLine.getOrDefault(running, ""));
            if (count.find() && !count.group(2).replace(",", "").matches("0+")) {
                bar = ReportPage.paragraph("<progress value=\"" + count.group(1).replace(",", "") + "\" max=\""
                        + count.group(2).replace(",", "") + "\"></progress> "
                        + ReportPage.escape(lastLine.get(running)));
            }
        }
        String progressBody = ReportPage.heading(1, TITLE)
                + head
                + ReportPage.paragraph(mark + writtenAt)
                + tiles(
                        tile("steps", "Steps finished: " + finished + " of " + STEPS_IN_JOB_ORDER.size()),
                        tile("step", stepTile),
                        tile("elapsed", "Elapsed: " + clockTime(elapsed)),
                        tile("database", "Database size: " + megabytes(bytes)))
                + bar
                + timeline(states, executions, started, now, zone)
                + databaseChart()
                + countsChart()
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

    private static String tile(String key, String text) {
        return "<div class=\"tile\" data-tile=\"" + key + "\">" + ReportPage.escape(text) + "</div>\n";
    }

    private static String tiles(String... tiles) {
        return "<div class=\"tiles\">\n" + String.join("", tiles) + "</div>\n";
    }

    private static String f1(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String megabytes(long bytes) {
        return f1(bytes / 1_000_000.0) + " MB";
    }

    /** One bar a step on one axis of time from the job's start to this write (ADR-267 section 1). */
    private static String timeline(
            Map<String, String> states, Map<String, StepExecution> executions, Instant started, Instant now, ZoneId zone) {
        long total = Math.max(1000, Duration.between(started, now).toMillis());
        int plot = CHART_WIDTH - TIMELINE_LABELS - 10;
        int height = STEPS_IN_JOB_ORDER.size() * TIMELINE_ROW + 24;
        StringBuilder svg = new StringBuilder("<svg class=\"timeline\" role=\"img\" viewBox=\"0 0 " + CHART_WIDTH + " "
                + height + "\"><title>Timeline of the steps, from the job's start to this write</title>\n");
        int row = 0;
        for (String step : STEPS_IN_JOB_ORDER) {
            String state = states.get(step);
            int y = row++ * TIMELINE_ROW;
            svg.append("<g class=\"step\" data-step=\"").append(step).append("\" data-state=\"").append(state)
                    .append("\"><text x=\"4\" y=\"").append(y + 14).append("\">").append(ReportPage.escape(step))
                    .append("</text>");
            StepExecution execution = executions.get(step);
            if (execution != null && execution.getStartTime() != null) {
                Instant start = execution.getStartTime().atZone(zone).toInstant();
                Instant end = execution.getEndTime() == null ? now : execution.getEndTime().atZone(zone).toInstant();
                long from = Math.clamp(Duration.between(started, start).toMillis(), 0, total);
                long to = Math.clamp(Duration.between(started, end).toMillis(), from, total);
                svg.append("<rect x=\"").append(f1(TIMELINE_LABELS + (double) from / total * plot))
                        .append("\" y=\"").append(y + 3).append("\" width=\"")
                        .append(f1(Math.max(2.0, (double) (to - from) / total * plot))).append("\" height=\"14\"><title>")
                        .append(ReportPage.escape(step + ": " + state + ", " + clockTime(Duration.between(start, end))))
                        .append("</title></rect>");
            }
            svg.append("</g>\n");
        }
        int axis = STEPS_IN_JOB_ORDER.size() * TIMELINE_ROW + 16;
        svg.append("<text class=\"tick\" x=\"").append(TIMELINE_LABELS).append("\" y=\"").append(axis).append("\">0:00:00</text>")
                .append("<text class=\"tick\" text-anchor=\"end\" x=\"").append(TIMELINE_LABELS + plot).append("\" y=\"")
                .append(axis).append("\">").append(clockTime(Duration.between(started, now))).append("</text>\n</svg>\n");
        return svg.toString();
    }

    private String databaseChart() {
        return chart("database", "Database size over time, in MB", samples,
                List.of(new Series("s1", "", false, " MB", s -> s.databaseBytes() / 1_000_000.0)));
    }

    /** The read and write counts at each sample taken while the latest named step ran; nothing before any names one. */
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
        return chart("counts", "Read and written by " + step + " over time",
                samples.stream().filter(s -> named.equals(s.step())).toList(),
                List.of(new Series("s1", "read", true, "", Sample::read), new Series("s2", "written", true, "", Sample::written)));
    }

    /** A line chart of the samples shown (ADR-267 section 1): a line, a titled point for each, the latest value at its end. */
    private static String chart(String key, String title, List<Sample> shown, List<Series> series) {
        long from = shown.getFirst().seconds();
        long span = Math.max(1, shown.getLast().seconds() - from);
        double most = 0;
        for (Series one : series) {
            for (Sample sample : shown) {
                most = Math.max(most, one.value().applyAsDouble(sample));
            }
        }
        double top = most <= 0 ? 1 : most;
        int bottom = PLOT_TOP + PLOT_HEIGHT;
        StringBuilder svg = new StringBuilder("<svg class=\"chart\" data-chart=\"" + key + "\" role=\"img\" viewBox=\"0 0 "
                + CHART_WIDTH + " " + CHART_HEIGHT + "\"><title>" + ReportPage.escape(title) + "</title>\n");
        svg.append("<line class=\"axis\" x1=\"").append(PLOT_LEFT).append("\" y1=\"").append(PLOT_TOP).append("\" x2=\"")
                .append(PLOT_LEFT).append("\" y2=\"").append(bottom).append("\"/><line class=\"axis\" x1=\"").append(PLOT_LEFT)
                .append("\" y1=\"").append(bottom).append("\" x2=\"").append(PLOT_LEFT + PLOT_WIDTH).append("\" y2=\"")
                .append(bottom).append("\"/>\n");
        Series first = series.getFirst();
        svg.append("<text class=\"tick\" text-anchor=\"end\" x=\"").append(PLOT_LEFT - 6).append("\" y=\"").append(PLOT_TOP + 4)
                .append("\">").append(ReportPage.escape(first.show(top))).append("</text>")
                .append("<text class=\"tick\" text-anchor=\"end\" x=\"").append(PLOT_LEFT - 6).append("\" y=\"").append(bottom)
                .append("\">0</text>")
                .append("<text class=\"tick\" x=\"").append(PLOT_LEFT).append("\" y=\"").append(bottom + 18).append("\">")
                .append(clockTime(Duration.ofSeconds(from))).append("</text>")
                .append("<text class=\"tick\" text-anchor=\"end\" x=\"").append(PLOT_LEFT + PLOT_WIDTH).append("\" y=\"")
                .append(bottom + 18).append("\">").append(clockTime(Duration.ofSeconds(shown.getLast().seconds()))).append("</text>\n");
        double previousLabelY = Double.NEGATIVE_INFINITY;
        for (Series one : series) {
            StringBuilder points = new StringBuilder();
            StringBuilder circles = new StringBuilder();
            double x = PLOT_LEFT;
            double y = bottom;
            double latest = 0;
            for (Sample sample : shown) {
                latest = one.value().applyAsDouble(sample);
                x = PLOT_LEFT + (double) (sample.seconds() - from) / span * PLOT_WIDTH;
                y = bottom - latest / top * PLOT_HEIGHT;
                points.append(points.isEmpty() ? "" : " ").append(f1(x)).append(',').append(f1(y));
                circles.append("<circle class=\"point ").append(one.css()).append("\" cx=\"").append(f1(x)).append("\" cy=\"")
                        .append(f1(y)).append("\" r=\"3\"><title>")
                        .append(clockTime(Duration.ofSeconds(sample.seconds()))).append(": ")
                        .append(ReportPage.escape(label(one, latest))).append("</title></circle>");
            }
            double labelY = Math.max(y + 4, previousLabelY + 14);
            previousLabelY = labelY;
            svg.append("<polyline class=\"line ").append(one.css()).append("\" points=\"").append(points).append("\"/>")
                    .append(circles).append("<text class=\"").append(one.css()).append("\" x=\"").append(f1(x + 8))
                    .append("\" y=\"").append(f1(labelY)).append("\">").append(ReportPage.escape(label(one, latest)))
                    .append("</text>\n");
        }
        return svg.append("</svg>\n").toString();
    }

    private static String label(Series series, double value) {
        return series.name().isEmpty() ? series.show(value) : series.name() + " " + series.show(value);
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
                rows.append(ReportPage.row(ReportPage.textCell(words), ReportPage.numberCell(value))));
        String body = ReportPage.heading(1, TITLE)
                + ReportPage.paragraph(ReportPage.escape(status))
                + ReportPage.paragraph("Written at " + WRITTEN_AT.format(LocalDateTime.ofInstant(now, ZoneId.systemDefault())))
                + tiles(
                        tile("command", "Command: " + command),
                        tile("elapsed", "Elapsed: " + clockTime(Duration.between(commandStarted, now))),
                        tile("database", "Database size: " + megabytes(databaseBytes())))
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
                ReportPage.numberCell(execution.getReadCount()),
                ReportPage.numberCell(execution.getWriteCount()),
                ReportPage.numberCell(execution.getSkipCount()),
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
