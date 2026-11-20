package io.algernon.vespera.engine.synthesis;

/** Where a topic's name came from (ADR-407 sections 6 to 8). */
public enum NameOrigin {
    /** The model wrote it at the first asking. */
    WRITTEN,
    /** The model wrote it at the second asking, the first being the same as an earlier sibling's (section 8). */
    WRITTEN_WHEN_ASKED_AGAIN,
    /** The model's answer was turned down and the topic took its fallback name (section 7). */
    FALLBACK,
    /** The operator listed it in the profile: its name as written, never asked of the model (ADR-435 section 7). */
    LISTED
}
