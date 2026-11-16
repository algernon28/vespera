package io.algernon.vespera.engine.synthesis;

import java.util.Optional;

/**
 * The name a topic takes where the model's is turned down (ADR-403 section 7): the Docling title of its strongest
 * contributing seed, then that seed's filename stem, then the topic's place among the topics in key order, in the
 * deliverable language. A title and a stem are the seed's own words and are not translated.
 */
public final class TopicFallbackName {

    private TopicFallbackName() {}

    /**
     * @param doclingTitle the seed's Docling title, read by its content hash
     * @param seedPath the seed's path relative to the seed folder
     * @param place the topic's place among the topics in key order, counted from 1
     */
    public static String of(
            Optional<String> doclingTitle, String seedPath, int place, DeliverableLanguage language) {
        if (doclingTitle.isPresent() && !doclingTitle.get().isBlank()) {
            return doclingTitle.get();
        }
        String stem = FilenameStem.of(seedPath);
        if (!stem.isBlank()) {
            return stem;
        }
        String word = language == DeliverableLanguage.ITALIAN ? "Argomento" : "Topic";
        return word + " " + place;
    }
}
