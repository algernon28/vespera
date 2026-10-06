package io.algernon.vespera.similarity;

import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.StatementSteps;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
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
 */
@Component
public class DocumentFrequency {

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
     * As {@link #measure(RunId, RunId)}, and tells {@code progress}, in this order: that the drain of stage
     * 2's survivors starts and ends ({@link SimilarityStatement#FREQUENCY_SURVIVORS}, timed, so with no
     * total); that the read of the shingle rows starts, with {@link #shingleRowsUpTo} as its total, empty
     * where the run holds no shingle row, the steps SQLite has taken at each of that read's callbacks, and
     * that it ends ({@link SimilarityStatement#SHINGLE_ROWS}); then the number of distinct (granularity,
     * hash) pairs counted in memory once, before the first is gone through (zero included), and each one
     * gone through, a row written for it or not (ADR-192 section 5, ADR-193 section 7). A statement that
     * throws is not told to have ended.
     */
    public void measure(RunId stage3RunId, RunId stage2RunId, FrequencyProgress progress) {
        progress.statementStarting(SimilarityStatement.FREQUENCY_SURVIVORS, OptionalLong.empty());
        Set<Long> survivorIds = drainSurvivors(stage2RunId);
        progress.statementEnded(SimilarityStatement.FREQUENCY_SURVIVORS);

        Map<Hash, Counts> byHash = new HashMap<>();
        Map<String, Set<Long>> shingledOccurrencesByParameter = new HashMap<>();

        // The one statement that reads every shingle row of stage 2's run, counted by SQLite's progress
        // handler (ADR-193). It runs on the connection the template hands over and is never handed back to it.
        progress.statementStarting(SimilarityStatement.SHINGLE_ROWS, shingleRowsUpTo(stage2RunId));
        StatementSteps.counted(
                jdbcTemplate,
                steps -> progress.stepsTaken(SimilarityStatement.SHINGLE_ROWS, steps),
                connection -> {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT occurrence_id, shingle_parameter_identity, shingle_hash FROM shingle"
                                    + " WHERE run_id = ?")) {
                        statement.setString(1, stage2RunId.value());
                        try (ResultSet resultSet = statement.executeQuery()) {
                            while (resultSet.next()) {
                                long occurrenceId = resultSet.getLong("occurrence_id");
                                if (!survivorIds.contains(occurrenceId)) {
                                    continue;
                                }
                                String parameterIdentity = resultSet.getString("shingle_parameter_identity");
                                long hash = resultSet.getLong("shingle_hash");
                                byHash.computeIfAbsent(new Hash(parameterIdentity, hash), ignored -> new Counts())
                                        .record(occurrenceId);
                                shingledOccurrencesByParameter
                                        .computeIfAbsent(parameterIdentity, ignored -> new HashSet<>())
                                        .add(occurrenceId);
                            }
                        }
                    }
                    return null;
                });
        progress.statementEnded(SimilarityStatement.SHINGLE_ROWS);

        progress.toGoThrough(byHash.size());
        byHash.forEach((hash, counts) -> {
            // The omission rule schema.sql's own comment states: only a hash seen in two or more
            // surviving documents earns a row (ADR-074) -- an absent hash means exactly one, never zero.
            if (counts.documentCount() >= 2) {
                jdbcTemplate.update(
                        "INSERT INTO shingle_document_frequency"
                                + " (run_id, shingle_parameter_identity, shingle_hash, document_count, total_count)"
                                + " VALUES (?, ?, ?, ?, ?)",
                        stage3RunId.value(),
                        hash.parameterIdentity(),
                        hash.shingleHash(),
                        counts.documentCount(),
                        counts.totalCount());
            }
            progress.hashGoneThrough();
        });

        shingledOccurrencesByParameter.forEach((parameterIdentity, occurrenceIds) -> jdbcTemplate.update(
                "INSERT INTO shingle_corpus_size (run_id, shingle_parameter_identity, shingled_document_count)"
                        + " VALUES (?, ?, ?)",
                stage3RunId.value(),
                parameterIdentity,
                occurrenceIds.size()));
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
     * FrequencyProgress} in {@code statementStarting}, immediately before the read and after the drain of
     * stage 2's survivors (ADR-193 section 7).
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

    /**
     * Reads {@code stage2RunId}'s survivors to exhaustion — the whole set is needed to test shingle
     * rows against, not one chunk of it.
     */
    private Set<Long> drainSurvivors(RunId stage2RunId) {
        ItemStreamReader<OccurrenceId> reader = ledger.survivors(stage2RunId);
        Set<Long> ids = new HashSet<>();
        try {
            reader.open(new ExecutionContext());
            try {
                for (OccurrenceId id = reader.read(); id != null; id = reader.read()) {
                    ids.add(id.value());
                }
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            throw new IllegalStateException("could not read stage 2's survivors for run " + stage2RunId.value(), e);
        }
        return ids;
    }

    /** One shingle hash within one granularity — the grain document frequency is grouped by. */
    private record Hash(String parameterIdentity, long shingleHash) {}

    /** Running totals for one {@link Hash}: distinct documents seen, and how many times overall. */
    private static final class Counts {

        private final Set<Long> occurrencesSeen = new HashSet<>();
        private int totalCount;

        void record(long occurrenceId) {
            occurrencesSeen.add(occurrenceId);
            totalCount++;
        }

        int documentCount() {
            return occurrencesSeen.size();
        }

        int totalCount() {
            return totalCount;
        }
    }
}
