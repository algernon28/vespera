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
    private ch.qos.logback.classic.Logger progressLogger;
    private ProgressAppender appender;
    private long withheld;

    /**
     * @param accountDirectory the folder {@code vespera.account-dir} names, or {@code null} when it is unset;
     *     where it is not set, lies inside a working directory by any reading of its name, or cannot be
     *     followed to where it leads, no account is written (ADR-198 section 1, ADR-203); on acceptance the
     *     folder judged is handed to {@link #open(Path)}, which writes nowhere else
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
        FolderRuling ruling = rulingOnTheFolder();
        if (ruling.refusal() != null) {
            log.warn("no invocation account was written: {}", ruling.refusal());
            return;
        }
        try {
            open(ruling.folder());
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
        // ADR-224 section 1: a total and a sum for each constant in declaration order, each text written out
        // whole so that a test plans it, NOCASE because a category is stored in lower case.
        counted("SELECT COUNT(*), SUM(kind = 'BROKEN' COLLATE NOCASE),"
                + " SUM(kind = 'DUPLICATE_OF' COLLATE NOCASE), SUM(kind = 'SUPERSEDED_BY' COLLATE NOCASE),"
                + " SUM(kind = 'OUT_OF_SCOPE' COLLATE NOCASE), SUM(kind = 'EXTRACTION_FAILED' COLLATE NOCASE),"
                + " SUM(kind = 'DEGENERATE_OUTPUT' COLLATE NOCASE), SUM(kind = 'REDUNDANT_WITH' COLLATE NOCASE),"
                + " SUM(kind = 'BELOW_THRESHOLD' COLLATE NOCASE), SUM(kind = 'PASSED' COLLATE NOCASE)"
                + " FROM verdict WHERE run_id IN " + in, args, "verdict kind", VerdictKind.class, false);
        counted("SELECT COUNT(*),"
                + " SUM(category = 'POLICY' COLLATE NOCASE), SUM(category = 'CAPACITY' COLLATE NOCASE),"
                + " SUM(category = 'SOURCE_UNAVAILABLE' COLLATE NOCASE),"
                + " SUM(category = 'TARGET_UNAVAILABLE' COLLATE NOCASE), SUM(category = 'TIMEOUT' COLLATE NOCASE),"
                + " SUM(category = 'INTERNAL' COLLATE NOCASE), SUM(category = 'BACKEND_FAILURE' COLLATE NOCASE),"
                + " SUM(category = 'INFERENCE_FAILURE' COLLATE NOCASE), SUM(category = 'UNKNOWN' COLLATE NOCASE)"
                + " FROM extraction_fault WHERE run_id IN " + in, args, "extraction-fault category",
                FailureCategory.class, true);
        counted("SELECT COUNT(*),"
                + " SUM(kind = 'PROMPT_EVALUATION_CEILING' COLLATE NOCASE),"
                + " SUM(kind = 'ANSWER_RAN_OUT_OF_ROOM' COLLATE NOCASE), SUM(kind = 'SCHEMA_VIOLATION' COLLATE NOCASE),"
                + " SUM(kind = 'CITATION_NOT_IN_RANGE' COLLATE NOCASE)"
                + " FROM cluster_fault WHERE run_id IN " + in, args, "cluster-fault kind",
                ClusterFaultKind.class, false);
        Long clusters = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cluster WHERE run_id IN " + in, Long.class, args);
        Long written = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM synthesis_doc WHERE run_id IN " + in, Long.class, args);
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
        return time == null ? "none" : time.toInstant(ZoneOffset.UTC).toString();
    }

    private static Duration durationBetweenOrZero(LocalDateTime start, LocalDateTime end) {
        return start == null || end == null ? Duration.ZERO : Duration.between(start, end);
    }

    private static String durationBetween(LocalDateTime start, LocalDateTime end) {
        return durationBetweenOrZero(start, end).toString();
    }

    /**
     * Why no account may be written, or else the folder to write in: the folder is not set, it lies inside a
     * working directory, or a name on the way to it, or the working directory, is there and cannot be
     * followed (ADR-203). The folder is judged as every reading of its name leads (its text, where its text
     * leads, the walk of ADR-201 section 1, and where this machine's own calls lead), and any one reading
     * inside a working directory refuses. On acceptance the folder returned is the one {@link #open(Path)}
     * writes to: where this machine's own calls lead.
     */
    private FolderRuling rulingOnTheFolder() {
        if (accountDirectory == null) {
            return FolderRuling.refused("vespera.account-dir is not set");
        }
        Path text = accountDirectory.toAbsolutePath().normalize();
        Path textWorking = workingDirectory.toAbsolutePath().normalize();
        Path[] readings;
        Path resolvedWorking;
        Path written;
        try {
            resolvedWorking = resolved(workingDirectory);
            written = resolved(accountDirectory);
            readings = new Path[] {text, resolved(text), walked(accountDirectory), written};
        } catch (IOException e) {
            return FolderRuling.refused("vespera.account-dir cannot be followed to where it leads");
        }
        for (Path reading : readings) {
            if (insideAWorkingDirectory(reading, textWorking, resolvedWorking)) {
                return FolderRuling.refused("vespera.account-dir lies inside a working directory");
            }
        }
        return FolderRuling.accepted(written);
    }

    /** What {@link #rulingOnTheFolder()} decided: the warning's reason, or the folder it accepted; never both. */
    private record FolderRuling(String refusal, Path folder) {

        static FolderRuling refused(String reason) {
            return new FolderRuling(reason, null);
        }

        static FolderRuling accepted(Path folder) {
            return new FolderRuling(null, folder);
        }
    }

    /** Whether {@code reading} is at or below a working directory, given or resolved, or a folder holding its files. */
    private static boolean insideAWorkingDirectory(Path reading, Path textWorking, Path resolvedWorking) {
        if (reading.startsWith(textWorking) || reading.startsWith(resolvedWorking)) {
            return true;
        }
        for (Path above = reading; above != null; above = above.getParent()) {
            if (Files.exists(above.resolve("vespera.db")) || Files.exists(above.resolve("vespera.lock"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Where {@code path} leads as this machine's own calls lead: its deepest ancestor that is there (asked
     * without following links, so a link to nothing is there) is resolved with {@code toRealPath()}, and the
     * rest, which is not there, is put back name by name and then has its parent steps folded; when that rest
     * held a parent step, the folded path, which holds none, is resolved again, so that a link the fold
     * brought up to the part that is there is followed too. The part that is there is read as the platform
     * reads it, which on Windows folds a parent step as text before it follows a link, so this is where
     * {@code createDirectories} and the open will go. When no ancestor is there (a missing drive or share)
     * the normalised text is used.
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
        Path result = existing.toRealPath();
        boolean putBackAParentStep = false;
        for (int i = existing.getNameCount(); i < absolute.getNameCount(); i++) {
            Path name = absolute.getName(i);
            putBackAParentStep |= name.toString().equals("..");
            result = result.resolve(name);
        }
        Path folded = result.normalize();
        // The fold may leave a name that is there and is a link (acct/missing/../a-link). The folded path holds
        // no parent step, so the second pass puts back none and ends there.
        return putBackAParentStep ? resolved(folded) : folded;
    }

    /**
     * The walked reading (ADR-201 section 1): from the root, one name at a time; at a {@code ..} the parent of
     * the real path reached so far, at any other name that is there its {@code toRealPath()}, and a name that
     * is not there is put back as text. It hands no path that still holds a {@code ..} to {@code relativize},
     * {@code resolve(String)} or {@code normalize}, which on Windows fold it as text.
     *
     * @throws IOException when a name the walk meets is there and cannot be followed
     */
    private static Path walked(Path path) throws IOException {
        Path absolute = path.toAbsolutePath();
        Path real = absolute.getRoot();
        for (int i = 0; i < absolute.getNameCount(); i++) {
            Path name = absolute.getName(i);
            String text = name.toString();
            if (text.equals(".")) {
                continue;
            }
            if (text.equals("..")) {
                Path parent = real.getParent();
                if (parent != null) {
                    real = parent;
                }
                continue;
            }
            Path candidate = real.resolve(name);
            real = Files.exists(candidate, LinkOption.NOFOLLOW_LINKS) ? candidate.toRealPath() : candidate;
        }
        return real;
    }

    /** Writes in {@code folder}, which {@link #rulingOnTheFolder()} judged and accepted. */
    private void open(Path folder) throws IOException {
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
