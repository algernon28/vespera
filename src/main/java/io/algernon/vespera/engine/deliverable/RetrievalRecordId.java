package io.algernon.vespera.engine.deliverable;

import io.algernon.vespera.engine.synthesis.ClusterSlot;
import io.algernon.vespera.engine.synthesis.TopicKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * A retrieval record's stable id (ADR-434 §6): the lowercase hexadecimal SHA-256 of the UTF-8 bytes of its kind and its
 * key's parts joined by U+001F, numbers in decimal, an absent subtopic {@code -1}. It names neither the generation
 * run nor the deliverable language, so the same place drawn again has the same id.
 */
public final class RetrievalRecordId {

    private static final String SEPARATOR = "\u001F";

    private RetrievalRecordId() {}

    /** A cluster's synthesis doc. */
    public static String ofSynthesisDoc(ClusterSlot cluster) {
        return digest(List.of(RetrievalRecord.Kind.SYNTHESIS_DOC.written(), path(cluster.topic()),
                index(cluster.topic()), subtopic(cluster), Integer.toString(cluster.clusterOrdinal())));
    }

    /** A topic's overview. */
    public static String ofTopicOverview(TopicKey topic) {
        return digest(List.of(RetrievalRecord.Kind.TOPIC_OVERVIEW.written(), path(topic), index(topic)));
    }

    /** The passage of a member, by content hash, at {@code passage} among that content's chunks. */
    public static String ofMemberPassage(ClusterSlot cluster, String contentHash, int passage) {
        return digest(List.of(RetrievalRecord.Kind.MEMBER_PASSAGE.written(), path(cluster.topic()),
                index(cluster.topic()), subtopic(cluster), Integer.toString(cluster.clusterOrdinal()), contentHash,
                Integer.toString(passage)));
    }

    private static String path(TopicKey topic) {
        return topic.seedPath();
    }

    private static String index(TopicKey topic) {
        return Integer.toString(topic.chunkIndex());
    }

    private static String subtopic(ClusterSlot cluster) {
        return Integer.toString(cluster.subtopic().orElse(-1));
    }

    private static String digest(List<String> parts) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(String.join(SEPARATOR, parts).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every JDK has SHA-256", e);
        }
    }
}
