package io.algernon.vespera.engine.pipeline;

import io.algernon.vespera.engine.extraction.FailureCategory;
import io.algernon.vespera.engine.ledger.Placeholders;
import io.algernon.vespera.engine.ledger.VerdictKind;
import io.algernon.vespera.engine.synthesis.ClusterFaultKind;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * What the report counts of an invocation, asked of the ledger when the job ends (ADR-362 section 3): the occurrences,
 * the verdicts, the extraction faults and the clusters under the runs the invocation arrived at, which
 * {@link InvocationReport#counted} holds as {@code counts}. Until ADR-362 section 4 this class wrote the invocation
 * account, {@code invocation-account-<UTC>.txt}, with them; no invocation writes it any more, and the report's copy
 * in the {@code logs} folder replaces it (ADR-270).
 *
 * <p><b>The guarantee is the allow-list.</b> Nothing here takes a message, a path or a title. Counts are numbers
 * grouped by a closed enumeration (a stored value that is none of them is counted as {@code other}). The SQL selects
 * counts and sums of comparisons with the enumeration's constants, and carries no column out: never {@code detail},
 * {@code reason}, {@code label}, {@code title} or a path (ADR-224).
 *
 * <p>A failure to count never fails the invocation (ADR-198 section 6).
 */
final class InvocationAccount implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(InvocationAccount.class);

    private final Path workingDirectory;
    private final Path accountDirectory;
    private final JdbcTemplate jdbcTemplate;
    private final InvocationReport report;

    /**
     * @param accountDirectory what {@code vespera.account-dir} holds, or {@code null} when it is unset; it is
     *     not read for a place, and set costs one warning in {@link #beforeJob} (ADR-270 section 3, ADR-362 section
     *     4); the report's copy is always written in {@code <workingDirectory>/logs}
     */
    InvocationAccount(
            Path workingDirectory, Path accountDirectory, JdbcTemplate jdbcTemplate, InvocationReport report) {
        this.report = report;
        this.workingDirectory = workingDirectory;
        this.accountDirectory = accountDirectory;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        if (accountDirectory != null) {
            log.warn("vespera.account-dir is no longer read, and the report's copy is written to {}",
                    workingDirectory.toAbsolutePath().resolve("logs"));
        }
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        try {
            counts(new InvocationRuns(jobExecution.getExecutionContext()));
        } catch (RuntimeException e) {
            log.warn("the invocation's counts could not be read: {}", e.getClass().getName());
        }
    }

    private void counts(InvocationRuns runs) {
        Collection<String> ids = runs.all().values();
        if (ids.isEmpty()) {
            return;
        }
        Object[] args = ids.toArray();
        Map<String, Object> held = new java.util.LinkedHashMap<>();
        held.put("occurrences", null);
        // The run ids' placeholders, which each statement brackets after its own IN, so that a test planning
        // the text reads a list there (ADR-224).
        String runIds = Placeholders.of(args.length);
        runs.runOf(StepNames.BYTE_LEVEL_REDUCTION).ifPresent(run -> held.put(
                "occurrences",
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM file_occurrence WHERE walk_id = (SELECT walk_id FROM run WHERE id = ?)",
                        Long.class,
                        run.value())));
        // ADR-224 section 1: a total and a sum for each constant in declaration order, each text written out
        // whole so that a test plans it, NOCASE because a category is stored in lower case.
        held.put("verdicts", counted("SELECT COUNT(*), SUM(kind = 'BROKEN' COLLATE NOCASE),"
                + " SUM(kind = 'DUPLICATE_OF' COLLATE NOCASE), SUM(kind = 'SUPERSEDED_BY' COLLATE NOCASE),"
                + " SUM(kind = 'OUT_OF_SCOPE' COLLATE NOCASE), SUM(kind = 'CODE' COLLATE NOCASE),"
                + " SUM(kind = 'EXTRACTION_FAILED' COLLATE NOCASE),"
                + " SUM(kind = 'DEGENERATE_OUTPUT' COLLATE NOCASE), SUM(kind = 'REDUNDANT_WITH' COLLATE NOCASE),"
                + " SUM(kind = 'TRANSLATION_OF' COLLATE NOCASE),"
                + " SUM(kind = 'BELOW_THRESHOLD' COLLATE NOCASE), SUM(kind = 'PASSED' COLLATE NOCASE)"
                + " FROM verdict WHERE run_id IN (" + runIds + ")", args, VerdictKind.class));
        held.put("extractionFaults", counted("SELECT COUNT(*),"
                + " SUM(category = 'POLICY' COLLATE NOCASE), SUM(category = 'CAPACITY' COLLATE NOCASE),"
                + " SUM(category = 'SOURCE_UNAVAILABLE' COLLATE NOCASE),"
                + " SUM(category = 'TARGET_UNAVAILABLE' COLLATE NOCASE), SUM(category = 'TIMEOUT' COLLATE NOCASE),"
                + " SUM(category = 'INTERNAL' COLLATE NOCASE), SUM(category = 'BACKEND_FAILURE' COLLATE NOCASE),"
                + " SUM(category = 'INFERENCE_FAILURE' COLLATE NOCASE), SUM(category = 'UNKNOWN' COLLATE NOCASE)"
                + " FROM extraction_fault WHERE run_id IN (" + runIds + ")", args, FailureCategory.class));
        held.put("clusterFaults", counted("SELECT COUNT(*),"
                + " SUM(kind = 'PROMPT_EVALUATION_CEILING' COLLATE NOCASE),"
                + " SUM(kind = 'ANSWER_RAN_OUT_OF_ROOM' COLLATE NOCASE), SUM(kind = 'SCHEMA_VIOLATION' COLLATE NOCASE),"
                + " SUM(kind = 'CITATION_NOT_IN_RANGE' COLLATE NOCASE)"
                + " FROM cluster_fault WHERE run_id IN (" + runIds + ")", args, ClusterFaultKind.class));
        Long clusters = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cluster WHERE run_id IN (" + runIds + ")", Long.class, args);
        Long written = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM synthesis_doc WHERE run_id IN (" + runIds + ")", Long.class, args);
        held.put("clusters", clusters);
        held.put("synthesisDocs", written);
        report.counted(held);
    }

    /**
     * One count for each constant of {@code type} that a row carries, and one for {@code other}, the rows that
     * carry none: the total less the sums, so a stored value is never held (ADR-224 section 1). The sums
     * come in the order the constants are declared, which the text names.
     */
    private <E extends Enum<E>> Map<String, Object> counted(
            String sql, Object[] args, Class<E> type) {
        Map<String, Object> held = new java.util.LinkedHashMap<>();
        jdbcTemplate.query(sql, resultSet -> {
            long other = resultSet.getLong(1);
            E[] kinds = type.getEnumConstants();
            for (int i = 0; i < kinds.length; i++) {
                long sum = resultSet.getLong(i + 2); // NULL over no rows reads as 0
                other -= sum;
                held.put(kinds[i].name().toLowerCase(Locale.ROOT).replace('_', '-'), sum);
            }
            held.put("other", other);
        }, args);
        return held;
    }
}
