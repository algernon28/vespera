package io.algernon.vespera.pipeline;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.algernon.vespera.extraction.FailureCategory;
import io.algernon.vespera.ledger.Placeholders;
import io.algernon.vespera.ledger.VerdictKind;
import io.algernon.vespera.synthesis.ClusterFaultKind;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
 * the runs, counts, progress lines, and a failed step's exception types. It names no document. It is
 * written as {@code invocation-account-<UTC>.txt} in the working directory's {@code logs} folder (ADR-270).
 *
 * <p><b>The guarantee is the allow-list.</b> Nothing here takes a message, a path or a title. Step and
 * stage names are checked against the closed sets this package already holds, counts are numbers grouped
 * by a closed enumeration (a stored value that is none of them is written as {@code other}), an exception
 * is written as class names, and a progress line is written only when its whole text has the shape a
 * progress counter gives it. The SQL selects counts and sums of comparisons with the enumeration's
 * constants, and carries no column out: never {@code detail}, {@code reason}, {@code label}, {@code title} or a path
 * (ADR-224).
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
            StepNames.TRANSLATION,
            StepNames.RELEVANCE_SCORING,
            StepNames.RELEVANCE_FLOOR,
            StepNames.CLUSTERING,
            StepNames.RELEVANCE_REPORT,
            StepNames.ARRANGEMENT,
            StepNames.GENERATION);

    private static final Set<String> STAGES =
            Arrays.stream(StageModules.values()).map(StageModules::stage).collect(Collectors.toSet());

    /** The whole text of a progress line: a stage label made of lowercase words, then counts. */
    private static final Pattern PROGRESS_SHAPE = StageProgress.COUNTER_LINE;

    private final Path workingDirectory;
    private final Path accountDirectory;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    private BufferedWriter out;
    private Path file;
    private ch.qos.logback.classic.Logger progressLogger;
    private ProgressAppender appender;
    private long withheld;

    /**
     * @param accountDirectory what {@code vespera.account-dir} holds, or {@code null} when it is unset; it is
     *     not read for a place, and set costs one warning in {@link #beforeJob} (ADR-270 section 3); the
     *     account is always written in {@code <workingDirectory>/logs}
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
        if (accountDirectory != null) {
            log.warn("vespera.account-dir is no longer read, and the invocation account is written to {}",
                    workingDirectory.toAbsolutePath().resolve("logs"));
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
        // The run ids' placeholders, which each statement brackets after its own IN, so that a test planning
        // the text reads a list there (ADR-224).
        String runIds = Placeholders.of(args.length);
        runs.runOf(StepNames.BYTE_LEVEL_REDUCTION).ifPresent(run -> {
            Long occurrences = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM file_occurrence WHERE walk_id = (SELECT walk_id FROM run WHERE id = ?)",
                    Long.class,
                    run.value());
            writeQuietly("counts occurrences=" + occurrences);
        });
        // ADR-224 section 1: a total and a sum for each constant in declaration order, each text written out
        // whole so that a test plans it, NOCASE because a category is stored in lower case.
        counted("SELECT COUNT(*), SUM(kind = 'BROKEN' COLLATE NOCASE),"
                + " SUM(kind = 'DUPLICATE_OF' COLLATE NOCASE), SUM(kind = 'SUPERSEDED_BY' COLLATE NOCASE),"
                + " SUM(kind = 'OUT_OF_SCOPE' COLLATE NOCASE), SUM(kind = 'EXTRACTION_FAILED' COLLATE NOCASE),"
                + " SUM(kind = 'DEGENERATE_OUTPUT' COLLATE NOCASE), SUM(kind = 'REDUNDANT_WITH' COLLATE NOCASE),"
                + " SUM(kind = 'TRANSLATION_OF' COLLATE NOCASE), SUM(kind = 'BELOW_THRESHOLD' COLLATE NOCASE), SUM(kind = 'PASSED' COLLATE NOCASE)"
                + " FROM verdict WHERE run_id IN (" + runIds + ")", args, "verdict kind", VerdictKind.class, false);
        counted("SELECT COUNT(*),"
                + " SUM(category = 'POLICY' COLLATE NOCASE), SUM(category = 'CAPACITY' COLLATE NOCASE),"
                + " SUM(category = 'SOURCE_UNAVAILABLE' COLLATE NOCASE),"
                + " SUM(category = 'TARGET_UNAVAILABLE' COLLATE NOCASE), SUM(category = 'TIMEOUT' COLLATE NOCASE),"
                + " SUM(category = 'INTERNAL' COLLATE NOCASE), SUM(category = 'BACKEND_FAILURE' COLLATE NOCASE),"
                + " SUM(category = 'INFERENCE_FAILURE' COLLATE NOCASE), SUM(category = 'UNKNOWN' COLLATE NOCASE)"
                + " FROM extraction_fault WHERE run_id IN (" + runIds + ")", args, "extraction-fault category",
                FailureCategory.class, true);
        counted("SELECT COUNT(*),"
                + " SUM(kind = 'PROMPT_EVALUATION_CEILING' COLLATE NOCASE),"
                + " SUM(kind = 'ANSWER_RAN_OUT_OF_ROOM' COLLATE NOCASE), SUM(kind = 'SCHEMA_VIOLATION' COLLATE NOCASE),"
                + " SUM(kind = 'CITATION_NOT_IN_RANGE' COLLATE NOCASE)"
                + " FROM cluster_fault WHERE run_id IN (" + runIds + ")", args, "cluster-fault kind",
                ClusterFaultKind.class, false);
        Long clusters = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cluster WHERE run_id IN (" + runIds + ")", Long.class, args);
        Long written = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM synthesis_doc WHERE run_id IN (" + runIds + ")", Long.class, args);
        line("counts clusters arranged=" + clusters + " written=" + written);
    }

    /**
     * One line for each constant of {@code type} that a row carries, and one for {@code other}, the rows that
     * carry none: the total less the sums, so a stored value is never written (ADR-224 section 1). The sums
     * come in the order the constants are declared, which the text names.
     */
    private <E extends Enum<E>> void counted(String sql, Object[] args, String what, Class<E> type, boolean lowerCase)
            throws IOException {
        Map<String, Long> totals = new TreeMap<>();
        jdbcTemplate.query(sql, resultSet -> {
            long other = resultSet.getLong(1);
            E[] kinds = type.getEnumConstants();
            for (int i = 0; i < kinds.length; i++) {
                long sum = resultSet.getLong(i + 2); // NULL over no rows reads as 0
                other -= sum;
                totals.put(lowerCase ? kinds[i].name().toLowerCase(Locale.ROOT) : kinds[i].name(), sum);
            }
            totals.put("other", other);
        }, args);
        for (Map.Entry<String, Long> total : totals.entrySet()) {
            if (total.getValue() > 0) {
                line("counts " + what + "=" + total.getKey() + " count=" + total.getValue());
            }
        }
    }

    /** A run id is a hexadecimal digest; anything else is not written. */
    private static String digestOnly(String id) {
        return id.matches("[0-9a-f]{8,128}") ? id : "unknown";
    }

    private static String instantOf(LocalDateTime time) {
        return time == null ? "none" : instant(time).toString();
    }

    /** Spring Batch records its times as {@code LocalDateTime.now()}: the wall clock of this machine's zone. */
    private static Instant instant(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant();
    }

    private static Duration durationBetweenOrZero(LocalDateTime start, LocalDateTime end) {
        return start == null || end == null ? Duration.ZERO : Duration.between(instant(start), instant(end));
    }

    private static String durationBetween(LocalDateTime start, LocalDateTime end) {
        return durationBetweenOrZero(start, end).toString();
    }

    /** Writes in the working directory's {@code logs} folder, made if it is not there (ADR-270 section 2). */
    private void open() throws IOException {
        Path folder = workingDirectory.resolve("logs");
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
