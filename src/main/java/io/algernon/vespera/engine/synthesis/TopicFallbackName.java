package io.algernon.vespera.engine.synthesis;

import java.util.Optional;

/**
 * The name a topic takes where the model's is turned down (ADR-407 section 7): the Docling title of its strongest
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

    /**
     * The name a subtopic takes where the model's is turned down (ADR-407 section 7, ADR-409 section 6): the Docling
     * title of its strongest survivor, then that survivor's filename stem, then the subtopic's place among its topic's
     * subtopics, as {@code Subtopic n} or {@code Sottoargomento n}. A title and a stem are the survivor's own words and
     * are not translated.
     *
     * @param doclingTitle the strongest survivor's Docling title, read by its content hash
     * @param survivorPath the strongest survivor's path relative to the corpus root
     * @param place the subtopic's place among its topic's subtopics in ADR-406 section 4's order, counted from 1
     */
    public static String ofSubtopic(
            Optional<String> doclingTitle, String survivorPath, int place, DeliverableLanguage language) {
        if (doclingTitle.isPresent() && !doclingTitle.get().isBlank()) {
            return doclingTitle.get();
        }
        String stem = FilenameStem.of(survivorPath);
        if (!stem.isBlank()) {
            return stem;
        }
        String word = language == DeliverableLanguage.ITALIAN ? "Sottoargomento" : "Subtopic";
        return word + " " + place;
    }
}
