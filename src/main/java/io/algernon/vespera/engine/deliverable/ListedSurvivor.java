package io.algernon.vespera.engine.deliverable;

import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.OccurrencePath;
import io.algernon.vespera.engine.synthesis.ClusterSlot;
import io.algernon.vespera.engine.synthesis.TopicKey;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * One survivor as {@code documents.csv} carries it (ADR-104, ADR-112): every column the ledger
 * already holds but one, {@code contentHash}, which is stage 2's recorded key (ADR-206),
 * gathered here as a plain value so {@link Deliverable} needs no capability module this ticket did
 * not already name (ADR-110).
 *
 * <p><b>Two names, deliberately kept apart.</b> {@code winningSeed} is the identity of the seed this
 * survivor scored highest against, and is what the manifest's {@code winning_seed} column states,
 * parallel to {@code occurrence_id} naming this survivor's own identity. {@code topic} is the key of
 * the topic it is placed under and {@code topicName} that topic's recorded name, which the manifest's
 * {@code topic} column states (ADR-415 section 6); the topic's first
 * passage may lie in a seed other than the winning one.
 *
 * @param occurrence this survivor's own identity
 * @param path this survivor's own root-relative path
 * @param contentHash the SHA-256 of this survivor's bytes as lowercase hex, the key stage 2
 *     recorded for this survivor, under which its conversion is cached (ADR-151, ADR-206); the copy
 *     is verified against it (ADR-274 section 2)
 * @param winningSeed the identity of the seed this survivor scored highest against (ADR-020), which the manifest's
 *     {@code winning_seed} column states
 * @param topic the topic this survivor is placed under (ADR-409 section 1)
 * @param topicName that topic's recorded name, which the manifest's {@code topic} column states
 *     (ADR-415 section 6)
 * @param subtopic the subtopic of that topic this survivor lies in, empty where the topic has none
 * @param clusterOrdinal the cluster's identity within its subtopic, or within the topic where it has none
 * @param score this survivor's relevance score (ADR-020)
 */
public record ListedSurvivor(
        OccurrenceId occurrence,
        OccurrencePath path,
        String contentHash,
        Optional<OccurrenceId> winningSeed,
        TopicKey topic,
        String topicName,
        OptionalInt subtopic,
        int clusterOrdinal,
        double score) {

    /** The cluster this survivor is a member of, by its topic, its subtopic and its ordinal there. */
    public ClusterSlot slot() {
        return new ClusterSlot(topic, subtopic, clusterOrdinal);
    }

    /**
     * The survivor with the score on record for it, or {@code 0.0} where there is none (ADR-226, moving
     * ADR-222's rule 8). {@code LeadDocument} weighs a member with no score as 0.0 too, for which member
     * leads; this 0.0 is the manifest's row and the page order, a distinct output with its own home.
     */
    public static ListedSurvivor of(
            OccurrenceId occurrence,
            OccurrencePath path,
            String contentHash,
            Optional<OccurrenceId> winningSeed,
            TopicKey topic,
            String topicName,
            OptionalInt subtopic,
            int clusterOrdinal,
            java.util.Map<OccurrenceId, Double> scoresOnRecord) {
        return new ListedSurvivor(
                occurrence,
                path,
                contentHash,
                winningSeed,
                topic,
                topicName,
                subtopic,
                clusterOrdinal,
                scoresOnRecord.getOrDefault(occurrence, 0.0));
    }
}
