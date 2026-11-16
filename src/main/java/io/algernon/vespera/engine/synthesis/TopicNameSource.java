package io.algernon.vespera.engine.synthesis;

/** Where a topic's name came from (ADR-403 sections 6 and 7): the model, or what stood in for it. */
public enum TopicNameSource {
    MODEL,
    SEED_TITLE,
    SEED_FILENAME_STEM,
    SEED_PATH
}
