package io.algernon.vespera.engine.pipeline;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.ledger.Runs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
 * What the job's report is handed while the job runs (ADR-362 section 3), which the progress pages were drawn from
 * until ADR-362 section 6 retired them: each step's state, counts and last progress line, the database's size, the
 * survivors and the verdicts of the step removing occurrences (ADR-268), the charts' samples and the runs the working
 * directory holds (ADR-269). It reads only what the job holds in memory, the verdict counts {@code Verdicts} keeps and
 * two file sizes, and writes no file: the report does (ADR-265).
 *
 * <p>Readings are made by one daemon thread of its own at a fixed delay, so no step thread makes one; the first is
 * made by {@link #beforeJob} and the last by {@link #afterJob}, under the lock that every reading is made under,
 * after which a reading that was due makes nothing. A reading that fails is warned of once, and again only after one
 * has succeeded; it never fails the invocation (ADR-265 section 4).
 *
 * <p>It holds no rule: it decides no verdict, cache key, cluster or text of the deliverable.
 */
final class JobSampler implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(JobSampler.class);

    /** The key of what entered the outcome step, in its step execution's context (ADR-268 section 2). */
    static final String ENTERED = "progress-page.entered";

    /** At most this many samples are held; one more thins them (ADR-267 section 2). */
    private static final int MOST_SAMPLES = 256;

    /** A rate is read back to the latest sample at least this long before the latest one (ADR-268 section 3). */
    private static final long RATE_SECONDS = 600;

    /** The runs a working directory holds, as one reading of the ledger made them (ADR-269 section 5). */
    record RunsRead(List<Runs.Listed> listed, Map<String, Set<String>> finished, Map<String, Map<String, Long>> kinds) {}

    /** What the job thread holds of the runs: which reading (1 as the invocation started, 2 as it ended) and its outcome. */
    private record HeldRuns(int reading, RunsRead read) {}


    /** A counter's line: its label, how far it has got and, where it has one, its total. */
    private static final Pattern COUNTER = Pattern.compile(
            "^(" + StageProgress.LABEL_SHAPE + "): ([0-9,]+)(?: of ([0-9,]+) \\([0-9]+%\\)| so far)$");


    private static final String[] SIZE_UNITS = {"B", "KB", "MB", "GB", "TB"};


    private static final String DATABASE_FILE = "vespera.db";

    private static final String WRITE_AHEAD_LOG_FILE = "vespera.db-wal";

    private static final List<String> STEPS_IN_JOB_ORDER = InvocationReport.STEPS_IN_JOB_ORDER;




    /**
     * The blocking kinds as {@code CONTEXT.md} writes them, in {@code VerdictKind}'s order (ADR-268 section 1). Names,
     * not the enum: the page decides no verdict, and no class of {@code pipeline} that does not is to name a kind.
     */
    private static final List<String> BLOCKING = List.of(
            "broken", "duplicate-of", "superseded-by", "out-of-scope", "code",
            "extraction-failed", "degenerate-output", "redundant-with", "translation-of", "below-threshold");

    /** The steps that remove occurrences, in job order, and the kinds each records (ADR-268 section 1). */
    private static final Map<String, List<String>> OUTCOME_STEPS = new LinkedHashMap<>();

    static {
        OUTCOME_STEPS.put(
                StepNames.BYTE_LEVEL_REDUCTION, List.of("broken", "duplicate-of", "superseded-by", "out-of-scope", "code"));
        OUTCOME_STEPS.put(StepNames.EXTRACTION, List.of("extraction-failed", "degenerate-output"));
        OUTCOME_STEPS.put(StepNames.CONTENT_REDUNDANCY, List.of("redundant-with"));
        OUTCOME_STEPS.put(StepNames.TRANSLATION, List.of("translation-of"));
        OUTCOME_STEPS.put(StepNames.RELEVANCE_FLOOR, List.of("below-threshold"));
    }

    /** The whole text of a statement's line over rows: its label in the form of a counter's, then the percentage. */
    private static final Pattern STATEMENT_LINE =
            Pattern.compile(StageProgress.LABEL_SHAPE + ": (about|at least) [0-9]+% of [0-9,]+ rows");


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

    /** A line of a chart: what it is called and its value at each sample. */
    private record Line(String name, double[] values) {}

    /** The verdicts recorded in this process by kind, read at each write without a lock any writer waits on. */
    private final Supplier<Map<String, Long>> recorded;

    /** Guarded by {@link #writing}: {@link #recorded} when the invocation began, so the report counts its own. */
    private Map<String, Long> baseline = Map.of();

    private volatile JobExecution jobExecution;
    private ScheduledExecutorService writer;
    private final List<ch.qos.logback.classic.Logger> listenedTo = new ArrayList<>();
    private final List<LineAppender> appenders = new ArrayList<>();

    /** Guarded by {@link #writing}. */
    private boolean ended;

    /** Guarded by {@link #writing}: whether the last reading failed, so that the warning is not said again. */
    private boolean warned;

    /** The report the job's writes are handed to, set where the application builds it (ADR-362 section 3). */
    private InvocationReport report;

    /** Guarded by {@link #writing}: the reading the last read made, for the report; null before and after a failed one. */
    private InvocationReport.Reading lastReading;

    private final Supplier<RunsRead> runsReader;

    /** The reading of the runs the report shows, set on the job's thread and read by the writer; null before the first. */
    private volatile HeldRuns heldRuns;

    /** Whether the failure of a reading was logged in this invocation. */
    private volatile boolean warnedOfRuns;

    /** Guarded by {@link #writing}: the instant of the first write, the start of a job that holds none. */
    private Instant firstWrite;

    JobSampler(Path workingDirectory, Duration interval, Clock clock) {
        this(workingDirectory, interval, clock, Map::of);
    }

    JobSampler(
            Path workingDirectory,
            Duration interval,
            Clock clock,
            Supplier<Map<String, Long>> recorded) {
        this(workingDirectory, interval, clock, recorded, () -> new RunsRead(List.of(), Map.of(), Map.of()));
    }

    JobSampler(
            Path workingDirectory,
            Duration interval,
            Clock clock,
            Supplier<Map<String, Long>> recorded,
            Supplier<RunsRead> runsReader) {
        this.workingDirectory = workingDirectory.toAbsolutePath().normalize();
        this.interval = interval;
        this.clock = clock;
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
     * invocation, as the exception class's name only, and the report says the runs could not be read.
     */
    private void readTheRuns(int reading) {
        RunsRead read = null;
        try {
            read = runsReader.get();
        } catch (RuntimeException e) {
            if (!warnedOfRuns) {
                warnedOfRuns = true;
                log.warn("the runs of the report could not be read: {}", e.getClass().getName());
            }
        }
        heldRuns = new HeldRuns(reading, read);
    }

    /** Hands every reading of the job to {@code report} (ADR-362 section 3). */
    void handTo(InvocationReport report) {
        this.report = report;
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
        if (report != null) {
            report.jobStarts();
        }
        synchronized (writing) {
            ended = false;
            baseline = recorded.get();
            samples.clear();
            firstWrite = null;
            heldRuns = null;
            warnedOfRuns = false;
            write(false);
        }
        try {
            if (interval.isZero() || interval.isNegative()) {
                throw new IllegalArgumentException("vespera.progress-interval is not above zero");
            }
            writer = Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "job-sampler");
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

    /** One reading handed to the report, under {@link #writing}; a failure is caught here and never reaches the job. */
    private void write(boolean last) {
        lastReading = null;
        try {
            read(last);
            warned = false;
        } catch (RuntimeException e) {
            failed(e);
        }
        if (report != null && lastReading != null) {
            report.job(lastReading);
        }
    }

    private void failed(Throwable e) {
        if (!warned) {
            warned = true;
            log.warn("the job could not be read for the report: {}", e.getClass().getName());
        }
    }

    /** One reading of the job: a sample held for the charts, and the report's reading made from it. */
    private void read(boolean last) {
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

        Map<String, String> states = new LinkedHashMap<>();
        String running = null;
        for (String step : STEPS_IN_JOB_ORDER) {
            StepExecution execution = executions.get(step);
            boolean inside = !last && stillInside.contains(step) && !anExecutionAfter(step, executions);
            String state = stateOf(execution, shutGates.shutAt(step), inside);
            states.put(step, state);
            if (state.equals("running")) {
                running = step;
            }
        }
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
        lastReading = reading(last, job, executions, states, shutGates, zone, now, bytes, outcomeStep, entered, survivors,
                latest, base);
    }

    /** What the report is handed of this reading (ADR-362 section 3). */
    private InvocationReport.Reading reading(
            boolean last, JobExecution job, Map<String, StepExecution> executions, Map<String, String> states,
            ShutGates shutGates, ZoneId zone, Instant now, long bytes, String outcomeStep, long entered, long survivors,
            Sample latest, Sample base) {
        List<InvocationReport.Step> steps = new ArrayList<>();
        Map<String, java.util.Optional<String>> gateKeys = new LinkedHashMap<>();
        List<InvocationReport.Fault> faults = new ArrayList<>();
        for (String step : STEPS_IN_JOB_ORDER) {
            StepExecution execution = executions.get(step);
            String state = states.get(step);
            Matcher counter = COUNTER.matcher(lastLine.getOrDefault(step, ""));
            boolean counted = counter.matches();
            Instant start = execution == null || execution.getStartTime() == null
                    ? null : execution.getStartTime().atZone(zone).toInstant();
            Instant end = execution == null || execution.getEndTime() == null
                    ? null : execution.getEndTime().atZone(zone).toInstant();
            steps.add(new InvocationReport.Step(
                    step,
                    state,
                    execution == null ? 0 : execution.getReadCount(),
                    execution == null ? 0 : execution.getWriteCount(),
                    execution == null ? 0 : execution.getSkipCount(),
                    start,
                    end,
                    start == null ? null : Math.max(0, Duration.between(start, end == null ? now : end).getSeconds()),
                    lastLine.get(step),
                    counted ? Long.parseLong(counter.group(2).replace(",", "")) : null,
                    counted && counter.group(3) != null ? Long.parseLong(counter.group(3).replace(",", "")) : null));
            if ("stopped at a gate".equals(state)) {
                gateKeys.put(step, shutGates.keyOf(step));
            }
            if ("failed".equals(state) && execution != null && !execution.getFailureExceptions().isEmpty()) {
                Throwable failure = StepFailure.firstBeneathTheFramework(execution.getFailureExceptions().getFirst());
                List<String> causes = new ArrayList<>();
                Throwable cause = failure.getCause();
                for (int i = 0; i < 3 && cause != null; i++, cause = cause.getCause()) {
                    causes.add(cause.getClass().getName());
                }
                faults.add(new InvocationReport.Fault(step, failure.getClass().getName(), causes));
            }
        }
        InvocationReport.Removing removing = null;
        if (outcomeStep != null) {
            Map<String, Long> verdicts = new LinkedHashMap<>();
            Map<String, Double> perMinute = new LinkedHashMap<>();
            for (String kind : OUTCOME_STEPS.get(outcomeStep)) {
                int at = BLOCKING.indexOf(kind);
                verdicts.put(kind, latest.kinds()[at]);
                if (base != null) {
                    perMinute.put(kind, (latest.kinds()[at] - base.kinds()[at]) * 60.0 / (latest.seconds() - base.seconds()));
                }
            }
            removing = new InvocationReport.Removing(outcomeStep, entered, survivors, verdicts, perMinute);
        }
        List<InvocationReport.Chart> charts = new ArrayList<>();
        for (ChartData chart : Arrays.asList(databaseData(), countsData(), verdictsData())) {
            if (chart != null) {
                charts.add(new InvocationReport.Chart(
                        chart.key(),
                        chart.title(),
                        chart.axis(),
                        chart.seconds(),
                        chart.lines().stream()
                                .map(line -> new InvocationReport.Line(line.name(), line.values()))
                                .toList()));
            }
        }
        Map<String, String> arrivedAt = new LinkedHashMap<>();
        new InvocationRuns(job.getExecutionContext()).all().forEach((stage, run) -> {
            if (run.matches("[0-9a-f]{8,128}")) {
                arrivedAt.put(stage, run);
            }
        });
        String outcome = !last ? null
                : job.getStatus() == BatchStatus.COMPLETED ? "completed"
                : job.getStatus() == BatchStatus.STOPPED || job.getStatus() == BatchStatus.STOPPING ? "stopped"
                : "failed";
        return new InvocationReport.Reading(
                last, outcome, steps, gateKeys, bytes, removing, charts, runsOf(job), arrivedAt, faults);
    }

    /** The runs as the held reading shows them, in the order the tab of runs lists them (ADR-269 section 5). */
    private InvocationReport.Runs runsOf(JobExecution job) {
        HeldRuns held = heldRuns;
        if (held == null) {
            return new InvocationReport.Runs("none", List.of());
        }
        String reading = held.reading() == 1 ? "start" : "end";
        if (held.read() == null) {
            return new InvocationReport.Runs("failed", List.of());
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
        List<InvocationReport.RunRow> rows = new ArrayList<>();
        for (Object[] entry : runs) {
            Runs.Listed run = (Runs.Listed) entry[0];
            List<String> steps = InvocationReport.STEPS_OF_A_STAGE.get(run.stage());
            Set<String> done = held.read().finished().getOrDefault(run.id(), Set.of());
            int finished = steps == null ? 0 : (int) steps.stream().filter(done::contains).count();
            Map<String, Long> kinds = held.read().kinds().getOrDefault(run.id(), Map.of());
            Map<String, Long> verdicts = new LinkedHashMap<>();
            for (String kind : BLOCKING) {
                verdicts.put(kind, kinds.getOrDefault(kind, 0L));
            }
            rows.add(new InvocationReport.RunRow(
                    run.walk(),
                    run.stage(),
                    (Integer) entry[1],
                    steps == null ? null : finished,
                    steps == null ? null : steps.size(),
                    steps == null ? null : finished == steps.size() ? "finished" : "not finished",
                    arrivedAt.contains(run.id()),
                    verdicts));
        }
        return new InvocationReport.Runs(reading, rows);
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

    /** A chart's numbers, which the report copies (ADR-362 section 3). */
    private record ChartData(String key, String title, String axis, List<Long> seconds, List<Line> lines) {}

    /** The database's size at each sample, over its own range (ADR-268 section 7). */
    private ChartData databaseData() {
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
        return new ChartData(
                "database",
                "Database size over time",
                "Database size (" + SIZE_UNITS[unit] + ")",
                samples.stream().map(Sample::seconds).toList(),
                List.of(new Line("size", Arrays.stream(values).map(v -> v / divisor).toArray())));
    }

    /** The latest step a sample names, or null where none does. */
    private String latestStepSampled() {
        String step = null;
        for (Sample sample : samples) {
            if (sample.step() != null) {
                step = sample.step();
            }
        }
        return step;
    }

    /**
     * The chart of counts of the latest step a sample names, from what that step says of itself first (section 7);
     * null where there is none to draw.
     */
    private ChartData countsData() {
        String step = latestStepSampled();
        if (step == null) {
            return null;
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
            return new ChartData(
                    "counts",
                    label + " over time",
                    "Done (count)",
                    at,
                    List.of(new Line("done", done.stream().mapToDouble(Double::doubleValue).toArray())));
        }
        if (ofStep.stream().anyMatch(s -> s.read() > 0 || s.written() > 0)) {
            double[] read = ofStep.stream().mapToDouble(Sample::read).toArray();
            double[] written = ofStep.stream().mapToDouble(Sample::written).toArray();
            return new ChartData(
                    "counts",
                    "Read and written by " + step + " over time",
                    "Items (count)",
                    ofStep.stream().map(Sample::seconds).toList(),
                    List.of(new Line("read", read), new Line("written", written)));
        }
        return null;
    }

    /** The verdicts of the outcome step by kind at each sample, from zero; null where no step is the outcome step. */
    private ChartData verdictsData() {
        String step = samples.getLast().outcomeStep();
        if (step == null) {
            return null;
        }
        List<Line> lines = new ArrayList<>();
        for (String kind : OUTCOME_STEPS.get(step)) {
            int at = BLOCKING.indexOf(kind);
            double[] values = samples.stream().mapToDouble(s -> s.kinds()[at]).toArray();
            lines.add(new Line(kind, values));
        }
        return new ChartData(
                "verdicts",
                "Verdicts recorded by " + step + " over time",
                "Verdicts (count)",
                samples.stream().map(Sample::seconds).toList(),
                lines);
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
