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

    private static final String TITLE = "Vespera progress";

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
    private final Path part;
    private final Path workingDirectory;
    private final Duration interval;
    private final Clock clock;

    /** The last progress line admitted for each step, written by the step's own thread and read by the writer. */
    private final Map<String, String> lastLine = new ConcurrentHashMap<>();

    /** Steps whose execution is complete while their own listener still runs on the step's thread. */
    private final java.util.Set<String> stillInside = ConcurrentHashMap.newKeySet();

    private final Object writing = new Object();

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
        this.part = this.workingDirectory.resolve(FILE_NAME + ".part");
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
            String text = command == null ? render(last) : renderCommand(last, outcome, counts);
            Files.writeString(part, text, StandardCharsets.UTF_8);
            ReplacingMove.ATOMIC.intoPlace(part, page);
            warned = false;
            return true;
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(part);
            } catch (IOException | RuntimeException ignored) {
                // the next write overwrites it
            }
            failed(e);
            return false;
        }
    }

    private void failed(Throwable e) {
        if (!warned) {
            warned = true;
            log.warn("the progress page could not be written: {}", e.getClass().getName());
        }
    }

    private String render(boolean last) {
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
        for (String step : STEPS_IN_JOB_ORDER) {
            StepExecution execution = executions.get(step);
            boolean inside = !last && stillInside.contains(step) && !anExecutionAfter(step, executions);
            rows.append(row(step, execution, shutGates.shutAt(step), inside, now, zone));
        }
        String status = last
                ? "This invocation has ended: " + job.getStatus().name().toLowerCase(Locale.ROOT) + "."
                : "This invocation is running. This page is rewritten every " + seconds + " s.";
        String body = ReportPage.heading(1, TITLE)
                + ReportPage.paragraph(ReportPage.escape(status))
                + ReportPage.paragraph("Written at " + WRITTEN_AT.format(LocalDateTime.ofInstant(now, zone)))
                + ReportPage.paragraph("Elapsed: " + clockTime(Duration.between(started, now)))
                + ReportPage.paragraph(String.format(Locale.ROOT, "Database size: %.1f MB", databaseBytes() / 1_000_000.0))
                + ReportPage.table(
                        ReportPage.headerRow("Step", "State", "Read", "Written", "Skipped", "Duration", "Progress"),
                        rows.toString());
        return last ? ReportPage.render(TITLE, body) : ReportPage.reloading(TITLE, body, seconds);
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
                + ReportPage.paragraph("Command: " + command)
                + ReportPage.paragraph("Written at " + WRITTEN_AT.format(LocalDateTime.ofInstant(now, ZoneId.systemDefault())))
                + ReportPage.paragraph("Elapsed: " + clockTime(Duration.between(commandStarted, now)))
                + ReportPage.paragraph(String.format(Locale.ROOT, "Database size: %.1f MB", databaseBytes() / 1_000_000.0))
                + (counts.isEmpty() ? "" : ReportPage.table(ReportPage.headerRow("Answers", "Count"), rows.toString()));
        return last ? ReportPage.render(TITLE, body) : ReportPage.reloading(TITLE, body, seconds);
    }

    private static boolean anExecutionAfter(String step, Map<String, StepExecution> executions) {
        return STEPS_IN_JOB_ORDER.subList(STEPS_IN_JOB_ORDER.indexOf(step) + 1, STEPS_IN_JOB_ORDER.size()).stream()
                .anyMatch(executions::containsKey);
    }

    private String row(
            String step, StepExecution execution, boolean shutAtAGate, boolean inside, Instant now, ZoneId zone) {
        if (execution == null) {
            return ReportPage.row(
                    ReportPage.textCell(step),
                    ReportPage.textCell("not started"),
                    ReportPage.numberCell(""),
                    ReportPage.numberCell(""),
                    ReportPage.numberCell(""),
                    ReportPage.textCell(""),
                    ReportPage.textCell(""));
        }
        BatchStatus status = execution.getStatus();
        String state = status == BatchStatus.STARTING || status == BatchStatus.STARTED || inside
                ? "running"
                : status == BatchStatus.COMPLETED ? (shutAtAGate ? "stopped at a gate" : "finished") : "failed";
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
