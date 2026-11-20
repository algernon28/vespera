package io.algernon.vespera.engine.deliverable;

import io.algernon.vespera.engine.synthesis.DeliverableLanguage;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * One retrieval record (ADR-434 §5): a piece of text, its vector and the place it stands in. Three kinds: a cluster's
 * synthesis doc, a topic's overview and a member's passage. Its {@code id} is its content key's digest ({@link
 * RetrievalRecordId}), naming no generation run and no language.
 */
public record RetrievalRecord(
        String id, Kind kind, String text, float[] vector, String embedderIdentity, Placement placement) {

    /** The three kinds, written {@code synthesis-doc}, {@code topic-overview} and {@code member-passage}. */
    public enum Kind {
        SYNTHESIS_DOC("synthesis-doc"),
        TOPIC_OVERVIEW("topic-overview"),
        MEMBER_PASSAGE("member-passage");

        private final String written;

        Kind(String written) {
            this.written = written;
        }

        /** The kind as a record writes it. */
        public String written() {
            return written;
        }
    }

    /**
     * Where a record stands: its generation run and language, its topic and that topic's place from 1, its subtopic
     * and cluster where it has them, the citation ordinals it carries and, for a passage, its document's path and the
     * passage's ordinal among that document's chunks.
     */
    public record Placement(
            String generationRun,
            DeliverableLanguage language,
            String topic,
            int topicOrder,
            Optional<String> subtopic,
            OptionalInt subtopicOrder,
            Optional<String> cluster,
            OptionalInt clusterNumber,
            List<Integer> citations,
            Optional<String> document,
            OptionalInt passage) {}
}
