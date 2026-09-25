package io.algernon.vespera.synthesis;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;

/**
 * One survivor as {@code documents.csv} carries it (ADR-104, ADR-112): every column the ledger
 * already holds but one, {@code contentHash}, which 6b takes from the file itself (ADR-151),
 * gathered here as a plain value so {@link Deliverable} needs no capability module this ticket did
 * not already name (ADR-110).
 *
 * <p><b>Two names for the seed, deliberately kept apart.</b> {@code winningSeed} is the seed's own
 * identity — the same value {@code document_cluster.winning_seed_occurrence_id} carries — and is
 * what the manifest's {@code winning_seed} column states, parallel to {@code occurrence_id} naming
 * this survivor's own identity. {@code seedPath} is the seed's own path, relative to the seed
 * folder rather than the archive (ADR-112) — the name an operator gave it by putting it in the seed
 * folder, and the same string {@link Deliverable} slugs to name the seed partition's directory — and
 * is what the manifest's {@code seed_partition} column states, parallel to {@code path} naming this
 * survivor's own place in the archive.
 *
 * @param occurrence this survivor's own identity
 * @param path this survivor's own root-relative path
 * @param contentHash the SHA-256 of this survivor's bytes as lowercase hex, taken at 6b through
 *     extraction's content hash, the key its conversion is cached under (ADR-151); blank only where
 *     the archive would not hand the file over when the tree was written
 * @param winningSeed the identity of the seed whose partition this survivor sits in
 * @param seedPath that seed's own path, relative to the seed folder (ADR-112)
 * @param clusterOrdinal the cluster's identity within that seed's partition
 * @param score this survivor's relevance score (ADR-020)
 */
public record ListedSurvivor(
        OccurrenceId occurrence,
        OccurrencePath path,
        String contentHash,
        OccurrenceId winningSeed,
        String seedPath,
        int clusterOrdinal,
        double score) {}
