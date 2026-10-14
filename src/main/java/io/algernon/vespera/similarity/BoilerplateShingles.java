package io.algernon.vespera.similarity;

import io.algernon.vespera.ledger.RunId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Resolves which shingle hashes count as boilerplate under one stage-3 run and one floor (ADR-080):
 * the gate {@code pipeline} checks before minting stage 4's run at all, and the exclusion set every
 * signature is computed against once the gate is open.
 *
 * <p><b>A hash with no row in {@code shingle_document_frequency} is never boilerplate.</b> ADR-074
 * writes a row only for a hash seen in two or more surviving documents, so an absent hash appeared in
 * exactly one — the rarest thing in the corpus, the opposite of boilerplate. This class only ever
 * excludes hashes it found a row for and whose count reached the floor; it never treats absence as
 * inclusion, which is the direction this filter silently inverts if misread.
 */
@Component
public class BoilerplateShingles {

    private final JdbcTemplate jdbcTemplate;

    public BoilerplateShingles(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * The shingle hashes whose {@code document_count} under {@code stage3RunId} (matched on {@link
     * ShingleParameters#DEFAULT}'s identity, the same granularity every stage-4 pass reads) is at
     * least {@code ceil(floor * shingled_document_count)}, taken in exact decimal arithmetic (ADR-260)
     * — {@code shingle_corpus_size}'s own denominator for that run and parameter identity.
     *
     * <p>An empty result when {@code shingle_corpus_size} carries no row for this run and parameter
     * identity is the correct answer, not a defect: it means stage 3 measured zero shingled documents,
     * so nothing can have reached any floor.
     */
    public Set<Long> resolve(RunId stage3RunId, double floor) {
        String shingleParameterIdentity = ShingleParameters.DEFAULT.identity();
        Long shingledDocumentCount = jdbcTemplate.query(
                "SELECT shingled_document_count FROM shingle_corpus_size"
                        + " WHERE run_id = ? AND shingle_parameter_identity = ?",
                resultSet -> resultSet.next() ? resultSet.getLong("shingled_document_count") : null,
                stage3RunId.value(),
                shingleParameterIdentity);
        if (shingledDocumentCount == null) {
            return Set.of();
        }

        long thresholdCount = cutoff(floor, shingledDocumentCount);
        Set<Long> boilerplate = new HashSet<>();
        jdbcTemplate.query(
                "SELECT shingle_hash FROM shingle_document_frequency"
                        + " WHERE run_id = ? AND shingle_parameter_identity = ? AND document_count >= ?",
                resultSet -> {
                    boilerplate.add(resultSet.getLong("shingle_hash"));
                },
                stage3RunId.value(),
                shingleParameterIdentity,
                thresholdCount);
        return boilerplate;
    }

    /**
     * {@code ceil(f * N)} in exact decimal arithmetic, {@code f} being the shortest decimal that reads
     * back as {@code floor} (ADR-260). {@code BigDecimal} has no NaN or infinity, so those, and any
     * product above N, give N + 1 (no count reaches it); a floor of 0 or below gives 0.
     */
    private static long cutoff(double floor, long shingledDocumentCount) {
        if (Double.isNaN(floor) || floor > 1) {
            return shingledDocumentCount + 1;
        }
        if (floor <= 0) {
            return 0;
        }
        return BigDecimal.valueOf(floor)
                .multiply(BigDecimal.valueOf(shingledDocumentCount))
                .setScale(0, RoundingMode.CEILING)
                .longValueExact();
    }
}
