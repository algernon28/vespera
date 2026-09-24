package io.algernon.vespera.similarity;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.util.HashSet;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code similarity}'s signature pass (ADR-080, ADR-081): for one stage-2 survivor, strips boilerplate
 * from its shingle set and — if anything is left — writes the resulting MinHash signature and its band
 * rows under stage 4's run. Writes nothing at all for a document whose every shingle was boilerplate:
 * it is empty rather than redundant, and comparing an empty signature against anything is not a
 * question this stage can answer honestly (ADR-080).
 */
@Component
public class RedundancySignatures {

    private final JdbcTemplate jdbcTemplate;

    public RedundancySignatures(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Reads {@code occurrenceId}'s shingle set from {@code stage2RunId} (the run stage 2's pass wrote
     * {@code shingle} rows under), strips every hash in {@code boilerplateHashes}, and computes and
     * writes the MinHash signature and band rows for what remains, under {@code stage4RunId}.
     *
     * @param boilerplateFloor the floor value that produced {@code boilerplateHashes} — folded into the
     *     written row's {@code signature_identity} (ADR-080), never re-derived from the set itself
     * @return whether a signature was written; {@code false} means every one of this document's
     *     shingles was boilerplate
     */
    public boolean write(
            OccurrenceId occurrenceId, RunId stage4RunId, RunId stage2RunId, Set<Long> boilerplateHashes, double boilerplateFloor) {
        Set<Long> distinctive = distinctiveShingleSet(occurrenceId, stage2RunId, boilerplateHashes);
        if (distinctive.isEmpty()) {
            return false;
        }

        MinHashParameters minHashParameters = MinHashParameters.DEFAULT;
        int[] minima = MinHashSignature.minima(distinctive, minHashParameters);
        String signatureIdentity = signatureIdentity(boilerplateFloor);

        jdbcTemplate.update(
                "INSERT INTO minhash_signature (occurrence_id, run_id, signature_identity, signature)"
                        + " VALUES (?, ?, ?, ?)",
                occurrenceId.value(),
                stage4RunId.value(),
                signatureIdentity,
                MinHashSignature.serialize(minima));

        long[] bandHashes = MinHashSignature.bandHashes(minima, minHashParameters);
        for (int band = 0; band < bandHashes.length; band++) {
            jdbcTemplate.update(
                    "INSERT INTO signature_band (occurrence_id, run_id, band_ordinal, band_hash) VALUES (?, ?, ?, ?)",
                    occurrenceId.value(),
                    stage4RunId.value(),
                    band,
                    bandHashes[band]);
        }
        return true;
    }

    /**
     * Deletes every signature and band row recorded under {@code stage4RunId} — the discard half of
     * ADR-115/ADR-116, for a step whose completion under this run is not recorded: both tables are
     * keyed with {@code run_id} in their primary key, so a second write over a stopped invocation's rows would
     * otherwise collide on the first document it re-signed.
     */
    public void discardForRun(RunId stage4RunId) {
        jdbcTemplate.update("DELETE FROM minhash_signature WHERE run_id = ?", stage4RunId.value());
        jdbcTemplate.update("DELETE FROM signature_band WHERE run_id = ?", stage4RunId.value());
    }

    /**
     * What a signature computed under {@link ShingleParameters#DEFAULT} and {@link
     * MinHashParameters#DEFAULT} means (ADR-080, ADR-081): the shingle parameter identity, the
     * permutation identity, and the boilerplate floor that stripped its input — deliberately redundant
     * with {@code run_id}, which already folds all three in, so a reader holding a signature row can
     * say what it is without first resolving the run row it belongs to.
     */
    static String signatureIdentity(double boilerplateFloor) {
        return ShingleParameters.DEFAULT.identity() + ";" + MinHashParameters.DEFAULT.identity() + ";floor:"
                + boilerplateFloor;
    }

    /**
     * {@code occurrenceId}'s boilerplate-stripped shingle set as distinct hashes — MinHash and Jaccard
     * both operate on a set, not on {@code shingle}'s own multiset of repeated phrases.
     *
     * <p>The set is what makes the hashes distinct, not the query (#277). Asked for {@code DISTINCT},
     * SQLite answers from {@code shingle_by_hash}, which is already ordered by hash, and so reads every
     * row of the run for each document: about five seconds a document on a 2.2-million-row table,
     * whatever the document's own size. Without it, the planner uses {@code shingle_by_occurrence} and
     * reads this document's rows alone.
     */
    private Set<Long> distinctiveShingleSet(OccurrenceId occurrenceId, RunId stage2RunId, Set<Long> boilerplateHashes) {
        Set<Long> distinctive = new HashSet<>();
        jdbcTemplate.query(
                "SELECT shingle_hash FROM shingle"
                        + " WHERE occurrence_id = ? AND run_id = ? AND shingle_parameter_identity = ?",
                resultSet -> {
                    long hash = resultSet.getLong("shingle_hash");
                    if (!boilerplateHashes.contains(hash)) {
                        distinctive.add(hash);
                    }
                },
                occurrenceId.value(),
                stage2RunId.value(),
                ShingleParameters.DEFAULT.identity());
        return distinctive;
    }
}
