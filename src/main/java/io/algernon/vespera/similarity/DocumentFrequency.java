package io.algernon.vespera.similarity;

import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.StatementSteps;
import io.algernon.vespera.ledger.WalkId;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code similarity}'s document-frequency pass (ADR-038, ADR-074): stage 3's corpus-wide measurement
 * of how many stage-2-surviving documents each shingle hash appears in, and how many times in total.
 * Reads the {@code shingle} table stage 2's pass already wrote; writes only its own two tables,
 * {@code shingle_document_frequency} and {@code shingle_corpus_size}. No verdict of any kind is
 * written here — stage 3 measures, it does not judge (ADR-074).
 *
 * <p>The denominator is stage-2 survivors, not every occurrence a shingle row was ever written for:
 * an occurrence carrying a blocking verdict from stage 2 ({@code extraction-failed},
 * {@code degenerate-output}) contributes to neither count, while a {@code partial_success} document
 * is a survivor by design and is included.
 *
 * <p>Counted in the database, in one grouping of the run's rows and then a check of the walk's occurrences
 * a page at a time that takes off what the ruled-out ones contributed (ADR-211 section 3). Nothing here
 * grows with the corpus in the heap: a page of ids and one count for each granularity.
 *
 * <p>The grouping names {@code shingle_by_run_id} ({@code INDEXED BY}), so SQLite reads the run's rows in the
 * order they were written and sorts them itself whether or not {@code shingle_by_hash} is built (ADR-219).
 */
@Component
public class DocumentFrequency {

    /** The most occurrences one statement of the check names: the ledger's own page. */
    private static final int PAGE = 1_000;

    private final JdbcTemplate jdbcTemplate;
    private final Ledger ledger;

    public DocumentFrequency(JdbcTemplate jdbcTemplate, Ledger ledger) {
        this.jdbcTemplate = jdbcTemplate;
        this.ledger = ledger;
    }

    /**
     * Measures document frequency over {@code stage2RunId}'s shingle rows, restricted to that run's
     * survivors, and writes the result under {@code stage3RunId}.
     */
    public void measure(RunId stage3RunId, RunId stage2RunId) {
        measure(stage3RunId, stage2RunId, FrequencyProgress.NONE);
    }

    /**
     * As {@link #measure(RunId, RunId)}, and tells {@code progress}, in this order: that the grouping of the
     * run's shingle rows starts, with {@link #shingleRowsUpTo} as its total, the steps SQLite has taken at
     * each of its callbacks, and that it ends ({@link SimilarityStatement#SHINGLE_ROWS}); then the number of
     * the walk's occurrences checked after each page of them (ADR-211 sections 3 and 9). A run with no shingle
     * row is told started with an empty total and ended at once, and nothing else is done. A statement that
     * throws is not told to have ended.
     */
    public void measure(RunId stage3RunId, RunId stage2RunId, FrequencyProgress progress) {
        OptionalLong rowsUpTo = shingleRowsUpTo(stage2RunId);
        progress.statementStarting(SimilarityStatement.SHINGLE_ROWS, rowsUpTo);
        if (rowsUpTo.isEmpty()) {
            progress.statementEnded(SimilarityStatement.SHINGLE_ROWS);
            return;
        }

        // The one grouping of every shingle row of stage 2's run, counted by SQLite's progress handler
        // (ADR-193, ADR-211 section 9). It runs on the connection the template hands over and is never
        // handed back to it.
        StatementSteps.counted(
                jdbcTemplate,
                steps -> progress.stepsTaken(SimilarityStatement.SHINGLE_ROWS, steps),
                connection -> {
                    try (PreparedStatement grouping = connection.prepareStatement(
                            "INSERT INTO shingle_document_frequency"
                                    + " (run_id, shingle_parameter_identity, shingle_hash, document_count, total_count)"
                                    + " SELECT ?, shingle_parameter_identity, shingle_hash,"
                                    + " COUNT(DISTINCT occurrence_id), COUNT(*) FROM shingle INDEXED BY shingle_by_run_id WHERE run_id = ?"
                                    + " GROUP BY shingle_parameter_identity, shingle_hash"
                                    + " HAVING COUNT(DISTINCT occurrence_id) >= 2")) {
                        grouping.setString(1, stage3RunId.value());
                        grouping.setString(2, stage2RunId.value());
                        grouping.executeUpdate();
                    }
                    return null;
                });
        progress.statementEnded(SimilarityStatement.SHINGLE_ROWS);

        WalkId walk = ledger.runs()
                .walkOf(stage2RunId)
                .orElseThrow(() -> new IllegalStateException("Run " + stage2RunId.value() + " has no walk"));
        Map<String, Long> shingledSurvivors = new LinkedHashMap<>();
        List<OccurrenceId> page = new ArrayList<>(PAGE);
        for (OccurrenceId occurrence : ledger.occurrences().occurrencesOf(walk)) {
            page.add(occurrence);
            if (page.size() == PAGE) {
                check(stage3RunId, stage2RunId, page, shingledSurvivors);
                progress.occurrencesChecked(page.size());
                page.clear();
            }
        }
        if (!page.isEmpty()) {
            check(stage3RunId, stage2RunId, page, shingledSurvivors);
            progress.occurrencesChecked(page.size());
        }

        // A granularity no surviving occurrence carries was never counted and earns no row (ADR-074).
        shingledSurvivors.forEach((parameterIdentity, shingledDocuments) -> jdbcTemplate.update(
                "INSERT INTO shingle_corpus_size (run_id, shingle_parameter_identity, shingled_document_count)"
                        + " VALUES (?, ?, ?)",
                stage3RunId.value(),
                parameterIdentity,
                shingledDocuments));
    }

