package io.algernon.vespera.similarity;

import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
        Set<Long> survivorIds = drainSurvivors(stage2RunId);

        Map<Hash, Counts> byHash = new HashMap<>();
        Map<String, Set<Long>> shingledOccurrencesByParameter = new HashMap<>();

        jdbcTemplate.query(
                "SELECT occurrence_id, shingle_parameter_identity, shingle_hash FROM shingle WHERE run_id = ?",
                resultSet -> {
                    long occurrenceId = resultSet.getLong("occurrence_id");
                    if (!survivorIds.contains(occurrenceId)) {
                        return;
                    }
                    String parameterIdentity = resultSet.getString("shingle_parameter_identity");
                    long hash = resultSet.getLong("shingle_hash");
                    byHash.computeIfAbsent(new Hash(parameterIdentity, hash), ignored -> new Counts())
                            .record(occurrenceId);
                    shingledOccurrencesByParameter
                            .computeIfAbsent(parameterIdentity, ignored -> new HashSet<>())
                            .add(occurrenceId);
                },
                stage2RunId.value());

        byHash.forEach((hash, counts) -> {
            // The omission rule schema.sql's own comment states: only a hash seen in two or more
            // surviving documents earns a row (ADR-074) -- an absent hash means exactly one, never zero.
            if (counts.documentCount() < 2) {
                return;
            }
            jdbcTemplate.update(
                    "INSERT INTO shingle_document_frequency"
                            + " (run_id, shingle_parameter_identity, shingle_hash, document_count, total_count)"
                            + " VALUES (?, ?, ?, ?, ?)",
                    stage3RunId.value(),
                    hash.parameterIdentity(),
                    hash.shingleHash(),
                    counts.documentCount(),
                    counts.totalCount());
        });

        shingledOccurrencesByParameter.forEach((parameterIdentity, occurrenceIds) -> jdbcTemplate.update(
                "INSERT INTO shingle_corpus_size (run_id, shingle_parameter_identity, shingled_document_count)"
                        + " VALUES (?, ?, ?)",
                stage3RunId.value(),
                parameterIdentity,
                occurrenceIds.size()));
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
