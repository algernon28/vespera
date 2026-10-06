package io.algernon.vespera.pipeline;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.algernon.vespera.extraction.FailureCategory;
import io.algernon.vespera.ledger.VerdictKind;
import io.algernon.vespera.synthesis.ClusterFaultKind;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The file an invocation writes for a hosted model to read (ADR-198, #424): each step and how it ended,
 * the runs, counts, progress lines, and a failed step's exception types. It names no document.
 *
 * <p><b>The guarantee is the allow-list.</b> Nothing here takes a message, a path or a title. Step and
 * stage names are checked against the closed sets this package already holds, counts are numbers grouped
 * by a closed enumeration (a stored value that is none of them is written as {@code other}), an exception
 * is written as class names, and a progress line is written only when its whole text has the shape a
 * progress counter gives it. The SQL selects counts and closed columns, never {@code detail}, {@code
 * reason}, {@code label}, {@code title} or a path.
 *
 * <p>A failure to write never fails the invocation (ADR-198 section 6).
 */
final class InvocationAccount implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(InvocationAccount.class);

    static final String FILE_PREFIX = "invocation-account-";

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private static final int MOST_CAUSES = 3;

    private static final Set<String> STEPS = Set.of(
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

    private static final Set<String> STAGES =
            Arrays.stream(StageModules.values()).map(StageModules::stage).collect(Collectors.toSet());

    /** The whole text of a progress line: a stage label made of lowercase words, then counts. */
    private static final Pattern PROGRESS_SHAPE = Pattern.compile(
            "Stage [0-9][a-z]? \\([a-z0-9, -]+\\): [0-9,]+( of [0-9,]+ \\([0-9]+%\\)| so far)");

    private final Path workingDirectory;
    private final Path accountDirectory;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    private BufferedWriter out;
    private Path file;
    /** The resolved place {@link #refusalToWrite()} judged, and the only place {@link #open()} writes to. */
    private Path judgedFolder;
    private ch.qos.logback.classic.Logger progressLogger;
    private ProgressAppender appender;
    private long withheld;

    /**
     * @param accountDirectory the folder {@code vespera.account-dir} names, or {@code null} when it is unset;
     *     where it is unset or lies inside a working directory no account is written (ADR-198 section 1)
     */
    InvocationAccount(Path workingDirectory, Path accountDirectory, JdbcTemplate jdbcTemplate, Clock clock) {
        this.workingDirectory = workingDirectory;
        this.accountDirectory = accountDirectory;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Override
    public synchronized void beforeJob(JobExecution jobExecution) {
        withheld = 0;
        String refusal = refusalToWrite();
        if (refusal != null) {
            log.warn("no invocation account was written: {}", refusal);
            return;
        }
        try {
            open();
            line("invocation started");
            if (LoggerFactory.getLogger(StageProgress.class) instanceof ch.qos.logback.classic.Logger logger) {
                progressLogger = logger;
                appender = new ProgressAppender();
                appender.setContext(logger.getLoggerContext());
                appender.start();
                logger.addAppender(appender);
            }
        } catch (IOException | RuntimeException e) {
            couldNotWrite(e);
        }
    }

    @Override
    public synchronized void afterJob(JobExecution jobExecution) {
        if (progressLogger != null) {
            progressLogger.detachAppender(appender);
            appender.stop();
            progressLogger = null;
        }
        if (out == null) {
            return;
        }
        try {
            if (withheld > 0) {
                line("progress withheld=" + withheld);
            }
            for (StepExecution step : jobExecution.getStepExecutions()) {
                stepLine(step);
            }
            for (Map.Entry<String, String> run : new InvocationRuns(jobExecution.getExecutionContext()).all().entrySet()) {
                line("run stage=" + (STAGES.contains(run.getKey()) ? run.getKey() : "unknown") + " id="
                        + digestOnly(run.getValue()));
            }
            counts(new InvocationRuns(jobExecution.getExecutionContext()));
            for (StepExecution step : jobExecution.getStepExecutions()) {
                failureLine(step);
            }
            line("invocation ended status=" + jobExecution.getStatus() + " duration="
                    + durationBetween(jobExecution.getStartTime(), jobExecution.getEndTime()));
        } catch (IOException | RuntimeException e) {
            couldNotWrite(e);
        } finally {
            try {
                out.close();
            } catch (IOException e) {
                couldNotWrite(e);
            }
            out = null;
        }
    }

    private void stepLine(StepExecution step) throws IOException {
        String name = STEPS.contains(step.getStepName()) ? step.getStepName() : "unknown";
        line("step name=" + name + " status=" + step.getStatus() + " start=" + instantOf(step.getStartTime())
                + " duration=" + durationBetween(step.getStartTime(), step.getEndTime()) + " read="
                + step.getReadCount() + " written=" + step.getWriteCount() + " skipped=" + step.getSkipCount()
                + " commits=" + step.getCommitCount());
    }

    private void failureLine(StepExecution step) throws IOException {
        if (step.getFailureExceptions().isEmpty()) {
            return;
        }
        StringBuilder types = new StringBuilder();
        Throwable failure = StepFailure.firstBeneathTheFramework(step.getFailureExceptions().getFirst());
        types.append(" type=").append(failure.getClass().getName());
        Throwable cause = failure.getCause();
        for (int i = 0; i < MOST_CAUSES && cause != null; i++, cause = cause.getCause()) {
            types.append(" cause=").append(cause.getClass().getName());
        }
        line("failure step=" + (STEPS.contains(step.getStepName()) ? step.getStepName() : "unknown") + types);
    }

    private void counts(InvocationRuns runs) throws IOException {
        Collection<String> ids = runs.all().values();
        if (ids.isEmpty()) {
            return;
        }
        Object[] args = ids.toArray();
        String in = "(" + String.join(",", Collections.nCopies(args.length, "?")) + ")";
        runs.runOf(StepNames.BYTE_LEVEL_REDUCTION).ifPresent(run -> {
            Long occurrences = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM file_occurrence WHERE walk_id = (SELECT walk_id FROM run WHERE id = ?)",
                    Long.class,
                    run.value());
            writeQuietly("counts occurrences=" + occurrences);
        });
        grouped("SELECT kind, COUNT(*) FROM verdict WHERE run_id IN " + in + " GROUP BY kind", args, "verdict kind",
                kind -> closed(VerdictKind.class, kind, false));
        grouped("SELECT category, COUNT(*) FROM extraction_fault WHERE run_id IN " + in + " GROUP BY category", args,
                "extraction-fault category", category -> closed(FailureCategory.class, category, true));
        grouped("SELECT kind, COUNT(*) FROM cluster_fault WHERE run_id IN " + in + " GROUP BY kind", args,
                "cluster-fault kind", kind -> closed(ClusterFaultKind.class, kind, false));
        Long clusters = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cluster WHERE run_id IN " + in, Long.class, args);
        Long written = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM synthesis_doc WHERE run_id IN " + in, Long.class, args);
        line("counts clusters arranged=" + clusters + " written=" + written);
    }

    private void grouped(String sql, Object[] args, String what, Function<String, String> allowed)
            throws IOException {
        Map<String, Long> totals = new TreeMap<>();
        jdbcTemplate.query(sql, resultSet -> {
            totals.merge(allowed.apply(resultSet.getString(1)), resultSet.getLong(2), Long::sum);
        }, args);
        for (Map.Entry<String, Long> total : totals.entrySet()) {
            line("counts " + what + "=" + total.getKey() + " count=" + total.getValue());
        }
    }

    /** The enumeration's own name for a stored value, or {@code other}: never the stored text itself. */
    private static <E extends Enum<E>> String closed(Class<E> type, String stored, boolean lowerCase) {
        for (E value : type.getEnumConstants()) {
            if (value.name().equalsIgnoreCase(stored)) {
                return lowerCase ? value.name().toLowerCase(Locale.ROOT) : value.name();
            }
        }
        return "other";
    }

    /** A run id is a hexadecimal digest; anything else is not written. */
    private static String digestOnly(String id) {
        return id.matches("[0-9a-f]{8,128}") ? id : "unknown";
    }

    private static String instantOf(LocalDateTime time) {
        return time == null ? "none" : time.toInstant(ZoneOffset.UTC).toString();
    }

    private static Duration durationBetweenOrZero(LocalDateTime start, LocalDateTime end) {
        return start == null || end == null ? Duration.ZERO : Duration.between(start, end);
    }

    private static String durationBetween(LocalDateTime start, LocalDateTime end) {
        return durationBetweenOrZero(start, end).toString();
    }

    /** Why no account may be written, or {@code null}: the folder is unset, or at or below a working directory. */
    private String refusalToWrite() {
        if (accountDirectory == null) {
            return "vespera.account-dir is not set";
        }
        Path folder;
        Path working;
        try {
            folder = resolved(accountDirectory);
            working = resolved(workingDirectory);
        } catch (IOException e) {
            return "vespera.account-dir cannot be followed to where it leads";
        }
        if (folder.startsWith(working)) {
            return "vespera.account-dir lies inside a working directory";
        }
        for (Path above = folder; above != null; above = above.getParent()) {
            if (Files.exists(above.resolve("vespera.db")) || Files.exists(above.resolve("vespera.lock"))) {
                return "vespera.account-dir lies inside a working directory";
            }
        }
        judgedFolder = folder;
        return null;
    }

    /**
     * Where {@code path} leads: its deepest ancestor that is there (asked without following links, so a link
     * to nothing is there) is resolved with {@code toRealPath()}, and the rest, which is not there, is put
     * back with its parent steps folded. No parent step is folded before the links are followed.
     *
     * @throws IOException when a name that is there cannot be followed (a link to nothing, a loop)
     */
    private static Path resolved(Path path) throws IOException {
        Path absolute = path.toAbsolutePath();
        Path existing = absolute;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            return absolute.normalize();
        }
        Path real = existing.toRealPath();
        return real.resolve(existing.relativize(absolute).toString()).normalize();
    }

    private void open() throws IOException {
        Path folder = judgedFolder;
        file = folder;
        Files.createDirectories(folder);
        String stamp = FILE_STAMP.format(clock.instant().atOffset(ZoneOffset.UTC));
        for (int attempt = 1; ; attempt++) {
            Path candidate = folder.resolve(
                    FILE_PREFIX + stamp + (attempt == 1 ? "" : "-" + attempt) + ".txt");
            file = candidate;
            try {
                out = Files.newBufferedWriter(
                        candidate, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                return;
            } catch (FileAlreadyExistsException taken) {
                // the next suffix
            }
        }
    }

    private synchronized void line(String text) throws IOException {
        if (out == null) {
            return;
        }
        out.write(clock.instant() + " " + text);
        out.newLine();
        out.flush();
    }

    private void writeQuietly(String text) {
        try {
            line(text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void couldNotWrite(Exception e) {
        log.warn("the invocation account could not be written to {}: {}", file, e.getClass().getName());
    }

    /** Carries the progress counters' lines, and only those whose whole text has a counter's shape. */
    private final class ProgressAppender extends AppenderBase<ILoggingEvent> {

        @Override
        protected void append(ILoggingEvent event) {
            String message = event.getFormattedMessage();
            synchronized (InvocationAccount.this) {
                try {
                    if (message != null && PROGRESS_SHAPE.matcher(message).matches()) {
                        line("progress " + message);
                    } else {
                        withheld++;
                    }
                } catch (IOException e) {
                    couldNotWrite(e);
                }
            }
        }
    }
}