    /**
     * One page of the walk's occurrences: counts the survivors' granularities into {@code shingledSurvivors},
     * and takes off the frequency rows what the page's ruled-out occurrences contributed to the grouping,
     * deleting each hash that falls below two.
     */
    private void check(RunId stage3RunId, RunId stage2RunId, List<OccurrenceId> page, Map<String, Long> shingledSurvivors) {
        Set<OccurrenceId> surviving = ledger.verdicts().survivingAmong(stage2RunId, page);
        List<Object> survivors = new ArrayList<>();
        List<Object> ruledOut = new ArrayList<>();
        for (OccurrenceId occurrence : page) {
            (surviving.contains(occurrence) ? survivors : ruledOut).add(occurrence.value());
        }
        if (!survivors.isEmpty()) {
            List<Object> arguments = new ArrayList<>();
            arguments.add(stage2RunId.value());
            arguments.addAll(survivors);
            jdbcTemplate.query(
                    "SELECT shingle_parameter_identity, COUNT(DISTINCT occurrence_id) FROM shingle"
                            + " WHERE run_id = ? AND occurrence_id IN (" + placeholders(survivors.size()) + ")"
                            + " GROUP BY shingle_parameter_identity",
                    resultSet -> {
                        shingledSurvivors.merge(resultSet.getString(1), resultSet.getLong(2), Long::sum);
                    },
                    arguments.toArray());
        }
        if (!ruledOut.isEmpty()) {
            String names = placeholders(ruledOut.size());
            List<Object> takeOff = new ArrayList<>();
            takeOff.add(stage3RunId.value());
            takeOff.add(stage2RunId.value());
            takeOff.addAll(ruledOut);
            jdbcTemplate.update(
                    "INSERT INTO shingle_document_frequency"
                            + " (run_id, shingle_parameter_identity, shingle_hash, document_count, total_count)"
                            + " SELECT ?, shingle_parameter_identity, shingle_hash,"
                            + " -COUNT(DISTINCT occurrence_id), -COUNT(*) FROM shingle"
                            + " WHERE run_id = ? AND occurrence_id IN (" + names + ")"
                            + " GROUP BY shingle_parameter_identity, shingle_hash"
                            + " ON CONFLICT (run_id, shingle_parameter_identity, shingle_hash) DO UPDATE SET"
                            + " document_count = document_count + excluded.document_count,"
                            + " total_count = total_count + excluded.total_count",
                    takeOff.toArray());
            List<Object> deletion = new ArrayList<>();
            deletion.add(stage3RunId.value());
            deletion.add(stage2RunId.value());
            deletion.addAll(ruledOut);
            jdbcTemplate.update(
                    "DELETE FROM shingle_document_frequency WHERE run_id = ? AND document_count < 2"
                            + " AND (shingle_parameter_identity, shingle_hash) IN"
                            + " (SELECT shingle_parameter_identity, shingle_hash FROM shingle"
                            + " WHERE run_id = ? AND occurrence_id IN (" + names + "))",
                    deletion.toArray());
        }
    }

    private static String placeholders(int count) {
        return java.util.stream.IntStream.range(0, count).mapToObj(i -> "?").collect(Collectors.joining(", "));
    }

    /**
     * How many shingle rows {@link #measure} reads at most from {@code stage2RunId}: the span of the
     * rowids that run wrote in {@code shingle}, greatest less least plus one (ADR-191 sections 2 and 3).
     * Empty for a run with no shingle row, which has nothing to read.
     *
     * <p>Exact where the run's rows are one unbroken stretch of rowids; too high where another run wrote
     * between them, because the span then takes in that run's rows. Hence "up to", not "exactly".
     * Writes nothing and logs nothing: {@code similarity} knows no stage, and what is said about the
     * bound, and when, is the caller's. {@link #measure} asks for it itself and hands it to its {@link
     * FrequencyProgress} in {@code statementStarting}, immediately before the grouping of the rows, the
     * total its progress lines are a share of (ADR-193 section 7, ADR-211 section 3).
     */
    public OptionalLong shingleRowsUpTo(RunId stage2RunId) {
        // Two statements on purpose, never one. Each is one descent of shingle_by_run_id: 2 ms and
        // 40 KB for the pair, with the file cache emptied. One statement asking for MIN(rowid) and
        // MAX(rowid) together, or COUNT(*), reads the run's whole part of the index instead: about
        // 2.9 s and 209 MB, measured for a run of 2,500,000 rows, with the cache emptied, on a
        // solid-state disk. On the disk where the read this bound is stated before took half an
        // hour, the same walk would be minutes more: an estimate, not a measurement, from ADR-191's
        // arithmetic of 51,092 pages at 100 pages a second, about eight and a half minutes. No test
        // can tell the two forms apart, so no test holds the two-statement form in place; ADR-191
        // section 2 and this comment do. The table's own MAX(rowid) is not used: it counts every
        // run's rows, and the table keeps them.
        Long least = jdbcTemplate.queryForObject(
                "SELECT MIN(rowid) FROM shingle WHERE run_id = ?", Long.class, stage2RunId.value());
        Long greatest = jdbcTemplate.queryForObject(
                "SELECT MAX(rowid) FROM shingle WHERE run_id = ?", Long.class, stage2RunId.value());
        if (least == null || greatest == null) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(greatest - least + 1);
    }

    /**
     * Deletes every document-frequency and corpus-size row recorded under {@code stage3RunId} — the
     * discard half of ADR-115/ADR-116, for a step whose completion under this run is not recorded.
     */
    public void discardForRun(RunId stage3RunId) {
        jdbcTemplate.update("DELETE FROM shingle_document_frequency WHERE run_id = ?", stage3RunId.value());
        jdbcTemplate.update("DELETE FROM shingle_corpus_size WHERE run_id = ?", stage3RunId.value());
    }
}
